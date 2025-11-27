package ru.rzn.gmyasoedov.gmaven.project.dependencyanalyzer

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.execution.ParametersListUtil.escape
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.*
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.util.GMavenNotification
import ru.rzn.gmyasoedov.gmaven.util.MavenPathUtil
import ru.rzn.gmyasoedov.gmaven.util.MavenPathUtil.getExtClassesJarPathString
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenArtifactState
import ru.rzn.gmyasoedov.maven.plugin.reader.model.tree.DependencyTreeNode
import ru.rzn.gmyasoedov.maven.plugin.reader.model.tree.MavenProjectDependencyTree
import ru.rzn.gmyasoedov.serverapi.GMavenServer
import java.io.FileReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class GDependencyAnalyzerContributor(private val project: Project) : DependencyAnalyzerContributor {
    @Volatile
    private var moduleMapByProject: Map<String, Pair<DAProject, ModuleData>>? = null
    private val dependencyTreeByProject: MutableMap<String, List<DependencyTreeNode>> = ConcurrentHashMap()

    override fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable) {
        val progressManager = ExternalSystemProgressNotificationManager.getInstance()
        progressManager.addNotificationListener(object : ExternalSystemTaskNotificationListener {
            override fun onEnd(path: String, id: ExternalSystemTaskId) {
                if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
                if (id.projectSystemId != SYSTEM_ID) return
                moduleMapByProject = null
                dependencyTreeByProject.clear()
            }
        }, parentDisposable)
    }

    override fun getProjects(): List<DependencyAnalyzerProject> {
        val moduleMap = getModuleMap()
        return moduleMap.values.map { it.first }
    }

    override fun getDependencyScopes(externalProject: DependencyAnalyzerProject) = listOf(
        scope(SCOPE_COMPILE),
        scope(SCOPE_PROVIDED),
        scope(SCOPE_RUNTIME),
        scope(SCOPE_SYSTEM),
        scope(SCOPE_IMPORT),
        scope(SCOPE_TEST)
    )

    override fun getDependencies(externalProject: DependencyAnalyzerProject): List<DependencyAnalyzerDependency> {
        val artifactGA = externalProject.getUserData(ARTIFACT_ID)!!
        val moduleData = getModuleMap()[artifactGA]?.second
        val projectPath = moduleData?.linkedExternalProjectPath ?: return emptyList()
        if (moduleData.getProperty(MODULE_PROP_HAS_DEPENDENCIES) == null) return emptyList()
        val mavenSettings = MavenSettings.getInstance(project)
        val projectSettings = mavenSettings.getLinkedProjectSettings(projectPath) ?: return emptyList()
        val dependencyTree = getDependencyTreeNodes(artifactGA, projectSettings.externalProjectPath)
        return createDependencyList(dependencyTree, moduleData, externalProject)
    }

    private fun getDependencyTreeNodes(artifactGA: String, externalProjectPath: String): List<DependencyTreeNode> {
        val dependencyTreeFromMap = dependencyTreeByProject[artifactGA]
        if (dependencyTreeFromMap != null) return dependencyTreeFromMap

        val resultPath = Path(externalProjectPath).resolve(GMavenServer.GMAVEN_DEPENDENCY_TREE)
        val settings = ExternalSystemTaskExecutionSettings()

        val wsl = MavenPathUtil.getWsl(resultPath.absolutePathString())
        val windowsPath = getExtClassesJarPathString()
        val extClassesJarPathString = wsl
            ?.let { wsl.getWslPath(Path(windowsPath)) } ?: getExtClassesJarPathString()
        val resultFilePath = MavenPathUtil.checkOnWsl(resultPath.absolutePathString())
        settings.scriptParameters = escape("-Dmaven.ext.class.path=$extClassesJarPathString")
        settings.scriptParameters += " -Daether.conflictResolver.verbose=true"
        settings.scriptParameters += " -Daether.dependencyManager.verbose=true"
        settings.scriptParameters += " " + escape("-DresultFilePath=${resultFilePath}")
        if (!Registry.`is`("gmaven.process.tree.fallback")) {
            settings.scriptParameters += " -pl $artifactGA -am"
        }
        settings.executionName = GBundle.message("gmaven.action.dependency.tree.sources")
        settings.externalProjectPath = externalProjectPath
        settings.taskNames = listOf(TASK_DEPENDENCY_TREE)
        settings.externalSystemIdString = SYSTEM_ID.id

        ExternalSystemUtil.runTask(
            settings, DefaultRunExecutor.EXECUTOR_ID, project, SYSTEM_ID,
            object : TaskCallback {
                override fun onSuccess() {
                    try {
                        val result: List<MavenProjectDependencyTree> = getDependencyTreeResult(resultPath)
                        result.forEach { dependencyTreeByProject[it.groupId + ":" + it.artifactId] = it.dependencies }
                    } catch (e: Exception) {
                        MavenLog.LOG.warn(e)
                        val message = GBundle.message("gmaven.action.notifications.dependency.tree.failed.content")
                        val finalMessage = e.localizedMessage + System.lineSeparator() + "<br/> $message"
                        GMavenNotification.createNotificationDA(finalMessage, NotificationType.ERROR)
                    } finally {
                        FileUtil.delete(resultPath.toFile())
                    }
                }

                override fun onFailure() {
                    val message = GBundle.message("gmaven.action.notifications.dependency.tree.failed.content")
                    GMavenNotification.createNotificationDA(message, NotificationType.ERROR)
                }
            }, ProgressExecutionMode.NO_PROGRESS_SYNC, true
        )

        return dependencyTreeByProject[artifactGA] ?: emptyList()
    }

    private fun getDependencyTreeResult(resultFilePath: Path): List<MavenProjectDependencyTree> {
        return FileReader(resultFilePath.toFile(), StandardCharsets.UTF_8).use {
            val typeOfT = TypeToken.getParameterized(
                MutableList::class.java,
                MavenProjectDependencyTree::class.java
            ).type
            Gson().fromJson(it, typeOfT)
        }
    }

    private fun getModuleMap(): Map<String, Pair<DAProject, ModuleData>> {
        val moduleMapByProjectLocal = moduleMapByProject
        if (moduleMapByProjectLocal != null) return moduleMapByProjectLocal
        return fillModulesInfoCache()
    }

    private fun fillModulesInfoCache(): Map<String, Pair<DAProject, ModuleData>> {
        val projectsData = ProjectDataManager.getInstance().getExternalProjectsData(project, SYSTEM_ID)
        val modulesData = getModulesData(projectsData)
        val modulesMap = modulesData.asSequence().map { Pair(it, MavenUtils.findIdeModule(project, it)) }
            .filter { it.second != null }.map { toDependencyAnalyzerProject(it) }.toMap()
        moduleMapByProject = modulesMap
        return modulesMap
    }

    private fun toDependencyAnalyzerProject(it: Pair<ModuleData, Module?>): Pair<String, Pair<DAProject, ModuleData>> {
        val moduleData = it.first
        val daProject = DAProject(it.second!!, moduleData.moduleName)
        val artifactId = MavenUtils.toGAString(moduleData)
        daProject.putUserData(ARTIFACT_ID, artifactId)
        return Pair(artifactId, Pair(daProject, moduleData))
    }

    private fun getModulesData(projectsData: Collection<ExternalProjectInfo>): List<ModuleData> {
        return projectsData.asSequence().map { it.externalProjectStructure }.filterNotNull()
            .flatMap { ExternalSystemApiUtil.findAll(it, ProjectKeys.MODULE) }.map { it.data }.toList()
    }

    private fun createDependencyList(
        treeNodeList: List<DependencyTreeNode>, moduleData: ModuleData, externalProject: DependencyAnalyzerProject
    ): List<DependencyAnalyzerDependency> {
        val root = DAModule(externalProject.title)
        root.putUserData(MODULE_DATA, moduleData)
        root.putUserData(BUILD_FILE, moduleData.getProperty(MODULE_PROP_BUILD_FILE))
        val rootDependency = DADependency(root, scope(SCOPE_COMPILE), null, emptyList())
        val result = mutableListOf<DependencyAnalyzerDependency>()
        collectDependency(treeNodeList, rootDependency, result)
        return result
    }

    private fun collectDependency(
        nodes: List<DependencyTreeNode>,
        parentDependency: DependencyAnalyzerDependency,
        result: MutableList<DependencyAnalyzerDependency>
    ) {
        for (mavenArtifactNode in nodes) {
            val dependencyData = getDependencyData(mavenArtifactNode)
            val dependency = DADependency(
                dependencyData,
                scope(mavenArtifactNode.originalScope ?: SCOPE_COMPILE),
                parentDependency,
                getStatus(mavenArtifactNode, dependencyData)
            )
            result.add(dependency)
            if (mavenArtifactNode.dependencies != null) {
                collectDependency(mavenArtifactNode.dependencies, dependency, result)
            }
        }
    }

    private fun getDependencyData(mavenArtifactNode: DependencyTreeNode): DependencyAnalyzerDependency.Data {
        val artifact = mavenArtifactNode.artifact
        val moduleInfo = getModuleMap().get(artifact.groupId + ":" + artifact.artifactId)
        if (moduleInfo != null) {
            val buildFile = moduleInfo.second.getProperty(MODULE_PROP_BUILD_FILE)
            val daModule = DAModule(artifact.artifactId)
            daModule.putUserData(BUILD_FILE, buildFile)
            return daModule
        }
        val daArtifact = DAArtifact(artifact.groupId, artifact.artifactId, artifact.version)
        if (artifact.filePath != null) {
            daArtifact.putUserData(BUILD_FILE, artifact.filePath.replace(".jar", ".pom"))
        }
        return daArtifact
    }

    private fun getStatus(
        mavenArtifactNode: DependencyTreeNode,
        dependencyData: DependencyAnalyzerDependency.Data
    ): List<DependencyAnalyzerDependency.Status> {
        val status = mutableListOf<DependencyAnalyzerDependency.Status>()
        if (mavenArtifactNode.state == MavenArtifactState.CONFLICT) {
            status.add(DAOmitted)
            mavenArtifactNode.relatedArtifact?.version?.also {
                val message =
                    ExternalSystemBundle.message("external.system.dependency.analyzer.warning.version.conflict", it)
                status.add(DAWarning(message))
            }
        } else if (mavenArtifactNode.state == MavenArtifactState.DUPLICATE) {
            status.add(DAOmitted)
        }
        if (dependencyData is DAArtifact && !mavenArtifactNode.artifact.isResolved) {
            status.add(DAWarning(ExternalSystemBundle.message("external.system.dependency.analyzer.warning.unresolved")))
        }
        return status
    }

    companion object {
        fun scope(name: @NlsSafe String) = DAScope(name, StringUtil.toTitleCase(name))
        val MODULE_DATA = Key.create<ModuleData>("GMavenDependencyAnalyzerContributor.ModuleData")
        val ARTIFACT_ID = Key.create<String>("GMavenDependencyAnalyzerContributor.ArtifactId")
        val BUILD_FILE = Key.create<String>("GMavenDependencyAnalyzerContributor.BuildFile")
    }
}
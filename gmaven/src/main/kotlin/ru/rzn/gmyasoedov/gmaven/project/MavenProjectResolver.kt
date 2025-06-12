package ru.rzn.gmyasoedov.gmaven.project

import com.intellij.execution.process.OSProcessHandler
import com.intellij.externalSystem.JavaProjectData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_INTERNAL_JAVA
import com.intellij.openapi.externalSystem.service.execution.ProjectJdkNotFoundException
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import org.jdom.Element
import org.jetbrains.kotlin.idea.base.externalSystem.find
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.extensionpoints.plugin.CompilerData
import ru.rzn.gmyasoedov.gmaven.extensionpoints.plugin.MavenFullImportPlugin
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.SourceSetData
import ru.rzn.gmyasoedov.gmaven.project.policy.ReadProjectResolverPolicy
import ru.rzn.gmyasoedov.gmaven.server.GServerRequest
import ru.rzn.gmyasoedov.gmaven.server.getProjectModel
import ru.rzn.gmyasoedov.gmaven.settings.MavenExecutionSettings
import ru.rzn.gmyasoedov.gmaven.util.toFeatureString
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenMapResult
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenPlugin
import ru.rzn.gmyasoedov.serverapi.GServerUtils
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class MavenProjectResolver : ExternalSystemProjectResolver<MavenExecutionSettings> {

    private val cancellationMap = ConcurrentHashMap<ExternalSystemTaskId, OSProcessHandler>()

    override fun cancelTask(id: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
        cancelTask(id, cancellationMap)
        return true
    }

    override fun resolveProjectInfo(
        id: ExternalSystemTaskId,
        projectPath: String,
        isPreviewMode: Boolean,
        settings: MavenExecutionSettings?,
        resolverPolicy: ProjectResolverPolicy?,
        listener: ExternalSystemTaskNotificationListener
    ): DataNode<ProjectData> {
        settings ?: throw ExternalSystemException("settings is empty")
        if (isPreviewMode) {
            return getPreviewProjectDataNode(projectPath, settings)
        }

        val sdk = settings.jdkName?.let { getSdk(it) } ?: throw ProjectJdkNotFoundException() //InvalidJavaHomeException
        val mavenHome = getMavenHome(settings)
        val buildPath = Path.of(settings.executionWorkspace.projectBuildFile ?: projectPath)
        val request = getServerRequest(id, buildPath, mavenHome, sdk, listener, settings, resolverPolicy)
        try {
            val projectModel = getProjectModel(request) { cancellationMap[id] = it }
            return getProjectDataNode(projectPath, projectModel, settings, sdk)
        } finally {
            cancellationMap.remove(id)
        }
    }

    private fun getServerRequest(
        id: ExternalSystemTaskId,
        buildPath: Path,
        mavenHome: Path,
        sdk: Sdk,
        listener: ExternalSystemTaskNotificationListener,
        settings: MavenExecutionSettings,
        resolverPolicy: ProjectResolverPolicy?
    ): GServerRequest {
        return GServerRequest(
            id, buildPath, mavenHome, sdk,
            listener = listener,
            settings = settings,
            readOnly = resolverPolicy is ReadProjectResolverPolicy || settings.isReadonly
        )
    }

    private fun getPreviewProjectDataNode(
        projectPath: String,
        settings: MavenExecutionSettings
    ): DataNode<ProjectData> {
        val projectDirectory = getProjectDirectory(projectPath).absolutePathString()
        val projectName = File(projectDirectory).name
        val projectData = ProjectData(GMavenConstants.SYSTEM_ID, projectName, projectDirectory, projectDirectory)
        val projectDataNode = DataNode(ProjectKeys.PROJECT, projectData, null)
        val ideProjectPath = settings.ideProjectPath
        val mainModuleFileDirectoryPath = ideProjectPath ?: projectDirectory
        projectDataNode
            .createChild(
                ProjectKeys.MODULE, ModuleData(
                    projectName, GMavenConstants.SYSTEM_ID, getDefaultModuleTypeId(),
                    projectName, mainModuleFileDirectoryPath, projectDirectory
                )
            )
            .createChild(ProjectKeys.CONTENT_ROOT, ContentRootData(GMavenConstants.SYSTEM_ID, projectDirectory))
        return projectDataNode
    }

    private fun getProjectDataNode(
        projectPath: String, mavenResult: MavenMapResult, settings: MavenExecutionSettings, sdk: Sdk
    ): DataNode<ProjectData> {
        val container = mavenResult.container
        val project = container.project
        val projectName = GServerUtils.getDisplayName(project)
        val absolutePath = project.basedir
        val projectData = ProjectData(GMavenConstants.SYSTEM_ID, projectName, absolutePath, projectPath)
        projectData.version = project.version
        projectData.group = project.groupId

        val projectDataNode = DataNode(ProjectKeys.PROJECT, projectData, null)

        val sdkName: String = sdk.name
        val projectSdkData = ProjectSdkData(sdkName)
        projectDataNode.createChild(ProjectSdkData.KEY, projectSdkData)
        var languageLevel = LanguageLevel.parse(sdkName)
        if (languageLevel == null) {
            languageLevel = LanguageLevel.HIGHEST
            MavenLog.LOG.warn("No language level for $sdkName use $languageLevel")
        }
        val javaProjectData = JavaProjectData(
            GMavenConstants.SYSTEM_ID, project.outputDirectory, languageLevel,
            languageLevel!!.toFeatureString(), emptyList()
        )
        projectDataNode.createChild(JavaProjectData.KEY, javaProjectData)

        var ideProjectPath = settings.ideProjectPath
        ideProjectPath = ideProjectPath ?: projectPath
        val context = getContext(projectDataNode, settings, absolutePath, ideProjectPath, mavenResult)

        val moduleNode = createModuleData(container, projectDataNode, context)
        addDependencies(container, projectDataNode, context)
        populateProfiles(projectDataNode, context.mavenResult.settings)
        moduleNode.data.setProperty(GMavenConstants.MODULE_PROP_LOCAL_REPO, mavenResult.settings.localRepository)

        return projectDataNode
    }

    private fun getContext(
        projectDataNode: DataNode<ProjectData>,
        settings: MavenExecutionSettings,
        absolutePath: String,
        ideProjectPath: String,
        mavenResult: MavenMapResult
    ): ProjectResolverContext {
        val languageLevel = projectDataNode.find(JavaProjectData.KEY)?.data?.languageLevel ?: LanguageLevel.HIGHEST
        return ProjectResolverContext(
            projectDataNode, settings, absolutePath, ideProjectPath, mavenResult, languageLevel, isMaven4(settings)
        )
    }

    private fun getProjectDirectory(projectPath: String): Path {
        val projectNioPath = Path(projectPath)
        return if (projectNioPath.toFile().isDirectory) projectNioPath else projectNioPath.parent
    }

    private fun getSdk(it: String) = if (ApplicationManager.getApplication().isUnitTestMode)
        ExternalSystemJdkUtil.getJdk(null, USE_INTERNAL_JAVA) else ExternalSystemJdkUtil.getJdk(null, it)

    class ProjectResolverContext(
        val projectNode: DataNode<ProjectData>,
        val settings: MavenExecutionSettings,
        val rootProjectPath: String,
        val ideaProjectPath: String,
        val mavenResult: MavenMapResult,
        val projectLanguageLevel: LanguageLevel,
        val isMaven4: Boolean,
        var aspectJCompilerData: MutableList<CompilerDataHolder> = ArrayList(0),
        val contextElementMap: MutableMap<String, Element> = HashMap(),
        val moduleDataByArtifactId: MutableMap<String, ModuleContextHolder> = TreeMap(),
        val libraryDataMap: MutableMap<String, DataNode<LibraryData>> = TreeMap(),
        val pluginExtensionMap: Map<String, MavenFullImportPlugin> = MavenFullImportPlugin.EP_NAME.extensions
            .associateBy { it.key }
    )

    class ModuleContextHolder(
        val moduleNode: DataNode<ModuleData>,
        val perSourceSetModules: PerSourceSetModules?,
    )

    class PerSourceSetModules(
        val mainNode: DataNode<SourceSetData>,
        val testNode: DataNode<SourceSetData>,
    )

    class CompilerDataHolder(val plugin: MavenPlugin, val compilerData: CompilerData)

    companion object {
        fun cancelTask(id: ExternalSystemTaskId, cancellationMap: MutableMap<ExternalSystemTaskId, OSProcessHandler>) {
            cancellationMap.remove(id)?.destroyProcess()
        }
    }
}
@file:JvmName("ProjectResolverUtils")

package ru.rzn.gmyasoedov.gmaven.project

import com.intellij.externalSystem.MavenRepositoryData
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil.compareVersionNumbers
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.extensionpoints.plugin.*
import ru.rzn.gmyasoedov.gmaven.extensionpoints.plugin.kotlin.KotlinMavenPlugin
import ru.rzn.gmyasoedov.gmaven.extensionpoints.plugin.kotlin.KotlinMavenPluginData
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.*
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.MainJavaCompilerData.Companion.ASPECTJ_COMPILER_ID
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.notification.OpenGMavenSettingsCallback
import ru.rzn.gmyasoedov.gmaven.project.wrapper.MavenWrapperDistribution
import ru.rzn.gmyasoedov.gmaven.project.wrapper.MvnDotProperties
import ru.rzn.gmyasoedov.gmaven.settings.DistributionType
import ru.rzn.gmyasoedov.gmaven.settings.MavenExecutionSettings
import ru.rzn.gmyasoedov.gmaven.util.MavenPathUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import ru.rzn.gmyasoedov.maven.plugin.reader.model.*
import ru.rzn.gmyasoedov.serverapi.GServerUtils
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

fun getMavenHome(executionSettings: MavenExecutionSettings): Path {
    val distributionSettings = executionSettings.distributionSettings
    if (distributionSettings.type == DistributionType.WRAPPER) {
        val externalProjectPath = executionSettings.executionWorkspace.externalProjectPath
        val distributionUrl = getDistributionUrl(externalProjectPath, executionSettings.project)
        if (distributionUrl != distributionSettings.url) {
            distributionSettings.path = null
        }
        distributionSettings.url = distributionUrl

        val mvnwScript = getMvnwScript(executionSettings)
        if (mvnwScript != null) return mvnwScript
    }
    if (distributionSettings.path != null && distributionSettings.path.exists()) return distributionSettings.path
    if (distributionSettings.url != null) {
        val mavenHome = MavenWrapperDistribution.getOrDownload(distributionSettings.url)
        distributionSettings.path = mavenHome.path
        return mavenHome.path
    }
    val quickFixId = OpenGMavenSettingsCallback.ID
    throw ExternalSystemException(GBundle.message("gmaven.notification.mvn.not.found", quickFixId), quickFixId)
}

private fun getDistributionUrl(externalProjectPath: String, project: Project?): String {
    if (project == null) return MvnDotProperties.getDistributionUrl(externalProjectPath)
    return try {
        MvnDotProperties.getDistributionUrl(project, externalProjectPath)
    } catch (_: Exception) {
        MvnDotProperties.getDistributionUrl(externalProjectPath)
    }
}

private fun getMvnwScript(executionSettings: MavenExecutionSettings): Path? {
    val wslDistribution = MavenPathUtil.getWsl(executionSettings)
    val projectRootPath = Path.of(executionSettings.executionWorkspace.externalProjectPath)
    val unixMvnw = projectRootPath.resolve("mvnw")
    if (wslDistribution != null) {
        return unixMvnw
    }
    if (SystemInfo.isWindows && projectRootPath.resolve("mvnw.cmd").exists()) {
        return projectRootPath.resolve("mvnw.cmd")
    } else if (unixMvnw.exists()) {
        return unixMvnw
    }
    return null
}

fun getPluginsData(mavenProject: MavenProject, context: MavenProjectResolver.ProjectResolverContext): PluginsData {
    var compilerPlugin: MavenPlugin? = null
    var compilerData: CompilerData? = null
    var kotlinPluginData: KotlinMavenPluginData? = null

    val localRepoPath: String? = context.mavenResult.settings.localRepository
    for (plugin in mavenProject.plugins) {
        val pluginExtension = context.pluginExtensionMap[MavenUtils.toGAString(plugin)] ?: continue
        if (pluginExtension is MavenCompilerFullImportPlugin) {
            val compilerDataPlugin = getCompilerData(localRepoPath, pluginExtension, mavenProject, plugin, context)
            if (isPriorityCompiler(compilerPlugin, plugin, context)) {
                compilerPlugin = plugin
                compilerData = compilerDataPlugin
            }
        } else if (pluginExtension is KotlinMavenPlugin && kotlinPluginData == null) {
            kotlinPluginData = pluginExtension.getCompilerData(mavenProject, plugin, context)
        }
    }

    addedMavenAspectJPluginInfo(compilerPlugin, compilerData, context)
    return PluginsData(
        compilerPlugin,
        compilerData ?: ApacheMavenCompilerPlugin.getDefaultCompilerData(mavenProject, context.projectLanguageLevel),
        kotlinPluginData,
        mavenProject.excludedPaths
    )
}

private fun getCompilerData(
    localRepoPath: String?,
    pluginExtension: MavenCompilerFullImportPlugin,
    mavenProject: MavenProject,
    plugin: MavenPlugin,
    context: MavenProjectResolver.ProjectResolverContext
): CompilerData? {
    localRepoPath ?: return null
    return pluginExtension.getCompilerData(mavenProject, plugin, Path.of(localRepoPath), context.contextElementMap)
}

fun getMainJavaCompilerData(
    plugin: MavenPlugin?,
    mavenProject: MavenProject,
    compilerData: CompilerData,
    context: MavenProjectResolver.ProjectResolverContext
): MainJavaCompilerData {
    plugin ?: return MainJavaCompilerData.createDefault()
    val localRepoPath = context.mavenResult.settings.localRepository ?: return MainJavaCompilerData.createDefault()

    return MavenFullImportPlugin.EP_NAME
        .findExtensionOrFail(MavenCompilerFullImportPlugin::class.java)
        .getJavaCompilerData(mavenProject, plugin, compilerData, Path.of(localRepoPath), context.contextElementMap)
}

fun getContentRootPath(paths: List<String>, type: ExternalSystemSourceType): List<MavenContentRoot> {
    return paths.asSequence()
        .filter { it.isNotEmpty() }
        .map { MavenContentRoot(type, it) }
        .toList()
}

fun getContentRootPathResource(paths: List<MavenResource>, type: ExternalSystemSourceType): List<MavenContentRoot> {
    return getContentRootPath(paths.mapNotNull { it.directory }, type)
}

fun populateTasks(
    moduleDataNode: DataNode<ModuleData>, mavenProject: MavenProject,
    context: MavenProjectResolver.ProjectResolverContext
) {
    //todo maven4 from MVND
    //add seed for tasks. all logic there: MavenExternalViewContributor.
    if (context.isMaven4) {
        val key = LifecycleMaven4Data.KEY
        moduleDataNode.createChild(key, LifecycleMaven4Data(SYSTEM_ID, "base", mavenProject.basedir))
    } else {
        moduleDataNode.createChild(LifecycleData.KEY, LifecycleData(SYSTEM_ID, "base", mavenProject.basedir))
    }

    if (!context.settings.isShowPluginNodes) {
        MavenArtifactUtil.clearPluginDescriptorCache()
        return
    }
    val localRepoPath = context.mavenResult.settings.localRepository?.let { Path.of(it) }
    for (plugin in mavenProject.plugins) {
        val pluginDescriptor = MavenArtifactUtil.readPluginDescriptor(localRepoPath, plugin) ?: continue
        for (mojo in pluginDescriptor.mojos) {
            moduleDataNode.createChild(
                PluginData.KEY, PluginData(
                    SYSTEM_ID, mojo.displayName, mavenProject.basedir,
                    GServerUtils.getMavenId(plugin), pluginDescriptor.goalPrefix
                )
            )
        }
    }
}

fun populateRemoteRepository(projectDataNode: DataNode<ProjectData>, remoteRepositories: Set<MavenRemoteRepository>) {
    for (repository in remoteRepositories) {
        projectDataNode.createChild(
            MavenRepositoryData.KEY, MavenRepositoryData(SYSTEM_ID, repository.id, repository.url)
        )
    }
}

fun populateProfiles(dataNode: DataNode<ProjectData>, mavenSettings: MavenSettings) {
    for (profile in mavenSettings.profiles) {
        dataNode.createChild(
            ProfileData.KEY,
            ProfileData(SYSTEM_ID, dataNode.data.linkedExternalProjectPath, profile.name, profile.isActivation)
        )
    }
}

fun populateAnnotationProcessorData(
    project: MavenProject,
    moduleDataNode: DataNode<ModuleData>,
    compilerData: CompilerData
) {
    val annotationProcessorPaths = project.annotationProcessorPaths
    val data = CompilerPluginData
        .create(annotationProcessorPaths, compilerData.arguments, project.buildDirectory, project.basedir)
    data.buildGeneratedAnnotationDirectory = project.generatedPath
    data.buildGeneratedAnnotationTestDirectory = project.testGeneratedPath
    moduleDataNode.createChild(CompilerPluginData.KEY, data)
}

fun getDefaultModuleTypeId(): String {
    return ModuleTypeManager.getInstance().defaultModuleType.id
}

private fun addedMavenAspectJPluginInfo(
    plugin: MavenPlugin?,
    compilerData: CompilerData?,
    context: MavenProjectResolver.ProjectResolverContext,
) {
    if (plugin?.artifactId == "aspectj-maven-plugin" && compilerData != null) {
        context.aspectJCompilerData.add(MavenProjectResolver.CompilerDataHolder(plugin, compilerData))
    }
}

fun getAjcCompilerData(context: MavenProjectResolver.ProjectResolverContext): MainJavaCompilerData? {
    val aspectJCompilerData = context.aspectJCompilerData
    if (aspectJCompilerData.isNotEmpty()) {
        val localRepository = context.mavenResult.settings.localRepository?.let { Path(it) } ?: return null
        var dependenciesPath = aspectJCompilerData
            .firstNotNullOfOrNull { DevAspectjMavenPlugin.getDependencyPath(it.plugin, localRepository) }
        if (dependenciesPath == null) {
            dependenciesPath = aspectJCompilerData
                .firstNotNullOfOrNull {
                    DevAspectjMavenPlugin.getDependencyPathFromDescriptor(it.plugin, localRepository)
                }
        }
        dependenciesPath ?: return null
        val arguments = aspectJCompilerData.flatMapTo(mutableSetOf()) { it.compilerData.pluginSpecificArguments }
        return MainJavaCompilerData.create(ASPECTJ_COMPILER_ID, listOf(dependenciesPath), arguments)
    }
    return null
}

private fun isPriorityCompiler(
    currentPlugin: MavenPlugin?, plugin: MavenPlugin, context: MavenProjectResolver.ProjectResolverContext
): Boolean {
    currentPlugin ?: return true
    val currentPriority = (context.pluginExtensionMap[MavenUtils.toGAString(currentPlugin)]
            as? MavenCompilerFullImportPlugin)?.priority() ?: 0
    val priority = (context.pluginExtensionMap[MavenUtils.toGAString(plugin)]
            as? MavenCompilerFullImportPlugin)?.priority() ?: 0
    return priority > currentPriority
}

fun isMaven4(settings: MavenExecutionSettings): Boolean {
    if (settings.distributionSettings.type == DistributionType.WRAPPER
        && settings.distributionSettings.url != null
    ) {
        return settings.distributionSettings.url.contains("maven-4.")
                || settings.distributionSettings.url.contains("/4.")
    }
    if (settings.distributionSettings.path != null) {
        try {
            val mavenVersion = MavenUtils.getMavenVersion(settings.distributionSettings.path.toFile())
            if (mavenVersion != null) {
                return compareVersionNumbers("4.0", mavenVersion) <= 0
            }
        } catch (_: Exception) {
            return false
        }
    }
    return false
}

class PluginsData(
    val compilerPlugin: MavenPlugin?, val compilerData: CompilerData,
    val kotlinPluginData: KotlinMavenPluginData?, val excludedPaths: List<String>
)
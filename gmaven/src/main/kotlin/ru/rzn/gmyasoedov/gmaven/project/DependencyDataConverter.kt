@file:JvmName("DependencyDataConverter")

package ru.rzn.gmyasoedov.gmaven.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependenciesImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.DependencyScope
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.project.MavenProjectResolver.ProjectResolverContext
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.SourceSetData
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenArtifact
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenProject
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenProjectContainer
import ru.rzn.gmyasoedov.serverapi.GServerUtils
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

private const val MIN_MODULES_COUNT_TO_CHECK_SOURCES = 10

fun addDependencies(
    container: MavenProjectContainer, projectNode: DataNode<ProjectData>, context: ProjectResolverContext
) {
    addDependencies(container.project, context)
    for (childContainer in container.modules) {
        addDependencies(childContainer, projectNode, context)
    }
}

private fun addDependencies(project: MavenProject, context: ProjectResolverContext) {
    val moduleContextHolder = context.moduleDataByArtifactId[GServerUtils.getMavenId(project)]!!
    val sourceSetModules = moduleContextHolder.perSourceSetModules
    if (sourceSetModules == null) {
        addDependencies(project, moduleContextHolder.moduleNode, context)
    } else {
        addDependencies(project, sourceSetModules.mainNode, sourceSetModules.testNode, context)
    }
}

private fun addDependencies(
    project: MavenProject, moduleByMavenProject: DataNode<ModuleData>, context: ProjectResolverContext
) {
    var hasLibrary = false
    for (artifact in project.resolvedArtifacts) {
        val moduleContextHolder = context.moduleDataByArtifactId[GServerUtils.getMavenId(artifact)]
        val moduleDataNodeByMavenArtifact = moduleContextHolder?.perSourceSetModules?.mainNode
            ?: moduleContextHolder?.moduleNode
        if (moduleDataNodeByMavenArtifact == null || isClassifierJar(artifact)) {
            addLibrary(moduleByMavenProject, artifact, moduleDataNodeByMavenArtifact, context)
            if (!hasLibrary) hasLibrary = true
        } else {
            addModuleDependency(moduleByMavenProject, moduleDataNodeByMavenArtifact.data, artifact)
        }
    }
    if (hasLibrary) {
        moduleByMavenProject.data.setProperty(GMavenConstants.MODULE_PROP_HAS_DEPENDENCIES, true.toString())
        moduleByMavenProject.createChild(ProjectKeys.DEPENDENCIES_GRAPH, ProjectDependenciesImpl())
    }
}

private fun addDependencies(
    project: MavenProject,
    mainModule: DataNode<SourceSetData>,
    testModule: DataNode<SourceSetData>,
    context: ProjectResolverContext
) {
    var hasLibrary = false
    for (artifact in project.resolvedArtifacts) {
        val moduleContextHolder = context.moduleDataByArtifactId[GServerUtils.getMavenId(artifact)]
        val moduleDataNodeByMavenArtifact = moduleContextHolder?.perSourceSetModules?.mainNode
            ?: moduleContextHolder?.moduleNode
        if (moduleDataNodeByMavenArtifact == null || isClassifierJar(artifact)) {
            addLibrary(mainModule, testModule, artifact, moduleDataNodeByMavenArtifact, context)
            if (!hasLibrary) hasLibrary = true
        } else {
            addModuleDependency(mainModule, moduleDataNodeByMavenArtifact.data, artifact)
            addModuleDependency(testModule, moduleDataNodeByMavenArtifact.data, artifact)
        }
    }
    if (hasLibrary) {
        mainModule.data.setProperty(GMavenConstants.MODULE_PROP_HAS_DEPENDENCIES, true.toString())
        mainModule.createChild(ProjectKeys.DEPENDENCIES_GRAPH, ProjectDependenciesImpl())

        testModule.data.setProperty(GMavenConstants.MODULE_PROP_HAS_DEPENDENCIES, true.toString())
        testModule.createChild(ProjectKeys.DEPENDENCIES_GRAPH, ProjectDependenciesImpl())
    }
}

private fun addLibrary(
    parentNode: DataNode<ModuleData>,
    artifact: MavenArtifact,
    classifierModuleNode: DataNode<out ModuleData>?,
    context: ProjectResolverContext
) {
    val createdLibrary = createLibrary(artifact, classifierModuleNode, context)
    var level = LibraryLevel.PROJECT
    if (!linkProjectLibrary(context, context.projectNode, createdLibrary)) {
        level = LibraryLevel.MODULE
    }

    val libraryDependencyData = LibraryDependencyData(parentNode.data, createdLibrary, level)
    libraryDependencyData.scope = getScope(artifact)
    libraryDependencyData.order = 20 + getScopeOrder(libraryDependencyData.scope)
    parentNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
}

private fun addLibrary(
    mainNode: DataNode<SourceSetData>,
    testNode: DataNode<SourceSetData>,
    artifact: MavenArtifact,
    classifierModuleNode: DataNode<out ModuleData>?,
    context: ProjectResolverContext
) {
    val scope = getScope(artifact)
    val createdLibrary = createLibrary(artifact, classifierModuleNode, context)
    var level = LibraryLevel.PROJECT
    if (!linkProjectLibrary(context, context.projectNode, createdLibrary)) {
        level = LibraryLevel.MODULE
    }
    if (scope != DependencyScope.TEST) {
        addLibraryDependencyData(mainNode, createdLibrary, level, scope)
        addLibraryDependencyData(testNode, createdLibrary, level, scope)
    } else {
        addLibraryDependencyData(testNode, createdLibrary, level, scope)
    }
}

private fun addLibraryDependencyData(
    dataNode: DataNode<SourceSetData>, library: LibraryData, level: LibraryLevel, scope: DependencyScope
) {
    val libraryDependencyData = LibraryDependencyData(dataNode.data, library, level)
    libraryDependencyData.scope = scope
    libraryDependencyData.order = 20 + getScopeOrder(libraryDependencyData.scope)
    dataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
}

private fun linkProjectLibrary(
    context: ProjectResolverContext, ideProject: DataNode<ProjectData>, library: LibraryData
): Boolean {
    val cache = context.libraryDataMap
    val libraryName = library.externalName

    val libraryData = cache.computeIfAbsent(libraryName) {
        var newValueToCache = ExternalSystemApiUtil
            .findChild(ideProject, ProjectKeys.LIBRARY) { it.data.externalName == libraryName }
        if (newValueToCache == null) {
            newValueToCache = ideProject.createChild(ProjectKeys.LIBRARY, library)
        }
        newValueToCache
    }
    return libraryData.data == library
}

private fun addModuleDependency(
    parentNode: DataNode<out ModuleData>, targetModule: ModuleData, artifact: MavenArtifact
) {
    val ownerModule = parentNode.data
    val data = ModuleDependencyData(ownerModule, targetModule)
    data.order = 1
    data.scope = getScope(artifact)
    parentNode.createChild(ProjectKeys.MODULE_DEPENDENCY, data)
}

private fun createLibrary(
    artifact: MavenArtifact, classifierModuleNode: DataNode<out ModuleData>?, context: ProjectResolverContext
): LibraryData {
    val artifactFile = getLibraryFile(artifact, classifierModuleNode)
    val library = LibraryData(GMavenConstants.SYSTEM_ID, GServerUtils.getMavenId(artifact), artifactFile == null)
    library.artifactId = artifact.artifactId
    if (artifact.classifier == "tests") {
        //todo
        //in ext.system - com.intellij.openapi.externalSystem.model.project.ModuleDependencyData.productionOnTestDependency
        //in maven - org.jetbrains.idea.maven.importing.tree.dependency.ModuleDependency (in org.jetbrains.idea.maven.importing.tree.MavenProjectImportContextProvider#getDependency)
        library.artifactId = artifact.artifactId + "-tests"
    }
    library.setGroup(artifact.groupId)
    library.version = artifact.version

    val artifactAbsolutePath = artifactFile?.absolutePath ?: return library

    library.addPath(getLibraryPathType(artifact), artifactAbsolutePath)
    if (context.moduleDataByArtifactId.size < MIN_MODULES_COUNT_TO_CHECK_SOURCES || context.settings.isCheckSources) {
        if (artifact.classifier.isNullOrEmpty()) {
            val sourceAbsolutePath = artifactAbsolutePath.replace(".jar", "-sources.jar")
            if (sourceAbsolutePath != artifactAbsolutePath && Path.of(sourceAbsolutePath).exists()) {
                library.addPath(LibraryPathType.SOURCE, sourceAbsolutePath)
            }
        }
    }

    return library
}

private fun getLibraryFile(artifact: MavenArtifact, classifierModuleNode: DataNode<out ModuleData>?): File? {
    if (artifact.filePath != null || artifact.classifier == null || classifierModuleNode == null) {
        return artifact.filePath?.let { File(it) }
    }
    return try {
        val files = Path.of(classifierModuleNode.data.linkedExternalProjectPath).resolve("target").toFile()
            .listFiles()?.filter { it.isFile } ?: emptyList()
        files.find { it.absolutePath.endsWith("-${artifact.classifier}.jar") }
    } catch (e: Exception) {
        null
    }
}

private fun getScope(artifact: MavenArtifact): DependencyScope {
    return when {
        isTestScope(artifact) -> DependencyScope.TEST
        GMavenConstants.SCOPE_RUNTIME == artifact.scope -> DependencyScope.RUNTIME
        GMavenConstants.SCOPE_PROVIDED == artifact.scope -> DependencyScope.PROVIDED
        else -> DependencyScope.COMPILE
    }
}

private fun getScopeOrder(scope: DependencyScope): Int {
    return when (scope) {
        DependencyScope.TEST -> 3
        DependencyScope.RUNTIME -> 2
        DependencyScope.PROVIDED -> 1
        else -> 0
    }
}

private fun getLibraryPathType(artifact: MavenArtifact): LibraryPathType {
    return when {
        "javadoc".equals(artifact.classifier, ignoreCase = true) -> LibraryPathType.DOC
        "sources".equals(artifact.classifier, ignoreCase = true) -> LibraryPathType.SOURCE
        else -> LibraryPathType.BINARY
    }
}

private fun isTestScope(artifact: MavenArtifact) = GMavenConstants.SCOPE_TEST == artifact.scope

private fun isClassifierJar(artifact: MavenArtifact): Boolean {
    return artifact.classifier != null && artifact.classifier.isNotEmpty()
}

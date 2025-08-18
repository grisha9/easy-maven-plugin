@file:JvmName("ModuleDataConverter")

package ru.rzn.gmyasoedov.gmaven.project

import com.intellij.execution.configurations.JavaParameters
import com.intellij.externalSystem.JavaModuleData
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.*
import ru.rzn.gmyasoedov.gmaven.extensionpoints.plugin.CompilerData
import ru.rzn.gmyasoedov.gmaven.extensionpoints.plugin.kotlin.KotlinMavenPluginData
import ru.rzn.gmyasoedov.gmaven.project.MavenProjectResolver.ModuleContextHolder
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.*
import ru.rzn.gmyasoedov.gmaven.util.toFeatureString
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenProject
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenProjectContainer
import ru.rzn.gmyasoedov.serverapi.GServerUtils
import java.io.File
import java.nio.file.Path

private const val MAIN = "main"
private const val TEST = "test"

fun createModuleData(
    container: MavenProjectContainer,
    projectDataNode: DataNode<ProjectData>,
    context: MavenProjectResolver.ProjectResolverContext,
    parentModuleDataNode: DataNode<ModuleData>? = null,
): DataNode<ModuleData> {
    val parentNode = parentModuleDataNode ?: projectDataNode
    val project = container.project
    val parentExternalName = getModuleName(parentNode.data, true)
    val parentInternalName = getModuleName(parentNode.data, false)
    val id = if (parentExternalName == null) project.artifactId else ":" + project.artifactId
    val projectPath = project.basedir
    val moduleFileDirectoryPath: String = getIdeaModulePath(context, projectPath)
    val moduleData = ModuleData(
        id, SYSTEM_ID, getDefaultModuleTypeId(), project.artifactId, moduleFileDirectoryPath, projectPath
    )
    moduleData.internalName = getInternalModuleName(parentInternalName, project.artifactId)
    moduleData.group = project.groupId
    moduleData.version = project.version
    moduleData.moduleName = project.artifactId
    moduleData.publication = ProjectId(project.groupId, project.artifactId, project.version)

    moduleData.isInheritProjectCompileOutputPath = false
    moduleData.setCompileOutputPath(ExternalSystemSourceType.SOURCE, project.outputDirectory)
    moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST, project.testOutputDirectory)
    moduleData.useExternalCompilerOutput(false)
    moduleData.setProperty(MODULE_PROP_BUILD_FILE, MavenUtils.getBuildFilePath(project.filePath))

    val moduleDataNode = projectDataNode.createChild(ProjectKeys.MODULE, moduleData)

    val pluginsData = getPluginsData(project, context)
    val rootPaths = listOf(
        getContentRootPath(project.sourceRoots, ExternalSystemSourceType.SOURCE),
        getContentRootPathResource(project.resourceRoots, ExternalSystemSourceType.RESOURCE),
        getContentRootPath(project.testSourceRoots, ExternalSystemSourceType.TEST),
        getContentRootPathResource(project.testResourceRoots, ExternalSystemSourceType.TEST_RESOURCE),
    ).flatten()
    val generatedPaths = getGeneratedSources(project, pluginsData.excludedPaths.toSet())
    val contentRoot = ContentRoots(rootPaths, generatedPaths, project.buildDirectory)

    val compilerData = pluginsData.compilerData
    val sourceLanguageLevel: LanguageLevel = getSourceLevel(compilerData.sourceLevel, compilerData)
    val targetBytecodeLevel: LanguageLevel = compilerData.targetLevel
    moduleDataNode.createChild(ModuleSdkData.KEY, ModuleSdkData(null))

    val targetBytecodeVersion = targetBytecodeLevel.toFeatureString()
    moduleDataNode.createChild(
        JavaModuleData.KEY, JavaModuleData(SYSTEM_ID, sourceLanguageLevel, targetBytecodeVersion, emptyList())
    )

    if (project.resolvedArtifacts.isNotEmpty()) {
        moduleDataNode.createChild(DependencyAnalyzerData.KEY, DependencyAnalyzerData(SYSTEM_ID, project.artifactId))
    }

    populateAnnotationProcessorData(project, moduleDataNode, compilerData)
    populateTasks(moduleDataNode, project, context)
    pluginsData.kotlinPluginData?.also { moduleDataNode.createChild(KotlinMavenPluginData.KEY, it) }

    val perSourceSetModules =
        setupContentRootAndSourceSetData(moduleDataNode, contentRoot, context, project, compilerData)
    val mavenId = GServerUtils.getMavenId(project)
    context.moduleDataByArtifactId[mavenId] = ModuleContextHolder(moduleDataNode, perSourceSetModules)

    setParentGA(project, context, moduleData)
    for (childContainer in container.modules) {
        createModuleData(childContainer, projectDataNode, context, moduleDataNode)
    }

    if (parentModuleDataNode == null) {
        val mainCompilerData = getAjcCompilerData(context)
            ?: getMainJavaCompilerData(pluginsData.compilerPlugin, project, compilerData, context)
        projectDataNode.createChild(MainJavaCompilerData.KEY, mainCompilerData)
    }
    return moduleDataNode
}

private fun setParentGA(
    project: MavenProject,
    context: MavenProjectResolver.ProjectResolverContext,
    moduleData: ModuleData
) {
    if (project.parentArtifact != null) {
        val parentModule = context.moduleDataByArtifactId[GServerUtils.getMavenId(project.parentArtifact)]
        parentModule?.moduleNode?.data?.also {
            moduleData.setProperty(MODULE_PROP_PARENT_GA, MavenUtils.toGAString(it))
        }
    }
}

private fun setupContentRootAndSourceSetData(
    moduleDataNode: DataNode<ModuleData>,
    contentRoot: ContentRoots,
    context: MavenProjectResolver.ProjectResolverContext,
    project: MavenProject,
    compilerData: CompilerData,
): MavenProjectResolver.PerSourceSetModules? {
    val perSourceSet = MavenUtils.isPerSourceSet(context.settings, project, compilerData)
    if (!perSourceSet) {
        val contentRootData = createContentRootData(contentRoot, project.basedir)
        contentRootData.forEach { moduleDataNode.createChild(ProjectKeys.CONTENT_ROOT, it) }
        return null
    }

    val mainModuleContentRoots = ContentRoots(emptyList(), emptyList(), contentRoot.excludedPath)
    val contentRootData = createContentRootData(mainModuleContentRoots, project.basedir)
    contentRootData.forEach { moduleDataNode.createChild(ProjectKeys.CONTENT_ROOT, it) }

    moduleDataNode.data.isInheritProjectCompileOutputPath = true
    val mainSourceSetModule = createSourceSetModule(moduleDataNode, project, compilerData, contentRoot, false)
    val testSourceSetModule = createSourceSetModule(moduleDataNode, project, compilerData, contentRoot, true)
    setDependTestOnMain(testSourceSetModule, mainSourceSetModule)
    return MavenProjectResolver.PerSourceSetModules(mainSourceSetModule, testSourceSetModule)
}

private fun setDependTestOnMain(
    testSourceSetModule: DataNode<SourceSetData>,
    mainSourceSetModule: DataNode<SourceSetData>
) {
    val ownerModule = testSourceSetModule.data
    val data = ModuleDependencyData(ownerModule, mainSourceSetModule.data)
    data.order = 1
    testSourceSetModule.createChild(ProjectKeys.MODULE_DEPENDENCY, data)
}

private fun createSourceSetModule(
    mainModuleNode: DataNode<ModuleData>,
    project: MavenProject,
    compilerData: CompilerData,
    contentRoots: ContentRoots,
    isTest: Boolean
): DataNode<SourceSetData> {
    val mainModuleData = mainModuleNode.data
    val moduleSuffix = if (isTest) TEST else MAIN
    val id = mainModuleData.id + ":" + moduleSuffix
    val externalName = project.artifactId + ":" + moduleSuffix
    val internalName = getInternalModuleName(mainModuleData.internalName, moduleSuffix)
    val data = SourceSetData(id, externalName, internalName, mainModuleData.moduleFileDirectoryPath, project.basedir)
    val moduleNode = mainModuleNode.createChild(SourceSetData.KEY, data)
    val currentContentRoots = splitContentRoots(contentRoots, isTest)

    data.isInheritProjectCompileOutputPath = false
    data.useExternalCompilerOutput(false)
    data.setProperty(MODULE_PROP_BUILD_FILE, MavenUtils.getBuildFilePath(project.filePath))

    val basePath = Path.of(project.basedir)
    val rootModulePath = basePath.resolve("src").resolve(moduleSuffix).toString()
    val contentRootData = createContentRootData(currentContentRoots, rootModulePath)
    contentRootData.forEach { moduleNode.createChild(ProjectKeys.CONTENT_ROOT, it) }

    if (isTest) {
        data.setCompileOutputPath(ExternalSystemSourceType.TEST, project.testOutputDirectory)
    } else {
        data.setCompileOutputPath(ExternalSystemSourceType.SOURCE, project.outputDirectory)
    }

    val sourceLevel = if (isTest) compilerData.testSourceLevel else compilerData.sourceLevel
    val targetLevel = (if (isTest) compilerData.testTargetLevel else compilerData.targetLevel)

    val level = getSourceLevel(sourceLevel, compilerData)
    val targetBytecodeVersion = targetLevel.toFeatureString()
    //todo fix compiler args
    moduleNode.createChild(JavaModuleData.KEY, JavaModuleData(SYSTEM_ID, level, targetBytecodeVersion, emptyList()))
    moduleNode.createChild(ModuleSdkData.KEY, ModuleSdkData(null))
    return moduleNode
}

private fun getModuleName(data: Any, external: Boolean): String? {
    return if (data is ModuleData) {
        if (external) data.externalName else data.internalName
    } else null
}

private fun getInternalModuleName(parentName: String?, moduleName: String): String {
    return if (parentName == null) moduleName else "$parentName.$moduleName"
}

private fun getIdeaModulePath(context: MavenProjectResolver.ProjectResolverContext, projectPath: String): String {
    val relativePath: String?
    val isUnderProjectRoot = FileUtil.isAncestor(context.rootProjectPath, projectPath, false)
    relativePath = if (isUnderProjectRoot) {
        FileUtil.getRelativePath(context.rootProjectPath, projectPath, File.separatorChar)
    } else {
        FileUtil.pathHashCode(projectPath).toString()
    }
    val subPath = if (relativePath == null || relativePath == ".") "" else relativePath
    return context.ideaProjectPath + File.separatorChar + subPath
}

private fun createContentRootData(contentRoots: ContentRoots, rootPath: String): List<ContentRootData> {
    val roots = mutableListOf(ContentRootData(SYSTEM_ID, rootPath))
    if (contentRoots.excludedPath != null) {
        addExcludedContentRoot(roots, contentRoots.excludedPath)
    }
    for (contentRoot in contentRoots.paths) {
        addContentRoot(roots, contentRoot.type, contentRoot.path)
    }
    for (generatedContentRoot in contentRoots.generatedPaths) {
        addContentRoot(roots, generatedContentRoot)
    }
    return roots
}

private fun addContentRoot(roots: MutableList<ContentRootData>, type: ExternalSystemSourceType, path: String) {
    var errorOnStorePath = false
    for (root in roots) {
        try {
            root.storePath(type, path)
            return
        } catch (e: IllegalArgumentException) {
            errorOnStorePath = true
        }
    }
    if (errorOnStorePath) {
        val contentRootData = ContentRootData(SYSTEM_ID, path)
        contentRootData.storePath(type, path)
        roots.add(contentRootData)
    }
}

private fun addContentRoot(roots: MutableList<ContentRootData>, generatedContentRoot: MavenGeneratedContentRoot) {
    var errorOnStorePath = false
    var added = false
    for (root in roots) {
        for (path in generatedContentRoot.paths) {
            try {
                root.storePath(generatedContentRoot.type, path)
                added = true
            } catch (e: IllegalArgumentException) {
                errorOnStorePath = true
            }
        }
        if (added) return
    }
    if (errorOnStorePath) {
        val contentRootData = ContentRootData(SYSTEM_ID, generatedContentRoot.rootPath.toString())
        generatedContentRoot.paths.forEach { contentRootData.storePath(generatedContentRoot.type, it) }
        roots.add(contentRootData)
    }
}

private fun addExcludedContentRoot(roots: MutableList<ContentRootData>, path: String) {
    var errorOnStorePath = false
    for (root in roots) {
        try {
            val sizeBefore = root.getPaths(ExternalSystemSourceType.EXCLUDED).size
            root.storePath(ExternalSystemSourceType.EXCLUDED, path)
            val sizeAfter = root.getPaths(ExternalSystemSourceType.EXCLUDED).size
            if (sizeBefore == sizeAfter) throw IllegalArgumentException()
            return
        } catch (e: IllegalArgumentException) {
            errorOnStorePath = true
        }
    }
    if (errorOnStorePath) {
        val contentRootData = ContentRootData(SYSTEM_ID, path)
        contentRootData.storePath(ExternalSystemSourceType.EXCLUDED, path)
        roots.add(contentRootData)
    }
}


private fun splitContentRoots(contentRoots: ContentRoots, test: Boolean): ContentRoots {
    return ContentRoots(
        contentRoots.paths.filter { it.type.isTest == test },
        contentRoots.generatedPaths.filter { it.type.isTest == test },
        null
    )
}

private fun getGeneratedSources(project: MavenProject, excludedRoots: Set<String>): List<MavenGeneratedContentRoot> {
    if (MavenUtils.isPomProject(project)) return emptyList()

    val generatedSourcePath = project.generatedPath?.let { Path.of(it).parent }
        ?: MavenUtils.getGeneratedSourcesDirectory(project.buildDirectory, false)
    val generatedTestSourcePath = project.testGeneratedPath?.let { Path.of(it).parent }
        ?: MavenUtils.getGeneratedSourcesDirectory(project.buildDirectory, true)
    return listOfNotNull(
        getGeneratedContentRoot(generatedSourcePath, excludedRoots, ExternalSystemSourceType.SOURCE_GENERATED),
        getGeneratedContentRoot(generatedTestSourcePath, excludedRoots, ExternalSystemSourceType.TEST_GENERATED),
    )
}

private fun getGeneratedContentRoot(
    rootPath: Path,
    excludedRoots: Set<String>,
    type: ExternalSystemSourceType
): MavenGeneratedContentRoot? {
    val listFiles = rootPath.toFile().listFiles() ?: return null
    val paths = listFiles.asSequence()
        .filter { it.isDirectory }
        .filter { !excludedRoots.contains(it.absolutePath) }
        .map { it.absolutePath }.toList()
    return MavenGeneratedContentRoot(type, rootPath, paths)
}

private fun getSourceLevel(level: LanguageLevel, compilerData: CompilerData): LanguageLevel {
    return if (compilerData.arguments.contains(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY))
        level.getPreviewLevel() ?: level else level
}

private class ContentRoots(
    val paths: List<MavenContentRoot>,
    val generatedPaths: List<MavenGeneratedContentRoot>,
    val excludedPath: String?
)
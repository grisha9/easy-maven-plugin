@file:JvmName("ExecutionSettingsUtil")

package ru.rzn.gmyasoedov.gmaven.util

import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.toNioPathOrNull
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.MODULE_PROP_BUILD_FILE
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.LifecycleData
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.ProfileData
import ru.rzn.gmyasoedov.gmaven.project.profile.ProjectProfilesStateService
import ru.rzn.gmyasoedov.gmaven.settings.MavenExecutionWorkspace
import ru.rzn.gmyasoedov.gmaven.settings.MavenProjectSettings
import ru.rzn.gmyasoedov.gmaven.settings.ProjectExecution
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import java.nio.file.Path
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

fun getLocalRepoPath(project: Project, externalProjectPath: String): String? {
    val projectDataNode = ProjectDataManager.getInstance()
        .getExternalProjectData(project, GMavenConstants.SYSTEM_ID, externalProjectPath)
        ?.externalProjectStructure ?: return null
    return ExternalSystemApiUtil.findAll(projectDataNode, ProjectKeys.MODULE)
        .map { it.data.getProperty(GMavenConstants.MODULE_PROP_LOCAL_REPO) }
        .firstOrNull()
}

fun fillExecutionWorkSpace(
    project: Project, projectSettings: MavenProjectSettings, projectPath: String, workspace: MavenExecutionWorkspace
) {
    workspace.externalProjectPath = projectSettings.externalProjectPath
    val projectDataNode = ProjectDataManager.getInstance()
        .getExternalProjectData(project, GMavenConstants.SYSTEM_ID, projectSettings.externalProjectPath)
        ?.externalProjectStructure
    if (projectDataNode == null) {
        addedProfiles(ProjectProfilesStateService.getInstance(project), workspace)
        if (projectSettings.projectBuildFile != null) {
            workspace.projectBuildFile = projectSettings.projectBuildFile
        }
        return
    }
    val allModules = ExternalSystemApiUtil.findAll(projectDataNode, ProjectKeys.MODULE)

    val mainModuleNode = allModules
        .find { MavenUtils.equalsPaths(it.data.linkedExternalProjectPath, projectSettings.externalProjectPath) }
    workspace.projectBuildFile = if (projectSettings.projectBuildFile != null) projectSettings.projectBuildFile else
        mainModuleNode?.data?.getProperty(MODULE_PROP_BUILD_FILE)

    val isRootPath = MavenUtils.equalsPaths(projectSettings.externalProjectPath, projectPath)
    var targetModuleNode: DataNode<ModuleData>? = null
    if (!isRootPath) {
        val contextPair = getTargetModuleAndContextMap(projectPath, allModules)
        targetModuleNode = contextPair.first
        targetModuleNode?.let { fillProjectBuildFiles(it, workspace, contextPair.second) }
    }
    addedIgnoredModule(workspace, allModules, targetModuleNode)
    addedProfiles(projectDataNode, ProjectProfilesStateService.getInstance(project), workspace)
    setIncrementalPath(project, workspace, projectSettings, allModules)
    setMaven4(workspace, projectDataNode)
    setMultiModuleProjectDirectory(projectSettings.externalProjectPath, workspace)
}

private fun setIncrementalPath(
    project: Project,
    workspace: MavenExecutionWorkspace,
    projectSettings: MavenProjectSettings,
    allModules: Collection<DataNode<ModuleData>>
) {
    workspace.projectBuildFile ?: return
    if (!projectSettings.incrementalSync) return
    if (Registry.`is`("gmaven.import.readonly")) return
    if (ActionManagerEx.getInstanceEx().lastPreformedActionId != "ExternalSystem.ProjectRefreshAction") return

    val incrementBuildFile = try {
        FileEditorManager.getInstance(project)?.selectedTextEditor?.virtualFile?.toNioPathOrNull()
    } catch (e: Exception) {
        MavenLog.LOG.error(e.message, e)
        null
    } ?: return
    val incrementBuildPath = incrementBuildFile.absolutePathString()
    if (MavenUtils.equalsPaths(workspace.projectBuildFile, incrementBuildPath)) return
    workspace.incrementalProjectName = allModules
        .find { MavenUtils.equalsPaths(it.data.getProperty(MODULE_PROP_BUILD_FILE), incrementBuildPath) }
        ?.data?.let { MavenUtils.toGAString(it) }
}

private fun getTargetModuleAndContextMap(
    projectPath: String, allModules: Collection<DataNode<ModuleData>>
): Pair<DataNode<ModuleData>?, TreeMap<String, DataNode<ModuleData>>> {
    val moduleByInternalName = TreeMap<String, DataNode<ModuleData>>()
    var targetModuleNode: DataNode<ModuleData>? = null
    for (each in allModules) {
        moduleByInternalName[each.data.internalName] = each
        if (targetModuleNode == null && MavenUtils.equalsPaths(each.data.linkedExternalProjectPath, projectPath)) {
            targetModuleNode = each
        }
    }
    return targetModuleNode to moduleByInternalName
}

private fun fillProjectBuildFiles(
    node: DataNode<ModuleData>,
    workspace: MavenExecutionWorkspace,
    moduleByInternalName: TreeMap<String, DataNode<ModuleData>>
) {
    val module = node.data
    workspace.subProjectBuildFile = module.getProperty(MODULE_PROP_BUILD_FILE)
    if (ActionManagerEx.getInstanceEx().lastPreformedActionId == "GMaven.ExternalSystem.RunSimplePomTaskAction") return

    val parentBuildFile = getParentBuildFile(node, moduleByInternalName)
    if (parentBuildFile != null) {
        workspace.projectBuildFile = parentBuildFile
        if (MavenUtils.equalsPaths(workspace.projectBuildFile, workspace.subProjectBuildFile)) {
            workspace.subProjectBuildFile = null
        }
    }
    if (workspace.subProjectBuildFile != null) {
        workspace.addProject(ProjectExecution(MavenUtils.toGAString(module), true))
    }
    workspace.subProjectBuildFile = null
}

private fun getParentBuildFile(
    node: DataNode<ModuleData>, moduleByInternalName: TreeMap<String, DataNode<ModuleData>>
): String? {
    val parentGA = node.data.getProperty(GMavenConstants.MODULE_PROP_PARENT_GA)
    if (parentGA != null) {
        val parentModule = node.data.ideParentGrouping?.let { moduleByInternalName[it] }
        if (parentModule != null) {
            return getParentBuildFile(parentModule, moduleByInternalName)
        }
    }
    return node.data.getProperty(MODULE_PROP_BUILD_FILE)
}

private fun addedIgnoredModule(
    workspace: MavenExecutionWorkspace,
    allModules: Collection<DataNode<ModuleData>>,
    targetModuleNode: DataNode<ModuleData>?
) {
    val basePrefixInternalName = if (targetModuleNode != null) {
        targetModuleNode.data.internalName
    } else {
        val buildFile = workspace.subProjectBuildFile ?: workspace.projectBuildFile ?: return
        val parentNode = allModules.find { it.data.getProperty(MODULE_PROP_BUILD_FILE) == buildFile } ?: return
        parentNode.data.internalName
    } + "."
    allModules.asSequence()
        .filter { it.isIgnored }
        .filter { it.data.internalName.startsWith(basePrefixInternalName) }
        .map { ProjectExecution(MavenUtils.toGAString(it.data), false) }
        .forEach { data: ProjectExecution? -> workspace.addProject(data) }
}

private fun addedProfiles(
    projectDataNode: DataNode<ProjectData>,
    profilesStateService: ProjectProfilesStateService,
    workspace: MavenExecutionWorkspace
) {
    for (profileDataNode in ExternalSystemApiUtil.findAll(projectDataNode, ProfileData.KEY)) {
        profilesStateService.getProfileExecution(profileDataNode.data)?.let { workspace.addProfile(it) }
    }
}

private fun addedProfiles(profilesStateService: ProjectProfilesStateService, workspace: MavenExecutionWorkspace) {
    profilesStateService.getProfileExecutions().forEach { workspace.addProfile(it) }
}

@Deprecated("delete in next release")
private fun setMultiModuleProjectDirectory(
    externalProjectPath: String?, workspace: MavenExecutionWorkspace
) {
    if (externalProjectPath == null) return
    val projectPathString = workspace.subProjectBuildFile ?: workspace.projectBuildFile ?: return
    val projectPath = Path.of(projectPathString)
    val projectDirPath = if (projectPath.toFile().isDirectory()) projectPath else projectPath.parent
    if (MavenUtils.equalsPaths(projectDirPath.toString(), externalProjectPath)) return
    val mainProjectPath = Path.of(externalProjectPath)
    workspace.multiModuleProjectDirectory = getMultiModuleProjectDirectory(projectDirPath, mainProjectPath).toString()
}

private fun getMultiModuleProjectDirectory(projectPath: Path, mainProjectPath: Path): Path {
    val workingDirectory = if (projectPath.toFile().isDirectory()) projectPath else projectPath.parent
    var projectPathTmp = workingDirectory
    try {
        while (projectPathTmp != mainProjectPath) {
            if (projectPathTmp.resolve(".mvn").exists()) {
                return projectPathTmp
            }
            projectPathTmp = projectPathTmp.parent
        }
        if (projectPathTmp.resolve(".mvn").exists()) {
            return projectPathTmp
        }
    } catch (_: Exception) {
    }
    return workingDirectory
}


private fun setMaven4(
    workspace: MavenExecutionWorkspace,
    projectDataNode: DataNode<ProjectData>
) {
    workspace.isMaven4 = (ExternalSystemApiUtil.findFirstRecursively(projectDataNode) { it.key == LifecycleData.KEY }
        ?.data as? LifecycleData)?.isMaven4 ?: false
}

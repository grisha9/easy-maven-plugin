package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.action.ExternalSystemNodeAction
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.project.Project
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import kotlin.io.path.Path

class OpenLocalRepoAction : ExternalSystemNodeAction<ProjectData>(ProjectData::class.java) {

    init {
        getTemplatePresentation().text = GBundle.message("gmaven.action.show.local.repo")
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
        if (!super.isEnabled(e)) return false
        val project = e.project ?: return false
        val systemId = getSystemId(e)
        if (systemId != GMavenConstants.SYSTEM_ID) return false
        val selectedNodes = e.getData(ExternalSystemDataKeys.SELECTED_NODES)
        if (selectedNodes == null || selectedNodes.size != 1) return false
        val externalData = selectedNodes[0].data
        return externalData is ProjectData && MavenSettings.getInstance(project)
            .getLinkedProjectSettings(externalData.linkedExternalProjectPath)
            ?.localRepositoryPath != null
    }

    override fun perform(
        project: Project, projectSystemId: ProjectSystemId, projectData: ProjectData, e: AnActionEvent
    ) {
        MavenSettings.getInstance(project)
            .getLinkedProjectSettings(projectData.linkedExternalProjectPath)
            ?.localRepositoryPath
            ?.let { RevealFileAction.openFile(Path(it)) }
    }
}
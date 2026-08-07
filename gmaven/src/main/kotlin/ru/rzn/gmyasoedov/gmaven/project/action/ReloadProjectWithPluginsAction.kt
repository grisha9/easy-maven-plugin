package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.action.ExternalSystemNodeAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.TASK_RESOLVE_PLUGINS
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle


class ReloadProjectWithPluginsAction :
    ExternalSystemNodeAction<AbstractExternalEntityData>(AbstractExternalEntityData::class.java), DumbAware {

    init {
        templatePresentation.text = GBundle.message("gmaven.action.reload.with.plugins.text")
        templatePresentation.setDescription(
            ExternalSystemBundle.messagePointer("action.refresh.project.description", "External")
        )
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
        if (!super.isEnabled(e)) return false
        val systemId = getSystemId(e)
        if (systemId != GMavenConstants.SYSTEM_ID) return false
        val selectedNodes = e.getData(ExternalSystemDataKeys.SELECTED_NODES)
        if (selectedNodes == null || selectedNodes.size != 1) return false
        val externalData = selectedNodes[0].data
        return externalData is ProjectData || externalData is ModuleData
    }

    override fun perform(
        project: Project,
        projectSystemId: ProjectSystemId,
        externalData: AbstractExternalEntityData,
        e: AnActionEvent
    ) {
        val selectedNodes = e.getData(ExternalSystemDataKeys.SELECTED_NODES)
        val configPathAware = ContainerUtil.getFirstItem(selectedNodes)?.data as? ExternalConfigPathAware ?: return
        FileDocumentManager.getInstance().saveAllDocuments()

        val projectSettings = ExternalSystemApiUtil.getSettings(project, projectSystemId)
            .getLinkedProjectSettings(configPathAware.linkedExternalProjectPath)
        val externalProjectPath = if (projectSettings == null)
            configPathAware.linkedExternalProjectPath else projectSettings.externalProjectPath

        val importSpec = ImportSpecBuilder(project, projectSystemId).withArguments(TASK_RESOLVE_PLUGINS)
        if (ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProject(project, projectSystemId)) {
            ExternalSystemUtil.refreshProject(externalProjectPath, importSpec)
        }
    }
}
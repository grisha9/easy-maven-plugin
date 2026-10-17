package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.DumbAwareAction
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle

class AndroidRefreshAllExternalProjectsAction : DumbAwareAction() {
    init {
        templatePresentation.setText(
            GBundle.messagePointer("gmaven.action.refresh.all.projects.text", SYSTEM_ID.readableName)
        )
        templatePresentation.setDescription(
            GBundle.messagePointer("gmaven.action.refresh.all.projects.text", SYSTEM_ID.readableName)
        )
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val fullProductName = ApplicationNamesInfo.getInstance().fullProductName
        e.presentation.isEnabledAndVisible = (e.getData(EXTERNAL_SYSTEM_ID) == SYSTEM_ID)
                && fullProductName.contains("android", true)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, SYSTEM_ID))
    }

}
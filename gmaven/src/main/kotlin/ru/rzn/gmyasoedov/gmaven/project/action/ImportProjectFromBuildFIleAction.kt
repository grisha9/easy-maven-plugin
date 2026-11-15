package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.externalSystem.action.ExternalSystemAction
import com.intellij.openapi.vfs.toNioPathOrNull
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import ru.rzn.gmyasoedov.gmaven.wizard.GOpenProjectProvider

class ImportProjectFromBuildFIleAction : ExternalSystemAction() {

    init {
        setText(GBundle.message("gmaven.action.ImportExternalProject.text"))
        setDescription(GBundle.message("gmaven.action.ImportExternalProject.description"))
    }

    override fun isEnabled(e: AnActionEvent): Boolean = true

    override fun isVisible(e: AnActionEvent): Boolean {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
        val project = e.getData(CommonDataKeys.PROJECT) ?: return false
        val filePath = virtualFile.toNioPathOrNull()?.toString()  ?: return false
        if (CachedModuleDataService.getDataHolder(project).isConfigPath(filePath)) return false
        return MavenUtils.isSimplePomFile(virtualFile.name) || MavenUtils.isPotentialPomFile(virtualFile.name)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        GOpenProjectProvider().linkMavenProject(virtualFile, project)
    }
}
package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.ui.MavenCentralUIEditor
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil

class MavenCentralBrowserAction : AnAction() {
    init {
        templatePresentation.icon = AllIcons.Actions.StartDebugger
        templatePresentation.text = GBundle.message("gmaven.action.maven.central")
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = getArtifactIdTag(e) != null
    }

    private fun getArtifactIdTag(e: AnActionEvent): XmlTag? {
        val project = e.project ?: return null
        val virtualFile = e.getData(VIRTUAL_FILE_ARRAY)?.takeIf { it.size == 1 }?.first() ?: return null
        if (virtualFile.fileType != XmlFileType.INSTANCE) return null
        val filePath = virtualFile.toNioPathOrNull()?.toString() ?: return null
        if (!CachedModuleDataService.getDataHolder(project).isConfigPath(filePath)) return null
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val offset = e.getData(CommonDataKeys.CARET)?.offset ?: return null
        val element = psiFile.findElementAt(offset)
        return getArtifactIdTag(element)
    }

    private fun getArtifactIdTag(element: PsiElement?): XmlTag? {
        val xmlTag = element as? XmlTag ?: element?.parentOfType<XmlTag>() ?: return null
        if (xmlTag.name == MavenArtifactUtil.ARTIFACT_ID) return xmlTag
        val parentTag = xmlTag.parentTag ?: return null
        if (parentTag.name.lowercase() == "project") return null
        return parentTag.findFirstSubTag(MavenArtifactUtil.ARTIFACT_ID)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val artifactIdTag = getArtifactIdTag(e) ?: return
        val artifactId = artifactIdTag.value.text
        val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
        val mavenCentralUIEditor = selectedEditor as? MavenCentralUIEditor ?: return
        mavenCentralUIEditor.search(artifactId)

    }
}
package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.project.stateStore
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.toUElement
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.central.EASY_MAVEN_CENTRAL_SEARCH
import ru.rzn.gmyasoedov.gmaven.central.MavenCentralUIEditor
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil
import javax.swing.SwingConstants


class MavenCentralBrowserAction : AnAction() {
    init {
        templatePresentation.icon = AllIcons.Actions.StartDebugger
        templatePresentation.text = GBundle.message("gmaven.action.maven.central")
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = isBuildScript(e)
    }

    private fun isBuildScript(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val virtualFile = e.getData(VIRTUAL_FILE_ARRAY)?.takeIf { it.size == 1 }?.first() ?: return false
        if (isGradleBuildScript(virtualFile)) {
            return true
        }
        if (virtualFile.fileType == XmlFileType.INSTANCE) {
            val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? XmlFile
            if (psiFile?.rootTag?.name == MavenArtifactUtil.PROJECT) {
                return true
            }
        }
        val filePath = virtualFile.toNioPathOrNull()?.toString() ?: return false
        return CachedModuleDataService.getDataHolder(project).isConfigPath(filePath)
    }

    private fun isGradleBuildScript(virtualFile: VirtualFile): Boolean =
        virtualFile.name.endsWith(".gradle.kts") || virtualFile.name.endsWith(".gradle")

    /**
     * Pair of artifactId - first & groupId - second
     * */
    private fun getArtifactIdGroupIdInfo(e: AnActionEvent): Pair<String, String?>? {
        val virtualFile = e.getData(VIRTUAL_FILE_ARRAY)?.takeIf { it.size == 1 }?.first() ?: return null
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val offset = e.getData(CommonDataKeys.CARET)?.offset ?: return null
        val element = psiFile.findElementAt(offset)
        if (isGradleBuildScript(virtualFile)) {
            val uElement = element.toUElement() ?: element?.parent?.toUElement() ?: return null
            val artifactIdFromGradle = (uElement as? ULiteralExpression)?.evaluateString() ?: return null
            return artifactIdFromGradle to null
        }

        val xmlTag = element as? XmlTag ?: element?.parentOfType<XmlTag>() ?: return null
        if (xmlTag.name == MavenArtifactUtil.ARTIFACT_ID) {
            val artifactId = xmlTag.value.text.takeIf { it.isNotEmpty() } ?: return null
            val groupId = xmlTag.parentTag?.getSubTagText(MavenArtifactUtil.GROUP_ID)
            return artifactId to groupId
        }
        return null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val artifactInfo = getArtifactIdGroupIdInfo(e)
        val artifactId = artifactInfo?.first
        val groupId = artifactInfo?.second

        val basePath = project.stateStore.directoryStorePath ?: return
        val searchTempPath = basePath.resolve("easy-maven").resolve(EASY_MAVEN_CENTRAL_SEARCH)
        FileUtil.createIfNotExists(searchTempPath.toFile())

        val virtualFile = VfsUtil.findFile(searchTempPath, true) ?: return
        val psiFile = virtualFile.toPsiFile(project)
        if (!MavenAdvancedSettingsState.getInstance().searchInSplitWindow && psiFile != null) {
            psiFile.navigate(true)
        } else {
            val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
            val curWindow = fileEditorManager.currentWindow ?: return
            curWindow.split(SwingConstants.VERTICAL, false, virtualFile, true)
            fileEditorManager.openFile(virtualFile, true)
        }

        startSearch(project, artifactId, groupId)
    }

    private fun startSearch(project: Project, artifactId: String?, groupId: String?) {
        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).allEditors
                .filterIsInstance<MavenCentralUIEditor>()
                .firstOrNull()
                ?.search(artifactId, groupId, Layout.SHOW_PREVIEW)
        }
    }
}
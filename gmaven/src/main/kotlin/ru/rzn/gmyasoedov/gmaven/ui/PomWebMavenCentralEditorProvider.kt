package ru.rzn.gmyasoedov.gmaven.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.ui.jcef.JBCefApp
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import kotlin.io.path.absolutePathString

class PomWebMavenCentralEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        val path = file.toNioPathOrNull()?.absolutePathString() ?: return false
        val contains = CachedModuleDataService.getDataHolder(project).activeConfigPaths.contains(path)
        println("!!! $contains $path")
        return contains
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val fileEditor = TextEditorProvider.getInstance().createEditor(project, file)
        if (!JBCefApp.isSupported()) return fileEditor

        return (fileEditor as? TextEditor)?.let {
            MavenCentralUIEditor(fileEditor, MavenCentralCefBrowser(file))
        } ?: fileEditor
    }

    override fun getEditorTypeId() = POM_EDITOR_TYPE_ID

    @Suppress("UnstableApiUsage")
    override fun getPolicy() = FileEditorPolicy.HIDE_OTHER_EDITORS
}

const val POM_EDITOR_TYPE_ID = "gmaven.pom.web.editor"
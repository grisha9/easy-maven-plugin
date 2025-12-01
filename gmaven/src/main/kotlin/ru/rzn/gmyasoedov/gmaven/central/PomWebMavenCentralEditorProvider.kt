package ru.rzn.gmyasoedov.gmaven.central

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp

const val EASY_MAVEN_CENTRAL_SEARCH = "easyMavenCentralSearch.xml"

class PomWebMavenCentralEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
//        val path = file.toNioPathOrNull()?.absolutePathString() ?: return false
//        val contains = CachedModuleDataService.getDataHolder(project).activeConfigPaths.contains(path)
        //return contains && JBCefApp.isSupported()
        return file.name == EASY_MAVEN_CENTRAL_SEARCH && JBCefApp.isSupported()
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val fileEditor = TextEditorProvider.getInstance().createEditor(project, file)
        if (!JBCefApp.isSupported()) return fileEditor

        val mavenCentralUIEditor = (fileEditor as? TextEditor)?.let { MavenCentralUIEditor(fileEditor) }
        // mavenCentralUIEditor?.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR)
        return mavenCentralUIEditor ?: fileEditor
    }

    override fun getEditorTypeId() = POM_EDITOR_TYPE_ID

    @Suppress("UnstableApiUsage")
    override fun getPolicy() = FileEditorPolicy.HIDE_OTHER_EDITORS
}

const val POM_EDITOR_TYPE_ID = "gmaven.pom.web.editor"
package ru.rzn.gmyasoedov.gmaven.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.util.Key

class MavenCentralUIEditor(textEditor: TextEditor, preview: MavenCentralCefBrowser) :
    TextEditorWithPreview(textEditor, preview, "Maven Central - Easy Maven Editor", DEFAULT_LAYOUT) {


    init {
        textEditor.putUserData(PARENT_EDITOR_KEY, this)
        preview.putUserData(PARENT_EDITOR_KEY, this)
    }

    fun showPreview(anchor: String = "", layout: Layout = DEFAULT_LAYOUT) {
        if (getLayout() != layout) {
            setLayout(layout = layout)
        }

        val browser = previewEditor as? MavenCentralCefBrowser ?: return
        browser.loadHtml(anchor)
    }


    companion object {
        fun from(editor: FileEditor) =
            when (editor) {
                is MavenCentralUIEditor -> editor
                else -> editor.getUserData(PARENT_EDITOR_KEY)
            }

        val PARENT_EDITOR_KEY = Key.create<MavenCentralUIEditor>("parentEditorKey")

        private val DEFAULT_LAYOUT = Layout.SHOW_EDITOR
    }

}
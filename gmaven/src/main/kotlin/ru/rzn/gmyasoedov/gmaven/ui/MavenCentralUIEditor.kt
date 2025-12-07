package ru.rzn.gmyasoedov.gmaven.ui

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview

class MavenCentralUIEditor(textEditor: TextEditor, preview: MavenCentralCefBrowser) :
    TextEditorWithPreview(textEditor, preview, "Maven Central - Easy Maven Editor", Layout.SHOW_EDITOR) {

    fun search(searchString: String, layout: Layout = Layout.SHOW_EDITOR_AND_PREVIEW) {
        if (getLayout() != layout) {
            setLayout(layout = layout)
        }
        val urlToSearch = "https://central.sonatype.com/search?q=$searchString"
        val browser = previewEditor as? MavenCentralCefBrowser ?: return
        browser.loadHtml(urlToSearch)
    }

}
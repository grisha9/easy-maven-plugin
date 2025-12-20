package ru.rzn.gmyasoedov.gmaven.central

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import ru.rzn.gmyasoedov.gmaven.settings.advanced.DEFAULT_SEARCH_URL
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState
import ru.rzn.gmyasoedov.gmaven.util.MvnUtil.getMavenCentralSearchUrl

class MavenCentralUIEditor(textEditor: TextEditor) :
    TextEditorWithPreview(
        textEditor,
        MavenCentralCefBrowser(),
        "Maven Central - Easy Maven Editor",
        Layout.SHOW_PREVIEW
    ) {

    fun search(artifactId: String?, groupId: String?, layout: Layout = Layout.SHOW_PREVIEW) {
        if (getLayout() != layout) {
            setLayout(layout = layout)
        }
        val browser = previewEditor as? MavenCentralCefBrowser ?: return
        browser.loadHtml(getUrl(artifactId, groupId))
    }

    private fun getUrl(artifactId: String?, groupId: String?): String {
        val settingsState = MavenAdvancedSettingsState.getInstance()
        val url = getMavenCentralSearchUrl()
        if (url == DEFAULT_SEARCH_URL) {
            if (!artifactId.isNullOrEmpty()) {
                var urlToSearch = url + "search?q=$artifactId"
                if (settingsState.addGroupIdToSearch && !groupId.isNullOrEmpty()) {
                    urlToSearch += "&namespace=$groupId"
                }
                return urlToSearch
            }
        }
        return url
    }

    override fun onLayoutChange(oldValue: Layout?, newValue: Layout?) {
        super.onLayoutChange(oldValue, newValue)
        if (oldValue != null) {
            val browser = previewEditor as? MavenCentralCefBrowser ?: return
            val url = MavenAdvancedSettingsState.getInstance().searchUrl ?: DEFAULT_SEARCH_URL
            browser.loadHtml(url)
        }
    }
}
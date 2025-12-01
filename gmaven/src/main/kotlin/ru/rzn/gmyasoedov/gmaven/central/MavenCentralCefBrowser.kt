package ru.rzn.gmyasoedov.gmaven.central

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.jcef.JBCefBrowser
import org.jetbrains.concurrency.runAsync
import java.beans.PropertyChangeListener

class MavenCentralCefBrowser() : UserDataHolderBase(), FileEditor {

    private var browser = JBCefBrowser()

    fun loadHtml(urlString: String) {
        runAsync {
            browser.loadURL(urlString)
        }
    }

    override fun isModified() = false
    override fun isValid() = !browser.isDisposed
    override fun getComponent() = browser.component
    override fun getPreferredFocusedComponent() = browser.component
    override fun getName() = "Maven Central Easy Maven"

    override fun dispose() {
        Disposer.dispose(browser)
    }

    override fun setState(state: FileEditorState) {}
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

}
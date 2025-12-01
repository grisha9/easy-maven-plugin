package ru.rzn.gmyasoedov.gmaven.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import org.jetbrains.concurrency.runAsync
import java.beans.PropertyChangeListener
import java.util.*

class MavenCentralCefBrowser(file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val specKey = UUID.randomUUID()
    private var disposed = false
    private val browser: JBCefBrowser
    private val jbCefClient: JBCefClient = JBCefApp.getInstance().createClient()

    init {

        browser = JBCefBrowser.createBuilder()
            .setClient(jbCefClient)
            .build()

        loadHtml()
    }

    fun loadHtml(anchor: String = "") {
        runAsync {
            browser.loadURL("https://search.maven.org/")
            //browser.loadURL("https://mvnrepository.com/")
        }
    }

    override fun isModified() = false
    override fun isValid() = !disposed
    override fun getComponent() = browser.component
    override fun getPreferredFocusedComponent() = browser.component
    override fun getName() = "Maven Central Easy Maven"

    override fun dispose() {
        Disposer.dispose(browser)
        Disposer.dispose(jbCefClient)
        disposed = true
    }

    override fun setState(state: FileEditorState) {}
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

}
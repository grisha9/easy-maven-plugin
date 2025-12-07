package ru.rzn.gmyasoedov.gmaven.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import org.jetbrains.concurrency.runAsync
import java.beans.PropertyChangeListener

class MavenCentralCefBrowser() : UserDataHolderBase(), FileEditor {

    private var disposed = false

    @Volatile
    private var browser: JBCefBrowser? = null

    @Volatile
    private var jbCefClient: JBCefClient? = null

    init {
        jbCefClient = JBCefApp.getInstance().createClient()
        browser = JBCefBrowser.createBuilder()
            .setClient(jbCefClient)
            .build()
        println("createEditor")
        loadHtml("https://search.maven.org/")
    }

    fun loadHtml(urlString: String) {
        runAsync {
            browser?.loadURL(urlString)
        }
    }

    override fun isModified() = false
    override fun isValid() = !disposed
    override fun getComponent() = browser?.component!!
    override fun getPreferredFocusedComponent() = browser?.component
    override fun getName() = "Maven Central Easy Maven"

    override fun dispose() {
        browser?.let { Disposer.dispose(it) }
        jbCefClient?.let { Disposer.dispose(it) }
        disposed = true
    }

    override fun setState(state: FileEditorState) {}
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

}
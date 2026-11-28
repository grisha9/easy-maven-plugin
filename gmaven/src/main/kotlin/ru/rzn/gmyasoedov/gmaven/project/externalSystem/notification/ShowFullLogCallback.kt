package ru.rzn.gmyasoedov.gmaven.project.externalSystem.notification

import com.intellij.build.BuildContentManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.tree.TreePath

/**
 * Navigates from an error node of the build tree (Run/Build tool window) to the top (root) build node,
 * whose console contains the full Maven log.
 */
class ShowFullLogCallback(private val project: Project) : NotificationListener.Adapter() {

    override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
        if (project.isDisposed) return
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindows = TOOL_WINDOW_IDS.mapNotNull { toolWindowManager.getToolWindow(it) }
        val source = e.source as? Component
        // the tab (Run/Debug/Build) where the link was clicked
        val content = source?.let { findContent(toolWindows, it) }
            // e.g. link clicked in a balloon notification: use the selected tab of the Build tool window
            ?: toolWindows.find { it.id == BuildContentManager.TOOL_WINDOW_ID }?.contentManager?.selectedContent
            ?: return
        val tree = UIUtil.findComponentOfType(content.component, JTree::class.java) ?: return
        val contentManager = content.manager
        if (contentManager != null && contentManager.selectedContent != content) {
            contentManager.setSelectedContent(content)
        }
        selectMainLogNode(tree)
    }

    private fun findContent(toolWindows: List<ToolWindow>, source: Component): Content? {
        for (toolWindow in toolWindows) {
            toolWindow.contentManager.contents
                .find { SwingUtilities.isDescendingFrom(source, it.component) }
                ?.let { return it }
        }
        return null
    }

    private fun selectMainLogNode(tree: JTree) {
        val root = tree.model?.root ?: return
        val path = if (tree.isRootVisible) {
            TreePath(root)
        } else {
            val selected = tree.selectionPath
            if (selected != null && selected.pathCount > 1) {
                TreePath(arrayOf(selected.getPathComponent(0), selected.getPathComponent(1)))
            } else {
                tree.getPathForRow(0)
            }
        } ?: return
        tree.selectionPath = path
        tree.scrollPathToVisible(path)
    }

    companion object {
        const val ID = "#gmaven_show_full_log"

        private val TOOL_WINDOW_IDS = listOf(
            ToolWindowId.RUN, ToolWindowId.DEBUG, ToolWindowId.SERVICES, BuildContentManager.TOOL_WINDOW_ID
        )
    }
}

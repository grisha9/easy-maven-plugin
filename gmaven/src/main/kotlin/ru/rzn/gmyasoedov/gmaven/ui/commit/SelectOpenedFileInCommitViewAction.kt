package ru.rzn.gmyasoedov.gmaven.ui.commit

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.ide.SelectInTarget
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action for the Commit tool window toolbar: selects the file chosen in the Commit changes tree
 * in the Project tool window.
 *
 * Does the same as [com.intellij.ide.projectView.impl.SelectFileAction] in "Select In Project View" mode:
 * resolves the Project view `SelectInTarget` (`ProjectViewSelectInGroupTarget`) and calls `selectIn`
 * with the select-in context of the current selection.
 */
class SelectOpenedFileInCommitViewAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val target = project?.let { findProjectViewTarget(it) }
        if (project == null || target == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isVisible = true
        val context = getSelectInContext(e, project)
        e.presentation.isEnabled = context != null && target.canSelect(context)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = findProjectViewTarget(project) ?: return
        val context = getSelectInContext(e, project) ?: return

        val selectRunnable = Runnable {
            if (!project.isDisposed && target.canSelect(context)) {
                target.selectIn(context, true)
            }
        }
        // 1) open Project tool window (Alt + 1), 2) select the file there
        val projectToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
        if (projectToolWindow != null) {
            projectToolWindow.activate(selectRunnable, true)
        } else {
            selectRunnable.run()
        }
    }

    private fun findProjectViewTarget(project: Project): SelectInTarget? =
        SelectInManager.findSelectInTarget(ToolWindowId.PROJECT_VIEW, project)

    /**
     * The Commit changes tree (`ChangesListView`) provides [SelectInContext.DATA_KEY] for the exactly selected node
     * and [CommonDataKeys.VIRTUAL_FILE] for the selection, the toolbar uses the tree as its target component.
     */
    private fun getSelectInContext(e: AnActionEvent, project: Project): SelectInContext? {
        e.getData(SelectInContext.DATA_KEY)?.let { return it }
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isValid } ?: return null
        return FileSelectInContext(project, file)
    }
}

package ru.rzn.gmyasoedov.gmaven.project.dependencyanalyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.dependency.analyzer.AbstractDependencyAnalyzerAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.view.ExternalSystemNode
import com.intellij.openapi.externalSystem.view.ModuleNode
import com.intellij.openapi.externalSystem.view.ProjectNode
import com.intellij.openapi.module.Module
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils

class ToolbarDependencyAnalyzerAction : DependencyAnalyzerAction() {

    private val viewAction = ViewDependencyAnalyzerAction()

    override fun getSystemId(e: AnActionEvent) = e.getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID)

    override fun isEnabledAndVisible(e: AnActionEvent) = getSystemId(e) == GMavenConstants.SYSTEM_ID

    override fun setSelectedState(view: DependencyAnalyzerView, e: AnActionEvent) {
        viewAction.setSelectedState(view, e)
    }
}

class ViewDependencyAnalyzerAction : AbstractDependencyAnalyzerAction<ExternalSystemNode<*>>() {

    override fun getSystemId(e: AnActionEvent) = GMavenConstants.SYSTEM_ID

    override fun getSelectedData(e: AnActionEvent): ExternalSystemNode<*>? {
        return e.getData(ExternalSystemDataKeys.SELECTED_NODES)?.firstOrNull()
    }

    override fun getModule(e: AnActionEvent, selectedData: ExternalSystemNode<*>): Module? {
        val project = e.project ?: return null
        val findNode = selectedData.findNode(ModuleNode::class.java)?.data
            ?: selectedData.findNode(ProjectNode::class.java)?.effectiveRoot?.data
            ?: return null
        return MavenUtils.findIdeModule(project, findNode)
    }

    override fun getDependencyData(e: AnActionEvent, selectedData: ExternalSystemNode<*>) = null

    override fun getDependencyScope(e: AnActionEvent, selectedData: ExternalSystemNode<*>) = null
}
package ru.rzn.gmyasoedov.gmaven.ui

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.view.ExternalProjectsView
import com.intellij.openapi.externalSystem.view.ExternalSystemNode
import com.intellij.openapi.externalSystem.view.ExternalSystemViewContributor
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.DependencyAnalyzerData
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.LifecycleData
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.PluginData
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.ProfileData
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.view.*
import ru.rzn.gmyasoedov.gmaven.project.task.Phase
import ru.rzn.gmyasoedov.gmaven.project.task.Phase4
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings

class MavenExternalViewContributor : ExternalSystemViewContributor() {
    override fun getSystemId() = GMavenConstants.SYSTEM_ID

    override fun getKeys(): List<Key<*>> = listOf(
        LifecycleData.KEY, PluginData.KEY, ProfileData.KEY, DependencyAnalyzerData.KEY
    )

    override fun createNodes(
        externalProjectsView: ExternalProjectsView,
        dataNodes: MultiMap<Key<*>?, DataNode<*>?>
    ): List<ExternalSystemNode<*>> {
        val result: MutableList<ExternalSystemNode<*>> = SmartList()

        // add base lifecycle tasks
        val tasksNodes = dataNodes[LifecycleData.KEY]
        if (tasksNodes.isNotEmpty()) {
            createTasks(tasksNodes, result, externalProjectsView)
        }

        // add profiles
        val profilesNodes = dataNodes[ProfileData.KEY]
        if (profilesNodes.isNotEmpty()) {
            result.add(ProfileNodes(externalProjectsView, profilesNodes))
        }

        // add plugin tasks
        val pluginsNode = dataNodes[PluginData.KEY]
        if (pluginsNode.isNotEmpty()) {
            result.add(PluginNodes(externalProjectsView, replacePluginDataOnTaskData(pluginsNode)))
        }

        // add DA node
        val dependencyAnalyzerNodes = dataNodes[DependencyAnalyzerData.KEY]
        if (dependencyAnalyzerNodes.isNotEmpty()) {
            result.add(DependencyAnalyzerNode(externalProjectsView))
        }
        return result
    }

    private fun createTasks(
        tasksNodes: Collection<DataNode<*>?>, result: MutableList<ExternalSystemNode<*>>, view: ExternalProjectsView
    ) {
        val baseLifecycleData = tasksNodes.first()!!.data as LifecycleData

        val showAllPhases = MavenSettings.getInstance(view.project).isShowAllPhases
        if (!showAllPhases) {
            val lifecyclesDataNode = Registry.stringValue("gmaven.lifecycles").split(",").asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map {
                    LifecycleData(
                        SYSTEM_ID, it, baseLifecycleData.linkedExternalProjectPath, baseLifecycleData.isMaven4
                    )
                }
                .map { DataNode(LifecycleData.KEY, it, tasksNodes.first()!!.parent) }
                .toList()
            if (lifecyclesDataNode.isNotEmpty()) {
                result.add(LifecycleNodes(view, lifecyclesDataNode))
            }
            return
        }

        val arrayList = ArrayList<DataNode<TaskData>>()
        if (baseLifecycleData.isMaven4) {
            for (p in Phase4.entries) {
                val description = p.lifecycle.lifecycleName + ":" + p.phaseName
                val phaseData =
                    TaskData(SYSTEM_ID, p.phaseName, baseLifecycleData.linkedExternalProjectPath, description)
                phaseData.group = p.lifecycle.lifecycleName
                arrayList += DataNode(ProjectKeys.TASK, phaseData, tasksNodes.first()!!.parent)
            }
        } else {
            for (p in Phase.entries) {
                val description = p.lifecycle.lifecycleName + ":" + p.phaseName
                val phaseData =
                    TaskData(SYSTEM_ID, p.phaseName, baseLifecycleData.linkedExternalProjectPath, description)
                phaseData.group = p.lifecycle.lifecycleName
                arrayList += DataNode(ProjectKeys.TASK, phaseData, tasksNodes.first()!!.parent)
            }
        }
        result.add(MavenTasksNode(view, arrayList))
    }

    private fun replacePluginDataOnTaskData(pluginsNode: Collection<DataNode<*>?>): List<DataNode<*>?> {
        return pluginsNode.asSequence()
            .filter { it?.data is PluginData }
            .map { toTaskNode(it as DataNode<PluginData>) }
            .toList()
    }

    private fun toTaskNode(dataNode: DataNode<PluginData>): DataNode<TaskData> {
        val data = dataNode.getData()
        val taskData = TaskData(GMavenConstants.SYSTEM_ID, data.name, data.linkedExternalProjectPath, data.description)
        taskData.group = data.group
        return DataNode(ProjectKeys.TASK, taskData, dataNode.parent)
    }

    override fun getDisplayName(node: DataNode<*>): String? {
        val profileData = node.data as? ProfileData ?: return null
        return profileData.name
    }
}

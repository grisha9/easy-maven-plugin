package ru.rzn.gmyasoedov.gmaven.project.task

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ProjectJdkNotFoundException
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.VisibleForTesting
import ru.rzn.gmyasoedov.gmaven.project.MavenProjectResolver
import ru.rzn.gmyasoedov.gmaven.project.getMavenHome
import ru.rzn.gmyasoedov.gmaven.server.GServerRequest
import ru.rzn.gmyasoedov.gmaven.server.runTasks
import ru.rzn.gmyasoedov.gmaven.settings.MavenExecutionSettings
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MavenTaskManager : ExternalSystemTaskManager<MavenExecutionSettings> {
    private val cancellationMap = ConcurrentHashMap<ExternalSystemTaskId, OSProcessHandler>()

    override fun executeTasks(
        projectPath: String,
        id: ExternalSystemTaskId,
        settings: MavenExecutionSettings,
        listener: ExternalSystemTaskNotificationListener
    ) {
        val tasks = getTasks(settings)
        val workspace = settings.executionWorkspace
        val projectBuildFile = workspace.projectBuildFile
            ?: throw ExternalSystemException("project build file is empty")
        val buildPath = Path.of(workspace.subProjectBuildFile ?: projectBuildFile)

        val sdk = settings.jdkName?.let { ExternalSystemJdkUtil.getJdk(null, it) }
            ?: throw ProjectJdkNotFoundException() //InvalidJavaHomeException
        val mavenHome = getMavenHome(settings, id)
        try {
            val request = GServerRequest(id, buildPath, mavenHome, sdk, settings, listener = listener)
            runTasks(request, tasks) { cancellationMap[id] = it }
        } finally {
            cancellationMap.remove(id)
        }
    }

    private fun getTasks(settings: MavenExecutionSettings): List<String> {
        val taskNames = settings.tasks
        val tasks = if (Registry.stringValue("gmaven.lifecycles").contains(" ")) {
            taskNames.flatMap { it.split(" ") }
        } else {
            taskNames
        }
        if (taskNames.size < 2) return taskNames
        return if (settings.executionWorkspace.isMaven4) prepareTaskOrderMvn4(tasks) else prepareTaskOrder(tasks)
    }

    override fun cancelTask(id: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
        MavenProjectResolver.cancelTask(id, cancellationMap)
        return true
    }

    @VisibleForTesting
    fun prepareTaskOrder(taskNames: List<String>): List<String> {
        val phaseTasks = TreeMap<Int, String>()
        val otherTasks = mutableListOf<String>()
        for (taskName in taskNames) {
            val phase = Phase.find(taskName)
            if (phase != null) phaseTasks[phase.ordinal] = phase.phaseName else otherTasks.add(taskName)
        }
        return phaseTasks.values + otherTasks
    }

    @VisibleForTesting
    fun prepareTaskOrderMvn4(taskNames: List<String>): List<String> {
        val phaseTasks = TreeMap<Int, String>()
        val otherTasks = mutableListOf<String>()
        for (taskName in taskNames) {
            val phase = Phase4.find(taskName)
            if (phase != null) phaseTasks[phase.ordinal] = phase.phaseName else otherTasks.add(taskName)
        }
        return phaseTasks.values + otherTasks
    }
}
package ru.rzn.gmyasoedov.gmaven.project.task

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState
import com.intellij.openapi.externalSystem.service.execution.ProjectJdkNotFoundException
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.VisibleForTesting
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.project.MavenProjectResolver
import ru.rzn.gmyasoedov.gmaven.project.getMavenHome
import ru.rzn.gmyasoedov.gmaven.project.process.BaseMavenCommandLine
import ru.rzn.gmyasoedov.gmaven.server.GServerRequest
import ru.rzn.gmyasoedov.gmaven.server.runTasks
import ru.rzn.gmyasoedov.gmaven.settings.MavenExecutionSettings
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.exists

class MavenTaskManager : ExternalSystemTaskManager<MavenExecutionSettings> {
    private val cancellationMap = ConcurrentHashMap<ExternalSystemTaskId, OSProcessHandler>()

    override fun executeTasks(
        projectPath: String,
        id: ExternalSystemTaskId,
        settings: MavenExecutionSettings,
        listener: ExternalSystemTaskNotificationListener
    ) {
        val tasks = getTasks(settings)
        val sdk = settings.jdkName?.let { ExternalSystemJdkUtil.getJdk(null, it) }
            ?: throw ProjectJdkNotFoundException() //InvalidJavaHomeException
        val mavenHome = getMavenHome(settings)
        try {
            preProcessMvnAgrs(settings)
            val port = getDebugPort(settings)
            val buildPath = getProjectBuilPath(settings)
            val request = GServerRequest(id, buildPath, mavenHome, sdk, settings, listener = listener, debugPort = port)
            runTasks(request, tasks) { cancellationMap[id] = it }
        } finally {
            cancellationMap.remove(id)
        }
    }

    private fun getProjectBuilPath(settings: MavenExecutionSettings): Path {
        val workspace = settings.executionWorkspace
        val projectBuildFile = workspace.projectBuildFile
            ?: throw ExternalSystemException("project build file is empty")
        return Path.of(workspace.subProjectBuildFile ?: projectBuildFile)
    }

    private fun getDebugPort(settings: MavenExecutionSettings): Int? {
        return try {
            val klass = ExternalSystemRunnableState::class.java
            val field = klass.getDeclaredField("DEBUGGER_DISPATCH_ADDR_KEY")
            val value = field.get(null) as? Key<*> ?: return null
            if (value.let { settings.getUserData(it) } == null) return null
            MavenLog.LOG.debug("run debug task")
            settings.env[GMavenConstants.GMAVEN_ENV_DEBUG_PORT]?.toInt() ?: BaseMavenCommandLine.getDebugPort()
        } catch (_: Exception) {
            null
        }
    }

    private fun preProcessMvnAgrs(settings: MavenExecutionSettings) {
        //for test no need param - SkipTests
        if (settings.tasks.contains("test")) {
            settings.isSkipTests = false
        }
        //if exist custom path -f then need replace base path and reset -pl projects
        for ((index, arg) in settings.arguments.withIndex()) {
            if (arg.trim() == "-f") {
                extractAndSetCustomPomPath(settings, index + 1)
                settings.executionWorkspace.clearProjectData()
            }
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

    private fun extractAndSetCustomPomPath(settings: MavenExecutionSettings, index: Int) {
        try {
            val customPomPath = settings.arguments[index] ?: return
            if (!Path(customPomPath).exists()) return
            settings.executionWorkspace.projectBuildFile = customPomPath
            settings.executionWorkspace.subProjectBuildFile = customPomPath
        } catch (_: Exception) {
            MavenLog.LOG.warn("Custom pom file not found")
        }
    }
}

enum class MavenDebugType(val paramName: String) {
    TEST("maven.surefire.debug"),
    SPRING("spring-boot.run.jvmArguments"),
    EXEC("exec.args"), ;

    fun getValue(debugPort: Int): String {
        return "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:%s".format(debugPort)
    }
}
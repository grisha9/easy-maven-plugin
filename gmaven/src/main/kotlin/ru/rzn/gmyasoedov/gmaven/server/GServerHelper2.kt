@file:JvmName("GServerHelper2")

package ru.rzn.gmyasoedov.gmaven.server

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.*
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.notification.ShowFullLogCallback
import ru.rzn.gmyasoedov.gmaven.project.process.BaseMavenCommandLine
import ru.rzn.gmyasoedov.gmaven.project.process.GOSProcessHandler
import ru.rzn.gmyasoedov.gmaven.project.process.WslBaseMavenCommandLine
import ru.rzn.gmyasoedov.gmaven.server.wsl.WslPathTransformer
import ru.rzn.gmyasoedov.gmaven.settings.DistributionType
import ru.rzn.gmyasoedov.gmaven.settings.ProjectSettingsControlBuilder
import ru.rzn.gmyasoedov.gmaven.settings.ProjectSettingsControlBuilder.SnapshotUpdateType
import ru.rzn.gmyasoedov.gmaven.util.MavenPathUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenMapResult
import ru.rzn.gmyasoedov.serverapi.GServerUtils
import java.io.FileReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.name

fun getProjectModel(
    request: GServerRequest, processConsumer: ((process: GOSProcessHandler) -> Unit)? = null
): MavenMapResult {
    val wslDistribution = MavenPathUtil.getWsl(request.settings)
    if (wslDistribution != null) return getProjectModelWsl(request, wslDistribution, processConsumer)

    val baseMavenCommandLine = BaseMavenCommandLine(request)
    val commandLine = baseMavenCommandLine.getCommandLine()
    val resultFilePath = baseMavenCommandLine.resultFilePath
    setupImportParamsFromSettings(request, commandLine)

    printDebugCommandLine(request, commandLine)
    val processHandler = GOSProcessHandler(request, commandLine, processConsumer)
    val mavenResult = runMavenImport(processHandler, resultFilePath, request)
    if (mavenResult.pluginNotResolved) {
        throw ExternalSystemException(
            GBundle.message(
                "gmaven.model.reader.plugin.not.found", PLUGIN_GROUP_ID, PLUGIN_ARTIFACT_ID, getVersion()
            )
        )
    }
    return mavenResult
}

fun getProjectModelWsl(
    request: GServerRequest,
    wslDistribution: WSLDistribution,
    processConsumer: ((process: GOSProcessHandler) -> Unit)? = null
): MavenMapResult {
    val baseMavenCommandLine = WslBaseMavenCommandLine(request, wslDistribution)
    val commandLine = baseMavenCommandLine.getCommandLine()
    val resultFilePath = baseMavenCommandLine.resultFilePath
    setupImportParamsFromSettings(request, commandLine)

    printDebugCommandLine(request, commandLine)
    val processHandler = GOSProcessHandler(request, commandLine, processConsumer)
    val result = runMavenImport(processHandler, resultFilePath, request)
    return WslPathTransformer.transform(result, wslDistribution)
}

private fun printDebugCommandLine(request: GServerRequest, commandLine: GeneralCommandLine) {
    val parameterList = commandLine.parametersList.list.dropWhile { it != "-f" }
    val distributionType = request.settings.distributionSettings.type
    val stringBuilder = StringBuilder("")
    if (distributionType == DistributionType.WRAPPER) {
        stringBuilder.append("mvnw")
    } else if (distributionType == DistributionType.CUSTOM_MVND) {
        stringBuilder.append("mvnd")
    } else {
        stringBuilder.append("mvn")
    }
    for (parameter in parameterList) {
        stringBuilder.append(" $parameter")
    }
    stringBuilder.append(System.lineSeparator()).append(System.lineSeparator())
    request.listener?.onTaskOutput(request.taskId, stringBuilder.toString(), ProcessOutputType.STDOUT)
    if (ApplicationManager.getApplication().isUnitTestMode) {
        println(stringBuilder.toString())
    }
}

fun runTasks(
    request: GServerRequest,
    tasks: List<String>,
    processConsumer: ((process: GOSProcessHandler) -> Unit)? = null
) {
    val commandLine = getCommandLine(request)

    setupBaseParamsFromSettings(request, commandLine)
    tasks.forEach { commandLine.addParameter(it) }

    val processHandler = GOSProcessHandler(request, commandLine, processConsumer)
    processHandler.startAndWait()
    val exitCode = processHandler.exitCode
    if (exitCode != null && exitCode != 0) {
        throw RuntimeException("See full maven log in run tab")
    }
}

private fun getCommandLine(request: GServerRequest): GeneralCommandLine {
    val wsl = MavenPathUtil.getWsl(request.settings)
    return if (wsl == null)
        BaseMavenCommandLine(request, false).getCommandLine()
    else
        WslBaseMavenCommandLine(request, wsl, false).getCommandLine()
}

private fun setupBaseParamsFromSettings(request: GServerRequest, commandLine: GeneralCommandLine) {
    if (request.settings.isNonRecursive) {
        commandLine.addParameter("-N")
    }
    if (request.settings.snapshotUpdateType == SnapshotUpdateType.FORCE) {
        commandLine.addParameter("-U")
    }
    if (request.settings.snapshotUpdateType == SnapshotUpdateType.NEVER) {
        commandLine.addParameter("-nsu")
    }
    if (request.settings.isOfflineWork) {
        commandLine.addParameter("-o")
    }
    if (request.settings.isSkipTests) {
        commandLine.addParameter("-DskipTests")
    }
    if (request.settings.threadCount?.isNotBlank() == true) {
        commandLine.addParameters("-T", request.settings.threadCount!!)
    }
    if (request.settings.outputLevel == ProjectSettingsControlBuilder.OutputLevelType.QUITE) {
        commandLine.addParameter("-q")
    }
    if (request.settings.outputLevel == ProjectSettingsControlBuilder.OutputLevelType.DEBUG) {
        commandLine.addParameter("-X")
    }
    if (request.settings.outputLevel == ProjectSettingsControlBuilder.OutputLevelType.ERROR) {
        commandLine.addParameter("-e")
    }
    val profiles = request.settings.executionWorkspace.profilesData.map { it.toRawName() }
    if (profiles.isNotEmpty()) {
        commandLine.addParameters("-P", profiles.joinToString(separator = ","))
    }
    val projectList = request.settings.executionWorkspace.projectData.map { it.toRawName() }
    if (projectList.isNotEmpty()) {
        commandLine.addParameters("-pl", projectList.joinToString(separator = ","))
        commandLine.addParameters(getSubTaskArgs())
    }
    request.settings.arguments.forEach { commandLine.addParameter(it) }
}

private fun setupImportParamsFromSettings(request: GServerRequest, commandLine: GeneralCommandLine) {
    setupBaseParamsFromSettings(request, commandLine)
    request.settings.argumentsImport?.forEach { commandLine.addParameter(it) }
    val importTaskName = "${getModelReaderPluginGAV()}:" + (if (request.readOnly) "read" else "resolve")
    commandLine.addParameter(importTaskName)
    if (request.settings.executionWorkspace.incrementalProjectName != null) {
        commandLine.parametersList.addProperty("incremental", "true")
        commandLine.addParameters("-pl", request.settings.executionWorkspace.incrementalProjectName, "-am", "-amd")
    }
}

private fun runMavenImport(
    processSupport: GOSProcessHandler, resultFilePath: Path, request: GServerRequest,
): MavenMapResult {
    val mavenResult = runMavenImportInner(processSupport, resultFilePath, request)
    processExceptions(mavenResult.exceptions)
    return mavenResult
}

private fun runMavenImportInner(
    processSupport: GOSProcessHandler, resultFilePath: Path, request: GServerRequest
): MavenMapResult {
    var result: MavenMapResult? = null
    return try {
        processSupport.startAndWait()
        result = FileReader(resultFilePath.toFile(), StandardCharsets.UTF_8).use {
            Gson().fromJson(it, MavenMapResult::class.java)
        }
        if (processSupport.exitCode != 0 && result.exceptions.isEmpty()) {
            throw ExternalSystemException("Process terminated. See log")
        }
        return result
    } catch (e: Exception) {
        if (result?.pluginNotResolved == true) {
            return result
        }
        if (processSupport.exitCode != 0) {
            MavenLog.LOG.debug(e)
            throw ExternalSystemException("Process terminated. See log")
        }
        MavenLog.LOG.warn(e)
        GServerUtils.toResult(e)
    } finally {
        if (processSupport.exitCode != 0) {
            NioFiles.deleteRecursively(resultFilePath)
        } else if (!request.settings.isIncrementalSync && Registry.`is`("gmaven.process.remove.result.file")) {
            if (!resultFilePath.parent.name.equals("target", true)) NioFiles.deleteRecursively(resultFilePath)
        }
    }
}

private fun processExceptions(exceptions: List<String>) {
    if (exceptions.isEmpty()) return
    val errorString = exceptions.joinToString(System.lineSeparator())

    if (Registry.`is`("gmaven.show.full.log")) {
        val fixId = ShowFullLogCallback.ID
        val message = GBundle.message("gmaven.notification.full.log", errorString, fixId)
        throw ExternalSystemException(message, fixId)
    }
    throw ExternalSystemException(errorString)
}

private fun getSubTaskArgs(): List<String> {
    return try {
        val stringValue = Registry.stringValue("gmaven.subtask.args")
        if (StringUtil.isEmptyOrSpaces(stringValue)) return emptyList()
        stringValue.split(",").map { it.trim() }
    } catch (ignored: Exception) {
        emptyList()
    }
}
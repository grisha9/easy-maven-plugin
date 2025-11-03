package ru.rzn.gmyasoedov.gmaven.project.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.externalSystem.service.notification.callback.OpenProjectJdkSettingsCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.notification.OpenGMavenSettingsCallback
import ru.rzn.gmyasoedov.gmaven.project.process.BaseMavenCommandLine.Companion.setupMavenOpts
import ru.rzn.gmyasoedov.gmaven.server.GServerRequest
import ru.rzn.gmyasoedov.gmaven.util.MavenPathUtil.getExtClassesJarPathString
import ru.rzn.gmyasoedov.serverapi.GMavenServer
import java.nio.file.Path
import kotlin.io.path.name


class WslBaseMavenCommandLine(
    private val request: GServerRequest,
    private val wslDistribution: WSLDistribution,
    private val isImport: Boolean = true
) {
    private val workingDirectory = getWorkingDirectory(request)
    val resultFilePath: Path = detectResultFilePath()

    fun getCommandLine(): GeneralCommandLine {
        val commandLine = GeneralCommandLine()
        val javaHomeWsl = request.settings.javaHome?.let { WslPath.getDistributionByWindowsUncPath(it) }
        if (javaHomeWsl == null) {
            val message = "WSL " + ExternalSystemBundle.message(
                "external.system.project_jdk.not_specified", OpenProjectJdkSettingsCallback.ID
            )
            throw ExternalSystemJdkException(message, null, OpenProjectJdkSettingsCallback.ID)
        }
        commandLine.environment["JAVA_HOME"] = wslDistribution.getWslPath(request.settings.javaHome!!)
        val wslExtClassesJarPath = wslDistribution.getWslPath(getExtClassesJarPathString())!!
        val resultJsonFilePath = wslDistribution.getWslPath(resultFilePath)!!
        BaseMavenCommandLine.setupGmavenPluginsProperty(
            commandLine, request, isImport, resultJsonFilePath, wslExtClassesJarPath
        )
        setupMavenOpts(request, commandLine)
        setupProjectPath(commandLine, request)

        val windowsPath = getExeMavenPath()
        val mvnDistribution = WslPath.getDistributionByWindowsUncPath(windowsPath.toString())
        if (mvnDistribution == null) {
            val message = "WSL " + GBundle.message("gmaven.notification.mvn.not.found", OpenGMavenSettingsCallback.ID)
            throw ExternalSystemException(message, OpenGMavenSettingsCallback.ID)
        }
        commandLine.exePath = wslDistribution.getWslPath(windowsPath)!!
        commandLine.workDirectory = workingDirectory.toFile()
        commandLine.isRedirectErrorStream = true

        wslDistribution.patchCommandLine(commandLine, request.taskId.findProject(), WSLCommandLineOptions())
        return commandLine
    }

    private fun setupProjectPath(commandLine: GeneralCommandLine, request: GServerRequest) {
        commandLine.addParameters("-f", wslDistribution.getWslPath(request.projectPath))
    }

    private fun getExeMavenPath(): Path {
        val mavenPath = request.mavenPath
        if (mavenPath.name == "mvnd.sh") return mavenPath
        if (mavenPath.name == "mvnw") return mavenPath
        return BaseMavenCommandLine.getUnixPath(mavenPath)
    }

    private fun getWorkingDirectory(request: GServerRequest): Path {
        return if (request.projectPath.toFile().isDirectory) request.projectPath else request.projectPath.parent
    }

    private fun detectResultFilePath(): Path {
        val targetPath = workingDirectory.resolve("target")
        if (targetPath.toFile().exists()) {
            return targetPath.resolve(GMavenServer.GMAVEN_RESPONSE_POM_FILE)
        }
        return workingDirectory.resolve(GMavenServer.GMAVEN_RESPONSE_POM_FILE)
    }
}
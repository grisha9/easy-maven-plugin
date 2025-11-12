package ru.rzn.gmyasoedov.gmaven.project.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.net.NetUtils
import ru.rzn.gmyasoedov.gmaven.extensionpoints.plugin.MavenCompilerFullImportPlugin
import ru.rzn.gmyasoedov.gmaven.extensionpoints.plugin.MavenFullImportPlugin
import ru.rzn.gmyasoedov.gmaven.server.GServerRequest
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.util.MavenPathUtil.getExtClassesJarPathString
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import ru.rzn.gmyasoedov.serverapi.GMavenServer
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name


class BaseMavenCommandLine(private val request: GServerRequest, private val isImport: Boolean = true) {
    private val workingDirectory = getWorkingDirectory(request)
    val resultFilePath: Path = detectResultFilePath()

    //QualityToolProcessHandler
    fun getCommandLine(): GeneralCommandLine {
        val commandLine = GeneralCommandLine()
        commandLine.environment["JAVA_HOME"] = request.settings.javaHome
        setupGmavenPluginsProperty(
            commandLine, request, isImport, resultFilePath.absolutePathString(), getExtClassesJarPathString()
        )
        setupMavenOpts(request, commandLine)
        setupProjectPath(commandLine, request)
        setupDebugParam(commandLine, request)

        commandLine.exePath = getExeMavenPath().absolutePathString()
        commandLine.workDirectory = workingDirectory.toFile()
        commandLine.isRedirectErrorStream = true

        return commandLine
    }

    private fun setupProjectPath(commandLine: GeneralCommandLine, request: GServerRequest) {
        commandLine.addParameters("-f", request.projectPath.absolutePathString())
    }

    private fun getExeMavenPath(): Path {
        val mavenPath = request.mavenPath
        if (mavenPath.name == "mvnw" || mavenPath.name == "mvnw.cmd") return mavenPath
        if (mavenPath.name == "mvnd.sh" || mavenPath.name == "mvnd.cmd") return mavenPath
        return if (SystemInfo.isWindows) getWinPath(mavenPath) else getUnixPath(mavenPath)
    }

    private fun getWinPath(mavenPath: Path): Path {
        val basePath = mavenPath.resolve("bin")
        if (basePath.resolve("mvnd.cmd").exists()) {
            return basePath.resolve("mvnd.cmd")
        }
        if (basePath.resolve("mvn.cmd").exists()) {
            return basePath.resolve("mvn.cmd")
        }
        return basePath.resolve("mvn.bat")
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

    companion object {

        fun setupMavenOpts(request: GServerRequest, commandLine: GeneralCommandLine) {
            var mavenOpts = System.getenv("MAVEN_OPTS") ?: ""
            if (request.settings.jvmArguments.isNotEmpty()) {
                mavenOpts += ParametersListUtil.join(request.settings.jvmArguments)
            }
            if (mavenOpts.isNotBlank()) {
                commandLine.environment["MAVEN_OPTS"] = mavenOpts
            }
            val project = request.taskId.findProject() ?: return
            if (MavenSettings.getInstance(project).isColoredSupport) {
                commandLine.parametersList.addProperty("style.color", "always")
                commandLine.parametersList.addProperty("jansi.passthrough", "true")
            }
        }

        fun getUnixPath(mavenPath: Path): Path {
            val basePath = mavenPath.resolve("bin")
            if (basePath.resolve("mvnd.sh").exists()) {
                return basePath.resolve("mvnd.sh")
            }
            if (basePath.resolve("mvn").exists()) {
                return basePath.resolve("mvn")
            }
            return basePath.resolve("mvn.sh")
        }

        private fun setupDebugParam(commandLine: GeneralCommandLine, request: GServerRequest) {
            if (Registry.`is`("gmaven.process.jsonPrettyPrinting")) {
                commandLine.parametersList.addProperty("jsonPrettyPrinting", "true")
            }
            //force debug mode via Registry key
            if (Registry.`is`("gmaven.server.debug")) {
                val debugPort = getDebugPort()
                MavenLog.LOG.debug("run force debug")
                commandLine.environment["MAVEN_DEBUG_OPTS"] =
                    "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=*:$debugPort"
                return
            }
            //debug param already exists from any task
            if (request.settings.arguments.any { it.contains("-agentlib:") }) return
            //regular maven task debug
            if (request.debugPort == null) return
            commandLine.parametersList.addProperty("jsonPrettyPrinting", "true")
            commandLine.environment["MAVEN_DEBUG_OPTS"] =
                "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=*:${request.debugPort}"
        }

        fun setupGmavenPluginsProperty(
            params: GeneralCommandLine,
            request: GServerRequest,
            isImport: Boolean,
            resultFilePath: String,
            extClassesJarPath: String
        ) {
            if (!isImport) return
            params.parametersList.addProperty("maven.ext.class.path", extClassesJarPath)
            params.parametersList.addProperty("resultAsTree", "true")
            params.parametersList.addProperty("resultFilePath", resultFilePath)
            if (!request.settings.isShowPluginNodes) {
                params.parametersList.addProperty("allPluginsInfo", "false")
            }
            val extensionList = MavenFullImportPlugin.EP_NAME.extensionList
            val pluginsForImport: MutableList<String> = ArrayList(extensionList.size)
            val pluginsForResolve: MutableList<String> = ArrayList(extensionList.size)
            for (plugin in extensionList) {
                pluginsForImport.add(plugin.key)
                if ((plugin as? MavenCompilerFullImportPlugin)?.resolvePlugin() == true) {
                    pluginsForResolve.add(plugin.key)
                }
            }

            if (pluginsForImport.isNotEmpty()) {
                params.addParameter("-DprocessingPluginGAIds=${createListParameter(pluginsForImport)}")
                params.addParameter("-DresolvedPluginGAIds=${createListParameter(pluginsForResolve)}")
            }
        }

        private fun createListParameter(plugins: MutableList<String>) = plugins.joinToString(",")

        fun getDebugPort(): Int {
            try {
                return NetUtils.findAvailableSocketPort()
            } catch (e: IOException) {
                MavenLog.LOG.warn(e)
            }
            return 5005
        }
    }
}
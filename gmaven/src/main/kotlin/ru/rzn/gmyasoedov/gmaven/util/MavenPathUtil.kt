package ru.rzn.gmyasoedov.gmaven.util

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PathUtil
import org.jetbrains.annotations.TestOnly
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.DEPENDENCY_TREE_EVENT_SPY_CLASS
import ru.rzn.gmyasoedov.gmaven.settings.MavenExecutionSettings

object MavenPathUtil {

    fun getExtClassesJarPathString(): String {
        if (ApplicationManager.getApplication().isUnitTestMode && testEventSpyJarPath.isNotEmpty()) {
            return testEventSpyJarPath
        }

        return PathUtil.getJarPathForClass(Class.forName(DEPENDENCY_TREE_EVENT_SPY_CLASS))
    }

    fun getWsl(settings: MavenExecutionSettings): WSLDistribution? {
        return if (SystemInfo.isWindows && Registry.`is`("gmaven.wsl.support"))
            WslPath.getDistributionByWindowsUncPath(settings.executionWorkspace.externalProjectPath) else null
    }

    fun getWsl(path: String): WSLDistribution? {
        return if (SystemInfo.isWindows && Registry.`is`("gmaven.wsl.support"))
            WslPath.getDistributionByWindowsUncPath(path) else null
    }

    fun checkOnWsl(path: String): String {
        val wslDistribution = if (SystemInfo.isWindows && Registry.`is`("gmaven.wsl.support"))
            WslPath.getDistributionByWindowsUncPath(path) else null
        wslDistribution ?: return path

        return wslDistribution.getWslPath(path)!!
    }

    @TestOnly
    fun getEventSpyJarPathForTest(): String {
        return PathUtil.getJarPathForClass(Class.forName(DEPENDENCY_TREE_EVENT_SPY_CLASS))
    }

    //@TestOnly
    var testEventSpyJarPath: String = ""
}
package ru.rzn.gmyasoedov.gmaven.junit

import com.intellij.execution.JUnitPatcher
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.module.Module
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState

class JUnitRunConfigurationPatcher : JUnitPatcher() {
    override fun patchJavaParameters(module: Module?, javaParameters: JavaParameters?) {
        super.patchJavaParameters(module, javaParameters)
        javaParameters ?: return
        val settings = MavenAdvancedSettingsState.getInstance()
        if (!settings.junitJvmArgs.isNullOrEmpty()) {
            javaParameters.vmParametersList.addParametersString(settings.junitJvmArgs)
        }
        if (!settings.junitEnv.isNullOrEmpty()) {
            val junitEnv = mutableMapOf<String, String>()
            val params = settings.junitEnv?.split(";") ?: emptyList()
            for (param in params) {
                val envPair = param.split("=")
                if (envPair.size != 2) continue
                junitEnv[envPair[0]] = envPair[1]
            }
            if (junitEnv.isNotEmpty()) {
                val env = javaParameters.env
                junitEnv.putAll(env)
                javaParameters.env = junitEnv
            }
        }
    }
}
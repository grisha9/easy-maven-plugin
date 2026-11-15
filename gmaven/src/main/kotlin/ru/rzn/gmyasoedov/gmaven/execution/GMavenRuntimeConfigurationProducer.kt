package ru.rzn.gmyasoedov.gmaven.execution

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemRunConfigurationProducer

class GMavenRuntimeConfigurationProducer : AbstractExternalSystemRunConfigurationProducer() {
    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationTypeUtil.findConfigurationType(GMavenExternalTaskConfigurationType::class.java).factory
}

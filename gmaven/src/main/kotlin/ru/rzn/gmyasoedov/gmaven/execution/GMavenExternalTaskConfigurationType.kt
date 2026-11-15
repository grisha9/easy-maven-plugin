package ru.rzn.gmyasoedov.gmaven.execution

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType

import com.intellij.openapi.project.Project
import ru.rzn.gmyasoedov.gmaven.GMavenConstants

class GMavenExternalTaskConfigurationType : AbstractExternalSystemTaskConfigurationType(GMavenConstants.SYSTEM_ID) {

    override fun getHelpTopic(): String = "reference.dialogs.rundebug.GMavenRunConfiguration"

    override fun getConfigurationFactoryId(): String = GMavenConstants.SYSTEM_ID.readableName

    override fun doCreateConfiguration(
        externalSystemId: ProjectSystemId,
        project: Project,
        factory: ConfigurationFactory,
        name: String
    ): GMavenRunConfiguration = GMavenRunConfiguration(project, factory, name)

    override fun isDumbAware(): Boolean = true

    override fun isEditableInDumbMode(): Boolean = true
}

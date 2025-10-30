package ru.rzn.gmyasoedov.gmaven.server

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.projectRoots.Sdk
import ru.rzn.gmyasoedov.gmaven.settings.MavenExecutionSettings
import java.nio.file.Path

//todo remove. use MavenExecutionSettings
data class GServerRequest(
    val taskId: ExternalSystemTaskId,
    val projectPath: Path,
    val mavenPath: Path,
    val sdk: Sdk,
    val settings: MavenExecutionSettings,
    val installGMavenPlugin: Boolean = false,
    val listener: ExternalSystemTaskNotificationListener? = null,
    val readOnly: Boolean = false,
    val debugPort: Int? = null,
)


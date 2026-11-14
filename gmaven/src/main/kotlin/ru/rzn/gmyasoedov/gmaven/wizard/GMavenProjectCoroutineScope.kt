package ru.rzn.gmyasoedov.gmaven.wizard

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Project-level coroutine scope provided by the platform.
 * It is cancelled automatically when the project is closed or the plugin is unloaded.
 */
@Service(Service.Level.PROJECT)
class GMavenProjectCoroutineScope(val coroutineScope: CoroutineScope) {

    companion object {
        @JvmStatic
        fun getInstance(project: Project): CoroutineScope = project.service<GMavenProjectCoroutineScope>().coroutineScope
    }
}

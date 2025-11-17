package ru.rzn.gmyasoedov.gmaven.project.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.server.GServerRequest

class GOSProcessHandler(
    private val request: GServerRequest,
    commandLine: GeneralCommandLine,
    private val processConsumer: ((process: GOSProcessHandler) -> Unit)? = null
) : OSProcessHandler(commandLine) {

    init {
        if (request.debugPort != null && request.settings.env[GMavenConstants.GMAVEN_ENV_DEBUG_PORT] != null) {
            addListeningRemoteAddress(request.debugPort)
        }
        addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                super.onTextAvailable(event, outputType)
                val text = StringUtil.notNullize(event.text)
                if (Registry.`is`("gmaven.server.debug")) {
                    println(text)
                }
                request.listener?.onTaskOutput(request.taskId, text, ProcessOutputType.STDOUT)
                if (request.debugPort != null && text.contains("T E S T S")) {
                    addListeningRemoteAddress(request.debugPort)
                }
            }
        })
    }

    private fun addListeningRemoteAddress(debugPort: Int) {
        request.listener?.onTaskOutput(
            request.taskId,
            "Easy Maven: Listening for transport dt_socket at address: $debugPort" + System.lineSeparator(),
            ProcessOutputType.STDOUT
        )
    }

    fun startAndWait() {
        processConsumer?.let { it(this) }
        startNotify()
        waitFor()
    }
}
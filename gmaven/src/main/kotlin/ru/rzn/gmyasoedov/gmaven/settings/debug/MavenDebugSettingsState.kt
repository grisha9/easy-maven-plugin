package ru.rzn.gmyasoedov.gmaven.settings.debug

import com.intellij.openapi.components.*
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference

const val DEFAULT_DEBUG_VALUE = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:*"

@Service(Service.Level.APP)
@State(name = "EasyMavenDebugSettings", storages = [Storage("easyMavenDebugSettings.xml")])
class MavenDebugSettingsState : PersistentStateComponent<MavenDebugSettingsState>, BaseState() {
    var surefireDebugParamName by string(MavenDebugType.TEST.paramName)
    var surefireDebugValue by string(DEFAULT_DEBUG_VALUE)

    var springDebugParamName by string(MavenDebugType.SPRING.paramName)
    var springDebugValue by string(DEFAULT_DEBUG_VALUE)

    var execDebugParamName by string(MavenDebugType.EXEC.paramName)
    var execDebugValue by string(DEFAULT_DEBUG_VALUE)

    override fun getState(): MavenDebugSettingsState = this

    override fun loadState(state: MavenDebugSettingsState) = this.copyFrom(state)

    companion object {
        fun getInstance(): MavenDebugSettingsState = service()
    }
}

enum class MavenDebugType(val paramName: String) {
    TEST("maven.surefire.debug"),
    SPRING("spring-boot.run.jvmArguments"),
    EXEC("exec.args"), ;

    fun getName(): String {
        val state = MavenDebugSettingsState.getInstance()
        return when (this) {
            TEST -> state.surefireDebugParamName
            SPRING -> state.springDebugParamName
            EXEC -> state.execDebugParamName
        } ?: throw RuntimeException("No debug value in 'Easy Maven Debug' settings")
    }

    fun getDebugParams(debugPort: Int) = getDebugParams(debugPort, MavenDebugSettingsState.getInstance())

    @VisibleForTesting
    fun getDebugParams(debugPort: Int, state: MavenDebugSettingsState): Pair<Int, String> {
        val defaultPort = AtomicReference(debugPort)
        val exception = RuntimeException("No debug value in 'Easy Maven Debug' settings")
        val value = when (this) {
            TEST -> state.surefireDebugValue
            SPRING -> state.springDebugValue
            EXEC -> state.execDebugValue
        } ?: throw exception
        val debugParams = value.substringAfter("-agentlib:jdwp=").split(",")
        val processedParams = debugParams.map { processPort(it, defaultPort) }
        return defaultPort.get() to "-agentlib:jdwp=" + processedParams.joinToString(",")
    }

    private fun processPort(param: String, defaultPort: AtomicReference<Int>): String {
        if (!param.startsWith("address")) return param
        if (!param.contains("=")) return param
        val substringValue = param.substringAfter("=")
        //no ":" delimiter
        if (!substringValue.contains(":")) {
            if (substringValue.trim() == "*") {
                return param.substringBefore("=") + "=" + defaultPort.get()
            } else {
                defaultPort.set(substringValue.trim().toInt())
            }
            return param
        }

        val portParam = param.substringAfter(":")
        if (portParam.trim() == "*") {
            return param.substringBefore(":") + ":" + defaultPort.get()
        } else {
            defaultPort.set(portParam.trim().toInt())
        }
        return param
    }
}
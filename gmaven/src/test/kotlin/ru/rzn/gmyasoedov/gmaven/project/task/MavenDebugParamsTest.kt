package ru.rzn.gmyasoedov.gmaven.project.task

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.rzn.gmyasoedov.gmaven.settings.debug.MavenDebugSettingsState
import ru.rzn.gmyasoedov.gmaven.settings.debug.MavenDebugType

class MavenDebugParamsTest {
    @Test
    fun testSetPortLocalhost() {
        val state = MavenDebugSettingsState()
        state.surefireDebugValue = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:*"
        val params = MavenDebugType.TEST.getDebugParams(5005, state)
        assertEquals(5005, params.first)
        assertEquals("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:5005", params.second)
    }

    @Test
    fun testSetPort() {
        val state = MavenDebugSettingsState()
        state.surefireDebugValue = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:*"
        val params = MavenDebugType.TEST.getDebugParams(5005, state)
        assertEquals(5005, params.first)
        assertEquals("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005", params.second)
    }

    @Test
    fun testPortExistLocalhost() {
        val state = MavenDebugSettingsState()
        state.surefireDebugValue = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:5001"
        val params = MavenDebugType.TEST.getDebugParams(5005, state)
        assertEquals(5001, params.first)
        assertEquals("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:5001", params.second)
    }

    @Test
    fun testPortExist() {
        val state = MavenDebugSettingsState()
        state.surefireDebugValue = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5001"
        val params = MavenDebugType.TEST.getDebugParams(5005, state)
        assertEquals(5001, params.first)
        assertEquals("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5001", params.second)
    }

    @Test
    fun testSetPortFirstPosition() {
        val state = MavenDebugSettingsState()
        state.surefireDebugValue = "-agentlib:jdwp=address=*:*,transport=dt_socket,server=y,suspend=y"
        val params = MavenDebugType.TEST.getDebugParams(5005, state)
        assertEquals(5005, params.first)
        assertEquals("-agentlib:jdwp=address=*:5005,transport=dt_socket,server=y,suspend=y", params.second)
    }

    @Test
    fun testSetPortFirstPositionExist() {
        val state = MavenDebugSettingsState()
        state.surefireDebugValue = "-agentlib:jdwp=address=*:5001,transport=dt_socket,server=y,suspend=y"
        val params = MavenDebugType.TEST.getDebugParams(5005, state)
        assertEquals(5001, params.first)
        assertEquals("-agentlib:jdwp=address=*:5001,transport=dt_socket,server=y,suspend=y", params.second)
    }

    @Test
    fun testSetPortOnly() {
        val state = MavenDebugSettingsState()
        state.surefireDebugValue = "-agentlib:jdwp=address=*,transport=dt_socket,server=y,suspend=y"
        val params = MavenDebugType.TEST.getDebugParams(5005, state)
        assertEquals(5005, params.first)
        assertEquals("-agentlib:jdwp=address=5005,transport=dt_socket,server=y,suspend=y", params.second)
    }

    @Test
    fun testSetPortOnlyExist() {
        val state = MavenDebugSettingsState()
        state.surefireDebugValue = "-agentlib:jdwp=address=5001,transport=dt_socket,server=y,suspend=y"
        val params = MavenDebugType.TEST.getDebugParams(5005, state)
        assertEquals(5001, params.first)
        assertEquals("-agentlib:jdwp=address=5001,transport=dt_socket,server=y,suspend=y", params.second)
    }

    @Test
    fun testSetPortMiddlePosition() {
        val state = MavenDebugSettingsState()
        state.surefireDebugValue = "-agentlib:jdwp=transport=dt_socket,address=*:*,server=y,suspend=y"
        val params = MavenDebugType.TEST.getDebugParams(5005, state)
        assertEquals(5005, params.first)
        assertEquals("-agentlib:jdwp=transport=dt_socket,address=*:5005,server=y,suspend=y", params.second)
    }

    @Test
    fun testSetPortMiddlePositionExist() {
        val state = MavenDebugSettingsState()
        state.surefireDebugValue = "-agentlib:jdwp=transport=dt_socket,address=*:5001,server=y,suspend=y"
        val params = MavenDebugType.TEST.getDebugParams(5005, state)
        assertEquals(5001, params.first)
        assertEquals("-agentlib:jdwp=transport=dt_socket,address=*:5001,server=y,suspend=y", params.second)
    }
}
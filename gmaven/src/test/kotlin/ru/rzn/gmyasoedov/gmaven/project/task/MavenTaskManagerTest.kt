package ru.rzn.gmyasoedov.gmaven.project.task

import org.junit.Assert.assertEquals
import org.junit.Test

class MavenTaskManagerTest {

    @Test
    fun testOrder() {
        val taskManager = MavenTaskManager()
        var tasks = taskManager.prepareTaskOrder(listOf("clean", "package"))
        assertEquals(listOf("clean", "package"), tasks)

        tasks = taskManager.prepareTaskOrder(listOf("package", "clean"))
        assertEquals(listOf("clean", "package"), tasks)

        tasks = taskManager.prepareTaskOrder(listOf("package", "verify", "clean"))
        assertEquals(listOf("clean", "package", "verify"), tasks)

        tasks = taskManager.prepareTaskOrder(listOf("package", "verify", "clean", "install"))
        assertEquals(listOf("clean", "package", "verify", "install"), tasks)

        tasks = taskManager.prepareTaskOrder(listOf("deploy", "verify", "clean", "install"))
        assertEquals(listOf("clean", "verify", "install", "deploy"), tasks)

        tasks = taskManager.prepareTaskOrder(
            listOf("help:effective-pom", "-f", "/path/project/pom.xml", "initialize", "site", "clean")
        )
        assertEquals(listOf("clean", "initialize", "site", "help:effective-pom", "-f", "/path/project/pom.xml"), tasks)
    }

    @Test
    fun testOrderMvn4() {
        val taskManager = MavenTaskManager()
        var tasks = taskManager.prepareTaskOrderMvn4(listOf("clean", "package"))
        assertEquals(listOf("clean", "package"), tasks)

        tasks = taskManager.prepareTaskOrderMvn4(listOf("package", "clean"))
        assertEquals(listOf("clean", "package"), tasks)

        tasks = taskManager.prepareTaskOrderMvn4(listOf("package", "verify", "clean"))
        assertEquals(listOf("clean", "package", "verify"), tasks)

        tasks = taskManager.prepareTaskOrderMvn4(listOf("package", "verify", "clean", "install"))
        assertEquals(listOf("clean", "package", "verify", "install"), tasks)

        tasks = taskManager.prepareTaskOrderMvn4(listOf("deploy", "verify", "clean", "install"))
        assertEquals(listOf("clean", "verify", "install", "deploy"), tasks)

        tasks = taskManager.prepareTaskOrderMvn4(
            listOf("after:all", "build", "before:test-sources", "after:validate", "before:all")
        )
        assertEquals(listOf("before:all", "after:validate", "before:test-sources", "build", "after:all"), tasks)

        tasks = taskManager.prepareTaskOrderMvn4(
            listOf("clean", "help:effective-pom", "-f", "/path/project/pom.xml", "initialize", "site")
        )
        assertEquals(listOf("clean", "initialize", "site", "help:effective-pom", "-f", "/path/project/pom.xml"), tasks)
    }
}
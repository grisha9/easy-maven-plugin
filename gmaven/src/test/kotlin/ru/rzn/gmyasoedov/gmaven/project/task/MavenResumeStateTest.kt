package ru.rzn.gmyasoedov.gmaven.project.task

import org.junit.Assert.*
import org.junit.Test

class MavenResumeStateTest {

    @Test
    fun testParseMaven3ResumeFrom() {
        val text = "[ERROR] \n[ERROR] After correcting the problems, you can resume the build with the command\n" +
                "[ERROR]   mvn <args> -rf :module-b\n"
        assertEquals(listOf("-rf", ":module-b"), MavenResumeState.parseResumeArgs(text))
    }

    @Test
    fun testParseMaven3ResumeFromWithAnsiColors() {
        val text = "\u001B[1;31mERROR\u001B[m]   \u001B[1mmvn <args> -rf :module-c\u001B[m\n"
        assertEquals(listOf("-rf", ":module-c"), MavenResumeState.parseResumeArgs(text))
    }

    @Test
    fun testParseMaven4Resume() {
        val text = "[ERROR]   mvn [args] -r\n"
        assertEquals(listOf("-r"), MavenResumeState.parseResumeArgs(text))
    }

    @Test
    fun testParseNoResumeHint() {
        assertNull(MavenResumeState.parseResumeArgs("[INFO] BUILD FAILURE\n"))
        assertNull(MavenResumeState.parseResumeArgs("[ERROR] -> [Help 1]\n"))
    }

    @Test
    fun testHasResumeOption() {
        assertTrue(MavenResumeState.hasResumeOption(listOf("-DskipTests", "-rf", ":a")))
        assertTrue(MavenResumeState.hasResumeOption(listOf("--resume-from=:a")))
        assertTrue(MavenResumeState.hasResumeOption(listOf("-r")))
        assertFalse(MavenResumeState.hasResumeOption(listOf("-DskipTests", "-U")))
    }
}

package ru.rzn.gmyasoedov.gmaven.project.task

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import org.jetbrains.annotations.VisibleForTesting

/**
 * Resume state of a Maven run configuration execution.
 *
 * Instance is stored in the [com.intellij.execution.runners.ExecutionEnvironment] user data, which is reused
 * by the 'Rerun' action of the run tool window. So if the previous run failed, the next rerun in the same
 * tab continues the build with Maven resume options ('-rf :module' for Maven 3 or '-r' for Maven 4)
 * instead of starting the whole build from scratch.
 */
class MavenResumeState {
    @Volatile
    var resumeArgs: List<String> = emptyList()

    companion object {
        @JvmField
        val KEY: Key<MavenResumeState> = Key.create("GMAVEN_RESUME_STATE")

        @JvmStatic
        fun getOrCreate(holder: UserDataHolderEx): MavenResumeState {
            return holder.putUserDataIfAbsent(KEY, MavenResumeState())
        }

        private val ANSI_ESCAPE = Regex("\u001B\\[[;\\d]*[ -/]*[@-~]")

        /** Maven hint: "mvn <args> -rf :module" (Maven 3/4) or "mvn <args> -r" (Maven 4). */
        private val RESUME_HINT = Regex("\\bmvn\\b.*\\s(?:(-rf)\\s+(\\S+)|(-r))\\s*$", RegexOption.MULTILINE)

        private val RESUME_OPTIONS = setOf("-rf", "--resume-from", "-r", "--resume")

        @VisibleForTesting
        @JvmStatic
        fun parseResumeArgs(text: String): List<String>? {
            val clearText = ANSI_ESCAPE.replace(text, "")
            val match = RESUME_HINT.findAll(clearText).lastOrNull() ?: return null
            val resumeFrom = match.groupValues[2]
            return if (resumeFrom.isNotEmpty()) listOf("-rf", resumeFrom) else listOf("-r")
        }

        @JvmStatic
        fun hasResumeOption(arguments: List<String>): Boolean {
            return arguments.any { arg -> RESUME_OPTIONS.any { arg == it || arg.startsWith("$it=") } }
        }
    }
}

/** Collects Maven resume hint from the build output. */
class MavenResumeOutputListener : ProcessListener {
    @Volatile
    var resumeArgs: List<String> = emptyList()
        private set

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        val text = event.text ?: return
        if (!text.contains("mvn")) return
        MavenResumeState.parseResumeArgs(text)?.let { resumeArgs = it }
    }
}

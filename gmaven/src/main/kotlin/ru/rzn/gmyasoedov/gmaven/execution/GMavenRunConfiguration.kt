package ru.rzn.gmyasoedov.gmaven.execution

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.util.execution.ParametersListUtil
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.project.task.MavenResumeOutputListener
import ru.rzn.gmyasoedov.gmaven.project.task.MavenResumeState

class GMavenRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    ExternalSystemRunConfiguration(GMavenConstants.SYSTEM_ID, project, factory, name) {

    /**
     * The same execution environment is reused by the 'Rerun' action of the run tool window.
     * If the previous execution in this environment has failed, Maven resume options
     * ('-rf :module' / '-r') printed by Maven are added to the command line,
     * so the build continues from the failed module instead of starting from scratch.
     */
    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
        val resumeState = MavenResumeState.getOrCreate(env)
        val settings = getSettingsWithResumeArgs(resumeState.resumeArgs)
        val isStateForDebug = ToolWindowId.DEBUG == executor.id || ToolWindowId.DEBUG == executor.toolWindowId
        val runnableState = GMavenRunnableState(settings, project, isStateForDebug, this, env, resumeState)
        copyUserDataTo(runnableState)
        return runnableState
    }

    private fun getSettingsWithResumeArgs(resumeArgs: List<String>): ExternalSystemTaskExecutionSettings {
        val settings = settings
        if (resumeArgs.isEmpty()) return settings
        val scriptParameters = settings.scriptParameters.orEmpty()
        val currentArgs = ParametersListUtil.parse(scriptParameters, false, true)
        if (MavenResumeState.hasResumeOption(currentArgs)) return settings

        val resumeSettings = settings.clone()
        val resumeArgsString = ParametersListUtil.join(resumeArgs)
        resumeSettings.scriptParameters =
            if (scriptParameters.isBlank()) resumeArgsString else "$scriptParameters $resumeArgsString"
        return resumeSettings
    }

    private class GMavenRunnableState(
        settings: ExternalSystemTaskExecutionSettings,
        project: Project,
        debug: Boolean,
        configuration: ExternalSystemRunConfiguration,
        env: ExecutionEnvironment,
        private val resumeState: MavenResumeState,
    ) : ExternalSystemRunnableState(settings, project, debug, configuration, env) {

        override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
            val result = super.execute(executor, runner)
            val processHandler = result?.processHandler ?: return result

            val outputListener = MavenResumeOutputListener()
            processHandler.addProcessListener(outputListener)
            processHandler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    resumeState.resumeArgs = if (event.exitCode == 0) emptyList() else outputListener.resumeArgs
                }
            })
            return result
        }
    }
}

package ru.rzn.gmyasoedov.gmaven.execution

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.ide.actions.runAnything.RunAnythingAction.EXECUTOR_KEY
import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.ModuleContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.ProjectContext
import com.intellij.ide.actions.runAnything.RunAnythingUtil
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.ide.actions.runAnything.getPath
import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import icons.GMavenIcons
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.BASIC_PHASES
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import ru.rzn.gmyasoedov.gmaven.project.task.MavenCommandLineOptions
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class MavenRunAnythingProvider : RunAnythingCommandLineProvider() {

    override fun getIcon(value: String): Icon = GMavenIcons.MavenProject

    override fun getHelpGroupTitle(): String = SYSTEM_ID.readableName

    override fun getCompletionGroupTitle(): String = "Easy Maven Goals"

    override fun getHelpCommandPlaceholder(): String = "mvn <goal...> <--option-name...>"

    override fun getHelpCommand(): String = HELP_COMMAND

    override fun getHelpCommandAliases(): List<String> = SECONDARY_HELP_COMMANDS

    override fun getHelpIcon(): Icon = GMavenIcons.MavenProject

    override fun getMainListItem(dataContext: DataContext, value: String) =
        RunAnythingEasyMavenItem(getCommand(value), getIcon(value))

    override fun getExecutionContexts(dataContext: DataContext): List<RunAnythingContext> {
        if (CachedModuleDataService.getCurrentData().modules.isEmpty()) {
            return super.getExecutionContexts(dataContext).filterIsInstance<ProjectContext>()
        }
        return super.getExecutionContexts(dataContext).filter {
            it !is ModuleContext || !it.module.isSourceRoot()
        }
    }

    override fun suggestCompletionVariants(dataContext: DataContext, commandLine: CommandLine): Sequence<String> {
        if (MavenUtils.pluginEnabled(MavenUtils.INTELLIJ_MAVEN_PLUGIN_ID)) return emptySequence()

        val project = RunAnythingUtil.fetchProject(dataContext)
        if (CachedModuleDataService.getDataHolder(project).modules.isEmpty()) return emptySequence()

        val goalsVariants = completeGoals().sorted()
        val longOptionsVariants = completeOptions(isLongOpt = true).sorted()
        val shortOptionsVariants = completeOptions(isLongOpt = false).sorted()
        val projectPaths = getAllLinkedProjectPath(project)
        val moduleGANames = getAllModuleNames(project)

        val toComplete = commandLine.toComplete
        return when {
            toComplete.startsWith("--") ->
                longOptionsVariants + shortOptionsVariants + goalsVariants + projectPaths + moduleGANames

            toComplete.startsWith("-") ->
                shortOptionsVariants + longOptionsVariants + goalsVariants + projectPaths + moduleGANames

            else ->
                goalsVariants + longOptionsVariants + shortOptionsVariants + projectPaths + moduleGANames
        }
    }

    override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
        val project = RunAnythingUtil.fetchProject(dataContext)
        val executionContext = dataContext.getData(EXECUTING_CONTEXT) ?: ProjectContext(project)
        val context = createContext(project, executionContext, dataContext) ?: return false
        val workingDirectory = context.workingDirectory
        runMaven(project, context.executor, workingDirectory, commandLine.command)
        return true
    }

    private fun completeGoals(): Sequence<String> {
        val commonGoals = listOf(
            "dependency:tree",
            "dependency:resolve",
            "dependency:sources",
            "dependency:resolve-plugins",
            "help:effective-pom",
            "help:effective-settings",
            "versions:display-dependency-updates",
            "versions:display-plugin-updates"
        )
        return (BASIC_PHASES + commonGoals).distinct().asSequence()
    }

    private fun completeOptions(isLongOpt: Boolean): Sequence<String> {
        return MavenCommandLineOptions.allOptions.asSequence()
            .map { if (isLongOpt) it.longName else it.name }
    }

    private fun runMaven(project: Project, executor: Executor?, workingDirectory: String, commandLine: String) {
        val settings = ExternalSystemTaskExecutionSettings()
        settings.externalProjectPath = workingDirectory
        settings.externalSystemIdString = SYSTEM_ID.id

        settings.scriptParameters = commandLine
        val executor = executor ?: DefaultRunExecutor.getRunExecutorInstance()

        ExternalSystemUtil.runTask(settings, executor.id, project, SYSTEM_ID)
    }

    private fun createContext(project: Project, context: RunAnythingContext, dataContext: DataContext): Context? {
        val workingDirectory = context.getWorkingDirectory() ?: return null
        val executor = EXECUTOR_KEY.getData(dataContext)
        return Context(context, project, workingDirectory, executor)
    }

    private fun RunAnythingContext.getWorkingDirectory(): String? {
        return when (this) {
            is ProjectContext -> getLinkedProjectPath() ?: getPath()
            is ModuleContext -> getLinkedModulePath() ?: getPath()
            else -> getPath()
        }
    }

    private fun ProjectContext.getLinkedProjectPath(): String? {
        return MavenSettings.getInstance(project)
            .linkedProjectsSettings.firstOrNull()
            ?.externalProjectPath
    }

    private fun getAllLinkedProjectPath(project: Project): List<String> {
        return MavenSettings.getInstance(project)
            .linkedProjectsSettings.mapNotNull { it.externalProjectPath }
            .map { Path(it).absolutePathString() }
    }

    private fun getAllModuleNames(project: Project): Sequence<String> {
        return CachedModuleDataService.getDataHolder(project).modules.asSequence()
            .map { it.groupId + ":" + it.artifactId }
    }

    private fun ModuleContext.getLinkedModulePath(): String? {
        return ExternalSystemApiUtil.getExternalProjectPath(module)
    }

    private fun Module.isSourceRoot(): Boolean {
        return GMavenConstants.SOURCE_SET_MODULE_TYPE_KEY == ExternalSystemApiUtil.getExternalModuleType(this)
    }

    data class Context(
        val context: RunAnythingContext,
        val project: Project,
        val workingDirectory: String,
        val executor: Executor?
    )

    companion object {
        const val HELP_COMMAND = "mvn"
        private val SECONDARY_HELP_COMMANDS = listOf("mvnw", "./mvnw", "mvnd")
    }
}

class RunAnythingEasyMavenItem(command: String, icon: Icon) : RunAnythingItemBase(command, icon) {
    override fun createComponent(pattern: String?, isSelected: Boolean, hasFocus: Boolean): Component {
        val command = getCommand()
        val component = super.createComponent(pattern, isSelected, hasFocus) as JPanel

        val toComplete = StringUtil.substringAfterLast(command, " ") ?: ""
        val option = if (toComplete.startsWith("--")) {
            MavenCommandLineOptions.allOptions.find { it.longName == toComplete }
        } else if (toComplete.startsWith("-")) {
            MavenCommandLineOptions.allOptions.find { it.name == toComplete }
        } else {
            null
        }

        if (option != null) {
            val description: String = option.description
            val descriptionComponent = SimpleColoredComponent()
            descriptionComponent.append(
                " " + StringUtil.shortenTextWithEllipsis(description, 200, 0),
                SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
            )
            component.add(descriptionComponent, BorderLayout.EAST)
        }

        return component
    }
}
package ru.rzn.gmyasoedov.gmaven.project.task

import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.command.line.CompletionTableInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.observable.util.createTextModificationTracker
import com.intellij.openapi.util.ModificationTracker
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import javax.swing.Icon

class GMavenCommandLineInfo(workingDirectoryField: WorkingDirectoryField) : CommandLineInfo {
    override val settingsName: String = GBundle.message("gmaven.run.configuration.command.line.name")
    override val settingsHint: String = GBundle.message("gmaven.run.configuration.command.line.hint")

    override val dialogTitle: String = GBundle.message("gmaven.run.configuration.command.line.title")
    override val dialogTooltip: String = GBundle.message("gmaven.run.configuration.command.line.tooltip")

    override val fieldEmptyState: String = GBundle.message("gmaven.run.configuration.command.line.empty.state")

    override val tablesInfo: List<CompletionTableInfo> = listOf(
        PhasesCompletionTableInfo(workingDirectoryField),
        ArgumentsCompletionTableInfo()
    )

    private class PhasesCompletionTableInfo(
        workingDirectoryField: WorkingDirectoryField
    ) : CompletionTableInfo {
        override val emptyState: String = GBundle.message("gmaven.run.configuration.command.line.tasks.empty.text")

        override val dataColumnIcon: Icon = AllIcons.General.Gear
        override val dataColumnName: String = GBundle.message("gmaven.run.configuration.command.line.task.column")

        override val descriptionColumnIcon: Icon? = null
        override val descriptionColumnName: String = GBundle.message("gmaven.run.configuration.command.line.description.column")

        override val completionModificationTracker: ModificationTracker =
            workingDirectoryField.createTextModificationTracker()

        private fun collectPhases(): List<TextCompletionInfo> {
            return GMavenConstants.BASIC_PHASES
                .map { TextCompletionInfo(it) }
                .sortedBy { it.text }
        }

        override suspend fun collectCompletionInfo(): List<TextCompletionInfo> {
            return collectPhases()
        }

        override suspend fun collectTableCompletionInfo(): List<TextCompletionInfo> {
            return collectCompletionInfo()
        }
    }

    private class ArgumentsCompletionTableInfo : CompletionTableInfo {
        override val emptyState: String = GBundle.message("gmaven.run.configuration.command.line.arguments.empty.text")

        override val dataColumnIcon: Icon? = null
        override val dataColumnName: String = GBundle.message("gmaven.run.configuration.command.line.argument.column")

        override val descriptionColumnIcon: Icon? = null
        override val descriptionColumnName: String =
            GBundle.message("gmaven.run.configuration.command.line.description.column")

        private fun collectOptionCompletion(isLongOptions: Boolean): List<TextCompletionInfo> {
            return MavenCommandLineOptions.allOptions
                .map { TextCompletionInfo(if (isLongOptions) it.name else it.longName, it.description) }
                .sortedBy { it.text }
        }

        override suspend fun collectCompletionInfo(): List<TextCompletionInfo> {
            return collectOptionCompletion(isLongOptions = false) +
                    collectOptionCompletion(isLongOptions = true)
        }

        override suspend fun collectTableCompletionInfo(): List<TextCompletionInfo> {
            return collectOptionCompletion(isLongOptions = true)
        }
    }
}
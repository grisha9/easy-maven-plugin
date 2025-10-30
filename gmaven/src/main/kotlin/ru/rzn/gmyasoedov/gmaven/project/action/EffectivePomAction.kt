package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.action.ExternalSystemAction
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.psi.PsiFileFactory
import com.intellij.util.execution.ParametersListUtil
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.TASK_EFFECTIVE_POM
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.util.GMavenNotification
import ru.rzn.gmyasoedov.gmaven.util.MavenPathUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.name


class EffectivePomAction : ExternalSystemAction() {

    init {
        setText(GBundle.message("gmaven.action.EffectivePom.text"))
        setDescription(GBundle.message("gmaven.action.EffectivePom.text"))
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isEnabled(e: AnActionEvent): Boolean = true

    override fun isVisible(e: AnActionEvent): Boolean {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
        val project = e.getData(CommonDataKeys.PROJECT) ?: return false
        val filePath = MavenUtils.toNioPathOrNull(virtualFile)?.toString() ?: return false
        return CachedModuleDataService.getDataHolder(project).isConfigPath(filePath)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val configNioPath = MavenUtils.toNioPathOrNull(virtualFile) ?: return
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val projectSettings = MavenSettings.getInstance(project)
            .getLinkedProjectSettings(configNioPath.parent.toString()) ?: return
        val settings = ExternalSystemTaskExecutionSettings()

        val resultNioPath = configNioPath.parent.resolve(virtualFile.name + ".effective")

        settings.externalSystemIdString = GMavenConstants.SYSTEM_ID.id
        settings.executionName = TASK_EFFECTIVE_POM
        settings.externalProjectPath = projectSettings.externalProjectPath
        settings.taskNames = listOf(TASK_EFFECTIVE_POM)

        val resultNioPathString = MavenPathUtil.checkOnWsl(resultNioPath.absolutePathString())
        val mavenPomPathString = MavenPathUtil.checkOnWsl(virtualFile.toNioPath().absolutePathString())
        settings.scriptParameters = " -N"
        settings.scriptParameters += " " + ParametersListUtil.escape("-Doutput=$resultNioPathString")
        settings.scriptParameters += " -f"
        settings.scriptParameters += " " + ParametersListUtil.escape(mavenPomPathString)

        ExternalSystemUtil.runTask(
            settings, DefaultRunExecutor.EXECUTOR_ID, project, GMavenConstants.SYSTEM_ID,
            object : TaskCallback {
                override fun onSuccess() {
                    if (!resultNioPath.toFile().exists()) {
                        GMavenNotification.errorExternalSystemNotification(
                            GBundle.message("gmaven.action.notifications.effective.pom.failed.title"),
                            "file not found $resultNioPath", project
                        )
                        return
                    }

                    ApplicationManager.getApplication().invokeLater {
                        val effectivePomFile = PsiFileFactory.getInstance(project).createFileFromText(
                            resultNioPath.parent.name, XMLLanguage.INSTANCE, Files.readString(resultNioPath)
                        )
                        try {
                            effectivePomFile.virtualFile.isWritable = false
                            effectivePomFile.navigate(true)
                        } catch (e: IOException) {
                            MavenLog.LOG.error(e)
                        } finally {
                            resultNioPath.toFile().delete()
                        }
                    }
                }

                override fun onFailure() {}
            }, ProgressExecutionMode.IN_BACKGROUND_ASYNC, true
        )
    }
}
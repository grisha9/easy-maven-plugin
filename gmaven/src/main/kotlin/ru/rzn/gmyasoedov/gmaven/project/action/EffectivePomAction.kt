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
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiFileFactory
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.TASK_EFFECTIVE_POM
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.util.GMavenNotification
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import java.io.IOException
import java.nio.file.Files
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
        val filePath = virtualFile.toNioPathOrNull()?.toString()  ?: return false
        return CachedModuleDataService.getDataHolder(project).isConfigPath(filePath)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val configNioPath = virtualFile.toNioPathOrNull() ?: return
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val projectSettings = MavenSettings.getInstance(project)
            .getLinkedProjectSettings(configNioPath.parent.toString()) ?: return
        val settings = ExternalSystemTaskExecutionSettings()

        val resultNioPath = configNioPath.parent.resolve(virtualFile.name + ".effective")
        val env = HashMap<String, String>(settings.env)
        env["output"] = resultNioPath.toString()

        settings.externalSystemIdString = GMavenConstants.SYSTEM_ID.id
        settings.executionName = TASK_EFFECTIVE_POM
        settings.externalProjectPath = projectSettings.externalProjectPath
        settings.taskNames = listOf(TASK_EFFECTIVE_POM, "-f", virtualFile.toNioPath().toString())
        settings.scriptParameters = "-N"
        settings.env = env

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
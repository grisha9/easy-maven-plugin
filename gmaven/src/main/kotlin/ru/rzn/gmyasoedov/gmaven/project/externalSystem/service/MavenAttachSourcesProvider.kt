package ru.rzn.gmyasoedov.gmaven.project.externalSystem.service

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.jarFinder.InternetAttachSourceProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.TASK_DOWNLOAD_SOURCE
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.util.GMavenNotification
import ru.rzn.gmyasoedov.gmaven.util.getLocalRepoPath
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists

class MavenAttachSourcesProvider : AttachSourcesProvider {

    override fun getActions(
        orderEntries: List<LibraryOrderEntry>,
        psiFile: PsiFile
    ): Collection<AttachSourcesAction> {
        if (getModules(orderEntries).isEmpty()) return listOf()
        return listOf(DownloadSourceAction(orderEntries, psiFile))
    }
}

internal class DownloadSourceAction(
    private val orderEntries: List<LibraryOrderEntry>, private val psiFile: PsiFile
) : AttachSourcesAction {

    override fun getName(): String {
        return GBundle.message("gmaven.action.download.sources")
    }

    override fun getBusyText(): String {
        return GBundle.message("gmaven.action.download.sources.busy.text")
    }

    override fun perform(orderEntriesContainingFile: List<LibraryOrderEntry>): ActionCallback {
        val modulesMap = getModules(orderEntries)
        if (modulesMap.isEmpty()) {
            return ActionCallback.REJECTED
        }
        val (libraryOrderEntry, module) = modulesMap.entries.iterator().next()
        val libraryName = libraryOrderEntry.libraryName ?: return ActionCallback.REJECTED
        val artifactCoordinates = StringUtil.trimStart(libraryName, SYSTEM_ID.readableName + ": ")
        if (StringUtil.equals(libraryName, artifactCoordinates)) {
            return ActionCallback.REJECTED
        }
        val externalProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
            ?: return ActionCallback.REJECTED
        return downloadSources(psiFile, artifactCoordinates, externalProjectPath)
    }

    private fun downloadSources(
        psiFile: PsiFile, artifactCoordinates: String, externalProjectPath: String
    ): ActionCallback {
        val split = artifactCoordinates.split(":")
        if (split.size < 3) return ActionCallback.REJECTED

        val project = psiFile.project
        val localRepoPath = getLocalRepoPath(project, externalProjectPath) ?: return ActionCallback.REJECTED
        val artifactNioPath = MavenArtifactUtil
            .getArtifactNioPath(Path.of(localRepoPath), split[0], split[1], split[2], "jar", "sources")
        if (artifactNioPath.exists()) {
            attachSources(artifactNioPath, orderEntries)
            return ActionCallback.DONE
        }
        val settings = ExternalSystemTaskExecutionSettings()
        settings.apply { scriptParameters = "" }
        settings.scriptParameters += " -DincludeGroupIds=${split[0]}"
        settings.scriptParameters += " -DincludeArtifactIds=${split[1]}"

        settings.executionName = name
        settings.externalProjectPath = externalProjectPath
        settings.taskNames = listOf(TASK_DOWNLOAD_SOURCE)
        settings.externalSystemIdString = SYSTEM_ID.id
        val resultWrapper = ActionCallback()
        ExternalSystemUtil.runTask(
            settings, DefaultRunExecutor.EXECUTOR_ID, project, SYSTEM_ID,
            object : TaskCallback {
                override fun onSuccess() {
                    val sourceJar: File
                    try {
                        sourceJar = artifactNioPath.toFile()
                        if (!sourceJar.exists()) {
                            resultWrapper.setRejected()
                            errorNotification("file not found $sourceJar", project)
                            return
                        }
                    } catch (e: IOException) {
                        MavenLog.LOG.warn(e)
                        resultWrapper.setRejected()
                        errorNotification(e.localizedMessage, project)
                        return
                    }
                    attachSources(artifactNioPath, orderEntries)
                    resultWrapper.setDone()
                }

                override fun onFailure() {
                    resultWrapper.setRejected()
                    val message = GBundle.message(
                        "gmaven.action.notifications.sources.download.failed.content", artifactCoordinates
                    )
                    errorNotification(message, project)
                }
            }, ProgressExecutionMode.IN_BACKGROUND_ASYNC, true
        )
        return resultWrapper
    }

    private fun errorNotification(
        message: String,
        project: Project
    ) {
        val title = GBundle.message("gmaven.action.notifications.sources.download.failed.title")
        GMavenNotification.errorExternalSystemNotification(title, message, project)
    }

    private fun attachSources(sourcesJar: Path, orderEntries: List<LibraryOrderEntry>) {
        ApplicationManager.getApplication()
            .invokeLater {
                InternetAttachSourceProvider.attachSourceJar(sourcesJar, orderEntries.mapNotNull { it.library })
            }
    }
}

private fun getModules(libraryOrderEntries: List<LibraryOrderEntry>): Map<LibraryOrderEntry, Module> {
    val result: MutableMap<LibraryOrderEntry, Module> = HashMap()
    for (entry in libraryOrderEntries) {
        val module = entry.ownerModule
        if (ExternalSystemApiUtil.isExternalSystemAwareModule(SYSTEM_ID, module)) {
            result[entry] = module
        }
    }
    return result
}
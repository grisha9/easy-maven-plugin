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
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.psi.PsiFile
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.TASK_DOWNLOAD_SOURCE
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState
import ru.rzn.gmyasoedov.gmaven.util.GMavenNotification
import ru.rzn.gmyasoedov.gmaven.util.getLocalRepoPath
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists

class MavenAttachSourcesProvider : AttachSourcesProvider {

    @Deprecated("Use getLibrariesActions(Collection<LibraryEntity>, PsiFile)")
    override fun getActions(
        orderEntries: List<LibraryOrderEntry>,
        psiFile: PsiFile
    ): Collection<AttachSourcesAction> {
        val libraries = orderEntries.mapNotNull { entry ->
            entry.library?.let { LibraryWithModule(entry.libraryName, it, entry.ownerModule) }
        }
        return getActionsInternal(libraries, psiFile)
    }

    override fun getLibrariesActions(
        libraryEntities: Collection<LibraryEntity>,
        psiFile: PsiFile
    ): Collection<AttachSourcesAction> {
        return getActionsInternal(findLibraries(libraryEntities, psiFile.project), psiFile)
    }

    private fun getActionsInternal(
        libraries: List<LibraryWithModule>, psiFile: PsiFile
    ): Collection<AttachSourcesAction> {
        val mavenLibraries = libraries.filter { isMavenModule(it.module) }
        if (mavenLibraries.isEmpty()) return listOf()
        return listOf(DownloadSourceAction(mavenLibraries, psiFile))
    }

    /**
     * Resolves workspace model library entities to legacy [Library] instances using public API only:
     * modules that reference the library -> module order entries with the same library name.
     */
    private fun findLibraries(libraryEntities: Collection<LibraryEntity>, project: Project): List<LibraryWithModule> {
        val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
        val moduleManager = ModuleManager.getInstance(project)
        val result = mutableListOf<LibraryWithModule>()
        for (libraryEntity in libraryEntities) {
            val libraryName = libraryEntity.name
            for (moduleEntity in snapshot.referrers(libraryEntity.symbolicId, ModuleEntity::class.java)) {
                val module = moduleManager.findModuleByName(moduleEntity.name) ?: continue
                if (!isMavenModule(module)) continue
                val library = ModuleRootManager.getInstance(module).orderEntries
                    .filterIsInstance<LibraryOrderEntry>()
                    .firstNotNullOfOrNull { it.takeIf { e -> e.libraryName == libraryName }?.library }
                    ?: continue
                result += LibraryWithModule(libraryName, library, module)
            }
        }
        return result
    }
}

internal data class LibraryWithModule(val libraryName: String?, val library: Library, val module: Module)

private fun isMavenModule(module: Module) = ExternalSystemApiUtil.isExternalSystemAwareModule(SYSTEM_ID, module)

internal class DownloadSourceAction(
    private val libraries: List<LibraryWithModule>, private val psiFile: PsiFile
) : AttachSourcesAction {

    override fun getName(): String {
        return GBundle.message("gmaven.action.download.sources")
    }

    override fun getBusyText(): String {
        return GBundle.message("gmaven.action.download.sources.busy.text")
    }

    @Deprecated("Use perform(Collection<LibraryEntity>, Project)")
    override fun perform(orderEntriesContainingFile: List<LibraryOrderEntry>): ActionCallback {
        return performInternal()
    }

    override fun perform(libraryEntities: Collection<LibraryEntity>, project: Project): ActionCallback {
        return performInternal()
    }

    private fun performInternal(): ActionCallback {
        val (libraryName, _, module) = libraries.firstOrNull() ?: return ActionCallback.REJECTED
        if (libraryName == null) return ActionCallback.REJECTED
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
            attachSources(artifactNioPath)
            return ActionCallback.DONE
        }

        val downloadSourceTask = getDownloadSourceTask()
        val settings = ExternalSystemTaskExecutionSettings()
        settings.apply { scriptParameters = "" }
        if (downloadSourceTask.contains(TASK_DOWNLOAD_SOURCE)) {
            settings.scriptParameters += " -DincludeGroupIds=${split[0]}"
            settings.scriptParameters += " -DincludeArtifactIds=${split[1]}"
        } else {
            settings.scriptParameters += " -Dartifact=${artifactCoordinates}:jar:sources"
        }

        settings.executionName = name
        settings.externalProjectPath = externalProjectPath
        settings.taskNames = downloadSourceTask
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
                    attachSources(artifactNioPath)
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

    private fun getDownloadSourceTask(): List<String> {
        val sourcesTask = (MavenAdvancedSettingsState.getInstance().sourcesMavenTask
            ?.ifEmpty { TASK_DOWNLOAD_SOURCE }
            ?: TASK_DOWNLOAD_SOURCE)
        return sourcesTask.trim().split(" ")
    }

    private fun errorNotification(
        message: String,
        project: Project
    ) {
        val title = GBundle.message("gmaven.action.notifications.sources.download.failed.title")
        GMavenNotification.errorExternalSystemNotification(title, message, project)
    }

    private fun attachSources(sourcesJar: Path) {
        ApplicationManager.getApplication()
            .invokeLater {
                InternetAttachSourceProvider.attachSourceJar(sourcesJar, libraries.map { it.library }.distinct())
            }
    }
}
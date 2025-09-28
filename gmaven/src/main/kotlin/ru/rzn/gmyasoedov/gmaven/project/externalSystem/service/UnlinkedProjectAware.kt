package ru.rzn.gmyasoedov.gmaven.project.externalSystem.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectLinkListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.projectImport.ProjectOpenProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import ru.rzn.gmyasoedov.gmaven.settings.MavenProjectSettings
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import ru.rzn.gmyasoedov.gmaven.wizard.GOpenProjectProvider
import ru.rzn.gmyasoedov.gmaven.wizard.GProjectOpenProcessor
import java.nio.file.Path

class UnlinkedProjectAware : ExternalSystemUnlinkedProjectAware {
    override val systemId = SYSTEM_ID

    override fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean {
        return buildFile.isFile && MavenUtils.isSimplePomFile(buildFile)
    }

    override fun isLinkedProject(project: Project, externalProjectPath: String): Boolean {
        return MavenSettings.getInstance(project).getLinkedProjectSettings(externalProjectPath) != null
                || isLinkedProjectTryCanonical(project, externalProjectPath)
    }

    override fun subscribe(
        project: Project, listener: ExternalSystemProjectLinkListener, parentDisposable: Disposable
    ) {
        val mavenSettings = MavenSettings.getInstance(project)
        mavenSettings.subscribe(object : ExternalSystemSettingsListener<MavenProjectSettings> {
            override fun onProjectsLinked(settings: Collection<MavenProjectSettings>) {
                CachedModuleDataService.invalidate()
                CachedModuleDataService.getDataHolder(project)
                settings.forEach { listener.onProjectLinked(it.externalProjectPath) }
            }

            override fun onProjectsUnlinked(linkedProjectPaths: Set<String>) {
                CachedModuleDataService.invalidate()
                CachedModuleDataService.getDataHolder(project)
                linkedProjectPaths.forEach { listener.onProjectUnlinked(it) }
            }
        }, parentDisposable)
    }

    override suspend fun linkAndLoadProjectAsync(project: Project, externalProjectPath: String) {
        withContext(Dispatchers.EDT) {
            ProjectOpenProcessor.EXTENSION_POINT_NAME.findExtensionOrFail(GProjectOpenProcessor::class.java)
                .importProjectAfterwards(project, MavenUtils.getVFile(Path.of(externalProjectPath).toFile()))
        }
    }

    override suspend fun unlinkProject(project: Project, externalProjectPath: String) {
        GOpenProjectProvider().unlinkProject(project, externalProjectPath)
    }

    private fun isLinkedProjectTryCanonical(project: Project, externalProjectPath: String): Boolean {
        return try {
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(externalProjectPath) ?: return false
            val directoryFile = if (!virtualFile.isDirectory) virtualFile.parent else virtualFile
            val canonicalPath = directoryFile.canonicalPath ?: return false
            MavenSettings.getInstance(project).getLinkedProjectSettings(canonicalPath) != null
        } catch (_: Exception) {
            false
        }
    }
}
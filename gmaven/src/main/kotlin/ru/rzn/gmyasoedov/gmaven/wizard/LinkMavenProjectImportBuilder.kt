package ru.rzn.gmyasoedov.gmaven.wizard

import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl.setupCreatedProject
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.projectImport.ProjectImportBuilder
import com.intellij.projectImport.ProjectImportProvider.getDefaultPath

import icons.GMavenIcons
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import java.nio.file.Path
import javax.swing.Icon

internal class LinkMavenProjectImportBuilder : ProjectImportBuilder<Any>() {
    override fun getName(): String = GMavenConstants.SYSTEM_ID.readableName

    override fun getIcon(): Icon = GMavenIcons.MavenProject

    override fun getList(): List<Any> = emptyList()

    override fun isMarked(element: Any): Boolean = true

    override fun setOpenProjectSettingsAfter(on: Boolean) {}

    private fun getPathToBeImported(path: String): String {
        val localForImport = LocalFileSystem.getInstance()
        val file = localForImport.refreshAndFindFileByPath(path)
        return file?.let(::getDefaultPath) ?: path
    }

    override fun setFileToImport(path: String) = super.setFileToImport(getPathToBeImported(path))

    override fun createProject(name: String, path: String): Project? {
        return setupCreatedProject(super.createProject(name, path))?.also {
            it.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true)
        }
    }

    override fun validate(currentProject: Project?, project: Project) = true

    override fun commit(
        project: Project,
        model: ModifiableModuleModel?,
        modulesProvider: ModulesProvider?,
        artifactModel: ModifiableArtifactModel?
    ): List<Module> {
        val virtualFile = MavenUtils.getVFile(Path.of(fileToImport).toFile())
        GOpenProjectProvider().linkMavenProject(virtualFile, project)
        return emptyList()
    }
}
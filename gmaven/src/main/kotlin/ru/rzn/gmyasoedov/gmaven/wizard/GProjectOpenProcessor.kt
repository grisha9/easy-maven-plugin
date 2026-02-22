package ru.rzn.gmyasoedov.gmaven.wizard

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import icons.GMavenIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.rzn.gmyasoedov.gmaven.GMavenConstants

internal class GProjectOpenProcessor : ProjectOpenProcessor() {
    private val importProvider = GOpenProjectProvider()

    override fun canOpenProject(file: VirtualFile): Boolean = importProvider.canOpenProject(file)

    override suspend fun openProjectAsync(
        virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean
    ): Project? {
        return importProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame)
    }

    override val name = GMavenConstants.SYSTEM_ID.readableName

    override val icon = GMavenIcons.MavenProject

    override fun canImportProjectAfterwards(): Boolean = true

    override suspend fun importProjectAfterwardsAsync(project: Project, file: VirtualFile) {
        withContext(Dispatchers.EDT) {
            importProvider.linkToExistingProject(file, project)
        }
    }
}
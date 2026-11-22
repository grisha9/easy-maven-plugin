package ru.rzn.gmyasoedov.gmaven.wizard

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils

class GMavenProjectImportProvider : AbstractExternalProjectImportProvider(
    LinkMavenProjectImportBuilder(), GMavenConstants.SYSTEM_ID
) {

    override fun createSteps(context: WizardContext) = ModuleWizardStep.EMPTY_ARRAY

    override fun canImport(fileOrDirectory: VirtualFile, project: Project?): Boolean {
        return MavenUtils.isProjectDirOrFile(fileOrDirectory)
    }

    override fun canImportFromFile(file: VirtualFile): Boolean {
        return MavenUtils.isSimplePomFile(file) || MavenUtils.isPotentialPomFile(file.name)
    }

    override fun getFileSample(): String {
        return GBundle.message("maven.project.file.pom.xml")
    }
}
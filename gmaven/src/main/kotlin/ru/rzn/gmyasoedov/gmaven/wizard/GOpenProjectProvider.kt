package ru.rzn.gmyasoedov.gmaven.wizard

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.VisibleForTesting
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import ru.rzn.gmyasoedov.gmaven.project.policy.ReadProjectResolverPolicy
import ru.rzn.gmyasoedov.gmaven.settings.MavenProjectSettings
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils


class GOpenProjectProvider : AbstractOpenProjectProvider() {

    override val systemId: ProjectSystemId = SYSTEM_ID

    override fun isProjectFile(file: VirtualFile) = MavenUtils.isPomFile(file)

    override fun linkToExistingProject(projectFile: VirtualFile, project: Project) {
        val mavenSettings = MavenSettings.getInstance(project)
        mavenSettings.storeProjectFilesExternally = true
        val mavenProjectSettings = createMavenProjectSettings(projectFile)
        attachProjectAndRefresh(mavenProjectSettings, project)
    }

    @VisibleForTesting
    fun attachProjectAndRefresh(settings: MavenProjectSettings, project: Project) {
        val externalProjectPath = settings.externalProjectPath

        ExternalSystemApiUtil.getSettings(project, SYSTEM_ID).linkProject(settings)
        ExternalSystemUtil.refreshProject(
            externalProjectPath,
            ImportSpecBuilder(project, SYSTEM_ID)
                .usePreviewMode()
                .use(ProgressExecutionMode.MODAL_SYNC)
        )
        if (Registry.`is`("external.system.auto.import.disabled")) return
        if (Registry.`is`("gmaven.import.readonly")) {
            runWhenInitialized(project) {
                ExternalSystemUtil.refreshProject(
                    externalProjectPath,
                    ImportSpecBuilder(project, SYSTEM_ID)
                        .callback(createFinalImportCallback(project, externalProjectPath))
                )
            }
            return
        }

        runWhenInitialized(project) {
            val importSpecBuilder = getImportSpecBuilder(project, externalProjectPath)
            ExternalSystemUtil.refreshProject(externalProjectPath, importSpecBuilder)
        }
    }

    private fun getImportSpecBuilder(
        project: Project,
        externalProjectPath: String
    ): ImportSpecBuilder {
        return if (!Registry.`is`("gmaven.fast.open.project")
            || ApplicationManager.getApplication().isHeadlessEnvironment
            || ApplicationManager.getApplication().isUnitTestMode
        ) {
            ImportSpecBuilder(project, SYSTEM_ID).callback(createFinalImportCallback(project, externalProjectPath))
        } else {
            ImportSpecBuilder(project, SYSTEM_ID)
                .projectResolverPolicy(ReadProjectResolverPolicy())
                .callback(createFinalImportCallbackWithResolve(project, externalProjectPath))
        }
    }

    private fun createFinalImportCallback(
        project: Project,
        externalProjectPath: String
    ): ExternalProjectRefreshCallback {
        return object : ExternalProjectRefreshCallback {
            override fun onSuccess(externalProject: DataNode<ProjectData>?) {
                if (externalProject == null) return
                ProjectDataManager.getInstance().importData(externalProject, project)
            }
        }
    }

    private fun createFinalImportCallbackWithResolve(
        project: Project,
        externalProjectPath: String
    ): ExternalProjectRefreshCallback {
        return object : ExternalProjectRefreshCallback {
            override fun onSuccess(externalProject: DataNode<ProjectData>?) {
                if (externalProject == null) return
                ProjectDataManager.getInstance().importData(externalProject, project)

                DumbService.getInstance(project).runWhenSmart {
                    ExternalSystemUtil.refreshProject(externalProjectPath, ImportSpecBuilder(project, SYSTEM_ID))
                }
            }
        }
    }

    private fun runWhenInitialized(project: Project, runnable: Runnable) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            runnable.run()
        } else {
            ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized {
                runnable.run()
            }
        }
    }
}
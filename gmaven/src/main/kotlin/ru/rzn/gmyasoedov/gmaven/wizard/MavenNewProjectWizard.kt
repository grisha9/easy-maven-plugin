package ru.rzn.gmyasoedov.gmaven.wizard

import com.intellij.ide.projectWizard.generators.*
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.maven.plugin.reader.model.SimpleMavenId
import java.nio.file.Path

class MavenNewProjectWizard : BuildSystemJavaNewProjectWizard {

    override val name = GMavenConstants.SYSTEM_ID.readableName

    override val ordinal = 110

    override fun createStep(parent: JavaNewProjectWizard.Step): NewProjectWizardStep =
        Step(parent).nextStep(::AssetsStep)

    class Step(parent: JavaNewProjectWizard.Step) :
        GMavenNewProjectWizardStep<JavaNewProjectWizard.Step>(parent),
        BuildSystemJavaNewProjectWizardData by parent,
        JavaNewProjectWizardData {

        override val addSampleCodeProperty = propertyGraph.property(true)
            .bindBooleanStorage(NewProjectWizardStep.ADD_SAMPLE_CODE_PROPERTY_NAME)
        override val generateOnboardingTipsProperty = propertyGraph
            .property(AssetsJavaNewProjectWizardStep.proposeToGenerateOnboardingTipsByDefault())
            .bindBooleanStorage(NewProjectWizardStep.GENERATE_ONBOARDING_TIPS_NAME)

        override var addSampleCode by addSampleCodeProperty
        override var generateOnboardingTips by generateOnboardingTipsProperty

        private fun setupSampleCodeUI(builder: Panel) {
            builder.row {
                checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
                    .bindSelected(addSampleCodeProperty)
            }
        }

        private fun setupSampleCodeWithOnBoardingTipsUI(builder: Panel) {
            builder.indent {
                row {
                    checkBox(UIBundle.message("label.project.wizard.new.project.generate.onboarding.tips"))
                        .bindSelected(generateOnboardingTipsProperty)
                }
            }.enabledIf(addSampleCodeProperty)
        }

        override fun setupSettingsUI(builder: Panel) {
            parentData?.let { this.groupId = it.groupId }
            setupJavaSdkUI(builder)
            setupParentsUI(builder)
            setupSampleCodeUI(builder)
            setupSampleCodeWithOnBoardingTipsUI(builder)
        }

        override fun setupAdvancedSettingsUI(builder: Panel) {
            setupGroupIdUI(builder)
            setupArtifactIdUI(builder)
        }

        override fun setupProject(project: Project) {
            project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, true)
            project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true)
            ExternalProjectsManagerImpl.setupCreatedProject(project)

            var buildFile: VirtualFile? = null
            runWriteAction {
                val moduleDir = Path.of(parentStep.path, parentStep.name)
                buildFile = GMavenModuleBuilderHelper.createExternalProjectConfigFile(moduleDir, addSampleCode)
            }

            ApplicationManager.getApplication().invokeLater { setupProjectFiles(buildFile!!, project) }
        }

        private fun setupProjectFiles(buildFile: VirtualFile, project: Project) {
            val mavenId = SimpleMavenId(groupId, artifactId, version)
            ApplicationManager.getApplication().invokeLater {
                GMavenModuleBuilderHelper(
                    mavenId, parentData, parentData?.groupId == groupId, parentData?.version == version
                ).setupBuildScript(project, sdk, buildFile)

                if (context.isCreatingNewProject) {
                    val openProcessor = ProjectOpenProcessor.EXTENSION_POINT_NAME
                        .findExtensionOrFail(GProjectOpenProcessor::class.java)
                    openProcessor.importProjectAfterwards(project, buildFile)
                } else {
                    val mavenSettings = MavenSettings.getInstance(project)
                    val parentCanonicalPath = parentStep.path
                    val projectSettings = mavenSettings.getLinkedProjectSettings(parentCanonicalPath)
                        ?: mavenSettings.getLinkedProjectSettings(Path.of(parentCanonicalPath).toString())
                        ?: throw ExternalSystemException("settings not found " + parentStep.path)
                    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized {
                        ExternalSystemUtil.refreshProject(
                            projectSettings.externalProjectPath,
                            ImportSpecBuilder(project, GMavenConstants.SYSTEM_ID)
                        )
                    }
                }
            }
        }

        init {
            data.putUserData(JavaNewProjectWizardData.KEY, this)
        }
    }

    private class AssetsStep(private val parent: Step) : AssetsNewProjectWizardStep(parent) {

        override fun setupAssets(project: Project) {
            if (context.isCreatingNewProject) {
                addAssets(StandardAssetsProvider().getMavenIgnoreAssets())
            }
            if (parent.addSampleCode) {
                //withJavaSampleCodeAsset(project, "src/main/java", parent.groupId) todo
            }
        }
    }
}
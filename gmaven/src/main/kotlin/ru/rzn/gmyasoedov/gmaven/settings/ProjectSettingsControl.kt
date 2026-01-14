package ru.rzn.gmyasoedov.gmaven.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.emptyText
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.Registry.stringValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.concurrency.AppExecutorUtil
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle.message
import ru.rzn.gmyasoedov.gmaven.project.wrapper.MvnDotProperties.getDistributionUrl
import ru.rzn.gmyasoedov.gmaven.settings.ProjectSettingsControlBuilder.*
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState
import ru.rzn.gmyasoedov.gmaven.util.MvnUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable
import kotlin.io.path.exists

class ProjectSettingsControl(private val project: Project, private val currentSettings: MavenProjectSettings) :
    AbstractExternalProjectSettingsControl<MavenProjectSettings>(project, currentSettings) {

    private lateinit var mainPanel: DialogPanel
    private lateinit var disposable: Disposable
    private var jdkComboBox: SdkComboBox? = null

    private val propertyGraph = PropertyGraph()

    private val nonRecursiveBind = propertyGraph.property(false)
    private val resolveModulePerSourceSetBind = propertyGraph.property(false)
    private val showPluginNodesBind = propertyGraph.property(false)
    private val incrementalSyncBind = propertyGraph.property(false)

    private val updateSnapshotsModel = CollectionComboBoxModel(SnapshotUpdateType.values().toList())
    private val outputLevelModel = CollectionComboBoxModel(OutputLevelType.values().toList())

    private val threadCountBind = propertyGraph.property("")
    private val vmOptionsBind = propertyGraph.property("")
    private val additionalArgsBind = propertyGraph.property("")
    private val importArgsBind = propertyGraph.property("")

    private val distributionTypeModel = CollectionComboBoxModel(mutableListOf<String>())
    private val mavenUrlBind = propertyGraph.property("")
    private val mavenCustomPathBind = propertyGraph.property("")
    private val modelReaderVersionBind = propertyGraph.property("")

    private val distributionTypeMap = mutableMapOf<String, DistributionSettings>()
    private val mavenPathVisible = AtomicBooleanProperty(false)

    init {
        mavenCustomPathBind.afterChange {
            val pathValue = it.takeIf { it.isNotEmpty() } ?: return@afterChange
            val settings = distributionTypeMap[distributionTypeModel.selected] ?: return@afterChange
            settings.path = Path.of(pathValue)
        }
    }

    override fun validate(settings: MavenProjectSettings) = true

    override fun fillExtraControls(content: PaintAwarePanel, indentLevel: Int) {
        disposable = Disposer.newDisposable()
        val settingsPanel = panel {
            group {
                row {
                    checkBox(message("gmaven.settings.project.recursive"))
                        .align(AlignX.FILL)
                        .bindSelected(nonRecursiveBind)
                        .resizableColumn()
                }

                row {
                    checkBox(message("gmaven.settings.project.module.per.source.set"))
                        .align(AlignX.FILL)
                        .bindSelected(resolveModulePerSourceSetBind)
                        .resizableColumn()
                }

                row {
                    checkBox(message("gmaven.settings.project.plugins"))
                        .align(AlignX.FILL)
                        .applyToComponent { toolTipText = message("gmaven.settings.project.plugins.tooltip") }
                        .bindSelected(showPluginNodesBind)
                        .resizableColumn()
                }

                row {
                    checkBox(message("gmaven.settings.project.incremental"))
                        .align(AlignX.FILL)
                        .bindSelected(incrementalSyncBind)
                        .resizableColumn()
                }

                row(message("gmaven.settings.project.snapshot.update")) {
                    comboBox(updateSnapshotsModel)
                        .align(AlignX.FILL)
                        .resizableColumn()
                }

                row(message("gmaven.settings.project.output.level")) {
                    comboBox(outputLevelModel)
                        .align(AlignX.FILL)
                        .resizableColumn()
                }

                row(message("gmaven.settings.project.thread.count")) {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(threadCountBind)
                        .resizableColumn()
                }

                row(message("gmaven.settings.project.vm.options")) {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(vmOptionsBind)
                        .resizableColumn()
                }

                row(message("gmaven.settings.project.arguments")) {
                    textField()
                        .align(AlignX.FILL)
                        .applyToComponent {
                            toolTipText = message("gmaven.settings.project.arguments.tooltip")
                            emptyText.text = "Example: -X -s /home/settings.xml -gs \"/home/global_settings.xml\""
                        }
                        .bindText(additionalArgsBind)
                        .resizableColumn()
                }

                row(message("gmaven.settings.project.arguments.import")) {
                    textField()
                        .align(AlignX.FILL)
                        .applyToComponent {
                            toolTipText = message("gmaven.settings.project.arguments.import.tooltip")
                            emptyText.text = "Example: -q verify"
                        }
                        .bindText(importArgsBind)
                        .resizableColumn()
                }

                row(message("gmaven.settings.project.jvm")) {
                    gSdkComboBox(project)
                        .align(AlignX.FILL)
                        .applyToComponent { jdkComboBox = this }
                }

                row(message("gmaven.settings.project.maven.home")) {
                    comboBox(distributionTypeModel)
                        .align(AlignX.FILL)
                        .applyToComponent { addActionListener { changeMavenTypeListener() } }
                        .resizableColumn()
                }

                row {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(mavenUrlBind)
                        .resizableColumn()
                        .enabled(false)
                }.visibleIf(mavenPathVisible)

                row {
                    textFieldWithBrowseButton(
                        message("gmaven.settings.project.maven.dialog.title"), project, createSingleFolderDescriptor()
                    )
                        .bindText(mavenCustomPathBind)
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .validationOnInput { validationInfo(it) }
                        .applyToComponent {
                            toolTipText = message("gmaven.settings.project.maven.dialog.title")
                            emptyText.text = toolTipText
                        }
                }.visibleIf(mavenPathVisible.not())

                row(message("gmaven.settings.project.maven.plugin.version")) {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(modelReaderVersionBind)
                        .resizableColumn()
                        .comment(message("gmaven.settings.project.maven.plugin.version.tooltip"))
                }

                row("-") { }
                row("-") { }
            }
        }

        settingsPanel.registerValidators(disposable)
        mainPanel = panel {
            row {
                cell(ScrollPaneFactory.createScrollPane(settingsPanel, true)).align(Align.FILL)
            }.resizableRow()
        }
        content.add(mainPanel, ExternalSystemUiUtil.getFillLineConstraints(0).insets(0, 0, 0, 0))
    }

    private fun validationInfo(component: TextFieldWithBrowseButton): ValidationInfo? {
        val distributionSettings = distributionTypeMap[distributionTypeModel.selected] ?: return null
        if (isCustomPathDistribution(distributionSettings.type)) {
            if (component.text.isEmpty()) {
                return ValidationInfoBuilder(component)
                    .error(UIBundle.message("create.new.folder.folder.name.cannot.be.empty.error.message"))
            }
            val binPath = Path.of(component.text).resolve("bin")
            if (distributionSettings.type == DistributionType.CUSTOM) {
                if (!binPath.resolve("mvn").exists() || !binPath.resolve("mvn.cmd").exists()) {
                    return ValidationInfoBuilder(component).error(message("gmaven.settings.project.mvn.error"))
                }
            }
            if (distributionSettings.type == DistributionType.CUSTOM_MVND) {
                if (!binPath.resolve("mvnd.sh").exists() || !binPath.resolve("mvnd.cmd").exists()) {
                    return ValidationInfoBuilder(component).error(message("gmaven.settings.project.mvnd.error"))
                }
            }
        }
        return null
    }

    private fun isCustomPathDistribution(distributionType: DistributionType) =
        distributionType == DistributionType.CUSTOM || distributionType == DistributionType.CUSTOM_MVND

    private fun changeMavenTypeListener() {
        val distributionSettings = distributionTypeMap[distributionTypeModel.selected] ?: return
        val distributionType = distributionSettings.type
        mavenPathVisible.set(
            distributionType == DistributionType.MVN
                    || distributionType == DistributionType.BUNDLED || distributionType == DistributionType.WRAPPER
        )
        if (mavenPathVisible.get()) {
            if (distributionType == DistributionType.MVN) {
                mavenUrlBind.set(distributionSettings.path?.toString() ?: "")
            } else {
                mavenUrlBind.set(distributionSettings.url)
            }
        } else {
            mavenUrlBind.set("")
            mavenCustomPathBind.set(distributionSettings.path?.toString() ?: "")
        }
    }

    override fun resetExtraSettings(isDefaultModuleCreation: Boolean) {
        nonRecursiveBind.set(currentSettings.nonRecursive)
        resolveModulePerSourceSetBind.set(currentSettings.resolveModulePerSourceSet)
        showPluginNodesBind.set(currentSettings.showPluginNodes)
        incrementalSyncBind.set(currentSettings.incrementalSync)

        updateSnapshotsModel.selectedItem = currentSettings.snapshotUpdateType
        outputLevelModel.selectedItem = currentSettings.outputLevel

        threadCountBind.set(currentSettings.threadCount ?: "")
        vmOptionsBind.set(currentSettings.vmOptions ?: "")
        additionalArgsBind.set(currentSettings.arguments ?: "")
        importArgsBind.set(currentSettings.argumentsImport ?: "")
        modelReaderVersionBind.set(stringValue("gmaven.model.plugin.version"))

        setSelectedJdk(jdkComboBox, currentSettings.jdkName)

        mavenCustomPathBind.set(currentSettings.distributionSettings.path?.toString() ?: "")
        if (distributionTypeMap.isNotEmpty()) {
            setSelectedMavenType(currentSettings)
        } else {
            ReadAction.nonBlocking(Callable { getMavenDistributionsInfo(project, currentSettings) })
                .finishOnUiThread(ModalityState.current(), {
                    setMavenTypeModelData(it).let { setSelectedMavenType(currentSettings) }
                })
                .submit(AppExecutorUtil.getAppExecutorService())
        }
    }

    override fun isExtraSettingModified(): Boolean {
        if (currentSettings.nonRecursive != nonRecursiveBind.get()) return true
        if (currentSettings.resolveModulePerSourceSet != resolveModulePerSourceSetBind.get()) return true
        if (currentSettings.showPluginNodes != showPluginNodesBind.get()) return true
        if (currentSettings.incrementalSync != incrementalSyncBind.get()) return true

        if (currentSettings.snapshotUpdateType != updateSnapshotsModel.selected) return true
        if (currentSettings.outputLevel != outputLevelModel.selected) return true

        if (isNotEqualSettings(currentSettings.threadCount, threadCountBind.get())) return true
        if (isNotEqualSettings(currentSettings.vmOptions, vmOptionsBind.get())) return true
        if (isNotEqualSettings(currentSettings.arguments, additionalArgsBind.get())) return true
        if (isNotEqualSettings(currentSettings.argumentsImport, importArgsBind.get())) return true
        if (isNotEqualSettings(stringValue("gmaven.model.plugin.version"), modelReaderVersionBind.get())) return true

        val jdkNameBindSetting = jdkComboBox?.let { getJdkName(it.selectedItem) } ?: ""
        if (isNotEqualSettings(currentSettings.jdkName, jdkNameBindSetting)) return true

        if (distributionTypeModel.selected == null || distributionTypeMap.isEmpty()) return false
        val distributionSettings = distributionTypeMap[distributionTypeModel.selected] ?: return false
        if (currentSettings.distributionSettings.type != distributionSettings.type) return true
        if (isCustomPathDistribution(distributionSettings.type)) {
            val distributionPath = currentSettings.distributionSettings.path?.toString()
            if (isNotEqualSettings(distributionPath, mavenCustomPathBind.get())) return true
        }
        return false
    }

    override fun applyExtraSettings(settings: MavenProjectSettings) {
        settings.nonRecursive = nonRecursiveBind.get()
        settings.resolveModulePerSourceSet = resolveModulePerSourceSetBind.get()
        settings.showPluginNodes = showPluginNodesBind.get()
        settings.incrementalSync = incrementalSyncBind.get()

        settings.snapshotUpdateType = updateSnapshotsModel.selected!!
        settings.outputLevel = outputLevelModel.selected!!

        settings.threadCount = threadCountBind.get()
        settings.vmOptions = vmOptionsBind.get()
        settings.arguments = additionalArgsBind.get()
        settings.argumentsImport = importArgsBind.get()
        Registry.get("gmaven.model.plugin.version").setValue(modelReaderVersionBind.get())

        settings.jdkName = jdkComboBox?.let { getJdkName(it.selectedItem) }
        settings.distributionSettings = getDistributionSettings()
        saveRecentMavenPath(settings.distributionSettings)
    }

    private fun saveRecentMavenPath(distributionSettings: DistributionSettings) {
        if (distributionSettings.path == null || distributionSettings.path.absolutePathString().isBlank()) return
        try {
            if (distributionSettings.type == DistributionType.CUSTOM) {
                MavenAdvancedSettingsState.getInstance().lastMvnPath = distributionSettings.path.absolutePathString()
            } else if (distributionSettings.type == DistributionType.CUSTOM_MVND) {
                MavenAdvancedSettingsState.getInstance().lastMvndPath = distributionSettings.path.absolutePathString()
            }
        } catch (_: Exception) {
        }
    }

    private fun getDistributionSettings(): DistributionSettings {
        val distributionSettings = distributionTypeMap[distributionTypeModel.selected]
        return if (isCustomPathDistribution(distributionSettings!!.type)) {
            DistributionSettings(distributionSettings.type, Path.of(mavenCustomPathBind.get()), null)
        } else {
            distributionSettings
        }
    }

    override fun disposeUIResources() {
        super.disposeUIResources()
        Disposer.dispose(disposable)
    }

    private fun setSelectedJdk(jdkComboBox: SdkComboBox?, jdkName: String?) {
        jdkComboBox ?: return
        if (jdkName == ExternalSystemJdkUtil.USE_PROJECT_JDK) {
            jdkComboBox.selectedItem = jdkComboBox.showProjectSdkItem()
        } else if (jdkName == ExternalSystemJdkUtil.USE_JAVA_HOME) {
            jdkComboBox.selectedItem = jdkComboBox.showProjectSdkItem()
        } else if (jdkName == null) {
            jdkComboBox.selectedItem = jdkComboBox.showNoneSdkItem()
        } else {
            jdkComboBox.setSelectedSdk(jdkName)
        }
    }

    private fun setMavenTypeModelData(distributionInfo: List<DistributionInfo>) {
        distributionTypeModel.removeAll()
        distributionTypeMap.clear()
        for (each in distributionInfo) {
            distributionTypeModel.add(each.description)
            distributionTypeMap[each.description] = each.distributionSettings
            if (isCustomPathDistribution(each.distributionSettings.type)) {
                val customPath = mavenCustomPathBind.get().takeIf { it.isNotEmpty() }
                    ?: MvnUtil.getRecentMvnPath(each.distributionSettings.type == DistributionType.CUSTOM_MVND)
                    ?: currentSettings.localRepositoryPath ?: ""
                each.distributionSettings.path = Path.of(customPath)
            }
        }
        distributionTypeModel.update()
    }

    private fun setSelectedMavenType(currentSettings: MavenProjectSettings) {
        for (entry in distributionTypeMap) {
            if (entry.value.type == currentSettings.distributionSettings.type) {
                distributionTypeModel.selectedItem = entry.key
                return
            }
        }
    }

    private fun isNotEqualSettings(currentSetting: String?, bindSetting: String): Boolean {
        return (currentSetting ?: "") != bindSetting
    }

    override fun getHelpId() = "GMaven_Project_Settings"
}

private fun Row.gSdkComboBox(
    project: Project
): Cell<SdkComboBox> {
    val structureConfigurable = ProjectStructureConfigurable.getInstance(project)
    val sdksModel = structureConfigurable.projectJdksModel

    setupProjectSdksModel(sdksModel, project)
    val sdkComboBox = SdkComboBox(SdkComboBoxModel.createJdkComboBoxModel(project, sdksModel))

    return cell(sdkComboBox)
}

private fun setupProjectSdksModel(sdksModel: ProjectSdksModel, project: Project) {
    sdksModel.reset(project)
    deduplicateSdkNames(sdksModel)

    var projectSdk = sdksModel.projectSdk
    projectSdk = sdksModel.findSdk(projectSdk)

    if (projectSdk != null) {
        projectSdk = ExternalSystemJdkUtil.resolveDependentJdk(projectSdk)
        projectSdk = sdksModel.findSdk(projectSdk.name)
    }
    sdksModel.projectSdk = projectSdk
}

private fun getMavenDistributionsInfo(project: Project, projectSettings: MavenProjectSettings): List<DistributionInfo> {
    val result = mutableListOf(DistributionSettings.getBundled())
    val distributionUrl = getDistributionUrl(project, projectSettings.externalProjectPath)
    if (distributionUrl.isNotEmpty()) {
        result.add(DistributionSettings.getWrapper(distributionUrl))
    }
    val mavenHome = MavenUtils.resolveMavenHome()
    if (mavenHome != null) {
        result.add(DistributionSettings.getLocal(mavenHome.toPath()))
    }
    result.add(DistributionSettings(DistributionType.CUSTOM, null, null))
    result.add(DistributionSettings(DistributionType.CUSTOM_MVND, null, null))
    return result.map { DistributionInfo(getDistributionUIText(it), it) }
}

private fun getDistributionUIText(each: DistributionSettings): String {
    return if (each.type == DistributionType.BUNDLED) {
        StringUtil.capitalize(each.type.name.lowercase(Locale.getDefault())) +
                "(maven version: " + GMavenConstants.BUNDLED_MAVEN_VERSION + ")"
    } else if (each.type == DistributionType.WRAPPER) {
        "Use Maven Wrapper"
    } else if (each.type == DistributionType.MVN) {
        getMvnText(each)
    } else if (each.type == DistributionType.CUSTOM_MVND) {
        "Maven Daemon"
    } else {
        "Custom Maven"
    }
}

private fun getMvnText(each: DistributionSettings): String {
    var text = "Maven home(mvn)"
    try {
        val mavenVersion = MavenUtils.getMavenVersion(each.path.toFile())
        if (mavenVersion != null) {
            text += ": $mavenVersion"
        }
    } catch (e: Exception) {
        MavenLog.LOG.warn(e)
    }
    return text
}

private data class DistributionInfo(val description: String, val distributionSettings: DistributionSettings)

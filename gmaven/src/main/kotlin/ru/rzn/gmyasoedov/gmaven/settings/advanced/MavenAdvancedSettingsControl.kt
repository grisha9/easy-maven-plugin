package ru.rzn.gmyasoedov.gmaven.settings.advanced

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.emptyText
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle.message
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import javax.swing.JComponent
import kotlin.io.path.absolutePathString

class MavenAdvancedSettingsControl(val project: Project) : SearchableConfigurable {
    private val additionalSettings = MavenAdvancedSettingsState.getInstance()
    private val baseSettings = MavenSettings.getInstance(project)

    private val propertyGraph = PropertyGraph()

    private val searchUrlBind = propertyGraph.property("")
    private val addGroupIdToSearchBind = propertyGraph.property(false)
    private val searchInSplitWindowBind = propertyGraph.property(false)
    private val groupIdFolderNavigationBind = propertyGraph.property(false)
    private val completionEasyMavenOnlyBind = propertyGraph.property(false)
    private val runLineMarkerBind = propertyGraph.property(false)
    private val pomNavigationBind = propertyGraph.property(false)
    private val defaultMavenHomeBind = propertyGraph.property("")
    private val sourcesMavenTaskBind = propertyGraph.property("")
    private val junitJvmArgsBind = propertyGraph.property("")
    private val junitEnvBind = propertyGraph.property("")

    private val checkSourcesBind = propertyGraph.property(false)
    private val wslSupportBind = propertyGraph.property(false)
    private val colorSupportBind = propertyGraph.property(false)

    override fun createComponent(): JComponent {
        return panel {
            group("Maven Central Search:") {
                row("Maven Central URL:") {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(searchUrlBind)
                        .resizableColumn()

                    link("To default") { searchUrlBind.set(DEFAULT_SEARCH_URL) }
                }

                row {
                    checkBox("Add groupId to search")
                        .bindSelected(addGroupIdToSearchBind)
                }

                row {
                    checkBox("Search in split window")
                        .bindSelected(searchInSplitWindowBind)
                }
            }

            group("JUnit Run Configuration:") {
                row("JVM Arguments:") {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(junitJvmArgsBind)
                        .resizableColumn()
                        .applyToComponent {
                            toolTipText = "Additional JVM arguments for JUnit tests (e.g., -Xmx512m -Dkey=value)"
                            emptyText.text = toolTipText
                        }
                }
                row("Environment Variables:") {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(junitEnvBind)
                        .resizableColumn()
                        .applyToComponent {
                            toolTipText = "Environment variables for JUnit tests (e.g., KEY1=value1;KEY2=value2)"
                            emptyText.text = toolTipText
                        }
                }
            }

            group("Other:") {
                row("Default Maven Home:") {
                    textFieldWithBrowseButton(
                        createSingleFolderDescriptor()
                            .withTitle("Select Maven Home Directory"),
                        project,
                        { it.toNioPath().absolutePathString() }
                    )
                        .align(AlignX.FILL)
                        .bindText(defaultMavenHomeBind)
                        .resizableColumn()
                        .applyToComponent {
                            toolTipText = message("gmaven.settings.project.maven.default.dialog.tooltip")
                            emptyText.text = toolTipText
                        }.comment(message("gmaven.settings.project.maven.default.dialog.comment"))
                }
                row("Sources Maven Task:") {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(sourcesMavenTaskBind)
                        .resizableColumn()
                        .applyToComponent {
                            toolTipText = "Maven task used to download sources"
                            emptyText.text = toolTipText
                        }

                    link("To default") { sourcesMavenTaskBind.set(DEFAULT_SOURCE_TASK) }
                }
                row {
                    checkBox(message("gmaven.settings.system.check.sources"))
                        .applyToComponent {
                            toolTipText =
                                message("gmaven.settings.system.check.sources.tooltip")
                        }
                        .bindSelected(checkSourcesBind)
                }
                row {
                    checkBox(message("gmaven.settings.system.wsl"))
                        .applyToComponent {
                            toolTipText =
                                message("gmaven.settings.system.wsl.tooltip")
                        }
                        .visible(SystemInfo.isWindows)
                        .bindSelected(wslSupportBind)
                }
                row {
                    checkBox(message("gmaven.settings.system.colored"))
                        .bindSelected(colorSupportBind)
                }
                row {
                    checkBox("Navigation to local repo from Dependency")
                        .bindSelected(groupIdFolderNavigationBind)
                }
                row {
                    checkBox("Dependency completion only in Easy Maven projects")
                        .bindSelected(completionEasyMavenOnlyBind)
                }
                row {
                    checkBox("Show gutter run line markers - Tests/SpringBoot")
                        .bindSelected(runLineMarkerBind)
                }
                row {
                    checkBox("Use Easy Maven navigation in pom.xml")
                        .bindSelected(pomNavigationBind)
                }
            }
        }
    }

    override fun reset() {
        searchUrlBind.set(additionalSettings.searchUrl ?: DEFAULT_SEARCH_URL)
        addGroupIdToSearchBind.set(additionalSettings.addGroupIdToSearch)
        searchInSplitWindowBind.set(additionalSettings.searchInSplitWindow)
        groupIdFolderNavigationBind.set(additionalSettings.groupIdFolderNavigation)
        completionEasyMavenOnlyBind.set(additionalSettings.completionEasyMavenOnly)
        runLineMarkerBind.set(additionalSettings.runLineMarker)
        pomNavigationBind.set(additionalSettings.pomNavigation)
        defaultMavenHomeBind.set(additionalSettings.defaultMavenHome ?: "")
        sourcesMavenTaskBind.set(additionalSettings.sourcesMavenTask ?: "")
        junitJvmArgsBind.set(additionalSettings.junitJvmArgs ?: "")
        junitEnvBind.set(additionalSettings.junitEnv ?: "")

        checkSourcesBind.set(baseSettings.isCheckSourcesInLocalRepo)
        colorSupportBind.set(baseSettings.isColoredSupport)
        wslSupportBind.set(Registry.`is`("gmaven.wsl.support"))
    }

    override fun isModified(): Boolean {
        if ((additionalSettings.searchUrl ?: DEFAULT_SEARCH_URL) != searchUrlBind.get()) return true
        if (additionalSettings.addGroupIdToSearch != addGroupIdToSearchBind.get()) return true
        if (additionalSettings.searchInSplitWindow != searchInSplitWindowBind.get()) return true
        if (additionalSettings.groupIdFolderNavigation != groupIdFolderNavigationBind.get()) return true
        if (additionalSettings.completionEasyMavenOnly != completionEasyMavenOnlyBind.get()) return true
        if (additionalSettings.runLineMarker != runLineMarkerBind.get()) return true
        if (additionalSettings.pomNavigation != pomNavigationBind.get()) return true
        if ((additionalSettings.defaultMavenHome ?: "") != defaultMavenHomeBind.get()) return true
        if ((additionalSettings.sourcesMavenTask ?: "") != sourcesMavenTaskBind.get()) return true
        if ((additionalSettings.junitJvmArgs ?: "") != junitJvmArgsBind.get()) return true
        if ((additionalSettings.junitEnv ?: "") != junitEnvBind.get()) return true

        if (baseSettings.isCheckSourcesInLocalRepo != checkSourcesBind.get()) return true
        if (baseSettings.isColoredSupport != colorSupportBind.get()) return true
        if (Registry.`is`("gmaven.wsl.support") != wslSupportBind.get()) return true

        return false
    }

    override fun apply() {
        additionalSettings.searchUrl = searchUrlBind.get()
        additionalSettings.addGroupIdToSearch = addGroupIdToSearchBind.get()
        additionalSettings.searchInSplitWindow = searchInSplitWindowBind.get()
        additionalSettings.groupIdFolderNavigation = groupIdFolderNavigationBind.get()
        additionalSettings.completionEasyMavenOnly = completionEasyMavenOnlyBind.get()
        additionalSettings.runLineMarker = runLineMarkerBind.get()
        additionalSettings.pomNavigation = pomNavigationBind.get()
        additionalSettings.defaultMavenHome = defaultMavenHomeBind.get()
        additionalSettings.sourcesMavenTask = sourcesMavenTaskBind.get()
        additionalSettings.junitJvmArgs = junitJvmArgsBind.get()
        additionalSettings.junitEnv = junitEnvBind.get()

        baseSettings.isCheckSourcesInLocalRepo = checkSourcesBind.get()
        baseSettings.isColoredSupport = colorSupportBind.get()
        Registry.get("gmaven.wsl.support").setValue(wslSupportBind.get())
    }

    override fun getId(): String = javaClass.name

    override fun getDisplayName() = id
}

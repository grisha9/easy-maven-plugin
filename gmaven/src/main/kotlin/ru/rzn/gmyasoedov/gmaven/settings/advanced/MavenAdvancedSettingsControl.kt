package ru.rzn.gmyasoedov.gmaven.settings.advanced

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import javax.swing.JComponent

class MavenAdvancedSettingsControl(val project: Project) : SearchableConfigurable {
    private val additionalSettings = MavenAdvancedSettingsState.getInstance()
    private val baseSettings = MavenSettings.getInstance(project)

    private val propertyGraph = PropertyGraph()

    private val searchUrlBind = propertyGraph.property("")
    private val addGroupIdToSearchBind = propertyGraph.property(false)
    private val searchInSplitWindowBind = propertyGraph.property(false)
    private val groupIdFolderNavigationBind = propertyGraph.property(false)

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

            group("Other:") {
                row {
                    checkBox(GBundle.message("gmaven.settings.system.check.sources"))
                        .applyToComponent {
                            toolTipText =
                                GBundle.message("gmaven.settings.system.check.sources.tooltip")
                        }
                        .bindSelected(checkSourcesBind)
                }
                row {
                    checkBox(GBundle.message("gmaven.settings.system.wsl"))
                        .applyToComponent {
                            toolTipText =
                                GBundle.message("gmaven.settings.system.wsl.tooltip")
                        }
                        .visible(SystemInfo.isWindows)
                        .bindSelected(wslSupportBind)
                }
                row {
                    checkBox(GBundle.message("gmaven.settings.system.colored"))
                        .bindSelected(colorSupportBind)
                }
                row {
                    checkBox("Enable groupId folder navigation in project")
                        .bindSelected(groupIdFolderNavigationBind)
                }
            }
        }
    }

    override fun reset() {
        searchUrlBind.set(additionalSettings.searchUrl ?: DEFAULT_SEARCH_URL)
        addGroupIdToSearchBind.set(additionalSettings.addGroupIdToSearch)
        searchInSplitWindowBind.set(additionalSettings.searchInSplitWindow)
        groupIdFolderNavigationBind.set(additionalSettings.groupIdFolderNavigation)

        checkSourcesBind.set(baseSettings.isCheckSourcesInLocalRepo)
        colorSupportBind.set(baseSettings.isColoredSupport)
        wslSupportBind.set(Registry.`is`("gmaven.wsl.support"))
    }

    override fun isModified(): Boolean {
        if ((additionalSettings.searchUrl ?: DEFAULT_SEARCH_URL) != searchUrlBind.get()) return true
        if (additionalSettings.addGroupIdToSearch != addGroupIdToSearchBind.get()) return true
        if (additionalSettings.searchInSplitWindow != searchInSplitWindowBind.get()) return true
        if (additionalSettings.groupIdFolderNavigation != groupIdFolderNavigationBind.get()) return true

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

        baseSettings.isCheckSourcesInLocalRepo = checkSourcesBind.get()
        baseSettings.isColoredSupport = colorSupportBind.get()
        Registry.get("gmaven.wsl.support").setValue(wslSupportBind.get())
    }

    override fun getId(): String = javaClass.name

    override fun getDisplayName() = id
}

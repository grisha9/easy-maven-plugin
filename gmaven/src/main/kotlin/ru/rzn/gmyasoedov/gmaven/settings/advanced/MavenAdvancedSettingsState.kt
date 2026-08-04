package ru.rzn.gmyasoedov.gmaven.settings.advanced

import com.intellij.openapi.components.*

const val DEFAULT_SEARCH_URL = "https://search.maven.org/"
const val DEFAULT_SOURCE_TASK = "dependency:get -N"

@Service(Service.Level.APP)
@State(name = "EasyMavenAdvancedSettings", storages = [Storage("EasyMavenAdvancedSettings.xml")])
class MavenAdvancedSettingsState : PersistentStateComponent<MavenAdvancedSettingsState>, BaseState() {
    var searchUrl by string(DEFAULT_SEARCH_URL)
    var addGroupIdToSearch by property(false)
    var searchInSplitWindow by property(true)
    var groupIdFolderNavigation by property(true)
    var completionEasyMavenOnly by property(false)
    var runLineMarker by property(true)

    var defaultMavenHome by string("")
    var sourcesMavenTask by string(DEFAULT_SOURCE_TASK)
    var lastMvnPath by string("")
    var lastMvndPath by string("")

    var junitJvmArgs by string("")
    var junitEnv by string("")

    override fun getState(): MavenAdvancedSettingsState = this

    override fun loadState(state: MavenAdvancedSettingsState) = this.copyFrom(state)

    companion object {
        fun getInstance(): MavenAdvancedSettingsState = service()
    }
}

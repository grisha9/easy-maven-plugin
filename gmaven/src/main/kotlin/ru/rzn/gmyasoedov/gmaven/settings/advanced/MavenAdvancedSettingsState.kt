package ru.rzn.gmyasoedov.gmaven.settings.advanced

import com.intellij.openapi.components.*

const val DEFAULT_SEARCH_URL = "https://search.maven.org/"

@Service(Service.Level.APP)
@State(name = "EasyMavenAdvancedSettings", storages = [Storage("EasyMavenAdvancedSettings.xml")])
class MavenAdvancedSettingsState : PersistentStateComponent<MavenAdvancedSettingsState>, BaseState() {
    var searchUrl by string(DEFAULT_SEARCH_URL)
    var addGroupIdToSearch by property(false)
    var searchInSplitWindow by property(true)
    var groupIdFolderNavigation by property(true)
    var completionEasyMavenOnly by property(false)
    var runLineMarker by property(true)

    override fun getState(): MavenAdvancedSettingsState = this

    override fun loadState(state: MavenAdvancedSettingsState) = this.copyFrom(state)

    companion object {
        fun getInstance(): MavenAdvancedSettingsState = service()
    }
}

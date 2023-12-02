package ru.rzn.gmyasoedov.gmaven.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object CachedModuleDataService {
    private val isPerform = AtomicBoolean(false)
    private val lastResult = AtomicReference(CachedDataHolder())
    private val modificationTracker = SimpleModificationTracker()

    fun getDataHolder(project: Project): CachedDataHolder {
        if (!isPerform.compareAndSet(false, true)) {
            return lastResult.get()
        }
        try {
            val cachedValue = CachedValuesManager.getManager(project).getCachedValue(project) {
                CachedValueProvider.Result
                    .create(getCacheDataHolder(project),
                        ExternalProjectsDataStorage.getInstance(project), modificationTracker
                    )
            }
            lastResult.set(cachedValue)
            return cachedValue
        } finally {
            isPerform.set(false)
        }
    }

    fun invalidate() {
        modificationTracker.incModificationCount()
    }

    fun getCurrentData(): CachedDataHolder = lastResult.get()

    private fun getCacheDataHolder(project: Project): CachedDataHolder {
        val cachedModuleDataList = ProjectDataManager.getInstance().getExternalProjectsData(project, SYSTEM_ID)
            .asSequence()
            .mapNotNull { it.externalProjectStructure }
            .flatMap { ExternalSystemApiUtil.findAll(it, ProjectKeys.MODULE) }
            .mapNotNull { mapToCachedModuleData(it) }
            .toList()

        val activeConfigPaths = HashSet<String>(cachedModuleDataList.size)
        val ignoredConfigPaths = HashSet<String>()
        for (each in cachedModuleDataList) {
            if (each.ignored) ignoredConfigPaths += each.configPath else activeConfigPaths += each.configPath
        }

        return CachedDataHolder(cachedModuleDataList, activeConfigPaths, ignoredConfigPaths)
    }

    private fun mapToCachedModuleData(moduleNode: DataNode<ModuleData>): CachedModuleData? {
        val data = moduleNode.data
        val configPath = data.getProperty(GMavenConstants.MODULE_PROP_BUILD_FILE) ?: return null
        val groupId = data.group ?: return null
        val version = data.version ?: return null
        return CachedModuleData(data.moduleName, groupId, version, moduleNode.isIgnored, configPath)
    }
}

data class CachedDataHolder(
    val modules: List<CachedModuleData> = emptyList(),
    val activeConfigPaths: Set<String> = emptySet(),
    val ignoredConfigPaths: Set<String> = emptySet()
) {
    fun isConfigPath(path: String) = activeConfigPaths.contains(path) || ignoredConfigPaths.contains(path)
}

data class CachedModuleData(
    val artifactId: String, val groupId: String, val version: String, val ignored: Boolean, val configPath: String
)
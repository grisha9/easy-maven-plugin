package ru.rzn.gmyasoedov.gmaven.project.importing

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.util.SmartList
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SOURCE_SET_MODULE_TYPE_KEY
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.SourceSetData

@Order(ExternalSystemConstants.BUILTIN_MODULE_DATA_SERVICE_ORDER + 1)
class SourceSetDataService : AbstractModuleDataService<SourceSetData>() {
    override fun getTargetDataKey() = SourceSetData.KEY

    override fun computeOrphanData(
        toImport: Collection<DataNode<SourceSetData?>?>,
        projectData: ProjectData,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ): Computable<Collection<Module>> {
        return Computable { return@Computable getOrphanIdeModules(modelsProvider, projectData) }
    }

    override fun createModule(
        sourceSetModuleNode: DataNode<SourceSetData>, modelsProvider: IdeModifiableModelsProvider
    ): Module {
        val parentModuleNode = sourceSetModuleNode.parent
            .takeIf { it?.data is ModuleData } ?: throw IllegalStateException()
        val parentModule = parentModuleNode.getUserData(MODULE_KEY)!!
        val actualModuleName = modelsProvider.modifiableModuleModel.getActualName(parentModule)

        val sourceSetModuleInternalName: String = sourceSetModuleNode.data.internalName
        if (!sourceSetModuleInternalName.startsWith(actualModuleName)) {
            val sourceSetName: String = sourceSetModuleNode.data.moduleName
            val adjustedInternalName: String = findDeduplicatedModuleName(
                "$actualModuleName.$sourceSetName", modelsProvider
            )
            sourceSetModuleNode.data.internalName = adjustedInternalName
        }

        return super.createModule(sourceSetModuleNode, modelsProvider)
    }

    override fun setModuleOptions(module: Module, moduleDataNode: DataNode<SourceSetData>) {
        super.setModuleOptions(module, moduleDataNode)
        ExternalSystemModulePropertyManager.getInstance(module).setExternalModuleType(SOURCE_SET_MODULE_TYPE_KEY)
    }

    private fun getOrphanIdeModules(
        modelsProvider: IdeModifiableModelsProvider, projectData: ProjectData
    ): MutableList<Module> {
        val orphanIdeModules: MutableList<Module> = SmartList()
        for (module in modelsProvider.modules) {
            if (module.isDisposed) continue
            if (!ExternalSystemApiUtil.isExternalSystemAwareModule(projectData.owner, module)) continue
            if (SOURCE_SET_MODULE_TYPE_KEY != ExternalSystemApiUtil.getExternalModuleType(module)) continue
            val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
            if (projectData.linkedExternalProjectPath == rootProjectPath) {
                if (module.getUserData(MODULE_DATA_KEY) == null) {
                    orphanIdeModules.add(module)
                }
            }
        }
        return orphanIdeModules
    }

    private fun findDeduplicatedModuleName(
        moduleName: String, modelsProvider: IdeModifiableModelsProvider
    ): String {
        modelsProvider.findIdeModule(moduleName) ?: return moduleName
        var ideModule: Module?
        var i = 0
        while (true) {
            val nextModuleNameCandidate = moduleName + "~" + ++i
            ideModule = modelsProvider.findIdeModule(nextModuleNameCandidate)
            if (ideModule == null) {
                return nextModuleNameCandidate
            }
        }
    }
}
package ru.rzn.gmyasoedov.gmaven.util

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.project.process.BaseMavenCommandLine
import ru.rzn.gmyasoedov.gmaven.settings.debug.MavenDebugType

object MvnUtil {

    fun findMavenModuleData(module: Module): ModuleData? {
        val moduleName = module.name
        val mavenModule = CachedModuleDataService.getModulesSequence(module.project)
            .filter { it.data.internalName == moduleName }
            .map { it.data }
            .firstOrNull()
        if (mavenModule != null) return mavenModule
        if (isSourceSetModule(moduleName)) {
            val rootName = moduleName.substringBeforeLast(".")
            return CachedModuleDataService.getModulesSequence(module.project)
                .filter { it.data.internalName == rootName }
                .map { it.data }
                .firstOrNull()
        }
        return null
    }

    fun isTestFile(psiElement: PsiElement?): Boolean {
        val virtualFile = psiElement?.containingFile?.virtualFile ?: return false
        val fileIndex = ProjectRootManager.getInstance(psiElement.project).fileIndex
        return fileIndex.isInTestSourceContent(virtualFile)
    }

    fun setRemoteDebugJvmParam(debugType: MavenDebugType, settings: ExternalSystemTaskExecutionSettings) {
        val debugParams = debugType.getDebugParams(BaseMavenCommandLine.getDebugPort())
        settings.env[GMavenConstants.GMAVEN_ENV_DEBUG_PORT] = debugParams.first.toString()
        settings.scriptParameters += " -D${debugType.getName()}=${debugParams.second}"
    }

    private fun isSourceSetModule(moduleName: String): Boolean =
        moduleName.endsWith(".test") || moduleName.endsWith(".main")
}
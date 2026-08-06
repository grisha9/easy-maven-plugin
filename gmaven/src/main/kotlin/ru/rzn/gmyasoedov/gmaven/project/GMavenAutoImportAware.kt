package ru.rzn.gmyasoedov.gmaven.project

import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy
import com.intellij.openapi.project.Project
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory

class GMavenAutoImportAware : ExternalSystemAutoImportAware {

    override fun getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project): String? {
        val changedPath = Path.of(changedFileOrDirPath)
        if (changedPath.isDirectory()) return null
        if (CachedModuleDataService.getDataHolder(project).isConfigPath(changedPath.absolutePathString())) {
            return changedFileOrDirPath
        }

        return null
    }

    override fun isApplicable(resolverPolicy: ProjectResolverPolicy?) = true
}
package ru.rzn.gmyasoedov.gmaven.dom

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class GroupIdToLocalRepoLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(psiElement: PsiElement): Info? {
        if (!MavenAdvancedSettingsState.getInstance().groupIdFolderNavigation) return null

        val xmlTag = psiElement as? XmlTag ?: return null
        if (xmlTag.name != MavenArtifactUtil.GROUP_ID) return null
        val parentTag = xmlTag.parentTag ?: return null
        if (parentTag.name == MavenArtifactUtil.PROJECT) return null

        val groupIdValue = xmlTag.value.trimmedText.takeIf { it.isNotEmpty() } ?: return null
        val virtualFile = xmlTag.containingFile?.virtualFile ?: return null
        val configPath = virtualFile.toNioPathOrNull()?.absolutePathString() ?: return null
        if (!CachedModuleDataService.getDataHolder(xmlTag.project).isConfigPath(configPath)) return null

        val artifactId = parentTag.getSubTagText(MavenArtifactUtil.ARTIFACT_ID)?.trim()?.takeIf { it.isNotEmpty() }
        return Info(
            AllIcons.Actions.MenuOpen,
            //AllIcons.Nodes.ConfigFolder,
            arrayOf(OpenArtifactFolderAction(virtualFile, groupIdValue, artifactId)),
            { GBundle.message("gmaven.action.show.local.repo") }
        )
    }
}

private class OpenArtifactFolderAction(
    val configVirtualFile: VirtualFile, val groupIdValue: String, val artifactId: String?
) : AnAction(GBundle.message("gmaven.action.show.local.repo")) {

    init {
        templatePresentation.text = GBundle.message("gmaven.action.show.local.repo")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val modulePath = configVirtualFile.parent.canonicalPath ?: return
        val localRepoPath = MavenSettings.getInstance(project)
            .getLinkedProjectSettings(modulePath)
            ?.localRepositoryPath
            ?: MavenSettings.getInstance(project).linkedProjectsSettings.firstOrNull()?.localRepositoryPath
            ?: return
        val path = getPath(localRepoPath)
        RevealFileAction.openFile(path)
    }

    private fun getPath(localRepoPath: String): Path {
        val groups = groupIdValue.split(".")
        val baseGroupIdPath = Path(localRepoPath, *groups.toTypedArray())
        if (artifactId != null) {
            val artifactIdPath = baseGroupIdPath.resolve(artifactId)
            if (artifactIdPath.exists()) return artifactIdPath
        }
        return baseGroupIdPath
    }
}
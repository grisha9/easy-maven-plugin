package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlFile
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.dom.XmlPsiUtil
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import java.nio.file.Path

class AddBuildCacheExtensionAction : AnAction() {
    init {
        templatePresentation.text = GBundle.message("gmaven.action.BuildCache.text")
        templatePresentation.description = "Add maven build cache extension to pom"
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isAvailable(e)
    }

    private fun isAvailable(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
        if (virtualFile.isDirectory || !virtualFile.name.endsWith(".xml", ignoreCase = true)) return false
        val nioPath = virtualFile.toNioPathOrNull() ?: return false
        if (!CachedModuleDataService.getDataHolder(project).isConfigPath(nioPath.toString())) return false
        return isRootProjectConfig(project, nioPath)
    }

    /**
     * Build cache extension makes sense only in the root (linked) project pom, not in submodules.
     */
    private fun isRootProjectConfig(project: Project, configPath: Path): Boolean {
        val parentPath = configPath.parent?.toString() ?: return false
        val projectSettings = MavenSettings.getInstance(project).getLinkedProjectSettings(parentPath) ?: return false
        val projectBuildFile = projectSettings.projectBuildFile ?: return true
        return MavenUtils.equalsPaths(projectBuildFile, configPath.toString())
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val xmlFile = getXmlFile(e, project, virtualFile) ?: return

        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.getDocument(xmlFile)?.let { psiDocumentManager.commitDocument(it) }

        val rootTag = xmlFile.rootTag?.takeIf { it.name == MavenArtifactUtil.PROJECT } ?: return

        WriteCommandAction.writeCommandAction(project, xmlFile)
            .withName(templatePresentation.text)
            .run<RuntimeException> {
                val buildTag = XmlPsiUtil.getOrCreateTag(rootTag, MavenArtifactUtil.BUILD, null)
                val extensionsTag = XmlPsiUtil.getOrCreateTag(buildTag, MavenArtifactUtil.EXTENSIONS, null)
                val alreadyExists = extensionsTag.findSubTags(MavenArtifactUtil.EXTENSION).any {
                    it.getSubTagText(MavenArtifactUtil.GROUP_ID) == GROUP_ID
                            && it.getSubTagText(MavenArtifactUtil.ARTIFACT_ID) == ARTIFACT_ID
                }
                if (alreadyExists) return@run

                val extensionTag = extensionsTag.addSubTag(
                    extensionsTag.createChildTag(MavenArtifactUtil.EXTENSION, null, null, false), false
                )
                extensionTag.addSubTag(
                    extensionTag.createChildTag(MavenArtifactUtil.GROUP_ID, null, GROUP_ID, false), false
                )
                extensionTag.addSubTag(
                    extensionTag.createChildTag(MavenArtifactUtil.ARTIFACT_ID, null, ARTIFACT_ID, false), false
                )
                extensionTag.addSubTag(
                    extensionTag.createChildTag(MavenArtifactUtil.VERSION, null, VERSION, false), false
                )

                CodeStyleManager.getInstance(project).reformat(extensionTag)
            }
    }

    private fun getXmlFile(e: AnActionEvent, project: Project, virtualFile: VirtualFile): XmlFile? {
        // In the editor PSI_FILE is available; in the Project View we resolve it from the VirtualFile.
        return e.getData(CommonDataKeys.PSI_FILE) as? XmlFile
            ?: PsiManager.getInstance(project).findFile(virtualFile) as? XmlFile
    }

    companion object {
        private const val GROUP_ID = "org.apache.maven.extensions"
        private const val ARTIFACT_ID = "maven-build-cache-extension"
        private const val VERSION = "1.3.0"
    }
}

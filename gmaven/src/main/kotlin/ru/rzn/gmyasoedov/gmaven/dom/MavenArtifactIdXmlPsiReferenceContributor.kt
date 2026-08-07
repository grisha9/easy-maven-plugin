package ru.rzn.gmyasoedov.gmaven.dom

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.*
import com.intellij.psi.PsiReferenceBase.Immediate
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext
import ru.rzn.gmyasoedov.gmaven.dom.reference.MavenArtifactIdPsiReference
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleData
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.util.MvnUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil
import kotlin.io.path.Path


class MavenArtifactIdXmlPsiReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        if (!MavenAdvancedSettingsState.getInstance().pomNavigation) return

        val patternDependency = XmlPatterns.xmlTag()
            .withName(MavenArtifactUtil.ARTIFACT_ID)
            .withParent(XmlPatterns.xmlTag().withName(MavenArtifactUtil.DEPENDENCY))
        val patternParent = XmlPatterns.xmlTag()
            .withName(MavenArtifactUtil.ARTIFACT_ID)
            .withParent(XmlPatterns.xmlTag().withName(MavenArtifactUtil.PARENT))

        val psiReferenceProvider = MavenArtifactIdXmlPsiReferenceProvider()
        registrar.registerReferenceProvider(patternDependency, psiReferenceProvider)
        registrar.registerReferenceProvider(patternParent, psiReferenceProvider)
    }
}

private class MavenArtifactIdXmlPsiReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val xmlTag = element as? XmlTag ?: return emptyArray()
        if (!MvnUtil.usePomNavigation(element)) return emptyArray()

        val file = element.containingFile as? XmlFile ?: return emptyArray()
        val moduleName = file.rootTag?.getSubTagText(MavenArtifactUtil.ARTIFACT_ID) ?: return emptyArray()
        if (xmlTag.name != MavenArtifactUtil.ARTIFACT_ID) return emptyArray()
        val value = ElementManipulators.getValueText(element)
        val range = ElementManipulators.getValueTextRange(element)
        val dataHolder = CachedModuleDataService.getCurrentData()
        val moduleData = dataHolder.modules.find { it.artifactId == value }
        return if (moduleData != null) {
            getModulePsiReferences(moduleData, element, xmlTag, range)
        } else {
            arrayOf(MavenArtifactIdPsiReference(value, xmlTag, range, moduleName))
        }
    }

    private fun getModulePsiReferences(
        moduleData: CachedModuleData, element: XmlTag, xmlTag: XmlTag, range: TextRange
    ): Array<PsiReference> {
        val virtualFile = LocalFileSystem.getInstance()
            .findFileByNioFile(Path(moduleData.configPath)) ?: return emptyArray()
        val psiFile = PsiManager.getInstance(element.project).findFile(virtualFile) as? XmlFile ?: return emptyArray()
        val artifactIdTag = psiFile.rootTag?.subTags
            ?.find { it.name == MavenArtifactUtil.ARTIFACT_ID } ?: return emptyArray()
        return arrayOf(Immediate(xmlTag, range, true, artifactIdTag))
    }
}

package ru.rzn.gmyasoedov.gmaven.dom

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.util.MavenArtifactInfo
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name

object XmlPsiUtil {

    fun getParentPath(
        xmlParentTag: XmlTag, localRepos: Collection<String>, properties: Map<String, String> = emptyMap()
    ): Path? {
        try {
            val groupIdText = xmlParentTag.getSubTagText(MavenArtifactUtil.GROUP_ID)
            val groupId = getPlaceHolderValue(groupIdText, properties) ?: return null
            val artifactId = xmlParentTag.getSubTagText(MavenArtifactUtil.ARTIFACT_ID) ?: return null

            val dataHolder = CachedModuleDataService.getDataHolder(xmlParentTag.project)
            val configPath = dataHolder.findModuleData(groupId, artifactId)?.configPath
            if (configPath != null) return Path(configPath)
            val versionText = xmlParentTag.getSubTagText(MavenArtifactUtil.VERSION)
            val version = getPlaceHolderValue(versionText, properties) ?: return null
            return searchInLocalRepo(groupId, artifactId, version, localRepos)
        } catch (_: Exception) {
            return null
        }
    }

    fun getPlaceHolderValue(subTagText: String?, properties: Map<String, String>): String? {
        if (subTagText != null && subTagText.startsWith("\${") && subTagText.endsWith("}")) {
            val propertyName = subTagText.subSequence(2, subTagText.length - 1)
            return properties[propertyName]
        }
        return subTagText
    }

    fun getXmlFile(path: Path, project: Project): XmlFile? {
        val filePath = if (path.toFile().isDirectory()) path.resolve(GMavenConstants.POM_XML) else path
        if (!filePath.exists()) return null

        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(filePath) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
        return if (psiFile is XmlFile) psiFile else {
            try {
                PsiFileFactory.getInstance(project)
                    .createFileFromText(filePath.name, XMLLanguage.INSTANCE, virtualFile.readText()) as? XmlFile
            } catch (e: Exception) {
                null
            }
        }
    }

    fun searchInLocalRepo(groupId: String, artifactId: String, version: String, localRepos: Collection<String>): Path? {
        for (localRepoPath in localRepos) {
            val artifactPath = MavenArtifactUtil
                .getArtifactNioPath(Path.of(localRepoPath), groupId, artifactId, version, "pom")
            if (artifactPath.exists()) return artifactPath
        }
        return null
    }

    fun fillProperties(
        xmlFile: XmlFile, propertiesMap: MutableMap<String, XmlTag> = TreeMap(), localRepos: Collection<String>,
        deepCount: Int = 0
    ) {
        if (deepCount > 100) return
        val properties = xmlFile.rootTag?.findFirstSubTag(MavenArtifactUtil.PROPERTIES)?.subTags ?: emptyArray()
        for (property in properties) {
            propertiesMap.putIfAbsent(property.name, property)
        }
        val parentTag = xmlFile.rootTag?.findFirstSubTag(MavenArtifactUtil.PARENT) ?: return
        val parentPath = getParentPath(parentTag, localRepos) ?: return
        val parentXmlFile = getXmlFile(parentPath, xmlFile.project) ?: return
        if (xmlFile.virtualFile == parentXmlFile.virtualFile) return
        fillProperties(parentXmlFile, propertiesMap, localRepos, deepCount + 1)
    }

    fun getDependencyManagementLibraryCache(xmlFile: PsiFile): Set<MavenArtifactInfo> {
        return CachedValuesManager.getManager(xmlFile.project).getCachedValue(xmlFile) {
            CachedValueProvider.Result
                .create(
                    getDependencyManagementLibrary(xmlFile),
                    ProjectRootManager.getInstance(xmlFile.project)
                )
        }
    }

    private fun getDependencyManagementLibrary(xmlFile: PsiFile): Set<MavenArtifactInfo> {
        if (xmlFile !is XmlFile) return emptySet()
        val repos = getLocalRepos(xmlFile)
        val librarySet = mutableSetOf<MavenArtifactInfo>()
        fillDependencyManagement(xmlFile, librarySet, mutableMapOf(), repos, 0)
        return librarySet
    }

    //todo to getLocalRepos(project): Collection<String> -> mavenUtil
    fun getLocalRepos(element: PsiElement): Collection<String> {
        return MavenSettings.getInstance(element.project).linkedProjectsSettings.asSequence()
            .mapNotNull { it.localRepositoryPath }.toSet()
    }

    private fun fillDependencyManagement(
        xmlFile: XmlFile, librarySet: MutableSet<MavenArtifactInfo>,
        propertiesMap: MutableMap<String, String>, localRepos: Collection<String>, deepCount: Int = 0
    ) {
        if (deepCount > 500) return
        val dependencies = xmlFile.rootTag?.findFirstSubTag(MavenArtifactUtil.DEPENDENCY_MANAGEMENT)
            ?.findFirstSubTag(MavenArtifactUtil.DEPENDENCIES)?.findSubTags(MavenArtifactUtil.DEPENDENCY) ?: emptyArray()
        val plugins = xmlFile.rootTag?.findFirstSubTag(MavenArtifactUtil.BUILD)
            ?.findFirstSubTag(MavenArtifactUtil.PLUGIN_MANAGEMENT)
            ?.findFirstSubTag(MavenArtifactUtil.PLUGINS)?.findSubTags(MavenArtifactUtil.PLUGIN) ?: emptyArray()
        val properties = xmlFile.rootTag?.findFirstSubTag(MavenArtifactUtil.PROPERTIES)?.subTags ?: emptyArray()
        for (property in properties) {
            propertiesMap.putIfAbsent(property.name, property.value.text)
        }
        for (each in dependencies) {
            dependencyToLibrary(each)?.let { librarySet.add(it) }
            if (each.getSubTagText(MavenArtifactUtil.TYPE) == "pom" && each.getSubTagText(MavenArtifactUtil.SCOPE) == "import") {
                val parentPath = getParentPath(each, localRepos, propertiesMap) ?: continue
                val parentXmlFile = getXmlFile(parentPath, xmlFile.project) ?: continue
                fillDependencyManagement(parentXmlFile, librarySet, propertiesMap, localRepos, deepCount + 1)
            }
        }
        for (each in plugins) {
            dependencyToLibrary(each)?.let { librarySet.add(it) }
            if (each.getSubTagText(MavenArtifactUtil.TYPE) == "pom" && each.getSubTagText(MavenArtifactUtil.SCOPE) == "import") {
                val parentPath = getParentPath(each, localRepos, propertiesMap) ?: continue
                val parentXmlFile = getXmlFile(parentPath, xmlFile.project) ?: continue
                fillDependencyManagement(parentXmlFile, librarySet, propertiesMap, localRepos, deepCount + 1)
            }
        }
        val parentTag = xmlFile.rootTag?.findFirstSubTag(MavenArtifactUtil.PARENT) ?: return
        val parentPath = getParentPath(parentTag, localRepos, propertiesMap) ?: return
        val parentXmlFile = getXmlFile(parentPath, xmlFile.project) ?: return
        if (xmlFile.virtualFile == parentXmlFile.virtualFile) return
        fillDependencyManagement(parentXmlFile, librarySet, propertiesMap, localRepos, deepCount + 1)
    }

    private fun dependencyToLibrary(xmlTag: XmlTag): MavenArtifactInfo? {
        val groupId = xmlTag.getSubTagText(MavenArtifactUtil.GROUP_ID) ?: return null
        val artifactId = xmlTag.getSubTagText(MavenArtifactUtil.ARTIFACT_ID) ?: return null
        val version = xmlTag.getSubTagText(MavenArtifactUtil.VERSION) ?: return null
        val id = "$groupId:$artifactId:$version"
        return MavenArtifactInfo(id, groupId, artifactId, version)
    }
}

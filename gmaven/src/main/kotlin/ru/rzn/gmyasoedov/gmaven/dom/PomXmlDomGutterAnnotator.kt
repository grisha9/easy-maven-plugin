package ru.rzn.gmyasoedov.gmaven.dom

import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.find.FindBundle
import com.intellij.find.FindModel
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import icons.GMavenIcons
import kotlinx.collections.immutable.toImmutableSet
import ru.rzn.gmyasoedov.gmaven.dom.XmlPsiUtil.getPlaceHolderValue
import ru.rzn.gmyasoedov.gmaven.settings.MavenSettings
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil.*
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.util.*
import javax.swing.Icon

class PomXmlDomGutterAnnotator : Annotator {

    private var projectSettings: ProjectSettings? = null
    private var dependencyManagement: DependencyManagement? = null

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (MavenUtils.pluginEnabled(MavenUtils.INTELLIJ_MAVEN_PLUGIN_ID)) return
        if (!Registry.`is`("gmaven.gutter.annotation")) return
        val xmlTag = element as? XmlTag ?: return
        val project = element.project
        val xmlFile = xmlTag.containingFile as? XmlFile ?: return
        val tagName = xmlTag.name

        val settingsHolder = getProjectSettings(project)

        if (tagName == PARENT) {
            val management = getDependencyManagement(xmlFile, settingsHolder)
            val parentPath = XmlPsiUtil
                .getParentPath(xmlTag, settingsHolder.localRepos, management.properties) ?: return
            addGutterIcon(parentPath, xmlTag, holder, GMavenIcons.ParentProject, "GMaven:parent")
        } else if (tagName == MODULE) {
            val moduleName = xmlTag.value.text
            val modulePath = xmlTag.containingFile.parent?.virtualFile?.toNioPathOrNull()?.resolve(moduleName) ?: return
            addGutterIcon(modulePath, xmlTag, holder, AllIcons.Gutter.OverridenMethod, "GMaven:module")
        } else if (tagName == DEPENDENCY && xmlTag.parentTag?.parentTag?.name != DEPENDENCY_MANAGEMENT) {
            val management = getDependencyManagement(xmlFile, settingsHolder)
            addGutterIcon(xmlTag, management, holder, false)
        } else if (tagName == PLUGIN && xmlTag.parentTag?.parentTag?.name != PLUGIN_MANAGEMENT) {
            val management = getDependencyManagement(xmlFile, settingsHolder)
            addGutterIcon(xmlTag, management, holder, true)
        } else if (tagName == DEPENDENCY && xmlTag.parentTag?.parentTag?.name == DEPENDENCY_MANAGEMENT) {
            addSearchArtifactGutterIcon(xmlTag, holder)
        } else if (tagName == PLUGIN && xmlTag.parentTag?.parentTag?.name == PLUGIN_MANAGEMENT) {
            addSearchArtifactGutterIcon(xmlTag, holder)
        }
    }

    private fun getProjectSettings(project: Project): ProjectSettings {
        if (projectSettings == null) {
            projectSettings = collectSettings(project)
        }
        return projectSettings!!
    }

    private fun addGutterIcon(
        xmlTag: XmlTag,
        management: DependencyManagement,
        holder: AnnotationHolder,
        isPlugin: Boolean
    ) {
        dependencyToString(xmlTag)
            ?.let { if (isPlugin) management.plugins[it] else management.dependencies[it] }
            ?.also {
                NavigationGutterIconBuilder
                    .create(AllIcons.Gutter.OverridingMethod)
                    .setTarget(it)
                    .setTooltipText(generateTooltip(it, isPlugin, management))
                    .createGutterIcon(holder, xmlTag)
            }
    }

    private fun addSearchArtifactGutterIcon(xmlTag: XmlTag, holder: AnnotationHolder) {
        val artifactIdTag = xmlTag.findFirstSubTag(ARTIFACT_ID) ?: return
        val artifactId = artifactIdTag.value.text
        if (artifactId.isEmpty()) return

        val renderer = NavigationGutterIconBuilder
            .create(AllIcons.Actions.Search)
            .setTarget(artifactIdTag)
            .setTooltipText(FindBundle.message("find.what.group") + " $artifactId")
            .createGutterIconRenderer(xmlTag.project) { event, element -> performSearch(event, element, artifactId) }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(artifactIdTag)
            .gutterIconRenderer(renderer)
            .create()
    }

    private fun performSearch(event: MouseEvent, element: PsiElement, artifactId: String) {
        val findModel = FindModel()
        findModel.stringToFind = "<artifactId>$artifactId</artifactId>"
        findModel.isProjectScope = true
        val dataContext = DataManager.getInstance().getDataContext(event.component)
        FindInProjectManager.getInstance(element.project).findInProject(dataContext, findModel)
    }

    private fun addGutterIcon(
        path: Path, xmlTag: XmlTag, holder: AnnotationHolder, icon: Icon, text: String
    ): Boolean {
        val xmlFile = XmlPsiUtil.getXmlFile(path, xmlTag.project) ?: return false
        NavigationGutterIconBuilder
            .create(icon)
            .setTargets(xmlFile)
            .setTooltipText(text)
            .createGutterIcon(holder, xmlTag)
        return true
    }

    private fun collectSettings(project: Project): ProjectSettings {
        val linkedProjectsSettings = MavenSettings.getInstance(project).linkedProjectsSettings
        if (linkedProjectsSettings.isEmpty()) return ProjectSettings(emptyList(), emptySet())
        if (linkedProjectsSettings.size == 1) {
            val settings = linkedProjectsSettings.first()
            val repos = settings.localRepositoryPath?.let { listOf(it) } ?: emptyList()
            return ProjectSettings(repos, settings.modules)
        }
        val repos = linkedProjectsSettings.mapNotNull { it.localRepositoryPath }
        val modules = linkedProjectsSettings.flatMap { it.modules }.toImmutableSet()
        return ProjectSettings(repos, modules)
    }

    private fun getDependencyManagement(xmlFile: XmlFile, projectSettings: ProjectSettings): DependencyManagement {
        if (dependencyManagement == null) {
            val management = DependencyManagement()
            fillDependencyManagement(xmlFile, management, projectSettings)
            dependencyManagement = management
        }
        return dependencyManagement!!
    }

    private fun fillDependencyManagement(
        xmlFile: XmlFile, dependencyManagement: DependencyManagement, projectSettings: ProjectSettings,
        deepCount: Int = 0
    ) {
        if (deepCount > 500) return
        val dependencies = xmlFile.rootTag?.findFirstSubTag(DEPENDENCY_MANAGEMENT)
            ?.findFirstSubTag(DEPENDENCIES)?.findSubTags(DEPENDENCY) ?: emptyArray()
        val plugins = xmlFile.rootTag?.findFirstSubTag(BUILD)?.findFirstSubTag(PLUGIN_MANAGEMENT)
            ?.findFirstSubTag(PLUGINS)?.findSubTags(PLUGIN) ?: emptyArray()
        val properties = xmlFile.rootTag?.findFirstSubTag(PROPERTIES)?.subTags ?: emptyArray()
        for (property in properties) {
            dependencyManagement.properties.putIfAbsent(property.name, property.value.text)
        }
        for (each in dependencies) {
            dependencyToString(each)?.let { dependencyManagement.dependencies.putIfAbsent(it, each) }
            if (each.getSubTagText(TYPE) == "pom" && each.getSubTagText(SCOPE) == "import") {
                val parentPath = XmlPsiUtil
                    .getParentPath(each, projectSettings.localRepos, dependencyManagement.properties) ?: continue
                val parentXmlFile = XmlPsiUtil.getXmlFile(parentPath, xmlFile.project) ?: continue
                fillDependencyManagement(parentXmlFile, dependencyManagement, projectSettings, deepCount + 1)
            }
        }
        for (each in plugins) {
            dependencyToString(each)?.let { dependencyManagement.plugins.putIfAbsent(it, each) }
            if (each.getSubTagText(TYPE) == "pom" && each.getSubTagText(SCOPE) == "import") {
                val parentPath = XmlPsiUtil
                    .getParentPath(each, projectSettings.localRepos, dependencyManagement.properties) ?: continue
                val parentXmlFile = XmlPsiUtil.getXmlFile(parentPath, xmlFile.project) ?: continue
                fillDependencyManagement(parentXmlFile, dependencyManagement, projectSettings, deepCount + 1)
            }
        }
        val parentTag = xmlFile.rootTag?.findFirstSubTag(PARENT) ?: return
        val parentPath = XmlPsiUtil.getParentPath(
            parentTag,
            projectSettings.localRepos,
            dependencyManagement.properties
        ) ?: return
        val parentXmlFile = XmlPsiUtil.getXmlFile(parentPath, xmlFile.project) ?: return
        if (xmlFile.virtualFile == parentXmlFile.virtualFile) return
        fillDependencyManagement(parentXmlFile, dependencyManagement, projectSettings, deepCount + 1)
    }

    private fun dependencyToString(xmlTag: XmlTag): String? {
        val groupId = xmlTag.getSubTagText(GROUP_ID) ?: return null
        val artifactId = xmlTag.getSubTagText(ARTIFACT_ID) ?: return null
        return "$groupId:$artifactId"
    }

    private fun generateTooltip(xmlTag: XmlTag, isPlugin: Boolean, dependencyManagement: DependencyManagement): String {
        val groupId = getPlaceHolderValue(xmlTag.getSubTagText(GROUP_ID), dependencyManagement.properties)
        val version = getPlaceHolderValue(xmlTag.getSubTagText(VERSION), dependencyManagement.properties)
        val res = StringBuilder()
        res.append(if (isPlugin) "<plugin>\n" else "<dependency>\n")
        res.append("    <groupId>").append(groupId).append("</groupId>\n")
        res.append("    <artifactId>").append(xmlTag.getSubTagText(ARTIFACT_ID)).append("</artifactId>\n")
        if (version != null) {
            res.append("    <version>").append(version).append("</version>\n")
        }
        res.append(if (isPlugin) "</plugin>\n" else "</dependency>\n")
        return StringUtil.escapeXmlEntities(res.toString()).replace(" ", "&nbsp;") //NON-NLS
    }

    private data class ProjectSettings(val localRepos: List<String>, val modules: Set<String>)
}

data class DependencyManagement(
    val dependencies: MutableMap<String, XmlTag> = TreeMap(),
    val plugins: MutableMap<String, XmlTag> = TreeMap(),
    val properties: MutableMap<String, String> = TreeMap()
)
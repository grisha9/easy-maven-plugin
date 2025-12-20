package ru.rzn.gmyasoedov.gmaven.dom.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.util.Processor
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.IDEA_PSI_EDIT_TOKEN
import ru.rzn.gmyasoedov.gmaven.dom.XmlPsiUtil
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.util.MavenArtifactInfo
import ru.rzn.gmyasoedov.gmaven.util.MvnUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenArtifactUtil.*
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import kotlin.io.path.*


private const val TIMEOUT_PROMISE_MS = 10_000
private const val TIMEOUT_REQUEST_MS = 1_000

class MavenCoordinateCompletionContributor : CompletionContributor() {
    private val supportTagNames = setOf(ARTIFACT_ID, GROUP_ID, VERSION)

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return

        val currentTimeMillis = System.currentTimeMillis()
        val get = Util.timeStamp.get()
        if (currentTimeMillis - get < getTimeout()) return
        Util.timeStamp.set(currentTimeMillis)

        getCompletionConsumer(parameters, result)?.accept(result)
    }

    private fun getCompletionConsumer(
        parameters: CompletionParameters, resultSet: CompletionResultSet
    ): Consumer<CompletionResultSet>? {
        val element: PsiElement = parameters.position
        val xmlText = element.parent as? XmlText ?: return null
        val tagElement = xmlText.parent as? XmlTag ?: return null
        if (!supportTagNames.contains(tagElement.name)) return null
        val parentXmlTag = tagElement.parent as? XmlTag ?: return null

        val originalFile = parameters.originalFile
        val configFilePath = originalFile.virtualFile.toNioPathOrNull()?.absolutePathString() ?: return null
        val localRepos = getLocalRepos(configFilePath, originalFile).takeIf { it.isNotEmpty() } ?: return null

        return when (tagElement.name) {
            VERSION -> {
                val artifactId = parentXmlTag.getSubTagText(ARTIFACT_ID) ?: return null
                val groupId = parentXmlTag.getSubTagText(GROUP_ID) ?: return null
                VersionContributor(artifactId, groupId, localRepos)
            }

            ARTIFACT_ID -> GAVContributor(tagElement, parentXmlTag, localRepos, resultSet)

            else -> fillGroupIdVariants(parameters, tagElement, localRepos, resultSet)
        }
    }

    private fun fillGroupIdVariants(
        parameters: CompletionParameters,
        tagElement: XmlTag,
        localRepos: Collection<String>,
        resultSet: CompletionResultSet
    ): Nothing? {
        val groupId = getTextUnderCursor(parameters)

        val allGroupIds = CachedModuleDataService.getLibrary(tagElement.project).map { it.g }
        allGroupIds.filter { it.contains(groupId) }.forEach {
            resultSet.addElement(LookupElementBuilder.create(it).withInsertHandler(GroupInsertHandler))
        }
        popularGroupIds.filter { it.contains(groupId) }.forEach {
            resultSet.addElement(LookupElementBuilder.create(it).withInsertHandler(GroupInsertHandler))
        }
        val folders = getSplitGroupIdOnFolders(groupId)
        val parentFolder = folders.joinToString(".")
        val result = localRepos.flatMapTo(mutableSetOf()) { getListFiles(it, folders, parentFolder) }
        result.forEach { resultSet.addElement(LookupElementBuilder.create(it).withInsertHandler(GroupInsertHandler)) }

        resultSet.stopHere()
        return null
    }

    private fun getListFiles(repo: String, folders: List<String>, parentFolder: String): List<String> {
        val path = Path(repo, *folders.toTypedArray())
        return try {
            path.listDirectoryEntries().filter { it.isDirectory() }.map { fullGroupIdPath(parentFolder, it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fullGroupIdPath(parentFolder: String, it: Path) =
        if (parentFolder.isEmpty()) it.fileName.toString() else parentFolder + "." + it.fileName

    private fun getTextUnderCursor(parameters: CompletionParameters): String {
        return parameters.position.text.substringBefore(IDEA_PSI_EDIT_TOKEN)
    }

    private fun getSplitGroupIdOnFolders(groupId: String): List<String> {
        val lastDotIndex = groupId.lastIndexOf(".")
        if (lastDotIndex < 0) return emptyList()
        return groupId.take(lastDotIndex).split(".")
    }

    object Util {
        val timeStamp = AtomicLong(0)
    }

    private fun getTimeout(): Int {
        return 200
    }
}

private class VersionContributor(
    val artifactId: String,
    val groupId: String,
    val localRepos: Collection<String>
) : Consumer<CompletionResultSet> {

    override fun accept(result: CompletionResultSet) {
        val folders = groupId.split(".")
        val localVersionList = localRepos.flatMapTo(mutableSetOf()) {
            getListVersions(it, folders, artifactId)
        }
        for (each in localVersionList) {
            result.addElement(LookupElementBuilder.create(each))
        }
    }

    private fun getListVersions(repo: String, folders: List<String>, artifactId: String): List<String> {
        val path = Path(repo, *folders.toTypedArray()).resolve(artifactId)
        return try {
            path.listDirectoryEntries().asSequence()
                .filter { it.isDirectory() }
                .filter { it.name.firstOrNull()?.isDigit() == true }
                .map { it.name }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

private class GAVContributor(
    val artifactIdTag: XmlTag,
    val parentXmlTag: XmlTag,
    val localRepos: Collection<String>,
    val resultSet: CompletionResultSet
) : Consumer<CompletionResultSet> {
    override fun accept(result: CompletionResultSet) {
        val queryText = result.prefixMatcher.prefix
        val isPlugin = parentXmlTag.name == PLUGIN
        val groupId = getGroupId(isPlugin)

        val promise: AsyncPromise<List<MavenArtifactInfo>>? = null//asyncPromise(queryText, groupId)

        val findInModules = findProjectModulesGAV(artifactIdTag, queryText)
        setLookupResult(findInModules, result)

        val artifactFromManagementData = XmlPsiUtil.getDependencyManagementLibraryCache(artifactIdTag.containingFile)
            .filter { filterArtifact(groupId, queryText, it) }
        val artifactFromProjectStructure = CachedModuleDataService.getLibrary(artifactIdTag.project)
            .filter { filterArtifact(groupId, queryText, it) }

        setLookupResult(artifactFromManagementData, result)
        setLookupResult(artifactFromProjectStructure, result)

        val folders = groupId?.split(".")?.takeIf { it.isNotEmpty() } ?: return
        val localArtefactIds = localRepos.flatMapTo(mutableSetOf()) { getArtifactIds(it, folders) }
        localArtefactIds.forEach { resultSet.addElement(LookupElementBuilder.create(it)) }
        resultSet.stopHere()
        if (promise == null) return
        val startMillis = System.currentTimeMillis()
        while (promise.getState() == Promise.State.PENDING
            && System.currentTimeMillis() - startMillis < TIMEOUT_PROMISE_MS
        ) {
            ProgressManager.checkCanceled()
            Thread.yield()
        }
        if (!promise.isDone()) return

        val artifactInfoList = promise.get() ?: return
        setLookupResult(artifactInfoList, result)
    }

    private fun getGroupId(isPlugin: Boolean): String? {
        val groupId = parentXmlTag.getSubTagText(GROUP_ID)
        if (groupId == null && isPlugin) {
            return PLUGIN_GROUP_ID
        }
        return groupId
    }

    private fun getArtifactIds(repo: String, folders: List<String>): List<String> {
        val path = Path(repo, *folders.toTypedArray())
        return try {
            path.listDirectoryEntries().asSequence()
                .filter { it.isDirectory() }
                .filter { it.name.firstOrNull()?.isDigit() == false }
                .map { it.name }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun setLookupResult(
        artifactInfoList: Collection<MavenArtifactInfo>, result: CompletionResultSet
    ) {
        artifactInfoList.forEach {
            result.addElement(
                LookupElementBuilder.create(it, it.a)
                    .withPresentableText(it.id)
                    .withInsertHandler(GAVInsertHandler)
            )
        }
    }

    private fun findProjectModulesGAV(artifactIdTag: XmlTag, query: String): List<MavenArtifactInfo> {
        if (query.length < 2) return emptyList()
        return CachedModuleDataService
            .getDataHolder(artifactIdTag.project).modules.asSequence()
            .filter { it.artifactId.contains(query, true) }
            .map { MavenArtifactInfo(it.groupId + ":" + it.artifactId, it.groupId, it.artifactId, it.version) }
            .toList()
    }

    private fun filterArtifact(groupId: String?, artifactId: String, data: MavenArtifactInfo): Boolean {
        return if (groupId == null || groupId.length < 3) {
            data.a.contains(artifactId)
        } else {
            data.g.contains(groupId)
        }
    }
}

private object GAVInsertHandler : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val artifactInfo: MavenArtifactInfo = item.`object` as? MavenArtifactInfo ?: return
        val contextFile = context.file as? XmlFile ?: return
        val element = contextFile.findElementAt(context.startOffset)
        val xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java) ?: return
        val isArtifactTag = xmlTag.name == ARTIFACT_ID
        val targetTag = if (isArtifactTag) xmlTag.parentTag?.findFirstSubTag(GROUP_ID) else
            xmlTag.parentTag?.findFirstSubTag(ARTIFACT_ID)
        val versionTag = xmlTag.parentTag?.findFirstSubTag(VERSION)
        if (artifactInfo.v != null) {
            versionTag?.value?.text = artifactInfo.v
        }
        context.commitDocument()
        targetTag?.value?.text = if (isArtifactTag) artifactInfo.g else artifactInfo.a
    }
}

private object GroupInsertHandler : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val groupId: String = item.`object` as? String ?: return
        val contextFile = context.file as? XmlFile ?: return
        val element = contextFile.findElementAt(context.startOffset)
        val xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java) ?: return
        context.commitDocument()
        xmlTag.value.text = groupId
    }
}

private val popularGroupIds: List<String> = listOf(
    "junit",
    "org.slf4j",
    "org.jetbrains.kotlin",
    "com.google.guava",
    "org.scala-lang",
    "org.mockito",
    "com.fasterxml.jackson.core",
    "org.apache.commons",
    "ch.qos.logback",
    "commons-io",
    "org.projectlombok",
    "com.google.code.gson",
    "com.android.support",
    "org.clojure",
    "javax.servlet",
    "log4j",
    "org.scalatest",
    "org.assertj",
    "org.junit.jupiter",
    "org.apache.httpcomponents",
    "org.springframework",
    "org.springframework.boot",
    "com.google.code.findbugs",
    "org.renjin",
    "commons-lang",
    "commons-codec",
    "androidx.appcompat",
    "commons-logging",
    "org.testng",
    "org.apache.logging.log4j",
    "com.squareup.okhttp3",
    "joda-time",
    "com.h2database",
    "org.apache.maven",
    "org.hamcrest",
    "mysql",
    "org.osgi",
    "javax.inject",
    "androidx.core",
    "org.jetbrains.kotlinx",
    "commons-collections",
    "javax.validation",
    "javax.annotation",
    "javax.xml.bind",
    "com.fasterxml.jackson.datatype",
    "com.fasterxml.jackson.dataformat",
    "clojure-complete",
    "org.scala-js",
    "org.json",
    "commons-beanutils",
    "com.google.protobuf",
    "org.apache.maven.plugin-tools",
    "com.squareup.retrofit2",
    "com.google.android.material",
    "com.google.inject",
    "org.easymock",
    "org.codehaus.groovy",
    "commons-cli",
    "org.yaml",
    "com.android.support",
    "org.powermock",
    "org.postgresql",
    "org.hibernate",
    "com.oracle.jdbc "
)

private fun getLocalRepos(configFilePath: String, originalFile: PsiFile): Collection<String> {
    if (CachedModuleDataService.getDataHolder(originalFile.project).isConfigPath(configFilePath)) {
        return MvnUtil.getLocalRepos(originalFile.project)
    }

    if (!MavenAdvancedSettingsState.getInstance().completionEasyMavenOnly) {
        val module = ModuleUtilCore.findModuleForFile(originalFile) ?: return emptyList()
        return findMavenLocalReposCacheable(module)
    }
    return emptyList()
}

private fun findMavenLocalReposCacheable(module: Module): List<String> {
    return CachedValuesManager.getManager(module.project).getCachedValue(module) {
        CachedValueProvider.Result(
            findMavenLocalRepos(module),
            JavaLibraryModificationTracker.getInstance(module.project)
        )
    }
}

private fun findMavenLocalRepos(module: Module): List<String> {
    var virtualFileM2: VirtualFile? = null
    OrderEnumerator.orderEntries(module).forEachLibrary(Processor { library ->
        if (virtualFileM2 != null) return@Processor false
        val virtualFiles = library.getFiles(OrderRootType.CLASSES)
        val virtualFile = virtualFiles.firstOrNull { it.canonicalPath?.contains("/.m2/repository") == true }
        if (virtualFile != null) {
            virtualFileM2 = virtualFile
            return@Processor false
        }
        return@Processor true
    })
    val canonicalPath = virtualFileM2?.canonicalPath ?: return emptyList()
    val repositoryPath = canonicalPath.substringBefore("/.m2/repository") + "/.m2/repository"
    return listOf(Path(repositoryPath).absolutePathString())
}
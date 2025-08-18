package ru.rzn.gmyasoedov.gmaven

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.rt.execution.junit.FileComparisonData
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ExceptionUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import java.awt.HeadlessException
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

abstract class MavenTestCase : UsefulTestCase() {
    private var ourTempDir: File? = null

    private var myTestFixture: IdeaProjectTestFixture? = null

    private var myProject: Project? = null

    private var myDir: File? = null
    private var myProjectRoot: VirtualFile? = null

    private var myProjectPom: VirtualFile? = null
    private val myAllPoms: MutableList<VirtualFile> = ArrayList()

    var testFixture: IdeaProjectTestFixture
        get() = myTestFixture!!
        set(testFixture) {
            myTestFixture = testFixture
        }

    fun setTestFixtureNull() {
        myTestFixture = null
    }

    val project: Project
        get() = myProject!!

    val dir: File
        get() = myDir!!

    val projectRoot: VirtualFile
        get() = myProjectRoot!!

    var projectPom: VirtualFile
        get() = myProjectPom!!
        set(projectPom) {
            myProjectPom = projectPom
        }

    val allPoms: List<VirtualFile>
        get() = myAllPoms

    fun addPom(pom: VirtualFile) {
        myAllPoms.add(pom)
    }

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        setUpFixtures()
        myProject = myTestFixture!!.project
        ensureTempDirCreated()

        myDir = File(ourTempDir, getTestName(false))
        FileUtil.ensureExists(myDir!!)

        runInEdtAndWait {
            try {
                WriteAction.run<Exception> { this.setUpInWriteAction() }
            } catch (e: Throwable) {
                try {
                    tearDown()
                } catch (e1: Exception) {
                    e1.printStackTrace()
                }
                throw RuntimeException(e)
            }
        }
    }

    @Throws(Throwable::class)
    override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
        LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
            override fun processError(
                category: String,
                message: String,
                details: Array<String>,
                t: Throwable?
            ): Set<Action> {
                val intercept = t != null && ((t.message ?: "").contains("The network name cannot be found") &&
                        message.contains("Couldn't read shelf information") ||
                        "JDK annotations not found" == t.message && "#com.intellij.openapi.projectRoots.impl.JavaSdkImpl" == category)
                return if (intercept) Action.NONE else Action.ALL
            }
        }) { super.runBare(testRunnable) }
    }

    @Throws(Exception::class)
    override fun tearDown() {
        RunAll(
            ThrowableRunnable { myProject = null },
            ThrowableRunnable { doTearDownFixtures() },
            ThrowableRunnable { deleteDirOnTearDown(myDir) },
            ThrowableRunnable { super.tearDown() }
        ).run()
    }

    private fun doTearDownFixtures() {
        if (ApplicationManager.getApplication().isDispatchThread) {
            runInEdtAndWait { tearDownFixtures() }
        } else {
            runBlockingMaybeCancellable {
                withContext(Dispatchers.EDT) {
                    tearDownFixtures()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun ensureTempDirCreated() {
        if (ourTempDir != null) return

        ourTempDir = File(FileUtil.getTempDirectory(), "mavenTests")
        FileUtil.delete(ourTempDir!!)
        FileUtil.ensureExists(ourTempDir!!)
    }

    @Throws(Exception::class)
    protected open fun setUpFixtures() {
        val isDirectoryBasedProject = useDirectoryBasedProjectFormat()
        myTestFixture = IdeaTestFixtureFactory.getFixtureFactory()
            .createFixtureBuilder(name, isDirectoryBasedProject).fixture
        myTestFixture!!.setUp()
    }

    protected open fun useDirectoryBasedProjectFormat(): Boolean {
        return false
    }

    @Throws(Exception::class)
    protected open fun setUpInWriteAction() {
        val projectDir = File(myDir, "project")
        projectDir.mkdirs()
        myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir)
    }

    @Throws(Exception::class)
    protected open fun tearDownFixtures() {
        try {
            ApplicationManager.getApplication().runWriteAction {
                val allJdks = ProjectJdkTable.getInstance().allJdks
                for (aech in allJdks) {
                    ProjectJdkTable.getInstance().removeJdk(aech)
                }
            }
            myTestFixture!!.tearDown()
        } finally {
            myTestFixture = null
        }
    }

    @Throws(Throwable::class)
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        try {
            super.runTestRunnable(testRunnable)
        } catch (throwable: Throwable) {
            if (ExceptionUtil.causedBy(throwable, HeadlessException::class.java)) {
                printIgnoredMessage("Doesn't work in Headless environment")
            }
            throw throwable
        }
    }

    protected val projectPath: String
        get() = myProjectRoot!!.path

    protected val parentPath: String
        get() = myProjectRoot!!.parent.path

    protected fun pathFromBasedir(relPath: String): String {
        return pathFromBasedir(myProjectRoot, relPath)
    }

    protected fun createModule(name: String, type: ModuleType<*>): Module {
        try {
            return WriteCommandAction.writeCommandAction(myProject).compute<Module, IOException> {
                val f = createProjectSubFile("$name/$name.iml")
                val module = getInstance(myProject!!).newModule(f.path, type.id)
                PsiTestUtil.addContentRoot(module, f.parent)
                module
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    protected fun createModule(name: String): Module = createModule(name, StdModuleTypes.JAVA)


    protected fun createProjectPom(
        @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String
    ): VirtualFile {
        return createPomFile(myProjectRoot, xml).also { myProjectPom = it }
    }

    protected fun createModulePom(
        relativePath: String,
        @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String?
    ): VirtualFile {
        return createPomFile(createProjectSubDir(relativePath), xml)
    }

    protected fun createPomFile(
        dir: VirtualFile?,
        @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String?
    ): VirtualFile {
        return createPomFile(dir, "pom.xml", xml)
    }

    protected fun createPomFile(
        dir: VirtualFile?, fileName: String? = "pom.xml",
        @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String?
    ): VirtualFile {
        val pomName = fileName ?: "pom.xml"
        var f = dir!!.findChild(pomName)
        if (f == null) {
            try {
                f = WriteAction.computeAndWait<VirtualFile, IOException> { dir.createChildData(null, pomName) }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            myAllPoms.add(f)
        }
        setPomContent(f, xml)
        return f!!
    }

    protected fun createProfilesXmlOldStyle(xml: String): VirtualFile {
        return createProfilesFile(myProjectRoot, xml, true)
    }

    protected fun createProfilesXmlOldStyle(relativePath: String, xml: String): VirtualFile {
        return createProfilesFile(createProjectSubDir(relativePath), xml, true)
    }

    protected fun createProfilesXml(xml: String): VirtualFile {
        return createProfilesFile(myProjectRoot, xml, false)
    }

    protected fun createProfilesXml(relativePath: String, xml: String): VirtualFile {
        return createProfilesFile(createProjectSubDir(relativePath), xml, false)
    }

    protected fun createFullProfilesXml(content: String): VirtualFile {
        return createProfilesFile(myProjectRoot, content)
    }

    protected fun createFullProfilesXml(relativePath: String, content: String): VirtualFile {
        return createProfilesFile(createProjectSubDir(relativePath), content)
    }

    @Throws(IOException::class)
    protected fun deleteProfilesXml() {
        WriteCommandAction.writeCommandAction(myProject).run<IOException> {
            val f = myProjectRoot!!.findChild("profiles.xml")
            f?.delete(this)
        }
    }

    protected fun createProjectSubDirs(vararg relativePaths: String) {
        for (path in relativePaths) {
            createProjectSubDir(path)
        }
    }

    protected fun createProjectSubDir(relativePath: String): VirtualFile {
        val f = File(projectPath, relativePath)
        f.mkdirs()
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f)!!
    }

    @Throws(IOException::class)
    protected fun createProjectSubFile(relativePath: String): VirtualFile {
        val f = File(projectPath, relativePath)
        f.parentFile.mkdirs()
        f.createNewFile()
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f)!!
    }

    @Throws(IOException::class)
    protected fun createProjectSubFile(relativePath: String, content: String): VirtualFile {
        val file = createProjectSubFile(relativePath)
        setFileContent(file, content, false)
        return file
    }

    protected fun ignore(): Boolean {
        //printIgnoredMessage(null);
        return false
    }

    protected fun hasMavenInstallation(): Boolean {
        val result = testMavenHome != null
        if (!result) printIgnoredMessage("Maven installation not found")
        return result
    }

    private fun printIgnoredMessage(message: String?) {
        var toPrint = "Ignored"
        if (message != null) {
            toPrint += ", because $message"
        }
        toPrint += ": " + javaClass.simpleName + "." + name
        println(toPrint)
    }

    protected fun <R, E : Throwable?> runWriteAction(computable: ThrowableComputable<R, E>): R {
        return WriteCommandAction.writeCommandAction(myProject).compute(computable)
    }

    protected fun <E : Throwable?> runWriteAction(runnable: ThrowableRunnable<E>) {
        WriteCommandAction.writeCommandAction(myProject).run(runnable)
    }

    protected val MAVEN_COMPILER_PROPERTIES: String = """
    <properties>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <maven.compiler.source>11</maven.compiler.source>
            <maven.compiler.target>11</maven.compiler.target>
    </properties>
    
    """.trimIndent()

    protected fun deleteDirOnTearDown(dir: File?) {
        FileUtil.delete(dir!!)
        // cannot use reliably the result of the com.intellij.openapi.util.io.FileUtil.delete() method
        // because com.intellij.openapi.util.io.FileUtilRt.deleteRecursivelyNIO() does not honor this contract
        if (dir.exists()) {
            System.err.println("Cannot delete $dir")
            //printDirectoryContent(myDir);
            dir.deleteOnExit()
        }
    }

    private fun printDirectoryContent(dir: File) {
        val files = dir.listFiles()
        if (files == null) return

        for (file in files) {
            println(file.absolutePath)

            if (file.isDirectory) {
                printDirectoryContent(file)
            }
        }
    }

    protected val root: String
        get() {
            if (SystemInfo.isWindows) return "c:"
            return ""
        }

    protected fun pathFromBasedir(root: VirtualFile?, relPath: String): String {
        return FileUtil.toSystemIndependentName(root!!.path + "/" + relPath)
    }

    private fun createSettingsXmlContent(content: String): String {
        return "<settings>" +
                content +
                "</settings>\r\n"
    }

    private fun createProfilesFile(dir: VirtualFile?, xml: String, oldStyle: Boolean): VirtualFile {
        return createProfilesFile(dir, createValidProfiles(xml, oldStyle))
    }

    private fun createProfilesFile(dir: VirtualFile?, content: String): VirtualFile {
        var f = dir!!.findChild("profiles.xml")
        if (f == null) {
            try {
                f = WriteAction.computeAndWait<VirtualFile, IOException> { dir.createChildData(null, "profiles.xml") }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
        setFileContent(f, content, true)
        return f!!
    }

    @Language("XML")
    private fun createValidProfiles(@Language("XML") xml: String, oldStyle: Boolean): String {
        if (oldStyle) {
            return "<?xml version=\"1.0\"?>" +
                    "<profiles>" +
                    xml +
                    "</profiles>"
        }
        return "<?xml version=\"1.0\"?>" +
                "<profilesXml>" +
                "<profiles>" +
                xml +
                "</profiles>" +
                "</profilesXml>"
    }

    protected fun setPomContent(
        file: VirtualFile?,
        @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String?
    ) {
        setFileContent(file, createPomXml(xml), true)
    }

    protected fun setFileContent(file: VirtualFile?, content: String?, advanceStamps: Boolean) {
        try {
            WriteAction.runAndWait<IOException> {
                doSetFileContent(file!!, content!!, advanceStamps)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    protected suspend fun setPomContentAsync(
        file: VirtualFile,
        @Language(
            value = "XML",
            prefix = "<project>",
            suffix = "</project>"
        ) xml: String
    ) {
        setFileContentAsync(file, createPomXml(xml), true)
    }

    protected suspend fun setFileContentAsync(file: VirtualFile, content: String, advanceStamps: Boolean) {
        writeAction {
            doSetFileContent(file, content, advanceStamps)
        }
    }

    private fun doSetFileContent(file: VirtualFile, content: String, advanceStamps: Boolean) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val newModificationStamp = if (advanceStamps) -1 else file.modificationStamp
        val newTimeStamp = if (advanceStamps) file.timeStamp + 4000 else file.timeStamp
        MavenLog.LOG.debug("Set file content, modification stamp $newModificationStamp, time stamp $newTimeStamp, file $file")
        file.setBinaryContent(bytes, newModificationStamp, newTimeStamp)
    }

    protected fun <T> assertOrderedElementsAreEqual(actual: Collection<T>, expected: List<T>) {
        val s = "\nexpected: $expected\nactual: $actual"
        assertEquals(s, expected.size, actual.size)

        val actualList: List<T> = ArrayList(actual)
        for (i in expected.indices) {
            val expectedElement = expected[i]
            val actualElement = actualList[i]
            assertEquals(s, expectedElement, actualElement)
        }
    }

    protected fun <T> assertOrderedElementsAreEqual(actual: Collection<T>, vararg expected: T) {
        assertOrderedElementsAreEqual(actual, expected.toList())
    }

    protected fun <T> assertUnorderedElementsAreEqual(actual: Collection<T>, expected: Collection<T>) {
        assertSameElements(actual, expected)
    }

    protected fun assertUnorderedPathsAreEqual(actual: Collection<String>, expected: Collection<String>) {
        assertEquals((CollectionFactory.createFilePathSet(expected)), (CollectionFactory.createFilePathSet(actual)))
    }

    protected fun <T> assertUnorderedElementsAreEqual(actual: Array<T>, vararg expected: T) {
        assertUnorderedElementsAreEqual(actual.toList(), *expected)
    }

    protected fun <T> assertUnorderedElementsAreEqual(actual: Collection<T>, vararg expected: T) {
        assertUnorderedElementsAreEqual(actual, expected.toList())
    }

    protected fun <T> assertContain(actual: Collection<T>, vararg expected: T) {
        val expectedList = expected.toList()
        if (actual.containsAll(expectedList)) return
        val absent: MutableSet<T> = HashSet(expectedList)
        absent.removeAll(actual.toSet())
        fail(
            """
  expected: $expectedList
  actual: $actual
  this elements not present: $absent
  """.trimIndent()
        )
    }

    protected fun <T> assertDoNotContain(actual: List<T>, vararg expected: T) {
        val actualCopy: MutableList<T> = ArrayList(actual)
        actualCopy.removeAll(expected.toSet())
        assertEquals(actual.toString(), actualCopy.size, actual.size)
    }

    protected fun assertUnorderedLinesWithFile(filePath: String?, expectedText: String?) {
        try {
            assertSameLinesWithFile(filePath!!, expectedText!!)
        } catch (e: AssertionError) {
            if (e !is FileComparisonData) throw e
            val expected: String = e.expectedStringPresentation
            val actual: String = e.actualStringPresentation
            assertUnorderedElementsAreEqual(expected.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray(),
                *actual.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            )
        }
    }

    private val testMavenHome: String?
        get() = System.getProperty("idea.maven.test.home")

    companion object {

        @Language("XML")
        fun createPomXml(
            @Language(
                value = "XML",
                prefix = "<project>",
                suffix = "</project>"
            ) xml: String?
        ): String {
            return """
             <?xml version="1.0"?>
             <project xmlns="http://maven.apache.org/POM/4.0.0"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
               <modelVersion>4.0.0</modelVersion>
             
             """.trimIndent() + xml + "</project>"
        }
    }
}

package ru.rzn.gmyasoedov.gmaven.project.filter

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.RegexpFilter
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class MavenConsoleFileFilterProviderTest : BasePlatformTestCase() {

    private lateinit var tempDir: Path
    private lateinit var filters: Array<Filter>

    override fun setUp() {
        super.setUp()
        // FileUtil temp directory is in the VFS allowed roots for tests
        tempDir = FileUtil.createTempDirectory("gmavenConsoleFilter", null, true).toPath()
        filters = MavenConsoleFileFilterProvider().getDefaultFilters(project)
    }

    override fun tearDown() {
        try {
            tempDir.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testDefaultFilters() {
        assertEquals(5, filters.size)
        filters.forEach { assertInstanceOf(it, RegexpFilter::class.java) }
    }

    fun testJavaCompilerError() {
        val file = createFile("src/main/java/Foo.java")
        val line = "[ERROR] ${file.path}:[10,5] cannot find symbol"

        assertHyperlink(line, file, 10, 5)
    }

    fun testJavaCompilerWarning() {
        val file = createFile("src/main/java/Bar.java")
        val line = "[WARNING] ${file.path}:[3,15] [deprecation] foo() in Bar has been deprecated"

        assertHyperlink(line, file, 3, 15)
    }


    fun testKotlinCompilerError() {
        val file = createFile("src/main/kotlin/Foo.kt")
        val line = "[ERROR] ${file.path}: (12, 7) Unresolved reference: bar"

        assertHyperlink(line, file, 12, 7)
    }

    fun testKotlinCompilerWarning() {
        val file = createFile("src/main/kotlin/Foo.kt")
        val line = "[WARNING] ${file.path}: (2, 20) Parameter 'x' is never used"

        assertHyperlink(line, file, 2, 20)
    }

    fun testPathAtLineStart() {
        val file = createFile("src/main/java/Foo.java")
        val line = "${file.path}:[4,2] error"

        assertHyperlink(line, file, 4, 2)
    }

    fun testMalformedPomError() {
        val file = createFile("spring-kotlin/pom.xml")
        val line = "[ERROR]     Malformed POM ${file.path}: Unrecognised tag: 'dependency1' " +
                "(position: START_TAG seen ...<dependencies>\\n\\t\\t<dependency1>... @22:16)  " +
                "@ ${file.path}, line 22, column 16 -> [Help 2]"

        assertHyperlink(line, file, 22, 16, line.lastIndexOf(file.path))
    }

    fun testPomProblemWithModelId() {
        val file = createFile("app/pom.xml")
        val line = "[ERROR] 'dependencies.dependency.version' for org.foo:bar:jar is missing. " +
                "@ com.example:app:1.0-SNAPSHOT, ${file.path}, line 31, column 21"

        assertHyperlink(line, file, 31, 21)
    }

    fun testPomProblemWarning() {
        val file = createFile("app/pom.xml")
        val line = "[WARNING] 'dependencies.dependency.(groupId:artifactId:type:classifier)' must be unique: " +
                "junit:junit:jar -> duplicate declaration of version 4.13 " +
                "@ com.example:app:[unknown-version], ${file.path}, line 49, column 21"

        assertHyperlink(line, file, 49, 21)
    }

    fun testPomProblemWithoutFileIsIgnored() {
        assertNull(applyFilters("[WARNING] 'build.plugins.plugin.version' for foo:bar is missing. @ line 40, column 21"))
    }

    fun testProjectProblemHeader() {
        val file = createFile("spring-kotlin/pom.xml")
        val line = "[ERROR]   The project com.example:spring-kotlin:0.0.1-SNAPSHOT (${file.path}) has 1 error"

        assertFileHyperlink(line, file)
    }

    fun testProjectProblemHeaderWithSeveralErrors() {
        val file = createFile("app/pom.xml")
        val line = "[ERROR]   The project com.example:app:[unknown-version] (${file.path}) has 2 errors"

        assertFileHyperlink(line, file)
    }

    fun testProjectProblemHeaderPathWithSpaces() {
        val file = createFile("my project/pom.xml")
        val line = "[ERROR]   The project com.example:app:1.0 (${file.path}) has 1 error"

        assertFileHyperlink(line, file)
    }

    fun testPathInParenthesesWithoutErrorsText() {
        val file = createFile("app/pom.xml")
        val line = "[INFO] Building app 1.0 (${file.path})"

        assertFileHyperlink(line, file)
    }

    fun testLinesWithoutPathAreIgnored() {
        assertNull(applyFilters("[ERROR] The build could not read 1 project -> [Help 1]"))
        assertNull(applyFilters("[ERROR] Unrecognised tag: 'dependency1' (position: START_TAG seen ... @22:16)"))
    }

    fun testMalformedPomParserPosition() {
        val file = createFile("spring-kotlin/pom.xml")
        val line = "[ERROR] Malformed POM ${file.path}: Unrecognised tag: 'dependency1' " +
                "(position: START_TAG seen ...<dependencies>\\n\\t\\t<dependency1>... @22:16) "

        assertHyperlink(line, file, 22, 16)
    }

    fun testPathWithPositionAtEndOfLine() {
        val file = createFile("module/pom.xml")
        val line = "[ERROR] Non-parseable POM ${file.path}: end tag name </project> must match start tag @105:3"

        assertHyperlink(line, file, 105, 3)
    }

    fun testPathWithoutPositionIsIgnored() {
        val file = createFile("spring-kotlin/pom.xml")
        assertNull(applyFilters("[ERROR] Malformed POM ${file.path}: Unrecognised tag: 'dependency1'"))
        assertNull(applyFilters("[ERROR] Malformed POM ${file.path}: wrong position @22"))
        assertNull(applyFilters("[ERROR] Malformed POM ${file.path}: wrong position @22:"))
    }

    fun testHighlightOffsetsInsideWholeConsoleText() {
        val file = createFile("src/main/java/Foo.java")
        val line = "[ERROR] ${file.path}:[10,5] cannot find symbol"
        val previousText = "[INFO] BUILD FAILURE\n"
        val entireLength = previousText.length + line.length

        val result = applyFilters(line, entireLength)

        assertNotNull(result)
        val item = result!!.resultItems.single()
        val expectedStart = previousText.length + line.indexOf(file.path)
        assertEquals(expectedStart, item.highlightStartOffset)
        assertEquals(expectedStart + file.path.length, item.highlightEndOffset)
    }

    fun testNonExistentFileHasNoHyperlink() {
        val path = tempDir.resolve("NotExists.java").toString().replace('\\', '/')
        val line = "[ERROR] $path:[10,5] cannot find symbol"

        val result = applyFilters(line, line.length)

        assertNotNull(result)
        assertNull(result!!.resultItems.single().hyperlinkInfo)
    }

    fun testLinesWithoutPositionAreIgnored() {
        val file = createFile("src/main/java/Foo.java")

        assertNull(applyFilters("[INFO] BUILD SUCCESS"))
        assertNull(applyFilters("[INFO] Compiling 1 source file to ${tempDir}/target/classes"))
        assertNull(applyFilters("[ERROR] ${file.path} cannot find symbol"))
        assertNull(applyFilters("[ERROR] ${file.path}:10:5 cannot find symbol"))
        assertNull(applyFilters("[ERROR] ${file.path}:[10] cannot find symbol"))
        assertNull(applyFilters("[ERROR] ${file.path}: (10) cannot find symbol"))
        assertNull(applyFilters("[ERROR] Failed to execute goal on project app"))
    }

    private fun assertHyperlink(
        line: String,
        file: VirtualFile,
        expectedLine: Int,
        expectedColumn: Int,
        start: Int = line.indexOf(file.path)
    ) {
        val result = applyFilters(line, line.length)
        assertNotNull("File path is not detected in: $line", result)

        val item = result!!.resultItems.single()
        assertEquals(start, item.highlightStartOffset)
        assertEquals(start + file.path.length, item.highlightEndOffset)

        val hyperlinkInfo = item.hyperlinkInfo
        assertInstanceOf(hyperlinkInfo, OpenFileHyperlinkInfo::class.java)
        assertEquals(file, (hyperlinkInfo as OpenFileHyperlinkInfo).virtualFile)

        val matcher = filters.asSequence()
            .map { (it as RegexpFilter).pattern.matcher(line) }
            .first { it.find() }
        assertEquals(file.path, matcher.group("file"))
        assertEquals(expectedLine.toString(), matcher.group("line"))
        assertEquals(expectedColumn.toString(), matcher.group("column"))
    }

    private fun assertFileHyperlink(line: String, file: VirtualFile) {
        val result = applyFilters(line, line.length)
        assertNotNull("File path is not detected in: $line", result)

        val item = result!!.resultItems.single()
        val start = line.indexOf(file.path)
        assertEquals(start, item.highlightStartOffset)
        assertEquals(start + file.path.length, item.highlightEndOffset)

        val hyperlinkInfo = item.hyperlinkInfo
        assertInstanceOf(hyperlinkInfo, OpenFileHyperlinkInfo::class.java)
        assertEquals(file, (hyperlinkInfo as OpenFileHyperlinkInfo).virtualFile)
    }

    private fun applyFilters(line: String, entireLength: Int = line.length): Filter.Result? {
        return filters.firstNotNullOfOrNull { it.applyFilter(line, entireLength) }
    }

    private fun createFile(relativePath: String): VirtualFile {
        val path = tempDir.resolve(relativePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, "class Foo {}\n")
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        assertNotNull("File not found in VFS: $path", file)
        return file!!
    }
}

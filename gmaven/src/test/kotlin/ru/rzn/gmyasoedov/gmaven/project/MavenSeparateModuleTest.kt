package ru.rzn.gmyasoedov.gmaven.project

import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.pom.java.LanguageLevel
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.externalSystem.findAll
import ru.rzn.gmyasoedov.gmaven.MavenImportingTestCase
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.SourceSetData

class MavenSeparateModuleTest : MavenImportingTestCase() {

    fun testSeparateModules() {
        import(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties> 
                <maven.compiler.release>8</maven.compiler.release> 
                <maven.compiler.testRelease>11</maven.compiler.testRelease>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>

            <dependencies>
                <dependency>
                  <groupId>joda-time</groupId>
                  <artifactId>joda-time</artifactId>
                  <version>2.12.7</version>
                </dependency>
                <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <version>4.12</version>
                  <scope>test</scope>
                </dependency>
            </dependencies>
        """
        )

        assertModules("project", "project.main", "project.test")
        val projectModuleNode = getModuleNode("project")
        val subModules = projectModuleNode!!.findAll(SourceSetData.KEY)
        val mainModule = subModules.first { it.data.internalName == "project.main" }
        val testModule = subModules.first { it.data.internalName == "project.test" }

        val mainLibraries = mainModule.node.findAll(ProjectKeys.LIBRARY_DEPENDENCY)
        val mainModules = mainModule.node.findAll(ProjectKeys.MODULE_DEPENDENCY)
        TestCase.assertEquals(
            setOf("joda-time:joda-time:2.12.7"),
            mainLibraries.map { it.data.externalName }.toSet()
        )
        TestCase.assertTrue(mainModules.isEmpty())

        val testLibraries = testModule.node.findAll(ProjectKeys.LIBRARY_DEPENDENCY)
        val testModules = testModule.node.findAll(ProjectKeys.MODULE_DEPENDENCY)
        TestCase.assertEquals(
            setOf("joda-time:joda-time:2.12.7", "junit:junit:4.12", "org.hamcrest:hamcrest-core:1.3"),
            testLibraries.map { it.data.externalName }.toSet()
        )
        TestCase.assertEquals(setOf("project.main"), testModules.map { it.data.internalName }.toSet())
    }

    fun testNoSeparateModulesTestEqualsRelease() {
        val languageLevel = LanguageLevel.HIGHEST.getPreviewLevel() ?: return
        val languageString = languageLevel.toJavaVersion().toFeatureString()
        import(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties> 
                <maven.compiler.release>${languageString}</maven.compiler.release> 
                <maven.compiler.testRelease>${languageString}</maven.compiler.testRelease>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>

        """
        )

        assertModules("project")
    }
}
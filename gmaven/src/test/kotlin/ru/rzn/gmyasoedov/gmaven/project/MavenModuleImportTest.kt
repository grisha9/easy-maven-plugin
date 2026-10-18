package ru.rzn.gmyasoedov.gmaven.project

import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import ru.rzn.gmyasoedov.gmaven.MavenImportingTestCase
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.SourceSetData

class MavenModuleImportTest : MavenImportingTestCase() {

    fun testSimpleModules() {
        createProjectSubFile(
            "m1/pom.xml", createPomXml(
                """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>m1</artifactId>                
        """.trimIndent()
            )
        )
        createProjectSubFile(
            "m2/pom.xml", createPomXml(
                """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>m2</artifactId>                
        """.trimIndent()
            )
        )
        val projectFile = createProjectPom(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <packaging>pom</packaging>
            <properties>
                <maven.compiler.source>17</maven.compiler.source>
                <maven.compiler.target>17</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
            <modules>
                <module>m1</module> 
                <module>m2</module>
            </modules> 
        """
        )
        import(projectFile)

        assertModules("project", "project.m1", "project.m2")
    }

    fun testDirectPathModules() {
        createProjectSubFile(
            "m1/pom.xml", createPomXml(
                """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>m1</artifactId>                
        """.trimIndent()
            )
        )
        createProjectSubFile(
            "m2/custom.xml", createPomXml(
                """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>m2</artifactId>                
        """.trimIndent()
            )
        )
        val projectFile = createProjectPom(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <packaging>pom</packaging>
            <properties>
                <maven.compiler.source>17</maven.compiler.source>
                <maven.compiler.target>17</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
            <modules>
                <module>m1/pom.xml</module> 
                <module>m2/custom.xml</module>
            </modules> 
        """
        )
        import(projectFile)

        assertModules("project", "project.m1", "project.m2")
    }

    fun testModuleDependencyScopeCompile() {
        createProjectSubFile(
            "m1/pom.xml", createPomXml(
                """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>m1</artifactId>
                            
        <dependencies>
            <dependency>
                <groupId>org.example</groupId>
                <artifactId>m2</artifactId>
                <version>1.0-SNAPSHOT</version>
            </dependency>
        </dependencies>
        """.trimIndent()
            )
        )
        createProjectSubFile(
            "m2/pom.xml", createPomXml(
                """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>m2</artifactId>                
        """.trimIndent()
            )
        )
        val projectFile = createProjectPom(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <packaging>pom</packaging>
            <properties>
                <maven.compiler.source>17</maven.compiler.source>
                <maven.compiler.target>17</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
            <modules>
                <module>m1</module> 
                <module>m2</module>
            </modules> 
        """
        )
        import(projectFile)

        assertModules("project", "project.m1", "project.m2")
        val moduleDependencyData = getModulesNode()
            .filter { it.data.moduleName == "m1" }
            .flatMap { it.children }
            .firstNotNullOf { it.data as? ModuleDependencyData }

        assertEquals("project.m2", moduleDependencyData.internalName)
        assertEquals(DependencyScope.COMPILE, moduleDependencyData.scope)
    }

    fun testModuleDependencyScopeTest() {
        createProjectSubFile(
            "m1/pom.xml", createPomXml(
                """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>m1</artifactId>
                            
        <dependencies>
            <dependency>
                <groupId>org.example</groupId>
                <artifactId>m2</artifactId>
                <version>1.0-SNAPSHOT</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
        """.trimIndent()
            )
        )
        createProjectSubFile(
            "m2/pom.xml", createPomXml(
                """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>m2</artifactId>                
        """.trimIndent()
            )
        )
        val projectFile = createProjectPom(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <packaging>pom</packaging>
            <properties>
                <maven.compiler.source>17</maven.compiler.source>
                <maven.compiler.target>17</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
            <modules>
                <module>m1</module> 
                <module>m2</module>
            </modules> 
        """
        )
        import(projectFile)

        assertModules("project", "project.m1", "project.m2")
        val moduleDependencyData = getModulesNode()
            .filter { it.data.moduleName == "m1" }
            .flatMap { it.children }
            .firstNotNullOf { it.data as? ModuleDependencyData }

        assertEquals("project.m2", moduleDependencyData.internalName)
        assertEquals(DependencyScope.TEST, moduleDependencyData.scope)
    }

    fun testModuleTestJarDependency() {
        importTestJarProject(
            """
            <dependency>
                <groupId>org.example</groupId>
                <artifactId>m2</artifactId>
                <version>1.0-SNAPSHOT</version>
                <type>test-jar</type>
                <scope>test</scope>
            </dependency>
            """, ""
        )

        val moduleDependencies = getM1ModuleDependencies()
        assertEquals(1, moduleDependencies.size)
        assertEquals("project.m2.test", moduleDependencies[0].internalName)
        assertEquals(DependencyScope.TEST, moduleDependencies[0].scope)
        assertTrue(moduleDependencies[0].isProductionOnTestDependency)
    }

    fun testModuleAndTestJarDependencySameScope() {
        importTestJarProject(
            """
            <dependency>
                <groupId>org.example</groupId>
                <artifactId>m2</artifactId>
                <version>1.0-SNAPSHOT</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.example</groupId>
                <artifactId>m2</artifactId>
                <version>1.0-SNAPSHOT</version>
                <type>test-jar</type>
                <scope>test</scope>
            </dependency>
            """, ""
        )
        reimport()

        val moduleDependencies = getM1ModuleDependencies()
        assertEquals(2, moduleDependencies.size)
        assertFalse(moduleDependencies[0].isProductionOnTestDependency)
        val m1 = getModule("project.m1")
        val moduleEntries = ModuleRootManager.getInstance(m1).orderEntries.filterIsInstance<ModuleOrderEntry>()
        assertEquals(2, moduleEntries.size)
    }

    fun testSplitModulesTestJarDependency() {
        val splitProperties = """
            <properties>
                <maven.compiler.release>8</maven.compiler.release>
                <maven.compiler.testRelease>11</maven.compiler.testRelease>
            </properties>
            """
        importTestJarProject(
            """
            <dependency>
                <groupId>org.example</groupId>
                <artifactId>m2</artifactId>
                <version>1.0-SNAPSHOT</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.example</groupId>
                <artifactId>m2</artifactId>
                <version>1.0-SNAPSHOT</version>
                <type>test-jar</type>
                <scope>test</scope>
            </dependency>
            """, splitProperties
        )

        val m1Test = getModulesNode()
            .filter { it.data.moduleName == "m1" }
            .flatMap { ExternalSystemApiUtil.findAll(it, SourceSetData.KEY) }
            .first { it.data.internalName == "project.m1.test" }
        val testDependencies = ExternalSystemApiUtil.findAll(m1Test, ProjectKeys.MODULE_DEPENDENCY).map { it.data }
        val testJarDependency = testDependencies.first { it.isProductionOnTestDependency }
        assertEquals("project.m2.test", testJarDependency.internalName)
        assertEquals(DependencyScope.TEST, testJarDependency.scope)
        assertTrue(testDependencies.any { it.internalName == "project.m2.main" && !it.isProductionOnTestDependency })
    }

    private fun importTestJarProject(m1Dependencies: String, modulesProperties: String) {
        createProjectSubFile(
            "m1/pom.xml", createPomXml(
                """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>m1</artifactId>
            $modulesProperties
            <dependencies>
            $m1Dependencies
            </dependencies>
        """.trimIndent()
            )
        )
        createProjectSubFile(
            "m2/pom.xml", createPomXml(
                """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>m2</artifactId>
            $modulesProperties
        """.trimIndent()
            )
        )
        val projectFile = createProjectPom(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <packaging>pom</packaging>
            <properties>
                <maven.compiler.source>17</maven.compiler.source>
                <maven.compiler.target>17</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
            <modules>
                <module>m1</module> 
                <module>m2</module>
            </modules> 
        """
        )
        import(projectFile)
    }

    private fun getM1ModuleDependencies(): List<ModuleDependencyData> = getModulesNode()
        .filter { it.data.moduleName == "m1" }
        .flatMap { it.children }
        .mapNotNull { it.data as? ModuleDependencyData }
}

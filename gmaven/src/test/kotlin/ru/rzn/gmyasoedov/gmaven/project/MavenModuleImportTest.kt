package ru.rzn.gmyasoedov.gmaven.project

import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.roots.DependencyScope
import ru.rzn.gmyasoedov.gmaven.MavenImportingTestCase

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
}
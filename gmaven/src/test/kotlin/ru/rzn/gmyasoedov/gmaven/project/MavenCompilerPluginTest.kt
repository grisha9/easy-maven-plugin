package ru.rzn.gmyasoedov.gmaven.project

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.execution.configurations.JavaParameters
import com.intellij.pom.java.LanguageLevel
import ru.rzn.gmyasoedov.gmaven.MavenImportingTestCase

class MavenCompilerPluginTest : MavenImportingTestCase() {

    fun testCompilerArgsAsString() {
        val languageLevel = LanguageLevel.HIGHEST.getPreviewLevel() ?: return
        val languageString = languageLevel.toJavaVersion().toFeatureString()
        import(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties> 
                <maven.compiler.release>${languageString}</maven.compiler.release>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
        
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.12.0</version>
                        <configuration>
                            <compilerArgs>--enable-preview</compilerArgs>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        """
        )

        assertModules("project")
        val compilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
        assertEquals("Javac", compilerConfiguration.defaultCompiler.id)
        assertUnorderedElementsAreEqual(
            compilerConfiguration.getAdditionalOptions(getModule("project")),
            "--enable-preview"
        )
        assertTrue(getLanguageLevel().isPreview)
    }

    fun testCompilerArgs() {
        val languageLevel = LanguageLevel.HIGHEST.getPreviewLevel() ?: return
        val languageString = languageLevel.toJavaVersion().toFeatureString()
        import(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties> 
                <maven.compiler.release>${languageString}</maven.compiler.release>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
        
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.12.0</version>
                        <configuration>
                            <compilerArgs>
                                <arg>-Xlint:unchecked</arg>
                                <arg1>--enable-preview</arg1>
                            </compilerArgs>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        """
        )

        assertModules("project")
        val compilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
        assertEquals("Javac", compilerConfiguration.defaultCompiler.id)
        assertUnorderedElementsAreEqual(
            compilerConfiguration.getAdditionalOptions(getModule("project")),
            "--enable-preview", "-Xlint:unchecked"
        )
        assertTrue(getLanguageLevel().isPreview)
    }

    fun testCompilerPluginConfigurationCompilerArguments() {
        import(
            "<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>org.apache.maven.plugins</groupId>" +
                    "      <artifactId>maven-compiler-plugin</artifactId>" +
                    "      <configuration>" +
                    "        <compilerArguments>" +
                    "          <Averbose>true</Averbose>" +
                    "          <parameters></parameters>" +
                    "          <bootclasspath>rt.jar_path_here</bootclasspath>" +
                    "        </compilerArguments>" +
                    "      </configuration>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>"
        )

        assertModules("project")
        val compilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
        assertEquals("Javac", compilerConfiguration.defaultCompiler.id)
        assertUnorderedElementsAreEqual(
            compilerConfiguration.getAdditionalOptions(getModule("project")),
            "-Averbose=true", "-parameters", "-bootclasspath", "rt.jar_path_here"
        )
    }

    fun testCompilerPluginConfigurationCompilerArgumentsParameters() {
        import(
            "<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>org.apache.maven.plugins</groupId>" +
                    "      <artifactId>maven-compiler-plugin</artifactId>" +
                    "      <configuration>" +
                    "        <parameters>true</parameters>" +
                    "      </configuration>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>"
        )

        assertModules("project")
        val ideCompilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
        assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
        assertUnorderedElementsAreEqual(
            ideCompilerConfiguration.getAdditionalOptions(getModule("project")),
            "-parameters"
        )
    }

    fun testCompilerPluginConfigurationCompilerArgumentsParametersFalse() {
        import(
            "<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>org.apache.maven.plugins</groupId>" +
                    "      <artifactId>maven-compiler-plugin</artifactId>" +
                    "      <configuration>" +
                    "        <parameters>false</parameters>" +
                    "      </configuration>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>"
        )

        assertModules("project")
        val ideCompilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
        assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
        assertEmpty(ideCompilerConfiguration.getAdditionalOptions(getModule("project")))
    }

    fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyOverride() {
        import(
            "<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<properties>" +
                    "  <maven.compiler.parameters>true</maven.compiler.parameters>" +
                    "</properties>" +
                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>org.apache.maven.plugins</groupId>" +
                    "      <artifactId>maven-compiler-plugin</artifactId>" +
                    "      <configuration>" +
                    "        <parameters>false</parameters>" +
                    "      </configuration>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>"
        )

        assertModules("project")
        val ideCompilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
        assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
        assertEmpty(ideCompilerConfiguration.getAdditionalOptions(getModule("project")))
    }

    fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyOverride1() {
        import(
            "<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<properties>" +
                    "  <maven.compiler.parameters>false</maven.compiler.parameters>" +
                    "</properties>" +
                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>org.apache.maven.plugins</groupId>" +
                    "      <artifactId>maven-compiler-plugin</artifactId>" +
                    "      <configuration>" +
                    "        <parameters>true</parameters>" +
                    "      </configuration>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>"
        )

        assertModules("project")
        val ideCompilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
        assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
        assertUnorderedElementsAreEqual(
            ideCompilerConfiguration.getAdditionalOptions(getModule("project")),
            "-parameters"
        )
    }

    fun testCompilerPluginConfigurationCompilerArgumentsParametersProperty() {
        import(
            "<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<properties>" +
                    "  <maven.compiler.parameters>true</maven.compiler.parameters>" +
                    "</properties>" +
                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>org.apache.maven.plugins</groupId>" +
                    "      <artifactId>maven-compiler-plugin</artifactId>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>"
        )

        assertModules("project")
        val ideCompilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
        assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
        assertUnorderedElementsAreEqual(
            ideCompilerConfiguration.getAdditionalOptions(getModule("project")),
            "-parameters"
        )
    }

    fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyFalse() {
        import(
            "<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<properties>" +
                    "  <maven.compiler.parameters>false</maven.compiler.parameters>" +
                    "</properties>" +
                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>org.apache.maven.plugins</groupId>" +
                    "      <artifactId>maven-compiler-plugin</artifactId>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>"
        )

        assertModules("project")
        val ideCompilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
        assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
        assertEmpty(ideCompilerConfiguration.getAdditionalOptions(getModule("project")))
    }

    fun testCompilerReleasePriority() {
        import(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties> 
                <maven.compiler.release>1.8</maven.compiler.release>
                <maven.compiler.source>1.6</maven.compiler.source>
                <maven.compiler.target>1.6</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
        
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.12.0</version>
                        <configuration>
                            <release>11</release>
                            <source>1.5</source>
                            <target>1.5</target>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        """
        )
        assertModules("project")
        assertEquals(LanguageLevel.JDK_11, getLanguageLevel())
    }

    fun testCompilerReleasePriorityProperty() {
        import(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties> 
                <maven.compiler.release>1.8</maven.compiler.release>
                <maven.compiler.source>1.6</maven.compiler.source>
                <maven.compiler.target>1.6</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
        
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.12.0</version>
                        <configuration> 
                            <source>1.5</source>
                            <target>1.5</target>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        """
        )
        assertModules("project")
        assertEquals(LanguageLevel.JDK_1_8, getLanguageLevel())
    }

    fun testCompilerSource() {
        import(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties>  
                <maven.compiler.source>11</maven.compiler.source>
                <maven.compiler.target>11</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
        
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.12.0</version>
                        <configuration> 
                            <source>12</source>
                            <target>12</target>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        """
        )
        assertModules("project")
        assertEquals(LanguageLevel.JDK_12, getLanguageLevel())
    }

    fun testEnablePreviewOption() {
        import(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties>  
                <maven.compiler.release>17</maven.compiler.release>
                <maven.compiler.enablePreview>false</maven.compiler.enablePreview>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
        
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId> 
                        <configuration> 
                            <enablePreview>true</enablePreview> 
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        """
        )
        assertModules("project")
        val compilerData = getCompilerData()
        assertTrue(compilerData.isNotEmpty())
        assertEquals(listOf(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY), compilerData[0].arguments)
    }

    fun testEnablePreviewOptionFalse() {
        import(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties>  
                <maven.compiler.release>17</maven.compiler.release>
                <maven.compiler.enablePreview>true</maven.compiler.enablePreview>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
        
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId> 
                        <configuration> 
                            <enablePreview>false</enablePreview> 
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        """
        )
        assertModules("project")
        val compilerData = getCompilerData()
        assertTrue(compilerData.isNotEmpty())
        assertTrue(compilerData[0].arguments.isEmpty())
    }

    fun testEnablePreviewOptionTrueProperty() {
        import(
            """
            <groupId>org.example</groupId>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            <properties>  
                <maven.compiler.release>17</maven.compiler.release>
                <maven.compiler.enablePreview>true</maven.compiler.enablePreview>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
        """
        )
        assertModules("project")
        val compilerData = getCompilerData()
        assertTrue(compilerData.isNotEmpty())
        assertEquals(listOf(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY), compilerData[0].arguments)
    }

    fun testMavenCompilerAnnotationProcessorVersionFromdependencyManagement() {
        val lombokVersion = "1.18.18"
        val pomXml = """
        <groupId>org.example</groupId>
        <artifactId>project</artifactId>
        <version>1.0-SNAPSHOT</version> 
        
        <dependencyManagement>
            <dependencies>
                <dependency>
                    <groupId>org.projectlombok</groupId>
                    <artifactId>lombok</artifactId>
                    <version>${lombokVersion}</version>
                </dependency>
            </dependencies>
        </dependencyManagement>
    
        <build>
            <plugins>
                <plugin>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.12.1</version>
                    <configuration>
                        <release>17</release>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId> 
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>
            </plugins>
            
        </build>
        """
        import(pomXml)
        val compilerData = getCompilerData()
        assertTrue(compilerData.isNotEmpty())
        assertTrue(compilerData[0].path.isNotEmpty())
        assertTrue(compilerData[0].path.joinToString().contains(lombokVersion))
    }
}
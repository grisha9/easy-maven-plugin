package ru.rzn.gmyasoedov.gmaven.utils;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WslPath;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rzn.gmyasoedov.gmaven.GMavenConstants;
import ru.rzn.gmyasoedov.gmaven.extensionpoints.plugin.CompilerData;
import ru.rzn.gmyasoedov.gmaven.settings.MavenExecutionSettings;
import ru.rzn.gmyasoedov.gmaven.settings.MavenProjectSettings;
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenId;
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.JarUtil.getJarAttribute;
import static com.intellij.openapi.util.io.JarUtil.loadProperties;
import static com.intellij.openapi.util.text.StringUtil.*;
import static ru.rzn.gmyasoedov.gmaven.GMavenConstants.M2;

public class MavenUtils {

    private static final String POLYGLOT_PREFIX = ".polyglot.";
    public static final String INTELLIJ_GROOVY_PLUGIN_ID = "org.intellij.groovy";
    public static final String INTELLIJ_MAVEN_PLUGIN_ID = "org.jetbrains.idea.maven";

    private MavenUtils() {
    }

    @NotNull
    public static VirtualFile getVFile(File file) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (virtualFile == null) throw new RuntimeException("Virtual file not found " + file);
        return virtualFile;
    }

    @NotNull
    public static VirtualFile getVFile(String filePath) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (virtualFile == null) throw new RuntimeException("Virtual file not found " + filePath);
        return virtualFile;
    }

    public static void setupFileTemplate(Project project,
                                         VirtualFile file,
                                         Properties properties) throws IOException {
        FileTemplateManager manager = FileTemplateManager.getInstance(project);
        FileTemplate fileTemplate = manager.getJ2eeTemplate(MavenFileTemplateGroupFactory.MAVEN_PROJECT_XML_TEMPLATE);
        Properties allProperties = manager.getDefaultProperties();
        allProperties.putAll(properties);

        String text = fileTemplate.getText(allProperties);
        Pattern pattern = Pattern.compile("\\$\\{(.*)\\}");
        Matcher matcher = pattern.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, "\\$" + toUpperCase(matcher.group(1)) + "\\$");
        }
        matcher.appendTail(builder);
        text = builder.toString();

        TemplateImpl template = (TemplateImpl) TemplateManager.getInstance(project).createTemplate("", "", text);
        for (int i = 0; i < template.getSegmentsCount(); i++) {
            if (i == template.getEndSegmentNumber()) continue;
            String name = template.getSegmentName(i);
            String value = "\"" + properties.getProperty(name, "") + "\"";
            template.addVariable(name, value, value, true);
        }

        VfsUtil.saveText(file, template.getTemplateText());
    }

    public static boolean isSimplePomFile(String fileName) {
        return fileName.equals(GMavenConstants.POM_XML) ||
                fileName.endsWith(".pom") || fileName.startsWith("pom.") ||
                (fileName.startsWith(GMavenConstants.SUPER_POM_PREFIX) && fileName.endsWith(".xml"));
    }

    public static boolean isSimplePomFile(@Nullable VirtualFile file) {
        if (file == null) return false;
        return isSimplePomFile(file.getName());
    }

    public static boolean isPotentialPomFile(String nameOrPath) {
        String[] split = getConfigFileExtensions();
        return ArrayUtil.contains(FileUtilRt.getExtension(nameOrPath), split);
    }

    @NotNull
    public static String[] getConfigFileExtensions() {
        String[] result;
        try {
            String extensions = Registry.stringValue("gmaven.support.extensions");
            result = extensions.split(",");
        } catch (Exception e) {
            result = new String[]{"pom"};
        }
        return result;
    }

    public static boolean isPomFile(@Nullable VirtualFile file) {
        if (file == null) return false;

        String name = file.getName();
        if (isSimplePomFile(name)) return true;
        return isPotentialPomFile(name);
    }

    public static boolean isProjectFile(@Nullable VirtualFile file) {
        if (file == null) return false;

        if (file.isDirectory()) {
            return Stream.of(file.getChildren()).anyMatch(MavenUtils::isSimplePomFile);
        } else {
            String name = file.getName();
            if (isSimplePomFile(name)) return true;
            return isPotentialPomFile(name);
        }
    }

    @Nullable
    public static Sdk suggestProjectSdk() {
        Project defaultProject = ProjectManager.getInstance().getDefaultProject();
        ProjectRootManager defaultProjectManager = ProjectRootManager.getInstance(defaultProject);
        Sdk defaultProjectSdk = defaultProjectManager.getProjectSdk();
        if (defaultProjectSdk != null) return null;
        ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
        Sdk[] allJdks = projectJdkTable.getAllJdks();
        if (allJdks.length == 0) {
            projectJdkTable.preconfigure();
        }
        SdkType sdkType = ExternalSystemJdkUtil.getJavaSdkType();
        return projectJdkTable.getSdksOfType(sdkType).stream()
                .filter(it -> it.getHomePath() != null && JdkUtil.checkForJre(it.getHomePath()))
                .max(sdkType.versionComparator())
                .orElse(null);
    }

    @Nullable
    public static Module findIdeModule(@NotNull Project project, @NotNull ModuleData moduleData) {
        return new IdeModelsProviderImpl(project).findIdeModule(moduleData);
    }

    @Nullable
    public static File resolveMavenHome() {
        String m2home = System.getenv("M2_HOME");
        if (!isEmptyOrSpaces(m2home)) {
            final File homeFromEnv = new File(m2home);
            if (isValidMavenHome(homeFromEnv)) {
                return homeFromEnv;
            }
        }

        String mavenHome = System.getenv("MAVEN_HOME");
        if (!isEmptyOrSpaces(mavenHome)) {
            final File mavenHomeFile = new File(mavenHome);
            if (isValidMavenHome(mavenHomeFile)) {
                return mavenHomeFile;
            }
        }

        String userHome = SystemProperties.getUserHome();
        if (!isEmptyOrSpaces(userHome)) {
            File underUserHome = new File(userHome, "m2");
            if (isValidMavenHome(underUserHome)) {
                return underUserHome;
            }

            File sdkManMavenHome = Path.of(userHome, ".sdkman", "candidates", "maven", "current").toFile();
            if (isValidMavenHome(sdkManMavenHome)) {
                return sdkManMavenHome;
            }
        }

        if (SystemInfo.isMac) {
            File home = fromBrew();
            if (home != null) {
                return home;
            }

            if ((home = fromMacSystemJavaTools()) != null) {
                return home;
            }
        } else if (SystemInfo.isLinux) {
            File home = new File("/usr/share/maven");
            if (isValidMavenHome(home)) {
                return home;
            }

            home = new File("/usr/share/maven2");
            if (isValidMavenHome(home)) {
                return home;
            }
        }

        return null;
    }

    public static boolean isValidMavenHome(@Nullable File home) {
        return home != null && isValidMavenHome(home.toPath());
    }

    public static boolean isValidMavenHome(@Nullable Path home) {
        if (home == null) return false;
        return home.resolve("bin").resolve("m2.conf").toFile().exists();
    }

    @Nullable
    private static File fromBrew() {
        final File brewDir = new File("/usr/local/Cellar/maven");
        final String[] list = brewDir.list();
        if (list == null || list.length == 0) {
            return null;
        }

        if (list.length > 1) {
            Arrays.sort(list, (o1, o2) -> compareVersionNumbers(o2, o1));
        }

        final File file = new File(brewDir, list[0] + "/libexec");
        return isValidMavenHome(file) ? file : null;
    }

    @Nullable
    private static File fromMacSystemJavaTools() {
        final File symlinkDir = new File("/usr/share/maven");
        if (isValidMavenHome(symlinkDir)) {
            return symlinkDir;
        }

        // well, try to search
        final File dir = new File("/usr/share/java");
        final String[] list = dir.list();
        if (list == null || list.length == 0) {
            return null;
        }

        String home = null;
        final String prefix = "maven-";
        final int versionIndex = prefix.length();
        for (String path : list) {
            if (path.startsWith(prefix) &&
                    (home == null || compareVersionNumbers(path.substring(versionIndex), home.substring(versionIndex)) > 0)) {
                home = path;
            }
        }

        if (home != null) {
            File file = new File(dir, home);
            if (isValidMavenHome(file)) {
                return file;
            }
        }

        return null;
    }

    public static @NotNull @NlsSafe Path getGeneratedSourcesDirectory(
            @NotNull String buildDirectory, boolean testSources) {
        return Path.of(buildDirectory, (testSources ? "generated-test-sources" : "generated-sources"));
    }

    public static @NotNull @NlsSafe Path getGeneratedAnnotationsDirectory(
            @NotNull String buildDirectory,
            boolean testSources) {
        return getGeneratedSourcesDirectory(buildDirectory, testSources)
                .resolve((testSources ? "test-annotations" : "annotations"));
    }

    @Nullable
    public static String getMavenVersion(@Nullable File mavenHome) {
        if (mavenHome == null) return null;
        File[] libs = new File(mavenHome, "lib").listFiles();


        if (libs != null) {
            for (File mavenLibFile : libs) {
                String lib = mavenLibFile.getName();
                if (lib.equals("maven-core.jar")) {
                    MavenLog.LOG.debug("Choosing version by maven-core.jar");
                    return getMavenLibVersion(mavenLibFile);
                }
                if (lib.startsWith("maven-core-") && lib.endsWith(".jar")) {
                    MavenLog.LOG.debug("Choosing version by maven-core.xxx.jar");
                    String version = lib.substring("maven-core-".length(), lib.length() - ".jar".length());
                    return contains(version, ".x") ? getMavenLibVersion(mavenLibFile) : version;
                }
                if (lib.startsWith("maven-") && lib.endsWith("-uber.jar")) {
                    MavenLog.LOG.debug("Choosing version by maven-xxx-uber.jar");
                    return lib.substring("maven-".length(), lib.length() - "-uber.jar".length());
                }
            }
        }
        MavenLog.LOG.warn("Cannot resolve maven version for " + mavenHome);
        return null;
    }

    private static String getMavenLibVersion(final File file) {
        WSLDistribution distribution = WslPath.getDistributionByWindowsUncPath(file.getPath());
        File fileToRead = Optional.ofNullable(distribution)
                .map(it -> distribution.getWslPath(file.toPath()))
                .map(distribution::resolveSymlink)
                .map(distribution::getWindowsPath)
                .map(File::new)
                .orElse(file);

        Properties props = loadProperties(fileToRead, "META-INF/maven/org.apache.maven/maven-core/pom.properties");
        return props != null
                ? nullize(props.getProperty("version"))
                : nullize(getJarAttribute(file, java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION));
    }

    @NotNull
    public static Path resolveM2() {
        return Path.of(SystemProperties.getUserHome(), M2);
    }

    public static boolean isPerSourceSet(
            @NotNull MavenExecutionSettings settings,
            @NotNull MavenProject project,
            @NotNull CompilerData compilerData) {
        if (!settings.isResolveModulePerSourceSet()) return false;
        if (isPomProject(project)) return false;
        return !Objects.equals(compilerData.getSourceLevel(), compilerData.getTestSourceLevel())
                || !Objects.equals(compilerData.getTargetLevel(), compilerData.getTestTargetLevel());
    }

    public static boolean isPomProject(@NotNull MavenProject project) {
        return "pom".equalsIgnoreCase(project.getPackaging());
    }

    @NotNull
    public static String toGAString(@NotNull String groupId, @NotNull String artifactId) {
        return groupId + ":" + artifactId;
    }

    @NotNull
    public static String toGAString(@NotNull ModuleData moduleData) {
        return toGAString(Objects.requireNonNullElse(moduleData.getGroup(), ""), moduleData.getModuleName());
    }

    @NotNull
    public static String toGAString(@NotNull MavenId mavenId) {
        return mavenId.getGroupId() + ":" + mavenId.getArtifactId();
    }

    public static boolean equalsPaths(String path1, String path2) {
        if (path1 == null || path2 == null) return Objects.equals(path1, path2);
        return Path.of(path1).equals(Path.of(path2));
    }

    @Nullable
    public static String getBuildFilePath(@Nullable String mavenBuildFilePath) {
        if (mavenBuildFilePath == null) return null;
        if (mavenBuildFilePath.contains(POLYGLOT_PREFIX)) {
            Path buildFile = Path.of(mavenBuildFilePath);
            String originalName = buildFile.getFileName().toString().replace(POLYGLOT_PREFIX, "");
            return buildFile.getParent().resolve(originalName).toString();
        } else {
            return mavenBuildFilePath;
        }
    }

    @Nullable
    public static IdeaPluginDescriptor getPlugin(@NotNull String pluginId) {
        return PluginManagerCore.getPlugin(PluginId.getId(pluginId));
    }

    public static boolean pluginEnabled(@NotNull String pluginId) {
        return Optional.ofNullable(PluginManagerCore.getPlugin(PluginId.getId(pluginId)))
                .map(PluginDescriptor::isEnabled).orElse(false);
    }

    public static boolean groovyPluginEnabled() {
        return pluginEnabled(INTELLIJ_GROOVY_PLUGIN_ID);
    }

    public static boolean isProjectFile(PsiFile file) {
        if (!(file instanceof XmlFile)) return false;

        XmlTag rootTag = ((XmlFile) file).getRootTag();
        if (rootTag == null || !"project".equals(rootTag.getName())) return false;

        String xmlns = rootTag.getAttributeValue("xmlns");
        if (xmlns != null && xmlns.startsWith("http://maven.apache.org/POM/")) {
            return true;
        }

        return MavenUtils.isSimplePomFile(file.getName());
    }

    @NotNull
    public static Path getWorkingDirectory(MavenProjectSettings settings) {
        if (settings.getProjectBuildFile() == null) return Path.of(settings.getExternalProjectPath());
        Path buildFilePath = Path.of(settings.getProjectBuildFile());
        if (buildFilePath.toFile().isDirectory()) {
            return buildFilePath;
        } else {
            return buildFilePath.getParent();
        }
    }
}

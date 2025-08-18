package ru.rzn.gmyasoedov.gmaven.wizard;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rzn.gmyasoedov.gmaven.GMavenConstants;
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle;
import ru.rzn.gmyasoedov.gmaven.util.GMavenNotification;
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog;
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils;
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenId;
import ru.rzn.gmyasoedov.maven.plugin.reader.model.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static com.intellij.openapi.vfs.VfsUtilCore.findRelativePath;

public class GMavenModuleBuilderHelper {
    private final MavenId myProjectId;

    private final MavenProject myParentProject;

    private final boolean myInheritGroupId;
    private final boolean myInheritVersion;

    public GMavenModuleBuilderHelper(@NotNull MavenId projectId,
                                     MavenProject parentProject,
                                     boolean inheritGroupId,
                                     boolean inheritVersion) {
        myProjectId = projectId;
        myParentProject = parentProject;
        myInheritGroupId = inheritGroupId;
        myInheritVersion = inheritVersion;
    }

    public static @NotNull VirtualFile createExternalProjectConfigFile(@NotNull Path parent, boolean addSampleCode)
            throws ConfigurationException {
        Path file = parent.resolve(GMavenConstants.POM_XML);
        try {
            Files.deleteIfExists(file);
            createDirectory(parent);
            try {
                Files.createFile(file);
            } catch (FileAlreadyExistsException ignore) {
            }
            if (addSampleCode) {
                createDirectory(parent.resolve("src").resolve("main").resolve("java"));
                createDirectory(parent.resolve("src").resolve("main").resolve("resources"));
                createDirectory(parent.resolve("src").resolve("test").resolve("java"));
                createDirectory(parent.resolve("src").resolve("test").resolve("resources"));
            }

            VirtualFile virtualFile = VfsUtil.findFile(file, true);
            if (virtualFile == null) {
                throw new ConfigurationException("Can not create configuration file " + file);
            }
            if (virtualFile.isDirectory()) {
                throw new ConfigurationException("Configuration file is directory " + file);
            }
            VfsUtil.markDirtyAndRefresh(false, false, false, virtualFile);
            return virtualFile;
        } catch (IOException e) {
            throw new ConfigurationException(e.getMessage(), e, "Error create build file");
        }
    }

    public void setupBuildScript(final Project project, final Sdk sdk, final VirtualFile buildScriptFile) {
        Properties templateProperties = getTemplateProperties(buildScriptFile, sdk);
        PsiFile[] psiFiles = myParentProject != null
                ? new PsiFile[]{getPsiFile(project, MavenUtils.getVFile(myParentProject.getFilePath()))}
                : PsiFile.EMPTY_ARRAY;
        WriteCommandAction
                .writeCommandAction(project, psiFiles)
                .withName(GBundle.message("command.name.create.new.maven.module"))
                .compute(() -> {
                    setupBuildPomFile(project, buildScriptFile, templateProperties);
                    return null;
                });
    }

    private void setupBuildPomFile(Project project, VirtualFile buildScriptFile, Properties properties) {

        try {
            MavenUtils.setupFileTemplate(project, buildScriptFile, properties);
        } catch (IOException e) {
            String title = GBundle.message("notification.title.failed.to.create.maven.project");
            GMavenNotification.INSTANCE.createNotification(title, e.getMessage(), NotificationType.ERROR, List.of());
        }

        updateProjectPom(project, buildScriptFile);

        if (myParentProject != null) {
            VirtualFile parentBuildFile = MavenUtils.getVFile(myParentProject.getFilePath());
            if (!parentBuildFile.isValid()) return;
            PsiFile psiFile = PsiManager.getInstance(project).findFile(parentBuildFile);
            if (!(psiFile instanceof XmlFile)) {
                return;
            }
            XmlTag rootTag = ((XmlFile) psiFile).getRootTag();
            if (rootTag == null) return;
            XmlTag modules = getOrCreateTag(rootTag, "modules", null);
            String relativePath = findRelativePath(parentBuildFile, buildScriptFile.getParent(), File.separatorChar);
            XmlTag module = modules.createChildTag("module", null, relativePath, true);
            modules.addSubTag(module, false);

            XmlTag packaging = getOrCreateTag(rootTag, "packaging", "pom");
            XmlText xmlText = (XmlText) Arrays.stream(packaging.getChildren())
                    .filter(p -> p instanceof XmlText)
                    .findFirst()
                    .orElse(null);
            if (xmlText != null && !xmlText.textMatches("pom")) {
                xmlText.setValue("pom");
            }
            CodeStyleManager.getInstance(project).reformat(getPsiFile(project, parentBuildFile));
            unblockAndSaveDocuments(project, parentBuildFile);
        }
    }

    private XmlTag getOrCreateTag(@NotNull XmlTag rootTag, @NotNull String tagName, @Nullable String value) {
        XmlTag xmlTag = rootTag.findFirstSubTag(tagName);
        if (xmlTag != null) {
            return xmlTag;
        }
        xmlTag = rootTag.createChildTag(tagName, null, value, false);
        rootTag.addSubTag(xmlTag, false);
        xmlTag = rootTag.findFirstSubTag(tagName);
        assert xmlTag != null;
        return xmlTag;
    }

    private void updateProjectPom(final Project project, final VirtualFile pom) {
        if (myParentProject == null) {
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            unblockAndSaveDocuments(project, pom);
            CodeStyleManager.getInstance(project).reformat(getPsiFile(project, pom));
            return;
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        CodeStyleManager.getInstance(project).reformat(getPsiFile(project, pom));

        List<VirtualFile> pomFiles = new ArrayList<>(2);
        pomFiles.add(pom);

        if (!myParentProject.getFilePath().toLowerCase().endsWith(GMavenConstants.POM_XML)) {
            pomFiles.add(MavenUtils.getVFile(myParentProject.getFilePath()));
        }

        unblockAndSaveDocuments(project, pomFiles.toArray(VirtualFile.EMPTY_ARRAY));
    }

    private static void unblockAndSaveDocuments(@NotNull Project project, VirtualFile... files) {
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        for (VirtualFile file : files) {
            Document document = fileDocumentManager.getDocument(file);
            if (document == null) continue;
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
            fileDocumentManager.saveDocument(document);
        }
    }

    private static PsiFile getPsiFile(Project project, VirtualFile pom) {
        return PsiManager.getInstance(project).findFile(pom);
    }

    private Properties getTemplateProperties(@NotNull VirtualFile file, @Nullable Sdk sdk
    ) {
        Properties properties = new Properties();
        properties.setProperty("GROUP_ID", myProjectId.getGroupId());
        properties.setProperty("ARTIFACT_ID", myProjectId.getArtifactId());
        properties.setProperty("VERSION", myProjectId.getVersion());
        properties.setProperty("HAS_GROUP_ID", Boolean.TRUE.toString());
        properties.setProperty("HAS_VERSION", Boolean.TRUE.toString());

        if (myParentProject != null) {
            properties.setProperty("HAS_PARENT", "true");
            properties.setProperty("PARENT_GROUP_ID", myParentProject.getGroupId());
            properties.setProperty("PARENT_ARTIFACT_ID", myParentProject.getArtifactId());
            properties.setProperty("PARENT_VERSION", myParentProject.getVersion());
            if (myInheritGroupId) {
                properties.remove("HAS_GROUP_ID");
            }
            if (myInheritVersion) {
                properties.remove("HAS_VERSION");
            }

            VirtualFile parentBuildFile = MavenUtils.getVFile(myParentProject.getFilePath());
            VirtualFile modulePath = file.getParent();
            VirtualFile parentModulePath = parentBuildFile.getParent();

            if (!Comparing.equal(modulePath.getParent(), parentModulePath) ||
                    !FileUtil.namesEqual(GMavenConstants.POM_XML, parentBuildFile.getName())) {
                String relativePath = findRelativePath(file, parentModulePath, File.separatorChar);
                if (relativePath != null) {
                    properties.setProperty("HAS_RELATIVE_PATH", "true");
                    properties.setProperty("PARENT_RELATIVE_PATH", relativePath);
                }
            }
        } else {
            //set language level only for root pom
            if (sdk != null && sdk.getSdkType() instanceof JavaSdk) {
                JavaSdk javaSdk = (JavaSdk) sdk.getSdkType();
                JavaSdkVersion version = javaSdk.getVersion(sdk);
                String description = version == null ? null : version.getDescription();
                boolean shouldSetLangLevel = version != null && version.isAtLeast(JavaSdkVersion.JDK_1_6);
                properties.setProperty("SHOULD_SET_LANG_LEVEL", String.valueOf(shouldSetLangLevel));
                properties.setProperty("COMPILER_LEVEL_SOURCE", description);
                properties.setProperty("COMPILER_LEVEL_TARGET", description);
            }
        }
        return properties;
    }

    private static void createDirectory(Path srcMainJavaPath) {
        try {
            srcMainJavaPath.toFile().mkdirs();
            LocalFileSystem.getInstance().refreshAndFindFileByPath(srcMainJavaPath.toString());
        } catch (Exception e) {
            MavenLog.LOG.warn(e);
        }
    }

    /* todo show original file for good example run procces
    @VisibleForTesting
    void copyGeneratedFiles(File workingDir, VirtualFile pom, Project project, String artifactId) {

    }*/
}

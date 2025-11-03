package ru.rzn.gmyasoedov.gmaven;

import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemConfigurableAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.project.autoimport.CachingExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.service.ui.DefaultExternalSystemUiAware;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.ExecutionSearchScopes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.execution.ParametersListUtil;
import icons.GMavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle;
import ru.rzn.gmyasoedov.gmaven.project.GMavenAutoImportAware;
import ru.rzn.gmyasoedov.gmaven.project.MavenProjectResolver;
import ru.rzn.gmyasoedov.gmaven.project.task.MavenTaskManager;
import ru.rzn.gmyasoedov.gmaven.settings.*;
import ru.rzn.gmyasoedov.gmaven.util.GMavenNotification;
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils;

import javax.swing.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_INTERNAL_JAVA;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.INFO;
import static ru.rzn.gmyasoedov.gmaven.util.ExecutionSettingsUtil.fillExecutionWorkSpace;

public final class MavenManager
        implements ExternalSystemConfigurableAware,
        ExternalSystemUiAware,
        ExternalSystemAutoImportAware,
        StartupActivity,
        ExternalSystemManager<MavenProjectSettings, MavenSettingsListener,
                MavenSettings, MavenLocalSettings, MavenExecutionSettings> {

    private final ExternalSystemAutoImportAware autoImportAwareDelegate = new CachingExternalSystemAutoImportAware(
            new GMavenAutoImportAware()
    );

    @NotNull
    @Override
    public ProjectSystemId getSystemId() {
        return GMavenConstants.SYSTEM_ID;
    }

    @NotNull
    @Override
    public Function<Project, MavenSettings> getSettingsProvider() {
        return MavenSettings::getInstance;
    }

    @NotNull
    @Override
    public Function<Project, MavenLocalSettings> getLocalSettingsProvider() {
        return project -> project.getService(MavenLocalSettings.class);
    }

    @NotNull
    @Override
    public Function<Pair<Project, String>, MavenExecutionSettings> getExecutionSettingsProvider() {
        return pair -> {
            Project project = pair.first;
            String projectPath = pair.second;
            MavenSettings settings = MavenSettings.getInstance(project);
            MavenProjectSettings projectSettings = settings.getLinkedProjectSettings(projectPath);
            return getExecutionSettings(project, projectPath, settings, projectSettings);
        };
    }

    @Override
    public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Class<? extends ExternalSystemProjectResolver<MavenExecutionSettings>> getProjectResolverClass() {
        return MavenProjectResolver.class;
    }

    @Override
    public Class<? extends ExternalSystemTaskManager<MavenExecutionSettings>> getTaskManagerClass() {
        return MavenTaskManager.class;
    }

    @NotNull
    @Override
    public Configurable getConfigurable(@NotNull Project project) {
        return new GMavenConfigurable(project);
    }

    @NotNull
    @Override
    public FileChooserDescriptor getExternalProjectConfigDescriptor() {
        return FileChooserDescriptorFactory.createSingleFolderDescriptor();
    }

    @NotNull
    @Override
    public Icon getProjectIcon() {
        return GMavenIcons.MavenProject;
    }

    @NotNull
    @Override
    public Icon getTaskIcon() {
        return DefaultExternalSystemUiAware.INSTANCE.getTaskIcon();
    }

    @NotNull
    @Override
    public String getProjectRepresentationName(@NotNull String targetProjectPath, @Nullable String rootProjectPath) {
        return ExternalSystemApiUtil.getProjectRepresentationName(targetProjectPath, rootProjectPath);
    }

    @NotNull
    @Override
    public String getProjectRepresentationName(@NotNull Project project,
                                               @NotNull String targetProjectPath,
                                               @Nullable String rootProjectPath) {
        return ExternalSystemApiUtil.getProjectRepresentationName(targetProjectPath, rootProjectPath);
    }

    @Nullable
    @Override
    public String getAffectedExternalProjectPath(@NotNull String changedFileOrDirPath, @NotNull Project project) {
        return autoImportAwareDelegate.getAffectedExternalProjectPath(changedFileOrDirPath, project);
    }

    @Override
    public List<File> getAffectedExternalProjectFiles(String projectPath, @NotNull Project project) {
        return autoImportAwareDelegate.getAffectedExternalProjectFiles(projectPath, project);
    }

    @Override
    public boolean isApplicable(@Nullable ProjectResolverPolicy resolverPolicy) {
        return autoImportAwareDelegate.isApplicable(resolverPolicy);
    }

    @NotNull
    @Override
    public FileChooserDescriptor getExternalProjectDescriptor() {
        return FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
    }

    @Nullable
    @Override
    public GlobalSearchScope getSearchScope(@NotNull Project project,
                                            @NotNull ExternalSystemTaskExecutionSettings taskExecutionSettings) {
        String projectPath = taskExecutionSettings.getExternalProjectPath();
        if (StringUtil.isEmpty(projectPath)) return null;

        MavenSettings settings = MavenSettings.getInstance(project);
        MavenProjectSettings projectSettings = settings.getLinkedProjectSettings(projectPath);
        if (projectSettings == null) return null;

        if (!projectSettings.getResolveModulePerSourceSet()) {
            // use default implementation which will find target module using projectPathFile
            return null;
        } else {
            List<Module> modules = JBIterable.of(ModuleManager.getInstance(project).getModules())
                    .filter(module -> StringUtil.equals(projectPath, ExternalSystemApiUtil.getExternalProjectPath(module)))
                    .toList();
            return modules.isEmpty() ? null : ExecutionSearchScopes.executionScope(modules);
        }
    }

    @Override
    public void runActivity(@NotNull final Project project) {
    }

    @NotNull
    public static MavenExecutionSettings getExecutionSettings(
            @NotNull Project project,
            @NotNull String projectPath,
            @NotNull MavenSettings settings,
            @Nullable MavenProjectSettings projectSettings) {
        MavenExecutionSettings result;
        if (projectSettings == null) {
            result = new MavenExecutionSettings(
                    DistributionSettings.getBundled(), null, settings.isOfflineMode());
        } else {
            result = new MavenExecutionSettings(
                    projectSettings.getDistributionSettings(),
                    projectSettings.getVmOptions(), settings.isOfflineMode()
            );
            result.setResolveModulePerSourceSet(projectSettings.getResolveModulePerSourceSet());
            result.setUseQualifiedModuleNames(projectSettings.isUseQualifiedModuleNames());
            result.setNonRecursive(projectSettings.getNonRecursive());
            result.setSnapshotUpdateType(projectSettings.getSnapshotUpdateType());
            result.setThreadCount(projectSettings.getThreadCount());
            result.setOutputLevel(projectSettings.getOutputLevel());
            result.setCheckSources(settings.isCheckSourcesInLocalRepo());
            result.setSkipTests(settings.isSkipTests());
            result.setIncrementalSync(projectSettings.getIncrementalSync());
            result.setShowPluginNodes(!Registry.is("gmaven.import.readonly") && projectSettings.getShowPluginNodes());
            result.setReadonly(Registry.is("gmaven.import.readonly"));
            if (result.isReadonly()) {
                GMavenNotification.INSTANCE.externalSystemNotification(
                        "", GBundle.message("gmaven.settings.system.readonly.info"), project, INFO
                );
            }
            fillExecutionWorkSpace(project, projectSettings, projectPath, result.getExecutionWorkspace());
            if (projectSettings.getArguments() != null) {
                result.withArguments(ParametersListUtil.parse(projectSettings.getArguments(), true, true));
            }
            if (projectSettings.getArgumentsImport() != null) {
                result.setArgumentsImport(ParametersListUtil.parse(projectSettings.getArgumentsImport(), true, true));
            }
        }

        String ideProjectPath;
        if (project.getBasePath() == null ||
                (project.getProjectFilePath() != null && StringUtil.endsWith(project.getProjectFilePath(), ".ipr"))) {
            ideProjectPath = projectSettings != null ? projectSettings.getExternalProjectPath() : projectPath;
        } else {
            ideProjectPath = Paths.get(project.getBasePath(), ".idea", "modules").toString();
        }
        result.setIdeProjectPath(ideProjectPath);

        setJdk(project, projectSettings, result);

        if (settings.isSkipTests()) {
            result.withEnvironmentVariables(Map.of("skipTests", "true"));
        }
        return result;
    }

    private static void setJdk(
            @NotNull Project project, @Nullable MavenProjectSettings projectSettings, MavenExecutionSettings result
    ) {
        String jdkName = projectSettings != null ? projectSettings.getJdkName() : null;
        Sdk jdk = ExternalSystemJdkUtil.getJdk(project, jdkName);
        if (jdk == null) {
            jdk = MavenUtils.suggestProjectSdk();
            if (jdk == null) {
                jdk = ExternalSystemJdkUtil.getJdk(project, USE_INTERNAL_JAVA);
            }
        }
        if (jdk != null) {
            result.setJavaHome(jdk.getHomePath());
            result.setJdkName(jdk.getName());
        }
    }
}

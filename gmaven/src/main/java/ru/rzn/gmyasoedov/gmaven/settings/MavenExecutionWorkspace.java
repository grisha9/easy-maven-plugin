package ru.rzn.gmyasoedov.gmaven.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MavenExecutionWorkspace implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    @NotNull
    private final List<ProfileExecution> profilesData = new ArrayList<>();
    @NotNull
    private final List<ProjectExecution> projectData = new ArrayList<>(2);
    @Nullable
    private String projectBuildFile;
    @Nullable
    private String subProjectBuildFile;
    @Nullable
    private String externalProjectPath;
    @Nullable
    private String multiModuleProjectDirectory;
    @Nullable
    private String incrementalProjectName;

    public void addProfile(@Nullable ProfileExecution data) {
        if (data != null) {
            profilesData.add(data);
        }
    }

    @NotNull
    public List<ProfileExecution> getProfilesData() {
        return List.copyOf(profilesData);
    }

    public void addProject(@Nullable ProjectExecution data) {
        if (data != null) {
            projectData.add(data);
        }
    }

    @NotNull
    public List<ProjectExecution> getProjectData() {
        return List.copyOf(projectData);
    }

    public void clearProjectData() {
        projectData.clear();
    }

    @Nullable
    public String getProjectBuildFile() {
        return projectBuildFile;
    }

    @Nullable
    public void setProjectBuildFile(String projectBuildFile) {
        this.projectBuildFile = projectBuildFile;
    }

    public String getSubProjectBuildFile() {
        return subProjectBuildFile;
    }

    public void setSubProjectBuildFile(String subProjectBuildFile) {
        this.subProjectBuildFile = subProjectBuildFile;
    }

    public String getExternalProjectPath() {
        return externalProjectPath;
    }

    public void setExternalProjectPath(String externalProjectPath) {
        this.externalProjectPath = externalProjectPath;
    }

    @Nullable
    public String getMultiModuleProjectDirectory() {
        return multiModuleProjectDirectory;
    }

    public void setMultiModuleProjectDirectory(@Nullable String multiModuleProjectDirectory) {
        this.multiModuleProjectDirectory = multiModuleProjectDirectory;
    }

    public @Nullable String getIncrementalProjectName() {
        return incrementalProjectName;
    }

    public void setIncrementalProjectName(@Nullable String incrementalProjectName) {
        this.incrementalProjectName = incrementalProjectName;
    }
}

package ru.rzn.gmyasoedov.maven.plugin.reader.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MavenProject implements MavenId, Serializable {

    private String artifactId;
    private String groupId;
    private String version;
    private String name;
    private String packaging;
    private String filePath;
    private String parentFilePath;
    private String basedir;
    private MavenArtifact parentArtifact;
    private List<MavenPlugin> plugins = Collections.emptyList();
    private List<String> modulesDir = Collections.emptyList();
    private List<String> sourceRoots = Collections.emptyList();
    private List<String> testSourceRoots = Collections.emptyList();
    private List<MavenResource> resourceRoots = Collections.emptyList();
    private List<MavenResource> testResourceRoots = Collections.emptyList();
    private List<MavenArtifact> resolvedArtifacts = Collections.emptyList();
    private List<MavenArtifact> dependencyArtifacts = Collections.emptyList();
    private String buildDirectory;//target dir
    private String outputDirectory;
    private String testOutputDirectory;
    private Map<Object, Object> properties;
    private List<MavenRemoteRepository> remoteRepositories = Collections.emptyList();

    private List<String> annotationProcessorPaths = Collections.emptyList();
    private List<String> excludedPaths = Collections.emptyList();
    private String generatedPath;
    private String testGeneratedPath;
    private boolean testJar;

    public boolean isTestJar() {
        return testJar;
    }

    public void setTestJar(boolean testJar) {
        this.testJar = testJar;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getParentFilePath() {
        return parentFilePath;
    }

    public void setParentFilePath(String parentFilePath) {
        this.parentFilePath = parentFilePath;
    }

    public String getBasedir() {
        return basedir;
    }

    public void setBasedir(String basedir) {
        this.basedir = basedir;
    }

    public MavenArtifact getParentArtifact() {
        return parentArtifact;
    }

    public void setParentArtifact(MavenArtifact parentArtifact) {
        this.parentArtifact = parentArtifact;
    }

    public List<MavenPlugin> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<MavenPlugin> plugins) {
        this.plugins = plugins;
    }

    public List<String> getModulesDir() {
        return modulesDir;
    }

    public void setModulesDir(List<String> modulesDir) {
        this.modulesDir = modulesDir;
    }

    public List<String> getSourceRoots() {
        return sourceRoots;
    }

    public void setSourceRoots(List<String> sourceRoots) {
        this.sourceRoots = sourceRoots;
    }

    public List<String> getTestSourceRoots() {
        return testSourceRoots;
    }

    public void setTestSourceRoots(List<String> testSourceRoots) {
        this.testSourceRoots = testSourceRoots;
    }

    public List<MavenResource> getResourceRoots() {
        return resourceRoots;
    }

    public void setResourceRoots(List<MavenResource> resourceRoots) {
        this.resourceRoots = resourceRoots;
    }

    public List<MavenResource> getTestResourceRoots() {
        return testResourceRoots;
    }

    public void setTestResourceRoots(List<MavenResource> testResourceRoots) {
        this.testResourceRoots = testResourceRoots;
    }

    public List<MavenArtifact> getResolvedArtifacts() {
        return resolvedArtifacts;
    }

    public void setResolvedArtifacts(List<MavenArtifact> resolvedArtifacts) {
        this.resolvedArtifacts = resolvedArtifacts;
    }

    public List<MavenArtifact> getDependencyArtifacts() {
        return dependencyArtifacts;
    }

    public void setDependencyArtifacts(List<MavenArtifact> dependencyArtifacts) {
        this.dependencyArtifacts = dependencyArtifacts;
    }

    public String getBuildDirectory() {
        return buildDirectory;
    }

    public void setBuildDirectory(String buildDirectory) {
        this.buildDirectory = buildDirectory;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public String getTestOutputDirectory() {
        return testOutputDirectory;
    }

    public void setTestOutputDirectory(String testOutputDirectory) {
        this.testOutputDirectory = testOutputDirectory;
    }

    public Map<Object, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<Object, Object> properties) {
        this.properties = properties;
    }

    public List<MavenRemoteRepository> getRemoteRepositories() {
        return remoteRepositories;
    }

    public void setRemoteRepositories(List<MavenRemoteRepository> remoteRepositories) {
        this.remoteRepositories = remoteRepositories;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }

    public String getGeneratedPath() {
        return generatedPath;
    }

    public void setGeneratedPath(String generatedPath) {
        this.generatedPath = generatedPath;
    }

    public String getTestGeneratedPath() {
        return testGeneratedPath;
    }

    public void setTestGeneratedPath(String testGeneratedPath) {
        this.testGeneratedPath = testGeneratedPath;
    }

    public List<String> getAnnotationProcessorPaths() {
        return annotationProcessorPaths;
    }

    public void setAnnotationProcessorPaths(List<String> annotationProcessorPaths) {
        this.annotationProcessorPaths = annotationProcessorPaths;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MavenProject that = (MavenProject) o;

        if (!Objects.equals(artifactId, that.artifactId)) return false;
        if (!Objects.equals(groupId, that.groupId)) return false;
        if (!Objects.equals(version, that.version)) return false;
        if (!Objects.equals(name, that.name)) return false;
        if (!Objects.equals(packaging, that.packaging)) return false;
        return Objects.equals(basedir, that.basedir);
    }

    @Override
    public int hashCode() {
        int result = artifactId != null ? artifactId.hashCode() : 0;
        result = 31 * result + (groupId != null ? groupId.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (packaging != null ? packaging.hashCode() : 0);
        result = 31 * result + (basedir != null ? basedir.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MavenProject{" +
                ", groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version=" + version +
                '}';
    }
}

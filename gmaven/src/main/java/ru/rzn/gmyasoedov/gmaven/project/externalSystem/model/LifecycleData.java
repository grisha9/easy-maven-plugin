package ru.rzn.gmyasoedov.gmaven.project.externalSystem.model;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

public class LifecycleData extends AbstractExternalEntityData implements ExternalConfigPathAware, Comparable<TaskData> {
    @NotNull public static final Key<LifecycleData> KEY = Key.create(LifecycleData.class, 250);

    @NotNull
    private final String name;
    @NotNull
    private final String linkedExternalProjectPath;
    private final boolean maven4;

    @PropertyMapping({"owner", "name", "linkedExternalProjectPath", "maven4"})
    public LifecycleData(@NotNull ProjectSystemId owner,
                         @NotNull String name,
                         @NotNull String linkedExternalProjectPath,
                         boolean maven4) {
        super(owner);
        this.name = name;
        this.linkedExternalProjectPath = linkedExternalProjectPath;
        this.maven4 = maven4;
    }

    @NotNull
    public @NlsSafe String getName() {
        return name;
    }

    @Override
    @NotNull
    public String getLinkedExternalProjectPath() {
        return linkedExternalProjectPath;
    }

    public boolean isMaven4() {
        return maven4;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        LifecycleData that = (LifecycleData) o;
        return maven4 == that.maven4 && name.equals(that.name) && linkedExternalProjectPath.equals(that.linkedExternalProjectPath);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + linkedExternalProjectPath.hashCode();
        result = 31 * result + Boolean.hashCode(maven4);
        return result;
    }

    @Override
    public int compareTo(@NotNull TaskData that) {
        return name.compareTo(that.getName());
    }

    @Override
    public @NlsSafe String toString() {
        return name;
    }
}

package ru.rzn.gmyasoedov.gmaven.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.project.ExternalStorageConfigurationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.TreeSet;

@State(name = "MavenSettings", storages = @Storage("gmaven.xml"))
public class MavenSettings extends AbstractExternalSystemSettings<MavenSettings, MavenProjectSettings, MavenSettingsListener>
        implements PersistentStateComponent<MavenSettings.MyState> {

    private boolean offlineMode = false;
    private boolean skipTests = false;
    private boolean checkSourcesInLocalRepo = false;
    private boolean showAllPhases = false;
    private boolean coloredSupport = true;

    public MavenSettings(@NotNull Project project) {
        super(MavenSettingsListener.TOPIC, project);
    }

    @NotNull
    public static MavenSettings getInstance(@NotNull Project project) {
        return project.getService(MavenSettings.class);
    }


    @Override
    public void subscribe(@NotNull ExternalSystemSettingsListener<MavenProjectSettings> listener,
                          @NotNull Disposable parentDisposable) {
        doSubscribe(new DelegatingMavenSettingsListenerAdapter(listener), parentDisposable);
    }

    @Override
    protected void copyExtraSettingsFrom(@NotNull MavenSettings settings) {
    }

    @Nullable
    @Override
    public MavenSettings.MyState getState() {
        MyState state = new MyState();
        fillState(state);

        state.setOfflineMode(isOfflineMode());
        state.setSkipTests(isSkipTests());
        state.setCheckSourcesInLocalRepo(isCheckSourcesInLocalRepo());
        state.setShowAllPhases(showAllPhases);
        state.setColoredSupport(coloredSupport);
        return state;
    }

    @Override
    public void loadState(@NotNull MyState state) {
        super.loadState(state);
        setOfflineMode(state.isOfflineMode());
        setSkipTests(state.skipTests);
        setCheckSourcesInLocalRepo(state.checkSourcesInLocalRepo);
        setShowAllPhases(state.showAllPhases);
        setColoredSupport(state.coloredSupport);
    }

    public boolean isOfflineMode() {
        return offlineMode;
    }

    public void setOfflineMode(boolean offlineMode) {
        this.offlineMode = offlineMode;
    }

    public boolean isSkipTests() {
        return skipTests;
    }

    public void setSkipTests(boolean skipTests) {
        this.skipTests = skipTests;
    }

    public boolean isCheckSourcesInLocalRepo() {
        return checkSourcesInLocalRepo;
    }

    public void setCheckSourcesInLocalRepo(boolean checkSourcesInLocalRepo) {
        this.checkSourcesInLocalRepo = checkSourcesInLocalRepo;
    }

    public boolean isShowAllPhases() {
        return showAllPhases;
    }

    public void setShowAllPhases(boolean showAllPhases) {
        this.showAllPhases = showAllPhases;
    }

    public boolean isColoredSupport() {
        return coloredSupport;
    }

    public void setColoredSupport(boolean coloredSupport) {
        this.coloredSupport = coloredSupport;
    }

    public boolean getStoreProjectFilesExternally() {
        return ExternalStorageConfigurationManager.getInstance(getProject()).isEnabled();
    }

    public void setStoreProjectFilesExternally(boolean value) {
        ExternalProjectsManagerImpl.getInstance(getProject()).setStoreExternally(value);
    }

    @Override
    protected void checkSettings(@NotNull MavenProjectSettings old, @NotNull MavenProjectSettings current) {
    }

    public static class MyState implements State<MavenProjectSettings> {
        private final Set<MavenProjectSettings> myProjectSettings = new TreeSet<>();
        private boolean isOfflineMode = false;
        private boolean skipTests = false;
        private boolean checkSourcesInLocalRepo = false;
        private boolean showAllPhases = false;
        private boolean coloredSupport = true;

        @Override
        @XCollection(elementTypes = MavenProjectSettings.class)
        public Set<MavenProjectSettings> getLinkedExternalProjectsSettings() {
            return myProjectSettings;
        }

        @Override
        public void setLinkedExternalProjectsSettings(Set<MavenProjectSettings> settings) {
            if (settings != null) {
                myProjectSettings.addAll(settings);
            }
        }

        public boolean isOfflineMode() {
            return isOfflineMode;
        }

        public void setOfflineMode(boolean isOfflineMode) {
            this.isOfflineMode = isOfflineMode;
        }

        public boolean isSkipTests() {
            return skipTests;
        }

        public void setSkipTests(boolean skipTests) {
            this.skipTests = skipTests;
        }

        public boolean isCheckSourcesInLocalRepo() {
            return checkSourcesInLocalRepo;
        }

        public void setCheckSourcesInLocalRepo(boolean checkSourcesInLocalRepo) {
            this.checkSourcesInLocalRepo = checkSourcesInLocalRepo;
        }

        public boolean isShowAllPhases() {
            return showAllPhases;
        }

        public void setShowAllPhases(boolean showAllPhases) {
            this.showAllPhases = showAllPhases;
        }

        public boolean isColoredSupport() {
            return coloredSupport;
        }

        public void setColoredSupport(boolean coloredSupport) {
            this.coloredSupport = coloredSupport;
        }
    }
}
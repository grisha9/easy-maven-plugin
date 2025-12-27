package ru.rzn.gmyasoedov.gmaven.project.externalSystem.view;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.view.ExternalProjectsView;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rzn.gmyasoedov.gmaven.project.externalSystem.model.ProfileData;
import ru.rzn.gmyasoedov.gmaven.project.profile.ProfileState;
import ru.rzn.gmyasoedov.gmaven.project.profile.ProjectProfilesStateService;

public class ProfileNode extends ExternalSystemNode<ProfileData> {

    public ProfileNode(@NotNull ExternalProjectsView externalProjectsView, @NotNull DataNode<ProfileData> dataNode) {
        super(externalProjectsView, null, dataNode);
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
        super.update(presentation);
        String state = getState(getData());
        if (ProfileData.SimpleProfile.ACTIVE.name().equals(state)) {
            presentation.setIcon(AllIcons.Diff.GutterCheckBoxSelected);
            presentation.setTooltip("Profile is active. Double click - change state");
        } else if (ProfileData.SimpleProfile.INACTIVE.name().equals(state)) {
            presentation.setIcon(AllIcons.Diff.GutterCheckBox);
            presentation.setTooltip("Profile is disable. Double click - change state");
        } else {
            presentation.setIcon(AllIcons.Diff.GutterCheckBoxIndeterminate);
            presentation.setTooltip("Profile is indeterminate (depends on condition in activation tag)Double click - change state");
        }
    }

    @NotNull
    private String getState(ProfileData data) {
        ProfileState currentState = ProjectProfilesStateService.getState(getProject(), data);
        if (currentState.getSimpleProfile() != null) return currentState.getSimpleProfile().name();
        if (currentState.getActivationProfile() != null) return currentState.getActivationProfile().name();
        throw new IllegalStateException("illegal profile state " + data.getStateKey());
    }

    @Override
    public @NlsSafe String getName() {
        return super.getName();
    }

    @Nullable
    @Override
    protected String getActionId() {
        return "GMaven.ExternalSystem.ChangeProfile";
    }

    @Override
    public boolean isAlwaysLeaf() {
        return true;
    }
}

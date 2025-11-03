
package ru.rzn.gmyasoedov.gmaven.settings;

import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle;

import static ru.rzn.gmyasoedov.gmaven.bundle.GBundle.message;

public class SystemSettingsControlBuilder implements ExternalSystemSettingsControl<MavenSettings> {

    @NotNull
    private final MavenSettings myInitialSettings;

    @Nullable
    private JBCheckBox offlineCheckBox;
    @Nullable
    private JBCheckBox skipTestsCheckBox;
    @Nullable
    private JBCheckBox checkSourcesCheckBox;
    @Nullable
    private JBCheckBox readonlyCheckBox;
    @Nullable
    private JBCheckBox wslSupportCheckBox;
    @Nullable
    private JBCheckBox colorSupportCheckBox;

    public SystemSettingsControlBuilder(@NotNull MavenSettings initialSettings) {
        myInitialSettings = initialSettings;
    }

    @Override
    public void fillUi(@NotNull PaintAwarePanel canvas, int indentLevel) {
        offlineCheckBox = new JBCheckBox(GBundle.message("gmaven.settings.system.offline"));
        skipTestsCheckBox = new JBCheckBox(GBundle.message("gmaven.settings.system.skip.tests"));
        checkSourcesCheckBox = new JBCheckBox(GBundle.message("gmaven.settings.system.check.sources"));
        checkSourcesCheckBox.setToolTipText(message("gmaven.settings.system.check.sources.tooltip"));
        readonlyCheckBox = new JBCheckBox(GBundle.message("gmaven.settings.system.readonly"));
        readonlyCheckBox.setToolTipText(message("gmaven.settings.system.readonly.tooltip"));
        wslSupportCheckBox = new JBCheckBox(GBundle.message("gmaven.settings.system.wsl"));
        wslSupportCheckBox.setToolTipText(message("gmaven.settings.system.wsl.tooltip"));
        wslSupportCheckBox.setVisible(SystemInfo.isWindows);
        colorSupportCheckBox = new JBCheckBox(GBundle.message("gmaven.settings.system.colored"));

        canvas.add(offlineCheckBox, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
        canvas.add(skipTestsCheckBox, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
        canvas.add(readonlyCheckBox, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
        canvas.add(checkSourcesCheckBox, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
        canvas.add(colorSupportCheckBox, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
        canvas.add(wslSupportCheckBox, ExternalSystemUiUtil.getFillLineConstraints(0));
    }

    @Override
    public void showUi(boolean show) {
        ExternalSystemUiUtil.showUi(this, show);
    }

    @Override
    public void reset() {
        if (offlineCheckBox != null) {
            offlineCheckBox.setSelected(myInitialSettings.isOfflineMode());
        }
        if (skipTestsCheckBox != null) {
            skipTestsCheckBox.setSelected(myInitialSettings.isSkipTests());
        }
        if (checkSourcesCheckBox != null) {
            checkSourcesCheckBox.setSelected(myInitialSettings.isCheckSourcesInLocalRepo());
        }
        if (readonlyCheckBox != null) {
            readonlyCheckBox.setSelected(Registry.is("gmaven.import.readonly"));
        }
        if (wslSupportCheckBox != null) {
            wslSupportCheckBox.setSelected(Registry.is("gmaven.wsl.support"));
        }
        if (colorSupportCheckBox != null) {
            colorSupportCheckBox.setSelected(myInitialSettings.isColoredSupport());
        }
    }

    @Override
    public boolean isModified() {
        if (offlineCheckBox != null && offlineCheckBox.isSelected() != myInitialSettings.isOfflineMode()) {
            return true;
        }
        if (skipTestsCheckBox != null && skipTestsCheckBox.isSelected() != myInitialSettings.isSkipTests()) {
            return true;
        }
        if (readonlyCheckBox != null && readonlyCheckBox.isSelected() != Registry.is("gmaven.import.readonly")) {
            return true;
        }
        if (wslSupportCheckBox != null && wslSupportCheckBox.isSelected() != Registry.is("gmaven.wsl.support")) {
            return true;
        }
        if (checkSourcesCheckBox != null
                && checkSourcesCheckBox.isSelected() != myInitialSettings.isCheckSourcesInLocalRepo()) {
            return true;
        }
        if (colorSupportCheckBox != null && colorSupportCheckBox.isSelected() != myInitialSettings.isColoredSupport()) {
            return true;
        }
        return false;
    }

    @Override
    public void apply(@NotNull MavenSettings settings) {
        if (offlineCheckBox != null) {
            settings.setOfflineMode(offlineCheckBox.isSelected());
        }
        if (skipTestsCheckBox != null) {
            settings.setSkipTests(skipTestsCheckBox.isSelected());
        }
        if (checkSourcesCheckBox != null) {
            settings.setCheckSourcesInLocalRepo(checkSourcesCheckBox.isSelected());
        }
        if (readonlyCheckBox != null) {
            Registry.get("gmaven.import.readonly").setValue(readonlyCheckBox.isSelected());
        }
        if (wslSupportCheckBox != null) {
            Registry.get("gmaven.wsl.support").setValue(wslSupportCheckBox.isSelected());
        }
        if (colorSupportCheckBox != null) {
            settings.setColoredSupport(colorSupportCheckBox.isSelected());
        }
    }

    @Override
    public boolean validate(@NotNull MavenSettings settings) {
        return true;
    }

    @Override
    public void disposeUIResources() {
        ExternalSystemUiUtil.disposeUi(this);
    }
}

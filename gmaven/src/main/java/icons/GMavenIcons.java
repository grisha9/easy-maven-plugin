package icons;

import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.util.IconLoader.getIcon;

public final class GMavenIcons {
    public static final @NotNull Icon ExecuteMavenGoal = getIcon("images/executeMavenGoal.svg", GMavenIcons.class);
    public static final @NotNull Icon MavenLoadChanges = getIcon("images/mavenLoadChanges.svg", GMavenIcons.class);
    public static final @NotNull Icon MavenProject = getIcon("images/mavenProject.svg", GMavenIcons.class);
    public static final @NotNull Icon MavenProjectSmall = getIcon("images/mavenProject12.svg", GMavenIcons.class);
    public static final @NotNull Icon MavenIgnored = getIcon("images/mavenIgnored.svg", GMavenIcons.class);
    public static final @NotNull Icon ParentProject = getIcon("images/mavenParentProjects.svg", GMavenIcons.class);
    public static final @NotNull Icon ToolWindowMaven = getIcon("images/toolwindow/maven.svg", GMavenIcons.class);
    public static final @NotNull Icon RunSmallCorner = getIcon("images/mvn_run.svg", GMavenIcons.class);
    public static final @NotNull Icon RepositoryLibraryLogo = getIcon("images/repositoryLibraryLogo.svg", GMavenIcons.class);

    private static final @NotNull Icon[] icons = {MavenProjectSmall, RunSmallCorner};
    public static final @NotNull Icon MavenRun = LayeredIcon.layeredIcon(icons);
}

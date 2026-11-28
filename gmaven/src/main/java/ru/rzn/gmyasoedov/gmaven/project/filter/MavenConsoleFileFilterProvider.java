package ru.rzn.gmyasoedov.gmaven.project.filter;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MavenConsoleFileFilterProvider implements ConsoleFilterProvider {
    private static final String MAVEN_PREFIX = "(?:^|(?:\\[\\w+\\]\\s*)( /)?)";
    private static final String CONSOLE_FILTER_REGEXP =
            MAVEN_PREFIX + RegexpFilter.FILE_PATH_MACROS + ":\\[" + RegexpFilter.LINE_MACROS + "," + RegexpFilter.COLUMN_MACROS + "]";
    private static final String CONSOLE_FILTER_REGEXP_KT =
            MAVEN_PREFIX + RegexpFilter.FILE_PATH_MACROS + ": \\(" + RegexpFilter.LINE_MACROS + ", " + RegexpFilter.COLUMN_MACROS + "\\)";
    /**
     * Maven model (build file) problem location, see ModelProblemUtils#formatLocation:
     * {@code @ [groupId:artifactId:version, ]/path/pom.xml, line 22, column 16}
     */
    private static final String CONSOLE_FILTER_REGEXP_MODEL_PROBLEM =
            "@(?: [^\\s,@]+,)?" + RegexpFilter.FILE_PATH_MACROS + ", line " + RegexpFilter.LINE_MACROS + ", column " + RegexpFilter.COLUMN_MACROS;
    /**
     * File path followed later in the same line by position {@code @line:column}, e.g. Maven XML parser errors:
     * {@code Malformed POM /path/pom.xml: Unrecognised tag: 'dependency1' (position: ... @22:16)}
     */
    private static final String CONSOLE_FILTER_REGEXP_AT_POSITION =
            RegexpFilter.FILE_PATH_MACROS + ".*?@" + RegexpFilter.LINE_MACROS + ":" + RegexpFilter.COLUMN_MACROS + "\\b";
    /**
     * File path in parentheses without position, e.g. {@code The project g:a:v (/path/pom.xml) has 1 error}.
     * The lookahead guarantees that the non-word prefix of {@link RegexpFilter#FILE_PATH_MACROS} is '('.
     */
    private static final String CONSOLE_FILTER_REGEXP_PARENTHESES =
            "(?=\\()" + RegexpFilter.FILE_PATH_MACROS + "\\)";


    @Override
    public Filter[] getDefaultFilters(@NotNull Project project) {
        return new Filter[]{
                new RegexpFilterMaven(project, CONSOLE_FILTER_REGEXP),
                new RegexpFilterMaven(project, CONSOLE_FILTER_REGEXP_KT),
                new RegexpFilterMaven(project, CONSOLE_FILTER_REGEXP_MODEL_PROBLEM),
                new RegexpFilterMaven(project, CONSOLE_FILTER_REGEXP_AT_POSITION),
                new RegexpFilterMaven(project, CONSOLE_FILTER_REGEXP_PARENTHESES)
        };
    }

    private static class RegexpFilterMaven extends RegexpFilter {
        private final Project project;

        private RegexpFilterMaven(Project project, String expression) {
            super(project, expression);
            this.project = project;
        }

        @Nullable
        @Override
        protected HyperlinkInfo createOpenFileHyperlink(String fileName, int line, int column) {
            HyperlinkInfo res = super.createOpenFileHyperlink(fileName, line, column);
            if (res == null && fileName.startsWith("\\") && SystemInfo.isWindows) {
                // Maven cut prefix 'C:\' from paths on Windows
                VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
                if (roots.length > 0) {
                    String projectPath = roots[0].getPath();
                    if (projectPath.matches("[A-Z]:[\\\\/].+")) {
                        res = super.createOpenFileHyperlink(projectPath.charAt(0) + ":" + fileName, line, column);
                    }
                }
            }

            return res;
        }
    }
}
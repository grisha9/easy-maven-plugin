# GMaven Changelog

## [Unreleased]

## [252.7] - 2025-12-14

### Added

- Completions for GroupId/ArtifactId/version. [Issue](https://github.com/grisha9/easy-maven-plugin/pull/52)
- Open Local Repo Action. [Issue](https://github.com/grisha9/gmaven-plugin/issues/47)

### Fix

- DA refresh dependency tree. [Issue](https://github.com/grisha9/easy-maven-plugin/issues/49)

## [252.6] - 2025-11-25

### Added

- Run test via Maven. Escape
  space. [Wiki](https://github.com/grisha9/easy-maven-plugin/wiki/Run-&-Debug-test-via-Maven-'test'-phase).
- Open Local Repo Action. [Issue](https://github.com/grisha9/gmaven-plugin/issues/47)

## [252.5] - 2025-11-11

### Added

- Run test via Maven. [Wiki](https://github.com/grisha9/easy-maven-plugin/wiki/Run-&-Debug-test-via-Maven-'test'-phase).
- Maven colored output

## [252.4] - 2025-11-03

### Added

- Colored Maven output

### Fixes

- Prepare for IDEA 253

## [252.3] - 2025-09-28

### Fixes

- Prepare for IDEA 253
- Test jar support

## [252.2] - 2025-09-19

### Fixes

- Prepare for IDEA 253

## [252.1] - 2025-08-18

### Fixes

- Support IDEA 252
- Maven4 Phases

## [251.2] - 2025-06-28

### Fixes

- Android Studio Meerkat support ([issue](https://github.com/grisha9/gmaven-plugin/issues/34))

### Added

- maven -e(errors) param to output level settings

## [251.1] - 2025-04-18

### Fixes

- Support Kotlin K2 mode

## [243.1] - 2025-02-21

### Fixes

- Kotlin noarg support jakarta.* [Issue](https://github.com/grisha9/gmaven-plugin/issues/29)
- Module dependency visibility [Issue](https://github.com/grisha9/gmaven-plugin/issues/27)

## [242.7] - 2025-02-13

### Fix

- Rename to Easy Maven ([issue](https://github.com/grisha9/gmaven-plugin/issues/10))

## [242.6] - 2025-02-08

### Added

- Rename to Easy Maven ([issue](https://github.com/grisha9/gmaven-plugin/issues/10))
- Support 2025.1 eap
- Support [MCOMPILER-391](https://issues.apache.org/jira/browse/MCOMPILER-391)

## [242.5] - 2024-11-23
### Added

- WSL
  Support [Wiki](https://github.com/grisha9/gmaven-plugin/wiki/GMaven-project-settings#wsl-support-wondows-users-only)
- Deployed maven plugin to maven central and migrate on it ([issue](https://github.com/grisha9/gmaven-plugin/issues/21))
- ### Fixed
- Escaping space symbol in starting maven process path ([issue](https://github.com/grisha9/gmaven-plugin/issues/20))

## [242.4] - 2024-11-07
### Added
- Incremental sync (Experimental feature in gmaven settings)
- ### Fixed
- Sync Maven-plugin fixed process result output
- Escaping space symbol in path ([issue](https://github.com/grisha9/gmaven-plugin/issues/20))

## [242.2] - 2024-10-10
### Added
- Read only mode [wiki](https://github.com/grisha9/gmaven-plugin/wiki/GMaven-project-settings#readonly-mode)
- Full support [Maven Daemon](https://github.com/grisha9/gmaven-plugin/wiki/GMaven-project-settings#maven-daemon-full-support)

## [242.1] - 2024-09-15
### Added
- Support IDEA 242.* version. Icons for new UI.
- Rework Maven integration via [file](https://github.com/grisha9/gmaven-plugin/wiki/New-integration-with-Maven-model-reader-plugin-via-file.-Since-242.1-GMaven-version)

## [233.30] - 2024-07-13
### Fixed
- Support test-jar type for module dependency ([issue](https://github.com/grisha9/gmaven-plugin/issues/15))

## [233.29] - 2024-06-22
### Fixed
- IDEA suggests GMaven import, even though project is GMaven already ([issue](https://github.com/grisha9/gmaven-plugin/issues/14))

## [233.28] - 2024-05-27
### Added
- Button for run multiple tasks ([issue](https://github.com/grisha9/gmaven-plugin/issues/13))
### Fixed
- Unlink\Link GMaven project ([issue](https://github.com/grisha9/gmaven-plugin/issues/12))
- Groovy content roots path (source/directory)

## [233.27] - 2024-04-26
### Added
- Added "Show all phases" toggle ([issue](https://github.com/grisha9/gmaven-plugin/issues/6))
- Support Maven Compiler Plugin enablePreview parameter ([issue](https://youtrack.jetbrains.com/issue/IDEA-340737/))
### Fixed
- Link/Unlink Maven projects

## [233.26] - 2024-04-14
### Added
- Support aspectj-maven-plugin ([issue](https://github.com/grisha9/gmaven-plugin/issues/7))
- Support --enable-preview

## [233.25] - 2024-03-27
### Fixed
- Project settings UI
- Windows OS: error when opening a project ([issue](https://github.com/grisha9/gmaven-plugin/issues/5))
- Action: download all sources(Not worked) [wiki](https://github.com/grisha9/gmaven-plugin/wiki/Actions#download-all-sources)
### Added
- Improvement artifacts completion and navigation


## [233.24] - 2024-03-17
### Fixed
- Kotlin content roots
### Added
- Action: download all sources [wiki](https://github.com/grisha9/gmaven-plugin/wiki/Actions#download-all-sources)
- Support Maven property in build files(navigation/completion/rename)

## [233.23] - 2024-03-08
### Added
- Search artifact from management sections [wiki](https://github.com/grisha9/gmaven-plugin/wiki/Actions#search-by-artifactid-from-dependencymanagement-and-pluginmanagement-sections)
- Support IDEA 241+ version
### Fixed
- Increased speed of projects reimport (Redundant dependency indexing)
- Dom gutter navigation for pom.xml

## [233.22] - 2024-02-02
### Added
- Fast open for existing project [wiki](https://github.com/grisha9/gmaven-plugin/wiki/GMaven-registry-keys#gmavenfastopenproject)
- Update bundled maven wrapper to 3.9.6

## [233.21] - 2024-01-14
### Added
- action: reload GMaven project with plugins [wiki](https://github.com/grisha9/gmaven-plugin/wiki/Actions#reload-gmaven-project-with-plugins)
- action: reset profile state to default [wiki](https://github.com/grisha9/gmaven-plugin/wiki/Actions#reset-profile-state-to-default)
- action: show effective pom  [wiki](https://github.com/grisha9/gmaven-plugin/wiki/Actions#show-effective-pom)

## [233.20] - 2023-12-24
### Fixed
- correct multiModuleProjectDirectory (checked .mvn folder)

## [233.19] - 2023-12-17
### Added
- Navigation from dependency/artifactId tag to module or local repository pom file
### Fixed
- key="gmaven.subtask.args" for sub task additional arguments [wiki](https://github.com/grisha9/gmaven-plugin/wiki/GMaven-registry-keys#gmavensubtaskargs)
- fixed '--javaagent param' for task execution
- find get parent for task execution

## [233.18] - 2023-12-02
### Fixed
- optimized modules cache data
- tool window actions visibility

## [232.17] - 2023-11-18
### Added
- Navigate to config file from GMaven-tool window/Module (F4 or Right click/Open External Config)
### Fixed
- IgnoreMavenProjectAction unexpected exceptions

## [232.16] - 2023-11-12
### Added
- Quick access to maven snapshot setting [wiki](https://github.com/grisha9/gmaven-plugin/wiki/GMaven-project-settings#update-snapshot-mode).
- clearer error messages in build/run windows
### Fixed
- do not use Kotlin deprecated versions

## [232.15] - 2023-11-01
### Added
- Optimize external project model structure
- Completions for groupId/artifactId/version [Wiki](https://github.com/grisha9/gmaven-plugin/wiki/Completions). Via perform HTTP request to https://search.maven.org/. Requires disabling bundled maven plugin that logic not intersected (Otherwise, the completions from the bundled maven plugin will work).
- Ignore/unignore maven projects with submodules (Right click on pom.xnl file or module directory).

### Fixed
- https://github.com/grisha9/gmaven-plugin/issues/2
- NPE for maven 4.0.0-alpha8

## [232.14] - 2023-10-26
### Added
- Quick fix for Maven distribution
- Gutter icons for configuration files. [Wiki](https://github.com/grisha9/gmaven-plugin/wiki/GMaven-registry-keys#gmavengutterannotation)   
- Icon for Maven project configuration files

## [232.13] - 2023-10-16

### Fixed
- Maven wrapper: changing distributionUrl in maven-wrapper.properties is not picked up during import
- Removed -Xmx and -Xms prediction and optimize firstRun

## [232.12] - 2023-10-08

### Added
- Mvnd task execution (https://github.com/grisha9/gmaven-plugin/wiki/GMaven-project-settings#delegate-tasks-execution-to-maven-daemon)
- Custom lifecycles (https://github.com/grisha9/gmaven-plugin/wiki/GMaven-registry-keys#gmavenlifecycles)

## [232.11] - 2023-10-02

### Fixed
- https://github.com/grisha9/gmaven-plugin/issues/4 (support relative path)

## [232.10] - 2023-09-27

### Added
- Download sources
- Improvement maven snapshot setting

### Fixed
- Task execution - use project context (get parent build file correctly)
- Sub-module creation (redundant gitignore & groupId from parent)

## [232.8] - 2023-09-18

### Added
- use ExternalEntityData for custom model data

### Fixed
- https://github.com/grisha9/gmaven-plugin/issues/3 (experimental key="gmaven.settings.linked.modules")
- create new module with artifactId != directory
- set project jdk after first import

## [232.7] - 2023-08-29

### Added
- Kotlin JVM support


## [232.6] - 2023-08-23

### Added
- Cleanup README
- Added setting - showPluginNodes for optimizing large projects

## [232.5] - 2023-08-22

### Added
- Groovy support (maven-compiler-plugin/groovy-maven-plugin/gmavenplus-plugin)
- Eclipse/Groovy-Eclipse Java Compiler support

### Fixed
- Default compiler settings from maven.properties
- Change jdk from GMaven settings
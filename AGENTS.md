# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project overview

**Easy Maven** (internal name `gmaven-plugin`, old name GMaven) is an IntelliJ IDEA plugin
(`ru.rzn.gmyasoedov.gmaven`) that adds Maven support to IDEA as a lightweight replacement for the bundled
Maven plugin. Core idea: **"everything is a Maven task"** — project import, test runs, dependency
analysis and debugging are executed by running real Maven with the appropriate plugin goals. The plugin
is a Maven proxy for IDEA and does not reimplement Maven resolution.

- Plugin id: `ru.rzn.gmyasoedov.gmaven`, display name "Easy Maven"
- Marketplace page: https://plugins.jetbrains.com/plugin/22370-easy-maven
- Issues: https://github.com/grisha9/easy-maven-plugin/issues
- Target platform: IntelliJ IDEA 262.x (`sinceBuild` 262, `untilBuild` 290.*), platform `262.8665.337`

## Repository layout

| Path | Purpose |
|------|---------|
| `gmaven/` | Main IntelliJ plugin module (Kotlin + some Java). Almost all code lives here. |
| `server-api/` | Shared API between the IDE side and the Maven-side server (`ru.rzn.gmyasoedov.serverapi`). |
| `maven-ext-event-handler/` | Maven extension (runtime-only dependency of `gmaven`) that reports build events back to the IDE. |
| `CHANGELOG.md` | Release notes; read by the `org.jetbrains.changelog` Gradle plugin for `changeNotes`. |
| `gradle.properties` | `pluginVersion`, `pluginSinceBuild`, `pluginUntilBuild`, `platformVersion`. |
| `.github/workflows/build.yaml` | CI: runs `./gradlew :gmaven:test` on push. |

Main packages inside `gmaven/src/main`:

- `project/` — import/resolution (`MavenProjectResolver`, `externalSystem/`, `policy/`), task execution
  (`task/MavenTaskManager`), actions (`action/`).
- `wizard/` — "New Project" wizard (`MavenNewProjectWizard`), open/link providers
  (`GOpenProjectProvider`, `GProjectOpenProcessor`), wizard data interfaces.
- `server/` — client side of the Maven server protocol (`GServerRequest`, `runTasks`, `getProjectModel`).
- `settings/` — persistent settings (`MavenSettings`, `MavenExecutionSettings`, advanced/debug states).
- `bundle/GBundle.java` — i18n bundle `messages.GBundle`.
- `extensionpoints/` — plugin-specific extension points (e.g. `MavenFullImportPlugin`).
- `util/`, `utils/` — helpers (`MavenLog`, `MavenUtils`, notifications).

## Build, run and test

Use the Gradle wrapper from the repository root.

```bash
./gradlew :gmaven:compileKotlin      # compile plugin code
./gradlew :gmaven:test               # unit/integration tests (IntelliJ test framework)
./gradlew :gmaven:runIde             # launch sandbox IDE with the plugin
./gradlew :gmaven:buildPlugin        # build distributable zip
```

Notes:

- Kotlin and Java toolchain are **JVM 25** (`kotlin { jvmToolchain(25) }`); CI (`build.yaml`) uses **Java 21**.
  Keep code compatible with both unless you change the CI config.
- Kotlin compiler flag `-Xjvm-default=all` is set; `kotlin.stdlib.default.dependency=false`.
- Gradle daemon heap: `-Xmx2048M`; Kotlin daemon `-Xmx2G`.
- Platform sources are downloaded (`downloadSources=true`), which helps when reading IntelliJ API.

## Coding conventions

- Language: Kotlin is the default. Java only where already used (e.g. `GBundle.java`) or when required.
- Indentation: 4 spaces in Kotlin files in `gmaven/src/main/kotlin` (keep the existing style of each file).
- Logging: use `ru.rzn.gmyasoedov.gmaven.utils.MavenLog` rather than creating new loggers.
- User-facing text: put strings in `messages/GBundle.properties` and access them through `GBundle.message(...)`.
  Do not hard-code UI strings.
- Notifications: use `GMavenNotification` helpers.
- Keep Maven-facing logic in the Maven task / server layer; do not add a parallel Maven model implementation
  in the IDE process.

## IntelliJ Platform API rules (important)

This plugin targets many IDE versions, so **avoid deprecated and internal platform APIs**. Before using a platform
class or method, verify its status:

1. Check `@ApiStatus.Internal`, `@ApiStatus.Obsolete`, `@ApiStatus.ScheduledForRemoval`
   and `@Deprecated` (Kotlin `@Deprecated` / Java `@Deprecated`) on the symbol and its class.
2. Prefer the replacement named in the deprecation message (`ReplaceWith`, `@see`, or the Javadoc).
3. When a deprecated method is only overridden (e.g. `AttachSourcesProvider.getActions`), implement the
   new method and mark the legacy override `@Deprecated`.
4. The Plugin Verifier reports deprecated usages; keep the number of such usages at zero when possible.

Reference sources: the IntelliJ Community repository (e.g. `plugins/gradle`) is the best example of
how JetBrains migrates away from deprecated APIs and how use ExternalSystem API. Platform sources are also available through the IDE's
library sources.

## Working with the IDE / tools

- Prefer IDE refactorings (`rename`, `move`, `safe_delete`) over text replacement for symbol changes.

## Release process

- Version is set in `gradle.properties` (`pluginVersion`) and must match a `## [x.y]` section in `CHANGELOG.md`.
- Add user-visible changes to `## [Unreleased]` in `CHANGELOG.md`, grouped under `### Added` / `### Fixed` / `### Changed`,
  with issue links such as `[#85](https://github.com/grisha9/easy-maven-plugin/issues/85)`.
- Signing and publishing use environment variables (`CERTIFICATE_CHAIN`, `PRIVATE_KEY`,
  `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`). Never commit secrets.

## Do not

- Do not commit build outputs (`build/`, `.gradle/`, `.intellijPlatform/`, `hs_err_*.log`).
- Do not edit generated or cached files under `build/`.
- Do not add new dependencies without checking they are available in Maven Central or the IntelliJ repositories.
- Do not introduce new usages of `@Internal`, `@Obsolete` or deprecated platform API.
- Do not compile code & run tests

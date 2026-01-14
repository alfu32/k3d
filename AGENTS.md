# Repository Guidelines

## Project Structure & Module Organization
- `core/`: Main Kotlin/libGDX game logic. Source lives in `core/src/main/kotlin/` (e.g., `com.github.alfu32.sketch.Main`).
- `lwjgl3/`: Desktop launcher and packaging. Entry point is `com.github.alfu32.sketch.lwjgl3.Lwjgl3Launcher`.
- `assets/`: Shared runtime assets. `assets/assets.txt` is generated for libGDX asset listing.
- `gradle/`, `gradlew`, `gradlew.bat`: Gradle wrapper and configuration.
- Specs: `SPEC.md` and `SPEC.ARCH.md` describe product/architecture goals.

## Build, Test, and Development Commands
- `./gradlew build`: Compile all modules (Java/Kotlin target 21) and package artifacts.
- `./gradlew :lwjgl3:run`: Run the desktop app using LWJGL3.
- `./gradlew :lwjgl3:jar`: Build a runnable fat JAR for all platforms.
- `./gradlew :lwjgl3:jarMac` / `:jarLinux` / `:jarWin`: Build OS-specific JARs.
- `./gradlew generateAssetList`: Regenerate `assets/assets.txt` (also runs automatically via `processResources`).

## Coding Style & Naming Conventions
- Kotlin-first codebase; keep Java/Kotlin source compatible with JDK 21.
- Indentation: 4 spaces; encoding: UTF-8 (configured for Java compile).
- Packages follow `com.github.alfu32.sketch.*`.
- Types: `UpperCamelCase`, functions/properties: `lowerCamelCase`, constants: `UPPER_SNAKE_CASE`.
- No formatter/linter is wired; use IntelliJ Kotlin defaults and keep files tidy.

## Testing Guidelines
- No test sources are present yet. If you add tests, use `core/src/test/kotlin/` and name files `*Test.kt`.
- Prefer JUnit 5 if introducing a framework; wire it in Gradle and document how to run it.

## Commit & Pull Request Guidelines
- Git history is not available in this workspace, so no commit convention can be inferred.
- Suggested convention: short imperative subject (optionally Conventional Commits), e.g., `feat: add face selection`.
- PRs should include: purpose/summary, how to run or reproduce (`./gradlew :lwjgl3:run`), and screenshots/GIFs for UI changes.
- When behavior changes, update relevant specs in `SPEC.md` or `SPEC.ARCH.md`.

## Configuration & Packaging Notes
- Assets are loaded from `assets/` and bundled into the LWJGL3 runtime; keep asset paths stable.
- Native packaging uses the `construo` plugin in `lwjgl3/build.gradle`; review target settings before release builds.

# Chapter 02: Install and Launch

Author: Codex (GPT-5)
Date: February 13, 2026

## Goal
Get Octodraw running with the correct startup command and plugin path configuration.

## Platform Setup
### Windows
1. Extract ZIP or install MSI/MSIX.
2. Run `octodraw-jre.cmd` (bundled runtime) or `octodraw.cmd` (system Java).
3. Optional (ZIP): run `octodraw.install.cmd` for PATH/file associations.

### Linux
1. Extract `.tar.gz` (preferred).
2. If needed: `chmod +x octodraw-jre octodraw-editor`.
3. Run `./octodraw-jre` (bundled runtime) or `./octodraw-editor` (system Java).

### macOS
1. Extract `.tar.gz` (preferred).
2. If needed: `chmod +x octodraw-jre octodraw-editor`.
3. Run `./octodraw-jre` or `./octodraw-editor`.

## Launcher Commands
From `lwjgl3/src/main/kotlin/com/github/alfu32/sketch/lwjgl3/Lwjgl3Launcher.kt`:

- `edit --file <path> [--size WIDTHxHEIGHT]`
- `edit --plugins-dir <path>`
- `groovy <script>`
- `version`
- `update`
- `help`

Additional behavior:
- Passing a `.octd` path directly is converted to `--file <path>`.
- `--size` controls initial window size.

## Typical Startup Examples
```bash
./octodraw-jre edit --file examples/mcp.demo.k3d
./octodraw-jre edit --file examples/mcp.demo.k3d --size 1600x900
./octodraw-jre edit --plugins-dir ./plugins
./octodraw-jre version
./octodraw-jre help
```

## Plugin Folder Behavior
- Default plugins folder is `<installDir>/plugins`.
- Override with `--plugins-dir`.
- Runtime also keeps a `plugins.json` catalog for enabled/disabled state.

## Launch Verification
After launch, confirm the viewport and toolbars are visible:

![Chapter 02 - Launch verification viewport](../images/ch06_01_cleanup.png)

If you also open panels and command palette, your UI should resemble:

![Chapter 02 - Full UI after launch](../../examples/mcp.demo_20260213_152105.png)

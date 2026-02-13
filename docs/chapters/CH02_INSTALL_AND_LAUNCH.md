# Chapter 02: Install and Launch

Author: Codex (GPT-5)
Date: February 13, 2026

## Goal
Get K3D running with the correct startup command and plugin path configuration.

## Platform Setup
### Windows
1. Extract ZIP or install MSI/MSIX.
2. Run `k3d-jre.cmd` (bundled runtime) or `k3d.cmd` (system Java).
3. Optional (ZIP): run `k3d.install.cmd` for PATH/file associations.

### Linux
1. Extract `.tar.gz` (preferred).
2. If needed: `chmod +x k3d-jre k3d-editor`.
3. Run `./k3d-jre` (bundled runtime) or `./k3d-editor` (system Java).

### macOS
1. Extract `.tar.gz` (preferred).
2. If needed: `chmod +x k3d-jre k3d-editor`.
3. Run `./k3d-jre` or `./k3d-editor`.

## Launcher Commands
From `lwjgl3/src/main/kotlin/com/github/alfu32/sketch/lwjgl3/Lwjgl3Launcher.kt`:

- `edit --file <path> [--size WIDTHxHEIGHT]`
- `edit --plugins-dir <path>`
- `groovy <script>`
- `version`
- `update`
- `help`

Additional behavior:
- Passing a `.k3d` path directly is converted to `--file <path>`.
- `--size` controls initial window size.

## Typical Startup Examples
```bash
./k3d-jre edit --file examples/mcp.demo.k3d
./k3d-jre edit --file examples/mcp.demo.k3d --size 1600x900
./k3d-jre edit --plugins-dir ./plugins
./k3d-jre version
./k3d-jre help
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

# Install and Launch

Use the latest release page for packaged artifacts. Do not rely on old version-pinned URLs when writing docs or scripts.

## Desktop

Windows packages provide launch scripts such as `octodraw-jre.cmd` for a bundled runtime and `octodraw.cmd` for a system Java runtime. Linux and macOS archives provide similar launchers such as `octodraw-jre` or `octodraw-editor`.

Typical desktop launch:

```sh
./octodraw-jre edit --file examples/mcp.demo.k3d --size 1600x900
```

Launcher commands documented in the current source include:

- `edit --file <path> [--size WIDTHxHEIGHT]`
- `edit --plugins-dir <path>`
- `groovy <script>`
- `version`
- `update`
- `help`

## Android

Android builds exist, but the current Android experience is best with external mouse and keyboard input. Treat touch-only workflows as limited unless a current build validates them.

## Build From Source

```sh
./gradlew build
./gradlew :lwjgl3:run
./gradlew :web:prepareWebComponentBundle
```

The webcomponent bundle is produced at `web/build/dist/webcomponent`.

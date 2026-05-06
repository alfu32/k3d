# Screenshot Pipeline

Screenshots and illustrations must come from a running app or webcomponent. Do not manually create fake screenshots.

Generated images belong in:

```text
docs-site/static/images/generated/
```

Every generated image must have a manifest record in:

```text
docs-site/static/images/generated/manifest.json
```

## 1. Start the App Locally

Launch the desktop app with a known file and window size:

```sh
./gradlew :lwjgl3:run
```

For packaged builds, use the launcher:

```sh
./octodraw-jre edit --file examples/mcp.demo.k3d --size 1600x900
```

## 2. Confirm MCP

Use the console alias or HTTP endpoint:

```text
mcp: status
GET http://127.0.0.1:8765/mcp/status
```

## 3. Discover Commands

Fetch the current command catalog:

```sh
curl http://127.0.0.1:8765/scene/listCommands
```

If `/scene/listCommands` returns an empty command array, retry:

```sh
curl http://127.0.0.1:8765/scene/commands
```

Search for command names, IDs, descriptions, or tags containing screenshot, capture, render-buffer, render, or PNG. Do not assume the capture command ID.

## 4. Generate a Deterministic Scene

Use a command such as reset-for-capture only after discovery confirms it exists. Otherwise run a console script that clears the scene, sets the file path, and positions the camera.

## 5. Capture the Render Buffer

Prefer a render-buffer or screenshot command discovered from `/scene/listCommands`. Render-buffer capture is cleaner than operating-system screenshots because it avoids window chrome and desktop scaling.

The current discovered desktop command is `export.screenshot`. It reads the libGDX framebuffer and writes a PNG next to the active model file. `view.capture_ui_minimal` is attempted before capture when present; in the current app it hides floating panels but does not hide every toolbar, so generated images may still show the left toolbar stack until the app exposes a stronger clean-canvas capture mode.

## 6. Fall Back Clearly

If render-buffer capture is unavailable or fails, a normal window or browser screenshot is allowed. Record the fallback source as `window-screenshot` or `browser-screenshot` in the manifest notes.

When the framebuffer PNG omits useful context such as the ground plane, prefer an application-area capture that includes the viewport plus the intended toolbars and panels, but crop out the operating-system title bar. That avoids exposing local file paths or usernames while keeping the documentation visually honest.

## 7. Store Images and Manifest Records

Use stable filenames:

```text
getting-started-rectangle-face.png
push-pull-house-solid.png
snapping-midpoint-example.png
objects-instance-editing.png
lighting-scene-example.png
```

Manifest records should include ID, file path, source, ISO timestamp, app target, commands used, script used, and notes.

## 8. Reference Images From Markdown

Use `ScreenshotFigure.svelte`:

```svelte
<ScreenshotFigure
  src="/images/generated/push-pull-house-solid.png"
  alt="Push/Pull house solid example"
  caption="Generated from local MCP render-buffer capture."
/>
```

## Script Skeleton

Run the local probe:

```sh
cd docs-site
npm run capture:examples -- --base-url http://127.0.0.1:8765
```

This script updates the manifest with MCP probe information and lists likely capture commands. It intentionally does not invent fixed command IDs.

The script uses human-paced waits between MCP operations so the render thread can settle before capture. Defaults are roughly 1000 ms after normal commands and console scripts, 1200 ms after scene reset, and 1600 ms immediately before screenshot capture. If captures look stale or the app becomes unstable, restart the app before rerunning and increase the delay environment variables documented in `docs-site/scripts/README.md`.

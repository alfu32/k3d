# Docs Site Automation Scripts

These scripts support local documentation authoring. They run from `docs-site/` and do not change the Kotlin or Gradle build.

## Commands

- `npm run validate:tutorials` validates every JSON tutorial in `static/tutorials/`.
- `npm run capture:examples` uses local MCP to prepare deterministic scenes, capture real PNG files, and update the generated-image manifest.
- `npm run generate:commands` snapshots `/scene/listCommands` or `/scene/commands` into generated command/tool JSON for reference pages.
- `npm run sync:webcomponent` copies the Gradle webcomponent bundle from `../web/build/dist/webcomponent` into `static/webcomponent` for local docs preview.

CI builds the Gradle webcomponent, syncs it into the docs static folder, runs tutorial validation, `svelte-check`, and the static SvelteKit build. CI does not require a live MCP server because screenshot generation is a local authoring workflow.

## Local Webcomponent Preview

From the repository root, build the browser bundle:

```sh
./gradlew :web:prepareWebComponentBundle
```

Then from `docs-site/`, sync it into the static folder:

```sh
npm run sync:webcomponent
```

The copied bundle is ignored by Git. Keep `static/webcomponent/.gitkeep` tracked so fresh checkouts retain the folder shape.

## MCP Capture Policy

Use MCP command discovery before capture. Prefer render-buffer or screenshot commands discovered from `/scene/listCommands`; if that endpoint returns an empty command array, fall back to `/scene/commands`. Do not assume a fixed command ID. If render-buffer capture is unavailable, use a documented window or browser screenshot fallback and record that fallback in `static/images/generated/manifest.json`.

The capture script is intentionally conservative. It pauses after scene resets, console scripts, command execution, pointer phases, and before capture so the render thread can settle before `export.screenshot` reads the frame buffer. The pacing can be adjusted with:

```sh
MCP_STEP_DELAY_MS=1000 \
MCP_POINTER_PHASE_DELAY_MS=300 \
MCP_RESET_DELAY_MS=1200 \
MCP_CONSOLE_DELAY_MS=1000 \
MCP_PRE_CAPTURE_DELAY_MS=1600 \
npm run capture:examples
```

Defaults are intentionally human paced, roughly 800-1600 ms for state-changing operations. If the app becomes unstable, restart Octodraw and rerun the script with larger delays before generating more captures.

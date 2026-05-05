# Docs Site Automation Scripts

These scripts support local documentation authoring. They run from `docs-site/` and do not change the Kotlin or Gradle build.

## Commands

- `npm run validate:tutorials` validates every JSON tutorial in `static/tutorials/`.
- `npm run capture:examples` probes a local Octodraw MCP server and prepares the generated-image manifest.

CI runs tutorial validation, `svelte-check`, and the static SvelteKit build. CI does not require a live MCP server because screenshot generation is a local authoring workflow.

## MCP Capture Policy

Use MCP command discovery before capture. Prefer render-buffer or screenshot commands discovered from `/scene/listCommands`; if that endpoint returns an empty command array, fall back to `/scene/commands`. Do not assume a fixed command ID. If render-buffer capture is unavailable, use a documented window or browser screenshot fallback and record that fallback in `static/images/generated/manifest.json`.

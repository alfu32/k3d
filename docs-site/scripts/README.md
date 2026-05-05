# Docs Site Automation Scripts

These scripts support local documentation authoring. They run from `docs-site/` and do not change the Kotlin or Gradle build.

## Commands

- `npm run validate:tutorials` validates every JSON tutorial in `static/tutorials/`.
- `npm run capture:examples` probes a local Octodraw MCP server and prepares the generated-image manifest.
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

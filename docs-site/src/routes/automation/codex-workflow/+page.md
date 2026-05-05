# Codex Workflow

Codex work in this repository should be small, inspectable, and verifiable.

## Working Rules

1. Inspect source and existing docs before editing.
2. Preserve `docs/` unless explicitly migrating material into the new docs site.
3. Keep generated command tables and screenshots separate from hand-authored explanation.
4. Avoid broad rewrites when a narrow change solves the task.
5. Run practical verification before reporting completion.
6. Use MCP locally for command discovery, deterministic scene setup, screenshots, and reference validation when it is available.

## Docs Site Boundaries

The Svelte docs application lives under `docs-site/`. Node scripts should stay inside `docs-site/`. Kotlin, Gradle, and libGDX builds stay under the existing Gradle project.

## Verification

Typical docs-site verification:

```sh
cd docs-site
npm run validate:tutorials
npm run check
npm run build
```

Typical webcomponent verification:

```sh
./gradlew :web:prepareWebComponentBundle
```

## MCP Use

Use MCP to discover commands, run console scripts, set known camera views, and generate screenshots. Do not invent command IDs for screenshots or capture; search `/scene/listCommands` for likely render-buffer, screenshot, capture, or PNG commands.

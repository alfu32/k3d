# Repository Guidelines

## Project Summary
- Octodraw is a Kotlin/libGDX direct 3D sketching and modeling project, historically hosted as `alfu32/k3d`.
- Main targets are JVM desktop via LWJGL3, Android, and TeaVM/WebGL web/webcomponent builds.
- Core application code lives in `core/`, desktop launcher and packaging in `lwjgl3/`, Android in `android/`, and web runtime/webcomponent tasks in `web/`.

## Docs Site
- The new GitHub Pages documentation app lives in `docs-site/`.
- Use SvelteKit, Svelte 4, TypeScript, mdsvex, and `@sveltejs/adapter-static`.
- Do not replace the docs site with Vue, VitePress, Nuxt, Docusaurus, React, or another framework.
- The production base path is `/k3d`; use `$app/paths` for base-sensitive app links and static assets.
- Markdown pages must be able to embed Svelte components through mdsvex.

## Existing Docs
- Preserve the existing `docs/` folder unless a task explicitly says to migrate or edit a specific file.
- Treat existing docs as source material, not as automatically current truth.
- When behavior is uncertain, inspect source and docs first; do not invent command IDs or workflows.

## Build and Verification
- Use Gradle for Kotlin, desktop, Android, and webcomponent builds.
- Use Node scripts only inside `docs-site/`.
- Practical checks for docs changes:
  - `cd docs-site && npm run validate:tutorials`
  - `cd docs-site && npm run check`
  - `cd docs-site && npm run build`
- Practical webcomponent check:
  - `./gradlew :web:prepareWebComponentBundle`

## Webcomponent
- The existing Gradle task `:web:prepareWebComponentBundle` writes to `web/build/dist/webcomponent`.
- GitHub Pages deployment copies that bundle into `docs-site/static/webcomponent`.
- Browser tutorials use the embedded webcomponent and must not depend on the desktop MCP server.

## MCP and Automation
- MCP is local automation and validation infrastructure, and also a documentation-authoring tool.
- Use MCP to check status, discover commands, execute deterministic setup commands, run console scripts, move the camera, validate documented commands, and generate screenshots when available.
- Prefer render-buffer or screenshot commands discovered through `/scene/listCommands`; if that returns an empty command list, retry `/scene/commands`. Do not assume screenshot/capture command IDs.
- If window or browser screenshot fallback is used, record it clearly in `docs-site/static/images/generated/manifest.json`.
- CI validates static docs and tutorial JSON; CI must not require a live MCP server.

## Release Links
- Avoid stale hardcoded release artifact links.
- Link to GitHub Releases or the latest release route unless current release metadata is generated.

## Coding Style
- Kotlin source uses 4-space indentation and packages under `com.github.alfu32.sketch.*`.
- Keep docs-site changes scoped and avoid unnecessary Gradle configuration changes.

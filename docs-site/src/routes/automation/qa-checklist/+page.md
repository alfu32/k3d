# QA Checklist

Use this checklist before merging docs-site changes.

## Static Site

- `npm run validate:tutorials`
- `npm run check`
- `npm run build`
- Verify the production base path is `/k3d`.
- Confirm no browser-only code runs during prerender.
- Check that internal links are relative or use `$app/paths`.

## Webcomponent

- `./gradlew :web:prepareWebComponentBundle`
- Confirm output path is `web/build/dist/webcomponent`.
- Copy the bundle into `docs-site/static/webcomponent` for local full preview or rely on the workflow for deployment.
- Verify `OctodrawEmbed.svelte` handles script load failure visibly.

## MCP Authoring

- Confirm `/mcp/status`.
- Run `/scene/listCommands`; if it returns an empty array, retry `/scene/commands`.
- Do not invent capture command IDs.
- Prefer render-buffer capture when a discovered command supports it.
- Record fallback screenshots in the manifest.

## Content

- No empty placeholder pages.
- No stale version-pinned release links.
- Browser tutorials do not require desktop MCP.
- Existing `docs/` remains preserved.

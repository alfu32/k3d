# 003 Integrate Webcomponent Embed

## Goal
Keep `OctodrawEmbed.svelte` aligned with the Gradle webcomponent bundle and the public tutorial contract.

## Files allowed to change
- `docs-site/src/lib/components/OctodrawEmbed.svelte`
- `docs-site/src/lib/tutorial/**`
- `docs-site/src/routes/reference/webcomponent/+page.md`
- `docs-site/static/webcomponent/.gitkeep`

## Files not allowed to change
- `web/build.gradle` unless the existing bundle task changes.
- Generated webcomponent runtime files unless intentionally refreshing local preview artifacts.

## Implementation notes
- Load `octodraw-element.js` from `static/webcomponent/`.
- Use `$app/paths`.
- Keep prerender safe.
- Do not depend on desktop MCP from browser tutorials.

## Acceptance checks
- Missing bundle shows a visible failure message.
- Static build succeeds.
- Deployment workflow copies `web/build/dist/webcomponent`.

## Review notes
Verify the custom element name and available methods against `web/component/README.md`.

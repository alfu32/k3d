# 001 Create Docs Site

## Goal
Maintain the SvelteKit/Svelte 4 documentation application under `docs-site/` and keep it statically buildable for GitHub Pages at `/k3d`.

## Files allowed to change
- `docs-site/**`
- `.github/workflows/docs-site.yml`
- `AGENTS.md`

## Files not allowed to change
- Existing `docs/**` unless a migration task explicitly allows it.
- Gradle build files unless the task explicitly requires build integration.

## Implementation notes
- Use Svelte 4, SvelteKit, TypeScript, mdsvex, and adapter-static.
- Keep browser-only code inside `onMount`.
- Use `$app/paths` for base-path-sensitive assets and app links.

## Acceptance checks
- `cd docs-site && npm run validate:tutorials`
- `cd docs-site && npm run check`
- `cd docs-site && npm run build`

## Review notes
Confirm the generated build contains static HTML under `docs-site/build` and no server routes.

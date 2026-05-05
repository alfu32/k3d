# 008 Docs Quality Pass

## Goal
Perform a focused quality pass over content, links, build output, tutorial JSON, and workflow behavior.

## Files allowed to change
- `docs-site/**`
- `AGENTS.md`
- `.github/workflows/docs-site.yml`

## Files not allowed to change
- Legacy docs or Kotlin source unless fixing a clearly requested docs integration issue.

## Implementation notes
- Check all pages for empty placeholder language.
- Confirm GitHub Pages base path handling.
- Verify no stale version-pinned release links were added.
- Confirm CI does not require a live MCP server.

## Acceptance checks
- `npm run validate:tutorials`
- `npm run check`
- `npm run build`
- `./gradlew :web:prepareWebComponentBundle` when practical.

## Review notes
Summarize known limitations directly, especially webcomponent runtime or MCP availability issues.

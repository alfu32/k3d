# 007 Add Screenshot Pipeline

## Goal
Generate real documentation screenshots through MCP render-buffer capture when available, with documented fallbacks.

## Files allowed to change
- `docs-site/scripts/mcp_capture_examples.mjs`
- `docs-site/static/images/generated/**`
- `docs-site/src/routes/automation/screenshot-pipeline/+page.md`
- Markdown pages that reference generated screenshots.

## Files not allowed to change
- Hand-created fake screenshots.
- OS-specific desktop capture scripts outside `docs-site/scripts/` unless requested.

## Implementation notes
- Start or connect to the running desktop app.
- Check `/mcp/status`.
- Discover capture commands through `/scene/listCommands`.
- Prefer render-buffer/screenshot commands over OS screenshots.
- Record every image in `manifest.json`.

## Acceptance checks
- Manifest records `id`, `file`, `source`, `generatedAt`, `appTarget`, `commandsUsed`, `scriptUsed`, and `notes`.
- Fallback screenshots are explicitly marked.
- No fixed capture command ID is invented.

## Review notes
If MCP is unavailable or returns an empty command catalog, do not generate screenshots.

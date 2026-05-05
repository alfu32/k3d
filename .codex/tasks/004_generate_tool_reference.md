# 004 Generate Tool Reference

## Goal
Refresh command and tool references from source plus live command discovery.

## Files allowed to change
- `docs-site/src/routes/reference/commands/+page.md`
- `docs-site/src/routes/reference/tools/+page.md`
- Generated reference data files under `docs-site/static/` if added later.

## Files not allowed to change
- `core/**` unless the task is explicitly about implementation changes.
- Existing `docs/**`.

## Implementation notes
- Inspect `ToolId.kt` and command registration in `Main.kt`.
- Prefer `/scene/listCommands` for exact runtime IDs.
- Keep generated data separate from explanatory prose.

## Acceptance checks
- Command IDs used as facts are either discovered or clearly marked as examples.
- No stale generated catalog is presented as current.

## Review notes
Record the build, timestamp, and command count for generated command catalogs.

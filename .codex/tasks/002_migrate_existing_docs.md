# 002 Migrate Existing Docs

## Goal
Move useful material from the legacy `docs/` folder into `docs-site/src/routes/` without deleting the source documents.

## Files allowed to change
- `docs-site/src/routes/**`
- `docs-site/static/images/**` only for copied or generated assets with clear provenance.

## Files not allowed to change
- `docs/**` source files.
- Kotlin source or Gradle build files.

## Implementation notes
- Read the relevant existing chapter first.
- Rewrite prose for the new route structure instead of pasting stale tables wholesale.
- Preserve uncertainty and mark runtime-dependent commands as pending discovery.

## Acceptance checks
- No page contains only placeholder text.
- Links are relative or base-path safe.
- `npm run build` succeeds.

## Review notes
Check that migrated content remains accurate for current Octodraw and does not overstate completeness.

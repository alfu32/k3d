# Documentation Generation

Generated documentation should support the hand-authored docs without replacing them.

## What to Generate

- Command catalogs from `/scene/listCommands`.
- Tool reference snapshots from source and runtime discovery.
- MCP contract snapshots from `/mcp/contract` when available.
- Screenshot manifests and image records.
- Tutorial validation reports.

## What to Keep Hand Authored

- Product explanation.
- User workflow guidance.
- Tutorial narrative.
- Compatibility warnings.
- Style and content standards.

## Recommended Layout

Keep generated files in predictable locations and include timestamps, source command, runtime build, and command count. Do not paste generated command dumps into prose pages unless they are curated.

## Refresh Rules

Before publishing generated references, rerun command discovery against the current build and compare old IDs. Removed commands should be documented as removed or omitted, not silently kept.

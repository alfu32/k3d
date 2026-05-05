# 005 Generate Interactive Tutorials

## Goal
Expand tutorial JSON and pages so public browser tutorials become progressively more executable through the webcomponent API.

## Files allowed to change
- `docs-site/static/tutorials/**`
- `docs-site/src/routes/tutorials/**`
- `docs-site/src/lib/components/TutorialRunner.svelte`
- `docs-site/src/lib/tutorial/**`

## Files not allowed to change
- Desktop MCP implementation unless explicitly requested.
- Legacy `docs/tutorial/**` unless migrating selected material.

## Implementation notes
- Keep JSON valid and validated by `npm run validate:tutorials`.
- Mark command actions as `pendingDiscovery` until verified.
- Public tutorials must not require the desktop MCP server.

## Acceptance checks
- Tutorial JSON has unique IDs and non-empty steps.
- Runner handles fetch failure and missing webcomponent bundle.
- Static build succeeds.

## Review notes
Future work should map tutorial actions to the stable `OctodrawTutorialElement` API.

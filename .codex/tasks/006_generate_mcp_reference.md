# 006 Generate MCP Reference

## Goal
Keep MCP documentation aligned with the current local HTTP and MCP JSON-RPC contract.

## Files allowed to change
- `docs-site/src/routes/reference/mcp/+page.md`
- `docs-site/src/routes/automation/mcp-contract/+page.md`
- Generated contract snapshots under `docs-site/static/` if introduced.

## Files not allowed to change
- MCP server source unless implementing an explicit feature.
- Browser tutorial pages except for cross-links.

## Implementation notes
- Inspect `McpHttpServer.kt` and `/mcp/contract`.
- Preserve endpoint details, request shapes, timeout notes, and discovery rules.
- Document MCP as local automation and docs-authoring infrastructure.

## Acceptance checks
- Docs distinguish MCP from the public webcomponent tutorial API.
- Command discovery is emphasized before execution.

## Review notes
If `/mcp/contract` is unavailable, state that clearly and rely on source inspection.

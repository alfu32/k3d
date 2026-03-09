# Chapter 12: Troubleshooting and FAQ

Author: Codex (GPT-5)
Date: February 13, 2026

## Goal
Provide fast diagnosis paths for common user and MCP-automation issues.

## 1) Scene Cleanup Did Not Remove Previous Artifacts
Symptoms:
- old geometry appears in new tutorial captures
- reset script ran but scene still contains prior objects

Actions:
1. Exit all nested groups first (`while (scene.exitGroup()) {}`).
2. Run full reset for root prototype stores and root children.
3. Clear selections (`scene.clearAllSelections()`).
4. Reposition camera in perspective orbit (Y-up) before capture.

Reference capture with stale geometry present:

![Troubleshooting - Residual artifacts example](../../examples/mcp.demo_20260213_145019.png)

## 2) Selection Window / Volume Cube Preview Not Visible
Symptoms:
- drag selection behavior applies,
- but rectangle/cube overlay is not rendered.

Likely cause:
- overlay preview path not exposed or not active in current runtime/capture mode.

Actions:
1. Confirm selection counts in `Selection` panel (`view.selection`).
2. Confirm results by selected entity highlights and counts.
3. Validate runtime version against latest selection-preview implementation.

## 3) Camera Confusion (Height vs Depth)
Symptoms:
- captures look lateral/flat for ground operations
- Z mistakenly treated as up axis

Correct model frame:
- Up = `Y`
- Depth = `Z`

Actions:
1. Force orbit camera (`view.camera.orbit`).
2. Set `camera.up` to `(0,1,0)`.
3. Use perspective captures for user-facing docs.

Good reference perspective:

![Troubleshooting - Correct perspective Y-up view](../images/ch06_01_cleanup.png)

## 4) MCP Status Works, Scene Endpoints Fail
Symptoms:
- `/mcp/status` returns running,
- `/scene/*` calls intermittently fail.

Actions:
1. Retry with short backoff.
2. Ensure local proxy bypass for localhost (`NO_PROXY` / `no_proxy`).
3. Confirm editor instance is still active and bound to MCP server.
4. Recheck with `/scene/listCommands`.

## 5) Groovy MissingProperty Errors (`guideManager`, `toolController`)
Symptoms:
- `MissingPropertyException` in `/scene/console` scripts for non-bound globals.

Cause:
- those properties are not guaranteed console bindings.

Actions:
1. Use `:list` and `:list <binding>` in console to discover valid bindings.
2. Prefer documented bindings (`scene`, `cameraCtl`, `unit`, `save`, `selection`, `status`, etc.).

## 6) Why Permission Prompts May Still Appear
Even with operation intent approval, prompts can still occur when the execution sandbox requires escalated filesystem/network scope not covered by existing prefix rules.

Practical fix:
- approve a stable command prefix rule for repeated operations,
- keep MCP calls on localhost with proxy bypass,
- keep file writes within approved writable roots when possible.

# Webcomponent Contract Specification

The webcomponent is the public browser tutorial runtime. Its contract must remain separate from the desktop MCP HTTP surface.

## Principles

- Tutorials call stable methods on the editor element.
- Tutorials do not inspect arbitrary internal DOM or canvas state.
- The browser API can share concepts with MCP but must not require a local desktop server.
- State checks should be explicit, serializable, and safe for static hosting.

## Current State

The current `web/component/README.md` documents `<octodraw-editor>` with model, selection, camera, tool, pointer, command, playback, and UI methods. The docs site now treats that as the live browser surface and adapts tutorial JSON onto it:

- `tool.builtin.<id>` maps to `selectTool(<ID>)`.
- Camera presets map to `setCamera({ mode, position, target })`.
- Scene loading maps to `setModel()`.
- Selection checks use `getSelection()` when available.
- Browser screenshot capture uses the embedded canvas; release-quality docs screenshots still come from MCP render-buffer capture.
- Groovy scripts, desktop command discovery, command palette panel operations, and filesystem exports remain MCP-only.

## Future Work

Broaden the browser wrapper only when the real webcomponent exposes the needed command. Do not make tutorials depend on internal canvas children, random runtime globals, or desktop MCP endpoints.

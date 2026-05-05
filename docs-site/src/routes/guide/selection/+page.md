# Selection

Selection works with edges, faces, groups, dimensions, text, voxels, hotspots, and objects depending on the active context.

## Basic Selection

- Left click: select the entity under the cursor.
- Shift + click: add to selection.
- Ctrl + click: remove from selection.
- Delete: delete the current selection.

## Advanced Selection

- Double-click group: enter object edit mode.
- Double-click face: select coplanar faces.
- Triple-click face or edge: select connected geometry in the current context.
- Drag left-to-right: window selection for fully contained items.
- Drag right-to-left: crossing selection for intersected items.
- Empty-space corner picking can create a volume selection.

## Automation Note

MCP pointer events support screen coordinates and world coordinates. For deterministic validation, prefer world-coordinate pointer events when the scenario can be expressed in model space.

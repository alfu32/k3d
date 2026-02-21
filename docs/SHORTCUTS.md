# K3D Shortcuts

This is a quick reference for keyboard and mouse shortcuts.

## General
- `Ctrl+Shift+P`: Command palette.
- `Esc`: Cancel/exit active tool, clear selection, exit object edit.
- `Delete`: Delete selection.
- `Backspace`: Clear current numeric input buffer.
- `Enter`: Commit current numeric input buffer.
- `Ctrl+Z`: Undo.
- `Ctrl+Y` or `Ctrl+Shift+Z`: Redo.
- Double-click panel title bar: Collapse/expand panel.

## Camera navigation
- Right mouse drag: Orbit.
- Shift + right mouse drag: Pan.
- Mouse wheel: Zoom.

## Selection
- Left click: Select.
- Shift + click: Add to selection.
- Ctrl + click: Remove from selection.
- Alt + click-drag: Force 2D window selection (skip direct picking).
- Double-click group: Enter object edit mode.
- Double-click face: Select coplanar faces.
- Triple-click face/edge: Select connected geometry.
- Drag left-to-right: Window select (inside only).
- Drag right-to-left: Window select (intersect).
- Click empty space, then click second corner: Volume select.

## Tool-specific keys
- `Line`: `Enter` finishes the current chain, `Esc` exits to Select.
- `Construction Line`: `Enter` clears current segment draft, `Esc` exits to Select.
- `Polyline` / `Double Line`: `Esc` cancel, `Enter` finalize, `Backspace` remove last segment.
- `Hotspot` (action workflow): no default keybinding; use Actions toolbar button or command palette.
- `Wall`: `Enter` finishes current wall chain, `Esc` exits to Select.
- `Slab` / `Stair` / `Add Hole` / `Window Frame` / `Door Frame`: `Enter` resets current draft, `Esc` exits to Select.

## Architecture groups
- Architecture tools auto-create and enter an architecture group when needed.
- `Delete` in architecture groups removes selected hole contours (wall holes), not generated wall mesh faces.

## Objects
- `Ctrl+O`: Create object prototype from selection.
- Double-click prototype in Objects panel: Place instance.

## Guides
- `T`: Add axis guide at current snap point.
- `G`: Add grid guide at current snap point.

## Model cleanup
- `Ctrl+L`: Run cleanup on the model.

## Numeric input
- `Ctrl+N`: Show numeric input popup at cursor.
- `0-9`, `.`, `-`: Direct numeric input while a tool is active.

## Input focus behavior
- Keyboard input stays in focused panel fields while editing text/numbers.
- Viewport keys (`Esc`, `Delete`, tool shortcuts) apply when the pointer is over the canvas.

## Grouping
- `Ctrl+G`: Group (create object prototype) from selection.
- `Ctrl+Shift+G`: Ungroup selection.

## Copy mode (tool-dependent)
- `Ctrl`: Toggle copy mode for Move/Rotate/Scale (and any tool that supports it).

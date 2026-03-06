# Octodraw Shortcuts

This is a quick reference for keyboard and mouse shortcuts.

## General
- `Ctrl+Shift+P`: Command palette.
- `Esc`: Cancel active tool, clear selection, exit object edit.
- `Delete`: Delete selection.
- `Backspace`: Clear current numeric input buffer.
- `Enter`: Commit current numeric input buffer.
- `Ctrl+Z`: Undo.
- `Ctrl+Y` or `Ctrl+Shift+Z`: Redo.

## Camera navigation
- Right mouse drag: Orbit.
- Shift + right mouse drag: Pan.
- Mouse wheel: Zoom.

## Selection
- Left click: Select.
- Shift + click: Add to selection.
- Ctrl + click: Remove from selection.
- Double-click group: Enter object edit mode.
- Double-click face: Select coplanar faces.
- Triple-click face/edge: Select connected geometry.
- Drag left-to-right: Window select (inside only).
- Drag right-to-left: Window select (intersect).
- Click empty space, then click second corner: Volume select.

## Tool cycling shortcuts
- `L`: `Hotspot -> Construction Line -> Line -> Polyline -> Double Line -> Rectangle -> Surface Rect -> Mesh -> Quad -> Circle -> Hotspot`.
- `D`: `Linear Dimension -> Screen Text -> Vectorial Text -> Linear Dimension`.
- `O`: `Line Offset -> Push/Pull (Extrude) -> Extrude-Swipe -> Plane Section -> Line Offset`.
- `M`: `Move -> Rotate -> Scale -> Stretch -> Rotate-Stretch -> Copy Multiple -> Planar Rotate Multiple -> Helicoidal Rotate Multiple -> Move`.
- From `Select`, pressing `L`, `D`, `O`, or `M` starts the corresponding cycle from its first tool.

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

## Grouping
- `Ctrl+G`: Group (create object prototype) from selection.
- `Ctrl+Shift+G`: Ungroup selection.

## Copy mode (tool-dependent)
- `Ctrl`: Toggle copy mode for Move/Rotate/Scale (and any tool that supports it).

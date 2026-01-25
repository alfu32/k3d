# Release Notes 1.4.3

## Implemented
- LWJGL3 window with clear screen, input loop, and camera orbit/pan/zoom controls.
- Scene basics: grid, axes, cursor, and guide visualization.
- Scene2D + VisUI UI: vertical toolbar, status bar, and selection panel with per-type counts.
- Tools: Select, Line (polyline chaining), Rectangle, Circle (24‑gon mesh), Quad, Push/Pull, Move, Rotate, Paint.
- Snapping: endpoints, midpoints, line segments, grid intersections/lines, grid guides, axis guides, and faces; snap distance set to `1e-2f`.
- Guides: multiple grid guides (G) and axis guides (T); guides cleared on Esc in Select mode.
- Selection modes: click toggle, window selection (2D), volume selection (3D cuboid), double‑click coplanar connected faces, triple‑click connected edges/faces.
- Selection visuals: face highlight overlays and wider blue edges.
- Transform previews: Move/Rotate show live preview geometry; Rotate shows axis (blue), reference (red), and current (green) segments.
- Rendering pipeline: default libGDX model batch + directional shadow light, tuned environment lighting, ground plane, grid, and edges.
- Painting: color picker button, current color display, paint selected or clicked faces.
- Cleanup action: model cleanup hook with status feedback.
- Persistence: JSON model save/load with autosave on every change, `--file` CLI, backup `.bak` on load.
- Launchers: updated Linux/Windows scripts to pass `--file`.

## In Progress / Known Gaps
- Quad/Rectangle/Circle normal consistency across all edge cases.
- Push/Pull adjacency and coplanar extrusion constraints may need further validation.
- Hidden edge rendering based on face occlusion is not finalized.
- Selection volume and window selection need final UX polish.
- Model cleanup should avoid removing faces/edges beyond the intended scope.
- Serializer schema versioning and explicit migration strategy.

## Next Steps
- implement scale, eraser, move-copy, rotate-copy
- add groups, objects and object instances,
- specify and implement a plugin runtime
- Add export formats (glTF/OBJ) and optional import.
- Add configurable snapping/grid spacing in UI.
- Add undo/redo and autosave debounce.

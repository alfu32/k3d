# Interface

The main UI has a 3D viewport, top toolbars, right-side panels, status feedback, and a command palette. The viewport is the modeling workspace; panels expose selection, objects, model settings, lighting, and plugin state.

## Main Regions

- Construction, modification, architecture, voxel, actions, and camera tool groups.
- Viewport with ground grid, world axes, selection overlays, guides, and snap feedback.
- Panels for selection, object info, objects, model settings, polyline settings, architecture settings, lighting, and plugin management.
- Status bar text for active tool hints, coordinates, snap context, and messages.

## Command Palette

Open the command palette with `Ctrl+Shift+P`. The palette executes the same registered command IDs exposed to local MCP automation through `/scene/listCommands` and `/scene/command`.

## Focus Rules

Text and numeric fields keep keyboard focus while editing panel values. Viewport shortcuts such as `Esc`, `Delete`, and tool-cycle keys apply when the viewport has interaction focus.

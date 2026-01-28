# K3D User Manual

This manual covers installation, navigation, selection, tools, panels, and common workflows.

![Placeholder: Main UI](docs/img_1.png)
TODO: Replace with a main UI screenshot.

## Installation

### Windows (ZIP)
1. Unzip the distribution archive.
2. Run `k3d-jre.cmd` to use the bundled runtime, or `k3d.cmd` to use a system Java.
3. Optional: run `k3d.install.cmd` to add K3D to PATH and register `.k3d` files.

### Linux (ZIP)
1. Unzip the distribution archive.
2. Make the launcher executable if needed: `chmod +x k3d-jre k3d-editor`.
3. Run `./k3d-jre` for the bundled runtime, or `./k3d-editor` for system Java.

### macOS (ZIP)
1. Unzip the distribution archive.
2. Make the launcher executable if needed: `chmod +x k3d-jre k3d-editor`.
3. Run `./k3d-jre` for the bundled runtime, or `./k3d-editor` for system Java.

### Plugins location
- Default plugin folder: `plugins/` next to the application.
- Override the location with: `--plugins-dir /path/to/plugins`.

## Files and autosave
- Default model file: `sketch3d.k3d` in the current working folder.
- Open a specific file with: `--file /path/to/model.k3d`.
- The model auto-saves when geometry or settings change.

![Placeholder: Open file](screenshots/open-file.png)
TODO: Replace with a file open screenshot.

## UI overview
- Viewport: the 3D drawing surface.
- Left toolbar: built-in tools.
- Right panels: selection info, objects, settings, lighting, plugins.
- Status bar: current tool, cursor status, and messages.
- Command palette: search and run commands.

Panels are collapsible: double-click a panel title bar to toggle its content.

![Placeholder: Panels](screenshots/panels.png)
TODO: Replace with a panels screenshot.

## Navigation (camera)
- Orbit: right mouse drag.
- Pan: Shift + right mouse drag.
- Zoom: mouse wheel (moves the camera along the view direction).
- Camera target: shown as an orange cross in the scene.

## Snapping and guides
- Snap radius is set in **Model Settings**.
- Axis guide: press `T` to add an axis guide at the current snap point.
- Grid guide: press `G` to add a grid guide at the current snap point.

### Numeric input
- While a tool is active, typing a number updates the active measurement.
- `Backspace` clears the current input buffer; `Enter` commits it.
- `Ctrl+N` opens a numeric input popup at the cursor.
- Expressions using `+ - * /` are supported (example: `12.5/2`).
- Leaving the popup cancels the operation.

![Placeholder: Distance input](screenshots/distance-input.png)
TODO: Replace with a distance input screenshot.

## Selection
- Click to select edges, faces, groups, dimensions, and texts.
- Shift-click: add to selection.
- Ctrl-click: remove from selection.
- Double-click a group to enter object edit mode.
- Double-click a face to select coplanar faces.
- Triple-click a face or edge to select connected geometry.

### Window selection
- Drag left-to-right: select items fully inside the window.
- Drag right-to-left (dashed outline): select intersecting items.

### Volume selection
- Click empty space once to set a first corner.
- Click a second point to select everything inside that 3D volume.

## Objects (prototypes and instances)
- Create an object prototype from selection: `Ctrl+O` (or `Ctrl+G`).
- The selection is replaced by a new instance.
- Objects panel lists prototypes and instance counts.
- Double-click a prototype in the list to place a new instance.
- Delete a prototype only when it has no instances.
- Renaming a selected object updates the prototype name.
- Double-click an instance to edit the prototype (changes apply to all instances).

![Placeholder: Objects panel](screenshots/objects-panel.png)
TODO: Replace with an objects panel screenshot.

## Built-in tools

### Select
- Default tool.
- Supports click, window, and volume selection.

### Line
- Click to start a polyline; click to add segments.
- Closing a loop creates a face.
- `Esc` cancels the active polyline.

### Rectangle
- Click to set a corner; click to finish the rectangle.
- Creates edges and a face on the best-fit plane.

### Surface Rectangle
- Click a surface to set plane and origin; click to finish the rectangle.
- Creates edges and a face aligned to the picked surface.

### Quad
- Click four corners to create a quadrilateral face.

### Circle
- Click center, then click radius to create a circular face.

### Linear Dimension
- Click first measure point.
- Click second measure point.
- Click a third point to position the dimension line.
- The displayed value uses the model unit and updates dynamically.

### Text
- Click to place text at the cursor plane.
- Edit content, size, and screen/model mode in the Selection panel.

### Push/Pull
- Click a face to start; click again to set the extrusion distance.

### Move
- Click a reference point, then click a destination.
- Press `Ctrl` to toggle copy mode (if enabled, copies instead of moving).

### Rotate
- Click to set a pivot; click again to set the rotation.
- Press `Ctrl` to toggle copy mode (if enabled, duplicates before rotating).

### Scale
- Click to set a pivot; click again to set scale.
- Press `Ctrl` to toggle copy mode (if enabled, duplicates before scaling).

### Paint
- Click a face to apply the active color.

### Eraser
- Placeholder tool in this build (no erase behavior yet).

### Object
- Places object prototypes into the scene after choosing one in the Objects panel.

![Placeholder: Tools toolbar](screenshots/tools-toolbar.png)
TODO: Replace with a tools toolbar screenshot.

## Panels

### Selection
- Shows counts for selected edges, faces, objects, dimensions, and texts.
- Text fields allow editing text content, size, and screen/model mode.

### Object Info
- Shows the selected object name and edit state.
- Rename objects and toggle "glue to surface".

### Objects
- Lists object prototypes and instance counts.
- Double-click a prototype to place a new instance.
- Delete a prototype when it has zero instances.

### Model Settings
- Unit name and unit size for measurements and exports.
- Snap radius for snapping behavior.

### Lighting
- Adjusts directional light and shadows.

### Plugin Manager
- Add/remove plugin paths, reload plugins, and manage plugin UI.

### Command Palette
- Open with `Ctrl+Shift+P`.
- Search for tools and panel commands.

![Placeholder: Command palette](screenshots/command-palette.png)
TODO: Replace with a command palette screenshot.

## Tips and troubleshooting
- If input feels ignored, click the viewport to return focus.
- If snapping is too aggressive, reduce Snap radius in Model Settings.
- Use the command palette to reveal hidden panels and tools.

For keyboard shortcuts, see `SHORTCUTS.md`.

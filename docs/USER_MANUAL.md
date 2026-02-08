# K3D User Manual

This manual covers installation, navigation, selection, tools, panels, and workflows for K3D.

![Screenshot placeholder: Main UI](images/img_2.png)

## Installation and launch

### Windows (ZIP / MSI / MSIX)

1. ZIP: unzip the distribution archive.
2. MSI/MSIX: install the package from the release artifacts.
3. Run `k3d-jre.cmd` to use the bundled runtime, or `k3d.cmd` to use a system Java.
4. Optional (ZIP only): run `k3d.install.cmd` to add K3D to PATH and register `.k3d` files.

### Linux (TAR.GZ recommended)

1. Extract the `.tar.gz` distribution archive (preferred, preserves executable flags).
2. If you used a ZIP, make the launcher executable: `chmod +x k3d-jre k3d-editor`.
3. Run `./k3d-jre` for the bundled runtime, or `./k3d-editor` for system Java.

### macOS (TAR.GZ recommended)

1. Extract the `.tar.gz` distribution archive (preferred, preserves executable flags).
2. If you used a ZIP, make the launcher executable: `chmod +x k3d-jre k3d-editor`.
3. Run `./k3d-jre` for the bundled runtime, or `./k3d-editor` for system Java.

### Launch commands

The desktop launcher supports commands and flags:

```
edit --file <path> [--size WIDTHxHEIGHT]   Open or create a model file
edit --plugins-dir <path>                 Override plugins folder
groovy <script>                           Run a Groovy script file
version                                  Show version information
update                                   Download and replace the editor jar
help                                     Show help
```

You can also pass a `.k3d` path directly (the launcher converts it into `--file`).

### Plugins location

- Default plugin folder: `plugins/` next to the application.
- Override the location with: `--plugins-dir /path/to/plugins`.

## Files and autosave

- Default model file: `sketch3d.k3d` in the current working folder.
- Open a specific file with: `--file /path/to/model.k3d`.
- K3D autosaves whenever geometry or settings change.
- Model files store: geometry, objects, camera, lighting, shadow settings, units, snap radius, and grid spacing.

## UI overview

- Viewport: the 3D drawing surface.
- Left toolbar: tools and actions.
- Right panels: selection, object info, objects list, model settings, lighting, plugins.
- Status bar: current tool, status message, snap info, cursor, and numeric input.
- Command palette: search and run tools and commands (`Ctrl+Shift+P`).
- Undo/Redo: `Ctrl+Z` / `Ctrl+Y` (or `Ctrl+Shift+Z`).

Panels are collapsible: double-click a panel title bar to toggle its content. Collapse/expand keeps the title bar anchored in place.
Keyboard focus is panel-aware: typing in focused fields stays in UI controls, while viewport shortcuts apply when the cursor is over the canvas.

![Screenshot placeholder: Panels](images/img_3.png)

## Navigation (camera)

- Orbit: right mouse drag.
- Pan: Shift + right mouse drag.
- Zoom: mouse wheel (moves the camera along the view direction).
- Camera target: shown as an orange cross in the scene.

## Selection

- Click to select edges, faces, objects, dimensions, and texts.
- Shift-click: add to selection.
- Ctrl-click: remove from selection.
- Double-click an object: enter object edit mode.
- Double-click a face: select coplanar faces.
- Triple-click a face or edge: select connected geometry.

### Window selection

- Drag left-to-right: select items fully inside the window.
- Drag right-to-left (dashed outline): select intersecting items.
- Hold `Alt` while starting selection to force 2D window selection and skip direct picking.

### Volume selection

- Click empty space once to set a first corner.
- Click a second point to select everything inside that 3D volume.

![Screenshot placeholder: Selection modes](images/img_7.png)

## Snapping and guides

K3D snaps to multiple inference targets for precision:

- Grid intersections and grid lines.
- Line endpoints and midpoints.
- Points along line segments.
- Faces and ground plane intersections.
- Grid guides and axis guides.

Guides help establish temporary reference planes or axes:

- `G`: create a grid guide at the current snap point.
- `T`: create an axis guide at the current snap point.
- `Esc` (Select tool): clear all guides.

Snap radius and grid spacing are configurable in **Model Settings**.

![Screenshot placeholder: Guides + snapping](images/img_8.png)

## Numeric input

- While a tool is active, typing a number updates the current measurement.
- `Backspace` clears the input buffer; `Enter` commits it.
- `Ctrl+N` opens a numeric input popup at the cursor.
- Expressions using `+ - * /` are supported (example: `12.5/2`).

![Screenshot placeholder: Numeric input](images/img_9.png)

## Objects (groups and prototypes)

Objects let you reuse geometry and isolate edits.

- Create an object prototype from selection: `Ctrl+G` or `Ctrl+O`.
- The selection is replaced by a new instance.
- Objects panel lists prototypes and instance counts.
- Double-click a prototype in the list to place a new instance.
- Delete a prototype only when it has no instances.
- Renaming a selected object updates the prototype name.
- Double-click an instance to edit the prototype (changes apply to all instances).
- **Glue to surface** keeps objects aligned to a picked surface during moves.

![Screenshot placeholder: Objects panel](images/img_10.png)

## Tools

### Select

- Default tool for selection and multi-select.
- Input: left-click for single select; drag for window/crossing selection; click empty space twice for volume selection.
- Modifiers: Shift adds, Ctrl removes, Alt forces 2D window select; Esc clears guides and selection.

### Line

- Click to start a line chain; each click adds another segment.
- Line tool creates edges only and does not auto-create faces.
- `Enter` finishes the current chain and keeps the tool active.
- `Esc` exits to Select.
- Input: left-click to place each segment endpoint.
- Modifiers: numeric input allowed for segment length.

### Construction Line

- Draws one segment per two clicks and does not auto-chain to the next segment.
- Tool stays active after each segment so you can place independent construction lines quickly.
- `Enter` clears an in-progress segment.
- `Esc` exits to Select.
- Input: left-click start, left-click end.

### Rectangle

- Click to set a corner; click to finish the rectangle.
- Creates edges and a face on the best-fit plane.
- Input: left-click two corners.
- Modifiers: numeric input allowed for side length.

### Surface Rectangle

- Click a surface to set plane and origin; click to finish the rectangle.
- Creates edges and a face aligned to the picked surface.
- Input: left-click on a face, then left-click to set size.

### Quad

- Click four corners to create a quadrilateral face.
- Input: four left-clicks to define corners.

### Circle

- Click center, then click radius to create a circular face.
- Input: left-click center, left-click radius.

### Linear Dimension

- Click first measure point.
- Click second measure point.
- Click a third point to position the dimension line.
- The displayed value uses the current model unit.
- Input: three left-clicks (start, end, placement).
- Modifiers: numeric input can override the measured distance.

### Text

- Click to place text at the cursor plane.
- Edit content, size, and screen/model mode in the Selection panel.
- Input: left-click to place.
- Modifiers: edit text, size, and screen/model toggle in Selection panel.

### Push/Pull

- Click a face to start; click again to set the extrusion distance.
- Input: left-click face, left-click to confirm distance.
- Modifiers: numeric input overrides distance.

### Move

- Click a reference point, then click a destination.
- `Ctrl` toggles copy mode (if enabled, copies instead of moving).
- Input: left-click start, left-click end.
- Modifiers: Ctrl toggles copy mode; numeric input overrides distance.

### Rotate

- Click to set a pivot; click again to set the rotation.
- `Ctrl` toggles copy mode (if enabled, duplicates before rotating).
- Input: left-click pivot, left-click to set angle.
- Modifiers: Ctrl toggles copy mode; numeric input overrides angle.

### Scale

- Click to set a pivot; click again to set the scale.
- `Ctrl` toggles copy mode (if enabled, duplicates before scaling).
- If the reference direction is axis-aligned (`X`, `Y`, or `Z`), scaling is constrained to that single axis (squash/stretch).
- Otherwise scaling uses the existing plane/uniform constrained behavior.
- Input: left-click pivot, left-click to set scale.
- Modifiers: Ctrl toggles copy mode; numeric input overrides factor.

### Stretch

- Move only the selected vertices or faces while preserving unselected geometry.
- Input: left-click reference, left-click destination.
- Modifiers: numeric input overrides distance.

### Paint

- Click a face to apply the active color.
- Use the Color action button to pick the active color.
- Input: left-click face to paint.

### Object

- Places object prototypes into the scene after choosing one in the Objects panel.
- Input: left-click to place the selected prototype instance.

![Screenshot placeholder: Tools toolbar](images/img_11.png)

## Actions and panels

### Actions (toolbar)

- **Cleanup**: split intersections and re-weld edges.
- **Delete**: delete current selection.
- **Flip Faces**: reverse the orientation of selected faces.
- **Color**: open the paint color picker.
- **Lighting**: toggle the Lighting panel.
- **Plugin Manager**: open the Plugin Manager panel.
Shortcut notes: `Ctrl+L` runs Cleanup; `Delete` removes selection; flip and color are toolbar-only.

### Selection panel

- Counts for edges, faces, objects, dimensions, and texts.
- Text fields to edit text content and size.
- Toggle for screen-aligned vs model-aligned text.
Input: click fields to edit; checkbox toggles screen-aligned text.

### Object Info panel

- Shows selected object name and edit state.
- Rename objects and toggle **Glue to surface**.
Input: click name field to edit; checkbox toggles glue.

### Objects panel

- Lists object prototypes and instance counts.
- Double-click a prototype to place an instance.
- Delete a prototype only when it has no instances.
Input: double-click to place; Delete Prototype button removes selection (when allowed).

### Model Settings panel

- Unit name and unit size for measurements.
- Grid size (spacing).
- Snap radius (snap tolerance).
Input: type values in fields; drag Snap radius slider.

### Lighting panel

- Directional, ambient, specular, and shadow controls.
- Shadow bias, normal bias, PCF mode, dithering, and cascades toggle.
Input: drag sliders and toggles for live updates.

### Plugin Manager panel

- Add plugin URLs or local paths.
- Download, enable/disable, and reload plugins.
- View load errors in the panel log.
Input: type a URL/path, click Add; use Download/Reload buttons and enable checkboxes.

![Screenshot placeholder: Lighting + plugin panels](images/img_12.png)

## Command palette

Open with `Ctrl+Shift+P` and search for tools, panels, and view commands. The palette also exposes plugin commands when available.
Input: type to filter, Enter to run the highlighted command.

## Built-in console (dev)

K3D includes a persistent Groovy console for power users. Start it using the launcher command:

```
edit --file path/to/model.k3d
```

In the console:

- Type `:help` for meta commands.
- Type `:examples` for snippets.
- Use `app.run { ... }` to mutate the model safely.
Input: Enter submits when syntax is complete; Up/Down navigates history if the buffer is empty; Ctrl+Up/Down always navigates history.

Common meta commands:

- `:help` / `:examples`
- `:exit` to quit the app
- `:history` to list prior commands
- `:perf` to show memory, disk, threads, and CPU stats
- `:objects` to list top-level objects
- `:list` to inspect bound variables
- `:line`, `:poly`, `:circle` to draw geometry

Useful bindings:

- `app`, `scene`, `selection`, `console`
- `pluginHost`, `lighting`, `shadow`, `lightingCtl`
- `camera`, `cameraTarget`, `cameraCtl`
- `status`, `unit`, `save`, `version`

![Screenshot placeholder: Console](images/img_13.png)

## Plugins

K3D loads Groovy plugins from the plugins folder at startup and via the Plugin Manager.

- Use the Plugin Manager to add, download, enable/disable, and reload plugins.
- Plugin tools appear in the toolbar and command palette.
- For development details, see [`PLUGIN_DEVELOPMENT.md`](PLUGIN_DEVELOPMENT.md).

## Tips and troubleshooting

- If a panel field is focused, move the cursor over the viewport to apply canvas shortcuts (`Esc`, `Delete`, tool keys).
- If snapping is too aggressive, reduce **Snap radius** in Model Settings.
- If guides clutter the view, press `Esc` in Select mode to clear them.
- If a plugin fails to load, open Plugin Manager and read the error log.

For keyboard shortcuts, see [`SHORTCUTS.md`](SHORTCUTS.md).

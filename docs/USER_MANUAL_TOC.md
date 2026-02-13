# K3D User Manual and Tutorial TOC (Draft)

This TOC is based on a code and MCP scan done on February 13, 2026.

## 1. Introduction
1.1 Purpose and audience
1.2 What is covered (modeling, architecture, voxel, plugins, MCP-assisted workflows)
1.3 Version and compatibility notes

## 2. Install and Launch
2.1 Windows setup (ZIP / MSI / MSIX)
2.2 Linux setup
2.3 macOS setup
2.4 Launch commands (`edit`, `groovy`, `version`, `update`, `help`)
2.5 Plugin directory and overrides

## 3. Files, Save, and Recovery
3.1 Model file format (`.k3d`)
3.2 Autosave behavior
3.3 Save-as and backup files (`.bak`)
3.4 Export targets and when to use each

## 4. UI Overview
4.1 Viewport and status bar
4.2 Floating toolbars
4.3 Right-side panels
4.4 Command palette
4.5 Built-in console
4.6 Focus rules (viewport vs text fields)

## 5. Interaction Fundamentals (Mouse + Keyboard)
5.1 Pointer buttons and modifiers
5.2 Camera interaction by mode (Orbit, Walkthrough, Orthographic)
5.3 Selection interactions (single, additive, subtractive)
5.4 Window selection (inside vs crossing)
5.5 Volume selection
5.6 Numeric input and expression entry
5.7 Guide placement shortcuts

## 6. Toolbar and Button Reference
6.1 Construction toolbar buttons
6.2 Modification toolbar buttons
6.3 Architecture toolbar buttons
6.4 Voxel toolbar buttons
6.5 Actions toolbar buttons
6.6 Camera toolbar buttons
6.7 Panel title-bar interactions (collapse/expand)

## 7. Tools Reference
7.1 Construction tools
7.2 Modification tools
7.3 Architecture tools
7.4 Voxel tools
7.5 Object placement tools
7.6 Tool-specific key interactions (`Enter`, `Esc`, `Backspace`, etc.)

## 8. Panels Reference
8.1 Selection
8.2 Object Info
8.3 Objects
8.4 Model Settings
8.5 Polyline Settings
8.6 Architecture Settings
8.7 Lighting
8.8 Plugin Manager

## 9. Commands Reference
9.1 How to use the command palette efficiently
9.2 Command naming conventions (`tool.*`, `view.*`, `edit.*`, `export.*`)
9.3 Built-in command catalog by category
9.4 MCP usage of the same commands (`/scene/listCommands`, `/scene/command`)

## 10. Tutorials (Step-by-Step)
10.1 Quickstart: rectangle to extrusion
10.2 Selection and editing drill
10.3 Architecture enclosure workflow
10.4 Openings (windows/doors) and frames workflow
10.5 Voxel blockout to mesh workflow
10.6 Export and screenshot workflow

## 11. Plugin Workflow
11.1 Installing and enabling plugins
11.2 Plugin toolbars and panels
11.3 Plugin commands in palette
11.4 Troubleshooting plugin load errors

## 12. Troubleshooting and FAQ
12.1 Input focus and shortcut conflicts
12.2 Snap or guide confusion
12.3 Selection misses and picking order
12.4 Camera mode confusion
12.5 Performance and large models
12.6 Common export issues

## 13. Appendices
13.1 Full keyboard shortcut table
13.2 Full mouse interaction table
13.3 Full command catalog snapshot
13.4 Screenshot index for tutorial steps

---

## Scan Snapshot (for documentation scope)

### A) MCP command inventory
- Total commands discovered: `61`
- Categories: `Tools (31)`, `View (19)`, `Edit (6)`, `Export (5)`
- Source: live MCP response from `http://127.0.0.1:8765/scene/listCommands`

### B) Built-in toolbar/button inventory
- Construction: `Line`, `Construction Line`, `Polyline`, `Double Line`, `Rectangle`, `Surface Rect`, `Quad`, `Circle`, `Linear Dimension`, `Text`, `Face Outline`, `Line Offset`, `Cutout`, `Plane Section`, `Mesh Intersection`
- Modification: `Select`, `Push/Pull`, `Move`, `Rotate`, `Scale`, `Stretch`, `Paint`
- Architecture: `Wall`, `Slab`, `Stair`, `Add Hole`, `Window Frame`, `Door Frame`
- Voxel: `Voxel`, `Voxel Volume`, `Voxel Frame`, plus action button `Voxelize Faces`
- Actions: `Cleanup`, `Color`, `Delete`, `Flip Faces`, `Lighting`, `Plugin Manager`
- Camera: `Orbit`, `Walk`, `Ortho`

### C) Core keyboard/mouse interactions to document
- Camera orbit mode: `Right drag` orbit, `Shift + Right drag` pan, `Wheel` zoom
- Camera walkthrough mode: `Right drag` look, `W/A/S/D` + arrows move, `Up/Down` height adjust, `Space` jump
- Camera orthographic mode: `Right drag` orbit, `Middle drag` (or `Shift` modifier) pan, `Wheel` zoom
- Selection: `Left click`, `Shift+click` add, `Ctrl+click` remove, `double-click`/`triple-click` behavior
- Window select: left-to-right inside; right-to-left crossing
- Volume select: click empty space for first corner, click second corner to confirm
- Global keys: `Ctrl+Shift+P`, `Esc`, `Delete`, `Ctrl+Z`, `Ctrl+Y`, `Ctrl+Shift+Z`, `Ctrl+G`, `Ctrl+Shift+G`, `Ctrl+O`, `Ctrl+L`, `Ctrl+N`, numeric typing + `Enter` / `Backspace`
- Guide keys: `T` axis guide, `G` grid guide

### D) Important documentation note
- UI toolbar buttons are icon-first in the app; labels appear via hover popover. The manual should always pair screenshots with a text mapping table.

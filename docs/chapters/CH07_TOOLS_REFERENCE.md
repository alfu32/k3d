# Chapter 07: Tools Reference

Author: Codex (GPT-5)
Date: February 13, 2026

## Goal
Provide a practical reference for high-use tools and expected interaction patterns.

## Tool Activation
Tools can be activated by:
- toolbar button click,
- command palette,
- MCP command (`tool.builtin.<id>`).

## Construction Tools
- `line`
- `construction_line`
- `polyline`
- `double_line`
- `rectangle`
- `surface_rectangle`
- `quad`
- `circle`
- `linear_dimension`
- `text`
- `face_outline`
- `line_offset`
- `cutout`
- `plane_section`
- `mesh_intersection`

Example (`tool.builtin.line`):

![Chapter 07 - Line tool](../images/ch06_02_line_tool.png)

Example (`tool.builtin.rectangle`):

![Chapter 07 - Rectangle tool](../images/ch06_03_rectangle_tool.png)

## Modification Tools
- `select`
- `push_pull`
- `move`
- `rotate`
- `scale`
- `stretch`
- `stretch_scale`
- `paint`
- `random_offset`
- `random_surface_array`
- `mesh_regularize`

## Volume Tools
- `object_cut`
- `solid_union`
- `solid_intersection`
- `solid_subtraction`

Volume tools operate on two selected mesh object instances. `object_cut` exposes the shared intersection-cut preparation step and keeps all fragments. `solid_union`, `solid_intersection`, and `solid_subtraction` run the same cut preparation, then classify and keep the requested fragments. Results are written back as loose selected geometry rather than as a new object.

## Fuzzy Tools
- `random_offset`: displaces selected connected vertices along averaged neighboring face normals.
- `random_surface_array`: scatters selected payload geometry or objects over selected target faces with count, normal alignment, scale fuzz, and rotation fuzz controls.
- `mesh_regularize`: replaces a near-planar selected face patch with a regular rectangular triangle grid.

Example (`tool.builtin.push_pull`) outcome:

![Chapter 07 - Push/Pull result](../../examples/mcp.demo_20260213_154809.png)

## Architecture Tools
- `arch_wall`
- `arch_slab`
- `arch_stair`
- `arch_add_hole`
- `arch_window_frame`
- `arch_door_frame`

Notes:
- Architecture tools auto-create/enter an architecture group when needed.
- Wall/slab/stair/frame dimensions come from `Architecture Settings`.

## Voxel Tools
- `voxel`
- `voxel_volume`
- `voxel_frame`

Example (`tool.builtin.voxel`):

![Chapter 07 - Voxel tool](../images/ch06_04_voxel_tool.png)

## Object Placement
- Object creation/grouping: `edit.group` (also `Ctrl+G`)
- Ungroup: `edit.ungroup`
- Place object instances with object tools/panel workflows

Example grouped object/prototype state:

![Chapter 07 - Object workflow](../../examples/mcp.demo_20260213_145026.png)

## Hotspot-Driven Object Behavior

- Add hotspot placement via the **Hotspot** action, then click in viewport to place.
- Hotspots are configured in the **Hotspot Settings** panel.
- Available operation classes:
  - transform operators (`MOVE`, `STRETCH`, `SCALE`, `ROTATE`),
  - multiply operators (`MULTIPLY_LINEAR`, `MULTIPLY_VOLUMETRIC`, `MULTIPLY_ROTATE_2D`, `MULTIPLY_ROTATE_3D`).
- Hotspots can bind to selected geometry and to other hotspots for cascading behavior.
- Runtime expectation:
  - edit mode changes prototype definitions,
  - instance mode changes per-instance hotspot state and recomputes runtime geometry.

## Key Interactions (Tool-Specific)
From `docs/SHORTCUTS.md`:
- `Enter`: commit/finalize/reset draft depending on active tool.
- `Esc`: cancel and return to Select (most tools).
- `Backspace`: remove last segment in polyline-like tools or clear numeric input.
- Numeric typing (`0-9`, `.`, `-`): live numeric input for active tool.

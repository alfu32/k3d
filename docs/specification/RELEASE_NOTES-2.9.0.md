# Release Notes 2.9.0

## Changes since 2.8.0

### Features
- Added internal Architecture toolkit with parametric elements:
  - `Wall`, `Slab`, `Stair`, `Add Hole`, `Window Frame`, `Door Frame`.
- Added dedicated `Architecture` toolbar and `Architecture Settings` panel for defaults and per-element parameter editing.
- Internalized `Polyline` and `Double Line` tools (migrated from external Groovy plugin to Kotlin).
- Added internal `Polyline Settings` panel (command palette: `View> Polyline Settings`) with:
  - arc resolution by max segment length,
  - double-line size,
  - double-line offset.
- Added `Face Outline` tool (build outlines from selected faces).
- Added `Line Offset` tool (offset selected line chains and build connecting faces).
- Added `Cut Out 3` cutting pipeline and exposed it as `Cutout` button.
- Added `Construction Line` tool (single segment placement, tool stays active).
- Added `Export> SVG (View)` command (camera-view vector export path).
- Added `Edit> Cut Rect Hole` command.

- Added voxel modeling core:
  - new voxel-group object kind with internal voxel store,
  - voxel group explode support (`Ctrl+Shift+G`) to static faces/lines,
  - per-voxel selection and operations in voxel mode.
- Added voxel tools:
  - `Voxel` (single placement),
  - `Voxel Volume` (fill box by 2 corners),
  - `Voxel Frame` (box edges only by 2 corners).
- Added `Edit> New Voxel Group` command.
- Added `Edit> Voxelize Faces` command and `Voxelize Faces` toolbar action.
- Added auto-create behavior: voxel tools create and enter a voxel group if none is active.

### Improvements
- Architecture editing workflow improvements:
  - architecture parameters are editable from selected architecture element/group context (no forced in-group editing for parameter changes),
  - wall/slab/stair/frame colors and defaults exposed in panel controls.
- Wall generation improvements:
  - improved wall-to-wall joining for slanted walls in full 3D (not only at ground projection),
  - improved clipping behavior at joins and better continuity of upper edges.
- Stair generator overhaul:
  - stair creation from contour polyline + open tread polyline,
  - per-step orientation follows segmented tread path,
  - automatic collapse to triangular step section when side lines intersect before contour hit,
  - support ribbon/backface generation refined for first/last step behavior,
  - optional left/right stair rail grids with panel toggles.
- Frame generation improvements:
  - window/door frame construction aligned to picked construction plane (not only ground plane).
- Refactored built-in tool UI into floating grouped toolbars:
  - `Construction`, `Modification`, `Voxel`, `Actions`.
- Toolbar UX polish:
  - icon-only buttons,
  - delayed hover popover labels,
  - active-tool marker dot overlay,
  - horizontal button layout,
  - top-left initial placement,
  - wrapping to next row on resize,
  - persisted toolbar positions.
- `Voxelize Faces` action moved to the `Voxel` toolbar and mapped to the dedicated `voxelize` icon from `assets/icons.mapping.csv`.
- Improved measurement overlay for line-like tools:
  - unified dimensioned line rendering,
  - length + relative coordinates display,
  - improved orientation/flip behavior for reversed screen angles.
- Improved explode performance path for voxel groups:
  - reduced transfer overhead,
  - status feedback shown before heavy explode operation.
- Selection workflow improvements:
  - `Alt` forces 2D window selection and skips direct picking.
- Group workflow improvements:
  - grouping replaces current selection with new group instance,
  - ungroup replaces selection with resulting exploded entities.
- Scale tool improvements:
  - preserves axis/plane-constrained behavior (`X`, `Y`, `Z`, `XOY`, `XOZ`, `YOZ`),
  - signed component scaling enabled (mirror-capable),
  - zero-crossing guard (`|scale| >= 0.1`) to prevent unstable blowups.
- Line workflow updates:
  - `Line` now creates edges only (no auto face fill),
  - `Enter` finalizes line chain, `Esc` exits to Select.
- Polyline/double-line meshing improvements:
  - contiguous contour behavior,
  - ribbon-style meshing for paired double-line rails.

### Fixes
- Fixed stair support/side face orientation issues (winding/normal corrections).
- Fixed last-step stair support closure to avoid open support skirt.
- Fixed stair rail placement to use offsets above step top edges.
- Fixed wall hole orientation on joined walls to stay face-perpendicular instead of drifting with join bisector.
- Fixed multiple polyline/double-line commit/finalization issues where geometry was not reliably added to model.
- Fixed missing final closing segment behavior in polyline/double-line closure cases.
- Fixed double-line runtime crashes around finalize/close path.
- Fixed plugin/runtime integration issues from legacy polyline Groovy script and removed legacy plugin source.
- Fixed plugin panel collapse behavior (title bar anchor no longer "drops downward" on collapse).
- Fixed UI focus/input routing:
  - typing in panel text/number fields no longer leaks keystrokes to canvas tools.
- Fixed canvas key regression:
  - `Esc` (clear/cancel) and `Delete` (delete selection) restored reliably.
- Fixed `Ctrl+N` numeric popup robustness:
  - reliable reopening,
  - stable lifecycle,
  - positioned near cursor and clamped to viewport.
- Fixed voxel cube winding/backface orientation (normals now oriented correctly).
- Fixed toolbar sizing/alignment startup issues (snug size + proper initial layout).
- Fixed icon mismatch for voxel actions by using mapped voxel icon set.

### Architecture Tools Changelog (Detailed)
- `Wall`:
  - chained wall construction and in-place rebuild on parameter edits,
  - improved slanted wall join clipping in 3D,
  - improved consistency of top-edge intersections at joins.
- `Add Hole`:
  - restricted to compatible wall architecture groups,
  - selected-wall targeting when available, nearest-wall fallback otherwise,
  - improved hole-side orientation on joined/slanted wall contexts.
- `Stair`:
  - switched to contour+thread polyline definition (closed contour + open thread),
  - step orientation derived from segmented thread path,
  - triangular-step fallback when side intersection occurs before contour,
  - support ribbon and backface logic refined (first/last-step handling),
  - optional left/right rail grids controlled from Architecture Settings,
  - rail lines generated at +1/+2/+3/+4 units above each step top edge.
- `Window Frame` / `Door Frame`:
  - frame generation aligned to picked plane,
  - depth/width/color controlled from architecture settings.
- `Architecture Settings` panel:
  - defaults persist for new architecture construction,
  - selected element parameters shown and editable directly from panel context.

### Removed / Changed Behavior
- Removed legacy `scripts/polyline.groovy` plugin path from active workflow.
- Removed unused `Eraser` placeholder tool.
- Kept `Cut Holes` and `Cut Holes 2` as command-driven (not toolbar buttons).

### Documentation Updates
- Updated `docs/USER_MANUAL.md` with detailed usage for:
  - architecture tools brief and full tool-by-tool chapter,
  - wall/slab/stair/hole/frame workflows and architecture panel editing model,
  - stair rail-grid options and contour+thread stair definition,
  - floating toolbars and interaction model,
  - voxel selection semantics and workflows,
  - voxel tools (`Voxel`, `Voxel Volume`, `Voxel Frame`),
  - `Voxelize Faces`,
  - explode voxel-group workflow.
- Updated shortcuts and behavior references for focus-aware input and current tool key flows.
- Release notes reorganized by `Features`, `Improvements`, and `Fixes`, ordered by impact.

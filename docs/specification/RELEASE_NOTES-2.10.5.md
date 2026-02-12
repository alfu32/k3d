# Release Notes 2.10.5

## Scope

These notes cover changes from `2.9.2` to `2.10.5` (inclusive of all `2.10.x` releases).

- Start tag: `2.9.2` (`6f763d8`)
- End tag: `2.10.5` (`036d304`)
- Commit span: 15 commits (`2.9.2..2.10.5`)

## Highlights

- Introduced an internal Architecture plugin stack:
  - parametric `Wall`, `Slab`, `Stair`, `Add Hole`, `Window Frame`, `Door Frame`,
  - architecture data model/store and persistence,
  - architecture toolbar and settings panel integration.
- Added IFC export support (`Export> IFC (Model)`) with architecture/mesh/line serialization.
- Upgraded snapping and selection for architecture workflows:
  - snapping to visible faces across groups,
  - snapping to triangle-border edges and edge midpoints,
  - architecture-aware picking/selection behavior.
- Implemented architecture editing UX:
  - wall endpoint drag handles,
  - hole markers/handles and drag updates,
  - direct architecture element selection from scene picking.
- Reworked stair generation into contour + thread-line driven construction with progressive refinements and optional rail grids.
- Improved wall join behavior for slanted walls with better 3D clipping/intersection continuity.

## Version-by-Version

### 2.10.0

- Added core architecture feature set:
  - new architecture tool IDs and toolbar entries,
  - architecture settings model,
  - architecture tools and scene/model integration,
  - architecture parameter panel wiring.
- Added architecture persistence wiring and baseline docs updates (`docs/SHORTCUTS.md`, `docs/USER_MANUAL.md`).

### 2.10.1

- Added IFC exporter (`IfcExporter`) and command-palette export action: `Export> IFC (Model)`.
- Expanded architecture integration in scene/model flow and persistence.
- Improved snapping base behavior:
  - face snapping beyond active group,
  - better candidate scoring/prioritization.

### 2.10.2

- Major stair-tool workflow update:
  - stair definition based on picked contour + picked thread polyline,
  - improved polyline/component picking logic,
  - stronger integration with architecture groups.
- Extended architecture selection/edit handling in `SelectTool`.

### 2.10.3

- Wall geometry/join improvements in scene reconstruction:
  - improved handling at segment transitions and joins,
  - better clipping behavior in generated wall mesh.

### 2.10.4

- Additional wall-tool refinements:
  - improved interaction behavior in wall editing flows,
  - further join/selection integration updates.

### 2.10.5

- Stair-tool refinements:
  - improved step/support generation behavior,
  - support for left/right rail options in architecture settings and persistence,
  - icon mapping updates for architecture/stair-related controls.
- Architecture panel/state integration refinements for stair parameters.

## Consolidated Change List

### Features

- New architecture plugin domain (store, tools, UI, persistence):
  - wall/slab/stair/frame creation,
  - wall holes and hole editing handles,
  - architecture-focused selection logic.
- IFC export pipeline for model geometry and architecture content.
- Stair rail options (`left` / `right`) added to architecture settings and stair generation.

### Improvements

- Snapping:
  - face snapping across non-active groups,
  - snap targets for face-edge points and face-edge midpoints.
- Selection and edit UX:
  - architecture element picking by face/edge context,
  - wall endpoint drag editing,
  - hole handle drag editing.
- Geometry generation:
  - wall join clipping quality improvements for slanted configurations,
  - iterative stair meshing/support quality improvements.

### Fixes

- Multiple fixes in stair face/support generation and orientation behavior.
- Multiple fixes in wall join continuity/intersection handling.
- Multiple fixes in architecture selection/update paths to keep model and UI state consistent.

## Files of Interest

Main implementation files in this release range:

- `core/src/main/kotlin/com/github/alfu32/sketch/model/ArchitectureStore.kt`
- `core/src/main/kotlin/com/github/alfu32/sketch/tools/ArchitectureTools.kt`
- `core/src/main/kotlin/com/github/alfu32/sketch/ui/SketchUiOverlay.kt`
- `core/src/main/kotlin/com/github/alfu32/sketch/model/GroupScene.kt`
- `core/src/main/kotlin/com/github/alfu32/sketch/tools/SelectTool.kt`
- `core/src/main/kotlin/com/github/alfu32/sketch/input/Snapper.kt`
- `core/src/main/kotlin/com/github/alfu32/sketch/export/IfcExporter.kt`
- `core/src/main/kotlin/com/github/alfu32/sketch/model/ModelPersistence.kt`


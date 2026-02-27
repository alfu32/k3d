# Release Notes 3.1.0

## Theme

`3.1.0` delivers a major extensibility milestone: **hotspot-driven visual programming for object instances**.

This release turns hotspots into dynamic operators that can procedurally reshape, replicate, and rotate attached geometry per instance, while keeping object prototypes authoritative.

## Major Features

### 1) Hotspot visual-programming operations (new)

Added four new hotspot operations:

- `MULTIPLY_LINEAR`
- `MULTIPLY_VOLUMETRIC`
- `MULTIPLY_ROTATE_2D`
- `MULTIPLY_ROTATE_3D`

Behavior:

- Operations run on attached geometry at instance runtime.
- Generated copies are derived from the prototype baseline plus per-instance hotspot state.
- Runtime generation includes a safety cap to prevent runaway duplication.

Implementation:

- `core/src/main/kotlin/com/github/alfu32/sketch/model/HotspotStore.kt`
- `core/src/main/kotlin/com/github/alfu32/sketch/model/GroupScene.kt`

### 2) Hotspot appearance is now user-defined (new)

Each hotspot now supports:

- shape: `TRIANGLE_UP`, `SQUARE`, `CIRCLE`, `DIAMOND`, `TRIANGLE_DOWN`
- color: user-selectable RGBA color

This is available both for defaults and per-hotspot editing.

Implementation:

- data model: `HotspotStore.kt`
- panel controls + persistence of defaults: `SketchUiOverlay.kt`, `HotspotSettings.kt`
- viewport rendering: `Main.kt`

### 3) Instance-safe object model for dynamic entities (major)

Object prototype/instance behavior was reworked to support stable parametric behavior:

- Prototype edit mode is explicit and authoritative.
- Instances hold per-instance hotspot overrides.
- Instance geometry is fully recomputed from prototype baseline + hotspot state.
- Hotspot bindings now include stable vertex-level attachment IDs.
- Hotspot-to-hotspot dependency chains are resolved per instance.

Implementation:

- `core/src/main/kotlin/com/github/alfu32/sketch/model/GroupScene.kt`
- `core/src/main/kotlin/com/github/alfu32/sketch/model/HotspotStore.kt`
- `core/src/main/kotlin/com/github/alfu32/sketch/model/ModelPersistence.kt`
- render recompute call path: `core/src/main/kotlin/com/github/alfu32/sketch/Main.kt`

## Improvements

- Hotspot markers now reflect resolved runtime hotspot positions (not only raw overrides), so marker location matches deformed instance geometry.
- Clone/delete/reset flows now clean and propagate hotspot runtime/dependency state consistently.
- Hotspot attachment flow now supports hotspot-to-hotspot links in addition to segment/triangle/vertex links.

## Persistence and Compatibility

- Model persistence schema bumped to **version 15**.
- New persisted hotspot fields:
  - `shape`
  - `color`
  - `attachedHotspotIds`
  - `attachedVertexIds` (completed vertex-binding persistence path)
- Prototype vertex identity mapping is persisted to stabilize hotspot-to-geometry behavior across reload.

## Fixes

- Fixed instance drift/regression paths caused by in-place instance geometry mutations.
- Fixed hotspot runtime recompute call path issues in prototype/instance synchronization.
- Fixed hotspot marker mismatches after dependency/cascade updates.

## Why this matters

This release introduces a practical visual-programming layer inside Octodraw:

- users can build reusable parametric/dynamic entities with hotspots,
- prototype topology remains clean and reusable,
- each instance can be independently driven by local hotspot values.

This significantly expands what can be authored without writing custom plugins or scripts.


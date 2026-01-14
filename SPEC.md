# SPEC.md — libGDX Mesh Editor (SketchUp-like) — Kotlin Desktop

## 1. Product goal
A desktop 3D modeling application focused on SketchUp-style direct modeling:
- Draw and edit edges and planar faces.
- Generate solids primarily via extrusion (Push/Pull).
- Precise point-and-click selection with inference-based snapping.
- Fast iteration: immediate visual feedback, simple workflows, strong undo/redo.

Primary target: desktop (LWJGL3) first.

---

## 2. Core concepts and terminology

### Entities
- **Vertex**: 3D point.
- **Edge**: line segment between two vertices.
- **Face**: planar polygon bounded by a closed edge loop (supports holes).
- **Mesh**: triangulated representation for rendering; derived from faces.
- **Group**: container creating an isolated editing context (prevents “sticky” geometry).
- **Component**: instanced reusable group definition (multiple instances share geometry definition).
- **Tag (Layer)**: visibility/organization label applied to groups/components, not raw edges/faces.
- **Material**: per-face front/back surface material; supports UV mapping.
- **Scene**: saved camera position + visibility states (optional v1).

### Editing contexts
- **Model context**: default top-level.
- **Group/Component edit context**: geometry edits affect only that container; outside geometry is non-sticky.

---

## 3. MVP feature set (baseline from your requirements)

### 3.1 Viewport interaction
- Mouse ray-cast for picking:
  - edges, faces, vertices.
- A **virtual ground plane** exists only as a fallback intersection target when no model hit occurs.
  - Ground plane never cuts geometry and cannot be selected.
- Camera controls:
  - Orbit, Pan, Zoom.
  - Zoom-to-fit selection.
  - Optional: “Walk” mode (later).

### 3.2 Drawing and creation
- **Line tool**:
  - Click-drag or click-click to create edges.
  - Polyline mode (continuous).
- **Closed polyline → planar face creation**:
  - If a set of edges forms a planar closed loop, generate a **Face**.
  - Must support faces with holes (inner loops).
- **Extrude (Push/Pull)**:
  - Select face, extrude linearly along face normal by a distance.
  - Creates side faces and a top face, producing a manifold solid when possible.

### 3.3 Cutting / splitting
- Drawing a line that crosses:
  - a **face**: splits the face into new faces along the cut.
  - an **edge**: splits the edge at intersection into two edges.
- Intersections occur in 3D, but face cutting is in face plane.
- Robustness requirement: handle near-coplanar and near-colinear tolerances.

### 3.4 Selection
- Single click selects entity under cursor (vertex > edge > face priority, configurable).
- Double click selects **connected** geometry:
  - “Connected” means edges/faces that share vertices/edges within the current edit context.
- Window selection:
  - **Window (inside-only)** selection when dragging left-to-right.
  - **Crossing (inside + intersected)** selection when dragging right-to-left.
- Selection set supports mixed entity types.
- Transform operations on selection:
  - Move, Rotate, Scale.
  - Delete.

### 3.5 Snapping (inference)
- Cursor snaps to:
  - endpoints
  - midpoints
  - points on edges
  - points on faces (projected)
- Visual feedback:
  - highlight snap target
  - show tooltip (e.g., “Endpoint”, “Midpoint”, “On Face”)

### 3.6 Rendering
- Basic PBR not required for MVP; prioritize clarity and speed.
- Shadows:
  - Shadow mapping with a single directional light is acceptable.
  - Adjustable shadow quality (resolution, cascades optional later).

---

## 4. SketchUp-derived features to add (recommended to match expectations)

### 4.1 Inference engine (high impact)
Add SketchUp-like inference behaviors beyond simple snapping:

- **Axis inference**: infer movement/drawing along red/green/blue axes.
- **Parallel/Perpendicular inference**:
  - When drawing or moving, infer directions parallel/perpendicular to nearby edges or face normals.
- **From-point inference**:
  - Hover a point to “lock” it as a reference, then infer alignment to it.
- **Inference lock**:
  - Modifier key locks current inference direction/plane (e.g., lock to axis, lock to plane).
- **Auto-face inference**:
  - While drawing edges in a plane, maintain that plane until explicitly changed.

Acceptance criteria:
- Users can reliably draw rectangles on a face, draw lines aligned to axes, and move geometry constrained to an axis/plane.

### 4.2 Measurement input (high impact)
- A measurement box (HUD) shows current length/angle/distance.
- User can type exact values (e.g., `250`, `2.5m`, `30deg`) to finalize operations:
  - line length
  - extrude distance
  - move distance
  - rotate angle
  - scale factor

Units:
- Configurable unit system (mm/cm/m, inches/feet). Internally store in meters or double units.

### 4.3 Groups and Components (essential for SketchUp-like workflow)
- **Group** creation from selection.
- **Explode** group back to raw geometry.
- **Component** creation:
  - definition + instances
  - editing one instance updates all.
- Selection and transforms operate on groups/components as a whole unless in edit context.

### 4.4 Visibility / organization
- Tags (layers) applied to groups/components.
- Tag visibility toggle in UI.
- Hide/Unhide:
  - Hide selected
  - Unhide all (within context)

### 4.5 Materials and UV basics (important for modeling apps)
- Paint tool:
  - apply material to face front/back.
- UV:
  - basic planar projection per face for now.
- Optional later:
  - texture positioning (move/rotate/scale texture on face).

### 4.6 Smoothing / soft edges (important for “nice” models)
- Edge properties:
  - soft
  - smooth
  - hidden
- Rendering uses vertex normals computed by smoothing groups.

### 4.7 Section plane (useful for inspection; optional v1.1)
- Add a section plane to cut view for inspection (view-only clipping, non-destructive).
- Toggle visibility and orientation.

### 4.8 Scenes (optional)
- Save camera position and visibility state (tags + hidden).
- Quick switching.

---

## 5. UX and interaction design

### 5.1 Tool system
Tools similar to SketchUp:
- Select
- Line
- Rectangle (recommended)
- Push/Pull
- Move
- Rotate
- Scale
- Paint (materials)
- Eraser (delete edges; with modifiers to soften/smooth/hide edges)

Each tool has:
- cursor mode
- HUD measurement readout
- inference behavior
- cancel/commit behavior (Esc cancels)

### 5.2 Modifiers (suggested defaults; configurable)
- Shift: lock inference direction/plane
- Ctrl: add/remove from selection / copy while move
- Alt: orbit/pan override (common CAD behavior)
- Esc: cancel current tool operation

### 5.3 Visual feedback
- Hover highlight
- Selection highlight
- Inference lines (dashed guides to axis/parallel/perpendicular)
- Temporary guides while drawing/moving
- Face orientation indicator (front/back coloring option)

---

## 6. Geometry and modeling rules

### 6.1 Planarity and face creation
- Face is created when:
  - closed edge loop exists,
  - loop is planar within tolerance epsilon,
  - loop is non-self-intersecting.
- Support holes:
  - inner loops must lie in same plane and be contained.

### 6.2 “Sticky” geometry
SketchUp-like behavior:
- Raw edges/faces merge automatically when they intersect in same context.
- Groups/components isolate geometry.

### 6.3 Precision and tolerances
- Use double precision for modeling operations.
- Define tolerances:
  - `EPS_DISTANCE` (e.g., 1e-6 model units)
  - `EPS_ANGLE` for coplanarity checks
- Snap tolerance:
  - screen-space pixel threshold converted to world threshold via depth.

### 6.4 Robust cutting
- Face cutting:
  - Project cutting segment into face plane.
  - Compute polygon split; create new faces and edges.
- Edge splitting:
  - Insert vertex at intersection, update topology.

---

## 7. Rendering requirements

### 7.1 Viewport
- Render edges (with depth bias) + faces.
- Optional display modes:
  - Shaded
  - Wireframe overlay
  - X-ray (semi-transparent)

### 7.2 Shadows
- Directional light shadow map (at minimum).
- Configurable:
  - resolution (e.g., 1024/2048/4096)
  - cascade count (0/2/4) optional
  - bias settings
- Must not flicker excessively while orbiting.

### 7.3 Picking
- Must accurately pick small edges/vertices.
- Use:
  - CPU ray tests against BVH/acceleration structure
  - or GPU ID buffer picking (optional)

---

## 8. Data model and file IO

### 8.1 In-memory model
Recommended structure:
- Topology graph: vertices/edges/faces with adjacency links.
- Containers: groups/components hold their own topology.
- Instances: component instances store transform + reference to definition.

### 8.2 Persistence
- Project file format:
  - JSON (easy) or binary (later).
  - Must store:
    - topology per container
    - transforms
    - materials + texture references
    - tags/visibility
    - camera and scenes (if implemented)

### 8.3 Import/Export (later)
- Export: OBJ + MTL (baseline), glTF (better).
- Import optional.

---

## 9. UI requirements (2D)

### 9.1 Layout
- Top toolbar (tools, snapping toggles)
- Left tool palette (optional)
- Right inspector:
  - entity info (type, length/area/volume)
  - materials
  - tags
- Bottom measurement box / status bar
- Outliner panel (groups/components hierarchy)

### 9.2 Command system
- Menu:
  - New/Open/Save/Save As
  - Undo/Redo
  - Preferences
- Shortcuts:
  - standard modeling shortcuts configurable

---

## 10. Undo/Redo (must-have)
- Every modeling action is a command with inverse operation:
  - draw edge(s)
  - create face
  - cut
  - extrude
  - transform
  - delete
  - group/component operations
- Multi-step operations commit as a single command on finish.

---

## 11. Performance requirements
- Target: smooth interaction at 60 FPS on mid-range desktop GPU.
- Editing operations should feel immediate:
  - simple actions < 50 ms typical
- Use spatial acceleration:
  - BVH or uniform grid for picking/intersections
- Mesh rebuild:
  - incremental updates when possible; otherwise rebuild per container.

---

## 12. Non-goals (for now)
- Full SketchUp extension ecosystem.
- Complex boolean solids beyond planar cuts (optional later).
- Advanced UV unwrapping.
- Physics simulation.

---

## 13. Acceptance tests (high-level)
1. Draw rectangle on ground plane by inference and typed dimensions → creates a face.
2. Push/Pull face by typed distance → creates a watertight prism.
3. Draw a diagonal line across top face → face splits into two faces.
4. Window-select right-to-left selects intersected edges; left-to-right selects only inside.
5. Double-click selects connected geometry of a face and its boundary edges.
6. Group geometry; draw a line through it outside group context → group geometry is not cut.
7. Snap to midpoint and endpoint is stable while orbiting.
8. Shadows render without severe acne or peter-panning at default settings.


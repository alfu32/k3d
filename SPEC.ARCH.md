Implement an explicit **topology model → derived render meshes → LWJGL3 rendering** pipeline. The critical part is keeping **editing operations on topology**, not on triangle meshes. Triangles are a cache.

## 1) Architecture layers

### 1. Model layer (authoritative)

Owns editable geometry and semantics.

**Entities**

* `Vertex(id, position: Vec3d)`
* `Edge(id, v0, v1, flags: soft/smooth/hidden, adjacency)`
* `Face(id, plane, outerLoop: List<EdgeRef>, innerLoops: List<List<EdgeRef>>, materialFront, materialBack)`
* `Container(id)` for **Model**, **GroupDefinition**, **ComponentDefinition**
* `Instance(id, definitionId, transform)`

**Indexes / adjacency**

* vertex → incident edges
* edge → adjacent faces (0/1/2 ideally)
* face → boundary loops
* spatial index (BVH/AABB tree) over **edges and faces** in current edit context

Edits mutate this graph and update adjacency + spatial index.

### 2. Derived geometry layer (cache)

Produces renderable buffers, rebuilt incrementally.

Per container (model or group definition):

* `RenderMeshCache`:

  * `faceMesh`: triangles + normals + UVs + material id
  * `edgeMesh`: line segments (optionally thick lines via camera-facing quads)
  * `vertexMesh`: optional points for debug/snap visualization
  * `bvh`: acceleration structure for picking/intersection (can share with model layer if you store BVH nodes referencing model primitives)

Dirty flags:

* `DIRTY_TOPOLOGY` (changed adjacency or face loops)
* `DIRTY_GEOMETRY` (vertex positions changed)
* `DIRTY_MATERIALS`
* `DIRTY_VISIBILITY`

Rules:

* Model edits mark affected containers dirty.
* Renderer queries cache; if dirty, rebuild only what’s needed.

### 3. Rendering layer (libGDX on LWJGL3)

libGDX already wraps LWJGL; you don’t render “with LWJGL” directly unless you want to drop down to custom GL calls. Use:

* `ModelBatch`/custom shader for faces
* `ImmediateModeRenderer` or custom mesh for edges
* Shadow mapping via custom `ShaderProgram` + depth pass or libGDX 3D utilities if they fit

Keep rendering entirely separate from modeling.

### 4. Interaction layer

* Tool state machine (Select, Line, Rectangle, PushPull, Move, Rotate, Scale…)
* Picking/inference uses:

  * camera ray
  * model BVH queries
  * ground plane fallback
  * snapping rules + inference constraints

### 5. Command layer (undo/redo)

* Every operation is a `Command` with `execute()` and `undo()`
* Commands mutate the model; they do not touch render meshes directly (only mark dirty)

---

## 2) Why topology-first is mandatory

If you edit only triangle meshes:

* You lose adjacency and planarity info needed for “closed polyline makes face”, push/pull, cutting.
* You can’t reliably implement connected selection, soft/smooth edges, holes, or inference.

So:

* **Topology graph** is the single source of truth.
* **Triangles** are a view.

---

## 3) Concrete base implementation plan

### Step A — Minimal model + rendering

1. Implement `Vertex`, `Edge`, `Face` with adjacency.
2. Implement “create edge” tool in a single plane (ground plane only).
3. Face creation:

   * detect closed loop
   * verify planarity
   * create `Face`
4. Triangulate face to render mesh:

   * simplest: polygon triangulation in face local 2D coordinates.
5. Render:

   * faces as triangles (solid shaded)
   * edges as lines overlay

At this stage you already have the “SketchUp feel” starting.

### Step B — Picking + snapping + inference

* BVH over edges and faces:

  * edge hit test: ray vs segment distance in screen-space threshold
  * face hit test: ray-plane intersection + point-in-polygon in face 2D
* Snapping candidates:

  * endpoints, midpoints (from edges)
  * closest point on edge segment
  * face intersection point
* Inference:

  * axis constraints (world or local)
  * plane constraints (face plane, ground plane)
  * parallel/perpendicular to nearby edges

### Step C — Push/Pull extrusion

* Input: face + distance
* Build side faces by offsetting boundary loop along normal
* Stitch topology:

  * new vertices at offset
  * new edges
  * new faces for sides + top
* Mark container dirty → rebuild render mesh

### Step D — Cutting/splitting

* Edge split: insert vertex, replace edge with two edges, update adjacent faces loops
* Face cut:

  * project cut segment to face 2D
  * perform polygon split (outer loop + holes)
  * generate new faces and new edges
    This is the first “hard geometry” milestone.

---

## 4) Data structures that make this workable

### 4.1 Stable IDs and reference storage

Use stable IDs for topology objects; selection stores IDs, not pointers.

### 4.2 Half-edge vs simple adjacency

You can start with “simple adjacency” (edge stores up to 2 adjacent faces) for MVP.
If you want robust planar edits and holes, a **half-edge** structure becomes worth it later.

Pragmatic approach:

* v1: simple graph + face loops as edge lists
* v2: migrate to half-edge when you hit complexity walls

### 4.3 Spatial index

You need this early. Without it, picking and inference will feel broken at scale.

* BVH nodes referencing:

  * edge AABBs
  * face AABBs

---

## 5) Rendering decisions (libGDX + LWJGL3)

### Faces

* Keep a `Mesh` per container or per material bucket.
* Vertex attributes: position, normal, uv, color/material id.

### Edges

Default GL lines are inconsistent across platforms and not thick.
Better:

* render edges as camera-facing quads (“polyline billboard”), thickness in pixels.
  This also improves pickability and SketchUp-like clarity.

### Shadows

Directional light shadow mapping:

* depth pass renders only faces
* main pass samples shadow map
  Start with single map; cascades later.

---

## 6) Minimal class map (base)

* `ModelRoot`

  * `Container` (top-level)
  * `GroupDefinition`, `ComponentDefinition`
  * `Instance`
* `TopologyStore` (vertices/edges/faces + adjacency)
* `SpatialIndex` (BVH)
* `MeshCache`

  * `buildFacesMesh(containerId)`
  * `buildEdgesMesh(containerId)`
* `Renderer3D`
* `ToolController` + tools
* `CommandStack`

---

If you want the next step to be actionable: implement **(1) topology for edges/faces**, **(2) triangulation into a libGDX `Mesh`**, **(3) BVH picking with ground-plane fallback**, and **(4) snap selection**. Everything else builds on that

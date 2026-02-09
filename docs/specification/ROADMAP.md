1. i want the grid size to be accessible for parametrization to the user in the settings panel, this measure will be used also by all the helpers ( t - the axis system and g - the grid planes system ) if
   it isnt already used.
2. i would also like to be able to toggle what the 3d renderer renders ( toggle the visibility of each of the entities types ). i dont know in which panel we should put these ... propose something
   ( maybe the selection/entities panel )
3. i want the cli to enter a repl mode after launching edit, it should accept commands towards tools like
```
line 0,0,0 1,2,3
camera target 0,0,0
camera position 10,10,8
select 3,2,7 9,5,9
list objects
list lines
list faces
save
exit
show <panel name>
hide <panel name>
list panels
... etc
```
or invoking a tool and feeding input until all its input requirements are satisfied:
```
line
0,0,0
1,2,3

camera
target
0,0,0

camera
position
10,10,8

select
3,2,7
9,5,9

list
objects

list
lines

list
faces

save

saveas
filename.k3d

exit

show
<panel name>

hide
<panel name>

list
panels

screenshot
screenshot_001.png

and so on ....
```

well in principle we could have each internal tool and each plugin, the app model return a list of named functions that take parameters.

the approach is to take each tool functionality one by one, and add a register of commands ...

that would also enable to record and enact tutorials or integration/user testing and regression testing

the scripting could also be simply a groovy REPL

we would need metacommands like :help,:list ( listing available objects ),:quit


# 4.   Spec: Auto‑cut lines + faces, then cleanup/consolidation (JTS)

Scope

- Applies only when adding/drawing lines (Line/Rectangle/SurfaceRect/Quad/Circle).
- No auto‑cut on move/stretch/rotate/etc.
- Must work in local group space; all geometry passed to the stores is local.

———

## 1) Line auto‑cut (existing behavior, but define it)

When adding a segment (A→B):

- Intersect the new segment with all existing segments in the same line store.
- Split at each intersection point.
- Also split any existing segment the new segment crosses.
- Snap endpoints if within epsilon.
- After split, consolidate collinear overlaps/contiguous segments.

Definition

- Intersection point if segments intersect in 3D and are coplanar (within epsilon) or truly intersect in 3D (current behavior is acceptable).
- Collinear merge: segments share the same infinite line (direction + offset within epsilon), overlapping or touching.

Output

- A set of segments representing the union of original + new, with no interior intersections and no contiguous collinear fragments.

———

## 2) Face auto‑cut by added line segment

Condition

- Only cut faces when the segment lies in the triangle’s plane (distance of both endpoints to plane <= epsilon and segment direction is in plane).

Behavior

- For each triangle intersected by the line segment, split that triangle by the line (not just the segment if one endpoint is inside or the segment is fully inside).
- Replace the triangle with the resulting sub‑triangles.

Edge cases

- If the line intersects at 1 boundary point and the other endpoint is inside, extend the line through the face to cut the polygon.
- If both endpoints are inside, cut the polygon by the infinite line through the segment (resulting in two polygons).

Output

- No face should have an edge that is intersected by a line segment without being split at that intersection.

———

## 3) Cleanup / Consolidation (JTS)

Line cleanup

- Convert each segment to JTS LineString.
- Group faces by coplanar plane (normal + distance, within epsilon).
- For each group:
    - Build polygons from triangles by assembling their boundary edges.
    - Use JTS Polygonization (or Polygonizer) to form polygons.
    - Union polygons to remove overlaps.
    - Triangulate the union polygon(s) (e.g., org.locationtech.jts.triangulate.EarClipper or DelaunayTriangulationBuilder).
- Replace the group’s triangles with the triangulated output.
- If polygonization or union fails, fall back to original group (do not delete faces).

Selection handling

- If any source face was selected, you can:
    - either reselect all produced triangles, or
    - reselect only those whose centroid lies within the original selected polygon area.
1. lineStore.addSegment(start, end) (auto‑split)
2. faceStore.cutBySegmentInPlane(start, end) (create splits)
3. lineStore.cleanup() or cleanupSelected() depending on scope
4. faceStore.cleanup() (JTS‑based)

Performance guard

- If repeated cuts are needed (e.g., a segment passes across many faces), allow iterative passes but cap at:
    - 10 iterations OR 1 second (whichever comes first).

———

## 5) Tolerances

- Use a shared epsilon for coplanarity and snapping (same as line epsilon or slightly larger).
- When converting to JTS, scale coordinates if needed to keep precision (e.g., multiply by 1e4, round to int, then divide back after).

---

## 6) Internal roadmap update (M1 -> OpenSCAD first)

Execution order

1. M1: UI foundation (first and mandatory)
   - Split standard tools into two groups: Construction and Modification; keep Actions separate.
   - Standard tool buttons show labels at all times.
   - Add delayed hover popover near target component (tooltip-like behavior).
   - Move built-in tool groups into floating, movable toolbars with persisted layout.
   - This is the enabling layer for all following internal plugins.

2. M2: OpenSCAD integration (before voxel/architecture/mechanical)
   - Add a generic parametric generation pipeline (`inputs -> generated faces/lines`).
   - Add regeneration cache + invalidation on parameter/script change.
   - Add safe execution boundary (timeouts/errors surfaced to UI without destabilizing session).
   - Add explode behavior: OpenSCAD object -> static faces/lines.

3. M3: Solid tools on top of the same backend
   - Build boolean operations plugin (union/intersection/difference) using the same parametric/solid pipeline.
   - Reuse execution, preview, caching, and explode paths from M2.

4. M4: Domain plugins powered by the same core
   - Voxel plugin (voxel group + voxel/volume/frame tools).
   - Architecture plugin (wall/slab/stair + rectangular hole workflow).
   - Mechanical plugin (gear object first, then additional parametric parts).

5. M5: Consolidation
   - Unify plugin object lifecycle (edit/open/close/explode/update) across all internal plugins.
   - Keep fallback behavior for missing OpenSCAD runtime or execution failure.

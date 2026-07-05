# Modify Tools

Modification tools transform existing geometry and convert sketches into volumes.

## High-Use Tools

- Select: inspect and choose model entities.
- Push/Pull: extrude planar faces into solids where topology allows.
- Move, Rotate, Scale, and Stretch: transform selected geometry.
- Rotate Stretch and copy-array tools: produce repeated or rotational edits.
- Paint: apply color to selected faces.
- Random Offset and Random Surface Array: create controlled irregularity from selected geometry.
- Mesh Regularize: rebuild a near-planar selected patch into regular triangles.

## Push/Pull Workflow

1. Draw or select a planar face.
2. Activate Push/Pull.
3. Pick the face.
4. Move along the face normal and commit the distance.

For documentation captures, generate a deterministic base face first, then validate the resulting solid either through scene state or screenshot comparison.

## Object Boolean Workflow

Solid Union, Solid Intersection, and Solid Subtraction are object-level mesh tools. They do not consume loose face selections. Select exactly two mesh object instances that share the same parent context, then run the operation from the toolbar or command palette.

The operation first computes the ordered intersection segment collection once and keeps it fixed. The cut phase then runs global convergence passes over that immutable segment list: the tool keeps mutating each face set until no fixed intersection segment crosses a remaining triangle interior. When a fixed segment only partly crosses a triangle, the cutter is clipped to the portion inside that triangle before the cut is applied. Segment/triangle pairs where the clipped segment already lies on a triangle border are ignored. It then removes or keeps the resulting face fragments according to the requested operation. The selected objects are replaced with loose selected mesh faces; this is deliberate because the result is immediately editable, can be inspected face-by-face, and can be grouped into a new object only after the user is satisfied with the topology.

Use closed, consistently wound mesh objects for best results. If the result is empty or incomplete, run Mesh Intersection first to see whether the operands really cross, then repair open faces or flipped normals before retrying.

## Cut Objects

Cut Objects is the non-boolean version of the object cut phase. Select exactly two mesh object instances, then run the tool to replace them with loose selected cut triangles, original loose segments from both objects, and the generated intersection segments. No faces are removed for union, intersection, or subtraction.

Use this tool when checking whether the cut phase is complete before reasoning about boolean classification.

## Fuzzy Tools

Random Offset moves selected connected vertices along an averaged normal direction. Select a patch of faces or boundary segments, run the tool, and enter a low strength first. The implementation moves shared vertices once, so adjacent selected faces and selected edge segments stay attached.

Random Surface Array distributes a selected payload over selected surface faces. Use it for rough coverage patterns such as panels, stones, vegetation placeholders, or randomized repeated details. Keep the first pass sparse, then increase count, scale fuzz, rotation fuzz, and normal alignment after the distribution looks correct.

Mesh Regularize is a repair and remeshing helper for near-planar patches. It replaces the selected patch with a rectangular grid of triangles at the requested step size. If the selection is curved, folded, or noisy, split it into smaller planar regions before regularizing.

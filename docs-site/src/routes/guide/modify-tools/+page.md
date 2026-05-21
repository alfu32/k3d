# Modify Tools

Modification tools transform existing geometry and convert sketches into volumes.

## High-Use Tools

- Select: inspect and choose model entities.
- Push/Pull: extrude planar faces into solids where topology allows.
- Move, Rotate, Scale, and Stretch: transform selected geometry.
- Rotate Stretch and copy-array tools: produce repeated or rotational edits.
- Paint: apply color to selected faces.

## Push/Pull Workflow

1. Draw or select a planar face.
2. Activate Push/Pull.
3. Pick the face.
4. Move along the face normal and commit the distance.

For documentation captures, generate a deterministic base face first, then validate the resulting solid either through scene state or screenshot comparison.

## Object Boolean Workflow

Solid Union, Solid Intersection, and Solid Subtraction are object-level mesh tools. They do not consume loose face selections. Select exactly two mesh object instances that share the same parent context, then run the operation from the toolbar or command palette.

The operation replaces the selected objects with loose selected mesh faces. This is deliberate: the result is immediately editable, can be inspected face-by-face, and can be grouped into a new object only after the user is satisfied with the topology.

Use closed, consistently wound mesh objects for best results. If the result is empty or incomplete, run Mesh Intersection first to see whether the operands really cross, then repair open faces or flipped normals before retrying.

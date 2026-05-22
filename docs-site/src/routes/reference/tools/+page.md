<script>
  import CommandReferenceTable from '$lib/components/CommandReferenceTable.svelte';
</script>

# Tools Reference

Built-in tools are defined in the Kotlin source as `ToolId` values and exposed through toolbar UI, keyboard cycles, command palette entries, and MCP command execution when registered.

## Construction Tools

Line, Construction Line, Polyline, Double Line, Rectangle, Surface Rectangle, Quad, Circle, Face Outline, Line Offset, Plane Section, Mesh Intersection, Dimension, Text, and Vector Text cover core sketching and annotation.

## Modification Tools

Select, Push/Pull, Move, Rotate, Scale, Stretch, Rotate Stretch, copy-array tools, and Paint cover the direct modeling loop.

### Object Boolean Operations

Solid Union, Solid Intersection, and Solid Subtraction operate on object instances, not arbitrary selected faces. Select exactly two mesh object instances in the same parent context, then run the boolean command.

The tool first finds the ordered intersection segment collection between the two object face sets and keeps that collection immutable for the rest of the operation. It then runs global convergence passes over that fixed segment list, applying the bounded cutout routine to both operands in each object's local coordinate system before any geometry is discarded. For each current triangle, the fixed intersection segment is clipped to the part that actually crosses that triangle before the cutout routine runs. Segment/triangle pairs where the clipped segment already rests on a triangle border are skipped so the cut phase converges. Only after no fixed segment crosses any remaining triangle interior does it classify face fragments against the opposite object, remove the two selected objects, and write the result back as exploded mesh faces in the parent context. The result is left selected so it can be moved, grouped, painted, or corrected immediately.

These tools are intended for mesh objects with coherent face normals and reasonably closed volumes. Open or inconsistent volumes may produce partial results; in that case, use Mesh Intersection or Cut With Plane to inspect and repair the operands before running the boolean again.

### Cut Objects

Cut Objects uses the same two-object input and the same intersection-cut preparation as the boolean tools, but it does not classify or discard any faces. Select exactly two mesh object instances, run Cut Objects, and the tool replaces both objects with loose selected triangles from both cut operands, the original loose segments from both objects, and loose selected intersection segments.

Use this when validating the shared boolean preparation step. If the resulting triangles and intersection segments are complete, the remaining union/intersection/subtraction behavior can be reasoned about as face-set selection. If cuts are missing, diagnose the cut phase before changing boolean classification.

## Domain Tools

Architecture tools include wall, slab, stair, hole, window frame, and door frame workflows. Voxel tools include voxel, volume, and frame blockout tools. HVAC tools exist in source and should be documented only after validating the current user workflow.

### Mechanical Tools

The Mech toolbar contains Screw, Circular Hole, Round Washer, and Cog Wheel tools.

Cog Wheel builds a procedural planar cog-wheel outline. Activate Cog Wheel, then pick:

1. the tangent start point,
2. the tangent end point,
3. a tooth-count measure point,
4. a tooth-depth point.

The tangent start/end segment defines the tangential length of one tooth pitch. The cog center is placed on the left-hand side of the tangent vector from start to end. The distance from tangent start to the third point, measured in model units and rounded, defines the number of teeth. Once the third point is picked, the tool previews the potential cog center and planar outline. The distance from tangent start to the fourth point defines tooth depth. Each pitch cell is built from inner/root at 0/4, outer rise, outer land, inner/root at 3/4, and inner/root at 4/4. The outer land uses a smaller angular span when needed so its arc length matches the inner root segment. The tool also adds one radial construction segment from the center to the tangent midpoint.

## Command Naming

Current docs describe built-in tool activation as `tool.builtin.<id>`, where `<id>` is the lower-case tool ID such as `rectangle` or `push_pull`. Automation must still call `/scene/listCommands` first.

## Generated Tool Commands

This table is generated from the local MCP command catalog. It should be refreshed before publishing release-quality tool references.

<CommandReferenceTable category="Tools" />

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

Cog Wheel builds a repeated tooth wheel from selected line segments. Select one connected closed outline for a single tooth, activate Cog Wheel, then pick:

1. the cog center `C`,
2. the inner radius point `R`,
3. the height point `H`.

The tool uses `C-H` as the cog axis and extrusion vector. It projects `C-R` perpendicular to that axis to define the inner radius and first radial direction. The selected tooth outline is shifted so its innermost radial projection sits on the chosen inner radius, then the tangential width of the tooth estimates the tooth count around the circumference. Each copied tooth is extruded along `C-H`, so slanted tooth outlines and slanted cog axes are supported as long as the input loop is connected and non-degenerate.

## Command Naming

Current docs describe built-in tool activation as `tool.builtin.<id>`, where `<id>` is the lower-case tool ID such as `rectangle` or `push_pull`. Automation must still call `/scene/listCommands` first.

## Generated Tool Commands

This table is generated from the local MCP command catalog. It should be refreshed before publishing release-quality tool references.

<CommandReferenceTable category="Tools" />

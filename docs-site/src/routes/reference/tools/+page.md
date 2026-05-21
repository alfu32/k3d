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

The tool first finds intersection segments between the two object face sets. It then iteratively applies the bounded cutout routine to both operands, in each object's local coordinate system, before any geometry is discarded. Segment/triangle pairs where the segment already rests on a triangle border are skipped so the cut phase converges. Only after this shared cut phase does it classify face fragments against the opposite object, remove the two selected objects, and write the result back as exploded mesh faces in the parent context. The result is left selected so it can be moved, grouped, painted, or corrected immediately.

These tools are intended for mesh objects with coherent face normals and reasonably closed volumes. Open or inconsistent volumes may produce partial results; in that case, use Mesh Intersection or Cut With Plane to inspect and repair the operands before running the boolean again.

## Domain Tools

Architecture tools include wall, slab, stair, hole, window frame, and door frame workflows. Voxel tools include voxel, volume, and frame blockout tools. HVAC tools exist in source and should be documented only after validating the current user workflow.

## Command Naming

Current docs describe built-in tool activation as `tool.builtin.<id>`, where `<id>` is the lower-case tool ID such as `rectangle` or `push_pull`. Automation must still call `/scene/listCommands` first.

## Generated Tool Commands

This table is generated from the local MCP command catalog. It should be refreshed before publishing release-quality tool references.

<CommandReferenceTable category="Tools" />

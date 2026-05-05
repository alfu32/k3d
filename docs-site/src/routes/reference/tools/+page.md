<script>
  import CommandReferenceTable from '$lib/components/CommandReferenceTable.svelte';
</script>

# Tools Reference

Built-in tools are defined in the Kotlin source as `ToolId` values and exposed through toolbar UI, keyboard cycles, command palette entries, and MCP command execution when registered.

## Construction Tools

Line, Construction Line, Polyline, Double Line, Rectangle, Surface Rectangle, Quad, Circle, Face Outline, Line Offset, Plane Section, Mesh Intersection, Dimension, Text, and Vector Text cover core sketching and annotation.

## Modification Tools

Select, Push/Pull, Move, Rotate, Scale, Stretch, Rotate Stretch, copy-array tools, and Paint cover the direct modeling loop.

## Domain Tools

Architecture tools include wall, slab, stair, hole, window frame, and door frame workflows. Voxel tools include voxel, volume, and frame blockout tools. HVAC tools exist in source and should be documented only after validating the current user workflow.

## Command Naming

Current docs describe built-in tool activation as `tool.builtin.<id>`, where `<id>` is the lower-case tool ID such as `rectangle` or `push_pull`. Automation must still call `/scene/listCommands` first.

## Generated Tool Commands

This table is generated from the local MCP command catalog. It should be refreshed before publishing release-quality tool references.

<CommandReferenceTable category="Tools" />

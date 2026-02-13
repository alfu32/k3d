# Chapter 09: Commands Reference

Author: Codex (GPT-5)
Date: February 13, 2026

## Goal
Document command usage patterns and verify command outcomes with practical MCP-driven examples.

## Chapter Cleanup
Before this chapter, scene state was reset:

```groovy
app.run {
  scene.resetScene()
  save.set("examples/mcp.demo.k3d")
  cameraCtl.setPosition(14f, 12f, 14f)
  cameraCtl.setTarget(0f, 0f, 0f)
}
```

![Step 1 - Cleanup state](../images/ch09_01_cleanup.png)

## Practical Command Examples

### 2) View commands opening panels
Commands:
- `view.objects`
- `view.selection`
- `view.object_info`
- `view.model_settings`

![Step 2 - View commands](../images/ch09_02_view_commands_open_panels.png)

### 3) Tool command execution
- `tool.builtin.rectangle`
- Draw rectangle from `(-2,0,-2)` to `(3,0,3)`

![Step 3 - Tool command rectangle](../images/ch09_03_tool_command_rectangle.png)

### 4) Edit command execution
- `tool.builtin.select` then face click near `(0,0,0)`
- `edit.group`

![Step 4 - Edit group](../images/ch09_04_edit_group_command.png)

### 5) Camera command execution
- `view.camera.orthographic`
- `view.ortho.front`

![Step 5 - Camera commands](../images/ch09_05_camera_commands.png)

## Command Catalog Snapshot
Live snapshot source: `/scene/listCommands` (captured February 13, 2026).

- Total: `61`
- `Tools`: `31`
- `View`: `19`
- `Edit`: `6`
- `Export`: `5`

## High-Use Commands by Category

### Edit
- `edit.group`
- `edit.ungroup`
- `edit.new_architecture_group`
- `edit.new_voxel_group`
- `edit.voxelize_faces`

### Tools
- `tool.builtin.select`
- `tool.builtin.line`
- `tool.builtin.rectangle`
- `tool.builtin.push_pull`
- `tool.builtin.move`
- `tool.builtin.rotate`
- `tool.builtin.scale`
- `tool.builtin.arch_wall`
- `tool.builtin.arch_add_hole`
- `tool.builtin.voxel`

### View
- `view.selection`
- `view.objects`
- `view.object_info`
- `view.model_settings`
- `view.lighting`
- `view.architecture_settings`
- `view.camera.orbit`
- `view.camera.walkthrough`
- `view.camera.orthographic`
- `view.ortho.top|bottom|left|right|front|back`

### Export
- `export.screenshot`
- `export.svg_view`
- `export.ifc_model`
- `exporters.export.obj`
- `exporters.export.stl`

## MCP Endpoints Used
- `GET /scene/listCommands`
- `GET|POST /scene/command`
- `GET|POST /scene/pointer`
- `GET|POST /scene/console`
- `GET /mcp/status`

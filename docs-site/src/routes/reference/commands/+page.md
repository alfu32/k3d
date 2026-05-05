<script>
  import CommandReferenceTable from '$lib/components/CommandReferenceTable.svelte';
</script>

# Commands Reference

Octodraw commands are registered in the command palette and exposed to local MCP automation. The command palette UI is opened with `Ctrl+Shift+P`; local automation lists commands through `/scene/listCommands`.

## Runtime Source of Truth

Agents and scripts must discover command IDs at runtime:

```sh
curl http://127.0.0.1:8765/scene/listCommands
```

The response contains `id`, `name`, `category`, `description`, `icon`, `priority`, and `tags` for each command available in that build.

## Known Patterns

Current source and docs show these patterns:

- View commands: `view.selection`, `view.objects`, `view.object_info`, `view.model_settings`, `view.lighting`, `view.plugin_manager`, camera commands, and capture UI commands.
- Built-in tool commands: `tool.builtin.<tool-id-lowercase>`.
- Edit commands: grouping, ungrouping, reset-for-capture, voxel/architecture setup, and selected cut workflows.
- Export commands: screenshot, SVG view, IFC, and mesh export flows.
- Plugin commands: `<pluginId>.<commandId>`.
- Plugin tools: `tool.<pluginId>.<toolId>`.

## Regeneration Workflow

1. Start Octodraw locally.
2. Confirm MCP with `/mcp/status`.
3. Fetch `/scene/listCommands`.
4. Sort by category and ID.
5. Compare generated command docs against hand-authored explanations.
6. Keep generated snapshots separate from explanation text so stale output can be refreshed.

## Generated Catalog

The table below is generated from the local MCP command catalog and kept separate from the prose in `src/lib/data/generated/command_catalog.json`.

<CommandReferenceTable />

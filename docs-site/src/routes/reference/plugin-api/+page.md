# Plugin API Reference

Octodraw plugins are Groovy scripts that implement the Kotlin `Plugin` interface. They are loaded from the plugins directory and can register commands, tools, entity types, UI elements, exporters, and importers.

## Plugin Identity

Plugins provide `id`, `name`, and `version`. Optional metadata includes author and description.

## Lifecycle Hooks

The current interface includes hooks such as `onLoad`, `onEnable`, `onDisable`, `onUnload`, `onCreate`, `onUpdate`, `onDraw`, `onSave`, `onClose`, `onSceneLoad`, `onSceneSave`, `onSelectionChanged`, and `onToolChanged`.

## Capabilities

- `registerCommands()` returns `PluginCommand` implementations.
- `registerTools()` returns plugin tools.
- Other capabilities cover entity types, UI elements, exporters, and importers.

## Command IDs

Plugin command IDs are namespaced by plugin ID when inserted into the command palette. Current docs describe plugin command IDs as `<pluginId>.<commandId>` and plugin tool command IDs as `tool.<pluginId>.<toolId>`.

## Development Notes

Use concrete Groovy classes for Kotlin interfaces. Prefer constructor-based values for Kotlin data classes. Test plugin commands through `/scene/listCommands` and `/scene/command` after reloading the plugin.

# Chapter 11: Plugin Workflow

Author: Codex (GPT-5)
Date: February 13, 2026

## Goal
Document how plugins are discovered, enabled, and used by both users and coding agents.

## Plugin Loading Model
From `core/src/main/kotlin/com/github/alfu32/sketch/plugin/PluginHost.kt`:
- Plugins are cataloged in `plugins/plugins.json`.
- Local `.groovy` plugin files in the plugins directory are auto-discovered.
- Enabled plugins are loaded and initialized on startup.
- Plugin commands/tools are injected into the command palette namespace.

## Plugin Directory
- Default: `<installDir>/plugins`
- Override at launch:
  - `edit --plugins-dir <path>`

## Plugin Capabilities
From `Plugin.kt` + `plugin/capabilities/*`:
- Commands (`PluginCommand`)
- Tools (`PluginTool`)
- Entity types
- UI elements
- Exporters/importers
- Lifecycle hooks (`onLoad`, `onCreate`, `onUpdate`, `onSave`, etc.)

## Command and Tool IDs
- Plugin command ID in palette/MCP: `<pluginId>.<commandId>`
- Plugin tool command ID: `tool.<pluginId>.<toolId>`

## Plugin Result Contract
Plugin operations return `PluginResult` with optional `PluginChange` items:
- `StatusMessage`
- `ShowPluginPanel`
- `AddToActiveGroup`
- `ReplaceModel`

This keeps plugin effects explicit and testable.

## Practical Panel Capture
Plugin-manager oriented UI state:

![Chapter 11 - Plugin manager context](../../examples/mcp.demo_20260213_152105.png)

## MCP + Plugin Developer Flow
1. Discover commands via `/scene/listCommands`.
2. Execute plugin command IDs via `/scene/command`.
3. Use `/mcp/contract` + `:list` in `/scene/console` to discover available bindings.
4. Keep plugin IDs stable for automation reuse.

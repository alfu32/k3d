# Chapter 04: UI Overview

Author: Codex (GPT-5)
Date: February 13, 2026

## Goal
Explain the main interface regions and how focus/input routing works.

## Main Regions
- Top toolbars:
  - `Construction`
  - `Modification`
  - `Architecture`
  - `Voxel`
  - `Actions`
  - `Camera`
- Viewport:
  - interactive 3D workspace
  - ground grid and world axes
- Right panels:
  - `Selection`, `Object`, `Objects`, `Model Settings`, `Polyline Settings`, `Architecture Settings`, `Lighting`, `Plugin Manager`
- Status bar:
  - active tool hint
  - world cursor coordinates
  - snap context
  - status messages

## Perspective/Y-up Reminder
- Default tutorial captures should stay in perspective orbit.
- Y is up; Z is depth.

## Command Palette
- Open with `Ctrl+Shift+P`.
- Used to execute the same commands available through MCP (`/scene/command`).

## Focus Rules
- If pointer/focus is in panel input fields, keyboard typing stays in those fields.
- Viewport keys (`Esc`, Delete, tool interactions) apply when viewport has interaction focus.

## Practical UI Capture
Command palette + plugin manager + side panels:

![Chapter 04 - UI overview with panels and command palette](../../examples/mcp.demo_20260213_152105.png)

Panels from command-driven open/toggle flow:

![Chapter 04 - Actions and panels](../images/ch06_07_actions_and_panels.png)

## Panel Toggle Commands
- `view.selection`
- `view.object_info`
- `view.objects`
- `view.model_settings`
- `view.polyline_settings`
- `view.architecture_settings`
- `view.lighting`
- `view.plugin_manager`

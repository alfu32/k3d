# Chapter 13: Appendices

Author: Codex (GPT-5)
Date: February 13, 2026

## 13.1 Keyboard Shortcuts (Core)
| Action | Shortcut |
|---|---|
| Command palette | `Ctrl+Shift+P` |
| Cancel / clear / exit tool | `Esc` |
| Delete selection | `Delete` |
| Undo | `Ctrl+Z` |
| Redo | `Ctrl+Y` or `Ctrl+Shift+Z` |
| Group | `Ctrl+G` |
| Ungroup | `Ctrl+Shift+G` |
| Create object prototype | `Ctrl+O` |
| Cleanup model | `Ctrl+L` |
| Numeric popup | `Ctrl+N` |
| Add axis guide | `T` |
| Add grid guide | `G` |

Source: `docs/SHORTCUTS.md`.

## 13.2 Mouse Interaction Table
| Context | Interaction | Result |
|---|---|---|
| Orbit camera | Right drag | Orbit |
| Orbit camera | Shift + right drag | Pan |
| Any camera | Mouse wheel | Zoom |
| Selection | Left click | Select |
| Selection | Shift + click | Add selection |
| Selection | Ctrl + click | Remove selection |
| Selection | Drag left->right | Window inside select |
| Selection | Drag right->left | Crossing select |
| Selection | Click empty then second corner | Volume select |

## 13.3 Command Catalog Snapshot
Use live discovery each session:
- `GET /scene/listCommands`

Command families:
- `tool.builtin.*`
- `view.*`
- `edit.*`
- `export.*`
- plugin-provided IDs (`<pluginId>.<commandId>`, `tool.<pluginId>.<toolId>`)

## 13.4 Screenshot Index (Key Tutorial Assets)
- `../images/ch06_01_cleanup.png`
- `../images/ch06_02_line_tool.png`
- `../images/ch06_03_rectangle_tool.png`
- `../images/ch06_04_voxel_tool.png`
- `../../examples/mcp.demo_20260213_145024.png`
- `../../examples/mcp.demo_20260213_145026.png`
- `../../examples/mcp.demo_20260213_152105.png`
- `../../examples/mcp.demo_20260213_154809.png`

## 13.5 MCP Contract Endpoint
Self-describing contract endpoint:
- `GET /mcp/contract`

Related contract document:
- `docs/MCP_CONTRACT_RELIABLE_AGENTS.md`

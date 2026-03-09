# MCP Contract Spec for Reliable Agents

Author: Codex (GPT-5)
Date: February 13, 2026

## Scope
This contract defines the stable automation/plugin surface for:
- coding agent developing plugins,
- designer agent emitting `app.run { ... }` model-building scripts,
- plugin developer creating plugins/tools/commands/entities,
- test automation.

## 1. Stable HTTP API (localhost)
Base URL: `http://127.0.0.1:<port>` (default port `8765`).

- `GET /mcp/status`
- `GET /scene/listCommands` (alias: `GET /scene/commands`)
- `GET|POST /scene/command`
- `GET|POST /scene/console` (alias: `GET|POST /scene/meta`)
- `GET|POST /scene/pointer`

Contract notes:
- Non-supported methods return HTTP `405`.
- Logical failure returns HTTP `500` with JSON `{"success":false,...}`.
- Validation failure returns HTTP `400`.

## 2. Request/Response Contract
### 2.1 Commands
- `GET /scene/command?id=<commandId>`
- `POST /scene/command` body:
  - JSON: `{"id":"<commandId>"}`
  - or plain text: `<commandId>`

Response fields:
- `success`, `commandId`, `message`, `durationMs`, `stdout`, `stdoutLines[]`

### 2.2 Console
- `GET /scene/console?cmd=<groovy>`
- `POST /scene/console` body:
  - JSON: `{"cmd":"..."}` (also accepts `command` or `script`)
  - or plain text script

Response fields:
- `success`, `command`, `message`, `durationMs`, `outputLines[]`, `stdout`, `stdoutLines[]`

### 2.3 Pointer
- `GET /scene/pointer?...` or `POST /scene/pointer` JSON
- Required:
  - `action`: `down|move|up` (aliases accepted: `event`, `type`)
  - and either:
    - screen: `screenX`, `screenY` (aliases: `sx`, `sy`)
    - or world: `worldX`, `worldY`, `worldZ` (aliases: `x`, `y`, `z`)
- Optional:
  - `pointer` (default `0`),
  - `button` (`left|right|middle|lmb|rmb|mmb|0|1|2`, default left),
  - `normalX|normalY|normalZ` (`nx|ny|nz`),
  - `valid` boolean.

Response fields:
- `success`, `handled`, `action`, `pointer`, `button`,
- `screenX|screenY`, `worldX|worldY|worldZ`, `normalX|normalY|normalZ`,
- `valid`, `message`, `durationMs`, `stdout`, `stdoutLines[]`.

Timeout behavior:
- command: ~20s,
- console: ~30s,
- pointer: ~5s.

## 3. Command Namespace Contract
Agents MUST discover available commands at runtime via `/scene/listCommands`.

### 3.1 Core static IDs (current build)
- View: `view.objects`, `view.selection`, `view.object_info`, `view.lighting`, `view.model_settings`, `view.polyline_settings`, `view.architecture_settings`, `view.plugin_manager`, `view.axis_guide`, `view.grid_guide`, `view.camera.orbit`, `view.camera.walkthrough`, `view.camera.orthographic`, `view.ortho.top|bottom|left|right|front|back`, `view.capture_ui_minimal`, `view.capture_ui_restore`
- Edit: `edit.new_architecture_group`, `edit.new_voxel_group`, `edit.voxelize_faces`, `edit.group`, `edit.ungroup`, `edit.cut_rect_hole`, `edit.reset_scene_for_capture`
- Export: `export.svg_view`, `export.screenshot`, `export.ifc_model`

### 3.2 Generated IDs
- Built-in tools: `tool.builtin.<toolid-lowercase>`
- Plugin commands: `<pluginId>.<commandId>`
- Plugin tools in palette: `tool.<pluginId>.<toolId>`

No agent should assume a command exists without checking `/scene/listCommands` first.

## 4. `app.run { ... }` Contract
`app.run` posts work to the LibGDX render thread.

- Use `app.run { ... }` for thread-safe model/UI mutation from console scripts.
- In `/scene/console`, execution already happens on the render thread; nested `app.run` schedules deferred work (async). Do not assume effects are visible immediately in the same response.

### 4.1 Top-level Groovy bindings (actual runtime)
- `app`: dispatch (`run`, `exit`)
- `scene`: `GroupScene` model API
- `selection`: selection facade
- `console`: helpers (`log`, `dir`, `type`, `exception`)
- `pluginHost`, `lighting`, `shadow`, `lightingCtl`
- `camera`, `cameraTarget`, `cameraCtl`
- `status`, `unit`, `save`, `mcp`, `version`

Important: bindings like `guideManager` and `toolController` are not guaranteed in Groovy binding; scripts using them may fail with `MissingPropertyException`.

### 4.2 Binding API cheat-sheet (high-value)
- `unit`: `name()`, `size()`, `set(name,size)`, `setName(name)`, `setSize(size)`
- `save`: `path()`, `name()`, `set(path)`
- `cameraCtl`: `position()`, `target()`, `setPosition(x,y,z)`, `setTarget(x,y,z)`, `lookAt(x,y,z)`
- `lightingCtl`: `lighting()`, `shadow()`, `apply()`
- `selection`: `clear()`, `groups()`, `faces()`, `edges()`, `dimensions()`, `texts()`
- `scene` (common): `activeGroup()`, `enterGroup(group)`, `exitGroup()`, `clearAllSelections()`, `selectedGroups()`, `resetScene()`, `rootPrototype()`, `createVoxelGroup()`, `createArchitectureGroup()`

### 4.3 Discovery pattern
To keep agents robust across builds:
- run `:list` (all bindings)
- run `:list <binding>` (methods/fields)

Example:
- `GET /scene/console?cmd=:list`
- `GET /scene/console?cmd=:list scene`

## 5. Reliability Rules By Use Case
### 5.1 Coding agent (plugin development)
- Prefer plugin APIs (`Plugin`, `PluginCommand`, `PluginTool`, `PluginResult`) over internal mutable state.
- Register commands/tools; execute through command palette IDs.
- Re-discover commands each session.

### 5.2 Designer agent (vision -> model)
- Prefer world-coordinate pointer events for deterministic geometry (`worldX/Y/Z`, Y-up).
- Force camera mode explicitly (`view.camera.orbit`) before capture.
- Use command-based tool activation (`tool.builtin.*`) instead of implicit UI assumptions.

### 5.3 Plugin developer (tools/commands/entities)
- Return `PluginResult` with explicit `PluginChange` objects.
- Keep command IDs stable and namespaced.
- Avoid relying on non-bound globals inside Groovy scripts.

### 5.4 Test automation
- Start each scenario with explicit reset command (`edit.reset_scene_for_capture` when available).
- Assert preconditions via status + command existence.
- Treat screenshots as asynchronous outputs; allow capture latency and verify file freshness.

## 6. Minimum Contract Checks (Smoke)
1. `GET /mcp/status` returns `success=true` and `running=true`.
2. `GET /scene/listCommands` includes required command IDs for the test scenario.
3. `GET /scene/console?cmd=:list` includes required bindings (`app`, `scene`, `cameraCtl`, `unit`, `save`).
4. One pointer down/move/up sequence returns `success=true` and sensible world/screen fields.
5. `export.screenshot` command returns `success=true`.

## Source of Truth
- HTTP contract: `core/src/main/kotlin/com/github/alfu32/sketch/mcp/McpHttpServer.kt`
- Console bindings and MCP execution path: `core/src/main/kotlin/com/github/alfu32/sketch/Main.kt`
- Groovy runtime binding setup: `core/src/main/kotlin/com/github/alfu32/sketch/console/ConsoleGroovyRuntime.kt`
- `app.run` semantics: `core/src/main/kotlin/com/github/alfu32/sketch/console/AppFacade.kt`
- Plugin contract: `core/src/main/kotlin/com/github/alfu32/sketch/plugin/`

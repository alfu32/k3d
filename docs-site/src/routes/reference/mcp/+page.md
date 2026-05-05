# MCP Reference

MCP is Octodraw's local automation surface. It is intended for agents, local validation, deterministic scene setup, command discovery, screenshot generation, and reference refreshes. It is not required by public browser tutorials on GitHub Pages.

## Local HTTP Endpoints

Default base URL:

```text
http://127.0.0.1:8765
```

Stable endpoints documented in the repository:

- `GET /mcp/status`
- `GET /mcp/contract`
- `GET /scene/listCommands`
- `GET /scene/commands`
- `GET|POST /scene/command`
- `GET|POST /scene/console`
- `GET|POST /scene/meta`
- `GET|POST /scene/pointer`

The root `/` and `/mcp` endpoints also support MCP JSON-RPC methods such as `initialize`, `tools/list`, `tools/call`, `resources/list`, and `resources/read`.

## Command Discovery

Run `/scene/listCommands` every session. If it responds successfully but returns an empty command array, retry the alias `/scene/commands`; some local sessions expose the populated catalog there. Do not assume a command exists because it appeared in older docs. Plugin commands and plugin tools are runtime dependent.

## Command Execution

`/scene/command` accepts a command ID by query string, JSON body, or plain text. Responses include success, command ID, message, duration, and captured stdout lines.

## Console Execution

`/scene/console` executes Groovy console source or meta commands. Use `:list` and `:list <binding>` to inspect available bindings. Console execution happens on the render thread; nested `app.run` schedules deferred work.

## Pointer Input

`/scene/pointer` dispatches `down`, `move`, and `up` events. It accepts screen coordinates or world coordinates, with optional normal and validity fields. Deterministic docs automation should prefer world coordinates when possible.

## Reliable Agent Rules

- Confirm `/mcp/status` first.
- Discover commands before executing them.
- Reset the scene before capture or reference generation.
- Use `app.run` for safe model mutation from console scripts outside render-thread execution.
- Prefer discovered render-buffer capture commands for documentation images.
- Record any window or browser screenshot fallback in the generated-image manifest.

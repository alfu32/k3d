# MCP Contract

The local MCP surface is designed for reliable agents, plugin development, scripted scene generation, screenshot production, and regression workflows.

## Startup

Start the desktop application normally, then use the console alias `mcp: status` or `mcp: start` if needed. The default HTTP endpoint is:

```text
http://127.0.0.1:8765
```

## Required Checks

1. `GET /mcp/status` returns success and running state.
2. `GET /scene/listCommands` returns the command catalog for the current build, or the alias `GET /scene/commands` does if the primary endpoint returns an empty list.
3. `GET /scene/console?cmd=:list` shows available Groovy bindings.
4. Pointer events return success for the chosen coordinate path.
5. Screenshot/render commands are discovered before use.

## Endpoints

- `/mcp/status`: local server status and port.
- `/mcp/contract`: self-describing contract payload when available.
- `/scene/listCommands`: command catalog source of truth.
- `/scene/command`: execute a command ID.
- `/scene/console`: execute Groovy console source or meta commands.
- `/scene/pointer`: dispatch pointer input.

## Reliable-Agent Pattern

Reset state, verify commands, run deterministic scene setup, capture output, and record every generated artifact. If both command discovery endpoints return an empty catalog, do not proceed with command-specific docs generation.

## Modeling And Design Agents

The contract includes `agentGuidance.modelingDesignWorkflow`. Read it before using MCP for design work. The key points are:

- model in real Octodraw, not in a separate renderer,
- state unit conversion and dimensional assumptions,
- split assemblies into named object prototypes,
- use functional component names,
- pause between MCP actions,
- capture, inspect, and adjust before reporting success.

For the full procedure, see [Codex Interoperability](../codex-interoperability/).

## Public Tutorial Boundary

MCP is local automation. Public tutorials use the webcomponent and must not require the desktop app or MCP server.

# Codex Interoperability

This page describes the preferred workflow for agents that use Octodraw as a design surface through MCP. It is written for Codex sessions, but the same procedure applies to any local automation client.

The goal is not to fake a screenshot or generate geometry in an unrelated renderer. The goal is to create real Octodraw model geometry, inspect it in the running app, correct it, and capture the result through the app.

## Modeling Workflow Prompt

Use this prompt pattern for 3D design tasks:

```text
You are controlling Octodraw through its local MCP server.

First inspect the MCP contract and command catalog. Confirm `/mcp/status`, discover commands through `/scene/listCommands` and fall back to `/scene/commands` if needed. Prefer discovered command IDs over assumptions.

Interpret the reference drawing or design request in model units. State the scale conversion before modeling. If dimensions are incomplete, make conservative approximations and record them.

Model in semantic components. Do not place all generated triangles into one anonymous active group when the design has recognizable parts. Create named object prototypes for components such as housing frame, cover plate, boss, ribs, mounting foot, terminal housing, reference guide, or other functional pieces. Give each object an eloquent functional name so a user can edit or replace it from the Objects panel.

Use the running Octodraw app as the source of truth. Use MCP console scripts for deterministic generated geometry when appropriate, pointer events for tool workflows when useful, and commands for camera, panels, selection, capture, and reset. Keep 800-1600 ms pauses between state-changing operations and before screenshots.

After each meaningful modeling pass, capture or inspect the view. Prefer the app screenshot/render-buffer command discovered at runtime. If that is unavailable, use a documented window or browser screenshot fallback. Do not manually create fake screenshots.

Evaluate the image against the request. Adjust proportions, camera, and component breakdown, then capture again. Stop only when the model is actually present in Octodraw and the result has been visually checked.

When saving generated assets, record the MCP source, commands used, script used, timestamp, scale assumptions, and known limitations.
```

## Practical Sequence

1. Check `GET /mcp/status`.
2. Discover commands with `GET /scene/listCommands`; retry `GET /scene/commands` if the list is empty.
3. Reset the scene with a discovered reset command or a small console reset script.
4. Decide the unit mapping, for example `1 unit = 5 mm`.
5. Break the requested object into functional components before writing geometry.
6. Generate each component as a named object prototype and place an instance in the scene.
7. Set camera and panels deliberately.
8. Pause, capture, inspect, and correct.
9. Save the generated screenshot and manifest record.

## Component Naming

Names should explain function, not construction order. Use names like:

- `Structural outer housing frame - 98 mm maximum diameter`
- `Front cover plate and raised outer retaining lip`
- `Central shaft hub boss with 8 mm through bore`
- `Mounting foot 1 with 4.5 mm through hole on 84 mm PCD`
- `Side electrical terminal housing and cover lip`
- `Non-printing reference guides for 80 mm active disc and 84 mm mounting PCD`

Avoid names like `part1`, `ring`, `box`, or `gray mesh`. If the user later edits the model, the object list should read like an assembly tree.

## Console Geometry Notes

MCP `/scene/console` has a finite timeout. Keep scripts bounded, suppress per-primitive change notifications when possible, and split very large geometry into multiple passes. For componentized mesh generation, prefer creating `GroupScene.MeshTriangle` and `GroupScene.MeshSegment` lists, then creating object prototypes with `scene.createImportedPrototype(...)` and instances with `scene.createInstanceAtWorld(...)`.

Do not use the desktop MCP server as a dependency for public browser tutorials. MCP is a local authoring, validation, and design-control interface.

## Screenshot Rules

Use the same screenshot policy as the documentation pipeline:

- Prefer discovered render-buffer or screenshot commands such as `export.screenshot`.
- Keep the UI visible when the task benefits from showing the actual toolbars and panels.
- Avoid title bars or full-path window captures when publishing public images.
- Record fallbacks clearly if a window or browser screenshot is used.

## What MCP Publishes

The runtime `/mcp/contract` payload includes an `agentGuidance.modelingDesignWorkflow` section. Agents that do not have repository source access should read that contract first and follow the embedded procedure before generating geometry.

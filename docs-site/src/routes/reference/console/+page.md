# Console Reference

Octodraw includes a persistent Groovy console for power users and automation. It runs in the same JVM as the desktop app and is not a sandboxed end-user scripting environment.

## Threading Boundary

Mutable LibGDX state belongs to the render thread. From the interactive console, use:

```groovy
app.run {
  // mutate scene state here
}
```

MCP `/scene/console` execution already runs on the render thread in current docs, so nested `app.run` schedules asynchronous work.

## High-Value Bindings

Current MCP contract docs list bindings such as `app`, `scene`, `selection`, `console`, `cameraCtl`, `unit`, `save`, `lightingCtl`, `status`, `pluginHost`, `camera`, `cameraTarget`, `lighting`, `shadow`, `mcp`, and `version`.

Use discovery:

```text
:list
:list scene
:list cameraCtl
```

## Practical Uses

- Reset a deterministic scene.
- Move the camera to a known view.
- Set the active file path for screenshots.
- Inspect selection and model state.
- Prototype small model-building scripts before turning them into tutorials or regression cases.

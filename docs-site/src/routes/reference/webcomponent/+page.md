# Webcomponent Reference

The Gradle webcomponent bundle exposes the browser-side Octodraw editor. The current bundle contains `octodraw-element.js`, `octodraw-runtime.js`, `assets/`, `scripts/`, and a demo page. This docs site loads that bundle from `static/webcomponent/` after the deployment workflow copies `web/build/dist/webcomponent`.

## Current Bundle API

The existing component README documents `<octodraw-editor>` with methods such as `getModel`, `setModel`, `getSelection`, `clearSelection`, `getCamera`, `setCamera`, `selectTool`, `cancelTool`, `pointer`, `exec`, `play`, and `setUi`.

Only one editor instance per page is supported in the current runtime.

## Tutorial-Facing Public Contract

The following is the intended public tutorial-facing contract for future lessons. Tutorials should depend on this stable API, not on random internal DOM structure.

```ts
interface OctodrawTutorialElement extends HTMLElement {
  loadModel(source: string | object): Promise<void>
  resetScene(): Promise<void>
  runCommand(commandId: string): Promise<CommandResult>
  runScript(script: string): Promise<ConsoleResult>
  setCamera(camera: CameraPreset | CameraState): Promise<void>
  captureScreenshot(): Promise<Blob>
  highlight(selector: TutorialTarget): Promise<void>
  clearHighlights(): Promise<void>
  getState(): Promise<TutorialState>
}
```

## Browser Tutorials Versus Desktop MCP

Browser tutorials on GitHub Pages use the embedded webcomponent and must not require the desktop MCP server. MCP is separate: it is for local automation, screenshot generation, verification, command discovery, and agent workflows.

## Asset Paths

Svelte components should use `$app/paths` so the bundle loads correctly under the GitHub Pages base path `/k3d`.

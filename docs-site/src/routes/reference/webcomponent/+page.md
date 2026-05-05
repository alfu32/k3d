# Webcomponent Reference

The Gradle webcomponent bundle exposes the browser-side Octodraw editor. The current bundle contains `octodraw-element.js`, `octodraw-runtime.js`, `assets/`, `scripts/`, and a demo page. This docs site loads that bundle from `static/webcomponent/` after the deployment workflow copies `web/build/dist/webcomponent`.

## Current Bundle API

The existing component README documents `<octodraw-editor>` with this current API:

```ts
interface OctodrawEditorElement extends HTMLElement {
  value: string | null
  controller: OctodrawEditorElement

  whenReady(): Promise<void>
  getModel(): Promise<string>
  setModel(model: string | object, fileName?: string): Promise<object>
  getSelection(): Promise<object>
  clearSelection(): Promise<object>
  getCamera(): Promise<object>
  setCamera(options: CameraState): Promise<object>
  selectTool(tool: string): Promise<object>
  cancelTool(): Promise<object>
  pointer(event: PointerCommand): Promise<object>
  exec(command: WebcomponentCommand, payload?: object): Promise<object>
  play(steps: Array<{ cmd: WebcomponentCommand } & object>): Promise<object>
  setUi(options: { toolbarsVisible?: boolean; panelsVisible?: boolean }): Promise<object>
}
```

Supported low-level webcomponent commands in this build are `model.get`, `model.set`, `selection.get`, `selection.clear`, `ui.setToolbars`, `ui.setPanels`, `ui.setVisibility`, `camera.get`, `camera.set`, `tool.select`, `tool.cancel`, and `tool.pointer`.

Only one active editor instance per browser document is supported in the current runtime. SvelteKit navigation can replace pages without reloading the document, so the component wrapper must clear stale host state when an editor element disconnects.

## Tutorial-Facing Public Contract

The docs-site `OctodrawEmbed.svelte` component adapts tutorial JSON actions onto the current API. The stable tutorial-facing contract is therefore a wrapper over the real `<octodraw-editor>` methods, not a separate DOM contract:

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

Current browser support maps `tool.builtin.<id>` actions to `selectTool()`, camera actions to `setCamera()`, load-scene actions to `setModel()`, and browser canvas capture to `HTMLCanvasElement.toBlob()`. Desktop-only actions such as Groovy console scripts, command palette panel commands, and local file-system screenshots must be executed through MCP instead.

## Browser Tutorials Versus Desktop MCP

Browser tutorials on GitHub Pages use the embedded webcomponent and must not require the desktop MCP server. MCP is separate: it is for local automation, screenshot generation, verification, command discovery, and agent workflows.

## Asset Paths

Svelte components should use `$app/paths` so the bundle loads correctly under the GitHub Pages base path `/k3d`.

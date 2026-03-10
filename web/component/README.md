# Octodraw Web Component

This bundle exposes an embeddable `<octodraw-editor>` custom element.

## Files

- `octodraw-element.js` - custom element wrapper
- `octodraw-runtime.js` - TeaVM/libGDX runtime
- `assets/` - runtime assets
- `scripts/` - web runtime support scripts
- `demo.html` - simple integration example

## Usage

Serve the extracted bundle over HTTP. Do not open `demo.html` or the runtime directly with `file://`.

Example:

```bash
python3 -m http.server 8000
```

Then open `http://127.0.0.1:8000/demo.html`.

```html
<script src="./octodraw-element.js"></script>
<octodraw-editor id="editor"></octodraw-editor>
<script>
  const editor = document.getElementById('editor')
  editor.value = '{"version":15}'
  editor.addEventListener('ready', async () => {
    await editor.setUi({ toolbarsVisible: true, panelsVisible: true })
  })
  editor.addEventListener('change', (event) => {
    console.log('Model changed', event.detail)
  })
</script>
```

## Current API

- `editor.getModel(): Promise<string>`
- `editor.setModel(model, fileName?): Promise<object>`
- `editor.getSelection(): Promise<object>`
- `editor.clearSelection(): Promise<object>`
- `editor.getCamera(): Promise<object>`
- `editor.setCamera(options): Promise<object>`
- `editor.selectTool(tool): Promise<object>`
- `editor.cancelTool(): Promise<object>`
- `editor.pointer(event): Promise<object>`
- `editor.exec(command, payload?): Promise<object>`
- `editor.play(steps): Promise<object>`
- `editor.setUi({ toolbarsVisible?, panelsVisible? }): Promise<object>`
- `editor.value` property for initial value / last known model

## Supported commands in this first slice

- `model.get`
- `model.set`
- `selection.get`
- `selection.clear`
- `ui.setToolbars`
- `ui.setPanels`
- `ui.setVisibility`
- `camera.get`
- `camera.set`
- `tool.select`
- `tool.cancel`
- `tool.pointer`

Only one editor instance per page is supported in the current runtime.

## Events

- `ready`
- `change`
- `selectionchange`
- `toolchange`
- `error`

## Simple scripted construction example

```js
await editor.selectTool('LINE')
await editor.pointer({ action: 'click', world: [0, 0, 0] })
await editor.pointer({ action: 'click', world: [4, 0, 0] })
```

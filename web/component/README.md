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
  editor.value = JSON.stringify({
    version: 17,
    segments: [],
    faces: [],
    prototypes: [
      {
        id: 'root',
        name: 'Root',
        definitionOrigin: {},
        definitionAxisU: { x: 1 },
        definitionAxisV: { y: 1 },
        definitionAxisW: { z: 1 },
        gluedToSurface: false,
        kind: 'MESH',
        externalReferenceEnabled: false,
        externalReferencePath: '',
        voxelColor: { r: 0.8, g: 0.8, b: 0.8 },
        voxels: [],
        architectureWalls: [],
        architectureSlabs: [],
        architectureStairs: [],
        architectureFrames: [],
        hvacPlumbingRuns: [],
        hvacVentilationDucts: [],
        hotspots: [],
        prototypeVertices: [],
        segments: [],
        faces: [],
        dimensions: [],
        texts: []
      }
    ],
    rootInstance: {
      id: 'root',
      prototypeId: 'root',
      instanceOrigin: {},
      instanceAxisU: { x: 1 },
      instanceAxisV: { y: 1 },
      instanceAxisW: { z: 1 },
      hotspotPositions: [],
      hotspotSegmentAttachments: [],
      hotspotTriangleAttachments: [],
      overrideSegments: [],
      overrideFaces: [],
      overrideDimensions: [],
      overrideTexts: [],
      children: []
    },
    cameraState: {
      position: { x: 6, y: 6, z: 6 },
      direction: { x: -0.57735026, y: -0.57735026, z: -0.57735026 },
      up: { x: -0.40824834, y: 0.8164967, z: -0.40824834 },
      target: {},
      near: 0.1,
      far: 500,
      fieldOfView: 67
    },
    lightingState: {
      shadowLightValue: 0.59,
      shadowLightAlpha: 0.5,
      directionalLightValue: 0.73,
      directionalLightAlpha: 1,
      ambientLightValue: 0.59,
      ambientLightAlpha: 1,
      specularLightValue: 0.2,
      specularLightAlpha: 0.95
    },
    shadowState: {
      shadowBias: 2500,
      shadowNormalBias: 5620,
      pcfMode: 1,
      dither: false,
      useCsm: true
    },
    modelUnit: {},
    snapEpsilon: 12,
    gridSpacing: 1,
    circleSegments: 24,
    undoHistory: {
      maxEntries: 20,
      index: 0,
      entries: []
    }
  })
  editor.addEventListener('ready', async () => {
    await editor.setUi({ toolbarsVisible: true, panelsVisible: true })
  })
  editor.addEventListener('change', (event) => {
    console.log('Model changed', event.detail)
  })
</script>
```

Do not seed the editor with a version-only payload such as `{"version":15}`. The current loader treats missing root axes as zero vectors, which can leave the scene graph with a non-invertible root transform.

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

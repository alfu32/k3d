<script lang="ts">
  import { base } from '$app/paths';
  import { onMount, tick } from 'svelte';
  import type { TutorialTarget } from '$lib/tutorial/tutorial_schema';
  import type { CommandResult, ConsoleResult } from '$lib/tutorial/tutorial_runtime';

  export let tutorial: string | undefined = undefined;
  export let height = '560px';
  export let initialScene = 'empty';

  let mounted = false;
  let loaded = false;
  let failed = false;
  let message = 'Loading Octodraw webcomponent...';
  type EditorElement = HTMLElement & {
    value?: string;
    getModel?: () => Promise<string>;
    setModel?: (model: string, fileName?: string) => Promise<object>;
    getSelection?: () => Promise<object>;
    clearSelection?: () => Promise<object>;
    getCamera?: () => Promise<object>;
    setCamera?: (options: Record<string, unknown>) => Promise<object>;
    selectTool?: (tool: string) => Promise<object>;
    cancelTool?: () => Promise<object>;
    pointer?: (event: Record<string, unknown>) => Promise<object>;
    exec?: (command: string, payload?: Record<string, unknown>) => Promise<object>;
    play?: (steps: Record<string, unknown>[]) => Promise<object>;
    setUi?: (options: { toolbarsVisible?: boolean; panelsVisible?: boolean }) => Promise<object>;
    whenReady?: () => Promise<void>;
  };

  let editorElement: EditorElement;
  let highlightMessage = '';

  const scriptId = 'octodraw-webcomponent-script';
  const EMPTY_SCENE_MODEL = JSON.stringify({
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
  });

  function scriptUrl(): string {
    return `${base}/webcomponent/octodraw-element.js`;
  }

  function initialModel(): string {
    if (initialScene === 'empty') {
      return EMPTY_SCENE_MODEL;
    }
    return initialScene;
  }

  async function applyInitialState() {
    if (!editorElement) {
      return;
    }
    editorElement.value = initialModel();
    try {
      await editorElement.whenReady?.();
      await editorElement.setUi?.({ toolbarsVisible: true, panelsVisible: true });
    } catch (error) {
      failed = true;
      message = error instanceof Error ? error.message : String(error);
    }
  }

  async function currentEditor(): Promise<EditorElement> {
    if (failed) {
      throw new Error(message);
    }
    if (!loaded) {
      await loadScript();
      loaded = true;
    }
    await tick();
    if (!editorElement) {
      throw new Error('Octodraw editor element is not mounted yet.');
    }
    await editorElement.whenReady?.();
    return editorElement;
  }

  function toolNameFromCommand(commandId: string): string | null {
    if (!commandId.startsWith('tool.builtin.')) {
      return null;
    }
    return commandId
      .slice('tool.builtin.'.length)
      .trim()
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '');
  }

  function cameraStateForPreset(preset: string | Record<string, unknown>): Record<string, unknown> {
    if (typeof preset !== 'string') {
      return preset;
    }
    if (preset === 'top') {
      return {
        mode: 'ORTHOGRAPHIC',
        position: { x: 0, y: 18, z: 0 },
        target: { x: 0, y: 0, z: 0 },
        orthoDistance: 18
      };
    }
    return {
      mode: 'ORBIT',
      position: { x: 14, y: 10, z: 14 },
      target: { x: 0, y: 0, z: 0 }
    };
  }

  function unsupportedBrowserCommand(commandId: string): CommandResult {
    return {
      success: false,
      message: `${commandId} is not exposed by the current browser webcomponent API. Use desktop MCP for this command.`
    };
  }

  export async function loadModel(source: string | object): Promise<void> {
    const editor = await currentEditor();
    const model = typeof source === 'string' ? source : JSON.stringify(source);
    if (!editor.setModel) {
      throw new Error('Current webcomponent does not expose setModel().');
    }
    await editor.setModel(model, `${tutorial ?? 'tutorial'}.octd`);
  }

  export async function resetScene(): Promise<void> {
    await loadModel(EMPTY_SCENE_MODEL);
  }

  export async function runCommand(commandId: string): Promise<CommandResult> {
    const editor = await currentEditor();
    const toolName = toolNameFromCommand(commandId);
    if (toolName) {
      if (!editor.selectTool) {
        return unsupportedBrowserCommand(commandId);
      }
      await editor.selectTool(toolName);
      return { success: true, message: `Selected ${toolName}.` };
    }
    if (commandId === 'view.camera.orbit') {
      await editor.setCamera?.(cameraStateForPreset('orbit-overview'));
      return { success: true, message: 'Set orbit camera preset.' };
    }
    if (commandId === 'view.camera.orthographic' || commandId === 'view.ortho.top') {
      await editor.setCamera?.(cameraStateForPreset('top'));
      return { success: true, message: 'Set top orthographic camera preset.' };
    }
    if (commandId === 'selection.clear') {
      await editor.clearSelection?.();
      return { success: true, message: 'Cleared selection.' };
    }
    if (commandId === 'export.screenshot') {
      await captureScreenshot();
      return { success: true, message: 'Captured the current browser canvas as a Blob.' };
    }
    return unsupportedBrowserCommand(commandId);
  }

  export async function runScript(script: string): Promise<ConsoleResult> {
    return {
      success: false,
      message: 'The browser webcomponent does not expose the desktop Groovy console. Use local MCP for scripts.',
      outputLines: [script]
    };
  }

  export async function setCamera(camera: string | Record<string, unknown>): Promise<void> {
    const editor = await currentEditor();
    if (!editor.setCamera) {
      throw new Error('Current webcomponent does not expose setCamera().');
    }
    await editor.setCamera(cameraStateForPreset(camera));
  }

  export async function captureScreenshot(): Promise<Blob> {
    const editor = await currentEditor();
    const canvas = editor.querySelector('canvas');
    if (!(canvas instanceof HTMLCanvasElement)) {
      throw new Error('No webcomponent canvas is available for capture.');
    }
    return new Promise((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (blob) {
          resolve(blob);
        } else {
          reject(new Error('Browser canvas capture returned an empty Blob.'));
        }
      }, 'image/png');
    });
  }

  export async function highlight(selector: TutorialTarget): Promise<void> {
    highlightMessage = selector.label ?? selector.selector;
  }

  export async function clearHighlights(): Promise<void> {
    highlightMessage = '';
  }

  export async function getState(): Promise<unknown> {
    const editor = await currentEditor();
    const [model, selection, camera] = await Promise.all([
      editor.getModel?.(),
      editor.getSelection?.(),
      editor.getCamera?.()
    ]);
    return { model, selection, camera };
  }

  function handleRuntimeError(event: Event) {
    const detail = (event as CustomEvent<{ message?: string }>).detail;
    failed = true;
    message = detail?.message || 'Octodraw webcomponent runtime reported an error.';
  }

  function loadScript(): Promise<void> {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
      return Promise.resolve();
    }
    if (customElements.get('octodraw-editor')) {
      return Promise.resolve();
    }
    const existing = document.getElementById(scriptId) as HTMLScriptElement | null;
    if (existing) {
      if (existing.dataset.loaded === 'true') {
        return Promise.resolve();
      }
      return new Promise((resolve, reject) => {
        existing.addEventListener(
          'load',
          () => {
            existing.dataset.loaded = 'true';
            resolve();
          },
          { once: true }
        );
        existing.addEventListener('error', () => reject(new Error('Failed to load existing Octodraw script.')), {
          once: true
        });
      });
    }
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.id = scriptId;
      script.src = scriptUrl();
      script.async = true;
      script.onload = () => {
        script.dataset.loaded = 'true';
        resolve();
      };
      script.onerror = () => reject(new Error(`Failed to load ${script.src}`));
      document.head.appendChild(script);
    });
  }

  onMount(async () => {
    mounted = true;
    try {
      await loadScript();
      loaded = true;
      message = 'Octodraw webcomponent loaded.';
      await tick();
      await applyInitialState();
    } catch (error) {
      failed = true;
      message = error instanceof Error ? error.message : String(error);
    }
  });
</script>

<div class="embed" style={`--embed-height: ${height};`}>
  {#if failed}
    <div class="embed-message failed" role="alert">
      {message}
      <span>The deployment workflow copies the Gradle webcomponent bundle into this folder.</span>
    </div>
  {:else if mounted && loaded}
    <svelte:element
      this="octodraw-editor"
      bind:this={editorElement}
      on:error={handleRuntimeError}
      data-tutorial={tutorial}
      data-initial-scene={initialScene}
      toolbars-visible="true"
      panels-visible="true"
    />
    {#if highlightMessage}
      <div class="highlight-label">{highlightMessage}</div>
    {/if}
  {:else}
    <div class="embed-message" role="status">
      {message}
    </div>
  {/if}
</div>

<style>
  .embed {
    position: relative;
    min-height: var(--embed-height);
    border: 1px solid var(--border);
    border-radius: 8px;
    overflow: hidden;
    background: #111820;
  }

  octodraw-editor {
    display: block;
    min-height: var(--embed-height);
    height: var(--embed-height);
  }

  .embed-message {
    display: grid;
    min-height: var(--embed-height);
    place-items: center;
    padding: 1rem;
    color: #d9e2ec;
    text-align: center;
  }

  .embed-message span {
    display: block;
    margin-top: 0.4rem;
    color: #aeb8c4;
    font-size: 0.92rem;
  }

  .failed {
    background: #27151a;
    color: #ffd9df;
  }

  .highlight-label {
    position: absolute;
    right: 1rem;
    bottom: 1rem;
    max-width: min(18rem, calc(100% - 2rem));
    padding: 0.4rem 0.55rem;
    border: 1px solid rgba(255, 255, 255, 0.45);
    border-radius: 6px;
    background: rgba(15, 23, 32, 0.86);
    color: white;
    font-size: 0.9rem;
  }
</style>

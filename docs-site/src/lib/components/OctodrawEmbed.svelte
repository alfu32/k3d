<script lang="ts">
  import { base } from '$app/paths';
  import { onMount } from 'svelte';

  export let tutorial: string | undefined = undefined;
  export let height = '560px';
  export let initialScene = 'empty';

  let mounted = false;
  let loaded = false;
  let failed = false;
  let message = 'Loading Octodraw webcomponent...';
  let editorElement: HTMLElement & {
    value?: string;
    setModel?: (model: string, fileName?: string) => Promise<object>;
    setUi?: (options: { toolbarsVisible?: boolean; panelsVisible?: boolean }) => Promise<object>;
    whenReady?: () => Promise<void>;
  };

  const scriptId = 'octodraw-webcomponent-script';

  function scriptUrl(): string {
    return `${base}/webcomponent/octodraw-element.js`;
  }

  function initialModel(): string {
    if (initialScene === 'empty') {
      return JSON.stringify({ version: 15 });
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
      if (editorElement.setModel) {
        await editorElement.setModel(initialModel(), `${tutorial ?? 'tutorial'}.octd`);
      }
    } catch (error) {
      message = error instanceof Error ? error.message : String(error);
    }
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
      return new Promise((resolve, reject) => {
        existing.addEventListener('load', () => resolve(), { once: true });
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
      script.onload = () => resolve();
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
      await applyInitialState();
    } catch (error) {
      failed = true;
      message = error instanceof Error ? error.message : String(error);
    }
  });
</script>

<div class="embed" style={`--embed-height: ${height};`}>
  {#if mounted && loaded}
    <svelte:element
      this="octodraw-editor"
      bind:this={editorElement}
      data-tutorial={tutorial}
      data-initial-scene={initialScene}
      toolbars-visible="true"
      panels-visible="true"
    />
  {:else}
    <div class:failed class="embed-message" role={failed ? 'alert' : 'status'}>
      {message}
      {#if failed}
        <span>The deployment workflow copies the Gradle webcomponent bundle into this folder.</span>
      {/if}
    </div>
  {/if}
</div>

<style>
  .embed {
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
</style>

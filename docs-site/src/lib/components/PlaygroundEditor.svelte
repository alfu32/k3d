<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import OctodrawEmbed from './OctodrawEmbed.svelte';

  type EmbedApi = {
    getState: () => Promise<unknown>;
    loadModel: (source: string | object) => Promise<void>;
  };

  const storageKey = 'octodraw.docs.playground.model.v1';
  const savedAtKey = 'octodraw.docs.playground.savedAt.v1';
  const fileName = 'octodraw-playground.octd';

  let embed: EmbedApi | null = null;
  let ready = false;
  let initialScene = 'empty';
  let status = 'Loading playground...';
  let savedAt = '';
  let storageEstimate = '';
  let saveTimer: number | undefined;
  let lastSavedModel = '';

  function savedAtLabel(value: string): string {
    if (!value) {
      return 'not saved yet';
    }
    return new Date(value).toLocaleString();
  }

  function modelFromState(state: unknown): string | null {
    if (!state || typeof state !== 'object') {
      return null;
    }
    const model = (state as { model?: unknown }).model;
    return typeof model === 'string' ? model : null;
  }

  async function updateStorageEstimate() {
    if (typeof navigator === 'undefined' || !navigator.storage?.estimate) {
      storageEstimate = 'storage quota unavailable';
      return;
    }
    const estimate = await navigator.storage.estimate();
    const quota = estimate.quota ? estimate.quota / (1024 * 1024) : 0;
    const usage = estimate.usage ? estimate.usage / (1024 * 1024) : 0;
    storageEstimate = quota > 0
      ? `${usage.toFixed(1)} MB used of ${quota.toFixed(0)} MB available`
      : `${usage.toFixed(1)} MB used`;
  }

  async function saveNow() {
    if (typeof localStorage === 'undefined' || !embed) {
      return;
    }
    try {
      const state = await embed.getState();
      const model = modelFromState(state);
      if (model == null || model === lastSavedModel) {
        return;
      }
      localStorage.setItem(storageKey, model);
      savedAt = new Date().toISOString();
      localStorage.setItem(savedAtKey, savedAt);
      lastSavedModel = model;
      status = `Saved ${Math.round(model.length / 1024)} KB locally.`;
      await updateStorageEstimate();
    } catch (error) {
      status = error instanceof Error ? `Autosave failed: ${error.message}` : 'Autosave failed.';
    }
  }

  async function restoreSavedModel() {
    if (typeof localStorage === 'undefined' || !embed) {
      return;
    }
    const model = localStorage.getItem(storageKey);
    if (!model) {
      status = 'No saved local model.';
      return;
    }
    await embed.loadModel(model);
    lastSavedModel = model;
    status = 'Restored the locally saved model.';
  }

  async function clearSavedModel() {
    if (typeof localStorage === 'undefined') {
      return;
    }
    localStorage.removeItem(storageKey);
    localStorage.removeItem(savedAtKey);
    savedAt = '';
    lastSavedModel = '';
    status = 'Cleared the saved playground model. Reload the page for a blank session.';
    await updateStorageEstimate();
  }

  async function downloadModel() {
    if (!embed || typeof document === 'undefined') {
      return;
    }
    const state = await embed.getState();
    const model = modelFromState(state);
    if (!model) {
      status = 'No model available to download yet.';
      return;
    }
    const blob = new Blob([model], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(url);
    status = 'Downloaded the current playground model.';
  }

  function handlePageHide() {
    void saveNow();
  }

  onMount(() => {
    if (typeof localStorage !== 'undefined') {
      const saved = localStorage.getItem(storageKey);
      if (saved) {
        initialScene = saved;
        lastSavedModel = saved;
        status = `Restoring ${Math.round(saved.length / 1024)} KB from local storage.`;
      } else {
        status = 'Blank playground session.';
      }
      savedAt = localStorage.getItem(savedAtKey) ?? '';
    }
    ready = true;
    void updateStorageEstimate();
    saveTimer = window.setInterval(() => void saveNow(), 5000);
    window.addEventListener('pagehide', handlePageHide);
  });

  onDestroy(() => {
    if (saveTimer !== undefined) {
      window.clearInterval(saveTimer);
    }
    if (typeof window !== 'undefined') {
      window.removeEventListener('pagehide', handlePageHide);
    }
    void saveNow();
  });
</script>

<section class="playground">
  <div class="playground-toolbar" aria-label="Playground controls">
    <div>
      <h1>Playground</h1>
      <p>{status} Last save: {savedAtLabel(savedAt)}. {storageEstimate}</p>
    </div>
    <div class="actions">
      <button type="button" on:click={saveNow}>Save now</button>
      <button type="button" on:click={restoreSavedModel}>Restore</button>
      <button type="button" on:click={downloadModel}>Download</button>
      <button type="button" on:click={clearSavedModel}>Clear saved</button>
    </div>
  </div>

  <div class="playground-stage">
    {#if ready}
      <OctodrawEmbed
        bind:this={embed}
        tutorial="playground"
        {initialScene}
        height="100%"
      />
    {/if}
  </div>
</section>

<style>
  .playground {
    display: grid;
    height: calc(100dvh - var(--site-header-height, 64px));
    min-height: 720px;
    grid-template-rows: auto minmax(0, 1fr);
    gap: 0;
  }

  .playground-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    padding: 0.65rem clamp(0.75rem, 2vw, 1.25rem);
    border-bottom: 1px solid var(--border);
    background: var(--panel);
  }

  .playground-toolbar h1 {
    margin: 0;
    font-size: 1.25rem;
    line-height: 1.2;
  }

  .playground-toolbar p {
    margin: 0.15rem 0 0;
    color: var(--muted);
    font-size: 0.86rem;
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 0.4rem;
  }

  button {
    padding: 0.38rem 0.55rem;
    border: 1px solid var(--border);
    border-radius: 6px;
    background: var(--panel-soft);
    color: var(--text);
    cursor: pointer;
  }

  button:hover,
  button:focus-visible {
    border-color: var(--accent);
  }

  .playground-stage {
    min-width: 0;
    min-height: 0;
    overflow: hidden;
  }

  @media (max-width: 760px) {
    .playground {
      height: auto;
      min-height: calc(100dvh - var(--site-header-height, 64px));
    }

    .playground-toolbar {
      align-items: flex-start;
      flex-direction: column;
    }

    .playground-stage {
      height: 78dvh;
      min-height: 560px;
    }
  }
</style>

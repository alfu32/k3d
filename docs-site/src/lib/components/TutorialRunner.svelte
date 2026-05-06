<script lang="ts">
  import { browser } from '$app/environment';
  import CommandPanel from './CommandPanel.svelte';
  import OctodrawEmbed from './OctodrawEmbed.svelte';
  import TutorialStepList from './TutorialStepList.svelte';
  import type { Tutorial, TutorialAction, TutorialStep } from '$lib/tutorial/tutorial_schema';
  import { createTutorialRuntime, type CommandResult, type OctodrawTutorialHost } from '$lib/tutorial/tutorial_runtime';
  import { createTutorialStore } from '$lib/tutorial/tutorial_store';

  export let tutorialUrl: string;
  export let height = '768px';

  const store = createTutorialStore();
  let tutorial: Tutorial | null = null;
  let currentIndex = 0;
  let loading = true;
  let error = '';
  let embed: Partial<OctodrawTutorialHost> | null = null;
  let loadToken = 0;

  $: currentStep = tutorial?.steps[currentIndex] as TutorialStep | undefined;

  function selectStep(index: number) {
    if (!tutorial) {
      return;
    }
    currentIndex = Math.min(Math.max(index, 0), tutorial.steps.length - 1);
    store.setCurrentStep(currentIndex);
  }

  async function runTutorialAction(action: TutorialAction): Promise<CommandResult> {
    if (!embed) {
      return { success: false, message: 'The Octodraw webcomponent is not mounted yet.' };
    }
    const runtime = createTutorialRuntime(embed);
    return runtime.executeAction(action);
  }

  async function loadTutorial(url: string) {
    const token = ++loadToken;
    loading = true;
    error = '';
    tutorial = null;
    currentIndex = 0;
    embed = null;
    store.setCurrentStep(0);

    try {
      const nextTutorial = await store.load(url);
      if (token !== loadToken) {
        return;
      }
      tutorial = nextTutorial;
      currentIndex = 0;
      store.setCurrentStep(0);
    } catch (err) {
      if (token !== loadToken) {
        return;
      }
      error = err instanceof Error ? err.message : String(err);
    } finally {
      if (token === loadToken) {
        loading = false;
      }
    }
  }

  $: if (browser && tutorialUrl) {
    void loadTutorial(tutorialUrl);
  }
</script>

{#key tutorialUrl}
  {#if loading}
    <p class="status-note">Loading tutorial...</p>
  {:else if error}
    <p class="status-note" role="alert">Tutorial failed to load: {error}</p>
  {:else if tutorial && currentStep}
    <section class="runner">
      <aside class="steps">
        <p class="meta">{tutorial.level} · {tutorial.estimatedMinutes} min</p>
        <TutorialStepList steps={tutorial.steps} {currentIndex} on:select={(event) => selectStep(event.detail)} />
      </aside>

      <div class="stage">
        {#key `${tutorial.id}:${tutorial.initialScene}:${height}`}
          <OctodrawEmbed bind:this={embed} tutorial={tutorial.id} initialScene={tutorial.initialScene} {height} />
        {/key}
        <div class="step-body">
          <p class="step-count">Step {currentIndex + 1} of {tutorial.steps.length}</p>
          <h2>{currentStep.title}</h2>
          <p>{currentStep.body}</p>
          {#if currentStep.notes}
            <p class="notes">{currentStep.notes}</p>
          {/if}
          <div class="controls">
            <button type="button" on:click={() => selectStep(currentIndex - 1)} disabled={currentIndex === 0}>
              Previous
            </button>
            <button
              type="button"
              on:click={() => selectStep(currentIndex + 1)}
              disabled={currentIndex === tutorial.steps.length - 1}
            >
              Next
            </button>
          </div>
        </div>

        <CommandPanel actions={currentStep.actions ?? []} runAction={runTutorialAction} />
      </div>
    </section>
  {/if}
{/key}

<style>
  .runner {
    display: grid;
    grid-template-columns: 240px minmax(0, 1fr);
    gap: 1rem;
    margin: 1.2rem 0;
    min-width: 0;
    width: 100%;
    overflow-x: auto;
  }

  .steps,
  .step-body {
    padding: 0.9rem;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--panel);
    min-width: 0;
  }

  .stage {
    display: grid;
    gap: 1rem;
    min-width: 0;
    width: 100%;
    min-width: 1024px;
  }

  .meta,
  .step-count,
  .notes {
    margin: 0 0 0.5rem;
    color: var(--muted);
    font-size: 0.92rem;
  }

  h2 {
    margin-top: 0;
  }

  .controls {
    display: flex;
    gap: 0.55rem;
    margin-top: 1rem;
  }

  button {
    padding: 0.5rem 0.8rem;
    border: 1px solid var(--border);
    border-radius: 6px;
    background: var(--panel-soft);
    cursor: pointer;
  }

  button:disabled {
    cursor: not-allowed;
    opacity: 0.55;
  }

  @media (max-width: 900px) {
    .runner {
      grid-template-columns: 1fr;
    }
  }
</style>

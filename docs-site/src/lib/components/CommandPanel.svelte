<script lang="ts">
  import type { TutorialAction } from '$lib/tutorial/tutorial_schema';
  import type { CommandResult } from '$lib/tutorial/tutorial_runtime';

  export let actions: TutorialAction[] = [];
  export let runAction: ((action: TutorialAction) => Promise<CommandResult>) | undefined = undefined;

  let lastMessage = 'Step actions execute against the browser webcomponent when supported.';
  let runningIndex = -1;
  let lastSuccess: boolean | null = null;

  function labelFor(action: TutorialAction): string {
    if (action.type === 'command') {
      return action.commandId;
    }
    if (action.type === 'script') {
      return 'Tutorial script';
    }
    if (action.type === 'camera') {
      return action.preset ?? 'Camera state';
    }
    if (action.type === 'loadScene') {
      return action.scene;
    }
    if (action.type === 'highlight') {
      return action.target.label ?? action.target.kind;
    }
    return 'No action';
  }

  async function run(action: TutorialAction, index: number) {
    if (!runAction) {
      lastSuccess = false;
      lastMessage = 'No tutorial runtime is attached.';
      return;
    }
    runningIndex = index;
    lastSuccess = null;
    lastMessage = `Running ${action.type} action...`;
    try {
      const result = await runAction(action);
      lastSuccess = result.success;
      lastMessage = result.message ?? (result.success ? 'Action completed.' : 'Action failed.');
    } catch (error) {
      lastSuccess = false;
      lastMessage = error instanceof Error ? error.message : String(error);
    } finally {
      runningIndex = -1;
    }
  }
</script>

<section class="panel" aria-label="Tutorial command panel">
  <h3>Command Panel</h3>
  {#if actions.length === 0}
    <p>No step action is defined.</p>
  {:else}
    <ul>
      {#each actions as action, index}
        <li>
          <div>
            <strong>{action.type}</strong>
            <span>{labelFor(action)}</span>
            {#if action.description}
              <small>{action.description}</small>
            {/if}
          </div>
          <button type="button" on:click={() => run(action, index)} disabled={runningIndex !== -1}>
            {runningIndex === index ? 'Running' : 'Run'}
          </button>
        </li>
      {/each}
    </ul>
  {/if}
  <p class:success={lastSuccess === true} class:failure={lastSuccess === false} class="status">{lastMessage}</p>
</section>

<style>
  .panel {
    padding: 0.9rem;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--panel);
  }

  h3 {
    margin: 0 0 0.7rem;
    font-size: 1rem;
  }

  ul {
    display: grid;
    gap: 0.5rem;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  li {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 0.6rem;
    align-items: center;
    padding-bottom: 0.5rem;
    border-bottom: 1px solid var(--border);
  }

  strong,
  span,
  small {
    display: block;
  }

  span,
  small,
  .status {
    color: var(--muted);
  }

  button {
    padding: 0.35rem 0.65rem;
    border: 1px solid var(--border);
    border-radius: 6px;
    background: var(--panel-soft);
    cursor: pointer;
  }

  button:disabled {
    cursor: wait;
    opacity: 0.7;
  }

  .success {
    color: #17613a;
  }

  .failure {
    color: #a13535;
  }
</style>

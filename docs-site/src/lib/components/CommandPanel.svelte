<script lang="ts">
  import type { TutorialAction } from '$lib/tutorial/tutorial_schema';

  export let actions: TutorialAction[] = [];

  let lastMessage = 'Command execution is stubbed until the webcomponent tutorial API is wired.';

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

  function runStub(action: TutorialAction) {
    lastMessage = `Prepared ${action.type} action. Runtime execution is pending webcomponent API integration.`;
  }
</script>

<section class="panel" aria-label="Tutorial command panel">
  <h3>Command Panel</h3>
  {#if actions.length === 0}
    <p>No step action is defined.</p>
  {:else}
    <ul>
      {#each actions as action}
        <li>
          <div>
            <strong>{action.type}</strong>
            <span>{labelFor(action)}</span>
            {#if action.description}
              <small>{action.description}</small>
            {/if}
          </div>
          <button type="button" on:click={() => runStub(action)}>Queue</button>
        </li>
      {/each}
    </ul>
  {/if}
  <p class="status">{lastMessage}</p>
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
</style>

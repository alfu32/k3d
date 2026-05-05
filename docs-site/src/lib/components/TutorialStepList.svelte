<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import type { TutorialStep } from '$lib/tutorial/tutorial_schema';

  export let steps: TutorialStep[] = [];
  export let currentIndex = 0;

  const dispatch = createEventDispatcher<{ select: number }>();
</script>

<ol class="step-list" aria-label="Tutorial steps">
  {#each steps as step, index}
    <li>
      <button
        type="button"
        class:active={index === currentIndex}
        aria-current={index === currentIndex ? 'step' : undefined}
        on:click={() => dispatch('select', index)}
      >
        <span>{index + 1}</span>
        <strong>{step.title}</strong>
      </button>
    </li>
  {/each}
</ol>

<style>
  .step-list {
    display: grid;
    gap: 0.3rem;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  button {
    display: grid;
    width: 100%;
    grid-template-columns: 2rem 1fr;
    gap: 0.55rem;
    align-items: center;
    padding: 0.45rem 0.55rem;
    border: 1px solid transparent;
    border-radius: 6px;
    background: transparent;
    color: var(--text);
    text-align: left;
    cursor: pointer;
  }

  button:hover,
  button:focus-visible,
  button.active {
    border-color: var(--border);
    background: var(--panel-soft);
  }

  span {
    display: grid;
    width: 1.6rem;
    height: 1.6rem;
    place-items: center;
    border-radius: 999px;
    background: var(--code);
    color: white;
    font-size: 0.8rem;
  }

  strong {
    font-size: 0.92rem;
  }
</style>

<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Push/Pull House

This lesson sketches a small house-like massing workflow. It is intentionally lightweight and avoids claiming full architecture CAD behavior.

<ToolbarIconGuide
  title="Buttons used in this lesson"
  items={[
    { name: 'select', label: 'Select', hint: 'Choose the face or roof plane before changing it.' },
    { name: 'rectangle', label: 'Rectangle', hint: 'Draw the base mass footprint.' },
    { name: 'push_pull', label: 'Push/Pull', hint: 'Raise the walls and shape the roof volumes.' },
    { name: 'move', label: 'Move', hint: 'Translate the body if the composition needs to shift.' }
  ]}
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/push-pull-house.json`} />

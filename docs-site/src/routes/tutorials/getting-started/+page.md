<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Getting Started

This tutorial introduces the viewport, command panel, and first rectangle workflow. The embedded editor loads from the static webcomponent bundle when the site is built with the Gradle webcomponent artifact.

<ToolbarIconGuide
  title="Buttons used in this lesson"
  items={[
    { name: 'select', label: 'Select', hint: 'Use this when the lesson asks you to inspect the scene.' },
    { name: 'camera_rotating_operation', label: 'Orbit', hint: 'Move around the scene without changing the model.' },
    { name: 'rectangle', label: 'Rectangle', hint: 'Draw the first face on the grid.' },
    { name: 'file_save', label: 'Save', hint: 'Keep the model once the first shape is complete.' }
  ]}
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/getting-started.json`} />

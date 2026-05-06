<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Snapping Basics

Practice the snapping workflow used by most direct modeling tasks.

<ToolbarIconGuide
  title="Buttons used in this lesson"
  items={[
    { name: 'select', label: 'Select', hint: 'Choose the edge or point you want to snap from.' },
    { name: 'construction_line', label: 'Construction Line', hint: 'Lay out a temporary guide line.' },
    { name: 'grid_helper_planar', label: 'Grid Guide', hint: 'Work with the visible grid helper.' },
    { name: 'camera_orthographic', label: 'Orthographic', hint: 'Read snap relationships without perspective skew.' }
  ]}
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/snapping-basics.json`} />

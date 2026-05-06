<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
  import ScreenshotFigure from '$lib/components/ScreenshotFigure.svelte';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Translate Arrays

Build one small box, then reuse it through linear, planar, and volumetric translation tools. The goal is not just duplication, but understanding how each array tool asks for a different measurement and span.

Use this lesson when you need evenly repeated masses such as studs, pavers, shelving blocks, or voxel-like layout studies.

<ScreenshotFigure
  src="/images/generated/translate-arrays-example.png"
  alt="Octodraw UI showing a linear row, a planar grid, and a volumetric cluster of repeated boxes"
  caption="Generated from a running desktop app through MCP capture. One frame shows the three translation-array families side by side."
/>

<ToolbarIconGuide
  title="Buttons used in this lesson"
  items={[
    { name: 'select', label: 'Select', hint: 'Pick the seed box before starting any copy operation.' },
    { name: 'move', label: 'Move', hint: 'Use this for the first translation and placement test.' },
    { name: 'multiple-copy-translate', label: 'Copy', hint: 'Create the linear repeat array.' },
    { name: 'multiple-copy-translate-planar', label: 'Planar Array', hint: 'Create a 2D repeated layout.' },
    { name: 'multiple-copy-translate-volumetric', label: 'Volumetric Array', hint: 'Create a 3D repeated block of copies.' }
  ]}
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/translate-arrays.json`} />

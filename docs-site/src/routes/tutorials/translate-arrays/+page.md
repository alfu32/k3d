<script>
  import { base } from '$app/paths';
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

<TutorialRunner tutorialUrl={`${base}/tutorials/translate-arrays.json`} />

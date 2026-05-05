<script>
  import { base } from '$app/paths';
  import ScreenshotFigure from '$lib/components/ScreenshotFigure.svelte';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Draw a Box

Create a rectangular face, use Push/Pull, and inspect the result from orbit camera mode.

<ScreenshotFigure
  src="/images/generated/push-pull-house-solid.png"
  alt="Octodraw viewport with a generated rectangular solid on the grid"
  caption="Target result for the box workflow. This PNG is generated from a running desktop app through MCP capture."
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/draw-a-box.json`} />

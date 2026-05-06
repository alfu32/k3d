<script>
  import { base } from '$app/paths';
  import ScreenshotFigure from '$lib/components/ScreenshotFigure.svelte';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Rotate Arrays

Create one seed box and drive it through rotational and helical array workflows. These tools are useful when the repeated form is organized around a center, an angle, or a rising spiral.

This lesson focuses on the pick order and the intent behind the reference points rather than on a polished final object.

<ScreenshotFigure
  src="/images/generated/rotate-arrays-example.png"
  alt="Octodraw UI showing repeated boxes arranged in a circular ring and a rising helical pattern"
  caption="Generated from a running desktop app through MCP capture. The left side reads as a rotational array and the right side as a helical array."
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/rotate-arrays.json`} />

<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
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

<ToolbarIconGuide
  title="Buttons used in this lesson"
  items={[
    { name: 'select', label: 'Select', hint: 'Choose the seed box before you duplicate it around a center.' },
    { name: 'rotate', label: 'Rotate', hint: 'Spin the seed before the first copy if the lesson asks for it.' },
    { name: 'multiple-copy-planar-rotate', label: 'Rotational Array', hint: 'Repeat the box around a central axis.' },
    { name: 'multiple-copy-helicoidal-rotate', label: 'Helical Array', hint: 'Repeat the box while rising along the spiral.' }
  ]}
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/rotate-arrays.json`} />

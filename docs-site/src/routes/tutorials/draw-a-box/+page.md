<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
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

<ToolbarIconGuide
  title="Buttons used in this lesson"
  items={[
    { name: 'select', label: 'Select', hint: 'Use this before editing the face or the resulting solid.' },
    { name: 'rectangle', label: 'Rectangle', hint: 'Draw the base face on the ground plane.' },
    { name: 'push_pull', label: 'Push/Pull', hint: 'Extrude the face into a box.' },
    { name: 'camera_rotating_operation', label: 'Orbit', hint: 'Inspect the result from around the scene.' }
  ]}
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/draw-a-box.json`} />

<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
  import ScreenshotFigure from '$lib/components/ScreenshotFigure.svelte';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Modify a Box

Start from one simple solid and work through the core direct-modeling edits: selection, move, copied move, rotate, copied rotate, and a final stretch driven from edge selection.

This lesson is useful when you want to understand how Octodraw treats one piece of geometry as something you can reshape repeatedly rather than redraw from scratch.

<ScreenshotFigure
  src="/images/generated/modify-a-box-workflow.png"
  alt="Octodraw UI showing several related box forms produced during a modify, copy, rotate, and stretch workflow"
  caption="Generated from a running desktop app through MCP capture. The scene summarizes the box modification workflow in one UI-visible frame."
/>

<ToolbarIconGuide
  title="Buttons used in this lesson"
  items={[
    { name: 'select', label: 'Select', hint: 'Choose the solid before changing it.' },
    { name: 'move', label: 'Move', hint: 'Translate the solid after it is selected.' },
    { name: 'multiple-copy-translate', label: 'Copy', hint: 'Duplicate the box before rotating or stretching it.' },
    { name: 'rotate', label: 'Rotate', hint: 'Spin the selected solid or the copied solid.' },
    { name: 'stretch', label: 'Stretch', hint: 'Pull one side after selecting the relevant segment.' }
  ]}
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/modify-a-box.json`} />

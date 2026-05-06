<script>
  import { base } from '$app/paths';
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

<TutorialRunner tutorialUrl={`${base}/tutorials/modify-a-box.json`} />

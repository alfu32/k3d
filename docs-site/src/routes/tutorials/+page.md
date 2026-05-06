<script>
  import { base } from '$app/paths';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Tutorials

Tutorials are JSON-driven lessons displayed by `TutorialRunner.svelte` and paired with the embedded Octodraw webcomponent. Public tutorials must work without the desktop MCP server. MCP remains a local authoring and validation tool for screenshots, command discovery, and generated references.

## Live Preview

<TutorialRunner tutorialUrl={`${base}/tutorials/getting-started.json`} height="768px" />

## Lessons

<div class="card-grid">
  <a class="doc-card" href="./getting-started/"><strong>Getting Started</strong>Open the embed, identify the viewport, and make the first rectangle.</a>
  <a class="doc-card" href="./draw-a-box/"><strong>Draw a Box</strong>Create a face and extrude it into a simple volume.</a>
  <a class="doc-card" href="./modify-a-box/"><strong>Modify a Box</strong>Select one box and work through move, copy, rotate, rotate-copy, and stretch.</a>
  <a class="doc-card" href="./translate-arrays/"><strong>Translate Arrays</strong>Turn one small box into linear, planar, and volumetric copy layouts.</a>
  <a class="doc-card" href="./rotate-arrays/"><strong>Rotate Arrays</strong>Use rotational and helical array tools on a single seed volume.</a>
  <a class="doc-card" href="./push-pull-house/"><strong>Push/Pull House</strong>Use direct modeling steps to block out a small house form.</a>
  <a class="doc-card" href="./snapping-basics/"><strong>Snapping Basics</strong>Practice grid, endpoint, midpoint, and guide snapping.</a>
  <a class="doc-card" href="./objects-and-instances/"><strong>Objects and Instances</strong>Create reusable geometry and reason about edit context.</a>
  <a class="doc-card" href="./lighting-and-export/"><strong>Lighting and Export</strong>Set view lighting and learn the screenshot/export path.</a>
</div>

## Implementation Status

The runner executes tutorial actions against the browser webcomponent when the current `<octodraw-editor>` API exposes the needed operation. Tool selection, camera presets, model reset/load, browser canvas capture, and simple highlight actions are wired. Desktop-only commands and Groovy scripts stay explicitly unavailable in public tutorials and should be validated through local MCP instead.

<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
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
  <a class="doc-card" href="./volume-and-fuzzy-tools/"><strong>Volume and Fuzzy Tools</strong>Prepare object booleans, inspect cut phases, and apply controlled random modeling helpers.</a>
  <a class="doc-card" href="./push-pull-house/"><strong>Push/Pull House</strong>Use direct modeling steps to block out a small house form.</a>
  <a class="doc-card" href="./snapping-basics/"><strong>Snapping Basics</strong>Practice grid, endpoint, midpoint, and guide snapping.</a>
  <a class="doc-card" href="./objects-and-instances/"><strong>Objects and Instances</strong>Create reusable geometry and reason about edit context.</a>
  <a class="doc-card" href="./lighting-and-export/"><strong>Lighting and Export</strong>Set view lighting and learn the screenshot/export path.</a>
</div>

## Common Buttons

<ToolbarIconGuide
  title="Button names used in the first tutorials"
  items={[
    { name: 'select', label: 'Select', hint: 'Start here when the lesson says to select geometry.' },
    { name: 'move', label: 'Move', hint: 'Use this for translation-based edits.' },
    { name: 'rotate', label: 'Rotate', hint: 'Use this for rotation edits and rotate-copy flows.' },
    { name: 'stretch', label: 'Stretch', hint: 'Use this when the lesson asks for segment or edge stretching.' },
    { name: 'multiple-copy-translate', label: 'Copy', hint: 'Use for linear copy workflows.' },
    { name: 'multiple-copy-translate-planar', label: 'Planar Array', hint: 'Use for 2D repeated layouts.' },
    { name: 'multiple-copy-translate-volumetric', label: 'Volumetric Array', hint: 'Use for 3D repeated layouts.' },
    { name: 'multiple-copy-planar-rotate', label: 'Rotational Array', hint: 'Use for circular copy patterns.' },
    { name: 'multiple-copy-helicoidal-rotate', label: 'Helical Array', hint: 'Use for spiral copy patterns.' },
    { name: 'solid_union', label: 'Solid Union', hint: 'Merge two selected mesh object instances.' },
    { name: 'solid_intersect', label: 'Solid Intersection', hint: 'Keep only overlapping volume fragments.' },
    { name: 'solid_subtract', label: 'Solid Subtraction', hint: 'Subtract the second selected object from the first.' },
    { name: 'fuzzy_offset', label: 'Random Offset', hint: 'Perturb selected connected vertices.' },
    { name: 'fuzzy_cover', label: 'Random Surface Array', hint: 'Scatter selected payload geometry over selected faces.' },
    { name: 'mesh_regularize', label: 'Mesh Regularize', hint: 'Rebuild a near-planar selected patch as regular triangles.' },
    { name: 'file_save', label: 'Save', hint: 'Use when the lesson asks you to keep the model.' }
  ]}
/>

## Implementation Status

The runner executes tutorial actions against the browser webcomponent when the current `<octodraw-editor>` API exposes the needed operation. Tool selection, camera presets, model reset/load, browser canvas capture, and simple highlight actions are wired. Desktop-only commands and Groovy scripts stay explicitly unavailable in public tutorials and should be validated through local MCP instead.

# Tutorials

Tutorials are JSON-driven lessons displayed by `TutorialRunner.svelte` and paired with the embedded Octodraw webcomponent. Public tutorials must work without the desktop MCP server. MCP remains a local authoring and validation tool for screenshots, command discovery, and generated references.

## Lessons

<div class="card-grid">
  <a class="doc-card" href="./getting-started/"><strong>Getting Started</strong>Open the embed, identify the viewport, and make the first rectangle.</a>
  <a class="doc-card" href="./draw-a-box/"><strong>Draw a Box</strong>Create a face and extrude it into a simple volume.</a>
  <a class="doc-card" href="./push-pull-house/"><strong>Push/Pull House</strong>Use direct modeling steps to block out a small house form.</a>
  <a class="doc-card" href="./snapping-basics/"><strong>Snapping Basics</strong>Practice grid, endpoint, midpoint, and guide snapping.</a>
  <a class="doc-card" href="./objects-and-instances/"><strong>Objects and Instances</strong>Create reusable geometry and reason about edit context.</a>
  <a class="doc-card" href="./lighting-and-export/"><strong>Lighting and Export</strong>Set view lighting and learn the screenshot/export path.</a>
</div>

## Implementation Status

The runner fetches tutorial JSON client-side and stubs command execution safely until the browser tutorial-facing webcomponent API is finalized. Local MCP scripts should validate command IDs before generated references are published.

<script>
  import ScreenshotFigure from '$lib/components/ScreenshotFigure.svelte';
</script>

# Snapping and Guides

Snapping is central to Octodraw's direct modeling workflow. It helps align new geometry with existing model features and the grid.

<ScreenshotFigure
  src="/images/generated/snapping-midpoint-example.png"
  alt="Octodraw viewport with a generated line and midpoint marker geometry"
  caption="A line example generated for endpoint and midpoint snapping documentation."
/>

## Snap Targets

Current docs and source describe snap feedback for:

- grid intersections and grid lines,
- endpoints,
- midpoints,
- points on line segments,
- points on faces,
- temporary grid and axis guides.

## Guides

- Press `G` to add a grid guide at the current snap.
- Press `T` to add an axis guide at the current snap.
- `Esc` clears guides when using Select mode in current documentation.

## Precision Workflow

Use guides and snapping before relying on visual placement. For tutorials and documentation automation, reset the scene, set the camera, then perform pointer operations against stable world coordinates or validated command/script paths.

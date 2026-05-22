<script>
  import ScreenshotFigure from '$lib/components/ScreenshotFigure.svelte';
</script>

# Drawing Tools

Drawing tools create edges, faces, dimensions, text, and specialized construction geometry. The current source lists built-in tool IDs in `ToolId.kt`; command palette entries are generated as `tool.builtin.<tool-id-lowercase>` when available.

<ScreenshotFigure
  src="/images/generated/getting-started-rectangle-face.png"
  alt="Octodraw viewport with a generated rectangular face on the grid"
  caption="A rectangle face generated through MCP for documentation. The manifest records the exact command and console steps."
/>

## Core Construction Tools

- Line, Construction Line, Polyline, and Double Line for edge and chain creation.
- Rectangle, Surface Rectangle, Quad, and Circle for planar faces.
- Face Outline, Line Offset, Plane Section, and Mesh Intersection for derived construction.
- Linear Dimension, Text, and Vector Text for annotations.

## Mechanical Construction

The Mech toolbar adds generated geometry for screw surfaces, circular/square hole patches, washers, and cog wheels. Cog Wheel uses selected segments as one tooth definition: draw or select the tooth, then pick tangent start, tangent end, and a third point whose distance from tangent start gives the tooth count in model units. The center is placed on the left-hand side of the tangent vector from start to end, and the result is a horizontal planar cog outline with a radial center marker.

## Surface-Aligned Drawing

Surface Rectangle is intended for drawing on existing face planes. Snapping and face hit detection should be checked in the current build before writing exact automation steps.

## Tutorial Guidance

When command IDs are uncertain, tutorial JSON marks command actions as pending discovery. Command validation belongs in local MCP authoring scripts, not in public browser execution.

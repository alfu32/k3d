<script>
  import ScreenshotFigure from '$lib/components/ScreenshotFigure.svelte';
</script>

# Lighting

Octodraw includes a real-time lighting panel with directional, ambient, specular, and shadow controls. Lighting settings are persisted with model files.

<ScreenshotFigure
  src="/images/generated/lighting-scene-example.png"
  alt="Octodraw viewport with a generated solid and lighting panel context"
  caption="Lighting documentation capture generated from the running desktop app through MCP."
/>

## Lighting Controls

The lighting system exposes:

- directional light intensity and color,
- ambient light intensity and color,
- specular light controls,
- shadow light parameters,
- shadow bias, normal bias, filtering, dither, and CSM toggles in current specs.

## Documentation Captures

Use the Lighting panel to create clearer documentation screenshots. The local screenshot pipeline should prefer MCP render-buffer or screenshot commands discovered at runtime. If no render-buffer command is available, record the window or browser screenshot fallback in the generated-image manifest.

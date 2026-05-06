<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Lighting and Export

Use lighting controls for readable model inspection and understand where screenshot/export automation fits.

<ToolbarIconGuide
  title="Buttons used in this lesson"
  items={[
    { name: 'select', label: 'Select', hint: 'Choose the object or scene region to inspect.' },
    { name: 'lighting', label: 'Lighting', hint: 'Open the lighting settings used for readable captures.' },
    { name: 'rendering_global_illumination', label: 'GI', hint: 'Enable global illumination when needed.' },
    { name: 'rendering_raytrace', label: 'RT', hint: 'Switch to ray tracing when the lesson calls for it.' },
    { name: 'file_save', label: 'Save', hint: 'Keep the exported scene or final capture setup.' }
  ]}
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/lighting-and-export.json`} />

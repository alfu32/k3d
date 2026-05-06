<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Objects and Instances

Learn the current object workflow: group selected geometry, place instances, and edit an object context deliberately.

<ToolbarIconGuide
  title="Buttons used in this lesson"
  items={[
    { name: 'select', label: 'Select', hint: 'Pick the geometry to group or instance.' },
    { name: 'object_create', label: 'Create Object', hint: 'Turn selected geometry into a reusable object.' },
    { name: 'edit_object', label: 'Edit Object', hint: 'Enter the object definition for editing.' },
    { name: 'move', label: 'Move', hint: 'Place the object instance in the scene.' },
    { name: 'file_save', label: 'Save', hint: 'Keep the object and instance changes.' }
  ]}
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/objects-and-instances.json`} />

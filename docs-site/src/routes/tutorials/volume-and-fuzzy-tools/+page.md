<script>
  import { base } from '$app/paths';
  import ToolbarIconGuide from '$lib/components/ToolbarIconGuide.svelte';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Volume and Fuzzy Tools

This lesson introduces the tools used when a direct modeling step needs topology preparation, object-level volume operations, or controlled randomness.

Volume boolean tools operate on two selected mesh object instances. They first cut both operands along their intersection segments, then keep or discard the resulting face fragments according to union, intersection, or subtraction. If you need to inspect that preparation step without removing geometry, use Cut Objects.

Fuzzy tools are intentionally destructive modeling helpers. Random Offset perturbs connected selected vertices along averaged surface normals. Random Surface Array scatters selected payload geometry over selected faces. Mesh Regularize replaces a near-planar selected patch with a rectangular grid of triangles.

<ToolbarIconGuide
  title="Volume and fuzzy buttons"
  columns={3}
  items={[
    { name: 'solid_union', label: 'Solid Union', hint: 'Merge two selected mesh object instances into one loose selected result.' },
    { name: 'solid_intersect', label: 'Solid Intersection', hint: 'Keep the face fragments that lie inside both selected mesh objects.' },
    { name: 'solid_subtract', label: 'Solid Subtraction', hint: 'Remove the second selected object volume from the first selected object volume.' },
    { name: 'mesh_intersection', label: 'Mesh Intersection', hint: 'Generate intersection segments for manual inspection and repair.' },
    { name: 'fuzzy_offset', label: 'Random Offset', hint: 'Move selected connected vertices along averaged normals with a controlled random strength.' },
    { name: 'fuzzy_cover', label: 'Random Surface Array', hint: 'Scatter selected payload geometry across selected surface faces.' },
    { name: 'mesh_regularize', label: 'Mesh Regularize', hint: 'Replace a near-planar patch with a regular rectangular triangle grid.' }
  ]}
/>

<TutorialRunner tutorialUrl={`${base}/tutorials/volume-and-fuzzy-tools.json`} />

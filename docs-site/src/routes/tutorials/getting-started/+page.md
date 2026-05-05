<script>
  import { base } from '$app/paths';
  import TutorialRunner from '$lib/components/TutorialRunner.svelte';
</script>

# Getting Started

This tutorial introduces the viewport, command panel, and first rectangle workflow. The embedded editor loads from the static webcomponent bundle when the site is built with the Gradle webcomponent artifact.

<TutorialRunner tutorialUrl={`${base}/tutorials/getting-started.json`} />

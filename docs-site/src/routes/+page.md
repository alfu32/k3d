<script>
  import DownloadMatrix from '$lib/components/DownloadMatrix.svelte';
</script>

# Octodraw

Octodraw is a lightweight direct 3D sketching and modeling project built with Kotlin and libGDX. It is useful for fast spatial sketching, planar face modeling, object/instance workflows, and automation experiments, not as a finished commercial CAD replacement.

The project currently targets JVM desktop through LWJGL3, Android, and a TeaVM/WebGL web runtime that can be packaged as an embeddable webcomponent.

## What It Is Good For

Octodraw focuses on direct modeling: draw edges, close planar faces, then use tools such as Push/Pull, Move, Rotate, Scale, Stretch, Paint, and object placement. The modeler keeps a simple Y-up 3D workspace with snapping, guides, panels, and command-driven workflows.

Precision work is supported through grid, endpoint, midpoint, line, face, and guide snapping. `G` places grid guides and `T` places axis guides at the current snap target. Numeric input exists for active tools and should continue to be validated against the current build.

Objects are group/prototype based. Create an object from selected geometry, place instances, then edit an instance when the object definition should change. Hotspot-driven instance behavior exists in the current docs and should be treated as an advanced workflow.

## Automation Surface

Power users and agents can work through the Groovy console, plugin API, command palette, and local MCP HTTP surface. MCP is deliberately local: it is for deterministic automation, validation, screenshot generation, command discovery, and documentation production. Public browser tutorials on GitHub Pages use the embedded webcomponent and must not require a desktop MCP server.

## Interactive Tutorials

The new tutorial pages load JSON tutorial definitions and embed the Octodraw webcomponent bundle produced by the existing Gradle web build. The first version uses safe stubs for tutorial command execution while the public tutorial-facing webcomponent contract is stabilized.

## Start Here

<div class="card-grid">
  <a class="doc-card" href="guide/"><strong>New user -&gt; Guide</strong>Install Octodraw, learn the interface, and practice selection, snapping, and files.</a>
  <a class="doc-card" href="tutorials/"><strong>Hands-on learner -&gt; Tutorials</strong>Run guided lessons backed by tutorial JSON and the webcomponent embed.</a>
  <a class="doc-card" href="automation/"><strong>Automation author -&gt; Automation</strong>Use MCP and Codex workflows to validate commands, scenes, and documentation images.</a>
  <a class="doc-card" href="reference/"><strong>Plugin developer -&gt; Reference</strong>Read command, console, plugin, file format, and webcomponent contracts.</a>
</div>

## Downloads and Source Builds

<DownloadMatrix />

Build from source:

```sh
./gradlew build
./gradlew :lwjgl3:run
./gradlew :web:prepareWebComponentBundle
```

The webcomponent bundle is written to `web/build/dist/webcomponent` and copied into this site during the GitHub Pages workflow.

Source repository: [github.com/alfu32/k3d](https://github.com/alfu32/k3d)

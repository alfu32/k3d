# Webcomponent Contract Specification

The webcomponent is the public browser tutorial runtime. Its contract must remain separate from the desktop MCP HTTP surface.

## Principles

- Tutorials call stable methods on the editor element.
- Tutorials do not inspect arbitrary internal DOM or canvas state.
- The browser API can share concepts with MCP but must not require a local desktop server.
- State checks should be explicit, serializable, and safe for static hosting.

## Current State

The current `web/component/README.md` documents `<octodraw-editor>` with model, selection, camera, tool, pointer, command, playback, and UI methods. The tutorial-facing API in the reference section is the intended stable wrapper over that lower-level surface.

## Future Work

Wire `TutorialRunner.svelte` to the stable webcomponent tutorial methods, then use the same tutorial JSON for public lessons and local validation where practical.

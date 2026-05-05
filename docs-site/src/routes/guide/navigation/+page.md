# Navigation

Octodraw uses a Y-up 3D workspace. Orbit, pan, and zoom are the core camera operations for direct modeling.

## Orbit Camera

- Right mouse drag: orbit around the camera target.
- Shift + right mouse drag: pan.
- Mouse wheel: zoom.

Use orbit mode for most modeling and documentation screenshots because it shows both face orientation and height.

## Walkthrough Camera

Walkthrough mode supports first-person style inspection. The current manual describes right-drag look, `W/A/S/D` and arrow movement, `Space` jump, and vertical height adjustment with `Up` and `Down`.

## Orthographic Views

Orthographic camera mode supports top, bottom, left, right, front, and back view commands. Automation should discover exact command availability through MCP before executing camera commands.

Common command IDs observed in current docs include `view.camera.orbit`, `view.camera.walkthrough`, `view.camera.orthographic`, and `view.ortho.top`.

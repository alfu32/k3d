# Release Notes 1.7.3

## Changes
- Push/Pull now extrudes only boundary edges of coplanar face groups (no internal edge extrusion).
- Paint UI now reflects the active paint color on both the Color button and Paint tool icon.
- Guides and snapping honor oriented guide bases when created on face planes.
- CLI command handler supports `edit`, `version`, `update`, and `help` with self-update download/install.
- Launchers updated for the command-first CLI format.

## Notes
- Use `edit --file <path>` to open or create a model file.
- `update` downloads the latest jar and archives the previous build by version.

# Release Notes 1.7.1

## Changes since 1.4.3
- Move/Rotate copy mode: hold Ctrl to toggle copy, with status bar indicator and selection duplication on commit.
- Surface-aligned rectangle tool: draws a rectangle on the picked face plane using a ground-parallel axis and its perpendicular.
- Guide placement now captures face orientation: grid/axis helpers align to the face normal and plane basis where they are created.
- Guide snapping respects guide orientation for both planes and axes.
- Command-line interface now uses a command first parameter: `edit`, `version`, `update`, `help`.
- Self-update command downloads the latest jar, archives the current jar with the version tag, and installs the new build.
- Linux/Windows launchers updated to use `edit` and support the new command format.

## Notes
- Existing `--file` support remains under `edit --file <path>`.
- Version output uses build metadata (tag/commit/date) when available.

## Next Steps / Known Gaps
- Finish scale and eraser tools.
- Improve model cleanup safety (avoid removing valid faces/edges).
- Add undo/redo, import/export, and plugin/runtime support.

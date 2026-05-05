# Tutorial Specification

Tutorials are JSON files under `docs-site/static/tutorials/`. They are fetched client-side by `TutorialRunner.svelte`.

## Required Fields

Each tutorial has `id`, `title`, `level`, `estimatedMinutes`, `initialScene`, and non-empty `steps`.

Each step has `id`, `title`, `body`, optional `notes`, optional `actions`, and optional `success`.

## Action Types

Supported action types are:

- `none`
- `command`
- `script`
- `camera`
- `loadScene`
- `highlight`

Command IDs that are not validated against the current runtime should be marked with `pendingDiscovery`.

## Success Condition Types

Supported success conditions are:

- `manual`
- `command-active`
- `scene-state`
- `selection`

The first implementation keeps most checks manual or stubbed until the webcomponent state API is wired.

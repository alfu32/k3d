# Content Style Guide

Write Octodraw docs in a direct technical tone.

## Accuracy

- Describe implemented behavior as implemented.
- Mark uncertain behavior as pending validation.
- Mark future behavior as planned.
- Do not present Octodraw as finished commercial CAD software.

## Links and Releases

Use the GitHub releases page or latest release route. Do not add stale hardcoded release artifact URLs.

## Commands

When exact command IDs matter, verify them with `/scene/listCommands`. If command discovery is unavailable, document the discovery workflow rather than inventing IDs.

## Screenshots

Use real screenshots from the running desktop app, render-buffer capture, or actual browser/webcomponent capture. Store generated images under `static/images/generated/` with manifest records.

## Existing Docs

The old `docs/` folder remains source material. Do not delete or rewrite it unless a task explicitly says to migrate a specific document.

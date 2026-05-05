# Files, Save, and Recovery

Octodraw model files use the `.octd` extension. Existing docs also show `.k3d` examples from repository history; preserve existing examples when migrating but prefer current file behavior when writing new docs.

## Saved Data

Current file snapshots include geometry, object hierarchy, camera and target, lighting and shadow settings, unit name and size, snap radius, grid spacing, and undo history snapshot data.

## Save Behavior

The desktop app autosaves after many geometry, selection, settings, and tool changes. Loading an existing file creates a backup file before reading and may resave older snapshots in the current format.

## Recovery Checklist

1. If the model fails to open, check the sibling backup file.
2. Confirm the file is non-empty.
3. Reopen with `edit --file <path>`.
4. Save a fresh copy after cleanup if the model loads.

# File Format Reference

Octodraw uses model snapshots for persisted files. `.octd` is the current user-facing extension in docs; `.k3d` examples remain in the repository from historical naming.

## Persisted Data

Current documentation says snapshots include:

- geometry and object hierarchy,
- camera and camera target,
- lighting and shadow settings,
- unit name and unit size,
- grid spacing and snap radius,
- undo history snapshot data.

## Compatibility

The loader creates backups for existing files and can resave older snapshots after migration. File formats and APIs may evolve while the project remains active.

## Guidance for Docs and Tutorials

Tutorials should prefer small, version-controlled JSON scene descriptions or embedded initial models. Do not depend on random internal file fields unless they are documented in a stable schema.

# Release Notes 1.7.10

## Changes since 1.7.9
- Persisted camera, lighting, and shadow settings to the model file with safe defaults and automatic backfill for older files.
- Scale tool now scales only along the reference axis (no skew), preserving perpendicular components.
- Forced a save on application shutdown to avoid losing the latest state.

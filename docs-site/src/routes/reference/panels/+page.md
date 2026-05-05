# Panels Reference

Panels expose state that is often easier to inspect than the raw model. Current docs identify these right-side panels and common commands.

## Panels

- Selection: counts selected entities and supports selection validation.
- Object Info: selected object metadata and glue-to-surface options.
- Objects: object prototypes and placement/edit workflow.
- Model Settings: unit name/size, grid size, snap radius, and walk tuning.
- Polyline Settings: polyline and double-line defaults.
- Architecture Settings: wall, slab, stair, and frame dimensions and colors.
- Hotspot Settings: advanced dynamic object hotspot controls.
- Lighting: directional, ambient, specular, and shadow controls.
- Plugin Manager: plugin enable, disable, reload, and load-status UI.

## Known Panel Commands

Examples from current docs include `view.selection`, `view.object_info`, `view.objects`, `view.model_settings`, `view.polyline_settings`, `view.architecture_settings`, `view.lighting`, and `view.plugin_manager`.

Use runtime command discovery before relying on a panel command in scripts.

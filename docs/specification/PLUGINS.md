# Plugin Developer Guide

This guide covers how to write and load plugins for Octodraw using Groovy scripts.

## Quick Start

1) Write a Groovy plugin (see example below).
2) Copy the `.groovy` file into the install `plugins/` directory.
3) Open the app and use `Plugins` -> `Reload`.

The plugins directory is resolved from the installation folder at runtime. The UI shows the full path in the Plugin Manager window.

## Plugin File Locations

- Install path: `dist/` when built locally.
- Plugins directory: `<install>/plugins`.
- Plugin API JAR: `<install>/plugins/octodraw-plugin-api*.jar` (packaged via `dist.sh`).

During development, you can run with `./gradlew :lwjgl3:run` and copy scripts into `dist/plugins/` to test.

## Plugin Interface

Plugins implement `com.github.alfu32.sketch.plugin.Plugin`.

Required properties:
- `id`, `name`, `version`

Optional properties (defaults provided by the interface):
- `author`, `description`

Lifecycle hooks (all optional with defaults):
- `onLoad`, `onEnable`, `onDisable`, `onUnload`
- `onCreate`, `onUpdate`, `onDraw`, `onSave`, `onClose`

Event hooks (optional):
- `onSceneLoad`, `onSceneSave`, `onSelectionChanged`, `onToolChanged`

State persistence (optional):
- `saveState`, `restoreState`

## Plugin Context

`PluginContext` provides snapshots (read-only) of the current state:
- `model`: `ModelSnapshot` (triangulated faces + groups)
- `selection`: selected edges, faces, and groups
- `cursor`: screen/world cursor info
- `screenSize`, `activeTool`, `copyMode`

Because this is snapshot data, do not mutate it directly.

## Command Palette Integration

Implement commands via `registerCommands()` and return a list of `PluginCommand` objects.

Important:
- In Groovy, do not use map coercion for `PluginCommand`. Implement a concrete class.
- Kotlin `val` properties are read-only from Groovy; use constructors for data classes.

Example command registration:

```groovy
import com.github.alfu32.sketch.plugin.Plugin
import com.github.alfu32.sketch.plugin.PluginContext
import com.github.alfu32.sketch.plugin.PluginResult
import com.github.alfu32.sketch.plugin.capabilities.KeyBinding
import com.github.alfu32.sketch.plugin.capabilities.PluginCommand

class HelloPlugin implements Plugin {
    String id = "hello"
    String name = "Hello"
    String version = "0.1"

    @Override
    List<PluginCommand> registerCommands() {
        return [
            new SimpleCommand(
                "hello",
                "Hello",
                "Show a message",
                "General",
                null,
                "edit",
                true,
                { PluginContext ctx -> PluginResult.success() }
            )
        ]
    }
}

class SimpleCommand implements PluginCommand {
    private final String id
    private final String name
    private final String description
    private final String category
    private final KeyBinding shortcut
    private final String icon
    private final boolean isVisibleInPalette
    private final Closure<PluginResult> executeAction

    SimpleCommand(
        String id,
        String name,
        String description,
        String category,
        KeyBinding shortcut,
        String icon,
        boolean isVisibleInPalette,
        Closure<PluginResult> executeAction
    ) {
        this.id = id
        this.name = name
        this.description = description
        this.category = category
        this.shortcut = shortcut
        this.icon = icon
        this.isVisibleInPalette = isVisibleInPalette
        this.executeAction = executeAction
    }

    String getId() { return id }
    String getName() { return name }
    String getDescription() { return description }
    String getCategory() { return category }
    KeyBinding getShortcut() { return shortcut }
    String getIcon() { return icon }
    boolean isVisibleInPalette() { return isVisibleInPalette }

    PluginResult execute(PluginContext context) {
        return executeAction.call(context)
    }
}

return new HelloPlugin()
```

## Debugging Tips

- Check the Plugin Manager window for:
  - Plugins directory path
  - Load errors
  - Enable/disable status
- If a plugin does not load, confirm the `.groovy` file is inside `<install>/plugins`.
- When you update a script, click `Reload`.

## Common Groovy Pitfalls

- Use `as float` when passing numeric values to libGDX types like `Vector3`.
- Prefer constructors over named property assignment for Kotlin data classes.
- Implement `isVisibleInPalette()` (not `getIsVisibleInPalette()`).
*** End Patch}

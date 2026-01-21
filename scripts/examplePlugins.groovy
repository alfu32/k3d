class HelloPlugin implements com.github.alfu32.sketch.plugin.Plugin {
    String id = "hello"
    String name = "Hello"
    String version = "0.2"
    String author = "Example"
    String description = "Minimal plugin updated for the new Plugin interface."

    @Override
    PluginResult onLoad(PluginContext context) {
        println("lifecycle plugin Hello :: onLoad")
        return PluginResult.success()
    }

    @Override
    PluginResult onCreate(PluginContext context) {
        println("lifecycle plugin Hello :: onCreate")
        return PluginResult.success()
    }

    @Override
    PluginResult onUpdate(PluginContext context, Float deltaSeconds) {
        return PluginResult.success()
    }

    @Override
    PluginDraw onDraw(PluginContext context) {
        return new PluginDraw()
    }

    @Override
    PluginResult onSave(PluginContext context) {
        println("lifecycle plugin Hello :: onSave")
        return PluginResult.success()
    }

    @Override
    PluginResult onClose(PluginContext context) {
        println("lifecycle plugin Hello :: onClose")
        return PluginResult.success()
    }
}
return new HelloPlugin()

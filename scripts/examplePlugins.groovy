class HelloPlugin2 implements com.github.alfu32.sketch.plugin.Plugin {
    String id = "hello"
    String name = "Hello"
    String version = "0.1"

    @Override
    PluginResult onLoad(PluginContext context) {
        println("lifecyle plugin Hello :: onLoad")
        return null
    }

    @Override
    PluginResult onCreate(PluginContext context) {
        println("lifecyle plugin Hello :: onCreate")
        return null
    }

    @Override
    PluginResult onUpdate(PluginContext context, Float deltaSeconds) {
        return null
    }

    @Override
    PluginDraw onDraw(PluginContext context) {
        println("lifecyle plugin Hello :: onDraw")
        return null
    }

    @Override
    PluginResult onSave(PluginContext context) {
        println("lifecyle plugin Hello :: onSave")
        return null
    }

    @Override
    PluginResult onClose(PluginContext context) {
        println("lifecyle plugin Hello :: onClose")
        return null
    }
}
return new examplePlugins()

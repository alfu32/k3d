
import com.github.alfu32.sketch.plugin.Plugin

class HelloPlugin implements Plugin{
    String id = "hello"
    String name = "Hello"
    String version = "0.2"
    String author = "Example"
    String description = "Minimal plugin updated for the new Plugin interface."
}
return new HelloPlugin()

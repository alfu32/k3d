import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

if (args.length == 0) {
    println("Usage: groovy encode_base64.groovy <path-to-file>")
    System.exit(1)
}

def path = Paths.get(args[0])
if (!Files.exists(path)) {
    println("File not found: ${path.toAbsolutePath()}")
    System.exit(1)
}

byte[] bytes = Files.readAllBytes(path)
String base64 = Base64.getEncoder().encodeToString(bytes)
println(base64)

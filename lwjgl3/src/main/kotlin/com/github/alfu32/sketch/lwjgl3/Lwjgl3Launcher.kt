@file:JvmName("Lwjgl3Launcher")

package com.github.alfu32.sketch.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.github.alfu32.sketch.K3DVersion
import com.github.alfu32.sketch.Main
import groovy.lang.GroovyShell
import java.io.File
import java.net.URL

/** Launches the desktop (LWJGL3) application. */
fun main(args: Array<String>) {
    // This handles macOS support and helps on Windows.
    if (StartupHelper.startNewJvmIfRequired())
      return
    val commandArgs = handleCommand(args) ?: return
    val (width, height) = parseSize(args)
    Lwjgl3Application(Main(commandArgs), Lwjgl3ApplicationConfiguration().apply {
        var ver = K3DVersion()
        setTitle("K3D ${preferVersion(ver)}")

        setWindowIcon("appicon.png")
        //// Vsync limits the frames per second to what your hardware can display, and helps eliminate
        //// screen tearing. This setting doesn't always work on Linux, so the line after is a safeguard.
        useVsync(true)
        //// Limits FPS to the refresh rate of the currently active monitor, plus 1 to try to match fractional
        //// refresh rates. The Vsync setting above should limit the actual FPS to match the monitor.
        setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1)
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.


        setWindowedMode(width, height)

        //// This should improve compatibility with Windows machines with buggy OpenGL drivers, Macs
        //// with Apple Silicon that have to emulate compatibility with OpenGL anyway, and more.
        //// This uses the dependency `com.badlogicgames.gdx:gdx-lwjgl3-angle` to function.
        //// You can choose to remove the following line and the mentioned dependency if you want; they
        //// are not intended for games that use GL30 (which is compatibility with OpenGL ES 3.0).
        setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 0, 0)

    })
}

private fun handleCommand(args: Array<String>): Array<String>? {
    if (args.isEmpty()) {
        return emptyArray()
    }
    val command = args[0].lowercase()
    return when (command) {
        "edit" -> {
            System.setProperty("k3d.devConsole", "true")
            args.drop(1).toTypedArray()
        }
        "groovy" -> {
            runGroovy(args.drop(1))
            null
        }
        "version" -> {
            printVersion()
            null
        }
        "help" -> {
            printHelp()
            null
        }
        "update" -> {
            runUpdate()
            null
        }
        else -> {
            if (command.startsWith("--file") || command.startsWith("--size") || command.startsWith("--plugins")) {
                return args
            }
            val fileArg = args[0]
            if (fileArg.endsWith(".k3d", ignoreCase = true) || File(fileArg).exists()) {
                return arrayOf("--file", fileArg)
            }
            printHelp()
            null
        }
    }
}

private fun printVersion() {
    val ver = K3DVersion()
    val version = preferVersion(ver)
    println("K3D Editor $version (${ver.buildGitCommit.take(8)}) ${ver.buildDate}")
}

private fun printHelp() {
    println(
        """
        K3D Editor
        Commands:
          edit --file <path> [--size WIDTHxHEIGHT]   Open or create a model file (default: sketch3d.k3d)
          edit --plugins-dir <path>                 Override plugins folder
          groovy <script>      Run a Groovy script file
          version             Show version information
          update              Download and replace the editor jar
          help                Show this help message
        """.trimIndent()
    )
}

private fun parseSize(args: Array<String>): Pair<Int, Int> {
    val defaultWidth = 1024
    val defaultHeight = 768
    val sizeIndex = args.indexOf("--size")
    if (sizeIndex == -1 || sizeIndex + 1 >= args.size) {
        return defaultWidth to defaultHeight
    }
    val raw = args[sizeIndex + 1].trim()
    val parts = raw.lowercase().split('x')
    if (parts.size != 2) {
        return defaultWidth to defaultHeight
    }
    val width = parts[0].toIntOrNull() ?: return defaultWidth to defaultHeight
    val height = parts[1].toIntOrNull() ?: return defaultWidth to defaultHeight
    return width to height
}

private fun runGroovy(args: List<String>) {
    val scriptPath = args.firstOrNull()
    if (scriptPath.isNullOrBlank()) {
        println("Groovy: missing script file.")
        return
    }
    val file = File(scriptPath)
    if (!file.exists()) {
        println("Groovy: file not found: $scriptPath")
        return
    }
    try {
        val shell = GroovyShell()
        shell.evaluate(file)
    } catch (ex: Exception) {
        println("Groovy: ${ex.message}")
    }
}

private fun runUpdate() {
    val jarFile = locateJarFile()
    if (jarFile == null) {
        println("Update failed: unable to locate installation jar.")
        return
    }
    val installDir = jarFile.parentFile ?: run {
        println("Update failed: unable to locate installation folder.")
        return
    }
    val tempFile = File(installDir, "${jarFile.name}.download")
    val url = URL("https://github.com/alfu32/k3d/releases/latest/download/k3d-editor.jar")
    try {
        url.openStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } catch (ex: Exception) {
        println("Update failed: ${ex.message}")
        tempFile.delete()
        return
    }
    if (!tempFile.exists() || tempFile.length() == 0L) {
        println("Update failed: download did not complete.")
        tempFile.delete()
        return
    }
    val version = preferVersion(K3DVersion())
    val archived = File(installDir, "${jarFile.nameWithoutExtension}.${version}.jar")
    if (archived.exists()) {
        archived.delete()
    }
    if (jarFile.exists()) {
        if (!jarFile.renameTo(archived)) {
            println("Update failed: unable to archive existing jar.")
            tempFile.delete()
            return
        }
    }
    if (!tempFile.renameTo(jarFile)) {
        println("Update failed: unable to install updated jar.")
        archived.renameTo(jarFile)
        tempFile.delete()
        return
    }
    println("Update complete: installed ${jarFile.name}")
}

private fun locateJarFile(): File? {
    return try {
        val uri = Main::class.java.protectionDomain.codeSource.location.toURI()
        val file = File(uri)
        if (file.isFile && file.name.endsWith(".jar")) file else null
    } catch (_: Exception) {
        null
    }
}

private fun preferVersion(ver: K3DVersion): String {
    return when {
        ver.buildVersion.isNotBlank() -> ver.buildVersion
        ver.buildGitTag.isNotBlank() -> ver.buildGitTag
        else -> "unknown"
    }
}

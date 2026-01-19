@file:JvmName("Lwjgl3Launcher")

package com.github.alfu32.sketch.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.github.alfu32.sketch.Katechup3dVersion
import com.github.alfu32.sketch.Main
import java.io.File
import java.net.URL

/** Launches the desktop (LWJGL3) application. */
fun main(args: Array<String>) {
    // This handles macOS support and helps on Windows.
    if (StartupHelper.startNewJvmIfRequired())
      return
    val commandArgs = handleCommand(args) ?: return
    Lwjgl3Application(Main(commandArgs), Lwjgl3ApplicationConfiguration().apply {
        var ver = Katechup3dVersion()
        setTitle("Katechup3d ${ver.buildGitTag} ${ver.buildGitBranch} ${ver.buildGitCommit} ${ver.buildDate.substring(0..10)}")
        //// Vsync limits the frames per second to what your hardware can display, and helps eliminate
        //// screen tearing. This setting doesn't always work on Linux, so the line after is a safeguard.
        useVsync(true)
        //// Limits FPS to the refresh rate of the currently active monitor, plus 1 to try to match fractional
        //// refresh rates. The Vsync setting above should limit the actual FPS to match the monitor.
        setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1)
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.


        setWindowedMode(640, 480)
        //// You can change these files; they are in lwjgl3/src/main/resources/ .
        //// They can also be loaded from the root of assets/ .
        setWindowIcon(*(arrayOf(128, 64, 32, 16).map { "libgdx$it.png" }.toTypedArray()))

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
        "edit" -> args.drop(1).toTypedArray()
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
            if (command.startsWith("--file")) {
                return args
            }
            printHelp()
            null
        }
    }
}

private fun printVersion() {
    val ver = Katechup3dVersion()
    val version = preferVersion(ver)
    println("Katechup3d Editor $version (${ver.buildGitCommit.take(8)}) ${ver.buildDate}")
}

private fun printHelp() {
    println(
        """
        Katechup3d Editor
        Commands:
          edit --file <path>   Open or create a model file (default: sketch3d.skate.json)
          version             Show version information
          update              Download and replace the editor jar
          help                Show this help message
        """.trimIndent()
    )
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
    val url = URL("https://github.com/alfu32/katechup3d/releases/latest/download/katechup3d-editor.jar")
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
    val version = preferVersion(Katechup3dVersion())
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

private fun preferVersion(ver: Katechup3dVersion): String {
    return when {
        ver.buildVersion.isNotBlank() -> ver.buildVersion
        ver.buildGitTag.isNotBlank() -> ver.buildGitTag
        else -> "unknown"
    }
}

package com.github.alfu32.sketch.console

import java.io.File

object ConsolePaths {
    fun historyFile(): File {
        val home = System.getProperty("user.home") ?: "."
        val octodrawDir = File(home, ".octodraw")
        if (octodrawDir.exists() || !File(home, ".k3d").exists()) {
            return File(octodrawDir, "console-history.groovy")
        }
        return File(File(home, ".k3d"), "console-history.groovy")
    }
}

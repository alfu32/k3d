package com.github.alfu32.sketch.console

import java.io.File

object ConsolePaths {
    fun historyFile(): File {
        val configuredHome = System.getenv("OCTODRAW_HOME")?.trim().orEmpty()
            .ifBlank { System.getenv("K3D_HOME")?.trim().orEmpty() }
        if (configuredHome.isNotBlank()) {
            return File(configuredHome, "console-history.groovy")
        }
        val home = System.getProperty("user.home") ?: "."
        val octodrawDir = File(home, ".octodraw")
        if (octodrawDir.exists() || !File(home, ".k3d").exists()) {
            return File(octodrawDir, "console-history.groovy")
        }
        return File(File(home, ".k3d"), "console-history.groovy")
    }
}

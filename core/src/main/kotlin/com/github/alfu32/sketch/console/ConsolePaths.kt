package com.github.alfu32.sketch.console

import java.io.File

object ConsolePaths {
    fun historyFile(): File {
        val home = System.getProperty("user.home") ?: "."
        val dir = File(home, ".k3d")
        return File(dir, "console-history.groovy")
    }
}

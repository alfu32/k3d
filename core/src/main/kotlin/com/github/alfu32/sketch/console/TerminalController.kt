package com.github.alfu32.sketch.console

class TerminalController {
    private var originalConfig: String? = null

    fun enterRawMode() {
        originalConfig = runStty("-g")
        runStty("-echo -icanon min 1 time 0")
    }

    fun restore() {
        val config = originalConfig ?: return
        runStty(config)
    }

    fun size(): TerminalSize {
        val raw = runStty("size")
        if (!raw.isNullOrBlank()) {
            val parts = raw.trim().split(" ")
            if (parts.size == 2) {
                val rows = parts[0].toIntOrNull()
                val cols = parts[1].toIntOrNull()
                if (rows != null && cols != null) {
                    return TerminalSize(cols, rows)
                }
            }
        }
        val envRows = System.getenv("LINES")?.toIntOrNull()
        val envCols = System.getenv("COLUMNS")?.toIntOrNull()
        return TerminalSize(envCols ?: 80, envRows ?: 24)
    }

    private fun runStty(args: String): String? {
        return try {
            val process = ProcessBuilder("sh", "-c", "stty $args < /dev/tty")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output.isBlank()) null else output
        } catch (_: Exception) {
            null
        }
    }
}

data class TerminalSize(val columns: Int, val rows: Int)

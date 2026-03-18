package com.github.alfu32.sketch.console

import com.github.alfu32.sketch.tui.InputEvent
import com.github.alfu32.sketch.tui.InputKeys
import com.github.alfu32.sketch.tui.InputModifiers
import java.io.Flushable
import java.io.InputStream
import java.util.concurrent.TimeUnit

open class TerminalController {
    private var originalConfig: String? = null
    private var alternateScreen = false
    private var lastKnownSize: TerminalSize = querySize()
    private var lastSizeCheckNanos: Long = 0L

    open fun awaitActivationIfNeeded() {
        // No-op by default.
    }

    open fun enterRawMode() {
        originalConfig = captureTerminalState()
        runShellCommand("stty raw -echo < /dev/tty")
        enterAlternateScreen()
    }

    open fun restore() {
        leaveAlternateScreen()
        restoreTerminalState(originalConfig)
        originalConfig = null
    }

    open fun size(): TerminalSize = querySize()

    open fun pollEvent(timeoutMillis: Long = 0L): InputEvent? {
        detectResize()?.let { return it }
        val deadline = if (timeoutMillis > 0) {
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        } else {
            Long.MIN_VALUE
        }
        while (true) {
            readInputEventNonBlocking()?.let { return it }
            if (timeoutMillis <= 0L || System.nanoTime() >= deadline) {
                return detectResize()
            }
            Thread.sleep(4L)
            detectResize()?.let { return it }
        }
    }

    protected open fun readInputEventNonBlocking(): InputEvent? {
        if (System.`in`.available() <= 0) {
            return null
        }
        return readAnsiEvent(System.`in`)
    }

    protected fun readAnsiEvent(input: InputStream): InputEvent? {
        val first = input.read()
        if (first == -1) {
            return null
        }
        return when (first) {
            10, 13 -> InputEvent.Key(InputKeys.ENTER, 0)
            9 -> InputEvent.Key(InputKeys.TAB, 0)
            127, 8 -> InputEvent.Key(InputKeys.BACKSPACE, 0)
            27 -> parseEscapeSequence(input)
            in 1..26 -> {
                val key = 'A'.code + first - 1
                InputEvent.Key(key, InputModifiers.CTRL)
            }
            else -> {
                if (first in 32..126) {
                    InputEvent.Key(first, 0)
                } else {
                    null
                }
            }
        }
    }

    protected fun querySize(): TerminalSize {
        val raw = runShellCommand("stty size < /dev/tty")
        if (!raw.isNullOrBlank()) {
            val parts = raw.trim().split(Regex("\\s+"))
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

    protected fun captureTerminalState(): String? =
        runShellCommand("stty -g < /dev/tty")?.trim()

    protected fun restoreTerminalState(state: String?) {
        val command = if (state.isNullOrBlank()) {
            "stty sane echo icanon isig < /dev/tty"
        } else {
            "stty $state < /dev/tty"
        }
        runShellCommand(command)
    }

    protected fun enterAlternateScreen() {
        if (alternateScreen) {
            return
        }
        alternateScreen = true
        print("\u001B[?1049h\u001B[2J\u001B[H\u001B[?25l")
        flushStdout()
    }

    protected fun leaveAlternateScreen() {
        if (!alternateScreen) {
            return
        }
        alternateScreen = false
        print("\u001B[0m\u001B[?25h\u001B[?1049l")
        flushStdout()
    }

    protected fun flushStdout() {
        (System.out as? Flushable)?.flush()
    }

    protected fun runShellCommand(command: String): String? {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.takeIf { it.isNotBlank() }?.trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun detectResize(): InputEvent.Resize? {
        val now = System.nanoTime()
        if (now - lastSizeCheckNanos < 200_000_000L) {
            return null
        }
        lastSizeCheckNanos = now
        val current = size()
        if (current != lastKnownSize) {
            lastKnownSize = current
            return InputEvent.Resize(current.columns, current.rows)
        }
        return null
    }

    private fun parseEscapeSequence(input: InputStream): InputEvent? {
        if (input.available() == 0) {
            return InputEvent.Key(InputKeys.ESC, 0)
        }
        val second = input.read()
        if (second == -1) {
            return null
        }
        return when (second) {
            '['.code -> parseCsi(input)
            else -> null
        }
    }

    private fun parseCsi(input: InputStream): InputEvent? {
        val seq = StringBuilder()
        while (true) {
            val next = input.read()
            if (next == -1) {
                return null
            }
            val ch = next.toChar()
            seq.append(ch)
            if (ch.isLetter() || ch == '~') {
                break
            }
        }
        val value = seq.toString()
        val finalChar = value.last()
        val params = value.dropLast(1)
        val keyCode = when (finalChar) {
            'A' -> InputKeys.UP
            'B' -> InputKeys.DOWN
            'C' -> InputKeys.RIGHT
            'D' -> InputKeys.LEFT
            'H' -> InputKeys.HOME
            'F' -> InputKeys.END
            '~' -> when (params.substringBefore(';')) {
                "3" -> InputKeys.DELETE
                "5" -> InputKeys.PAGE_UP
                "6" -> InputKeys.PAGE_DOWN
                else -> null
            }
            else -> null
        }
        if (keyCode == null) {
            return null
        }
        return InputEvent.Key(keyCode, parseModifier(params))
    }

    private fun parseModifier(params: String): Int {
        if (!params.contains(';')) {
            return 0
        }
        val parts = params.split(';')
        val mod = parts.last().toIntOrNull() ?: return 0
        return when (mod) {
            2 -> InputModifiers.SHIFT
            3 -> InputModifiers.ALT
            4 -> InputModifiers.SHIFT or InputModifiers.ALT
            5 -> InputModifiers.CTRL
            6 -> InputModifiers.SHIFT or InputModifiers.CTRL
            7 -> InputModifiers.ALT or InputModifiers.CTRL
            8 -> InputModifiers.SHIFT or InputModifiers.ALT or InputModifiers.CTRL
            else -> 0
        }
    }
}

data class TerminalSize(val columns: Int, val rows: Int)

package com.github.alfu32.sketch.console

import com.github.alfu32.sketch.tui.ConsoleTui
import com.github.alfu32.sketch.tui.InputEvent
import com.github.alfu32.sketch.tui.InputKeys
import com.github.alfu32.sketch.tui.InputModifiers
import java.io.InputStream

class ConsoleThread(
    private val runtime: ConsoleGroovyRuntime,
    private val tui: ConsoleTui,
    private val terminal: TerminalController
) : Thread("k3d-dev-console") {
    @Volatile
    private var running = true

    override fun run() {
        terminal.enterRawMode()
        try {
            tui.history.loadFromDisk()
            tui.outputPane.append(
            """
                 ██╗  ██╗ ██████╗  ██████╗       ██████╗  ██████╗  ███╗   ██╗ ███████╗  ██████╗  ██╗      ███████╗
                 ██║ ██╔╝ ╚════██╗ ██╔══██╗     ██╔════╝ ██╔═══██╗ ████╗  ██║ ██╔════╝ ██╔═══██╗ ██║      ██╔════╝
                 █████╔╝   █████╔╝ ██║  ██║     ██║      ██║   ██║ ██╔██╗ ██║ ███████╗ ██║   ██║ ██║      █████╗
                 ██╔═██╗   ╚═══██╗ ██║  ██║     ██║      ██║   ██║ ██║╚██╗██║ ╚════██║ ██║   ██║ ██║      ██╔══╝
                 ██║  ██╗ ██████╔╝ ██████╔╝     ╚██████╗ ╚██████╔╝ ██║ ╚████║ ███████║ ╚██████╔╝ ███████╗ ███████╗
                 ╚═╝  ╚═╝ ╚═════╝  ╚═════╝       ╚═════╝  ╚═════╝  ╚═╝  ╚═══╝ ╚══════╝  ╚═════╝  ╚══════╝ ╚══════╝
            """.trimIndent()
            )
            tui.printHelp()
            tui.render()
            val input = System.`in`
            while (running) {
                if (input.available() > 0) {
                    val event = readEvent(input) ?: break
                    tui.handleInput(event)
                    if (tui.needsRender()) {
                        tui.render()
                    }
                } else {
                    if (tui.needsRender()) {
                        tui.render()
                    }
                    sleep(16)
                }
            }
        } finally {
            tui.history.saveToDisk()
            terminal.restore()
        }
    }

    fun shutdown() {
        running = false
    }

    private fun readEvent(input: InputStream): InputEvent? {
        val first = input.read()
        if (first == -1) {
            return null
        }
        return when (first) {
            10, 13 -> InputEvent.Key(InputKeys.ENTER, 0)
            9 -> InputEvent.Key(InputKeys.TAB, 0)
            127, 8 -> InputEvent.Key(InputKeys.BACKSPACE, 0)
            27 -> parseEscapeSequence(input)
            else -> {
                if (first in 32..126) {
                    InputEvent.Key(first, 0)
                } else {
                    null
                }
            }
        }
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
            '~' -> when (params) {
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
        val modifier = parseModifier(params)
        return InputEvent.Key(keyCode, modifier)
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

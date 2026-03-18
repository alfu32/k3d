package com.github.alfu32.sketch.lwjgl3.console

import com.github.alfu32.sketch.console.TerminalController
import com.github.alfu32.sketch.console.TerminalSize
import com.github.alfu32.sketch.tui.InputEvent
import com.github.alfu32.sketch.tui.InputKeys
import com.github.alfu32.sketch.tui.InputModifiers
import java.util.concurrent.TimeUnit

class LwjglTerminalController : TerminalController() {
    private val isWindows = WindowsConsole.isWindows()
    private val activationInput by lazy { WindowsConsole.openInputStream() }

    override fun awaitActivationIfNeeded() {
        if (!isWindows) {
            return
        }
        print("Octodraw Dev Console - press Enter to activate the terminal UI...\r\n")
        flushStdout()
        while (true) {
            val next = activationInput.read()
            if (next == -1 || next == '\n'.code || next == '\r'.code) {
                break
            }
        }
    }

    override fun enterRawMode() {
        if (!isWindows) {
            super.enterRawMode()
            return
        }
        WindowsConsole.enableVirtualTerminalProcessing()
        WindowsConsole.enableVirtualTerminalInput()
        enterAlternateScreen()
    }

    override fun restore() {
        if (!isWindows) {
            super.restore()
            return
        }
        leaveAlternateScreen()
        WindowsConsole.restoreInputMode()
    }

    override fun size(): TerminalSize {
        if (!isWindows) {
            return super.size()
        }
        val size = WindowsConsole.getConsoleSize()
        return if (size != null) {
            TerminalSize(size.second, size.first)
        } else {
            super.size()
        }
    }

    override fun pollEvent(timeoutMillis: Long): InputEvent? {
        if (!isWindows) {
            return super.pollEvent(timeoutMillis)
        }
        val deadline = if (timeoutMillis > 0) {
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        } else {
            Long.MIN_VALUE
        }
        while (true) {
            WindowsConsole.pollConsoleEvent()?.let { event ->
                return mapEvent(event)
            }
            if (timeoutMillis <= 0L || System.nanoTime() >= deadline) {
                return null
            }
            Thread.sleep(4L)
        }
    }

    private fun mapEvent(event: WindowsConsole.ConsoleEvent): InputEvent? {
        return when (event) {
            is WindowsConsole.KeyEvent -> InputEvent.Key(mapKey(event.key), mapModifiers(event))
            is WindowsConsole.ResizeEvent -> InputEvent.Resize(event.cols, event.rows)
        }
    }

    private fun mapKey(key: String): Int {
        return when (key) {
            "Enter" -> InputKeys.ENTER
            "Tab" -> InputKeys.TAB
            "Backspace" -> InputKeys.BACKSPACE
            "Escape" -> InputKeys.ESC
            "Delete" -> InputKeys.DELETE
            "PageUp" -> InputKeys.PAGE_UP
            "PageDown" -> InputKeys.PAGE_DOWN
            "Home" -> InputKeys.HOME
            "End" -> InputKeys.END
            "Left" -> InputKeys.LEFT
            "Up" -> InputKeys.UP
            "Right" -> InputKeys.RIGHT
            "Down" -> InputKeys.DOWN
            else -> key.firstOrNull()?.code ?: InputKeys.ESC
        }
    }

    private fun mapModifiers(event: WindowsConsole.KeyEvent): Int {
        var modifiers = 0
        if (event.ctrl) modifiers = modifiers or InputModifiers.CTRL
        if (event.alt) modifiers = modifiers or InputModifiers.ALT
        if (event.shift) modifiers = modifiers or InputModifiers.SHIFT
        return modifiers
    }
}

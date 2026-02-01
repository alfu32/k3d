package com.github.alfu32.sketch.tui

import com.github.alfu32.sketch.console.ConsoleGroovyRuntime
import com.github.alfu32.sketch.console.TerminalController
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import java.io.StringWriter
import java.util.Locale

class ConsoleTui(
    private val runtime: ConsoleGroovyRuntime,
    private val terminal: TerminalController,
    outputPane: OutputPane,
    history: HistoryManager,
    private val onQuit: () -> Unit
) {
    val outputPane: OutputPane = outputPane
    val editorPane: EditorPane = EditorPane()
    val history: HistoryManager = history

    private var state: ConsoleState = ConsoleState.IDLE
    private var editorScrollTop = 0
    @Volatile
    private var dirty = true

    init {
        outputPane.setOnAppend { markDirty() }
    }

    fun render() {
        dirty = false
        val size = terminal.size()
        val width = size.columns.coerceAtLeast(20)
        val height = size.rows.coerceAtLeast(10)
        val editorHeight = (height / 3).coerceIn(3, 8)
        val outputHeight = (height - editorHeight - 1).coerceAtLeast(1)
        val builder = StringBuilder()
        builder.append(ANSI_HIDE_CURSOR)
        builder.append(ANSI_CLEAR)
        builder.append(ANSI_HOME)

        val outputLines = outputPane.visibleLines(outputHeight, width)
        for (i in 0 until outputHeight) {
            val line = outputLines.getOrNull(i) ?: ""
            builder.append(padLine(line, width)).append('\n')
        }
        builder.append("-".repeat(width)).append('\n')

        val lines = editorPane.lines()
        val (cursorLine, cursorColumn) = editorPane.lineAndColumn(editorPane.cursorPosition)
        if (cursorLine < editorScrollTop) {
            editorScrollTop = cursorLine
        } else if (cursorLine >= editorScrollTop + editorHeight) {
            editorScrollTop = (cursorLine - editorHeight + 1).coerceAtLeast(0)
        }
        for (i in 0 until editorHeight) {
            val lineIndex = editorScrollTop + i
            val rawLine = lines.getOrNull(lineIndex) ?: ""
            val prefix = if (lineIndex == 0) "> " else "  "
            val highlighted = highlight(rawLine)
            val trimmed = truncateAnsi(highlighted, width - prefix.length)
            builder.append(padLine(prefix + trimmed, width)).append('\n')
        }
        val cursorRow = outputHeight + 1 + (cursorLine - editorScrollTop)
        val prefixLength = if (cursorLine == 0) 2 else 2
        val cursorCol = (prefixLength + cursorColumn + 1).coerceAtLeast(1)
        builder.append(ANSI_MOVE_CURSOR.format(Locale.US, cursorRow, cursorCol))
        builder.append(ANSI_SHOW_CURSOR)
        print(builder.toString())
        System.out.flush()
    }

    fun handleInput(event: InputEvent) {
        when (event) {
            is InputEvent.Key -> handleKey(event)
            is InputEvent.Mouse -> {
                // No-op for now; mouse selection is optional.
            }
        }
        markDirty()
    }

    fun needsRender(): Boolean = dirty

    private fun handleKey(event: InputEvent.Key) {
        val key = event.keyCode
        val modifiers = event.modifiers
        val isHistoryModifier = (modifiers and (InputModifiers.CTRL or InputModifiers.ALT)) != 0
        if (state == ConsoleState.OUTPUT_SCROLL && key != InputKeys.PAGE_UP && key != InputKeys.PAGE_DOWN) {
            state = if (editorPane.buffer.isEmpty()) ConsoleState.IDLE else ConsoleState.EDITING
        }
        when (key) {
            InputKeys.ENTER -> onEnter()
            InputKeys.BACKSPACE -> {
                editorPane.delete()
                if (editorPane.buffer.isNotEmpty()) {
                    state = ConsoleState.EDITING
                } else {
                    state = ConsoleState.IDLE
                }
            }
            InputKeys.DELETE -> editorPane.deleteForward()
            InputKeys.LEFT -> editorPane.moveCursor(-1)
            InputKeys.RIGHT -> editorPane.moveCursor(1)
            InputKeys.UP -> {
                if (isHistoryModifier || editorPane.buffer.isEmpty()) {
                    recallHistory(previous = true)
                } else {
                    editorPane.moveCursorVertical(-1)
                }
            }
            InputKeys.DOWN -> {
                if (isHistoryModifier || editorPane.buffer.isEmpty()) {
                    recallHistory(previous = false)
                } else {
                    editorPane.moveCursorVertical(1)
                }
            }
            InputKeys.HOME -> editorPane.moveCursorHome()
            InputKeys.END -> editorPane.moveCursorEnd()
            InputKeys.PAGE_UP -> {
                outputPane.scroll(5)
                state = ConsoleState.OUTPUT_SCROLL
            }
            InputKeys.PAGE_DOWN -> {
                outputPane.scroll(-5)
                state = ConsoleState.OUTPUT_SCROLL
            }
            InputKeys.ESC -> {
                if (state == ConsoleState.HISTORY_NAVIGATION) {
                    editorPane.clear()
                    state = ConsoleState.IDLE
                }
            }
            else -> {
                if (key >= 32) {
                    editorPane.insert(key.toChar().toString())
                    state = ConsoleState.EDITING
                }
            }
        }
    }

    private fun onEnter() {
        val source = editorPane.buffer.toString()
        if (source.isBlank()) {
            return
        }
        if (handleMetaCommand(source)) {
            editorPane.clear()
            state = ConsoleState.IDLE
            return
        }
        state = ConsoleState.EXECUTING
        try {
            val result = runtime.shell.evaluate(source)
            outputPane.append(result?.toString() ?: "null")
            history.add(source)
            editorPane.clear()
            state = ConsoleState.IDLE
        } catch (ex: Exception) {
            if (isIncompleteInput(ex)) {
                state = ConsoleState.EDITING
                return
            }
            val writer = StringWriter()
            ex.printStackTrace(java.io.PrintWriter(writer))
            outputPane.append(writer.toString().trimEnd())
            state = ConsoleState.EDITING
        }
    }

    private fun handleMetaCommand(source: String): Boolean {
        val trimmed = source.trim()
        if (!trimmed.startsWith(":") || trimmed.contains('\n')) {
            return false
        }
        return when (trimmed.lowercase()) {
            ":help" -> {
                outputPane.append(
                    "Console commands:\\n" +
                        "  :help  Show this help\\n" +
                        "  :quit  Exit the console"
                )
                true
            }
            ":quit", ":q" -> {
                outputPane.append("Exiting console.")
                onQuit()
                true
            }
            else -> {
                outputPane.append("Unknown command: $trimmed (try :help)")
                true
            }
        }
    }

    private fun recallHistory(previous: Boolean) {
        val entry = if (previous) history.previous() else history.next()
        if (entry == null) {
            return
        }
        if (entry.isEmpty()) {
            editorPane.clear()
            state = ConsoleState.IDLE
            return
        }
        editorPane.clear()
        editorPane.insert(entry)
        state = ConsoleState.HISTORY_NAVIGATION
    }

    private fun isIncompleteInput(ex: Exception): Boolean {
        if (ex is MultipleCompilationErrorsException) {
            val message = ex.message ?: ""
            if (message.contains("unexpected EOF", ignoreCase = true)) {
                return true
            }
            if (message.contains("expecting EOF", ignoreCase = true)) {
                return true
            }
            if (message.contains("unexpected token: EOF", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun markDirty() {
        dirty = true
    }

    private fun padLine(line: String, width: Int): String {
        val visibleLen = stripAnsi(line).length
        return if (visibleLen >= width) {
            line
        } else {
            line + " ".repeat(width - visibleLen)
        }
    }

    private fun highlight(line: String): String {
        if (line.isEmpty()) {
            return line
        }
        var result = line
        KEYWORDS.forEach { keyword ->
            result = result.replace(Regex("\\b${Regex.escape(keyword)}\\b")) { matchResult ->
                "$ANSI_KEYWORD${matchResult.value}$ANSI_RESET"
            }
        }
        return result
    }

    private fun stripAnsi(text: String): String {
        return text.replace(ANSI_REGEX, "")
    }

    private fun truncateAnsi(text: String, width: Int): String {
        if (width <= 0) {
            return ""
        }
        var visible = 0
        val builder = StringBuilder()
        var i = 0
        while (i < text.length && visible < width) {
            if (text[i] == '\u001B') {
                val end = text.indexOf('m', i)
                if (end != -1) {
                    builder.append(text.substring(i, end + 1))
                    i = end + 1
                    continue
                }
            }
            builder.append(text[i])
            visible++
            i++
        }
        return builder.toString()
    }

    companion object {
        private const val ANSI_CLEAR = "\u001B[2J"
        private const val ANSI_HOME = "\u001B[H"
        private const val ANSI_HIDE_CURSOR = "\u001B[?25l"
        private const val ANSI_SHOW_CURSOR = "\u001B[?25h"
        private const val ANSI_KEYWORD = "\u001B[96m"
        private const val ANSI_RESET = "\u001B[0m"
        private const val ANSI_MOVE_CURSOR = "\u001B[%d;%dH"
        private val ANSI_REGEX = Regex("\\u001B\\[[0-9;]*m")
        private val KEYWORDS = listOf(
            "def", "class", "if", "else", "for", "while", "return", "true", "false",
            "null", "new", "try", "catch", "finally", "import", "package", "switch",
            "case", "break", "continue", "as", "in"
        )
    }
}

enum class ConsoleState {
    IDLE,
    EDITING,
    EXECUTING,
    HISTORY_NAVIGATION,
    OUTPUT_SCROLL
}

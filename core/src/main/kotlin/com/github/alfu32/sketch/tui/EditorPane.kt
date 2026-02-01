package com.github.alfu32.sketch.tui

class EditorPane {
    val buffer = StringBuilder()
    var cursorPosition: Int = 0
        private set

    fun insert(text: String) {
        buffer.insert(cursorPosition, text)
        cursorPosition += text.length
    }

    fun delete() {
        if (cursorPosition <= 0) {
            return
        }
        buffer.deleteCharAt(cursorPosition - 1)
        cursorPosition -= 1
    }

    fun deleteForward() {
        if (cursorPosition >= buffer.length) {
            return
        }
        buffer.deleteCharAt(cursorPosition)
    }

    fun moveCursor(delta: Int) {
        cursorPosition = (cursorPosition + delta).coerceIn(0, buffer.length)
    }

    fun moveCursorVertical(deltaLines: Int) {
        val (line, column) = lineAndColumn(cursorPosition)
        val targetLine = (line + deltaLines).coerceAtLeast(0)
        val lines = linesWithStarts()
        if (targetLine >= lines.size) {
            cursorPosition = buffer.length
            return
        }
        val targetStart = lines[targetLine].second
        val targetLength = lines[targetLine].first.length
        cursorPosition = targetStart + column.coerceAtMost(targetLength)
    }

    fun moveCursorHome() {
        val (line, _) = lineAndColumn(cursorPosition)
        val lines = linesWithStarts()
        cursorPosition = lines.getOrNull(line)?.second ?: 0
    }

    fun moveCursorEnd() {
        val (line, _) = lineAndColumn(cursorPosition)
        val lines = linesWithStarts()
        val start = lines.getOrNull(line)?.second ?: 0
        val len = lines.getOrNull(line)?.first?.length ?: 0
        cursorPosition = start + len
    }

    fun clear() {
        buffer.clear()
        cursorPosition = 0
    }

    fun lines(): List<String> = splitPreserveTrailing(buffer.toString())

    fun lineAndColumn(index: Int): Pair<Int, Int> {
        var line = 0
        var col = 0
        for (i in 0 until index.coerceAtMost(buffer.length)) {
            if (buffer[i] == '\n') {
                line++
                col = 0
            } else {
                col++
            }
        }
        return line to col
    }

    fun linesWithStarts(): List<Pair<String, Int>> {
        val lines = mutableListOf<Pair<String, Int>>()
        var start = 0
        val parts = splitPreserveTrailing(buffer.toString())
        parts.forEach { line ->
            lines.add(line to start)
            start += line.length + 1
        }
        return lines
    }

    private fun splitPreserveTrailing(text: String): List<String> {
        if (text.isEmpty()) {
            return listOf("")
        }
        val result = mutableListOf<String>()
        var start = 0
        for (i in text.indices) {
            if (text[i] == '\n') {
                result.add(text.substring(start, i))
                start = i + 1
            }
        }
        if (start <= text.length) {
            result.add(text.substring(start))
        }
        return result
    }
}

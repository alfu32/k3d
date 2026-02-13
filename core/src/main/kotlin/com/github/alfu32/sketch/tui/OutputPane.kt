package com.github.alfu32.sketch.tui

class OutputPane(
    private var onAppend: (() -> Unit)? = null
) {
    private val lines: MutableList<String> = mutableListOf()
    private var scrollOffset = 0

    @Synchronized
    fun append(text: String) {
        val normalized = text.replace("\r\n", "\n").replace("\r", "")
        val newLines = normalized.split("\n")
        lines.addAll(newLines)
        if (scrollOffset > 0) {
            scrollOffset += newLines.size
        }
        onAppend?.invoke()
    }

    fun setOnAppend(callback: (() -> Unit)?) {
        onAppend = callback
    }

    @Synchronized
    fun scroll(delta: Int) {
        if (lines.isEmpty()) {
            scrollOffset = 0
            return
        }
        scrollOffset = (scrollOffset + delta).coerceAtLeast(0)
        val maxOffset = (lines.size - 1).coerceAtLeast(0)
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset
        }
    }

    @Synchronized
    fun visibleLines(height: Int, width: Int): List<String> {
        if (height <= 0) {
            return emptyList()
        }
        val endIndex = (lines.size - scrollOffset).coerceAtLeast(0)
        val startIndex = (endIndex - height).coerceAtLeast(0)
        return lines.subList(startIndex, endIndex).map { line ->
            if (line.length > width) line.substring(0, width) else line
        }
    }

    @Synchronized
    fun isScrolledUp(): Boolean = scrollOffset > 0

    @Synchronized
    fun lineCount(): Int = lines.size

    @Synchronized
    fun linesSince(startIndex: Int): List<String> {
        if (startIndex < 0) {
            return lines.toList()
        }
        if (startIndex >= lines.size) {
            return emptyList()
        }
        return lines.subList(startIndex, lines.size).toList()
    }
}

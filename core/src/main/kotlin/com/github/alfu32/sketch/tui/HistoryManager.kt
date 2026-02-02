package com.github.alfu32.sketch.tui

import java.io.File
import java.util.Base64

class HistoryManager(
    private val file: File,
    private val maxEntries: Int = 500
) {
    private val entries: MutableList<String> = mutableListOf()
    private var index: Int = -1

    fun add(entry: String) {
        entries.add(entry)
        prune()
        index = -1
    }

    fun previous(): String? {
        if (entries.isEmpty()) {
            return null
        }
        if (index == -1) {
            index = entries.size - 1
        } else if (index > 0) {
            index--
        }
        return entries.getOrNull(index)
    }

    fun next(): String? {
        if (entries.isEmpty()) {
            return null
        }
        if (index == -1) {
            return null
        }
        if (index < entries.size - 1) {
            index++
            return entries[index]
        }
        index = -1
        return ""
    }

    fun resetNavigation() {
        index = -1
    }

    fun entries(): List<String> = entries.toList()

    fun loadFromDisk() {
        if (!file.exists()) {
            return
        }
        file.readLines().forEach { line ->
            val decoded = runCatching {
                String(Base64.getDecoder().decode(line), Charsets.UTF_8)
            }.getOrNull() ?: return@forEach
            entries.add(decoded)
        }
        prune()
        index = -1
    }

    fun saveToDisk() {
        file.parentFile?.mkdirs()
        prune()
        val lines = entries.map {
            Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
        }
        file.writeText(lines.joinToString("\n"))
    }

    private fun prune() {
        if (entries.size <= maxEntries) {
            return
        }
        val overflow = entries.size - maxEntries
        repeat(overflow) { entries.removeAt(0) }
    }
}

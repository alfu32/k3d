package com.github.alfu32.sketch.plugin

class CommandPalette {
    private val commands = mutableListOf<PaletteCommand>()
    private val commandHistory = mutableListOf<String>()
    private var isVisible = false
    private var currentSearch = ""
    private val maxHistory = 20

    fun registerCommand(command: PaletteCommand) {
        if (commands.none { it.id == command.id }) {
            commands.add(command)
        }
    }

    fun show() {
        isVisible = true
        currentSearch = ""
    }

    fun hide() {
        isVisible = false
    }

    fun toggle() {
        isVisible = !isVisible
        if (isVisible) {
            currentSearch = ""
        }
    }

    fun executeCommand(commandId: String): PluginResult {
        val command = commands.find { it.id == commandId }
        return command?.execute() ?: PluginResult(success = false, message = "Command not found")
    }

    fun searchCommands(query: String): List<PaletteCommand> {
        currentSearch = query
        return if (query.isBlank()) {
            val recent = getRecentCommands()
            (recent + commands.filter { !recent.contains(it) })
                .distinct()
                .take(20)
        } else {
            commands.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true) ||
                it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }.sortedBy { it.priority }
        }
    }

    fun getRecentCommands(): List<PaletteCommand> {
        return commandHistory.mapNotNull { id ->
            commands.find { it.id == id }
        }.distinct()
    }

    fun getVisibleCommands(): List<PaletteCommand> {
        return if (isVisible) searchCommands(currentSearch) else emptyList()
    }

    fun addToHistory(commandId: String) {
        commandHistory.remove(commandId)
        commandHistory.add(0, commandId)
        if (commandHistory.size > maxHistory) {
            commandHistory.removeAt(commandHistory.size - 1)
        }
    }

    fun allCommands(): List<PaletteCommand> = commands.toList().sortedBy { it.name.lowercase() }

    fun isVisible(): Boolean = isVisible
}

data class PaletteCommand(
    val id: String,
    val name: String,
    val description: String,
    val icon: String?,
    val category: String,
    val tags: List<String>,
    val priority: Int,
    val execute: () -> PluginResult
)

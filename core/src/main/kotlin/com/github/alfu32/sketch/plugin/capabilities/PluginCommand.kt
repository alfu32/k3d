package com.github.alfu32.sketch.plugin.capabilities

import com.github.alfu32.sketch.plugin.PluginContext
import com.github.alfu32.sketch.plugin.PluginResult

interface PluginCommand {
    val id: String
    val name: String
    val description: String
    val category: String
    val shortcut: KeyBinding?
    val icon: String?
    val isVisibleInPalette: Boolean

    fun execute(context: PluginContext): PluginResult
    fun isAvailable(context: PluginContext): Boolean = true
    fun getSearchTags(): List<String> = listOf(name.lowercase())
}

data class KeyBinding(
    val key: Int,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false
)
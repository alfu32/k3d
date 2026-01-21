package com.github.alfu32.sketch.plugin.capabilities

import com.github.alfu32.sketch.plugin.PluginContext
import com.github.alfu32.sketch.plugin.PluginResult
import java.io.File

interface PluginExporter {
    val id: String
    val name: String
    val description: String
    val fileExtensions: List<String>
    val supportsSelectionOnly: Boolean

    fun export(
        context: PluginContext,
        file: File,
        selectionOnly: Boolean,
        options: Map<String, Any>
    ): PluginResult
}

interface PluginImporter {
    val id: String
    val name: String
    val description: String
    val fileExtensions: List<String>

    fun import(
        context: PluginContext,
        file: File,
        options: Map<String, Any>
    ): PluginResult
}
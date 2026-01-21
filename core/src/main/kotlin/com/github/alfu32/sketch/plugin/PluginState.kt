package com.github.alfu32.sketch.plugin

/**
 * Represents the state of a plugin that can be persisted.
 */
data class PluginState(
    val data: Map<String, Any> = emptyMap()
)
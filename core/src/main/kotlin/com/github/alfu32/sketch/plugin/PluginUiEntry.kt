package com.github.alfu32.sketch.plugin

import com.github.alfu32.sketch.plugin.capabilities.PluginUIElement

data class PluginUiEntry(
    val pluginId: String,
    val pluginName: String,
    val element: PluginUIElement
)

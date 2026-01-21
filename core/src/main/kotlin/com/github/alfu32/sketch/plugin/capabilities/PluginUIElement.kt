package com.github.alfu32.sketch.plugin.capabilities

import com.kotcrab.vis.ui.widget.VisTable

sealed interface PluginUIElement

data class PluginPanel(
    val id: String,
    val title: String,
    val width: Float,
    val height: Float,
    val position: PanelPosition,
    val creator: (Any) -> VisTable  // Takes PluginContext
) : PluginUIElement

data class PluginMenuItem(
    val id: String,
    val parentMenu: String,
    val title: String,
    val action: (Any) -> Unit,  // Takes PluginContext
    val shortcut: KeyBinding? = null,
    val icon: String? = null
) : PluginUIElement

data class PluginToolbarButton(
    val id: String,
    val group: String,
    val icon: String,
    val tooltip: String,
    val action: (Any) -> Unit,  // Takes PluginContext
    val isToggle: Boolean = false
) : PluginUIElement

enum class PanelPosition {
    LEFT, RIGHT, BOTTOM, FLOATING
}
package com.github.alfu32.sketch.plugin

import com.github.alfu32.sketch.plugin.capabilities.*
import com.github.alfu32.sketch.ui.ToolId

interface Plugin {
    // Core properties (required)
    val id: String
    val name: String
    val version: String

    // Optional properties with defaults
    val author: String
        get() = "Unknown"

    val description: String
        get() = ""

    // Lifecycle methods (all with defaults)
    fun onLoad(context: PluginContext): PluginResult = PluginResult.success()
    fun onEnable(context: PluginContext): PluginResult = PluginResult.success()
    fun onDisable(context: PluginContext): PluginResult = PluginResult.success()
    fun onUnload(context: PluginContext): PluginResult = PluginResult.success()
    fun onCreate(context: PluginContext): PluginResult = PluginResult.success()
    fun onUpdate(context: PluginContext, deltaSeconds: Float): PluginResult = PluginResult.success()
    fun onDraw(context: PluginContext): PluginDraw = PluginDraw()
    fun onSave(context: PluginContext): PluginResult = PluginResult.success()
    fun onClose(context: PluginContext): PluginResult = PluginResult.success()

    // New capability methods (all with defaults)
    fun registerCommands(): List<PluginCommand> = emptyList()
    fun registerTools(): List<PluginTool> = emptyList()
    fun registerEntityTypes(): List<PluginEntityType> = emptyList()
    fun registerUIElements(): List<PluginUIElement> = emptyList()
    fun registerExporters(): List<PluginExporter> = emptyList()
    fun registerImporters(): List<PluginImporter> = emptyList()

    // New event handlers (all with defaults)
    fun onSceneLoad(context: PluginContext): PluginResult = PluginResult.success()
    fun onSceneSave(context: PluginContext): PluginResult = PluginResult.success()
    fun onSelectionChanged(context: PluginContext): PluginResult = PluginResult.success()
    fun onToolChanged(context: PluginContext, newTool: ToolId): PluginResult = PluginResult.success()

    // State management (with defaults)
    fun saveState(): PluginState = PluginState()
    fun restoreState(state: PluginState): PluginResult = PluginResult.success()
}

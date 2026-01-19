package com.github.alfu32.sketch.plugin

interface Plugin {
    val id: String
    val name: String
    val version: String

    fun onLoad(context: PluginContext): PluginResult = PluginResult()
    fun onCreate(context: PluginContext): PluginResult = PluginResult()
    fun onUpdate(context: PluginContext, deltaSeconds: Float): PluginResult = PluginResult()
    fun onDraw(context: PluginContext): PluginDraw = PluginDraw()
    fun onSave(context: PluginContext): PluginResult = PluginResult()
    fun onClose(context: PluginContext): PluginResult = PluginResult()
}

package com.github.alfu32.sketch.plugin

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val isEnabled: Boolean,
    val isScript: Boolean
)
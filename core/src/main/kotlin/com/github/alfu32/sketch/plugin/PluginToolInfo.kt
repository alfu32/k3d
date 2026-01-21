package com.github.alfu32.sketch.plugin

import com.badlogic.gdx.scenes.scene2d.utils.Drawable

data class PluginToolInfo(
    val id: String,
    val pluginId: String,
    val pluginName: String,
    val name: String,
    val description: String,
    val icon: String,
    val iconDrawable: Drawable?
)

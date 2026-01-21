package com.github.alfu32.sketch.plugin.capabilities

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.github.alfu32.sketch.plugin.PluginContext
import com.github.alfu32.sketch.ui.ToolId
import com.badlogic.gdx.scenes.scene2d.utils.Drawable

interface PluginTool {
    val id: String
    val name: String
    val description: String
    val icon: String
    val iconDrawable: Drawable?
        get() = null
    val cursor: String
    val category: ToolCategory
    val visibleInPalette: Boolean
        get() = true

    fun onActivate(context: PluginContext)
    fun onDeactivate(context: PluginContext)
    fun onMouseMove(context: PluginContext, screenX: Int, screenY: Int)
    fun onMouseDown(context: PluginContext, button: Int, screenX: Int, screenY: Int)
    fun onMouseUp(context: PluginContext, button: Int, screenX: Int, screenY: Int)
    fun onKeyDown(context: PluginContext, keycode: Int): Boolean
    fun onKeyUp(context: PluginContext, keycode: Int): Boolean
    fun onDraw2D(context: PluginContext, batch: SpriteBatch)
    fun onDraw3D(context: PluginContext, shapeRenderer: ShapeRenderer)
    fun onUpdate(context: PluginContext, delta: Float)
}

enum class ToolCategory {
    DRAWING, MODIFICATION, SELECTION, MEASUREMENT, UTILITY, CUSTOM
}

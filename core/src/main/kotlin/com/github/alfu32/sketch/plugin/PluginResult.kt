package com.github.alfu32.sketch.plugin

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.ModelPersistence

data class PluginResult(
    val changes: List<PluginChange> = emptyList(),
    val success: Boolean = true,
    val message: String? = null
) {
    companion object {
        fun success(): PluginResult = PluginResult()
        fun failure(message: String): PluginResult = PluginResult(success = false, message = message)
    }
}

sealed class PluginChange {
    data class ReplaceModel(val snapshot: ModelPersistence.ModelSnapshot) : PluginChange()
    data class StatusMessage(val message: String) : PluginChange()
}

data class PluginDraw(
    val lines: List<PluginLine> = emptyList()
)

data class PluginLine(
    val start: Vector3,
    val end: Vector3,
    val color: Color = Color(0.9f, 0.9f, 0.2f, 1f),
    val width: Float = 2f
)

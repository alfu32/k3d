package com.github.alfu32.sketch.ui

class StatusModel(
    var activeTool: ToolId = ToolId.SELECT,
    var message: String = "",
    var backgroundStatus: String = "",
    var inputBuffer: String = "",
    var cursorScreenX: Int = 0,
    var cursorScreenY: Int = 0,
    var cursorWorld: String = "",
    var cursorSnapLabel: String = "",
    var paintColor: com.badlogic.gdx.graphics.Color = com.badlogic.gdx.graphics.Color(0.8f, 0.8f, 0.8f, 1f),
    var copyMode: Boolean = false,
    var anchorWorld: com.badlogic.gdx.math.Vector3? = null
) {
    fun clearInput() {
        inputBuffer = ""
    }
}

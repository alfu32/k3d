package com.github.alfu32.sketch.ui

class StatusModel(
    var activeTool: ToolId = ToolId.SELECT,
    var message: String = "",
    var inputBuffer: String = "",
    var cursorScreenX: Int = 0,
    var cursorScreenY: Int = 0,
    var cursorWorld: String = "",
    var cursorSnapLabel: String = ""
) {
    fun clearInput() {
        inputBuffer = ""
    }
}

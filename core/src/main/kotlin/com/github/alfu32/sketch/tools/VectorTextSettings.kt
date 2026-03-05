package com.github.alfu32.sketch.tools

data class VectorTextSettings(
    var text: String = "TEXT",
    var size: Float = 1f,
    var tracking: Float = 0.1f,
    var lineSpacing: Float = 1.25f,
    var glyphSourcePath: String = "embedded:alphabet"
)

package com.github.alfu32.sketch.tools

data class ArchitectureSettings(
    var wallThickness: Float = 0.2f,
    var wallHeight: Float = 2.7f,
    var wallInclinationDeg: Float = 0f,
    var slabThickness: Float = 0.2f,
    var stairHeight: Float = 2.7f,
    var stairStepCount: Int = 14,
    var stairSupportThickness: Float = 0.2f,
    var frameDepth: Float = 0.12f,
    var frameWidth: Float = 0.06f
)

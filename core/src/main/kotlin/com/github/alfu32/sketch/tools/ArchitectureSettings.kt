package com.github.alfu32.sketch.tools

import com.badlogic.gdx.graphics.Color

data class ArchitectureSettings(
    var wallThickness: Float = 0.2f,
    var wallHeight: Float = 2.7f,
    var wallInclinationDeg: Float = 0f,
    var wallExteriorColor: Color = Color(0.93f, 0.93f, 0.93f, 1f),
    var wallInteriorColor: Color = Color(0.84f, 0.84f, 0.84f, 1f),
    var slabThickness: Float = 0.2f,
    var slabTopColor: Color = Color(0.93f, 0.93f, 0.93f, 1f),
    var slabBottomColor: Color = Color(0.84f, 0.84f, 0.84f, 1f),
    var slabSideColor: Color = Color(0.88f, 0.88f, 0.88f, 1f),
    var stairHeight: Float = 2.7f,
    var stairStepCount: Int = 14,
    var stairSupportThickness: Float = 0.2f,
    var stairTreadColor: Color = Color(0.93f, 0.93f, 0.93f, 1f),
    var stairSupportColor: Color = Color(0.82f, 0.82f, 0.82f, 1f),
    var frameDepth: Float = 0.12f,
    var frameWidth: Float = 0.06f,
    var frameColor: Color = Color(0.90f, 0.90f, 0.90f, 1f)
)

package com.github.alfu32.sketch.tools

import com.badlogic.gdx.graphics.Color

data class HvacSettings(
    var plumbingDiameter: Float = 0.2f,
    var plumbingSides: Int = 16,
    var plumbingColor: Color = Color(0.70f, 0.82f, 0.95f, 1f),
    var ventilationWidth: Float = 0.5f,
    var ventilationHeight: Float = 0.25f,
    var ventilationColor: Color = Color(0.82f, 0.82f, 0.82f, 1f)
)

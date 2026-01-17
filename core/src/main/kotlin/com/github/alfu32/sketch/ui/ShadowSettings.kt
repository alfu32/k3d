package com.github.alfu32.sketch.ui

data class ShadowSettings(
    var shadowBias: Float,
    var shadowNormalBias: Float,
    var pcfMode: Int,
    var dither: Boolean,
    var useCsm: Boolean
)

package com.github.alfu32.sketch.ui

data class LightingSettings(
    var shadowLightValue: Float,
    var shadowLightAlpha: Float,
    var directionalLightValue: Float,
    var directionalLightAlpha: Float,
    var ambientLightValue: Float,
    var ambientLightAlpha: Float,
    var specularLightValue: Float,
    var specularLightAlpha: Float
)

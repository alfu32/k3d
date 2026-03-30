package com.github.alfu32.sketch.render

enum class RenderMode {
    RAYTRACE,
    PATHTRACE;

    fun displayName(): String = when (this) {
        RAYTRACE -> "Raytrace"
        PATHTRACE -> "GI / Pathtrace"
    }
}

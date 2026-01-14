package com.github.alfu32.sketch.input

import com.badlogic.gdx.math.Vector3

data class SnapResult(
    val world: Vector3?,
    val normal: Vector3?,
    val type: SnapType,
    val screenX: Int,
    val screenY: Int,
    val valid: Boolean
)

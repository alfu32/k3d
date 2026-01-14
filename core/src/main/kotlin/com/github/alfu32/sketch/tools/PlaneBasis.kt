package com.github.alfu32.sketch.tools

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs

class PlaneBasis(val normal: Vector3, val axisU: Vector3, val axisV: Vector3)

fun planeBasisFromNormal(normal: Vector3): PlaneBasis {
    val n = Vector3(normal).nor()
    val ref = if (abs(n.y) < 0.9f) Vector3(0f, 1f, 0f) else Vector3(1f, 0f, 0f)
    val axisU = Vector3(n).crs(ref).nor()
    val axisV = Vector3(n).crs(axisU).nor()
    return PlaneBasis(n, axisU, axisV)
}

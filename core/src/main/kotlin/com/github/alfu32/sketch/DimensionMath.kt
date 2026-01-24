package com.github.alfu32.sketch

import com.badlogic.gdx.math.Vector3

object DimensionMath {
    private const val EPS = 1e-6f

    fun computeOffsetLine(start: Vector3, end: Vector3, offset: Vector3): Pair<Vector3, Vector3> {
        val dir = Vector3(end).sub(start)
        if (dir.len2() < EPS) {
            return Vector3(start) to Vector3(end)
        }
        dir.nor()
        val projLen = Vector3(offset).sub(start).dot(dir)
        val projPoint = Vector3(start).mulAdd(dir, projLen)
        val shift = Vector3(offset).sub(projPoint)
        return Vector3(start).add(shift) to Vector3(end).add(shift)
    }

    fun computeOffsetDirection(start: Vector3, end: Vector3, offset: Vector3): Vector3 {
        val dir = Vector3(end).sub(start)
        if (dir.len2() < EPS) {
            return Vector3(0f, 1f, 0f)
        }
        dir.nor()
        var normal = Vector3(end).sub(start).crs(Vector3(offset).sub(start))
        if (normal.len2() < EPS) {
            normal = Vector3(0f, 1f, 0f).crs(dir)
            if (normal.len2() < EPS) {
                normal = Vector3(1f, 0f, 0f).crs(dir)
            }
        }
        val offsetDir = normal.crs(dir)
        if (offsetDir.len2() < EPS) {
            return Vector3(0f, 1f, 0f)
        }
        return offsetDir.nor()
    }
}

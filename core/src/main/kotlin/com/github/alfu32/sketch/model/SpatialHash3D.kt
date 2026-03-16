package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class SpatialHash3D<T>(
    private val cellSize: Float,
    private val keyOf: (T) -> String
) {
    data class CellKey(val x: Int, val y: Int, val z: Int)

    private val cells = linkedMapOf<CellKey, MutableList<T>>()

    fun clear() {
        cells.clear()
    }

    fun insertAabb(min: Vector3, max: Vector3, item: T) {
        val minKey = cellKeyOf(min)
        val maxKey = cellKeyOf(max)
        for (x in minKey.x..maxKey.x) {
            for (y in minKey.y..maxKey.y) {
                for (z in minKey.z..maxKey.z) {
                    cells.getOrPut(CellKey(x, y, z)) { mutableListOf() }.add(item)
                }
            }
        }
    }

    fun queryAabb(min: Vector3, max: Vector3): List<T> {
        if (cells.isEmpty()) {
            return emptyList()
        }
        val minX = min(min.x, max.x)
        val minY = min(min.y, max.y)
        val minZ = min(min.z, max.z)
        val maxX = max(min.x, max.x)
        val maxY = max(min.y, max.y)
        val maxZ = max(min.z, max.z)
        val minKey = cellKey(minX, minY, minZ)
        val maxKey = cellKey(maxX, maxY, maxZ)
        val deduped = linkedMapOf<String, T>()
        for (x in minKey.x..maxKey.x) {
            for (y in minKey.y..maxKey.y) {
                for (z in minKey.z..maxKey.z) {
                    cells[CellKey(x, y, z)].orEmpty().forEach { item ->
                        deduped.putIfAbsent(keyOf(item), item)
                    }
                }
            }
        }
        return deduped.values.toList()
    }

    private fun cellKeyOf(point: Vector3): CellKey = cellKey(point.x, point.y, point.z)

    private fun cellKey(x: Float, y: Float, z: Float): CellKey {
        return CellKey(
            floor(x / cellSize).toInt(),
            floor(y / cellSize).toInt(),
            floor(z / cellSize).toInt()
        )
    }

    companion object {
        fun rayAabbRange(
            origin: Vector3,
            direction: Vector3,
            min: Vector3,
            max: Vector3,
            epsilon: Float
        ): FloatArray? {
            var tMin = 0f
            var tMax = Float.POSITIVE_INFINITY

            fun update(originValue: Float, dirValue: Float, minValue: Float, maxValue: Float): Boolean {
                if (kotlin.math.abs(dirValue) <= epsilon) {
                    return originValue >= minValue - epsilon && originValue <= maxValue + epsilon
                }
                val inv = 1f / dirValue
                var t1 = (minValue - originValue) * inv
                var t2 = (maxValue - originValue) * inv
                if (t1 > t2) {
                    val tmp = t1
                    t1 = t2
                    t2 = tmp
                }
                tMin = max(tMin, t1)
                tMax = min(tMax, t2)
                return tMin <= tMax
            }

            if (!update(origin.x, direction.x, min.x, max.x)) return null
            if (!update(origin.y, direction.y, min.y, max.y)) return null
            if (!update(origin.z, direction.z, min.z, max.z)) return null
            if (tMax < 0f) {
                return null
            }
            return floatArrayOf(max(0f, tMin), tMax)
        }
    }
}

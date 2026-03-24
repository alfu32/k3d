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

    private val cells = linkedMapOf<CellKey, LinkedHashMap<String, T>>()
    private val itemCells = linkedMapOf<String, MutableSet<CellKey>>()

    fun clear() {
        cells.clear()
        itemCells.clear()
    }

    fun insertAabb(min: Vector3, max: Vector3, item: T) {
        upsertAabb(min, max, item)
    }

    fun upsertAabb(min: Vector3, max: Vector3, item: T) {
        val key = keyOf(item)
        val newKeys = cellKeysForAabb(min, max)
        val oldKeys = itemCells[key]

        if (oldKeys != null) {
            oldKeys.filter { it !in newKeys }.forEach { cellKey ->
                cells[cellKey]?.let { bucket ->
                    bucket.remove(key)
                    if (bucket.isEmpty()) {
                        cells.remove(cellKey)
                    }
                }
            }
        }

        newKeys.forEach { cellKey ->
            cells.getOrPut(cellKey) { linkedMapOf() }[key] = item
        }
        itemCells[key] = newKeys
    }

    fun remove(item: T) {
        removeByKey(keyOf(item))
    }

    fun removeByKey(key: String) {
        val oldKeys = itemCells.remove(key) ?: return
        oldKeys.forEach { cellKey ->
            cells[cellKey]?.let { bucket ->
                bucket.remove(key)
                if (bucket.isEmpty()) {
                    cells.remove(cellKey)
                }
            }
        }
    }

    fun queryAabb(min: Vector3, max: Vector3): List<T> {
        if (cells.isEmpty()) {
            return emptyList()
        }
        val deduped = linkedMapOf<String, T>()
        cellKeysForAabb(min, max).forEach { cellKey ->
            cells[cellKey].orEmpty().forEach { (key, item) ->
                deduped.putIfAbsent(key, item)
            }
        }
        return deduped.values.toList()
    }

    private fun cellKeyOf(point: Vector3): CellKey = cellKey(point.x, point.y, point.z)

    private fun cellKeysForAabb(min: Vector3, max: Vector3): MutableSet<CellKey> {
        val minX = min(min.x, max.x)
        val minY = min(min.y, max.y)
        val minZ = min(min.z, max.z)
        val maxX = max(min.x, max.x)
        val maxY = max(min.y, max.y)
        val maxZ = max(min.z, max.z)
        val minKey = cellKey(minX, minY, minZ)
        val maxKey = cellKey(maxX, maxY, maxZ)
        val result = linkedSetOf<CellKey>()
        for (x in minKey.x..maxKey.x) {
            for (y in minKey.y..maxKey.y) {
                for (z in minKey.z..maxKey.z) {
                    result.add(CellKey(x, y, z))
                }
            }
        }
        return result
    }

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

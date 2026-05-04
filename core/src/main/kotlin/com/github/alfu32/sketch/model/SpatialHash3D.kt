package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.perf.PerfStats
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class SpatialHash3D<T>(
    private val cellSize: Float,
    private val keyOf: (T) -> String,
    private val debugName: String = "spatial"
) {
    companion object {
        private const val PACKED_COORD_BITS = 21
        private const val PACKED_COORD_MASK = (1 shl PACKED_COORD_BITS) - 1
        private const val PACKED_COORD_OFFSET = 1 shl (PACKED_COORD_BITS - 1)
        private const val PACKED_COORD_MIN = -PACKED_COORD_OFFSET
        private const val PACKED_COORD_MAX = PACKED_COORD_OFFSET - 1

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

    private class CellBucket<T>(
        val x: Int,
        val y: Int,
        val z: Int
    ) {
        val items = HashMap<String, T>()
    }

    private data class OverflowCellKey(
        val x: Int,
        val y: Int,
        val z: Int
    )

    private val packedCells = HashMap<Long, CellBucket<T>>()
    private val overflowCells = HashMap<OverflowCellKey, CellBucket<T>>()
    private val itemCells = HashMap<String, MutableList<CellBucket<T>>>()
    private val seenStamps = HashMap<String, Int>()
    private var queryStamp = 0

    fun clear() {
        packedCells.clear()
        overflowCells.clear()
        itemCells.clear()
        seenStamps.clear()
        queryStamp = 0
    }

    fun insertAabb(min: Vector3, max: Vector3, item: T) {
        upsertAabb(min, max, item)
    }

    fun upsertAabb(min: Vector3, max: Vector3, item: T) {
        val key = keyOf(item)
        val newBuckets = bucketsForAabb(min, max, create = true)
        val oldBuckets = itemCells[key]

        if (oldBuckets != null) {
            oldBuckets.forEach { bucket ->
                if (!newBuckets.contains(bucket)) {
                    bucket.items.remove(key)
                    removeBucketIfEmpty(bucket)
                }
            }
        }

        newBuckets.forEach { bucket ->
            bucket.items[key] = item
        }
        itemCells[key] = newBuckets
    }

    fun remove(item: T) {
        removeByKey(keyOf(item))
    }

    fun removeByKey(key: String) {
        val oldBuckets = itemCells.remove(key) ?: return
        oldBuckets.forEach { bucket ->
            bucket.items.remove(key)
            removeBucketIfEmpty(bucket)
        }
        seenStamps.remove(key)
    }

    fun queryAabb(min: Vector3, max: Vector3): List<T> {
        if (packedCells.isEmpty() && overflowCells.isEmpty()) {
            return emptyList()
        }
        val result = ArrayList<T>()
        forEachAabb(min, max) { item ->
            result.add(item)
            true
        }
        return result
    }

    fun forEachAabb(min: Vector3, max: Vector3, visitor: (T) -> Boolean): Int {
        if (packedCells.isEmpty() && overflowCells.isEmpty()) {
            return 0
        }
        return PerfStats.measure("spatial.$debugName.queryAabb") {
            var count = 0
            val stamp = nextQueryStamp()
            val minX = min(min.x, max.x)
            val minY = min(min.y, max.y)
            val minZ = min(min.z, max.z)
            val maxX = max(min.x, max.x)
            val maxY = max(min.y, max.y)
            val maxZ = max(min.z, max.z)
            val minCellX = floor(minX / cellSize).toInt()
            val minCellY = floor(minY / cellSize).toInt()
            val minCellZ = floor(minZ / cellSize).toInt()
            val maxCellX = floor(maxX / cellSize).toInt()
            val maxCellY = floor(maxY / cellSize).toInt()
            val maxCellZ = floor(maxZ / cellSize).toInt()
            for (x in minCellX..maxCellX) {
                for (y in minCellY..maxCellY) {
                    for (z in minCellZ..maxCellZ) {
                        val bucket = findBucket(x, y, z) ?: continue
                        bucket.items.forEach { (key, item) ->
                            if (seenStamps.put(key, stamp) != stamp) {
                                count++
                                if (!visitor(item)) {
                                    return@measure count
                                }
                            }
                        }
                    }
                }
            }
            count
        }
    }

    private fun bucketsForAabb(min: Vector3, max: Vector3, create: Boolean): MutableList<CellBucket<T>> {
        val minX = min(min.x, max.x)
        val minY = min(min.y, max.y)
        val minZ = min(min.z, max.z)
        val maxX = max(min.x, max.x)
        val maxY = max(min.y, max.y)
        val maxZ = max(min.z, max.z)
        val minCellX = floor(minX / cellSize).toInt()
        val minCellY = floor(minY / cellSize).toInt()
        val minCellZ = floor(minZ / cellSize).toInt()
        val maxCellX = floor(maxX / cellSize).toInt()
        val maxCellY = floor(maxY / cellSize).toInt()
        val maxCellZ = floor(maxZ / cellSize).toInt()
        val result = ArrayList<CellBucket<T>>()
        for (x in minCellX..maxCellX) {
            for (y in minCellY..maxCellY) {
                for (z in minCellZ..maxCellZ) {
                    val bucket = findOrCreateBucket(x, y, z, create) ?: continue
                    result.add(bucket)
                }
            }
        }
        return result
    }

    private fun findOrCreateBucket(x: Int, y: Int, z: Int, create: Boolean): CellBucket<T>? {
        if (canPackCell(x, y, z)) {
            val key = packCellKey(x, y, z)
            if (!create) {
                return packedCells[key]
            }
            return packedCells.getOrPut(key) { CellBucket(x, y, z) }
        }
        val key = OverflowCellKey(x, y, z)
        if (!create) {
            return overflowCells[key]
        }
        return overflowCells.getOrPut(key) { CellBucket(x, y, z) }
    }

    private fun findBucket(
        x: Int,
        y: Int,
        z: Int
    ): CellBucket<T>? {
        if (canPackCell(x, y, z)) {
            return packedCells[packCellKey(x, y, z)]
        }
        return overflowCells[OverflowCellKey(x, y, z)]
    }

    private fun removeBucketIfEmpty(bucket: CellBucket<T>) {
        if (bucket.items.isNotEmpty()) {
            return
        }
        if (canPackCell(bucket.x, bucket.y, bucket.z)) {
            packedCells.remove(packCellKey(bucket.x, bucket.y, bucket.z), bucket)
        } else {
            overflowCells.remove(OverflowCellKey(bucket.x, bucket.y, bucket.z), bucket)
        }
    }

    private fun nextQueryStamp(): Int {
        if (queryStamp == Int.MAX_VALUE) {
            seenStamps.clear()
            queryStamp = 0
        }
        queryStamp += 1
        return queryStamp
    }

    private fun canPackCell(x: Int, y: Int, z: Int): Boolean {
        return x in PACKED_COORD_MIN..PACKED_COORD_MAX &&
            y in PACKED_COORD_MIN..PACKED_COORD_MAX &&
            z in PACKED_COORD_MIN..PACKED_COORD_MAX
    }

    private fun packCellKey(x: Int, y: Int, z: Int): Long {
        val px = (x + PACKED_COORD_OFFSET).toLong() and PACKED_COORD_MASK.toLong()
        val py = (y + PACKED_COORD_OFFSET).toLong() and PACKED_COORD_MASK.toLong()
        val pz = (z + PACKED_COORD_OFFSET).toLong() and PACKED_COORD_MASK.toLong()
        return (px shl (PACKED_COORD_BITS * 2)) or
            (py shl PACKED_COORD_BITS) or
            pz
    }
}

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
    private class CellBucket<T>(
        val x: Int,
        val y: Int,
        val z: Int
    ) {
        val items = linkedMapOf<String, T>()
    }

    private val cellsByHash = linkedMapOf<Long, MutableList<CellBucket<T>>>()
    private val itemCells = linkedMapOf<String, MutableList<CellBucket<T>>>()
    private val seenStamps = linkedMapOf<String, Int>()
    private var queryStamp = 0

    fun clear() {
        cellsByHash.clear()
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
        if (cellsByHash.isEmpty()) {
            return emptyList()
        }
        return PerfStats.measure("spatial.$debugName.queryAabb") {
            val result = ArrayList<T>()
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
                        val bucket = findBucket(cellsByHash[cellHash(x, y, z)], x, y, z) ?: continue
                        bucket.items.forEach { (key, item) ->
                            if (seenStamps.put(key, stamp) != stamp) {
                                result.add(item)
                            }
                        }
                    }
                }
            }
            result
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
        val hash = cellHash(x, y, z)
        val buckets = cellsByHash[hash] ?: if (create) {
            mutableListOf<CellBucket<T>>().also { cellsByHash[hash] = it }
        } else {
            return null
        }
        val existing = findBucket(buckets, x, y, z)
        if (existing != null || !create) {
            return existing
        }
        return CellBucket<T>(x, y, z).also { buckets.add(it) }
    }

    private fun findBucket(
        buckets: List<CellBucket<T>>?,
        x: Int,
        y: Int,
        z: Int
    ): CellBucket<T>? {
        buckets?.forEach { bucket ->
            if (bucket.x == x && bucket.y == y && bucket.z == z) {
                return bucket
            }
        }
        return null
    }

    private fun removeBucketIfEmpty(bucket: CellBucket<T>) {
        if (bucket.items.isNotEmpty()) {
            return
        }
        val hash = cellHash(bucket.x, bucket.y, bucket.z)
        val buckets = cellsByHash[hash] ?: return
        buckets.remove(bucket)
        if (buckets.isEmpty()) {
            cellsByHash.remove(hash)
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

    private fun cellHash(x: Int, y: Int, z: Int): Long {
        var hash = -3750763034362895579L
        hash = mix(hash, x.toLong())
        hash = mix(hash, y.toLong())
        hash = mix(hash, z.toLong())
        return hash
    }

    private fun mix(seed: Long, value: Long): Long {
        var mixed = seed xor (value - 7046029254386353131L + (seed shl 6) + (seed ushr 2))
        mixed = mixed xor (mixed ushr 33)
        mixed *= -4417276706812531889L
        mixed = mixed xor (mixed ushr 29)
        return mixed
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

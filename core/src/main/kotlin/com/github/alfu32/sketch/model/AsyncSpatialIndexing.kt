package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.github.alfu32.sketch.BuildFlags

data class IndexedAabbSnapshot<T>(
    val item: T,
    val min: Vector3,
    val max: Vector3
)

data class AsyncAabbIndexResult<T>(
    val generation: Long,
    val index: SpatialHash3D<T>,
    val boundsMin: Vector3,
    val boundsMax: Vector3,
    val hasBounds: Boolean,
    val boundsByKey: Map<String, BoundingBox>
)

object IndexingStatusBus {
    private data class Job(
        val label: String,
        val done: Int,
        val total: Int
    )

    private val lock = Any()
    private val jobs = linkedMapOf<String, Job>()

    fun started(key: String, label: String, total: Int) {
        synchronized(lock) {
            jobs[key] = Job(label, 0, total)
        }
    }

    fun progress(key: String, label: String, done: Int, total: Int) {
        synchronized(lock) {
            jobs[key] = Job(label, done, total)
        }
    }

    fun completed(key: String) {
        synchronized(lock) {
            jobs.remove(key)
        }
    }

    fun failed(key: String) {
        synchronized(lock) {
            jobs.remove(key)
        }
    }

    fun summary(): String {
        val snapshot = synchronized(lock) {
            jobs.values.toList()
        }
        if (snapshot.isEmpty()) {
            return ""
        }
        val tracked = snapshot.filter { it.total > 0 }
        if (tracked.isEmpty()) {
            return "Indexing..."
        }
        val done = tracked.sumOf { it.done.coerceIn(0, it.total) }
        val total = tracked.sumOf { it.total }
        return "Indexing $done/$total"
    }
}

class AsyncAabbIndexRebuilder<T>(
    private val label: String,
    private val cellSize: Float,
    private val keyOf: (T) -> String,
    private val debounceMs: Long = 250L
) {
    private val statusKey = "${label.lowercase().replace(' ', '-')}-${System.identityHashCode(this)}"
    @Volatile private var requestedGeneration = 0L
    @Volatile private var appliedGeneration = 0L
    @Volatile private var inFlightGeneration = 0L
    @Volatile private var pendingResult: AsyncAabbIndexResult<T>? = null
    @Volatile private var buildFailed = false

    @Volatile
    private var lastDirtyAtMs: Long = 0L

    fun markDirty(nowMs: Long = System.currentTimeMillis()) {
        requestedGeneration += 1L
        lastDirtyAtMs = nowMs
    }

    fun markCurrent() {
        val generation = requestedGeneration + 1L
        requestedGeneration = generation
        appliedGeneration = generation
        inFlightGeneration = 0L
        pendingResult = null
        buildFailed = false
        IndexingStatusBus.completed(statusKey)
    }

    fun isCurrent(): Boolean = !buildFailed && appliedGeneration == requestedGeneration

    fun process(
        nowMs: Long = System.currentTimeMillis(),
        snapshotProvider: () -> List<IndexedAabbSnapshot<T>>,
        apply: (AsyncAabbIndexResult<T>) -> Unit
    ) {
        val ready = pendingResult
        if (ready != null) {
            pendingResult = null
            if (inFlightGeneration == ready.generation) {
                inFlightGeneration = 0L
            }
            if (ready.generation == requestedGeneration) {
                apply(ready)
                appliedGeneration = ready.generation
            }
            IndexingStatusBus.completed(statusKey)
        }

        val requested = requestedGeneration
        if (requested == appliedGeneration) {
            return
        }
        if (inFlightGeneration != 0L) {
            return
        }
        if (nowMs - lastDirtyAtMs < debounceMs) {
            return
        }

        val snapshot = snapshotProvider()
        inFlightGeneration = requested
        buildFailed = false
        IndexingStatusBus.started(statusKey, label, snapshot.size)

        if (BuildFlags.WEB_BUILD) {
            val result = buildIndexResult(requested, snapshot)
            if (result != null && requestedGeneration == requested) {
                apply(result)
                appliedGeneration = result.generation
            }
            inFlightGeneration = 0L
            IndexingStatusBus.completed(statusKey)
            return
        }

        Thread({
            try {
                pendingResult = buildIndexResult(requested, snapshot)
            } catch (_: Throwable) {
                buildFailed = true
                IndexingStatusBus.failed(statusKey)
                if (inFlightGeneration == requested) {
                    inFlightGeneration = 0L
                }
            }
        }, "k3d-indexer").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    private fun buildIndexResult(
        requested: Long,
        snapshot: List<IndexedAabbSnapshot<T>>
    ): AsyncAabbIndexResult<T>? {
        val index = SpatialHash3D<T>(cellSize, keyOf)
        val boundsByKey = linkedMapOf<String, BoundingBox>()
        val boundsMin = Vector3()
        val boundsMax = Vector3()
        var hasBounds = false
        val total = snapshot.size.coerceAtLeast(1)

        snapshot.forEachIndexed { idx, entry ->
            if (requestedGeneration != requested) {
                IndexingStatusBus.completed(statusKey)
                return null
            }
            index.insertAabb(entry.min, entry.max, entry.item)
            boundsByKey[keyOf(entry.item)] = BoundingBox(entry.min.cpy(), entry.max.cpy())
            if (!hasBounds) {
                boundsMin.set(entry.min)
                boundsMax.set(entry.max)
                hasBounds = true
            } else {
                boundsMin.x = kotlin.math.min(boundsMin.x, entry.min.x)
                boundsMin.y = kotlin.math.min(boundsMin.y, entry.min.y)
                boundsMin.z = kotlin.math.min(boundsMin.z, entry.min.z)
                boundsMax.x = kotlin.math.max(boundsMax.x, entry.max.x)
                boundsMax.y = kotlin.math.max(boundsMax.y, entry.max.y)
                boundsMax.z = kotlin.math.max(boundsMax.z, entry.max.z)
            }
            if ((idx and 255) == 0 || idx == snapshot.lastIndex) {
                IndexingStatusBus.progress(statusKey, label, idx + 1, total)
            }
        }

        return AsyncAabbIndexResult(
            generation = requested,
            index = index,
            boundsMin = boundsMin,
            boundsMax = boundsMax,
            hasBounds = hasBounds,
            boundsByKey = boundsByKey
        )
    }
}

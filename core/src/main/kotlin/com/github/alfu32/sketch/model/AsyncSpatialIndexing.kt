package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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

    private val jobs = ConcurrentHashMap<String, Job>()

    fun started(key: String, label: String, total: Int) {
        jobs[key] = Job(label, 0, total)
    }

    fun progress(key: String, label: String, done: Int, total: Int) {
        jobs[key] = Job(label, done, total)
    }

    fun completed(key: String) {
        jobs.remove(key)
    }

    fun failed(key: String) {
        jobs.remove(key)
    }

    fun summary(): String {
        if (jobs.isEmpty()) {
            return ""
        }
        return jobs.values
            .sortedBy { it.label }
            .joinToString(" | ") { job ->
                if (job.total <= 0) {
                    "${job.label}..."
                } else {
                    val pct = ((job.done.coerceIn(0, job.total) * 100f) / job.total.toFloat()).toInt()
                    "${job.label} ${pct}%"
                }
            }
    }
}

internal object AsyncIndexExecutor {
    val executor: ExecutorService = Executors.newSingleThreadExecutor(ThreadFactory { runnable ->
        Thread(runnable, "k3d-indexer").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    })
}

class AsyncAabbIndexRebuilder<T>(
    private val label: String,
    private val cellSize: Float,
    private val keyOf: (T) -> String,
    private val debounceMs: Long = 250L
) {
    private val statusKey = "${label.lowercase().replace(' ', '-')}-${System.identityHashCode(this)}"
    private val requestedGeneration = AtomicLong(0L)
    private val appliedGeneration = AtomicLong(0L)
    private val inFlightGeneration = AtomicLong(0L)
    private val pendingResult = AtomicReference<AsyncAabbIndexResult<T>?>(null)
    private val buildFailed = AtomicBoolean(false)

    @Volatile
    private var lastDirtyAtMs: Long = 0L

    fun markDirty(nowMs: Long = System.currentTimeMillis()) {
        requestedGeneration.incrementAndGet()
        lastDirtyAtMs = nowMs
    }

    fun markCurrent() {
        val generation = requestedGeneration.incrementAndGet()
        appliedGeneration.set(generation)
        pendingResult.set(null)
        buildFailed.set(false)
        IndexingStatusBus.completed(statusKey)
    }

    fun isCurrent(): Boolean = !buildFailed.get() && appliedGeneration.get() == requestedGeneration.get()

    fun process(
        nowMs: Long = System.currentTimeMillis(),
        snapshotProvider: () -> List<IndexedAabbSnapshot<T>>,
        apply: (AsyncAabbIndexResult<T>) -> Unit
    ) {
        pendingResult.getAndSet(null)?.let { result ->
            inFlightGeneration.compareAndSet(result.generation, 0L)
            if (result.generation == requestedGeneration.get()) {
                apply(result)
                appliedGeneration.set(result.generation)
            }
            IndexingStatusBus.completed(statusKey)
        }

        val requested = requestedGeneration.get()
        if (requested == appliedGeneration.get()) {
            return
        }
        if (inFlightGeneration.get() != 0L) {
            return
        }
        if (nowMs - lastDirtyAtMs < debounceMs) {
            return
        }

        val snapshot = snapshotProvider()
        if (!inFlightGeneration.compareAndSet(0L, requested)) {
            return
        }

        buildFailed.set(false)
        IndexingStatusBus.started(statusKey, label, snapshot.size)

        AsyncIndexExecutor.executor.submit {
            try {
                val index = SpatialHash3D<T>(cellSize, keyOf)
                val boundsByKey = linkedMapOf<String, BoundingBox>()
                val boundsMin = Vector3()
                val boundsMax = Vector3()
                var hasBounds = false
                val total = snapshot.size.coerceAtLeast(1)

                snapshot.forEachIndexed { idx, entry ->
                    if (requestedGeneration.get() != requested) {
                        IndexingStatusBus.completed(statusKey)
                        inFlightGeneration.compareAndSet(requested, 0L)
                        return@submit
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

                pendingResult.set(
                    AsyncAabbIndexResult(
                        generation = requested,
                        index = index,
                        boundsMin = boundsMin,
                        boundsMax = boundsMax,
                        hasBounds = hasBounds,
                        boundsByKey = boundsByKey
                    )
                )
            } catch (_: Throwable) {
                buildFailed.set(true)
                IndexingStatusBus.failed(statusKey)
                inFlightGeneration.compareAndSet(requested, 0L)
            }
        }
    }
}

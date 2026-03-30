package com.github.alfu32.sketch.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.github.alfu32.sketch.BuildFlags
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque

data class RenderStatus(
    val running: Boolean,
    val mode: RenderMode?,
    val completedTiles: Int,
    val totalTiles: Int,
    val currentPassLabel: String,
    val resolutionLabel: String
)

class RenderController {
    private data class Job(
        val snapshot: SceneSnapshot,
        val mode: RenderMode,
        val buffer: RenderBuffer,
        val scheduler: ProgressiveTileScheduler,
        val renderer: CpuRenderer,
        val pendingTileUpdates: ArrayDeque<RenderTile> = ArrayDeque(),
        val workerThreads: MutableList<Thread> = mutableListOf(),
        @Volatile var cancelled: Boolean = false,
        @Volatile var activeWorkers: Int = 0
    )

    private val stateLock = Any()
    private var currentJob: Job? = null
    @Volatile private var running = false
    @Volatile private var mode: RenderMode? = null
    @Volatile private var completedTiles = 0
    @Volatile private var totalTiles = 0
    @Volatile private var currentPassLabel = ""
    @Volatile private var glassTransmission = 1f

    fun start(
        snapshot: SceneSnapshot,
        mode: RenderMode,
        workerCount: Int,
        glassTransmission: Float,
        pruningEnabled: Boolean
    ) {
        stop()
        val buffer = RenderBuffer(snapshot.camera.width, snapshot.camera.height).apply {
            clear(Color.CLEAR)
        }
        val scheduler = ProgressiveTileScheduler(
            snapshot.camera.width,
            snapshot.camera.height,
            pruningEnabled = pruningEnabled
        )
        val job = Job(
            snapshot = snapshot,
            mode = mode,
            buffer = buffer,
            scheduler = scheduler,
            renderer = when (mode) {
                RenderMode.RAYTRACE -> CpuRayTracer()
                RenderMode.PATHTRACE -> CpuPathTracer()
            }
        )
        currentJob = job
        this.mode = mode
        this.glassTransmission = glassTransmission
        completedTiles = 0
        totalTiles = scheduler.totalTiles
        currentPassLabel = if (totalTiles > 0) "pass ${scheduler.currentTileSize}" else ""
        running = totalTiles > 0

        val parallelWorkers = if (BuildFlags.WEB_BUILD) 1 else workerCount.coerceAtLeast(1)
        if (running && parallelWorkers > 1) {
            job.activeWorkers = parallelWorkers
            repeat(parallelWorkers) { index ->
                val thread = Thread({ workerLoop(job) }, "k3d-render-$index").apply { isDaemon = true }
                job.workerThreads += thread
                thread.start()
            }
        }
    }

    fun step(maxTiles: Int, glassTransmission: Float): Boolean {
        this.glassTransmission = glassTransmission
        val job = currentJob ?: return false
        if (!running && job.workerThreads.isEmpty()) {
            return false
        }
        if (job.workerThreads.isNotEmpty()) {
            if (running && job.activeWorkers <= 0) {
                running = false
            }
            synchronized(job.pendingTileUpdates) {
                return job.pendingTileUpdates.isNotEmpty()
            }
        }
        var changed = false
        var remaining = maxTiles.coerceAtLeast(1)
        while (remaining > 0) {
            val tile = job.scheduler.nextTile() ?: break
            syncProgress(job)
            currentPassLabel = "pass ${tile.pixelStep}"
            val hadHit = job.renderer.renderTile(
                snapshot = job.snapshot,
                tile = tile,
                buffer = job.buffer,
                seed = (tile.passIndex.toLong() shl 32) xor (tile.x.toLong() shl 16) xor tile.y.toLong(),
                glassTransmission = this.glassTransmission
            )
            if (!hadHit) {
                job.scheduler.markEmpty(tile)
            }
            synchronized(job.pendingTileUpdates) {
                job.pendingTileUpdates.addLast(tile)
            }
            changed = true
            remaining -= 1
        }
        syncProgress(job)
        if (completedTiles >= totalTiles) {
            running = false
        }
        return changed
    }

    fun stop() {
        running = false
        currentJob?.cancelled = true
    }

    fun isRunning(): Boolean = running

    fun hasImage(): Boolean = currentJob != null

    fun status(): RenderStatus {
        val job = currentJob
        return RenderStatus(
            running = running,
            mode = mode,
            completedTiles = completedTiles,
            totalTiles = totalTiles,
            currentPassLabel = currentPassLabel,
            resolutionLabel = job?.let { "${it.buffer.width}x${it.buffer.height}" } ?: "-"
        )
    }

    fun flushPreviewUpdates(pixmap: Pixmap): Boolean {
        val job = currentJob ?: return false
        var changed = false
        synchronized(job.pendingTileUpdates) {
            while (job.pendingTileUpdates.isNotEmpty()) {
                val tile = job.pendingTileUpdates.removeFirst()
                job.buffer.writeTileToPixmap(pixmap, tile)
                changed = true
            }
        }
        return changed
    }

    fun resolvePreview(pixmap: Pixmap): Boolean {
        val job = currentJob ?: return false
        job.buffer.toPixmap(pixmap)
        return true
    }

    fun savePng(file: File) {
        val job = currentJob ?: error("No render image available.")
        val pixmap = Pixmap(job.buffer.width, job.buffer.height, Pixmap.Format.RGBA8888)
        val writer = PixmapIO.PNG((job.buffer.width * job.buffer.height * 4).coerceAtLeast(1024))
        try {
            job.buffer.toFlippedPixmap(pixmap)
            FileOutputStream(file).use { out ->
                writer.write(out, pixmap)
            }
        } finally {
            writer.dispose()
            pixmap.dispose()
        }
    }

    private fun workerLoop(job: Job) {
        try {
            while (!job.cancelled) {
                val tile = job.scheduler.nextTile() ?: break
                syncProgress(job)
                currentPassLabel = "pass ${tile.pixelStep}"
                val hadHit = job.renderer.renderTile(
                    snapshot = job.snapshot,
                    tile = tile,
                    buffer = job.buffer,
                    seed = (tile.passIndex.toLong() shl 32) xor (tile.x.toLong() shl 16) xor tile.y.toLong(),
                    glassTransmission = glassTransmission
                )
                if (!hadHit) {
                    job.scheduler.markEmpty(tile)
                }
                synchronized(job.pendingTileUpdates) {
                    job.pendingTileUpdates.addLast(tile)
                }
                syncProgress(job)
            }
        } finally {
            synchronized(stateLock) {
                syncProgressLocked(job)
                job.activeWorkers = (job.activeWorkers - 1).coerceAtLeast(0)
                if (currentJob === job && job.activeWorkers <= 0) {
                    running = false
                }
            }
        }
    }

    private fun syncProgress(job: Job) {
        synchronized(stateLock) {
            syncProgressLocked(job)
        }
    }

    private fun syncProgressLocked(job: Job) {
        if (currentJob === job) {
            completedTiles = job.scheduler.completedTiles
            totalTiles = job.scheduler.totalTiles
        }
    }
}

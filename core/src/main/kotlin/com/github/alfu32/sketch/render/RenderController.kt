package com.github.alfu32.sketch.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import kotlin.concurrent.thread

data class RenderStatus(
    val running: Boolean,
    val mode: RenderMode?,
    val completedTiles: Int,
    val totalTiles: Int,
    val currentPassLabel: String,
    val resolutionLabel: String
)

class RenderController {
    private data class TileUpdate(
        val generation: Int,
        val tile: RenderTile
    )

    private data class Job(
        val snapshot: SceneSnapshot,
        val mode: RenderMode,
        val buffer: RenderBuffer,
        val scheduler: ProgressiveTileScheduler,
        val generation: Int
    )

    private val pendingTileUpdates = ArrayDeque<TileUpdate>()
    private var worker: Thread? = null
    @Volatile private var stopRequested = false
    @Volatile private var running = false
    @Volatile private var mode: RenderMode? = null
    @Volatile private var completedTiles = 0
    @Volatile private var totalTiles = 0
    @Volatile private var currentPassLabel = ""
    @Volatile private var activeGeneration = 0
    private var currentJob: Job? = null

    fun start(snapshot: SceneSnapshot, mode: RenderMode) {
        stop()
        val buffer = RenderBuffer(snapshot.camera.width, snapshot.camera.height).apply {
            clear(Color.CLEAR)
        }
        val scheduler = ProgressiveTileScheduler(snapshot.camera.width, snapshot.camera.height)
        val generation = activeGeneration + 1
        activeGeneration = generation
        synchronized(pendingTileUpdates) {
            pendingTileUpdates.clear()
        }
        val job = Job(snapshot, mode, buffer, scheduler, generation)
        currentJob = job
        this.mode = mode
        completedTiles = 0
        totalTiles = scheduler.totalTiles
        currentPassLabel = if (totalTiles > 0) "pass 64" else ""
        stopRequested = false
        running = true
        worker = thread(
            start = true,
            isDaemon = true,
            name = "k3d-render-worker"
        ) {
            val renderer: CpuRenderer = when (mode) {
                RenderMode.RAYTRACE -> CpuRayTracer()
                RenderMode.PATHTRACE -> CpuPathTracer()
            }
            try {
                while (!stopRequested && generation == activeGeneration) {
                    val tile = scheduler.nextTile() ?: break
                    currentPassLabel = "pass ${tile.pixelStep}"
                    renderer.renderTile(job.snapshot, tile, job.buffer, seed = (tile.passIndex.toLong() shl 32) xor (tile.x.toLong() shl 16) xor tile.y.toLong())
                    synchronized(pendingTileUpdates) {
                        pendingTileUpdates.addLast(TileUpdate(generation, tile))
                    }
                    completedTiles += 1
                }
            } finally {
                if (generation == activeGeneration) {
                    running = false
                }
            }
        }
    }

    fun stop() {
        stopRequested = true
        activeGeneration += 1
        worker?.join(100)
        worker = null
        running = false
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
        synchronized(pendingTileUpdates) {
            while (pendingTileUpdates.isNotEmpty()) {
                val update = pendingTileUpdates.removeFirst()
                if (update.generation != job.generation) {
                    continue
                }
                job.buffer.writeTileToPixmap(pixmap, update.tile)
                changed = true
            }
        }
        return changed
    }

    fun currentBufferWidth(): Int = currentJob?.buffer?.width ?: 0
    fun currentBufferHeight(): Int = currentJob?.buffer?.height ?: 0

    fun savePng(file: File) {
        val job = currentJob ?: error("No render image available.")
        val pixmap = Pixmap(job.buffer.width, job.buffer.height, Pixmap.Format.RGBA8888)
        val writer = PixmapIO.PNG((job.buffer.width * job.buffer.height * 4).coerceAtLeast(1024))
        try {
            job.buffer.toPixmap(pixmap)
            FileOutputStream(file).use { out ->
                writer.write(out, pixmap)
            }
        } finally {
            writer.dispose()
            pixmap.dispose()
        }
    }
}

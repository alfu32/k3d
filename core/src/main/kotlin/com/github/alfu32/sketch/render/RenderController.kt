package com.github.alfu32.sketch.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
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
        val renderer: CpuRenderer
    )

    private val pendingTileUpdates = ArrayDeque<RenderTile>()
    private var currentJob: Job? = null
    private var running = false
    private var mode: RenderMode? = null
    private var completedTiles = 0
    private var totalTiles = 0
    private var currentPassLabel = ""

    fun start(snapshot: SceneSnapshot, mode: RenderMode) {
        pendingTileUpdates.clear()
        val buffer = RenderBuffer(snapshot.camera.width, snapshot.camera.height).apply {
            clear(Color.CLEAR)
        }
        val scheduler = ProgressiveTileScheduler(snapshot.camera.width, snapshot.camera.height)
        currentJob = Job(
            snapshot = snapshot,
            mode = mode,
            buffer = buffer,
            scheduler = scheduler,
            renderer = when (mode) {
                RenderMode.RAYTRACE -> CpuRayTracer()
                RenderMode.PATHTRACE -> CpuPathTracer()
            }
        )
        this.mode = mode
        completedTiles = 0
        totalTiles = scheduler.totalTiles
        currentPassLabel = if (totalTiles > 0) "pass 64" else ""
        running = totalTiles > 0
    }

    fun step(maxTiles: Int): Boolean {
        val job = currentJob ?: return false
        if (!running) {
            return false
        }
        var changed = false
        var remaining = maxTiles.coerceAtLeast(1)
        while (remaining > 0) {
            val tile = job.scheduler.nextTile() ?: break
            currentPassLabel = "pass ${tile.pixelStep}"
            job.renderer.renderTile(
                snapshot = job.snapshot,
                tile = tile,
                buffer = job.buffer,
                seed = (tile.passIndex.toLong() shl 32) xor (tile.x.toLong() shl 16) xor tile.y.toLong()
            )
            pendingTileUpdates.addLast(tile)
            completedTiles += 1
            changed = true
            remaining -= 1
        }
        if (completedTiles >= totalTiles) {
            running = false
        }
        return changed
    }

    fun stop() {
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
        while (pendingTileUpdates.isNotEmpty()) {
            val tile = pendingTileUpdates.removeFirst()
            job.buffer.writeTileToPixmap(pixmap, tile)
            changed = true
        }
        return changed
    }

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

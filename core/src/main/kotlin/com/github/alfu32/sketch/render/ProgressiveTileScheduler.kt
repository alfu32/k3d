package com.github.alfu32.sketch.render

import kotlin.math.sqrt

data class RenderTile(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val pixelStep: Int,
    val passIndex: Int,
    val passCount: Int
)

class ProgressiveTileScheduler(
    width: Int,
    height: Int,
    tileSizes: IntArray = intArrayOf(64, 32, 16, 8, 4, 2, 1)
) {
    private val tiles: List<RenderTile>
    private var cursor = 0

    val totalTiles: Int
        get() = tiles.size

    val completedTiles: Int
        get() = cursor.coerceAtMost(tiles.size)

    val currentTileSize: Int
        get() = tiles.getOrNull(cursor)?.pixelStep ?: 1

    init {
        val passCount = tileSizes.size
        val imageCenterX = width * 0.5f
        val imageCenterY = height * 0.5f
        val built = mutableListOf<RenderTile>()
        tileSizes.forEachIndexed { passIndex, step ->
            val passTiles = mutableListOf<RenderTile>()
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    passTiles += RenderTile(
                        x = x,
                        y = y,
                        width = minOf(step, width - x),
                        height = minOf(step, height - y),
                        pixelStep = step,
                        passIndex = passIndex,
                        passCount = passCount
                    )
                    x += step
                }
                y += step
            }
            built += passTiles.sortedWith(
                compareBy<RenderTile> {
                    val cx = it.x + it.width * 0.5f
                    val cy = it.y + it.height * 0.5f
                    val dx = cx - imageCenterX
                    val dy = cy - imageCenterY
                    sqrt(dx * dx + dy * dy)
                }.thenBy { it.y }.thenBy { it.x }
            )
        }
        tiles = built
    }

    fun nextTile(): RenderTile? {
        if (cursor >= tiles.size) {
            return null
        }
        return tiles[cursor++]
    }
}

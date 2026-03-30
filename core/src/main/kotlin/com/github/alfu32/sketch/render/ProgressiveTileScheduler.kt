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
    tileSizes: IntArray = intArrayOf(32, 16, 8, 2, 1),
    private val pruningEnabled: Boolean = false
) {
    private val passSteps = tileSizes.copyOf()
    private val passGridWidths = IntArray(tileSizes.size)
    private val passEmptyTiles = Array(tileSizes.size) { BooleanArray(0) }
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
            val gridWidth = (width + step - 1) / step
            val gridHeight = (height + step - 1) / step
            passGridWidths[passIndex] = gridWidth
            passEmptyTiles[passIndex] = BooleanArray((gridWidth * gridHeight).coerceAtLeast(1))
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

    @Synchronized
    fun nextTile(): RenderTile? {
        while (cursor < tiles.size) {
            val candidate = tiles[cursor++]
            if (isPruned(candidate)) {
                continue
            }
            return candidate
        }
        return null
    }

    @Synchronized
    fun markEmpty(tile: RenderTile) {
        if (!pruningEnabled) {
            return
        }
        if (tile.passIndex >= passSteps.lastIndex) {
            return
        }
        val step = passSteps[tile.passIndex]
        val gridWidth = passGridWidths[tile.passIndex]
        val gridX = tile.x / step
        val gridY = tile.y / step
        val index = gridY * gridWidth + gridX
        if (index in passEmptyTiles[tile.passIndex].indices) {
            passEmptyTiles[tile.passIndex][index] = true
        }
    }

    private fun isPruned(tile: RenderTile): Boolean {
        if (!pruningEnabled) {
            return false
        }
        if (tile.passIndex <= 0) {
            return false
        }
        for (ancestorPass in 0 until tile.passIndex) {
            val ancestorStep = passSteps[ancestorPass]
            val gridWidth = passGridWidths[ancestorPass]
            val gridX = tile.x / ancestorStep
            val gridY = tile.y / ancestorStep
            val index = gridY * gridWidth + gridX
            if (
                index in passEmptyTiles[ancestorPass].indices &&
                passEmptyTiles[ancestorPass][index] &&
                isInsidePrunedInterior(tile, ancestorStep, gridX, gridY)
            ) {
                return true
            }
        }
        return false
    }

    private fun isInsidePrunedInterior(tile: RenderTile, ancestorStep: Int, ancestorGridX: Int, ancestorGridY: Int): Boolean {
        val ancestorX = ancestorGridX * ancestorStep
        val ancestorY = ancestorGridY * ancestorStep
        val margin = minOf(tile.pixelStep, ancestorStep / 4).coerceAtLeast(1)
        val innerMinX = ancestorX + margin
        val innerMinY = ancestorY + margin
        val innerMaxX = ancestorX + ancestorStep - margin
        val innerMaxY = ancestorY + ancestorStep - margin
        return tile.x >= innerMinX &&
            tile.y >= innerMinY &&
            tile.x + tile.width <= innerMaxX &&
            tile.y + tile.height <= innerMaxY
    }
}

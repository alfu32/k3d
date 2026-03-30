package com.github.alfu32.sketch.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import kotlin.math.roundToInt

class RenderBuffer(
    val width: Int,
    val height: Int
) {
    private val pixels = IntArray(width * height)

    fun clear(color: Color = Color.CLEAR) {
        val packed = Color.rgba8888(color)
        pixels.fill(packed)
    }

    fun setBlock(x: Int, y: Int, w: Int, h: Int, color: Color) {
        val packed = Color.rgba8888(color)
        val clampedX0 = x.coerceIn(0, width)
        val clampedY0 = y.coerceIn(0, height)
        val clampedX1 = (x + w).coerceIn(0, width)
        val clampedY1 = (y + h).coerceIn(0, height)
        for (yy in clampedY0 until clampedY1) {
            val row = yy * width
            for (xx in clampedX0 until clampedX1) {
                pixels[row + xx] = packed
            }
        }
    }

    fun toPixmap(pixmap: Pixmap) {
        require(pixmap.width == width && pixmap.height == height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                pixmap.drawPixel(x, y, pixels[row + x])
            }
        }
    }

    fun toFlippedPixmap(pixmap: Pixmap) {
        require(pixmap.width == width && pixmap.height == height)
        for (y in 0 until height) {
            val srcRow = y * width
            val dstY = height - 1 - y
            for (x in 0 until width) {
                pixmap.drawPixel(x, dstY, pixels[srcRow + x])
            }
        }
    }

    fun writeTileToPixmap(pixmap: Pixmap, tile: RenderTile) {
        val clampedX1 = (tile.x + tile.width).coerceIn(0, width)
        val clampedY1 = (tile.y + tile.height).coerceIn(0, height)
        for (y in tile.y.coerceAtLeast(0) until clampedY1) {
            val row = y * width
            for (x in tile.x.coerceAtLeast(0) until clampedX1) {
                pixmap.drawPixel(x, y, pixels[row + x])
            }
        }
    }
}

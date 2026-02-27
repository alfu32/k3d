package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.DimensionMath
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class LinearDimensionTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.LINEAR_DIMENSION
    override val message: String = "Click first measure point."

    private var startWorld: Vector3? = null
    private var endWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clearTransient()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clearTransient()
        status.message = "Canceled."
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hover.set(world)
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        val group = scene.activeGroup()
        when {
            startWorld == null -> {
                startWorld = Vector3(world)
                status.message = "Click second measure point."
            }
            endWorld == null -> {
                endWorld = Vector3(world)
                status.message = "Click offset point."
            }
            else -> {
                val start = startWorld ?: return false
                val end = endWorld ?: return false
                val offset = Vector3(world)
                group.dimensionStore.addDimension(
                    group.toLocal(start),
                    group.toLocal(end),
                    group.toLocal(offset)
                )
                clearTransient()
                status.message = "Dimension added. Click first measure point."
            }
        }
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val start = startWorld ?: return
        if (!hasHover) {
            return
        }
        renderer.color = ToolFeedbackColors.DARK
        val end = endWorld
        if (end == null) {
            renderer.line(start, hover)
            return
        }
        renderer.line(start, end)
        val (lineStart, lineEnd) = DimensionMath.computeOffsetLine(start, end, hover)
        renderer.line(lineStart, lineEnd)
        renderer.line(start, lineStart)
        renderer.line(end, lineEnd)
    }

    private fun clearTransient() {
        startWorld = null
        endWorld = null
        hasHover = false
    }
}

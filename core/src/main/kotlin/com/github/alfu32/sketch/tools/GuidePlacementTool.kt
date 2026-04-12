package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class GuidePlacementTool(
    override val id: ToolId,
    override val message: String,
    private val guideLabel: String,
    private val placeGuide: (Vector3, Vector3?) -> Unit,
    private val onSelectTool: () -> Unit
) : Tool {
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        hasHover = false
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        hasHover = false
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        hasHover = false
        status.message = "$guideLabel placement canceled."
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
        placeGuide(Vector3(world), normal?.let { Vector3(it).nor() })
        hasHover = false
        status.message = "$guideLabel placed."
        onSelectTool()
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        if (keycode != Input.Keys.ESCAPE) {
            return false
        }
        onCancel(status)
        onSelectTool()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        if (!hasHover) {
            return
        }
        val size = 0.35f
        renderer.color = ToolFeedbackColors.TERTIARY
        renderer.line(hover.x - size, hover.y, hover.z, hover.x + size, hover.y, hover.z)
        renderer.line(hover.x, hover.y - size, hover.z, hover.x, hover.y + size, hover.z)
        renderer.line(hover.x, hover.y, hover.z - size, hover.x, hover.y, hover.z + size)
    }
}

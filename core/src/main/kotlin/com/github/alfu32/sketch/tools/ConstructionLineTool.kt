package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class ConstructionLineTool(
    private val scene: GroupScene,
    private val onSelectTool: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.CONSTRUCTION_LINE
    override val message: String = "Click to start a construction line."

    private var anchorWorld: Vector3? = null
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
        if (anchorWorld == null) {
            anchorWorld = Vector3(world)
            status.message = "Click to finish segment. Enter finishes. Esc selects."
        } else {
            val startWorld = anchorWorld ?: return false
            val endWorld = Vector3(world)
            val startLocal = group.toLocal(startWorld)
            val endLocal = group.toLocal(endWorld)
            group.addSketchSegment(startLocal, endLocal)
            anchorWorld = null
            status.message = message
        }
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                if (anchorWorld != null) {
                    anchorWorld = null
                    status.message = message
                    true
                } else {
                    false
                }
            }
            Input.Keys.ESCAPE -> {
                clearTransient()
                status.message = "Canceled."
                onSelectTool()
                true
            }
            else -> false
        }
    }

    override fun anchorWorld(): Vector3? {
        return anchorWorld
    }

    override fun render(renderer: ShapeRenderer) {
        val start = anchorWorld
        if (start != null && hasHover) {
            renderer.color = Color(0.65f, 0.9f, 0.65f, 1f)
            renderer.line(start.x, start.y, start.z, hover.x, hover.y, hover.z)
        }
    }

    private fun clearTransient() {
        anchorWorld = null
        hasHover = false
    }
}

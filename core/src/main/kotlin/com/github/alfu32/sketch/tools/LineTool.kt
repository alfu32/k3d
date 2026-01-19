package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class LineTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.LINE
    override val message: String = "Click to start a line."

    private var anchorWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false
    private val polylinePointsLocal = mutableListOf<Vector3>()
    private var preferredNormalLocal: Vector3? = null
    private val epsilonSq = 1e-4f

    override fun onEnter(status: StatusModel) {
        status.message = "Click to start a line."
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
            preferredNormalLocal = normal?.let { group.vectorToLocal(it) }
            polylinePointsLocal.clear()
            polylinePointsLocal.add(group.toLocal(world))
            status.message = "Click to finish segment. Esc cancels."
        } else {
            val startWorld = anchorWorld ?: return false
            val endWorld = Vector3(world)
            val startLocal = group.toLocal(startWorld)
            val endLocal = group.toLocal(endWorld)
            group.lineStore.addSegment(startLocal, endLocal)
            anchorWorld = Vector3(endWorld)
            polylinePointsLocal.add(Vector3(endLocal))
            if (polylinePointsLocal.size >= 3 && polylinePointsLocal.first().dst2(endLocal) <= epsilonSq) {
                val facing = normal?.let { group.vectorToLocal(it) } ?: preferredNormalLocal
                group.faceStore.addPolygon(polylinePointsLocal, facing)
                polylinePointsLocal.clear()
                polylinePointsLocal.add(Vector3(endLocal))
                status.message = "Face created. Click to continue line. Esc cancels."
            } else {
                status.message = "Click to continue line. Esc cancels."
            }
        }
        if (normal != null) {
            preferredNormalLocal = scene.activeGroup().vectorToLocal(normal)
        }
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val start = anchorWorld
        if (start != null && hasHover) {
            renderer.color = Color(0.95f, 0.75f, 0.25f, 1f)
            renderer.line(start.x, start.y, start.z, hover.x, hover.y, hover.z)
        }
    }

    private fun clearTransient() {
        anchorWorld = null
        hasHover = false
        polylinePointsLocal.clear()
        preferredNormalLocal = null
    }
}

package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class LineTool(
    private val lineStore: DraftLineStore,
    private val faceStore: DraftFaceStore
) : Tool {
    override val id: ToolId = ToolId.LINE
    override val message: String = "Click to start a line."

    private var anchor: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false
    private val polylinePoints = mutableListOf<Vector3>()
    private var planeNormal: Vector3? = null
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
        if (anchor == null) {
            anchor = Vector3(world)
            planeNormal = normal?.cpy()
            polylinePoints.clear()
            polylinePoints.add(Vector3(world))
            status.message = "Click to finish segment. Esc cancels."
        } else {
            val start = anchor ?: return false
            val end = Vector3(world)
            lineStore.addSegment(start, end)
            anchor = Vector3(end)
            polylinePoints.add(Vector3(end))
            if (polylinePoints.size >= 3 && polylinePoints.first().dst2(end) <= epsilonSq) {
                faceStore.addPolygon(polylinePoints, planeNormal)
                polylinePoints.clear()
                polylinePoints.add(Vector3(end))
                status.message = "Face created. Click to continue line. Esc cancels."
            } else {
                status.message = "Click to continue line. Esc cancels."
            }
        }
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val start = anchor
        if (start != null && hasHover) {
            renderer.color = Color(0.95f, 0.75f, 0.25f, 1f)
            renderer.line(start.x, start.y, start.z, hover.x, hover.y, hover.z)
        }
    }

    private fun clearTransient() {
        anchor = null
        hasHover = false
        polylinePoints.clear()
        planeNormal = null
    }
}

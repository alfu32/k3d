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

class SurfaceRectangleTool(
    private val lineStore: DraftLineStore,
    private val faceStore: DraftFaceStore
) : Tool {
    override val id: ToolId = ToolId.SURFACE_RECTANGLE
    override val message: String = "Click to start surface rectangle."

    private var anchor: Vector3? = null
    private var normal: Vector3? = null
    private var axisU: Vector3? = null
    private var axisV: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Click to start surface rectangle."
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
            val n = normal?.cpy()?.nor() ?: Vector3(0f, 1f, 0f)
            this.normal = n
            val basis = surfaceBasis(n)
            axisU = basis.first
            axisV = basis.second
            status.message = "Click to finish surface rectangle."
            return true
        }
        val start = anchor ?: return false
        val u = axisU ?: return false
        val v = axisV ?: return false
        val corners = rectangleCorners(start, world, u, v)
        for (i in 0 until 4) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            lineStore.addSegment(a, b)
        }
        addRectangleFace(corners, this.normal ?: Vector3(0f, 1f, 0f))
        clearTransient()
        status.message = "Click to start surface rectangle."
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val start = anchor
        val u = axisU
        val v = axisV
        if (start != null && u != null && v != null && hasHover) {
            val corners = rectangleCorners(start, hover, u, v)
            renderer.color = Color(0.35f, 0.85f, 0.65f, 1f)
            for (i in 0 until 4) {
                val a = corners[i]
                val b = corners[(i + 1) % 4]
                renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            }
        }
    }

    private fun rectangleCorners(start: Vector3, end: Vector3, axisU: Vector3, axisV: Vector3): List<Vector3> {
        val delta = Vector3(end).sub(start)
        val u = delta.dot(axisU)
        val v = delta.dot(axisV)
        val p0 = Vector3(start)
        val p1 = Vector3(start).mulAdd(axisU, u)
        val p2 = Vector3(p1).mulAdd(axisV, v)
        val p3 = Vector3(start).mulAdd(axisV, v)
        return listOf(p0, p1, p2, p3)
    }

    private fun addRectangleFace(corners: List<Vector3>, preferredNormal: Vector3) {
        val normal = Vector3(corners[1]).sub(corners[0]).crs(Vector3(corners[2]).sub(corners[0]))
        if (normal.dot(preferredNormal) >= 0f) {
            faceStore.addTriangle(corners[0], corners[1], corners[2])
            faceStore.addTriangle(corners[0], corners[2], corners[3])
        } else {
            faceStore.addTriangle(corners[0], corners[2], corners[1])
            faceStore.addTriangle(corners[0], corners[3], corners[2])
        }
    }

    private fun clearTransient() {
        anchor = null
        normal = null
        axisU = null
        axisV = null
        hasHover = false
    }

    private fun surfaceBasis(normal: Vector3): Pair<Vector3, Vector3> {
        val up = Vector3(0f, 1f, 0f)
        var axisU = Vector3(up).crs(normal)
        if (axisU.len2() <= 1e-6f) {
            axisU = Vector3(1f, 0f, 0f)
        }
        axisU.nor()
        val axisV = Vector3(normal).crs(axisU).nor()
        return axisU to axisV
    }
}

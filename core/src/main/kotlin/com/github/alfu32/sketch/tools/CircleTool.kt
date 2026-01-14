package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import kotlin.math.sqrt

class CircleTool(
    private val lineStore: DraftLineStore,
    private val faceStore: com.github.alfu32.sketch.model.DraftFaceStore
) : Tool {
    override val id: ToolId = ToolId.CIRCLE
    override val message: String = "Click to set center."

    private var center: Vector3? = null
    private var basis: PlaneBasis? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Click to set center."
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
        if (center == null) {
            center = Vector3(world)
            basis = planeBasisFromNormal(normal ?: Vector3(0f, 1f, 0f))
            status.message = "Click to set radius."
        } else {
            val c = center ?: return false
            val currentBasis = basis ?: planeBasisFromNormal(Vector3(0f, 1f, 0f))
            val radius = radiusOnPlane(c, world, currentBasis)
            commitCircle(c, currentBasis, radius)
            center = null
            basis = null
            status.message = "Click to set center."
        }
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val c = center
        val currentBasis = basis
        if (c != null && currentBasis != null && hasHover) {
            val radius = radiusOnPlane(c, hover, currentBasis)
            renderCircle(renderer, c, currentBasis, radius, Color(0.95f, 0.55f, 0.75f, 1f))
        }
    }

    private fun radiusOnPlane(center: Vector3, point: Vector3, basis: PlaneBasis): Float {
        val delta = Vector3(point).sub(center)
        val u = delta.dot(basis.axisU)
        val v = delta.dot(basis.axisV)
        return sqrt(u * u + v * v)
    }

    private fun commitCircle(center: Vector3, basis: PlaneBasis, radius: Float) {
        val segments = 24
        val points = circlePoints(center, basis, radius, segments)
        for (i in 0 until segments) {
            val a = points[i]
            val b = points[(i + 1) % segments]
            lineStore.addSegment(a, b)
            faceStore.addTriangle(center, b, a)
        }
    }

    private fun renderCircle(renderer: ShapeRenderer, center: Vector3, basis: PlaneBasis, radius: Float, color: Color) {
        if (radius <= 0f) {
            return
        }
        val segments = 24
        renderer.color = color
        val points = circlePoints(center, basis, radius, segments)
        for (i in 0 until segments) {
            val a = points[i]
            val b = points[(i + 1) % segments]
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
    }

    private fun circlePoints(center: Vector3, basis: PlaneBasis, radius: Float, segments: Int): List<Vector3> {
        val points = ArrayList<Vector3>(segments)
        for (i in 0 until segments) {
            val angle = MathUtils.PI2 * (i.toFloat() / segments)
            val u = MathUtils.cos(angle) * radius
            val v = MathUtils.sin(angle) * radius
            val point = Vector3(center)
                .mulAdd(basis.axisU, u)
                .mulAdd(basis.axisV, v)
            points.add(point)
        }
        return points
    }

    private fun clearTransient() {
        center = null
        basis = null
        hasHover = false
    }
}

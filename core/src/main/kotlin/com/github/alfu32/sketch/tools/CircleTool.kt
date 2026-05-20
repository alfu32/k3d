package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import com.github.alfu32.sketch.ui.ToolMeasurementLabel
import kotlin.math.sqrt

class CircleTool(
    private val scene: GroupScene,
    private val segmentsProvider: () -> Int
) : Tool {
    override val id: ToolId = ToolId.CIRCLE
    override val message: String = "Click to set center."

    private var centerWorld: Vector3? = null
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
        if (centerWorld == null) {
            centerWorld = Vector3(world)
            basis = planeBasisFromNormal(normal ?: Vector3(0f, 1f, 0f))
            status.message = "Click to set radius."
        } else {
            val c = centerWorld ?: return false
            val currentBasis = basis ?: planeBasisFromNormal(Vector3(0f, 1f, 0f))
            val radius = radiusOnPlane(c, world, currentBasis)
            commitCircle(c, currentBasis, radius)
            centerWorld = null
            basis = null
            status.message = "Click to set center."
        }
        return true
    }

    override fun anchorWorld(): Vector3? {
        return centerWorld
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val c = centerWorld ?: return null
        val currentBasis = basis ?: return null
        if (!hasHover) {
            return null
        }
        val radius = radiusOnPlane(c, hover, currentBasis)
        return ToolMeasurement(
            startWorld = Vector3(c),
            endWorld = Vector3(hover),
            extraLabels = listOf(
                ToolMeasurementLabel("Radius", radius),
                ToolMeasurementLabel("Diameter", 2f * radius),
                ToolMeasurementLabel("Perimeter", MathUtils.PI2 * radius),
                ToolMeasurementLabel("Area", MathUtils.PI * radius * radius, unitPower = 2)
            )
        )
    }

    override fun render(renderer: ShapeRenderer) {
        val c = centerWorld
        val currentBasis = basis
        if (c != null && currentBasis != null && hasHover) {
            val radius = radiusOnPlane(c, hover, currentBasis)
            renderCircle(renderer, c, currentBasis, radius, ToolFeedbackColors.SECONDARY)
        }
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val c = centerWorld ?: return emptyList()
        val currentBasis = basis ?: return emptyList()
        if (!hasHover) {
            return emptyList()
        }
        val radius = radiusOnPlane(c, hover, currentBasis)
        if (radius <= 0f) {
            return emptyList()
        }
        return pathFeedbackLines(circlePoints(c, currentBasis, radius, circleSegments()), close = true)
    }

    private fun radiusOnPlane(center: Vector3, point: Vector3, basis: PlaneBasis): Float {
        val delta = Vector3(point).sub(center)
        val u = delta.dot(basis.axisU)
        val v = delta.dot(basis.axisV)
        return sqrt(u * u + v * v)
    }

    private fun commitCircle(center: Vector3, basis: PlaneBasis, radius: Float) {
        val group = scene.activeGroup()
        val segments = circleSegments()
        val points = circlePoints(center, basis, radius, segments)
        val centerLocal = group.toLocal(center)
        for (i in 0 until segments) {
            val a = group.toLocal(points[i])
            val b = group.toLocal(points[(i + 1) % segments])
            group.addSketchSegment(a, b)
            group.faceStore.addTriangle(centerLocal, b, a)
        }
    }

    private fun renderCircle(renderer: ShapeRenderer, center: Vector3, basis: PlaneBasis, radius: Float, color: Color) {
        if (radius <= 0f) {
            return
        }
        val segments = circleSegments()
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
        centerWorld = null
        basis = null
        hasHover = false
    }

    private fun circleSegments(): Int {
        return segmentsProvider().coerceIn(3, 256)
    }
}

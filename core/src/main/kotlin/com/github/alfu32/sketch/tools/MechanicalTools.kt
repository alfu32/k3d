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
import kotlin.math.sqrt

class MechScrewTool(
    private val scene: GroupScene,
    private val segmentsProvider: () -> Int
) : Tool {
    override val id: ToolId = ToolId.MECH_SCREW
    override val message: String = "Select profile lines, then click screw center."

    private val sourceSegments = mutableListOf<SourceSegment>()
    private var centerWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        refreshSourceSegments()
        status.message = if (sourceSegments.isEmpty()) {
            "Select line segments first, then click screw center."
        } else {
            "Click screw center. Selected profile lines: ${sourceSegments.size}."
        }
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
            refreshSourceSegments()
            if (sourceSegments.isEmpty()) {
                status.message = "Screw needs selected line segments as a profile."
                return true
            }
            centerWorld = Vector3(world)
            status.message = "Click screw height point."
            return true
        }

        val center = centerWorld ?: return false
        val height = Vector3(world)
        val result = commitScrew(center, height)
        clearTransient()
        status.message = if (result) {
            "Created screw from ${sourceSegments.size} selected profile line(s)."
        } else {
            "Screw canceled: height is too short."
        }
        return true
    }

    override fun anchorWorld(): Vector3? {
        return centerWorld
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val center = centerWorld ?: return null
        if (!hasHover) return null
        return ToolMeasurement(Vector3(center), Vector3(hover))
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val center = centerWorld ?: return emptyList()
        if (!hasHover) return emptyList()
        return listOf(center to Vector3(hover))
    }

    override fun render(renderer: ShapeRenderer) {
        val center = centerWorld ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, center, 0.18f)
        if (hasHover) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, hover, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(center.x, center.y, center.z, hover.x, hover.y, hover.z)
        }
    }

    private fun commitScrew(centerWorld: Vector3, heightWorld: Vector3): Boolean {
        val group = scene.activeGroup()
        val center = group.toLocal(centerWorld)
        val height = group.toLocal(heightWorld)
        val axis = Vector3(height).sub(center)
        if (axis.len2() <= EPSILON_SQ) {
            return false
        }
        val axisUnit = Vector3(axis).nor()
        val steps = circleSegments()
        val faceColor = Color(scene.defaultFaceColor)
        val endModuleColor = Color(0.86f, 0.88f, 0.9f, 1f)

        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                group.lineStore.addSegment(center, height, autoCleanup = false)
                sourceSegments.forEach { source ->
                    val startSamples = ArrayList<Vector3>(steps + 1)
                    val endSamples = ArrayList<Vector3>(steps + 1)
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps.toFloat()
                        val angle = MathUtils.PI2 * t
                        startSamples += screwPoint(source.start, center, axis, axisUnit, angle, t)
                        endSamples += screwPoint(source.end, center, axis, axisUnit, angle, t)
                    }
                    for (i in 0 until steps) {
                        val a0 = startSamples[i]
                        val b0 = endSamples[i]
                        val a1 = startSamples[i + 1]
                        val b1 = endSamples[i + 1]
                        group.faceStore.addTriangle(a0, b0, b1, faceColor)
                        group.faceStore.addTriangle(a0, b1, a1, faceColor)
                        group.lineStore.addSegment(a0, b0, autoCleanup = false)
                        group.lineStore.addSegment(a0, a1, autoCleanup = false)
                        group.lineStore.addSegment(b0, b1, autoCleanup = false)
                    }
                    group.lineStore.addSegment(startSamples.last(), endSamples.last(), autoCleanup = false)
                    addEndHalfModule(
                        group = group,
                        source = source,
                        center = center,
                        axis = axis,
                        axisUnit = axisUnit,
                        startAngle = -MathUtils.PI,
                        endAngle = 0f,
                        lift = 0f,
                        steps = maxOf(1, steps / 2),
                        color = endModuleColor
                    )
                    addEndHalfModule(
                        group = group,
                        source = source,
                        center = center,
                        axis = axis,
                        axisUnit = axisUnit,
                        startAngle = MathUtils.PI2,
                        endAngle = MathUtils.PI2 + MathUtils.PI,
                        lift = 1f,
                        steps = maxOf(1, steps / 2),
                        color = endModuleColor
                    )
                }
            }
        }
        group.faceStore.notifyExternalChange()
        group.lineStore.notifyExternalChange()
        return true
    }

    private fun addEndHalfModule(
        group: GroupScene.GroupNode,
        source: SourceSegment,
        center: Vector3,
        axis: Vector3,
        axisUnit: Vector3,
        startAngle: Float,
        endAngle: Float,
        lift: Float,
        steps: Int,
        color: Color
    ) {
        val startSamples = ArrayList<Vector3>(steps + 1)
        val endSamples = ArrayList<Vector3>(steps + 1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val angle = MathUtils.lerp(startAngle, endAngle, t)
            startSamples += screwPoint(source.start, center, axis, axisUnit, angle, lift)
            endSamples += screwPoint(source.end, center, axis, axisUnit, angle, lift)
        }
        for (i in 0 until steps) {
            val a0 = startSamples[i]
            val b0 = endSamples[i]
            val a1 = startSamples[i + 1]
            val b1 = endSamples[i + 1]
            group.faceStore.addTriangle(a0, b0, b1, color)
            group.faceStore.addTriangle(a0, b1, a1, color)
            group.lineStore.addSegment(a0, b0, autoCleanup = false)
            group.lineStore.addSegment(a0, a1, autoCleanup = false)
            group.lineStore.addSegment(b0, b1, autoCleanup = false)
        }
        group.lineStore.addSegment(startSamples.last(), endSamples.last(), autoCleanup = false)
    }

    private fun screwPoint(
        sourcePoint: Vector3,
        center: Vector3,
        axis: Vector3,
        axisUnit: Vector3,
        angleRad: Float,
        t: Float
    ): Vector3 {
        val relative = Vector3(sourcePoint).sub(center)
        val axial = Vector3(axisUnit).scl(relative.dot(axisUnit))
        val radial = Vector3(relative).sub(axial)
        return Vector3(center)
            .add(axial)
            .mulAdd(axis, t)
            .add(rotateAroundAxis(radial, axisUnit, angleRad))
    }

    private fun refreshSourceSegments() {
        sourceSegments.clear()
        scene.activeGroup().lineStore.getSelected().forEach { segment ->
            if (segment.start.dst2(segment.end) > EPSILON_SQ) {
                sourceSegments += SourceSegment(Vector3(segment.start), Vector3(segment.end))
            }
        }
    }

    private fun clearTransient() {
        centerWorld = null
        hasHover = false
    }

    private fun circleSegments(): Int {
        return segmentsProvider().coerceIn(3, 256)
    }
}

class MechCircularHoleTool(
    private val scene: GroupScene,
    private val segmentsProvider: () -> Int
) : Tool {
    override val id: ToolId = ToolId.MECH_CIRCULAR_HOLE
    override val message: String = "Click circular hole center."

    private var centerWorld: Vector3? = null
    private var basis: PlaneBasis? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Click circular hole center."
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
            status.message = "Click circular hole radius point."
            return true
        }

        val center = centerWorld ?: return false
        val currentBasis = basis ?: planeBasisFromNormal(Vector3(0f, 1f, 0f))
        val radius = radiusOnPlane(center, world, currentBasis)
        if (radius <= EPSILON) {
            status.message = "Circular hole canceled: radius is too small."
            clearTransient()
            return true
        }
        commitCircularHole(center, currentBasis, radius)
        val sides = circleSegments()
        clearTransient()
        status.message = "Created circular hole module with $sides sides."
        return true
    }

    override fun anchorWorld(): Vector3? {
        return centerWorld
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val center = centerWorld ?: return null
        if (!hasHover) return null
        return ToolMeasurement(Vector3(center), Vector3(hover))
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val center = centerWorld ?: return emptyList()
        val currentBasis = basis ?: return emptyList()
        if (!hasHover) return emptyList()
        val radius = radiusOnPlane(center, hover, currentBasis)
        if (radius <= EPSILON) return emptyList()
        val inner = ringPoints(center, currentBasis, radius, circleSegments())
        val outer = ringPoints(center, currentBasis, radius * OUTER_RADIUS_FACTOR, circleSegments())
        val lines = mutableListOf<Pair<Vector3, Vector3>>()
        appendPathFeedbackLines(lines, inner, close = true)
        appendPathFeedbackLines(lines, outer, close = true)
        for (i in inner.indices) {
            lines += inner[i] to outer[i]
        }
        return lines
    }

    override fun render(renderer: ShapeRenderer) {
        val center = centerWorld ?: return
        val currentBasis = basis ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, center, 0.18f)
        if (hasHover) {
            val radius = radiusOnPlane(center, hover, currentBasis)
            if (radius > EPSILON) {
                renderer.color = ToolFeedbackColors.SECONDARY
                drawRing(renderer, center, currentBasis, radius, circleSegments())
                renderer.color = ToolFeedbackColors.TERTIARY
                drawRing(renderer, center, currentBasis, radius * OUTER_RADIUS_FACTOR, circleSegments())
            }
        }
    }

    private fun commitCircularHole(center: Vector3, basis: PlaneBasis, radius: Float) {
        val group = scene.activeGroup()
        val segments = circleSegments()
        val innerWorld = ringPoints(center, basis, radius, segments)
        val outerWorld = ringPoints(center, basis, radius * OUTER_RADIUS_FACTOR, segments)
        val inner = innerWorld.map(group::toLocal)
        val outer = outerWorld.map(group::toLocal)
        val green = Color(0.52f, 0.95f, 0.38f, 1f)
        val white = Color(0.96f, 0.96f, 0.92f, 1f)

        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                for (i in 0 until segments) {
                    val next = (i + 1) % segments
                    val innerA = inner[i]
                    val innerB = inner[next]
                    val outerA = outer[i]
                    val outerB = outer[next]
                    group.faceStore.addTriangle(outerA, innerA, innerB, green)
                    group.faceStore.addTriangle(outerA, innerB, outerB, white)
                    group.lineStore.addSegment(innerA, innerB, autoCleanup = false)
                    group.lineStore.addSegment(outerA, outerB, autoCleanup = false)
                    group.lineStore.addSegment(innerA, outerA, autoCleanup = false)
                }
            }
        }
        group.faceStore.notifyExternalChange()
        group.lineStore.notifyExternalChange()
    }

    private fun radiusOnPlane(center: Vector3, point: Vector3, basis: PlaneBasis): Float {
        val delta = Vector3(point).sub(center)
        val u = delta.dot(basis.axisU)
        val v = delta.dot(basis.axisV)
        return sqrt(u * u + v * v)
    }

    private fun ringPoints(center: Vector3, basis: PlaneBasis, radius: Float, segments: Int): List<Vector3> {
        val points = ArrayList<Vector3>(segments)
        for (i in 0 until segments) {
            val angle = MathUtils.PI2 * (i.toFloat() / segments)
            points += Vector3(center)
                .mulAdd(basis.axisU, MathUtils.cos(angle) * radius)
                .mulAdd(basis.axisV, MathUtils.sin(angle) * radius)
        }
        return points
    }

    private fun drawRing(
        renderer: ShapeRenderer,
        center: Vector3,
        basis: PlaneBasis,
        radius: Float,
        segments: Int
    ) {
        val points = ringPoints(center, basis, radius, segments)
        for (i in 0 until segments) {
            val a = points[i]
            val b = points[(i + 1) % segments]
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
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

private data class SourceSegment(val start: Vector3, val end: Vector3)

private const val EPSILON = 0.001f
private const val EPSILON_SQ = EPSILON * EPSILON
private const val OUTER_RADIUS_FACTOR = 1.75f

private fun rotateAroundAxis(vector: Vector3, axisUnit: Vector3, angleRad: Float): Vector3 {
    val cos = MathUtils.cos(angleRad)
    val sin = MathUtils.sin(angleRad)
    val parallel = Vector3(axisUnit).scl(axisUnit.dot(vector) * (1f - cos))
    val perpendicular = Vector3(vector).scl(cos)
    val cross = Vector3(axisUnit).crs(vector).scl(sin)
    return perpendicular.add(cross).add(parallel)
}

private fun drawCross(renderer: ShapeRenderer, point: Vector3, size: Float) {
    renderer.line(point.x - size, point.y, point.z, point.x + size, point.y, point.z)
    renderer.line(point.x, point.y - size, point.z, point.x, point.y + size, point.z)
    renderer.line(point.x, point.y, point.z - size, point.x, point.y, point.z + size)
}

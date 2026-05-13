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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class MechScrewTool(
    private val scene: GroupScene,
    private val segmentsProvider: () -> Int
) : Tool {
    override val id: ToolId = ToolId.MECH_SCREW
    override val message: String = "Select profile lines, then click screw center."

    private val sourceSegments = mutableListOf<SourceSegment>()
    private var centerWorld: Vector3? = null
    private var moduleHeightWorld: Vector3? = null
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
            status.message = "Click one-revolution height point."
            return true
        }
        if (moduleHeightWorld == null) {
            moduleHeightWorld = Vector3(world)
            status.message = "Click total screw length point."
            return true
        }

        val center = centerWorld ?: return false
        val moduleHeight = moduleHeightWorld ?: return false
        val result = commitScrew(center, moduleHeight, Vector3(world))
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
        val moduleHeight = moduleHeightWorld
        return if (moduleHeight == null) {
            listOf(center to Vector3(hover))
        } else {
            listOf(center to moduleHeight, center to Vector3(hover))
        }
    }

    override fun render(renderer: ShapeRenderer) {
        val center = centerWorld ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, center, 0.18f)
        moduleHeightWorld?.let { moduleHeight ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, moduleHeight, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(center.x, center.y, center.z, moduleHeight.x, moduleHeight.y, moduleHeight.z)
        }
        if (hasHover) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, hover, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(center.x, center.y, center.z, hover.x, hover.y, hover.z)
        }
    }

    private fun commitScrew(centerWorld: Vector3, moduleHeightWorld: Vector3, totalLengthWorld: Vector3): Boolean {
        val group = scene.activeGroup()
        val center = group.toLocal(centerWorld)
        val moduleHeight = group.toLocal(moduleHeightWorld)
        val totalLengthPoint = group.toLocal(totalLengthWorld)
        val moduleAxis = Vector3(moduleHeight).sub(center)
        if (moduleAxis.len2() <= EPSILON_SQ) {
            return false
        }
        val axisUnit = Vector3(moduleAxis).nor()
        val moduleLength = moduleAxis.len()
        val requestedLength = Vector3(totalLengthPoint).sub(center).dot(axisUnit)
        if (requestedLength <= EPSILON) {
            return false
        }
        val totalTurns = (requestedLength / moduleLength).coerceAtLeast(1f)
        val steps = circleSegments()
        val totalSteps = maxOf(steps, ceil(steps * totalTurns).toInt())
        val faceColor = Color(scene.defaultFaceColor)
        val capColor = Color(0.86f, 0.88f, 0.9f, 1f)

        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                val totalAxis = Vector3(moduleAxis).scl(totalTurns)
                group.lineStore.addSegment(center, Vector3(center).add(totalAxis), autoCleanup = false)
                sourceSegments.forEach { source ->
                    val startSamples = ArrayList<Vector3>(totalSteps + 1)
                    val endSamples = ArrayList<Vector3>(totalSteps + 1)
                    for (i in 0..totalSteps) {
                        val t = totalTurns * (i.toFloat() / totalSteps.toFloat())
                        val angle = MathUtils.PI2 * t
                        startSamples += screwPoint(source.start, center, moduleAxis, axisUnit, angle, t)
                        endSamples += screwPoint(source.end, center, moduleAxis, axisUnit, angle, t)
                    }
                    for (i in 0 until totalSteps) {
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
                }
                if (totalTurns >= 2f) {
                    addAxialEndCaps(
                        group = group,
                        center = center,
                        moduleAxis = moduleAxis,
                        axisUnit = axisUnit,
                        totalTurns = totalTurns,
                        segments = steps,
                        color = capColor
                    )
                }
            }
        }
        group.faceStore.notifyExternalChange()
        group.lineStore.notifyExternalChange()
        return true
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
        moduleHeightWorld = null
        hasHover = false
    }

    private fun circleSegments(): Int {
        return segmentsProvider().coerceIn(3, 256)
    }

    private fun addAxialEndCaps(
        group: GroupScene.GroupNode,
        center: Vector3,
        moduleAxis: Vector3,
        axisUnit: Vector3,
        totalTurns: Float,
        segments: Int,
        color: Color
    ) {
        val boundaries = profileBoundaryRings(center, axisUnit)
        if (boundaries == null) {
            return
        }
        addRevolvedCap(
            group = group,
            capCenter = Vector3(center).mulAdd(axisUnit, boundaries.minAxial),
            axisUnit = axisUnit,
            rings = boundaries.minRadials,
            phase = 0f,
            segments = segments,
            color = color,
            reverse = true
        )
        addRevolvedCap(
            group = group,
            capCenter = Vector3(center)
                .mulAdd(moduleAxis, totalTurns)
                .mulAdd(axisUnit, boundaries.maxAxial),
            axisUnit = axisUnit,
            rings = boundaries.maxRadials,
            phase = MathUtils.PI2 * totalTurns,
            segments = segments,
            color = color,
            reverse = false
        )
    }

    private fun profileBoundaryRings(center: Vector3, axisUnit: Vector3): ProfileBoundaries? {
        val points = sourceSegments.flatMap { listOf(it.start, it.end) }
        if (points.isEmpty()) {
            return null
        }
        val axialValues = points.map { Vector3(it).sub(center).dot(axisUnit) }
        val minAxial = axialValues.minOrNull() ?: return null
        val maxAxial = axialValues.maxOrNull() ?: return null
        val minRadials = boundaryRadials(points, center, axisUnit, minAxial)
        val maxRadials = boundaryRadials(points, center, axisUnit, maxAxial)
        if (minRadials.isEmpty() || maxRadials.isEmpty()) {
            return null
        }
        return ProfileBoundaries(minAxial, maxAxial, minRadials, maxRadials)
    }

    private fun boundaryRadials(
        points: List<Vector3>,
        center: Vector3,
        axisUnit: Vector3,
        targetAxial: Float
    ): List<Vector3> {
        val radials = points.mapNotNull { point ->
            val relative = Vector3(point).sub(center)
            val axial = relative.dot(axisUnit)
            if (abs(axial - targetAxial) > 0.01f) {
                null
            } else {
                Vector3(relative).mulAdd(axisUnit, -axial)
            }
        }.filter { it.len2() > EPSILON_SQ }
            .sortedBy { it.len2() }

        val unique = mutableListOf<Vector3>()
        radials.forEach { radial ->
            if (unique.none { it.dst2(radial) <= 0.0001f || abs(it.len2() - radial.len2()) <= 0.0001f }) {
                unique += radial
            }
        }
        return unique
    }

    private fun addRevolvedCap(
        group: GroupScene.GroupNode,
        capCenter: Vector3,
        axisUnit: Vector3,
        rings: List<Vector3>,
        phase: Float,
        segments: Int,
        color: Color,
        reverse: Boolean
    ) {
        val ringPoints = rings.map { radial ->
            List(segments) { i ->
                val angle = phase + MathUtils.PI2 * (i.toFloat() / segments.toFloat())
                Vector3(capCenter).add(rotateAroundAxis(radial, axisUnit, angle))
            }
        }
        if (ringPoints.isEmpty()) {
            return
        }
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            if (ringPoints.size == 1) {
                val a = ringPoints[0][i]
                val b = ringPoints[0][next]
                addCapTriangle(group, capCenter, a, b, color, reverse)
                group.lineStore.addSegment(a, b, autoCleanup = false)
            } else {
                for (r in 0 until ringPoints.lastIndex) {
                    val innerA = ringPoints[r][i]
                    val innerB = ringPoints[r][next]
                    val outerA = ringPoints[r + 1][i]
                    val outerB = ringPoints[r + 1][next]
                    addCapTriangle(group, innerA, outerA, outerB, color, reverse)
                    addCapTriangle(group, innerA, outerB, innerB, color, reverse)
                    group.lineStore.addSegment(innerA, innerB, autoCleanup = false)
                    group.lineStore.addSegment(outerA, outerB, autoCleanup = false)
                    group.lineStore.addSegment(innerA, outerA, autoCleanup = false)
                }
            }
        }
    }

    private fun addCapTriangle(
        group: GroupScene.GroupNode,
        a: Vector3,
        b: Vector3,
        c: Vector3,
        color: Color,
        reverse: Boolean
    ) {
        if (reverse) {
            group.faceStore.addTriangle(a, c, b, color)
        } else {
            group.faceStore.addTriangle(a, b, c, color)
        }
    }
}

private data class ProfileBoundaries(
    val minAxial: Float,
    val maxAxial: Float,
    val minRadials: List<Vector3>,
    val maxRadials: List<Vector3>
)

abstract class BaseMechWasherTool(
    private val scene: GroupScene,
    private val segmentsProvider: () -> Int
) : Tool {
    protected abstract val toolLabel: String
    protected abstract val outerKindLabel: String
    protected abstract fun outerPoints(center: Vector3, basis: PlaneBasis, outerRadius: Float, segments: Int): List<Vector3>

    override val message: String
        get() = "Click $toolLabel center."

    private var centerWorld: Vector3? = null
    private var basis: PlaneBasis? = null
    private var innerRadius: Float? = null
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
        if (centerWorld == null) {
            centerWorld = Vector3(world)
            basis = planeBasisFromNormal(normal ?: Vector3(0f, 1f, 0f))
            status.message = "Click inner radius point."
            return true
        }

        val center = centerWorld ?: return false
        val currentBasis = basis ?: planeBasisFromNormal(Vector3(0f, 1f, 0f))
        if (innerRadius == null) {
            val radius = radiusOnPlane(center, world, currentBasis)
            if (radius <= EPSILON) {
                status.message = "$toolLabel canceled: inner radius is too small."
                clearTransient()
                return true
            }
            innerRadius = radius
            status.message = "Click outer $outerKindLabel radius point."
            return true
        }

        val inner = innerRadius ?: return false
        val outer = radiusOnPlane(center, world, currentBasis)
        if (outer <= inner + EPSILON) {
            status.message = "$toolLabel canceled: outer radius must be larger than inner radius."
            clearTransient()
            return true
        }
        commitWasher(center, currentBasis, inner, outer)
        val sides = washerSegments()
        clearTransient()
        status.message = "Created $toolLabel with $sides inner sides."
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
        val previewInner = innerRadius ?: radiusOnPlane(center, hover, currentBasis)
        if (previewInner <= EPSILON) return emptyList()
        val previewOuter = if (innerRadius == null) {
            previewInner
        } else {
            radiusOnPlane(center, hover, currentBasis).coerceAtLeast(previewInner + EPSILON)
        }
        val segments = washerSegments()
        val inner = ringPoints(center, currentBasis, previewInner, segments)
        val lines = mutableListOf<Pair<Vector3, Vector3>>()
        appendPathFeedbackLines(lines, inner, close = true)
        if (innerRadius != null) {
            val outer = outerPoints(center, currentBasis, previewOuter, segments)
            appendPathFeedbackLines(lines, outer, close = true)
            for (i in inner.indices) {
                lines += inner[i] to outer[i]
            }
        }
        return lines
    }

    override fun render(renderer: ShapeRenderer) {
        val center = centerWorld ?: return
        val currentBasis = basis ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, center, 0.18f)
        if (hasHover) {
            val previewInner = innerRadius ?: radiusOnPlane(center, hover, currentBasis)
            if (previewInner > EPSILON) {
                renderer.color = ToolFeedbackColors.SECONDARY
                drawPath(renderer, ringPoints(center, currentBasis, previewInner, washerSegments()))
                if (innerRadius != null) {
                    val previewOuter = radiusOnPlane(center, hover, currentBasis).coerceAtLeast(previewInner + EPSILON)
                    renderer.color = ToolFeedbackColors.TERTIARY
                    drawPath(renderer, outerPoints(center, currentBasis, previewOuter, washerSegments()))
                }
            }
        }
    }

    private fun commitWasher(center: Vector3, basis: PlaneBasis, innerRadius: Float, outerRadius: Float) {
        val group = scene.activeGroup()
        val segments = washerSegments()
        val innerWorld = ringPoints(center, basis, innerRadius, segments)
        val outerWorld = outerPoints(center, basis, outerRadius, segments)
        val inner = innerWorld.map(group::toLocal)
        val outer = outerWorld.map(group::toLocal)
        val faceColor = Color(scene.defaultFaceColor)

        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                for (i in 0 until segments) {
                    val next = (i + 1) % segments
                    val innerA = inner[i]
                    val innerB = inner[next]
                    val outerA = outer[i]
                    val outerB = outer[next]
                    group.faceStore.addTriangle(outerA, innerA, innerB, faceColor)
                    group.faceStore.addTriangle(outerA, innerB, outerB, faceColor)
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

    private fun drawPath(renderer: ShapeRenderer, points: List<Vector3>) {
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
    }

    private fun clearTransient() {
        centerWorld = null
        basis = null
        innerRadius = null
        hasHover = false
    }

    private fun washerSegments(): Int {
        val segments = segmentsProvider().coerceIn(8, 256)
        return if (segments % 8 == 0) segments else segments + (8 - segments % 8)
    }
}

class MechCircularHoleTool(
    scene: GroupScene,
    segmentsProvider: () -> Int
) : BaseMechWasherTool(scene, segmentsProvider) {
    override val id: ToolId = ToolId.MECH_CIRCULAR_HOLE
    override val toolLabel: String = "circular hole patch"
    override val outerKindLabel: String = "square"

    override fun outerPoints(center: Vector3, basis: PlaneBasis, outerRadius: Float, segments: Int): List<Vector3> {
        val points = ArrayList<Vector3>(segments)
        for (i in 0 until segments) {
            val angle = MathUtils.PI2 * (i.toFloat() / segments)
            val cos = MathUtils.cos(angle)
            val sin = MathUtils.sin(angle)
            val scale = outerRadius / maxOf(abs(cos), abs(sin)).coerceAtLeast(0.0001f)
            points += Vector3(center)
                .mulAdd(basis.axisU, cos * scale)
                .mulAdd(basis.axisV, sin * scale)
        }
        return points
    }
}

class MechRoundWasherTool(
    scene: GroupScene,
    segmentsProvider: () -> Int
) : BaseMechWasherTool(scene, segmentsProvider) {
    override val id: ToolId = ToolId.MECH_ROUND_WASHER
    override val toolLabel: String = "round washer"
    override val outerKindLabel: String = "outer"

    override fun outerPoints(center: Vector3, basis: PlaneBasis, outerRadius: Float, segments: Int): List<Vector3> {
        return ringPoints(center, basis, outerRadius, segments)
    }
}

private data class SourceSegment(val start: Vector3, val end: Vector3)

private const val EPSILON = 0.001f
private const val EPSILON_SQ = EPSILON * EPSILON

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

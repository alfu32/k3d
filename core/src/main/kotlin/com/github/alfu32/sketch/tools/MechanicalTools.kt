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
import kotlin.math.roundToInt
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
        val result = commitScrew(center, Vector3(world))
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
        hasHover = false
    }

    private fun circleSegments(): Int {
        return segmentsProvider().coerceIn(3, 256)
    }
}

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

class MechCogWheelTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.MECH_COG_WHEEL
    override val message: String = "Click tooth tangent start."

    private var tangentStartWorld: Vector3? = null
    private var tangentEndWorld: Vector3? = null
    private var toothCountWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Click tooth tangent start."
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
        val tangentStart = tangentStartWorld
        val tangentEnd = tangentEndWorld
        val toothCount = toothCountWorld

        if (tangentStart == null) {
            tangentStartWorld = Vector3(world)
            status.message = "Click tangent end point."
            return true
        }

        if (tangentEnd == null) {
            tangentEndWorld = Vector3(world)
            status.message = "Click tooth count point. Distance from tangent start in model units rounds to the number of teeth."
            return true
        }

        if (toothCount == null) {
            toothCountWorld = Vector3(world)
            status.message = "Click tooth depth point."
            return true
        }

        val result = commitCogWheel(tangentStart, tangentEnd, toothCount, Vector3(world))
        clearTransient()
        status.message = result.message
        return true
    }

    override fun anchorWorld(): Vector3? {
        return toothCountWorld ?: tangentEndWorld ?: tangentStartWorld
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val start = tangentStartWorld ?: return null
        if (!hasHover) return null
        return ToolMeasurement(Vector3(start), Vector3(hover))
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val tangentStart = tangentStartWorld ?: return emptyList()
        val lines = mutableListOf<Pair<Vector3, Vector3>>()
        val tangentEnd = tangentEndWorld
        if (tangentEnd == null) {
            if (hasHover) {
                lines += tangentStart to Vector3(hover)
            }
            return lines
        }
        lines += tangentStart to Vector3(tangentEnd)
        val toothCount = toothCountWorld
        if (toothCount == null) {
            if (hasHover) {
                lines += tangentStart to Vector3(hover)
            }
            return lines
        }
        lines += tangentStart to Vector3(toothCount)
        previewCogGeometry(tangentStart, tangentEnd, toothCount, if (hasHover) hover else null)?.let { geometry ->
            lines += geometry.center to geometry.tangentMidpoint
        }
        if (hasHover) {
            lines += tangentStart to Vector3(hover)
        }
        return lines
    }

    override fun render(renderer: ShapeRenderer) {
        val tangentStart = tangentStartWorld ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, tangentStart, 0.18f)
        tangentEndWorld?.let { tangentEnd ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, tangentEnd, 0.18f)
            renderer.line(tangentStart.x, tangentStart.y, tangentStart.z, tangentEnd.x, tangentEnd.y, tangentEnd.z)
        }
        toothCountWorld?.let { toothCount ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, toothCount, 0.18f)
            val depth = if (hasHover) hover else null
            tangentEndWorld?.let { tangentEnd ->
                previewCogGeometry(tangentStart, tangentEnd, toothCount, depth)?.let { geometry ->
                    renderer.color = ToolFeedbackColors.TERTIARY
                    drawCross(renderer, geometry.center, 0.18f)
                    drawPolyline(renderer, geometry.outline, close = true)
                }
            }
        }
        if (hasHover) {
            renderer.color = ToolFeedbackColors.TERTIARY
            drawCross(renderer, hover, 0.18f)
            renderer.line(tangentStart.x, tangentStart.y, tangentStart.z, hover.x, hover.y, hover.z)
        }
    }

    private fun commitCogWheel(
        tangentStartWorld: Vector3,
        tangentEndWorld: Vector3,
        countWorld: Vector3,
        depthWorld: Vector3
    ): CogResult {
        val group = scene.activeGroup()
        val rawStart = group.toLocal(tangentStartWorld)
        val planeY = rawStart.y
        val tangentStart = horizontalPoint(rawStart, planeY)
        val tangentEnd = horizontalPoint(group.toLocal(tangentEndWorld), planeY)
        val countPoint = horizontalPoint(group.toLocal(countWorld), planeY)
        val depthPoint = horizontalPoint(group.toLocal(depthWorld), planeY)
        val tangent = Vector3(tangentEnd).sub(tangentStart)
        if (tangent.len2() <= EPSILON_SQ) {
            return CogResult(false, "Cog Wheel canceled: tangent start and end are too close.")
        }
        val toothCountMeasure = horizontalDistance(tangentStart, countPoint)
        if (toothCountMeasure < 3f) {
            return CogResult(false, "Cog Wheel canceled: tooth count measure must be at least 3 units.")
        }
        val requestedToothCount = toothCountMeasure.roundToInt()
        val toothCount = requestedToothCount.coerceAtMost(MAX_COG_TEETH)
        val depth = horizontalDistance(tangentStart, depthPoint)
        if (depth <= EPSILON) {
            return CogResult(false, "Cog Wheel canceled: tooth depth is too small.")
        }
        val geometry = cogGeometry(tangentStart, tangentEnd, toothCount, depth)
            ?: return CogResult(false, "Cog Wheel canceled: invalid cog geometry.")
        var edges = 0

        group.lineStore.withChangeSuppressed {
            for (i in geometry.outline.indices) {
                val next = (i + 1) % geometry.outline.size
                group.lineStore.addSegment(geometry.outline[i], geometry.outline[next], autoCleanup = false)
                edges++
            }
            group.lineStore.addSegment(geometry.center, geometry.tangentMidpoint, autoCleanup = false)
            edges++
        }
        group.lineStore.notifyExternalChange()
        val capMessage = if (requestedToothCount > MAX_COG_TEETH) " limited from $requestedToothCount" else ""
        return CogResult(
            true,
            "Created planar cog wheel with $toothCount teeth$capMessage, depth ${formatDecimal(depth)}, and $edges segment(s)."
        )
    }

    private fun clearTransient() {
        tangentStartWorld = null
        tangentEndWorld = null
        toothCountWorld = null
        hasHover = false
    }
}

private data class SourceSegment(val start: Vector3, val end: Vector3)
private data class CogResult(val success: Boolean, val message: String)
private data class CogGeometry(val center: Vector3, val tangentMidpoint: Vector3, val outline: List<Vector3>)

private const val EPSILON = 0.001f
private const val EPSILON_SQ = EPSILON * EPSILON
private const val MAX_COG_TEETH = 512

private fun horizontalPoint(point: Vector3, planeY: Float): Vector3 {
    return Vector3(point.x, planeY, point.z)
}

private fun horizontalDistance(a: Vector3, b: Vector3): Float {
    val dx = b.x - a.x
    val dz = b.z - a.z
    return sqrt(dx * dx + dz * dz)
}

private fun horizontalMidpoint(a: Vector3, b: Vector3): Vector3 {
    return Vector3((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f, (a.z + b.z) * 0.5f)
}

private fun previewCogGeometry(
    tangentStart: Vector3,
    tangentEnd: Vector3,
    countPoint: Vector3,
    depthPoint: Vector3?
): CogGeometry? {
    val planeY = tangentStart.y
    val start = horizontalPoint(tangentStart, planeY)
    val end = horizontalPoint(tangentEnd, planeY)
    val count = horizontalPoint(countPoint, planeY)
    val toothCount = horizontalDistance(start, count).roundToInt().coerceAtMost(MAX_COG_TEETH)
    val depth = depthPoint?.let { horizontalDistance(start, horizontalPoint(it, planeY)) } ?: 0f
    return cogGeometry(start, end, toothCount, depth)
}

private fun cogGeometry(tangentStart: Vector3, tangentEnd: Vector3, toothCount: Int, toothDepth: Float): CogGeometry? {
    if (toothCount < 3) {
        return null
    }
    val tangent = Vector3(tangentEnd).sub(tangentStart)
    if (tangent.len2() <= EPSILON_SQ) {
        return null
    }
    val tangentLength = tangent.len()
    val rootRadius = tangentLength * toothCount.toFloat() / MathUtils.PI2
    val tangentUnit = tangent.scl(1f / tangentLength)
    val leftNormal = Vector3(-tangentUnit.z, 0f, tangentUnit.x)
    val midpoint = horizontalMidpoint(tangentStart, tangentEnd)
    val center = Vector3(midpoint).mulAdd(leftNormal, rootRadius)
    val radialToMidpoint = Vector3(midpoint).sub(center)
    val baseAngle = MathUtils.atan2(radialToMidpoint.z, radialToMidpoint.x)
    val pitchAngle = MathUtils.PI2 / toothCount.toFloat()
    val outerRadius = rootRadius + toothDepth.coerceAtLeast(0f)
    val outline = ArrayList<Vector3>(toothCount * 3 + 1)

    for (tooth in 0 until toothCount) {
        val toothAngle = baseAngle + pitchAngle * tooth.toFloat()
        if (tooth == 0) {
            outline += polarHorizontal(center, rootRadius, toothAngle - pitchAngle * 0.5f)
        }
        outline += polarHorizontal(center, outerRadius, toothAngle - pitchAngle * 0.25f)
        outline += polarHorizontal(center, outerRadius, toothAngle + pitchAngle * 0.25f)
        outline += polarHorizontal(center, rootRadius, toothAngle + pitchAngle * 0.5f)
    }

    return CogGeometry(center, midpoint, outline)
}

private fun polarHorizontal(center: Vector3, radius: Float, angle: Float): Vector3 {
    return Vector3(
        center.x + MathUtils.cos(angle) * radius,
        center.y,
        center.z + MathUtils.sin(angle) * radius
    )
}

private fun formatDecimal(value: Float): String {
    return ((value * 100f).roundToInt() / 100f).toString()
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

private fun rotateAroundAxis(vector: Vector3, axisUnit: Vector3, angleRad: Float): Vector3 {
    val cos = MathUtils.cos(angleRad)
    val sin = MathUtils.sin(angleRad)
    val parallel = Vector3(axisUnit).scl(axisUnit.dot(vector) * (1f - cos))
    val perpendicular = Vector3(vector).scl(cos)
    val cross = Vector3(axisUnit).crs(vector).scl(sin)
    return perpendicular.add(cross).add(parallel)
}

private fun drawPolyline(renderer: ShapeRenderer, points: List<Vector3>, close: Boolean) {
    if (points.size < 2) {
        return
    }
    for (i in 0 until points.lastIndex) {
        val start = points[i]
        val end = points[i + 1]
        renderer.line(start.x, start.y, start.z, end.x, end.y, end.z)
    }
    if (close) {
        val start = points.last()
        val end = points.first()
        renderer.line(start.x, start.y, start.z, end.x, end.y, end.z)
    }
}

private fun drawCross(renderer: ShapeRenderer, point: Vector3, size: Float) {
    renderer.line(point.x - size, point.y, point.z, point.x + size, point.y, point.z)
    renderer.line(point.x, point.y - size, point.z, point.x, point.y + size, point.z)
    renderer.line(point.x, point.y, point.z - size, point.x, point.y, point.z + size)
}

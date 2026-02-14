package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

class ExtrudeSwipeTool(
    private val scene: GroupScene,
    private val settings: PolylineSettings,
    private val onSelectTool: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.EXTRUDE_SWIPE
    override val message: String = "Select one or more lines, then click to draw swipe path."

    private data class SourceSegment(val start: Vector3, val end: Vector3)

    private val sourceSegments = mutableListOf<SourceSegment>()
    private val pathLocal = mutableListOf<Vector3>()
    private val hoverLocal = Vector3()
    private var hasHover = false
    private var arcMode = ArcMode.LINE
    private var arcCenterLocal: Vector3? = null
    private var arcPass1Local: Vector3? = null

    private val epsilon = 1e-4f
    private val epsilonSq = epsilon * epsilon

    override fun onEnter(status: StatusModel) {
        clearTransient()
        refreshSourceSegments()
        status.message = if (sourceSegments.isEmpty()) {
            message
        } else {
            "Click to start swipe path. A/C/L arc modes. Enter creates faces. Esc selects."
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
            hoverLocal.set(scene.activeGroup().toLocal(world))
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        if (pathLocal.isEmpty()) {
            refreshSourceSegments()
        }
        if (sourceSegments.isEmpty()) {
            status.message = "Select one or more line segments before using Extrude Swipe."
            return false
        }

        val local = scene.activeGroup().toLocal(world)
        if (pathLocal.isEmpty()) {
            pathLocal.add(Vector3(local))
            status.message = "Click next path point. A/C/L arc modes. Enter creates faces."
            return true
        }
        if (pathLocal.last().dst2(local) <= epsilonSq) {
            return true
        }

        when (arcMode) {
            ArcMode.CENTER -> {
                if (arcCenterLocal == null) {
                    arcCenterLocal = Vector3(local)
                    status.message = "Pick arc end point. Enter creates faces."
                } else {
                    val arcPoints = arcPointsFromCenter(pathLocal.last(), local, arcCenterLocal!!)
                    appendArcPoints(arcPoints)
                    arcCenterLocal = null
                    status.message = "Click to continue path. A/C/L arc modes. Enter creates faces."
                }
            }

            ArcMode.THREE -> {
                if (arcPass1Local == null) {
                    arcPass1Local = Vector3(local)
                    status.message = "Pick arc end point. Enter creates faces."
                } else {
                    val arcPoints = arcPointsThrough(pathLocal.last(), arcPass1Local!!, local)
                    appendArcPoints(arcPoints)
                    arcPass1Local = null
                    status.message = "Click to continue path. A/C/L arc modes. Enter creates faces."
                }
            }

            ArcMode.LINE -> {
                pathLocal.add(Vector3(local))
                status.message = "Click to continue path. A/C/L arc modes. Enter creates faces."
            }
        }
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.A -> {
                arcMode = ArcMode.THREE
                arcCenterLocal = null
                arcPass1Local = null
                status.message = "Arc mode (through point). Pick mid then end."
                true
            }

            Input.Keys.C -> {
                arcMode = ArcMode.CENTER
                arcCenterLocal = null
                arcPass1Local = null
                status.message = "Arc mode (center). Pick center then end."
                true
            }

            Input.Keys.L -> {
                arcMode = ArcMode.LINE
                arcCenterLocal = null
                arcPass1Local = null
                status.message = "Line mode."
                true
            }

            Input.Keys.ENTER -> {
                if (sourceSegments.isEmpty()) {
                    refreshSourceSegments()
                }
                if (sourceSegments.isEmpty()) {
                    status.message = "Select one or more line segments before using Extrude Swipe."
                    true
                } else if (pathLocal.size < 2) {
                    status.message = "Pick at least two path points."
                    true
                } else {
                    val generated = commitSwipe()
                    clearPath()
                    status.message = if (generated > 0) {
                        "Extrude Swipe: created $generated faces."
                    } else {
                        "Extrude Swipe: nothing generated."
                    }
                    onSelectTool()
                    true
                }
            }

            Input.Keys.BACKSPACE -> {
                if (arcMode == ArcMode.CENTER && arcCenterLocal != null) {
                    arcCenterLocal = null
                    status.message = "Arc center cleared."
                    true
                } else if (arcMode == ArcMode.THREE && arcPass1Local != null) {
                    arcPass1Local = null
                    status.message = "Arc mid-point cleared."
                    true
                } else if (pathLocal.isNotEmpty()) {
                    pathLocal.removeAt(pathLocal.lastIndex)
                    status.message = if (pathLocal.isEmpty()) {
                        "Click to start swipe path. Enter creates faces."
                    } else {
                        "Click next path point. A/C/L arc modes. Enter creates faces."
                    }
                    true
                } else {
                    false
                }
            }

            Input.Keys.ESCAPE -> {
                clearPath()
                status.message = "Canceled."
                onSelectTool()
                true
            }

            else -> false
        }
    }

    override fun anchorWorld(): Vector3? {
        if (pathLocal.isEmpty()) {
            return null
        }
        return scene.activeGroup().toWorld(pathLocal.last())
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        if (!hasHover || pathLocal.isEmpty()) {
            return null
        }
        val group = scene.activeGroup()
        return ToolMeasurement(
            startWorld = group.toWorld(pathLocal.last()),
            endWorld = group.toWorld(hoverLocal),
            lineColor = Color(0.4f, 0.9f, 1f, 1f)
        )
    }

    override fun render(renderer: ShapeRenderer) {
        val group = scene.activeGroup()

        renderer.color = Color(0.95f, 0.7f, 0.25f, 1f)
        sourceSegments.forEach { segment ->
            renderer.line(group.toWorld(segment.start), group.toWorld(segment.end))
        }

        if (pathLocal.isEmpty()) {
            return
        }

        renderer.color = Color(0.35f, 0.9f, 1f, 1f)
        for (i in 0 until pathLocal.lastIndex) {
            renderer.line(group.toWorld(pathLocal[i]), group.toWorld(pathLocal[i + 1]))
        }
        if (hasHover) {
            val preview = when {
                arcMode == ArcMode.CENTER && arcCenterLocal != null ->
                    arcPointsFromCenter(pathLocal.last(), hoverLocal, arcCenterLocal!!)
                arcMode == ArcMode.THREE && arcPass1Local != null ->
                    arcPointsThrough(pathLocal.last(), arcPass1Local!!, hoverLocal)
                else -> null
            }
            if (preview != null && preview.size > 1) {
                for (i in 0 until preview.lastIndex) {
                    renderer.line(group.toWorld(preview[i]), group.toWorld(preview[i + 1]))
                }
            } else {
                renderer.line(group.toWorld(pathLocal.last()), group.toWorld(hoverLocal))
            }
        }
    }

    private fun commitSwipe(): Int {
        val group = scene.activeGroup()
        val path = dedupePath(pathLocal)
        if (path.size < 2 || sourceSegments.isEmpty()) {
            return 0
        }

        val firstDir = Vector3(path[1]).sub(path[0])
        if (firstDir.len2() <= epsilonSq) {
            return 0
        }
        firstDir.nor()
        val projectedFigure = sourceSegments.mapNotNull { segment ->
            val a = projectPointToPlane(segment.start, path[0], firstDir)
            val b = projectPointToPlane(segment.end, path[0], firstDir)
            if (a.dst2(b) <= epsilonSq) {
                null
            } else {
                SourceSegment(a, b)
            }
        }
        if (projectedFigure.isEmpty()) {
            return 0
        }
        val segmentDirs = buildPathDirections(path)
        if (segmentDirs.isEmpty()) {
            return 0
        }
        val up = localVerticalAxis(group)
        val nodeHeadings = buildNodeHeadings(segmentDirs, up)
        if (nodeHeadings.isEmpty()) {
            return 0
        }
        val baseHeading = nodeHeadings.first()
        val nodeAngles = nodeHeadings.map { heading ->
            signedAngleAroundAxis(baseHeading, heading, up)
        }

        var generatedFaces = 0
        var generatedLines = 0

        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                projectedFigure.forEach { segment ->
                    for (i in 0 until path.lastIndex) {
                        val a0 = transformFigurePoint(segment.start, path.first(), path[i], up, nodeAngles[i])
                        val b0 = transformFigurePoint(segment.end, path.first(), path[i], up, nodeAngles[i])
                        val a1 = transformFigurePoint(segment.start, path.first(), path[i + 1], up, nodeAngles[i + 1])
                        val b1 = transformFigurePoint(segment.end, path.first(), path[i + 1], up, nodeAngles[i + 1])
                        val spanNormal = Vector3(b0).sub(a0).crs(Vector3(path[i + 1]).sub(path[i]))
                        if (spanNormal.len2() <= epsilonSq) {
                            continue
                        }
                        generatedFaces += addQuad(group, a0, b0, b1, a1, spanNormal)
                        generatedLines += addQuadEdges(group, a0, b0, b1, a1)
                    }
                }
                if (generatedLines > 0) {
                    group.lineStore.cleanupJts()
                }
            }
        }

        if (generatedFaces > 0 || generatedLines > 0) {
            group.faceStore.notifyExternalChange()
            group.lineStore.notifyExternalChange()
        }

        return generatedFaces
    }

    private fun addQuad(
        group: GroupScene.GroupNode,
        p0: Vector3,
        p1: Vector3,
        p2: Vector3,
        p3: Vector3,
        preferredNormal: Vector3
    ): Int {
        var count = 0
        val tri0 = Vector3(p1).sub(p0).crs(Vector3(p2).sub(p0))
        if (tri0.len2() > epsilonSq) {
            if (tri0.dot(preferredNormal) >= 0f) {
                group.faceStore.addTriangle(p0, p1, p2)
            } else {
                group.faceStore.addTriangle(p0, p2, p1)
            }
            count++
        }

        val tri1 = Vector3(p2).sub(p0).crs(Vector3(p3).sub(p0))
        if (tri1.len2() > epsilonSq) {
            if (tri1.dot(preferredNormal) >= 0f) {
                group.faceStore.addTriangle(p0, p2, p3)
            } else {
                group.faceStore.addTriangle(p0, p3, p2)
            }
            count++
        }
        return count
    }

    private fun addQuadEdges(
        group: GroupScene.GroupNode,
        p0: Vector3,
        p1: Vector3,
        p2: Vector3,
        p3: Vector3
    ): Int {
        var count = 0
        if (p0.dst2(p1) > epsilonSq) {
            group.lineStore.addSegment(p0, p1, autoCleanup = false)
            count++
        }
        if (p1.dst2(p2) > epsilonSq) {
            group.lineStore.addSegment(p1, p2, autoCleanup = false)
            count++
        }
        if (p2.dst2(p3) > epsilonSq) {
            group.lineStore.addSegment(p2, p3, autoCleanup = false)
            count++
        }
        if (p3.dst2(p0) > epsilonSq) {
            group.lineStore.addSegment(p3, p0, autoCleanup = false)
            count++
        }
        return count
    }

    private fun buildPathDirections(path: List<Vector3>): List<Vector3> {
        val dirs = mutableListOf<Vector3>()
        for (i in 0 until path.lastIndex) {
            val dir = Vector3(path[i + 1]).sub(path[i])
            if (dir.len2() <= epsilonSq) {
                continue
            }
            dirs.add(dir.nor())
        }
        return dirs
    }

    private fun buildNodeHeadings(segmentDirs: List<Vector3>, up: Vector3): List<Vector3> {
        if (segmentDirs.isEmpty()) {
            return emptyList()
        }
        val segmentHeadings = mutableListOf<Vector3>()
        segmentDirs.forEach { dir ->
            val heading = rejectOnAxis(dir, up)
            if (heading.len2() > epsilonSq) {
                segmentHeadings.add(heading.nor())
            } else if (segmentHeadings.isNotEmpty()) {
                segmentHeadings.add(Vector3(segmentHeadings.last()))
            } else {
                segmentHeadings.add(defaultHeading(up))
            }
        }
        val headings = MutableList(segmentHeadings.size + 1) { Vector3() }
        headings[0] = Vector3(segmentHeadings.first())
        headings[headings.lastIndex] = Vector3(segmentHeadings.last())
        for (i in 1 until headings.lastIndex) {
            val prev = segmentHeadings[i - 1]
            val next = segmentHeadings[i]
            val bisector = Vector3(prev).add(next)
            headings[i] = if (bisector.len2() > epsilonSq) {
                bisector.nor()
            } else {
                Vector3(next)
            }
        }
        return headings
    }

    private fun signedAngleAroundAxis(from: Vector3, to: Vector3, axis: Vector3): Float {
        val a = rejectOnAxis(from, axis)
        val b = rejectOnAxis(to, axis)
        if (a.len2() <= epsilonSq || b.len2() <= epsilonSq) {
            return 0f
        }
        a.nor()
        b.nor()
        val cross = Vector3(a).crs(b)
        return atan2(cross.dot(axis).toDouble(), a.dot(b).toDouble()).toFloat()
    }

    private fun transformFigurePoint(point: Vector3, pathStart: Vector3, pathNode: Vector3, up: Vector3, angle: Float): Vector3 {
        val relative = Vector3(point).sub(pathStart)
        val rotated = rotateVectorAroundAxis(relative, up, angle)
        return Vector3(pathNode).add(rotated)
    }

    private fun rotateVectorAroundAxis(vector: Vector3, axis: Vector3, angle: Float): Vector3 {
        if (abs(angle) <= 1e-6f) {
            return Vector3(vector)
        }
        val c = cos(angle)
        val s = sin(angle)
        val term1 = Vector3(vector).scl(c)
        val term2 = Vector3(axis).crs(vector).scl(s)
        val term3 = Vector3(axis).scl(axis.dot(vector) * (1f - c))
        return term1.add(term2).add(term3)
    }

    private fun projectPointToPlane(point: Vector3, planePoint: Vector3, planeNormal: Vector3): Vector3 {
        val signedDistance = Vector3(point).sub(planePoint).dot(planeNormal)
        return Vector3(point).mulAdd(planeNormal, -signedDistance)
    }

    private fun rejectOnAxis(vector: Vector3, axis: Vector3): Vector3 {
        return Vector3(vector).mulAdd(axis, -vector.dot(axis))
    }

    private fun localVerticalAxis(group: GroupScene.GroupNode): Vector3 {
        val up = group.vectorToLocal(Vector3(0f, 1f, 0f))
        if (up.len2() <= epsilonSq) {
            return Vector3(0f, 1f, 0f)
        }
        return up.nor()
    }

    private fun defaultHeading(up: Vector3): Vector3 {
        val candidate = Vector3(up).crs(1f, 0f, 0f)
        if (candidate.len2() > epsilonSq) {
            return candidate.nor()
        }
        return Vector3(up).crs(0f, 0f, 1f).nor()
    }

    private fun appendArcPoints(arcPoints: List<Vector3>) {
        if (arcPoints.size <= 1 || pathLocal.isEmpty()) {
            return
        }
        for (i in 1 until arcPoints.size) {
            val next = arcPoints[i]
            if (pathLocal.last().dst(next) > epsilon) {
                pathLocal.add(Vector3(next))
            }
        }
    }

    private fun arcPointsFromCenter(start: Vector3, end: Vector3, center: Vector3): List<Vector3> {
        val startVec = Vector3(start).sub(center)
        val endVec = Vector3(end).sub(center)
        val radius = startVec.len()
        if (radius <= epsilon) {
            return emptyList()
        }
        val normal = Vector3(startVec).crs(endVec)
        if (normal.len2() <= epsilonSq) {
            return if (start.dst2(end) <= epsilonSq) emptyList() else listOf(start, end)
        }
        val u = startVec.nor()
        val w = normal.nor()
        val v = Vector3(w).crs(u).nor()
        val endAngle = atan2(endVec.dot(v).toDouble(), endVec.dot(u).toDouble()).toFloat()
        val delta = normalizeAngle(endAngle)
        if (abs(delta) <= epsilon) {
            return listOf(start, end)
        }
        val steps = stepsForArc(radius, delta)
        return buildArcPoints(center, u, v, radius, delta, steps)
    }

    private fun arcPointsThrough(start: Vector3, mid: Vector3, end: Vector3): List<Vector3> {
        val ab = Vector3(mid).sub(start)
        val ac = Vector3(end).sub(start)
        val normal = Vector3(ab).crs(ac)
        if (normal.len2() <= epsilonSq) {
            return if (start.dst2(end) <= epsilonSq) emptyList() else listOf(start, end)
        }
        val u = Vector3(ab).nor()
        val w = normal.nor()
        val v = Vector3(w).crs(u).nor()
        val bx = ab.len()
        val cx = ac.dot(u)
        val cy = ac.dot(v)
        val d = 2f * (bx * cy)
        if (abs(d) <= epsilon) {
            return listOf(start, end)
        }
        val ux = (bx * bx * cy) / d
        val uy = (cx * cx + cy * cy - bx * bx) / (2f * cy)
        val center = Vector3(start).add(Vector3(u).scl(ux)).add(Vector3(v).scl(uy))
        val startVec = Vector3(start).sub(center)
        val midVec = Vector3(mid).sub(center)
        val endVec = Vector3(end).sub(center)
        val radius = startVec.len()
        if (radius <= epsilon) {
            return emptyList()
        }
        val u2 = startVec.nor()
        val v2 = Vector3(w).crs(u2).nor()
        val midAngle = atan2(midVec.dot(v2).toDouble(), midVec.dot(u2).toDouble()).toFloat()
        val endAngle = atan2(endVec.dot(v2).toDouble(), endVec.dot(u2).toDouble()).toFloat()
        var delta = normalizeAngle(endAngle)
        val midNorm = normalizeAngle(midAngle)
        if (!isBetweenCCW(0f, midNorm, delta)) {
            delta -= (Math.PI * 2.0).toFloat()
        }
        if (abs(delta) <= epsilon) {
            return listOf(start, end)
        }
        val steps = stepsForArc(radius, delta)
        return buildArcPoints(center, u2, v2, radius, delta, steps)
    }

    private fun buildArcPoints(center: Vector3, u: Vector3, v: Vector3, radius: Float, delta: Float, steps: Int): List<Vector3> {
        val pts = mutableListOf(Vector3(center).add(Vector3(u).scl(radius)))
        val step = delta / steps
        for (i in 1..steps) {
            val angle = step * i
            val c = cos(angle)
            val s = sin(angle)
            val point = Vector3(center)
                .add(Vector3(u).scl(radius * c))
                .add(Vector3(v).scl(radius * s))
            pts.add(point)
        }
        return pts
    }

    private fun normalizeAngle(angle: Float): Float {
        val twoPi = (Math.PI * 2.0).toFloat()
        val a = angle % twoPi
        return if (a < 0f) a + twoPi else a
    }

    private fun stepsForArc(radius: Float, delta: Float): Int {
        val maxLen = settings.arcMaxLength
        if (maxLen <= 0.0001f) {
            return 16
        }
        val arcLength = abs(delta) * radius
        val steps = ceil(arcLength / maxLen).toInt()
        return maxOf(1, minOf(steps, 512))
    }

    private fun isBetweenCCW(start: Float, mid: Float, end: Float): Boolean {
        var endVal = end
        var midVal = mid
        if (endVal < start) {
            endVal += (Math.PI * 2.0).toFloat()
        }
        if (midVal < start) {
            midVal += (Math.PI * 2.0).toFloat()
        }
        return midVal >= start && midVal <= endVal
    }

    private fun refreshSourceSegments() {
        sourceSegments.clear()
        val selected = scene.activeGroup().lineStore.getSelected()
        selected.forEach { segment ->
            if (segment.start.dst2(segment.end) > epsilonSq) {
                sourceSegments.add(SourceSegment(Vector3(segment.start), Vector3(segment.end)))
            }
        }
    }

    private fun dedupePath(points: List<Vector3>): List<Vector3> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val result = mutableListOf(Vector3(points.first()))
        for (i in 1 until points.size) {
            if (points[i].dst2(result.last()) > epsilonSq) {
                result.add(Vector3(points[i]))
            }
        }
        return result
    }

    private fun clearTransient() {
        sourceSegments.clear()
        clearPath()
    }

    private fun clearPath() {
        pathLocal.clear()
        hasHover = false
        arcMode = ArcMode.LINE
        arcCenterLocal = null
        arcPass1Local = null
    }

    private fun isFinite(vec: Vector3): Boolean {
        return vec.x.isFinite() && vec.y.isFinite() && vec.z.isFinite()
    }

    private enum class ArcMode { LINE, CENTER, THREE }
}

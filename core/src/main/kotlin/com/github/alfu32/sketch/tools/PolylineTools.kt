package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import kotlin.math.abs
import kotlin.math.ceil

class PolylineToolInternal(
    private val scene: GroupScene,
    private val settings: PolylineSettings
) : Tool {
    override val id: ToolId = ToolId.POLYLINE
    override val message: String = "Click to start a polyline. A/C/L switch arc modes."

    private val pointsLocal = mutableListOf<Vector3>()
    private val hoverLocal = Vector3()
    private var hasHover = false
    private var arcMode = ArcMode.LINE
    private var arcCenterLocal: Vector3? = null
    private var arcPass1Local: Vector3? = null
    private var closed = false
    private val closeDistance = 0.15f
    private val epsilon = 1e-4f

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
        status.message = "Canceled."
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        val group = scene.activeGroup()
        val hit = group.toLocal(world)
        if (pointsLocal.isEmpty()) {
            pointsLocal.add(Vector3(hit))
            return true
        }
        if (isClosing(hit)) {
            closed = true
            finalizePolyline(group)
            return true
        }
        when (arcMode) {
            ArcMode.CENTER -> {
                if (arcCenterLocal == null) {
                    arcCenterLocal = Vector3(hit)
                } else {
                    val arcPoints = arcPointsFromCenter(pointsLocal.last(), hit, arcCenterLocal!!)
                    appendArcPoints(arcPoints)
                    arcCenterLocal = null
                }
                return true
            }
            ArcMode.THREE -> {
                if (arcPass1Local == null) {
                    arcPass1Local = Vector3(hit)
                } else {
                    val arcPoints = arcPointsThrough(pointsLocal.last(), arcPass1Local!!, hit)
                    appendArcPoints(arcPoints)
                    arcPass1Local = null
                }
                return true
            }
            ArcMode.LINE -> {
                pointsLocal.add(Vector3(hit))
                return true
            }
        }
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hoverLocal.set(scene.activeGroup().toLocal(world))
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.A -> {
                arcMode = ArcMode.THREE
                arcPass1Local = null
                arcCenterLocal = null
                return true
            }
            Input.Keys.C -> {
                arcMode = ArcMode.CENTER
                arcCenterLocal = null
                arcPass1Local = null
                return true
            }
            Input.Keys.L -> {
                arcMode = ArcMode.LINE
                arcCenterLocal = null
                arcPass1Local = null
                return true
            }
            Input.Keys.ENTER -> {
                finalizePolyline(scene.activeGroup())
                return true
            }
            Input.Keys.BACKSPACE -> {
                if (arcMode == ArcMode.CENTER && arcCenterLocal != null) {
                    arcCenterLocal = null
                    return true
                }
                if (arcMode == ArcMode.THREE && arcPass1Local != null) {
                    arcPass1Local = null
                    return true
                }
                if (pointsLocal.isNotEmpty()) {
                    pointsLocal.removeAt(pointsLocal.size - 1)
                    closed = false
                    return true
                }
            }
        }
        return false
    }

    override fun render(renderer: ShapeRenderer) {
        if (pointsLocal.isEmpty()) {
            return
        }
        val group = scene.activeGroup()
        renderer.color = Color(0.95f, 0.75f, 0.25f, 1f)
        for (i in 0 until pointsLocal.size - 1) {
            renderer.line(group.toWorld(pointsLocal[i]), group.toWorld(pointsLocal[i + 1]))
        }
        if (hasHover) {
            val preview = when {
                arcMode == ArcMode.CENTER && arcCenterLocal != null ->
                    arcPointsFromCenter(pointsLocal.last(), hoverLocal, arcCenterLocal!!)
                arcMode == ArcMode.THREE && arcPass1Local != null ->
                    arcPointsThrough(pointsLocal.last(), arcPass1Local!!, hoverLocal)
                else -> null
            }
            if (preview != null && preview.size > 1) {
                for (i in 0 until preview.size - 1) {
                    renderer.line(group.toWorld(preview[i]), group.toWorld(preview[i + 1]))
                }
            } else {
                renderer.line(group.toWorld(pointsLocal.last()), group.toWorld(hoverLocal))
            }
        }
    }

    private fun clear() {
        pointsLocal.clear()
        arcMode = ArcMode.LINE
        arcCenterLocal = null
        arcPass1Local = null
        closed = false
        hasHover = false
    }

    private fun isClosing(candidate: Vector3): Boolean {
        if (pointsLocal.size < 3) {
            return false
        }
        return pointsLocal.first().dst(candidate) <= closeDistance
    }

    private fun finalizePolyline(group: GroupScene.GroupNode) {
        if (pointsLocal.size < 2) {
            clear()
            return
        }
        addSegments(group, pointsLocal, closed)
        if (closed && pointsLocal.size >= 3) {
            val tris = triangulate(pointsLocal)
            tris.forEach { tri ->
                group.faceStore.addTriangle(tri[0], tri[1], tri[2])
            }
        }
        group.lineStore.cleanupJts()
        clear()
    }

    private fun addSegments(group: GroupScene.GroupNode, pts: List<Vector3>, close: Boolean) {
        for (i in 0 until pts.size - 1) {
            if (pts[i].dst2(pts[i + 1]) <= epsilon * epsilon) {
                continue
            }
            group.lineStore.addSegment(pts[i], pts[i + 1], autoCleanup = false)
        }
        if (close && pts.size > 1 && pts.last().dst2(pts.first()) > 0f) {
            group.lineStore.addSegment(pts.last(), pts.first(), autoCleanup = false)
        }
    }

    private fun appendArcPoints(arcPoints: List<Vector3>) {
        if (arcPoints.size <= 1) {
            return
        }
        for (i in 1 until arcPoints.size) {
            val next = arcPoints[i]
            if (pointsLocal.last().dst(next) > epsilon) {
                pointsLocal.add(Vector3(next))
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
        if (normal.len2() <= epsilon * epsilon) {
            if (start.dst(end) <= epsilon) {
                return emptyList()
            }
            return listOf(start, end)
        }
        val u = startVec.nor()
        val w = normal.nor()
        val v = Vector3(w).crs(u).nor()
        val endAngle = kotlin.math.atan2(endVec.dot(v), endVec.dot(u)).toFloat()
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
        if (normal.len2() <= epsilon * epsilon) {
            if (start.dst(end) <= epsilon) {
                return emptyList()
            }
            return listOf(start, end)
        }
        val u = Vector3(ab).nor()
        val w = Vector3(normal).nor()
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
        val midAngle = kotlin.math.atan2(midVec.dot(v2), midVec.dot(u2)).toFloat()
        val endAngle = kotlin.math.atan2(endVec.dot(v2), endVec.dot(u2)).toFloat()
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
            val cos = kotlin.math.cos(angle.toDouble()).toFloat()
            val sin = kotlin.math.sin(angle.toDouble()).toFloat()
            val point = Vector3(center)
                .add(Vector3(u).scl(radius * cos))
                .add(Vector3(v).scl(radius * sin))
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

    private fun triangulate(pts: List<Vector3>): List<List<Vector3>> {
        val normal = computeNormal(pts)
        val projected = projectTo2D(pts, normal)
        var indices = pts.indices.toList()
        if (signedArea(projected) < 0f) {
            indices = indices.reversed()
        }
        val triangles = mutableListOf<List<Vector3>>()
        var guard = 0
        while (indices.size > 2 && guard < 10000) {
            guard++
            var earFound = false
            for (i in indices.indices) {
                val prev = indices[(i - 1 + indices.size) % indices.size]
                val curr = indices[i]
                val next = indices[(i + 1) % indices.size]
                if (!isConvex(projected[prev], projected[curr], projected[next])) {
                    continue
                }
                if (containsPoint(projected, indices, prev, curr, next)) {
                    continue
                }
                triangles.add(listOf(pts[prev], pts[curr], pts[next]))
                indices = indices.toMutableList().also { it.removeAt(i) }
                earFound = true
                break
            }
            if (!earFound) {
                break
            }
        }
        return triangles
    }

    private fun computeNormal(pts: List<Vector3>): Vector3 {
        var nx = 0f
        var ny = 0f
        var nz = 0f
        for (i in pts.indices) {
            val current = pts[i]
            val next = pts[(i + 1) % pts.size]
            nx += (current.y - next.y) * (current.z + next.z)
            ny += (current.z - next.z) * (current.x + next.x)
            nz += (current.x - next.x) * (current.y + next.y)
        }
        val normal = Vector3(nx, ny, nz)
        return if (normal.len2() <= 1e-6f) Vector3(0f, 1f, 0f) else normal.nor()
    }

    private fun projectTo2D(pts: List<Vector3>, normal: Vector3): List<FloatArray> {
        val ax = abs(normal.x)
        val ay = abs(normal.y)
        val az = abs(normal.z)
        return when {
            ax >= ay && ax >= az -> pts.map { floatArrayOf(it.y, it.z) }
            ay >= ax && ay >= az -> pts.map { floatArrayOf(it.x, it.z) }
            else -> pts.map { floatArrayOf(it.x, it.y) }
        }
    }

    private fun signedArea(pts: List<FloatArray>): Float {
        var area = 0f
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            area += a[0] * b[1] - b[0] * a[1]
        }
        return area * 0.5f
    }

    private fun isConvex(a: FloatArray, b: FloatArray, c: FloatArray): Boolean {
        val cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        return cross > 0f
    }

    private fun containsPoint(pts: List<FloatArray>, indices: List<Int>, prev: Int, curr: Int, next: Int): Boolean {
        val a = pts[prev]
        val b = pts[curr]
        val c = pts[next]
        for (idx in indices) {
            if (idx == prev || idx == curr || idx == next) {
                continue
            }
            if (pointInTriangle(pts[idx], a, b, c)) {
                return true
            }
        }
        return false
    }

    private fun pointInTriangle(p: FloatArray, a: FloatArray, b: FloatArray, c: FloatArray): Boolean {
        val area = abs(triangleArea(a, b, c))
        val a1 = abs(triangleArea(p, b, c))
        val a2 = abs(triangleArea(a, p, c))
        val a3 = abs(triangleArea(a, b, p))
        return abs(area - (a1 + a2 + a3)) < 0.0001f
    }

    private fun triangleArea(a: FloatArray, b: FloatArray, c: FloatArray): Float {
        return (a[0] * (b[1] - c[1]) +
            b[0] * (c[1] - a[1]) +
            c[0] * (a[1] - b[1])) / 2f
    }

    private enum class ArcMode { LINE, CENTER, THREE }
}

class DoubleLineToolInternal(
    private val scene: GroupScene,
    private val settings: PolylineSettings
) : Tool {
    override val id: ToolId = ToolId.DOUBLE_LINE
    override val message: String = "Click to start a double line. A/C/L switch arc modes."

    private val pointsLocal = mutableListOf<Vector3>()
    private val hoverLocal = Vector3()
    private var hasHover = false
    private var arcMode = ArcMode.LINE
    private var arcCenterLocal: Vector3? = null
    private var arcPass1Local: Vector3? = null
    private var closed = false
    private val closeDistance = 0.15f
    private val epsilon = 1e-4f

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
        status.message = "Canceled."
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        val group = scene.activeGroup()
        val hit = group.toLocal(world)
        if (pointsLocal.isEmpty()) {
            pointsLocal.add(Vector3(hit))
            return true
        }
        if (isClosing(hit)) {
            closed = true
            finalizeDoubleLine(group)
            return true
        }
        when (arcMode) {
            ArcMode.CENTER -> {
                if (arcCenterLocal == null) {
                    arcCenterLocal = Vector3(hit)
                } else {
                    val arcPoints = arcPointsFromCenter(pointsLocal.last(), hit, arcCenterLocal!!)
                    appendArcPoints(arcPoints)
                    arcCenterLocal = null
                }
                return true
            }
            ArcMode.THREE -> {
                if (arcPass1Local == null) {
                    arcPass1Local = Vector3(hit)
                } else {
                    val arcPoints = arcPointsThrough(pointsLocal.last(), arcPass1Local!!, hit)
                    appendArcPoints(arcPoints)
                    arcPass1Local = null
                }
                return true
            }
            ArcMode.LINE -> {
                pointsLocal.add(Vector3(hit))
                return true
            }
        }
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hoverLocal.set(scene.activeGroup().toLocal(world))
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.A -> {
                arcMode = ArcMode.THREE
                arcPass1Local = null
                arcCenterLocal = null
                return true
            }
            Input.Keys.C -> {
                arcMode = ArcMode.CENTER
                arcCenterLocal = null
                arcPass1Local = null
                return true
            }
            Input.Keys.L -> {
                arcMode = ArcMode.LINE
                arcCenterLocal = null
                arcPass1Local = null
                return true
            }
            Input.Keys.ENTER -> {
                finalizeDoubleLine(scene.activeGroup())
                return true
            }
            Input.Keys.BACKSPACE -> {
                if (arcMode == ArcMode.CENTER && arcCenterLocal != null) {
                    arcCenterLocal = null
                    return true
                }
                if (arcMode == ArcMode.THREE && arcPass1Local != null) {
                    arcPass1Local = null
                    return true
                }
                if (pointsLocal.isNotEmpty()) {
                    pointsLocal.removeAt(pointsLocal.size - 1)
                    closed = false
                    return true
                }
            }
        }
        return false
    }

    override fun render(renderer: ShapeRenderer) {
        if (pointsLocal.isEmpty()) {
            return
        }
        val group = scene.activeGroup()
        renderer.color = Color(0.35f, 0.75f, 0.95f, 1f)
        val renderPoints = pointsLocal.toMutableList()
        if (hasHover) {
            val preview = when {
                arcMode == ArcMode.CENTER && arcCenterLocal != null ->
                    arcPointsFromCenter(pointsLocal.last(), hoverLocal, arcCenterLocal!!)
                arcMode == ArcMode.THREE && arcPass1Local != null ->
                    arcPointsThrough(pointsLocal.last(), arcPass1Local!!, hoverLocal)
                else -> listOf(pointsLocal.last(), hoverLocal)
            }
            if (preview.size > 1) {
                renderPoints.addAll(preview.drop(1))
            }
        }
        if (renderPoints.size >= 2) {
            val left = buildOffsetPath(renderPoints, offsetA(), closed)
            val right = buildOffsetPath(renderPoints, offsetB(), closed)
            drawPolylinePreview(renderer, group, left, closed)
            drawPolylinePreview(renderer, group, right, closed)
        }
    }

    private fun clear() {
        pointsLocal.clear()
        arcMode = ArcMode.LINE
        arcCenterLocal = null
        arcPass1Local = null
        closed = false
        hasHover = false
    }

    private fun isClosing(candidate: Vector3): Boolean {
        if (pointsLocal.size < 2) {
            return false
        }
        return pointsLocal.first().dst(candidate) <= closeDistance
    }

    private fun finalizeDoubleLine(group: GroupScene.GroupNode) {
        if (pointsLocal.size < 2) {
            clear()
            return
        }
        val left = buildOffsetPath(pointsLocal, offsetA(), closed)
        val right = buildOffsetPath(pointsLocal, offsetB(), closed)
        val loop = buildLoop(left, right)
        addSegments(group, loop, true)
        addStripFaces(group, left, right, closed)
        group.lineStore.cleanupJts()
        clear()
    }

    private fun addSegments(group: GroupScene.GroupNode, pts: List<Vector3>, close: Boolean) {
        for (i in 0 until pts.size - 1) {
            if (pts[i].dst2(pts[i + 1]) <= epsilon * epsilon) {
                continue
            }
            group.lineStore.addSegment(pts[i], pts[i + 1], autoCleanup = false)
        }
        if (close && pts.size > 1 && pts.last().dst2(pts.first()) > 0f) {
            group.lineStore.addSegment(pts.last(), pts.first(), autoCleanup = false)
        }
    }

    private fun addStripFaces(group: GroupScene.GroupNode, left: List<Vector3>, right: List<Vector3>, close: Boolean) {
        val count = minOf(left.size, right.size)
        if (count < 2) {
            return
        }
        val limit = if (close) count else count - 1
        for (i in 0 until limit) {
            val next = (i + 1) % count
            val a = left[i]
            val b = left[next]
            val c = right[next]
            val d = right[i]
            group.faceStore.addTriangle(a, b, c)
            group.faceStore.addTriangle(a, c, d)
        }
    }

    private fun buildLoop(left: List<Vector3>, right: List<Vector3>): List<Vector3> {
        if (left.isEmpty() || right.isEmpty()) {
            return emptyList()
        }
        val loop = mutableListOf<Vector3>()
        loop.addAll(left)
        loop.addAll(right.asReversed())
        return loop
    }

    private fun drawPolylinePreview(renderer: ShapeRenderer, group: GroupScene.GroupNode, pts: List<Vector3>, close: Boolean) {
        if (pts.size < 2) {
            return
        }
        for (i in 0 until pts.size - 1) {
            renderer.line(group.toWorld(pts[i]), group.toWorld(pts[i + 1]))
        }
        if (close) {
            renderer.line(group.toWorld(pts.last()), group.toWorld(pts.first()))
        }
    }

    private fun appendArcPoints(arcPoints: List<Vector3>) {
        if (arcPoints.size <= 1) {
            return
        }
        for (i in 1 until arcPoints.size) {
            val next = arcPoints[i]
            if (pointsLocal.last().dst(next) > epsilon) {
                pointsLocal.add(Vector3(next))
            }
        }
    }

    private fun offsetA(): Float {
        val size = settings.doubleLineSize.coerceAtLeast(0f)
        return settings.doubleLineOffset - (size * 0.5f)
    }

    private fun offsetB(): Float {
        val size = settings.doubleLineSize.coerceAtLeast(0f)
        return settings.doubleLineOffset + (size * 0.5f)
    }

    private fun buildOffsetPath(base: List<Vector3>, offset: Float, isClosed: Boolean): List<Vector3> {
        val result = mutableListOf<Vector3>()
        val count = base.size
        for (i in 0 until count) {
            val curr = base[i]
            val prev = if (i > 0) base[i - 1] else if (isClosed) base[count - 1] else null
            val next = if (i < count - 1) base[i + 1] else if (isClosed) base[0] else null
            val perpPrev = prev?.let { segmentPerp(it, curr) }
            val perpNext = next?.let { segmentPerp(curr, it) }
            var offsetDir: Vector3? = null
            if (perpPrev != null && perpNext != null) {
                val miter = Vector3(perpPrev).add(perpNext)
                if (miter.len2() > epsilon * epsilon) {
                    miter.nor()
                    val denom = miter.dot(perpPrev)
                    if (abs(denom) > 0.01f) {
                        val scale = offset / denom
                        val maxScale = abs(offset) * 10f + 1f
                        if (abs(scale) <= maxScale) {
                            offsetDir = Vector3(miter).scl(scale)
                        }
                    }
                }
            }
            if (offsetDir == null) {
                val fallback = perpPrev ?: perpNext
                offsetDir = if (fallback != null) Vector3(fallback).scl(offset) else Vector3()
            }
            if (!isFinite(offsetDir)) {
                offsetDir.set(0f, 0f, 0f)
            }
            result.add(Vector3(curr).add(offsetDir))
        }
        return result
    }

    private fun isFinite(vec: Vector3): Boolean {
        return vec.x.isFinite() && vec.y.isFinite() && vec.z.isFinite()
    }

    private fun segmentPerp(start: Vector3, end: Vector3): Vector3? {
        val dir = Vector3(end).sub(start)
        if (dir.len2() <= epsilon * epsilon) {
            return null
        }
        dir.nor()
        val perp = Vector3(dir).crs(0f, 1f, 0f)
        if (perp.len2() <= epsilon * epsilon) {
            return null
        }
        return perp.nor()
    }

    private fun arcPointsFromCenter(start: Vector3, end: Vector3, center: Vector3): List<Vector3> {
        val startVec = Vector3(start).sub(center)
        val endVec = Vector3(end).sub(center)
        val radius = startVec.len()
        if (radius <= epsilon) {
            return emptyList()
        }
        val normal = Vector3(startVec).crs(endVec)
        if (normal.len2() <= epsilon * epsilon) {
            if (start.dst(end) <= epsilon) {
                return emptyList()
            }
            return listOf(start, end)
        }
        val u = startVec.nor()
        val w = normal.nor()
        val v = Vector3(w).crs(u).nor()
        val endAngle = kotlin.math.atan2(endVec.dot(v), endVec.dot(u)).toFloat()
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
        if (normal.len2() <= epsilon * epsilon) {
            if (start.dst(end) <= epsilon) {
                return emptyList()
            }
            return listOf(start, end)
        }
        val u = Vector3(ab).nor()
        val w = Vector3(normal).nor()
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
        val midAngle = kotlin.math.atan2(midVec.dot(v2), midVec.dot(u2)).toFloat()
        val endAngle = kotlin.math.atan2(endVec.dot(v2), endVec.dot(u2)).toFloat()
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
            val cos = kotlin.math.cos(angle.toDouble()).toFloat()
            val sin = kotlin.math.sin(angle.toDouble()).toFloat()
            val point = Vector3(center)
                .add(Vector3(u).scl(radius * cos))
                .add(Vector3(v).scl(radius * sin))
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

    private enum class ArcMode { LINE, CENTER, THREE }
}

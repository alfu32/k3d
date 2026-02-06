package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class LineOffsetTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.LINE_OFFSET
    override val message: String = "Select edges, then pick reference point."

    private var referenceWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false
    private val epsilonSq = 1e-6f
    private val keyEpsilon = 1e-3f

    override fun onEnter(status: StatusModel) {
        status.message = "Select edges, then pick reference point."
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
        val selected = group.lineStore.getSelected().toList()
        if (selected.isEmpty()) {
            status.message = "No edges selected."
            return true
        }
        if (referenceWorld == null) {
            referenceWorld = Vector3(world)
            status.message = "Pick target point for offset."
            return true
        }
        val deltaWorld = Vector3(world).sub(referenceWorld)
        if (deltaWorld.len2() <= epsilonSq) {
            clearTransient()
            status.message = "Offset is too small."
            return true
        }
        val components = collectComponents(selected)
        var addedSegments = 0
        var addedFaces = 0
        val lineStore = group.lineStore
        lineStore.withChangeSuppressed {
            components.forEach { component ->
                val ordered = orderSegments(component)
                val points = ordered.points
                if (points.size < 2) {
                    return@forEach
                }
                val (offsetPoints, offset) = offsetPathFor(component, points, ordered.closed, deltaWorld)
                if (offsetPoints.isEmpty()) {
                    return@forEach
                }
                for (i in 0 until points.size - 1) {
                    lineStore.addSegment(offsetPoints[i], offsetPoints[i + 1], autoCleanup = false)
                    addedSegments++
                    addedFaces += addStripFaces(group, points[i], points[i + 1], offsetPoints[i], offsetPoints[i + 1], offset)
                }
                if (ordered.closed) {
                    lineStore.addSegment(offsetPoints.last(), offsetPoints.first(), autoCleanup = false)
                    addedSegments++
                    addedFaces += addStripFaces(group, points.last(), points.first(), offsetPoints.last(), offsetPoints.first(), offset)
                }
            }
        }
        lineStore.cleanup()
        status.message = "Offset copy | edges $addedSegments faces $addedFaces"
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        if (!hasHover) {
            return
        }
        val start = referenceWorld ?: return
        renderer.color = Color(0.35f, 0.75f, 0.95f, 1f)
        renderer.line(start.x, start.y, start.z, hover.x, hover.y, hover.z)
    }

    private fun addStripFaces(
        group: GroupScene.GroupNode,
        a: Vector3,
        b: Vector3,
        a2: Vector3,
        b2: Vector3,
        offset: Float
    ): Int {
        val normal = Vector3(b).sub(a).crs(Vector3(a2).sub(a))
        if (normal.len2() <= epsilonSq) {
            return 0
        }
        if (offset >= 0f) {
            group.faceStore.addTriangle(Vector3(a), Vector3(b), Vector3(b2))
            group.faceStore.addTriangle(Vector3(a), Vector3(b2), Vector3(a2))
        } else {
            group.faceStore.addTriangle(Vector3(a), Vector3(b2), Vector3(b))
            group.faceStore.addTriangle(Vector3(a), Vector3(a2), Vector3(b2))
        }
        return 2
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private data class OrderedLine(val points: List<Vector3>, val closed: Boolean)

    private fun collectComponents(segments: List<DraftLineStore.Segment>): List<List<DraftLineStore.Segment>> {
        if (segments.isEmpty()) {
            return emptyList()
        }
        val endpointMap = mutableMapOf<VertexKey, MutableList<DraftLineStore.Segment>>()
        segments.forEach { segment ->
            val a = vertexKey(segment.start)
            val b = vertexKey(segment.end)
            endpointMap.getOrPut(a) { mutableListOf() }.add(segment)
            endpointMap.getOrPut(b) { mutableListOf() }.add(segment)
        }
        val result = mutableListOf<List<DraftLineStore.Segment>>()
        val visited = mutableSetOf<DraftLineStore.Segment>()
        segments.forEach { start ->
            if (visited.contains(start)) {
                return@forEach
            }
            val queue = ArrayDeque<DraftLineStore.Segment>()
            val component = mutableListOf<DraftLineStore.Segment>()
            queue.add(start)
            visited.add(start)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                component.add(current)
                val a = vertexKey(current.start)
                val b = vertexKey(current.end)
                val neighbors = endpointMap[a].orEmpty() + endpointMap[b].orEmpty()
                neighbors.forEach { neighbor ->
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor)
                        queue.add(neighbor)
                    }
                }
            }
            result.add(component)
        }
        return result
    }

    private fun orderSegments(segments: List<DraftLineStore.Segment>): OrderedLine {
        val keyToPoint = mutableMapOf<VertexKey, Vector3>()
        val endpointMap = mutableMapOf<VertexKey, MutableList<DraftLineStore.Segment>>()
        val segmentKeys = mutableMapOf<DraftLineStore.Segment, Pair<VertexKey, VertexKey>>()
        segments.forEach { segment ->
            val a = vertexKey(segment.start).also { keyToPoint.putIfAbsent(it, Vector3(segment.start)) }
            val b = vertexKey(segment.end).also { keyToPoint.putIfAbsent(it, Vector3(segment.end)) }
            endpointMap.getOrPut(a) { mutableListOf() }.add(segment)
            endpointMap.getOrPut(b) { mutableListOf() }.add(segment)
            segmentKeys[segment] = Pair(a, b)
        }
        val endpoints = endpointMap.filterValues { it.size == 1 }.keys
        val startKey = endpoints.firstOrNull() ?: segmentKeys[segments.first()]!!.first
        val points = mutableListOf<Vector3>()
        points.add(Vector3(keyToPoint[startKey] ?: segments.first().start))
        val used = mutableSetOf<DraftLineStore.Segment>()
        var current = startKey
        while (true) {
            val candidates = endpointMap[current].orEmpty().filter { it !in used }
            if (candidates.isEmpty()) {
                break
            }
            val nextSeg = candidates.first()
            used.add(nextSeg)
            val (a, b) = segmentKeys[nextSeg]!!
            val nextKey = if (a == current) b else a
            points.add(Vector3(keyToPoint[nextKey] ?: if (a == current) nextSeg.end else nextSeg.start))
            current = nextKey
            if (current == startKey) {
                break
            }
        }
        var closed = current == startKey && points.size > 2
        if (closed && points.first().dst2(points.last()) <= keyEpsilon * keyEpsilon) {
            points.removeAt(points.lastIndex)
        }
        if (points.size < 2) {
            closed = false
        }
        return OrderedLine(points, closed)
    }

    private fun offsetPathFor(
        component: List<DraftLineStore.Segment>,
        points: List<Vector3>,
        isClosed: Boolean,
        deltaWorld: Vector3
    ): Pair<List<Vector3>, Float> {
        val group = scene.activeGroup()
        val deltaLocal = group.vectorToLocal(deltaWorld)
        val baseDir = firstDirection(component) ?: return Pair(emptyList(), 0f)
        val planeNormal = Vector3(baseDir).crs(deltaLocal)
        val plane = if (planeNormal.len2() > epsilonSq) {
            planeNormal.nor()
        } else {
            fallbackPlaneNormal(baseDir)
        }
        val perp = Vector3(plane).crs(baseDir).nor()
        val offset = deltaLocal.dot(perp)
        if (kotlin.math.abs(offset) <= 1e-6f) {
            return Pair(emptyList(), 0f)
        }
        return Pair(buildOffsetPath(points, offset, plane, isClosed), offset)
    }

    private fun firstDirection(segments: List<DraftLineStore.Segment>): Vector3? {
        segments.forEach { segment ->
            val dir = Vector3(segment.end).sub(segment.start)
            if (dir.len2() > epsilonSq) {
                return dir.nor()
            }
        }
        return null
    }

    private fun fallbackPlaneNormal(dir: Vector3): Vector3 {
        val up = Vector3(0f, 1f, 0f)
        val normal = Vector3(dir).crs(up)
        if (normal.len2() > epsilonSq) {
            return normal.nor()
        }
        val right = Vector3(1f, 0f, 0f)
        val alt = Vector3(dir).crs(right)
        return if (alt.len2() > epsilonSq) alt.nor() else Vector3(0f, 0f, 1f)
    }

    private fun buildOffsetPath(base: List<Vector3>, offset: Float, planeNormal: Vector3, isClosed: Boolean): List<Vector3> {
        val result = mutableListOf<Vector3>()
        val count = base.size
        for (i in 0 until count) {
            val curr = base[i]
            val prev = if (i > 0) base[i - 1] else if (isClosed) base[count - 1] else null
            val next = if (i < count - 1) base[i + 1] else if (isClosed) base[0] else null
            val perpPrev = prev?.let { segmentPerp(it, curr, planeNormal) }
            val perpNext = next?.let { segmentPerp(curr, it, planeNormal) }
            var offsetDir: Vector3? = null
            if (perpPrev != null && perpNext != null) {
                val miter = Vector3(perpPrev).add(perpNext)
                if (miter.len2() > epsilonSq) {
                    miter.nor()
                    val denom = miter.dot(perpPrev)
                    if (kotlin.math.abs(denom) > 0.01f) {
                        val scale = offset / denom
                        val maxScale = kotlin.math.abs(offset) * 10f + 1f
                        if (kotlin.math.abs(scale) <= maxScale) {
                            offsetDir = Vector3(miter).scl(scale)
                        }
                    }
                }
            }
            if (offsetDir == null) {
                val fallback = perpPrev ?: perpNext
                offsetDir = if (fallback != null) Vector3(fallback).scl(offset) else Vector3()
            }
            result.add(Vector3(curr).add(offsetDir))
        }
        return result
    }

    private fun segmentPerp(start: Vector3, end: Vector3, planeNormal: Vector3): Vector3? {
        val dir = Vector3(end).sub(start)
        if (dir.len2() <= epsilonSq) {
            return null
        }
        dir.nor()
        val perp = Vector3(planeNormal).crs(dir)
        if (perp.len2() <= epsilonSq) {
            return null
        }
        return perp.nor()
    }

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(quant(point.x), quant(point.y), quant(point.z))
    }

    private fun quant(value: Float): Int = kotlin.math.round(value / keyEpsilon).toInt()

    private fun clearTransient() {
        referenceWorld = null
        hasHover = false
    }
}

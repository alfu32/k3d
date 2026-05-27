package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.round

class RevolveTool(
    private val scene: GroupScene,
    private val segmentsProvider: () -> Int
) : Tool {
    override val id: ToolId = ToolId.REVOLVE
    override val message: String = "Select connected profile lines, then pick revolve axis origin."

    private var axisOriginWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false
    private val keyEpsilon = 1e-3f

    override fun onEnter(status: StatusModel) {
        val selected = scene.activeGroup().lineStore.getSelected()
        status.message = if (selected.isEmpty()) {
            "Select connected profile lines first."
        } else {
            "Pick revolve axis origin. Selected profile lines: ${selected.size}."
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
        if (axisOriginWorld == null) {
            axisOriginWorld = Vector3(world)
            status.message = "Pick revolve axis direction point."
            return true
        }

        val origin = axisOriginWorld ?: return false
        val result = commitRevolve(origin, Vector3(world))
        clearTransient()
        status.message = result
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val origin = axisOriginWorld ?: return
        if (!hasHover) {
            return
        }
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, origin, 0.18f)
        renderer.color = ToolFeedbackColors.TERTIARY
        drawCross(renderer, hover, 0.18f)
        renderer.line(origin.x, origin.y, origin.z, hover.x, hover.y, hover.z)
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val origin = axisOriginWorld ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(Vector3(origin), Vector3(hover))
    }

    private fun commitRevolve(axisOriginWorld: Vector3, axisDirectionWorld: Vector3): String {
        val group = scene.activeGroup()
        val selected = group.lineStore.getSelected().toList()
        if (selected.isEmpty()) {
            return "Revolve canceled: select connected profile lines first."
        }
        val origin = group.toLocal(axisOriginWorld)
        val directionPoint = group.toLocal(axisDirectionWorld)
        val axis = Vector3(directionPoint).sub(origin)
        if (axis.len2() <= 1e-6f) {
            return "Revolve canceled: axis direction is too short."
        }
        val axisUnit = axis.nor()
        val chains = orderedChains(selected).filter { it.points.size >= 2 }
        if (chains.isEmpty()) {
            return "Revolve canceled: selected profile lines could not be ordered."
        }
        val steps = segmentsProvider().coerceAtLeast(3)
        val color = Color(scene.defaultFaceColor)
        var faces = 0

        group.faceStore.withChangeSuppressed {
            chains.forEach { chain ->
                faces += addRevolveFaces(group, chain.points, chain.closed, origin, axisUnit, steps, color)
            }
        }
        group.faceStore.notifyExternalChange()
        return if (faces > 0) {
            "Revolved ${chains.size} profile chain(s) into $faces face(s)."
        } else {
            "Revolve created no faces."
        }
    }

    private fun addRevolveFaces(
        group: GroupScene.GroupNode,
        points: List<Vector3>,
        closedProfile: Boolean,
        origin: Vector3,
        axisUnit: Vector3,
        steps: Int,
        color: Color
    ): Int {
        var faces = 0
        val rings = ArrayList<List<Vector3>>(steps)
        for (step in 0 until steps) {
            val angle = MathUtils.PI2 * step.toFloat() / steps.toFloat()
            rings += points.map { point -> rotateAroundAxis(point, origin, axisUnit, angle) }
        }
        val edgeCount = if (closedProfile) points.size else points.size - 1
        for (step in 0 until steps) {
            val nextStep = (step + 1) % steps
            for (edge in 0 until edgeCount) {
                val nextEdge = (edge + 1) % points.size
                val a = rings[step][edge]
                val b = rings[step][nextEdge]
                val c = rings[nextStep][nextEdge]
                val d = rings[nextStep][edge]
                if (!degenerateTriangle(a, b, c)) {
                    group.faceStore.addTriangle(a, b, c, color)
                    faces++
                }
                if (!degenerateTriangle(a, c, d)) {
                    group.faceStore.addTriangle(a, c, d, color)
                    faces++
                }
            }
        }
        return faces
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)
    private data class SegmentKeys(val start: VertexKey, val end: VertexKey)
    private data class Chain(val points: List<Vector3>, val closed: Boolean)

    private fun orderedChains(segments: List<DraftLineStore.Segment>): List<Chain> {
        val keyToPoint = linkedMapOf<VertexKey, Vector3>()
        val segmentKeys = linkedMapOf<DraftLineStore.Segment, SegmentKeys>()
        val adjacency = linkedMapOf<VertexKey, MutableList<DraftLineStore.Segment>>()

        segments.forEach { segment ->
            val start = vertexKey(segment.start).also { keyToPoint.putIfAbsent(it, Vector3(segment.start)) }
            val end = vertexKey(segment.end).also { keyToPoint.putIfAbsent(it, Vector3(segment.end)) }
            segmentKeys[segment] = SegmentKeys(start, end)
            adjacency.getOrPut(start) { mutableListOf() }.add(segment)
            adjacency.getOrPut(end) { mutableListOf() }.add(segment)
        }

        val visited = mutableSetOf<DraftLineStore.Segment>()
        val chains = mutableListOf<Chain>()
        val orderedStarts = segments.sortedBy { segment ->
            val keys = segmentKeys[segment]
            val startDegree = keys?.let { adjacency[it.start]?.size ?: 0 } ?: 0
            val endDegree = keys?.let { adjacency[it.end]?.size ?: 0 } ?: 0
            minOf(startDegree, endDegree)
        }

        orderedStarts.forEach { seed ->
            if (seed in visited) {
                return@forEach
            }
            val keys = segmentKeys[seed] ?: return@forEach
            val startKey = chooseChainStart(keys, adjacency)
            val points = mutableListOf(Vector3(keyToPoint[startKey] ?: seed.start))
            var current = startKey
            var closed = false
            while (true) {
                val nextSegment = adjacency[current].orEmpty().firstOrNull { it !in visited } ?: break
                visited.add(nextSegment)
                val nextKeys = segmentKeys[nextSegment] ?: break
                val nextKey = if (nextKeys.start == current) nextKeys.end else nextKeys.start
                points += Vector3(keyToPoint[nextKey] ?: nextSegment.end)
                current = nextKey
                if (current == startKey) {
                    closed = true
                    break
                }
            }
            val cleaned = removeDuplicateClosingPoint(points)
            if (cleaned.size >= 2) {
                chains += Chain(cleaned, closed)
            }
        }
        return chains
    }

    private fun chooseChainStart(
        keys: SegmentKeys,
        adjacency: Map<VertexKey, List<DraftLineStore.Segment>>
    ): VertexKey {
        val startDegree = adjacency[keys.start]?.size ?: 0
        val endDegree = adjacency[keys.end]?.size ?: 0
        return if (startDegree <= endDegree) keys.start else keys.end
    }

    private fun removeDuplicateClosingPoint(points: List<Vector3>): List<Vector3> {
        if (points.size <= 1) {
            return points
        }
        return if (vertexKey(points.first()) == vertexKey(points.last())) points.dropLast(1) else points
    }

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(
            round(point.x / keyEpsilon).toInt(),
            round(point.y / keyEpsilon).toInt(),
            round(point.z / keyEpsilon).toInt()
        )
    }

    private fun rotateAroundAxis(point: Vector3, origin: Vector3, axisUnit: Vector3, angleRad: Float): Vector3 {
        val relative = Vector3(point).sub(origin)
        val cos = MathUtils.cos(angleRad)
        val sin = MathUtils.sin(angleRad)
        val parallel = Vector3(axisUnit).scl(axisUnit.dot(relative))
        val perpendicular = Vector3(relative).sub(parallel)
        val cross = Vector3(axisUnit).crs(perpendicular)
        return Vector3(origin).add(parallel).mulAdd(perpendicular, cos).mulAdd(cross, sin)
    }

    private fun degenerateTriangle(a: Vector3, b: Vector3, c: Vector3): Boolean {
        return Vector3(b).sub(a).crs(Vector3(c).sub(a)).len2() <= 1e-8f
    }

    private fun drawCross(renderer: ShapeRenderer, point: Vector3, size: Float) {
        renderer.line(point.x - size, point.y, point.z, point.x + size, point.y, point.z)
        renderer.line(point.x, point.y - size, point.z, point.x, point.y + size, point.z)
        renderer.line(point.x, point.y, point.z - size, point.x, point.y, point.z + size)
    }

    private fun clearTransient() {
        axisOriginWorld = null
        hasHover = false
    }
}

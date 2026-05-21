package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class PolylineMeshTool(
    private val scene: GroupScene,
    private val done: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.POLYLINE_MESH
    override val message: String = "Select connected polyline segments, then fill them with faces."

    private val keyEpsilon = 1e-3f

    override fun onEnter(status: StatusModel) {
        status.message = "Polyline mesh..."
        val group = scene.activeGroup()
        val selected = group.lineStore.getSelected().toList()
        if (selected.isEmpty()) {
            status.message = "Select polyline segments first."
            done()
            return
        }

        val chains = orderedChains(selected).filter { it.points.size >= 3 }
        if (chains.isEmpty()) {
            status.message = "Need at least three connected polyline points."
            done()
            return
        }

        val before = group.faceStore.getTriangles().size
        chains.forEach { chain ->
            group.faceStore.addPolygon(chain.points)
        }
        val added = group.faceStore.getTriangles().size - before
        status.message = if (added > 0) {
            "Polyline mesh created $added face(s) from ${chains.size} chain(s)."
        } else {
            "Polyline mesh did not create faces."
        }
        done()
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        return button == Input.Buttons.LEFT
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)
    private data class SegmentKeys(val start: VertexKey, val end: VertexKey)
    private data class Chain(val points: List<Vector3>, val closed: Boolean)

    private fun orderedChains(segments: List<DraftLineStore.Segment>): List<Chain> {
        val keyToPoint = linkedMapOf<VertexKey, Vector3>()
        val segmentKeys = linkedMapOf<DraftLineStore.Segment, SegmentKeys>()
        val adjacency = linkedMapOf<VertexKey, MutableList<DraftLineStore.Segment>>()

        segments.forEach { segment ->
            val a = vertexKey(segment.start).also { keyToPoint.putIfAbsent(it, Vector3(segment.start)) }
            val b = vertexKey(segment.end).also { keyToPoint.putIfAbsent(it, Vector3(segment.end)) }
            segmentKeys[segment] = SegmentKeys(a, b)
            adjacency.getOrPut(a) { mutableListOf() }.add(segment)
            adjacency.getOrPut(b) { mutableListOf() }.add(segment)
        }

        val visited = mutableSetOf<DraftLineStore.Segment>()
        val chains = mutableListOf<Chain>()
        val orderedStarts = segments.sortedBy { segment ->
            val keys = segmentKeys[segment]
            val degreeA = keys?.let { adjacency[it.start]?.size ?: 0 } ?: 0
            val degreeB = keys?.let { adjacency[it.end]?.size ?: 0 } ?: 0
            minOf(degreeA, degreeB)
        }

        orderedStarts.forEach { seed ->
            if (seed in visited) {
                return@forEach
            }
            val keys = segmentKeys[seed] ?: return@forEach
            val startKey = chooseChainStart(keys, adjacency)
            val points = mutableListOf<Vector3>()
            points.add(Vector3(keyToPoint[startKey] ?: seed.start))
            var current = startKey
            var closed = false

            while (true) {
                val nextSegment = adjacency[current].orEmpty().firstOrNull { it !in visited } ?: break
                visited.add(nextSegment)
                val nextKeys = segmentKeys[nextSegment] ?: break
                val nextKey = if (nextKeys.start == current) nextKeys.end else nextKeys.start
                points.add(Vector3(keyToPoint[nextKey] ?: nextSegment.end))
                current = nextKey
                if (current == startKey) {
                    closed = true
                    break
                }
            }

            val cleaned = removeDuplicateClosingPoint(points)
            if (cleaned.size >= 3) {
                chains.add(Chain(cleaned, closed))
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
        if (points.size < 2) {
            return points
        }
        val result = points.map { Vector3(it) }.toMutableList()
        if (result.first().dst2(result.last()) <= keyEpsilon * keyEpsilon) {
            result.removeAt(result.lastIndex)
        }
        return result
    }

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(
            kotlin.math.round(point.x / keyEpsilon).toInt(),
            kotlin.math.round(point.y / keyEpsilon).toInt(),
            kotlin.math.round(point.z / keyEpsilon).toInt()
        )
    }
}

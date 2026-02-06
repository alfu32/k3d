package com.github.alfu32.sketch.tools

import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class CutHolesTool(
    private val scene: GroupScene,
    private val done: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.CUT_HOLES
    override val message: String = "Cuts selected faces by selected edges."

    private val keyEpsilon = 1e-3f
    private val keyEpsilonSq = keyEpsilon * keyEpsilon

    override fun onEnter(status: StatusModel) {
        status.message = "Cutting holes..."
        val group = scene.activeGroup()
        val faces = group.faceStore.getSelected().toList()
        val segments = group.lineStore.getSelected().toList()
        println("[CutHoles] faces=${faces.size} segments=${segments.size}")
        faces.take(5).forEachIndexed { index, tri ->
            println("[CutHoles] face[$index]=(${tri.a}) (${tri.b}) (${tri.c})")
        }
        segments.take(5).forEachIndexed { index, seg ->
            println("[CutHoles] seg[$index]=(${seg.start}) -> (${seg.end})")
        }
        if (faces.isEmpty() || segments.isEmpty()) {
            status.message = "Select faces and edges first."
            done()
            return
        }
        val loops = orderedLoops(segments).filter { it.closed && it.points.size >= 3 }
        val loopPoints = loops.flatMap { it.points }
        val endpointPoints = segments.flatMap { listOf(it.start, it.end) }
        val splits = group.faceStore.cutSelectedByPolyline(loopPoints + endpointPoints, segments)
        var removed = 0
        if (loops.isNotEmpty()) {
            val base = faces.first()
            val normalWorld = Vector3(base.b).sub(base.a).crs(Vector3(base.c).sub(base.a))
            if (normalWorld.len2() > 1e-6f) {
                val basis = planeBasisFromNormal(normalWorld)
                val loopPolygons = loops.map { loop ->
                    loop.points.map { point ->
                        val rel = Vector3(point).sub(base.a)
                        floatArrayOf(rel.dot(basis.axisU), rel.dot(basis.axisV))
                    }
                }
                val toRemove = group.faceStore.getSelected().filter { tri ->
                    val center = Vector3(tri.a).add(tri.b).add(tri.c).scl(1f / 3f)
                    val rel = Vector3(center).sub(base.a)
                    val p = floatArrayOf(rel.dot(basis.axisU), rel.dot(basis.axisV))
                    loopPolygons.any { poly -> pointInPolygon(p, poly) }
                }
                removed = group.faceStore.deleteTriangles(toRemove)
            }
        }
        status.message = "Cut faces | splits $splits removed $removed"
        done()
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private data class OrderedLoop(val points: List<Vector3>, val closed: Boolean)

    private fun orderedLoops(segments: List<DraftLineStore.Segment>): List<OrderedLoop> {
        if (segments.isEmpty()) {
            return emptyList()
        }
        val endpointMap = mutableMapOf<VertexKey, MutableList<DraftLineStore.Segment>>()
        val segmentKeys = mutableMapOf<DraftLineStore.Segment, Pair<VertexKey, VertexKey>>()
        val keyToPoint = mutableMapOf<VertexKey, Vector3>()
        segments.forEach { segment ->
            val a = vertexKey(segment.start).also { keyToPoint.putIfAbsent(it, Vector3(segment.start)) }
            val b = vertexKey(segment.end).also { keyToPoint.putIfAbsent(it, Vector3(segment.end)) }
            endpointMap.getOrPut(a) { mutableListOf() }.add(segment)
            endpointMap.getOrPut(b) { mutableListOf() }.add(segment)
            segmentKeys[segment] = Pair(a, b)
        }
        val loops = mutableListOf<OrderedLoop>()
        val visited = mutableSetOf<DraftLineStore.Segment>()
        segments.forEach { start ->
            if (visited.contains(start)) {
                return@forEach
            }
            val keys = segmentKeys[start] ?: return@forEach
            val startKey = keys.first
            val points = mutableListOf<Vector3>()
            points.add(Vector3(keyToPoint[startKey] ?: start.start))
            var current = startKey
            var closed = false
            while (true) {
                val candidates = endpointMap[current].orEmpty().filter { it !in visited }
                if (candidates.isEmpty()) {
                    break
                }
                val nextSeg = candidates.first()
                visited.add(nextSeg)
                val (a, b) = segmentKeys[nextSeg]!!
                val nextKey = if (a == current) b else a
                points.add(Vector3(keyToPoint[nextKey] ?: if (a == current) nextSeg.end else nextSeg.start))
                current = nextKey
                if (current == startKey) {
                    closed = true
                    break
                }
            }
            if (closed && points.size > 1 && points.first().dst2(points.last()) <= keyEpsilonSq) {
                points.removeAt(points.lastIndex)
            }
            loops.add(OrderedLoop(points, closed))
        }
        return loops
    }

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(quant(point.x), quant(point.y), quant(point.z))
    }

    private fun quant(value: Float): Int = kotlin.math.round(value / keyEpsilon).toInt()

    private fun pointInPolygon(point: FloatArray, polygon: List<FloatArray>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i][0]
            val yi = polygon[i][1]
            val xj = polygon[j][0]
            val yj = polygon[j][1]
            val intersect = ((yi > point[1]) != (yj > point[1])) &&
                (point[0] < (xj - xi) * (point[1] - yi) / (yj - yi + 1e-9f) + xi)
            if (intersect) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}

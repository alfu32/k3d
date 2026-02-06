package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class FaceOutlineTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.FACE_OUTLINE
    override val message: String = "Click to outline selected faces."

    private val epsilon = 1e-4f

    override fun onEnter(status: StatusModel) {
        status.message = "Click to outline selected faces."
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        val group = scene.activeGroup()
        val selected = group.faceStore.getSelected().toList()
        if (selected.isEmpty()) {
            status.message = "No faces selected."
            return true
        }
        val edges = findBoundaryEdges(selected)
        if (edges.isEmpty()) {
            status.message = "No outline found."
            return true
        }
        val lineStore = group.lineStore
        lineStore.withChangeSuppressed {
            edges.forEach { (a, b) ->
                lineStore.addSegment(Vector3(a), Vector3(b), autoCleanup = false)
            }
        }
        lineStore.cleanup()
        status.message = "Outlined ${edges.size} edge(s)."
        return true
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private data class EdgeKey(val a: VertexKey, val b: VertexKey)

    private fun findBoundaryEdges(triangles: List<DraftFaceStore.Triangle>): List<Pair<Vector3, Vector3>> {
        val keyToPoint = mutableMapOf<VertexKey, Vector3>()
        val edgeCount = mutableMapOf<EdgeKey, Int>()
        triangles.forEach { tri ->
            val a = vertexKey(tri.a).also { keyToPoint.putIfAbsent(it, Vector3(tri.a)) }
            val b = vertexKey(tri.b).also { keyToPoint.putIfAbsent(it, Vector3(tri.b)) }
            val c = vertexKey(tri.c).also { keyToPoint.putIfAbsent(it, Vector3(tri.c)) }
            listOf(Pair(a, b), Pair(b, c), Pair(c, a)).forEach { (u, v) ->
                val edgeKey = if (compareKeys(u, v) <= 0) EdgeKey(u, v) else EdgeKey(v, u)
                edgeCount[edgeKey] = (edgeCount[edgeKey] ?: 0) + 1
            }
        }
        val boundaryEdges = edgeCount.filterValues { it == 1 }.keys
        val result = mutableListOf<Pair<Vector3, Vector3>>()
        boundaryEdges.forEach { edge ->
            val a = keyToPoint[edge.a]
            val b = keyToPoint[edge.b]
            if (a != null && b != null) {
                result.add(Pair(a, b))
            }
        }
        return result
    }

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(quant(point.x), quant(point.y), quant(point.z))
    }

    private fun quant(value: Float): Int = kotlin.math.round(value / epsilon).toInt()

    private fun compareKeys(a: VertexKey, b: VertexKey): Int {
        if (a.x != b.x) return a.x.compareTo(b.x)
        if (a.y != b.y) return a.y.compareTo(b.y)
        return a.z.compareTo(b.z)
    }
}

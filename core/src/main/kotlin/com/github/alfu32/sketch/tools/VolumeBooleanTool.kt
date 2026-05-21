package com.github.alfu32.sketch.tools

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import kotlin.math.abs

class VolumeBooleanTool(
    override val id: ToolId,
    private val scene: GroupScene,
    private val operation: Operation,
    private val done: () -> Unit
) : Tool {
    enum class Operation { UNION, INTERSECTION, SUBTRACTION }

    override val message: String = "Select two connected face sets before running ${id.displayName}."

    private val keyEpsilon = 1e-3f
    private val rayEpsilon = 1e-5f
    private val rayDirection = Vector3(0.8713f, 0.3571f, 0.3359f).nor()

    override fun onEnter(status: StatusModel) {
        val group = scene.activeGroup()
        val faceStore = group.faceStore
        val selected = faceStore.getSelected().toList()
        if (selected.size < 2) {
            status.message = "Select faces from two volumes first."
            done()
            return
        }

        val components = connectedComponents(selected).sortedByDescending { it.size }
        if (components.size < 2) {
            status.message = "Selection must contain two connected face sets."
            done()
            return
        }

        val rawSetA = components[0]
        val rawSetB = components[1]
        val intersectionSegments = MeshIntersectionMath.dedupeSegments(intersectionSegments(rawSetA, rawSetB), keyEpsilon)
        var cuts = 0
        intersectionSegments.forEach { segment ->
            cuts += faceStore.cutBySegmentInPlane(segment.start, segment.end)
        }

        val operationSelection = faceStore.getSelected().toList()
        val operationComponents = connectedComponents(operationSelection).sortedByDescending { it.size }
        val setA = operationComponents.getOrNull(0) ?: rawSetA
        val setB = operationComponents.getOrNull(1) ?: rawSetB
        val result = buildResult(faceStore, setA, setB)
        if (result.isEmpty()) {
            status.message = "${id.displayName} produced no faces. Check that the two selected face sets overlap."
            done()
            return
        }

        faceStore.withChangeSuppressed {
            faceStore.deleteTriangles(operationSelection)
            result.forEach { item ->
                val added = faceStore.appendTriangleRaw(item.a, item.b, item.c, item.color)
                faceStore.addSelection(added)
            }
        }
        faceStore.notifyExternalChange()
        status.message = "${id.displayName} created ${result.size} face(s). Intersection cuts: $cuts."
        done()
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)
    private data class FaceItem(val a: Vector3, val b: Vector3, val c: Vector3, val color: Color)

    private fun buildResult(
        faceStore: DraftFaceStore,
        setA: List<DraftFaceStore.Triangle>,
        setB: List<DraftFaceStore.Triangle>
    ): List<FaceItem> {
        val out = mutableListOf<FaceItem>()
        when (operation) {
            Operation.UNION -> {
                setA.filterNot { centroidInside(it, setB) }.forEach { out.add(faceItem(faceStore, it)) }
                setB.filterNot { centroidInside(it, setA) }.forEach { out.add(faceItem(faceStore, it)) }
                if (out.isEmpty()) {
                    setA.forEach { out.add(faceItem(faceStore, it)) }
                    setB.forEach { out.add(faceItem(faceStore, it)) }
                }
            }
            Operation.INTERSECTION -> {
                setA.filter { centroidInside(it, setB) }.forEach { out.add(faceItem(faceStore, it)) }
                setB.filter { centroidInside(it, setA) }.forEach { out.add(faceItem(faceStore, it)) }
            }
            Operation.SUBTRACTION -> {
                setA.filterNot { centroidInside(it, setB) }.forEach { out.add(faceItem(faceStore, it)) }
                setB.filter { centroidInside(it, setA) }.forEach { out.add(faceItem(faceStore, it, flip = true)) }
            }
        }
        return out
    }

    private fun intersectionSegments(
        setA: List<DraftFaceStore.Triangle>,
        setB: List<DraftFaceStore.Triangle>
    ): List<MeshIntersectionMath.Segment3> {
        val segments = mutableListOf<MeshIntersectionMath.Segment3>()
        setA.forEach { a ->
            val triA = MeshIntersectionMath.Triangle3(a.a, a.b, a.c)
            setB.forEach { b ->
                val triB = MeshIntersectionMath.Triangle3(b.a, b.b, b.c)
                MeshIntersectionMath.intersectTriangles(triA, triB, keyEpsilon)?.let(segments::add)
            }
        }
        return segments
    }

    private fun faceItem(faceStore: DraftFaceStore, triangle: DraftFaceStore.Triangle, flip: Boolean = false): FaceItem {
        val color = Color(faceStore.colorFor(triangle))
        return if (flip) {
            FaceItem(Vector3(triangle.a), Vector3(triangle.c), Vector3(triangle.b), color)
        } else {
            FaceItem(Vector3(triangle.a), Vector3(triangle.b), Vector3(triangle.c), color)
        }
    }

    private fun centroidInside(triangle: DraftFaceStore.Triangle, volume: List<DraftFaceStore.Triangle>): Boolean {
        val centroid = Vector3(triangle.a).add(triangle.b).add(triangle.c).scl(1f / 3f)
        return isInsideByRayCast(centroid, volume)
    }

    private fun isInsideByRayCast(point: Vector3, volume: List<DraftFaceStore.Triangle>): Boolean {
        var hits = 0
        volume.forEach { triangle ->
            val t = rayTriangleIntersection(point, rayDirection, triangle)
            if (t != null && t > rayEpsilon) {
                hits++
            }
        }
        return hits % 2 == 1
    }

    private fun rayTriangleIntersection(
        origin: Vector3,
        direction: Vector3,
        triangle: DraftFaceStore.Triangle
    ): Float? {
        val edge1 = Vector3(triangle.b).sub(triangle.a)
        val edge2 = Vector3(triangle.c).sub(triangle.a)
        val h = Vector3(direction).crs(edge2)
        val det = edge1.dot(h)
        if (abs(det) <= rayEpsilon) {
            return null
        }
        val invDet = 1f / det
        val s = Vector3(origin).sub(triangle.a)
        val u = invDet * s.dot(h)
        if (u < -rayEpsilon || u > 1f + rayEpsilon) {
            return null
        }
        val q = Vector3(s).crs(edge1)
        val v = invDet * direction.dot(q)
        if (v < -rayEpsilon || u + v > 1f + rayEpsilon) {
            return null
        }
        val t = invDet * edge2.dot(q)
        return if (t > rayEpsilon) t else null
    }

    private fun connectedComponents(triangles: List<DraftFaceStore.Triangle>): List<List<DraftFaceStore.Triangle>> {
        val vertexToFaces = linkedMapOf<VertexKey, MutableList<DraftFaceStore.Triangle>>()
        triangles.forEach { triangle ->
            listOf(triangle.a, triangle.b, triangle.c).forEach { point ->
                vertexToFaces.getOrPut(vertexKey(point)) { mutableListOf() }.add(triangle)
            }
        }
        val visited = mutableSetOf<DraftFaceStore.Triangle>()
        val components = mutableListOf<List<DraftFaceStore.Triangle>>()
        triangles.forEach { seed ->
            if (seed in visited) {
                return@forEach
            }
            val component = mutableListOf<DraftFaceStore.Triangle>()
            val stack = ArrayDeque<DraftFaceStore.Triangle>()
            stack.add(seed)
            visited.add(seed)
            while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                component.add(current)
                listOf(current.a, current.b, current.c).forEach { point ->
                    vertexToFaces[vertexKey(point)].orEmpty().forEach { neighbor ->
                        if (neighbor !in visited) {
                            visited.add(neighbor)
                            stack.add(neighbor)
                        }
                    }
                }
            }
            components.add(component)
        }
        return components
    }

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(
            kotlin.math.round(point.x / keyEpsilon).toInt(),
            kotlin.math.round(point.y / keyEpsilon).toInt(),
            kotlin.math.round(point.z / keyEpsilon).toInt()
        )
    }
}

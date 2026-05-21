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

    override val message: String = "Select exactly 2 mesh object instances, then run ${id.displayName}."

    private val intersectionEpsilon = 1e-4f
    private val rayEpsilon = 1e-5f
    private val rayDirection = Vector3(0.8713f, 0.3571f, 0.3359f).nor()

    override fun onEnter(status: StatusModel) {
        val selectedObjects = scene.selectedGroups().toList()
        if (selectedObjects.size != 2) {
            status.message = "${id.displayName}: select exactly 2 object instances."
            done()
            return
        }

        val groupA = selectedObjects[0]
        val groupB = selectedObjects[1]
        val targetGroup = groupA.parent
        if (targetGroup == null || groupB.parent != targetGroup) {
            status.message = "${id.displayName}: selected objects must share the same parent context."
            done()
            return
        }
        if (scene.isVoxelGroup(groupA) || scene.isVoxelGroup(groupB)) {
            status.message = "${id.displayName}: mesh object instances only; voxel objects are not supported yet."
            done()
            return
        }

        val facesA = collectWorldFaces(groupA)
        val facesB = collectWorldFaces(groupB)
        if (facesA.isEmpty() || facesB.isEmpty()) {
            status.message = "${id.displayName}: both selected objects must contain mesh faces."
            done()
            return
        }

        val intersectionSegments = MeshIntersectionMath.dedupeSegments(
            intersectionSegments(facesA, facesB),
            intersectionEpsilon
        )
        val preparedA = prepareCutFaces(facesA, intersectionSegments)
        val preparedB = prepareCutFaces(facesB, intersectionSegments)
        val result = buildResult(preparedA.faces, preparedB.faces)
        if (result.isEmpty()) {
            status.message = "${id.displayName}: no result faces. The objects may not overlap or their face normals may be inconsistent."
            done()
            return
        }

        scene.clearGroupSelection()
        selectedObjects.forEach { scene.addGroupSelection(it) }
        val removedObjects = scene.deleteSelectedGroups()

        targetGroup.lineStore.clearSelection()
        targetGroup.faceStore.clearSelection()
        targetGroup.dimensionStore.clearSelection()
        targetGroup.textStore.clearSelection()
        targetGroup.voxelStore?.clearSelection()

        val added = mutableListOf<DraftFaceStore.Triangle>()
        val faceStore = targetGroup.faceStore
        faceStore.withChangeSuppressed {
            result.forEach { item ->
                val a = targetGroup.toLocal(item.a)
                val b = targetGroup.toLocal(item.b)
                val c = targetGroup.toLocal(item.c)
                added.add(faceStore.appendTriangleRaw(a, b, c, item.color))
            }
        }
        faceStore.notifyExternalChange()
        added.forEach(faceStore::addSelection)

        status.message =
            "${id.displayName}: replaced $removedObjects object(s) with ${added.size} selected exploded mesh face(s). Cuts: A ${preparedA.cuts}, B ${preparedB.cuts}."
        done()
    }

    private data class FaceItem(val a: Vector3, val b: Vector3, val c: Vector3, val color: Color)
    private data class PreparedFaces(val faces: List<FaceItem>, val cuts: Int)

    private fun collectWorldFaces(group: GroupScene.GroupNode): List<FaceItem> {
        return group.faceStore.getTriangles().map { triangle ->
            FaceItem(
                group.toWorld(triangle.a),
                group.toWorld(triangle.b),
                group.toWorld(triangle.c),
                Color(group.faceStore.colorFor(triangle))
            )
        }
    }

    private fun prepareCutFaces(
        faces: List<FaceItem>,
        intersectionSegments: List<MeshIntersectionMath.Segment3>
    ): PreparedFaces {
        if (faces.isEmpty()) {
            return PreparedFaces(emptyList(), 0)
        }
        val store = DraftFaceStore()
        store.withChangeSuppressed {
            faces.forEach { face ->
                store.appendTriangleRaw(face.a, face.b, face.c, face.color)
            }
        }
        store.notifyExternalChange()

        var cuts = 0
        intersectionSegments.forEach { segment ->
            cuts += store.cutBySegmentInPlane(segment.start, segment.end)
        }
        val prepared = store.getTriangles().map { triangle ->
            FaceItem(
                Vector3(triangle.a),
                Vector3(triangle.b),
                Vector3(triangle.c),
                Color(store.colorFor(triangle))
            )
        }
        return PreparedFaces(prepared, cuts)
    }

    private fun buildResult(setA: List<FaceItem>, setB: List<FaceItem>): List<FaceItem> {
        val out = mutableListOf<FaceItem>()
        when (operation) {
            Operation.UNION -> {
                setA.filterNot { centroidInside(it, setB) }.forEach(out::add)
                setB.filterNot { centroidInside(it, setA) }.forEach(out::add)
                if (out.isEmpty()) {
                    out.addAll(setA)
                    out.addAll(setB)
                }
            }
            Operation.INTERSECTION -> {
                setA.filter { centroidInside(it, setB) }.forEach(out::add)
                setB.filter { centroidInside(it, setA) }.forEach(out::add)
            }
            Operation.SUBTRACTION -> {
                setA.filterNot { centroidInside(it, setB) }.forEach(out::add)
                setB.filter { centroidInside(it, setA) }.forEach { out.add(it.flipped()) }
            }
        }
        return out
    }

    private fun intersectionSegments(
        setA: List<FaceItem>,
        setB: List<FaceItem>
    ): List<MeshIntersectionMath.Segment3> {
        val segments = mutableListOf<MeshIntersectionMath.Segment3>()
        setA.forEach { a ->
            val triA = MeshIntersectionMath.Triangle3(a.a, a.b, a.c)
            setB.forEach { b ->
                val triB = MeshIntersectionMath.Triangle3(b.a, b.b, b.c)
                MeshIntersectionMath.intersectTriangles(triA, triB, intersectionEpsilon)?.let(segments::add)
            }
        }
        return segments
    }

    private fun FaceItem.flipped(): FaceItem {
        return FaceItem(Vector3(a), Vector3(c), Vector3(b), Color(color))
    }

    private fun centroidInside(triangle: FaceItem, volume: List<FaceItem>): Boolean {
        val centroid = Vector3(triangle.a).add(triangle.b).add(triangle.c).scl(1f / 3f)
        return isInsideByRayCast(centroid, volume)
    }

    private fun isInsideByRayCast(point: Vector3, volume: List<FaceItem>): Boolean {
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
        triangle: FaceItem
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
}

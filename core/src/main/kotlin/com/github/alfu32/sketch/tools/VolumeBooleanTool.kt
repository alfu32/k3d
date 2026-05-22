package com.github.alfu32.sketch.tools

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
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
    private val cutEpsilon = 1e-3f
    private val planeEpsilon = 1e-3f
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

        val intersectionSegments = intersectionSegments(facesA, facesB)
            .filter { it.start.dst2(it.end) > intersectionEpsilon * intersectionEpsilon }
            .map { MeshIntersectionMath.Segment3(Vector3(it.start), Vector3(it.end)) }
        val preparedA = prepareCutFaces(groupA, intersectionSegments)
        val preparedB = prepareCutFaces(groupB, intersectionSegments)
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
            "${id.displayName}: replaced $removedObjects object(s) with ${added.size} selected exploded mesh face(s). Segments: ${intersectionSegments.size}. Cuts: A ${preparedA.cuts}, B ${preparedB.cuts}. Unresolved: A ${preparedA.unresolved}, B ${preparedB.unresolved}."
        done()
    }

    private data class FaceItem(val a: Vector3, val b: Vector3, val c: Vector3, val color: Color)
    private data class PreparedFaces(val faces: List<FaceItem>, val cuts: Int, val unresolved: Int)

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
        group: GroupScene.GroupNode,
        intersectionSegments: List<MeshIntersectionMath.Segment3>
    ): PreparedFaces {
        val sourceFaces = group.faceStore.getTriangles().toList()
        if (sourceFaces.isEmpty()) {
            return PreparedFaces(emptyList(), 0, 0)
        }
        val store = DraftFaceStore()
        store.withChangeSuppressed {
            sourceFaces.forEach { triangle ->
                store.appendTriangleRaw(
                    triangle.a,
                    triangle.b,
                    triangle.c,
                    Color(group.faceStore.colorFor(triangle))
                )
            }
        }
        store.notifyExternalChange()

        val localSegments = intersectionSegments.map { segment ->
            CutSegment(group.toLocal(segment.start), group.toLocal(segment.end))
        }
        val cutStats = cutUntilConverged(store, localSegments)
        val prepared = store.getTriangles().map { triangle ->
            FaceItem(
                group.toWorld(triangle.a),
                group.toWorld(triangle.b),
                group.toWorld(triangle.c),
                Color(store.colorFor(triangle))
            )
        }
        return PreparedFaces(prepared, cutStats.cuts, cutStats.unresolved)
    }

    private data class CutSegment(val start: Vector3, val end: Vector3)
    private data class TriangleCutTarget(
        val triangle: DraftFaceStore.Triangle,
        val segment: CutSegment
    )
    private data class CutStats(val cuts: Int, val unresolved: Int)
    private data class DeadCutPair(val segmentIndex: Int, val triangleId: String)

    private fun cutUntilConverged(
        store: DraftFaceStore,
        segments: List<CutSegment>
    ): CutStats {
        var totalCuts = 0
        val deadPairs = mutableSetOf<DeadCutPair>()
        var pass = 0
        var operationGuard = 0

        while (pass < 200 && operationGuard < 20_000) {
            pass++
            var changedInPass = false

            segments.forEachIndexed { index, segment ->
                while (operationGuard < 20_000) {
                    val target = store.getTriangles().asSequence().mapNotNull { triangle ->
                        if (DeadCutPair(index, triangle.id) in deadPairs) {
                            null
                        } else {
                            clippedSegmentForTriangle(segment, triangle)?.let { clipped ->
                                TriangleCutTarget(triangle, clipped)
                            }
                        }
                    }.firstOrNull()
                    if (target == null) {
                        break
                    }
                    operationGuard++
                    store.clearSelection()
                    store.addSelection(target.triangle)
                    var cut = store.cutSelectedByPolyline(
                        points = listOf(target.segment.start, target.segment.end),
                        segments = listOf(DraftLineStore.Segment(target.segment.start, target.segment.end))
                    )
                    if (cut <= 0) {
                        cut = fallbackCutSingleTriangle(store, target)
                    }
                    if (cut <= 0) {
                        deadPairs.add(DeadCutPair(index, target.triangle.id))
                        continue
                    }
                    totalCuts += cut
                    changedInPass = true
                }
            }

            if (!changedInPass) {
                break
            }
        }
        store.clearSelection()
        val unresolved = segments.withIndex().sumOf { (_, segment) ->
            store.getTriangles().count { triangle ->
                clippedSegmentForTriangle(segment, triangle) != null
            }
        }
        return CutStats(totalCuts, unresolved)
    }

    private fun fallbackCutSingleTriangle(
        store: DraftFaceStore,
        target: TriangleCutTarget
    ): Int {
        val color = Color(store.colorFor(target.triangle))
        val temp = DraftFaceStore(color)
        temp.appendTriangleRaw(target.triangle.a, target.triangle.b, target.triangle.c, color)
        val cuts = temp.cutBySegmentInPlane(target.segment.start, target.segment.end)
        val result = temp.getTriangles()
        if (cuts <= 0 || result.size <= 1) {
            return 0
        }
        store.deleteTriangles(listOf(target.triangle))
        store.withChangeSuppressed {
            result.forEach { triangle ->
                store.appendTriangleRaw(triangle.a, triangle.b, triangle.c, color)
            }
        }
        store.notifyExternalChange()
        return result.size
    }

    private fun clippedSegmentForTriangle(
        segment: CutSegment,
        triangle: DraftFaceStore.Triangle
    ): CutSegment? {
        if (segment.start.dst2(segment.end) <= cutEpsilon * cutEpsilon) {
            return null
        }
        val normal = Vector3(triangle.b).sub(triangle.a).crs(Vector3(triangle.c).sub(triangle.a))
        if (normal.len2() <= cutEpsilon * cutEpsilon) {
            return null
        }
        normal.nor()
        val d = -normal.dot(triangle.a)
        val ds = normal.dot(segment.start) + d
        val de = normal.dot(segment.end) + d
        if (abs(ds) > planeEpsilon || abs(de) > planeEpsilon) {
            return null
        }

        val basis = planeBasisFromNormal(normal)
        val origin = triangle.a
        val a = to2d(triangle.a, origin, basis)
        val b = to2d(triangle.b, origin, basis)
        val c = to2d(triangle.c, origin, basis)
        val s = to2d(segment.start, origin, basis)
        val e = to2d(segment.end, origin, basis)
        if (s.dst2(e) <= cutEpsilon * cutEpsilon) {
            return null
        }

        val hits = mutableListOf<Vector2>()
        if (pointInTriangle2d(s, a, b, c) || pointOnTriangleBoundary2d(s, a, b, c)) {
            addUnique(hits, s)
        }
        if (pointInTriangle2d(e, a, b, c) || pointOnTriangleBoundary2d(e, a, b, c)) {
            addUnique(hits, e)
        }
        addUnique(hits, segmentIntersection2d(s, e, a, b))
        addUnique(hits, segmentIntersection2d(s, e, b, c))
        addUnique(hits, segmentIntersection2d(s, e, c, a))
        if (hits.size < 2) {
            return null
        }

        val dir = Vector2(e).sub(s)
        val len2 = dir.len2()
        if (len2 <= cutEpsilon * cutEpsilon) {
            return null
        }
        val sorted = hits.sortedBy { point -> Vector2(point).sub(s).dot(dir) / len2 }
        val first = sorted.first()
        val last = sorted.last()
        if (first.dst2(last) <= cutEpsilon * cutEpsilon) {
            return null
        }
        val mid = Vector2(first).add(last).scl(0.5f)
        if (pointOnTriangleBoundary2d(mid, a, b, c)) {
            return null
        }
        if (!pointInTriangle2d(mid, a, b, c)) {
            return null
        }

        val firstT = Vector2(first).sub(s).dot(dir) / len2
        val lastT = Vector2(last).sub(s).dot(dir) / len2
        val startT = kotlin.math.min(firstT, lastT).coerceIn(0f, 1f)
        val endT = kotlin.math.max(firstT, lastT).coerceIn(0f, 1f)
        if (endT - startT <= cutEpsilon) {
            return null
        }
        val segmentVector = Vector3(segment.end).sub(segment.start)
        val clippedStart = Vector3(segment.start).mulAdd(segmentVector, startT)
        val clippedEnd = Vector3(segment.start).mulAdd(segmentVector, endT)
        return if (clippedStart.dst2(clippedEnd) > cutEpsilon * cutEpsilon) {
            CutSegment(clippedStart, clippedEnd)
        } else {
            null
        }
    }

    private fun to2d(point: Vector3, origin: Vector3, basis: PlaneBasis): Vector2 {
        val rel = Vector3(point).sub(origin)
        return Vector2(rel.dot(basis.axisU), rel.dot(basis.axisV))
    }

    private fun addUnique(points: MutableList<Vector2>, point: Vector2?) {
        if (point == null) {
            return
        }
        if (points.none { it.dst2(point) <= cutEpsilon * cutEpsilon }) {
            points.add(Vector2(point))
        }
    }

    private fun pointInTriangle2d(p: Vector2, a: Vector2, b: Vector2, c: Vector2): Boolean {
        val d1 = signedArea2(p, a, b)
        val d2 = signedArea2(p, b, c)
        val d3 = signedArea2(p, c, a)
        val hasNeg = d1 < -cutEpsilon || d2 < -cutEpsilon || d3 < -cutEpsilon
        val hasPos = d1 > cutEpsilon || d2 > cutEpsilon || d3 > cutEpsilon
        return !(hasNeg && hasPos)
    }

    private fun pointOnTriangleBoundary2d(p: Vector2, a: Vector2, b: Vector2, c: Vector2): Boolean {
        return pointOnSegment2d(p, a, b) || pointOnSegment2d(p, b, c) || pointOnSegment2d(p, c, a)
    }

    private fun pointOnSegment2d(p: Vector2, a: Vector2, b: Vector2): Boolean {
        val ab = Vector2(b).sub(a)
        val ap = Vector2(p).sub(a)
        val cross = abs(ab.crs(ap))
        if (cross > cutEpsilon) {
            return false
        }
        val dot = ap.dot(ab)
        if (dot < -cutEpsilon) {
            return false
        }
        return dot <= ab.len2() + cutEpsilon
    }

    private fun segmentCollinearOverlap2d(a: Vector2, b: Vector2, c: Vector2, d: Vector2): Boolean {
        val ab = Vector2(b).sub(a)
        val ac = Vector2(c).sub(a)
        val ad = Vector2(d).sub(a)
        if (abs(ab.crs(ac)) > cutEpsilon || abs(ab.crs(ad)) > cutEpsilon) {
            return false
        }
        val len2 = ab.len2()
        if (len2 <= cutEpsilon * cutEpsilon) {
            return false
        }
        val tc = ac.dot(ab) / len2
        val td = ad.dot(ab) / len2
        val minT = kotlin.math.min(tc, td)
        val maxT = kotlin.math.max(tc, td)
        return maxT >= -cutEpsilon && minT <= 1f + cutEpsilon
    }

    private fun segmentIntersection2d(a: Vector2, b: Vector2, c: Vector2, d: Vector2): Vector2? {
        val r = Vector2(b).sub(a)
        val s = Vector2(d).sub(c)
        val denom = r.crs(s)
        if (abs(denom) <= cutEpsilon) {
            return null
        }
        val ca = Vector2(c).sub(a)
        val t = ca.crs(s) / denom
        val u = ca.crs(r) / denom
        if (t < -cutEpsilon || t > 1f + cutEpsilon || u < -cutEpsilon || u > 1f + cutEpsilon) {
            return null
        }
        return Vector2(a).mulAdd(r, t.coerceIn(0f, 1f))
    }

    private fun signedArea2(p: Vector2, a: Vector2, b: Vector2): Float {
        return (p.x - b.x) * (a.y - b.y) - (a.x - b.x) * (p.y - b.y)
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

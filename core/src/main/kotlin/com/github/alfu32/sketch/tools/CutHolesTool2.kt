package com.github.alfu32.sketch.tools

import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class CutHolesTool2(
    private val scene: GroupScene,
    private val done: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.CUT_HOLES_2
    override val message: String = "Cuts selected faces by selected edges (robust)."

    override fun onEnter(status: StatusModel) {
        status.message = "Cutting holes..."
        val group = scene.activeGroup()
        val faces = group.faceStore.getSelected().toList()
        val segments = group.lineStore.getSelected().toList()
        if (faces.isEmpty() || segments.isEmpty()) {
            status.message = "Select faces and edges first."
            done()
            return
        }

        val polygons = facePolysFromTriangles(faces)
        val segs = segments.map { seg ->
            CutHoles2.Segment3(
                CutHoles2.Vec3(seg.start.x.toDouble(), seg.start.y.toDouble(), seg.start.z.toDouble()),
                CutHoles2.Vec3(seg.end.x.toDouble(), seg.end.y.toDouble(), seg.end.z.toDouble())
            )
        }
        val cutPolys = CutHoles2.cutFaces(polygons, segs, 1e-3)
        val triangles = cutPolys.flatMap { poly ->
            triangulate(poly.verts).map { tri ->
                DraftFaceStore.Triangle(
                    Vector3(tri[0].x.toFloat(), tri[0].y.toFloat(), tri[0].z.toFloat()),
                    Vector3(tri[1].x.toFloat(), tri[1].y.toFloat(), tri[1].z.toFloat()),
                    Vector3(tri[2].x.toFloat(), tri[2].y.toFloat(), tri[2].z.toFloat())
                )
            }
        }

        val store = group.faceStore
        val color = store.colorFor(faces.first())
        val before = store.getTriangles().toSet()
        store.deleteTriangles(faces)
        triangles.forEach { tri ->
            store.addTriangle(tri.a, tri.b, tri.c, color)
        }
        val added = store.getTriangles().filter { it !in before }
        store.clearSelection()
        added.forEach { store.addSelection(it) }
        status.message = "Cut holes 2 | triangles ${triangles.size}"
        done()
    }

    private fun facePolysFromTriangles(tris: List<DraftFaceStore.Triangle>): List<CutHoles2.FacePoly> {
        return tris.map { tri ->
            CutHoles2.FacePoly(
                listOf(
                    CutHoles2.Vec3(tri.a.x.toDouble(), tri.a.y.toDouble(), tri.a.z.toDouble()),
                    CutHoles2.Vec3(tri.b.x.toDouble(), tri.b.y.toDouble(), tri.b.z.toDouble()),
                    CutHoles2.Vec3(tri.c.x.toDouble(), tri.c.y.toDouble(), tri.c.z.toDouble())
                )
            )
        }
    }

    private fun triangulate(pts: List<CutHoles2.Vec3>): List<List<CutHoles2.Vec3>> {
        if (pts.size < 3) return emptyList()
        val normal = computeNormal(pts)
        val projected = projectTo2D(pts, normal)
        var indices = pts.indices.toList()
        if (signedArea(projected) < 0f) {
            indices = indices.reversed()
        }
        val triangles = mutableListOf<List<CutHoles2.Vec3>>()
        var guard = 0
        while (indices.size > 2 && guard < 10000) {
            guard++
            var earFound = false
            for (i in indices.indices) {
                val prev = indices[(i - 1 + indices.size) % indices.size]
                val curr = indices[i]
                val next = indices[(i + 1) % indices.size]
                if (!isConvex(projected[prev], projected[curr], projected[next])) continue
                if (containsPoint(projected, indices, prev, curr, next)) continue
                triangles.add(listOf(pts[prev], pts[curr], pts[next]))
                indices = indices.toMutableList().also { it.removeAt(i) }
                earFound = true
                break
            }
            if (!earFound) break
        }
        return triangles
    }

    private fun computeNormal(pts: List<CutHoles2.Vec3>): Vector3 {
        var nx = 0.0
        var ny = 0.0
        var nz = 0.0
        for (i in pts.indices) {
            val current = pts[i]
            val next = pts[(i + 1) % pts.size]
            nx += (current.y - next.y) * (current.z + next.z)
            ny += (current.z - next.z) * (current.x + next.x)
            nz += (current.x - next.x) * (current.y + next.y)
        }
        val normal = Vector3(nx.toFloat(), ny.toFloat(), nz.toFloat())
        return if (normal.len2() <= 1e-6f) Vector3(0f, 1f, 0f) else normal.nor()
    }

    private fun projectTo2D(pts: List<CutHoles2.Vec3>, normal: Vector3): List<FloatArray> {
        val ax = kotlin.math.abs(normal.x)
        val ay = kotlin.math.abs(normal.y)
        val az = kotlin.math.abs(normal.z)
        return when {
            ax >= ay && ax >= az -> pts.map { floatArrayOf(it.y.toFloat(), it.z.toFloat()) }
            ay >= ax && ay >= az -> pts.map { floatArrayOf(it.x.toFloat(), it.z.toFloat()) }
            else -> pts.map { floatArrayOf(it.x.toFloat(), it.y.toFloat()) }
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
            if (idx == prev || idx == curr || idx == next) continue
            if (pointInTriangle(pts[idx], a, b, c)) return true
        }
        return false
    }

    private fun pointInTriangle(p: FloatArray, a: FloatArray, b: FloatArray, c: FloatArray): Boolean {
        val area = kotlin.math.abs(triangleArea(a, b, c))
        val a1 = kotlin.math.abs(triangleArea(p, b, c))
        val a2 = kotlin.math.abs(triangleArea(a, p, c))
        val a3 = kotlin.math.abs(triangleArea(a, b, p))
        return kotlin.math.abs(area - (a1 + a2 + a3)) < 0.0001f
    }

    private fun triangleArea(a: FloatArray, b: FloatArray, c: FloatArray): Float {
        return (a[0] * (b[1] - c[1]) +
            b[0] * (c[1] - a[1]) +
            c[0] * (a[1] - b[1])) / 2f
    }

}

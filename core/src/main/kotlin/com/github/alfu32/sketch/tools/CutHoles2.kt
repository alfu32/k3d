package com.github.alfu32.sketch.tools

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

object CutHoles2 {
    data class Vec3(val x: Double, val y: Double, val z: Double)
    data class Segment3(val a: Vec3, val b: Vec3)
    data class FacePoly(val verts: List<Vec3>)

    private class Vtx(var x: Double, var y: Double, var z: Double, var key3: Long) {
        var outgoing: HE? = null
        fun toVec3(): Vec3 = Vec3(x, y, z)
    }

    private class HE {
        lateinit var origin: Vtx
        var twin: HE? = null
        var next: HE? = null
        var prev: HE? = null
        lateinit var face: Face
        var edgeRef: EEdge? = null
    }

    private class EEdge {
        var heA: HE? = null
        var heB: HE? = null
    }

    private class Face {
        lateinit var boundary: HE
        lateinit var basis: PlaneBasis
    }

    private class Mesh {
        val faces = mutableListOf<Face>()
        val vtxByKey = mutableMapOf<Long, Vtx>()
        val heByEdgeKey = mutableMapOf<Long, HE>()
    }

    private data class PlaneBasis(val o: Vec3, val u: Vec3, val v: Vec3, val n: Vec3)

    private data class SegHit2(
        val tEdge: Double,
        val tSeg: Double,
        val ix: Double,
        val iy: Double,
        var edgeHe: HE? = null
    )

    private class Node2(val x: Double, val y: Double, val v3: Vtx, val key2: Long) {
        val out = mutableListOf<LocHE>()
    }

    private class LocHE(
        val origin: Node2,
        var isBoundary: Boolean
    ) {
        var twin: LocHE? = null
        var next: LocHE? = null
        var prev: LocHE? = null
        var angle: Double = 0.0
        var used: Boolean = false
    }

    private fun clamp(x: Double, a: Double, b: Double): Double = max(a, min(b, x))

    private fun vadd(a: Vec3, b: Vec3): Vec3 = Vec3(a.x + b.x, a.y + b.y, a.z + b.z)
    private fun vsub(a: Vec3, b: Vec3): Vec3 = Vec3(a.x - b.x, a.y - b.y, a.z - b.z)
    private fun vmul(a: Vec3, s: Double): Vec3 = Vec3(a.x * s, a.y * s, a.z * s)
    private fun dot3(a: Vec3, b: Vec3): Double = a.x * b.x + a.y * b.y + a.z * b.z
    private fun cross3(a: Vec3, b: Vec3): Vec3 = Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )
    private fun norm3(a: Vec3): Double = sqrt(dot3(a, a))
    private fun normalize3(a: Vec3, eps: Double): Vec3 {
        val n = norm3(a)
        return if (n <= eps) Vec3(0.0, 0.0, 0.0) else vmul(a, 1.0 / n)
    }

    private fun q1(x: Double, q: Double): Long = round(x / q).toLong()
    private fun hashMix64(x: Long): Long {
        var z = x + 0x9E3779B97F4A7C15uL.toLong()
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return z xor (z ushr 31)
    }
    private fun key3(p: Vec3, eps: Double): Long {
        val kx = q1(p.x, eps)
        val ky = q1(p.y, eps)
        val kz = q1(p.z, eps)
        var h = 1469598103934665603uL.toLong()
        h = hashMix64(h xor kx)
        h = hashMix64(h xor ky)
        h = hashMix64(h xor kz)
        return h
    }
    private fun key2(x: Double, y: Double, eps: Double): Long {
        val kx = q1(x, eps)
        val ky = q1(y, eps)
        var h = 1099511628211uL.toLong()
        h = hashMix64(h xor kx)
        h = hashMix64(h xor ky)
        return h
    }
    private fun directedEdgeKey(a: Long, b: Long): Long {
        var h = 0x84222325CBF29CE4uL.toLong()
        h = hashMix64(h xor a)
        h = hashMix64(h xor b)
        return h
    }

    private fun computeFaceBasis(verts: List<Vec3>, eps: Double): PlaneBasis {
        var nx = 0.0
        var ny = 0.0
        var nz = 0.0
        val n = verts.size
        for (i in 0 until n) {
            val a = verts[i]
            val b = verts[(i + 1) % n]
            nx += (a.y - b.y) * (a.z + b.z)
            ny += (a.z - b.z) * (a.x + b.x)
            nz += (a.x - b.x) * (a.y + b.y)
        }
        val normal = normalize3(Vec3(nx, ny, nz), eps)
        var cx = 0.0
        var cy = 0.0
        var cz = 0.0
        verts.forEach { v -> cx += v.x; cy += v.y; cz += v.z }
        val o = Vec3(cx / n, cy / n, cz / n)
        var ref = if (abs(normal.x) < 0.7) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        var u = normalize3(cross3(ref, normal), eps)
        if (norm3(u) <= eps) {
            ref = Vec3(0.0, 0.0, 1.0)
            u = normalize3(cross3(ref, normal), eps)
        }
        val v = cross3(normal, u)
        return PlaneBasis(o, u, v, normal)
    }

    private fun signedDistanceToPlane(p: Vec3, b: PlaneBasis): Double = dot3(vsub(p, b.o), b.n)

    private fun project2(p: Vec3, b: PlaneBasis): DoubleArray {
        val d = vsub(p, b.o)
        val x = dot3(d, b.u)
        val y = dot3(d, b.v)
        return doubleArrayOf(x, y)
    }

    private fun unproject2(x: Double, y: Double, b: PlaneBasis): Vec3 {
        return vadd(b.o, vadd(vmul(b.u, x), vmul(b.v, y)))
    }

    private fun segIntersect2(
        ax: Double, ay: Double, bx: Double, by: Double,
        px: Double, py: Double, qx: Double, qy: Double,
        eps: Double
    ): SegHit2? {
        val rdx = bx - ax
        val rdy = by - ay
        val sdx = qx - px
        val sdy = qy - py
        val denom = rdx * sdy - rdy * sdx
        val tol = eps * eps * 10.0
        if (abs(denom) <= tol) return null
        val apx = px - ax
        val apy = py - ay
        var t = (apx * sdy - apy * sdx) / denom
        var u = (apx * rdy - apy * rdx) / denom
        if (t < -eps || t > 1.0 + eps || u < -eps || u > 1.0 + eps) return null
        t = clamp(t, 0.0, 1.0)
        u = clamp(u, 0.0, 1.0)
        val ix = ax + t * rdx
        val iy = ay + t * rdy
        return SegHit2(t, u, ix, iy)
    }

    private fun pointInPolygon2(px: Double, py: Double, poly: List<DoubleArray>, eps: Double): Boolean {
        var inside = false
        val n = poly.size
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[i][0]; val yi = poly[i][1]
            val xj = poly[j][0]; val yj = poly[j][1]
            val intersect = (yi > py) != (yj > py) &&
                (px < (xj - xi) * (py - yi) / (yj - yi + 0.0) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun getOrCreateVtx(m: Mesh, p: Vec3, eps: Double): Vtx {
        val k = key3(p, eps)
        val v = m.vtxByKey[k]
        if (v != null) return v
        val created = Vtx(p.x, p.y, p.z, k)
        m.vtxByKey[k] = created
        return created
    }

    private fun buildHalfEdgeMesh(inputFaces: List<FacePoly>, eps: Double): Mesh {
        val m = Mesh()
        inputFaces.forEach { fp ->
            if (fp.verts.size < 3) return@forEach
            val f = Face()
            f.basis = computeFaceBasis(fp.verts, eps)
            val n = fp.verts.size
            val vs = fp.verts.map { getOrCreateVtx(m, it, eps) }
            val hes = (0 until n).map { HE() }
            for (i in 0 until n) {
                val he = hes[i]
                he.origin = vs[i]
                he.face = f
                vs[i].outgoing = he
            }
            for (i in 0 until n) {
                val he = hes[i]
                he.next = hes[(i + 1) % n]
                he.prev = hes[(i - 1 + n) % n]
            }
            for (i in 0 until n) {
                val a = hes[i].origin
                val b = hes[i].next!!.origin
                val dk = directedEdgeKey(a.key3, b.key3)
                val rk = directedEdgeKey(b.key3, a.key3)
                val other = m.heByEdgeKey[rk]
                if (other != null) {
                    hes[i].twin = other
                    other.twin = hes[i]
                    val ee = other.edgeRef ?: EEdge()
                    ee.heA = other
                    ee.heB = hes[i]
                    other.edgeRef = ee
                    hes[i].edgeRef = ee
                } else {
                    m.heByEdgeKey[dk] = hes[i]
                }
            }
            f.boundary = hes[0]
            m.faces.add(f)
        }
        return m
    }

    private fun splitEdgeAtParam(m: Mesh, he: HE, t: Double, eps: Double): Vtx {
        val a = he.origin
        val b = he.next!!.origin
        if (t <= eps) return a
        if (t >= 1.0 - eps) return b
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        val tq = if (len > eps) round(t * (len / eps)) / (len / eps) else t
        val tcl = clamp(tq, 0.0, 1.0)
        val p = Vec3(a.x + tcl * (b.x - a.x), a.y + tcl * (b.y - a.y), a.z + tcl * (b.z - a.z))
        val mid = getOrCreateVtx(m, p, eps)
        if (mid == a || mid == b) return mid
        val heAM = HE()
        heAM.origin = mid
        heAM.face = he.face
        heAM.next = he.next
        heAM.prev = he
        he.next!!.prev = heAM
        he.next = heAM
        if (he.twin != null) {
            val ht = he.twin!!
            val htAM = HE()
            htAM.origin = mid
            htAM.face = ht.face
            htAM.next = ht.next
            htAM.prev = ht
            ht.next!!.prev = htAM
            ht.next = htAM
            heAM.twin = ht
            ht.twin = heAM
            he.twin = htAM
            htAM.twin = he
            val e1 = EEdge().apply { heA = he; heB = he.twin }
            val e2 = EEdge().apply { heA = heAM; heB = heAM.twin }
            he.edgeRef = e1; he.twin!!.edgeRef = e1
            heAM.edgeRef = e2; heAM.twin!!.edgeRef = e2
        }
        mid.outgoing = heAM
        return mid
    }

    private fun splitFaceToPolys(mesh: Mesh, face: Face, segs: List<Segment3>, eps: Double): List<FacePoly> {
        val b = face.basis
        val planeTol = eps * 5.0
        val boundaryHEs = mutableListOf<HE>()
        run {
            var he = face.boundary
            boundaryHEs.add(he)
            he = he.next!!
            while (he != face.boundary) {
                boundaryHEs.add(he)
                he = he.next!!
            }
        }
        val poly2 = boundaryHEs.map { project2(it.origin.toVec3(), b) }
        val nodeByKey = mutableMapOf<Long, Node2>()
        fun getNode(x: Double, y: Double, v3: Vtx): Node2 {
            val k = key2(x, y, eps)
            return nodeByKey.getOrPut(k) { Node2(x, y, v3, k) }
        }
        val boundaryNodes = mutableListOf<Node2>()
        for (i in boundaryHEs.indices) {
            val v3 = boundaryHEs[i].origin
            val p2 = poly2[i]
            boundaryNodes.add(getNode(p2[0], p2[1], v3))
        }

        class EndPt(val n2: Node2)
        val cutPairs = mutableListOf<Pair<EndPt, EndPt>>()

        segs.forEach { s ->
            val da = abs(signedDistanceToPlane(s.a, b))
            val db = abs(signedDistanceToPlane(s.b, b))
            if (min(da, db) > planeTol) return@forEach
            val a2 = project2(s.a, b)
            val c2 = project2(s.b, b)
            val ax = a2[0]; val ay = a2[1]; val cx = c2[0]; val cy = c2[1]

            val hits = mutableListOf<SegHit2>()
            for (i in boundaryHEs.indices) {
                val j = (i + 1) % boundaryHEs.size
                val ex0 = poly2[i][0]; val ey0 = poly2[i][1]
                val ex1 = poly2[j][0]; val ey1 = poly2[j][1]
                val hit = segIntersect2(ex0, ey0, ex1, ey1, ax, ay, cx, cy, eps)
                if (hit != null) {
                    hit.edgeHe = boundaryHEs[i]
                    hits.add(hit)
                }
            }

            val aInside = pointInPolygon2(ax, ay, poly2, eps)
            val cInside = pointInPolygon2(cx, cy, poly2, eps)
            val candidates = mutableListOf<Map<String, Any?>>()
            if (aInside) candidates.add(mapOf("t" to 0.0, "x" to ax, "y" to ay, "edgeHe" to null))
            if (cInside) candidates.add(mapOf("t" to 1.0, "x" to cx, "y" to cy, "edgeHe" to null))
            hits.forEach { h ->
                candidates.add(mapOf("t" to h.tSeg, "x" to h.ix, "y" to h.iy, "edgeHe" to h.edgeHe, "tEdge" to h.tEdge))
            }
            if (candidates.size < 2) return@forEach
            candidates.sortBy { it["t"] as Double }
            val p0 = candidates.first()
            val p1 = candidates.last()
            if (abs((p1["t"] as Double) - (p0["t"] as Double)) <= eps) return@forEach

            fun makeEndpoint(pp: Map<String, Any?>): EndPt {
                val edgeHe = pp["edgeHe"] as HE?
                return if (edgeHe != null) {
                    val vMid = splitEdgeAtParam(mesh, edgeHe, pp["tEdge"] as Double, eps)
                    val pm2 = project2(vMid.toVec3(), b)
                    EndPt(getNode(pm2[0], pm2[1], vMid))
                } else {
                    val p3 = unproject2(pp["x"] as Double, pp["y"] as Double, b)
                    val v3 = getOrCreateVtx(mesh, p3, eps)
                    val p2n = project2(v3.toVec3(), b)
                    EndPt(getNode(p2n[0], p2n[1], v3))
                }
            }

            val e0 = makeEndpoint(p0)
            val e1 = makeEndpoint(p1)
            if (e0.n2 == e1.n2) return@forEach
            cutPairs.add(Pair(e0, e1))
        }

        if (cutPairs.isEmpty()) {
            return listOf(FacePoly(boundaryHEs.map { it.origin.toVec3() }))
        }

        // Rebuild boundary nodes after edge splits
        boundaryHEs.clear()
        run {
            var he = face.boundary
            boundaryHEs.add(he)
            he = he.next!!
            while (he != face.boundary) {
                boundaryHEs.add(he)
                he = he.next!!
            }
        }
        boundaryNodes.clear()
        for (he in boundaryHEs) {
            val p2 = project2(he.origin.toVec3(), b)
            boundaryNodes.add(getNode(p2[0], p2[1], he.origin))
        }

        val allLoc = mutableListOf<LocHE>()
        fun addDirectedEdge(a: Node2, c: Node2, isBoundary: Boolean) {
            val e = LocHE(a, isBoundary)
            val et = LocHE(c, isBoundary)
            e.twin = et
            et.twin = e
            allLoc.add(e)
            allLoc.add(et)
            a.out.add(e)
            c.out.add(et)
        }

        for (i in boundaryNodes.indices) {
            val na = boundaryNodes[i]
            val nb = boundaryNodes[(i + 1) % boundaryNodes.size]
            addDirectedEdge(na, nb, true)
        }
        cutPairs.forEach { pair -> addDirectedEdge(pair.first.n2, pair.second.n2, false) }

        nodeByKey.values.forEach { n2 ->
            n2.out.forEach { e ->
                val dx = e.twin!!.origin.x - e.origin.x
                val dy = e.twin!!.origin.y - e.origin.y
                e.angle = atan2(dy, dx)
            }
            n2.out.sortBy { it.angle }
        }

        fun indexOfEdge(list: List<LocHE>, target: LocHE): Int {
            for (i in list.indices) {
                if (list[i] === target) return i
            }
            return -1
        }

        allLoc.forEach { e ->
            val v = e.twin!!.origin
            val outs = v.out
            val idx = indexOfEdge(outs, e.twin!!)
            if (idx < 0) return@forEach
            val prevIdx = (idx - 1 + outs.size) % outs.size
            e.next = outs[prevIdx]
            outs[prevIdx].prev = e
        }

        data class Cycle(val edges: MutableList<LocHE> = mutableListOf(), var area: Double = 0.0)
        val cycles = mutableListOf<Cycle>()
        allLoc.forEach { e ->
            if (e.used) return@forEach
            var cur: LocHE? = e
            var guard = 0
            val cy = Cycle()
            while (cur != null && !cur.used && guard++ < 100000) {
                cur.used = true
                cy.edges.add(cur)
                cur = cur.next
                if (cur === e) break
            }
            if (cur === e && cy.edges.size >= 3) {
                var A = 0.0
                for (i in cy.edges.indices) {
                    val p = cy.edges[i].origin
                    val q = cy.edges[(i + 1) % cy.edges.size].origin
                    A += (p.x * q.y - q.x * p.y)
                }
                cy.area = 0.5 * A
                cycles.add(cy)
            }
        }

        if (cycles.isEmpty()) {
            return listOf(FacePoly(boundaryHEs.map { it.origin.toVec3() }))
        }
        val outer = cycles.maxByOrNull { abs(it.area) }
        val inner = cycles.filter { it !== outer && abs(it.area) > eps * eps }
        if (inner.isEmpty()) {
            return listOf(FacePoly(boundaryHEs.map { it.origin.toVec3() }))
        }
        val out = mutableListOf<FacePoly>()
        inner.forEach { cy ->
            val poly3 = cy.edges.map { it.origin.v3.toVec3() }
            val cleaned = mutableListOf<Vec3>()
            for (i in poly3.indices) {
                val p = poly3[i]
                val q = poly3[(i + 1) % poly3.size]
                val dx = p.x - q.x
                val dy = p.y - q.y
                val dz = p.z - q.z
                if (sqrt(dx * dx + dy * dy + dz * dz) > eps * 0.5) cleaned.add(p)
            }
            if (cleaned.size >= 3) out.add(FacePoly(cleaned))
        }
        return out
    }

    fun cutFaces(inputFaces: List<FacePoly>, segments: List<Segment3>, eps: Double = 1e-3): List<FacePoly> {
        if (inputFaces.isEmpty()) return emptyList()
        if (segments.isEmpty()) return inputFaces
        val mesh = buildHalfEdgeMesh(inputFaces, eps)
        val faceCuts = mutableMapOf<Face, MutableList<Segment3>>()
        mesh.faces.forEach { faceCuts[it] = mutableListOf() }
        mesh.faces.forEach { f ->
            val b = f.basis
            val p2 = mutableListOf<DoubleArray>()
            run {
                var h = f.boundary
                p2.add(project2(h.origin.toVec3(), b))
                h = h.next!!
                while (h != f.boundary) {
                    p2.add(project2(h.origin.toVec3(), b))
                    h = h.next!!
                }
            }
            var minx = Double.POSITIVE_INFINITY
            var miny = Double.POSITIVE_INFINITY
            var maxx = Double.NEGATIVE_INFINITY
            var maxy = Double.NEGATIVE_INFINITY
            p2.forEach {
                minx = min(minx, it[0]); miny = min(miny, it[1])
                maxx = max(maxx, it[0]); maxy = max(maxy, it[1])
            }
            segments.forEach { s ->
                val da = abs(signedDistanceToPlane(s.a, b))
                val db = abs(signedDistanceToPlane(s.b, b))
                if (min(da, db) > eps * 5.0) return@forEach
                val a2 = project2(s.a, b)
                val c2 = project2(s.b, b)
                val sminx = min(a2[0], c2[0]) - eps
                val smaxx = max(a2[0], c2[0]) + eps
                val sminy = min(a2[1], c2[1]) - eps
                val smaxy = max(a2[1], c2[1]) + eps
                val overlap = !(smaxx < minx || sminx > maxx || smaxy < miny || sminy > maxy)
                if (overlap) faceCuts[f]!!.add(s)
            }
        }

        val outFaces = mutableListOf<FacePoly>()
        mesh.faces.forEach { f ->
            val segs = faceCuts[f]!!
            if (segs.isEmpty()) {
                val verts = mutableListOf<Vec3>()
                var h = f.boundary
                verts.add(h.origin.toVec3())
                h = h.next!!
                while (h != f.boundary) {
                    verts.add(h.origin.toVec3())
                    h = h.next!!
                }
                outFaces.add(FacePoly(verts))
            } else {
                outFaces.addAll(splitFaceToPolys(mesh, f, segs, eps))
            }
        }

        val cleaned = mutableListOf<FacePoly>()
        outFaces.forEach { fp ->
            if (fp.verts.size < 3) return@forEach
            val v = mutableListOf<Vec3>()
            for (i in fp.verts.indices) {
                val a = fp.verts[i]
                val b = fp.verts[(i + 1) % fp.verts.size]
                val dx = a.x - b.x
                val dy = a.y - b.y
                val dz = a.z - b.z
                if (sqrt(dx * dx + dy * dy + dz * dz) > eps * 0.5) v.add(a)
            }
            if (v.size >= 3) cleaned.add(FacePoly(v))
        }
        return cleaned
    }
}

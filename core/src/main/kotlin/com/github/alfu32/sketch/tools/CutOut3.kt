@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.github.alfu32.sketch.tools

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Cuts triangles by a sequence of infinite planes derived from input segments.
 *
 * For each segment (a,b):
 * 1) Build one plane that passes through the segment line and contains the global averaged normal.
 * 2) Intersect that plane against every current triangle.
 * 3) Replace each intersected triangle with the split result immediately for the next iteration.
 *
 * This follows the "replace after every segment" behavior directly and does not run any
 * post-merge/consolidation pass that could collapse cuts.
 */
object CutOut3 {
    data class Vec3(val x: Double, val y: Double, val z: Double)
    data class Triangle(val a: Vec3, val b: Vec3, val c: Vec3)
    data class Segment(val a: Vec3, val b: Vec3)

    private data class Vtx(val x: Double, val y: Double, val z: Double, val key: Long) {
        fun v(): Vec3 = Vec3(x, y, z)
    }

    private data class TriV(val a: Vtx, val b: Vtx, val c: Vtx)

    private fun dot(a: Vec3, b: Vec3): Double = a.x * b.x + a.y * b.y + a.z * b.z
    private fun sub(a: Vec3, b: Vec3): Vec3 = Vec3(a.x - b.x, a.y - b.y, a.z - b.z)
    private fun mul(a: Vec3, s: Double): Vec3 = Vec3(a.x * s, a.y * s, a.z * s)
    private fun cross(a: Vec3, b: Vec3): Vec3 {
        return Vec3(
            a.y * b.z - a.z * b.y,
            a.z * b.x - a.x * b.z,
            a.x * b.y - a.y * b.x
        )
    }

    private fun norm(a: Vec3): Double = sqrt(dot(a, a))

    private fun normalize(a: Vec3, eps: Double): Vec3 {
        val n = norm(a)
        return if (n <= eps) Vec3(0.0, 0.0, 0.0) else mul(a, 1.0 / n)
    }

    private fun clamp(x: Double, lo: Double, hi: Double): Double = max(lo, min(hi, x))

    private fun q1(x: Double, q: Double): Long = (x / q).roundToLong()

    private fun mix64(x0: Long): Long {
        var z = x0 + 0x9E3779B97F4A7C15UL.toLong()
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9UL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBUL.toLong()
        return z xor (z ushr 31)
    }

    private fun key3(p: Vec3, eps: Double): Long {
        val kx = q1(p.x, eps)
        val ky = q1(p.y, eps)
        val kz = q1(p.z, eps)
        var h = 0xCBF29CE484222325UL.toLong()
        h = mix64(h xor kx)
        h = mix64(h xor ky)
        h = mix64(h xor kz)
        return h
    }

    private class VertexPool(private val eps: Double) {
        private val map = HashMap<Long, Vtx>(1 shl 16)

        fun weld(p: Vec3): Vtx {
            val key = key3(p, eps)
            return map.getOrPut(key) { Vtx(p.x, p.y, p.z, key) }
        }
    }

    private data class Plane(val n: Vec3, val d: Double)

    private fun signedDistance(p: Vec3, pl: Plane): Double = dot(pl.n, p) - pl.d

    private fun snapToPlane(p: Vec3, pl: Plane, s: Double): Vec3 = sub(p, mul(pl.n, s))

    private fun triArea2(a: Vec3, b: Vec3, c: Vec3): Double {
        return norm(cross(sub(b, a), sub(c, a)))
    }

    private fun clipTriangleToHalfspace(
        a: Vtx,
        b: Vtx,
        c: Vtx,
        pl: Plane,
        eps: Double,
        keepPositive: Boolean,
        pool: VertexPool
    ): List<Vtx> {
        val verts = arrayOf(a, b, c)
        val out = ArrayList<Vtx>(4)

        fun inside(s: Double): Boolean = if (keepPositive) s >= -eps else s <= eps

        for (i in 0..2) {
            val cur = verts[i]
            val nxt = verts[(i + 1) % 3]
            val p = cur.v()
            val q = nxt.v()
            var sp = signedDistance(p, pl)
            val sq = signedDistance(q, pl)

            if (abs(sp) <= eps) {
                val ps = snapToPlane(p, pl, sp)
                val w = pool.weld(ps)
                sp = 0.0
                if (inside(sp)) out.add(w)
            } else if (inside(sp)) {
                out.add(cur)
            }

            val inP = inside(sp)
            val inQ = inside(sq)
            if (inP xor inQ) {
                val denom = sp - sq
                if (abs(denom) > 1e-15) {
                    var t = sp / denom
                    t = clamp(t, 0.0, 1.0)
                    if (t <= eps) t = 0.0
                    if (t >= 1.0 - eps) t = 1.0
                    val ip = Vec3(
                        p.x + t * (q.x - p.x),
                        p.y + t * (q.y - p.y),
                        p.z + t * (q.z - p.z)
                    )
                    val si = signedDistance(ip, pl)
                    val ips = if (abs(si) <= 5.0 * eps) snapToPlane(ip, pl, si) else ip
                    out.add(pool.weld(ips))
                }
            }
        }

        if (out.size >= 2) {
            val compact = ArrayList<Vtx>(out.size)
            for (i in out.indices) {
                val p = out[i]
                val q = out[(i + 1) % out.size]
                if (p.key != q.key) compact.add(p)
            }
            return compact
        }
        return out
    }

    private fun fanTriangulate(poly: List<Vtx>): List<TriV> {
        if (poly.size < 3) return emptyList()
        if (poly.size == 3) return listOf(TriV(poly[0], poly[1], poly[2]))
        val a = poly[0]
        val out = ArrayList<TriV>(poly.size - 2)
        for (i in 1 until poly.size - 1) {
            out.add(TriV(a, poly[i], poly[i + 1]))
        }
        return out
    }

    private fun orientToNormal(t: TriV, nRef: Vec3): TriV {
        val n = cross(sub(t.b.v(), t.a.v()), sub(t.c.v(), t.a.v()))
        return if (dot(n, nRef) >= 0.0) t else TriV(t.a, t.c, t.b)
    }

    private fun splitTriangleByPlane(
        tri: TriV,
        pl: Plane,
        eps: Double,
        pool: VertexPool,
        nRef: Vec3,
        areaEps2: Double
    ): List<TriV> {
        val posPoly = clipTriangleToHalfspace(tri.a, tri.b, tri.c, pl, eps, keepPositive = true, pool = pool)
        val negPoly = clipTriangleToHalfspace(tri.a, tri.b, tri.c, pl, eps, keepPositive = false, pool = pool)

        val out = ArrayList<TriV>(4)
        fun addIfNonDeg(list: List<TriV>) {
            for (t in list) {
                if (triArea2(t.a.v(), t.b.v(), t.c.v()) > areaEps2) {
                    out.add(orientToNormal(t, nRef))
                }
            }
        }

        addIfNonDeg(fanTriangulate(posPoly))
        addIfNonDeg(fanTriangulate(negPoly))
        return out
    }

    private fun averageNormal(tris: List<Triangle>, eps: Double): Vec3 {
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0
        for (t in tris) {
            val n = cross(sub(t.b, t.a), sub(t.c, t.a))
            sx += n.x
            sy += n.y
            sz += n.z
        }
        return normalize(Vec3(sx, sy, sz), eps)
    }

    private data class Basis2(val u: Vec3, val v: Vec3)

    private fun surfaceBasis(n: Vec3, eps: Double): Basis2 {
        val ref = if (abs(n.x) < 0.7) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        var u = normalize(cross(ref, n), eps)
        if (norm(u) <= eps) {
            u = normalize(cross(Vec3(0.0, 0.0, 1.0), n), eps)
        }
        return Basis2(u, cross(n, u))
    }

    private fun proj2(p: Vec3, basis: Basis2): Pair<Double, Double> = Pair(dot(p, basis.u), dot(p, basis.v))

    private fun collinear2(
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
        c: Pair<Double, Double>,
        eps: Double
    ): Boolean {
        val abx = b.first - a.first
        val aby = b.second - a.second
        val acx = c.first - a.first
        val acy = c.second - a.second
        val area2 = abs(abx * acy - aby * acx)
        val scale = max(1.0, abs(abx) + abs(aby) + abs(acx) + abs(acy))
        return area2 <= eps * scale
    }

    private fun between2(
        p: Pair<Double, Double>,
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
        eps: Double
    ): Boolean {
        val pax = p.first - a.first
        val pay = p.second - a.second
        val pbx = p.first - b.first
        val pby = p.second - b.second
        val dp = pax * pbx + pay * pby
        val tol = eps * (abs(pax) + abs(pay) + abs(pbx) + abs(pby) + 1.0)
        return dp <= tol
    }

    private fun undirectedEdgeKey(aKey: Long, bKey: Long): Long {
        val lo = min(aKey, bKey)
        val hi = max(aKey, bKey)
        var h = 0x84222325CBF29CE4UL.toLong()
        h = mix64(h xor lo)
        h = mix64(h xor hi)
        return h
    }

    private fun consolidateTriangles(tris: List<TriV>, nRef: Vec3, eps: Double, areaEps2: Double): List<TriV> {
        val basis = surfaceBasis(nRef, eps)

        data class EdgeUse(val triIndex: Int, val a: Vtx, val b: Vtx)
        val edgeMap = HashMap<Long, MutableList<EdgeUse>>(tris.size * 3)

        fun addEdge(ti: Int, a: Vtx, b: Vtx) {
            val key = undirectedEdgeKey(a.key, b.key)
            edgeMap.getOrPut(key) { ArrayList(2) }.add(EdgeUse(ti, a, b))
        }

        for ((i, t) in tris.withIndex()) {
            addEdge(i, t.a, t.b)
            addEdge(i, t.b, t.c)
            addEdge(i, t.c, t.a)
        }

        val removed = BooleanArray(tris.size)
        val additions = ArrayList<TriV>()

        for ((_, uses) in edgeMap) {
            if (uses.size != 2) continue
            val i0 = uses[0].triIndex
            val i1 = uses[1].triIndex
            if (removed[i0] || removed[i1]) continue

            val t0 = tris[i0]
            val t1 = tris[i1]
            val sA = uses[0].a
            val sB = uses[0].b

            fun otherVertex(t: TriV, a: Long, b: Long): Vtx {
                return when {
                    t.a.key != a && t.a.key != b -> t.a
                    t.b.key != a && t.b.key != b -> t.b
                    else -> t.c
                }
            }

            val w0 = otherVertex(t0, sA.key, sB.key)
            val w1 = otherVertex(t1, sA.key, sB.key)
            val pSA = proj2(sA.v(), basis)
            val pSB = proj2(sB.v(), basis)
            val pW0 = proj2(w0.v(), basis)
            val pW1 = proj2(w1.v(), basis)

            var merged: TriV? = null
            if (collinear2(pW0, pSA, pW1, eps) && between2(pSA, pW0, pW1, eps)) {
                merged = TriV(w0, sB, w1)
            } else if (collinear2(pW0, pSB, pW1, eps) && between2(pSB, pW0, pW1, eps)) {
                merged = TriV(w0, sA, w1)
            }

            if (merged != null && triArea2(merged.a.v(), merged.b.v(), merged.c.v()) > areaEps2) {
                removed[i0] = true
                removed[i1] = true
                additions.add(orientToNormal(merged, nRef))
            }
        }

        val out = ArrayList<TriV>(tris.size)
        for (i in tris.indices) {
            if (!removed[i]) out.add(tris[i])
        }
        out.addAll(additions)
        return out
    }

    fun cutTriangles(
        triangles: List<Triangle>,
        segments: List<Segment>,
        eps: Double = 1e-3,
        consolidate: Boolean = false
    ): List<Triangle> {
        if (triangles.isEmpty() || segments.isEmpty()) return triangles

        val pool = VertexPool(eps)
        val nAvg = averageNormal(triangles, eps)
        if (norm(nAvg) <= eps) return triangles

        val areaEps2 = (eps * eps) * 0.5
        var work = ArrayList<TriV>(triangles.size)
        for (t in triangles) {
            val tri = orientToNormal(TriV(pool.weld(t.a), pool.weld(t.b), pool.weld(t.c)), nAvg)
            if (triArea2(tri.a.v(), tri.b.v(), tri.c.v()) > areaEps2) {
                work.add(tri)
            }
        }

        for (s in segments) {
            val d = sub(s.b, s.a)
            val dLen = norm(d)
            if (dLen <= eps) continue
            val dir = mul(d, 1.0 / dLen)
            val m = cross(dir, nAvg)
            val mLen = norm(m)
            if (mLen <= eps) continue
            val mUnit = mul(m, 1.0 / mLen)
            val plane = Plane(mUnit, dot(mUnit, s.a))

            val next = ArrayList<TriV>(work.size * 2)
            for (tri in work) {
                next.addAll(splitTriangleByPlane(tri, plane, eps, pool, nAvg, areaEps2))
            }
            work = next
        }

        // Keep cuts exactly as generated by the sequential plane splits.
        // Consolidation can remove intended cut edges, so it is intentionally disabled.
        if (consolidate && work.size >= 2) {
            // no-op by design
        }

        return work.map { t -> Triangle(t.a.v(), t.b.v(), t.c.v()) }
    }
}

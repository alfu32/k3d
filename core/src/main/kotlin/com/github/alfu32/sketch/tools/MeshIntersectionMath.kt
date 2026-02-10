package com.github.alfu32.sketch.tools

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

object MeshIntersectionMath {
    data class Triangle3(val a: Vector3, val b: Vector3, val c: Vector3)
    data class Segment3(val start: Vector3, val end: Vector3)

    private data class QuantKey(val x: Long, val y: Long, val z: Long) : Comparable<QuantKey> {
        override fun compareTo(other: QuantKey): Int {
            if (x != other.x) return x.compareTo(other.x)
            if (y != other.y) return y.compareTo(other.y)
            return z.compareTo(other.z)
        }
    }

    private data class SegmentKey(val a: QuantKey, val b: QuantKey)

    fun intersectTrianglePlane(
        triangle: Triangle3,
        planePoint: Vector3,
        planeNormal: Vector3,
        epsilon: Float = 1e-4f
    ): Segment3? {
        val normal = Vector3(planeNormal)
        if (normal.len2() <= epsilon * epsilon) {
            return null
        }
        normal.nor()
        return intersectTrianglePlaneNormalized(triangle, planePoint, normal, epsilon)
    }

    fun intersectTrianglePlaneNormalized(
        triangle: Triangle3,
        planePoint: Vector3,
        planeNormalUnit: Vector3,
        epsilon: Float = 1e-4f
    ): Segment3? {
        if (planeNormalUnit.len2() <= epsilon * epsilon) {
            return null
        }
        val d0 = signedDistance(triangle.a, planePoint, planeNormalUnit)
        val d1 = signedDistance(triangle.b, planePoint, planeNormalUnit)
        val d2 = signedDistance(triangle.c, planePoint, planeNormalUnit)

        if ((d0 > epsilon && d1 > epsilon && d2 > epsilon) ||
            (d0 < -epsilon && d1 < -epsilon && d2 < -epsilon)
        ) {
            return null
        }

        val points = mutableListOf<Vector3>()
        val epsSq = epsilon * epsilon

        if (abs(d0) <= epsilon) addUniquePoint(points, Vector3(triangle.a), epsSq)
        if (abs(d1) <= epsilon) addUniquePoint(points, Vector3(triangle.b), epsSq)
        if (abs(d2) <= epsilon) addUniquePoint(points, Vector3(triangle.c), epsSq)

        addEdgeIntersection(points, triangle.a, triangle.b, d0, d1, epsilon, epsSq)
        addEdgeIntersection(points, triangle.b, triangle.c, d1, d2, epsilon, epsSq)
        addEdgeIntersection(points, triangle.c, triangle.a, d2, d0, epsilon, epsSq)

        if (points.size < 2) {
            return null
        }

        val pair = farthestPair(points) ?: return null
        if (pair.first.dst2(pair.second) <= epsSq) {
            return null
        }
        return Segment3(pair.first, pair.second)
    }

    fun intersectTriangles(
        t1: Triangle3,
        t2: Triangle3,
        epsilon: Float = 1e-4f
    ): Segment3? {
        val n1 = triangleNormal(t1, epsilon) ?: return null
        val n2 = triangleNormal(t2, epsilon) ?: return null

        val s1 = intersectTrianglePlaneNormalized(t1, t2.a, n2, epsilon) ?: return null
        val s2 = intersectTrianglePlaneNormalized(t2, t1.a, n1, epsilon) ?: return null

        val direction = Vector3(n1).crs(n2)
        if (direction.len2() <= epsilon * epsilon) {
            // Coplanar or nearly parallel surfaces are intentionally ignored in this tool.
            return null
        }
        direction.nor()

        val t1a = direction.dot(s1.start)
        val t1b = direction.dot(s1.end)
        val t2a = direction.dot(s2.start)
        val t2b = direction.dot(s2.end)

        val min1 = min(t1a, t1b)
        val max1 = max(t1a, t1b)
        val min2 = min(t2a, t2b)
        val max2 = max(t2a, t2b)

        val overlapMin = max(min1, min2)
        val overlapMax = min(max1, max2)
        if (overlapMax - overlapMin <= epsilon) {
            return null
        }

        val base = Vector3(s1.start)
        val baseT = direction.dot(base)
        val p0 = Vector3(base).mulAdd(direction, overlapMin - baseT)
        val p1 = Vector3(base).mulAdd(direction, overlapMax - baseT)
        if (p0.dst2(p1) <= epsilon * epsilon) {
            return null
        }

        return Segment3(p0, p1)
    }

    fun dedupeSegments(segments: List<Segment3>, epsilon: Float = 1e-3f): List<Segment3> {
        if (segments.isEmpty()) {
            return emptyList()
        }
        val map = linkedMapOf<SegmentKey, Segment3>()
        segments.forEach { segment ->
            if (segment.start.dst2(segment.end) <= epsilon * epsilon) {
                return@forEach
            }
            val a = quantKey(segment.start, epsilon)
            val b = quantKey(segment.end, epsilon)
            val key = if (a <= b) SegmentKey(a, b) else SegmentKey(b, a)
            map.putIfAbsent(key, segment)
        }
        return map.values.toList()
    }

    private fun triangleNormal(triangle: Triangle3, epsilon: Float): Vector3? {
        val n = Vector3(triangle.b).sub(triangle.a).crs(Vector3(triangle.c).sub(triangle.a))
        if (n.len2() <= epsilon * epsilon) {
            return null
        }
        return n.nor()
    }

    private fun signedDistance(point: Vector3, planePoint: Vector3, planeNormal: Vector3): Float {
        val dx = point.x - planePoint.x
        val dy = point.y - planePoint.y
        val dz = point.z - planePoint.z
        return dx * planeNormal.x + dy * planeNormal.y + dz * planeNormal.z
    }

    private fun addEdgeIntersection(
        out: MutableList<Vector3>,
        a: Vector3,
        b: Vector3,
        da: Float,
        db: Float,
        epsilon: Float,
        epsilonSq: Float
    ) {
        if (da * db >= -(epsilon * epsilon)) {
            return
        }
        val denom = da - db
        if (abs(denom) <= epsilon) {
            return
        }
        val t = (da / denom).coerceIn(0f, 1f)
        val p = Vector3(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t
        )
        addUniquePoint(out, p, epsilonSq)
    }

    private fun addUniquePoint(out: MutableList<Vector3>, point: Vector3, epsilonSq: Float) {
        if (out.any { it.dst2(point) <= epsilonSq }) {
            return
        }
        out.add(point)
    }

    private fun farthestPair(points: List<Vector3>): Pair<Vector3, Vector3>? {
        if (points.size < 2) {
            return null
        }
        var bestI = 0
        var bestJ = 1
        var bestD2 = points[0].dst2(points[1])
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val d2 = points[i].dst2(points[j])
                if (d2 > bestD2) {
                    bestD2 = d2
                    bestI = i
                    bestJ = j
                }
            }
        }
        return Pair(Vector3(points[bestI]), Vector3(points[bestJ]))
    }

    private fun quantKey(point: Vector3, epsilon: Float): QuantKey {
        return QuantKey(
            (point.x / epsilon).roundToLong(),
            (point.y / epsilon).roundToLong(),
            (point.z / epsilon).roundToLong()
        )
    }
}

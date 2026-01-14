package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3

class DraftLineStore {
    data class Segment(val start: Vector3, val end: Vector3)

    private val segments = mutableListOf<Segment>()
    private val epsilon = 1e-3f
    private val epsilonSq = epsilon * epsilon

    fun addSegment(start: Vector3, end: Vector3) {
        if (start.dst2(end) <= epsilonSq) {
            return
        }
        val newStart = snapToExistingEndpoint(start) ?: Vector3(start)
        val newEnd = snapToExistingEndpoint(end) ?: Vector3(end)
        val splitPoints = mutableListOf(PointOnSegment(0f, newStart), PointOnSegment(1f, newEnd))

        var i = 0
        while (i < segments.size) {
            val existing = segments[i]
            val intersection = intersectSegments(newStart, newEnd, existing.start, existing.end) ?: run {
                i++
                continue
            }
            val snapped = snapToExistingEndpoint(intersection.point)
            val point = snapped ?: intersection.point
            val t = paramAlong(newStart, newEnd, point)
            val u = paramAlong(existing.start, existing.end, point)

            if (u > epsilon && u < 1f - epsilon) {
                segments.removeAt(i)
                segments.add(i, Segment(Vector3(existing.start), Vector3(point)))
                segments.add(i + 1, Segment(Vector3(point), Vector3(existing.end)))
                i += 2
            } else {
                i++
            }

            if (t > epsilon && t < 1f - epsilon) {
                splitPoints.add(PointOnSegment(t, Vector3(point)))
            }
        }

        val orderedPoints = splitPoints
            .sortedBy { it.t }
            .map { it.point }
            .let { dedupePoints(it) }

        for (p in 0 until orderedPoints.size - 1) {
            val a = orderedPoints[p]
            val b = orderedPoints[p + 1]
            if (a.dst2(b) > epsilonSq) {
                segments.add(Segment(Vector3(a), Vector3(b)))
            }
        }

        mergeColinearSegments()
    }

    fun getSegments(): List<Segment> = segments

    fun cleanup() {
        if (segments.isEmpty()) {
            return
        }
        val snapshot = segments.toList()
        segments.clear()
        snapshot.forEach { addSegment(it.start, it.end) }
    }

    private data class PointOnSegment(val t: Float, val point: Vector3)

    private data class Intersection(val point: Vector3, val s: Float, val t: Float)

    private fun intersectSegments(a0: Vector3, a1: Vector3, b0: Vector3, b1: Vector3): Intersection? {
        val d1 = Vector3(a1).sub(a0)
        val d2 = Vector3(b1).sub(b0)
        val r = Vector3(a0).sub(b0)
        val a = d1.dot(d1)
        val e = d2.dot(d2)
        val f = d2.dot(r)
        if (a <= epsilonSq && e <= epsilonSq) {
            return null
        }
        var s: Float
        var t: Float
        if (a <= epsilonSq) {
            s = 0f
            t = clamp(f / e)
        } else {
            val c = d1.dot(r)
            if (e <= epsilonSq) {
                t = 0f
                s = clamp(-c / a)
            } else {
                val b = d1.dot(d2)
                val denom = a * e - b * b
                s = if (kotlin.math.abs(denom) > epsilon) {
                    clamp((b * f - c * e) / denom)
                } else {
                    0f
                }
                val tNom = b * s + f
                if (tNom < 0f) {
                    t = 0f
                    s = clamp(-c / a)
                } else if (tNom > e) {
                    t = 1f
                    s = clamp((b - c) / a)
                } else {
                    t = tNom / e
                }
            }
        }
        val p1 = Vector3(a0).mulAdd(d1, s)
        val p2 = Vector3(b0).mulAdd(d2, t)
        if (p1.dst2(p2) > epsilonSq) {
            return null
        }
        val hit = Vector3(p1).add(p2).scl(0.5f)
        return Intersection(hit, s, t)
    }

    private fun clamp(value: Float): Float = value.coerceIn(0f, 1f)

    private fun paramAlong(a: Vector3, b: Vector3, p: Vector3): Float {
        val ab = Vector3(b).sub(a)
        val lenSq = ab.len2()
        if (lenSq <= epsilonSq) {
            return 0f
        }
        return clamp(Vector3(p).sub(a).dot(ab) / lenSq)
    }

    private fun snapToExistingEndpoint(point: Vector3): Vector3? {
        segments.forEach { segment ->
            if (segment.start.dst2(point) <= epsilonSq) {
                return Vector3(segment.start)
            }
            if (segment.end.dst2(point) <= epsilonSq) {
                return Vector3(segment.end)
            }
        }
        return null
    }

    private fun dedupePoints(points: List<Vector3>): List<Vector3> {
        if (points.isEmpty()) {
            return points
        }
        val result = mutableListOf(Vector3(points.first()))
        for (i in 1 until points.size) {
            if (points[i].dst2(result.last()) > epsilonSq) {
                result.add(Vector3(points[i]))
            }
        }
        return result
    }

    private fun mergeColinearSegments() {
        var changed = true
        while (changed) {
            changed = false
            outer@ for (i in segments.indices) {
                val a = segments[i]
                for (j in i + 1 until segments.size) {
                    val b = segments[j]
                    val shared = sharedEndpoint(a, b) ?: continue
                    val dirA = Vector3(a.end).sub(a.start).nor()
                    val dirB = Vector3(b.end).sub(b.start).nor()
                    if (dirA.crs(dirB).len2() > epsilonSq) {
                        continue
                    }
                    val otherA = if (shared.dst2(a.start) <= epsilonSq) a.end else a.start
                    val otherB = if (shared.dst2(b.start) <= epsilonSq) b.end else b.start
                    segments.removeAt(j)
                    segments.removeAt(i)
                    segments.add(Segment(Vector3(otherA), Vector3(otherB)))
                    changed = true
                    break@outer
                }
            }
        }
    }

    private fun sharedEndpoint(a: Segment, b: Segment): Vector3? {
        if (a.start.dst2(b.start) <= epsilonSq) return a.start
        if (a.start.dst2(b.end) <= epsilonSq) return a.start
        if (a.end.dst2(b.start) <= epsilonSq) return a.end
        if (a.end.dst2(b.end) <= epsilonSq) return a.end
        return null
    }
}

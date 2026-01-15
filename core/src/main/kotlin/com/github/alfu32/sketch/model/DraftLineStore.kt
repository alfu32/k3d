package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3

class DraftLineStore {
    data class Segment(val start: Vector3, val end: Vector3)
    data class Hit(val segment: Segment, val point: Vector3, val t: Float)

    private val segments = mutableListOf<Segment>()
    private val selected = mutableSetOf<Segment>()
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

    }

    fun getSegments(): List<Segment> = segments

    fun getSelected(): Set<Segment> = selected

    fun isSelected(segment: Segment): Boolean = selected.contains(segment)

    fun addSelection(segment: Segment): Boolean = selected.add(segment)

    fun removeSelection(segment: Segment) {
        selected.remove(segment)
    }

    fun toggleSelection(segment: Segment) {
        if (!selected.add(segment)) {
            selected.remove(segment)
        }
    }

    fun clearSelection() {
        selected.clear()
    }

    fun deleteSelected(): Int {
        if (selected.isEmpty()) {
            return 0
        }
        val before = segments.size
        segments.removeAll(selected)
        selected.clear()
        return before - segments.size
    }

    fun selectInVolume(min: Vector3, max: Vector3, replace: Boolean = true): Int {
        if (replace) {
            selected.clear()
        }
        var count = 0
        segments.forEach { segment ->
            if (segmentIntersectsAabb(segment.start, segment.end, min, max)) {
                if (selected.add(segment)) {
                    count++
                }
            }
        }
        return count
    }

    fun cleanup() {
        if (segments.isEmpty()) {
            return
        }
        val snapshot = segments.toList()
        segments.clear()
        snapshot.forEach { addSegment(it.start, it.end) }
    }

    fun pickSegment(
        ray: com.badlogic.gdx.math.collision.Ray,
        camera: com.badlogic.gdx.graphics.Camera,
        screenX: Int,
        screenY: Int,
        maxPixels: Float = 12f
    ): Hit? {
        var best: Hit? = null
        val dir = Vector3(ray.direction).nor()
        segments.forEach { segment ->
            val hit = closestRaySegment(ray.origin, dir, segment) ?: return@forEach
            val screenDist = screenDistance(camera, hit.point, screenX, screenY)
            if (screenDist <= maxPixels) {
                if (best == null || hit.t < best!!.t) {
                    best = hit
                }
            }
        }
        return best
    }

    fun collectConnected(base: Segment): List<Segment> {
        if (segments.isEmpty()) {
            return emptyList()
        }
        val endpointMap = mutableMapOf<VertexKey, MutableList<Segment>>()
        segments.forEach { segment ->
            val a = vertexKey(segment.start)
            val b = vertexKey(segment.end)
            endpointMap.getOrPut(a) { mutableListOf() }.add(segment)
            endpointMap.getOrPut(b) { mutableListOf() }.add(segment)
        }
        val result = mutableListOf<Segment>()
        val queue = ArrayDeque<Segment>()
        val visited = mutableSetOf<Segment>()
        queue.add(base)
        visited.add(base)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            val a = vertexKey(current.start)
            val b = vertexKey(current.end)
            val neighbors = endpointMap[a].orEmpty() + endpointMap[b].orEmpty()
            neighbors.forEach { neighbor ->
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        return result
    }

    private fun closestRaySegment(
        rayOrigin: Vector3,
        rayDir: Vector3,
        segment: Segment
    ): Hit? {
        val a = segment.start
        val b = segment.end
        val e = Vector3(b).sub(a)
        val r = Vector3(rayOrigin).sub(a)
        val aDot = rayDir.dot(rayDir)
        val eDot = e.dot(e)
        val f = rayDir.dot(e)
        val c = rayDir.dot(r)
        val g = e.dot(r)
        val denom = aDot * eDot - f * f
        var t: Float
        var s: Float
        if (kotlin.math.abs(denom) > epsilon) {
            t = (f * g - eDot * c) / denom
            s = (aDot * g - f * c) / denom
            s = s.coerceIn(0f, 1f)
            t = (-c + f * s) / aDot
        } else {
            s = (g / eDot).coerceIn(0f, 1f)
            t = (-c + f * s) / aDot
        }
        if (t <= 0f) {
            t = 0f
            s = (g / eDot).coerceIn(0f, 1f)
        }
        val pointOnRay = Vector3(rayOrigin).mulAdd(rayDir, t)
        val pointOnSeg = Vector3(a).mulAdd(e, s)
        return Hit(segment, pointOnSeg, t)
    }

    private fun screenDistance(
        camera: com.badlogic.gdx.graphics.Camera,
        world: Vector3,
        screenX: Int,
        screenY: Int
    ): Float {
        val projected = camera.project(Vector3(world))
        val dx = projected.x - screenX
        val dy = (com.badlogic.gdx.Gdx.graphics.height - projected.y) - screenY
        return kotlin.math.sqrt(dx * dx + dy * dy)
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

    private fun segmentIntersectsAabb(a: Vector3, b: Vector3, min: Vector3, max: Vector3): Boolean {
        var tmin = 0f
        var tmax = 1f

        val dx = b.x - a.x
        if (kotlin.math.abs(dx) < epsilon) {
            if (a.x < min.x || a.x > max.x) return false
        } else {
            val inv = 1f / dx
            var t1 = (min.x - a.x) * inv
            var t2 = (max.x - a.x) * inv
            if (t1 > t2) {
                val tmp = t1
                t1 = t2
                t2 = tmp
            }
            tmin = kotlin.math.max(tmin, t1)
            tmax = kotlin.math.min(tmax, t2)
            if (tmin > tmax) return false
        }

        val dy = b.y - a.y
        if (kotlin.math.abs(dy) < epsilon) {
            if (a.y < min.y || a.y > max.y) return false
        } else {
            val inv = 1f / dy
            var t1 = (min.y - a.y) * inv
            var t2 = (max.y - a.y) * inv
            if (t1 > t2) {
                val tmp = t1
                t1 = t2
                t2 = tmp
            }
            tmin = kotlin.math.max(tmin, t1)
            tmax = kotlin.math.min(tmax, t2)
            if (tmin > tmax) return false
        }

        val dz = b.z - a.z
        if (kotlin.math.abs(dz) < epsilon) {
            if (a.z < min.z || a.z > max.z) return false
        } else {
            val inv = 1f / dz
            var t1 = (min.z - a.z) * inv
            var t2 = (max.z - a.z) * inv
            if (t1 > t2) {
                val tmp = t1
                t1 = t2
                t2 = tmp
            }
            tmin = kotlin.math.max(tmin, t1)
            tmax = kotlin.math.min(tmax, t2)
            if (tmin > tmax) return false
        }
        return true
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(quant(point.x), quant(point.y), quant(point.z))
    }

    private fun quant(value: Float): Int = kotlin.math.round(value / epsilon).toInt()

}

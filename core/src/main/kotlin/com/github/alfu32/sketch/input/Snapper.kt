package com.github.alfu32.sketch.input

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftLineStore
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

class Snapper(
    private val camera: Camera,
    private val lineStore: DraftLineStore,
    private val guideManager: GuideManager,
    private val gridSpacing: Float = 1f,
    private val snapPixels: Float = 12f
) {
    private val tmp = Vector3()
    private val epsilon = 1e-2f

    fun compute(screenX: Int, screenY: Int): SnapResult {
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        val baseHit = pickBaseHit(ray)
        val basePoint = baseHit?.point
        val baseNormal = baseHit?.normal
        var best: SnapCandidate? = null

        if (basePoint != null && baseNormal != null) {
            best = SnapCandidate(basePoint, baseNormal, SnapType.NONE, Float.MAX_VALUE)
            snapToGrid(basePoint, baseNormal, screenX, screenY)?.let {
                best = pickBetter(best, it)
            }

            snapToLineEndpoints(baseNormal, screenX, screenY)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            snapToLineMidpoints(baseNormal, screenX, screenY)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            snapToGridLines(basePoint, baseNormal, screenX, screenY)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            snapToLineSegments(basePoint, baseNormal, screenX, screenY)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
        }

        if (guideManager.hasGridGuides()) {
            snapToGridGuides(ray, screenX, screenY)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
        }

        if (guideManager.hasAxisGuides() && baseNormal != null) {
            snapToAxisGuides(ray, baseNormal, screenX, screenY)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
        }

        if (best == null) {
            return SnapResult(null, null, SnapType.NONE, screenX, screenY, false)
        }
        val candidate = best ?: return SnapResult(null, null, SnapType.NONE, screenX, screenY, false)
        return SnapResult(candidate.world, candidate.normal, candidate.type, screenX, screenY, true)
    }

    private fun intersectGround(ray: com.badlogic.gdx.math.collision.Ray): PlaneHit? {
        val dirY = ray.direction.y
        if (abs(dirY) < epsilon) {
            return null
        }
        val t = -ray.origin.y / dirY
        if (t <= 0f) {
            return null
        }
        val point = Vector3(ray.origin).mulAdd(ray.direction, t)
        val normal = facingNormal(Vector3(0f, 1f, 0f), ray.direction)
        return PlaneHit(point, normal, t)
    }

    private fun pickBaseHit(ray: com.badlogic.gdx.math.collision.Ray): PlaneHit? {
        var best = intersectGround(ray)
        val guides = guideManager.getGridGuides()
        for (center in guides) {
            val planes = listOf(
                PlaneGuide(Vector3(0f, 0f, 1f), Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f)),
                PlaneGuide(Vector3(0f, 1f, 0f), Vector3(1f, 0f, 0f), Vector3(0f, 0f, 1f)),
                PlaneGuide(Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f))
            )
            for (plane in planes) {
                val hit = intersectPlane(ray, center, plane.normal)
                if (hit != null && (best == null || hit.t < best.t)) {
                    best = hit
                }
            }
        }
        return best
    }

    private fun facingNormal(normal: Vector3, rayDir: Vector3): Vector3 {
        return if (normal.dot(rayDir) > 0f) {
            Vector3(normal).scl(-1f)
        } else {
            Vector3(normal)
        }
    }

    private fun snapToGrid(base: Vector3, normal: Vector3, screenX: Int, screenY: Int): SnapCandidate? {
        val gx = round(base.x / gridSpacing) * gridSpacing
        val gz = round(base.z / gridSpacing) * gridSpacing
        val candidate = Vector3(gx, 0f, gz)
        val dist = screenDistance(candidate, screenX, screenY)
        return if (dist <= snapPixels) {
            SnapCandidate(candidate, Vector3(normal), SnapType.GRID, dist)
        } else {
            null
        }
    }

    private fun snapToLineEndpoints(normal: Vector3, screenX: Int, screenY: Int): SnapCandidate? {
        var best: SnapCandidate? = null
        lineStore.getSegments().forEach { segment ->
            listOf(segment.start, segment.end).forEach { point ->
                val dist = screenDistance(point, screenX, screenY)
                if (dist <= snapPixels) {
                    val candidate = SnapCandidate(Vector3(point), Vector3(normal), SnapType.ENDPOINT, dist)
                    best = pickBetter(best, candidate)
                }
            }
        }
        return best
    }

    private fun snapToLineMidpoints(normal: Vector3, screenX: Int, screenY: Int): SnapCandidate? {
        var best: SnapCandidate? = null
        lineStore.getSegments().forEach { segment ->
            val midpoint = Vector3(segment.start).add(segment.end).scl(0.5f)
            val dist = screenDistance(midpoint, screenX, screenY)
            if (dist <= snapPixels) {
                val candidate = SnapCandidate(midpoint, Vector3(normal), SnapType.MIDPOINT, dist)
                best = pickBetter(best, candidate)
            }
        }
        return best
    }

    private fun snapToLineSegments(base: Vector3, normal: Vector3, screenX: Int, screenY: Int): SnapCandidate? {
        var best: SnapCandidate? = null
        lineStore.getSegments().forEach { segment ->
            val closest = closestPointOnSegment(base, segment.start, segment.end)
            val dist = screenDistance(closest, screenX, screenY)
            if (dist <= snapPixels) {
                val candidate = SnapCandidate(closest, Vector3(normal), SnapType.LINE, dist)
                best = pickBetter(best, candidate)
            }
        }
        return best
    }

    private fun snapToGridLines(base: Vector3, normal: Vector3, screenX: Int, screenY: Int): SnapCandidate? {
        val gx = round(base.x / gridSpacing) * gridSpacing
        val gz = round(base.z / gridSpacing) * gridSpacing
        val candidateX = Vector3(gx, 0f, base.z)
        val candidateZ = Vector3(base.x, 0f, gz)
        val distX = screenDistance(candidateX, screenX, screenY)
        val distZ = screenDistance(candidateZ, screenX, screenY)
        val bestCandidate = if (distX <= distZ) candidateX else candidateZ
        val bestDist = minOf(distX, distZ)
        return if (bestDist <= snapPixels) {
            SnapCandidate(bestCandidate, Vector3(normal), SnapType.GRID_LINE, bestDist)
        } else {
            null
        }
    }

    private fun snapToGridGuides(ray: com.badlogic.gdx.math.collision.Ray, screenX: Int, screenY: Int): SnapCandidate? {
        val candidates = mutableListOf<SnapCandidate>()
        guideManager.getGridGuides().forEach { center ->
            val planes = listOf(
                PlaneGuide(Vector3(0f, 0f, 1f), Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f)),
                PlaneGuide(Vector3(0f, 1f, 0f), Vector3(1f, 0f, 0f), Vector3(0f, 0f, 1f)),
                PlaneGuide(Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f))
            )
            planes.forEach { plane ->
                val hit = intersectPlane(ray, center, plane.normal)
                if (hit != null) {
                    val snapped = snapPointOnPlane(hit.point, center, plane.axisU, plane.axisV)
                    val dist = screenDistance(snapped, screenX, screenY)
                    if (dist <= snapPixels) {
                        val normal = facingNormal(Vector3(plane.normal), ray.direction)
                        candidates.add(SnapCandidate(snapped, normal, SnapType.GRID_GUIDE, dist))
                    }
                }
            }
        }
        return candidates.minByOrNull { it.scoreKey() }
    }

    private fun snapToAxisGuides(
        ray: com.badlogic.gdx.math.collision.Ray,
        baseNormal: Vector3,
        screenX: Int,
        screenY: Int
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        guideManager.getAxisGuides().forEach { center ->
            val extent = gridSpacing * 10f
            val axes = listOf(
                Vector3(1f, 0f, 0f),
                Vector3(0f, 1f, 0f),
                Vector3(0f, 0f, 1f)
            )
            axes.forEach { axis ->
                val result = closestPointRayLine(ray, center, axis)
                if (result != null) {
                    val (point, s) = result
                    if (s >= -extent && s <= extent) {
                        val dist = screenDistance(point, screenX, screenY)
                        if (dist <= snapPixels) {
                            val normal = Vector3(baseNormal)
                            val candidate = SnapCandidate(point, normal, SnapType.AXIS_GUIDE, dist)
                            best = pickBetter(best, candidate)
                        }
                    }
                }
            }
        }
        return best
    }

    private fun intersectPlane(ray: com.badlogic.gdx.math.collision.Ray, point: Vector3, normal: Vector3): PlaneHit? {
        val denom = normal.dot(ray.direction)
        if (abs(denom) < epsilon) {
            return null
        }
        val t = Vector3(point).sub(ray.origin).dot(normal) / denom
        if (t <= 0f) {
            return null
        }
        val hitPoint = Vector3(ray.origin).mulAdd(ray.direction, t)
        val facing = facingNormal(Vector3(normal), ray.direction)
        return PlaneHit(hitPoint, facing, t)
    }

    private fun snapPointOnPlane(point: Vector3, origin: Vector3, axisU: Vector3, axisV: Vector3): Vector3 {
        val local = Vector3(point).sub(origin)
        val u = local.dot(axisU)
        val v = local.dot(axisV)
        val snappedU = round(u / gridSpacing) * gridSpacing
        val snappedV = round(v / gridSpacing) * gridSpacing
        return Vector3(origin)
            .mulAdd(axisU, snappedU)
            .mulAdd(axisV, snappedV)
    }

    private fun closestPointRayLine(
        ray: com.badlogic.gdx.math.collision.Ray,
        linePoint: Vector3,
        lineDir: Vector3
    ): Pair<Vector3, Float>? {
        val p = ray.origin
        val d = ray.direction
        val q = linePoint
        val e = lineDir

        val a = d.dot(d)
        val b = d.dot(e)
        val c = e.dot(e)
        val r = Vector3(p).sub(q)
        val dR = d.dot(r)
        val eR = e.dot(r)
        val denom = a * c - b * b
        if (abs(denom) < epsilon) {
            return null
        }
        val t = (b * eR - c * dR) / denom
        val s = (a * eR - b * dR) / denom
        if (t <= 0f) {
            return null
        }
        val pointOnLine = Vector3(q).mulAdd(e, s)
        return Pair(pointOnLine, s)
    }

    private fun closestPointOnSegment(point: Vector3, a: Vector3, b: Vector3): Vector3 {
        val ab = Vector3(b).sub(a)
        val t = (Vector3(point).sub(a)).dot(ab) / ab.len2()
        val clamped = t.coerceIn(0f, 1f)
        return Vector3(a).mulAdd(ab, clamped)
    }

    private fun screenDistance(world: Vector3, screenX: Int, screenY: Int): Float {
        val projected = camera.project(tmp.set(world))
        val dx = projected.x - screenX
        val dy = (Gdx.graphics.height - projected.y) - screenY
        return sqrt(dx * dx + dy * dy)
    }

    private fun pickBetter(current: SnapCandidate?, next: SnapCandidate): SnapCandidate {
        if (current == null) {
            return next
        }
        val currentKey = current.scoreKey()
        val nextKey = next.scoreKey()
        return if (nextKey < currentKey) next else current
    }

    private fun SnapCandidate.scoreKey(): Float {
        return (1000 - type.priority * 100).toFloat() + distance
    }

    private data class SnapCandidate(
        val world: Vector3,
        val normal: Vector3,
        val type: SnapType,
        val distance: Float
    )

    private data class PlaneGuide(
        val normal: Vector3,
        val axisU: Vector3,
        val axisV: Vector3
    )

    private data class PlaneHit(
        val point: Vector3,
        val normal: Vector3,
        val t: Float
    )
}

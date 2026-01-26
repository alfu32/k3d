package com.github.alfu32.sketch.input

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

class Snapper(
    private val camera: Camera,
    private val scene: GroupScene,
    private val guideManager: GuideManager,
    initialGridSpacing: Float = 1f,
    snapPixels: Float = 12f
) {
    private val tmp = Vector3()
    private val epsilon = 1e-1f
    var snapPixels: Float = snapPixels
    var gridSpacing: Float = initialGridSpacing
        set(value) {
            field = value.coerceAtLeast(1e-4f)
        }

    fun compute(screenX: Int, screenY: Int): SnapResult {
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        val group = scene.activeGroup()
        val localRay = com.badlogic.gdx.math.collision.Ray(
            group.toLocal(ray.origin),
            group.vectorToLocal(ray.direction).nor()
        )
        val baseHit = pickBaseHit(ray)
        val basePoint = baseHit?.point
        val baseNormal = baseHit?.normal
        var best: SnapCandidate? = null

        group.faceStore.pickTriangle(localRay)?.let { hit ->
            val worldPoint = group.toWorld(hit.point)
            val faceNormal = facingNormal(group.vectorToWorld(hit.normal), ray.direction)
            val t = rayT(ray, worldPoint) ?: return@let
            val candidate = SnapCandidate(worldPoint, faceNormal, SnapType.FACE, 0f, t, SnapSource.FACE)
            best = pickBetter(best, candidate)
        }

        if (basePoint != null && baseNormal != null) {
            snapToGrid(basePoint, baseNormal, screenX, screenY, ray)?.let {
                best = pickBetter(best, it)
            }

            snapToLineEndpoints(group, baseNormal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            snapToLineMidpoints(group, baseNormal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            snapToLineEndpointsAllGroups(baseNormal, screenX, screenY, ray, group)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            snapToLineMidpointsAllGroups(baseNormal, screenX, screenY, ray, group)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            snapToGridLines(basePoint, baseNormal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            snapToLineSegments(group, basePoint, baseNormal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
        } else {
            val fallbackNormal = facingNormal(Vector3(0f, 1f, 0f), ray.direction)
            snapToLineEndpointsAllGroups(fallbackNormal, screenX, screenY, ray, group)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
            snapToLineMidpointsAllGroups(fallbackNormal, screenX, screenY, ray, group)?.let { candidate ->
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
            return if (basePoint != null && baseNormal != null) {
                SnapResult(basePoint, baseNormal, SnapType.NONE, screenX, screenY, true)
            } else {
                SnapResult(null, null, SnapType.NONE, screenX, screenY, false)
            }
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
        for (guide in guides) {
            val planes = listOf(
                PlaneGuide(Vector3(guide.axisW), Vector3(guide.axisU), Vector3(guide.axisV)),
                PlaneGuide(Vector3(guide.axisV), Vector3(guide.axisU), Vector3(guide.axisW)),
                PlaneGuide(Vector3(guide.axisU), Vector3(guide.axisV), Vector3(guide.axisW))
            )
            for (plane in planes) {
                val hit = intersectPlane(ray, guide.origin, plane.normal)
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

    private fun snapToGrid(base: Vector3, normal: Vector3, screenX: Int, screenY: Int, ray: com.badlogic.gdx.math.collision.Ray): SnapCandidate? {
        val gx = round(base.x / gridSpacing) * gridSpacing
        val gz = round(base.z / gridSpacing) * gridSpacing
        val candidate = Vector3(gx, 0f, gz)
        val dist = screenDistance(candidate, screenX, screenY)
        val t = rayT(ray, candidate) ?: return null
        return if (dist <= snapPixels) {
            SnapCandidate(candidate, Vector3(normal), SnapType.GRID, dist, t, SnapSource.GRID)
        } else {
            null
        }
    }

    private fun snapToLineEndpoints(
        group: GroupScene.GroupNode,
        normal: Vector3,
        screenX: Int,
        screenY: Int,
        ray: com.badlogic.gdx.math.collision.Ray
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        group.lineStore.getSegments().forEach { segment ->
            listOf(segment.start, segment.end).forEach { local ->
                val point = group.toWorld(local)
                val dist = screenDistance(point, screenX, screenY)
                if (dist <= snapPixels) {
                    val t = rayT(ray, point) ?: return@forEach
                    val candidate = SnapCandidate(Vector3(point), Vector3(normal), SnapType.ENDPOINT, dist, t, SnapSource.LINE_ENDPOINT)
                    best = pickBetter(best, candidate)
                }
            }
        }
        return best
    }

    private fun snapToLineMidpoints(
        group: GroupScene.GroupNode,
        normal: Vector3,
        screenX: Int,
        screenY: Int,
        ray: com.badlogic.gdx.math.collision.Ray
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        group.lineStore.getSegments().forEach { segment ->
            val midpointLocal = Vector3(segment.start).add(segment.end).scl(0.5f)
            val midpoint = group.toWorld(midpointLocal)
            val dist = screenDistance(midpoint, screenX, screenY)
            if (dist <= snapPixels) {
                val t = rayT(ray, midpoint) ?: return@forEach
                val candidate = SnapCandidate(midpoint, Vector3(normal), SnapType.MIDPOINT, dist, t, SnapSource.LINE_MIDPOINT)
                best = pickBetter(best, candidate)
            }
        }
        return best
    }

    private fun snapToLineEndpointsAllGroups(
        normal: Vector3,
        screenX: Int,
        screenY: Int,
        ray: com.badlogic.gdx.math.collision.Ray,
        activeGroup: GroupScene.GroupNode
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        val root = scene.root
        if (activeGroup !== root) {
            root.lineStore.getSegments().forEach { segment ->
                listOf(segment.start, segment.end).forEach { point ->
                    val dist = screenDistance(point, screenX, screenY)
                    if (dist <= snapPixels) {
                        val t = rayT(ray, point) ?: return@forEach
                        val candidate = SnapCandidate(Vector3(point), Vector3(normal), SnapType.ENDPOINT, dist, t, SnapSource.LINE_ENDPOINT)
                        best = pickBetter(best, candidate)
                    }
                }
            }
        }
        scene.walkGroups(root) { group ->
            if (group === activeGroup) {
                return@walkGroups
            }
            group.lineStore.getSegments().forEach { segment ->
                listOf(segment.start, segment.end).forEach { local ->
                    val point = group.toWorld(local)
                    val dist = screenDistance(point, screenX, screenY)
                    if (dist <= snapPixels) {
                        val t = rayT(ray, point) ?: return@forEach
                        val candidate = SnapCandidate(Vector3(point), Vector3(normal), SnapType.ENDPOINT, dist, t, SnapSource.LINE_ENDPOINT)
                        best = pickBetter(best, candidate)
                    }
                }
            }
        }
        return best
    }

    private fun snapToLineMidpointsAllGroups(
        normal: Vector3,
        screenX: Int,
        screenY: Int,
        ray: com.badlogic.gdx.math.collision.Ray,
        activeGroup: GroupScene.GroupNode
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        val root = scene.root
        if (activeGroup !== root) {
            root.lineStore.getSegments().forEach { segment ->
                val midpoint = Vector3(segment.start).add(segment.end).scl(0.5f)
                val dist = screenDistance(midpoint, screenX, screenY)
                if (dist <= snapPixels) {
                    val t = rayT(ray, midpoint) ?: return@forEach
                    val candidate = SnapCandidate(midpoint, Vector3(normal), SnapType.MIDPOINT, dist, t, SnapSource.LINE_MIDPOINT)
                    best = pickBetter(best, candidate)
                }
            }
        }
        scene.walkGroups(root) { group ->
            if (group === activeGroup) {
                return@walkGroups
            }
            group.lineStore.getSegments().forEach { segment ->
                val midpointLocal = Vector3(segment.start).add(segment.end).scl(0.5f)
                val midpoint = group.toWorld(midpointLocal)
                val dist = screenDistance(midpoint, screenX, screenY)
                if (dist <= snapPixels) {
                    val t = rayT(ray, midpoint) ?: return@forEach
                    val candidate = SnapCandidate(midpoint, Vector3(normal), SnapType.MIDPOINT, dist, t, SnapSource.LINE_MIDPOINT)
                    best = pickBetter(best, candidate)
                }
            }
        }
        return best
    }

    private fun snapToLineSegments(
        group: GroupScene.GroupNode,
        base: Vector3,
        normal: Vector3,
        screenX: Int,
        screenY: Int,
        ray: com.badlogic.gdx.math.collision.Ray
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        group.lineStore.getSegments().forEach { segment ->
            val start = group.toWorld(segment.start)
            val end = group.toWorld(segment.end)
            val closest = closestPointOnSegment(base, start, end)
            val dist = screenDistance(closest, screenX, screenY)
            if (dist <= snapPixels) {
                val t = rayT(ray, closest) ?: return@forEach
                val candidate = SnapCandidate(closest, Vector3(normal), SnapType.LINE, dist, t, SnapSource.LINE_SEGMENT)
                best = pickBetter(best, candidate)
            }
        }
        return best
    }

    private fun snapToGridLines(base: Vector3, normal: Vector3, screenX: Int, screenY: Int, ray: com.badlogic.gdx.math.collision.Ray): SnapCandidate? {
        val gx = round(base.x / gridSpacing) * gridSpacing
        val gz = round(base.z / gridSpacing) * gridSpacing
        val candidateX = Vector3(gx, 0f, base.z)
        val candidateZ = Vector3(base.x, 0f, gz)
        val distX = screenDistance(candidateX, screenX, screenY)
        val distZ = screenDistance(candidateZ, screenX, screenY)
        val bestCandidate = if (distX <= distZ) candidateX else candidateZ
        val bestDist = minOf(distX, distZ)
        val t = rayT(ray, bestCandidate) ?: return null
        return if (bestDist <= snapPixels) {
            SnapCandidate(bestCandidate, Vector3(normal), SnapType.GRID_LINE, bestDist, t, SnapSource.GRID_LINE)
        } else {
            null
        }
    }

    private fun snapToGridGuides(ray: com.badlogic.gdx.math.collision.Ray, screenX: Int, screenY: Int): SnapCandidate? {
        var best: SnapCandidate? = null
        var bestT = Float.POSITIVE_INFINITY
        guideManager.getGridGuides().forEach { guide ->
            val planes = listOf(
                PlaneGuide(Vector3(guide.axisW), Vector3(guide.axisU), Vector3(guide.axisV)),
                PlaneGuide(Vector3(guide.axisV), Vector3(guide.axisU), Vector3(guide.axisW)),
                PlaneGuide(Vector3(guide.axisU), Vector3(guide.axisV), Vector3(guide.axisW))
            )
            planes.forEach { plane ->
                val hit = intersectPlane(ray, guide.origin, plane.normal)
                if (hit != null) {
                    val snapped = snapPointOnPlane(hit.point, guide.origin, plane.axisU, plane.axisV)
                    val dist = screenDistance(snapped, screenX, screenY)
                    if (dist <= snapPixels) {
                        val normal = facingNormal(Vector3(plane.normal), ray.direction)
                        val candidate = SnapCandidate(snapped, normal, SnapType.GRID_GUIDE, dist, hit.t, SnapSource.GRID_GUIDE)
                        if (hit.t < bestT - epsilon) {
                            bestT = hit.t
                            best = candidate
                        } else if (kotlin.math.abs(hit.t - bestT) <= epsilon) {
                            best = pickBetter(best, candidate)
                        }
                    }
                }
            }
        }
        return best
    }

    private fun snapToAxisGuides(
        ray: com.badlogic.gdx.math.collision.Ray,
        baseNormal: Vector3,
        screenX: Int,
        screenY: Int
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        guideManager.getAxisGuides().forEach { guide ->
            val extent = gridSpacing * 10f
            val axes = listOf(
                Vector3(guide.axisU),
                Vector3(guide.axisV),
                Vector3(guide.axisW)
            )
            axes.forEach { axis ->
                val result = closestPointRayLine(ray, guide.origin, axis)
                if (result != null) {
                    val (point, s, tRay) = result
                    val snappedS = round(s / gridSpacing) * gridSpacing
                    if (snappedS >= -extent && snappedS <= extent) {
                        val snappedPoint = Vector3(guide.origin).mulAdd(axis, snappedS)
                        val dist = screenDistance(snappedPoint, screenX, screenY)
                        if (dist <= snapPixels) {
                            val normal = Vector3(baseNormal)
                            val t = rayT(ray, snappedPoint) ?: return@forEach
                            val candidate = SnapCandidate(snappedPoint, normal, SnapType.AXIS_GUIDE, dist, t, SnapSource.AXIS_GUIDE)
                            best = pickBetter(best, candidate)
                            return@forEach
                        }
                    }
                    if (s >= -extent && s <= extent) {
                        val dist = screenDistance(point, screenX, screenY)
                        if (dist <= snapPixels) {
                            val normal = Vector3(baseNormal)
                            val t = rayT(ray, point) ?: return@forEach
                            val candidate = SnapCandidate(point, normal, SnapType.AXIS_GUIDE, dist, t, SnapSource.AXIS_GUIDE)
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
    ): Triple<Vector3, Float, Float>? {
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
        return Triple(pointOnLine, s, t)
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
        return if (next.t + epsilon < current.t) next else current
    }

    fun collectSelectionPoints(): List<SelectionPoint> {
        val points = mutableListOf<SelectionPoint>()
        val group = scene.activeGroup()
        group.lineStore.getSegments().forEach { segment ->
            val start = group.toWorld(segment.start)
            val end = group.toWorld(segment.end)
            points.add(SelectionPoint(projectScreen(start), Vector3(start), null, SelectionSource.EDGE))
            points.add(SelectionPoint(projectScreen(end), Vector3(end), null, SelectionSource.EDGE))
        }
        group.faceStore.getTriangles().forEach { tri ->
            val a = group.toWorld(tri.a)
            val b = group.toWorld(tri.b)
            val c = group.toWorld(tri.c)
            val centroid = Vector3(a).add(b).add(c).scl(1f / 3f)
            val normal = Vector3(b).sub(a).crs(Vector3(c).sub(a)).nor()
            points.add(SelectionPoint(projectScreen(centroid), centroid, normal, SelectionSource.FACE))
        }
        return points
    }

    private fun projectScreen(world: Vector3): Vector2 {
        val projected = camera.project(tmp.set(world))
        return Vector2(projected.x, Gdx.graphics.height - projected.y)
    }

    private fun SnapCandidate.scoreKey(): Float {
        val sourceWeight = (1000 - source.priority * 100).toFloat()
        val typeWeight = (100 - type.priority) * 0.1f
        return sourceWeight + typeWeight + distance
    }

    private data class SnapCandidate(
        val world: Vector3,
        val normal: Vector3,
        val type: SnapType,
        val distance: Float,
        val t: Float,
        val source: SnapSource
    )

    private data class PlaneGuide(
        val normal: Vector3,
        val axisU: Vector3,
        val axisV: Vector3
    )

    private enum class SnapSource(val priority: Int) {
        LINE_ENDPOINT(6),
        LINE_MIDPOINT(5),
        LINE_SEGMENT(4),
        GRID(3),
        GRID_LINE(2),
        GRID_GUIDE(2),
        AXIS_GUIDE(2),
        FACE(1)
    }

    data class SelectionPoint(
        val screen: Vector2,
        val world: Vector3,
        val normal: Vector3?,
        val source: SelectionSource
    )

    enum class SelectionSource {
        EDGE,
        FACE
    }

    private fun rayT(ray: com.badlogic.gdx.math.collision.Ray, point: Vector3): Float? {
        val t = Vector3(point).sub(ray.origin).dot(ray.direction)
        return if (t > 0f) t else null
    }
    private data class PlaneHit(
        val point: Vector3,
        val normal: Vector3,
        val t: Float
    )
}

package com.github.alfu32.sketch.input

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.GroupScene
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

class Snapper(
    private var camera: Camera,
    private val scene: GroupScene,
    private val guideManager: GuideManager,
    private val defaultGridPlaneProvider: () -> GuideManager.GuideBasis = {
        GuideManager.GuideBasis(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(0f, 0f, 1f),
            Vector3(0f, 1f, 0f)
        )
    },
    private val lineSnapVisible: ((GroupScene.GroupNode, DraftLineStore.Segment) -> Boolean)? = null,
    private val faceSnapVisible: ((GroupScene.GroupNode, DraftFaceStore.Triangle) -> Boolean)? = null,
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

    fun setCamera(camera: Camera) {
        this.camera = camera
    }

    private fun isLineSnapVisible(group: GroupScene.GroupNode, segment: DraftLineStore.Segment): Boolean {
        return lineSnapVisible?.invoke(group, segment) ?: true
    }

    private fun isFaceSnapVisible(group: GroupScene.GroupNode, triangle: DraftFaceStore.Triangle): Boolean {
        return faceSnapVisible?.invoke(group, triangle) ?: true
    }

    fun compute(screenX: Int, screenY: Int): SnapResult {
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        val group = scene.activeGroup()
        val localRay = com.badlogic.gdx.math.collision.Ray(
            group.toLocal(ray.origin),
            group.vectorToLocal(ray.direction).nor()
        )
        val baseHit = pickBaseHit(ray)
        var basePoint = baseHit?.point
        var baseNormal = baseHit?.normal
        var baseT = baseHit?.t ?: Float.POSITIVE_INFINITY
        var best: SnapCandidate? = null
        var faceReferencePoint: Vector3? = null

        group.faceStore.pickTriangle(localRay) { tri -> isFaceSnapVisible(group, tri) }?.let { hit ->
            val worldPoint = group.toWorld(hit.point)
            val faceNormal = facingNormal(group.vectorToWorld(hit.normal), ray.direction)
            val t = rayT(ray, worldPoint) ?: return@let
            val candidate = SnapCandidate(worldPoint, faceNormal, SnapType.FACE, 0f, t, SnapSource.FACE)
            best = pickBetter(best, candidate)
            faceReferencePoint = worldPoint
            basePoint = Vector3(worldPoint)
            baseNormal = Vector3(faceNormal)
            baseT = t
        }

        // Also allow snapping to visible faces of non-active groups.
        snapToFacesAllGroups(ray, group)?.let { candidate ->
            best = pickBetter(best, candidate)
            if (faceReferencePoint == null) {
                faceReferencePoint = Vector3(candidate.world)
            }
            if (candidate.t < baseT) {
                basePoint = Vector3(candidate.world)
                baseNormal = Vector3(candidate.normal)
                baseT = candidate.t
            }
        }

        if (basePoint != null && baseNormal != null) {
            snapToGrid(basePoint, baseNormal, screenX, screenY, ray)?.let {
                best = pickBetter(best, it)
            }

            snapToLineEndpoints(group, baseNormal, screenX, screenY, ray, localRay)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            snapToLineMidpoints(group, baseNormal, screenX, screenY, ray, localRay)?.let { candidate ->
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

            snapToLineSegments(group, basePoint, baseNormal, screenX, screenY, ray, localRay)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            val ref = faceReferencePoint ?: basePoint
            snapToFaceEdges(group, ref, baseNormal, screenX, screenY, ray, localRay)?.let { candidate ->
                best = pickBetter(best, candidate)
            }

            snapToFaceEdgesAllGroups(group, ref, baseNormal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
        } else {
            val fallbackNormal = facingNormal(Vector3(defaultGridPlaneProvider().axisW), ray.direction)
            snapToLineEndpointsAllGroups(fallbackNormal, screenX, screenY, ray, group)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
            snapToLineMidpointsAllGroups(fallbackNormal, screenX, screenY, ray, group)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
            val ref = faceReferencePoint
            if (ref != null) {
                snapToFaceEdges(group, ref, fallbackNormal, screenX, screenY, ray, localRay)?.let { candidate ->
                    best = pickBetter(best, candidate)
                }
                snapToFaceEdgesAllGroups(group, ref, fallbackNormal, screenX, screenY, ray)?.let { candidate ->
                    best = pickBetter(best, candidate)
                }
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

    private fun intersectDefaultGridPlane(ray: com.badlogic.gdx.math.collision.Ray): PlaneHit? {
        val plane = defaultGridPlaneProvider()
        return intersectPlane(ray, plane.origin, plane.axisW)
            ?: intersectOrthographicViewPlane(ray, plane.origin)
    }

    private fun intersectOrthographicViewPlane(ray: com.badlogic.gdx.math.collision.Ray, origin: Vector3): PlaneHit? {
        val ortho = camera as? OrthographicCamera ?: return null
        val normal = Vector3(ortho.direction)
        if (normal.len2() <= 1e-8f) {
            return null
        }
        return intersectPlane(ray, origin, normal.nor())
    }

    private fun pickBaseHit(ray: com.badlogic.gdx.math.collision.Ray): PlaneHit? {
        var best = intersectDefaultGridPlane(ray)
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
        val basis = buildPlaneGridBasis(base, normal) ?: return null
        val coords = planeCoordinates(base, basis.origin, basis.axisU, basis.axisV)
        val candidate = pointOnPlane(basis.origin, basis.axisU, basis.axisV, coords.snappedU, coords.snappedV)
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
        ray: com.badlogic.gdx.math.collision.Ray,
        localRay: com.badlogic.gdx.math.collision.Ray
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        group.lineStore.forEachRayCandidate(localRay) { segment ->
            if (!isLineSnapVisible(group, segment)) return@forEachRayCandidate true
            val start = group.toWorld(segment.start)
            var dist = screenDistance(start, screenX, screenY)
            if (dist <= snapPixels) {
                val t = rayT(ray, start) ?: return@forEachRayCandidate true
                val candidate = SnapCandidate(Vector3(start), Vector3(normal), SnapType.ENDPOINT, dist, t, SnapSource.LINE_ENDPOINT)
                best = pickBetter(best, candidate)
            }
            val end = group.toWorld(segment.end)
            dist = screenDistance(end, screenX, screenY)
            if (dist <= snapPixels) {
                val t = rayT(ray, end) ?: return@forEachRayCandidate true
                val candidate = SnapCandidate(Vector3(end), Vector3(normal), SnapType.ENDPOINT, dist, t, SnapSource.LINE_ENDPOINT)
                best = pickBetter(best, candidate)
            }
            true
        }
        return best
    }

    private fun snapToLineMidpoints(
        group: GroupScene.GroupNode,
        normal: Vector3,
        screenX: Int,
        screenY: Int,
        ray: com.badlogic.gdx.math.collision.Ray,
        localRay: com.badlogic.gdx.math.collision.Ray
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        group.lineStore.forEachRayCandidate(localRay) { segment ->
            if (!isLineSnapVisible(group, segment)) return@forEachRayCandidate true
            val midpointLocal = Vector3(segment.start).add(segment.end).scl(0.5f)
            val midpoint = group.toWorld(midpointLocal)
            val dist = screenDistance(midpoint, screenX, screenY)
            if (dist <= snapPixels) {
                val t = rayT(ray, midpoint) ?: return@forEachRayCandidate true
                val candidate = SnapCandidate(midpoint, Vector3(normal), SnapType.MIDPOINT, dist, t, SnapSource.LINE_MIDPOINT)
                best = pickBetter(best, candidate)
            }
            true
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
        scene.queryGroupsByRay(ray, includeRoot = true).forEach { group ->
            if (group === activeGroup) {
                return@forEach
            }
            val localRay = if (group === scene.root) ray else com.badlogic.gdx.math.collision.Ray(
                group.toLocal(ray.origin),
                group.vectorToLocal(ray.direction).nor()
            )
            group.lineStore.forEachRayCandidate(localRay) { segment ->
                if (!isLineSnapVisible(group, segment)) return@forEachRayCandidate true
                val start = if (group === scene.root) Vector3(segment.start) else group.toWorld(segment.start)
                var dist = screenDistance(start, screenX, screenY)
                if (dist <= snapPixels) {
                    val t = rayT(ray, start) ?: return@forEachRayCandidate true
                    val candidate = SnapCandidate(Vector3(start), Vector3(normal), SnapType.ENDPOINT, dist, t, SnapSource.LINE_ENDPOINT)
                    best = pickBetter(best, candidate)
                }
                val end = if (group === scene.root) Vector3(segment.end) else group.toWorld(segment.end)
                dist = screenDistance(end, screenX, screenY)
                if (dist <= snapPixels) {
                    val t = rayT(ray, end) ?: return@forEachRayCandidate true
                    val candidate = SnapCandidate(Vector3(end), Vector3(normal), SnapType.ENDPOINT, dist, t, SnapSource.LINE_ENDPOINT)
                    best = pickBetter(best, candidate)
                }
                true
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
        scene.queryGroupsByRay(ray, includeRoot = true).forEach { group ->
            if (group === activeGroup) {
                return@forEach
            }
            val localRay = if (group === scene.root) ray else com.badlogic.gdx.math.collision.Ray(
                group.toLocal(ray.origin),
                group.vectorToLocal(ray.direction).nor()
            )
            group.lineStore.forEachRayCandidate(localRay) { segment ->
                if (!isLineSnapVisible(group, segment)) return@forEachRayCandidate true
                val midpointLocal = Vector3(segment.start).add(segment.end).scl(0.5f)
                val midpoint = if (group === scene.root) midpointLocal else group.toWorld(midpointLocal)
                val dist = screenDistance(midpoint, screenX, screenY)
                if (dist <= snapPixels) {
                    val t = rayT(ray, midpoint) ?: return@forEachRayCandidate true
                    val candidate = SnapCandidate(midpoint, Vector3(normal), SnapType.MIDPOINT, dist, t, SnapSource.LINE_MIDPOINT)
                    best = pickBetter(best, candidate)
                }
                true
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
        ray: com.badlogic.gdx.math.collision.Ray,
        localRay: com.badlogic.gdx.math.collision.Ray
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        group.lineStore.forEachRayCandidate(localRay) { segment ->
            if (!isLineSnapVisible(group, segment)) return@forEachRayCandidate true
            val start = group.toWorld(segment.start)
            val end = group.toWorld(segment.end)
            val closest = closestPointOnSegment(base, start, end)
            val dist = screenDistance(closest, screenX, screenY)
            if (dist <= snapPixels) {
                val t = rayT(ray, closest) ?: return@forEachRayCandidate true
                val candidate = SnapCandidate(closest, Vector3(normal), SnapType.LINE, dist, t, SnapSource.LINE_SEGMENT)
                best = pickBetter(best, candidate)
            }
            true
        }
        return best
    }

    private fun snapToFacesAllGroups(
        ray: com.badlogic.gdx.math.collision.Ray,
        activeGroup: GroupScene.GroupNode
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        scene.queryGroupsByRay(ray, includeRoot = true).forEach { group ->
            if (group === activeGroup) {
                return@forEach
            }
            val localRay = if (group === scene.root) ray else com.badlogic.gdx.math.collision.Ray(
                group.toLocal(ray.origin),
                group.vectorToLocal(ray.direction).nor()
            )
            group.faceStore.pickTriangle(localRay) { tri -> isFaceSnapVisible(group, tri) }?.let { hit ->
                val worldPoint = if (group === scene.root) Vector3(hit.point) else group.toWorld(hit.point)
                val faceNormal = if (group === scene.root) facingNormal(Vector3(hit.normal), ray.direction) else facingNormal(group.vectorToWorld(hit.normal), ray.direction)
                val t = rayT(ray, worldPoint) ?: return@let
                val candidate = SnapCandidate(worldPoint, faceNormal, SnapType.FACE, 0f, t, SnapSource.FACE)
                best = pickBetter(best, candidate)
            }
        }
        return best
    }

    private fun snapToFaceEdges(
        group: GroupScene.GroupNode,
        reference: Vector3,
        normal: Vector3,
        screenX: Int,
        screenY: Int,
        ray: com.badlogic.gdx.math.collision.Ray,
        localRay: com.badlogic.gdx.math.collision.Ray
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        group.faceStore.forEachRayCandidate(localRay) { tri ->
            if (!isFaceSnapVisible(group, tri)) return@forEachRayCandidate true
            val a = group.toWorld(tri.a)
            val b = group.toWorld(tri.b)
            val c = group.toWorld(tri.c)
            snapEdgeMidpointCandidate(a, b, normal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
            snapEdgeMidpointCandidate(b, c, normal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
            snapEdgeMidpointCandidate(c, a, normal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
            snapEdgeCandidate(reference, a, b, normal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
            snapEdgeCandidate(reference, b, c, normal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
            snapEdgeCandidate(reference, c, a, normal, screenX, screenY, ray)?.let { candidate ->
                best = pickBetter(best, candidate)
            }
            true
        }
        return best
    }

    private fun snapToFaceEdgesAllGroups(
        activeGroup: GroupScene.GroupNode,
        reference: Vector3,
        normal: Vector3,
        screenX: Int,
        screenY: Int,
        ray: com.badlogic.gdx.math.collision.Ray
    ): SnapCandidate? {
        var best: SnapCandidate? = null
        scene.queryGroupsByRay(ray, includeRoot = true).forEach { group ->
            if (group === activeGroup) {
                return@forEach
            }
            val localRay = if (group === scene.root) ray else com.badlogic.gdx.math.collision.Ray(
                group.toLocal(ray.origin),
                group.vectorToLocal(ray.direction).nor()
            )
            group.faceStore.forEachRayCandidate(localRay) { tri ->
                if (!isFaceSnapVisible(group, tri)) return@forEachRayCandidate true
                val a = if (group === scene.root) Vector3(tri.a) else group.toWorld(tri.a)
                val b = if (group === scene.root) Vector3(tri.b) else group.toWorld(tri.b)
                val c = if (group === scene.root) Vector3(tri.c) else group.toWorld(tri.c)
                snapEdgeMidpointCandidate(a, b, normal, screenX, screenY, ray)?.let { candidate ->
                    best = pickBetter(best, candidate)
                }
                snapEdgeMidpointCandidate(b, c, normal, screenX, screenY, ray)?.let { candidate ->
                    best = pickBetter(best, candidate)
                }
                snapEdgeMidpointCandidate(c, a, normal, screenX, screenY, ray)?.let { candidate ->
                    best = pickBetter(best, candidate)
                }
                snapEdgeCandidate(reference, a, b, normal, screenX, screenY, ray)?.let { candidate ->
                    best = pickBetter(best, candidate)
                }
                snapEdgeCandidate(reference, b, c, normal, screenX, screenY, ray)?.let { candidate ->
                    best = pickBetter(best, candidate)
                }
                snapEdgeCandidate(reference, c, a, normal, screenX, screenY, ray)?.let { candidate ->
                    best = pickBetter(best, candidate)
                }
                true
            }
        }
        return best
    }

    private fun snapEdgeCandidate(
        reference: Vector3,
        a: Vector3,
        b: Vector3,
        normal: Vector3,
        screenX: Int,
        screenY: Int,
        ray: com.badlogic.gdx.math.collision.Ray
    ): SnapCandidate? {
        val closest = closestPointOnSegment(reference, a, b)
        val dist = screenDistance(closest, screenX, screenY)
        if (dist > snapPixels) {
            return null
        }
        val t = rayT(ray, closest) ?: return null
        return SnapCandidate(closest, Vector3(normal), SnapType.LINE, dist, t, SnapSource.FACE_EDGE)
    }

    private fun snapEdgeMidpointCandidate(
        a: Vector3,
        b: Vector3,
        normal: Vector3,
        screenX: Int,
        screenY: Int,
        ray: com.badlogic.gdx.math.collision.Ray
    ): SnapCandidate? {
        val midpoint = Vector3(a).add(b).scl(0.5f)
        val dist = screenDistance(midpoint, screenX, screenY)
        if (dist > snapPixels) {
            return null
        }
        val t = rayT(ray, midpoint) ?: return null
        return SnapCandidate(midpoint, Vector3(normal), SnapType.MIDPOINT, dist, t, SnapSource.FACE_EDGE_MIDPOINT)
    }

    private fun snapToGridLines(base: Vector3, normal: Vector3, screenX: Int, screenY: Int, ray: com.badlogic.gdx.math.collision.Ray): SnapCandidate? {
        val basis = buildPlaneGridBasis(base, normal) ?: return null
        val coords = planeCoordinates(base, basis.origin, basis.axisU, basis.axisV)
        val candidateU = pointOnPlane(basis.origin, basis.axisU, basis.axisV, coords.snappedU, coords.v)
        val candidateV = pointOnPlane(basis.origin, basis.axisU, basis.axisV, coords.u, coords.snappedV)
        val distU = screenDistance(candidateU, screenX, screenY)
        val distV = screenDistance(candidateV, screenX, screenY)
        val bestCandidate = if (distU <= distV) candidateU else candidateV
        val bestDist = minOf(distU, distV)
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
                    val coords = planeCoordinates(hit.point, guide.origin, plane.axisU, plane.axisV)
                    val snapped = pointOnPlane(guide.origin, plane.axisU, plane.axisV, coords.snappedU, coords.snappedV)
                    val supported = isSupportedGuidePoint(snapped)
                    val normal = facingNormal(Vector3(plane.normal), ray.direction)
                    if (supported) {
                        val dist = screenDistance(snapped, screenX, screenY)
                        if (dist <= snapPixels) {
                            val t = rayT(ray, snapped) ?: hit.t
                            val candidate = SnapCandidate(snapped, normal, SnapType.GRID_GUIDE, dist, t, SnapSource.GRID_GUIDE)
                            if (t < bestT - epsilon) {
                                bestT = t
                                best = candidate
                            } else if (kotlin.math.abs(t - bestT) <= epsilon) {
                                best = pickBetter(best, candidate)
                            }
                        }
                    } else {
                        val lineU = pointOnPlane(guide.origin, plane.axisU, plane.axisV, coords.snappedU, coords.v)
                        val lineV = pointOnPlane(guide.origin, plane.axisU, plane.axisV, coords.u, coords.snappedV)
                        val distU = screenDistance(lineU, screenX, screenY)
                        val distV = screenDistance(lineV, screenX, screenY)
                        val (linePoint, lineDist) = if (distU <= distV) {
                            lineU to distU
                        } else {
                            lineV to distV
                        }
                        if (lineDist <= snapPixels) {
                            val t = rayT(ray, linePoint) ?: hit.t
                            val candidate = SnapCandidate(linePoint, normal, SnapType.GRID_LINE, lineDist, t, SnapSource.GRID_LINE)
                            if (t < bestT - epsilon) {
                                bestT = t
                                best = candidate
                            } else if (kotlin.math.abs(t - bestT) <= epsilon) {
                                best = pickBetter(best, candidate)
                            }
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
        val lineT = Vector3(point).sub(ray.origin).dot(normal) / denom
        if (!lineT.isFinite()) {
            return null
        }
        if (camera !is OrthographicCamera && lineT <= 0f) {
            return null
        }
        val hitPoint = Vector3(ray.origin).mulAdd(ray.direction, lineT)
        val t = rayT(ray, hitPoint) ?: return null
        val facing = facingNormal(Vector3(normal), ray.direction)
        return PlaneHit(hitPoint, facing, t)
    }

    private fun planeCoordinates(point: Vector3, origin: Vector3, axisU: Vector3, axisV: Vector3): PlaneCoordinates {
        val local = Vector3(point).sub(origin)
        val u = local.dot(axisU)
        val v = local.dot(axisV)
        val snappedU = round(u / gridSpacing) * gridSpacing
        val snappedV = round(v / gridSpacing) * gridSpacing
        return PlaneCoordinates(u, v, snappedU, snappedV)
    }

    private fun buildPlaneGridBasis(base: Vector3, normal: Vector3): PlaneGridBasis? {
        val n = Vector3(normal)
        if (n.len2() <= 1e-8f) {
            return null
        }
        n.nor()
        val ref = if (abs(n.y) < 0.95f) Vector3(0f, 1f, 0f) else Vector3(1f, 0f, 0f)
        var axisU = Vector3(ref).crs(n)
        if (axisU.len2() <= 1e-8f) {
            axisU = Vector3(0f, 0f, 1f).crs(n)
        }
        if (axisU.len2() <= 1e-8f) {
            return null
        }
        axisU.nor()
        val axisV = Vector3(n).crs(axisU).nor()
        val origin = Vector3(n).scl(base.dot(n))
        return PlaneGridBasis(origin, axisU, axisV)
    }

    private fun pointOnPlane(origin: Vector3, axisU: Vector3, axisV: Vector3, u: Float, v: Float): Vector3 {
        return Vector3(origin)
            .mulAdd(axisU, u)
            .mulAdd(axisV, v)
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
        val strongerPriorityDepthTolerance = max(1.5f, gridSpacing * 2f)
        if (next.source.priority != current.source.priority) {
            val stronger = if (next.source.priority > current.source.priority) next else current
            val weaker = if (stronger === next) current else next
            if (stronger.t <= weaker.t + strongerPriorityDepthTolerance) {
                return stronger
            }
        }
        val depthTolerance = 0.35f
        if (next.t + depthTolerance < current.t) {
            return next
        }
        if (current.t + depthTolerance < next.t) {
            return current
        }
        val nextScore = next.scoreKey() + next.t * 0.02f
        val currentScore = current.scoreKey() + current.t * 0.02f
        return if (nextScore + 1e-4f < currentScore) next else current
    }

    private fun isSupportedGuidePoint(point: Vector3): Boolean {
        val plane = defaultGridPlaneProvider()
        val gridPlaneTolerance = max(1e-3f, gridSpacing * 0.05f)
        val planeDistance = abs(Vector3(point).sub(plane.origin).dot(plane.axisW))
        if (planeDistance <= gridPlaneTolerance) {
            return true
        }
        val featureTolerance = max(5e-2f, gridSpacing * 0.15f)
        if (isNearVisibleLine(point, featureTolerance)) {
            return true
        }
        return isOnVisibleFace(point, featureTolerance)
    }

    private fun isNearVisibleLine(point: Vector3, tolerance: Float): Boolean {
        val tol2 = tolerance * tolerance
        val rootMin = Vector3(point.x - tolerance, point.y - tolerance, point.z - tolerance)
        val rootMax = Vector3(point.x + tolerance, point.y + tolerance, point.z + tolerance)
        var found = false
        scene.queryGroupsByAabb(rootMin, rootMax, includeRoot = true).forEach { group ->
            if (found) return@forEach
            if (group === scene.root) {
                group.lineStore.forEachAabbCandidate(rootMin, rootMax) { seg ->
                    if (!isLineSnapVisible(group, seg)) return@forEachAabbCandidate true
                    val a = Vector3(seg.start)
                    val b = Vector3(seg.end)
                    if (distanceToSegmentSquared(point, a, b) <= tol2) {
                        found = true
                        return@forEachAabbCandidate false
                    }
                    true
                }
            } else {
                val (localMin, localMax) = worldAabbToLocalQueryBounds(group, point, tolerance)
                group.lineStore.forEachAabbCandidate(localMin, localMax) { seg ->
                    if (!isLineSnapVisible(group, seg)) return@forEachAabbCandidate true
                    val a = group.toWorld(seg.start)
                    val b = group.toWorld(seg.end)
                    if (distanceToSegmentSquared(point, a, b) <= tol2) {
                        found = true
                        return@forEachAabbCandidate false
                    }
                    true
                }
            }
        }
        return found
    }

    private fun isOnVisibleFace(point: Vector3, tolerance: Float): Boolean {
        val rootMin = Vector3(point.x - tolerance, point.y - tolerance, point.z - tolerance)
        val rootMax = Vector3(point.x + tolerance, point.y + tolerance, point.z + tolerance)
        var found = false
        scene.queryGroupsByAabb(rootMin, rootMax, includeRoot = true).forEach { group ->
            if (found) return@forEach
            if (group === scene.root) {
                group.faceStore.forEachAabbCandidate(rootMin, rootMax) { tri ->
                    if (!isFaceSnapVisible(group, tri)) return@forEachAabbCandidate true
                    val a = Vector3(tri.a)
                    val b = Vector3(tri.b)
                    val c = Vector3(tri.c)
                    if (pointNearTriangle(point, a, b, c, tolerance)) {
                        found = true
                        return@forEachAabbCandidate false
                    }
                    true
                }
            } else {
                val (localMin, localMax) = worldAabbToLocalQueryBounds(group, point, tolerance)
                group.faceStore.forEachAabbCandidate(localMin, localMax) { tri ->
                    if (!isFaceSnapVisible(group, tri)) return@forEachAabbCandidate true
                    val a = group.toWorld(tri.a)
                    val b = group.toWorld(tri.b)
                    val c = group.toWorld(tri.c)
                    if (pointNearTriangle(point, a, b, c, tolerance)) {
                        found = true
                        return@forEachAabbCandidate false
                    }
                    true
                }
            }
        }
        return found
    }

    private fun pointNearTriangle(point: Vector3, a: Vector3, b: Vector3, c: Vector3, tolerance: Float): Boolean {
        val ab = Vector3(b).sub(a)
        val ac = Vector3(c).sub(a)
        val normal = Vector3(ab).crs(ac)
        val len = normal.len()
        if (len <= 1e-8f) {
            return false
        }
        val planeDist = abs(Vector3(point).sub(a).dot(normal) / len)
        if (planeDist > tolerance) {
            return false
        }
        val v0 = Vector3(c).sub(a)
        val v1 = Vector3(b).sub(a)
        val v2 = Vector3(point).sub(a)
        val dot00 = v0.dot(v0)
        val dot01 = v0.dot(v1)
        val dot02 = v0.dot(v2)
        val dot11 = v1.dot(v1)
        val dot12 = v1.dot(v2)
        val denom = dot00 * dot11 - dot01 * dot01
        if (abs(denom) <= 1e-8f) {
            return false
        }
        val invDenom = 1f / denom
        val u = (dot11 * dot02 - dot01 * dot12) * invDenom
        val v = (dot00 * dot12 - dot01 * dot02) * invDenom
        val edgeTol = tolerance / max(gridSpacing, 1e-4f)
        return u >= -edgeTol && v >= -edgeTol && u + v <= 1f + edgeTol
    }

    private fun distanceToSegmentSquared(point: Vector3, a: Vector3, b: Vector3): Float {
        val ab = Vector3(b).sub(a)
        val len2 = ab.len2()
        if (len2 <= 1e-8f) {
            return point.dst2(a)
        }
        val t = Vector3(point).sub(a).dot(ab) / len2
        val clamped = t.coerceIn(0f, 1f)
        val closest = Vector3(a).mulAdd(ab, clamped)
        return point.dst2(closest)
    }

    private fun worldAabbToLocalQueryBounds(
        group: GroupScene.GroupNode,
        center: Vector3,
        radius: Float
    ): Pair<Vector3, Vector3> {
        val corners = arrayOf(
            Vector3(center.x - radius, center.y - radius, center.z - radius),
            Vector3(center.x - radius, center.y - radius, center.z + radius),
            Vector3(center.x - radius, center.y + radius, center.z - radius),
            Vector3(center.x - radius, center.y + radius, center.z + radius),
            Vector3(center.x + radius, center.y - radius, center.z - radius),
            Vector3(center.x + radius, center.y - radius, center.z + radius),
            Vector3(center.x + radius, center.y + radius, center.z - radius),
            Vector3(center.x + radius, center.y + radius, center.z + radius)
        )
        val first = group.toLocal(corners[0])
        val min = Vector3(first)
        val max = Vector3(first)
        for (i in 1 until corners.size) {
            val local = group.toLocal(corners[i])
            min.x = kotlin.math.min(min.x, local.x)
            min.y = kotlin.math.min(min.y, local.y)
            min.z = kotlin.math.min(min.z, local.z)
            max.x = kotlin.math.max(max.x, local.x)
            max.y = kotlin.math.max(max.y, local.y)
            max.z = kotlin.math.max(max.z, local.z)
        }
        return min to max
    }

    fun collectSelectionPoints(): List<SelectionPoint> {
        val points = mutableListOf<SelectionPoint>()
        val group = scene.activeGroup()
        group.lineStore.getSegments().forEach { segment ->
            if (!isLineSnapVisible(group, segment)) return@forEach
            val start = group.toWorld(segment.start)
            val end = group.toWorld(segment.end)
            points.add(SelectionPoint(projectScreen(start), Vector3(start), null, SelectionSource.EDGE))
            points.add(SelectionPoint(projectScreen(end), Vector3(end), null, SelectionSource.EDGE))
        }
        group.faceStore.getTriangles().forEach { tri ->
            if (!isFaceSnapVisible(group, tri)) return@forEach
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

    private data class PlaneGridBasis(
        val origin: Vector3,
        val axisU: Vector3,
        val axisV: Vector3
    )

    private data class PlaneCoordinates(
        val u: Float,
        val v: Float,
        val snappedU: Float,
        val snappedV: Float
    )

    private enum class SnapSource(val priority: Int) {
        // Vertices > grid points > lines > faces.
        LINE_ENDPOINT(8),
        LINE_MIDPOINT(7),
        FACE_EDGE_MIDPOINT(7),
        GRID(6),
        GRID_GUIDE(6),
        AXIS_GUIDE(6),
        FACE_EDGE(4),
        LINE_SEGMENT(4),
        GRID_LINE(4),
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
        val t = if (camera is OrthographicCamera) {
            Vector3(point).sub(camera.position).dot(camera.direction)
        } else {
            Vector3(point).sub(ray.origin).dot(ray.direction)
        }
        return if (t.isFinite() && t >= -1e-4f) max(0f, t) else null
    }
    private data class PlaneHit(
        val point: Vector3,
        val normal: Vector3,
        val t: Float
    )
}

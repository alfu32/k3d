package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3
import java.util.UUID

class DraftLineStore {
    companion object {
        private const val SPATIAL_HASH_CELL_SIZE = 10f
    }

    data class Segment(val start: Vector3, val end: Vector3) {
        var id: String = UUID.randomUUID().toString()
    }
    data class Hit(val segment: Segment, val point: Vector3, val t: Float)

    private val segments = mutableListOf<Segment>()
    private val selected = mutableSetOf<Segment>()
    private var onChange: (() -> Unit)? = null
    private var suppressChange = false
    private var suppressAutoSplitDepth = 0
    private var autoProcessingIgnorePredicate: ((Segment) -> Boolean)? = null
    private val epsilon = 1e-3f
    private val epsilonSq = epsilon * epsilon
    private val spatialIndex = SpatialHash3D<Segment>(SPATIAL_HASH_CELL_SIZE) { segment -> segment.id }
    private var spatialIndexDirty = true
    private val spatialBoundsMin = Vector3()
    private val spatialBoundsMax = Vector3()
    private var hasSpatialBounds = false

    fun setChangeListener(listener: () -> Unit) {
        onChange = listener
    }

    fun withChangeSuppressed(block: () -> Unit) {
        val prev = suppressChange
        suppressChange = true
        try {
            block()
        } finally {
            suppressChange = prev
        }
    }

    fun withAutoSplitSuppressed(block: () -> Unit) {
        suppressAutoSplitDepth++
        try {
            block()
        } finally {
            suppressAutoSplitDepth--
        }
    }

    fun setAutoProcessingIgnorePredicate(predicate: ((Segment) -> Boolean)?) {
        autoProcessingIgnorePredicate = predicate
    }

    fun addSegment(start: Vector3, end: Vector3, autoCleanup: Boolean = true, id: String = UUID.randomUUID().toString()) {
        if (start.dst2(end) <= epsilonSq) {
            return
        }
        val changed = addSegmentInternal(start, end, id)
        if (autoCleanup) {
            withChangeSuppressed {
                cleanupJts()
            }
            notifyChange()
        } else if (changed) {
            notifyChange()
        }
    }

    fun getSegments(): List<Segment> = segments

    fun segmentById(id: String): Segment? = segments.firstOrNull { it.id == id }

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
        notifyChange()
        return before - segments.size
    }

    fun deleteSegments(items: Collection<Segment>): Int {
        if (items.isEmpty()) {
            return 0
        }
        val before = segments.size
        val target = items.toSet()
        segments.removeAll(target)
        selected.removeAll(target)
        if (before != segments.size) {
            notifyChange()
        }
        return before - segments.size
    }

    fun transformSelected(transform: (Vector3) -> Vector3): Int {
        if (selected.isEmpty()) {
            return 0
        }
        val oldSelected = selected.toSet()
        val newSelected = mutableSetOf<Segment>()
        val newSegments = mutableListOf<Segment>()
        segments.forEach { segment ->
            if (oldSelected.contains(segment)) {
                val a = transform(Vector3(segment.start))
                val b = transform(Vector3(segment.end))
                val next = Segment(a, b)
                next.id = segment.id
                newSegments.add(next)
                newSelected.add(next)
            } else {
                newSegments.add(segment)
            }
        }
        segments.clear()
        segments.addAll(newSegments)
        selected.clear()
        selected.addAll(newSelected)
        notifyChange()
        return newSelected.size
    }

    fun transformSegments(
        targets: Set<Segment>,
        transform: (Vector3) -> Vector3
    ): Map<Segment, Segment> {
        if (targets.isEmpty()) {
            return emptyMap()
        }
        val mapping = linkedMapOf<Segment, Segment>()
        val targetSet = targets.toSet()
        val oldSelected = selected.toSet()
        val newSelected = mutableSetOf<Segment>()
        val newSegments = mutableListOf<Segment>()
        segments.forEach { segment ->
            if (targetSet.contains(segment)) {
                val a = transform(Vector3(segment.start))
                val b = transform(Vector3(segment.end))
                val next = Segment(a, b)
                next.id = segment.id
                newSegments.add(next)
                mapping[segment] = next
                if (oldSelected.contains(segment)) {
                    newSelected.add(next)
                }
            } else {
                newSegments.add(segment)
                if (oldSelected.contains(segment)) {
                    newSelected.add(segment)
                }
            }
        }
        segments.clear()
        segments.addAll(newSegments)
        selected.clear()
        selected.addAll(newSelected)
        notifyChange()
        return mapping
    }

    fun copySelected(transform: (Vector3) -> Vector3): Int {
        if (selected.isEmpty()) {
            return 0
        }
        val original = selected.toList()
        val newSelected = mutableSetOf<Segment>()
        original.forEach { segment ->
            val a = transform(Vector3(segment.start))
            val b = transform(Vector3(segment.end))
            val next = Segment(a, b)
            segments.add(next)
            newSelected.add(next)
        }
        selected.clear()
        selected.addAll(newSelected)
        notifyChange()
        return newSelected.size
    }

    fun selectInVolume(min: Vector3, max: Vector3, replace: Boolean = true): Int {
        if (replace) {
            selected.clear()
        }
        var count = 0
        segmentsIntersectingQuery(min, max).forEach { segment ->
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
        cleanupJts()
        notifyChange()
    }

    fun clearAll() {
        if (segments.isNotEmpty()) {
            segments.clear()
            selected.clear()
            notifyChange()
        }
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
        rayCandidates(ray).forEach { segment ->
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

    private fun snapToExistingEndpoint(point: Vector3, include: (Segment) -> Boolean = { true }): Vector3? {
        segments.forEach { segment ->
            if (!include(segment)) {
                return@forEach
            }
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

    private fun notifyChange() {
        spatialIndexDirty = true
        if (!suppressChange) {
            onChange?.invoke()
        }
    }

    fun notifyExternalChange() {
        notifyChange()
    }

    fun rayCandidates(ray: com.badlogic.gdx.math.collision.Ray): List<Segment> = segmentCandidatesForRay(ray)

    fun aabbCandidates(min: Vector3, max: Vector3): List<Segment> = segmentsIntersectingQuery(min, max)

    private fun ensureSpatialIndex() {
        if (!spatialIndexDirty) {
            return
        }
        spatialIndex.clear()
        hasSpatialBounds = false
        segments.forEach { segment ->
            val min = segmentMin(segment)
            val max = segmentMax(segment)
            if (!hasSpatialBounds) {
                spatialBoundsMin.set(min)
                spatialBoundsMax.set(max)
                hasSpatialBounds = true
            } else {
                spatialBoundsMin.x = kotlin.math.min(spatialBoundsMin.x, min.x)
                spatialBoundsMin.y = kotlin.math.min(spatialBoundsMin.y, min.y)
                spatialBoundsMin.z = kotlin.math.min(spatialBoundsMin.z, min.z)
                spatialBoundsMax.x = kotlin.math.max(spatialBoundsMax.x, max.x)
                spatialBoundsMax.y = kotlin.math.max(spatialBoundsMax.y, max.y)
                spatialBoundsMax.z = kotlin.math.max(spatialBoundsMax.z, max.z)
            }
            spatialIndex.insertAabb(min, max, segment)
        }
        spatialIndexDirty = false
    }

    private fun segmentCandidatesForRay(ray: com.badlogic.gdx.math.collision.Ray): List<Segment> {
        ensureSpatialIndex()
        if (!hasSpatialBounds) {
            return emptyList()
        }
        val range = SpatialHash3D.rayAabbRange(ray.origin, ray.direction, spatialBoundsMin, spatialBoundsMax, epsilon)
            ?: return emptyList()
        val start = Vector3(ray.origin).mulAdd(ray.direction, range[0])
        val end = Vector3(ray.origin).mulAdd(ray.direction, range[1])
        return spatialIndex.queryAabb(
            Vector3(
                kotlin.math.min(start.x, end.x),
                kotlin.math.min(start.y, end.y),
                kotlin.math.min(start.z, end.z)
            ),
            Vector3(
                kotlin.math.max(start.x, end.x),
                kotlin.math.max(start.y, end.y),
                kotlin.math.max(start.z, end.z)
            )
        )
    }

    private fun segmentsIntersectingQuery(min: Vector3, max: Vector3): List<Segment> {
        ensureSpatialIndex()
        return if (hasSpatialBounds) spatialIndex.queryAabb(min, max) else emptyList()
    }

    private fun segmentMin(segment: Segment): Vector3 {
        return Vector3(
            kotlin.math.min(segment.start.x, segment.end.x) - epsilon,
            kotlin.math.min(segment.start.y, segment.end.y) - epsilon,
            kotlin.math.min(segment.start.z, segment.end.z) - epsilon
        )
    }

    private fun segmentMax(segment: Segment): Vector3 {
        return Vector3(
            kotlin.math.max(segment.start.x, segment.end.x) + epsilon,
            kotlin.math.max(segment.start.y, segment.end.y) + epsilon,
            kotlin.math.max(segment.start.z, segment.end.z) + epsilon
        )
    }

    fun cleanupJts() {
        if (segments.isEmpty()) {
            return
        }
        val ignore = autoProcessingIgnorePredicate
        if (ignore != null) {
            val excluded = segments.filter(ignore)
            if (excluded.isNotEmpty()) {
                val included = segments.filterNot(ignore)
                if (included.isEmpty()) {
                    return
                }
                val excludedSelected = selected.filterTo(mutableSetOf(), ignore)
                val (cleanedSegments, cleanedSelected) = cleanupSegmentsJts(included, selected)
                segments.clear()
                segments.addAll(cleanedSegments)
                segments.addAll(excluded)
                selected.clear()
                selected.addAll(cleanedSelected)
                selected.addAll(excludedSelected)
                return
            }
        }
        val (newSegments, newSelected) = cleanupSegmentsJts(segments, selected)
        segments.clear()
        segments.addAll(newSegments)
        selected.clear()
        selected.addAll(newSelected)
    }

    private fun cleanupSegmentsJts(
        sourceSegments: List<Segment>,
        selectedSource: Set<Segment>
    ): Pair<List<Segment>, Set<Segment>> {
        val grouped = sourceSegments.groupBy { lineKey(it) }
        val newSegments = mutableListOf<Segment>()
        val newSelected = mutableSetOf<Segment>()

        grouped.values.forEach { group ->
            if (group.isEmpty()) {
                return@forEach
            }
            val ref = group.first().start
            val dir = Vector3(group.first().end).sub(ref)
            if (dir.len2() <= epsilonSq) {
                return@forEach
            }
            dir.nor()

            val forcedTs = mutableListOf<Float>()
            group.forEach { seg ->
                sourceSegments.forEach { other ->
                    if (group.contains(other)) {
                        return@forEach
                    }
                    if (lineKey(other) == lineKey(seg)) {
                        return@forEach
                    }
                    val hit = intersectSegments(seg.start, seg.end, other.start, other.end) ?: return@forEach
                    val t = dir.dot(Vector3(hit.point).sub(ref))
                    forcedTs.add(t)
                }
            }
            val forcedSplit = forcedTs.distinct().sorted()

            val intervals = group.mapNotNull { seg ->
                val t0 = dir.dot(Vector3(seg.start).sub(ref))
                val t1 = dir.dot(Vector3(seg.end).sub(ref))
                if (kotlin.math.abs(t0 - t1) <= epsilon) {
                    null
                } else {
                    if (t0 <= t1) Interval(t0, t1) else Interval(t1, t0)
                }
            }.sortedBy { it.start }
            val hadSelection = group.any { selectedSource.contains(it) }
            val mergedIntervals = mergeIntervals(intervals)
            mergedIntervals.forEach { interval ->
                val splits = forcedSplit.filter { it > interval.start + epsilon && it < interval.end - epsilon }
                var last = interval.start
                if (splits.isEmpty()) {
                    val p0 = Vector3(ref).mulAdd(dir, interval.start)
                    val p1 = Vector3(ref).mulAdd(dir, interval.end)
                    if (p0.dst2(p1) <= epsilonSq) {
                        return@forEach
                    }
                    val seg = Segment(p0, p1)
                    newSegments.add(seg)
                    if (hadSelection) {
                        newSelected.add(seg)
                    }
                    return@forEach
                }
                splits.forEach { split ->
                    val p0 = Vector3(ref).mulAdd(dir, last)
                    val p1 = Vector3(ref).mulAdd(dir, split)
                    if (p0.dst2(p1) > epsilonSq) {
                        val seg = Segment(p0, p1)
                        newSegments.add(seg)
                        if (hadSelection) {
                            newSelected.add(seg)
                        }
                    }
                    last = split
                }
                val p0 = Vector3(ref).mulAdd(dir, last)
                val p1 = Vector3(ref).mulAdd(dir, interval.end)
                if (p0.dst2(p1) > epsilonSq) {
                    val seg = Segment(p0, p1)
                    newSegments.add(seg)
                    if (hadSelection) {
                        newSelected.add(seg)
                    }
                }
            }
        }

        return newSegments to newSelected
    }

    private fun addSegmentInternal(start: Vector3, end: Vector3, id: String): Boolean {
        if (start.dst2(end) <= epsilonSq) {
            return false
        }
        if (suppressAutoSplitDepth > 0) {
            val raw = Segment(Vector3(start), Vector3(end))
            raw.id = id
            segments.add(raw)
            return true
        }
        val before = segments.size
        val ignore = autoProcessingIgnorePredicate
        fun include(segment: Segment): Boolean = ignore?.invoke(segment) != true
        val newStart = snapToExistingEndpoint(start, ::include) ?: Vector3(start)
        val newEnd = snapToExistingEndpoint(end, ::include) ?: Vector3(end)
        val splitPoints = mutableListOf(PointOnSegment(0f, newStart), PointOnSegment(1f, newEnd))

        var i = 0
        while (i < segments.size) {
            val existing = segments[i]
            if (!include(existing)) {
                i++
                continue
            }
            val intersection = intersectSegments(newStart, newEnd, existing.start, existing.end) ?: run {
                i++
                continue
            }
            val snapped = snapToExistingEndpoint(intersection.point, ::include)
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
                val segment = Segment(Vector3(a), Vector3(b))
                if (orderedPoints.size == 2 && p == 0) {
                    segment.id = id
                }
                segments.add(segment)
            }
        }

        return segments.size != before
    }

    private data class LineKey(
        val dx: Int,
        val dy: Int,
        val dz: Int,
        val mx: Int,
        val my: Int,
        val mz: Int
    )

    private fun lineKey(segment: Segment): LineKey {
        val dir = Vector3(segment.end).sub(segment.start)
        if (dir.len2() <= epsilonSq) {
            return LineKey(0, 0, 0, 0, 0, 0)
        }
        dir.nor()
        if (dir.y < 0f || (dir.y == 0f && (dir.x < 0f || (dir.x == 0f && dir.z < 0f)))) {
            dir.scl(-1f)
        }
        val moment = Vector3(segment.start).crs(dir)
        return LineKey(
            quant(dir.x),
            quant(dir.y),
            quant(dir.z),
            quant(moment.x),
            quant(moment.y),
            quant(moment.z)
        )
    }

    private fun quant(value: Float): Int = kotlin.math.round(value / epsilon).toInt()

    private data class Interval(val start: Float, val end: Float)

    private fun mergeIntervals(intervals: List<Interval>): List<Interval> {
        if (intervals.isEmpty()) {
            return emptyList()
        }
        val result = mutableListOf<Interval>()
        var current = intervals.first()
        for (i in 1 until intervals.size) {
            val next = intervals[i]
            if (next.start <= current.end + epsilon) {
                current = Interval(current.start, kotlin.math.max(current.end, next.end))
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
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

}

package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.tools.PlaneBasis
import com.github.alfu32.sketch.tools.planeBasisFromNormal
import com.github.alfu32.sketch.model.DraftLineStore
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.operation.polygonize.Polygonizer
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder
import java.util.UUID

class DraftFaceStore(
    private val defaultColor: com.badlogic.gdx.graphics.Color = com.badlogic.gdx.graphics.Color(0.93f, 0.93f, 0.93f, 1f)
) {
    companion object {
        private const val SPATIAL_HASH_CELL_SIZE = 10f
    }

    data class Triangle(val a: Vector3, val b: Vector3, val c: Vector3) {
        var id: String = UUID.randomUUID().toString()
    }
    data class Hit(val triangle: Triangle, val point: Vector3, val normal: Vector3, val t: Float)

    private val triangles = mutableListOf<Triangle>()
    private val trianglesById = linkedMapOf<String, Triangle>()
    private val selected = mutableSetOf<Triangle>()
    private val colors = mutableMapOf<Triangle, com.badlogic.gdx.graphics.Color>()
    private var onChange: (() -> Unit)? = null
    private var suppressChange = false
    private var visualVersion = 0L
    private val epsilon = 1e-4f
    private val epsilonSq = epsilon * epsilon
    private val jtsScale = 10000.0
    private val epsilon2d = (epsilon * jtsScale).toFloat()
    private val cutEps2d = 1e-2f
    private val planeEps = 1e-2f
    private var spatialIndex = SpatialHash3D<String>(SPATIAL_HASH_CELL_SIZE) { it }
    private val asyncSpatialIndex = AsyncAabbIndexRebuilder<String>("Faces index", SPATIAL_HASH_CELL_SIZE, keyOf = { it })
    private var spatialIndexDirty = true
    private var spatialBoundsDirty = true
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

    fun addTriangle(a: Vector3, b: Vector3, c: Vector3, id: String = UUID.randomUUID().toString()) {
        val triangle = Triangle(Vector3(a), Vector3(b), Vector3(c))
        triangle.id = id
        triangles.add(triangle)
        trianglesById[triangle.id] = triangle
        colors[triangle] = com.badlogic.gdx.graphics.Color(defaultColor)
        notifyChange()
    }

    fun addTriangle(
        a: Vector3,
        b: Vector3,
        c: Vector3,
        color: com.badlogic.gdx.graphics.Color,
        id: String = UUID.randomUUID().toString()
    ) {
        val triangle = Triangle(Vector3(a), Vector3(b), Vector3(c))
        triangle.id = id
        triangles.add(triangle)
        trianglesById[triangle.id] = triangle
        colors[triangle] = com.badlogic.gdx.graphics.Color(color)
        notifyChange()
    }

    fun addPolygon(points: List<Vector3>, preferredNormal: Vector3? = null) {
        if (points.size < 3) {
            return
        }
        val cleaned = removeClosingPoint(points)
        if (cleaned.size < 3) {
            return
        }
        var ordered = cleaned
        val normal = computeNormal(ordered)
        val target = preferredNormal?.cpy()?.nor()
        if (target != null && normal.dot(target) < 0f) {
            ordered = ordered.asReversed()
        }
        triangulatePolygon(ordered)
    }

    fun getTriangles(): List<Triangle> = triangles

    fun triangleById(id: String): Triangle? = trianglesById[id]

    fun getSelected(): Set<Triangle> = selected

    fun colorFor(triangle: Triangle): com.badlogic.gdx.graphics.Color {
        return colors[triangle] ?: defaultColor
    }

    fun isSelected(triangle: Triangle): Boolean = selected.contains(triangle)

    fun addSelection(triangle: Triangle): Boolean {
        val added = selected.add(triangle)
        if (added) {
            markVisualChanged()
        }
        return added
    }

    fun removeSelection(triangle: Triangle) {
        if (selected.remove(triangle)) {
            markVisualChanged()
        }
    }

    fun toggleSelection(triangle: Triangle) {
        if (!selected.add(triangle)) {
            selected.remove(triangle)
        }
        markVisualChanged()
    }

    fun clearSelection() {
        if (selected.isNotEmpty()) {
            selected.clear()
            markVisualChanged()
        }
    }

    fun flipSelected(): Int {
        if (selected.isEmpty()) {
            return 0
        }
        val oldSelected = selected.toSet()
        val newSelected = mutableSetOf<Triangle>()
        val newTriangles = mutableListOf<Triangle>()
        triangles.forEach { tri ->
            if (oldSelected.contains(tri)) {
                val flipped = Triangle(Vector3(tri.a), Vector3(tri.c), Vector3(tri.b))
                flipped.id = tri.id
                colors[flipped] = colors.remove(tri) ?: com.badlogic.gdx.graphics.Color(defaultColor)
                newTriangles.add(flipped)
                newSelected.add(flipped)
            } else {
                newTriangles.add(tri)
            }
        }
        triangles.clear()
        triangles.addAll(newTriangles)
        rebuildTriangleLookup()
        selected.clear()
        selected.addAll(newSelected)
        notifyChange()
        return newSelected.size
    }

    fun deleteSelected(): Int {
        if (selected.isEmpty()) {
            return 0
        }
        val before = triangles.size
        selected.forEach { colors.remove(it) }
        triangles.removeAll(selected)
        selected.forEach { trianglesById.remove(it.id) }
        selected.clear()
        notifyChange()
        return before - triangles.size
    }

    fun deleteTriangles(items: Collection<Triangle>): Int {
        if (items.isEmpty()) {
            return 0
        }
        val before = triangles.size
        val target = items.toSet()
        triangles.removeAll(target)
        target.forEach { tri ->
            colors.remove(tri)
            selected.remove(tri)
            trianglesById.remove(tri.id)
        }
        if (before != triangles.size) {
            notifyChange()
        }
        return before - triangles.size
    }

    fun clearAll() {
        if (triangles.isNotEmpty()) {
            triangles.clear()
            trianglesById.clear()
            selected.clear()
            colors.clear()
            clearSpatialIndexState()
            notifyChange(false)
        }
    }

    fun paintSelected(color: com.badlogic.gdx.graphics.Color): Int {
        if (selected.isEmpty()) {
            return 0
        }
        selected.forEach { triangle ->
            colors[triangle] = com.badlogic.gdx.graphics.Color(color)
        }
        notifyChange(false)
        return selected.size
    }

    fun paintTriangle(triangle: Triangle, color: com.badlogic.gdx.graphics.Color) {
        colors[triangle] = com.badlogic.gdx.graphics.Color(color)
        notifyChange(false)
    }

    fun transformSelected(transform: (Vector3) -> Vector3): Int {
        if (selected.isEmpty()) {
            return 0
        }
        val oldSelected = selected.toSet()
        val newSelected = mutableSetOf<Triangle>()
        val newTriangles = mutableListOf<Triangle>()
        val newColors = mutableMapOf<Triangle, com.badlogic.gdx.graphics.Color>()

        triangles.forEach { tri ->
            val color = colors[tri] ?: defaultColor
            if (oldSelected.contains(tri)) {
                val a = transform(Vector3(tri.a))
                val b = transform(Vector3(tri.b))
                val c = transform(Vector3(tri.c))
                val next = Triangle(a, b, c)
                next.id = tri.id
                newTriangles.add(next)
                newSelected.add(next)
                newColors[next] = com.badlogic.gdx.graphics.Color(color)
            } else {
                newTriangles.add(tri)
                newColors[tri] = com.badlogic.gdx.graphics.Color(color)
            }
        }

        triangles.clear()
        triangles.addAll(newTriangles)
        rebuildTriangleLookup()
        selected.clear()
        selected.addAll(newSelected)
        colors.clear()
        colors.putAll(newColors)
        notifyChange()
        return newSelected.size
    }

    fun transformTriangles(
        targets: Set<Triangle>,
        transform: (Vector3) -> Vector3
    ): Map<Triangle, Triangle> {
        if (targets.isEmpty()) {
            return emptyMap()
        }
        val mapping = linkedMapOf<Triangle, Triangle>()
        val targetSet = targets.toSet()
        val oldSelected = selected.toSet()
        val newSelected = mutableSetOf<Triangle>()
        val newTriangles = mutableListOf<Triangle>()
        val newColors = mutableMapOf<Triangle, com.badlogic.gdx.graphics.Color>()

        triangles.forEach { tri ->
            val color = colors[tri] ?: defaultColor
            if (targetSet.contains(tri)) {
                val a = transform(Vector3(tri.a))
                val b = transform(Vector3(tri.b))
                val c = transform(Vector3(tri.c))
                val next = Triangle(a, b, c)
                next.id = tri.id
                newTriangles.add(next)
                newColors[next] = com.badlogic.gdx.graphics.Color(color)
                mapping[tri] = next
                if (oldSelected.contains(tri)) {
                    newSelected.add(next)
                }
            } else {
                newTriangles.add(tri)
                newColors[tri] = com.badlogic.gdx.graphics.Color(color)
                if (oldSelected.contains(tri)) {
                    newSelected.add(tri)
                }
            }
        }

        triangles.clear()
        triangles.addAll(newTriangles)
        rebuildTriangleLookup()
        colors.clear()
        colors.putAll(newColors)
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
        val newSelected = mutableSetOf<Triangle>()
        original.forEach { tri ->
            val color = colors[tri] ?: defaultColor
            val a = transform(Vector3(tri.a))
            val b = transform(Vector3(tri.b))
            val c = transform(Vector3(tri.c))
            val next = Triangle(a, b, c)
            triangles.add(next)
            trianglesById[next.id] = next
            colors[next] = com.badlogic.gdx.graphics.Color(color)
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
        trianglesIntersectingQuery(min, max).forEach { tri ->
            if (triangleIntersectsAabb(tri, min, max)) {
                if (selected.add(tri)) {
                    count++
                }
            }
        }
        return count
    }

    fun pickTriangle(ray: com.badlogic.gdx.math.collision.Ray): Hit? {
        var best: Hit? = null
        rayCandidates(ray).forEach { tri ->
            val hit = intersectRayTriangle(ray, tri) ?: return@forEach
            if (best == null || hit.t < best!!.t) {
                best = hit
            }
        }
        return best
    }

    fun pickTriangle(
        ray: com.badlogic.gdx.math.collision.Ray,
        predicate: (Triangle) -> Boolean
    ): Hit? {
        var best: Hit? = null
        rayCandidates(ray).forEach { tri ->
            if (!predicate(tri)) {
                return@forEach
            }
            val hit = intersectRayTriangle(ray, tri) ?: return@forEach
            if (best == null || hit.t < best!!.t) {
                best = hit
            }
        }
        return best
    }

    fun collectCoplanar(base: Triangle, normalEps: Float = 1e-3f, distEps: Float = 1e-3f): List<Triangle> {
        val baseNormal = Vector3(base.b).sub(base.a).crs(Vector3(base.c).sub(base.a))
        if (baseNormal.len2() <= epsilonSq) {
            return listOf(base)
        }
        baseNormal.nor()
        val baseD = -baseNormal.dot(base.a)
        return triangles.filter { tri ->
            val n = Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a))
            if (n.len2() <= epsilonSq) {
                return@filter false
            }
            n.nor()
            var d = -n.dot(tri.a)
            if (n.dot(baseNormal) < 0f) {
                n.scl(-1f)
                d = -d
            }
            kotlin.math.abs(1f - n.dot(baseNormal)) <= normalEps && kotlin.math.abs(d - baseD) <= distEps
        }
    }

    fun collectCoplanarConnected(base: Triangle, normalEps: Float = 1e-3f, distEps: Float = 1e-3f): List<Triangle> {
        val coplanar = collectCoplanar(base, normalEps, distEps)
        if (coplanar.size <= 1) {
            return coplanar
        }
        val edgeMap = mutableMapOf<EdgeKey, MutableList<Triangle>>()
        coplanar.forEach { tri ->
            val edges = listOf(
                edgeKey(tri.a, tri.b),
                edgeKey(tri.b, tri.c),
                edgeKey(tri.c, tri.a)
            )
            edges.forEach { key ->
                edgeMap.getOrPut(key) { mutableListOf() }.add(tri)
            }
        }
        val result = mutableListOf<Triangle>()
        val queue = ArrayDeque<Triangle>()
        val visited = mutableSetOf<Triangle>()
        queue.add(base)
        visited.add(base)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            val edges = listOf(
                edgeKey(current.a, current.b),
                edgeKey(current.b, current.c),
                edgeKey(current.c, current.a)
            )
            edges.forEach { key ->
                edgeMap[key].orEmpty().forEach { neighbor ->
                    if (neighbor !in visited) {
                        visited.add(neighbor)
                        queue.add(neighbor)
                    }
                }
            }
        }
        return result
    }

    fun collectConnected(base: Triangle): List<Triangle> {
        if (triangles.isEmpty()) {
            return emptyList()
        }
        val edgeMap = mutableMapOf<EdgeKey, MutableList<Triangle>>()
        triangles.forEach { tri ->
            val edges = listOf(
                edgeKey(tri.a, tri.b),
                edgeKey(tri.b, tri.c),
                edgeKey(tri.c, tri.a)
            )
            edges.forEach { key ->
                edgeMap.getOrPut(key) { mutableListOf() }.add(tri)
            }
        }
        val result = mutableListOf<Triangle>()
        val queue = ArrayDeque<Triangle>()
        val visited = mutableSetOf<Triangle>()
        queue.add(base)
        visited.add(base)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            val edges = listOf(
                edgeKey(current.a, current.b),
                edgeKey(current.b, current.c),
                edgeKey(current.c, current.a)
            )
            edges.forEach { key ->
                edgeMap[key].orEmpty().forEach { neighbor ->
                    if (neighbor !in visited) {
                        visited.add(neighbor)
                        queue.add(neighbor)
                    }
                }
            }
        }
        return result
    }

    fun cleanupCoplanarFaces() {
        if (triangles.isEmpty()) {
            return
        }
        val groups = triangles.groupBy { planeKey(it) }
        val merged = mutableListOf<Triangle>()
        groups.values.forEach { group ->
            mergeCoplanarGroup(group, merged)
        }
        triangles.clear()
        triangles.addAll(merged)
    }

    fun cutBySegmentInPlane(start: Vector3, end: Vector3): Int {
        if (triangles.isEmpty()) {
            return 0
        }
        val dir = Vector3(end).sub(start)
        if (dir.len2() <= epsilonSq) {
            return 0
        }
        val startTime = System.nanoTime()
        var iteration = 0
        var totalSplits = 0

        while (iteration < 10 && (System.nanoTime() - startTime) < 1_000_000_000L) {
            iteration++
            var changed = false
            val newTriangles = mutableListOf<Triangle>()
            val newColors = mutableMapOf<Triangle, com.badlogic.gdx.graphics.Color>()
            val newSelected = mutableSetOf<Triangle>()

            triangles.forEach { tri ->
                val color = colors[tri] ?: defaultColor
                val wasSelected = selected.contains(tri)
                val split = splitTriangleByLine(tri, start, end)
                if (split == null) {
                    newTriangles.add(tri)
                    newColors[tri] = color
                    if (wasSelected) {
                        newSelected.add(tri)
                    }
                } else {
                    changed = true
                    totalSplits += split.size
                    split.forEach { next ->
                        newTriangles.add(next)
                        newColors[next] = com.badlogic.gdx.graphics.Color(color)
                        if (wasSelected) {
                            newSelected.add(next)
                        }
                    }
                }
            }

            if (!changed) {
                break
            }
            triangles.clear()
            triangles.addAll(newTriangles)
            colors.clear()
            colors.putAll(newColors)
            selected.clear()
            selected.addAll(newSelected)
        }

        if (totalSplits > 0) {
            notifyChange()
        }
        return totalSplits
    }

    fun cutSelectedByPolyline(points: List<Vector3>, segments: List<DraftLineStore.Segment>): Int {
        if (selected.isEmpty() || triangles.isEmpty()) {
            return 0
        }
        val selectedTriangles = selected.toMutableSet()
        if (selectedTriangles.isEmpty()) {
            return 0
        }
        val first = selectedTriangles.first()
        val normal = Vector3(first.b).sub(first.a).crs(Vector3(first.c).sub(first.a))
        if (normal.len2() <= epsilonSq) {
            return 0
        }
        val basis = planeBasisFromNormal(normal)
        val origin = Vector3(first.a)
        val dedupedPoints = dedupePoints(points, 1e-2f)
        var totalSplits = 0

        dedupedPoints.forEach { point ->
            val p2 = to2d(point, origin, basis)
            val target = selectedTriangles.firstOrNull { tri ->
                val a2 = to2d(tri.a, origin, basis)
                val b2 = to2d(tri.b, origin, basis)
                val c2 = to2d(tri.c, origin, basis)
                !pointOnTriangleBoundary2d(p2, a2, b2, c2) && pointInTriangle2d(p2, a2, b2, c2)
            } ?: return@forEach
            val color = colors[target] ?: defaultColor
            val split = splitTriangleAtPoint(target, point)
            triangles.remove(target)
            colors.remove(target)
            selectedTriangles.remove(target)
            split.forEach { next ->
                triangles.add(next)
                colors[next] = com.badlogic.gdx.graphics.Color(color)
                selectedTriangles.add(next)
            }
            totalSplits += split.size
        }

        selected.clear()
        selected.addAll(selectedTriangles)

        val sortedSegments = segments.sortedWith(compareBy(
            { segmentKeyMin(it) },
            { segmentKeyMax(it) }
        ))
        var globalChanged: Boolean
        var globalGuard = 0
        do {
            globalChanged = false
            globalGuard++
            sortedSegments.forEach { segment ->
            val s = segment.start
            val e = segment.end
            if (flipDiagonalForSegment(s, e, selectedTriangles)) {
                totalSplits += 2
                globalChanged = true
            }
            var changed = true
            var iterations = 0
            while (changed && iterations < 20) {
                iterations++
                changed = false
                val newTriangles = mutableListOf<Triangle>()
                val newColors = mutableMapOf<Triangle, com.badlogic.gdx.graphics.Color>()
                val newSelected = mutableSetOf<Triangle>()
                triangles.forEach { tri ->
                    val color = colors[tri] ?: defaultColor
                    val wasSelected = selectedTriangles.contains(tri)
                    if (!wasSelected) {
                        newTriangles.add(tri)
                        newColors[tri] = color
                        return@forEach
                    }
                    val split = splitTriangleBySegmentCut(tri, s, e, origin, basis)
                    if (split == null) {
                        newTriangles.add(tri)
                        newColors[tri] = color
                        newSelected.add(tri)
                    } else {
                        changed = true
                        globalChanged = true
                        totalSplits += split.size
                        split.forEach { next ->
                            newTriangles.add(next)
                            newColors[next] = com.badlogic.gdx.graphics.Color(color)
                            newSelected.add(next)
                        }
                    }
                }
                triangles.clear()
                triangles.addAll(newTriangles)
                colors.clear()
                colors.putAll(newColors)
                selectedTriangles.clear()
                selectedTriangles.addAll(newSelected)
            }
            }
        } while (globalChanged && globalGuard < 5)

        selected.clear()
        selected.addAll(selectedTriangles)
        if (totalSplits > 0) {
            notifyChange()
        }
        return totalSplits
    }

    fun cutSelectedByPolygon(points: List<Vector3>): Int {
        if (selected.isEmpty() || triangles.isEmpty() || points.size < 3) {
            return 0
        }
        val geometryFactory = GeometryFactory()
        val newTriangles = mutableListOf<Triangle>()
        val newColors = mutableMapOf<Triangle, com.badlogic.gdx.graphics.Color>()
        val newSelected = mutableSetOf<Triangle>()
        val unselected = triangles.filter { !selected.contains(it) }
        val unselectedColors = unselected.associateWith { colors[it] ?: defaultColor }
        val selectedGroups = selected.groupBy { planeKey(it) }

        selectedGroups.values.forEach { group ->
            if (group.isEmpty()) {
                return@forEach
            }
            val base = group.first()
            val normal = Vector3(base.b).sub(base.a).crs(Vector3(base.c).sub(base.a))
            if (normal.len2() <= epsilonSq) {
                group.forEach { tri ->
                    newTriangles.add(tri)
                    newColors[tri] = colors[tri] ?: defaultColor
                    newSelected.add(tri)
                }
                return@forEach
            }
            val basis = planeBasisFromNormal(normal)
            val origin = Vector3(base.a)
            val holePoints = points.filter { pointOnPlane(it, normal, -normal.dot(origin)) }
            if (holePoints.size < 3) {
                group.forEach { tri ->
                    newTriangles.add(tri)
                    newColors[tri] = colors[tri] ?: defaultColor
                    newSelected.add(tri)
                }
                return@forEach
            }
            val holeCoords = mutableListOf<Coordinate>()
            holePoints.forEach { holeCoords.add(toCoord(it, origin, basis)) }
            if (holeCoords.size >= 2) {
                val first = holeCoords.first()
                val last = holeCoords.last()
                val dx = first.x - last.x
                val dy = first.y - last.y
                if (dx * dx + dy * dy > epsilon2d * epsilon2d) {
                    holeCoords.add(Coordinate(first))
                }
            }
            val holePolygon = try {
                geometryFactory.createPolygon(holeCoords.toTypedArray())
            } catch (ex: Exception) {
                null
            }
            if (holePolygon == null || holePolygon.isEmpty) {
                group.forEach { tri ->
                    newTriangles.add(tri)
                    newColors[tri] = colors[tri] ?: defaultColor
                    newSelected.add(tri)
                }
                return@forEach
            }
            val facePolys = group.map { tri ->
                val coords = arrayOf(
                    toCoord(tri.a, origin, basis),
                    toCoord(tri.b, origin, basis),
                    toCoord(tri.c, origin, basis),
                    toCoord(tri.a, origin, basis)
                )
                geometryFactory.createPolygon(coords)
            }
            val union = try {
                UnaryUnionOp.union(facePolys)
            } catch (ex: Exception) {
                null
            }
            if (union == null || union.isEmpty) {
                group.forEach { tri ->
                    newTriangles.add(tri)
                    newColors[tri] = colors[tri] ?: defaultColor
                    newSelected.add(tri)
                }
                return@forEach
            }
            val diff = try {
                union.difference(holePolygon)
            } catch (ex: Exception) {
                null
            }
            if (diff == null || diff.isEmpty) {
                group.forEach { tri ->
                    newTriangles.add(tri)
                    newColors[tri] = colors[tri] ?: defaultColor
                    newSelected.add(tri)
                }
                return@forEach
            }
            val polygonsToTriangulate = collectPolygons(diff)
            if (polygonsToTriangulate.isEmpty()) {
                group.forEach { tri ->
                    newTriangles.add(tri)
                    newColors[tri] = colors[tri] ?: defaultColor
                    newSelected.add(tri)
                }
                return@forEach
            }
            val groupColor = colors[group.first()] ?: defaultColor
            polygonsToTriangulate.forEach { polygon ->
                val triangles2d = tryTriangulatePolygon(polygon, emptyList(), origin, basis, geometryFactory)
                if (triangles2d == null || triangles2d.isEmpty()) {
                    return@forEach
                }
                triangles2d.forEach { tri2d ->
                    val coords = tri2d.coordinates
                    if (coords.size < 4) return@forEach
                    val a = fromCoord(coords[0], origin, basis)
                    val b = fromCoord(coords[1], origin, basis)
                    val c = fromCoord(coords[2], origin, basis)
                    val tri = Triangle(a, b, c)
                    newTriangles.add(tri)
                    newColors[tri] = com.badlogic.gdx.graphics.Color(groupColor)
                    newSelected.add(tri)
                }
            }
        }

        triangles.clear()
        triangles.addAll(unselected)
        triangles.addAll(newTriangles)
        colors.clear()
        colors.putAll(unselectedColors)
        colors.putAll(newColors)
        selected.clear()
        selected.addAll(newSelected)
        notifyChange()
        return newTriangles.size
    }

    private fun flipDiagonalForSegment(
        start: Vector3,
        end: Vector3,
        selectedTriangles: MutableSet<Triangle>
    ): Boolean {
        if (selectedTriangles.size < 2) {
            return false
        }
        val triList = selectedTriangles.toList().sortedWith(compareBy(
            { triangleKey(it) }
        ))
        val matchStart = vertexMatch(start)
        val matchEnd = vertexMatch(end)
        if (matchStart == null || matchEnd == null) {
            return false
        }
        for (i in 0 until triList.size) {
            val t1 = triList[i]
            for (j in i + 1 until triList.size) {
                val t2 = triList[j]
                val shared = sharedEdge(t1, t2) ?: continue
                val c1 = otherVertex(t1, shared.first, shared.second) ?: continue
                val c2 = otherVertex(t2, shared.first, shared.second) ?: continue
                if (!matchStart(c1) || !matchEnd(c2)) {
                    if (!matchStart(c2) || !matchEnd(c1)) {
                        continue
                    }
                }
                val color1 = colors[t1] ?: defaultColor
                val color2 = colors[t2] ?: defaultColor
                triangles.remove(t1)
                triangles.remove(t2)
                colors.remove(t1)
                colors.remove(t2)
                selectedTriangles.remove(t1)
                selectedTriangles.remove(t2)
                val normal = Vector3(t1.b).sub(t1.a).crs(Vector3(t1.c).sub(t1.a))
                val a = Vector3(start)
                val b = Vector3(end)
                val tA = orientTriangle(a, b, Vector3(shared.first), normal)
                val tB = orientTriangle(b, a, Vector3(shared.second), normal)
                triangles.add(tA)
                triangles.add(tB)
                colors[tA] = com.badlogic.gdx.graphics.Color(color1)
                colors[tB] = com.badlogic.gdx.graphics.Color(color2)
                selectedTriangles.add(tA)
                selectedTriangles.add(tB)
                return true
            }
        }
        return false
    }

    private fun vertexMatch(target: Vector3): ((Vector3) -> Boolean)? {
        return { v -> v.dst2(target) <= epsilonSq }
    }

    private fun triangleKey(tri: Triangle): Long {
        val keys = listOf(vertexKey(tri.a), vertexKey(tri.b), vertexKey(tri.c))
            .sortedWith { a, b -> compareKeys(a, b) }
        var h = 1469598103934665603L
        keys.forEach { k ->
            h = h * 31 + k.x
            h = h * 31 + k.y
            h = h * 31 + k.z
        }
        return h
    }

    private fun segmentKeyMin(segment: DraftLineStore.Segment): Long {
        val a = vertexKey(segment.start)
        val b = vertexKey(segment.end)
        return if (compareKeys(a, b) <= 0) hashVertexKey(a) else hashVertexKey(b)
    }

    private fun segmentKeyMax(segment: DraftLineStore.Segment): Long {
        val a = vertexKey(segment.start)
        val b = vertexKey(segment.end)
        return if (compareKeys(a, b) <= 0) hashVertexKey(b) else hashVertexKey(a)
    }

    private fun hashVertexKey(key: VertexKey): Long {
        var h = 1469598103934665603L
        h = h * 31 + key.x
        h = h * 31 + key.y
        h = h * 31 + key.z
        return h
    }

    private fun sharedEdge(a: Triangle, b: Triangle): Pair<Vector3, Vector3>? {
        val vertsA = listOf(a.a, a.b, a.c)
        val vertsB = listOf(b.a, b.b, b.c)
        val shared = vertsA.filter { va -> vertsB.any { vb -> va.dst2(vb) <= epsilonSq } }
        return if (shared.size == 2) Pair(shared[0], shared[1]) else null
    }

    private fun otherVertex(tri: Triangle, a: Vector3, b: Vector3): Vector3? {
        return listOf(tri.a, tri.b, tri.c).firstOrNull {
            it.dst2(a) > epsilonSq && it.dst2(b) > epsilonSq
        }
    }

    private fun orientTriangle(a: Vector3, b: Vector3, c: Vector3, normal: Vector3): Triangle {
        val n = Vector3(b).sub(a).crs(Vector3(c).sub(a))
        return if (n.dot(normal) >= 0f) {
            Triangle(Vector3(a), Vector3(b), Vector3(c))
        } else {
            Triangle(Vector3(a), Vector3(c), Vector3(b))
        }
    }

    fun cleanupJts(keepPoints: List<Vector3> = emptyList()) {
        if (triangles.isEmpty()) {
            return
        }
        val geometryFactory = GeometryFactory()
        val newTriangles = mutableListOf<Triangle>()
        val newColors = mutableMapOf<Triangle, com.badlogic.gdx.graphics.Color>()
        val newSelected = mutableSetOf<Triangle>()

        val groups = triangles.groupBy { planeKey(it) }
        groups.values.forEach { group ->
            if (group.isEmpty()) {
                return@forEach
            }
            val base = group.first()
            val normal = Vector3(base.b).sub(base.a).crs(Vector3(base.c).sub(base.a))
            if (normal.len2() <= epsilonSq) {
                group.forEach { tri ->
                    newTriangles.add(tri)
                    newColors[tri] = colors[tri] ?: defaultColor
                    if (selected.contains(tri)) {
                        newSelected.add(tri)
                    }
                }
                return@forEach
            }
            val basis = planeBasisFromNormal(normal)
            val origin = Vector3(base.a)
            val polygons = group.map { tri ->
                val coords = arrayOf(
                    toCoord(tri.a, origin, basis),
                    toCoord(tri.b, origin, basis),
                    toCoord(tri.c, origin, basis),
                    toCoord(tri.a, origin, basis)
                )
                geometryFactory.createPolygon(coords)
            }

            val union = try {
                UnaryUnionOp.union(polygons)
            } catch (ex: Exception) {
                null
            }

            if (union == null || union.isEmpty) {
                group.forEach { tri ->
                    newTriangles.add(tri)
                    newColors[tri] = colors[tri] ?: defaultColor
                    if (selected.contains(tri)) {
                        newSelected.add(tri)
                    }
                }
                return@forEach
            }

            val hadSelection = group.any { selected.contains(it) }
            val groupColor = colors[group.first()] ?: defaultColor
            val polygonsToTriangulate = collectPolygons(union)
            if (polygonsToTriangulate.isEmpty()) {
                group.forEach { tri ->
                    newTriangles.add(tri)
                    newColors[tri] = colors[tri] ?: defaultColor
                    if (selected.contains(tri)) {
                        newSelected.add(tri)
                    }
                }
                return@forEach
            }

            val keepInPlane = keepPoints.filter { pointOnPlane(it, normal, -normal.dot(origin)) }
            var triangulationFailed = false
            polygonsToTriangulate.forEach { polygon ->
                val triangles2d = tryTriangulatePolygon(polygon, keepInPlane, origin, basis, geometryFactory)
                if (triangles2d == null || triangles2d.isEmpty()) {
                    triangulationFailed = true
                    return@forEach
                }
                triangles2d.forEach { tri2d ->
                    val coords = tri2d.coordinates
                    if (coords.size < 4) {
                        return@forEach
                    }
                    val a = fromCoord(coords[0], origin, basis)
                    val b = fromCoord(coords[1], origin, basis)
                    val c = fromCoord(coords[2], origin, basis)
                    val tri = Triangle(a, b, c)
                    newTriangles.add(tri)
                    newColors[tri] = com.badlogic.gdx.graphics.Color(groupColor)
                    if (hadSelection) {
                        newSelected.add(tri)
                    }
                }
            }

            if (triangulationFailed) {
                group.forEach { tri ->
                    newTriangles.add(tri)
                    newColors[tri] = colors[tri] ?: defaultColor
                    if (selected.contains(tri)) {
                        newSelected.add(tri)
                    }
                }
            }
        }

        triangles.clear()
        triangles.addAll(newTriangles)
        colors.clear()
        colors.putAll(newColors)
        selected.clear()
        selected.addAll(newSelected)
    }

    private fun removeClosingPoint(points: List<Vector3>): List<Vector3> {
        if (points.size < 2) {
            return points
        }
        val first = points.first()
        val last = points.last()
        return if (first.dst2(last) <= epsilonSq) {
            points.dropLast(1)
        } else {
            points
        }
    }

    private fun splitTriangleByLine(tri: Triangle, start: Vector3, end: Vector3): List<Triangle>? {
        val normal = Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a))
        if (normal.len2() <= epsilonSq) {
            return null
        }
        normal.nor()
        val planeD = -normal.dot(tri.a)
        val distStart = normal.dot(start) + planeD
        val distEnd = normal.dot(end) + planeD
        if (kotlin.math.abs(distStart) > planeEps || kotlin.math.abs(distEnd) > planeEps) {
            return null
        }
        val startProj = Vector3(start).mulAdd(normal, -distStart)
        val endProj = Vector3(end).mulAdd(normal, -distEnd)

        val basis = planeBasisFromNormal(normal)
        val origin = Vector3(tri.a)
        val geometryFactory = GeometryFactory()

        val triCoords = arrayOf(
            toCoord(tri.a, origin, basis),
            toCoord(tri.b, origin, basis),
            toCoord(tri.c, origin, basis),
            toCoord(tri.a, origin, basis)
        )
        val start2d = toCoord(startProj, origin, basis)
        val end2d = toCoord(endProj, origin, basis)
        val dir2d = Vector2(
            (end2d.x - start2d.x).toFloat(),
            (end2d.y - start2d.y).toFloat()
        )
        if (dir2d.len2() <= epsilonSq) {
            return null
        }
        val linePoint = Vector2(start2d.x.toFloat(), start2d.y.toFloat())
        val tri2d = listOf(
            Vector2(triCoords[0].x.toFloat(), triCoords[0].y.toFloat()),
            Vector2(triCoords[1].x.toFloat(), triCoords[1].y.toFloat()),
            Vector2(triCoords[2].x.toFloat(), triCoords[2].y.toFloat())
        )
        val pos = clipPolygonByLine(tri2d, linePoint, dir2d, true)
        val neg = clipPolygonByLine(tri2d, linePoint, dir2d, false)
        if (pos.size < 3 || neg.size < 3) {
            return null
        }
        val result = mutableListOf<Triangle>()
        triangulateConvex(pos).forEach { polyTri ->
            result.add(Triangle(
                fromCoord(polyTri[0], origin, basis),
                fromCoord(polyTri[1], origin, basis),
                fromCoord(polyTri[2], origin, basis)
            ))
        }
        triangulateConvex(neg).forEach { polyTri ->
            result.add(Triangle(
                fromCoord(polyTri[0], origin, basis),
                fromCoord(polyTri[1], origin, basis),
                fromCoord(polyTri[2], origin, basis)
            ))
        }
        return if (result.isEmpty()) null else result
    }

    private fun splitTriangleByLineCut(tri: Triangle, start: Vector3, end: Vector3): List<Triangle>? {
        val normal = Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a))
        if (normal.len2() <= epsilonSq) {
            return null
        }
        normal.nor()
        val planeD = -normal.dot(tri.a)
        val distStart = normal.dot(start) + planeD
        val distEnd = normal.dot(end) + planeD
        if (kotlin.math.abs(distStart) > planeEps || kotlin.math.abs(distEnd) > planeEps) {
            return null
        }
        val startProj = Vector3(start).mulAdd(normal, -distStart)
        val endProj = Vector3(end).mulAdd(normal, -distEnd)

        val basis = planeBasisFromNormal(normal)
        val origin = Vector3(tri.a)

        val tri2d = listOf(
            to2d(tri.a, origin, basis),
            to2d(tri.b, origin, basis),
            to2d(tri.c, origin, basis)
        )
        val start2d = to2d(startProj, origin, basis)
        val end2d = to2d(endProj, origin, basis)
        val dir2d = Vector2(end2d).sub(start2d)
        if (dir2d.len2() <= cutEps2d * cutEps2d) {
            return null
        }
        val linePoint = Vector2(start2d)
        val pos = clipPolygonByLineEps(tri2d, linePoint, dir2d, true, cutEps2d)
        val neg = clipPolygonByLineEps(tri2d, linePoint, dir2d, false, cutEps2d)
        if (pos.size < 3 || neg.size < 3) {
            val sideA = lineSide(tri2d[0], linePoint, dir2d)
            val sideB = lineSide(tri2d[1], linePoint, dir2d)
            val sideC = lineSide(tri2d[2], linePoint, dir2d)
            val sides = floatArrayOf(sideA, sideB, sideC)
            val zeros = sides.count { kotlin.math.abs(it) <= cutEps2d }
            if (zeros >= 2) {
                return null
            }
            val signs = sides.map { if (it > cutEps2d) 1 else if (it < -cutEps2d) -1 else 0 }
            val verts = listOf(
                Pair(tri2d[0], tri.a),
                Pair(tri2d[1], tri.b),
                Pair(tri2d[2], tri.c)
            )
            val intersections = mutableListOf<Pair<Vector2, Pair<Vector3, Vector3>>>()
            val edges = listOf(
                Triple(0, 1, Pair(tri.a, tri.b)),
                Triple(1, 2, Pair(tri.b, tri.c)),
                Triple(2, 0, Pair(tri.c, tri.a))
            )
            edges.forEach { (i, j, edge3d) ->
                val si = signs[i]
                val sj = signs[j]
                if (si == 0 && sj == 0) {
                    return@forEach
                }
                if (si == 0 && sj != 0 || sj == 0 && si != 0 || si != sj) {
                    val inter = intersectLineSegment2DEps(tri2d[i], tri2d[j], linePoint, dir2d, cutEps2d)
                    if (inter != null) {
                        intersections.add(Pair(inter, edge3d))
                    }
                }
            }
            if (intersections.size < 2) {
                return null
            }
            val inter1 = intersections[0].first
            val inter2 = intersections[1].first
            val i1 = from2d(inter1, origin, basis)
            val i2 = from2d(inter2, origin, basis)
            val posIndices = signs.withIndex().filter { it.value > 0 }.map { it.index }
            val negIndices = signs.withIndex().filter { it.value < 0 }.map { it.index }
            val result = mutableListOf<Triangle>()
            if (posIndices.size == 1 && negIndices.size == 2) {
                val p = verts[posIndices[0]].second
                val n1 = verts[negIndices[0]].second
                val n2 = verts[negIndices[1]].second
                result.add(Triangle(Vector3(p), Vector3(i1), Vector3(i2)))
                result.add(Triangle(Vector3(n1), Vector3(n2), Vector3(i2)))
                result.add(Triangle(Vector3(n1), Vector3(i2), Vector3(i1)))
                return result
            }
            if (negIndices.size == 1 && posIndices.size == 2) {
                val n = verts[negIndices[0]].second
                val p1 = verts[posIndices[0]].second
                val p2 = verts[posIndices[1]].second
                result.add(Triangle(Vector3(n), Vector3(i1), Vector3(i2)))
                result.add(Triangle(Vector3(p1), Vector3(p2), Vector3(i2)))
                result.add(Triangle(Vector3(p1), Vector3(i2), Vector3(i1)))
                return result
            }
            return null
        }
        val result = mutableListOf<Triangle>()
        triangulateConvex(pos).forEach { polyTri ->
            result.add(
                Triangle(
                    from2d(polyTri[0], origin, basis),
                    from2d(polyTri[1], origin, basis),
                    from2d(polyTri[2], origin, basis)
                )
            )
        }
        triangulateConvex(neg).forEach { polyTri ->
            result.add(
                Triangle(
                    from2d(polyTri[0], origin, basis),
                    from2d(polyTri[1], origin, basis),
                    from2d(polyTri[2], origin, basis)
                )
            )
        }
        return if (result.isEmpty()) null else result
    }

    private fun splitTriangleBySegmentCut(
        tri: Triangle,
        start: Vector3,
        end: Vector3,
        origin: Vector3,
        basis: PlaneBasis
    ): List<Triangle>? {
        val normal = Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a))
        if (normal.len2() <= epsilonSq) {
            return null
        }
        normal.nor()
        val planeD = -normal.dot(tri.a)
        val distStart = normal.dot(start) + planeD
        val distEnd = normal.dot(end) + planeD
        if (kotlin.math.abs(distStart) > planeEps || kotlin.math.abs(distEnd) > planeEps) {
            return null
        }
        val s2 = to2d(start, origin, basis)
        val e2 = to2d(end, origin, basis)
        val segMid = Vector2((s2.x + e2.x) * 0.5f, (s2.y + e2.y) * 0.5f)
        val a2 = to2d(tri.a, origin, basis)
        val b2 = to2d(tri.b, origin, basis)
        val c2 = to2d(tri.c, origin, basis)

        val midInside = pointInTriangle2d(segMid, a2, b2, c2) || pointOnTriangleBoundary2d(segMid, a2, b2, c2)
        if (!midInside) {
            return null
        }

        if (segmentCollinearOverlap2d(s2, e2, a2, b2) ||
            segmentCollinearOverlap2d(s2, e2, b2, c2) ||
            segmentCollinearOverlap2d(s2, e2, c2, a2)
        ) {
            return null
        }

        val points = mutableListOf<Vector2>()
        if (pointInTriangle2d(s2, a2, b2, c2) || pointOnTriangleBoundary2d(s2, a2, b2, c2)) {
            addIntersection(points, s2)
        }
        if (pointInTriangle2d(e2, a2, b2, c2) || pointOnTriangleBoundary2d(e2, a2, b2, c2)) {
            addIntersection(points, e2)
        }
        addIntersection(points, segmentIntersection2d(s2, e2, a2, b2))
        addIntersection(points, segmentIntersection2d(s2, e2, b2, c2))
        addIntersection(points, segmentIntersection2d(s2, e2, c2, a2))
            if (points.size < 2) {
                val sOnEdge = pointOnTriangleBoundary2d(s2, a2, b2, c2)
                val eOnEdge = pointOnTriangleBoundary2d(e2, a2, b2, c2)
                if (sOnEdge && !eOnEdge) {
                    val proj = projectPointToTriangleBoundary(e2, a2, b2, c2)
                    if (proj != null) {
                        addIntersection(points, proj)
                    }
                } else if (eOnEdge && !sOnEdge) {
                    val proj = projectPointToTriangleBoundary(s2, a2, b2, c2)
                    if (proj != null) {
                        addIntersection(points, proj)
                    }
                }
                if (points.size < 2) {
                    return null
                }
            }

        val dir = Vector2(e2).sub(s2)
        val len2 = dir.len2()
        if (len2 <= cutEps2d * cutEps2d) {
            return null
        }
        val sorted = points
            .distinctBy { Pair(kotlin.math.round(it.x / cutEps2d), kotlin.math.round(it.y / cutEps2d)) }
            .sortedBy { p -> ((p.x - s2.x) * dir.x + (p.y - s2.y) * dir.y) / len2 }
        if (sorted.size < 2) {
            return null
        }
        val p1 = sorted.first()
        val p2 = sorted.last()
        if (p1.dst2(p2) <= cutEps2d * cutEps2d) {
            return null
        }
        val tri2d = listOf(a2, b2, c2)
        val linePoint = Vector2(p1)
        val lineDir = Vector2(p2).sub(p1)
        if (lineDir.len2() <= cutEps2d * cutEps2d) {
            return null
        }
        val pos = clipPolygonByLineEps(tri2d, linePoint, lineDir, true, cutEps2d)
        val neg = clipPolygonByLineEps(tri2d, linePoint, lineDir, false, cutEps2d)
        if (pos.size < 3 || neg.size < 3) {
            return null
        }
        val result = mutableListOf<Triangle>()
        triangulateConvex(pos).forEach { polyTri ->
            result.add(
                Triangle(
                    from2d(polyTri[0], origin, basis),
                    from2d(polyTri[1], origin, basis),
                    from2d(polyTri[2], origin, basis)
                )
            )
        }
        triangulateConvex(neg).forEach { polyTri ->
            result.add(
                Triangle(
                    from2d(polyTri[0], origin, basis),
                    from2d(polyTri[1], origin, basis),
                    from2d(polyTri[2], origin, basis)
                )
            )
        }
        return if (result.isEmpty()) null else result
    }

    private fun splitTriangleBySegment(tri: Triangle, start: Vector3, end: Vector3): List<Triangle>? {
        val normal = Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a))
        if (normal.len2() <= epsilonSq) {
            return null
        }
        normal.nor()
        val planeD = -normal.dot(tri.a)
        val distStart = normal.dot(start) + planeD
        val distEnd = normal.dot(end) + planeD
        if (kotlin.math.abs(distStart) > planeEps || kotlin.math.abs(distEnd) > planeEps) {
            return null
        }
        val basis = planeBasisFromNormal(normal)
        val origin = Vector3(tri.a)
        val a2 = to2d(tri.a, origin, basis)
        val b2 = to2d(tri.b, origin, basis)
        val c2 = to2d(tri.c, origin, basis)
        val s2 = to2d(start, origin, basis)
        val e2 = to2d(end, origin, basis)

        if (segmentCollinearOverlap2d(s2, e2, a2, b2) ||
            segmentCollinearOverlap2d(s2, e2, b2, c2) ||
            segmentCollinearOverlap2d(s2, e2, c2, a2)
        ) {
            return null
        }

        val startOnEdge = pointOnTriangleBoundary2d(s2, a2, b2, c2)
        val endOnEdge = pointOnTriangleBoundary2d(e2, a2, b2, c2)
        val startInside = !startOnEdge && pointInTriangle2d(s2, a2, b2, c2)
        val endInside = !endOnEdge && pointInTriangle2d(e2, a2, b2, c2)

        if (startInside && endInside) {
            return splitTriangleByLine(tri, start, end)
        }
        if (startOnEdge && endInside) {
            return splitTriangleFromEdgePoint(tri, start, end, s2, a2, b2, c2)
        }
        if (endOnEdge && startInside) {
            return splitTriangleFromEdgePoint(tri, end, start, e2, a2, b2, c2)
        }
        if (startInside) {
            return splitTriangleAtPoint(tri, start)
        }
        if (endInside) {
            return splitTriangleAtPoint(tri, end)
        }

        val intersections = mutableListOf<Vector2>()
        if (startOnEdge) {
            addIntersection(intersections, s2)
        }
        if (endOnEdge) {
            addIntersection(intersections, e2)
        }
        addIntersection(intersections, segmentIntersection2d(s2, e2, a2, b2))
        addIntersection(intersections, segmentIntersection2d(s2, e2, b2, c2))
        addIntersection(intersections, segmentIntersection2d(s2, e2, c2, a2))
        if (intersections.size < 2) {
            val mid = Vector2((s2.x + e2.x) * 0.5f, (s2.y + e2.y) * 0.5f)
            val midInside = pointInTriangle2d(mid, a2, b2, c2) || pointOnTriangleBoundary2d(mid, a2, b2, c2)
            if (!midInside || intersections.isEmpty()) {
                return null
            }
        }
        return splitTriangleByLine(tri, start, end)
    }

    private fun splitTriangleAtPoint(tri: Triangle, point: Vector3): List<Triangle> {
        return listOf(
            Triangle(Vector3(point), Vector3(tri.a), Vector3(tri.b)),
            Triangle(Vector3(point), Vector3(tri.b), Vector3(tri.c)),
            Triangle(Vector3(point), Vector3(tri.c), Vector3(tri.a))
        )
    }

    private fun splitTriangleFromEdgePoint(
        tri: Triangle,
        edgePoint: Vector3,
        insidePoint: Vector3,
        edgePoint2d: Vector2,
        a2: Vector2,
        b2: Vector2,
        c2: Vector2
    ): List<Triangle>? {
        val edgeIndex = edgeIndexForPoint(edgePoint2d, a2, b2, c2)
        val a = Vector3(tri.a)
        val b = Vector3(tri.b)
        val c = Vector3(tri.c)
        val p = Vector3(edgePoint)
        val q = Vector3(insidePoint)

        val nearA = edgePoint2d.dst2(a2) <= cutEps2d * cutEps2d
        val nearB = edgePoint2d.dst2(b2) <= cutEps2d * cutEps2d
        val nearC = edgePoint2d.dst2(c2) <= cutEps2d * cutEps2d
        if (nearA) {
            return listOf(
                Triangle(Vector3(a), Vector3(b), Vector3(q)),
                Triangle(Vector3(a), Vector3(q), Vector3(c))
            )
        }
        if (nearB) {
            return listOf(
                Triangle(Vector3(b), Vector3(c), Vector3(q)),
                Triangle(Vector3(b), Vector3(q), Vector3(a))
            )
        }
        if (nearC) {
            return listOf(
                Triangle(Vector3(c), Vector3(a), Vector3(q)),
                Triangle(Vector3(c), Vector3(q), Vector3(b))
            )
        }

        return when (edgeIndex) {
            0 -> listOf(
                Triangle(Vector3(a), Vector3(p), Vector3(q)),
                Triangle(Vector3(p), Vector3(b), Vector3(q)),
                Triangle(Vector3(q), Vector3(b), Vector3(c))
            )
            1 -> listOf(
                Triangle(Vector3(b), Vector3(p), Vector3(q)),
                Triangle(Vector3(p), Vector3(c), Vector3(q)),
                Triangle(Vector3(q), Vector3(c), Vector3(a))
            )
            2 -> listOf(
                Triangle(Vector3(c), Vector3(p), Vector3(q)),
                Triangle(Vector3(p), Vector3(a), Vector3(q)),
                Triangle(Vector3(q), Vector3(a), Vector3(b))
            )
            else -> null
        }
    }

    private fun to2d(point: Vector3, origin: Vector3, basis: PlaneBasis): Vector2 {
        val rel = Vector3(point).sub(origin)
        return Vector2(rel.dot(basis.axisU), rel.dot(basis.axisV))
    }

    private fun edgeIndexForPoint(p: Vector2, a: Vector2, b: Vector2, c: Vector2): Int {
        if (pointOnSegment2d(p, a, b)) {
            return 0
        }
        if (pointOnSegment2d(p, b, c)) {
            return 1
        }
        if (pointOnSegment2d(p, c, a)) {
            return 2
        }
        return -1
    }

    private fun pointInTriangle2d(p: Vector2, a: Vector2, b: Vector2, c: Vector2): Boolean {
        val v0x = c.x - a.x
        val v0y = c.y - a.y
        val v1x = b.x - a.x
        val v1y = b.y - a.y
        val v2x = p.x - a.x
        val v2y = p.y - a.y
        val dot00 = v0x * v0x + v0y * v0y
        val dot01 = v0x * v1x + v0y * v1y
        val dot02 = v0x * v2x + v0y * v2y
        val dot11 = v1x * v1x + v1y * v1y
        val dot12 = v1x * v2x + v1y * v2y
        val denom = dot00 * dot11 - dot01 * dot01
        if (kotlin.math.abs(denom) < cutEps2d) {
            return false
        }
        val invDenom = 1f / denom
        val u = (dot11 * dot02 - dot01 * dot12) * invDenom
        val v = (dot00 * dot12 - dot01 * dot02) * invDenom
        return u >= -cutEps2d && v >= -cutEps2d && u + v <= 1f + cutEps2d
    }

    private fun pointOnTriangleBoundary2d(p: Vector2, a: Vector2, b: Vector2, c: Vector2): Boolean {
        return pointOnSegment2d(p, a, b) || pointOnSegment2d(p, b, c) || pointOnSegment2d(p, c, a)
    }

    private fun pointOnSegment2d(p: Vector2, a: Vector2, b: Vector2): Boolean {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val apx = p.x - a.x
        val apy = p.y - a.y
        val cross = abx * apy - aby * apx
        if (kotlin.math.abs(cross) > cutEps2d) {
            return false
        }
        val dot = apx * abx + apy * aby
        if (dot < -cutEps2d) {
            return false
        }
        val lenSq = abx * abx + aby * aby
        if (dot > lenSq + cutEps2d) {
            return false
        }
        return true
    }

    private fun segmentIntersection2d(a: Vector2, b: Vector2, c: Vector2, d: Vector2): Vector2? {
        val r = Vector2(b).sub(a)
        val s = Vector2(d).sub(c)
        val denom = r.x * s.y - r.y * s.x
        if (kotlin.math.abs(denom) < cutEps2d) {
            return null
        }
        val cma = Vector2(c).sub(a)
        val t = (cma.x * s.y - cma.y * s.x) / denom
        val u = (cma.x * r.y - cma.y * r.x) / denom
        return if (t >= -cutEps2d && t <= 1f + cutEps2d && u >= -cutEps2d && u <= 1f + cutEps2d) {
            Vector2(a.x + r.x * t, a.y + r.y * t)
        } else {
            null
        }
    }

    private fun segmentCollinearOverlap2d(a: Vector2, b: Vector2, c: Vector2, d: Vector2): Boolean {
        val ab = Vector2(b).sub(a)
        val ac = Vector2(c).sub(a)
        val ad = Vector2(d).sub(a)
        val cross1 = ab.x * ac.y - ab.y * ac.x
        val cross2 = ab.x * ad.y - ab.y * ad.x
        if (kotlin.math.abs(cross1) > cutEps2d || kotlin.math.abs(cross2) > cutEps2d) {
            return false
        }
        val minAx = kotlin.math.min(a.x, b.x) - cutEps2d
        val maxAx = kotlin.math.max(a.x, b.x) + cutEps2d
        val minAy = kotlin.math.min(a.y, b.y) - cutEps2d
        val maxAy = kotlin.math.max(a.y, b.y) + cutEps2d
        val minCx = kotlin.math.min(c.x, d.x)
        val maxCx = kotlin.math.max(c.x, d.x)
        val minCy = kotlin.math.min(c.y, d.y)
        val maxCy = kotlin.math.max(c.y, d.y)
        val overlapX = maxCx >= minAx && minCx <= maxAx
        val overlapY = maxCy >= minAy && minCy <= maxAy
        return overlapX && overlapY
    }

    private fun addIntersection(list: MutableList<Vector2>, point: Vector2?) {
        if (point == null) {
            return
        }
        if (list.none { it.dst2(point) <= cutEps2d * cutEps2d }) {
            list.add(point)
        }
    }

    private fun projectPointToTriangleBoundary(p: Vector2, a: Vector2, b: Vector2, c: Vector2): Vector2? {
        val candidates = listOf(
            closestPointOnSegment2d(p, a, b),
            closestPointOnSegment2d(p, b, c),
            closestPointOnSegment2d(p, c, a)
        )
        return candidates.minByOrNull { it.dst2(p) }
    }

    private fun closestPointOnSegment2d(p: Vector2, a: Vector2, b: Vector2): Vector2 {
        val ab = Vector2(b).sub(a)
        val lenSq = ab.len2()
        if (lenSq <= cutEps2d * cutEps2d) {
            return Vector2(a)
        }
        val t = ((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / lenSq
        val clamped = t.coerceIn(0f, 1f)
        return Vector2(a.x + ab.x * clamped, a.y + ab.y * clamped)
    }

    private fun segmentIntersectsTriangle2d(s: Vector2, e: Vector2, a: Vector2, b: Vector2, c: Vector2): Boolean {
        if (segmentCollinearOverlap2d(s, e, a, b) ||
            segmentCollinearOverlap2d(s, e, b, c) ||
            segmentCollinearOverlap2d(s, e, c, a)
        ) {
            return false
        }
        if (pointInTriangle2d(s, a, b, c) || pointInTriangle2d(e, a, b, c)) {
            return true
        }
        if (segmentIntersection2d(s, e, a, b) != null) return true
        if (segmentIntersection2d(s, e, b, c) != null) return true
        if (segmentIntersection2d(s, e, c, a) != null) return true
        return false
    }

    private fun dedupePoints(points: List<Vector3>, tolerance: Float): List<Vector3> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val scale = 1f / tolerance
        val seen = mutableSetOf<Triple<Int, Int, Int>>()
        val result = mutableListOf<Vector3>()
        points.forEach { p ->
            val key = Triple(
                kotlin.math.round(p.x * scale).toInt(),
                kotlin.math.round(p.y * scale).toInt(),
                kotlin.math.round(p.z * scale).toInt()
            )
            if (seen.add(key)) {
                result.add(p)
            }
        }
        return result
    }

    private fun pointOnPlane(point: Vector3, normal: Vector3, d: Float): Boolean {
        val dist = normal.dot(point) + d
        return kotlin.math.abs(dist) <= planeEps
    }

    private fun clipPolygonByLineEps(
        polygon: List<Vector2>,
        linePoint: Vector2,
        lineDir: Vector2,
        keepPositive: Boolean,
        eps: Float
    ): List<Vector2> {
        if (polygon.isEmpty()) {
            return emptyList()
        }
        val output = mutableListOf<Vector2>()
        val n = polygon.size
        for (i in 0 until n) {
            val a = polygon[i]
            val b = polygon[(i + 1) % n]
            val da = lineSide(a, linePoint, lineDir)
            val db = lineSide(b, linePoint, lineDir)
            val aInside = if (keepPositive) da >= -eps else da <= eps
            val bInside = if (keepPositive) db >= -eps else db <= eps
            if (aInside && bInside) {
                output.add(Vector2(b))
            } else if (aInside && !bInside) {
                val inter = intersectLineSegment2DEps(a, b, linePoint, lineDir, eps)
                if (inter != null) {
                    output.add(inter)
                }
            } else if (!aInside && bInside) {
                val inter = intersectLineSegment2DEps(a, b, linePoint, lineDir, eps)
                if (inter != null) {
                    output.add(inter)
                }
                output.add(Vector2(b))
            }
        }
        return output
    }

    private fun intersectLineSegment2DEps(
        a: Vector2,
        b: Vector2,
        linePoint: Vector2,
        lineDir: Vector2,
        eps: Float
    ): Vector2? {
        val segDir = Vector2(b).sub(a)
        val denom = lineDir.x * segDir.y - lineDir.y * segDir.x
        if (kotlin.math.abs(denom) < eps) {
            return null
        }
        val ax = a.x - linePoint.x
        val ay = a.y - linePoint.y
        val t = (lineDir.x * ay - lineDir.y * ax) / denom
        return if (t >= -eps && t <= 1f + eps) {
            Vector2(a.x + segDir.x * t, a.y + segDir.y * t)
        } else {
            null
        }
    }

    private fun toCoord(point: Vector3, origin: Vector3, basis: PlaneBasis): Coordinate {
        val rel = Vector3(point).sub(origin)
        val u = rel.dot(basis.axisU) * jtsScale
        val v = rel.dot(basis.axisV) * jtsScale
        return Coordinate(u, v)
    }

    private fun from2d(point: Vector2, origin: Vector3, basis: PlaneBasis): Vector3 {
        return Vector3(origin)
            .mulAdd(basis.axisU, point.x)
            .mulAdd(basis.axisV, point.y)
    }

    private fun fromCoord(coord: Coordinate, origin: Vector3, basis: PlaneBasis): Vector3 {
        val u = (coord.x / jtsScale).toFloat()
        val v = (coord.y / jtsScale).toFloat()
        return Vector3(origin).mulAdd(basis.axisU, u).mulAdd(basis.axisV, v)
    }

    private fun fromCoord(coord: Vector2, origin: Vector3, basis: PlaneBasis): Vector3 {
        val u = coord.x / jtsScale.toFloat()
        val v = coord.y / jtsScale.toFloat()
        return Vector3(origin).mulAdd(basis.axisU, u).mulAdd(basis.axisV, v)
    }

    private fun clipPolygonByLine(
        polygon: List<Vector2>,
        linePoint: Vector2,
        lineDir: Vector2,
        keepPositive: Boolean
    ): List<Vector2> {
        if (polygon.isEmpty()) {
            return emptyList()
        }
        val output = mutableListOf<Vector2>()
        val n = polygon.size
        for (i in 0 until n) {
            val a = polygon[i]
            val b = polygon[(i + 1) % n]
            val da = lineSide(a, linePoint, lineDir)
            val db = lineSide(b, linePoint, lineDir)
            val aInside = if (keepPositive) da >= -epsilon2d else da <= epsilon2d
            val bInside = if (keepPositive) db >= -epsilon2d else db <= epsilon2d
            if (aInside && bInside) {
                output.add(Vector2(b))
            } else if (aInside && !bInside) {
                val inter = intersectLineSegment2D(a, b, linePoint, lineDir)
                if (inter != null) {
                    output.add(inter)
                }
            } else if (!aInside && bInside) {
                val inter = intersectLineSegment2D(a, b, linePoint, lineDir)
                if (inter != null) {
                    output.add(inter)
                }
                output.add(Vector2(b))
            }
        }
        return output
    }

    private fun lineSide(point: Vector2, linePoint: Vector2, lineDir: Vector2): Float {
        val dx = point.x - linePoint.x
        val dy = point.y - linePoint.y
        return lineDir.x * dy - lineDir.y * dx
    }

    private fun intersectLineSegment2D(
        a: Vector2,
        b: Vector2,
        linePoint: Vector2,
        lineDir: Vector2
    ): Vector2? {
        val segDir = Vector2(b).sub(a)
        val denom = lineDir.x * segDir.y - lineDir.y * segDir.x
        if (kotlin.math.abs(denom) < epsilon2d) {
            return null
        }
        val ax = a.x - linePoint.x
        val ay = a.y - linePoint.y
        val t = (lineDir.x * ay - lineDir.y * ax) / denom
        return if (t >= -epsilon && t <= 1f + epsilon) {
            Vector2(a.x + segDir.x * t, a.y + segDir.y * t)
        } else {
            null
        }
    }

    private fun triangulateConvex(polygon: List<Vector2>): List<List<Vector2>> {
        if (polygon.size < 3) {
            return emptyList()
        }
        val result = mutableListOf<List<Vector2>>()
        for (i in 1 until polygon.size - 1) {
            result.add(listOf(polygon[0], polygon[i], polygon[i + 1]))
        }
        return result
    }

    private fun extendLine(line: LineString, polygon: Polygon): LineString {
        val coords = line.coordinates
        if (coords.size < 2) {
            return line
        }
        val a = coords.first()
        val b = coords.last()
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len <= 1e-9) {
            return line
        }
        val env = polygon.envelopeInternal
        val diag = kotlin.math.hypot(env.width, env.height)
        val scale = (diag + len) * 2.0
        val ux = dx / len
        val uy = dy / len
        val extendedA = Coordinate(a.x - ux * scale, a.y - uy * scale)
        val extendedB = Coordinate(b.x + ux * scale, b.y + uy * scale)
        val geometryFactory = GeometryFactory()
        return geometryFactory.createLineString(arrayOf(extendedA, extendedB))
    }

    private fun collectPolygons(geometry: Geometry): List<Polygon> {
        val result = mutableListOf<Polygon>()
        when (geometry) {
            is Polygon -> result.add(geometry)
            else -> {
                for (i in 0 until geometry.numGeometries) {
                    val child = geometry.getGeometryN(i)
                    if (child is Polygon) {
                        result.add(child)
                    }
                }
            }
        }
        return result
    }

    private fun tryTriangulatePolygon(
        polygon: Polygon,
        keepPoints: List<Vector3>,
        origin: Vector3,
        basis: PlaneBasis,
        geometryFactory: GeometryFactory
    ): List<Polygon>? {
        return try {
            val boundaryCoords = polygon.exteriorRing.coordinates.toMutableList()
            keepPoints.forEach { point ->
                boundaryCoords.add(toCoord(point, origin, basis))
            }
            val sites = geometryFactory.createMultiPointFromCoords(boundaryCoords.toTypedArray())
            val builder = DelaunayTriangulationBuilder()
            builder.setSites(sites)
            val triangles = builder.getTriangles(geometryFactory)
            val result = mutableListOf<Polygon>()
            for (i in 0 until triangles.numGeometries) {
                val tri = triangles.getGeometryN(i)
                if (tri is Polygon && polygon.covers(tri)) {
                    result.add(tri)
                }
            }
            result
        } catch (ex: Exception) {
            null
        }
    }

    private fun computeNormal(points: List<Vector3>): Vector3 {
        var nx = 0f
        var ny = 0f
        var nz = 0f
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            nx += (current.y - next.y) * (current.z + next.z)
            ny += (current.z - next.z) * (current.x + next.x)
            nz += (current.x - next.x) * (current.y + next.y)
        }
        val normal = Vector3(nx, ny, nz)
        if (normal.len2() <= epsilonSq) {
            return Vector3(0f, 1f, 0f)
        }
        return normal.nor()
    }

    private fun triangulatePolygon(points: List<Vector3>) {
        if (points.size < 3) {
            return
        }
        val normal = computeNormal(points)
        val projected = projectTo2D(points, normal)
        val indices = points.indices.toMutableList()
        if (signedArea(projected) < 0f) {
            indices.reverse()
        }
        var guard = 0
        while (indices.size > 2 && guard < 10000) {
            guard++
            var earFound = false
            for (i in indices.indices) {
                val prev = indices[(i - 1 + indices.size) % indices.size]
                val curr = indices[i]
                val next = indices[(i + 1) % indices.size]
                if (!isConvex(projected[prev], projected[curr], projected[next])) {
                    continue
                }
                if (containsPoint(projected, indices, prev, curr, next)) {
                    continue
                }
                addTriangle(points[prev], points[curr], points[next])
                indices.removeAt(i)
                earFound = true
                break
            }
            if (!earFound) {
                break
            }
        }
    }

    private fun triangleIntersectsAabb(triangle: Triangle, min: Vector3, max: Vector3): Boolean {
        if (pointInsideAabb(triangle.a, min, max) ||
            pointInsideAabb(triangle.b, min, max) ||
            pointInsideAabb(triangle.c, min, max)) {
            return true
        }
        if (segmentIntersectsAabb(triangle.a, triangle.b, min, max)) return true
        if (segmentIntersectsAabb(triangle.b, triangle.c, min, max)) return true
        if (segmentIntersectsAabb(triangle.c, triangle.a, min, max)) return true
        return false
    }

    private fun pointInsideAabb(point: Vector3, min: Vector3, max: Vector3): Boolean {
        return point.x >= min.x - epsilon && point.x <= max.x + epsilon &&
            point.y >= min.y - epsilon && point.y <= max.y + epsilon &&
            point.z >= min.z - epsilon && point.z <= max.z + epsilon
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

    private fun projectTo2D(points: List<Vector3>, normal: Vector3): List<Vector2> {
        val absX = kotlin.math.abs(normal.x)
        val absY = kotlin.math.abs(normal.y)
        val absZ = kotlin.math.abs(normal.z)
        return points.map { p ->
            when {
                absX >= absY && absX >= absZ -> Vector2(p.y, p.z)
                absY >= absX && absY >= absZ -> Vector2(p.x, p.z)
                else -> Vector2(p.x, p.y)
            }
        }
    }

    private fun signedArea(points: List<Vector2>): Float {
        var area = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            area += (a.x * b.y - b.x * a.y)
        }
        return area * 0.5f
    }

    private fun isConvex(a: Vector2, b: Vector2, c: Vector2): Boolean {
        val cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        return cross > epsilon
    }

    private fun containsPoint(points: List<Vector2>, indices: List<Int>, a: Int, b: Int, c: Int): Boolean {
        val pa = points[a]
        val pb = points[b]
        val pc = points[c]
        for (index in indices) {
            if (index == a || index == b || index == c) {
                continue
            }
            val p = points[index]
            if (pointInTriangle(p, pa, pb, pc)) {
                return true
            }
        }
        return false
    }

    private fun pointInTriangle(p: Vector2, a: Vector2, b: Vector2, c: Vector2): Boolean {
        val v0x = c.x - a.x
        val v0y = c.y - a.y
        val v1x = b.x - a.x
        val v1y = b.y - a.y
        val v2x = p.x - a.x
        val v2y = p.y - a.y
        val dot00 = v0x * v0x + v0y * v0y
        val dot01 = v0x * v1x + v0y * v1y
        val dot02 = v0x * v2x + v0y * v2y
        val dot11 = v1x * v1x + v1y * v1y
        val dot12 = v1x * v2x + v1y * v2y
        val invDen = 1f / (dot00 * dot11 - dot01 * dot01)
        val u = (dot11 * dot02 - dot01 * dot12) * invDen
        val v = (dot00 * dot12 - dot01 * dot02) * invDen
        return u >= -epsilon && v >= -epsilon && u + v <= 1f + epsilon
    }

    private fun intersectRayTriangle(
        ray: com.badlogic.gdx.math.collision.Ray,
        tri: Triangle
    ): Hit? {
        val edge1 = Vector3(tri.b).sub(tri.a)
        val edge2 = Vector3(tri.c).sub(tri.a)
        val pvec = Vector3(ray.direction).crs(edge2)
        val det = edge1.dot(pvec)
        if (kotlin.math.abs(det) < epsilon) {
            return null
        }
        val invDet = 1f / det
        val tvec = Vector3(ray.origin).sub(tri.a)
        val u = tvec.dot(pvec) * invDet
        if (u < 0f || u > 1f) {
            return null
        }
        val qvec = Vector3(tvec).crs(edge1)
        val v = ray.direction.dot(qvec) * invDet
        if (v < 0f || u + v > 1f) {
            return null
        }
        val t = edge2.dot(qvec) * invDet
        if (t <= 0f) {
            return null
        }
        val point = Vector3(ray.origin).mulAdd(ray.direction, t)
        val normal = edge1.crs(edge2).nor()
        return Hit(tri, point, normal, t)
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private data class EdgeKey(val a: VertexKey, val b: VertexKey)

    private data class Edge(val from: VertexKey, val to: VertexKey)

    private fun planeKey(triangle: Triangle): List<Int> {
        val normal = Vector3(triangle.b).sub(triangle.a).crs(Vector3(triangle.c).sub(triangle.a))
        if (normal.len2() <= epsilonSq) {
            return listOf(0, 0, 0, 0)
        }
        normal.nor()
        if (normal.y < 0f || (normal.y == 0f && (normal.x < 0f || (normal.x == 0f && normal.z < 0f)))) {
            normal.scl(-1f)
        }
        val d = -normal.dot(triangle.a)
        return listOf(
            quant(normal.x),
            quant(normal.y),
            quant(normal.z),
            quant(d)
        )
    }

    private fun edgeKey(a: Vector3, b: Vector3): EdgeKey {
        val va = vertexKey(a)
        val vb = vertexKey(b)
        return if (compareKeys(va, vb) <= 0) EdgeKey(va, vb) else EdgeKey(vb, va)
    }

    private fun quant(value: Float): Int = kotlin.math.round(value / epsilon).toInt()

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(quant(point.x), quant(point.y), quant(point.z))
    }

    private fun mergeCoplanarGroup(group: List<Triangle>, output: MutableList<Triangle>) {
        if (group.size == 1) {
            output.add(group.first())
            return
        }
        val keyToPoint = mutableMapOf<VertexKey, Vector3>()
        val edgeCount = mutableMapOf<EdgeKey, Int>()
        group.forEach { tri ->
            val a = vertexKey(tri.a).also { keyToPoint.putIfAbsent(it, Vector3(tri.a)) }
            val b = vertexKey(tri.b).also { keyToPoint.putIfAbsent(it, Vector3(tri.b)) }
            val c = vertexKey(tri.c).also { keyToPoint.putIfAbsent(it, Vector3(tri.c)) }
            listOf(Pair(a, b), Pair(b, c), Pair(c, a)).forEach { (u, v) ->
                val edgeKey = if (compareKeys(u, v) <= 0) EdgeKey(u, v) else EdgeKey(v, u)
                edgeCount[edgeKey] = (edgeCount[edgeKey] ?: 0) + 1
            }
        }

        val boundaryEdges = edgeCount.filterValues { it == 1 }.keys
        if (boundaryEdges.size < 3) {
            output.addAll(group)
            return
        }
        val adjacency = mutableMapOf<VertexKey, MutableList<Edge>>()
        boundaryEdges.forEach { edge ->
            adjacency.getOrPut(edge.a) { mutableListOf() }.add(Edge(edge.a, edge.b))
            adjacency.getOrPut(edge.b) { mutableListOf() }.add(Edge(edge.b, edge.a))
        }

        val loop = mutableListOf<Vector3>()
        val used = mutableSetOf<EdgeKey>()
        val startEdge = boundaryEdges.first()
        var current = startEdge.a
        var next = startEdge.b
        val startPoint = keyToPoint[current]
        if (startPoint == null) {
            output.addAll(group)
            return
        }
        loop.add(startPoint)
        while (true) {
            val edgeKey = if (compareKeys(current, next) <= 0) EdgeKey(current, next) else EdgeKey(next, current)
            used.add(edgeKey)
            loop.add(keyToPoint[next] ?: break)
            val candidates = adjacency[next].orEmpty()
            val candidate = candidates.firstOrNull { edge ->
                val key = if (compareKeys(edge.from, edge.to) <= 0) EdgeKey(edge.from, edge.to) else EdgeKey(edge.to, edge.from)
                !used.contains(key)
            } ?: break
            current = candidate.from
            next = candidate.to
            if (next == startEdge.a) {
                break
            }
        }

        if (loop.size < 4) {
            output.addAll(group)
            return
        }
        val normal = computeNormal(loop)
        val origin = loop.first()
        for (i in 1 until loop.size - 2) {
            val a = origin
            val b = loop[i]
            val c = loop[i + 1]
            if (Vector3(b).sub(a).crs(Vector3(c).sub(a)).dot(normal) < 0f) {
                output.add(Triangle(Vector3(a), Vector3(c), Vector3(b)))
            } else {
                output.add(Triangle(Vector3(a), Vector3(b), Vector3(c)))
            }
        }
    }

    private fun compareKeys(a: VertexKey, b: VertexKey): Int {
        if (a.x != b.x) return a.x.compareTo(b.x)
        if (a.y != b.y) return a.y.compareTo(b.y)
        return a.z.compareTo(b.z)
    }

    private fun notifyChange(invalidateSpatialIndex: Boolean = true) {
        if (invalidateSpatialIndex) {
            rebuildTriangleLookup()
            invalidateSpatialIndex()
        }
        markVisualChanged()
        if (!suppressChange) {
            onChange?.invoke()
        }
    }

    fun notifyExternalChange() {
        rehashSelectionAndColors()
        notifyChange()
    }

    fun visualVersion(): Long = visualVersion

    fun rayCandidates(ray: com.badlogic.gdx.math.collision.Ray): List<Triangle> = triangleCandidatesForRay(ray)

    fun aabbCandidates(min: Vector3, max: Vector3): List<Triangle> = trianglesIntersectingQuery(min, max)

    fun processAsyncMaintenance(nowMs: Long = System.currentTimeMillis()) {
        asyncSpatialIndex.process(
            nowMs = nowMs,
            snapshotProvider = {
                triangles.map { triangle ->
                    IndexedAabbSnapshot<String>(
                        item = triangle.id,
                        min = triangleMin(triangle),
                        max = triangleMax(triangle)
                    )
                }
            },
            apply = { result ->
                spatialIndex = result.index
                hasSpatialBounds = result.hasBounds
                spatialBoundsMin.set(result.boundsMin)
                spatialBoundsMax.set(result.boundsMax)
                spatialBoundsDirty = false
                spatialIndexDirty = false
            }
        )
    }

    private fun markVisualChanged() {
        visualVersion++
    }

    private fun rehashSelectionAndColors() {
        if (selected.isNotEmpty()) {
            val currentSelection = selected.toList()
            selected.clear()
            selected.addAll(currentSelection)
        }
        if (colors.isNotEmpty()) {
            val currentColors = colors.entries.map { it.key to com.badlogic.gdx.graphics.Color(it.value) }
            colors.clear()
            currentColors.forEach { (triangle, color) ->
                colors[triangle] = color
            }
        }
    }

    private fun triangleCandidatesForRay(ray: com.badlogic.gdx.math.collision.Ray): List<Triangle> {
        if (spatialIndexDirty) {
            return triangles
        }
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
        ).mapNotNull { id -> trianglesById[id] }
    }

    private fun trianglesIntersectingQuery(min: Vector3, max: Vector3): List<Triangle> {
        if (spatialIndexDirty) {
            return triangles.filter { tri -> triangleIntersectsAabb(tri, min, max) }
        }
        return if (hasSpatialBounds) spatialIndex.queryAabb(min, max).mapNotNull { id -> trianglesById[id] } else emptyList()
    }

    private fun invalidateSpatialIndex() {
        spatialIndexDirty = true
        spatialBoundsDirty = true
        hasSpatialBounds = false
        asyncSpatialIndex.markDirty()
    }

    private fun clearSpatialIndexState() {
        spatialIndex.clear()
        spatialIndexDirty = false
        spatialBoundsDirty = false
        hasSpatialBounds = false
        asyncSpatialIndex.markCurrent()
    }

    private fun ensureSpatialBounds() {
        if (!spatialBoundsDirty) {
            return
        }
        hasSpatialBounds = false
        triangles.forEach { triangle ->
            expandSpatialBounds(triangleMin(triangle), triangleMax(triangle))
        }
        spatialBoundsDirty = false
    }

    private fun registerTriangle(triangle: Triangle) {
        val min = triangleMin(triangle)
        val max = triangleMax(triangle)
        spatialIndex.upsertAabb(min, max, triangle.id)
        expandSpatialBounds(min, max)
    }

    private fun unregisterTriangle(triangle: Triangle) {
        spatialIndex.removeByKey(triangle.id)
        spatialBoundsDirty = true
        hasSpatialBounds = false
    }

    private fun expandSpatialBounds(min: Vector3, max: Vector3) {
        if (!hasSpatialBounds) {
            spatialBoundsMin.set(min)
            spatialBoundsMax.set(max)
            hasSpatialBounds = true
            return
        }
        spatialBoundsMin.x = kotlin.math.min(spatialBoundsMin.x, min.x)
        spatialBoundsMin.y = kotlin.math.min(spatialBoundsMin.y, min.y)
        spatialBoundsMin.z = kotlin.math.min(spatialBoundsMin.z, min.z)
        spatialBoundsMax.x = kotlin.math.max(spatialBoundsMax.x, max.x)
        spatialBoundsMax.y = kotlin.math.max(spatialBoundsMax.y, max.y)
        spatialBoundsMax.z = kotlin.math.max(spatialBoundsMax.z, max.z)
    }

    private fun triangleMin(triangle: Triangle): Vector3 {
        return Vector3(
            kotlin.math.min(triangle.a.x, kotlin.math.min(triangle.b.x, triangle.c.x)) - epsilon,
            kotlin.math.min(triangle.a.y, kotlin.math.min(triangle.b.y, triangle.c.y)) - epsilon,
            kotlin.math.min(triangle.a.z, kotlin.math.min(triangle.b.z, triangle.c.z)) - epsilon
        )
    }

    private fun triangleMax(triangle: Triangle): Vector3 {
        return Vector3(
            kotlin.math.max(triangle.a.x, kotlin.math.max(triangle.b.x, triangle.c.x)) + epsilon,
            kotlin.math.max(triangle.a.y, kotlin.math.max(triangle.b.y, triangle.c.y)) + epsilon,
            kotlin.math.max(triangle.a.z, kotlin.math.max(triangle.b.z, triangle.c.z)) + epsilon
        )
    }

    private fun rebuildTriangleLookup() {
        trianglesById.clear()
        triangles.forEach { triangle ->
            trianglesById[triangle.id] = triangle
        }
    }
}

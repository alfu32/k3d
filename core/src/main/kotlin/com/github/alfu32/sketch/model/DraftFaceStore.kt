package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.tools.PlaneBasis
import com.github.alfu32.sketch.tools.planeBasisFromNormal
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.operation.polygonize.Polygonizer
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder

class DraftFaceStore(
    private val defaultColor: com.badlogic.gdx.graphics.Color = com.badlogic.gdx.graphics.Color(0.93f, 0.93f, 0.93f, 1f)
) {
    data class Triangle(val a: Vector3, val b: Vector3, val c: Vector3)
    data class Hit(val triangle: Triangle, val point: Vector3, val normal: Vector3, val t: Float)

    private val triangles = mutableListOf<Triangle>()
    private val selected = mutableSetOf<Triangle>()
    private val colors = mutableMapOf<Triangle, com.badlogic.gdx.graphics.Color>()
    private var onChange: (() -> Unit)? = null
    private var suppressChange = false
    private val epsilon = 1e-4f
    private val epsilonSq = epsilon * epsilon
    private val jtsScale = 10000.0
    private val epsilon2d = (epsilon * jtsScale).toFloat()
    private val planeEps = 1e-2f

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

    fun addTriangle(a: Vector3, b: Vector3, c: Vector3) {
        val triangle = Triangle(Vector3(a), Vector3(b), Vector3(c))
        triangles.add(triangle)
        colors[triangle] = com.badlogic.gdx.graphics.Color(defaultColor)
        notifyChange()
    }

    fun addTriangle(a: Vector3, b: Vector3, c: Vector3, color: com.badlogic.gdx.graphics.Color) {
        val triangle = Triangle(Vector3(a), Vector3(b), Vector3(c))
        triangles.add(triangle)
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

    fun getSelected(): Set<Triangle> = selected

    fun colorFor(triangle: Triangle): com.badlogic.gdx.graphics.Color {
        return colors[triangle] ?: defaultColor
    }

    fun isSelected(triangle: Triangle): Boolean = selected.contains(triangle)

    fun addSelection(triangle: Triangle): Boolean = selected.add(triangle)

    fun removeSelection(triangle: Triangle) {
        selected.remove(triangle)
    }

    fun toggleSelection(triangle: Triangle) {
        if (!selected.add(triangle)) {
            selected.remove(triangle)
        }
    }

    fun clearSelection() {
        selected.clear()
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
                colors[flipped] = colors.remove(tri) ?: com.badlogic.gdx.graphics.Color(defaultColor)
                newTriangles.add(flipped)
                newSelected.add(flipped)
            } else {
                newTriangles.add(tri)
            }
        }
        triangles.clear()
        triangles.addAll(newTriangles)
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
        selected.clear()
        notifyChange()
        return before - triangles.size
    }

    fun clearAll() {
        if (triangles.isNotEmpty()) {
            triangles.clear()
            selected.clear()
            colors.clear()
            notifyChange()
        }
    }

    fun paintSelected(color: com.badlogic.gdx.graphics.Color): Int {
        if (selected.isEmpty()) {
            return 0
        }
        selected.forEach { triangle ->
            colors[triangle] = com.badlogic.gdx.graphics.Color(color)
        }
        notifyChange()
        return selected.size
    }

    fun paintTriangle(triangle: Triangle, color: com.badlogic.gdx.graphics.Color) {
        colors[triangle] = com.badlogic.gdx.graphics.Color(color)
        notifyChange()
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
        selected.clear()
        selected.addAll(newSelected)
        colors.clear()
        colors.putAll(newColors)
        notifyChange()
        return newSelected.size
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
        triangles.forEach { tri ->
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
        triangles.forEach { tri ->
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

    private fun pointOnPlane(point: Vector3, normal: Vector3, d: Float): Boolean {
        val dist = normal.dot(point) + d
        return kotlin.math.abs(dist) <= planeEps
    }

    private fun toCoord(point: Vector3, origin: Vector3, basis: PlaneBasis): Coordinate {
        val rel = Vector3(point).sub(origin)
        val u = rel.dot(basis.axisU) * jtsScale
        val v = rel.dot(basis.axisV) * jtsScale
        return Coordinate(u, v)
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

    private fun notifyChange() {
        if (!suppressChange) {
            onChange?.invoke()
        }
    }

    fun notifyExternalChange() {
        notifyChange()
    }
}

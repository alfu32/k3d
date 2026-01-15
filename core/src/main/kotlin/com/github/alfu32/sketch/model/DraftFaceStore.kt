package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3

class DraftFaceStore(
    private val defaultColor: com.badlogic.gdx.graphics.Color = com.badlogic.gdx.graphics.Color(0.93f, 0.93f, 0.93f, 1f)
) {
    data class Triangle(val a: Vector3, val b: Vector3, val c: Vector3)
    data class Hit(val triangle: Triangle, val point: Vector3, val normal: Vector3, val t: Float)

    private val triangles = mutableListOf<Triangle>()
    private val selected = mutableSetOf<Triangle>()
    private val colors = mutableMapOf<Triangle, com.badlogic.gdx.graphics.Color>()
    private val epsilon = 1e-4f
    private val epsilonSq = epsilon * epsilon

    fun addTriangle(a: Vector3, b: Vector3, c: Vector3) {
        val triangle = Triangle(Vector3(a), Vector3(b), Vector3(c))
        triangles.add(triangle)
        colors[triangle] = com.badlogic.gdx.graphics.Color(defaultColor)
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
        return before - triangles.size
    }

    fun paintSelected(color: com.badlogic.gdx.graphics.Color): Int {
        if (selected.isEmpty()) {
            return 0
        }
        selected.forEach { triangle ->
            colors[triangle] = com.badlogic.gdx.graphics.Color(color)
        }
        return selected.size
    }

    fun paintTriangle(triangle: Triangle, color: com.badlogic.gdx.graphics.Color) {
        colors[triangle] = com.badlogic.gdx.graphics.Color(color)
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
}

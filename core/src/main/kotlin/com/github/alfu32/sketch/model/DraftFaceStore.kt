package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3

class DraftFaceStore {
    data class Triangle(val a: Vector3, val b: Vector3, val c: Vector3)

    private val triangles = mutableListOf<Triangle>()
    private val epsilon = 1e-4f
    private val epsilonSq = epsilon * epsilon

    fun addTriangle(a: Vector3, b: Vector3, c: Vector3) {
        triangles.add(Triangle(Vector3(a), Vector3(b), Vector3(c)))
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
        val origin = ordered.first()
        for (i in 1 until ordered.size - 1) {
            addTriangle(origin, ordered[i], ordered[i + 1])
        }
    }

    fun getTriangles(): List<Triangle> = triangles

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
}

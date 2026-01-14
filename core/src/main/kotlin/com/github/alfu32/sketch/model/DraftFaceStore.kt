package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3

class DraftFaceStore {
    data class Triangle(val a: Vector3, val b: Vector3, val c: Vector3)

    private val triangles = mutableListOf<Triangle>()

    fun addTriangle(a: Vector3, b: Vector3, c: Vector3) {
        triangles.add(Triangle(Vector3(a), Vector3(b), Vector3(c)))
    }

    fun getTriangles(): List<Triangle> = triangles
}

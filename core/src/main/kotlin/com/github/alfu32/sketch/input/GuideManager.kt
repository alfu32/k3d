package com.github.alfu32.sketch.input

import com.badlogic.gdx.math.Vector3

class GuideManager {
    data class GuideBasis(
        val origin: Vector3,
        val axisU: Vector3,
        val axisV: Vector3,
        val axisW: Vector3
    )

    private val gridGuides = mutableListOf<GuideBasis>()
    private val axisGuides = mutableListOf<GuideBasis>()

    fun addGridGuide(point: Vector3, normal: Vector3? = null) {
        gridGuides.add(buildBasis(point, normal))
    }

    fun addAxisGuide(point: Vector3, normal: Vector3? = null) {
        axisGuides.add(buildBasis(point, normal))
    }

    fun getGridGuides(): List<GuideBasis> = gridGuides

    fun getAxisGuides(): List<GuideBasis> = axisGuides

    fun hasGridGuides(): Boolean = gridGuides.isNotEmpty()

    fun hasAxisGuides(): Boolean = axisGuides.isNotEmpty()

    fun clear() {
        gridGuides.clear()
        axisGuides.clear()
    }

    private fun buildBasis(origin: Vector3, normal: Vector3?): GuideBasis {
        val n = (normal ?: Vector3(0f, 1f, 0f)).cpy().nor()
        val up = Vector3(0f, 1f, 0f)
        val forward = Vector3(0f, 0f, 1f)

        var axisV = projectOntoPlane(up, n)
        if (axisV.len2() <= 1e-6f) {
            axisV = projectOntoPlane(forward, n)
        }
        if (axisV.len2() <= 1e-6f) {
            axisV = Vector3(1f, 0f, 0f)
        } else {
            axisV.nor()
        }

        val axisU = Vector3(axisV).crs(n).nor()
        return GuideBasis(Vector3(origin), axisU, axisV, n)
    }

    private fun projectOntoPlane(vector: Vector3, normal: Vector3): Vector3 {
        val dot = vector.dot(normal)
        return Vector3(vector).mulAdd(normal, -dot)
    }
}

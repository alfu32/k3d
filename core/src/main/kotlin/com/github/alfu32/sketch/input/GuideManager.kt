package com.github.alfu32.sketch.input

import com.badlogic.gdx.math.Vector3

class GuideManager {
    private val gridGuides = mutableListOf<Vector3>()
    private val axisGuides = mutableListOf<Vector3>()

    fun addGridGuide(point: Vector3) {
        gridGuides.add(Vector3(point))
    }

    fun addAxisGuide(point: Vector3) {
        axisGuides.add(Vector3(point))
    }

    fun getGridGuides(): List<Vector3> = gridGuides

    fun getAxisGuides(): List<Vector3> = axisGuides

    fun hasGridGuides(): Boolean = gridGuides.isNotEmpty()

    fun hasAxisGuides(): Boolean = axisGuides.isNotEmpty()

    fun clear() {
        gridGuides.clear()
        axisGuides.clear()
    }
}

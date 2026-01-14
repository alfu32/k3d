package com.github.alfu32.sketch.input

import com.badlogic.gdx.math.Vector3

class GuideManager {
    var gridGuideActive: Boolean = false
        private set
    var axisGuideActive: Boolean = false
        private set

    val gridGuideCenter: Vector3 = Vector3()
    val axisGuideCenter: Vector3 = Vector3()

    fun setGridGuideAt(point: Vector3) {
        gridGuideCenter.set(point)
        gridGuideActive = true
    }

    fun setAxisGuideAt(point: Vector3) {
        axisGuideCenter.set(point)
        axisGuideActive = true
    }

    fun toggleGridGuide(point: Vector3) {
        if (gridGuideActive) {
            gridGuideCenter.set(point)
        } else {
            setGridGuideAt(point)
        }
    }

    fun toggleAxisGuide(point: Vector3) {
        if (axisGuideActive) {
            axisGuideCenter.set(point)
        } else {
            setAxisGuideAt(point)
        }
    }

    fun clear() {
        gridGuideActive = false
        axisGuideActive = false
    }
}

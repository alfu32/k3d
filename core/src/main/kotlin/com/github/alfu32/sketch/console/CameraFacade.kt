package com.github.alfu32.sketch.console

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3

class CameraFacade(
    private val camera: PerspectiveCamera,
    private val target: Vector3,
    private val apply: () -> Unit
) {
    fun position(): Vector3 = Vector3(camera.position)

    fun target(): Vector3 = Vector3(target)

    fun setPosition(x: Float, y: Float, z: Float) {
        camera.position.set(x, y, z)
        apply()
    }

    fun setTarget(x: Float, y: Float, z: Float) {
        target.set(x, y, z)
        apply()
    }

    fun lookAt(x: Float, y: Float, z: Float) {
        camera.lookAt(x, y, z)
        apply()
    }

    override fun toString(): String = "Camera(pos=${camera.position}, target=$target)"
}

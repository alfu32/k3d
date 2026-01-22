package com.github.alfu32.sketch

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3

class ShiftCameraController(camera: PerspectiveCamera) : CameraInputController(camera) {
    private var translating = false
    private val panPlane = Plane()
    private val panAnchor = Vector3()
    private val tmp = Vector3()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
        if (shift) {
            translateButton = Input.Buttons.RIGHT
            rotateButton = -1
        } else {
            rotateButton = Input.Buttons.RIGHT
            translateButton = -1
        }
        translating = shift && button == translateButton
        if (translating) {
            val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
            val normal = Vector3(camera.direction).nor()
            panPlane.set(normal, target)
            if (Intersector.intersectRayPlane(ray, panPlane, tmp)) {
                panAnchor.set(tmp)
            } else {
                translating = false
            }
        }
        return super.touchDown(screenX, screenY, pointer, button)
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (translating) {
            val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
            if (Intersector.intersectRayPlane(ray, panPlane, tmp)) {
                tmp.sub(panAnchor)
                camera.position.sub(tmp)
                target.sub(tmp)
                camera.update()
            }
            return true
        }
        return super.touchDragged(screenX, screenY, pointer)
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == translateButton) {
            translating = false
        }
        return super.touchUp(screenX, screenY, pointer, button)
    }
}

package com.github.alfu32.sketch

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.math.Vector3

class ShiftCameraController(camera: PerspectiveCamera) : CameraInputController(camera) {
    private var translating = false
    private val panAnchor = Vector3()
    private val tmp = Vector3()
    private val zoomDir = Vector3()
    private var zoomSpeed = 1f
    private var panDepth = 0f

    init {
        forwardKey = -1
        backwardKey = -1
        rotateRightKey = -1
        rotateLeftKey = -1
        forwardButton = -1
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
        if (shift) {
            translateButton = -1
            rotateButton = -1
        } else {
            rotateButton = Input.Buttons.RIGHT
            translateButton = -1
        }
        translating = shift && button == Input.Buttons.RIGHT
        if (translating) {
            val depth = camera.project(Vector3(target)).z
            panDepth = depth
            panAnchor.set(screenX.toFloat(), screenY.toFloat(), panDepth)
            camera.unproject(panAnchor)
        }
        return super.touchDown(screenX, screenY, pointer, button)
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (translating) {
            tmp.set(screenX.toFloat(), screenY.toFloat(), panDepth)
            camera.unproject(tmp)
            tmp.sub(panAnchor)
            camera.position.sub(tmp)
            target.sub(tmp)
            camera.update()
            return true
        }
        return super.touchDragged(screenX, screenY, pointer)
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == Input.Buttons.RIGHT) {
            translating = false
        }
        return super.touchUp(screenX, screenY, pointer, button)
    }

    override fun zoom(amount: Float): Boolean {
        zoomDir.set(target).sub(camera.position)
        val distance = zoomDir.len()
        if (distance <= 1e-4f) {
            return false
        }
        zoomDir.scl(1f / distance)
        var step = amount * zoomSpeed
        val minDistance = 0.1f
        if (distance - step < minDistance) {
            step = distance - minDistance
        }
        camera.position.mulAdd(zoomDir, step)
        camera.update()
        return true
    }
}

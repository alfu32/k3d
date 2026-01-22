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
    private val panStartPos = Vector3()
    private val panStartTarget = Vector3()
    private val panStartGrab = Vector3()
    private val tmp = Vector3()
    private val tmpDir = Vector3()
    private val zoomDir = Vector3()
    private var zoomSpeed = 1f

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
            panStartPos.set(camera.position)
            panStartTarget.set(target)
            val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
            val screenPlane = Plane(Vector3(camera.direction).nor(), panStartTarget)
            if (!Intersector.intersectRayPlane(ray, screenPlane, panStartGrab)) {
                translating = false
                return super.touchDown(screenX, screenY, pointer, button)
            }
        }
        return super.touchDown(screenX, screenY, pointer, button)
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (translating) {
            val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
            tmpDir.set(ray.direction).nor()
            tmp.set(panStartGrab).sub(panStartPos)
            val t = tmp.dot(tmpDir)
            tmp.mulAdd(tmpDir, -t)
            camera.position.set(panStartPos).add(tmp)
            target.set(panStartTarget).add(tmp)
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

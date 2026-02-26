package com.github.alfu32.sketch

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class ShiftCameraController(
    camera: PerspectiveCamera,
    private val pickModelPoint: (screenX: Int, screenY: Int) -> Vector3?
) : CameraInputController(camera) {
    private var translating = false
    private val panStartPos = Vector3()
    private val panStartTarget = Vector3()
    private val panStartGrab = Vector3()
    private val panPlaneNormal = Vector3()
    private val panPlanePoint = Vector3()
    private val tmp = Vector3()
    private val tmpDir = Vector3()
    private val zoomDir = Vector3()
    private var zoomSpeed = 1f
    private val targetGlueDistanceThresholdZoomIn = 1f
    private val targetGlueDistanceThresholdZoomOut = 30f

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
            setPanPlaneNormal()
            panPlanePoint.set(target)
            val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
            val picked = intersectRayPlane(ray.origin, ray.direction, panPlanePoint, panPlaneNormal)
            if (picked == null) {
                translating = false
                return super.touchDown(screenX, screenY, pointer, button)
            }
            panStartGrab.set(picked)
        }
        return super.touchDown(screenX, screenY, pointer, button)
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (translating) {
            val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
            val hit = intersectRayPlane(ray.origin, ray.direction, panPlanePoint, panPlaneNormal) ?: return true
            tmp.set(panStartGrab).sub(hit).scl(0.92f)
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
        val allowTargetGlue =
            (step > 0f && distance > targetGlueDistanceThresholdZoomIn) ||
                (step < 0f && distance < targetGlueDistanceThresholdZoomOut)

        val projectedTarget = Vector3(target)
        camera.project(
            projectedTarget,
            0f,
            0f,
            Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat()
        )
        val targetScreenX = projectedTarget.x.toInt()
        val targetScreenY = (Gdx.graphics.height - projectedTarget.y).toInt()
        val lockHit = if (
            allowTargetGlue &&
            projectedTarget.z in 0f..1f &&
            targetScreenX in 0 until Gdx.graphics.width &&
            targetScreenY in 0 until Gdx.graphics.height
        ) {
            pickModelPoint(targetScreenX, targetScreenY)?.takeIf { hit ->
                hit.dst(camera.position) <= distance + 1e-2f
            }
        } else {
            null
        }

        if (lockHit != null) {
            val lockDistance = lockHit.dst(camera.position)
            if (lockDistance - step < minDistance) {
                step = lockDistance - minDistance
            }
        }
        camera.position.mulAdd(zoomDir, step)
        if (lockHit == null) {
            target.mulAdd(zoomDir, step)
        } else {
            target.set(lockHit)
            camera.lookAt(target)
        }
        camera.update()
        return true
    }

    private fun setPanPlaneNormal() {
        val view = Vector3(target).sub(camera.position)
        val horiz = sqrt(view.x * view.x + view.z * view.z)
        val angle = abs(atan2(view.y, horiz))
        val threshold = (PI * 0.25).toFloat()
        if (angle >= threshold || horiz <= 1e-4f) {
            panPlaneNormal.set(0f, 1f, 0f)
            return
        }
        panPlaneNormal.set(view.x, 0f, view.z)
        if (panPlaneNormal.len2() <= 1e-6f) {
            panPlaneNormal.set(0f, 1f, 0f)
        } else {
            panPlaneNormal.nor()
        }
    }

    private fun intersectRayPlane(
        rayOrigin: Vector3,
        rayDir: Vector3,
        planePoint: Vector3,
        planeNormal: Vector3
    ): Vector3? {
        val denom = planeNormal.dot(rayDir)
        if (kotlin.math.abs(denom) < 1e-6f) {
            return null
        }
        val t = Vector3(planePoint).sub(rayOrigin).dot(planeNormal) / denom
        if (t < 0f) {
            return null
        }
        return Vector3(rayOrigin).mulAdd(rayDir, t)
    }
}

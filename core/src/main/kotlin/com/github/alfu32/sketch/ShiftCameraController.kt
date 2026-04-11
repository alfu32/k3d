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
    var panButton: Int = Input.Buttons.MIDDLE
    private var translating = false
    private var orbitRotating = false
    private var orbitRotateAroundPosition = false
    private var orbitRotateMoved = false
    private var lastRotateScreenX = 0
    private var lastRotateScreenY = 0
    private val panStartPos = Vector3()
    private val panStartTarget = Vector3()
    private val panStartGrab = Vector3()
    private val panPlaneNormal = Vector3()
    private val panPlanePoint = Vector3()
    private val tmp = Vector3()
    private val tmpDir = Vector3()
    private val zoomDir = Vector3()
    private val rotateView = Vector3()
    private val rotateAxis = Vector3()
    private var zoomSpeed = 1f
    private val minOrbitZoomTargetDistance = 5f

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
        val alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)
        if (shift) {
            translateButton = -1
            rotateButton = -1
        } else {
            rotateButton = if (alt) -1 else Input.Buttons.RIGHT
            translateButton = -1
        }
        translating = button == panButton || (shift && button == Input.Buttons.RIGHT)
        orbitRotating = !translating && button == Input.Buttons.RIGHT
        orbitRotateAroundPosition = orbitRotating && alt
        if (orbitRotating) {
            orbitRotateMoved = false
            lastRotateScreenX = screenX
            lastRotateScreenY = screenY
        }
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
        if (orbitRotating && orbitRotateAroundPosition) {
            rotateTargetAroundPosition(screenX, screenY)
            return true
        }
        if (orbitRotating) {
            rotatePositionAroundTarget(screenX, screenY)
            return true
        }
        return super.touchDragged(screenX, screenY, pointer)
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == Input.Buttons.RIGHT || button == panButton) {
            val shouldSnapTarget = orbitRotating && orbitRotateMoved && !translating
            translating = false
            orbitRotating = false
            orbitRotateAroundPosition = false
            orbitRotateMoved = false
            if (shouldSnapTarget) {
                snapTargetToViewedModelPoint()
            }
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
        val minDistance = minOrbitZoomTargetDistance
        val zoomingIn = step > 0f

        // If the orbit distance is very small, treat zoom as a pure dolly and
        // keep the current close framing instead of forcing target glue/clamping.
        if ((zoomingIn && distance - step <= minDistance + 1e-4f) || distance < minDistance) {
            camera.position.mulAdd(zoomDir, step)
            target.mulAdd(zoomDir, step)
            camera.lookAt(target)
            camera.update()
            return true
        }

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
        } else if (distance - step < minDistance) {
            // Keep a practical minimum orbit distance even when no surface glue target is found.
            step = distance - minDistance
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

    private fun rotateTargetAroundPosition(screenX: Int, screenY: Int) {
        val dxPixels = screenX - lastRotateScreenX
        val dyPixels = screenY - lastRotateScreenY
        lastRotateScreenX = screenX
        lastRotateScreenY = screenY
        if (dxPixels == 0 && dyPixels == 0) {
            return
        }
        orbitRotateMoved = true

        val width = Gdx.graphics.width.coerceAtLeast(1)
        val height = Gdx.graphics.height.coerceAtLeast(1)
        val yawDeg = (dxPixels.toFloat() / width.toFloat()) * rotateAngle
        val pitchDeg = (dyPixels.toFloat() / height.toFloat()) * rotateAngle

        rotateView.set(target).sub(camera.position)
        if (rotateView.len2() <= 1e-8f) {
            return
        }

        if (abs(yawDeg) > 1e-5f) {
            rotateAxis.set(camera.up)
            if (rotateAxis.len2() > 1e-8f) {
                rotateView.rotate(rotateAxis.nor(), yawDeg)
            }
        }

        if (abs(pitchDeg) > 1e-5f) {
            rotateAxis.set(rotateView).crs(camera.up)
            if (rotateAxis.len2() > 1e-8f) {
                rotateView.rotate(rotateAxis.nor(), pitchDeg)
            }
        }

        target.set(camera.position).add(rotateView)
        camera.lookAt(target)
        camera.update()
    }

    private fun rotatePositionAroundTarget(screenX: Int, screenY: Int) {
        val width = Gdx.graphics.width.coerceAtLeast(1).toFloat()
        val height = Gdx.graphics.height.coerceAtLeast(1).toFloat()
        val deltaX = (screenX - lastRotateScreenX).toFloat() / width
        val deltaY = (lastRotateScreenY - screenY).toFloat() / height
        lastRotateScreenX = screenX
        lastRotateScreenY = screenY
        if (abs(deltaX) <= 1e-7f && abs(deltaY) <= 1e-7f) {
            return
        }
        orbitRotateMoved = true

        rotateAxis.set(camera.direction).crs(camera.up)
        rotateAxis.y = 0f
        if (rotateAxis.len2() > 1e-8f && abs(deltaY) > 1e-7f) {
            camera.rotateAround(target, rotateAxis.nor(), deltaY * rotateAngle)
        }
        if (abs(deltaX) > 1e-7f) {
            camera.rotateAround(target, Vector3.Y, deltaX * -rotateAngle)
        }
        camera.update()
    }

    private fun snapTargetToViewedModelPoint() {
        val projectedTarget = Vector3(target)
        camera.project(
            projectedTarget,
            0f,
            0f,
            Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat()
        )
        if (projectedTarget.z !in 0f..1f) return
        val targetScreenX = projectedTarget.x.toInt()
        val targetScreenY = (Gdx.graphics.height - projectedTarget.y).toInt()
        if (targetScreenX !in 0 until Gdx.graphics.width || targetScreenY !in 0 until Gdx.graphics.height) return

        val hit = pickModelPoint(targetScreenX, targetScreenY) ?: return
        val toHit = Vector3(hit).sub(camera.position)
        val hitDistance = toHit.len()
        if (hitDistance <= 1e-6f) return

        if (hitDistance < minOrbitZoomTargetDistance) {
            toHit.scl(minOrbitZoomTargetDistance / hitDistance)
            target.set(camera.position).add(toHit)
        } else {
            target.set(hit)
        }
        camera.lookAt(target)
        camera.update()
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

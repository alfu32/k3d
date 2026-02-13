package com.github.alfu32.sketch

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs

class OrthographicCameraController(
    private val camera: OrthographicCamera,
    private val target: Vector3
) : InputAdapter() {
    var orbitButton: Int = Input.Buttons.RIGHT
    var panButton: Int = Input.Buttons.MIDDLE
    var zoomStep: Float = 0.1f
    var minZoom: Float = 0.05f
    var maxZoom: Float = 200f
    var orbitDegreesPerPixel: Float = 0.35f
    var maxPitchAbsY: Float = 0.995f

    private enum class DragMode { NONE, ORBIT, PAN }

    private var dragMode = DragMode.NONE
    private var lastX = 0
    private var lastY = 0
    private val worldUp = Vector3(0f, 1f, 0f)
    private val right = Vector3()
    private val upAxis = Vector3()
    private val delta = Vector3()
    private val toCamera = Vector3()
    private val viewDir = Vector3()
    private val candidate = Vector3()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button != orbitButton && button != panButton) {
            return false
        }
        dragMode = when {
            button == panButton -> DragMode.PAN
            isShiftPressed() -> DragMode.PAN
            else -> DragMode.ORBIT
        }
        lastX = screenX
        lastY = screenY
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (dragMode == DragMode.NONE) {
            return false
        }
        val dx = screenX - lastX
        val dy = screenY - lastY
        lastX = screenX
        lastY = screenY
        when (dragMode) {
            DragMode.PAN -> panByPixels(dx.toFloat(), dy.toFloat())
            DragMode.ORBIT -> orbitByPixels(dx.toFloat(), dy.toFloat())
            DragMode.NONE -> Unit
        }
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == orbitButton || button == panButton) {
            dragMode = DragMode.NONE
            return true
        }
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        val factor = 1f + amountY * zoomStep
        if (factor <= 0f) {
            return true
        }
        camera.zoom = (camera.zoom * factor).coerceIn(minZoom, maxZoom)
        camera.update()
        return true
    }

    fun update() {
        // Camera is updated on interaction and explicit view changes.
    }

    private fun isShiftPressed(): Boolean {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
    }

    private fun panByPixels(dx: Float, dy: Float) {
        val widthPx = Gdx.graphics.width.coerceAtLeast(1).toFloat()
        val heightPx = Gdx.graphics.height.coerceAtLeast(1).toFloat()
        val worldPerPixelX = camera.viewportWidth * camera.zoom / widthPx
        val worldPerPixelY = camera.viewportHeight * camera.zoom / heightPx

        right.set(camera.direction).crs(camera.up)
        if (right.len2() <= 1e-8f) {
            right.set(1f, 0f, 0f)
        } else {
            right.nor()
        }
        upAxis.set(camera.up).nor()

        delta.set(right).scl(-dx * worldPerPixelX).mulAdd(upAxis, dy * worldPerPixelY)
        camera.position.add(delta)
        target.add(delta)
        camera.update()
    }

    private fun orbitByPixels(dx: Float, dy: Float) {
        val yaw = -dx * orbitDegreesPerPixel
        val pitch = -dy * orbitDegreesPerPixel
        if (abs(yaw) <= 1e-6f && abs(pitch) <= 1e-6f) {
            return
        }
        toCamera.set(camera.position).sub(target)
        if (toCamera.len2() <= 1e-6f) {
            toCamera.set(0f, 0f, 12f)
        }

        if (abs(yaw) > 1e-6f) {
            toCamera.rotate(worldUp, yaw)
        }

        if (abs(pitch) > 1e-6f) {
            viewDir.set(toCamera).scl(-1f).nor()
            right.set(viewDir).crs(camera.up)
            if (right.len2() > 1e-8f) {
                right.nor()
                candidate.set(toCamera).rotate(right, pitch)
                val candidateDirY = Vector3(candidate).scl(-1f).nor().y
                if (abs(candidateDirY) <= maxPitchAbsY) {
                    toCamera.set(candidate)
                }
            }
        }

        camera.position.set(target).add(toCamera)
        camera.direction.set(target).sub(camera.position).nor()
        if (camera.direction.len2() <= 1e-8f) {
            camera.direction.set(0f, 0f, -1f)
        }
        right.set(camera.direction).crs(worldUp)
        if (right.len2() > 1e-8f) {
            right.nor()
            camera.up.set(right).crs(camera.direction).nor()
        } else {
            camera.up.set(camera.up).nor()
        }
        camera.update()
    }
}

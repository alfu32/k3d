package com.github.alfu32.sketch

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import kotlin.math.atan2
import kotlin.math.sqrt

class WalkthroughCameraController(
    private val camera: PerspectiveCamera,
    private val supportHeightProvider: (x: Float, z: Float, currentY: Float) -> Float,
    private val eyeHeight: Float = 6f
) : InputAdapter() {
    var lookButton: Int = Input.Buttons.RIGHT
    var moveSpeed: Float = 24f
    var lookDegreesPerPixel: Float = 0.25f
    var heightAdjustSpeed: Float = 8f
    var minEyeOffset: Float = -5f
    var maxEyeOffset: Float = 60f
    var jumpVelocity: Float = 16f
    var gravity: Float = 48f

    private var movingForward = false
    private var movingBackward = false
    private var movingLeft = false
    private var movingRight = false
    private var touchMovingForward = false
    private var touchMovingBackward = false
    private var raisingHeight = false
    private var loweringHeight = false
    private var looking = false
    private var jumpQueued = false
    private var eyeOffset = 0f
    private var jumpOffset = 0f
    private var verticalVelocity = 0f
    private var grounded = true
    private var lastX = 0
    private var lastY = 0
    private var yawDeg = 0f
    private var pitchDeg = 0f
    private val up = Vector3(0f, 1f, 0f)
    private val forward = Vector3()
    private val right = Vector3()
    private val move = Vector3()

    init {
        syncFromCamera()
    }

    fun syncFromCamera() {
        val dir = Vector3(camera.direction).nor()
        val flatLen = sqrt(dir.x * dir.x + dir.z * dir.z)
        pitchDeg = Math.toDegrees(atan2(dir.y.toDouble(), flatLen.toDouble())).toFloat().coerceIn(-89f, 89f)
        yawDeg = Math.toDegrees(atan2(dir.z.toDouble(), dir.x.toDouble())).toFloat()
        updateDirectionFromAngles()
    }

    fun update(deltaTime: Float) {
        move.setZero()
        forward.set(camera.direction.x, 0f, camera.direction.z)
        if (forward.len2() <= 1e-8f) {
            val yawRad = Math.toRadians(yawDeg.toDouble())
            forward.set(kotlin.math.cos(yawRad).toFloat(), 0f, kotlin.math.sin(yawRad).toFloat())
        }
        forward.nor()
        right.set(forward).crs(up).nor()

        if (movingForward || touchMovingForward) move.add(forward)
        if (movingBackward || touchMovingBackward) move.sub(forward)
        if (movingRight) move.add(right)
        if (movingLeft) move.sub(right)
        if (move.len2() > 1e-8f) {
            move.nor().scl(moveSpeed * deltaTime)
            camera.position.add(move)
        }

        if (raisingHeight) {
            eyeOffset = (eyeOffset + heightAdjustSpeed * deltaTime).coerceIn(minEyeOffset, maxEyeOffset)
        }
        if (loweringHeight) {
            eyeOffset = (eyeOffset - heightAdjustSpeed * deltaTime).coerceIn(minEyeOffset, maxEyeOffset)
        }

        val supportY = supportHeightProvider(camera.position.x, camera.position.z, camera.position.y)
        val standingY = supportY + eyeHeight + eyeOffset
        if (jumpQueued && grounded) {
            grounded = false
            verticalVelocity = jumpVelocity
            jumpQueued = false
        }
        if (!grounded) {
            verticalVelocity -= gravity * deltaTime
            jumpOffset += verticalVelocity * deltaTime
            if (jumpOffset <= 0f) {
                jumpOffset = 0f
                verticalVelocity = 0f
                grounded = true
            }
        }
        camera.position.y = standingY + jumpOffset
        camera.up.set(up)
        camera.update()
    }

    override fun keyDown(keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.W -> movingForward = true
            Input.Keys.S -> movingBackward = true
            Input.Keys.A, Input.Keys.LEFT -> movingLeft = true
            Input.Keys.D, Input.Keys.RIGHT -> movingRight = true
            Input.Keys.UP -> raisingHeight = true
            Input.Keys.DOWN -> loweringHeight = true
            Input.Keys.SPACE -> jumpQueued = true
            else -> return false
        }
        return true
    }

    override fun keyUp(keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.W -> movingForward = false
            Input.Keys.S -> movingBackward = false
            Input.Keys.A, Input.Keys.LEFT -> movingLeft = false
            Input.Keys.D, Input.Keys.RIGHT -> movingRight = false
            Input.Keys.UP -> raisingHeight = false
            Input.Keys.DOWN -> loweringHeight = false
            else -> return false
        }
        return true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button != lookButton) {
            return false
        }
        looking = true
        lastX = screenX
        lastY = screenY
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!looking) {
            return false
        }
        val dx = screenX - lastX
        val dy = screenY - lastY
        lastX = screenX
        lastY = screenY
        yawDeg += dx * lookDegreesPerPixel
        pitchDeg = (pitchDeg - dy * lookDegreesPerPixel).coerceIn(-89f, 89f)
        updateDirectionFromAngles()
        camera.update()
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button != lookButton) {
            return false
        }
        looking = false
        return true
    }

    fun setTouchAdvanceDirection(direction: Int) {
        touchMovingForward = direction > 0
        touchMovingBackward = direction < 0
    }

    private fun updateDirectionFromAngles() {
        val yawRad = Math.toRadians(yawDeg.toDouble())
        val pitchRad = Math.toRadians(pitchDeg.toDouble())
        val cosPitch = kotlin.math.cos(pitchRad).toFloat()
        val x = cosPitch * kotlin.math.cos(yawRad).toFloat()
        val y = kotlin.math.sin(pitchRad).toFloat()
        val z = cosPitch * kotlin.math.sin(yawRad).toFloat()
        camera.direction.set(x, y, z).nor()
    }
}

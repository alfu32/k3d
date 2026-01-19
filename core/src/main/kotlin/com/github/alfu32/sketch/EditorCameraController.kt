package com.github.alfu32.sketch

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3

class EditorCameraController(camera: Camera) : CameraInputController(camera) {
    init {
        // Disable default orbiting and panning
        rotateButton = -1 // Disable orbiting with any mouse button
        translateButton = -1 // Disable panning with any mouse button
    }
    var translatePressed = false
    var startX=0f
    var startY=0f
    var tmpV1: Vector3 = Vector3(0f, 0f, 0f,)
    var tmpV2: Vector3 = Vector3(0f, 0f, 0f,)

    fun xxtouchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        // Check if Left-Shift is pressed for panning
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
            if (!translatePressed) {
                startX = screenX.toFloat()
                startY = screenY.toFloat()
                translatePressed = true
            }
            val deltaX = (screenX - startX) * translateUnits // Adjust these values as needed
            val deltaY = (startY - screenY) * translateUnits
            startX = screenX.toFloat()
            startY = screenY.toFloat()

            // Temporarily set translateButton to enable panning
            translateButton = Input.Buttons.LEFT

            // Calculate panning vector in world coordinates
            camera.unproject(tmpV1.set(startX, startY, 0f))
            camera.unproject(tmpV2.set(startX + deltaX, startY + deltaY, 0f))
            tmpV2.sub(tmpV1)

            camera.position.add(tmpV2)
            target.add(tmpV2)
            camera.update()

            translateButton = -1 // Disable default behavior
            return true
        } else {
            translatePressed = false
        }
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        // Orbit when Left-Ctrl is pressed and dragging
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
            rotateButton = Input.Buttons.LEFT // Temporarily enable orbiting
            val result = super.touchDragged(screenX, screenY, pointer)
            rotateButton = -1 // Disable again to prevent default behavior
            return result
        }

        // Pan when Left-Shift is pressed and dragging

        // Adjusted panning logic
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
            val modelDelta = Vector3()
            val ray = camera.getPickRay(
                screenX.toFloat(), screenY.toFloat(),
                screenX.toFloat()/camera.viewportWidth, screenY.toFloat()/camera.viewportHeight,
                camera.viewportWidth,camera.viewportHeight,
            )
            val plane = Plane()
            plane.set(0f,0f,0f,0f,1f,0f)
            Intersector.intersectRayPlane(ray, plane,modelDelta)
            val panningScalar=0.5f // * (camera.position.sub(target).len())
            translateButton = Input.Buttons.LEFT // Temporarily enable panning
            // Temporarily adjust translateUnits to scale with panningScalar
            val prevTranslateUnits = translateUnits
            translateUnits *= panningScalar*modelDelta.len()
            val result = super.touchDragged(screenX, screenY, pointer)
            translateButton = -1 // Disable again
            translateUnits = prevTranslateUnits // Reset translateUnits to original
            return result
        }

        return false
    }

    // Optionally, override other methods if needed
}

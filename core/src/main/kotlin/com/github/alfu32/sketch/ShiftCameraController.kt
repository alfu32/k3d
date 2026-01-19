package com.github.alfu32.sketch

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3

class ShiftCameraController(camera: PerspectiveCamera) : CameraInputController(camera) {
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
        return super.touchDown(screenX, screenY, pointer, button)
    }
}

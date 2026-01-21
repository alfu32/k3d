package com.github.alfu32.sketch.input

import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController

class CameraScrollForwarder(
    private val controller: CameraInputController
) : InputAdapter() {
    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        controller.scrolled(amountX, amountY)
        return false
    }
}

class CameraEventRouter(
    private val controller: CameraInputController
) : InputProcessor {
    override fun keyDown(keycode: Int): Boolean = controller.keyDown(keycode)
    override fun keyUp(keycode: Int): Boolean = controller.keyUp(keycode)
    override fun keyTyped(character: Char): Boolean = controller.keyTyped(character)
    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
        controller.touchDown(screenX, screenY, pointer, button)
    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
        controller.touchUp(screenX, screenY, pointer, button)
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean =
        controller.touchDragged(screenX, screenY, pointer)
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean =
        controller.mouseMoved(screenX, screenY)
    override fun scrolled(amountX: Float, amountY: Float): Boolean = false
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
        controller.touchCancelled(screenX, screenY, pointer, button)
}

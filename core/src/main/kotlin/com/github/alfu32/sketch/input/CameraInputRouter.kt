package com.github.alfu32.sketch.input

import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor

class CameraScrollForwarder(
    private val processorProvider: () -> InputProcessor,
    private val shouldForward: () -> Boolean = { true }
) : InputAdapter() {
    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        if (!shouldForward()) {
            return false
        }
        processorProvider().scrolled(amountX, amountY)
        // When camera consumes wheel input we must stop propagation so UI scroll panes do not scroll too.
        return true
    }
}

class CameraEventRouter(
    private val processorProvider: () -> InputProcessor
) : InputProcessor {
    override fun keyDown(keycode: Int): Boolean = processorProvider().keyDown(keycode)
    override fun keyUp(keycode: Int): Boolean = processorProvider().keyUp(keycode)
    override fun keyTyped(character: Char): Boolean = processorProvider().keyTyped(character)
    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
        processorProvider().touchDown(screenX, screenY, pointer, button)
    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
        processorProvider().touchUp(screenX, screenY, pointer, button)
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean =
        processorProvider().touchDragged(screenX, screenY, pointer)
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean =
        processorProvider().mouseMoved(screenX, screenY)
    override fun scrolled(amountX: Float, amountY: Float): Boolean = false
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
        processorProvider().touchCancelled(screenX, screenY, pointer, button)
}

package com.github.alfu32.sketch.input

import com.badlogic.gdx.InputAdapter
import com.github.alfu32.sketch.ui.ToolController

class ToolPointerProcessor(
    private val controller: ToolController,
    private val snapper: Snapper,
    private val overrideSnapProvider: () -> SnapResult?
) : InputAdapter() {

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        val snap = overrideSnapProvider() ?: snapper.compute(screenX, screenY)
        controller.pointerMoved(snap.world, snap.normal, snap.valid)
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val snap = overrideSnapProvider() ?: snapper.compute(screenX, screenY)
        controller.pointerMoved(snap.world, snap.normal, snap.valid)
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val snap = overrideSnapProvider() ?: snapper.compute(screenX, screenY)
        return controller.pointerDown(snap.world, snap.normal, snap.valid, button)
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val snap = overrideSnapProvider() ?: snapper.compute(screenX, screenY)
        return controller.pointerUp(snap.world, snap.normal, snap.valid, button)
    }
}

package com.github.alfu32.sketch.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.github.alfu32.sketch.input.GuideManager
import com.github.alfu32.sketch.input.SnapResult
import com.github.alfu32.sketch.ui.ToolId

class ToolInputProcessor(
    private val controller: ToolController,
    private val guideManager: GuideManager,
    private val lastSnapProvider: () -> SnapResult?
) : InputAdapter() {
    override fun keyDown(keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.ESCAPE -> {
                if (controller.activeToolId() == ToolId.SELECT) {
                    guideManager.clear()
                }
                controller.cancelActiveTool()
                return true
            }
            Input.Keys.BACKSPACE -> {
                controller.backspaceInput()
                return true
            }
            Input.Keys.ENTER -> {
                controller.commitInput()
                return true
            }
            Input.Keys.G -> {
                val snap = lastSnapProvider()
                val point = snap?.world
                if (snap != null && snap.valid && point != null) {
                    val alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) ||
                        Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)
                    if (alt) {
                        guideManager.toggleAxisGuide(point)
                    } else {
                        guideManager.toggleGridGuide(point)
                    }
                }
                return true
            }
        }
        return false
    }

    override fun keyTyped(character: Char): Boolean {
        if (character.isDigit() || character == '.' || character == '-') {
            controller.appendInput(character)
            return true
        }
        return false
    }
}

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
    private val cleanupAction: () -> Unit,
    private val clearSelectionAction: () -> Unit,
    private val deleteSelectionAction: () -> Unit,
    private val undoAction: () -> Unit,
    private val redoAction: () -> Unit,
    private val groupSelectionAction: () -> Unit,
    private val objectPrototypeSelectionAction: () -> Unit,
    private val ungroupSelectionAction: () -> Unit,
    private val exitGroupEditAction: () -> Boolean,
    private val lastSnapProvider: () -> SnapResult?,
    private val showDistanceInput: () -> Unit,
    private val uiCapturesInput: () -> Boolean
) : InputAdapter() {
    private fun ctrlPressed(): Boolean {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT) ||
            Gdx.input.isKeyPressed(Input.Keys.SYM)
    }

    override fun keyDown(keycode: Int): Boolean {
        if (uiCapturesInput()) {
            return true
        }
        if (controller.handleKeyDown(keycode)) {
            return true
        }
        when (keycode) {
            Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT -> {
                if (controller.toggleCopyMode()) {
                    return true
                }
            }
            Input.Keys.Z -> {
                val ctrl = ctrlPressed()
                val shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
                    Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                if (ctrl && shift) {
                    redoAction()
                    return true
                }
                if (ctrl) {
                    undoAction()
                    return true
                }
            }
            Input.Keys.Y -> {
                val ctrl = ctrlPressed()
                if (ctrl) {
                    redoAction()
                    return true
                }
            }
            Input.Keys.ESCAPE, Input.Keys.BACK -> {
                if (controller.activeToolId() == ToolId.SELECT) {
                    guideManager.clear()
                }
                exitGroupEditAction()
                clearSelectionAction()
                controller.cancelActiveTool()
                return true
            }
            Input.Keys.DEL, Input.Keys.FORWARD_DEL -> {
                deleteSelectionAction()
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
            Input.Keys.T -> {
                val snap = lastSnapProvider()
                val point = snap?.world
                if (snap != null && snap.valid && point != null) {
                    guideManager.addAxisGuide(point, snap.normal)
                }
                return true
            }
            Input.Keys.G -> {
                val ctrl = ctrlPressed()
                val shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
                    Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                if (ctrl && shift) {
                    ungroupSelectionAction()
                    return true
                }
                if (ctrl) {
                    groupSelectionAction()
                    return true
                }
                val snap = lastSnapProvider()
                val point = snap?.world
                if (snap != null && snap.valid && point != null) {
                    guideManager.addGridGuide(point, snap.normal)
                }
                return true
            }
            Input.Keys.L -> {
                val ctrl = ctrlPressed()
                if (ctrl) {
                    cleanupAction()
                    return true
                }
            }
            Input.Keys.O -> {
                val ctrl = ctrlPressed()
                if (ctrl) {
                    objectPrototypeSelectionAction()
                    return true
                }
            }
            Input.Keys.N -> {
                val ctrl = ctrlPressed()
                if (ctrl) {
                    showDistanceInput()
                    return true
                }
            }
        }
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        if (uiCapturesInput()) {
            return true
        }
        return controller.handleKeyUp(keycode)
    }

    override fun keyTyped(character: Char): Boolean {
        if (uiCapturesInput()) {
            return true
        }
        if (character.isDigit() || character == '.' || character == '-') {
            controller.appendInput(character)
            return true
        }
        return false
    }
}

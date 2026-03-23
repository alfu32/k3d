package com.github.alfu32.sketch.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.github.alfu32.sketch.InputModifiers
import com.github.alfu32.sketch.input.GuideManager
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
    private val axisGuideAction: () -> Unit,
    private val gridGuideAction: () -> Unit,
    private val exitGroupEditAction: () -> Boolean,
    private val startHotspotAddMode: () -> Unit,
    private val cancelHotspotAddMode: () -> Unit,
    private val isHotspotAddModeActive: () -> Boolean,
    private val showDistanceInput: () -> Unit,
    private val uiCapturesInput: () -> Boolean
) : InputAdapter() {
    private val cycleD = listOf(
        ToolId.LINEAR_DIMENSION,
        ToolId.TEXT,
        ToolId.VECTOR_TEXT
    )
    private val cycleO = listOf(
        ToolId.LINE_OFFSET,
        ToolId.PUSH_PULL,
        ToolId.EXTRUDE_SWIPE,
        ToolId.PLANE_SECTION
    )
    private val cycleM = listOf(
        ToolId.MOVE,
        ToolId.ROTATE,
        ToolId.SCALE,
        ToolId.STRETCH,
        ToolId.ROTATE_STRETCH,
        ToolId.COPY_MULTIPLE,
        ToolId.PLANAR_TRANSLATE_MULTIPLE,
        ToolId.VOLUMETRIC_TRANSLATE_MULTIPLE,
        ToolId.PLANAR_ROTATE_MULTIPLE,
        ToolId.HELICOIDAL_ROTATE_MULTIPLE
    )

    private fun ctrlPressed(): Boolean {
        return InputModifiers.isCtrlPressed()
    }

    private fun activateNextInCycle(cycle: List<ToolId>): Boolean {
        if (cycle.isEmpty()) {
            return false
        }
        val active = controller.activeToolId()
        val next = when {
            active == ToolId.SELECT -> cycle.first()
            else -> {
                val idx = cycle.indexOf(active)
                if (idx < 0) cycle.first() else cycle[(idx + 1) % cycle.size]
            }
        }
        controller.setTool(next)
        return true
    }

    private fun beginHotspotCycleStep(): Boolean {
        startHotspotAddMode()
        return true
    }

    private fun cancelHotspotCycleStepIfActive() {
        if (isHotspotAddModeActive()) {
            cancelHotspotAddMode()
        }
    }

    private fun handleLCycleKey(): Boolean {
        if (isHotspotAddModeActive()) {
            cancelHotspotAddMode()
            controller.setTool(ToolId.CONSTRUCTION_LINE)
            return true
        }
        return when (controller.activeToolId()) {
            ToolId.SELECT -> beginHotspotCycleStep()
            ToolId.CONSTRUCTION_LINE -> { controller.setTool(ToolId.LINE); true }
            ToolId.LINE -> { controller.setTool(ToolId.POLYLINE); true }
            ToolId.POLYLINE -> { controller.setTool(ToolId.DOUBLE_LINE); true }
            ToolId.DOUBLE_LINE -> { controller.setTool(ToolId.RECTANGLE); true }
            ToolId.RECTANGLE -> { controller.setTool(ToolId.SURFACE_RECTANGLE); true }
            ToolId.SURFACE_RECTANGLE -> { controller.setTool(ToolId.MESH); true }
            ToolId.MESH -> { controller.setTool(ToolId.QUAD); true }
            ToolId.QUAD -> { controller.setTool(ToolId.CIRCLE); true }
            ToolId.CIRCLE -> beginHotspotCycleStep()
            else -> beginHotspotCycleStep()
        }
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
                axisGuideAction()
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
                gridGuideAction()
                return true
            }
            Input.Keys.L -> {
                val ctrl = ctrlPressed()
                if (ctrl) {
                    cleanupAction()
                    return true
                }
                return handleLCycleKey()
            }
            Input.Keys.D -> {
                val ctrl = ctrlPressed()
                if (ctrl) {
                    return false
                }
                cancelHotspotCycleStepIfActive()
                return activateNextInCycle(cycleD)
            }
            Input.Keys.O -> {
                val ctrl = ctrlPressed()
                if (ctrl) {
                    objectPrototypeSelectionAction()
                    return true
                }
                cancelHotspotCycleStepIfActive()
                return activateNextInCycle(cycleO)
            }
            Input.Keys.M -> {
                val ctrl = ctrlPressed()
                if (ctrl) {
                    return false
                }
                cancelHotspotCycleStepIfActive()
                return activateNextInCycle(cycleM)
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

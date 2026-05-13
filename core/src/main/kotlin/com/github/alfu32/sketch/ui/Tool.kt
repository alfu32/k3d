package com.github.alfu32.sketch.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.tools.ToolFeedbackColors

data class ToolMeasurement(
    val startWorld: Vector3,
    val endWorld: Vector3,
    val lineColor: com.badlogic.gdx.graphics.Color = ToolFeedbackColors.PRIMARY
)

sealed class ToolOperatorAction {
    data class KeyDown(val keycode: Int) : ToolOperatorAction()
    object ToggleCopyMode : ToolOperatorAction()
    object ShowDistanceInput : ToolOperatorAction()
    object ExitObjectEdit : ToolOperatorAction()
}

data class ToolOperator(
    val id: String,
    val label: String,
    val action: ToolOperatorAction
) {
    companion object {
        fun key(id: String, label: String, keycode: Int): ToolOperator {
            return ToolOperator(id, label, ToolOperatorAction.KeyDown(keycode))
        }
    }
}

object ToolOperatorPresets {
    private fun lineSegment(): ToolOperator = ToolOperator.key("segment-line", "Line Seg", Input.Keys.L)
    private fun arcSegment(): ToolOperator = ToolOperator.key("segment-arc", "Arc Seg", Input.Keys.A)
    private fun circleSegment(): ToolOperator = ToolOperator.key("segment-circle", "Circle Seg", Input.Keys.C)
    fun finish(label: String = "Finish"): ToolOperator = ToolOperator.key("finish", label, Input.Keys.ENTER)
    fun undoPoint(label: String = "Undo Pt"): ToolOperator = ToolOperator.key("undo-point", label, Input.Keys.BACKSPACE)
    fun cancel(label: String = "Esc"): ToolOperator = ToolOperator.key("cancel", label, Input.Keys.ESCAPE)
    fun closeObject(): ToolOperator = ToolOperator("close-object", "Close Object", ToolOperatorAction.ExitObjectEdit)

    private fun arcPathOperators(): List<ToolOperator> {
        return listOf(
            lineSegment(),
            arcSegment(),
            circleSegment(),
            undoPoint(),
            finish(),
            cancel()
        )
    }

    fun forTool(toolId: ToolId): List<ToolOperator> {
        return when (toolId) {
            ToolId.LINE,
            ToolId.CONSTRUCTION_LINE,
            ToolId.ARCH_WALL,
            ToolId.HVAC_VENTILATION -> listOf(finish(), cancel())

            ToolId.POLYLINE,
            ToolId.DOUBLE_LINE,
            ToolId.EXTRUDE_SWIPE,
            ToolId.ARCH_WINDOW_FRAME -> arcPathOperators()

            ToolId.MESH,
            ToolId.VOXEL_VOLUME,
            ToolId.VOXEL_FRAME,
            ToolId.ARCH_SLAB,
            ToolId.ARCH_STAIR,
            ToolId.ARCH_ADD_HOLE,
            ToolId.ARCH_DOOR_FRAME,
            ToolId.PLANE_SECTION,
            ToolId.CUT_WITH_PLANE -> listOf(finish(), cancel())

            ToolId.HVAC_PLUMBING -> listOf(finish(), undoPoint(), cancel())

            ToolId.VOXEL,
            ToolId.AXIAL_GRID,
            ToolId.PLANAR_GRID -> listOf(cancel())

            else -> emptyList()
        }
    }
}

interface Tool {
    val id: ToolId
    val message: String

    fun onEnter(status: StatusModel) {
        status.message = message
    }

    fun onExit(status: StatusModel) {
        if (status.message == message) {
            status.message = ""
        }
    }

    fun onCancel(status: StatusModel) {
        status.message = "Canceled."
    }

    fun supportsCopyMode(): Boolean = false

    fun onCopyModeChanged(status: StatusModel, enabled: Boolean) {
        // Default no-op.
    }

    fun toolOperators(status: StatusModel): List<ToolOperator> = ToolOperatorPresets.forTool(id)

    fun onTextInput(status: StatusModel, text: String) {
        status.inputBuffer = text
    }

    fun anchorWorld(): Vector3? = null

    fun measurement(status: StatusModel): ToolMeasurement? = null

    fun feedbackLines(): List<Pair<Vector3, Vector3>> = emptyList()

    fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        // Default no-op.
    }

    fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        return false
    }

    fun onPointerUp(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        return false
    }

    fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return false
    }

    fun onKeyUp(status: StatusModel, keycode: Int): Boolean {
        return false
    }

    fun render(renderer: ShapeRenderer) {
        // Default no-op.
    }
}

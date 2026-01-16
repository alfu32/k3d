package com.github.alfu32.sketch.ui

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3

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

    fun onTextInput(status: StatusModel, text: String) {
        status.inputBuffer = text
    }

    fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        // Default no-op.
    }

    fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        return false
    }

    fun onPointerUp(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        return false
    }

    fun render(renderer: ShapeRenderer) {
        // Default no-op.
    }
}

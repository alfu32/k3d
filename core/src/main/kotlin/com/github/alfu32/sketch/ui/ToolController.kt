package com.github.alfu32.sketch.ui

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3

class ToolController(
    private val status: StatusModel,
    tools: List<Tool>
) {
    private val toolMap = tools.associateBy { it.id }
    private var activeTool: Tool = toolMap[ToolId.SELECT]
        ?: error("Select tool is required")

    init {
        status.activeTool = activeTool.id
        activeTool.onEnter(status)
    }

    fun setTool(id: ToolId) {
        val next = toolMap[id] ?: return
        if (next == activeTool) return
        activeTool.onExit(status)
        activeTool = next
        status.activeTool = activeTool.id
        status.clearInput()
        activeTool.onEnter(status)
    }

    fun resetToDefault() {
        setTool(ToolId.SELECT)
    }

    fun activeToolId(): ToolId {
        return activeTool.id
    }

    fun activeTool(): Tool {
        return activeTool
    }

    fun cancelActiveTool() {
        activeTool.onCancel(status)
        resetToDefault()
    }

    fun appendInput(char: Char) {
        status.inputBuffer += char
        activeTool.onTextInput(status, status.inputBuffer)
    }

    fun backspaceInput() {
        if (status.inputBuffer.isNotEmpty()) {
            status.inputBuffer = status.inputBuffer.dropLast(1)
            activeTool.onTextInput(status, status.inputBuffer)
        }
    }

    fun commitInput() {
        if (status.inputBuffer.isNotBlank()) {
            status.message = "Input: ${status.inputBuffer}"
        }
        status.clearInput()
    }

    fun pointerMoved(world: Vector3?, normal: Vector3?, valid: Boolean) {
        activeTool.onPointerMoved(status, world, normal, valid)
    }

    fun pointerDown(world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        return activeTool.onPointerDown(status, world, normal, valid, button)
    }

    fun pointerUp(world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        return activeTool.onPointerUp(status, world, normal, valid, button)
    }

    fun render(renderer: ShapeRenderer) {
        activeTool.render(renderer)
    }
}

package com.github.alfu32.sketch.ui

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3

class ToolController(
    private val status: StatusModel,
    tools: List<Tool>
) {
    private val toolMap = tools.associateBy { it.id }.toMutableMap()
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
        status.copyMode = false
        status.anchorWorld = null
        status.clearInput()
        activeTool.onEnter(status)
        if (activeTool.supportsCopyMode()) {
            activeTool.onCopyModeChanged(status, status.copyMode)
        }
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

    fun registerTool(tool: Tool) {
        toolMap[tool.id] = tool
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
        val handled = activeTool.onPointerDown(status, world, normal, valid, button)
        if (handled && valid && world != null) {
            status.anchorWorld = Vector3(world)
        }
        return handled
    }

    fun pointerUp(world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        return activeTool.onPointerUp(status, world, normal, valid, button)
    }

    fun render(renderer: ShapeRenderer) {
        activeTool.render(renderer)
    }

    fun update(delta: Float) {
        val pluginTool = (activeTool as? PluginToolAdapter) ?: return
        pluginTool.update(delta, status)
    }

    fun handleKeyDown(keycode: Int): Boolean {
        if (activeTool.onKeyDown(status, keycode)) {
            return true
        }
        val pluginTool = (activeTool as? PluginToolAdapter) ?: return false
        return pluginTool.handleKeyDown(keycode, status)
    }

    fun handleKeyUp(keycode: Int): Boolean {
        if (activeTool.onKeyUp(status, keycode)) {
            return true
        }
        val pluginTool = (activeTool as? PluginToolAdapter) ?: return false
        return pluginTool.handleKeyUp(keycode, status)
    }

    fun toggleCopyMode(): Boolean {
        if (!activeTool.supportsCopyMode()) {
            return false
        }
        status.copyMode = !status.copyMode
        activeTool.onCopyModeChanged(status, status.copyMode)
        return true
    }
}

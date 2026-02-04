package com.github.alfu32.sketch.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.plugin.PluginHost

class PluginToolAdapter(
    private val pluginHost: PluginHost
) : Tool {
    override val id: ToolId = ToolId.PLUGIN
    override val message: String = "Plugin tool active."

    override fun onEnter(status: StatusModel) {
        super.onEnter(status)
        pluginHost.activePluginTool()?.onActivate(pluginHost.pluginContext())
    }

    override fun onExit(status: StatusModel) {
        pluginHost.activePluginTool()?.onDeactivate(pluginHost.pluginContext())
        super.onExit(status)
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        val tool = pluginHost.activePluginTool() ?: return
        tool.onMouseMove(pluginHost.pluginContext(), Gdx.input.x, Gdx.input.y)
    }

    override fun onPointerDown(
        status: StatusModel,
        world: Vector3?,
        normal: Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        val tool = pluginHost.activePluginTool() ?: return false
        if (button != com.badlogic.gdx.Input.Buttons.LEFT) {
            return false
        }
        tool.onMouseDown(pluginHost.pluginContext(), button, Gdx.input.x, Gdx.input.y)
        return true
    }

    override fun onPointerUp(
        status: StatusModel,
        world: Vector3?,
        normal: Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        val tool = pluginHost.activePluginTool() ?: return false
        if (button != com.badlogic.gdx.Input.Buttons.LEFT) {
            return false
        }
        tool.onMouseUp(pluginHost.pluginContext(), button, Gdx.input.x, Gdx.input.y)
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        pluginHost.activePluginTool()?.onDraw3D(pluginHost.pluginContext(), renderer)
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return handleKeyDown(keycode, status)
    }

    override fun onKeyUp(status: StatusModel, keycode: Int): Boolean {
        return handleKeyUp(keycode, status)
    }

    fun update(delta: Float, status: StatusModel) {
        pluginHost.activePluginTool()?.onUpdate(pluginHost.pluginContext(), delta)
    }

    fun handleKeyDown(keycode: Int, status: StatusModel): Boolean {
        val tool = pluginHost.activePluginTool() ?: return false
        return tool.onKeyDown(pluginHost.pluginContext(), keycode)
    }

    fun handleKeyUp(keycode: Int, status: StatusModel): Boolean {
        val tool = pluginHost.activePluginTool() ?: return false
        return tool.onKeyUp(pluginHost.pluginContext(), keycode)
    }
}

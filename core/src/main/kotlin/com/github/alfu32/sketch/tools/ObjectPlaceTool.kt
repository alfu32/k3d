package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class ObjectPlaceTool(
    private val scene: GroupScene,
    private val onPlaced: (GroupScene.GroupNode) -> Unit,
    private val onCanceled: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.OBJECT_PLACE
    override val message: String = "Click to place object. Esc cancels."
    private var prototype: GroupScene.ObjectPrototype? = null
    private var hoverWorld: Vector3? = null
    private var hoverValid: Boolean = false

    fun setPrototype(target: GroupScene.ObjectPrototype) {
        prototype = target
        hoverWorld = null
        hoverValid = false
    }

    override fun onEnter(status: StatusModel) {
        status.message = message
    }

    override fun onCancel(status: StatusModel) {
        prototype = null
        hoverWorld = null
        hoverValid = false
        status.message = "Canceled."
        onCanceled()
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        hoverWorld = world?.let { Vector3(it) }
        hoverValid = valid
    }

    override fun onPointerDown(
        status: StatusModel,
        world: Vector3?,
        normal: Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        val targetPrototype = prototype ?: return false
        if (!valid || world == null) {
            return false
        }
        val instance = scene.createInstanceAtWorld(targetPrototype, scene.activeGroup(), world)
        status.message = "Object placed."
        prototype = null
        hoverWorld = null
        hoverValid = false
        onPlaced(instance)
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val targetPrototype = prototype ?: return
        val origin = hoverWorld ?: return
        if (!hoverValid) {
            return
        }
        val previous = Color(renderer.color)
        renderer.color = Color(0f, 0f, 0f, 1f)
        targetPrototype.lineStore.getSegments().forEach { segment ->
            renderer.line(
                Vector3(segment.start).add(origin),
                Vector3(segment.end).add(origin)
            )
        }
        renderer.color = previous
    }
}

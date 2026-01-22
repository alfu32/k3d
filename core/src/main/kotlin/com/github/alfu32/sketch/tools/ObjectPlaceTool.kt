package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
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

    fun setPrototype(target: GroupScene.ObjectPrototype) {
        prototype = target
    }

    override fun onEnter(status: StatusModel) {
        status.message = message
    }

    override fun onCancel(status: StatusModel) {
        prototype = null
        status.message = "Canceled."
        onCanceled()
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
        onPlaced(instance)
        return true
    }
}

package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class TextTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.TEXT
    override val message: String = "Click to place text."

    override fun onEnter(status: StatusModel) {
        status.message = message
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        val group = scene.activeGroup()
        group.textStore.addText(group.toLocal(world), "text", 1f)
        status.message = "Text placed."
        return true
    }
}

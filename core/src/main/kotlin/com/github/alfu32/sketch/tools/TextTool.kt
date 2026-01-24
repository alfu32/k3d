package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.graphics.Camera
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class TextTool(
    private val scene: GroupScene,
    private val camera: Camera
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
        val normalWorld = normal?.cpy()?.nor() ?: Vector3(0f, 1f, 0f)
        val axisUWorld = planeAxisU(normalWorld)
        group.textStore.addText(
            group.toLocal(world),
            "text",
            1f,
            group.vectorToLocal(normalWorld),
            group.vectorToLocal(axisUWorld)
        )
        status.message = "Text placed."
        return true
    }

    private fun planeAxisU(normal: Vector3): Vector3 {
        val primary = Vector3(1f, 0f, 0f)
        val fallback = Vector3(0f, 0f, 1f)
        val axis = if (kotlin.math.abs(normal.dot(primary)) < 0.95f) {
            Vector3(primary)
        } else {
            Vector3(fallback)
        }
        axis.mulAdd(normal, -axis.dot(normal))
        if (axis.len2() < 1e-6f) {
            axis.set(camera.direction).crs(normal)
        }
        return axis.nor()
    }
}

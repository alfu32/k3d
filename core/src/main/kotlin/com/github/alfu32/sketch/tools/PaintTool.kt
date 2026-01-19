package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class PaintTool(
    private val scene: GroupScene,
    private val camera: Camera,
    private val colorProvider: () -> Color
) : Tool {
    override val id: ToolId = ToolId.PAINT
    override val message: String = "Paint faces."

    override fun onEnter(status: StatusModel) {
        val applied = scene.activeGroup().faceStore.paintSelected(colorProvider())
        status.message = if (applied > 0) {
            "Paint applied to selection."
        } else {
            "Paint: click a face."
        }
    }

    override fun onPointerDown(
        status: StatusModel,
        world: com.badlogic.gdx.math.Vector3?,
        normal: com.badlogic.gdx.math.Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        val group = scene.activeGroup()
        val ray = camera.getPickRay(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        val localRay = com.badlogic.gdx.math.collision.Ray(
            group.toLocal(ray.origin),
            group.vectorToLocal(ray.direction).nor()
        )
        val hit = group.faceStore.pickTriangle(localRay) ?: return false
        group.faceStore.paintTriangle(hit.triangle, colorProvider())
        status.message = "Painted face."
        return true
    }
}

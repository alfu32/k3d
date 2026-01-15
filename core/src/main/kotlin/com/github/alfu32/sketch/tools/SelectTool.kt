package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class SelectTool(
    private val lineStore: DraftLineStore,
    private val faceStore: DraftFaceStore,
    private val camera: Camera
) : Tool {
    override val id: ToolId = ToolId.SELECT
    override val message: String = "Select entities."

    override fun onEnter(status: StatusModel) {
        status.message = "Select entities."
    }

    override fun onCancel(status: StatusModel) {
        lineStore.clearSelection()
        faceStore.clearSelection()
        status.message = "Selection cleared."
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
        val ray = camera.getPickRay(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        val faceHit = faceStore.pickTriangle(ray)
        val edgeHit = lineStore.pickSegment(ray, camera, Gdx.input.x, Gdx.input.y)
        val pickedFace = faceHit != null
        val pickedEdge = edgeHit != null
        if (!pickedFace && !pickedEdge) {
            return false
        }
        if (pickedFace && pickedEdge) {
            if (faceHit!!.t <= edgeHit!!.t) {
                faceStore.toggleSelection(faceHit.triangle)
                status.message = "Face toggled."
            } else {
                lineStore.toggleSelection(edgeHit.segment)
                status.message = "Edge toggled."
            }
            return true
        }
        if (pickedFace) {
            faceStore.toggleSelection(faceHit!!.triangle)
            status.message = "Face toggled."
            return true
        }
        lineStore.toggleSelection(edgeHit!!.segment)
        status.message = "Edge toggled."
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        // Selection visuals are handled elsewhere.
    }
}

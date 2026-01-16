package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class MoveTool(
    private val lineStore: DraftLineStore,
    private val faceStore: DraftFaceStore
) : Tool {
    override val id: ToolId = ToolId.MOVE
    override val message: String = "Pick reference point."

    private var start: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Pick reference point."
    }

    override fun onExit(status: StatusModel) {
        clearTransient()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clearTransient()
        status.message = "Canceled."
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hover.set(world)
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        if (start == null) {
            start = Vector3(world)
            status.message = "Pick destination point."
            return true
        }
        val from = start ?: return false
        val to = Vector3(world)
        val delta = Vector3(to).sub(from)
        if (delta.len2() <= 1e-6f) {
            clearTransient()
            status.message = "No movement."
            return true
        }
        val movedFaces = faceStore.transformSelected { point -> Vector3(point).add(delta) }
        val movedEdges = lineStore.transformSelected { point -> Vector3(point).add(delta) }
        status.message = "Moved | edges $movedEdges faces $movedFaces"
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val startPoint = start
        if (startPoint != null && hasHover) {
            renderer.color = Color(0.95f, 0.3f, 0.3f, 1f)
            drawCross(renderer, startPoint, 0.18f)
            renderer.color = Color(0.2f, 0.55f, 0.95f, 1f)
            drawCross(renderer, hover, 0.18f)
            renderer.color = Color(0.95f, 0.9f, 0.2f, 1f)
            renderer.line(startPoint.x, startPoint.y, startPoint.z, hover.x, hover.y, hover.z)
            val delta = Vector3(hover).sub(startPoint)
            if (delta.len2() > 1e-6f) {
                renderPreview(renderer) { point -> Vector3(point).add(delta) }
            }
        }
    }

    private fun drawCross(renderer: ShapeRenderer, point: Vector3, size: Float) {
        renderer.line(point.x - size, point.y, point.z, point.x + size, point.y, point.z)
        renderer.line(point.x, point.y - size, point.z, point.x, point.y + size, point.z)
        renderer.line(point.x, point.y, point.z - size, point.x, point.y, point.z + size)
    }

    private fun clearTransient() {
        start = null
        hasHover = false
    }

    private fun renderPreview(renderer: ShapeRenderer, transform: (Vector3) -> Vector3) {
        renderer.color = Color(0.25f, 0.85f, 0.55f, 1f)
        faceStore.getSelected().forEach { tri ->
            val a = transform(tri.a)
            val b = transform(tri.b)
            val c = transform(tri.c)
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            renderer.line(b.x, b.y, b.z, c.x, c.y, c.z)
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
        }
        lineStore.getSelected().forEach { segment ->
            val a = transform(segment.start)
            val b = transform(segment.end)
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
    }
}

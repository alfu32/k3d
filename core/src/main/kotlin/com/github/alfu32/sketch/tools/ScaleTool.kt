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

class ScaleTool(
    private val lineStore: DraftLineStore,
    private val faceStore: DraftFaceStore
) : Tool {
    override val id: ToolId = ToolId.SCALE
    override val message: String = "Pick scale center."

    private var center: Vector3? = null
    private var reference: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Pick scale center."
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
        if (center == null) {
            center = Vector3(world)
            status.message = "Pick reference point."
            return true
        }
        if (reference == null) {
            reference = Vector3(world)
            status.message = "Pick final point."
            return true
        }
        val c = center ?: return false
        val a = reference ?: return false
        val b = Vector3(world)
        val refVec = Vector3(a).sub(c)
        val nextVec = Vector3(b).sub(c)
        val refLen = refVec.len()
        val nextLen = nextVec.len()
        if (refLen <= 1e-6f || nextLen <= 1e-6f) {
            clearTransient()
            status.message = "Scale vectors are too short."
            return true
        }
        val axis = refVec.nor()
        val scale = nextLen / refLen
        if (kotlin.math.abs(scale - 1f) <= 1e-4f) {
            clearTransient()
            status.message = "No scale."
            return true
        }
        val scaledFaces = faceStore.transformSelected { point ->
            scaleAlongAxis(point, c, axis, scale)
        }
        val scaledEdges = lineStore.transformSelected { point ->
            scaleAlongAxis(point, c, axis, scale)
        }
        status.message = "Scaled | edges $scaledEdges faces $scaledFaces"
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        if (!hasHover) {
            return
        }
        val c = center ?: return
        val ref = reference
        if (ref != null) {
            renderer.color = Color(0.95f, 0.3f, 0.3f, 1f)
            renderer.line(c.x, c.y, c.z, ref.x, ref.y, ref.z)
        }
        renderer.color = Color(0.25f, 0.85f, 0.35f, 1f)
        renderer.line(c.x, c.y, c.z, hover.x, hover.y, hover.z)
        if (ref != null) {
            val refVec = Vector3(ref).sub(c)
            val nextVec = Vector3(hover).sub(c)
            val refLen = refVec.len()
            val nextLen = nextVec.len()
            if (refLen > 1e-6f && nextLen > 1e-6f) {
                val axis = refVec.nor()
                val scale = nextLen / refLen
                renderPreview(renderer, c, axis, scale)
            }
        }
    }

    private fun clearTransient() {
        center = null
        reference = null
        hasHover = false
    }

    private fun renderPreview(renderer: ShapeRenderer, center: Vector3, axis: Vector3, scale: Float) {
        renderer.color = Color(0.25f, 0.85f, 0.55f, 1f)
        faceStore.getSelected().forEach { tri ->
            val a = scaleAlongAxis(tri.a, center, axis, scale)
            val b = scaleAlongAxis(tri.b, center, axis, scale)
            val c = scaleAlongAxis(tri.c, center, axis, scale)
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            renderer.line(b.x, b.y, b.z, c.x, c.y, c.z)
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
        }
        lineStore.getSelected().forEach { segment ->
            val a = scaleAlongAxis(segment.start, center, axis, scale)
            val b = scaleAlongAxis(segment.end, center, axis, scale)
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
    }

    private fun scaleAlongAxis(point: Vector3, center: Vector3, axis: Vector3, scale: Float): Vector3 {
        val v = Vector3(point).sub(center)
        val parallel = Vector3(axis).scl(v.dot(axis))
        val perpendicular = Vector3(v).sub(parallel)
        return Vector3(center).add(perpendicular).add(parallel.scl(scale))
    }
}

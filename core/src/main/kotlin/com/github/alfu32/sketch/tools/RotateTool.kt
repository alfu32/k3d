package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class RotateTool(
    private val lineStore: DraftLineStore,
    private val faceStore: DraftFaceStore
) : Tool {
    override val id: ToolId = ToolId.ROTATE
    override val message: String = "Pick rotation center."

    private var center: Vector3? = null
    private var axisDir: Vector3? = null
    private var centerNormal: Vector3? = null
    private var reference: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Pick rotation center."
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
            centerNormal = normal?.cpy()
            status.message = "Pick axis direction."
            return true
        }
        if (axisDir == null) {
            val c = center ?: return false
            val axis = Vector3(world).sub(c)
            axisDir = if (axis.len2() <= 1e-4f) {
                (centerNormal ?: Vector3(0f, 1f, 0f)).cpy().nor()
            } else {
                axis.nor()
            }
            status.message = "Pick reference point."
            return true
        }
        if (reference == null) {
            reference = Vector3(world)
            status.message = "Pick final point."
            return true
        }
        val c = center ?: return false
        val axis = axisDir ?: return false
        val from = reference ?: return false
        val to = Vector3(world)
        val v1 = Vector3(from).sub(c)
        val v2 = Vector3(to).sub(c)
        if (v1.len2() <= 1e-6f || v2.len2() <= 1e-6f) {
            clearTransient()
            status.message = "Rotation vectors are too short."
            return true
        }
        val cross = Vector3(v1).crs(v2)
        val angle = MathUtils.atan2(axis.dot(cross), v1.dot(v2))
        val degrees = angle * MathUtils.radiansToDegrees
        val quaternion = Quaternion().setFromAxis(axis, degrees)
        val movedFaces = faceStore.transformSelected { point ->
            Vector3(point).sub(c).mul(quaternion).add(c)
        }
        val movedEdges = lineStore.transformSelected { point ->
            Vector3(point).sub(c).mul(quaternion).add(c)
        }
        status.message = "Rotated | edges $movedEdges faces $movedFaces"
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        if (!hasHover) {
            return
        }
        val c = center ?: return
        val axis = axisDir
        val ref = reference
        val axisLen = when {
            ref != null -> Vector3(ref).sub(c).len()
            hasHover -> Vector3(hover).sub(c).len()
            else -> 1f
        }.coerceAtLeast(0.5f)
        if (axis != null) {
            val axisEnd = Vector3(c).mulAdd(axis, axisLen)
            renderer.color = Color(0.35f, 0.45f, 0.95f, 1f)
            renderer.line(c.x, c.y, c.z, axisEnd.x, axisEnd.y, axisEnd.z)
        }
        if (ref != null) {
            renderer.color = Color(0.95f, 0.3f, 0.3f, 1f)
            renderer.line(c.x, c.y, c.z, ref.x, ref.y, ref.z)
        }
        renderer.color = Color(0.25f, 0.85f, 0.35f, 1f)
        renderer.line(c.x, c.y, c.z, hover.x, hover.y, hover.z)
        if (axis != null && ref != null) {
            val v1 = Vector3(ref).sub(c)
            val v2 = Vector3(hover).sub(c)
            if (v1.len2() > 1e-6f && v2.len2() > 1e-6f) {
                val cross = Vector3(v1).crs(v2)
                val angle = MathUtils.atan2(axis.dot(cross), v1.dot(v2))
                val degrees = angle * MathUtils.radiansToDegrees
                val quaternion = Quaternion().setFromAxis(axis, degrees)
                renderPreview(renderer, c, quaternion)
            }
        }
    }

    private fun clearTransient() {
        center = null
        axisDir = null
        centerNormal = null
        reference = null
        hasHover = false
    }

    private fun renderPreview(renderer: ShapeRenderer, center: Vector3, rotation: Quaternion) {
        renderer.color = Color(0.25f, 0.85f, 0.55f, 1f)
        faceStore.getSelected().forEach { tri ->
            val a = Vector3(tri.a).sub(center).mul(rotation).add(center)
            val b = Vector3(tri.b).sub(center).mul(rotation).add(center)
            val c = Vector3(tri.c).sub(center).mul(rotation).add(center)
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            renderer.line(b.x, b.y, b.z, c.x, c.y, c.z)
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
        }
        lineStore.getSelected().forEach { segment ->
            val a = Vector3(segment.start).sub(center).mul(rotation).add(center)
            val b = Vector3(segment.end).sub(center).mul(rotation).add(center)
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
    }
}

package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class ScaleTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.SCALE
    override val message: String = "Pick scale center."

    private var centerWorld: Vector3? = null
    private var referenceWorld: Vector3? = null
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
        val group = scene.activeGroup()
        if (centerWorld == null) {
            centerWorld = Vector3(world)
            status.message = "Pick reference point."
            return true
        }
        if (referenceWorld == null) {
            referenceWorld = Vector3(world)
            status.message = "Pick final point."
            return true
        }
        val cWorld = centerWorld ?: return false
        val aWorld = referenceWorld ?: return false
        val bWorld = Vector3(world)
        val refVec = Vector3(aWorld).sub(cWorld)
        val nextVec = Vector3(bWorld).sub(cWorld)
        val refLen = refVec.len()
        val nextLen = nextVec.len()
        if (refLen <= 1e-6f || nextLen <= 1e-6f) {
            clearTransient()
            status.message = "Scale vectors are too short."
            return true
        }
        val axisWorld = refVec.nor()
        val scale = nextLen / refLen
        if (kotlin.math.abs(scale - 1f) <= 1e-4f) {
            clearTransient()
            status.message = "No scale."
            return true
        }
        val cLocal = group.toLocal(cWorld)
        val axisLocal = group.vectorToLocal(axisWorld).nor()
        val scaledFaces = group.faceStore.transformSelected { point ->
            scaleAlongAxis(point, cLocal, axisLocal, scale)
        }
        val scaledEdges = group.lineStore.transformSelected { point ->
            scaleAlongAxis(point, cLocal, axisLocal, scale)
        }
        val scaledGroups = scene.transformSelectedGroups(
            { point -> scaleAlongAxis(point, cWorld, axisWorld, scale) },
            { vector -> scaleVectorAlongAxis(vector, axisWorld, scale) }
        )
        status.message = "Scaled | edges $scaledEdges faces $scaledFaces groups $scaledGroups"
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        if (!hasHover) {
            return
        }
        val c = centerWorld ?: return
        val ref = referenceWorld
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
                renderGroupPreview(renderer, c, axis, scale)
            }
        }
    }

    private fun clearTransient() {
        centerWorld = null
        referenceWorld = null
        hasHover = false
    }

    private fun renderPreview(renderer: ShapeRenderer, center: Vector3, axis: Vector3, scale: Float) {
        renderer.color = Color(0.25f, 0.85f, 0.55f, 1f)
        val group = scene.activeGroup()
        val centerLocal = group.toLocal(center)
        val axisLocal = group.vectorToLocal(axis).nor()
        group.faceStore.getSelected().forEach { tri ->
            val a = scaleAlongAxis(tri.a, centerLocal, axisLocal, scale)
            val b = scaleAlongAxis(tri.b, centerLocal, axisLocal, scale)
            val c = scaleAlongAxis(tri.c, centerLocal, axisLocal, scale)
            val aw = group.toWorld(a)
            val bw = group.toWorld(b)
            val cw = group.toWorld(c)
            renderer.line(aw.x, aw.y, aw.z, bw.x, bw.y, bw.z)
            renderer.line(bw.x, bw.y, bw.z, cw.x, cw.y, cw.z)
            renderer.line(cw.x, cw.y, cw.z, aw.x, aw.y, aw.z)
        }
        group.lineStore.getSelected().forEach { segment ->
            val a = scaleAlongAxis(segment.start, centerLocal, axisLocal, scale)
            val b = scaleAlongAxis(segment.end, centerLocal, axisLocal, scale)
            val aw = group.toWorld(a)
            val bw = group.toWorld(b)
            renderer.line(aw.x, aw.y, aw.z, bw.x, bw.y, bw.z)
        }
    }

    private fun scaleAlongAxis(point: Vector3, center: Vector3, axis: Vector3, scale: Float): Vector3 {
        val v = Vector3(point).sub(center)
        val parallel = Vector3(axis).scl(v.dot(axis))
        val perpendicular = Vector3(v).sub(parallel)
        return Vector3(center).add(perpendicular).add(parallel.scl(scale))
    }

    private fun scaleVectorAlongAxis(vector: Vector3, axis: Vector3, scale: Float): Vector3 {
        val parallel = Vector3(axis).scl(vector.dot(axis))
        val perpendicular = Vector3(vector).sub(parallel)
        return Vector3(perpendicular).add(parallel.scl(scale))
    }

    private fun renderGroupPreview(renderer: ShapeRenderer, center: Vector3, axis: Vector3, scale: Float) {
        if (scene.selectedGroups().isEmpty()) {
            return
        }
        renderer.color = Color(0.25f, 0.85f, 0.55f, 1f)
        scene.selectedGroups().forEach { group ->
            val bounds = group.worldBounds() ?: return@forEach
            val corners = arrayOf(
                Vector3(bounds.min.x, bounds.min.y, bounds.min.z),
                Vector3(bounds.min.x, bounds.min.y, bounds.max.z),
                Vector3(bounds.min.x, bounds.max.y, bounds.min.z),
                Vector3(bounds.min.x, bounds.max.y, bounds.max.z),
                Vector3(bounds.max.x, bounds.min.y, bounds.min.z),
                Vector3(bounds.max.x, bounds.min.y, bounds.max.z),
                Vector3(bounds.max.x, bounds.max.y, bounds.min.z),
                Vector3(bounds.max.x, bounds.max.y, bounds.max.z)
            )
            corners.forEach { corner ->
                val scaled = scaleAlongAxis(corner, center, axis, scale)
                corner.set(scaled)
            }
            val min = corners.reduce { a, b -> Vector3(
                kotlin.math.min(a.x, b.x),
                kotlin.math.min(a.y, b.y),
                kotlin.math.min(a.z, b.z)
            ) }
            val max = corners.reduce { a, b -> Vector3(
                kotlin.math.max(a.x, b.x),
                kotlin.math.max(a.y, b.y),
                kotlin.math.max(a.z, b.z)
            ) }
            renderer.line(min.x, min.y, min.z, max.x, min.y, min.z)
            renderer.line(max.x, min.y, min.z, max.x, min.y, max.z)
            renderer.line(max.x, min.y, max.z, min.x, min.y, max.z)
            renderer.line(min.x, min.y, max.z, min.x, min.y, min.z)
            renderer.line(min.x, max.y, min.z, max.x, max.y, min.z)
            renderer.line(max.x, max.y, min.z, max.x, max.y, max.z)
            renderer.line(max.x, max.y, max.z, min.x, max.y, max.z)
            renderer.line(min.x, max.y, max.z, min.x, max.y, min.z)
            renderer.line(min.x, min.y, min.z, min.x, max.y, min.z)
            renderer.line(max.x, min.y, min.z, max.x, max.y, min.z)
            renderer.line(max.x, min.y, max.z, max.x, max.y, max.z)
            renderer.line(min.x, min.y, max.z, min.x, max.y, max.z)
        }
    }
}

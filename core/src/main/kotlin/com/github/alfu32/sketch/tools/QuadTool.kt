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

class QuadTool(
    private val lineStore: DraftLineStore,
    private val faceStore: DraftFaceStore
) : Tool {
    override val id: ToolId = ToolId.QUAD
    override val message: String = "Pick origin point."

    private var origin: Vector3? = null
    private var pointB: Vector3? = null
    private var pickNormal: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Pick origin point."
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
        if (origin == null) {
            origin = Vector3(world)
            pickNormal = normal?.cpy()
            status.message = "Pick second point."
            return true
        }
        if (pointB == null) {
            pointB = Vector3(world)
            status.message = "Pick third point."
            return true
        }
        val a = origin ?: return false
        val b = pointB ?: return false
        val c = Vector3(world)
        val u = Vector3(b).sub(a)
        val v = Vector3(c).sub(a)
        if (u.len2() <= 1e-4f || v.len2() <= 1e-4f) {
            status.message = "Points are too close."
            clearTransient()
            return true
        }
        val d = Vector3(b).add(v)
        val corners = listOf(Vector3(a), Vector3(b), d, Vector3(c))
        for (i in 0 until 4) {
            val p0 = corners[i]
            val p1 = corners[(i + 1) % 4]
            lineStore.addSegment(p0, p1)
        }
        val normalPref = pickNormal ?: Vector3(u).crs(v).nor()
        val quadNormal = Vector3(u).crs(v)
        if (quadNormal.dot(normalPref) >= 0f) {
            faceStore.addTriangle(corners[0], corners[1], corners[2])
            faceStore.addTriangle(corners[0], corners[2], corners[3])
        } else {
            faceStore.addTriangle(corners[0], corners[2], corners[1])
            faceStore.addTriangle(corners[0], corners[3], corners[2])
        }
        clearTransient()
        status.message = "Quad created. Pick origin point."
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val a = origin ?: return
        if (!hasHover) {
            return
        }
        renderer.color = Color(0.55f, 0.85f, 0.95f, 1f)
        val b = pointB
        if (b == null) {
            renderer.line(a.x, a.y, a.z, hover.x, hover.y, hover.z)
            return
        }
        val c = hover
        val d = Vector3(b).add(Vector3(c).sub(a))
        renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        renderer.line(b.x, b.y, b.z, d.x, d.y, d.z)
        renderer.line(d.x, d.y, d.z, c.x, c.y, c.z)
        renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
    }

    private fun clearTransient() {
        origin = null
        pointB = null
        pickNormal = null
        hasHover = false
    }
}

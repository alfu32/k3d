package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class QuadTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.QUAD
    override val message: String = "Pick origin point."

    private var originWorld: Vector3? = null
    private var pointBWorld: Vector3? = null
    private var pickNormalWorld: Vector3? = null
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
        if (originWorld == null) {
            originWorld = Vector3(world)
            pickNormalWorld = normal?.cpy()
            status.message = "Pick second point."
            return true
        }
        if (pointBWorld == null) {
            pointBWorld = Vector3(world)
            status.message = "Pick third point."
            return true
        }
        val group = scene.activeGroup()
        val aWorld = originWorld ?: return false
        val bWorld = pointBWorld ?: return false
        val cWorld = Vector3(world)
        val u = Vector3(bWorld).sub(aWorld)
        val v = Vector3(cWorld).sub(aWorld)
        if (u.len2() <= 1e-4f || v.len2() <= 1e-4f) {
            status.message = "Points are too close."
            clearTransient()
            return true
        }
        val dWorld = Vector3(bWorld).add(v)
        val cornersWorld = listOf(Vector3(aWorld), Vector3(bWorld), dWorld, Vector3(cWorld))
        val cornersLocal = cornersWorld.map { group.toLocal(it) }
        for (i in 0 until 4) {
            val p0 = cornersLocal[i]
            val p1 = cornersLocal[(i + 1) % 4]
            group.addSketchSegment(p0, p1)
        }
        val normalPref = pickNormalWorld?.let { group.vectorToLocal(it) }
            ?: Vector3(cornersLocal[1]).sub(cornersLocal[0])
                .crs(Vector3(cornersLocal[2]).sub(cornersLocal[0])).nor()
        val quadNormal = Vector3(cornersLocal[1]).sub(cornersLocal[0])
            .crs(Vector3(cornersLocal[2]).sub(cornersLocal[0]))
        if (quadNormal.dot(normalPref) >= 0f) {
            group.faceStore.addTriangle(cornersLocal[0], cornersLocal[1], cornersLocal[2])
            group.faceStore.addTriangle(cornersLocal[0], cornersLocal[2], cornersLocal[3])
        } else {
            group.faceStore.addTriangle(cornersLocal[0], cornersLocal[2], cornersLocal[1])
            group.faceStore.addTriangle(cornersLocal[0], cornersLocal[3], cornersLocal[2])
        }
        clearTransient()
        status.message = "Quad created. Pick origin point."
        return true
    }

    override fun anchorWorld(): Vector3? {
        return originWorld
    }

    override fun render(renderer: ShapeRenderer) {
        val a = originWorld ?: return
        if (!hasHover) {
            return
        }
        renderer.color = Color(0.55f, 0.85f, 0.95f, 1f)
        val b = pointBWorld
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
        originWorld = null
        pointBWorld = null
        pickNormalWorld = null
        hasHover = false
    }
}

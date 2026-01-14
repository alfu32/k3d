package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class PushPullTool(
    private val lineStore: DraftLineStore,
    private val faceStore: DraftFaceStore,
    private val camera: com.badlogic.gdx.graphics.Camera
) : Tool {
    override val id: ToolId = ToolId.PUSH_PULL
    override val message: String = "Click face to start push/pull."

    private var activeTriangles: List<DraftFaceStore.Triangle> = emptyList()
    private var anchorPoint: Vector3? = null
    private var normal: Vector3? = null
    private var currentDistance = 0f
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Click face to start push/pull."
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
        val anchor = anchorPoint
        val faceNormal = this.normal
        if (anchor != null && faceNormal != null && valid && world != null) {
            currentDistance = Vector3(world).sub(anchor).dot(faceNormal)
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        if (anchorPoint == null) {
            val ray = camera.getPickRay(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            val hit = faceStore.pickTriangle(ray) ?: return false
            val faceNormal = facingNormal(hit.normal, ray.direction)
            val coplanar = faceStore.collectCoplanarConnected(hit.triangle)
            activeTriangles = if (coplanar.isNotEmpty()) coplanar else listOf(hit.triangle)
            anchorPoint = Vector3(hit.point)
            this.normal = faceNormal
            status.message = "Drag to extrude. Click to commit."
            return true
        }
        val anchor = anchorPoint ?: return false
        val faceNormal = this.normal ?: return false
        if (hasHover && valid && world != null) {
            currentDistance = Vector3(world).sub(anchor).dot(faceNormal)
        }
        commitExtrusion(faceNormal, currentDistance)
        clearTransient()
        status.message = "Click face to start push/pull."
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        if (!hasHover || activeTriangles.isEmpty()) {
            return
        }
        val faceNormal = normal ?: return
        renderer.color = Color(0.95f, 0.75f, 0.25f, 1f)
        val offset = Vector3(faceNormal).scl(currentDistance)
        activeTriangles.forEach { tri ->
            val a = Vector3(tri.a)
            val b = Vector3(tri.b)
            val c = Vector3(tri.c)
            val ap = Vector3(a).add(offset)
            val bp = Vector3(b).add(offset)
            val cp = Vector3(c).add(offset)
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            renderer.line(b.x, b.y, b.z, c.x, c.y, c.z)
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
            renderer.line(ap.x, ap.y, ap.z, bp.x, bp.y, bp.z)
            renderer.line(bp.x, bp.y, bp.z, cp.x, cp.y, cp.z)
            renderer.line(cp.x, cp.y, cp.z, ap.x, ap.y, ap.z)
            renderer.line(a.x, a.y, a.z, ap.x, ap.y, ap.z)
            renderer.line(b.x, b.y, b.z, bp.x, bp.y, bp.z)
            renderer.line(c.x, c.y, c.z, cp.x, cp.y, cp.z)
        }
    }

    private fun commitExtrusion(faceNormal: Vector3, distance: Float) {
        if (activeTriangles.isEmpty() || kotlin.math.abs(distance) <= 1e-4f) {
            return
        }
        val offset = Vector3(faceNormal).scl(distance)
        val reverse = distance < 0f
        activeTriangles.forEach { tri ->
            val a = Vector3(tri.a)
            val b = Vector3(tri.b)
            val c = Vector3(tri.c)
            val ap = Vector3(a).add(offset)
            val bp = Vector3(b).add(offset)
            val cp = Vector3(c).add(offset)

            if (!reverse) {
                faceStore.addTriangle(ap, bp, cp)
            } else {
                faceStore.addTriangle(ap, cp, bp)
            }

            addSideQuad(a, b, ap, bp, reverse)
            addSideQuad(b, c, bp, cp, reverse)
            addSideQuad(c, a, cp, ap, reverse)

            lineStore.addSegment(ap, bp)
            lineStore.addSegment(bp, cp)
            lineStore.addSegment(cp, ap)
            lineStore.addSegment(a, ap)
            lineStore.addSegment(b, bp)
            lineStore.addSegment(c, cp)
        }
    }

    private fun addSideQuad(a: Vector3, b: Vector3, ap: Vector3, bp: Vector3, reverse: Boolean) {
        if (!reverse) {
            faceStore.addTriangle(a, b, bp)
            faceStore.addTriangle(a, bp, ap)
        } else {
            faceStore.addTriangle(a, bp, b)
            faceStore.addTriangle(a, ap, bp)
        }
    }

    private fun clearTransient() {
        activeTriangles = emptyList()
        anchorPoint = null
        normal = null
        currentDistance = 0f
        hasHover = false
    }

    private fun facingNormal(normal: Vector3, rayDir: Vector3): Vector3 {
        return if (normal.dot(rayDir) > 0f) Vector3(normal).scl(-1f) else Vector3(normal)
    }
}

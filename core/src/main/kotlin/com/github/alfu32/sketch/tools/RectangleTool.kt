package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class RectangleTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.RECTANGLE
    override val message: String = "Click to start rectangle."

    private var anchorWorld: Vector3? = null
    private var pickNormalWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Click to start rectangle."
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
        if (anchorWorld == null) {
            anchorWorld = Vector3(world)
            pickNormalWorld = normal?.let { Vector3(it) } ?: Vector3(0f, 1f, 0f)
            status.message = "Click to finish rectangle."
        } else {
            val startWorld = anchorWorld ?: return false
            val currentBasis = chooseRectangleBasis(startWorld, world, pickNormalWorld ?: Vector3(0f, 1f, 0f))
            val cornersWorld = rectangleCorners(startWorld, world, currentBasis)
            val cornersLocal = cornersWorld.map { group.toLocal(it) }
            for (i in 0 until 4) {
                val a = cornersLocal[i]
                val b = cornersLocal[(i + 1) % 4]
                group.addSketchSegment(a, b)
            }
            val preferred = normal?.let { group.vectorToLocal(it) }
                ?: pickNormalWorld?.let { group.vectorToLocal(it) }
                ?: Vector3(0f, 1f, 0f)
            addRectangleFace(group, cornersLocal, preferred)
            anchorWorld = null
            pickNormalWorld = null
            status.message = "Click to start rectangle."
        }
        return true
    }

    override fun anchorWorld(): Vector3? {
        return anchorWorld
    }

    override fun render(renderer: ShapeRenderer) {
        val start = anchorWorld
        if (start != null && hasHover) {
            val currentBasis = chooseRectangleBasis(start, hover, pickNormalWorld ?: Vector3(0f, 1f, 0f))
            renderer.color = ToolFeedbackColors.SECONDARY
            val corners = rectangleCorners(start, hover, currentBasis)
            for (i in 0 until 4) {
                val a = corners[i]
                val b = corners[(i + 1) % 4]
                renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            }
        }
    }

    private fun rectangleCorners(start: Vector3, end: Vector3, basis: PlaneBasis): List<Vector3> {
        val delta = Vector3(end).sub(start)
        val u = delta.dot(basis.axisU)
        val v = delta.dot(basis.axisV)
        val p0 = Vector3(start)
        val p1 = Vector3(start).mulAdd(basis.axisU, u)
        val p2 = Vector3(p1).mulAdd(basis.axisV, v)
        val p3 = Vector3(start).mulAdd(basis.axisV, v)
        return listOf(p0, p1, p2, p3)
    }

    private fun addRectangleFace(group: GroupScene.GroupNode, corners: List<Vector3>, preferredNormal: Vector3) {
        val normal = Vector3(corners[1]).sub(corners[0]).crs(Vector3(corners[2]).sub(corners[0]))
        if (normal.dot(preferredNormal) >= 0f) {
            group.faceStore.addTriangle(corners[0], corners[1], corners[2])
            group.faceStore.addTriangle(corners[0], corners[2], corners[3])
        } else {
            group.faceStore.addTriangle(corners[0], corners[2], corners[1])
            group.faceStore.addTriangle(corners[0], corners[3], corners[2])
        }
    }

    private fun clearTransient() {
        anchorWorld = null
        pickNormalWorld = null
        hasHover = false
    }

    private fun chooseRectangleBasis(start: Vector3, end: Vector3, fallbackNormal: Vector3): PlaneBasis {
        val delta = Vector3(end).sub(start)
        val eps = 1e-2f
        val absX = kotlin.math.abs(delta.x)
        val absY = kotlin.math.abs(delta.y)
        val absZ = kotlin.math.abs(delta.z)

        if (absY <= eps) {
            val axisU = Vector3(1f, 0f, 0f)
            val axisV = Vector3(0f, 0f, 1f)
            val normal = Vector3(0f, 1f, 0f)
            return PlaneBasis(facingNormal(normal, fallbackNormal), axisU, axisV)
        }
        if (absX <= eps) {
            val axisU = Vector3(0f, 1f, 0f)
            val axisV = Vector3(0f, 0f, 1f)
            val normal = Vector3(1f, 0f, 0f)
            return PlaneBasis(facingNormal(normal, fallbackNormal), axisU, axisV)
        }
        if (absZ <= eps) {
            val axisU = Vector3(1f, 0f, 0f)
            val axisV = Vector3(0f, 1f, 0f)
            val normal = Vector3(0f, 0f, 1f)
            return PlaneBasis(facingNormal(normal, fallbackNormal), axisU, axisV)
        }

        val dirXZ = Vector3(delta.x, 0f, delta.z)
        if (dirXZ.len2() > eps * eps) {
            val axisU = dirXZ.nor()
            val axisV = Vector3(0f, 1f, 0f)
            val normal = Vector3(axisU).crs(axisV).nor()
            return PlaneBasis(facingNormal(normal, fallbackNormal), axisU, axisV)
        }

        return planeBasisFromNormal(fallbackNormal)
    }

    private fun facingNormal(normal: Vector3, reference: Vector3): Vector3 {
        return if (normal.dot(reference) < 0f) Vector3(normal).scl(-1f) else Vector3(normal)
    }
}

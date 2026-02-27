package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement

class MeshTool(
    private val scene: GroupScene,
    private val onSelectTool: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.MESH
    override val message: String = "Pick mesh points. Esc exits tool."

    private val pickedWorld = mutableListOf<Vector3>()
    private val hoverWorld = Vector3()
    private var hoverValid = false
    private var hoverNormalWorld: Vector3? = null

    override fun onEnter(status: StatusModel) {
        status.message = "Pick mesh points. Esc exits tool."
    }

    override fun onExit(status: StatusModel) {
        clearTransient()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clearTransient()
        status.message = "Canceled."
        onSelectTool()
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hoverWorld.set(world)
            hoverValid = true
            hoverNormalWorld = normal?.cpy()
        } else {
            hoverValid = false
            hoverNormalWorld = null
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) return false
        val current = Vector3(world)
        val group = scene.activeGroup()
        val prev = pickedWorld.lastOrNull()
        pickedWorld.add(current)
        if (prev != null) {
            group.addSketchSegment(group.toLocal(prev), group.toLocal(current))
        }
        if (pickedWorld.size >= 3) {
            val previous = pickedWorld[pickedWorld.size - 2]
            val nearest = nearestPointExcludingLastTwo(current) ?: nearestPointExcluding(setOf(pickedWorld.size - 1, pickedWorld.size - 2))
            if (nearest != null) {
                addTriangleOriented(group, nearest, previous, current, normal ?: hoverNormalWorld)
                status.message = "Mesh point added. Triangle created."
            } else {
                status.message = "Mesh point added."
            }
        } else {
            status.message = "Pick more mesh points. Esc exits tool."
        }
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ESCAPE -> {
                clearTransient()
                status.message = "Mesh tool exited."
                onSelectTool()
                true
            }
            Input.Keys.ENTER -> {
                clearTransient()
                status.message = message
                true
            }
            else -> false
        }
    }

    override fun anchorWorld(): Vector3? = pickedWorld.lastOrNull()?.cpy()

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val anchor = pickedWorld.lastOrNull() ?: return null
        if (!hoverValid) return null
        return ToolMeasurement(Vector3(anchor), Vector3(hoverWorld))
    }

    override fun render(renderer: ShapeRenderer) {
        if (pickedWorld.isEmpty()) return
        renderer.color = ToolFeedbackColors.SECONDARY
        for (i in 1 until pickedWorld.size) {
            val a = pickedWorld[i - 1]
            val b = pickedWorld[i]
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
        if (hoverValid) {
            val last = pickedWorld.last()
            renderer.color = ToolFeedbackColors.PRIMARY
            renderer.line(last.x, last.y, last.z, hoverWorld.x, hoverWorld.y, hoverWorld.z)
            if (pickedWorld.size >= 2) {
                val prev = pickedWorld.last()
                val nearest = nearestPointForPreview(hoverWorld, pickedWorld.size - 1) // exclude preview's previous point only
                if (nearest != null) {
                    renderer.color = ToolFeedbackColors.PRIMARY
                    renderer.line(nearest.x, nearest.y, nearest.z, prev.x, prev.y, prev.z)
                    renderer.line(nearest.x, nearest.y, nearest.z, hoverWorld.x, hoverWorld.y, hoverWorld.z)
                }
            }
        }
    }

    private fun nearestPointExcludingLastTwo(current: Vector3): Vector3? {
        if (pickedWorld.size < 3) return null
        val lastIndex = pickedWorld.lastIndex
        val prevIndex = lastIndex - 1
        var best: Vector3? = null
        var bestDist2 = Float.POSITIVE_INFINITY
        pickedWorld.forEachIndexed { index, point ->
            if (index == lastIndex || index == prevIndex) return@forEachIndexed
            val d2 = point.dst2(current)
            if (d2 < bestDist2) {
                bestDist2 = d2
                best = point
            }
        }
        return best?.cpy()
    }

    private fun nearestPointExcluding(excluded: Set<Int>): Vector3? {
        var best: Vector3? = null
        var bestDist2 = Float.POSITIVE_INFINITY
        pickedWorld.forEachIndexed { index, point ->
            if (index in excluded) return@forEachIndexed
            val ref = pickedWorld.last()
            val d2 = point.dst2(ref)
            if (d2 < bestDist2) {
                bestDist2 = d2
                best = point
            }
        }
        return best?.cpy()
    }

    private fun nearestPointForPreview(current: Vector3, previousIndex: Int): Vector3? {
        if (pickedWorld.isEmpty()) return null
        var best: Vector3? = null
        var bestDist2 = Float.POSITIVE_INFINITY
        pickedWorld.forEachIndexed { index, point ->
            if (index == previousIndex) return@forEachIndexed
            val d2 = point.dst2(current)
            if (d2 < bestDist2) {
                bestDist2 = d2
                best = point
            }
        }
        return best?.cpy()
    }

    private fun addTriangleOriented(
        group: GroupScene.GroupNode,
        aWorld: Vector3,
        bWorld: Vector3,
        cWorld: Vector3,
        normalWorld: Vector3?
    ) {
        val a = group.toLocal(aWorld)
        val b = group.toLocal(bWorld)
        val c = group.toLocal(cWorld)
        val triNormal = Vector3(b).sub(a).crs(Vector3(c).sub(a))
        if (triNormal.len2() <= 1e-6f) return
        val preferredLocal = normalWorld?.let { group.vectorToLocal(it).nor() }
        if (preferredLocal != null && triNormal.dot(preferredLocal) < 0f) {
            group.faceStore.addTriangle(a, c, b)
        } else {
            group.faceStore.addTriangle(a, b, c)
        }
    }

    private fun clearTransient() {
        pickedWorld.clear()
        hoverValid = false
        hoverNormalWorld = null
    }
}

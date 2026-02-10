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
import kotlin.math.acos

class MoveTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.MOVE
    override val message: String = "Pick reference point."

    private var start: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false
    private var hoverNormal: Vector3? = null

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

    override fun supportsCopyMode(): Boolean = true

    override fun onCopyModeChanged(status: StatusModel, enabled: Boolean) {
        if (start == null) {
            status.message = "Pick reference point."
        } else {
            status.message = "Pick destination point."
        }
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hover.set(world)
            hasHover = true
            hoverNormal = normal?.cpy()
        } else {
            hasHover = false
            hoverNormal = null
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
        val group = scene.activeGroup()
        val localDelta = group.vectorToLocal(delta)
        val voxelDx = kotlin.math.round(localDelta.x).toInt()
        val voxelDy = kotlin.math.round(localDelta.y).toInt()
        val voxelDz = kotlin.math.round(localDelta.z).toInt()
        if (status.copyMode) {
            val movedVoxels = if (scene.isVoxelGroup(group)) {
                scene.moveSelectedVoxels(group, voxelDx, voxelDy, voxelDz, copy = true)
            } else {
                0
            }
            val movedFaces = group.faceStore.copySelected { point -> Vector3(point).add(localDelta) }
            val movedEdges = group.lineStore.copySelected { point -> Vector3(point).add(localDelta) }
            val movedDimensions = group.dimensionStore.copySelected { point -> Vector3(point).add(localDelta) }
            val movedTexts = group.textStore.copySelected { point -> Vector3(point).add(localDelta) }
            val movedGroups = scene.copySelectedGroups(
                { point -> Vector3(point).add(delta) },
                { vector -> Vector3(vector) }
            )
            alignSelectedGroupsIfNeeded()
            status.message =
                "Copied | voxels $movedVoxels edges $movedEdges faces $movedFaces dims $movedDimensions texts $movedTexts groups $movedGroups"
        } else {
            val movedVoxels = if (scene.isVoxelGroup(group)) {
                scene.moveSelectedVoxels(group, voxelDx, voxelDy, voxelDz, copy = false)
            } else {
                0
            }
            val movedFaces = group.faceStore.transformSelected { point -> Vector3(point).add(localDelta) }
            val movedEdges = group.lineStore.transformSelected { point -> Vector3(point).add(localDelta) }
            val movedDimensions = group.dimensionStore.transformSelected { point -> Vector3(point).add(localDelta) }
            val movedTexts = group.textStore.transformSelected { point -> Vector3(point).add(localDelta) }
            val movedGroups = scene.transformSelectedGroups(
                { point -> Vector3(point).add(delta) },
                { vector -> Vector3(vector) }
            )
            alignSelectedGroupsIfNeeded()
            status.message =
                "Moved | voxels $movedVoxels edges $movedEdges faces $movedFaces dims $movedDimensions texts $movedTexts groups $movedGroups"
        }
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
                renderPreview(renderer, delta)
                renderGroupPreview(renderer, delta)
            }
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val startPoint = start ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(
            startWorld = Vector3(startPoint),
            endWorld = Vector3(hover)
        )
    }

    private fun drawCross(renderer: ShapeRenderer, point: Vector3, size: Float) {
        renderer.line(point.x - size, point.y, point.z, point.x + size, point.y, point.z)
        renderer.line(point.x, point.y - size, point.z, point.x, point.y + size, point.z)
        renderer.line(point.x, point.y, point.z - size, point.x, point.y, point.z + size)
    }

    private fun clearTransient() {
        start = null
        hasHover = false
        hoverNormal = null
    }

    private fun renderPreview(renderer: ShapeRenderer, deltaWorld: Vector3) {
        renderer.color = Color(0.25f, 0.85f, 0.55f, 1f)
        val group = scene.activeGroup()
        group.faceStore.getSelected().forEach { tri ->
            val a = group.toWorld(tri.a).add(deltaWorld)
            val b = group.toWorld(tri.b).add(deltaWorld)
            val c = group.toWorld(tri.c).add(deltaWorld)
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            renderer.line(b.x, b.y, b.z, c.x, c.y, c.z)
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
        }
        group.lineStore.getSelected().forEach { segment ->
            val a = group.toWorld(segment.start).add(deltaWorld)
            val b = group.toWorld(segment.end).add(deltaWorld)
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
    }

    private fun renderGroupPreview(renderer: ShapeRenderer, deltaWorld: Vector3) {
        if (scene.selectedGroups().isEmpty()) {
            return
        }
        renderer.color = Color(0.25f, 0.85f, 0.55f, 1f)
        scene.selectedGroups().forEach { group ->
            val corners = group.orientedBoundsCorners() ?: return@forEach
            corners.indices.forEach { idx ->
                corners[idx].add(deltaWorld)
            }
            drawWireBox(renderer, corners)
        }
    }

    private fun drawWireBox(renderer: ShapeRenderer, corners: Array<Vector3>) {
        if (corners.size < 8) {
            return
        }
        val c0 = corners[0]
        val c1 = corners[1]
        val c2 = corners[2]
        val c3 = corners[3]
        val c4 = corners[4]
        val c5 = corners[5]
        val c6 = corners[6]
        val c7 = corners[7]
        renderer.line(c0, c1)
        renderer.line(c1, c2)
        renderer.line(c2, c3)
        renderer.line(c3, c0)
        renderer.line(c4, c5)
        renderer.line(c5, c6)
        renderer.line(c6, c7)
        renderer.line(c7, c4)
        renderer.line(c0, c4)
        renderer.line(c1, c5)
        renderer.line(c2, c6)
        renderer.line(c3, c7)
    }

    private fun alignSelectedGroupsIfNeeded() {
        val normal = hoverNormal?.cpy()?.nor() ?: return
        scene.selectedGroups().forEach { group ->
            if (!group.gluedToSurface) {
                return@forEach
            }
            val axes = group.worldAxes()
            val lenU = axes.u.len()
            val lenV = axes.v.len()
            val lenW = axes.w.len()
            if (lenW <= 1e-6f) {
                return@forEach
            }
            val newW = normal.cpy().nor().scl(lenW)
            var newU = axes.u.cpy()
            newU.mulAdd(newW, -newU.dot(newW) / (lenW * lenW))
            if (newU.len2() <= 1e-6f) {
                newU = axes.v.cpy()
                newU.mulAdd(newW, -newU.dot(newW) / (lenW * lenW))
            }
            if (newU.len2() <= 1e-6f) {
                return@forEach
            }
            newU.nor().scl(lenU)
            val newV = Vector3(newW).crs(newU).nor().scl(lenV)
            val origin = group.worldOrigin()
            group.setInstanceFromWorld(origin, newU, newV, newW)
        }
    }
}

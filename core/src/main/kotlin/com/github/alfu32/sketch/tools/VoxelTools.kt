package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.model.VoxelStore
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class VoxelTool(
    private val scene: GroupScene,
    private val onSelectTool: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.VOXEL
    override val message: String = "Click to place voxel. Esc selects."

    override fun onEnter(status: StatusModel) {
        status.message = message
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        val group = scene.activeGroup()
        if (!scene.isVoxelGroup(group)) {
            status.message = "Active object is not a voxel group."
            return true
        }
        val localPoint = group.toLocal(world)
        val localNormal = normal?.let { group.vectorToLocal(it).nor() }
        val key = voxelKey(localPoint, localNormal)
        val color = scene.voxelColor(group) ?: status.paintColor
        val changed = scene.setVoxel(group, key.x, key.y, key.z, color)
        status.message = if (changed) {
            "Voxel placed at ${key.x}, ${key.y}, ${key.z}."
        } else {
            "Voxel unchanged at ${key.x}, ${key.y}, ${key.z}."
        }
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        if (keycode == Input.Keys.ESCAPE) {
            onSelectTool()
            return true
        }
        return false
    }
}

class VoxelVolumeTool(
    private val scene: GroupScene,
    private val onSelectTool: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.VOXEL_VOLUME
    override val message: String = "Pick first voxel corner."

    private var anchorKey: VoxelStore.Key? = null
    private var hoverKey: VoxelStore.Key? = null
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        anchorKey = null
        hoverKey = null
        hasHover = false
        status.message = message
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        val group = scene.activeGroup()
        if (!scene.isVoxelGroup(group) || !valid || world == null) {
            hasHover = false
            return
        }
        val localPoint = group.toLocal(world)
        val localNormal = normal?.let { group.vectorToLocal(it).nor() }
        hoverKey = voxelKey(localPoint, localNormal)
        hasHover = true
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        val group = scene.activeGroup()
        if (!scene.isVoxelGroup(group)) {
            status.message = "Active object is not a voxel group."
            return true
        }
        val localPoint = group.toLocal(world)
        val localNormal = normal?.let { group.vectorToLocal(it).nor() }
        val key = voxelKey(localPoint, localNormal)
        if (anchorKey == null) {
            anchorKey = key
            hoverKey = key
            hasHover = true
            status.message = "Pick opposite voxel corner."
            return true
        }
        val first = anchorKey ?: return true
        val color = scene.voxelColor(group) ?: status.paintColor
        val added = fillVolume(group, first, key, color)
        status.message = "Voxel volume placed: $added voxels."
        anchorKey = null
        hoverKey = null
        hasHover = false
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ESCAPE -> {
                anchorKey = null
                hoverKey = null
                hasHover = false
                onSelectTool()
                true
            }
            Input.Keys.ENTER -> {
                anchorKey = null
                hoverKey = null
                hasHover = false
                status.message = message
                true
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val a = anchorKey ?: return null
        val b = hoverKey ?: return null
        val group = scene.activeGroup()
        val start = group.toWorld(Vector3(a.x.toFloat(), a.y.toFloat(), a.z.toFloat()))
        val end = group.toWorld(Vector3((b.x + 1).toFloat(), (b.y + 1).toFloat(), (b.z + 1).toFloat()))
        return ToolMeasurement(start, end, Color(0.55f, 0.85f, 0.95f, 1f))
    }

    override fun render(renderer: ShapeRenderer) {
        val a = anchorKey ?: return
        val b = hoverKey ?: return
        if (!hasHover) {
            return
        }
        val group = scene.activeGroup()
        drawAabb(
            renderer,
            group,
            min(a.x, b.x),
            min(a.y, b.y),
            min(a.z, b.z),
            max(a.x, b.x) + 1,
            max(a.y, b.y) + 1,
            max(a.z, b.z) + 1,
            Color(0.35f, 0.8f, 0.95f, 1f)
        )
    }

    private fun fillVolume(group: GroupScene.GroupNode, a: VoxelStore.Key, b: VoxelStore.Key, color: Color): Int {
        val minX = min(a.x, b.x)
        val maxX = max(a.x, b.x)
        val minY = min(a.y, b.y)
        val maxY = max(a.y, b.y)
        val minZ = min(a.z, b.z)
        val maxZ = max(a.z, b.z)
        val voxels = mutableListOf<Pair<VoxelStore.Key, Color>>()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    voxels.add(VoxelStore.Key(x, y, z) to color)
                }
            }
        }
        return scene.setVoxels(group, voxels)
    }
}

class VoxelFrameTool(
    private val scene: GroupScene,
    private val onSelectTool: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.VOXEL_FRAME
    override val message: String = "Pick first frame corner."

    private var anchorKey: VoxelStore.Key? = null
    private var hoverKey: VoxelStore.Key? = null
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        anchorKey = null
        hoverKey = null
        hasHover = false
        status.message = message
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        val group = scene.activeGroup()
        if (!scene.isVoxelGroup(group) || !valid || world == null) {
            hasHover = false
            return
        }
        val localPoint = group.toLocal(world)
        val localNormal = normal?.let { group.vectorToLocal(it).nor() }
        hoverKey = voxelKey(localPoint, localNormal)
        hasHover = true
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        val group = scene.activeGroup()
        if (!scene.isVoxelGroup(group)) {
            status.message = "Active object is not a voxel group."
            return true
        }
        val localPoint = group.toLocal(world)
        val localNormal = normal?.let { group.vectorToLocal(it).nor() }
        val key = voxelKey(localPoint, localNormal)
        if (anchorKey == null) {
            anchorKey = key
            hoverKey = key
            hasHover = true
            status.message = "Pick opposite frame corner."
            return true
        }
        val first = anchorKey ?: return true
        val color = scene.voxelColor(group) ?: status.paintColor
        val added = fillFrame(group, first, key, color)
        status.message = "Voxel frame placed: $added voxels."
        anchorKey = null
        hoverKey = null
        hasHover = false
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ESCAPE -> {
                anchorKey = null
                hoverKey = null
                hasHover = false
                onSelectTool()
                true
            }
            Input.Keys.ENTER -> {
                anchorKey = null
                hoverKey = null
                hasHover = false
                status.message = message
                true
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val a = anchorKey ?: return null
        val b = hoverKey ?: return null
        val group = scene.activeGroup()
        val start = group.toWorld(Vector3(a.x.toFloat(), a.y.toFloat(), a.z.toFloat()))
        val end = group.toWorld(Vector3((b.x + 1).toFloat(), (b.y + 1).toFloat(), (b.z + 1).toFloat()))
        return ToolMeasurement(start, end, Color(0.85f, 0.65f, 0.25f, 1f))
    }

    override fun render(renderer: ShapeRenderer) {
        val a = anchorKey ?: return
        val b = hoverKey ?: return
        if (!hasHover) {
            return
        }
        val group = scene.activeGroup()
        drawAabb(
            renderer,
            group,
            min(a.x, b.x),
            min(a.y, b.y),
            min(a.z, b.z),
            max(a.x, b.x) + 1,
            max(a.y, b.y) + 1,
            max(a.z, b.z) + 1,
            Color(0.95f, 0.7f, 0.3f, 1f)
        )
    }

    private fun fillFrame(group: GroupScene.GroupNode, a: VoxelStore.Key, b: VoxelStore.Key, color: Color): Int {
        val minX = min(a.x, b.x)
        val maxX = max(a.x, b.x)
        val minY = min(a.y, b.y)
        val maxY = max(a.y, b.y)
        val minZ = min(a.z, b.z)
        val maxZ = max(a.z, b.z)
        val voxels = mutableListOf<Pair<VoxelStore.Key, Color>>()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val boundaryCount =
                        (if (x == minX || x == maxX) 1 else 0) +
                        (if (y == minY || y == maxY) 1 else 0) +
                        (if (z == minZ || z == maxZ) 1 else 0)
                    if (boundaryCount >= 2) {
                        voxels.add(VoxelStore.Key(x, y, z) to color)
                    }
                }
            }
        }
        return scene.setVoxels(group, voxels)
    }
}

private fun voxelKey(local: Vector3, normal: Vector3?): VoxelStore.Key {
    var x = floor(local.x.toDouble() + 1e-4).toInt()
    var y = floor(local.y.toDouble() + 1e-4).toInt()
    var z = floor(local.z.toDouble() + 1e-4).toInt()
    if (normal != null && normal.len2() > 1e-6f) {
        val absX = abs(normal.x)
        val absY = abs(normal.y)
        val absZ = abs(normal.z)
        when {
            absX >= absY && absX >= absZ && absX >= 0.5f -> {
                x = if (normal.x >= 0f) {
                    floor(local.x.toDouble() + 1e-4).toInt()
                } else {
                    floor(local.x.toDouble() - 1e-4).toInt()
                }
            }
            absY >= absX && absY >= absZ && absY >= 0.5f -> {
                y = if (normal.y >= 0f) {
                    floor(local.y.toDouble() + 1e-4).toInt()
                } else {
                    floor(local.y.toDouble() - 1e-4).toInt()
                }
            }
            absZ >= absX && absZ >= absY && absZ >= 0.5f -> {
                z = if (normal.z >= 0f) {
                    floor(local.z.toDouble() + 1e-4).toInt()
                } else {
                    floor(local.z.toDouble() - 1e-4).toInt()
                }
            }
        }
    }
    return VoxelStore.Key(x, y, z)
}

private fun drawAabb(
    renderer: ShapeRenderer,
    group: GroupScene.GroupNode,
    minX: Int,
    minY: Int,
    minZ: Int,
    maxXExclusive: Int,
    maxYExclusive: Int,
    maxZExclusive: Int,
    color: Color
) {
    val min = Vector3(minX.toFloat(), minY.toFloat(), minZ.toFloat())
    val max = Vector3(maxXExclusive.toFloat(), maxYExclusive.toFloat(), maxZExclusive.toFloat())
    val c = arrayOf(
        Vector3(min.x, min.y, min.z),
        Vector3(max.x, min.y, min.z),
        Vector3(max.x, min.y, max.z),
        Vector3(min.x, min.y, max.z),
        Vector3(min.x, max.y, min.z),
        Vector3(max.x, max.y, min.z),
        Vector3(max.x, max.y, max.z),
        Vector3(min.x, max.y, max.z)
    )
    c.indices.forEach { i -> c[i] = group.toWorld(c[i]) }
    renderer.color = color
    renderer.line(c[0], c[1]); renderer.line(c[1], c[2]); renderer.line(c[2], c[3]); renderer.line(c[3], c[0])
    renderer.line(c[4], c[5]); renderer.line(c[5], c[6]); renderer.line(c[6], c[7]); renderer.line(c[7], c[4])
    renderer.line(c[0], c[4]); renderer.line(c[1], c[5]); renderer.line(c[2], c[6]); renderer.line(c[3], c[7])
}

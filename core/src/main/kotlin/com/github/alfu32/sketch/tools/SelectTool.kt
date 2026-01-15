package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class SelectTool(
    private val lineStore: DraftLineStore,
    private val faceStore: DraftFaceStore,
    private val camera: Camera
) : Tool {
    override val id: ToolId = ToolId.SELECT
    override val message: String = "Select entities."
    private val doubleClickMs = 350L
    private val clickDistanceSq = 36
    private var lastClickTime = 0L
    private var lastClickX = 0
    private var lastClickY = 0
    private var clickCount = 0
    private var volumeStart: Vector3? = null
    private var volumeEnd: Vector3? = null
    private var selectingVolume = false

    override fun onEnter(status: StatusModel) {
        status.message = "Select entities."
    }

    override fun onCancel(status: StatusModel) {
        lineStore.clearSelection()
        faceStore.clearSelection()
        selectingVolume = false
        volumeStart = null
        volumeEnd = null
        status.message = "Selection cleared."
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (selectingVolume && valid && world != null) {
            volumeEnd = Vector3(world)
        }
    }

    override fun onPointerDown(
        status: StatusModel,
        world: com.badlogic.gdx.math.Vector3?,
        normal: com.badlogic.gdx.math.Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        if (selectingVolume) {
            if (valid && world != null) {
                volumeEnd = Vector3(world)
                finalizeVolumeSelection(status)
                selectingVolume = false
                return true
            }
            return false
        }
        val clickType = updateClickCount()
        val ray = camera.getPickRay(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        val faceHit = faceStore.pickTriangle(ray)
        val edgeHit = lineStore.pickSegment(ray, camera, Gdx.input.x, Gdx.input.y)
        val pickedFace = faceHit != null
        val pickedEdge = edgeHit != null
        if (!pickedFace && !pickedEdge) {
            if (valid && world != null) {
                selectingVolume = true
                volumeStart = Vector3(world)
                volumeEnd = Vector3(world)
                status.message = "Volume select: pick second corner."
                return true
            }
            return false
        }
        if (clickType >= 3) {
            clickCount = 0
            status.message = "Triple-click reserved."
            return true
        }
        if (clickType == 2 && pickedFace) {
            val group = faceStore.collectCoplanarConnected(faceHit!!.triangle)
            val allSelected = group.all { faceStore.isSelected(it) }
            if (allSelected) {
                group.forEach { faceStore.removeSelection(it) }
                status.message = "Coplanar faces deselected."
            } else {
                group.forEach { faceStore.addSelection(it) }
                status.message = "Coplanar faces selected."
            }
            return true
        }
        if (pickedFace && pickedEdge) {
            if (faceHit!!.t <= edgeHit!!.t) {
                faceStore.toggleSelection(faceHit.triangle)
                status.message = "Face toggled."
            } else {
                lineStore.toggleSelection(edgeHit.segment)
                status.message = "Edge toggled."
            }
            return true
        }
        if (pickedFace) {
            faceStore.toggleSelection(faceHit!!.triangle)
            status.message = "Face toggled."
            return true
        }
        lineStore.toggleSelection(edgeHit!!.segment)
        status.message = "Edge toggled."
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val start = volumeStart ?: return
        val end = volumeEnd ?: return
        if (!selectingVolume) {
            return
        }
        val minX = kotlin.math.min(start.x, end.x)
        val minY = kotlin.math.min(start.y, end.y)
        val minZ = kotlin.math.min(start.z, end.z)
        val maxX = kotlin.math.max(start.x, end.x)
        val maxY = kotlin.math.max(start.y, end.y)
        val maxZ = kotlin.math.max(start.z, end.z)
        renderer.color = com.badlogic.gdx.graphics.Color(0.25f, 0.55f, 0.95f, 0.35f)
        renderer.box(minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ)
    }

    private fun finalizeVolumeSelection(status: StatusModel) {
        val start = volumeStart ?: return
        val end = volumeEnd ?: return
        val min = Vector3(
            kotlin.math.min(start.x, end.x),
            kotlin.math.min(start.y, end.y),
            kotlin.math.min(start.z, end.z)
        )
        val max = Vector3(
            kotlin.math.max(start.x, end.x),
            kotlin.math.max(start.y, end.y),
            kotlin.math.max(start.z, end.z)
        )
        val faceCount = faceStore.selectInVolume(min, max, replace = true)
        val edgeCount = lineStore.selectInVolume(min, max, replace = true)
        status.message = "Volume select | edges $edgeCount faces $faceCount"
    }

    private fun updateClickCount(): Int {
        val now = TimeUtils.millis()
        val x = Gdx.input.x
        val y = Gdx.input.y
        val dx = x - lastClickX
        val dy = y - lastClickY
        val withinTime = (now - lastClickTime) <= doubleClickMs
        val withinDistance = (dx * dx + dy * dy) <= clickDistanceSq
        clickCount = if (withinTime && withinDistance) {
            (clickCount + 1).coerceAtMost(3)
        } else {
            1
        }
        lastClickTime = now
        lastClickX = x
        lastClickY = y
        return clickCount
    }

}

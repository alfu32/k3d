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
    data class WindowRect(val x: Float, val y: Float, val width: Float, val height: Float)

    override val id: ToolId = ToolId.SELECT
    override val message: String = "Select entities."
    private val doubleClickMs = 350L
    private val clickDistanceSq = 36
    private val dragDistanceSq = 49
    private var lastClickTime = 0L
    private var lastClickX = 0
    private var lastClickY = 0
    private var clickCount = 0
    private var volumeStartRaw: Vector3? = null
    private var volumeEndRaw: Vector3? = null
    private var selectingVolume = false
    private var selectingWindow = false
    private var windowDragActive = false
    private var windowStartX = 0
    private var windowStartY = 0
    private var windowEndX = 0
    private var windowEndY = 0
    private var pendingVolumeStart: Vector3? = null

    override fun onEnter(status: StatusModel) {
        status.message = "Select entities."
    }

    override fun onCancel(status: StatusModel) {
        lineStore.clearSelection()
        faceStore.clearSelection()
        selectingVolume = false
        volumeStartRaw = null
        volumeEndRaw = null
        selectingWindow = false
        windowDragActive = false
        pendingVolumeStart = null
        status.message = "Selection cleared."
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (selectingVolume && valid && world != null) {
            volumeEndRaw = Vector3(world)
        }
        if (selectingWindow && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            windowEndX = Gdx.input.x
            windowEndY = Gdx.input.y
            val dx = windowEndX - windowStartX
            val dy = windowEndY - windowStartY
            if (dx * dx + dy * dy >= dragDistanceSq) {
                windowDragActive = true
            }
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
                volumeEndRaw = Vector3(world)
                finalizeVolumeSelection(status)
                selectingVolume = false
                return true
            }
            return false
        }
        val ray = camera.getPickRay(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        val faceHit = faceStore.pickTriangle(ray)
        val edgeHit = lineStore.pickSegment(ray, camera, Gdx.input.x, Gdx.input.y)
        val pickedFace = faceHit != null
        val pickedEdge = edgeHit != null
        if (!pickedFace && !pickedEdge) {
            selectingWindow = true
            windowDragActive = false
            windowStartX = Gdx.input.x
            windowStartY = Gdx.input.y
            windowEndX = windowStartX
            windowEndY = windowStartY
            pendingVolumeStart = if (valid && world != null) Vector3(world) else null
            return true
        }
        val clickType = updateClickCount()
        if (clickType >= 3) {
            clickCount = 0
            val pickFace = pickedFace && (!pickedEdge || faceHit!!.t <= edgeHit!!.t)
            if (pickFace) {
                val group = faceStore.collectConnected(faceHit!!.triangle)
                val allSelected = group.all { faceStore.isSelected(it) }
                if (allSelected) {
                    group.forEach { faceStore.removeSelection(it) }
                    status.message = "Connected faces deselected."
                } else {
                    group.forEach { faceStore.addSelection(it) }
                    status.message = "Connected faces selected."
                }
            } else {
                val group = lineStore.collectConnected(edgeHit!!.segment)
                val allSelected = group.all { lineStore.isSelected(it) }
                if (allSelected) {
                    group.forEach { lineStore.removeSelection(it) }
                    status.message = "Connected edges deselected."
                } else {
                    group.forEach { lineStore.addSelection(it) }
                    status.message = "Connected edges selected."
                }
            }
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

    override fun onPointerUp(
        status: StatusModel,
        world: Vector3?,
        normal: Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        if (selectingWindow) {
            windowEndX = Gdx.input.x
            windowEndY = Gdx.input.y
            val rect = if (windowDragActive) windowRectTopLeft() else null
            val hadDrag = windowDragActive
            selectingWindow = false
            windowDragActive = false
            if (rect != null) {
                faceStore.clearSelection()
                lineStore.clearSelection()
                val faces = selectFacesInWindow(rect)
                val edges = selectEdgesInWindow(rect)
                status.message = "Window select | edges $edges faces $faces"
                return true
            } else if (pendingVolumeStart != null) {
                selectingVolume = true
                volumeStartRaw = Vector3(pendingVolumeStart)
                volumeEndRaw = Vector3(pendingVolumeStart)
                status.message = "Volume select: pick second corner."
                pendingVolumeStart = null
                return true
            }
            pendingVolumeStart = null
            return hadDrag
        }
        return false
    }

    fun windowRect(screenWidth: Int, screenHeight: Int): WindowRect? {
        if (!windowDragActive) {
            return null
        }
        val minX = kotlin.math.min(windowStartX, windowEndX).toFloat()
        val maxX = kotlin.math.max(windowStartX, windowEndX).toFloat()
        val minY = kotlin.math.min(windowStartY, windowEndY).toFloat()
        val maxY = kotlin.math.max(windowStartY, windowEndY).toFloat()
        val bottom = screenHeight - maxY
        val top = screenHeight - minY
        return WindowRect(minX, bottom, maxX - minX, top - bottom)
    }

    override fun render(renderer: ShapeRenderer) {
        val bounds = volumeBounds() ?: return
        val start = bounds.first
        val end = bounds.second
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
        drawWireBox(renderer, minX, minY, minZ, maxX, maxY, maxZ)
    }

    private fun finalizeVolumeSelection(status: StatusModel) {
        val bounds = volumeBounds() ?: return
        val faceCount = faceStore.selectInVolume(bounds.first, bounds.second, replace = true)
        val edgeCount = lineStore.selectInVolume(bounds.first, bounds.second, replace = true)
        status.message = "Volume select | edges $edgeCount faces $faceCount"
    }

    private data class WindowRectTopLeft(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float
    )

    private fun windowRectTopLeft(): WindowRectTopLeft? {
        if (!windowDragActive) {
            return null
        }
        val minX = kotlin.math.min(windowStartX, windowEndX).toFloat()
        val maxX = kotlin.math.max(windowStartX, windowEndX).toFloat()
        val minY = kotlin.math.min(windowStartY, windowEndY).toFloat()
        val maxY = kotlin.math.max(windowStartY, windowEndY).toFloat()
        return WindowRectTopLeft(minX, maxX, minY, maxY)
    }

    private fun selectFacesInWindow(rect: WindowRectTopLeft): Int {
        var count = 0
        faceStore.getTriangles().forEach { tri ->
            val a = projectToScreen(tri.a)
            val b = projectToScreen(tri.b)
            val c = projectToScreen(tri.c)
            if (pointInRect(a, rect) && pointInRect(b, rect) && pointInRect(c, rect)) {
                if (faceStore.addSelection(tri)) {
                    count++
                }
            }
        }
        return count
    }

    private fun selectEdgesInWindow(rect: WindowRectTopLeft): Int {
        var count = 0
        lineStore.getSegments().forEach { segment ->
            val a = projectToScreen(segment.start)
            val b = projectToScreen(segment.end)
            if (pointInRect(a, rect) && pointInRect(b, rect)) {
                if (lineStore.addSelection(segment)) {
                    count++
                }
            }
        }
        return count
    }

    private fun projectToScreen(point: Vector3): Vector3 {
        val projected = camera.project(Vector3(point))
        projected.y = Gdx.graphics.height - projected.y
        return projected
    }

    private fun pointInRect(point: Vector3, rect: WindowRectTopLeft): Boolean {
        return point.x >= rect.minX &&
            point.x <= rect.maxX &&
            point.y >= rect.minY &&
            point.y <= rect.maxY
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

    private fun volumeBounds(): Pair<Vector3, Vector3>? {
        val start = volumeStartRaw ?: return null
        val end = volumeEndRaw ?: return null
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
        return min to max
    }

    private fun drawWireBox(
        renderer: ShapeRenderer,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float
    ) {
        // Bottom rectangle
        renderer.line(minX, minY, minZ, maxX, minY, minZ)
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ)
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ)
        renderer.line(minX, minY, maxZ, minX, minY, minZ)
        // Top rectangle
        renderer.line(minX, maxY, minZ, maxX, maxY, minZ)
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ)
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ)
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ)
        // Vertical edges
        renderer.line(minX, minY, minZ, minX, maxY, minZ)
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ)
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ)
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ)
    }

}

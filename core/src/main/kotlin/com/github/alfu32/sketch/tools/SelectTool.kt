package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.math.Vector2
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
    data class WindowRect(val x: Float, val y: Float, val width: Float, val height: Float, val dashed: Boolean)

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
    private enum class SelectionMode { REPLACE, ADD, REMOVE }

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
                val includeIntersect = windowStartX > windowEndX
                val mode = selectionMode()
                if (mode == SelectionMode.REPLACE) {
                    faceStore.clearSelection()
                    lineStore.clearSelection()
                }
                val faces = selectFacesInWindow(rect, includeIntersect, mode)
                val edges = selectEdgesInWindow(rect, includeIntersect, mode)
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
        val dashed = windowStartX > windowEndX
        return WindowRect(minX, bottom, maxX - minX, top - bottom, dashed)
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

    private fun selectFacesInWindow(rect: WindowRectTopLeft, includeIntersect: Boolean, mode: SelectionMode): Int {
        var count = 0
        faceStore.getTriangles().forEach { tri ->
            val a = projectToScreen(tri.a)
            val b = projectToScreen(tri.b)
            val c = projectToScreen(tri.c)
            val matches = if (!includeIntersect) {
                pointInRect(a, rect) && pointInRect(b, rect) && pointInRect(c, rect)
            } else {
                faceIntersectsRect(a, b, c, rect)
            }
            if (matches) {
                when (mode) {
                    SelectionMode.ADD, SelectionMode.REPLACE -> {
                        if (faceStore.addSelection(tri)) {
                            count++
                        }
                    }
                    SelectionMode.REMOVE -> {
                        if (faceStore.isSelected(tri)) {
                            faceStore.removeSelection(tri)
                            count++
                        }
                    }
                }
            }
        }
        return count
    }

    private fun selectEdgesInWindow(rect: WindowRectTopLeft, includeIntersect: Boolean, mode: SelectionMode): Int {
        var count = 0
        lineStore.getSegments().forEach { segment ->
            val a = projectToScreen(segment.start)
            val b = projectToScreen(segment.end)
            val matches = if (!includeIntersect) {
                pointInRect(a, rect) && pointInRect(b, rect)
            } else {
                segmentIntersectsRect(a, b, rect)
            }
            if (matches) {
                when (mode) {
                    SelectionMode.ADD, SelectionMode.REPLACE -> {
                        if (lineStore.addSelection(segment)) {
                            count++
                        }
                    }
                    SelectionMode.REMOVE -> {
                        if (lineStore.isSelected(segment)) {
                            lineStore.removeSelection(segment)
                            count++
                        }
                    }
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

    private fun selectionMode(): SelectionMode {
        val shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
        val ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)
        return when {
            ctrl -> SelectionMode.REMOVE
            shift -> SelectionMode.ADD
            else -> SelectionMode.REPLACE
        }
    }

    private fun faceIntersectsRect(a: Vector3, b: Vector3, c: Vector3, rect: WindowRectTopLeft): Boolean {
        if (pointInRect(a, rect) || pointInRect(b, rect) || pointInRect(c, rect)) {
            return true
        }
        val rectPoints = rectCorners(rect)
        val a2 = Vector2(a.x, a.y)
        val b2 = Vector2(b.x, b.y)
        val c2 = Vector2(c.x, c.y)
        rectPoints.forEach { p ->
            if (pointInTriangle(p, a2, b2, c2)) {
                return true
            }
        }
        return segmentIntersectsRect(a, b, rect) ||
            segmentIntersectsRect(b, c, rect) ||
            segmentIntersectsRect(c, a, rect)
    }

    private fun segmentIntersectsRect(a: Vector3, b: Vector3, rect: WindowRectTopLeft): Boolean {
        if (pointInRect(a, rect) || pointInRect(b, rect)) {
            return true
        }
        val corners = rectCorners(rect)
        val r0 = corners[0]
        val r1 = corners[1]
        val r2 = corners[2]
        val r3 = corners[3]
        val a2 = Vector2(a.x, a.y)
        val b2 = Vector2(b.x, b.y)
        return segmentsIntersect(a2, b2, r0, r1) ||
            segmentsIntersect(a2, b2, r1, r2) ||
            segmentsIntersect(a2, b2, r2, r3) ||
            segmentsIntersect(a2, b2, r3, r0)
    }

    private fun rectCorners(rect: WindowRectTopLeft): List<Vector2> {
        val minX = rect.minX
        val maxX = rect.maxX
        val minY = rect.minY
        val maxY = rect.maxY
        return listOf(
            Vector2(minX, minY),
            Vector2(maxX, minY),
            Vector2(maxX, maxY),
            Vector2(minX, maxY)
        )
    }

    private fun pointInTriangle(p: Vector2, a: Vector2, b: Vector2, c: Vector2): Boolean {
        val v0 = Vector2(c).sub(a)
        val v1 = Vector2(b).sub(a)
        val v2 = Vector2(p).sub(a)
        val dot00 = v0.dot(v0)
        val dot01 = v0.dot(v1)
        val dot02 = v0.dot(v2)
        val dot11 = v1.dot(v1)
        val dot12 = v1.dot(v2)
        val invDen = 1f / (dot00 * dot11 - dot01 * dot01)
        val u = (dot11 * dot02 - dot01 * dot12) * invDen
        val v = (dot00 * dot12 - dot01 * dot02) * invDen
        return u >= -1e-4f && v >= -1e-4f && u + v <= 1f + 1e-4f
    }

    private fun segmentsIntersect(p1: Vector2, p2: Vector2, q1: Vector2, q2: Vector2): Boolean {
        val o1 = orientation(p1, p2, q1)
        val o2 = orientation(p1, p2, q2)
        val o3 = orientation(q1, q2, p1)
        val o4 = orientation(q1, q2, p2)
        if (o1 != o2 && o3 != o4) {
            return true
        }
        return o1 == 0 && onSegment(p1, q1, p2) ||
            o2 == 0 && onSegment(p1, q2, p2) ||
            o3 == 0 && onSegment(q1, p1, q2) ||
            o4 == 0 && onSegment(q1, p2, q2)
    }

    private fun orientation(a: Vector2, b: Vector2, c: Vector2): Int {
        val value = (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y)
        val eps = 1e-6f
        return when {
            kotlin.math.abs(value) < eps -> 0
            value > 0f -> 1
            else -> 2
        }
    }

    private fun onSegment(a: Vector2, b: Vector2, c: Vector2): Boolean {
        return b.x <= maxOf(a.x, c.x) + 1e-6f &&
            b.x + 1e-6f >= minOf(a.x, c.x) &&
            b.y <= maxOf(a.y, c.y) + 1e-6f &&
            b.y + 1e-6f >= minOf(a.y, c.y)
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

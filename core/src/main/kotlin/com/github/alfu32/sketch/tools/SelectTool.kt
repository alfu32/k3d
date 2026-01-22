package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.collision.Ray
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class SelectTool(
    private val scene: GroupScene,
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
        scene.activeGroup().lineStore.clearSelection()
        scene.activeGroup().faceStore.clearSelection()
        scene.clearGroupSelection()
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
        val faceHit = pickFaceWorld(ray)
        val edgeHit = pickEdgeWorld(ray, Gdx.input.x, Gdx.input.y)
        val groupHit = pickGroupWorld(ray, Gdx.input.x, Gdx.input.y)
        val pickedFace = faceHit != null
        val pickedEdge = edgeHit != null
        val pickedGroup = groupHit != null
        if (!pickedFace && !pickedEdge && !pickedGroup) {
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
        if (clickType == 2 && pickedGroup) {
            val targetGroup = groupHit!!.group
            if (scene.enterGroup(targetGroup)) {
                scene.clearAllSelections()
                status.message = "Editing object: ${targetGroup.name}"
                return true
            }
        }
        if (clickType >= 3) {
            clickCount = 0
            if (!pickedFace && !pickedEdge) {
                return true
            }
            val pickFace = pickedFace && (!pickedEdge || faceHit!!.t <= edgeHit!!.t)
            if (pickFace) {
                val group = scene.activeGroup().faceStore.collectConnected(faceHit!!.triangle)
                val allSelected = group.all { scene.activeGroup().faceStore.isSelected(it) }
                if (allSelected) {
                    group.forEach { scene.activeGroup().faceStore.removeSelection(it) }
                    status.message = "Connected faces deselected."
                } else {
                    group.forEach { scene.activeGroup().faceStore.addSelection(it) }
                    status.message = "Connected faces selected."
                }
            } else {
                val group = scene.activeGroup().lineStore.collectConnected(edgeHit!!.segment)
                val allSelected = group.all { scene.activeGroup().lineStore.isSelected(it) }
                if (allSelected) {
                    group.forEach { scene.activeGroup().lineStore.removeSelection(it) }
                    status.message = "Connected edges deselected."
                } else {
                    group.forEach { scene.activeGroup().lineStore.addSelection(it) }
                    status.message = "Connected edges selected."
                }
            }
            return true
        }
        if (clickType == 2 && pickedFace) {
            val group = scene.activeGroup().faceStore.collectCoplanarConnected(faceHit!!.triangle)
            val allSelected = group.all { scene.activeGroup().faceStore.isSelected(it) }
            if (allSelected) {
                group.forEach { scene.activeGroup().faceStore.removeSelection(it) }
                status.message = "Coplanar faces deselected."
            } else {
                group.forEach { scene.activeGroup().faceStore.addSelection(it) }
                status.message = "Coplanar faces selected."
            }
            return true
        }
        if (pickedGroup && (!pickedFace || groupHit!!.t <= faceHit!!.t) && (!pickedEdge || groupHit!!.t <= edgeHit!!.t)) {
            scene.toggleGroupSelection(groupHit!!.group)
            status.message = "Group toggled."
            return true
        }
        if (pickedFace && pickedEdge) {
            if (faceHit!!.t <= edgeHit!!.t) {
                scene.activeGroup().faceStore.toggleSelection(faceHit.triangle)
                status.message = "Face toggled."
            } else {
                scene.activeGroup().lineStore.toggleSelection(edgeHit.segment)
                status.message = "Edge toggled."
            }
            return true
        }
        if (pickedFace) {
            scene.activeGroup().faceStore.toggleSelection(faceHit!!.triangle)
            status.message = "Face toggled."
            return true
        }
        if (pickedEdge) {
            scene.activeGroup().lineStore.toggleSelection(edgeHit!!.segment)
            status.message = "Edge toggled."
            return true
        }
        return false
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
                    scene.activeGroup().faceStore.clearSelection()
                    scene.activeGroup().lineStore.clearSelection()
                    scene.clearGroupSelection()
                }
                val faces = selectFacesInWindow(rect, includeIntersect, mode)
                val edges = selectEdgesInWindow(rect, includeIntersect, mode)
                val groups = selectGroupsInWindow(rect, includeIntersect, mode)
                status.message = "Window select | edges $edges faces $faces groups $groups"
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

    private data class FaceHitWorld(
        val triangle: DraftFaceStore.Triangle,
        val point: Vector3,
        val t: Float
    )

    private data class EdgeHitWorld(
        val segment: DraftLineStore.Segment,
        val point: Vector3,
        val t: Float
    )

    private data class GroupHitWorld(
        val group: GroupScene.GroupNode,
        val point: Vector3,
        val t: Float
    )

    private fun finalizeVolumeSelection(status: StatusModel) {
        val bounds = volumeBounds() ?: return
        val group = scene.activeGroup()
        val localBounds = volumeBoundsLocal(group, bounds.first, bounds.second)
        val faceCount = group.faceStore.selectInVolume(localBounds.first, localBounds.second, replace = true)
        val edgeCount = group.lineStore.selectInVolume(localBounds.first, localBounds.second, replace = true)
        val groupCount = selectGroupsInVolume(bounds.first, bounds.second)
        status.message = "Volume select | edges $edgeCount faces $faceCount groups $groupCount"
    }

    private fun pickFaceWorld(ray: Ray): FaceHitWorld? {
        val group = scene.activeGroup()
        val localRay = Ray(group.toLocal(ray.origin), group.vectorToLocal(ray.direction).nor())
        val hit = group.faceStore.pickTriangle(localRay) ?: return null
        val worldPoint = group.toWorld(hit.point)
        val t = Vector3(worldPoint).sub(ray.origin).dot(ray.direction)
        return FaceHitWorld(hit.triangle, worldPoint, t)
    }

    private fun pickEdgeWorld(ray: Ray, screenX: Int, screenY: Int, maxPixels: Float = 12f): EdgeHitWorld? {
        val group = scene.activeGroup()
        var best: EdgeHitWorld? = null
        group.lineStore.getSegments().forEach { segment ->
            val a = group.toWorld(segment.start)
            val b = group.toWorld(segment.end)
            val hit = closestRaySegment(ray.origin, ray.direction, a, b) ?: return@forEach
            val screenDist = screenDistance(hit.point, screenX, screenY)
            if (screenDist <= maxPixels) {
                if (best == null || hit.t < best!!.t) {
                    best = EdgeHitWorld(segment, hit.point, hit.t)
                }
            }
        }
        return best
    }

    private fun pickGroupWorld(ray: Ray, screenX: Int, screenY: Int, maxPixels: Float = 12f): GroupHitWorld? {
        var best: GroupHitWorld? = null
        val candidates = scene.groupsInActiveContext()
        candidates.forEach { group ->
            val localRay = Ray(group.toLocal(ray.origin), group.vectorToLocal(ray.direction).nor())
            var bestForGroup: GroupHitWorld? = null
            val faceHit = group.faceStore.pickTriangle(localRay)
            if (faceHit != null) {
                val worldPoint = group.toWorld(faceHit.point)
                val t = Vector3(worldPoint).sub(ray.origin).dot(ray.direction)
                bestForGroup = GroupHitWorld(group, worldPoint, t)
            }
            if (bestForGroup == null && group.faceStore.getTriangles().isEmpty()) {
                group.lineStore.getSegments().forEach { segment ->
                    val a = group.toWorld(segment.start)
                    val b = group.toWorld(segment.end)
                    val hit = closestRaySegment(ray.origin, ray.direction, a, b) ?: return@forEach
                    val screenDist = screenDistance(hit.point, screenX, screenY)
                    if (screenDist <= maxPixels) {
                        if (bestForGroup == null || hit.t < bestForGroup!!.t) {
                            bestForGroup = GroupHitWorld(group, hit.point, hit.t)
                        }
                    }
                }
            }
            if (bestForGroup != null) {
                if (best == null || bestForGroup!!.t < best!!.t) {
                    best = bestForGroup
                }
            }
        }
        return best
    }

    private fun selectGroupsInWindow(rect: WindowRectTopLeft, includeIntersect: Boolean, mode: SelectionMode): Int {
        var count = 0
        val groups = scene.groupsInActiveContext()
        groups.forEach { group ->
            val bounds = group.worldBounds() ?: return@forEach
            val screenBounds = boundsToScreen(bounds) ?: return@forEach
            val matches = if (!includeIntersect) {
                screenBounds.minX >= rect.minX &&
                    screenBounds.maxX <= rect.maxX &&
                    screenBounds.minY >= rect.minY &&
                    screenBounds.maxY <= rect.maxY
            } else {
                rect.minX <= screenBounds.maxX &&
                    rect.maxX >= screenBounds.minX &&
                    rect.minY <= screenBounds.maxY &&
                    rect.maxY >= screenBounds.minY
            }
            if (matches) {
                when (mode) {
                    SelectionMode.ADD, SelectionMode.REPLACE -> {
                        if (scene.addGroupSelection(group)) {
                            count++
                        }
                    }
                    SelectionMode.REMOVE -> {
                        if (scene.selectedGroups().contains(group)) {
                            scene.removeGroupSelection(group)
                            count++
                        }
                    }
                }
            }
        }
        return count
    }

    private fun selectGroupsInVolume(min: Vector3, max: Vector3): Int {
        scene.clearGroupSelection()
        var count = 0
        scene.groupsInActiveContext().forEach { group ->
            val bounds = group.worldBounds() ?: return@forEach
            if (aabbIntersects(bounds.min, bounds.max, min, max)) {
                if (scene.addGroupSelection(group)) {
                    count++
                }
            }
        }
        return count
    }

    private data class ScreenBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

    private fun boundsToScreen(bounds: com.badlogic.gdx.math.collision.BoundingBox): ScreenBounds? {
        val corners = arrayOf(
            Vector3(bounds.min.x, bounds.min.y, bounds.min.z),
            Vector3(bounds.min.x, bounds.min.y, bounds.max.z),
            Vector3(bounds.min.x, bounds.max.y, bounds.min.z),
            Vector3(bounds.min.x, bounds.max.y, bounds.max.z),
            Vector3(bounds.max.x, bounds.min.y, bounds.min.z),
            Vector3(bounds.max.x, bounds.min.y, bounds.max.z),
            Vector3(bounds.max.x, bounds.max.y, bounds.min.z),
            Vector3(bounds.max.x, bounds.max.y, bounds.max.z)
        )
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        corners.forEach { corner ->
            val projected = camera.project(Vector3(corner))
            val x = projected.x
            val y = Gdx.graphics.height - projected.y
            minX = kotlin.math.min(minX, x)
            maxX = kotlin.math.max(maxX, x)
            minY = kotlin.math.min(minY, y)
            maxY = kotlin.math.max(maxY, y)
        }
        if (minX == Float.POSITIVE_INFINITY) {
            return null
        }
        return ScreenBounds(minX, maxX, minY, maxY)
    }

    private fun aabbIntersects(aMin: Vector3, aMax: Vector3, bMin: Vector3, bMax: Vector3): Boolean {
        return aMin.x <= bMax.x && aMax.x >= bMin.x &&
            aMin.y <= bMax.y && aMax.y >= bMin.y &&
            aMin.z <= bMax.z && aMax.z >= bMin.z
    }

    private data class RaySegmentHit(val point: Vector3, val t: Float)

    private fun closestRaySegment(rayOrigin: Vector3, rayDir: Vector3, a: Vector3, b: Vector3): RaySegmentHit? {
        val dir = Vector3(rayDir).nor()
        val e = Vector3(b).sub(a)
        val r = Vector3(rayOrigin).sub(a)
        val aDot = dir.dot(dir)
        val eDot = e.dot(e)
        val f = dir.dot(e)
        val c = dir.dot(r)
        val g = e.dot(r)
        val denom = aDot * eDot - f * f
        var t: Float
        var s: Float
        if (kotlin.math.abs(denom) > 1e-6f) {
            t = (f * g - eDot * c) / denom
            s = (aDot * g - f * c) / denom
            s = s.coerceIn(0f, 1f)
            t = (-c + f * s) / aDot
        } else {
            s = (g / eDot).coerceIn(0f, 1f)
            t = (-c + f * s) / aDot
        }
        if (t <= 0f) {
            t = 0f
            s = (g / eDot).coerceIn(0f, 1f)
        }
        val pointOnRay = Vector3(rayOrigin).mulAdd(dir, t)
        val pointOnSeg = Vector3(a).mulAdd(e, s)
        return RaySegmentHit(pointOnSeg, t)
    }

    private fun screenDistance(world: Vector3, screenX: Int, screenY: Int): Float {
        val projected = camera.project(Vector3(world))
        val dx = projected.x - screenX
        val dy = (Gdx.graphics.height - projected.y) - screenY
        return kotlin.math.sqrt(dx * dx + dy * dy)
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
        scene.activeGroup().faceStore.getTriangles().forEach { tri ->
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
                        if (scene.activeGroup().faceStore.addSelection(tri)) {
                            count++
                        }
                    }
                    SelectionMode.REMOVE -> {
                        if (scene.activeGroup().faceStore.isSelected(tri)) {
                            scene.activeGroup().faceStore.removeSelection(tri)
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
        scene.activeGroup().lineStore.getSegments().forEach { segment ->
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
                        if (scene.activeGroup().lineStore.addSelection(segment)) {
                            count++
                        }
                    }
                    SelectionMode.REMOVE -> {
                        if (scene.activeGroup().lineStore.isSelected(segment)) {
                            scene.activeGroup().lineStore.removeSelection(segment)
                            count++
                        }
                    }
                }
            }
        }
        return count
    }

    private fun projectToScreen(point: Vector3): Vector3 {
        val world = scene.activeGroup().toWorld(point)
        val projected = camera.project(world)
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

    private fun volumeBoundsLocal(
        group: GroupScene.GroupNode,
        worldMin: Vector3,
        worldMax: Vector3
    ): Pair<Vector3, Vector3> {
        val corners = arrayOf(
            Vector3(worldMin.x, worldMin.y, worldMin.z),
            Vector3(worldMin.x, worldMin.y, worldMax.z),
            Vector3(worldMin.x, worldMax.y, worldMin.z),
            Vector3(worldMin.x, worldMax.y, worldMax.z),
            Vector3(worldMax.x, worldMin.y, worldMin.z),
            Vector3(worldMax.x, worldMin.y, worldMax.z),
            Vector3(worldMax.x, worldMax.y, worldMin.z),
            Vector3(worldMax.x, worldMax.y, worldMax.z)
        )
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        corners.forEach { corner ->
            val local = group.toLocal(corner)
            minX = kotlin.math.min(minX, local.x)
            minY = kotlin.math.min(minY, local.y)
            minZ = kotlin.math.min(minZ, local.z)
            maxX = kotlin.math.max(maxX, local.x)
            maxY = kotlin.math.max(maxY, local.y)
            maxZ = kotlin.math.max(maxZ, local.z)
        }
        return Vector3(minX, minY, minZ) to Vector3(maxX, maxY, maxZ)
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

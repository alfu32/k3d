package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.github.alfu32.sketch.model.ArchitectureStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ArchitectureWallTool(
    private val scene: GroupScene,
    private val settings: ArchitectureSettings,
    private val onSelectTool: () -> Unit,
    private val ensureArchitectureGroup: (() -> GroupScene.GroupNode?)? = null
) : Tool {
    override val id: ToolId = ToolId.ARCH_WALL
    override val message: String = "Pick first wall point."

    private var anchorWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
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
        val group = resolveArchitectureGroup(scene, status, ensureArchitectureGroup) ?: return true
        if (anchorWorld == null) {
            anchorWorld = Vector3(world)
            status.message = "Pick wall end point."
            return true
        }
        val startWorld = anchorWorld ?: return true
        val created = scene.addArchitectureWall(
            group = group,
            start = group.toLocal(startWorld),
            end = group.toLocal(world),
            thickness = settings.wallThickness,
            height = settings.wallHeight,
            inclinationDeg = settings.wallInclinationDeg,
            exteriorColor = settings.wallExteriorColor,
            interiorColor = settings.wallInteriorColor
        )
        if (created) {
            anchorWorld = Vector3(world)
            status.message = "Wall added. Pick next end point, Enter to finish."
        } else {
            status.message = "Wall not created."
        }
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                clear()
                status.message = message
                true
            }
            Input.Keys.ESCAPE -> {
                clear()
                status.message = "Canceled."
                onSelectTool()
                true
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val start = anchorWorld ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(startWorld = Vector3(start), endWorld = Vector3(hover), lineColor = ToolFeedbackColors.PRIMARY)
    }

    override fun render(renderer: ShapeRenderer) {
        val start = anchorWorld ?: return
        if (!hasHover) {
            return
        }
        renderer.color = ToolFeedbackColors.PRIMARY
        renderer.line(start, hover)
    }

    private fun clear() {
        anchorWorld = null
        hasHover = false
    }
}

class ArchitectureSlabTool(
    private val scene: GroupScene,
    private val settings: ArchitectureSettings,
    private val onSelectTool: () -> Unit,
    private val ensureArchitectureGroup: (() -> GroupScene.GroupNode?)? = null
) : Tool {
    override val id: ToolId = ToolId.ARCH_SLAB
    override val message: String = "Pick first slab corner."

    private var firstCornerWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
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
        val group = resolveArchitectureGroup(scene, status, ensureArchitectureGroup) ?: return true
        if (firstCornerWorld == null) {
            firstCornerWorld = Vector3(world)
            status.message = "Pick opposite slab corner."
            return true
        }
        val first = firstCornerWorld ?: return true
        val created = scene.addArchitectureSlab(
            group = group,
            minCorner = group.toLocal(first),
            maxCorner = group.toLocal(world),
            thickness = settings.slabThickness,
            topColor = settings.slabTopColor,
            bottomColor = settings.slabBottomColor,
            sideColor = settings.slabSideColor
        )
        if (created) {
            status.message = "Slab created."
        } else {
            status.message = "Slab not created."
        }
        clear()
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                clear()
                status.message = message
                true
            }
            Input.Keys.ESCAPE -> {
                clear()
                status.message = "Canceled."
                onSelectTool()
                true
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val start = firstCornerWorld ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(startWorld = Vector3(start), endWorld = Vector3(hover), lineColor = ToolFeedbackColors.SECONDARY)
    }

    override fun render(renderer: ShapeRenderer) {
        val first = firstCornerWorld ?: return
        if (!hasHover) {
            return
        }
        val corners = horizontalRectCorners(first, hover)
        drawLoop(renderer, corners, ToolFeedbackColors.SECONDARY)
    }

    private fun clear() {
        firstCornerWorld = null
        hasHover = false
    }
}

class ArchitectureAddHoleTool(
    private val scene: GroupScene,
    private val onSelectTool: () -> Unit,
    private val ensureArchitectureGroup: (() -> GroupScene.GroupNode?)? = null
) : Tool {
    override val id: ToolId = ToolId.ARCH_ADD_HOLE
    override val message: String = "Pick first hole corner."

    private var firstCornerWorld: Vector3? = null
    private var firstNormalWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
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
        val group = resolveHoleTargetGroup(status) ?: return true
        if (firstCornerWorld == null) {
            firstCornerWorld = Vector3(world)
            firstNormalWorld = normal?.let { Vector3(it).nor() } ?: Vector3(0f, 1f, 0f)
            status.message = "Pick opposite hole corner."
            return true
        }
        val first = firstCornerWorld ?: return true
        val localA = group.toLocal(first)
        val localB = group.toLocal(world)
        val selected = scene.selectedArchitectureElement(group)
        val created = when (selected?.kind) {
            ArchitectureStore.ElementKind.WALL -> scene.addArchitectureHoleToWall(group, selected.id, localA, localB)
            ArchitectureStore.ElementKind.SLAB -> scene.addArchitectureHoleToSlab(group, selected.id, localA, localB)
            else -> {
                scene.addArchitectureHoleToNearestWall(group, localA, localB) ||
                    scene.addArchitectureHoleToNearestSlab(group, localA, localB)
            }
        }
        if (created) {
            status.message = "Hole added."
        } else {
            status.message = "No compatible wall/slab found for hole."
        }
        clear()
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                clear()
                status.message = message
                true
            }
            Input.Keys.ESCAPE -> {
                clear()
                status.message = "Canceled."
                onSelectTool()
                true
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val start = firstCornerWorld ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(startWorld = Vector3(start), endWorld = Vector3(hover), lineColor = ToolFeedbackColors.PRIMARY)
    }

    override fun render(renderer: ShapeRenderer) {
        val first = firstCornerWorld ?: return
        if (!hasHover) {
            return
        }
        val basis = chooseRectangleBasis(first, hover, firstNormalWorld ?: Vector3(0f, 1f, 0f))
        val corners = rectangleCorners(first, hover, basis)
        drawLoop(renderer, corners, ToolFeedbackColors.PRIMARY)
    }

    private fun clear() {
        firstCornerWorld = null
        firstNormalWorld = null
        hasHover = false
    }

    private fun resolveHoleTargetGroup(status: StatusModel): GroupScene.GroupNode? {
        if (!scene.hasArchitectureElements()) {
            status.message = "No architecture elements available for hole creation."
            return null
        }
        return ensureArchitectureGroup?.invoke() ?: scene.activeGroup()
    }
}

class ArchitectureStairTool(
    private val scene: GroupScene,
    private val cameraProvider: () -> Camera,
    private val settings: ArchitectureSettings,
    private val onSelectTool: () -> Unit,
    private val ensureArchitectureGroup: (() -> GroupScene.GroupNode?)? = null
) : Tool {
    private data class PolylinePath(val points: List<Vector3>, val closed: Boolean)
    private data class PolylineComponent(val path: PolylinePath, val segments: List<DraftLineStore.Segment>)

    private data class EdgeHitWorld(val segment: DraftLineStore.Segment, val point: Vector3, val t: Float)
    private data class GroupPolylineHitWorld(val path: PolylinePath, val t: Float)

    private data class Key3(val x: Int, val y: Int, val z: Int)

    override val id: ToolId = ToolId.ARCH_STAIR
    override val message: String = "Pick closed stair contour polyline."

    private var contourPath: PolylinePath? = null
    private var treadPath: PolylinePath? = null
    private val hoverPoint = Vector3()
    private var hasHoverPoint = false

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
        status.message = "Canceled."
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hoverPoint.set(world)
            hasHoverPoint = true
        } else {
            hasHoverPoint = false
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        val sourceGroup = scene.activeGroup()
        val screenX = Gdx.input.x
        val screenY = Gdx.input.y
        val ray = cameraProvider().getPickRay(screenX.toFloat(), screenY.toFloat())

        // Prefer single-click source-group polyline picking (one click = one full polyline).
        val groupPolylinePath = if (contourPath == null) {
            pickGroupPolylineWorld(ray, screenX, screenY, requireClosed = true)?.path
        } else {
            pickGroupPolylineWorld(ray, screenX, screenY, requireClosed = false)?.path
        }

        // Fallback to legacy edge-connected pick inside the current active group.
        val edgeConnectedPath = run {
            val edgeHit = pickEdgeWorld(sourceGroup, ray, screenX, screenY) ?: return@run null
            val connected = sourceGroup.lineStore.collectConnected(edgeHit.segment)
            val localPath = orderedPolyline(connected) ?: return@run null
            PolylinePath(points = localPath.points.map { sourceGroup.toWorld(it) }, closed = localPath.closed)
        }

        val path = groupPolylinePath ?: edgeConnectedPath
        if (path == null || path.points.size < 2) {
            status.message = "Pick a polyline group (preferred) or an existing polyline edge."
            return true
        }

        if (contourPath == null) {
            if (!path.closed || path.points.size < 3) {
                status.message = "Contour must be a closed polyline."
                return true
            }
            contourPath = path
            status.message = "Pick stair tread line polyline/group."
            return true
        }

        if (path.closed) {
            status.message = "Tread line must be open."
            return true
        }
        treadPath = path

        val group = resolveArchitectureGroup(scene, status, ensureArchitectureGroup) ?: return true
        val contour = contourPath ?: return true
        val tread = treadPath ?: return true
        val contourLocal = contour.points.map { group.toLocal(it) }
        val treadLocal = tread.points.map { group.toLocal(it) }
        if (treadLocal.size < 2) {
            status.message = "Tread line requires at least 2 points."
            clear()
            return true
        }
        val walkStart = treadLocal.first()
        val walkEnd = treadLocal.last()
        if (walkStart.dst2(walkEnd) <= 1e-6f) {
            status.message = "Tread line is too short."
            clear()
            return true
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        contourLocal.forEach { point ->
            minX = kotlin.math.min(minX, point.x)
            minY = kotlin.math.min(minY, point.y)
            minZ = kotlin.math.min(minZ, point.z)
            maxX = kotlin.math.max(maxX, point.x)
            maxY = kotlin.math.max(maxY, point.y)
            maxZ = kotlin.math.max(maxZ, point.z)
        }

        val created = scene.addArchitectureStair(
            group = group,
            minCorner = Vector3(minX, minY, minZ),
            maxCorner = Vector3(maxX, maxY, maxZ),
            contourPoints = contourLocal,
            walkingPathPoints = treadLocal,
            walkingStart = walkStart,
            walkingEnd = walkEnd,
            height = settings.stairHeight,
            stepCount = settings.stairStepCount,
            supportThickness = settings.stairSupportThickness,
            railLeftEnabled = settings.stairRailLeftEnabled,
            railRightEnabled = settings.stairRailRightEnabled,
            treadColor = settings.stairTreadColor,
            supportColor = settings.stairSupportColor
        )
        if (created) {
            status.message = "Stair created."
        } else {
            status.message = "Stair not created."
        }
        clear()
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                clear()
                status.message = message
                true
            }
            Input.Keys.ESCAPE -> {
                clear()
                status.message = "Canceled."
                onSelectTool()
                true
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        if (!hasHoverPoint) {
            return null
        }
        val start = contourPath?.points?.firstOrNull() ?: return null
        return ToolMeasurement(startWorld = Vector3(start), endWorld = Vector3(hoverPoint), lineColor = ToolFeedbackColors.SECONDARY)
    }

    override fun render(renderer: ShapeRenderer) {
        contourPath?.let { contour ->
            drawLoop(renderer, contour.points, ToolFeedbackColors.SECONDARY)
        }
        treadPath?.let { tread ->
            renderer.color = ToolFeedbackColors.PRIMARY
            for (i in 0 until tread.points.lastIndex) {
                renderer.line(tread.points[i], tread.points[i + 1])
            }
        }
    }

    private fun clear() {
        contourPath = null
        treadPath = null
        hasHoverPoint = false
    }

    private fun orderedPolyline(segments: List<DraftLineStore.Segment>, epsilon: Float = 1e-3f): PolylinePath? {
        if (segments.isEmpty()) {
            return null
        }

        fun keyOf(point: Vector3): Key3 {
            val scale = 1f / epsilon
            return Key3(
                (point.x * scale).roundToInt(),
                (point.y * scale).roundToInt(),
                (point.z * scale).roundToInt()
            )
        }

        val keyToPoint = linkedMapOf<Key3, Vector3>()
        val endpoints = segments.map { segment ->
            val a = keyOf(segment.start)
            val b = keyOf(segment.end)
            keyToPoint.putIfAbsent(a, Vector3(segment.start))
            keyToPoint.putIfAbsent(b, Vector3(segment.end))
            a to b
        }

        val adjacency = mutableMapOf<Key3, MutableList<Int>>()
        endpoints.forEachIndexed { index, (a, b) ->
            adjacency.getOrPut(a) { mutableListOf() }.add(index)
            adjacency.getOrPut(b) { mutableListOf() }.add(index)
        }

        val degreeOne = adjacency.filterValues { it.size == 1 }.keys.toList()
        val closed = degreeOne.isEmpty() && adjacency.values.all { it.size == 2 }
        if (!closed && degreeOne.size != 2) {
            return null
        }

        val startKey = if (closed) adjacency.keys.first() else degreeOne.first()
        val visited = mutableSetOf<Int>()
        val ordered = mutableListOf<Vector3>()
        var current = startKey
        ordered.add(Vector3(keyToPoint[current] ?: return null))

        while (visited.size < segments.size) {
            val nextSegment = adjacency[current]?.firstOrNull { it !in visited } ?: break
            visited.add(nextSegment)
            val (a, b) = endpoints[nextSegment]
            current = if (a == current) b else a
            ordered.add(Vector3(keyToPoint[current] ?: return null))
            if (closed && current == startKey) {
                break
            }
        }

        if (visited.size != segments.size) {
            return null
        }

        if (closed) {
            if (ordered.size < 4 || ordered.first().dst2(ordered.last()) > epsilon * epsilon) {
                return null
            }
            return PolylinePath(points = ordered.dropLast(1), closed = true)
        }
        return PolylinePath(points = ordered, closed = false)
    }

    private fun pickGroupPolylineWorld(
        ray: Ray,
        screenX: Int,
        screenY: Int,
        requireClosed: Boolean,
        maxPixels: Float = 12f
    ): GroupPolylineHitWorld? {
        var best: GroupPolylineHitWorld? = null
        candidateGroupsForStairPick().forEach { candidate ->
            val polylines = polylineComponents(candidate)
            if (polylines.isEmpty()) {
                return@forEach
            }
            polylines.forEach { component ->
                if (component.path.closed != requireClosed) {
                    return@forEach
                }
                var bestT: Float? = null
                component.segments.forEach { segment ->
                    val a = candidate.toWorld(segment.start)
                    val b = candidate.toWorld(segment.end)
                    val hit = closestRaySegment(ray.origin, ray.direction, a, b) ?: return@forEach
                    val screenDist = screenDistance(hit.point, screenX, screenY)
                    if (screenDist <= maxPixels) {
                        if (bestT == null || hit.t < bestT!!) {
                            bestT = hit.t
                        }
                    }
                }
                val t = bestT ?: return@forEach
                val worldPath = PolylinePath(
                    points = component.path.points.map { candidate.toWorld(it) },
                    closed = component.path.closed
                )
                if (best == null || t < best!!.t) {
                    best = GroupPolylineHitWorld(worldPath, t)
                }
            }
        }
        return best
    }

    private fun candidateGroupsForStairPick(): List<GroupScene.GroupNode> {
        val activeParent = scene.activeGroup()
        val inContext = scene.groupsInActiveContext()
        if (inContext.isEmpty()) {
            return emptyList()
        }
        val selected = scene.selectedGroups()
            .filter { it.parent == activeParent && inContext.contains(it) }
        if (selected.isEmpty()) {
            return inContext
        }
        val remainder = inContext.filterNot { selected.contains(it) }
        return selected + remainder
    }

    private fun polylineComponents(group: GroupScene.GroupNode, epsilon: Float = 1e-3f): List<PolylineComponent> {
        val segments = group.lineStore.getSegments()
        if (segments.isEmpty()) {
            return emptyList()
        }
        fun keyOf(point: Vector3): Key3 {
            val scale = 1f / epsilon
            return Key3(
                (point.x * scale).roundToInt(),
                (point.y * scale).roundToInt(),
                (point.z * scale).roundToInt()
            )
        }

        val segmentEndpointKeys = segments.map { keyOf(it.start) to keyOf(it.end) }
        val endpointToSegments = mutableMapOf<Key3, MutableList<Int>>()
        segmentEndpointKeys.forEachIndexed { index, (a, b) ->
            endpointToSegments.getOrPut(a) { mutableListOf() }.add(index)
            endpointToSegments.getOrPut(b) { mutableListOf() }.add(index)
        }

        val visited = BooleanArray(segments.size)
        val components = mutableListOf<PolylineComponent>()
        val queue = ArrayDeque<Int>()
        for (startIndex in segments.indices) {
            if (visited[startIndex]) {
                continue
            }
            val componentIndices = mutableListOf<Int>()
            queue.clear()
            queue.addLast(startIndex)
            visited[startIndex] = true
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                componentIndices.add(current)
                val (a, b) = segmentEndpointKeys[current]
                (endpointToSegments[a] ?: emptyList()).forEach { neighbor ->
                    if (!visited[neighbor]) {
                        visited[neighbor] = true
                        queue.addLast(neighbor)
                    }
                }
                (endpointToSegments[b] ?: emptyList()).forEach { neighbor ->
                    if (!visited[neighbor]) {
                        visited[neighbor] = true
                        queue.addLast(neighbor)
                    }
                }
            }
            val componentSegments = componentIndices.map { segments[it] }
            val ordered = orderedPolyline(componentSegments, epsilon) ?: continue
            components.add(PolylineComponent(path = ordered, segments = componentSegments))
        }
        return components
    }

    private fun pickEdgeWorld(
        group: GroupScene.GroupNode,
        ray: Ray,
        screenX: Int,
        screenY: Int,
        maxPixels: Float = 12f
    ): EdgeHitWorld? {
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
        val pointOnSeg = Vector3(a).mulAdd(e, s)
        return RaySegmentHit(pointOnSeg, t)
    }

    private fun screenDistance(world: Vector3, screenX: Int, screenY: Int): Float {
        val projected = cameraProvider().project(Vector3(world))
        val dx = projected.x - screenX
        val dy = (Gdx.graphics.height - projected.y) - screenY
        return sqrt(dx * dx + dy * dy)
    }
}

class ArchitectureWindowFrameTool(
    private val scene: GroupScene,
    private val settings: ArchitectureSettings,
    private val onSelectTool: () -> Unit,
    private val ensureArchitectureGroup: (() -> GroupScene.GroupNode?)? = null
) : Tool {
    override val id: ToolId = ToolId.ARCH_WINDOW_FRAME
    override val message: String = "Pick first window contour point. A/C/L switch arc modes. Enter finishes."

    private val pointsWorld = mutableListOf<Vector3>()
    private var planeOriginWorld: Vector3? = null
    private var planeNormalWorld: Vector3? = null
    private val hoverWorld = Vector3()
    private var hasHover = false
    private var arcMode = FrameArcMode.LINE
    private var arcCenterWorld: Vector3? = null
    private var arcPass1World: Vector3? = null
    private val closeDistance = 0.15f
    private val epsilon = 1e-4f
    private val arcMaxLength = 0.5f

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
        status.message = "Canceled."
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        val origin = planeOriginWorld
        val planeNormal = planeNormalWorld
        if (valid && world != null) {
            hoverWorld.set(
                if (origin != null && planeNormal != null) {
                    projectPointToPlane(world, origin, planeNormal)
                } else {
                    world
                }
            )
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        val group = resolveArchitectureGroup(scene, status, ensureArchitectureGroup) ?: return true
        if (pointsWorld.isEmpty()) {
            val start = Vector3(world)
            pointsWorld.add(start)
            planeOriginWorld = Vector3(start)
            planeNormalWorld = normal?.let { Vector3(it).nor() } ?: Vector3(0f, 1f, 0f)
            status.message = "Pick next window contour point. A/C/L switch arc modes. Enter finishes."
            return true
        }
        val origin = planeOriginWorld ?: Vector3(world)
        val planeNormal = planeNormalWorld ?: (normal?.let { Vector3(it).nor() } ?: Vector3(0f, 1f, 0f))
        val hit = projectPointToPlane(world, origin, planeNormal)
        if (arcMode == FrameArcMode.LINE && isClosing(hit) && pointsWorld.size >= 3) {
            return finalizeContour(status, group, closeContour = true)
        }
        when (arcMode) {
            FrameArcMode.CENTER -> {
                if (arcCenterWorld == null) {
                    arcCenterWorld = Vector3(hit)
                    status.message = "Pick window arc end point."
                } else {
                    appendArcPoints(arcPointsFromCenter(pointsWorld.last(), hit, arcCenterWorld!!))
                    arcCenterWorld = null
                    status.message = "Pick next window contour point. A/C/L switch arc modes. Enter finishes."
                }
                return true
            }
            FrameArcMode.THREE -> {
                if (arcPass1World == null) {
                    arcPass1World = Vector3(hit)
                    status.message = "Pick third point of the window arc."
                } else {
                    appendArcPoints(arcPointsThrough(pointsWorld.last(), arcPass1World!!, hit))
                    arcPass1World = null
                    status.message = "Pick next window contour point. A/C/L switch arc modes. Enter finishes."
                }
                return true
            }
            FrameArcMode.LINE -> {
                pointsWorld.add(Vector3(hit))
                return true
            }
        }
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.A -> {
                arcMode = FrameArcMode.THREE
                arcCenterWorld = null
                arcPass1World = null
                status.message = "Window tool | arc through 3 points."
                return true
            }
            Input.Keys.C -> {
                arcMode = FrameArcMode.CENTER
                arcCenterWorld = null
                arcPass1World = null
                status.message = "Window tool | arc from center."
                return true
            }
            Input.Keys.L -> {
                arcMode = FrameArcMode.LINE
                arcCenterWorld = null
                arcPass1World = null
                status.message = "Window tool | line mode."
                return true
            }
            Input.Keys.BACKSPACE -> {
                when {
                    arcMode == FrameArcMode.CENTER && arcCenterWorld != null -> arcCenterWorld = null
                    arcMode == FrameArcMode.THREE && arcPass1World != null -> arcPass1World = null
                    pointsWorld.isNotEmpty() -> {
                        pointsWorld.removeAt(pointsWorld.lastIndex)
                        if (pointsWorld.isEmpty()) {
                            clear()
                        }
                    }
                }
                status.message = if (pointsWorld.isEmpty()) message else "Pick next window contour point. A/C/L switch arc modes. Enter finishes."
                return true
            }
            Input.Keys.ENTER -> {
                val group = resolveArchitectureGroup(scene, status, ensureArchitectureGroup) ?: return true
                return finalizeContour(status, group, closeContour = true)
            }
            Input.Keys.ESCAPE -> {
                clear()
                status.message = "Canceled."
                onSelectTool()
                return true
            }
        }
        return false
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        if (pointsWorld.isEmpty() || !hasHover) {
            return null
        }
        return ToolMeasurement(
            startWorld = Vector3(pointsWorld.last()),
            endWorld = Vector3(hoverWorld),
            lineColor = Color(ToolFeedbackColors.SECONDARY)
        )
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        if (pointsWorld.isEmpty()) {
            return emptyList()
        }
        val lines = mutableListOf<Pair<Vector3, Vector3>>()
        for (i in 0 until pointsWorld.size - 1) {
            lines.add(Vector3(pointsWorld[i]) to Vector3(pointsWorld[i + 1]))
        }
        previewSegments().forEach { (a, b) ->
            lines.add(Vector3(a) to Vector3(b))
        }
        return lines
    }

    override fun render(renderer: ShapeRenderer) {
        // Feedback is routed through the thick 2D overlay lines.
    }

    private fun previewSegments(): List<Pair<Vector3, Vector3>> {
        if (!hasHover || pointsWorld.isEmpty()) {
            return emptyList()
        }
        val preview = when {
            arcMode == FrameArcMode.CENTER && arcCenterWorld != null ->
                arcPointsFromCenter(pointsWorld.last(), hoverWorld, arcCenterWorld!!)
            arcMode == FrameArcMode.THREE && arcPass1World != null ->
                arcPointsThrough(pointsWorld.last(), arcPass1World!!, hoverWorld)
            arcMode == FrameArcMode.LINE && isClosing(hoverWorld) && pointsWorld.size >= 3 ->
                listOf(pointsWorld.last(), pointsWorld.first())
            else -> emptyList()
        }
        if (preview.size > 1) {
            return preview.zipWithNext { a, b -> Vector3(a) to Vector3(b) }
        }
        if (arcMode == FrameArcMode.LINE) {
            return listOf(Vector3(pointsWorld.last()) to Vector3(hoverWorld))
        }
        return emptyList()
    }

    private fun isClosing(candidate: Vector3): Boolean {
        if (pointsWorld.size < 3) {
            return false
        }
        return pointsWorld.first().dst(candidate) <= closeDistance
    }

    private fun finalizeContour(
        status: StatusModel,
        group: GroupScene.GroupNode,
        closeContour: Boolean
    ): Boolean {
        if (pointsWorld.size < 2) {
            clear()
            status.message = message
            return true
        }
        val planeNormal = planeNormalWorld ?: Vector3(0f, 1f, 0f)
        val contourWorld = when {
            pointsWorld.size == 2 -> {
                val basis = planeBasisFromNormal(planeNormal)
                rectangleCorners(pointsWorld.first(), pointsWorld.last(), basis)
            }
            else -> {
                val pts = pointsWorld.map { Vector3(it) }.toMutableList()
                if (closeContour && pts.size >= 3 && pts.last().dst(pts.first()) <= closeDistance) {
                    pts.removeAt(pts.lastIndex)
                }
                pts
            }
        }
        if (contourWorld.size < 3) {
            clear()
            status.message = "Frame not created."
            return true
        }
        val origin = planeOriginWorld ?: contourWorld.first()
        val basis = planeBasisFromNormal(planeNormal)
        var uMin = Float.POSITIVE_INFINITY
        var uMax = Float.NEGATIVE_INFINITY
        var vMin = Float.POSITIVE_INFINITY
        var vMax = Float.NEGATIVE_INFINITY
        contourWorld.forEach { point ->
            val rel = Vector3(point).sub(origin)
            val u = rel.dot(basis.axisU)
            val v = rel.dot(basis.axisV)
            uMin = min(uMin, u)
            uMax = max(uMax, u)
            vMin = min(vMin, v)
            vMax = max(vMax, v)
        }
        if (!uMin.isFinite() || !uMax.isFinite() || !vMin.isFinite() || !vMax.isFinite()) {
            clear()
            status.message = "Frame not created."
            return true
        }
        val cornerAWorld = Vector3(origin).mulAdd(basis.axisU, uMin).mulAdd(basis.axisV, vMin)
        val cornerBWorld = Vector3(origin).mulAdd(basis.axisU, uMax).mulAdd(basis.axisV, vMax)
        val created = scene.addArchitectureFrame(
            group = group,
            cornerA = group.toLocal(cornerAWorld),
            cornerB = group.toLocal(cornerBWorld),
            contourPoints = contourWorld.map { group.toLocal(it) },
            normal = group.vectorToLocal(planeNormal).nor(),
            depth = settings.frameDepth,
            frameWidth = settings.frameWidth,
            kind = ArchitectureStore.FrameKind.WINDOW,
            color = settings.frameColor,
            glazingEnabled = settings.frameGlazingEnabled,
            glazingColor = settings.frameGlazingColor
        )
        status.message = if (created) "Window frame created." else "Frame not created."
        clear()
        return true
    }

    private fun appendArcPoints(arcPoints: List<Vector3>) {
        if (arcPoints.size <= 1) {
            return
        }
        for (i in 1 until arcPoints.size) {
            val next = arcPoints[i]
            if (pointsWorld.last().dst(next) > epsilon) {
                pointsWorld.add(Vector3(next))
            }
        }
    }

    private fun arcPointsFromCenter(start: Vector3, end: Vector3, center: Vector3): List<Vector3> {
        val startVec = Vector3(start).sub(center)
        val endVec = Vector3(end).sub(center)
        val radius = startVec.len()
        if (radius <= epsilon) {
            return emptyList()
        }
        val normal = Vector3(startVec).crs(endVec)
        if (normal.len2() <= epsilon * epsilon) {
            return if (start.dst(end) <= epsilon) emptyList() else listOf(start, end)
        }
        val u = startVec.nor()
        val w = normal.nor()
        val v = Vector3(w).crs(u).nor()
        val endAngle = kotlin.math.atan2(endVec.dot(v), endVec.dot(u)).toFloat()
        val delta = normalizeAngle(endAngle)
        if (abs(delta) <= epsilon) {
            return listOf(start, end)
        }
        return buildArcPoints(center, u, v, radius, delta, stepsForArc(radius, delta))
    }

    private fun arcPointsThrough(start: Vector3, mid: Vector3, end: Vector3): List<Vector3> {
        val ab = Vector3(mid).sub(start)
        val ac = Vector3(end).sub(start)
        val normal = Vector3(ab).crs(ac)
        if (normal.len2() <= epsilon * epsilon) {
            return if (start.dst(end) <= epsilon) emptyList() else listOf(start, end)
        }
        val u = Vector3(ab).nor()
        val w = Vector3(normal).nor()
        val v = Vector3(w).crs(u).nor()
        val bx = ab.len()
        val cx = ac.dot(u)
        val cy = ac.dot(v)
        val d = 2f * (bx * cy)
        if (abs(d) <= epsilon) {
            return listOf(start, end)
        }
        val ux = (bx * bx * cy) / d
        val uy = (cx * cx + cy * cy - bx * bx) / (2f * cy)
        val center = Vector3(start).add(Vector3(u).scl(ux)).add(Vector3(v).scl(uy))
        val startVec = Vector3(start).sub(center)
        val midVec = Vector3(mid).sub(center)
        val endVec = Vector3(end).sub(center)
        val radius = startVec.len()
        if (radius <= epsilon) {
            return emptyList()
        }
        val u2 = startVec.nor()
        val v2 = Vector3(w).crs(u2).nor()
        val midAngle = kotlin.math.atan2(midVec.dot(v2), midVec.dot(u2)).toFloat()
        val endAngle = kotlin.math.atan2(endVec.dot(v2), endVec.dot(u2)).toFloat()
        var delta = normalizeAngle(endAngle)
        val midNorm = normalizeAngle(midAngle)
        if (!isBetweenCCW(0f, midNorm, delta)) {
            delta -= (Math.PI * 2.0).toFloat()
        }
        if (abs(delta) <= epsilon) {
            return listOf(start, end)
        }
        return buildArcPoints(center, u2, v2, radius, delta, stepsForArc(radius, delta))
    }

    private fun buildArcPoints(
        center: Vector3,
        u: Vector3,
        v: Vector3,
        radius: Float,
        delta: Float,
        steps: Int
    ): List<Vector3> {
        val points = mutableListOf(Vector3(center).add(Vector3(u).scl(radius)))
        val step = delta / steps
        for (i in 1..steps) {
            val angle = step * i
            val cos = kotlin.math.cos(angle.toDouble()).toFloat()
            val sin = kotlin.math.sin(angle.toDouble()).toFloat()
            points.add(
                Vector3(center)
                    .add(Vector3(u).scl(radius * cos))
                    .add(Vector3(v).scl(radius * sin))
            )
        }
        return points
    }

    private fun normalizeAngle(angle: Float): Float {
        val twoPi = (Math.PI * 2.0).toFloat()
        val a = angle % twoPi
        return if (a < 0f) a + twoPi else a
    }

    private fun stepsForArc(radius: Float, delta: Float): Int {
        val steps = kotlin.math.ceil((abs(delta) * radius) / arcMaxLength.coerceAtLeast(1e-4f)).toInt()
        return maxOf(1, minOf(steps, 512))
    }

    private fun isBetweenCCW(start: Float, mid: Float, end: Float): Boolean {
        var endValue = end
        var midValue = mid
        if (endValue < start) {
            endValue += (Math.PI * 2.0).toFloat()
        }
        if (midValue < start) {
            midValue += (Math.PI * 2.0).toFloat()
        }
        return midValue >= start && midValue <= endValue
    }

    private fun clear() {
        pointsWorld.clear()
        planeOriginWorld = null
        planeNormalWorld = null
        hasHover = false
        arcMode = FrameArcMode.LINE
        arcCenterWorld = null
        arcPass1World = null
    }
}

class ArchitectureDoorFrameTool(
    scene: GroupScene,
    settings: ArchitectureSettings,
    onSelectTool: () -> Unit,
    ensureArchitectureGroup: (() -> GroupScene.GroupNode?)? = null
) : ArchitectureRectFrameTool(
    scene = scene,
    settings = settings,
    onSelectTool = onSelectTool,
    ensureArchitectureGroup = ensureArchitectureGroup,
    toolId = ToolId.ARCH_DOOR_FRAME,
    frameKind = ArchitectureStore.FrameKind.DOOR,
    baseMessage = "Pick first door frame corner.",
    drawColor = ToolFeedbackColors.PRIMARY
)

private enum class FrameArcMode { LINE, CENTER, THREE }

abstract class ArchitectureRectFrameTool(
    private val scene: GroupScene,
    private val settings: ArchitectureSettings,
    private val onSelectTool: () -> Unit,
    private val ensureArchitectureGroup: (() -> GroupScene.GroupNode?)?,
    private val toolId: ToolId,
    private val frameKind: ArchitectureStore.FrameKind,
    private val baseMessage: String,
    private val drawColor: Color
) : Tool {
    override val id: ToolId = toolId
    override val message: String = baseMessage

    private var firstCornerWorld: Vector3? = null
    private var firstNormalWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = baseMessage
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
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
        val group = resolveArchitectureGroup(scene, status, ensureArchitectureGroup) ?: return true
        if (firstCornerWorld == null) {
            firstCornerWorld = Vector3(world)
            firstNormalWorld = normal?.let { Vector3(it).nor() } ?: Vector3(0f, 1f, 0f)
            status.message = "Pick opposite frame corner."
            return true
        }
        val first = firstCornerWorld ?: return true
        val worldNormal = firstNormalWorld ?: normal ?: Vector3(0f, 1f, 0f)
        val secondOnPlane = projectPointToPlane(world, first, worldNormal)
        val localNormal = group.vectorToLocal(worldNormal).nor()
        val created = scene.addArchitectureFrame(
            group = group,
            cornerA = group.toLocal(first),
            cornerB = group.toLocal(secondOnPlane),
            normal = localNormal,
            depth = settings.frameDepth,
            frameWidth = settings.frameWidth,
            kind = frameKind,
            color = settings.frameColor,
            glazingEnabled = settings.frameGlazingEnabled,
            glazingColor = settings.frameGlazingColor
        )
        if (created) {
            status.message = "${frameKind.name.lowercase().replaceFirstChar { it.uppercaseChar() }} frame created."
        } else {
            status.message = "Frame not created."
        }
        clear()
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                clear()
                status.message = baseMessage
                true
            }
            Input.Keys.ESCAPE -> {
                clear()
                status.message = "Canceled."
                onSelectTool()
                true
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val start = firstCornerWorld ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(startWorld = Vector3(start), endWorld = Vector3(hover), lineColor = Color(drawColor))
    }

    override fun render(renderer: ShapeRenderer) {
        val first = firstCornerWorld ?: return
        if (!hasHover) {
            return
        }
        val worldNormal = firstNormalWorld ?: Vector3(0f, 1f, 0f)
        val basis = planeBasisFromNormal(worldNormal)
        val hoverOnPlane = projectPointToPlane(hover, first, worldNormal)
        val corners = rectangleCorners(first, hoverOnPlane, basis)
        drawLoop(renderer, corners, drawColor)
    }

    private fun clear() {
        firstCornerWorld = null
        firstNormalWorld = null
        hasHover = false
    }
}

private fun resolveArchitectureGroup(
    scene: GroupScene,
    status: StatusModel,
    ensureArchitectureGroup: (() -> GroupScene.GroupNode?)?
): GroupScene.GroupNode? {
    val current = scene.activeGroup()
    return ensureArchitectureGroup?.invoke() ?: current
}

private fun drawLoop(renderer: ShapeRenderer, points: List<Vector3>, color: Color) {
    if (points.size < 2) {
        return
    }
    renderer.color = color
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        renderer.line(a, b)
    }
}

private fun horizontalRectCorners(first: Vector3, second: Vector3): List<Vector3> {
    val y = first.y
    val p0 = Vector3(first.x, y, first.z)
    val p1 = Vector3(second.x, y, first.z)
    val p2 = Vector3(second.x, y, second.z)
    val p3 = Vector3(first.x, y, second.z)
    return listOf(p0, p1, p2, p3)
}

private fun chooseRectangleBasis(start: Vector3, end: Vector3, fallbackNormal: Vector3): PlaneBasis {
    val delta = Vector3(end).sub(start)
    val eps = 1e-2f
    val absX = abs(delta.x)
    val absY = abs(delta.y)
    val absZ = abs(delta.z)

    if (absY <= eps) {
        return PlaneBasis(Vector3(0f, 1f, 0f), Vector3(1f, 0f, 0f), Vector3(0f, 0f, 1f))
    }
    if (absX <= eps) {
        return PlaneBasis(Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f))
    }
    if (absZ <= eps) {
        return PlaneBasis(Vector3(0f, 0f, 1f), Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f))
    }

    val dirXZ = Vector3(delta.x, 0f, delta.z)
    if (dirXZ.len2() > eps * eps) {
        val axisU = dirXZ.nor()
        val axisV = Vector3(0f, 1f, 0f)
        val normal = Vector3(axisU).crs(axisV).nor()
        return PlaneBasis(normal, axisU, axisV)
    }

    return planeBasisFromNormal(fallbackNormal)
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

private fun projectPointToPlane(point: Vector3, planePoint: Vector3, planeNormal: Vector3): Vector3 {
    val n = Vector3(planeNormal)
    if (n.len2() <= 1e-6f) {
        return Vector3(point)
    }
    n.nor()
    val signed = Vector3(point).sub(planePoint).dot(n)
    return Vector3(point).mulAdd(n, -signed)
}

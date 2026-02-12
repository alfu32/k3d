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
        return ToolMeasurement(startWorld = Vector3(start), endWorld = Vector3(hover), lineColor = Color(0.95f, 0.65f, 0.25f, 1f))
    }

    override fun render(renderer: ShapeRenderer) {
        val start = anchorWorld ?: return
        if (!hasHover) {
            return
        }
        renderer.color = Color(0.95f, 0.65f, 0.25f, 1f)
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
        return ToolMeasurement(startWorld = Vector3(start), endWorld = Vector3(hover), lineColor = Color(0.35f, 0.75f, 0.95f, 1f))
    }

    override fun render(renderer: ShapeRenderer) {
        val first = firstCornerWorld ?: return
        if (!hasHover) {
            return
        }
        val corners = horizontalRectCorners(first, hover)
        drawLoop(renderer, corners, Color(0.35f, 0.75f, 0.95f, 1f))
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
        val group = resolveWallTargetGroup(status) ?: return true
        if (!scene.isWallOnlyArchitectureGroup(group)) {
            status.message = "Add Hole works only on wall-only architecture groups."
            return true
        }
        if (firstCornerWorld == null) {
            firstCornerWorld = Vector3(world)
            firstNormalWorld = normal?.let { Vector3(it).nor() } ?: Vector3(0f, 1f, 0f)
            status.message = "Pick opposite hole corner."
            return true
        }
        val first = firstCornerWorld ?: return true
        val localA = group.toLocal(first)
        val localB = group.toLocal(world)
        val selectedWallId = scene.selectedArchitectureElement(group)
            ?.takeIf { it.kind == ArchitectureStore.ElementKind.WALL }
            ?.id
        val created = if (selectedWallId != null) {
            scene.addArchitectureHoleToWall(group, selectedWallId, localA, localB)
        } else {
            scene.addArchitectureHoleToNearestWall(group, localA, localB)
        }
        if (created) {
            status.message = "Wall hole added."
        } else {
            status.message = "No compatible wall found for hole."
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
        return ToolMeasurement(startWorld = Vector3(start), endWorld = Vector3(hover), lineColor = Color(0.95f, 0.55f, 0.25f, 1f))
    }

    override fun render(renderer: ShapeRenderer) {
        val first = firstCornerWorld ?: return
        if (!hasHover) {
            return
        }
        val basis = chooseRectangleBasis(first, hover, firstNormalWorld ?: Vector3(0f, 1f, 0f))
        val corners = rectangleCorners(first, hover, basis)
        drawLoop(renderer, corners, Color(0.95f, 0.55f, 0.25f, 1f))
    }

    private fun clear() {
        firstCornerWorld = null
        firstNormalWorld = null
        hasHover = false
    }

    private fun resolveWallTargetGroup(status: StatusModel): GroupScene.GroupNode? {
        val active = scene.activeGroup()
        if (scene.isArchitectureGroup(active) && scene.isWallOnlyArchitectureGroup(active)) {
            return active
        }
        val selected = scene.selectedGroups().filter { scene.isArchitectureGroup(it) && scene.isWallOnlyArchitectureGroup(it) }
        return when {
            selected.size == 1 -> selected.first()
            selected.size > 1 -> {
                status.message = "Select only one wall group before drawing holes."
                null
            }
            else -> {
                status.message = "Select a wall group (or enter one) before drawing holes."
                null
            }
        }
    }
}

class ArchitectureStairTool(
    private val scene: GroupScene,
    private val camera: Camera,
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
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())

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
        return ToolMeasurement(startWorld = Vector3(start), endWorld = Vector3(hoverPoint), lineColor = Color(0.75f, 0.55f, 0.95f, 1f))
    }

    override fun render(renderer: ShapeRenderer) {
        contourPath?.let { contour ->
            drawLoop(renderer, contour.points, Color(0.75f, 0.55f, 0.95f, 1f))
        }
        treadPath?.let { tread ->
            renderer.color = Color(0.95f, 0.6f, 0.3f, 1f)
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
        val projected = camera.project(Vector3(world))
        val dx = projected.x - screenX
        val dy = (Gdx.graphics.height - projected.y) - screenY
        return sqrt(dx * dx + dy * dy)
    }
}

class ArchitectureWindowFrameTool(
    scene: GroupScene,
    settings: ArchitectureSettings,
    onSelectTool: () -> Unit,
    ensureArchitectureGroup: (() -> GroupScene.GroupNode?)? = null
) : ArchitectureFrameTool(
    scene = scene,
    settings = settings,
    onSelectTool = onSelectTool,
    ensureArchitectureGroup = ensureArchitectureGroup,
    toolId = ToolId.ARCH_WINDOW_FRAME,
    frameKind = ArchitectureStore.FrameKind.WINDOW,
    baseMessage = "Pick first window frame corner.",
    drawColor = Color(0.35f, 0.8f, 0.95f, 1f)
)

class ArchitectureDoorFrameTool(
    scene: GroupScene,
    settings: ArchitectureSettings,
    onSelectTool: () -> Unit,
    ensureArchitectureGroup: (() -> GroupScene.GroupNode?)? = null
) : ArchitectureFrameTool(
    scene = scene,
    settings = settings,
    onSelectTool = onSelectTool,
    ensureArchitectureGroup = ensureArchitectureGroup,
    toolId = ToolId.ARCH_DOOR_FRAME,
    frameKind = ArchitectureStore.FrameKind.DOOR,
    baseMessage = "Pick first door frame corner.",
    drawColor = Color(0.95f, 0.75f, 0.25f, 1f)
)

abstract class ArchitectureFrameTool(
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
            color = settings.frameColor
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
    if (scene.isArchitectureGroup(current)) {
        return current
    }
    val created = ensureArchitectureGroup?.invoke()
    val resolved = created ?: scene.activeGroup()
    if (!scene.isArchitectureGroup(resolved)) {
        status.message = "Active object is not an architecture group."
        return null
    }
    return resolved
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

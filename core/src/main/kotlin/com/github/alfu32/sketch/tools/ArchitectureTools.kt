package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.ArchitectureStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.abs

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
            inclinationDeg = settings.wallInclinationDeg
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
            thickness = settings.slabThickness
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
        val group = resolveArchitectureGroup(scene, status, ensureArchitectureGroup) ?: return true
        if (firstCornerWorld == null) {
            firstCornerWorld = Vector3(world)
            firstNormalWorld = normal?.let { Vector3(it).nor() } ?: Vector3(0f, 1f, 0f)
            status.message = "Pick opposite hole corner."
            return true
        }
        val first = firstCornerWorld ?: return true
        val created = scene.addArchitectureHoleToNearestWall(
            group = group,
            cornerA = group.toLocal(first),
            cornerB = group.toLocal(world)
        )
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
}

class ArchitectureStairTool(
    private val scene: GroupScene,
    private val settings: ArchitectureSettings,
    private val onSelectTool: () -> Unit,
    private val ensureArchitectureGroup: (() -> GroupScene.GroupNode?)? = null
) : Tool {
    override val id: ToolId = ToolId.ARCH_STAIR
    override val message: String = "Pick first stair contour corner."

    private var contourAWorld: Vector3? = null
    private var contourBWorld: Vector3? = null
    private var walkingStartWorld: Vector3? = null
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
        if (contourAWorld == null) {
            contourAWorld = Vector3(world)
            status.message = "Pick opposite stair contour corner."
            return true
        }
        if (contourBWorld == null) {
            contourBWorld = Vector3(world)
            status.message = "Pick walking line start point."
            return true
        }
        if (walkingStartWorld == null) {
            walkingStartWorld = Vector3(world)
            status.message = "Pick walking line end point."
            return true
        }
        val contourA = contourAWorld ?: return true
        val contourB = contourBWorld ?: return true
        val walkingStart = walkingStartWorld ?: return true
        val created = scene.addArchitectureStair(
            group = group,
            minCorner = group.toLocal(contourA),
            maxCorner = group.toLocal(contourB),
            walkingStart = group.toLocal(walkingStart),
            walkingEnd = group.toLocal(world),
            height = settings.stairHeight,
            stepCount = settings.stairStepCount,
            supportThickness = settings.stairSupportThickness
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
        if (!hasHover) {
            return null
        }
        val start = when {
            walkingStartWorld != null -> walkingStartWorld
            contourBWorld != null -> contourBWorld
            contourAWorld != null -> contourAWorld
            else -> null
        } ?: return null
        return ToolMeasurement(startWorld = Vector3(start), endWorld = Vector3(hover), lineColor = Color(0.75f, 0.55f, 0.95f, 1f))
    }

    override fun render(renderer: ShapeRenderer) {
        val contourA = contourAWorld
        if (contourA != null && contourBWorld == null && hasHover) {
            drawLoop(renderer, horizontalRectCorners(contourA, hover), Color(0.75f, 0.55f, 0.95f, 1f))
            return
        }
        val contourB = contourBWorld
        if (contourA != null && contourB != null) {
            drawLoop(renderer, horizontalRectCorners(contourA, contourB), Color(0.75f, 0.55f, 0.95f, 1f))
        }
        if (walkingStartWorld != null && hasHover) {
            renderer.color = Color(0.95f, 0.6f, 0.3f, 1f)
            renderer.line(walkingStartWorld, hover)
        }
    }

    private fun clear() {
        contourAWorld = null
        contourBWorld = null
        walkingStartWorld = null
        hasHover = false
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
        val localNormal = group.vectorToLocal(worldNormal).nor()
        val created = scene.addArchitectureFrame(
            group = group,
            cornerA = group.toLocal(first),
            cornerB = group.toLocal(world),
            normal = localNormal,
            depth = settings.frameDepth,
            frameWidth = settings.frameWidth,
            kind = frameKind
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
        val basis = chooseRectangleBasis(first, hover, firstNormalWorld ?: Vector3(0f, 1f, 0f))
        val corners = rectangleCorners(first, hover, basis)
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

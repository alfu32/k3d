package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private data class MultipleCopyCounts(
    var architecture: Int = 0,
    var hvac: Int = 0,
    var voxels: Int = 0,
    var edges: Int = 0,
    var faces: Int = 0,
    var dimensions: Int = 0,
    var texts: Int = 0,
    var groups: Int = 0
)

private const val MAX_MULTIPLE_COPY_STEPS = 2048
private const val MAX_MULTIPLE_COPY_PREVIEW_STEPS = 32
private const val MULTIPLE_COPY_EPS = 1e-5f

private fun applyCopyStep(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    pointTransformWorld: (Vector3) -> Vector3,
    vectorTransformWorld: (Vector3) -> Vector3,
    pointTransformLocal: (Vector3) -> Vector3,
    pointTransformVoxelLocal: ((Vector3) -> Vector3)?
): MultipleCopyCounts {
    val counts = MultipleCopyCounts()
    counts.architecture = scene.copySelectedArchitectureElements(
        group = group,
        pointTransform = pointTransformWorld,
        vectorTransform = vectorTransformWorld
    )
    counts.hvac = scene.copySelectedHvacElements(
        group = group,
        pointTransform = pointTransformWorld
    )
    counts.faces = group.faceStore.copySelected(pointTransformLocal)
    counts.edges = group.lineStore.copySelected(pointTransformLocal)
    counts.dimensions = group.dimensionStore.copySelected(pointTransformLocal)
    counts.texts = group.textStore.copySelected(pointTransformLocal)
    counts.groups = scene.copySelectedGroups(pointTransformWorld, vectorTransformWorld)
    counts.voxels = if (pointTransformVoxelLocal != null) {
        copySelectedVoxelsWithLocalTransform(scene, group, pointTransformVoxelLocal)
    } else {
        0
    }
    return counts
}

private fun copySelectedVoxelsWithLocalTransform(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    transform: (Vector3) -> Vector3
): Int {
    if (!scene.isVoxelGroup(group)) {
        return 0
    }
    val store = group.voxelStore ?: return 0
    val selected = store.selected().toList()
    if (selected.isEmpty()) {
        return 0
    }
    val entries = selected.mapNotNull { key -> store.colorAt(key)?.let { color -> key to color } }
    if (entries.isEmpty()) {
        return 0
    }
    var changed = 0
    val nextSelection = linkedSetOf<com.github.alfu32.sketch.model.VoxelStore.Key>()
    entries.forEach { (key, color) ->
        val target = transform(Vector3(key.x.toFloat(), key.y.toFloat(), key.z.toFloat()))
        val nx = target.x.roundToInt()
        val ny = target.y.roundToInt()
        val nz = target.z.roundToInt()
        if (store.set(nx, ny, nz, color)) {
            changed++
        }
        nextSelection.add(com.github.alfu32.sketch.model.VoxelStore.Key(nx, ny, nz))
    }
    store.replaceSelection(nextSelection)
    scene.rebuildVoxelGeometry(group.prototype)
    return changed
}

private fun mergeCounts(total: MultipleCopyCounts, step: MultipleCopyCounts) {
    total.architecture += step.architecture
    total.hvac += step.hvac
    total.voxels += step.voxels
    total.edges += step.edges
    total.faces += step.faces
    total.dimensions += step.dimensions
    total.texts += step.texts
    total.groups += step.groups
}

private fun drawCross(renderer: ShapeRenderer, point: Vector3, size: Float) {
    renderer.line(point.x - size, point.y, point.z, point.x + size, point.y, point.z)
    renderer.line(point.x, point.y - size, point.z, point.x, point.y + size, point.z)
    renderer.line(point.x, point.y, point.z - size, point.x, point.y, point.z + size)
}

private fun signedAngleDeg(from: Vector3, to: Vector3, axis: Vector3): Float {
    val n = Vector3(axis)
    if (n.len2() <= MULTIPLE_COPY_EPS) {
        return 0f
    }
    n.nor()
    val a = Vector3(from).mulAdd(n, -from.dot(n))
    val b = Vector3(to).mulAdd(n, -to.dot(n))
    if (a.len2() <= MULTIPLE_COPY_EPS || b.len2() <= MULTIPLE_COPY_EPS) {
        return 0f
    }
    a.nor()
    b.nor()
    val cross = Vector3(a).crs(b)
    val sin = n.dot(cross).coerceIn(-1f, 1f)
    val cos = a.dot(b).coerceIn(-1f, 1f)
    return MathUtils.atan2(sin, cos) * MathUtils.radiansToDegrees
}

private fun normalizeSignedSweep(angle: Float, direction: Float): Float {
    var sweep = angle
    if (direction > 0f) {
        while (sweep <= 0f) {
            sweep += 360f
        }
    } else {
        while (sweep >= 0f) {
            sweep -= 360f
        }
    }
    return sweep
}

private fun boundingCorners(bounds: BoundingBox): Array<Vector3> {
    val min = bounds.min
    val max = bounds.max
    return arrayOf(
        Vector3(min.x, min.y, min.z),
        Vector3(min.x, min.y, max.z),
        Vector3(min.x, max.y, min.z),
        Vector3(min.x, max.y, max.z),
        Vector3(max.x, min.y, min.z),
        Vector3(max.x, min.y, max.z),
        Vector3(max.x, max.y, min.z),
        Vector3(max.x, max.y, max.z)
    )
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

private fun renderMultiplePreview(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    renderer: ShapeRenderer,
    previewCopies: Int,
    localPointTransformAtStep: (Int, Vector3) -> Vector3,
    worldPointTransformAtStep: (Int, Vector3) -> Vector3
) {
    if (previewCopies <= 0) {
        return
    }
    renderer.color = ToolFeedbackColors.TERTIARY

    val selectedFaces = group.faceStore.getSelected().toList()
    val selectedEdges = group.lineStore.getSelected().toList()
    val selectedDims = group.dimensionStore.getSelected().toList()
    val selectedTexts = group.textStore.getSelected().toList()
    val selectedGroups = scene.selectedGroups().toList()
    val architectureBounds = scene.selectedArchitectureBounds(group)
    val hvacBounds = scene.selectedHvacBounds(group)
    val selectedVoxels = if (scene.isVoxelGroup(group)) scene.selectedVoxels(group).toList() else emptyList()

    val voxelBoundsCorners = if (selectedVoxels.isNotEmpty()) {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        selectedVoxels.forEach { key ->
            minX = kotlin.math.min(minX, key.x)
            minY = kotlin.math.min(minY, key.y)
            minZ = kotlin.math.min(minZ, key.z)
            maxX = kotlin.math.max(maxX, key.x + 1)
            maxY = kotlin.math.max(maxY, key.y + 1)
            maxZ = kotlin.math.max(maxZ, key.z + 1)
        }
        arrayOf(
            Vector3(minX.toFloat(), minY.toFloat(), minZ.toFloat()),
            Vector3(minX.toFloat(), minY.toFloat(), maxZ.toFloat()),
            Vector3(minX.toFloat(), maxY.toFloat(), minZ.toFloat()),
            Vector3(minX.toFloat(), maxY.toFloat(), maxZ.toFloat()),
            Vector3(maxX.toFloat(), minY.toFloat(), minZ.toFloat()),
            Vector3(maxX.toFloat(), minY.toFloat(), maxZ.toFloat()),
            Vector3(maxX.toFloat(), maxY.toFloat(), minZ.toFloat()),
            Vector3(maxX.toFloat(), maxY.toFloat(), maxZ.toFloat())
        )
    } else {
        emptyArray()
    }

    for (step in 1..previewCopies) {
        selectedFaces.forEach { tri ->
            val a = group.toWorld(localPointTransformAtStep(step, tri.a))
            val b = group.toWorld(localPointTransformAtStep(step, tri.b))
            val c = group.toWorld(localPointTransformAtStep(step, tri.c))
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            renderer.line(b.x, b.y, b.z, c.x, c.y, c.z)
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
        }
        selectedEdges.forEach { segment ->
            val a = group.toWorld(localPointTransformAtStep(step, segment.start))
            val b = group.toWorld(localPointTransformAtStep(step, segment.end))
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
        selectedDims.forEach { dim ->
            val a = group.toWorld(localPointTransformAtStep(step, dim.start))
            val b = group.toWorld(localPointTransformAtStep(step, dim.end))
            val o = group.toWorld(localPointTransformAtStep(step, dim.offset))
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            renderer.line(a.x, a.y, a.z, o.x, o.y, o.z)
            renderer.line(b.x, b.y, b.z, o.x, o.y, o.z)
        }
        selectedTexts.forEach { text ->
            val p = group.toWorld(localPointTransformAtStep(step, text.position))
            drawCross(renderer, p, 0.12f)
        }
        selectedGroups.forEach { selectedGroup ->
            val corners = selectedGroup.orientedBoundsCorners() ?: return@forEach
            val transformed = Array(corners.size) { idx -> worldPointTransformAtStep(step, corners[idx]) }
            drawWireBox(renderer, transformed)
        }
        if (architectureBounds != null) {
            val corners = boundingCorners(architectureBounds)
            val transformed = Array(corners.size) { idx -> worldPointTransformAtStep(step, corners[idx]) }
            drawWireBox(renderer, transformed)
        }
        if (hvacBounds != null) {
            val corners = boundingCorners(hvacBounds)
            val transformed = Array(corners.size) { idx -> worldPointTransformAtStep(step, corners[idx]) }
            drawWireBox(renderer, transformed)
        }
        if (voxelBoundsCorners.isNotEmpty()) {
            val transformed = Array(voxelBoundsCorners.size) { idx ->
                val local = localPointTransformAtStep(step, voxelBoundsCorners[idx])
                Vector3(
                    local.x.roundToInt().toFloat(),
                    local.y.roundToInt().toFloat(),
                    local.z.roundToInt().toFloat()
                )
            }.map { group.toWorld(it) }.toTypedArray()
            drawWireBox(renderer, transformed)
        }
    }
}

class CopyMultipleTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.COPY_MULTIPLE
    override val message: String = "Pick reference point (u)."

    private var u: Vector3? = null
    private var v: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Pick reference point (u)."
    }

    override fun onExit(status: StatusModel) {
        clearTransient()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clearTransient()
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
        if (u == null) {
            u = Vector3(world)
            status.message = "Pick size point (v)."
            return true
        }
        if (v == null) {
            v = Vector3(world)
            status.message = "Pick span point (d)."
            return true
        }

        val ref = u ?: return false
        val sizePoint = v ?: return false
        val spanPoint = Vector3(world)
        val sizeVector = Vector3(sizePoint).sub(ref)
        val spanVector = Vector3(spanPoint).sub(ref)
        val spanLength = spanVector.len()
        if (spanLength <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Copy multiple canceled: span is too short."
            return true
        }
        val spanDir = Vector3(spanVector).scl(1f / spanLength)
        val stepSigned = sizeVector.dot(spanDir)
        val stepLength = abs(stepSigned)
        if (stepLength <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Copy multiple canceled: size projection on span is too small."
            return true
        }
        var copies = floor(spanLength / stepLength).toInt()
        if (copies <= 0) {
            clearTransient()
            status.message = "Copy multiple: projection does not fit into span."
            return true
        }
        var capped = false
        if (copies > MAX_MULTIPLE_COPY_STEPS) {
            copies = MAX_MULTIPLE_COPY_STEPS
            capped = true
        }

        val stepWorld = Vector3(spanDir).scl(stepSigned)
        val group = scene.activeGroup()
        val stepLocal = group.vectorToLocal(stepWorld)
        val total = MultipleCopyCounts()
        repeat(copies) {
            val stepCounts = applyCopyStep(
                scene = scene,
                group = group,
                pointTransformWorld = { point -> Vector3(point).add(stepWorld) },
                vectorTransformWorld = { vector -> Vector3(vector) },
                pointTransformLocal = { point -> Vector3(point).add(stepLocal) },
                pointTransformVoxelLocal = { point -> Vector3(point).add(stepLocal) }
            )
            mergeCounts(total, stepCounts)
        }
        status.message = buildString {
            append("Copy multiple: copies ").append(copies)
            if (capped) append(" (capped)")
            append(" | architecture ").append(total.architecture)
            append(" hvac ").append(total.hvac)
            append(" voxels ").append(total.voxels)
            append(" edges ").append(total.edges)
            append(" faces ").append(total.faces)
            append(" dims ").append(total.dimensions)
            append(" texts ").append(total.texts)
            append(" groups ").append(total.groups)
        }
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val ref = u ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, ref, 0.18f)
        val sizePoint = v
        if (sizePoint != null) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, sizePoint, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(ref.x, ref.y, ref.z, sizePoint.x, sizePoint.y, sizePoint.z)
        }
        if (hasHover) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, hover, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(ref.x, ref.y, ref.z, hover.x, hover.y, hover.z)
            val sizePoint = v
            if (sizePoint != null) {
                val sizeVector = Vector3(sizePoint).sub(ref)
                val spanVector = Vector3(hover).sub(ref)
                val spanLength = spanVector.len()
                if (spanLength > MULTIPLE_COPY_EPS) {
                    val spanDir = Vector3(spanVector).scl(1f / spanLength)
                    val stepSigned = sizeVector.dot(spanDir)
                    val stepLength = abs(stepSigned)
                    if (stepLength > MULTIPLE_COPY_EPS) {
                        val previewCopies =
                            floor(spanLength / stepLength).toInt().coerceAtLeast(0).coerceAtMost(MAX_MULTIPLE_COPY_PREVIEW_STEPS)
                        if (previewCopies > 0) {
                            val stepWorld = Vector3(spanDir).scl(stepSigned)
                            val group = scene.activeGroup()
                            val stepLocal = group.vectorToLocal(stepWorld)
                            renderMultiplePreview(
                                scene = scene,
                                group = group,
                                renderer = renderer,
                                previewCopies = previewCopies,
                                localPointTransformAtStep = { step, point -> Vector3(point).mulAdd(stepLocal, step.toFloat()) },
                                worldPointTransformAtStep = { step, point -> Vector3(point).mulAdd(stepWorld, step.toFloat()) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val ref = u ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(Vector3(ref), Vector3(hover))
    }

    private fun clearTransient() {
        u = null
        v = null
        hasHover = false
    }
}

abstract class BaseRotateMultipleTool(
    private val scene: GroupScene,
    private val helicoidal: Boolean
) : Tool {
    private var center: Vector3? = null
    private var reference: Vector3? = null
    private var increment: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Pick rotation center (c)."
    }

    override fun onExit(status: StatusModel) {
        clearTransient()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clearTransient()
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
        if (center == null) {
            center = Vector3(world)
            status.message = "Pick reference point (u)."
            return true
        }
        if (reference == null) {
            reference = Vector3(world)
            status.message = "Pick increment point (v)."
            return true
        }
        if (increment == null) {
            increment = Vector3(world)
            status.message = "Pick final point (w)."
            return true
        }

        val c = center ?: return false
        val u = reference ?: return false
        val v = increment ?: return false
        val w = Vector3(world)

        val ref = Vector3(u).sub(c)
        val inc = Vector3(v).sub(c)
        val end = Vector3(w).sub(c)
        if (ref.len2() <= MULTIPLE_COPY_EPS || inc.len2() <= MULTIPLE_COPY_EPS || end.len2() <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Rotate multiple canceled: vectors are too short."
            return true
        }
        val normalAxis = Vector3(ref).crs(inc)
        if (normalAxis.len2() <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Rotate multiple canceled: c/u/v are collinear."
            return true
        }
        normalAxis.nor()

        val stepAngle = signedAngleDeg(ref, inc, normalAxis)
        if (abs(stepAngle) <= 1e-4f) {
            clearTransient()
            status.message = "Rotate multiple canceled: angular increment is zero."
            return true
        }
        val rawSweep = signedAngleDeg(ref, end, normalAxis)
        val direction = if (stepAngle >= 0f) 1f else -1f
        var sweep = normalizeSignedSweep(rawSweep, direction)
        if (abs(sweep) <= 1e-4f) {
            sweep = 360f * direction
        }
        if (sweep * direction <= 0f) {
            clearTransient()
            status.message = "Rotate multiple canceled: sweep direction differs from increment."
            return true
        }

        val stepAbs = abs(stepAngle)
        var copies = floor(abs(sweep) / stepAbs).toInt()
        if (copies <= 0) {
            clearTransient()
            status.message = "Rotate multiple: increment does not fit in sweep."
            return true
        }
        var capped = false
        if (copies > MAX_MULTIPLE_COPY_STEPS) {
            copies = MAX_MULTIPLE_COPY_STEPS
            capped = true
        }

        val totalLiftWorld = if (helicoidal) {
            end.dot(normalAxis) - ref.dot(normalAxis)
        } else {
            0f
        }
        val perStepLiftWorld = if (helicoidal) totalLiftWorld / copies.toFloat() else 0f

        // Avoid duplicate full-circle copy on purely planar 360 distributions.
        if (abs(abs(sweep) - 360f) <= 1e-3f && abs(perStepLiftWorld) <= 1e-6f && copies > 1) {
            copies -= 1
        }
        if (copies <= 0) {
            clearTransient()
            status.message = "Rotate multiple: no effective copies."
            return true
        }

        val group = scene.activeGroup()
        val centerLocal = group.toLocal(c)
        val axisLocal = group.vectorToLocal(normalAxis).nor()
        val localLiftStep = group.vectorToLocal(Vector3(normalAxis).scl(perStepLiftWorld))
        val rotationWorld = Quaternion().setFromAxis(normalAxis, stepAngle)
        val rotationLocal = Quaternion().setFromAxis(axisLocal, stepAngle)

        val total = MultipleCopyCounts()
        repeat(copies) {
            val stepCounts = applyCopyStep(
                scene = scene,
                group = group,
                pointTransformWorld = { point ->
                    Vector3(point).sub(c).mul(rotationWorld).add(c).mulAdd(normalAxis, perStepLiftWorld)
                },
                vectorTransformWorld = { vector -> Vector3(vector).mul(rotationWorld) },
                pointTransformLocal = { point ->
                    Vector3(point).sub(centerLocal).mul(rotationLocal).add(centerLocal).add(localLiftStep)
                },
                pointTransformVoxelLocal = { point ->
                    Vector3(point).sub(centerLocal).mul(rotationLocal).add(centerLocal).add(localLiftStep)
                }
            )
            mergeCounts(total, stepCounts)
        }

        status.message = buildString {
            append(if (helicoidal) "Helicoidal rotate multiple: copies " else "Planar rotate multiple: copies ")
            append(copies)
            if (capped) append(" (capped)")
            append(" | architecture ").append(total.architecture)
            append(" hvac ").append(total.hvac)
            append(" voxels ").append(total.voxels)
            append(" edges ").append(total.edges)
            append(" faces ").append(total.faces)
            append(" dims ").append(total.dimensions)
            append(" texts ").append(total.texts)
            append(" groups ").append(total.groups)
        }
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val c = center ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, c, 0.18f)
        reference?.let { u ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, u, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, u.x, u.y, u.z)
        }
        increment?.let { v ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, v, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, v.x, v.y, v.z)
        }
        if (hasHover) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, hover, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, hover.x, hover.y, hover.z)

            val u = reference
            val v = increment
            if (u != null && v != null) {
                val ref = Vector3(u).sub(c)
                val inc = Vector3(v).sub(c)
                val end = Vector3(hover).sub(c)
                if (ref.len2() > MULTIPLE_COPY_EPS && inc.len2() > MULTIPLE_COPY_EPS && end.len2() > MULTIPLE_COPY_EPS) {
                    val normalAxis = Vector3(ref).crs(inc)
                    if (normalAxis.len2() > MULTIPLE_COPY_EPS) {
                        normalAxis.nor()
                        val stepAngle = signedAngleDeg(ref, inc, normalAxis)
                        if (abs(stepAngle) > 1e-4f) {
                            val direction = if (stepAngle >= 0f) 1f else -1f
                            val rawSweep = signedAngleDeg(ref, end, normalAxis)
                            var sweep = normalizeSignedSweep(rawSweep, direction)
                            if (abs(sweep) <= 1e-4f) {
                                sweep = 360f * direction
                            }
                            if (sweep * direction > 0f) {
                                val totalCopies = floor(abs(sweep) / abs(stepAngle)).toInt()
                                if (totalCopies > 0) {
                                    val previewCopies = totalCopies.coerceAtMost(MAX_MULTIPLE_COPY_PREVIEW_STEPS)
                                    val totalLiftWorld = if (helicoidal) {
                                        end.dot(normalAxis) - ref.dot(normalAxis)
                                    } else {
                                        0f
                                    }
                                    val perStepLiftWorld =
                                        if (helicoidal) totalLiftWorld / max(totalCopies, 1).toFloat() else 0f
                                    val group = scene.activeGroup()
                                    val centerLocal = group.toLocal(c)
                                    val axisLocal = group.vectorToLocal(normalAxis).nor()
                                    val localLiftStep = group.vectorToLocal(Vector3(normalAxis).scl(perStepLiftWorld))

                                    renderMultiplePreview(
                                        scene = scene,
                                        group = group,
                                        renderer = renderer,
                                        previewCopies = previewCopies,
                                        localPointTransformAtStep = { step, point ->
                                            val q = Quaternion().setFromAxis(axisLocal, stepAngle * step.toFloat())
                                            Vector3(point)
                                                .sub(centerLocal)
                                                .mul(q)
                                                .add(centerLocal)
                                                .mulAdd(localLiftStep, step.toFloat())
                                        },
                                        worldPointTransformAtStep = { step, point ->
                                            val q = Quaternion().setFromAxis(normalAxis, stepAngle * step.toFloat())
                                            Vector3(point)
                                                .sub(c)
                                                .mul(q)
                                                .add(c)
                                                .mulAdd(normalAxis, perStepLiftWorld * step.toFloat())
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val c = center ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(Vector3(c), Vector3(hover))
    }

    private fun clearTransient() {
        center = null
        reference = null
        increment = null
        hasHover = false
    }
}

class PlanarRotateMultipleTool(
    scene: GroupScene
) : BaseRotateMultipleTool(scene, helicoidal = false) {
    override val id: ToolId = ToolId.PLANAR_ROTATE_MULTIPLE
    override val message: String = "Pick rotation center (c)."
}

class HelicoidalRotateMultipleTool(
    scene: GroupScene
) : BaseRotateMultipleTool(scene, helicoidal = true) {
    override val id: ToolId = ToolId.HELICOIDAL_ROTATE_MULTIPLE
    override val message: String = "Pick rotation center (c)."
}

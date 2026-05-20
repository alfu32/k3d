package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.github.alfu32.sketch.model.ArchitectureStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.model.HvacStore
import com.github.alfu32.sketch.model.VoxelStore
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import com.github.alfu32.sketch.ui.ToolMeasurementLabel
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

private data class MultipleCopySelectionSnapshot(
    val faces: List<com.github.alfu32.sketch.model.DraftFaceStore.Triangle>,
    val edges: List<com.github.alfu32.sketch.model.DraftLineStore.Segment>,
    val dimensions: List<com.github.alfu32.sketch.model.DraftDimensionStore.LinearDimension>,
    val texts: List<com.github.alfu32.sketch.model.DraftTextStore.TextEntity>,
    val groups: List<GroupScene.GroupNode>,
    val architecture: List<ArchitectureStore.ElementSelection>,
    val hvac: List<HvacStore.ElementSelection>,
    val voxels: List<VoxelStore.Key>
)

private data class MultipleCopySelectionAccumulator(
    val faces: LinkedHashSet<com.github.alfu32.sketch.model.DraftFaceStore.Triangle> = linkedSetOf(),
    val edges: LinkedHashSet<com.github.alfu32.sketch.model.DraftLineStore.Segment> = linkedSetOf(),
    val dimensions: LinkedHashSet<com.github.alfu32.sketch.model.DraftDimensionStore.LinearDimension> = linkedSetOf(),
    val texts: LinkedHashSet<com.github.alfu32.sketch.model.DraftTextStore.TextEntity> = linkedSetOf(),
    val groups: LinkedHashSet<GroupScene.GroupNode> = linkedSetOf(),
    val architecture: LinkedHashSet<ArchitectureStore.ElementSelection> = linkedSetOf(),
    val hvac: LinkedHashSet<HvacStore.ElementSelection> = linkedSetOf(),
    val voxels: LinkedHashSet<VoxelStore.Key> = linkedSetOf()
)

private const val MAX_MULTIPLE_COPY_STEPS = 2048
private const val MAX_MULTIPLE_COPY_PREVIEW_STEPS = 10_000
private const val MULTIPLE_COPY_EPS = 1e-5f
private const val FULL_CIRCLE_REQUEST_EPS_DEG = 1f

private fun snapshotMultipleCopySelection(
    scene: GroupScene,
    group: GroupScene.GroupNode
): MultipleCopySelectionSnapshot {
    return MultipleCopySelectionSnapshot(
        faces = group.faceStore.getSelected().toList(),
        edges = group.lineStore.getSelected().toList(),
        dimensions = group.dimensionStore.getSelected().toList(),
        texts = group.textStore.getSelected().toList(),
        groups = scene.selectedGroups().toList(),
        architecture = scene.selectedArchitectureElements(group).toList(),
        hvac = scene.selectedHvacElements(group).toList(),
        voxels = scene.selectedVoxels(group).toList()
    )
}

private fun restoreMultipleCopySelection(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    snapshot: MultipleCopySelectionSnapshot
) {
    group.faceStore.withChangeSuppressed {
        group.faceStore.clearSelection()
        snapshot.faces.forEach { group.faceStore.addSelection(it) }
    }
    group.lineStore.withChangeSuppressed {
        group.lineStore.clearSelection()
        snapshot.edges.forEach { group.lineStore.addSelection(it) }
    }
    group.dimensionStore.clearSelection()
    snapshot.dimensions.forEach { group.dimensionStore.addSelection(it) }
    group.textStore.clearSelection()
    snapshot.texts.forEach { group.textStore.addSelection(it) }
    scene.clearGroupSelection()
    snapshot.groups.forEach { scene.addGroupSelection(it) }
    scene.clearArchitectureElementSelection(group)
    snapshot.architecture.forEachIndexed { index, selection ->
        scene.selectArchitectureElement(
            group = group,
            kind = selection.kind,
            id = selection.id,
            mode = if (index == 0) GroupScene.ArchitectureSelectionMode.REPLACE else GroupScene.ArchitectureSelectionMode.ADD
        )
    }
    scene.clearHvacElementSelection(group)
    snapshot.hvac.forEachIndexed { index, selection ->
        scene.selectHvacElement(
            group = group,
            kind = selection.kind,
            id = selection.id,
            mode = if (index == 0) GroupScene.HvacSelectionMode.REPLACE else GroupScene.HvacSelectionMode.ADD
        )
    }
    scene.replaceVoxelSelection(group, snapshot.voxels)
}

private fun collectMultipleCopySelection(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    target: MultipleCopySelectionAccumulator
) {
    target.faces.addAll(group.faceStore.getSelected())
    target.edges.addAll(group.lineStore.getSelected())
    target.dimensions.addAll(group.dimensionStore.getSelected())
    target.texts.addAll(group.textStore.getSelected())
    target.groups.addAll(scene.selectedGroups())
    target.architecture.addAll(scene.selectedArchitectureElements(group))
    target.hvac.addAll(scene.selectedHvacElements(group))
    target.voxels.addAll(scene.selectedVoxels(group))
}

private fun applyMultipleCopySelection(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    target: MultipleCopySelectionAccumulator
) {
    restoreMultipleCopySelection(
        scene = scene,
        group = group,
        snapshot = MultipleCopySelectionSnapshot(
            faces = target.faces.toList(),
            edges = target.edges.toList(),
            dimensions = target.dimensions.toList(),
            texts = target.texts.toList(),
            groups = target.groups.toList(),
            architecture = target.architecture.toList(),
            hvac = target.hvac.toList(),
            voxels = target.voxels.toList()
        )
    )
}

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

private fun transformSelectedVoxelsWithLocalTransform(
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
    entries.forEach { (key, _) ->
        store.remove(key.x, key.y, key.z)
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
    val a = projectOntoPlane(from, n)
    val b = projectOntoPlane(to, n)
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

private fun projectOntoPlane(vector: Vector3, planeNormal: Vector3): Vector3 {
    val n = Vector3(planeNormal)
    if (n.len2() <= MULTIPLE_COPY_EPS) {
        return Vector3()
    }
    n.nor()
    return Vector3(vector).mulAdd(n, -vector.dot(n))
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

private fun renderTranslatedMultiplePreview(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    renderer: ShapeRenderer,
    offsetsWorld: List<Vector3>,
    offsetsLocal: List<Vector3>
) {
    if (offsetsWorld.isEmpty() || offsetsLocal.isEmpty()) {
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

    offsetsWorld.zip(offsetsLocal).forEach { (offsetWorld, offsetLocal) ->
        selectedFaces.forEach { tri ->
            val a = group.toWorld(Vector3(tri.a).add(offsetLocal))
            val b = group.toWorld(Vector3(tri.b).add(offsetLocal))
            val c = group.toWorld(Vector3(tri.c).add(offsetLocal))
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            renderer.line(b.x, b.y, b.z, c.x, c.y, c.z)
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
        }
        selectedEdges.forEach { segment ->
            val a = group.toWorld(Vector3(segment.start).add(offsetLocal))
            val b = group.toWorld(Vector3(segment.end).add(offsetLocal))
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
        selectedDims.forEach { dim ->
            val a = group.toWorld(Vector3(dim.start).add(offsetLocal))
            val b = group.toWorld(Vector3(dim.end).add(offsetLocal))
            val o = group.toWorld(Vector3(dim.offset).add(offsetLocal))
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            renderer.line(a.x, a.y, a.z, o.x, o.y, o.z)
            renderer.line(b.x, b.y, b.z, o.x, o.y, o.z)
        }
        selectedTexts.forEach { text ->
            val p = group.toWorld(Vector3(text.position).add(offsetLocal))
            drawCross(renderer, p, 0.12f)
        }
        selectedGroups.forEach { selectedGroup ->
            val corners = selectedGroup.orientedBoundsCorners() ?: return@forEach
            val transformed = Array(corners.size) { idx -> Vector3(corners[idx]).add(offsetWorld) }
            drawWireBox(renderer, transformed)
        }
        if (architectureBounds != null) {
            val corners = boundingCorners(architectureBounds)
            val transformed = Array(corners.size) { idx -> Vector3(corners[idx]).add(offsetWorld) }
            drawWireBox(renderer, transformed)
        }
        if (hvacBounds != null) {
            val corners = boundingCorners(hvacBounds)
            val transformed = Array(corners.size) { idx -> Vector3(corners[idx]).add(offsetWorld) }
            drawWireBox(renderer, transformed)
        }
        if (voxelBoundsCorners.isNotEmpty()) {
            val transformed = Array(voxelBoundsCorners.size) { idx ->
                val local = Vector3(voxelBoundsCorners[idx]).add(offsetLocal)
                Vector3(local.x.roundToInt().toFloat(), local.y.roundToInt().toFloat(), local.z.roundToInt().toFloat())
            }.map { group.toWorld(it) }.toTypedArray()
            drawWireBox(renderer, transformed)
        }
    }
}

private fun buildMultipleCopyStatus(label: String, copies: Int, capped: Boolean, total: MultipleCopyCounts): String {
    return buildString {
        append(label).append(": copies ").append(copies)
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
}

private fun buildTransformStatus(label: String, total: MultipleCopyCounts): String {
    return buildString {
        append(label)
        append(" | architecture ").append(total.architecture)
        append(" hvac ").append(total.hvac)
        append(" voxels ").append(total.voxels)
        append(" edges ").append(total.edges)
        append(" faces ").append(total.faces)
        append(" dims ").append(total.dimensions)
        append(" texts ").append(total.texts)
        append(" groups ").append(total.groups)
    }
}

private data class EqualizedCircularLayout(
    val stepAngle: Float,
    val positionCount: Int,
    val capped: Boolean
)

private fun collectSelectedWorldPoints(
    scene: GroupScene,
    group: GroupScene.GroupNode
): List<Vector3> {
    val points = ArrayList<Vector3>()
    group.faceStore.getSelected().forEach { triangle ->
        points += group.toWorld(triangle.a)
        points += group.toWorld(triangle.b)
        points += group.toWorld(triangle.c)
    }
    group.lineStore.getSelected().forEach { segment ->
        points += group.toWorld(segment.start)
        points += group.toWorld(segment.end)
    }
    group.dimensionStore.getSelected().forEach { dimension ->
        points += group.toWorld(dimension.start)
        points += group.toWorld(dimension.end)
        points += group.toWorld(dimension.offset)
    }
    group.textStore.getSelected().forEach { text ->
        points += group.toWorld(text.position)
    }
    scene.selectedGroups().forEach { selectedGroup ->
        selectedGroup.orientedBoundsCorners()?.forEach { corner ->
            points += Vector3(corner)
        }
    }
    scene.selectedArchitectureBounds(group)?.let { bounds ->
        boundingCorners(bounds).forEach { corner -> points += Vector3(corner) }
    }
    scene.selectedHvacBounds(group)?.let { bounds ->
        boundingCorners(bounds).forEach { corner -> points += Vector3(corner) }
    }
    if (scene.isVoxelGroup(group)) {
        val selectedVoxels = scene.selectedVoxels(group).toList()
        if (selectedVoxels.isNotEmpty()) {
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
            ).forEach { corner ->
                points += group.toWorld(corner)
            }
        }
    }
    return points
}

private fun projectedSelectionSpanWorld(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    directionWorld: Vector3
): Float {
    if (directionWorld.len2() <= MULTIPLE_COPY_EPS) {
        return 0f
    }
    val direction = Vector3(directionWorld).nor()
    val points = collectSelectedWorldPoints(scene, group)
    if (points.isEmpty()) {
        return 0f
    }
    var minProjection = Float.POSITIVE_INFINITY
    var maxProjection = Float.NEGATIVE_INFINITY
    points.forEach { point ->
        val projection = direction.dot(point)
        minProjection = kotlin.math.min(minProjection, projection)
        maxProjection = kotlin.math.max(maxProjection, projection)
    }
    return maxProjection - minProjection
}

private fun resolveEqualizedCircularLayout(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    centerWorld: Vector3,
    referenceWorld: Vector3,
    axisWorld: Vector3,
    fallbackStepAngle: Float
): EqualizedCircularLayout? {
    if (abs(fallbackStepAngle) <= 1e-4f) {
        return null
    }
    val refProjected = projectOntoPlane(Vector3(referenceWorld).sub(centerWorld), axisWorld)
    val radius = refProjected.len()
    if (radius <= MULTIPLE_COPY_EPS) {
        return null
    }
    val tangent = Vector3(axisWorld).crs(refProjected)
    if (tangent.len2() <= MULTIPLE_COPY_EPS) {
        return null
    }
    tangent.nor()
    val span = projectedSelectionSpanWorld(scene, group, tangent)
    val fallbackPositions = (360f / abs(fallbackStepAngle)).roundToInt().coerceAtLeast(2)
    val rawPositionCount = if (span <= MULTIPLE_COPY_EPS) {
        fallbackPositions
    } else {
        val ratio = (span / (2f * radius)).coerceIn(0f, 1f)
        if (ratio <= MULTIPLE_COPY_EPS) {
            fallbackPositions
        } else {
            (MathUtils.PI / MathUtils.asin(ratio)).roundToInt().coerceAtLeast(2)
        }
    }
    val capped = rawPositionCount > MAX_MULTIPLE_COPY_STEPS
    val positionCount = rawPositionCount.coerceAtMost(MAX_MULTIPLE_COPY_STEPS)
    val direction = if (fallbackStepAngle >= 0f) 1f else -1f
    return EqualizedCircularLayout(
        stepAngle = 360f * direction / positionCount.toFloat(),
        positionCount = positionCount,
        capped = capped
    )
}

private fun buildEqualizedMultipleCopyStatus(
    label: String,
    copies: Int,
    capped: Boolean,
    total: MultipleCopyCounts,
    positionCount: Int?
): String {
    val base = buildMultipleCopyStatus(label, copies, capped, total)
    return if (positionCount == null) base else "$base | positions $positionCount"
}

private fun applyRotationalCopyStep(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    centerWorld: Vector3,
    axisWorld: Vector3,
    degrees: Float,
    liftWorld: Float
): MultipleCopyCounts {
    val centerLocal = group.toLocal(centerWorld)
    val axisLocal = group.vectorToLocal(axisWorld).nor()
    val localLift = group.vectorToLocal(Vector3(axisWorld).scl(liftWorld))
    val rotationWorld = Quaternion().setFromAxis(axisWorld, degrees)
    val rotationLocal = Quaternion().setFromAxis(axisLocal, degrees)
    return applyCopyStep(
        scene = scene,
        group = group,
        pointTransformWorld = { point ->
            Vector3(point).sub(centerWorld).mul(rotationWorld).add(centerWorld).mulAdd(axisWorld, liftWorld)
        },
        vectorTransformWorld = { vector -> Vector3(vector).mul(rotationWorld) },
        pointTransformLocal = { point ->
            Vector3(point).sub(centerLocal).mul(rotationLocal).add(centerLocal).add(localLift)
        },
        pointTransformVoxelLocal = { point ->
            Vector3(point).sub(centerLocal).mul(rotationLocal).add(centerLocal).add(localLift)
        }
    )
}

private fun applyRotationalTransform(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    centerWorld: Vector3,
    axisWorld: Vector3,
    degrees: Float,
    liftWorld: Float
): MultipleCopyCounts {
    val total = MultipleCopyCounts()
    val centerLocal = group.toLocal(centerWorld)
    val axisLocal = group.vectorToLocal(axisWorld).nor()
    val localLift = group.vectorToLocal(Vector3(axisWorld).scl(liftWorld))
    val rotationWorld = Quaternion().setFromAxis(axisWorld, degrees)
    val rotationLocal = Quaternion().setFromAxis(axisLocal, degrees)
    val pointTransformWorld: (Vector3) -> Vector3 = { point ->
        Vector3(point).sub(centerWorld).mul(rotationWorld).add(centerWorld).mulAdd(axisWorld, liftWorld)
    }
    val vectorTransformWorld: (Vector3) -> Vector3 = { vector -> Vector3(vector).mul(rotationWorld) }
    val pointTransformLocal: (Vector3) -> Vector3 = { point ->
        Vector3(point).sub(centerLocal).mul(rotationLocal).add(centerLocal).add(localLift)
    }
    val vectorTransformLocal: (Vector3) -> Vector3 = { vector -> Vector3(vector).mul(rotationLocal) }
    total.architecture = scene.transformSelectedArchitectureElements(
        group = group,
        pointTransform = pointTransformWorld,
        vectorTransform = vectorTransformWorld
    )
    total.hvac = scene.transformSelectedHvacElements(
        group = group,
        pointTransform = pointTransformWorld
    )
    total.faces = group.faceStore.transformSelected(pointTransformLocal)
    total.edges = group.lineStore.transformSelected(pointTransformLocal)
    total.dimensions = group.dimensionStore.transformSelected(pointTransformLocal)
    total.texts = group.textStore.transformSelectedAdvanced(pointTransformLocal, vectorTransformLocal)
    total.groups = scene.transformSelectedGroups(pointTransformWorld, vectorTransformWorld)
    total.voxels = transformSelectedVoxelsWithLocalTransform(scene, group, pointTransformLocal)
    return total
}

private fun renderRotationalPreview(
    scene: GroupScene,
    group: GroupScene.GroupNode,
    renderer: ShapeRenderer,
    previewCopies: Int,
    centerWorld: Vector3,
    axisWorld: Vector3,
    degreesPerStep: Float,
    liftWorldPerStep: Float
) {
    val centerLocal = group.toLocal(centerWorld)
    val axisLocal = group.vectorToLocal(axisWorld).nor()
    val localLiftStep = group.vectorToLocal(Vector3(axisWorld).scl(liftWorldPerStep))
    renderMultiplePreview(
        scene = scene,
        group = group,
        renderer = renderer,
        previewCopies = previewCopies,
        localPointTransformAtStep = { step, point ->
            val q = Quaternion().setFromAxis(axisLocal, degreesPerStep * step.toFloat())
            Vector3(point)
                .sub(centerLocal)
                .mul(q)
                .add(centerLocal)
                .mulAdd(localLiftStep, step.toFloat())
        },
        worldPointTransformAtStep = { step, point ->
            val q = Quaternion().setFromAxis(axisWorld, degreesPerStep * step.toFloat())
            Vector3(point)
                .sub(centerWorld)
                .mul(q)
                .add(centerWorld)
                .mulAdd(axisWorld, liftWorldPerStep * step.toFloat())
        }
    )
}

private fun axisCopyCount(step: Float, span: Float): Int {
    if (abs(step) <= MULTIPLE_COPY_EPS) {
        return 0
    }
    val ratio = span / step
    if (ratio <= 0f) {
        return 0
    }
    return floor(abs(ratio)).toInt()
}

private fun copyCountLabels(copies: Int, capped: Boolean = false): List<ToolMeasurementLabel> {
    if (copies <= 0) {
        return emptyList()
    }
    val countText = if (capped) "$copies+" else copies.toString()
    return listOf(ToolMeasurementLabel("Copies", text = countText))
}

private fun linearCopyCount(reference: Vector3, measurePoint: Vector3, finalPoint: Vector3): Int {
    val stepWorld = Vector3(measurePoint).sub(reference)
    val stepLength = stepWorld.len()
    if (stepLength <= MULTIPLE_COPY_EPS) {
        return 0
    }
    val direction = Vector3(stepWorld).scl(1f / stepLength)
    val spanProjected = Vector3(finalPoint).sub(reference).dot(direction)
    if (spanProjected <= MULTIPLE_COPY_EPS) {
        return 0
    }
    return floor(spanProjected / stepLength).toInt().coerceAtLeast(0)
}

private fun planarTranslateCopyCount(origin: Vector3, measure: Vector3, finalPoint: Vector3): Int {
    val nx = axisCopyCount(measure.x - origin.x, finalPoint.x - origin.x)
    val nz = axisCopyCount(measure.z - origin.z, finalPoint.z - origin.z)
    return ((nx + 1) * (nz + 1) - 1).coerceAtLeast(0)
}

private fun volumetricTranslateCopyCount(origin: Vector3, measure: Vector3, finalPoint: Vector3): Int {
    val nx = axisCopyCount(measure.x - origin.x, finalPoint.x - origin.x)
    val ny = axisCopyCount(measure.y - origin.y, finalPoint.y - origin.y)
    val nz = axisCopyCount(measure.z - origin.z, finalPoint.z - origin.z)
    return ((nx + 1) * (ny + 1) * (nz + 1) - 1).coerceAtLeast(0)
}

private fun cappedCopyOffsets(
    offsetsWorld: List<Vector3>,
    group: GroupScene.GroupNode
): Triple<List<Vector3>, List<Vector3>, Boolean> {
    val capped = offsetsWorld.size > MAX_MULTIPLE_COPY_STEPS
    val limitedWorld = if (capped) offsetsWorld.take(MAX_MULTIPLE_COPY_STEPS) else offsetsWorld
    val limitedLocal = limitedWorld.map { offset -> group.vectorToLocal(offset) }
    return Triple(limitedWorld, limitedLocal, capped)
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
        val directionPoint = v ?: return false
        val finalPoint = Vector3(world)
        val stepWorld = Vector3(directionPoint).sub(ref)
        val stepLength = stepWorld.len()
        if (stepLength <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Copy multiple canceled: measure is too short."
            return true
        }
        val direction = Vector3(stepWorld).scl(1f / stepLength)
        val spanProjected = Vector3(finalPoint).sub(ref).dot(direction)
        if (spanProjected <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Copy multiple canceled: final point does not extend in the measure direction."
            return true
        }
        val group = scene.activeGroup()
        val (offsetsWorld, offsetsLocal, capped) = cappedCopyOffsets(
            offsetsWorld = (1..floor(spanProjected / stepLength).toInt()).map { step ->
                Vector3(stepWorld).scl(step.toFloat())
            },
            group = group
        )
        if (offsetsWorld.isEmpty()) {
            clearTransient()
            status.message = "Copy multiple: measure does not fit into span."
            return true
        }
        val originalSelection = snapshotMultipleCopySelection(scene, group)
        val generatedSelection = MultipleCopySelectionAccumulator()
        val total = MultipleCopyCounts()
        offsetsWorld.zip(offsetsLocal).forEach { (offsetWorld, offsetLocal) ->
            restoreMultipleCopySelection(scene, group, originalSelection)
            val stepCounts = applyCopyStep(
                scene = scene,
                group = group,
                pointTransformWorld = { point -> Vector3(point).add(offsetWorld) },
                vectorTransformWorld = { vector -> Vector3(vector) },
                pointTransformLocal = { point -> Vector3(point).add(offsetLocal) },
                pointTransformVoxelLocal = { point -> Vector3(point).add(offsetLocal) }
            )
            collectMultipleCopySelection(scene, group, generatedSelection)
            mergeCounts(total, stepCounts)
        }
        applyMultipleCopySelection(scene, group, generatedSelection)
        status.message = buildMultipleCopyStatus("Copy multiple", offsetsWorld.size, capped, total)
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
                val stepWorld = Vector3(sizePoint).sub(ref)
                val stepLength = stepWorld.len()
                if (stepLength > MULTIPLE_COPY_EPS) {
                    val direction = Vector3(stepWorld).scl(1f / stepLength)
                    val spanProjected = Vector3(hover).sub(ref).dot(direction)
                    val previewCopies = floor(spanProjected / stepLength).toInt()
                        .coerceAtLeast(0)
                        .coerceAtMost(MAX_MULTIPLE_COPY_PREVIEW_STEPS)
                    if (previewCopies > 0) {
                        val group = scene.activeGroup()
                        val offsetsWorld = (1..previewCopies).map { step -> Vector3(stepWorld).scl(step.toFloat()) }
                        val offsetsLocal = offsetsWorld.map { offset -> group.vectorToLocal(offset) }
                        renderTranslatedMultiplePreview(scene, group, renderer, offsetsWorld, offsetsLocal)
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
        val copies = v?.let { linearCopyCount(ref, it, hover) } ?: 0
        return ToolMeasurement(
            startWorld = Vector3(ref),
            endWorld = Vector3(hover),
            extraLabels = copyCountLabels(copies, copies > MAX_MULTIPLE_COPY_STEPS)
        )
    }

    private fun clearTransient() {
        u = null
        v = null
        hasHover = false
    }
}

class PlanarTranslateMultipleTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.PLANAR_TRANSLATE_MULTIPLE
    override val message: String = "Pick origin point (o)."

    private var origin: Vector3? = null
    private var measure: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Pick origin point (o)."
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
        if (origin == null) {
            origin = Vector3(world)
            status.message = "Pick planar measure point (a)."
            return true
        }
        if (measure == null) {
            measure = Vector3(world)
            status.message = "Pick planar final point (b)."
            return true
        }

        val o = origin ?: return false
        val a = measure ?: return false
        val b = Vector3(world)
        val stepX = a.x - o.x
        val stepZ = a.z - o.z
        val spanX = b.x - o.x
        val spanZ = b.z - o.z
        val nx = axisCopyCount(stepX, spanX)
        val nz = axisCopyCount(stepZ, spanZ)
        val offsetsWorld = mutableListOf<Vector3>()
        loop@ for (ix in 0..nx) {
            for (iz in 0..nz) {
                if (ix == 0 && iz == 0) continue
                if (offsetsWorld.size >= MAX_MULTIPLE_COPY_STEPS) break@loop
                offsetsWorld.add(Vector3(stepX * ix.toFloat(), 0f, stepZ * iz.toFloat()))
            }
        }
        if (offsetsWorld.isEmpty()) {
            clearTransient()
            status.message = "Planar translate multiple: no copies fit inside the measured span."
            return true
        }
        val group = scene.activeGroup()
        val originalSelection = snapshotMultipleCopySelection(scene, group)
        val generatedSelection = MultipleCopySelectionAccumulator()
        val offsetsLocal = offsetsWorld.map { offset -> group.vectorToLocal(offset) }
        val total = MultipleCopyCounts()
        offsetsWorld.zip(offsetsLocal).forEach { (offsetWorld, offsetLocal) ->
            restoreMultipleCopySelection(scene, group, originalSelection)
            val stepCounts = applyCopyStep(
                scene = scene,
                group = group,
                pointTransformWorld = { point -> Vector3(point).add(offsetWorld) },
                vectorTransformWorld = { vector -> Vector3(vector) },
                pointTransformLocal = { point -> Vector3(point).add(offsetLocal) },
                pointTransformVoxelLocal = { point -> Vector3(point).add(offsetLocal) }
            )
            collectMultipleCopySelection(scene, group, generatedSelection)
            mergeCounts(total, stepCounts)
        }
        applyMultipleCopySelection(scene, group, generatedSelection)
        val capped = (nx + 1) * (nz + 1) - 1 > offsetsWorld.size
        status.message = buildMultipleCopyStatus("Planar translate multiple", offsetsWorld.size, capped, total)
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val o = origin ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, o, 0.18f)
        measure?.let { a ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, a, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(o.x, o.y, o.z, a.x, a.y, a.z)
        }
        if (hasHover) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, hover, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(o.x, o.y, o.z, hover.x, hover.y, hover.z)
            val a = measure
            if (a != null) {
                val stepX = a.x - o.x
                val stepZ = a.z - o.z
                val spanX = hover.x - o.x
                val spanZ = hover.z - o.z
                val nx = axisCopyCount(stepX, spanX)
                val nz = axisCopyCount(stepZ, spanZ)
                val group = scene.activeGroup()
                val offsetsWorld = mutableListOf<Vector3>()
                loop@ for (ix in 0..nx) {
                    for (iz in 0..nz) {
                        if (ix == 0 && iz == 0) continue
                        if (offsetsWorld.size >= MAX_MULTIPLE_COPY_PREVIEW_STEPS) break@loop
                        offsetsWorld.add(Vector3(stepX * ix.toFloat(), 0f, stepZ * iz.toFloat()))
                    }
                }
                if (offsetsWorld.isNotEmpty()) {
                    val offsetsLocal = offsetsWorld.map { offset -> group.vectorToLocal(offset) }
                    renderTranslatedMultiplePreview(scene, group, renderer, offsetsWorld, offsetsLocal)
                }
            }
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val o = origin ?: return null
        if (!hasHover) {
            return null
        }
        val copies = measure?.let { planarTranslateCopyCount(o, it, hover) } ?: 0
        return ToolMeasurement(
            startWorld = Vector3(o),
            endWorld = Vector3(hover),
            extraLabels = copyCountLabels(copies.coerceAtMost(MAX_MULTIPLE_COPY_STEPS), copies > MAX_MULTIPLE_COPY_STEPS)
        )
    }

    private fun clearTransient() {
        origin = null
        measure = null
        hasHover = false
    }
}

class VolumetricTranslateMultipleTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.VOLUMETRIC_TRANSLATE_MULTIPLE
    override val message: String = "Pick origin point (o)."

    private var origin: Vector3? = null
    private var measure: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Pick origin point (o)."
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
        if (origin == null) {
            origin = Vector3(world)
            status.message = "Pick volumetric measure point (a)."
            return true
        }
        if (measure == null) {
            measure = Vector3(world)
            status.message = "Pick volumetric final point (b)."
            return true
        }

        val o = origin ?: return false
        val a = measure ?: return false
        val b = Vector3(world)
        val stepX = a.x - o.x
        val stepY = a.y - o.y
        val stepZ = a.z - o.z
        val spanX = b.x - o.x
        val spanY = b.y - o.y
        val spanZ = b.z - o.z
        val nx = axisCopyCount(stepX, spanX)
        val ny = axisCopyCount(stepY, spanY)
        val nz = axisCopyCount(stepZ, spanZ)
        val offsetsWorld = mutableListOf<Vector3>()
        loop@ for (ix in 0..nx) {
            for (iy in 0..ny) {
                for (iz in 0..nz) {
                    if (ix == 0 && iy == 0 && iz == 0) continue
                    if (offsetsWorld.size >= MAX_MULTIPLE_COPY_STEPS) break@loop
                    offsetsWorld.add(Vector3(stepX * ix.toFloat(), stepY * iy.toFloat(), stepZ * iz.toFloat()))
                }
            }
        }
        if (offsetsWorld.isEmpty()) {
            clearTransient()
            status.message = "Volumetric translate multiple: no copies fit inside the measured span."
            return true
        }
        val group = scene.activeGroup()
        val originalSelection = snapshotMultipleCopySelection(scene, group)
        val generatedSelection = MultipleCopySelectionAccumulator()
        val offsetsLocal = offsetsWorld.map { offset -> group.vectorToLocal(offset) }
        val total = MultipleCopyCounts()
        offsetsWorld.zip(offsetsLocal).forEach { (offsetWorld, offsetLocal) ->
            restoreMultipleCopySelection(scene, group, originalSelection)
            val stepCounts = applyCopyStep(
                scene = scene,
                group = group,
                pointTransformWorld = { point -> Vector3(point).add(offsetWorld) },
                vectorTransformWorld = { vector -> Vector3(vector) },
                pointTransformLocal = { point -> Vector3(point).add(offsetLocal) },
                pointTransformVoxelLocal = { point -> Vector3(point).add(offsetLocal) }
            )
            collectMultipleCopySelection(scene, group, generatedSelection)
            mergeCounts(total, stepCounts)
        }
        applyMultipleCopySelection(scene, group, generatedSelection)
        val capped = (nx + 1) * (ny + 1) * (nz + 1) - 1 > offsetsWorld.size
        status.message = buildMultipleCopyStatus("Volumetric translate multiple", offsetsWorld.size, capped, total)
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val o = origin ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, o, 0.18f)
        measure?.let { a ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, a, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(o.x, o.y, o.z, a.x, a.y, a.z)
        }
        if (hasHover) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, hover, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(o.x, o.y, o.z, hover.x, hover.y, hover.z)
            val a = measure
            if (a != null) {
                val stepX = a.x - o.x
                val stepY = a.y - o.y
                val stepZ = a.z - o.z
                val spanX = hover.x - o.x
                val spanY = hover.y - o.y
                val spanZ = hover.z - o.z
                val nx = axisCopyCount(stepX, spanX)
                val ny = axisCopyCount(stepY, spanY)
                val nz = axisCopyCount(stepZ, spanZ)
                val group = scene.activeGroup()
                val offsetsWorld = mutableListOf<Vector3>()
                loop@ for (ix in 0..nx) {
                    for (iy in 0..ny) {
                        for (iz in 0..nz) {
                            if (ix == 0 && iy == 0 && iz == 0) continue
                            if (offsetsWorld.size >= MAX_MULTIPLE_COPY_PREVIEW_STEPS) break@loop
                            offsetsWorld.add(Vector3(stepX * ix.toFloat(), stepY * iy.toFloat(), stepZ * iz.toFloat()))
                        }
                    }
                }
                if (offsetsWorld.isNotEmpty()) {
                    val offsetsLocal = offsetsWorld.map { offset -> group.vectorToLocal(offset) }
                    renderTranslatedMultiplePreview(scene, group, renderer, offsetsWorld, offsetsLocal)
                }
            }
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val o = origin ?: return null
        if (!hasHover) {
            return null
        }
        val copies = measure?.let { volumetricTranslateCopyCount(o, it, hover) } ?: 0
        return ToolMeasurement(
            startWorld = Vector3(o),
            endWorld = Vector3(hover),
            extraLabels = copyCountLabels(copies.coerceAtMost(MAX_MULTIPLE_COPY_STEPS), copies > MAX_MULTIPLE_COPY_STEPS)
        )
    }

    private fun clearTransient() {
        origin = null
        measure = null
        hasHover = false
    }
}

abstract class BaseRotateMultipleTool(
    protected val scene: GroupScene,
    private val helicoidal: Boolean
) : Tool {
    private var center: Vector3? = null
    private var reference: Vector3? = null
    private var increment: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    protected open fun centerPrompt(): String = "Pick rotation center (c)."
    protected open fun referencePrompt(): String = "Pick reference point (u)."
    protected open fun incrementPrompt(): String = "Pick increment point (v)."
    protected open fun finalPrompt(): String = "Pick final point (w)."
    protected open fun resultLabel(): String =
        if (helicoidal) "Helicoidal rotate multiple" else "Planar rotate multiple"

    override fun onEnter(status: StatusModel) {
        status.message = centerPrompt()
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
            status.message = referencePrompt()
            return true
        }
        if (reference == null) {
            reference = Vector3(world)
            status.message = incrementPrompt()
            return true
        }
        if (increment == null) {
            increment = Vector3(world)
            status.message = finalPrompt()
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

        val refProjected = projectOntoPlane(ref, normalAxis)
        val incProjected = projectOntoPlane(inc, normalAxis)
        val endProjected = projectOntoPlane(end, normalAxis)
        if (refProjected.len2() <= MULTIPLE_COPY_EPS ||
            incProjected.len2() <= MULTIPLE_COPY_EPS ||
            endProjected.len2() <= MULTIPLE_COPY_EPS
        ) {
            clearTransient()
            status.message = "Rotate multiple canceled: projected vectors are too short."
            return true
        }

        val stepAngle = signedAngleDeg(refProjected, incProjected, normalAxis)
        if (abs(stepAngle) <= 1e-4f) {
            clearTransient()
            status.message = "Rotate multiple canceled: angular increment is zero."
            return true
        }
        val rawSweep = signedAngleDeg(refProjected, endProjected, normalAxis)
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
        val total = MultipleCopyCounts()
        repeat(copies) {
            val stepCounts = applyRotationalCopyStep(
                scene = scene,
                group = group,
                centerWorld = c,
                axisWorld = normalAxis,
                degrees = stepAngle,
                liftWorld = perStepLiftWorld
            )
            mergeCounts(total, stepCounts)
        }

        status.message = buildMultipleCopyStatus(resultLabel(), copies, capped, total)
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
                        val refProjected = projectOntoPlane(ref, normalAxis)
                        val incProjected = projectOntoPlane(inc, normalAxis)
                        val endProjected = projectOntoPlane(end, normalAxis)
                        if (refProjected.len2() > MULTIPLE_COPY_EPS &&
                            incProjected.len2() > MULTIPLE_COPY_EPS &&
                            endProjected.len2() > MULTIPLE_COPY_EPS
                        ) {
                            val stepAngle = signedAngleDeg(refProjected, incProjected, normalAxis)
                            if (abs(stepAngle) > 1e-4f) {
                                val direction = if (stepAngle >= 0f) 1f else -1f
                                val rawSweep = signedAngleDeg(refProjected, endProjected, normalAxis)
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
                                        renderRotationalPreview(
                                            scene = scene,
                                            group = scene.activeGroup(),
                                            renderer = renderer,
                                            previewCopies = previewCopies,
                                            centerWorld = c,
                                            axisWorld = normalAxis,
                                            degreesPerStep = stepAngle,
                                            liftWorldPerStep = perStepLiftWorld
                                        )
                                    }
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
        val copies = resolveProspectiveCopies(hover)
        return ToolMeasurement(
            startWorld = Vector3(c),
            endWorld = Vector3(hover),
            extraLabels = copyCountLabels(
                copies?.coerceAtMost(MAX_MULTIPLE_COPY_STEPS) ?: 0,
                (copies ?: 0) > MAX_MULTIPLE_COPY_STEPS
            )
        )
    }

    private fun resolveProspectiveCopies(finalPoint: Vector3): Int? {
        val c = center ?: return null
        val u = reference ?: return null
        val v = increment ?: return null
        val ref = Vector3(u).sub(c)
        val inc = Vector3(v).sub(c)
        val end = Vector3(finalPoint).sub(c)
        if (ref.len2() <= MULTIPLE_COPY_EPS || inc.len2() <= MULTIPLE_COPY_EPS || end.len2() <= MULTIPLE_COPY_EPS) {
            return null
        }
        val normalAxis = Vector3(ref).crs(inc)
        if (normalAxis.len2() <= MULTIPLE_COPY_EPS) {
            return null
        }
        normalAxis.nor()
        val refProjected = projectOntoPlane(ref, normalAxis)
        val incProjected = projectOntoPlane(inc, normalAxis)
        val endProjected = projectOntoPlane(end, normalAxis)
        if (refProjected.len2() <= MULTIPLE_COPY_EPS ||
            incProjected.len2() <= MULTIPLE_COPY_EPS ||
            endProjected.len2() <= MULTIPLE_COPY_EPS
        ) {
            return null
        }
        val stepAngle = signedAngleDeg(refProjected, incProjected, normalAxis)
        if (abs(stepAngle) <= 1e-4f) {
            return null
        }
        val rawSweep = signedAngleDeg(refProjected, endProjected, normalAxis)
        val direction = if (stepAngle >= 0f) 1f else -1f
        var sweep = normalizeSignedSweep(rawSweep, direction)
        if (abs(sweep) <= 1e-4f) {
            sweep = 360f * direction
        }
        if (sweep * direction <= 0f) {
            return null
        }
        var copies = floor(abs(sweep) / abs(stepAngle)).toInt()
        val totalLiftWorld = if (helicoidal) {
            end.dot(normalAxis) - ref.dot(normalAxis)
        } else {
            0f
        }
        val perStepLiftWorld = if (helicoidal && copies > 0) totalLiftWorld / copies.toFloat() else 0f
        if (abs(abs(sweep) - 360f) <= 1e-3f && abs(perStepLiftWorld) <= 1e-6f && copies > 1) {
            copies -= 1
        }
        return copies.coerceAtLeast(0)
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

class RotationalArrayTool(
    scene: GroupScene
) : BaseRotateMultipleTool(scene, helicoidal = false) {
    override val id: ToolId = ToolId.ROTATIONAL_ARRAY
    override val message: String = "Pick rotation center (c)."
    override fun referencePrompt(): String = "Pick first reference point (a)."
    override fun incrementPrompt(): String = "Pick increment point (b)."
    override fun finalPrompt(): String = "Pick final sweep point (d)."
    override fun resultLabel(): String = "Rotational array"
}

class Rotate2Tool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.ROTATE_2
    override val message: String = "Pick rotation center (c)."

    private var center: Vector3? = null
    private var reference: Vector3? = null
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

    override fun supportsCopyMode(): Boolean = true

    override fun onCopyModeChanged(status: StatusModel, enabled: Boolean) {
        status.message = when {
            center == null -> "Pick rotation center (c)."
            reference == null -> "Pick first reference point (a)."
            else -> "Pick end angle point (b)."
        }
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
            status.message = "Pick first reference point (a)."
            return true
        }
        if (reference == null) {
            reference = Vector3(world)
            status.message = "Pick end angle point (b)."
            return true
        }

        val c = center ?: return false
        val a = reference ?: return false
        val b = Vector3(world)
        val ref = Vector3(a).sub(c)
        val end = Vector3(b).sub(c)
        if (ref.len2() <= MULTIPLE_COPY_EPS || end.len2() <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Rotate 2 canceled: vectors are too short."
            return true
        }
        val axisWorld = Vector3(ref).crs(end)
        if (axisWorld.len2() <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Rotate 2 canceled: c/a/b are collinear."
            return true
        }
        axisWorld.nor()
        val refProjected = projectOntoPlane(ref, axisWorld)
        val endProjected = projectOntoPlane(end, axisWorld)
        if (refProjected.len2() <= MULTIPLE_COPY_EPS || endProjected.len2() <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Rotate 2 canceled: projected vectors are too short."
            return true
        }
        val degrees = signedAngleDeg(refProjected, endProjected, axisWorld)
        if (abs(degrees) <= 1e-4f) {
            clearTransient()
            status.message = "Rotate 2 canceled: rotation angle is zero."
            return true
        }
        val group = scene.activeGroup()
        val total = if (status.copyMode) {
            applyRotationalCopyStep(scene, group, c, axisWorld, degrees, 0f)
        } else {
            applyRotationalTransform(scene, group, c, axisWorld, degrees, 0f)
        }
        status.message = buildTransformStatus(
            if (status.copyMode) "Rotate 2 copied" else "Rotate 2 rotated",
            total
        )
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val c = center ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, c, 0.18f)
        reference?.let { a ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, a, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
        }
        if (hasHover) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, hover, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, hover.x, hover.y, hover.z)
            val a = reference
            if (a != null) {
                val ref = Vector3(a).sub(c)
                val end = Vector3(hover).sub(c)
                if (ref.len2() > MULTIPLE_COPY_EPS && end.len2() > MULTIPLE_COPY_EPS) {
                    val axisWorld = Vector3(ref).crs(end)
                    if (axisWorld.len2() > MULTIPLE_COPY_EPS) {
                        axisWorld.nor()
                        val refProjected = projectOntoPlane(ref, axisWorld)
                        val endProjected = projectOntoPlane(end, axisWorld)
                        if (refProjected.len2() > MULTIPLE_COPY_EPS && endProjected.len2() > MULTIPLE_COPY_EPS) {
                            val degrees = signedAngleDeg(refProjected, endProjected, axisWorld)
                            if (abs(degrees) > 1e-4f) {
                                renderRotationalPreview(
                                    scene = scene,
                                    group = scene.activeGroup(),
                                    renderer = renderer,
                                    previewCopies = 1,
                                    centerWorld = c,
                                    axisWorld = axisWorld,
                                    degreesPerStep = degrees,
                                    liftWorldPerStep = 0f
                                )
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
        return ToolMeasurement(
            startWorld = Vector3(c),
            endWorld = Vector3(hover)
        )
    }

    private fun clearTransient() {
        center = null
        reference = null
        hasHover = false
    }
}

class HelicalArrayTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.HELICAL_ARRAY
    override val message: String = "Pick rotation center (c)."

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
            status.message = "Pick first reference point (a)."
            return true
        }
        if (reference == null) {
            reference = Vector3(world)
            status.message = "Pick helical unit point (b)."
            return true
        }
        if (increment == null) {
            increment = Vector3(world)
            status.message = "Pick final point (d)."
            return true
        }

        val plan = resolveHelicalPlan(Vector3(world))
        if (plan == null) {
            clearTransient()
            status.message = "Helical array canceled: invalid helical definition."
            return true
        }
        val group = scene.activeGroup()
        val total = MultipleCopyCounts()
        repeat(plan.copies) {
            val stepCounts = applyRotationalCopyStep(
                scene = scene,
                group = group,
                centerWorld = plan.centerWorld,
                axisWorld = plan.axisWorld,
                degrees = plan.stepAngle,
                liftWorld = plan.stepLiftWorld
            )
            mergeCounts(total, stepCounts)
        }
        status.message = buildMultipleCopyStatus("Helical array", plan.copies, plan.capped, total)
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val c = center ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, c, 0.18f)
        reference?.let { a ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, a, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
        }
        increment?.let { b ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, b, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, b.x, b.y, b.z)
        }
        if (hasHover) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, hover, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, hover.x, hover.y, hover.z)
            val plan = resolveHelicalPlan(hover) ?: return
            renderRotationalPreview(
                scene = scene,
                group = scene.activeGroup(),
                renderer = renderer,
                previewCopies = plan.copies.coerceAtMost(MAX_MULTIPLE_COPY_PREVIEW_STEPS),
                centerWorld = plan.centerWorld,
                axisWorld = plan.axisWorld,
                degreesPerStep = plan.stepAngle,
                liftWorldPerStep = plan.stepLiftWorld
            )
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val c = center ?: return null
        if (!hasHover) {
            return null
        }
        val plan = resolveHelicalPlan(hover)
        return ToolMeasurement(
            startWorld = Vector3(c),
            endWorld = Vector3(hover),
            extraLabels = copyCountLabels(plan?.copies ?: 0, plan?.capped == true)
        )
    }

    private fun resolveProspectiveCopies(finalPoint: Vector3): Int? {
        val c = center ?: return null
        val a = reference ?: return null
        val b = increment ?: return null
        val ref = Vector3(a).sub(c)
        val inc = Vector3(b).sub(c)
        val end = Vector3(finalPoint).sub(c)
        if (ref.len2() <= MULTIPLE_COPY_EPS || inc.len2() <= MULTIPLE_COPY_EPS || end.len2() <= MULTIPLE_COPY_EPS) {
            return null
        }
        val axisWorld = Vector3(ref).crs(inc)
        if (axisWorld.len2() <= MULTIPLE_COPY_EPS) {
            return null
        }
        axisWorld.nor()
        val refProjected = projectOntoPlane(ref, axisWorld)
        val incProjected = projectOntoPlane(inc, axisWorld)
        val endProjected = projectOntoPlane(end, axisWorld)
        if (refProjected.len2() <= MULTIPLE_COPY_EPS ||
            incProjected.len2() <= MULTIPLE_COPY_EPS ||
            endProjected.len2() <= MULTIPLE_COPY_EPS
        ) {
            return null
        }
        val rawStepAngle = signedAngleDeg(refProjected, incProjected, axisWorld)
        if (abs(rawStepAngle) <= 1e-4f) {
            return null
        }
        val rawSweep = signedAngleDeg(refProjected, endProjected, axisWorld)
        val layout = if (abs(rawSweep) <= FULL_CIRCLE_REQUEST_EPS_DEG) {
            resolveEqualizedCircularLayout(scene, scene.activeGroup(), c, a, axisWorld, rawStepAngle)
        } else {
            null
        }
        val stepAngle = layout?.stepAngle ?: rawStepAngle
        return if (layout != null) {
            layout.positionCount - 1
        } else {
            val direction = if (stepAngle >= 0f) 1f else -1f
            val sweep = normalizeSignedSweep(rawSweep, direction)
            if (sweep * direction <= 0f) return null
            floor(abs(sweep) / abs(stepAngle)).toInt()
        }.coerceAtLeast(0)
    }

    private data class HelicalPlan(
        val centerWorld: Vector3,
        val axisWorld: Vector3,
        val stepAngle: Float,
        val stepLiftWorld: Float,
        val copies: Int,
        val capped: Boolean
    )

    private fun resolveHelicalPlan(finalPoint: Vector3): HelicalPlan? {
        val c = center ?: return null
        val a = reference ?: return null
        val b = increment ?: return null
        val upAxis = Vector3(0f, 1f, 0f)
        val ref = Vector3(a).sub(c)
        val unit = Vector3(b).sub(c)
        val end = Vector3(finalPoint).sub(c)
        val refProjected = projectOntoPlane(ref, upAxis)
        val unitProjected = projectOntoPlane(unit, upAxis)
        val endProjected = projectOntoPlane(end, upAxis)
        if (refProjected.len2() <= MULTIPLE_COPY_EPS ||
            unitProjected.len2() <= MULTIPLE_COPY_EPS ||
            endProjected.len2() <= MULTIPLE_COPY_EPS
        ) {
            return null
        }
        val stepAngle = signedAngleDeg(refProjected, unitProjected, upAxis)
        if (abs(stepAngle) <= 1e-4f) {
            return null
        }
        val stepLiftWorld = b.y - a.y
        val copies = resolveHelicalCopies(
            refProjected = refProjected,
            endProjected = endProjected,
            stepAngle = stepAngle,
            stepLiftWorld = stepLiftWorld,
            startHeight = a.y,
            endHeight = finalPoint.y
        ) ?: return null
        var capped = false
        var resolvedCopies = copies
        if (resolvedCopies > MAX_MULTIPLE_COPY_STEPS) {
            resolvedCopies = MAX_MULTIPLE_COPY_STEPS
            capped = true
        }
        if (resolvedCopies <= 0) {
            return null
        }
        return HelicalPlan(
            centerWorld = Vector3(c),
            axisWorld = upAxis,
            stepAngle = stepAngle,
            stepLiftWorld = stepLiftWorld,
            copies = resolvedCopies,
            capped = capped
        )
    }

    private fun resolveHelicalCopies(
        refProjected: Vector3,
        endProjected: Vector3,
        stepAngle: Float,
        stepLiftWorld: Float,
        startHeight: Float,
        endHeight: Float
    ): Int? {
        val direction = if (stepAngle >= 0f) 1f else -1f
        val rawSweep = signedAngleDeg(refProjected, endProjected, Vector3(0f, 1f, 0f))
        if (abs(stepLiftWorld) <= 1e-4f) {
            var sweep = normalizeSignedSweep(rawSweep, direction)
            if (abs(sweep) <= 1e-4f) {
                sweep = 360f * direction
            }
            if (sweep * direction <= 0f) {
                return null
            }
            var copies = floor(abs(sweep) / abs(stepAngle)).toInt()
            if (abs(abs(sweep) - 360f) <= 1e-3f && copies > 1) {
                copies -= 1
            }
            return if (copies > 0) copies else null
        }
        val heightEstimate = (endHeight - startHeight) / stepLiftWorld
        if (heightEstimate <= 0f) {
            return null
        }
        val approximateSweep = abs(stepAngle * heightEstimate)
        val maxTurns = (floor(approximateSweep / 360f).toInt() + 8).coerceAtMost(MAX_MULTIPLE_COPY_STEPS)
        var bestCopies = 0
        var bestScore = Float.POSITIVE_INFINITY
        for (turns in 0..maxTurns) {
            var candidateSweep = rawSweep + 360f * direction * turns.toFloat()
            if (candidateSweep * direction <= 0f) {
                candidateSweep += 360f * direction
            }
            if (candidateSweep * direction <= 0f) {
                continue
            }
            val candidateCopiesFloat = candidateSweep / stepAngle
            if (candidateCopiesFloat <= 0f) {
                continue
            }
            val candidateCopies = candidateCopiesFloat.roundToInt().coerceAtLeast(1)
            val score = abs(candidateCopies.toFloat() - heightEstimate) +
                abs(candidateCopiesFloat - candidateCopies.toFloat()) * 0.25f
            if (score < bestScore) {
                bestScore = score
                bestCopies = candidateCopies
            }
        }
        if (bestCopies <= 0) {
            bestCopies = heightEstimate.roundToInt().coerceAtLeast(1)
        }
        return bestCopies
    }

    private fun clearTransient() {
        center = null
        reference = null
        increment = null
        hasHover = false
    }
}

class EqualizedRotationalArrayTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.ROTATIONAL_ARRAY_EQUALIZED
    override val message: String = "Pick rotation center (c)."

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
            status.message = "Pick first reference point (a)."
            return true
        }
        if (reference == null) {
            reference = Vector3(world)
            status.message = "Pick increment point (b)."
            return true
        }
        if (increment == null) {
            increment = Vector3(world)
            status.message = "Pick full or partial sweep point (d)."
            return true
        }

        val c = center ?: return false
        val a = reference ?: return false
        val b = increment ?: return false
        val d = Vector3(world)
        val ref = Vector3(a).sub(c)
        val inc = Vector3(b).sub(c)
        val end = Vector3(d).sub(c)
        if (ref.len2() <= MULTIPLE_COPY_EPS || inc.len2() <= MULTIPLE_COPY_EPS || end.len2() <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Rotational array even canceled: vectors are too short."
            return true
        }
        val axisWorld = Vector3(ref).crs(inc)
        if (axisWorld.len2() <= MULTIPLE_COPY_EPS) {
            clearTransient()
            status.message = "Rotational array even canceled: c/a/b are collinear."
            return true
        }
        axisWorld.nor()
        val refProjected = projectOntoPlane(ref, axisWorld)
        val incProjected = projectOntoPlane(inc, axisWorld)
        val endProjected = projectOntoPlane(end, axisWorld)
        if (refProjected.len2() <= MULTIPLE_COPY_EPS ||
            incProjected.len2() <= MULTIPLE_COPY_EPS ||
            endProjected.len2() <= MULTIPLE_COPY_EPS
        ) {
            clearTransient()
            status.message = "Rotational array even canceled: projected vectors are too short."
            return true
        }
        val rawStepAngle = signedAngleDeg(refProjected, incProjected, axisWorld)
        if (abs(rawStepAngle) <= 1e-4f) {
            clearTransient()
            status.message = "Rotational array even canceled: angular increment is zero."
            return true
        }
        val rawSweep = signedAngleDeg(refProjected, endProjected, axisWorld)
        val fullCircleRequested = abs(rawSweep) <= FULL_CIRCLE_REQUEST_EPS_DEG
        val group = scene.activeGroup()
        val layout = if (fullCircleRequested) {
            resolveEqualizedCircularLayout(scene, group, c, a, axisWorld, rawStepAngle)
        } else {
            null
        }
        val stepAngle = layout?.stepAngle ?: rawStepAngle
        val direction = if (stepAngle >= 0f) 1f else -1f
        var sweep = if (layout != null) {
            360f * direction
        } else {
            normalizeSignedSweep(rawSweep, direction)
        }
        if (layout == null && sweep * direction <= 0f) {
            clearTransient()
            status.message = "Rotational array even canceled: sweep direction differs from increment."
            return true
        }
        var copies = if (layout != null) {
            layout.positionCount - 1
        } else {
            floor(abs(sweep) / abs(stepAngle)).toInt()
        }
        if (copies <= 0) {
            clearTransient()
            status.message = "Rotational array even: no effective copies."
            return true
        }
        var capped = layout?.capped == true
        if (copies > MAX_MULTIPLE_COPY_STEPS) {
            copies = MAX_MULTIPLE_COPY_STEPS
            capped = true
        }
        val total = MultipleCopyCounts()
        repeat(copies) {
            val stepCounts = applyRotationalCopyStep(
                scene = scene,
                group = group,
                centerWorld = c,
                axisWorld = axisWorld,
                degrees = stepAngle,
                liftWorld = 0f
            )
            mergeCounts(total, stepCounts)
        }
        status.message = buildEqualizedMultipleCopyStatus(
            label = "Rotational array even",
            copies = copies,
            capped = capped,
            total = total,
            positionCount = layout?.positionCount
        )
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val c = center ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, c, 0.18f)
        reference?.let { a ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, a, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
        }
        increment?.let { b ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, b, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, b.x, b.y, b.z)
        }
        if (!hasHover) {
            return
        }
        renderer.color = ToolFeedbackColors.SECONDARY
        drawCross(renderer, hover, 0.18f)
        renderer.color = ToolFeedbackColors.TERTIARY
        renderer.line(c.x, c.y, c.z, hover.x, hover.y, hover.z)

        val a = reference ?: return
        val b = increment ?: return
        val ref = Vector3(a).sub(c)
        val inc = Vector3(b).sub(c)
        val end = Vector3(hover).sub(c)
        if (ref.len2() <= MULTIPLE_COPY_EPS || inc.len2() <= MULTIPLE_COPY_EPS || end.len2() <= MULTIPLE_COPY_EPS) {
            return
        }
        val axisWorld = Vector3(ref).crs(inc)
        if (axisWorld.len2() <= MULTIPLE_COPY_EPS) {
            return
        }
        axisWorld.nor()
        val refProjected = projectOntoPlane(ref, axisWorld)
        val incProjected = projectOntoPlane(inc, axisWorld)
        val endProjected = projectOntoPlane(end, axisWorld)
        if (refProjected.len2() <= MULTIPLE_COPY_EPS ||
            incProjected.len2() <= MULTIPLE_COPY_EPS ||
            endProjected.len2() <= MULTIPLE_COPY_EPS
        ) {
            return
        }
        val rawStepAngle = signedAngleDeg(refProjected, incProjected, axisWorld)
        if (abs(rawStepAngle) <= 1e-4f) {
            return
        }
        val rawSweep = signedAngleDeg(refProjected, endProjected, axisWorld)
        val layout = if (abs(rawSweep) <= FULL_CIRCLE_REQUEST_EPS_DEG) {
            resolveEqualizedCircularLayout(scene, scene.activeGroup(), c, a, axisWorld, rawStepAngle)
        } else {
            null
        }
        val stepAngle = layout?.stepAngle ?: rawStepAngle
        val previewCopies = if (layout != null) {
            layout.positionCount - 1
        } else {
            val direction = if (stepAngle >= 0f) 1f else -1f
            val sweep = normalizeSignedSweep(rawSweep, direction)
            if (sweep * direction <= 0f) return
            floor(abs(sweep) / abs(stepAngle)).toInt()
        }.coerceAtMost(MAX_MULTIPLE_COPY_PREVIEW_STEPS)
        if (previewCopies <= 0) {
            return
        }
        renderRotationalPreview(
            scene = scene,
            group = scene.activeGroup(),
            renderer = renderer,
            previewCopies = previewCopies,
            centerWorld = c,
            axisWorld = axisWorld,
            degreesPerStep = stepAngle,
            liftWorldPerStep = 0f
        )
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val c = center ?: return null
        if (!hasHover) {
            return null
        }
        val copies = resolveEqualizedRotationalProspectiveCopies(hover)
        return ToolMeasurement(
            startWorld = Vector3(c),
            endWorld = Vector3(hover),
            extraLabels = copyCountLabels(
                copies?.coerceAtMost(MAX_MULTIPLE_COPY_STEPS) ?: 0,
                (copies ?: 0) > MAX_MULTIPLE_COPY_STEPS
            )
        )
    }

    private fun resolveEqualizedRotationalProspectiveCopies(finalPoint: Vector3): Int? {
        val c = center ?: return null
        val a = reference ?: return null
        val b = increment ?: return null
        val ref = Vector3(a).sub(c)
        val inc = Vector3(b).sub(c)
        val end = Vector3(finalPoint).sub(c)
        if (ref.len2() <= MULTIPLE_COPY_EPS || inc.len2() <= MULTIPLE_COPY_EPS || end.len2() <= MULTIPLE_COPY_EPS) {
            return null
        }
        val axisWorld = Vector3(ref).crs(inc)
        if (axisWorld.len2() <= MULTIPLE_COPY_EPS) {
            return null
        }
        axisWorld.nor()
        val refProjected = projectOntoPlane(ref, axisWorld)
        val incProjected = projectOntoPlane(inc, axisWorld)
        val endProjected = projectOntoPlane(end, axisWorld)
        if (refProjected.len2() <= MULTIPLE_COPY_EPS ||
            incProjected.len2() <= MULTIPLE_COPY_EPS ||
            endProjected.len2() <= MULTIPLE_COPY_EPS
        ) {
            return null
        }
        val rawStepAngle = signedAngleDeg(refProjected, incProjected, axisWorld)
        if (abs(rawStepAngle) <= 1e-4f) {
            return null
        }
        val rawSweep = signedAngleDeg(refProjected, endProjected, axisWorld)
        val layout = if (abs(rawSweep) <= FULL_CIRCLE_REQUEST_EPS_DEG) {
            resolveEqualizedCircularLayout(scene, scene.activeGroup(), c, a, axisWorld, rawStepAngle)
        } else {
            null
        }
        val stepAngle = layout?.stepAngle ?: rawStepAngle
        return if (layout != null) {
            layout.positionCount - 1
        } else {
            val direction = if (stepAngle >= 0f) 1f else -1f
            val sweep = normalizeSignedSweep(rawSweep, direction)
            if (sweep * direction <= 0f) return null
            floor(abs(sweep) / abs(stepAngle)).toInt()
        }.coerceAtLeast(0)
    }

    private fun clearTransient() {
        center = null
        reference = null
        increment = null
        hasHover = false
    }
}

class EqualizedHelicalArrayTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.HELICAL_ARRAY_EQUALIZED
    override val message: String = "Pick rotation center (c)."

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
            status.message = "Pick first reference point (a)."
            return true
        }
        if (reference == null) {
            reference = Vector3(world)
            status.message = "Pick helical unit point (b)."
            return true
        }
        if (increment == null) {
            increment = Vector3(world)
            status.message = "Pick final point (d)."
            return true
        }

        val plan = resolvePlan(Vector3(world))
        if (plan == null) {
            clearTransient()
            status.message = "Helical array even canceled: invalid helical definition."
            return true
        }
        val group = scene.activeGroup()
        val total = MultipleCopyCounts()
        repeat(plan.copies) {
            val stepCounts = applyRotationalCopyStep(
                scene = scene,
                group = group,
                centerWorld = plan.centerWorld,
                axisWorld = plan.axisWorld,
                degrees = plan.stepAngle,
                liftWorld = plan.stepLiftWorld
            )
            mergeCounts(total, stepCounts)
        }
        status.message = buildEqualizedMultipleCopyStatus(
            label = "Helical array even",
            copies = plan.copies,
            capped = plan.capped,
            total = total,
            positionCount = plan.positionCount
        )
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val c = center ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, c, 0.18f)
        reference?.let { a ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, a, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, a.x, a.y, a.z)
        }
        increment?.let { b ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, b, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, b.x, b.y, b.z)
        }
        if (hasHover) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawCross(renderer, hover, 0.18f)
            renderer.color = ToolFeedbackColors.TERTIARY
            renderer.line(c.x, c.y, c.z, hover.x, hover.y, hover.z)
            val plan = resolvePlan(hover) ?: return
            renderRotationalPreview(
                scene = scene,
                group = scene.activeGroup(),
                renderer = renderer,
                previewCopies = plan.copies.coerceAtMost(MAX_MULTIPLE_COPY_PREVIEW_STEPS),
                centerWorld = plan.centerWorld,
                axisWorld = plan.axisWorld,
                degreesPerStep = plan.stepAngle,
                liftWorldPerStep = plan.stepLiftWorld
            )
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val c = center ?: return null
        if (!hasHover) {
            return null
        }
        val plan = resolvePlan(hover)
        return ToolMeasurement(
            startWorld = Vector3(c),
            endWorld = Vector3(hover),
            extraLabels = copyCountLabels(plan?.copies ?: 0, plan?.capped == true)
        )
    }

    private data class HelicalPlan(
        val centerWorld: Vector3,
        val axisWorld: Vector3,
        val stepAngle: Float,
        val stepLiftWorld: Float,
        val copies: Int,
        val capped: Boolean,
        val positionCount: Int?
    )

    private fun resolvePlan(finalPoint: Vector3): HelicalPlan? {
        val c = center ?: return null
        val a = reference ?: return null
        val b = increment ?: return null
        val upAxis = Vector3(0f, 1f, 0f)
        val ref = Vector3(a).sub(c)
        val unit = Vector3(b).sub(c)
        val end = Vector3(finalPoint).sub(c)
        val refProjected = projectOntoPlane(ref, upAxis)
        val unitProjected = projectOntoPlane(unit, upAxis)
        val endProjected = projectOntoPlane(end, upAxis)
        if (refProjected.len2() <= MULTIPLE_COPY_EPS ||
            unitProjected.len2() <= MULTIPLE_COPY_EPS ||
            endProjected.len2() <= MULTIPLE_COPY_EPS
        ) {
            return null
        }
        val rawStepAngle = signedAngleDeg(refProjected, unitProjected, upAxis)
        if (abs(rawStepAngle) <= 1e-4f) {
            return null
        }
        val rawSweep = signedAngleDeg(refProjected, endProjected, upAxis)
        val stepLiftWorld = b.y - a.y
        val layout = if (abs(rawSweep) <= FULL_CIRCLE_REQUEST_EPS_DEG) {
            resolveEqualizedCircularLayout(scene, scene.activeGroup(), c, a, upAxis, rawStepAngle)
        } else {
            null
        }
        if (layout != null) {
            var copies = if (abs(stepLiftWorld) <= 1e-4f) {
                layout.positionCount - 1
            } else {
                val heightEstimate = (finalPoint.y - a.y) / stepLiftWorld
                if (heightEstimate <= 0f) {
                    return null
                }
                val turns = max(1, (heightEstimate / layout.positionCount.toFloat()).roundToInt())
                turns * layout.positionCount
            }
            var capped = layout.capped
            if (copies > MAX_MULTIPLE_COPY_STEPS) {
                copies = MAX_MULTIPLE_COPY_STEPS
                capped = true
            }
            if (copies <= 0) {
                return null
            }
            return HelicalPlan(
                centerWorld = Vector3(c),
                axisWorld = upAxis,
                stepAngle = layout.stepAngle,
                stepLiftWorld = stepLiftWorld,
                copies = copies,
                capped = capped,
                positionCount = layout.positionCount
            )
        }

        val copies = resolveStandardHelicalCopies(
            refProjected = refProjected,
            endProjected = endProjected,
            stepAngle = rawStepAngle,
            stepLiftWorld = stepLiftWorld,
            startHeight = a.y,
            endHeight = finalPoint.y
        ) ?: return null
        var resolvedCopies = copies
        var capped = false
        if (resolvedCopies > MAX_MULTIPLE_COPY_STEPS) {
            resolvedCopies = MAX_MULTIPLE_COPY_STEPS
            capped = true
        }
        if (resolvedCopies <= 0) {
            return null
        }
        return HelicalPlan(
            centerWorld = Vector3(c),
            axisWorld = upAxis,
            stepAngle = rawStepAngle,
            stepLiftWorld = stepLiftWorld,
            copies = resolvedCopies,
            capped = capped,
            positionCount = null
        )
    }

    private fun resolveStandardHelicalCopies(
        refProjected: Vector3,
        endProjected: Vector3,
        stepAngle: Float,
        stepLiftWorld: Float,
        startHeight: Float,
        endHeight: Float
    ): Int? {
        val direction = if (stepAngle >= 0f) 1f else -1f
        val rawSweep = signedAngleDeg(refProjected, endProjected, Vector3(0f, 1f, 0f))
        if (abs(stepLiftWorld) <= 1e-4f) {
            var sweep = normalizeSignedSweep(rawSweep, direction)
            if (abs(sweep) <= 1e-4f) {
                sweep = 360f * direction
            }
            if (sweep * direction <= 0f) {
                return null
            }
            var copies = floor(abs(sweep) / abs(stepAngle)).toInt()
            if (abs(abs(sweep) - 360f) <= 1e-3f && copies > 1) {
                copies -= 1
            }
            return if (copies > 0) copies else null
        }
        val heightEstimate = (endHeight - startHeight) / stepLiftWorld
        if (heightEstimate <= 0f) {
            return null
        }
        val approximateSweep = abs(stepAngle * heightEstimate)
        val maxTurns = (floor(approximateSweep / 360f).toInt() + 8).coerceAtMost(MAX_MULTIPLE_COPY_STEPS)
        var bestCopies = 0
        var bestScore = Float.POSITIVE_INFINITY
        for (turns in 0..maxTurns) {
            var candidateSweep = rawSweep + 360f * direction * turns.toFloat()
            if (candidateSweep * direction <= 0f) {
                candidateSweep += 360f * direction
            }
            if (candidateSweep * direction <= 0f) {
                continue
            }
            val candidateCopiesFloat = candidateSweep / stepAngle
            if (candidateCopiesFloat <= 0f) {
                continue
            }
            val candidateCopies = candidateCopiesFloat.roundToInt().coerceAtLeast(1)
            val score = abs(candidateCopies.toFloat() - heightEstimate) +
                abs(candidateCopiesFloat - candidateCopies.toFloat()) * 0.25f
            if (score < bestScore) {
                bestScore = score
                bestCopies = candidateCopies
            }
        }
        if (bestCopies <= 0) {
            bestCopies = heightEstimate.roundToInt().coerceAtLeast(1)
        }
        return bestCopies
    }

    private fun clearTransient() {
        center = null
        reference = null
        increment = null
        hasHover = false
    }
}

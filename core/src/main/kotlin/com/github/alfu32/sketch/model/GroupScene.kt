package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

class GroupScene(
    val defaultFaceColor: Color
) {
    data class Axes(val u: Vector3, val v: Vector3, val w: Vector3)
    enum class PrototypeKind { MESH, VOXEL, ARCHITECTURE }
    enum class HoleHandleKind {
        CORNER_0, CORNER_1, CORNER_2, CORNER_3,
        EDGE_0, EDGE_1, EDGE_2, EDGE_3,
        CENTER
    }
    data class HoleHandleMarker(
        val wallId: String,
        val holeId: String,
        val kind: HoleHandleKind,
        val world: Vector3
    )
    data class WallEndpointHandleMarker(
        val wallId: String,
        val draggingStart: Boolean,
        val center: Vector3,
        val halfSize: Float
    )

    class ObjectPrototype(
        val id: String,
        var name: String,
        var definitionOrigin: Vector3,
        var definitionAxisU: Vector3,
        var definitionAxisV: Vector3,
        var definitionAxisW: Vector3,
        var gluedToSurface: Boolean,
        var kind: PrototypeKind = PrototypeKind.MESH,
        var voxelColor: Color = Color(1f, 1f, 1f, 1f),
        val voxelStore: VoxelStore? = null,
        val architectureStore: ArchitectureStore? = null,
        val lineStore: DraftLineStore,
        val faceStore: DraftFaceStore,
        val dimensionStore: DraftDimensionStore,
        val textStore: DraftTextStore
    )

    class GroupNode(
        val id: String,
        val prototype: ObjectPrototype,
        var instanceOrigin: Vector3,
        var instanceAxisU: Vector3,
        var instanceAxisV: Vector3,
        var instanceAxisW: Vector3
    ) {
        val children: MutableList<GroupNode> = mutableListOf()
        var parent: GroupNode? = null
        val lineStore: DraftLineStore
            get() = prototype.lineStore
        val faceStore: DraftFaceStore
            get() = prototype.faceStore
        val dimensionStore: DraftDimensionStore
            get() = prototype.dimensionStore
        val textStore: DraftTextStore
            get() = prototype.textStore
        var name: String
            get() = prototype.name
            set(value) { prototype.name = value }
        var gluedToSurface: Boolean
            get() = prototype.gluedToSurface
            set(value) { prototype.gluedToSurface = value }
        var definitionOrigin: Vector3
            get() = prototype.definitionOrigin
            set(value) { prototype.definitionOrigin = value }
        var definitionAxisU: Vector3
            get() = prototype.definitionAxisU
            set(value) { prototype.definitionAxisU = value }
        var definitionAxisV: Vector3
            get() = prototype.definitionAxisV
            set(value) { prototype.definitionAxisV = value }
        var definitionAxisW: Vector3
            get() = prototype.definitionAxisW
            set(value) { prototype.definitionAxisW = value }
        val kind: PrototypeKind
            get() = prototype.kind
        val voxelStore: VoxelStore?
            get() = prototype.voxelStore
        val architectureStore: ArchitectureStore?
            get() = prototype.architectureStore
        val voxelColor: Color
            get() = prototype.voxelColor

        fun addSketchSegment(startLocal: Vector3, endLocal: Vector3) {
            lineStore.addSegment(startLocal, endLocal, autoCleanup = false)
            lineStore.cleanupJts()
        }

        fun worldAxes(): Axes {
            val matrix = worldMatrix()
            return axesFromMatrix(matrix)
        }

        fun worldOrigin(): Vector3 {
            val matrix = worldMatrix()
            val v = matrix.`val`
            return Vector3(v[Matrix4.M03], v[Matrix4.M13], v[Matrix4.M23])
        }

        fun toWorld(local: Vector3): Vector3 {
            return transformPoint(worldMatrix(), local)
        }

        fun toLocal(world: Vector3): Vector3 {
            val inv = worldMatrix().inv()
            return transformPoint(inv, world)
        }

        fun vectorToWorld(local: Vector3): Vector3 {
            return transformVector(worldMatrix(), local)
        }

        fun vectorToLocal(world: Vector3): Vector3 {
            val inv = worldMatrix().inv()
            return transformVector(inv, world)
        }

        fun setInstanceFromWorld(originWorld: Vector3, worldU: Vector3, worldV: Vector3, worldW: Vector3) {
            val parentMatrix = parent?.worldMatrix() ?: Matrix4().idt()
            val desiredWorld = matrixFromAxes(originWorld, Axes(worldU, worldV, worldW))
            val defMatrix = definitionMatrix()
            val invParent = Matrix4(parentMatrix).inv()
            val invDef = Matrix4(defMatrix).inv()
            val instance = Matrix4(invParent).mul(desiredWorld).mul(invDef)
            setInstanceFromMatrix(instance)
        }

        fun definitionMatrix(): Matrix4 {
            return matrixFromAxes(definitionOrigin, Axes(definitionAxisU, definitionAxisV, definitionAxisW))
        }

        fun instanceMatrix(): Matrix4 {
            return matrixFromAxes(instanceOrigin, Axes(instanceAxisU, instanceAxisV, instanceAxisW))
        }

        fun worldMatrix(): Matrix4 {
            val parentMatrix = parent?.worldMatrix() ?: Matrix4().idt()
            val result = Matrix4(parentMatrix)
            result.mul(instanceMatrix())
            result.mul(definitionMatrix())
            return result
        }

        fun worldBounds(): BoundingBox? {
            val bounds = BoundingBox()
            var hasAny = false
            lineStore.getSegments().forEach { seg ->
                val a = toWorld(seg.start)
                val b = toWorld(seg.end)
                if (!hasAny) {
                    bounds.set(a, a)
                    hasAny = true
                }
                bounds.ext(a)
                bounds.ext(b)
            }
            faceStore.getTriangles().forEach { tri ->
                val a = toWorld(tri.a)
                val b = toWorld(tri.b)
                val c = toWorld(tri.c)
                if (!hasAny) {
                    bounds.set(a, a)
                    hasAny = true
                }
                bounds.ext(a)
                bounds.ext(b)
                bounds.ext(c)
            }
            children.forEach { child ->
                val childBounds = child.worldBounds()
                if (childBounds != null) {
                    if (!hasAny) {
                        bounds.set(childBounds)
                        hasAny = true
                    } else {
                        bounds.ext(childBounds)
                    }
                }
            }
            return if (hasAny) bounds else null
        }

        fun localBounds(): BoundingBox? {
            val bounds = BoundingBox()
            var hasAny = false
            lineStore.getSegments().forEach { seg ->
                if (!hasAny) {
                    bounds.set(seg.start, seg.start)
                    hasAny = true
                }
                bounds.ext(seg.start)
                bounds.ext(seg.end)
            }
            faceStore.getTriangles().forEach { tri ->
                if (!hasAny) {
                    bounds.set(tri.a, tri.a)
                    hasAny = true
                }
                bounds.ext(tri.a)
                bounds.ext(tri.b)
                bounds.ext(tri.c)
            }
            children.forEach { child ->
                val childBounds = child.worldBounds()
                if (childBounds != null) {
                    val corners = arrayOf(
                        Vector3(childBounds.min.x, childBounds.min.y, childBounds.min.z),
                        Vector3(childBounds.min.x, childBounds.min.y, childBounds.max.z),
                        Vector3(childBounds.min.x, childBounds.max.y, childBounds.min.z),
                        Vector3(childBounds.min.x, childBounds.max.y, childBounds.max.z),
                        Vector3(childBounds.max.x, childBounds.min.y, childBounds.min.z),
                        Vector3(childBounds.max.x, childBounds.min.y, childBounds.max.z),
                        Vector3(childBounds.max.x, childBounds.max.y, childBounds.min.z),
                        Vector3(childBounds.max.x, childBounds.max.y, childBounds.max.z)
                    )
                    corners.forEach { corner ->
                        val local = toLocal(corner)
                        if (!hasAny) {
                            bounds.set(local, local)
                            hasAny = true
                        }
                        bounds.ext(local)
                    }
                }
            }
            return if (hasAny) bounds else null
        }

        fun orientedBoundsCorners(): Array<Vector3>? = orientedBoundsCorners(0f)

        fun orientedBoundsCorners(expandRatio: Float): Array<Vector3>? {
            val bounds = localBounds() ?: return null
            val min = bounds.min
            val max = bounds.max
            val center = Vector3(min).lerp(max, 0.5f)
            val half = Vector3(max).sub(min).scl(0.5f)
            if (expandRatio > 0f) {
                half.scl(1f + expandRatio)
            }
            val expandedMin = Vector3(center).sub(half)
            val expandedMax = Vector3(center).add(half)
            val corners = arrayOf(
                Vector3(expandedMin.x, expandedMin.y, expandedMin.z),
                Vector3(expandedMax.x, expandedMin.y, expandedMin.z),
                Vector3(expandedMax.x, expandedMin.y, expandedMax.z),
                Vector3(expandedMin.x, expandedMin.y, expandedMax.z),
                Vector3(expandedMin.x, expandedMax.y, expandedMin.z),
                Vector3(expandedMax.x, expandedMax.y, expandedMin.z),
                Vector3(expandedMax.x, expandedMax.y, expandedMax.z),
                Vector3(expandedMin.x, expandedMax.y, expandedMax.z)
            )
            corners.indices.forEach { idx ->
                corners[idx] = toWorld(corners[idx])
            }
            return corners
        }

        private fun setInstanceFromMatrix(matrix: Matrix4) {
            val v = matrix.`val`
            instanceAxisU.set(v[Matrix4.M00], v[Matrix4.M10], v[Matrix4.M20])
            instanceAxisV.set(v[Matrix4.M01], v[Matrix4.M11], v[Matrix4.M21])
            instanceAxisW.set(v[Matrix4.M02], v[Matrix4.M12], v[Matrix4.M22])
            instanceOrigin.set(v[Matrix4.M03], v[Matrix4.M13], v[Matrix4.M23])
        }
    }

    private val prototypes = linkedMapOf<String, ObjectPrototype>()
    private val prototypeInstances = mutableMapOf<String, MutableSet<GroupNode>>()
    private val rootPrototype = ObjectPrototype(
        id = "root",
        name = "Root",
        definitionOrigin = Vector3(),
        definitionAxisU = Vector3(1f, 0f, 0f),
        definitionAxisV = Vector3(0f, 1f, 0f),
        definitionAxisW = Vector3(0f, 0f, 1f),
        gluedToSurface = false,
        kind = PrototypeKind.MESH,
        voxelColor = Color(defaultFaceColor),
        voxelStore = null,
        architectureStore = null,
        lineStore = DraftLineStore(),
        faceStore = DraftFaceStore(defaultFaceColor),
        dimensionStore = DraftDimensionStore(),
        textStore = DraftTextStore()
    )
    val root: GroupNode = GroupNode(
        id = "root",
        prototype = rootPrototype,
        instanceOrigin = Vector3(),
        instanceAxisU = Vector3(1f, 0f, 0f),
        instanceAxisV = Vector3(0f, 1f, 0f),
        instanceAxisW = Vector3(0f, 0f, 1f)
    )
    private val selectedGroups = mutableSetOf<GroupNode>()
    private var activeGroup: GroupNode = root
    private var changeListener: (() -> Unit)? = null

    init {
        registerPrototype(rootPrototype)
        registerInstance(root)
    }

    fun activeGroup(): GroupNode = activeGroup

    fun isEditing(): Boolean = activeGroup != root

    fun resetActiveGroup() {
        activeGroup = root
    }

    fun setChangeListener(listener: () -> Unit) {
        changeListener = listener
        applyChangeListener(root)
        walkGroups(root) { group ->
            applyChangeListener(group)
        }
    }

    fun applyChangeListenerToAll() {
        val listener = changeListener ?: return
        applyChangeListener(root)
        walkGroups(root) { group ->
            applyChangeListener(group)
        }
    }

    fun enterGroup(group: GroupNode): Boolean {
        if (group == activeGroup) {
            return false
        }
        activeGroup = group
        clearGroupSelection()
        return true
    }

    fun exitGroup(): Boolean {
        val parent = activeGroup.parent ?: return false
        activeGroup = parent
        clearGroupSelection()
        return true
    }

    fun clearGroupSelection() {
        selectedGroups.clear()
    }

    fun selectedGroups(): Set<GroupNode> = selectedGroups

    fun toggleGroupSelection(group: GroupNode) {
        if (!selectedGroups.add(group)) {
            selectedGroups.remove(group)
        }
    }

    fun addGroupSelection(group: GroupNode): Boolean = selectedGroups.add(group)

    fun removeGroupSelection(group: GroupNode) {
        selectedGroups.remove(group)
    }

    fun clearAllSelections() {
        root.lineStore.clearSelection()
        root.faceStore.clearSelection()
        root.dimensionStore.clearSelection()
        root.textStore.clearSelection()
        root.voxelStore?.clearSelection()
        walkGroups(root) { group ->
            group.lineStore.clearSelection()
            group.faceStore.clearSelection()
            group.dimensionStore.clearSelection()
            group.textStore.clearSelection()
            group.voxelStore?.clearSelection()
            group.architectureStore?.clearSelectedElement()
        }
        clearGroupSelection()
    }

    fun groupsInActiveContext(): List<GroupNode> {
        return activeGroup.children.toList()
    }

    fun objectPrototypes(): List<ObjectPrototype> {
        return prototypes.values.filter { it.id != rootPrototype.id }
    }

    fun allPrototypes(): List<ObjectPrototype> = prototypes.values.toList()

    fun rootPrototypeId(): String = rootPrototype.id

    fun rootPrototype(): ObjectPrototype = rootPrototype

    fun registerPrototypeForLoad(prototype: ObjectPrototype) {
        registerPrototype(prototype)
    }

    fun registerInstanceTree(group: GroupNode) {
        registerInstance(group)
        applyChangeListener(group)
    }

    fun objectPrototypeById(id: String): ObjectPrototype? = prototypes[id]

    fun objectPrototypeInstanceCount(id: String): Int {
        return prototypeInstances[id]?.size ?: 0
    }

    fun deletePrototype(id: String): Boolean {
        if (id == rootPrototype.id) {
            return false
        }
        if (objectPrototypeInstanceCount(id) > 0) {
            return false
        }
        prototypeInstances.remove(id)
        return prototypes.remove(id) != null
    }

    fun createVoxelGroup(name: String = "Voxel Group", color: Color = defaultFaceColor): GroupNode {
        val parent = activeGroup
        val prototype = ObjectPrototype(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            definitionOrigin = Vector3(),
            definitionAxisU = Vector3(1f, 0f, 0f),
            definitionAxisV = Vector3(0f, 1f, 0f),
            definitionAxisW = Vector3(0f, 0f, 1f),
            gluedToSurface = false,
            kind = PrototypeKind.VOXEL,
            voxelColor = Color(color),
            voxelStore = VoxelStore(),
            architectureStore = null,
            lineStore = DraftLineStore(),
            faceStore = DraftFaceStore(defaultFaceColor),
            dimensionStore = DraftDimensionStore(),
            textStore = DraftTextStore()
        )
        registerPrototype(prototype)
        val group = GroupNode(
            id = java.util.UUID.randomUUID().toString(),
            prototype = prototype,
            instanceOrigin = Vector3(),
            instanceAxisU = Vector3(1f, 0f, 0f),
            instanceAxisV = Vector3(0f, 1f, 0f),
            instanceAxisW = Vector3(0f, 0f, 1f)
        )
        group.parent = parent
        parent.children.add(group)
        registerInstance(group)
        applyChangeListener(group)
        clearGroupSelection()
        selectedGroups.add(group)
        notifyChange()
        return group
    }

    fun createArchitectureGroup(name: String = "Architecture Group", color: Color = defaultFaceColor): GroupNode {
        val parent = activeGroup
        val prototype = ObjectPrototype(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            definitionOrigin = Vector3(),
            definitionAxisU = Vector3(1f, 0f, 0f),
            definitionAxisV = Vector3(0f, 1f, 0f),
            definitionAxisW = Vector3(0f, 0f, 1f),
            gluedToSurface = false,
            kind = PrototypeKind.ARCHITECTURE,
            voxelColor = Color(color),
            voxelStore = null,
            architectureStore = ArchitectureStore(),
            lineStore = DraftLineStore(),
            faceStore = DraftFaceStore(defaultFaceColor),
            dimensionStore = DraftDimensionStore(),
            textStore = DraftTextStore()
        )
        registerPrototype(prototype)
        val group = GroupNode(
            id = java.util.UUID.randomUUID().toString(),
            prototype = prototype,
            instanceOrigin = Vector3(),
            instanceAxisU = Vector3(1f, 0f, 0f),
            instanceAxisV = Vector3(0f, 1f, 0f),
            instanceAxisW = Vector3(0f, 0f, 1f)
        )
        group.parent = parent
        parent.children.add(group)
        registerInstance(group)
        applyChangeListener(group)
        clearGroupSelection()
        selectedGroups.add(group)
        notifyChange()
        return group
    }

    fun isVoxelGroup(group: GroupNode): Boolean {
        return group.kind == PrototypeKind.VOXEL && group.voxelStore != null
    }

    fun isArchitectureGroup(group: GroupNode): Boolean {
        return group.kind == PrototypeKind.ARCHITECTURE && group.architectureStore != null
    }

    fun isWallOnlyArchitectureGroup(group: GroupNode): Boolean {
        val store = group.architectureStore ?: return false
        return store.allWalls().isNotEmpty() &&
            store.allSlabs().isEmpty() &&
            store.allStairs().isEmpty() &&
            store.allFrames().isEmpty()
    }

    fun voxelColor(group: GroupNode): Color? {
        if (!isVoxelGroup(group)) {
            return null
        }
        return Color(group.prototype.voxelColor)
    }

    fun setVoxelColor(group: GroupNode, color: Color, applyToExisting: Boolean = true): Boolean {
        if (!isVoxelGroup(group)) {
            return false
        }
        val prototype = group.prototype
        val store = prototype.voxelStore ?: return false
        var changed = false
        if (!colorsEqual(prototype.voxelColor, color)) {
            prototype.voxelColor.set(color)
            changed = true
        }
        if (applyToExisting) {
            if (store.recolorAll(color) > 0) {
                changed = true
            }
        }
        if (!changed) {
            return false
        }
        rebuildVoxelGeometry(prototype)
        notifyChange()
        return true
    }

    fun setVoxel(group: GroupNode, x: Int, y: Int, z: Int, color: Color): Boolean {
        val store = group.voxelStore ?: return false
        if (!store.set(x, y, z, color)) {
            return false
        }
        rebuildVoxelGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun removeVoxel(group: GroupNode, x: Int, y: Int, z: Int): Boolean {
        val store = group.voxelStore ?: return false
        if (!store.remove(x, y, z)) {
            return false
        }
        rebuildVoxelGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun selectedVoxels(group: GroupNode): Set<VoxelStore.Key> {
        return group.voxelStore?.selected().orEmpty()
    }

    fun clearVoxelSelection(group: GroupNode): Boolean {
        val store = group.voxelStore ?: return false
        if (store.selectedCount() == 0) {
            return false
        }
        store.clearSelection()
        return true
    }

    fun addVoxelSelection(group: GroupNode, key: VoxelStore.Key): Boolean {
        val store = group.voxelStore ?: return false
        if (!store.addSelection(key)) {
            return false
        }
        return true
    }

    fun removeVoxelSelection(group: GroupNode, key: VoxelStore.Key): Boolean {
        val store = group.voxelStore ?: return false
        if (!store.removeSelection(key)) {
            return false
        }
        return true
    }

    fun toggleVoxelSelection(group: GroupNode, key: VoxelStore.Key): Boolean {
        val store = group.voxelStore ?: return false
        if (!store.toggleSelection(key)) {
            return false
        }
        return true
    }

    fun replaceVoxelSelection(group: GroupNode, keys: Collection<VoxelStore.Key>): Int {
        val store = group.voxelStore ?: return 0
        return store.replaceSelection(keys)
    }

    fun deleteSelectedVoxels(group: GroupNode): Int {
        val store = group.voxelStore ?: return 0
        val removed = store.deleteSelected()
        if (removed <= 0) {
            return 0
        }
        rebuildVoxelGeometry(group.prototype)
        notifyChange()
        return removed
    }

    fun paintSelectedVoxels(group: GroupNode, color: Color): Int {
        val store = group.voxelStore ?: return 0
        val changed = store.recolorSelected(color)
        if (changed <= 0) {
            return 0
        }
        rebuildVoxelGeometry(group.prototype)
        notifyChange()
        return changed
    }

    fun moveSelectedVoxels(group: GroupNode, dx: Int, dy: Int, dz: Int, copy: Boolean): Int {
        val store = group.voxelStore ?: return 0
        val moved = store.moveSelected(dx, dy, dz, copy)
        if (moved <= 0) {
            return 0
        }
        rebuildVoxelGeometry(group.prototype)
        notifyChange()
        return moved
    }

    fun setVoxels(group: GroupNode, voxels: Collection<Pair<VoxelStore.Key, Color>>): Int {
        val store = group.voxelStore ?: return 0
        var changed = 0
        voxels.forEach { (key, color) ->
            if (store.set(key.x, key.y, key.z, color)) {
                changed++
            }
        }
        if (changed > 0) {
            rebuildVoxelGeometry(group.prototype)
            notifyChange()
        }
        return changed
    }

    fun addArchitectureWall(
        group: GroupNode,
        start: Vector3,
        end: Vector3,
        thickness: Float,
        height: Float,
        inclinationDeg: Float,
        exteriorColor: Color,
        interiorColor: Color
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (start.dst2(end) <= 1e-6f) {
            return false
        }
        store.addWall(
            start = start,
            end = end,
            thickness = thickness.coerceAtLeast(0.01f),
            height = height.coerceAtLeast(0.05f),
            inclinationDeg = inclinationDeg,
            exteriorColor = exteriorColor,
            interiorColor = interiorColor
        )
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun addArchitectureSlab(
        group: GroupNode,
        minCorner: Vector3,
        maxCorner: Vector3,
        thickness: Float,
        topColor: Color,
        bottomColor: Color,
        sideColor: Color
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (minCorner.dst2(maxCorner) <= 1e-6f) {
            return false
        }
        store.addSlab(
            minCorner = minCorner,
            maxCorner = maxCorner,
            thickness = thickness.coerceAtLeast(0.01f),
            topColor = topColor,
            bottomColor = bottomColor,
            sideColor = sideColor
        )
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun addArchitectureStair(
        group: GroupNode,
        minCorner: Vector3,
        maxCorner: Vector3,
        contourPoints: List<Vector3>,
        walkingPathPoints: List<Vector3>,
        walkingStart: Vector3,
        walkingEnd: Vector3,
        height: Float,
        stepCount: Int,
        supportThickness: Float,
        railLeftEnabled: Boolean,
        railRightEnabled: Boolean,
        treadColor: Color,
        supportColor: Color
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (minCorner.dst2(maxCorner) <= 1e-6f) {
            return false
        }
        store.addStair(
            minCorner = minCorner,
            maxCorner = maxCorner,
            contourPoints = contourPoints,
            walkingPathPoints = walkingPathPoints,
            walkingStart = walkingStart,
            walkingEnd = walkingEnd,
            height = height.coerceAtLeast(0.05f),
            stepCount = stepCount.coerceAtLeast(1),
            supportThickness = supportThickness.coerceAtLeast(0.01f),
            railLeftEnabled = railLeftEnabled,
            railRightEnabled = railRightEnabled,
            treadColor = treadColor,
            supportColor = supportColor
        )
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun addArchitectureFrame(
        group: GroupNode,
        cornerA: Vector3,
        cornerB: Vector3,
        normal: Vector3,
        depth: Float,
        frameWidth: Float,
        kind: ArchitectureStore.FrameKind,
        color: Color
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (cornerA.dst2(cornerB) <= 1e-6f) {
            return false
        }
        val safeNormal = if (normal.len2() <= 1e-6f) Vector3(0f, 1f, 0f) else Vector3(normal).nor()
        store.addFrame(
            cornerA = cornerA,
            cornerB = cornerB,
            normal = safeNormal,
            depth = depth.coerceAtLeast(0.01f),
            frameWidth = frameWidth.coerceAtLeast(0.01f),
            kind = kind,
            color = color
        )
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun updateArchitectureWall(
        group: GroupNode,
        id: String,
        thickness: Float,
        height: Float,
        inclinationDeg: Float,
        exteriorColor: Color,
        interiorColor: Color
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (
            !store.updateWall(
                id,
                thickness.coerceAtLeast(0.01f),
                height.coerceAtLeast(0.05f),
                inclinationDeg,
                exteriorColor,
                interiorColor
            )
        ) {
            return false
        }
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun updateArchitectureSlab(
        group: GroupNode,
        id: String,
        thickness: Float,
        topColor: Color,
        bottomColor: Color,
        sideColor: Color
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (!store.updateSlab(id, thickness.coerceAtLeast(0.01f), topColor, bottomColor, sideColor)) {
            return false
        }
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun updateArchitectureStair(
        group: GroupNode,
        id: String,
        height: Float,
        stepCount: Int,
        supportThickness: Float,
        railLeftEnabled: Boolean,
        railRightEnabled: Boolean,
        treadColor: Color,
        supportColor: Color
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (
            !store.updateStair(
                id,
                height.coerceAtLeast(0.05f),
                stepCount.coerceAtLeast(1),
                supportThickness.coerceAtLeast(0.01f),
                railLeftEnabled,
                railRightEnabled,
                treadColor,
                supportColor
            )
        ) {
            return false
        }
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun updateArchitectureFrame(group: GroupNode, id: String, depth: Float, frameWidth: Float, color: Color): Boolean {
        val store = group.architectureStore ?: return false
        if (!store.updateFrame(id, depth.coerceAtLeast(0.01f), frameWidth.coerceAtLeast(0.01f), color)) {
            return false
        }
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun selectedArchitectureElement(group: GroupNode): ArchitectureStore.ElementSelection? {
        return group.architectureStore?.selectedElement()
    }

    fun selectedArchitectureWall(group: GroupNode): ArchitectureStore.WallSegment? {
        return group.architectureStore?.selectedWall()
    }

    fun clearArchitectureElementSelection(group: GroupNode): Boolean {
        val store = group.architectureStore ?: return false
        if (store.selectedElement() == null) {
            return false
        }
        store.clearSelectedElement()
        return true
    }

    fun selectArchitectureElementNearWorldPoint(
        group: GroupNode,
        worldPoint: Vector3
    ): ArchitectureStore.ElementSelection? {
        val store = group.architectureStore ?: return null
        val point = group.toLocal(worldPoint)
        var bestSelection: ArchitectureStore.ElementSelection? = null
        var bestDist2 = Float.POSITIVE_INFINITY

        store.allWalls().forEach { wall ->
            val dist2 = architectureWallDistanceSq(wall, point)
            if (dist2 < bestDist2) {
                bestDist2 = dist2
                bestSelection = ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.WALL, wall.id)
            }
        }
        store.allSlabs().forEach { slab ->
            val dist2 = architectureSlabDistanceSq(slab, point)
            if (dist2 < bestDist2) {
                bestDist2 = dist2
                bestSelection = ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.SLAB, slab.id)
            }
        }
        store.allStairs().forEach { stair ->
            val dist2 = architectureStairDistanceSq(stair, point)
            if (dist2 < bestDist2) {
                bestDist2 = dist2
                bestSelection = ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.STAIR, stair.id)
            }
        }
        store.allFrames().forEach { frame ->
            val dist2 = architectureFrameDistanceSq(frame, point)
            if (dist2 < bestDist2) {
                bestDist2 = dist2
                bestSelection = ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.FRAME, frame.id)
            }
        }

        val selection = bestSelection
        if (selection == null) {
            store.clearSelectedElement()
            return null
        }
        store.setSelectedElement(selection.kind, selection.id)
        return store.selectedElement()
    }

    fun updateArchitectureWallEndpoints(
        group: GroupNode,
        id: String,
        start: Vector3,
        end: Vector3
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (start.dst2(end) <= 1e-6f) {
            return false
        }
        if (!store.updateWallEndpoints(id, start, end)) {
            return false
        }
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun addArchitectureHoleToWall(
        group: GroupNode,
        wallId: String,
        cornerA: Vector3,
        cornerB: Vector3
    ): Boolean {
        val store = group.architectureStore ?: return false
        val wall = store.wallById(wallId) ?: return false
        val candidate = wallHoleCandidate(wall, cornerA, cornerB) ?: return false
        val added = store.addHole(
            wallId = wall.id,
            u0 = candidate.u0,
            u1 = candidate.u1,
            v0 = candidate.v0,
            v1 = candidate.v1
        ) ?: return false
        if (added.u1 - added.u0 <= 1e-4f || added.v1 - added.v0 <= 1e-4f) {
            return false
        }
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun addArchitectureHoleToNearestWall(
        group: GroupNode,
        cornerA: Vector3,
        cornerB: Vector3
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (
            store.allWalls().isEmpty() ||
            store.allSlabs().isNotEmpty() ||
            store.allStairs().isNotEmpty() ||
            store.allFrames().isNotEmpty()
        ) {
            return false
        }
        val candidate = findNearestWallHoleCandidate(store, cornerA, cornerB) ?: return false
        return addArchitectureHoleToWall(group, candidate.wall.id, cornerA, cornerB)
    }

    fun architectureHoleGuideSegmentsWorld(
        group: GroupNode,
        includeDiagonals: Boolean = true
    ): List<Pair<Vector3, Vector3>> {
        val store = group.architectureStore ?: return emptyList()
        val guides = mutableListOf<Pair<Vector3, Vector3>>()
        store.allWalls().forEach { wall ->
            wall.holes.forEach { hole ->
                guides.addAll(holeContourSegments(wall, hole, includeDiagonals = includeDiagonals))
            }
        }
        return guides
    }

    fun architectureHoleHandleMarkersWorld(
        group: GroupNode,
        wallId: String? = null
    ): List<HoleHandleMarker> {
        val store = group.architectureStore ?: return emptyList()
        val handles = mutableListOf<HoleHandleMarker>()
        store.allWalls().forEach { wall ->
            if (wallId != null && wall.id != wallId) {
                return@forEach
            }
            val basis = wallBasis(wall) ?: return@forEach
            wall.holes.forEach { hole ->
                val p0 = wallPoint(basis, wall, hole.u0, hole.v0, 1f, 0.002f)
                val p1 = wallPoint(basis, wall, hole.u1, hole.v0, 1f, 0.002f)
                val p2 = wallPoint(basis, wall, hole.u1, hole.v1, 1f, 0.002f)
                val p3 = wallPoint(basis, wall, hole.u0, hole.v1, 1f, 0.002f)
                val e0 = Vector3(p0).lerp(p1, 0.5f)
                val e1 = Vector3(p1).lerp(p2, 0.5f)
                val e2 = Vector3(p2).lerp(p3, 0.5f)
                val e3 = Vector3(p3).lerp(p0, 0.5f)
                val center = Vector3(
                    (p0.x + p1.x + p2.x + p3.x) * 0.25f,
                    (p0.y + p1.y + p2.y + p3.y) * 0.25f,
                    (p0.z + p1.z + p2.z + p3.z) * 0.25f
                )
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.CORNER_0, p0))
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.CORNER_1, p1))
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.CORNER_2, p2))
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.CORNER_3, p3))
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.EDGE_0, e0))
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.EDGE_1, e1))
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.EDGE_2, e2))
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.EDGE_3, e3))
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.CENTER, center))
            }
        }
        return handles
    }

    fun architectureWallEndpointHandleMarkersWorld(
        group: GroupNode,
        wallId: String? = null
    ): List<WallEndpointHandleMarker> {
        val store = group.architectureStore ?: return emptyList()
        val targetWall = when {
            wallId != null -> store.wallById(wallId)
            else -> store.selectedWall()
        } ?: return emptyList()
        val startWorld = group.toWorld(targetWall.start)
        val endWorld = group.toWorld(targetWall.end)
        val halfSize = max(0.2f, targetWall.thickness * 0.65f)
        return listOf(
            WallEndpointHandleMarker(
                wallId = targetWall.id,
                draggingStart = true,
                center = Vector3(startWorld),
                halfSize = halfSize
            ),
            WallEndpointHandleMarker(
                wallId = targetWall.id,
                draggingStart = false,
                center = Vector3(endWorld),
                halfSize = halfSize
            )
        )
    }

    fun updateArchitectureHoleByHandle(
        group: GroupNode,
        wallId: String,
        holeId: String,
        handleKind: HoleHandleKind,
        targetWorld: Vector3
    ): Boolean {
        val store = group.architectureStore ?: return false
        val wall = store.wallById(wallId) ?: return false
        val hole = wall.holes.firstOrNull { it.id == holeId } ?: return false
        val basis = wallBasis(wall) ?: return false
        val projected = projectToWall(basis, targetWorld)

        var u0 = hole.u0
        var u1 = hole.u1
        var v0 = hole.v0
        var v1 = hole.v1

        when (handleKind) {
            HoleHandleKind.CENTER -> {
                val width = (hole.u1 - hole.u0).coerceAtLeast(0.05f)
                val height = (hole.v1 - hole.v0).coerceAtLeast(0.05f)
                val safeWidth = width.coerceAtMost((basis.length - 0.02f).coerceAtLeast(0.05f))
                val safeHeight = height.coerceAtMost((wall.height - 0.02f).coerceAtLeast(0.05f))
                val centerU = projected.x.coerceIn(safeWidth * 0.5f, basis.length - safeWidth * 0.5f)
                val centerV = projected.y.coerceIn(safeHeight * 0.5f, wall.height - safeHeight * 0.5f)
                u0 = centerU - safeWidth * 0.5f
                u1 = centerU + safeWidth * 0.5f
                v0 = centerV - safeHeight * 0.5f
                v1 = centerV + safeHeight * 0.5f
            }
            HoleHandleKind.CORNER_0 -> {
                u0 = projected.x.coerceIn(0f, basis.length)
                v0 = projected.y.coerceIn(0f, wall.height)
            }
            HoleHandleKind.CORNER_1 -> {
                u1 = projected.x.coerceIn(0f, basis.length)
                v0 = projected.y.coerceIn(0f, wall.height)
            }
            HoleHandleKind.CORNER_2 -> {
                u1 = projected.x.coerceIn(0f, basis.length)
                v1 = projected.y.coerceIn(0f, wall.height)
            }
            HoleHandleKind.CORNER_3 -> {
                u0 = projected.x.coerceIn(0f, basis.length)
                v1 = projected.y.coerceIn(0f, wall.height)
            }
            HoleHandleKind.EDGE_0 -> {
                v0 = projected.y.coerceIn(0f, wall.height)
            }
            HoleHandleKind.EDGE_1 -> {
                u1 = projected.x.coerceIn(0f, basis.length)
            }
            HoleHandleKind.EDGE_2 -> {
                v1 = projected.y.coerceIn(0f, wall.height)
            }
            HoleHandleKind.EDGE_3 -> {
                u0 = projected.x.coerceIn(0f, basis.length)
            }
        }

        if (!store.updateHole(wallId, holeId, u0, u1, v0, v1)) {
            return false
        }
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun deleteSelectedArchitectureHoleContours(group: GroupNode): Int {
        val store = group.architectureStore ?: return 0
        val selected = group.lineStore.getSelected().toList()
        if (selected.isEmpty()) {
            return 0
        }
        val removed = store.removeHoles { wall, hole ->
            val contours = holeContourSegments(wall, hole, includeDiagonals = true)
            contours.any { contour ->
                selected.any { seg ->
                    segmentsApproxEqual(seg.start, seg.end, contour.first, contour.second)
                }
            }
        }
        if (removed > 0) {
            rebuildArchitectureGeometry(group.prototype)
            notifyChange()
        }
        return removed
    }

    fun createGroupFromSelection(): GroupNode? {
        val parent = activeGroup
        val selectedEdges = parent.lineStore.getSelected().toList()
        val selectedFaces = parent.faceStore.getSelected().toList()
        val selectedChildren = selectedGroups.filter { it.parent == parent }
        if (selectedEdges.isEmpty() && selectedFaces.isEmpty() && selectedChildren.isEmpty()) {
            return null
        }
        val bounds = BoundingBox()
        var hasBounds = false
        selectedEdges.forEach { seg ->
            if (!hasBounds) {
                bounds.set(seg.start, seg.start)
                hasBounds = true
            }
            bounds.ext(seg.start)
            bounds.ext(seg.end)
        }
        selectedFaces.forEach { tri ->
            if (!hasBounds) {
                bounds.set(tri.a, tri.a)
                hasBounds = true
            }
            bounds.ext(tri.a)
            bounds.ext(tri.b)
            bounds.ext(tri.c)
        }
        selectedChildren.forEach { child ->
            val childBounds = child.worldBounds()
            if (childBounds != null) {
                val parentSpace = boundsInParent(childBounds, parent)
                if (parentSpace != null) {
                    if (!hasBounds) {
                        bounds.set(parentSpace)
                        hasBounds = true
                    } else {
                        bounds.ext(parentSpace)
                    }
                }
            }
        }
        if (!hasBounds) {
            return null
        }
        val origin = Vector3(bounds.min)
        val prototype = ObjectPrototype(
            id = java.util.UUID.randomUUID().toString(),
            name = "Object",
            definitionOrigin = Vector3(),
            definitionAxisU = Vector3(1f, 0f, 0f),
            definitionAxisV = Vector3(0f, 1f, 0f),
            definitionAxisW = Vector3(0f, 0f, 1f),
            gluedToSurface = false,
            kind = PrototypeKind.MESH,
            voxelColor = Color(defaultFaceColor),
            voxelStore = null,
            architectureStore = null,
            lineStore = DraftLineStore(),
            faceStore = DraftFaceStore(defaultFaceColor),
            dimensionStore = DraftDimensionStore(),
            textStore = DraftTextStore()
        )
        registerPrototype(prototype)
        val group = GroupNode(
            id = java.util.UUID.randomUUID().toString(),
            prototype = prototype,
            instanceOrigin = origin,
            instanceAxisU = Vector3(1f, 0f, 0f),
            instanceAxisV = Vector3(0f, 1f, 0f),
            instanceAxisW = Vector3(0f, 0f, 1f)
        )
        group.parent = parent
        parent.children.add(group)
        registerInstance(group)
        applyChangeListener(group)

        if (selectedEdges.isNotEmpty()) {
            parent.lineStore.deleteSelected()
            selectedEdges.forEach { seg ->
                val a = Vector3(seg.start).sub(origin)
                val b = Vector3(seg.end).sub(origin)
                group.lineStore.addSegment(a, b, autoCleanup = false)
            }
        }
        if (selectedFaces.isNotEmpty()) {
            val colors = selectedFaces.associateWith { face -> parent.faceStore.colorFor(face) }
            parent.faceStore.deleteSelected()
            selectedFaces.forEach { tri ->
                val a = Vector3(tri.a).sub(origin)
                val b = Vector3(tri.b).sub(origin)
                val c = Vector3(tri.c).sub(origin)
                group.faceStore.addTriangle(a, b, c, colors[tri] ?: defaultFaceColor)
            }
        }
        val selectedDimensions = parent.dimensionStore.getSelected()
        if (selectedDimensions.isNotEmpty()) {
            parent.dimensionStore.deleteSelected()
            selectedDimensions.forEach { dimension ->
                group.dimensionStore.addDimension(
                    Vector3(dimension.start).sub(origin),
                    Vector3(dimension.end).sub(origin),
                    Vector3(dimension.offset).sub(origin)
                )
            }
        }
        val selectedTexts = parent.textStore.getSelected()
        if (selectedTexts.isNotEmpty()) {
            parent.textStore.deleteSelected()
            selectedTexts.forEach { text ->
                group.textStore.addText(Vector3(text.position).sub(origin), text.text)
            }
        }
        if (selectedChildren.isNotEmpty()) {
            selectedChildren.forEach { child ->
                parent.children.remove(child)
                reparentGroup(child, group)
            }
            clearGroupSelection()
        }
        parent.lineStore.clearSelection()
        parent.faceStore.clearSelection()
        parent.dimensionStore.clearSelection()
        parent.textStore.clearSelection()
        clearGroupSelection()
        selectedGroups.add(group)
        applyChangeListenerToAll()
        notifyChange()
        if (isEditing()) {
            syncPrototypeInstances(activeGroup)
        }
        return group
    }

    fun ungroupSelected(): Int {
        val targets = selectedGroups.toList()
        if (targets.isEmpty()) {
            return 0
        }
        data class ParentSnapshot(
            val lines: Set<DraftLineStore.Segment>,
            val faces: Set<DraftFaceStore.Triangle>,
            val dimensions: Set<DraftDimensionStore.LinearDimension>,
            val texts: Set<DraftTextStore.TextEntity>
        )
        val parentSnapshots = mutableMapOf<GroupNode, ParentSnapshot>()
        val movedChildren = mutableMapOf<GroupNode, MutableList<GroupNode>>()
        val lineParentsWithChanges = mutableSetOf<GroupNode>()
        val faceParentsWithChanges = mutableSetOf<GroupNode>()
        targets.forEach { group ->
            val parent = group.parent ?: return@forEach
            parentSnapshots.getOrPut(parent) {
                ParentSnapshot(
                    lines = parent.lineStore.getSegments().toSet(),
                    faces = parent.faceStore.getTriangles().toSet(),
                    dimensions = parent.dimensionStore.getDimensions().toSet(),
                    texts = parent.textStore.getTexts().toSet()
                )
            }
        }
        var count = 0
        targets.forEach { group ->
            val parent = group.parent ?: return@forEach
            // Transform cached prototype geometry to parent space using a single precomputed matrix.
            val toParentMatrix = Matrix4(group.instanceMatrix()).mul(group.definitionMatrix())
            val lines = group.lineStore.getSegments()
            if (lines.isNotEmpty()) {
                parent.lineStore.withChangeSuppressed {
                    lines.forEach { seg ->
                        val a = transformPoint(toParentMatrix, seg.start)
                        val b = transformPoint(toParentMatrix, seg.end)
                        parent.lineStore.addSegment(a, b, autoCleanup = false)
                    }
                }
                lineParentsWithChanges.add(parent)
            }
            val faces = group.faceStore.getTriangles()
            if (faces.isNotEmpty()) {
                parent.faceStore.withChangeSuppressed {
                    faces.forEach { tri ->
                        val color = group.faceStore.colorFor(tri)
                        val a = transformPoint(toParentMatrix, tri.a)
                        val b = transformPoint(toParentMatrix, tri.b)
                        val c = transformPoint(toParentMatrix, tri.c)
                        parent.faceStore.addTriangle(a, b, c, color)
                    }
                }
                faceParentsWithChanges.add(parent)
            }
            group.dimensionStore.getDimensions().forEach { dimension ->
                val a = transformPoint(toParentMatrix, dimension.start)
                val b = transformPoint(toParentMatrix, dimension.end)
                val o = transformPoint(toParentMatrix, dimension.offset)
                parent.dimensionStore.addDimension(a, b, o)
            }
            group.textStore.getTexts().forEach { text ->
                val position = transformPoint(toParentMatrix, text.position)
                parent.textStore.addText(position, text.text)
            }
            group.children.forEach { child ->
                reparentGroup(child, parent)
                movedChildren.getOrPut(parent) { mutableListOf() }.add(child)
            }
            group.children.clear()
            parent.children.remove(group)
            unregisterInstance(group)
            count++
        }
        lineParentsWithChanges.forEach { parent -> parent.lineStore.notifyExternalChange() }
        faceParentsWithChanges.forEach { parent -> parent.faceStore.notifyExternalChange() }
        clearGroupSelection()
        parentSnapshots.forEach { (parent, snapshot) ->
            parent.lineStore.clearSelection()
            parent.faceStore.clearSelection()
            parent.dimensionStore.clearSelection()
            parent.textStore.clearSelection()
            parent.lineStore.getSegments().filter { it !in snapshot.lines }.forEach { parent.lineStore.addSelection(it) }
            parent.faceStore.getTriangles().filter { it !in snapshot.faces }.forEach { parent.faceStore.addSelection(it) }
            parent.dimensionStore.getDimensions().filter { it !in snapshot.dimensions }.forEach { parent.dimensionStore.addSelection(it) }
            parent.textStore.getTexts().filter { it !in snapshot.texts }.forEach { parent.textStore.addSelection(it) }
            movedChildren[parent].orEmpty().forEach { addGroupSelection(it) }
        }
        applyChangeListenerToAll()
        notifyChange()
        if (isEditing()) {
            syncPrototypeInstances(activeGroup)
        }
        return count
    }

    fun deleteSelectedGroups(): Int {
        val targets = selectedGroups.toList()
        if (targets.isEmpty()) {
            return 0
        }
        targets.forEach { group ->
            group.parent?.children?.remove(group)
            unregisterInstance(group)
        }
        clearGroupSelection()
        applyChangeListenerToAll()
        notifyChange()
        if (isEditing()) {
            syncPrototypeInstances(activeGroup)
        }
        return targets.size
    }

    fun transformSelectedGroups(
        pointTransform: (Vector3) -> Vector3,
        vectorTransform: (Vector3) -> Vector3
    ): Int {
        val targets = selectedGroups.toList()
        if (targets.isEmpty()) {
            return 0
        }
        targets.forEach { group ->
            val newOriginWorld = pointTransform(group.worldOrigin())
            val worldAxes = group.worldAxes()
            val newU = vectorTransform(worldAxes.u)
            val newV = vectorTransform(worldAxes.v)
            val newW = vectorTransform(worldAxes.w)
            val parent = group.parent
            if (parent == null) {
                group.setInstanceFromWorld(newOriginWorld, newU, newV, newW)
            } else {
                group.setInstanceFromWorld(newOriginWorld, newU, newV, newW)
            }
        }
        applyChangeListenerToAll()
        notifyChange()
        if (isEditing()) {
            syncPrototypeInstances(activeGroup)
        }
        return targets.size
    }

    fun copySelectedGroups(
        pointTransform: (Vector3) -> Vector3,
        vectorTransform: (Vector3) -> Vector3
    ): Int {
        val targets = selectedGroups.toList()
        if (targets.isEmpty()) {
            return 0
        }
        clearGroupSelection()
        targets.forEach { group ->
            val clone = cloneGroup(group)
            val newOriginWorld = pointTransform(group.worldOrigin())
            val worldAxes = group.worldAxes()
            val newU = vectorTransform(worldAxes.u)
            val newV = vectorTransform(worldAxes.v)
            val newW = vectorTransform(worldAxes.w)
            val parent = group.parent
            if (parent == null) {
                clone.parent = root
                root.children.add(clone)
            } else {
                clone.parent = parent
                parent.children.add(clone)
            }
            clone.setInstanceFromWorld(newOriginWorld, newU, newV, newW)
            registerInstance(clone)
            selectedGroups.add(clone)
        }
        applyChangeListenerToAll()
        notifyChange()
        if (isEditing()) {
            syncPrototypeInstances(activeGroup)
        }
        return targets.size
    }

    fun createInstanceAtWorld(
        prototype: ObjectPrototype,
        parent: GroupNode,
        originWorld: Vector3,
        worldU: Vector3 = Vector3(1f, 0f, 0f),
        worldV: Vector3 = Vector3(0f, 1f, 0f),
        worldW: Vector3 = Vector3(0f, 0f, 1f)
    ): GroupNode {
        val instance = GroupNode(
            id = java.util.UUID.randomUUID().toString(),
            prototype = prototype,
            instanceOrigin = Vector3(),
            instanceAxisU = Vector3(1f, 0f, 0f),
            instanceAxisV = Vector3(0f, 1f, 0f),
            instanceAxisW = Vector3(0f, 0f, 1f)
        )
        val template = prototypeInstances[prototype.id]?.firstOrNull()
        template?.children?.forEach { child ->
            val childClone = cloneGroup(child)
            childClone.parent = instance
            instance.children.add(childClone)
        }
        instance.parent = parent
        parent.children.add(instance)
        instance.setInstanceFromWorld(originWorld, worldU, worldV, worldW)
        registerInstance(instance)
        applyChangeListener(instance)
        notifyChange()
        return instance
    }

    fun walkGroups(rootNode: GroupNode = root, visitor: (GroupNode) -> Unit) {
        rootNode.children.forEach { child ->
            visitor(child)
            walkGroups(child, visitor)
        }
    }

    fun collectWorldTriangles(consumer: (Vector3, Vector3, Vector3, Color, Boolean) -> Unit) {
        walkGroups(root) { group ->
            group.faceStore.getTriangles().forEach { tri ->
                val a = group.toWorld(tri.a)
                val b = group.toWorld(tri.b)
                val c = group.toWorld(tri.c)
                val color = group.faceStore.colorFor(tri)
                consumer(a, b, c, color, group.faceStore.isSelected(tri))
            }
        }
        root.faceStore.getTriangles().forEach { tri ->
            val color = root.faceStore.colorFor(tri)
            consumer(
                Vector3(tri.a),
                Vector3(tri.b),
                Vector3(tri.c),
                color,
                root.faceStore.isSelected(tri)
            )
        }
    }

    fun collectWorldLines(consumer: (Vector3, Vector3) -> Unit) {
        walkGroups(root) { group ->
            group.lineStore.getSegments().forEach { seg ->
                consumer(group.toWorld(seg.start), group.toWorld(seg.end))
            }
        }
        root.lineStore.getSegments().forEach { seg ->
            consumer(Vector3(seg.start), Vector3(seg.end))
        }
    }

    fun collectWorldDimensions(consumer: (Vector3, Vector3, Vector3, Boolean) -> Unit) {
        walkGroups(root) { group ->
            group.dimensionStore.getDimensions().forEach { dim ->
                consumer(
                    group.toWorld(dim.start),
                    group.toWorld(dim.end),
                    group.toWorld(dim.offset),
                    group.dimensionStore.isSelected(dim)
                )
            }
        }
        root.dimensionStore.getDimensions().forEach { dim ->
            consumer(
                Vector3(dim.start),
                Vector3(dim.end),
                Vector3(dim.offset),
                root.dimensionStore.isSelected(dim)
            )
        }
    }

    fun collectWorldTexts(consumer: (Vector3, String, Float, Vector3, Vector3, Boolean, Boolean) -> Unit) {
        walkGroups(root) { group ->
            group.textStore.getTexts().forEach { text ->
                consumer(
                    group.toWorld(text.position),
                    text.text,
                    text.size,
                    group.vectorToWorld(text.normal),
                    group.vectorToWorld(text.axisU),
                    group.textStore.isSelected(text),
                    text.screenText
                )
            }
        }
        root.textStore.getTexts().forEach { text ->
            consumer(
                Vector3(text.position),
                text.text,
                text.size,
                Vector3(text.normal),
                Vector3(text.axisU),
                root.textStore.isSelected(text),
                text.screenText
            )
        }
    }

    fun rebuildArchitectureGeometry(prototype: ObjectPrototype) {
        if (prototype.kind != PrototypeKind.ARCHITECTURE) {
            return
        }
        val store = prototype.architectureStore ?: return
        val lineStore = prototype.lineStore
        val faceStore = prototype.faceStore

        lineStore.withChangeSuppressed {
            lineStore.clearAll()
        }
        faceStore.withChangeSuppressed {
            faceStore.clearAll()
        }

        if (store.allWalls().isEmpty() &&
            store.allSlabs().isEmpty() &&
            store.allStairs().isEmpty() &&
            store.allFrames().isEmpty()
        ) {
            lineStore.notifyExternalChange()
            faceStore.notifyExternalChange()
            return
        }

        val wallJoinShifts = computeWallJoinShifts(store.allWalls())
        faceStore.withChangeSuppressed {
            lineStore.withChangeSuppressed {
                store.allWalls().forEach { wall ->
                    appendWallGeometry(
                        faceStore = faceStore,
                        lineStore = lineStore,
                        wall = wall,
                        exteriorColor = wall.exteriorColor,
                        interiorColor = wall.interiorColor,
                        joinShift = wallJoinShifts[wall.id] ?: WallJoinShift()
                    )
                }
                store.allSlabs().forEach { slab ->
                    appendSlabGeometry(faceStore, lineStore, slab, slab.topColor, slab.bottomColor, slab.sideColor)
                }
                store.allStairs().forEach { stair ->
                    appendStairGeometry(faceStore, lineStore, stair, stair.treadColor, stair.supportColor)
                }
                store.allFrames().forEach { frame ->
                    appendFrameGeometry(faceStore, lineStore, frame, frame.color)
                }
            }
        }
        lineStore.cleanupJts()
        lineStore.notifyExternalChange()
        faceStore.notifyExternalChange()
    }

    private data class WallBasis(
        val start: Vector3,
        val dir: Vector3,
        val up: Vector3,
        val normal: Vector3,
        val length: Float
    )

    private data class WallJoinShift(
        val startInnerAlongDirBottom: Float = 0f,
        val startInnerAlongDirTop: Float = 0f,
        val endInnerAlongDirBottom: Float = 0f,
        val endInnerAlongDirTop: Float = 0f,
        val startOuterAlongDirBottom: Float = 0f,
        val startOuterAlongDirTop: Float = 0f,
        val endOuterAlongDirBottom: Float = 0f,
        val endOuterAlongDirTop: Float = 0f,
        val startJoined: Boolean = false,
        val endJoined: Boolean = false
    )

    private data class HoleCandidate(
        val wall: ArchitectureStore.WallSegment,
        val u0: Float,
        val u1: Float,
        val v0: Float,
        val v1: Float,
        val planeDistance: Float
    )

    private fun findNearestWallHoleCandidate(
        store: ArchitectureStore,
        cornerA: Vector3,
        cornerB: Vector3
    ): HoleCandidate? {
        var best: HoleCandidate? = null
        store.allWalls().forEach { wall ->
            val candidate = wallHoleCandidate(wall, cornerA, cornerB) ?: return@forEach
            if (best == null || candidate.planeDistance < best!!.planeDistance) {
                best = candidate
            }
        }
        return best
    }

    private fun wallHoleCandidate(
        wall: ArchitectureStore.WallSegment,
        cornerA: Vector3,
        cornerB: Vector3
    ): HoleCandidate? {
        val basis = wallBasis(wall) ?: return null
        val pa = projectToWall(basis, cornerA)
        val pb = projectToWall(basis, cornerB)
        val u0 = min(pa.x, pb.x).coerceIn(0f, basis.length)
        val u1 = max(pa.x, pb.x).coerceIn(0f, basis.length)
        val v0 = min(pa.y, pb.y).coerceAtLeast(0f).coerceAtMost(wall.height)
        val v1 = max(pa.y, pb.y).coerceAtLeast(0f).coerceAtMost(wall.height)
        if (u1 - u0 <= 0.05f || v1 - v0 <= 0.05f) {
            return null
        }
        val dist = min(
            min(abs(pa.z), abs(pa.z - wall.thickness)),
            min(abs(pb.z), abs(pb.z - wall.thickness))
        )
        return HoleCandidate(wall, u0, u1, v0, v1, dist)
    }

    private fun projectToWall(basis: WallBasis, point: Vector3): Vector3 {
        val rel = Vector3(point).sub(basis.start)
        return Vector3(
            rel.dot(basis.dir),
            rel.dot(basis.up),
            rel.dot(basis.normal)
        )
    }

    private fun holeContourSegments(
        wall: ArchitectureStore.WallSegment,
        hole: ArchitectureStore.RectHole,
        includeDiagonals: Boolean = false
    ): List<Pair<Vector3, Vector3>> {
        val basis = wallBasis(wall) ?: return emptyList()
        val eps = 0.0015f
        val p0 = wallPoint(basis, wall, hole.u0, hole.v0, 1f, eps)
        val p1 = wallPoint(basis, wall, hole.u1, hole.v0, 1f, eps)
        val p2 = wallPoint(basis, wall, hole.u1, hole.v1, 1f, eps)
        val p3 = wallPoint(basis, wall, hole.u0, hole.v1, 1f, eps)
        val segments = mutableListOf(
            p0 to p1,
            p1 to p2,
            p2 to p3,
            p3 to p0
        )
        if (includeDiagonals) {
            segments.add(p0 to p2)
            segments.add(p1 to p3)
        }
        return segments
    }

    private fun segmentsApproxEqual(
        a0: Vector3,
        a1: Vector3,
        b0: Vector3,
        b1: Vector3,
        epsilon: Float = 1e-3f
    ): Boolean {
        val eps2 = epsilon * epsilon
        val direct = a0.dst2(b0) <= eps2 && a1.dst2(b1) <= eps2
        val swapped = a0.dst2(b1) <= eps2 && a1.dst2(b0) <= eps2
        return direct || swapped
    }

    private fun wallBasis(wall: ArchitectureStore.WallSegment): WallBasis? {
        val dir = Vector3(wall.end).sub(wall.start)
        val len = dir.len()
        if (len <= 1e-6f) {
            return null
        }
        dir.scl(1f / len)
        var up = Vector3(0f, 1f, 0f)
        if (abs(dir.dot(up)) > 0.99f) {
            up = Vector3(1f, 0f, 0f)
        }
        val normal = Vector3(up).crs(dir)
        if (normal.len2() <= 1e-6f) {
            return null
        }
        normal.nor()
        val correctedUp = Vector3(dir).crs(normal).nor()
        return WallBasis(
            start = Vector3(wall.start),
            dir = dir,
            up = correctedUp,
            normal = normal,
            length = len
        )
    }

    private data class EndpointKey(val x: Int, val y: Int, val z: Int)

    private data class WallEndpointRef(
        val wall: ArchitectureStore.WallSegment,
        val atStart: Boolean,
        val point: Vector3,
        val basis: WallBasis
    )

    private fun computeWallJoinShifts(walls: List<ArchitectureStore.WallSegment>): Map<String, WallJoinShift> {
        if (walls.size < 2) {
            return emptyMap()
        }
        val basisById = mutableMapOf<String, WallBasis>()
        walls.forEach { wall ->
            val basis = wallBasis(wall) ?: return@forEach
            basisById[wall.id] = basis
        }
        if (basisById.size < 2) {
            return emptyMap()
        }

        val snap = 1e-3f
        fun keyOf(point: Vector3): EndpointKey {
            return EndpointKey(
                (point.x / snap).roundToInt(),
                (point.y / snap).roundToInt(),
                (point.z / snap).roundToInt()
            )
        }

        val refsByKey = mutableMapOf<EndpointKey, MutableList<WallEndpointRef>>()
        walls.forEach { wall ->
            val basis = basisById[wall.id] ?: return@forEach
            val start = Vector3(wall.start)
            val end = Vector3(wall.end)
            refsByKey.getOrPut(keyOf(start)) { mutableListOf() }
                .add(WallEndpointRef(wall, atStart = true, point = start, basis = basis))
            refsByKey.getOrPut(keyOf(end)) { mutableListOf() }
                .add(WallEndpointRef(wall, atStart = false, point = end, basis = basis))
        }

        val startShiftInnerBottom = mutableMapOf<String, Float>()
        val startShiftInnerTop = mutableMapOf<String, Float>()
        val endShiftInnerBottom = mutableMapOf<String, Float>()
        val endShiftInnerTop = mutableMapOf<String, Float>()
        val startShiftBottom = mutableMapOf<String, Float>()
        val startShiftTop = mutableMapOf<String, Float>()
        val endShiftBottom = mutableMapOf<String, Float>()
        val endShiftTop = mutableMapOf<String, Float>()
        val startJoined = mutableSetOf<String>()
        val endJoined = mutableSetOf<String>()

        refsByKey.values.forEach { refs ->
            if (refs.size != 2) {
                return@forEach
            }
            val a = refs[0]
            val b = refs[1]
            if (a.wall.id == b.wall.id) {
                return@forEach
            }

            val dirA = if (a.atStart) Vector3(a.basis.dir).scl(-1f) else Vector3(a.basis.dir)
            val dirB = if (b.atStart) Vector3(b.basis.dir).scl(-1f) else Vector3(b.basis.dir)
            val leanA = tan(a.wall.inclinationDeg * PI.toFloat() / 180f) * a.wall.height
            val leanB = tan(b.wall.inclinationDeg * PI.toFloat() / 180f) * b.wall.height

            val baseInnerA = Vector3(a.point)
            val baseInnerB = Vector3(b.point)
            val topInnerA = Vector3(baseInnerA).mulAdd(
                a.basis.normal,
                leanA
            )
            val topInnerB = Vector3(baseInnerB).mulAdd(
                b.basis.normal,
                leanB
            )
            val baseOuterA = Vector3(baseInnerA).mulAdd(a.basis.normal, a.wall.thickness)
            val baseOuterB = Vector3(baseInnerB).mulAdd(b.basis.normal, b.wall.thickness)
            val topOuterA = Vector3(baseOuterA).mulAdd(a.basis.normal, leanA)
            val topOuterB = Vector3(baseOuterB).mulAdd(b.basis.normal, leanB)

            fun solveAlong(
                lineA: Vector3,
                lineB: Vector3
            ): Pair<Float, Float> {
                val hit = intersectLinesXZ(lineA, dirA, lineB, dirB) ?: return 0f to 0f
                val aParam = if (hit.first.isFinite()) hit.first else 0f
                val bParam = if (hit.second.isFinite()) hit.second else 0f
                val alongA = if (a.atStart) -aParam else aParam
                val alongB = if (b.atStart) -bParam else bParam
                return alongA to alongB
            }

            val (alongAInnerBottom, alongBInnerBottom) = solveAlong(baseInnerA, baseInnerB)
            val (alongAInnerTop, alongBInnerTop) = solveAlong(topInnerA, topInnerB)
            val (alongAOuterBottom, alongBOuterBottom) = solveAlong(baseOuterA, baseOuterB)
            val (alongAOuterTop, alongBOuterTop) = solveAlong(topOuterA, topOuterB)

            if (a.atStart) {
                startShiftInnerBottom[a.wall.id] = alongAInnerBottom
                startShiftInnerTop[a.wall.id] = alongAInnerTop
                startShiftBottom[a.wall.id] = alongAOuterBottom
                startShiftTop[a.wall.id] = alongAOuterTop
                startJoined.add(a.wall.id)
            } else {
                endShiftInnerBottom[a.wall.id] = alongAInnerBottom
                endShiftInnerTop[a.wall.id] = alongAInnerTop
                endShiftBottom[a.wall.id] = alongAOuterBottom
                endShiftTop[a.wall.id] = alongAOuterTop
                endJoined.add(a.wall.id)
            }
            if (b.atStart) {
                startShiftInnerBottom[b.wall.id] = alongBInnerBottom
                startShiftInnerTop[b.wall.id] = alongBInnerTop
                startShiftBottom[b.wall.id] = alongBOuterBottom
                startShiftTop[b.wall.id] = alongBOuterTop
                startJoined.add(b.wall.id)
            } else {
                endShiftInnerBottom[b.wall.id] = alongBInnerBottom
                endShiftInnerTop[b.wall.id] = alongBInnerTop
                endShiftBottom[b.wall.id] = alongBOuterBottom
                endShiftTop[b.wall.id] = alongBOuterTop
                endJoined.add(b.wall.id)
            }
        }

        return basisById.keys.associateWith { id ->
            WallJoinShift(
                startInnerAlongDirBottom = startShiftInnerBottom[id] ?: 0f,
                startInnerAlongDirTop = startShiftInnerTop[id] ?: (startShiftInnerBottom[id] ?: 0f),
                endInnerAlongDirBottom = endShiftInnerBottom[id] ?: 0f,
                endInnerAlongDirTop = endShiftInnerTop[id] ?: (endShiftInnerBottom[id] ?: 0f),
                startOuterAlongDirBottom = startShiftBottom[id] ?: 0f,
                startOuterAlongDirTop = startShiftTop[id] ?: (startShiftBottom[id] ?: 0f),
                endOuterAlongDirBottom = endShiftBottom[id] ?: 0f,
                endOuterAlongDirTop = endShiftTop[id] ?: (endShiftBottom[id] ?: 0f),
                startJoined = startJoined.contains(id),
                endJoined = endJoined.contains(id)
            )
        }
    }

    private fun intersectLinesXZ(
        p: Vector3,
        d: Vector3,
        q: Vector3,
        e: Vector3
    ): Pair<Float, Float>? {
        val cross = d.x * e.z - d.z * e.x
        if (abs(cross) <= 1e-5f) {
            return null
        }
        val dx = q.x - p.x
        val dz = q.z - p.z
        val t = (dx * e.z - dz * e.x) / cross
        val s = (dx * d.z - dz * d.x) / cross
        return t to s
    }

    private fun wallPoint(
        basis: WallBasis,
        wall: ArchitectureStore.WallSegment,
        u: Float,
        v: Float,
        side: Float,
        extra: Float = 0f
    ): Vector3 {
        val sideOffset = if (side >= 0f) wall.thickness + extra else -extra
        val safeHeight = wall.height.coerceAtLeast(0.0001f)
        val inclinationOffset = tan(wall.inclinationDeg * PI.toFloat() / 180f) * wall.height
        val lean = inclinationOffset * (v / safeHeight)
        return Vector3(basis.start)
            .mulAdd(basis.dir, u)
            .mulAdd(basis.up, v)
            .mulAdd(basis.normal, sideOffset + lean)
    }

    private data class FrameSelectionBasis(
        val origin: Vector3,
        val normal: Vector3,
        val axisU: Vector3,
        val axisV: Vector3,
        val uMin: Float,
        val uMax: Float,
        val vMin: Float,
        val vMax: Float,
        val nMin: Float,
        val nMax: Float
    )

    private fun architectureWallDistanceSq(wall: ArchitectureStore.WallSegment, point: Vector3): Float {
        val basis = wallBasis(wall) ?: return Float.POSITIVE_INFINITY
        val projected = projectToWall(basis, point)
        val safeHeight = wall.height.coerceAtLeast(0.0001f)
        val vOnWall = projected.y.coerceIn(0f, wall.height)
        val lean = tan(wall.inclinationDeg * PI.toFloat() / 180f) * vOnWall
        val n = projected.z - lean
        val du = rangeDistance(projected.x, 0f, basis.length)
        val dv = rangeDistance(projected.y, 0f, wall.height)
        val dn = rangeDistance(n, 0f, wall.thickness)
        return du * du + dv * dv + dn * dn
    }

    private fun architectureSlabDistanceSq(slab: ArchitectureStore.Slab, point: Vector3): Float {
        val minX = min(slab.min.x, slab.max.x)
        val maxX = max(slab.min.x, slab.max.x)
        val minZ = min(slab.min.z, slab.max.z)
        val maxZ = max(slab.min.z, slab.max.z)
        val baseY = min(slab.min.y, slab.max.y)
        val topY = baseY + slab.thickness.coerceAtLeast(0.01f)
        val dx = rangeDistance(point.x, minX, maxX)
        val dy = rangeDistance(point.y, baseY, topY)
        val dz = rangeDistance(point.z, minZ, maxZ)
        return dx * dx + dy * dy + dz * dz
    }

    private fun architectureStairDistanceSq(stair: ArchitectureStore.Stair, point: Vector3): Float {
        val minX = min(stair.min.x, stair.max.x)
        val maxX = max(stair.min.x, stair.max.x)
        val minZ = min(stair.min.z, stair.max.z)
        val maxZ = max(stair.min.z, stair.max.z)
        val baseY = min(stair.min.y, stair.max.y)
        val maxY = baseY + stair.height.coerceAtLeast(0.05f)
        val minY = baseY - stair.supportThickness.coerceAtLeast(0.01f)
        val dx = rangeDistance(point.x, minX, maxX)
        val dy = rangeDistance(point.y, minY, maxY)
        val dz = rangeDistance(point.z, minZ, maxZ)
        return dx * dx + dy * dy + dz * dz
    }

    private fun architectureFrameDistanceSq(frame: ArchitectureStore.Frame, point: Vector3): Float {
        val basis = frameSelectionBasis(frame) ?: return Float.POSITIVE_INFINITY
        val rel = Vector3(point).sub(basis.origin)
        val u = rel.dot(basis.axisU)
        val v = rel.dot(basis.axisV)
        val n = rel.dot(basis.normal)
        val du = rangeDistance(u, basis.uMin, basis.uMax)
        val dv = rangeDistance(v, basis.vMin, basis.vMax)
        val dn = rangeDistance(n, basis.nMin, basis.nMax)
        return du * du + dv * dv + dn * dn
    }

    private fun frameSelectionBasis(frame: ArchitectureStore.Frame): FrameSelectionBasis? {
        val normal = Vector3(frame.normal)
        if (normal.len2() <= 1e-6f) {
            normal.set(0f, 1f, 0f)
        } else {
            normal.nor()
        }
        var axisU = if (abs(normal.y) < 0.9f) {
            Vector3(0f, 1f, 0f).crs(normal)
        } else {
            Vector3(1f, 0f, 0f).crs(normal)
        }
        if (axisU.len2() <= 1e-6f) {
            axisU = Vector3(0f, 0f, 1f).crs(normal)
        }
        if (axisU.len2() <= 1e-6f) {
            return null
        }
        axisU.nor()
        var axisV = Vector3(normal).crs(axisU)
        if (axisV.len2() <= 1e-6f) {
            return null
        }
        axisV.nor()

        val origin = Vector3(frame.cornerA)
        val delta = Vector3(frame.cornerB).sub(origin)
        var uLen = delta.dot(axisU)
        var vLen = delta.dot(axisV)
        if (abs(uLen) <= 0.01f || abs(vLen) <= 0.01f) {
            val diagProjected = Vector3(delta).sub(Vector3(normal).scl(delta.dot(normal)))
            if (diagProjected.len2() > 1e-6f) {
                axisU = diagProjected.nor()
                axisV = Vector3(normal).crs(axisU).nor()
                uLen = delta.dot(axisU)
                vLen = delta.dot(axisV)
            }
        }
        if (abs(uLen) <= 0.01f || abs(vLen) <= 0.01f) {
            return null
        }
        return FrameSelectionBasis(
            origin = origin,
            normal = normal,
            axisU = axisU,
            axisV = axisV,
            uMin = min(0f, uLen),
            uMax = max(0f, uLen),
            vMin = min(0f, vLen),
            vMax = max(0f, vLen),
            nMin = 0f,
            nMax = frame.depth.coerceAtLeast(0.01f)
        )
    }

    private fun rangeDistance(value: Float, minValue: Float, maxValue: Float): Float {
        return when {
            value < minValue -> minValue - value
            value > maxValue -> value - maxValue
            else -> 0f
        }
    }

    private fun appendWallGeometry(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        wall: ArchitectureStore.WallSegment,
        exteriorColor: Color,
        interiorColor: Color,
        joinShift: WallJoinShift
    ) {
        val basis = wallBasis(wall) ?: return
        val holes = wall.holes.map { hole ->
            val u0 = min(hole.u0, hole.u1).coerceIn(0.01f, basis.length - 0.01f)
            val u1 = max(hole.u0, hole.u1).coerceIn(0.01f, basis.length - 0.01f)
            val v0 = min(hole.v0, hole.v1).coerceIn(0.01f, wall.height - 0.01f)
            val v1 = max(hole.v0, hole.v1).coerceIn(0.01f, wall.height - 0.01f)
            ArchitectureStore.RectHole(hole.id, u0, u1, v0, v1)
        }.filter { it.u1 - it.u0 > 0.02f && it.v1 - it.v0 > 0.02f }

        fun wallJoinPoint(u: Float, v: Float, side: Float): Vector3 {
            val point = wallPoint(basis, wall, u, v, side)
            if (basis.length <= 1e-6f) {
                return point
            }
            val safeHeight = wall.height.coerceAtLeast(1e-6f)
            val tV = (v / safeHeight).coerceIn(0f, 1f)
            val startAlong = if (side >= 0f) {
                joinShift.startOuterAlongDirBottom * (1f - tV) + joinShift.startOuterAlongDirTop * tV
            } else {
                joinShift.startInnerAlongDirBottom * (1f - tV) + joinShift.startInnerAlongDirTop * tV
            }
            val endAlong = if (side >= 0f) {
                joinShift.endOuterAlongDirBottom * (1f - tV) + joinShift.endOuterAlongDirTop * tV
            } else {
                joinShift.endInnerAlongDirBottom * (1f - tV) + joinShift.endInnerAlongDirTop * tV
            }
            val edgeEps = 1e-4f
            val alongDir = when {
                u <= edgeEps -> startAlong
                u >= basis.length - edgeEps -> endAlong
                else -> 0f
            }
            return point.mulAdd(basis.dir, alongDir)
        }

        fun onHoleBoundary(u: Float, v: Float, epsilon: Float = 1e-4f): Boolean {
            return holes.any { hole ->
                val withinU = u >= hole.u0 - epsilon && u <= hole.u1 + epsilon
                val withinV = v >= hole.v0 - epsilon && v <= hole.v1 + epsilon
                (withinV && (abs(u - hole.u0) <= epsilon || abs(u - hole.u1) <= epsilon)) ||
                    (withinU && (abs(v - hole.v0) <= epsilon || abs(v - hole.v1) <= epsilon))
            }
        }

        fun outerPoint(u: Float, v: Float): Vector3 {
            return if (onHoleBoundary(u, v)) {
                wallPoint(basis, wall, u, v, 1f)
            } else {
                wallJoinPoint(u, v, 1f)
            }
        }

        val uCuts = mutableListOf(0f, basis.length)
        val vCuts = mutableListOf(0f, wall.height)
        holes.forEach { hole ->
            uCuts.add(hole.u0)
            uCuts.add(hole.u1)
            vCuts.add(hole.v0)
            vCuts.add(hole.v1)
        }
        val sortedU = uCuts.distinct().sorted()
        val sortedV = vCuts.distinct().sorted()
        if (sortedU.size < 2 || sortedV.size < 2) {
            return
        }

        val solid = Array(sortedU.size - 1) { BooleanArray(sortedV.size - 1) }
        for (i in 0 until sortedU.lastIndex) {
            for (j in 0 until sortedV.lastIndex) {
                val uMid = (sortedU[i] + sortedU[i + 1]) * 0.5f
                val vMid = (sortedV[j] + sortedV[j + 1]) * 0.5f
                val insideHole = holes.any { hole ->
                    uMid >= hole.u0 && uMid <= hole.u1 &&
                        vMid >= hole.v0 && vMid <= hole.v1
                }
                solid[i][j] = !insideHole
            }
        }

        fun isSolid(i: Int, j: Int): Boolean {
            if (i < 0 || j < 0 || i >= solid.size || j >= solid[i].size) {
                return false
            }
            return solid[i][j]
        }

        for (i in 0 until sortedU.lastIndex) {
            for (j in 0 until sortedV.lastIndex) {
                if (!solid[i][j]) {
                    continue
                }
                val u0 = sortedU[i]
                val u1 = sortedU[i + 1]
                val v0 = sortedV[j]
                val v1 = sortedV[j + 1]

                val ff0 = outerPoint(u0, v0)
                val ff1 = outerPoint(u1, v0)
                val ff2 = outerPoint(u1, v1)
                val ff3 = outerPoint(u0, v1)
                addQuad(faceStore, lineStore, ff0, ff1, ff2, ff3, basis.normal, exteriorColor)

                val bb0 = wallJoinPoint(u0, v0, -1f)
                val bb1 = wallJoinPoint(u1, v0, -1f)
                val bb2 = wallJoinPoint(u1, v1, -1f)
                val bb3 = wallJoinPoint(u0, v1, -1f)
                addQuad(faceStore, lineStore, bb3, bb2, bb1, bb0, Vector3(basis.normal).scl(-1f), interiorColor)

                val isStartBoundary = i == 0
                val isEndBoundary = i == sortedU.lastIndex - 1
                if (!isSolid(i - 1, j) && !(isStartBoundary && joinShift.startJoined)) {
                    addQuad(faceStore, lineStore, ff0, ff3, bb3, bb0, Vector3(basis.dir).scl(-1f), exteriorColor)
                }
                if (!isSolid(i + 1, j) && !(isEndBoundary && joinShift.endJoined)) {
                    addQuad(faceStore, lineStore, ff1, bb1, bb2, ff2, Vector3(basis.dir), exteriorColor)
                }
                if (!isSolid(i, j - 1)) {
                    addQuad(faceStore, lineStore, ff0, bb0, bb1, ff1, Vector3(basis.up).scl(-1f), exteriorColor)
                }
                if (!isSolid(i, j + 1)) {
                    addQuad(faceStore, lineStore, ff3, ff2, bb2, bb3, Vector3(basis.up), exteriorColor)
                }
            }
        }

        holes.forEach { hole ->
            val contours = holeContourSegments(wall, hole, includeDiagonals = true)
            contours.forEach { (a, b) ->
                lineStore.addSegment(a, b, autoCleanup = false)
            }
        }
    }

    private fun appendSlabGeometry(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        slab: ArchitectureStore.Slab,
        topColor: Color,
        bottomColor: Color,
        sideColor: Color
    ) {
        val minX = min(slab.min.x, slab.max.x)
        val maxX = max(slab.min.x, slab.max.x)
        val minZ = min(slab.min.z, slab.max.z)
        val maxZ = max(slab.min.z, slab.max.z)
        val baseY = min(slab.min.y, slab.max.y)
        val topY = baseY + slab.thickness.coerceAtLeast(0.01f)
        val p000 = Vector3(minX, baseY, minZ)
        val p100 = Vector3(maxX, baseY, minZ)
        val p110 = Vector3(maxX, baseY, maxZ)
        val p010 = Vector3(minX, baseY, maxZ)
        val p001 = Vector3(minX, topY, minZ)
        val p101 = Vector3(maxX, topY, minZ)
        val p111 = Vector3(maxX, topY, maxZ)
        val p011 = Vector3(minX, topY, maxZ)
        addQuad(faceStore, lineStore, p000, p100, p110, p010, Vector3(0f, -1f, 0f), bottomColor)
        addQuad(faceStore, lineStore, p001, p011, p111, p101, Vector3(0f, 1f, 0f), topColor)
        addQuad(faceStore, lineStore, p000, p001, p101, p100, Vector3(0f, 0f, -1f), sideColor)
        addQuad(faceStore, lineStore, p100, p101, p111, p110, Vector3(1f, 0f, 0f), sideColor)
        addQuad(faceStore, lineStore, p110, p111, p011, p010, Vector3(0f, 0f, 1f), sideColor)
        addQuad(faceStore, lineStore, p010, p011, p001, p000, Vector3(-1f, 0f, 0f), sideColor)
    }

    private fun appendStairGeometry(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        stair: ArchitectureStore.Stair,
        treadColor: Color,
        supportColor: Color
    ) {
        val baseY = min(stair.min.y, stair.max.y)
        val steps = stair.stepCount.coerceAtLeast(1)
        val walkingPath = sanitizeWalkingPath(
            if (stair.walkingPath.size >= 2) stair.walkingPath else listOf(stair.walkingStart, stair.walkingEnd)
        )
        val topHeight = stair.height.coerceAtLeast(0.05f)
        val supportThickness = stair.supportThickness.coerceAtLeast(0.01f)
        val stepRise = topHeight / (steps + 1f)
        val treadThickness = (stepRise + supportThickness).coerceAtLeast(0.03f)
        val totalWalkLength = walkingPathLength(walkingPath)
        if (walkingPath.size < 2 || totalWalkLength <= 0.01f) {
            return
        }

        val contourPoints = if (stair.contour.size >= 3) {
            stair.contour
        } else {
            listOf(
                Vector3(stair.min.x, stair.min.y, stair.min.z),
                Vector3(stair.max.x, stair.min.y, stair.min.z),
                Vector3(stair.max.x, stair.min.y, stair.max.z),
                Vector3(stair.min.x, stair.min.y, stair.max.z)
            )
        }
        val contour2 = sanitizeContourPlanar(contourPoints)
        if (contour2.size < 3) {
            return
        }

        val stepLength = totalWalkLength / steps.toFloat()
        val boundaries = mutableListOf<StairPathSample>()
        for (i in 0..steps) {
            val d = (stepLength * i.toFloat()).coerceIn(0f, totalWalkLength)
            val sample = sampleWalkingPath(walkingPath, d) ?: return
            boundaries.add(sample)
        }
        if (boundaries.size < steps + 1) {
            return
        }

        val stepDirs = MutableList(steps) { StairPlanarPoint(1f, 0f) }
        for (i in 0 until steps) {
            val a = StairPlanarPoint(boundaries[i].point.x, boundaries[i].point.z)
            val b = StairPlanarPoint(boundaries[i + 1].point.x, boundaries[i + 1].point.z)
            var d = (b - a).normalized()
            if (d.lengthSquared() <= 1e-8f) {
                d = StairPlanarPoint(boundaries[i].tangent.x, boundaries[i].tangent.z).normalized()
            }
            if (d.lengthSquared() <= 1e-8f) {
                d = StairPlanarPoint(1f, 0f)
            }
            stepDirs[i] = d
        }

        val startPoint = StairPlanarPoint(boundaries.first().point.x, boundaries.first().point.z)
        val startLineDir = contourEdgeDirectionAtPoint(contour2, startPoint, epsilon = 1e-2f)
            ?: stepDirs.first().perpendicular().normalized()
        val crossLines = mutableListOf<StairCrossLine>()
        val supportSections = mutableListOf<StairSupportSection>()
        for (i in 0..steps) {
            val point = StairPlanarPoint(boundaries[i].point.x, boundaries[i].point.z)
            val dir = if (i == 0) {
                startLineDir
            } else {
                stepDirs[i - 1].perpendicular().normalized()
            }
            crossLines.add(StairCrossLine(point = point, dir = dir))
        }

        for (i in 0 until steps) {
            val startLine = crossLines[i]
            val endLine = crossLines[i + 1]
            val startHits = lineIntersectionsWithContour(startLine, contour2)
            val endHits = lineIntersectionsWithContour(endLine, contour2)
            val startPair = pickCrossSectionPair(startHits) ?: continue
            val endPair = pickCrossSectionPair(endHits) ?: continue

            val side = stepDirs[i].perpendicular().normalized()
            var (startRight, startLeft) = orderPairBySide(startPair, startLine.point, side)
            var (endRight, endLeft) = orderPairBySide(endPair, endLine.point, side)

            val sideIntersection = intersectCrossLines(startLine, endLine)
            if (sideIntersection != null) {
                val collapseRight = isBeforeContourHit(startLine.point, startRight, sideIntersection) &&
                    isBeforeContourHit(endLine.point, endRight, sideIntersection)
                if (collapseRight) {
                    startRight = sideIntersection
                    endRight = sideIntersection
                }
                val collapseLeft = isBeforeContourHit(startLine.point, startLeft, sideIntersection) &&
                    isBeforeContourHit(endLine.point, endLeft, sideIntersection)
                if (collapseLeft) {
                    startLeft = sideIntersection
                    endLeft = sideIntersection
                }
            }

            val stepTopY = baseY + (i + 1f) * stepRise
            val stepBottomY = stepTopY - treadThickness
            val planarTop = dedupePlanarLoop(listOf(startRight, endRight, endLeft, startLeft))
            if (planarTop.size < 3) {
                continue
            }
            val topLoop = planarTop.map { point -> Vector3(point.x, stepTopY, point.z) }
            addStairStepSolid(faceStore, lineStore, topLoop, stepBottomY, treadColor)
            addStairRailGrid(
                lineStore = lineStore,
                startRight = startRight,
                endRight = endRight,
                startLeft = startLeft,
                endLeft = endLeft,
                stepTopY = stepTopY,
                stepBottomY = stepBottomY,
                leftEnabled = stair.railLeftEnabled,
                rightEnabled = stair.railRightEnabled
            )

            supportSections.add(
                StairSupportSection(
                    c0 = Vector3(startRight.x, stepBottomY, startRight.z),
                    c1 = Vector3(startLeft.x, stepBottomY, startLeft.z),
                    o0 = Vector3(endRight.x, stepBottomY, endRight.z),
                    o1 = Vector3(endLeft.x, stepBottomY, endLeft.z)
                )
            )
        }
        appendStairSupportRibbon(faceStore, lineStore, supportSections, baseY, supportColor)
    }

    private fun addStairRailGrid(
        lineStore: DraftLineStore,
        startRight: StairPlanarPoint,
        endRight: StairPlanarPoint,
        startLeft: StairPlanarPoint,
        endLeft: StairPlanarPoint,
        stepTopY: Float,
        stepBottomY: Float,
        leftEnabled: Boolean,
        rightEnabled: Boolean
    ) {
        if (!leftEnabled && !rightEnabled) {
            return
        }
        val offsets = floatArrayOf(1f, 2f, 3f, 4f)
        offsets.forEach { offset ->
            val y = stepTopY - offset
            if (y <= stepBottomY + 1e-4f) {
                return@forEach
            }
            if (rightEnabled) {
                val rr0 = Vector3(startRight.x, y, startRight.z)
                val rr1 = Vector3(endRight.x, y, endRight.z)
                if (rr0.dst2(rr1) > 1e-8f) {
                    lineStore.addSegment(rr0, rr1, autoCleanup = false)
                }
            }
            if (leftEnabled) {
                val ll0 = Vector3(startLeft.x, y, startLeft.z)
                val ll1 = Vector3(endLeft.x, y, endLeft.z)
                if (ll0.dst2(ll1) > 1e-8f) {
                    lineStore.addSegment(ll0, ll1, autoCleanup = false)
                }
            }
        }
    }

    private fun addStairStepSolid(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        topLoop: List<Vector3>,
        stepBottomY: Float,
        color: Color
    ) {
        if (topLoop.size < 3) {
            return
        }
        val bottomLoop = topLoop.map { point -> Vector3(point.x, stepBottomY, point.z) }
        val uv = topLoop.map { point -> StairUvPoint(point.x, point.z) }
        addPolygonTriangulated(faceStore, lineStore, topLoop, uv, Vector3(0f, 1f, 0f), color, emitBoundaryLines = true)
        addPolygonTriangulated(faceStore, lineStore, bottomLoop, uv, Vector3(0f, -1f, 0f), color, emitBoundaryLines = true)

        for (i in topLoop.indices) {
            val next = (i + 1) % topLoop.size
            val t0 = topLoop[i]
            val t1 = topLoop[next]
            val b0 = bottomLoop[i]
            val b1 = bottomLoop[next]
            if (t0.dst2(t1) <= 1e-8f) {
                continue
            }
            // Vertical face normals intentionally flipped compared to the previous implementation.
            val outward = Vector3(t1).sub(t0).crs(Vector3(0f, 1f, 0f)).scl(-1f)
            addQuad(faceStore, lineStore, t0, b0, b1, t1, outward, color)
        }
    }

    private data class StairSupportSection(
        val c0: Vector3,
        val c1: Vector3,
        val o0: Vector3,
        val o1: Vector3
    )

    private fun appendStairSupportRibbon(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        sections: List<StairSupportSection>,
        baseY: Float,
        supportColor: Color
    ) {
        if (sections.isEmpty()) {
            return
        }
        for (i in sections.indices) {
            val current = sections[i]
            when {
                // First step support backface lies on the ground plane.
                i == 0 -> addStairSupportQuad(
                    faceStore = faceStore,
                    lineStore = lineStore,
                    p0 = Vector3(current.o0.x, baseY, current.o0.z),
                    p1 = Vector3(current.o1.x, baseY, current.o1.z),
                    p2 = Vector3(current.c1.x, baseY, current.c1.z),
                    p3 = Vector3(current.c0.x, baseY, current.c0.z),
                    color = supportColor
                )
                // Second step uses its own support points.
                i == 1 -> addStairSupportQuad(
                    faceStore = faceStore,
                    lineStore = lineStore,
                    p0 = current.o0,
                    p1 = current.o1,
                    p2 = current.c1,
                    p3 = current.c0,
                    color = supportColor
                )
                else -> {
                    val previous = sections[i - 1]
                    // Last step backface stays horizontal at previous step support height.
                    if (i == sections.lastIndex) {
                        val y = previous.o0.y
                        addStairSupportQuad(
                            faceStore = faceStore,
                            lineStore = lineStore,
                            p0 = Vector3(current.o0.x, y, current.o0.z),
                            p1 = Vector3(current.o1.x, y, current.o1.z),
                            p2 = Vector3(current.c1.x, y, current.c1.z),
                            p3 = Vector3(current.c0.x, y, current.c0.z),
                            color = supportColor
                        )
                    } else {
                        // Seamless ribbon by reusing previous step end support edge.
                        addStairSupportQuad(
                            faceStore = faceStore,
                            lineStore = lineStore,
                            p0 = current.o0,
                            p1 = current.o1,
                            p2 = previous.o1,
                            p3 = previous.o0,
                            color = supportColor
                        )
                        // Lateral extension triangles to current copied-side support points.
                        // Winding is flipped so outward normals match the expected side orientation.
                        addStairSupportTriangle(
                            faceStore = faceStore,
                            lineStore = lineStore,
                            a = current.o0,
                            b = previous.o0,
                            c = current.c0,
                            color = supportColor
                        )
                        addStairSupportTriangle(
                            faceStore = faceStore,
                            lineStore = lineStore,
                            a = current.o1,
                            b = current.c1,
                            c = previous.o1,
                            color = supportColor
                        )
                    }
                }
            }
        }
    }

    private fun addStairSupportQuad(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        p0: Vector3,
        p1: Vector3,
        p2: Vector3,
        p3: Vector3,
        color: Color
    ) {
        val normal = Vector3(p1).sub(p0).crs(Vector3(p2).sub(p0))
        if (normal.len2() <= 1e-8f) {
            return
        }
        addQuad(faceStore, lineStore, p0, p1, p2, p3, normal, color)
    }

    private fun addStairSupportTriangle(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        a: Vector3,
        b: Vector3,
        c: Vector3,
        color: Color
    ) {
        val normal = Vector3(b).sub(a).crs(Vector3(c).sub(a))
        if (normal.len2() <= 1e-8f) {
            return
        }
        faceStore.addTriangle(Vector3(a), Vector3(b), Vector3(c), color)
        lineStore.addSegment(Vector3(a), Vector3(b), autoCleanup = false)
        lineStore.addSegment(Vector3(b), Vector3(c), autoCleanup = false)
        lineStore.addSegment(Vector3(c), Vector3(a), autoCleanup = false)
    }

    private data class StairPathSample(val point: Vector3, val tangent: Vector3)
    private data class StairPlanarPoint(val x: Float, val z: Float) {
        operator fun minus(other: StairPlanarPoint): StairPlanarPoint = StairPlanarPoint(x - other.x, z - other.z)
        operator fun plus(other: StairPlanarPoint): StairPlanarPoint = StairPlanarPoint(x + other.x, z + other.z)
        operator fun times(scale: Float): StairPlanarPoint = StairPlanarPoint(x * scale, z * scale)
        fun dot(other: StairPlanarPoint): Float = x * other.x + z * other.z
        fun cross(other: StairPlanarPoint): Float = x * other.z - z * other.x
        fun lengthSquared(): Float = x * x + z * z
        fun normalized(): StairPlanarPoint {
            val len2 = lengthSquared()
            if (len2 <= 1e-12f) return StairPlanarPoint(0f, 0f)
            val inv = 1f / kotlin.math.sqrt(len2)
            return StairPlanarPoint(x * inv, z * inv)
        }
        fun perpendicular(): StairPlanarPoint = StairPlanarPoint(-z, x)
    }
    private data class StairCrossLine(val point: StairPlanarPoint, val dir: StairPlanarPoint)
    private data class StairLineIntersection(val point: StairPlanarPoint, val t: Float)

    private fun sanitizeContourPlanar(points: List<Vector3>, epsilon: Float = 1e-4f): List<StairPlanarPoint> {
        val out = mutableListOf<StairPlanarPoint>()
        points.forEach { point ->
            val planar = StairPlanarPoint(point.x, point.z)
            val prev = out.lastOrNull()
            if (prev == null || (planar - prev).lengthSquared() > epsilon * epsilon) {
                out.add(planar)
            }
        }
        if (out.size >= 2) {
            val first = out.first()
            val last = out.last()
            if ((first - last).lengthSquared() <= epsilon * epsilon) {
                out.removeAt(out.lastIndex)
            }
        }
        return out
    }

    private fun contourEdgeDirectionAtPoint(
        contour: List<StairPlanarPoint>,
        point: StairPlanarPoint,
        epsilon: Float
    ): StairPlanarPoint? {
        if (contour.size < 2) {
            return null
        }
        var bestDir: StairPlanarPoint? = null
        var bestDist2 = Float.POSITIVE_INFINITY
        for (i in contour.indices) {
            val a = contour[i]
            val b = contour[(i + 1) % contour.size]
            val ab = b - a
            val abLen2 = ab.lengthSquared()
            if (abLen2 <= 1e-10f) {
                continue
            }
            val t = ((point - a).dot(ab) / abLen2).coerceIn(0f, 1f)
            val proj = a + ab * t
            val dist2 = (point - proj).lengthSquared()
            if (dist2 <= epsilon * epsilon && dist2 < bestDist2) {
                bestDist2 = dist2
                bestDir = ab.normalized()
            }
        }
        return bestDir
    }

    private fun lineIntersectionsWithContour(
        line: StairCrossLine,
        contour: List<StairPlanarPoint>,
        epsilon: Float = 1e-5f
    ): List<StairLineIntersection> {
        if (contour.size < 2 || line.dir.lengthSquared() <= 1e-10f) {
            return emptyList()
        }
        val hits = mutableListOf<StairLineIntersection>()
        for (i in contour.indices) {
            val a = contour[i]
            val b = contour[(i + 1) % contour.size]
            val edge = b - a
            val denom = line.dir.cross(edge)
            if (abs(denom) <= epsilon) {
                continue
            }
            val ap = a - line.point
            val t = ap.cross(edge) / denom
            val u = ap.cross(line.dir) / denom
            if (u < -epsilon || u > 1f + epsilon) {
                continue
            }
            val point = line.point + line.dir * t
            if (hits.any { (it.point - point).lengthSquared() <= epsilon * epsilon }) {
                continue
            }
            hits.add(StairLineIntersection(point = point, t = t))
        }
        return hits
    }

    private fun pickCrossSectionPair(hits: List<StairLineIntersection>): Pair<StairPlanarPoint, StairPlanarPoint>? {
        if (hits.size < 2) {
            return null
        }
        val neg = hits.filter { it.t <= 0f }.maxByOrNull { it.t }
        val pos = hits.filter { it.t >= 0f }.minByOrNull { it.t }
        if (neg != null && pos != null) {
            return neg.point to pos.point
        }
        val sorted = hits.sortedBy { it.t }
        return sorted.first().point to sorted.last().point
    }

    private fun orderPairBySide(
        pair: Pair<StairPlanarPoint, StairPlanarPoint>,
        center: StairPlanarPoint,
        side: StairPlanarPoint
    ): Pair<StairPlanarPoint, StairPlanarPoint> {
        val scoreA = (pair.first - center).dot(side)
        val scoreB = (pair.second - center).dot(side)
        return if (scoreA <= scoreB) {
            pair.first to pair.second
        } else {
            pair.second to pair.first
        }
    }

    private fun intersectCrossLines(
        a: StairCrossLine,
        b: StairCrossLine,
        epsilon: Float = 1e-6f
    ): StairPlanarPoint? {
        val denom = a.dir.cross(b.dir)
        if (abs(denom) <= epsilon) {
            return null
        }
        val delta = b.point - a.point
        val t = delta.cross(b.dir) / denom
        return a.point + a.dir * t
    }

    private fun isBeforeContourHit(
        origin: StairPlanarPoint,
        contourHit: StairPlanarPoint,
        candidate: StairPlanarPoint,
        epsilon: Float = 1e-5f
    ): Boolean {
        val edge = contourHit - origin
        val edgeLen2 = edge.lengthSquared()
        if (edgeLen2 <= epsilon * epsilon) {
            return false
        }
        val toCandidate = candidate - origin
        val t = toCandidate.dot(edge) / edgeLen2
        if (t <= epsilon || t >= 1f - epsilon) {
            return false
        }
        val lineError = abs(toCandidate.cross(edge))
        val tolerance = epsilon * sqrt(edgeLen2)
        return lineError <= tolerance
    }

    private fun dedupePlanarLoop(
        points: List<StairPlanarPoint>,
        epsilon: Float = 1e-5f
    ): List<StairPlanarPoint> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val out = mutableListOf<StairPlanarPoint>()
        points.forEach { point ->
            val prev = out.lastOrNull()
            if (prev == null || (point - prev).lengthSquared() > epsilon * epsilon) {
                out.add(point)
            }
        }
        if (out.size >= 2 && (out.first() - out.last()).lengthSquared() <= epsilon * epsilon) {
            out.removeAt(out.lastIndex)
        }
        return out
    }

    private fun sanitizeWalkingPath(points: List<Vector3>, epsilon: Float = 1e-4f): List<Vector3> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val out = mutableListOf<Vector3>()
        points.forEach { point ->
            val planar = Vector3(point.x, 0f, point.z)
            val prev = out.lastOrNull()
            if (prev == null || prev.dst2(planar) > epsilon * epsilon) {
                out.add(planar)
            }
        }
        return out
    }

    private fun walkingPathLength(points: List<Vector3>): Float {
        if (points.size < 2) {
            return 0f
        }
        var total = 0f
        for (i in 0 until points.lastIndex) {
            total += Vector3(points[i + 1]).sub(points[i]).len()
        }
        return total
    }

    private fun sampleWalkingPath(points: List<Vector3>, distance: Float): StairPathSample? {
        if (points.size < 2) {
            return null
        }
        val firstDelta = Vector3(points[1]).sub(points[0]).also { it.y = 0f }
        val firstDir = if (firstDelta.len2() > 1e-8f) firstDelta.nor() else Vector3(1f, 0f, 0f)
        val totalLength = walkingPathLength(points)
        if (totalLength <= 1e-6f) {
            return StairPathSample(Vector3(points.first()), firstDir)
        }
        if (distance <= 0f) {
            val p = Vector3(points.first()).mulAdd(firstDir, distance)
            return StairPathSample(p, firstDir)
        }
        if (distance >= totalLength) {
            val last = points.last()
            val prev = points[points.lastIndex - 1]
            val lastDelta = Vector3(last).sub(prev).also { it.y = 0f }
            val lastDir = if (lastDelta.len2() > 1e-8f) lastDelta.nor() else Vector3(firstDir)
            val p = Vector3(last).mulAdd(lastDir, distance - totalLength)
            return StairPathSample(p, lastDir)
        }
        var traversed = 0f
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val seg = Vector3(b).sub(a).also { it.y = 0f }
            val segLength = seg.len()
            if (segLength <= 1e-8f) {
                continue
            }
            val next = traversed + segLength
            if (distance <= next) {
                val t = (distance - traversed) / segLength
                val point = Vector3(a).mulAdd(seg, t)
                return StairPathSample(point, seg.scl(1f / segLength))
            }
            traversed = next
        }
        val last = points.last()
        val prev = points[points.lastIndex - 1]
        val lastDelta = Vector3(last).sub(prev).also { it.y = 0f }
        val lastDir = if (lastDelta.len2() > 1e-8f) lastDelta.nor() else Vector3(firstDir)
        return StairPathSample(Vector3(last), lastDir)
    }

    private data class StairUvPoint(val u: Float, val v: Float)

    private fun clipStairPolygonByU(
        polygon: List<StairUvPoint>,
        edgeU: Float,
        keepGreater: Boolean,
        epsilon: Float = 1e-5f
    ): List<StairUvPoint> {
        if (polygon.isEmpty()) {
            return emptyList()
        }
        fun inside(point: StairUvPoint): Boolean {
            return if (keepGreater) point.u >= edgeU - epsilon else point.u <= edgeU + epsilon
        }

        val out = mutableListOf<StairUvPoint>()
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val inA = inside(a)
            val inB = inside(b)
            if (inA) {
                out.add(a)
            }
            if (inA != inB) {
                val denom = b.u - a.u
                if (abs(denom) > epsilon) {
                    val t = ((edgeU - a.u) / denom).coerceIn(0f, 1f)
                    out.add(StairUvPoint(edgeU, a.v + (b.v - a.v) * t))
                }
            }
        }
        return out
    }

    private fun dedupeStairPolygon(polygon: List<StairUvPoint>, epsilon: Float = 1e-5f): List<StairUvPoint> {
        if (polygon.isEmpty()) {
            return emptyList()
        }
        val result = mutableListOf<StairUvPoint>()
        polygon.forEach { point ->
            val prev = result.lastOrNull()
            if (prev == null || abs(prev.u - point.u) > epsilon || abs(prev.v - point.v) > epsilon) {
                result.add(point)
            }
        }
        if (result.size >= 2) {
            val first = result.first()
            val last = result.last()
            if (abs(first.u - last.u) <= epsilon && abs(first.v - last.v) <= epsilon) {
                result.removeAt(result.lastIndex)
            }
        }
        return result
    }

    private fun stairPolygonArea(polygon: List<StairUvPoint>): Float {
        if (polygon.size < 3) {
            return 0f
        }
        var area = 0f
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            area += a.u * b.v - b.u * a.v
        }
        return area * 0.5f
    }

    private fun triangulateStairPolygon(polygon: List<StairUvPoint>): List<IntArray> {
        if (polygon.size < 3) {
            return emptyList()
        }
        if (polygon.size == 3) {
            return listOf(intArrayOf(0, 1, 2))
        }
        val isCcw = stairPolygonArea(polygon) >= 0f
        val remaining = polygon.indices.toMutableList()
        val triangles = mutableListOf<IntArray>()
        var guard = 0
        while (remaining.size > 2 && guard < 2048) {
            guard++
            var earFound = false
            for (i in remaining.indices) {
                val prevIndex = remaining[(i - 1 + remaining.size) % remaining.size]
                val currIndex = remaining[i]
                val nextIndex = remaining[(i + 1) % remaining.size]
                val a = polygon[prevIndex]
                val b = polygon[currIndex]
                val c = polygon[nextIndex]
                val cross = (b.u - a.u) * (c.v - a.v) - (b.v - a.v) * (c.u - a.u)
                val convex = if (isCcw) cross > 1e-6f else cross < -1e-6f
                if (!convex) {
                    continue
                }
                var hasPointInside = false
                for (testIndex in remaining) {
                    if (testIndex == prevIndex || testIndex == currIndex || testIndex == nextIndex) {
                        continue
                    }
                    if (pointInStairTriangle(polygon[testIndex], a, b, c)) {
                        hasPointInside = true
                        break
                    }
                }
                if (hasPointInside) {
                    continue
                }
                triangles.add(intArrayOf(prevIndex, currIndex, nextIndex))
                remaining.removeAt(i)
                earFound = true
                break
            }
            if (!earFound) {
                break
            }
        }
        if (triangles.isEmpty() && polygon.size >= 3) {
            for (i in 1 until polygon.lastIndex) {
                triangles.add(intArrayOf(0, i, i + 1))
            }
        }
        return triangles
    }

    private fun pointInStairTriangle(
        p: StairUvPoint,
        a: StairUvPoint,
        b: StairUvPoint,
        c: StairUvPoint
    ): Boolean {
        val c1 = cross2d(a, b, p)
        val c2 = cross2d(b, c, p)
        val c3 = cross2d(c, a, p)
        val hasNeg = c1 < -1e-6f || c2 < -1e-6f || c3 < -1e-6f
        val hasPos = c1 > 1e-6f || c2 > 1e-6f || c3 > 1e-6f
        return !(hasNeg && hasPos)
    }

    private fun cross2d(a: StairUvPoint, b: StairUvPoint, c: StairUvPoint): Float {
        return (b.u - a.u) * (c.v - a.v) - (b.v - a.v) * (c.u - a.u)
    }

    private fun addPolygonTriangulated(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        points3d: List<Vector3>,
        polygonUv: List<StairUvPoint>,
        outward: Vector3,
        color: Color,
        emitBoundaryLines: Boolean
    ) {
        if (points3d.size < 3 || polygonUv.size < 3 || points3d.size != polygonUv.size) {
            return
        }
        val triangles = triangulateStairPolygon(polygonUv)
        triangles.forEach { tri ->
            val p0 = points3d[tri[0]]
            val p1 = points3d[tri[1]]
            val p2 = points3d[tri[2]]
            val normal = Vector3(p1).sub(p0).crs(Vector3(p2).sub(p0))
            if (normal.dot(outward) >= 0f) {
                faceStore.addTriangle(Vector3(p0), Vector3(p1), Vector3(p2), color)
            } else {
                faceStore.addTriangle(Vector3(p0), Vector3(p2), Vector3(p1), color)
            }
        }
        if (emitBoundaryLines) {
            for (i in points3d.indices) {
                val next = (i + 1) % points3d.size
                lineStore.addSegment(Vector3(points3d[i]), Vector3(points3d[next]), autoCleanup = false)
            }
        }
    }

    private fun appendFrameGeometry(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        frame: ArchitectureStore.Frame,
        color: Color
    ) {
        val normal = Vector3(frame.normal)
        if (normal.len2() <= 1e-6f) {
            normal.set(0f, 1f, 0f)
        } else {
            normal.nor()
        }
        var axisU = if (abs(normal.y) < 0.9f) {
            Vector3(0f, 1f, 0f).crs(normal)
        } else {
            Vector3(1f, 0f, 0f).crs(normal)
        }
        if (axisU.len2() <= 1e-6f) {
            axisU = Vector3(0f, 0f, 1f).crs(normal)
        }
        if (axisU.len2() <= 1e-6f) {
            return
        }
        axisU.nor()

        var axisV = Vector3(normal).crs(axisU)
        if (axisV.len2() <= 1e-6f) {
            return
        }
        axisV.nor()

        val origin = Vector3(frame.cornerA)
        val delta = Vector3(frame.cornerB).sub(origin)
        var uLen = delta.dot(axisU)
        var vLen = delta.dot(axisV)

        // If the initial basis collapses one extent, align U on the rectangle diagonal projection.
        if (abs(uLen) <= 0.01f || abs(vLen) <= 0.01f) {
            val diagProjected = Vector3(delta).sub(Vector3(normal).scl(delta.dot(normal)))
            if (diagProjected.len2() > 1e-6f) {
                axisU = diagProjected.nor()
                axisV = Vector3(normal).crs(axisU).nor()
                uLen = delta.dot(axisU)
                vLen = delta.dot(axisV)
            }
        }
        if (abs(uLen) <= 0.01f || abs(vLen) <= 0.01f) {
            return
        }

        val uMin = min(0f, uLen)
        val uMax = max(0f, uLen)
        val vMin = min(0f, vLen)
        val vMax = max(0f, vLen)
        val minExtent = min(uMax - uMin, vMax - vMin)
        val width = frame.frameWidth.coerceAtLeast(0.01f).coerceAtMost(minExtent * 0.45f)
        val iuMin = uMin + width
        val iuMax = uMax - width
        val ivMin = vMin + width
        val ivMax = vMax - width
        if (iuMax - iuMin <= 0.01f || ivMax - ivMin <= 0.01f) {
            return
        }

        fun framePoint(u: Float, v: Float): Vector3 {
            return Vector3(origin).mulAdd(axisU, u).mulAdd(axisV, v)
        }

        val a = framePoint(uMin, vMin)
        val b = framePoint(uMax, vMin)
        val c = framePoint(uMax, vMax)
        val d = framePoint(uMin, vMax)
        val ia = framePoint(iuMin, ivMin)
        val ib = framePoint(iuMax, ivMin)
        val ic = framePoint(iuMax, ivMax)
        val id = framePoint(iuMin, ivMax)

        val depth = frame.depth.coerceAtLeast(0.01f)
        appendPrismFromRect(faceStore, lineStore, a, b, ib, ia, normal, depth, color)
        appendPrismFromRect(faceStore, lineStore, b, c, ic, ib, normal, depth, color)
        appendPrismFromRect(faceStore, lineStore, id, ic, c, d, normal, depth, color)
        appendPrismFromRect(faceStore, lineStore, a, ia, id, d, normal, depth, color)
    }

    private fun appendPrismFromRect(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        p0: Vector3,
        p1: Vector3,
        p2: Vector3,
        p3: Vector3,
        normal: Vector3,
        depth: Float,
        color: Color
    ) {
        val front0 = Vector3(p0).mulAdd(normal, depth)
        val front1 = Vector3(p1).mulAdd(normal, depth)
        val front2 = Vector3(p2).mulAdd(normal, depth)
        val front3 = Vector3(p3).mulAdd(normal, depth)
        val back0 = Vector3(p0)
        val back1 = Vector3(p1)
        val back2 = Vector3(p2)
        val back3 = Vector3(p3)

        addQuad(faceStore, lineStore, front0, front1, front2, front3, normal, color)
        addQuad(faceStore, lineStore, back3, back2, back1, back0, Vector3(normal).scl(-1f), color)
        addQuad(faceStore, lineStore, front0, back0, back1, front1, Vector3(front1).sub(front0).crs(normal), color)
        addQuad(faceStore, lineStore, front1, back1, back2, front2, Vector3(front2).sub(front1).crs(normal), color)
        addQuad(faceStore, lineStore, front2, back2, back3, front3, Vector3(front3).sub(front2).crs(normal), color)
        addQuad(faceStore, lineStore, front3, back3, back0, front0, Vector3(front0).sub(front3).crs(normal), color)
    }

    private fun addBox(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        p000: Vector3,
        p100: Vector3,
        p110: Vector3,
        p010: Vector3,
        p001: Vector3,
        p101: Vector3,
        p111: Vector3,
        p011: Vector3,
        color: Color
    ) {
        addQuad(faceStore, lineStore, p000, p100, p110, p010, Vector3(0f, -1f, 0f), color)
        addQuad(faceStore, lineStore, p001, p011, p111, p101, Vector3(0f, 1f, 0f), color)
        addQuad(faceStore, lineStore, p000, p001, p101, p100, Vector3(0f, 0f, -1f), color)
        addQuad(faceStore, lineStore, p100, p101, p111, p110, Vector3(1f, 0f, 0f), color)
        addQuad(faceStore, lineStore, p110, p111, p011, p010, Vector3(0f, 0f, 1f), color)
        addQuad(faceStore, lineStore, p010, p011, p001, p000, Vector3(-1f, 0f, 0f), color)
    }

    private fun addQuad(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        p0: Vector3,
        p1: Vector3,
        p2: Vector3,
        p3: Vector3,
        outward: Vector3,
        color: Color
    ) {
        val n = Vector3(p1).sub(p0).crs(Vector3(p2).sub(p0))
        val flip = n.dot(outward) < 0f
        if (!flip) {
            faceStore.addTriangle(Vector3(p0), Vector3(p1), Vector3(p2), color)
            faceStore.addTriangle(Vector3(p0), Vector3(p2), Vector3(p3), color)
        } else {
            faceStore.addTriangle(Vector3(p0), Vector3(p3), Vector3(p2), color)
            faceStore.addTriangle(Vector3(p0), Vector3(p2), Vector3(p1), color)
        }
        lineStore.addSegment(Vector3(p0), Vector3(p1), autoCleanup = false)
        lineStore.addSegment(Vector3(p1), Vector3(p2), autoCleanup = false)
        lineStore.addSegment(Vector3(p2), Vector3(p3), autoCleanup = false)
        lineStore.addSegment(Vector3(p3), Vector3(p0), autoCleanup = false)
    }

    private fun uvPoint(u: Float, v: Float, y: Float, axisU: Vector3, axisV: Vector3): Vector3 {
        return Vector3(axisU).scl(u).add(Vector3(axisV).scl(v)).set(
            axisU.x * u + axisV.x * v,
            y,
            axisU.z * u + axisV.z * v
        )
    }

    fun rebuildVoxelGeometry(prototype: ObjectPrototype) {
        if (prototype.kind != PrototypeKind.VOXEL) {
            return
        }
        val voxels = prototype.voxelStore?.all().orEmpty()
        prototype.lineStore.withChangeSuppressed {
            prototype.lineStore.clearAll()
        }
        prototype.faceStore.withChangeSuppressed {
            prototype.faceStore.clearAll()
        }
        if (voxels.isEmpty()) {
            prototype.lineStore.notifyExternalChange()
            prototype.faceStore.notifyExternalChange()
            return
        }
        val occupied = voxels.associateBy { VoxelStore.Key(it.x, it.y, it.z) }
        val corners = arrayOf(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(1f, 1f, 0f),
            Vector3(0f, 1f, 0f),
            Vector3(0f, 0f, 1f),
            Vector3(1f, 0f, 1f),
            Vector3(1f, 1f, 1f),
            Vector3(0f, 1f, 1f)
        )
        // 6 faces, each defined by corner indices and outward neighbor offset.
        val faces = arrayOf(
            FaceDef(intArrayOf(0, 3, 7, 4), -1, 0, 0),
            FaceDef(intArrayOf(1, 5, 6, 2), 1, 0, 0),
            FaceDef(intArrayOf(0, 4, 5, 1), 0, -1, 0),
            FaceDef(intArrayOf(3, 2, 6, 7), 0, 1, 0),
            FaceDef(intArrayOf(0, 1, 2, 3), 0, 0, -1),
            FaceDef(intArrayOf(4, 7, 6, 5), 0, 0, 1)
        )
        prototype.faceStore.withChangeSuppressed {
            prototype.lineStore.withChangeSuppressed {
                voxels.forEach { voxel ->
                    val base = Vector3(voxel.x.toFloat(), voxel.y.toFloat(), voxel.z.toFloat())
                    faces.forEach { def ->
                        val neighborKey = VoxelStore.Key(voxel.x + def.dx, voxel.y + def.dy, voxel.z + def.dz)
                        if (occupied.containsKey(neighborKey)) {
                            return@forEach
                        }
                        val a = Vector3(corners[def.indices[0]]).add(base)
                        val b = Vector3(corners[def.indices[1]]).add(base)
                        val c = Vector3(corners[def.indices[2]]).add(base)
                        val d = Vector3(corners[def.indices[3]]).add(base)
                        // Invert winding so cube face normals point outward.
                        prototype.faceStore.addTriangle(a, c, b, voxel.color)
                        prototype.faceStore.addTriangle(a, d, c, voxel.color)
                        prototype.lineStore.addSegment(a, b, autoCleanup = false)
                        prototype.lineStore.addSegment(b, c, autoCleanup = false)
                        prototype.lineStore.addSegment(c, d, autoCleanup = false)
                        prototype.lineStore.addSegment(d, a, autoCleanup = false)
                    }
                }
            }
        }
        prototype.lineStore.cleanupJts()
        prototype.lineStore.notifyExternalChange()
        prototype.faceStore.notifyExternalChange()
    }

    private data class FaceDef(val indices: IntArray, val dx: Int, val dy: Int, val dz: Int)

    private fun cloneGroup(group: GroupNode): GroupNode {
        val clone = GroupNode(
            id = java.util.UUID.randomUUID().toString(),
            prototype = group.prototype,
            instanceOrigin = Vector3(group.instanceOrigin),
            instanceAxisU = Vector3(group.instanceAxisU),
            instanceAxisV = Vector3(group.instanceAxisV),
            instanceAxisW = Vector3(group.instanceAxisW)
        )
        group.children.forEach { child ->
            val childClone = cloneGroup(child)
            childClone.parent = clone
            clone.children.add(childClone)
        }
        applyChangeListener(clone)
        return clone
    }

    private fun syncPrototypeInstances(source: GroupNode) {
        val instances = prototypeInstances[source.prototype.id] ?: return
        instances.filter { it !== source }.forEach { instance ->
            unregisterChildren(instance)
            instance.children.clear()
            source.children.forEach { child ->
                val childClone = cloneGroup(child)
                childClone.parent = instance
                instance.children.add(childClone)
                registerInstance(childClone)
            }
        }
    }

    private fun unregisterChildren(group: GroupNode) {
        group.children.forEach { child -> unregisterInstance(child) }
    }

    private fun boundsInParent(bounds: BoundingBox, parent: GroupNode): BoundingBox? {
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
        val out = BoundingBox()
        var hasAny = false
        corners.forEach { corner ->
            val local = parent.toLocal(corner)
            if (!hasAny) {
                out.set(local, local)
                hasAny = true
            }
            out.ext(local)
        }
        return if (hasAny) out else null
    }

    private fun toParentSpace(group: GroupNode, local: Vector3): Vector3 {
        val matrix = Matrix4(group.instanceMatrix()).mul(group.definitionMatrix())
        return transformPoint(matrix, local)
    }

    private fun toParentVector(group: GroupNode, local: Vector3): Vector3 {
        val matrix = Matrix4(group.instanceMatrix()).mul(group.definitionMatrix())
        return transformVector(matrix, local)
    }

    private fun reparentGroup(child: GroupNode, newParent: GroupNode) {
        val childWorld = child.worldMatrix()
        val parentWorld = newParent.worldMatrix()
        val invParent = Matrix4(parentWorld).inv()
        val defMatrix = child.definitionMatrix()
        val invDef = Matrix4(defMatrix).inv()
        val localInstance = Matrix4(invParent).mul(childWorld).mul(invDef)
        child.instanceOrigin.set(
            localInstance.`val`[Matrix4.M03],
            localInstance.`val`[Matrix4.M13],
            localInstance.`val`[Matrix4.M23]
        )
        child.instanceAxisU.set(
            localInstance.`val`[Matrix4.M00],
            localInstance.`val`[Matrix4.M10],
            localInstance.`val`[Matrix4.M20]
        )
        child.instanceAxisV.set(
            localInstance.`val`[Matrix4.M01],
            localInstance.`val`[Matrix4.M11],
            localInstance.`val`[Matrix4.M21]
        )
        child.instanceAxisW.set(
            localInstance.`val`[Matrix4.M02],
            localInstance.`val`[Matrix4.M12],
            localInstance.`val`[Matrix4.M22]
        )
        child.parent = newParent
        newParent.children.add(child)
    }

    private fun applyChangeListener(group: GroupNode) {
        val listener = changeListener ?: return
        group.lineStore.setChangeListener(listener)
        group.faceStore.setChangeListener(listener)
        group.dimensionStore.setChangeListener(listener)
        group.textStore.setChangeListener(listener)
    }

    private fun notifyChange() {
        changeListener?.invoke()
    }

    private fun colorsEqual(a: Color, b: Color): Boolean {
        return kotlin.math.abs(a.r - b.r) <= 1e-6f &&
            kotlin.math.abs(a.g - b.g) <= 1e-6f &&
            kotlin.math.abs(a.b - b.b) <= 1e-6f &&
            kotlin.math.abs(a.a - b.a) <= 1e-6f
    }

    companion object {
        private fun transformPoint(matrix: Matrix4, point: Vector3): Vector3 {
            val v = matrix.`val`
            val x = point.x
            val y = point.y
            val z = point.z
            return Vector3(
                x * v[Matrix4.M00] + y * v[Matrix4.M01] + z * v[Matrix4.M02] + v[Matrix4.M03],
                x * v[Matrix4.M10] + y * v[Matrix4.M11] + z * v[Matrix4.M12] + v[Matrix4.M13],
                x * v[Matrix4.M20] + y * v[Matrix4.M21] + z * v[Matrix4.M22] + v[Matrix4.M23]
            )
        }

        private fun transformVector(matrix: Matrix4, vector: Vector3): Vector3 {
            val v = matrix.`val`
            val x = vector.x
            val y = vector.y
            val z = vector.z
            return Vector3(
                x * v[Matrix4.M00] + y * v[Matrix4.M01] + z * v[Matrix4.M02],
                x * v[Matrix4.M10] + y * v[Matrix4.M11] + z * v[Matrix4.M12],
                x * v[Matrix4.M20] + y * v[Matrix4.M21] + z * v[Matrix4.M22]
            )
        }

        private fun matrixFromAxes(origin: Vector3, axes: Axes): Matrix4 {
            val matrix = Matrix4()
            val v = matrix.`val`
            v[Matrix4.M00] = axes.u.x
            v[Matrix4.M10] = axes.u.y
            v[Matrix4.M20] = axes.u.z
            v[Matrix4.M30] = 0f

            v[Matrix4.M01] = axes.v.x
            v[Matrix4.M11] = axes.v.y
            v[Matrix4.M21] = axes.v.z
            v[Matrix4.M31] = 0f

            v[Matrix4.M02] = axes.w.x
            v[Matrix4.M12] = axes.w.y
            v[Matrix4.M22] = axes.w.z
            v[Matrix4.M32] = 0f

            v[Matrix4.M03] = origin.x
            v[Matrix4.M13] = origin.y
            v[Matrix4.M23] = origin.z
            v[Matrix4.M33] = 1f
            return matrix
        }

        private fun axesFromMatrix(matrix: Matrix4): Axes {
            val v = matrix.`val`
            val u = Vector3(v[Matrix4.M00], v[Matrix4.M10], v[Matrix4.M20])
            val vAxis = Vector3(v[Matrix4.M01], v[Matrix4.M11], v[Matrix4.M21])
            val w = Vector3(v[Matrix4.M02], v[Matrix4.M12], v[Matrix4.M22])
            return Axes(u, vAxis, w)
        }
    }

    fun resetScene() {
        root.children.clear()
        root.lineStore.clearAll()
        root.faceStore.clearAll()
        root.dimensionStore.clearAll()
        root.textStore.clearAll()
        clearGroupSelection()
        activeGroup = root
        prototypeInstances.clear()
        prototypes.keys.filter { it != rootPrototype.id }.forEach { prototypes.remove(it) }
        registerPrototype(rootPrototype)
        registerInstance(root)
    }

    private fun registerPrototype(prototype: ObjectPrototype) {
        prototypes[prototype.id] = prototype
    }

    private fun registerInstance(group: GroupNode) {
        prototypeInstances.getOrPut(group.prototype.id) { mutableSetOf() }.add(group)
        group.children.forEach { child -> registerInstance(child) }
    }

    private fun unregisterInstance(group: GroupNode) {
        prototypeInstances[group.prototype.id]?.remove(group)
        group.children.forEach { child -> unregisterInstance(child) }
    }
}

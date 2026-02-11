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
import kotlin.math.tan

class GroupScene(
    val defaultFaceColor: Color
) {
    data class Axes(val u: Vector3, val v: Vector3, val w: Vector3)
    enum class PrototypeKind { MESH, VOXEL, ARCHITECTURE }

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
        inclinationDeg: Float
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (start.dst2(end) <= 1e-6f) {
            return false
        }
        store.addWall(start, end, thickness.coerceAtLeast(0.01f), height.coerceAtLeast(0.05f), inclinationDeg)
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun addArchitectureSlab(
        group: GroupNode,
        minCorner: Vector3,
        maxCorner: Vector3,
        thickness: Float
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (minCorner.dst2(maxCorner) <= 1e-6f) {
            return false
        }
        store.addSlab(minCorner, maxCorner, thickness.coerceAtLeast(0.01f))
        rebuildArchitectureGeometry(group.prototype)
        notifyChange()
        return true
    }

    fun addArchitectureStair(
        group: GroupNode,
        minCorner: Vector3,
        maxCorner: Vector3,
        walkingStart: Vector3,
        walkingEnd: Vector3,
        height: Float,
        stepCount: Int,
        supportThickness: Float
    ): Boolean {
        val store = group.architectureStore ?: return false
        if (minCorner.dst2(maxCorner) <= 1e-6f) {
            return false
        }
        store.addStair(
            minCorner = minCorner,
            maxCorner = maxCorner,
            walkingStart = walkingStart,
            walkingEnd = walkingEnd,
            height = height.coerceAtLeast(0.05f),
            stepCount = stepCount.coerceAtLeast(1),
            supportThickness = supportThickness.coerceAtLeast(0.01f)
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
        kind: ArchitectureStore.FrameKind
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
            kind = kind
        )
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
        val candidate = findNearestWallHoleCandidate(store, cornerA, cornerB) ?: return false
        val added = store.addHole(
            wallId = candidate.wall.id,
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

    fun deleteSelectedArchitectureHoleContours(group: GroupNode): Int {
        val store = group.architectureStore ?: return 0
        val selected = group.lineStore.getSelected().toList()
        if (selected.isEmpty()) {
            return 0
        }
        val removed = store.removeHoles { wall, hole ->
            val contours = holeContourSegments(wall, hole)
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
        val color = prototype.voxelColor

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

        faceStore.withChangeSuppressed {
            lineStore.withChangeSuppressed {
                store.allWalls().forEach { wall ->
                    appendWallGeometry(faceStore, lineStore, wall, color)
                }
                store.allSlabs().forEach { slab ->
                    appendSlabGeometry(faceStore, lineStore, slab, color)
                }
                store.allStairs().forEach { stair ->
                    appendStairGeometry(faceStore, lineStore, stair, color)
                }
                store.allFrames().forEach { frame ->
                    appendFrameGeometry(faceStore, lineStore, frame, color)
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
            val basis = wallBasis(wall) ?: return@forEach
            val pa = projectToWall(basis, cornerA)
            val pb = projectToWall(basis, cornerB)
            val u0 = min(pa.x, pb.x).coerceIn(0f, basis.length)
            val u1 = max(pa.x, pb.x).coerceIn(0f, basis.length)
            val v0 = min(pa.y, pb.y).coerceAtLeast(0f).coerceAtMost(wall.height)
            val v1 = max(pa.y, pb.y).coerceAtLeast(0f).coerceAtMost(wall.height)
            if (u1 - u0 <= 0.05f || v1 - v0 <= 0.05f) {
                return@forEach
            }
            val dist = min(abs(pa.z), abs(pb.z))
            val candidate = HoleCandidate(wall, u0, u1, v0, v1, dist)
            if (best == null || candidate.planeDistance < best!!.planeDistance) {
                best = candidate
            }
        }
        return best
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
        hole: ArchitectureStore.RectHole
    ): List<Pair<Vector3, Vector3>> {
        val basis = wallBasis(wall) ?: return emptyList()
        val eps = 0.0015f
        val p0 = wallPoint(basis, wall, hole.u0, hole.v0, 1f, eps)
        val p1 = wallPoint(basis, wall, hole.u1, hole.v0, 1f, eps)
        val p2 = wallPoint(basis, wall, hole.u1, hole.v1, 1f, eps)
        val p3 = wallPoint(basis, wall, hole.u0, hole.v1, 1f, eps)
        return listOf(
            p0 to p1,
            p1 to p2,
            p2 to p3,
            p3 to p0
        )
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

    private fun wallPoint(
        basis: WallBasis,
        wall: ArchitectureStore.WallSegment,
        u: Float,
        v: Float,
        side: Float,
        extra: Float = 0f
    ): Vector3 {
        val half = wall.thickness * 0.5f + extra
        val safeHeight = wall.height.coerceAtLeast(0.0001f)
        val inclinationOffset = tan(wall.inclinationDeg * PI.toFloat() / 180f) * wall.height
        val lean = inclinationOffset * (v / safeHeight)
        return Vector3(basis.start)
            .mulAdd(basis.dir, u)
            .mulAdd(basis.up, v)
            .mulAdd(basis.normal, side * half + lean)
    }

    private fun appendWallGeometry(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        wall: ArchitectureStore.WallSegment,
        color: Color
    ) {
        val basis = wallBasis(wall) ?: return
        val holes = wall.holes.map { hole ->
            val u0 = min(hole.u0, hole.u1).coerceIn(0.01f, basis.length - 0.01f)
            val u1 = max(hole.u0, hole.u1).coerceIn(0.01f, basis.length - 0.01f)
            val v0 = min(hole.v0, hole.v1).coerceIn(0.01f, wall.height - 0.01f)
            val v1 = max(hole.v0, hole.v1).coerceIn(0.01f, wall.height - 0.01f)
            ArchitectureStore.RectHole(hole.id, u0, u1, v0, v1)
        }.filter { it.u1 - it.u0 > 0.02f && it.v1 - it.v0 > 0.02f }

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

                val ff0 = wallPoint(basis, wall, u0, v0, 1f)
                val ff1 = wallPoint(basis, wall, u1, v0, 1f)
                val ff2 = wallPoint(basis, wall, u1, v1, 1f)
                val ff3 = wallPoint(basis, wall, u0, v1, 1f)
                addQuad(faceStore, lineStore, ff0, ff1, ff2, ff3, basis.normal, color)

                val bb0 = wallPoint(basis, wall, u0, v0, -1f)
                val bb1 = wallPoint(basis, wall, u1, v0, -1f)
                val bb2 = wallPoint(basis, wall, u1, v1, -1f)
                val bb3 = wallPoint(basis, wall, u0, v1, -1f)
                addQuad(faceStore, lineStore, bb3, bb2, bb1, bb0, Vector3(basis.normal).scl(-1f), color)

                if (!isSolid(i - 1, j)) {
                    addQuad(faceStore, lineStore, ff0, ff3, bb3, bb0, Vector3(basis.dir).scl(-1f), color)
                }
                if (!isSolid(i + 1, j)) {
                    addQuad(faceStore, lineStore, ff1, bb1, bb2, ff2, Vector3(basis.dir), color)
                }
                if (!isSolid(i, j - 1)) {
                    addQuad(faceStore, lineStore, ff0, bb0, bb1, ff1, Vector3(basis.up).scl(-1f), color)
                }
                if (!isSolid(i, j + 1)) {
                    addQuad(faceStore, lineStore, ff3, ff2, bb2, bb3, Vector3(basis.up), color)
                }
            }
        }

        holes.forEach { hole ->
            val contours = holeContourSegments(wall, hole)
            contours.forEach { (a, b) ->
                lineStore.addSegment(a, b, autoCleanup = false)
            }
        }
    }

    private fun appendSlabGeometry(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        slab: ArchitectureStore.Slab,
        color: Color
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
        addBox(faceStore, lineStore, p000, p100, p110, p010, p001, p101, p111, p011, color)
    }

    private fun appendStairGeometry(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        stair: ArchitectureStore.Stair,
        color: Color
    ) {
        val baseY = min(stair.min.y, stair.max.y)
        val topHeight = stair.height.coerceAtLeast(0.05f)
        val steps = stair.stepCount.coerceAtLeast(1)
        val support = stair.supportThickness.coerceAtLeast(0.01f)

        val walkDir = Vector3(stair.walkingEnd).sub(stair.walkingStart).also {
            it.y = 0f
        }
        if (walkDir.len2() <= 1e-6f) {
            walkDir.set(1f, 0f, 0f)
        } else {
            walkDir.nor()
        }
        val sideDir = Vector3(-walkDir.z, 0f, walkDir.x).nor()

        val corners = listOf(
            Vector3(stair.min.x, 0f, stair.min.z),
            Vector3(stair.max.x, 0f, stair.min.z),
            Vector3(stair.max.x, 0f, stair.max.z),
            Vector3(stair.min.x, 0f, stair.max.z)
        )
        var uMin = Float.POSITIVE_INFINITY
        var uMax = Float.NEGATIVE_INFINITY
        var vMin = Float.POSITIVE_INFINITY
        var vMax = Float.NEGATIVE_INFINITY
        corners.forEach { corner ->
            val u = corner.dot(walkDir)
            val v = corner.dot(sideDir)
            uMin = min(uMin, u)
            uMax = max(uMax, u)
            vMin = min(vMin, v)
            vMax = max(vMax, v)
        }
        if (uMax - uMin <= 0.01f || vMax - vMin <= 0.01f) {
            return
        }

        val run = (uMax - uMin) / steps.toFloat()
        val rise = topHeight / steps.toFloat()
        for (index in 0 until steps) {
            val stepU0 = uMin + run * index
            val stepU1 = uMin + run * (index + 1)
            val stepTop = baseY + rise * (index + 1)
            val p000 = uvPoint(stepU0, vMin, baseY, walkDir, sideDir)
            val p100 = uvPoint(stepU1, vMin, baseY, walkDir, sideDir)
            val p110 = uvPoint(stepU1, vMax, baseY, walkDir, sideDir)
            val p010 = uvPoint(stepU0, vMax, baseY, walkDir, sideDir)
            val p001 = uvPoint(stepU0, vMin, stepTop, walkDir, sideDir)
            val p101 = uvPoint(stepU1, vMin, stepTop, walkDir, sideDir)
            val p111 = uvPoint(stepU1, vMax, stepTop, walkDir, sideDir)
            val p011 = uvPoint(stepU0, vMax, stepTop, walkDir, sideDir)
            addBox(faceStore, lineStore, p000, p100, p110, p010, p001, p101, p111, p011, color)
        }

        val supportBottom = baseY - support
        val s000 = uvPoint(uMin, vMin, supportBottom, walkDir, sideDir)
        val s100 = uvPoint(uMax, vMin, supportBottom, walkDir, sideDir)
        val s110 = uvPoint(uMax, vMax, supportBottom, walkDir, sideDir)
        val s010 = uvPoint(uMin, vMax, supportBottom, walkDir, sideDir)
        val s001 = uvPoint(uMin, vMin, baseY, walkDir, sideDir)
        val s101 = uvPoint(uMax, vMin, baseY, walkDir, sideDir)
        val s111 = uvPoint(uMax, vMax, baseY, walkDir, sideDir)
        val s011 = uvPoint(uMin, vMax, baseY, walkDir, sideDir)
        addBox(faceStore, lineStore, s000, s100, s110, s010, s001, s101, s111, s011, color)
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
        val diag = Vector3(frame.cornerB).sub(frame.cornerA)
        var axisU = Vector3(diag).sub(Vector3(normal).scl(diag.dot(normal)))
        if (axisU.len2() <= 1e-6f) {
            axisU = if (abs(normal.y) < 0.9f) {
                Vector3(0f, 1f, 0f).crs(normal)
            } else {
                Vector3(1f, 0f, 0f).crs(normal)
            }
        }
        axisU.nor()
        var axisV = Vector3(normal).crs(axisU)
        if (axisV.len2() <= 1e-6f) {
            return
        }
        axisV.nor()

        val delta = Vector3(frame.cornerB).sub(frame.cornerA)
        val uLen = delta.dot(axisU)
        val vLen = delta.dot(axisV)
        val uVec = Vector3(axisU).scl(uLen)
        val vVec = Vector3(axisV).scl(vLen)
        if (uVec.len2() <= 1e-6f || vVec.len2() <= 1e-6f) {
            return
        }

        val minExtent = min(abs(uLen), abs(vLen))
        val width = frame.frameWidth.coerceAtLeast(0.01f).coerceAtMost(minExtent * 0.45f)
        val uInside = Vector3(axisU).scl(if (uLen >= 0f) width else -width)
        val vInside = Vector3(axisV).scl(if (vLen >= 0f) width else -width)

        val a = Vector3(frame.cornerA)
        val b = Vector3(a).add(uVec)
        val c = Vector3(b).add(vVec)
        val d = Vector3(a).add(vVec)

        val depth = frame.depth.coerceAtLeast(0.01f)
        appendPrismFromRect(faceStore, lineStore, a, b, Vector3(b).add(vInside), Vector3(a).add(vInside), normal, depth, color)
        appendPrismFromRect(faceStore, lineStore, Vector3(d).sub(vInside), Vector3(c).sub(vInside), c, d, normal, depth, color)
        appendPrismFromRect(faceStore, lineStore, a, Vector3(a).add(uInside), Vector3(d).add(uInside), d, normal, depth, color)
        appendPrismFromRect(faceStore, lineStore, Vector3(b).sub(uInside), b, c, Vector3(c).sub(uInside), normal, depth, color)
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
        val half = depth * 0.5f
        val front0 = Vector3(p0).mulAdd(normal, half)
        val front1 = Vector3(p1).mulAdd(normal, half)
        val front2 = Vector3(p2).mulAdd(normal, half)
        val front3 = Vector3(p3).mulAdd(normal, half)
        val back0 = Vector3(p0).mulAdd(normal, -half)
        val back1 = Vector3(p1).mulAdd(normal, -half)
        val back2 = Vector3(p2).mulAdd(normal, -half)
        val back3 = Vector3(p3).mulAdd(normal, -half)

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

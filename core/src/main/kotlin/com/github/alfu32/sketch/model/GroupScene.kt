package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.math.tan

class GroupScene(
    val defaultFaceColor: Color
) {
    data class Axes(val u: Vector3, val v: Vector3, val w: Vector3)
    data class MeshTriangle(val a: Vector3, val b: Vector3, val c: Vector3, val color: Color = Color(1f, 1f, 1f, 1f))
    enum class PrototypeKind { MESH, VOXEL, ARCHITECTURE }
    enum class HoleHandleKind {
        CORNER_0, CORNER_1, CORNER_2, CORNER_3,
        EDGE_0, EDGE_1, EDGE_2, EDGE_3,
        CENTER
    }
    enum class ArchitectureSelectionMode { REPLACE, ADD, REMOVE }
    enum class HvacSelectionMode { REPLACE, ADD, REMOVE }
    enum class HotspotSelectionMode { REPLACE, ADD, REMOVE }
    data class HoleHandleMarker(
        val wallId: String,
        val holeId: String,
        val kind: HoleHandleKind,
        val world: Vector3
    )
    data class SlabHoleHandleMarker(
        val slabId: String,
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
    data class ArchitectureEndpointHandleMarker(
        val kind: ArchitectureStore.ElementKind,
        val id: String,
        val draggingStart: Boolean,
        val center: Vector3,
        val halfSize: Float
    )
    data class ArchitectureElementHotspotMarker(
        val kind: ArchitectureStore.ElementKind,
        val id: String,
        val world: Vector3
    )
    data class ArchitectureHoleSelection(
        val wallId: String,
        val holeId: String
    )
    data class ArchitectureSlabHoleSelection(
        val slabId: String,
        val holeId: String
    )
    data class HvacControlHandleMarker(
        val kind: HvacStore.ElementKind,
        val id: String,
        val pointIndex: Int?,
        val ventilationControlKind: HvacStore.VentilationControlKind?,
        val center: Vector3,
        val halfSize: Float
    )
    data class HvacElementHotspotMarker(
        val kind: HvacStore.ElementKind,
        val id: String,
        val world: Vector3
    )
    private data class VertexHandle(val primitiveKind: Int, val primitiveId: String, val slot: Int)
    data class HotspotMarker(
        val id: String,
        val world: Vector3,
        val selected: Boolean,
        val referenceWorld: Vector3?,
        val shape: HotspotStore.ShapeKind,
        val color: Color
    )
    data class HotspotDragPreview(
        val currentWorld: Vector3,
        val targetWorld: Vector3,
        val lines: List<Pair<Vector3, Vector3>>
    )
    private data class ResolvedVentilationDuct(
        val id: String,
        val start: Vector3,
        val end: Vector3,
        val path: List<Vector3>,
        val binormalRef: Vector3,
        val autoJoinEnabled: Boolean,
        val width: Float,
        val height: Float,
        val startJoined: Boolean,
        val endJoined: Boolean,
        val startJoinPlaneNormal: Vector3? = null,
        val endJoinPlaneNormal: Vector3? = null
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
        val hvacStore: HvacStore? = null,
        val hotspotStore: HotspotStore = HotspotStore(),
        val lineStore: DraftLineStore,
        val faceStore: DraftFaceStore,
        val dimensionStore: DraftDimensionStore,
        val textStore: DraftTextStore,
        val prototypeVertexIds: MutableMap<HotspotStore.VertexKey, String> = linkedMapOf()
    )

    class GroupNode(
        val id: String,
        var prototype: ObjectPrototype,
        var instanceOrigin: Vector3,
        var instanceAxisU: Vector3,
        var instanceAxisV: Vector3,
        var instanceAxisW: Vector3
    ) {
        var editPrototypeMode: Boolean = false
        var lineStoreOverride: DraftLineStore? = null
        var faceStoreOverride: DraftFaceStore? = null
        var dimensionStoreOverride: DraftDimensionStore? = null
        var textStoreOverride: DraftTextStore? = null
        val hotspotPositionOverrides: MutableMap<String, Vector3> = linkedMapOf()
        val runtimeHotspotPositions: MutableMap<String, Vector3> = linkedMapOf()
        val hotspotAttachedSegmentOverrides: MutableMap<String, MutableSet<HotspotStore.SegmentRef>> = linkedMapOf()
        val hotspotAttachedTriangleOverrides: MutableMap<String, MutableSet<HotspotStore.TriangleRef>> = linkedMapOf()
        val hotspotSelectionIds: MutableSet<String> = linkedSetOf()

        val children: MutableList<GroupNode> = mutableListOf()
        var parent: GroupNode? = null
        val lineStore: DraftLineStore
            get() = if (editPrototypeMode) {
                prototype.lineStore
            } else {
                lineStoreOverride ?: prototype.lineStore
            }
        val faceStore: DraftFaceStore
            get() = if (editPrototypeMode) {
                prototype.faceStore
            } else {
                faceStoreOverride ?: prototype.faceStore
            }
        val dimensionStore: DraftDimensionStore
            get() = if (editPrototypeMode) {
                prototype.dimensionStore
            } else {
                dimensionStoreOverride ?: prototype.dimensionStore
            }
        val textStore: DraftTextStore
            get() = if (editPrototypeMode) {
                prototype.textStore
            } else {
                textStoreOverride ?: prototype.textStore
            }
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
        val hvacStore: HvacStore?
            get() = prototype.hvacStore
        val hotspotStore: HotspotStore
            get() = prototype.hotspotStore
        val voxelColor: Color
            get() = prototype.voxelColor

        fun hasGeometryOverrides(): Boolean {
            return lineStoreOverride != null ||
                faceStoreOverride != null ||
                dimensionStoreOverride != null ||
                textStoreOverride != null
        }

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

        fun geometryWorldBounds(): BoundingBox? {
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
    private val modelArchitectureStore = ArchitectureStore()
    private val modelHvacStore = HvacStore()
    private val generatedArchitectureLines = mutableSetOf<DraftLineStore.Segment>()
    private val generatedArchitectureFaces = mutableSetOf<DraftFaceStore.Triangle>()
    private val generatedArchitectureLineOwners = mutableMapOf<DraftLineStore.Segment, ArchitectureStore.ElementSelection>()
    private val generatedArchitectureFaceOwners = mutableMapOf<DraftFaceStore.Triangle, ArchitectureStore.ElementSelection>()
    private val generatedHvacLines = mutableSetOf<DraftLineStore.Segment>()
    private val generatedHvacFaces = mutableSetOf<DraftFaceStore.Triangle>()
    private val generatedHvacLineOwners = mutableMapOf<DraftLineStore.Segment, HvacStore.ElementSelection>()
    private val generatedHvacFaceOwners = mutableMapOf<DraftFaceStore.Triangle, HvacStore.ElementSelection>()
    private val resolvedHvacVentilation = mutableMapOf<String, ResolvedVentilationDuct>()
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
        architectureStore = modelArchitectureStore,
        hvacStore = modelHvacStore,
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
    private var selectedArchitectureHole: ArchitectureHoleSelection? = null
    private var selectedArchitectureSlabHole: ArchitectureSlabHoleSelection? = null
    private var changeListener: (() -> Unit)? = null
    private var groupSpatialIndex = SpatialHash3D<String>(GROUP_SPATIAL_HASH_CELL_SIZE) { it }
    private var groupSpatialIndexDirty = true
    private val groupSpatialBoundsById = linkedMapOf<String, BoundingBox>()
    private val groupSpatialBoundsMin = Vector3()
    private val groupSpatialBoundsMax = Vector3()
    private var hasGroupSpatialBounds = false

    init {
        configureRootLineStoreAutoProcessingFilters()
        registerPrototype(rootPrototype)
        registerInstance(root)
    }

    private fun configureRootLineStoreAutoProcessingFilters() {
        rootPrototype.lineStore.setAutoProcessingIgnorePredicate { segment ->
            generatedArchitectureLines.contains(segment) || generatedHvacLines.contains(segment)
        }
    }

    fun activeGroup(): GroupNode = activeGroup

    fun isEditing(): Boolean = activeGroup != root

    fun resetActiveGroup() {
        if (activeGroup != root) {
            activeGroup.editPrototypeMode = false
        }
        activeGroup = root
        activeGroup.editPrototypeMode = false
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

    fun processAsyncMaintenance(nowMs: Long = System.currentTimeMillis()) {
        val lineStores = linkedSetOf<DraftLineStore>()
        val faceStores = linkedSetOf<DraftFaceStore>()
        fun collectStores(group: GroupNode) {
            lineStores.add(group.prototype.lineStore)
            faceStores.add(group.prototype.faceStore)
            group.lineStoreOverride?.let { lineStores.add(it) }
            group.faceStoreOverride?.let { faceStores.add(it) }
        }
        collectStores(root)
        if (activeGroup !== root) {
            collectStores(activeGroup)
        }
        lineStores.forEach { it.processAsyncMaintenance(nowMs) }
        faceStores.forEach { it.processAsyncMaintenance(nowMs) }
    }

    fun enterGroup(group: GroupNode): Boolean {
        if (group == activeGroup) {
            return false
        }
        if (activeGroup != root) {
            activeGroup.editPrototypeMode = false
        }
        activeGroup = group
        activeGroup.editPrototypeMode = activeGroup != root
        clearGroupSelection()
        return true
    }

    fun exitGroup(): Boolean {
        val current = activeGroup
        val parent = current.parent ?: return false
        current.editPrototypeMode = false
        activeGroup = parent
        activeGroup.editPrototypeMode = activeGroup != root
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
        root.hotspotSelectionIds.clear()
        modelArchitectureStore.clearSelectedElement()
        selectedArchitectureHole = null
        selectedArchitectureSlabHole = null
        modelHvacStore.clearSelectedElement()
        root.voxelStore?.clearSelection()
        walkGroups(root) { group ->
            group.lineStore.clearSelection()
            group.faceStore.clearSelection()
            group.dimensionStore.clearSelection()
            group.textStore.clearSelection()
            group.hotspotSelectionIds.clear()
            group.voxelStore?.clearSelection()
            group.architectureStore?.clearSelectedElement()
            group.hvacStore?.clearSelectedElement()
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
            hvacStore = null,
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
            hvacStore = null,
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

    fun createMeshPrototype(
        name: String,
        triangles: List<MeshTriangle>,
        includeEdges: Boolean = true
    ): ObjectPrototype? {
        if (triangles.isEmpty()) {
            return null
        }
        val bounds = BoundingBox()
        var hasBounds = false
        triangles.forEach { tri ->
            if (!hasBounds) {
                bounds.set(tri.a, tri.a)
                hasBounds = true
            }
            bounds.ext(tri.a)
            bounds.ext(tri.b)
            bounds.ext(tri.c)
        }
        if (!hasBounds) {
            return null
        }
        val localOrigin = Vector3(bounds.min)
        val prototype = ObjectPrototype(
            id = java.util.UUID.randomUUID().toString(),
            name = name.ifBlank { "Imported Object" },
            definitionOrigin = Vector3(),
            definitionAxisU = Vector3(1f, 0f, 0f),
            definitionAxisV = Vector3(0f, 1f, 0f),
            definitionAxisW = Vector3(0f, 0f, 1f),
            gluedToSurface = false,
            kind = PrototypeKind.MESH,
            voxelColor = Color(defaultFaceColor),
            voxelStore = null,
            architectureStore = null,
            hvacStore = null,
            lineStore = DraftLineStore(),
            faceStore = DraftFaceStore(defaultFaceColor),
            dimensionStore = DraftDimensionStore(),
            textStore = DraftTextStore()
        )

        prototype.faceStore.withChangeSuppressed {
            prototype.lineStore.withChangeSuppressed {
                triangles.forEach { tri ->
                    val a = Vector3(tri.a).sub(localOrigin)
                    val b = Vector3(tri.b).sub(localOrigin)
                    val c = Vector3(tri.c).sub(localOrigin)
                    prototype.faceStore.addTriangle(a, b, c, tri.color)
                    if (includeEdges) {
                        prototype.lineStore.addSegment(a, b, autoCleanup = false)
                        prototype.lineStore.addSegment(b, c, autoCleanup = false)
                        prototype.lineStore.addSegment(c, a, autoCleanup = false)
                    }
                }
            }
        }
        if (includeEdges) {
            prototype.lineStore.cleanupJts()
        }
        registerPrototype(prototype)
        refreshPrototypeVertexIds(prototype)
        prototype.lineStore.notifyExternalChange()
        prototype.faceStore.notifyExternalChange()
        notifyChange()
        return prototype
    }

    fun isVoxelGroup(group: GroupNode): Boolean {
        return group.kind == PrototypeKind.VOXEL && group.voxelStore != null
    }

    fun isArchitectureGroup(group: GroupNode): Boolean {
        return group.kind == PrototypeKind.ARCHITECTURE && group.architectureStore != null
    }

    fun isWallOnlyArchitectureGroup(group: GroupNode): Boolean {
        val store = architectureStoreFor(group)
        return store.allWalls().isNotEmpty() &&
            store.allSlabs().isEmpty() &&
            store.allStairs().isEmpty() &&
            store.allFrames().isEmpty()
    }

    fun hasArchitectureElements(): Boolean {
        val store = modelArchitectureStore
        return store.allWalls().isNotEmpty() ||
            store.allSlabs().isNotEmpty() ||
            store.allStairs().isNotEmpty() ||
            store.allFrames().isNotEmpty()
    }

    fun hasHvacElements(): Boolean {
        return modelHvacStore.allPlumbingRuns().isNotEmpty() ||
            modelHvacStore.allVentilationDucts().isNotEmpty()
    }

    fun isGeneratedArchitectureSegment(segment: DraftLineStore.Segment): Boolean {
        return generatedArchitectureLines.contains(segment)
    }

    fun isGeneratedArchitectureTriangle(triangle: DraftFaceStore.Triangle): Boolean {
        return generatedArchitectureFaces.contains(triangle)
    }

    fun generatedArchitectureOwner(segment: DraftLineStore.Segment): ArchitectureStore.ElementSelection? {
        return generatedArchitectureLineOwners[segment]
    }

    fun generatedArchitectureOwner(triangle: DraftFaceStore.Triangle): ArchitectureStore.ElementSelection? {
        return generatedArchitectureFaceOwners[triangle]
    }

    fun isGeneratedHvacSegment(segment: DraftLineStore.Segment): Boolean {
        return generatedHvacLines.contains(segment)
    }

    fun isGeneratedHvacTriangle(triangle: DraftFaceStore.Triangle): Boolean {
        return generatedHvacFaces.contains(triangle)
    }

    fun generatedHvacOwner(segment: DraftLineStore.Segment): HvacStore.ElementSelection? {
        return generatedHvacLineOwners[segment]
    }

    fun generatedHvacOwner(triangle: DraftFaceStore.Triangle): HvacStore.ElementSelection? {
        return generatedHvacFaceOwners[triangle]
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

    private fun architectureStoreFor(group: GroupNode? = null): ArchitectureStore {
        return modelArchitectureStore
    }

    private fun hvacStoreFor(group: GroupNode? = null): HvacStore {
        return modelHvacStore
    }

    private fun hotspotStoreFor(group: GroupNode? = null): HotspotStore {
        return (group ?: activeGroup).hotspotStore
    }

    private fun cloneLineStore(source: DraftLineStore): DraftLineStore {
        val clone = DraftLineStore()
        source.getSegments().forEach { segment ->
            clone.addSegment(segment.start, segment.end, autoCleanup = false, id = segment.id)
        }
        source.getSelected().forEach { segment ->
            val matched = clone.getSegments().firstOrNull {
                it.start.epsilonEquals(segment.start, 1e-6f) && it.end.epsilonEquals(segment.end, 1e-6f)
            } ?: return@forEach
            clone.addSelection(matched)
        }
        return clone
    }

    private fun cloneFaceStore(source: DraftFaceStore): DraftFaceStore {
        val clone = DraftFaceStore(defaultFaceColor)
        source.getTriangles().forEach { triangle ->
            clone.addTriangle(
                triangle.a,
                triangle.b,
                triangle.c,
                source.colorFor(triangle),
                id = triangle.id
            )
        }
        source.getSelected().forEach { triangle ->
            val matched = clone.getTriangles().firstOrNull {
                it.a.epsilonEquals(triangle.a, 1e-6f) &&
                    it.b.epsilonEquals(triangle.b, 1e-6f) &&
                    it.c.epsilonEquals(triangle.c, 1e-6f)
            } ?: return@forEach
            clone.addSelection(matched)
        }
        return clone
    }

    private fun cloneDimensionStore(source: DraftDimensionStore): DraftDimensionStore {
        val clone = DraftDimensionStore()
        source.getDimensions().forEach { dimension ->
            clone.addDimension(dimension.start, dimension.end, dimension.offset)
        }
        source.getSelected().forEach { dimension ->
            val matched = clone.getDimensions().firstOrNull {
                it.start.epsilonEquals(dimension.start, 1e-6f) &&
                    it.end.epsilonEquals(dimension.end, 1e-6f) &&
                    it.offset.epsilonEquals(dimension.offset, 1e-6f)
            } ?: return@forEach
            clone.addSelection(matched)
        }
        return clone
    }

    private fun cloneTextStore(source: DraftTextStore): DraftTextStore {
        val clone = DraftTextStore()
        source.getTexts().forEach { text ->
            clone.addText(
                position = text.position,
                text = text.text,
                size = text.size,
                normal = text.normal,
                axisU = text.axisU,
                screenText = text.screenText,
                kind = text.kind,
                tracking = text.tracking,
                lineSpacing = text.lineSpacing,
                glyphSourcePath = text.glyphSourcePath
            )
        }
        source.getSelected().forEach { text ->
            val matched = clone.getTexts().firstOrNull {
                it.text == text.text &&
                    abs(it.size - text.size) <= 1e-6f &&
                    it.screenText == text.screenText &&
                    it.kind == text.kind &&
                    abs(it.tracking - text.tracking) <= 1e-6f &&
                    abs(it.lineSpacing - text.lineSpacing) <= 1e-6f &&
                    it.glyphSourcePath == text.glyphSourcePath &&
                    it.position.epsilonEquals(text.position, 1e-6f) &&
                    it.normal.epsilonEquals(text.normal, 1e-6f) &&
                    it.axisU.epsilonEquals(text.axisU, 1e-6f)
            } ?: return@forEach
            clone.addSelection(matched)
        }
        return clone
    }

    private fun ensureInstanceGeometryOverrides(group: GroupNode) {
        if (group === root || group === activeGroup) {
            return
        }
        if (group.hasGeometryOverrides()) {
            return
        }
        group.lineStoreOverride = cloneLineStore(group.prototype.lineStore)
        group.faceStoreOverride = cloneFaceStore(group.prototype.faceStore)
        group.dimensionStoreOverride = cloneDimensionStore(group.prototype.dimensionStore)
        group.textStoreOverride = cloneTextStore(group.prototype.textStore)
        applyChangeListener(group)
    }

    private fun hotspotVertexKey(point: Vector3): HotspotStore.VertexKey {
        val eps = 1e-3f
        return HotspotStore.VertexKey(
            (point.x / eps).roundToInt(),
            (point.y / eps).roundToInt(),
            (point.z / eps).roundToInt()
        )
    }

    private fun collectPrototypeVertexKeys(prototype: ObjectPrototype): Set<HotspotStore.VertexKey> {
        val keys = linkedSetOf<HotspotStore.VertexKey>()
        prototype.lineStore.getSegments().forEach { segment ->
            keys.add(hotspotVertexKey(segment.start))
            keys.add(hotspotVertexKey(segment.end))
        }
        prototype.faceStore.getTriangles().forEach { triangle ->
            keys.add(hotspotVertexKey(triangle.a))
            keys.add(hotspotVertexKey(triangle.b))
            keys.add(hotspotVertexKey(triangle.c))
        }
        return keys
    }

    private fun refreshPrototypeVertexIds(prototype: ObjectPrototype) {
        val keys = collectPrototypeVertexKeys(prototype)
        val next = linkedMapOf<HotspotStore.VertexKey, String>()
        keys.forEach { key ->
            val existing = prototype.prototypeVertexIds[key]
            next[key] = existing ?: java.util.UUID.randomUUID().toString()
        }
        prototype.prototypeVertexIds.clear()
        prototype.prototypeVertexIds.putAll(next)
    }

    private fun vertexIdForPrototypePoint(prototype: ObjectPrototype, point: Vector3): String? {
        return prototype.prototypeVertexIds[hotspotVertexKey(point)]
    }

    private fun attachedHotspotVertexKeys(
        attachedSegments: Set<HotspotStore.SegmentRef>,
        attachedTriangles: Set<HotspotStore.TriangleRef>
    ): Set<HotspotStore.VertexKey> {
        val keys = linkedSetOf<HotspotStore.VertexKey>()
        attachedSegments.forEach { ref ->
            keys.add(ref.a)
            keys.add(ref.b)
        }
        attachedTriangles.forEach { ref ->
            keys.add(ref.a)
            keys.add(ref.b)
            keys.add(ref.c)
        }
        return keys
    }

    private fun attachedSegmentsFor(
        group: GroupNode,
        hotspot: HotspotStore.Hotspot
    ): Set<HotspotStore.SegmentRef> {
        return group.hotspotAttachedSegmentOverrides[hotspot.id] ?: hotspot.attachedSegments
    }

    private fun attachedTrianglesFor(
        group: GroupNode,
        hotspot: HotspotStore.Hotspot
    ): Set<HotspotStore.TriangleRef> {
        return group.hotspotAttachedTriangleOverrides[hotspot.id] ?: hotspot.attachedTriangles
    }

    private fun matchesSegmentRef(
        store: HotspotStore,
        segment: DraftLineStore.Segment,
        ref: HotspotStore.SegmentRef
    ): Boolean {
        if (ref.id.isNotBlank()) {
            return segment.id == ref.id
        }
        val segmentRef = store.segmentRef(segment)
        return segmentRef.a == ref.a && segmentRef.b == ref.b
    }

    private fun matchesTriangleRef(
        store: HotspotStore,
        triangle: DraftFaceStore.Triangle,
        ref: HotspotStore.TriangleRef
    ): Boolean {
        if (ref.id.isNotBlank()) {
            return triangle.id == ref.id
        }
        val triangleRef = store.triangleRef(triangle)
        return triangleRef.a == ref.a && triangleRef.b == ref.b && triangleRef.c == ref.c
    }

    private fun containsSegmentRef(
        store: HotspotStore,
        refs: Set<HotspotStore.SegmentRef>,
        segment: DraftLineStore.Segment
    ): Boolean = refs.any { ref -> matchesSegmentRef(store, segment, ref) }

    private fun containsTriangleRef(
        store: HotspotStore,
        refs: Set<HotspotStore.TriangleRef>,
        triangle: DraftFaceStore.Triangle
    ): Boolean = refs.any { ref -> matchesTriangleRef(store, triangle, ref) }

    private fun captureVertexHandlesAtPoint(
        lineStore: DraftLineStore,
        faceStore: DraftFaceStore,
        point: Vector3
    ): Set<VertexHandle> {
        val out = linkedSetOf<VertexHandle>()
        lineStore.getSegments().forEach { segment ->
            if (segment.start.epsilonEquals(point, 1e-6f)) {
                out.add(VertexHandle(0, segment.id, 0))
            }
            if (segment.end.epsilonEquals(point, 1e-6f)) {
                out.add(VertexHandle(0, segment.id, 1))
            }
        }
        faceStore.getTriangles().forEach { triangle ->
            if (triangle.a.epsilonEquals(point, 1e-6f)) {
                out.add(VertexHandle(1, triangle.id, 0))
            }
            if (triangle.b.epsilonEquals(point, 1e-6f)) {
                out.add(VertexHandle(1, triangle.id, 1))
            }
            if (triangle.c.epsilonEquals(point, 1e-6f)) {
                out.add(VertexHandle(1, triangle.id, 2))
            }
        }
        return out
    }

    private fun buildVertexIdByHandle(
        lineStore: DraftLineStore,
        faceStore: DraftFaceStore,
        prototype: ObjectPrototype
    ): Map<VertexHandle, String> {
        val out = linkedMapOf<VertexHandle, String>()
        lineStore.getSegments().forEach { segment ->
            vertexIdForPrototypePoint(prototype, segment.start)?.let { id ->
                out[VertexHandle(0, segment.id, 0)] = id
            }
            vertexIdForPrototypePoint(prototype, segment.end)?.let { id ->
                out[VertexHandle(0, segment.id, 1)] = id
            }
        }
        faceStore.getTriangles().forEach { triangle ->
            vertexIdForPrototypePoint(prototype, triangle.a)?.let { id ->
                out[VertexHandle(1, triangle.id, 0)] = id
            }
            vertexIdForPrototypePoint(prototype, triangle.b)?.let { id ->
                out[VertexHandle(1, triangle.id, 1)] = id
            }
            vertexIdForPrototypePoint(prototype, triangle.c)?.let { id ->
                out[VertexHandle(1, triangle.id, 2)] = id
            }
        }
        return out
    }

    private fun transformRuntimeGeometry(
        lineStore: DraftLineStore,
        faceStore: DraftFaceStore,
        segments: Set<DraftLineStore.Segment>,
        triangles: Set<DraftFaceStore.Triangle>,
        transform: (Vector3) -> Vector3
    ) {
        lineStore.withChangeSuppressed {
            lineStore.getSegments().forEach { segment ->
                if (!segments.contains(segment)) {
                    return@forEach
                }
                segment.start.set(transform(Vector3(segment.start)))
                segment.end.set(transform(Vector3(segment.end)))
            }
        }
        faceStore.withChangeSuppressed {
            faceStore.getTriangles().forEach { triangle ->
                if (!triangles.contains(triangle)) {
                    return@forEach
                }
                triangle.a.set(transform(Vector3(triangle.a)))
                triangle.b.set(transform(Vector3(triangle.b)))
                triangle.c.set(transform(Vector3(triangle.c)))
            }
        }
    }

    private fun hotspotPointTransform(
        hotspot: HotspotStore.Hotspot,
        targetLocal: Vector3
    ): ((Vector3) -> Vector3)? {
        val baseLocal = hotspot.position
        if (targetLocal.epsilonEquals(baseLocal, 1e-6f)) {
            return null
        }
        val referenceLocal = hotspot.referencePosition ?: Vector3()
        return when (hotspot.operation) {
            HotspotStore.OperationKind.MOVE,
            HotspotStore.OperationKind.STRETCH -> {
                val delta = Vector3(targetLocal).sub(baseLocal)
                val transform: (Vector3) -> Vector3 = { point -> Vector3(point).add(delta) }
                transform
            }
            HotspotStore.OperationKind.SCALE -> {
                val baseVec = Vector3(baseLocal).sub(referenceLocal)
                val targetVec = Vector3(targetLocal).sub(referenceLocal)
                val baseLen = baseVec.len()
                val targetLen = targetVec.len()
                val factor = if (baseLen <= 1e-6f) 1f else (targetLen / baseLen).coerceIn(0.01f, 100f)
                val transform: (Vector3) -> Vector3 = { point ->
                    Vector3(point).sub(referenceLocal).scl(factor).add(referenceLocal)
                }
                transform
            }
            HotspotStore.OperationKind.ROTATE -> {
                val from = Vector3(baseLocal).sub(referenceLocal)
                val to = Vector3(targetLocal).sub(referenceLocal)
                val quat = Quaternion()
                if (from.len2() <= 1e-8f || to.len2() <= 1e-8f) {
                    quat.idt()
                } else {
                    quat.setFromCross(Vector3(from).nor(), Vector3(to).nor())
                }
                val transform: (Vector3) -> Vector3 = { point ->
                    Vector3(point).sub(referenceLocal).mul(quat).add(referenceLocal)
                }
                transform
            }
            HotspotStore.OperationKind.MULTIPLY_LINEAR,
            HotspotStore.OperationKind.MULTIPLY_VOLUMETRIC,
            HotspotStore.OperationKind.MULTIPLY_ROTATE_2D,
            HotspotStore.OperationKind.MULTIPLY_ROTATE_3D -> null
        }
    }

    private fun resolveHotspotTargets(
        group: GroupNode,
        hotspotStore: HotspotStore
    ): Map<String, Vector3> {
        val orderedHotspots = hotspotStore.allHotspots()
        val hotspotById = orderedHotspots.associateBy { it.id }
        val resolved = linkedMapOf<String, Vector3>()
        orderedHotspots.forEach { hotspot ->
            val target = group.hotspotPositionOverrides[hotspot.id] ?: hotspot.position
            resolved[hotspot.id] = Vector3(target)
        }
        val maxPasses = orderedHotspots.size.coerceAtLeast(1)
        for (pass in 0 until maxPasses) {
            var changed = false
            orderedHotspots.forEach { hotspot ->
                val target = resolved[hotspot.id] ?: Vector3(hotspot.position)
                val transform = hotspotPointTransform(hotspot, target) ?: return@forEach
                hotspot.attachedHotspotIds.forEach { attachedId ->
                    val attachedCurrent = resolved[attachedId]
                        ?: hotspotById[attachedId]?.let { Vector3(it.position) }
                        ?: return@forEach
                    val next = transform(attachedCurrent)
                    if (!next.epsilonEquals(attachedCurrent, 1e-6f)) {
                        resolved[attachedId] = next
                        changed = true
                    }
                }
            }
            if (!changed) {
                break
            }
        }
        return resolved
    }

    private fun signedAngleRadiansAround(a: Vector3, b: Vector3, axis: Vector3): Float {
        if (a.len2() <= 1e-10f || b.len2() <= 1e-10f || axis.len2() <= 1e-10f) {
            return 0f
        }
        val na = Vector3(a).nor()
        val nb = Vector3(b).nor()
        val nAxis = Vector3(axis).nor()
        val cross = Vector3(na).crs(nb)
        val sin = cross.dot(nAxis)
        val cos = na.dot(nb).coerceIn(-1f, 1f)
        return atan2(sin.toDouble(), cos.toDouble()).toFloat()
    }

    private fun extentAlongDirection(points: List<Vector3>, direction: Vector3): Float {
        if (points.isEmpty() || direction.len2() <= 1e-10f) {
            return 0f
        }
        val dir = Vector3(direction).nor()
        var minProj = Float.POSITIVE_INFINITY
        var maxProj = Float.NEGATIVE_INFINITY
        points.forEach { point ->
            val proj = point.dot(dir)
            if (proj < minProj) minProj = proj
            if (proj > maxProj) maxProj = proj
        }
        return (maxProj - minProj).coerceAtLeast(0f)
    }

    private fun multiplierAxisCount(step: Float, span: Float): Int {
        if (abs(step) <= 1e-6f) {
            return 0
        }
        val ratio = span / step
        if (ratio <= 0f) {
            return 0
        }
        return floor(abs(ratio).toDouble()).toInt()
    }

    private fun duplicateGeometry(
        lineStore: DraftLineStore,
        faceStore: DraftFaceStore,
        sourceSegments: List<DraftLineStore.Segment>,
        sourceTriangles: List<DraftFaceStore.Triangle>,
        transform: (Vector3) -> Vector3
    ): Int {
        if (sourceSegments.isEmpty() && sourceTriangles.isEmpty()) {
            return 0
        }
        lineStore.withChangeSuppressed {
            sourceSegments.forEach { segment ->
                lineStore.addSegment(
                    start = transform(Vector3(segment.start)),
                    end = transform(Vector3(segment.end)),
                    autoCleanup = false
                )
            }
        }
        faceStore.withChangeSuppressed {
            sourceTriangles.forEach { triangle ->
                faceStore.addTriangle(
                    a = transform(Vector3(triangle.a)),
                    b = transform(Vector3(triangle.b)),
                    c = transform(Vector3(triangle.c)),
                    color = faceStore.colorFor(triangle)
                )
            }
        }
        lineStore.notifyExternalChange()
        faceStore.notifyExternalChange()
        return sourceSegments.size + sourceTriangles.size
    }

    private fun applyHotspotToRuntimeGeometry(
        group: GroupNode,
        hotspotStore: HotspotStore,
        hotspot: HotspotStore.Hotspot,
        resolvedTargetLocal: Vector3,
        lineStore: DraftLineStore,
        faceStore: DraftFaceStore,
        vertexIdByHandle: Map<VertexHandle, String>
    ) {
        val baseLocal = hotspot.position
        val targetLocal = resolvedTargetLocal
        if (targetLocal.epsilonEquals(baseLocal, 1e-6f)) {
            return
        }
        val attachedSegments = attachedSegmentsFor(group, hotspot).toSet()
        val attachedTriangles = attachedTrianglesFor(group, hotspot).toSet()
        val attachedSegmentObjects = lineStore.getSegments()
            .filter { segment -> containsSegmentRef(hotspotStore, attachedSegments, segment) }
            .toSet()
        val attachedTriangleObjects = faceStore.getTriangles()
            .filter { triangle -> containsTriangleRef(hotspotStore, attachedTriangles, triangle) }
            .toSet()
        val explicitVertexHandles = if (hotspot.attachedVertexIds.isEmpty()) {
            emptySet()
        } else {
            vertexIdByHandle
                .filter { (_, vertexId) -> hotspot.attachedVertexIds.contains(vertexId) }
                .keys
        }
        val attachedVertexHandles = if (attachedSegments.isEmpty() && attachedTriangles.isEmpty()) {
            emptySet()
        } else {
            val attachedVertexIds = attachedHotspotVertexKeys(attachedSegments, attachedTriangles)
                .mapNotNullTo(linkedSetOf()) { key -> group.prototype.prototypeVertexIds[key] }
            vertexIdByHandle
                .filter { (_, vertexId) -> attachedVertexIds.contains(vertexId) }
                .keys
        }
        val drivenVertexHandles = linkedSetOf<VertexHandle>().apply {
            addAll(explicitVertexHandles)
            addAll(attachedVertexHandles)
        }
        if (hotspot.operation == HotspotStore.OperationKind.MULTIPLY_LINEAR ||
            hotspot.operation == HotspotStore.OperationKind.MULTIPLY_VOLUMETRIC ||
            hotspot.operation == HotspotStore.OperationKind.MULTIPLY_ROTATE_2D ||
            hotspot.operation == HotspotStore.OperationKind.MULTIPLY_ROTATE_3D
        ) {
            if (attachedSegmentObjects.isEmpty() && attachedTriangleObjects.isEmpty()) {
                return
            }
            val sourceSegments = attachedSegmentObjects.toList()
            val sourceTriangles = attachedTriangleObjects.toList()
            val sourcePoints = mutableListOf<Vector3>()
            sourceSegments.forEach { segment ->
                sourcePoints.add(Vector3(segment.start))
                sourcePoints.add(Vector3(segment.end))
            }
            sourceTriangles.forEach { triangle ->
                sourcePoints.add(Vector3(triangle.a))
                sourcePoints.add(Vector3(triangle.b))
                sourcePoints.add(Vector3(triangle.c))
            }
            if (sourcePoints.isEmpty()) {
                return
            }
            val maxCopies = 2048
            var copyCount = 0
            val baseLocal = hotspot.position
            val refLocal = hotspot.referencePosition
            when (hotspot.operation) {
                HotspotStore.OperationKind.MULTIPLY_LINEAR -> {
                    val reference = refLocal ?: return
                    val step = Vector3(baseLocal).sub(reference)
                    val stepLength = step.len()
                    if (stepLength <= 1e-6f) return
                    val dir = Vector3(step).nor()
                    val spanProjected = Vector3(targetLocal).sub(reference).dot(dir)
                    val steps = floor((spanProjected / stepLength).toDouble()).toInt().coerceAtMost(maxCopies)
                    if (steps <= 0) return
                    for (i in 1..steps) {
                        val offset = Vector3(step).scl(i.toFloat())
                        duplicateGeometry(
                            lineStore = lineStore,
                            faceStore = faceStore,
                            sourceSegments = sourceSegments,
                            sourceTriangles = sourceTriangles
                        ) { point ->
                            Vector3(point).add(offset)
                        }
                        copyCount++
                    }
                }
                HotspotStore.OperationKind.MULTIPLY_VOLUMETRIC -> {
                    val reference = refLocal ?: return
                    val stepX = baseLocal.x - reference.x
                    val stepY = baseLocal.y - reference.y
                    val stepZ = baseLocal.z - reference.z
                    val spanX = targetLocal.x - reference.x
                    val spanY = targetLocal.y - reference.y
                    val spanZ = targetLocal.z - reference.z
                    val nx = multiplierAxisCount(stepX, spanX)
                    val ny = multiplierAxisCount(stepY, spanY)
                    val nz = multiplierAxisCount(stepZ, spanZ)
                    loop@ for (ix in 0..nx) {
                        for (iy in 0..ny) {
                            for (iz in 0..nz) {
                                if (ix == 0 && iy == 0 && iz == 0) {
                                    continue
                                }
                                if (copyCount >= maxCopies) {
                                    break@loop
                                }
                                val offset = Vector3(
                                    ix.toFloat() * stepX,
                                    iy.toFloat() * stepY,
                                    iz.toFloat() * stepZ
                                )
                                duplicateGeometry(
                                    lineStore = lineStore,
                                    faceStore = faceStore,
                                    sourceSegments = sourceSegments,
                                    sourceTriangles = sourceTriangles
                                ) { point ->
                                    Vector3(point).add(offset)
                                }
                                copyCount++
                            }
                        }
                    }
                }
                HotspotStore.OperationKind.MULTIPLY_ROTATE_2D -> {
                    val reference = refLocal ?: return
                    val fromVec = Vector3(baseLocal).sub(reference)
                    val toVec = Vector3(targetLocal).sub(reference)
                    if (fromVec.len2() <= 1e-8f) return
                    val axis = Vector3(fromVec).crs(toVec).let {
                        if (it.len2() <= 1e-8f) Vector3(0f, 1f, 0f) else it.nor()
                    }
                    val totalAngle = signedAngleRadiansAround(fromVec, toVec, axis)
                    if (abs(totalAngle) <= 1e-6f) return
                    val radius = fromVec.len().coerceAtLeast(1e-4f)
                    val tangent = Vector3(axis).crs(fromVec).nor()
                    val stepLength = extentAlongDirection(sourcePoints, tangent)
                        .coerceAtLeast(radius * 0.1f)
                        .coerceAtLeast(1e-3f)
                    val stepAngle = (stepLength / radius).coerceAtLeast(1e-5f)
                    val steps = floor(abs(totalAngle) / stepAngle).toInt().coerceAtMost(maxCopies)
                    if (steps <= 0) return
                    for (i in 1..steps) {
                        var angle = stepAngle * i.toFloat() * if (totalAngle < 0f) -1f else 1f
                        if (abs(angle) > abs(totalAngle)) {
                            angle = totalAngle
                        }
                        val quat = Quaternion().setFromAxisRad(axis, angle)
                        duplicateGeometry(
                            lineStore = lineStore,
                            faceStore = faceStore,
                            sourceSegments = sourceSegments,
                            sourceTriangles = sourceTriangles
                        ) { point ->
                            Vector3(point).sub(reference).mul(quat).add(reference)
                        }
                        copyCount++
                    }
                }
                HotspotStore.OperationKind.MULTIPLY_ROTATE_3D -> {
                    val reference = refLocal ?: return
                    val axis = Vector3(0f, 1f, 0f)
                    val fromVec = Vector3(baseLocal).sub(reference)
                    if (fromVec.len2() <= 1e-8f) return
                    val toVec = Vector3(targetLocal).sub(reference)
                    val fromRadial = Vector3(fromVec).sub(Vector3(axis).scl(fromVec.dot(axis)))
                    val toRadial = Vector3(toVec).sub(Vector3(axis).scl(toVec.dot(axis)))
                    if (fromRadial.len2() <= 1e-8f) return
                    val totalAngle = signedAngleRadiansAround(fromRadial, toRadial, axis)
                    val totalHeight = targetLocal.y - reference.y
                    val radius = fromRadial.len().coerceAtLeast(1e-4f)
                    val helixLength = sqrt(totalAngle * totalAngle * radius * radius + totalHeight * totalHeight)
                    if (helixLength <= 1e-6f) return
                    val tangent = Vector3(axis).crs(fromRadial).nor()
                    val stepLength = max(
                        extentAlongDirection(sourcePoints, tangent),
                        extentAlongDirection(sourcePoints, axis)
                    ).coerceAtLeast(1e-3f)
                    val steps = floor(helixLength / stepLength).toInt().coerceAtMost(maxCopies)
                    if (steps <= 0) return
                    for (i in 1..steps) {
                        val distance = (stepLength * i.toFloat()).coerceAtMost(helixLength)
                        val t = (distance / helixLength).coerceIn(0f, 1f)
                        val angle = totalAngle * t
                        val yOffset = totalHeight * t
                        val quat = Quaternion().setFromAxisRad(axis, angle)
                        duplicateGeometry(
                            lineStore = lineStore,
                            faceStore = faceStore,
                            sourceSegments = sourceSegments,
                            sourceTriangles = sourceTriangles
                        ) { point ->
                            Vector3(point).sub(reference).mul(quat).add(reference).add(0f, yOffset, 0f)
                        }
                        copyCount++
                    }
                }
                else -> Unit
            }
            return
        }

        if (hotspot.operation == HotspotStore.OperationKind.STRETCH) {
            val delta = Vector3(targetLocal).sub(baseLocal)
            val anchorHandles = if (
                attachedSegments.isEmpty() &&
                attachedTriangles.isEmpty() &&
                explicitVertexHandles.isEmpty()
            ) {
                captureVertexHandlesAtPoint(lineStore, faceStore, baseLocal)
            } else {
                emptySet()
            }
            lineStore.withChangeSuppressed {
                lineStore.getSegments().forEach { segment ->
                    if (attachedSegmentObjects.contains(segment)) {
                        segment.start.add(delta)
                        segment.end.add(delta)
                    } else {
                        if (drivenVertexHandles.contains(VertexHandle(0, segment.id, 0)) ||
                            anchorHandles.contains(VertexHandle(0, segment.id, 0))
                        ) {
                            segment.start.add(delta)
                        }
                        if (drivenVertexHandles.contains(VertexHandle(0, segment.id, 1)) ||
                            anchorHandles.contains(VertexHandle(0, segment.id, 1))
                        ) {
                            segment.end.add(delta)
                        }
                    }
                }
            }
            faceStore.withChangeSuppressed {
                faceStore.getTriangles().forEach { triangle ->
                    if (attachedTriangleObjects.contains(triangle)) {
                        triangle.a.add(delta)
                        triangle.b.add(delta)
                        triangle.c.add(delta)
                    } else {
                        if (drivenVertexHandles.contains(VertexHandle(1, triangle.id, 0)) ||
                            anchorHandles.contains(VertexHandle(1, triangle.id, 0))
                        ) {
                            triangle.a.add(delta)
                        }
                        if (drivenVertexHandles.contains(VertexHandle(1, triangle.id, 1)) ||
                            anchorHandles.contains(VertexHandle(1, triangle.id, 1))
                        ) {
                            triangle.b.add(delta)
                        }
                        if (drivenVertexHandles.contains(VertexHandle(1, triangle.id, 2)) ||
                            anchorHandles.contains(VertexHandle(1, triangle.id, 2))
                        ) {
                            triangle.c.add(delta)
                        }
                    }
                }
            }
            return
        }

        val transform = hotspotPointTransform(hotspot, targetLocal) ?: return

        if (attachedSegmentObjects.isNotEmpty() || attachedTriangleObjects.isNotEmpty() || drivenVertexHandles.isNotEmpty()) {
            lineStore.withChangeSuppressed {
                lineStore.getSegments().forEach { segment ->
                    if (attachedSegmentObjects.contains(segment)) {
                        segment.start.set(transform(Vector3(segment.start)))
                        segment.end.set(transform(Vector3(segment.end)))
                    } else {
                        if (drivenVertexHandles.contains(VertexHandle(0, segment.id, 0))) {
                            segment.start.set(transform(Vector3(segment.start)))
                        }
                        if (drivenVertexHandles.contains(VertexHandle(0, segment.id, 1))) {
                            segment.end.set(transform(Vector3(segment.end)))
                        }
                    }
                }
            }
            faceStore.withChangeSuppressed {
                faceStore.getTriangles().forEach { triangle ->
                    if (attachedTriangleObjects.contains(triangle)) {
                        triangle.a.set(transform(Vector3(triangle.a)))
                        triangle.b.set(transform(Vector3(triangle.b)))
                        triangle.c.set(transform(Vector3(triangle.c)))
                    } else {
                        if (drivenVertexHandles.contains(VertexHandle(1, triangle.id, 0))) {
                            triangle.a.set(transform(Vector3(triangle.a)))
                        }
                        if (drivenVertexHandles.contains(VertexHandle(1, triangle.id, 1))) {
                            triangle.b.set(transform(Vector3(triangle.b)))
                        }
                        if (drivenVertexHandles.contains(VertexHandle(1, triangle.id, 2))) {
                            triangle.c.set(transform(Vector3(triangle.c)))
                        }
                    }
                }
            }
        } else {
            val anchorHandles = captureVertexHandlesAtPoint(lineStore, faceStore, baseLocal)
            val anchorKey = hotspotVertexKey(baseLocal)
            lineStore.withChangeSuppressed {
                lineStore.getSegments().forEach { segment ->
                    if (anchorHandles.contains(VertexHandle(0, segment.id, 0)) ||
                        hotspotVertexKey(segment.start) == anchorKey
                    ) {
                        segment.start.set(transform(Vector3(segment.start)))
                    }
                    if (anchorHandles.contains(VertexHandle(0, segment.id, 1)) ||
                        hotspotVertexKey(segment.end) == anchorKey
                    ) {
                        segment.end.set(transform(Vector3(segment.end)))
                    }
                }
            }
            faceStore.withChangeSuppressed {
                faceStore.getTriangles().forEach { triangle ->
                    if (anchorHandles.contains(VertexHandle(1, triangle.id, 0)) ||
                        hotspotVertexKey(triangle.a) == anchorKey
                    ) {
                        triangle.a.set(transform(Vector3(triangle.a)))
                    }
                    if (anchorHandles.contains(VertexHandle(1, triangle.id, 1)) ||
                        hotspotVertexKey(triangle.b) == anchorKey
                    ) {
                        triangle.b.set(transform(Vector3(triangle.b)))
                    }
                    if (anchorHandles.contains(VertexHandle(1, triangle.id, 2)) ||
                        hotspotVertexKey(triangle.c) == anchorKey
                    ) {
                        triangle.c.set(transform(Vector3(triangle.c)))
                    }
                }
            }
        }
    }

    private fun recomputeInstanceGeometryFromPrototype(group: GroupNode) {
        if (group === root || group.editPrototypeMode) {
            group.runtimeHotspotPositions.clear()
            return
        }
        refreshPrototypeVertexIds(group.prototype)
        val selectedSegmentIds = group.lineStoreOverride?.getSelected()?.map { segment -> segment.id }?.toSet().orEmpty()
        val selectedFaceIds = group.faceStoreOverride?.getSelected()?.map { triangle -> triangle.id }?.toSet().orEmpty()

        val lineStore = cloneLineStore(group.prototype.lineStore)
        val faceStore = cloneFaceStore(group.prototype.faceStore)
        val dimensionStore = cloneDimensionStore(group.prototype.dimensionStore)
        val textStore = cloneTextStore(group.prototype.textStore)
        val vertexIdByHandle = buildVertexIdByHandle(lineStore, faceStore, group.prototype)

        val hotspotStore = group.prototype.hotspotStore
        val resolvedTargets = resolveHotspotTargets(group, hotspotStore)
        group.runtimeHotspotPositions.clear()
        hotspotStore.allHotspots().forEach { hotspot ->
            val resolved = resolvedTargets[hotspot.id] ?: hotspot.position
            group.runtimeHotspotPositions[hotspot.id] = Vector3(resolved)
        }
        hotspotStore.allHotspots().forEach { hotspot ->
            applyHotspotToRuntimeGeometry(
                group = group,
                hotspotStore = hotspotStore,
                hotspot = hotspot,
                resolvedTargetLocal = resolvedTargets[hotspot.id] ?: hotspot.position,
                lineStore = lineStore,
                faceStore = faceStore,
                vertexIdByHandle = vertexIdByHandle
            )
        }
        lineStore.notifyExternalChange()
        faceStore.notifyExternalChange()

        if (selectedSegmentIds.isNotEmpty()) {
            lineStore.getSegments().forEach { segment ->
                if (selectedSegmentIds.contains(segment.id)) {
                    lineStore.addSelection(segment)
                }
            }
        }
        if (selectedFaceIds.isNotEmpty()) {
            faceStore.getTriangles().forEach { triangle ->
                if (selectedFaceIds.contains(triangle.id)) {
                    faceStore.addSelection(triangle)
                }
            }
        }

        group.lineStoreOverride = lineStore
        group.faceStoreOverride = faceStore
        group.dimensionStoreOverride = dimensionStore
        group.textStoreOverride = textStore
    }

    fun recomputeAllInstanceGeometryFromPrototypes() {
        prototypes.values.forEach { prototype ->
            refreshPrototypeVertexIds(prototype)
        }
        walkGroups(root) { group ->
            recomputeInstanceGeometryFromPrototype(group)
        }
    }

    private fun ensureIndependentPrototypeForHotspotGroup(group: GroupNode) {
        if (group === root || group === activeGroup) {
            return
        }
        val currentPrototype = group.prototype
        val instances = prototypeInstances[currentPrototype.id] ?: return
        if (instances.size <= 1) {
            return
        }

        val newPrototype = clonePrototypeForInstance(currentPrototype)
        registerPrototype(newPrototype)
        instances.remove(group)
        if (instances.isEmpty()) {
            prototypeInstances.remove(currentPrototype.id)
            if (currentPrototype.id != rootPrototype.id) {
                prototypes.remove(currentPrototype.id)
            }
        }
        group.prototype = newPrototype
        prototypeInstances.getOrPut(newPrototype.id) { mutableSetOf() }.add(group)
        applyChangeListener(group)
    }

    private fun clonePrototypeForInstance(source: ObjectPrototype): ObjectPrototype {
        val clonedLineStore = cloneLineStore(source.lineStore)
        val clonedFaceStore = cloneFaceStore(source.faceStore)
        val clonedDimensionStore = cloneDimensionStore(source.dimensionStore)
        val clonedTextStore = cloneTextStore(source.textStore)

        val clonedVoxelStore = source.voxelStore?.let { voxelStore ->
            VoxelStore().also { clone ->
                voxelStore.all().forEach { voxel ->
                    clone.set(voxel.x, voxel.y, voxel.z, voxel.color)
                }
            }
        }

        val clonedArchitectureStore = source.architectureStore?.let { architecture ->
            ArchitectureStore().also { clone ->
                architecture.allWalls().forEach { wall ->
                    val addedWall = clone.addWall(
                        start = wall.start,
                        end = wall.end,
                        thickness = wall.thickness,
                        height = wall.height,
                        inclinationDeg = wall.inclinationDeg,
                        exteriorColor = wall.exteriorColor,
                        interiorColor = wall.interiorColor,
                        name = wall.name,
                        id = wall.id
                    )
                    wall.holes.forEach { hole ->
                        clone.addHole(
                            wallId = addedWall.id,
                            u0 = hole.u0,
                            u1 = hole.u1,
                            v0 = hole.v0,
                            v1 = hole.v1,
                            minSize = 0f,
                            name = hole.name,
                            id = hole.id
                        )
                    }
                }
                architecture.allSlabs().forEach { slab ->
                    val added = clone.addSlab(
                        minCorner = slab.min,
                        maxCorner = slab.max,
                        thickness = slab.thickness,
                        topColor = slab.topColor,
                        bottomColor = slab.bottomColor,
                        sideColor = slab.sideColor,
                        name = slab.name,
                        id = slab.id
                    )
                    added.axisU.set(slab.axisU)
                    added.axisV.set(slab.axisV)
                    added.normal.set(slab.normal)
                }
                architecture.allStairs().forEach { stair ->
                    clone.addStair(
                        minCorner = stair.min,
                        maxCorner = stair.max,
                        contourPoints = stair.contour,
                        walkingPathPoints = stair.walkingPath,
                        walkingStart = stair.walkingStart,
                        walkingEnd = stair.walkingEnd,
                        height = stair.height,
                        stepCount = stair.stepCount,
                        supportThickness = stair.supportThickness,
                        railLeftEnabled = stair.railLeftEnabled,
                        railRightEnabled = stair.railRightEnabled,
                        treadColor = stair.treadColor,
                        supportColor = stair.supportColor,
                        name = stair.name,
                        id = stair.id
                    )
                }
                architecture.allFrames().forEach { frame ->
                    clone.addFrame(
                        cornerA = frame.cornerA,
                        cornerB = frame.cornerB,
                        contourPoints = frame.contour,
                        normal = frame.normal,
                        depth = frame.depth,
                        frameWidth = frame.frameWidth,
                        kind = frame.kind,
                        color = frame.color,
                        glazingEnabled = frame.glazingEnabled,
                        glazingColor = frame.glazingColor,
                        name = frame.name,
                        id = frame.id
                    )
                }
                architecture.selectedElements().forEach { selection ->
                    clone.addSelectedElement(selection.kind, selection.id)
                }
            }
        }

        val clonedHvacStore = source.hvacStore?.let { hvac ->
            HvacStore().also { clone ->
                hvac.allPlumbingRuns().forEach { run ->
                    clone.addPlumbingRun(
                        path = run.path,
                        diameter = run.diameter,
                        sides = run.sides,
                        color = run.color,
                        name = run.name,
                        id = run.id
                    )
                }
                hvac.allVentilationDucts().forEach { duct ->
                    clone.addVentilationDuct(
                        start = duct.start,
                        end = duct.end,
                        binormalRef = duct.binormalRef,
                        autoJoinEnabled = duct.autoJoinEnabled,
                        width = duct.width,
                        height = duct.height,
                        humpHalfSpan = duct.humpHalfSpan,
                        humpClearance = duct.humpClearance,
                        color = duct.color,
                        name = duct.name,
                        id = duct.id
                    )
                }
                hvac.selectedElements().forEach { selection ->
                    clone.addSelectedElement(selection.kind, selection.id)
                }
            }
        }

        val clonedHotspotStore = HotspotStore()
        source.hotspotStore.allHotspots().forEach { hotspot ->
            clonedHotspotStore.addHotspot(
                position = hotspot.position,
                operation = hotspot.operation,
                shape = hotspot.shape,
                color = hotspot.color,
                referencePosition = hotspot.referencePosition,
                attachedHotspotIds = hotspot.attachedHotspotIds,
                attachedVertexIds = hotspot.attachedVertexIds,
                attachedSegments = hotspot.attachedSegments,
                attachedTriangles = hotspot.attachedTriangles,
                name = hotspot.name,
                id = hotspot.id
            )
        }
        source.hotspotStore.selectedHotspots().forEach { selection ->
            clonedHotspotStore.addSelected(selection.id)
        }

        return ObjectPrototype(
            id = java.util.UUID.randomUUID().toString(),
            name = source.name,
            definitionOrigin = Vector3(source.definitionOrigin),
            definitionAxisU = Vector3(source.definitionAxisU),
            definitionAxisV = Vector3(source.definitionAxisV),
            definitionAxisW = Vector3(source.definitionAxisW),
            gluedToSurface = source.gluedToSurface,
            kind = source.kind,
            voxelColor = Color(source.voxelColor),
            voxelStore = clonedVoxelStore,
            architectureStore = clonedArchitectureStore,
            hvacStore = clonedHvacStore,
            hotspotStore = clonedHotspotStore,
            lineStore = clonedLineStore,
            faceStore = clonedFaceStore,
            dimensionStore = clonedDimensionStore,
            textStore = clonedTextStore,
            prototypeVertexIds = linkedMapOf<HotspotStore.VertexKey, String>().also { map ->
                source.prototypeVertexIds.forEach { (key, id) -> map[key] = id }
            }
        )
    }

    fun addHvacPlumbingRun(
        pathWorld: List<Vector3>,
        diameter: Float,
        sides: Int,
        color: Color
    ): Boolean {
        if (pathWorld.size < 2) {
            return false
        }
        val cleaned = mutableListOf<Vector3>()
        pathWorld.forEach { point ->
            if (cleaned.isEmpty() || cleaned.last().dst2(point) > 1e-6f) {
                cleaned.add(Vector3(point))
            }
        }
        if (cleaned.size < 2) {
            return false
        }
        hvacStoreFor().addPlumbingRun(
            path = cleaned,
            diameter = diameter.coerceAtLeast(0.01f),
            sides = sides.coerceIn(3, 128),
            color = color
        )
        rebuildHvacGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun addHvacVentilationDuct(
        startWorld: Vector3,
        endWorld: Vector3,
        binormalRefWorld: Vector3,
        autoJoinEnabled: Boolean,
        width: Float,
        height: Float,
        humpHalfSpan: Float,
        humpClearance: Float,
        color: Color
    ): Boolean {
        if (startWorld.dst2(endWorld) <= 1e-6f) {
            return false
        }
        hvacStoreFor().addVentilationDuct(
            start = startWorld,
            end = endWorld,
            binormalRef = binormalRefWorld,
            autoJoinEnabled = autoJoinEnabled,
            width = width.coerceAtLeast(0.01f),
            height = height.coerceAtLeast(0.01f),
            humpHalfSpan = humpHalfSpan.coerceAtLeast(0.01f),
            humpClearance = humpClearance.coerceAtLeast(0f),
            color = color
        )
        rebuildHvacGeometry(rootPrototype)
        notifyChange()
        return true
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
        val store = architectureStoreFor(group)
        val startWorld = group.toWorld(start)
        val endWorld = group.toWorld(end)
        if (startWorld.dst2(endWorld) <= 1e-6f) {
            return false
        }
        store.addWall(
            start = startWorld,
            end = endWorld,
            thickness = thickness.coerceAtLeast(0.01f),
            height = height.coerceAtLeast(0.05f),
            inclinationDeg = inclinationDeg,
            exteriorColor = exteriorColor,
            interiorColor = interiorColor
        )
        rebuildArchitectureGeometry(rootPrototype)
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
        val store = architectureStoreFor(group)
        val minWorld = group.toWorld(minCorner)
        val maxWorld = group.toWorld(maxCorner)
        if (minWorld.dst2(maxWorld) <= 1e-6f) {
            return false
        }
        store.addSlab(
            minCorner = minWorld,
            maxCorner = maxWorld,
            thickness = thickness.coerceAtLeast(0.01f),
            topColor = topColor,
            bottomColor = bottomColor,
            sideColor = sideColor
        )
        rebuildArchitectureGeometry(rootPrototype)
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
        val store = architectureStoreFor(group)
        val minWorld = group.toWorld(minCorner)
        val maxWorld = group.toWorld(maxCorner)
        if (minWorld.dst2(maxWorld) <= 1e-6f) {
            return false
        }
        store.addStair(
            minCorner = minWorld,
            maxCorner = maxWorld,
            contourPoints = contourPoints.map { group.toWorld(it) },
            walkingPathPoints = walkingPathPoints.map { group.toWorld(it) },
            walkingStart = group.toWorld(walkingStart),
            walkingEnd = group.toWorld(walkingEnd),
            height = height.coerceAtLeast(0.05f),
            stepCount = stepCount.coerceAtLeast(1),
            supportThickness = supportThickness.coerceAtLeast(0.01f),
            railLeftEnabled = railLeftEnabled,
            railRightEnabled = railRightEnabled,
            treadColor = treadColor,
            supportColor = supportColor
        )
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun addArchitectureFrame(
        group: GroupNode,
        cornerA: Vector3,
        cornerB: Vector3,
        contourPoints: List<Vector3> = emptyList(),
        normal: Vector3,
        depth: Float,
        frameWidth: Float,
        kind: ArchitectureStore.FrameKind,
        color: Color,
        glazingEnabled: Boolean,
        glazingColor: Color
    ): Boolean {
        val store = architectureStoreFor(group)
        val cornerAWorld = group.toWorld(cornerA)
        val cornerBWorld = group.toWorld(cornerB)
        if (cornerAWorld.dst2(cornerBWorld) <= 1e-6f) {
            return false
        }
        val contourWorld = contourPoints.map { group.toWorld(it) }
        val normalWorld = group.vectorToWorld(normal)
        val safeNormal = if (normalWorld.len2() <= 1e-6f) Vector3(0f, 1f, 0f) else Vector3(normalWorld).nor()
        store.addFrame(
            cornerA = cornerAWorld,
            cornerB = cornerBWorld,
            contourPoints = contourWorld,
            normal = safeNormal,
            depth = depth.coerceAtLeast(0.01f),
            frameWidth = frameWidth.coerceAtLeast(0.01f),
            kind = kind,
            color = color,
            glazingEnabled = glazingEnabled,
            glazingColor = glazingColor
        )
        rebuildArchitectureGeometry(rootPrototype)
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
        val store = architectureStoreFor(group)
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
        rebuildArchitectureGeometry(rootPrototype)
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
        val store = architectureStoreFor(group)
        if (!store.updateSlab(id, thickness.coerceAtLeast(0.01f), topColor, bottomColor, sideColor)) {
            return false
        }
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun updateArchitectureSlabCorners(
        group: GroupNode,
        id: String,
        minCorner: Vector3,
        maxCorner: Vector3
    ): Boolean {
        val store = architectureStoreFor(group)
        val minWorld = group.toWorld(minCorner)
        val maxWorld = group.toWorld(maxCorner)
        if (minWorld.dst2(maxWorld) <= 1e-6f) {
            return false
        }
        if (!store.updateSlabCorners(id, minWorld, maxWorld)) {
            return false
        }
        rebuildArchitectureGeometry(rootPrototype)
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
        val store = architectureStoreFor(group)
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
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun updateArchitectureFrame(
        group: GroupNode,
        id: String,
        depth: Float,
        frameWidth: Float,
        color: Color,
        glazingEnabled: Boolean,
        glazingColor: Color
    ): Boolean {
        val store = architectureStoreFor(group)
        if (
            !store.updateFrame(
                id,
                depth.coerceAtLeast(0.01f),
                frameWidth.coerceAtLeast(0.01f),
                color,
                glazingEnabled,
                glazingColor
            )
        ) {
            return false
        }
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun updateArchitectureFrameCorners(
        group: GroupNode,
        id: String,
        cornerA: Vector3,
        cornerB: Vector3
    ): Boolean {
        val store = architectureStoreFor(group)
        val frame = store.allFrames().firstOrNull { it.id == id } ?: return false
        val cornerAWorld = group.toWorld(cornerA)
        val cornerBWorld = group.toWorld(cornerB)
        if (cornerAWorld.dst2(cornerBWorld) <= 1e-6f) {
            return false
        }
        val resizedContour = rescaleFrameContour(frame, cornerAWorld, cornerBWorld) ?: return false
        if (!store.updateFrameCorners(id, cornerAWorld, cornerBWorld, resizedContour)) {
            return false
        }
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun updateArchitectureElementName(
        group: GroupNode,
        kind: ArchitectureStore.ElementKind,
        id: String,
        name: String
    ): Boolean {
        val store = architectureStoreFor(group)
        val updated = when (kind) {
            ArchitectureStore.ElementKind.WALL -> store.updateWallName(id, name)
            ArchitectureStore.ElementKind.SLAB -> store.updateSlabName(id, name)
            ArchitectureStore.ElementKind.STAIR -> store.updateStairName(id, name)
            ArchitectureStore.ElementKind.FRAME -> store.updateFrameName(id, name)
        }
        if (!updated) {
            return false
        }
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun selectedArchitectureElement(group: GroupNode): ArchitectureStore.ElementSelection? {
        return architectureStoreFor(group).selectedElement()
    }

    fun selectedArchitectureElements(group: GroupNode): Set<ArchitectureStore.ElementSelection> {
        return architectureStoreFor(group).selectedElements()
    }

    fun selectedArchitectureWall(group: GroupNode): ArchitectureStore.WallSegment? {
        return architectureStoreFor(group).selectedWall()
    }

    fun architectureWallById(group: GroupNode, id: String): ArchitectureStore.WallSegment? {
        return architectureStoreFor(group).wallById(id)
    }

    fun architectureSlabById(group: GroupNode, id: String): ArchitectureStore.Slab? {
        return architectureStoreFor(group).allSlabs().firstOrNull { it.id == id }
    }

    fun architectureStairById(group: GroupNode, id: String): ArchitectureStore.Stair? {
        return architectureStoreFor(group).allStairs().firstOrNull { it.id == id }
    }

    fun architectureFrameById(group: GroupNode, id: String): ArchitectureStore.Frame? {
        return architectureStoreFor(group).allFrames().firstOrNull { it.id == id }
    }

    fun selectedHvacElement(group: GroupNode): HvacStore.ElementSelection? {
        return hvacStoreFor(group).selectedElement()
    }

    fun selectedHvacElements(group: GroupNode): Set<HvacStore.ElementSelection> {
        return hvacStoreFor(group).selectedElements()
    }

    fun hvacPlumbingById(group: GroupNode, id: String): HvacStore.PlumbingRun? {
        return hvacStoreFor(group).plumbingById(id)
    }

    fun hvacVentilationById(group: GroupNode, id: String): HvacStore.VentilationDuct? {
        return hvacStoreFor(group).ventilationById(id)
    }

    fun selectedHotspots(group: GroupNode): Set<HotspotStore.HotspotSelection> {
        val available = hotspotStoreFor(group).allHotspots().mapTo(mutableSetOf()) { it.id }
        return group.hotspotSelectionIds
            .filter { id -> available.contains(id) }
            .mapTo(linkedSetOf()) { id -> HotspotStore.HotspotSelection(id) }
    }

    fun selectedHotspot(group: GroupNode): HotspotStore.Hotspot? {
        val selection = group.hotspotSelectionIds.lastOrNull() ?: return null
        return hotspotStoreFor(group).hotspotById(selection)
    }

    fun hotspotById(group: GroupNode, id: String): HotspotStore.Hotspot? {
        return hotspotStoreFor(group).hotspotById(id)
    }

    fun clearHotspotSelection(group: GroupNode) {
        group.hotspotSelectionIds.clear()
    }

    fun addHotspot(
        group: GroupNode,
        position: Vector3,
        operation: HotspotStore.OperationKind,
        shape: HotspotStore.ShapeKind = HotspotStore.ShapeKind.CIRCLE,
        color: Color = Color(0.2f, 0.55f, 0.95f, 1f),
        referencePosition: Vector3? = null
    ): HotspotStore.Hotspot? {
        if (group !== activeGroup) {
            return null
        }
        val store = hotspotStoreFor(group)
        val hotspot = store.addHotspot(
            position = Vector3(position),
            operation = operation,
            shape = shape,
            color = color,
            referencePosition = referencePosition?.let { Vector3(it) }
        )
        group.hotspotSelectionIds.clear()
        group.hotspotSelectionIds.add(hotspot.id)
        notifyChange()
        return hotspot
    }

    fun selectHotspot(
        group: GroupNode,
        id: String,
        mode: HotspotSelectionMode
    ): Boolean {
        if (hotspotStoreFor(group).hotspotById(id) == null) {
            if (mode == HotspotSelectionMode.REPLACE) {
                group.hotspotSelectionIds.clear()
            }
            return false
        }
        return when (mode) {
            HotspotSelectionMode.REPLACE -> {
                val changed = group.hotspotSelectionIds.size != 1 || !group.hotspotSelectionIds.contains(id)
                group.hotspotSelectionIds.clear()
                group.hotspotSelectionIds.add(id)
                changed
            }
            HotspotSelectionMode.ADD -> group.hotspotSelectionIds.add(id)
            HotspotSelectionMode.REMOVE -> group.hotspotSelectionIds.remove(id)
        }
    }

    fun deleteSelectedHotspots(group: GroupNode): Int {
        if (group !== activeGroup) {
            return 0
        }
        if (group.hotspotSelectionIds.isEmpty()) {
            return 0
        }
        val store = hotspotStoreFor(group)
        val selected = group.hotspotSelectionIds.toSet()
        val removed = store.deleteByIds(selected)
        group.hotspotSelectionIds.clear()
        if (removed > 0) {
            prototypeInstances[group.prototype.id].orEmpty().forEach { instance ->
                selected.forEach { hotspotId ->
                    instance.hotspotSelectionIds.remove(hotspotId)
                    instance.hotspotPositionOverrides.remove(hotspotId)
                    instance.runtimeHotspotPositions.remove(hotspotId)
                    instance.hotspotAttachedSegmentOverrides.remove(hotspotId)
                    instance.hotspotAttachedTriangleOverrides.remove(hotspotId)
                }
            }
        }
        if (removed > 0) {
            notifyChange()
        }
        return removed
    }

    fun updateHotspotOperation(group: GroupNode, id: String, operation: HotspotStore.OperationKind): Boolean {
        if (group !== activeGroup) {
            return false
        }
        if (hotspotStoreFor(group).hotspotById(id) == null) {
            return false
        }
        val updated = hotspotStoreFor(group).updateOperation(id, operation)
        if (updated) {
            notifyChange()
        }
        return updated
    }

    fun updateHotspotShape(group: GroupNode, id: String, shape: HotspotStore.ShapeKind): Boolean {
        if (group !== activeGroup) {
            return false
        }
        if (hotspotStoreFor(group).hotspotById(id) == null) {
            return false
        }
        val updated = hotspotStoreFor(group).updateShape(id, shape)
        if (updated) {
            notifyChange()
        }
        return updated
    }

    fun updateHotspotColor(group: GroupNode, id: String, color: Color): Boolean {
        if (group !== activeGroup) {
            return false
        }
        if (hotspotStoreFor(group).hotspotById(id) == null) {
            return false
        }
        val updated = hotspotStoreFor(group).updateColor(id, color)
        if (updated) {
            notifyChange()
        }
        return updated
    }

    fun updateHotspotName(group: GroupNode, id: String, name: String): Boolean {
        if (group !== activeGroup) {
            return false
        }
        if (hotspotStoreFor(group).hotspotById(id) == null) {
            return false
        }
        val updated = hotspotStoreFor(group).updateName(id, name)
        if (updated) {
            notifyChange()
        }
        return updated
    }

    fun updateHotspotReferencePosition(group: GroupNode, id: String, referencePosition: Vector3?): Boolean {
        if (group !== activeGroup) {
            return false
        }
        if (hotspotStoreFor(group).hotspotById(id) == null) {
            return false
        }
        val updated = hotspotStoreFor(group).updateReference(id, referencePosition)
        if (updated) {
            notifyChange()
        }
        return updated
    }

    fun updateHotspotPosition(group: GroupNode, id: String, position: Vector3): Boolean {
        if (hotspotStoreFor(group).hotspotById(id) == null) {
            return false
        }
        if (group !== activeGroup) {
            group.hotspotPositionOverrides[id] = Vector3(position)
            group.runtimeHotspotPositions.clear()
            notifyChange()
            return true
        }
        group.hotspotPositionOverrides.remove(id)
        if (group.editPrototypeMode) {
            prototypeInstances[group.prototype.id].orEmpty().forEach { instance ->
                instance.runtimeHotspotPositions.clear()
            }
        } else {
            group.runtimeHotspotPositions.clear()
        }
        val updated = hotspotStoreFor(group).updatePosition(id, position)
        if (updated) {
            notifyChange()
        }
        return updated
    }

    fun hotspotMarkersWorld(group: GroupNode): List<HotspotMarker> {
        val store = hotspotStoreFor(group)
        return store.allHotspots().map { hotspot ->
            val localPosition = if (group.editPrototypeMode) {
                hotspot.position
            } else {
                group.runtimeHotspotPositions[hotspot.id]
                    ?: group.hotspotPositionOverrides[hotspot.id]
                    ?: hotspot.position
            }
            HotspotMarker(
                id = hotspot.id,
                world = group.toWorld(localPosition),
                selected = group.hotspotSelectionIds.contains(hotspot.id),
                referenceWorld = hotspot.referencePosition?.let { group.toWorld(it) },
                shape = hotspot.shape,
                color = Color(hotspot.color)
            )
        }
    }

    fun buildHotspotDragPreview(
        group: GroupNode,
        id: String,
        targetWorld: Vector3,
        maxLines: Int = 50_000
    ): HotspotDragPreview? {
        val store = hotspotStoreFor(group)
        val hotspot = store.hotspotById(id) ?: return null
        val currentLocal = if (group.editPrototypeMode) {
            hotspot.position
        } else {
            group.runtimeHotspotPositions[hotspot.id]
                ?: group.hotspotPositionOverrides[hotspot.id]
                ?: hotspot.position
        }
        val previewLines = mutableListOf<Pair<Vector3, Vector3>>()
        if (group.editPrototypeMode) {
            collectHotspotPreviewLines(
                group = group,
                lineStore = group.lineStore,
                faceStore = group.faceStore,
                target = previewLines,
                maxLines = maxLines
            )
        } else {
            refreshPrototypeVertexIds(group.prototype)
            val previewLineStore = cloneLineStore(group.prototype.lineStore)
            val previewFaceStore = cloneFaceStore(group.prototype.faceStore)
            val resolvedTargets = resolveHotspotTargets(group, group.prototype.hotspotStore).toMutableMap()
            resolvedTargets[id] = group.toLocal(targetWorld)
            val vertexIdByHandle = buildVertexIdByHandle(previewLineStore, previewFaceStore, group.prototype)
            group.prototype.hotspotStore.allHotspots().forEach { entry ->
                applyHotspotToRuntimeGeometry(
                    group = group,
                    hotspotStore = group.prototype.hotspotStore,
                    hotspot = entry,
                    resolvedTargetLocal = resolvedTargets[entry.id] ?: entry.position,
                    lineStore = previewLineStore,
                    faceStore = previewFaceStore,
                    vertexIdByHandle = vertexIdByHandle
                )
            }
            previewLineStore.notifyExternalChange()
            previewFaceStore.notifyExternalChange()
            collectHotspotPreviewLines(
                group = group,
                lineStore = previewLineStore,
                faceStore = previewFaceStore,
                target = previewLines,
                maxLines = maxLines
            )
        }
        return HotspotDragPreview(
            currentWorld = group.toWorld(currentLocal),
            targetWorld = Vector3(targetWorld),
            lines = previewLines
        )
    }

    private fun collectHotspotPreviewLines(
        group: GroupNode,
        lineStore: DraftLineStore,
        faceStore: DraftFaceStore,
        target: MutableList<Pair<Vector3, Vector3>>,
        maxLines: Int
    ) {
        lineStore.getSegments().forEach { segment ->
            if (target.size >= maxLines) {
                return
            }
            target += group.toWorld(segment.start) to group.toWorld(segment.end)
        }
        faceStore.getTriangles().forEach { triangle ->
            if (target.size >= maxLines) {
                return
            }
            val a = group.toWorld(triangle.a)
            val b = group.toWorld(triangle.b)
            val c = group.toWorld(triangle.c)
            target += a to b
            if (target.size >= maxLines) {
                return
            }
            target += b to c
            if (target.size >= maxLines) {
                return
            }
            target += c to a
        }
    }

    fun attachCurrentSelectionToHotspot(group: GroupNode, id: String): Pair<Int, Int>? {
        if (group !== activeGroup) {
            return null
        }
        if (hotspotStoreFor(group).hotspotById(id) == null) {
            return null
        }
        val store = hotspotStoreFor(group)
        val hotspot = store.hotspotById(id) ?: return null
        refreshPrototypeVertexIds(group.prototype)
        val attachedHotspotIds = group.hotspotSelectionIds.filterNot { it == id }.toSet()
        val segmentRefs = group.lineStore.getSelected().map { segment -> store.segmentRef(segment) }.toSet()
        val triangleRefs = group.faceStore.getSelected().map { tri -> store.triangleRef(tri) }.toSet()
        val vertexIds = linkedSetOf<String>()
        group.lineStore.getSelected().forEach { segment ->
            vertexIdForPrototypePoint(group.prototype, segment.start)?.let { vertexIds.add(it) }
            vertexIdForPrototypePoint(group.prototype, segment.end)?.let { vertexIds.add(it) }
        }
        group.faceStore.getSelected().forEach { triangle ->
            vertexIdForPrototypePoint(group.prototype, triangle.a)?.let { vertexIds.add(it) }
            vertexIdForPrototypePoint(group.prototype, triangle.b)?.let { vertexIds.add(it) }
            vertexIdForPrototypePoint(group.prototype, triangle.c)?.let { vertexIds.add(it) }
        }
        if (vertexIds.isEmpty()) {
            // Fallback: if selection is empty and hotspot sits on a prototype vertex, bind to that vertex.
            vertexIdForPrototypePoint(group.prototype, hotspot.position)?.let { vertexIds.add(it) }
        }
        hotspot.attachedHotspotIds = attachedHotspotIds.toMutableSet()
        hotspot.attachedVertexIds = vertexIds
        hotspot.attachedSegments = segmentRefs.toMutableSet()
        hotspot.attachedTriangles = triangleRefs.toMutableSet()
        prototypeInstances[group.prototype.id].orEmpty().forEach { instance ->
            instance.runtimeHotspotPositions.clear()
            if (instance !== group) {
                instance.hotspotAttachedSegmentOverrides.remove(hotspot.id)
                instance.hotspotAttachedTriangleOverrides.remove(hotspot.id)
            }
        }
        store.notifyExternalChange()
        notifyChange()
        return segmentRefs.size to triangleRefs.size
    }

    fun selectHotspotAttachedGeometry(group: GroupNode, id: String, replace: Boolean = true): Pair<Int, Int>? {
        val store = hotspotStoreFor(group)
        val hotspot = store.hotspotById(id) ?: return null
        val attachedSegments = attachedSegmentsFor(group, hotspot)
        val attachedTriangles = attachedTrianglesFor(group, hotspot)
        if (replace) {
            group.lineStore.clearSelection()
            group.faceStore.clearSelection()
        }
        var lineCount = 0
        group.lineStore.getSegments().forEach { segment ->
            if (containsSegmentRef(store, attachedSegments, segment)) {
                if (group.lineStore.addSelection(segment)) {
                    lineCount++
                }
            }
        }
        var faceCount = 0
        group.faceStore.getTriangles().forEach { triangle ->
            if (containsTriangleRef(store, attachedTriangles, triangle)) {
                if (group.faceStore.addSelection(triangle)) {
                    faceCount++
                }
            }
        }
        return lineCount to faceCount
    }

    fun applyHotspotDrag(group: GroupNode, id: String, targetWorld: Vector3): Boolean {
        if (hotspotStoreFor(group).hotspotById(id) == null) {
            return false
        }
        val store = hotspotStoreFor(group)
        val hotspot = store.hotspotById(id) ?: return false
        val targetLocal = group.toLocal(targetWorld)
        if (group === activeGroup && group.editPrototypeMode) {
            hotspot.position.set(targetLocal)
            prototypeInstances[group.prototype.id].orEmpty().forEach { instance ->
                instance.runtimeHotspotPositions.clear()
            }
        } else if (targetLocal.epsilonEquals(hotspot.position, 1e-6f)) {
            // Keep instance state compact: equal-to-default values are not persisted as overrides.
            group.hotspotPositionOverrides.remove(hotspot.id)
            group.runtimeHotspotPositions.clear()
        } else {
            group.hotspotPositionOverrides[hotspot.id] = Vector3(targetLocal)
            group.runtimeHotspotPositions.clear()
        }
        store.notifyExternalChange()
        notifyChange()
        return true
    }

    fun selectedArchitectureBounds(group: GroupNode): BoundingBox? {
        val store = architectureStoreFor(group)
        val selected = store.selectedElements().toList()
        if (selected.isEmpty()) {
            return null
        }
        val bounds = BoundingBox()
        var hasAny = false
        fun ext(point: Vector3) {
            if (!hasAny) {
                bounds.set(point, point)
                hasAny = true
            } else {
                bounds.ext(point)
            }
        }

        selected.forEach { selection ->
            when (selection.kind) {
                ArchitectureStore.ElementKind.WALL -> {
                    val wall = store.wallById(selection.id) ?: return@forEach
                    val basis = wallBasis(wall) ?: return@forEach
                    val uMax = basis.length
                    val vMax = wall.height
                    listOf(0f, uMax).forEach { u ->
                        listOf(0f, vMax).forEach { v ->
                            ext(wallPoint(basis, wall, u, v, 0f, 0f))
                            ext(wallPoint(basis, wall, u, v, 1f, 0f))
                        }
                    }
                }
                ArchitectureStore.ElementKind.SLAB -> {
                    val slab = store.allSlabs().firstOrNull { it.id == selection.id } ?: return@forEach
                    slabCorners(slab).forEach { ext(it) }
                }
                ArchitectureStore.ElementKind.STAIR -> {
                    val stair = store.allStairs().firstOrNull { it.id == selection.id } ?: return@forEach
                    val minX = min(stair.min.x, stair.max.x)
                    val maxX = max(stair.min.x, stair.max.x)
                    val minZ = min(stair.min.z, stair.max.z)
                    val maxZ = max(stair.min.z, stair.max.z)
                    val baseY = min(stair.min.y, stair.max.y) - stair.supportThickness.coerceAtLeast(0.01f)
                    val maxY = min(stair.min.y, stair.max.y) + stair.height.coerceAtLeast(0.05f)
                    listOf(minX, maxX).forEach { x ->
                        listOf(baseY, maxY).forEach { y ->
                            listOf(minZ, maxZ).forEach { z ->
                                ext(Vector3(x, y, z))
                            }
                        }
                    }
                }
                ArchitectureStore.ElementKind.FRAME -> {
                    val frame = store.allFrames().firstOrNull { it.id == selection.id } ?: return@forEach
                    val basis = frameSelectionBasis(frame) ?: return@forEach
                    listOf(basis.uMin, basis.uMax).forEach { u ->
                        listOf(basis.vMin, basis.vMax).forEach { v ->
                            listOf(basis.nMin, basis.nMax).forEach { n ->
                                ext(
                                    Vector3(basis.origin)
                                        .mulAdd(basis.axisU, u)
                                        .mulAdd(basis.axisV, v)
                                        .mulAdd(basis.normal, n)
                                )
                            }
                        }
                    }
                }
            }
        }

        return if (hasAny) bounds else null
    }

    fun selectedHvacBounds(group: GroupNode): BoundingBox? {
        val store = hvacStoreFor(group)
        val selected = store.selectedElements().toList()
        if (selected.isEmpty()) {
            return null
        }
        val bounds = BoundingBox()
        var hasAny = false
        fun ext(point: Vector3) {
            if (!hasAny) {
                bounds.set(point, point)
                hasAny = true
            } else {
                bounds.ext(point)
            }
        }

        selected.forEach { selection ->
            when (selection.kind) {
                HvacStore.ElementKind.PLUMBING -> {
                    val run = store.plumbingById(selection.id) ?: return@forEach
                    val half = (run.diameter * 0.5f).coerceAtLeast(0.01f)
                    run.path.forEach { point ->
                        ext(Vector3(point.x - half, point.y - half, point.z - half))
                        ext(Vector3(point.x + half, point.y + half, point.z + half))
                    }
                }
                HvacStore.ElementKind.VENTILATION -> {
                    val duct = store.ventilationById(selection.id) ?: return@forEach
                    hvacVentilationCorners(duct).forEach { ext(it) }
                    ext(Vector3(duct.binormalRef))
                }
            }
        }

        return if (hasAny) bounds else null
    }

    fun clearArchitectureElementSelection(group: GroupNode): Boolean {
        val store = architectureStoreFor(group)
        val hadHole = if (group == root && selectedArchitectureHole != null) {
            selectedArchitectureHole = null
            true
        } else {
            false
        }
        val hadSlabHole = if (group == root && selectedArchitectureSlabHole != null) {
            selectedArchitectureSlabHole = null
            true
        } else {
            false
        }
        if (store.selectedElement() == null) {
            return hadHole || hadSlabHole
        }
        store.clearSelectedElement()
        return true
    }

    fun selectedArchitectureHole(group: GroupNode): ArchitectureHoleSelection? {
        if (group != root) {
            return null
        }
        val sel = selectedArchitectureHole ?: return null
        val wall = architectureStoreFor(group).wallById(sel.wallId) ?: run {
            selectedArchitectureHole = null
            return null
        }
        if (wall.holes.none { it.id == sel.holeId }) {
            selectedArchitectureHole = null
            return null
        }
        return sel
    }

    fun clearArchitectureHoleSelection(group: GroupNode): Boolean {
        if (group != root || selectedArchitectureHole == null) {
            return false
        }
        selectedArchitectureHole = null
        return true
    }

    fun selectedArchitectureSlabHole(group: GroupNode): ArchitectureSlabHoleSelection? {
        if (group != root) {
            return null
        }
        val sel = selectedArchitectureSlabHole ?: return null
        val slab = architectureStoreFor(group).allSlabs().firstOrNull { it.id == sel.slabId } ?: run {
            selectedArchitectureSlabHole = null
            return null
        }
        if (slab.holes.none { it.id == sel.holeId }) {
            selectedArchitectureSlabHole = null
            return null
        }
        return sel
    }

    fun clearArchitectureSlabHoleSelection(group: GroupNode): Boolean {
        if (group != root || selectedArchitectureSlabHole == null) {
            return false
        }
        selectedArchitectureSlabHole = null
        return true
    }

    fun selectArchitectureHole(
        group: GroupNode,
        wallId: String,
        holeId: String,
        mode: ArchitectureSelectionMode = ArchitectureSelectionMode.REPLACE
    ): Boolean {
        if (group != root) {
            return false
        }
        val store = architectureStoreFor(group)
        val wall = store.wallById(wallId) ?: return false
        if (wall.holes.none { it.id == holeId }) {
            return false
        }
        val sel = ArchitectureHoleSelection(wallId, holeId)
        return when (mode) {
            ArchitectureSelectionMode.REPLACE, ArchitectureSelectionMode.ADD -> {
                val changed = selectedArchitectureHole != sel
                selectedArchitectureHole = sel
                if (mode == ArchitectureSelectionMode.REPLACE) {
                    selectedArchitectureSlabHole = null
                }
                changed || mode == ArchitectureSelectionMode.REPLACE
            }
            ArchitectureSelectionMode.REMOVE -> {
                val removed = selectedArchitectureHole == sel
                if (removed) {
                    selectedArchitectureHole = null
                }
                removed
            }
        }
    }

    fun selectArchitectureSlabHole(
        group: GroupNode,
        slabId: String,
        holeId: String,
        mode: ArchitectureSelectionMode = ArchitectureSelectionMode.REPLACE
    ): Boolean {
        if (group != root) {
            return false
        }
        val store = architectureStoreFor(group)
        val slab = store.allSlabs().firstOrNull { it.id == slabId } ?: return false
        if (slab.holes.none { it.id == holeId }) {
            return false
        }
        val sel = ArchitectureSlabHoleSelection(slabId, holeId)
        return when (mode) {
            ArchitectureSelectionMode.REPLACE, ArchitectureSelectionMode.ADD -> {
                val changed = selectedArchitectureSlabHole != sel
                selectedArchitectureSlabHole = sel
                if (mode == ArchitectureSelectionMode.REPLACE) {
                    selectedArchitectureHole = null
                }
                changed || mode == ArchitectureSelectionMode.REPLACE
            }
            ArchitectureSelectionMode.REMOVE -> {
                val removed = selectedArchitectureSlabHole == sel
                if (removed) {
                    selectedArchitectureSlabHole = null
                }
                removed
            }
        }
    }

    fun deleteSelectedArchitectureElements(group: GroupNode): Int {
        val store = architectureStoreFor(group)
        val removed = store.deleteSelectedElements()
        if (removed <= 0) {
            return 0
        }
        selectedArchitectureHole(group)
        selectedArchitectureSlabHole(group)
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return removed
    }

    fun deleteSelectedArchitectureElement(group: GroupNode): Boolean {
        return deleteSelectedArchitectureElements(group) > 0
    }

    fun selectArchitectureElement(
        group: GroupNode,
        kind: ArchitectureStore.ElementKind,
        id: String,
        mode: ArchitectureSelectionMode = ArchitectureSelectionMode.REPLACE
    ): Boolean {
        val store = architectureStoreFor(group)
        val changed = when (mode) {
            ArchitectureSelectionMode.REPLACE -> store.setSelectedElement(kind, id)
            ArchitectureSelectionMode.ADD -> store.addSelectedElement(kind, id)
            ArchitectureSelectionMode.REMOVE -> store.removeSelectedElement(kind, id)
        }
        if (group == root && mode == ArchitectureSelectionMode.REPLACE) {
            selectedArchitectureHole = null
            selectedArchitectureSlabHole = null
        }
        return changed
    }

    fun selectArchitectureElementNearWorldPoint(
        group: GroupNode,
        worldPoint: Vector3,
        mode: ArchitectureSelectionMode = ArchitectureSelectionMode.REPLACE
    ): ArchitectureStore.ElementSelection? {
        val store = architectureStoreFor(group)
        val point = Vector3(worldPoint)
        var bestSelection: ArchitectureStore.ElementSelection? = null
        var bestDist2 = Float.POSITIVE_INFINITY
        fun priority(kind: ArchitectureStore.ElementKind): Int = when (kind) {
            ArchitectureStore.ElementKind.FRAME -> 0
            ArchitectureStore.ElementKind.STAIR -> 1
            ArchitectureStore.ElementKind.SLAB -> 2
            ArchitectureStore.ElementKind.WALL -> 3
        }
        fun tryCandidate(kind: ArchitectureStore.ElementKind, id: String, dist2: Float) {
            if (!dist2.isFinite()) {
                return
            }
            val current = bestSelection
            if (current == null) {
                bestDist2 = dist2
                bestSelection = ArchitectureStore.ElementSelection(kind, id)
                return
            }
            val delta = dist2 - bestDist2
            if (delta < -1e-6f || (abs(delta) <= 1e-6f && priority(kind) < priority(current.kind))) {
                bestDist2 = dist2
                bestSelection = ArchitectureStore.ElementSelection(kind, id)
            }
        }

        store.allFrames().forEach { frame ->
            tryCandidate(ArchitectureStore.ElementKind.FRAME, frame.id, architectureFrameDistanceSq(frame, point))
        }
        store.allStairs().forEach { stair ->
            tryCandidate(ArchitectureStore.ElementKind.STAIR, stair.id, architectureStairDistanceSq(stair, point))
        }
        store.allSlabs().forEach { slab ->
            tryCandidate(ArchitectureStore.ElementKind.SLAB, slab.id, architectureSlabDistanceSq(slab, point))
        }
        store.allWalls().forEach { wall ->
            tryCandidate(ArchitectureStore.ElementKind.WALL, wall.id, architectureWallDistanceSq(wall, point))
        }

        val selection = bestSelection
        if (selection == null) {
            if (mode == ArchitectureSelectionMode.REPLACE) {
                store.clearSelectedElement()
            }
            return null
        }
        when (mode) {
            ArchitectureSelectionMode.REPLACE -> store.setSelectedElement(selection.kind, selection.id)
            ArchitectureSelectionMode.ADD -> store.addSelectedElement(selection.kind, selection.id)
            ArchitectureSelectionMode.REMOVE -> store.removeSelectedElement(selection.kind, selection.id)
        }
        return store.selectedElement()
    }

    fun clearHvacElementSelection(group: GroupNode): Boolean {
        val store = hvacStoreFor(group)
        if (store.selectedElement() == null) {
            return false
        }
        store.clearSelectedElement()
        return true
    }

    fun deleteSelectedHvacElements(group: GroupNode): Int {
        val store = hvacStoreFor(group)
        val removed = store.deleteSelectedElements()
        if (removed <= 0) {
            return 0
        }
        rebuildHvacGeometry(rootPrototype)
        notifyChange()
        return removed
    }

    fun selectHvacElement(
        group: GroupNode,
        kind: HvacStore.ElementKind,
        id: String,
        mode: HvacSelectionMode = HvacSelectionMode.REPLACE
    ): Boolean {
        val store = hvacStoreFor(group)
        return when (mode) {
            HvacSelectionMode.REPLACE -> store.setSelectedElement(kind, id)
            HvacSelectionMode.ADD -> store.addSelectedElement(kind, id)
            HvacSelectionMode.REMOVE -> store.removeSelectedElement(kind, id)
        }
    }

    fun selectHvacElementNearWorldPoint(
        group: GroupNode,
        worldPoint: Vector3,
        mode: HvacSelectionMode = HvacSelectionMode.REPLACE
    ): HvacStore.ElementSelection? {
        val store = hvacStoreFor(group)
        val point = Vector3(worldPoint)
        var bestSelection: HvacStore.ElementSelection? = null
        var bestDist2 = Float.POSITIVE_INFINITY
        fun priority(kind: HvacStore.ElementKind): Int = when (kind) {
            HvacStore.ElementKind.VENTILATION -> 0
            HvacStore.ElementKind.PLUMBING -> 1
        }
        fun tryCandidate(kind: HvacStore.ElementKind, id: String, dist2: Float) {
            if (!dist2.isFinite()) {
                return
            }
            val current = bestSelection
            if (current == null) {
                bestDist2 = dist2
                bestSelection = HvacStore.ElementSelection(kind, id)
                return
            }
            val delta = dist2 - bestDist2
            if (delta < -1e-6f || (abs(delta) <= 1e-6f && priority(kind) < priority(current.kind))) {
                bestDist2 = dist2
                bestSelection = HvacStore.ElementSelection(kind, id)
            }
        }

        store.allVentilationDucts().forEach { duct ->
            tryCandidate(HvacStore.ElementKind.VENTILATION, duct.id, hvacVentilationDistanceSq(duct, point))
        }
        store.allPlumbingRuns().forEach { run ->
            tryCandidate(HvacStore.ElementKind.PLUMBING, run.id, hvacPlumbingDistanceSq(run, point))
        }

        val selection = bestSelection
        if (selection == null) {
            if (mode == HvacSelectionMode.REPLACE) {
                store.clearSelectedElement()
            }
            return null
        }
        when (mode) {
            HvacSelectionMode.REPLACE -> store.setSelectedElement(selection.kind, selection.id)
            HvacSelectionMode.ADD -> store.addSelectedElement(selection.kind, selection.id)
            HvacSelectionMode.REMOVE -> store.removeSelectedElement(selection.kind, selection.id)
        }
        return store.selectedElement()
    }

    fun transformSelectedArchitectureElements(
        group: GroupNode,
        pointTransform: (Vector3) -> Vector3,
        vectorTransform: (Vector3) -> Vector3 = { Vector3(it) }
    ): Int {
        val store = architectureStoreFor(group)
        val selected = store.selectedElements().toList()
        if (selected.isEmpty()) {
            return 0
        }
        var changed = 0
        selected.forEach { selection ->
            when (selection.kind) {
                ArchitectureStore.ElementKind.WALL -> {
                    val wall = store.wallById(selection.id) ?: return@forEach
                    wall.start.set(pointTransform(Vector3(wall.start)))
                    wall.end.set(pointTransform(Vector3(wall.end)))
                    changed++
                }
                ArchitectureStore.ElementKind.SLAB -> {
                    val slab = store.allSlabs().firstOrNull { it.id == selection.id } ?: return@forEach
                    val a = pointTransform(Vector3(slab.min))
                    val b = pointTransform(Vector3(slab.max))
                    store.updateSlabCorners(slab.id, a, b)
                    val nextAxisU = vectorTransform(Vector3(slab.axisU))
                    if (nextAxisU.len2() > 1e-6f) {
                        slab.axisU.set(nextAxisU.nor())
                    }
                    val nextAxisV = vectorTransform(Vector3(slab.axisV))
                    if (nextAxisV.len2() > 1e-6f) {
                        slab.axisV.set(nextAxisV.nor())
                    }
                    val nextNormal = vectorTransform(Vector3(slab.normal))
                    if (nextNormal.len2() > 1e-6f) {
                        slab.normal.set(nextNormal.nor())
                    }
                    changed++
                }
                ArchitectureStore.ElementKind.STAIR -> {
                    val stair = store.allStairs().firstOrNull { it.id == selection.id } ?: return@forEach
                    stair.min.set(pointTransform(Vector3(stair.min)))
                    stair.max.set(pointTransform(Vector3(stair.max)))
                    stair.contour = stair.contour.map { pointTransform(Vector3(it)) }.toMutableList()
                    stair.walkingPath = stair.walkingPath.map { pointTransform(Vector3(it)) }.toMutableList()
                    stair.walkingStart.set(pointTransform(Vector3(stair.walkingStart)))
                    stair.walkingEnd.set(pointTransform(Vector3(stair.walkingEnd)))
                    changed++
                }
                ArchitectureStore.ElementKind.FRAME -> {
                    val frame = store.allFrames().firstOrNull { it.id == selection.id } ?: return@forEach
                    frame.cornerA.set(pointTransform(Vector3(frame.cornerA)))
                    frame.cornerB.set(pointTransform(Vector3(frame.cornerB)))
                    if (frame.contour.isNotEmpty()) {
                        frame.contour = frame.contour
                            .map { pointTransform(Vector3(it)) }
                            .toMutableList()
                    }
                    val newNormal = vectorTransform(Vector3(frame.normal))
                    if (newNormal.len2() > 1e-6f) {
                        frame.normal.set(newNormal.nor())
                    }
                    changed++
                }
            }
        }
        if (changed > 0) {
            rebuildArchitectureGeometry(rootPrototype)
            notifyChange()
        }
        return changed
    }

    fun transformSelectedHvacElements(
        group: GroupNode,
        pointTransform: (Vector3) -> Vector3
    ): Int {
        val store = hvacStoreFor(group)
        val selected = store.selectedElements().toList()
        if (selected.isEmpty()) {
            return 0
        }
        var changed = 0
        selected.forEach { selection ->
            when (selection.kind) {
                HvacStore.ElementKind.PLUMBING -> {
                    val run = store.plumbingById(selection.id) ?: return@forEach
                    run.path = run.path.map { pointTransform(Vector3(it)) }.toMutableList()
                    changed++
                }
                HvacStore.ElementKind.VENTILATION -> {
                    val duct = store.ventilationById(selection.id) ?: return@forEach
                    duct.start.set(pointTransform(Vector3(duct.start)))
                    duct.end.set(pointTransform(Vector3(duct.end)))
                    duct.binormalRef.set(pointTransform(Vector3(duct.binormalRef)))
                    changed++
                }
            }
        }
        if (changed > 0) {
            rebuildHvacGeometry(rootPrototype)
            notifyChange()
        }
        return changed
    }

    fun copySelectedArchitectureElements(
        group: GroupNode,
        pointTransform: (Vector3) -> Vector3,
        vectorTransform: (Vector3) -> Vector3 = { Vector3(it) }
    ): Int {
        val store = architectureStoreFor(group)
        val selected = store.selectedElements().toList()
        if (selected.isEmpty()) {
            return 0
        }

        val copiedSelections = mutableListOf<ArchitectureStore.ElementSelection>()
        selected.forEach { selection ->
            when (selection.kind) {
                ArchitectureStore.ElementKind.WALL -> {
                    val wall = store.wallById(selection.id) ?: return@forEach
                    val copiedWall = store.addWall(
                        start = pointTransform(Vector3(wall.start)),
                        end = pointTransform(Vector3(wall.end)),
                        thickness = wall.thickness,
                        height = wall.height,
                        inclinationDeg = wall.inclinationDeg,
                        exteriorColor = Color(wall.exteriorColor),
                        interiorColor = Color(wall.interiorColor)
                    )
                    wall.holes.forEach { hole ->
                        store.addHole(
                            wallId = copiedWall.id,
                            u0 = hole.u0,
                            u1 = hole.u1,
                            v0 = hole.v0,
                            v1 = hole.v1,
                            minSize = 0f
                        )
                    }
                    copiedSelections.add(
                        ArchitectureStore.ElementSelection(
                            ArchitectureStore.ElementKind.WALL,
                            copiedWall.id
                        )
                    )
                }
                ArchitectureStore.ElementKind.SLAB -> {
                    val slab = store.allSlabs().firstOrNull { it.id == selection.id } ?: return@forEach
                    val copiedSlab = store.addSlab(
                        minCorner = pointTransform(Vector3(slab.min)),
                        maxCorner = pointTransform(Vector3(slab.max)),
                        thickness = slab.thickness,
                        topColor = Color(slab.topColor),
                        bottomColor = Color(slab.bottomColor),
                        sideColor = Color(slab.sideColor)
                    )
                    val nextAxisU = vectorTransform(Vector3(slab.axisU))
                    if (nextAxisU.len2() > 1e-6f) {
                        copiedSlab.axisU.set(nextAxisU.nor())
                    }
                    val nextAxisV = vectorTransform(Vector3(slab.axisV))
                    if (nextAxisV.len2() > 1e-6f) {
                        copiedSlab.axisV.set(nextAxisV.nor())
                    }
                    val nextNormal = vectorTransform(Vector3(slab.normal))
                    if (nextNormal.len2() > 1e-6f) {
                        copiedSlab.normal.set(nextNormal.nor())
                    }
                    slab.holes.forEach { hole ->
                        store.addSlabHole(
                            slabId = copiedSlab.id,
                            u0 = hole.u0,
                            u1 = hole.u1,
                            v0 = hole.v0,
                            v1 = hole.v1,
                            minSize = 0f
                        )
                    }
                    copiedSelections.add(
                        ArchitectureStore.ElementSelection(
                            ArchitectureStore.ElementKind.SLAB,
                            copiedSlab.id
                        )
                    )
                }
                ArchitectureStore.ElementKind.STAIR -> {
                    val stair = store.allStairs().firstOrNull { it.id == selection.id } ?: return@forEach
                    val copiedStair = store.addStair(
                        minCorner = pointTransform(Vector3(stair.min)),
                        maxCorner = pointTransform(Vector3(stair.max)),
                        contourPoints = stair.contour.map { pointTransform(Vector3(it)) },
                        walkingPathPoints = stair.walkingPath.map { pointTransform(Vector3(it)) },
                        walkingStart = pointTransform(Vector3(stair.walkingStart)),
                        walkingEnd = pointTransform(Vector3(stair.walkingEnd)),
                        height = stair.height,
                        stepCount = stair.stepCount,
                        supportThickness = stair.supportThickness,
                        railLeftEnabled = stair.railLeftEnabled,
                        railRightEnabled = stair.railRightEnabled,
                        treadColor = Color(stair.treadColor),
                        supportColor = Color(stair.supportColor)
                    )
                    copiedSelections.add(
                        ArchitectureStore.ElementSelection(
                            ArchitectureStore.ElementKind.STAIR,
                            copiedStair.id
                        )
                    )
                }
                ArchitectureStore.ElementKind.FRAME -> {
                    val frame = store.allFrames().firstOrNull { it.id == selection.id } ?: return@forEach
                    val normal = vectorTransform(Vector3(frame.normal))
                    val copiedFrame = store.addFrame(
                        cornerA = pointTransform(Vector3(frame.cornerA)),
                        cornerB = pointTransform(Vector3(frame.cornerB)),
                        contourPoints = frame.contour.map { pointTransform(Vector3(it)) },
                        normal = if (normal.len2() <= 1e-6f) Vector3(frame.normal) else normal.nor(),
                        depth = frame.depth,
                        frameWidth = frame.frameWidth,
                        kind = frame.kind,
                        color = Color(frame.color),
                        glazingEnabled = frame.glazingEnabled,
                        glazingColor = Color(frame.glazingColor)
                    )
                    copiedSelections.add(
                        ArchitectureStore.ElementSelection(
                            ArchitectureStore.ElementKind.FRAME,
                            copiedFrame.id
                        )
                    )
                }
            }
        }

        if (copiedSelections.isNotEmpty()) {
            store.clearSelectedElement()
            copiedSelections.forEach { copied ->
                store.addSelectedElement(copied.kind, copied.id)
            }
            rebuildArchitectureGeometry(rootPrototype)
            notifyChange()
        }

        return copiedSelections.size
    }

    fun copySelectedHvacElements(
        group: GroupNode,
        pointTransform: (Vector3) -> Vector3
    ): Int {
        val store = hvacStoreFor(group)
        val selected = store.selectedElements().toList()
        if (selected.isEmpty()) {
            return 0
        }

        val copiedSelections = mutableListOf<HvacStore.ElementSelection>()
        selected.forEach { selection ->
            when (selection.kind) {
                HvacStore.ElementKind.PLUMBING -> {
                    val run = store.plumbingById(selection.id) ?: return@forEach
                    val copy = store.addPlumbingRun(
                        path = run.path.map { pointTransform(Vector3(it)) },
                        diameter = run.diameter,
                        sides = run.sides,
                        color = Color(run.color)
                    )
                    copiedSelections.add(HvacStore.ElementSelection(HvacStore.ElementKind.PLUMBING, copy.id))
                }
                HvacStore.ElementKind.VENTILATION -> {
                    val duct = store.ventilationById(selection.id) ?: return@forEach
                    val copy = store.addVentilationDuct(
                        start = pointTransform(Vector3(duct.start)),
                        end = pointTransform(Vector3(duct.end)),
                        binormalRef = pointTransform(Vector3(duct.binormalRef)),
                        autoJoinEnabled = duct.autoJoinEnabled,
                        width = duct.width,
                        height = duct.height,
                        humpHalfSpan = duct.humpHalfSpan,
                        humpClearance = duct.humpClearance,
                        color = Color(duct.color)
                    )
                    copiedSelections.add(HvacStore.ElementSelection(HvacStore.ElementKind.VENTILATION, copy.id))
                }
            }
        }

        if (copiedSelections.isNotEmpty()) {
            store.clearSelectedElement()
            copiedSelections.forEach { copied ->
                store.addSelectedElement(copied.kind, copied.id)
            }
            rebuildHvacGeometry(rootPrototype)
            notifyChange()
        }
        return copiedSelections.size
    }

    fun updateArchitectureWallEndpoints(
        group: GroupNode,
        id: String,
        start: Vector3,
        end: Vector3
    ): Boolean {
        val store = architectureStoreFor(group)
        val startWorld = group.toWorld(start)
        val endWorld = group.toWorld(end)
        if (startWorld.dst2(endWorld) <= 1e-6f) {
            return false
        }
        if (!store.updateWallEndpoints(id, startWorld, endWorld)) {
            return false
        }
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun addArchitectureHoleToWall(
        group: GroupNode,
        wallId: String,
        cornerA: Vector3,
        cornerB: Vector3
    ): Boolean {
        val store = architectureStoreFor(group)
        val cornerAWorld = group.toWorld(cornerA)
        val cornerBWorld = group.toWorld(cornerB)
        val wall = store.wallById(wallId) ?: return false
        val candidate = wallHoleCandidate(wall, cornerAWorld, cornerBWorld) ?: return false
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
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun addArchitectureHoleToSlab(
        group: GroupNode,
        slabId: String,
        cornerA: Vector3,
        cornerB: Vector3
    ): Boolean {
        val store = architectureStoreFor(group)
        val cornerAWorld = group.toWorld(cornerA)
        val cornerBWorld = group.toWorld(cornerB)
        val slab = store.allSlabs().firstOrNull { it.id == slabId } ?: return false
        val candidate = slabHoleCandidate(slab, cornerAWorld, cornerBWorld) ?: return false
        val added = store.addSlabHole(
            slabId = slab.id,
            u0 = candidate.u0,
            u1 = candidate.u1,
            v0 = candidate.v0,
            v1 = candidate.v1
        ) ?: return false
        if (added.u1 - added.u0 <= 1e-4f || added.v1 - added.v0 <= 1e-4f) {
            return false
        }
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun addArchitectureHoleToNearestWall(
        group: GroupNode,
        cornerA: Vector3,
        cornerB: Vector3
    ): Boolean {
        val store = architectureStoreFor(group)
        val cornerAWorld = group.toWorld(cornerA)
        val cornerBWorld = group.toWorld(cornerB)
        if (store.allWalls().isEmpty()) {
            return false
        }
        val candidate = findNearestWallHoleCandidate(store, cornerAWorld, cornerBWorld) ?: return false
        return addArchitectureHoleToWall(group, candidate.wall.id, cornerA, cornerB)
    }

    fun addArchitectureHoleToNearestSlab(
        group: GroupNode,
        cornerA: Vector3,
        cornerB: Vector3
    ): Boolean {
        val store = architectureStoreFor(group)
        val cornerAWorld = group.toWorld(cornerA)
        val cornerBWorld = group.toWorld(cornerB)
        if (store.allSlabs().isEmpty()) {
            return false
        }
        val candidate = findNearestSlabHoleCandidate(store, cornerAWorld, cornerBWorld) ?: return false
        return addArchitectureHoleToSlab(group, candidate.slab.id, cornerA, cornerB)
    }

    fun architectureHoleGuideSegmentsWorld(
        group: GroupNode,
        includeDiagonals: Boolean = true,
        wallId: String? = null
    ): List<Pair<Vector3, Vector3>> {
        val store = architectureStoreFor(group)
        val guides = mutableListOf<Pair<Vector3, Vector3>>()
        store.allWalls().forEach { wall ->
            if (wallId != null && wall.id != wallId) {
                return@forEach
            }
            wall.holes.forEach { hole ->
                guides.addAll(holeContourSegments(wall, hole, includeDiagonals = includeDiagonals))
            }
        }
        return guides
    }

    fun architectureSlabHoleGuideSegmentsWorld(
        group: GroupNode,
        includeDiagonals: Boolean = true,
        slabId: String? = null
    ): List<Pair<Vector3, Vector3>> {
        val store = architectureStoreFor(group)
        val guides = mutableListOf<Pair<Vector3, Vector3>>()
        store.allSlabs().forEach { slab ->
            if (slabId != null && slab.id != slabId) {
                return@forEach
            }
            slab.holes.forEach { hole ->
                guides.addAll(slabHoleContourSegments(slab, hole, includeDiagonals = includeDiagonals))
            }
        }
        return guides
    }

    fun architectureHoleHandleMarkersWorld(
        group: GroupNode,
        wallId: String? = null
    ): List<HoleHandleMarker> {
        val store = architectureStoreFor(group)
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
                // Minimal hole editing handles: top-left and bottom-right only.
                // In wall-local UV, top-left is (u0,v1)=CORNER_3 and bottom-right is (u1,v0)=CORNER_1.
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.CORNER_3, p3))
                handles.add(HoleHandleMarker(wall.id, hole.id, HoleHandleKind.CORNER_1, p1))
            }
        }
        return handles
    }

    fun architectureSlabHoleHandleMarkersWorld(
        group: GroupNode,
        slabId: String? = null
    ): List<SlabHoleHandleMarker> {
        val store = architectureStoreFor(group)
        val handles = mutableListOf<SlabHoleHandleMarker>()
        store.allSlabs().forEach { slab ->
            if (slabId != null && slab.id != slabId) {
                return@forEach
            }
            val basis = slabBasis(slab) ?: return@forEach
            slab.holes.forEach { hole ->
                val p1 = slabPoint(basis, hole.u1, hole.v0, basis.thickness + 0.002f)
                val p3 = slabPoint(basis, hole.u0, hole.v1, basis.thickness + 0.002f)
                handles.add(SlabHoleHandleMarker(slab.id, hole.id, HoleHandleKind.CORNER_3, p3))
                handles.add(SlabHoleHandleMarker(slab.id, hole.id, HoleHandleKind.CORNER_1, p1))
            }
        }
        return handles
    }

    fun architectureHoleConstructionHotspotsWorld(
        group: GroupNode,
        wallId: String? = null
    ): List<ArchitectureElementHotspotMarker> {
        val store = architectureStoreFor(group)
        val markers = mutableListOf<ArchitectureElementHotspotMarker>()
        store.allWalls().forEach { wall ->
            if (wallId != null && wall.id != wallId) {
                return@forEach
            }
            val basis = wallBasis(wall) ?: return@forEach
            wall.holes.forEach { hole ->
                val p0 = wallPoint(basis, wall, hole.u0, hole.v0, 1f, 0.002f)
                val p2 = wallPoint(basis, wall, hole.u1, hole.v1, 1f, 0.002f)
                markers.add(
                    ArchitectureElementHotspotMarker(
                        kind = ArchitectureStore.ElementKind.WALL,
                        id = wall.id,
                        world = p0
                    )
                )
                markers.add(
                    ArchitectureElementHotspotMarker(
                        kind = ArchitectureStore.ElementKind.WALL,
                        id = wall.id,
                        world = p2
                    )
                )
            }
        }
        return markers
    }

    fun architectureSlabConstructionHotspotsWorld(
        group: GroupNode,
        slabId: String? = null
    ): List<ArchitectureElementHotspotMarker> {
        val store = architectureStoreFor(group)
        val markers = mutableListOf<ArchitectureElementHotspotMarker>()
        store.allSlabs().forEach { slab ->
            if (slabId != null && slab.id != slabId) {
                return@forEach
            }
            markers.add(
                ArchitectureElementHotspotMarker(
                    kind = ArchitectureStore.ElementKind.SLAB,
                    id = slab.id,
                    world = Vector3(slab.min)
                )
            )
            markers.add(
                ArchitectureElementHotspotMarker(
                    kind = ArchitectureStore.ElementKind.SLAB,
                    id = slab.id,
                    world = Vector3(slab.max)
                )
            )
        }
        return markers
    }

    fun architectureFrameConstructionHotspotsWorld(
        group: GroupNode,
        frameId: String? = null
    ): List<ArchitectureElementHotspotMarker> {
        val store = architectureStoreFor(group)
        val markers = mutableListOf<ArchitectureElementHotspotMarker>()
        store.allFrames().forEach { frame ->
            if (frameId != null && frame.id != frameId) {
                return@forEach
            }
            markers.add(
                ArchitectureElementHotspotMarker(
                    kind = ArchitectureStore.ElementKind.FRAME,
                    id = frame.id,
                    world = Vector3(frame.cornerA)
                )
            )
            markers.add(
                ArchitectureElementHotspotMarker(
                    kind = ArchitectureStore.ElementKind.FRAME,
                    id = frame.id,
                    world = Vector3(frame.cornerB)
                )
            )
        }
        return markers
    }

    fun architectureSlabEndpointHandleMarkersWorld(
        group: GroupNode,
        slabId: String? = null
    ): List<ArchitectureEndpointHandleMarker> {
        val store = architectureStoreFor(group)
        val selectedIds = store.selectedElements()
            .filter { it.kind == ArchitectureStore.ElementKind.SLAB }
            .map { it.id }
            .toSet()
        val targets = store.allSlabs().filter { slab ->
            when {
                slabId != null -> slab.id == slabId
                selectedIds.isNotEmpty() -> selectedIds.contains(slab.id)
                else -> false
            }
        }
        return targets.flatMap { slab ->
            val halfSize = max(0.2f, slab.thickness * 0.65f)
            listOf(
                ArchitectureEndpointHandleMarker(
                    kind = ArchitectureStore.ElementKind.SLAB,
                    id = slab.id,
                    draggingStart = true,
                    center = Vector3(slab.min),
                    halfSize = halfSize
                ),
                ArchitectureEndpointHandleMarker(
                    kind = ArchitectureStore.ElementKind.SLAB,
                    id = slab.id,
                    draggingStart = false,
                    center = Vector3(slab.max),
                    halfSize = halfSize
                )
            )
        }
    }

    fun architectureFrameEndpointHandleMarkersWorld(
        group: GroupNode,
        frameId: String? = null
    ): List<ArchitectureEndpointHandleMarker> {
        val store = architectureStoreFor(group)
        val selectedIds = store.selectedElements()
            .filter { it.kind == ArchitectureStore.ElementKind.FRAME }
            .map { it.id }
            .toSet()
        val targets = store.allFrames().filter { frame ->
            when {
                frameId != null -> frame.id == frameId
                selectedIds.isNotEmpty() -> selectedIds.contains(frame.id)
                else -> false
            }
        }
        return targets.flatMap { frame ->
            val halfSize = max(0.2f, max(frame.frameWidth, frame.depth) * 0.75f)
            listOf(
                ArchitectureEndpointHandleMarker(
                    kind = ArchitectureStore.ElementKind.FRAME,
                    id = frame.id,
                    draggingStart = true,
                    center = Vector3(frame.cornerA),
                    halfSize = halfSize
                ),
                ArchitectureEndpointHandleMarker(
                    kind = ArchitectureStore.ElementKind.FRAME,
                    id = frame.id,
                    draggingStart = false,
                    center = Vector3(frame.cornerB),
                    halfSize = halfSize
                )
            )
        }
    }

    fun architectureWallEndpointHandleMarkersWorld(
        group: GroupNode,
        wallId: String? = null
    ): List<WallEndpointHandleMarker> {
        val store = architectureStoreFor(group)
        val selectedIds = store.selectedElements()
            .filter { it.kind == ArchitectureStore.ElementKind.WALL }
            .map { it.id }
            .toSet()
        val targets = store.allWalls().filter { wall ->
            when {
                wallId != null -> wall.id == wallId
                selectedIds.isNotEmpty() -> selectedIds.contains(wall.id)
                else -> false
            }
        }
        return targets.flatMap { wall ->
            val startWorld = Vector3(wall.start)
            val endWorld = Vector3(wall.end)
            val halfSize = max(0.2f, wall.thickness * 0.65f)
            listOf(
                WallEndpointHandleMarker(
                    wallId = wall.id,
                    draggingStart = true,
                    center = Vector3(startWorld),
                    halfSize = halfSize
                ),
                WallEndpointHandleMarker(
                    wallId = wall.id,
                    draggingStart = false,
                    center = Vector3(endWorld),
                    halfSize = halfSize
                )
            )
        }
    }

    fun hvacConstructionHotspotsWorld(
        group: GroupNode,
        id: String? = null
    ): List<HvacElementHotspotMarker> {
        val store = hvacStoreFor(group)
        val markers = mutableListOf<HvacElementHotspotMarker>()
        store.allPlumbingRuns().forEach { run ->
            if (id != null && run.id != id) {
                return@forEach
            }
            if (run.path.isEmpty()) {
                return@forEach
            }
            markers.add(
                HvacElementHotspotMarker(
                    kind = HvacStore.ElementKind.PLUMBING,
                    id = run.id,
                    world = Vector3(run.path.first())
                )
            )
            markers.add(
                HvacElementHotspotMarker(
                    kind = HvacStore.ElementKind.PLUMBING,
                    id = run.id,
                    world = Vector3(run.path.last())
                )
            )
        }
        store.allVentilationDucts().forEach { duct ->
            if (id != null && duct.id != id) {
                return@forEach
            }
            val resolved = resolvedVentilation(duct)
            markers.add(
                HvacElementHotspotMarker(
                    kind = HvacStore.ElementKind.VENTILATION,
                    id = duct.id,
                    world = Vector3(resolved.start)
                )
            )
            markers.add(
                HvacElementHotspotMarker(
                    kind = HvacStore.ElementKind.VENTILATION,
                    id = duct.id,
                    world = Vector3(resolved.end)
                )
            )
            markers.add(
                HvacElementHotspotMarker(
                    kind = HvacStore.ElementKind.VENTILATION,
                    id = duct.id,
                    world = Vector3(resolved.binormalRef)
                )
            )
        }
        return markers
    }

    fun hvacControlHandleMarkersWorld(
        group: GroupNode,
        id: String? = null
    ): List<HvacControlHandleMarker> {
        val store = hvacStoreFor(group)
        val selectedPlumbing = store.selectedElements()
            .filter { it.kind == HvacStore.ElementKind.PLUMBING }
            .map { it.id }
            .toSet()
        val selectedVentilation = store.selectedElements()
            .filter { it.kind == HvacStore.ElementKind.VENTILATION }
            .map { it.id }
            .toSet()
        val markers = mutableListOf<HvacControlHandleMarker>()
        store.allPlumbingRuns().forEach { run ->
            val include = when {
                id != null -> run.id == id
                selectedPlumbing.isNotEmpty() -> selectedPlumbing.contains(run.id)
                else -> false
            }
            if (!include) {
                return@forEach
            }
            val halfSize = max(0.14f, run.diameter * 0.45f)
            run.path.forEachIndexed { index, point ->
                markers.add(
                    HvacControlHandleMarker(
                        kind = HvacStore.ElementKind.PLUMBING,
                        id = run.id,
                        pointIndex = index,
                        ventilationControlKind = null,
                        center = Vector3(point),
                        halfSize = halfSize
                    )
                )
            }
        }
        store.allVentilationDucts().forEach { duct ->
            val include = when {
                id != null -> duct.id == id
                selectedVentilation.isNotEmpty() -> selectedVentilation.contains(duct.id)
                else -> false
            }
            if (!include) {
                return@forEach
            }
            val resolved = resolvedVentilation(duct)
            val halfSize = max(0.14f, max(resolved.width, resolved.height) * 0.45f)
            markers.add(
                HvacControlHandleMarker(
                    kind = HvacStore.ElementKind.VENTILATION,
                    id = duct.id,
                    pointIndex = null,
                    ventilationControlKind = HvacStore.VentilationControlKind.START,
                    center = Vector3(resolved.start),
                    halfSize = halfSize
                )
            )
            markers.add(
                HvacControlHandleMarker(
                    kind = HvacStore.ElementKind.VENTILATION,
                    id = duct.id,
                    pointIndex = null,
                    ventilationControlKind = HvacStore.VentilationControlKind.END,
                    center = Vector3(resolved.end),
                    halfSize = halfSize
                )
            )
            markers.add(
                HvacControlHandleMarker(
                    kind = HvacStore.ElementKind.VENTILATION,
                    id = duct.id,
                    pointIndex = null,
                    ventilationControlKind = HvacStore.VentilationControlKind.BINORMAL_REF,
                    center = Vector3(resolved.binormalRef),
                    halfSize = halfSize
                )
            )
        }
        return markers
    }

    fun updateHvacControlPoint(
        group: GroupNode,
        marker: HvacControlHandleMarker,
        targetWorld: Vector3
    ): Boolean {
        val store = hvacStoreFor(group)
        val updated = when (marker.kind) {
            HvacStore.ElementKind.PLUMBING -> {
                val pointIndex = marker.pointIndex ?: return false
                store.updatePlumbingPathPoint(marker.id, pointIndex, targetWorld)
            }
            HvacStore.ElementKind.VENTILATION -> {
                val controlKind = marker.ventilationControlKind ?: return false
                store.updateVentilationControlPoint(marker.id, controlKind, targetWorld)
            }
        }
        if (!updated) {
            return false
        }
        rebuildHvacGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun updateArchitectureHoleByHandle(
        group: GroupNode,
        wallId: String,
        holeId: String,
        handleKind: HoleHandleKind,
        targetWorld: Vector3
    ): Boolean {
        val store = architectureStoreFor(group)
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
        selectedArchitectureHole = ArchitectureHoleSelection(wallId, holeId)
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun updateArchitectureSlabHoleByHandle(
        group: GroupNode,
        slabId: String,
        holeId: String,
        handleKind: HoleHandleKind,
        targetWorld: Vector3
    ): Boolean {
        val store = architectureStoreFor(group)
        val slab = store.allSlabs().firstOrNull { it.id == slabId } ?: return false
        val hole = slab.holes.firstOrNull { it.id == holeId } ?: return false
        val basis = slabBasis(slab) ?: return false
        val projected = projectToSlab(basis, targetWorld)
        val uMin = min(0f, basis.sizeU)
        val uMax = max(0f, basis.sizeU)
        val vMin = min(0f, basis.sizeV)
        val vMax = max(0f, basis.sizeV)

        var u0 = hole.u0
        var u1 = hole.u1
        var v0 = hole.v0
        var v1 = hole.v1

        when (handleKind) {
            HoleHandleKind.CENTER -> {
                val width = (hole.u1 - hole.u0).coerceAtLeast(0.05f)
                val height = (hole.v1 - hole.v0).coerceAtLeast(0.05f)
                val safeWidth = width.coerceAtMost((uMax - uMin - 0.02f).coerceAtLeast(0.05f))
                val safeHeight = height.coerceAtMost((vMax - vMin - 0.02f).coerceAtLeast(0.05f))
                val centerU = projected.x.coerceIn(uMin + safeWidth * 0.5f, uMax - safeWidth * 0.5f)
                val centerV = projected.y.coerceIn(vMin + safeHeight * 0.5f, vMax - safeHeight * 0.5f)
                u0 = centerU - safeWidth * 0.5f
                u1 = centerU + safeWidth * 0.5f
                v0 = centerV - safeHeight * 0.5f
                v1 = centerV + safeHeight * 0.5f
            }
            HoleHandleKind.CORNER_0 -> {
                u0 = projected.x.coerceIn(uMin, uMax)
                v0 = projected.y.coerceIn(vMin, vMax)
            }
            HoleHandleKind.CORNER_1 -> {
                u1 = projected.x.coerceIn(uMin, uMax)
                v0 = projected.y.coerceIn(vMin, vMax)
            }
            HoleHandleKind.CORNER_2 -> {
                u1 = projected.x.coerceIn(uMin, uMax)
                v1 = projected.y.coerceIn(vMin, vMax)
            }
            HoleHandleKind.CORNER_3 -> {
                u0 = projected.x.coerceIn(uMin, uMax)
                v1 = projected.y.coerceIn(vMin, vMax)
            }
            HoleHandleKind.EDGE_0 -> v0 = projected.y.coerceIn(vMin, vMax)
            HoleHandleKind.EDGE_1 -> u1 = projected.x.coerceIn(uMin, uMax)
            HoleHandleKind.EDGE_2 -> v1 = projected.y.coerceIn(vMin, vMax)
            HoleHandleKind.EDGE_3 -> u0 = projected.x.coerceIn(uMin, uMax)
        }

        if (!store.updateSlabHole(slabId, holeId, u0, u1, v0, v1)) {
            return false
        }
        selectedArchitectureSlabHole = ArchitectureSlabHoleSelection(slabId, holeId)
        rebuildArchitectureGeometry(rootPrototype)
        notifyChange()
        return true
    }

    fun architectureHoleForContourSegment(
        group: GroupNode,
        segment: DraftLineStore.Segment,
        wallIdFilter: String? = null
    ): Pair<String, String>? {
        val store = architectureStoreFor(group)
        store.allWalls().forEach { wall ->
            if (wallIdFilter != null && wall.id != wallIdFilter) {
                return@forEach
            }
            wall.holes.forEach { hole ->
                val contours = holeContourSegments(wall, hole, includeDiagonals = true)
                val matches = contours.any { contour ->
                    segmentsApproxEqual(segment.start, segment.end, contour.first, contour.second)
                }
                if (matches) {
                    return wall.id to hole.id
                }
            }
        }
        return null
    }

    fun architectureSlabHoleForContourSegment(
        group: GroupNode,
        segment: DraftLineStore.Segment,
        slabIdFilter: String? = null
    ): Pair<String, String>? {
        val store = architectureStoreFor(group)
        store.allSlabs().forEach { slab ->
            if (slabIdFilter != null && slab.id != slabIdFilter) {
                return@forEach
            }
            slab.holes.forEach { hole ->
                val contours = slabHoleContourSegments(slab, hole, includeDiagonals = true)
                val matches = contours.any { contour ->
                    segmentsApproxEqual(segment.start, segment.end, contour.first, contour.second)
                }
                if (matches) {
                    return slab.id to hole.id
                }
            }
        }
        return null
    }

    fun deleteSelectedArchitectureHoleContours(group: GroupNode): Int {
        val store = architectureStoreFor(group)
        val selected = root.lineStore.getSelected().toList()
        if (selected.isEmpty()) {
            return 0
        }
        val removedWalls = store.removeHoles { wall, hole ->
            val contours = holeContourSegments(wall, hole, includeDiagonals = true)
            contours.any { contour ->
                selected.any { seg ->
                    segmentsApproxEqual(seg.start, seg.end, contour.first, contour.second)
                }
            }
        }
        val removedSlabs = store.removeSlabHoles { slab, hole ->
            val contours = slabHoleContourSegments(slab, hole, includeDiagonals = true)
            contours.any { contour ->
                selected.any { seg ->
                    segmentsApproxEqual(seg.start, seg.end, contour.first, contour.second)
                }
            }
        }
        val removed = removedWalls + removedSlabs
        if (removed > 0) {
            rebuildArchitectureGeometry(rootPrototype)
            notifyChange()
        }
        return removed
    }

    fun deleteSelectedArchitectureHoles(group: GroupNode): Int {
        if (group != root) {
            return 0
        }
        val sel = selectedArchitectureHole(group) ?: return 0
        val removed = if (architectureStoreFor(group).removeHole(sel.wallId, sel.holeId)) 1 else 0
        if (removed > 0) {
            selectedArchitectureHole = null
            rebuildArchitectureGeometry(rootPrototype)
            notifyChange()
        }
        return removed
    }

    fun deleteSelectedArchitectureSlabHoles(group: GroupNode): Int {
        if (group != root) {
            return 0
        }
        val sel = selectedArchitectureSlabHole(group) ?: return 0
        val removed = if (architectureStoreFor(group).removeSlabHole(sel.slabId, sel.holeId)) 1 else 0
        if (removed > 0) {
            selectedArchitectureSlabHole = null
            rebuildArchitectureGeometry(rootPrototype)
            notifyChange()
        }
        return removed
    }

    fun explodeSelectedArchitectureElements(group: GroupNode): Int {
        val store = architectureStoreFor(group)
        val selected = store.selectedElements().toList()
        if (selected.isEmpty()) {
            return 0
        }

        val wallJoinShifts = computeWallJoinShifts(store.allWalls())
        val capturedLineStore = DraftLineStore()
        val capturedFaceStore = DraftFaceStore(defaultFaceColor)
        capturedFaceStore.withChangeSuppressed {
            capturedLineStore.withChangeSuppressed {
                selected.forEach { selection ->
                    when (selection.kind) {
                        ArchitectureStore.ElementKind.WALL -> {
                            val wall = store.wallById(selection.id) ?: return@forEach
                            appendWallGeometry(
                                faceStore = capturedFaceStore,
                                lineStore = capturedLineStore,
                                wall = wall,
                                exteriorColor = wall.exteriorColor,
                                interiorColor = wall.interiorColor,
                                joinShift = wallJoinShifts[wall.id] ?: WallJoinShift()
                            )
                        }
                        ArchitectureStore.ElementKind.SLAB -> {
                            val slab = store.allSlabs().firstOrNull { it.id == selection.id } ?: return@forEach
                            appendSlabGeometry(
                                faceStore = capturedFaceStore,
                                lineStore = capturedLineStore,
                                slab = slab,
                                topColor = slab.topColor,
                                bottomColor = slab.bottomColor,
                                sideColor = slab.sideColor
                            )
                        }
                        ArchitectureStore.ElementKind.STAIR -> {
                            val stair = store.allStairs().firstOrNull { it.id == selection.id } ?: return@forEach
                            appendStairGeometry(
                                faceStore = capturedFaceStore,
                                lineStore = capturedLineStore,
                                stair = stair,
                                treadColor = stair.treadColor,
                                supportColor = stair.supportColor
                            )
                        }
                        ArchitectureStore.ElementKind.FRAME -> {
                            val frame = store.allFrames().firstOrNull { it.id == selection.id } ?: return@forEach
                            appendFrameGeometry(
                                faceStore = capturedFaceStore,
                                lineStore = capturedLineStore,
                                frame = frame,
                                color = frame.color
                            )
                        }
                    }
                }
            }
        }

        val removed = store.deleteSelectedElements()
        if (removed <= 0) {
            return 0
        }
        rebuildArchitectureGeometry(rootPrototype)

        val lineStore = root.lineStore
        val faceStore = root.faceStore
        val existingLines = lineStore.getSegments().toMutableSet()
        val existingFaces = faceStore.getTriangles().toMutableSet()

        faceStore.withChangeSuppressed {
            lineStore.withChangeSuppressed {
                capturedLineStore.getSegments().forEach { segment ->
                    if (existingLines.add(segment)) {
                        lineStore.addSegment(segment.start, segment.end, autoCleanup = false)
                    }
                }
                capturedFaceStore.getTriangles().forEach { triangle ->
                    if (existingFaces.add(triangle)) {
                        faceStore.addTriangle(triangle.a, triangle.b, triangle.c, capturedFaceStore.colorFor(triangle))
                    }
                }
            }
        }
        lineStore.notifyExternalChange()
        faceStore.notifyExternalChange()
        notifyChange()
        return removed
    }

    fun explodeSelectedHvacElements(group: GroupNode): Int {
        val store = hvacStoreFor(group)
        val selected = store.selectedElements().toList()
        if (selected.isEmpty()) {
            return 0
        }
        rebuildResolvedVentilation(store.allVentilationDucts())

        val capturedLineStore = DraftLineStore()
        val capturedFaceStore = DraftFaceStore(defaultFaceColor)
        capturedFaceStore.withChangeSuppressed {
            capturedLineStore.withChangeSuppressed {
                selected.forEach { selection ->
                    when (selection.kind) {
                        HvacStore.ElementKind.PLUMBING -> {
                            val run = store.plumbingById(selection.id) ?: return@forEach
                            appendHvacPlumbingGeometry(capturedFaceStore, capturedLineStore, run)
                        }
                        HvacStore.ElementKind.VENTILATION -> {
                            val duct = store.ventilationById(selection.id) ?: return@forEach
                            appendHvacVentilationGeometry(capturedFaceStore, capturedLineStore, duct)
                        }
                    }
                }
            }
        }

        val removed = store.deleteSelectedElements()
        if (removed <= 0) {
            return 0
        }
        rebuildHvacGeometry(rootPrototype)

        val lineStore = root.lineStore
        val faceStore = root.faceStore
        val existingLines = lineStore.getSegments().toMutableSet()
        val existingFaces = faceStore.getTriangles().toMutableSet()

        faceStore.withChangeSuppressed {
            lineStore.withChangeSuppressed {
                capturedLineStore.getSegments().forEach { segment ->
                    if (existingLines.add(segment)) {
                        lineStore.addSegment(segment.start, segment.end, autoCleanup = false)
                    }
                }
                capturedFaceStore.getTriangles().forEach { triangle ->
                    if (existingFaces.add(triangle)) {
                        faceStore.addTriangle(triangle.a, triangle.b, triangle.c, capturedFaceStore.colorFor(triangle))
                    }
                }
            }
        }
        lineStore.notifyExternalChange()
        faceStore.notifyExternalChange()
        notifyChange()
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
            hvacStore = null,
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
                group.textStore.addText(
                    position = Vector3(text.position).sub(origin),
                    text = text.text,
                    size = text.size,
                    normal = text.normal,
                    axisU = text.axisU,
                    screenText = text.screenText,
                    kind = text.kind,
                    tracking = text.tracking,
                    lineSpacing = text.lineSpacing,
                    glyphSourcePath = text.glyphSourcePath
                )
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
                parent.textStore.addText(
                    position = position,
                    text = text.text,
                    size = text.size,
                    normal = text.normal,
                    axisU = text.axisU,
                    screenText = text.screenText,
                    kind = text.kind,
                    tracking = text.tracking,
                    lineSpacing = text.lineSpacing,
                    glyphSourcePath = text.glyphSourcePath
                )
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
        // New instances start from prototype defaults; runtime overrides are per-instance and not copied.
        val template = prototypeInstances[prototype.id]
            ?.maxByOrNull { candidate -> candidate.children.size }
        template?.children?.forEach { child ->
            val childClone = cloneGroupStructure(child)
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

    fun queryGroupsByAabb(min: Vector3, max: Vector3, includeRoot: Boolean = true): List<GroupNode> {
        ensureGroupSpatialIndex()
        if (!hasGroupSpatialBounds) {
            return emptyList()
        }
        return groupSpatialIndex.queryAabb(min, max)
            .mapNotNull { id -> findGroupById(id) }
            .filter { includeRoot || it !== root }
    }

    fun queryGroupsByRay(ray: Ray, includeRoot: Boolean = true): List<GroupNode> {
        ensureGroupSpatialIndex()
        if (!hasGroupSpatialBounds) {
            return emptyList()
        }
        val range = SpatialHash3D.rayAabbRange(ray.origin, ray.direction, groupSpatialBoundsMin, groupSpatialBoundsMax, 1e-4f)
            ?: return emptyList()
        val start = Vector3(ray.origin).mulAdd(ray.direction, range[0])
        val end = Vector3(ray.origin).mulAdd(ray.direction, range[1])
        val minPoint = Vector3(
            kotlin.math.min(start.x, end.x),
            kotlin.math.min(start.y, end.y),
            kotlin.math.min(start.z, end.z)
        )
        val maxPoint = Vector3(
            kotlin.math.max(start.x, end.x),
            kotlin.math.max(start.y, end.y),
            kotlin.math.max(start.z, end.z)
        )
        return queryGroupsByAabb(minPoint, maxPoint, includeRoot)
    }

    fun queryGroupsByFrustum(camera: Camera, includeRoot: Boolean = true): List<GroupNode> {
        ensureGroupSpatialIndex()
        if (!hasGroupSpatialBounds) {
            return emptyList()
        }
        val planePoints = camera.frustum.planePoints
        if (planePoints.isEmpty()) {
            return emptyList()
        }
        val minPoint = Vector3(planePoints[0])
        val maxPoint = Vector3(planePoints[0])
        for (i in 1 until planePoints.size) {
            val point = planePoints[i]
            minPoint.x = min(minPoint.x, point.x)
            minPoint.y = min(minPoint.y, point.y)
            minPoint.z = min(minPoint.z, point.z)
            maxPoint.x = max(maxPoint.x, point.x)
            maxPoint.y = max(maxPoint.y, point.y)
            maxPoint.z = max(maxPoint.z, point.z)
        }
        return queryGroupsByAabb(minPoint, maxPoint, includeRoot).filter { group ->
            groupSpatialBoundsById[group.id]?.let { camera.frustum.boundsInFrustum(it) } == true
        }
    }

    fun warmSpatialIndex() {
        ensureGroupSpatialIndex()
    }

    private fun queryGroupsByAabbLinear(min: Vector3, max: Vector3, includeRoot: Boolean): List<GroupNode> {
        val result = mutableListOf<GroupNode>()
        fun test(group: GroupNode) {
            if (!includeRoot && group === root) {
                return
            }
            val bounds = group.geometryWorldBounds() ?: return
            if (bounds.max.x >= min.x && bounds.min.x <= max.x &&
                bounds.max.y >= min.y && bounds.min.y <= max.y &&
                bounds.max.z >= min.z && bounds.min.z <= max.z) {
                result.add(group)
            }
        }
        test(root)
        walkGroups(root) { group -> test(group) }
        return result
    }

    private fun queryGroupsByRayLinear(ray: Ray, includeRoot: Boolean): List<GroupNode> {
        val result = mutableListOf<GroupNode>()
        fun test(group: GroupNode) {
            if (!includeRoot && group === root) {
                return
            }
            val bounds = group.geometryWorldBounds() ?: return
            if (SpatialHash3D.rayAabbRange(ray.origin, ray.direction, bounds.min, bounds.max, 1e-4f) != null) {
                result.add(group)
            }
        }
        test(root)
        walkGroups(root) { group -> test(group) }
        return result
    }

    private fun queryGroupsByFrustumLinear(camera: Camera, includeRoot: Boolean): List<GroupNode> {
        val result = mutableListOf<GroupNode>()
        fun test(group: GroupNode) {
            if (!includeRoot && group === root) {
                return
            }
            val bounds = group.geometryWorldBounds() ?: return
            if (camera.frustum.boundsInFrustum(bounds)) {
                result.add(group)
            }
        }
        test(root)
        walkGroups(root) { group -> test(group) }
        return result
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

    fun collectActivePrototypeWorldTriangles(consumer: (Vector3, Vector3, Vector3, Color) -> Unit) {
        if (!isEditing()) {
            return
        }
        val group = activeGroup
        if (group.editPrototypeMode) {
            return
        }
        if (!group.hasGeometryOverrides()) {
            return
        }
        group.prototype.faceStore.getTriangles().forEach { tri ->
            consumer(
                group.toWorld(tri.a),
                group.toWorld(tri.b),
                group.toWorld(tri.c),
                group.prototype.faceStore.colorFor(tri)
            )
        }
    }

    fun collectActivePrototypeWorldLines(consumer: (Vector3, Vector3) -> Unit) {
        if (!isEditing()) {
            return
        }
        val group = activeGroup
        if (group.editPrototypeMode) {
            return
        }
        if (!group.hasGeometryOverrides()) {
            return
        }
        group.prototype.lineStore.getSegments().forEach { seg ->
            consumer(group.toWorld(seg.start), group.toWorld(seg.end))
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

    fun collectWorldTexts(
        consumer: (
            position: Vector3,
            text: String,
            size: Float,
            normal: Vector3,
            axisU: Vector3,
            selected: Boolean,
            screenText: Boolean,
            kind: DraftTextStore.Kind,
            tracking: Float,
            lineSpacing: Float,
            glyphSourcePath: String
        ) -> Unit
    ) {
        walkGroups(root) { group ->
            group.textStore.getTexts().forEach { text ->
                consumer(
                    group.toWorld(text.position),
                    text.text,
                    text.size,
                    group.vectorToWorld(text.normal),
                    group.vectorToWorld(text.axisU),
                    group.textStore.isSelected(text),
                    text.screenText,
                    text.kind,
                    text.tracking,
                    text.lineSpacing,
                    text.glyphSourcePath
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
                text.screenText,
                text.kind,
                text.tracking,
                text.lineSpacing,
                text.glyphSourcePath
            )
        }
    }

    fun rebuildArchitectureGeometry(prototype: ObjectPrototype) {
        val store = modelArchitectureStore
        val lineStore = root.lineStore
        val faceStore = root.faceStore

        lineStore.withChangeSuppressed {
            lineStore.deleteSegments(generatedArchitectureLines)
        }
        faceStore.withChangeSuppressed {
            faceStore.deleteTriangles(generatedArchitectureFaces)
        }
        generatedArchitectureLines.clear()
        generatedArchitectureFaces.clear()
        generatedArchitectureLineOwners.clear()
        generatedArchitectureFaceOwners.clear()

        if (store.allWalls().isEmpty() &&
            store.allSlabs().isEmpty() &&
            store.allStairs().isEmpty() &&
            store.allFrames().isEmpty()
        ) {
            lineStore.notifyExternalChange()
            faceStore.notifyExternalChange()
            return
        }

        val lineBaseline = lineStore.getSegments().toSet()
        val faceBaseline = faceStore.getTriangles().toSet()
        val wallJoinShifts = computeWallJoinShifts(store.allWalls())
        faceStore.withChangeSuppressed {
            lineStore.withChangeSuppressed {
                lineStore.withAutoSplitSuppressed {
                    store.allWalls().forEach { wall ->
                        val lineSizeBefore = lineStore.getSegments().size
                        val faceSizeBefore = faceStore.getTriangles().size
                        appendWallGeometry(
                            faceStore = faceStore,
                            lineStore = lineStore,
                            wall = wall,
                            exteriorColor = wall.exteriorColor,
                            interiorColor = wall.interiorColor,
                            joinShift = wallJoinShifts[wall.id] ?: WallJoinShift()
                        )
                        val owner = ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.WALL, wall.id)
                        lineStore.getSegments().drop(lineSizeBefore).forEach { generatedArchitectureLineOwners[it] = owner }
                        faceStore.getTriangles().drop(faceSizeBefore).forEach { generatedArchitectureFaceOwners[it] = owner }
                    }
                    store.allSlabs().forEach { slab ->
                        val lineSizeBefore = lineStore.getSegments().size
                        val faceSizeBefore = faceStore.getTriangles().size
                        appendSlabGeometry(faceStore, lineStore, slab, slab.topColor, slab.bottomColor, slab.sideColor)
                        val owner = ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.SLAB, slab.id)
                        lineStore.getSegments().drop(lineSizeBefore).forEach { generatedArchitectureLineOwners[it] = owner }
                        faceStore.getTriangles().drop(faceSizeBefore).forEach { generatedArchitectureFaceOwners[it] = owner }
                    }
                    store.allStairs().forEach { stair ->
                        val lineSizeBefore = lineStore.getSegments().size
                        val faceSizeBefore = faceStore.getTriangles().size
                        appendStairGeometry(faceStore, lineStore, stair, stair.treadColor, stair.supportColor)
                        val owner = ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.STAIR, stair.id)
                        lineStore.getSegments().drop(lineSizeBefore).forEach { generatedArchitectureLineOwners[it] = owner }
                        faceStore.getTriangles().drop(faceSizeBefore).forEach { generatedArchitectureFaceOwners[it] = owner }
                    }
                    store.allFrames().forEach { frame ->
                        val lineSizeBefore = lineStore.getSegments().size
                        val faceSizeBefore = faceStore.getTriangles().size
                        appendFrameGeometry(faceStore, lineStore, frame, frame.color)
                        val owner = ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.FRAME, frame.id)
                        lineStore.getSegments().drop(lineSizeBefore).forEach { generatedArchitectureLineOwners[it] = owner }
                        faceStore.getTriangles().drop(faceSizeBefore).forEach { generatedArchitectureFaceOwners[it] = owner }
                    }
                }
            }
        }
        generatedArchitectureLines.addAll(lineStore.getSegments().filter { it !in lineBaseline })
        generatedArchitectureFaces.addAll(faceStore.getTriangles().filter { it !in faceBaseline })
        lineStore.notifyExternalChange()
        faceStore.notifyExternalChange()
    }

    fun syncArchitectureGeometryAfterLoad() {
        generatedArchitectureLines.clear()
        generatedArchitectureFaces.clear()

        val store = modelArchitectureStore
        if (store.allWalls().isEmpty() &&
            store.allSlabs().isEmpty() &&
            store.allStairs().isEmpty() &&
            store.allFrames().isEmpty()
        ) {
            return
        }

        val expectedLineStore = DraftLineStore()
        val expectedFaceStore = DraftFaceStore(defaultFaceColor)
        val wallJoinShifts = computeWallJoinShifts(store.allWalls())
        expectedFaceStore.withChangeSuppressed {
            expectedLineStore.withChangeSuppressed {
                expectedLineStore.withAutoSplitSuppressed {
                    store.allWalls().forEach { wall ->
                        appendWallGeometry(
                            faceStore = expectedFaceStore,
                            lineStore = expectedLineStore,
                            wall = wall,
                            exteriorColor = wall.exteriorColor,
                            interiorColor = wall.interiorColor,
                            joinShift = wallJoinShifts[wall.id] ?: WallJoinShift()
                        )
                    }
                    store.allSlabs().forEach { slab ->
                        appendSlabGeometry(expectedFaceStore, expectedLineStore, slab, slab.topColor, slab.bottomColor, slab.sideColor)
                    }
                    store.allStairs().forEach { stair ->
                        appendStairGeometry(expectedFaceStore, expectedLineStore, stair, stair.treadColor, stair.supportColor)
                    }
                    store.allFrames().forEach { frame ->
                        appendFrameGeometry(expectedFaceStore, expectedLineStore, frame, frame.color)
                    }
                }
            }
        }

        val expectedSegmentCounts = mutableMapOf<SegmentGeomKey, Int>()
        expectedLineStore.getSegments().forEach { segment ->
            val key = segmentGeomKey(segment.start, segment.end)
            expectedSegmentCounts[key] = (expectedSegmentCounts[key] ?: 0) + 1
        }
        val expectedTriangleCounts = mutableMapOf<TriangleGeomKey, Int>()
        expectedFaceStore.getTriangles().forEach { triangle ->
            val key = triangleGeomKey(triangle.a, triangle.b, triangle.c)
            expectedTriangleCounts[key] = (expectedTriangleCounts[key] ?: 0) + 1
        }

        val staleSegments = mutableListOf<DraftLineStore.Segment>()
        root.lineStore.getSegments().forEach { segment ->
            val key = segmentGeomKey(segment.start, segment.end)
            val remaining = expectedSegmentCounts[key] ?: 0
            if (remaining > 0) {
                staleSegments.add(segment)
                if (remaining == 1) {
                    expectedSegmentCounts.remove(key)
                } else {
                    expectedSegmentCounts[key] = remaining - 1
                }
            }
        }

        val staleTriangles = mutableListOf<DraftFaceStore.Triangle>()
        root.faceStore.getTriangles().forEach { triangle ->
            val key = triangleGeomKey(triangle.a, triangle.b, triangle.c)
            val remaining = expectedTriangleCounts[key] ?: 0
            if (remaining > 0) {
                staleTriangles.add(triangle)
                if (remaining == 1) {
                    expectedTriangleCounts.remove(key)
                } else {
                    expectedTriangleCounts[key] = remaining - 1
                }
            }
        }

        if (staleSegments.isNotEmpty() || staleTriangles.isNotEmpty()) {
            root.faceStore.withChangeSuppressed {
                root.lineStore.withChangeSuppressed {
                    root.lineStore.deleteSegments(staleSegments)
                    root.faceStore.deleteTriangles(staleTriangles)
                }
            }
            root.lineStore.notifyExternalChange()
            root.faceStore.notifyExternalChange()
        }

        rebuildArchitectureGeometry(rootPrototype)
    }

    fun rebuildHvacGeometry(prototype: ObjectPrototype) {
        val store = modelHvacStore
        val lineStore = root.lineStore
        val faceStore = root.faceStore
        rebuildResolvedVentilation(store.allVentilationDucts())

        lineStore.withChangeSuppressed {
            lineStore.deleteSegments(generatedHvacLines)
        }
        faceStore.withChangeSuppressed {
            faceStore.deleteTriangles(generatedHvacFaces)
        }
        generatedHvacLines.clear()
        generatedHvacFaces.clear()
        generatedHvacLineOwners.clear()
        generatedHvacFaceOwners.clear()

        if (store.allPlumbingRuns().isEmpty() && store.allVentilationDucts().isEmpty()) {
            resolvedHvacVentilation.clear()
            lineStore.notifyExternalChange()
            faceStore.notifyExternalChange()
            return
        }

        val lineBaseline = lineStore.getSegments().toSet()
        val faceBaseline = faceStore.getTriangles().toSet()
        faceStore.withChangeSuppressed {
            lineStore.withChangeSuppressed {
                lineStore.withAutoSplitSuppressed {
                    store.allPlumbingRuns().forEach { run ->
                        val lineSizeBefore = lineStore.getSegments().size
                        val faceSizeBefore = faceStore.getTriangles().size
                        appendHvacPlumbingGeometry(faceStore, lineStore, run)
                        val owner = HvacStore.ElementSelection(HvacStore.ElementKind.PLUMBING, run.id)
                        lineStore.getSegments().drop(lineSizeBefore).forEach { generatedHvacLineOwners[it] = owner }
                        faceStore.getTriangles().drop(faceSizeBefore).forEach { generatedHvacFaceOwners[it] = owner }
                    }
                    store.allVentilationDucts().forEach { duct ->
                        val lineSizeBefore = lineStore.getSegments().size
                        val faceSizeBefore = faceStore.getTriangles().size
                        appendHvacVentilationGeometry(faceStore, lineStore, duct)
                        val owner = HvacStore.ElementSelection(HvacStore.ElementKind.VENTILATION, duct.id)
                        lineStore.getSegments().drop(lineSizeBefore).forEach { generatedHvacLineOwners[it] = owner }
                        faceStore.getTriangles().drop(faceSizeBefore).forEach { generatedHvacFaceOwners[it] = owner }
                    }
                }
            }
        }
        generatedHvacLines.addAll(lineStore.getSegments().filter { it !in lineBaseline })
        generatedHvacFaces.addAll(faceStore.getTriangles().filter { it !in faceBaseline })
        lineStore.notifyExternalChange()
        faceStore.notifyExternalChange()
    }

    fun syncHvacGeometryAfterLoad() {
        generatedHvacLines.clear()
        generatedHvacFaces.clear()
        rebuildHvacGeometry(rootPrototype)
    }

    private data class QuantizedPointKey(val x: Long, val y: Long, val z: Long)

    private data class SegmentGeomKey(val a: QuantizedPointKey, val b: QuantizedPointKey)

    private data class TriangleGeomKey(val a: QuantizedPointKey, val b: QuantizedPointKey, val c: QuantizedPointKey)

    private val quantizedPointComparator = Comparator<QuantizedPointKey> { left, right ->
        when {
            left.x != right.x -> left.x.compareTo(right.x)
            left.y != right.y -> left.y.compareTo(right.y)
            else -> left.z.compareTo(right.z)
        }
    }

    private fun quantizedPointKey(point: Vector3, epsilon: Float = 1e-3f): QuantizedPointKey {
        return QuantizedPointKey(
            x = (point.x / epsilon).roundToLong(),
            y = (point.y / epsilon).roundToLong(),
            z = (point.z / epsilon).roundToLong()
        )
    }

    private fun segmentGeomKey(start: Vector3, end: Vector3): SegmentGeomKey {
        val a = quantizedPointKey(start)
        val b = quantizedPointKey(end)
        return if (quantizedPointComparator.compare(a, b) <= 0) {
            SegmentGeomKey(a, b)
        } else {
            SegmentGeomKey(b, a)
        }
    }

    private fun triangleGeomKey(a: Vector3, b: Vector3, c: Vector3): TriangleGeomKey {
        val sorted = listOf(
            quantizedPointKey(a),
            quantizedPointKey(b),
            quantizedPointKey(c)
        ).sortedWith(quantizedPointComparator)
        return TriangleGeomKey(sorted[0], sorted[1], sorted[2])
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

    private data class SlabHoleCandidate(
        val slab: ArchitectureStore.Slab,
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

    private fun findNearestSlabHoleCandidate(
        store: ArchitectureStore,
        cornerA: Vector3,
        cornerB: Vector3
    ): SlabHoleCandidate? {
        var best: SlabHoleCandidate? = null
        store.allSlabs().forEach { slab ->
            val candidate = slabHoleCandidate(slab, cornerA, cornerB) ?: return@forEach
            if (best == null || candidate.planeDistance < best!!.planeDistance) {
                best = candidate
            }
        }
        return best
    }

    private fun slabHoleCandidate(
        slab: ArchitectureStore.Slab,
        cornerA: Vector3,
        cornerB: Vector3
    ): SlabHoleCandidate? {
        val basis = slabBasis(slab) ?: return null
        val pa = projectToSlab(basis, cornerA)
        val pb = projectToSlab(basis, cornerB)
        val uMin = min(0f, basis.sizeU)
        val uMax = max(0f, basis.sizeU)
        val vMin = min(0f, basis.sizeV)
        val vMax = max(0f, basis.sizeV)
        val marginU = min(0.01f, ((uMax - uMin) * 0.45f).coerceAtLeast(0f))
        val marginV = min(0.01f, ((vMax - vMin) * 0.45f).coerceAtLeast(0f))
        val u0 = min(pa.x, pb.x).coerceIn(uMin + marginU, uMax - marginU)
        val u1 = max(pa.x, pb.x).coerceIn(uMin + marginU, uMax - marginU)
        val v0 = min(pa.y, pb.y).coerceIn(vMin + marginV, vMax - marginV)
        val v1 = max(pa.y, pb.y).coerceIn(vMin + marginV, vMax - marginV)
        if (u1 - u0 <= 0.05f || v1 - v0 <= 0.05f) {
            return null
        }
        val dist = min(
            min(abs(pa.z), abs(pa.z - basis.thickness)),
            min(abs(pb.z), abs(pb.z - basis.thickness))
        )
        return SlabHoleCandidate(slab, u0, u1, v0, v1, dist)
    }

    private fun projectToWall(basis: WallBasis, point: Vector3): Vector3 {
        val rel = Vector3(point).sub(basis.start)
        return Vector3(
            rel.dot(basis.dir),
            rel.dot(basis.up),
            rel.dot(basis.normal)
        )
    }

    private fun projectToSlab(basis: SlabBasis, point: Vector3): Vector3 {
        val rel = Vector3(point).sub(basis.origin)
        return Vector3(
            rel.dot(basis.axisU),
            rel.dot(basis.axisV),
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
        val edgeEps = 1e-4f
        val segments = mutableListOf<Pair<Vector3, Vector3>>()
        if (abs(hole.v0) > edgeEps) {
            segments.add(p0 to p1)
        }
        if (abs(hole.u1 - basis.length) > edgeEps) {
            segments.add(p1 to p2)
        }
        if (abs(hole.v1 - wall.height) > edgeEps) {
            segments.add(p2 to p3)
        }
        if (abs(hole.u0) > edgeEps) {
            segments.add(p3 to p0)
        }
        if (includeDiagonals) {
            segments.add(p0 to p2)
            segments.add(p1 to p3)
        }
        return segments
    }

    private fun slabPoint(
        basis: SlabBasis,
        u: Float,
        v: Float,
        depth: Float
    ): Vector3 {
        return Vector3(basis.origin)
            .mulAdd(basis.axisU, u)
            .mulAdd(basis.axisV, v)
            .mulAdd(basis.normal, depth)
    }

    private fun slabHoleContourSegments(
        slab: ArchitectureStore.Slab,
        hole: ArchitectureStore.RectHole,
        includeDiagonals: Boolean = false
    ): List<Pair<Vector3, Vector3>> {
        val basis = slabBasis(slab) ?: return emptyList()
        val eps = 0.0015f
        val p0 = slabPoint(basis, hole.u0, hole.v0, basis.thickness + eps)
        val p1 = slabPoint(basis, hole.u1, hole.v0, basis.thickness + eps)
        val p2 = slabPoint(basis, hole.u1, hole.v1, basis.thickness + eps)
        val p3 = slabPoint(basis, hole.u0, hole.v1, basis.thickness + eps)
        val edgeEps = 1e-4f
        val uMin = min(0f, basis.sizeU)
        val uMax = max(0f, basis.sizeU)
        val vMin = min(0f, basis.sizeV)
        val vMax = max(0f, basis.sizeV)
        val segments = mutableListOf<Pair<Vector3, Vector3>>()
        if (abs(hole.v0 - vMin) > edgeEps) {
            segments.add(p0 to p1)
        }
        if (abs(hole.u1 - uMax) > edgeEps) {
            segments.add(p1 to p2)
        }
        if (abs(hole.v1 - vMax) > edgeEps) {
            segments.add(p2 to p3)
        }
        if (abs(hole.u0 - uMin) > edgeEps) {
            segments.add(p3 to p0)
        }
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

    private data class SlabBasis(
        val origin: Vector3,
        val axisU: Vector3,
        val axisV: Vector3,
        val normal: Vector3,
        val sizeU: Float,
        val sizeV: Float,
        val thickness: Float
    )

    private data class VentilationBasis(
        val start: Vector3,
        val tangent: Vector3,
        val binormal: Vector3,
        val normal: Vector3,
        val length: Float,
        val halfWidth: Float,
        val height: Float
    )

    private fun slabBasis(slab: ArchitectureStore.Slab): SlabBasis? {
        var axisU = Vector3(slab.axisU)
        if (axisU.len2() <= 1e-6f) {
            axisU.set(1f, 0f, 0f)
        } else {
            axisU.nor()
        }
        var axisV = Vector3(slab.axisV)
        axisV.mulAdd(axisU, -axisV.dot(axisU))
        if (axisV.len2() <= 1e-6f) {
            axisV = if (abs(axisU.y) < 0.9f) {
                Vector3(0f, 1f, 0f).crs(axisU)
            } else {
                Vector3(0f, 0f, 1f).crs(axisU)
            }
        }
        if (axisV.len2() <= 1e-6f) {
            return null
        }
        axisV.nor()

        var normal = Vector3(slab.normal)
        if (normal.len2() <= 1e-6f) {
            normal = Vector3(axisU).crs(axisV)
        }
        if (normal.len2() <= 1e-6f) {
            return null
        }
        normal.nor()
        if (Vector3(axisU).crs(axisV).dot(normal) < 0f) {
            axisV.scl(-1f)
        }

        val diagonal = Vector3(slab.max).sub(slab.min)
        var sizeU = diagonal.dot(axisU)
        var sizeV = diagonal.dot(axisV)
        if (abs(sizeU) <= 1e-6f && abs(sizeV) <= 1e-6f) {
            sizeU = 1f
            sizeV = 1f
        }

        return SlabBasis(
            origin = Vector3(slab.min),
            axisU = axisU,
            axisV = axisV,
            normal = normal,
            sizeU = sizeU,
            sizeV = sizeV,
            thickness = slab.thickness.coerceAtLeast(0.01f)
        )
    }

    private fun slabCorners(slab: ArchitectureStore.Slab): List<Vector3> {
        val basis = slabBasis(slab) ?: return emptyList()
        val c0 = Vector3(basis.origin)
        val c1 = Vector3(c0).mulAdd(basis.axisU, basis.sizeU)
        val c3 = Vector3(c0).mulAdd(basis.axisV, basis.sizeV)
        val c2 = Vector3(c1).mulAdd(basis.axisV, basis.sizeV)
        val lift = Vector3(basis.normal).scl(basis.thickness)
        val t0 = Vector3(c0).add(lift)
        val t1 = Vector3(c1).add(lift)
        val t2 = Vector3(c2).add(lift)
        val t3 = Vector3(c3).add(lift)
        return listOf(c0, c1, c2, c3, t0, t1, t2, t3)
    }

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
        val basis = slabBasis(slab) ?: return Float.POSITIVE_INFINITY
        val rel = Vector3(point).sub(basis.origin)
        val u = rel.dot(basis.axisU)
        val v = rel.dot(basis.axisV)
        val n = rel.dot(basis.normal)
        val du = rangeDistance(u, min(0f, basis.sizeU), max(0f, basis.sizeU))
        val dv = rangeDistance(v, min(0f, basis.sizeV), max(0f, basis.sizeV))
        val dn = rangeDistance(n, 0f, basis.thickness)
        return du * du + dv * dv + dn * dn
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

    private fun hvacPlumbingDistanceSq(run: HvacStore.PlumbingRun, point: Vector3): Float {
        if (run.path.size < 2) {
            return Float.POSITIVE_INFINITY
        }
        var best = Float.POSITIVE_INFINITY
        for (i in 0 until run.path.lastIndex) {
            val dist2 = pointSegmentDistanceSq(point, run.path[i], run.path[i + 1])
            if (dist2 < best) {
                best = dist2
            }
        }
        val radius = (run.diameter * 0.5f).coerceAtLeast(0.005f)
        return (best - radius * radius).coerceAtLeast(0f)
    }

    private fun hvacVentilationDistanceSq(duct: HvacStore.VentilationDuct, point: Vector3): Float {
        val resolved = resolvedVentilation(duct)
        if (resolved.path.size < 2) {
            return Float.POSITIVE_INFINITY
        }
        var best = Float.POSITIVE_INFINITY
        for (i in 0 until resolved.path.lastIndex) {
            val dist2 = pointSegmentDistanceSq(point, resolved.path[i], resolved.path[i + 1])
            if (dist2 < best) {
                best = dist2
            }
        }
        val radius = max(resolved.width, resolved.height).coerceAtLeast(0.01f)
        return (best - radius * radius).coerceAtLeast(0f)
    }

    private fun pointSegmentDistanceSq(point: Vector3, a: Vector3, b: Vector3): Float {
        val ab = Vector3(b).sub(a)
        val denom = ab.len2()
        if (denom <= 1e-10f) {
            return Vector3(point).sub(a).len2()
        }
        val t = Vector3(point).sub(a).dot(ab) / denom
        val clamped = t.coerceIn(0f, 1f)
        val closest = Vector3(a).mulAdd(ab, clamped)
        return Vector3(point).sub(closest).len2()
    }

    private fun buildFrameSelectionBasis(
        cornerA: Vector3,
        cornerB: Vector3,
        normalInput: Vector3,
        depth: Float
    ): FrameSelectionBasis? {
        val normal = Vector3(normalInput)
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
        val origin = Vector3(cornerA)
        val delta = Vector3(cornerB).sub(origin)
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
            nMax = depth.coerceAtLeast(0.01f)
        )
    }

    private fun frameSelectionBasis(frame: ArchitectureStore.Frame): FrameSelectionBasis? {
        val base = buildFrameSelectionBasis(frame.cornerA, frame.cornerB, frame.normal, frame.depth) ?: return null
        if (frame.contour.size < 3) {
            return base
        }
        var uMin = Float.POSITIVE_INFINITY
        var uMax = Float.NEGATIVE_INFINITY
        var vMin = Float.POSITIVE_INFINITY
        var vMax = Float.NEGATIVE_INFINITY
        frame.contour.forEach { point ->
            val rel = Vector3(point).sub(base.origin)
            val u = rel.dot(base.axisU)
            val v = rel.dot(base.axisV)
            uMin = min(uMin, u)
            uMax = max(uMax, u)
            vMin = min(vMin, v)
            vMax = max(vMax, v)
        }
        if (!uMin.isFinite() || !uMax.isFinite() || !vMin.isFinite() || !vMax.isFinite()) {
            return base
        }
        return base.copy(
            uMin = uMin,
            uMax = uMax,
            vMin = vMin,
            vMax = vMax
        )
    }

    private fun framePoint(basis: FrameSelectionBasis, u: Float, v: Float, depth: Float = 0f): Vector3 {
        return Vector3(basis.origin)
            .mulAdd(basis.axisU, u)
            .mulAdd(basis.axisV, v)
            .mulAdd(basis.normal, depth)
    }

    private fun frameContourProjected(frame: ArchitectureStore.Frame, basis: FrameSelectionBasis): List<FloatArray> {
        if (frame.contour.size >= 3) {
            return frame.contour.map { point ->
                val rel = Vector3(point).sub(basis.origin)
                floatArrayOf(rel.dot(basis.axisU), rel.dot(basis.axisV))
            }
        }
        return listOf(
            floatArrayOf(basis.uMin, basis.vMin),
            floatArrayOf(basis.uMax, basis.vMin),
            floatArrayOf(basis.uMax, basis.vMax),
            floatArrayOf(basis.uMin, basis.vMax)
        )
    }

    private fun frameProjectedToWorld(
        basis: FrameSelectionBasis,
        points: List<FloatArray>,
        depth: Float = 0f
    ): List<Vector3> {
        return points.map { point -> framePoint(basis, point[0], point[1], depth) }
    }

    private fun polygonSignedArea2d(points: List<FloatArray>): Float {
        var area = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            area += a[0] * b[1] - b[0] * a[1]
        }
        return area * 0.5f
    }

    private fun normalize2d(x: Float, y: Float): FloatArray? {
        val len = sqrt(x * x + y * y)
        if (len <= 1e-6f) {
            return null
        }
        return floatArrayOf(x / len, y / len)
    }

    private fun intersectLines2d(
        p0x: Float,
        p0y: Float,
        d0x: Float,
        d0y: Float,
        p1x: Float,
        p1y: Float,
        d1x: Float,
        d1y: Float
    ): FloatArray? {
        val det = d0x * d1y - d0y * d1x
        if (abs(det) <= 1e-6f) {
            return null
        }
        val dx = p1x - p0x
        val dy = p1y - p0y
        val t = (dx * d1y - dy * d1x) / det
        return floatArrayOf(p0x + d0x * t, p0y + d0y * t)
    }

    private fun insetClosedPolygon(points: List<FloatArray>, inset: Float): List<FloatArray>? {
        if (points.size < 3) {
            return null
        }
        val signedArea = polygonSignedArea2d(points)
        if (abs(signedArea) <= 1e-6f) {
            return null
        }
        val orientation = if (signedArea > 0f) 1f else -1f
        val result = mutableListOf<FloatArray>()
        for (i in points.indices) {
            val prev = points[(i - 1 + points.size) % points.size]
            val curr = points[i]
            val next = points[(i + 1) % points.size]
            val dirPrev = normalize2d(curr[0] - prev[0], curr[1] - prev[1]) ?: return null
            val dirNext = normalize2d(next[0] - curr[0], next[1] - curr[1]) ?: return null
            val nPrev = floatArrayOf(-dirPrev[1] * orientation, dirPrev[0] * orientation)
            val nNext = floatArrayOf(-dirNext[1] * orientation, dirNext[0] * orientation)
            val offsetPrevX = curr[0] + nPrev[0] * inset
            val offsetPrevY = curr[1] + nPrev[1] * inset
            val offsetNextX = curr[0] + nNext[0] * inset
            val offsetNextY = curr[1] + nNext[1] * inset
            val intersect = intersectLines2d(
                offsetPrevX, offsetPrevY, dirPrev[0], dirPrev[1],
                offsetNextX, offsetNextY, dirNext[0], dirNext[1]
            )
            val point = intersect ?: run {
                val miterX = nPrev[0] + nNext[0]
                val miterY = nPrev[1] + nNext[1]
                val miter = normalize2d(miterX, miterY)
                if (miter == null) {
                    floatArrayOf(offsetPrevX, offsetPrevY)
                } else {
                    val denom = miter[0] * nPrev[0] + miter[1] * nPrev[1]
                    if (abs(denom) <= 1e-4f) {
                        floatArrayOf(offsetPrevX, offsetPrevY)
                    } else {
                        val scale = inset / denom
                        floatArrayOf(curr[0] + miter[0] * scale, curr[1] + miter[1] * scale)
                    }
                }
            }
            result.add(point)
        }
        if (result.size < 3 || abs(polygonSignedArea2d(result)) <= 1e-6f) {
            return null
        }
        return result
    }

    private fun triangulateProjectedPolygon(points: List<FloatArray>): List<IntArray> {
        if (points.size < 3) {
            return emptyList()
        }
        val projected = points.map { floatArrayOf(it[0], it[1]) }
        var indices = projected.indices.toList()
        if (polygonSignedArea2d(projected) < 0f) {
            indices = indices.reversed()
        }
        val triangles = mutableListOf<IntArray>()
        var guard = 0
        while (indices.size > 2 && guard < 10000) {
            guard++
            var earFound = false
            for (i in indices.indices) {
                val prev = indices[(i - 1 + indices.size) % indices.size]
                val curr = indices[i]
                val next = indices[(i + 1) % indices.size]
                if (!isConvex2d(projected[prev], projected[curr], projected[next])) {
                    continue
                }
                if (containsPointInTriangle2d(projected, indices, prev, curr, next)) {
                    continue
                }
                triangles.add(intArrayOf(prev, curr, next))
                indices = indices.toMutableList().also { it.removeAt(i) }
                earFound = true
                break
            }
            if (!earFound) {
                break
            }
        }
        return triangles
    }

    private fun isConvex2d(a: FloatArray, b: FloatArray, c: FloatArray): Boolean {
        val cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        return cross > 1e-6f
    }

    private fun containsPointInTriangle2d(
        points: List<FloatArray>,
        indices: List<Int>,
        prev: Int,
        curr: Int,
        next: Int
    ): Boolean {
        val a = points[prev]
        val b = points[curr]
        val c = points[next]
        val area = abs(triangleArea2d(a, b, c))
        for (idx in indices) {
            if (idx == prev || idx == curr || idx == next) {
                continue
            }
            val p = points[idx]
            val a1 = abs(triangleArea2d(p, b, c))
            val a2 = abs(triangleArea2d(a, p, c))
            val a3 = abs(triangleArea2d(a, b, p))
            if (abs(area - (a1 + a2 + a3)) < 1e-4f) {
                return true
            }
        }
        return false
    }

    private fun triangleArea2d(a: FloatArray, b: FloatArray, c: FloatArray): Float {
        return (a[0] * (b[1] - c[1]) +
            b[0] * (c[1] - a[1]) +
            c[0] * (a[1] - b[1])) * 0.5f
    }

    private fun rangeDistance(value: Float, minValue: Float, maxValue: Float): Float {
        return when {
            value < minValue -> minValue - value
            value > maxValue -> value - maxValue
            else -> 0f
        }
    }

    private fun rescaleFrameContour(
        frame: ArchitectureStore.Frame,
        newCornerA: Vector3,
        newCornerB: Vector3
    ): List<Vector3>? {
        if (frame.contour.size < 3) {
            return emptyList()
        }
        val oldBasis = frameSelectionBasis(frame) ?: return null
        val newBasis = buildFrameSelectionBasis(newCornerA, newCornerB, frame.normal, frame.depth) ?: return null
        val oldWidth = oldBasis.uMax - oldBasis.uMin
        val oldHeight = oldBasis.vMax - oldBasis.vMin
        val newWidth = newBasis.uMax - newBasis.uMin
        val newHeight = newBasis.vMax - newBasis.vMin
        if (oldWidth <= 1e-6f || oldHeight <= 1e-6f || newWidth <= 1e-6f || newHeight <= 1e-6f) {
            return null
        }
        return frame.contour.map { point ->
            val rel = Vector3(point).sub(oldBasis.origin)
            val u = rel.dot(oldBasis.axisU)
            val v = rel.dot(oldBasis.axisV)
            val tu = (u - oldBasis.uMin) / oldWidth
            val tv = (v - oldBasis.vMin) / oldHeight
            framePoint(
                newBasis,
                newBasis.uMin + tu * newWidth,
                newBasis.vMin + tv * newHeight
            )
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
            val u0 = min(hole.u0, hole.u1).coerceIn(0f, basis.length)
            val u1 = max(hole.u0, hole.u1).coerceIn(0f, basis.length)
            val v0 = min(hole.v0, hole.v1).coerceIn(0f, wall.height)
            val v1 = max(hole.v0, hole.v1).coerceIn(0f, wall.height)
            ArchitectureStore.RectHole(hole.id, hole.name, u0, u1, v0, v1)
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
        val basis = slabBasis(slab) ?: return
        val uMin = min(0f, basis.sizeU)
        val uMax = max(0f, basis.sizeU)
        val vMin = min(0f, basis.sizeV)
        val vMax = max(0f, basis.sizeV)
        if (uMax - uMin <= 1e-4f || vMax - vMin <= 1e-4f) {
            return
        }
        val holes = slab.holes.map { hole ->
            val hu0 = min(hole.u0, hole.u1).coerceIn(uMin, uMax)
            val hu1 = max(hole.u0, hole.u1).coerceIn(uMin, uMax)
            val hv0 = min(hole.v0, hole.v1).coerceIn(vMin, vMax)
            val hv1 = max(hole.v0, hole.v1).coerceIn(vMin, vMax)
            ArchitectureStore.RectHole(hole.id, hole.name, hu0, hu1, hv0, hv1)
        }.filter { it.u1 - it.u0 > 0.02f && it.v1 - it.v0 > 0.02f }

        val uCuts = mutableListOf(uMin, uMax)
        val vCuts = mutableListOf(vMin, vMax)
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

        val topNormal = Vector3(basis.normal).nor()
        val depth0 = 0f
        val depth1 = basis.thickness

        for (i in 0 until sortedU.lastIndex) {
            for (j in 0 until sortedV.lastIndex) {
                if (!solid[i][j]) {
                    continue
                }
                val u0 = sortedU[i]
                val u1 = sortedU[i + 1]
                val v0 = sortedV[j]
                val v1 = sortedV[j + 1]

                val b00 = slabPoint(basis, u0, v0, depth0)
                val b10 = slabPoint(basis, u1, v0, depth0)
                val b11 = slabPoint(basis, u1, v1, depth0)
                val b01 = slabPoint(basis, u0, v1, depth0)
                val t00 = slabPoint(basis, u0, v0, depth1)
                val t10 = slabPoint(basis, u1, v0, depth1)
                val t11 = slabPoint(basis, u1, v1, depth1)
                val t01 = slabPoint(basis, u0, v1, depth1)

                addQuad(faceStore, lineStore, b00, b10, b11, b01, Vector3(topNormal).scl(-1f), bottomColor)
                addQuad(faceStore, lineStore, t00, t01, t11, t10, Vector3(topNormal), topColor)

                if (!isSolid(i - 1, j)) {
                    addQuad(faceStore, lineStore, b00, b01, t01, t00, Vector3(basis.axisU).scl(-1f), sideColor)
                }
                if (!isSolid(i + 1, j)) {
                    addQuad(faceStore, lineStore, b10, t10, t11, b11, Vector3(basis.axisU), sideColor)
                }
                if (!isSolid(i, j - 1)) {
                    addQuad(faceStore, lineStore, b00, t00, t10, b10, Vector3(basis.axisV).scl(-1f), sideColor)
                }
                if (!isSolid(i, j + 1)) {
                    addQuad(faceStore, lineStore, b01, b11, t11, t01, Vector3(basis.axisV), sideColor)
                }
            }
        }

        holes.forEach { hole ->
            val contours = slabHoleContourSegments(slab, hole, includeDiagonals = true)
            contours.forEach { (a, b) ->
                lineStore.addSegment(a, b, autoCleanup = false)
            }
        }
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
        leftEnabled: Boolean,
        rightEnabled: Boolean
    ) {
        if (!leftEnabled && !rightEnabled) {
            return
        }
        val offsets = floatArrayOf(1f, 2f, 3f, 4f)
        offsets.forEach { offset ->
            val y = stepTopY + offset
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
                        addLastStepSupportSkirt(
                            faceStore = faceStore,
                            lineStore = lineStore,
                            section = current,
                            bottomY = y,
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

    private fun addLastStepSupportSkirt(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        section: StairSupportSection,
        bottomY: Float,
        color: Color
    ) {
        val topLoop = listOf(section.c0, section.c1, section.o1, section.o0)
        val bottomLoop = topLoop.map { point -> Vector3(point.x, bottomY, point.z) }
        for (i in topLoop.indices) {
            val next = (i + 1) % topLoop.size
            val t0 = topLoop[i]
            val t1 = topLoop[next]
            val b0 = bottomLoop[i]
            val b1 = bottomLoop[next]
            if (t0.dst2(t1) <= 1e-8f) {
                continue
            }
            if (abs(t0.y - b0.y) <= 1e-5f && abs(t1.y - b1.y) <= 1e-5f) {
                continue
            }
            addStairSupportQuad(
                faceStore = faceStore,
                lineStore = lineStore,
                p0 = t0,
                p1 = b0,
                p2 = b1,
                p3 = t1,
                color = color
            )
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
        val basis = frameSelectionBasis(frame) ?: return
        val outerProjected = frameContourProjected(frame, basis)
        if (outerProjected.size < 3) {
            return
        }
        val signedArea = polygonSignedArea2d(outerProjected)
        if (abs(signedArea) <= 1e-6f) {
            return
        }
        val outerWorld = frameProjectedToWorld(basis, outerProjected)
        val minExtent = run {
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            outerProjected.forEach { point ->
                minX = min(minX, point[0])
                maxX = max(maxX, point[0])
                minY = min(minY, point[1])
                maxY = max(maxY, point[1])
            }
            min(maxX - minX, maxY - minY)
        }
        val inset = frame.frameWidth.coerceAtLeast(0.01f).coerceAtMost(minExtent * 0.45f)
        val innerProjected = insetClosedPolygon(outerProjected, inset) ?: return
        val innerWorld = frameProjectedToWorld(basis, innerProjected)
        val normal = Vector3(basis.normal).nor()
        val backNormal = Vector3(normal).scl(-1f)
        val depth = frame.depth.coerceAtLeast(0.01f)
        val areaPositive = signedArea > 0f

        for (i in outerWorld.indices) {
            val next = (i + 1) % outerWorld.size
            val o0 = outerWorld[i]
            val o1 = outerWorld[next]
            val i1 = innerWorld[next]
            val i0 = innerWorld[i]
            val o0b = Vector3(o0).mulAdd(normal, depth)
            val o1b = Vector3(o1).mulAdd(normal, depth)
            val i1b = Vector3(i1).mulAdd(normal, depth)
            val i0b = Vector3(i0).mulAdd(normal, depth)

            addQuad(faceStore, lineStore, o0, o1, i1, i0, backNormal, color)
            addQuad(faceStore, lineStore, o0b, i0b, i1b, o1b, normal, color)

            val edgeDir = Vector3(o1).sub(o0)
            val outerNormal = if (areaPositive) {
                Vector3(edgeDir).crs(normal).nor()
            } else {
                Vector3(normal).crs(edgeDir).nor()
            }
            addQuad(faceStore, lineStore, o0, o0b, o1b, o1, outerNormal, color)

            val innerEdgeDir = Vector3(i1).sub(i0)
            val innerNormal = if (areaPositive) {
                Vector3(normal).crs(innerEdgeDir).nor()
            } else {
                Vector3(innerEdgeDir).crs(normal).nor()
            }
            addQuad(faceStore, lineStore, i0, i1, i1b, i0b, innerNormal, color)
        }

        if (frame.glazingEnabled) {
            val panelWorld = innerProjected.map { point -> framePoint(basis, point[0], point[1], depth * 0.5f) }
            triangulateProjectedPolygon(innerProjected).forEach { tri ->
                val a = panelWorld[tri[0]]
                val b = panelWorld[tri[1]]
                val c = panelWorld[tri[2]]
                faceStore.addTriangle(Vector3(a), Vector3(b), Vector3(c), frame.glazingColor)
                faceStore.addTriangle(Vector3(a), Vector3(c), Vector3(b), frame.glazingColor)
            }
        }
    }

    private data class SegmentProjection(
        val point: Vector3,
        val t: Float,
        val dist2: Float
    )

    private data class ResolvedJoinPoint(
        val point: Vector3,
        val joined: Boolean
    )

    private data class SegmentSegmentClosest(
        val dist2: Float,
        val s: Float,
        val t: Float
    )

    private fun rebuildResolvedVentilation(ducts: List<HvacStore.VentilationDuct>) {
        resolvedHvacVentilation.clear()
        if (ducts.isEmpty()) {
            return
        }
        val processed = mutableListOf<ResolvedVentilationDuct>()
        ducts.forEach { duct ->
            var start = Vector3(duct.start)
            var end = Vector3(duct.end)
            var binormalRef = Vector3(duct.binormalRef)
            val autoJoinEnabled = duct.autoJoinEnabled
            val width = duct.width.coerceAtLeast(0.01f)
            val height = duct.height.coerceAtLeast(0.01f)
            if (start.dst2(end) <= 1e-6f) {
                return@forEach
            }

            if (!autoJoinEnabled) {
                processed.add(
                    ResolvedVentilationDuct(
                        id = duct.id,
                        start = start,
                        end = end,
                        path = listOf(Vector3(start), Vector3(end)),
                        binormalRef = binormalRef,
                        autoJoinEnabled = false,
                        width = width,
                        height = height,
                        startJoined = false,
                        endJoined = false
                    )
                )
                return@forEach
            }

            val joinSnapDistance = max(0.08f, max(width, height) * 0.65f)
            val startJoin = resolveVentilationJoinPoint(start, processed, joinSnapDistance)
            val endJoin = resolveVentilationJoinPoint(end, processed, joinSnapDistance)
            start = startJoin.point
            end = endJoin.point
            if (start.dst2(end) <= 1e-6f) {
                end.set(duct.end)
                if (start.dst2(end) <= 1e-6f) {
                    return@forEach
                }
            }

            val resolved = ResolvedVentilationDuct(
                id = duct.id,
                start = start,
                end = end,
                path = buildVentilationPathWithHumps(
                    start = start,
                    end = end,
                    binormalRef = binormalRef,
                    width = width,
                    height = height,
                    humpHalfSpan = duct.humpHalfSpan.coerceAtLeast(0.01f),
                    humpClearance = duct.humpClearance.coerceAtLeast(0f),
                    older = processed,
                    joinSnapDistance = joinSnapDistance
                ),
                binormalRef = binormalRef,
                autoJoinEnabled = true,
                width = width,
                height = height,
                startJoined = startJoin.joined,
                endJoined = endJoin.joined
            )
            processed.add(resolved)
        }
        val startJoinPlanes = mutableMapOf<String, Vector3>()
        val endJoinPlanes = mutableMapOf<String, Vector3>()
        computeVentilationJoinPlaneNormals(processed, startJoinPlanes, endJoinPlanes)
        processed.forEach { resolved ->
            resolvedHvacVentilation[resolved.id] = resolved.copy(
                startJoinPlaneNormal = startJoinPlanes[resolved.id]?.let { Vector3(it) },
                endJoinPlaneNormal = endJoinPlanes[resolved.id]?.let { Vector3(it) }
            )
        }
    }

    private fun computeVentilationJoinPlaneNormals(
        ducts: List<ResolvedVentilationDuct>,
        startPlanes: MutableMap<String, Vector3>,
        endPlanes: MutableMap<String, Vector3>
    ) {
        data class EndpointRef(
            val id: String,
            val atStart: Boolean,
            val point: Vector3,
            val inwardDir: Vector3,
            val sideDir: Vector3,
            val upDir: Vector3
        )

        val snap = 1e-3f
        fun keyOf(point: Vector3): EndpointKey {
            return EndpointKey(
                (point.x / snap).roundToInt(),
                (point.y / snap).roundToInt(),
                (point.z / snap).roundToInt()
            )
        }

        fun endpointAxes(point: Vector3, inward: Vector3, binormalRef: Vector3): Pair<Vector3, Vector3>? {
            if (inward.len2() <= 1e-8f) {
                return null
            }
            var side = Vector3(binormalRef).sub(point)
            side.sub(Vector3(inward).scl(side.dot(inward)))
            if (side.len2() <= 1e-8f) {
                side = initialPerpendicular(inward)
            }
            if (side.len2() <= 1e-8f) {
                return null
            }
            side.nor()
            var up = Vector3(inward).crs(side)
            if (up.len2() <= 1e-8f) {
                return null
            }
            up.nor()
            side = Vector3(up).crs(inward)
            if (side.len2() <= 1e-8f) {
                return null
            }
            side.nor()
            return Pair(side, up)
        }

        fun averageDirection(values: List<Vector3>): Vector3? {
            if (values.isEmpty()) {
                return null
            }
            val sum = Vector3()
            values.forEach { value -> sum.add(value) }
            if (sum.len2() <= 1e-8f) {
                return null
            }
            return sum.nor()
        }

        fun toSignedUnit(value: Float): Float {
            return when {
                value > 1e-4f -> 1f
                value < -1e-4f -> -1f
                else -> 0f
            }
        }

        fun buildCandidate(raw: Vector3, upAxis: Vector3?): Vector3? {
            if (raw.len2() <= 1e-8f) {
                return null
            }
            val candidate = Vector3(raw)
            if (upAxis != null) {
                val flattened = Vector3(candidate).mulAdd(upAxis, -candidate.dot(upAxis))
                if (flattened.len2() > 1e-8f) {
                    candidate.set(flattened)
                }
            }
            if (candidate.len2() <= 1e-8f) {
                return null
            }
            return candidate.nor()
        }

        fun scoreCandidate(candidate: Vector3, refs: List<EndpointRef>, sideAxis: Vector3?, upAxis: Vector3?): Float {
            val minDenom = refs.minOfOrNull { abs(candidate.dot(it.inwardDir)) } ?: 0f
            if (minDenom <= 1e-4f) {
                return Float.NEGATIVE_INFINITY
            }
            var score = minDenom * 4f
            if (sideAxis != null) {
                score += abs(candidate.dot(sideAxis)) * 2f
            }
            if (upAxis != null) {
                score -= abs(candidate.dot(upAxis)) * 2f
            }
            if (refs.size == 2) {
                val first = refs[0]
                val second = refs[1]
                val firstInsideSign = toSignedUnit(second.inwardDir.dot(first.sideDir))
                val secondInsideSign = toSignedUnit(first.inwardDir.dot(second.sideDir))
                if (firstInsideSign != 0f) {
                    val slope = -candidate.dot(first.sideDir) / candidate.dot(first.inwardDir)
                    score += (-slope * firstInsideSign) * 3f
                }
                if (secondInsideSign != 0f) {
                    val slope = -candidate.dot(second.sideDir) / candidate.dot(second.inwardDir)
                    score += (-slope * secondInsideSign) * 3f
                }
            }
            return score
        }

        val refsByKey = mutableMapOf<EndpointKey, MutableList<EndpointRef>>()
        ducts.forEach { duct ->
            if (!duct.autoJoinEnabled) {
                return@forEach
            }
            val path = duct.path
            if (path.size < 2) {
                return@forEach
            }
            val start = Vector3(path.first())
            val end = Vector3(path.last())
            val startDir = Vector3(path[1]).sub(start)
            val endDir = Vector3(path[path.lastIndex - 1]).sub(end)
            if (startDir.len2() > 1e-8f) {
                val inward = startDir.nor()
                val axes = endpointAxes(start, inward, duct.binormalRef) ?: return@forEach
                refsByKey.getOrPut(keyOf(start)) { mutableListOf() }.add(
                    EndpointRef(
                        id = duct.id,
                        atStart = true,
                        point = start,
                        inwardDir = inward,
                        sideDir = axes.first,
                        upDir = axes.second
                    )
                )
            }
            if (endDir.len2() > 1e-8f) {
                val inward = endDir.nor()
                val axes = endpointAxes(end, inward, duct.binormalRef) ?: return@forEach
                refsByKey.getOrPut(keyOf(end)) { mutableListOf() }.add(
                    EndpointRef(
                        id = duct.id,
                        atStart = false,
                        point = end,
                        inwardDir = inward,
                        sideDir = axes.first,
                        upDir = axes.second
                    )
                )
            }
        }

        refsByKey.values.forEach { refs ->
            if (refs.size < 2) {
                return@forEach
            }
            val averageUp = averageDirection(refs.map { it.upDir })
            val averageSide = averageDirection(refs.map { it.sideDir })
            val candidates = mutableListOf<Vector3>()
            if (refs.size == 2) {
                buildCandidate(Vector3(refs[0].inwardDir).add(refs[1].inwardDir), averageUp)?.let { candidates.add(it) }
                buildCandidate(Vector3(refs[0].inwardDir).sub(refs[1].inwardDir), averageUp)?.let { candidates.add(it) }
            } else {
                val sum = Vector3()
                refs.forEach { ref -> sum.add(ref.inwardDir) }
                buildCandidate(sum, averageUp)?.let { candidates.add(it) }
            }
            if (candidates.isEmpty()) {
                return@forEach
            }
            val planeNormal = candidates.maxByOrNull { candidate ->
                scoreCandidate(candidate, refs, averageSide, averageUp)
            } ?: return@forEach
            refs.forEach { ref ->
                if (ref.atStart) {
                    startPlanes[ref.id] = Vector3(planeNormal)
                } else {
                    endPlanes[ref.id] = Vector3(planeNormal)
                }
            }
        }
    }

    private fun resolveVentilationJoinPoint(
        point: Vector3,
        older: List<ResolvedVentilationDuct>,
        snapDistance: Float
    ): ResolvedJoinPoint {
        if (older.isEmpty()) {
            return ResolvedJoinPoint(Vector3(point), false)
        }
        val snapSq = snapDistance * snapDistance
        var bestPoint: Vector3? = null
        var bestDist2 = snapSq
        var joined = false

        older.forEach { duct ->
            val endpoints = listOf(duct.start, duct.end)
            endpoints.forEach { endpoint ->
                val dist2 = point.dst2(endpoint)
                if (dist2 < bestDist2) {
                    bestDist2 = dist2
                    bestPoint = Vector3(endpoint)
                    joined = true
                }
            }
        }
        if (bestPoint != null) {
            return ResolvedJoinPoint(bestPoint!!, joined)
        }

        older.forEach { duct ->
            val path = duct.path
            if (path.size < 2) {
                return@forEach
            }
            for (i in 0 until path.lastIndex) {
                val projected = projectPointToSegment(point, path[i], path[i + 1]) ?: continue
                if (projected.t <= 0.05f || projected.t >= 0.95f) {
                    continue
                }
                if (projected.dist2 < bestDist2) {
                    bestDist2 = projected.dist2
                    bestPoint = Vector3(projected.point)
                    joined = true
                }
            }
        }

        return ResolvedJoinPoint(bestPoint ?: Vector3(point), joined)
    }

    private data class VentilationCrossing(
        val s: Float,
        val olderWidth: Float,
        val olderHeight: Float
    )

    private data class HumpInterval(
        val s0: Float,
        val s1: Float,
        val lift: Float
    )

    private fun buildVentilationPathWithHumps(
        start: Vector3,
        end: Vector3,
        binormalRef: Vector3,
        width: Float,
        height: Float,
        humpHalfSpan: Float,
        humpClearance: Float,
        older: List<ResolvedVentilationDuct>,
        joinSnapDistance: Float
    ): List<Vector3> {
        if (older.isEmpty()) {
            return listOf(Vector3(start), Vector3(end))
        }
        val basis = hvacVentilationBasis(start, end, binormalRef, width, height) ?: return listOf(Vector3(start), Vector3(end))
        val length = start.dst(end)
        if (length <= 1e-6f) {
            return listOf(Vector3(start), Vector3(end))
        }

        val crossings = collectVentilationCrossings(
            start = start,
            end = end,
            height = height,
            older = older,
            joinSnapDistance = joinSnapDistance
        )
        if (crossings.isEmpty()) {
            return listOf(Vector3(start), Vector3(end))
        }

        val halfSpanSetting = humpHalfSpan.coerceAtLeast(0.01f)
        val clearanceSetting = humpClearance.coerceAtLeast(0f)
        val rawIntervals = crossings.map { crossing ->
            val halfSpan = halfSpanSetting
            val s0 = ((crossing.s * length - halfSpan) / length).coerceIn(0f, 1f)
            val s1 = ((crossing.s * length + halfSpan) / length).coerceIn(0f, 1f)
            val lift = max(height, crossing.olderHeight).coerceAtLeast(0.05f) + clearanceSetting
            HumpInterval(s0, s1, lift)
        }.sortedBy { it.s0 }

        if (rawIntervals.isEmpty()) {
            return listOf(Vector3(start), Vector3(end))
        }

        val merged = mutableListOf<HumpInterval>()
        rawIntervals.forEach { interval ->
            val last = merged.lastOrNull()
            if (last == null || interval.s0 > last.s1 + 0.02f) {
                merged.add(interval)
            } else {
                merged[merged.lastIndex] = HumpInterval(
                    s0 = last.s0,
                    s1 = max(last.s1, interval.s1),
                    lift = max(last.lift, interval.lift)
                )
            }
        }

        val result = mutableListOf<Vector3>()
        appendUniquePoint(result, start)
        merged.forEach { interval ->
            if (interval.s1 <= interval.s0 + 1e-4f) {
                return@forEach
            }
            val p0 = Vector3(start).lerp(end, interval.s0)
            val p1 = Vector3(start).lerp(end, interval.s1)
            val up0 = Vector3(p0).mulAdd(basis.normal, interval.lift)
            val up1 = Vector3(p1).mulAdd(basis.normal, interval.lift)
            appendUniquePoint(result, p0)
            appendUniquePoint(result, up0)
            appendUniquePoint(result, up1)
            appendUniquePoint(result, p1)
        }
        appendUniquePoint(result, end)

        return result
    }

    private fun collectVentilationCrossings(
        start: Vector3,
        end: Vector3,
        height: Float,
        older: List<ResolvedVentilationDuct>,
        joinSnapDistance: Float
    ): List<VentilationCrossing> {
        val endpointTolSq = (joinSnapDistance * 0.75f) * (joinSnapDistance * 0.75f)
        val found = mutableListOf<VentilationCrossing>()
        older.forEach { duct ->
            val sharedEndpoint =
                start.dst2(duct.start) <= endpointTolSq ||
                    start.dst2(duct.end) <= endpointTolSq ||
                    end.dst2(duct.start) <= endpointTolSq ||
                    end.dst2(duct.end) <= endpointTolSq
            if (sharedEndpoint) {
                return@forEach
            }
            val path = duct.path
            if (path.size < 2) {
                return@forEach
            }
            for (i in 0 until path.lastIndex) {
                val closest = closestSegmentToSegment(start, end, path[i], path[i + 1]) ?: continue
                if (closest.s <= 0.05f || closest.s >= 0.95f || closest.t <= 0.05f || closest.t >= 0.95f) {
                    continue
                }
                val tol = max(0.05f, min(height, duct.height) * 0.5f)
                if (closest.dist2 <= tol * tol) {
                    found.add(
                        VentilationCrossing(
                            s = closest.s,
                            olderWidth = duct.width,
                            olderHeight = duct.height
                        )
                    )
                }
            }
        }
        if (found.isEmpty()) {
            return emptyList()
        }

        val sorted = found.sortedBy { it.s }
        val merged = mutableListOf<VentilationCrossing>()
        sorted.forEach { crossing ->
            val last = merged.lastOrNull()
            if (last == null || abs(crossing.s - last.s) > 0.05f) {
                merged.add(crossing)
            } else {
                merged[merged.lastIndex] = VentilationCrossing(
                    s = (last.s + crossing.s) * 0.5f,
                    olderWidth = max(last.olderWidth, crossing.olderWidth),
                    olderHeight = max(last.olderHeight, crossing.olderHeight)
                )
            }
        }
        return merged
    }

    private fun appendUniquePoint(path: MutableList<Vector3>, point: Vector3, epsilonSq: Float = 1e-6f) {
        if (path.isEmpty() || path.last().dst2(point) > epsilonSq) {
            path.add(Vector3(point))
        }
    }

    private fun projectPointToSegment(point: Vector3, a: Vector3, b: Vector3): SegmentProjection? {
        val ab = Vector3(b).sub(a)
        val len2 = ab.len2()
        if (len2 <= 1e-10f) {
            return null
        }
        val t = Vector3(point).sub(a).dot(ab) / len2
        val clamped = t.coerceIn(0f, 1f)
        val projected = Vector3(a).mulAdd(ab, clamped)
        return SegmentProjection(
            point = projected,
            t = clamped,
            dist2 = Vector3(point).sub(projected).len2()
        )
    }

    private fun closestSegmentToSegment(
        p0: Vector3,
        p1: Vector3,
        q0: Vector3,
        q1: Vector3
    ): SegmentSegmentClosest? {
        val u = Vector3(p1).sub(p0)
        val v = Vector3(q1).sub(q0)
        val w = Vector3(p0).sub(q0)
        val a = u.dot(u)
        val b = u.dot(v)
        val c = v.dot(v)
        val d = u.dot(w)
        val e = v.dot(w)
        val small = 1e-8f
        var sN: Float
        var sD = a * c - b * b
        var tN: Float
        var tD = sD

        if (a <= small || c <= small) {
            return null
        }

        if (sD < small) {
            sN = 0f
            sD = 1f
            tN = e
            tD = c
        } else {
            sN = b * e - c * d
            tN = a * e - b * d
            if (sN < 0f) {
                sN = 0f
                tN = e
                tD = c
            } else if (sN > sD) {
                sN = sD
                tN = e + b
                tD = c
            }
        }

        if (tN < 0f) {
            tN = 0f
            if (-d < 0f) {
                sN = 0f
            } else if (-d > a) {
                sN = sD
            } else {
                sN = -d
                sD = a
            }
        } else if (tN > tD) {
            tN = tD
            if (-d + b < 0f) {
                sN = 0f
            } else if (-d + b > a) {
                sN = sD
            } else {
                sN = -d + b
                sD = a
            }
        }

        val s = if (abs(sN) < small) 0f else sN / sD
        val t = if (abs(tN) < small) 0f else tN / tD
        val dP = Vector3(w).mulAdd(u, s).mulAdd(v, -t)
        return SegmentSegmentClosest(dist2 = dP.len2(), s = s, t = t)
    }

    private fun resolvedVentilation(duct: HvacStore.VentilationDuct): ResolvedVentilationDuct {
        return resolvedHvacVentilation[duct.id] ?: ResolvedVentilationDuct(
            id = duct.id,
            start = Vector3(duct.start),
            end = Vector3(duct.end),
            path = listOf(Vector3(duct.start), Vector3(duct.end)),
            binormalRef = Vector3(duct.binormalRef),
            autoJoinEnabled = duct.autoJoinEnabled,
            width = duct.width.coerceAtLeast(0.01f),
            height = duct.height.coerceAtLeast(0.01f),
            startJoined = false,
            endJoined = false
        )
    }

    private fun appendHvacPlumbingGeometry(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        run: HvacStore.PlumbingRun
    ) {
        val path = run.path
        if (path.size < 2) {
            return
        }
        val radius = (run.diameter * 0.5f).coerceAtLeast(0.005f)
        val sides = run.sides.coerceIn(3, 128)
        val tangents = computePipeTangents(path)
        if (tangents.isEmpty()) {
            return
        }
        val rings = mutableListOf<List<Vector3>>()
        var previousNormal = initialPerpendicular(tangents.first())
        for (i in path.indices) {
            val tangent = tangents[i]
            var normal = if (i == 0) {
                Vector3(previousNormal)
            } else {
                Vector3(previousNormal).sub(Vector3(tangent).scl(previousNormal.dot(tangent)))
            }
            if (normal.len2() <= 1e-6f) {
                normal = initialPerpendicular(tangent)
            }
            normal.nor()
            var binormal = Vector3(tangent).crs(normal)
            if (binormal.len2() <= 1e-6f) {
                normal = initialPerpendicular(tangent)
                binormal = Vector3(tangent).crs(normal)
            }
            binormal.nor()
            normal = Vector3(binormal).crs(tangent).nor()
            previousNormal = Vector3(normal)

            val ring = MutableList(sides) { index ->
                val angle = (2.0 * Math.PI * index.toDouble() / sides.toDouble()).toFloat()
                val radial = Vector3(normal).scl(kotlin.math.cos(angle.toDouble()).toFloat())
                    .add(Vector3(binormal).scl(kotlin.math.sin(angle.toDouble()).toFloat()))
                Vector3(path[i]).mulAdd(radial, radius)
            }
            rings.add(ring)
        }

        for (i in 0 until rings.lastIndex) {
            val ringA = rings[i]
            val ringB = rings[i + 1]
            for (j in 0 until sides) {
                val next = (j + 1) % sides
                val a = ringA[j]
                val b = ringA[next]
                val c = ringB[next]
                val d = ringB[j]
                val outward = Vector3(a).sub(path[i])
                addQuad(faceStore, lineStore, a, b, c, d, outward, run.color)
            }
        }
        addPipeCap(faceStore, lineStore, path.first(), rings.first(), Vector3(tangents.first()).scl(-1f), run.color)
        addPipeCap(faceStore, lineStore, path.last(), rings.last(), Vector3(tangents.last()), run.color)
    }

    private fun appendHvacVentilationGeometry(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        duct: HvacStore.VentilationDuct
    ) {
        val resolved = resolvedVentilation(duct)
        val path = resolved.path
        if (path.size < 2) {
            return
        }
        val rings = hvacVentilationRings(resolved)
        if (rings.size < 2) {
            return
        }
        val tangents = computePipeTangents(path)
        if (tangents.size != rings.size) {
            return
        }
        for (i in 0 until rings.lastIndex) {
            val ringA = rings[i]
            val ringB = rings[i + 1]
            val tangent = Vector3(path[i + 1]).sub(path[i]).nor()
            if (tangent.len2() <= 1e-6f) {
                continue
            }
            val binormalA = Vector3(ringA[0]).sub(path[i]).nor()
            val binormalB = Vector3(ringB[0]).sub(path[i + 1]).nor()
            val binormal = Vector3(binormalA).add(binormalB)
            if (binormal.len2() <= 1e-6f) {
                binormal.set(binormalA)
            }
            binormal.nor()
            val normal = Vector3(tangent).crs(binormal).nor()
            val capNormal = Vector3(tangent)
            addQuad(faceStore, lineStore, ringA[0], ringB[0], ringB[1], ringA[1], normal, duct.color)
            addQuad(faceStore, lineStore, ringA[3], ringA[2], ringB[2], ringB[3], Vector3(normal).scl(-1f), duct.color)
            addQuad(faceStore, lineStore, ringA[0], ringA[3], ringB[3], ringB[0], binormal, duct.color)
            addQuad(faceStore, lineStore, ringA[1], ringB[1], ringB[2], ringA[2], Vector3(binormal).scl(-1f), duct.color)
        }

        val startTangent = Vector3(tangents.first()).nor()
        val endTangent = Vector3(tangents.last()).nor()
        val startRing = rings.first()
        val endRing = rings.last()
        if (!resolved.startJoined) {
            addQuad(
                faceStore,
                lineStore,
                startRing[1],
                startRing[0],
                startRing[3],
                startRing[2],
                Vector3(startTangent).scl(-1f),
                duct.color
            )
        }
        if (!resolved.endJoined) {
            addQuad(
                faceStore,
                lineStore,
                endRing[0],
                endRing[1],
                endRing[2],
                endRing[3],
                endTangent,
                duct.color
            )
        }
    }

    private fun hvacVentilationRings(resolved: ResolvedVentilationDuct): List<List<Vector3>> {
        val path = resolved.path
        if (path.size < 2) {
            return emptyList()
        }
        val tangents = computePipeTangents(path)
        if (tangents.isEmpty()) {
            return emptyList()
        }
        val halfWidth = (resolved.width * 0.5f).coerceAtLeast(0.005f)
        val height = resolved.height.coerceAtLeast(0.005f)

        var binormal = Vector3(resolved.binormalRef).sub(path.first())
        val firstTangent = tangents.first()
        binormal.sub(Vector3(firstTangent).scl(binormal.dot(firstTangent)))
        if (binormal.len2() <= 1e-6f) {
            binormal = initialPerpendicular(firstTangent)
        }
        if (binormal.len2() <= 1e-6f) {
            return emptyList()
        }
        binormal.nor()
        var normal = Vector3(firstTangent).crs(binormal)
        if (normal.len2() <= 1e-6f) {
            normal = initialPerpendicular(firstTangent)
            binormal = Vector3(normal).crs(firstTangent)
        }
        if (normal.len2() <= 1e-6f || binormal.len2() <= 1e-6f) {
            return emptyList()
        }
        normal.nor()
        binormal.nor()

        val rings = mutableListOf<List<Vector3>>()
        for (i in path.indices) {
            val tangent = tangents[i]
            if (i > 0) {
                binormal = Vector3(binormal).sub(Vector3(tangent).scl(binormal.dot(tangent)))
                if (binormal.len2() <= 1e-6f) {
                    binormal = Vector3(normal).sub(Vector3(tangent).scl(normal.dot(tangent)))
                }
                if (binormal.len2() <= 1e-6f) {
                    binormal = initialPerpendicular(tangent)
                }
                if (binormal.len2() <= 1e-6f) {
                    return emptyList()
                }
                binormal.nor()
                normal = Vector3(tangent).crs(binormal)
                if (normal.len2() <= 1e-6f) {
                    normal = initialPerpendicular(tangent)
                    binormal = Vector3(normal).crs(tangent)
                }
                if (normal.len2() <= 1e-6f || binormal.len2() <= 1e-6f) {
                    return emptyList()
                }
                normal.nor()
                binormal.nor()
            }
            val center = path[i]
            val tl = Vector3(center).mulAdd(binormal, halfWidth)
            val tr = Vector3(center).mulAdd(binormal, -halfWidth)
            val br = Vector3(tr).mulAdd(normal, -height)
            val bl = Vector3(tl).mulAdd(normal, -height)
            var ring = listOf(tl, tr, br, bl)
            if (i == 0 && resolved.startJoinPlaneNormal != null) {
                val inside = Vector3(path[1]).sub(path[0]).nor()
                ring = applyMiterPlaneToRing(
                    ring = ring,
                    planePoint = path[0],
                    planeNormal = resolved.startJoinPlaneNormal,
                    insideDir = inside,
                    maxOffset = max(resolved.width, resolved.height) * 4f
                )
            } else if (i == path.lastIndex && resolved.endJoinPlaneNormal != null) {
                val inside = Vector3(path[path.lastIndex - 1]).sub(path[path.lastIndex]).nor()
                ring = applyMiterPlaneToRing(
                    ring = ring,
                    planePoint = path[path.lastIndex],
                    planeNormal = resolved.endJoinPlaneNormal,
                    insideDir = inside,
                    maxOffset = max(resolved.width, resolved.height) * 4f
                )
            }
            rings.add(ring)
        }
        return rings
    }

    private fun applyMiterPlaneToRing(
        ring: List<Vector3>,
        planePoint: Vector3,
        planeNormal: Vector3,
        insideDir: Vector3,
        maxOffset: Float
    ): List<Vector3> {
        if (ring.isEmpty()) {
            return ring
        }
        if (insideDir.len2() <= 1e-8f || planeNormal.len2() <= 1e-8f) {
            return ring
        }
        val dir = Vector3(insideDir).nor()
        val normal = Vector3(planeNormal).nor()
        val denom = normal.dot(dir)
        if (abs(denom) <= 1e-6f) {
            return ring
        }
        return ring.map { point ->
            val s = (-normal.dot(Vector3(point).sub(planePoint)) / denom).coerceIn(-maxOffset, maxOffset)
            Vector3(point).mulAdd(dir, s)
        }
    }

    private fun hvacVentilationBasis(duct: HvacStore.VentilationDuct): VentilationBasis? {
        val resolved = resolvedVentilation(duct)
        return hvacVentilationBasis(
            start = resolved.start,
            end = resolved.end,
            binormalRef = resolved.binormalRef,
            width = resolved.width,
            height = resolved.height
        )
    }

    private fun hvacVentilationBasis(
        start: Vector3,
        end: Vector3,
        binormalRef: Vector3,
        width: Float,
        height: Float
    ): VentilationBasis? {
        val tangent = Vector3(end).sub(start)
        val length = tangent.len()
        if (length <= 1e-6f) {
            return null
        }
        tangent.scl(1f / length)

        var binormal = Vector3(binormalRef).sub(start)
        binormal.sub(Vector3(tangent).scl(binormal.dot(tangent)))
        if (binormal.len2() <= 1e-6f) {
            binormal = initialPerpendicular(tangent)
        }
        if (binormal.len2() <= 1e-6f) {
            return null
        }
        binormal.nor()

        var normal = Vector3(tangent).crs(binormal)
        if (normal.len2() <= 1e-6f) {
            normal = initialPerpendicular(tangent)
            binormal = Vector3(normal).crs(tangent)
        }
        if (normal.len2() <= 1e-6f || binormal.len2() <= 1e-6f) {
            return null
        }
        normal.nor()
        binormal.nor()

        return VentilationBasis(
            start = Vector3(start),
            tangent = tangent,
            binormal = binormal,
            normal = normal,
            length = length,
            halfWidth = (width * 0.5f).coerceAtLeast(0.005f),
            height = height.coerceAtLeast(0.005f)
        )
    }

    private fun hvacVentilationCorners(duct: HvacStore.VentilationDuct): List<Vector3> {
        val resolved = resolvedVentilation(duct)
        return hvacVentilationRings(resolved).flatten()
    }

    private fun computePipeTangents(path: List<Vector3>): List<Vector3> {
        if (path.size < 2) {
            return emptyList()
        }
        val tangents = MutableList(path.size) { Vector3() }
        for (i in path.indices) {
            tangents[i] = when (i) {
                0 -> Vector3(path[1]).sub(path[0]).nor()
                path.lastIndex -> Vector3(path[path.lastIndex]).sub(path[path.lastIndex - 1]).nor()
                else -> {
                    val prev = Vector3(path[i]).sub(path[i - 1]).nor()
                    val next = Vector3(path[i + 1]).sub(path[i]).nor()
                    val merged = Vector3(prev).add(next)
                    if (merged.len2() <= 1e-8f) next else merged.nor()
                }
            }
            if (tangents[i].len2() <= 1e-8f) {
                tangents[i] = Vector3(1f, 0f, 0f)
            }
        }
        return tangents
    }

    private fun initialPerpendicular(direction: Vector3): Vector3 {
        var normal = Vector3(0f, 1f, 0f).sub(Vector3(direction).scl(direction.y))
        if (normal.len2() <= 1e-6f) {
            normal = Vector3(1f, 0f, 0f).sub(Vector3(direction).scl(direction.x))
        }
        if (normal.len2() <= 1e-6f) {
            normal = Vector3(0f, 0f, 1f).sub(Vector3(direction).scl(direction.z))
        }
        return normal.nor()
    }

    private fun addPipeCap(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        center: Vector3,
        ring: List<Vector3>,
        outward: Vector3,
        color: Color
    ) {
        if (ring.size < 3) {
            return
        }
        for (i in ring.indices) {
            val next = (i + 1) % ring.size
            val p0 = Vector3(center)
            val p1 = Vector3(ring[i])
            val p2 = Vector3(ring[next])
            val normal = Vector3(p1).sub(p0).crs(Vector3(p2).sub(p0))
            if (normal.dot(outward) >= 0f) {
                faceStore.addTriangle(p0, p1, p2, color)
            } else {
                faceStore.addTriangle(p0, p2, p1, color)
            }
            lineStore.addSegment(Vector3(ring[i]), Vector3(ring[next]), autoCleanup = false)
        }
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

    private fun addDoubleSidedQuad(
        faceStore: DraftFaceStore,
        lineStore: DraftLineStore,
        p0: Vector3,
        p1: Vector3,
        p2: Vector3,
        p3: Vector3,
        color: Color
    ) {
        faceStore.addTriangle(Vector3(p0), Vector3(p1), Vector3(p2), color)
        faceStore.addTriangle(Vector3(p0), Vector3(p2), Vector3(p3), color)
        faceStore.addTriangle(Vector3(p0), Vector3(p2), Vector3(p1), color)
        faceStore.addTriangle(Vector3(p0), Vector3(p3), Vector3(p2), color)
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
        clone.lineStoreOverride = group.lineStoreOverride?.let { cloneLineStore(it) }
        clone.faceStoreOverride = group.faceStoreOverride?.let { cloneFaceStore(it) }
        clone.dimensionStoreOverride = group.dimensionStoreOverride?.let { cloneDimensionStore(it) }
        clone.textStoreOverride = group.textStoreOverride?.let { cloneTextStore(it) }
        group.hotspotPositionOverrides.forEach { (hotspotId, position) ->
            clone.hotspotPositionOverrides[hotspotId] = Vector3(position)
        }
        group.runtimeHotspotPositions.forEach { (hotspotId, position) ->
            clone.runtimeHotspotPositions[hotspotId] = Vector3(position)
        }
        group.hotspotAttachedSegmentOverrides.forEach { (hotspotId, refs) ->
            clone.hotspotAttachedSegmentOverrides[hotspotId] = refs.toMutableSet()
        }
        group.hotspotAttachedTriangleOverrides.forEach { (hotspotId, refs) ->
            clone.hotspotAttachedTriangleOverrides[hotspotId] = refs.toMutableSet()
        }
        clone.hotspotSelectionIds.addAll(group.hotspotSelectionIds)
        group.children.forEach { child ->
            val childClone = cloneGroup(child)
            childClone.parent = clone
            clone.children.add(childClone)
        }
        applyChangeListener(clone)
        return clone
    }

    private fun cloneGroupStructure(group: GroupNode): GroupNode {
        val clone = GroupNode(
            id = java.util.UUID.randomUUID().toString(),
            prototype = group.prototype,
            instanceOrigin = Vector3(group.instanceOrigin),
            instanceAxisU = Vector3(group.instanceAxisU),
            instanceAxisV = Vector3(group.instanceAxisV),
            instanceAxisW = Vector3(group.instanceAxisW)
        )
        group.children.forEach { child ->
            val childClone = cloneGroupStructure(child)
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
        group.prototype.lineStore.setChangeListener(listener)
        group.prototype.faceStore.setChangeListener(listener)
        group.prototype.dimensionStore.setChangeListener(listener)
        group.prototype.textStore.setChangeListener(listener)
        group.hotspotStore.setChangeListener(listener)
        group.lineStoreOverride?.setChangeListener(listener)
        group.faceStoreOverride?.setChangeListener(listener)
        group.dimensionStoreOverride?.setChangeListener(listener)
        group.textStoreOverride?.setChangeListener(listener)
    }

    private fun notifyChange() {
        groupSpatialIndexDirty = true
        changeListener?.invoke()
    }

    private fun ensureGroupSpatialIndex() {
        if (!groupSpatialIndexDirty) {
            return
        }
        groupSpatialIndex = SpatialHash3D(GROUP_SPATIAL_HASH_CELL_SIZE) { it }
        groupSpatialBoundsById.clear()
        hasGroupSpatialBounds = false
        fun register(group: GroupNode) {
            val bounds = group.geometryWorldBounds() ?: return
            val boundsMin = bounds.min.cpy()
            val boundsMax = bounds.max.cpy()
            groupSpatialIndex.insertAabb(boundsMin, boundsMax, group.id)
            groupSpatialBoundsById[group.id] = BoundingBox(boundsMin.cpy(), boundsMax.cpy())
            if (!hasGroupSpatialBounds) {
                groupSpatialBoundsMin.set(boundsMin)
                groupSpatialBoundsMax.set(boundsMax)
                hasGroupSpatialBounds = true
            } else {
                groupSpatialBoundsMin.x = min(groupSpatialBoundsMin.x, boundsMin.x)
                groupSpatialBoundsMin.y = min(groupSpatialBoundsMin.y, boundsMin.y)
                groupSpatialBoundsMin.z = min(groupSpatialBoundsMin.z, boundsMin.z)
                groupSpatialBoundsMax.x = max(groupSpatialBoundsMax.x, boundsMax.x)
                groupSpatialBoundsMax.y = max(groupSpatialBoundsMax.y, boundsMax.y)
                groupSpatialBoundsMax.z = max(groupSpatialBoundsMax.z, boundsMax.z)
            }
        }
        register(root)
        walkGroups(root) { group -> register(group) }
        groupSpatialIndexDirty = false
    }

    private fun findGroupById(id: String): GroupNode? {
        if (root.id == id) {
            return root
        }
        var found: GroupNode? = null
        walkGroups(root) { group ->
            if (found == null && group.id == id) {
                found = group
            }
        }
        return found
    }

    private fun colorsEqual(a: Color, b: Color): Boolean {
        return kotlin.math.abs(a.r - b.r) <= 1e-6f &&
            kotlin.math.abs(a.g - b.g) <= 1e-6f &&
            kotlin.math.abs(a.b - b.b) <= 1e-6f &&
            kotlin.math.abs(a.a - b.a) <= 1e-6f
    }

    companion object {
        private const val GROUP_SPATIAL_HASH_CELL_SIZE = 10f

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
        root.lineStoreOverride = null
        root.faceStoreOverride = null
        root.dimensionStoreOverride = null
        root.textStoreOverride = null
        root.hotspotPositionOverrides.clear()
        root.runtimeHotspotPositions.clear()
        root.hotspotAttachedSegmentOverrides.clear()
        root.hotspotAttachedTriangleOverrides.clear()
        root.hotspotSelectionIds.clear()
        root.lineStore.clearAll()
        root.faceStore.clearAll()
        root.dimensionStore.clearAll()
        root.textStore.clearAll()
        rootPrototype.prototypeVertexIds.clear()
        modelArchitectureStore.clear()
        modelHvacStore.clear()
        generatedArchitectureLines.clear()
        generatedArchitectureFaces.clear()
        generatedArchitectureLineOwners.clear()
        generatedArchitectureFaceOwners.clear()
        selectedArchitectureHole = null
        selectedArchitectureSlabHole = null
        generatedHvacLines.clear()
        generatedHvacFaces.clear()
        generatedHvacLineOwners.clear()
        generatedHvacFaceOwners.clear()
        resolvedHvacVentilation.clear()
        clearGroupSelection()
        if (activeGroup != root) {
            activeGroup.editPrototypeMode = false
        }
        activeGroup = root
        activeGroup.editPrototypeMode = false
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

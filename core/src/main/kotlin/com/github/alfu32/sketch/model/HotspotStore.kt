package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import kotlin.math.roundToInt
import java.util.UUID

class HotspotStore {
    enum class OperationKind {
        MOVE,
        SCALE,
        STRETCH,
        ROTATE,
        MULTIPLY_LINEAR,
        MULTIPLY_VOLUMETRIC,
        MULTIPLY_ROTATE_2D,
        MULTIPLY_ROTATE_3D
    }

    enum class ShapeKind {
        TRIANGLE_UP,
        SQUARE,
        CIRCLE,
        DIAMOND,
        TRIANGLE_DOWN
    }

    data class VertexKey(val x: Int, val y: Int, val z: Int)

    data class SegmentRef(
        val id: String = "",
        val a: VertexKey = VertexKey(0, 0, 0),
        val b: VertexKey = VertexKey(0, 0, 0)
    )

    data class TriangleRef(
        val id: String = "",
        val a: VertexKey = VertexKey(0, 0, 0),
        val b: VertexKey = VertexKey(0, 0, 0),
        val c: VertexKey = VertexKey(0, 0, 0)
    )

    data class Hotspot(
        val id: String,
        var name: String,
        var position: Vector3,
        var operation: OperationKind,
        var shape: ShapeKind = ShapeKind.CIRCLE,
        var color: Color = Color(0.2f, 0.55f, 0.95f, 1f),
        var referencePosition: Vector3? = null,
        var attachedHotspotIds: MutableSet<String> = linkedSetOf(),
        var attachedVertexIds: MutableSet<String> = linkedSetOf(),
        var attachedSegments: MutableSet<SegmentRef> = linkedSetOf(),
        var attachedTriangles: MutableSet<TriangleRef> = linkedSetOf()
    )

    data class HotspotSelection(val id: String)

    private val hotspots = mutableListOf<Hotspot>()
    private val selected = linkedSetOf<HotspotSelection>()
    private var hotspotNameCounter = 1
    private var onChange: (() -> Unit)? = null
    private var suppressChange = false
    private val keyEps = 1e-3f

    fun setChangeListener(listener: () -> Unit) {
        onChange = listener
    }

    fun withChangeSuppressed(block: () -> Unit) {
        val prev = suppressChange
        suppressChange = true
        try {
            block()
        } finally {
            suppressChange = prev
        }
    }

    fun allHotspots(): List<Hotspot> = hotspots

    fun hotspotById(id: String): Hotspot? = hotspots.firstOrNull { it.id == id }

    fun selectedHotspots(): Set<HotspotSelection> = selected

    fun selectedHotspot(): HotspotSelection? = selected.lastOrNull()

    fun clearSelected() {
        if (selected.isNotEmpty()) {
            selected.clear()
            notifyChange()
        }
    }

    fun setSelected(id: String): Boolean {
        if (hotspotById(id) == null) {
            clearSelected()
            return false
        }
        selected.clear()
        selected.add(HotspotSelection(id))
        notifyChange()
        return true
    }

    fun addSelected(id: String): Boolean {
        if (hotspotById(id) == null) {
            return false
        }
        val changed = selected.add(HotspotSelection(id))
        notifyChange()
        return changed
    }

    fun removeSelected(id: String): Boolean {
        val changed = selected.remove(HotspotSelection(id))
        if (changed) {
            notifyChange()
        }
        return changed
    }

    fun isSelected(id: String): Boolean = selected.contains(HotspotSelection(id))

    fun addHotspot(
        position: Vector3,
        operation: OperationKind,
        shape: ShapeKind = ShapeKind.CIRCLE,
        color: Color = Color(0.2f, 0.55f, 0.95f, 1f),
        referencePosition: Vector3? = null,
        attachedHotspotIds: Set<String> = emptySet(),
        attachedVertexIds: Set<String> = emptySet(),
        attachedSegments: Set<SegmentRef> = emptySet(),
        attachedTriangles: Set<TriangleRef> = emptySet(),
        name: String = "",
        id: String = UUID.randomUUID().toString()
    ): Hotspot {
        val hotspot = Hotspot(
            id = id,
            name = nextName(name),
            position = Vector3(position),
            operation = operation,
            shape = shape,
            color = Color(color),
            referencePosition = referencePosition?.let { Vector3(it) },
            attachedHotspotIds = attachedHotspotIds.toMutableSet(),
            attachedVertexIds = attachedVertexIds.toMutableSet(),
            attachedSegments = attachedSegments.toMutableSet(),
            attachedTriangles = attachedTriangles.toMutableSet()
        )
        hotspots.add(hotspot)
        notifyChange()
        return hotspot
    }

    fun deleteSelected(): Int {
        if (selected.isEmpty()) {
            return 0
        }
        val before = hotspots.size
        hotspots.removeAll { hotspot -> selected.contains(HotspotSelection(hotspot.id)) }
        selected.clear()
        val removed = before - hotspots.size
        if (removed > 0) {
            notifyChange()
        }
        return removed
    }

    fun deleteByIds(ids: Set<String>): Int {
        if (ids.isEmpty()) {
            return 0
        }
        val before = hotspots.size
        hotspots.removeAll { hotspot -> ids.contains(hotspot.id) }
        hotspots.forEach { hotspot ->
            hotspot.attachedHotspotIds.removeAll(ids)
        }
        selected.removeAll { selection -> ids.contains(selection.id) }
        val removed = before - hotspots.size
        if (removed > 0) {
            notifyChange()
        }
        return removed
    }

    fun clearAll() {
        if (hotspots.isEmpty() && selected.isEmpty()) {
            return
        }
        hotspots.clear()
        selected.clear()
        hotspotNameCounter = 1
        notifyChange()
    }

    fun updatePosition(id: String, position: Vector3): Boolean {
        val hotspot = hotspotById(id) ?: return false
        hotspot.position.set(position)
        notifyChange()
        return true
    }

    fun updateOperation(id: String, operation: OperationKind): Boolean {
        val hotspot = hotspotById(id) ?: return false
        hotspot.operation = operation
        notifyChange()
        return true
    }

    fun updateShape(id: String, shape: ShapeKind): Boolean {
        val hotspot = hotspotById(id) ?: return false
        hotspot.shape = shape
        notifyChange()
        return true
    }

    fun updateColor(id: String, color: Color): Boolean {
        val hotspot = hotspotById(id) ?: return false
        hotspot.color.set(color)
        notifyChange()
        return true
    }

    fun updateReference(id: String, referencePosition: Vector3?): Boolean {
        val hotspot = hotspotById(id) ?: return false
        hotspot.referencePosition = referencePosition?.let { Vector3(it) }
        notifyChange()
        return true
    }

    fun updateAttachments(
        id: String,
        hotspotIds: Set<String>,
        vertexIds: Set<String>,
        segmentRefs: Set<SegmentRef>,
        triangleRefs: Set<TriangleRef>
    ): Boolean {
        val hotspot = hotspotById(id) ?: return false
        hotspot.attachedHotspotIds = hotspotIds.filterNot { it == id }.toMutableSet()
        hotspot.attachedVertexIds = vertexIds.toMutableSet()
        hotspot.attachedSegments = segmentRefs.toMutableSet()
        hotspot.attachedTriangles = triangleRefs.toMutableSet()
        notifyChange()
        return true
    }

    fun updateAttachments(
        id: String,
        segmentRefs: Set<SegmentRef>,
        triangleRefs: Set<TriangleRef>
    ): Boolean {
        return updateAttachments(id, emptySet(), emptySet(), segmentRefs, triangleRefs)
    }

    fun updateName(id: String, name: String): Boolean {
        val hotspot = hotspotById(id) ?: return false
        hotspot.name = nextName(name, hotspot.id)
        notifyChange()
        return true
    }

    fun notifyExternalChange() {
        notifyChange()
    }

    fun segmentRef(segment: DraftLineStore.Segment): SegmentRef {
        val a = vertexKey(segment.start)
        val b = vertexKey(segment.end)
        return if (compareVertexKeys(a, b) <= 0) {
            SegmentRef(segment.id, a, b)
        } else {
            SegmentRef(segment.id, b, a)
        }
    }

    fun triangleRef(triangle: DraftFaceStore.Triangle): TriangleRef {
        val keys = listOf(vertexKey(triangle.a), vertexKey(triangle.b), vertexKey(triangle.c))
            .sortedWith { a, b -> compareVertexKeys(a, b) }
        return TriangleRef(triangle.id, keys[0], keys[1], keys[2])
    }

    private fun nextName(candidate: String, ignoreId: String? = null): String {
        val trimmed = candidate.trim()
        if (trimmed.isNotEmpty()) {
            return trimmed
        }
        var index = hotspotNameCounter
        var generated = "HOTSPOT_$index"
        while (hotspots.any { it.id != ignoreId && it.name == generated }) {
            index++
            generated = "HOTSPOT_$index"
        }
        hotspotNameCounter = index + 1
        return generated
    }

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(
            (point.x / keyEps).roundToInt(),
            (point.y / keyEps).roundToInt(),
            (point.z / keyEps).roundToInt()
        )
    }

    private fun compareVertexKeys(a: VertexKey, b: VertexKey): Int {
        if (a.x != b.x) return a.x.compareTo(b.x)
        if (a.y != b.y) return a.y.compareTo(b.y)
        return a.z.compareTo(b.z)
    }

    private fun notifyChange() {
        if (!suppressChange) {
            onChange?.invoke()
        }
    }
}

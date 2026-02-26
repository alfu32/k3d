package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class ArchitectureStore {
    enum class FrameKind {
        WINDOW,
        DOOR
    }

    enum class ElementKind {
        WALL,
        SLAB,
        STAIR,
        FRAME
    }

    data class ElementSelection(val kind: ElementKind, val id: String)

    data class RectHole(
        var id: String,
        var name: String,
        var u0: Float,
        var u1: Float,
        var v0: Float,
        var v1: Float
    )

    data class WallSegment(
        var id: String,
        var name: String,
        var start: Vector3,
        var end: Vector3,
        var thickness: Float,
        var height: Float,
        var inclinationDeg: Float,
        var exteriorColor: Color,
        var interiorColor: Color,
        val holes: MutableList<RectHole> = mutableListOf()
    )

    data class Slab(
        var id: String,
        var name: String,
        var min: Vector3,
        var max: Vector3,
        var axisU: Vector3,
        var axisV: Vector3,
        var normal: Vector3,
        var thickness: Float,
        var topColor: Color,
        var bottomColor: Color,
        var sideColor: Color,
        val holes: MutableList<RectHole> = mutableListOf()
    )

    data class Stair(
        var id: String,
        var name: String,
        var min: Vector3,
        var max: Vector3,
        var contour: MutableList<Vector3>,
        var walkingPath: MutableList<Vector3>,
        var walkingStart: Vector3,
        var walkingEnd: Vector3,
        var height: Float,
        var stepCount: Int,
        var supportThickness: Float,
        var railLeftEnabled: Boolean,
        var railRightEnabled: Boolean,
        var treadColor: Color,
        var supportColor: Color
    )

    data class Frame(
        var id: String,
        var name: String,
        var cornerA: Vector3,
        var cornerB: Vector3,
        var normal: Vector3,
        var depth: Float,
        var frameWidth: Float,
        var kind: FrameKind,
        var color: Color,
        var glazingEnabled: Boolean,
        var glazingColor: Color
    )

    private val walls = mutableListOf<WallSegment>()
    private val slabs = mutableListOf<Slab>()
    private val stairs = mutableListOf<Stair>()
    private val frames = mutableListOf<Frame>()
    private var wallNameCounter = 1
    private var slabNameCounter = 1
    private var stairNameCounter = 1
    private var frameNameCounter = 1
    private var holeNameCounter = 1
    private var selectedElement: ElementSelection? = null
    private val selectedElements = linkedSetOf<ElementSelection>()

    fun allWalls(): List<WallSegment> = walls

    fun allSlabs(): List<Slab> = slabs

    fun allStairs(): List<Stair> = stairs

    fun allFrames(): List<Frame> = frames

    fun selectedElement(): ElementSelection? = selectedElement

    fun selectedElements(): Set<ElementSelection> = selectedElements

    fun wallById(id: String): WallSegment? = walls.firstOrNull { it.id == id }

    fun selectedWall(): WallSegment? {
        val selection = selectedElement ?: return null
        if (selection.kind != ElementKind.WALL) {
            return null
        }
        return wallById(selection.id)
    }

    fun clearSelectedElement() {
        selectedElement = null
        selectedElements.clear()
    }

    fun deleteSelectedElement(): Boolean {
        return deleteSelectedElements() > 0
    }

    fun deleteSelectedElements(): Int {
        val targets = selectedElements.toList()
        if (targets.isEmpty()) {
            return 0
        }
        var removed = 0
        targets.forEach { selection ->
            val deleted = when (selection.kind) {
                ElementKind.WALL -> walls.removeIf { it.id == selection.id }
                ElementKind.SLAB -> slabs.removeIf { it.id == selection.id }
                ElementKind.STAIR -> stairs.removeIf { it.id == selection.id }
                ElementKind.FRAME -> frames.removeIf { it.id == selection.id }
            }
            if (deleted) {
                removed++
            }
        }
        selectedElements.clear()
        selectedElement = null
        return removed
    }

    fun setSelectedElement(kind: ElementKind, id: String): Boolean {
        val exists = when (kind) {
            ElementKind.WALL -> walls.any { it.id == id }
            ElementKind.SLAB -> slabs.any { it.id == id }
            ElementKind.STAIR -> stairs.any { it.id == id }
            ElementKind.FRAME -> frames.any { it.id == id }
        }
        if (!exists) {
            selectedElement = null
            selectedElements.clear()
            return false
        }
        val selection = ElementSelection(kind, id)
        selectedElement = selection
        selectedElements.clear()
        selectedElements.add(selection)
        return true
    }

    fun addSelectedElement(kind: ElementKind, id: String): Boolean {
        val exists = when (kind) {
            ElementKind.WALL -> walls.any { it.id == id }
            ElementKind.SLAB -> slabs.any { it.id == id }
            ElementKind.STAIR -> stairs.any { it.id == id }
            ElementKind.FRAME -> frames.any { it.id == id }
        }
        if (!exists) {
            return false
        }
        val selection = ElementSelection(kind, id)
        selectedElement = selection
        return selectedElements.add(selection)
    }

    fun removeSelectedElement(kind: ElementKind, id: String): Boolean {
        val selection = ElementSelection(kind, id)
        val removed = selectedElements.remove(selection)
        if (selectedElement == selection) {
            selectedElement = selectedElements.lastOrNull()
        }
        return removed
    }

    fun isSelectedElement(kind: ElementKind, id: String): Boolean {
        return selectedElements.contains(ElementSelection(kind, id))
    }

    fun addWall(
        start: Vector3,
        end: Vector3,
        thickness: Float,
        height: Float,
        inclinationDeg: Float,
        exteriorColor: Color = Color(0.93f, 0.93f, 0.93f, 1f),
        interiorColor: Color = Color(0.84f, 0.84f, 0.84f, 1f),
        name: String = "",
        id: String = UUID.randomUUID().toString()
    ): WallSegment {
        val wall = WallSegment(
            id = id,
            name = nextWallName(name),
            start = Vector3(start),
            end = Vector3(end),
            thickness = thickness,
            height = height,
            inclinationDeg = inclinationDeg,
            exteriorColor = Color(exteriorColor),
            interiorColor = Color(interiorColor)
        )
        walls.add(wall)
        return wall
    }

    fun addSlab(
        minCorner: Vector3,
        maxCorner: Vector3,
        thickness: Float,
        topColor: Color = Color(0.93f, 0.93f, 0.93f, 1f),
        bottomColor: Color = Color(0.84f, 0.84f, 0.84f, 1f),
        sideColor: Color = Color(0.88f, 0.88f, 0.88f, 1f),
        name: String = "",
        id: String = UUID.randomUUID().toString()
    ): Slab {
        val min = Vector3(
            min(minCorner.x, maxCorner.x),
            min(minCorner.y, maxCorner.y),
            min(minCorner.z, maxCorner.z)
        )
        val max = Vector3(
            max(minCorner.x, maxCorner.x),
            max(minCorner.y, maxCorner.y),
            max(minCorner.z, maxCorner.z)
        )
        val slab = Slab(
            id = id,
            name = nextSlabName(name),
            min = min,
            max = max,
            axisU = Vector3(1f, 0f, 0f),
            axisV = Vector3(0f, 0f, 1f),
            normal = Vector3(0f, 1f, 0f),
            thickness = thickness,
            topColor = Color(topColor),
            bottomColor = Color(bottomColor),
            sideColor = Color(sideColor)
        )
        slabs.add(slab)
        return slab
    }

    fun addSlabHole(
        slabId: String,
        u0: Float,
        u1: Float,
        v0: Float,
        v1: Float,
        minSize: Float = 0.05f,
        name: String = "",
        id: String = UUID.randomUUID().toString()
    ): RectHole? {
        val slab = slabs.firstOrNull { it.id == slabId } ?: return null
        val holeU0 = min(u0, u1)
        val holeU1 = max(u0, u1)
        val holeV0 = min(v0, v1)
        val holeV1 = max(v0, v1)
        if (holeU1 - holeU0 < minSize || holeV1 - holeV0 < minSize) {
            return null
        }
        val hole = RectHole(
            id = id,
            name = nextHoleName(name),
            u0 = holeU0,
            u1 = holeU1,
            v0 = holeV0,
            v1 = holeV1
        )
        slab.holes.add(hole)
        return hole
    }

    fun addStair(
        minCorner: Vector3,
        maxCorner: Vector3,
        contourPoints: List<Vector3>,
        walkingPathPoints: List<Vector3>,
        walkingStart: Vector3,
        walkingEnd: Vector3,
        height: Float,
        stepCount: Int,
        supportThickness: Float,
        railLeftEnabled: Boolean = true,
        railRightEnabled: Boolean = true,
        treadColor: Color = Color(0.93f, 0.93f, 0.93f, 1f),
        supportColor: Color = Color(0.82f, 0.82f, 0.82f, 1f),
        name: String = "",
        id: String = UUID.randomUUID().toString()
    ): Stair {
        val min = Vector3(
            min(minCorner.x, maxCorner.x),
            min(minCorner.y, maxCorner.y),
            min(minCorner.z, maxCorner.z)
        )
        val max = Vector3(
            max(minCorner.x, maxCorner.x),
            max(minCorner.y, maxCorner.y),
            max(minCorner.z, maxCorner.z)
        )
        val contour = if (contourPoints.size >= 3) {
            contourPoints.map { Vector3(it) }
        } else {
            listOf(
                Vector3(min.x, min.y, min.z),
                Vector3(max.x, min.y, min.z),
                Vector3(max.x, min.y, max.z),
                Vector3(min.x, min.y, max.z)
            )
        }
        val walkingPath = if (walkingPathPoints.size >= 2) {
            walkingPathPoints.map { Vector3(it) }
        } else {
            listOf(Vector3(walkingStart), Vector3(walkingEnd))
        }
        val stair = Stair(
            id = id,
            name = nextStairName(name),
            min = min,
            max = max,
            contour = contour.toMutableList(),
            walkingPath = walkingPath.toMutableList(),
            walkingStart = Vector3(walkingPath.first()),
            walkingEnd = Vector3(walkingPath.last()),
            height = height,
            stepCount = stepCount.coerceAtLeast(1),
            supportThickness = supportThickness,
            railLeftEnabled = railLeftEnabled,
            railRightEnabled = railRightEnabled,
            treadColor = Color(treadColor),
            supportColor = Color(supportColor)
        )
        stairs.add(stair)
        return stair
    }

    fun addFrame(
        cornerA: Vector3,
        cornerB: Vector3,
        normal: Vector3,
        depth: Float,
        frameWidth: Float,
        kind: FrameKind,
        color: Color = Color(0.9f, 0.9f, 0.9f, 1f),
        glazingEnabled: Boolean = kind == FrameKind.WINDOW,
        glazingColor: Color = Color(0.72f, 0.84f, 0.95f, 0.40f),
        name: String = "",
        id: String = UUID.randomUUID().toString()
    ): Frame {
        val frame = Frame(
            id = id,
            name = nextFrameName(name),
            cornerA = Vector3(cornerA),
            cornerB = Vector3(cornerB),
            normal = Vector3(normal),
            depth = depth,
            frameWidth = frameWidth,
            kind = kind,
            color = Color(color),
            glazingEnabled = glazingEnabled,
            glazingColor = Color(glazingColor)
        )
        frames.add(frame)
        return frame
    }

    fun addHole(
        wallId: String,
        u0: Float,
        u1: Float,
        v0: Float,
        v1: Float,
        minSize: Float = 0.05f,
        name: String = "",
        id: String = UUID.randomUUID().toString()
    ): RectHole? {
        val wall = walls.firstOrNull { it.id == wallId } ?: return null
        val holeU0 = min(u0, u1)
        val holeU1 = max(u0, u1)
        val holeV0 = min(v0, v1)
        val holeV1 = max(v0, v1)
        if (holeU1 - holeU0 < minSize || holeV1 - holeV0 < minSize) {
            return null
        }
        val hole = RectHole(
            id = id,
            name = nextHoleName(name),
            u0 = holeU0,
            u1 = holeU1,
            v0 = holeV0,
            v1 = holeV1
        )
        wall.holes.add(hole)
        return hole
    }

    fun removeHole(wallId: String, holeId: String): Boolean {
        val wall = walls.firstOrNull { it.id == wallId } ?: return false
        val before = wall.holes.size
        wall.holes.removeAll { it.id == holeId }
        return wall.holes.size != before
    }

    fun removeSlabHole(slabId: String, holeId: String): Boolean {
        val slab = slabs.firstOrNull { it.id == slabId } ?: return false
        val before = slab.holes.size
        slab.holes.removeAll { it.id == holeId }
        return slab.holes.size != before
    }

    fun updateHole(
        wallId: String,
        holeId: String,
        u0: Float,
        u1: Float,
        v0: Float,
        v1: Float,
        minSize: Float = 0.05f
    ): Boolean {
        val wall = walls.firstOrNull { it.id == wallId } ?: return false
        val hole = wall.holes.firstOrNull { it.id == holeId } ?: return false
        val nextU0 = min(u0, u1)
        val nextU1 = max(u0, u1)
        val nextV0 = min(v0, v1)
        val nextV1 = max(v0, v1)
        if (nextU1 - nextU0 < minSize || nextV1 - nextV0 < minSize) {
            return false
        }
        hole.u0 = nextU0
        hole.u1 = nextU1
        hole.v0 = nextV0
        hole.v1 = nextV1
        return true
    }

    fun updateSlabHole(
        slabId: String,
        holeId: String,
        u0: Float,
        u1: Float,
        v0: Float,
        v1: Float,
        minSize: Float = 0.05f
    ): Boolean {
        val slab = slabs.firstOrNull { it.id == slabId } ?: return false
        val hole = slab.holes.firstOrNull { it.id == holeId } ?: return false
        val nextU0 = min(u0, u1)
        val nextU1 = max(u0, u1)
        val nextV0 = min(v0, v1)
        val nextV1 = max(v0, v1)
        if (nextU1 - nextU0 < minSize || nextV1 - nextV0 < minSize) {
            return false
        }
        hole.u0 = nextU0
        hole.u1 = nextU1
        hole.v0 = nextV0
        hole.v1 = nextV1
        return true
    }

    fun removeHoles(predicate: (wall: WallSegment, hole: RectHole) -> Boolean): Int {
        var removed = 0
        walls.forEach { wall ->
            val before = wall.holes.size
            wall.holes.removeIf { hole -> predicate(wall, hole) }
            removed += before - wall.holes.size
        }
        return removed
    }

    fun removeSlabHoles(predicate: (slab: Slab, hole: RectHole) -> Boolean): Int {
        var removed = 0
        slabs.forEach { slab ->
            val before = slab.holes.size
            slab.holes.removeIf { hole -> predicate(slab, hole) }
            removed += before - slab.holes.size
        }
        return removed
    }

    fun clear() {
        walls.clear()
        slabs.clear()
        stairs.clear()
        frames.clear()
        wallNameCounter = 1
        slabNameCounter = 1
        stairNameCounter = 1
        frameNameCounter = 1
        holeNameCounter = 1
        selectedElement = null
        selectedElements.clear()
    }

    fun updateWall(
        id: String,
        thickness: Float,
        height: Float,
        inclinationDeg: Float,
        exteriorColor: Color,
        interiorColor: Color
    ): Boolean {
        val wall = walls.firstOrNull { it.id == id } ?: return false
        wall.thickness = thickness
        wall.height = height
        wall.inclinationDeg = inclinationDeg
        wall.exteriorColor.set(exteriorColor)
        wall.interiorColor.set(interiorColor)
        return true
    }

    fun updateWallEndpoints(id: String, start: Vector3, end: Vector3): Boolean {
        val wall = walls.firstOrNull { it.id == id } ?: return false
        wall.start.set(start)
        wall.end.set(end)
        return true
    }

    fun updateSlab(
        id: String,
        thickness: Float,
        topColor: Color,
        bottomColor: Color,
        sideColor: Color
    ): Boolean {
        val slab = slabs.firstOrNull { it.id == id } ?: return false
        slab.thickness = thickness
        slab.topColor.set(topColor)
        slab.bottomColor.set(bottomColor)
        slab.sideColor.set(sideColor)
        return true
    }

    fun updateSlabCorners(id: String, minCorner: Vector3, maxCorner: Vector3): Boolean {
        val slab = slabs.firstOrNull { it.id == id } ?: return false
        slab.min.set(minCorner)
        slab.max.set(maxCorner)
        return true
    }

    fun updateStair(
        id: String,
        height: Float,
        stepCount: Int,
        supportThickness: Float,
        railLeftEnabled: Boolean,
        railRightEnabled: Boolean,
        treadColor: Color,
        supportColor: Color
    ): Boolean {
        val stair = stairs.firstOrNull { it.id == id } ?: return false
        stair.height = height
        stair.stepCount = stepCount.coerceAtLeast(1)
        stair.supportThickness = supportThickness
        stair.railLeftEnabled = railLeftEnabled
        stair.railRightEnabled = railRightEnabled
        stair.treadColor.set(treadColor)
        stair.supportColor.set(supportColor)
        return true
    }

    fun updateFrame(
        id: String,
        depth: Float,
        frameWidth: Float,
        color: Color,
        glazingEnabled: Boolean,
        glazingColor: Color
    ): Boolean {
        val frame = frames.firstOrNull { it.id == id } ?: return false
        frame.depth = depth
        frame.frameWidth = frameWidth
        frame.color.set(color)
        frame.glazingEnabled = glazingEnabled
        frame.glazingColor.set(glazingColor)
        return true
    }

    fun updateFrameCorners(id: String, cornerA: Vector3, cornerB: Vector3): Boolean {
        val frame = frames.firstOrNull { it.id == id } ?: return false
        frame.cornerA.set(cornerA)
        frame.cornerB.set(cornerB)
        return true
    }

    fun updateWallName(id: String, name: String): Boolean {
        val wall = walls.firstOrNull { it.id == id } ?: return false
        wall.name = name.trim().ifBlank { wall.name }
        return true
    }

    fun updateSlabName(id: String, name: String): Boolean {
        val slab = slabs.firstOrNull { it.id == id } ?: return false
        slab.name = name.trim().ifBlank { slab.name }
        return true
    }

    fun updateStairName(id: String, name: String): Boolean {
        val stair = stairs.firstOrNull { it.id == id } ?: return false
        stair.name = name.trim().ifBlank { stair.name }
        return true
    }

    fun updateFrameName(id: String, name: String): Boolean {
        val frame = frames.firstOrNull { it.id == id } ?: return false
        frame.name = name.trim().ifBlank { frame.name }
        return true
    }

    fun updateHoleName(wallId: String, holeId: String, name: String): Boolean {
        val wall = walls.firstOrNull { it.id == wallId } ?: return false
        val hole = wall.holes.firstOrNull { it.id == holeId } ?: return false
        hole.name = name.trim().ifBlank { hole.name }
        return true
    }

    fun updateSlabHoleName(slabId: String, holeId: String, name: String): Boolean {
        val slab = slabs.firstOrNull { it.id == slabId } ?: return false
        val hole = slab.holes.firstOrNull { it.id == holeId } ?: return false
        hole.name = name.trim().ifBlank { hole.name }
        return true
    }

    private fun nextWallName(candidate: String): String = nextName(candidate, "WALL_") { proposed ->
        walls.none { it.name == proposed }
    }

    private fun nextSlabName(candidate: String): String = nextName(candidate, "SLAB_") { proposed ->
        slabs.none { it.name == proposed }
    }

    private fun nextStairName(candidate: String): String = nextName(candidate, "STAIR_") { proposed ->
        stairs.none { it.name == proposed }
    }

    private fun nextFrameName(candidate: String): String = nextName(candidate, "FRAME_") { proposed ->
        frames.none { it.name == proposed }
    }

    private fun nextHoleName(candidate: String): String {
        return nextName(candidate, "HOLE_") { proposed ->
            walls.none { wall -> wall.holes.any { it.name == proposed } } &&
                slabs.none { slab -> slab.holes.any { it.name == proposed } }
        }
    }

    private fun nextName(candidate: String, prefix: String, isAvailable: (String) -> Boolean): String {
        val trimmed = candidate.trim()
        if (trimmed.isNotEmpty()) {
            return trimmed
        }
        var index = when (prefix) {
            "WALL_" -> wallNameCounter
            "SLAB_" -> slabNameCounter
            "STAIR_" -> stairNameCounter
            "FRAME_" -> frameNameCounter
            "HOLE_" -> holeNameCounter
            else -> 1
        }
        var generated = "$prefix$index"
        while (!isAvailable(generated)) {
            index++
            generated = "$prefix$index"
        }
        when (prefix) {
            "WALL_" -> wallNameCounter = index + 1
            "SLAB_" -> slabNameCounter = index + 1
            "STAIR_" -> stairNameCounter = index + 1
            "FRAME_" -> frameNameCounter = index + 1
            "HOLE_" -> holeNameCounter = index + 1
        }
        return generated
    }
}

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
        var u0: Float,
        var u1: Float,
        var v0: Float,
        var v1: Float
    )

    data class WallSegment(
        var id: String,
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
        var min: Vector3,
        var max: Vector3,
        var thickness: Float,
        var topColor: Color,
        var bottomColor: Color,
        var sideColor: Color
    )

    data class Stair(
        var id: String,
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
        var cornerA: Vector3,
        var cornerB: Vector3,
        var normal: Vector3,
        var depth: Float,
        var frameWidth: Float,
        var kind: FrameKind,
        var color: Color
    )

    private val walls = mutableListOf<WallSegment>()
    private val slabs = mutableListOf<Slab>()
    private val stairs = mutableListOf<Stair>()
    private val frames = mutableListOf<Frame>()
    private var selectedElement: ElementSelection? = null

    fun allWalls(): List<WallSegment> = walls

    fun allSlabs(): List<Slab> = slabs

    fun allStairs(): List<Stair> = stairs

    fun allFrames(): List<Frame> = frames

    fun selectedElement(): ElementSelection? = selectedElement

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
            return false
        }
        selectedElement = ElementSelection(kind, id)
        return true
    }

    fun addWall(
        start: Vector3,
        end: Vector3,
        thickness: Float,
        height: Float,
        inclinationDeg: Float,
        exteriorColor: Color = Color(0.93f, 0.93f, 0.93f, 1f),
        interiorColor: Color = Color(0.84f, 0.84f, 0.84f, 1f),
        id: String = UUID.randomUUID().toString()
    ): WallSegment {
        val wall = WallSegment(
            id = id,
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
            min = min,
            max = max,
            thickness = thickness,
            topColor = Color(topColor),
            bottomColor = Color(bottomColor),
            sideColor = Color(sideColor)
        )
        slabs.add(slab)
        return slab
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
        id: String = UUID.randomUUID().toString()
    ): Frame {
        val frame = Frame(
            id = id,
            cornerA = Vector3(cornerA),
            cornerB = Vector3(cornerB),
            normal = Vector3(normal),
            depth = depth,
            frameWidth = frameWidth,
            kind = kind,
            color = Color(color)
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

    fun removeHoles(predicate: (wall: WallSegment, hole: RectHole) -> Boolean): Int {
        var removed = 0
        walls.forEach { wall ->
            val before = wall.holes.size
            wall.holes.removeIf { hole -> predicate(wall, hole) }
            removed += before - wall.holes.size
        }
        return removed
    }

    fun clear() {
        walls.clear()
        slabs.clear()
        stairs.clear()
        frames.clear()
        selectedElement = null
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

    fun updateFrame(id: String, depth: Float, frameWidth: Float, color: Color): Boolean {
        val frame = frames.firstOrNull { it.id == id } ?: return false
        frame.depth = depth
        frame.frameWidth = frameWidth
        frame.color.set(color)
        return true
    }
}

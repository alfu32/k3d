package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class ArchitectureStore {
    enum class FrameKind {
        WINDOW,
        DOOR
    }

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
        val holes: MutableList<RectHole> = mutableListOf()
    )

    data class Slab(
        var id: String,
        var min: Vector3,
        var max: Vector3,
        var thickness: Float
    )

    data class Stair(
        var id: String,
        var min: Vector3,
        var max: Vector3,
        var walkingStart: Vector3,
        var walkingEnd: Vector3,
        var height: Float,
        var stepCount: Int,
        var supportThickness: Float
    )

    data class Frame(
        var id: String,
        var cornerA: Vector3,
        var cornerB: Vector3,
        var normal: Vector3,
        var depth: Float,
        var frameWidth: Float,
        var kind: FrameKind
    )

    private val walls = mutableListOf<WallSegment>()
    private val slabs = mutableListOf<Slab>()
    private val stairs = mutableListOf<Stair>()
    private val frames = mutableListOf<Frame>()

    fun allWalls(): List<WallSegment> = walls

    fun allSlabs(): List<Slab> = slabs

    fun allStairs(): List<Stair> = stairs

    fun allFrames(): List<Frame> = frames

    fun addWall(
        start: Vector3,
        end: Vector3,
        thickness: Float,
        height: Float,
        inclinationDeg: Float,
        id: String = UUID.randomUUID().toString()
    ): WallSegment {
        val wall = WallSegment(
            id = id,
            start = Vector3(start),
            end = Vector3(end),
            thickness = thickness,
            height = height,
            inclinationDeg = inclinationDeg
        )
        walls.add(wall)
        return wall
    }

    fun addSlab(minCorner: Vector3, maxCorner: Vector3, thickness: Float, id: String = UUID.randomUUID().toString()): Slab {
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
            thickness = thickness
        )
        slabs.add(slab)
        return slab
    }

    fun addStair(
        minCorner: Vector3,
        maxCorner: Vector3,
        walkingStart: Vector3,
        walkingEnd: Vector3,
        height: Float,
        stepCount: Int,
        supportThickness: Float,
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
        val stair = Stair(
            id = id,
            min = min,
            max = max,
            walkingStart = Vector3(walkingStart),
            walkingEnd = Vector3(walkingEnd),
            height = height,
            stepCount = stepCount.coerceAtLeast(1),
            supportThickness = supportThickness
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
        id: String = UUID.randomUUID().toString()
    ): Frame {
        val frame = Frame(
            id = id,
            cornerA = Vector3(cornerA),
            cornerB = Vector3(cornerB),
            normal = Vector3(normal),
            depth = depth,
            frameWidth = frameWidth,
            kind = kind
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
    }
}

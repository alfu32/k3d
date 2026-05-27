package com.github.alfu32.sketch.tools

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs

internal data class SweepFrame(
    val origin: Vector3,
    val normal: Vector3,
    val u: Vector3,
    val v: Vector3
)

internal data class ProfileSegment2d(val a: Vector3, val b: Vector3)

internal object Sweep3dSupport {
    private const val EPSILON = 1e-4f
    private const val EPSILON_SQ = EPSILON * EPSILON

    fun buildFrames(path: List<Vector3>, worldUpLocal: Vector3): List<SweepFrame> {
        if (path.size < 2) {
            return emptyList()
        }
        val clean = dedupe(path)
        if (clean.size < 2) {
            return emptyList()
        }
        val tangents = mutableListOf<Vector3>()
        for (i in 0 until clean.lastIndex) {
            val dir = Vector3(clean[i + 1]).sub(clean[i])
            if (dir.len2() <= EPSILON_SQ) {
                return emptyList()
            }
            tangents += dir.nor()
        }
        val normals = mutableListOf<Vector3>()
        normals += Vector3(tangents.first())
        for (i in 1 until clean.lastIndex) {
            val bisector = Vector3(tangents[i - 1]).add(tangents[i])
            normals += if (bisector.len2() > EPSILON_SQ) bisector.nor() else Vector3(tangents[i])
        }
        normals += Vector3(tangents.last())

        val frames = mutableListOf<SweepFrame>()
        var u = initialU(normals.first(), worldUpLocal)
        var v = Vector3(normals.first()).crs(u).nor()
        frames += SweepFrame(Vector3(clean.first()), Vector3(normals.first()), Vector3(u), Vector3(v))

        for (i in 1 until clean.size) {
            val previousNormal = normals[i - 1]
            val currentNormal = normals[i]
            u = transportVector(u, previousNormal, currentNormal)
            u = rejectOnAxis(u, currentNormal)
            if (u.len2() <= EPSILON_SQ) {
                u = initialU(currentNormal, worldUpLocal)
            } else {
                u.nor()
            }
            v = Vector3(currentNormal).crs(u).nor()
            frames += SweepFrame(Vector3(clean[i]), Vector3(currentNormal), Vector3(u), Vector3(v))
        }
        return frames
    }

    fun sectionPoint(frame: SweepFrame, profilePoint: Vector3): Vector3 {
        return Vector3(frame.origin)
            .mulAdd(frame.u, profilePoint.x)
            .mulAdd(frame.v, profilePoint.y)
    }

    fun projectProfileSegments(
        sourceSegments: List<Pair<Vector3, Vector3>>,
        pathStart: Vector3,
        firstFrame: SweepFrame
    ): List<ProfileSegment2d> {
        val result = mutableListOf<ProfileSegment2d>()
        sourceSegments.forEach { (start, end) ->
            val a = profileCoordinates(start, pathStart, firstFrame)
            val b = profileCoordinates(end, pathStart, firstFrame)
            if (a.dst2(b) > EPSILON_SQ) {
                result += ProfileSegment2d(a, b)
            }
        }
        return result
    }

    private fun profileCoordinates(point: Vector3, origin: Vector3, frame: SweepFrame): Vector3 {
        val relative = Vector3(point).sub(origin)
        return Vector3(relative.dot(frame.u), relative.dot(frame.v), 0f)
    }

    private fun initialU(normal: Vector3, worldUpLocal: Vector3): Vector3 {
        val up = if (worldUpLocal.len2() > EPSILON_SQ) Vector3(worldUpLocal).nor() else Vector3(0f, 1f, 0f)
        var candidate = rejectOnAxis(up, normal)
        if (candidate.len2() <= EPSILON_SQ) {
            candidate = rejectOnAxis(Vector3(1f, 0f, 0f), normal)
        }
        if (candidate.len2() <= EPSILON_SQ) {
            candidate = rejectOnAxis(Vector3(0f, 0f, 1f), normal)
        }
        return candidate.nor()
    }

    private fun transportVector(vector: Vector3, fromNormal: Vector3, toNormal: Vector3): Vector3 {
        val axis = Vector3(fromNormal).crs(toNormal)
        val axisLen2 = axis.len2()
        if (axisLen2 <= EPSILON_SQ) {
            return Vector3(vector)
        }
        axis.nor()
        val dot = fromNormal.dot(toNormal).coerceIn(-1f, 1f)
        val angle = kotlin.math.atan2(kotlin.math.sqrt(axisLen2), dot)
        return rotateAroundAxis(vector, axis, angle)
    }

    private fun rotateAroundAxis(vector: Vector3, axis: Vector3, angle: Float): Vector3 {
        if (abs(angle) <= EPSILON) {
            return Vector3(vector)
        }
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        return Vector3(vector).scl(cos)
            .add(Vector3(axis).crs(vector).scl(sin))
            .add(Vector3(axis).scl(axis.dot(vector) * (1f - cos)))
    }

    private fun rejectOnAxis(vector: Vector3, axis: Vector3): Vector3 {
        return Vector3(vector).mulAdd(axis, -vector.dot(axis))
    }

    private fun dedupe(points: List<Vector3>): List<Vector3> {
        val out = mutableListOf<Vector3>()
        points.forEach { point ->
            if (out.isEmpty() || out.last().dst2(point) > EPSILON_SQ) {
                out += Vector3(point)
            }
        }
        return out
    }
}

package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.abs

class ExtrudeSwipeTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.EXTRUDE_SWIPE
    override val message: String = "Select one or more lines, then click to draw swipe path."

    private data class SourceSegment(val start: Vector3, val end: Vector3)

    private val sourceSegments = mutableListOf<SourceSegment>()
    private val pathLocal = mutableListOf<Vector3>()
    private val hoverLocal = Vector3()
    private var hasHover = false

    private val epsilon = 1e-4f
    private val epsilonSq = epsilon * epsilon

    override fun onEnter(status: StatusModel) {
        clearTransient()
        refreshSourceSegments()
        status.message = if (sourceSegments.isEmpty()) {
            message
        } else {
            "Click to start swipe path. Enter creates faces."
        }
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
            hoverLocal.set(scene.activeGroup().toLocal(world))
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        if (pathLocal.isEmpty()) {
            refreshSourceSegments()
        }
        if (sourceSegments.isEmpty()) {
            status.message = "Select one or more line segments before using Extrude Swipe."
            return false
        }

        val local = scene.activeGroup().toLocal(world)
        if (pathLocal.isNotEmpty() && pathLocal.last().dst2(local) <= epsilonSq) {
            return true
        }
        pathLocal.add(Vector3(local))
        status.message = if (pathLocal.size < 2) {
            "Click next path point. Enter creates faces."
        } else {
            "Click to continue path. Enter creates faces."
        }
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                if (sourceSegments.isEmpty()) {
                    refreshSourceSegments()
                }
                if (sourceSegments.isEmpty()) {
                    status.message = "Select one or more line segments before using Extrude Swipe."
                    true
                } else if (pathLocal.size < 2) {
                    status.message = "Pick at least two path points."
                    true
                } else {
                    val generated = commitSwipe()
                    clearPath()
                    status.message = if (generated > 0) {
                        "Extrude Swipe: created $generated faces."
                    } else {
                        "Extrude Swipe: nothing generated."
                    }
                    true
                }
            }

            Input.Keys.BACKSPACE -> {
                if (pathLocal.isNotEmpty()) {
                    pathLocal.removeAt(pathLocal.lastIndex)
                    status.message = if (pathLocal.isEmpty()) {
                        "Click to start swipe path. Enter creates faces."
                    } else {
                        "Click next path point. Enter creates faces."
                    }
                    true
                } else {
                    false
                }
            }

            Input.Keys.ESCAPE -> {
                clearPath()
                status.message = "Click to start swipe path. Enter creates faces."
                true
            }

            else -> false
        }
    }

    override fun anchorWorld(): Vector3? {
        if (pathLocal.isEmpty()) {
            return null
        }
        return scene.activeGroup().toWorld(pathLocal.last())
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        if (!hasHover || pathLocal.isEmpty()) {
            return null
        }
        val group = scene.activeGroup()
        return ToolMeasurement(
            startWorld = group.toWorld(pathLocal.last()),
            endWorld = group.toWorld(hoverLocal),
            lineColor = Color(0.4f, 0.9f, 1f, 1f)
        )
    }

    override fun render(renderer: ShapeRenderer) {
        val group = scene.activeGroup()

        renderer.color = Color(0.95f, 0.7f, 0.25f, 1f)
        sourceSegments.forEach { segment ->
            renderer.line(group.toWorld(segment.start), group.toWorld(segment.end))
        }

        if (pathLocal.isEmpty()) {
            return
        }

        renderer.color = Color(0.35f, 0.9f, 1f, 1f)
        for (i in 0 until pathLocal.lastIndex) {
            renderer.line(group.toWorld(pathLocal[i]), group.toWorld(pathLocal[i + 1]))
        }
        if (hasHover) {
            renderer.line(group.toWorld(pathLocal.last()), group.toWorld(hoverLocal))
        }
    }

    private fun commitSwipe(): Int {
        val group = scene.activeGroup()
        val path = dedupePath(pathLocal)
        if (path.size < 2 || sourceSegments.isEmpty()) {
            return 0
        }

        val segmentDirs = buildPathDirections(path)
        if (segmentDirs.isEmpty()) {
            return 0
        }
        val nodePlaneNormals = buildNodePlaneNormals(segmentDirs)

        var generatedFaces = 0
        var generatedLines = 0

        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                sourceSegments.forEach { segment ->
                    val aTrack = buildSweepTrack(segment.start, path, segmentDirs, nodePlaneNormals)
                    val bTrack = buildSweepTrack(segment.end, path, segmentDirs, nodePlaneNormals)
                    for (i in 0 until path.lastIndex) {
                        val a0 = aTrack[i]
                        val b0 = bTrack[i]
                        val b1 = bTrack[i + 1]
                        val a1 = aTrack[i + 1]
                        val spanNormal = Vector3(b0).sub(a0).crs(Vector3(path[i + 1]).sub(path[i]))
                        generatedFaces += addQuad(group, a0, b0, b1, a1, spanNormal)
                        generatedLines += addQuadEdges(group, a0, b0, b1, a1)
                    }
                }
                if (generatedLines > 0) {
                    group.lineStore.cleanupJts()
                }
            }
        }

        if (generatedFaces > 0 || generatedLines > 0) {
            group.faceStore.notifyExternalChange()
            group.lineStore.notifyExternalChange()
        }

        return generatedFaces
    }

    private fun addQuad(
        group: GroupScene.GroupNode,
        p0: Vector3,
        p1: Vector3,
        p2: Vector3,
        p3: Vector3,
        preferredNormal: Vector3
    ): Int {
        var count = 0
        val tri0 = Vector3(p1).sub(p0).crs(Vector3(p2).sub(p0))
        if (tri0.len2() > epsilonSq) {
            if (tri0.dot(preferredNormal) >= 0f) {
                group.faceStore.addTriangle(p0, p1, p2)
            } else {
                group.faceStore.addTriangle(p0, p2, p1)
            }
            count++
        }

        val tri1 = Vector3(p2).sub(p0).crs(Vector3(p3).sub(p0))
        if (tri1.len2() > epsilonSq) {
            if (tri1.dot(preferredNormal) >= 0f) {
                group.faceStore.addTriangle(p0, p2, p3)
            } else {
                group.faceStore.addTriangle(p0, p3, p2)
            }
            count++
        }
        return count
    }

    private fun addQuadEdges(
        group: GroupScene.GroupNode,
        p0: Vector3,
        p1: Vector3,
        p2: Vector3,
        p3: Vector3
    ): Int {
        var count = 0
        if (p0.dst2(p1) > epsilonSq) {
            group.lineStore.addSegment(p0, p1, autoCleanup = false)
            count++
        }
        if (p1.dst2(p2) > epsilonSq) {
            group.lineStore.addSegment(p1, p2, autoCleanup = false)
            count++
        }
        if (p2.dst2(p3) > epsilonSq) {
            group.lineStore.addSegment(p2, p3, autoCleanup = false)
            count++
        }
        if (p3.dst2(p0) > epsilonSq) {
            group.lineStore.addSegment(p3, p0, autoCleanup = false)
            count++
        }
        return count
    }

    private fun buildSweepTrack(
        sourcePoint: Vector3,
        path: List<Vector3>,
        segmentDirs: List<Vector3>,
        nodePlaneNormals: List<Vector3>
    ): List<Vector3> {
        val result = MutableList(path.size) { Vector3() }
        val pathStart = path.first()
        result[0] = Vector3(sourcePoint)

        for (node in 1 until path.size) {
            val prev = result[node - 1]
            val dir = segmentDirs[node - 1]
            val planeNormal = nodePlaneNormals[node]
            val anchor = Vector3(sourcePoint).add(Vector3(path[node]).sub(pathStart))

            val denom = planeNormal.dot(dir)
            val next = if (abs(denom) > epsilon) {
                val t = planeNormal.dot(Vector3(anchor).sub(prev)) / denom
                if (t.isFinite()) {
                    Vector3(prev).mulAdd(dir, t)
                } else {
                    anchor
                }
            } else {
                anchor
            }
            result[node] = if (isFinite(next)) next else anchor
        }

        return result
    }

    private fun buildPathDirections(path: List<Vector3>): List<Vector3> {
        val dirs = mutableListOf<Vector3>()
        for (i in 0 until path.lastIndex) {
            val dir = Vector3(path[i + 1]).sub(path[i])
            if (dir.len2() <= epsilonSq) {
                continue
            }
            dirs.add(dir.nor())
        }
        return dirs
    }

    private fun buildNodePlaneNormals(segmentDirs: List<Vector3>): List<Vector3> {
        if (segmentDirs.isEmpty()) {
            return emptyList()
        }
        val normals = MutableList(segmentDirs.size + 1) { Vector3() }
        normals[0] = Vector3(segmentDirs.first())
        normals[normals.lastIndex] = Vector3(segmentDirs.last())
        for (i in 1 until normals.lastIndex) {
            val prev = segmentDirs[i - 1]
            val next = segmentDirs[i]
            val bisector = Vector3(prev).add(next)
            normals[i] = if (bisector.len2() > epsilonSq) {
                bisector.nor()
            } else {
                Vector3(next)
            }
        }
        return normals
    }

    private fun refreshSourceSegments() {
        sourceSegments.clear()
        val selected = scene.activeGroup().lineStore.getSelected()
        selected.forEach { segment ->
            if (segment.start.dst2(segment.end) > epsilonSq) {
                sourceSegments.add(SourceSegment(Vector3(segment.start), Vector3(segment.end)))
            }
        }
    }

    private fun dedupePath(points: List<Vector3>): List<Vector3> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val result = mutableListOf(Vector3(points.first()))
        for (i in 1 until points.size) {
            if (points[i].dst2(result.last()) > epsilonSq) {
                result.add(Vector3(points[i]))
            }
        }
        return result
    }

    private fun clearTransient() {
        sourceSegments.clear()
        clearPath()
    }

    private fun clearPath() {
        pathLocal.clear()
        hasHover = false
    }

    private fun isFinite(vec: Vector3): Boolean {
        return vec.x.isFinite() && vec.y.isFinite() && vec.z.isFinite()
    }
}

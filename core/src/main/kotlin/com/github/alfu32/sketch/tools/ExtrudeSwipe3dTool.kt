package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement

class ExtrudeSwipe3dTool(
    private val scene: GroupScene,
    private val onSelectTool: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.EXTRUDE_SWIPE_3D
    override val message: String = "Select profile lines, then click to draw 3D swipe path. Enter creates faces."

    private val sourceSegments = mutableListOf<Pair<Vector3, Vector3>>()
    private val pathLocal = mutableListOf<Vector3>()
    private val hoverLocal = Vector3()
    private var hasHover = false
    private val epsilon = 1e-4f
    private val epsilonSq = epsilon * epsilon

    override fun onEnter(status: StatusModel) {
        clearPath()
        refreshSourceSegments()
        status.message = if (sourceSegments.isEmpty()) {
            "Select profile lines before using Extrude Swipe 3D."
        } else {
            "Click to start 3D swipe path. Enter creates faces."
        }
    }

    override fun onExit(status: StatusModel) {
        clearPath()
        sourceSegments.clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clearPath()
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
            status.message = "Select profile lines before using Extrude Swipe 3D."
            return true
        }
        val local = scene.activeGroup().toLocal(world)
        if (pathLocal.isEmpty() || pathLocal.last().dst2(local) > epsilonSq) {
            pathLocal += Vector3(local)
        }
        status.message = "Click next path point. Enter creates faces."
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                if (sourceSegments.isEmpty()) {
                    refreshSourceSegments()
                }
                val faces = commitSwipe()
                status.message = if (faces > 0) "Extrude Swipe 3D created $faces face(s)." else "Extrude Swipe 3D created no faces."
                clearPath()
                onSelectTool()
                true
            }
            Input.Keys.BACKSPACE -> {
                if (pathLocal.isNotEmpty()) {
                    pathLocal.removeAt(pathLocal.lastIndex)
                    true
                } else {
                    false
                }
            }
            Input.Keys.ESCAPE -> {
                clearPath()
                status.message = "Canceled."
                onSelectTool()
                true
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        if (!hasHover || pathLocal.isEmpty()) {
            return null
        }
        val group = scene.activeGroup()
        return ToolMeasurement(group.toWorld(pathLocal.last()), group.toWorld(hoverLocal))
    }

    override fun render(renderer: ShapeRenderer) {
        val group = scene.activeGroup()
        renderer.color = ToolFeedbackColors.PRIMARY
        sourceSegments.forEach { (a, b) ->
            renderer.line(group.toWorld(a), group.toWorld(b))
        }
        val points = previewPath()
        if (points.size < 2) {
            return
        }
        renderer.color = ToolFeedbackColors.SECONDARY
        for (i in 0 until points.lastIndex) {
            renderer.line(group.toWorld(points[i]), group.toWorld(points[i + 1]))
        }
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val group = scene.activeGroup()
        val out = mutableListOf<Pair<Vector3, Vector3>>()
        sourceSegments.forEach { (a, b) -> out += group.toWorld(a) to group.toWorld(b) }
        val points = previewPath()
        if (points.size >= 2) {
            appendPathFeedbackLines(out, points.map { group.toWorld(it) })
        }
        return out
    }

    private fun commitSwipe(): Int {
        val group = scene.activeGroup()
        val path = pathLocal.map { Vector3(it) }
        val frames = Sweep3dSupport.buildFrames(path, localVerticalAxis(group))
        if (frames.size < 2 || sourceSegments.isEmpty()) {
            return 0
        }
        val profile = Sweep3dSupport.projectProfileSegments(sourceSegments, path.first(), frames.first())
        if (profile.isEmpty()) {
            return 0
        }
        var faces = 0
        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                profile.forEach { segment ->
                    for (i in 0 until frames.lastIndex) {
                        val a0 = Sweep3dSupport.sectionPoint(frames[i], segment.a)
                        val b0 = Sweep3dSupport.sectionPoint(frames[i], segment.b)
                        val a1 = Sweep3dSupport.sectionPoint(frames[i + 1], segment.a)
                        val b1 = Sweep3dSupport.sectionPoint(frames[i + 1], segment.b)
                        group.faceStore.addTriangle(a0, b0, b1)
                        group.faceStore.addTriangle(a0, b1, a1)
                        faces += 2
                        addEdge(group, a0, b0)
                        addEdge(group, b0, b1)
                        addEdge(group, b1, a1)
                        addEdge(group, a1, a0)
                    }
                }
            }
        }
        group.faceStore.notifyExternalChange()
        group.lineStore.notifyExternalChange()
        return faces
    }

    private fun addEdge(group: GroupScene.GroupNode, a: Vector3, b: Vector3) {
        if (a.dst2(b) > epsilonSq) {
            group.lineStore.addSegment(a, b, autoCleanup = false)
        }
    }

    private fun refreshSourceSegments() {
        sourceSegments.clear()
        scene.activeGroup().lineStore.getSelected().forEach { segment ->
            if (segment.start.dst2(segment.end) > epsilonSq) {
                sourceSegments += Vector3(segment.start) to Vector3(segment.end)
            }
        }
    }

    private fun previewPath(): List<Vector3> {
        val out = pathLocal.map { Vector3(it) }.toMutableList()
        if (hasHover && out.isNotEmpty() && out.last().dst2(hoverLocal) > epsilonSq) {
            out += Vector3(hoverLocal)
        }
        return out
    }

    private fun localVerticalAxis(group: GroupScene.GroupNode): Vector3 {
        val up = group.vectorToLocal(Vector3(0f, 1f, 0f))
        return if (up.len2() > epsilonSq) up.nor() else Vector3(0f, 1f, 0f)
    }

    private fun clearPath() {
        pathLocal.clear()
        hasHover = false
    }
}

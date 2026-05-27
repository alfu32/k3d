package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement

class Ribbon3dTool(
    private val scene: GroupScene,
    private val settings: PolylineSettings,
    private val onSelectTool: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.RIBBON_3D
    override val message: String = "Click to draw a 3D ribbon path. Enter creates ribbon."

    private val pathLocal = mutableListOf<Vector3>()
    private val hoverLocal = Vector3()
    private var hasHover = false
    private val epsilon = 1e-4f
    private val epsilonSq = epsilon * epsilon

    override fun onEnter(status: StatusModel) {
        clear()
        status.message = message
    }

    override fun onExit(status: StatusModel) {
        clear()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clear()
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
        val local = scene.activeGroup().toLocal(world)
        if (pathLocal.isEmpty() || pathLocal.last().dst2(local) > epsilonSq) {
            pathLocal += Vector3(local)
        }
        status.message = "Click next point. Enter creates ribbon."
        return true
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ENTER -> {
                val faces = commitRibbon()
                status.message = if (faces > 0) "Ribbon 3D created $faces face(s)." else "Ribbon 3D created no faces."
                clear()
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
                clear()
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
        val points = previewPath()
        if (points.size < 2) {
            return
        }
        renderer.color = ToolFeedbackColors.SECONDARY
        for (i in 0 until points.lastIndex) {
            renderer.line(group.toWorld(points[i]), group.toWorld(points[i + 1]))
        }
        val frames = Sweep3dSupport.buildFrames(points, localVerticalAxis(group))
        if (frames.isEmpty()) {
            return
        }
        val profile = ribbonProfile()
        renderer.color = ToolFeedbackColors.TERTIARY
        val left = frames.map { Sweep3dSupport.sectionPoint(it, profile.first) }
        val right = frames.map { Sweep3dSupport.sectionPoint(it, profile.second) }
        for (i in 0 until left.lastIndex) {
            renderer.line(group.toWorld(left[i]), group.toWorld(left[i + 1]))
            renderer.line(group.toWorld(right[i]), group.toWorld(right[i + 1]))
        }
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val group = scene.activeGroup()
        val points = previewPath()
        if (points.size < 2) {
            return emptyList()
        }
        return pathFeedbackLines(points.map { group.toWorld(it) }, close = false)
    }

    private fun commitRibbon(): Int {
        val group = scene.activeGroup()
        val path = pathLocal.map { Vector3(it) }
        val frames = Sweep3dSupport.buildFrames(path, localVerticalAxis(group))
        if (frames.size < 2) {
            return 0
        }
        val profile = ribbonProfile()
        val left = frames.map { Sweep3dSupport.sectionPoint(it, profile.first) }
        val right = frames.map { Sweep3dSupport.sectionPoint(it, profile.second) }
        var faces = 0
        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                for (i in 0 until frames.lastIndex) {
                    val a = left[i]
                    val b = left[i + 1]
                    val c = right[i + 1]
                    val d = right[i]
                    group.faceStore.addTriangle(a, b, c)
                    group.faceStore.addTriangle(a, c, d)
                    faces += 2
                    addEdge(group, a, b)
                    addEdge(group, b, c)
                    addEdge(group, c, d)
                    addEdge(group, d, a)
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

    private fun ribbonProfile(): Pair<Vector3, Vector3> {
        val size = settings.doubleLineSize.coerceAtLeast(0f)
        val a = settings.doubleLineOffset - size * 0.5f
        val b = settings.doubleLineOffset + size * 0.5f
        return Vector3(a, 0f, 0f) to Vector3(b, 0f, 0f)
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

    private fun clear() {
        pathLocal.clear()
        hasHover = false
    }
}

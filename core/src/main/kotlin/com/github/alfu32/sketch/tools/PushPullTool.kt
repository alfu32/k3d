package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement

class PushPullTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.PUSH_PULL
    override val message: String = "Select faces, then pick reference point."

    private var activeTriangles: List<DraftFaceStore.Triangle> = emptyList()
    private var referencePointWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false
    private var currentOffsetLocal = Vector3()

    override fun onEnter(status: StatusModel) {
        val selectedCount = scene.activeGroup().faceStore.getSelected().size
        status.message = if (selectedCount > 0) {
            "Push/Pull $selectedCount selected face(s): pick reference point."
        } else {
            "Select faces, then pick reference point."
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
        val reference = referencePointWorld
        if (reference != null && valid && world != null) {
            hover.set(world)
            hasHover = true
            currentOffsetLocal = scene.activeGroup().vectorToLocal(Vector3(world).sub(reference))
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        if (referencePointWorld == null) {
            val selected = scene.activeGroup().faceStore.getSelected().toList()
            if (selected.isEmpty()) {
                status.message = "Push/Pull requires selected faces."
                return true
            }
            activeTriangles = selected
            referencePointWorld = Vector3(world)
            hasHover = false
            currentOffsetLocal.setZero()
            status.message = "Pick extrusion point."
            return true
        }

        val reference = referencePointWorld ?: return false
        currentOffsetLocal = scene.activeGroup().vectorToLocal(Vector3(world).sub(reference))
        commitExtrusion(currentOffsetLocal)
        clearTransient()
        status.message = "Select faces, then pick reference point."
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val reference = referencePointWorld ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawCross(renderer, reference, 0.18f)
        if (!hasHover || activeTriangles.isEmpty()) {
            return
        }

        renderer.color = ToolFeedbackColors.SECONDARY
        drawCross(renderer, hover, 0.18f)
        renderer.color = ToolFeedbackColors.TERTIARY
        renderer.line(reference.x, reference.y, reference.z, hover.x, hover.y, hover.z)
        renderExtrusionPreview(renderer, currentOffsetLocal)
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val reference = referencePointWorld ?: return emptyList()
        if (!hasHover || activeTriangles.isEmpty()) {
            return listOf(reference to reference)
        }
        val out = mutableListOf<Pair<Vector3, Vector3>>()
        out += reference to hover
        appendExtrusionPreviewLines(out, currentOffsetLocal)
        return out
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val reference = referencePointWorld ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(Vector3(reference), Vector3(hover))
    }

    private fun renderExtrusionPreview(renderer: ShapeRenderer, offsetLocal: Vector3) {
        val lines = mutableListOf<Pair<Vector3, Vector3>>()
        appendExtrusionPreviewLines(lines, offsetLocal)
        lines.forEach { (a, b) ->
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
        }
    }

    private fun appendExtrusionPreviewLines(out: MutableList<Pair<Vector3, Vector3>>, offsetLocal: Vector3) {
        if (activeTriangles.isEmpty() || offsetLocal.len2() <= 1e-8f) {
            return
        }
        val group = scene.activeGroup()
        val boundaryEdges = collectBoundaryEdges(activeTriangles)
        val verticalKeys = mutableSetOf<VertexKey>()
        boundaryEdges.forEach { edge ->
            val aLocal = edge.from
            val bLocal = edge.to
            val apLocal = Vector3(aLocal).add(offsetLocal)
            val bpLocal = Vector3(bLocal).add(offsetLocal)
            val a = group.toWorld(aLocal)
            val b = group.toWorld(bLocal)
            val ap = group.toWorld(apLocal)
            val bp = group.toWorld(bpLocal)
            out += a to b
            out += ap to bp
            val aKey = vertexKey(a)
            val bKey = vertexKey(b)
            if (verticalKeys.add(aKey)) {
                out += a to ap
            }
            if (verticalKeys.add(bKey)) {
                out += b to bp
            }
        }
    }

    private fun commitExtrusion(offsetLocal: Vector3) {
        if (activeTriangles.isEmpty() || offsetLocal.len2() <= 1e-8f) {
            return
        }
        val group = scene.activeGroup()
        val boundaryEdges = collectBoundaryEdges(activeTriangles)
        activeTriangles.forEach { tri ->
            val a = Vector3(tri.a)
            val b = Vector3(tri.b)
            val c = Vector3(tri.c)
            val ap = Vector3(a).add(offsetLocal)
            val bp = Vector3(b).add(offsetLocal)
            val cp = Vector3(c).add(offsetLocal)
            group.faceStore.addTriangle(ap, bp, cp)
        }

        val verticalKeys = mutableSetOf<VertexKey>()
        boundaryEdges.forEach { edge ->
            val a = edge.from
            val b = edge.to
            val ap = Vector3(a).add(offsetLocal)
            val bp = Vector3(b).add(offsetLocal)
            addSideQuad(a, b, ap, bp)
            group.lineStore.addSegment(ap, bp, autoCleanup = false)
            val aKey = vertexKey(a)
            val bKey = vertexKey(b)
            if (verticalKeys.add(aKey)) {
                group.lineStore.addSegment(a, ap, autoCleanup = false)
            }
            if (verticalKeys.add(bKey)) {
                group.lineStore.addSegment(b, bp, autoCleanup = false)
            }
        }
    }

    private fun addSideQuad(a: Vector3, b: Vector3, ap: Vector3, bp: Vector3) {
        scene.activeGroup().faceStore.addTriangle(a, b, bp)
        scene.activeGroup().faceStore.addTriangle(a, bp, ap)
    }

    private fun clearTransient() {
        activeTriangles = emptyList()
        referencePointWorld = null
        hasHover = false
        currentOffsetLocal.setZero()
    }

    private fun drawCross(renderer: ShapeRenderer, point: Vector3, size: Float) {
        renderer.line(point.x - size, point.y, point.z, point.x + size, point.y, point.z)
        renderer.line(point.x, point.y - size, point.z, point.x, point.y + size, point.z)
        renderer.line(point.x, point.y, point.z - size, point.x, point.y, point.z + size)
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private data class EdgeKey(val a: VertexKey, val b: VertexKey)

    private data class DirectedEdge(val from: Vector3, val to: Vector3)

    private fun collectBoundaryEdges(triangles: List<DraftFaceStore.Triangle>): List<DirectedEdge> {
        val edgeCount = mutableMapOf<EdgeKey, Int>()
        val edgeDirs = mutableMapOf<EdgeKey, DirectedEdge>()
        triangles.forEach { tri ->
            val edges = listOf(
                Pair(tri.a, tri.b),
                Pair(tri.b, tri.c),
                Pair(tri.c, tri.a)
            )
            edges.forEach { (from, to) ->
                val key = edgeKey(from, to)
                edgeCount[key] = (edgeCount[key] ?: 0) + 1
                edgeDirs.putIfAbsent(key, DirectedEdge(Vector3(from), Vector3(to)))
            }
        }
        return edgeCount.filterValues { it == 1 }.keys.mapNotNull { edgeDirs[it] }
    }

    private fun edgeKey(a: Vector3, b: Vector3): EdgeKey {
        val va = vertexKey(a)
        val vb = vertexKey(b)
        return if (compareKeys(va, vb) <= 0) EdgeKey(va, vb) else EdgeKey(vb, va)
    }

    private fun vertexKey(point: Vector3): VertexKey {
        val eps = 1e-3f
        fun quant(value: Float): Int = kotlin.math.round(value / eps).toInt()
        return VertexKey(quant(point.x), quant(point.y), quant(point.z))
    }

    private fun compareKeys(a: VertexKey, b: VertexKey): Int {
        if (a.x != b.x) return a.x.compareTo(b.x)
        if (a.y != b.y) return a.y.compareTo(b.y)
        return a.z.compareTo(b.z)
    }
}

package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import kotlin.math.round

class StretchTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.STRETCH
    override val message: String = "Pick reference point."

    private var start: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false
    private var hoverNormal: Vector3? = null

    private val keyEpsilon = 1e-2f

    override fun onEnter(status: StatusModel) {
        status.message = "Pick reference point."
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
            hover.set(world)
            hasHover = true
            hoverNormal = normal?.cpy()
        } else {
            hasHover = false
            hoverNormal = null
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        if (start == null) {
            start = Vector3(world)
            status.message = "Pick destination point."
            return true
        }
        val from = start ?: return false
        val to = Vector3(world)
        val deltaWorld = Vector3(to).sub(from)
        if (deltaWorld.len2() <= 1e-6f) {
            clearTransient()
            status.message = "No movement."
            return true
        }

        val group = scene.activeGroup()
        val localDelta = group.vectorToLocal(deltaWorld)
        val selectedKeys = collectSelectedKeys(group)
        val movedHotspots = mutableSetOf<VertexKey>()

        val lineStore = group.lineStore
        lineStore.withChangeSuppressed {
            lineStore.getSegments().forEach { segment ->
                if (lineStore.isSelected(segment)) {
                    segment.start.add(localDelta)
                    segment.end.add(localDelta)
                } else {
                    val startKey = vertexKey(segment.start)
                    if (selectedKeys.contains(startKey)) {
                        segment.start.add(localDelta)
                        movedHotspots.add(startKey)
                    }
                    val endKey = vertexKey(segment.end)
                    if (selectedKeys.contains(endKey)) {
                        segment.end.add(localDelta)
                        movedHotspots.add(endKey)
                    }
                }
            }
        }
        lineStore.notifyExternalChange()

        val faceStore = group.faceStore
        faceStore.withChangeSuppressed {
            faceStore.getTriangles().forEach { tri ->
                if (faceStore.isSelected(tri)) {
                    tri.a.add(localDelta)
                    tri.b.add(localDelta)
                    tri.c.add(localDelta)
                } else {
                    val aKey = vertexKey(tri.a)
                    if (selectedKeys.contains(aKey)) {
                        tri.a.add(localDelta)
                        movedHotspots.add(aKey)
                    }
                    val bKey = vertexKey(tri.b)
                    if (selectedKeys.contains(bKey)) {
                        tri.b.add(localDelta)
                        movedHotspots.add(bKey)
                    }
                    val cKey = vertexKey(tri.c)
                    if (selectedKeys.contains(cKey)) {
                        tri.c.add(localDelta)
                        movedHotspots.add(cKey)
                    }
                }
            }
        }
        faceStore.notifyExternalChange()

        val movedDimensions = group.dimensionStore.transformSelected { point -> Vector3(point).add(localDelta) }
        val movedTexts = group.textStore.transformSelected { point -> Vector3(point).add(localDelta) }
        val movedGroups = scene.transformSelectedGroups(
            { point -> Vector3(point).add(deltaWorld) },
            { vector -> Vector3(vector) }
        )
        alignSelectedGroupsIfNeeded()

        val movedEdges = lineStore.getSelected().size
        val movedFaces = faceStore.getSelected().size
        status.message =
            "Stretched | edges $movedEdges faces $movedFaces hotspots ${movedHotspots.size} dims $movedDimensions texts $movedTexts groups $movedGroups"
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val startPoint = start
        if (startPoint != null && hasHover) {
            renderer.color = Color(0.95f, 0.3f, 0.3f, 1f)
            drawCross(renderer, startPoint, 0.18f)
            renderer.color = Color(0.2f, 0.55f, 0.95f, 1f)
            drawCross(renderer, hover, 0.18f)
            renderer.color = Color(0.95f, 0.9f, 0.2f, 1f)
            renderer.line(startPoint.x, startPoint.y, startPoint.z, hover.x, hover.y, hover.z)
            val deltaWorld = Vector3(hover).sub(startPoint)
            if (deltaWorld.len2() > 1e-6f) {
                renderPreview(renderer, deltaWorld)
                renderGroupPreview(renderer, deltaWorld)
            }
        }
    }

    private fun drawCross(renderer: ShapeRenderer, point: Vector3, size: Float) {
        renderer.line(point.x - size, point.y, point.z, point.x + size, point.y, point.z)
        renderer.line(point.x, point.y - size, point.z, point.x, point.y + size, point.z)
        renderer.line(point.x, point.y, point.z - size, point.x, point.y, point.z + size)
    }

    private fun clearTransient() {
        start = null
        hasHover = false
        hoverNormal = null
    }

    private fun collectSelectedKeys(group: GroupScene.GroupNode): Set<VertexKey> {
        val keys = mutableSetOf<VertexKey>()
        group.lineStore.getSelected().forEach { segment ->
            keys.add(vertexKey(segment.start))
            keys.add(vertexKey(segment.end))
        }
        group.faceStore.getSelected().forEach { tri ->
            keys.add(vertexKey(tri.a))
            keys.add(vertexKey(tri.b))
            keys.add(vertexKey(tri.c))
        }
        return keys
    }

    private fun renderPreview(renderer: ShapeRenderer, deltaWorld: Vector3) {
        renderer.color = Color(0.25f, 0.85f, 0.55f, 1f)
        val group = scene.activeGroup()
        val localDelta = group.vectorToLocal(deltaWorld)
        val selectedKeys = collectSelectedKeys(group)

        group.faceStore.getTriangles().forEach { tri ->
            val selected = group.faceStore.isSelected(tri)
            val a = adjustPoint(tri.a, selected, selectedKeys, localDelta)
            val b = adjustPoint(tri.b, selected, selectedKeys, localDelta)
            val c = adjustPoint(tri.c, selected, selectedKeys, localDelta)
            val aw = group.toWorld(a)
            val bw = group.toWorld(b)
            val cw = group.toWorld(c)
            renderer.line(aw.x, aw.y, aw.z, bw.x, bw.y, bw.z)
            renderer.line(bw.x, bw.y, bw.z, cw.x, cw.y, cw.z)
            renderer.line(cw.x, cw.y, cw.z, aw.x, aw.y, aw.z)
        }
        group.lineStore.getSegments().forEach { segment ->
            val selected = group.lineStore.isSelected(segment)
            val a = adjustPoint(segment.start, selected, selectedKeys, localDelta)
            val b = adjustPoint(segment.end, selected, selectedKeys, localDelta)
            val aw = group.toWorld(a)
            val bw = group.toWorld(b)
            renderer.line(aw.x, aw.y, aw.z, bw.x, bw.y, bw.z)
        }
    }

    private fun renderGroupPreview(renderer: ShapeRenderer, deltaWorld: Vector3) {
        if (scene.selectedGroups().isEmpty()) {
            return
        }
        renderer.color = Color(0.25f, 0.85f, 0.55f, 1f)
        scene.selectedGroups().forEach { group ->
            val corners = group.orientedBoundsCorners() ?: return@forEach
            corners.indices.forEach { idx ->
                corners[idx].add(deltaWorld)
            }
            drawWireBox(renderer, corners)
        }
    }

    private fun drawWireBox(renderer: ShapeRenderer, corners: Array<Vector3>) {
        if (corners.size < 8) {
            return
        }
        val c0 = corners[0]
        val c1 = corners[1]
        val c2 = corners[2]
        val c3 = corners[3]
        val c4 = corners[4]
        val c5 = corners[5]
        val c6 = corners[6]
        val c7 = corners[7]
        renderer.line(c0, c1)
        renderer.line(c1, c2)
        renderer.line(c2, c3)
        renderer.line(c3, c0)
        renderer.line(c4, c5)
        renderer.line(c5, c6)
        renderer.line(c6, c7)
        renderer.line(c7, c4)
        renderer.line(c0, c4)
        renderer.line(c1, c5)
        renderer.line(c2, c6)
        renderer.line(c3, c7)
    }

    private fun adjustPoint(
        point: Vector3,
        selectedEntity: Boolean,
        selectedKeys: Set<VertexKey>,
        localDelta: Vector3
    ): Vector3 {
        if (selectedEntity || selectedKeys.contains(vertexKey(point))) {
            return Vector3(point).add(localDelta)
        }
        return Vector3(point)
    }

    private fun alignSelectedGroupsIfNeeded() {
        val normal = hoverNormal?.cpy()?.nor() ?: return
        scene.selectedGroups().forEach { group ->
            if (!group.gluedToSurface) {
                return@forEach
            }
            val axes = group.worldAxes()
            val lenU = axes.u.len()
            val lenV = axes.v.len()
            val lenW = axes.w.len()
            if (lenW <= 1e-6f) {
                return@forEach
            }
            val newW = normal.cpy().nor().scl(lenW)
            var newU = axes.u.cpy()
            newU.mulAdd(newW, -newU.dot(newW) / (lenW * lenW))
            if (newU.len2() <= 1e-6f) {
                newU = axes.v.cpy()
                newU.mulAdd(newW, -newU.dot(newW) / (lenW * lenW))
            }
            if (newU.len2() <= 1e-6f) {
                return@forEach
            }
            newU.nor().scl(lenU)
            val newV = Vector3(newW).crs(newU).nor().scl(lenV)
            val origin = group.worldOrigin()
            group.setInstanceFromWorld(origin, newU, newV, newW)
        }
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(quant(point.x), quant(point.y), quant(point.z))
    }

    private fun quant(value: Float): Int = round(value / keyEpsilon).toInt()
}

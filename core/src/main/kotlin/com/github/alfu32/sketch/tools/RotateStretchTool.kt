package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.round

class RotateStretchTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.ROTATE_STRETCH
    override val message: String = "Pick rotation center."

    private var centerWorld: Vector3? = null
    private var centerLocal: Vector3? = null
    private var centerNormalWorld: Vector3? = null
    private var axisDirWorld: Vector3? = null
    private var axisDirLocal: Vector3? = null
    private var referenceWorld: Vector3? = null
    private var referenceLocal: Vector3? = null

    private val hover = Vector3()
    private var hasHover = false

    private val keyEpsilon = 1e-2f

    override fun onEnter(status: StatusModel) {
        status.message = "Pick rotation center."
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
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        val group = scene.activeGroup()
        if (centerWorld == null) {
            centerWorld = Vector3(world)
            centerLocal = group.toLocal(world)
            centerNormalWorld = normal?.cpy()
            status.message = "Pick axis direction."
            return true
        }
        if (axisDirWorld == null) {
            val c = centerWorld ?: return false
            val axis = Vector3(world).sub(c)
            val worldAxis = if (axis.len2() <= 1e-4f) {
                (centerNormalWorld ?: Vector3(0f, 1f, 0f)).cpy().nor()
            } else {
                axis.nor()
            }
            axisDirWorld = worldAxis
            axisDirLocal = group.vectorToLocal(worldAxis).nor()
            status.message = "Pick reference point."
            return true
        }
        if (referenceWorld == null) {
            referenceWorld = Vector3(world)
            referenceLocal = group.toLocal(world)
            status.message = "Pick final point."
            return true
        }

        val cWorld = centerWorld ?: return false
        val cLocal = centerLocal ?: return false
        val axisW = axisDirWorld ?: return false
        val axisL = axisDirLocal ?: return false
        val fromLocal = referenceLocal ?: return false
        val toLocal = group.toLocal(world)

        val v1 = Vector3(fromLocal).sub(cLocal)
        val v2 = Vector3(toLocal).sub(cLocal)
        if (v1.len2() <= 1e-6f || v2.len2() <= 1e-6f) {
            clearTransient()
            status.message = "Rotation vectors are too short."
            return true
        }
        val cross = Vector3(v1).crs(v2)
        val angle = MathUtils.atan2(axisL.dot(cross), v1.dot(v2))
        val degrees = angle * MathUtils.radiansToDegrees
        if (!degrees.isFinite() || kotlin.math.abs(degrees) <= 1e-5f) {
            clearTransient()
            status.message = "No rotation."
            return true
        }
        val rotationLocal = Quaternion().setFromAxis(axisL, degrees)
        val rotationWorld = Quaternion().setFromAxis(axisW, degrees)

        val selectedKeys = collectSelectedKeys(group)
        val movedHotspots = mutableSetOf<VertexKey>()

        group.lineStore.withChangeSuppressed {
            group.lineStore.getSegments().forEach { segment ->
                if (group.lineStore.isSelected(segment)) {
                    rotateInPlace(segment.start, cLocal, rotationLocal)
                    rotateInPlace(segment.end, cLocal, rotationLocal)
                } else {
                    val startKey = vertexKey(segment.start)
                    if (selectedKeys.contains(startKey)) {
                        rotateInPlace(segment.start, cLocal, rotationLocal)
                        movedHotspots.add(startKey)
                    }
                    val endKey = vertexKey(segment.end)
                    if (selectedKeys.contains(endKey)) {
                        rotateInPlace(segment.end, cLocal, rotationLocal)
                        movedHotspots.add(endKey)
                    }
                }
            }
        }
        group.lineStore.notifyExternalChange()

        group.faceStore.withChangeSuppressed {
            group.faceStore.getTriangles().forEach { tri ->
                if (group.faceStore.isSelected(tri)) {
                    rotateInPlace(tri.a, cLocal, rotationLocal)
                    rotateInPlace(tri.b, cLocal, rotationLocal)
                    rotateInPlace(tri.c, cLocal, rotationLocal)
                } else {
                    val aKey = vertexKey(tri.a)
                    if (selectedKeys.contains(aKey)) {
                        rotateInPlace(tri.a, cLocal, rotationLocal)
                        movedHotspots.add(aKey)
                    }
                    val bKey = vertexKey(tri.b)
                    if (selectedKeys.contains(bKey)) {
                        rotateInPlace(tri.b, cLocal, rotationLocal)
                        movedHotspots.add(bKey)
                    }
                    val cKey = vertexKey(tri.c)
                    if (selectedKeys.contains(cKey)) {
                        rotateInPlace(tri.c, cLocal, rotationLocal)
                        movedHotspots.add(cKey)
                    }
                }
            }
        }
        group.faceStore.notifyExternalChange()

        var movedDimensions = 0
        group.dimensionStore.getDimensions().forEach { dimension ->
            var changed = false
            if (group.dimensionStore.isSelected(dimension)) {
                rotateInPlace(dimension.start, cLocal, rotationLocal)
                rotateInPlace(dimension.end, cLocal, rotationLocal)
                rotateInPlace(dimension.offset, cLocal, rotationLocal)
                changed = true
            } else {
                val startKey = vertexKey(dimension.start)
                if (selectedKeys.contains(startKey)) {
                    rotateInPlace(dimension.start, cLocal, rotationLocal)
                    changed = true
                }
                val endKey = vertexKey(dimension.end)
                if (selectedKeys.contains(endKey)) {
                    rotateInPlace(dimension.end, cLocal, rotationLocal)
                    changed = true
                }
                val offsetKey = vertexKey(dimension.offset)
                if (selectedKeys.contains(offsetKey)) {
                    rotateInPlace(dimension.offset, cLocal, rotationLocal)
                    changed = true
                }
            }
            if (changed) {
                movedDimensions++
            }
        }
        if (movedDimensions > 0) {
            group.dimensionStore.notifyExternalChange()
        }

        val movedTexts = group.textStore.transformSelected { point ->
            Vector3(point).sub(cLocal).mul(rotationLocal).add(cLocal)
        }
        val movedGroups = scene.transformSelectedGroups(
            { point -> Vector3(point).sub(cWorld).mul(rotationWorld).add(cWorld) },
            { vector -> Vector3(vector).mul(rotationWorld) }
        )

        val movedEdges = group.lineStore.getSelected().size
        val movedFaces = group.faceStore.getSelected().size
        status.message =
            "Rotate-stretched | edges $movedEdges faces $movedFaces hotspots ${movedHotspots.size} dims $movedDimensions texts $movedTexts groups $movedGroups"
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        if (!hasHover) {
            return
        }
        val cWorld = centerWorld ?: return
        drawCross(renderer, cWorld, 0.18f, ToolFeedbackColors.PRIMARY)
        drawCross(renderer, hover, 0.18f, ToolFeedbackColors.SECONDARY)

        val axis = axisDirWorld
        if (axis != null) {
            val axisLen = when {
                referenceWorld != null -> Vector3(referenceWorld).sub(cWorld).len()
                else -> Vector3(hover).sub(cWorld).len()
            }.coerceAtLeast(0.5f)
            val axisEnd = Vector3(cWorld).mulAdd(axis, axisLen)
            renderer.color = ToolFeedbackColors.SECONDARY
            renderer.line(cWorld.x, cWorld.y, cWorld.z, axisEnd.x, axisEnd.y, axisEnd.z)
        }

        val reference = referenceWorld
        if (reference != null) {
            renderer.color = ToolFeedbackColors.PRIMARY
            renderer.line(cWorld.x, cWorld.y, cWorld.z, reference.x, reference.y, reference.z)
        }
        renderer.color = ToolFeedbackColors.TERTIARY
        renderer.line(cWorld.x, cWorld.y, cWorld.z, hover.x, hover.y, hover.z)

        if (axis == null || reference == null) {
            return
        }
        val group = scene.activeGroup()
        val cLocal = centerLocal ?: return
        val axisLocal = axisDirLocal ?: return
        val v1 = Vector3(group.toLocal(reference)).sub(cLocal)
        val v2 = Vector3(group.toLocal(hover)).sub(cLocal)
        if (v1.len2() <= 1e-6f || v2.len2() <= 1e-6f) {
            return
        }
        val cross = Vector3(v1).crs(v2)
        val angle = MathUtils.atan2(axisLocal.dot(cross), v1.dot(v2))
        val degrees = angle * MathUtils.radiansToDegrees
        val rotationLocal = Quaternion().setFromAxis(axisLocal, degrees)
        renderGeometryPreview(renderer, cLocal, rotationLocal)
        renderGroupPreview(renderer, cWorld, axis, degrees)
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val center = centerWorld ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(
            startWorld = Vector3(center),
            endWorld = Vector3(hover)
        )
    }

    private fun clearTransient() {
        centerWorld = null
        centerLocal = null
        centerNormalWorld = null
        axisDirWorld = null
        axisDirLocal = null
        referenceWorld = null
        referenceLocal = null
        hasHover = false
    }

    private fun renderGeometryPreview(renderer: ShapeRenderer, centerLocal: Vector3, rotationLocal: Quaternion) {
        renderer.color = ToolFeedbackColors.TERTIARY
        val group = scene.activeGroup()
        val selectedKeys = collectSelectedKeys(group)
        group.faceStore.getTriangles().forEach { tri ->
            val selected = group.faceStore.isSelected(tri)
            val a = rotatedPoint(tri.a, selected, selectedKeys, centerLocal, rotationLocal)
            val b = rotatedPoint(tri.b, selected, selectedKeys, centerLocal, rotationLocal)
            val c = rotatedPoint(tri.c, selected, selectedKeys, centerLocal, rotationLocal)
            val aw = group.toWorld(a)
            val bw = group.toWorld(b)
            val cw = group.toWorld(c)
            renderer.line(aw.x, aw.y, aw.z, bw.x, bw.y, bw.z)
            renderer.line(bw.x, bw.y, bw.z, cw.x, cw.y, cw.z)
            renderer.line(cw.x, cw.y, cw.z, aw.x, aw.y, aw.z)
        }
        group.lineStore.getSegments().forEach { segment ->
            val selected = group.lineStore.isSelected(segment)
            val a = rotatedPoint(segment.start, selected, selectedKeys, centerLocal, rotationLocal)
            val b = rotatedPoint(segment.end, selected, selectedKeys, centerLocal, rotationLocal)
            val aw = group.toWorld(a)
            val bw = group.toWorld(b)
            renderer.line(aw.x, aw.y, aw.z, bw.x, bw.y, bw.z)
        }
    }

    private fun renderGroupPreview(renderer: ShapeRenderer, centerWorld: Vector3, axisWorld: Vector3, degrees: Float) {
        if (scene.selectedGroups().isEmpty()) {
            return
        }
        val rotation = Quaternion().setFromAxis(axisWorld, degrees)
        renderer.color = ToolFeedbackColors.TERTIARY
        scene.selectedGroups().forEach { group ->
            val corners = group.orientedBoundsCorners() ?: return@forEach
            corners.indices.forEach { index ->
                corners[index].sub(centerWorld).mul(rotation).add(centerWorld)
            }
            drawWireBox(renderer, corners)
        }
    }

    private fun rotatedPoint(
        point: Vector3,
        selectedEntity: Boolean,
        selectedKeys: Set<VertexKey>,
        centerLocal: Vector3,
        rotationLocal: Quaternion
    ): Vector3 {
        if (selectedEntity || selectedKeys.contains(vertexKey(point))) {
            return Vector3(point).sub(centerLocal).mul(rotationLocal).add(centerLocal)
        }
        return Vector3(point)
    }

    private fun rotateInPlace(point: Vector3, centerLocal: Vector3, rotationLocal: Quaternion) {
        point.sub(centerLocal).mul(rotationLocal).add(centerLocal)
    }

    private fun drawCross(renderer: ShapeRenderer, point: Vector3, size: Float, color: com.badlogic.gdx.graphics.Color) {
        renderer.color = color
        renderer.line(point.x - size, point.y, point.z, point.x + size, point.y, point.z)
        renderer.line(point.x, point.y - size, point.z, point.x, point.y + size, point.z)
        renderer.line(point.x, point.y, point.z - size, point.x, point.y, point.z + size)
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

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(quant(point.x), quant(point.y), quant(point.z))
    }

    private fun quant(value: Float): Int = round(value / keyEpsilon).toInt()
}

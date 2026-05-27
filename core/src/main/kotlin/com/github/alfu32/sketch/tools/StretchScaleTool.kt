package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.round

class StretchScaleTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.STRETCH_SCALE
    override val message: String = "Pick scale center."

    private var centerWorld: Vector3? = null
    private var referenceWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false
    private val keyEpsilon = 1e-2f

    override fun onEnter(status: StatusModel) {
        status.message = "Pick scale center."
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
        if (centerWorld == null) {
            centerWorld = Vector3(world)
            status.message = "Pick reference point."
            return true
        }
        if (referenceWorld == null) {
            referenceWorld = Vector3(world)
            status.message = "Pick final point."
            return true
        }

        val center = centerWorld ?: return false
        val reference = referenceWorld ?: return false
        val refVec = Vector3(reference).sub(center)
        val nextVec = Vector3(world).sub(center)
        if (refVec.len2() <= 1e-6f || nextVec.len2() <= 1e-6f) {
            clearTransient()
            status.message = "Scale vectors are too short."
            return true
        }
        val constraint = scaleConstraintFor(refVec, nextVec)
        val scale = scaleFor(constraint, refVec, nextVec)
        if (!scale.isFinite() || kotlin.math.abs(scale - 1f) <= 1e-4f) {
            clearTransient()
            status.message = "No scale."
            return true
        }

        val group = scene.activeGroup()
        val centerLocal = group.toLocal(center)
        val selectedKeys = collectSelectedKeys(group)
        var movedVertices = 0

        val lineStore = group.lineStore
        lineStore.withChangeSuppressed {
            lineStore.getSegments().forEach { segment ->
                val selected = lineStore.isSelected(segment)
                if (selected || selectedKeys.contains(vertexKey(segment.start))) {
                    segment.start.set(scalePoint(segment.start, centerLocal, scale, constraint))
                    movedVertices++
                }
                if (selected || selectedKeys.contains(vertexKey(segment.end))) {
                    segment.end.set(scalePoint(segment.end, centerLocal, scale, constraint))
                    movedVertices++
                }
            }
        }
        lineStore.notifyExternalChange()

        val faceStore = group.faceStore
        faceStore.withChangeSuppressed {
            faceStore.getTriangles().forEach { tri ->
                val selected = faceStore.isSelected(tri)
                if (selected || selectedKeys.contains(vertexKey(tri.a))) {
                    tri.a.set(scalePoint(tri.a, centerLocal, scale, constraint))
                    movedVertices++
                }
                if (selected || selectedKeys.contains(vertexKey(tri.b))) {
                    tri.b.set(scalePoint(tri.b, centerLocal, scale, constraint))
                    movedVertices++
                }
                if (selected || selectedKeys.contains(vertexKey(tri.c))) {
                    tri.c.set(scalePoint(tri.c, centerLocal, scale, constraint))
                    movedVertices++
                }
            }
        }
        faceStore.notifyExternalChange()

        var scaledDimensions = 0
        group.dimensionStore.getDimensions().forEach { dimension ->
            var changed = false
            if (group.dimensionStore.isSelected(dimension) || selectedKeys.contains(vertexKey(dimension.start))) {
                dimension.start.set(scalePoint(dimension.start, centerLocal, scale, constraint))
                changed = true
            }
            if (group.dimensionStore.isSelected(dimension) || selectedKeys.contains(vertexKey(dimension.end))) {
                dimension.end.set(scalePoint(dimension.end, centerLocal, scale, constraint))
                changed = true
            }
            if (group.dimensionStore.isSelected(dimension) || selectedKeys.contains(vertexKey(dimension.offset))) {
                dimension.offset.set(scalePoint(dimension.offset, centerLocal, scale, constraint))
                changed = true
            }
            if (changed) {
                scaledDimensions++
            }
        }
        if (scaledDimensions > 0) {
            group.dimensionStore.notifyExternalChange()
        }

        val scaledTexts = group.textStore.transformSelectedAdvanced(
            pointTransform = { point -> scalePoint(point, centerLocal, scale, constraint) },
            vectorTransform = { vector -> scaleVector(vector, scale, constraint) }
        )
        val scaledGroups = scene.transformSelectedGroups(
            { point -> scalePoint(point, center, scale, constraint) },
            { vector -> scaleVector(vector, scale, constraint) }
        )

        status.message =
            "Stretch scaled | vertices $movedVertices dims $scaledDimensions texts $scaledTexts groups $scaledGroups"
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        val center = centerWorld ?: return
        if (!hasHover) {
            return
        }
        val reference = referenceWorld
        if (reference != null) {
            renderer.color = ToolFeedbackColors.PRIMARY
            renderer.line(center.x, center.y, center.z, reference.x, reference.y, reference.z)
        }
        renderer.color = ToolFeedbackColors.TERTIARY
        renderer.line(center.x, center.y, center.z, hover.x, hover.y, hover.z)
        if (reference != null) {
            val refVec = Vector3(reference).sub(center)
            val nextVec = Vector3(hover).sub(center)
            if (refVec.len2() > 1e-6f && nextVec.len2() > 1e-6f) {
                val constraint = scaleConstraintFor(refVec, nextVec)
                val scale = scaleFor(constraint, refVec, nextVec)
                if (scale.isFinite()) {
                    renderPreview(renderer, center, scale, constraint)
                }
            }
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val center = centerWorld ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(Vector3(center), Vector3(hover))
    }

    private fun renderPreview(renderer: ShapeRenderer, centerWorld: Vector3, scale: Float, constraint: ScaleConstraint) {
        val group = scene.activeGroup()
        val centerLocal = group.toLocal(centerWorld)
        val selectedKeys = collectSelectedKeys(group)
        renderer.color = ToolFeedbackColors.TERTIARY

        group.lineStore.getSegments().forEach { segment ->
            val selected = group.lineStore.isSelected(segment)
            val a = adjustedPoint(segment.start, selected, selectedKeys, centerLocal, scale, constraint)
            val b = adjustedPoint(segment.end, selected, selectedKeys, centerLocal, scale, constraint)
            val aw = group.toWorld(a)
            val bw = group.toWorld(b)
            renderer.line(aw.x, aw.y, aw.z, bw.x, bw.y, bw.z)
        }
        group.faceStore.getTriangles().forEach { tri ->
            val selected = group.faceStore.isSelected(tri)
            val a = adjustedPoint(tri.a, selected, selectedKeys, centerLocal, scale, constraint)
            val b = adjustedPoint(tri.b, selected, selectedKeys, centerLocal, scale, constraint)
            val c = adjustedPoint(tri.c, selected, selectedKeys, centerLocal, scale, constraint)
            val aw = group.toWorld(a)
            val bw = group.toWorld(b)
            val cw = group.toWorld(c)
            renderer.line(aw.x, aw.y, aw.z, bw.x, bw.y, bw.z)
            renderer.line(bw.x, bw.y, bw.z, cw.x, cw.y, cw.z)
            renderer.line(cw.x, cw.y, cw.z, aw.x, aw.y, aw.z)
        }
    }

    private fun adjustedPoint(
        point: Vector3,
        selectedEntity: Boolean,
        selectedKeys: Set<VertexKey>,
        center: Vector3,
        scale: Float,
        constraint: ScaleConstraint
    ): Vector3 {
        return if (selectedEntity || selectedKeys.contains(vertexKey(point))) {
            scalePoint(point, center, scale, constraint)
        } else {
            Vector3(point)
        }
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

    private fun clearTransient() {
        centerWorld = null
        referenceWorld = null
        hasHover = false
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private fun vertexKey(point: Vector3): VertexKey {
        return VertexKey(quant(point.x), quant(point.y), quant(point.z))
    }

    private fun quant(value: Float): Int = round(value / keyEpsilon).toInt()

    private enum class ScaleConstraint {
        UNIFORM,
        PLANE_X,
        PLANE_Y,
        PLANE_Z,
        AXIS_X,
        AXIS_Y,
        AXIS_Z
    }

    private fun scalePoint(point: Vector3, center: Vector3, scale: Float, constraint: ScaleConstraint): Vector3 {
        val scaled = Vector3(point).sub(center)
        when (constraint) {
            ScaleConstraint.UNIFORM -> scaled.scl(scale)
            ScaleConstraint.PLANE_X -> {
                scaled.y *= scale
                scaled.z *= scale
            }
            ScaleConstraint.PLANE_Y -> {
                scaled.x *= scale
                scaled.z *= scale
            }
            ScaleConstraint.PLANE_Z -> {
                scaled.x *= scale
                scaled.y *= scale
            }
            ScaleConstraint.AXIS_X -> scaled.x *= scale
            ScaleConstraint.AXIS_Y -> scaled.y *= scale
            ScaleConstraint.AXIS_Z -> scaled.z *= scale
        }
        return Vector3(center).add(scaled)
    }

    private fun scaleVector(vector: Vector3, scale: Float, constraint: ScaleConstraint): Vector3 {
        val scaled = Vector3(vector)
        when (constraint) {
            ScaleConstraint.UNIFORM -> scaled.scl(scale)
            ScaleConstraint.PLANE_X -> {
                scaled.y *= scale
                scaled.z *= scale
            }
            ScaleConstraint.PLANE_Y -> {
                scaled.x *= scale
                scaled.z *= scale
            }
            ScaleConstraint.PLANE_Z -> {
                scaled.x *= scale
                scaled.y *= scale
            }
            ScaleConstraint.AXIS_X -> scaled.x *= scale
            ScaleConstraint.AXIS_Y -> scaled.y *= scale
            ScaleConstraint.AXIS_Z -> scaled.z *= scale
        }
        return scaled
    }

    private fun scaleConstraintFor(refVec: Vector3, nextVec: Vector3): ScaleConstraint {
        return referenceConstraintFor(refVec) ?: planeConstraintFor(refVec, nextVec) ?: ScaleConstraint.UNIFORM
    }

    private fun planeConstraintFor(refVec: Vector3, nextVec: Vector3): ScaleConstraint? {
        val normal = Vector3(refVec).crs(nextVec)
        if (normal.len2() <= 1e-6f) {
            return null
        }
        normal.nor()
        val absX = kotlin.math.abs(normal.x)
        val absY = kotlin.math.abs(normal.y)
        val absZ = kotlin.math.abs(normal.z)
        val threshold = 0.98f
        return when {
            absX >= threshold && absX >= absY && absX >= absZ -> ScaleConstraint.PLANE_X
            absY >= threshold && absY >= absX && absY >= absZ -> ScaleConstraint.PLANE_Y
            absZ >= threshold && absZ >= absX && absZ >= absY -> ScaleConstraint.PLANE_Z
            else -> null
        }
    }

    private fun referenceConstraintFor(refVec: Vector3): ScaleConstraint? {
        val len = refVec.len()
        if (len <= 1e-6f) {
            return null
        }
        val tol = len * 0.03f
        val zeroX = kotlin.math.abs(refVec.x) <= tol
        val zeroY = kotlin.math.abs(refVec.y) <= tol
        val zeroZ = kotlin.math.abs(refVec.z) <= tol
        val zeroCount = (if (zeroX) 1 else 0) + (if (zeroY) 1 else 0) + (if (zeroZ) 1 else 0)
        if (zeroCount >= 2) {
            return when {
                !zeroX -> ScaleConstraint.AXIS_X
                !zeroY -> ScaleConstraint.AXIS_Y
                !zeroZ -> ScaleConstraint.AXIS_Z
                else -> null
            }
        }
        if (zeroCount == 1) {
            return when {
                zeroX -> ScaleConstraint.PLANE_X
                zeroY -> ScaleConstraint.PLANE_Y
                zeroZ -> ScaleConstraint.PLANE_Z
                else -> null
            }
        }
        return null
    }

    private fun scaleFor(constraint: ScaleConstraint, refVec: Vector3, nextVec: Vector3): Float {
        return when (constraint) {
            ScaleConstraint.AXIS_X -> componentScale(refVec.x, nextVec.x)
            ScaleConstraint.AXIS_Y -> componentScale(refVec.y, nextVec.y)
            ScaleConstraint.AXIS_Z -> componentScale(refVec.z, nextVec.z)
            else -> nextVec.len() / refVec.len()
        }
    }

    private fun componentScale(ref: Float, next: Float): Float {
        val denom = kotlin.math.abs(ref)
        if (denom <= 1e-6f) {
            return Float.NaN
        }
        val raw = next / ref
        val minMagnitude = 1e-1f
        return when {
            raw >= 0f -> kotlin.math.max(raw, minMagnitude)
            else -> kotlin.math.min(raw, -minMagnitude)
        }
    }
}

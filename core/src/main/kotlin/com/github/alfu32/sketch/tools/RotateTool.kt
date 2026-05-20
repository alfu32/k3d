package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import com.github.alfu32.sketch.ui.ToolMeasurementLabel

class RotateTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.ROTATE
    override val message: String = "Pick rotation center."

    private var centerWorld: Vector3? = null
    private var centerLocal: Vector3? = null
    private var axisDirWorld: Vector3? = null
    private var axisDirLocal: Vector3? = null
    private var centerNormalWorld: Vector3? = null
    private var referenceWorld: Vector3? = null
    private var referenceLocal: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

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

    override fun supportsCopyMode(): Boolean = true

    override fun onCopyModeChanged(status: StatusModel, enabled: Boolean) {
        status.message = when {
            centerWorld == null -> "Pick rotation center."
            axisDirWorld == null -> "Pick axis direction."
            referenceWorld == null -> "Pick reference point."
            else -> "Pick final point."
        }
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
            axisDirWorld = if (axis.len2() <= 1e-4f) {
                (centerNormalWorld ?: Vector3(0f, 1f, 0f)).cpy().nor()
            } else {
                axis.nor()
            }
            axisDirLocal = axisDirWorld?.let { group.vectorToLocal(it).nor() }
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
        val quaternionLocal = Quaternion().setFromAxis(axisL, degrees)
        val quaternionWorld = Quaternion().setFromAxis(axisW, degrees)
        if (status.copyMode) {
            val rotatedArchitecture = scene.copySelectedArchitectureElements(
                group = group,
                pointTransform = { point -> Vector3(point).sub(cWorld).mul(quaternionWorld).add(cWorld) },
                vectorTransform = { vector -> Vector3(vector).mul(quaternionWorld) }
            )
            val rotatedHvac = scene.copySelectedHvacElements(
                group = group,
                pointTransform = { point -> Vector3(point).sub(cWorld).mul(quaternionWorld).add(cWorld) }
            )
            val movedFaces = group.faceStore.copySelected { point ->
                Vector3(point).sub(cLocal).mul(quaternionLocal).add(cLocal)
            }
            val movedEdges = group.lineStore.copySelected { point ->
                Vector3(point).sub(cLocal).mul(quaternionLocal).add(cLocal)
            }
            val movedTexts = group.textStore.copySelectedAdvanced(
                pointTransform = { point -> Vector3(point).sub(cLocal).mul(quaternionLocal).add(cLocal) },
                vectorTransform = { vector -> Vector3(vector).mul(quaternionLocal) }
            )
            val movedGroups = scene.copySelectedGroups(
                { point -> Vector3(point).sub(cWorld).mul(quaternionWorld).add(cWorld) },
                { vector -> Vector3(vector).mul(quaternionWorld) }
            )
            status.message =
                "Copied | architecture $rotatedArchitecture hvac $rotatedHvac edges $movedEdges faces $movedFaces texts $movedTexts groups $movedGroups"
        } else {
            val rotatedArchitecture = scene.transformSelectedArchitectureElements(
                group = group,
                pointTransform = { point -> Vector3(point).sub(cWorld).mul(quaternionWorld).add(cWorld) },
                vectorTransform = { vector -> Vector3(vector).mul(quaternionWorld) }
            )
            val rotatedHvac = scene.transformSelectedHvacElements(
                group = group,
                pointTransform = { point -> Vector3(point).sub(cWorld).mul(quaternionWorld).add(cWorld) }
            )
            val movedFaces = group.faceStore.transformSelected { point ->
                Vector3(point).sub(cLocal).mul(quaternionLocal).add(cLocal)
            }
            val movedEdges = group.lineStore.transformSelected { point ->
                Vector3(point).sub(cLocal).mul(quaternionLocal).add(cLocal)
            }
            val movedTexts = group.textStore.transformSelectedAdvanced(
                pointTransform = { point -> Vector3(point).sub(cLocal).mul(quaternionLocal).add(cLocal) },
                vectorTransform = { vector -> Vector3(vector).mul(quaternionLocal) }
            )
            val movedGroups = scene.transformSelectedGroups(
                { point -> Vector3(point).sub(cWorld).mul(quaternionWorld).add(cWorld) },
                { vector -> Vector3(vector).mul(quaternionWorld) }
            )
            status.message =
                "Rotated | architecture $rotatedArchitecture hvac $rotatedHvac edges $movedEdges faces $movedFaces texts $movedTexts groups $movedGroups"
        }
        clearTransient()
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        if (!hasHover) {
            return
        }
        val c = centerWorld ?: return
        val axis = axisDirWorld
        val ref = referenceWorld
        val axisLen = when {
            ref != null -> Vector3(ref).sub(c).len()
            hasHover -> Vector3(hover).sub(c).len()
            else -> 1f
        }.coerceAtLeast(0.5f)
        if (axis != null) {
            val axisEnd = Vector3(c).mulAdd(axis, axisLen)
            renderer.color = ToolFeedbackColors.SECONDARY
            renderer.line(c.x, c.y, c.z, axisEnd.x, axisEnd.y, axisEnd.z)
        }
        if (ref != null) {
            renderer.color = ToolFeedbackColors.PRIMARY
            renderer.line(c.x, c.y, c.z, ref.x, ref.y, ref.z)
        }
        renderer.color = ToolFeedbackColors.TERTIARY
        renderer.line(c.x, c.y, c.z, hover.x, hover.y, hover.z)
        if (axis != null && ref != null) {
            val group = scene.activeGroup()
            val cLocal = centerLocal ?: return
            val axisLocal = axisDirLocal ?: return
            val v1 = Vector3(group.toLocal(ref)).sub(cLocal)
            val v2 = Vector3(group.toLocal(hover)).sub(cLocal)
            if (v1.len2() > 1e-6f && v2.len2() > 1e-6f) {
                val cross = Vector3(v1).crs(v2)
                val angle = MathUtils.atan2(axisLocal.dot(cross), v1.dot(v2))
                val degrees = angle * MathUtils.radiansToDegrees
                val quaternion = Quaternion().setFromAxis(axisLocal, degrees)
                renderPreview(renderer, cLocal, quaternion)
                renderGroupPreview(renderer, c, axis, degrees)
            }
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val center = centerWorld ?: return null
        if (!hasHover) {
            return null
        }
        val extras = rotationAngleLabels()
        return ToolMeasurement(
            startWorld = Vector3(center),
            endWorld = Vector3(hover),
            extraLabels = extras
        )
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val c = centerWorld ?: return emptyList()
        val out = mutableListOf<Pair<Vector3, Vector3>>()
        val axis = axisDirWorld
        val ref = referenceWorld
        val axisLen = when {
            ref != null -> Vector3(ref).sub(c).len()
            hasHover -> Vector3(hover).sub(c).len()
            else -> 1f
        }.coerceAtLeast(0.5f)
        if (axis != null) {
            out += Vector3(c) to Vector3(c).mulAdd(axis, axisLen)
        }
        if (ref != null) {
            out += Vector3(c) to Vector3(ref)
        }
        if (hasHover) {
            out += Vector3(c) to Vector3(hover)
        }
        return out
    }

    private fun rotationAngleLabels(): List<ToolMeasurementLabel> {
        val group = scene.activeGroup()
        val cLocal = centerLocal ?: return emptyList()
        val axisLocal = axisDirLocal ?: return emptyList()
        val ref = referenceWorld ?: return emptyList()
        val v1 = Vector3(group.toLocal(ref)).sub(cLocal)
        val v2 = Vector3(group.toLocal(hover)).sub(cLocal)
        if (v1.len2() <= 1e-6f || v2.len2() <= 1e-6f) {
            return emptyList()
        }
        val angle = MathUtils.atan2(axisLocal.dot(Vector3(v1).crs(v2)), v1.dot(v2))
        return listOf(
            ToolMeasurementLabel("Angle", text = String.format(java.util.Locale.US, "%.3f deg", angle * MathUtils.radiansToDegrees)),
            ToolMeasurementLabel("Radians", text = String.format(java.util.Locale.US, "%.4f rad", angle))
        )
    }

    private fun clearTransient() {
        centerWorld = null
        centerLocal = null
        axisDirWorld = null
        axisDirLocal = null
        centerNormalWorld = null
        referenceWorld = null
        referenceLocal = null
        hasHover = false
    }

    private fun renderPreview(renderer: ShapeRenderer, center: Vector3, rotation: Quaternion) {
        renderer.color = ToolFeedbackColors.TERTIARY
        val group = scene.activeGroup()
        group.faceStore.getSelected().forEach { tri ->
            val a = Vector3(tri.a).sub(center).mul(rotation).add(center)
            val b = Vector3(tri.b).sub(center).mul(rotation).add(center)
            val c = Vector3(tri.c).sub(center).mul(rotation).add(center)
            val aw = group.toWorld(a)
            val bw = group.toWorld(b)
            val cw = group.toWorld(c)
            renderer.line(aw.x, aw.y, aw.z, bw.x, bw.y, bw.z)
            renderer.line(bw.x, bw.y, bw.z, cw.x, cw.y, cw.z)
            renderer.line(cw.x, cw.y, cw.z, aw.x, aw.y, aw.z)
        }
        group.lineStore.getSelected().forEach { segment ->
            val a = Vector3(segment.start).sub(center).mul(rotation).add(center)
            val b = Vector3(segment.end).sub(center).mul(rotation).add(center)
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
            val bounds = group.worldBounds() ?: return@forEach
            val corners = arrayOf(
                Vector3(bounds.min.x, bounds.min.y, bounds.min.z),
                Vector3(bounds.min.x, bounds.min.y, bounds.max.z),
                Vector3(bounds.min.x, bounds.max.y, bounds.min.z),
                Vector3(bounds.min.x, bounds.max.y, bounds.max.z),
                Vector3(bounds.max.x, bounds.min.y, bounds.min.z),
                Vector3(bounds.max.x, bounds.min.y, bounds.max.z),
                Vector3(bounds.max.x, bounds.max.y, bounds.min.z),
                Vector3(bounds.max.x, bounds.max.y, bounds.max.z)
            )
            corners.forEach { corner ->
                corner.sub(centerWorld).mul(rotation).add(centerWorld)
            }
            val min = corners.reduce { a, b -> Vector3(
                kotlin.math.min(a.x, b.x),
                kotlin.math.min(a.y, b.y),
                kotlin.math.min(a.z, b.z)
            ) }
            val max = corners.reduce { a, b -> Vector3(
                kotlin.math.max(a.x, b.x),
                kotlin.math.max(a.y, b.y),
                kotlin.math.max(a.z, b.z)
            ) }
            renderer.line(min.x, min.y, min.z, max.x, min.y, min.z)
            renderer.line(max.x, min.y, min.z, max.x, min.y, max.z)
            renderer.line(max.x, min.y, max.z, min.x, min.y, max.z)
            renderer.line(min.x, min.y, max.z, min.x, min.y, min.z)
            renderer.line(min.x, max.y, min.z, max.x, max.y, min.z)
            renderer.line(max.x, max.y, min.z, max.x, max.y, max.z)
            renderer.line(max.x, max.y, max.z, min.x, max.y, max.z)
            renderer.line(min.x, max.y, max.z, min.x, max.y, min.z)
            renderer.line(min.x, min.y, min.z, min.x, max.y, min.z)
            renderer.line(max.x, min.y, min.z, max.x, max.y, min.z)
            renderer.line(max.x, min.y, max.z, max.x, max.y, max.z)
            renderer.line(min.x, min.y, max.z, min.x, max.y, max.z)
        }
    }
}

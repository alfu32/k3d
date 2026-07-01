package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.max

class SpherePrimitiveTool(
    private val scene: GroupScene,
    private val segmentsProvider: () -> Int
) : Tool {
    override val id: ToolId = ToolId.PRIMITIVE_SPHERE
    override val message: String = "Click sphere center."

    private var centerWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = message
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
        val center = centerWorld
        if (center == null) {
            centerWorld = Vector3(world)
            status.message = "Click sphere radius point."
            return true
        }
        val result = commitSphere(center, Vector3(world))
        clearTransient()
        status.message = result
        return true
    }

    override fun anchorWorld(): Vector3? = centerWorld

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val center = centerWorld ?: return null
        if (!hasHover) return null
        return ToolMeasurement(Vector3(center), Vector3(hover))
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val center = centerWorld ?: return emptyList()
        if (!hasHover) return emptyList()
        return listOf(center to Vector3(hover))
    }

    override fun render(renderer: ShapeRenderer) {
        val center = centerWorld ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawPrimitiveCross(renderer, center, 0.18f)
        if (hasHover) {
            renderer.color = ToolFeedbackColors.SECONDARY
            drawPrimitiveCross(renderer, hover, 0.18f)
            renderer.line(center.x, center.y, center.z, hover.x, hover.y, hover.z)
        }
    }

    private fun commitSphere(centerWorld: Vector3, radiusWorld: Vector3): String {
        val group = scene.activeGroup()
        val center = group.toLocal(centerWorld)
        val radius = group.toLocal(radiusWorld).dst(center)
        if (radius <= PRIMITIVE_EPSILON) {
            return "Sphere canceled: radius is too small."
        }
        val segments = primitiveSegments()
        val stacks = max(2, segments / 2)
        val rings = ArrayList<List<Vector3>>(stacks + 1)
        for (stack in 0..stacks) {
            val phi = -MathUtils.PI * 0.5f + MathUtils.PI * stack.toFloat() / stacks.toFloat()
            val y = MathUtils.sin(phi) * radius
            val ringRadius = MathUtils.cos(phi) * radius
            rings += List(segments) { i ->
                val theta = MathUtils.PI2 * i.toFloat() / segments.toFloat()
                Vector3(
                    center.x + MathUtils.cos(theta) * ringRadius,
                    center.y + y,
                    center.z + MathUtils.sin(theta) * ringRadius
                )
            }
        }
        var faces = 0
        var edges = 0
        val faceColor = Color(scene.defaultFaceColor)
        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                for (stack in 0 until stacks) {
                    val lower = rings[stack]
                    val upper = rings[stack + 1]
                    for (i in 0 until segments) {
                        val next = (i + 1) % segments
                        val a = lower[i]
                        val b = lower[next]
                        val c = upper[next]
                        val d = upper[i]
                        if (stack == 0) {
                            group.faceStore.addTriangle(a, c, d, faceColor)
                            faces++
                        } else if (stack == stacks - 1) {
                            group.faceStore.addTriangle(a, b, d, faceColor)
                            faces++
                        } else {
                            group.faceStore.addTriangle(a, b, c, faceColor)
                            group.faceStore.addTriangle(a, c, d, faceColor)
                            faces += 2
                        }
                        edges += addPrimitiveSegment(group, a, b)
                        edges += addPrimitiveSegment(group, a, d)
                    }
                }
            }
        }
        group.faceStore.notifyExternalChange()
        group.lineStore.notifyExternalChange()
        return "Created sphere with $faces face(s), $edges segment(s), and $segments radial sides."
    }

    private fun primitiveSegments(): Int = segmentsProvider().coerceIn(3, 256)

    private fun clearTransient() {
        centerWorld = null
        hasHover = false
    }
}

abstract class AxisPrimitiveTool(
    private val scene: GroupScene,
    private val segmentsProvider: () -> Int
) : Tool {
    protected abstract val primitiveLabel: String

    private var centerWorld: Vector3? = null
    private var radiusWorld: Vector3? = null
    private val hover = Vector3()
    private var hasHover = false

    override val message: String
        get() = "Click $primitiveLabel base center."

    override fun onEnter(status: StatusModel) {
        status.message = message
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
        val center = centerWorld
        val radius = radiusWorld
        if (center == null) {
            centerWorld = Vector3(world)
            status.message = "Click $primitiveLabel radius point."
            return true
        }
        if (radius == null) {
            radiusWorld = Vector3(world)
            status.message = "Click $primitiveLabel height point."
            return true
        }
        val result = commitAxisPrimitive(center, radius, Vector3(world))
        clearTransient()
        status.message = result
        return true
    }

    override fun anchorWorld(): Vector3? = radiusWorld ?: centerWorld

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val anchor = radiusWorld ?: centerWorld ?: return null
        if (!hasHover) return null
        return ToolMeasurement(Vector3(anchor), Vector3(hover))
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val center = centerWorld ?: return emptyList()
        val lines = mutableListOf<Pair<Vector3, Vector3>>()
        val radius = radiusWorld
        if (radius == null) {
            if (hasHover) lines += center to Vector3(hover)
            return lines
        }
        lines += center to Vector3(radius)
        if (hasHover) lines += center to Vector3(hover)
        return lines
    }

    override fun render(renderer: ShapeRenderer) {
        val center = centerWorld ?: return
        renderer.color = ToolFeedbackColors.PRIMARY
        drawPrimitiveCross(renderer, center, 0.18f)
        radiusWorld?.let { radius ->
            renderer.color = ToolFeedbackColors.SECONDARY
            drawPrimitiveCross(renderer, radius, 0.18f)
            renderer.line(center.x, center.y, center.z, radius.x, radius.y, radius.z)
        }
        if (hasHover) {
            renderer.color = ToolFeedbackColors.TERTIARY
            drawPrimitiveCross(renderer, hover, 0.18f)
            renderer.line(center.x, center.y, center.z, hover.x, hover.y, hover.z)
        }
    }

    private fun commitAxisPrimitive(centerWorld: Vector3, radiusWorld: Vector3, heightWorld: Vector3): String {
        val group = scene.activeGroup()
        val center = group.toLocal(centerWorld)
        val radiusPoint = group.toLocal(radiusWorld)
        val height = group.toLocal(heightWorld)
        val axis = Vector3(height).sub(center)
        if (axis.len2() <= PRIMITIVE_EPSILON_SQ) {
            return "$primitiveLabel canceled: height is too short."
        }
        val axisUnit = Vector3(axis).nor()
        val radialRaw = Vector3(radiusPoint).sub(center)
        val radial = Vector3(radialRaw).mulAdd(axisUnit, -radialRaw.dot(axisUnit))
        val radius = radial.len()
        val basis = if (radial.len2() > PRIMITIVE_EPSILON_SQ) {
            val axisU = radial.nor()
            PrimitiveBasis(axisUnit, axisU, Vector3(axisUnit).crs(axisU).nor())
        } else {
            primitiveBasisFromAxis(axisUnit)
        }
        if (radius <= PRIMITIVE_EPSILON) {
            return "$primitiveLabel canceled: radius is too small or lies on the height axis."
        }
        return buildPrimitive(group, center, height, basis, radius, primitiveSegments())
    }

    protected abstract fun buildPrimitive(
        group: GroupScene.GroupNode,
        center: Vector3,
        height: Vector3,
        basis: PrimitiveBasis,
        radius: Float,
        segments: Int
    ): String

    protected fun faceColor(): Color = Color(scene.defaultFaceColor)

    private fun primitiveSegments(): Int = segmentsProvider().coerceIn(3, 256)

    private fun clearTransient() {
        centerWorld = null
        radiusWorld = null
        hasHover = false
    }
}

class CylinderPrimitiveTool(
    scene: GroupScene,
    segmentsProvider: () -> Int
) : AxisPrimitiveTool(scene, segmentsProvider) {
    override val id: ToolId = ToolId.PRIMITIVE_CYLINDER
    override val primitiveLabel: String = "cylinder"

    override fun buildPrimitive(
        group: GroupScene.GroupNode,
        center: Vector3,
        height: Vector3,
        basis: PrimitiveBasis,
        radius: Float,
        segments: Int
    ): String {
        val bottom = primitiveRing(center, basis, radius, segments)
        val top = primitiveRing(height, basis, radius, segments)
        val color = faceColor()
        var faces = 0
        var edges = 0
        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                for (i in 0 until segments) {
                    val next = (i + 1) % segments
                    faces += addPrimitiveQuad(group, bottom[i], bottom[next], top[next], top[i], color)
                    group.faceStore.addTriangle(center, bottom[next], bottom[i], color)
                    group.faceStore.addTriangle(height, top[i], top[next], color)
                    faces += 2
                    edges += addPrimitiveSegment(group, bottom[i], bottom[next])
                    edges += addPrimitiveSegment(group, top[i], top[next])
                    edges += addPrimitiveSegment(group, bottom[i], top[i])
                }
            }
        }
        group.faceStore.notifyExternalChange()
        group.lineStore.notifyExternalChange()
        return "Created cylinder with $faces face(s), $edges segment(s), and $segments sides."
    }
}

class ConePrimitiveTool(
    scene: GroupScene,
    segmentsProvider: () -> Int
) : AxisPrimitiveTool(scene, segmentsProvider) {
    override val id: ToolId = ToolId.PRIMITIVE_CONE
    override val primitiveLabel: String = "cone"

    override fun buildPrimitive(
        group: GroupScene.GroupNode,
        center: Vector3,
        height: Vector3,
        basis: PrimitiveBasis,
        radius: Float,
        segments: Int
    ): String {
        val bottom = primitiveRing(center, basis, radius, segments)
        val color = faceColor()
        var faces = 0
        var edges = 0
        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                for (i in 0 until segments) {
                    val next = (i + 1) % segments
                    group.faceStore.addTriangle(bottom[i], bottom[next], height, color)
                    group.faceStore.addTriangle(center, bottom[next], bottom[i], color)
                    faces += 2
                    edges += addPrimitiveSegment(group, bottom[i], bottom[next])
                    edges += addPrimitiveSegment(group, bottom[i], height)
                }
            }
        }
        group.faceStore.notifyExternalChange()
        group.lineStore.notifyExternalChange()
        return "Created cone with $faces face(s), $edges segment(s), and $segments sides."
    }
}

class PillPrimitiveTool(
    scene: GroupScene,
    segmentsProvider: () -> Int
) : AxisPrimitiveTool(scene, segmentsProvider) {
    override val id: ToolId = ToolId.PRIMITIVE_PILL
    override val primitiveLabel: String = "pill"

    override fun buildPrimitive(
        group: GroupScene.GroupNode,
        center: Vector3,
        height: Vector3,
        basis: PrimitiveBasis,
        radius: Float,
        segments: Int
    ): String {
        val axis = Vector3(height).sub(center)
        val axisLen = axis.len()
        if (axisLen <= PRIMITIVE_EPSILON) {
            return "pill canceled: height is too short."
        }
        val axisUnit = Vector3(axis).scl(1f / axisLen)
        val halfStacks = max(2, segments / 4)
        val rings = ArrayList<List<Vector3>>(halfStacks * 2 + 2)
        val bottomPole = Vector3(center).mulAdd(axisUnit, -radius)
        val topPole = Vector3(height).mulAdd(axisUnit, radius)
        rings += List(segments) { Vector3(bottomPole) }
        for (stack in 1..halfStacks) {
            val t = stack.toFloat() / halfStacks.toFloat()
            val angle = -MathUtils.PI * 0.5f + t * MathUtils.PI * 0.5f
            val offset = MathUtils.sin(angle) * radius
            val ringRadius = MathUtils.cos(angle) * radius
            rings += primitiveRing(Vector3(center).mulAdd(axisUnit, offset), basis, ringRadius, segments)
        }
        rings += primitiveRing(height, basis, radius, segments)
        for (stack in 1 until halfStacks) {
            val t = stack.toFloat() / halfStacks.toFloat()
            val angle = t * MathUtils.PI * 0.5f
            val offset = MathUtils.sin(angle) * radius
            val ringRadius = MathUtils.cos(angle) * radius
            rings += primitiveRing(Vector3(height).mulAdd(axisUnit, offset), basis, ringRadius, segments)
        }
        rings += List(segments) { Vector3(topPole) }

        val color = faceColor()
        var faces = 0
        var edges = 0
        group.faceStore.withChangeSuppressed {
            group.lineStore.withChangeSuppressed {
                for (ringIndex in 0 until rings.lastIndex) {
                    val aRing = rings[ringIndex]
                    val bRing = rings[ringIndex + 1]
                    for (i in 0 until segments) {
                        val next = (i + 1) % segments
                        val a = aRing[i]
                        val b = aRing[next]
                        val c = bRing[next]
                        val d = bRing[i]
                        faces += if (ringIndex == 0) {
                            group.faceStore.addTriangle(a, c, d, color)
                            1
                        } else if (ringIndex == rings.lastIndex - 1) {
                            group.faceStore.addTriangle(a, b, d, color)
                            1
                        } else {
                            addPrimitiveQuad(group, a, b, c, d, color)
                        }
                        edges += addPrimitiveSegment(group, a, b)
                        edges += addPrimitiveSegment(group, a, d)
                    }
                }
            }
        }
        group.faceStore.notifyExternalChange()
        group.lineStore.notifyExternalChange()
        return "Created pill with $faces face(s), $edges segment(s), and $segments sides."
    }
}

data class PrimitiveBasis(val axis: Vector3, val axisU: Vector3, val axisV: Vector3)

private const val PRIMITIVE_EPSILON = 0.001f
private const val PRIMITIVE_EPSILON_SQ = PRIMITIVE_EPSILON * PRIMITIVE_EPSILON

private fun primitiveBasisFromAxis(axisUnit: Vector3): PrimitiveBasis {
    val reference = if (kotlin.math.abs(axisUnit.y) < 0.9f) Vector3(0f, 1f, 0f) else Vector3(1f, 0f, 0f)
    val axisU = Vector3(reference).crs(axisUnit).nor()
    val axisV = Vector3(axisUnit).crs(axisU).nor()
    return PrimitiveBasis(Vector3(axisUnit), axisU, axisV)
}

private fun primitiveRing(center: Vector3, basis: PrimitiveBasis, radius: Float, segments: Int): List<Vector3> {
    return List(segments) { i ->
        val angle = MathUtils.PI2 * i.toFloat() / segments.toFloat()
        Vector3(center)
            .mulAdd(basis.axisU, MathUtils.cos(angle) * radius)
            .mulAdd(basis.axisV, MathUtils.sin(angle) * radius)
    }
}

private fun addPrimitiveQuad(
    group: GroupScene.GroupNode,
    a: Vector3,
    b: Vector3,
    c: Vector3,
    d: Vector3,
    color: Color
): Int {
    group.faceStore.addTriangle(a, b, c, color)
    group.faceStore.addTriangle(a, c, d, color)
    return 2
}

private fun addPrimitiveSegment(group: GroupScene.GroupNode, a: Vector3, b: Vector3): Int {
    if (a.dst2(b) <= PRIMITIVE_EPSILON_SQ) {
        return 0
    }
    group.lineStore.addSegment(a, b, autoCleanup = false)
    return 1
}

private fun drawPrimitiveCross(renderer: ShapeRenderer, point: Vector3, size: Float) {
    renderer.line(point.x - size, point.y, point.z, point.x + size, point.y, point.z)
    renderer.line(point.x, point.y - size, point.z, point.x, point.y + size, point.z)
    renderer.line(point.x, point.y, point.z - size, point.x, point.y, point.z + size)
}

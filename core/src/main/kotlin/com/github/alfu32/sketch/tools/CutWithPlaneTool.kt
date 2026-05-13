package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement
import kotlin.math.abs

class CutWithPlaneTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.CUT_WITH_PLANE
    override val message: String = "Select mesh faces, then pick cut plane origin."

    private val planeEpsilon = 1e-4f
    private val dedupeEpsilon = 1e-3f
    private var originWorld: Vector3? = null
    private val hoverWorld = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Select mesh faces, then pick cut plane origin."
    }

    override fun onExit(status: StatusModel) {
        clearTransient()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clearTransient()
        status.message = "Cut with plane canceled."
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hoverWorld.set(world)
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(
        status: StatusModel,
        world: Vector3?,
        normal: Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }

        val origin = originWorld
        if (origin == null) {
            originWorld = Vector3(world)
            status.message = "Pick second point to define the cut plane normal."
            return true
        }

        return finalizeCut(status, origin, Vector3(world))
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ESCAPE -> {
                onCancel(status)
                true
            }
            Input.Keys.ENTER -> {
                val origin = originWorld
                if (origin != null && hasHover) {
                    finalizeCut(status, origin, Vector3(hoverWorld))
                } else {
                    false
                }
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val origin = originWorld ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(
            startWorld = Vector3(origin),
            endWorld = Vector3(hoverWorld)
        )
    }

    override fun render(renderer: ShapeRenderer) {
        val origin = originWorld ?: return
        if (!hasHover) {
            return
        }
        renderer.color = ToolFeedbackColors.SECONDARY
        renderer.line(origin.x, origin.y, origin.z, hoverWorld.x, hoverWorld.y, hoverWorld.z)
    }

    private fun finalizeCut(status: StatusModel, planePointWorld: Vector3, normalPointWorld: Vector3): Boolean {
        val group = scene.activeGroup()
        val faceStore = group.faceStore
        val selected = faceStore.getSelected().toList()
        if (selected.isEmpty()) {
            status.message = "Cut with plane needs selected mesh faces."
            clearTransient()
            return true
        }

        val planePointLocal = group.toLocal(planePointWorld)
        val normalPointLocal = group.toLocal(normalPointWorld)
        val planeNormalLocal = Vector3(normalPointLocal).sub(planePointLocal)
        if (planeNormalLocal.len2() <= planeEpsilon * planeEpsilon) {
            status.message = "Cut plane normal is too small. Pick a different second point."
            return true
        }
        planeNormalLocal.nor()

        val replacements = mutableListOf<ReplacementTriangle>()
        val cutSegments = mutableListOf<Pair<Vector3, Vector3>>()
        val toDelete = mutableListOf<DraftFaceStore.Triangle>()

        selected.forEach { triangle ->
            val color = faceStore.colorFor(triangle)
            val result = splitTriangleByPlane(triangle, color, planePointLocal, planeNormalLocal)
            if (result != null) {
                toDelete.add(triangle)
                replacements.addAll(result.triangles)
                result.cutSegment?.let { cutSegments.add(it) }
            }
        }

        if (toDelete.isEmpty()) {
            status.message = "Cut with plane did not intersect the selected faces."
            clearTransient()
            return true
        }

        val addedFaces = mutableListOf<DraftFaceStore.Triangle>()
        faceStore.withChangeSuppressed {
            faceStore.deleteTriangles(toDelete)
            replacements.forEach { replacement ->
                val added = faceStore.appendTriangleRaw(
                    replacement.a,
                    replacement.b,
                    replacement.c,
                    replacement.color
                )
                addedFaces.add(added)
            }
        }
        addedFaces.forEach { faceStore.addSelection(it) }
        faceStore.notifyExternalChange()

        val lineStore = group.lineStore
        val uniqueSegments = dedupeSegments(cutSegments)
        if (uniqueSegments.isNotEmpty()) {
            lineStore.withChangeSuppressed {
                uniqueSegments.forEach { (start, end) ->
                    lineStore.addSegment(start, end, autoCleanup = false)
                }
            }
            lineStore.notifyExternalChange()
        }

        status.message =
            "Cut with plane | split ${toDelete.size} face(s) into ${addedFaces.size} face(s)."
        clearTransient()
        return true
    }

    private fun splitTriangleByPlane(
        triangle: DraftFaceStore.Triangle,
        color: Color,
        planePoint: Vector3,
        planeNormal: Vector3
    ): SplitResult? {
        val vertices = listOf(triangle.a, triangle.b, triangle.c)
        val distances = vertices.map { signedDistance(it, planePoint, planeNormal) }
        val hasPositive = distances.any { it > planeEpsilon }
        val hasNegative = distances.any { it < -planeEpsilon }
        if (!hasPositive || !hasNegative) {
            return null
        }

        val positive = clipPolygon(vertices, distances) { it >= -planeEpsilon }
        val negative = clipPolygon(vertices, distances) { it <= planeEpsilon }
        val triangles = mutableListOf<ReplacementTriangle>()
        appendTriangulated(positive, color, triangles)
        appendTriangulated(negative, color, triangles)
        if (triangles.isEmpty()) {
            return null
        }

        return SplitResult(
            triangles = triangles,
            cutSegment = cutSegment(vertices, distances)
        )
    }

    private fun signedDistance(point: Vector3, planePoint: Vector3, planeNormal: Vector3): Float {
        return Vector3(point).sub(planePoint).dot(planeNormal)
    }

    private fun clipPolygon(
        vertices: List<Vector3>,
        distances: List<Float>,
        inside: (Float) -> Boolean
    ): List<Vector3> {
        val output = mutableListOf<Vector3>()
        vertices.indices.forEach { index ->
            val nextIndex = (index + 1) % vertices.size
            val current = vertices[index]
            val next = vertices[nextIndex]
            val currentDistance = distances[index]
            val nextDistance = distances[nextIndex]
            val currentInside = inside(currentDistance)
            val nextInside = inside(nextDistance)

            when {
                currentInside && nextInside -> output.add(Vector3(next))
                currentInside && !nextInside -> output.add(intersection(current, next, currentDistance, nextDistance))
                !currentInside && nextInside -> {
                    output.add(intersection(current, next, currentDistance, nextDistance))
                    output.add(Vector3(next))
                }
            }
        }
        return cleanPolygon(output)
    }

    private fun intersection(a: Vector3, b: Vector3, da: Float, db: Float): Vector3 {
        val denominator = da - db
        val t = if (abs(denominator) <= planeEpsilon) 0f else (da / denominator).coerceIn(0f, 1f)
        return Vector3(a).lerp(b, t)
    }

    private fun cleanPolygon(points: List<Vector3>): List<Vector3> {
        val cleaned = mutableListOf<Vector3>()
        points.forEach { point ->
            if (cleaned.none { it.dst2(point) <= dedupeEpsilon * dedupeEpsilon }) {
                cleaned.add(Vector3(point))
            }
        }
        if (cleaned.size > 1 && cleaned.first().dst2(cleaned.last()) <= dedupeEpsilon * dedupeEpsilon) {
            cleaned.removeAt(cleaned.lastIndex)
        }
        return cleaned
    }

    private fun appendTriangulated(
        polygon: List<Vector3>,
        color: Color,
        out: MutableList<ReplacementTriangle>
    ) {
        if (polygon.size < 3) {
            return
        }
        val anchor = polygon.first()
        for (index in 1 until polygon.lastIndex) {
            val a = Vector3(anchor)
            val b = Vector3(polygon[index])
            val c = Vector3(polygon[index + 1])
            if (triangleArea2(a, b, c) > planeEpsilon * planeEpsilon) {
                out.add(ReplacementTriangle(a, b, c, Color(color)))
            }
        }
    }

    private fun triangleArea2(a: Vector3, b: Vector3, c: Vector3): Float {
        return Vector3(b).sub(a).crs(Vector3(c).sub(a)).len2()
    }

    private fun cutSegment(vertices: List<Vector3>, distances: List<Float>): Pair<Vector3, Vector3>? {
        val points = mutableListOf<Vector3>()
        vertices.indices.forEach { index ->
            val nextIndex = (index + 1) % vertices.size
            val current = vertices[index]
            val next = vertices[nextIndex]
            val currentDistance = distances[index]
            val nextDistance = distances[nextIndex]

            if (abs(currentDistance) <= planeEpsilon) {
                addUnique(points, current)
            }
            if (currentDistance * nextDistance < -planeEpsilon * planeEpsilon) {
                addUnique(points, intersection(current, next, currentDistance, nextDistance))
            }
            if (abs(nextDistance) <= planeEpsilon) {
                addUnique(points, next)
            }
        }
        return if (points.size >= 2) {
            Vector3(points[0]) to Vector3(points[1])
        } else {
            null
        }
    }

    private fun addUnique(points: MutableList<Vector3>, candidate: Vector3) {
        if (points.none { it.dst2(candidate) <= dedupeEpsilon * dedupeEpsilon }) {
            points.add(Vector3(candidate))
        }
    }

    private fun dedupeSegments(segments: List<Pair<Vector3, Vector3>>): List<Pair<Vector3, Vector3>> {
        val unique = mutableListOf<Pair<Vector3, Vector3>>()
        segments.forEach { segment ->
            if (segment.first.dst2(segment.second) <= dedupeEpsilon * dedupeEpsilon) {
                return@forEach
            }
            val exists = unique.any { existing ->
                samePoint(existing.first, segment.first) && samePoint(existing.second, segment.second) ||
                    samePoint(existing.first, segment.second) && samePoint(existing.second, segment.first)
            }
            if (!exists) {
                unique.add(Vector3(segment.first) to Vector3(segment.second))
            }
        }
        return unique
    }

    private fun samePoint(a: Vector3, b: Vector3): Boolean {
        return a.dst2(b) <= dedupeEpsilon * dedupeEpsilon
    }

    private fun clearTransient() {
        originWorld = null
        hasHover = false
    }

    private data class ReplacementTriangle(
        val a: Vector3,
        val b: Vector3,
        val c: Vector3,
        val color: Color
    )

    private data class SplitResult(
        val triangles: List<ReplacementTriangle>,
        val cutSegment: Pair<Vector3, Vector3>?
    )
}

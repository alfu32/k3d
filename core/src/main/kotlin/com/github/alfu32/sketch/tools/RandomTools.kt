package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolOperator
import com.github.alfu32.sketch.ui.ToolOperatorAction
import kotlin.math.E
import kotlin.math.round
import kotlin.math.pow
import kotlin.random.Random

class RandomOffsetTool(
    private val scene: GroupScene
) : Tool {
    override val id: ToolId = ToolId.RANDOM_OFFSET
    override val message: String = "Random Offset: type strength 0-100, then click to offset selected faces/segments."

    private var strengthInput = 1f

    override fun onEnter(status: StatusModel) {
        status.message = "Random Offset strength ${formatStrength()}. Type a new strength or click to apply."
        status.inputBuffer = formatStrength()
    }

    override fun onTextInput(status: StatusModel, text: String) {
        status.inputBuffer = text
        val parsed = text.trim().toFloatOrNull()
        if (parsed != null) {
            strengthInput = parsed.coerceIn(0f, 100f)
            status.message = "Random Offset strength ${formatStrength()}. Click to apply to selection."
        } else if (text.isBlank()) {
            status.message = "Random Offset: type strength 0-100, then click to apply."
        } else {
            status.message = "Random Offset: invalid strength '$text'."
        }
    }

    override fun toolOperators(status: StatusModel): List<ToolOperator> {
        return listOf(
            ToolOperator("strength", "Strength", ToolOperatorAction.ShowDistanceInput),
            ToolOperator.key("cancel", "Esc", Input.Keys.ESCAPE)
        )
    }

    override fun onPointerDown(
        status: StatusModel,
        world: Vector3?,
        normal: Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        val result = applyRandomOffset()
        status.message = result
        return true
    }

    private fun applyRandomOffset(): String {
        val group = scene.activeGroup()
        val selectedFaces = group.faceStore.getSelected().toList()
        val selectedSegments = group.lineStore.getSelected().toList()
        if (selectedFaces.isEmpty() && selectedSegments.isEmpty()) {
            return "Random Offset: select faces or segments first."
        }

        val targetKeys = linkedSetOf<VertexKey>()
        selectedFaces.forEach { tri ->
            targetKeys += vertexKey(tri.a)
            targetKeys += vertexKey(tri.b)
            targetKeys += vertexKey(tri.c)
        }
        selectedSegments.forEach { segment ->
            targetKeys += vertexKey(segment.start)
            targetKeys += vertexKey(segment.end)
        }
        if (targetKeys.isEmpty()) {
            return "Random Offset: no selected vertices."
        }

        val selectedNormals = averageNormalsFor(targetKeys, selectedFaces)
        val fallbackNormals = averageNormalsFor(targetKeys, group.faceStore.getTriangles())
        val displacementByKey = linkedMapOf<VertexKey, Vector3>()
        val amplitude = (E.toFloat().pow(strengthInput) - 1f).coerceAtLeast(0f)
        val rng = Random(System.nanoTime())
        targetKeys.forEach { key ->
            val direction = selectedNormals[key] ?: fallbackNormals[key] ?: Vector3(0f, 1f, 0f)
            if (direction.len2() > RANDOM_OFFSET_EPSILON_SQ) {
                direction.nor()
            } else {
                direction.set(0f, 1f, 0f)
            }
            val integerNoise = rng.nextInt(-1000, 1001)
            val signedFactor = integerNoise.toFloat() / 1000f
            displacementByKey[key] = direction.scl(amplitude * signedFactor)
        }

        var movedFaceVertices = 0
        group.faceStore.withChangeSuppressed {
            group.faceStore.getTriangles().forEach { tri ->
                movedFaceVertices += moveIfTarget(tri.a, displacementByKey)
                movedFaceVertices += moveIfTarget(tri.b, displacementByKey)
                movedFaceVertices += moveIfTarget(tri.c, displacementByKey)
            }
        }
        group.faceStore.notifyExternalChange()

        var movedSegmentVertices = 0
        group.lineStore.withChangeSuppressed {
            group.lineStore.getSegments().forEach { segment ->
                movedSegmentVertices += moveIfTarget(segment.start, displacementByKey)
                movedSegmentVertices += moveIfTarget(segment.end, displacementByKey)
            }
        }
        group.lineStore.notifyExternalChange()

        return "Random Offset moved ${targetKeys.size} shared vertex/vertices (faces $movedFaceVertices, segments $movedSegmentVertices)."
    }

    private fun averageNormalsFor(
        targetKeys: Set<VertexKey>,
        faces: List<DraftFaceStore.Triangle>
    ): Map<VertexKey, Vector3> {
        val normals = linkedMapOf<VertexKey, Vector3>()
        faces.forEach { tri ->
            val normal = triangleNormal(tri)
            if (normal.len2() <= RANDOM_OFFSET_EPSILON_SQ) {
                return@forEach
            }
            listOf(tri.a, tri.b, tri.c).forEach { point ->
                val key = vertexKey(point)
                if (key in targetKeys) {
                    normals.getOrPut(key) { Vector3() }.add(normal)
                }
            }
        }
        return normals.mapValues { (_, value) ->
            if (value.len2() > RANDOM_OFFSET_EPSILON_SQ) value.nor() else value
        }
    }

    private fun triangleNormal(tri: DraftFaceStore.Triangle): Vector3 {
        return Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a)).nor()
    }

    private fun moveIfTarget(point: Vector3, displacementByKey: Map<VertexKey, Vector3>): Int {
        val displacement = displacementByKey[vertexKey(point)] ?: return 0
        point.add(displacement)
        return 1
    }

    private fun formatStrength(): String {
        val rounded = round(strengthInput * 100f) / 100f
        return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
    }
}

private data class VertexKey(val x: Int, val y: Int, val z: Int)

private const val RANDOM_OFFSET_EPSILON = 0.001f
private const val RANDOM_OFFSET_EPSILON_SQ = RANDOM_OFFSET_EPSILON * RANDOM_OFFSET_EPSILON

private fun vertexKey(point: Vector3): VertexKey {
    return VertexKey(
        round(point.x / RANDOM_OFFSET_EPSILON).toInt(),
        round(point.y / RANDOM_OFFSET_EPSILON).toInt(),
        round(point.z / RANDOM_OFFSET_EPSILON).toInt()
    )
}

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
import com.github.alfu32.sketch.ui.ToolOperator
import com.github.alfu32.sketch.ui.ToolOperatorAction
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.pow
import kotlin.random.Random

data class RandomOffsetConfig(
    val strength: Float = 1f
)

data class RandomSurfaceArrayConfig(
    val count: Int = 12,
    val scaleStrength: Float = 0f,
    val rotationFuzz: Float = 0f,
    val alignToNormal: Boolean = true
)

data class MeshRegularizeConfig(
    val subdivisions: Int = 12,
    val planarTolerance: Float = 0.05f
)

enum class MeshRegularizeMode {
    PLANAR,
    SURFACE
}

class RandomOffsetTool(
    private val scene: GroupScene,
    private val showConfigDialog: ((RandomOffsetConfig, (RandomOffsetConfig) -> Unit, (RandomOffsetConfig) -> Unit, () -> Unit) -> Unit)? = null,
    private val onFinished: (() -> Unit)? = null
) : Tool {
    override val id: ToolId = ToolId.RANDOM_OFFSET
    override val message: String = "Random Offset: type strength 0-100, then click to offset selected faces/segments."

    private var strengthInput = 1f

    override fun onEnter(status: StatusModel) {
        val initialConfig = RandomOffsetConfig(strengthInput)
        status.message = "Random Offset strength ${formatStrength()}. Type a new strength or click to apply."
        status.inputBuffer = formatStrength()
        showConfigDialog?.invoke(
            initialConfig,
            { config ->
                strengthInput = config.strength.coerceAtLeast(0f)
                status.inputBuffer = formatStrength()
                status.message = applyRandomOffset()
                onFinished?.invoke()
            },
            { config ->
                strengthInput = config.strength.coerceAtLeast(0f)
                status.inputBuffer = formatStrength()
                status.message = "Random Offset strength ${formatStrength()}. Preview updated."
            },
            {
                strengthInput = initialConfig.strength
                status.inputBuffer = formatStrength()
                status.message = "Random Offset cancelled."
                onFinished?.invoke()
            }
        )
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

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val preview = randomOffsetPreview() ?: return emptyList()
        return preview.displacedEdges + preview.displacementVectors
    }

    override fun render(renderer: ShapeRenderer) {
        val preview = randomOffsetPreview() ?: return
        renderer.color = ToolFeedbackColors.TRANSLUCENT
        drawPreviewLines(renderer, preview.originalEdges)
        renderer.color = ToolFeedbackColors.TERTIARY
        drawPreviewLines(renderer, preview.displacedEdges)
        renderer.color = ToolFeedbackColors.SECONDARY
        drawPreviewLines(renderer, preview.displacementVectors)
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
        val amplitude = randomOffsetAmplitude(strengthInput)
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

    private fun randomOffsetPreview(): OffsetPreview? {
        val group = scene.activeGroup()
        val selectedFaces = group.faceStore.getSelected().toList()
        val selectedSegments = group.lineStore.getSelected().toList()
        if (selectedFaces.isEmpty() && selectedSegments.isEmpty()) {
            return null
        }

        val targetKeys = selectedVertexKeys(selectedFaces, selectedSegments)
        if (targetKeys.isEmpty()) {
            return null
        }
        val displacementByKey = displacementPreviewFor(group, targetKeys, selectedFaces)
        if (displacementByKey.isEmpty()) {
            return null
        }

        val originalEdges = mutableListOf<Pair<Vector3, Vector3>>()
        val displacedEdges = mutableListOf<Pair<Vector3, Vector3>>()
        val displacementVectors = mutableListOf<Pair<Vector3, Vector3>>()

        fun displaced(point: Vector3): Vector3 {
            val local = Vector3(point).add(displacementByKey[vertexKey(point)] ?: Vector3())
            return group.toWorld(local)
        }

        selectedFaces.forEach { tri ->
            addTriangleEdges(originalEdges, group.toWorld(tri.a), group.toWorld(tri.b), group.toWorld(tri.c))
            addTriangleEdges(displacedEdges, displaced(tri.a), displaced(tri.b), displaced(tri.c))
        }
        selectedSegments.forEach { segment ->
            originalEdges += group.toWorld(segment.start) to group.toWorld(segment.end)
            displacedEdges += displaced(segment.start) to displaced(segment.end)
        }
        targetKeys.forEach { key ->
            val displacement = displacementByKey[key] ?: return@forEach
            val source = firstSelectedPointForKey(key, selectedFaces, selectedSegments) ?: return@forEach
            val a = group.toWorld(source)
            val b = group.toWorld(Vector3(source).add(displacement))
            if (a.dst2(b) > RANDOM_OFFSET_EPSILON_SQ) {
                displacementVectors += a to b
            }
        }

        return OffsetPreview(originalEdges, displacedEdges, displacementVectors)
    }

    private fun selectedVertexKeys(
        selectedFaces: List<DraftFaceStore.Triangle>,
        selectedSegments: List<com.github.alfu32.sketch.model.DraftLineStore.Segment>
    ): Set<VertexKey> {
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
        return targetKeys
    }

    private fun displacementPreviewFor(
        group: GroupScene.GroupNode,
        targetKeys: Set<VertexKey>,
        selectedFaces: List<DraftFaceStore.Triangle>
    ): Map<VertexKey, Vector3> {
        val selectedNormals = averageNormalsFor(targetKeys, selectedFaces)
        val fallbackNormals = averageNormalsFor(targetKeys, group.faceStore.getTriangles())
        val amplitude = randomOffsetAmplitude(strengthInput)
        val rng = Random(RANDOM_PREVIEW_SEED)
        return targetKeys.associateWith { key ->
            val direction = selectedNormals[key] ?: fallbackNormals[key] ?: Vector3(0f, 1f, 0f)
            if (direction.len2() > RANDOM_OFFSET_EPSILON_SQ) {
                direction.nor()
            } else {
                direction.set(0f, 1f, 0f)
            }
            val signedFactor = rng.nextInt(-1000, 1001).toFloat() / 1000f
            direction.scl(amplitude * signedFactor)
        }
    }

    private fun firstSelectedPointForKey(
        key: VertexKey,
        selectedFaces: List<DraftFaceStore.Triangle>,
        selectedSegments: List<com.github.alfu32.sketch.model.DraftLineStore.Segment>
    ): Vector3? {
        selectedFaces.forEach { tri ->
            listOf(tri.a, tri.b, tri.c).firstOrNull { vertexKey(it) == key }?.let { return it }
        }
        selectedSegments.forEach { segment ->
            listOf(segment.start, segment.end).firstOrNull { vertexKey(it) == key }?.let { return it }
        }
        return null
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

    private fun randomOffsetAmplitude(value: Float): Float {
        return ((E.toFloat().pow(value) - 1f).coerceAtLeast(0f)) / 10f
    }

    private data class OffsetPreview(
        val originalEdges: List<Pair<Vector3, Vector3>>,
        val displacedEdges: List<Pair<Vector3, Vector3>>,
        val displacementVectors: List<Pair<Vector3, Vector3>>
    )
}

class RandomSurfaceArrayTool(
    private val scene: GroupScene,
    private val showConfigDialog: ((RandomSurfaceArrayConfig, (RandomSurfaceArrayConfig) -> Unit, (RandomSurfaceArrayConfig) -> Unit, () -> Unit) -> Unit)? = null,
    private val onFinished: (() -> Unit)? = null
) : Tool {
    override val id: ToolId = ToolId.RANDOM_SURFACE_ARRAY
    override val message: String =
        "Random Surface Array: select distribution faces and payload objects or segments. Type: count scale fuzz align."

    private var count = 12
    private var scaleStrength = 0f
    private var rotationFuzz = 0f
    private var alignToNormal = true

    override fun onEnter(status: StatusModel) {
        val initialConfig = currentConfig()
        status.inputBuffer = formatConfig()
        status.message = "Random Surface Array: ${formatConfig()}. Click to scatter selected objects or segments on selected faces."
        showConfigDialog?.invoke(
            initialConfig,
            { config ->
                count = config.count.coerceIn(1, 5000)
                scaleStrength = config.scaleStrength.coerceIn(0f, 100f)
                rotationFuzz = config.rotationFuzz.coerceIn(0f, 100f)
                alignToNormal = config.alignToNormal
                status.inputBuffer = formatConfig()
                status.message = applyArray()
                onFinished?.invoke()
            },
            { config ->
                count = config.count.coerceIn(1, 5000)
                scaleStrength = config.scaleStrength.coerceIn(0f, 100f)
                rotationFuzz = config.rotationFuzz.coerceIn(0f, 100f)
                alignToNormal = config.alignToNormal
                status.inputBuffer = formatConfig()
                status.message = "Random Surface Array: ${formatConfig()}. Preview updated."
            },
            {
                count = initialConfig.count
                scaleStrength = initialConfig.scaleStrength
                rotationFuzz = initialConfig.rotationFuzz
                alignToNormal = initialConfig.alignToNormal
                status.inputBuffer = formatConfig()
                status.message = "Random Surface Array cancelled."
                onFinished?.invoke()
            }
        )
    }

    override fun onTextInput(status: StatusModel, text: String) {
        status.inputBuffer = text
        parseConfig(text)
        status.message = "Random Surface Array: ${formatConfig()}. Click to apply."
    }

    override fun toolOperators(status: StatusModel): List<ToolOperator> {
        return listOf(
            ToolOperator("config", "Config", ToolOperatorAction.ShowDistanceInput),
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
        status.message = applyArray()
        return true
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val preview = surfaceArrayPreview() ?: return emptyList()
        return preview.surfaceEdges + preview.payloadEdges + preview.normalTicks
    }

    override fun render(renderer: ShapeRenderer) {
        val preview = surfaceArrayPreview() ?: return
        renderer.color = ToolFeedbackColors.TRANSLUCENT
        drawPreviewLines(renderer, preview.surfaceEdges)
        renderer.color = ToolFeedbackColors.TERTIARY
        drawPreviewLines(renderer, preview.payloadEdges)
        renderer.color = ToolFeedbackColors.SECONDARY
        drawPreviewLines(renderer, preview.normalTicks)
    }

    private fun parseConfig(text: String) {
        val parts = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        parts.getOrNull(0)?.toIntOrNull()?.let { count = it.coerceIn(1, 5000) }
        parts.getOrNull(1)?.toFloatOrNull()?.let { scaleStrength = it.coerceIn(0f, 100f) }
        parts.getOrNull(2)?.toFloatOrNull()?.let { rotationFuzz = it.coerceIn(0f, 100f) }
        parts.getOrNull(3)?.let { alignToNormal = it.equals("true", true) || it == "1" || it.equals("yes", true) }
    }

    private fun formatConfig(): String {
        return "$count ${formatFloat(scaleStrength)} ${formatFloat(rotationFuzz)} ${if (alignToNormal) 1 else 0}"
    }

    private fun currentConfig(): RandomSurfaceArrayConfig {
        return RandomSurfaceArrayConfig(
            count = count,
            scaleStrength = scaleStrength,
            rotationFuzz = rotationFuzz,
            alignToNormal = alignToNormal
        )
    }

    private fun applyArray(): String {
        val group = scene.activeGroup()
        val surface = group.faceStore.getSelected().toList()
        if (surface.isEmpty()) {
            return "Random Surface Array: select one or more faces as the target surface."
        }
        val selectedGroups = scene.selectedGroups().toList()
        val selectedSegments = group.lineStore.getSelected().toList()
        if (selectedGroups.isEmpty() && selectedSegments.isEmpty()) {
            return "Random Surface Array: select object instances or segments to scatter."
        }

        val weighted = surface.mapNotNull { tri ->
            val area = triangleArea(tri)
            if (area > RANDOM_OFFSET_EPSILON) WeightedTriangle(tri, area) else null
        }
        val totalArea = weighted.sumOf { it.area.toDouble() }.toFloat()
        if (totalArea <= RANDOM_OFFSET_EPSILON) {
            return "Random Surface Array: selected surface area is too small."
        }

        val rng = Random(System.nanoTime())
        var copiedGroups = 0
        var copiedSegments = 0
        val segmentCentroid = if (selectedSegments.isNotEmpty()) centroidOfSegments(selectedSegments) else Vector3()

        repeat(count) {
            val sample = sampleSurface(weighted, totalArea, rng)
            val normal = triangleNormal(sample.triangle)
            val scale = randomScale(rng)
            val twist = randomSignedUnit(rng) * rotationAmountRadians()

            selectedGroups.forEach { source ->
                val sourceOrigin = source.worldOrigin()
                scene.clearGroupSelection()
                scene.addGroupSelection(source)
                scene.copySelectedGroups(
                    pointTransform = { point ->
                        val local = Vector3(point).sub(sourceOrigin)
                        val transformed = transformPayloadVector(local, normal, scale, twist)
                        Vector3(sample.point).add(transformed)
                    },
                    vectorTransform = { vector -> transformPayloadVector(vector, normal, scale, twist) }
                )
                copiedGroups++
            }

            selectedSegments.forEach { segment ->
                val a = Vector3(segment.start).sub(segmentCentroid)
                val b = Vector3(segment.end).sub(segmentCentroid)
                val nextA = group.toLocal(Vector3(sample.point).add(transformPayloadVector(group.vectorToWorld(a), normal, scale, twist)))
                val nextB = group.toLocal(Vector3(sample.point).add(transformPayloadVector(group.vectorToWorld(b), normal, scale, twist)))
                group.lineStore.appendSegmentRaw(nextA, nextB)
                copiedSegments++
            }
        }

        scene.clearGroupSelection()
        selectedGroups.forEach { scene.addGroupSelection(it) }
        group.lineStore.notifyExternalChange()
        return "Random Surface Array copied $copiedGroups object instance(s) and $copiedSegments segment(s)."
    }

    private fun surfaceArrayPreview(): SurfaceArrayPreview? {
        val group = scene.activeGroup()
        val surface = group.faceStore.getSelected().toList()
        if (surface.isEmpty()) {
            return null
        }
        val selectedGroups = scene.selectedGroups().toList()
        val selectedSegments = group.lineStore.getSelected().toList()
        if (selectedGroups.isEmpty() && selectedSegments.isEmpty()) {
            return SurfaceArrayPreview(surfaceEdges(surface, group), emptyList(), emptyList())
        }

        val weighted = surface.mapNotNull { tri ->
            val area = triangleArea(tri)
            if (area > RANDOM_OFFSET_EPSILON) WeightedTriangle(tri, area) else null
        }
        val totalArea = weighted.sumOf { it.area.toDouble() }.toFloat()
        if (totalArea <= RANDOM_OFFSET_EPSILON) {
            return SurfaceArrayPreview(surfaceEdges(surface, group), emptyList(), emptyList())
        }

        val rng = Random(RANDOM_PREVIEW_SEED)
        val payloadEdges = mutableListOf<Pair<Vector3, Vector3>>()
        val normalTicks = mutableListOf<Pair<Vector3, Vector3>>()
        val segmentCentroid = if (selectedSegments.isNotEmpty()) centroidOfSegments(selectedSegments) else Vector3()
        val previewCount = count.coerceAtMost(RANDOM_SURFACE_ARRAY_PREVIEW_LIMIT)

        repeat(previewCount) {
            val sample = sampleSurface(weighted, totalArea, rng)
            val normal = triangleNormal(sample.triangle)
            val scale = randomScale(rng)
            val twist = randomSignedUnit(rng) * rotationAmountRadians()
            val normalWorld = group.vectorToWorld(Vector3(normal)).nor()
            normalTicks += sample.point to Vector3(sample.point).add(Vector3(normalWorld).scl(SURFACE_ARRAY_NORMAL_TICK))

            selectedGroups.forEach { source ->
                appendGroupPreviewEdges(source, sample.point, normal, scale, twist, payloadEdges)
            }

            selectedSegments.forEach { segment ->
                val a = Vector3(segment.start).sub(segmentCentroid)
                val b = Vector3(segment.end).sub(segmentCentroid)
                val nextA = Vector3(sample.point).add(transformPayloadVector(group.vectorToWorld(a), normal, scale, twist))
                val nextB = Vector3(sample.point).add(transformPayloadVector(group.vectorToWorld(b), normal, scale, twist))
                payloadEdges += nextA to nextB
            }
        }

        return SurfaceArrayPreview(surfaceEdges(surface, group), payloadEdges, normalTicks)
    }

    private fun appendGroupPreviewEdges(
        source: GroupScene.GroupNode,
        samplePoint: Vector3,
        normal: Vector3,
        scale: Float,
        twist: Float,
        target: MutableList<Pair<Vector3, Vector3>>
    ) {
        val origin = source.worldOrigin()
        fun transformed(localPoint: Vector3): Vector3 {
            val world = source.toWorld(localPoint)
            val relative = Vector3(world).sub(origin)
            return Vector3(samplePoint).add(transformPayloadVector(relative, normal, scale, twist))
        }
        source.lineStore.getSegments().forEach { segment ->
            target += transformed(segment.start) to transformed(segment.end)
        }
        source.faceStore.getTriangles().forEach { tri ->
            addTriangleEdges(target, transformed(tri.a), transformed(tri.b), transformed(tri.c))
        }
    }

    private fun transformPayloadVector(vectorWorld: Vector3, surfaceNormal: Vector3, scale: Float, twistRadians: Float): Vector3 {
        val result = Vector3(vectorWorld).scl(scale)
        if (alignToNormal && surfaceNormal.len2() > RANDOM_OFFSET_EPSILON_SQ) {
            val up = Vector3(0f, 1f, 0f)
            val target = Vector3(surfaceNormal).nor()
            val axis = Vector3(up).crs(target)
            val dot = up.dot(target).coerceIn(-1f, 1f)
            if (axis.len2() > RANDOM_OFFSET_EPSILON_SQ) {
                result.rotateRad(axis.nor(), kotlin.math.acos(dot))
            } else if (dot < 0f) {
                result.rotateRad(Vector3(1f, 0f, 0f), PI.toFloat())
            }
        }
        if (kotlin.math.abs(twistRadians) > RANDOM_OFFSET_EPSILON) {
            val axis = if (surfaceNormal.len2() > RANDOM_OFFSET_EPSILON_SQ) Vector3(surfaceNormal).nor() else Vector3(0f, 1f, 0f)
            result.rotateRad(axis, twistRadians)
        }
        return result
    }

    private fun randomScale(rng: Random): Float {
        if (scaleStrength <= 0f) {
            return 1f
        }
        val amplitude = (E.toFloat().pow(scaleStrength) - 1f).coerceAtMost(10_000f)
        return (1f + randomSignedUnit(rng) * amplitude).coerceAtLeast(0.001f)
    }

    private fun rotationAmountRadians(): Float {
        if (rotationFuzz <= 0f) {
            return 0f
        }
        val degrees = (E.toFloat().pow(rotationFuzz) - 1f).coerceAtMost(360f)
        return degrees * MathUtilsDegreesToRadians
    }

    private fun sampleSurface(weighted: List<WeightedTriangle>, totalArea: Float, rng: Random): SurfaceSample {
        var target = rng.nextFloat() * totalArea
        val tri = weighted.firstOrNull { item ->
            target -= item.area
            target <= 0f
        }?.triangle ?: weighted.last().triangle
        val r1 = rng.nextFloat()
        val r2 = rng.nextFloat()
        val sqrtR1 = kotlin.math.sqrt(r1)
        val u = 1f - sqrtR1
        val v = sqrtR1 * (1f - r2)
        val w = sqrtR1 * r2
        val point = Vector3(tri.a).scl(u).add(Vector3(tri.b).scl(v)).add(Vector3(tri.c).scl(w))
        return SurfaceSample(tri, groupWorld(point))
    }

    private fun groupWorld(local: Vector3): Vector3 = scene.activeGroup().toWorld(local)

    private data class WeightedTriangle(val triangle: DraftFaceStore.Triangle, val area: Float)
    private data class SurfaceSample(val triangle: DraftFaceStore.Triangle, val point: Vector3)
    private data class SurfaceArrayPreview(
        val surfaceEdges: List<Pair<Vector3, Vector3>>,
        val payloadEdges: List<Pair<Vector3, Vector3>>,
        val normalTicks: List<Pair<Vector3, Vector3>>
    )
}

class MeshRegularizeTool(
    private val scene: GroupScene,
    private val toolId: ToolId = ToolId.MESH_REGULARIZE,
    private val mode: MeshRegularizeMode = MeshRegularizeMode.PLANAR,
    private val showConfigDialog: ((MeshRegularizeConfig, (MeshRegularizeConfig) -> Unit, (MeshRegularizeConfig) -> Unit, () -> Unit) -> Unit)? = null,
    private val onFinished: (() -> Unit)? = null
) : Tool {
    override val id: ToolId = toolId
    override val message: String = "${toolLabel()}: select faces, configure subdivisions and tolerance, then remesh."

    private var subdivisionCount = 12
    private var planarTolerance = 0.05f
    private var lastRegularizeFailure = "${toolLabel()}: could not build a regular square grid from the selection."

    override fun onEnter(status: StatusModel) {
        val initialConfig = MeshRegularizeConfig(subdivisionCount, planarTolerance)
        status.inputBuffer = formatConfig()
        status.message = "${toolLabel()} ${formatConfig()}. Select a face patch and confirm."
        showConfigDialog?.invoke(
            initialConfig,
            { config ->
                subdivisionCount = config.subdivisions.coerceIn(1, 512)
                planarTolerance = config.planarTolerance.coerceAtLeast(0.0001f)
                status.inputBuffer = formatConfig()
                status.message = regularize()
                onFinished?.invoke()
            },
            { config ->
                subdivisionCount = config.subdivisions.coerceIn(1, 512)
                planarTolerance = config.planarTolerance.coerceAtLeast(0.0001f)
                status.inputBuffer = formatConfig()
                status.message = "${toolLabel()} ${formatConfig()}. Preview updated."
            },
            {
                subdivisionCount = initialConfig.subdivisions
                planarTolerance = initialConfig.planarTolerance
                status.inputBuffer = formatConfig()
                status.message = "${toolLabel()} cancelled."
                onFinished?.invoke()
            }
        )
    }

    override fun onTextInput(status: StatusModel, text: String) {
        status.inputBuffer = text
        val parts = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        parts.getOrNull(0)?.toIntOrNull()?.let { subdivisionCount = it.coerceIn(1, 512) }
        parts.getOrNull(1)?.toFloatOrNull()?.let { planarTolerance = it.coerceAtLeast(0.0001f) }
        status.message = "${toolLabel()} ${formatConfig()}. Click to remesh selected faces."
    }

    override fun toolOperators(status: StatusModel): List<ToolOperator> {
        return listOf(
            ToolOperator("config", "Config", ToolOperatorAction.ShowDistanceInput),
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
        status.message = regularize()
        return true
    }

    override fun feedbackLines(): List<Pair<Vector3, Vector3>> {
        val preview = regularizePreview() ?: return emptyList()
        return preview.sourceEdges + preview.gridEdges
    }

    override fun render(renderer: ShapeRenderer) {
        val preview = regularizePreview() ?: return
        renderer.color = ToolFeedbackColors.TRANSLUCENT
        drawPreviewLines(renderer, preview.sourceEdges)
        renderer.color = ToolFeedbackColors.TERTIARY
        drawPreviewLines(renderer, preview.gridEdges)
    }

    private fun regularize(): String {
        val group = scene.activeGroup()
        val selected = group.faceStore.getSelected().toList()
        if (selected.isEmpty()) {
            return "${toolLabel()}: select faces first."
        }
        val result = when (mode) {
            MeshRegularizeMode.PLANAR -> buildPlanarRegularizedFaces(selected)
            MeshRegularizeMode.SURFACE -> buildSurfaceRemeshedFaces(selected)
        }
            ?: return lastRegularizeFailure
        val newFaces = result.faces
        if (newFaces.isEmpty()) {
            return "${toolLabel()}: no regular cells fell inside the selected patch."
        }

        val color = selected.firstOrNull()?.let { group.faceStore.colorFor(it) } ?: Color(0.93f, 0.93f, 0.93f, 1f)
        group.faceStore.withChangeSuppressed {
            group.faceStore.deleteTriangles(selected)
            newFaces.forEach { (a, b, c) ->
                val tri = group.faceStore.appendTriangleRaw(a, b, c, color)
                group.faceStore.addSelection(tri)
            }
        }
        group.lineStore.withChangeSuppressed {
            result.edges.forEach { (a, b) ->
                group.lineStore.addSegment(a, b, autoCleanup = false)
            }
        }
        group.faceStore.notifyExternalChange()
        group.lineStore.notifyExternalChange()
        return "${toolLabel()} replaced ${selected.size} face(s) with ${newFaces.size} regular triangle(s) and ${result.edges.size} border segment(s)."
    }

    private fun regularizePreview(): RegularizePreview? {
        val group = scene.activeGroup()
        val selected = group.faceStore.getSelected().toList()
        if (selected.isEmpty()) {
            return null
        }
        val sourceEdges = surfaceEdges(selected, group)
        val result = when (mode) {
            MeshRegularizeMode.PLANAR -> buildPlanarRegularizedFaces(selected)
            MeshRegularizeMode.SURFACE -> buildSurfaceRemeshedFaces(selected)
        }
        val gridEdges = result?.edges.orEmpty().map { (a, b) -> group.toWorld(a) to group.toWorld(b) }
        return RegularizePreview(sourceEdges, gridEdges)
    }

    private fun buildPlanarRegularizedFaces(selected: List<DraftFaceStore.Triangle>): RegularizeBuildResult? {
        lastRegularizeFailure = "Planar Regularize: could not build a regular square grid on the selected near-planar patch."
        val normal = averageNormal(selected)
        if (normal.len2() <= RANDOM_OFFSET_EPSILON_SQ) {
            lastRegularizeFailure = "Planar Regularize: selected patch has no stable averaged normal."
            return null
        }
        normal.nor()
        val origin = centroidOfFaces(selected)
        val basis = planeBasisFromNormal(normal)
        val projected = selected.map { tri ->
            ProjectedTriangle(
                tri,
                project2d(tri.a, origin, basis),
                project2d(tri.b, origin, basis),
                project2d(tri.c, origin, basis)
            )
        }
        if (!isNearPlanar(selected, origin, normal, planarTolerance)) {
            lastRegularizeFailure = "Planar Regularize: selected patch exceeds planar tolerance ${formatFloat(planarTolerance)}."
            return null
        }
        val grid = projectedGrid(projected) ?: return null
        val newFaces = mutableListOf<Triple<Vector3, Vector3, Vector3>>()
        val edgeMap = linkedMapOf<EdgeKey, Pair<Vector3, Vector3>>()
        iterateGridCells(grid) { p00, p10, p11, p01 ->
            addPlanarRegularizedCell(projected, p00, p10, p11, p01, origin, basis, newFaces, edgeMap)
        }
        if (newFaces.isEmpty()) {
            lastRegularizeFailure = "Planar Regularize: no regular cells fit inside the selected patch."
        }
        return RegularizeBuildResult(newFaces, edgeMap.values.toList())
    }

    private fun buildSurfaceRemeshedFaces(selected: List<DraftFaceStore.Triangle>): RegularizeBuildResult? {
        lastRegularizeFailure = "Surface Remesh: could not project a regular square grid onto the selected open surface patch."
        val normal = averageNormal(selected)
        if (normal.len2() <= RANDOM_OFFSET_EPSILON_SQ) {
            lastRegularizeFailure = "Surface Remesh: selected surface has no stable averaged normal."
            return null
        }
        normal.nor()
        val origin = centroidOfFaces(selected)
        val basis = planeBasisFromNormal(normal)
        val projected = selected.map { tri ->
            ProjectedTriangle(
                tri,
                project2d(tri.a, origin, basis),
                project2d(tri.b, origin, basis),
                project2d(tri.c, origin, basis)
            )
        }
        val boundaryLoop = buildBoundaryLoop(selected, origin, basis) ?: return null

        val grid = projectedGrid(projected) ?: return null
        val newFaces = mutableListOf<Triple<Vector3, Vector3, Vector3>>()
        val edgeMap = linkedMapOf<EdgeKey, Pair<Vector3, Vector3>>()
        val raySpan = computeRegularizeRaySpan(selected, origin, normal, grid.extent)

        iterateGridCells(grid) { p00, p10, p11, p01 ->
            addSurfaceRemeshCell(
                projected,
                boundaryLoop,
                p00,
                p10,
                p11,
                p01,
                origin,
                basis,
                normal,
                raySpan,
                grid.cellSize,
                planarTolerance,
                newFaces,
                edgeMap
            )
        }
        if (newFaces.isEmpty()) {
            lastRegularizeFailure = "Surface Remesh: no regular cells fit inside the selected open surface patch."
        }
        return RegularizeBuildResult(newFaces, edgeMap.values.toList())
    }

    private fun addPlanarRegularizedCell(
        source: List<ProjectedTriangle>,
        a: Vec2,
        b: Vec2,
        c: Vec2,
        d: Vec2,
        origin: Vector3,
        basis: PlaneBasis,
        target: MutableList<Triple<Vector3, Vector3, Vector3>>,
        edgeMap: MutableMap<EdgeKey, Pair<Vector3, Vector3>>
    ) {
        val center = Vec2((a.x + b.x + c.x + d.x) / 4f, (a.y + b.y + c.y + d.y) / 4f)
        if (source.none { pointInTriangle(center, it.a, it.b, it.c) }) {
            return
        }
        val a3 = unproject2d(a, origin, basis)
        val b3 = unproject2d(b, origin, basis)
        val c3 = unproject2d(c, origin, basis)
        val d3 = unproject2d(d, origin, basis)
        addRegularizedQuad(a3, b3, c3, d3, target, edgeMap)
    }

    private fun addSurfaceRemeshCell(
        source: List<ProjectedTriangle>,
        boundaryLoop: BoundaryLoop,
        a: Vec2,
        b: Vec2,
        c: Vec2,
        d: Vec2,
        origin: Vector3,
        basis: PlaneBasis,
        surfaceNormal: Vector3,
        raySpan: Float,
        cellSize: Float,
        tolerance: Float,
        target: MutableList<Triple<Vector3, Vector3, Vector3>>,
        edgeMap: MutableMap<EdgeKey, Pair<Vector3, Vector3>>
    ) {
        val center = Vec2((a.x + b.x + c.x + d.x) / 4f, (a.y + b.y + c.y + d.y) / 4f)
        if (!pointInPolygon(center, boundaryLoop.polygon)) {
            return
        }
        val a3 = projectRegularizedNode(a, source, boundaryLoop, origin, basis, surfaceNormal, raySpan, cellSize, tolerance) ?: return
        val b3 = projectRegularizedNode(b, source, boundaryLoop, origin, basis, surfaceNormal, raySpan, cellSize, tolerance) ?: return
        val c3 = projectRegularizedNode(c, source, boundaryLoop, origin, basis, surfaceNormal, raySpan, cellSize, tolerance) ?: return
        val d3 = projectRegularizedNode(d, source, boundaryLoop, origin, basis, surfaceNormal, raySpan, cellSize, tolerance) ?: return
        addRegularizedQuad(a3, b3, c3, d3, target, edgeMap)
    }

    private fun addRegularizedQuad(
        a3: Vector3,
        b3: Vector3,
        c3: Vector3,
        d3: Vector3,
        target: MutableList<Triple<Vector3, Vector3, Vector3>>,
        edgeMap: MutableMap<EdgeKey, Pair<Vector3, Vector3>>
    ) {
        target += Triple(a3, b3, c3)
        target += Triple(a3, c3, d3)
        registerEdge(edgeMap, a3, b3)
        registerEdge(edgeMap, b3, c3)
        registerEdge(edgeMap, c3, d3)
        registerEdge(edgeMap, d3, a3)
    }

    private fun registerEdge(edgeMap: MutableMap<EdgeKey, Pair<Vector3, Vector3>>, a: Vector3, b: Vector3) {
        val keyA = vertexKey(a)
        val keyB = vertexKey(b)
        val key = if (compareVertexKeys(keyA, keyB) <= 0) EdgeKey(keyA, keyB) else EdgeKey(keyB, keyA)
        edgeMap.putIfAbsent(key, Vector3(a) to Vector3(b))
    }

    private fun formatConfig(): String {
        return "${subdivisionCount} ${formatFloat(planarTolerance)}"
    }

    private fun toolLabel(): String {
        return when (mode) {
            MeshRegularizeMode.PLANAR -> "Planar Regularize"
            MeshRegularizeMode.SURFACE -> "Surface Remesh"
        }
    }

    private fun projectedGrid(projected: List<ProjectedTriangle>): ProjectedGrid? {
        val minU = projected.minOf { min(it.a.x, min(it.b.x, it.c.x)) }
        val maxU = projected.maxOf { max(it.a.x, max(it.b.x, it.c.x)) }
        val minV = projected.minOf { min(it.a.y, min(it.b.y, it.c.y)) }
        val maxV = projected.maxOf { max(it.a.y, max(it.b.y, it.c.y)) }
        val extent = max(maxU - minU, maxV - minV)
        if (extent <= RANDOM_OFFSET_EPSILON) {
            return null
        }
        val cellSize = (extent / subdivisionCount.toFloat()).coerceAtLeast(RANDOM_OFFSET_EPSILON)
        return ProjectedGrid(
            firstU = floor(minU / cellSize) * cellSize,
            lastU = ceil(maxU / cellSize) * cellSize,
            firstV = floor(minV / cellSize) * cellSize,
            lastV = ceil(maxV / cellSize) * cellSize,
            cellSize = cellSize,
            extent = extent
        )
    }

    private fun iterateGridCells(grid: ProjectedGrid, block: (Vec2, Vec2, Vec2, Vec2) -> Unit) {
        var u = grid.firstU
        while (u < grid.lastU - RANDOM_OFFSET_EPSILON) {
            var v = grid.firstV
            while (v < grid.lastV - RANDOM_OFFSET_EPSILON) {
                val p00 = Vec2(u, v)
                val p10 = Vec2((u + grid.cellSize).coerceAtMost(grid.lastU), v)
                val p11 = Vec2((u + grid.cellSize).coerceAtMost(grid.lastU), (v + grid.cellSize).coerceAtMost(grid.lastV))
                val p01 = Vec2(u, (v + grid.cellSize).coerceAtMost(grid.lastV))
                block(p00, p10, p11, p01)
                v += grid.cellSize
            }
            u += grid.cellSize
        }
    }

    private fun computeRegularizeRaySpan(
        faces: List<DraftFaceStore.Triangle>,
        origin: Vector3,
        normal: Vector3,
        extent: Float
    ): Float {
        val maxOffset = faces
            .flatMap { listOf(it.a, it.b, it.c) }
            .maxOf { point -> kotlin.math.abs(Vector3(point).sub(origin).dot(normal)) }
        return max(extent * 2f, maxOffset + extent + 1f)
    }

    private fun projectRegularizedNode(
        point: Vec2,
        source: List<ProjectedTriangle>,
        boundaryLoop: BoundaryLoop,
        origin: Vector3,
        basis: PlaneBasis,
        surfaceNormal: Vector3,
        raySpan: Float,
        cellSize: Float,
        tolerance: Float
    ): Vector3? {
        nearestBoundaryProjection(point, boundaryLoop, max(cellSize * 0.6f, tolerance * 4f))?.let { return it }
        val planePoint = unproject2d(point, origin, basis)
        val rayDir = Vector3(surfaceNormal).nor()
        val candidates = mutableListOf<Vector3>()

        val aboveOrigin = Vector3(planePoint).mulAdd(rayDir, raySpan)
        rayTriangleHit(aboveOrigin, Vector3(rayDir).scl(-1f), source, tolerance)?.let(candidates::add)

        val belowOrigin = Vector3(planePoint).mulAdd(rayDir, -raySpan)
        rayTriangleHit(belowOrigin, Vector3(rayDir), source, tolerance)?.let(candidates::add)

        if (candidates.isEmpty()) {
            return null
        }
        return candidates.minByOrNull { candidate -> candidate.dst2(planePoint) }?.let(::Vector3)
    }

    private fun rayTriangleHit(
        origin: Vector3,
        direction: Vector3,
        source: List<ProjectedTriangle>,
        tolerance: Float
    ): Vector3? {
        var bestT = Float.POSITIVE_INFINITY
        var bestPoint: Vector3? = null
        source.forEach { projected ->
            val t = rayTriangleIntersection(origin, direction, projected.triangle, tolerance) ?: return@forEach
            if (t < bestT) {
                bestT = t
                bestPoint = Vector3(origin).mulAdd(direction, t)
            }
        }
        return bestPoint
    }

    private fun nearestBoundaryProjection(point: Vec2, boundaryLoop: BoundaryLoop, threshold: Float): Vector3? {
        var bestDistanceSq = Float.POSITIVE_INFINITY
        var bestPoint: Vector3? = null
        boundaryLoop.segments.forEach { segment ->
            val nearest = closestPointOnSegment2d(point, segment.uvStart, segment.uvEnd)
            val distanceSq = dst2(point, nearest)
            if (distanceSq < bestDistanceSq) {
                val uvLengthSq = dst2(segment.uvStart, segment.uvEnd)
                val t = if (uvLengthSq <= RANDOM_OFFSET_EPSILON_SQ) 0f else
                    (((nearest.x - segment.uvStart.x) * (segment.uvEnd.x - segment.uvStart.x)) +
                        ((nearest.y - segment.uvStart.y) * (segment.uvEnd.y - segment.uvStart.y))) / uvLengthSq
                bestDistanceSq = distanceSq
                bestPoint = Vector3(segment.start3d).lerp(segment.end3d, t.coerceIn(0f, 1f))
            }
        }
        return if (bestDistanceSq <= threshold * threshold) bestPoint else null
    }

    private fun buildBoundaryLoop(
        faces: List<DraftFaceStore.Triangle>,
        origin: Vector3,
        basis: PlaneBasis
    ): BoundaryLoop? {
        val boundaryEdges = mutableMapOf<EdgeKey, BoundaryEdgeBuilder>()
        faces.forEach { tri ->
            accumulateBoundaryEdge(boundaryEdges, tri.a, tri.b, origin, basis)
            accumulateBoundaryEdge(boundaryEdges, tri.b, tri.c, origin, basis)
            accumulateBoundaryEdge(boundaryEdges, tri.c, tri.a, origin, basis)
        }
        val edges = boundaryEdges.values.filter { it.count == 1 }
        if (edges.isEmpty()) {
            lastRegularizeFailure = "Mesh Regularize: closed surfaces are not supported yet. Select a single open patch."
            return null
        }
        val adjacency = linkedMapOf<VertexKey, MutableList<BoundaryEdge>>()
        edges.forEach { edge ->
            val built = edge.toBoundaryEdge()
            adjacency.getOrPut(built.startKey) { mutableListOf() }.add(built)
            adjacency.getOrPut(built.endKey) { mutableListOf() }.add(built)
        }
        if (adjacency.values.any { it.size != 2 }) {
            lastRegularizeFailure = "Mesh Regularize: expected one open-patch boundary loop without branches."
            return null
        }
        val start = edges.first().toBoundaryEdge()
        val ordered = mutableListOf<BoundarySegmentSample>()
        val visited = mutableSetOf<EdgeKey>()
        var currentKey = start.startKey
        var previousKey: VertexKey? = null
        var safety = 0
        while (safety++ < edges.size + 2) {
            val nextEdge = adjacency[currentKey].orEmpty()
                .firstOrNull { edge -> edge.key !in visited && edge.other(currentKey) != previousKey }
                ?: adjacency[currentKey].orEmpty().firstOrNull { edge -> edge.key !in visited }
                ?: break
            visited += nextEdge.key
            val forward = nextEdge.orientedFrom(currentKey)
            ordered += forward
            previousKey = currentKey
            currentKey = nextEdge.other(currentKey)
            if (currentKey == start.startKey) {
                break
            }
        }
        if (ordered.size != edges.size || currentKey != start.startKey) {
            lastRegularizeFailure = "Mesh Regularize: could not order the boundary as a single loop."
            return null
        }
        return BoundaryLoop(ordered.map { it.uvStart }, ordered)
    }

    private fun accumulateBoundaryEdge(
        map: MutableMap<EdgeKey, BoundaryEdgeBuilder>,
        start: Vector3,
        end: Vector3,
        origin: Vector3,
        basis: PlaneBasis
    ) {
        val startKey = vertexKey(start)
        val endKey = vertexKey(end)
        val key = if (compareVertexKeys(startKey, endKey) <= 0) EdgeKey(startKey, endKey) else EdgeKey(endKey, startKey)
        val builder = map.getOrPut(key) {
            BoundaryEdgeBuilder(
                key = key,
                startKey = startKey,
                endKey = endKey,
                start3d = Vector3(start),
                end3d = Vector3(end),
                uvStart = project2d(start, origin, basis),
                uvEnd = project2d(end, origin, basis)
            )
        }
        builder.count++
    }

    private data class ProjectedTriangle(
        val triangle: DraftFaceStore.Triangle,
        val a: Vec2,
        val b: Vec2,
        val c: Vec2
    )
    private data class RegularizeBuildResult(
        val faces: List<Triple<Vector3, Vector3, Vector3>>,
        val edges: List<Pair<Vector3, Vector3>>
    )
    private data class RegularizePreview(
        val sourceEdges: List<Pair<Vector3, Vector3>>,
        val gridEdges: List<Pair<Vector3, Vector3>>
    )
    private data class ProjectedGrid(
        val firstU: Float,
        val lastU: Float,
        val firstV: Float,
        val lastV: Float,
        val cellSize: Float,
        val extent: Float
    )
    private data class BoundaryLoop(
        val polygon: List<Vec2>,
        val segments: List<BoundarySegmentSample>
    )
    private data class BoundarySegmentSample(
        val uvStart: Vec2,
        val uvEnd: Vec2,
        val start3d: Vector3,
        val end3d: Vector3
    )
    private data class BoundaryEdgeBuilder(
        val key: EdgeKey,
        val startKey: VertexKey,
        val endKey: VertexKey,
        val start3d: Vector3,
        val end3d: Vector3,
        val uvStart: Vec2,
        val uvEnd: Vec2,
        var count: Int = 0
    ) {
        fun toBoundaryEdge(): BoundaryEdge = BoundaryEdge(key, startKey, endKey, uvStart, uvEnd, start3d, end3d)
    }
    private data class BoundaryEdge(
        val key: EdgeKey,
        val startKey: VertexKey,
        val endKey: VertexKey,
        val uvStart: Vec2,
        val uvEnd: Vec2,
        val start3d: Vector3,
        val end3d: Vector3
    ) {
        fun other(key: VertexKey): VertexKey = if (key == startKey) endKey else startKey
        fun orientedFrom(key: VertexKey): BoundarySegmentSample =
            if (key == startKey) BoundarySegmentSample(uvStart, uvEnd, start3d, end3d)
            else BoundarySegmentSample(uvEnd, uvStart, end3d, start3d)
    }
}

private data class VertexKey(val x: Int, val y: Int, val z: Int)
private data class EdgeKey(val a: VertexKey, val b: VertexKey)
private data class Vec2(val x: Float, val y: Float)

private const val RANDOM_OFFSET_EPSILON = 0.001f
private const val RANDOM_OFFSET_EPSILON_SQ = RANDOM_OFFSET_EPSILON * RANDOM_OFFSET_EPSILON
private const val MathUtilsDegreesToRadians = PI.toFloat() / 180f
private const val RANDOM_PREVIEW_SEED = 0x5eed1234L
private const val RANDOM_SURFACE_ARRAY_PREVIEW_LIMIT = 250
private const val SURFACE_ARRAY_NORMAL_TICK = 0.35f

private fun vertexKey(point: Vector3): VertexKey {
    return VertexKey(
        round(point.x / RANDOM_OFFSET_EPSILON).toInt(),
        round(point.y / RANDOM_OFFSET_EPSILON).toInt(),
        round(point.z / RANDOM_OFFSET_EPSILON).toInt()
    )
}

private fun triangleNormal(tri: DraftFaceStore.Triangle): Vector3 {
    return Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a)).nor()
}

private fun triangleArea(tri: DraftFaceStore.Triangle): Float {
    return Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a)).len() * 0.5f
}

private fun randomSignedUnit(rng: Random): Float {
    return rng.nextInt(-1000, 1001).toFloat() / 1000f
}

private fun centroidOfSegments(segments: List<com.github.alfu32.sketch.model.DraftLineStore.Segment>): Vector3 {
    val sum = Vector3()
    var count = 0
    segments.forEach { segment ->
        sum.add(segment.start).add(segment.end)
        count += 2
    }
    return if (count > 0) sum.scl(1f / count.toFloat()) else sum
}

private fun centroidOfFaces(faces: List<DraftFaceStore.Triangle>): Vector3 {
    val sum = Vector3()
    var count = 0
    faces.forEach { tri ->
        sum.add(tri.a).add(tri.b).add(tri.c)
        count += 3
    }
    return if (count > 0) sum.scl(1f / count.toFloat()) else sum
}

private fun averageNormal(faces: List<DraftFaceStore.Triangle>): Vector3 {
    val normal = Vector3()
    faces.forEach { tri ->
        val n = Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a))
        if (n.len2() > RANDOM_OFFSET_EPSILON_SQ) {
            normal.add(n.nor())
        }
    }
    return normal
}

private fun isNearPlanar(
    faces: List<DraftFaceStore.Triangle>,
    origin: Vector3,
    normal: Vector3,
    tolerance: Float
): Boolean {
    return faces.all { tri ->
        kotlin.math.abs(Vector3(tri.a).sub(origin).dot(normal)) <= tolerance &&
            kotlin.math.abs(Vector3(tri.b).sub(origin).dot(normal)) <= tolerance &&
            kotlin.math.abs(Vector3(tri.c).sub(origin).dot(normal)) <= tolerance
    }
}

private fun rayTriangleIntersection(
    origin: Vector3,
    direction: Vector3,
    triangle: DraftFaceStore.Triangle,
    tolerance: Float
): Float? {
    val edge1 = Vector3(triangle.b).sub(triangle.a)
    val edge2 = Vector3(triangle.c).sub(triangle.a)
    val h = Vector3(direction).crs(edge2)
    val det = edge1.dot(h)
    val epsilon = max(tolerance, RANDOM_OFFSET_EPSILON)
    if (kotlin.math.abs(det) <= epsilon) {
        return null
    }
    val invDet = 1f / det
    val s = Vector3(origin).sub(triangle.a)
    val u = invDet * s.dot(h)
    if (u < -epsilon || u > 1f + epsilon) {
        return null
    }
    val q = Vector3(s).crs(edge1)
    val v = invDet * direction.dot(q)
    if (v < -epsilon || u + v > 1f + epsilon) {
        return null
    }
    val t = invDet * edge2.dot(q)
    return if (t > epsilon) t else null
}

private fun compareVertexKeys(a: VertexKey, b: VertexKey): Int {
    if (a.x != b.x) return a.x.compareTo(b.x)
    if (a.y != b.y) return a.y.compareTo(b.y)
    return a.z.compareTo(b.z)
}

private fun project2d(point: Vector3, origin: Vector3, basis: PlaneBasis): Vec2 {
    val v = Vector3(point).sub(origin)
    return Vec2(v.dot(basis.axisU), v.dot(basis.axisV))
}

private fun unproject2d(point: Vec2, origin: Vector3, basis: PlaneBasis): Vector3 {
    return Vector3(origin)
        .add(Vector3(basis.axisU).scl(point.x))
        .add(Vector3(basis.axisV).scl(point.y))
}

private fun pointInTriangle(p: Vec2, a: Vec2, b: Vec2, c: Vec2): Boolean {
    val d1 = sign2d(p, a, b)
    val d2 = sign2d(p, b, c)
    val d3 = sign2d(p, c, a)
    val hasNeg = d1 < -RANDOM_OFFSET_EPSILON || d2 < -RANDOM_OFFSET_EPSILON || d3 < -RANDOM_OFFSET_EPSILON
    val hasPos = d1 > RANDOM_OFFSET_EPSILON || d2 > RANDOM_OFFSET_EPSILON || d3 > RANDOM_OFFSET_EPSILON
    return !(hasNeg && hasPos)
}

private fun pointInPolygon(point: Vec2, polygon: List<Vec2>): Boolean {
    if (polygon.size < 3) {
        return false
    }
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        val intersects = ((pi.y > point.y) != (pj.y > point.y)) &&
            (point.x < (pj.x - pi.x) * (point.y - pi.y) / ((pj.y - pi.y).takeIf { kotlin.math.abs(it) > RANDOM_OFFSET_EPSILON } ?: RANDOM_OFFSET_EPSILON) + pi.x)
        if (intersects) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private fun sign2d(p1: Vec2, p2: Vec2, p3: Vec2): Float {
    return (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y)
}

private fun closestPointOnSegment2d(point: Vec2, start: Vec2, end: Vec2): Vec2 {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSq = dx * dx + dy * dy
    if (lengthSq <= RANDOM_OFFSET_EPSILON_SQ) {
        return start
    }
    val t = (((point.x - start.x) * dx) + ((point.y - start.y) * dy)) / lengthSq
    val clamped = t.coerceIn(0f, 1f)
    return Vec2(start.x + dx * clamped, start.y + dy * clamped)
}

private fun dst2(a: Vec2, b: Vec2): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

private fun formatFloat(value: Float): String {
    val rounded = round(value * 100f) / 100f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}

private fun addTriangleEdges(target: MutableList<Pair<Vector3, Vector3>>, a: Vector3, b: Vector3, c: Vector3) {
    target += a to b
    target += b to c
    target += c to a
}

private fun surfaceEdges(faces: List<DraftFaceStore.Triangle>, group: GroupScene.GroupNode): List<Pair<Vector3, Vector3>> {
    val edges = mutableListOf<Pair<Vector3, Vector3>>()
    faces.forEach { tri ->
        addTriangleEdges(edges, group.toWorld(tri.a), group.toWorld(tri.b), group.toWorld(tri.c))
    }
    return edges
}

private fun drawPreviewLines(renderer: ShapeRenderer, lines: List<Pair<Vector3, Vector3>>) {
    lines.forEach { (a, b) ->
        renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
    }
}

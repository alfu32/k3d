package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object ModelPersistence {
    private const val VERSION = 17
    private const val GZIP_MAGIC_0 = 0x1f
    private const val GZIP_MAGIC_1 = 0x8b
    private const val COMPRESS_THRESHOLD_BYTES = 10 * 1024 * 1024
    private class DecodeCanceledException : RuntimeException()

    private fun createJson(): Json {
        return Json().apply {
            setOutputType(JsonWriter.OutputType.json)
        }
    }

    fun save(
        file: File,
        scene: GroupScene,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        cameraTarget: Vector3,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings,
        modelUnit: ModelUnit,
        snapEpsilon: Float,
        gridSpacing: Float,
        circleSegments: Int,
        undoHistory: UndoHistoryDto? = null
    ) {
        val snapshot = snapshot(scene, camera, cameraTarget, lighting, shadow, modelUnit, snapEpsilon, gridSpacing, circleSegments, undoHistory)
        saveSnapshot(file, snapshot)
    }

    fun saveSnapshot(file: File, snapshot: ModelSnapshot) {
        val bytes = saveSnapshotBytes(snapshot)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    fun saveSnapshotText(snapshot: ModelSnapshot): String {
        return createJson().toJson(snapshot)
    }

    fun saveSnapshotBytes(snapshot: ModelSnapshot): ByteArray {
        val text = saveSnapshotText(snapshot)
        val rawBytes = text.toByteArray(Charsets.UTF_8)
        if (rawBytes.size <= COMPRESS_THRESHOLD_BYTES) {
            return rawBytes
        }
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { stream ->
            stream.write(rawBytes)
        }
        return output.toByteArray()
    }

    fun snapshot(
        scene: GroupScene,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        cameraTarget: Vector3,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings,
        modelUnit: ModelUnit,
        snapEpsilon: Float,
        gridSpacing: Float,
        circleSegments: Int,
        undoHistory: UndoHistoryDto? = null
    ): ModelSnapshot {
        val rootPrototypeId = scene.rootPrototypeId()
        return ModelSnapshot().apply {
            version = VERSION
            prototypes = scene.allPrototypes().map { prototype ->
                val dto = ObjectPrototypeDto.fromPrototype(prototype)
                if (prototype.id == rootPrototypeId) {
                    dto.segments = prototype.lineStore.getSegments()
                        .filterNot { scene.isGeneratedArchitectureSegment(it) || scene.isGeneratedHvacSegment(it) }
                        .map { seg -> SegmentDto(Vec3Dto(seg.start), Vec3Dto(seg.end), seg.id) }
                        .toMutableList()
                    dto.faces = prototype.faceStore.getTriangles()
                        .filterNot { scene.isGeneratedArchitectureTriangle(it) || scene.isGeneratedHvacTriangle(it) }
                        .map { tri ->
                            val color = prototype.faceStore.colorFor(tri)
                            FaceDto(Vec3Dto(tri.a), Vec3Dto(tri.b), Vec3Dto(tri.c), ColorDto(color), tri.id)
                        }
                        .toMutableList()
                }
                dto
            }.toMutableList()
            rootInstance = GroupInstanceDto.fromInstance(scene.root)
            cameraState = CameraDto(camera, cameraTarget)
            lightingState = LightingDto(lighting)
            shadowState = ShadowDto(shadow)
            this.modelUnit = ModelUnitDto(modelUnit)
            this.snapEpsilon = snapEpsilon
            this.gridSpacing = gridSpacing
            this.circleSegments = circleSegments
            this.undoHistory = undoHistory
        }
    }

    data class LoadResult(val ok: Boolean, val needsResave: Boolean, val snapshot: ModelSnapshot? = null)

    fun load(
        file: File,
        scene: GroupScene,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        cameraTarget: Vector3,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings,
        modelUnit: ModelUnit,
        snapEpsilonSetter: (Float) -> Unit,
        gridSpacingSetter: (Float) -> Unit,
        circleSegmentsSetter: (Int) -> Unit
    ): LoadResult {
        if (!file.exists() || file.length() == 0L) {
            return LoadResult(false, false)
        }
        val snapshot = parseSnapshotFile(file) ?: return LoadResult(false, false)

        applySnapshot(snapshot, scene, camera, cameraTarget, lighting, shadow, modelUnit, snapEpsilonSetter, gridSpacingSetter, circleSegmentsSetter)
        return LoadResult(true, needsResave(snapshot), snapshot)
    }

    fun loadFromText(
        text: String,
        scene: GroupScene,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        cameraTarget: Vector3,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings,
        modelUnit: ModelUnit,
        snapEpsilonSetter: (Float) -> Unit,
        gridSpacingSetter: (Float) -> Unit,
        circleSegmentsSetter: (Int) -> Unit
    ): LoadResult {
        if (text.isBlank()) {
            return LoadResult(false, false)
        }
        val snapshot = parseSnapshotText(text) ?: return LoadResult(false, false)

        applySnapshot(snapshot, scene, camera, cameraTarget, lighting, shadow, modelUnit, snapEpsilonSetter, gridSpacingSetter, circleSegmentsSetter)
        return LoadResult(true, needsResave(snapshot), snapshot)
    }

    fun parseSnapshotText(text: String): ModelSnapshot? {
        val json = createJson()
        return try {
            json.fromJson(ModelSnapshot::class.java, text)
        } catch (_: Exception) {
            null
        }
    }

    fun parseSnapshotFile(file: File): ModelSnapshot? {
        if (!file.exists() || file.length() == 0L) {
            return null
        }
        return try {
            file.inputStream().buffered().use { input ->
                parseSnapshotStream(input)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseSnapshotBytes(
        bytes: ByteArray,
        onDecodeProgress: (Float) -> Unit = {},
        isCanceled: () -> Boolean = { false }
    ): ModelSnapshot? {
        if (bytes.isEmpty()) {
            return null
        }
        return try {
            parseSnapshotText(decodeSnapshotBytesToText(bytes, onDecodeProgress, isCanceled) ?: return null)
        } catch (_: DecodeCanceledException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun needsResave(snapshot: ModelSnapshot): Boolean {
        return snapshot.cameraState == null ||
            snapshot.lightingState == null ||
            snapshot.shadowState == null ||
            (snapshot.rootInstance == null && snapshot.rootGroup == null) ||
            (snapshot.rootInstance != null && snapshot.prototypes.isEmpty()) ||
            snapshot.cameraState?.hasNulls() == true ||
            snapshot.lightingState?.hasNulls() == true ||
            snapshot.shadowState?.hasNulls() == true ||
            snapshot.modelUnit == null ||
            snapshot.snapEpsilon == null ||
            snapshot.gridSpacing == null ||
            snapshot.circleSegments == null
    }

    private fun parseSnapshotStream(input: InputStream): ModelSnapshot? {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        val reader = if (first == GZIP_MAGIC_0 && second == GZIP_MAGIC_1) {
            GZIPInputStream(buffered).bufferedReader(Charsets.UTF_8)
        } else {
            buffered.reader(Charsets.UTF_8)
        }
        return reader.use { parseSnapshotText(it.readText()) }
    }

    private fun decodeSnapshotBytesToText(
        bytes: ByteArray,
        onDecodeProgress: (Float) -> Unit,
        isCanceled: () -> Boolean
    ): String? {
        if (bytes.isEmpty()) {
            return null
        }
        fun checkCanceled() {
            if (isCanceled()) {
                throw DecodeCanceledException()
            }
        }
        onDecodeProgress(0f)
        checkCanceled()
        val gzip = bytes.size >= 2 &&
            bytes[0].toInt() and 0xff == GZIP_MAGIC_0 &&
            bytes[1].toInt() and 0xff == GZIP_MAGIC_1
        if (!gzip) {
            val text = bytes.toString(Charsets.UTF_8)
            onDecodeProgress(1f)
            return text
        }
        val totalBytes = bytes.size.toFloat().coerceAtLeast(1f)
        val source = ByteArrayInputStream(bytes)
        var consumedBytes = 0
        val countingInput = object : InputStream() {
            private fun reportProgress() {
                onDecodeProgress((consumedBytes / totalBytes).coerceIn(0f, 1f))
            }

            override fun read(): Int {
                checkCanceled()
                val value = source.read()
                if (value >= 0) {
                    consumedBytes += 1
                    reportProgress()
                }
                return value
            }

            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                checkCanceled()
                val read = source.read(buffer, off, len)
                if (read > 0) {
                    consumedBytes += read
                    reportProgress()
                }
                return read
            }

            override fun close() {
                source.close()
            }
        }
        val output = StringBuilder(bytes.size)
        GZIPInputStream(BufferedInputStream(countingInput)).bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8 * 1024)
            while (true) {
                checkCanceled()
                val read = reader.read(buffer)
                if (read <= 0) {
                    break
                }
                output.append(buffer, 0, read)
            }
        }
        onDecodeProgress(1f)
        return output.toString()
    }

    fun applySnapshot(
        snapshot: ModelSnapshot,
        scene: GroupScene,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        cameraTarget: Vector3? = null,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings,
        modelUnit: ModelUnit? = null,
        snapEpsilonSetter: ((Float) -> Unit)? = null,
        gridSpacingSetter: ((Float) -> Unit)? = null,
        circleSegmentsSetter: ((Int) -> Unit)? = null
    ) {
        val session = beginApplySnapshot(
            snapshot = snapshot,
            scene = scene,
            camera = camera,
            cameraTarget = cameraTarget,
            lighting = lighting,
            shadow = shadow,
            modelUnit = modelUnit,
            snapEpsilonSetter = snapEpsilonSetter,
            gridSpacingSetter = gridSpacingSetter,
            circleSegmentsSetter = circleSegmentsSetter
        )
        while (!session.advance(Long.MAX_VALUE)) {
            // Synchronous wrapper for legacy call sites.
        }
    }

    class ApplySnapshotSession internal constructor(
        private val snapshot: ModelSnapshot,
        private val scene: GroupScene,
        private val camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        private val cameraTarget: Vector3?,
        private val lighting: com.github.alfu32.sketch.ui.LightingSettings,
        private val shadow: com.github.alfu32.sketch.ui.ShadowSettings,
        private val modelUnit: ModelUnit?,
        private val snapEpsilonSetter: ((Float) -> Unit)?,
        private val gridSpacingSetter: ((Float) -> Unit)?,
        private val circleSegmentsSetter: ((Int) -> Unit)?
    ) {
        private val defaultColor = scene.defaultFaceColor
        private val prototypeMap = linkedMapOf<String, GroupScene.ObjectPrototype>()
        private val prototypeTasks = ArrayDeque<PrototypeTask>()
        private val instanceTasks = ArrayDeque<InstanceTask>()
        private val legacyTasks = ArrayDeque<LegacyGroupTask>()
        private var initialized = false
        private var finalized = false
        private var completedWork = 0
        private val totalWork = estimateTotalWork(snapshot).coerceAtLeast(1)
        var progress: Float = 0f
            private set

        private inner class PrototypeTask(
            private val dto: ObjectPrototypeDto,
            private val prototype: GroupScene.ObjectPrototype
        ) {
            private var prepared = false
            private var segmentIndex = 0
            private var faceIndex = 0
            private var dimensionIndex = 0
            private var textIndex = 0
            private var finalizedGeometry = false

            fun step(deadlineNs: Long): Boolean {
                if (!prepared) {
                    dto.preparePrototypeForIncrementalLoad(prototype, defaultColor)
                    prepared = true
                    completedWork += 1
                    updateProgress()
                }
                if (System.nanoTime() < deadlineNs) {
                    var changedLineOrFace = false
                    prototype.faceStore.withChangeSuppressed {
                        prototype.lineStore.withChangeSuppressed {
                            while (segmentIndex < dto.segments.size && System.nanoTime() < deadlineNs) {
                                val segment = dto.segments[segmentIndex++]
                                prototype.lineStore.addSegment(
                                    segment.start.toVector3(),
                                    segment.end.toVector3(),
                                    autoCleanup = false,
                                    id = segment.id.ifBlank { java.util.UUID.randomUUID().toString() }
                                )
                                completedWork += 1
                                changedLineOrFace = true
                            }
                            while (faceIndex < dto.faces.size && System.nanoTime() < deadlineNs) {
                                val face = dto.faces[faceIndex++]
                                prototype.faceStore.addTriangle(
                                    face.a.toVector3(),
                                    face.b.toVector3(),
                                    face.c.toVector3(),
                                    face.color.toColor(),
                                    id = face.id.ifBlank { java.util.UUID.randomUUID().toString() }
                                )
                                completedWork += 1
                                changedLineOrFace = true
                            }
                        }
                    }
                    if (changedLineOrFace) {
                        prototype.lineStore.notifyExternalChange()
                        prototype.faceStore.notifyExternalChange()
                        updateProgress()
                    }
                }
                while (dimensionIndex < dto.dimensions.size && System.nanoTime() < deadlineNs) {
                    val dimension = dto.dimensions[dimensionIndex++]
                    prototype.dimensionStore.addDimension(
                        dimension.start.toVector3(),
                        dimension.end.toVector3(),
                        dimension.offset.toVector3()
                    )
                    completedWork += 1
                    updateProgress()
                }
                while (textIndex < dto.texts.size && System.nanoTime() < deadlineNs) {
                    val text = dto.texts[textIndex++]
                    prototype.textStore.addText(
                        text.position.toVector3(),
                        text.text,
                        text.size,
                        text.normal.toVector3(),
                        text.axisU.toVector3(),
                        text.screenText,
                        kind = runCatching { DraftTextStore.Kind.valueOf(text.kind) }.getOrDefault(DraftTextStore.Kind.BITMAP),
                        tracking = text.tracking,
                        lineSpacing = text.lineSpacing,
                        glyphSourcePath = text.glyphSourcePath
                    )
                    completedWork += 1
                    updateProgress()
                }
                if (!finalizedGeometry &&
                    segmentIndex >= dto.segments.size &&
                    faceIndex >= dto.faces.size &&
                    dimensionIndex >= dto.dimensions.size &&
                    textIndex >= dto.texts.size &&
                    System.nanoTime() < deadlineNs
                ) {
                    dto.finalizePrototypeAfterIncrementalLoad(prototype)
                    finalizedGeometry = true
                    completedWork += 1
                    updateProgress()
                }
                return prepared && finalizedGeometry
            }
        }

        private inner class InstanceTask(
            private val dto: GroupInstanceDto,
            private val parent: GroupScene.GroupNode?,
            private val existingTarget: GroupScene.GroupNode? = null
        ) {
            private var group: GroupScene.GroupNode? = null
            private var prepared = false
            private var segmentIndex = 0
            private var faceIndex = 0
            private var dimensionIndex = 0
            private var textIndex = 0
            private var childrenQueued = false

            fun step(deadlineNs: Long): Boolean {
                if (!prepared) {
                    val target = existingTarget ?: dto.createInstanceShell(prototypeMap, defaultColor)
                    group = target
                    target.instanceOrigin.set(dto.instanceOrigin.toVector3())
                    target.instanceAxisU.set(dto.instanceAxisU.toVector3())
                    target.instanceAxisV.set(dto.instanceAxisV.toVector3())
                    target.instanceAxisW.set(dto.instanceAxisW.toVector3())
                    target.hotspotPositionOverrides.clear()
                    dto.hotspotPositions.forEach { hotspot ->
                        if (hotspot.id.isNotBlank()) {
                            target.hotspotPositionOverrides[hotspot.id] = hotspot.position.toVector3()
                        }
                    }
                    target.hotspotAttachedSegmentOverrides.clear()
                    dto.hotspotSegmentAttachments.forEach { attachment ->
                        if (attachment.id.isNotBlank()) {
                            target.hotspotAttachedSegmentOverrides[attachment.id] = attachment.refs.map { it.toSegmentRef() }.toMutableSet()
                        }
                    }
                    target.hotspotAttachedTriangleOverrides.clear()
                    dto.hotspotTriangleAttachments.forEach { attachment ->
                        if (attachment.id.isNotBlank()) {
                            target.hotspotAttachedTriangleOverrides[attachment.id] = attachment.refs.map { it.toTriangleRef() }.toMutableSet()
                        }
                    }
                    target.children.clear()
                    if (existingTarget == null) {
                        target.parent = parent
                        parent?.children?.add(target)
                        scene.registerInstanceTree(target)
                    }
                    if (
                        dto.overrideSegments.isNotEmpty() ||
                        dto.overrideFaces.isNotEmpty() ||
                        dto.overrideDimensions.isNotEmpty() ||
                        dto.overrideTexts.isNotEmpty()
                    ) {
                        target.lineStoreOverride = DraftLineStore()
                        target.faceStoreOverride = DraftFaceStore(defaultColor)
                        target.dimensionStoreOverride = DraftDimensionStore()
                        target.textStoreOverride = DraftTextStore()
                    } else {
                        target.lineStoreOverride = null
                        target.faceStoreOverride = null
                        target.dimensionStoreOverride = null
                        target.textStoreOverride = null
                    }
                    prepared = true
                    completedWork += 1
                    updateProgress()
                }
                val currentGroup = group ?: return true
                val lineOverride = currentGroup.lineStoreOverride
                val faceOverride = currentGroup.faceStoreOverride
                if (lineOverride != null && faceOverride != null && System.nanoTime() < deadlineNs) {
                    var changedLineOrFace = false
                    faceOverride.withChangeSuppressed {
                        lineOverride.withChangeSuppressed {
                            while (segmentIndex < dto.overrideSegments.size && System.nanoTime() < deadlineNs) {
                                val segment = dto.overrideSegments[segmentIndex++]
                                lineOverride.addSegment(
                                    segment.start.toVector3(),
                                    segment.end.toVector3(),
                                    autoCleanup = false,
                                    id = segment.id.ifBlank { java.util.UUID.randomUUID().toString() }
                                )
                                completedWork += 1
                                changedLineOrFace = true
                            }
                            while (faceIndex < dto.overrideFaces.size && System.nanoTime() < deadlineNs) {
                                val face = dto.overrideFaces[faceIndex++]
                                faceOverride.addTriangle(
                                    face.a.toVector3(),
                                    face.b.toVector3(),
                                    face.c.toVector3(),
                                    face.color.toColor(),
                                    id = face.id.ifBlank { java.util.UUID.randomUUID().toString() }
                                )
                                completedWork += 1
                                changedLineOrFace = true
                            }
                        }
                    }
                    if (changedLineOrFace) {
                        lineOverride.notifyExternalChange()
                        faceOverride.notifyExternalChange()
                        updateProgress()
                    }
                }
                val dimensionOverride = currentGroup.dimensionStoreOverride
                while (dimensionOverride != null && dimensionIndex < dto.overrideDimensions.size && System.nanoTime() < deadlineNs) {
                    val dimension = dto.overrideDimensions[dimensionIndex++]
                    dimensionOverride.addDimension(
                        dimension.start.toVector3(),
                        dimension.end.toVector3(),
                        dimension.offset.toVector3()
                    )
                    completedWork += 1
                    updateProgress()
                }
                val textOverride = currentGroup.textStoreOverride
                while (textOverride != null && textIndex < dto.overrideTexts.size && System.nanoTime() < deadlineNs) {
                    val text = dto.overrideTexts[textIndex++]
                    textOverride.addText(
                        text.position.toVector3(),
                        text.text,
                        text.size,
                        text.normal.toVector3(),
                        text.axisU.toVector3(),
                        text.screenText,
                        kind = runCatching { DraftTextStore.Kind.valueOf(text.kind) }.getOrDefault(DraftTextStore.Kind.BITMAP),
                        tracking = text.tracking,
                        lineSpacing = text.lineSpacing,
                        glyphSourcePath = text.glyphSourcePath
                    )
                    completedWork += 1
                    updateProgress()
                }
                if (!childrenQueued &&
                    segmentIndex >= dto.overrideSegments.size &&
                    faceIndex >= dto.overrideFaces.size &&
                    dimensionIndex >= dto.overrideDimensions.size &&
                    textIndex >= dto.overrideTexts.size
                ) {
                    dto.children.forEach { child ->
                        instanceTasks.addLast(InstanceTask(child, currentGroup))
                    }
                    childrenQueued = true
                }
                return prepared && childrenQueued
            }
        }

        private inner class LegacyGroupTask(
            private val dto: GroupDto,
            private val parent: GroupScene.GroupNode?,
            private val existingTarget: GroupScene.GroupNode? = null
        ) {
            private var group: GroupScene.GroupNode? = null
            private var prepared = false
            private var segmentIndex = 0
            private var faceIndex = 0
            private var childrenQueued = false

            fun step(deadlineNs: Long): Boolean {
                if (!prepared) {
                    val target = existingTarget ?: dto.createGroupShell(defaultColor)
                    group = target
                    if (existingTarget != null) {
                        target.instanceOrigin.set(dto.instanceOrigin?.toVector3() ?: dto.origin.toVector3())
                        target.instanceAxisU.set(dto.instanceAxisU?.toVector3() ?: dto.axisU.toVector3())
                        target.instanceAxisV.set(dto.instanceAxisV?.toVector3() ?: dto.axisV.toVector3())
                        target.instanceAxisW.set(dto.instanceAxisW?.toVector3() ?: dto.axisW.toVector3())
                        target.prototype.name = dto.name.ifBlank { target.prototype.name }
                        target.prototype.definitionOrigin.set(dto.definitionOrigin?.toVector3() ?: Vector3())
                        target.prototype.definitionAxisU.set(dto.definitionAxisU?.toVector3() ?: Vector3(1f, 0f, 0f))
                        target.prototype.definitionAxisV.set(dto.definitionAxisV?.toVector3() ?: Vector3(0f, 1f, 0f))
                        target.prototype.definitionAxisW.set(dto.definitionAxisW?.toVector3() ?: Vector3(0f, 0f, 1f))
                        target.prototype.gluedToSurface = dto.gluedToSurface
                        target.children.clear()
                        target.lineStore.clearAll()
                        target.faceStore.clearAll()
                    } else {
                        target.parent = parent
                        parent?.children?.add(target)
                        scene.registerPrototypeForLoad(target.prototype)
                        scene.registerInstanceTree(target)
                    }
                    prepared = true
                    completedWork += 1
                    updateProgress()
                }
                val currentGroup = group ?: return true
                if (System.nanoTime() < deadlineNs) {
                    var changed = false
                    currentGroup.faceStore.withChangeSuppressed {
                        currentGroup.lineStore.withChangeSuppressed {
                            while (segmentIndex < dto.segments.size && System.nanoTime() < deadlineNs) {
                                val segment = dto.segments[segmentIndex++]
                                currentGroup.lineStore.addSegment(
                                    segment.start.toVector3(),
                                    segment.end.toVector3(),
                                    autoCleanup = false,
                                    id = segment.id.ifBlank { java.util.UUID.randomUUID().toString() }
                                )
                                completedWork += 1
                                changed = true
                            }
                            while (faceIndex < dto.faces.size && System.nanoTime() < deadlineNs) {
                                val face = dto.faces[faceIndex++]
                                currentGroup.faceStore.addTriangle(
                                    face.a.toVector3(),
                                    face.b.toVector3(),
                                    face.c.toVector3(),
                                    face.color.toColor(),
                                    id = face.id.ifBlank { java.util.UUID.randomUUID().toString() }
                                )
                                completedWork += 1
                                changed = true
                            }
                        }
                    }
                    if (changed) {
                        currentGroup.lineStore.notifyExternalChange()
                        currentGroup.faceStore.notifyExternalChange()
                        updateProgress()
                    }
                }
                if (!childrenQueued && segmentIndex >= dto.segments.size && faceIndex >= dto.faces.size) {
                    dto.children.forEach { child ->
                        legacyTasks.addLast(LegacyGroupTask(child, currentGroup))
                    }
                    childrenQueued = true
                }
                return prepared && childrenQueued
            }
        }

        fun abort() {
            resetScene(scene)
            progress = 0f
        }

        fun advance(deadlineNs: Long): Boolean {
            if (finalized) {
                return true
            }
            if (!initialized) {
                initialize()
            }
            while (System.nanoTime() < deadlineNs) {
                val activeTaskDone = when {
                    prototypeTasks.isNotEmpty() -> prototypeTasks.first().step(deadlineNs).also {
                        if (it) prototypeTasks.removeFirst()
                    }
                    instanceTasks.isNotEmpty() -> instanceTasks.first().step(deadlineNs).also {
                        if (it) instanceTasks.removeFirst()
                    }
                    legacyTasks.isNotEmpty() -> legacyTasks.first().step(deadlineNs).also {
                        if (it) legacyTasks.removeFirst()
                    }
                    else -> {
                        finalizeApply()
                        finalized = true
                        progress = 1f
                        return true
                    }
                }
                if (!activeTaskDone && System.nanoTime() >= deadlineNs) {
                    break
                }
            }
            updateProgress()
            return finalized
        }

        private fun initialize() {
            resetScene(scene)
            if (snapshot.rootInstance != null && snapshot.prototypes.isNotEmpty()) {
                val rootPrototype = scene.rootPrototype()
                snapshot.prototypes.forEach { dto ->
                    if (dto.id == rootPrototype.id) {
                        prototypeMap[rootPrototype.id] = rootPrototype
                        prototypeTasks.addLast(PrototypeTask(dto, rootPrototype))
                    } else {
                        val prototype = dto.createPrototypeShell(defaultColor)
                        scene.registerPrototypeForLoad(prototype)
                        prototypeMap[prototype.id] = prototype
                        prototypeTasks.addLast(PrototypeTask(dto, prototype))
                    }
                }
                instanceTasks.addLast(InstanceTask(snapshot.rootInstance!!, null, scene.root))
            } else if (snapshot.prototypes.isNotEmpty()) {
                val rootPrototype = scene.rootPrototype()
                prototypeMap[rootPrototype.id] = rootPrototype
                snapshot.prototypes.forEach { dto ->
                    if (dto.id == rootPrototype.id) {
                        prototypeTasks.addLast(PrototypeTask(dto, rootPrototype))
                    } else {
                        val prototype = dto.createPrototypeShell(defaultColor)
                        scene.registerPrototypeForLoad(prototype)
                        prototypeMap[prototype.id] = prototype
                        prototypeTasks.addLast(PrototypeTask(dto, prototype))
                    }
                }
                val objectPrototypeId = snapshot.prototypes
                    .firstOrNull { it.id != rootPrototype.id }
                    ?.id
                val rootInstance = GroupInstanceDto().apply {
                    id = "root"
                    prototypeId = rootPrototype.id
                    instanceOrigin = Vec3Dto(Vector3())
                    instanceAxisU = Vec3Dto(Vector3(1f, 0f, 0f))
                    instanceAxisV = Vec3Dto(Vector3(0f, 1f, 0f))
                    instanceAxisW = Vec3Dto(Vector3(0f, 0f, 1f))
                    if (objectPrototypeId != null) {
                        children = mutableListOf(
                            GroupInstanceDto().apply {
                                id = java.util.UUID.randomUUID().toString()
                                prototypeId = objectPrototypeId
                                instanceOrigin = Vec3Dto(Vector3())
                                instanceAxisU = Vec3Dto(Vector3(1f, 0f, 0f))
                                instanceAxisV = Vec3Dto(Vector3(0f, 1f, 0f))
                                instanceAxisW = Vec3Dto(Vector3(0f, 0f, 1f))
                            }
                        )
                    }
                }
                instanceTasks.addLast(InstanceTask(rootInstance, null, scene.root))
            } else if (snapshot.rootGroup != null) {
                legacyTasks.addLast(LegacyGroupTask(snapshot.rootGroup!!, null, scene.root))
            } else {
                val flatDto = GroupDto().apply {
                    segments = snapshot.segments
                    faces = snapshot.faces
                }
                legacyTasks.addLast(LegacyGroupTask(flatDto, null, scene.root))
            }
            initialized = true
            updateProgress()
        }

        private fun finalizeApply() {
            scene.syncArchitectureGeometryAfterLoad()
            scene.syncHvacGeometryAfterLoad()
            if (snapshot.cameraState != null) {
                snapshot.cameraState?.applyTo(camera, cameraTarget)
            } else {
                camera.position.set(10f, 10f, 10f)
                camera.up.set(0f, 1f, 0f)
                camera.direction.set(0f, 0f, 0f).sub(camera.position).nor()
                cameraTarget?.set(0f, 0f, 0f)
                camera.update()
            }
            snapshot.lightingState?.applyTo(lighting)
            snapshot.shadowState?.applyTo(shadow)
            snapshot.modelUnit?.let { dto ->
                modelUnit?.let { unit ->
                    unit.name = dto.name
                    unit.size = dto.size
                }
            }
            snapshot.snapEpsilon?.let { value -> snapEpsilonSetter?.invoke(value) }
            snapshot.gridSpacing?.let { value -> gridSpacingSetter?.invoke(value) }
            circleSegmentsSetter?.invoke(snapshot.circleSegments ?: 24)
            completedWork = totalWork
            updateProgress()
        }

        private fun updateProgress() {
            progress = (completedWork.toFloat() / totalWork.toFloat()).coerceIn(0f, 1f)
        }
    }

    fun beginApplySnapshot(
        snapshot: ModelSnapshot,
        scene: GroupScene,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        cameraTarget: Vector3? = null,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings,
        modelUnit: ModelUnit? = null,
        snapEpsilonSetter: ((Float) -> Unit)? = null,
        gridSpacingSetter: ((Float) -> Unit)? = null,
        circleSegmentsSetter: ((Int) -> Unit)? = null
    ): ApplySnapshotSession {
        return ApplySnapshotSession(
            snapshot = snapshot,
            scene = scene,
            camera = camera,
            cameraTarget = cameraTarget,
            lighting = lighting,
            shadow = shadow,
            modelUnit = modelUnit,
            snapEpsilonSetter = snapEpsilonSetter,
            gridSpacingSetter = gridSpacingSetter,
            circleSegmentsSetter = circleSegmentsSetter
        )
    }

    class ModelSnapshot {
        var version: Int = VERSION
        var segments: MutableList<SegmentDto> = mutableListOf()
        var faces: MutableList<FaceDto> = mutableListOf()
        var rootGroup: GroupDto? = null
        var prototypes: MutableList<ObjectPrototypeDto> = mutableListOf()
        var rootInstance: GroupInstanceDto? = null
        var cameraState: CameraDto? = null
        var lightingState: LightingDto? = null
        var shadowState: ShadowDto? = null
        var modelUnit: ModelUnitDto? = null
        var snapEpsilon: Float? = null
        var gridSpacing: Float? = null
        var circleSegments: Int? = null
        var undoHistory: UndoHistoryDto? = null
    }

    class UndoHistoryDto {
        var maxEntries: Int = 0
        var index: Int = -1
        var entries: MutableList<UndoEntryDto> = mutableListOf()
    }

    class UndoEntryDto {
        var data: String = ""
        var label: String? = null
        var timestamp: Long = 0L
    }

    class ModelUnitDto() {
        var size: Float = 1f
        var name: String = "unit"

        constructor(unit: ModelUnit) : this() {
            size = unit.size
            name = unit.name
        }
    }

    class ObjectPrototypeDto() {
        var id: String = ""
        var name: String = ""
        var definitionOrigin: Vec3Dto = Vec3Dto()
        var definitionAxisU: Vec3Dto = Vec3Dto()
        var definitionAxisV: Vec3Dto = Vec3Dto()
        var definitionAxisW: Vec3Dto = Vec3Dto()
        var gluedToSurface: Boolean = false
        var kind: String = GroupScene.PrototypeKind.MESH.name
        var externalReferenceEnabled: Boolean = false
        var externalReferencePath: String = ""
        var voxelColor: ColorDto? = null
        var voxels: MutableList<VoxelDto> = mutableListOf()
        var architectureWalls: MutableList<ArchitectureWallDto> = mutableListOf()
        var architectureSlabs: MutableList<ArchitectureSlabDto> = mutableListOf()
        var architectureStairs: MutableList<ArchitectureStairDto> = mutableListOf()
        var architectureFrames: MutableList<ArchitectureFrameDto> = mutableListOf()
        var hvacPlumbingRuns: MutableList<HvacPlumbingDto> = mutableListOf()
        var hvacVentilationDucts: MutableList<HvacVentilationDto> = mutableListOf()
        var hotspots: MutableList<HotspotDto> = mutableListOf()
        var prototypeVertices: MutableList<PrototypeVertexDto> = mutableListOf()
        var segments: MutableList<SegmentDto> = mutableListOf()
        var faces: MutableList<FaceDto> = mutableListOf()
        var dimensions: MutableList<DimensionDto> = mutableListOf()
        var texts: MutableList<TextDto> = mutableListOf()

        fun createPrototypeShell(defaultColor: Color): GroupScene.ObjectPrototype {
            val prototypeKind = parsedKind()
            val voxelStore = if (prototypeKind == GroupScene.PrototypeKind.VOXEL) VoxelStore() else null
            val architectureStore = if (prototypeKind == GroupScene.PrototypeKind.ARCHITECTURE || id == "root") ArchitectureStore() else null
            val hvacStore = if (id == "root") HvacStore() else null
            return GroupScene.ObjectPrototype(
                id = id.ifBlank { java.util.UUID.randomUUID().toString() },
                name = name.ifBlank { "Object" },
                definitionOrigin = definitionOrigin.toVector3(),
                definitionAxisU = definitionAxisU.toVector3(),
                definitionAxisV = definitionAxisV.toVector3(),
                definitionAxisW = definitionAxisW.toVector3(),
                gluedToSurface = gluedToSurface,
                kind = prototypeKind,
                voxelColor = voxelColor?.toColor() ?: Color(defaultColor),
                voxelStore = voxelStore,
                architectureStore = architectureStore,
                hvacStore = hvacStore,
                hotspotStore = HotspotStore(),
                lineStore = DraftLineStore(),
                faceStore = DraftFaceStore(defaultColor),
                dimensionStore = DraftDimensionStore(),
                textStore = DraftTextStore(),
                externalReferenceEnabled = externalReferenceEnabled,
                externalReferencePath = externalReferencePath
            )
        }

        fun preparePrototypeForIncrementalLoad(prototype: GroupScene.ObjectPrototype, defaultColor: Color) {
            prototype.name = name.ifBlank { prototype.name }
            prototype.definitionOrigin.set(definitionOrigin.toVector3())
            prototype.definitionAxisU.set(definitionAxisU.toVector3())
            prototype.definitionAxisV.set(definitionAxisV.toVector3())
            prototype.definitionAxisW.set(definitionAxisW.toVector3())
            prototype.gluedToSurface = gluedToSurface
            prototype.externalReferenceEnabled = externalReferenceEnabled
            prototype.externalReferencePath = externalReferencePath
            val parsedKind = parsedKind()
            prototype.kind = when {
                parsedKind == GroupScene.PrototypeKind.VOXEL && prototype.voxelStore == null -> GroupScene.PrototypeKind.MESH
                parsedKind == GroupScene.PrototypeKind.ARCHITECTURE && prototype.architectureStore == null -> GroupScene.PrototypeKind.MESH
                else -> parsedKind
            }
            prototype.voxelColor = voxelColor?.toColor() ?: Color(defaultColor)
            prototype.voxelStore?.clear()
            voxels.forEach { voxel ->
                prototype.voxelStore?.set(voxel.x, voxel.y, voxel.z, voxel.color.toColor())
            }
            prototype.architectureStore?.clear()
            restoreArchitecture(prototype.architectureStore)
            prototype.hvacStore?.clear()
            restoreHvac(prototype.hvacStore)
            prototype.hotspotStore.clearAll()
            restoreHotspots(prototype.hotspotStore)
            prototype.lineStore.clearAll()
            prototype.faceStore.clearAll()
            prototype.dimensionStore.clearAll()
            prototype.textStore.clearAll()
            prototype.prototypeVertexIds.clear()
            prototypeVertices.forEach { vertex ->
                prototype.prototypeVertexIds[vertex.toVertexKey()] = vertex.id.ifBlank { java.util.UUID.randomUUID().toString() }
            }
        }

        fun finalizePrototypeAfterIncrementalLoad(prototype: GroupScene.ObjectPrototype) {
            if (prototype.kind == GroupScene.PrototypeKind.VOXEL) {
                rebuildVoxelGeometry(prototype)
            }
        }

        fun toPrototype(defaultColor: Color): GroupScene.ObjectPrototype {
            val prototype = createPrototypeShell(defaultColor)
            preparePrototypeForIncrementalLoad(prototype, defaultColor)
            applyGeometry(prototype, defaultColor)
            finalizePrototypeAfterIncrementalLoad(prototype)
            return prototype
        }

        fun applyTo(prototype: GroupScene.ObjectPrototype, defaultColor: Color) {
            preparePrototypeForIncrementalLoad(prototype, defaultColor)
            applyGeometry(prototype, defaultColor)
            finalizePrototypeAfterIncrementalLoad(prototype)
        }

        private fun applyGeometry(prototype: GroupScene.ObjectPrototype, defaultColor: Color? = null) {
            segments.forEach { segment ->
                prototype.lineStore.addSegment(
                    segment.start.toVector3(),
                    segment.end.toVector3(),
                    autoCleanup = false,
                    id = segment.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
            faces.forEach { face ->
                prototype.faceStore.addTriangle(
                    face.a.toVector3(),
                    face.b.toVector3(),
                    face.c.toVector3(),
                    face.color.toColor(),
                    id = face.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
            dimensions.forEach { dimension ->
                prototype.dimensionStore.addDimension(
                    dimension.start.toVector3(),
                    dimension.end.toVector3(),
                    dimension.offset.toVector3()
                )
            }
            texts.forEach { text ->
                prototype.textStore.addText(
                    text.position.toVector3(),
                    text.text,
                    text.size,
                    text.normal.toVector3(),
                    text.axisU.toVector3(),
                    text.screenText,
                    kind = runCatching { DraftTextStore.Kind.valueOf(text.kind) }.getOrDefault(DraftTextStore.Kind.BITMAP),
                    tracking = text.tracking,
                    lineSpacing = text.lineSpacing,
                    glyphSourcePath = text.glyphSourcePath
                )
            }
        }

        private fun parsedKind(): GroupScene.PrototypeKind {
            return try {
                GroupScene.PrototypeKind.valueOf(kind)
            } catch (_: IllegalArgumentException) {
                GroupScene.PrototypeKind.MESH
            }
        }

        private fun restoreArchitecture(store: ArchitectureStore?) {
            val architecture = store ?: return
            architectureWalls.forEach { wall ->
                val created = architecture.addWall(
                    start = wall.start.toVector3(),
                    end = wall.end.toVector3(),
                    thickness = wall.thickness,
                    height = wall.height,
                    inclinationDeg = wall.inclinationDeg,
                    exteriorColor = wall.exteriorColor.toColor(),
                    interiorColor = wall.interiorColor.toColor(),
                    name = wall.name,
                    id = wall.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
                wall.holes.forEach { hole ->
                    architecture.addHole(
                        wallId = created.id,
                        u0 = hole.u0,
                        u1 = hole.u1,
                        v0 = hole.v0,
                        v1 = hole.v1,
                        minSize = 0f,
                        name = hole.name,
                        id = hole.id.ifBlank { java.util.UUID.randomUUID().toString() }
                    )
                }
            }
            architectureSlabs.forEach { slab ->
                val created = architecture.addSlab(
                    minCorner = slab.min.toVector3(),
                    maxCorner = slab.max.toVector3(),
                    thickness = slab.thickness,
                    topColor = slab.topColor.toColor(),
                    bottomColor = slab.bottomColor.toColor(),
                    sideColor = slab.sideColor.toColor(),
                    name = slab.name,
                    id = slab.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
                created.axisU.set(slab.axisU.toVector3())
                created.axisV.set(slab.axisV.toVector3())
                created.normal.set(slab.normal.toVector3())
                slab.holes.forEach { hole ->
                    architecture.addSlabHole(
                        slabId = created.id,
                        u0 = hole.u0,
                        u1 = hole.u1,
                        v0 = hole.v0,
                        v1 = hole.v1,
                        minSize = 0f,
                        name = hole.name,
                        id = hole.id.ifBlank { java.util.UUID.randomUUID().toString() }
                    )
                }
            }
            architectureStairs.forEach { stair ->
                architecture.addStair(
                    minCorner = stair.min.toVector3(),
                    maxCorner = stair.max.toVector3(),
                    contourPoints = stair.contour.map { it.toVector3() },
                    walkingPathPoints = stair.walkingPath.map { it.toVector3() },
                    walkingStart = stair.walkingStart.toVector3(),
                    walkingEnd = stair.walkingEnd.toVector3(),
                    height = stair.height,
                    stepCount = stair.stepCount,
                    supportThickness = stair.supportThickness,
                    railLeftEnabled = stair.railLeft,
                    railRightEnabled = stair.railRight,
                    treadColor = stair.treadColor.toColor(),
                    supportColor = stair.supportColor.toColor(),
                    name = stair.name,
                    id = stair.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
            architectureFrames.forEach { frame ->
                val kind = try {
                    ArchitectureStore.FrameKind.valueOf(frame.kind)
                } catch (_: IllegalArgumentException) {
                    ArchitectureStore.FrameKind.WINDOW
                }
                architecture.addFrame(
                    cornerA = frame.cornerA.toVector3(),
                    cornerB = frame.cornerB.toVector3(),
                    contourPoints = frame.contour.map { it.toVector3() },
                    normal = frame.normal.toVector3(),
                    depth = frame.depth,
                    frameWidth = frame.frameWidth,
                    kind = kind,
                    color = frame.color.toColor(),
                    glazingEnabled = frame.glazingEnabled,
                    glazingColor = frame.glazingColor.toColor(),
                    name = frame.name,
                    id = frame.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
        }

        private fun restoreHvac(store: HvacStore?) {
            val hvac = store ?: return
            hvacPlumbingRuns.forEach { run ->
                hvac.addPlumbingRun(
                    path = run.path.map { it.toVector3() },
                    diameter = run.diameter,
                    sides = run.sides,
                    color = run.color.toColor(),
                    name = run.name,
                    id = run.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
            hvacVentilationDucts.forEach { duct ->
                hvac.addVentilationDuct(
                    start = duct.start.toVector3(),
                    end = duct.end.toVector3(),
                    binormalRef = duct.binormalRef.toVector3(),
                    autoJoinEnabled = duct.autoJoinEnabled,
                    width = duct.width,
                    height = duct.height,
                    humpHalfSpan = duct.humpHalfSpan,
                    humpClearance = duct.humpClearance,
                    color = duct.color.toColor(),
                    name = duct.name,
                    id = duct.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
        }

        private fun restoreHotspots(store: HotspotStore?) {
            val hotspotsStore = store ?: return
            hotspots.forEach { hotspot ->
                val operation = try {
                    HotspotStore.OperationKind.valueOf(hotspot.operation)
                } catch (_: IllegalArgumentException) {
                    HotspotStore.OperationKind.MOVE
                }
                val shape = try {
                    HotspotStore.ShapeKind.valueOf(hotspot.shape)
                } catch (_: IllegalArgumentException) {
                    HotspotStore.ShapeKind.CIRCLE
                }
                hotspotsStore.addHotspot(
                    position = hotspot.position.toVector3(),
                    operation = operation,
                    shape = shape,
                    color = hotspot.color.toColor(),
                    referencePosition = hotspot.referencePosition?.toVector3(),
                    attachedHotspotIds = hotspot.attachedHotspotIds.toSet(),
                    attachedVertexIds = hotspot.attachedVertexIds.toSet(),
                    attachedSegments = hotspot.attachedSegments.map { it.toSegmentRef() }.toSet(),
                    attachedTriangles = hotspot.attachedTriangles.map { it.toTriangleRef() }.toSet(),
                    name = hotspot.name,
                    id = hotspot.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
            hotspotsStore.clearSelected()
        }

        companion object {
            fun fromPrototype(prototype: GroupScene.ObjectPrototype): ObjectPrototypeDto {
                val dto = ObjectPrototypeDto()
                dto.id = prototype.id
                dto.name = prototype.name
                dto.definitionOrigin = Vec3Dto(prototype.definitionOrigin)
                dto.definitionAxisU = Vec3Dto(prototype.definitionAxisU)
                dto.definitionAxisV = Vec3Dto(prototype.definitionAxisV)
                dto.definitionAxisW = Vec3Dto(prototype.definitionAxisW)
                dto.gluedToSurface = prototype.gluedToSurface
                dto.kind = prototype.kind.name
                dto.externalReferenceEnabled = prototype.externalReferenceEnabled
                dto.externalReferencePath = prototype.externalReferencePath
                dto.voxelColor = ColorDto(prototype.voxelColor)
                dto.voxels = prototype.voxelStore?.all()?.map { voxel ->
                    VoxelDto(voxel.x, voxel.y, voxel.z, ColorDto(voxel.color))
                }?.toMutableList() ?: mutableListOf()
                dto.architectureWalls = prototype.architectureStore?.allWalls()?.map { wall ->
                    ArchitectureWallDto(
                        id = wall.id,
                        name = wall.name,
                        start = Vec3Dto(wall.start),
                        end = Vec3Dto(wall.end),
                        thickness = wall.thickness,
                        height = wall.height,
                        inclinationDeg = wall.inclinationDeg,
                        exteriorColor = ColorDto(wall.exteriorColor),
                        interiorColor = ColorDto(wall.interiorColor),
                        holes = wall.holes.map { hole ->
                            ArchitectureHoleDto(
                                id = hole.id,
                                name = hole.name,
                                u0 = hole.u0,
                                u1 = hole.u1,
                                v0 = hole.v0,
                                v1 = hole.v1
                            )
                        }.toMutableList()
                    )
                }?.toMutableList() ?: mutableListOf()
                dto.architectureSlabs = prototype.architectureStore?.allSlabs()?.map { slab ->
                    ArchitectureSlabDto(
                        id = slab.id,
                        name = slab.name,
                        min = Vec3Dto(slab.min),
                        max = Vec3Dto(slab.max),
                        axisU = Vec3Dto(slab.axisU),
                        axisV = Vec3Dto(slab.axisV),
                        normal = Vec3Dto(slab.normal),
                        thickness = slab.thickness,
                        topColor = ColorDto(slab.topColor),
                        bottomColor = ColorDto(slab.bottomColor),
                        sideColor = ColorDto(slab.sideColor),
                        holes = slab.holes.map { hole ->
                            ArchitectureHoleDto(
                                id = hole.id,
                                name = hole.name,
                                u0 = hole.u0,
                                u1 = hole.u1,
                                v0 = hole.v0,
                                v1 = hole.v1
                            )
                        }.toMutableList()
                    )
                }?.toMutableList() ?: mutableListOf()
                dto.architectureStairs = prototype.architectureStore?.allStairs()?.map { stair ->
                    ArchitectureStairDto(
                        id = stair.id,
                        name = stair.name,
                        min = Vec3Dto(stair.min),
                        max = Vec3Dto(stair.max),
                        contour = stair.contour.map { Vec3Dto(it) }.toMutableList(),
                        walkingPath = stair.walkingPath.map { Vec3Dto(it) }.toMutableList(),
                        walkingStart = Vec3Dto(stair.walkingStart),
                        walkingEnd = Vec3Dto(stair.walkingEnd),
                        height = stair.height,
                        stepCount = stair.stepCount,
                        supportThickness = stair.supportThickness,
                        railLeft = stair.railLeftEnabled,
                        railRight = stair.railRightEnabled,
                        treadColor = ColorDto(stair.treadColor),
                        supportColor = ColorDto(stair.supportColor)
                    )
                }?.toMutableList() ?: mutableListOf()
                dto.architectureFrames = prototype.architectureStore?.allFrames()?.map { frame ->
                    ArchitectureFrameDto(
                        id = frame.id,
                        name = frame.name,
                        cornerA = Vec3Dto(frame.cornerA),
                        cornerB = Vec3Dto(frame.cornerB),
                        contour = frame.contour.map { Vec3Dto(it) }.toMutableList(),
                        normal = Vec3Dto(frame.normal),
                        depth = frame.depth,
                        frameWidth = frame.frameWidth,
                        kind = frame.kind.name,
                        color = ColorDto(frame.color),
                        glazingEnabled = frame.glazingEnabled,
                        glazingColor = ColorDto(frame.glazingColor)
                    )
                }?.toMutableList() ?: mutableListOf()
                dto.hvacPlumbingRuns = prototype.hvacStore?.allPlumbingRuns()?.map { run ->
                    HvacPlumbingDto(
                        id = run.id,
                        name = run.name,
                        path = run.path.map { Vec3Dto(it) }.toMutableList(),
                        diameter = run.diameter,
                        sides = run.sides,
                        color = ColorDto(run.color)
                    )
                }?.toMutableList() ?: mutableListOf()
                dto.hvacVentilationDucts = prototype.hvacStore?.allVentilationDucts()?.map { duct ->
                    HvacVentilationDto(
                        id = duct.id,
                        name = duct.name,
                        start = Vec3Dto(duct.start),
                        end = Vec3Dto(duct.end),
                        binormalRef = Vec3Dto(duct.binormalRef),
                        autoJoinEnabled = duct.autoJoinEnabled,
                        width = duct.width,
                        height = duct.height,
                        humpHalfSpan = duct.humpHalfSpan,
                        humpClearance = duct.humpClearance,
                        color = ColorDto(duct.color)
                    )
                }?.toMutableList() ?: mutableListOf()
                dto.hotspots = prototype.hotspotStore.allHotspots().map { hotspot ->
                    HotspotDto(
                        id = hotspot.id,
                        name = hotspot.name,
                        position = Vec3Dto(hotspot.position),
                        operation = hotspot.operation.name,
                        shape = hotspot.shape.name,
                        color = ColorDto(hotspot.color),
                        referencePosition = hotspot.referencePosition?.let { Vec3Dto(it) },
                        attachedHotspotIds = hotspot.attachedHotspotIds.toMutableList(),
                        attachedVertexIds = hotspot.attachedVertexIds.toMutableList(),
                        attachedSegments = hotspot.attachedSegments.map { ref ->
                            HotspotSegmentRefDto(
                                id = ref.id,
                                a = HotspotVertexKeyDto(ref.a.x, ref.a.y, ref.a.z),
                                b = HotspotVertexKeyDto(ref.b.x, ref.b.y, ref.b.z)
                            )
                        }.toMutableList(),
                        attachedTriangles = hotspot.attachedTriangles.map { ref ->
                            HotspotTriangleRefDto(
                                id = ref.id,
                                a = HotspotVertexKeyDto(ref.a.x, ref.a.y, ref.a.z),
                                b = HotspotVertexKeyDto(ref.b.x, ref.b.y, ref.b.z),
                                c = HotspotVertexKeyDto(ref.c.x, ref.c.y, ref.c.z)
                            )
                        }.toMutableList()
                    )
                }.toMutableList()
                dto.prototypeVertices = prototype.prototypeVertexIds.map { (key, id) ->
                    PrototypeVertexDto(
                        id = id,
                        x = key.x,
                        y = key.y,
                        z = key.z
                    )
                }.toMutableList()
                dto.segments = prototype.lineStore.getSegments().map { seg ->
                    SegmentDto(Vec3Dto(seg.start), Vec3Dto(seg.end), seg.id)
                }.toMutableList()
                dto.faces = prototype.faceStore.getTriangles().map { tri ->
                    val color = prototype.faceStore.colorFor(tri)
                    FaceDto(Vec3Dto(tri.a), Vec3Dto(tri.b), Vec3Dto(tri.c), ColorDto(color), tri.id)
                }.toMutableList()
                dto.dimensions = prototype.dimensionStore.getDimensions().map { dim ->
                    DimensionDto(Vec3Dto(dim.start), Vec3Dto(dim.end), Vec3Dto(dim.offset))
                }.toMutableList()
                dto.texts = prototype.textStore.getTexts().map { text ->
                    TextDto(
                        Vec3Dto(text.position),
                        text.text,
                        text.size,
                        Vec3Dto(text.normal),
                        Vec3Dto(text.axisU),
                        text.screenText,
                        text.kind.name,
                        text.tracking,
                        text.lineSpacing,
                        text.glyphSourcePath
                    )
                }.toMutableList()
                return dto
            }
        }
    }

    class VoxelDto() {
        var x: Int = 0
        var y: Int = 0
        var z: Int = 0
        var color: ColorDto = ColorDto()

        constructor(x: Int, y: Int, z: Int, color: ColorDto) : this() {
            this.x = x
            this.y = y
            this.z = z
            this.color = color
        }
    }

    class ArchitectureHoleDto() {
        var id: String = ""
        var name: String = ""
        var u0: Float = 0f
        var u1: Float = 0f
        var v0: Float = 0f
        var v1: Float = 0f

        constructor(id: String, name: String, u0: Float, u1: Float, v0: Float, v1: Float) : this() {
            this.id = id
            this.name = name
            this.u0 = u0
            this.u1 = u1
            this.v0 = v0
            this.v1 = v1
        }
    }

    class ArchitectureWallDto() {
        var id: String = ""
        var name: String = ""
        var start: Vec3Dto = Vec3Dto()
        var end: Vec3Dto = Vec3Dto()
        var thickness: Float = 0.2f
        var height: Float = 2.7f
        var inclinationDeg: Float = 0f
        var exteriorColor: ColorDto = ColorDto(Color(0.93f, 0.93f, 0.93f, 1f))
        var interiorColor: ColorDto = ColorDto(Color(0.84f, 0.84f, 0.84f, 1f))
        var holes: MutableList<ArchitectureHoleDto> = mutableListOf()

        constructor(
            id: String,
            name: String,
            start: Vec3Dto,
            end: Vec3Dto,
            thickness: Float,
            height: Float,
            inclinationDeg: Float,
            exteriorColor: ColorDto,
            interiorColor: ColorDto,
            holes: MutableList<ArchitectureHoleDto>
        ) : this() {
            this.id = id
            this.name = name
            this.start = start
            this.end = end
            this.thickness = thickness
            this.height = height
            this.inclinationDeg = inclinationDeg
            this.exteriorColor = exteriorColor
            this.interiorColor = interiorColor
            this.holes = holes
        }
    }

    class ArchitectureSlabDto() {
        var id: String = ""
        var name: String = ""
        var min: Vec3Dto = Vec3Dto()
        var max: Vec3Dto = Vec3Dto()
        var axisU: Vec3Dto = Vec3Dto(Vector3(1f, 0f, 0f))
        var axisV: Vec3Dto = Vec3Dto(Vector3(0f, 0f, 1f))
        var normal: Vec3Dto = Vec3Dto(Vector3(0f, 1f, 0f))
        var thickness: Float = 0.2f
        var topColor: ColorDto = ColorDto(Color(0.93f, 0.93f, 0.93f, 1f))
        var bottomColor: ColorDto = ColorDto(Color(0.84f, 0.84f, 0.84f, 1f))
        var sideColor: ColorDto = ColorDto(Color(0.88f, 0.88f, 0.88f, 1f))
        var holes: MutableList<ArchitectureHoleDto> = mutableListOf()

        constructor(
            id: String,
            name: String,
            min: Vec3Dto,
            max: Vec3Dto,
            axisU: Vec3Dto,
            axisV: Vec3Dto,
            normal: Vec3Dto,
            thickness: Float,
            topColor: ColorDto,
            bottomColor: ColorDto,
            sideColor: ColorDto,
            holes: MutableList<ArchitectureHoleDto>
        ) : this() {
            this.id = id
            this.name = name
            this.min = min
            this.max = max
            this.axisU = axisU
            this.axisV = axisV
            this.normal = normal
            this.thickness = thickness
            this.topColor = topColor
            this.bottomColor = bottomColor
            this.sideColor = sideColor
            this.holes = holes
        }
    }

    class ArchitectureStairDto() {
        var id: String = ""
        var name: String = ""
        var min: Vec3Dto = Vec3Dto()
        var max: Vec3Dto = Vec3Dto()
        var contour: MutableList<Vec3Dto> = mutableListOf()
        var walkingPath: MutableList<Vec3Dto> = mutableListOf()
        var walkingStart: Vec3Dto = Vec3Dto()
        var walkingEnd: Vec3Dto = Vec3Dto()
        var height: Float = 2.7f
        var stepCount: Int = 14
        var supportThickness: Float = 0.2f
        var railLeft: Boolean = true
        var railRight: Boolean = true
        var treadColor: ColorDto = ColorDto(Color(0.93f, 0.93f, 0.93f, 1f))
        var supportColor: ColorDto = ColorDto(Color(0.82f, 0.82f, 0.82f, 1f))

        constructor(
            id: String,
            name: String,
            min: Vec3Dto,
            max: Vec3Dto,
            contour: MutableList<Vec3Dto>,
            walkingPath: MutableList<Vec3Dto>,
            walkingStart: Vec3Dto,
            walkingEnd: Vec3Dto,
            height: Float,
            stepCount: Int,
            supportThickness: Float,
            railLeft: Boolean,
            railRight: Boolean,
            treadColor: ColorDto,
            supportColor: ColorDto
        ) : this() {
            this.id = id
            this.name = name
            this.min = min
            this.max = max
            this.contour = contour
            this.walkingPath = walkingPath
            this.walkingStart = walkingStart
            this.walkingEnd = walkingEnd
            this.height = height
            this.stepCount = stepCount
            this.supportThickness = supportThickness
            this.railLeft = railLeft
            this.railRight = railRight
            this.treadColor = treadColor
            this.supportColor = supportColor
        }
    }

    class ArchitectureFrameDto() {
        var id: String = ""
        var name: String = ""
        var cornerA: Vec3Dto = Vec3Dto()
        var cornerB: Vec3Dto = Vec3Dto()
        var contour: MutableList<Vec3Dto> = mutableListOf()
        var normal: Vec3Dto = Vec3Dto(Vector3(0f, 1f, 0f))
        var depth: Float = 0.12f
        var frameWidth: Float = 0.06f
        var kind: String = ArchitectureStore.FrameKind.WINDOW.name
        var color: ColorDto = ColorDto(Color(0.90f, 0.90f, 0.90f, 1f))
        var glazingEnabled: Boolean = false
        var glazingColor: ColorDto = ColorDto(Color(0.72f, 0.84f, 0.95f, 0.40f))

        constructor(
            id: String,
            name: String,
            cornerA: Vec3Dto,
            cornerB: Vec3Dto,
            contour: MutableList<Vec3Dto>,
            normal: Vec3Dto,
            depth: Float,
            frameWidth: Float,
            kind: String,
            color: ColorDto,
            glazingEnabled: Boolean,
            glazingColor: ColorDto
        ) : this() {
            this.id = id
            this.name = name
            this.cornerA = cornerA
            this.cornerB = cornerB
            this.contour = contour
            this.normal = normal
            this.depth = depth
            this.frameWidth = frameWidth
            this.kind = kind
            this.color = color
            this.glazingEnabled = glazingEnabled
            this.glazingColor = glazingColor
        }
    }

    class HvacPlumbingDto() {
        var id: String = ""
        var name: String = ""
        var path: MutableList<Vec3Dto> = mutableListOf()
        var diameter: Float = 0.2f
        var sides: Int = 16
        var color: ColorDto = ColorDto(Color(0.70f, 0.82f, 0.95f, 1f))

        constructor(
            id: String,
            name: String,
            path: MutableList<Vec3Dto>,
            diameter: Float,
            sides: Int,
            color: ColorDto
        ) : this() {
            this.id = id
            this.name = name
            this.path = path
            this.diameter = diameter
            this.sides = sides
            this.color = color
        }
    }

    class HvacVentilationDto() {
        var id: String = ""
        var name: String = ""
        var start: Vec3Dto = Vec3Dto()
        var end: Vec3Dto = Vec3Dto()
        var binormalRef: Vec3Dto = Vec3Dto()
        var autoJoinEnabled: Boolean = false
        var width: Float = 0.5f
        var height: Float = 0.25f
        var humpHalfSpan: Float = 0.625f
        var humpClearance: Float = 0.05f
        var color: ColorDto = ColorDto(Color(0.82f, 0.82f, 0.82f, 1f))

        constructor(
            id: String,
            name: String,
            start: Vec3Dto,
            end: Vec3Dto,
            binormalRef: Vec3Dto,
            autoJoinEnabled: Boolean,
            width: Float,
            height: Float,
            humpHalfSpan: Float,
            humpClearance: Float,
            color: ColorDto
        ) : this() {
            this.id = id
            this.name = name
            this.start = start
            this.end = end
            this.binormalRef = binormalRef
            this.autoJoinEnabled = autoJoinEnabled
            this.width = width
            this.height = height
            this.humpHalfSpan = humpHalfSpan
            this.humpClearance = humpClearance
            this.color = color
        }
    }

    class HotspotVertexKeyDto() {
        var x: Int = 0
        var y: Int = 0
        var z: Int = 0

        constructor(x: Int, y: Int, z: Int) : this() {
            this.x = x
            this.y = y
            this.z = z
        }

        fun toVertexKey(): HotspotStore.VertexKey = HotspotStore.VertexKey(x, y, z)
    }

    class HotspotSegmentRefDto() {
        var id: String = ""
        var a: HotspotVertexKeyDto = HotspotVertexKeyDto()
        var b: HotspotVertexKeyDto = HotspotVertexKeyDto()

        constructor(id: String, a: HotspotVertexKeyDto, b: HotspotVertexKeyDto) : this() {
            this.id = id
            this.a = a
            this.b = b
        }

        fun toSegmentRef(): HotspotStore.SegmentRef = HotspotStore.SegmentRef(id, a.toVertexKey(), b.toVertexKey())
    }

    class HotspotTriangleRefDto() {
        var id: String = ""
        var a: HotspotVertexKeyDto = HotspotVertexKeyDto()
        var b: HotspotVertexKeyDto = HotspotVertexKeyDto()
        var c: HotspotVertexKeyDto = HotspotVertexKeyDto()

        constructor(id: String, a: HotspotVertexKeyDto, b: HotspotVertexKeyDto, c: HotspotVertexKeyDto) : this() {
            this.id = id
            this.a = a
            this.b = b
            this.c = c
        }

        fun toTriangleRef(): HotspotStore.TriangleRef =
            HotspotStore.TriangleRef(id, a.toVertexKey(), b.toVertexKey(), c.toVertexKey())
    }

    class HotspotDto() {
        var id: String = ""
        var name: String = ""
        var position: Vec3Dto = Vec3Dto()
        var operation: String = HotspotStore.OperationKind.MOVE.name
        var shape: String = HotspotStore.ShapeKind.CIRCLE.name
        var color: ColorDto = ColorDto(Color(0.2f, 0.55f, 0.95f, 1f))
        var referencePosition: Vec3Dto? = null
        var attachedHotspotIds: MutableList<String> = mutableListOf()
        var attachedVertexIds: MutableList<String> = mutableListOf()
        var attachedSegments: MutableList<HotspotSegmentRefDto> = mutableListOf()
        var attachedTriangles: MutableList<HotspotTriangleRefDto> = mutableListOf()

        constructor(
            id: String,
            name: String,
            position: Vec3Dto,
            operation: String,
            shape: String,
            color: ColorDto,
            referencePosition: Vec3Dto?,
            attachedHotspotIds: MutableList<String>,
            attachedVertexIds: MutableList<String>,
            attachedSegments: MutableList<HotspotSegmentRefDto>,
            attachedTriangles: MutableList<HotspotTriangleRefDto>
        ) : this() {
            this.id = id
            this.name = name
            this.position = position
            this.operation = operation
            this.shape = shape
            this.color = color
            this.referencePosition = referencePosition
            this.attachedHotspotIds = attachedHotspotIds
            this.attachedVertexIds = attachedVertexIds
            this.attachedSegments = attachedSegments
            this.attachedTriangles = attachedTriangles
        }
    }

    class DimensionDto() {
        var start: Vec3Dto = Vec3Dto()
        var end: Vec3Dto = Vec3Dto()
        var offset: Vec3Dto = Vec3Dto()

        constructor(start: Vec3Dto, end: Vec3Dto, offset: Vec3Dto) : this() {
            this.start = start
            this.end = end
            this.offset = offset
        }
    }

    class TextDto() {
        var position: Vec3Dto = Vec3Dto()
        var text: String = ""
        var size: Float = 0.1f
        var normal: Vec3Dto = Vec3Dto()
        var axisU: Vec3Dto = Vec3Dto()
        var screenText: Boolean = true
        var kind: String = DraftTextStore.Kind.BITMAP.name
        var tracking: Float = 0.1f
        var lineSpacing: Float = 1.25f
        var glyphSourcePath: String = "embedded:alphabet"

        constructor(
            position: Vec3Dto,
            text: String,
            size: Float = 0.1f,
            normal: Vec3Dto = Vec3Dto(Vector3(0f, 1f, 0f)),
            axisU: Vec3Dto = Vec3Dto(Vector3(1f, 0f, 0f)),
            screenText: Boolean = true,
            kind: String = DraftTextStore.Kind.BITMAP.name,
            tracking: Float = 0.1f,
            lineSpacing: Float = 1.25f,
            glyphSourcePath: String = "embedded:alphabet"
        ) : this() {
            this.position = position
            this.text = text
            this.size = size
            this.normal = normal
            this.axisU = axisU
            this.screenText = screenText
            this.kind = kind
            this.tracking = tracking
            this.lineSpacing = lineSpacing
            this.glyphSourcePath = glyphSourcePath
        }
    }

    class GroupInstanceDto() {
        var id: String = ""
        var prototypeId: String = ""
        var instanceOrigin: Vec3Dto = Vec3Dto()
        var instanceAxisU: Vec3Dto = Vec3Dto()
        var instanceAxisV: Vec3Dto = Vec3Dto()
        var instanceAxisW: Vec3Dto = Vec3Dto()
        var hotspotPositions: MutableList<HotspotPositionDto> = mutableListOf()
        var hotspotSegmentAttachments: MutableList<HotspotSegmentAttachmentsDto> = mutableListOf()
        var hotspotTriangleAttachments: MutableList<HotspotTriangleAttachmentsDto> = mutableListOf()
        var overrideSegments: MutableList<SegmentDto> = mutableListOf()
        var overrideFaces: MutableList<FaceDto> = mutableListOf()
        var overrideDimensions: MutableList<DimensionDto> = mutableListOf()
        var overrideTexts: MutableList<TextDto> = mutableListOf()
        var children: MutableList<GroupInstanceDto> = mutableListOf()

        fun createInstanceShell(
            prototypes: Map<String, GroupScene.ObjectPrototype>,
            defaultColor: Color
        ): GroupScene.GroupNode {
            val prototype = prototypes[prototypeId] ?: GroupScene.ObjectPrototype(
                id = prototypeId.ifBlank { java.util.UUID.randomUUID().toString() },
                name = "Object",
                definitionOrigin = Vector3(),
                definitionAxisU = Vector3(1f, 0f, 0f),
                definitionAxisV = Vector3(0f, 1f, 0f),
                definitionAxisW = Vector3(0f, 0f, 1f),
                gluedToSurface = false,
                voxelColor = Color(defaultColor),
                voxelStore = null,
                architectureStore = null,
                lineStore = DraftLineStore(),
                faceStore = DraftFaceStore(defaultColor),
                dimensionStore = DraftDimensionStore(),
                textStore = DraftTextStore()
            )
            return GroupScene.GroupNode(
                id = id.ifBlank { java.util.UUID.randomUUID().toString() },
                prototype = prototype,
                instanceOrigin = instanceOrigin.toVector3(),
                instanceAxisU = instanceAxisU.toVector3(),
                instanceAxisV = instanceAxisV.toVector3(),
                instanceAxisW = instanceAxisW.toVector3()
            )
        }

        fun toInstance(
            prototypes: Map<String, GroupScene.ObjectPrototype>,
            defaultColor: Color
        ): GroupScene.GroupNode {
            val group = createInstanceShell(prototypes, defaultColor)
            hotspotPositions.forEach { hotspot ->
                if (hotspot.id.isNotBlank()) {
                    group.hotspotPositionOverrides[hotspot.id] = hotspot.position.toVector3()
                }
            }
            hotspotSegmentAttachments.forEach { dto ->
                if (dto.id.isNotBlank()) {
                    group.hotspotAttachedSegmentOverrides[dto.id] = dto.refs.map { it.toSegmentRef() }.toMutableSet()
                }
            }
            hotspotTriangleAttachments.forEach { dto ->
                if (dto.id.isNotBlank()) {
                    group.hotspotAttachedTriangleOverrides[dto.id] = dto.refs.map { it.toTriangleRef() }.toMutableSet()
                }
            }
            if (
                overrideSegments.isNotEmpty() ||
                overrideFaces.isNotEmpty() ||
                overrideDimensions.isNotEmpty() ||
                overrideTexts.isNotEmpty()
            ) {
                val lineOverride = DraftLineStore()
                overrideSegments.forEach { segment ->
                    lineOverride.addSegment(
                        segment.start.toVector3(),
                        segment.end.toVector3(),
                        autoCleanup = false,
                        id = segment.id.ifBlank { java.util.UUID.randomUUID().toString() }
                    )
                }
                val faceOverride = DraftFaceStore(defaultColor)
                overrideFaces.forEach { face ->
                    faceOverride.addTriangle(
                        face.a.toVector3(),
                        face.b.toVector3(),
                        face.c.toVector3(),
                        face.color.toColor(),
                        id = face.id.ifBlank { java.util.UUID.randomUUID().toString() }
                    )
                }
                val dimensionOverride = DraftDimensionStore()
                overrideDimensions.forEach { dimension ->
                    dimensionOverride.addDimension(
                        dimension.start.toVector3(),
                        dimension.end.toVector3(),
                        dimension.offset.toVector3()
                    )
                }
                val textOverride = DraftTextStore()
                overrideTexts.forEach { text ->
                    textOverride.addText(
                        text.position.toVector3(),
                        text.text,
                        text.size,
                        text.normal.toVector3(),
                        text.axisU.toVector3(),
                        text.screenText,
                        kind = runCatching { DraftTextStore.Kind.valueOf(text.kind) }.getOrDefault(DraftTextStore.Kind.BITMAP),
                        tracking = text.tracking,
                        lineSpacing = text.lineSpacing,
                        glyphSourcePath = text.glyphSourcePath
                    )
                }
                group.lineStoreOverride = lineOverride
                group.faceStoreOverride = faceOverride
                group.dimensionStoreOverride = dimensionOverride
                group.textStoreOverride = textOverride
            }
            children.forEach { child ->
                val childGroup = child.toInstance(prototypes, defaultColor)
                childGroup.parent = group
                group.children.add(childGroup)
            }
            return group
        }

        companion object {
            fun fromInstance(group: GroupScene.GroupNode): GroupInstanceDto {
                val dto = GroupInstanceDto()
                dto.id = group.id
                dto.prototypeId = group.prototype.id
                dto.instanceOrigin = Vec3Dto(group.instanceOrigin)
                dto.instanceAxisU = Vec3Dto(group.instanceAxisU)
                dto.instanceAxisV = Vec3Dto(group.instanceAxisV)
                dto.instanceAxisW = Vec3Dto(group.instanceAxisW)
                dto.hotspotPositions = group.hotspotPositionOverrides.map { (hotspotId, position) ->
                    HotspotPositionDto(hotspotId, Vec3Dto(position))
                }.toMutableList()
                dto.hotspotSegmentAttachments = group.hotspotAttachedSegmentOverrides.map { (hotspotId, refs) ->
                    HotspotSegmentAttachmentsDto(
                        id = hotspotId,
                        refs = refs.map { ref ->
                            HotspotSegmentRefDto(
                                id = ref.id,
                                a = HotspotVertexKeyDto(ref.a.x, ref.a.y, ref.a.z),
                                b = HotspotVertexKeyDto(ref.b.x, ref.b.y, ref.b.z)
                            )
                        }.toMutableList()
                    )
                }.toMutableList()
                dto.hotspotTriangleAttachments = group.hotspotAttachedTriangleOverrides.map { (hotspotId, refs) ->
                    HotspotTriangleAttachmentsDto(
                        id = hotspotId,
                        refs = refs.map { ref ->
                            HotspotTriangleRefDto(
                                id = ref.id,
                                a = HotspotVertexKeyDto(ref.a.x, ref.a.y, ref.a.z),
                                b = HotspotVertexKeyDto(ref.b.x, ref.b.y, ref.b.z),
                                c = HotspotVertexKeyDto(ref.c.x, ref.c.y, ref.c.z)
                            )
                        }.toMutableList()
                    )
                }.toMutableList()
                if (group.hasGeometryOverrides()) {
                    dto.overrideSegments = group.lineStore.getSegments().map { seg ->
                        SegmentDto(Vec3Dto(seg.start), Vec3Dto(seg.end), seg.id)
                    }.toMutableList()
                    dto.overrideFaces = group.faceStore.getTriangles().map { tri ->
                        val color = group.faceStore.colorFor(tri)
                        FaceDto(Vec3Dto(tri.a), Vec3Dto(tri.b), Vec3Dto(tri.c), ColorDto(color), tri.id)
                    }.toMutableList()
                    dto.overrideDimensions = group.dimensionStore.getDimensions().map { dimension ->
                        DimensionDto(
                            start = Vec3Dto(dimension.start),
                            end = Vec3Dto(dimension.end),
                            offset = Vec3Dto(dimension.offset)
                        )
                    }.toMutableList()
                    dto.overrideTexts = group.textStore.getTexts().map { text ->
                        TextDto(
                            position = Vec3Dto(text.position),
                            text = text.text,
                            size = text.size,
                            normal = Vec3Dto(text.normal),
                            axisU = Vec3Dto(text.axisU),
                            screenText = text.screenText,
                            kind = text.kind.name,
                            tracking = text.tracking,
                            lineSpacing = text.lineSpacing,
                            glyphSourcePath = text.glyphSourcePath
                        )
                    }.toMutableList()
                }
                dto.children = group.children.map { child -> fromInstance(child) }.toMutableList()
                return dto
            }
        }
    }

    class HotspotPositionDto() {
        var id: String = ""
        var position: Vec3Dto = Vec3Dto()

        constructor(id: String, position: Vec3Dto) : this() {
            this.id = id
            this.position = position
        }
    }

    class HotspotSegmentAttachmentsDto() {
        var id: String = ""
        var refs: MutableList<HotspotSegmentRefDto> = mutableListOf()

        constructor(id: String, refs: MutableList<HotspotSegmentRefDto>) : this() {
            this.id = id
            this.refs = refs
        }
    }

    class HotspotTriangleAttachmentsDto() {
        var id: String = ""
        var refs: MutableList<HotspotTriangleRefDto> = mutableListOf()

        constructor(id: String, refs: MutableList<HotspotTriangleRefDto>) : this() {
            this.id = id
            this.refs = refs
        }
    }

    class PrototypeVertexDto() {
        var id: String = ""
        var x: Int = 0
        var y: Int = 0
        var z: Int = 0

        constructor(id: String, x: Int, y: Int, z: Int) : this() {
            this.id = id
            this.x = x
            this.y = y
            this.z = z
        }

        fun toVertexKey(): HotspotStore.VertexKey = HotspotStore.VertexKey(x, y, z)
    }

    class SegmentDto() {
        var id: String = ""
        var start: Vec3Dto = Vec3Dto()
        var end: Vec3Dto = Vec3Dto()

        constructor(start: Vec3Dto, end: Vec3Dto, id: String = "") : this() {
            this.id = id
            this.start = start
            this.end = end
        }
    }

    class FaceDto() {
        var id: String = ""
        var a: Vec3Dto = Vec3Dto()
        var b: Vec3Dto = Vec3Dto()
        var c: Vec3Dto = Vec3Dto()
        var color: ColorDto = ColorDto()

        constructor(a: Vec3Dto, b: Vec3Dto, c: Vec3Dto, color: ColorDto, id: String = "") : this() {
            this.id = id
            this.a = a
            this.b = b
            this.c = c
            this.color = color
        }
    }

    class GroupDto() {
        var id: String = ""
        var name: String = ""
        var origin: Vec3Dto = Vec3Dto()
        var axisU: Vec3Dto = Vec3Dto()
        var axisV: Vec3Dto = Vec3Dto()
        var axisW: Vec3Dto = Vec3Dto()
        var definitionOrigin: Vec3Dto? = null
        var definitionAxisU: Vec3Dto? = null
        var definitionAxisV: Vec3Dto? = null
        var definitionAxisW: Vec3Dto? = null
        var instanceOrigin: Vec3Dto? = null
        var instanceAxisU: Vec3Dto? = null
        var instanceAxisV: Vec3Dto? = null
        var instanceAxisW: Vec3Dto? = null
        var gluedToSurface: Boolean = false
        var segments: MutableList<SegmentDto> = mutableListOf()
        var faces: MutableList<FaceDto> = mutableListOf()
        var children: MutableList<GroupDto> = mutableListOf()

        fun createGroupShell(defaultColor: Color): GroupScene.GroupNode {
            val defOrigin = definitionOrigin?.toVector3() ?: Vector3()
            val defAxisU = definitionAxisU?.toVector3() ?: Vector3(1f, 0f, 0f)
            val defAxisV = definitionAxisV?.toVector3() ?: Vector3(0f, 1f, 0f)
            val defAxisW = definitionAxisW?.toVector3() ?: Vector3(0f, 0f, 1f)
            val instOrigin = instanceOrigin?.toVector3() ?: origin.toVector3()
            val instAxisU = instanceAxisU?.toVector3() ?: axisU.toVector3()
            val instAxisV = instanceAxisV?.toVector3() ?: axisV.toVector3()
            val instAxisW = instanceAxisW?.toVector3() ?: axisW.toVector3()
            val prototype = GroupScene.ObjectPrototype(
                id = java.util.UUID.randomUUID().toString(),
                name = name.ifBlank { "Object" },
                definitionOrigin = defOrigin,
                definitionAxisU = defAxisU,
                definitionAxisV = defAxisV,
                definitionAxisW = defAxisW,
                gluedToSurface = gluedToSurface,
                voxelColor = Color(defaultColor),
                voxelStore = null,
                architectureStore = null,
                lineStore = DraftLineStore(),
                faceStore = DraftFaceStore(defaultColor),
                dimensionStore = DraftDimensionStore(),
                textStore = DraftTextStore()
            )
            return GroupScene.GroupNode(
                id = id.ifBlank { java.util.UUID.randomUUID().toString() },
                prototype = prototype,
                instanceOrigin = instOrigin,
                instanceAxisU = instAxisU,
                instanceAxisV = instAxisV,
                instanceAxisW = instAxisW
            )
        }

        fun toGroup(defaultColor: Color): GroupScene.GroupNode {
            val group = createGroupShell(defaultColor)
            segments.forEach { segment ->
                group.lineStore.addSegment(
                    segment.start.toVector3(),
                    segment.end.toVector3(),
                    autoCleanup = false,
                    id = segment.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
            faces.forEach { face ->
                group.faceStore.addTriangle(
                    face.a.toVector3(),
                    face.b.toVector3(),
                    face.c.toVector3(),
                    face.color.toColor(),
                    id = face.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
            children.forEach { child ->
                val childGroup = child.toGroup(defaultColor)
                childGroup.parent = group
                group.children.add(childGroup)
            }
            return group
        }

        companion object {
            fun fromGroup(group: GroupScene.GroupNode): GroupDto {
                val dto = GroupDto()
                dto.id = group.id
                dto.name = group.prototype.name
                dto.origin = Vec3Dto(group.instanceOrigin)
                dto.axisU = Vec3Dto(group.instanceAxisU)
                dto.axisV = Vec3Dto(group.instanceAxisV)
                dto.axisW = Vec3Dto(group.instanceAxisW)
                dto.definitionOrigin = Vec3Dto(group.prototype.definitionOrigin)
                dto.definitionAxisU = Vec3Dto(group.prototype.definitionAxisU)
                dto.definitionAxisV = Vec3Dto(group.prototype.definitionAxisV)
                dto.definitionAxisW = Vec3Dto(group.prototype.definitionAxisW)
                dto.instanceOrigin = Vec3Dto(group.instanceOrigin)
                dto.instanceAxisU = Vec3Dto(group.instanceAxisU)
                dto.instanceAxisV = Vec3Dto(group.instanceAxisV)
                dto.instanceAxisW = Vec3Dto(group.instanceAxisW)
                dto.gluedToSurface = group.prototype.gluedToSurface
                dto.segments = group.lineStore.getSegments().map { seg ->
                    SegmentDto(Vec3Dto(seg.start), Vec3Dto(seg.end), seg.id)
                }.toMutableList()
                dto.faces = group.faceStore.getTriangles().map { tri ->
                    val color = group.faceStore.colorFor(tri)
                    FaceDto(Vec3Dto(tri.a), Vec3Dto(tri.b), Vec3Dto(tri.c), ColorDto(color), tri.id)
                }.toMutableList()
                dto.children = group.children.map { child -> fromGroup(child) }.toMutableList()
                return dto
            }
        }
    }

    class Vec3Dto() {
        var x: Float = 0f
        var y: Float = 0f
        var z: Float = 0f

        constructor(vec: com.badlogic.gdx.math.Vector3) : this() {
            x = vec.x
            y = vec.y
            z = vec.z
        }

        fun toVector3(): com.badlogic.gdx.math.Vector3 {
            return com.badlogic.gdx.math.Vector3(x, y, z)
        }
    }

    class ColorDto() {
        var r: Float = 1f
        var g: Float = 1f
        var b: Float = 1f
        var a: Float = 1f

        constructor(color: Color) : this() {
            r = color.r
            g = color.g
            b = color.b
            a = color.a
        }

        fun toColor(): Color {
            return Color(r, g, b, a)
        }
    }

    class CameraDto() {
        var position: Vec3Dto? = null
        var direction: Vec3Dto? = null
        var up: Vec3Dto? = null
        var target: Vec3Dto? = null
        var near: Float? = null
        var far: Float? = null
        var fieldOfView: Float? = null

        constructor(camera: com.badlogic.gdx.graphics.PerspectiveCamera, cameraTarget: Vector3) : this() {
            position = Vec3Dto(camera.position)
            direction = Vec3Dto(camera.direction)
            up = Vec3Dto(camera.up)
            target = Vec3Dto(cameraTarget)
            near = camera.near
            far = camera.far
            fieldOfView = camera.fieldOfView
        }

        fun applyTo(camera: com.badlogic.gdx.graphics.PerspectiveCamera, cameraTarget: Vector3? = null) {
            val resolvedPosition = position?.toVector3() ?: Vector3(10f, 10f, 10f)
            val resolvedTarget = target?.toVector3() ?: Vector3(0f, 0f, 0f)
            camera.position.set(resolvedPosition)
            camera.up.set(0f, 1f, 0f)
            camera.direction.set(resolvedTarget).sub(resolvedPosition)
            if (camera.direction.len2() <= 1e-6f) {
                camera.direction.set(0f, -1f, 0f)
            } else {
                camera.direction.nor()
            }
            cameraTarget?.set(resolvedTarget)
            near?.let { camera.near = it }
            far?.let { camera.far = it }
            fieldOfView?.let { camera.fieldOfView = it }
            camera.update()
        }

        fun hasNulls(): Boolean {
            return position == null || direction == null || up == null || target == null ||
                near == null || far == null || fieldOfView == null
        }
    }

    class LightingDto() {
        var shadowLightValue: Float? = null
        var shadowLightAlpha: Float? = null
        var directionalLightValue: Float? = null
        var directionalLightAlpha: Float? = null
        var ambientLightValue: Float? = null
        var ambientLightAlpha: Float? = null
        var specularLightValue: Float? = null
        var specularLightAlpha: Float? = null

        constructor(settings: com.github.alfu32.sketch.ui.LightingSettings) : this() {
            shadowLightValue = settings.shadowLightValue
            shadowLightAlpha = settings.shadowLightAlpha
            directionalLightValue = settings.directionalLightValue
            directionalLightAlpha = settings.directionalLightAlpha
            ambientLightValue = settings.ambientLightValue
            ambientLightAlpha = settings.ambientLightAlpha
            specularLightValue = settings.specularLightValue
            specularLightAlpha = settings.specularLightAlpha
        }

        fun applyTo(settings: com.github.alfu32.sketch.ui.LightingSettings) {
            shadowLightValue?.let { settings.shadowLightValue = it }
            shadowLightAlpha?.let { settings.shadowLightAlpha = it }
            directionalLightValue?.let { settings.directionalLightValue = it }
            directionalLightAlpha?.let { settings.directionalLightAlpha = it }
            ambientLightValue?.let { settings.ambientLightValue = it }
            ambientLightAlpha?.let { settings.ambientLightAlpha = it }
            specularLightValue?.let { settings.specularLightValue = it }
            specularLightAlpha?.let { settings.specularLightAlpha = it }
        }

        fun hasNulls(): Boolean {
            return shadowLightValue == null || shadowLightAlpha == null ||
                directionalLightValue == null || directionalLightAlpha == null ||
                ambientLightValue == null || ambientLightAlpha == null ||
                specularLightValue == null || specularLightAlpha == null
        }
    }

    class ShadowDto() {
        var shadowBias: Float? = null
        var shadowNormalBias: Float? = null
        var pcfMode: Int? = null
        var dither: Boolean? = null
        var useCsm: Boolean? = null

        constructor(settings: com.github.alfu32.sketch.ui.ShadowSettings) : this() {
            shadowBias = settings.shadowBias
            shadowNormalBias = settings.shadowNormalBias
            pcfMode = settings.pcfMode
            dither = settings.dither
            useCsm = settings.useCsm
        }

        fun applyTo(settings: com.github.alfu32.sketch.ui.ShadowSettings) {
            shadowBias?.let { settings.shadowBias = it }
            shadowNormalBias?.let { settings.shadowNormalBias = it }
            pcfMode?.let { settings.pcfMode = it }
            dither?.let { settings.dither = it }
            useCsm?.let { settings.useCsm = it }
        }

        fun hasNulls(): Boolean {
            return shadowBias == null || shadowNormalBias == null ||
                pcfMode == null || dither == null || useCsm == null
        }
    }

    private fun resetScene(scene: GroupScene) {
        scene.resetScene()
    }

    private fun estimateTotalWork(snapshot: ModelSnapshot): Int {
        fun prototypeWork(dto: ObjectPrototypeDto): Int {
            var work = 1 + dto.segments.size + dto.faces.size + dto.dimensions.size + dto.texts.size + 1
            work += dto.voxels.size
            work += dto.architectureWalls.size + dto.architectureSlabs.size + dto.architectureStairs.size + dto.architectureFrames.size
            work += dto.hvacPlumbingRuns.size + dto.hvacVentilationDucts.size
            work += dto.hotspots.size + dto.prototypeVertices.size
            return work
        }
        fun instanceWork(dto: GroupInstanceDto): Int {
            var work = 1 + dto.overrideSegments.size + dto.overrideFaces.size + dto.overrideDimensions.size + dto.overrideTexts.size
            dto.children.forEach { child -> work += instanceWork(child) }
            return work
        }
        fun legacyWork(dto: GroupDto): Int {
            var work = 1 + dto.segments.size + dto.faces.size
            dto.children.forEach { child -> work += legacyWork(child) }
            return work
        }
        var total = 3
        if (snapshot.rootInstance != null && snapshot.prototypes.isNotEmpty()) {
            snapshot.prototypes.forEach { dto -> total += prototypeWork(dto) }
            total += instanceWork(snapshot.rootInstance!!)
        } else if (snapshot.prototypes.isNotEmpty()) {
            snapshot.prototypes.forEach { dto -> total += prototypeWork(dto) }
            total += 2
        } else if (snapshot.rootGroup != null) {
            total += legacyWork(snapshot.rootGroup!!)
        } else {
            total += 1 + snapshot.segments.size + snapshot.faces.size
        }
        return total
    }

    private fun registerLegacyPrototypes(scene: GroupScene, group: GroupScene.GroupNode) {
        scene.registerPrototypeForLoad(group.prototype)
        group.children.forEach { child -> registerLegacyPrototypes(scene, child) }
    }

    private data class FaceDef(val indices: IntArray, val dx: Int, val dy: Int, val dz: Int)

    private fun rebuildVoxelGeometry(prototype: GroupScene.ObjectPrototype) {
        if (prototype.kind != GroupScene.PrototypeKind.VOXEL) {
            return
        }
        val voxels = prototype.voxelStore?.all().orEmpty()
        prototype.lineStore.withChangeSuppressed {
            prototype.lineStore.clearAll()
        }
        prototype.faceStore.withChangeSuppressed {
            prototype.faceStore.clearAll()
        }
        if (voxels.isEmpty()) {
            prototype.lineStore.notifyExternalChange()
            prototype.faceStore.notifyExternalChange()
            return
        }
        val occupied = voxels.associateBy { VoxelStore.Key(it.x, it.y, it.z) }
        val corners = arrayOf(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(1f, 1f, 0f),
            Vector3(0f, 1f, 0f),
            Vector3(0f, 0f, 1f),
            Vector3(1f, 0f, 1f),
            Vector3(1f, 1f, 1f),
            Vector3(0f, 1f, 1f)
        )
        val faces = arrayOf(
            FaceDef(intArrayOf(0, 3, 7, 4), -1, 0, 0),
            FaceDef(intArrayOf(1, 5, 6, 2), 1, 0, 0),
            FaceDef(intArrayOf(0, 4, 5, 1), 0, -1, 0),
            FaceDef(intArrayOf(3, 2, 6, 7), 0, 1, 0),
            FaceDef(intArrayOf(0, 1, 2, 3), 0, 0, -1),
            FaceDef(intArrayOf(4, 7, 6, 5), 0, 0, 1)
        )
        prototype.faceStore.withChangeSuppressed {
            prototype.lineStore.withChangeSuppressed {
                voxels.forEach { voxel ->
                    val base = Vector3(voxel.x.toFloat(), voxel.y.toFloat(), voxel.z.toFloat())
                    faces.forEach { def ->
                        val neighbor = VoxelStore.Key(voxel.x + def.dx, voxel.y + def.dy, voxel.z + def.dz)
                        if (occupied.containsKey(neighbor)) {
                            return@forEach
                        }
                        val a = Vector3(corners[def.indices[0]]).add(base)
                        val b = Vector3(corners[def.indices[1]]).add(base)
                        val c = Vector3(corners[def.indices[2]]).add(base)
                        val d = Vector3(corners[def.indices[3]]).add(base)
                        // Invert winding so cube face normals point outward.
                        prototype.faceStore.addTriangle(a, c, b, voxel.color)
                        prototype.faceStore.addTriangle(a, d, c, voxel.color)
                        prototype.lineStore.addSegment(a, b, autoCleanup = false)
                        prototype.lineStore.addSegment(b, c, autoCleanup = false)
                        prototype.lineStore.addSegment(c, d, autoCleanup = false)
                        prototype.lineStore.addSegment(d, a, autoCleanup = false)
                    }
                }
            }
        }
        prototype.lineStore.cleanupJts()
        prototype.lineStore.notifyExternalChange()
        prototype.faceStore.notifyExternalChange()
    }

    fun encodeSnapshot(snapshot: ModelSnapshot): String {
        val json = Json().apply {
            setOutputType(JsonWriter.OutputType.json)
        }
        val text = json.toJson(snapshot)
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    fun decodeSnapshot(encoded: String): ModelSnapshot? {
        return try {
            val decoded = Base64.getDecoder().decode(encoded)
            val text = GZIPInputStream(ByteArrayInputStream(decoded)).bufferedReader().readText()
            Json().fromJson(ModelSnapshot::class.java, text)
        } catch (_: Exception) {
            null
        }
    }
}

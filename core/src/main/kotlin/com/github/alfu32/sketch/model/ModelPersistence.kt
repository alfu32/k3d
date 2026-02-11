package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object ModelPersistence {
    private const val VERSION = 11

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
        undoHistory: UndoHistoryDto? = null
    ) {
        val snapshot = snapshot(scene, camera, cameraTarget, lighting, shadow, modelUnit, snapEpsilon, gridSpacing, undoHistory)
        val json = Json().apply {
            setOutputType(JsonWriter.OutputType.json)
        }
        val text = json.prettyPrint(snapshot)
        file.parentFile?.mkdirs()
        file.writeText(text)
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
        undoHistory: UndoHistoryDto? = null
    ): ModelSnapshot {
        return ModelSnapshot().apply {
            version = VERSION
            prototypes = scene.allPrototypes().map { ObjectPrototypeDto.fromPrototype(it) }.toMutableList()
            rootInstance = GroupInstanceDto.fromInstance(scene.root)
            cameraState = CameraDto(camera, cameraTarget)
            lightingState = LightingDto(lighting)
            shadowState = ShadowDto(shadow)
            this.modelUnit = ModelUnitDto(modelUnit)
            this.snapEpsilon = snapEpsilon
            this.gridSpacing = gridSpacing
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
        gridSpacingSetter: (Float) -> Unit
    ): LoadResult {
        if (!file.exists() || file.length() == 0L) {
            return LoadResult(false, false)
        }
        val json = Json()
        val snapshot = try {
            json.fromJson(ModelSnapshot::class.java, file.readText())
        } catch (ex: Exception) {
            return LoadResult(false, false)
        } ?: return LoadResult(false, false)

        applySnapshot(snapshot, scene, camera, cameraTarget, lighting, shadow, modelUnit, snapEpsilonSetter, gridSpacingSetter)
        val needsResave = snapshot.cameraState == null ||
            snapshot.lightingState == null ||
            snapshot.shadowState == null ||
            (snapshot.rootInstance == null && snapshot.rootGroup == null) ||
            (snapshot.rootInstance != null && snapshot.prototypes.isEmpty()) ||
            snapshot.cameraState?.hasNulls() == true ||
            snapshot.lightingState?.hasNulls() == true ||
            snapshot.shadowState?.hasNulls() == true ||
            snapshot.modelUnit == null ||
            snapshot.snapEpsilon == null ||
            snapshot.gridSpacing == null
        return LoadResult(true, needsResave, snapshot)
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
        gridSpacingSetter: ((Float) -> Unit)? = null
    ) {
        resetScene(scene)
        if (snapshot.rootInstance != null && snapshot.prototypes.isNotEmpty()) {
            val prototypeMap = mutableMapOf<String, GroupScene.ObjectPrototype>()
            val rootPrototype = scene.rootPrototype()
            snapshot.prototypes.forEach { dto ->
                if (dto.id == rootPrototype.id) {
                    dto.applyTo(rootPrototype, scene.defaultFaceColor)
                    prototypeMap[rootPrototype.id] = rootPrototype
                } else {
                    val prototype = dto.toPrototype(scene.defaultFaceColor)
                    scene.registerPrototypeForLoad(prototype)
                    prototypeMap[prototype.id] = prototype
                }
            }
            val loadedRoot = snapshot.rootInstance!!.toInstance(prototypeMap, scene.defaultFaceColor)
            scene.root.instanceOrigin.set(loadedRoot.instanceOrigin)
            scene.root.instanceAxisU.set(loadedRoot.instanceAxisU)
            scene.root.instanceAxisV.set(loadedRoot.instanceAxisV)
            scene.root.instanceAxisW.set(loadedRoot.instanceAxisW)
            scene.root.children.clear()
            loadedRoot.children.forEach { child ->
                child.parent = scene.root
                scene.root.children.add(child)
                scene.registerInstanceTree(child)
            }
        } else if (snapshot.rootGroup != null) {
            val loaded = snapshot.rootGroup!!.toGroup(scene.defaultFaceColor)
            loaded.children.forEach { child -> registerLegacyPrototypes(scene, child) }
            scene.root.instanceOrigin.set(loaded.instanceOrigin)
            scene.root.instanceAxisU.set(loaded.instanceAxisU)
            scene.root.instanceAxisV.set(loaded.instanceAxisV)
            scene.root.instanceAxisW.set(loaded.instanceAxisW)
            scene.root.prototype.name = loaded.prototype.name
            scene.root.prototype.definitionOrigin.set(loaded.prototype.definitionOrigin)
            scene.root.prototype.definitionAxisU.set(loaded.prototype.definitionAxisU)
            scene.root.prototype.definitionAxisV.set(loaded.prototype.definitionAxisV)
            scene.root.prototype.definitionAxisW.set(loaded.prototype.definitionAxisW)
            scene.root.prototype.gluedToSurface = loaded.prototype.gluedToSurface
            scene.root.lineStore.clearAll()
            scene.root.faceStore.clearAll()
            loaded.lineStore.getSegments().forEach { seg ->
                scene.root.lineStore.addSegment(seg.start, seg.end, autoCleanup = false)
            }
            loaded.faceStore.getTriangles().forEach { tri ->
                scene.root.faceStore.addTriangle(tri.a, tri.b, tri.c, loaded.faceStore.colorFor(tri))
            }
            scene.root.children.clear()
            loaded.children.forEach { child ->
                child.parent = scene.root
                scene.root.children.add(child)
                scene.registerInstanceTree(child)
            }
        } else {
            snapshot.segments.forEach { segment ->
                scene.root.lineStore.addSegment(segment.start.toVector3(), segment.end.toVector3(), autoCleanup = false)
            }
            snapshot.faces.forEach { face ->
                scene.root.faceStore.addTriangle(
                    face.a.toVector3(),
                    face.b.toVector3(),
                    face.c.toVector3(),
                    face.color.toColor()
                )
            }
        }
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
        snapshot.snapEpsilon?.let { value ->
            snapEpsilonSetter?.invoke(value)
        }
        snapshot.gridSpacing?.let { value ->
            gridSpacingSetter?.invoke(value)
        }
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
        var voxelColor: ColorDto? = null
        var voxels: MutableList<VoxelDto> = mutableListOf()
        var architectureWalls: MutableList<ArchitectureWallDto> = mutableListOf()
        var architectureSlabs: MutableList<ArchitectureSlabDto> = mutableListOf()
        var architectureStairs: MutableList<ArchitectureStairDto> = mutableListOf()
        var architectureFrames: MutableList<ArchitectureFrameDto> = mutableListOf()
        var segments: MutableList<SegmentDto> = mutableListOf()
        var faces: MutableList<FaceDto> = mutableListOf()
        var dimensions: MutableList<DimensionDto> = mutableListOf()
        var texts: MutableList<TextDto> = mutableListOf()

        fun toPrototype(defaultColor: Color): GroupScene.ObjectPrototype {
            val prototypeKind = try {
                GroupScene.PrototypeKind.valueOf(kind)
            } catch (_: IllegalArgumentException) {
                GroupScene.PrototypeKind.MESH
            }
            val voxelStore = if (prototypeKind == GroupScene.PrototypeKind.VOXEL) VoxelStore() else null
            val architectureStore = if (prototypeKind == GroupScene.PrototypeKind.ARCHITECTURE) ArchitectureStore() else null
            val prototype = GroupScene.ObjectPrototype(
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
                lineStore = DraftLineStore(),
                faceStore = DraftFaceStore(defaultColor),
                dimensionStore = DraftDimensionStore(),
                textStore = DraftTextStore()
            )
            restoreArchitecture(architectureStore)
            applyGeometry(prototype)
            return prototype
        }

        fun applyTo(prototype: GroupScene.ObjectPrototype, defaultColor: Color) {
            prototype.name = name.ifBlank { prototype.name }
            prototype.definitionOrigin.set(definitionOrigin.toVector3())
            prototype.definitionAxisU.set(definitionAxisU.toVector3())
            prototype.definitionAxisV.set(definitionAxisV.toVector3())
            prototype.definitionAxisW.set(definitionAxisW.toVector3())
            prototype.gluedToSurface = gluedToSurface
            val parsedKind = try {
                GroupScene.PrototypeKind.valueOf(kind)
            } catch (_: IllegalArgumentException) {
                GroupScene.PrototypeKind.MESH
            }
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
            prototype.lineStore.clearAll()
            prototype.faceStore.clearAll()
            prototype.dimensionStore.clearAll()
            prototype.textStore.clearAll()
            applyGeometry(prototype, defaultColor)
        }

        private fun applyGeometry(prototype: GroupScene.ObjectPrototype, defaultColor: Color? = null) {
            segments.forEach { segment ->
                prototype.lineStore.addSegment(segment.start.toVector3(), segment.end.toVector3(), autoCleanup = false)
            }
            faces.forEach { face ->
                prototype.faceStore.addTriangle(
                    face.a.toVector3(),
                    face.b.toVector3(),
                    face.c.toVector3(),
                    face.color.toColor()
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
                    text.screenText
                )
            }
            if (prototype.kind == GroupScene.PrototypeKind.VOXEL) {
                prototype.voxelStore?.clear()
                voxels.forEach { voxel ->
                    prototype.voxelStore?.set(voxel.x, voxel.y, voxel.z, voxel.color.toColor())
                }
                rebuildVoxelGeometry(prototype)
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
                        id = hole.id.ifBlank { java.util.UUID.randomUUID().toString() }
                    )
                }
            }
            architectureSlabs.forEach { slab ->
                architecture.addSlab(
                    minCorner = slab.min.toVector3(),
                    maxCorner = slab.max.toVector3(),
                    thickness = slab.thickness,
                    topColor = slab.topColor.toColor(),
                    bottomColor = slab.bottomColor.toColor(),
                    sideColor = slab.sideColor.toColor(),
                    id = slab.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
            architectureStairs.forEach { stair ->
                architecture.addStair(
                    minCorner = stair.min.toVector3(),
                    maxCorner = stair.max.toVector3(),
                    contourPoints = stair.contour.map { it.toVector3() },
                    walkingStart = stair.walkingStart.toVector3(),
                    walkingEnd = stair.walkingEnd.toVector3(),
                    height = stair.height,
                    stepCount = stair.stepCount,
                    supportThickness = stair.supportThickness,
                    treadColor = stair.treadColor.toColor(),
                    supportColor = stair.supportColor.toColor(),
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
                    normal = frame.normal.toVector3(),
                    depth = frame.depth,
                    frameWidth = frame.frameWidth,
                    kind = kind,
                    color = frame.color.toColor(),
                    id = frame.id.ifBlank { java.util.UUID.randomUUID().toString() }
                )
            }
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
                dto.voxelColor = ColorDto(prototype.voxelColor)
                dto.voxels = prototype.voxelStore?.all()?.map { voxel ->
                    VoxelDto(voxel.x, voxel.y, voxel.z, ColorDto(voxel.color))
                }?.toMutableList() ?: mutableListOf()
                dto.architectureWalls = prototype.architectureStore?.allWalls()?.map { wall ->
                    ArchitectureWallDto(
                        id = wall.id,
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
                        min = Vec3Dto(slab.min),
                        max = Vec3Dto(slab.max),
                        thickness = slab.thickness,
                        topColor = ColorDto(slab.topColor),
                        bottomColor = ColorDto(slab.bottomColor),
                        sideColor = ColorDto(slab.sideColor)
                    )
                }?.toMutableList() ?: mutableListOf()
                dto.architectureStairs = prototype.architectureStore?.allStairs()?.map { stair ->
                    ArchitectureStairDto(
                        id = stair.id,
                        min = Vec3Dto(stair.min),
                        max = Vec3Dto(stair.max),
                        contour = stair.contour.map { Vec3Dto(it) }.toMutableList(),
                        walkingStart = Vec3Dto(stair.walkingStart),
                        walkingEnd = Vec3Dto(stair.walkingEnd),
                        height = stair.height,
                        stepCount = stair.stepCount,
                        supportThickness = stair.supportThickness,
                        treadColor = ColorDto(stair.treadColor),
                        supportColor = ColorDto(stair.supportColor)
                    )
                }?.toMutableList() ?: mutableListOf()
                dto.architectureFrames = prototype.architectureStore?.allFrames()?.map { frame ->
                    ArchitectureFrameDto(
                        id = frame.id,
                        cornerA = Vec3Dto(frame.cornerA),
                        cornerB = Vec3Dto(frame.cornerB),
                        normal = Vec3Dto(frame.normal),
                        depth = frame.depth,
                        frameWidth = frame.frameWidth,
                        kind = frame.kind.name,
                        color = ColorDto(frame.color)
                    )
                }?.toMutableList() ?: mutableListOf()
                dto.segments = prototype.lineStore.getSegments().map { seg ->
                    SegmentDto(Vec3Dto(seg.start), Vec3Dto(seg.end))
                }.toMutableList()
                dto.faces = prototype.faceStore.getTriangles().map { tri ->
                    val color = prototype.faceStore.colorFor(tri)
                    FaceDto(Vec3Dto(tri.a), Vec3Dto(tri.b), Vec3Dto(tri.c), ColorDto(color))
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
                        text.screenText
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
        var u0: Float = 0f
        var u1: Float = 0f
        var v0: Float = 0f
        var v1: Float = 0f

        constructor(id: String, u0: Float, u1: Float, v0: Float, v1: Float) : this() {
            this.id = id
            this.u0 = u0
            this.u1 = u1
            this.v0 = v0
            this.v1 = v1
        }
    }

    class ArchitectureWallDto() {
        var id: String = ""
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
        var min: Vec3Dto = Vec3Dto()
        var max: Vec3Dto = Vec3Dto()
        var thickness: Float = 0.2f
        var topColor: ColorDto = ColorDto(Color(0.93f, 0.93f, 0.93f, 1f))
        var bottomColor: ColorDto = ColorDto(Color(0.84f, 0.84f, 0.84f, 1f))
        var sideColor: ColorDto = ColorDto(Color(0.88f, 0.88f, 0.88f, 1f))

        constructor(
            id: String,
            min: Vec3Dto,
            max: Vec3Dto,
            thickness: Float,
            topColor: ColorDto,
            bottomColor: ColorDto,
            sideColor: ColorDto
        ) : this() {
            this.id = id
            this.min = min
            this.max = max
            this.thickness = thickness
            this.topColor = topColor
            this.bottomColor = bottomColor
            this.sideColor = sideColor
        }
    }

    class ArchitectureStairDto() {
        var id: String = ""
        var min: Vec3Dto = Vec3Dto()
        var max: Vec3Dto = Vec3Dto()
        var contour: MutableList<Vec3Dto> = mutableListOf()
        var walkingStart: Vec3Dto = Vec3Dto()
        var walkingEnd: Vec3Dto = Vec3Dto()
        var height: Float = 2.7f
        var stepCount: Int = 14
        var supportThickness: Float = 0.2f
        var treadColor: ColorDto = ColorDto(Color(0.93f, 0.93f, 0.93f, 1f))
        var supportColor: ColorDto = ColorDto(Color(0.82f, 0.82f, 0.82f, 1f))

        constructor(
            id: String,
            min: Vec3Dto,
            max: Vec3Dto,
            contour: MutableList<Vec3Dto>,
            walkingStart: Vec3Dto,
            walkingEnd: Vec3Dto,
            height: Float,
            stepCount: Int,
            supportThickness: Float,
            treadColor: ColorDto,
            supportColor: ColorDto
        ) : this() {
            this.id = id
            this.min = min
            this.max = max
            this.contour = contour
            this.walkingStart = walkingStart
            this.walkingEnd = walkingEnd
            this.height = height
            this.stepCount = stepCount
            this.supportThickness = supportThickness
            this.treadColor = treadColor
            this.supportColor = supportColor
        }
    }

    class ArchitectureFrameDto() {
        var id: String = ""
        var cornerA: Vec3Dto = Vec3Dto()
        var cornerB: Vec3Dto = Vec3Dto()
        var normal: Vec3Dto = Vec3Dto(Vector3(0f, 1f, 0f))
        var depth: Float = 0.12f
        var frameWidth: Float = 0.06f
        var kind: String = ArchitectureStore.FrameKind.WINDOW.name
        var color: ColorDto = ColorDto(Color(0.90f, 0.90f, 0.90f, 1f))

        constructor(
            id: String,
            cornerA: Vec3Dto,
            cornerB: Vec3Dto,
            normal: Vec3Dto,
            depth: Float,
            frameWidth: Float,
            kind: String,
            color: ColorDto
        ) : this() {
            this.id = id
            this.cornerA = cornerA
            this.cornerB = cornerB
            this.normal = normal
            this.depth = depth
            this.frameWidth = frameWidth
            this.kind = kind
            this.color = color
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

        constructor(
            position: Vec3Dto,
            text: String,
            size: Float = 0.1f,
            normal: Vec3Dto = Vec3Dto(Vector3(0f, 1f, 0f)),
            axisU: Vec3Dto = Vec3Dto(Vector3(1f, 0f, 0f)),
            screenText: Boolean = true
        ) : this() {
            this.position = position
            this.text = text
            this.size = size
            this.normal = normal
            this.axisU = axisU
            this.screenText = screenText
        }
    }

    class GroupInstanceDto() {
        var id: String = ""
        var prototypeId: String = ""
        var instanceOrigin: Vec3Dto = Vec3Dto()
        var instanceAxisU: Vec3Dto = Vec3Dto()
        var instanceAxisV: Vec3Dto = Vec3Dto()
        var instanceAxisW: Vec3Dto = Vec3Dto()
        var children: MutableList<GroupInstanceDto> = mutableListOf()

        fun toInstance(
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
            val group = GroupScene.GroupNode(
                id = id.ifBlank { java.util.UUID.randomUUID().toString() },
                prototype = prototype,
                instanceOrigin = instanceOrigin.toVector3(),
                instanceAxisU = instanceAxisU.toVector3(),
                instanceAxisV = instanceAxisV.toVector3(),
                instanceAxisW = instanceAxisW.toVector3()
            )
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
                dto.children = group.children.map { child -> fromInstance(child) }.toMutableList()
                return dto
            }
        }
    }

    class SegmentDto() {
        var start: Vec3Dto = Vec3Dto()
        var end: Vec3Dto = Vec3Dto()

        constructor(start: Vec3Dto, end: Vec3Dto) : this() {
            this.start = start
            this.end = end
        }
    }

    class FaceDto() {
        var a: Vec3Dto = Vec3Dto()
        var b: Vec3Dto = Vec3Dto()
        var c: Vec3Dto = Vec3Dto()
        var color: ColorDto = ColorDto()

        constructor(a: Vec3Dto, b: Vec3Dto, c: Vec3Dto, color: ColorDto) : this() {
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

        fun toGroup(defaultColor: Color): GroupScene.GroupNode {
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
            val group = GroupScene.GroupNode(
                id = id.ifBlank { java.util.UUID.randomUUID().toString() },
                prototype = prototype,
                instanceOrigin = instOrigin,
                instanceAxisU = instAxisU,
                instanceAxisV = instAxisV,
                instanceAxisW = instAxisW
            )
            segments.forEach { segment ->
                group.lineStore.addSegment(segment.start.toVector3(), segment.end.toVector3(), autoCleanup = false)
            }
            faces.forEach { face ->
                group.faceStore.addTriangle(
                    face.a.toVector3(),
                    face.b.toVector3(),
                    face.c.toVector3(),
                    face.color.toColor()
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
                    SegmentDto(Vec3Dto(seg.start), Vec3Dto(seg.end))
                }.toMutableList()
                dto.faces = group.faceStore.getTriangles().map { tri ->
                    val color = group.faceStore.colorFor(tri)
                    FaceDto(Vec3Dto(tri.a), Vec3Dto(tri.b), Vec3Dto(tri.c), ColorDto(color))
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

package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import java.io.File

object ModelPersistence {
    private const val VERSION = 7

    fun save(
        file: File,
        scene: GroupScene,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        cameraTarget: Vector3,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings,
        modelUnit: ModelUnit,
        snapEpsilon: Float
    ) {
        val snapshot = snapshot(scene, camera, cameraTarget, lighting, shadow, modelUnit, snapEpsilon)
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
        snapEpsilon: Float
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
        }
    }

    data class LoadResult(val ok: Boolean, val needsResave: Boolean)

    fun load(
        file: File,
        scene: GroupScene,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        cameraTarget: Vector3,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings,
        modelUnit: ModelUnit,
        snapEpsilonSetter: (Float) -> Unit
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

        applySnapshot(snapshot, scene, camera, cameraTarget, lighting, shadow, modelUnit, snapEpsilonSetter)
        val needsResave = snapshot.cameraState == null ||
            snapshot.lightingState == null ||
            snapshot.shadowState == null ||
            (snapshot.rootInstance == null && snapshot.rootGroup == null) ||
            (snapshot.rootInstance != null && snapshot.prototypes.isEmpty()) ||
            snapshot.cameraState?.hasNulls() == true ||
            snapshot.lightingState?.hasNulls() == true ||
            snapshot.shadowState?.hasNulls() == true ||
            snapshot.modelUnit == null ||
            snapshot.snapEpsilon == null
        return LoadResult(true, needsResave)
    }

    fun applySnapshot(
        snapshot: ModelSnapshot,
        scene: GroupScene,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        cameraTarget: Vector3? = null,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings,
        modelUnit: ModelUnit? = null,
        snapEpsilonSetter: ((Float) -> Unit)? = null
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
                scene.root.lineStore.addSegment(seg.start, seg.end)
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
                scene.root.lineStore.addSegment(segment.start.toVector3(), segment.end.toVector3())
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
        var segments: MutableList<SegmentDto> = mutableListOf()
        var faces: MutableList<FaceDto> = mutableListOf()
        var dimensions: MutableList<DimensionDto> = mutableListOf()
        var texts: MutableList<TextDto> = mutableListOf()

        fun toPrototype(defaultColor: Color): GroupScene.ObjectPrototype {
            val prototype = GroupScene.ObjectPrototype(
                id = id.ifBlank { java.util.UUID.randomUUID().toString() },
                name = name.ifBlank { "Object" },
                definitionOrigin = definitionOrigin.toVector3(),
                definitionAxisU = definitionAxisU.toVector3(),
                definitionAxisV = definitionAxisV.toVector3(),
                definitionAxisW = definitionAxisW.toVector3(),
                gluedToSurface = gluedToSurface,
                lineStore = DraftLineStore(),
                faceStore = DraftFaceStore(defaultColor),
                dimensionStore = DraftDimensionStore(),
                textStore = DraftTextStore()
            )
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
            prototype.lineStore.clearAll()
            prototype.faceStore.clearAll()
            prototype.dimensionStore.clearAll()
            prototype.textStore.clearAll()
            applyGeometry(prototype, defaultColor)
        }

        private fun applyGeometry(prototype: GroupScene.ObjectPrototype, defaultColor: Color? = null) {
            segments.forEach { segment ->
                prototype.lineStore.addSegment(segment.start.toVector3(), segment.end.toVector3())
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
                    text.axisU.toVector3()
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
                    TextDto(Vec3Dto(text.position), text.text, text.size, Vec3Dto(text.normal), Vec3Dto(text.axisU))
                }.toMutableList()
                return dto
            }
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

        constructor(
            position: Vec3Dto,
            text: String,
            size: Float = 0.1f,
            normal: Vec3Dto = Vec3Dto(Vector3(0f, 1f, 0f)),
            axisU: Vec3Dto = Vec3Dto(Vector3(1f, 0f, 0f))
        ) : this() {
            this.position = position
            this.text = text
            this.size = size
            this.normal = normal
            this.axisU = axisU
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
                group.lineStore.addSegment(segment.start.toVector3(), segment.end.toVector3())
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
}

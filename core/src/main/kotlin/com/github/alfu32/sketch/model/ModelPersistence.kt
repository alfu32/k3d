package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import java.io.File

object ModelPersistence {
    private const val VERSION = 2

    fun save(
        file: File,
        lineStore: DraftLineStore,
        faceStore: DraftFaceStore,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings
    ) {
        val snapshot = ModelSnapshot().apply {
            version = VERSION
            segments = lineStore.getSegments().map { segment ->
                SegmentDto(Vec3Dto(segment.start), Vec3Dto(segment.end))
            }.toMutableList()
            faces = faceStore.getTriangles().map { tri ->
                val color = faceStore.colorFor(tri)
                FaceDto(Vec3Dto(tri.a), Vec3Dto(tri.b), Vec3Dto(tri.c), ColorDto(color))
            }.toMutableList()
            cameraState = CameraDto(camera)
            lightingState = LightingDto(lighting)
            shadowState = ShadowDto(shadow)
        }
        val json = Json().apply {
            setOutputType(JsonWriter.OutputType.json)
        }
        val text = json.prettyPrint(snapshot)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    data class LoadResult(val ok: Boolean, val needsResave: Boolean)

    fun load(
        file: File,
        lineStore: DraftLineStore,
        faceStore: DraftFaceStore,
        camera: com.badlogic.gdx.graphics.PerspectiveCamera,
        lighting: com.github.alfu32.sketch.ui.LightingSettings,
        shadow: com.github.alfu32.sketch.ui.ShadowSettings
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

        lineStore.clearAll()
        faceStore.clearAll()
        snapshot.segments.forEach { segment ->
            lineStore.addSegment(segment.start.toVector3(), segment.end.toVector3())
        }
        snapshot.faces.forEach { face ->
            faceStore.addTriangle(
                face.a.toVector3(),
                face.b.toVector3(),
            face.c.toVector3(),
            face.color.toColor()
        )
        }
        snapshot.cameraState?.applyTo(camera)
        snapshot.lightingState?.applyTo(lighting)
        snapshot.shadowState?.applyTo(shadow)
        val needsResave = snapshot.cameraState == null ||
            snapshot.lightingState == null ||
            snapshot.shadowState == null ||
            snapshot.cameraState?.hasNulls() == true ||
            snapshot.lightingState?.hasNulls() == true ||
            snapshot.shadowState?.hasNulls() == true
        return LoadResult(true, needsResave)
    }

    class ModelSnapshot {
        var version: Int = VERSION
        var segments: MutableList<SegmentDto> = mutableListOf()
        var faces: MutableList<FaceDto> = mutableListOf()
        var cameraState: CameraDto? = null
        var lightingState: LightingDto? = null
        var shadowState: ShadowDto? = null
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
        var near: Float? = null
        var far: Float? = null
        var fieldOfView: Float? = null

        constructor(camera: com.badlogic.gdx.graphics.PerspectiveCamera) : this() {
            position = Vec3Dto(camera.position)
            direction = Vec3Dto(camera.direction)
            up = Vec3Dto(camera.up)
            near = camera.near
            far = camera.far
            fieldOfView = camera.fieldOfView
        }

        fun applyTo(camera: com.badlogic.gdx.graphics.PerspectiveCamera) {
            position?.let { camera.position.set(it.toVector3()) }
            direction?.let { camera.direction.set(it.toVector3()) }
            up?.let { camera.up.set(it.toVector3()) }
            near?.let { camera.near = it }
            far?.let { camera.far = it }
            fieldOfView?.let { camera.fieldOfView = it }
            camera.update()
        }

        fun hasNulls(): Boolean {
            return position == null || direction == null || up == null ||
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
}

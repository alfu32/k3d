package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import java.io.File

object ModelPersistence {
    private const val VERSION = 1

    fun save(file: File, lineStore: DraftLineStore, faceStore: DraftFaceStore) {
        val snapshot = ModelSnapshot().apply {
            version = VERSION
            segments = lineStore.getSegments().map { segment ->
                SegmentDto(Vec3Dto(segment.start), Vec3Dto(segment.end))
            }.toMutableList()
            faces = faceStore.getTriangles().map { tri ->
                val color = faceStore.colorFor(tri)
                FaceDto(Vec3Dto(tri.a), Vec3Dto(tri.b), Vec3Dto(tri.c), ColorDto(color))
            }.toMutableList()
        }
        val json = Json().apply {
            setOutputType(JsonWriter.OutputType.json)
        }
        val text = json.prettyPrint(snapshot)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    fun load(file: File, lineStore: DraftLineStore, faceStore: DraftFaceStore): Boolean {
        if (!file.exists() || file.length() == 0L) {
            return false
        }
        val json = Json()
        val snapshot = try {
            json.fromJson(ModelSnapshot::class.java, file.readText())
        } catch (ex: Exception) {
            return false
        } ?: return false

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
        return true
    }

    class ModelSnapshot {
        var version: Int = VERSION
        var segments: MutableList<SegmentDto> = mutableListOf()
        var faces: MutableList<FaceDto> = mutableListOf()
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
}

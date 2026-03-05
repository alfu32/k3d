package com.github.alfu32.sketch.tools

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.JsonReader
import com.github.alfu32.sketch.model.ModelPersistence
import kotlin.math.max

class VectorGlyphCatalog(
    val source: String,
    private val glyphs: Map<Int, GlyphDefinition>
) {
    data class SegmentDef(val start: Vector3, val end: Vector3)
    data class FaceDef(val a: Vector3, val b: Vector3, val c: Vector3)
    data class GlyphDefinition(
        val code: Int,
        val segments: List<SegmentDef>,
        val faces: List<FaceDef>,
        val minX: Float,
        val maxX: Float,
        val minZ: Float,
        val maxZ: Float
    ) {
        val width: Float get() = max(0f, maxX - minX)
        val height: Float get() = max(0f, maxZ - minZ)
        val complexity: Int get() = segments.size + faces.size * 3
    }

    fun glyph(code: Int): GlyphDefinition? = glyphs[code]
    fun glyphOrSquare(code: Int): GlyphDefinition = glyph(code) ?: fallbackSquareGlyph()
    fun isEmpty(): Boolean = glyphs.isEmpty()
    fun size(): Int = glyphs.size

    companion object {
        private val glyphNameRegex = Regex("^GL_?0x([0-9A-Fa-f]{2})$")

        fun empty(source: String = ""): VectorGlyphCatalog = VectorGlyphCatalog(source, emptyMap())

        fun fromEncodedModel(text: String, source: String): VectorGlyphCatalog? {
            val snapshot = ModelPersistence.decodeSnapshot(text) ?: return null
            return fromSnapshot(snapshot, source)
        }

        fun fromSnapshot(snapshot: ModelPersistence.ModelSnapshot, source: String): VectorGlyphCatalog {
            val glyphsByCode = linkedMapOf<Int, GlyphDefinition>()
            snapshot.prototypes.forEach { prototype ->
                val match = glyphNameRegex.matchEntire(prototype.name.trim()) ?: return@forEach
                val code = match.groupValues[1].toIntOrNull(16) ?: return@forEach
                if (code !in 0..255) return@forEach
                val segments = prototype.segments.map { seg ->
                    SegmentDef(seg.start.toVector3(), seg.end.toVector3())
                }
                val faces = prototype.faces.map { face ->
                    FaceDef(face.a.toVector3(), face.b.toVector3(), face.c.toVector3())
                }
                if (segments.isEmpty() && faces.isEmpty()) {
                    return@forEach
                }
                val glyph = buildGlyphDefinition(code, segments, faces) ?: return@forEach
                val existing = glyphsByCode[code]
                if (existing == null || glyph.complexity > existing.complexity) {
                    glyphsByCode[code] = glyph
                }
            }
            return VectorGlyphCatalog(source, glyphsByCode)
        }

        fun fromCompactGlyphJson(text: String, source: String): VectorGlyphCatalog? {
            val root = try {
                JsonReader().parse(text)
            } catch (_: Throwable) {
                return null
            }
            if (!root.isArray) {
                return null
            }
            val glyphsByCode = linkedMapOf<Int, GlyphDefinition>()
            var cursor = root.child
            while (cursor != null) {
                val code = cursor.getInt("c", -1)
                if (code in 0..255) {
                    val segments = mutableListOf<SegmentDef>()
                    val faces = mutableListOf<FaceDef>()
                    var segNode = cursor.get("s")?.child
                    while (segNode != null) {
                        val a = segNode.get(0)
                        val b = segNode.get(1)
                        if (a != null && b != null && a.size >= 3 && b.size >= 3) {
                            segments += SegmentDef(
                                Vector3(a.getFloat(0), a.getFloat(1), a.getFloat(2)),
                                Vector3(b.getFloat(0), b.getFloat(1), b.getFloat(2))
                            )
                        }
                        segNode = segNode.next
                    }
                    var faceNode = cursor.get("f")?.child
                    while (faceNode != null) {
                        val a = faceNode.get(0)
                        val b = faceNode.get(1)
                        val c = faceNode.get(2)
                        if (a != null && b != null && c != null && a.size >= 3 && b.size >= 3 && c.size >= 3) {
                            faces += FaceDef(
                                Vector3(a.getFloat(0), a.getFloat(1), a.getFloat(2)),
                                Vector3(b.getFloat(0), b.getFloat(1), b.getFloat(2)),
                                Vector3(c.getFloat(0), c.getFloat(1), c.getFloat(2))
                            )
                        }
                        faceNode = faceNode.next
                    }
                    buildGlyphDefinition(code, segments, faces)?.let { glyph ->
                        val existing = glyphsByCode[code]
                        if (existing == null || glyph.complexity > existing.complexity) {
                            glyphsByCode[code] = glyph
                        }
                    }
                }
                cursor = cursor.next
            }
            return VectorGlyphCatalog(source, glyphsByCode)
        }

        fun fallbackSquareGlyph(): GlyphDefinition {
            val s = listOf(
                SegmentDef(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f)),
                SegmentDef(Vector3(1f, 0f, 0f), Vector3(1f, 0f, 1f)),
                SegmentDef(Vector3(1f, 0f, 1f), Vector3(0f, 0f, 1f)),
                SegmentDef(Vector3(0f, 0f, 1f), Vector3(0f, 0f, 0f))
            )
            val f = listOf(
                FaceDef(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f), Vector3(1f, 0f, 1f)),
                FaceDef(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 1f), Vector3(0f, 0f, 1f))
            )
            return buildGlyphDefinition(0x25A1, s, f)!!
        }

        private fun buildGlyphDefinition(
            code: Int,
            segments: List<SegmentDef>,
            faces: List<FaceDef>
        ): GlyphDefinition? {
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY
            var hasPoint = false

            fun include(point: Vector3) {
                hasPoint = true
                minX = minX.coerceAtMost(point.x)
                maxX = maxX.coerceAtLeast(point.x)
                minZ = minZ.coerceAtMost(point.z)
                maxZ = maxZ.coerceAtLeast(point.z)
            }

            segments.forEach {
                include(it.start)
                include(it.end)
            }
            faces.forEach {
                include(it.a)
                include(it.b)
                include(it.c)
            }
            if (!hasPoint) {
                return null
            }
            return GlyphDefinition(code, segments, faces, minX, maxX, minZ, maxZ)
        }
    }
}

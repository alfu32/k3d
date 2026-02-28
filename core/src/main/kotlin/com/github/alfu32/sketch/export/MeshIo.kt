package com.github.alfu32.sketch.export

import com.badlogic.gdx.math.Vector3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object MeshIo {
    data class Triangle(val a: Vector3, val b: Vector3, val c: Vector3)

    enum class ImportFormat {
        OBJ,
        STL_AUTO,
        STL_ASCII,
        STL_BINARY
    }

    enum class ExportFormat {
        OBJ,
        STL_ASCII,
        STL_BINARY
    }

    fun importFormatForExtension(ext: String): ImportFormat? {
        return when (ext.lowercase(Locale.US)) {
            "obj" -> ImportFormat.OBJ
            "stl" -> ImportFormat.STL_AUTO
            "stla" -> ImportFormat.STL_ASCII
            "stlb" -> ImportFormat.STL_BINARY
            else -> null
        }
    }

    fun exportFormatForExtension(ext: String): ExportFormat? {
        return when (ext.lowercase(Locale.US)) {
            "obj" -> ExportFormat.OBJ
            "stl", "stlb" -> ExportFormat.STL_BINARY
            "stla" -> ExportFormat.STL_ASCII
            else -> null
        }
    }

    fun importTriangles(bytes: ByteArray, format: ImportFormat): List<Triangle> {
        return when (format) {
            ImportFormat.OBJ -> parseObj(String(bytes, StandardCharsets.UTF_8))
            ImportFormat.STL_ASCII -> parseStlAscii(String(bytes, StandardCharsets.UTF_8))
            ImportFormat.STL_BINARY -> parseStlBinary(bytes)
            ImportFormat.STL_AUTO -> {
                if (looksLikeBinaryStl(bytes)) {
                    parseStlBinary(bytes)
                } else {
                    val ascii = parseStlAscii(String(bytes, StandardCharsets.UTF_8))
                    if (ascii.isNotEmpty()) ascii else parseStlBinary(bytes)
                }
            }
        }
    }

    fun exportTriangles(triangles: List<Triangle>, format: ExportFormat): ByteArray {
        return when (format) {
            ExportFormat.OBJ -> writeObj(triangles).toByteArray(StandardCharsets.UTF_8)
            ExportFormat.STL_ASCII -> writeStlAscii(triangles).toByteArray(StandardCharsets.UTF_8)
            ExportFormat.STL_BINARY -> writeStlBinary(triangles)
        }
    }

    private fun parseObj(text: String): List<Triangle> {
        val vertices = ArrayList<Vector3>(1024)
        val triangles = ArrayList<Triangle>(2048)

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                return@forEach
            }
            when {
                line.startsWith("v ") -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        val x = parts[1].toFloatOrNull() ?: return@forEach
                        val y = parts[2].toFloatOrNull() ?: return@forEach
                        val z = parts[3].toFloatOrNull() ?: return@forEach
                        vertices.add(Vector3(x, y, z))
                    }
                }
                line.startsWith("f ") -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size < 4) {
                        return@forEach
                    }
                    val indices = ArrayList<Int>(parts.size - 1)
                    for (i in 1 until parts.size) {
                        val token = parts[i]
                        if (token.isBlank()) {
                            continue
                        }
                        val idxToken = token.substringBefore('/')
                        val parsed = idxToken.toIntOrNull() ?: return@forEach
                        val resolved = if (parsed < 0) vertices.size + parsed else parsed - 1
                        if (resolved < 0 || resolved >= vertices.size) {
                            return@forEach
                        }
                        indices.add(resolved)
                    }
                    if (indices.size < 3) {
                        return@forEach
                    }
                    val a = vertices[indices[0]]
                    for (i in 1 until indices.size - 1) {
                        val b = vertices[indices[i]]
                        val c = vertices[indices[i + 1]]
                        triangles.add(Triangle(Vector3(a), Vector3(b), Vector3(c)))
                    }
                }
            }
        }

        return triangles
    }

    private fun parseStlAscii(text: String): List<Triangle> {
        val triangles = ArrayList<Triangle>(2048)
        val vertices = ArrayList<Vector3>(3)
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!line.startsWith("vertex", ignoreCase = true)) {
                return@forEach
            }
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 4) {
                return@forEach
            }
            val x = parts[1].toFloatOrNull() ?: return@forEach
            val y = parts[2].toFloatOrNull() ?: return@forEach
            val z = parts[3].toFloatOrNull() ?: return@forEach
            vertices.add(Vector3(x, y, z))
            if (vertices.size == 3) {
                triangles.add(Triangle(Vector3(vertices[0]), Vector3(vertices[1]), Vector3(vertices[2])))
                vertices.clear()
            }
        }
        return triangles
    }

    private fun parseStlBinary(bytes: ByteArray): List<Triangle> {
        if (bytes.size < 84) {
            return emptyList()
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(80)
        val triangleCount = buffer.int
        val maxReadable = (bytes.size - 84) / 50
        val count = min(max(0, triangleCount), maxReadable)
        val triangles = ArrayList<Triangle>(count)
        repeat(count) {
            if (buffer.remaining() < 50) {
                return@repeat
            }
            // normal
            buffer.float
            buffer.float
            buffer.float
            val a = Vector3(buffer.float, buffer.float, buffer.float)
            val b = Vector3(buffer.float, buffer.float, buffer.float)
            val c = Vector3(buffer.float, buffer.float, buffer.float)
            triangles.add(Triangle(a, b, c))
            // attribute byte count
            buffer.short
        }
        return triangles
    }

    private fun looksLikeBinaryStl(bytes: ByteArray): Boolean {
        if (bytes.size < 84) {
            return false
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(80)
        val count = buffer.int.toLong()
        if (count < 0L) {
            return false
        }
        return 84L + count * 50L == bytes.size.toLong()
    }

    private fun writeObj(triangles: List<Triangle>): String {
        val sb = StringBuilder()
        sb.append("# Octodraw OBJ export\n")
        var vertexIndex = 1
        triangles.forEach { tri ->
            appendObjVertex(sb, tri.a)
            appendObjVertex(sb, tri.b)
            appendObjVertex(sb, tri.c)
            sb.append("f ")
                .append(vertexIndex).append(' ')
                .append(vertexIndex + 1).append(' ')
                .append(vertexIndex + 2).append('\n')
            vertexIndex += 3
        }
        return sb.toString()
    }

    private fun appendObjVertex(sb: StringBuilder, v: Vector3) {
        sb.append("v ")
            .append(fmt(v.x)).append(' ')
            .append(fmt(v.y)).append(' ')
            .append(fmt(v.z)).append('\n')
    }

    private fun writeStlAscii(triangles: List<Triangle>): String {
        val sb = StringBuilder()
        sb.append("solid octodraw\n")
        triangles.forEach { tri ->
            val n = normalFor(tri)
            sb.append("  facet normal ")
                .append(fmt(n.x)).append(' ')
                .append(fmt(n.y)).append(' ')
                .append(fmt(n.z)).append('\n')
            sb.append("    outer loop\n")
            appendStlVertex(sb, tri.a)
            appendStlVertex(sb, tri.b)
            appendStlVertex(sb, tri.c)
            sb.append("    endloop\n")
            sb.append("  endfacet\n")
        }
        sb.append("endsolid octodraw\n")
        return sb.toString()
    }

    private fun appendStlVertex(sb: StringBuilder, v: Vector3) {
        sb.append("      vertex ")
            .append(fmt(v.x)).append(' ')
            .append(fmt(v.y)).append(' ')
            .append(fmt(v.z)).append('\n')
    }

    private fun writeStlBinary(triangles: List<Triangle>): ByteArray {
        val buffer = ByteBuffer.allocate(84 + triangles.size * 50).order(ByteOrder.LITTLE_ENDIAN)
        val header = "Octodraw STL Binary".toByteArray(StandardCharsets.US_ASCII)
        repeat(80) { idx ->
            buffer.put(if (idx < header.size) header[idx] else 0)
        }
        buffer.putInt(triangles.size)
        triangles.forEach { tri ->
            val n = normalFor(tri)
            buffer.putFloat(n.x)
            buffer.putFloat(n.y)
            buffer.putFloat(n.z)
            putVector(buffer, tri.a)
            putVector(buffer, tri.b)
            putVector(buffer, tri.c)
            buffer.putShort(0)
        }
        return buffer.array()
    }

    private fun putVector(buffer: ByteBuffer, v: Vector3) {
        buffer.putFloat(v.x)
        buffer.putFloat(v.y)
        buffer.putFloat(v.z)
    }

    private fun normalFor(triangle: Triangle): Vector3 {
        val ab = Vector3(triangle.b).sub(triangle.a)
        val ac = Vector3(triangle.c).sub(triangle.a)
        val n = ab.crs(ac)
        val lenSq = n.len2()
        if (lenSq <= 1e-12f) {
            return Vector3(0f, 0f, 0f)
        }
        val invLen = (1.0 / sqrt(lenSq.toDouble())).toFloat()
        return n.scl(invLen)
    }

    private fun fmt(value: Float): String {
        val clean = if (abs(value) < 1e-9f) 0f else value
        return String.format(Locale.US, "%.6f", clean)
    }
}

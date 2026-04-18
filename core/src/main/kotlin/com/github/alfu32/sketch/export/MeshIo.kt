package com.github.alfu32.sketch.export

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object MeshIo {
    data class Triangle(val a: Vector3, val b: Vector3, val c: Vector3)
    data class Segment(val start: Vector3, val end: Vector3)
    data class DxfGeometry(
        val triangles: List<Triangle> = emptyList(),
        val segments: List<Segment> = emptyList()
    )

    data class ExportSettings(
        val coordinateScale: Float = 1f,
        val unit: ExportUnit = ExportUnit.UNITLESS,
        val threeMf: ThreeMfExportSettings = ThreeMfExportSettings()
    )

    data class ImportSettings(
        val threeMf: ThreeMfImportSettings = ThreeMfImportSettings()
    )

    data class ThreeMfExportSettings(
        val unit: ThreeMfUnit? = null,
        val coordinateScale: Float = 1f
    )

    data class ThreeMfImportSettings(
        val modelUnitsPerMillimeter: Float = 1f
    )

    enum class ThreeMfUnit(val xmlValue: String) {
        MICRON("micron"),
        MILLIMETER("millimeter"),
        CENTIMETER("centimeter"),
        INCH("inch"),
        FOOT("foot"),
        METER("meter")
    }

    enum class ExportUnit(
        val displayName: String,
        val metersPerUnit: Float,
        val suffixes: Set<String>,
        val threeMfUnit: ThreeMfUnit?,
        val colladaName: String,
        val amfUnit: String?,
        val dxfInsUnits: Int
    ) {
        UNITLESS("unitless", 1f, emptySet(), null, "unitless", null, 0),
        MICRON("micron", 0.000001f, setOf("um", "micron", "microns"), ThreeMfUnit.MICRON, "micron", "micron", 13),
        MILLIMETER("millimeter", 0.001f, setOf("mm", "millimeter", "millimeters", "millimetre", "millimetres"), ThreeMfUnit.MILLIMETER, "millimeter", "millimeter", 4),
        CENTIMETER("centimeter", 0.01f, setOf("cm", "centimeter", "centimeters", "centimetre", "centimetres"), ThreeMfUnit.CENTIMETER, "centimeter", null, 5),
        METER("meter", 1f, setOf("m", "meter", "meters", "metre", "metres"), ThreeMfUnit.METER, "meter", "meter", 6),
        INCH("inch", 0.0254f, setOf("in", "inch", "inches"), ThreeMfUnit.INCH, "inch", "inch", 1),
        FOOT("foot", 0.3048f, setOf("ft", "foot", "feet"), ThreeMfUnit.FOOT, "foot", "feet", 2);

        companion object {
            fun fromSuffix(token: String): ExportUnit? {
                val normalized = token.trim().lowercase(Locale.US)
                if (normalized.isBlank()) {
                    return null
                }
                return values().firstOrNull { normalized in it.suffixes }
            }
        }
    }

    enum class ImportFormat {
        OBJ,
        STL_AUTO,
        STL_ASCII,
        STL_BINARY,
        FBX,
        GLTF,
        GLB,
        DAE,
        DXF,
        THREE_MF,
        AMF,
        IFC
    }

    enum class ExportFormat {
        OBJ,
        STL_ASCII,
        STL_BINARY,
        FBX,
        GLTF,
        GLB,
        DAE,
        DXF,
        THREE_MF,
        AMF
    }

    fun importFormatForExtension(ext: String): ImportFormat? {
        return when (ext.lowercase(Locale.US)) {
            "obj" -> ImportFormat.OBJ
            "stl" -> ImportFormat.STL_AUTO
            "stla" -> ImportFormat.STL_ASCII
            "stlb" -> ImportFormat.STL_BINARY
            "fbx" -> ImportFormat.FBX
            "gltf" -> ImportFormat.GLTF
            "glb" -> ImportFormat.GLB
            "dae" -> ImportFormat.DAE
            "dxf" -> ImportFormat.DXF
            "3mf" -> ImportFormat.THREE_MF
            "amf" -> ImportFormat.AMF
            "ifc" -> ImportFormat.IFC
            else -> null
        }
    }

    fun exportFormatForExtension(ext: String): ExportFormat? {
        return when (ext.lowercase(Locale.US)) {
            "obj" -> ExportFormat.OBJ
            "stl", "stlb" -> ExportFormat.STL_BINARY
            "stla" -> ExportFormat.STL_ASCII
            "fbx" -> ExportFormat.FBX
            "gltf" -> ExportFormat.GLTF
            "glb" -> ExportFormat.GLB
            "dae" -> ExportFormat.DAE
            "dxf" -> ExportFormat.DXF
            "3mf" -> ExportFormat.THREE_MF
            "amf" -> ExportFormat.AMF
            else -> null
        }
    }

    fun importTriangles(
        bytes: ByteArray,
        format: ImportFormat,
        settings: ImportSettings = ImportSettings()
    ): List<Triangle> {
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
            ImportFormat.FBX -> parseFbx(bytes)
            ImportFormat.GLTF -> parseGltfJson(String(bytes, StandardCharsets.UTF_8), null)
            ImportFormat.GLB -> parseGlb(bytes)
            ImportFormat.DAE -> parseCollada(bytes)
            ImportFormat.DXF -> parseDxf(String(bytes, StandardCharsets.UTF_8)).triangles
            ImportFormat.THREE_MF -> parse3mf(bytes, settings.threeMf)
            ImportFormat.AMF -> parseAmf(bytes)
            ImportFormat.IFC -> parseIfc(String(bytes, StandardCharsets.UTF_8))
        }
    }

    fun exportTriangles(
        triangles: List<Triangle>,
        format: ExportFormat,
        settings: ExportSettings = ExportSettings()
    ): ByteArray {
        val scaledTriangles = scaleTriangles(triangles, settings.coordinateScale)
        return when (format) {
            ExportFormat.OBJ -> writeObj(scaledTriangles).toByteArray(StandardCharsets.UTF_8)
            ExportFormat.STL_ASCII -> writeStlAscii(scaledTriangles).toByteArray(StandardCharsets.UTF_8)
            ExportFormat.STL_BINARY -> writeStlBinary(scaledTriangles)
            ExportFormat.FBX -> writeFbxAscii(scaledTriangles).toByteArray(StandardCharsets.UTF_8)
            ExportFormat.GLTF -> writeGltf(scaledTriangles)
            ExportFormat.GLB -> writeGlb(scaledTriangles)
            ExportFormat.DAE -> writeCollada(scaledTriangles, settings.unit).toByteArray(StandardCharsets.UTF_8)
            ExportFormat.DXF -> writeDxf(scaledTriangles, emptyList(), settings.unit).toByteArray(StandardCharsets.UTF_8)
            ExportFormat.THREE_MF -> write3mf(
                scaledTriangles,
                settings.threeMf.copy(
                    unit = settings.unit.threeMfUnit,
                    coordinateScale = settings.threeMf.coordinateScale
                )
            )
            ExportFormat.AMF -> writeAmf(scaledTriangles, settings.unit).toByteArray(StandardCharsets.UTF_8)
        }
    }

    fun importDxfGeometry(bytes: ByteArray): DxfGeometry {
        return parseDxf(String(bytes, StandardCharsets.UTF_8))
    }

    fun exportDxfGeometry(
        triangles: List<Triangle>,
        segments: List<Segment>,
        settings: ExportSettings = ExportSettings()
    ): ByteArray {
        return writeDxf(
            scaleTriangles(triangles, settings.coordinateScale),
            scaleSegments(segments, settings.coordinateScale),
            settings.unit
        ).toByteArray(StandardCharsets.UTF_8)
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

    private fun scaleTriangles(triangles: List<Triangle>, scale: Float): List<Triangle> {
        val safeScale = if (scale.isFinite() && scale > 0f) scale else 1f
        if (abs(safeScale - 1f) <= 1e-9f) {
            return triangles
        }
        return triangles.map { tri ->
            Triangle(
                Vector3(tri.a).scl(safeScale),
                Vector3(tri.b).scl(safeScale),
                Vector3(tri.c).scl(safeScale)
            )
        }
    }

    private fun scaleSegments(segments: List<Segment>, scale: Float): List<Segment> {
        val safeScale = if (scale.isFinite() && scale > 0f) scale else 1f
        if (abs(safeScale - 1f) <= 1e-9f) {
            return segments
        }
        return segments.map { segment ->
            Segment(Vector3(segment.start).scl(safeScale), Vector3(segment.end).scl(safeScale))
        }
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
            buffer.float
            buffer.float
            buffer.float
            val a = Vector3(buffer.float, buffer.float, buffer.float)
            val b = Vector3(buffer.float, buffer.float, buffer.float)
            val c = Vector3(buffer.float, buffer.float, buffer.float)
            triangles.add(Triangle(a, b, c))
            buffer.short
        }
        return triangles
    }

    private fun parseFbx(bytes: ByteArray): List<Triangle> {
        if (bytes.size < 27 && bytes.isNotEmpty()) {
            return emptyList()
        }
        val header = if (bytes.size >= 21) String(bytes, 0, 21, StandardCharsets.US_ASCII) else ""
        if (header.startsWith("Kaydara FBX Binary", ignoreCase = true)) {
            // Binary FBX requires a full node parser. Keep ASCII support for now.
            return emptyList()
        }
        return parseFbxAscii(String(bytes, StandardCharsets.UTF_8))
    }

    private fun parseFbxAscii(text: String): List<Triangle> {
        val verticesRegex = Regex(
            "Vertices\\s*:\\s*\\*\\d+\\s*\\{\\s*a\\s*:\\s*([^}]*)\\}",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val polygonRegex = Regex(
            "PolygonVertexIndex\\s*:\\s*\\*\\d+\\s*\\{\\s*a\\s*:\\s*([^}]*)\\}",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val vertexMatches = verticesRegex.findAll(text).toList()
        val polygonMatches = polygonRegex.findAll(text).toList()
        if (vertexMatches.isEmpty() || polygonMatches.isEmpty()) {
            return emptyList()
        }
        val geometryCount = min(vertexMatches.size, polygonMatches.size)
        val out = ArrayList<Triangle>(4096)
        repeat(geometryCount) { index ->
            val rawVertexValues = parseDoubleList(vertexMatches[index].groupValues[1])
            if (rawVertexValues.size < 9) {
                return@repeat
            }
            val vertices = ArrayList<Vector3>(rawVertexValues.size / 3)
            var p = 0
            while (p + 2 < rawVertexValues.size) {
                vertices.add(
                    Vector3(
                        rawVertexValues[p].toFloat(),
                        rawVertexValues[p + 1].toFloat(),
                        rawVertexValues[p + 2].toFloat()
                    )
                )
                p += 3
            }
            val encodedIndices = parseIntList(polygonMatches[index].groupValues[1])
            if (encodedIndices.isEmpty()) {
                return@repeat
            }
            val polygon = ArrayList<Int>(8)
            fun flushPolygon() {
                if (polygon.size < 3) {
                    polygon.clear()
                    return
                }
                val a = polygon.first()
                for (i in 1 until polygon.size - 1) {
                    val b = polygon[i]
                    val c = polygon[i + 1]
                    if (a !in vertices.indices || b !in vertices.indices || c !in vertices.indices) {
                        continue
                    }
                    out.add(Triangle(Vector3(vertices[a]), Vector3(vertices[b]), Vector3(vertices[c])))
                }
                polygon.clear()
            }
            encodedIndices.forEach { encoded ->
                if (encoded < 0) {
                    val value = -encoded - 1
                    polygon.add(value)
                    flushPolygon()
                } else {
                    polygon.add(encoded)
                }
            }
            flushPolygon()
        }
        return out
    }

    private data class IfcEntity(val name: String, val args: String)

    private fun parseIfc(text: String): List<Triangle> {
        val entities = parseIfcEntities(text)
        if (entities.isEmpty()) {
            return emptyList()
        }
        val points = HashMap<Int, Vector3>(entities.size)
        val pointLists = HashMap<Int, List<Vector3>>(entities.size / 8)
        val indexedFaces = HashMap<Int, IntArray>(entities.size / 8)
        val polyLoops = HashMap<Int, IntArray>(entities.size / 8)
        val faceBounds = HashMap<Int, Int>(entities.size / 8)
        val facesById = HashMap<Int, IntArray>(entities.size / 8)
        val shells = HashMap<Int, IntArray>(entities.size / 8)
        val breps = ArrayList<Int>(64)

        entities.forEach { (id, entity) ->
            when (entity.name) {
                "IFCCARTESIANPOINT" -> {
                    val values = parseDoubleList(entity.args)
                    if (values.size >= 3) {
                        points[id] = Vector3(values[0].toFloat(), values[1].toFloat(), values[2].toFloat())
                    }
                }
                "IFCCARTESIANPOINTLIST3D" -> {
                    val tuples = extractDoubleTuples(entity.args)
                    if (tuples.isNotEmpty()) {
                        val list = tuples.mapNotNull { tuple ->
                            if (tuple.size < 3) null else Vector3(tuple[0].toFloat(), tuple[1].toFloat(), tuple[2].toFloat())
                        }
                        if (list.isNotEmpty()) {
                            pointLists[id] = list
                        }
                    }
                }
                "IFCINDEXEDPOLYGONALFACE", "IFCINDEXEDPOLYGONALFACEWITHVOIDS" -> {
                    val tuple = extractIntTuples(entity.args).firstOrNull()
                    if (tuple != null && tuple.size >= 3) {
                        indexedFaces[id] = tuple.toIntArray()
                    }
                }
                "IFCPOLYLOOP" -> {
                    val refs = extractRefs(entity.args)
                    if (refs.size >= 3) {
                        polyLoops[id] = refs.toIntArray()
                    }
                }
                "IFCFACEOUTERBOUND" -> {
                    val refs = extractRefs(entity.args)
                    if (refs.isNotEmpty()) {
                        faceBounds[id] = refs.first()
                    }
                }
                "IFCFACE" -> {
                    val refs = extractRefs(entity.args)
                    if (refs.isNotEmpty()) {
                        facesById[id] = refs.toIntArray()
                    }
                }
                "IFCCLOSEDSHELL" -> {
                    val refs = extractRefs(entity.args)
                    if (refs.isNotEmpty()) {
                        shells[id] = refs.toIntArray()
                    }
                }
                "IFCFACETEDBREP" -> {
                    val refs = extractRefs(entity.args)
                    if (refs.isNotEmpty()) {
                        breps.add(refs.first())
                    }
                }
            }
        }

        val out = ArrayList<Triangle>(4096)

        entities.forEach { (_, entity) ->
            if (entity.name == "IFCTRIANGULATEDFACESET") {
                val refs = extractRefs(entity.args)
                if (refs.isEmpty()) {
                    return@forEach
                }
                val pointList = pointLists[refs.first()] ?: return@forEach
                val faces = extractIntTuples(entity.args)
                faces.forEach { tuple ->
                    if (tuple.size < 3) {
                        return@forEach
                    }
                    val root = tuple.first() - 1
                    for (i in 1 until tuple.size - 1) {
                        val i1 = tuple[i] - 1
                        val i2 = tuple[i + 1] - 1
                        if (root !in pointList.indices || i1 !in pointList.indices || i2 !in pointList.indices) {
                            continue
                        }
                        out.add(
                            Triangle(
                                Vector3(pointList[root]),
                                Vector3(pointList[i1]),
                                Vector3(pointList[i2])
                            )
                        )
                    }
                }
            } else if (entity.name == "IFCPOLYGONALFACESET") {
                val refs = extractRefs(entity.args)
                if (refs.isEmpty()) {
                    return@forEach
                }
                val pointList = pointLists[refs.first()] ?: return@forEach
                refs.drop(1).forEach { faceRef ->
                    val polygon = indexedFaces[faceRef] ?: return@forEach
                    triangulateIfcIndexPolygon(out, pointList, polygon)
                }
            }
        }

        breps.forEach { shellRef ->
            val faceRefs = shells[shellRef] ?: return@forEach
            faceRefs.forEach { faceRef ->
                val bounds = facesById[faceRef] ?: return@forEach
                val outerBound = bounds.firstOrNull() ?: return@forEach
                val loopRef = faceBounds[outerBound] ?: return@forEach
                val pointRefs = polyLoops[loopRef] ?: return@forEach
                val polygon = ArrayList<Vector3>(pointRefs.size)
                pointRefs.forEach { pointRef ->
                    val point = points[pointRef] ?: return@forEach
                    polygon.add(point)
                }
                triangulateIfcPolygon(out, polygon)
            }
        }
        return out
    }

    private fun triangulateIfcIndexPolygon(out: MutableList<Triangle>, vertices: List<Vector3>, polygonIndices: IntArray) {
        if (polygonIndices.size < 3) {
            return
        }
        val root = polygonIndices.first() - 1
        for (i in 1 until polygonIndices.size - 1) {
            val i1 = polygonIndices[i] - 1
            val i2 = polygonIndices[i + 1] - 1
            if (root !in vertices.indices || i1 !in vertices.indices || i2 !in vertices.indices) {
                continue
            }
            out.add(Triangle(Vector3(vertices[root]), Vector3(vertices[i1]), Vector3(vertices[i2])))
        }
    }

    private fun triangulateIfcPolygon(out: MutableList<Triangle>, polygon: List<Vector3>) {
        if (polygon.size < 3) {
            return
        }
        val root = polygon.first()
        for (i in 1 until polygon.size - 1) {
            out.add(Triangle(Vector3(root), Vector3(polygon[i]), Vector3(polygon[i + 1])))
        }
    }

    private fun parseIfcEntities(text: String): Map<Int, IfcEntity> {
        val dataStart = text.indexOf("DATA;", ignoreCase = true)
        val source = if (dataStart >= 0) {
            val dataEnd = text.indexOf("ENDSEC;", startIndex = dataStart + 5, ignoreCase = true)
            if (dataEnd > dataStart) text.substring(dataStart + 5, dataEnd) else text.substring(dataStart + 5)
        } else {
            text
        }
        val out = LinkedHashMap<Int, IfcEntity>(4096)
        var i = 0
        while (i < source.length) {
            val hash = source.indexOf('#', i)
            if (hash < 0) break
            var cursor = hash + 1
            while (cursor < source.length && source[cursor].isDigit()) {
                cursor++
            }
            val id = source.substring(hash + 1, cursor).toIntOrNull()
            if (id == null) {
                i = hash + 1
                continue
            }
            while (cursor < source.length && source[cursor].isWhitespace()) cursor++
            if (cursor >= source.length || source[cursor] != '=') {
                i = cursor
                continue
            }
            cursor++
            while (cursor < source.length && source[cursor].isWhitespace()) cursor++
            val nameStart = cursor
            while (cursor < source.length && (source[cursor].isLetterOrDigit() || source[cursor] == '_')) {
                cursor++
            }
            if (cursor <= nameStart) {
                i = cursor
                continue
            }
            val name = source.substring(nameStart, cursor).uppercase(Locale.US)
            while (cursor < source.length && source[cursor].isWhitespace()) cursor++
            if (cursor >= source.length || source[cursor] != '(') {
                i = cursor
                continue
            }
            val argsStart = cursor + 1
            var depth = 1
            var inString = false
            cursor++
            while (cursor < source.length && depth > 0) {
                val ch = source[cursor]
                if (inString) {
                    if (ch == '\'') {
                        if (cursor + 1 < source.length && source[cursor + 1] == '\'') {
                            cursor += 2
                            continue
                        }
                        inString = false
                    }
                } else {
                    when (ch) {
                        '\'' -> inString = true
                        '(' -> depth++
                        ')' -> depth--
                    }
                }
                cursor++
            }
            if (depth != 0) {
                break
            }
            val args = source.substring(argsStart, cursor - 1)
            while (cursor < source.length && source[cursor].isWhitespace()) cursor++
            if (cursor < source.length && source[cursor] == ';') {
                cursor++
            }
            out[id] = IfcEntity(name, args)
            i = cursor
        }
        return out
    }

    private fun parseGltfJson(jsonText: String, glbBinaryChunk: ByteArray?): List<Triangle> {
        val root = JsonReader().parse(jsonText)
        val buffers = loadGltfBuffers(root.get("buffers"), glbBinaryChunk)
        if (buffers.isEmpty()) {
            return emptyList()
        }
        val bufferViews = root.get("bufferViews") ?: return emptyList()
        val accessors = root.get("accessors") ?: return emptyList()
        val meshes = root.get("meshes") ?: return emptyList()
        val out = ArrayList<Triangle>(4096)

        var mesh = meshes.child
        while (mesh != null) {
            val primitives = mesh.get("primitives")
            var primitive = primitives?.child
            while (primitive != null) {
                val attributes = primitive.get("attributes")
                val positionAccessorIndex = attributes?.getInt("POSITION", -1) ?: -1
                if (positionAccessorIndex >= 0) {
                    val positions = readAccessorVec3(accessors, bufferViews, buffers, positionAccessorIndex)
                    if (positions.isNotEmpty()) {
                        val mode = primitive.getInt("mode", 4)
                        val indexAccessorIndex = primitive.getInt("indices", -1)
                        val indices = if (indexAccessorIndex >= 0) {
                            readAccessorIndices(accessors, bufferViews, buffers, indexAccessorIndex)
                        } else {
                            IntArray(positions.size) { it }
                        }
                        out.addAll(triangulateIndexedPrimitive(positions, indices, mode))
                    }
                }
                primitive = primitive.next
            }
            mesh = mesh.next
        }
        return out
    }

    private fun loadGltfBuffers(buffersNode: JsonValue?, glbBinaryChunk: ByteArray?): List<ByteArray> {
        if (buffersNode == null) {
            return emptyList()
        }
        val buffers = ArrayList<ByteArray>()
        var index = 0
        var bufferNode = buffersNode.child
        while (bufferNode != null) {
            val uri = bufferNode.getString("uri", "")
            val bytes = when {
                uri.isNotBlank() -> decodeGltfBufferUri(uri)
                glbBinaryChunk != null && index == 0 -> glbBinaryChunk
                else -> null
            } ?: return emptyList()
            buffers.add(bytes)
            bufferNode = bufferNode.next
            index++
        }
        return buffers
    }

    private fun decodeGltfBufferUri(uri: String): ByteArray? {
        if (uri.startsWith("data:", ignoreCase = true)) {
            val comma = uri.indexOf(',')
            if (comma <= 0) {
                return null
            }
            val meta = uri.substring(0, comma)
            val payload = uri.substring(comma + 1)
            return if (meta.contains(";base64", ignoreCase = true)) {
                Base64.getDecoder().decode(payload)
            } else {
                payload.toByteArray(StandardCharsets.UTF_8)
            }
        }
        // External buffers are not available in byte-array import mode.
        return null
    }

    private fun readAccessorVec3(
        accessors: JsonValue,
        bufferViews: JsonValue,
        buffers: List<ByteArray>,
        accessorIndex: Int
    ): List<Vector3> {
        val accessor = accessors.get(accessorIndex) ?: return emptyList()
        if (accessor.getString("type", "") != "VEC3") {
            return emptyList()
        }
        val componentType = accessor.getInt("componentType", 0)
        if (componentType != 5126) {
            return emptyList()
        }
        val bufferViewIndex = accessor.getInt("bufferView", -1)
        if (bufferViewIndex < 0) {
            return emptyList()
        }
        val bufferView = bufferViews.get(bufferViewIndex) ?: return emptyList()
        val bufferIndex = bufferView.getInt("buffer", -1)
        if (bufferIndex !in buffers.indices) {
            return emptyList()
        }
        val count = accessor.getInt("count", 0)
        if (count <= 0) {
            return emptyList()
        }
        val accessorOffset = accessor.getInt("byteOffset", 0)
        val viewOffset = bufferView.getInt("byteOffset", 0)
        val stride = bufferView.getInt("byteStride", 12).let { if (it <= 0) 12 else it }
        val buffer = buffers[bufferIndex]
        val out = ArrayList<Vector3>(count)
        val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        repeat(count) { idx ->
            val base = viewOffset + accessorOffset + idx * stride
            if (base + 11 >= buffer.size) {
                return out
            }
            out.add(
                Vector3(
                    byteBuffer.getFloat(base),
                    byteBuffer.getFloat(base + 4),
                    byteBuffer.getFloat(base + 8)
                )
            )
        }
        return out
    }

    private fun readAccessorIndices(
        accessors: JsonValue,
        bufferViews: JsonValue,
        buffers: List<ByteArray>,
        accessorIndex: Int
    ): IntArray {
        val accessor = accessors.get(accessorIndex) ?: return IntArray(0)
        val bufferViewIndex = accessor.getInt("bufferView", -1)
        if (bufferViewIndex < 0) {
            return IntArray(0)
        }
        val bufferView = bufferViews.get(bufferViewIndex) ?: return IntArray(0)
        val bufferIndex = bufferView.getInt("buffer", -1)
        if (bufferIndex !in buffers.indices) {
            return IntArray(0)
        }
        val count = accessor.getInt("count", 0)
        if (count <= 0) {
            return IntArray(0)
        }
        val accessorOffset = accessor.getInt("byteOffset", 0)
        val viewOffset = bufferView.getInt("byteOffset", 0)
        val componentType = accessor.getInt("componentType", 0)
        val componentSize = when (componentType) {
            5121 -> 1
            5123 -> 2
            5125 -> 4
            else -> return IntArray(0)
        }
        val stride = bufferView.getInt("byteStride", componentSize).let { if (it <= 0) componentSize else it }
        val buffer = buffers[bufferIndex]
        val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        val out = IntArray(count)
        for (i in 0 until count) {
            val base = viewOffset + accessorOffset + i * stride
            if (base + componentSize - 1 >= buffer.size) {
                return IntArray(i)
            }
            out[i] = when (componentType) {
                5121 -> byteBuffer.get(base).toInt() and 0xFF
                5123 -> byteBuffer.getShort(base).toInt() and 0xFFFF
                5125 -> byteBuffer.getInt(base)
                else -> 0
            }
        }
        return out
    }

    private fun triangulateIndexedPrimitive(
        positions: List<Vector3>,
        indices: IntArray,
        mode: Int
    ): List<Triangle> {
        if (indices.isEmpty()) {
            return emptyList()
        }
        val out = ArrayList<Triangle>(indices.size / 3)
        fun addTri(i0: Int, i1: Int, i2: Int) {
            if (i0 !in positions.indices || i1 !in positions.indices || i2 !in positions.indices) {
                return
            }
            out.add(
                Triangle(
                    Vector3(positions[i0]),
                    Vector3(positions[i1]),
                    Vector3(positions[i2])
                )
            )
        }
        when (mode) {
            4 -> {
                var i = 0
                while (i + 2 < indices.size) {
                    addTri(indices[i], indices[i + 1], indices[i + 2])
                    i += 3
                }
            }
            5 -> {
                var i = 0
                while (i + 2 < indices.size) {
                    if (i % 2 == 0) {
                        addTri(indices[i], indices[i + 1], indices[i + 2])
                    } else {
                        addTri(indices[i + 1], indices[i], indices[i + 2])
                    }
                    i++
                }
            }
            6 -> {
                val root = indices[0]
                var i = 1
                while (i + 1 < indices.size) {
                    addTri(root, indices[i], indices[i + 1])
                    i++
                }
            }
        }
        return out
    }

    private fun parseGlb(bytes: ByteArray): List<Triangle> {
        if (bytes.size < 20) {
            return emptyList()
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int
        if (magic != 0x46546C67) {
            return emptyList()
        }
        val version = buffer.int
        if (version != 2) {
            return emptyList()
        }
        val totalLength = buffer.int
        if (totalLength > bytes.size) {
            return emptyList()
        }
        var jsonChunk: ByteArray? = null
        var binChunk: ByteArray? = null
        while (buffer.remaining() >= 8) {
            val chunkLength = buffer.int
            val chunkType = buffer.int
            if (chunkLength < 0 || buffer.remaining() < chunkLength) {
                return emptyList()
            }
            val data = ByteArray(chunkLength)
            buffer.get(data)
            when (chunkType) {
                0x4E4F534A -> jsonChunk = data
                0x004E4942 -> if (binChunk == null) binChunk = data
            }
        }
        val jsonBytes = jsonChunk ?: return emptyList()
        return parseGltfJson(String(jsonBytes, StandardCharsets.UTF_8), binChunk)
    }

    private fun parseCollada(bytes: ByteArray): List<Triangle> {
        val doc = parseXml(bytes) ?: return emptyList()
        val sourceFloatArrays = linkedMapOf<String, FloatArray>()
        val sourceNodes = doc.getElementsByTagName("source")
        for (i in 0 until sourceNodes.length) {
            val source = sourceNodes.item(i) as? Element ?: continue
            val sourceId = source.getAttribute("id")
            if (sourceId.isNullOrBlank()) continue
            val floatArrays = source.getElementsByTagName("float_array")
            if (floatArrays.length == 0) continue
            val floatArray = floatArrays.item(0) as? Element ?: continue
            sourceFloatArrays[sourceId] = parseFloatArrayText(floatArray.textContent)
        }

        val verticesToSource = linkedMapOf<String, String>()
        val verticesNodes = doc.getElementsByTagName("vertices")
        for (i in 0 until verticesNodes.length) {
            val vertices = verticesNodes.item(i) as? Element ?: continue
            val id = vertices.getAttribute("id")
            if (id.isNullOrBlank()) continue
            val inputs = vertices.getElementsByTagName("input")
            for (j in 0 until inputs.length) {
                val input = inputs.item(j) as? Element ?: continue
                if (input.getAttribute("semantic").equals("POSITION", ignoreCase = true)) {
                    val sourceRef = input.getAttribute("source").removePrefix("#")
                    if (sourceRef.isNotBlank()) {
                        verticesToSource[id] = sourceRef
                    }
                }
            }
        }

        val out = ArrayList<Triangle>(2048)
        val trianglesNodes = doc.getElementsByTagName("triangles")
        for (i in 0 until trianglesNodes.length) {
            val trianglesEl = trianglesNodes.item(i) as? Element ?: continue
            val inputNodes = trianglesEl.getElementsByTagName("input")
            var maxOffset = 0
            var vertexOffset = -1
            var positionSourceId: String? = null
            for (j in 0 until inputNodes.length) {
                val input = inputNodes.item(j) as? Element ?: continue
                val semantic = input.getAttribute("semantic")
                val offset = input.getAttribute("offset").toIntOrNull() ?: 0
                maxOffset = max(maxOffset, offset)
                if (semantic.equals("VERTEX", ignoreCase = true)) {
                    vertexOffset = offset
                    val verticesId = input.getAttribute("source").removePrefix("#")
                    positionSourceId = verticesToSource[verticesId]
                } else if (semantic.equals("POSITION", ignoreCase = true)) {
                    vertexOffset = offset
                    positionSourceId = input.getAttribute("source").removePrefix("#")
                }
            }
            if (vertexOffset < 0 || positionSourceId.isNullOrBlank()) continue
            val positions = sourceFloatArrays[positionSourceId] ?: continue
            val pNodes = trianglesEl.getElementsByTagName("p")
            if (pNodes.length == 0) continue
            val indices = parseIntArrayText(pNodes.item(0).textContent)
            val stride = maxOffset + 1
            val triCount = indices.size / (stride * 3)
            for (tri in 0 until triCount) {
                val i0 = indices[tri * stride * 3 + vertexOffset]
                val i1 = indices[tri * stride * 3 + stride + vertexOffset]
                val i2 = indices[tri * stride * 3 + 2 * stride + vertexOffset]
                val a = vec3FromFloatArray(positions, i0) ?: continue
                val b = vec3FromFloatArray(positions, i1) ?: continue
                val c = vec3FromFloatArray(positions, i2) ?: continue
                out.add(Triangle(a, b, c))
            }
        }
        return out
    }

    private data class DxfPair(val code: Int, val value: String)

    private data class DxfEntity(
        val type: String,
        val pairs: List<DxfPair>
    )

    private fun parseDxf(text: String): DxfGeometry {
        val rawLines = text.lineSequence().map { it.trimEnd('\r') }.toList()
        val entities = ArrayList<DxfEntity>(2048)
        var i = 0
        var pendingSection = false
        var inEntities = false
        var currentType: String? = null
        var currentPairs = ArrayList<DxfPair>(16)

        fun flushEntity() {
            val type = currentType ?: return
            if (inEntities) {
                entities.add(DxfEntity(type, currentPairs.toList()))
            }
            currentType = null
            currentPairs = ArrayList(16)
        }

        while (i + 1 < rawLines.size) {
            val code = rawLines[i].trim().toIntOrNull()
            val value = rawLines[i + 1].trim()
            i += 2
            if (code == null) continue
            if (pendingSection && code == 2) {
                inEntities = value.equals("ENTITIES", ignoreCase = true)
                pendingSection = false
                continue
            }
            if (code == 0) {
                if (value.equals("SECTION", ignoreCase = true)) {
                    flushEntity()
                    pendingSection = true
                    inEntities = false
                    continue
                }
                if (value.equals("ENDSEC", ignoreCase = true) || value.equals("EOF", ignoreCase = true)) {
                    flushEntity()
                    inEntities = false
                    pendingSection = false
                    continue
                }
                flushEntity()
                if (inEntities) {
                    currentType = value.uppercase(Locale.US)
                }
                continue
            }
            if (currentType != null) {
                currentPairs.add(DxfPair(code, value))
            }
        }
        flushEntity()

        val triangles = ArrayList<Triangle>(2048)
        val segments = ArrayList<Segment>(4096)
        var entityIndex = 0
        while (entityIndex < entities.size) {
            val entity = entities[entityIndex]
            when (entity.type) {
                "3DFACE", "SOLID", "TRACE" -> {
                    parseDxfFaceEntity(entity)?.let { triangles.addAll(it) }
                }
                "LINE" -> {
                    parseDxfLineEntity(entity)?.let { segments.add(it) }
                }
                "LWPOLYLINE" -> {
                    segments.addAll(parseDxfLwPolyline(entity))
                }
                "POLYLINE" -> {
                    val polylineEntities = ArrayList<DxfEntity>(8)
                    var lookahead = entityIndex + 1
                    while (lookahead < entities.size) {
                        val next = entities[lookahead]
                        if (next.type == "SEQEND") {
                            polylineEntities.add(next)
                            break
                        }
                        if (next.type != "VERTEX") {
                            lookahead--
                            break
                        }
                        polylineEntities.add(next)
                        lookahead++
                    }
                    segments.addAll(parseDxfPolyline(entity, polylineEntities))
                    entityIndex = lookahead
                }
            }
            entityIndex++
        }
        return DxfGeometry(triangles = triangles, segments = segments)
    }

    private fun parseDxfFaceEntity(entity: DxfEntity): List<Triangle>? {
        val a = dxfPoint(entity.pairs, 10, 20, 30) ?: return null
        val b = dxfPoint(entity.pairs, 11, 21, 31) ?: return null
        val c = dxfPoint(entity.pairs, 12, 22, 32) ?: return null
        val d = dxfPoint(entity.pairs, 13, 23, 33) ?: Vector3(c)
        val out = ArrayList<Triangle>(2)
        if (a.dst2(b) > 1e-12f && a.dst2(c) > 1e-12f && b.dst2(c) > 1e-12f) {
            out.add(Triangle(Vector3(a), Vector3(b), Vector3(c)))
        }
        if (d.dst2(c) > 1e-10f && a.dst2(d) > 1e-12f && c.dst2(d) > 1e-12f) {
            out.add(Triangle(Vector3(a), Vector3(c), Vector3(d)))
        }
        return out
    }

    private fun parseDxfLineEntity(entity: DxfEntity): Segment? {
        val start = dxfPoint(entity.pairs, 10, 20, 30) ?: return null
        val end = dxfPoint(entity.pairs, 11, 21, 31) ?: return null
        if (start.dst2(end) <= 1e-12f) {
            return null
        }
        return Segment(start, end)
    }

    private fun parseDxfLwPolyline(entity: DxfEntity): List<Segment> {
        val xs = entity.pairs.filter { it.code == 10 }.mapNotNull { it.value.toFloatOrNull() }
        val ys = entity.pairs.filter { it.code == 20 }.mapNotNull { it.value.toFloatOrNull() }
        val zs = entity.pairs.filter { it.code == 30 }.mapNotNull { it.value.toFloatOrNull() }
        val count = min(xs.size, ys.size)
        if (count < 2) {
            return emptyList()
        }
        val closed = ((dxfInt(entity.pairs, 70) ?: 0) and 1) != 0
        val points = ArrayList<Vector3>(count)
        for (index in 0 until count) {
            points.add(Vector3(xs[index], ys[index], zs.getOrNull(index) ?: 0f))
        }
        return dxfPolylineSegments(points, closed)
    }

    private fun parseDxfPolyline(entity: DxfEntity, nestedEntities: List<DxfEntity>): List<Segment> {
        val closed = ((dxfInt(entity.pairs, 70) ?: 0) and 1) != 0
        val points = ArrayList<Vector3>(nestedEntities.size)
        nestedEntities.forEach { nested ->
            if (nested.type != "VERTEX") return@forEach
            dxfPoint(nested.pairs, 10, 20, 30)?.let { points.add(it) }
        }
        return dxfPolylineSegments(points, closed)
    }

    private fun dxfPolylineSegments(points: List<Vector3>, closed: Boolean): List<Segment> {
        if (points.size < 2) {
            return emptyList()
        }
        val out = ArrayList<Segment>(points.size)
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            if (start.dst2(end) > 1e-12f) {
                out.add(Segment(Vector3(start), Vector3(end)))
            }
        }
        if (closed && points.first().dst2(points.last()) > 1e-12f) {
            out.add(Segment(Vector3(points.last()), Vector3(points.first())))
        }
        return out
    }

    private fun dxfPoint(
        pairs: List<DxfPair>,
        xCode: Int,
        yCode: Int,
        zCode: Int
    ): Vector3? {
        val x = dxfFloat(pairs, xCode) ?: return null
        val y = dxfFloat(pairs, yCode) ?: return null
        val z = dxfFloat(pairs, zCode) ?: 0f
        return Vector3(x, y, z)
    }

    private fun dxfFloat(pairs: List<DxfPair>, code: Int): Float? {
        return pairs.firstOrNull { it.code == code }?.value?.toFloatOrNull()
    }

    private fun dxfInt(pairs: List<DxfPair>, code: Int): Int? {
        return pairs.firstOrNull { it.code == code }?.value?.toIntOrNull()
    }

    private fun parse3mf(bytes: ByteArray, settings: ThreeMfImportSettings): List<Triangle> {
        val modelBytes = extractZipEntry(bytes) { name ->
            name.equals("3D/3dmodel.model", ignoreCase = true) || name.lowercase(Locale.US).endsWith(".model")
        } ?: return emptyList()
        val doc = parseXml(modelBytes) ?: return emptyList()
        val modelElement = doc.documentElement
        val fileUnitScale = threeMfUnitScaleToMillimeter(modelElement?.getAttribute("unit"))
        val coordinateScale = fileUnitScale * settings.modelUnitsPerMillimeter.coerceAtLeast(1e-9f)
        val vertexNodes = doc.getElementsByTagName("vertex")
        if (vertexNodes.length == 0) {
            return emptyList()
        }
        val vertices = ArrayList<Vector3>(vertexNodes.length)
        for (i in 0 until vertexNodes.length) {
            val node = vertexNodes.item(i) as? Element ?: continue
            val x = node.getAttribute("x").toFloatOrNull() ?: continue
            val y = node.getAttribute("y").toFloatOrNull() ?: continue
            val z = node.getAttribute("z").toFloatOrNull() ?: continue
            vertices.add(Vector3(x, y, z).scl(coordinateScale))
        }
        val out = ArrayList<Triangle>(2048)
        val triangleNodes = doc.getElementsByTagName("triangle")
        for (i in 0 until triangleNodes.length) {
            val node = triangleNodes.item(i) as? Element ?: continue
            val i0 = node.getAttribute("v1").toIntOrNull() ?: continue
            val i1 = node.getAttribute("v2").toIntOrNull() ?: continue
            val i2 = node.getAttribute("v3").toIntOrNull() ?: continue
            if (i0 !in vertices.indices || i1 !in vertices.indices || i2 !in vertices.indices) {
                continue
            }
            out.add(
                Triangle(
                    Vector3(vertices[i0]),
                    Vector3(vertices[i1]),
                    Vector3(vertices[i2])
                )
            )
        }
        return out
    }

    private fun threeMfUnitScaleToMillimeter(unit: String?): Float {
        return when (unit?.trim()?.lowercase(Locale.US)) {
            null, "", "millimeter" -> 1f
            "micron" -> 0.001f
            "centimeter" -> 10f
            "inch" -> 25.4f
            "foot" -> 304.8f
            "meter" -> 1000f
            else -> 1f
        }
    }

    private fun parseAmf(bytes: ByteArray): List<Triangle> {
        val doc = parseXml(bytes) ?: return emptyList()
        val vertices = ArrayList<Vector3>()
        val vertexNodes = doc.getElementsByTagName("vertex")
        for (i in 0 until vertexNodes.length) {
            val vertex = vertexNodes.item(i) as? Element ?: continue
            val coords = vertex.getElementsByTagName("coordinates")
            if (coords.length == 0) continue
            val coord = coords.item(0) as? Element ?: continue
            val x = coord.getElementsByTagName("x").item(0)?.textContent?.trim()?.toFloatOrNull() ?: continue
            val y = coord.getElementsByTagName("y").item(0)?.textContent?.trim()?.toFloatOrNull() ?: continue
            val z = coord.getElementsByTagName("z").item(0)?.textContent?.trim()?.toFloatOrNull() ?: continue
            vertices.add(Vector3(x, y, z))
        }
        if (vertices.isEmpty()) {
            return emptyList()
        }
        val out = ArrayList<Triangle>(2048)
        val triangleNodes = doc.getElementsByTagName("triangle")
        for (i in 0 until triangleNodes.length) {
            val node = triangleNodes.item(i) as? Element ?: continue
            val i0 = node.getElementsByTagName("v1").item(0)?.textContent?.trim()?.toIntOrNull() ?: continue
            val i1 = node.getElementsByTagName("v2").item(0)?.textContent?.trim()?.toIntOrNull() ?: continue
            val i2 = node.getElementsByTagName("v3").item(0)?.textContent?.trim()?.toIntOrNull() ?: continue
            if (i0 !in vertices.indices || i1 !in vertices.indices || i2 !in vertices.indices) {
                continue
            }
            out.add(Triangle(Vector3(vertices[i0]), Vector3(vertices[i1]), Vector3(vertices[i2])))
        }
        return out
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

    private fun writeFbxAscii(triangles: List<Triangle>): String {
        val vertexCount = triangles.size * 3
        val polygonIndexCount = triangles.size * 3
        val vertices = StringBuilder(max(128, vertexCount * 24))
        val indices = StringBuilder(max(128, polygonIndexCount * 6))
        var vertexIndex = 0
        triangles.forEach { tri ->
            listOf(tri.a, tri.b, tri.c).forEach { v ->
                if (vertices.isNotEmpty()) vertices.append(',')
                vertices.append(fmt(v.x)).append(',').append(fmt(v.y)).append(',').append(fmt(v.z))
            }
            if (indices.isNotEmpty()) {
                indices.append(',')
            }
            indices.append(vertexIndex)
                .append(',')
                .append(vertexIndex + 1)
                .append(',')
                .append(-(vertexIndex + 2) - 1)
            vertexIndex += 3
        }

        val geometryId = 100000L
        val modelId = 100001L
        val sb = StringBuilder()
        sb.append("; FBX 7.4.0 generated by Octodraw\n")
        sb.append("FBXHeaderExtension:  {\n")
        sb.append("  FBXHeaderVersion: 1003\n")
        sb.append("  FBXVersion: 7400\n")
        sb.append("}\n")
        sb.append("Definitions:  {\n")
        sb.append("  Version: 100\n")
        sb.append("  Count: 1\n")
        sb.append("  ObjectType: \"Geometry\" {\n")
        sb.append("    Count: 1\n")
        sb.append("    PropertyTemplate: \"FbxMesh\" {}\n")
        sb.append("  }\n")
        sb.append("}\n")
        sb.append("Objects:  {\n")
        sb.append("  Geometry: ").append(geometryId).append(", \"Geometry::OctodrawMesh\", \"Mesh\" {\n")
        sb.append("    Vertices: *").append(vertexCount * 3).append(" {\n")
        sb.append("      a: ").append(vertices).append("\n")
        sb.append("    }\n")
        sb.append("    PolygonVertexIndex: *").append(polygonIndexCount).append(" {\n")
        sb.append("      a: ").append(indices).append("\n")
        sb.append("    }\n")
        sb.append("    GeometryVersion: 124\n")
        sb.append("  }\n")
        sb.append("  Model: ").append(modelId).append(", \"Model::OctodrawMesh\", \"Mesh\" {\n")
        sb.append("    Version: 232\n")
        sb.append("  }\n")
        sb.append("}\n")
        sb.append("Connections:  {\n")
        sb.append("  C: \"OO\",").append(geometryId).append(",").append(modelId).append("\n")
        sb.append("  C: \"OO\",").append(modelId).append(",0\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun writeGltf(triangles: List<Triangle>): ByteArray {
        val packed = packPositionsAndIndices(triangles)
        val json = buildGltfJson(
            vertexCount = packed.vertexCount,
            indexCount = packed.indexCount,
            bufferByteLength = packed.binary.size,
            indicesByteOffset = packed.indicesByteOffset,
            min = packed.min,
            max = packed.max,
            dataUri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(packed.binary)
        )
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    private fun writeGlb(triangles: List<Triangle>): ByteArray {
        val packed = packPositionsAndIndices(triangles)
        val jsonText = buildGltfJson(
            vertexCount = packed.vertexCount,
            indexCount = packed.indexCount,
            bufferByteLength = packed.binary.size,
            indicesByteOffset = packed.indicesByteOffset,
            min = packed.min,
            max = packed.max,
            dataUri = null
        )
        val jsonBytes = jsonText.toByteArray(StandardCharsets.UTF_8)
        val jsonPadded = padChunk(jsonBytes, 0x20)
        val binPadded = padChunk(packed.binary, 0x00)
        val totalLength = 12 + 8 + jsonPadded.size + 8 + binPadded.size
        val out = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67)
        out.putInt(2)
        out.putInt(totalLength)
        out.putInt(jsonPadded.size)
        out.putInt(0x4E4F534A)
        out.put(jsonPadded)
        out.putInt(binPadded.size)
        out.putInt(0x004E4942)
        out.put(binPadded)
        return out.array()
    }

    private data class PackedMesh(
        val binary: ByteArray,
        val vertexCount: Int,
        val indexCount: Int,
        val indicesByteOffset: Int,
        val min: Vector3,
        val max: Vector3
    )

    private fun packPositionsAndIndices(triangles: List<Triangle>): PackedMesh {
        val vertexCount = triangles.size * 3
        val indexCount = triangles.size * 3
        val positionBytes = vertexCount * 12
        val indicesOffset = align4(positionBytes)
        val indexBytes = indexCount * 4
        val totalBytes = indicesOffset + indexBytes
        val buffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        val minV = Vector3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val maxV = Vector3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        triangles.forEach { tri ->
            listOf(tri.a, tri.b, tri.c).forEach { v ->
                minV.x = min(minV.x, v.x)
                minV.y = min(minV.y, v.y)
                minV.z = min(minV.z, v.z)
                maxV.x = max(maxV.x, v.x)
                maxV.y = max(maxV.y, v.y)
                maxV.z = max(maxV.z, v.z)
                buffer.putFloat(v.x)
                buffer.putFloat(v.y)
                buffer.putFloat(v.z)
            }
        }
        while (buffer.position() < indicesOffset) {
            buffer.put(0)
        }
        for (i in 0 until indexCount) {
            buffer.putInt(i)
        }
        return PackedMesh(
            binary = buffer.array(),
            vertexCount = vertexCount,
            indexCount = indexCount,
            indicesByteOffset = indicesOffset,
            min = minV,
            max = maxV
        )
    }

    private fun buildGltfJson(
        vertexCount: Int,
        indexCount: Int,
        bufferByteLength: Int,
        indicesByteOffset: Int,
        min: Vector3,
        max: Vector3,
        dataUri: String?
    ): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"asset\":{\"version\":\"2.0\",\"generator\":\"Octodraw\"},")
        sb.append("\"scene\":0,")
        sb.append("\"scenes\":[{\"nodes\":[0]}],")
        sb.append("\"nodes\":[{\"mesh\":0}],")
        sb.append("\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"indices\":1}]}],")
        sb.append("\"accessors\":[")
        sb.append("{\"bufferView\":0,\"componentType\":5126,\"count\":").append(vertexCount)
            .append(",\"type\":\"VEC3\",\"min\":[")
            .append(fmt(min.x)).append(",").append(fmt(min.y)).append(",").append(fmt(min.z))
            .append("],\"max\":[")
            .append(fmt(max.x)).append(",").append(fmt(max.y)).append(",").append(fmt(max.z))
            .append("]},")
        sb.append("{\"bufferView\":1,\"componentType\":5125,\"count\":").append(indexCount)
            .append(",\"type\":\"SCALAR\"}")
        sb.append("],")
        sb.append("\"bufferViews\":[")
        sb.append("{\"buffer\":0,\"byteOffset\":0,\"byteLength\":").append(vertexCount * 12).append(",\"target\":34962},")
        sb.append("{\"buffer\":0,\"byteOffset\":").append(indicesByteOffset)
            .append(",\"byteLength\":").append(indexCount * 4).append(",\"target\":34963}")
        sb.append("],")
        sb.append("\"buffers\":[{")
        if (dataUri != null) {
            sb.append("\"uri\":\"").append(dataUri).append("\",")
        }
        sb.append("\"byteLength\":").append(bufferByteLength)
        sb.append("}]")
        sb.append("}")
        return sb.toString()
    }

    private fun writeCollada(triangles: List<Triangle>, unit: ExportUnit): String {
        val points = triangles.flatMap { tri -> listOf(tri.a, tri.b, tri.c) }
        val triCount = triangles.size
        val floatCount = points.size * 3
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<COLLADA xmlns=\"http://www.collada.org/2005/11/COLLADASchema\" version=\"1.4.1\">\n")
        sb.append("  <asset><unit name=\"")
            .append(escapeXmlAttribute(unit.colladaName))
            .append("\" meter=\"")
            .append(fmt(unit.metersPerUnit))
            .append("\"/><up_axis>Y_UP</up_axis></asset>\n")
        sb.append("  <library_geometries>\n")
        sb.append("    <geometry id=\"mesh0\" name=\"mesh0\"><mesh>\n")
        sb.append("      <source id=\"mesh0-positions\">\n")
        sb.append("        <float_array id=\"mesh0-positions-array\" count=\"").append(floatCount).append("\">")
        points.forEachIndexed { idx, v ->
            if (idx > 0) sb.append(' ')
            sb.append(fmt(v.x)).append(' ').append(fmt(v.y)).append(' ').append(fmt(v.z))
        }
        sb.append("</float_array>\n")
        sb.append("        <technique_common><accessor source=\"#mesh0-positions-array\" count=\"")
            .append(points.size)
            .append("\" stride=\"3\"><param name=\"X\" type=\"float\"/><param name=\"Y\" type=\"float\"/><param name=\"Z\" type=\"float\"/></accessor></technique_common>\n")
        sb.append("      </source>\n")
        sb.append("      <vertices id=\"mesh0-vertices\"><input semantic=\"POSITION\" source=\"#mesh0-positions\"/></vertices>\n")
        sb.append("      <triangles count=\"").append(triCount).append("\"><input semantic=\"VERTEX\" source=\"#mesh0-vertices\" offset=\"0\"/><p>")
        for (i in points.indices) {
            if (i > 0) sb.append(' ')
            sb.append(i)
        }
        sb.append("</p></triangles>\n")
        sb.append("    </mesh></geometry>\n")
        sb.append("  </library_geometries>\n")
        sb.append("  <library_visual_scenes><visual_scene id=\"Scene\" name=\"Scene\"><node id=\"mesh0-node\" name=\"mesh0-node\"><instance_geometry url=\"#mesh0\"/></node></visual_scene></library_visual_scenes>\n")
        sb.append("  <scene><instance_visual_scene url=\"#Scene\"/></scene>\n")
        sb.append("</COLLADA>\n")
        return sb.toString()
    }

    private fun writeDxf(triangles: List<Triangle>, segments: List<Segment>, unit: ExportUnit): String {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n\$INSUNITS\n70\n").append(unit.dxfInsUnits).append("\n")
        sb.append("0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")
        segments.forEach { segment ->
            sb.append("0\nLINE\n8\n0\n")
            sb.append("10\n").append(fmt(segment.start.x)).append("\n20\n").append(fmt(segment.start.y)).append("\n30\n").append(fmt(segment.start.z)).append("\n")
            sb.append("11\n").append(fmt(segment.end.x)).append("\n21\n").append(fmt(segment.end.y)).append("\n31\n").append(fmt(segment.end.z)).append("\n")
        }
        triangles.forEach { tri ->
            sb.append("0\n3DFACE\n8\n0\n")
            sb.append("10\n").append(fmt(tri.a.x)).append("\n20\n").append(fmt(tri.a.y)).append("\n30\n").append(fmt(tri.a.z)).append("\n")
            sb.append("11\n").append(fmt(tri.b.x)).append("\n21\n").append(fmt(tri.b.y)).append("\n31\n").append(fmt(tri.b.z)).append("\n")
            sb.append("12\n").append(fmt(tri.c.x)).append("\n22\n").append(fmt(tri.c.y)).append("\n32\n").append(fmt(tri.c.z)).append("\n")
            sb.append("13\n").append(fmt(tri.c.x)).append("\n23\n").append(fmt(tri.c.y)).append("\n33\n").append(fmt(tri.c.z)).append("\n")
        }
        sb.append("0\nENDSEC\n0\nEOF\n")
        return sb.toString()
    }

    private data class IndexedTriangle(val v1: Int, val v2: Int, val v3: Int)

    private data class IndexedMesh(
        val vertices: List<Vector3>,
        val triangles: List<IndexedTriangle>
    )

    private data class QuantizedVertexKey(val x: Long, val y: Long, val z: Long)

    private data class EdgeKey private constructor(val a: Int, val b: Int) {
        companion object {
            fun of(v1: Int, v2: Int): EdgeKey {
                return if (v1 <= v2) EdgeKey(v1, v2) else EdgeKey(v2, v1)
            }
        }
    }

    private data class EdgeUse(val triangleIndex: Int, val from: Int, val to: Int)

    private fun write3mf(triangles: List<Triangle>, settings: ThreeMfExportSettings): ByteArray {
        val scale = settings.coordinateScale.coerceAtLeast(1e-9f)
        val mesh = buildIndexedMesh(triangles, scale)
        validateThreeMfMesh(mesh)
        val unitAttribute = settings.unit?.let { " unit=\"${it.xmlValue}\"" }.orEmpty()
        val modelXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<model").append(unitAttribute).append(" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n")
            append("  <resources>\n")
            append("    <object id=\"1\" type=\"model\">\n")
            append("      <mesh>\n")
            append("        <vertices>\n")
            mesh.vertices.forEach { v ->
                append("          <vertex x=\"").append(fmt(v.x)).append("\" y=\"").append(fmt(v.y)).append("\" z=\"").append(fmt(v.z)).append("\"/>\n")
            }
            append("        </vertices>\n")
            append("        <triangles>\n")
            mesh.triangles.forEach { tri ->
                append("          <triangle v1=\"").append(tri.v1).append("\" v2=\"").append(tri.v2).append("\" v3=\"").append(tri.v3).append("\"/>\n")
            }
            append("        </triangles>\n")
            append("      </mesh>\n")
            append("    </object>\n")
            append("  </resources>\n")
            append("  <build><item objectid=\"1\"/></build>\n")
            append("</model>\n")
        }
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>
            </Types>
        """.trimIndent() + "\n"
        val rels = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
            </Relationships>
        """.trimIndent() + "\n"

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypes.toByteArray(StandardCharsets.UTF_8))
            writeZipEntry(zip, "_rels/.rels", rels.toByteArray(StandardCharsets.UTF_8))
            writeZipEntry(zip, "3D/3dmodel.model", modelXml.toByteArray(StandardCharsets.UTF_8))
        }
        return out.toByteArray()
    }

    private fun buildIndexedMesh(triangles: List<Triangle>, coordinateScale: Float): IndexedMesh {
        val vertices = ArrayList<Vector3>(triangles.size * 2)
        val indexedTriangles = ArrayList<IndexedTriangle>(triangles.size)
        val vertexLookup = HashMap<QuantizedVertexKey, Int>(triangles.size * 3)
        triangles.forEach { triangle ->
            val v1 = indexScaledVertex(triangle.a, coordinateScale, vertices, vertexLookup)
            val v2 = indexScaledVertex(triangle.b, coordinateScale, vertices, vertexLookup)
            val v3 = indexScaledVertex(triangle.c, coordinateScale, vertices, vertexLookup)
            indexedTriangles.add(IndexedTriangle(v1, v2, v3))
        }
        return IndexedMesh(vertices, indexedTriangles)
    }

    private fun indexScaledVertex(
        source: Vector3,
        coordinateScale: Float,
        vertices: MutableList<Vector3>,
        vertexLookup: MutableMap<QuantizedVertexKey, Int>
    ): Int {
        val scaledX = source.x * coordinateScale
        val scaledY = source.y * coordinateScale
        val scaledZ = source.z * coordinateScale
        val key = QuantizedVertexKey(
            quantizeCoordinate(scaledX),
            quantizeCoordinate(scaledY),
            quantizeCoordinate(scaledZ)
        )
        return vertexLookup.getOrPut(key) {
            val index = vertices.size
            vertices.add(Vector3(scaledX, scaledY, scaledZ))
            index
        }
    }

    private fun quantizeCoordinate(value: Float): Long {
        val step = 1e-5
        return kotlin.math.round(value.toDouble() / step).toLong()
    }

    private fun validateThreeMfMesh(mesh: IndexedMesh) {
        if (mesh.triangles.isEmpty()) {
            throw IllegalArgumentException("3MF export failed: no triangles to export.")
        }
        val areaToleranceSquared = 1e-10f
        val edgeUses = HashMap<EdgeKey, MutableList<EdgeUse>>(mesh.triangles.size * 2)
        val adjacency = Array(mesh.triangles.size) { mutableSetOf<Int>() }
        var degenerateCount = 0
        mesh.triangles.forEachIndexed { index, triangle ->
            if (triangle.v1 == triangle.v2 || triangle.v2 == triangle.v3 || triangle.v3 == triangle.v1) {
                degenerateCount++
            } else {
                val a = mesh.vertices[triangle.v1]
                val b = mesh.vertices[triangle.v2]
                val c = mesh.vertices[triangle.v3]
                val ab = Vector3(b).sub(a)
                val ac = Vector3(c).sub(a)
                if (ab.crs(ac).len2() <= areaToleranceSquared) {
                    degenerateCount++
                }
            }
            registerEdge(edgeUses, index, triangle.v1, triangle.v2)
            registerEdge(edgeUses, index, triangle.v2, triangle.v3)
            registerEdge(edgeUses, index, triangle.v3, triangle.v1)
        }
        if (degenerateCount > 0) {
            throw IllegalArgumentException("3MF export failed: mesh contains $degenerateCount degenerate triangle(s).")
        }

        var openEdgeCount = 0
        var nonManifoldEdgeCount = 0
        var windingMismatchCount = 0
        edgeUses.forEach { (_, uses) ->
            when (uses.size) {
                2 -> {
                    val first = uses[0]
                    val second = uses[1]
                    if (first.from != second.to || first.to != second.from) {
                        windingMismatchCount++
                    }
                    adjacency[first.triangleIndex].add(second.triangleIndex)
                    adjacency[second.triangleIndex].add(first.triangleIndex)
                }
                1 -> openEdgeCount++
                else -> nonManifoldEdgeCount++
            }
        }
        if (openEdgeCount > 0 || nonManifoldEdgeCount > 0) {
            val parts = ArrayList<String>(2)
            if (openEdgeCount > 0) {
                parts.add("$openEdgeCount open edge(s)")
            }
            if (nonManifoldEdgeCount > 0) {
                parts.add("$nonManifoldEdgeCount non-manifold edge(s)")
            }
            throw IllegalArgumentException("3MF export failed: mesh is not manifold (${parts.joinToString(", ")}).")
        }
        if (windingMismatchCount > 0) {
            throw IllegalArgumentException("3MF export failed: mesh winding is inconsistent across $windingMismatchCount shared edge(s).")
        }

        val volumeTolerance = 1e-6
        val visited = BooleanArray(mesh.triangles.size)
        for (start in mesh.triangles.indices) {
            if (visited[start]) {
                continue
            }
            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            visited[start] = true
            var signedVolume = 0.0
            while (stack.isNotEmpty()) {
                val triangleIndex = stack.removeLast()
                val triangle = mesh.triangles[triangleIndex]
                val a = mesh.vertices[triangle.v1]
                val b = mesh.vertices[triangle.v2]
                val c = mesh.vertices[triangle.v3]
                signedVolume += signedTetrahedronVolume(a, b, c)
                adjacency[triangleIndex].forEach { neighbor ->
                    if (!visited[neighbor]) {
                        visited[neighbor] = true
                        stack.addLast(neighbor)
                    }
                }
            }
            if (kotlin.math.abs(signedVolume) <= volumeTolerance) {
                throw IllegalArgumentException("3MF export failed: mesh contains a zero-volume shell.")
            }
            if (signedVolume < 0.0) {
                throw IllegalArgumentException("3MF export failed: mesh contains an inward-facing shell.")
            }
        }
    }

    private fun registerEdge(
        edgeUses: MutableMap<EdgeKey, MutableList<EdgeUse>>,
        triangleIndex: Int,
        from: Int,
        to: Int
    ) {
        edgeUses.getOrPut(EdgeKey.of(from, to)) { ArrayList(2) }
            .add(EdgeUse(triangleIndex, from, to))
    }

    private fun signedTetrahedronVolume(a: Vector3, b: Vector3, c: Vector3): Double {
        return (
            a.x.toDouble() * (b.y.toDouble() * c.z.toDouble() - b.z.toDouble() * c.y.toDouble()) -
                a.y.toDouble() * (b.x.toDouble() * c.z.toDouble() - b.z.toDouble() * c.x.toDouble()) +
                a.z.toDouble() * (b.x.toDouble() * c.y.toDouble() - b.y.toDouble() * c.x.toDouble())
            ) / 6.0
    }

    private fun writeAmf(triangles: List<Triangle>, unit: ExportUnit): String {
        val points = triangles.flatMap { tri -> listOf(tri.a, tri.b, tri.c) }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        val unitAttribute = unit.amfUnit?.let { " unit=\"${escapeXmlAttribute(it)}\"" }.orEmpty()
        sb.append("<amf").append(unitAttribute).append(">\n")
        sb.append("  <object id=\"0\"><mesh>\n")
        sb.append("    <vertices>\n")
        points.forEach { v ->
            sb.append("      <vertex><coordinates><x>").append(fmt(v.x)).append("</x><y>")
                .append(fmt(v.y)).append("</y><z>").append(fmt(v.z))
                .append("</z></coordinates></vertex>\n")
        }
        sb.append("    </vertices>\n")
        sb.append("    <volume>\n")
        for (i in triangles.indices) {
            val base = i * 3
            sb.append("      <triangle><v1>").append(base).append("</v1><v2>")
                .append(base + 1).append("</v2><v3>").append(base + 2).append("</v3></triangle>\n")
        }
        sb.append("    </volume>\n")
        sb.append("  </mesh></object>\n")
        sb.append("</amf>\n")
        return sb.toString()
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    private fun extractZipEntry(bytes: ByteArray, selector: (String) -> Boolean): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && selector(entry.name)) {
                    return zip.readBytes()
                }
            }
        }
        return null
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            builder.parse(ByteArrayInputStream(bytes))
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseDoubleList(text: String): List<Double> {
        val normalized = text
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('{', ' ')
            .replace('}', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
        return normalized.split(',', ' ').mapNotNull { token ->
            val cleaned = token.trim()
            if (cleaned.isEmpty()) null else cleaned.toDoubleOrNull()
        }
    }

    private fun parseIntList(text: String): List<Int> {
        val normalized = text
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('{', ' ')
            .replace('}', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
        return normalized.split(',', ' ').mapNotNull { token ->
            val cleaned = token.trim()
            if (cleaned.isEmpty()) null else cleaned.toIntOrNull()
        }
    }

    private fun extractRefs(text: String): List<Int> {
        val refs = ArrayList<Int>()
        var i = 0
        while (i < text.length) {
            val hash = text.indexOf('#', i)
            if (hash < 0) break
            var j = hash + 1
            while (j < text.length && text[j].isDigit()) j++
            text.substring(hash + 1, j).toIntOrNull()?.let { refs.add(it) }
            i = j
        }
        return refs
    }

    private fun extractIntTuples(text: String): List<List<Int>> {
        val pattern = Regex("\\((-?\\d+(?:\\s*,\\s*-?\\d+){2,})\\)")
        val tuples = ArrayList<List<Int>>()
        pattern.findAll(text).forEach { match ->
            val values = match.groupValues[1].split(',').mapNotNull { it.trim().toIntOrNull() }
            if (values.size >= 3) {
                tuples.add(values)
            }
        }
        return tuples
    }

    private fun extractDoubleTuples(text: String): List<List<Double>> {
        val tuples = ArrayList<List<Double>>()
        var i = 0
        while (i < text.length) {
            val open = text.indexOf('(', i)
            if (open < 0) break
            val close = text.indexOf(')', open + 1)
            if (close < 0) break
            val inner = text.substring(open + 1, close)
            val values = inner.split(',').mapNotNull { it.trim().toDoubleOrNull() }
            if (values.size >= 3) {
                tuples.add(values)
            }
            i = close + 1
        }
        return tuples
    }

    private fun parseFloatArrayText(text: String): FloatArray {
        val values = text.trim().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
        return FloatArray(values.size) { idx -> values[idx] }
    }

    private fun parseIntArrayText(text: String): IntArray {
        val values = text.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
        return IntArray(values.size) { idx -> values[idx] }
    }

    private fun vec3FromFloatArray(values: FloatArray, index: Int): Vector3? {
        val base = index * 3
        if (base + 2 >= values.size || index < 0) {
            return null
        }
        return Vector3(values[base], values[base + 1], values[base + 2])
    }

    private fun align4(value: Int): Int = (value + 3) and 0x7FFFFFFC.toInt()

    private fun padChunk(bytes: ByteArray, padByte: Int): ByteArray {
        val aligned = align4(bytes.size)
        if (aligned == bytes.size) {
            return bytes
        }
        val out = ByteArray(aligned)
        System.arraycopy(bytes, 0, out, 0, bytes.size)
        for (i in bytes.size until aligned) {
            out[i] = padByte.toByte()
        }
        return out
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

    private fun escapeXmlAttribute(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}

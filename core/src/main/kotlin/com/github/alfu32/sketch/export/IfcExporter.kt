package com.github.alfu32.sketch.export

import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

object IfcExporter {
    data class ExportReport(
        val productCount: Int,
        val triangleCount: Int,
        val lineCount: Int
    )

    private data class Triangle3(val a: Vector3, val b: Vector3, val c: Vector3)
    private data class Segment3(val start: Vector3, val end: Vector3)
    private data class MeshChunk(val name: String, val triangles: List<Triangle3>)
    private data class LineChunk(val name: String, val segments: List<Segment3>)
    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private class StepWriter {
        private var nextId = 1
        private val entities = mutableListOf<String>()

        fun add(definition: String): Int {
            val id = nextId++
            entities.add("#$id=$definition;")
            return id
        }

        fun all(): List<String> = entities
    }

    fun export(scene: GroupScene, file: File, unitScale: Float): ExportReport {
        val scale = if (unitScale.isFinite() && unitScale > 0f) unitScale.toDouble() else 1.0
        val (meshes, lines) = collectGeometry(scene, scale)
        val writer = StepWriter()
        val report = buildIfcData(writer, meshes, lines)
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val sb = StringBuilder()
        sb.appendLine("ISO-10303-21;")
        sb.appendLine("HEADER;")
        sb.appendLine("FILE_DESCRIPTION(('ViewDefinition [CoordinationView_V2.0]'),'2;1');")
        sb.appendLine("FILE_NAME('${escapeStep(file.name)}','$timestamp',('Octodraw'),('Octodraw'),'Codex','Octodraw','');")
        sb.appendLine("FILE_SCHEMA(('IFC4'));")
        sb.appendLine("ENDSEC;")
        sb.appendLine("DATA;")
        writer.all().forEach { sb.appendLine(it) }
        sb.appendLine("ENDSEC;")
        sb.appendLine("END-ISO-10303-21;")
        file.writeText(sb.toString())
        return report
    }

    private fun buildIfcData(
        writer: StepWriter,
        meshes: List<MeshChunk>,
        lines: List<LineChunk>
    ): ExportReport {
        val person = writer.add("IFCPERSON($,$,'Octodraw',$,$,$,$,$)")
        val org = writer.add("IFCORGANIZATION($,'Octodraw',$,$,$)")
        val personOrg = writer.add("IFCPERSONANDORGANIZATION(#$person,#$org,$)")
        val app = writer.add("IFCAPPLICATION(#$org,'3.x','Octodraw','OCTODRAW')")
        val owner = writer.add("IFCOWNERHISTORY(#$personOrg,#$app,$,.ADDED.,$,$,$,0)")

        val origin = writer.add("IFCCARTESIANPOINT((0.,0.,0.))")
        val dirZ = writer.add("IFCDIRECTION((0.,0.,1.))")
        val dirX = writer.add("IFCDIRECTION((1.,0.,0.))")
        val axis3d = writer.add("IFCAXIS2PLACEMENT3D(#$origin,#$dirZ,#$dirX)")
        val modelContext = writer.add("IFCGEOMETRICREPRESENTATIONCONTEXT($,'Model',3,1.E-5,#$axis3d,$)")
        val unitLength = writer.add("IFCSIUNIT(*,.LENGTHUNIT.,$,.METRE.)")
        val unitArea = writer.add("IFCSIUNIT(*,.AREAUNIT.,$,.SQUARE_METRE.)")
        val unitVolume = writer.add("IFCSIUNIT(*,.VOLUMEUNIT.,$,.CUBIC_METRE.)")
        val units = writer.add("IFCUNITASSIGNMENT((#$unitLength,#$unitArea,#$unitVolume))")

        val project = writer.add("IFCPROJECT('${ifcGuid()}',#$owner,'Octodraw Model',$,$,$,$,(#$modelContext),#$units)")

        val sitePlacement = writer.add("IFCLOCALPLACEMENT($,#$axis3d)")
        val buildingPlacement = writer.add("IFCLOCALPLACEMENT(#$sitePlacement,#$axis3d)")
        val storeyPlacement = writer.add("IFCLOCALPLACEMENT(#$buildingPlacement,#$axis3d)")
        val site = writer.add("IFCSITE('${ifcGuid()}',#$owner,'Site',$,$,#$sitePlacement,$,$,.ELEMENT.,$,$,$,$,$)")
        val building = writer.add("IFCBUILDING('${ifcGuid()}',#$owner,'Building',$,$,#$buildingPlacement,$,$,.ELEMENT.,$,$,$)")
        val storey = writer.add("IFCBUILDINGSTOREY('${ifcGuid()}',#$owner,'Storey',$,$,#$storeyPlacement,$,$,.ELEMENT.,0.)")
        writer.add("IFCRELAGGREGATES('${ifcGuid()}',#$owner,$,$,#$project,(#$site))")
        writer.add("IFCRELAGGREGATES('${ifcGuid()}',#$owner,$,$,#$site,(#$building))")
        writer.add("IFCRELAGGREGATES('${ifcGuid()}',#$owner,$,$,#$building,(#$storey))")

        val products = mutableListOf<Int>()
        var triangleCount = 0
        var lineCount = 0

        meshes.forEach { mesh ->
            if (mesh.triangles.isEmpty()) {
                return@forEach
            }
            triangleCount += mesh.triangles.size
            val product = addMeshProduct(writer, modelContext, owner, storeyPlacement, mesh)
            products.add(product)
        }
        lines.forEach { chunk ->
            if (chunk.segments.isEmpty()) {
                return@forEach
            }
            lineCount += chunk.segments.size
            val product = addLineProduct(writer, modelContext, owner, storeyPlacement, chunk)
            products.add(product)
        }

        if (products.isNotEmpty()) {
            val refs = products.joinToString(",") { "#$it" }
            writer.add("IFCRELCONTAINEDINSPATIALSTRUCTURE('${ifcGuid()}',#$owner,$,$,($refs),#$storey)")
        }

        return ExportReport(
            productCount = products.size,
            triangleCount = triangleCount,
            lineCount = lineCount
        )
    }

    private fun addMeshProduct(
        writer: StepWriter,
        contextId: Int,
        ownerId: Int,
        placementId: Int,
        mesh: MeshChunk
    ): Int {
        val vertexIndexByKey = linkedMapOf<VertexKey, Int>()
        val vertices = mutableListOf<Vector3>()
        val faces = mutableListOf<IntArray>()

        mesh.triangles.forEach { tri ->
            val ia = meshVertexIndex(vertexIndexByKey, vertices, tri.a)
            val ib = meshVertexIndex(vertexIndexByKey, vertices, tri.b)
            val ic = meshVertexIndex(vertexIndexByKey, vertices, tri.c)
            if (ia != ib && ib != ic && ic != ia) {
                faces.add(intArrayOf(ia, ib, ic))
            }
        }
        if (faces.isEmpty()) {
            return writer.add("IFCBUILDINGELEMENTPROXY('${ifcGuid()}',#$ownerId,${stepString(mesh.name)},$,$,#$placementId,$,$,$)")
        }

        val pointsText = vertices.joinToString(",") { point ->
            "(${fmt(point.x.toDouble())},${fmt(point.y.toDouble())},${fmt(point.z.toDouble())})"
        }
        val pointList = writer.add("IFCCARTESIANPOINTLIST3D(($pointsText))")
        val faceIndexText = faces.joinToString(",") { idx ->
            "(${idx[0]},${idx[1]},${idx[2]})"
        }
        val tessellation = writer.add("IFCTRIANGULATEDFACESET(#$pointList,$,.T.,($faceIndexText),$)")
        val shapeRep = writer.add("IFCSHAPEREPRESENTATION(#$contextId,'Body','Tessellation',(#$tessellation))")
        val productShape = writer.add("IFCPRODUCTDEFINITIONSHAPE($,$,(#$shapeRep))")
        return writer.add("IFCBUILDINGELEMENTPROXY('${ifcGuid()}',#$ownerId,${stepString(mesh.name)},$,$,#$placementId,#$productShape,$,$)")
    }

    private fun addLineProduct(
        writer: StepWriter,
        contextId: Int,
        ownerId: Int,
        placementId: Int,
        chunk: LineChunk
    ): Int {
        val polylineIds = mutableListOf<Int>()
        chunk.segments.forEach { segment ->
            if (segment.start.epsilonEquals(segment.end, 1e-6f)) {
                return@forEach
            }
            val p0 = writer.add(
                "IFCCARTESIANPOINT((" +
                    "${fmt(segment.start.x.toDouble())},${fmt(segment.start.y.toDouble())},${fmt(segment.start.z.toDouble())}" +
                    "))"
            )
            val p1 = writer.add(
                "IFCCARTESIANPOINT((" +
                    "${fmt(segment.end.x.toDouble())},${fmt(segment.end.y.toDouble())},${fmt(segment.end.z.toDouble())}" +
                    "))"
            )
            polylineIds.add(writer.add("IFCPOLYLINE((#$p0,#$p1))"))
        }
        if (polylineIds.isEmpty()) {
            return writer.add("IFCBUILDINGELEMENTPROXY('${ifcGuid()}',#$ownerId,${stepString(chunk.name)},$,$,#$placementId,$,$,$)")
        }
        val items = polylineIds.joinToString(",") { "#$it" }
        val shapeRep = writer.add("IFCSHAPEREPRESENTATION(#$contextId,'Axis','Curve3D',($items))")
        val productShape = writer.add("IFCPRODUCTDEFINITIONSHAPE($,$,(#$shapeRep))")
        return writer.add("IFCBUILDINGELEMENTPROXY('${ifcGuid()}',#$ownerId,${stepString(chunk.name)},$,$,#$placementId,#$productShape,$,$)")
    }

    private fun meshVertexIndex(
        indexByKey: MutableMap<VertexKey, Int>,
        vertices: MutableList<Vector3>,
        point: Vector3
    ): Int {
        val key = VertexKey(
            x = (point.x * 1_000_000f).roundToInt(),
            y = (point.y * 1_000_000f).roundToInt(),
            z = (point.z * 1_000_000f).roundToInt()
        )
        val existing = indexByKey[key]
        if (existing != null) {
            return existing
        }
        vertices.add(Vector3(point))
        val index = vertices.size
        indexByKey[key] = index
        return index
    }

    private fun collectGeometry(scene: GroupScene, unitScale: Double): Pair<List<MeshChunk>, List<LineChunk>> {
        val meshes = mutableListOf<MeshChunk>()
        val lines = mutableListOf<LineChunk>()
        val groups = mutableListOf<GroupScene.GroupNode>()
        groups.add(scene.root)
        scene.walkGroups(scene.root) { group -> groups.add(group) }

        groups.forEach { group ->
            val baseName = when (group.kind) {
                GroupScene.PrototypeKind.ARCHITECTURE -> "Architecture: ${group.name}"
                GroupScene.PrototypeKind.VOXEL -> "Voxel: ${group.name}"
                GroupScene.PrototypeKind.MESH -> if (group === scene.root) "Model" else "Object: ${group.name}"
            }
            val groupTriangles = group.faceStore.getTriangles().map { tri ->
                Triangle3(
                    scale(group.toWorld(tri.a), unitScale),
                    scale(group.toWorld(tri.b), unitScale),
                    scale(group.toWorld(tri.c), unitScale)
                )
            }
            if (groupTriangles.isNotEmpty()) {
                meshes.add(MeshChunk("$baseName Mesh", groupTriangles))
            }
            val groupLines = group.lineStore.getSegments().map { seg ->
                Segment3(
                    scale(group.toWorld(seg.start), unitScale),
                    scale(group.toWorld(seg.end), unitScale)
                )
            }
            if (groupLines.isNotEmpty()) {
                lines.add(LineChunk("$baseName Lines", groupLines))
            }
        }

        val dimensionSegments = mutableListOf<Segment3>()
        scene.collectWorldDimensions { start, end, offset, _ ->
            val a = scale(start, unitScale)
            val b = scale(end, unitScale)
            val o = scale(offset, unitScale)
            dimensionSegments.add(Segment3(a, b))
            dimensionSegments.add(Segment3(a, o))
            dimensionSegments.add(Segment3(b, o))
        }
        if (dimensionSegments.isNotEmpty()) {
            lines.add(LineChunk("Dimensions", dimensionSegments))
        }

        return meshes to lines
    }

    private fun scale(point: Vector3, unitScale: Double): Vector3 {
        return Vector3(
            (point.x.toDouble() * unitScale).toFloat(),
            (point.y.toDouble() * unitScale).toFloat(),
            (point.z.toDouble() * unitScale).toFloat()
        )
    }

    private fun fmt(value: Double): String {
        if (!value.isFinite() || abs(value) < 1e-12) {
            return "0."
        }
        val raw = String.format(java.util.Locale.US, "%.9f", value)
        val trimmed = raw.trimEnd('0').trimEnd('.')
        return if (trimmed.contains('.')) trimmed else "$trimmed."
    }

    private fun stepString(value: String): String {
        return "'${escapeStep(value)}'"
    }

    private fun escapeStep(value: String): String {
        return value.replace("'", "''")
    }

    private fun ifcGuid(): String {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$"
        val random = Random(UUID.randomUUID().mostSignificantBits xor UUID.randomUUID().leastSignificantBits)
        return buildString(22) {
            repeat(22) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }
}

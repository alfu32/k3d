package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class PushPullTool(
    private val scene: GroupScene,
    private val camera: com.badlogic.gdx.graphics.Camera
) : Tool {
    override val id: ToolId = ToolId.PUSH_PULL
    override val message: String = "Click face to start push/pull."

    private var activeTriangles: List<DraftFaceStore.Triangle> = emptyList()
    private var anchorPointLocal: Vector3? = null
    private var normalLocal: Vector3? = null
    private var currentDistance = 0f
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Click face to start push/pull."
    }

    override fun onExit(status: StatusModel) {
        clearTransient()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clearTransient()
        status.message = "Canceled."
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        val anchor = anchorPointLocal
        val faceNormal = this.normalLocal
        val group = scene.activeGroup()
        if (anchor != null && faceNormal != null && valid && world != null) {
            val localWorld = group.toLocal(world)
            currentDistance = Vector3(localWorld).sub(anchor).dot(faceNormal)
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean, button: Int): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        val group = scene.activeGroup()
        if (anchorPointLocal == null) {
            val ray = camera.getPickRay(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            val localRay = toLocalRay(group, ray)
            val hit = group.faceStore.pickTriangle(localRay) ?: return false
            val faceNormal = facingNormal(hit.normal, localRay.direction)
            val coplanar = group.faceStore.collectCoplanarConnected(hit.triangle)
            activeTriangles = if (coplanar.isNotEmpty()) coplanar else listOf(hit.triangle)
            anchorPointLocal = Vector3(hit.point)
            this.normalLocal = faceNormal
            status.message = "Drag to extrude. Click to commit."
            return true
        }
        val anchor = anchorPointLocal ?: return false
        val faceNormal = this.normalLocal ?: return false
        if (hasHover && valid && world != null) {
            val localWorld = group.toLocal(world)
            currentDistance = Vector3(localWorld).sub(anchor).dot(faceNormal)
        }
        commitExtrusion(faceNormal, currentDistance)
        clearTransient()
        status.message = "Click face to start push/pull."
        return true
    }

    override fun render(renderer: ShapeRenderer) {
        if (!hasHover || activeTriangles.isEmpty()) {
            return
        }
        val faceNormal = normalLocal ?: return
        val group = scene.activeGroup()
        renderer.color = Color(0.95f, 0.75f, 0.25f, 1f)
        val offset = Vector3(faceNormal).scl(currentDistance)
        val boundaryEdges = collectBoundaryEdges(activeTriangles)
        val verticalKeys = mutableSetOf<VertexKey>()
        boundaryEdges.forEach { edge ->
            val aLocal = edge.from
            val bLocal = edge.to
            val apLocal = Vector3(aLocal).add(offset)
            val bpLocal = Vector3(bLocal).add(offset)
            val a = group.toWorld(aLocal)
            val b = group.toWorld(bLocal)
            val ap = group.toWorld(apLocal)
            val bp = group.toWorld(bpLocal)
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            renderer.line(ap.x, ap.y, ap.z, bp.x, bp.y, bp.z)
            val aKey = vertexKey(a)
            val bKey = vertexKey(b)
            if (verticalKeys.add(aKey)) {
                renderer.line(a.x, a.y, a.z, ap.x, ap.y, ap.z)
            }
            if (verticalKeys.add(bKey)) {
                renderer.line(b.x, b.y, b.z, bp.x, bp.y, bp.z)
            }
        }
    }

    private fun commitExtrusion(faceNormal: Vector3, distance: Float) {
        if (activeTriangles.isEmpty() || kotlin.math.abs(distance) <= 1e-4f) {
            return
        }
        val group = scene.activeGroup()
        val offset = Vector3(faceNormal).scl(distance)
        val reverse = distance < 0f
        val boundaryEdges = collectBoundaryEdges(activeTriangles)
        activeTriangles.forEach { tri ->
            val a = Vector3(tri.a)
            val b = Vector3(tri.b)
            val c = Vector3(tri.c)
            val ap = Vector3(a).add(offset)
            val bp = Vector3(b).add(offset)
            val cp = Vector3(c).add(offset)

            if (!reverse) {
                group.faceStore.addTriangle(ap, bp, cp)
            } else {
                group.faceStore.addTriangle(ap, cp, bp)
            }
        }
        val verticalKeys = mutableSetOf<VertexKey>()
        boundaryEdges.forEach { edge ->
            val a = edge.from
            val b = edge.to
            val ap = Vector3(a).add(offset)
            val bp = Vector3(b).add(offset)
            addSideQuad(a, b, ap, bp, reverse)
            group.lineStore.addSegment(ap, bp)
            val aKey = vertexKey(a)
            val bKey = vertexKey(b)
            if (verticalKeys.add(aKey)) {
                group.lineStore.addSegment(a, ap)
            }
            if (verticalKeys.add(bKey)) {
                group.lineStore.addSegment(b, bp)
            }
        }
    }

    private fun addSideQuad(a: Vector3, b: Vector3, ap: Vector3, bp: Vector3, reverse: Boolean) {
        if (!reverse) {
            scene.activeGroup().faceStore.addTriangle(a, b, bp)
            scene.activeGroup().faceStore.addTriangle(a, bp, ap)
        } else {
            scene.activeGroup().faceStore.addTriangle(a, bp, b)
            scene.activeGroup().faceStore.addTriangle(a, ap, bp)
        }
    }

    private fun clearTransient() {
        activeTriangles = emptyList()
        anchorPointLocal = null
        normalLocal = null
        currentDistance = 0f
        hasHover = false
    }

    private fun facingNormal(normal: Vector3, rayDir: Vector3): Vector3 {
        return if (normal.dot(rayDir) > 0f) Vector3(normal).scl(-1f) else Vector3(normal)
    }

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private data class EdgeKey(val a: VertexKey, val b: VertexKey)

    private data class DirectedEdge(val from: Vector3, val to: Vector3)

    private fun collectBoundaryEdges(triangles: List<DraftFaceStore.Triangle>): List<DirectedEdge> {
        val edgeCount = mutableMapOf<EdgeKey, Int>()
        val edgeDirs = mutableMapOf<EdgeKey, DirectedEdge>()
        triangles.forEach { tri ->
            val edges = listOf(
                Pair(tri.a, tri.b),
                Pair(tri.b, tri.c),
                Pair(tri.c, tri.a)
            )
            edges.forEach { (from, to) ->
                val key = edgeKey(from, to)
                edgeCount[key] = (edgeCount[key] ?: 0) + 1
                if (!edgeDirs.containsKey(key)) {
                    edgeDirs[key] = DirectedEdge(Vector3(from), Vector3(to))
                }
            }
        }
        return edgeCount.filterValues { it == 1 }.keys.mapNotNull { key -> edgeDirs[key] }
    }

    private fun edgeKey(a: Vector3, b: Vector3): EdgeKey {
        val va = vertexKey(a)
        val vb = vertexKey(b)
        return if (compareKeys(va, vb) <= 0) EdgeKey(va, vb) else EdgeKey(vb, va)
    }

    private fun vertexKey(point: Vector3): VertexKey {
        val eps = 1e-3f
        fun quant(value: Float): Int = kotlin.math.round(value / eps).toInt()
        return VertexKey(quant(point.x), quant(point.y), quant(point.z))
    }

    private fun compareKeys(a: VertexKey, b: VertexKey): Int {
        if (a.x != b.x) return a.x.compareTo(b.x)
        if (a.y != b.y) return a.y.compareTo(b.y)
        return a.z.compareTo(b.z)
    }

    private fun toLocalRay(
        group: GroupScene.GroupNode,
        ray: com.badlogic.gdx.math.collision.Ray
    ): com.badlogic.gdx.math.collision.Ray {
        val originLocal = group.toLocal(ray.origin)
        val dirLocal = group.vectorToLocal(ray.direction).nor()
        return com.badlogic.gdx.math.collision.Ray(originLocal, dirLocal)
    }
}

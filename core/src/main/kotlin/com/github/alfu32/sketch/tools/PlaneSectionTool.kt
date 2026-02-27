package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolMeasurement

class PlaneSectionTool(
    private val scene: GroupScene,
    private val done: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.PLANE_SECTION
    override val message: String = "Pick plane origin point."

    private val planeEpsilon = 1e-4f
    private val dedupeEpsilon = 1e-3f
    private var originWorld: Vector3? = null
    private val hoverWorld = Vector3()
    private var hasHover = false

    override fun onEnter(status: StatusModel) {
        status.message = "Pick plane origin point."
    }

    override fun onExit(status: StatusModel) {
        clearTransient()
        super.onExit(status)
    }

    override fun onCancel(status: StatusModel) {
        clearTransient()
        status.message = "Plane section canceled."
        done()
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        if (valid && world != null) {
            hoverWorld.set(world)
            hasHover = true
        } else {
            hasHover = false
        }
    }

    override fun onPointerDown(
        status: StatusModel,
        world: Vector3?,
        normal: Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }

        val origin = originWorld
        if (origin == null) {
            originWorld = Vector3(world)
            status.message = "Pick second point to define plane normal."
            return true
        }

        return finalizeSection(status, origin, Vector3(world))
    }

    override fun onKeyDown(status: StatusModel, keycode: Int): Boolean {
        return when (keycode) {
            Input.Keys.ESCAPE -> {
                onCancel(status)
                true
            }
            Input.Keys.ENTER -> {
                val origin = originWorld
                if (origin != null && hasHover) {
                    finalizeSection(status, origin, Vector3(hoverWorld))
                } else {
                    false
                }
            }
            else -> false
        }
    }

    override fun measurement(status: StatusModel): ToolMeasurement? {
        val origin = originWorld ?: return null
        if (!hasHover) {
            return null
        }
        return ToolMeasurement(
            startWorld = Vector3(origin),
            endWorld = Vector3(hoverWorld)
        )
    }

    override fun render(renderer: ShapeRenderer) {
        val origin = originWorld ?: return
        if (!hasHover) {
            return
        }
        renderer.color = ToolFeedbackColors.SECONDARY
        renderer.line(origin.x, origin.y, origin.z, hoverWorld.x, hoverWorld.y, hoverWorld.z)
    }

    private fun finalizeSection(status: StatusModel, planePoint: Vector3, normalPoint: Vector3): Boolean {
        val planeNormal = Vector3(normalPoint).sub(planePoint)
        if (planeNormal.len2() <= planeEpsilon * planeEpsilon) {
            status.message = "Plane normal is too small. Pick a different second point."
            return true
        }
        planeNormal.nor()

        val rawSegments = mutableListOf<MeshIntersectionMath.Segment3>()
        collectIntersections(planePoint, planeNormal, rawSegments)

        val segments = MeshIntersectionMath.dedupeSegments(rawSegments, dedupeEpsilon)
        if (segments.isEmpty()) {
            clearTransient()
            status.message = "Plane section produced no intersection."
            done()
            return true
        }

        val targetGroup = scene.activeGroup()
        val lineStore = targetGroup.lineStore
        val before = lineStore.getSegments().toSet()

        lineStore.withChangeSuppressed {
            segments.forEach { segment ->
                val startLocal = targetGroup.toLocal(segment.start)
                val endLocal = targetGroup.toLocal(segment.end)
                lineStore.addSegment(startLocal, endLocal, autoCleanup = false)
            }
        }
        lineStore.notifyExternalChange()

        val addedSegments = lineStore.getSegments().filter { it !in before }
        if (addedSegments.isEmpty()) {
            clearTransient()
            status.message = "Plane section generated no new edges."
            done()
            return true
        }

        isolateSegmentsAsGroup(targetGroup, addedSegments, "Plane Section")
        clearTransient()
        status.message = "Plane section created | segments ${addedSegments.size}"
        done()
        return true
    }

    private fun collectIntersections(
        planePoint: Vector3,
        planeNormal: Vector3,
        outSegments: MutableList<MeshIntersectionMath.Segment3>
    ) {
        processGroup(scene.root, planePoint, planeNormal, outSegments, isRoot = true)
        scene.walkGroups(scene.root) { group ->
            processGroup(group, planePoint, planeNormal, outSegments, isRoot = false)
        }
    }

    private fun processGroup(
        group: GroupScene.GroupNode,
        planePoint: Vector3,
        planeNormal: Vector3,
        outSegments: MutableList<MeshIntersectionMath.Segment3>,
        isRoot: Boolean
    ) {
        val triangles = group.faceStore.getTriangles()
        if (triangles.isEmpty()) {
            return
        }

        val worldMatrix = if (isRoot) null else group.worldMatrix()
        triangles.forEach { triangle ->
            val worldTriangle = if (worldMatrix == null) {
                MeshIntersectionMath.Triangle3(triangle.a, triangle.b, triangle.c)
            } else {
                MeshIntersectionMath.Triangle3(
                    transformPoint(worldMatrix, triangle.a),
                    transformPoint(worldMatrix, triangle.b),
                    transformPoint(worldMatrix, triangle.c)
                )
            }
            val cut = MeshIntersectionMath.intersectTrianglePlaneNormalized(
                worldTriangle,
                planePoint,
                planeNormal,
                planeEpsilon
            )
            if (cut != null) {
                outSegments.add(cut)
            }
        }
    }

    private fun isolateSegmentsAsGroup(
        group: GroupScene.GroupNode,
        segments: List<DraftLineStore.Segment>,
        groupName: String
    ) {
        scene.clearGroupSelection()
        group.lineStore.clearSelection()
        group.faceStore.clearSelection()
        group.dimensionStore.clearSelection()
        group.textStore.clearSelection()
        group.voxelStore?.clearSelection()

        segments.forEach { group.lineStore.addSelection(it) }
        val created = scene.createGroupFromSelection() ?: return
        created.name = groupName
        scene.clearGroupSelection()
        scene.addGroupSelection(created)
    }

    private fun transformPoint(matrix: Matrix4, point: Vector3): Vector3 {
        val v = matrix.`val`
        val x = point.x
        val y = point.y
        val z = point.z
        return Vector3(
            x * v[Matrix4.M00] + y * v[Matrix4.M01] + z * v[Matrix4.M02] + v[Matrix4.M03],
            x * v[Matrix4.M10] + y * v[Matrix4.M11] + z * v[Matrix4.M12] + v[Matrix4.M13],
            x * v[Matrix4.M20] + y * v[Matrix4.M21] + z * v[Matrix4.M22] + v[Matrix4.M23]
        )
    }

    private fun clearTransient() {
        originWorld = null
        hasHover = false
    }
}

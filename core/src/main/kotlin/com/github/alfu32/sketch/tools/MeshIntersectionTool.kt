package com.github.alfu32.sketch.tools

import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class MeshIntersectionTool(
    private val scene: GroupScene,
    private val done: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.MESH_INTERSECTION
    override val message: String = "Select exactly 2 mesh groups, then run Mesh Intersection."

    private val intersectionEpsilon = 1e-4f
    private val dedupeEpsilon = 1e-3f

    override fun onEnter(status: StatusModel) {
        status.message = "Mesh intersection..."

        val selectedGroups = scene.selectedGroups().toList()
        if (selectedGroups.size != 2) {
            status.message = "Select exactly 2 groups."
            done()
            return
        }

        val groupA = selectedGroups[0]
        val groupB = selectedGroups[1]

        if (scene.isVoxelGroup(groupA) || scene.isVoxelGroup(groupB)) {
            status.message = "Mesh intersection currently supports mesh groups only."
            done()
            return
        }

        val facesA = groupA.faceStore.getTriangles().toList()
        val facesB = groupB.faceStore.getTriangles().toList()
        if (facesA.isEmpty() || facesB.isEmpty()) {
            status.message = "Both groups must have faces."
            done()
            return
        }

        val rawSegments = mutableListOf<MeshIntersectionMath.Segment3>()
        facesA.forEach { triA ->
            val worldA = MeshIntersectionMath.Triangle3(
                groupA.toWorld(triA.a),
                groupA.toWorld(triA.b),
                groupA.toWorld(triA.c)
            )
            facesB.forEach { triB ->
                val worldB = MeshIntersectionMath.Triangle3(
                    groupB.toWorld(triB.a),
                    groupB.toWorld(triB.b),
                    groupB.toWorld(triB.c)
                )
                val segment = MeshIntersectionMath.intersectTriangles(worldA, worldB, intersectionEpsilon)
                if (segment != null) {
                    rawSegments.add(segment)
                }
            }
        }

        val intersectionSegments = MeshIntersectionMath.dedupeSegments(rawSegments, dedupeEpsilon)
        if (intersectionSegments.isEmpty()) {
            status.message = "No mesh intersection detected."
            done()
            return
        }

        var cutsA = 0
        var cutsB = 0
        intersectionSegments.forEach { segment ->
            val a0 = groupA.toLocal(segment.start)
            val a1 = groupA.toLocal(segment.end)
            cutsA += groupA.faceStore.cutBySegmentInPlane(a0, a1)

            val b0 = groupB.toLocal(segment.start)
            val b1 = groupB.toLocal(segment.end)
            cutsB += groupB.faceStore.cutBySegmentInPlane(b0, b1)
        }

        val targetGroup = scene.activeGroup()
        val lineStore = targetGroup.lineStore
        val before = lineStore.getSegments().toSet()

        lineStore.withChangeSuppressed {
            intersectionSegments.forEach { segment ->
                val startLocal = targetGroup.toLocal(segment.start)
                val endLocal = targetGroup.toLocal(segment.end)
                lineStore.addSegment(startLocal, endLocal, autoCleanup = false)
            }
        }
        lineStore.notifyExternalChange()

        val addedSegments = lineStore.getSegments().filter { it !in before }
        if (addedSegments.isNotEmpty()) {
            isolateSegmentsAsGroup(targetGroup, addedSegments, "Mesh Intersection")
        }

        status.message =
            "Mesh intersection | lines ${intersectionSegments.size} | cuts A:$cutsA B:$cutsB"
        done()
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
}

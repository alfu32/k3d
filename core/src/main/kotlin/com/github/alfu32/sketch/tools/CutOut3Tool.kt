package com.github.alfu32.sketch.tools

import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class CutOut3Tool(
    private val scene: GroupScene,
    private val done: () -> Unit
) : Tool {
    override val id: ToolId = ToolId.CUT_OUT_3
    override val message: String = "Cut selected faces by selected edges (Cut Out 3)."

    override fun onEnter(status: StatusModel) {
        status.message = "Cut Out 3..."
        val group = scene.activeGroup()
        val faces = group.faceStore.getSelected().toList()
        val segments = group.lineStore.getSelected().toList()
        if (faces.isEmpty() || segments.isEmpty()) {
            status.message = "Select faces and edges first."
            done()
            return
        }

        val input = faces.map { tri ->
            CutOut3.Triangle(
                CutOut3.Vec3(tri.a.x.toDouble(), tri.a.y.toDouble(), tri.a.z.toDouble()),
                CutOut3.Vec3(tri.b.x.toDouble(), tri.b.y.toDouble(), tri.b.z.toDouble()),
                CutOut3.Vec3(tri.c.x.toDouble(), tri.c.y.toDouble(), tri.c.z.toDouble())
            )
        }
        val cutters = segments.map { seg ->
            CutOut3.Segment(
                CutOut3.Vec3(seg.start.x.toDouble(), seg.start.y.toDouble(), seg.start.z.toDouble()),
                CutOut3.Vec3(seg.end.x.toDouble(), seg.end.y.toDouble(), seg.end.z.toDouble())
            )
        }

        val result = CutOut3.cutTriangles(input, cutters, eps = 1e-3, consolidate = false)
        if (result.isEmpty()) {
            status.message = "Cut Out 3 produced no triangles."
            done()
            return
        }

        val store = group.faceStore
        val color = store.colorFor(faces.first())
        val before = store.getTriangles().toSet()
        store.deleteTriangles(faces)
        result.forEach { tri ->
            store.addTriangle(
                Vector3(tri.a.x.toFloat(), tri.a.y.toFloat(), tri.a.z.toFloat()),
                Vector3(tri.b.x.toFloat(), tri.b.y.toFloat(), tri.b.z.toFloat()),
                Vector3(tri.c.x.toFloat(), tri.c.y.toFloat(), tri.c.z.toFloat()),
                color
            )
        }
        val added = store.getTriangles().filter { it !in before }
        store.clearSelection()
        added.forEach { store.addSelection(it) }

        status.message = "Cut Out 3 | triangles ${result.size}"
        done()
    }
}

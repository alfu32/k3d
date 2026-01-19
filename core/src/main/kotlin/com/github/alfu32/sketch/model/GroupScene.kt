package com.github.alfu32.sketch.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

class GroupScene(
    val defaultFaceColor: Color
) {
    data class Axes(val u: Vector3, val v: Vector3, val w: Vector3)

    class GroupNode(
        val id: String,
        var name: String,
        var definitionOrigin: Vector3,
        var definitionAxisU: Vector3,
        var definitionAxisV: Vector3,
        var definitionAxisW: Vector3,
        var instanceOrigin: Vector3,
        var instanceAxisU: Vector3,
        var instanceAxisV: Vector3,
        var instanceAxisW: Vector3,
        var gluedToSurface: Boolean,
        val lineStore: DraftLineStore,
        val faceStore: DraftFaceStore
    ) {
        val children: MutableList<GroupNode> = mutableListOf()
        var parent: GroupNode? = null

        fun worldAxes(): Axes {
            val matrix = worldMatrix()
            return axesFromMatrix(matrix)
        }

        fun worldOrigin(): Vector3 {
            val matrix = worldMatrix()
            val v = matrix.`val`
            return Vector3(v[Matrix4.M03], v[Matrix4.M13], v[Matrix4.M23])
        }

        fun toWorld(local: Vector3): Vector3 {
            return transformPoint(worldMatrix(), local)
        }

        fun toLocal(world: Vector3): Vector3 {
            val inv = worldMatrix().inv()
            return transformPoint(inv, world)
        }

        fun vectorToWorld(local: Vector3): Vector3 {
            return transformVector(worldMatrix(), local)
        }

        fun vectorToLocal(world: Vector3): Vector3 {
            val inv = worldMatrix().inv()
            return transformVector(inv, world)
        }

        fun setInstanceFromWorld(originWorld: Vector3, worldU: Vector3, worldV: Vector3, worldW: Vector3) {
            val parentMatrix = parent?.worldMatrix() ?: Matrix4().idt()
            val desiredWorld = matrixFromAxes(originWorld, Axes(worldU, worldV, worldW))
            val defMatrix = definitionMatrix()
            val invParent = Matrix4(parentMatrix).inv()
            val invDef = Matrix4(defMatrix).inv()
            val instance = Matrix4(invParent).mul(desiredWorld).mul(invDef)
            setInstanceFromMatrix(instance)
        }

        fun definitionMatrix(): Matrix4 {
            return matrixFromAxes(definitionOrigin, Axes(definitionAxisU, definitionAxisV, definitionAxisW))
        }

        fun instanceMatrix(): Matrix4 {
            return matrixFromAxes(instanceOrigin, Axes(instanceAxisU, instanceAxisV, instanceAxisW))
        }

        fun worldMatrix(): Matrix4 {
            val parentMatrix = parent?.worldMatrix() ?: Matrix4().idt()
            val result = Matrix4(parentMatrix)
            result.mul(instanceMatrix())
            result.mul(definitionMatrix())
            return result
        }

        fun worldBounds(): BoundingBox? {
            val bounds = BoundingBox()
            var hasAny = false
            lineStore.getSegments().forEach { seg ->
                val a = toWorld(seg.start)
                val b = toWorld(seg.end)
                if (!hasAny) {
                    bounds.set(a, a)
                    hasAny = true
                }
                bounds.ext(a)
                bounds.ext(b)
            }
            faceStore.getTriangles().forEach { tri ->
                val a = toWorld(tri.a)
                val b = toWorld(tri.b)
                val c = toWorld(tri.c)
                if (!hasAny) {
                    bounds.set(a, a)
                    hasAny = true
                }
                bounds.ext(a)
                bounds.ext(b)
                bounds.ext(c)
            }
            children.forEach { child ->
                val childBounds = child.worldBounds()
                if (childBounds != null) {
                    if (!hasAny) {
                        bounds.set(childBounds)
                        hasAny = true
                    } else {
                        bounds.ext(childBounds)
                    }
                }
            }
            return if (hasAny) bounds else null
        }

        fun localBounds(): BoundingBox? {
            val bounds = BoundingBox()
            var hasAny = false
            lineStore.getSegments().forEach { seg ->
                if (!hasAny) {
                    bounds.set(seg.start, seg.start)
                    hasAny = true
                }
                bounds.ext(seg.start)
                bounds.ext(seg.end)
            }
            faceStore.getTriangles().forEach { tri ->
                if (!hasAny) {
                    bounds.set(tri.a, tri.a)
                    hasAny = true
                }
                bounds.ext(tri.a)
                bounds.ext(tri.b)
                bounds.ext(tri.c)
            }
            children.forEach { child ->
                val childBounds = child.worldBounds()
                if (childBounds != null) {
                    val corners = arrayOf(
                        Vector3(childBounds.min.x, childBounds.min.y, childBounds.min.z),
                        Vector3(childBounds.min.x, childBounds.min.y, childBounds.max.z),
                        Vector3(childBounds.min.x, childBounds.max.y, childBounds.min.z),
                        Vector3(childBounds.min.x, childBounds.max.y, childBounds.max.z),
                        Vector3(childBounds.max.x, childBounds.min.y, childBounds.min.z),
                        Vector3(childBounds.max.x, childBounds.min.y, childBounds.max.z),
                        Vector3(childBounds.max.x, childBounds.max.y, childBounds.min.z),
                        Vector3(childBounds.max.x, childBounds.max.y, childBounds.max.z)
                    )
                    corners.forEach { corner ->
                        val local = toLocal(corner)
                        if (!hasAny) {
                            bounds.set(local, local)
                            hasAny = true
                        }
                        bounds.ext(local)
                    }
                }
            }
            return if (hasAny) bounds else null
        }

        fun orientedBoundsCorners(): Array<Vector3>? {
            val bounds = localBounds() ?: return null
            val min = bounds.min
            val max = bounds.max
            val corners = arrayOf(
                Vector3(min.x, min.y, min.z),
                Vector3(max.x, min.y, min.z),
                Vector3(max.x, min.y, max.z),
                Vector3(min.x, min.y, max.z),
                Vector3(min.x, max.y, min.z),
                Vector3(max.x, max.y, min.z),
                Vector3(max.x, max.y, max.z),
                Vector3(min.x, max.y, max.z)
            )
            corners.indices.forEach { idx ->
                corners[idx] = toWorld(corners[idx])
            }
            return corners
        }

        private fun setInstanceFromMatrix(matrix: Matrix4) {
            val v = matrix.`val`
            instanceAxisU.set(v[Matrix4.M00], v[Matrix4.M10], v[Matrix4.M20])
            instanceAxisV.set(v[Matrix4.M01], v[Matrix4.M11], v[Matrix4.M21])
            instanceAxisW.set(v[Matrix4.M02], v[Matrix4.M12], v[Matrix4.M22])
            instanceOrigin.set(v[Matrix4.M03], v[Matrix4.M13], v[Matrix4.M23])
        }
    }

    val root: GroupNode = GroupNode(
        id = "root",
        name = "Root",
        definitionOrigin = Vector3(),
        definitionAxisU = Vector3(1f, 0f, 0f),
        definitionAxisV = Vector3(0f, 1f, 0f),
        definitionAxisW = Vector3(0f, 0f, 1f),
        instanceOrigin = Vector3(),
        instanceAxisU = Vector3(1f, 0f, 0f),
        instanceAxisV = Vector3(0f, 1f, 0f),
        instanceAxisW = Vector3(0f, 0f, 1f),
        gluedToSurface = false,
        lineStore = DraftLineStore(),
        faceStore = DraftFaceStore(defaultFaceColor)
    )
    private val selectedGroups = mutableSetOf<GroupNode>()
    private var activeGroup: GroupNode = root
    private var changeListener: (() -> Unit)? = null

    fun activeGroup(): GroupNode = activeGroup

    fun isEditing(): Boolean = activeGroup != root

    fun resetActiveGroup() {
        activeGroup = root
    }

    fun setChangeListener(listener: () -> Unit) {
        changeListener = listener
        applyChangeListener(root)
        walkGroups(root) { group ->
            applyChangeListener(group)
        }
    }

    fun applyChangeListenerToAll() {
        val listener = changeListener ?: return
        applyChangeListener(root)
        walkGroups(root) { group ->
            applyChangeListener(group)
        }
    }

    fun enterGroup(group: GroupNode): Boolean {
        if (group == activeGroup) {
            return false
        }
        activeGroup = group
        clearGroupSelection()
        return true
    }

    fun exitGroup(): Boolean {
        val parent = activeGroup.parent ?: return false
        activeGroup = parent
        clearGroupSelection()
        return true
    }

    fun clearGroupSelection() {
        selectedGroups.clear()
    }

    fun selectedGroups(): Set<GroupNode> = selectedGroups

    fun toggleGroupSelection(group: GroupNode) {
        if (!selectedGroups.add(group)) {
            selectedGroups.remove(group)
        }
    }

    fun addGroupSelection(group: GroupNode): Boolean = selectedGroups.add(group)

    fun removeGroupSelection(group: GroupNode) {
        selectedGroups.remove(group)
    }

    fun clearAllSelections() {
        root.lineStore.clearSelection()
        root.faceStore.clearSelection()
        walkGroups(root) { group ->
            group.lineStore.clearSelection()
            group.faceStore.clearSelection()
        }
        clearGroupSelection()
    }

    fun groupsInActiveContext(): List<GroupNode> {
        return activeGroup.children.toList()
    }

    fun createGroupFromSelection(): GroupNode? {
        val parent = activeGroup
        val selectedEdges = parent.lineStore.getSelected().toList()
        val selectedFaces = parent.faceStore.getSelected().toList()
        val selectedChildren = selectedGroups.filter { it.parent == parent }
        if (selectedEdges.isEmpty() && selectedFaces.isEmpty() && selectedChildren.isEmpty()) {
            return null
        }
        val bounds = BoundingBox()
        var hasBounds = false
        selectedEdges.forEach { seg ->
            if (!hasBounds) {
                bounds.set(seg.start, seg.start)
                hasBounds = true
            }
            bounds.ext(seg.start)
            bounds.ext(seg.end)
        }
        selectedFaces.forEach { tri ->
            if (!hasBounds) {
                bounds.set(tri.a, tri.a)
                hasBounds = true
            }
            bounds.ext(tri.a)
            bounds.ext(tri.b)
            bounds.ext(tri.c)
        }
        selectedChildren.forEach { child ->
            val childBounds = child.worldBounds()
            if (childBounds != null) {
                val parentSpace = boundsInParent(childBounds, parent)
                if (parentSpace != null) {
                    if (!hasBounds) {
                        bounds.set(parentSpace)
                        hasBounds = true
                    } else {
                        bounds.ext(parentSpace)
                    }
                }
            }
        }
        if (!hasBounds) {
            return null
        }
        val origin = Vector3(bounds.min)
        val group = GroupNode(
            id = java.util.UUID.randomUUID().toString(),
            name = "Group",
            definitionOrigin = Vector3(),
            definitionAxisU = Vector3(1f, 0f, 0f),
            definitionAxisV = Vector3(0f, 1f, 0f),
            definitionAxisW = Vector3(0f, 0f, 1f),
            instanceOrigin = origin,
            instanceAxisU = Vector3(1f, 0f, 0f),
            instanceAxisV = Vector3(0f, 1f, 0f),
            instanceAxisW = Vector3(0f, 0f, 1f),
            gluedToSurface = false,
            lineStore = DraftLineStore(),
            faceStore = DraftFaceStore(defaultFaceColor)
        )
        group.parent = parent
        parent.children.add(group)
        applyChangeListener(group)

        if (selectedEdges.isNotEmpty()) {
            parent.lineStore.deleteSelected()
            selectedEdges.forEach { seg ->
                val a = Vector3(seg.start).sub(origin)
                val b = Vector3(seg.end).sub(origin)
                group.lineStore.addSegment(a, b)
            }
        }
        if (selectedFaces.isNotEmpty()) {
            val colors = selectedFaces.associateWith { face -> parent.faceStore.colorFor(face) }
            parent.faceStore.deleteSelected()
            selectedFaces.forEach { tri ->
                val a = Vector3(tri.a).sub(origin)
                val b = Vector3(tri.b).sub(origin)
                val c = Vector3(tri.c).sub(origin)
                group.faceStore.addTriangle(a, b, c, colors[tri] ?: defaultFaceColor)
            }
        }
        if (selectedChildren.isNotEmpty()) {
            selectedChildren.forEach { child ->
                parent.children.remove(child)
                reparentGroup(child, group)
            }
            clearGroupSelection()
        }
        applyChangeListenerToAll()
        notifyChange()
        return group
    }

    fun ungroupSelected(): Int {
        val targets = selectedGroups.toList()
        if (targets.isEmpty()) {
            return 0
        }
        var count = 0
        targets.forEach { group ->
            val parent = group.parent ?: return@forEach
            group.lineStore.getSegments().forEach { seg ->
                val a = toParentSpace(group, seg.start)
                val b = toParentSpace(group, seg.end)
                parent.lineStore.addSegment(a, b)
            }
            group.faceStore.getTriangles().forEach { tri ->
                val color = group.faceStore.colorFor(tri)
                val a = toParentSpace(group, tri.a)
                val b = toParentSpace(group, tri.b)
                val c = toParentSpace(group, tri.c)
                parent.faceStore.addTriangle(a, b, c, color)
            }
            group.children.forEach { child ->
                reparentGroup(child, parent)
            }
            parent.children.remove(group)
            count++
        }
        clearGroupSelection()
        applyChangeListenerToAll()
        notifyChange()
        return count
    }

    fun deleteSelectedGroups(): Int {
        val targets = selectedGroups.toList()
        if (targets.isEmpty()) {
            return 0
        }
        targets.forEach { group ->
            group.parent?.children?.remove(group)
        }
        clearGroupSelection()
        applyChangeListenerToAll()
        notifyChange()
        return targets.size
    }

    fun transformSelectedGroups(
        pointTransform: (Vector3) -> Vector3,
        vectorTransform: (Vector3) -> Vector3
    ): Int {
        val targets = selectedGroups.toList()
        if (targets.isEmpty()) {
            return 0
        }
        targets.forEach { group ->
            val newOriginWorld = pointTransform(group.worldOrigin())
            val worldAxes = group.worldAxes()
            val newU = vectorTransform(worldAxes.u)
            val newV = vectorTransform(worldAxes.v)
            val newW = vectorTransform(worldAxes.w)
            val parent = group.parent
            if (parent == null) {
                group.setInstanceFromWorld(newOriginWorld, newU, newV, newW)
            } else {
                group.setInstanceFromWorld(newOriginWorld, newU, newV, newW)
            }
        }
        applyChangeListenerToAll()
        notifyChange()
        return targets.size
    }

    fun copySelectedGroups(
        pointTransform: (Vector3) -> Vector3,
        vectorTransform: (Vector3) -> Vector3
    ): Int {
        val targets = selectedGroups.toList()
        if (targets.isEmpty()) {
            return 0
        }
        clearGroupSelection()
        targets.forEach { group ->
            val clone = cloneGroup(group)
            val newOriginWorld = pointTransform(group.worldOrigin())
            val worldAxes = group.worldAxes()
            val newU = vectorTransform(worldAxes.u)
            val newV = vectorTransform(worldAxes.v)
            val newW = vectorTransform(worldAxes.w)
            val parent = group.parent
            if (parent == null) {
                clone.parent = root
                root.children.add(clone)
            } else {
                clone.parent = parent
                parent.children.add(clone)
            }
            clone.setInstanceFromWorld(newOriginWorld, newU, newV, newW)
            selectedGroups.add(clone)
        }
        applyChangeListenerToAll()
        notifyChange()
        return targets.size
    }

    fun walkGroups(rootNode: GroupNode = root, visitor: (GroupNode) -> Unit) {
        rootNode.children.forEach { child ->
            visitor(child)
            walkGroups(child, visitor)
        }
    }

    fun collectWorldTriangles(consumer: (Vector3, Vector3, Vector3, Color) -> Unit) {
        walkGroups(root) { group ->
            group.faceStore.getTriangles().forEach { tri ->
                val a = group.toWorld(tri.a)
                val b = group.toWorld(tri.b)
                val c = group.toWorld(tri.c)
                val color = group.faceStore.colorFor(tri)
                consumer(a, b, c, color)
            }
        }
        root.faceStore.getTriangles().forEach { tri ->
            val color = root.faceStore.colorFor(tri)
            consumer(Vector3(tri.a), Vector3(tri.b), Vector3(tri.c), color)
        }
    }

    fun collectWorldLines(consumer: (Vector3, Vector3) -> Unit) {
        walkGroups(root) { group ->
            group.lineStore.getSegments().forEach { seg ->
                consumer(group.toWorld(seg.start), group.toWorld(seg.end))
            }
        }
        root.lineStore.getSegments().forEach { seg ->
            consumer(Vector3(seg.start), Vector3(seg.end))
        }
    }

    private fun cloneGroup(group: GroupNode): GroupNode {
        val clone = GroupNode(
            id = java.util.UUID.randomUUID().toString(),
            name = group.name,
            definitionOrigin = Vector3(group.definitionOrigin),
            definitionAxisU = Vector3(group.definitionAxisU),
            definitionAxisV = Vector3(group.definitionAxisV),
            definitionAxisW = Vector3(group.definitionAxisW),
            instanceOrigin = Vector3(group.instanceOrigin),
            instanceAxisU = Vector3(group.instanceAxisU),
            instanceAxisV = Vector3(group.instanceAxisV),
            instanceAxisW = Vector3(group.instanceAxisW),
            gluedToSurface = group.gluedToSurface,
            lineStore = DraftLineStore(),
            faceStore = DraftFaceStore(defaultFaceColor)
        )
        group.lineStore.getSegments().forEach { seg ->
            clone.lineStore.addSegment(seg.start, seg.end)
        }
        group.faceStore.getTriangles().forEach { tri ->
            val color = group.faceStore.colorFor(tri)
            clone.faceStore.addTriangle(tri.a, tri.b, tri.c, color)
        }
        group.children.forEach { child ->
            val childClone = cloneGroup(child)
            childClone.parent = clone
            clone.children.add(childClone)
        }
        applyChangeListener(clone)
        return clone
    }

    private fun boundsInParent(bounds: BoundingBox, parent: GroupNode): BoundingBox? {
        val corners = arrayOf(
            Vector3(bounds.min.x, bounds.min.y, bounds.min.z),
            Vector3(bounds.min.x, bounds.min.y, bounds.max.z),
            Vector3(bounds.min.x, bounds.max.y, bounds.min.z),
            Vector3(bounds.min.x, bounds.max.y, bounds.max.z),
            Vector3(bounds.max.x, bounds.min.y, bounds.min.z),
            Vector3(bounds.max.x, bounds.min.y, bounds.max.z),
            Vector3(bounds.max.x, bounds.max.y, bounds.min.z),
            Vector3(bounds.max.x, bounds.max.y, bounds.max.z)
        )
        val out = BoundingBox()
        var hasAny = false
        corners.forEach { corner ->
            val local = parent.toLocal(corner)
            if (!hasAny) {
                out.set(local, local)
                hasAny = true
            }
            out.ext(local)
        }
        return if (hasAny) out else null
    }

    private fun toParentSpace(group: GroupNode, local: Vector3): Vector3 {
        val matrix = Matrix4(group.instanceMatrix()).mul(group.definitionMatrix())
        return transformPoint(matrix, local)
    }

    private fun toParentVector(group: GroupNode, local: Vector3): Vector3 {
        val matrix = Matrix4(group.instanceMatrix()).mul(group.definitionMatrix())
        return transformVector(matrix, local)
    }

    private fun reparentGroup(child: GroupNode, newParent: GroupNode) {
        val childWorld = child.worldMatrix()
        val parentWorld = newParent.worldMatrix()
        val invParent = Matrix4(parentWorld).inv()
        val defMatrix = child.definitionMatrix()
        val invDef = Matrix4(defMatrix).inv()
        val localInstance = Matrix4(invParent).mul(childWorld).mul(invDef)
        child.instanceOrigin.set(
            localInstance.`val`[Matrix4.M03],
            localInstance.`val`[Matrix4.M13],
            localInstance.`val`[Matrix4.M23]
        )
        child.instanceAxisU.set(
            localInstance.`val`[Matrix4.M00],
            localInstance.`val`[Matrix4.M10],
            localInstance.`val`[Matrix4.M20]
        )
        child.instanceAxisV.set(
            localInstance.`val`[Matrix4.M01],
            localInstance.`val`[Matrix4.M11],
            localInstance.`val`[Matrix4.M21]
        )
        child.instanceAxisW.set(
            localInstance.`val`[Matrix4.M02],
            localInstance.`val`[Matrix4.M12],
            localInstance.`val`[Matrix4.M22]
        )
        child.parent = newParent
        newParent.children.add(child)
    }

    private fun applyChangeListener(group: GroupNode) {
        val listener = changeListener ?: return
        group.lineStore.setChangeListener(listener)
        group.faceStore.setChangeListener(listener)
    }

    private fun notifyChange() {
        changeListener?.invoke()
    }

    companion object {
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

        private fun transformVector(matrix: Matrix4, vector: Vector3): Vector3 {
            val v = matrix.`val`
            val x = vector.x
            val y = vector.y
            val z = vector.z
            return Vector3(
                x * v[Matrix4.M00] + y * v[Matrix4.M01] + z * v[Matrix4.M02],
                x * v[Matrix4.M10] + y * v[Matrix4.M11] + z * v[Matrix4.M12],
                x * v[Matrix4.M20] + y * v[Matrix4.M21] + z * v[Matrix4.M22]
            )
        }

        private fun matrixFromAxes(origin: Vector3, axes: Axes): Matrix4 {
            val matrix = Matrix4()
            val v = matrix.`val`
            v[Matrix4.M00] = axes.u.x
            v[Matrix4.M10] = axes.u.y
            v[Matrix4.M20] = axes.u.z
            v[Matrix4.M30] = 0f

            v[Matrix4.M01] = axes.v.x
            v[Matrix4.M11] = axes.v.y
            v[Matrix4.M21] = axes.v.z
            v[Matrix4.M31] = 0f

            v[Matrix4.M02] = axes.w.x
            v[Matrix4.M12] = axes.w.y
            v[Matrix4.M22] = axes.w.z
            v[Matrix4.M32] = 0f

            v[Matrix4.M03] = origin.x
            v[Matrix4.M13] = origin.y
            v[Matrix4.M23] = origin.z
            v[Matrix4.M33] = 1f
            return matrix
        }

        private fun axesFromMatrix(matrix: Matrix4): Axes {
            val v = matrix.`val`
            val u = Vector3(v[Matrix4.M00], v[Matrix4.M10], v[Matrix4.M20])
            val vAxis = Vector3(v[Matrix4.M01], v[Matrix4.M11], v[Matrix4.M21])
            val w = Vector3(v[Matrix4.M02], v[Matrix4.M12], v[Matrix4.M22])
            return Axes(u, vAxis, w)
        }
    }
}

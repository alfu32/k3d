package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3

class DraftDimensionStore {
    data class LinearDimension(
        val id: String,
        val start: Vector3,
        val end: Vector3,
        val offset: Vector3
    )

    private val dimensions = mutableListOf<LinearDimension>()
    private val selectedIds = mutableSetOf<String>()
    private var changeListener: (() -> Unit)? = null

    fun setChangeListener(listener: (() -> Unit)?) {
        changeListener = listener
    }

    fun getDimensions(): List<LinearDimension> = dimensions

    fun addDimension(start: Vector3, end: Vector3, offset: Vector3): LinearDimension {
        val dimension = LinearDimension(
            id = java.util.UUID.randomUUID().toString(),
            start = Vector3(start),
            end = Vector3(end),
            offset = Vector3(offset)
        )
        dimensions.add(dimension)
        notifyChange()
        return dimension
    }

    fun removeDimension(dimension: LinearDimension): Boolean {
        val removed = dimensions.remove(dimension)
        if (removed) {
            selectedIds.remove(dimension.id)
            notifyChange()
        }
        return removed
    }

    fun clearAll() {
        dimensions.clear()
        selectedIds.clear()
        notifyChange()
    }

    fun getSelected(): List<LinearDimension> {
        if (selectedIds.isEmpty()) {
            return emptyList()
        }
        return dimensions.filter { selectedIds.contains(it.id) }
    }

    fun isSelected(dimension: LinearDimension): Boolean = selectedIds.contains(dimension.id)

    fun addSelection(dimension: LinearDimension): Boolean {
        val added = selectedIds.add(dimension.id)
        if (added) {
            notifyChange()
        }
        return added
    }

    fun removeSelection(dimension: LinearDimension) {
        if (selectedIds.remove(dimension.id)) {
            notifyChange()
        }
    }

    fun toggleSelection(dimension: LinearDimension) {
        if (!selectedIds.add(dimension.id)) {
            selectedIds.remove(dimension.id)
        }
        notifyChange()
    }

    fun clearSelection() {
        if (selectedIds.isNotEmpty()) {
            selectedIds.clear()
            notifyChange()
        }
    }

    fun deleteSelected(): Int {
        if (selectedIds.isEmpty()) {
            return 0
        }
        val before = dimensions.size
        dimensions.removeAll { selectedIds.contains(it.id) }
        val removed = before - dimensions.size
        selectedIds.clear()
        if (removed > 0) {
            notifyChange()
        }
        return removed
    }

    fun transformSelected(transform: (Vector3) -> Vector3): Int {
        if (selectedIds.isEmpty()) {
            return 0
        }
        var count = 0
        dimensions.forEach { dimension ->
            if (selectedIds.contains(dimension.id)) {
                dimension.start.set(transform(Vector3(dimension.start)))
                dimension.end.set(transform(Vector3(dimension.end)))
                dimension.offset.set(transform(Vector3(dimension.offset)))
                count++
            }
        }
        if (count > 0) {
            notifyChange()
        }
        return count
    }

    fun copySelected(transform: (Vector3) -> Vector3): Int {
        if (selectedIds.isEmpty()) {
            return 0
        }
        val original = dimensions.filter { selectedIds.contains(it.id) }
        selectedIds.clear()
        var count = 0
        original.forEach { dimension ->
            val copy = addDimension(
                transform(Vector3(dimension.start)),
                transform(Vector3(dimension.end)),
                transform(Vector3(dimension.offset))
            )
            selectedIds.add(copy.id)
            count++
        }
        if (count > 0) {
            notifyChange()
        }
        return count
    }

    private fun notifyChange() {
        changeListener?.invoke()
    }
}

package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3

class DraftTextStore {
    data class TextEntity(
        val id: String,
        val position: Vector3,
        var text: String,
        var size: Float,
        val normal: Vector3,
        val axisU: Vector3,
        var screenText: Boolean
    )

    private val texts = mutableListOf<TextEntity>()
    private val selectedIds = mutableSetOf<String>()
    private var changeListener: (() -> Unit)? = null

    fun setChangeListener(listener: (() -> Unit)?) {
        changeListener = listener
    }

    fun getTexts(): List<TextEntity> = texts

    fun addText(
        position: Vector3,
        text: String,
        size: Float = 0.1f,
        normal: Vector3 = Vector3(0f, 1f, 0f),
        axisU: Vector3 = Vector3(1f, 0f, 0f),
        screenText: Boolean = true
    ): TextEntity {
        val entity = TextEntity(
            id = java.util.UUID.randomUUID().toString(),
            position = Vector3(position),
            text = text,
            size = size,
            normal = Vector3(normal).nor(),
            axisU = Vector3(axisU).nor(),
            screenText = screenText
        )
        texts.add(entity)
        notifyChange()
        return entity
    }

    fun removeText(entity: TextEntity): Boolean {
        val removed = texts.remove(entity)
        if (removed) {
            selectedIds.remove(entity.id)
            notifyChange()
        }
        return removed
    }

    fun clearAll() {
        texts.clear()
        selectedIds.clear()
        notifyChange()
    }

    fun getSelected(): List<TextEntity> {
        if (selectedIds.isEmpty()) {
            return emptyList()
        }
        return texts.filter { selectedIds.contains(it.id) }
    }

    fun isSelected(entity: TextEntity): Boolean = selectedIds.contains(entity.id)

    fun addSelection(entity: TextEntity): Boolean {
        val added = selectedIds.add(entity.id)
        if (added) {
            notifyChange()
        }
        return added
    }

    fun removeSelection(entity: TextEntity) {
        if (selectedIds.remove(entity.id)) {
            notifyChange()
        }
    }

    fun toggleSelection(entity: TextEntity) {
        if (!selectedIds.add(entity.id)) {
            selectedIds.remove(entity.id)
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
        val before = texts.size
        texts.removeAll { selectedIds.contains(it.id) }
        val removed = before - texts.size
        selectedIds.clear()
        if (removed > 0) {
            notifyChange()
        }
        return removed
    }

    fun updateText(id: String, text: String): Boolean {
        val entity = texts.firstOrNull { it.id == id } ?: return false
        if (entity.text == text) {
            return false
        }
        entity.text = text
        notifyChange()
        return true
    }

    fun updateSize(id: String, size: Float): Boolean {
        val entity = texts.firstOrNull { it.id == id } ?: return false
        if (entity.size == size) {
            return false
        }
        entity.size = size
        notifyChange()
        return true
    }

    fun updateScreenText(id: String, screenText: Boolean): Boolean {
        val entity = texts.firstOrNull { it.id == id } ?: return false
        if (entity.screenText == screenText) {
            return false
        }
        entity.screenText = screenText
        notifyChange()
        return true
    }

    fun transformSelected(transform: (Vector3) -> Vector3): Int {
        if (selectedIds.isEmpty()) {
            return 0
        }
        var count = 0
        texts.forEach { text ->
            if (selectedIds.contains(text.id)) {
                text.position.set(transform(Vector3(text.position)))
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
        val original = texts.filter { selectedIds.contains(it.id) }
        selectedIds.clear()
        var count = 0
        original.forEach { text ->
            val copy = addText(
                transform(Vector3(text.position)),
                text.text,
                text.size,
                text.normal,
                text.axisU,
                text.screenText
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

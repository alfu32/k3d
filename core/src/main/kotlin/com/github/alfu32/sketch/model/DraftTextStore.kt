package com.github.alfu32.sketch.model

import com.badlogic.gdx.math.Vector3

class DraftTextStore {
    enum class Kind {
        BITMAP,
        VECTOR
    }

    data class TextEntity(
        val id: String,
        val position: Vector3,
        var text: String,
        var size: Float,
        val normal: Vector3,
        val axisU: Vector3,
        var screenText: Boolean,
        var kind: Kind = Kind.BITMAP,
        var tracking: Float = 0.1f,
        var lineSpacing: Float = 1.25f,
        var glyphSourcePath: String = "embedded:alphabet"
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
        screenText: Boolean = true,
        kind: Kind = Kind.BITMAP,
        tracking: Float = 0.1f,
        lineSpacing: Float = 1.25f,
        glyphSourcePath: String = "embedded:alphabet"
    ): TextEntity {
        val normalizedKind = kind
        val normalizedTracking = tracking.coerceAtLeast(0f)
        val normalizedLineSpacing = lineSpacing.coerceAtLeast(0.1f)
        val normalizedGlyphSource = glyphSourcePath.trim().ifBlank { "embedded:alphabet" }
        val entity = TextEntity(
            id = java.util.UUID.randomUUID().toString(),
            position = Vector3(position),
            text = text,
            size = size,
            normal = Vector3(normal).nor(),
            axisU = Vector3(axisU).nor(),
            screenText = if (normalizedKind == Kind.VECTOR) false else screenText,
            kind = normalizedKind,
            tracking = normalizedTracking,
            lineSpacing = normalizedLineSpacing,
            glyphSourcePath = normalizedGlyphSource
        )
        texts.add(entity)
        notifyChange()
        return entity
    }

    fun addVectorText(
        position: Vector3,
        text: String,
        size: Float = 1f,
        normal: Vector3 = Vector3(0f, 1f, 0f),
        axisU: Vector3 = Vector3(1f, 0f, 0f),
        tracking: Float = 0.1f,
        lineSpacing: Float = 1.25f,
        glyphSourcePath: String = "embedded:alphabet"
    ): TextEntity {
        return addText(
            position = position,
            text = text,
            size = size,
            normal = normal,
            axisU = axisU,
            screenText = false,
            kind = Kind.VECTOR,
            tracking = tracking,
            lineSpacing = lineSpacing,
            glyphSourcePath = glyphSourcePath
        )
    }

    fun removeText(entity: TextEntity): Boolean {
        val removed = texts.remove(entity)
        if (removed) {
            selectedIds.remove(entity.id)
            notifyChange()
        }
        return removed
    }

    fun removeTexts(entities: Collection<TextEntity>): Int {
        if (entities.isEmpty()) {
            return 0
        }
        val ids = entities.mapTo(mutableSetOf()) { it.id }
        if (ids.isEmpty()) {
            return 0
        }
        val before = texts.size
        texts.removeAll { ids.contains(it.id) }
        val removed = before - texts.size
        if (removed > 0) {
            selectedIds.removeAll(ids)
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
        val target = if (entity.kind == Kind.VECTOR) false else screenText
        if (entity.screenText == target) {
            return false
        }
        entity.screenText = target
        notifyChange()
        return true
    }

    fun updateTracking(id: String, tracking: Float): Boolean {
        val entity = texts.firstOrNull { it.id == id } ?: return false
        if (entity.kind != Kind.VECTOR) {
            return false
        }
        val value = tracking.coerceAtLeast(0f)
        if (entity.tracking == value) {
            return false
        }
        entity.tracking = value
        notifyChange()
        return true
    }

    fun updateLineSpacing(id: String, lineSpacing: Float): Boolean {
        val entity = texts.firstOrNull { it.id == id } ?: return false
        if (entity.kind != Kind.VECTOR) {
            return false
        }
        val value = lineSpacing.coerceAtLeast(0.1f)
        if (entity.lineSpacing == value) {
            return false
        }
        entity.lineSpacing = value
        notifyChange()
        return true
    }

    fun updateGlyphSourcePath(id: String, glyphSourcePath: String): Boolean {
        val entity = texts.firstOrNull { it.id == id } ?: return false
        if (entity.kind != Kind.VECTOR) {
            return false
        }
        val value = glyphSourcePath.trim().ifBlank { "embedded:alphabet" }
        if (entity.glyphSourcePath == value) {
            return false
        }
        entity.glyphSourcePath = value
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

    fun transformSelectedAdvanced(
        pointTransform: (Vector3) -> Vector3,
        vectorTransform: ((Vector3) -> Vector3)? = null,
        sizeTransform: ((Float) -> Float)? = null
    ): Int {
        if (selectedIds.isEmpty()) {
            return 0
        }
        var count = 0
        texts.forEach { text ->
            if (!selectedIds.contains(text.id)) {
                return@forEach
            }
            text.position.set(pointTransform(Vector3(text.position)))
            if (vectorTransform != null) {
                val nextAxisU = vectorTransform(Vector3(text.axisU))
                if (nextAxisU.len2() > 1e-8f) {
                    text.axisU.set(nextAxisU)
                }
                val nextNormal = vectorTransform(Vector3(text.normal))
                if (nextNormal.len2() > 1e-8f) {
                    text.normal.set(nextNormal)
                }
                stabilizeBasis(text)
            }
            if (sizeTransform != null) {
                val nextSize = sizeTransform(text.size)
                text.size = if (text.kind == Kind.VECTOR) {
                    nextSize.coerceAtLeast(1e-3f)
                } else {
                    nextSize
                }
            }
            count++
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
                text.screenText,
                text.kind,
                text.tracking,
                text.lineSpacing,
                text.glyphSourcePath
            )
            selectedIds.add(copy.id)
            count++
        }
        if (count > 0) {
            notifyChange()
        }
        return count
    }

    fun copySelectedAdvanced(
        pointTransform: (Vector3) -> Vector3,
        vectorTransform: ((Vector3) -> Vector3)? = null,
        sizeTransform: ((Float) -> Float)? = null
    ): Int {
        if (selectedIds.isEmpty()) {
            return 0
        }
        val original = texts.filter { selectedIds.contains(it.id) }
        selectedIds.clear()
        var count = 0
        original.forEach { text ->
            val transformedNormal = if (vectorTransform != null) vectorTransform(Vector3(text.normal)) else Vector3(text.normal)
            val transformedAxisU = if (vectorTransform != null) vectorTransform(Vector3(text.axisU)) else Vector3(text.axisU)
            val copy = addText(
                position = pointTransform(Vector3(text.position)),
                text = text.text,
                size = if (sizeTransform != null) sizeTransform(text.size) else text.size,
                normal = transformedNormal,
                axisU = transformedAxisU,
                screenText = text.screenText,
                kind = text.kind,
                tracking = text.tracking,
                lineSpacing = text.lineSpacing,
                glyphSourcePath = text.glyphSourcePath
            )
            if (vectorTransform != null) {
                copy.normal.set(transformedNormal)
                copy.axisU.set(transformedAxisU)
                stabilizeBasis(copy)
            }
            if (text.kind == Kind.VECTOR) {
                copy.size = copy.size.coerceAtLeast(1e-3f)
            }
            selectedIds.add(copy.id)
            count++
        }
        if (count > 0) {
            notifyChange()
        }
        return count
    }

    private fun stabilizeBasis(text: TextEntity) {
        val eps = 1e-8f
        val axisU = Vector3(text.axisU)
        if (axisU.len2() <= eps) {
            axisU.set(1f, 0f, 0f)
        }
        val normal = Vector3(text.normal)
        if (normal.len2() <= eps) {
            normal.set(0f, 1f, 0f)
        }
        if (Vector3(axisU).crs(normal).len2() <= eps) {
            val desiredLen = normal.len().coerceAtLeast(1f)
            var perpendicular = if (kotlin.math.abs(axisU.y) < 0.9f) {
                Vector3(0f, 1f, 0f).crs(axisU)
            } else {
                Vector3(1f, 0f, 0f).crs(axisU)
            }
            if (perpendicular.len2() <= eps) {
                perpendicular = Vector3(0f, 0f, 1f).crs(axisU)
            }
            if (perpendicular.len2() <= eps) {
                perpendicular = Vector3(0f, 1f, 0f)
            }
            perpendicular.nor().scl(desiredLen)
            normal.set(perpendicular)
        }
        text.axisU.set(axisU)
        text.normal.set(normal)
    }

    private fun notifyChange() {
        changeListener?.invoke()
    }
}

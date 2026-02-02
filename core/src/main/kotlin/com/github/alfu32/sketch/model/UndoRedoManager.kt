package com.github.alfu32.sketch.model

class UndoRedoManager(
    private val snapshotProvider: () -> ModelPersistence.ModelSnapshot,
    private val applySnapshot: (ModelPersistence.ModelSnapshot) -> Unit,
    private val onSnapshotApplied: () -> Unit,
    private val debounceMs: Long = 3500L,
    private val maxEntries: Int = 100
) {
    data class UndoEntry(val data: String, val label: String?, val timestamp: Long)

    private val entries = mutableListOf<UndoEntry>()
    private var index = -1
    private var pending = false
    private var lastChangeAt = 0L
    private var lastCommittedData: String? = null

    fun markChanged(now: Long = System.currentTimeMillis()) {
        if (index < entries.size - 1) {
            entries.subList(index + 1, entries.size).clear()
        }
        pending = true
        lastChangeAt = now
    }

    fun update(now: Long = System.currentTimeMillis()) {
        if (pending && now - lastChangeAt >= debounceMs) {
            commit("Auto", now)
        }
    }

    fun commit(label: String? = null, now: Long = System.currentTimeMillis()): Boolean {
        val snapshot = snapshotProvider()
        snapshot.undoHistory = null
        val data = ModelPersistence.encodeSnapshot(snapshot)
        if (data == lastCommittedData) {
            pending = false
            return false
        }
        if (index < entries.size - 1) {
            entries.subList(index + 1, entries.size).clear()
        }
        entries.add(UndoEntry(data, label, now))
        if (entries.size > maxEntries) {
            val overflow = entries.size - maxEntries
            repeat(overflow) { entries.removeAt(0) }
            index = (index - overflow).coerceAtLeast(-1)
        }
        index = entries.size - 1
        lastCommittedData = data
        pending = false
        return true
    }

    fun reset(label: String? = "Initial") {
        entries.clear()
        index = -1
        pending = false
        lastCommittedData = null
        commit(label)
    }

    fun undo(): Boolean {
        if (pending) {
            commit("Auto")
        }
        if (index <= 0 || entries.isEmpty()) {
            return false
        }
        index -= 1
        return applyAt(index)
    }

    fun redo(): Boolean {
        if (pending) {
            commit("Auto")
        }
        if (index >= entries.size - 1) {
            return false
        }
        index += 1
        return applyAt(index)
    }

    fun canUndo(): Boolean = index > 0

    fun canRedo(): Boolean = index in 0 until (entries.size - 1)

    fun exportHistory(): ModelPersistence.UndoHistoryDto {
        return ModelPersistence.UndoHistoryDto().apply {
            maxEntries = this@UndoRedoManager.maxEntries
            index = this@UndoRedoManager.index
            entries = this@UndoRedoManager.entries.map { entry ->
                ModelPersistence.UndoEntryDto().apply {
                    data = entry.data
                    label = entry.label
                    timestamp = entry.timestamp
                }
            }.toMutableList()
        }
    }

    fun importHistory(history: ModelPersistence.UndoHistoryDto?) {
        entries.clear()
        index = -1
        pending = false
        lastCommittedData = null
        if (history == null) {
            return
        }
        history.entries.forEach { dto ->
            if (dto.data.isNotBlank()) {
                entries.add(UndoEntry(dto.data, dto.label, dto.timestamp))
            }
        }
        if (entries.isEmpty()) {
            return
        }
        index = history.index.coerceIn(0, entries.size - 1)
        lastCommittedData = entries[index].data
    }

    private fun applyAt(targetIndex: Int): Boolean {
        val entry = entries.getOrNull(targetIndex) ?: return false
        val snapshot = ModelPersistence.decodeSnapshot(entry.data) ?: return false
        applySnapshot(snapshot)
        lastCommittedData = entry.data
        onSnapshotApplied()
        return true
    }
}

package com.github.alfu32.sketch.model

class ModelCleanup(
    private val lineStore: DraftLineStore,
    private val faceStore: DraftFaceStore
) {
    fun run() {
        lineStore.cleanup()
    }
}

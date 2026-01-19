package com.github.alfu32.sketch.model

class ModelCleanup(
    private val scene: GroupScene
) {
    fun run() {
        scene.root.lineStore.cleanup()
        scene.walkGroups(scene.root) { group ->
            group.lineStore.cleanup()
        }
    }
}

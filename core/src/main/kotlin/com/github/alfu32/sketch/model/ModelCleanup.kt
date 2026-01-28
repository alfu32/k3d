package com.github.alfu32.sketch.model

class ModelCleanup(
    private val scene: GroupScene
) {
    fun run() {
        val cleaned = mutableSetOf<String>()
        scene.allPrototypes().forEach { prototype ->
            if (cleaned.add(prototype.id)) {
                prototype.lineStore.cleanupJts()
            }
        }
    }
}

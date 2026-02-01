package com.github.alfu32.sketch.console

import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.DraftDimensionStore
import com.github.alfu32.sketch.model.DraftTextStore

class SelectionFacade(
    private val scene: GroupScene
) {
    fun clear() {
        scene.clearAllSelections()
    }

    fun groups(): Set<GroupScene.GroupNode> = scene.selectedGroups()

    fun faces(): Set<DraftFaceStore.Triangle> = scene.activeGroup().faceStore.getSelected()

    fun edges(): Set<DraftLineStore.Segment> = scene.activeGroup().lineStore.getSelected()

    fun dimensions(): List<DraftDimensionStore.LinearDimension> = scene.activeGroup().dimensionStore.getSelected()

    fun texts(): List<DraftTextStore.TextEntity> = scene.activeGroup().textStore.getSelected()
}

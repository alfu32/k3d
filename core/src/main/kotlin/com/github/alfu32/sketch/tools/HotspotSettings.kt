package com.github.alfu32.sketch.tools

import com.badlogic.gdx.graphics.Color
import com.github.alfu32.sketch.model.HotspotStore

data class HotspotSettings(
    var defaultOperation: HotspotStore.OperationKind = HotspotStore.OperationKind.MOVE,
    var defaultShape: HotspotStore.ShapeKind = HotspotStore.ShapeKind.CIRCLE,
    var defaultColor: Color = Color(0.2f, 0.55f, 0.95f, 1f)
)

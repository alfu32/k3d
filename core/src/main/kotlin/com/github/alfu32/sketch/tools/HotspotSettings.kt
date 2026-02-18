package com.github.alfu32.sketch.tools

import com.github.alfu32.sketch.model.HotspotStore

data class HotspotSettings(
    var defaultOperation: HotspotStore.OperationKind = HotspotStore.OperationKind.MOVE
)

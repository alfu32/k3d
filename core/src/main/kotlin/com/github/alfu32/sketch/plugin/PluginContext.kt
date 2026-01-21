package com.github.alfu32.sketch.plugin

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.ModelPersistence
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.plugin.PluginResult

data class PluginContext(
    val model: ModelPersistence.ModelSnapshot,
    val selection: SelectionSnapshot,
    val cursor: CursorSnapshot,
    val screenSize: Vector2,
    val activeTool: ToolId,
    val copyMode: Boolean,
    val applyResult: (PluginResult) -> Unit = {}
)

data class SelectionSnapshot(
    val selectedEdges: List<ModelPersistence.SegmentDto>,
    val selectedFaces: List<ModelPersistence.FaceDto>,
    val selectedGroupIds: List<String>
)

data class CursorSnapshot(
    val screen: Vector2,
    val world: Vector3?,
    val snapLabel: String
)

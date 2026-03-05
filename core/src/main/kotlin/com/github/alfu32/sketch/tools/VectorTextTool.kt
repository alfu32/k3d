package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId
import kotlin.math.abs

class VectorTextTool(
    private val scene: GroupScene,
    private val cameraProvider: () -> Camera,
    private val settingsProvider: () -> VectorTextSettings,
    private val glyphCatalogProvider: () -> VectorGlyphCatalog
) : Tool {
    override val id: ToolId = ToolId.VECTOR_TEXT
    override val message: String = "Vector text: click insertion point."

    override fun onEnter(status: StatusModel) {
        status.message = message
    }

    override fun onPointerDown(
        status: StatusModel,
        world: Vector3?,
        normal: Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        if (button != Input.Buttons.LEFT || !valid || world == null) {
            return false
        }
        val settings = settingsProvider()
        val content = settings.text
        if (content.isBlank()) {
            status.message = "Vector text is empty."
            return true
        }
        val catalog = glyphCatalogProvider()
        if (catalog.isEmpty()) {
            status.message = "Glyph catalog is empty."
            return true
        }

        val normalWorld = normal?.cpy()?.nor() ?: Vector3(0f, 1f, 0f)
        val axisUWorld = planeAxisU(normalWorld)
        val axisWWorld = Vector3(axisUWorld).crs(normalWorld).nor()
        if (axisWWorld.len2() <= 1e-6f) {
            status.message = "Invalid placement plane."
            return true
        }

        val parent = scene.activeGroup()
        val localPosition = parent.toLocal(world)
        val localNormal = parent.vectorToLocal(normalWorld).nor()
        val localAxisU = parent.vectorToLocal(axisUWorld).nor()
        parent.textStore.addVectorText(
            position = localPosition,
            text = content,
            size = settings.size.coerceAtLeast(1e-3f),
            normal = localNormal,
            axisU = localAxisU,
            tracking = settings.tracking.coerceAtLeast(0f),
            lineSpacing = settings.lineSpacing.coerceAtLeast(0.1f),
            glyphSourcePath = settings.glyphSourcePath
        )
        status.message = "Vector text entity placed."
        return true
    }

    private fun planeAxisU(normal: Vector3): Vector3 {
        val primary = Vector3(1f, 0f, 0f)
        val fallback = Vector3(0f, 0f, 1f)
        var axis = if (abs(normal.dot(primary)) < 0.95f) {
            Vector3(primary)
        } else {
            Vector3(fallback)
        }
        axis.mulAdd(normal, -axis.dot(normal))
        if (axis.len2() <= 1e-6f) {
            axis = Vector3(cameraProvider().direction).crs(normal)
        }
        if (axis.len2() <= 1e-6f) {
            axis = Vector3(1f, 0f, 0f)
        }
        axis.nor()
        if (abs(axis.dot(normal)) > 1e-3f) {
            axis.mulAdd(normal, -axis.dot(normal)).nor()
        }
        return axis
    }
}

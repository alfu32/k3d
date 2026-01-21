package com.github.alfu32.sketch.plugin

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.github.alfu32.sketch.model.ModelPersistence
import com.github.alfu32.sketch.ui.ToolId
import java.io.File
import java.util.Base64

data class PluginContext(
    val model: ModelPersistence.ModelSnapshot,
    val selection: SelectionSnapshot,
    val cursor: CursorSnapshot,
    val screenSize: Vector2,
    val activeTool: ToolId,
    val copyMode: Boolean,
    val installDir: File = File(System.getProperty("user.dir")),
    val pluginsDir: File = File(System.getProperty("user.dir")),
    val currentDir: File = File(System.getProperty("user.dir")),
    val currentFile: File? = null,
    val epsilon: Float = 1e-4f,
    val applyResult: (PluginResult) -> Unit = {}
) {
    companion object {
        @JvmStatic
        fun iconFromBase64(base64: String): TextureRegionDrawable {
            val payload = base64.substringAfter(",")
            val bytes = Base64.getDecoder().decode(payload)
            return iconFromBytes(bytes)
        }

        @JvmStatic
        fun iconFromBytes(bytes: ByteArray): TextureRegionDrawable {
            val pixmap = Pixmap(bytes, 0, bytes.size)
            return iconFromPixmap(pixmap)
        }

        @JvmStatic
        fun iconFromPixmap(pixmap: Pixmap): TextureRegionDrawable {
            val texture = Texture(pixmap)
            pixmap.dispose()
            return TextureRegionDrawable(TextureRegion(texture))
        }

        @JvmStatic
        fun iconFromFile(file: File): TextureRegionDrawable {
            val handle = Gdx.files.absolute(file.absolutePath)
            val texture = Texture(handle)
            return TextureRegionDrawable(TextureRegion(texture))
        }

        @JvmStatic
        fun iconFromFile(path: String): TextureRegionDrawable {
            return iconFromFile(File(path))
        }
    }
}

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

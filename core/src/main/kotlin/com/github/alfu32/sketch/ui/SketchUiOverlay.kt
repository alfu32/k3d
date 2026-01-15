package com.github.alfu32.sketch.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.widget.VisImageTextButton
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.color.ColorPicker
import com.kotcrab.vis.ui.widget.color.ColorPickerListener

class SketchUiOverlay(
    private val controller: ToolController,
    private val status: StatusModel,
    private val cleanupAction: () -> Unit,
    private val deleteSelectionAction: () -> Unit,
    private val flipFacesAction: () -> Unit,
    private val selectionInfoProvider: () -> SelectionInfo
) {
    val stage: Stage = Stage(ScreenViewport())
    private val toolButtons = mutableMapOf<ToolId, VisImageTextButton>()
    private val iconTextures = mutableListOf<Texture>()
    private val toolLabel = VisLabel()
    private val messageLabel = VisLabel()
    private val inputLabel = VisLabel()
    private val cursorLabel = VisLabel()
    private val selectionEdgesLabel = VisLabel()
    private val selectionFacesLabel = VisLabel()
    private var paintColorButton: VisImageTextButton? = null
    private var lastPaintColor = Color(-1f, -1f, -1f, -1f)
    private var colorPicker: ColorPicker? = null

    init {
        val root = Table()
        root.setFillParent(true)
        stage.addActor(root)

        val toolbar = buildToolbar()
        val selectionPanel = buildSelectionPanel()
        val mainRow = Table()
        mainRow.add(toolbar).top().left().pad(8f)
        mainRow.add().expand().fill()
        mainRow.add(selectionPanel).top().right().pad(8f)

        root.add(mainRow).expand().fill().row()
        root.add(buildStatusBar()).expandX().fillX().pad(6f)

        updateFromStatus()
    }

    fun updateFromStatus() {
        val selection = selectionInfoProvider()
        toolLabel.setText("Tool: ${status.activeTool.displayName}")
        messageLabel.setText(status.message)
        inputLabel.setText(if (status.inputBuffer.isNotEmpty()) "Input: ${status.inputBuffer}" else "")
        cursorLabel.setText(
            "Screen: ${status.cursorScreenX}, ${status.cursorScreenY} | " +
                "World: ${status.cursorWorld} | ${status.cursorSnapLabel}"
        )
        selectionEdgesLabel.setText("Edges: ${selection.edgeCount}")
        selectionFacesLabel.setText("Faces: ${selection.faceCount}")
        toolButtons[status.activeTool]?.isChecked = true
        updatePaintColorButton()
    }

    fun act(delta: Float) {
        updateFromStatus()
        stage.act(delta)
    }

    fun draw() {
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    fun dispose() {
        stage.dispose()
        iconTextures.forEach { it.dispose() }
    }

    private fun buildToolbar(): VisTable {
        val toolbar = VisTable()
        toolbar.defaults().pad(4f).left()
        toolbar.add(VisLabel("Tools")).row()

        val group = ButtonGroup<VisImageTextButton>()
        group.setMaxCheckCount(1)
        group.setMinCheckCount(1)
        group.setUncheckLast(false)

        ToolId.values().forEach { toolId ->
            val icon = createIconDrawable(toolId)
            val button = VisImageTextButton(toolId.displayName, icon)
            button.setChecked(toolId == status.activeTool)
            button.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    controller.setTool(toolId)
                }
            })
            val cell = toolbar.add(button)
            cell.left()
            toolbar.row()
            group.add(button)
            toolButtons[toolId] = button
        }

        toolbar.add(VisLabel("Actions")).padTop(8f).row()
        val cleanupButton = VisImageTextButton("Cleanup", createActionIconDrawable(Color(0.55f, 0.85f, 0.65f, 1f)))
        cleanupButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                cleanupAction()
            }
        })
        toolbar.add(cleanupButton).left().row()

        val colorButton = VisImageTextButton("Color", createActionIconDrawable(status.paintColor))
        colorButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                showColorPicker()
            }
        })
        toolbar.add(colorButton).left().row()
        paintColorButton = colorButton

        val deleteButton = VisImageTextButton("Delete", createActionIconDrawable(Color(0.9f, 0.45f, 0.45f, 1f)))
        deleteButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                deleteSelectionAction()
            }
        })
        toolbar.add(deleteButton).left().row()

        val flipButton = VisImageTextButton("Flip Faces", createActionIconDrawable(Color(0.45f, 0.65f, 0.95f, 1f)))
        flipButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                flipFacesAction()
            }
        })
        toolbar.add(flipButton).left().row()

        return toolbar
    }

    private fun buildStatusBar(): VisTable {
        val bar = VisTable()
        bar.defaults().pad(4f)
        bar.add(toolLabel).left()
        bar.add(messageLabel).expandX().left()
        bar.add(cursorLabel).expandX().left()
        bar.add(inputLabel).right()
        return bar
    }

    private fun buildSelectionPanel(): VisTable {
        val panel = VisTable()
        panel.defaults().pad(4f).left()
        panel.add(VisLabel("Selection")).row()
        panel.add(selectionEdgesLabel).row()
        panel.add(selectionFacesLabel).row()
        return panel
    }

    private fun createIconDrawable(toolId: ToolId): TextureRegionDrawable {
        val color = when (toolId) {
            ToolId.SELECT -> Color(0.85f, 0.85f, 0.85f, 1f)
            ToolId.LINE -> Color(0.95f, 0.75f, 0.25f, 1f)
            ToolId.RECTANGLE -> Color(0.35f, 0.75f, 0.95f, 1f)
            ToolId.CIRCLE -> Color(0.95f, 0.55f, 0.75f, 1f)
            ToolId.PUSH_PULL -> Color(0.45f, 0.95f, 0.55f, 1f)
            ToolId.MOVE -> Color(0.95f, 0.45f, 0.35f, 1f)
            ToolId.ROTATE -> Color(0.75f, 0.55f, 0.95f, 1f)
            ToolId.SCALE -> Color(0.95f, 0.55f, 0.75f, 1f)
            ToolId.PAINT -> Color(0.95f, 0.95f, 0.45f, 1f)
            ToolId.ERASER -> Color(0.65f, 0.65f, 0.65f, 1f)
        }

        val size = 16
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color(0.1f, 0.1f, 0.1f, 1f))
        pixmap.fill()
        pixmap.setColor(color)
        pixmap.fillRectangle(2, 2, size - 4, size - 4)

        val texture = Texture(pixmap)
        pixmap.dispose()
        iconTextures.add(texture)
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createActionIconDrawable(color: Color): TextureRegionDrawable {
        val size = 16
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color(0.1f, 0.1f, 0.1f, 1f))
        pixmap.fill()
        pixmap.setColor(color)
        pixmap.fillRectangle(2, 2, size - 4, size - 4)
        val texture = Texture(pixmap)
        pixmap.dispose()
        iconTextures.add(texture)
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun updatePaintColorButton() {
        val button = paintColorButton ?: return
        if (sameColor(status.paintColor, lastPaintColor)) {
            return
        }
        lastPaintColor = Color(status.paintColor)
        button.image.drawable = createActionIconDrawable(status.paintColor)
    }

    private fun showColorPicker() {
        if (colorPicker == null) {
            colorPicker = ColorPicker("Paint Color").apply {
                setListener(object : ColorPickerListener {
                    override fun changed(color: Color?) {
                        if (color != null) {
                            status.paintColor.set(color)
                        }
                    }

                    override fun canceled(oldColor: Color?) {
                        if (oldColor != null) {
                            status.paintColor.set(oldColor)
                        }
                    }

                    override fun reset(oldColor: Color?, newColor: Color?) {
                        if (newColor != null) {
                            status.paintColor.set(newColor)
                        }
                    }

                    override fun finished(color: Color?) {
                        if (color != null) {
                            status.paintColor.set(color)
                        }
                    }
                })
            }
        }
        val picker = colorPicker ?: return
        picker.color = Color(status.paintColor)
        if (picker.stage == null) {
            stage.addActor(picker)
        }
        picker.centerWindow()
        picker.fadeIn()
    }

    private fun sameColor(a: Color, b: Color): Boolean {
        return a.r == b.r && a.g == b.g && a.b == b.b && a.a == b.a
    }

    data class SelectionInfo(val edgeCount: Int, val faceCount: Int)
}

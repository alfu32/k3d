package com.github.alfu32.sketch.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.widget.VisImageTextButton
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisCheckBox
import com.kotcrab.vis.ui.widget.VisSlider
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisTextField
import com.kotcrab.vis.ui.widget.color.ColorPicker
import com.kotcrab.vis.ui.widget.color.ColorPickerListener
import com.github.alfu32.sketch.plugin.PluginHost
import java.util.Locale

class SketchUiOverlay(
    private val controller: ToolController,
    private val status: StatusModel,
    private val cleanupAction: () -> Unit,
    private val deleteSelectionAction: () -> Unit,
    private val flipFacesAction: () -> Unit,
    private val selectionInfoProvider: () -> SelectionInfo,
    private val groupInfoProvider: () -> GroupInfo?,
    private val groupNameChanged: (String) -> Unit,
    private val groupGlueChanged: (Boolean) -> Unit,
    private val objectPrototypeProvider: () -> List<ObjectPrototypeInfo>,
    private val objectPrototypePlace: (String) -> Unit,
    private val objectPrototypeDelete: (String) -> Unit,
    private val lightingSettings: LightingSettings,
    private val lightingChanged: (LightingSettings) -> Unit,
    private val shadowSettings: ShadowSettings,
    private val shadowChanged: (ShadowSettings) -> Unit
) {
    private open class CollapsibleWindow(
        title: String,
        private val fixedHeight: Float? = null,
        showCloseButton: Boolean = true
    ) : com.kotcrab.vis.ui.widget.VisWindow(title, true) {
        private var collapsed = false

        init {
            isMovable = true
            isResizable = true
            isModal = false
            setKeepWithinParent(false)
            if (showCloseButton) {
                addCloseButton()
            }
            getTitleTable().addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (tapCount >= 2) {
                        toggleCollapsed()
                    }
                }
            })
        }

        override fun close() {
            isVisible = false
        }

        override fun getPrefWidth(): Float {
            return if (isVisible) super.getPrefWidth() else 0f
        }

        override fun getPrefHeight(): Float {
            if (!isVisible) {
                return 0f
            }
            if (collapsed) {
                return getTitleTable().prefHeight
            }
            val pref = super.getPrefHeight()
            return fixedHeight ?: pref
        }

        private fun toggleCollapsed() {
            collapsed = !collapsed
            val title = getTitleTable()
            children.forEach { child ->
                if (child !== title) {
                    child.isVisible = !collapsed
                }
            }
            invalidateHierarchy()
            pack()
        }
    }

    val stage: Stage = Stage(ScreenViewport())
    private val toolButtons = mutableMapOf<ToolId, VisImageTextButton>()
    private val buttonLabels = mutableMapOf<VisImageTextButton, String>()
    private val pluginToolButtons = mutableMapOf<String, VisImageTextButton>()
    private val pluginToolbars = mutableMapOf<String, CollapsibleWindow>()
    private var pluginTooltip: CollapsibleWindow? = null
    private val hoveredButtons = mutableSetOf<VisImageTextButton>()
    private val iconTextures = mutableListOf<Texture>()
    private val iconDrawables = mutableMapOf<String, TextureRegionDrawable>()
    private var iconsTexture: Texture? = null
    private var whiteButtonDrawable: TextureRegionDrawable? = null
    private var darkBarDrawable: TextureRegionDrawable? = null
    private val toolLabel = VisLabel()
    private val messageLabel = VisLabel()
    private val copyLabel = VisLabel()
    private val inputLabel = VisLabel()
    private val cursorLabel = VisLabel()
    private val selectionEdgesLabel = VisLabel()
    private val selectionFacesLabel = VisLabel()
    private val selectionGroupsLabel = VisLabel()
    private var lastPluginTools: List<String> = emptyList()
    private lateinit var groupPanel: CollapsibleWindow
    private val groupStatusLabel = VisLabel()
    private val groupNameField = VisTextField()
    private val groupGlueCheck = VisCheckBox("Glue to surface")
    private var lastGroupName = ""
    private var lastGroupGlue = false
    private var lastGroupEditing = false
    private var lastGroupId = ""
    private var updatingGroupFields = false
    private var paintColorButton: VisImageTextButton? = null
    private var lastPaintColor = Color(-1f, -1f, -1f, -1f)
    private var colorPicker: ColorPicker? = null
    private var lightingPanel: CollapsibleWindow? = null
    private lateinit var objectsPanel: CollapsibleWindow
    private val objectsList = com.kotcrab.vis.ui.widget.VisList<String>()
    private var objectPrototypeItems: List<ObjectPrototypeInfo> = emptyList()
    private lateinit var objectsDeleteButton: VisTextButton
    private val lightingRefreshers = mutableListOf<() -> Unit>()
    private lateinit var selectionPanel: CollapsibleWindow
    private var needsPanelLayout = true
    private var pluginManagerPanel: PluginManagerPanel? = null
    private var commandPaletteUI: CommandPaletteUI? = null
    private var pluginHost: PluginHost? = null
    private var pluginPanelsPositioned = false

    init {
        iconDrawables.putAll(loadIconDrawables())
        val root = Table()
        root.setFillParent(true)
        stage.addActor(root)
        stage.viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)

        val toolbar = buildToolbar()
        selectionPanel = buildSelectionPanel()
        groupPanel = buildGroupPanel()
        objectsPanel = buildObjectsPanel()
        lightingPanel = buildLightingPanel()
        val mainRow = Table()
        mainRow.add(toolbar).top().left().pad(6f)
        mainRow.add().expand().fill()

        root.add(mainRow).expand().fill().row()
        root.add(buildStatusBar()).expandX().fillX().bottom().pad(0f)

        stage.addActor(selectionPanel)
        stage.addActor(groupPanel)
        stage.addActor(objectsPanel)
        lightingPanel?.let { stage.addActor(it) }
        positionPanels()
        needsPanelLayout = true

        updateFromStatus()
    }

    fun updateFromStatus() {
        val selection = selectionInfoProvider()
        toolLabel.setText("Tool: ${status.activeTool.displayName}")
        messageLabel.setText(status.message)
        val copyText = if (status.activeTool == ToolId.MOVE || status.activeTool == ToolId.ROTATE) {
            if (status.copyMode) "Copy: On" else "Copy: Off"
        } else {
            ""
        }
        copyLabel.setText(copyText)
        inputLabel.setText(if (status.inputBuffer.isNotEmpty()) "Input: ${status.inputBuffer}" else "")
        cursorLabel.setText(
            "Screen: ${status.cursorScreenX}, ${status.cursorScreenY} | " +
                "World: ${status.cursorWorld} | ${status.cursorSnapLabel}"
        )
        selectionEdgesLabel.setText("Edges: ${selection.edgeCount}")
        selectionFacesLabel.setText("Faces: ${selection.faceCount}")
        selectionGroupsLabel.setText("Objects: ${selection.groupCount}")
        updateGroupPanel()
        updateObjectsPanel()
        refreshPluginToolbar()
        toolButtons[status.activeTool]?.isChecked = true
        updatePluginToolSelection()
        updatePaintColorButton()
        updateButtonLabels()
    }

    fun refreshLightingControls() {
        lightingRefreshers.forEach { it.invoke() }
    }

    fun act(delta: Float) {
        updateFromStatus()
        if (needsPanelLayout) {
            positionPanels()
            needsPanelLayout = false
        }
        stage.act(delta)
    }

    fun draw() {
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        needsPanelLayout = true
        pluginPanelsPositioned = false
    }

    // Plugin management methods
    fun setPluginHost(host: PluginHost) {
        pluginHost = host
        pluginManagerPanel = PluginManagerPanel(host)
        commandPaletteUI = CommandPaletteUI(host.getCommandPalette(), stage)
        stage.addActor(pluginManagerPanel)
        pluginPanelsPositioned = false
    }

    fun togglePluginManager() {
        pluginManagerPanel?.let {
            it.isVisible = !it.isVisible
            if (it.isVisible) {
                it.refresh()
            }
        }
    }

    fun showSelectionPanel() {
        selectionPanel.isVisible = true
        needsPanelLayout = true
    }

    fun showGroupPanel() {
        groupPanel.isVisible = true
        needsPanelLayout = true
    }

    fun showLightingPanel() {
        lightingPanel?.let {
            it.isVisible = true
            needsPanelLayout = true
        }
    }

    fun showPluginManager() {
        pluginManagerPanel?.let {
            it.isVisible = true
            it.refresh()
        }
    }

    fun showCommandPalette() {
        commandPaletteUI?.show()
    }

    fun dispose() {
        stage.dispose()
        iconTextures.forEach { it.dispose() }
        iconsTexture?.dispose()
    }

    private fun buildToolbar(): VisTable {
        val toolbar = VisTable()
        toolbar.defaults().pad(2f).padRight(6f).left()
        toolbar.add(VisLabel("Tools")).row()

        val group = ButtonGroup<VisImageTextButton>()
        group.setMaxCheckCount(1)
        group.setMinCheckCount(1)
        group.setUncheckLast(false)

        ToolId.values().filter { it != ToolId.PLUGIN && it != ToolId.OBJECT_PLACE }.forEach { toolId ->
            val icon = createIconDrawable(toolId)
            val button = VisImageTextButton(toolId.displayName, icon)
            applyWhiteButtonStyle(button)
            applyIconStyle(button, icon)
            button.setChecked(toolId == status.activeTool)
            button.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    controller.setTool(toolId)
                }
            })
            val cell = toolbar.add(button).padRight(6f)
            cell.left()
            toolbar.row()
            group.add(button)
            toolButtons[toolId] = button
            buttonLabels[button] = toolId.displayName
            button.addListener(object : ClickListener() {
                override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                    hoveredButtons.add(button)
                    updateButtonLabels()
                }

                override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                    hoveredButtons.remove(button)
                    updateButtonLabels()
                }
            })
        }

        toolbar.add(VisLabel("Actions")).padTop(8f).row()
        val cleanupButton = VisImageTextButton("Cleanup", createActionIconDrawable(Color(0.55f, 0.85f, 0.65f, 1f)))
        applyWhiteButtonStyle(cleanupButton)
        applyIconStyle(cleanupButton, iconFor("cleanup", cleanupButton.image.drawable))
        buttonLabels[cleanupButton] = "Cleanup"
        cleanupButton.addListener(hoverListener(cleanupButton))
        cleanupButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                cleanupAction()
            }
        })
        toolbar.add(cleanupButton).left().padRight(6f).row()

        val colorButton = VisImageTextButton("Color", createActionIconDrawable(status.paintColor))
        applyWhiteButtonStyle(colorButton)
        applyIconStyle(colorButton, iconFor("color", colorButton.image.drawable))
        buttonLabels[colorButton] = "Color"
        colorButton.addListener(hoverListener(colorButton))
        colorButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                showColorPicker()
            }
        })
        toolbar.add(colorButton).left().padRight(6f).row()
        paintColorButton = colorButton

        val deleteButton = VisImageTextButton("Delete", createActionIconDrawable(Color(0.9f, 0.45f, 0.45f, 1f)))
        applyWhiteButtonStyle(deleteButton)
        applyIconStyle(deleteButton, iconFor("delete", deleteButton.image.drawable))
        buttonLabels[deleteButton] = "Delete"
        deleteButton.addListener(hoverListener(deleteButton))
        deleteButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                deleteSelectionAction()
            }
        })
        toolbar.add(deleteButton).left().padRight(6f).row()

        val flipButton = VisImageTextButton("Flip Faces", createActionIconDrawable(Color(0.45f, 0.65f, 0.95f, 1f)))
        applyWhiteButtonStyle(flipButton)
        applyIconStyle(flipButton, iconFor("flip_faces", flipButton.image.drawable))
        buttonLabels[flipButton] = "Flip Faces"
        flipButton.addListener(hoverListener(flipButton))
        flipButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                flipFacesAction()
            }
        })
        toolbar.add(flipButton).left().padRight(6f).row()

        val lightingFallback = createActionIconDrawable(Color(0.95f, 0.85f, 0.2f, 1f))
        val lightingIcon = iconFor("lighting", lightingFallback)
        val lightingButton = VisImageTextButton("Lighting", lightingIcon)
        applyWhiteButtonStyle(lightingButton)
        applyIconStyle(lightingButton, lightingIcon)
        buttonLabels[lightingButton] = "Lighting"
        lightingButton.addListener(hoverListener(lightingButton))
        lightingButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                lightingPanel?.let { panel ->
                    panel.isVisible = !panel.isVisible
                    needsPanelLayout = true
                    panel.toFront()
                }
            }
        })
        toolbar.add(lightingButton).left().padRight(6f).row()

        val pluginFallback = createActionIconDrawable(Color(0.6f, 0.6f, 0.6f, 1f))
        val pluginIcon = iconFor("plugins", pluginFallback)
        val pluginButton = VisImageTextButton("Plugin Manager", pluginIcon)
        applyWhiteButtonStyle(pluginButton)
        applyIconStyle(pluginButton, pluginIcon)
        buttonLabels[pluginButton] = "Plugin Manager"
        pluginButton.addListener(hoverListener(pluginButton))
        pluginButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                togglePluginManager()
            }
        })
        toolbar.add(pluginButton).left().padRight(6f).row()

        return toolbar
    }

    private fun buildStatusBar(): VisTable {
        val bar = VisTable()
        bar.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        bar.defaults().pad(2f)
        bar.add(toolLabel).left()
        bar.add(messageLabel).expandX().left()
        bar.add(copyLabel).left()
        bar.add(cursorLabel).expandX().left()
        bar.add(inputLabel).right()
        return bar
    }

    private fun buildSelectionPanel(): CollapsibleWindow {
        val panel = CollapsibleWindow("Selection")
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left()
        content.add(selectionEdgesLabel).row()
        content.add(selectionFacesLabel).row()
        content.add(selectionGroupsLabel).row()
        panel.add(content).grow()
        return panel
    }

    private fun buildGroupPanel(): CollapsibleWindow {
        val panel = CollapsibleWindow("Object")
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        content.add(groupStatusLabel).left().row()
        content.add(VisLabel("Name")).left().row()
        content.add(groupNameField).growX().row()
        content.add(groupGlueCheck).left().row()
        panel.add(content).growX()
        panel.isVisible = false

        groupNameField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (updatingGroupFields) {
                    return
                }
                groupNameChanged(groupNameField.text)
            }
        })
        groupGlueCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (updatingGroupFields) {
                    return
                }
                groupGlueChanged(groupGlueCheck.isChecked)
            }
        })
        return panel
    }

    private fun buildObjectsPanel(): CollapsibleWindow {
        val panel = CollapsibleWindow("Objects", fixedHeight = 240f)
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        objectsList.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (tapCount >= 2) {
                    val selected = selectedObjectPrototype() ?: return
                    objectPrototypePlace(selected.id)
                }
            }
        })
        val scroll = com.kotcrab.vis.ui.widget.VisScrollPane(objectsList).apply {
            setFadeScrollBars(false)
            setScrollingDisabled(true, false)
        }
        content.add(scroll).growX().height(160f).row()
        objectsDeleteButton = VisTextButton("Delete Prototype")
        objectsDeleteButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val selected = selectedObjectPrototype() ?: return
                objectPrototypeDelete(selected.id)
            }
        })
        content.add(objectsDeleteButton).left().padTop(4f).row()
        panel.add(content).growX()
        panel.isVisible = false
        return panel
    }

    private fun updateGroupPanel() {
        val info = groupInfoProvider()
        val wasVisible = groupPanel.isVisible
        groupPanel.isVisible = true
        if (!wasVisible) {
            needsPanelLayout = true
        }
        if (info == null) {
            groupStatusLabel.setText("No object selected")
            updatingGroupFields = true
            groupNameField.text = ""
            groupGlueCheck.isChecked = false
            updatingGroupFields = false
            groupNameField.isDisabled = true
            groupGlueCheck.isDisabled = true
            lastGroupName = ""
            lastGroupGlue = false
            lastGroupEditing = false
            lastGroupId = ""
            return
        }
        groupNameField.isDisabled = false
        groupGlueCheck.isDisabled = false
        val statusText = if (info.editing) {
            "Editing object: ${info.name}"
        } else {
            "Selected object: ${info.name}"
        }
        groupStatusLabel.setText(statusText)
        val selectionChanged = info.id != lastGroupId
        if (selectionChanged || !groupNameField.hasKeyboardFocus() || info.name != lastGroupName) {
            updatingGroupFields = true
            groupNameField.text = info.name
            updatingGroupFields = false
        }
        if (selectionChanged || info.glued != lastGroupGlue) {
            updatingGroupFields = true
            groupGlueCheck.isChecked = info.glued
            updatingGroupFields = false
        }
        lastGroupName = info.name
        lastGroupGlue = info.glued
        lastGroupEditing = info.editing
        lastGroupId = info.id
    }

    private fun updateObjectsPanel() {
        val prototypes = objectPrototypeProvider()
        if (prototypes.isEmpty()) {
            objectsDeleteButton.isDisabled = true
        }
        if (prototypes != objectPrototypeItems) {
            objectPrototypeItems = prototypes
            val items = prototypes.map { prototype ->
                "${prototype.name} (${prototype.instanceCount}) [${prototype.id.take(8)}]"
            }
            objectsList.setItems(*items.toTypedArray())
            objectsList.selectedIndex = -1
        }
        val selected = selectedObjectPrototype()
        objectsDeleteButton.isDisabled = selected == null || selected.instanceCount > 0
    }

    private fun selectedObjectPrototype(): ObjectPrototypeInfo? {
        val index = objectsList.selectedIndex
        if (index < 0 || index >= objectPrototypeItems.size) {
            return null
        }
        return objectPrototypeItems[index]
    }

    fun toggleObjectsPanel() {
        objectsPanel.isVisible = !objectsPanel.isVisible
        needsPanelLayout = true
    }


    private fun buildLightingPanel(): CollapsibleWindow {
        val panel = CollapsibleWindow("Lighting")
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        content.add(
            buildLightingSlider("Shadow value", lightingSettings.shadowLightValue) { value ->
                lightingSettings.shadowLightValue = value
                lightingChanged(lightingSettings)
            }
        ).growX().row()
        content.add(
            buildLightingSlider("Shadow alpha", lightingSettings.shadowLightAlpha) { value ->
                lightingSettings.shadowLightAlpha = value
                lightingChanged(lightingSettings)
            }
        ).growX().row()
        content.add(
            buildLightingSlider("Directional value", lightingSettings.directionalLightValue) { value ->
                lightingSettings.directionalLightValue = value
                lightingChanged(lightingSettings)
            }
        ).growX().row()
        content.add(
            buildLightingSlider("Directional alpha", lightingSettings.directionalLightAlpha) { value ->
                lightingSettings.directionalLightAlpha = value
                lightingChanged(lightingSettings)
            }
        ).growX().row()
        content.add(
            buildLightingSlider("Ambient value", lightingSettings.ambientLightValue) { value ->
                lightingSettings.ambientLightValue = value
                lightingChanged(lightingSettings)
            }
        ).growX().row()
        content.add(
            buildLightingSlider("Ambient alpha", lightingSettings.ambientLightAlpha) { value ->
                lightingSettings.ambientLightAlpha = value
                lightingChanged(lightingSettings)
            }
        ).growX().row()
        content.add(
            buildLightingSlider("Specular value", lightingSettings.specularLightValue) { value ->
                lightingSettings.specularLightValue = value
                lightingChanged(lightingSettings)
            }
        ).growX().row()
        content.add(
            buildLightingSlider("Specular alpha", lightingSettings.specularLightAlpha) { value ->
                lightingSettings.specularLightAlpha = value
                lightingChanged(lightingSettings)
            }
        ).growX().row()
        content.add(VisLabel("Shadow Settings")).padTop(6f).row()
        content.add(
            buildShadowSlider("Shadow bias", shadowSettings.shadowBias, 0f, 4096f) { value ->
                shadowSettings.shadowBias = value
                shadowChanged(shadowSettings)
            }
        ).growX().row()
        content.add(
            buildShadowSlider("Normal bias", shadowSettings.shadowNormalBias, 0f, 8192f) { value ->
                shadowSettings.shadowNormalBias = value
                shadowChanged(shadowSettings)
            }
        ).growX().row()
        content.add(
            buildPcfSlider("PCF", shadowSettings.pcfMode) { mode ->
                shadowSettings.pcfMode = mode
                shadowChanged(shadowSettings)
            }
        ).growX().row()
        content.add(
            buildShadowToggles()
        ).growX().row()
        panel.add(content).growX()
        panel.isVisible = false
        lightingPanel = panel
        return panel
    }

    private fun positionPanels() {
        val panels = mutableListOf<CollapsibleWindow>()
        panels.add(selectionPanel)
        panels.add(groupPanel)
        panels.add(objectsPanel)
        lightingPanel?.let { panels.add(it) }
        panels.forEach {
            it.invalidateHierarchy()
            it.pack()
            it.toFront()
        }
        positionPanelStack(panels, 8f, 8f, 6f)
        if (!pluginPanelsPositioned) {
            positionPluginPanels()
            pluginPanelsPositioned = true
        }
    }

    private fun positionPluginPanels() {
        val width = if (stage.viewport.screenWidth > 0) stage.viewport.screenWidth.toFloat() else Gdx.graphics.width.toFloat()
        val height = if (stage.viewport.screenHeight > 0) stage.viewport.screenHeight.toFloat() else Gdx.graphics.height.toFloat()
        pluginManagerPanel?.let { panel ->
            panel.pack()
            panel.setPosition(width - panel.width - 12f, 12f)
            panel.isVisible = true
        }
        commandPaletteUI?.let { palette ->
            val paletteWidth = palette.windowWidth().coerceAtLeast(520f)
            val paletteHeight = palette.windowHeight().coerceAtLeast(360f)
            val x = width - paletteWidth - 24f
            val y = (height - paletteHeight) * 0.5f
            palette.showDefaultAt(x, y)
        }
        pluginToolbars.values.forEachIndexed { idx, window ->
            window.pack()
            val y = height - 12f - window.height - (idx * (window.height + 8f))
            window.setPosition(12f, y)
        }
    }

    private fun positionPanelStack(
        panels: List<CollapsibleWindow>,
        rightPadding: Float,
        topPadding: Float,
        gap: Float
    ) {
        val width = if (stage.viewport.screenWidth > 0) stage.viewport.screenWidth.toFloat() else Gdx.graphics.width.toFloat()
        val height = if (stage.viewport.screenHeight > 0) stage.viewport.screenHeight.toFloat() else Gdx.graphics.height.toFloat()
        var y = height - topPadding
        panels.forEach { panel ->
            if (!panel.isVisible) {
                return@forEach
            }
            panel.pack()
            val panelWidth = panel.width
            val panelHeight = panel.height
            panel.setPosition(width - rightPadding - panelWidth, y - panelHeight)
            y -= panelHeight + gap
        }
    }

    private fun buildLightingSlider(
        label: String,
        initial: Float,
        onChange: (Float) -> Unit
    ): VisTable {
        val row = VisTable()
        row.defaults().left()
        val title = VisLabel("$label: ${formatValue(initial)}")
        val slider = VisSlider(-1f, 1f, 0.01f, false).apply {
            value = initial
        }
        lightingRefreshers.add {
            slider.value = when (label) {
                "Shadow value" -> lightingSettings.shadowLightValue
                "Shadow alpha" -> lightingSettings.shadowLightAlpha
                "Directional value" -> lightingSettings.directionalLightValue
                "Directional alpha" -> lightingSettings.directionalLightAlpha
                "Ambient value" -> lightingSettings.ambientLightValue
                "Ambient alpha" -> lightingSettings.ambientLightAlpha
                "Specular value" -> lightingSettings.specularLightValue
                "Specular alpha" -> lightingSettings.specularLightAlpha
                else -> slider.value
            }
            title.setText("$label: ${formatValue(slider.value)}")
        }
        slider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                val value = slider.value
                title.setText("$label: ${formatValue(value)}")
                onChange(value)
            }
        })
        row.add(title).left().padRight(6f)
        row.add(slider).width(140f).left()
        return row
    }

    private fun buildShadowSlider(
        label: String,
        initial: Float,
        min: Float,
        max: Float,
        onChange: (Float) -> Unit
    ): VisTable {
        val row = VisTable()
        row.defaults().left()
        val title = VisLabel("$label: ${formatValue(initial)}")
        val slider = VisSlider(min, max, 1f, false).apply {
            value = initial
        }
        lightingRefreshers.add {
            slider.value = when (label) {
                "Shadow bias" -> shadowSettings.shadowBias
                "Normal bias" -> shadowSettings.shadowNormalBias
                else -> slider.value
            }
            title.setText("$label: ${formatValue(slider.value)}")
        }
        slider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                val value = slider.value
                title.setText("$label: ${formatValue(value)}")
                onChange(value)
            }
        })
        row.add(title).left().padRight(6f)
        row.add(slider).width(140f).left()
        return row
    }

    private fun buildPcfSlider(
        label: String,
        initial: Int,
        onChange: (Int) -> Unit
    ): VisTable {
        val row = VisTable()
        row.defaults().left()
        val title = VisLabel("$label: ${initial}x${initial}")
        val slider = VisSlider(1f, 3f, 1f, false).apply {
            value = initial.toFloat()
        }
        lightingRefreshers.add {
            slider.value = shadowSettings.pcfMode.toFloat()
            val mode = shadowSettings.pcfMode.coerceIn(1, 3)
            title.setText("$label: ${mode}x${mode}")
        }
        slider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                val mode = slider.value.toInt().coerceIn(1, 3)
                title.setText("$label: ${mode}x${mode}")
                onChange(mode)
            }
        })
        row.add(title).left().padRight(6f)
        row.add(slider).width(140f).left()
        return row
    }

    private fun buildShadowToggles(): VisTable {
        val row = VisTable()
        row.defaults().left().padRight(8f)
        val useCsm = VisCheckBox("Use CSM", shadowSettings.useCsm)
        lightingRefreshers.add {
            useCsm.isChecked = shadowSettings.useCsm
        }
        useCsm.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                shadowSettings.useCsm = useCsm.isChecked
                shadowChanged(shadowSettings)
            }
        })
        val dither = VisCheckBox("Dither Shadows", shadowSettings.dither)
        lightingRefreshers.add {
            dither.isChecked = shadowSettings.dither
        }
        dither.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                shadowSettings.dither = dither.isChecked
                shadowChanged(shadowSettings)
            }
        })
        row.add(useCsm)
        row.add(dither)
        return row
    }

    private fun formatValue(value: Float): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun refreshPluginToolbar() {
        val host = pluginHost ?: return
        val entries = host.pluginToolEntries()
        val ids = entries.map { it.id }
        if (ids == lastPluginTools) {
            return
        }
        lastPluginTools = ids
        pluginToolButtons.values.forEach { it.remove() }
        pluginToolButtons.clear()
        pluginToolbars.values.forEach { it.remove() }
        pluginToolbars.clear()
        val grouped = entries.groupBy { it.pluginId }
        grouped.forEach { (pluginId, tools) ->
            val title = tools.firstOrNull()?.pluginName ?: pluginId
            val window = CollapsibleWindow(title, showCloseButton = false)
            val group = HorizontalGroup().apply {
                space(6f)
                pad(6f)
            }
            tools.forEach { entry ->
                val fallback = createActionIconDrawable(Color(0.65f, 0.75f, 0.95f, 1f))
                val icon = entry.iconDrawable ?: iconFor(entry.icon, fallback)
                val button = VisImageTextButton(entry.name, icon)
                applyWhiteButtonStyle(button)
                applyIconStyle(button, icon)
                buttonLabels[button] = entry.name
                button.addListener(hoverListener(button))
                button.addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        host.activatePluginTool(entry.id)
                    }
                })
                group.addActor(button)
                pluginToolButtons[entry.id] = button
            }
            window.add(group).grow()
            stage.addActor(window)
            pluginToolbars[pluginId] = window
        }
        updatePluginToolSelection()
        pluginPanelsPositioned = false
    }

    private fun updatePluginToolSelection() {
        val host = pluginHost ?: return
        val activeId = host.activePluginToolId()
        pluginToolButtons.forEach { (id, button) ->
            button.isChecked = id == activeId
        }
    }

    private fun showPluginTooltip(button: VisImageTextButton, text: String) {
        val tooltip = pluginTooltip ?: CollapsibleWindow("", showCloseButton = false).also {
            it.isModal = false
            it.isMovable = false
            it.isResizable = false
            it.setKeepWithinParent(false)
            pluginTooltip = it
            stage.addActor(it)
        }
        tooltip.clearChildren()
        tooltip.add(VisLabel(text)).pad(6f)
        tooltip.pack()
        val pos = button.localToStageCoordinates(com.badlogic.gdx.math.Vector2(0f, 0f))
        tooltip.setPosition(pos.x, pos.y - tooltip.height - 6f)
        tooltip.isVisible = true
    }

    private fun hidePluginTooltip() {
        pluginTooltip?.isVisible = false
    }

    private fun createIconDrawable(toolId: ToolId): TextureRegionDrawable {
        val iconName = when (toolId) {
            ToolId.SELECT -> "select"
            ToolId.LINE -> "line"
            ToolId.RECTANGLE -> "rectangle"
            ToolId.SURFACE_RECTANGLE -> "surface_rect"
            ToolId.QUAD -> "quad"
            ToolId.CIRCLE -> "circle"
            ToolId.PUSH_PULL -> "push_pull"
            ToolId.MOVE -> "move"
            ToolId.ROTATE -> "rotate"
            ToolId.SCALE -> "scale"
            ToolId.PAINT -> "paint"
            ToolId.ERASER -> "eraser"
            ToolId.OBJECT_PLACE -> "select"
            ToolId.PLUGIN -> "plugins"
        }
        iconDrawables[iconName]?.let { return it }
        val color = when (toolId) {
            ToolId.SELECT -> Color(0.85f, 0.85f, 0.85f, 1f)
            ToolId.LINE -> Color(0.95f, 0.75f, 0.25f, 1f)
            ToolId.RECTANGLE -> Color(0.35f, 0.75f, 0.95f, 1f)
            ToolId.SURFACE_RECTANGLE -> Color(0.35f, 0.85f, 0.65f, 1f)
            ToolId.QUAD -> Color(0.55f, 0.85f, 0.95f, 1f)
            ToolId.CIRCLE -> Color(0.95f, 0.55f, 0.75f, 1f)
            ToolId.PUSH_PULL -> Color(0.45f, 0.95f, 0.55f, 1f)
            ToolId.MOVE -> Color(0.95f, 0.45f, 0.35f, 1f)
            ToolId.ROTATE -> Color(0.75f, 0.55f, 0.95f, 1f)
            ToolId.SCALE -> Color(0.95f, 0.55f, 0.75f, 1f)
            ToolId.PAINT -> Color(0.95f, 0.95f, 0.45f, 1f)
            ToolId.ERASER -> Color(0.65f, 0.65f, 0.65f, 1f)
            ToolId.OBJECT_PLACE -> Color(0.85f, 0.85f, 0.85f, 1f)
            ToolId.PLUGIN -> Color(0.75f, 0.85f, 0.95f, 1f)
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
        if (sameColor(status.paintColor, lastPaintColor)) {
            return
        }
        lastPaintColor = Color(status.paintColor)
        if (iconDrawables.containsKey("color")) {
            paintColorButton?.image?.setColor(status.paintColor)
        } else {
            updateButtonIcon(paintColorButton, status.paintColor)
        }
        if (iconDrawables.containsKey("paint")) {
            toolButtons[ToolId.PAINT]?.image?.setColor(status.paintColor)
        } else {
            updateButtonIcon(toolButtons[ToolId.PAINT], status.paintColor)
        }
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

    private fun updateButtonIcon(button: VisImageTextButton?, color: Color) {
        val target = button ?: return
        val drawable = createActionIconDrawable(color)
        val style = target.style
        style.imageUp = drawable
        style.imageDown = drawable
        style.imageChecked = drawable
        style.imageOver = drawable
        target.style = style
        target.image?.drawable = drawable
        target.invalidateHierarchy()
    }

    private fun sameColor(a: Color, b: Color): Boolean {
        return a.r == b.r && a.g == b.g && a.b == b.b && a.a == b.a
    }

    private fun applyIconStyle(button: VisImageTextButton, icon: com.badlogic.gdx.scenes.scene2d.utils.Drawable) {
        val style = button.style
        style.imageUp = icon
        style.imageDown = icon
        style.imageChecked = icon
        style.imageOver = icon
        button.image?.drawable = icon
    }

    private fun updateButtonLabels() {
        buttonLabels.forEach { (button, label) ->
            val isTool = toolButtons.containsValue(button)
            val show = if (isTool) {
                val toolId = toolButtons.entries.firstOrNull { it.value == button }?.key
                toolId == status.activeTool || hoveredButtons.contains(button)
            } else {
                hoveredButtons.contains(button)
            }
            button.setText(if (show) label else "")
        }
    }

    private fun hoverListener(button: VisImageTextButton): ClickListener {
        return object : ClickListener() {
            override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                hoveredButtons.add(button)
                updateButtonLabels()
            }

            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                hoveredButtons.remove(button)
                updateButtonLabels()
            }
        }
    }

    private fun applyWhiteButtonStyle(button: VisImageTextButton) {
        val drawable = whiteButtonDrawable ?: createWhiteButtonDrawable().also { whiteButtonDrawable = it }
        val style = button.style
        style.up = drawable
        style.down = drawable
        style.checked = drawable
        style.over = drawable
        style.fontColor = Color.BLACK
        style.downFontColor = Color.BLACK
        style.overFontColor = Color.BLACK
        style.checkedFontColor = Color.BLACK
        style.disabledFontColor = Color.DARK_GRAY
    }

    private fun createWhiteButtonDrawable(): TextureRegionDrawable {
        val pixmap = Pixmap(2, 2, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()
        iconTextures.add(texture)
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createDarkBarDrawable(): TextureRegionDrawable {
        val pixmap = Pixmap(2, 2, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.valueOf("555555"))
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()
        iconTextures.add(texture)
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun iconFor(name: String, fallback: com.badlogic.gdx.scenes.scene2d.utils.Drawable?): TextureRegionDrawable {
        return iconDrawables[name] ?: (fallback as? TextureRegionDrawable)
            ?: createActionIconDrawable(Color(0.3f, 0.3f, 0.3f, 1f))
    }

    private fun loadIconDrawables(): Map<String, TextureRegionDrawable> {
        val mapping = mutableMapOf<String, TextureRegionDrawable>()
        val mappingFile = Gdx.files.internal("icons.mapping.csv")
        val textureFile = Gdx.files.internal("icons.png")
        if (!mappingFile.exists() || !textureFile.exists()) {
            return mapping
        }
        val texture = Texture(textureFile)
        iconsTexture = texture
        val lines = mappingFile.readString("UTF-8").lines().filter { it.isNotBlank() }
        lines.drop(1).forEach { line ->
            val parts = line.split('|')
            if (parts.size < 8) {
                return@forEach
            }
            val name = parts[1].trim()
            val startX = parts[4].trim().toInt()
            val endX = parts[5].trim().toInt()
            val startY = parts[6].trim().toInt()
            val endY = parts[7].trim().toInt()
            val width = endX - startX + 1
            val height = endY - startY + 1
            val region = TextureRegion(texture, startX, startY, width, height)
            mapping[name] = TextureRegionDrawable(region)
        }
        return mapping
    }

    data class SelectionInfo(val edgeCount: Int, val faceCount: Int, val groupCount: Int)

    data class GroupInfo(val id: String, val name: String, val glued: Boolean, val editing: Boolean)

    data class ObjectPrototypeInfo(val id: String, val name: String, val instanceCount: Int)
}

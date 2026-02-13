package com.github.alfu32.sketch.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.math.Vector2
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
import com.github.alfu32.sketch.plugin.capabilities.PanelPosition
import com.github.alfu32.sketch.plugin.capabilities.PluginPanel
import com.github.alfu32.sketch.tools.ArchitectureSettings
import com.github.alfu32.sketch.tools.PolylineSettings
import java.util.Locale
import kotlin.math.abs

class SketchUiOverlay(
    private val controller: ToolController,
    private val status: StatusModel,
    private val cleanupAction: () -> Unit,
    private val deleteSelectionAction: () -> Unit,
    private val flipFacesAction: () -> Unit,
    private val voxelizeFacesAction: () -> Unit,
    private val selectionInfoProvider: () -> SelectionInfo,
    private val selectionTextChanged: (String, String) -> Unit,
    private val selectionTextSizeChanged: (String, Float) -> Unit,
    private val selectionTextScreenChanged: (String, Boolean) -> Unit,
    private val groupInfoProvider: () -> GroupInfo?,
    private val groupNameChanged: (String) -> Unit,
    private val groupGlueChanged: (Boolean) -> Unit,
    private val objectPrototypeProvider: () -> List<ObjectPrototypeInfo>,
    private val objectPrototypePlace: (String) -> Unit,
    private val objectPrototypeDelete: (String) -> Unit,
    private val modelUnitProvider: () -> com.github.alfu32.sketch.model.ModelUnit,
    private val modelUnitChanged: (String, Float) -> Unit,
    private val gridSpacingProvider: () -> Float,
    private val gridSpacingChanged: (Float) -> Unit,
    private val snapEpsilonProvider: () -> Float,
    private val snapEpsilonChanged: (Float) -> Unit,
    private val lightingSettings: LightingSettings,
    private val lightingChanged: (LightingSettings) -> Unit,
    private val shadowSettings: ShadowSettings,
    private val shadowChanged: (ShadowSettings) -> Unit,
    private val polylineSettings: PolylineSettings,
    private val architectureSettings: ArchitectureSettings,
    private val architectureElementProvider: () -> ArchitectureElementInfo?,
    private val architectureWallChanged: (String, Float, Float, Float, Color, Color) -> Unit,
    private val architectureSlabChanged: (String, Float, Color, Color, Color) -> Unit,
    private val architectureStairChanged: (String, Float, Int, Float, Boolean, Boolean, Color, Color) -> Unit,
    private val architectureFrameChanged: (String, Float, Float, Color) -> Unit,
    private val cameraModeProvider: () -> CameraMode,
    private val cameraModeChanged: (CameraMode) -> Unit
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
            val top = y + height
            collapsed = !collapsed
            val title = getTitleTable()
            children.forEach { child ->
                if (child !== title) {
                    child.isVisible = !collapsed
                }
            }
            invalidateHierarchy()
            pack()
            setY(top - height)
        }
    }

    val stage: Stage = Stage(ScreenViewport())
    private val toolButtons = mutableMapOf<ToolId, VisImageTextButton>()
    private val toolButtonByWidget = mutableMapOf<VisImageTextButton, ToolId>()
    private val buttonLabels = mutableMapOf<VisImageTextButton, String>()
    private val buttonMarkers = mutableMapOf<VisImageTextButton, Image>()
    private val hoveredButtons = mutableSetOf<VisImageTextButton>()
    private val builtInToolbars = linkedMapOf<String, CollapsibleWindow>()
    private val pluginToolButtons = mutableMapOf<String, VisImageTextButton>()
    private val pluginToolByWidget = mutableMapOf<VisImageTextButton, String>()
    private val pluginToolbars = mutableMapOf<String, CollapsibleWindow>()
    private val cameraModeButtons = mutableMapOf<CameraMode, VisTextButton>()
    private var updatingCameraModeButtons = false
    private val pluginPanels = mutableMapOf<String, CollapsibleWindow>()
    private val pluginPanelPositions = mutableMapOf<String, PanelPosition>()
    private var hoverPopoverWindow: CollapsibleWindow? = null
    private var hoverPopoverTarget: VisImageTextButton? = null
    private var hoverPopoverText: String = ""
    private var hoverPopoverElapsed = 0f
    private val hoverPopoverDelay = 0.5f
    private var toolbarsPositioned = false
    private val uiPrefs by lazy { Gdx.app.getPreferences("k3d-ui-layout") }
    private val toolbarLayoutVersionKey = "builtin_toolbar_layout_version"
    private val toolbarLayoutVersion = 6
    private val toolbarButtonSize = 32f
    private val archDefaultWallThicknessKey = "arch_default_wall_thickness"
    private val archDefaultWallHeightKey = "arch_default_wall_height"
    private val archDefaultWallInclinationKey = "arch_default_wall_inclination"
    private val archDefaultWallExteriorColorKey = "arch_default_wall_exterior_color"
    private val archDefaultWallInteriorColorKey = "arch_default_wall_interior_color"
    private val archDefaultSlabThicknessKey = "arch_default_slab_thickness"
    private val archDefaultSlabTopColorKey = "arch_default_slab_top_color"
    private val archDefaultSlabBottomColorKey = "arch_default_slab_bottom_color"
    private val archDefaultSlabSideColorKey = "arch_default_slab_side_color"
    private val archDefaultStairHeightKey = "arch_default_stair_height"
    private val archDefaultStairStepsKey = "arch_default_stair_steps"
    private val archDefaultStairSupportKey = "arch_default_stair_support"
    private val archDefaultStairRailLeftKey = "arch_default_stair_rail_left"
    private val archDefaultStairRailRightKey = "arch_default_stair_rail_right"
    private val archDefaultStairTreadColorKey = "arch_default_stair_tread_color"
    private val archDefaultStairSupportColorKey = "arch_default_stair_support_color"
    private val archDefaultFrameDepthKey = "arch_default_frame_depth"
    private val archDefaultFrameWidthKey = "arch_default_frame_width"
    private val archDefaultFrameColorKey = "arch_default_frame_color"
    private val iconTextures = mutableListOf<Texture>()
    private val iconDrawables = mutableMapOf<String, TextureRegionDrawable>()
    private var iconsTexture: Texture? = null
    private var buttonUpDrawable: TextureRegionDrawable? = null
    private var markerDrawable: TextureRegionDrawable? = null
    private var darkBarDrawable: TextureRegionDrawable? = null
    private val toolLabel = VisLabel()
    private val messageLabel = VisLabel()
    private val copyLabel = VisLabel()
    private val inputLabel = VisLabel()
    private val cursorLabel = VisLabel()
    private val selectionEdgesLabel = VisLabel()
    private val selectionFacesLabel = VisLabel()
    private val selectionVoxelsLabel = VisLabel()
    private val selectionGroupsLabel = VisLabel()
    private val selectionDimensionsLabel = VisLabel()
    private val selectionTextsLabel = VisLabel()
    private val selectionTextLabel = VisLabel("Text")
    private val selectionTextField = VisTextField()
    private val selectionTextSizeLabel = VisLabel("Text size")
    private val selectionTextSizeField = VisTextField()
    private val selectionTextScreenCheck = VisCheckBox("Screen text")
    private var updatingSelectionFields = false
    private var selectionTextId: String? = null
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
    private lateinit var modelSettingsPanel: CollapsibleWindow
    private lateinit var polylineSettingsPanel: CollapsibleWindow
    private lateinit var architectureSettingsPanel: CollapsibleWindow
    private lateinit var architectureModeLabel: VisLabel
    private lateinit var architectureWallThicknessLabel: VisLabel
    private lateinit var architectureWallThicknessField: VisTextField
    private lateinit var architectureWallHeightLabel: VisLabel
    private lateinit var architectureWallHeightField: VisTextField
    private lateinit var architectureWallInclinationLabel: VisLabel
    private lateinit var architectureWallInclinationField: VisTextField
    private lateinit var architectureWallExteriorColorLabel: VisLabel
    private lateinit var architectureWallExteriorColorField: VisTextField
    private lateinit var architectureWallExteriorColorButton: VisImageTextButton
    private lateinit var architectureWallInteriorColorLabel: VisLabel
    private lateinit var architectureWallInteriorColorField: VisTextField
    private lateinit var architectureWallInteriorColorButton: VisImageTextButton
    private lateinit var architectureSlabThicknessLabel: VisLabel
    private lateinit var architectureSlabThicknessField: VisTextField
    private lateinit var architectureSlabTopColorLabel: VisLabel
    private lateinit var architectureSlabTopColorField: VisTextField
    private lateinit var architectureSlabTopColorButton: VisImageTextButton
    private lateinit var architectureSlabBottomColorLabel: VisLabel
    private lateinit var architectureSlabBottomColorField: VisTextField
    private lateinit var architectureSlabBottomColorButton: VisImageTextButton
    private lateinit var architectureSlabSideColorLabel: VisLabel
    private lateinit var architectureSlabSideColorField: VisTextField
    private lateinit var architectureSlabSideColorButton: VisImageTextButton
    private lateinit var architectureStairHeightLabel: VisLabel
    private lateinit var architectureStairHeightField: VisTextField
    private lateinit var architectureStairStepsLabel: VisLabel
    private lateinit var architectureStairStepsField: VisTextField
    private lateinit var architectureStairSupportLabel: VisLabel
    private lateinit var architectureStairSupportField: VisTextField
    private lateinit var architectureStairLeftRailLabel: VisLabel
    private lateinit var architectureStairLeftRailCheck: VisCheckBox
    private lateinit var architectureStairRightRailLabel: VisLabel
    private lateinit var architectureStairRightRailCheck: VisCheckBox
    private lateinit var architectureStairTreadColorLabel: VisLabel
    private lateinit var architectureStairTreadColorField: VisTextField
    private lateinit var architectureStairTreadColorButton: VisImageTextButton
    private lateinit var architectureStairSupportColorLabel: VisLabel
    private lateinit var architectureStairSupportColorField: VisTextField
    private lateinit var architectureStairSupportColorButton: VisImageTextButton
    private lateinit var architectureFrameDepthLabel: VisLabel
    private lateinit var architectureFrameDepthField: VisTextField
    private lateinit var architectureFrameWidthLabel: VisLabel
    private lateinit var architectureFrameWidthField: VisTextField
    private lateinit var architectureFrameColorLabel: VisLabel
    private lateinit var architectureFrameColorField: VisTextField
    private lateinit var architectureFrameColorButton: VisImageTextButton
    private lateinit var architectureSettingsContent: VisTable
    private var architecturePanelMode: ArchitectureElementKind? = null
    private var updatingArchitectureFields = false
    private val unitNameField = VisTextField()
    private val unitSizeField = VisTextField()
    private val gridSpacingField = VisTextField()
    private val snapEpsilonMin = 2f
    private val snapEpsilonMax = 48f
    private val snapEpsilonSlider = VisSlider(snapEpsilonMin, snapEpsilonMax, 1f, false)
    private var updatingModelSettingsFields = false
    private var lastUnitName = ""
    private var lastUnitSize = -1f
    private var lastGridSpacing = -1f
    private var lastSnapEpsilon = -1f
    private val lightingRefreshers = mutableListOf<() -> Unit>()
    private lateinit var selectionPanel: CollapsibleWindow
    private var needsPanelLayout = true
    private var pluginManagerPanel: PluginManagerPanel? = null
    private var commandPaletteUI: CommandPaletteUI? = null
    private var pluginHost: PluginHost? = null
    private var pluginPanelsPositioned = false
    private var distancePopup: CollapsibleWindow? = null
    private var distanceField: VisTextField? = null
    private var distanceChangeHandler: ((String) -> Unit)? = null
    private var distanceCommitHandler: ((String) -> Unit)? = null
    private var distanceCancelHandler: (() -> Unit)? = null

    init {
        iconDrawables.putAll(loadIconDrawables())
        migrateBuiltinToolbarPrefs()
        loadArchitectureDefaults()
        val root = Table()
        root.setFillParent(true)
        stage.addActor(root)
        stage.viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)

        buildStandardToolbars().forEach { stage.addActor(it) }
        selectionPanel = buildSelectionPanel()
        groupPanel = buildGroupPanel()
        objectsPanel = buildObjectsPanel()
        modelSettingsPanel = buildModelSettingsPanel()
        polylineSettingsPanel = buildPolylineSettingsPanel()
        architectureSettingsPanel = buildArchitectureSettingsPanel()
        lightingPanel = buildLightingPanel()
        val mainRow = Table()
        mainRow.add().expand().fill()

        root.add(mainRow).expand().fill().row()
        root.add(buildStatusBar()).expandX().fillX().bottom().pad(0f)

        stage.addActor(selectionPanel)
        stage.addActor(groupPanel)
        stage.addActor(objectsPanel)
        stage.addActor(modelSettingsPanel)
        stage.addActor(polylineSettingsPanel)
        stage.addActor(architectureSettingsPanel)
        lightingPanel?.let { stage.addActor(it) }
        positionPanels()
        needsPanelLayout = true

        updateFromStatus()
    }

    fun showDistancePopup(
        screenX: Int,
        screenY: Int,
        text: String,
        onChange: (String) -> Unit,
        onCommit: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        if (distancePopup == null) {
            val popup = CollapsibleWindow("Distance", showCloseButton = false)
            popup.isMovable = false
            popup.isResizable = false
            val field = VisTextField()
            field.messageText = "Enter distance"
            popup.add(field).width(160f).pad(6f)
            popup.pack()
            stage.addActor(popup)
            distancePopup = popup
            distanceField = field
            field.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                    distanceChangeHandler?.invoke(field.text)
                }
            })
            field.addListener(object : com.badlogic.gdx.scenes.scene2d.InputListener() {
                override fun keyDown(
                    event: com.badlogic.gdx.scenes.scene2d.InputEvent?,
                    keycode: Int
                ): Boolean {
                    when (keycode) {
                        com.badlogic.gdx.Input.Keys.ENTER -> {
                            distanceCommitHandler?.invoke(field.text)
                            parkDistancePopup()
                            return true
                        }
                        com.badlogic.gdx.Input.Keys.ESCAPE -> {
                            hideDistancePopup(cancel = true)
                            return true
                        }
                    }
                    return false
                }
            })
        }
        distanceChangeHandler = onChange
        distanceCommitHandler = onCommit
        distanceCancelHandler = onCancel
        val popup = distancePopup ?: return
        val field = distanceField ?: return
        val wasVisible = popup.isVisible
        if (!wasVisible) {
            if (text.isNotBlank()) {
                field.text = text
                field.selectAll()
            } else {
                field.text = ""
            }
        }
        popup.pack()
        val stageCoords = stage.screenToStageCoordinates(com.badlogic.gdx.math.Vector2(screenX.toFloat(), screenY.toFloat()))
        val maxX = (stage.width - popup.width).coerceAtLeast(0f)
        val maxY = (stage.height - popup.height).coerceAtLeast(0f)
        val x = stageCoords.x.coerceIn(0f, maxX)
        val y = stageCoords.y.coerceIn(0f, maxY)
        popup.setPosition(x, y)
        popup.isVisible = true
        popup.toFront()
        stage.keyboardFocus = field
        stage.scrollFocus = field
        if (!wasVisible && field.text.isNotBlank()) {
            distanceChangeHandler?.invoke(field.text)
        }
    }

    fun hideDistancePopup(cancel: Boolean) {
        val popup = distancePopup ?: return
        if (!popup.isVisible) {
            return
        }
        popup.isVisible = false
        if (stage.keyboardFocus === distanceField) {
            stage.keyboardFocus = null
        }
        if (stage.scrollFocus === distanceField) {
            stage.scrollFocus = null
        }
        if (cancel) {
            distanceCancelHandler?.invoke()
        }
        distanceChangeHandler = null
        distanceCommitHandler = null
        distanceCancelHandler = null
    }

    fun parkDistancePopup() {
        val popup = distancePopup ?: return
        val field = distanceField ?: return
        field.text = ""
        popup.pack()
        val x = stage.width - popup.width - 8f
        val y = 8f
        popup.setPosition(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
        popup.isVisible = true
        if (stage.keyboardFocus === field) {
            stage.keyboardFocus = null
        }
        if (stage.scrollFocus === field) {
            stage.scrollFocus = null
        }
    }

    fun updateDistancePopupHover(screenX: Int, screenY: Int) {
        // Kept for compatibility. Popup visibility is explicitly managed by Enter/Escape/Ctrl+N.
    }

    fun isUiCapturingInput(): Boolean {
        return isFocusedUiActor(stage.keyboardFocus)
    }

    fun isUiCapturingInputByPointer(): Boolean {
        if (!isUiCapturingInput()) {
            return false
        }
        return isUiHit(Gdx.input.x, Gdx.input.y)
    }

    fun isUiHit(screenX: Int, screenY: Int): Boolean {
        val stageCoords = stage.screenToStageCoordinates(com.badlogic.gdx.math.Vector2(screenX.toFloat(), screenY.toFloat()))
        val hit = stage.hit(stageCoords.x, stageCoords.y, true) ?: return false
        return hit !== stage.root
    }

    fun clearUiFocus() {
        stage.keyboardFocus = null
        stage.scrollFocus = null
    }

    private fun isFocusedUiActor(actor: Actor?): Boolean {
        return actor != null && actor !== stage.root && actor.isDescendantOf(stage.root)
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
        selectionVoxelsLabel.setText("Voxels: ${selection.voxelCount}")
        selectionGroupsLabel.setText("Objects: ${selection.groupCount}")
        selectionDimensionsLabel.setText("Dimensions: ${selection.dimensionCount}")
        selectionTextsLabel.setText("Texts: ${selection.textCount}")
        val hasTextSelection = selection.selectedTextId != null
        selectionTextLabel.isVisible = hasTextSelection
        selectionTextField.isVisible = hasTextSelection
        selectionTextSizeLabel.isVisible = hasTextSelection
        selectionTextSizeField.isVisible = hasTextSelection
        selectionTextScreenCheck.isVisible = hasTextSelection
        updatingSelectionFields = true
        selectionTextId = selection.selectedTextId
        selectionTextField.text = selection.selectedTextValue ?: ""
        selectionTextSizeField.text = selection.selectedTextSize?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        selectionTextScreenCheck.isChecked = selection.selectedTextScreen ?: true
        updatingSelectionFields = false
        updateGroupPanel()
        updateObjectsPanel()
        updateModelSettingsPanel()
        updateArchitectureSettingsPanel()
        refreshPluginToolbar()
        toolButtons[status.activeTool]?.isChecked = true
        updatePluginToolSelection()
        updatePaintColorButton()
        updateButtonLabels()
        syncCameraModeButtons()
    }

    fun refreshLightingControls() {
        lightingRefreshers.forEach { it.invoke() }
    }

    private fun syncCameraModeButtons() {
        if (cameraModeButtons.isEmpty()) {
            return
        }
        updatingCameraModeButtons = true
        val mode = cameraModeProvider()
        cameraModeButtons.forEach { (cameraMode, button) ->
            button.isChecked = cameraMode == mode
        }
        updatingCameraModeButtons = false
    }

    fun act(delta: Float) {
        updateFromStatus()
        if (needsPanelLayout) {
            positionPanels()
            needsPanelLayout = false
        }
        updateHoverPopover(delta)
        stage.act(delta)
    }

    fun draw() {
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        needsPanelLayout = true
        toolbarsPositioned = false
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

    fun showModelSettingsPanel() {
        modelSettingsPanel.isVisible = true
        needsPanelLayout = true
    }

    fun showPolylineSettingsPanel() {
        polylineSettingsPanel.isVisible = true
        needsPanelLayout = true
    }

    fun showArchitectureSettingsPanel() {
        architectureSettingsPanel.isVisible = true
        needsPanelLayout = true
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

    fun refreshPluginPanels() {
        pluginManagerPanel?.refresh(force = true)
        commandPaletteUI?.refreshList()
        rebuildPluginPanels()
    }

    fun dispose() {
        stage.dispose()
        iconTextures.forEach { it.dispose() }
        iconsTexture?.dispose()
    }

    private fun buildStandardToolbars(): List<CollapsibleWindow> {
        val toolGroup = ButtonGroup<VisImageTextButton>()
        toolGroup.setMaxCheckCount(1)
        toolGroup.setMinCheckCount(1)
        toolGroup.setUncheckLast(false)

        val constructionTools = listOf(
            ToolId.LINE,
            ToolId.CONSTRUCTION_LINE,
            ToolId.POLYLINE,
            ToolId.DOUBLE_LINE,
            ToolId.RECTANGLE,
            ToolId.SURFACE_RECTANGLE,
            ToolId.QUAD,
            ToolId.CIRCLE,
            ToolId.LINEAR_DIMENSION,
            ToolId.TEXT,
            ToolId.FACE_OUTLINE,
            ToolId.LINE_OFFSET,
            ToolId.CUT_OUT_3,
            ToolId.PLANE_SECTION,
            ToolId.MESH_INTERSECTION
        )
        val modificationTools = listOf(
            ToolId.SELECT,
            ToolId.PUSH_PULL,
            ToolId.MOVE,
            ToolId.ROTATE,
            ToolId.SCALE,
            ToolId.STRETCH,
            ToolId.PAINT
        )
        val voxelTools = listOf(
            ToolId.VOXEL,
            ToolId.VOXEL_VOLUME,
            ToolId.VOXEL_FRAME
        )
        val architectureTools = listOf(
            ToolId.ARCH_WALL,
            ToolId.ARCH_SLAB,
            ToolId.ARCH_STAIR,
            ToolId.ARCH_ADD_HOLE,
            ToolId.ARCH_WINDOW_FRAME,
            ToolId.ARCH_DOOR_FRAME
        )

        val construction = buildToolsToolbarWindow(
            title = "Construction",
            toolbarId = "builtin_toolbar_construction",
            toolIds = constructionTools,
            group = toolGroup
        )
        val modification = buildToolsToolbarWindow(
            title = "Modification",
            toolbarId = "builtin_toolbar_modification",
            toolIds = modificationTools,
            group = toolGroup
        )
        val voxel = buildToolsToolbarWindow(
            title = "Voxel",
            toolbarId = "builtin_toolbar_voxel",
            toolIds = voxelTools,
            group = toolGroup,
            extraButtons = listOf(
                createActionButton(
                    label = "Voxelize Faces",
                    icon = iconFor("voxelize", createActionIconDrawable(Color(0.7f, 0.85f, 0.4f, 1f)))
                ) { voxelizeFacesAction() }
            )
        )
        val architecture = buildToolsToolbarWindow(
            title = "Architecture",
            toolbarId = "builtin_toolbar_architecture",
            toolIds = architectureTools,
            group = toolGroup
        )
        val actions = buildActionsToolbarWindow(
            title = "Actions",
            toolbarId = "builtin_toolbar_actions"
        )
        val camera = buildCameraToolbarWindow(
            title = "Camera",
            toolbarId = "builtin_toolbar_camera"
        )

        builtInToolbars.clear()
        builtInToolbars["builtin_toolbar_construction"] = construction
        builtInToolbars["builtin_toolbar_modification"] = modification
        builtInToolbars["builtin_toolbar_architecture"] = architecture
        builtInToolbars["builtin_toolbar_voxel"] = voxel
        builtInToolbars["builtin_toolbar_actions"] = actions
        builtInToolbars["builtin_toolbar_camera"] = camera
        toolbarsPositioned = false
        return listOf(construction, modification, architecture, voxel, actions, camera)
    }

    private fun buildToolsToolbarWindow(
        title: String,
        toolbarId: String,
        toolIds: List<ToolId>,
        group: ButtonGroup<VisImageTextButton>,
        extraButtons: List<VisImageTextButton> = emptyList()
    ): CollapsibleWindow {
        val window = CollapsibleWindow(title, showCloseButton = false)
        window.isResizable = false
        val content = VisTable()
        content.defaults().pad(2f).left()
        toolIds.forEach { toolId ->
            val button = createToolButton(toolId, group)
            content.add(button).size(toolbarButtonSize, toolbarButtonSize)
        }
        extraButtons.forEach { button ->
            content.add(button).size(toolbarButtonSize, toolbarButtonSize)
        }
        window.add(content).pad(4f).left()
        window.pack()
        window.setSize(window.prefWidth, window.prefHeight)
        attachToolbarPersistence(window, toolbarId)
        return window
    }

    private fun createToolButton(toolId: ToolId, group: ButtonGroup<VisImageTextButton>): VisImageTextButton {
        val icon = createIconDrawable(toolId)
        val button = VisImageTextButton(toolId.displayName, icon)
        applyWhiteButtonStyle(button)
        applyIconStyle(button, icon)
        button.setText("")
        button.isChecked = toolId == status.activeTool
        button.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                controller.setTool(toolId)
            }
        })
        button.addListener(hoverListener(button))
        attachButtonMarker(button)
        group.add(button)
        toolButtons[toolId] = button
        toolButtonByWidget[button] = toolId
        buttonLabels[button] = toolId.displayName
        return button
    }

    private fun buildActionsToolbarWindow(title: String, toolbarId: String): CollapsibleWindow {
        val window = CollapsibleWindow(title, showCloseButton = false)
        window.isResizable = false
        val content = VisTable()
        content.defaults().pad(2f).left()

        val cleanupButton = createActionButton(
            label = "Cleanup",
            icon = iconFor("cleanup", createActionIconDrawable(Color(0.55f, 0.85f, 0.65f, 1f)))
        ) {
            cleanupAction()
        }

        val colorButton = createActionButton(
            label = "Color",
            icon = iconFor("color", createActionIconDrawable(status.paintColor))
        ) {
            showColorPicker()
        }
        paintColorButton = colorButton

        val deleteButton = createActionButton(
            label = "Delete",
            icon = iconFor("delete", createActionIconDrawable(Color(0.9f, 0.45f, 0.45f, 1f)))
        ) {
            deleteSelectionAction()
        }

        val flipButton = createActionButton(
            label = "Flip Faces",
            icon = iconFor("flip_faces", createActionIconDrawable(Color(0.45f, 0.65f, 0.95f, 1f)))
        ) {
            flipFacesAction()
        }

        val lightingButton = createActionButton(
            label = "Lighting",
            icon = iconFor("lighting", createActionIconDrawable(Color(0.95f, 0.85f, 0.2f, 1f)))
        ) {
            lightingPanel?.let { panel ->
                panel.isVisible = !panel.isVisible
                needsPanelLayout = true
                panel.toFront()
            }
        }

        val pluginButton = createActionButton(
            label = "Plugin Manager",
            icon = iconFor("plugins", createActionIconDrawable(Color(0.6f, 0.6f, 0.6f, 1f)))
        ) {
            togglePluginManager()
        }
        val buttons = listOf(cleanupButton, colorButton, deleteButton, flipButton, lightingButton, pluginButton)
        buttons.forEach { button ->
            content.add(button).size(toolbarButtonSize, toolbarButtonSize)
        }

        window.add(content).pad(4f).left()
        window.pack()
        window.setSize(window.prefWidth, window.prefHeight)
        attachToolbarPersistence(window, toolbarId)
        return window
    }

    private fun buildCameraToolbarWindow(title: String, toolbarId: String): CollapsibleWindow {
        val window = CollapsibleWindow(title, showCloseButton = false)
        window.isResizable = false
        val content = VisTable()
        content.defaults().pad(2f).left()

        val group = ButtonGroup<VisTextButton>().apply {
            setMaxCheckCount(1)
            setMinCheckCount(1)
            setUncheckLast(false)
        }
        cameraModeButtons.clear()
        listOf(
            CameraMode.ORBIT to "Orbit",
            CameraMode.WALKTHROUGH to "Walk",
            CameraMode.ORTHOGRAPHIC to "Ortho"
        ).forEach { (mode, label) ->
            val button = VisTextButton(label, "toggle")
            button.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (updatingCameraModeButtons) {
                        return
                    }
                    cameraModeChanged(mode)
                }
            })
            cameraModeButtons[mode] = button
            group.add(button)
            content.add(button).height(toolbarButtonSize).minWidth(54f)
        }
        syncCameraModeButtons()

        window.add(content).pad(4f).left()
        window.pack()
        window.setSize(window.prefWidth, window.prefHeight)
        attachToolbarPersistence(window, toolbarId)
        return window
    }

    private fun createActionButton(
        label: String,
        icon: com.badlogic.gdx.scenes.scene2d.utils.Drawable,
        onClick: () -> Unit
    ): VisImageTextButton {
        val button = VisImageTextButton(label, icon)
        applyWhiteButtonStyle(button)
        applyIconStyle(button, icon)
        button.setText("")
        buttonLabels[button] = label
        button.addListener(hoverListener(button))
        attachButtonMarker(button)
        button.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                onClick()
            }
        })
        return button
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
        content.add(selectionVoxelsLabel).row()
        content.add(selectionGroupsLabel).row()
        content.add(selectionDimensionsLabel).row()
        content.add(selectionTextsLabel).row()
        content.add(selectionTextLabel).left().padTop(4f).row()
        content.add(selectionTextField).growX().row()
        content.add(selectionTextSizeLabel).left().padTop(4f).row()
        content.add(selectionTextSizeField).growX().row()
        content.add(selectionTextScreenCheck).left().padTop(4f).row()
        panel.add(content).grow()

        selectionTextField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (updatingSelectionFields) {
                    return
                }
                val targetId = selectionTextId ?: return
                selectionTextChanged(targetId, selectionTextField.text)
            }
        })
        selectionTextSizeField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (updatingSelectionFields) {
                    return
                }
                val targetId = selectionTextId ?: return
                val size = selectionTextSizeField.text.toFloatOrNull() ?: return
                if (size > 0f) {
                    selectionTextSizeChanged(targetId, size)
                }
            }
        })
        selectionTextScreenCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (updatingSelectionFields) {
                    return
                }
                val targetId = selectionTextId ?: return
                selectionTextScreenChanged(targetId, selectionTextScreenCheck.isChecked)
            }
        })
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

    private fun buildModelSettingsPanel(): CollapsibleWindow {
        val panel = CollapsibleWindow("Model Settings")
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        content.add(VisLabel("Unit name")).left().row()
        content.add(unitNameField).growX().row()
        content.add(VisLabel("Unit size")).left().padTop(4f).row()
        content.add(unitSizeField).growX().row()
        content.add(VisLabel("Grid size")).left().padTop(4f).row()
        content.add(gridSpacingField).growX().row()
        content.add(VisLabel("Snap radius")).left().padTop(6f).row()
        content.add(snapEpsilonSlider).growX().row()
        panel.add(content).growX()
        panel.isVisible = false

        unitNameField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (updatingModelSettingsFields) {
                    return
                }
                val name = unitNameField.text.trim()
                val size = unitSizeField.text.toFloatOrNull() ?: return
                if (name.isNotBlank()) {
                    modelUnitChanged(name, size)
                }
            }
        })
        unitSizeField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (updatingModelSettingsFields) {
                    return
                }
                val name = unitNameField.text.trim()
                val size = unitSizeField.text.toFloatOrNull() ?: return
                if (name.isNotBlank()) {
                    modelUnitChanged(name, size)
                }
            }
        })
        gridSpacingField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (updatingModelSettingsFields) {
                    return
                }
                val gridSize = gridSpacingField.text.toFloatOrNull() ?: return
                if (gridSize > 0f) {
                    gridSpacingChanged(gridSize)
                }
            }
        })
        snapEpsilonSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (updatingModelSettingsFields) {
                    return
                }
                snapEpsilonChanged(snapEpsilonSlider.value)
            }
        })
        return panel
    }

    private fun buildPolylineSettingsPanel(): CollapsibleWindow {
        val panel = CollapsibleWindow("Polyline Settings")
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        val arcField = VisTextField(String.format(Locale.US, "%.3f", polylineSettings.arcMaxLength))
        val sizeField = VisTextField(String.format(Locale.US, "%.3f", polylineSettings.doubleLineSize))
        val offsetField = VisTextField(String.format(Locale.US, "%.3f", polylineSettings.doubleLineOffset))
        content.add(VisLabel("Arc max length")).left().row()
        content.add(arcField).growX().row()
        content.add(VisLabel("Double line size")).left().padTop(4f).row()
        content.add(sizeField).growX().row()
        content.add(VisLabel("Double line offset")).left().padTop(4f).row()
        content.add(offsetField).growX().row()
        panel.add(content).growX()
        panel.isVisible = false

        arcField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                val value = arcField.text.toFloatOrNull() ?: return
                polylineSettings.arcMaxLength = value.coerceAtLeast(0.001f)
            }
        })
        sizeField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                val value = sizeField.text.toFloatOrNull() ?: return
                polylineSettings.doubleLineSize = value.coerceAtLeast(0f)
            }
        })
        offsetField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                val value = offsetField.text.toFloatOrNull() ?: return
                polylineSettings.doubleLineOffset = value
            }
        })
        return panel
    }

    private fun buildArchitectureSettingsPanel(): CollapsibleWindow {
        val panel = CollapsibleWindow("Architecture Settings")
        architectureSettingsContent = VisTable()
        architectureSettingsContent.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        architectureSettingsContent.defaults().pad(4f).left().top()
        architectureModeLabel = VisLabel()
        architectureWallThicknessLabel = VisLabel("Wall thickness")
        architectureWallThicknessField = VisTextField()
        architectureWallHeightLabel = VisLabel("Wall height")
        architectureWallHeightField = VisTextField()
        architectureWallInclinationLabel = VisLabel("Wall inclination (deg)")
        architectureWallInclinationField = VisTextField()
        architectureWallExteriorColorLabel = VisLabel("Wall exterior color")
        architectureWallExteriorColorField = VisTextField()
        architectureWallExteriorColorButton = createArchitectureColorButton("Wall Exterior Color", architectureWallExteriorColorField)
        architectureWallInteriorColorLabel = VisLabel("Wall interior color")
        architectureWallInteriorColorField = VisTextField()
        architectureWallInteriorColorButton = createArchitectureColorButton("Wall Interior Color", architectureWallInteriorColorField)
        architectureSlabThicknessLabel = VisLabel("Slab thickness")
        architectureSlabThicknessField = VisTextField()
        architectureSlabTopColorLabel = VisLabel("Slab top color")
        architectureSlabTopColorField = VisTextField()
        architectureSlabTopColorButton = createArchitectureColorButton("Slab Top Color", architectureSlabTopColorField)
        architectureSlabBottomColorLabel = VisLabel("Slab bottom color")
        architectureSlabBottomColorField = VisTextField()
        architectureSlabBottomColorButton = createArchitectureColorButton("Slab Bottom Color", architectureSlabBottomColorField)
        architectureSlabSideColorLabel = VisLabel("Slab side color")
        architectureSlabSideColorField = VisTextField()
        architectureSlabSideColorButton = createArchitectureColorButton("Slab Side Color", architectureSlabSideColorField)
        architectureStairHeightLabel = VisLabel("Stair height")
        architectureStairHeightField = VisTextField()
        architectureStairStepsLabel = VisLabel("Stair steps")
        architectureStairStepsField = VisTextField()
        architectureStairSupportLabel = VisLabel("Stair support thickness")
        architectureStairSupportField = VisTextField()
        architectureStairLeftRailLabel = VisLabel("Stair left rail grid")
        architectureStairLeftRailCheck = VisCheckBox("Enabled")
        architectureStairRightRailLabel = VisLabel("Stair right rail grid")
        architectureStairRightRailCheck = VisCheckBox("Enabled")
        architectureStairTreadColorLabel = VisLabel("Stair tread color")
        architectureStairTreadColorField = VisTextField()
        architectureStairTreadColorButton = createArchitectureColorButton("Stair Tread Color", architectureStairTreadColorField)
        architectureStairSupportColorLabel = VisLabel("Stair support color")
        architectureStairSupportColorField = VisTextField()
        architectureStairSupportColorButton = createArchitectureColorButton("Stair Support Color", architectureStairSupportColorField)
        architectureFrameDepthLabel = VisLabel("Frame depth")
        architectureFrameDepthField = VisTextField()
        architectureFrameWidthLabel = VisLabel("Frame width")
        architectureFrameWidthField = VisTextField()
        architectureFrameColorLabel = VisLabel("Frame color")
        architectureFrameColorField = VisTextField()
        architectureFrameColorButton = createArchitectureColorButton("Frame Color", architectureFrameColorField)

        panel.add(architectureSettingsContent).growX()
        panel.isVisible = false

        architectureWallThicknessField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureWallThicknessField.text.toFloatOrNull()?.coerceAtLeast(0.01f) ?: return
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.wallThickness = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.WALL) {
                    architectureWallChanged(
                        selection.id,
                        value,
                        selection.wallHeight ?: 2.7f,
                        selection.wallInclinationDeg ?: 0f,
                        selection.wallExteriorColor ?: Color(architectureSettings.wallExteriorColor),
                        selection.wallInteriorColor ?: Color(architectureSettings.wallInteriorColor)
                    )
                }
            }
        })
        architectureWallHeightField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureWallHeightField.text.toFloatOrNull()?.coerceAtLeast(0.05f) ?: return
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.wallHeight = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.WALL) {
                    architectureWallChanged(
                        selection.id,
                        selection.wallThickness ?: 0.2f,
                        value,
                        selection.wallInclinationDeg ?: 0f,
                        selection.wallExteriorColor ?: Color(architectureSettings.wallExteriorColor),
                        selection.wallInteriorColor ?: Color(architectureSettings.wallInteriorColor)
                    )
                }
            }
        })
        architectureWallInclinationField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureWallInclinationField.text.toFloatOrNull() ?: return
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.wallInclinationDeg = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.WALL) {
                    architectureWallChanged(
                        selection.id,
                        selection.wallThickness ?: 0.2f,
                        selection.wallHeight ?: 2.7f,
                        value,
                        selection.wallExteriorColor ?: Color(architectureSettings.wallExteriorColor),
                        selection.wallInteriorColor ?: Color(architectureSettings.wallInteriorColor)
                    )
                }
            }
        })
        architectureSlabThicknessField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureSlabThicknessField.text.toFloatOrNull()?.coerceAtLeast(0.01f) ?: return
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.slabThickness = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.SLAB) {
                    architectureSlabChanged(
                        selection.id,
                        value,
                        selection.slabTopColor ?: Color(architectureSettings.slabTopColor),
                        selection.slabBottomColor ?: Color(architectureSettings.slabBottomColor),
                        selection.slabSideColor ?: Color(architectureSettings.slabSideColor)
                    )
                }
            }
        })
        architectureStairHeightField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureStairHeightField.text.toFloatOrNull()?.coerceAtLeast(0.05f) ?: return
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.stairHeight = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.STAIR) {
                    architectureStairChanged(
                        selection.id,
                        value,
                        selection.stairStepCount ?: 14,
                        selection.stairSupportThickness ?: 0.2f,
                        selection.stairRailLeftEnabled ?: architectureSettings.stairRailLeftEnabled,
                        selection.stairRailRightEnabled ?: architectureSettings.stairRailRightEnabled,
                        selection.stairTreadColor ?: Color(architectureSettings.stairTreadColor),
                        selection.stairSupportColor ?: Color(architectureSettings.stairSupportColor)
                    )
                }
            }
        })
        architectureStairStepsField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureStairStepsField.text.toIntOrNull()?.coerceAtLeast(1) ?: return
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.stairStepCount = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.STAIR) {
                    architectureStairChanged(
                        selection.id,
                        selection.stairHeight ?: 2.7f,
                        value,
                        selection.stairSupportThickness ?: 0.2f,
                        selection.stairRailLeftEnabled ?: architectureSettings.stairRailLeftEnabled,
                        selection.stairRailRightEnabled ?: architectureSettings.stairRailRightEnabled,
                        selection.stairTreadColor ?: Color(architectureSettings.stairTreadColor),
                        selection.stairSupportColor ?: Color(architectureSettings.stairSupportColor)
                    )
                }
            }
        })
        architectureStairSupportField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureStairSupportField.text.toFloatOrNull()?.coerceAtLeast(0.01f) ?: return
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.stairSupportThickness = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.STAIR) {
                    architectureStairChanged(
                        selection.id,
                        selection.stairHeight ?: 2.7f,
                        selection.stairStepCount ?: 14,
                        value,
                        selection.stairRailLeftEnabled ?: architectureSettings.stairRailLeftEnabled,
                        selection.stairRailRightEnabled ?: architectureSettings.stairRailRightEnabled,
                        selection.stairTreadColor ?: Color(architectureSettings.stairTreadColor),
                        selection.stairSupportColor ?: Color(architectureSettings.stairSupportColor)
                    )
                }
            }
        })
        architectureStairLeftRailCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureStairLeftRailCheck.isChecked
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.stairRailLeftEnabled = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.STAIR) {
                    architectureStairChanged(
                        selection.id,
                        selection.stairHeight ?: architectureSettings.stairHeight,
                        selection.stairStepCount ?: architectureSettings.stairStepCount,
                        selection.stairSupportThickness ?: architectureSettings.stairSupportThickness,
                        value,
                        selection.stairRailRightEnabled ?: architectureSettings.stairRailRightEnabled,
                        selection.stairTreadColor ?: Color(architectureSettings.stairTreadColor),
                        selection.stairSupportColor ?: Color(architectureSettings.stairSupportColor)
                    )
                }
            }
        })
        architectureStairRightRailCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureStairRightRailCheck.isChecked
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.stairRailRightEnabled = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.STAIR) {
                    architectureStairChanged(
                        selection.id,
                        selection.stairHeight ?: architectureSettings.stairHeight,
                        selection.stairStepCount ?: architectureSettings.stairStepCount,
                        selection.stairSupportThickness ?: architectureSettings.stairSupportThickness,
                        selection.stairRailLeftEnabled ?: architectureSettings.stairRailLeftEnabled,
                        value,
                        selection.stairTreadColor ?: Color(architectureSettings.stairTreadColor),
                        selection.stairSupportColor ?: Color(architectureSettings.stairSupportColor)
                    )
                }
            }
        })
        architectureFrameDepthField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureFrameDepthField.text.toFloatOrNull()?.coerceAtLeast(0.01f) ?: return
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.frameDepth = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.FRAME) {
                    architectureFrameChanged(
                        selection.id,
                        value,
                        selection.frameWidth ?: 0.06f,
                        selection.frameColor ?: Color(architectureSettings.frameColor)
                    )
                }
            }
        })
        architectureFrameWidthField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val value = architectureFrameWidthField.text.toFloatOrNull()?.coerceAtLeast(0.01f) ?: return
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.frameWidth = value
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.FRAME) {
                    architectureFrameChanged(
                        selection.id,
                        selection.frameDepth ?: 0.12f,
                        value,
                        selection.frameColor ?: Color(architectureSettings.frameColor)
                    )
                }
            }
        })
        architectureWallExteriorColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val color = parseColorField(architectureWallExteriorColorField.text) ?: return
                updateArchitectureColorButtonSwatch(architectureWallExteriorColorButton, color)
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.wallExteriorColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.WALL) {
                    architectureWallChanged(
                        selection.id,
                        selection.wallThickness ?: architectureSettings.wallThickness,
                        selection.wallHeight ?: architectureSettings.wallHeight,
                        selection.wallInclinationDeg ?: architectureSettings.wallInclinationDeg,
                        color,
                        selection.wallInteriorColor ?: Color(architectureSettings.wallInteriorColor)
                    )
                }
            }
        })
        architectureWallInteriorColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val color = parseColorField(architectureWallInteriorColorField.text) ?: return
                updateArchitectureColorButtonSwatch(architectureWallInteriorColorButton, color)
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.wallInteriorColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.WALL) {
                    architectureWallChanged(
                        selection.id,
                        selection.wallThickness ?: architectureSettings.wallThickness,
                        selection.wallHeight ?: architectureSettings.wallHeight,
                        selection.wallInclinationDeg ?: architectureSettings.wallInclinationDeg,
                        selection.wallExteriorColor ?: Color(architectureSettings.wallExteriorColor),
                        color
                    )
                }
            }
        })
        architectureSlabTopColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val color = parseColorField(architectureSlabTopColorField.text) ?: return
                updateArchitectureColorButtonSwatch(architectureSlabTopColorButton, color)
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.slabTopColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.SLAB) {
                    architectureSlabChanged(
                        selection.id,
                        selection.slabThickness ?: architectureSettings.slabThickness,
                        color,
                        selection.slabBottomColor ?: Color(architectureSettings.slabBottomColor),
                        selection.slabSideColor ?: Color(architectureSettings.slabSideColor)
                    )
                }
            }
        })
        architectureSlabBottomColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val color = parseColorField(architectureSlabBottomColorField.text) ?: return
                updateArchitectureColorButtonSwatch(architectureSlabBottomColorButton, color)
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.slabBottomColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.SLAB) {
                    architectureSlabChanged(
                        selection.id,
                        selection.slabThickness ?: architectureSettings.slabThickness,
                        selection.slabTopColor ?: Color(architectureSettings.slabTopColor),
                        color,
                        selection.slabSideColor ?: Color(architectureSettings.slabSideColor)
                    )
                }
            }
        })
        architectureSlabSideColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val color = parseColorField(architectureSlabSideColorField.text) ?: return
                updateArchitectureColorButtonSwatch(architectureSlabSideColorButton, color)
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.slabSideColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.SLAB) {
                    architectureSlabChanged(
                        selection.id,
                        selection.slabThickness ?: architectureSettings.slabThickness,
                        selection.slabTopColor ?: Color(architectureSettings.slabTopColor),
                        selection.slabBottomColor ?: Color(architectureSettings.slabBottomColor),
                        color
                    )
                }
            }
        })
        architectureStairTreadColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val color = parseColorField(architectureStairTreadColorField.text) ?: return
                updateArchitectureColorButtonSwatch(architectureStairTreadColorButton, color)
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.stairTreadColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.STAIR) {
                    architectureStairChanged(
                        selection.id,
                        selection.stairHeight ?: architectureSettings.stairHeight,
                        selection.stairStepCount ?: architectureSettings.stairStepCount,
                        selection.stairSupportThickness ?: architectureSettings.stairSupportThickness,
                        selection.stairRailLeftEnabled ?: architectureSettings.stairRailLeftEnabled,
                        selection.stairRailRightEnabled ?: architectureSettings.stairRailRightEnabled,
                        color,
                        selection.stairSupportColor ?: Color(architectureSettings.stairSupportColor)
                    )
                }
            }
        })
        architectureStairSupportColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val color = parseColorField(architectureStairSupportColorField.text) ?: return
                updateArchitectureColorButtonSwatch(architectureStairSupportColorButton, color)
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.stairSupportColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.STAIR) {
                    architectureStairChanged(
                        selection.id,
                        selection.stairHeight ?: architectureSettings.stairHeight,
                        selection.stairStepCount ?: architectureSettings.stairStepCount,
                        selection.stairSupportThickness ?: architectureSettings.stairSupportThickness,
                        selection.stairRailLeftEnabled ?: architectureSettings.stairRailLeftEnabled,
                        selection.stairRailRightEnabled ?: architectureSettings.stairRailRightEnabled,
                        selection.stairTreadColor ?: Color(architectureSettings.stairTreadColor),
                        color
                    )
                }
            }
        })
        architectureFrameColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val color = parseColorField(architectureFrameColorField.text) ?: return
                updateArchitectureColorButtonSwatch(architectureFrameColorButton, color)
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.frameColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.FRAME) {
                    architectureFrameChanged(
                        selection.id,
                        selection.frameDepth ?: architectureSettings.frameDepth,
                        selection.frameWidth ?: architectureSettings.frameWidth,
                        color
                    )
                }
            }
        })

        rebuildArchitectureSettingsContent(null)
        updateArchitectureSettingsPanel()
        return panel
    }

    private fun loadArchitectureDefaults() {
        architectureSettings.wallThickness = uiPrefs.getFloat(archDefaultWallThicknessKey, architectureSettings.wallThickness).coerceAtLeast(0.01f)
        architectureSettings.wallHeight = uiPrefs.getFloat(archDefaultWallHeightKey, architectureSettings.wallHeight).coerceAtLeast(0.05f)
        architectureSettings.wallInclinationDeg = uiPrefs.getFloat(archDefaultWallInclinationKey, architectureSettings.wallInclinationDeg)
        architectureSettings.wallExteriorColor.set(loadColorPref(archDefaultWallExteriorColorKey, architectureSettings.wallExteriorColor))
        architectureSettings.wallInteriorColor.set(loadColorPref(archDefaultWallInteriorColorKey, architectureSettings.wallInteriorColor))
        architectureSettings.slabThickness = uiPrefs.getFloat(archDefaultSlabThicknessKey, architectureSettings.slabThickness).coerceAtLeast(0.01f)
        architectureSettings.slabTopColor.set(loadColorPref(archDefaultSlabTopColorKey, architectureSettings.slabTopColor))
        architectureSettings.slabBottomColor.set(loadColorPref(archDefaultSlabBottomColorKey, architectureSettings.slabBottomColor))
        architectureSettings.slabSideColor.set(loadColorPref(archDefaultSlabSideColorKey, architectureSettings.slabSideColor))
        architectureSettings.stairHeight = uiPrefs.getFloat(archDefaultStairHeightKey, architectureSettings.stairHeight).coerceAtLeast(0.05f)
        architectureSettings.stairStepCount = uiPrefs.getInteger(archDefaultStairStepsKey, architectureSettings.stairStepCount).coerceAtLeast(1)
        architectureSettings.stairSupportThickness = uiPrefs.getFloat(archDefaultStairSupportKey, architectureSettings.stairSupportThickness).coerceAtLeast(0.01f)
        architectureSettings.stairRailLeftEnabled = uiPrefs.getBoolean(archDefaultStairRailLeftKey, architectureSettings.stairRailLeftEnabled)
        architectureSettings.stairRailRightEnabled = uiPrefs.getBoolean(archDefaultStairRailRightKey, architectureSettings.stairRailRightEnabled)
        architectureSettings.stairTreadColor.set(loadColorPref(archDefaultStairTreadColorKey, architectureSettings.stairTreadColor))
        architectureSettings.stairSupportColor.set(loadColorPref(archDefaultStairSupportColorKey, architectureSettings.stairSupportColor))
        architectureSettings.frameDepth = uiPrefs.getFloat(archDefaultFrameDepthKey, architectureSettings.frameDepth).coerceAtLeast(0.01f)
        architectureSettings.frameWidth = uiPrefs.getFloat(archDefaultFrameWidthKey, architectureSettings.frameWidth).coerceAtLeast(0.01f)
        architectureSettings.frameColor.set(loadColorPref(archDefaultFrameColorKey, architectureSettings.frameColor))
    }

    private fun saveArchitectureDefaults() {
        uiPrefs.putFloat(archDefaultWallThicknessKey, architectureSettings.wallThickness)
        uiPrefs.putFloat(archDefaultWallHeightKey, architectureSettings.wallHeight)
        uiPrefs.putFloat(archDefaultWallInclinationKey, architectureSettings.wallInclinationDeg)
        uiPrefs.putString(archDefaultWallExteriorColorKey, formatColorField(architectureSettings.wallExteriorColor))
        uiPrefs.putString(archDefaultWallInteriorColorKey, formatColorField(architectureSettings.wallInteriorColor))
        uiPrefs.putFloat(archDefaultSlabThicknessKey, architectureSettings.slabThickness)
        uiPrefs.putString(archDefaultSlabTopColorKey, formatColorField(architectureSettings.slabTopColor))
        uiPrefs.putString(archDefaultSlabBottomColorKey, formatColorField(architectureSettings.slabBottomColor))
        uiPrefs.putString(archDefaultSlabSideColorKey, formatColorField(architectureSettings.slabSideColor))
        uiPrefs.putFloat(archDefaultStairHeightKey, architectureSettings.stairHeight)
        uiPrefs.putInteger(archDefaultStairStepsKey, architectureSettings.stairStepCount)
        uiPrefs.putFloat(archDefaultStairSupportKey, architectureSettings.stairSupportThickness)
        uiPrefs.putBoolean(archDefaultStairRailLeftKey, architectureSettings.stairRailLeftEnabled)
        uiPrefs.putBoolean(archDefaultStairRailRightKey, architectureSettings.stairRailRightEnabled)
        uiPrefs.putString(archDefaultStairTreadColorKey, formatColorField(architectureSettings.stairTreadColor))
        uiPrefs.putString(archDefaultStairSupportColorKey, formatColorField(architectureSettings.stairSupportColor))
        uiPrefs.putFloat(archDefaultFrameDepthKey, architectureSettings.frameDepth)
        uiPrefs.putFloat(archDefaultFrameWidthKey, architectureSettings.frameWidth)
        uiPrefs.putString(archDefaultFrameColorKey, formatColorField(architectureSettings.frameColor))
        uiPrefs.flush()
    }

    private fun rebuildArchitectureSettingsContent(mode: ArchitectureElementKind?) {
        architecturePanelMode = mode
        architectureSettingsContent.clearChildren()
        architectureSettingsContent.add(architectureModeLabel).left().colspan(2).growX().row()
        val entries = mutableListOf<ArchitectureFieldEntry>()
        when (mode) {
            null -> {
                entries.add(ArchitectureFieldEntry(architectureWallThicknessLabel, architectureWallThicknessField))
                entries.add(ArchitectureFieldEntry(architectureWallHeightLabel, architectureWallHeightField))
                entries.add(ArchitectureFieldEntry(architectureWallInclinationLabel, architectureWallInclinationField))
                entries.add(
                    ArchitectureFieldEntry(
                        architectureWallExteriorColorLabel,
                        architectureWallExteriorColorField,
                        architectureWallExteriorColorButton
                    )
                )
                entries.add(
                    ArchitectureFieldEntry(
                        architectureWallInteriorColorLabel,
                        architectureWallInteriorColorField,
                        architectureWallInteriorColorButton
                    )
                )
                entries.add(ArchitectureFieldEntry(architectureSlabThicknessLabel, architectureSlabThicknessField))
                entries.add(
                    ArchitectureFieldEntry(
                        architectureSlabTopColorLabel,
                        architectureSlabTopColorField,
                        architectureSlabTopColorButton
                    )
                )
                entries.add(
                    ArchitectureFieldEntry(
                        architectureSlabBottomColorLabel,
                        architectureSlabBottomColorField,
                        architectureSlabBottomColorButton
                    )
                )
                entries.add(
                    ArchitectureFieldEntry(
                        architectureSlabSideColorLabel,
                        architectureSlabSideColorField,
                        architectureSlabSideColorButton
                    )
                )
                entries.add(ArchitectureFieldEntry(architectureStairHeightLabel, architectureStairHeightField))
                entries.add(ArchitectureFieldEntry(architectureStairStepsLabel, architectureStairStepsField))
                entries.add(ArchitectureFieldEntry(architectureStairSupportLabel, architectureStairSupportField))
                entries.add(ArchitectureFieldEntry(architectureStairLeftRailLabel, checkBox = architectureStairLeftRailCheck))
                entries.add(ArchitectureFieldEntry(architectureStairRightRailLabel, checkBox = architectureStairRightRailCheck))
                entries.add(
                    ArchitectureFieldEntry(
                        architectureStairTreadColorLabel,
                        architectureStairTreadColorField,
                        architectureStairTreadColorButton
                    )
                )
                entries.add(
                    ArchitectureFieldEntry(
                        architectureStairSupportColorLabel,
                        architectureStairSupportColorField,
                        architectureStairSupportColorButton
                    )
                )
                entries.add(ArchitectureFieldEntry(architectureFrameDepthLabel, architectureFrameDepthField))
                entries.add(ArchitectureFieldEntry(architectureFrameWidthLabel, architectureFrameWidthField))
                entries.add(
                    ArchitectureFieldEntry(
                        architectureFrameColorLabel,
                        architectureFrameColorField,
                        architectureFrameColorButton
                    )
                )
            }
            ArchitectureElementKind.WALL -> {
                entries.add(ArchitectureFieldEntry(architectureWallThicknessLabel, architectureWallThicknessField))
                entries.add(ArchitectureFieldEntry(architectureWallHeightLabel, architectureWallHeightField))
                entries.add(ArchitectureFieldEntry(architectureWallInclinationLabel, architectureWallInclinationField))
                entries.add(
                    ArchitectureFieldEntry(
                        architectureWallExteriorColorLabel,
                        architectureWallExteriorColorField,
                        architectureWallExteriorColorButton
                    )
                )
                entries.add(
                    ArchitectureFieldEntry(
                        architectureWallInteriorColorLabel,
                        architectureWallInteriorColorField,
                        architectureWallInteriorColorButton
                    )
                )
            }
            ArchitectureElementKind.SLAB -> {
                entries.add(ArchitectureFieldEntry(architectureSlabThicknessLabel, architectureSlabThicknessField))
                entries.add(
                    ArchitectureFieldEntry(
                        architectureSlabTopColorLabel,
                        architectureSlabTopColorField,
                        architectureSlabTopColorButton
                    )
                )
                entries.add(
                    ArchitectureFieldEntry(
                        architectureSlabBottomColorLabel,
                        architectureSlabBottomColorField,
                        architectureSlabBottomColorButton
                    )
                )
                entries.add(
                    ArchitectureFieldEntry(
                        architectureSlabSideColorLabel,
                        architectureSlabSideColorField,
                        architectureSlabSideColorButton
                    )
                )
            }
            ArchitectureElementKind.STAIR -> {
                entries.add(ArchitectureFieldEntry(architectureStairHeightLabel, architectureStairHeightField))
                entries.add(ArchitectureFieldEntry(architectureStairStepsLabel, architectureStairStepsField))
                entries.add(ArchitectureFieldEntry(architectureStairSupportLabel, architectureStairSupportField))
                entries.add(ArchitectureFieldEntry(architectureStairLeftRailLabel, checkBox = architectureStairLeftRailCheck))
                entries.add(ArchitectureFieldEntry(architectureStairRightRailLabel, checkBox = architectureStairRightRailCheck))
                entries.add(
                    ArchitectureFieldEntry(
                        architectureStairTreadColorLabel,
                        architectureStairTreadColorField,
                        architectureStairTreadColorButton
                    )
                )
                entries.add(
                    ArchitectureFieldEntry(
                        architectureStairSupportColorLabel,
                        architectureStairSupportColorField,
                        architectureStairSupportColorButton
                    )
                )
            }
            ArchitectureElementKind.FRAME -> {
                entries.add(ArchitectureFieldEntry(architectureFrameDepthLabel, architectureFrameDepthField))
                entries.add(ArchitectureFieldEntry(architectureFrameWidthLabel, architectureFrameWidthField))
                entries.add(
                    ArchitectureFieldEntry(
                        architectureFrameColorLabel,
                        architectureFrameColorField,
                        architectureFrameColorButton
                    )
                )
            }
        }
        addArchitectureFieldGrid(entries)
        if (::architectureSettingsPanel.isInitialized) {
            architectureSettingsPanel.pack()
        }
    }

    private data class ArchitectureFieldEntry(
        val label: VisLabel,
        val field: VisTextField? = null,
        val colorButton: VisImageTextButton? = null,
        val checkBox: VisCheckBox? = null
    )

    private fun addArchitectureFieldGrid(entries: List<ArchitectureFieldEntry>) {
        var index = 0
        while (index < entries.size) {
            architectureSettingsContent.add(architectureFieldCell(entries[index])).growX().top().padTop(2f)
            if (index + 1 < entries.size) {
                architectureSettingsContent.add(architectureFieldCell(entries[index + 1])).growX().top().padTop(2f)
            } else {
                architectureSettingsContent.add().growX()
            }
            architectureSettingsContent.row()
            index += 2
        }
    }

    private fun architectureFieldCell(entry: ArchitectureFieldEntry): VisTable {
        val table = VisTable()
        table.defaults().left().growX()
        table.add(entry.label).left().row()
        when {
            entry.checkBox != null -> table.add(entry.checkBox).left()
            entry.colorButton == null -> table.add(requireNotNull(entry.field)).growX()
            else -> {
            val controls = VisTable()
            controls.defaults().left()
            controls.add(requireNotNull(entry.field)).growX().padRight(4f)
            controls.add(entry.colorButton).size(24f, 24f)
            table.add(controls).growX()
            }
        }
        return table
    }

    private fun updateArchitectureSettingsPanel() {
        if (!::architectureSettingsContent.isInitialized) {
            return
        }
        val selection = architectureElementProvider()
        if (selection?.kind != architecturePanelMode) {
            rebuildArchitectureSettingsContent(selection?.kind)
        }
        architectureModeLabel.setText(
            if (selection == null) {
                "Default construction settings"
            } else {
                "Selected ${selection.kind.name.lowercase()} [${selection.id.take(8)}]"
            }
        )

        fun updateField(field: VisTextField, text: String) {
            if (field.hasKeyboardFocus()) {
                return
            }
            if (field.text != text) {
                field.text = text
            }
        }

        updatingArchitectureFields = true
        if (selection == null) {
            updateField(architectureWallThicknessField, String.format(Locale.US, "%.3f", architectureSettings.wallThickness))
            updateField(architectureWallHeightField, String.format(Locale.US, "%.3f", architectureSettings.wallHeight))
            updateField(architectureWallInclinationField, String.format(Locale.US, "%.3f", architectureSettings.wallInclinationDeg))
            updateField(architectureWallExteriorColorField, formatColorField(architectureSettings.wallExteriorColor))
            updateField(architectureWallInteriorColorField, formatColorField(architectureSettings.wallInteriorColor))
            updateField(architectureSlabThicknessField, String.format(Locale.US, "%.3f", architectureSettings.slabThickness))
            updateField(architectureSlabTopColorField, formatColorField(architectureSettings.slabTopColor))
            updateField(architectureSlabBottomColorField, formatColorField(architectureSettings.slabBottomColor))
            updateField(architectureSlabSideColorField, formatColorField(architectureSettings.slabSideColor))
            updateField(architectureStairHeightField, String.format(Locale.US, "%.3f", architectureSettings.stairHeight))
            updateField(architectureStairStepsField, architectureSettings.stairStepCount.toString())
            updateField(architectureStairSupportField, String.format(Locale.US, "%.3f", architectureSettings.stairSupportThickness))
            architectureStairLeftRailCheck.isChecked = architectureSettings.stairRailLeftEnabled
            architectureStairRightRailCheck.isChecked = architectureSettings.stairRailRightEnabled
            updateField(architectureStairTreadColorField, formatColorField(architectureSettings.stairTreadColor))
            updateField(architectureStairSupportColorField, formatColorField(architectureSettings.stairSupportColor))
            updateField(architectureFrameDepthField, String.format(Locale.US, "%.3f", architectureSettings.frameDepth))
            updateField(architectureFrameWidthField, String.format(Locale.US, "%.3f", architectureSettings.frameWidth))
            updateField(architectureFrameColorField, formatColorField(architectureSettings.frameColor))
        } else {
            when (selection.kind) {
                ArchitectureElementKind.WALL -> {
                    updateField(architectureWallThicknessField, String.format(Locale.US, "%.3f", selection.wallThickness ?: architectureSettings.wallThickness))
                    updateField(architectureWallHeightField, String.format(Locale.US, "%.3f", selection.wallHeight ?: architectureSettings.wallHeight))
                    updateField(architectureWallInclinationField, String.format(Locale.US, "%.3f", selection.wallInclinationDeg ?: architectureSettings.wallInclinationDeg))
                    updateField(
                        architectureWallExteriorColorField,
                        formatColorField(selection.wallExteriorColor ?: architectureSettings.wallExteriorColor)
                    )
                    updateField(
                        architectureWallInteriorColorField,
                        formatColorField(selection.wallInteriorColor ?: architectureSettings.wallInteriorColor)
                    )
                }
                ArchitectureElementKind.SLAB -> {
                    updateField(architectureSlabThicknessField, String.format(Locale.US, "%.3f", selection.slabThickness ?: architectureSettings.slabThickness))
                    updateField(
                        architectureSlabTopColorField,
                        formatColorField(selection.slabTopColor ?: architectureSettings.slabTopColor)
                    )
                    updateField(
                        architectureSlabBottomColorField,
                        formatColorField(selection.slabBottomColor ?: architectureSettings.slabBottomColor)
                    )
                    updateField(
                        architectureSlabSideColorField,
                        formatColorField(selection.slabSideColor ?: architectureSettings.slabSideColor)
                    )
                }
                ArchitectureElementKind.STAIR -> {
                    updateField(architectureStairHeightField, String.format(Locale.US, "%.3f", selection.stairHeight ?: architectureSettings.stairHeight))
                    updateField(architectureStairStepsField, (selection.stairStepCount ?: architectureSettings.stairStepCount).toString())
                    updateField(architectureStairSupportField, String.format(Locale.US, "%.3f", selection.stairSupportThickness ?: architectureSettings.stairSupportThickness))
                    architectureStairLeftRailCheck.isChecked = selection.stairRailLeftEnabled ?: architectureSettings.stairRailLeftEnabled
                    architectureStairRightRailCheck.isChecked = selection.stairRailRightEnabled ?: architectureSettings.stairRailRightEnabled
                    updateField(
                        architectureStairTreadColorField,
                        formatColorField(selection.stairTreadColor ?: architectureSettings.stairTreadColor)
                    )
                    updateField(
                        architectureStairSupportColorField,
                        formatColorField(selection.stairSupportColor ?: architectureSettings.stairSupportColor)
                    )
                }
                ArchitectureElementKind.FRAME -> {
                    updateField(architectureFrameDepthField, String.format(Locale.US, "%.3f", selection.frameDepth ?: architectureSettings.frameDepth))
                    updateField(architectureFrameWidthField, String.format(Locale.US, "%.3f", selection.frameWidth ?: architectureSettings.frameWidth))
                    updateField(architectureFrameColorField, formatColorField(selection.frameColor ?: architectureSettings.frameColor))
                }
            }
        }
        syncArchitectureColorButtonSwatches()
        updatingArchitectureFields = false
    }

    private fun createArchitectureColorButton(title: String, field: VisTextField): VisImageTextButton {
        val icon = iconFor("color", createActionIconDrawable(Color(0.8f, 0.8f, 0.8f, 1f)))
        return VisImageTextButton("", icon).apply {
            applyWhiteButtonStyle(this)
            applyIconStyle(this, icon)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val current = parseColorField(field.text) ?: Color.WHITE
                    showArchitectureColorPicker(title, current) { picked ->
                        field.text = formatColorField(picked)
                        updateArchitectureColorButtonSwatch(this@apply, picked)
                    }
                }
            })
        }
    }

    private fun showArchitectureColorPicker(title: String, start: Color, onApply: (Color) -> Unit) {
        val picker = ColorPicker(title)
        picker.color = Color(start)
        picker.setListener(object : ColorPickerListener {
            override fun changed(color: Color?) {
                if (color != null) {
                    onApply(Color(color))
                }
            }

            override fun canceled(oldColor: Color?) {
                if (oldColor != null) {
                    onApply(Color(oldColor))
                }
                picker.remove()
            }

            override fun reset(oldColor: Color?, newColor: Color?) {
                if (newColor != null) {
                    onApply(Color(newColor))
                }
            }

            override fun finished(color: Color?) {
                if (color != null) {
                    onApply(Color(color))
                }
                picker.remove()
            }
        })
        stage.addActor(picker)
        picker.centerWindow()
        picker.fadeIn()
    }

    private fun updateArchitectureColorButtonSwatch(button: VisImageTextButton, color: Color) {
        if (iconDrawables.containsKey("color")) {
            button.image?.setColor(color)
        } else {
            updateButtonIcon(button, color)
        }
    }

    private fun syncArchitectureColorButtonSwatches() {
        updateArchitectureColorButtonSwatch(
            architectureWallExteriorColorButton,
            parseColorField(architectureWallExteriorColorField.text) ?: architectureSettings.wallExteriorColor
        )
        updateArchitectureColorButtonSwatch(
            architectureWallInteriorColorButton,
            parseColorField(architectureWallInteriorColorField.text) ?: architectureSettings.wallInteriorColor
        )
        updateArchitectureColorButtonSwatch(
            architectureSlabTopColorButton,
            parseColorField(architectureSlabTopColorField.text) ?: architectureSettings.slabTopColor
        )
        updateArchitectureColorButtonSwatch(
            architectureSlabBottomColorButton,
            parseColorField(architectureSlabBottomColorField.text) ?: architectureSettings.slabBottomColor
        )
        updateArchitectureColorButtonSwatch(
            architectureSlabSideColorButton,
            parseColorField(architectureSlabSideColorField.text) ?: architectureSettings.slabSideColor
        )
        updateArchitectureColorButtonSwatch(
            architectureStairTreadColorButton,
            parseColorField(architectureStairTreadColorField.text) ?: architectureSettings.stairTreadColor
        )
        updateArchitectureColorButtonSwatch(
            architectureStairSupportColorButton,
            parseColorField(architectureStairSupportColorField.text) ?: architectureSettings.stairSupportColor
        )
        updateArchitectureColorButtonSwatch(
            architectureFrameColorButton,
            parseColorField(architectureFrameColorField.text) ?: architectureSettings.frameColor
        )
    }

    private fun parseColorField(text: String): Color? {
        val normalized = text.trim().removePrefix("#")
        if (normalized.length != 6 && normalized.length != 8) {
            return null
        }
        return try {
            if (normalized.length == 6) {
                Color.valueOf("${normalized}ff")
            } else {
                Color.valueOf(normalized)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatColorField(color: Color): String {
        return String.format(
            Locale.US,
            "%02x%02x%02x%02x",
            (color.r.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
            (color.g.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
            (color.b.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
            (color.a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        )
    }

    private fun loadColorPref(key: String, fallback: Color): Color {
        val raw = uiPrefs.getString(key, "")
        return parseColorField(raw) ?: Color(fallback)
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

    private fun updateModelSettingsPanel() {
        val unit = modelUnitProvider()
        val snapEpsilon = snapEpsilonProvider()
        val gridSpacing = gridSpacingProvider()
        val unitName = unit.name
        val unitSize = unit.size
        if (unitName != lastUnitName || unitSize != lastUnitSize || gridSpacing != lastGridSpacing || snapEpsilon != lastSnapEpsilon) {
            updatingModelSettingsFields = true
            if (unitName != lastUnitName || !unitNameField.hasKeyboardFocus()) {
                unitNameField.text = unitName
            }
            val sizeText = String.format(Locale.US, "%.4f", unitSize)
            if (sizeText != unitSizeField.text || !unitSizeField.hasKeyboardFocus()) {
                unitSizeField.text = sizeText
            }
            val gridText = String.format(Locale.US, "%.4f", gridSpacing)
            if (gridText != gridSpacingField.text || !gridSpacingField.hasKeyboardFocus()) {
                gridSpacingField.text = gridText
            }
            snapEpsilonSlider.value = snapEpsilon.coerceIn(snapEpsilonMin, snapEpsilonMax)
            updatingModelSettingsFields = false
            lastUnitName = unitName
            lastUnitSize = unitSize
            lastGridSpacing = gridSpacing
            lastSnapEpsilon = snapEpsilon
        }
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
        panels.add(modelSettingsPanel)
        panels.add(polylineSettingsPanel)
        panels.add(architectureSettingsPanel)
        lightingPanel?.let { panels.add(it) }
        panels.forEach {
            it.invalidateHierarchy()
            it.pack()
            it.toFront()
        }
        positionPanelStack(panels, 8f, 8f, 6f)
        if (!toolbarsPositioned) {
            positionTopFlowToolbars()
            toolbarsPositioned = true
        }
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
        val rightPanels = pluginPanels.filter { pluginPanelPositions[it.key] == PanelPosition.RIGHT && it.value.isVisible }
        positionPanelStack(rightPanels.values.toList(), 12f, 8f, 6f)
        val leftPanels = pluginPanels.filter { pluginPanelPositions[it.key] == PanelPosition.LEFT && it.value.isVisible }
        positionPanelStackLeft(leftPanels.values.toList(), 12f, 8f, 6f)
        val bottomPanels = pluginPanels.filter { pluginPanelPositions[it.key] == PanelPosition.BOTTOM && it.value.isVisible }
        positionPanelStackBottom(bottomPanels.values.toList(), 12f, 12f, 6f)
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

    private fun positionPanelStackLeft(
        panels: List<CollapsibleWindow>,
        leftPadding: Float,
        topPadding: Float,
        gap: Float
    ) {
        val height = if (stage.viewport.screenHeight > 0) stage.viewport.screenHeight.toFloat() else Gdx.graphics.height.toFloat()
        var y = height - topPadding
        panels.forEach { panel ->
            if (!panel.isVisible) {
                return@forEach
            }
            panel.pack()
            val panelHeight = panel.height
            panel.setPosition(leftPadding, y - panelHeight)
            y -= panelHeight + gap
        }
    }

    private fun positionPanelStackBottom(
        panels: List<CollapsibleWindow>,
        leftPadding: Float,
        bottomPadding: Float,
        gap: Float
    ) {
        var x = leftPadding
        panels.forEach { panel ->
            if (!panel.isVisible) {
                return@forEach
            }
            panel.pack()
            panel.setPosition(x, bottomPadding)
            x += panel.width + gap
        }
    }

    private fun positionTopFlowToolbars() {
        val width = if (stage.viewport.screenWidth > 0) stage.viewport.screenWidth.toFloat() else Gdx.graphics.width.toFloat()
        val height = if (stage.viewport.screenHeight > 0) stage.viewport.screenHeight.toFloat() else Gdx.graphics.height.toFloat()
        val orderedBuiltInIds = listOf(
            "builtin_toolbar_construction",
            "builtin_toolbar_modification",
            "builtin_toolbar_architecture",
            "builtin_toolbar_voxel",
            "builtin_toolbar_actions",
            "builtin_toolbar_camera"
        )
        val margin = 12f
        val gapX = 8f
        val gapY = 8f
        var x = margin
        var yTop = height - margin
        var rowHeight = 0f
        val toolbarEntries = mutableListOf<Pair<String?, CollapsibleWindow>>()
        orderedBuiltInIds.forEach { id ->
            builtInToolbars[id]?.let { toolbarEntries.add(id to it) }
        }
        pluginToolbars.entries
            .sortedBy { it.key }
            .forEach { (_, window) ->
                toolbarEntries.add(null to window)
            }

        toolbarEntries.forEach { (toolbarId, window) ->
            if (!window.isVisible) {
                return@forEach
            }
            window.invalidateHierarchy()
            window.pack()
            window.setSize(window.prefWidth, window.prefHeight)
            if (x > margin && x + window.width > width - margin) {
                x = margin
                yTop -= rowHeight + gapY
                rowHeight = 0f
            }
            window.setPosition(x, yTop - window.height)
            window.toFront()
            toolbarId?.let { saveToolbarPosition(it, window) }
            x += window.width + gapX
            rowHeight = kotlin.math.max(rowHeight, window.height)
        }
    }

    private fun migrateBuiltinToolbarPrefs() {
        val current = uiPrefs.getInteger(toolbarLayoutVersionKey, 0)
        if (current == toolbarLayoutVersion) {
            return
        }
        val toolbarIds = listOf(
            "builtin_toolbar_construction",
            "builtin_toolbar_modification",
            "builtin_toolbar_architecture",
            "builtin_toolbar_voxel",
            "builtin_toolbar_actions",
            "builtin_toolbar_camera"
        )
        toolbarIds.forEach { id ->
            uiPrefs.remove("$id.x")
            uiPrefs.remove("$id.y")
        }
        uiPrefs.putInteger(toolbarLayoutVersionKey, toolbarLayoutVersion)
        uiPrefs.flush()
    }

    private fun attachToolbarPersistence(window: CollapsibleWindow, toolbarId: String) {
        window.addListener(object : InputListener() {
            override fun touchUp(
                event: InputEvent?,
                x: Float,
                y: Float,
                pointer: Int,
                button: Int
            ) {
                saveToolbarPosition(toolbarId, window)
            }
        })
    }

    private fun readToolbarPosition(toolbarId: String): Vector2? {
        val xKey = "$toolbarId.x"
        val yKey = "$toolbarId.y"
        if (!uiPrefs.contains(xKey) || !uiPrefs.contains(yKey)) {
            return null
        }
        return Vector2(uiPrefs.getFloat(xKey), uiPrefs.getFloat(yKey))
    }

    private fun saveToolbarPosition(toolbarId: String, window: CollapsibleWindow) {
        val xKey = "$toolbarId.x"
        val yKey = "$toolbarId.y"
        val prevX = uiPrefs.getFloat(xKey, Float.NaN)
        val prevY = uiPrefs.getFloat(yKey, Float.NaN)
        if (!prevX.isNaN() && !prevY.isNaN() && abs(prevX - window.x) < 0.25f && abs(prevY - window.y) < 0.25f) {
            return
        }
        uiPrefs.putFloat(xKey, window.x)
        uiPrefs.putFloat(yKey, window.y)
        uiPrefs.flush()
    }

    private fun rebuildPluginPanels() {
        val host = pluginHost ?: return
        val entries = host.pluginUiElements()
        val panels = entries.mapNotNull { entry ->
            val panel = entry.element as? PluginPanel ?: return@mapNotNull null
            Triple("${entry.pluginId}.${panel.id}", panel, entry)
        }
        pluginPanels.values.forEach { it.remove() }
        pluginPanels.clear()
        pluginPanelPositions.clear()
        panels.forEach { (panelId, panel, _) ->
            val window = CollapsibleWindow(panel.title)
            val content = panel.creator(host.pluginContext())
            window.add(content).grow()
            window.pack()
            if (panel.width > 0f || panel.height > 0f) {
                val width = if (panel.width > 0f) panel.width else window.width
                val height = if (panel.height > 0f) panel.height else window.height
                window.setSize(width, height)
            }
            window.isVisible = false
            stage.addActor(window)
            pluginPanels[panelId] = window
            pluginPanelPositions[panelId] = panel.position
        }
        pluginPanelsPositioned = false
    }

    fun showPluginPanel(panelId: String) {
        val panel = pluginPanels[panelId] ?: return
        panel.isVisible = true
        panel.toFront()
        needsPanelLayout = true
        pluginPanelsPositioned = false
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
        pluginToolButtons.values.forEach { button ->
            hoveredButtons.remove(button)
            buttonLabels.remove(button)
            buttonMarkers.remove(button)
            pluginToolByWidget.remove(button)
            button.remove()
        }
        pluginToolButtons.clear()
        pluginToolByWidget.clear()
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
                button.setText("")
                buttonLabels[button] = entry.name
                button.addListener(hoverListener(button))
                attachButtonMarker(button)
                button.addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        host.activatePluginTool(entry.id)
                    }
                })
                group.addActor(button)
                pluginToolButtons[entry.id] = button
                pluginToolByWidget[button] = entry.id
            }
            window.add(group).grow()
            stage.addActor(window)
            pluginToolbars[pluginId] = window
        }
        updatePluginToolSelection()
        updateButtonLabels()
        toolbarsPositioned = false
        needsPanelLayout = true
        pluginPanelsPositioned = false
    }

    private fun updatePluginToolSelection() {
        val host = pluginHost ?: return
        val activeId = host.activePluginToolId()
        pluginToolButtons.forEach { (id, button) ->
            button.isChecked = id == activeId
        }
    }

    private fun updateHoverPopover(delta: Float) {
        val target = hoverPopoverTarget
        if (target == null || target.stage == null || !target.isVisible) {
            hideHoverPopover()
            hoverPopoverElapsed = 0f
            return
        }
        hoverPopoverElapsed += delta
        if (hoverPopoverElapsed >= hoverPopoverDelay) {
            showHoverPopover(target, hoverPopoverText)
        }
    }

    private fun showHoverPopover(button: VisImageTextButton, text: String) {
        if (text.isBlank()) {
            return
        }
        val tooltip = hoverPopoverWindow ?: CollapsibleWindow("", showCloseButton = false).also {
            it.isModal = false
            it.isMovable = false
            it.isResizable = false
            it.setKeepWithinParent(false)
            hoverPopoverWindow = it
            stage.addActor(it)
        }
        tooltip.clearChildren()
        tooltip.add(VisLabel(text)).pad(6f)
        tooltip.pack()
        val pos = button.localToStageCoordinates(Vector2(0f, 0f))
        val desiredX = pos.x
        val desiredY = pos.y - tooltip.height - 6f
        val maxX = (stage.width - tooltip.width).coerceAtLeast(0f)
        val maxY = (stage.height - tooltip.height).coerceAtLeast(0f)
        tooltip.setPosition(desiredX.coerceIn(0f, maxX), desiredY.coerceIn(0f, maxY))
        tooltip.toFront()
        tooltip.isVisible = true
    }

    private fun hideHoverPopover() {
        hoverPopoverWindow?.isVisible = false
    }

    private fun createIconDrawable(toolId: ToolId): TextureRegionDrawable {
        val iconName = when (toolId) {
            ToolId.SELECT -> "select"
            ToolId.LINE -> "line"
            ToolId.CONSTRUCTION_LINE -> "line"
            ToolId.POLYLINE -> "polyline"
            ToolId.DOUBLE_LINE -> "double_line"
            ToolId.VOXEL -> "voxel"
            ToolId.VOXEL_VOLUME -> "voxel_volume"
            ToolId.VOXEL_FRAME -> "voxel_frame"
            ToolId.ARCH_WALL -> "arch_wall"
            ToolId.ARCH_SLAB -> "arch_slab"
            ToolId.ARCH_STAIR -> "arch_stair"
            ToolId.ARCH_ADD_HOLE -> "arch_hole"
            ToolId.ARCH_WINDOW_FRAME -> "arch_window"
            ToolId.ARCH_DOOR_FRAME -> "arch_door"
            ToolId.FACE_OUTLINE -> "line"
            ToolId.LINE_OFFSET -> "offset"
            ToolId.CUT_HOLES -> "cleanup"
            ToolId.CUT_HOLES_2 -> "cleanup"
            ToolId.CUT_OUT_3 -> "cleanup"
            ToolId.PLANE_SECTION -> "plane_section"
            ToolId.MESH_INTERSECTION -> "mesh_intersection"
            ToolId.RECTANGLE -> "rectangle"
            ToolId.SURFACE_RECTANGLE -> "surface_rect"
            ToolId.QUAD -> "quad"
            ToolId.CIRCLE -> "circle"
            ToolId.LINEAR_DIMENSION -> "dimension"
            ToolId.TEXT -> "text"
            ToolId.PUSH_PULL -> "push_pull"
            ToolId.MOVE -> "move"
            ToolId.ROTATE -> "rotate"
            ToolId.SCALE -> "scale"
            ToolId.STRETCH -> "stretch"
            ToolId.PAINT -> "paint"
            ToolId.OBJECT_PLACE -> "select"
            ToolId.PLUGIN -> "plugins"
        }
        iconDrawables[iconName]?.let { return it }
        val color = when (toolId) {
            ToolId.SELECT -> Color(0.85f, 0.85f, 0.85f, 1f)
            ToolId.LINE -> Color(0.95f, 0.75f, 0.25f, 1f)
            ToolId.CONSTRUCTION_LINE -> Color(0.65f, 0.9f, 0.65f, 1f)
            ToolId.POLYLINE -> Color(0.95f, 0.75f, 0.25f, 1f)
            ToolId.DOUBLE_LINE -> Color(0.35f, 0.75f, 0.95f, 1f)
            ToolId.VOXEL -> Color(0.75f, 0.85f, 0.45f, 1f)
            ToolId.VOXEL_VOLUME -> Color(0.55f, 0.85f, 0.95f, 1f)
            ToolId.VOXEL_FRAME -> Color(0.95f, 0.7f, 0.3f, 1f)
            ToolId.ARCH_WALL -> Color(0.95f, 0.65f, 0.25f, 1f)
            ToolId.ARCH_SLAB -> Color(0.35f, 0.75f, 0.95f, 1f)
            ToolId.ARCH_STAIR -> Color(0.75f, 0.55f, 0.95f, 1f)
            ToolId.ARCH_ADD_HOLE -> Color(0.95f, 0.55f, 0.25f, 1f)
            ToolId.ARCH_WINDOW_FRAME -> Color(0.35f, 0.8f, 0.95f, 1f)
            ToolId.ARCH_DOOR_FRAME -> Color(0.95f, 0.75f, 0.25f, 1f)
            ToolId.FACE_OUTLINE -> Color(0.95f, 0.75f, 0.25f, 1f)
            ToolId.LINE_OFFSET -> Color(0.35f, 0.75f, 0.95f, 1f)
            ToolId.CUT_HOLES -> Color(0.85f, 0.55f, 0.35f, 1f)
            ToolId.CUT_HOLES_2 -> Color(0.85f, 0.55f, 0.35f, 1f)
            ToolId.CUT_OUT_3 -> Color(0.85f, 0.55f, 0.35f, 1f)
            ToolId.PLANE_SECTION -> Color(0.75f, 0.6f, 0.95f, 1f)
            ToolId.MESH_INTERSECTION -> Color(0.55f, 0.8f, 0.95f, 1f)
            ToolId.RECTANGLE -> Color(0.35f, 0.75f, 0.95f, 1f)
            ToolId.SURFACE_RECTANGLE -> Color(0.35f, 0.85f, 0.65f, 1f)
            ToolId.QUAD -> Color(0.55f, 0.85f, 0.95f, 1f)
            ToolId.CIRCLE -> Color(0.95f, 0.55f, 0.75f, 1f)
            ToolId.LINEAR_DIMENSION -> Color(0.8f, 0.8f, 0.4f, 1f)
            ToolId.TEXT -> Color(0.8f, 0.8f, 0.8f, 1f)
            ToolId.PUSH_PULL -> Color(0.45f, 0.95f, 0.55f, 1f)
            ToolId.MOVE -> Color(0.95f, 0.45f, 0.35f, 1f)
            ToolId.ROTATE -> Color(0.75f, 0.55f, 0.95f, 1f)
            ToolId.SCALE -> Color(0.95f, 0.55f, 0.75f, 1f)
            ToolId.STRETCH -> Color(0.95f, 0.65f, 0.25f, 1f)
            ToolId.PAINT -> Color(0.95f, 0.95f, 0.45f, 1f)
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

    private fun attachButtonMarker(button: VisImageTextButton) {
        val drawable = markerDrawable ?: createSolidDrawable(Color.WHITE).also { markerDrawable = it }
        val marker = object : Image(drawable) {
            override fun act(delta: Float) {
                super.act(delta)
                setPosition((button.width - width - 2f).coerceAtLeast(1f), (button.height - height - 2f).coerceAtLeast(1f))
            }
        }.apply {
            color = Color(0.35f, 0.35f, 0.35f, 0.8f)
            setSize(4f, 4f)
            touchable = Touchable.disabled
        }
        button.addActor(marker)
        buttonMarkers[button] = marker
    }

    private fun updateButtonLabels() {
        val host = pluginHost
        val activePluginToolId = host?.activePluginToolId()
        val activeColor = Color(0.2f, 0.75f, 0.25f, 1f)
        val hoverColor = Color(0.95f, 0.65f, 0.15f, 1f)
        val neutralColor = Color(0.35f, 0.35f, 0.35f, 0.8f)

        buttonLabels.forEach { (button, _) ->
            button.setText("")
            val isHovered = hoveredButtons.contains(button)
            val isActiveTool = toolButtonByWidget[button] == status.activeTool
            val isActivePluginTool = pluginToolByWidget[button] != null && pluginToolByWidget[button] == activePluginToolId
            val marker = buttonMarkers[button]
            marker?.color = when {
                (isActiveTool || isActivePluginTool) && isHovered -> hoverColor
                isActiveTool || isActivePluginTool -> activeColor
                isHovered -> hoverColor
                else -> neutralColor
            }
        }
    }

    private fun hoverListener(button: VisImageTextButton): ClickListener {
        return object : ClickListener() {
            override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                hoveredButtons.add(button)
                hoverPopoverTarget = button
                hoverPopoverText = buttonLabels[button] ?: ""
                hoverPopoverElapsed = 0f
                hideHoverPopover()
                updateButtonLabels()
            }

            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                hoveredButtons.remove(button)
                if (hoverPopoverTarget === button) {
                    hoverPopoverTarget = null
                    hoverPopoverText = ""
                    hoverPopoverElapsed = 0f
                    hideHoverPopover()
                }
                updateButtonLabels()
            }

            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, buttonCode: Int): Boolean {
                hoveredButtons.remove(button)
                hoverPopoverTarget = null
                hoverPopoverText = ""
                hoverPopoverElapsed = 0f
                hideHoverPopover()
                updateButtonLabels()
                return false
            }
        }
    }

    private fun applyWhiteButtonStyle(button: VisImageTextButton) {
        val up = buttonUpDrawable ?: createButtonBackgroundDrawable(
            fill = Color.WHITE,
            border = Color(0.55f, 0.55f, 0.55f, 1f)
        ).also { buttonUpDrawable = it }
        val style = button.style
        style.up = up
        style.down = up
        style.checked = up
        style.over = up
        style.fontColor = Color.BLACK
        style.downFontColor = Color.BLACK
        style.overFontColor = Color.BLACK
        style.checkedFontColor = Color.BLACK
        style.disabledFontColor = Color.DARK_GRAY
    }

    private fun createButtonBackgroundDrawable(fill: Color, border: Color): TextureRegionDrawable {
        val pixmap = Pixmap(16, 16, Pixmap.Format.RGBA8888)
        pixmap.setColor(fill)
        pixmap.fill()
        pixmap.setColor(border)
        pixmap.drawRectangle(0, 0, 16, 16)
        val texture = Texture(pixmap)
        pixmap.dispose()
        iconTextures.add(texture)
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun createSolidDrawable(color: Color): TextureRegionDrawable {
        val pixmap = Pixmap(2, 2, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
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

    data class SelectionInfo(
        val edgeCount: Int,
        val faceCount: Int,
        val voxelCount: Int,
        val groupCount: Int,
        val dimensionCount: Int,
        val textCount: Int,
        val selectedTextId: String? = null,
        val selectedTextValue: String? = null,
        val selectedTextSize: Float? = null,
        val selectedTextScreen: Boolean? = null
    )

    data class GroupInfo(val id: String, val name: String, val glued: Boolean, val editing: Boolean)

    data class ObjectPrototypeInfo(val id: String, val name: String, val instanceCount: Int)

    enum class ArchitectureElementKind {
        WALL,
        SLAB,
        STAIR,
        FRAME
    }

    data class ArchitectureElementInfo(
        val kind: ArchitectureElementKind,
        val id: String,
        val wallThickness: Float? = null,
        val wallHeight: Float? = null,
        val wallInclinationDeg: Float? = null,
        val wallExteriorColor: Color? = null,
        val wallInteriorColor: Color? = null,
        val slabThickness: Float? = null,
        val slabTopColor: Color? = null,
        val slabBottomColor: Color? = null,
        val slabSideColor: Color? = null,
        val stairHeight: Float? = null,
        val stairStepCount: Int? = null,
        val stairSupportThickness: Float? = null,
        val stairRailLeftEnabled: Boolean? = null,
        val stairRailRightEnabled: Boolean? = null,
        val stairTreadColor: Color? = null,
        val stairSupportColor: Color? = null,
        val frameDepth: Float? = null,
        val frameWidth: Float? = null,
        val frameColor: Color? = null
    )
}

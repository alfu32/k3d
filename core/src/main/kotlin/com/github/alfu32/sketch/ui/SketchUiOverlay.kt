package com.github.alfu32.sketch.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Application
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.github.alfu32.sketch.model.HotspotStore
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisCheckBox
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisSelectBox
import com.kotcrab.vis.ui.widget.VisSlider
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisTextField
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.color.ColorPicker
import com.kotcrab.vis.ui.widget.color.ColorPickerListener
import com.github.alfu32.sketch.plugin.PluginHost
import com.github.alfu32.sketch.plugin.capabilities.PanelPosition
import com.github.alfu32.sketch.plugin.capabilities.PluginPanel
import com.github.alfu32.sketch.tutorial.TutorialFileEntry
import com.github.alfu32.sketch.tutorial.TutorialMode
import com.github.alfu32.sketch.tutorial.TutorialUiState
import com.github.alfu32.sketch.tools.ArchitectureSettings
import com.github.alfu32.sketch.tools.HotspotSettings
import com.github.alfu32.sketch.tools.HvacSettings
import com.github.alfu32.sketch.tools.PolylineSettings
import com.github.alfu32.sketch.tools.VectorTextSettings
import java.util.Base64
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class SketchUiOverlay(
    private val controller: ToolController,
    private val status: StatusModel,
    private val openModelAction: () -> Unit,
    private val saveAsModelAction: () -> Unit,
    private val importMeshAction: () -> Unit,
    private val exportMeshAction: () -> Unit,
    private val cleanupAction: () -> Unit,
    private val deleteSelectionAction: () -> Unit,
    private val flipFacesAction: () -> Unit,
    private val voxelizeFacesAction: () -> Unit,
    private val hotspotCreateAction: () -> Unit,
    private val selectionInfoProvider: () -> SelectionInfo,
    private val selectionFilterChanged: (SelectionFilterKind, Boolean, Boolean, Boolean) -> Unit,
    private val selectionTextChanged: (String, String) -> Unit,
    private val selectionTextSizeChanged: (String, Float) -> Unit,
    private val selectionTextScreenChanged: (String, Boolean) -> Unit,
    private val selectionColorChanged: (Color) -> Unit,
    private val groupInfoProvider: () -> GroupInfo?,
    private val groupNameChanged: (String) -> Unit,
    private val groupGlueChanged: (Boolean) -> Unit,
    private val groupEditModeAction: () -> Unit,
    private val objectPrototypeProvider: () -> List<ObjectPrototypeInfo>,
    private val objectPrototypePlace: (String) -> Unit,
    private val objectPrototypeDelete: (String) -> Unit,
    private val modelUnitProvider: () -> com.github.alfu32.sketch.model.ModelUnit,
    private val modelUnitChanged: (String, Float) -> Unit,
    private val gridSpacingProvider: () -> Float,
    private val gridSpacingChanged: (Float) -> Unit,
    private val circleSegmentsProvider: () -> Int,
    private val circleSegmentsChanged: (Int) -> Unit,
    private val snapEpsilonProvider: () -> Float,
    private val snapEpsilonChanged: (Float) -> Unit,
    private val walkthroughTuningProvider: () -> WalkthroughTuning,
    private val walkthroughTuningChanged: (WalkthroughTuning) -> Unit,
    private val lightingSettings: LightingSettings,
    private val lightingChanged: (LightingSettings) -> Unit,
    private val shadowSettings: ShadowSettings,
    private val shadowChanged: (ShadowSettings) -> Unit,
    private val polylineSettings: PolylineSettings,
    private val vectorTextSettings: VectorTextSettings,
    private val vectorGlyphSourceProvider: () -> String,
    private val vectorGlyphSourceLoadAction: (String) -> Unit,
    private val vectorGlyphSourceBrowseAction: () -> Unit,
    private val vectorTextTrackingChanged: (String, Float) -> Unit,
    private val vectorTextLineSpacingChanged: (String, Float) -> Unit,
    private val architectureSettings: ArchitectureSettings,
    private val hvacSettings: HvacSettings,
    private val hotspotSettings: HotspotSettings,
    private val architectureElementProvider: () -> ArchitectureElementInfo?,
    private val architectureSelectionSummaryProvider: () -> ArchitectureSelectionSummary,
    private val architectureElementNameChanged: (ArchitectureElementKind, String, String) -> Unit,
    private val architectureWallChanged: (String, Float, Float, Float, Color, Color) -> Unit,
    private val architectureSlabChanged: (String, Float, Color, Color, Color) -> Unit,
    private val architectureStairChanged: (String, Float, Int, Float, Boolean, Boolean, Color, Color) -> Unit,
    private val architectureFrameChanged: (String, Float, Float, Color, Boolean, Color) -> Unit,
    private val hotspotSelectionProvider: () -> HotspotSelectionInfo,
    private val hotspotDefaultOperationChanged: (HotspotStore.OperationKind) -> Unit,
    private val hotspotDefaultShapeChanged: (HotspotStore.ShapeKind) -> Unit,
    private val hotspotDefaultColorChanged: (Color) -> Unit,
    private val hotspotNameChanged: (String, String) -> Unit,
    private val hotspotOperationChanged: (String, HotspotStore.OperationKind) -> Unit,
    private val hotspotShapeChanged: (String, HotspotStore.ShapeKind) -> Unit,
    private val hotspotColorChanged: (String, Color) -> Unit,
    private val hotspotAddAtCursor: () -> Unit,
    private val hotspotDeleteSelected: () -> Unit,
    private val hotspotAttachSelection: (String) -> Unit,
    private val hotspotSelectAttached: (String) -> Unit,
    private val hotspotBeginReferencePick: (String) -> Unit,
    private val hotspotClearReference: (String) -> Unit,
    private val cameraModeProvider: () -> CameraMode,
    private val cameraModeChanged: (CameraMode) -> Unit,
    private val renderRaytraceRequested: () -> Unit,
    private val renderPathtraceRequested: () -> Unit,
    private val renderStopRequested: () -> Unit,
    private val renderSaveRequested: () -> Unit,
    private val renderResolutionDivisorProvider: () -> Int,
    private val renderResolutionLabelProvider: () -> String,
    private val renderResolutionDivisorChanged: (Int) -> Unit,
    private val renderWorkerCountProvider: () -> Int,
    private val renderWorkerCountMaxProvider: () -> Int,
    private val renderWorkerCountChanged: (Int) -> Unit,
    private val renderPruningEnabledProvider: () -> Boolean,
    private val renderPruningEnabledChanged: (Boolean) -> Unit,
    private val renderBlendProvider: () -> Float,
    private val renderBlendChanged: (Float) -> Unit,
    private val renderBlurProvider: () -> Int,
    private val renderBlurChanged: (Int) -> Unit,
    private val renderGlassTransmissionProvider: () -> Float,
    private val renderGlassTransmissionChanged: (Float) -> Unit,
    private val renderCameraLightIntensityProvider: () -> Float,
    private val renderCameraLightIntensityChanged: (Float) -> Unit,
    private val normalLineWidthChanged: (Float) -> Unit,
    private val feedbackLineWidthChanged: (Float) -> Unit,
    private val tutorialStateProvider: () -> TutorialUiState,
    private val tutorialStartRecording: () -> Unit,
    private val tutorialStopRecording: () -> Unit,
    private val tutorialPlay: (String?) -> Unit,
    private val tutorialPauseToggle: () -> Unit,
    private val tutorialStop: () -> Unit,
    private val tutorialPrevious: () -> Unit,
    private val tutorialNext: () -> Unit,
    private val tutorialUiActionObserved: (String, String) -> Unit
) {
    private data class ToolbarButtonSlot(
        val actor: Actor,
        val cell: Cell<*>,
        val width: Float,
        val height: Float
    )

    private data class ToolbarBinding(
        val toolbarId: String,
        val window: CollapsibleWindow,
        val content: Table,
        val slots: List<ToolbarButtonSlot>,
        var hovered: Boolean = false,
        var expanded: Boolean = true,
        var visibleButtonIndex: Int = -1
    )

    private data class ToolbarLayoutState(
        val x: Float? = null,
        val y: Float? = null,
        val width: Float? = null,
        val height: Float? = null,
        val visible: Boolean? = null
    ) {
        fun hasPosition(): Boolean = x != null && y != null
    }

    private inner open class CollapsibleWindow(
        title: String,
        private val fixedHeight: Float? = null,
        showCloseButton: Boolean = true
    ) : com.kotcrab.vis.ui.widget.VisWindow(title, true) {
        private val baseTitle = title
        private val compactTitle = baseTitle.take(4)
        private var collapsed = false
        private var toolbarCompactTitle = false

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

        fun setToolbarCompactTitle(compact: Boolean) {
            if (toolbarCompactTitle == compact) {
                return
            }
            toolbarCompactTitle = compact
            getTitleLabel().setText(if (compact) compactTitle else baseTitle)
            invalidateHierarchy()
        }

        fun setCollapsedState(value: Boolean) {
            if (collapsed == value) {
                return
            }
            toggleCollapsed()
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
            val dockedInRightPanel = ::rightSidePanelContent.isInitialized && isDescendantOf(rightSidePanelContent)
            if (dockedInRightPanel) {
                return pref
            }
            return fixedHeight?.let { kotlin.math.max(pref, it) } ?: pref
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
            val dockedInRightPanel = ::rightSidePanelContent.isInitialized && isDescendantOf(rightSidePanelContent)
            if (dockedInRightPanel) {
                rightSidePanelContent.invalidateHierarchy()
                rightSidePanelScroll.invalidateHierarchy()
                rightSidePanel.invalidateHierarchy()
                needsPanelLayout = true
            } else {
                setY(top - height)
            }
        }
    }

    private inner class DockStackGroup : WidgetGroup() {
        private var prefW = 0f
        private var prefH = 0f

        fun setPreferredContentSize(width: Float, height: Float) {
            prefW = width
            prefH = height
            setSize(width, height)
            invalidateHierarchy()
        }

        override fun getPrefWidth(): Float = prefW

        override fun getPrefHeight(): Float = prefH
    }

    private inner class DockSection(
        private val titleText: String,
        private val body: Actor
    ) : WidgetGroup() {
        private val headerTable = VisTable()
        private val titleLabel = VisLabel()
        private var collapsed = false
        private val headerHeight = 28f
        private val padding = 4f

        init {
            touchable = Touchable.enabled
            headerTable.add(titleLabel).left().padLeft(6f).growX()
            addActor(headerTable)
            addActor(body)
            updateHeaderState()
            headerTable.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (tapCount >= 1) {
                        toggleCollapsed()
                    }
                }
            })
        }

        fun setCollapsedState(value: Boolean) {
            if (collapsed == value) return
            toggleCollapsed()
        }

        fun isCollapsed(): Boolean = collapsed

        private fun toggleCollapsed() {
            collapsed = !collapsed
            body.isVisible = !collapsed
            updateHeaderState()
            invalidateHierarchy()
            pack()
            needsPanelLayout = true
        }

        private fun updateHeaderState() {
            headerTable.background = if (collapsed) {
                dockSectionHeaderClosedDrawable ?: createDockSectionHeaderDrawable(
                    fill = Color.valueOf("3c4650"),
                    border = Color.valueOf("657380")
                ).also { dockSectionHeaderClosedDrawable = it }
            } else {
                dockSectionHeaderOpenDrawable ?: createDockSectionHeaderDrawable(
                    fill = Color.valueOf("21445f"),
                    border = Color.valueOf("3ba7ff")
                ).also { dockSectionHeaderOpenDrawable = it }
            }
            titleLabel.color = if (collapsed) Color.valueOf("d8dee5") else Color.WHITE
            titleLabel.setText((if (collapsed) "▶ " else "▼ ") + titleText)
        }

        override fun getPrefWidth(): Float {
            if (!isVisible) return 0f
            val headerPref = headerTable.prefWidth
            val bodyLayout = body as? com.badlogic.gdx.scenes.scene2d.utils.Layout
            val bodyPref = bodyLayout?.prefWidth ?: body.width
            return kotlin.math.max(headerPref, bodyPref + padding * 2f)
        }

        override fun getPrefHeight(): Float {
            if (!isVisible) return 0f
            val bodyLayout = body as? com.badlogic.gdx.scenes.scene2d.utils.Layout
            if (body.isVisible && bodyLayout != null && width > 0f) {
                val bodyW = (width - padding * 2f).coerceAtLeast(1f)
                body.setSize(bodyW, body.height.coerceAtLeast(1f))
                bodyLayout.invalidate()
                bodyLayout.validate()
            }
            val bodyPref = if (body.isVisible) {
                (bodyLayout?.prefHeight ?: body.height).coerceAtLeast(0f)
            } else {
                0f
            }
            return headerHeight + if (body.isVisible) (padding + bodyPref + padding) else 0f
        }

        override fun layout() {
            val w = width.coerceAtLeast(1f)
            headerTable.setBounds(0f, height - headerHeight, w, headerHeight)
            if (body.isVisible) {
                val bodyY = padding
                val bodyW = (w - padding * 2f).coerceAtLeast(1f)
                val bodyH = (height - headerHeight - padding * 2f).coerceAtLeast(1f)
                body.setBounds(padding, bodyY, bodyW, bodyH)
                (body as? com.badlogic.gdx.scenes.scene2d.utils.Layout)?.let {
                    it.invalidate()
                    it.validate()
                }
            }
            headerTable.invalidate()
            headerTable.validate()
        }
    }

    val stage: Stage = Stage(ScreenViewport())
    private val toolButtons = mutableMapOf<ToolId, AppImageTextButton>()
    private val toolButtonByWidget = mutableMapOf<AppImageTextButton, ToolId>()
    private val buttonLabels = mutableMapOf<AppImageTextButton, String>()
    private val buttonMarkers = mutableMapOf<AppImageTextButton, Image>()
    private val hoveredButtons = mutableSetOf<AppImageTextButton>()
    private val builtInToolbars = linkedMapOf<String, CollapsibleWindow>()
    private val toolbarBindingsById = linkedMapOf<String, ToolbarBinding>()
    private val toolbarBindingsByWindow = mutableMapOf<CollapsibleWindow, ToolbarBinding>()
    private val pluginToolButtons = mutableMapOf<String, AppImageTextButton>()
    private val pluginToolByWidget = mutableMapOf<AppImageTextButton, String>()
    private val pluginToolbars = mutableMapOf<String, CollapsibleWindow>()
    private val cameraModeButtons = mutableMapOf<CameraMode, VisTextButton>()
    private val cameraModeLabels = mutableMapOf<CameraMode, String>()
    private var updatingCameraModeButtons = false
    private val pluginPanels = mutableMapOf<String, CollapsibleWindow>()
    private val pluginPanelPositions = mutableMapOf<String, PanelPosition>()
    private var hoverPopoverWindow: CollapsibleWindow? = null
    private var hoverPopoverTarget: AppImageTextButton? = null
    private var hoverPopoverText: String = ""
    private var hoverPopoverElapsed = 0f
    private val hoverPopoverDelay = 0.5f
    private var toolbarsPositioned = false
    private var toolbarsVisible = true
    private val uiPrefs by lazy { Gdx.app.getPreferences("k3d-ui-layout") }
    private val toolbarLayoutVersionKey = "builtin_toolbar_layout_version"
    private val toolbarLayoutVersion = 10
    private val toolbarsVisibleKey = "toolbars.visible"
    private val uiToolbarButtonSizeKey = "ui_toolbar_button_size_px"
    private val uiToolbarAutoCollapseKey = "ui_toolbar_auto_collapse"
    private val uiTextScaleKey = "ui_text_scale"
    private val uiNormalLineWidthKey = "ui_normal_line_width"
    private val uiThickLineWidthKey = "ui_thick_line_width"
    private val toolbarLayoutMargin = 4f
    private val toolbarLayoutGap = 4f
    private val orderedBuiltInToolbarIds = listOf(
        "builtin_toolbar_construction_points",
        "builtin_toolbar_construction_entities",
        "builtin_toolbar_modification",
        "builtin_toolbar_architecture",
        "builtin_toolbar_hvac",
        "builtin_toolbar_voxel",
        "builtin_toolbar_actions",
        "builtin_toolbar_camera",
        "builtin_toolbar_rendering"
    )
    private var toolbarButtonSize = 32f
    private var toolbarIconSizePx = 32
    private var toolbarAutoCollapse = false
    private var uiTextScale = 1f
    private var normalLineWidth = 1f
    private var thickLineWidth = 3f
    private val uiBaseFontScales = IdentityHashMap<BitmapFont, Pair<Float, Float>>()
    private val toolbarDesiredVisibility = mutableMapOf<String, Boolean>()
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
    private val archDefaultFrameGlazingEnabledKey = "arch_default_frame_glazing_enabled"
    private val archDefaultFrameGlazingColorKey = "arch_default_frame_glazing_color"
    private val hvacDefaultPlumbingDiameterKey = "hvac_default_plumbing_diameter"
    private val hvacDefaultPlumbingSidesKey = "hvac_default_plumbing_sides"
    private val hvacDefaultPlumbingColorKey = "hvac_default_plumbing_color"
    private val hvacDefaultVentilationAutoJoinKey = "hvac_default_ventilation_auto_join"
    private val hvacDefaultVentilationWidthKey = "hvac_default_ventilation_width"
    private val hvacDefaultVentilationHeightKey = "hvac_default_ventilation_height"
    private val hvacDefaultVentilationHumpHalfSpanKey = "hvac_default_ventilation_hump_half_span"
    private val hvacDefaultVentilationHumpClearanceKey = "hvac_default_ventilation_hump_clearance"
    private val hvacDefaultVentilationColorKey = "hvac_default_ventilation_color"
    private val hotspotDefaultOperationKey = "hotspot_default_operation"
    private val hotspotDefaultShapeKey = "hotspot_default_shape"
    private val hotspotDefaultColorKey = "hotspot_default_color"
    private val iconTextures = mutableListOf<Texture>()
    private val iconDrawables = mutableMapOf<String, TextureRegionDrawable>()
    private var iconsTexture: Texture? = null
    private var buttonUpDrawable: TextureRegionDrawable? = null
    private var markerDrawable: TextureRegionDrawable? = null
    private var darkBarDrawable: TextureRegionDrawable? = null
    private var dockSectionHeaderOpenDrawable: TextureRegionDrawable? = null
    private var dockSectionHeaderClosedDrawable: TextureRegionDrawable? = null
    private var tutorialPreviewSlotDrawable: TextureRegionDrawable? = null
    private val toolLabel = VisLabel()
    private val messageLabel = VisLabel()
    private val copyLabel = VisLabel()
    private val inputLabel = VisLabel()
    private val cursorLabel = VisLabel()
    private val selectionEdgesLabel = VisLabel()
    private val selectionFacesLabel = VisLabel()
    private val selectionVoxelsLabel = VisLabel()
    private val selectionHotspotsLabel = VisLabel()
    private val selectionGroupsLabel = VisLabel()
    private val selectionDimensionsLabel = VisLabel()
    private val selectionTextsLabel = VisLabel()
    private val selectionCountTotalLabels = mutableMapOf<String, VisLabel>()
    private val selectionCountSelectedLabels = mutableMapOf<String, VisLabel>()
    private data class SelectionFilterRowWidgets(
        val totalLabel: VisLabel,
        val selectedLabel: VisLabel,
        val drawCheck: VisCheckBox,
        val modifyCheck: VisCheckBox,
        val wireframeCheck: VisCheckBox
    )
    private val selectionGenericRows = mutableMapOf<SelectionFilterKind, SelectionFilterRowWidgets>()
    private val selectionArchitectureRows = mutableMapOf<ArchitectureElementKind, SelectionFilterRowWidgets>()
    private val selectionTextLabel = VisLabel("Text")
    private val selectionTextField = AppTextField()
    private val selectionTextSizeLabel = VisLabel("Text size")
    private val selectionTextSizeField = AppTextField()
    private val selectionTextScreenCheck = VisCheckBox("Screen text")
    private val selectionColorLabel = VisLabel("Color")
    private val selectionColorField = AppTextField()
    private lateinit var selectionColorButton: AppImageTextButton
    private var updatingSelectionFields = false
    private var updatingSelectionFilters = false
    private var selectionColorEditable = false
    private var selectionTextId: String? = null
    private var lastPluginTools: List<String> = emptyList()
    private lateinit var groupPanel: DockSection
    private val groupStatusLabel = VisLabel()
    private val groupNameField = AppTextField()
    private val groupGlueCheck = VisCheckBox("Glue to surface")
    private var lastGroupName = ""
    private var lastGroupGlue = false
    private var lastGroupEditing = false
    private var lastGroupId = ""
    private var updatingGroupFields = false
    private var paintColorButton: AppImageTextButton? = null
    private var lastPaintColor = Color(-1f, -1f, -1f, -1f)
    private var colorPicker: ColorPicker? = null
    private var lightingPanel: DockSection? = null
    private lateinit var objectsPanel: DockSection
    private val objectsList = com.kotcrab.vis.ui.widget.VisList<String>()
    private var objectPrototypeItems: List<ObjectPrototypeInfo> = emptyList()
    private lateinit var objectsDeleteButton: VisTextButton
    private lateinit var modelSettingsPanel: DockSection
    private lateinit var uiSettingsPanel: DockSection
    private lateinit var rightSidePanel: CollapsibleWindow
    private lateinit var rightSidePanelContent: DockStackGroup
    private lateinit var rightSidePanelScroll: VisScrollPane
    private lateinit var rightSidePanelScrollCell: Cell<VisScrollPane>
    private lateinit var helpPanel: DockSection
    private lateinit var polylineSettingsPanel: DockSection
    private lateinit var vectorTextSettingsPanel: DockSection
    private lateinit var architectureWallsPanel: DockSection
    private lateinit var architectureSlabsPanel: DockSection
    private lateinit var architectureStairsPanel: DockSection
    private lateinit var architectureFramesPanel: DockSection
    private lateinit var hvacSettingsPanel: DockSection
    private lateinit var hotspotSettingsPanel: DockSection
    private lateinit var tutorialsPanel: DockSection
    private lateinit var tutorialsModeLabel: VisLabel
    private lateinit var tutorialsStepLabel: VisLabel
    private lateinit var tutorialsListContent: VisTable
    private lateinit var tutorialsRecordButton: VisTextButton
    private lateinit var tutorialsStopRecordButton: VisTextButton
    private lateinit var tutorialsPlayButton: VisTextButton
    private lateinit var tutorialsPauseButton: VisTextButton
    private lateinit var tutorialsStopButton: VisTextButton
    private lateinit var tutorialMessageWindow: CollapsibleWindow
    private lateinit var tutorialMessageLabel: VisLabel
    private lateinit var tutorialMessageStatusLabel: VisLabel
    private lateinit var tutorialPreviousButton: VisTextButton
    private lateinit var tutorialNextButton: VisTextButton
    private lateinit var tutorialPreviewBeforeImage: Image
    private lateinit var tutorialPreviewAfterImage: Image
    private var tutorialPreviewBeforeTexture: Texture? = null
    private var tutorialPreviewAfterTexture: Texture? = null
    private var tutorialPreviewBeforeKey: String? = null
    private var tutorialPreviewAfterKey: String? = null
    private var tutorialEntries: List<TutorialFileEntry> = emptyList()
    private var selectedTutorialPath: String? = null
    private var tutorialMessagePositionInitialized = false
    private val tutorialMessageWindowXKey = "tutorial_message_window_x"
    private val tutorialMessageWindowYKey = "tutorial_message_window_y"
    private lateinit var renderWindow: CollapsibleWindow
    private lateinit var renderPreviewImage: Image
    private lateinit var renderStatusLabel: VisLabel
    private lateinit var renderResolutionValueLabel: VisLabel
    private var renderPreviewCell: Cell<Image>? = null
    private var renderWindowPositionInitialized = false
    private val renderWindowXKey = "render_window_x"
    private val renderWindowYKey = "render_window_y"
    private var renderPreviewTexture: Texture? = null
    private val tutorialActionTargets = mutableMapOf<String, Actor>()
    private val tutorialListItemMaxChars = 48
    private val tutorialCollapsedSections = mutableMapOf<String, Boolean>()
    private var tutorialListRenderSignature: String = ""
    private var tutorialSectionToggleStyle: TextButton.TextButtonStyle? = null
    private lateinit var architectureModeLabel: VisLabel
    private lateinit var architectureWallSectionLabel: VisLabel
    private lateinit var architectureSlabSectionLabel: VisLabel
    private lateinit var architectureStairSectionLabel: VisLabel
    private lateinit var architectureFrameSectionLabel: VisLabel
    private lateinit var architectureWallNameLabel: VisLabel
    private lateinit var architectureWallNameField: VisTextField
    private lateinit var architectureWallThicknessLabel: VisLabel
    private lateinit var architectureWallThicknessField: VisTextField
    private lateinit var architectureWallHeightLabel: VisLabel
    private lateinit var architectureWallHeightField: VisTextField
    private lateinit var architectureWallInclinationLabel: VisLabel
    private lateinit var architectureWallInclinationField: VisTextField
    private lateinit var architectureWallExteriorColorLabel: VisLabel
    private lateinit var architectureWallExteriorColorField: VisTextField
    private lateinit var architectureWallExteriorColorButton: AppImageTextButton
    private lateinit var architectureWallInteriorColorLabel: VisLabel
    private lateinit var architectureWallInteriorColorField: VisTextField
    private lateinit var architectureWallInteriorColorButton: AppImageTextButton
    private lateinit var architectureSlabNameLabel: VisLabel
    private lateinit var architectureSlabNameField: VisTextField
    private lateinit var architectureSlabThicknessLabel: VisLabel
    private lateinit var architectureSlabThicknessField: VisTextField
    private lateinit var architectureSlabTopColorLabel: VisLabel
    private lateinit var architectureSlabTopColorField: VisTextField
    private lateinit var architectureSlabTopColorButton: AppImageTextButton
    private lateinit var architectureSlabBottomColorLabel: VisLabel
    private lateinit var architectureSlabBottomColorField: VisTextField
    private lateinit var architectureSlabBottomColorButton: AppImageTextButton
    private lateinit var architectureSlabSideColorLabel: VisLabel
    private lateinit var architectureSlabSideColorField: VisTextField
    private lateinit var architectureSlabSideColorButton: AppImageTextButton
    private lateinit var architectureStairNameLabel: VisLabel
    private lateinit var architectureStairNameField: VisTextField
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
    private lateinit var architectureStairTreadColorButton: AppImageTextButton
    private lateinit var architectureStairSupportColorLabel: VisLabel
    private lateinit var architectureStairSupportColorField: VisTextField
    private lateinit var architectureStairSupportColorButton: AppImageTextButton
    private lateinit var architectureFrameNameLabel: VisLabel
    private lateinit var architectureFrameNameField: VisTextField
    private lateinit var architectureFrameDepthLabel: VisLabel
    private lateinit var architectureFrameDepthField: VisTextField
    private lateinit var architectureFrameWidthLabel: VisLabel
    private lateinit var architectureFrameWidthField: VisTextField
    private lateinit var architectureFrameColorLabel: VisLabel
    private lateinit var architectureFrameColorField: VisTextField
    private lateinit var architectureFrameColorButton: AppImageTextButton
    private lateinit var architectureFrameGlazingLabel: VisLabel
    private lateinit var architectureFrameGlazingCheck: VisCheckBox
    private lateinit var architectureFrameGlazingColorLabel: VisLabel
    private lateinit var architectureFrameGlazingColorField: VisTextField
    private lateinit var architectureFrameGlazingColorButton: AppImageTextButton
    private lateinit var hvacPlumbingDiameterField: VisTextField
    private lateinit var hvacPlumbingSidesField: VisTextField
    private lateinit var hvacPlumbingColorField: VisTextField
    private lateinit var hvacPlumbingColorButton: AppImageTextButton
    private lateinit var hvacVentilationAutoJoinCheck: VisCheckBox
    private lateinit var hvacVentilationWidthField: VisTextField
    private lateinit var hvacVentilationHeightField: VisTextField
    private lateinit var hvacVentilationHumpHalfSpanField: VisTextField
    private lateinit var hvacVentilationHumpClearanceField: VisTextField
    private lateinit var hvacVentilationColorField: VisTextField
    private lateinit var hvacVentilationColorButton: AppImageTextButton
    private var updatingHvacFields = false
    private lateinit var hotspotModeLabel: VisLabel
    private lateinit var hotspotNameField: VisTextField
    private lateinit var hotspotOperationSelect: VisSelectBox<HotspotStore.OperationKind>
    private lateinit var hotspotShapeSelect: VisSelectBox<HotspotStore.ShapeKind>
    private lateinit var hotspotColorField: VisTextField
    private lateinit var hotspotColorButton: AppImageTextButton
    private lateinit var hotspotAttachedLabel: VisLabel
    private lateinit var hotspotReferenceLabel: VisLabel
    private lateinit var hotspotAddButton: VisTextButton
    private lateinit var hotspotDeleteButton: VisTextButton
    private lateinit var hotspotAttachButton: VisTextButton
    private lateinit var hotspotSelectAttachedButton: VisTextButton
    private lateinit var hotspotPickReferenceButton: VisTextButton
    private lateinit var hotspotClearReferenceButton: VisTextButton
    private var updatingHotspotFields = false
    private var selectedHotspotId: String? = null
    private lateinit var architectureWallsContent: VisTable
    private lateinit var architectureSlabsContent: VisTable
    private lateinit var architectureStairsContent: VisTable
    private lateinit var architectureFramesContent: VisTable
    private var updatingArchitectureFields = false
    private lateinit var vectorTextValueField: VisTextField
    private lateinit var vectorTextSizeField: VisTextField
    private lateinit var vectorTextTrackingField: VisTextField
    private lateinit var vectorTextLineSpacingField: VisTextField
    private lateinit var vectorTextGlyphSourceField: VisTextField
    private lateinit var vectorTextLoadGlyphSourceButton: VisTextButton
    private lateinit var vectorTextBrowseGlyphSourceButton: VisTextButton
    private var updatingVectorTextFields = false
    private val unitNameField = AppTextField()
    private val unitSizeField = AppTextField()
    private val gridSpacingField = AppTextField()
    private val circleSegmentsField = AppTextField()
    private val modelHotspotsLabel = VisLabel()
    private val snapEpsilonMin = 2f
    private val snapEpsilonMax = 48f
    private val snapEpsilonSlider = VisSlider(snapEpsilonMin, snapEpsilonMax, 1f, false)
    private val walkthroughJumpField = AppTextField()
    private val walkthroughGravityField = AppTextField()
    private val walkthroughHeightAdjustField = AppTextField()
    private val walkthroughMoveSpeedField = AppTextField()
    private lateinit var uiToolbarSizeSelect: VisSelectBox<String>
    private lateinit var uiTextScaleSelect: VisSelectBox<String>
    private lateinit var uiToolbarAutoCollapseCheck: VisCheckBox
    private lateinit var uiNormalLineWidthSlider: VisSlider
    private lateinit var uiNormalLineWidthValueLabel: VisLabel
    private lateinit var uiThickLineWidthSlider: VisSlider
    private lateinit var uiThickLineWidthValueLabel: VisLabel
    private var updatingUiSettingsFields = false
    private var updatingModelSettingsFields = false
    private var lastUnitName = ""
    private var lastUnitSize = -1f
    private var lastGridSpacing = -1f
    private var lastCircleSegments = -1
    private var lastSnapEpsilon = -1f
    private var lastWalkJump = -1f
    private var lastWalkGravity = -1f
    private var lastWalkHeightAdjust = -1f
    private var lastWalkMoveSpeed = -1f
    private val lightingRefreshers = mutableListOf<() -> Unit>()
    private lateinit var selectionPanel: DockSection
    private var needsPanelLayout = true
    private var rightDockStableMinContentWidth = 0f
    private var pluginManagerPanel: PluginManagerPanel? = null
    private var commandPaletteUI: CommandPaletteUI? = null
    private var pluginHost: PluginHost? = null
    private var pluginPanelsPositioned = false
    private var dockPanelsVisible = true
    private var automationHidePanels = false
    private var distancePopup: CollapsibleWindow? = null
    private var distanceField: VisTextField? = null
    private var distanceChangeHandler: ((String) -> Unit)? = null
    private var distanceCommitHandler: ((String) -> Unit)? = null
    private var distanceCancelHandler: (() -> Unit)? = null

    init {
        loadUiVisualSettings()
        applyUiTextScaleToSkin()
        normalLineWidthChanged(normalLineWidth)
        feedbackLineWidthChanged(thickLineWidth)
        iconDrawables.putAll(loadIconDrawables())
        migrateBuiltinToolbarPrefs()
        toolbarsVisible = uiPrefs.getBoolean(toolbarsVisibleKey, true)
        loadArchitectureDefaults()
        loadHvacDefaults()
        loadHotspotDefaults()
        val root = Table()
        root.setFillParent(true)
        stage.addActor(root)
        stage.viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)

        buildStandardToolbars().forEach { stage.addActor(it) }
        selectionPanel = buildSelectionPanel()
        groupPanel = buildGroupPanel()
        objectsPanel = buildObjectsPanel()
        modelSettingsPanel = buildModelSettingsPanel()
        uiSettingsPanel = buildUiSettingsPanel()
        helpPanel = buildHelpPanel()
        polylineSettingsPanel = buildPolylineSettingsPanel()
        vectorTextSettingsPanel = buildVectorTextSettingsPanel()
        buildArchitectureSettingsPanels()
        hvacSettingsPanel = buildHvacSettingsPanel()
        hotspotSettingsPanel = buildHotspotSettingsPanel()
        tutorialsPanel = buildTutorialsPanel()
        lightingPanel = buildLightingPanel()
        rightSidePanel = buildRightSidePanel()
        tutorialMessageWindow = buildTutorialMessageWindow()
        renderWindow = buildRenderWindow()
        val mainRow = Table()
        mainRow.add().expand().fill()

        root.add(mainRow).expand().fill().row()
        root.add(buildStatusBar()).expandX().fillX().bottom().pad(0f)

        stage.addActor(rightSidePanel)
        stage.addActor(tutorialMessageWindow)
        stage.addActor(renderWindow)
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
            val field = AppTextField()
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
        messageLabel.setText(
            listOf(status.message, status.backgroundStatus)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
        )
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
        selectionHotspotsLabel.setText("Hotspots: ${selection.hotspotCount}")
        selectionGroupsLabel.setText("Objects: ${selection.groupCount}")
        selectionDimensionsLabel.setText("Dimensions: ${selection.dimensionCount}")
        selectionTextsLabel.setText("Texts: ${selection.textCount}")
        selectionCountTotalLabels["edges"]?.setText(selection.edgeTotalCount.toString())
        selectionCountSelectedLabels["edges"]?.setText(selection.edgeCount.toString())
        selectionCountTotalLabels["faces"]?.setText(selection.faceTotalCount.toString())
        selectionCountSelectedLabels["faces"]?.setText(selection.faceCount.toString())
        selectionCountTotalLabels["voxels"]?.setText(selection.voxelTotalCount.toString())
        selectionCountSelectedLabels["voxels"]?.setText(selection.voxelCount.toString())
        selectionCountTotalLabels["hotspots"]?.setText(selection.hotspotTotalCount.toString())
        selectionCountSelectedLabels["hotspots"]?.setText(selection.hotspotCount.toString())
        selectionCountTotalLabels["objects"]?.setText(selection.groupTotalCount.toString())
        selectionCountSelectedLabels["objects"]?.setText(selection.groupCount.toString())
        selectionCountTotalLabels["dimensions"]?.setText(selection.dimensionTotalCount.toString())
        selectionCountSelectedLabels["dimensions"]?.setText(selection.dimensionCount.toString())
        selectionCountTotalLabels["texts"]?.setText(selection.textTotalCount.toString())
        selectionCountSelectedLabels["texts"]?.setText(selection.textCount.toString())
        updatingSelectionFilters = true
        selectionGenericRows[SelectionFilterKind.EDGE]?.let { row ->
            row.drawCheck.isChecked = selection.edgeDrawEnabled
            row.modifyCheck.isChecked = selection.edgeModifyEnabled
            row.modifyCheck.isDisabled = !selection.edgeDrawEnabled
            row.wireframeCheck.isChecked = selection.edgeWireframe
            row.wireframeCheck.isDisabled = true
        }
        selectionGenericRows[SelectionFilterKind.FACE]?.let { row ->
            row.drawCheck.isChecked = selection.faceDrawEnabled
            row.modifyCheck.isChecked = selection.faceModifyEnabled
            row.modifyCheck.isDisabled = !selection.faceDrawEnabled
            row.wireframeCheck.isChecked = selection.faceWireframe
            row.wireframeCheck.isDisabled = !selection.faceDrawEnabled
        }
        selectionGenericRows[SelectionFilterKind.VOXEL]?.let { row ->
            row.drawCheck.isChecked = selection.voxelDrawEnabled
            row.modifyCheck.isChecked = selection.voxelModifyEnabled
            row.modifyCheck.isDisabled = !selection.voxelDrawEnabled
            row.wireframeCheck.isChecked = selection.voxelWireframe
            row.wireframeCheck.isDisabled = !selection.voxelDrawEnabled
        }
        selectionGenericRows[SelectionFilterKind.HOTSPOT]?.let { row ->
            row.drawCheck.isChecked = selection.hotspotDrawEnabled
            row.modifyCheck.isChecked = selection.hotspotModifyEnabled
            row.modifyCheck.isDisabled = !selection.hotspotDrawEnabled
            row.wireframeCheck.isChecked = selection.hotspotWireframe
            row.wireframeCheck.isDisabled = true
        }
        selectionGenericRows[SelectionFilterKind.OBJECT]?.let { row ->
            row.drawCheck.isChecked = selection.objectDrawEnabled
            row.modifyCheck.isChecked = selection.objectModifyEnabled
            row.modifyCheck.isDisabled = !selection.objectDrawEnabled
            row.wireframeCheck.isChecked = selection.objectWireframe
            row.wireframeCheck.isDisabled = true
        }
        selectionGenericRows[SelectionFilterKind.DIMENSION]?.let { row ->
            row.drawCheck.isChecked = selection.dimensionDrawEnabled
            row.modifyCheck.isChecked = selection.dimensionModifyEnabled
            row.modifyCheck.isDisabled = !selection.dimensionDrawEnabled
            row.wireframeCheck.isChecked = selection.dimensionWireframe
            row.wireframeCheck.isDisabled = !selection.dimensionDrawEnabled
        }
        selectionGenericRows[SelectionFilterKind.TEXT]?.let { row ->
            row.drawCheck.isChecked = selection.textDrawEnabled
            row.modifyCheck.isChecked = selection.textModifyEnabled
            row.modifyCheck.isDisabled = !selection.textDrawEnabled
            row.wireframeCheck.isChecked = selection.textWireframe
            row.wireframeCheck.isDisabled = !selection.textDrawEnabled
        }
        selectionArchitectureRows[ArchitectureElementKind.WALL]?.let { row ->
            row.totalLabel.setText(selection.wallTotalCount.toString())
            row.selectedLabel.setText(selection.wallSelectedCount.toString())
            row.drawCheck.isChecked = selection.wallDrawEnabled
            row.modifyCheck.isChecked = selection.wallModifyEnabled
            row.modifyCheck.isDisabled = !selection.wallDrawEnabled
            row.wireframeCheck.isChecked = selection.wallWireframe
            row.wireframeCheck.isDisabled = !selection.wallDrawEnabled
        }
        selectionArchitectureRows[ArchitectureElementKind.SLAB]?.let { row ->
            row.totalLabel.setText(selection.slabTotalCount.toString())
            row.selectedLabel.setText(selection.slabSelectedCount.toString())
            row.drawCheck.isChecked = selection.slabDrawEnabled
            row.modifyCheck.isChecked = selection.slabModifyEnabled
            row.modifyCheck.isDisabled = !selection.slabDrawEnabled
            row.wireframeCheck.isChecked = selection.slabWireframe
            row.wireframeCheck.isDisabled = !selection.slabDrawEnabled
        }
        selectionArchitectureRows[ArchitectureElementKind.STAIR]?.let { row ->
            row.totalLabel.setText(selection.stairTotalCount.toString())
            row.selectedLabel.setText(selection.stairSelectedCount.toString())
            row.drawCheck.isChecked = selection.stairDrawEnabled
            row.modifyCheck.isChecked = selection.stairModifyEnabled
            row.modifyCheck.isDisabled = !selection.stairDrawEnabled
            row.wireframeCheck.isChecked = selection.stairWireframe
            row.wireframeCheck.isDisabled = !selection.stairDrawEnabled
        }
        selectionArchitectureRows[ArchitectureElementKind.FRAME]?.let { row ->
            row.totalLabel.setText(selection.frameTotalCount.toString())
            row.selectedLabel.setText(selection.frameSelectedCount.toString())
            row.drawCheck.isChecked = selection.frameDrawEnabled
            row.modifyCheck.isChecked = selection.frameModifyEnabled
            row.modifyCheck.isDisabled = !selection.frameDrawEnabled
            row.wireframeCheck.isChecked = selection.frameWireframe
            row.wireframeCheck.isDisabled = !selection.frameDrawEnabled
        }
        updatingSelectionFilters = false
        val hasTextSelection = selection.selectedTextId != null
        val isVectorTextSelection = selection.selectedVectorText
        selectionTextLabel.isVisible = hasTextSelection
        selectionTextField.isVisible = hasTextSelection
        selectionTextSizeLabel.isVisible = hasTextSelection
        selectionTextSizeField.isVisible = hasTextSelection
        selectionTextScreenCheck.isVisible = hasTextSelection && !isVectorTextSelection
        updatingSelectionFields = true
        selectionTextId = selection.selectedTextId
        selectionTextField.text = selection.selectedTextValue ?: ""
        selectionTextSizeField.text = selection.selectedTextSize?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        selectionTextScreenCheck.isChecked = selection.selectedTextScreen ?: true
        selectionColorEditable = selection.selectedColorEditable
        selectionColorLabel.isVisible = selectionColorEditable
        selectionColorField.isVisible = selectionColorEditable
        selectionColorButton.isVisible = selectionColorEditable
        selectionColorField.isDisabled = !selectionColorEditable
        selectionColorButton.isDisabled = !selectionColorEditable
        selectionColorField.text = selection.selectedColor?.let { formatColorField(it) } ?: ""
        updateArchitectureColorButtonSwatch(selectionColorButton, selection.selectedColor ?: status.paintColor)
        updatingSelectionFields = false
        updateGroupPanel()
        updateObjectsPanel()
        updateModelSettingsPanel()
        updateVectorTextSettingsPanel()
        updateArchitectureSettingsPanel()
        updateHvacSettingsPanel()
        updateHotspotSettingsPanel()
        updateTutorialUi()
        refreshPluginToolbar()
        toolButtons[status.activeTool]?.isChecked = true
        updatePluginToolSelection()
        updatePaintColorButton()
        updateButtonLabels()
        refreshToolbarAutoCollapseStates()
        syncCameraModeButtons()
    }

    fun refreshLightingControls() {
        lightingRefreshers.forEach { it.invoke() }
    }

    private fun updateVectorTextSettingsPanel() {
        if (!::vectorTextValueField.isInitialized) {
            return
        }
        val selection = selectionInfoProvider()
        val selectedVector = selection.selectedTextId != null && selection.selectedVectorText
        val displayText = if (selectedVector) {
            selection.selectedTextValue ?: ""
        } else {
            vectorTextSettings.text
        }
        val displaySize = if (selectedVector) {
            selection.selectedTextSize ?: vectorTextSettings.size
        } else {
            vectorTextSettings.size
        }
        val displayTracking = if (selectedVector) {
            selection.selectedVectorTextTracking ?: vectorTextSettings.tracking
        } else {
            vectorTextSettings.tracking
        }
        val displayLineSpacing = if (selectedVector) {
            selection.selectedVectorTextLineSpacing ?: vectorTextSettings.lineSpacing
        } else {
            vectorTextSettings.lineSpacing
        }
        val displayGlyphSource = if (selectedVector) {
            selection.selectedVectorTextGlyphSourcePath ?: vectorGlyphSourceProvider()
        } else {
            vectorGlyphSourceProvider()
        }

        updatingVectorTextFields = true
        if (!vectorTextValueField.hasKeyboardFocus() && vectorTextValueField.text != displayText) {
            vectorTextValueField.text = displayText
        }
        val sizeText = String.format(Locale.US, "%.3f", displaySize)
        if (!vectorTextSizeField.hasKeyboardFocus() && vectorTextSizeField.text != sizeText) {
            vectorTextSizeField.text = sizeText
        }
        val trackingText = String.format(Locale.US, "%.3f", displayTracking)
        if (!vectorTextTrackingField.hasKeyboardFocus() && vectorTextTrackingField.text != trackingText) {
            vectorTextTrackingField.text = trackingText
        }
        val lineSpacingText = String.format(Locale.US, "%.3f", displayLineSpacing)
        if (!vectorTextLineSpacingField.hasKeyboardFocus() && vectorTextLineSpacingField.text != lineSpacingText) {
            vectorTextLineSpacingField.text = lineSpacingText
        }
        val sourceText = displayGlyphSource
        if (!vectorTextGlyphSourceField.hasKeyboardFocus() && vectorTextGlyphSourceField.text != sourceText) {
            vectorTextGlyphSourceField.text = sourceText
        }
        updatingVectorTextFields = false
    }

    private fun syncCameraModeButtons() {
        if (cameraModeButtons.isEmpty()) {
            return
        }
        updatingCameraModeButtons = true
        val mode = cameraModeProvider()
        cameraModeButtons.forEach { (cameraMode, button) ->
            val active = cameraMode == mode
            button.isChecked = active
            val label = cameraModeLabels[cameraMode] ?: cameraMode.displayName
            button.setText(if (active) "• $label" else label)
        }
        updatingCameraModeButtons = false
    }

    fun act(delta: Float) {
        updateFromStatus()
        if (stage.scrollFocus != null && !isUiHit(Gdx.input.x, Gdx.input.y)) {
            stage.scrollFocus = null
        }
        if (!automationHidePanels && dockPanelsVisible && ::rightSidePanel.isInitialized) {
            var changed = false
            rightDockPanels().forEach { panel ->
                if (!panel.isVisible) {
                    panel.isVisible = true
                    changed = true
                }
            }
            if (changed) {
                needsPanelLayout = true
            }
        }
        if (needsPanelLayout) {
            positionPanels()
            needsPanelLayout = false
        }
        persistToolbarStates()
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
        pluginManagerPanel?.isVisible = false
        commandPaletteUI?.hide()
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
        selectionPanel.setCollapsedState(false)
        if (::rightSidePanel.isInitialized) rightSidePanel.isVisible = true
        needsPanelLayout = true
    }

    fun showGroupPanel() {
        groupPanel.isVisible = true
        groupPanel.setCollapsedState(false)
        if (::rightSidePanel.isInitialized) rightSidePanel.isVisible = true
        needsPanelLayout = true
    }

    fun showLightingPanel() {
        lightingPanel?.let {
            it.isVisible = true
            it.setCollapsedState(false)
            if (::rightSidePanel.isInitialized) rightSidePanel.isVisible = true
            needsPanelLayout = true
        }
    }

    fun showModelSettingsPanel() {
        modelSettingsPanel.isVisible = true
        modelSettingsPanel.setCollapsedState(false)
        if (::rightSidePanel.isInitialized) rightSidePanel.isVisible = true
        needsPanelLayout = true
    }

    fun showHelpPanel() {
        helpPanel.isVisible = true
        helpPanel.setCollapsedState(false)
        if (::rightSidePanel.isInitialized) rightSidePanel.isVisible = true
        needsPanelLayout = true
    }

    fun showPolylineSettingsPanel() {
        polylineSettingsPanel.isVisible = true
        polylineSettingsPanel.setCollapsedState(false)
        if (::rightSidePanel.isInitialized) rightSidePanel.isVisible = true
        needsPanelLayout = true
    }

    fun showVectorTextSettingsPanel() {
        vectorTextSettingsPanel.isVisible = true
        vectorTextSettingsPanel.setCollapsedState(false)
        if (::rightSidePanel.isInitialized) rightSidePanel.isVisible = true
        needsPanelLayout = true
    }

    fun showArchitectureSettingsPanel() {
        val selection = architectureElementProvider()
        val sections = listOf(
            architectureWallsPanel,
            architectureSlabsPanel,
            architectureStairsPanel,
            architectureFramesPanel
        )
        sections.forEach { it.isVisible = true }
        sections.forEach { it.setCollapsedState(true) }
        when (selection?.kind) {
            ArchitectureElementKind.SLAB -> architectureSlabsPanel.setCollapsedState(false)
            ArchitectureElementKind.STAIR -> architectureStairsPanel.setCollapsedState(false)
            ArchitectureElementKind.FRAME -> architectureFramesPanel.setCollapsedState(false)
            else -> architectureWallsPanel.setCollapsedState(false)
        }
        if (::rightSidePanel.isInitialized) rightSidePanel.isVisible = true
        needsPanelLayout = true
    }

    fun showHvacSettingsPanel() {
        hvacSettingsPanel.isVisible = true
        hvacSettingsPanel.setCollapsedState(false)
        if (::rightSidePanel.isInitialized) rightSidePanel.isVisible = true
        needsPanelLayout = true
    }

    fun showHotspotSettingsPanel() {
        hotspotSettingsPanel.isVisible = true
        hotspotSettingsPanel.setCollapsedState(false)
        if (::rightSidePanel.isInitialized) rightSidePanel.isVisible = true
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

    fun setToolbarsVisible(visible: Boolean) {
        toolbarsVisible = visible
        saveToolbarsVisible()
        applyToolbarsVisibility()
        toolbarsPositioned = false
        needsPanelLayout = true
    }

    fun setPanelsVisible(visible: Boolean) {
        dockPanelsVisible = visible
        if (::rightSidePanel.isInitialized) {
            rightSidePanel.isVisible = visible
        }
        needsPanelLayout = true
    }

    fun setAutomationHidePanels(enabled: Boolean) {
        automationHidePanels = enabled
        if (enabled) {
            if (::rightSidePanel.isInitialized) {
                rightSidePanel.isVisible = false
            }
            selectionPanel.isVisible = false
            groupPanel.isVisible = false
            objectsPanel.isVisible = false
            modelSettingsPanel.isVisible = false
            helpPanel.isVisible = false
            polylineSettingsPanel.isVisible = false
            vectorTextSettingsPanel.isVisible = false
            architectureWallsPanel.isVisible = false
            architectureSlabsPanel.isVisible = false
            architectureStairsPanel.isVisible = false
            architectureFramesPanel.isVisible = false
            hvacSettingsPanel.isVisible = false
            hotspotSettingsPanel.isVisible = false
            lightingPanel?.isVisible = false
            pluginManagerPanel?.isVisible = false
            pluginPanels.values.forEach { panel -> panel.isVisible = false }
            commandPaletteUI?.hide()
            clearUiFocus()
        }
        if (!enabled) {
            if (::rightSidePanel.isInitialized) {
                rightSidePanel.isVisible = true
            }
            rightDockPanels().forEach { panel -> panel.isVisible = true }
        }
        needsPanelLayout = true
    }

    fun refreshPluginPanels() {
        pluginManagerPanel?.refresh(force = true)
        commandPaletteUI?.refreshList()
        rebuildPluginPanels()
    }

    fun dispose() {
        disposeTutorialPreviewTextures()
        clearRenderPreviewReference()
        stage.dispose()
        iconTextures.forEach { it.dispose() }
    }

    private fun buildStandardToolbars(): List<CollapsibleWindow> {
        val toolGroup = ButtonGroup<AppImageTextButton>()
        toolGroup.setMaxCheckCount(1)
        toolGroup.setMinCheckCount(1)
        toolGroup.setUncheckLast(false)

        val pointConstructionTools = listOf(
            ToolId.LINE,
            ToolId.CONSTRUCTION_LINE,
            ToolId.POLYLINE,
            ToolId.DOUBLE_LINE,
            ToolId.RECTANGLE,
            ToolId.SURFACE_RECTANGLE,
            ToolId.FACE_OUTLINE,
            ToolId.MESH,
            ToolId.QUAD,
            ToolId.CIRCLE
        )
        val entityConstructionTools = listOf(
            ToolId.LINEAR_DIMENSION,
            ToolId.TEXT,
            ToolId.VECTOR_TEXT,
            ToolId.LINE_OFFSET,
            ToolId.CUT_OUT_3,
            ToolId.PUSH_PULL,
            ToolId.EXTRUDE_SWIPE,
            ToolId.PLANE_SECTION,
            ToolId.MESH_INTERSECTION
        )
        val modificationTools = listOf(
            ToolId.SELECT,
            ToolId.MOVE,
            ToolId.ROTATE,
            ToolId.SCALE,
            ToolId.STRETCH,
            ToolId.ROTATE_STRETCH,
            ToolId.COPY_MULTIPLE,
            ToolId.PLANAR_TRANSLATE_MULTIPLE,
            ToolId.VOLUMETRIC_TRANSLATE_MULTIPLE,
            ToolId.PLANAR_ROTATE_MULTIPLE,
            ToolId.HELICOIDAL_ROTATE_MULTIPLE,
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
        val hvacTools = listOf(
            ToolId.HVAC_PLUMBING,
            ToolId.HVAC_VENTILATION
        )

        val pointConstruction = buildToolsToolbarWindow(
            title = "Point Construction",
            toolbarId = "builtin_toolbar_construction_points",
            toolIds = pointConstructionTools,
            group = toolGroup
            ,
            leadingButtons = listOf(
                createActionButton(
                    label = "Add Hotspot",
                    icon = iconFor("hotspot", createActionIconDrawable(Color(0.2f, 0.55f, 0.95f, 1f))),
                    tutorialActionId = "ui.action.add_hotspot"
                ) { hotspotCreateAction() }
            )
        )
        val entityConstruction = buildToolsToolbarWindow(
            title = "Entity Construction",
            toolbarId = "builtin_toolbar_construction_entities",
            toolIds = entityConstructionTools,
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
                    icon = iconFor("voxelize", createActionIconDrawable(Color(0.7f, 0.85f, 0.4f, 1f))),
                    tutorialActionId = "ui.action.voxelize_faces"
                ) { voxelizeFacesAction() }
            )
        )
        val architecture = buildToolsToolbarWindow(
            title = "Architecture",
            toolbarId = "builtin_toolbar_architecture",
            toolIds = architectureTools,
            group = toolGroup
        )
        val hvac = buildToolsToolbarWindow(
            title = "HVAC",
            toolbarId = "builtin_toolbar_hvac",
            toolIds = hvacTools,
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
        val rendering = buildRenderingToolbarWindow(
            title = "Rendering",
            toolbarId = "builtin_toolbar_rendering"
        )

        builtInToolbars.clear()
        builtInToolbars["builtin_toolbar_construction_points"] = pointConstruction
        builtInToolbars["builtin_toolbar_construction_entities"] = entityConstruction
        builtInToolbars["builtin_toolbar_modification"] = modification
        builtInToolbars["builtin_toolbar_architecture"] = architecture
        builtInToolbars["builtin_toolbar_hvac"] = hvac
        builtInToolbars["builtin_toolbar_voxel"] = voxel
        builtInToolbars["builtin_toolbar_actions"] = actions
        builtInToolbars["builtin_toolbar_camera"] = camera
        builtInToolbars["builtin_toolbar_rendering"] = rendering
        builtInToolbars.forEach { (toolbarId, window) ->
            applyToolbarState(toolbarId, window)
        }
        toolbarsPositioned = false
        return listOf(pointConstruction, entityConstruction, modification, architecture, hvac, voxel, actions, camera, rendering)
    }

    private fun buildToolsToolbarWindow(
        title: String,
        toolbarId: String,
        toolIds: List<ToolId>,
        group: ButtonGroup<AppImageTextButton>,
        leadingButtons: List<AppImageTextButton> = emptyList(),
        extraButtons: List<AppImageTextButton> = emptyList()
    ): CollapsibleWindow {
        val window = CollapsibleWindow(title, showCloseButton = false)
        window.isResizable = false
        val content = VisTable()
        content.defaults().pad(0f).left()
        val slots = mutableListOf<ToolbarButtonSlot>()
        leadingButtons.forEach { button ->
            val cell = content.add(button).size(toolbarButtonSize, toolbarButtonSize)
            slots += ToolbarButtonSlot(button, cell, toolbarButtonSize, toolbarButtonSize)
        }
        toolIds.forEach { toolId ->
            val button = createToolButton(toolId, group)
            val cell = content.add(button).size(toolbarButtonSize, toolbarButtonSize)
            slots += ToolbarButtonSlot(button, cell, toolbarButtonSize, toolbarButtonSize)
        }
        extraButtons.forEach { button ->
            val cell = content.add(button).size(toolbarButtonSize, toolbarButtonSize)
            slots += ToolbarButtonSlot(button, cell, toolbarButtonSize, toolbarButtonSize)
        }
        window.add(content).pad(0f).left()
        window.pack()
        window.setSize(window.prefWidth, window.prefHeight)
        attachToolbarPersistence(window, toolbarId)
        registerToolbarBinding(toolbarId, window, content, slots)
        return window
    }

    private fun createToolButton(toolId: ToolId, group: ButtonGroup<AppImageTextButton>): AppImageTextButton {
        val icon = createIconDrawable(toolId)
        val button = AppImageTextButton(toolId.displayName, icon)
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
        registerTutorialActionTarget("tool.start.${toolId.name.lowercase(Locale.US)}", button)
        return button
    }

    private fun buildActionsToolbarWindow(title: String, toolbarId: String): CollapsibleWindow {
        val window = CollapsibleWindow(title, showCloseButton = false)
        window.isResizable = false
        val content = VisTable()
        content.defaults().pad(0f).left()
        val slots = mutableListOf<ToolbarButtonSlot>()

        val openButton = createActionButton(
            label = "Open",
            icon = iconFor("file_open", createActionIconDrawable(Color(0.65f, 0.8f, 0.95f, 1f))),
            tutorialActionId = "ui.action.open_model"
        ) {
            openModelAction()
        }

        val saveAsButton = createActionButton(
            label = "Save As",
            icon = iconFor("file_save", createActionIconDrawable(Color(0.6f, 0.9f, 0.6f, 1f))),
            tutorialActionId = "ui.action.save_as_model"
        ) {
            saveAsModelAction()
        }

        val importMeshButton = createActionButton(
            label = "Import Mesh",
            icon = iconFor("file_open", createActionIconDrawable(Color(0.9f, 0.78f, 0.45f, 1f))),
            tutorialActionId = "ui.action.import_mesh"
        ) {
            importMeshAction()
        }

        val exportMeshButton = createActionButton(
            label = "Export Mesh",
            icon = iconFor("file_save", createActionIconDrawable(Color(0.85f, 0.7f, 0.95f, 1f))),
            tutorialActionId = "ui.action.export_mesh"
        ) {
            exportMeshAction()
        }

        val cleanupButton = createActionButton(
            label = "Cleanup",
            icon = iconFor("cleanup", createActionIconDrawable(Color(0.55f, 0.85f, 0.65f, 1f))),
            tutorialActionId = "ui.action.cleanup"
        ) {
            cleanupAction()
        }

        val colorButton = createActionButton(
            label = "Color",
            icon = iconFor("color", createActionIconDrawable(status.paintColor)),
            tutorialActionId = "ui.action.color"
        ) {
            showColorPicker()
        }
        paintColorButton = colorButton

        val deleteButton = createActionButton(
            label = "Delete",
            icon = iconFor("delete", createActionIconDrawable(Color(0.9f, 0.45f, 0.45f, 1f))),
            tutorialActionId = "ui.action.delete_selection"
        ) {
            deleteSelectionAction()
        }

        val editObjectButton = createActionButton(
            label = "Edit Selected Object",
            icon = iconFor("edit_object", createActionIconDrawable(Color(0.75f, 0.82f, 0.95f, 1f))),
            tutorialActionId = "ui.action.edit_selected_object"
        ) {
            groupEditModeAction()
        }

        val flipButton = createActionButton(
            label = "Flip Faces",
            icon = iconFor("flip_faces", createActionIconDrawable(Color(0.45f, 0.65f, 0.95f, 1f))),
            tutorialActionId = "ui.action.flip_faces"
        ) {
            flipFacesAction()
        }

        val lightingButton = createActionButton(
            label = "Shader Settings",
            icon = iconFor("lighting", createActionIconDrawable(Color(0.95f, 0.85f, 0.2f, 1f))),
            tutorialActionId = "ui.action.toggle_lighting_panel"
        ) {
            lightingPanel?.let { panel ->
                panel.isVisible = !panel.isVisible
                needsPanelLayout = true
                panel.toFront()
            }
        }

        val pluginButton = createActionButton(
            label = "Plugin Manager",
            icon = iconFor("plugins", createActionIconDrawable(Color(0.6f, 0.6f, 0.6f, 1f))),
            tutorialActionId = "ui.action.plugin_manager"
        ) {
            togglePluginManager()
        }
        val buttons = listOf(
            openButton,
            saveAsButton,
            importMeshButton,
            exportMeshButton,
            cleanupButton,
            colorButton,
            deleteButton,
            editObjectButton,
            flipButton,
            lightingButton,
            pluginButton
        )
        buttons.forEach { button ->
            val cell = content.add(button).size(toolbarButtonSize, toolbarButtonSize)
            slots += ToolbarButtonSlot(button, cell, toolbarButtonSize, toolbarButtonSize)
        }

        window.add(content).pad(0f).left()
        window.pack()
        window.setSize(window.prefWidth, window.prefHeight)
        attachToolbarPersistence(window, toolbarId)
        registerToolbarBinding(toolbarId, window, content, slots)
        return window
    }

    private fun buildCameraToolbarWindow(title: String, toolbarId: String): CollapsibleWindow {
        val window = CollapsibleWindow(title, showCloseButton = false)
        window.isResizable = false
        val content = VisTable()
        content.defaults().pad(0f).left()
        val slots = mutableListOf<ToolbarButtonSlot>()

        val group = ButtonGroup<VisTextButton>().apply {
            setMaxCheckCount(1)
            setMinCheckCount(0)
            setUncheckLast(true)
        }
        cameraModeButtons.clear()
        cameraModeLabels.clear()
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
                    tutorialUiActionObserved("ui.action.camera_mode.${mode.name.lowercase(Locale.US)}", label)
                    cameraModeChanged(mode)
                    syncCameraModeButtons()
                }
            })
            registerTutorialActionTarget("ui.action.camera_mode.${mode.name.lowercase(Locale.US)}", button)
            cameraModeButtons[mode] = button
            cameraModeLabels[mode] = label
            group.add(button)
            val buttonWidth = button.prefWidth.coerceAtLeast(54f)
            val cell = content.add(button).height(toolbarButtonSize).minWidth(buttonWidth)
            slots += ToolbarButtonSlot(button, cell, buttonWidth, toolbarButtonSize)
        }
        syncCameraModeButtons()

        window.add(content).pad(0f).left()
        window.pack()
        window.setSize(window.prefWidth, window.prefHeight)
        attachToolbarPersistence(window, toolbarId)
        registerToolbarBinding(toolbarId, window, content, slots)
        return window
    }

    private fun buildRenderingToolbarWindow(title: String, toolbarId: String): CollapsibleWindow {
        val window = CollapsibleWindow(title, showCloseButton = false)
        window.isResizable = false
        val content = VisTable()
        content.defaults().pad(0f).left()
        val slots = mutableListOf<ToolbarButtonSlot>()
        val buttons = listOf(
            VisTextButton("RT").apply {
                addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        renderRaytraceRequested()
                    }
                })
            },
            VisTextButton("GI").apply {
                addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        renderPathtraceRequested()
                    }
                })
            },
            VisTextButton("Stop").apply {
                addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        renderStopRequested()
                    }
                })
            },
            VisTextButton("Save").apply {
                addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        renderSaveRequested()
                    }
                })
            }
        )
        buttons.forEach { button ->
            val buttonWidth = button.prefWidth.coerceAtLeast(54f)
            val cell = content.add(button).height(toolbarButtonSize).minWidth(buttonWidth)
            slots += ToolbarButtonSlot(button, cell, buttonWidth, toolbarButtonSize)
        }
        window.add(content).pad(0f).left()
        window.pack()
        window.setSize(window.prefWidth, window.prefHeight)
        attachToolbarPersistence(window, toolbarId)
        registerToolbarBinding(toolbarId, window, content, slots)
        return window
    }

    private fun registerToolbarBinding(
        toolbarId: String,
        window: CollapsibleWindow,
        content: Table,
        slots: List<ToolbarButtonSlot>
    ) {
        toolbarBindingsByWindow.remove(window)
        toolbarBindingsById.remove(toolbarId)
        val binding = ToolbarBinding(toolbarId, window, content, slots)
        toolbarBindingsById[toolbarId] = binding
        toolbarBindingsByWindow[window] = binding
        window.addListener(object : InputListener() {
            override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
                if (pointer != -1) return
                setToolbarHovered(binding, true)
            }

            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                if (pointer != -1) return
                if (toActor != null && toActor.isDescendantOf(window)) {
                    return
                }
                setToolbarHovered(binding, false)
            }
        })
        applyToolbarAutoCollapse(binding, force = true)
    }

    private fun setToolbarHovered(binding: ToolbarBinding, hovered: Boolean) {
        if (binding.hovered == hovered) {
            return
        }
        binding.hovered = hovered
        applyToolbarAutoCollapse(binding, force = true)
    }

    private fun preferredCollapsedToolbarButtonIndex(binding: ToolbarBinding): Int {
        val activePluginToolId = pluginHost?.activePluginToolId()
        binding.slots.forEachIndexed { index, slot ->
            val actor = slot.actor
            if (actor is AppImageTextButton) {
                val toolId = toolButtonByWidget[actor]
                if (toolId != null && toolId == status.activeTool) {
                    return index
                }
                val pluginToolId = pluginToolByWidget[actor]
                if (pluginToolId != null && pluginToolId == activePluginToolId) {
                    return index
                }
            }
            if (actor is VisTextButton && cameraModeButtons[cameraModeProvider()] === actor) {
                return index
            }
        }
        return 0
    }

    private fun applyToolbarAutoCollapse(binding: ToolbarBinding, force: Boolean = false): Boolean {
        if (binding.slots.isEmpty()) {
            return false
        }
        val expanded = !toolbarAutoCollapse || binding.hovered || binding.slots.size <= 1
        val visibleButtonIndex = if (expanded) -1 else preferredCollapsedToolbarButtonIndex(binding)
        if (!force && binding.expanded == expanded && binding.visibleButtonIndex == visibleButtonIndex) {
            return false
        }
        val window = binding.window
        val oldX = window.x
        val oldTop = window.y + window.height
        window.setToolbarCompactTitle(!expanded)
        binding.expanded = expanded
        binding.visibleButtonIndex = visibleButtonIndex
        binding.slots.forEachIndexed { index, slot ->
            val show = expanded || index == visibleButtonIndex
            slot.actor.isVisible = show
            if (show) {
                slot.cell.size(slot.width, slot.height)
                slot.cell.pad(0f)
            } else {
                slot.cell.size(0f, 0f)
                slot.cell.pad(0f)
            }
        }
        binding.content.invalidateHierarchy()
        window.invalidateHierarchy()
        window.pack()
        if (!expanded) {
            val collapsedWidth = max(48f, binding.content.prefWidth)
            window.setSize(collapsedWidth, window.height)
        }
        window.setPosition(oldX, oldTop - window.height)
        val viewportWidth = if (stage.viewport.screenWidth > 0) stage.viewport.screenWidth.toFloat() else Gdx.graphics.width.toFloat()
        val viewportHeight = if (stage.viewport.screenHeight > 0) stage.viewport.screenHeight.toFloat() else Gdx.graphics.height.toFloat()
        clampToolbarWindowToViewport(window, viewportWidth, viewportHeight, toolbarLayoutMargin)
        return true
    }

    private fun refreshToolbarAutoCollapseStates(force: Boolean = false) {
        toolbarBindingsById.values.forEach { binding ->
            applyToolbarAutoCollapse(binding, force)
        }
    }

    private fun createActionButton(
        label: String,
        icon: com.badlogic.gdx.scenes.scene2d.utils.Drawable,
        tutorialActionId: String? = null,
        onClick: () -> Unit
    ): AppImageTextButton {
        val button = AppImageTextButton(label, icon)
        applyWhiteButtonStyle(button)
        applyIconStyle(button, icon)
        button.setText("")
        buttonLabels[button] = label
        button.addListener(hoverListener(button))
        attachButtonMarker(button)
        tutorialActionId?.let { registerTutorialActionTarget(it, button) }
        button.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialActionId?.let { tutorialUiActionObserved(it, label) }
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

    private fun buildSelectionPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()

        val countsTable = VisTable()
        countsTable.defaults().pad(2f).left()
        countsTable.add(VisLabel("Type")).left().width(96f)
        countsTable.add(VisLabel("T")).center().width(34f)
        countsTable.add(VisLabel("S")).center().width(34f)
        countsTable.add(VisLabel("D")).center().width(28f)
        countsTable.add(VisLabel("M")).center().width(28f)
        countsTable.add(VisLabel("W")).center().width(28f).row()

        fun addCountRow(label: String, key: String) {
            val totalLabel = VisLabel("0")
            val selectedLabel = VisLabel("0")
            selectionCountTotalLabels[key] = totalLabel
            selectionCountSelectedLabels[key] = selectedLabel
            countsTable.add(VisLabel(label)).left()
            countsTable.add(totalLabel).center()
            countsTable.add(selectedLabel).center()
            countsTable.add(VisLabel("")).width(28f)
            countsTable.add(VisLabel("")).width(28f)
            countsTable.add(VisLabel("")).width(28f).row()
        }

        fun addFilterRow(label: String, key: String, kind: SelectionFilterKind) {
            val totalLabel = VisLabel("0")
            val selectedLabel = VisLabel("0")
            val drawCheck = VisCheckBox("")
            val modifyCheck = VisCheckBox("")
            val wireframeCheck = VisCheckBox("")
            selectionCountTotalLabels[key] = totalLabel
            selectionCountSelectedLabels[key] = selectedLabel
            selectionGenericRows[kind] = SelectionFilterRowWidgets(totalLabel, selectedLabel, drawCheck, modifyCheck, wireframeCheck)
            fun fireChange() {
                if (updatingSelectionFilters) return
                selectionFilterChanged(kind, drawCheck.isChecked, modifyCheck.isChecked, wireframeCheck.isChecked)
            }
            drawCheck.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) = fireChange()
            })
            modifyCheck.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) = fireChange()
            })
            wireframeCheck.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) = fireChange()
            })
            countsTable.add(VisLabel(label)).left()
            countsTable.add(totalLabel).center()
            countsTable.add(selectedLabel).center()
            countsTable.add(drawCheck).center().width(28f)
            countsTable.add(modifyCheck).center().width(28f)
            countsTable.add(wireframeCheck).center().width(28f).row()
        }

        fun addArchitectureRow(label: String, kind: ArchitectureElementKind) {
            val totalLabel = VisLabel("0")
            val selectedLabel = VisLabel("0")
            val drawCheck = VisCheckBox("")
            val modifyCheck = VisCheckBox("")
            val wireframeCheck = VisCheckBox("")
            selectionArchitectureRows[kind] = SelectionFilterRowWidgets(totalLabel, selectedLabel, drawCheck, modifyCheck, wireframeCheck)
            fun fireChange() {
                if (updatingSelectionFilters) return
                selectionFilterChanged(
                    when (kind) {
                        ArchitectureElementKind.WALL -> SelectionFilterKind.WALL
                        ArchitectureElementKind.SLAB -> SelectionFilterKind.SLAB
                        ArchitectureElementKind.STAIR -> SelectionFilterKind.STAIR
                        ArchitectureElementKind.FRAME -> SelectionFilterKind.FRAME
                    },
                    drawCheck.isChecked,
                    modifyCheck.isChecked,
                    wireframeCheck.isChecked
                )
            }
            drawCheck.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) = fireChange()
            })
            modifyCheck.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) = fireChange()
            })
            wireframeCheck.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) = fireChange()
            })

            countsTable.add(VisLabel(label)).left()
            countsTable.add(totalLabel).center()
            countsTable.add(selectedLabel).center()
            countsTable.add(drawCheck).center().width(28f)
            countsTable.add(modifyCheck).center().width(28f)
            countsTable.add(wireframeCheck).center().width(28f).row()
        }

        addFilterRow("Edges", "edges", SelectionFilterKind.EDGE)
        addFilterRow("Faces", "faces", SelectionFilterKind.FACE)
        addFilterRow("Voxels", "voxels", SelectionFilterKind.VOXEL)
        addFilterRow("Hotspots", "hotspots", SelectionFilterKind.HOTSPOT)
        addFilterRow("Objects", "objects", SelectionFilterKind.OBJECT)
        addFilterRow("Dimensions", "dimensions", SelectionFilterKind.DIMENSION)
        addFilterRow("Texts", "texts", SelectionFilterKind.TEXT)
        addArchitectureRow("Walls", ArchitectureElementKind.WALL)
        addArchitectureRow("Slabs", ArchitectureElementKind.SLAB)
        addArchitectureRow("Stairs", ArchitectureElementKind.STAIR)
        addArchitectureRow("Frames", ArchitectureElementKind.FRAME)

        content.add(countsTable).growX().row()
        selectionColorButton = createSelectionColorButton()
        val selectionColorRow = VisTable()
        selectionColorRow.defaults().pad(2f)
        selectionColorRow.add(selectionColorField).growX()
        selectionColorRow.add(selectionColorButton).size(toolbarButtonSize, toolbarButtonSize)
        content.add(selectionColorLabel).left().padTop(4f).row()
        content.add(selectionColorRow).growX().row()
        content.add(selectionTextLabel).left().padTop(4f).row()
        content.add(selectionTextField).growX().row()
        content.add(selectionTextSizeLabel).left().padTop(4f).row()
        content.add(selectionTextSizeField).growX().row()
        content.add(selectionTextScreenCheck).left().padTop(4f).row()
        val panel = buildDockSection("Selection", content, visible = true, collapsed = false)

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
        selectionColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingSelectionFields || !selectionColorEditable) {
                    return
                }
                val parsed = parseColorField(selectionColorField.text) ?: return
                selectionColorChanged(parsed)
            }
        })
        return panel
    }

    private fun buildGroupPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        content.add(groupStatusLabel).left().row()
        content.add(VisLabel("Name")).left().row()
        content.add(groupNameField).growX().row()
        content.add(groupGlueCheck).left().row()
        val panel = buildDockSection("Object", content, visible = true, collapsed = false)

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

    private fun buildObjectsPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        objectsList.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (tapCount >= 2) {
                    val selected = selectedObjectPrototype() ?: return
                    tutorialUiActionObserved("ui.action.objects.place_prototype", "Place Object Prototype")
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
        registerTutorialActionTarget("ui.action.objects.delete_prototype", objectsDeleteButton)
        objectsDeleteButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val selected = selectedObjectPrototype() ?: return
                tutorialUiActionObserved("ui.action.objects.delete_prototype", "Delete Prototype")
                objectPrototypeDelete(selected.id)
            }
        })
        content.add(objectsDeleteButton).left().padTop(4f).row()
        return buildDockSection("Objects", content, visible = true, collapsed = false)
    }

    private fun buildDockSection(title: String, body: Actor, visible: Boolean = true, collapsed: Boolean = true): DockSection {
        val section = DockSection(title, body)
        section.isVisible = visible
        section.setCollapsedState(collapsed)
        return section
    }

    private fun buildModelSettingsPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        content.add(modelHotspotsLabel).left().row()
        content.add(VisLabel("Unit name")).left().row()
        content.add(unitNameField).growX().row()
        content.add(VisLabel("Unit size")).left().padTop(4f).row()
        content.add(unitSizeField).growX().row()
        content.add(VisLabel("Grid size")).left().padTop(4f).row()
        content.add(gridSpacingField).growX().row()
        content.add(VisLabel("Circle segments")).left().padTop(4f).row()
        content.add(circleSegmentsField).growX().row()
        content.add(VisLabel("Snap radius")).left().padTop(6f).row()
        content.add(snapEpsilonSlider).growX().row()
        content.add(VisLabel("Walk move speed")).left().padTop(6f).row()
        content.add(walkthroughMoveSpeedField).growX().row()
        content.add(VisLabel("Walk jump velocity")).left().padTop(6f).row()
        content.add(walkthroughJumpField).growX().row()
        content.add(VisLabel("Walk gravity")).left().padTop(4f).row()
        content.add(walkthroughGravityField).growX().row()
        content.add(VisLabel("Walk height adjust speed")).left().padTop(4f).row()
        content.add(walkthroughHeightAdjustField).growX().row()
        val panel = buildDockSection("Model Settings", content, visible = true, collapsed = true)

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
        circleSegmentsField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                if (updatingModelSettingsFields) {
                    return
                }
                val sanitized = circleSegmentsField.text.filter { it.isDigit() }
                if (sanitized != circleSegmentsField.text) {
                    updatingModelSettingsFields = true
                    circleSegmentsField.text = sanitized
                    updatingModelSettingsFields = false
                }
                val segments = sanitized.toIntOrNull()?.coerceIn(3, 256) ?: return
                circleSegmentsChanged(segments)
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
        walkthroughJumpField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                applyWalkthroughTuningFromFields()
            }
        })
        walkthroughMoveSpeedField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                applyWalkthroughTuningFromFields()
            }
        })
        walkthroughGravityField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                applyWalkthroughTuningFromFields()
            }
        })
        walkthroughHeightAdjustField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                applyWalkthroughTuningFromFields()
            }
        })
        return panel
    }

    private fun buildUiSettingsPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        content.add(VisLabel("UI text size")).left().row()
        uiTextScaleSelect = VisSelectBox<String>().apply {
            setItems("1x", "1.5x", "2x")
        }
        content.add(uiTextScaleSelect).growX().row()
        uiToolbarAutoCollapseCheck = VisCheckBox("Auto-collapse toolbars")
        content.add(uiToolbarAutoCollapseCheck).left().row()
        content.add(VisLabel("Toolbar icon/button size")).left().row()
        uiToolbarSizeSelect = VisSelectBox<String>().apply {
            setItems("32 x 32 px", "48 x 48 px", "64 x 64 px")
        }
        content.add(uiToolbarSizeSelect).growX().row()
        content.add(VisLabel("Normal line width")).left().padTop(4f).row()
        uiNormalLineWidthSlider = VisSlider(1f, 8f, 0.5f, false)
        uiNormalLineWidthValueLabel = VisLabel()
        val normalLineRow = VisTable()
        normalLineRow.defaults().pad(2f)
        normalLineRow.add(uiNormalLineWidthSlider).growX()
        normalLineRow.add(uiNormalLineWidthValueLabel).right().width(56f)
        content.add(normalLineRow).growX().row()
        content.add(VisLabel("Thick line width")).left().padTop(4f).row()
        uiThickLineWidthSlider = VisSlider(1f, 16f, 0.5f, false)
        uiThickLineWidthValueLabel = VisLabel()
        val thickLineRow = VisTable()
        thickLineRow.defaults().pad(2f)
        thickLineRow.add(uiThickLineWidthSlider).growX()
        thickLineRow.add(uiThickLineWidthValueLabel).right().width(56f)
        content.add(thickLineRow).growX().row()
        content.add(VisLabel("Arrange toolbars")).left().padTop(4f).row()
        val arrangeRow = VisTable()
        arrangeRow.defaults().pad(2f)
        val arrangeHorizontalButton = VisTextButton("Flow L->R")
        val arrangeVerticalButton = VisTextButton("Flow T->D")
        arrangeRow.add(arrangeHorizontalButton).growX()
        arrangeRow.add(arrangeVerticalButton).growX()
        content.add(arrangeRow).growX().row()
        val uiInfoLabel = VisLabel("Affects built-in and mapped toolbar icons.").apply {
            setWrap(true)
        }
        content.add(uiInfoLabel).left().width(250f).padTop(2f).row()

        syncUiSettingsPanel()

        uiTextScaleSelect.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingUiSettingsFields) return
                val scale = when (uiTextScaleSelect.selected) {
                    "1.5x" -> 1.5f
                    "2x" -> 2f
                    else -> 1f
                }
                setUiTextScale(scale)
            }
        })

        uiToolbarSizeSelect.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingUiSettingsFields) return
                val size = when (uiToolbarSizeSelect.selected) {
                    "48 x 48 px" -> 48
                    "64 x 64 px" -> 64
                    else -> 32
                }
                setToolbarIconAndButtonSize(size)
            }
        })
        uiToolbarAutoCollapseCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingUiSettingsFields) return
                setToolbarAutoCollapse(uiToolbarAutoCollapseCheck.isChecked)
            }
        })
        uiNormalLineWidthSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingUiSettingsFields) return
                setNormalLineWidth(uiNormalLineWidthSlider.value)
            }
        })
        uiThickLineWidthSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingUiSettingsFields) return
                setThickLineWidth(uiThickLineWidthSlider.value)
            }
        })
        arrangeHorizontalButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                arrangeToolbarsHorizontalFlow()
            }
        })
        arrangeVerticalButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                arrangeToolbarsVerticalFlow()
            }
        })

        return buildDockSection("UI Settings", content, visible = true, collapsed = true)
    }

    private fun buildHelpPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()

        val title = VisLabel("Octodraw")
        val platformLabel = VisLabel(
            "Platform: " + when (Gdx.app?.type) {
                Application.ApplicationType.Android -> "Android"
                Application.ApplicationType.Desktop -> "Desktop"
                else -> (Gdx.app?.type?.name ?: "Unknown")
            }
        )
        val warning = VisLabel(
            "Android input note:\n" +
                "Octodraw is currently optimized for an external mouse and keyboard.\n" +
                "Touch-only use is limited (hover, right-click, wheel, modifier keys and precise picking are reduced).\n" +
                "Use a tablet/Chromebook/DeX setup with mouse + keyboard for the best experience."
        ).apply {
            setWrap(true)
        }
        val pluginsNote = VisLabel(
            "Android also supports user plugins from the app plugin folder in app storage."
        ).apply {
            setWrap(true)
        }

        content.add(title).left().row()
        content.add(platformLabel).left().row()
        content.add(warning).width(320f).left().padTop(6f).row()
        content.add(pluginsNote).width(320f).left().padTop(4f).row()
        return buildDockSection("Help / About", content, visible = false, collapsed = true)
    }

    private fun buildPolylineSettingsPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        val arcField = AppTextField(String.format(Locale.US, "%.3f", polylineSettings.arcMaxLength))
        val sizeField = AppTextField(String.format(Locale.US, "%.3f", polylineSettings.doubleLineSize))
        val offsetField = AppTextField(String.format(Locale.US, "%.3f", polylineSettings.doubleLineOffset))
        content.add(VisLabel("Arc max length")).left().row()
        content.add(arcField).growX().row()
        content.add(VisLabel("Double line size")).left().padTop(4f).row()
        content.add(sizeField).growX().row()
        content.add(VisLabel("Double line offset")).left().padTop(4f).row()
        content.add(offsetField).growX().row()
        val panel = buildDockSection("Polyline Settings", content, visible = false, collapsed = true)

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

    private fun buildVectorTextSettingsPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()
        vectorTextValueField = AppTextField(vectorTextSettings.text)
        vectorTextSizeField = AppTextField(String.format(Locale.US, "%.3f", vectorTextSettings.size))
        vectorTextTrackingField = AppTextField(String.format(Locale.US, "%.3f", vectorTextSettings.tracking))
        vectorTextLineSpacingField = AppTextField(String.format(Locale.US, "%.3f", vectorTextSettings.lineSpacing))
        vectorTextGlyphSourceField = AppTextField(vectorGlyphSourceProvider())
        vectorTextLoadGlyphSourceButton = VisTextButton("Load Glyph Source")
        vectorTextBrowseGlyphSourceButton = VisTextButton("Browse...")

        content.add(VisLabel("Text")).left().row()
        content.add(vectorTextValueField).growX().row()
        content.add(VisLabel("Size")).left().padTop(4f).row()
        content.add(vectorTextSizeField).growX().row()
        content.add(VisLabel("Tracking")).left().padTop(4f).row()
        content.add(vectorTextTrackingField).growX().row()
        content.add(VisLabel("Line spacing")).left().padTop(4f).row()
        content.add(vectorTextLineSpacingField).growX().row()
        content.add(VisLabel("Glyph source file (.octd / .ttf / .otf)")).left().padTop(6f).row()
        content.add(vectorTextGlyphSourceField).growX().row()
        val glyphButtons = VisTable()
        glyphButtons.defaults().padRight(6f)
        glyphButtons.add(vectorTextLoadGlyphSourceButton).left()
        glyphButtons.add(vectorTextBrowseGlyphSourceButton).left()
        content.add(glyphButtons).left().padTop(2f).row()
        content.add(VisLabel("Default uses embedded glyph map (alphabet)")).left().padTop(4f).row()

        val panel = buildDockSection("Vector Text", content, visible = true, collapsed = true)

        vectorTextValueField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingVectorTextFields) return
                val selection = selectionInfoProvider()
                val selectedId = selection.selectedTextId
                if (selection.selectedVectorText && selectedId != null) {
                    selectionTextChanged(selectedId, vectorTextValueField.text)
                } else {
                    vectorTextSettings.text = vectorTextValueField.text
                }
            }
        })
        vectorTextSizeField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingVectorTextFields) return
                val value = vectorTextSizeField.text.toFloatOrNull() ?: return
                val normalized = value.coerceAtLeast(0.01f)
                val selection = selectionInfoProvider()
                val selectedId = selection.selectedTextId
                if (selection.selectedVectorText && selectedId != null) {
                    selectionTextSizeChanged(selectedId, normalized)
                } else {
                    vectorTextSettings.size = normalized
                }
            }
        })
        vectorTextTrackingField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingVectorTextFields) return
                val value = vectorTextTrackingField.text.toFloatOrNull() ?: return
                val normalized = value.coerceAtLeast(0f)
                val selection = selectionInfoProvider()
                val selectedId = selection.selectedTextId
                if (selection.selectedVectorText && selectedId != null) {
                    vectorTextTrackingChanged(selectedId, normalized)
                } else {
                    vectorTextSettings.tracking = normalized
                }
            }
        })
        vectorTextLineSpacingField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingVectorTextFields) return
                val value = vectorTextLineSpacingField.text.toFloatOrNull() ?: return
                val normalized = value.coerceAtLeast(0.1f)
                val selection = selectionInfoProvider()
                val selectedId = selection.selectedTextId
                if (selection.selectedVectorText && selectedId != null) {
                    vectorTextLineSpacingChanged(selectedId, normalized)
                } else {
                    vectorTextSettings.lineSpacing = normalized
                }
            }
        })
        vectorTextLoadGlyphSourceButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val path = vectorTextGlyphSourceField.text.trim()
                if (path.isBlank()) {
                    return
                }
                vectorGlyphSourceLoadAction(path)
            }
        })
        vectorTextBrowseGlyphSourceButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                vectorGlyphSourceBrowseAction()
            }
        })
        return panel
    }

    private fun newArchitectureDockContentTable(): VisTable {
        return VisTable().apply {
            background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
            defaults().pad(4f).left().top()
        }
    }

    private fun buildArchitectureSettingsPanels() {
        architectureWallsContent = newArchitectureDockContentTable()
        architectureSlabsContent = newArchitectureDockContentTable()
        architectureStairsContent = newArchitectureDockContentTable()
        architectureFramesContent = newArchitectureDockContentTable()
        architectureModeLabel = VisLabel()
        architectureWallSectionLabel = VisLabel("Walls")
        architectureSlabSectionLabel = VisLabel("Slabs")
        architectureStairSectionLabel = VisLabel("Stairs")
        architectureFrameSectionLabel = VisLabel("Frames")
        architectureWallNameLabel = VisLabel("Wall name")
        architectureWallNameField = AppTextField()
        architectureWallThicknessLabel = VisLabel("Wall thickness")
        architectureWallThicknessField = AppTextField()
        architectureWallHeightLabel = VisLabel("Wall height")
        architectureWallHeightField = AppTextField()
        architectureWallInclinationLabel = VisLabel("Wall inclination (deg)")
        architectureWallInclinationField = AppTextField()
        architectureWallExteriorColorLabel = VisLabel("Wall exterior color")
        architectureWallExteriorColorField = AppTextField()
        architectureWallExteriorColorButton = createArchitectureColorButton("Wall Exterior Color", architectureWallExteriorColorField)
        architectureWallInteriorColorLabel = VisLabel("Wall interior color")
        architectureWallInteriorColorField = AppTextField()
        architectureWallInteriorColorButton = createArchitectureColorButton("Wall Interior Color", architectureWallInteriorColorField)
        architectureSlabNameLabel = VisLabel("Slab name")
        architectureSlabNameField = AppTextField()
        architectureSlabThicknessLabel = VisLabel("Slab thickness")
        architectureSlabThicknessField = AppTextField()
        architectureSlabTopColorLabel = VisLabel("Slab top color")
        architectureSlabTopColorField = AppTextField()
        architectureSlabTopColorButton = createArchitectureColorButton("Slab Top Color", architectureSlabTopColorField)
        architectureSlabBottomColorLabel = VisLabel("Slab bottom color")
        architectureSlabBottomColorField = AppTextField()
        architectureSlabBottomColorButton = createArchitectureColorButton("Slab Bottom Color", architectureSlabBottomColorField)
        architectureSlabSideColorLabel = VisLabel("Slab side color")
        architectureSlabSideColorField = AppTextField()
        architectureSlabSideColorButton = createArchitectureColorButton("Slab Side Color", architectureSlabSideColorField)
        architectureStairNameLabel = VisLabel("Stair name")
        architectureStairNameField = AppTextField()
        architectureStairHeightLabel = VisLabel("Stair height")
        architectureStairHeightField = AppTextField()
        architectureStairStepsLabel = VisLabel("Stair steps")
        architectureStairStepsField = AppTextField()
        architectureStairSupportLabel = VisLabel("Stair support thickness")
        architectureStairSupportField = AppTextField()
        architectureStairLeftRailLabel = VisLabel("Stair left rail grid")
        architectureStairLeftRailCheck = VisCheckBox("Enabled")
        architectureStairRightRailLabel = VisLabel("Stair right rail grid")
        architectureStairRightRailCheck = VisCheckBox("Enabled")
        architectureStairTreadColorLabel = VisLabel("Stair tread color")
        architectureStairTreadColorField = AppTextField()
        architectureStairTreadColorButton = createArchitectureColorButton("Stair Tread Color", architectureStairTreadColorField)
        architectureStairSupportColorLabel = VisLabel("Stair support color")
        architectureStairSupportColorField = AppTextField()
        architectureStairSupportColorButton = createArchitectureColorButton("Stair Support Color", architectureStairSupportColorField)
        architectureFrameNameLabel = VisLabel("Frame name")
        architectureFrameNameField = AppTextField()
        architectureFrameDepthLabel = VisLabel("Frame depth")
        architectureFrameDepthField = AppTextField()
        architectureFrameWidthLabel = VisLabel("Frame width")
        architectureFrameWidthField = AppTextField()
        architectureFrameColorLabel = VisLabel("Frame color")
        architectureFrameColorField = AppTextField()
        architectureFrameColorButton = createArchitectureColorButton("Frame Color", architectureFrameColorField)
        architectureFrameGlazingLabel = VisLabel("Frame glazing")
        architectureFrameGlazingCheck = VisCheckBox("Enabled")
        architectureFrameGlazingColorLabel = VisLabel("Glazing color")
        architectureFrameGlazingColorField = AppTextField()
        architectureFrameGlazingColorButton = createArchitectureColorButton("Frame Glazing Color", architectureFrameGlazingColorField)

        architectureWallsPanel = buildDockSection("Architecture - Walls", architectureWallsContent, visible = true, collapsed = true)
        architectureSlabsPanel = buildDockSection("Architecture - Slabs", architectureSlabsContent, visible = true, collapsed = true)
        architectureStairsPanel = buildDockSection("Architecture - Stairs", architectureStairsContent, visible = true, collapsed = true)
        architectureFramesPanel = buildDockSection("Architecture - Frames", architectureFramesContent, visible = true, collapsed = true)

        architectureWallNameField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val summary = architectureSelectionSummaryProvider()
                val id = summary.singleWallId ?: return
                val name = architectureWallNameField.text.trim()
                if (name.isBlank()) return
                architectureElementNameChanged(ArchitectureElementKind.WALL, id, name)
            }
        })
        architectureSlabNameField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val summary = architectureSelectionSummaryProvider()
                val id = summary.singleSlabId ?: return
                val name = architectureSlabNameField.text.trim()
                if (name.isBlank()) return
                architectureElementNameChanged(ArchitectureElementKind.SLAB, id, name)
            }
        })
        architectureStairNameField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val summary = architectureSelectionSummaryProvider()
                val id = summary.singleStairId ?: return
                val name = architectureStairNameField.text.trim()
                if (name.isBlank()) return
                architectureElementNameChanged(ArchitectureElementKind.STAIR, id, name)
            }
        })
        architectureFrameNameField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val summary = architectureSelectionSummaryProvider()
                val id = summary.singleFrameId ?: return
                val name = architectureFrameNameField.text.trim()
                if (name.isBlank()) return
                architectureElementNameChanged(ArchitectureElementKind.FRAME, id, name)
            }
        })

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
                        selection.frameColor ?: Color(architectureSettings.frameColor),
                        selection.frameGlazingEnabled ?: architectureSettings.frameGlazingEnabled,
                        selection.frameGlazingColor ?: Color(architectureSettings.frameGlazingColor)
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
                        selection.frameColor ?: Color(architectureSettings.frameColor),
                        selection.frameGlazingEnabled ?: architectureSettings.frameGlazingEnabled,
                        selection.frameGlazingColor ?: Color(architectureSettings.frameGlazingColor)
                    )
                }
            }
        })
        architectureFrameGlazingCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) {
                    return
                }
                val enabled = architectureFrameGlazingCheck.isChecked
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.frameGlazingEnabled = enabled
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.FRAME) {
                    architectureFrameChanged(
                        selection.id,
                        selection.frameDepth ?: architectureSettings.frameDepth,
                        selection.frameWidth ?: architectureSettings.frameWidth,
                        selection.frameColor ?: Color(architectureSettings.frameColor),
                        enabled,
                        selection.frameGlazingColor ?: Color(architectureSettings.frameGlazingColor)
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
                        color,
                        selection.frameGlazingEnabled ?: architectureSettings.frameGlazingEnabled,
                        selection.frameGlazingColor ?: Color(architectureSettings.frameGlazingColor)
                    )
                }
            }
        })
        architectureFrameGlazingColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingArchitectureFields) return
                val color = parseColorField(architectureFrameGlazingColorField.text) ?: return
                updateArchitectureColorButtonSwatch(architectureFrameGlazingColorButton, color)
                val selection = architectureElementProvider()
                if (selection == null) {
                    architectureSettings.frameGlazingColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.FRAME) {
                    architectureFrameChanged(
                        selection.id,
                        selection.frameDepth ?: architectureSettings.frameDepth,
                        selection.frameWidth ?: architectureSettings.frameWidth,
                        selection.frameColor ?: Color(architectureSettings.frameColor),
                        selection.frameGlazingEnabled ?: architectureSettings.frameGlazingEnabled,
                        color
                    )
                }
            }
        })

        rebuildArchitectureSettingsContent(null)
        updateArchitectureSettingsPanel()
        rebuildArchitectureSettingsContent(null)
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
        architectureSettings.frameGlazingEnabled = uiPrefs.getBoolean(archDefaultFrameGlazingEnabledKey, architectureSettings.frameGlazingEnabled)
        architectureSettings.frameGlazingColor.set(loadColorPref(archDefaultFrameGlazingColorKey, architectureSettings.frameGlazingColor))
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
        uiPrefs.putBoolean(archDefaultFrameGlazingEnabledKey, architectureSettings.frameGlazingEnabled)
        uiPrefs.putString(archDefaultFrameGlazingColorKey, formatColorField(architectureSettings.frameGlazingColor))
        uiPrefs.flush()
    }

    private fun buildHvacSettingsPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left()

        hvacPlumbingDiameterField = AppTextField()
        hvacPlumbingSidesField = AppTextField()
        hvacPlumbingColorField = AppTextField()
        hvacPlumbingColorButton = createHvacColorButton("HVAC Plumbing Color", hvacPlumbingColorField)
        hvacVentilationAutoJoinCheck = VisCheckBox("Enabled")
        hvacVentilationWidthField = AppTextField()
        hvacVentilationHeightField = AppTextField()
        hvacVentilationHumpHalfSpanField = AppTextField()
        hvacVentilationHumpClearanceField = AppTextField()
        hvacVentilationColorField = AppTextField()
        hvacVentilationColorButton = createHvacColorButton("HVAC Ventilation Color", hvacVentilationColorField)

        content.add(VisLabel("Plumbing")).colspan(2).left().growX().row()
        content.add(VisLabel("Pipe diameter")).left()
        content.add(hvacPlumbingDiameterField).growX().row()
        content.add(VisLabel("Pipe sides")).left()
        content.add(hvacPlumbingSidesField).growX().row()
        content.add(VisLabel("Pipe color")).left()
        run {
            val colorControls = VisTable()
            colorControls.defaults().left()
            colorControls.add(hvacPlumbingColorField).growX().padRight(4f)
            colorControls.add(hvacPlumbingColorButton).size(24f, 24f)
            content.add(colorControls).growX().row()
        }

        content.add(VisLabel("Ventilation")).colspan(2).left().growX().padTop(6f).row()
        content.add(VisLabel("Auto join")).left()
        content.add(hvacVentilationAutoJoinCheck).left().row()
        content.add(VisLabel("Duct width")).left()
        content.add(hvacVentilationWidthField).growX().row()
        content.add(VisLabel("Duct height")).left()
        content.add(hvacVentilationHeightField).growX().row()
        content.add(VisLabel("Hump half span")).left()
        content.add(hvacVentilationHumpHalfSpanField).growX().row()
        content.add(VisLabel("Hump clearance")).left()
        content.add(hvacVentilationHumpClearanceField).growX().row()
        content.add(VisLabel("Duct color")).left()
        run {
            val colorControls = VisTable()
            colorControls.defaults().left()
            colorControls.add(hvacVentilationColorField).growX().padRight(4f)
            colorControls.add(hvacVentilationColorButton).size(24f, 24f)
            content.add(colorControls).growX().row()
        }

        hvacPlumbingDiameterField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHvacFields) {
                    return
                }
                val value = hvacPlumbingDiameterField.text.toFloatOrNull()?.coerceAtLeast(0.01f) ?: return
                hvacSettings.plumbingDiameter = value
                saveHvacDefaults()
            }
        })
        hvacPlumbingSidesField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHvacFields) {
                    return
                }
                val value = hvacPlumbingSidesField.text.toIntOrNull()?.coerceIn(3, 128) ?: return
                hvacSettings.plumbingSides = value
                saveHvacDefaults()
            }
        })
        hvacVentilationWidthField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHvacFields) {
                    return
                }
                val value = hvacVentilationWidthField.text.toFloatOrNull()?.coerceAtLeast(0.01f) ?: return
                hvacSettings.ventilationWidth = value
                saveHvacDefaults()
            }
        })
        hvacVentilationHeightField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHvacFields) {
                    return
                }
                val value = hvacVentilationHeightField.text.toFloatOrNull()?.coerceAtLeast(0.01f) ?: return
                hvacSettings.ventilationHeight = value
                saveHvacDefaults()
            }
        })
        hvacVentilationAutoJoinCheck.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHvacFields) {
                    return
                }
                hvacSettings.ventilationAutoJoin = hvacVentilationAutoJoinCheck.isChecked
                saveHvacDefaults()
            }
        })
        hvacVentilationHumpHalfSpanField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHvacFields) {
                    return
                }
                val value = hvacVentilationHumpHalfSpanField.text.toFloatOrNull()?.coerceAtLeast(0.01f) ?: return
                hvacSettings.ventilationHumpHalfSpan = value
                saveHvacDefaults()
            }
        })
        hvacVentilationHumpClearanceField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHvacFields) {
                    return
                }
                val value = hvacVentilationHumpClearanceField.text.toFloatOrNull()?.coerceAtLeast(0f) ?: return
                hvacSettings.ventilationHumpClearance = value
                saveHvacDefaults()
            }
        })
        hvacPlumbingColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHvacFields) {
                    return
                }
                val color = parseColorField(hvacPlumbingColorField.text) ?: return
                hvacSettings.plumbingColor.set(color)
                updateArchitectureColorButtonSwatch(hvacPlumbingColorButton, color)
                saveHvacDefaults()
            }
        })
        hvacVentilationColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHvacFields) {
                    return
                }
                val color = parseColorField(hvacVentilationColorField.text) ?: return
                hvacSettings.ventilationColor.set(color)
                updateArchitectureColorButtonSwatch(hvacVentilationColorButton, color)
                saveHvacDefaults()
            }
        })

        val panel = buildDockSection("HVAC Settings", content, visible = false, collapsed = true)
        updateHvacSettingsPanel()
        return panel
    }

    private fun createHvacColorButton(title: String, field: VisTextField): AppImageTextButton {
        val icon = iconFor("color", createActionIconDrawable(Color(0.8f, 0.8f, 0.8f, 1f)))
        return AppImageTextButton("", icon).apply {
            applyWhiteButtonStyle(this)
            applyIconStyle(this, icon)
            registerTutorialActionTarget("ui.action.color.${normalizeTutorialActionKey(title)}", this)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    tutorialUiActionObserved("ui.action.color.${normalizeTutorialActionKey(title)}", title)
                    val current = parseColorField(field.text) ?: Color.WHITE
                    showArchitectureColorPicker(title, current) { picked ->
                        applyHvacColorPickerValue(field, this@apply, picked)
                    }
                }
            })
        }
    }

    private fun createSelectionColorButton(): AppImageTextButton {
        val icon = iconFor("color", createActionIconDrawable(Color(0.8f, 0.8f, 0.8f, 1f)))
        return AppImageTextButton("", icon).apply {
            applyWhiteButtonStyle(this)
            applyIconStyle(this, icon)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (!selectionColorEditable) {
                        return
                    }
                    val current = parseColorField(selectionColorField.text) ?: status.paintColor
                    showArchitectureColorPicker("Selection Color", current) { picked ->
                        updatingSelectionFields = true
                        selectionColorField.text = formatColorField(picked)
                        updateArchitectureColorButtonSwatch(this@apply, picked)
                        updatingSelectionFields = false
                        selectionColorChanged(picked)
                    }
                }
            })
        }
    }

    private fun applyHvacColorPickerValue(field: VisTextField, button: AppImageTextButton, color: Color) {
        val value = formatColorField(color)
        updatingHvacFields = true
        field.text = value
        updateArchitectureColorButtonSwatch(button, color)
        updatingHvacFields = false
        when (field) {
            hvacPlumbingColorField -> hvacSettings.plumbingColor.set(color)
            hvacVentilationColorField -> hvacSettings.ventilationColor.set(color)
        }
        saveHvacDefaults()
    }

    private fun loadHvacDefaults() {
        hvacSettings.plumbingDiameter =
            uiPrefs.getFloat(hvacDefaultPlumbingDiameterKey, hvacSettings.plumbingDiameter).coerceAtLeast(0.01f)
        hvacSettings.plumbingSides =
            uiPrefs.getInteger(hvacDefaultPlumbingSidesKey, hvacSettings.plumbingSides).coerceIn(3, 128)
        hvacSettings.plumbingColor.set(loadColorPref(hvacDefaultPlumbingColorKey, hvacSettings.plumbingColor))
        hvacSettings.ventilationAutoJoin =
            uiPrefs.getBoolean(hvacDefaultVentilationAutoJoinKey, hvacSettings.ventilationAutoJoin)
        hvacSettings.ventilationWidth =
            uiPrefs.getFloat(hvacDefaultVentilationWidthKey, hvacSettings.ventilationWidth).coerceAtLeast(0.01f)
        hvacSettings.ventilationHeight =
            uiPrefs.getFloat(hvacDefaultVentilationHeightKey, hvacSettings.ventilationHeight).coerceAtLeast(0.01f)
        hvacSettings.ventilationHumpHalfSpan =
            uiPrefs.getFloat(
                hvacDefaultVentilationHumpHalfSpanKey,
                hvacSettings.ventilationHumpHalfSpan
            ).coerceAtLeast(0.01f)
        hvacSettings.ventilationHumpClearance =
            uiPrefs.getFloat(
                hvacDefaultVentilationHumpClearanceKey,
                hvacSettings.ventilationHumpClearance
            ).coerceAtLeast(0f)
        hvacSettings.ventilationColor.set(loadColorPref(hvacDefaultVentilationColorKey, hvacSettings.ventilationColor))
    }

    private fun saveHvacDefaults() {
        uiPrefs.putFloat(hvacDefaultPlumbingDiameterKey, hvacSettings.plumbingDiameter)
        uiPrefs.putInteger(hvacDefaultPlumbingSidesKey, hvacSettings.plumbingSides)
        uiPrefs.putString(hvacDefaultPlumbingColorKey, formatColorField(hvacSettings.plumbingColor))
        uiPrefs.putBoolean(hvacDefaultVentilationAutoJoinKey, hvacSettings.ventilationAutoJoin)
        uiPrefs.putFloat(hvacDefaultVentilationWidthKey, hvacSettings.ventilationWidth)
        uiPrefs.putFloat(hvacDefaultVentilationHeightKey, hvacSettings.ventilationHeight)
        uiPrefs.putFloat(hvacDefaultVentilationHumpHalfSpanKey, hvacSettings.ventilationHumpHalfSpan)
        uiPrefs.putFloat(hvacDefaultVentilationHumpClearanceKey, hvacSettings.ventilationHumpClearance)
        uiPrefs.putString(hvacDefaultVentilationColorKey, formatColorField(hvacSettings.ventilationColor))
        uiPrefs.flush()
    }

    private fun loadHotspotDefaults() {
        val savedOperation = uiPrefs.getString(hotspotDefaultOperationKey, hotspotSettings.defaultOperation.name)
        hotspotSettings.defaultOperation = try {
            HotspotStore.OperationKind.valueOf(savedOperation)
        } catch (_: IllegalArgumentException) {
            HotspotStore.OperationKind.MOVE
        }
        val savedShape = uiPrefs.getString(hotspotDefaultShapeKey, hotspotSettings.defaultShape.name)
        hotspotSettings.defaultShape = try {
            HotspotStore.ShapeKind.valueOf(savedShape)
        } catch (_: IllegalArgumentException) {
            HotspotStore.ShapeKind.CIRCLE
        }
        hotspotSettings.defaultColor.set(loadColorPref(hotspotDefaultColorKey, hotspotSettings.defaultColor))
    }

    private fun saveHotspotDefaults() {
        uiPrefs.putString(hotspotDefaultOperationKey, hotspotSettings.defaultOperation.name)
        uiPrefs.putString(hotspotDefaultShapeKey, hotspotSettings.defaultShape.name)
        uiPrefs.putString(hotspotDefaultColorKey, formatColorField(hotspotSettings.defaultColor))
        uiPrefs.flush()
    }

    private fun buildHotspotSettingsPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left()

        hotspotModeLabel = VisLabel("No hotspot selected")
        hotspotNameField = AppTextField()
        hotspotOperationSelect = VisSelectBox()
        hotspotOperationSelect.setItems(*HotspotStore.OperationKind.entries.toTypedArray())
        hotspotShapeSelect = VisSelectBox()
        hotspotShapeSelect.setItems(*HotspotStore.ShapeKind.entries.toTypedArray())
        hotspotColorField = AppTextField()
        hotspotColorButton = createArchitectureColorButton("Hotspot color", hotspotColorField)
        hotspotAttachedLabel = VisLabel("Attached: edges 0 faces 0")
        hotspotReferenceLabel = VisLabel("Reference: none")
        hotspotAddButton = VisTextButton("Add At Cursor")
        hotspotDeleteButton = VisTextButton("Delete Selected")
        hotspotAttachButton = VisTextButton("Attach Selection")
        hotspotSelectAttachedButton = VisTextButton("Select Attached")
        hotspotPickReferenceButton = VisTextButton("Pick Reference")
        hotspotClearReferenceButton = VisTextButton("Clear Reference")

        content.add(hotspotModeLabel).colspan(3).left().growX().row()
        content.add(VisLabel("Name")).left()
        content.add(hotspotNameField).growX().row()
        content.add(VisLabel("Operation")).left()
        content.add(hotspotOperationSelect).growX().row()
        content.add(VisLabel("Shape")).left()
        content.add(hotspotShapeSelect).growX().row()
        content.add(VisLabel("Color")).left()
        content.add(hotspotColorField).growX()
        content.add(hotspotColorButton).size(30f, 24f).left().row()
        content.add(hotspotAttachedLabel).colspan(3).left().growX().row()
        content.add(hotspotReferenceLabel).colspan(3).left().growX().row()
        content.add(hotspotAttachButton).left().growX()
        content.add(hotspotSelectAttachedButton).left().growX().row()
        content.add(hotspotPickReferenceButton).left().growX()
        content.add(hotspotClearReferenceButton).left().growX().row()
        content.add(hotspotAddButton).left().growX()
        content.add(hotspotDeleteButton).left().growX().row()

        hotspotNameField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHotspotFields) {
                    return
                }
                val targetId = selectedHotspotId ?: return
                hotspotNameChanged(targetId, hotspotNameField.text)
            }
        })
        hotspotOperationSelect.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHotspotFields) {
                    return
                }
                val operation = hotspotOperationSelect.selected ?: return
                val targetId = selectedHotspotId
                if (targetId == null) {
                    hotspotSettings.defaultOperation = operation
                    hotspotDefaultOperationChanged(operation)
                    saveHotspotDefaults()
                } else {
                    hotspotOperationChanged(targetId, operation)
                }
            }
        })
        hotspotShapeSelect.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHotspotFields) {
                    return
                }
                val shape = hotspotShapeSelect.selected ?: return
                val targetId = selectedHotspotId
                if (targetId == null) {
                    hotspotSettings.defaultShape = shape
                    hotspotDefaultShapeChanged(shape)
                    saveHotspotDefaults()
                } else {
                    hotspotShapeChanged(targetId, shape)
                }
            }
        })
        hotspotColorField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (updatingHotspotFields) {
                    return
                }
                val parsed = parseColorField(hotspotColorField.text) ?: return
                val targetId = selectedHotspotId
                if (targetId == null) {
                    hotspotSettings.defaultColor.set(parsed)
                    hotspotDefaultColorChanged(Color(parsed))
                    updateArchitectureColorButtonSwatch(hotspotColorButton, parsed)
                    saveHotspotDefaults()
                } else {
                    hotspotColorChanged(targetId, Color(parsed))
                    updateArchitectureColorButtonSwatch(hotspotColorButton, parsed)
                }
            }
        })
        hotspotColorButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialUiActionObserved("ui.action.color.hotspot", "Hotspot color")
                val current = parseColorField(hotspotColorField.text) ?: hotspotSettings.defaultColor
                showArchitectureColorPicker("Hotspot color", current) { picked ->
                    updatingHotspotFields = true
                    hotspotColorField.text = formatColorField(picked)
                    updateArchitectureColorButtonSwatch(hotspotColorButton, picked)
                    updatingHotspotFields = false
                    val targetId = selectedHotspotId
                    if (targetId == null) {
                        hotspotSettings.defaultColor.set(picked)
                        hotspotDefaultColorChanged(Color(picked))
                        saveHotspotDefaults()
                    } else {
                        hotspotColorChanged(targetId, Color(picked))
                    }
                }
            }
        })
        registerTutorialActionTarget("ui.action.color.hotspot", hotspotColorButton)
        hotspotAddButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialUiActionObserved("ui.action.hotspot.add_at_cursor", "Add At Cursor")
                hotspotAddAtCursor()
            }
        })
        registerTutorialActionTarget("ui.action.hotspot.add_at_cursor", hotspotAddButton)
        hotspotDeleteButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialUiActionObserved("ui.action.hotspot.delete_selected", "Delete Selected")
                hotspotDeleteSelected()
            }
        })
        registerTutorialActionTarget("ui.action.hotspot.delete_selected", hotspotDeleteButton)
        hotspotAttachButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialUiActionObserved("ui.action.hotspot.attach_selection", "Attach Selection")
                selectedHotspotId?.let { hotspotAttachSelection(it) }
            }
        })
        registerTutorialActionTarget("ui.action.hotspot.attach_selection", hotspotAttachButton)
        hotspotSelectAttachedButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialUiActionObserved("ui.action.hotspot.select_attached", "Select Attached")
                selectedHotspotId?.let { hotspotSelectAttached(it) }
            }
        })
        registerTutorialActionTarget("ui.action.hotspot.select_attached", hotspotSelectAttachedButton)
        hotspotPickReferenceButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialUiActionObserved("ui.action.hotspot.pick_reference", "Pick Reference")
                selectedHotspotId?.let { hotspotBeginReferencePick(it) }
            }
        })
        registerTutorialActionTarget("ui.action.hotspot.pick_reference", hotspotPickReferenceButton)
        hotspotClearReferenceButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialUiActionObserved("ui.action.hotspot.clear_reference", "Clear Reference")
                selectedHotspotId?.let { hotspotClearReference(it) }
            }
        })
        registerTutorialActionTarget("ui.action.hotspot.clear_reference", hotspotClearReferenceButton)

        val panel = buildDockSection("Hotspot Settings", content, visible = false, collapsed = true)
        updateHotspotSettingsPanel()
        return panel
    }

    private fun buildTutorialsPanel(): DockSection {
        val content = VisTable()
        content.background = darkBarDrawable ?: createDarkBarDrawable().also { darkBarDrawable = it }
        content.defaults().pad(4f).left().growX()

        tutorialsModeLabel = VisLabel("Mode: Idle")
        tutorialsStepLabel = VisLabel("Step: 0/0")
        tutorialsListContent = VisTable().apply {
            defaults().left().growX().pad(1f)
            top()
        }
        val listScroll = com.kotcrab.vis.ui.widget.VisScrollPane(tutorialsListContent).apply {
            setFadeScrollBars(false)
            setScrollingDisabled(true, false)
        }
        tutorialsRecordButton = VisTextButton("Record")
        tutorialsStopRecordButton = VisTextButton("Stop Rec")
        tutorialsPlayButton = VisTextButton("Play")
        tutorialsPauseButton = VisTextButton("Pause")
        tutorialsStopButton = VisTextButton("Stop")

        content.add(tutorialsModeLabel).left().growX().row()
        content.add(tutorialsStepLabel).left().growX().row()
        content.add(VisLabel("Tutorials")).left().padTop(4f).row()
        content.add(listScroll).growX().height(150f).row()

        val buttonsRow = VisTable()
        buttonsRow.defaults().pad(2f).left().growX()
        buttonsRow.add(tutorialsRecordButton)
        buttonsRow.add(tutorialsStopRecordButton).row()
        buttonsRow.add(tutorialsPlayButton)
        buttonsRow.add(tutorialsPauseButton)
        buttonsRow.add(tutorialsStopButton)
        content.add(buttonsRow).growX().row()
        tutorialsRecordButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialStartRecording()
            }
        })
        tutorialsStopRecordButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialStopRecording()
            }
        })
        tutorialsPlayButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialPlay(selectedTutorialPath)
            }
        })
        tutorialsPauseButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialPauseToggle()
            }
        })
        tutorialsStopButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialStop()
            }
        })

        return buildDockSection("Tutorials", content, visible = true, collapsed = true)
    }

    private fun buildTutorialMessageWindow(): CollapsibleWindow {
        val window = CollapsibleWindow("Tutorial", showCloseButton = false)
        window.isResizable = false
        tutorialMessageLabel = VisLabel("").apply { setWrap(true) }
        tutorialMessageStatusLabel = VisLabel("")
        tutorialPreviewBeforeImage = Image().apply {
            setScaling(Scaling.fit)
            touchable = Touchable.disabled
        }
        tutorialPreviewAfterImage = Image().apply {
            setScaling(Scaling.fit)
            touchable = Touchable.disabled
        }
        tutorialPreviousButton = VisTextButton("Previous")
        tutorialNextButton = VisTextButton("Next").apply { isDisabled = true }
        tutorialPreviousButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialPrevious()
            }
        })
        tutorialNextButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                tutorialNext()
            }
        })
        val content = VisTable()
        content.defaults().pad(4f).left().growX()
        content.add(tutorialMessageLabel).width(320f).left().growX().row()
        content.add(tutorialMessageStatusLabel).left().growX().row()
        val previews = VisTable()
        previews.defaults().pad(2f).top()
        previews.add(buildTutorialPreviewPane("Initial", tutorialPreviewBeforeImage))
        previews.add(buildTutorialPreviewPane("Final", tutorialPreviewAfterImage))
        content.add(previews).left().padTop(2f).row()
        val controls = VisTable()
        controls.defaults().pad(2f).left()
        controls.add(tutorialPreviousButton)
        controls.add(tutorialNextButton)
        content.add(controls).left().padTop(2f).row()
        window.add(content).pad(4f).grow()
        window.pack()
        window.isVisible = false
        window.addListener(object : InputListener() {
            override fun touchUp(
                event: InputEvent?,
                x: Float,
                y: Float,
                pointer: Int,
                button: Int
            ) {
                saveTutorialMessageWindowPosition()
            }
        })
        return window
    }

    private fun buildRenderWindow(): CollapsibleWindow {
        val window = CollapsibleWindow("Rendering", showCloseButton = false)
        window.isResizable = false
        renderPreviewImage = Image().apply {
            setScaling(Scaling.fit)
            touchable = Touchable.disabled
        }
        renderStatusLabel = VisLabel("Idle")
        renderResolutionValueLabel = VisLabel(renderResolutionLabelProvider())
        val sizePresetDivisors = intArrayOf(1, 2, 4, 8, 16)
        val sizePresetLabels = sizePresetDivisors.map { "Screen / $it" }
        val sizePresetSelect = VisSelectBox<String>().apply {
            setItems(*sizePresetLabels.toTypedArray())
            val currentIndex = sizePresetDivisors.indexOf(renderResolutionDivisorProvider()).let { index ->
                if (index >= 0) index else sizePresetDivisors.indexOf(4).coerceAtLeast(0)
            }
            selected = sizePresetLabels[currentIndex]
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    val divisorIndex = sizePresetLabels.indexOf(selected)
                    val divisor = sizePresetDivisors.getOrElse(divisorIndex) { 4 }
                    renderResolutionDivisorChanged(divisor)
                    renderResolutionValueLabel.setText(renderResolutionLabelProvider())
                }
            })
        }
        val maxWorkers = renderWorkerCountMaxProvider().coerceAtLeast(1)
        val workerValueLabel = VisLabel("")
        val workerSlider = VisSlider(1f, maxWorkers.toFloat(), 1f, false).apply {
            value = renderWorkerCountProvider().toFloat().coerceIn(1f, maxWorkers.toFloat())
            isDisabled = maxWorkers <= 1
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    renderWorkerCountChanged(value.toInt())
                    workerValueLabel.setText(value.toInt().toString())
                }
            })
        }
        val blendValueLabel = VisLabel("")
        val blurValueLabel = VisLabel("")
        val glassValueLabel = VisLabel("")
        val cameraLightValueLabel = VisLabel("")
        val pruningCheckBox = VisCheckBox("").apply {
            isChecked = renderPruningEnabledProvider()
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    renderPruningEnabledChanged(isChecked)
                }
            })
        }
        val blendSlider = VisSlider(0f, 1f, 0.01f, false).apply {
            value = renderBlendProvider()
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    renderBlendChanged(value)
                    blendValueLabel.setText("${(value * 100f).toInt()}%")
                }
            })
        }
        val blurSlider = VisSlider(0f, 3f, 1f, false).apply {
            value = renderBlurProvider().toFloat()
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    renderBlurChanged(value.toInt())
                    blurValueLabel.setText("${value.toInt()} px")
                }
            })
        }
        val glassSlider = VisSlider(0f, 1f, 0.01f, false).apply {
            value = renderGlassTransmissionProvider()
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    renderGlassTransmissionChanged(value)
                    glassValueLabel.setText("${(value * 100f).toInt()}%")
                }
            })
        }
        val cameraLightSlider = VisSlider(0f, 100f, 1f, false).apply {
            value = renderCameraLightIntensityProvider()
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    renderCameraLightIntensityChanged(value)
                    cameraLightValueLabel.setText(value.toInt().toString())
                }
            })
        }
        workerValueLabel.setText(workerSlider.value.toInt().toString())
        blendValueLabel.setText("${(blendSlider.value * 100f).toInt()}%")
        blurValueLabel.setText("${blurSlider.value.toInt()} px")
        glassValueLabel.setText("${(glassSlider.value * 100f).toInt()}%")
        cameraLightValueLabel.setText(cameraLightSlider.value.toInt().toString())
        val content = VisTable()
        content.defaults().pad(4f).left().growX()
        val slot = VisTable().apply {
            background = tutorialPreviewSlotDrawable
                ?: createButtonBackgroundDrawable(Color(0.12f, 0.12f, 0.14f, 1f), Color(0.35f, 0.35f, 0.4f, 1f))
                    .also { tutorialPreviewSlotDrawable = it }
            touchable = Touchable.disabled
        }
        renderPreviewCell = slot.add(renderPreviewImage).width(480f).height(270f).center()
        content.add(slot).row()
        content.add(renderStatusLabel).left().growX().row()
        val controls = VisTable()
        controls.defaults().pad(2f).left()
        controls.add(VisLabel("Size")).width(58f)
        controls.add(sizePresetSelect).width(180f)
        controls.add(renderResolutionValueLabel).left().row()
        controls.add(VisLabel("Workers")).width(58f)
        controls.add(workerSlider).width(180f)
        controls.add(workerValueLabel).left().row()
        controls.add(VisLabel("Prune")).width(58f)
        controls.add(pruningCheckBox).left().colspan(2).row()
        controls.add(VisLabel("Blend")).width(58f)
        controls.add(blendSlider).width(180f)
        controls.add(blendValueLabel).left().row()
        controls.add(VisLabel("Blur")).width(58f)
        controls.add(blurSlider).width(180f)
        controls.add(blurValueLabel).left().row()
        controls.add(VisLabel("Glass")).width(58f)
        controls.add(glassSlider).width(180f)
        controls.add(glassValueLabel).left().row()
        controls.add(VisLabel("Light")).width(58f)
        controls.add(cameraLightSlider).width(180f)
        controls.add(cameraLightValueLabel).left().row()
        content.add(controls).left().growX().row()
        window.add(content).pad(4f).grow()
        window.pack()
        window.isVisible = false
        window.addListener(object : InputListener() {
            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                saveRenderWindowPosition()
            }
        })
        return window
    }

    private fun buildTutorialPreviewPane(title: String, image: Image): VisTable {
        val pane = VisTable()
        pane.defaults().pad(2f).left().growX()
        pane.add(VisLabel(title)).left().row()
        val slot = VisTable().apply {
            background = tutorialPreviewSlotDrawable
                ?: createButtonBackgroundDrawable(Color(0.12f, 0.12f, 0.14f, 1f), Color(0.35f, 0.35f, 0.4f, 1f))
                    .also { tutorialPreviewSlotDrawable = it }
            touchable = Touchable.disabled
        }
        slot.add(image).grow().center()
        pane.add(slot).width(380f).height(200f).grow().row()
        return pane
    }

    private fun updateTutorialPreviewTextures(state: TutorialUiState) {
        updateTutorialPreviewTexture(
            encoded = state.currentPreviewBeforePngBase64,
            image = tutorialPreviewBeforeImage,
            currentKey = tutorialPreviewBeforeKey,
            currentTexture = tutorialPreviewBeforeTexture
        ) { newKey, newTexture ->
            tutorialPreviewBeforeKey = newKey
            tutorialPreviewBeforeTexture = newTexture
        }
        updateTutorialPreviewTexture(
            encoded = state.currentPreviewAfterPngBase64,
            image = tutorialPreviewAfterImage,
            currentKey = tutorialPreviewAfterKey,
            currentTexture = tutorialPreviewAfterTexture
        ) { newKey, newTexture ->
            tutorialPreviewAfterKey = newKey
            tutorialPreviewAfterTexture = newTexture
        }
    }

    private fun updateTutorialPreviewTexture(
        encoded: String?,
        image: Image,
        currentKey: String?,
        currentTexture: Texture?,
        store: (String?, Texture?) -> Unit
    ) {
        val normalized = encoded?.takeIf { it.isNotBlank() }
        if (normalized == currentKey) {
            return
        }
        currentTexture?.dispose()
        if (normalized.isNullOrBlank()) {
            image.drawable = null
            store(null, null)
            return
        }
        val newTexture = decodeTutorialPreviewTexture(normalized)
        if (newTexture == null) {
            image.drawable = null
            store(null, null)
            return
        }
        image.drawable = TextureRegionDrawable(TextureRegion(newTexture))
        store(normalized, newTexture)
    }

    private fun decodeTutorialPreviewTexture(encoded: String): Texture? {
        return try {
            val bytes = Base64.getDecoder().decode(encoded)
            val pixmap = Pixmap(bytes, 0, bytes.size)
            val texture = Texture(pixmap)
            pixmap.dispose()
            texture
        } catch (_: Throwable) {
            null
        }
    }

    private fun disposeTutorialPreviewTextures() {
        tutorialPreviewBeforeTexture?.dispose()
        tutorialPreviewAfterTexture?.dispose()
        tutorialPreviewBeforeTexture = null
        tutorialPreviewAfterTexture = null
        tutorialPreviewBeforeKey = null
        tutorialPreviewAfterKey = null
        if (::tutorialPreviewBeforeImage.isInitialized) {
            tutorialPreviewBeforeImage.drawable = null
        }
        if (::tutorialPreviewAfterImage.isInitialized) {
            tutorialPreviewAfterImage.drawable = null
        }
    }

    private fun clearRenderPreviewReference() {
        renderPreviewTexture = null
        if (::renderPreviewImage.isInitialized) {
            renderPreviewImage.drawable = null
        }
    }

    private fun updateHotspotSettingsPanel() {
        if (!::hotspotModeLabel.isInitialized) {
            return
        }
        val info = hotspotSelectionProvider()
        selectedHotspotId = info.selectedId
        val hasSelection = info.selectedId != null

        hotspotModeLabel.setText(
            if (hasSelection) {
                "Selected hotspot ${info.selectedName ?: info.selectedId}"
            } else {
                "Default hotspot settings"
            }
        )

        updatingHotspotFields = true
        hotspotNameField.text = info.selectedName ?: ""
        hotspotOperationSelect.selected = info.selectedOperation ?: hotspotSettings.defaultOperation
        hotspotShapeSelect.selected = info.selectedShape ?: hotspotSettings.defaultShape
        hotspotColorField.text = formatColorField(info.selectedColor ?: hotspotSettings.defaultColor)
        updateArchitectureColorButtonSwatch(
            hotspotColorButton,
            parseColorField(hotspotColorField.text) ?: hotspotSettings.defaultColor
        )
        updatingHotspotFields = false

        hotspotNameField.isDisabled = !hasSelection
        hotspotShapeSelect.isDisabled = false
        hotspotColorField.isDisabled = false
        hotspotAttachButton.isDisabled = !hasSelection
        hotspotSelectAttachedButton.isDisabled = !hasSelection
        hotspotPickReferenceButton.isDisabled = !hasSelection
        hotspotClearReferenceButton.isDisabled = !hasSelection
        hotspotDeleteButton.isDisabled = info.selectedCount <= 0
        hotspotAttachedLabel.setText("Attached: edges ${info.attachedEdgeCount} faces ${info.attachedFaceCount}")
        hotspotReferenceLabel.setText(if (info.hasReference) "Reference: set" else "Reference: none")
    }

    private fun updateTutorialUi() {
        if (!::tutorialsModeLabel.isInitialized) {
            return
        }
        val state = tutorialStateProvider()
        tutorialsModeLabel.setText(
            when (state.mode) {
                TutorialMode.RECORDING -> "Mode: Recording ${state.activeTutorialName ?: ""}".trim()
                TutorialMode.PLAYING -> "Mode: Playing ${state.activeTutorialName ?: ""}".trim()
                TutorialMode.PAUSED -> "Mode: Paused ${state.activeTutorialName ?: ""}".trim()
                TutorialMode.IDLE -> "Mode: Idle"
            }
        )
        tutorialsStepLabel.setText("Step: ${state.currentStepIndex}/${state.totalSteps}")

        if (state.tutorials != tutorialEntries) {
            tutorialEntries = state.tutorials
            needsPanelLayout = true
        }
        val availablePaths = tutorialEntries.map { it.path }.toSet()
        selectedTutorialPath = when {
            !state.activeTutorialPath.isNullOrBlank() && availablePaths.contains(state.activeTutorialPath) -> state.activeTutorialPath
            !selectedTutorialPath.isNullOrBlank() && availablePaths.contains(selectedTutorialPath) -> selectedTutorialPath
            else -> tutorialEntries.firstOrNull()?.path
        }
        rebuildTutorialListContent(state)

        tutorialsRecordButton.isDisabled = state.mode == TutorialMode.RECORDING || state.mode == TutorialMode.PLAYING || state.mode == TutorialMode.PAUSED
        tutorialsStopRecordButton.isDisabled = state.mode != TutorialMode.RECORDING
        tutorialsPlayButton.isDisabled = selectedTutorialPath.isNullOrBlank() || state.mode == TutorialMode.RECORDING
        tutorialsPauseButton.isDisabled = state.mode != TutorialMode.PLAYING && state.mode != TutorialMode.PAUSED
        tutorialsPauseButton.setText(if (state.mode == TutorialMode.PAUSED) "Resume" else "Pause")
        tutorialsStopButton.isDisabled = state.mode != TutorialMode.PLAYING && state.mode != TutorialMode.PAUSED

        updateTutorialMessageWindow(state)
    }

    private fun updateTutorialMessageWindow(state: TutorialUiState) {
        if (!::tutorialMessageWindow.isInitialized) {
            return
        }
        if (!state.messageVisible) {
            disposeTutorialPreviewTextures()
            tutorialMessageWindow.isVisible = false
            return
        }
        val stepPrefix = if (state.totalSteps > 0) "${state.currentStepIndex}/${state.totalSteps} " else ""
        tutorialMessageLabel.setText(stepPrefix + state.currentMessage.ifBlank { "Follow the current tutorial step." })
        tutorialMessageStatusLabel.setText(
            if (state.currentStepMatched) {
                "Action matched. Press Next to continue."
            } else {
                "Waiting for: ${state.expectedAction ?: "current step"}"
            }
        )
        updateTutorialPreviewTextures(state)
        tutorialPreviousButton.isDisabled = false
        tutorialNextButton.isDisabled = false
        tutorialMessageWindow.pack()
        ensureTutorialMessageWindowPosition()
        tutorialMessageWindow.isVisible = true
        tutorialMessageWindow.toFront()
    }

    private fun formatTutorialEntryLabel(entry: TutorialFileEntry, state: TutorialUiState): String {
        val suffix = when {
            state.mode == TutorialMode.RECORDING && state.activeTutorialPath == entry.path ->
                "${state.totalSteps} steps"
            (state.mode == TutorialMode.PLAYING || state.mode == TutorialMode.PAUSED) && state.activeTutorialPath == entry.path ->
                "${state.currentStepIndex}/${state.totalSteps}"
            else -> "${entry.stepCount} steps"
        }
        return truncateTutorialListItem("${entry.name} [${entry.fileName}] - $suffix")
    }

    private fun rebuildTutorialListContent(state: TutorialUiState) {
        if (!::tutorialsListContent.isInitialized) {
            return
        }
        val signature = buildTutorialListRenderSignature(state)
        if (signature == tutorialListRenderSignature) {
            return
        }
        tutorialListRenderSignature = signature
        tutorialsListContent.clearChildren()

        val rootEntries = tutorialEntries.filter { it.relativeFolder.isBlank() }
        rootEntries.forEach { entry ->
            tutorialsListContent.add(createTutorialEntryButton(entry, state, indent = 0f)).growX().row()
        }

        val grouped = tutorialEntries
            .filter { it.relativeFolder.isNotBlank() }
            .groupBy { it.relativeFolder }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        grouped.forEach { (section, entries) ->
            val collapsed = tutorialCollapsedSections[section] ?: false
            tutorialsListContent.add(createTutorialSectionHeader(section, collapsed)).growX().row()
            if (!collapsed) {
                entries.forEach { entry ->
                    tutorialsListContent.add(createTutorialEntryButton(entry, state, indent = 16f)).growX().row()
                }
            }
        }
        tutorialsListContent.invalidateHierarchy()
    }

    private fun buildTutorialListRenderSignature(state: TutorialUiState): String {
        val entriesSignature = tutorialEntries.joinToString(separator = "|") { entry ->
            buildString {
                append(entry.relativeFolder)
                append('\u0001')
                append(entry.path)
                append('\u0001')
                append(entry.name)
                append('\u0001')
                append(entry.fileName)
                append('\u0001')
                append(entry.stepCount)
            }
        }
        val sectionsSignature = tutorialCollapsedSections.toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .entries
            .joinToString(separator = "|") { (section, collapsed) -> "$section=$collapsed" }
        return listOf(
            entriesSignature,
            sectionsSignature,
            selectedTutorialPath.orEmpty(),
            state.mode.name,
            state.activeTutorialPath.orEmpty(),
            state.currentStepIndex.toString(),
            state.totalSteps.toString()
        ).joinToString("::")
    }

    private fun createTutorialEntryButton(entry: TutorialFileEntry, state: TutorialUiState, indent: Float): VisTextButton {
        val selected = entry.path == selectedTutorialPath
        return VisTextButton(formatTutorialEntryLabel(entry, state)).apply {
            label.setAlignment(Align.left)
            labelCell.expandX().fillX().left()
            isChecked = selected
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    selectedTutorialPath = entry.path
                    tutorialListRenderSignature = ""
                    if (tapCount >= 2) {
                        tutorialPlay(selectedTutorialPath)
                    }
                }
            })
            padLeft(indent)
        }
    }

    private fun createTutorialSectionHeader(section: String, collapsed: Boolean): VisTable {
        val row = VisTable()
        row.defaults().left().pad(1f)
        val toggle = TextButton(if (collapsed) "+" else "-", tutorialSectionToggleButtonStyle()).apply {
            label.setAlignment(Align.center)
            labelCell.pad(0f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    tutorialCollapsedSections[section] = !(tutorialCollapsedSections[section] ?: false)
                    tutorialListRenderSignature = ""
                    rebuildTutorialListContent(tutorialStateProvider())
                }
            })
        }
        val label = VisLabel(truncateTutorialListItem(section))
        row.add(toggle).width(label.prefHeight).height(label.prefHeight)
        row.add(label).growX().left()
        return row
    }

    private fun tutorialSectionToggleButtonStyle(): TextButton.TextButtonStyle {
        tutorialSectionToggleStyle?.let { return it }
        val base = VisTextButton(" ").style
        return TextButton.TextButtonStyle(base).apply {
            up = null
            down = null
            over = null
            checked = null
            checkedOver = null
            disabled = null
            fontColor = Color.WHITE
            downFontColor = Color.ORANGE
            overFontColor = Color.ORANGE
            checkedFontColor = Color.RED
            checkedOverFontColor = Color.RED
            disabledFontColor = Color.DARK_GRAY
        }.also { tutorialSectionToggleStyle = it }
    }

    private fun truncateTutorialListItem(text: String): String {
        if (text.length <= tutorialListItemMaxChars) {
            return text
        }
        return text.take((tutorialListItemMaxChars - 3).coerceAtLeast(1)) + "..."
    }

    private fun ensureTutorialMessageWindowPosition() {
        if (tutorialMessagePositionInitialized) {
            return
        }
        tutorialMessagePositionInitialized = true
        val savedX = if (uiPrefs.contains(tutorialMessageWindowXKey)) uiPrefs.getFloat(tutorialMessageWindowXKey) else Float.NaN
        val savedY = if (uiPrefs.contains(tutorialMessageWindowYKey)) uiPrefs.getFloat(tutorialMessageWindowYKey) else Float.NaN
        val desiredX = if (savedX.isFinite()) savedX else 8f
        val desiredY = if (savedY.isFinite()) savedY else 40f
        val maxX = (stage.width - tutorialMessageWindow.width).coerceAtLeast(0f)
        val maxY = (stage.height - tutorialMessageWindow.height).coerceAtLeast(0f)
        tutorialMessageWindow.setPosition(desiredX.coerceIn(0f, maxX), desiredY.coerceIn(0f, maxY))
    }

    private fun saveTutorialMessageWindowPosition() {
        if (!::tutorialMessageWindow.isInitialized) {
            return
        }
        uiPrefs.putFloat(tutorialMessageWindowXKey, tutorialMessageWindow.x)
        uiPrefs.putFloat(tutorialMessageWindowYKey, tutorialMessageWindow.y)
        uiPrefs.flush()
    }

    private fun ensureRenderWindowPosition() {
        if (renderWindowPositionInitialized) {
            return
        }
        renderWindowPositionInitialized = true
        val savedX = if (uiPrefs.contains(renderWindowXKey)) uiPrefs.getFloat(renderWindowXKey) else Float.NaN
        val savedY = if (uiPrefs.contains(renderWindowYKey)) uiPrefs.getFloat(renderWindowYKey) else Float.NaN
        val desiredX = if (savedX.isFinite()) savedX else ((stage.width - renderWindow.width) * 0.5f).coerceAtLeast(0f)
        val desiredY = if (savedY.isFinite()) savedY else ((stage.height - renderWindow.height) * 0.5f).coerceAtLeast(0f)
        val maxX = (stage.width - renderWindow.width).coerceAtLeast(0f)
        val maxY = (stage.height - renderWindow.height).coerceAtLeast(0f)
        renderWindow.setPosition(desiredX.coerceIn(0f, maxX), desiredY.coerceIn(0f, maxY))
    }

    private fun saveRenderWindowPosition() {
        if (!::renderWindow.isInitialized) {
            return
        }
        uiPrefs.putFloat(renderWindowXKey, renderWindow.x)
        uiPrefs.putFloat(renderWindowYKey, renderWindow.y)
        uiPrefs.flush()
    }

    fun showRenderWindow() {
        if (!::renderWindow.isInitialized) {
            return
        }
        renderWindow.pack()
        ensureRenderWindowPosition()
        renderWindow.isVisible = true
        renderWindow.toFront()
    }

    fun clearRenderPreview() {
        if (!::renderStatusLabel.isInitialized) {
            return
        }
        clearRenderPreviewReference()
        renderStatusLabel.setText("Idle")
    }

    fun setRenderPreview(texture: Texture, width: Int, height: Int) {
        if (!::renderPreviewImage.isInitialized) {
            return
        }
        if (renderPreviewTexture !== texture) {
            renderPreviewTexture = texture
            renderPreviewImage.drawable = TextureRegionDrawable(TextureRegion(texture))
        }
        val clampedWidth = width.coerceAtLeast(1).toFloat()
        val clampedHeight = height.coerceAtLeast(1).toFloat()
        renderPreviewImage.setSize(clampedWidth, clampedHeight)
        renderPreviewCell?.width(clampedWidth)?.height(clampedHeight)
        renderWindow.pack()
        ensureRenderWindowPosition()
        renderWindow.isVisible = true
        renderWindow.toFront()
    }

    fun setRenderStatus(text: String) {
        if (!::renderStatusLabel.isInitialized) {
            return
        }
        renderStatusLabel.setText(text)
    }

    fun setRenderResolutionLabel(text: String) {
        if (!::renderResolutionValueLabel.isInitialized) {
            return
        }
        renderResolutionValueLabel.setText(text)
    }

    private fun updateHvacSettingsPanel() {
        if (!::hvacPlumbingDiameterField.isInitialized) {
            return
        }
        fun updateField(field: VisTextField, text: String) {
            if (field.hasKeyboardFocus()) {
                return
            }
            if (field.text != text) {
                field.text = text
            }
        }
        updatingHvacFields = true
        updateField(hvacPlumbingDiameterField, String.format(Locale.US, "%.3f", hvacSettings.plumbingDiameter))
        updateField(hvacPlumbingSidesField, hvacSettings.plumbingSides.toString())
        updateField(hvacPlumbingColorField, formatColorField(hvacSettings.plumbingColor))
        if (hvacVentilationAutoJoinCheck.isChecked != hvacSettings.ventilationAutoJoin) {
            hvacVentilationAutoJoinCheck.isChecked = hvacSettings.ventilationAutoJoin
        }
        updateField(hvacVentilationWidthField, String.format(Locale.US, "%.3f", hvacSettings.ventilationWidth))
        updateField(hvacVentilationHeightField, String.format(Locale.US, "%.3f", hvacSettings.ventilationHeight))
        updateField(
            hvacVentilationHumpHalfSpanField,
            String.format(Locale.US, "%.3f", hvacSettings.ventilationHumpHalfSpan)
        )
        updateField(
            hvacVentilationHumpClearanceField,
            String.format(Locale.US, "%.3f", hvacSettings.ventilationHumpClearance)
        )
        updateField(hvacVentilationColorField, formatColorField(hvacSettings.ventilationColor))
        updateArchitectureColorButtonSwatch(
            hvacPlumbingColorButton,
            parseColorField(hvacPlumbingColorField.text) ?: hvacSettings.plumbingColor
        )
        updateArchitectureColorButtonSwatch(
            hvacVentilationColorButton,
            parseColorField(hvacVentilationColorField.text) ?: hvacSettings.ventilationColor
        )
        updatingHvacFields = false
    }

    private fun rebuildArchitectureSettingsContent(mode: ArchitectureElementKind?) {
        if (!::architectureWallsContent.isInitialized) {
            return
        }
        architectureWallsContent.clearChildren()
        architectureSlabsContent.clearChildren()
        architectureStairsContent.clearChildren()
        architectureFramesContent.clearChildren()

        architectureWallsContent.add(architectureModeLabel).left().colspan(2).growX().row()
        architectureWallsContent.add(architectureWallSectionLabel).left().colspan(2).growX().padTop(8f).row()
        addArchitectureFieldGrid(
            architectureWallsContent,
            listOf(
                ArchitectureFieldEntry(architectureWallNameLabel, architectureWallNameField),
                ArchitectureFieldEntry(architectureWallThicknessLabel, architectureWallThicknessField),
                ArchitectureFieldEntry(architectureWallHeightLabel, architectureWallHeightField),
                ArchitectureFieldEntry(architectureWallInclinationLabel, architectureWallInclinationField),
                ArchitectureFieldEntry(
                    architectureWallExteriorColorLabel,
                    architectureWallExteriorColorField,
                    architectureWallExteriorColorButton
                ),
                ArchitectureFieldEntry(
                    architectureWallInteriorColorLabel,
                    architectureWallInteriorColorField,
                    architectureWallInteriorColorButton
                )
            )
        )

        architectureSlabsContent.add(architectureSlabSectionLabel).left().colspan(2).growX().padTop(8f).row()
        addArchitectureFieldGrid(
            architectureSlabsContent,
            listOf(
                ArchitectureFieldEntry(architectureSlabNameLabel, architectureSlabNameField),
                ArchitectureFieldEntry(architectureSlabThicknessLabel, architectureSlabThicknessField),
                ArchitectureFieldEntry(
                    architectureSlabTopColorLabel,
                    architectureSlabTopColorField,
                    architectureSlabTopColorButton
                ),
                ArchitectureFieldEntry(
                    architectureSlabBottomColorLabel,
                    architectureSlabBottomColorField,
                    architectureSlabBottomColorButton
                ),
                ArchitectureFieldEntry(
                    architectureSlabSideColorLabel,
                    architectureSlabSideColorField,
                    architectureSlabSideColorButton
                )
            )
        )

        architectureStairsContent.add(architectureStairSectionLabel).left().colspan(2).growX().padTop(8f).row()
        addArchitectureFieldGrid(
            architectureStairsContent,
            listOf(
                ArchitectureFieldEntry(architectureStairNameLabel, architectureStairNameField),
                ArchitectureFieldEntry(architectureStairHeightLabel, architectureStairHeightField),
                ArchitectureFieldEntry(architectureStairStepsLabel, architectureStairStepsField),
                ArchitectureFieldEntry(architectureStairSupportLabel, architectureStairSupportField),
                ArchitectureFieldEntry(architectureStairLeftRailLabel, checkBox = architectureStairLeftRailCheck),
                ArchitectureFieldEntry(architectureStairRightRailLabel, checkBox = architectureStairRightRailCheck),
                ArchitectureFieldEntry(
                    architectureStairTreadColorLabel,
                    architectureStairTreadColorField,
                    architectureStairTreadColorButton
                ),
                ArchitectureFieldEntry(
                    architectureStairSupportColorLabel,
                    architectureStairSupportColorField,
                    architectureStairSupportColorButton
                )
            )
        )

        architectureFramesContent.add(architectureFrameSectionLabel).left().colspan(2).growX().padTop(8f).row()
        addArchitectureFieldGrid(
            architectureFramesContent,
            listOf(
                ArchitectureFieldEntry(architectureFrameNameLabel, architectureFrameNameField),
                ArchitectureFieldEntry(architectureFrameDepthLabel, architectureFrameDepthField),
                ArchitectureFieldEntry(architectureFrameWidthLabel, architectureFrameWidthField),
                ArchitectureFieldEntry(architectureFrameGlazingLabel, checkBox = architectureFrameGlazingCheck),
                ArchitectureFieldEntry(
                    architectureFrameColorLabel,
                    architectureFrameColorField,
                    architectureFrameColorButton
                ),
                ArchitectureFieldEntry(
                    architectureFrameGlazingColorLabel,
                    architectureFrameGlazingColorField,
                    architectureFrameGlazingColorButton
                )
            )
        )

        listOf(
            architectureWallsPanel,
            architectureSlabsPanel,
            architectureStairsPanel,
            architectureFramesPanel
        ).forEach { it.pack() }
    }

    private data class ArchitectureFieldEntry(
        val label: VisLabel,
        val field: VisTextField? = null,
        val colorButton: AppImageTextButton? = null,
        val checkBox: VisCheckBox? = null
    )

    private fun addArchitectureFieldGrid(target: VisTable, entries: List<ArchitectureFieldEntry>) {
        var index = 0
        while (index < entries.size) {
            target.add(architectureFieldCell(entries[index])).growX().top().padTop(2f)
            if (index + 1 < entries.size) {
                target.add(architectureFieldCell(entries[index + 1])).growX().top().padTop(2f)
            } else {
                target.add().growX()
            }
            target.row()
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
        if (!::architectureWallsContent.isInitialized) {
            return
        }
        val selection = architectureElementProvider()
        val summary = architectureSelectionSummaryProvider()
        architectureModeLabel.setText(
            if (selection == null) {
                "Default construction settings"
            } else {
                "Selected ${selection.kind.name.lowercase()} [${selection.id.take(8)}]"
            }
        )
        architectureWallSectionLabel.setText("Walls (${summary.selectedWallCount} selected)")
        architectureSlabSectionLabel.setText("Slabs (${summary.selectedSlabCount} selected)")
        architectureStairSectionLabel.setText("Stairs (${summary.selectedStairCount} selected)")
        architectureFrameSectionLabel.setText("Frames (${summary.selectedFrameCount} selected)")

        fun updateField(field: VisTextField, text: String) {
            if (field.hasKeyboardFocus()) {
                return
            }
            if (field.text != text) {
                field.text = text
            }
        }

        fun updateNameField(field: VisTextField, count: Int, value: String?) {
            when {
                count <= 0 -> {
                    field.isDisabled = true
                    updateField(field, "")
                }
                count == 1 -> {
                    field.isDisabled = false
                    updateField(field, value.orEmpty())
                }
                else -> {
                    field.isDisabled = true
                    updateField(field, "<multiple selected>")
                }
            }
        }

        updatingArchitectureFields = true
        updateNameField(architectureWallNameField, summary.selectedWallCount, summary.singleWallName)
        updateNameField(architectureSlabNameField, summary.selectedSlabCount, summary.singleSlabName)
        updateNameField(architectureStairNameField, summary.selectedStairCount, summary.singleStairName)
        updateNameField(architectureFrameNameField, summary.selectedFrameCount, summary.singleFrameName)
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
            architectureFrameGlazingCheck.isChecked = architectureSettings.frameGlazingEnabled
            updateField(architectureFrameGlazingColorField, formatColorField(architectureSettings.frameGlazingColor))
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
                    architectureFrameGlazingCheck.isChecked = selection.frameGlazingEnabled ?: architectureSettings.frameGlazingEnabled
                    updateField(
                        architectureFrameGlazingColorField,
                        formatColorField(selection.frameGlazingColor ?: architectureSettings.frameGlazingColor)
                    )
                }
            }
        }
        syncArchitectureColorButtonSwatches()
        updatingArchitectureFields = false
    }

    private fun createArchitectureColorButton(title: String, field: VisTextField): AppImageTextButton {
        val icon = iconFor("color", createActionIconDrawable(Color(0.8f, 0.8f, 0.8f, 1f)))
        return AppImageTextButton("", icon).apply {
            applyWhiteButtonStyle(this)
            applyIconStyle(this, icon)
            registerTutorialActionTarget("ui.action.color.${normalizeTutorialActionKey(title)}", this)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    tutorialUiActionObserved("ui.action.color.${normalizeTutorialActionKey(title)}", title)
                    val current = parseColorField(field.text) ?: Color.WHITE
                    showArchitectureColorPicker(title, current) { picked ->
                        applyArchitectureColorPickerValue(field, this@apply, picked)
                    }
                }
            })
        }
    }

    private fun applyArchitectureColorPickerValue(
        field: VisTextField,
        button: AppImageTextButton,
        color: Color
    ) {
        val value = formatColorField(color)
        updatingArchitectureFields = true
        field.text = value
        updateArchitectureColorButtonSwatch(button, color)
        updatingArchitectureFields = false

        val selection = architectureElementProvider()
        when (field) {
            architectureWallExteriorColorField -> {
                if (selection == null) {
                    architectureSettings.wallExteriorColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.WALL) {
                    architectureWallChanged(
                        selection.id,
                        selection.wallThickness ?: architectureSettings.wallThickness,
                        selection.wallHeight ?: architectureSettings.wallHeight,
                        selection.wallInclinationDeg ?: architectureSettings.wallInclinationDeg,
                        Color(color),
                        selection.wallInteriorColor ?: Color(architectureSettings.wallInteriorColor)
                    )
                }
            }
            architectureWallInteriorColorField -> {
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
                        Color(color)
                    )
                }
            }
            architectureSlabTopColorField -> {
                if (selection == null) {
                    architectureSettings.slabTopColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.SLAB) {
                    architectureSlabChanged(
                        selection.id,
                        selection.slabThickness ?: architectureSettings.slabThickness,
                        Color(color),
                        selection.slabBottomColor ?: Color(architectureSettings.slabBottomColor),
                        selection.slabSideColor ?: Color(architectureSettings.slabSideColor)
                    )
                }
            }
            architectureSlabBottomColorField -> {
                if (selection == null) {
                    architectureSettings.slabBottomColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.SLAB) {
                    architectureSlabChanged(
                        selection.id,
                        selection.slabThickness ?: architectureSettings.slabThickness,
                        selection.slabTopColor ?: Color(architectureSettings.slabTopColor),
                        Color(color),
                        selection.slabSideColor ?: Color(architectureSettings.slabSideColor)
                    )
                }
            }
            architectureSlabSideColorField -> {
                if (selection == null) {
                    architectureSettings.slabSideColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.SLAB) {
                    architectureSlabChanged(
                        selection.id,
                        selection.slabThickness ?: architectureSettings.slabThickness,
                        selection.slabTopColor ?: Color(architectureSettings.slabTopColor),
                        selection.slabBottomColor ?: Color(architectureSettings.slabBottomColor),
                        Color(color)
                    )
                }
            }
            architectureStairTreadColorField -> {
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
                        Color(color),
                        selection.stairSupportColor ?: Color(architectureSettings.stairSupportColor)
                    )
                }
            }
            architectureStairSupportColorField -> {
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
                        Color(color)
                    )
                }
            }
            architectureFrameColorField -> {
                if (selection == null) {
                    architectureSettings.frameColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.FRAME) {
                    architectureFrameChanged(
                        selection.id,
                        selection.frameDepth ?: architectureSettings.frameDepth,
                        selection.frameWidth ?: architectureSettings.frameWidth,
                        Color(color),
                        selection.frameGlazingEnabled ?: architectureSettings.frameGlazingEnabled,
                        selection.frameGlazingColor ?: Color(architectureSettings.frameGlazingColor)
                    )
                }
            }
            architectureFrameGlazingColorField -> {
                if (selection == null) {
                    architectureSettings.frameGlazingColor.set(color)
                    saveArchitectureDefaults()
                } else if (selection.kind == ArchitectureElementKind.FRAME) {
                    architectureFrameChanged(
                        selection.id,
                        selection.frameDepth ?: architectureSettings.frameDepth,
                        selection.frameWidth ?: architectureSettings.frameWidth,
                        selection.frameColor ?: Color(architectureSettings.frameColor),
                        selection.frameGlazingEnabled ?: architectureSettings.frameGlazingEnabled,
                        Color(color)
                    )
                }
            }
        }
    }

    private fun showArchitectureColorPicker(title: String, start: Color, onApply: (Color) -> Unit) {
        val picker = ColorPicker(title)
        picker.color = Color(start)
        picker.setListener(object : ColorPickerListener {
            override fun changed(color: Color?) {
                // Commit only on OK to avoid writing to unrelated fields while browsing colors.
            }

            override fun canceled(oldColor: Color?) {
                picker.remove()
            }

            override fun reset(oldColor: Color?, newColor: Color?) {
                // No-op; final value is committed from finished().
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

    private fun updateArchitectureColorButtonSwatch(button: AppImageTextButton, color: Color) {
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
        updateArchitectureColorButtonSwatch(
            architectureFrameGlazingColorButton,
            parseColorField(architectureFrameGlazingColorField.text) ?: architectureSettings.frameGlazingColor
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
        if (automationHidePanels) {
            groupPanel.isVisible = false
            groupNameField.isDisabled = true
            groupGlueCheck.isDisabled = true
            return
        }
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
        val circleSegments = circleSegmentsProvider()
        val walk = walkthroughTuningProvider()
        val selection = selectionInfoProvider()
        modelHotspotsLabel.setText("Selected hotspots: ${selection.hotspotCount}")
        val unitName = unit.name
        val unitSize = unit.size
        if (
            unitName != lastUnitName || unitSize != lastUnitSize || gridSpacing != lastGridSpacing || circleSegments != lastCircleSegments || snapEpsilon != lastSnapEpsilon ||
            walk.moveSpeed != lastWalkMoveSpeed ||
            walk.jumpVelocity != lastWalkJump || walk.gravity != lastWalkGravity || walk.heightAdjustSpeed != lastWalkHeightAdjust
        ) {
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
            val circleSegmentsText = circleSegments.toString()
            if (circleSegmentsText != circleSegmentsField.text || !circleSegmentsField.hasKeyboardFocus()) {
                circleSegmentsField.text = circleSegmentsText
            }
            val walkMoveText = String.format(Locale.US, "%.3f", walk.moveSpeed)
            if (walkMoveText != walkthroughMoveSpeedField.text || !walkthroughMoveSpeedField.hasKeyboardFocus()) {
                walkthroughMoveSpeedField.text = walkMoveText
            }
            val walkJumpText = String.format(Locale.US, "%.3f", walk.jumpVelocity)
            if (walkJumpText != walkthroughJumpField.text || !walkthroughJumpField.hasKeyboardFocus()) {
                walkthroughJumpField.text = walkJumpText
            }
            val walkGravityText = String.format(Locale.US, "%.3f", walk.gravity)
            if (walkGravityText != walkthroughGravityField.text || !walkthroughGravityField.hasKeyboardFocus()) {
                walkthroughGravityField.text = walkGravityText
            }
            val walkHeightText = String.format(Locale.US, "%.3f", walk.heightAdjustSpeed)
            if (walkHeightText != walkthroughHeightAdjustField.text || !walkthroughHeightAdjustField.hasKeyboardFocus()) {
                walkthroughHeightAdjustField.text = walkHeightText
            }
            snapEpsilonSlider.value = snapEpsilon.coerceIn(snapEpsilonMin, snapEpsilonMax)
            updatingModelSettingsFields = false
            lastUnitName = unitName
            lastUnitSize = unitSize
            lastGridSpacing = gridSpacing
            lastCircleSegments = circleSegments
            lastSnapEpsilon = snapEpsilon
            lastWalkMoveSpeed = walk.moveSpeed
            lastWalkJump = walk.jumpVelocity
            lastWalkGravity = walk.gravity
            lastWalkHeightAdjust = walk.heightAdjustSpeed
        }
    }

    private fun applyWalkthroughTuningFromFields() {
        if (updatingModelSettingsFields) {
            return
        }
        val moveSpeed = walkthroughMoveSpeedField.text.toFloatOrNull() ?: return
        val jump = walkthroughJumpField.text.toFloatOrNull() ?: return
        val gravity = walkthroughGravityField.text.toFloatOrNull() ?: return
        val adjust = walkthroughHeightAdjustField.text.toFloatOrNull() ?: return
        if (moveSpeed <= 0f || jump <= 0f || gravity <= 0f || adjust <= 0f) {
            return
        }
        walkthroughTuningChanged(
            WalkthroughTuning(
                moveSpeed = moveSpeed,
                jumpVelocity = jump,
                gravity = gravity,
                heightAdjustSpeed = adjust
            )
        )
    }

    private fun selectedObjectPrototype(): ObjectPrototypeInfo? {
        val index = objectsList.selectedIndex
        if (index < 0 || index >= objectPrototypeItems.size) {
            return null
        }
        return objectPrototypeItems[index]
    }

    private fun loadUiVisualSettings() {
        val saved = uiPrefs.getInteger(uiToolbarButtonSizeKey, 32)
        val normalized = when (saved) {
            48, 64 -> saved
            else -> 32
        }
        toolbarAutoCollapse = uiPrefs.getBoolean(uiToolbarAutoCollapseKey, false)
        uiTextScale = when (uiPrefs.getString(uiTextScaleKey, "1x")) {
            "1.5x" -> 1.5f
            "2x" -> 2f
            else -> 1f
        }
        normalLineWidth = uiPrefs.getFloat(uiNormalLineWidthKey, 1f).coerceIn(1f, 8f)
        thickLineWidth = uiPrefs.getFloat(uiThickLineWidthKey, 3f).coerceIn(1f, 16f)
        toolbarIconSizePx = normalized
        toolbarButtonSize = normalized.toFloat()
    }

    private fun syncUiSettingsPanel() {
        if (
            !::uiToolbarSizeSelect.isInitialized ||
            !::uiTextScaleSelect.isInitialized ||
            !::uiToolbarAutoCollapseCheck.isInitialized ||
            !::uiNormalLineWidthSlider.isInitialized ||
            !::uiNormalLineWidthValueLabel.isInitialized ||
            !::uiThickLineWidthSlider.isInitialized ||
            !::uiThickLineWidthValueLabel.isInitialized
        ) return
        updatingUiSettingsFields = true
        uiTextScaleSelect.selected = when (uiTextScale) {
            1.5f -> "1.5x"
            2f -> "2x"
            else -> "1x"
        }
        uiToolbarSizeSelect.selected = when (toolbarIconSizePx) {
            48 -> "48 x 48 px"
            64 -> "64 x 64 px"
            else -> "32 x 32 px"
        }
        uiToolbarAutoCollapseCheck.isChecked = toolbarAutoCollapse
        uiNormalLineWidthSlider.value = normalLineWidth
        uiNormalLineWidthValueLabel.setText(String.format(Locale.US, "%.1f px", normalLineWidth))
        uiThickLineWidthSlider.value = thickLineWidth
        uiThickLineWidthValueLabel.setText(String.format(Locale.US, "%.1f px", thickLineWidth))
        updatingUiSettingsFields = false
    }

    private fun setUiTextScale(scale: Float) {
        val normalized = when {
            scale >= 1.75f -> 2f
            scale >= 1.25f -> 1.5f
            else -> 1f
        }
        if (normalized == uiTextScale) {
            syncUiSettingsPanel()
            return
        }
        uiTextScale = normalized
        uiPrefs.putString(
            uiTextScaleKey,
            when (normalized) {
                1.5f -> "1.5x"
                2f -> "2x"
                else -> "1x"
            }
        )
        uiPrefs.flush()
        applyUiTextScaleToSkin()
        syncUiSettingsPanel()
        refreshUiForTextScaleChange()
    }

    private fun applyUiTextScaleToSkin() {
        val skin = runCatching { VisUI.getSkin() }.getOrNull() ?: return
        val fonts = skin.getAll(BitmapFont::class.java)
        fonts.values().forEach { font ->
            val base = uiBaseFontScales.getOrPut(font) {
                font.data.scaleX to font.data.scaleY
            }
            font.data.setScale(base.first * uiTextScale, base.second * uiTextScale)
        }
    }

    private fun setToolbarIconAndButtonSize(sizePx: Int) {
        val normalized = when (sizePx) {
            48, 64 -> sizePx
            else -> 32
        }
        if (normalized == toolbarIconSizePx) {
            syncUiSettingsPanel()
            return
        }
        toolbarIconSizePx = normalized
        toolbarButtonSize = normalized.toFloat()
        uiPrefs.putInteger(uiToolbarButtonSizeKey, normalized)
        uiPrefs.flush()
        syncUiSettingsPanel()
        rebuildToolbarsForUiScaleChange()
    }

    private fun setToolbarAutoCollapse(enabled: Boolean) {
        if (toolbarAutoCollapse == enabled) {
            syncUiSettingsPanel()
            return
        }
        toolbarAutoCollapse = enabled
        uiPrefs.putBoolean(uiToolbarAutoCollapseKey, enabled)
        uiPrefs.flush()
        syncUiSettingsPanel()
        refreshToolbarAutoCollapseStates(force = true)
    }

    private fun setNormalLineWidth(width: Float) {
        val normalized = width.coerceIn(1f, 8f)
        if (abs(normalLineWidth - normalized) <= 0.01f) {
            syncUiSettingsPanel()
            return
        }
        normalLineWidth = normalized
        uiPrefs.putFloat(uiNormalLineWidthKey, normalized)
        uiPrefs.flush()
        normalLineWidthChanged(normalized)
        syncUiSettingsPanel()
    }

    private fun setThickLineWidth(width: Float) {
        val normalized = width.coerceIn(1f, 16f)
        if (abs(thickLineWidth - normalized) <= 0.01f) {
            syncUiSettingsPanel()
            return
        }
        thickLineWidth = normalized
        uiPrefs.putFloat(uiThickLineWidthKey, normalized)
        uiPrefs.flush()
        feedbackLineWidthChanged(normalized)
        syncUiSettingsPanel()
    }

    private fun rebuildToolbarsForUiScaleChange() {
        hideHoverPopover()
        hoverPopoverTarget = null
        hoverPopoverText = ""
        hoverPopoverElapsed = 0f
        hoveredButtons.clear()
        buttonLabels.clear()
        buttonMarkers.clear()
        toolButtons.clear()
        toolButtonByWidget.clear()
        pluginToolButtons.clear()
        pluginToolByWidget.clear()
        toolbarBindingsById.clear()
        toolbarBindingsByWindow.clear()

        builtInToolbars.values.forEach { it.remove() }
        builtInToolbars.clear()
        pluginToolbars.values.forEach { it.remove() }
        pluginToolbars.clear()

        iconDrawables.clear()
        iconDrawables.putAll(loadIconDrawables())

        buildStandardToolbars().forEach { stage.addActor(it) }
        lastPluginTools = emptyList()
        refreshPluginToolbar()
        refreshUiForTextScaleChange()
    }

    private fun invalidateUiActorTree(actor: Actor) {
        (actor as? Layout)?.invalidateHierarchy()
        if (actor is Group) {
            actor.children.forEach { child ->
                invalidateUiActorTree(child)
            }
        }
    }

    private fun validateUiActorTree(actor: Actor) {
        (actor as? Layout)?.validate()
        if (actor is Group) {
            actor.children.forEach { child ->
                validateUiActorTree(child)
            }
        }
    }

    private fun rebuildRightSidePanelForUiScaleChange() {
        if (!::rightSidePanel.isInitialized) {
            return
        }
        val wasVisible = rightSidePanel.isVisible
        val previousX = rightSidePanel.x
        val previousY = rightSidePanel.y
        val previousWidth = rightSidePanel.width
        val previousHeight = rightSidePanel.height
        val previousScrollY = if (::rightSidePanelScroll.isInitialized) rightSidePanelScroll.scrollY else 0f
        rightSidePanel.remove()
        rightSidePanel = buildRightSidePanel()
        rightSidePanel.isVisible = wasVisible
        if (previousWidth > 0f && previousHeight > 0f) {
            rightSidePanel.setSize(previousWidth, previousHeight)
        }
        rightSidePanel.setPosition(previousX, previousY)
        stage.addActor(rightSidePanel)
        if (::rightSidePanelScroll.isInitialized && previousScrollY > 0f) {
            rightSidePanelScroll.scrollY = previousScrollY
            rightSidePanelScroll.updateVisualScroll()
        }
    }

    private fun refreshUiForTextScaleChange() {
        tutorialSectionToggleStyle = null
        rightDockStableMinContentWidth = 0f
        rebuildRightSidePanelForUiScaleChange()
        invalidateUiActorTree(stage.root)
        rightDockPanels().forEach {
            it.invalidateHierarchy()
            it.pack()
        }
        builtInToolbars.values.forEach {
            it.invalidateHierarchy()
            it.pack()
        }
        pluginToolbars.values.forEach {
            it.invalidateHierarchy()
            it.pack()
        }
        pluginPanels.values.forEach {
            it.invalidateHierarchy()
            it.pack()
        }
        if (::rightSidePanelContent.isInitialized) {
            rightSidePanelContent.invalidateHierarchy()
        }
        if (::rightSidePanelScroll.isInitialized) {
            rightSidePanelScroll.invalidateHierarchy()
            rightSidePanelScroll.layout()
        }
        if (::rightSidePanel.isInitialized) {
            rightSidePanel.invalidateHierarchy()
            rightSidePanel.pack()
        }
        if (::tutorialMessageWindow.isInitialized) {
            tutorialMessageWindow.invalidateHierarchy()
            tutorialMessageWindow.pack()
        }
        distancePopup?.invalidateHierarchy()
        distancePopup?.pack()
        hoverPopoverWindow?.invalidateHierarchy()
        hoverPopoverWindow?.pack()
        refreshToolbarAutoCollapseStates(force = true)
        toolbarsPositioned = false
        pluginPanelsPositioned = false
        needsPanelLayout = true
        positionPanels()
        validateUiActorTree(stage.root)
    }

    fun toggleObjectsPanel() {
        objectsPanel.isVisible = !objectsPanel.isVisible
        if (objectsPanel.isVisible) {
            objectsPanel.setCollapsedState(false)
        }
        needsPanelLayout = true
    }


    private fun buildLightingPanel(): DockSection {
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
        val panel = buildDockSection("Shader Settings", content, visible = false, collapsed = true)
        lightingPanel = panel
        return panel
    }

    private fun rightDockPanels(): List<DockSection> {
        val panels = mutableListOf(
            selectionPanel,
            groupPanel,
            objectsPanel,
            modelSettingsPanel,
            uiSettingsPanel,
            helpPanel,
            polylineSettingsPanel,
            vectorTextSettingsPanel,
            architectureWallsPanel,
            architectureSlabsPanel,
            architectureStairsPanel,
            architectureFramesPanel,
            hvacSettingsPanel,
            hotspotSettingsPanel,
            tutorialsPanel
        )
        lightingPanel?.let { panels.add(it) }
        return panels
    }

    private fun buildRightSidePanel(): CollapsibleWindow {
        val panel = CollapsibleWindow("Panels", showCloseButton = false)
        panel.isResizable = false
        rightSidePanelContent = DockStackGroup()

        rightDockPanels().forEach { child ->
            child.isVisible = true
            rightSidePanelContent.addActor(child)
        }

        rightSidePanelScroll = VisScrollPane(rightSidePanelContent).apply {
            setFadeScrollBars(false)
            setScrollingDisabled(true, false)
        }
        rightSidePanelScrollCell = panel.add(rightSidePanelScroll).grow().pad(4f)
        panel.pack()
        return panel
    }

    private fun updateRightSidePanelLayout() {
        if (!::rightSidePanel.isInitialized) {
            return
        }
        val panels = rightDockPanels()
        var maxChildWidth = 320f
        val panelGap = 6f
        panels.forEach { panel ->
            panel.invalidateHierarchy()
            panel.pack()
            if (panel.isVisible) {
                maxChildWidth = kotlin.math.max(maxChildWidth, panel.prefWidth)
            }
        }
        rightDockStableMinContentWidth = kotlin.math.max(rightDockStableMinContentWidth, maxChildWidth)
        maxChildWidth = kotlin.math.max(maxChildWidth, rightDockStableMinContentWidth)

        val stageWidth = if (stage.viewport.screenWidth > 0) stage.viewport.screenWidth.toFloat() else Gdx.graphics.width.toFloat()
        val stageHeight = if (stage.viewport.screenHeight > 0) stage.viewport.screenHeight.toFloat() else Gdx.graphics.height.toFloat()
        val outerMargin = 8f
        val scrollWidth = kotlin.math.min(maxChildWidth + 18f, stageWidth - 2f * outerMargin)
            .coerceAtLeast(240f)
        val titleHeight = rightSidePanel.getTitleTable().prefHeight
        val scrollHeight = (stageHeight - 2f * outerMargin - titleHeight - 12f).coerceAtLeast(120f)
        val initialInnerPanelWidth = (scrollWidth - 10f).coerceAtLeast(220f)

        fun layoutDockChildrenAtWidth(targetWidth: Float): Float {
            var totalHeight = 0f
            panels.forEach { panel ->
                if (!panel.isVisible) {
                    return@forEach
                }
                panel.width = targetWidth
                panel.invalidateHierarchy()
                panel.validate()
                val computedHeight = panel.prefHeight.coerceAtLeast(24f)
                panel.setSize(targetWidth, computedHeight)
                panel.validate()
                totalHeight += panel.height + panelGap
            }
            if (totalHeight > 0f) totalHeight -= panelGap
            rightSidePanelContent.setPreferredContentSize(targetWidth, totalHeight.coerceAtLeast(1f))
            return totalHeight
        }

        var contentHeight = layoutDockChildrenAtWidth(initialInnerPanelWidth)

        rightSidePanelScrollCell.width(scrollWidth).height(scrollHeight)
        rightSidePanelContent.invalidateHierarchy()
        rightSidePanelScroll.invalidateHierarchy()
        rightSidePanel.invalidateHierarchy()
        rightSidePanel.pack()
        rightSidePanelScroll.validate()
        rightSidePanel.validate()

        // When vertical scrolling kicks in, ScrollPane narrows the content viewport (due to scrollbar).
        // Recompute child heights at the actual content width to keep wrapping/pref-heights consistent.
        val actualContentWidth = rightSidePanelContent.width.coerceAtLeast(220f)
        if (kotlin.math.abs(actualContentWidth - initialInnerPanelWidth) > 0.5f) {
            contentHeight = layoutDockChildrenAtWidth(actualContentWidth)
            rightSidePanelContent.invalidateHierarchy()
            rightSidePanelScroll.invalidateHierarchy()
            rightSidePanel.invalidateHierarchy()
            rightSidePanel.pack()
            rightSidePanelScroll.validate()
            rightSidePanel.validate()
        }

        // ScrollPane may stretch the content actor to the viewport height when content is shorter.
        // Anchor children to the top of the actual content actor height to avoid bottom stacking.
        var y = rightSidePanelContent.height
        panels.forEach { panel ->
            if (!panel.isVisible) {
                return@forEach
            }
            y -= panel.height
            panel.setPosition(0f, y)
            y -= panelGap
        }
    }

    private fun positionPanels() {
        updateRightSidePanelLayout()
        val panels = mutableListOf<CollapsibleWindow>()
        if (!automationHidePanels && dockPanelsVisible) {
            rightDockPanels().forEach { it.isVisible = true }
            rightSidePanel.isVisible = true
        }
        panels.forEach {
            it.invalidateHierarchy()
            it.pack()
            it.toFront()
        }
        if (rightSidePanel.isVisible) {
            rightSidePanel.invalidateHierarchy()
            rightSidePanel.pack()
            val width = if (stage.viewport.screenWidth > 0) stage.viewport.screenWidth.toFloat() else Gdx.graphics.width.toFloat()
            val height = if (stage.viewport.screenHeight > 0) stage.viewport.screenHeight.toFloat() else Gdx.graphics.height.toFloat()
            rightSidePanel.setPosition(width - 8f - rightSidePanel.width, height - 8f - rightSidePanel.height)
            rightSidePanel.toFront()
        }
        val dockWidth = if (rightSidePanel.isVisible) rightSidePanel.width + 14f else 8f
        positionPanelStack(panels, dockWidth, 8f, 6f)
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
        if (automationHidePanels) {
            pluginManagerPanel?.isVisible = false
            commandPaletteUI?.hide()
            pluginPanels.values.forEach { panel ->
                panel.isVisible = false
            }
            return
        }
        val width = if (stage.viewport.screenWidth > 0) stage.viewport.screenWidth.toFloat() else Gdx.graphics.width.toFloat()
        pluginManagerPanel?.let { panel ->
            panel.pack()
            panel.setPosition(width - panel.width - 12f, 12f)
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
        val margin = toolbarLayoutMargin
        val gap = toolbarLayoutGap
        val rightLimit = availableToolbarRightEdge(width, margin)
        val unsavedEntries = mutableListOf<Pair<String, CollapsibleWindow>>()

        orderedToolbarEntries().forEach { (toolbarId, window) ->
            if (!window.isVisible) {
                return@forEach
            }
            window.invalidateHierarchy()
            window.pack()
            val state = toolbarId?.let { readToolbarState(it) }
            val titleHeight = window.getTitleTable().prefHeight
            val targetWidth = if (window.isResizable) (state?.width ?: window.prefWidth) else window.prefWidth
            val targetHeight = if (window.isResizable) (state?.height ?: window.prefHeight) else window.prefHeight
            window.setSize(
                max(64f, targetWidth),
                max(titleHeight, targetHeight)
            )
            if (state?.hasPosition() == true) {
                val restoredX = state.x ?: margin
                val restoredTopY = state.y ?: margin
                val restoredBottomY = height - restoredTopY - window.height
                window.setPosition(restoredX, restoredBottomY)
                clampToolbarWindowToViewport(window, width, height, margin)
            } else {
                unsavedEntries += toolbarId to window
            }
            window.toFront()
            toolbarId?.let { saveToolbarState(it, window) }
        }

        var yTop = height - margin
        horizontalToolbarRows(
            entries = visibleToolbarEntriesSortedByWidthDescending()
                .filter { (toolbarId, _) -> unsavedEntries.any { it.first == toolbarId } },
            usableWidth = (rightLimit - margin).coerceAtLeast(1f),
            gap = gap
        ).forEach { row ->
            var x = margin
            var rowHeight = 0f
            row.entries.forEach { (toolbarId, window) ->
                window.setPosition(x, yTop - window.height)
                clampToolbarWindowToViewport(window, width, height, margin)
                window.toFront()
                saveToolbarState(toolbarId, window)
                x += window.width + gap
                rowHeight = kotlin.math.max(rowHeight, window.height)
            }
            yTop -= rowHeight + gap
        }
    }

    private fun orderedToolbarEntries(): List<Pair<String, CollapsibleWindow>> {
        val toolbarEntries = mutableListOf<Pair<String, CollapsibleWindow>>()
        orderedBuiltInToolbarIds.forEach { id ->
            builtInToolbars[id]?.let { toolbarEntries.add(id to it) }
        }
        pluginToolbars.entries
            .sortedBy { it.key }
            .forEach { (pluginId, window) ->
                toolbarEntries.add(pluginToolbarStateId(pluginId) to window)
        }
        return toolbarEntries
    }

    private data class ToolbarFlowRow(
        val entries: MutableList<Pair<String, CollapsibleWindow>> = mutableListOf(),
        var width: Float = 0f
    )

    private fun availableToolbarRightEdge(viewportWidth: Float, margin: Float): Float {
        return if (::rightSidePanel.isInitialized && rightSidePanel.isVisible) {
            (rightSidePanel.x - margin).coerceAtLeast(margin)
        } else {
            viewportWidth - margin
        }
    }

    private fun visibleToolbarEntries(): List<Pair<String, CollapsibleWindow>> {
        return orderedToolbarEntries().mapNotNull { (toolbarId, window) ->
            if (!window.isVisible) {
                null
            } else {
                window.invalidateHierarchy()
                window.pack()
                toolbarId to window
            }
        }
    }

    private fun visibleToolbarEntriesSortedByWidthDescending(): List<Pair<String, CollapsibleWindow>> {
        return visibleToolbarEntries()
            .sortedWith(
                compareByDescending<Pair<String, CollapsibleWindow>> { (_, window) -> window.width }
                    .thenBy { (toolbarId, _) -> toolbarId }
            )
    }

    private fun horizontalToolbarRows(
        entries: List<Pair<String, CollapsibleWindow>>,
        usableWidth: Float,
        gap: Float
    ): List<ToolbarFlowRow> {
        val rows = mutableListOf<ToolbarFlowRow>()
        entries.forEach { entry ->
            val entryWidth = entry.second.width
            val targetRow = rows.firstOrNull { row ->
                val nextWidth = if (row.entries.isEmpty()) entryWidth else row.width + gap + entryWidth
                nextWidth <= usableWidth
            }
            if (targetRow != null) {
                targetRow.entries += entry
                targetRow.width = if (targetRow.entries.size == 1) entryWidth else targetRow.width + gap + entryWidth
            } else {
                rows += ToolbarFlowRow(mutableListOf(entry), entryWidth)
            }
        }
        return rows
    }

    private fun arrangeToolbarsHorizontalFlow() {
        val width = if (stage.viewport.screenWidth > 0) stage.viewport.screenWidth.toFloat() else Gdx.graphics.width.toFloat()
        val height = if (stage.viewport.screenHeight > 0) stage.viewport.screenHeight.toFloat() else Gdx.graphics.height.toFloat()
        val margin = toolbarLayoutMargin
        val gap = toolbarLayoutGap
        val rightLimit = availableToolbarRightEdge(width, margin)
        val usableWidth = (rightLimit - margin).coerceAtLeast(1f)
        var yTop = height - margin
        horizontalToolbarRows(visibleToolbarEntriesSortedByWidthDescending(), usableWidth, gap).forEach { row ->
            var x = margin
            var rowHeight = 0f
            row.entries.forEach { (toolbarId, window) ->
                window.setPosition(x, yTop - window.height)
                clampToolbarWindowToViewport(window, width, height, margin)
                window.toFront()
                saveToolbarState(toolbarId, window)
                x += window.width + gap
                rowHeight = max(rowHeight, window.height)
            }
            yTop -= rowHeight + gap
        }
        toolbarsPositioned = true
    }

    private fun arrangeToolbarsVerticalFlow() {
        val width = if (stage.viewport.screenWidth > 0) stage.viewport.screenWidth.toFloat() else Gdx.graphics.width.toFloat()
        val height = if (stage.viewport.screenHeight > 0) stage.viewport.screenHeight.toFloat() else Gdx.graphics.height.toFloat()
        val margin = toolbarLayoutMargin
        val gap = toolbarLayoutGap
        var x = margin
        var yTop = height - margin
        var columnWidth = 0f
        visibleToolbarEntriesSortedByWidthDescending().forEach { (toolbarId, window) ->
            if (yTop < height - margin && yTop - window.height < margin) {
                x += columnWidth + gap
                yTop = height - margin
                columnWidth = 0f
            }
            window.setPosition(x, yTop - window.height)
            clampToolbarWindowToViewport(window, width, height, margin)
            window.toFront()
            saveToolbarState(toolbarId, window)
            yTop -= window.height + gap
            columnWidth = max(columnWidth, window.width)
        }
        toolbarsPositioned = true
    }

    private fun migrateBuiltinToolbarPrefs() {
        val current = uiPrefs.getInteger(toolbarLayoutVersionKey, 0)
        if (current == toolbarLayoutVersion) {
            return
        }
        uiPrefs.get().keys
            .filter { key ->
                (key.startsWith("builtin_toolbar_") || key.startsWith("plugin_toolbar_")) &&
                    (key.endsWith(".x") || key.endsWith(".y"))
            }
            .forEach { key ->
                uiPrefs.remove(key)
            }
        listOf("builtin_toolbar_construction.x", "builtin_toolbar_construction.y").forEach { key ->
            uiPrefs.remove(key)
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
                saveToolbarState(toolbarId, window)
            }
        })
    }

    private fun pluginToolbarStateId(pluginId: String): String {
        return "plugin_toolbar_${normalizeTutorialActionKey(pluginId)}"
    }

    private fun readToolbarState(toolbarId: String): ToolbarLayoutState {
        val xKey = "$toolbarId.x"
        val yKey = "$toolbarId.y"
        val wKey = "$toolbarId.w"
        val hKey = "$toolbarId.h"
        val visibleKey = "$toolbarId.visible"
        return ToolbarLayoutState(
            x = if (uiPrefs.contains(xKey)) uiPrefs.getFloat(xKey) else null,
            y = if (uiPrefs.contains(yKey)) uiPrefs.getFloat(yKey) else null,
            width = if (uiPrefs.contains(wKey)) uiPrefs.getFloat(wKey) else null,
            height = if (uiPrefs.contains(hKey)) uiPrefs.getFloat(hKey) else null,
            visible = if (uiPrefs.contains(visibleKey)) uiPrefs.getBoolean(visibleKey) else null
        )
    }

    private fun applyToolbarState(toolbarId: String, window: CollapsibleWindow, defaultVisible: Boolean = true) {
        val state = readToolbarState(toolbarId)
        val desiredVisible = state.visible ?: defaultVisible
        toolbarDesiredVisibility[toolbarId] = desiredVisible
        window.invalidateHierarchy()
        window.pack()
        val titleHeight = window.getTitleTable().prefHeight
        val targetWidth = if (window.isResizable) (state.width ?: window.prefWidth) else window.prefWidth
        val targetHeight = if (window.isResizable) (state.height ?: window.prefHeight) else window.prefHeight
        window.setSize(
            max(64f, targetWidth),
            max(titleHeight, targetHeight)
        )
        window.isVisible = toolbarsVisible && desiredVisible
    }

    private fun applyToolbarsVisibility() {
        builtInToolbars.forEach { (toolbarId, window) ->
            window.isVisible = toolbarsVisible && (toolbarDesiredVisibility[toolbarId] ?: true)
        }
        pluginToolbars.forEach { (pluginId, window) ->
            val toolbarId = pluginToolbarStateId(pluginId)
            window.isVisible = toolbarsVisible && (toolbarDesiredVisibility[toolbarId] ?: true)
        }
        refreshToolbarAutoCollapseStates(force = true)
    }

    private fun clampToolbarWindowToViewport(
        window: CollapsibleWindow,
        viewportWidth: Float,
        viewportHeight: Float,
        margin: Float
    ) {
        val maxX = (viewportWidth - margin - window.width).coerceAtLeast(margin)
        val maxY = (viewportHeight - margin - window.height).coerceAtLeast(margin)
        window.setPosition(
            window.x.coerceIn(margin, maxX),
            window.y.coerceIn(margin, maxY)
        )
    }

    private fun saveToolbarsVisible() {
        uiPrefs.putBoolean(toolbarsVisibleKey, toolbarsVisible)
        uiPrefs.flush()
    }

    private fun saveToolbarState(toolbarId: String, window: CollapsibleWindow) {
        val viewportHeight = if (stage.viewport.screenHeight > 0) stage.viewport.screenHeight.toFloat() else Gdx.graphics.height.toFloat()
        val xKey = "$toolbarId.x"
        val yKey = "$toolbarId.y"
        val wKey = "$toolbarId.w"
        val hKey = "$toolbarId.h"
        val visibleKey = "$toolbarId.visible"
        val prevX = uiPrefs.getFloat(xKey, Float.NaN)
        val prevY = uiPrefs.getFloat(yKey, Float.NaN)
        val prevW = uiPrefs.getFloat(wKey, Float.NaN)
        val prevH = uiPrefs.getFloat(hKey, Float.NaN)
        val topLeftY = viewportHeight - window.y - window.height
        val desiredVisible = toolbarDesiredVisibility[toolbarId] ?: true
        val prevVisible = if (uiPrefs.contains(visibleKey)) uiPrefs.getBoolean(visibleKey) else desiredVisible
        if (!prevX.isNaN() &&
            !prevY.isNaN() &&
            !prevW.isNaN() &&
            !prevH.isNaN() &&
            abs(prevX - window.x) < 0.25f &&
            abs(prevY - topLeftY) < 0.25f &&
            abs(prevW - window.width) < 0.25f &&
            abs(prevH - window.height) < 0.25f &&
            prevVisible == desiredVisible
        ) {
            return
        }
        uiPrefs.putFloat(xKey, window.x)
        uiPrefs.putFloat(yKey, topLeftY)
        uiPrefs.putFloat(wKey, window.width)
        uiPrefs.putFloat(hKey, window.height)
        uiPrefs.putBoolean(visibleKey, desiredVisible)
        uiPrefs.flush()
    }

    private fun persistToolbarStates() {
        builtInToolbars.forEach { (toolbarId, window) ->
            saveToolbarState(toolbarId, window)
        }
        pluginToolbars.forEach { (pluginId, window) ->
            saveToolbarState(pluginToolbarStateId(pluginId), window)
        }
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

    private fun normalizeTutorialActionKey(text: String): String {
        val normalized = buildString {
            text.lowercase(Locale.US).forEach { ch ->
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    ch == ' ' || ch == '-' || ch == '/' -> append('_')
                }
            }
        }.trim('_')
        return normalized.ifBlank { "action" }
    }

    private fun registerTutorialActionTarget(actionId: String, actor: Actor) {
        tutorialActionTargets[actionId] = actor
    }

    fun tutorialArrow(): Pair<Vector2, Vector2>? {
        val state = tutorialStateProvider()
        if (!state.messageVisible) {
            return null
        }
        val expectedAction = state.expectedAction ?: return null
        val target = tutorialActionTargets[expectedAction] ?: return null
        if (target.stage != stage || !target.isVisible) {
            return null
        }
        val start = tutorialMessageWindow.localToStageCoordinates(Vector2(tutorialMessageWindow.width * 0.5f, tutorialMessageWindow.height))
        val end = target.localToStageCoordinates(Vector2(target.width * 0.5f, target.height * 0.5f))
        return start to end
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
        toolbarBindingsById.entries.removeIf { it.key.startsWith("plugin_toolbar_") }
        toolbarBindingsByWindow.entries.removeIf { (_, binding) -> binding.toolbarId.startsWith("plugin_toolbar_") }
        val grouped = entries.groupBy { it.pluginId }
        grouped.forEach { (pluginId, tools) ->
            val title = tools.firstOrNull()?.pluginName ?: pluginId
            val window = CollapsibleWindow(title, showCloseButton = false)
            val content = VisTable().apply {
                defaults().pad(0f).left()
            }
            val slots = mutableListOf<ToolbarButtonSlot>()
            tools.forEach { entry ->
                val fallback = createActionIconDrawable(Color(0.65f, 0.75f, 0.95f, 1f))
                val icon = entry.iconDrawable ?: iconFor(entry.icon, fallback)
                val button = AppImageTextButton(entry.name, icon)
                applyWhiteButtonStyle(button)
                applyIconStyle(button, icon)
                button.setText("")
                buttonLabels[button] = entry.name
                button.addListener(hoverListener(button))
                attachButtonMarker(button)
                button.addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        tutorialUiActionObserved(
                            "ui.action.plugin_tool.${normalizeTutorialActionKey(entry.id)}",
                            entry.name
                        )
                        host.activatePluginTool(entry.id)
                    }
                })
                registerTutorialActionTarget("ui.action.plugin_tool.${normalizeTutorialActionKey(entry.id)}", button)
                val cell = content.add(button).size(toolbarButtonSize, toolbarButtonSize)
                slots += ToolbarButtonSlot(button, cell, toolbarButtonSize, toolbarButtonSize)
                pluginToolButtons[entry.id] = button
                pluginToolByWidget[button] = entry.id
            }
            window.add(content).pad(0f).left()
            val toolbarId = pluginToolbarStateId(pluginId)
            attachToolbarPersistence(window, toolbarId)
            applyToolbarState(toolbarId, window)
            registerToolbarBinding(toolbarId, window, content, slots)
            stage.addActor(window)
            pluginToolbars[pluginId] = window
        }
        applyToolbarsVisibility()
        updatePluginToolSelection()
        updateButtonLabels()
        refreshToolbarAutoCollapseStates(force = true)
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

    private fun showHoverPopover(button: AppImageTextButton, text: String) {
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
            ToolId.CONSTRUCTION_LINE -> "construction_line"
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
            ToolId.HVAC_PLUMBING -> "hvac_plumbing"
            ToolId.HVAC_VENTILATION -> "hvac_ventilation"
            ToolId.FACE_OUTLINE -> "face_outline"
            ToolId.MESH -> "mesh"
            ToolId.LINE_OFFSET -> "offset"
            ToolId.CUT_HOLES -> "cleanup"
            ToolId.CUT_HOLES_2 -> "cleanup"
            ToolId.CUT_OUT_3 -> "cleanup"
            ToolId.EXTRUDE_SWIPE -> "swipe_surface"
            ToolId.PLANE_SECTION -> "plane_section"
            ToolId.MESH_INTERSECTION -> "mesh_intersection"
            ToolId.RECTANGLE -> "rectangle"
            ToolId.SURFACE_RECTANGLE -> "surface_rect"
            ToolId.QUAD -> "quad"
            ToolId.CIRCLE -> "circle"
            ToolId.LINEAR_DIMENSION -> "dimension"
            ToolId.TEXT -> "text"
            ToolId.VECTOR_TEXT -> "vectorial-text"
            ToolId.PUSH_PULL -> "push_pull"
            ToolId.MOVE -> "move"
            ToolId.ROTATE -> "rotate"
            ToolId.SCALE -> "scale"
            ToolId.STRETCH -> "stretch"
            ToolId.ROTATE_STRETCH -> "stretch-rotate"
            ToolId.COPY_MULTIPLE -> "multiple-copy-translate"
            ToolId.PLANAR_TRANSLATE_MULTIPLE -> "multiple-copy-translate-planar"
            ToolId.VOLUMETRIC_TRANSLATE_MULTIPLE -> "multiple-copy-translate-volumetric"
            ToolId.PLANAR_ROTATE_MULTIPLE -> "multiple-copy-planar-rotate"
            ToolId.HELICOIDAL_ROTATE_MULTIPLE -> "multiple-copy-helicoidal-rotate"
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
            ToolId.HVAC_PLUMBING -> Color(0.55f, 0.8f, 0.95f, 1f)
            ToolId.HVAC_VENTILATION -> Color(0.82f, 0.82f, 0.82f, 1f)
            ToolId.FACE_OUTLINE -> Color(0.95f, 0.75f, 0.25f, 1f)
            ToolId.MESH -> Color(0.55f, 0.85f, 0.95f, 1f)
            ToolId.LINE_OFFSET -> Color(0.35f, 0.75f, 0.95f, 1f)
            ToolId.CUT_HOLES -> Color(0.85f, 0.55f, 0.35f, 1f)
            ToolId.CUT_HOLES_2 -> Color(0.85f, 0.55f, 0.35f, 1f)
            ToolId.CUT_OUT_3 -> Color(0.85f, 0.55f, 0.35f, 1f)
            ToolId.EXTRUDE_SWIPE -> Color(0.4f, 0.85f, 0.95f, 1f)
            ToolId.PLANE_SECTION -> Color(0.75f, 0.6f, 0.95f, 1f)
            ToolId.MESH_INTERSECTION -> Color(0.55f, 0.8f, 0.95f, 1f)
            ToolId.RECTANGLE -> Color(0.35f, 0.75f, 0.95f, 1f)
            ToolId.SURFACE_RECTANGLE -> Color(0.35f, 0.85f, 0.65f, 1f)
            ToolId.QUAD -> Color(0.55f, 0.85f, 0.95f, 1f)
            ToolId.CIRCLE -> Color(0.95f, 0.55f, 0.75f, 1f)
            ToolId.LINEAR_DIMENSION -> Color(0.8f, 0.8f, 0.4f, 1f)
            ToolId.TEXT -> Color(0.8f, 0.8f, 0.8f, 1f)
            ToolId.VECTOR_TEXT -> Color(0.7f, 0.85f, 0.95f, 1f)
            ToolId.PUSH_PULL -> Color(0.45f, 0.95f, 0.55f, 1f)
            ToolId.MOVE -> Color(0.95f, 0.45f, 0.35f, 1f)
            ToolId.ROTATE -> Color(0.75f, 0.55f, 0.95f, 1f)
            ToolId.SCALE -> Color(0.95f, 0.55f, 0.75f, 1f)
            ToolId.STRETCH -> Color(0.95f, 0.65f, 0.25f, 1f)
            ToolId.ROTATE_STRETCH -> Color(0.85f, 0.6f, 0.25f, 1f)
            ToolId.COPY_MULTIPLE -> Color(0.95f, 0.75f, 0.25f, 1f)
            ToolId.PLANAR_TRANSLATE_MULTIPLE -> Color(0.55f, 0.8f, 0.95f, 1f)
            ToolId.VOLUMETRIC_TRANSLATE_MULTIPLE -> Color(0.65f, 0.9f, 0.65f, 1f)
            ToolId.PLANAR_ROTATE_MULTIPLE -> Color(0.75f, 0.65f, 0.95f, 1f)
            ToolId.HELICOIDAL_ROTATE_MULTIPLE -> Color(0.65f, 0.75f, 0.95f, 1f)
            ToolId.PAINT -> Color(0.95f, 0.95f, 0.45f, 1f)
            ToolId.OBJECT_PLACE -> Color(0.85f, 0.85f, 0.85f, 1f)
            ToolId.PLUGIN -> Color(0.75f, 0.85f, 0.95f, 1f)
        }

        val size = toolbarIconSizePx.coerceAtLeast(16)
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
        val size = toolbarIconSizePx.coerceAtLeast(16)
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

    private fun updateButtonIcon(button: AppImageTextButton?, color: Color) {
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

    private fun applyIconStyle(button: AppImageTextButton, icon: com.badlogic.gdx.scenes.scene2d.utils.Drawable) {
        val style = button.style
        style.imageUp = icon
        style.imageDown = icon
        style.imageChecked = icon
        style.imageOver = icon
        button.image?.drawable = icon
        val iconCellSize = (toolbarButtonSize - 4f).coerceAtLeast(12f)
        button.imageCell?.size(iconCellSize, iconCellSize)
    }

    private fun attachButtonMarker(button: AppImageTextButton) {
        val drawable = markerDrawable ?: createSolidDrawable(Color.WHITE).also { markerDrawable = it }
        val marker = object : Image(drawable) {
            override fun act(delta: Float) {
                super.act(delta)
                setPosition((button.width - width - 2f).coerceAtLeast(1f), (button.height - height - 2f).coerceAtLeast(1f))
            }
        }.apply {
            color = Color.WHITE
            val markerSize = (toolbarButtonSize * 0.22f).coerceIn(8f, 16f)
            setSize(markerSize, markerSize)
            touchable = Touchable.disabled
        }
        button.addActor(marker)
        buttonMarkers[button] = marker
    }

    private fun updateButtonLabels() {
        val host = pluginHost
        val activePluginToolId = host?.activePluginToolId()
        val activeColor = Color(0.9f, 0.1f, 0.1f, 1f)
        val hoverColor = Color(1f, 0.55f, 0.1f, 1f)
        val neutralColor = Color.WHITE

        buttonLabels.forEach { (button, _) ->
            button.setText("")
            val isHovered = hoveredButtons.contains(button)
            val isActiveTool = toolButtonByWidget[button] == status.activeTool
            val isActivePluginTool = pluginToolByWidget[button] != null && pluginToolByWidget[button] == activePluginToolId
            val marker = buttonMarkers[button]
            marker?.color = when {
                isActiveTool || isActivePluginTool -> activeColor
                isHovered -> hoverColor
                else -> neutralColor
            }
        }
    }

    private fun hoverListener(button: AppImageTextButton): ClickListener {
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

    private fun applyWhiteButtonStyle(button: AppImageTextButton) {
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

    private fun createDockSectionHeaderDrawable(fill: Color, border: Color): TextureRegionDrawable {
        return createButtonBackgroundDrawable(fill, border)
    }

    private fun iconFor(name: String, fallback: com.badlogic.gdx.scenes.scene2d.utils.Drawable?): TextureRegionDrawable {
        return iconDrawables[name] ?: (fallback as? TextureRegionDrawable)
            ?: createActionIconDrawable(Color(0.3f, 0.3f, 0.3f, 1f))
    }

    private fun loadIconDrawables(): Map<String, TextureRegionDrawable> {
        val mapping = mutableMapOf<String, TextureRegionDrawable>()
        val mappingFile = Gdx.files.internal("icons.mapping.csv")
        val textureFile = when (toolbarIconSizePx) {
            48 -> Gdx.files.internal("icons.48.png")
            64 -> Gdx.files.internal("icons.64.png")
            else -> Gdx.files.internal("icons.png")
        }.let { preferred ->
            if (preferred.exists()) preferred else Gdx.files.internal("icons.png")
        }
        if (!mappingFile.exists() || !textureFile.exists()) {
            return mapping
        }
        val texture = Texture(textureFile)
        iconsTexture = texture
        iconTextures.add(texture)
        val lines = mappingFile.readString("UTF-8").lines().filter { it.isNotBlank() }
        val coordStartIndex = when (toolbarIconSizePx) {
            48 -> 8
            64 -> 12
            else -> 4
        }
        lines.drop(1).forEach { line ->
            val parts = line.split('|')
            if (parts.size < coordStartIndex + 4) {
                return@forEach
            }
            val name = parts[1].trim()
            val startX = parts[coordStartIndex].trim().toIntOrNull() ?: return@forEach
            val endX = parts[coordStartIndex + 1].trim().toIntOrNull() ?: return@forEach
            val startY = parts[coordStartIndex + 2].trim().toIntOrNull() ?: return@forEach
            val endY = parts[coordStartIndex + 3].trim().toIntOrNull() ?: return@forEach
            val width = endX - startX + 1
            val height = endY - startY + 1
            val region = TextureRegion(texture, startX, startY, width, height)
            mapping[name] = TextureRegionDrawable(region)
        }
        return mapping
    }

    data class SelectionInfo(
        val edgeCount: Int,
        val edgeTotalCount: Int = 0,
        val edgeDrawEnabled: Boolean = true,
        val edgeModifyEnabled: Boolean = true,
        val edgeWireframe: Boolean = false,
        val faceCount: Int,
        val faceTotalCount: Int = 0,
        val faceDrawEnabled: Boolean = true,
        val faceModifyEnabled: Boolean = true,
        val faceWireframe: Boolean = false,
        val voxelCount: Int,
        val voxelTotalCount: Int = 0,
        val voxelDrawEnabled: Boolean = true,
        val voxelModifyEnabled: Boolean = true,
        val voxelWireframe: Boolean = false,
        val hotspotCount: Int,
        val hotspotTotalCount: Int = 0,
        val hotspotDrawEnabled: Boolean = true,
        val hotspotModifyEnabled: Boolean = true,
        val hotspotWireframe: Boolean = false,
        val groupCount: Int,
        val groupTotalCount: Int = 0,
        val objectDrawEnabled: Boolean = true,
        val objectModifyEnabled: Boolean = true,
        val objectWireframe: Boolean = false,
        val dimensionCount: Int,
        val dimensionTotalCount: Int = 0,
        val dimensionDrawEnabled: Boolean = true,
        val dimensionModifyEnabled: Boolean = true,
        val dimensionWireframe: Boolean = false,
        val textCount: Int,
        val textTotalCount: Int = 0,
        val textDrawEnabled: Boolean = true,
        val textModifyEnabled: Boolean = true,
        val textWireframe: Boolean = false,
        val wallTotalCount: Int = 0,
        val wallSelectedCount: Int = 0,
        val wallDrawEnabled: Boolean = true,
        val wallModifyEnabled: Boolean = true,
        val wallWireframe: Boolean = false,
        val slabTotalCount: Int = 0,
        val slabSelectedCount: Int = 0,
        val slabDrawEnabled: Boolean = true,
        val slabModifyEnabled: Boolean = true,
        val slabWireframe: Boolean = false,
        val stairTotalCount: Int = 0,
        val stairSelectedCount: Int = 0,
        val stairDrawEnabled: Boolean = true,
        val stairModifyEnabled: Boolean = true,
        val stairWireframe: Boolean = false,
        val frameTotalCount: Int = 0,
        val frameSelectedCount: Int = 0,
        val frameDrawEnabled: Boolean = true,
        val frameModifyEnabled: Boolean = true,
        val frameWireframe: Boolean = false,
        val selectedTextId: String? = null,
        val selectedTextValue: String? = null,
        val selectedTextSize: Float? = null,
        val selectedTextScreen: Boolean? = null,
        val selectedColor: Color? = null,
        val selectedColorEditable: Boolean = false,
        val selectedVectorText: Boolean = false,
        val selectedVectorTextTracking: Float? = null,
        val selectedVectorTextLineSpacing: Float? = null,
        val selectedVectorTextGlyphSourcePath: String? = null
    )

    data class HotspotSelectionInfo(
        val selectedCount: Int = 0,
        val selectedId: String? = null,
        val selectedName: String? = null,
        val selectedOperation: HotspotStore.OperationKind? = null,
        val selectedShape: HotspotStore.ShapeKind? = null,
        val selectedColor: Color? = null,
        val attachedEdgeCount: Int = 0,
        val attachedFaceCount: Int = 0,
        val hasReference: Boolean = false
    )

    data class GroupInfo(val id: String, val name: String, val glued: Boolean, val editing: Boolean)

    data class ObjectPrototypeInfo(val id: String, val name: String, val instanceCount: Int)

    enum class ArchitectureElementKind {
        WALL,
        SLAB,
        STAIR,
        FRAME
    }

    enum class SelectionFilterKind {
        EDGE,
        FACE,
        VOXEL,
        HOTSPOT,
        OBJECT,
        DIMENSION,
        TEXT,
        WALL,
        SLAB,
        STAIR,
        FRAME
    }

    data class ArchitectureElementInfo(
        val kind: ArchitectureElementKind,
        val id: String,
        val name: String? = null,
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
        val frameColor: Color? = null,
        val frameGlazingEnabled: Boolean? = null,
        val frameGlazingColor: Color? = null
    )

    data class ArchitectureSelectionSummary(
        val selectedWallCount: Int = 0,
        val selectedSlabCount: Int = 0,
        val selectedStairCount: Int = 0,
        val selectedFrameCount: Int = 0,
        val singleWallId: String? = null,
        val singleSlabId: String? = null,
        val singleStairId: String? = null,
        val singleFrameId: String? = null,
        val singleWallName: String? = null,
        val singleSlabName: String? = null,
        val singleStairName: String? = null,
        val singleFrameName: String? = null
    )
}

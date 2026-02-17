package com.github.alfu32.sketch

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.RenderableProvider
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import com.github.alfu32.sketch.input.GuideManager
import com.github.alfu32.sketch.input.CameraEventRouter
import com.github.alfu32.sketch.input.CameraScrollForwarder
import com.github.alfu32.sketch.input.SnapResult
import com.github.alfu32.sketch.input.Snapper
import com.github.alfu32.sketch.input.ToolPointerProcessor
import com.github.alfu32.sketch.model.ArchitectureStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.model.ModelPersistence
import com.github.alfu32.sketch.model.ModelCleanup
import com.github.alfu32.sketch.model.ModelUnit
import com.github.alfu32.sketch.model.VoxelStore
import com.github.alfu32.sketch.render.SketchShaderProvider
import com.github.alfu32.sketch.console.AppFacade
import com.github.alfu32.sketch.console.ConsoleGroovyRuntime
import com.github.alfu32.sketch.console.CameraFacade
import com.github.alfu32.sketch.console.ConsolePaths
import com.github.alfu32.sketch.console.ConsoleThread
import com.github.alfu32.sketch.console.ConsoleUtils
import com.github.alfu32.sketch.console.LightingFacade
import com.github.alfu32.sketch.console.McpFacade
import com.github.alfu32.sketch.console.SelectionFacade
import com.github.alfu32.sketch.console.TerminalController
import com.github.alfu32.sketch.console.UnitFacade
import com.github.alfu32.sketch.console.SaveFacade
import com.github.alfu32.sketch.mcp.McpHttpServer
import com.github.alfu32.sketch.mcp.McpConsoleResult
import com.github.alfu32.sketch.mcp.McpPointerEventRequest
import com.github.alfu32.sketch.mcp.McpPointerEventResult
import com.github.alfu32.sketch.mcp.StdoutTap
import com.github.alfu32.sketch.tui.ConsoleExecutionResult
import com.github.alfu32.sketch.tui.ConsoleTui
import com.github.alfu32.sketch.tui.HistoryManager
import com.github.alfu32.sketch.tui.OutputPane
import com.github.alfu32.sketch.K3DVersion
import com.badlogic.gdx.Graphics
import com.github.alfu32.sketch.plugin.PluginHost
import com.github.alfu32.sketch.export.IfcExporter
import com.github.alfu32.sketch.tools.CircleTool
import com.github.alfu32.sketch.tools.ConstructionLineTool
import com.github.alfu32.sketch.tools.CutHolesTool
import com.github.alfu32.sketch.tools.CutHolesTool2
import com.github.alfu32.sketch.tools.CutOut3Tool
import com.github.alfu32.sketch.tools.ArchitectureAddHoleTool
import com.github.alfu32.sketch.tools.ArchitectureDoorFrameTool
import com.github.alfu32.sketch.tools.ArchitectureSettings
import com.github.alfu32.sketch.tools.ArchitectureSlabTool
import com.github.alfu32.sketch.tools.ArchitectureStairTool
import com.github.alfu32.sketch.tools.ArchitectureWallTool
import com.github.alfu32.sketch.tools.ArchitectureWindowFrameTool
import com.github.alfu32.sketch.tools.ExtrudeSwipeTool
import com.github.alfu32.sketch.tools.MeshIntersectionTool
import com.github.alfu32.sketch.tools.FaceOutlineTool
import com.github.alfu32.sketch.tools.HvacPlumbingTool
import com.github.alfu32.sketch.tools.HvacSettings
import com.github.alfu32.sketch.tools.HvacVentilationTool
import com.github.alfu32.sketch.tools.LinearDimensionTool
import com.github.alfu32.sketch.tools.LineOffsetTool
import com.github.alfu32.sketch.tools.LineTool
import com.github.alfu32.sketch.tools.MoveTool
import com.github.alfu32.sketch.tools.ObjectPlaceTool
import com.github.alfu32.sketch.tools.PaintTool
import com.github.alfu32.sketch.tools.PlaneSectionTool
import com.github.alfu32.sketch.tools.PolylineSettings
import com.github.alfu32.sketch.tools.PolylineToolInternal
import com.github.alfu32.sketch.tools.DoubleLineToolInternal
import com.github.alfu32.sketch.tools.PushPullTool
import com.github.alfu32.sketch.tools.QuadTool
import com.github.alfu32.sketch.tools.RectangleTool
import com.github.alfu32.sketch.tools.RotateTool
import com.github.alfu32.sketch.tools.SurfaceRectangleTool
import com.github.alfu32.sketch.tools.SelectTool
import com.github.alfu32.sketch.tools.ScaleTool
import com.github.alfu32.sketch.tools.StretchTool
import com.github.alfu32.sketch.tools.TextTool
import com.github.alfu32.sketch.tools.VoxelFrameTool
import com.github.alfu32.sketch.tools.VoxelTool
import com.github.alfu32.sketch.tools.VoxelVolumeTool
import com.github.alfu32.sketch.ui.SketchUiOverlay
import com.github.alfu32.sketch.ui.LightingSettings
import com.github.alfu32.sketch.ui.CameraMode
import com.github.alfu32.sketch.ui.ShadowSettings
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.ToolController
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolInputProcessor
import com.github.alfu32.sketch.ui.PluginToolAdapter
import com.github.alfu32.sketch.ui.WalkthroughTuning
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.file.FileChooser
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter
import com.kotcrab.vis.ui.widget.file.FileTypeFilter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.floor

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms. */
class Main(private val startupArgs: kotlin.Array<String> = emptyArray()) : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var walkCamera: PerspectiveCamera
    private lateinit var orthoCamera: OrthographicCamera
    private lateinit var activeCamera: Camera
    private lateinit var orbitCameraController: ShiftCameraController
    private lateinit var walkCameraController: WalkthroughCameraController
    private lateinit var orthoCameraController: OrthographicCameraController
    private var activeCameraMode: CameraMode = CameraMode.ORBIT
    private var activeCameraInputProcessor: InputProcessor = InputAdapter()
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var textFont: BitmapFont
    private var ownsTextFont = false
    private val textLayout = GlyphLayout()
    private val textTransform = Matrix4()
    private val textTransformBackup = Matrix4()
    private lateinit var faceMesh: Mesh
    private lateinit var groundMesh: Mesh
    private lateinit var modelBatch: ModelBatch
    private lateinit var shadowBatch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var shadowLight: DirectionalShadowLight
    private lateinit var mainLight: DirectionalLight
    private lateinit var faceFrontMaterial: Material
    private lateinit var faceBackMaterial: Material
    private lateinit var groundMaterial: Material
    private lateinit var faceFrontRenderable: MeshRenderableProvider
    private lateinit var faceBackRenderable: MeshRenderableProvider
    private lateinit var groundRenderable: MeshRenderableProvider
    private val selectedFaceColor = Color(1f, 0f, 0f, 0.3f)
    private val selectedLineColor = Color(1f, 0f, 0f, 1f)
    private val architectureHoleGuideColor = Color(0.2f, 0.55f, 0.95f, 1f)
    private val architectureHoleHotspotColor = Color(0.2f, 0.55f, 0.95f, 1f)
    private val architectureSlabHotspotColor = Color(0.2f, 0.9f, 0.85f, 1f)
    private val architectureFrameHotspotColor = Color(1f, 0.7f, 0.25f, 1f)
    private val selectedEntityBoxColor = Color(0.2f, 0.7f, 0.95f, 1f)
    private val editModeBoxColor = Color(1f, 0.6f, 0.2f, 1f)
    private val selectedLineWidth = 8f
    private lateinit var toolController: ToolController
    private lateinit var toolInput: ToolInputProcessor
    private lateinit var uiOverlay: SketchUiOverlay
    private lateinit var toolPointer: ToolPointerProcessor
    private val polylineSettings = PolylineSettings()
    private val architectureSettings = ArchitectureSettings()
    private val hvacSettings = HvacSettings()
    private lateinit var statusModel: StatusModel
    private lateinit var scene: GroupScene
    private lateinit var modelCleanup: ModelCleanup
    private lateinit var guideManager: GuideManager
    private lateinit var snapper: Snapper
    private var lastSnap: SnapResult? = null
    private var distanceOverrideSnap: SnapResult? = null
    private var distanceInputActive = false
    private var gridSpacing = 1f
    private var snapEpsilon = 12f
    private var modelUnit = ModelUnit(1f, "unit")
    private lateinit var modelFile: java.io.File
    private var shadowLightValue = 0.59f
    private var shadowLightAlpha = 0.5f
    private var directionalLightValue = 0.73f
    private var directionalLightAlpha = 1f
    private var ambientLightValue = 0.59f
    private var ambientLightAlpha = 1f
    private var specularLightValue = 0.2f
    private var specularLightAlpha = 0.95f
    private lateinit var lightingSettings: LightingSettings
    private var shadowBias = 365f
    private var shadowNormalBias = 5620f
    private var shadowPcfMode = 1
    private var shadowDither = false
    private var shadowUseCsm = true
    private lateinit var shadowSettings: ShadowSettings
    private lateinit var pluginHost: PluginHost
    private lateinit var installDir: java.io.File
    private lateinit var objectPlaceTool: ObjectPlaceTool
    private val cameraTarget = Vector3(0f, 0f, 0f)
    private val screenshotTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private var mcpPort = 8765
    private lateinit var stdoutTap: StdoutTap
    private lateinit var mcpServer: McpHttpServer
    private val orthoDistance = 250f
    private enum class OrthoView { TOP, BOTTOM, LEFT, RIGHT, FRONT, BACK }
    private var consoleThread: ConsoleThread? = null
    private var consoleRuntime: ConsoleGroovyRuntime? = null
    private var mcpConsoleRuntime: ConsoleGroovyRuntime? = null
    private var mcpConsoleTui: ConsoleTui? = null
    private var consoleTerminal: TerminalController? = null
    private lateinit var undoManager: com.github.alfu32.sketch.model.UndoRedoManager
    private var restoringSnapshot = false

    override fun create() {
        stdoutTap = StdoutTap.install()
        if (!VisUI.isLoaded()) {
            VisUI.load()
        }

        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            position.set(6f, 6f, 6f)
            lookAt(0f, 0f, 0f)
            near = 0.1f
            far = 500f
            update()
        }

        walkCamera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            position.set(camera.position)
            direction.set(camera.direction)
            up.set(camera.up)
            near = 0.1f
            far = 500f
            update()
        }

        orthoCamera = OrthographicCamera().apply {
            near = -2000f
            far = 2000f
            zoom = 1f
        }
        configureOrthoViewport(Gdx.graphics.width, Gdx.graphics.height)
        alignOrthographicView(OrthoView.TOP)

        orbitCameraController = ShiftCameraController(camera, this::pickPanPoint).apply {
            rotateButton = Input.Buttons.RIGHT
            translateButton = Input.Buttons.RIGHT
        }
        orbitCameraController.target.set(cameraTarget)

        walkCameraController = WalkthroughCameraController(
            walkCamera,
            this::walkSupportHeightAt,
            eyeHeight = 6f
        )
        orthoCameraController = OrthographicCameraController(orthoCamera, cameraTarget)
        setCameraMode(CameraMode.ORBIT)
        installDir = resolveInstallDir()
        statusModel = StatusModel(
            activeTool = ToolId.SELECT,
            message = "Select entities.",
            inputBuffer = ""
        )
        scene = GroupScene(Color(0.8f, 0.8f, 0.8f, 1f))
        modelCleanup = ModelCleanup(scene)
        guideManager = GuideManager()
        snapper = Snapper(activeCamera, scene, guideManager, gridSpacing, snapEpsilon)
        objectPlaceTool = ObjectPlaceTool(
            scene,
            { instance ->
                scene.clearGroupSelection()
                scene.addGroupSelection(instance)
                toolController.setTool(ToolId.SELECT)
            },
            {
                toolController.setTool(ToolId.SELECT)
            }
        )
        toolController = ToolController(
            statusModel,
            listOf(
                SelectTool(scene) { activeCamera },
                LineTool(scene) { toolController.setTool(ToolId.SELECT) },
                ConstructionLineTool(scene) { toolController.setTool(ToolId.SELECT) },
                PolylineToolInternal(scene, polylineSettings),
                DoubleLineToolInternal(scene, polylineSettings),
                VoxelTool(scene, { toolController.setTool(ToolId.SELECT) }, ::ensureActiveVoxelGroupForTools),
                VoxelVolumeTool(scene, { toolController.setTool(ToolId.SELECT) }, ::ensureActiveVoxelGroupForTools),
                VoxelFrameTool(scene, { toolController.setTool(ToolId.SELECT) }, ::ensureActiveVoxelGroupForTools),
                ArchitectureWallTool(scene, architectureSettings, { toolController.setTool(ToolId.SELECT) }, ::ensureActiveArchitectureGroupForTools),
                ArchitectureSlabTool(scene, architectureSettings, { toolController.setTool(ToolId.SELECT) }, ::ensureActiveArchitectureGroupForTools),
                ArchitectureStairTool(scene, { activeCamera }, architectureSettings, { toolController.setTool(ToolId.SELECT) }, ::ensureActiveArchitectureGroupForTools),
                ArchitectureAddHoleTool(scene, { toolController.setTool(ToolId.SELECT) }, ::ensureActiveArchitectureGroupForTools),
                ArchitectureWindowFrameTool(scene, architectureSettings, { toolController.setTool(ToolId.SELECT) }, ::ensureActiveArchitectureGroupForTools),
                ArchitectureDoorFrameTool(scene, architectureSettings, { toolController.setTool(ToolId.SELECT) }, ::ensureActiveArchitectureGroupForTools),
                HvacPlumbingTool(scene, hvacSettings) { toolController.setTool(ToolId.SELECT) },
                HvacVentilationTool(scene, hvacSettings) { toolController.setTool(ToolId.SELECT) },
                FaceOutlineTool(scene),
                LineOffsetTool(scene),
                CutHolesTool(scene) { toolController.setTool(ToolId.SELECT) },
                CutHolesTool2(scene) { toolController.setTool(ToolId.SELECT) },
                CutOut3Tool(scene) { toolController.setTool(ToolId.SELECT) },
                ExtrudeSwipeTool(scene, polylineSettings) { toolController.setTool(ToolId.SELECT) },
                PlaneSectionTool(scene) { toolController.setTool(ToolId.SELECT) },
                MeshIntersectionTool(scene) { toolController.setTool(ToolId.SELECT) },
                RectangleTool(scene),
                SurfaceRectangleTool(scene),
                QuadTool(scene),
                CircleTool(scene),
                LinearDimensionTool(scene),
                TextTool(scene) { activeCamera },
                PushPullTool(scene) { activeCamera },
                MoveTool(scene),
                RotateTool(scene),
                ScaleTool(scene),
                StretchTool(scene),
                PaintTool(scene, { activeCamera }) { statusModel.paintColor.cpy() },
                objectPlaceTool
            )
        )
        toolInput = ToolInputProcessor(
            controller = toolController,
            guideManager = guideManager,
            cleanupAction = ::runCleanup,
            clearSelectionAction = ::clearSelection,
            deleteSelectionAction = ::deleteSelection,
            undoAction = ::undoAction,
            redoAction = ::redoAction,
            groupSelectionAction = ::groupSelection,
            objectPrototypeSelectionAction = ::objectPrototypeSelection,
            ungroupSelectionAction = ::ungroupSelection,
            exitGroupEditAction = ::exitGroupEditMode,
            lastSnapProvider = { lastSnap },
            showDistanceInput = { startDistanceInput() },
            uiCapturesInput = { uiOverlay.isUiCapturingInputByPointer() }
        )
        lightingSettings = LightingSettings(
            shadowLightValue = shadowLightValue,
            shadowLightAlpha = shadowLightAlpha,
            directionalLightValue = directionalLightValue,
            directionalLightAlpha = directionalLightAlpha,
            ambientLightValue = ambientLightValue,
            ambientLightAlpha = ambientLightAlpha,
            specularLightValue = specularLightValue,
            specularLightAlpha = specularLightAlpha
        )
        shadowSettings = ShadowSettings(
            shadowBias = shadowBias,
            shadowNormalBias = shadowNormalBias,
            pcfMode = shadowPcfMode,
            dither = shadowDither,
            useCsm = shadowUseCsm
        )
        val pluginsDir = resolvePluginsDir(startupArgs, installDir)
        pluginHost = PluginHost(
            scene,
            statusModel,
            camera,
            { orbitCameraController.target },
            lightingSettings,
            shadowSettings,
            { toolController.activeToolId() },
            { statusModel.copyMode },
            { lastSnap },
            { toolId -> toolController.setTool(toolId) },
            { modelUnit },
            { snapEpsilon },
            { gridSpacing },
            pluginsDir,
            { modelFile }
        )
        toolController.registerTool(PluginToolAdapter(pluginHost))
        uiOverlay = SketchUiOverlay(
            toolController,
            statusModel,
            ::runCleanup,
            ::deleteSelection,
            ::flipSelectedFaces,
            ::voxelizeSelectedFaces,
            ::selectionInfo,
            ::updateSelectedText,
            ::updateSelectedTextSize,
            ::updateSelectedTextScreen,
            ::groupInfo,
            ::updateGroupName,
            ::updateGroupGlue,
            ::objectPrototypeInfo,
            ::startObjectPlacement,
            ::deleteObjectPrototype,
            ::modelUnitInfo,
            ::updateModelUnit,
            { gridSpacing },
            ::setGridSpacing,
            { snapEpsilon },
            ::setSnapEpsilon,
            ::walkthroughTuningInfo,
            ::updateWalkthroughTuning,
            lightingSettings,
            ::applyLightingSettings,
            shadowSettings,
            ::applyShadowSettings,
            polylineSettings,
            architectureSettings,
            hvacSettings,
            ::architectureSelectionInfo,
            ::architectureSelectionSummary,
            ::updateArchitectureElementName,
            ::updateArchitectureWallParameters,
            ::updateArchitectureSlabParameters,
            ::updateArchitectureStairParameters,
            ::updateArchitectureFrameParameters,
            { activeCameraMode },
            ::setCameraMode
        )

        // Set up plugin host for UI
        uiOverlay.setPluginHost(pluginHost)
        pluginHost.setShowPluginPanelHandler { panelId ->
            uiOverlay.showPluginPanel(panelId)
        }
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.objects",
                name = "View> Objects",
                description = "Toggle object prototypes panel",
                icon = "view",
                category = "View",
                tags = listOf("objects", "panel", "prototypes"),
                priority = 1,
                execute = {
                    uiOverlay.toggleObjectsPanel()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.selection",
                name = "View> Selection",
                description = "Show selection panel",
                icon = "view",
                category = "View",
                tags = listOf("selection", "panel"),
                priority = 1,
                execute = {
                    uiOverlay.showSelectionPanel()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.object_info",
                name = "View> Object Info",
                description = "Show object info panel",
                icon = "view",
                category = "View",
                tags = listOf("object", "info", "panel"),
                priority = 1,
                execute = {
                    uiOverlay.showGroupPanel()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.lighting",
                name = "View> Lighting",
                description = "Show lighting panel",
                icon = "view",
                category = "View",
                tags = listOf("lighting", "panel"),
                priority = 1,
                execute = {
                    uiOverlay.showLightingPanel()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.model_settings",
                name = "View> Model Settings",
                description = "Show model settings panel",
                icon = "view",
                category = "View",
                tags = listOf("model", "settings", "panel"),
                priority = 1,
                execute = {
                    uiOverlay.showModelSettingsPanel()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.polyline_settings",
                name = "View> Polyline Settings",
                description = "Show polyline settings panel",
                icon = "view",
                category = "View",
                tags = listOf("polyline", "settings", "panel"),
                priority = 1,
                execute = {
                    uiOverlay.showPolylineSettingsPanel()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.architecture_settings",
                name = "View> Architecture Settings",
                description = "Show architecture settings panel",
                icon = "view",
                category = "View",
                tags = listOf("architecture", "settings", "panel"),
                priority = 1,
                execute = {
                    uiOverlay.showArchitectureSettingsPanel()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.hvac_settings",
                name = "View> HVAC Settings",
                description = "Show HVAC settings panel",
                icon = "view",
                category = "View",
                tags = listOf("hvac", "settings", "panel"),
                priority = 1,
                execute = {
                    uiOverlay.showHvacSettingsPanel()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.plugin_manager",
                name = "View> Plugin Manager",
                description = "Show plugin manager panel",
                icon = "view",
                category = "View",
                tags = listOf("plugins", "panel"),
                priority = 1,
                execute = {
                    uiOverlay.showPluginManager()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "edit.new_architecture_group",
                name = "Edit> Architecture: Wall Tool",
                description = "Activate architecture modeling in the model root",
                icon = "edit",
                category = "Edit",
                tags = listOf("architecture", "wall", "slab", "stair"),
                priority = 1,
                execute = {
                    createArchitectureGroup()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "edit.new_voxel_group",
                name = "Edit> New Voxel Group",
                description = "Create a voxel group and enter edit mode",
                icon = "edit",
                category = "Edit",
                tags = listOf("voxel", "group", "minecraft"),
                priority = 1,
                execute = {
                    createVoxelGroup()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "edit.voxelize_faces",
                name = "Edit> Voxelize Faces",
                description = "Voxelize selected faces into the active voxel model",
                icon = "edit",
                category = "Edit",
                tags = listOf("voxel", "faces", "convert"),
                priority = 1,
                execute = {
                    voxelizeSelectedFaces()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "edit.group",
                name = "Edit> Group",
                description = "Create object prototype from selection",
                icon = "edit",
                category = "Edit",
                tags = listOf("group", "object"),
                priority = 1,
                execute = {
                    objectPrototypeSelection()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "edit.ungroup",
                name = "Edit> Ungroup",
                description = "Ungroup selected objects",
                icon = "edit",
                category = "Edit",
                tags = listOf("ungroup", "object"),
                priority = 1,
                execute = {
                    ungroupSelection()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.axis_guide",
                name = "View> Add Axis Guide",
                description = "Add axis helper at cursor snap",
                icon = "view",
                category = "View",
                tags = listOf("axis", "guide"),
                priority = 1,
                execute = {
                    addAxisGuide()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.grid_guide",
                name = "View> Add Grid Guide",
                description = "Add grid helper at cursor snap",
                icon = "view",
                category = "View",
                tags = listOf("grid", "guide"),
                priority = 1,
                execute = {
                    addGridGuide()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.camera.orbit",
                name = "View> Camera Orbit",
                description = "Switch to perspective orbit camera",
                icon = "view",
                category = "View",
                tags = listOf("camera", "orbit", "perspective"),
                priority = 1,
                execute = {
                    setCameraMode(CameraMode.ORBIT)
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.camera.walkthrough",
                name = "View> Camera Walkthrough",
                description = "Switch to perspective walkthrough camera",
                icon = "view",
                category = "View",
                tags = listOf("camera", "walkthrough", "perspective"),
                priority = 1,
                execute = {
                    setCameraMode(CameraMode.WALKTHROUGH)
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.camera.orthographic",
                name = "View> Camera Orthographic",
                description = "Switch to orthographic camera",
                icon = "view",
                category = "View",
                tags = listOf("camera", "orthographic"),
                priority = 1,
                execute = {
                    setCameraMode(CameraMode.ORTHOGRAPHIC)
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.capture_ui_minimal",
                name = "View> Capture UI Minimal",
                description = "Hide floating panels and palette for cleaner screenshots",
                icon = "view",
                category = "View",
                tags = listOf("capture", "screenshot", "ui", "panels"),
                priority = 1,
                execute = {
                    uiOverlay.setAutomationHidePanels(true)
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.capture_ui_restore",
                name = "View> Capture UI Restore",
                description = "Restore normal floating panel behavior after capture mode",
                icon = "view",
                category = "View",
                tags = listOf("capture", "screenshot", "ui", "panels"),
                priority = 1,
                execute = {
                    uiOverlay.setAutomationHidePanels(false)
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        registerOrthographicViewCommand("view.ortho.top", "View> Ortho Top", "Switch to orthographic top view", OrthoView.TOP)
        registerOrthographicViewCommand("view.ortho.bottom", "View> Ortho Bottom", "Switch to orthographic bottom view", OrthoView.BOTTOM)
        registerOrthographicViewCommand("view.ortho.left", "View> Ortho Left", "Switch to orthographic left view", OrthoView.LEFT)
        registerOrthographicViewCommand("view.ortho.right", "View> Ortho Right", "Switch to orthographic right view", OrthoView.RIGHT)
        registerOrthographicViewCommand("view.ortho.front", "View> Ortho Front", "Switch to orthographic front view", OrthoView.FRONT)
        registerOrthographicViewCommand("view.ortho.back", "View> Ortho Back", "Switch to orthographic back view", OrthoView.BACK)
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "export.svg_view",
                name = "Export> SVG (View)",
                description = "Export current view as SVG",
                icon = "export",
                category = "Export",
                tags = listOf("export", "svg", "view"),
                priority = 1,
                execute = {
                    showSvgExportDialog()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "export.screenshot",
                name = "Export> Screenshot",
                description = "Save a screenshot named after the current file with timestamp",
                icon = "export",
                category = "Export",
                tags = listOf("export", "screenshot", "printscreen", "png"),
                priority = 1,
                execute = {
                    val saved = saveScreenshotWithCurrentFileName()
                    if (saved != null) {
                        com.github.alfu32.sketch.plugin.PluginResult.success()
                    } else {
                        com.github.alfu32.sketch.plugin.PluginResult.failure(statusModel.message)
                    }
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "export.ifc_model",
                name = "Export> IFC (Model)",
                description = "Export model geometry as IFC",
                icon = "export",
                category = "Export",
                tags = listOf("export", "ifc", "model"),
                priority = 1,
                execute = {
                    showIfcExportDialog()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "edit.cut_rect_hole",
                name = "Edit> Cut Rect Hole",
                description = "Cut a rectangular hole from selected faces using selected polyline",
                icon = "edit",
                category = "Edit",
                tags = listOf("cut", "hole", "rect"),
                priority = 1,
                execute = {
                    cutRectHole()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "edit.reset_scene_for_capture",
                name = "Edit> Reset Scene For Capture",
                description = "Reset scene and set perspective Y-up overview camera for screenshot automation",
                icon = "edit",
                category = "Edit",
                tags = listOf("reset", "scene", "capture", "screenshot", "perspective"),
                priority = 1,
                execute = {
                    resetSceneForCapture()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        listOf(
            ToolId.SELECT,
            ToolId.LINE,
            ToolId.CONSTRUCTION_LINE,
            ToolId.VOXEL,
            ToolId.VOXEL_VOLUME,
            ToolId.VOXEL_FRAME,
            ToolId.ARCH_WALL,
            ToolId.ARCH_SLAB,
            ToolId.ARCH_STAIR,
            ToolId.ARCH_ADD_HOLE,
            ToolId.ARCH_WINDOW_FRAME,
            ToolId.ARCH_DOOR_FRAME,
            ToolId.RECTANGLE,
            ToolId.SURFACE_RECTANGLE,
            ToolId.QUAD,
            ToolId.CIRCLE,
            ToolId.FACE_OUTLINE,
            ToolId.LINE_OFFSET,
            ToolId.CUT_HOLES,
            ToolId.CUT_HOLES_2,
            ToolId.CUT_OUT_3,
            ToolId.EXTRUDE_SWIPE,
            ToolId.PLANE_SECTION,
            ToolId.MESH_INTERSECTION,
            ToolId.LINEAR_DIMENSION,
            ToolId.TEXT,
            ToolId.PUSH_PULL,
            ToolId.MOVE,
            ToolId.ROTATE,
            ToolId.SCALE,
            ToolId.STRETCH,
            ToolId.PAINT
        ).forEach { toolId ->
            pluginHost.getCommandPalette().registerCommand(
                com.github.alfu32.sketch.plugin.PaletteCommand(
                    id = "tool.builtin.${toolId.name.lowercase()}",
                    name = "Tool> ${toolId.displayName}",
                    description = "Activate ${toolId.displayName} tool",
                    icon = "tool",
                    category = "Tools",
                    tags = listOf(toolId.displayName.lowercase()),
                    priority = 1,
                    execute = {
                        toolController.setTool(toolId)
                        com.github.alfu32.sketch.plugin.PluginResult.success()
                    }
                )
            )
        }

        toolPointer = ToolPointerProcessor(toolController, snapper) { distanceOverrideSnap }
        val uiBlocker = object : com.badlogic.gdx.InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (uiOverlay.isUiHit(screenX, screenY)) {
                    return true
                }
                uiOverlay.clearUiFocus()
                return false
            }

            override fun keyDown(keycode: Int): Boolean {
                return uiOverlay.isUiCapturingInputByPointer()
            }

            override fun keyUp(keycode: Int): Boolean {
                return uiOverlay.isUiCapturingInputByPointer()
            }

            override fun keyTyped(character: Char): Boolean {
                return uiOverlay.isUiCapturingInputByPointer()
            }
        }
        val cameraScrollForwarder = CameraScrollForwarder { activeCameraInputProcessor }
        val cameraEventRouter = CameraEventRouter { activeCameraInputProcessor }
        Gdx.input.inputProcessor = InputMultiplexer(
            cameraScrollForwarder,
            uiOverlay.stage,
            uiBlocker,
            toolPointer,
            toolInput,
            cameraEventRouter
        )

        modelFile = resolveModelFile(startupArgs)
        updateWindowTitle()

        shapeRenderer = ShapeRenderer()
        spriteBatch = SpriteBatch()
        textFont = loadTextFont()
        setupLighting()
        setupUndoManager()
        loadModel()
        applyLightingSettings(lightingSettings)
        applyShadowSettings(shadowSettings)
        uiOverlay.refreshLightingControls()
        scene.setChangeListener { onModelChanged() }
        pluginHost.loadCatalog()
        pluginHost.reloadEnabledAndInit()
        uiOverlay.refreshPluginPanels()
        setupMeshes()
        setupRenderables()
        setupMcpServer()
        startConsoleIfRequested()
    }

    private fun configureOrthoViewport(width: Int, height: Int) {
        val safeHeight = height.coerceAtLeast(1)
        val aspect = width.coerceAtLeast(1).toFloat() / safeHeight.toFloat()
        val worldHeight = 22f
        orthoCamera.viewportHeight = worldHeight
        orthoCamera.viewportWidth = worldHeight * aspect
    }

    private fun setCameraMode(mode: CameraMode) {
        if (!::camera.isInitialized || !::walkCamera.isInitialized || !::orthoCamera.isInitialized) {
            return
        }
        if (activeCameraMode == mode && ::activeCamera.isInitialized) {
            return
        }
        when (mode) {
            CameraMode.ORBIT -> {
                copyPoseToPerspective(activeCameraOrNull(), camera, keepTarget = true)
                orbitCameraController.target.set(cameraTarget)
                activeCamera = camera
                activeCameraInputProcessor = orbitCameraController
            }

            CameraMode.WALKTHROUGH -> {
                copyPoseToPerspective(activeCameraOrNull(), walkCamera, keepTarget = false)
                walkCameraController.syncFromCamera()
                activeCamera = walkCamera
                activeCameraInputProcessor = walkCameraController
            }

            CameraMode.ORTHOGRAPHIC -> {
                updateTargetFromPerspective(activeCameraOrNull())
                activeCamera = orthoCamera
                activeCameraInputProcessor = orthoCameraController
                orthoCamera.position.set(cameraTarget).sub(Vector3(orthoCamera.direction).nor().scl(orthoDistance))
                orthoCamera.update()
            }
        }
        activeCameraMode = mode
        if (::snapper.isInitialized) {
            snapper.setCamera(activeCamera)
        }
    }

    private fun updateActiveCamera(deltaTime: Float) {
        when (activeCameraMode) {
            CameraMode.ORBIT -> {
                orbitCameraController.update()
                cameraTarget.set(orbitCameraController.target)
                camera.up.set(0f, 1f, 0f)
                camera.lookAt(cameraTarget)
                camera.update()
            }

            CameraMode.WALKTHROUGH -> {
                walkCameraController.update(deltaTime)
                cameraTarget.set(walkCamera.position).mulAdd(walkCamera.direction, 8f)
                walkCamera.update()
            }

            CameraMode.ORTHOGRAPHIC -> {
                orthoCameraController.update()
                orthoCamera.update()
            }
        }
    }

    private fun activeCameraOrNull(): Camera? {
        return if (::activeCamera.isInitialized) activeCamera else null
    }

    private fun updateTargetFromPerspective(source: Camera?) {
        val perspective = source as? PerspectiveCamera ?: return
        if (perspective === camera) {
            return
        }
        cameraTarget.set(perspective.position).mulAdd(perspective.direction, 8f)
    }

    private fun copyPoseToPerspective(source: Camera?, target: PerspectiveCamera, keepTarget: Boolean) {
        when (source) {
            is PerspectiveCamera -> {
                target.position.set(source.position)
                target.direction.set(source.direction).nor()
                target.up.set(source.up).nor()
                if (keepTarget) {
                    cameraTarget.set(source.position).mulAdd(source.direction, 8f)
                }
            }

            is OrthographicCamera -> {
                val dir = Vector3(source.direction).nor()
                if (dir.len2() <= 1e-8f) {
                    dir.set(0f, -1f, 0f)
                }
                target.position.set(cameraTarget).sub(dir.scl(12f))
                target.up.set(source.up).nor()
                target.lookAt(cameraTarget)
            }

            else -> {
                target.lookAt(cameraTarget)
            }
        }
        target.update()
    }

    private fun setOrthographicView(view: OrthoView) {
        setCameraMode(CameraMode.ORTHOGRAPHIC)
        alignOrthographicView(view)
        statusModel.message = "Orthographic ${view.name.lowercase()} view."
    }

    private fun alignOrthographicView(view: OrthoView) {
        val direction = Vector3()
        val up = Vector3()
        when (view) {
            OrthoView.TOP -> {
                direction.set(0f, -1f, 0f)
                up.set(0f, 0f, -1f)
            }

            OrthoView.BOTTOM -> {
                direction.set(0f, 1f, 0f)
                up.set(0f, 0f, 1f)
            }

            OrthoView.LEFT -> {
                direction.set(1f, 0f, 0f)
                up.set(0f, 1f, 0f)
            }

            OrthoView.RIGHT -> {
                direction.set(-1f, 0f, 0f)
                up.set(0f, 1f, 0f)
            }

            OrthoView.FRONT -> {
                direction.set(0f, 0f, -1f)
                up.set(0f, 1f, 0f)
            }

            OrthoView.BACK -> {
                direction.set(0f, 0f, 1f)
                up.set(0f, 1f, 0f)
            }
        }
        orthoCamera.direction.set(direction).nor()
        orthoCamera.up.set(up).nor()
        orthoCamera.position.set(cameraTarget).sub(direction.scl(orthoDistance))
        orthoCamera.update()
    }

    private fun registerOrthographicViewCommand(
        id: String,
        name: String,
        description: String,
        view: OrthoView
    ) {
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = id,
                name = name,
                description = description,
                icon = "view",
                category = "View",
                tags = listOf("camera", "orthographic", view.name.lowercase()),
                priority = 1,
                execute = {
                    setOrthographicView(view)
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
    }

    private fun syncCameraModesAfterOrbitStateChange() {
        if (!::walkCamera.isInitialized || !::orthoCamera.isInitialized) {
            return
        }
        copyPoseToPerspective(camera, walkCamera, keepTarget = false)
        when (activeCameraMode) {
            CameraMode.ORBIT -> {
                activeCamera = camera
                activeCameraInputProcessor = orbitCameraController
            }

            CameraMode.WALKTHROUGH -> {
                walkCameraController.syncFromCamera()
                activeCamera = walkCamera
                activeCameraInputProcessor = walkCameraController
            }

            CameraMode.ORTHOGRAPHIC -> {
                orthoCamera.position.set(cameraTarget).sub(Vector3(orthoCamera.direction).nor().scl(orthoDistance))
                orthoCamera.update()
                activeCamera = orthoCamera
                activeCameraInputProcessor = orthoCameraController
            }
        }
        if (::snapper.isInitialized) {
            snapper.setCamera(activeCamera)
        }
    }

    private fun setupMcpServer() {
        mcpServer = McpHttpServer(
            initialPort = mcpPort,
            stdoutTap = stdoutTap,
            listCommands = ::listPaletteCommandsForMcp,
            executeCommand = ::executePaletteCommandForMcp,
            executeConsoleCommand = ::executeConsoleCommandForMcp,
            dispatchPointerEvent = ::dispatchPointerEventForMcp,
            contractProvider = ::buildMcpContractForMcp,
            sceneSummaryProvider = ::buildMcpSceneSummaryForMcp,
            selectionSummaryProvider = ::buildMcpSelectionSummaryForMcp
        )
        val message = mcpServer.start()
        mcpPort = mcpServer.port()
        statusModel.message = message
        println(message)
    }

    private fun startMcpServer(): String {
        if (!::mcpServer.isInitialized) {
            return "MCP HTTP server is not initialized."
        }
        val message = mcpServer.start()
        mcpPort = mcpServer.port()
        return message
    }

    private fun stopMcpServer(): String {
        if (!::mcpServer.isInitialized) {
            return "MCP HTTP server is not initialized."
        }
        return mcpServer.stop()
    }

    private fun mcpServerStatus(): String {
        if (!::mcpServer.isInitialized) {
            return "MCP HTTP server is not initialized."
        }
        return mcpServer.status()
    }

    private fun mcpServerPort(): Int {
        return if (::mcpServer.isInitialized) mcpServer.port() else mcpPort
    }

    private fun setMcpServerPort(port: Int): String {
        if (!::mcpServer.isInitialized) {
            mcpPort = port.coerceIn(1, 65535)
            return "MCP HTTP server port set to $mcpPort (server not initialized)."
        }
        val message = mcpServer.setPort(port)
        mcpPort = mcpServer.port()
        return message
    }

    private fun buildMcpContractForMcp(): String {
        val version = K3DVersion()
        val commands = listPaletteCommandsForMcp()
            .sortedBy { it.id }
            .map { command ->
                linkedMapOf<String, Any?>(
                    "id" to command.id,
                    "name" to command.name,
                    "category" to command.category,
                    "description" to command.description,
                    "icon" to command.icon,
                    "priority" to command.priority,
                    "tags" to command.tags
                )
            }
        val payload = linkedMapOf<String, Any?>(
            "success" to true,
            "contractVersion" to "1.0.0",
            "name" to "k3d-mcp-contract",
            "generatedAt" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "transport" to linkedMapOf(
                "protocol" to "http",
                "host" to "127.0.0.1",
                "port" to mcpServerPort()
            ),
            "engine" to linkedMapOf(
                "buildVersion" to version.buildVersion,
                "buildGitTag" to version.buildGitTag,
                "buildGitCommit" to version.buildGitCommit,
                "buildGitBranch" to version.buildGitBranch,
                "buildDate" to version.buildDate
            ),
            "endpoints" to listOf(
                linkedMapOf(
                    "path" to "/mcp/status",
                    "methods" to listOf("GET"),
                    "summary" to "MCP HTTP status and port.",
                    "responseShape" to linkedMapOf(
                        "success" to "boolean",
                        "running" to "boolean",
                        "port" to "number",
                        "message" to "string"
                    )
                ),
                linkedMapOf(
                    "path" to "/mcp/contract",
                    "methods" to listOf("GET"),
                    "summary" to "Self-describing MCP contract for agents and plugin developers."
                ),
                linkedMapOf(
                    "path" to "/scene/listCommands",
                    "aliases" to listOf("/scene/commands"),
                    "methods" to listOf("GET"),
                    "summary" to "List executable command IDs (built-in + plugin)."
                ),
                linkedMapOf(
                    "path" to "/scene/command",
                    "methods" to listOf("GET", "POST"),
                    "summary" to "Execute a command by ID.",
                    "requestShape" to linkedMapOf(
                        "query" to linkedMapOf("id" to "string"),
                        "postJson" to linkedMapOf("id" to "string"),
                        "postText" to "command id as plain text"
                    ),
                    "responseShape" to linkedMapOf(
                        "success" to "boolean",
                        "commandId" to "string",
                        "message" to "string",
                        "durationMs" to "number",
                        "stdout" to "string",
                        "stdoutLines" to "string[]"
                    )
                ),
                linkedMapOf(
                    "path" to "/scene/console",
                    "aliases" to listOf("/scene/meta"),
                    "methods" to listOf("GET", "POST"),
                    "summary" to "Execute Groovy console script/command.",
                    "requestShape" to linkedMapOf(
                        "query" to linkedMapOf("cmd|command|script" to "string"),
                        "postJson" to linkedMapOf("cmd|command|script" to "string"),
                        "postText" to "groovy source as plain text"
                    ),
                    "responseShape" to linkedMapOf(
                        "success" to "boolean",
                        "command" to "string",
                        "message" to "string",
                        "durationMs" to "number",
                        "outputLines" to "string[]",
                        "stdout" to "string",
                        "stdoutLines" to "string[]"
                    )
                ),
                linkedMapOf(
                    "path" to "/scene/pointer",
                    "methods" to listOf("GET", "POST"),
                    "summary" to "Dispatch pointer events in screen or world coordinates.",
                    "requestShape" to linkedMapOf(
                        "action" to "down|move|up",
                        "pointer" to "number (default 0)",
                        "button" to "left|right|middle|lmb|rmb|mmb|0|1|2 (default left)",
                        "requiredOneOf" to listOf(
                            listOf("screenX", "screenY"),
                            listOf("worldX", "worldY", "worldZ")
                        ),
                        "optional" to listOf("normalX", "normalY", "normalZ", "valid")
                    ),
                    "responseShape" to linkedMapOf(
                        "success" to "boolean",
                        "handled" to "boolean",
                        "action" to "string",
                        "pointer" to "number",
                        "button" to "number",
                        "screenX" to "number|null",
                        "screenY" to "number|null",
                        "worldX" to "number|null",
                        "worldY" to "number|null",
                        "worldZ" to "number|null",
                        "normalX" to "number|null",
                        "normalY" to "number|null",
                        "normalZ" to "number|null",
                        "valid" to "boolean",
                        "message" to "string",
                        "durationMs" to "number"
                    )
                )
            ),
            "errors" to linkedMapOf(
                "400" to "Invalid payload / missing required fields",
                "405" to "Method not allowed",
                "500" to "Execution or runtime failure"
            ),
            "executionBoundary" to linkedMapOf(
                "mutationBoundary" to "Use app.run { ... } for thread-safe deferred mutation from Groovy.",
                "consoleThreadingNote" to "MCP /scene/console executes on the render thread; nested app.run schedules async work.",
                "pointerTimeoutMs" to 5000,
                "commandTimeoutMs" to 20000,
                "consoleTimeoutMs" to 30000
            ),
            "groovyBindings" to listOf(
                linkedMapOf(
                    "name" to "app",
                    "type" to "AppFacade",
                    "members" to listOf("run(block)", "exit()")
                ),
                linkedMapOf(
                    "name" to "scene",
                    "type" to "GroupScene",
                    "highValueMembers" to listOf(
                        "activeGroup()",
                        "enterGroup(group)",
                        "exitGroup()",
                        "clearAllSelections()",
                        "selectedGroups()",
                        "resetScene()",
                        "rootPrototype()",
                        "createVoxelGroup()",
                        "hasArchitectureElements()"
                    )
                ),
                linkedMapOf(
                    "name" to "selection",
                    "type" to "SelectionFacade",
                    "members" to listOf("clear()", "groups()", "faces()", "edges()", "dimensions()", "texts()")
                ),
                linkedMapOf(
                    "name" to "console",
                    "type" to "ConsoleUtils",
                    "members" to listOf("log(...)", "dir(obj)", "type(obj)", "exception(ex)")
                ),
                linkedMapOf(
                    "name" to "cameraCtl",
                    "type" to "CameraFacade",
                    "members" to listOf(
                        "position()",
                        "target()",
                        "setPosition(x,y,z)",
                        "setTarget(x,y,z)",
                        "lookAt(x,y,z)"
                    )
                ),
                linkedMapOf(
                    "name" to "unit",
                    "type" to "UnitFacade",
                    "members" to listOf("name()", "size()", "set(name,size)", "setName(name)", "setSize(size)")
                ),
                linkedMapOf(
                    "name" to "save",
                    "type" to "SaveFacade",
                    "members" to listOf("path()", "name()", "set(path)")
                ),
                linkedMapOf(
                    "name" to "lightingCtl",
                    "type" to "LightingFacade",
                    "members" to listOf("lighting()", "shadow()", "apply()")
                ),
                linkedMapOf(
                    "name" to "status",
                    "type" to "StatusModel",
                    "highValueFields" to listOf("message", "paintColor", "activeTool", "cursorWorld", "cursorSnapLabel")
                ),
                linkedMapOf("name" to "pluginHost", "type" to "PluginHost"),
                linkedMapOf("name" to "camera", "type" to "PerspectiveCamera"),
                linkedMapOf("name" to "cameraTarget", "type" to "Vector3"),
                linkedMapOf("name" to "lighting", "type" to "LightingSettings"),
                linkedMapOf("name" to "shadow", "type" to "ShadowSettings"),
                linkedMapOf("name" to "mcp", "type" to "McpFacade"),
                linkedMapOf("name" to "version", "type" to "K3DVersion")
            ),
            "bindingDiscovery" to linkedMapOf(
                "listBindings" to "GET /scene/console?cmd=:list",
                "listBindingMembers" to "GET /scene/console?cmd=:list <bindingName>"
            ),
            "nonBoundGlobals" to listOf(
                "guideManager",
                "toolController"
            ),
            "commandCatalog" to linkedMapOf(
                "source" to "/scene/listCommands",
                "count" to commands.size,
                "commands" to commands
            ),
            "businessUseCases" to listOf(
                linkedMapOf(
                    "id" to "coding_agent_plugin_development",
                    "title" to "Coding agent developing a plugin",
                    "flow" to listOf(
                        "GET /mcp/contract",
                        "GET /scene/listCommands",
                        "Use /scene/console to inspect bindings and pluginHost API",
                        "Register plugin commands/tools under plugin namespace",
                        "Execute new commands through /scene/command"
                    )
                ),
                linkedMapOf(
                    "id" to "designer_agent_modeling",
                    "title" to "Designer agent generating app.run scripts to model geometry",
                    "flow" to listOf(
                        "GET /mcp/contract",
                        "Switch camera: /scene/command?id=view.camera.orbit",
                        "Use Y-up world coordinates in /scene/pointer or /scene/console app.run",
                        "Capture progress with /scene/command?id=export.screenshot"
                    )
                ),
                linkedMapOf(
                    "id" to "plugin_developer",
                    "title" to "Plugin developer creating plugins/tools/commands/entities",
                    "flow" to listOf(
                        "Consult plugin contract via groovy bindings and pluginHost in contract",
                        "Implement Plugin + capability interfaces",
                        "Return PluginResult with explicit PluginChange objects"
                    )
                ),
                linkedMapOf(
                    "id" to "test_automation",
                    "title" to "Test automation",
                    "flow" to listOf(
                        "GET /mcp/status for readiness",
                        "GET /scene/listCommands and assert required IDs",
                        "Reset deterministic scene using command or app.run script",
                        "Replay pointer/command steps",
                        "Capture and compare screenshots or assert console/model outputs"
                    )
                )
            )
        )
        return toMcpJson(payload)
    }

    private fun buildMcpSceneSummaryForMcp(): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        Gdx.app.postRunnable {
            try {
                val root = scene.root
                var groupCount = 0
                var edgeCount = root.lineStore.getSegments().size
                var faceCount = root.faceStore.getTriangles().size
                var voxelCount = root.voxelStore?.all()?.size ?: 0
                scene.walkGroups(root) { group ->
                    groupCount++
                    edgeCount += group.lineStore.getSegments().size
                    faceCount += group.faceStore.getTriangles().size
                    voxelCount += group.voxelStore?.all()?.size ?: 0
                }
                val payload = linkedMapOf<String, Any?>(
                    "success" to true,
                    "generatedAt" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "activeTool" to linkedMapOf(
                        "id" to toolController.activeToolId().name,
                        "name" to toolController.activeToolId().displayName
                    ),
                    "activeCameraMode" to activeCameraMode.name,
                    "activeGroup" to linkedMapOf(
                        "id" to scene.activeGroup().id,
                        "name" to scene.activeGroup().name,
                        "isEditing" to scene.isEditing()
                    ),
                    "counts" to linkedMapOf(
                        "groups" to groupCount,
                        "groupsIncludingRoot" to groupCount + 1,
                        "faces" to faceCount,
                        "edges" to edgeCount,
                        "voxels" to voxelCount
                    )
                )
                result.set(toMcpJson(payload))
            } catch (t: Throwable) {
                result.set(
                    toMcpJson(
                        linkedMapOf(
                            "success" to false,
                            "message" to "Scene summary failed: ${t.message ?: t.javaClass.simpleName}"
                        )
                    )
                )
            } finally {
                latch.countDown()
            }
        }
        val ok = latch.await(3, TimeUnit.SECONDS)
        if (!ok) {
            return toMcpJson(linkedMapOf("success" to false, "message" to "Scene summary timed out."))
        }
        return result.get() ?: toMcpJson(linkedMapOf("success" to false, "message" to "Scene summary unavailable."))
    }

    private fun buildMcpSelectionSummaryForMcp(): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        Gdx.app.postRunnable {
            try {
                val info = selectionInfo()
                val active = scene.activeGroup()
                val selectedGroups = scene.selectedGroups().map { group ->
                    linkedMapOf(
                        "id" to group.id,
                        "name" to group.name
                    )
                }
                val selectedVoxels = scene.selectedVoxels(active)
                    .take(128)
                    .map { key -> linkedMapOf("x" to key.x, "y" to key.y, "z" to key.z) }
                val selectedArch = scene.root.architectureStore
                    ?.selectedElements()
                    ?.take(128)
                    ?.map { element ->
                        linkedMapOf(
                            "kind" to element.kind.name,
                            "id" to element.id
                        )
                    }
                val payload = linkedMapOf<String, Any?>(
                    "success" to true,
                    "generatedAt" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "activeGroup" to linkedMapOf(
                        "id" to active.id,
                        "name" to active.name,
                        "kind" to active.kind.name
                    ),
                    "counts" to linkedMapOf(
                        "edges" to info.edgeCount,
                        "faces" to info.faceCount,
                        "voxels" to info.voxelCount,
                        "groups" to info.groupCount,
                        "dimensions" to info.dimensionCount,
                        "texts" to info.textCount
                    ),
                    "selectedGroups" to selectedGroups,
                    "selectedVoxelsSample" to selectedVoxels,
                    "selectedArchitectureElements" to selectedArch,
                    "selectedText" to linkedMapOf(
                        "id" to info.selectedTextId,
                        "value" to info.selectedTextValue,
                        "size" to info.selectedTextSize,
                        "screenText" to info.selectedTextScreen
                    )
                )
                result.set(toMcpJson(payload))
            } catch (t: Throwable) {
                result.set(
                    toMcpJson(
                        linkedMapOf(
                            "success" to false,
                            "message" to "Selection summary failed: ${t.message ?: t.javaClass.simpleName}"
                        )
                    )
                )
            } finally {
                latch.countDown()
            }
        }
        val ok = latch.await(3, TimeUnit.SECONDS)
        if (!ok) {
            return toMcpJson(linkedMapOf("success" to false, "message" to "Selection summary timed out."))
        }
        return result.get() ?: toMcpJson(linkedMapOf("success" to false, "message" to "Selection summary unavailable."))
    }

    private fun toMcpJson(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${escapeMcpJsonString(value)}\""
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                "\"${escapeMcpJsonString(k.toString())}\":${toMcpJson(v)}"
            }
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { toMcpJson(it) }
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { toMcpJson(it) }
            else -> "\"${escapeMcpJsonString(value.toString())}\""
        }
    }

    private fun escapeMcpJsonString(value: String): String {
        val out = StringBuilder(value.length + 16)
        value.forEach { ch ->
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun listPaletteCommandsForMcp(): List<com.github.alfu32.sketch.plugin.PaletteCommand> {
        if (!::pluginHost.isInitialized) {
            return emptyList()
        }
        val latch = CountDownLatch(1)
        val result = AtomicReference<List<com.github.alfu32.sketch.plugin.PaletteCommand>>(emptyList())
        Gdx.app.postRunnable {
            try {
                result.set(pluginHost.getCommandPalette().allCommands())
            } finally {
                latch.countDown()
            }
        }
        val ok = latch.await(3, TimeUnit.SECONDS)
        return if (ok) result.get() else emptyList()
    }

    private fun executePaletteCommandForMcp(commandId: String): com.github.alfu32.sketch.plugin.PluginResult {
        if (!::pluginHost.isInitialized) {
            return com.github.alfu32.sketch.plugin.PluginResult.failure("Plugin host not initialized.")
        }
        val latch = CountDownLatch(1)
        val result = AtomicReference<com.github.alfu32.sketch.plugin.PluginResult?>()
        val error = AtomicReference<Throwable?>()
        Gdx.app.postRunnable {
            try {
                result.set(pluginHost.getCommandPalette().executeCommand(commandId))
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        val ok = latch.await(20, TimeUnit.SECONDS)
        if (!ok) {
            return com.github.alfu32.sketch.plugin.PluginResult.failure("Command timed out: $commandId")
        }
        val thrown = error.get()
        if (thrown != null) {
            return com.github.alfu32.sketch.plugin.PluginResult.failure(
                "Command crashed: ${thrown.message ?: thrown.javaClass.simpleName}"
            )
        }
        return result.get() ?: com.github.alfu32.sketch.plugin.PluginResult.failure("Command produced no result.")
    }

    private fun executeConsoleCommandForMcp(source: String): McpConsoleResult {
        val command = source.trim()
        if (command.isBlank()) {
            return McpConsoleResult(success = false, message = "Empty command.")
        }
        val latch = CountDownLatch(1)
        val result = AtomicReference<ConsoleExecutionResult?>()
        val error = AtomicReference<Throwable?>()
        Gdx.app.postRunnable {
            try {
                val tui = ensureMcpConsoleTui()
                result.set(tui.executeForMcp(command))
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        val ok = latch.await(30, TimeUnit.SECONDS)
        if (!ok) {
            return McpConsoleResult(success = false, message = "Console command timed out.")
        }
        val thrown = error.get()
        if (thrown != null) {
            return McpConsoleResult(
                success = false,
                message = "Console command crashed: ${thrown.message ?: thrown.javaClass.simpleName}"
            )
        }
        val execResult = result.get() ?: return McpConsoleResult(
            success = false,
            message = "Console command produced no result."
        )
        return McpConsoleResult(
            success = execResult.success,
            message = execResult.message,
            outputLines = execResult.outputLines
        )
    }

    private fun ensureMcpConsoleTui(): ConsoleTui {
        mcpConsoleTui?.let { return it }
        val outputPane = OutputPane()
        val history = HistoryManager(ConsolePaths.historyFile())
        val runtime = buildConsoleRuntime(outputPane)
        val tui = ConsoleTui(runtime, TerminalController(), outputPane, history, {}, {})
        mcpConsoleRuntime = runtime
        mcpConsoleTui = tui
        return tui
    }

    private fun dispatchPointerEventForMcp(request: McpPointerEventRequest): McpPointerEventResult {
        val latch = CountDownLatch(1)
        val result = AtomicReference<McpPointerEventResult?>()
        val error = AtomicReference<Throwable?>()
        Gdx.app.postRunnable {
            try {
                result.set(dispatchPointerEventOnRenderThread(request))
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        val ok = latch.await(5, TimeUnit.SECONDS)
        if (!ok) {
            return McpPointerEventResult(
                success = false,
                handled = false,
                message = "Pointer event timed out.",
                action = request.action,
                pointer = request.pointer,
                button = request.button
            )
        }
        val thrown = error.get()
        if (thrown != null) {
            return McpPointerEventResult(
                success = false,
                handled = false,
                message = "Pointer event crashed: ${thrown.message ?: thrown.javaClass.simpleName}",
                action = request.action,
                pointer = request.pointer,
                button = request.button
            )
        }
        return result.get() ?: McpPointerEventResult(
            success = false,
            handled = false,
            message = "Pointer event produced no result.",
            action = request.action,
            pointer = request.pointer,
            button = request.button
        )
    }

    private fun dispatchPointerEventOnRenderThread(request: McpPointerEventRequest): McpPointerEventResult {
        val action = request.action.lowercase()
        val hasScreen = request.screenX != null && request.screenY != null
        val hasWorld = request.worldX != null && request.worldY != null && request.worldZ != null
        if (!hasScreen && !hasWorld) {
            return McpPointerEventResult(
                success = false,
                handled = false,
                message = "Provide screenX/screenY and/or worldX/worldY/worldZ.",
                action = action,
                pointer = request.pointer,
                button = request.button
            )
        }
        if (action != "down" && action != "move" && action != "up") {
            return McpPointerEventResult(
                success = false,
                handled = false,
                message = "Unsupported action: ${request.action}",
                action = action,
                pointer = request.pointer,
                button = request.button
            )
        }

        val pointer = request.pointer.coerceAtLeast(0)
        val button = request.button
        var screenX = request.screenX
        var screenY = request.screenY
        val world = if (hasWorld) {
            Vector3(request.worldX!!, request.worldY!!, request.worldZ!!)
        } else {
            null
        }
        val explicitNormal = if (
            request.normalX != null &&
            request.normalY != null &&
            request.normalZ != null
        ) {
            Vector3(request.normalX, request.normalY, request.normalZ).nor()
        } else {
            null
        }

        var eventWorld: Vector3? = world?.cpy()
        var eventNormal: Vector3? = explicitNormal?.cpy()
        var eventValid = request.valid ?: hasWorld
        val pureScreenEvent = hasScreen && !hasWorld && explicitNormal == null && request.valid == null
        if (hasScreen) {
            val snap = snapper.compute(screenX!!, screenY!!)
            if (!hasWorld) {
                eventWorld = snap.world?.let { Vector3(it) }
                eventValid = snap.valid
            }
            if (eventNormal == null) {
                eventNormal = snap.normal?.let { Vector3(it) }
            }
        }
        if (eventNormal == null && eventWorld != null) {
            eventNormal = Vector3(0f, 1f, 0f)
        }
        if (!hasScreen && eventWorld != null) {
            val projected = activeCamera.project(Vector3(eventWorld))
            screenX = projected.x.toInt()
            screenY = (Gdx.graphics.height - projected.y).toInt()
        }

        val handled = if (pureScreenEvent && hasScreen) {
            when (action) {
                "down" -> toolPointer.touchDown(screenX!!, screenY!!, pointer, button)
                "up" -> toolPointer.touchUp(screenX!!, screenY!!, pointer, button)
                else -> {
                    toolPointer.touchDragged(screenX!!, screenY!!, pointer)
                    true
                }
            }
        } else {
            when (action) {
                "down" -> toolController.pointerDown(eventWorld, eventNormal, eventValid, button)
                "up" -> toolController.pointerUp(eventWorld, eventNormal, eventValid, button)
                else -> {
                    toolController.pointerMoved(eventWorld, eventNormal, eventValid)
                    true
                }
            }
        }

        return McpPointerEventResult(
            success = true,
            handled = handled,
            message = "Pointer $action dispatched.",
            action = action,
            pointer = pointer,
            button = button,
            screenX = screenX,
            screenY = screenY,
            worldX = eventWorld?.x,
            worldY = eventWorld?.y,
            worldZ = eventWorld?.z,
            normalX = eventNormal?.x,
            normalY = eventNormal?.y,
            normalZ = eventNormal?.z,
            valid = eventValid
        )
    }

    private fun walkSupportHeightAt(worldX: Float, worldZ: Float, currentY: Float): Float {
        val rayOrigin = Vector3(worldX, currentY + 0.25f, worldZ)
        val rayDir = Vector3(0f, -1f, 0f)
        var bestY: Float? = null

        fun testGroup(group: GroupScene.GroupNode) {
            val localRay = com.badlogic.gdx.math.collision.Ray(
                group.toLocal(rayOrigin),
                group.vectorToLocal(rayDir).nor()
            )
            val hit = group.faceStore.pickTriangle(localRay) ?: return
            val worldHit = group.toWorld(hit.point)
            if (worldHit.y <= rayOrigin.y + 1e-3f) {
                bestY = if (bestY == null) {
                    worldHit.y
                } else {
                    kotlin.math.max(bestY!!, worldHit.y)
                }
            }
        }

        testGroup(scene.root)
        scene.walkGroups(scene.root) { group ->
            testGroup(group)
        }
        return bestY ?: 0f
    }

    private fun pickPanPoint(screenX: Int, screenY: Int): Vector3? {
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        val group = scene.activeGroup()
        val localRay = com.badlogic.gdx.math.collision.Ray(
            group.toLocal(ray.origin),
            group.vectorToLocal(ray.direction).nor()
        )
        val faceHit = group.faceStore.pickTriangle(localRay)
        if (faceHit != null) {
            return group.toWorld(faceHit.point)
        }
        val dirY = ray.direction.y
        if (kotlin.math.abs(dirY) < 1e-6f) {
            return null
        }
        val t = -ray.origin.y / dirY
        if (t <= 0f) {
            return null
        }
        return Vector3(ray.origin).mulAdd(ray.direction, t)
    }

    override fun render() {
        updateActiveCamera(Gdx.graphics.deltaTime)
        handleGlobalDistanceShortcut()
        updateCursorStatus()
        undoManager.update()
        pluginHost.dispatchUpdate(Gdx.graphics.deltaTime)
        toolController.update(Gdx.graphics.deltaTime)

        updateFaceMesh()
        shadowLight.update(activeCamera)
        renderShadowPass()

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        val skyColor=Color(0.6f,0.75f,0.9f,1f,)
        Gdx.gl.glClearColor(0.6f, 0.75f, 0.9f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        Gdx.gl.glLineWidth(2f)
        shapeRenderer.projectionMatrix = activeCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawGrid(20, gridSpacing)
        shapeRenderer.end()

        modelBatch.begin(activeCamera)
        modelBatch.render(faceBackRenderable, environment)
        modelBatch.render(faceFrontRenderable, environment)
        modelBatch.render(groundRenderable, environment)
        modelBatch.end()

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawAxes(2.5f)
        drawActiveGroupAxes(1.8f)
        drawCameraTarget(1f)
        drawGuides()
        drawCursor()
        drawSelectionHighlights()
        drawDraftLines()
        drawArchitectureHoleGuides()
        drawArchitectureWallEndpointHitAreas()
        drawArchitectureConstructionHotspots()
        drawDimensions()
        toolController.render(shapeRenderer)
        drawActiveToolMeasurementLine()
        drawPluginLines()
        shapeRenderer.end()

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        drawAnnotations2D()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        val windowRect = (toolController.activeTool() as? SelectTool)
            ?.windowRect(Gdx.graphics.width, Gdx.graphics.height)
        if (windowRect != null) {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            shapeRenderer.projectionMatrix = uiOverlay.stage.camera.combined
            shapeRenderer.transformMatrix = Matrix4().idt()
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color(0.25f, 0.55f, 0.95f, 0.18f)
            shapeRenderer.rect(windowRect.x, windowRect.y, windowRect.width, windowRect.height)
            shapeRenderer.end()
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.color = Color(0.25f, 0.55f, 0.95f, 0.9f)
            if (windowRect.dashed) {
                drawDashedRect(
                    shapeRenderer,
                    windowRect.x,
                    windowRect.y,
                    windowRect.width,
                    windowRect.height,
                    6f,
                    4f
                )
            } else {
                shapeRenderer.rect(windowRect.x, windowRect.y, windowRect.width, windowRect.height)
            }
            shapeRenderer.end()
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        }

        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        uiOverlay.stage.viewport.apply()
        uiOverlay.act(Gdx.graphics.deltaTime)
        uiOverlay.draw()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
        walkCamera.viewportWidth = width.toFloat()
        walkCamera.viewportHeight = height.toFloat()
        walkCamera.update()
        configureOrthoViewport(width, height)
        orthoCamera.update()
        uiOverlay.resize(width, height)
        updateWindowTitle()
    }

    override fun dispose() {
        if (::modelFile.isInitialized && ::scene.isInitialized && ::camera.isInitialized &&
            ::lightingSettings.isInitialized && ::shadowSettings.isInitialized
        ) {
            saveModel()
        }
        if (::pluginHost.isInitialized) {
            pluginHost.dispatchClose()
        }
        if (::mcpServer.isInitialized) {
            mcpServer.stop()
        }
        consoleThread?.shutdown()
        try {
            consoleThread?.join(500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        shapeRenderer.dispose()
        faceMesh.dispose()
        groundMesh.dispose()
        modelBatch.dispose()
        shadowBatch.dispose()
        shadowLight.dispose()
        uiOverlay.dispose()
        spriteBatch.dispose()
        if (ownsTextFont) {
            textFont.dispose()
        }
        if (VisUI.isLoaded()) {
            VisUI.dispose()
        }
    }

    private fun loadTextFont(): BitmapFont {
        if (VisUI.isLoaded()) {
            val skin = VisUI.getSkin()
            if (skin.has("mono", BitmapFont::class.java)) {
                ownsTextFont = false
                return skin.get("mono", BitmapFont::class.java)
            }
            if (skin.has("default-font", BitmapFont::class.java)) {
                ownsTextFont = false
                return skin.get("default-font", BitmapFont::class.java)
            }
        }
        ownsTextFont = true
        return BitmapFont()
    }

    private fun drawGrid(halfSize: Int, step: Float) {
        shapeRenderer.color = Color(0.35f, 0.35f, 0.35f, 1f)
        for (i in -halfSize..halfSize) {
            val offset = i * step
            shapeRenderer.line(-halfSize * step, 0f, offset, halfSize * step, 0f, offset)
            shapeRenderer.line(offset, 0f, -halfSize * step, offset, 0f, halfSize * step)
        }
    }

    private fun drawAxes(length: Float) {
        shapeRenderer.color = Color(0.85f, 0.25f, 0.25f, 1f)
        shapeRenderer.line(0f, 0f, 0f, length, 0f, 0f)
        shapeRenderer.color = Color(0.25f, 0.85f, 0.35f, 1f)
        shapeRenderer.line(0f, 0f, 0f, 0f, length, 0f)
        shapeRenderer.color = Color(0.35f, 0.45f, 0.95f, 1f)
        shapeRenderer.line(0f, 0f, 0f, 0f, 0f, length)
    }

    private fun drawActiveGroupAxes(length: Float) {
        val group = scene.activeGroup()
        if (group === scene.root) {
            return
        }
        val origin = group.worldOrigin()
        val axes = group.worldAxes()
        val uEnd = Vector3(origin).mulAdd(axes.u.nor(), length)
        val vEnd = Vector3(origin).mulAdd(axes.v.nor(), length)
        val wEnd = Vector3(origin).mulAdd(axes.w.nor(), length)
        shapeRenderer.color = Color(0.85f, 0.25f, 0.25f, 1f)
        shapeRenderer.line(origin, uEnd)
        shapeRenderer.color = Color(0.25f, 0.85f, 0.35f, 1f)
        shapeRenderer.line(origin, vEnd)
        shapeRenderer.color = Color(0.35f, 0.45f, 0.95f, 1f)
        shapeRenderer.line(origin, wEnd)
    }

    private fun drawCameraTarget(size: Float) {
        val half = size * 0.5f
        shapeRenderer.color = Color(1f, 0.55f, 0.1f, 1f)
        shapeRenderer.line(
            cameraTarget.x - half, cameraTarget.y, cameraTarget.z,
            cameraTarget.x + half, cameraTarget.y, cameraTarget.z
        )
        shapeRenderer.line(
            cameraTarget.x, cameraTarget.y - half, cameraTarget.z,
            cameraTarget.x, cameraTarget.y + half, cameraTarget.z
        )
        shapeRenderer.line(
            cameraTarget.x, cameraTarget.y, cameraTarget.z - half,
            cameraTarget.x, cameraTarget.y, cameraTarget.z + half
        )
    }

    private fun drawCursor() {
        val snap = lastSnap ?: return
        val hit = snap.world ?: return
        val size = 0.24f
        shapeRenderer.color = Color(0.9f, 0.2f, 0.2f, 1f) // X
        shapeRenderer.line(hit.x - size, hit.y, hit.z, hit.x + size, hit.y, hit.z)
        shapeRenderer.color = Color(0.2f, 0.45f, 0.95f, 1f) // Y
        shapeRenderer.line(hit.x, hit.y - size, hit.z, hit.x, hit.y + size, hit.z)
        shapeRenderer.color = Color(0.2f, 0.85f, 0.3f, 1f) // Z
        shapeRenderer.line(hit.x, hit.y, hit.z - size, hit.x, hit.y, hit.z + size)
        if (snap.type != com.github.alfu32.sketch.input.SnapType.NONE) {
            val snapSize = 0.12f
            shapeRenderer.color = Color(1f, 0.95f, 0.6f, 1f)
            shapeRenderer.line(hit.x - snapSize, hit.y, hit.z, hit.x + snapSize, hit.y, hit.z)
            shapeRenderer.line(hit.x, hit.y - snapSize, hit.z, hit.x, hit.y + snapSize, hit.z)
            shapeRenderer.line(hit.x, hit.y, hit.z - snapSize, hit.x, hit.y, hit.z + snapSize)
        }
    }

    private fun drawGuides() {
        val extent = gridSpacing * 10f
        guideManager.getGridGuides().forEach { guide ->
            drawGuidePlane(guide.origin, guide.axisU, guide.axisV, extent)
            drawGuidePlane(guide.origin, guide.axisU, guide.axisW, extent)
            drawGuidePlane(guide.origin, guide.axisV, guide.axisW, extent)
        }
        guideManager.getAxisGuides().forEach { guide ->
            val origin = guide.origin
            val axisU = guide.axisU
            val axisV = guide.axisV
            val axisW = guide.axisW
            shapeRenderer.color = axisColor(axisU)
            val uStart = Vector3(origin).mulAdd(axisU, -extent)
            val uEnd = Vector3(origin).mulAdd(axisU, extent)
            shapeRenderer.line(uStart.x, uStart.y, uStart.z, uEnd.x, uEnd.y, uEnd.z)
            shapeRenderer.color = axisColor(axisV)
            val vStart = Vector3(origin).mulAdd(axisV, -extent)
            val vEnd = Vector3(origin).mulAdd(axisV, extent)
            shapeRenderer.line(vStart.x, vStart.y, vStart.z, vEnd.x, vEnd.y, vEnd.z)
            shapeRenderer.color = axisColor(axisW)
            val wStart = Vector3(origin).mulAdd(axisW, -extent)
            val wEnd = Vector3(origin).mulAdd(axisW, extent)
            shapeRenderer.line(wStart.x, wStart.y, wStart.z, wEnd.x, wEnd.y, wEnd.z)
        }
    }

    private fun drawGuidePlane(origin: Vector3, axisU: Vector3, axisV: Vector3, extent: Float) {
        val steps = (extent / gridSpacing).toInt()
        for (i in -steps..steps) {
            val offset = i * gridSpacing
            val startU = Vector3(origin).mulAdd(axisU, -extent).mulAdd(axisV, offset)
            val endU = Vector3(origin).mulAdd(axisU, extent).mulAdd(axisV, offset)
            val startV = Vector3(origin).mulAdd(axisV, -extent).mulAdd(axisU, offset)
            val endV = Vector3(origin).mulAdd(axisV, extent).mulAdd(axisU, offset)

            shapeRenderer.color = axisColor(axisU)
            shapeRenderer.line(startU.x, startU.y, startU.z, endU.x, endU.y, endU.z)
            shapeRenderer.color = axisColor(axisV)
            shapeRenderer.line(startV.x, startV.y, startV.z, endV.x, endV.y, endV.z)
        }
    }

    private fun axisColor(axis: Vector3): Color {
        return when {
            axis.x > 0.5f -> Color(0.85f, 0.25f, 0.25f, 1f)
            axis.y > 0.5f -> Color(0.35f, 0.45f, 0.95f, 1f)
            else -> Color(0.25f, 0.85f, 0.35f, 1f)
        }
    }

    private fun drawDashedRect(
        renderer: ShapeRenderer,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        dash: Float,
        gap: Float
    ) {
        drawDashedLine(renderer, x, y, x + width, y, dash, gap)
        drawDashedLine(renderer, x + width, y, x + width, y + height, dash, gap)
        drawDashedLine(renderer, x + width, y + height, x, y + height, dash, gap)
        drawDashedLine(renderer, x, y + height, x, y, dash, gap)
    }

    private fun drawDashedLine(
        renderer: ShapeRenderer,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        dash: Float,
        gap: Float
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length <= 0.001f) {
            return
        }
        val step = dash + gap
        val nx = dx / length
        val ny = dy / length
        var dist = 0f
        while (dist < length) {
            val segment = kotlin.math.min(dash, length - dist)
            val sx = x1 + nx * dist
            val sy = y1 + ny * dist
            val ex = x1 + nx * (dist + segment)
            val ey = y1 + ny * (dist + segment)
            renderer.line(sx, sy, ex, ey)
            dist += step
        }
    }

    private fun showSvgExportDialog() {
        val chooser = FileChooser(System.getProperty("user.dir"), FileChooser.Mode.SAVE)
        chooser.getTitleLabel().setText("Export SVG (View)")
        chooser.setSelectionMode(FileChooser.SelectionMode.FILES)
        chooser.setDefaultFileName("view.svg")
        val filter = FileTypeFilter(true)
        filter.addRule("SVG", "svg")
        chooser.setFileTypeFilter(filter)
        chooser.setListener(object : FileChooserAdapter() {
            override fun selected(files: Array<FileHandle>?) {
                if (files == null || files.size == 0) {
                    return
                }
                val handle = files.first()
                val target = if (handle.extension().lowercase() == "svg") handle.file()
                else File(handle.file().parentFile, "${handle.file().name}.svg")
                exportSvgView(target)
            }
        })
        uiOverlay.stage.addActor(chooser)
    }

    private fun showIfcExportDialog() {
        val chooser = FileChooser(System.getProperty("user.dir"), FileChooser.Mode.SAVE)
        chooser.getTitleLabel().setText("Export IFC (Model)")
        chooser.setSelectionMode(FileChooser.SelectionMode.FILES)
        chooser.setDefaultFileName("model.ifc")
        val filter = FileTypeFilter(true)
        filter.addRule("IFC", "ifc")
        chooser.setFileTypeFilter(filter)
        chooser.setListener(object : FileChooserAdapter() {
            override fun selected(files: Array<FileHandle>?) {
                if (files == null || files.size == 0) {
                    return
                }
                val handle = files.first()
                val target = if (handle.extension().lowercase() == "ifc") handle.file()
                else File(handle.file().parentFile, "${handle.file().name}.ifc")
                exportIfcModel(target)
            }
        })
        uiOverlay.stage.addActor(chooser)
    }

    private fun exportIfcModel(file: File) {
        val unitScale = modelUnit.size.coerceAtLeast(1e-6f)
        try {
            val report = IfcExporter.export(scene, file, unitScale)
            statusModel.message = "Exported IFC to ${file.absolutePath} (${report.productCount} products)"
        } catch (t: Throwable) {
            statusModel.message = "IFC export failed: ${t.message ?: t.javaClass.simpleName}"
            t.printStackTrace()
        }
    }

    private fun saveScreenshotWithCurrentFileName(): File? {
        if (!::modelFile.isInitialized) {
            val failure = "Screenshot failed: current model file is not initialized."
            statusModel.message = failure
            println(failure)
            return null
        }
        val baseName = modelFile.nameWithoutExtension.ifBlank { "k3d" }
        val timestamp = LocalDateTime.now().format(screenshotTimestampFormatter)
        val fileName = "${baseName}_${timestamp}.png"
        val directory = modelFile.parentFile ?: File(".")
        val outFile = File(directory, fileName)
        var pixmap: Pixmap? = null
        var pngWriter: PixmapIO.PNG? = null
        return try {
            val width = Gdx.graphics.backBufferWidth.coerceAtLeast(1)
            val height = Gdx.graphics.backBufferHeight.coerceAtLeast(1)
            pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, width, height)
            pngWriter = PixmapIO.PNG((width * height * 1.5f).toInt().coerceAtLeast(1024))
            pngWriter?.setFlipY(true)
            pngWriter?.write(FileHandle(outFile), pixmap)
            val success = "Screenshot saved: ${outFile.absolutePath}"
            statusModel.message = success
            println(success)
            outFile
        } catch (t: Throwable) {
            val failure = "Screenshot failed: ${t.message ?: t.javaClass.simpleName}"
            statusModel.message = failure
            println(failure)
            null
        } finally {
            pngWriter?.dispose()
            pixmap?.dispose()
        }
    }

    private fun exportSvgView(file: File) {
        val viewCamera = activeCamera
        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        val view = Matrix4(viewCamera.view)
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""")
        sb.append("\n")
        fun toSvgY(y: Float): Float = height - y
        fun colorHex(color: Color): String {
            val r = (color.r.coerceIn(0f, 1f) * 255).toInt()
            val g = (color.g.coerceIn(0f, 1f) * 255).toInt()
            val b = (color.b.coerceIn(0f, 1f) * 255).toInt()
            return String.format("#%02x%02x%02x", r, g, b)
        }
        fun project(world: Vector3): Vector3? {
            val v = Vector3(world)
            viewCamera.project(v, 0f, 0f, width, height)
            if (!v.x.isFinite() || !v.y.isFinite() || !v.z.isFinite()) {
                return null
            }
            if (v.z < 0f || v.z > 1f) {
                return null
            }
            return v
        }
        fun lineSvg(a: Vector3, b: Vector3, color: Color, widthPx: Float = 1f) {
            val pa = project(a) ?: return
            val pb = project(b) ?: return
            val stroke = colorHex(color)
            val opacity = color.a.coerceIn(0f, 1f)
            sb.append("""<line x1="${pa.x}" y1="${toSvgY(pa.y)}" x2="${pb.x}" y2="${toSvgY(pb.y)}" stroke="$stroke" stroke-width="$widthPx" stroke-opacity="$opacity" />""")
            sb.append("\n")
        }
        fun triangleSvg(a: Vector3, b: Vector3, c: Vector3, color: Color) {
            val pa = project(a) ?: return
            val pb = project(b) ?: return
            val pc = project(c) ?: return
            val fill = colorHex(color)
            val opacity = color.a.coerceIn(0f, 1f)
            sb.append(
                """<polygon points="${pa.x},${toSvgY(pa.y)} ${pb.x},${toSvgY(pb.y)} ${pc.x},${toSvgY(pc.y)}" fill="$fill" fill-opacity="$opacity" stroke="none" />"""
            )
            sb.append("\n")
        }
        fun depth(point: Vector3): Float {
            val cam = Vector3(point).mul(view)
            return cam.z
        }
        fun collectGroups(): List<GroupScene.GroupNode> {
            val groups = mutableListOf<GroupScene.GroupNode>()
            groups.add(scene.root)
            scene.walkGroups(scene.root) { group -> groups.add(group) }
            return groups
        }

        // Grid
        val gridColor = Color(0.35f, 0.35f, 0.35f, 1f)
        val halfSize = 20
        for (i in -halfSize..halfSize) {
            val offset = i * gridSpacing
            lineSvg(Vector3(-halfSize * gridSpacing, 0f, offset), Vector3(halfSize * gridSpacing, 0f, offset), gridColor)
            lineSvg(Vector3(offset, 0f, -halfSize * gridSpacing), Vector3(offset, 0f, halfSize * gridSpacing), gridColor)
        }

        // Guides
        val extent = gridSpacing * 10f
        guideManager.getGridGuides().forEach { guide ->
            drawGuidePlaneSvg(guide.origin, guide.axisU, guide.axisV, extent, ::lineSvg)
            drawGuidePlaneSvg(guide.origin, guide.axisU, guide.axisW, extent, ::lineSvg)
            drawGuidePlaneSvg(guide.origin, guide.axisV, guide.axisW, extent, ::lineSvg)
        }
        guideManager.getAxisGuides().forEach { guide ->
            val origin = guide.origin
            val axisU = guide.axisU
            val axisV = guide.axisV
            val axisW = guide.axisW
            val uStart = Vector3(origin).mulAdd(axisU, -extent)
            val uEnd = Vector3(origin).mulAdd(axisU, extent)
            lineSvg(uStart, uEnd, axisColor(axisU))
            val vStart = Vector3(origin).mulAdd(axisV, -extent)
            val vEnd = Vector3(origin).mulAdd(axisV, extent)
            lineSvg(vStart, vEnd, axisColor(axisV))
            val wStart = Vector3(origin).mulAdd(axisW, -extent)
            val wEnd = Vector3(origin).mulAdd(axisW, extent)
            lineSvg(wStart, wEnd, axisColor(axisW))
        }

        // Faces (sorted far to near)
        data class FaceEntry(val a: Vector3, val b: Vector3, val c: Vector3, val color: Color, val depth: Float)
        val faces = mutableListOf<FaceEntry>()
        collectGroups().forEach { group ->
            group.faceStore.getTriangles().forEach { tri ->
                val a = group.toWorld(tri.a)
                val b = group.toWorld(tri.b)
                val c = group.toWorld(tri.c)
                val color = group.faceStore.colorFor(tri)
                val d = (depth(a) + depth(b) + depth(c)) / 3f
                faces.add(FaceEntry(a, b, c, color, d))
            }
        }
        faces.sortedBy { it.depth }.forEach { tri ->
            triangleSvg(tri.a, tri.b, tri.c, tri.color)
        }

        // Edges
        val edgeColor = Color(0.1f, 0.1f, 0.1f, 1f)
        collectGroups().forEach { group ->
            group.lineStore.getSegments().forEach { seg ->
                lineSvg(group.toWorld(seg.start), group.toWorld(seg.end), edgeColor)
            }
        }

        // Axes + active group axes
        lineSvg(Vector3(0f, 0f, 0f), Vector3(2.5f, 0f, 0f), Color(0.85f, 0.25f, 0.25f, 1f))
        lineSvg(Vector3(0f, 0f, 0f), Vector3(0f, 2.5f, 0f), Color(0.25f, 0.85f, 0.35f, 1f))
        lineSvg(Vector3(0f, 0f, 0f), Vector3(0f, 0f, 2.5f), Color(0.35f, 0.45f, 0.95f, 1f))
        val active = scene.activeGroup()
        if (active !== scene.root) {
            val origin = active.worldOrigin()
            val axes = active.worldAxes()
            lineSvg(origin, Vector3(origin).mulAdd(axes.u.nor(), 1.8f), Color(0.85f, 0.25f, 0.25f, 1f))
            lineSvg(origin, Vector3(origin).mulAdd(axes.v.nor(), 1.8f), Color(0.25f, 0.85f, 0.35f, 1f))
            lineSvg(origin, Vector3(origin).mulAdd(axes.w.nor(), 1.8f), Color(0.35f, 0.45f, 0.95f, 1f))
        }

        // Camera target
        val half = 0.5f
        lineSvg(Vector3(cameraTarget.x - half, cameraTarget.y, cameraTarget.z), Vector3(cameraTarget.x + half, cameraTarget.y, cameraTarget.z), Color(1f, 0.55f, 0.1f, 1f))
        lineSvg(Vector3(cameraTarget.x, cameraTarget.y - half, cameraTarget.z), Vector3(cameraTarget.x, cameraTarget.y + half, cameraTarget.z), Color(1f, 0.55f, 0.1f, 1f))
        lineSvg(Vector3(cameraTarget.x, cameraTarget.y, cameraTarget.z - half), Vector3(cameraTarget.x, cameraTarget.y, cameraTarget.z + half), Color(1f, 0.55f, 0.1f, 1f))

        // Text
        collectGroups().forEach { group ->
            group.textStore.getTexts().forEach { text ->
                val pos = if (text.screenText) text.position else group.toWorld(text.position)
                val p = project(pos) ?: return@forEach
                val x = p.x
                val y = toSvgY(p.y)
                val size = (text.size * 64f).coerceAtLeast(8f)
                sb.append("""<text x="$x" y="$y" font-size="$size" fill="#111">${escapeSvg(text.text)}</text>""")
                sb.append("\n")
            }
        }

        sb.append("</svg>")
        file.writeText(sb.toString())
        statusModel.message = "Exported SVG view to ${file.absolutePath}"
    }

    private fun drawGuidePlaneSvg(
        origin: Vector3,
        axisU: Vector3,
        axisV: Vector3,
        extent: Float,
        line: (Vector3, Vector3, Color, Float) -> Unit
    ) {
        val steps = (extent / gridSpacing).toInt()
        for (i in -steps..steps) {
            val offset = i * gridSpacing
            val startU = Vector3(origin).mulAdd(axisU, -extent).mulAdd(axisV, offset)
            val endU = Vector3(origin).mulAdd(axisU, extent).mulAdd(axisV, offset)
            val startV = Vector3(origin).mulAdd(axisV, -extent).mulAdd(axisU, offset)
            val endV = Vector3(origin).mulAdd(axisV, extent).mulAdd(axisU, offset)
            line(startU, endU, axisColor(axisU), 1f)
            line(startV, endV, axisColor(axisV), 1f)
        }
    }

    private fun escapeSvg(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun updateCursorStatus() {
        lastSnap = snapper.compute(Gdx.input.x, Gdx.input.y)
        val snap = lastSnap
        if (snap != null && snap.valid && snap.world != null) {
            statusModel.cursorScreenX = snap.screenX
            statusModel.cursorScreenY = snap.screenY
            statusModel.cursorWorld = String.format("%.2f, %.2f, %.2f", snap.world.x, snap.world.y, snap.world.z)
            statusModel.cursorSnapLabel = snap.type.label
        } else {
            statusModel.cursorScreenX = Gdx.input.x
            statusModel.cursorScreenY = Gdx.input.y
            statusModel.cursorWorld = "--"
            statusModel.cursorSnapLabel = "No hit"
        }
    }

    private fun startDistanceInput() {
        val anchor = statusModel.anchorWorld
        val snap = lastSnap
        if (anchor == null || snap?.world == null || !snap.valid) {
            statusModel.message = "Distance input: no anchor."
            return
        }
        distanceInputActive = true
        uiOverlay.showDistancePopup(
            snap.screenX,
            snap.screenY,
            "",
            { text -> updateDistanceInput(text) },
            { text -> commitDistanceInput(text) },
            { cancelDistanceInput() }
        )
    }

    private fun handleGlobalDistanceShortcut() {
        val ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            startDistanceInput()
        }
    }

    private fun updateDistanceInput(text: String) {
        try {
            val anchor = statusModel.anchorWorld ?: return
            val snap = lastSnap ?: return
            val cursor = snap.world ?: return
            if (text.isBlank()) {
                distanceOverrideSnap = null
                return
            }
            val distance = parseDistanceExpression(text) ?: return
            val direction = Vector3(cursor).sub(anchor)
            if (direction.len2() <= 1e-6f) {
                return
            }
            direction.nor()
            val point = Vector3(anchor).mulAdd(direction, distance.toFloat())
            val normal = snap.normal ?: Vector3(0f, 1f, 0f)
            distanceOverrideSnap = SnapResult(point, normal, snap.type, snap.screenX, snap.screenY, true)
            toolController.pointerMoved(point, normal, true)
        } catch (_: Exception) {
            distanceOverrideSnap = null
            statusModel.message = "Distance input error."
        }
    }

    private fun commitDistanceInput(text: String) {
        val snap = distanceOverrideSnap ?: return cancelDistanceInput()
        toolController.pointerDown(snap.world, snap.normal, snap.valid, Input.Buttons.LEFT)
        distanceOverrideSnap = null
        distanceInputActive = false
    }

    private fun cancelDistanceInput() {
        distanceOverrideSnap = null
        distanceInputActive = false
    }

    private fun parseDistanceExpression(text: String): Double? {
        val input = text.replace(" ", "")
        if (input.isBlank()) {
            return null
        }
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch.isDigit() || ch == '.' || (ch == '-' && (i == 0 || "+-*/".contains(input[i - 1])))) {
                val start = i
                i++
                while (i < input.length && (input[i].isDigit() || input[i] == '.')) {
                    i++
                }
                tokens.add(input.substring(start, i))
                continue
            }
            if (ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                tokens.add(ch.toString())
                i++
                continue
            }
            return null
        }
        val output = java.util.Stack<Double>()
        val ops = java.util.Stack<String>()
        fun precedence(op: String): Int = if (op == "*" || op == "/") 2 else 1
        fun applyOp() {
            if (output.size < 2 || ops.isEmpty()) return
            val b = output.pop()
            val a = output.pop()
            val op = ops.pop()
            val result = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> a / b
                else -> return
            }
            output.push(result)
        }
        tokens.forEach { token ->
            val number = token.toDoubleOrNull()
            if (number != null) {
                output.push(number)
            } else {
                while (ops.isNotEmpty() && precedence(ops.peek()) >= precedence(token)) {
                    applyOp()
                }
                ops.push(token)
            }
        }
        while (ops.isNotEmpty()) {
            applyOp()
        }
        return if (output.size == 1) output.pop() else null
    }

    private fun startConsoleIfRequested() {
        if (!shouldStartConsole()) {
            return
        }
        val outputPane = OutputPane()
        val history = HistoryManager(ConsolePaths.historyFile())
        val runtime = buildConsoleRuntime(outputPane)
        val terminal = TerminalController()
        val tui = ConsoleTui(runtime, terminal, outputPane, history, ::openTerminal) {
            consoleThread?.shutdown()
        }
        consoleRuntime = runtime
        consoleTerminal = terminal
        consoleThread = ConsoleThread(runtime, tui, terminal).apply { start() }
    }

    private fun shouldStartConsole(): Boolean {
        return System.getProperty("k3d.devConsole") == "true"
    }

    private fun buildConsoleRuntime(outputPane: OutputPane): ConsoleGroovyRuntime {
        val consoleUtils = ConsoleUtils(outputPane)
        val appFacade = AppFacade(Gdx.app)
        val selectionFacade = SelectionFacade(scene)
        val unitFacade = UnitFacade({ modelUnit }, ::updateModelUnit)
        val saveFacade = SaveFacade({ modelFile }, ::setSaveName)
        val cameraFacade = CameraFacade(camera, orbitCameraController.target) { camera.update() }
        val mcpFacade = McpFacade(
            startFn = ::startMcpServer,
            stopFn = ::stopMcpServer,
            statusFn = ::mcpServerStatus,
            portFn = ::mcpServerPort,
            setPortFn = ::setMcpServerPort
        )
        val lightingFacade = LightingFacade(lightingSettings, shadowSettings) {
            applyLightingSettings(lightingSettings)
            applyShadowSettings(shadowSettings)
            uiOverlay.refreshLightingControls()
        }
        return ConsoleGroovyRuntime(
            appFacade,
            scene,
            selectionFacade,
            consoleUtils,
            mapOf(
                "pluginHost" to pluginHost,
                "lighting" to lightingSettings,
                "shadow" to shadowSettings,
                "lightingCtl" to lightingFacade,
                "camera" to camera,
                "cameraTarget" to cameraTarget,
                "cameraCtl" to cameraFacade,
                "status" to statusModel,
                "unit" to unitFacade,
                "save" to saveFacade,
                "mcp" to mcpFacade,
                "version" to K3DVersion()
            )
        )
    }

    private fun setSaveName(file: java.io.File) {
        modelFile = file.absoluteFile
        statusModel.message = "Save file set to ${modelFile.name}"
        updateWindowTitle()
    }

    private fun openTerminal() {
        val terminal = consoleTerminal ?: return
        terminal.restore()
        println("Entering shell. Type 'exit' to return to the K3D console.")
        val shell = System.getenv("SHELL") ?: "/bin/bash"
        try {
            ProcessBuilder(shell)
                .inheritIO()
                .start()
                .waitFor()
        } catch (_: Exception) {
            println("Failed to launch shell: $shell")
        } finally {
            terminal.enterRawMode()
        }
    }

    private fun updateWindowTitle() {
        if (!::modelFile.isInitialized) {
            return
        }
        val version = preferVersion(K3DVersion())
        val totalMax = (Gdx.graphics.width / 8).coerceAtLeast(30)
        val separator = " | "
        val pathMax = (totalMax - version.length - separator.length).coerceAtLeast(10)
        val path = trimMiddle(modelFile.absolutePath, pathMax)
        Gdx.graphics.setTitle("$version$separator$path")
    }

    private fun preferVersion(ver: K3DVersion): String {
        return when {
            ver.buildVersion.isNotBlank() -> ver.buildVersion
            ver.buildGitTag.isNotBlank() -> ver.buildGitTag
            else -> "unknown"
        }
    }

    private fun trimMiddle(text: String, maxLength: Int): String {
        if (text.length <= maxLength) {
            return text
        }
        if (maxLength <= 2) {
            return text.take(maxLength)
        }
        val keep = maxLength - 2
        val head = (keep + 1) / 2
        val tail = keep / 2
        return text.take(head) + ".." + text.takeLast(tail)
    }

    private fun drawDraftLines() {
        val defaultColor = Color(0.2f, 0.2f, 0.2f, 1f)
        val crossSize = 0.1f
        scene.walkGroups(scene.root) { group ->
            val selected = group.lineStore.getSelected()
            group.lineStore.getSegments().forEach { segment ->
                val isSelected = selected.contains(segment)
                shapeRenderer.color = if (isSelected) selectedLineColor else defaultColor
                Gdx.gl.glLineWidth(if (isSelected) selectedLineWidth else 2f)
                val start = group.toWorld(segment.start)
                val end = group.toWorld(segment.end)
                if (isSelected) {
                    drawDashedLine(start, end, 0.4f, 0.25f)
                } else {
                    shapeRenderer.line(start, end)
                }
                drawLineCross(start, crossSize)
                drawLineCross(end, crossSize)
            }
        }
        scene.root.lineStore.getSegments().forEach { segment ->
            val isSelected = scene.root.lineStore.isSelected(segment)
            shapeRenderer.color = if (isSelected) selectedLineColor else defaultColor
            Gdx.gl.glLineWidth(if (isSelected) selectedLineWidth else 2f)
            if (isSelected) {
                drawDashedLine(segment.start, segment.end, 0.4f, 0.25f)
            } else {
                shapeRenderer.line(segment.start, segment.end)
            }
            drawLineCross(segment.start, crossSize)
            drawLineCross(segment.end, crossSize)
        }
        Gdx.gl.glLineWidth(2f)
    }

    private fun drawArchitectureHoleGuides() {
        if (!scene.hasArchitectureElements()) {
            return
        }
        val selectedWallIds = scene.selectedArchitectureElements(scene.root)
            .filter { it.kind == ArchitectureStore.ElementKind.WALL }
            .map { it.id }
            .toSet()
        if (selectedWallIds.isEmpty()) {
            return
        }
        val guides = selectedWallIds.flatMap { wallId ->
            scene.architectureHoleGuideSegmentsWorld(scene.root, includeDiagonals = true, wallId = wallId)
        }
        if (guides.isEmpty()) {
            return
        }
        shapeRenderer.color = architectureHoleGuideColor
        Gdx.gl.glLineWidth(3f)
        guides.forEach { (a, b) -> shapeRenderer.line(a, b) }
        Gdx.gl.glLineWidth(2f)
    }

    private fun drawArchitectureWallEndpointHitAreas() {
        if (!scene.hasArchitectureElements()) {
            return
        }
        val root = scene.root
        val wallMarkers = scene.architectureWallEndpointHandleMarkersWorld(root)
        val slabMarkers = scene.architectureSlabEndpointHandleMarkersWorld(root)
        val frameMarkers = scene.architectureFrameEndpointHandleMarkersWorld(root)
        val selectedWallIds = scene.selectedArchitectureElements(root)
            .filter { it.kind == ArchitectureStore.ElementKind.WALL }
            .map { it.id }
            .toSet()
        val holeMarkers = if (selectedWallIds.isEmpty()) {
            emptyList()
        } else {
            selectedWallIds.flatMap { wallId ->
                scene.architectureHoleHandleMarkersWorld(root, wallId = wallId)
            }
        }
        if (wallMarkers.isEmpty() && slabMarkers.isEmpty() && frameMarkers.isEmpty() && holeMarkers.isEmpty()) {
            return
        }
        Gdx.gl.glLineWidth(3f)
        shapeRenderer.color = architectureHoleGuideColor
        wallMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.center, marker.halfSize) }
        shapeRenderer.color = architectureSlabHotspotColor
        slabMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.center, marker.halfSize) }
        shapeRenderer.color = architectureFrameHotspotColor
        frameMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.center, marker.halfSize) }
        shapeRenderer.color = architectureHoleHotspotColor
        holeMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.world, 0.18f) }
        Gdx.gl.glLineWidth(2f)
    }

    private fun drawArchitectureConstructionHotspots() {
        if (!scene.hasArchitectureElements()) {
            return
        }
        val root = scene.root
        val selections = scene.selectedArchitectureElements(root)
        if (selections.isEmpty()) {
            return
        }
        val slabIds = selections.filter { it.kind == ArchitectureStore.ElementKind.SLAB }.map { it.id }.toSet()
        val frameIds = selections.filter { it.kind == ArchitectureStore.ElementKind.FRAME }.map { it.id }.toSet()
        val wallIds = selections.filter { it.kind == ArchitectureStore.ElementKind.WALL }.map { it.id }.toSet()
        val slabMarkers = slabIds.flatMap { slabId -> scene.architectureSlabConstructionHotspotsWorld(root, slabId = slabId) }
        val frameMarkers = frameIds.flatMap { frameId -> scene.architectureFrameConstructionHotspotsWorld(root, frameId = frameId) }
        val holeMarkers = wallIds.flatMap { wallId -> scene.architectureHoleConstructionHotspotsWorld(root, wallId = wallId) }
        if (slabMarkers.isEmpty() && frameMarkers.isEmpty() && holeMarkers.isEmpty()) {
            return
        }
        val hotspotSize = 0.16f
        Gdx.gl.glLineWidth(3f)
        shapeRenderer.color = architectureSlabHotspotColor
        slabMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.world, hotspotSize) }
        shapeRenderer.color = architectureFrameHotspotColor
        frameMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.world, hotspotSize) }
        shapeRenderer.color = architectureHoleHotspotColor
        holeMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.world, hotspotSize) }
        Gdx.gl.glLineWidth(2f)
    }

    private fun drawArchitectureHandleSquare(center: Vector3, halfSize: Float) {
        val y = center.y + 0.01f
        val p0 = Vector3(center.x - halfSize, y, center.z - halfSize)
        val p1 = Vector3(center.x + halfSize, y, center.z - halfSize)
        val p2 = Vector3(center.x + halfSize, y, center.z + halfSize)
        val p3 = Vector3(center.x - halfSize, y, center.z + halfSize)
        shapeRenderer.line(p0, p1)
        shapeRenderer.line(p1, p2)
        shapeRenderer.line(p2, p3)
        shapeRenderer.line(p3, p0)
        shapeRenderer.line(p0, p2)
        shapeRenderer.line(p1, p3)
    }

    private fun drawLineCross(point: Vector3, size: Float) {
        val half = size * 0.5f
        shapeRenderer.line(point.x - half, point.y, point.z, point.x + half, point.y, point.z)
        shapeRenderer.line(point.x, point.y - half, point.z, point.x, point.y + half, point.z)
        shapeRenderer.line(point.x, point.y, point.z - half, point.x, point.y, point.z + half)
    }

    private fun drawDimensions() {
        val defaultColor = Color(0.2f, 0.2f, 0.2f, 1f)
        scene.collectWorldDimensions { start, end, offset, selected ->
            val (lineStart, lineEnd) = DimensionMath.computeOffsetLine(start, end, offset)
            val dir = Vector3(lineEnd).sub(lineStart).nor()
            val offsetDir = DimensionMath.computeOffsetDirection(start, end, offset)
            val baseScale = 1.2f
            val extension = textWorldSize(lineStart, textFont.lineHeight * baseScale)
            val extensionEndA = Vector3(lineStart).mulAdd(offsetDir, extension)
            val extensionEndB = Vector3(lineEnd).mulAdd(offsetDir, extension)
            val dimensionExtend = extension * 0.7f
            val dimStart = Vector3(lineStart).mulAdd(dir, -dimensionExtend)
            val dimEnd = Vector3(lineEnd).mulAdd(dir, dimensionExtend)
            val dimensionLineWidth = if (selected) selectedLineWidth + 2f else 4f
            val extensionLineWidth = if (selected) 3f else 1.5f
            val arrowLineWidth = if (selected) selectedLineWidth * 2f else 16f
            shapeRenderer.color = if (selected) selectedLineColor else defaultColor
            Gdx.gl.glLineWidth(dimensionLineWidth)
            shapeRenderer.line(dimStart, dimEnd)
            Gdx.gl.glLineWidth(extensionLineWidth)
            shapeRenderer.line(start, extensionEndA)
            shapeRenderer.line(end, extensionEndB)
            val slashDir = Vector3(dir).add(offsetDir).nor()
            val slashLen = extension * 0.6f
            shapeRenderer.color = Color(0f, 0f, 0f, 1f)
            Gdx.gl.glLineWidth(arrowLineWidth)
            shapeRenderer.line(
                Vector3(lineStart).mulAdd(slashDir, -slashLen * 0.5f),
                Vector3(lineStart).mulAdd(slashDir, slashLen * 0.5f)
            )
            shapeRenderer.line(
                Vector3(lineEnd).mulAdd(slashDir, -slashLen * 0.5f),
                Vector3(lineEnd).mulAdd(slashDir, slashLen * 0.5f)
            )
        }
        Gdx.gl.glLineWidth(2f)
    }

    private fun drawAnnotations2D() {
        spriteBatch.projectionMatrix = uiOverlay.stage.camera.combined
        textTransform.idt()
        spriteBatch.transformMatrix = textTransform
        spriteBatch.begin()
        scene.collectWorldDimensions { start, end, offset, selected ->
            val (lineStart, lineEnd) = DimensionMath.computeOffsetLine(start, end, offset)
            drawDimensionText(lineStart, lineEnd, start, end, offset, selected)
        }
        drawActiveToolMeasurementLabels()
        spriteBatch.end()

        spriteBatch.projectionMatrix = uiOverlay.stage.camera.combined
        textTransform.idt()
        spriteBatch.transformMatrix = textTransform
        spriteBatch.begin()
        scene.collectWorldTexts { position, text, size, normal, axisU, selected, screenText ->
            if (screenText) {
                drawWorldTextScreen(text, position, size, selected)
            }
        }
        spriteBatch.end()

        spriteBatch.projectionMatrix = activeCamera.combined
        textTransform.idt()
        spriteBatch.transformMatrix = textTransform
        spriteBatch.begin()
        scene.collectWorldTexts { position, text, size, normal, axisU, selected, screenText ->
            if (!screenText) {
                drawWorldTextModel(text, position, size, normal, axisU, selected)
            }
        }
        spriteBatch.end()
    }

    private fun drawDimensionText(
        lineStart: Vector3,
        lineEnd: Vector3,
        start: Vector3,
        end: Vector3,
        offset: Vector3,
        selected: Boolean
    ) {
        val length = start.dst(end)
        val value = length * modelUnit.size
        val label = formatMeasurement(value, modelUnit.name)
        val mid = Vector3(lineStart).add(lineEnd).scl(0.5f)
        val offsetDir = DimensionMath.computeOffsetDirection(start, end, offset)
        val scale = 1.2f
        val offsetAmount = textWorldSize(lineStart, textFont.lineHeight * scale * 0.35f)
        val textPos = Vector3(mid).mulAdd(offsetDir, offsetAmount)
        val viewCamera = activeCamera
        val screenPos = viewCamera.project(textPos)
        val screenA = viewCamera.project(Vector3(lineStart))
        val screenB = viewCamera.project(Vector3(lineEnd))
        val angleRad = kotlin.math.atan2(screenB.y - screenA.y, screenB.x - screenA.x)
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
        val color = if (selected) selectedLineColor else Color(0.1f, 0.1f, 0.1f, 1f)
        drawRotatedTextScaled(label, screenPos.x, screenPos.y, angleDeg, scale, color)
    }

    private fun drawActiveToolMeasurementLine() {
        val measurement = toolController.activeTool().measurement(statusModel) ?: return
        val start = measurement.startWorld
        val end = measurement.endWorld
        if (start.dst2(end) <= 1e-8f) {
            return
        }
        shapeRenderer.color = measurement.lineColor
        shapeRenderer.line(start, end)
    }

    private fun drawActiveToolMeasurementLabels() {
        val measurement = toolController.activeTool().measurement(statusModel) ?: return
        val start = measurement.startWorld
        val end = measurement.endWorld
        if (start.dst2(end) <= 1e-8f) {
            return
        }
        val viewCamera = activeCamera
        val screenA = viewCamera.project(Vector3(start))
        val screenB = viewCamera.project(Vector3(end))
        val dx = screenB.x - screenA.x
        val dy = screenB.y - screenA.y
        val screenLen = kotlin.math.sqrt(dx * dx + dy * dy)
        if (screenLen <= 1f) {
            return
        }
        var nx = -dy / screenLen
        var ny = dx / screenLen
        val midX = (screenA.x + screenB.x) * 0.5f
        val midY = (screenA.y + screenB.y) * 0.5f
        val angleRad = kotlin.math.atan2(dy, dx)
        var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
        val angleNorm = ((angleDeg % 360f) + 360f) % 360f
        var sideSign = 1f
        if (angleNorm in 90f..270f) {
            // Keep text readable and swap the two label sides for backward-facing screen lines.
            angleDeg += 180f
            sideSign = -1f
        }
        nx *= sideSign
        ny *= sideSign
        val sideOffsetPx = 12f
        // BitmapFont draws from baseline; keep labels lifted but closer to the line.
        val liftPx = textFont.lineHeight * 0.75f
        val lengthValue = start.dst(end) * modelUnit.size
        val lengthLabel = formatMeasurement(lengthValue, modelUnit.name)
        val delta = Vector3(end).sub(start).scl(modelUnit.size)
        val relativeLabel = formatRelativeVector(delta, modelUnit.name)
        drawRotatedTextScaled(
            lengthLabel,
            midX + nx * sideOffsetPx,
            midY + ny * sideOffsetPx + liftPx,
            angleDeg,
            1f,
            Color(0.1f, 0.1f, 0.1f, 1f)
        )
        drawRotatedTextScaled(
            relativeLabel,
            midX - nx * sideOffsetPx,
            midY - ny * sideOffsetPx + liftPx,
            angleDeg,
            1f,
            Color(0.15f, 0.15f, 0.15f, 0.95f)
        )
    }

    private fun drawWorldTextModel(
        text: String,
        position: Vector3,
        size: Float,
        normal: Vector3,
        axisU: Vector3,
        selected: Boolean
    ) {
        val color = if (selected) selectedLineColor else Color(0.1f, 0.1f, 0.1f, 1f)
        drawTextInPlane(text, position, size, normal, axisU, color)
    }

    private fun drawWorldTextScreen(text: String, position: Vector3, size: Float, selected: Boolean) {
        val screenPos = activeCamera.project(Vector3(position))
        val color = if (selected) selectedLineColor else Color(0.1f, 0.1f, 0.1f, 1f)
        drawTextScaled(text, screenPos.x, screenPos.y, size * 10f, color)
    }

    private fun drawTextScaled(text: String, x: Float, y: Float, scale: Float, color: Color) {
        val previousScaleX = textFont.data.scaleX
        val previousScaleY = textFont.data.scaleY
        textFont.data.setScale(scale)
        textFont.color = color
        textFont.draw(spriteBatch, text, x, y)
        textFont.data.setScale(previousScaleX, previousScaleY)
    }

    private fun drawRotatedText(text: String, x: Float, y: Float, angleDeg: Float, color: Color) {
        textLayout.setText(textFont, text)
        val originX = textLayout.width / 2f
        val originY = textLayout.height / 2f
        textTransformBackup.set(spriteBatch.transformMatrix)
        textTransform.idt()
        textTransform.translate(x, y, 0f)
        textTransform.rotate(Vector3.Z, angleDeg)
        textTransform.translate(-originX, -originY, 0f)
        spriteBatch.transformMatrix = textTransform
        textFont.color = color
        textFont.draw(spriteBatch, text, 0f, 0f)
        spriteBatch.transformMatrix = textTransformBackup
    }

    private fun drawRotatedTextScaled(text: String, x: Float, y: Float, angleDeg: Float, scale: Float, color: Color) {
        val previousScaleX = textFont.data.scaleX
        val previousScaleY = textFont.data.scaleY
        textFont.data.setScale(scale)
        textLayout.setText(textFont, text)
        val originX = textLayout.width / 2f
        val originY = textLayout.height / 2f
        textTransformBackup.set(spriteBatch.transformMatrix)
        textTransform.idt()
        textTransform.translate(x, y, 0f)
        textTransform.rotate(Vector3.Z, angleDeg)
        textTransform.translate(-originX, -originY, 0f)
        spriteBatch.transformMatrix = textTransform
        textFont.color = color
        textFont.draw(spriteBatch, text, 0f, 0f)
        spriteBatch.transformMatrix = textTransformBackup
        textFont.data.setScale(previousScaleX, previousScaleY)
    }

    private fun drawTextInPlane(
        text: String,
        position: Vector3,
        size: Float,
        normal: Vector3,
        axisU: Vector3,
        color: Color
    ) {
        val n = Vector3(normal).nor()
        var u = Vector3(axisU).mulAdd(n, -axisU.dot(n))
        if (u.len2() < 1e-6f) {
            u = Vector3(1f, 0f, 0f).mulAdd(n, -n.x)
        }
        u.nor()
        val v = Vector3(n).crs(u).nor()
        val worldUp = Vector3(0f, 1f, 0f)
        if (v.dot(worldUp) < 0f) {
            u.scl(-1f)
            v.scl(-1f)
        }
        val viewCamera = activeCamera
        val screenOrigin = viewCamera.project(Vector3(position))
        val screenU = viewCamera.project(Vector3(position).add(u))
        val screenV = viewCamera.project(Vector3(position).add(v))
        val ux = screenU.x - screenOrigin.x
        val vy = screenV.y - screenOrigin.y
        if (ux < 0f || vy < 0f) {
            u.scl(-1f)
            v.scl(-1f)
        }
        val worldPerPixel = worldPerPixelAt(position)
        val baseHeightWorld = textFont.lineHeight * worldPerPixel
        val scale = if (baseHeightWorld > 1e-6f) size / baseHeightWorld else 1f
        val prevScaleX = textFont.data.scaleX
        val prevScaleY = textFont.data.scaleY
        textFont.data.setScale(scale)
        textLayout.setText(textFont, text)
        val originX = textLayout.width / 2f
        val originY = textLayout.height / 2f
        val rotation = Quaternion().setFromAxes(
            u.x, u.y, u.z,
            v.x, v.y, v.z,
            n.x, n.y, n.z
        )
        textTransformBackup.set(spriteBatch.transformMatrix)
        textTransform.idt()
        textTransform.set(position, rotation, Vector3(1f, 1f, 1f))
        spriteBatch.transformMatrix = textTransform
        textFont.color = color
        textFont.draw(spriteBatch, text, -originX, originY)
        spriteBatch.transformMatrix = textTransformBackup
        textFont.data.setScale(prevScaleX, prevScaleY)
    }

    private fun textWorldSize(worldPoint: Vector3, pixelSize: Float): Float {
        return worldPerPixelAt(worldPoint) * pixelSize
    }

    private fun worldPerPixelAt(worldPoint: Vector3): Float {
        val viewCamera = activeCamera
        return when (viewCamera) {
            is PerspectiveCamera -> {
                val toPoint = Vector3(worldPoint).sub(viewCamera.position)
                val depth = toPoint.dot(viewCamera.direction)
                if (depth <= 0f) {
                    return 0.01f
                }
                val viewportHeight = 2f * depth * kotlin.math.tan(Math.toRadians(viewCamera.fieldOfView.toDouble() / 2.0)).toFloat()
                viewportHeight / Gdx.graphics.height
            }

            is OrthographicCamera -> {
                val pixels = Gdx.graphics.height.coerceAtLeast(1).toFloat()
                (viewCamera.viewportHeight * viewCamera.zoom) / pixels
            }

            else -> 0.01f
        }
    }

    private fun formatMeasurement(value: Float, unitName: String): String {
        val formatted = String.format(java.util.Locale.US, "%.3f", value)
            .trimEnd('0')
            .trimEnd('.')
        return if (unitName.isBlank()) formatted else "$formatted $unitName"
    }

    private fun formatRelativeVector(delta: Vector3, unitName: String): String {
        val x = formatSignedComponent(delta.x)
        val y = formatSignedComponent(delta.y)
        val z = formatSignedComponent(delta.z)
        val base = "($x, $y, $z)"
        return if (unitName.isBlank()) base else "$base $unitName"
    }

    private fun formatSignedComponent(value: Float): String {
        val roundedZero = if (kotlin.math.abs(value) < 1e-4f) 0f else value
        val formatted = String.format(java.util.Locale.US, "%+.3f", roundedZero)
            .trimEnd('0')
            .trimEnd('.')
        return if (formatted == "-0") "+0" else formatted
    }

    private fun drawPluginLines() {
        if (!::pluginHost.isInitialized) {
            return
        }
        val lines = pluginHost.collectDrawLines()
        if (lines.isEmpty()) {
            return
        }
        lines.forEach { line ->
            Gdx.gl.glLineWidth(line.width)
            shapeRenderer.color = line.color
            shapeRenderer.line(line.start, line.end)
        }
        Gdx.gl.glLineWidth(2f)
    }

    private fun drawSelectionHighlights() {
        drawSelectedVoxelHighlights()
        drawSelectedEntityBounds()
        drawGroupSelectionHighlights()
        drawActiveGroupEditBounds()
    }

    private fun setupUndoManager() {
        undoManager = com.github.alfu32.sketch.model.UndoRedoManager(
            snapshotProvider = { snapshotForUndo() },
            applySnapshot = { snapshot -> applyUndoSnapshot(snapshot) },
            onSnapshotApplied = {
                uiOverlay.refreshLightingControls()
                updateWindowTitle()
            }
        )
    }

    private fun snapshotForUndo(): ModelPersistence.ModelSnapshot {
        return ModelPersistence.snapshot(
            scene,
            camera,
            orbitCameraController.target,
            lightingSettings,
            shadowSettings,
            modelUnit,
            snapEpsilon,
            gridSpacing
        ).apply {
            undoHistory = null
        }
    }

    private fun applyUndoSnapshot(snapshot: ModelPersistence.ModelSnapshot) {
        restoringSnapshot = true
        try {
            ModelPersistence.applySnapshot(
                snapshot,
                scene,
                camera,
                cameraTarget,
                lightingSettings,
                shadowSettings,
                modelUnit,
                { value -> applySnapEpsilon(value, false) },
                { value -> applyGridSpacing(value, false) }
            )
            scene.applyChangeListenerToAll()
            applyLightingSettings(lightingSettings)
            applyShadowSettings(shadowSettings)
            orbitCameraController.target.set(cameraTarget)
            syncCameraModesAfterOrbitStateChange()
        } finally {
            restoringSnapshot = false
        }
    }

    private fun onModelChanged() {
        if (restoringSnapshot) {
            return
        }
        undoManager.markChanged()
        saveModel()
    }

    private fun runCleanup() {
        val startEdges = totalEdgeCount()
        val startFaces = totalFaceCount()
        statusModel.message = "Cleanup start | edges $startEdges faces $startFaces"
        modelCleanup.run()
        val endEdges = totalEdgeCount()
        val endFaces = totalFaceCount()
        statusModel.message = "Cleanup done | edges $endEdges faces $endFaces"
        undoManager.commit("Cleanup")
        saveModel()
    }

    private fun undoAction() {
        if (undoManager.undo()) {
            statusModel.message = "Undo."
            saveModel()
        } else {
            statusModel.message = "Nothing to undo."
        }
    }

    private fun redoAction() {
        if (undoManager.redo()) {
            statusModel.message = "Redo."
            saveModel()
        } else {
            statusModel.message = "Nothing to redo."
        }
    }

    private fun clearSelection() {
        scene.clearAllSelections()
        statusModel.message = "Selection cleared."
    }

    private fun resetSceneForCapture() {
        while (scene.exitGroup()) {
            // Return to root before clearing stores and prototype content.
        }
        scene.resetScene()
        val rootPrototype = scene.rootPrototype()
        rootPrototype.lineStore.clearAll()
        rootPrototype.faceStore.clearAll()
        rootPrototype.dimensionStore.clearAll()
        rootPrototype.textStore.clearAll()
        scene.root.children.clear()
        scene.clearAllSelections()
        guideManager.clear()
        toolController.resetToDefault()
        toolController.cancelActiveTool()
        setCameraMode(CameraMode.ORBIT)
        orbitCameraController.target.set(0f, 0f, 0f)
        camera.position.set(18f, 14f, 18f)
        camera.up.set(0f, 1f, 0f)
        camera.lookAt(orbitCameraController.target)
        camera.update()
        uiOverlay.setAutomationHidePanels(true)
        statusModel.message = "Scene reset for capture (perspective, Y-up)."
    }

    private fun deleteSelection() {
        val group = scene.activeGroup()
        val isVoxel = scene.isVoxelGroup(group)
        val hasArchitecture = scene.hasArchitectureElements()
        val architectureElementDeletes = if (hasArchitecture) {
            scene.deleteSelectedArchitectureElements(scene.root)
        } else {
            0
        }
        val voxelDeletes = if (isVoxel) {
            scene.deleteSelectedVoxels(group)
        } else {
            0
        }
        val architectureHoleDeletes = if (hasArchitecture) {
            scene.deleteSelectedArchitectureHoleContours(group)
        } else {
            0
        }
        val edges = if (isVoxel) 0 else activeLineStore().deleteSelected()
        val faces = if (isVoxel) 0 else activeFaceStore().deleteSelected()
        val dimensions = activeDimensionStore().deleteSelected()
        val texts = activeTextStore().deleteSelected()
        val groups = scene.deleteSelectedGroups()
        if (edges + faces + voxelDeletes + architectureHoleDeletes + architectureElementDeletes + dimensions + texts + groups > 0) {
            statusModel.message =
                "Deleted | architecture $architectureElementDeletes voxels $voxelDeletes holes $architectureHoleDeletes edges $edges faces $faces dimensions $dimensions texts $texts groups $groups"
            if (groups > 0 && edges + faces + voxelDeletes + architectureHoleDeletes + architectureElementDeletes == 0) {
                undoManager.commit("Delete")
                saveModel()
            }
        } else if (hasArchitecture) {
            statusModel.message = "Select hole contours to delete wall holes."
        }
    }

    private fun flipSelectedFaces() {
        val flipped = activeFaceStore().flipSelected()
        if (flipped > 0) {
            statusModel.message = "Flipped faces: $flipped"
            undoManager.commit("Flip Faces")
            saveModel()
        }
    }

    private fun saveModel() {
        ModelPersistence.save(
            modelFile,
            scene,
            camera,
            orbitCameraController.target,
            lightingSettings,
            shadowSettings,
            modelUnit,
            snapEpsilon,
            gridSpacing,
            undoManager.exportHistory()
        )
        if (::pluginHost.isInitialized) {
            pluginHost.dispatchSave()
        }
    }

    private fun loadModel() {
        if (modelFile.exists()) {
            val backup = java.io.File(modelFile.absolutePath + ".bak")
            modelFile.copyTo(backup, overwrite = true)
            val result = ModelPersistence.load(
                modelFile,
                scene,
                camera,
                cameraTarget,
                lightingSettings,
                shadowSettings,
                modelUnit,
                { value -> applySnapEpsilon(value, false) },
                { value -> applyGridSpacing(value, false) }
            )
            scene.applyChangeListenerToAll()
            val history = result.snapshot?.undoHistory
            undoManager.importHistory(history)
            if (history == null || history.entries.isEmpty()) {
                undoManager.reset("Loaded")
            }
            if (result.ok && result.needsResave) {
                ModelPersistence.save(
                    modelFile,
                    scene,
                    camera,
                    orbitCameraController.target,
                    lightingSettings,
                    shadowSettings,
                    modelUnit,
                    snapEpsilon,
                    gridSpacing,
                    undoManager.exportHistory()
                )
            }
            orbitCameraController.target.set(cameraTarget)
            syncCameraModesAfterOrbitStateChange()
            statusModel.message = "Loaded ${modelFile.name}"
        } else {
            undoManager.reset("Created")
            saveModel()
            statusModel.message = "Created ${modelFile.name}"
        }
    }

    private fun resolveModelFile(args: kotlin.Array<String>): java.io.File {
        var fileArg: String? = null
        var i = 0
        while (i < args.size) {
            if (args[i] == "--file" && i + 1 < args.size) {
                fileArg = args[i + 1]
                break
            }
            i++
        }
        if (fileArg.isNullOrBlank()) {
            fileArg = "sketch3d.k3d"
        }
        return java.io.File(fileArg).absoluteFile
    }

    private fun resolvePluginsDir(args: kotlin.Array<String>, installDir: java.io.File): java.io.File {
        var dirArg: String? = null
        var i = 0
        while (i < args.size) {
            if ((args[i] == "--plugins-dir" || args[i] == "--pluginsDir") && i + 1 < args.size) {
                dirArg = args[i + 1]
                break
            }
            i++
        }
        return if (dirArg.isNullOrBlank()) {
            java.io.File(installDir, "plugins").absoluteFile
        } else {
            java.io.File(dirArg).absoluteFile
        }
    }

    private fun resolveInstallDir(): java.io.File {
        return try {
            val location = java.io.File(Main::class.java.protectionDomain.codeSource.location.toURI())
            if (location.isFile) {
                location.parentFile ?: java.io.File(System.getProperty("user.dir"))
            } else {
                location
            }
        } catch (_: Exception) {
            java.io.File(System.getProperty("user.dir"))
        }
    }

    private fun selectionInfo(): SketchUiOverlay.SelectionInfo {
        val group = scene.activeGroup()
        val selectedTexts = group.textStore.getSelected()
        val selectedText = if (selectedTexts.size == 1) selectedTexts.first() else null
        val selectedVoxels = scene.selectedVoxels(group).size
        return SketchUiOverlay.SelectionInfo(
            edgeCount = activeLineStore().getSelected().size,
            faceCount = activeFaceStore().getSelected().size,
            voxelCount = selectedVoxels,
            groupCount = scene.selectedGroups().size,
            dimensionCount = group.dimensionStore.getSelected().size,
            textCount = selectedTexts.size,
            selectedTextId = selectedText?.id,
            selectedTextValue = selectedText?.text,
            selectedTextSize = selectedText?.size,
            selectedTextScreen = selectedText?.screenText
        )
    }

    private fun architectureSelectionInfo(): SketchUiOverlay.ArchitectureElementInfo? {
        val group = architecturePanelTargetGroup() ?: return null
        val store = group.architectureStore ?: return null
        val selection = architecturePanelSelection(store) ?: return null
        return when (selection.kind) {
            ArchitectureStore.ElementKind.WALL -> {
                val wall = store.allWalls().firstOrNull { it.id == selection.id } ?: return null
                SketchUiOverlay.ArchitectureElementInfo(
                    kind = SketchUiOverlay.ArchitectureElementKind.WALL,
                    id = wall.id,
                    name = wall.name,
                    wallThickness = wall.thickness,
                    wallHeight = wall.height,
                    wallInclinationDeg = wall.inclinationDeg,
                    wallExteriorColor = Color(wall.exteriorColor),
                    wallInteriorColor = Color(wall.interiorColor)
                )
            }
            ArchitectureStore.ElementKind.SLAB -> {
                val slab = store.allSlabs().firstOrNull { it.id == selection.id } ?: return null
                SketchUiOverlay.ArchitectureElementInfo(
                    kind = SketchUiOverlay.ArchitectureElementKind.SLAB,
                    id = slab.id,
                    name = slab.name,
                    slabThickness = slab.thickness,
                    slabTopColor = Color(slab.topColor),
                    slabBottomColor = Color(slab.bottomColor),
                    slabSideColor = Color(slab.sideColor)
                )
            }
            ArchitectureStore.ElementKind.STAIR -> {
                val stair = store.allStairs().firstOrNull { it.id == selection.id } ?: return null
                SketchUiOverlay.ArchitectureElementInfo(
                    kind = SketchUiOverlay.ArchitectureElementKind.STAIR,
                    id = stair.id,
                    name = stair.name,
                    stairHeight = stair.height,
                    stairStepCount = stair.stepCount,
                    stairSupportThickness = stair.supportThickness,
                    stairRailLeftEnabled = stair.railLeftEnabled,
                    stairRailRightEnabled = stair.railRightEnabled,
                    stairTreadColor = Color(stair.treadColor),
                    stairSupportColor = Color(stair.supportColor)
                )
            }
            ArchitectureStore.ElementKind.FRAME -> {
                val frame = store.allFrames().firstOrNull { it.id == selection.id } ?: return null
                SketchUiOverlay.ArchitectureElementInfo(
                    kind = SketchUiOverlay.ArchitectureElementKind.FRAME,
                    id = frame.id,
                    name = frame.name,
                    frameDepth = frame.depth,
                    frameWidth = frame.frameWidth,
                    frameColor = Color(frame.color),
                    frameGlazingEnabled = frame.glazingEnabled,
                    frameGlazingColor = Color(frame.glazingColor)
                )
            }
        }
    }

    private fun architectureSelectionSummary(): SketchUiOverlay.ArchitectureSelectionSummary {
        val group = architecturePanelTargetGroup() ?: return SketchUiOverlay.ArchitectureSelectionSummary()
        val store = group.architectureStore ?: return SketchUiOverlay.ArchitectureSelectionSummary()
        val selected = store.selectedElements()
        val selectedWalls = selected.filter { it.kind == ArchitectureStore.ElementKind.WALL }
        val selectedSlabs = selected.filter { it.kind == ArchitectureStore.ElementKind.SLAB }
        val selectedStairs = selected.filter { it.kind == ArchitectureStore.ElementKind.STAIR }
        val selectedFrames = selected.filter { it.kind == ArchitectureStore.ElementKind.FRAME }

        val singleWall = if (selectedWalls.size == 1) store.wallById(selectedWalls.first().id) else null
        val singleSlab = if (selectedSlabs.size == 1) store.allSlabs().firstOrNull { it.id == selectedSlabs.first().id } else null
        val singleStair = if (selectedStairs.size == 1) store.allStairs().firstOrNull { it.id == selectedStairs.first().id } else null
        val singleFrame = if (selectedFrames.size == 1) store.allFrames().firstOrNull { it.id == selectedFrames.first().id } else null

        return SketchUiOverlay.ArchitectureSelectionSummary(
            selectedWallCount = selectedWalls.size,
            selectedSlabCount = selectedSlabs.size,
            selectedStairCount = selectedStairs.size,
            selectedFrameCount = selectedFrames.size,
            singleWallId = singleWall?.id,
            singleSlabId = singleSlab?.id,
            singleStairId = singleStair?.id,
            singleFrameId = singleFrame?.id,
            singleWallName = singleWall?.name,
            singleSlabName = singleSlab?.name,
            singleStairName = singleStair?.name,
            singleFrameName = singleFrame?.name
        )
    }

    private fun architecturePanelTargetGroup(): GroupScene.GroupNode? {
        return scene.root
    }

    private fun architecturePanelSelection(store: ArchitectureStore): ArchitectureStore.ElementSelection? {
        store.selectedElement()?.let { return it }
        val candidates = mutableListOf<ArchitectureStore.ElementSelection>()
        if (store.allWalls().size == 1) {
            candidates.add(ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.WALL, store.allWalls().first().id))
        }
        if (store.allSlabs().size == 1) {
            candidates.add(ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.SLAB, store.allSlabs().first().id))
        }
        if (store.allStairs().size == 1) {
            candidates.add(ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.STAIR, store.allStairs().first().id))
        }
        if (store.allFrames().size == 1) {
            candidates.add(ArchitectureStore.ElementSelection(ArchitectureStore.ElementKind.FRAME, store.allFrames().first().id))
        }
        return if (candidates.size == 1) candidates.first() else null
    }

    private fun updateArchitectureWallParameters(
        id: String,
        thickness: Float,
        height: Float,
        inclinationDeg: Float,
        exteriorColor: Color,
        interiorColor: Color
    ) {
        val group = architecturePanelTargetGroup() ?: return
        val selectedWallIds = scene.selectedArchitectureElements(group)
            .filter { it.kind == ArchitectureStore.ElementKind.WALL }
            .map { it.id }
            .toSet()
        val targetIds = if (selectedWallIds.isEmpty()) setOf(id) else selectedWallIds
        var updated = 0
        targetIds.forEach { wallId ->
            if (scene.updateArchitectureWall(group, wallId, thickness, height, inclinationDeg, exteriorColor, interiorColor)) {
                updated++
            }
        }
        if (updated > 0) {
            statusModel.message = if (updated == 1) {
                "Wall parameters updated."
            } else {
                "Wall parameters updated for $updated walls."
            }
        }
    }

    private fun updateArchitectureSlabParameters(
        id: String,
        thickness: Float,
        topColor: Color,
        bottomColor: Color,
        sideColor: Color
    ) {
        val group = architecturePanelTargetGroup() ?: return
        val selectedSlabIds = scene.selectedArchitectureElements(group)
            .filter { it.kind == ArchitectureStore.ElementKind.SLAB }
            .map { it.id }
            .toSet()
        val targetIds = if (selectedSlabIds.isEmpty()) setOf(id) else selectedSlabIds
        var updated = 0
        targetIds.forEach { slabId ->
            if (scene.updateArchitectureSlab(group, slabId, thickness, topColor, bottomColor, sideColor)) {
                updated++
            }
        }
        if (updated > 0) {
            statusModel.message = if (updated == 1) {
                "Slab parameters updated."
            } else {
                "Slab parameters updated for $updated slabs."
            }
        }
    }

    private fun updateArchitectureStairParameters(
        id: String,
        height: Float,
        stepCount: Int,
        supportThickness: Float,
        railLeftEnabled: Boolean,
        railRightEnabled: Boolean,
        treadColor: Color,
        supportColor: Color
    ) {
        val group = architecturePanelTargetGroup() ?: return
        val selectedStairIds = scene.selectedArchitectureElements(group)
            .filter { it.kind == ArchitectureStore.ElementKind.STAIR }
            .map { it.id }
            .toSet()
        val targetIds = if (selectedStairIds.isEmpty()) setOf(id) else selectedStairIds
        var updated = 0
        targetIds.forEach { stairId ->
            if (
                scene.updateArchitectureStair(
                    group,
                    stairId,
                    height,
                    stepCount,
                    supportThickness,
                    railLeftEnabled,
                    railRightEnabled,
                    treadColor,
                    supportColor
                )
            ) {
                updated++
            }
        }
        if (updated > 0) {
            statusModel.message = if (updated == 1) {
                "Stair parameters updated."
            } else {
                "Stair parameters updated for $updated stairs."
            }
        }
    }

    private fun updateArchitectureFrameParameters(
        id: String,
        depth: Float,
        frameWidth: Float,
        color: Color,
        glazingEnabled: Boolean,
        glazingColor: Color
    ) {
        val group = architecturePanelTargetGroup() ?: return
        val selectedFrameIds = scene.selectedArchitectureElements(group)
            .filter { it.kind == ArchitectureStore.ElementKind.FRAME }
            .map { it.id }
            .toSet()
        val targetIds = if (selectedFrameIds.isEmpty()) setOf(id) else selectedFrameIds
        var updated = 0
        targetIds.forEach { frameId ->
            if (scene.updateArchitectureFrame(group, frameId, depth, frameWidth, color, glazingEnabled, glazingColor)) {
                updated++
            }
        }
        if (updated > 0) {
            statusModel.message = if (updated == 1) {
                "Frame parameters updated."
            } else {
                "Frame parameters updated for $updated frames."
            }
        }
    }

    private fun updateArchitectureElementName(
        kind: SketchUiOverlay.ArchitectureElementKind,
        id: String,
        name: String
    ) {
        val group = architecturePanelTargetGroup() ?: return
        val targetKind = when (kind) {
            SketchUiOverlay.ArchitectureElementKind.WALL -> ArchitectureStore.ElementKind.WALL
            SketchUiOverlay.ArchitectureElementKind.SLAB -> ArchitectureStore.ElementKind.SLAB
            SketchUiOverlay.ArchitectureElementKind.STAIR -> ArchitectureStore.ElementKind.STAIR
            SketchUiOverlay.ArchitectureElementKind.FRAME -> ArchitectureStore.ElementKind.FRAME
        }
        val selectedIds = scene.selectedArchitectureElements(group)
            .filter { it.kind == targetKind }
            .map { it.id }
            .toSet()
        val targetIds = if (selectedIds.isEmpty()) setOf(id) else selectedIds
        var updated = 0
        targetIds.forEach { targetId ->
            if (scene.updateArchitectureElementName(group, targetKind, targetId, name)) {
                updated++
            }
        }
        if (updated > 0) {
            statusModel.message = if (updated == 1) {
                "${targetKind.name.lowercase().replaceFirstChar { it.uppercase() }} name updated."
            } else {
                "${targetKind.name.lowercase().replaceFirstChar { it.uppercase() }} names updated for $updated elements."
            }
        }
    }

    private fun updateSelectedText(textId: String, value: String) {
        val group = scene.activeGroup()
        if (group.textStore.updateText(textId, value)) {
            statusModel.message = "Text updated."
        }
    }

    private fun updateSelectedTextSize(textId: String, size: Float) {
        val group = scene.activeGroup()
        if (group.textStore.updateSize(textId, size)) {
            statusModel.message = "Text size updated."
        }
    }

    private fun updateSelectedTextScreen(textId: String, screenText: Boolean) {
        val group = scene.activeGroup()
        if (group.textStore.updateScreenText(textId, screenText)) {
            statusModel.message = if (screenText) "Text set to screen mode." else "Text set to model mode."
        }
    }

    private fun groupInfo(): SketchUiOverlay.GroupInfo? {
        val editing = scene.isEditing()
        val targetInstance = if (editing) {
            scene.activeGroup()
        } else {
            val selected = scene.selectedGroups()
            if (selected.size == 1) selected.first() else null
        }
        val prototype = targetInstance?.prototype ?: return null
        return SketchUiOverlay.GroupInfo(prototype.id, prototype.name, prototype.gluedToSurface, editing)
    }

    private fun updateGroupName(name: String) {
        val target = groupPanelTarget() ?: return
        if (name.isNotBlank() && name != target.name) {
            target.name = name
            statusModel.message = "Object renamed."
            undoManager.commit("Rename Object")
            saveModel()
        }
    }

    private fun updateGroupGlue(glued: Boolean) {
        val target = groupPanelTarget() ?: return
        if (target.gluedToSurface != glued) {
            target.gluedToSurface = glued
            statusModel.message = if (glued) "Object glue enabled." else "Object glue disabled."
            undoManager.commit("Toggle Glue")
            saveModel()
        }
    }

    private fun groupPanelTarget(): GroupScene.ObjectPrototype? {
        return if (scene.isEditing()) {
            scene.activeGroup().prototype
        } else {
            val selected = scene.selectedGroups()
            if (selected.size == 1) selected.first().prototype else null
        }
    }

    private fun groupSelection() {
        objectPrototypeSelection()
    }

    private fun createVoxelGroup() {
        val group = scene.createVoxelGroup(color = statusModel.paintColor)
        scene.enterGroup(group)
        statusModel.message = "Voxel group created. Editing voxel group."
        toolController.setTool(ToolId.VOXEL)
        undoManager.commit("Create Voxel Group")
        saveModel()
    }

    private fun createArchitectureGroup() {
        var exited = false
        while (scene.exitGroup()) {
            exited = true
        }
        scene.clearAllSelections()
        statusModel.message = if (exited) {
            "Exited object edit mode. Architecture wall tool active."
        } else {
            "Architecture wall tool active."
        }
        toolController.setTool(ToolId.ARCH_WALL)
    }

    private fun ensureActiveVoxelGroupForTools(): GroupScene.GroupNode? {
        val current = scene.activeGroup()
        if (scene.isVoxelGroup(current)) {
            return current
        }
        val group = scene.createVoxelGroup(color = statusModel.paintColor)
        scene.enterGroup(group)
        statusModel.message = "Voxel group created. Editing voxel group."
        undoManager.commit("Create Voxel Group")
        saveModel()
        return group
    }

    private fun ensureActiveArchitectureGroupForTools(): GroupScene.GroupNode? {
        while (scene.exitGroup()) {
            // Architecture entities live at model level.
        }
        return scene.root
    }

    private data class WorldTriangle(
        val a: Vector3,
        val b: Vector3,
        val c: Vector3
    )

    private fun voxelizeSelectedFaces() {
        val sourceGroup = scene.activeGroup()
        val selectedFaces = sourceGroup.faceStore.getSelected().toList()
        if (selectedFaces.isEmpty()) {
            statusModel.message = "Select faces to voxelize."
            return
        }
        val worldTriangles = selectedFaces.map { tri ->
            WorldTriangle(
                sourceGroup.toWorld(tri.a),
                sourceGroup.toWorld(tri.b),
                sourceGroup.toWorld(tri.c)
            )
        }
        val targetGroup = if (scene.isVoxelGroup(sourceGroup)) {
            sourceGroup
        } else {
            ensureActiveVoxelGroupForTools()
        } ?: return
        val color = scene.voxelColor(targetGroup) ?: statusModel.paintColor
        val keys = linkedSetOf<VoxelStore.Key>()
        worldTriangles.forEach { tri ->
            val a = targetGroup.toLocal(tri.a)
            val b = targetGroup.toLocal(tri.b)
            val c = targetGroup.toLocal(tri.c)
            keys.addAll(voxelKeysForTriangle(a, b, c))
        }
        if (keys.isEmpty()) {
            statusModel.message = "Voxelization produced no voxels."
            return
        }
        val changed = scene.setVoxels(targetGroup, keys.map { it to color })
        if (changed > 0) {
            statusModel.message = "Voxelized ${selectedFaces.size} face(s) into $changed voxel(s)."
            undoManager.commit("Voxelize Faces")
            saveModel()
        } else {
            statusModel.message = "Voxelization produced no changes."
        }
    }

    private fun voxelKeysForTriangle(a: Vector3, b: Vector3, c: Vector3): Set<VoxelStore.Key> {
        val result = linkedSetOf<VoxelStore.Key>()
        val normal = Vector3(b).sub(a).crs(Vector3(c).sub(a))
        if (normal.len2() <= 1e-8f) {
            return result
        }
        val n = Vector3(normal).nor()
        val threshold = 0.5f * (kotlin.math.abs(n.x) + kotlin.math.abs(n.y) + kotlin.math.abs(n.z)) + 1e-3f
        val minX = floor(minOf(a.x, b.x, c.x).toDouble()).toInt() - 1
        val minY = floor(minOf(a.y, b.y, c.y).toDouble()).toInt() - 1
        val minZ = floor(minOf(a.z, b.z, c.z).toDouble()).toInt() - 1
        val maxX = floor(maxOf(a.x, b.x, c.x).toDouble()).toInt() + 1
        val maxY = floor(maxOf(a.y, b.y, c.y).toDouble()).toInt() + 1
        val maxZ = floor(maxOf(a.z, b.z, c.z).toDouble()).toInt() + 1

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val center = Vector3(x + 0.5f, y + 0.5f, z + 0.5f)
                    val signedDistance = Vector3(center).sub(a).dot(n)
                    if (kotlin.math.abs(signedDistance) > threshold) {
                        continue
                    }
                    val projected = Vector3(center).mulAdd(n, -signedDistance)
                    if (!pointInsideTriangleProjected(projected, a, b, c, n, 1e-3f)) {
                        continue
                    }
                    result.add(VoxelStore.Key(x, y, z))
                }
            }
        }
        addTriangleSampleVoxels(a, b, c, result)
        return result
    }

    private fun pointInsideTriangleProjected(
        p: Vector3,
        a: Vector3,
        b: Vector3,
        c: Vector3,
        normal: Vector3,
        eps: Float
    ): Boolean {
        val s0 = Vector3(b).sub(a).crs(Vector3(p).sub(a)).dot(normal)
        val s1 = Vector3(c).sub(b).crs(Vector3(p).sub(b)).dot(normal)
        val s2 = Vector3(a).sub(c).crs(Vector3(p).sub(c)).dot(normal)
        val sameSignPositive = s0 >= -eps && s1 >= -eps && s2 >= -eps
        val sameSignNegative = s0 <= eps && s1 <= eps && s2 <= eps
        return sameSignPositive || sameSignNegative
    }

    private fun addTriangleSampleVoxels(
        a: Vector3,
        b: Vector3,
        c: Vector3,
        output: MutableSet<VoxelStore.Key>
    ) {
        val step = 0.25f
        val maxEdge = maxOf(a.dst(b), b.dst(c), c.dst(a))
        val steps = maxOf(1, ceil((maxEdge / step).toDouble()).toInt())
        for (i in 0..steps) {
            for (j in 0..(steps - i)) {
                val u = i.toFloat() / steps.toFloat()
                val v = j.toFloat() / steps.toFloat()
                val w = 1f - u - v
                val point = Vector3(a).scl(w).mulAdd(b, u).mulAdd(c, v)
                output.add(
                    VoxelStore.Key(
                        floor((point.x + 1e-4f).toDouble()).toInt(),
                        floor((point.y + 1e-4f).toDouble()).toInt(),
                        floor((point.z + 1e-4f).toDouble()).toInt()
                    )
                )
            }
        }
    }

    private fun objectPrototypeSelection() {
        val created = scene.createGroupFromSelection()
        if (created != null) {
            statusModel.message = "Object created."
            undoManager.commit("Create Object")
            saveModel()
        }
    }

    private fun addAxisGuide() {
        val snap = lastSnap
        val point = snap?.world
        if (snap != null && snap.valid && point != null) {
            guideManager.addAxisGuide(point, snap.normal)
            statusModel.message = "Axis guide added."
        }
    }

    private fun addGridGuide() {
        val snap = lastSnap
        val point = snap?.world
        if (snap != null && snap.valid && point != null) {
            guideManager.addGridGuide(point, snap.normal)
            statusModel.message = "Grid guide added."
        }
    }

    private fun objectPrototypeInfo(): List<SketchUiOverlay.ObjectPrototypeInfo> {
        return scene.objectPrototypes().map { prototype ->
            SketchUiOverlay.ObjectPrototypeInfo(
                id = prototype.id,
                name = prototype.name,
                instanceCount = scene.objectPrototypeInstanceCount(prototype.id)
            )
        }
    }

    private fun startObjectPlacement(prototypeId: String) {
        val prototype = scene.objectPrototypeById(prototypeId) ?: return
        objectPlaceTool.setPrototype(prototype)
        toolController.setTool(ToolId.OBJECT_PLACE)
        statusModel.message = "Place object: ${prototype.name}"
    }

    private fun deleteObjectPrototype(prototypeId: String) {
        if (scene.deletePrototype(prototypeId)) {
            statusModel.message = "Object prototype deleted."
            undoManager.commit("Delete Prototype")
            saveModel()
        } else {
            statusModel.message = "Cannot delete: object has instances."
        }
    }

    private fun modelUnitInfo(): ModelUnit {
        return modelUnit
    }

    private fun updateModelUnit(name: String, size: Float) {
        if (modelUnit.name == name && modelUnit.size == size) {
            return
        }
        modelUnit.name = name
        modelUnit.size = size
        statusModel.message = "Model unit updated."
        undoManager.markChanged()
        saveModel()
    }

    private fun applySnapEpsilon(value: Float, save: Boolean) {
        if (snapEpsilon == value) {
            return
        }
        snapEpsilon = value
        snapper.snapPixels = value
        if (save) {
            undoManager.markChanged()
            saveModel()
        }
    }

    private fun setSnapEpsilon(value: Float) {
        applySnapEpsilon(value, true)
    }

    private fun applyGridSpacing(value: Float, save: Boolean) {
        val next = value.coerceAtLeast(1e-4f)
        if (gridSpacing == next) {
            return
        }
        gridSpacing = next
        snapper.gridSpacing = next
        if (save) {
            undoManager.markChanged()
            saveModel()
        }
    }

    private fun setGridSpacing(value: Float) {
        applyGridSpacing(value, true)
    }

    private fun walkthroughTuningInfo(): WalkthroughTuning {
        return WalkthroughTuning(
            jumpVelocity = walkCameraController.jumpVelocity,
            gravity = walkCameraController.gravity,
            heightAdjustSpeed = walkCameraController.heightAdjustSpeed
        )
    }

    private fun updateWalkthroughTuning(tuning: WalkthroughTuning) {
        val jump = tuning.jumpVelocity.coerceAtLeast(0.1f)
        val gravity = tuning.gravity.coerceAtLeast(0.1f)
        val adjust = tuning.heightAdjustSpeed.coerceAtLeast(0.1f)
        walkCameraController.jumpVelocity = jump
        walkCameraController.gravity = gravity
        walkCameraController.heightAdjustSpeed = adjust
        statusModel.message = "Walkthrough tuning updated."
    }

    private fun ungroupSelection() {
        val targets = scene.selectedGroups().toList()
        val architectureSelections = scene.selectedArchitectureElements(scene.root)
        val explodedArchitecture = if (architectureSelections.isNotEmpty()) {
            scene.explodeSelectedArchitectureElements(scene.root)
        } else {
            0
        }
        if (targets.isEmpty()) {
            if (explodedArchitecture > 0) {
                statusModel.message = "Exploded $explodedArchitecture architecture element(s)."
                undoManager.commit("Explode Architecture")
                saveModel()
            }
            return
        }
        val voxelTargets = targets.count { scene.isVoxelGroup(it) }
        val cachedLines = targets.filter { scene.isVoxelGroup(it) }.sumOf { it.lineStore.getSegments().size }
        val cachedFaces = targets.filter { scene.isVoxelGroup(it) }.sumOf { it.faceStore.getTriangles().size }
        if (voxelTargets > 0) {
            statusModel.message =
                "Exploding $voxelTargets voxel group(s)... cached lines $cachedLines faces $cachedFaces"
            // Defer actual explode one frame so the user sees feedback before heavy geometry transfer.
            Gdx.app.postRunnable {
                val count = scene.ungroupSelected()
                val changed = count + explodedArchitecture
                if (changed > 0) {
                    statusModel.message = if (explodedArchitecture > 0) {
                        "Ungrouped $count group(s), exploded $explodedArchitecture architecture element(s)."
                    } else {
                        "Ungrouped $count group(s)."
                    }
                    undoManager.commit("Explode")
                    saveModel()
                }
            }
            return
        }
        val count = scene.ungroupSelected()
        val changed = count + explodedArchitecture
        if (changed > 0) {
            statusModel.message = if (explodedArchitecture > 0) {
                "Ungrouped $count group(s), exploded $explodedArchitecture architecture element(s)."
            } else {
                "Ungrouped $count group(s)."
            }
            undoManager.commit("Explode")
            saveModel()
        }
    }

    private fun exitGroupEditMode(): Boolean {
        if (!scene.isEditing()) {
            return false
        }
        scene.exitGroup()
        statusModel.message = "Exited object edit."
        return true
    }

    private fun activeGroup(): GroupScene.GroupNode = scene.activeGroup()

    private fun activeLineStore(): com.github.alfu32.sketch.model.DraftLineStore {
        return activeGroup().lineStore
    }

    private fun activeFaceStore(): com.github.alfu32.sketch.model.DraftFaceStore {
        return activeGroup().faceStore
    }

    private fun activeDimensionStore(): com.github.alfu32.sketch.model.DraftDimensionStore {
        return activeGroup().dimensionStore
    }

    private fun activeTextStore(): com.github.alfu32.sketch.model.DraftTextStore {
        return activeGroup().textStore
    }

    private fun cutRectHole() {
        val group = scene.activeGroup()
        val faces = group.faceStore.getSelected()
        val segments = group.lineStore.getSelected()
        if (faces.isEmpty() || segments.isEmpty()) {
            statusModel.message = "Select faces and hole polyline first."
            return
        }
        val loops = orderedLoops(segments.toList())
        val loop = loops.firstOrNull { it.closed && it.points.size >= 3 }
        if (loop == null) {
            statusModel.message = "Hole polyline must be a closed loop."
            return
        }
        val count = group.faceStore.cutSelectedByPolygon(loop.points)
        if (count > 0) {
            statusModel.message = "Cut rect hole | triangles $count"
            undoManager.commit("Cut Rect Hole")
            saveModel()
        } else {
            statusModel.message = "Cut rect hole failed."
        }
    }

    private data class OrderedLoop(val points: List<Vector3>, val closed: Boolean)

    private data class VertexKey(val x: Int, val y: Int, val z: Int)

    private fun orderedLoops(segments: List<com.github.alfu32.sketch.model.DraftLineStore.Segment>): List<OrderedLoop> {
        if (segments.isEmpty()) {
            return emptyList()
        }
        val keyEps = 1e-3f
        fun vertexKey(point: Vector3): VertexKey {
            return VertexKey(
                kotlin.math.round(point.x / keyEps).toInt(),
                kotlin.math.round(point.y / keyEps).toInt(),
                kotlin.math.round(point.z / keyEps).toInt()
            )
        }
        val endpointMap = mutableMapOf<VertexKey, MutableList<com.github.alfu32.sketch.model.DraftLineStore.Segment>>()
        val segmentKeys = mutableMapOf<com.github.alfu32.sketch.model.DraftLineStore.Segment, Pair<VertexKey, VertexKey>>()
        val keyToPoint = mutableMapOf<VertexKey, Vector3>()
        segments.forEach { segment ->
            val a = vertexKey(segment.start).also { keyToPoint.putIfAbsent(it, Vector3(segment.start)) }
            val b = vertexKey(segment.end).also { keyToPoint.putIfAbsent(it, Vector3(segment.end)) }
            endpointMap.getOrPut(a) { mutableListOf() }.add(segment)
            endpointMap.getOrPut(b) { mutableListOf() }.add(segment)
            segmentKeys[segment] = Pair(a, b)
        }
        val loops = mutableListOf<OrderedLoop>()
        val visited = mutableSetOf<com.github.alfu32.sketch.model.DraftLineStore.Segment>()
        segments.forEach { start ->
            if (visited.contains(start)) {
                return@forEach
            }
            val keys = segmentKeys[start] ?: return@forEach
            val startKey = keys.first
            val points = mutableListOf<Vector3>()
            points.add(Vector3(keyToPoint[startKey] ?: start.start))
            var current = startKey
            var closed = false
            while (true) {
                val candidates = endpointMap[current].orEmpty().filter { it !in visited }
                if (candidates.isEmpty()) {
                    break
                }
                val nextSeg = candidates.first()
                visited.add(nextSeg)
                val (a, b) = segmentKeys[nextSeg]!!
                val nextKey = if (a == current) b else a
                points.add(Vector3(keyToPoint[nextKey] ?: if (a == current) nextSeg.end else nextSeg.start))
                current = nextKey
                if (current == startKey) {
                    closed = true
                    break
                }
            }
            if (closed && points.size > 1) {
                val first = points.first()
                val last = points.last()
                if (first.dst2(last) <= keyEps * keyEps) {
                    points.removeAt(points.lastIndex)
                }
            }
            loops.add(OrderedLoop(points, closed))
        }
        return loops
    }

    private fun totalEdgeCount(): Int {
        var count = scene.root.lineStore.getSegments().size
        scene.walkGroups(scene.root) { group ->
            count += group.lineStore.getSegments().size
        }
        return count
    }

    private fun totalFaceCount(): Int {
        var count = scene.root.faceStore.getTriangles().size
        scene.walkGroups(scene.root) { group ->
            count += group.faceStore.getTriangles().size
        }
        return count
    }

    private fun drawGroupSelectionHighlights() {
        val groups = scene.selectedGroups()
        if (groups.isEmpty()) {
            return
        }
        shapeRenderer.color = selectedLineColor
        groups.forEach { group ->
            val corners = group.orientedBoundsCorners(0.05f) ?: return@forEach
            drawWireBox(corners)
        }
    }

    private fun drawSelectedEntityBounds() {
        val bounds = computeSelectedEntityBounds() ?: return
        val expanded = expandBounds(bounds, 0.02f)
        val corners = cornersFromBounds(expanded)
        shapeRenderer.color = selectedEntityBoxColor
        drawWireBox(corners)
    }

    private fun drawActiveGroupEditBounds() {
        if (!scene.isEditing()) {
            return
        }
        val group = scene.activeGroup()
        val corners = group.orientedBoundsCorners(0.08f) ?: return
        shapeRenderer.color = editModeBoxColor
        drawWireBox(corners)
    }

    private fun computeSelectedEntityBounds(): com.badlogic.gdx.math.collision.BoundingBox? {
        val group = scene.activeGroup()
        val bounds = com.badlogic.gdx.math.collision.BoundingBox()
        var hasAny = false
        val selectedVoxels = scene.selectedVoxels(group)
        selectedVoxels.forEach { key ->
            val min = Vector3(key.x.toFloat(), key.y.toFloat(), key.z.toFloat())
            val max = Vector3((key.x + 1).toFloat(), (key.y + 1).toFloat(), (key.z + 1).toFloat())
            val corners = arrayOf(
                Vector3(min.x, min.y, min.z),
                Vector3(max.x, min.y, min.z),
                Vector3(max.x, min.y, max.z),
                Vector3(min.x, min.y, max.z),
                Vector3(min.x, max.y, min.z),
                Vector3(max.x, max.y, min.z),
                Vector3(max.x, max.y, max.z),
                Vector3(min.x, max.y, max.z)
            )
            corners.forEach { corner ->
                val world = group.toWorld(corner)
                if (!hasAny) {
                    bounds.set(world, world)
                    hasAny = true
                }
                bounds.ext(world)
            }
        }
        scene.selectedGroups().forEach { selectedGroup ->
            val groupBounds = selectedGroup.worldBounds() ?: return@forEach
            if (!hasAny) {
                bounds.set(groupBounds)
                hasAny = true
            } else {
                bounds.ext(groupBounds)
            }
        }
        scene.selectedArchitectureBounds(scene.root)?.let { architectureBounds ->
            if (!hasAny) {
                bounds.set(architectureBounds)
                hasAny = true
            } else {
                bounds.ext(architectureBounds)
            }
        }
        group.lineStore.getSelected().forEach { segment ->
            val a = group.toWorld(segment.start)
            val b = group.toWorld(segment.end)
            if (!hasAny) {
                bounds.set(a, a)
                hasAny = true
            }
            bounds.ext(a)
            bounds.ext(b)
        }
        group.faceStore.getSelected().forEach { tri ->
            val a = group.toWorld(tri.a)
            val b = group.toWorld(tri.b)
            val c = group.toWorld(tri.c)
            if (!hasAny) {
                bounds.set(a, a)
                hasAny = true
            }
            bounds.ext(a)
            bounds.ext(b)
            bounds.ext(c)
        }
        group.dimensionStore.getSelected().forEach { dimension ->
            val start = group.toWorld(dimension.start)
            val end = group.toWorld(dimension.end)
            val offsetPoint = group.toWorld(com.badlogic.gdx.math.Vector3(dimension.start).add(dimension.offset))
            if (!hasAny) {
                bounds.set(start, start)
                hasAny = true
            }
            bounds.ext(start)
            bounds.ext(end)
            bounds.ext(offsetPoint)
        }
        group.textStore.getSelected().forEach { text ->
            val pos = group.toWorld(text.position)
            if (!hasAny) {
                bounds.set(pos, pos)
                hasAny = true
            }
            bounds.ext(pos)
        }
        return if (hasAny) bounds else null
    }

    private fun drawSelectedVoxelHighlights() {
        val group = scene.activeGroup()
        if (!scene.isVoxelGroup(group)) {
            return
        }
        val selected = scene.selectedVoxels(group)
        if (selected.isEmpty()) {
            return
        }
        shapeRenderer.color = selectedLineColor
        selected.forEach { key ->
            val min = Vector3(key.x.toFloat(), key.y.toFloat(), key.z.toFloat())
            val max = Vector3((key.x + 1).toFloat(), (key.y + 1).toFloat(), (key.z + 1).toFloat())
            val corners = arrayOf(
                Vector3(min.x, min.y, min.z),
                Vector3(max.x, min.y, min.z),
                Vector3(max.x, min.y, max.z),
                Vector3(min.x, min.y, max.z),
                Vector3(min.x, max.y, min.z),
                Vector3(max.x, max.y, min.z),
                Vector3(max.x, max.y, max.z),
                Vector3(min.x, max.y, max.z)
            )
            corners.indices.forEach { idx ->
                corners[idx] = group.toWorld(corners[idx])
            }
            drawWireBox(corners)
        }
    }

    private fun expandBounds(
        bounds: com.badlogic.gdx.math.collision.BoundingBox,
        expandRatio: Float
    ): com.badlogic.gdx.math.collision.BoundingBox {
        if (expandRatio <= 0f) {
            return com.badlogic.gdx.math.collision.BoundingBox(bounds)
        }
        val center = com.badlogic.gdx.math.Vector3(bounds.min).lerp(bounds.max, 0.5f)
        val half = com.badlogic.gdx.math.Vector3(bounds.max).sub(bounds.min).scl(0.5f * (1f + expandRatio))
        val min = com.badlogic.gdx.math.Vector3(center).sub(half)
        val max = com.badlogic.gdx.math.Vector3(center).add(half)
        return com.badlogic.gdx.math.collision.BoundingBox(min, max)
    }

    private fun cornersFromBounds(bounds: com.badlogic.gdx.math.collision.BoundingBox): kotlin.Array<Vector3> {
        val min = bounds.min
        val max = bounds.max
        return arrayOf(
            Vector3(min.x, min.y, min.z),
            Vector3(max.x, min.y, min.z),
            Vector3(max.x, min.y, max.z),
            Vector3(min.x, min.y, max.z),
            Vector3(min.x, max.y, min.z),
            Vector3(max.x, max.y, min.z),
            Vector3(max.x, max.y, max.z),
            Vector3(min.x, max.y, max.z)
        )
    }

    private fun drawWireBox(corners: kotlin.Array<Vector3>) {
        if (corners.size < 8) {
            return
        }
        val c0 = corners[0]
        val c1 = corners[1]
        val c2 = corners[2]
        val c3 = corners[3]
        val c4 = corners[4]
        val c5 = corners[5]
        val c6 = corners[6]
        val c7 = corners[7]
        shapeRenderer.line(c0, c1)
        shapeRenderer.line(c1, c2)
        shapeRenderer.line(c2, c3)
        shapeRenderer.line(c3, c0)
        shapeRenderer.line(c4, c5)
        shapeRenderer.line(c5, c6)
        shapeRenderer.line(c6, c7)
        shapeRenderer.line(c7, c4)
        shapeRenderer.line(c0, c4)
        shapeRenderer.line(c1, c5)
        shapeRenderer.line(c2, c6)
        shapeRenderer.line(c3, c7)
    }

    private fun drawDashedLine(start: Vector3, end: Vector3, dashLength: Float, gapLength: Float) {
        val total = start.dst(end)
        if (total <= 1e-6f) {
            return
        }
        val dir = Vector3(end).sub(start).nor()
        var dist = 0f
        val a = Vector3()
        val b = Vector3()
        while (dist < total) {
            val dashEnd = kotlin.math.min(dist + dashLength, total)
            a.set(start).mulAdd(dir, dist)
            b.set(start).mulAdd(dir, dashEnd)
            shapeRenderer.line(a, b)
            dist = dashEnd + gapLength
        }
    }


    private fun setupLighting() {
        environment = Environment()
        shadowLight = DirectionalShadowLight(
            8192,
            8192,
            60f,
            60f,
            1f,
            300f
        )
        environment.add(shadowLight)
        environment.shadowMap = shadowLight
        mainLight = DirectionalLight()
        environment.add(mainLight)
        environment.add(
            DirectionalLight()
                .set(0.005f, 0.005f, 0.005f, 1.2f, 1.8f, 0.5f)
                .setColor(Color(0.005f, 0.005f, 0.005f, 0.15f))
        )
        updateLighting()
        modelBatch = ModelBatch(SketchShaderProvider({ shadowSettings }, { shadowLight }))
        shadowBatch = ModelBatch(DepthShaderProvider())
    }

    private fun applyLightingSettings(settings: LightingSettings) {
        val changed = shadowLightValue != settings.shadowLightValue ||
            shadowLightAlpha != settings.shadowLightAlpha ||
            directionalLightValue != settings.directionalLightValue ||
            directionalLightAlpha != settings.directionalLightAlpha ||
            ambientLightValue != settings.ambientLightValue ||
            ambientLightAlpha != settings.ambientLightAlpha ||
            specularLightValue != settings.specularLightValue ||
            specularLightAlpha != settings.specularLightAlpha
        if (!changed) {
            return
        }
        shadowLightValue = settings.shadowLightValue
        shadowLightAlpha = settings.shadowLightAlpha
        directionalLightValue = settings.directionalLightValue
        directionalLightAlpha = settings.directionalLightAlpha
        ambientLightValue = settings.ambientLightValue
        ambientLightAlpha = settings.ambientLightAlpha
        specularLightValue = settings.specularLightValue
        specularLightAlpha = settings.specularLightAlpha
        updateLighting()
        if (!restoringSnapshot) {
            undoManager.markChanged()
            saveModel()
        }
    }

    private fun applyShadowSettings(settings: ShadowSettings) {
        val changed = shadowBias != settings.shadowBias ||
            shadowNormalBias != settings.shadowNormalBias ||
            shadowPcfMode != settings.pcfMode ||
            shadowDither != settings.dither ||
            shadowUseCsm != settings.useCsm
        if (!changed) {
            return
        }
        shadowBias = settings.shadowBias
        shadowNormalBias = settings.shadowNormalBias
        shadowPcfMode = settings.pcfMode
        shadowDither = settings.dither
        shadowUseCsm = settings.useCsm
        if (!restoringSnapshot) {
            undoManager.markChanged()
            saveModel()
        }
    }

    private fun updateLighting() {
        shadowLight.set(shadowLightValue, shadowLightValue, shadowLightValue, -0.5f, -1.8f, -1.2f)
        shadowLight.setColor(Color(shadowLightValue, shadowLightValue, shadowLightValue, shadowLightAlpha))
        mainLight.set(directionalLightValue, directionalLightValue, directionalLightValue, -0.5f, -1.8f, -1.2f)
        mainLight.color.set(directionalLightValue, directionalLightValue, directionalLightValue, directionalLightAlpha)
        environment.set(
            ColorAttribute(
                ColorAttribute.AmbientLight,
                ambientLightValue,
                ambientLightValue,
                ambientLightValue,
                ambientLightAlpha
            )
        )
        environment.set(
            ColorAttribute(
                ColorAttribute.Specular,
                specularLightValue,
                specularLightValue,
                specularLightValue,
                specularLightAlpha
            )
        )
    }

    private fun setupMeshes() {
        faceMesh = Mesh(false, 1, 0,
            VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
            VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color")
        )
        groundMesh = buildGroundMesh(120f)
    }

    private fun setupRenderables() {
        faceFrontMaterial = Material(
            ColorAttribute.createDiffuse(Color.WHITE),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1f),
            IntAttribute(IntAttribute.CullFace, GL20.GL_BACK)
        )
        faceBackMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.8f, 0.83f, 0.93f, 1f)),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1f),
            IntAttribute(IntAttribute.CullFace, GL20.GL_FRONT)
        )
        groundMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.9f, 0.9f, 0.9f, 1f)),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.5f),
            IntAttribute(IntAttribute.CullFace, GL20.GL_BACK)
        )
        faceFrontRenderable = MeshRenderableProvider(faceMesh, faceFrontMaterial, GL20.GL_TRIANGLES)
        faceBackRenderable = MeshRenderableProvider(faceMesh, faceBackMaterial, GL20.GL_TRIANGLES)
        groundRenderable = MeshRenderableProvider(groundMesh, groundMaterial, GL20.GL_TRIANGLES)
    }

    private fun updateFaceMesh() {
        val triangles = mutableListOf<TriangleWorld>()
        scene.collectWorldTriangles { a, b, c, color, selected ->
            triangles.add(TriangleWorld(a, b, c, color, selected))
        }
        val vertexCount = triangles.size * 3
        if (vertexCount == 0) {
            return
        }
        val vertices = FloatArray(vertexCount * 10)
        var idx = 0
        triangles.forEach { tri ->
            val a = tri.a
            val b = tri.b
            val c = tri.c
            val color = if (tri.selected) selectedFaceColor else tri.color
            val normal = Vector3(b).sub(a).crs(Vector3(c).sub(a)).nor()
            idx = writeVertex(vertices, idx, a, normal, color)
            idx = writeVertex(vertices, idx, b, normal, color)
            idx = writeVertex(vertices, idx, c, normal, color)
        }
        if (faceMesh.maxVertices < vertexCount) {
            faceMesh.dispose()
            faceMesh = Mesh(false, vertexCount, 0,
                VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color")
            )
            faceFrontRenderable = MeshRenderableProvider(faceMesh, faceFrontMaterial, GL20.GL_TRIANGLES)
            faceBackRenderable = MeshRenderableProvider(faceMesh, faceBackMaterial, GL20.GL_TRIANGLES)
        }
        faceMesh.setVertices(vertices)
    }

    private data class TriangleWorld(
        val a: Vector3,
        val b: Vector3,
        val c: Vector3,
        val color: Color,
        val selected: Boolean
    )

    private fun writeVertex(
        buffer: FloatArray,
        start: Int,
        pos: Vector3,
        normal: Vector3,
        color: Color
    ): Int {
        var i = start
        buffer[i++] = pos.x
        buffer[i++] = pos.y
        buffer[i++] = pos.z
        buffer[i++] = normal.x
        buffer[i++] = normal.y
        buffer[i++] = normal.z
        buffer[i++] = color.r
        buffer[i++] = color.g
        buffer[i++] = color.b
        buffer[i++] = color.a
        return i
    }

    private fun buildGroundMesh(size: Float): Mesh {
        val half = size * 0.5f
        val vertices = floatArrayOf(
            -half, 0f, -half, 0f, 1f, 0f,
            half, 0f, half, 0f, 1f, 0f,
            half, 0f, -half, 0f, 1f, 0f,
            -half, 0f, -half, 0f, 1f, 0f,
            -half, 0f, half, 0f, 1f, 0f,
            half, 0f, half, 0f, 1f, 0f
        )
        return Mesh(true, 6, 0,
            VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal")
        ).apply { setVertices(vertices) }
    }

    private fun renderShadowPass() {
        shadowLight.begin(Vector3.Zero, shadowLight.direction)
        shadowBatch.begin(shadowLight.camera)
        shadowBatch.render(faceFrontRenderable)
        shadowBatch.render(faceBackRenderable)
        shadowBatch.end()
        shadowLight.end()
    }

    private class MeshRenderableProvider(
        private val mesh: Mesh,
        private val material: Material,
        private val primitiveType: Int
    ) : RenderableProvider {
        override fun getRenderables(renderables: Array<Renderable>, pool: Pool<Renderable>) {
            if (mesh.numVertices == 0) {
                return
            }
            val renderable = pool.obtain()
            renderable.material = material
            renderable.meshPart.mesh = mesh
            renderable.meshPart.offset = 0
            renderable.meshPart.size = mesh.numVertices
            renderable.meshPart.primitiveType = primitiveType
            renderable.worldTransform.idt()
            renderables.add(renderable)
        }
    }
}

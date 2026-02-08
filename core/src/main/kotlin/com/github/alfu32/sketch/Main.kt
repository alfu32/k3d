package com.github.alfu32.sketch

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
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
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import com.github.alfu32.sketch.input.GuideManager
import com.github.alfu32.sketch.input.CameraEventRouter
import com.github.alfu32.sketch.input.CameraScrollForwarder
import com.github.alfu32.sketch.input.SnapResult
import com.github.alfu32.sketch.input.Snapper
import com.github.alfu32.sketch.input.ToolPointerProcessor
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.model.ModelPersistence
import com.github.alfu32.sketch.model.ModelCleanup
import com.github.alfu32.sketch.model.ModelUnit
import com.github.alfu32.sketch.render.SketchShaderProvider
import com.github.alfu32.sketch.console.AppFacade
import com.github.alfu32.sketch.console.ConsoleGroovyRuntime
import com.github.alfu32.sketch.console.CameraFacade
import com.github.alfu32.sketch.console.ConsolePaths
import com.github.alfu32.sketch.console.ConsoleThread
import com.github.alfu32.sketch.console.ConsoleUtils
import com.github.alfu32.sketch.console.LightingFacade
import com.github.alfu32.sketch.console.SelectionFacade
import com.github.alfu32.sketch.console.TerminalController
import com.github.alfu32.sketch.console.UnitFacade
import com.github.alfu32.sketch.console.SaveFacade
import com.github.alfu32.sketch.tui.ConsoleTui
import com.github.alfu32.sketch.tui.HistoryManager
import com.github.alfu32.sketch.tui.OutputPane
import com.github.alfu32.sketch.K3DVersion
import com.badlogic.gdx.Graphics
import com.github.alfu32.sketch.plugin.PluginHost
import com.github.alfu32.sketch.tools.CircleTool
import com.github.alfu32.sketch.tools.ConstructionLineTool
import com.github.alfu32.sketch.tools.CutHolesTool
import com.github.alfu32.sketch.tools.CutHolesTool2
import com.github.alfu32.sketch.tools.CutOut3Tool
import com.github.alfu32.sketch.tools.FaceOutlineTool
import com.github.alfu32.sketch.tools.LinearDimensionTool
import com.github.alfu32.sketch.tools.LineOffsetTool
import com.github.alfu32.sketch.tools.LineTool
import com.github.alfu32.sketch.tools.MoveTool
import com.github.alfu32.sketch.tools.ObjectPlaceTool
import com.github.alfu32.sketch.tools.PaintTool
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
import com.github.alfu32.sketch.ui.SimpleTool
import com.github.alfu32.sketch.ui.SketchUiOverlay
import com.github.alfu32.sketch.ui.LightingSettings
import com.github.alfu32.sketch.ui.ShadowSettings
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.ToolController
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolInputProcessor
import com.github.alfu32.sketch.ui.PluginToolAdapter
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.file.FileChooser
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter
import com.kotcrab.vis.ui.widget.file.FileTypeFilter
import java.io.File

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms. */
class Main(private val startupArgs: kotlin.Array<String> = emptyArray()) : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var cameraController: CameraInputController
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
    private val selectedEntityBoxColor = Color(0.2f, 0.7f, 0.95f, 1f)
    private val editModeBoxColor = Color(1f, 0.6f, 0.2f, 1f)
    private val selectedLineWidth = 8f
    private lateinit var toolController: ToolController
    private lateinit var toolInput: ToolInputProcessor
    private lateinit var uiOverlay: SketchUiOverlay
    private lateinit var toolPointer: ToolPointerProcessor
    private val polylineSettings = PolylineSettings()
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
    private var consoleThread: ConsoleThread? = null
    private var consoleRuntime: ConsoleGroovyRuntime? = null
    private var consoleTerminal: TerminalController? = null
    private lateinit var undoManager: com.github.alfu32.sketch.model.UndoRedoManager
    private var restoringSnapshot = false

    override fun create() {
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

        cameraController = ShiftCameraController(camera, this::pickPanPoint).apply {
            rotateButton = Input.Buttons.RIGHT
            translateButton = Input.Buttons.RIGHT
        }
        cameraController.target.set(cameraTarget)
        installDir = resolveInstallDir()
        statusModel = StatusModel(
            activeTool = ToolId.SELECT,
            message = "Select entities.",
            inputBuffer = ""
        )
        scene = GroupScene(Color(0.8f, 0.8f, 0.8f, 1f))
        modelCleanup = ModelCleanup(scene)
        guideManager = GuideManager()
        snapper = Snapper(camera, scene, guideManager, gridSpacing, snapEpsilon)
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
                SelectTool(scene, camera),
                LineTool(scene) { toolController.setTool(ToolId.SELECT) },
                ConstructionLineTool(scene) { toolController.setTool(ToolId.SELECT) },
                PolylineToolInternal(scene, polylineSettings),
                DoubleLineToolInternal(scene, polylineSettings),
                FaceOutlineTool(scene),
                LineOffsetTool(scene),
                CutHolesTool(scene) { toolController.setTool(ToolId.SELECT) },
                CutHolesTool2(scene) { toolController.setTool(ToolId.SELECT) },
                CutOut3Tool(scene) { toolController.setTool(ToolId.SELECT) },
                RectangleTool(scene),
                SurfaceRectangleTool(scene),
                QuadTool(scene),
                CircleTool(scene),
                LinearDimensionTool(scene),
                TextTool(scene, camera),
                PushPullTool(scene, camera),
                MoveTool(scene),
                RotateTool(scene),
                ScaleTool(scene),
                StretchTool(scene),
                PaintTool(scene, camera) { statusModel.paintColor.cpy() },
                SimpleTool(ToolId.ERASER, "Click to erase edges."),
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
            uiCapturesInput = { uiOverlay.isUiCapturingInput() }
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
            { cameraTarget },
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
            lightingSettings,
            ::applyLightingSettings,
            shadowSettings,
            ::applyShadowSettings,
            polylineSettings
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
        listOf(
            ToolId.SELECT,
            ToolId.LINE,
            ToolId.CONSTRUCTION_LINE,
            ToolId.RECTANGLE,
            ToolId.SURFACE_RECTANGLE,
            ToolId.QUAD,
            ToolId.CIRCLE,
            ToolId.FACE_OUTLINE,
            ToolId.LINE_OFFSET,
            ToolId.CUT_HOLES,
            ToolId.CUT_HOLES_2,
            ToolId.CUT_OUT_3,
            ToolId.LINEAR_DIMENSION,
            ToolId.TEXT,
            ToolId.PUSH_PULL,
            ToolId.MOVE,
            ToolId.ROTATE,
            ToolId.SCALE,
            ToolId.STRETCH,
            ToolId.PAINT,
            ToolId.ERASER
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
                return uiOverlay.isUiCapturingInput()
            }

            override fun keyUp(keycode: Int): Boolean {
                return uiOverlay.isUiCapturingInput()
            }

            override fun keyTyped(character: Char): Boolean {
                return uiOverlay.isUiCapturingInput()
            }
        }
        val cameraScrollForwarder = CameraScrollForwarder(cameraController)
        val cameraEventRouter = CameraEventRouter(cameraController)
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
        startConsoleIfRequested()
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
        cameraController.update()
        cameraTarget.set(cameraController.target)
        camera.up.set(0f, 1f, 0f)
        camera.lookAt(cameraTarget)
        camera.update()
        handleGlobalDistanceShortcut()
        updateCursorStatus()
        undoManager.update()
        pluginHost.dispatchUpdate(Gdx.graphics.deltaTime)
        toolController.update(Gdx.graphics.deltaTime)

        updateFaceMesh()
        shadowLight.update(camera)
        renderShadowPass()

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        val skyColor=Color(0.6f,0.75f,0.9f,1f,)
        Gdx.gl.glClearColor(0.6f, 0.75f, 0.9f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        Gdx.gl.glLineWidth(2f)
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawGrid(20, gridSpacing)
        shapeRenderer.end()

        modelBatch.begin(camera)
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
        drawDimensions()
        toolController.render(shapeRenderer)
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
        val size = 0.15f
        shapeRenderer.color = Color(0.95f, 0.85f, 0.2f, 1f)
        shapeRenderer.line(hit.x - size, hit.y, hit.z, hit.x + size, hit.y, hit.z)
        shapeRenderer.line(hit.x, hit.y, hit.z - size, hit.x, hit.y, hit.z + size)
        if (snap.type != com.github.alfu32.sketch.input.SnapType.NONE) {
            val snapSize = 0.08f
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

    private fun exportSvgView(file: File) {
        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        val view = Matrix4(camera.view)
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
            camera.project(v, 0f, 0f, width, height)
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
        val consoleUtils = ConsoleUtils(outputPane)
        val appFacade = AppFacade(Gdx.app)
        val selectionFacade = SelectionFacade(scene)
        val unitFacade = UnitFacade({ modelUnit }, ::updateModelUnit)
        val saveFacade = SaveFacade({ modelFile }, ::setSaveName)
        val cameraFacade = CameraFacade(camera, cameraTarget) { camera.update() }
        val lightingFacade = LightingFacade(lightingSettings, shadowSettings) {
            applyLightingSettings(lightingSettings)
            applyShadowSettings(shadowSettings)
            uiOverlay.refreshLightingControls()
        }
        val runtime = ConsoleGroovyRuntime(
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
                "version" to K3DVersion()
            )
        )
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

        spriteBatch.projectionMatrix = camera.combined
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
        val screenPos = camera.project(textPos)
        val screenA = camera.project(Vector3(lineStart))
        val screenB = camera.project(Vector3(lineEnd))
        val angleRad = kotlin.math.atan2(screenB.y - screenA.y, screenB.x - screenA.x)
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
        val color = if (selected) selectedLineColor else Color(0.1f, 0.1f, 0.1f, 1f)
        drawRotatedTextScaled(label, screenPos.x, screenPos.y, angleDeg, scale, color)
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
        val screenPos = camera.project(Vector3(position))
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
        val screenOrigin = camera.project(Vector3(position))
        val screenU = camera.project(Vector3(position).add(u))
        val screenV = camera.project(Vector3(position).add(v))
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
        val toPoint = Vector3(worldPoint).sub(camera.position)
        val depth = toPoint.dot(camera.direction)
        if (depth <= 0f) {
            return 0.01f
        }
        val viewportHeight = 2f * depth * kotlin.math.tan(Math.toRadians(camera.fieldOfView.toDouble() / 2.0)).toFloat()
        return viewportHeight / Gdx.graphics.height
    }

    private fun formatMeasurement(value: Float, unitName: String): String {
        val formatted = String.format(java.util.Locale.US, "%.3f", value)
            .trimEnd('0')
            .trimEnd('.')
        return if (unitName.isBlank()) formatted else "$formatted $unitName"
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
            cameraTarget,
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
            cameraController.target.set(cameraTarget)
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

    private fun deleteSelection() {
        val edges = activeLineStore().deleteSelected()
        val faces = activeFaceStore().deleteSelected()
        val dimensions = activeDimensionStore().deleteSelected()
        val texts = activeTextStore().deleteSelected()
        val groups = scene.deleteSelectedGroups()
        if (edges + faces + dimensions + texts + groups > 0) {
            statusModel.message =
                "Deleted | edges $edges faces $faces dimensions $dimensions texts $texts groups $groups"
            if (groups > 0 && edges + faces == 0) {
                undoManager.commit("Delete")
                saveModel()
            }
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
            cameraTarget,
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
                    cameraTarget,
                    lightingSettings,
                    shadowSettings,
                    modelUnit,
                    snapEpsilon,
                    gridSpacing,
                    undoManager.exportHistory()
                )
            }
            cameraController.target.set(cameraTarget)
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
        return SketchUiOverlay.SelectionInfo(
            edgeCount = activeLineStore().getSelected().size,
            faceCount = activeFaceStore().getSelected().size,
            groupCount = scene.selectedGroups().size,
            dimensionCount = group.dimensionStore.getSelected().size,
            textCount = selectedTexts.size,
            selectedTextId = selectedText?.id,
            selectedTextValue = selectedText?.text,
            selectedTextSize = selectedText?.size,
            selectedTextScreen = selectedText?.screenText
        )
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

    private fun ungroupSelection() {
        val count = scene.ungroupSelected()
        if (count > 0) {
            statusModel.message = "Ungrouped $count group(s)."
            undoManager.commit("Ungroup")
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
        scene.selectedGroups().forEach { selectedGroup ->
            val groupBounds = selectedGroup.worldBounds() ?: return@forEach
            if (!hasAny) {
                bounds.set(groupBounds)
                hasAny = true
            } else {
                bounds.ext(groupBounds)
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

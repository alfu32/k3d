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
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
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
import com.github.alfu32.sketch.plugin.PluginHost
import com.github.alfu32.sketch.tools.CircleTool
import com.github.alfu32.sketch.tools.LineTool
import com.github.alfu32.sketch.tools.MoveTool
import com.github.alfu32.sketch.tools.ObjectPlaceTool
import com.github.alfu32.sketch.tools.PaintTool
import com.github.alfu32.sketch.tools.PushPullTool
import com.github.alfu32.sketch.tools.QuadTool
import com.github.alfu32.sketch.tools.RectangleTool
import com.github.alfu32.sketch.tools.RotateTool
import com.github.alfu32.sketch.tools.SurfaceRectangleTool
import com.github.alfu32.sketch.tools.SelectTool
import com.github.alfu32.sketch.tools.ScaleTool
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

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms. */
class Main(private val startupArgs: kotlin.Array<String> = emptyArray()) : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var cameraController: CameraInputController
    private lateinit var shapeRenderer: ShapeRenderer
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
    private val selectedFaceColor = Color(0f, 0f, 1f, 0.4f)
    private val selectedLineColor = Color(0f, 0f, 1f, 1f)
    private val selectedLineWidth = 4f
    private lateinit var toolController: ToolController
    private lateinit var toolInput: ToolInputProcessor
    private lateinit var uiOverlay: SketchUiOverlay
    private lateinit var toolPointer: ToolPointerProcessor
    private lateinit var statusModel: StatusModel
    private lateinit var scene: GroupScene
    private lateinit var modelCleanup: ModelCleanup
    private lateinit var guideManager: GuideManager
    private lateinit var snapper: Snapper
    private var lastSnap: SnapResult? = null
    private val gridSpacing = 1f
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
                LineTool(scene),
                RectangleTool(scene),
                SurfaceRectangleTool(scene),
                QuadTool(scene),
                CircleTool(scene),
                PushPullTool(scene, camera),
                MoveTool(scene),
                RotateTool(scene),
                ScaleTool(scene),
                PaintTool(scene, camera) { statusModel.paintColor.cpy() },
                SimpleTool(ToolId.ERASER, "Click to erase edges."),
                objectPlaceTool
            )
        )
        toolInput = ToolInputProcessor(
            toolController,
            guideManager,
            ::runCleanup,
            ::clearSelection,
            ::deleteSelection,
            ::groupSelection,
            ::objectPrototypeSelection,
            ::ungroupSelection,
            ::exitGroupEditMode
        ) { lastSnap }
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
            java.io.File(installDir, "plugins"),
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
            ::groupInfo,
            ::updateGroupName,
            ::updateGroupGlue,
            ::objectPrototypeInfo,
            ::startObjectPlacement,
            ::deleteObjectPrototype,
            ::modelUnitInfo,
            ::updateModelUnit,
            { snapEpsilon },
            ::setSnapEpsilon,
            lightingSettings,
            ::applyLightingSettings,
            shadowSettings,
            ::applyShadowSettings
        )
        
        // Set up plugin host for UI
        uiOverlay.setPluginHost(pluginHost)
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
        listOf(
            ToolId.SELECT,
            ToolId.LINE,
            ToolId.RECTANGLE,
            ToolId.SURFACE_RECTANGLE,
            ToolId.QUAD,
            ToolId.CIRCLE,
            ToolId.PUSH_PULL,
            ToolId.MOVE,
            ToolId.ROTATE,
            ToolId.SCALE,
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
        
        toolPointer = ToolPointerProcessor(toolController, snapper)
        val cameraScrollForwarder = CameraScrollForwarder(cameraController)
        val cameraEventRouter = CameraEventRouter(cameraController)
        Gdx.input.inputProcessor = InputMultiplexer(
            cameraScrollForwarder,
            uiOverlay.stage,
            toolPointer,
            toolInput,
            cameraEventRouter
        )

        modelFile = resolveModelFile(startupArgs)

        shapeRenderer = ShapeRenderer()
        setupLighting()
        loadModel()
        applyLightingSettings(lightingSettings)
        applyShadowSettings(shadowSettings)
        uiOverlay.refreshLightingControls()
        scene.setChangeListener { saveModel() }
        pluginHost.loadCatalog()
        pluginHost.reloadEnabledAndInit()
        setupMeshes()
        setupRenderables()
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
        updateCursorStatus()
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
        drawGrid(20, 1f)
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
        toolController.render(shapeRenderer)
        drawPluginLines()
        shapeRenderer.end()

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
        shapeRenderer.dispose()
        faceMesh.dispose()
        groundMesh.dispose()
        modelBatch.dispose()
        shadowBatch.dispose()
        shadowLight.dispose()
        uiOverlay.dispose()
        if (VisUI.isLoaded()) {
            VisUI.dispose()
        }
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

    private fun drawDraftLines() {
        val defaultColor = Color(0.2f, 0.2f, 0.2f, 1f)
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
        }
        Gdx.gl.glLineWidth(2f)
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
        drawGroupSelectionHighlights()
    }

    private fun runCleanup() {
        val startEdges = totalEdgeCount()
        val startFaces = totalFaceCount()
        statusModel.message = "Cleanup start | edges $startEdges faces $startFaces"
        modelCleanup.run()
        val endEdges = totalEdgeCount()
        val endFaces = totalFaceCount()
        statusModel.message = "Cleanup done | edges $endEdges faces $endFaces"
        saveModel()
    }

    private fun clearSelection() {
        scene.clearAllSelections()
        statusModel.message = "Selection cleared."
    }

    private fun deleteSelection() {
        val edges = activeLineStore().deleteSelected()
        val faces = activeFaceStore().deleteSelected()
        val groups = scene.deleteSelectedGroups()
        if (edges + faces + groups > 0) {
            statusModel.message = "Deleted | edges $edges faces $faces groups $groups"
            if (groups > 0 && edges + faces == 0) {
                saveModel()
            }
        }
    }

    private fun flipSelectedFaces() {
        val flipped = activeFaceStore().flipSelected()
        if (flipped > 0) {
            statusModel.message = "Flipped faces: $flipped"
        }
    }

    private fun saveModel() {
        ModelPersistence.save(modelFile, scene, camera, cameraTarget, lightingSettings, shadowSettings, modelUnit, snapEpsilon)
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
                { value -> applySnapEpsilon(value, false) }
            )
            scene.applyChangeListenerToAll()
            if (result.ok && result.needsResave) {
                ModelPersistence.save(modelFile, scene, camera, cameraTarget, lightingSettings, shadowSettings, modelUnit, snapEpsilon)
            }
            cameraController.target.set(cameraTarget)
            statusModel.message = "Loaded ${modelFile.name}"
        } else {
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
            fileArg = "sketch3d.skate.json"
        }
        return java.io.File(fileArg).absoluteFile
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
        return SketchUiOverlay.SelectionInfo(
            edgeCount = activeLineStore().getSelected().size,
            faceCount = activeFaceStore().getSelected().size,
            groupCount = scene.selectedGroups().size
        )
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
            saveModel()
        }
    }

    private fun updateGroupGlue(glued: Boolean) {
        val target = groupPanelTarget() ?: return
        if (target.gluedToSurface != glued) {
            target.gluedToSurface = glued
            statusModel.message = if (glued) "Object glue enabled." else "Object glue disabled."
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
        saveModel()
    }

    private fun applySnapEpsilon(value: Float, save: Boolean) {
        if (snapEpsilon == value) {
            return
        }
        snapEpsilon = value
        snapper.snapPixels = value
        if (save) {
            saveModel()
        }
    }

    private fun setSnapEpsilon(value: Float) {
        applySnapEpsilon(value, true)
    }

    private fun ungroupSelection() {
        val count = scene.ungroupSelected()
        if (count > 0) {
            statusModel.message = "Ungrouped $count group(s)."
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
            val corners = group.orientedBoundsCorners() ?: return@forEach
            drawWireBox(corners)
        }
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
        shadowLightValue = settings.shadowLightValue
        shadowLightAlpha = settings.shadowLightAlpha
        directionalLightValue = settings.directionalLightValue
        directionalLightAlpha = settings.directionalLightAlpha
        ambientLightValue = settings.ambientLightValue
        ambientLightAlpha = settings.ambientLightAlpha
        specularLightValue = settings.specularLightValue
        specularLightAlpha = settings.specularLightAlpha
        updateLighting()
    }

    private fun applyShadowSettings(settings: ShadowSettings) {
        shadowBias = settings.shadowBias
        shadowNormalBias = settings.shadowNormalBias
        shadowPcfMode = settings.pcfMode
        shadowDither = settings.dither
        shadowUseCsm = settings.useCsm
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

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
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import com.github.alfu32.sketch.input.GuideManager
import com.github.alfu32.sketch.input.SnapResult
import com.github.alfu32.sketch.input.Snapper
import com.github.alfu32.sketch.input.ToolPointerProcessor
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.model.ModelPersistence
import com.github.alfu32.sketch.model.ModelCleanup
import com.github.alfu32.sketch.render.SketchShaderProvider
import com.github.alfu32.sketch.tools.CircleTool
import com.github.alfu32.sketch.tools.LineTool
import com.github.alfu32.sketch.tools.MoveTool
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
import com.kotcrab.vis.ui.VisUI

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms. */
class Main(private val startupArgs: kotlin.Array<String> = emptyArray()) : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var cameraController: CameraInputController
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var faceMesh: Mesh
    private lateinit var selectedFaceMesh: Mesh
    private lateinit var groundMesh: Mesh
    private lateinit var modelBatch: ModelBatch
    private lateinit var shadowBatch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var shadowLight: DirectionalShadowLight
    private lateinit var mainLight: DirectionalLight
    private lateinit var faceFrontMaterial: Material
    private lateinit var faceBackMaterial: Material
    private lateinit var selectedFaceMaterial: Material
    private lateinit var groundMaterial: Material
    private lateinit var faceFrontRenderable: MeshRenderableProvider
    private lateinit var faceBackRenderable: MeshRenderableProvider
    private lateinit var selectedFaceRenderable: MeshRenderableProvider
    private lateinit var groundRenderable: MeshRenderableProvider
    private var selectedFaceVertexCount = 0
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

        cameraController = ShiftCameraController(camera).apply {
            rotateButton = Input.Buttons.RIGHT
            translateButton = Input.Buttons.RIGHT
        }
        statusModel = StatusModel(
            activeTool = ToolId.SELECT,
            message = "Select entities.",
            inputBuffer = ""
        )
        scene = GroupScene(Color(0.8f, 0.8f, 0.8f, 1f))
        modelCleanup = ModelCleanup(scene)
        guideManager = GuideManager()
        snapper = Snapper(camera, scene, guideManager, gridSpacing)
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
                SimpleTool(ToolId.ERASER, "Click to erase edges.")
            )
        )
        toolInput = ToolInputProcessor(
            toolController,
            guideManager,
            ::runCleanup,
            ::clearSelection,
            ::deleteSelection,
            ::groupSelection,
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
            lightingSettings,
            ::applyLightingSettings,
            shadowSettings,
            ::applyShadowSettings
        )
        toolPointer = ToolPointerProcessor(toolController, snapper)
        Gdx.input.inputProcessor = InputMultiplexer(
            uiOverlay.stage,
            toolPointer,
            toolInput,
            cameraController
        )

        modelFile = resolveModelFile(startupArgs)

        shapeRenderer = ShapeRenderer()
        setupLighting()
        loadModel()
        applyLightingSettings(lightingSettings)
        applyShadowSettings(shadowSettings)
        uiOverlay.refreshLightingControls()
        scene.setChangeListener { saveModel() }
        setupMeshes()
        setupRenderables()
    }

    override fun render() {
        updateCameraControls()
        cameraController.update()
        updateCursorStatus()

        updateFaceMesh()
        updateSelectedFaceMesh()
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

        if (selectedFaceVertexCount > 0) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            modelBatch.begin(camera)
            modelBatch.render(selectedFaceRenderable, environment)
            modelBatch.end()
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawAxes(2.5f)
        drawActiveGroupAxes(1.8f)
        drawGuides()
        drawCursor()
        drawSelectionHighlights()
        drawDraftLines()
        toolController.render(shapeRenderer)
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
        shapeRenderer.dispose()
        faceMesh.dispose()
        selectedFaceMesh.dispose()
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

    private fun updateCameraControls() {
        val target = cameraController.target
        val distance = camera.position.dst(target)
        val safeDistance = kotlin.math.max(distance, 0.01f)
        val fovRad = camera.fieldOfView * MathUtils.degreesToRadians
        val worldHeight = 2f * safeDistance * kotlin.math.tan(fovRad * 0.5f)
        val unitsPerPixel = worldHeight / kotlin.math.max(camera.viewportHeight, 1f)
        cameraController.translateUnits = unitsPerPixel * 60f
        cameraController.scrollFactor = kotlin.math.max(0.02f, safeDistance * 0.25f)
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
        shapeRenderer.color = Color(0.2f, 0.2f, 0.2f, 1f)
        scene.collectWorldLines { start, end ->
            shapeRenderer.line(start.x, start.y, start.z, end.x, end.y, end.z)
        }
    }

    private fun drawSelectionHighlights() {
        val selectedEdges = activeLineStore().getSelected()
        if (selectedEdges.isNotEmpty()) {
            shapeRenderer.color = Color(0.25f, 0.55f, 0.95f, 1f)
            Gdx.gl.glLineWidth(6f)
            selectedEdges.forEach { segment ->
                val a = activeGroup().toWorld(segment.start)
                val b = activeGroup().toWorld(segment.end)
                shapeRenderer.line(a.x, a.y, a.z, b.x, b.y, b.z)
            }
            Gdx.gl.glLineWidth(4f)
        }
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
        ModelPersistence.save(modelFile, scene, camera, lightingSettings, shadowSettings)
    }

    private fun loadModel() {
        if (modelFile.exists()) {
            val backup = java.io.File(modelFile.absolutePath + ".bak")
            modelFile.copyTo(backup, overwrite = true)
            val result = ModelPersistence.load(
                modelFile,
                scene,
                camera,
                lightingSettings,
                shadowSettings
            )
            scene.applyChangeListenerToAll()
            if (result.ok && result.needsResave) {
                ModelPersistence.save(modelFile, scene, camera, lightingSettings, shadowSettings)
            }
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

    private fun selectionInfo(): SketchUiOverlay.SelectionInfo {
        return SketchUiOverlay.SelectionInfo(
            edgeCount = activeLineStore().getSelected().size,
            faceCount = activeFaceStore().getSelected().size,
            groupCount = scene.selectedGroups().size
        )
    }

    private fun groupInfo(): SketchUiOverlay.GroupInfo? {
        val editing = scene.isEditing()
        val target = if (editing) {
            scene.activeGroup()
        } else {
            val selected = scene.selectedGroups()
            if (selected.size == 1) selected.first() else null
        }
        return target?.let {
            SketchUiOverlay.GroupInfo(it.id, it.name, it.gluedToSurface, editing)
        }
    }

    private fun updateGroupName(name: String) {
        val target = groupPanelTarget() ?: return
        if (name.isNotBlank() && name != target.name) {
            target.name = name
            statusModel.message = "Group renamed."
            saveModel()
        }
    }

    private fun updateGroupGlue(glued: Boolean) {
        val target = groupPanelTarget() ?: return
        if (target.gluedToSurface != glued) {
            target.gluedToSurface = glued
            statusModel.message = if (glued) "Group glue enabled." else "Group glue disabled."
            saveModel()
        }
    }

    private fun groupPanelTarget(): GroupScene.GroupNode? {
        return if (scene.isEditing()) {
            scene.activeGroup()
        } else {
            val selected = scene.selectedGroups()
            if (selected.size == 1) selected.first() else null
        }
    }

    private fun groupSelection() {
        val created = scene.createGroupFromSelection()
        if (created != null) {
            statusModel.message = "Grouped."
            saveModel()
        }
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
        statusModel.message = "Exited group edit."
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
        shapeRenderer.color = Color(0.25f, 0.55f, 0.95f, 1f)
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

    private class ShiftCameraController(camera: PerspectiveCamera) : CameraInputController(camera) {
        private val panPlane = Plane()
        private val panPoint = Vector3()
        private val lastPanPoint = Vector3()
        private var panning = false

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            val shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
                Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
            if (shift) {
                translateButton = Input.Buttons.RIGHT
                rotateButton = -1
            } else {
                rotateButton = Input.Buttons.RIGHT
                translateButton = -1
            }
            return super.touchDown(screenX, screenY, pointer, button)
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            val shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
                Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
            val right = Gdx.input.isButtonPressed(Input.Buttons.RIGHT)
            if (shift && right) {
                val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
                panPlane.set(camera.direction, target)
                if (!Intersector.intersectRayPlane(ray, panPlane, panPoint)) {
                    return false
                }
                if (!panning) {
                    lastPanPoint.set(panPoint)
                    panning = true
                    return true
                }
                val delta = Vector3(lastPanPoint).sub(panPoint)
                camera.position.add(delta)
                target.add(delta)
                camera.update()
                lastPanPoint.set(panPoint)
                return true
            }
            panning = false
            return super.touchDragged(screenX, screenY, pointer)
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
        selectedFaceMesh = Mesh(false, 1, 0,
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
        selectedFaceMaterial = Material(
            ColorAttribute.createDiffuse(Color.WHITE),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.6f),
            IntAttribute(IntAttribute.CullFace, 0)
        )
        groundMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.9f, 0.9f, 0.9f, 1f)),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.5f),
            IntAttribute(IntAttribute.CullFace, GL20.GL_BACK)
        )
        faceFrontRenderable = MeshRenderableProvider(faceMesh, faceFrontMaterial, GL20.GL_TRIANGLES)
        faceBackRenderable = MeshRenderableProvider(faceMesh, faceBackMaterial, GL20.GL_TRIANGLES)
        selectedFaceRenderable = MeshRenderableProvider(selectedFaceMesh, selectedFaceMaterial, GL20.GL_TRIANGLES)
        groundRenderable = MeshRenderableProvider(groundMesh, groundMaterial, GL20.GL_TRIANGLES)
    }

    private fun updateFaceMesh() {
        val triangles = mutableListOf<TriangleWorld>()
        scene.collectWorldTriangles { a, b, c, color ->
            triangles.add(TriangleWorld(a, b, c, color))
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
            val color = tri.color
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

    private data class TriangleWorld(val a: Vector3, val b: Vector3, val c: Vector3, val color: Color)

    private fun updateSelectedFaceMesh() {
        val selected = activeFaceStore().getSelected()
        val vertexCount = selected.size * 3
        if (vertexCount == 0) {
            selectedFaceVertexCount = 0
            return
        }
        val vertices = FloatArray(vertexCount * 10)
        var idx = 0
        val highlight = Color(0.35f, 0.7f, 0.95f, 0.6f)
        val group = activeGroup()
        selected.forEach { tri ->
            val a = group.toWorld(tri.a)
            val b = group.toWorld(tri.b)
            val c = group.toWorld(tri.c)
            val normal = Vector3(b).sub(a).crs(Vector3(c).sub(a)).nor()
            idx = writeVertex(vertices, idx, a, normal, highlight)
            idx = writeVertex(vertices, idx, b, normal, highlight)
            idx = writeVertex(vertices, idx, c, normal, highlight)
        }
        if (selectedFaceMesh.maxVertices < vertexCount) {
            selectedFaceMesh.dispose()
            selectedFaceMesh = Mesh(false, vertexCount, 0,
                VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color")
            )
            selectedFaceRenderable = MeshRenderableProvider(selectedFaceMesh, selectedFaceMaterial, GL20.GL_TRIANGLES)
        }
        selectedFaceMesh.setVertices(vertices)
        selectedFaceVertexCount = vertexCount
    }

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

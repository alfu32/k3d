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
import com.github.alfu32.sketch.input.SnapResult
import com.github.alfu32.sketch.input.Snapper
import com.github.alfu32.sketch.input.ToolPointerProcessor
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.ModelPersistence
import com.github.alfu32.sketch.model.ModelCleanup
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
import com.github.alfu32.sketch.ui.SimpleTool
import com.github.alfu32.sketch.ui.SketchUiOverlay
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
    private lateinit var lineStore: DraftLineStore
    private lateinit var faceStore: DraftFaceStore
    private lateinit var modelCleanup: ModelCleanup
    private lateinit var guideManager: GuideManager
    private lateinit var snapper: Snapper
    private var lastSnap: SnapResult? = null
    private val gridSpacing = 1f
    private lateinit var modelFile: java.io.File

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
        lineStore = DraftLineStore()
        faceStore = DraftFaceStore(Color(0.8f, 0.8f, 0.8f, 1f))
        modelCleanup = ModelCleanup(lineStore, faceStore)
        guideManager = GuideManager()
        snapper = Snapper(camera, lineStore, faceStore, guideManager, gridSpacing)
        toolController = ToolController(
            statusModel,
            listOf(
                SelectTool(lineStore, faceStore, camera),
                LineTool(lineStore, faceStore),
                RectangleTool(lineStore, faceStore),
                SurfaceRectangleTool(lineStore, faceStore),
                QuadTool(lineStore, faceStore),
                CircleTool(lineStore, faceStore),
                PushPullTool(lineStore, faceStore, camera),
                MoveTool(lineStore, faceStore),
                RotateTool(lineStore, faceStore),
                SimpleTool(ToolId.SCALE, "Select and scale."),
                PaintTool(faceStore, camera) { statusModel.paintColor.cpy() },
                SimpleTool(ToolId.ERASER, "Click to erase edges.")
            )
        )
        toolInput = ToolInputProcessor(
            toolController,
            guideManager,
            ::runCleanup,
            ::clearSelection,
            ::deleteSelection
        ) { lastSnap }
        uiOverlay = SketchUiOverlay(
            toolController,
            statusModel,
            ::runCleanup,
            ::deleteSelection,
            ::flipSelectedFaces,
            ::selectionInfo
        )
        toolPointer = ToolPointerProcessor(toolController, snapper)
        Gdx.input.inputProcessor = InputMultiplexer(
            uiOverlay.stage,
            toolPointer,
            toolInput,
            cameraController
        )

        modelFile = resolveModelFile(startupArgs)
        loadModel()
        lineStore.setChangeListener { saveModel() }
        faceStore.setChangeListener { saveModel() }

        shapeRenderer = ShapeRenderer()
        setupLighting()
        setupMeshes()
        setupRenderables()
    }

    override fun render() {
        cameraController.update()
        updateCursorStatus()

        updateFaceMesh()
        updateSelectedFaceMesh()
        shadowLight.update(camera)
        renderShadowPass()

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0.62f, 0.68f, 0.72f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        Gdx.gl.glLineWidth(4f)
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawGrid(20, 1f)
        shapeRenderer.end()

        modelBatch.begin(camera)
        modelBatch.render(faceFrontRenderable, environment)
        modelBatch.render(faceBackRenderable, environment)
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
            shapeRenderer.rect(windowRect.x, windowRect.y, windowRect.width, windowRect.height)
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
        shapeRenderer.color = Color(0.35f, 0.37f, 0.39f, 1f)
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
        guideManager.getGridGuides().forEach { center ->
            drawGuidePlane(center, Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f), extent)
            drawGuidePlane(center, Vector3(1f, 0f, 0f), Vector3(0f, 0f, 1f), extent)
            drawGuidePlane(center, Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f), extent)
        }
        guideManager.getAxisGuides().forEach { center ->
            shapeRenderer.color = Color(0.85f, 0.25f, 0.25f, 1f)
            shapeRenderer.line(center.x - extent, center.y, center.z, center.x + extent, center.y, center.z)
            shapeRenderer.color = Color(0.35f, 0.45f, 0.95f, 1f)
            shapeRenderer.line(center.x, center.y - extent, center.z, center.x, center.y + extent, center.z)
            shapeRenderer.color = Color(0.25f, 0.85f, 0.35f, 1f)
            shapeRenderer.line(center.x, center.y, center.z - extent, center.x, center.y, center.z + extent)
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
        val segments = lineStore.getSegments()
        if (segments.isEmpty()) {
            return
        }
        shapeRenderer.color = Color(0.2f, 0.2f, 0.2f, 1f)
        segments.forEach { segment ->
            shapeRenderer.line(
                segment.start.x, segment.start.y, segment.start.z,
                segment.end.x, segment.end.y, segment.end.z
            )
        }
    }

    private fun drawSelectionHighlights() {
        val selectedEdges = lineStore.getSelected()
        if (selectedEdges.isNotEmpty()) {
            shapeRenderer.color = Color(0.25f, 0.55f, 0.95f, 1f)
            Gdx.gl.glLineWidth(6f)
            selectedEdges.forEach { segment ->
                shapeRenderer.line(
                    segment.start.x, segment.start.y, segment.start.z,
                    segment.end.x, segment.end.y, segment.end.z
                )
            }
            Gdx.gl.glLineWidth(4f)
        }
    }

    private fun runCleanup() {
        val startEdges = lineStore.getSegments().size
        val startFaces = faceStore.getTriangles().size
        statusModel.message = "Cleanup start | edges $startEdges faces $startFaces"
        lineStore.withChangeSuppressed {
            faceStore.withChangeSuppressed {
                modelCleanup.run()
            }
        }
        val endEdges = lineStore.getSegments().size
        val endFaces = faceStore.getTriangles().size
        statusModel.message = "Cleanup done | edges $endEdges faces $endFaces"
        saveModel()
    }

    private fun clearSelection() {
        lineStore.clearSelection()
        faceStore.clearSelection()
        statusModel.message = "Selection cleared."
    }

    private fun deleteSelection() {
        val edges = lineStore.deleteSelected()
        val faces = faceStore.deleteSelected()
        statusModel.message = "Deleted | edges $edges faces $faces"
    }

    private fun flipSelectedFaces() {
        val flipped = faceStore.flipSelected()
        statusModel.message = "Flipped faces: $flipped"
    }

    private fun saveModel() {
        ModelPersistence.save(modelFile, lineStore, faceStore)
    }

    private fun loadModel() {
        if (modelFile.exists()) {
            val backup = java.io.File(modelFile.absolutePath + ".bak")
            modelFile.copyTo(backup, overwrite = true)
            lineStore.withChangeSuppressed {
                faceStore.withChangeSuppressed {
                    ModelPersistence.load(modelFile, lineStore, faceStore)
                }
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
            fileArg = "sketch3d.json"
        }
        return java.io.File(fileArg).absoluteFile
    }

    private fun selectionInfo(): SketchUiOverlay.SelectionInfo {
        return SketchUiOverlay.SelectionInfo(
            edgeCount = lineStore.getSelected().size,
            faceCount = faceStore.getSelected().size
        )
    }

    private class ShiftCameraController(camera: PerspectiveCamera) : CameraInputController(camera) {
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
    }

    private fun setupLighting() {
        environment = Environment()
        shadowLight = DirectionalShadowLight(
            4096,
            4096,
            60f,
            60f,
            1f,
            300f
        ).apply {
            set(0.5f, 0.5f, 0.5f, -0.5f, -1.8f, -1.2f)
            setColor(Color(0f, 0f, 0f, 0.85f))
            environment.add(this)
            environment.shadowMap = this
        }
        environment.add(
            DirectionalLight()
                .set(0.6f, 0.6f, 0.6f, -0.5f, -1.8f, -1.2f)
                .setColor(Color(0.6f, 0.6f, 0.6f, 0.9f))
        )
        environment.add(
            DirectionalLight()
                .set(0.08f, 0.08f, 0.08f, 1.2f, 1.8f, 0.5f)
                .setColor(Color(0.08f, 0.08f, 0.08f, 0.15f))
        )
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.82f, 0.82f, 0.82f, 0.95f))
        environment.set(ColorAttribute(ColorAttribute.Specular, 0.5f, 0.5f, 0.9f, 0.7f))
        modelBatch = ModelBatch()
        shadowBatch = ModelBatch(DepthShaderProvider())
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
            IntAttribute(IntAttribute.CullFace, GL20.GL_BACK)
        )
        faceBackMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.8f, 0.83f, 0.93f, 1f)),
            IntAttribute(IntAttribute.CullFace, GL20.GL_FRONT)
        )
        selectedFaceMaterial = Material(
            ColorAttribute.createDiffuse(Color.WHITE),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.6f),
            IntAttribute(IntAttribute.CullFace, 0)
        )
        groundMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.72f, 0.70f, 0.60f, 0.5f)),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.5f),
            IntAttribute(IntAttribute.CullFace, GL20.GL_BACK)
        )
        faceFrontRenderable = MeshRenderableProvider(faceMesh, faceFrontMaterial, GL20.GL_TRIANGLES)
        faceBackRenderable = MeshRenderableProvider(faceMesh, faceBackMaterial, GL20.GL_TRIANGLES)
        selectedFaceRenderable = MeshRenderableProvider(selectedFaceMesh, selectedFaceMaterial, GL20.GL_TRIANGLES)
        groundRenderable = MeshRenderableProvider(groundMesh, groundMaterial, GL20.GL_TRIANGLES)
    }

    private fun updateFaceMesh() {
        val triangles = faceStore.getTriangles()
        val vertexCount = triangles.size * 3
        if (vertexCount == 0) {
            return
        }
        val vertices = FloatArray(vertexCount * 10)
        var idx = 0
        triangles.forEach { tri ->
            val normal = Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a)).nor()
            val color = faceStore.colorFor(tri)
            idx = writeVertex(vertices, idx, tri.a, normal, color)
            idx = writeVertex(vertices, idx, tri.b, normal, color)
            idx = writeVertex(vertices, idx, tri.c, normal, color)
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

    private fun updateSelectedFaceMesh() {
        val selected = faceStore.getSelected()
        val vertexCount = selected.size * 3
        if (vertexCount == 0) {
            selectedFaceVertexCount = 0
            return
        }
        val vertices = FloatArray(vertexCount * 10)
        var idx = 0
        val highlight = Color(0.35f, 0.7f, 0.95f, 0.6f)
        selected.forEach { tri ->
            val normal = Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a)).nor()
            idx = writeVertex(vertices, idx, tri.a, normal, highlight)
            idx = writeVertex(vertices, idx, tri.b, normal, highlight)
            idx = writeVertex(vertices, idx, tri.c, normal, highlight)
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

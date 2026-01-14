package com.github.alfu32.sketch

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.input.GuideManager
import com.github.alfu32.sketch.input.SnapResult
import com.github.alfu32.sketch.input.Snapper
import com.github.alfu32.sketch.input.ToolPointerProcessor
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.tools.CircleTool
import com.github.alfu32.sketch.tools.LineTool
import com.github.alfu32.sketch.tools.RectangleTool
import com.github.alfu32.sketch.ui.SimpleTool
import com.github.alfu32.sketch.ui.SketchUiOverlay
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.ToolController
import com.github.alfu32.sketch.ui.ToolId
import com.github.alfu32.sketch.ui.ToolInputProcessor
import com.kotcrab.vis.ui.VisUI

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms. */
class Main : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var cameraController: CameraInputController
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var faceMesh: Mesh
    private lateinit var groundMesh: Mesh
    private lateinit var depthShader: ShaderProgram
    private lateinit var mainShader: ShaderProgram
    private lateinit var shadowBuffer: FrameBuffer
    private lateinit var shadowTexture: Texture
    private lateinit var lightCamera: OrthographicCamera
    private val lightDir = Vector3(-1f, -1f, -0.6f).nor()
    private val fillDir = Vector3(1.2f, 1.8f, 0.5f).nor()
    private val ambientStrength = 0.55f
    private val fillStrength = 0.25f
    private val shadowDarkness = 0.35f
    private val groundShadowOpacity = 0.55f
    private val faceShadowOpacity = 0.85f
    private val faceShadowNormalOffset = 0.002f
    private val groundShadowNormalOffset = 0.0f
    private val shadowBias = 0.0015f
    private val shadowMapSize = 4096
    private val shadowSlopeBias = 0.01f
    private lateinit var toolController: ToolController
    private lateinit var toolInput: ToolInputProcessor
    private lateinit var uiOverlay: SketchUiOverlay
    private lateinit var toolPointer: ToolPointerProcessor
    private lateinit var statusModel: StatusModel
    private lateinit var lineStore: DraftLineStore
    private lateinit var faceStore: DraftFaceStore
    private lateinit var guideManager: GuideManager
    private lateinit var snapper: Snapper
    private var lastSnap: SnapResult? = null
    private val gridSpacing = 1f

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

        cameraController = CameraInputController(camera).apply {
            rotateButton = Input.Buttons.LEFT
            translateButton = Input.Buttons.RIGHT
        }
        statusModel = StatusModel(
            activeTool = ToolId.SELECT,
            message = "Select entities.",
            inputBuffer = ""
        )
        lineStore = DraftLineStore()
        faceStore = DraftFaceStore()
        guideManager = GuideManager()
        snapper = Snapper(camera, lineStore, guideManager, gridSpacing)
        toolController = ToolController(
            statusModel,
            listOf(
                SimpleTool(ToolId.SELECT, "Select entities."),
                LineTool(lineStore),
                RectangleTool(lineStore, faceStore),
                CircleTool(lineStore, faceStore),
                SimpleTool(ToolId.PUSH_PULL, "Click face then drag."),
                SimpleTool(ToolId.MOVE, "Select and move."),
                SimpleTool(ToolId.ROTATE, "Select and rotate."),
                SimpleTool(ToolId.SCALE, "Select and scale."),
                SimpleTool(ToolId.PAINT, "Click to paint faces."),
                SimpleTool(ToolId.ERASER, "Click to erase edges.")
            )
        )
        toolInput = ToolInputProcessor(toolController, guideManager) { lastSnap }
        uiOverlay = SketchUiOverlay(toolController, statusModel)
        toolPointer = ToolPointerProcessor(toolController, snapper)
        Gdx.input.inputProcessor = InputMultiplexer(
            uiOverlay.stage,
            toolPointer,
            toolInput,
            cameraController
        )

        shapeRenderer = ShapeRenderer()
        setupShaders()
        setupShadowMap()
        setupLightCamera()
        setupMeshes()
    }

    override fun render() {
        cameraController.update()
        updateCursorStatus()

        updateFaceMesh()
        updateLightCameraBounds()
        renderShadowPass()

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0.62f, 0.68f, 0.72f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        renderMainPass()

        Gdx.gl.glLineWidth(4f)
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawGrid(20, 1f)
        drawAxes(2.5f)
        drawGuides()
        drawCursor()
        drawDraftLines()
        toolController.render(shapeRenderer)
        shapeRenderer.end()

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
        groundMesh.dispose()
        depthShader.dispose()
        mainShader.dispose()
        shadowBuffer.dispose()
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
        if (guideManager.gridGuideActive) {
            drawGuidePlane(guideManager.gridGuideCenter, Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f), extent)
            drawGuidePlane(guideManager.gridGuideCenter, Vector3(1f, 0f, 0f), Vector3(0f, 0f, 1f), extent)
            drawGuidePlane(guideManager.gridGuideCenter, Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f), extent)
        }
        if (guideManager.axisGuideActive) {
            val center = guideManager.axisGuideCenter
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

    private fun setupShaders() {
        ShaderProgram.pedantic = false
        depthShader = ShaderProgram(
            """
            attribute vec3 a_position;
            uniform mat4 u_lightVP;
            void main() {
                gl_Position = u_lightVP * vec4(a_position, 1.0);
            }
            """.trimIndent(),
            """
            #ifdef GL_ES
            precision mediump float;
            #endif
            void main() {
                float depth = gl_FragCoord.z;
                gl_FragColor = vec4(depth, depth, depth, 1.0);
            }
            """.trimIndent()
        )
        if (!depthShader.isCompiled) {
            error("Depth shader failed: ${depthShader.log}")
        }

        mainShader = ShaderProgram(
            """
            #ifdef GL_ES
            precision mediump float;
            #endif
            attribute vec3 a_position;
            attribute vec3 a_normal;
            uniform mat4 u_projView;
            uniform mat4 u_lightVP;
            uniform mediump vec3 u_lightDir;
            uniform float u_shadowNormalOffset;
            varying vec3 v_normal;
            varying vec4 v_shadowCoord;
            void main() {
                v_normal = a_normal;
                vec3 shadowPos = a_position - u_lightDir * u_shadowNormalOffset;
                v_shadowCoord = u_lightVP * vec4(shadowPos, 1.0);
                gl_Position = u_projView * vec4(a_position, 1.0);
            }
            """.trimIndent(),
            """
            #ifdef GL_ES
            precision mediump float;
            #endif
            uniform mediump vec3 u_lightDir;
            uniform mediump vec3 u_fillDir;
            uniform float u_ambient;
            uniform float u_fillStrength;
            uniform vec4 u_color;
            uniform sampler2D u_shadowMap;
            uniform float u_shadowBias;
            uniform float u_shadowSlopeBias;
            uniform float u_shadowDarkness;
            uniform float u_receiveShadows;
            uniform float u_shadowOpacity;
            uniform vec2 u_shadowTexelSize;
            varying vec3 v_normal;
            varying vec4 v_shadowCoord;

            float shadowFactor(float ndl) {
                if (u_receiveShadows < 0.5) {
                    return 1.0;
                }
                vec3 proj = v_shadowCoord.xyz / v_shadowCoord.w;
                vec2 uv = proj.xy * 0.5 + 0.5;
                float depth = proj.z * 0.5 + 0.5;
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    return 1.0;
                }
                float bias = u_shadowBias + u_shadowSlopeBias * (1.0 - ndl);
                vec2 o = u_shadowTexelSize * 0.5;
                float hit0 = (depth - bias) > texture2D(u_shadowMap, uv + vec2(-o.x, -o.y)).r ? 1.0 : 0.0;
                float hit1 = (depth - bias) > texture2D(u_shadowMap, uv + vec2(o.x, -o.y)).r ? 1.0 : 0.0;
                float hit2 = (depth - bias) > texture2D(u_shadowMap, uv + vec2(-o.x, o.y)).r ? 1.0 : 0.0;
                float hit3 = (depth - bias) > texture2D(u_shadowMap, uv + vec2(o.x, o.y)).r ? 1.0 : 0.0;
                float shadowHit = (hit0 + hit1 + hit2 + hit3) * 0.25;
                float shadowFactor = mix(1.0, u_shadowDarkness, shadowHit);
                return mix(1.0, shadowFactor, u_shadowOpacity);
            }

            void main() {
                vec3 n = normalize(v_normal);
                if (!gl_FrontFacing) {
                    n = -n;
                }
                vec3 l0 = normalize(-u_lightDir);
                vec3 l1 = normalize(-u_fillDir);
                float diff0 = max(dot(n, l0), 0.0);
                float diff1 = max(dot(n, l1), 0.0);
                float shadow = shadowFactor(diff0);
                float lighting = u_ambient + diff0 * shadow + diff1 * u_fillStrength;
                vec3 color = u_color.rgb * lighting;
                gl_FragColor = vec4(color, u_color.a);
            }
            """.trimIndent()
        )
        if (!mainShader.isCompiled) {
            error("Main shader failed: ${mainShader.log}")
        }
    }

    private fun setupShadowMap() {
        shadowBuffer = FrameBuffer(Pixmap.Format.RGBA8888, shadowMapSize, shadowMapSize, true)
        shadowTexture = shadowBuffer.colorBufferTexture
        shadowTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
    }

    private fun setupLightCamera() {
        val size = 60f
        lightCamera = OrthographicCamera(size, size)
        lightCamera.position.set(Vector3(lightDir).scl(-40f))
        lightCamera.lookAt(0f, 0f, 0f)
        lightCamera.near = 1f
        lightCamera.far = 200f
        lightCamera.update()
    }

    private fun setupMeshes() {
        faceMesh = Mesh(false, 1, 0,
            VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal")
        )
        groundMesh = buildGroundMesh(120f)
    }

    private fun updateFaceMesh() {
        val triangles = faceStore.getTriangles()
        val vertexCount = triangles.size * 3
        if (vertexCount == 0) {
            return
        }
        val vertices = FloatArray(vertexCount * 6)
        var idx = 0
        triangles.forEach { tri ->
            val normal = Vector3(tri.b).sub(tri.a).crs(Vector3(tri.c).sub(tri.a)).nor()
            idx = writeVertex(vertices, idx, tri.a, normal)
            idx = writeVertex(vertices, idx, tri.b, normal)
            idx = writeVertex(vertices, idx, tri.c, normal)
        }
        if (faceMesh.maxVertices < vertexCount) {
            faceMesh.dispose()
            faceMesh = Mesh(false, vertexCount, 0,
                VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal")
            )
        }
        faceMesh.setVertices(vertices)
    }

    private fun updateLightCameraBounds() {
        val triangles = faceStore.getTriangles()
        if (triangles.isEmpty()) {
            return
        }
        val min = Vector3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val max = Vector3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        triangles.forEach { tri ->
            listOf(tri.a, tri.b, tri.c).forEach { v ->
                min.x = kotlin.math.min(min.x, v.x)
                min.y = kotlin.math.min(min.y, v.y)
                min.z = kotlin.math.min(min.z, v.z)
                max.x = kotlin.math.max(max.x, v.x)
                max.y = kotlin.math.max(max.y, v.y)
                max.z = kotlin.math.max(max.z, v.z)
            }
        }
        val center = Vector3(min).add(max).scl(0.5f)
        val extents = Vector3(max).sub(min)
        val size = kotlin.math.max(extents.x, kotlin.math.max(extents.y, extents.z)) + 10f
        lightCamera.viewportWidth = size
        lightCamera.viewportHeight = size
        lightCamera.position.set(Vector3(center).sub(Vector3(lightDir).scl(size)))
        lightCamera.lookAt(center)
        lightCamera.near = 0.1f
        lightCamera.far = size * 4f
        lightCamera.update()
    }

    private fun writeVertex(buffer: FloatArray, start: Int, pos: Vector3, normal: Vector3): Int {
        var i = start
        buffer[i++] = pos.x
        buffer[i++] = pos.y
        buffer[i++] = pos.z
        buffer[i++] = normal.x
        buffer[i++] = normal.y
        buffer[i++] = normal.z
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
        shadowBuffer.begin()
        Gdx.gl.glViewport(0, 0, shadowMapSize, shadowMapSize)
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
        Gdx.gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL)
        Gdx.gl.glPolygonOffset(1f, 1.5f)
        depthShader.bind()
        depthShader.setUniformMatrix("u_lightVP", lightCamera.combined)
        if (faceMesh.numVertices > 0) {
            faceMesh.render(depthShader, GL20.GL_TRIANGLES)
        }
        Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        shadowBuffer.end()
    }

    private fun renderMainPass() {
        mainShader.bind()
        mainShader.setUniformMatrix("u_projView", camera.combined)
        mainShader.setUniformMatrix("u_lightVP", lightCamera.combined)
        mainShader.setUniformf("u_lightDir", lightDir)
        mainShader.setUniformf("u_fillDir", fillDir)
        mainShader.setUniformf("u_ambient", ambientStrength)
        mainShader.setUniformf("u_fillStrength", fillStrength)
        mainShader.setUniformf("u_shadowBias", shadowBias)
        mainShader.setUniformf("u_shadowSlopeBias", shadowSlopeBias)
        mainShader.setUniformf("u_shadowDarkness", shadowDarkness)
        mainShader.setUniformi("u_shadowMap", 0)
        mainShader.setUniformf("u_shadowTexelSize", 1f / shadowMapSize.toFloat(), 1f / shadowMapSize.toFloat())
        shadowTexture.bind(0)

        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        mainShader.setUniformf("u_color", 0.8f, 0.8f, 0.8f, 1f)
        mainShader.setUniformf("u_receiveShadows", 1f)
        mainShader.setUniformf("u_shadowOpacity", faceShadowOpacity)
        mainShader.setUniformf("u_shadowNormalOffset", faceShadowNormalOffset)
        if (faceMesh.numVertices > 0) {
            faceMesh.render(mainShader, GL20.GL_TRIANGLES)
        }

        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
        mainShader.setUniformf("u_color", 0.72f, 0.70f, 0.60f, 0.5f)
        mainShader.setUniformf("u_receiveShadows", 1f)
        mainShader.setUniformf("u_shadowOpacity", groundShadowOpacity)
        mainShader.setUniformf("u_shadowNormalOffset", groundShadowNormalOffset)
        groundMesh.render(mainShader, GL20.GL_TRIANGLES)
    }
}

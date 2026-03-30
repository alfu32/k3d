package com.github.alfu32.sketch

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Application
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
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.Pool
import com.github.alfu32.sketch.input.GuideManager
import com.github.alfu32.sketch.input.CameraEventRouter
import com.github.alfu32.sketch.input.CameraScrollForwarder
import com.github.alfu32.sketch.input.SnapResult
import com.github.alfu32.sketch.input.Snapper
import com.github.alfu32.sketch.input.ToolPointerProcessor
import com.github.alfu32.sketch.model.ArchitectureStore
import com.github.alfu32.sketch.model.DraftTextStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.model.HvacStore
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
import com.github.alfu32.sketch.console.TerminalControllerFactory
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
import com.github.alfu32.sketch.InputModifiers
import com.github.alfu32.sketch.K3DVersion
import com.badlogic.gdx.Graphics
import com.github.alfu32.sketch.plugin.PluginHost
import com.github.alfu32.sketch.export.IfcExporter
import com.github.alfu32.sketch.export.MeshIo
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
import com.github.alfu32.sketch.tools.CopyMultipleTool
import com.github.alfu32.sketch.tools.PlanarTranslateMultipleTool
import com.github.alfu32.sketch.tools.VolumetricTranslateMultipleTool
import com.github.alfu32.sketch.tools.PlanarRotateMultipleTool
import com.github.alfu32.sketch.tools.HelicoidalRotateMultipleTool
import com.github.alfu32.sketch.tools.HotspotSettings
import com.github.alfu32.sketch.tools.HvacPlumbingTool
import com.github.alfu32.sketch.tools.HvacSettings
import com.github.alfu32.sketch.tools.HvacVentilationTool
import com.github.alfu32.sketch.tools.LinearDimensionTool
import com.github.alfu32.sketch.tools.LineOffsetTool
import com.github.alfu32.sketch.tools.LineTool
import com.github.alfu32.sketch.tools.MeshTool
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
import com.github.alfu32.sketch.tools.RotateStretchTool
import com.github.alfu32.sketch.tools.SurfaceRectangleTool
import com.github.alfu32.sketch.tools.SelectTool
import com.github.alfu32.sketch.tools.ScaleTool
import com.github.alfu32.sketch.tools.StretchTool
import com.github.alfu32.sketch.tools.TextTool
import com.github.alfu32.sketch.tools.EmbeddedVectorGlyphCatalog
import com.github.alfu32.sketch.tools.VectorTextTool
import com.github.alfu32.sketch.tools.VectorTextSettings
import com.github.alfu32.sketch.tools.VectorGlyphCatalog
import com.github.alfu32.sketch.tools.VoxelFrameTool
import com.github.alfu32.sketch.tools.VoxelTool
import com.github.alfu32.sketch.tools.VoxelVolumeTool
import com.github.alfu32.sketch.tutorial.TutorialManager
import com.github.alfu32.sketch.tutorial.TutorialMode
import com.github.alfu32.sketch.tutorial.TutorialUiState
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
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilenameFilter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Base64
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.CountDownLatch
import javax.swing.JOptionPane
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms. */
class Main(
    private val startupArgs: kotlin.Array<String> = emptyArray(),
    private val runtimeProfile: RuntimeProfile = RuntimeProfile.AUTO
) : ApplicationAdapter() {
    enum class RuntimeProfile {
        AUTO,
        WEB_SAFE
    }

    private data class FeedbackWorldLine(val start: Vector3, val end: Vector3)
    private data class PendingTutorialPreviewCapture(val stepIndex: Int)
    private sealed interface PendingPngExport {
        data class FileTarget(val target: File) : PendingPngExport
        data class WebTarget(val targetName: String, val bridge: WebRuntimeBridge) : PendingPngExport
        data class AndroidTarget(val targetName: String, val uri: String, val bridge: AndroidSafBridge) : PendingPngExport
    }

    private data class EntityDisplayState(
        var draw: Boolean = true,
        var unlocked: Boolean = true,
        var wireframe: Boolean = false
    )

    private data class WebEmbedConfig(
        val hostId: String?,
        val canvasId: String?,
        val initialModel: String?,
        val initialFileName: String?,
        val toolbarsVisible: Boolean,
        val panelsVisible: Boolean
    )

    private data class SelectionTotalsCache(
        val edges: Int = 0,
        val faces: Int = 0,
        val voxels: Int = 0,
        val hotspots: Int = 0,
        val groups: Int = 0,
        val dimensions: Int = 0,
        val texts: Int = 0,
        val walls: Int = 0,
        val slabs: Int = 0,
        val stairs: Int = 0,
        val frames: Int = 0
    )

    private enum class BasicSelectionFilterKind {
        EDGE,
        FACE,
        VOXEL,
        HOTSPOT,
        OBJECT,
        DIMENSION,
        TEXT
    }

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
    private lateinit var shadowLight: ResizableDirectionalShadowLight
    private lateinit var mainLight: DirectionalLight
    private lateinit var faceFrontMaterial: Material
    private lateinit var faceBackMaterial: Material
    private lateinit var groundMaterial: Material
    private lateinit var faceFrontRenderable: MeshRenderableProvider
    private lateinit var faceBackRenderable: MeshRenderableProvider
    private lateinit var groundRenderable: MeshRenderableProvider
    private val groupFaceBundles = linkedMapOf<String, FaceMeshBundle>()
    private val selectedFaceColor = Color(1f, 0f, 0f, 0.3f)
    private val selectedLineColor = Color(1f, 0f, 0f, 1f)
    private val selectedLineOverlayPointColor = Color(0.12f, 0.32f, 0.95f, 0.95f)
    private val feedbackOverlayLineColor = Color(0.06f, 0.12f, 0.24f, 0.95f)
    private val architectureHoleGuideColor = Color(0.2f, 0.55f, 0.95f, 1f)
    private val architectureHoleHotspotColor = Color(0.2f, 0.55f, 0.95f, 1f)
    private val architectureSlabHotspotColor = Color(0.2f, 0.9f, 0.85f, 1f)
    private val architectureFrameHotspotColor = Color(1f, 0.7f, 0.25f, 1f)
    private val hvacPlumbingHotspotColor = Color(0.3f, 0.75f, 0.95f, 1f)
    private val hvacVentilationHotspotColor = Color(0.45f, 0.95f, 0.55f, 1f)
    private val hotspotColor = Color(0.2f, 0.55f, 0.95f, 1f)
    private val hotspotReferenceColor = Color(0.25f, 0.9f, 0.35f, 1f)
    private val hotspotSelectedColor = Color(1f, 0.2f, 0.2f, 1f)
    private val entityHotspotColor = Color(0.05f, 0.05f, 0.05f, 1f)
    private val entityHotspotAccentColor = Color(0.85f, 0f, 0.85f, 1f)
    private val prototypeGuideFaceColor = Color(0.2f, 0.65f, 1f, 0.2f)
    private val prototypeGuideLineColor = Color(0.2f, 0.65f, 1f, 1f)
    private val selectedEntityBoxColor = Color(0.2f, 0.7f, 0.95f, 1f)
    private val editModeBoxColor = Color(1f, 0.6f, 0.2f, 1f)
    private val selectedLineWidth = 8f
    private val normalLineWidthPrefKey = "ui.normal_line_width"
    private val feedbackLineWidthPrefKey = "ui.feedback_line_width"
    private lateinit var toolController: ToolController
    private lateinit var toolInput: ToolInputProcessor
    private lateinit var uiOverlay: SketchUiOverlay
    private lateinit var toolPointer: ToolPointerProcessor
    private val polylineSettings = PolylineSettings()
    private val vectorTextSettings = VectorTextSettings()
    private var vectorGlyphCatalog = VectorGlyphCatalog.empty("uninitialized")
    private val vectorGlyphCatalogCache = mutableMapOf<String, VectorGlyphCatalog>()
    private val architectureSettings = ArchitectureSettings()
    private val hvacSettings = HvacSettings()
    private val hotspotSettings = HotspotSettings()
    private lateinit var statusModel: StatusModel
    private lateinit var scene: GroupScene
    private lateinit var modelCleanup: ModelCleanup
    private lateinit var guideManager: GuideManager
    private lateinit var snapper: Snapper
    private var lastSnap: SnapResult? = null
    private var distanceOverrideSnap: SnapResult? = null
    private var distanceInputActive = false
    private var gridSpacing = 1f
    private var circleSegments = 24
    private var snapEpsilon = 12f
    private val baseGridHalfSize = 20
    private val adaptiveGridCloudRadiusUnits = 7
    private var modelUnit = ModelUnit(1f, "unit")
    private lateinit var modelFile: java.io.File
    private var shadowLightValue = 0.59f
    private var shadowLightAlpha = 0.5f
    private var directionalLightValue = 0.73f
    private var directionalLightAlpha = 1f
    private var ambientLightValue = 0.59f
    private val shadowModelBoundsCenter = Vector3()
    private val minimumShadowBoundsRadius = 17.320509f
    private var shadowModelBoundsRadius = minimumShadowBoundsRadius
    private var shadowModelBoundsValid = false
    private val minimumGroundPlaneSize = 120f
    private val groundPlaneExtentMultiplier = 1.6f
    private val groundPlaneCenter = Vector3()
    private var groundPlaneSize = minimumGroundPlaneSize
    private var normalOverlayLineWidth = 1f
    private var feedbackOverlayLineWidth = 3f
    private var visibleRenderableGroupIds = emptySet<String>()
    private var shadowModelTrackedEdgeCount = -1
    private var shadowModelTrackedFaceCount = -1
    private val shadowBoundsCenterTmp = Vector3()
    private val shadowBoundsDimensionsTmp = Vector3()
    private var instanceGeometryDirty = true
    private val capturedFeedbackLines = mutableListOf<FeedbackWorldLine>()
    private val pendingPngExports = ArrayDeque<PendingPngExport>()
    private val pendingTutorialPreviewCaptures = ArrayDeque<PendingTutorialPreviewCapture>()
    private var pendingTutorialPreviewDelayFrames = 0
    private val tutorialThumbnailWidth = 380
    private val tutorialThumbnailHeight = 200
    private var faceMeshDirty = true
    private var faceMeshVisualStamp = Long.MIN_VALUE
    private var shadowPassDirty = true
    private var selectionTotalsDirty = true
    private var selectionTotalsCache = SelectionTotalsCache()
    private var autosavePending = false
    private var autosaveDueAtMs = 0L
    private val autosaveDebounceMs = 1500L
    private var nextAsyncMaintenanceAtMs = 0L
    private val asyncMaintenanceIntervalMs = 150L
    private var webEmbedChangePending = false
    private var webEmbedChangeDueAtMs = 0L
    private val webEmbedChangeDebounceMs = 250L
    @Volatile private var asyncModelSaveThread: Thread? = null
    @Volatile private var asyncModelSaveRunning = false
    @Volatile private var asyncModelSaveError: String? = null
    private var cursorStatusSampleInitialized = false
    private var cursorStatusLastSampleAtMs = 0L
    private var cursorStatusLastScreenX = Int.MIN_VALUE
    private var cursorStatusLastScreenY = Int.MIN_VALUE
    private val cursorStatusLastCamPos = Vector3()
    private val cursorStatusLastCamDir = Vector3()
    private val cursorStatusLastCamUp = Vector3()
    private var ambientLightAlpha = 1f
    private var specularLightValue = 0.2f
    private var specularLightAlpha = 0.95f
    private lateinit var lightingSettings: LightingSettings
    private var shadowBias = 2500f
    private var shadowNormalBias = 5620f
    private var shadowPcfMode = 1
    private var shadowDither = false
    private var shadowUseCsm = true
    private lateinit var shadowSettings: ShadowSettings
    private lateinit var pluginHost: PluginHost
    private lateinit var tutorialManager: TutorialManager
    private val architectureDisplayState = EnumMap<ArchitectureStore.ElementKind, EntityDisplayState>(ArchitectureStore.ElementKind::class.java).apply {
        put(ArchitectureStore.ElementKind.WALL, EntityDisplayState())
        put(ArchitectureStore.ElementKind.SLAB, EntityDisplayState())
        put(ArchitectureStore.ElementKind.STAIR, EntityDisplayState())
        put(ArchitectureStore.ElementKind.FRAME, EntityDisplayState())
    }
    private val basicDisplayState = EnumMap<BasicSelectionFilterKind, EntityDisplayState>(BasicSelectionFilterKind::class.java).apply {
        put(BasicSelectionFilterKind.EDGE, EntityDisplayState(wireframe = false))
        put(BasicSelectionFilterKind.FACE, EntityDisplayState(wireframe = false))
        put(BasicSelectionFilterKind.VOXEL, EntityDisplayState(wireframe = false))
        put(BasicSelectionFilterKind.HOTSPOT, EntityDisplayState(wireframe = false))
        put(BasicSelectionFilterKind.OBJECT, EntityDisplayState(wireframe = false))
        put(BasicSelectionFilterKind.DIMENSION, EntityDisplayState(wireframe = false))
        put(BasicSelectionFilterKind.TEXT, EntityDisplayState(wireframe = false))
    }
    private lateinit var installDir: java.io.File
    private lateinit var objectPlaceTool: ObjectPlaceTool
    private val runtimePrefs by lazy { Gdx.app.getPreferences("k3d-runtime") }
    private val walkMoveSpeedPrefKey = "walk.moveSpeed"
    private val walkJumpVelocityPrefKey = "walk.jumpVelocity"
    private val walkGravityPrefKey = "walk.gravity"
    private val walkHeightAdjustSpeedPrefKey = "walk.heightAdjustSpeed"
    private val cameraTarget = Vector3(0f, 0f, 0f)
    private data class AdaptiveGridCloudState(
        val center: Vector3,
        val normal: Vector3,
        val axisU: Vector3,
        val axisV: Vector3,
        val halfExtent: Float
    )
    private var adaptiveGridCloudState: AdaptiveGridCloudState? = null
    private val screenshotTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val webAutosaveStorageKey = "octodraw.web.autosave"
    private val webModelNameStorageKey = "octodraw.web.modelName"
    private var webEmbedConfig: WebEmbedConfig? = null
    private var webEmbedRevision = 0
    private var mcpPort = 8765
    private var stdoutTap: StdoutTap? = null
    private lateinit var mcpServer: McpHttpServer
    private var orthoDistance = 250f
    private enum class OrthoView { TOP, BOTTOM, LEFT, RIGHT, FRONT, BACK }
    private var consoleThread: ConsoleThread? = null
    private var consoleRuntime: ConsoleGroovyRuntime? = null
    private var hotspotReferencePickTargetId: String? = null
    private var hotspotReferencePickTargetGroup: GroupScene.GroupNode? = null
    private var hotspotAddPickTargetGroup: GroupScene.GroupNode? = null
    private var mcpConsoleRuntime: ConsoleGroovyRuntime? = null
    private var mcpConsoleTui: ConsoleTui? = null
    private var consoleTerminal: TerminalController? = null
    private lateinit var undoManager: com.github.alfu32.sketch.model.UndoRedoManager
    private var restoringSnapshot = false
    private var pendingModelLoad: PendingModelLoad? = null
    private var deferredStartupLoad = false
    private var deferredStartupLoadFrames = 0
    private var modelLoadDialog: com.kotcrab.vis.ui.widget.VisWindow? = null
    private var modelLoadTitleLabel: com.kotcrab.vis.ui.widget.VisLabel? = null
    private var modelLoadDetailLabel: com.kotcrab.vis.ui.widget.VisLabel? = null
    private var modelLoadProgressBar: com.kotcrab.vis.ui.widget.VisProgressBar? = null
    private var modelLoadStepLabel: com.kotcrab.vis.ui.widget.VisLabel? = null

    private class PendingModelLoad(
        val file: java.io.File,
        val displayName: String,
        val createIfMissing: Boolean
    ) {
        @Volatile var backupProgress: Float = 0f
        @Volatile var backupStageDone = false
        @Volatile var fileProgress: Float = 0f
        @Volatile var fileStageDone = false
        @Volatile var decodeProgress: Float = 0f
        @Volatile var decodeStageDone = false
        @Volatile var snapshot: ModelPersistence.ModelSnapshot? = null
        @Volatile var needsResave = false
        @Volatile var errorMessage: String? = null
        @Volatile var workerDone = false
        @Volatile var canceled = false
        var sceneProgress: Float = 0f
        var indexProgress: Float = 0f
        var sceneApplied = false
        var sceneSession: ModelPersistence.ApplySnapshotSession? = null
        var indexingInitialized = false
        var indexingTotal = 0
        var indexingDone = 0
        val indexingTasks = ArrayDeque<() -> Unit>()
    }

    private data class ModelLoadStepUiState(
        val index: Int,
        val total: Int,
        val description: String,
        val progress: Float
    )

    private fun requireWebInternalFile(path: String): FileHandle {
        val file = Gdx.files.internal(path)
        if (!file.exists()) {
            throw IllegalStateException("Required web asset not found (internal): $path")
        }
        return file
    }

    private fun appendEmbedJsonValue(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> out.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")).append('"')
            is Number, is Boolean -> out.append(value.toString())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                value.forEach { (key, entryValue) ->
                    if (key == null) return@forEach
                    if (!first) out.append(',')
                    first = false
                    appendEmbedJsonValue(out, key.toString())
                    out.append(':')
                    appendEmbedJsonValue(out, entryValue)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                value.forEach { entry ->
                    if (!first) out.append(',')
                    first = false
                    appendEmbedJsonValue(out, entry)
                }
                out.append(']')
            }
            is Array<*> -> {
                out.append('[')
                value.forEachIndexed { index, entry ->
                    if (index > 0) out.append(',')
                    appendEmbedJsonValue(out, entry)
                }
                out.append(']')
            }
            else -> appendEmbedJsonValue(out, value.toString())
        }
    }

    private fun encodeEmbedJson(value: Any?): String {
        val out = StringBuilder()
        appendEmbedJsonValue(out, value)
        return out.toString()
    }

    private fun parseWebEmbedConfig(jsonText: String?): WebEmbedConfig? {
        if (jsonText.isNullOrBlank()) {
            return null
        }
        return try {
            val root = JsonReader().parse(jsonText)
            WebEmbedConfig(
                hostId = root.childString("hostId"),
                canvasId = root.childString("canvasId"),
                initialModel = root.childString("initialModel"),
                initialFileName = root.childString("initialFileName"),
                toolbarsVisible = root.childBoolean("toolbarsVisible", true),
                panelsVisible = root.childBoolean("panelsVisible", true)
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun JsonValue.childString(name: String): String? = get(name)?.asString()

    private fun JsonValue.childInt(name: String, defaultValue: Int? = null): Int? {
        val child = get(name) ?: return defaultValue
        return when {
            child.isLong || child.isDouble || child.isNumber -> child.asInt()
            child.isString -> child.asString().toIntOrNull() ?: defaultValue
            child.isBoolean -> if (child.asBoolean()) 1 else 0
            else -> defaultValue
        }
    }

    private fun JsonValue.childFloat(name: String, defaultValue: Float? = null): Float? {
        val child = get(name) ?: return defaultValue
        return when {
            child.isLong || child.isDouble || child.isNumber -> child.asFloat()
            child.isString -> child.asString().toFloatOrNull() ?: defaultValue
            child.isBoolean -> if (child.asBoolean()) 1f else 0f
            else -> defaultValue
        }
    }

    private fun JsonValue.childBoolean(name: String, defaultValue: Boolean): Boolean {
        val child = get(name) ?: return defaultValue
        return when {
            child.isBoolean -> child.asBoolean()
            child.isString -> child.asString().equals("true", ignoreCase = true)
            child.isNumber -> child.asInt() != 0
            else -> defaultValue
        }
    }

    private fun JsonValue.asFloatOrNull(): Float? {
        return when {
            isLong || isDouble || isNumber -> asFloat()
            isString -> asString().toFloatOrNull()
            isBoolean -> if (asBoolean()) 1f else 0f
            else -> null
        }
    }

    private fun JsonValue.asVector3OrNull(): Vector3? {
        return when {
            isArray && size >= 3 -> {
                val x = get(0)?.asFloatOrNull() ?: return null
                val y = get(1)?.asFloatOrNull() ?: return null
                val z = get(2)?.asFloatOrNull() ?: return null
                Vector3(x, y, z)
            }

            isObject -> {
                val x = get("x")?.asFloatOrNull() ?: return null
                val y = get("y")?.asFloatOrNull() ?: return null
                val z = get("z")?.asFloatOrNull() ?: return null
                Vector3(x, y, z)
            }

            else -> null
        }
    }

    private fun JsonValue.childVector3(name: String): Vector3? = get(name)?.asVector3OrNull()

    private fun JsonValue.childVector3Flexible(name: String): Vector3? {
        childVector3(name)?.let { return it }
        val x = childFloat("${name}X") ?: return null
        val y = childFloat("${name}Y") ?: return null
        val z = childFloat("${name}Z") ?: return null
        return Vector3(x, y, z)
    }

    override fun create() {
        val webSafeRuntime = if (BuildFlags.WEB_BUILD) true else isWebSafeRuntime()
        if (BuildFlags.WEB_BUILD) {
            webEmbedConfig = parseWebEmbedConfig(WebRuntime.bridge?.readEmbedConfigJson())
        }
        if (!BuildFlags.WEB_BUILD && !webSafeRuntime) {
            stdoutTap = StdoutTap.install()
        }
        if (!VisUI.isLoaded()) {
            if (BuildFlags.WEB_BUILD) {
                VisUI.setSkipGdxVersionCheck(true)
                requireWebInternalFile("com/kotcrab/vis/ui/skin/x1/uiskin.json")
                requireWebInternalFile("com/kotcrab/vis/ui/skin/x1/uiskin.atlas")
                requireWebInternalFile("com/kotcrab/vis/ui/skin/x1/uiskin.png")
                requireWebInternalFile("com/kotcrab/vis/ui/skin/x1/default.fnt")
                requireWebInternalFile("com/kotcrab/vis/ui/skin/x1/font-small.fnt")
                val webSkin = Gdx.files.internal("com/kotcrab/vis/ui/skin/x1/uiskin.json")
                VisUI.load(webSkin)
            } else {
                VisUI.load()
            }
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

        orbitCameraController = ShiftCameraController(camera, this::pickOrbitModelPoint).apply {
            rotateButton = Input.Buttons.RIGHT
            translateButton = Input.Buttons.RIGHT
        }
        orbitCameraController.target.set(cameraTarget)

        walkCameraController = WalkthroughCameraController(
            walkCamera,
            this::walkSupportHeightAt,
            eyeHeight = 6f
        )
        loadWalkthroughTuningPrefs()
        orthoCameraController = OrthographicCameraController(orthoCamera, cameraTarget)
        setCameraMode(CameraMode.ORBIT)
        installDir = resolveInstallDir()
        ensureDesktopBundledContent(installDir)
        tutorialManager = TutorialManager { resolveTutorialsDir(installDir) }
        statusModel = StatusModel(
            activeTool = ToolId.SELECT,
            message = "Select entities.",
            inputBuffer = ""
        )
        normalOverlayLineWidth = runtimePrefs.getFloat(normalLineWidthPrefKey, normalOverlayLineWidth).coerceIn(1f, 8f)
        feedbackOverlayLineWidth = runtimePrefs.getFloat(feedbackLineWidthPrefKey, feedbackOverlayLineWidth).coerceIn(1f, 16f)
        scene = GroupScene(Color(0.8f, 0.8f, 0.8f, 1f))
        vectorGlyphCatalog = loadVectorGlyphCatalog(vectorTextSettings.glyphSourcePath)
        modelCleanup = ModelCleanup(scene)
        guideManager = GuideManager()
        snapper = Snapper(
            activeCamera,
            scene,
            guideManager,
            lineSnapVisible = ::isLineSnapVisibleForSnapping,
            faceSnapVisible = ::isFaceVisibleForSnapping,
            initialGridSpacing = gridSpacing,
            snapPixels = snapEpsilon
        )
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
                SelectTool(
                    scene,
                    { activeCamera },
                    ::isArchitectureKindVisible,
                    ::isArchitectureKindUnlocked,
                    ::isArchitectureKindWireframe,
                    { kind ->
                        isBasicKindVisible(
                            when (kind) {
                                SelectTool.BasicSelectionKind.EDGE -> BasicSelectionFilterKind.EDGE
                                SelectTool.BasicSelectionKind.FACE -> BasicSelectionFilterKind.FACE
                                SelectTool.BasicSelectionKind.VOXEL -> BasicSelectionFilterKind.VOXEL
                                SelectTool.BasicSelectionKind.HOTSPOT -> BasicSelectionFilterKind.HOTSPOT
                                SelectTool.BasicSelectionKind.OBJECT -> BasicSelectionFilterKind.OBJECT
                            }
                        )
                    },
                    { kind ->
                        isBasicKindUnlocked(
                            when (kind) {
                                SelectTool.BasicSelectionKind.EDGE -> BasicSelectionFilterKind.EDGE
                                SelectTool.BasicSelectionKind.FACE -> BasicSelectionFilterKind.FACE
                                SelectTool.BasicSelectionKind.VOXEL -> BasicSelectionFilterKind.VOXEL
                                SelectTool.BasicSelectionKind.HOTSPOT -> BasicSelectionFilterKind.HOTSPOT
                                SelectTool.BasicSelectionKind.OBJECT -> BasicSelectionFilterKind.OBJECT
                            }
                        )
                    },
                    { kind ->
                        isBasicKindWireframe(
                            when (kind) {
                                SelectTool.BasicSelectionKind.EDGE -> BasicSelectionFilterKind.EDGE
                                SelectTool.BasicSelectionKind.FACE -> BasicSelectionFilterKind.FACE
                                SelectTool.BasicSelectionKind.VOXEL -> BasicSelectionFilterKind.VOXEL
                                SelectTool.BasicSelectionKind.HOTSPOT -> BasicSelectionFilterKind.HOTSPOT
                                SelectTool.BasicSelectionKind.OBJECT -> BasicSelectionFilterKind.OBJECT
                            }
                        )
                    },
                    { isBasicKindVisible(BasicSelectionFilterKind.DIMENSION) },
                    { isBasicKindUnlocked(BasicSelectionFilterKind.DIMENSION) },
                    { isBasicKindVisible(BasicSelectionFilterKind.TEXT) },
                    { isBasicKindUnlocked(BasicSelectionFilterKind.TEXT) },
                    ::glyphCatalogForSource,
                    ::observeTutorialUiAction
                ),
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
                MeshTool(scene) { toolController.setTool(ToolId.SELECT) },
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
                CircleTool(scene) { circleSegments },
                LinearDimensionTool(scene),
                TextTool(scene) { activeCamera },
                VectorTextTool(
                    scene,
                    { activeCamera },
                    { vectorTextSettings },
                    { vectorGlyphCatalog }
                ),
                PushPullTool(scene),
                MoveTool(scene),
                RotateTool(scene),
                ScaleTool(scene),
                StretchTool(scene),
                RotateStretchTool(scene),
                CopyMultipleTool(scene),
                PlanarTranslateMultipleTool(scene),
                VolumetricTranslateMultipleTool(scene),
                PlanarRotateMultipleTool(scene),
                HelicoidalRotateMultipleTool(scene),
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
            axisGuideAction = ::addAxisGuide,
            gridGuideAction = ::addGridGuide,
            exitGroupEditAction = ::exitGroupEditMode,
            startHotspotAddMode = ::addHotspotAtCursor,
            cancelHotspotAddMode = ::cancelHotspotAddPickMode,
            isHotspotAddModeActive = ::isHotspotAddPickModeActive,
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
        if (!BuildFlags.WEB_BUILD && !webSafeRuntime) {
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
                { circleSegments },
                { value -> applyCircleSegments(value, false) },
                pluginsDir,
                { modelFile }
            )
            toolController.registerTool(PluginToolAdapter(pluginHost))
        }
        toolController.addToolChangeListener { previous, next ->
            if (BuildFlags.WEB_BUILD) {
                return@addToolChangeListener
            }
            if (previous != ToolId.SELECT) {
                recordTutorialAction(
                    "tool.end.${previous.name.lowercase(Locale.US)}",
                    previous.displayName
                )
            }
            if (next != ToolId.SELECT) {
                recordTutorialAction(
                    "tool.start.${next.name.lowercase(Locale.US)}",
                    next.displayName
                )
            }
        }
        uiOverlay = SketchUiOverlay(
            toolController,
            statusModel,
            ::showOpenModelDialog,
            ::showSaveAsModelDialog,
            ::showImportMeshDialog,
            ::showExportMeshDialog,
            ::runCleanup,
            ::deleteSelection,
            ::flipSelectedFaces,
            ::voxelizeSelectedFaces,
            ::addHotspotAtCursor,
            ::selectionInfo,
            ::updateSelectionFilterFromSelectionPanel,
            ::updateSelectedText,
            ::updateSelectedTextSize,
            ::updateSelectedTextScreen,
            ::updateSelectionColor,
            ::groupInfo,
            ::updateGroupName,
            ::updateGroupGlue,
            ::enterSelectedObjectEditMode,
            ::objectPrototypeInfo,
            ::startObjectPlacement,
            ::deleteObjectPrototype,
            ::modelUnitInfo,
            ::updateModelUnit,
            { gridSpacing },
            ::setGridSpacing,
            { circleSegments },
            ::setCircleSegments,
            { snapEpsilon },
            ::setSnapEpsilon,
            ::walkthroughTuningInfo,
            ::updateWalkthroughTuning,
            lightingSettings,
            ::applyLightingSettings,
            shadowSettings,
            ::applyShadowSettings,
            polylineSettings,
            vectorTextSettings,
            ::vectorGlyphSourcePath,
            ::loadVectorGlyphCatalogFromPath,
            ::showVectorGlyphSourceDialog,
            ::updateSelectedVectorTextTracking,
            ::updateSelectedVectorTextLineSpacing,
            architectureSettings,
            hvacSettings,
            hotspotSettings,
            ::architectureSelectionInfo,
            ::architectureSelectionSummary,
            ::updateArchitectureElementName,
            ::updateArchitectureWallParameters,
            ::updateArchitectureSlabParameters,
            ::updateArchitectureStairParameters,
            ::updateArchitectureFrameParameters,
            ::hotspotSelectionInfo,
            ::updateHotspotDefaultOperation,
            ::updateHotspotDefaultShape,
            ::updateHotspotDefaultColor,
            ::updateHotspotName,
            ::updateHotspotOperation,
            ::updateHotspotShape,
            ::updateHotspotColor,
            ::addHotspotAtCursor,
            ::deleteSelectedHotspots,
            ::attachSelectionToHotspot,
            ::selectHotspotAttachedGeometry,
            ::beginHotspotReferencePick,
            ::clearHotspotReference,
            { activeCameraMode },
            ::setCameraMode,
            ::updateNormalOverlayLineWidth,
            ::updateFeedbackOverlayLineWidth,
            ::tutorialUiState,
            ::startTutorialRecording,
            ::stopTutorialRecording,
            ::playTutorial,
            ::toggleTutorialPause,
            ::stopTutorialPlayback,
            ::tutorialPreviousStep,
            ::tutorialNextStep,
            ::observeTutorialUiAction
        )

        if (!BuildFlags.WEB_BUILD && !webSafeRuntime) {
            // Set up plugin host for UI.
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
                id = "view.vector_text_settings",
                name = "View> Vector Text Settings",
                description = "Show vector text settings panel",
                icon = "view",
                category = "View",
                tags = listOf("vector", "text", "settings", "panel"),
                priority = 1,
                execute = {
                    uiOverlay.showVectorTextSettingsPanel()
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
                id = "view.hotspot_settings",
                name = "View> Hotspot Settings",
                description = "Show hotspot settings panel",
                icon = "view",
                category = "View",
                tags = listOf("hotspot", "settings", "panel"),
                priority = 1,
                execute = {
                    uiOverlay.showHotspotSettingsPanel()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "view.help_about",
                name = "View> Help / About",
                description = "Show help and platform notes panel",
                icon = "view",
                category = "View",
                tags = listOf("help", "about", "android", "input"),
                priority = 1,
                execute = {
                    uiOverlay.showHelpPanel()
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
                id = "import.mesh",
                name = "Import> Mesh (OBJ/STL/FBX/glTF/DAE/DXF/3MF/AMF/IFC)",
                description = "Import mesh geometry and place as object",
                icon = "import",
                category = "Import",
                tags = listOf("import", "mesh", "obj", "stl", "fbx", "gltf", "glb", "dae", "dxf", "3mf", "amf", "ifc"),
                priority = 1,
                execute = {
                    showImportMeshDialog()
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
        )
        pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "export.mesh",
                name = "Export> Mesh (OBJ/STL/FBX/glTF/DAE/DXF/3MF/AMF/IFC)",
                description = "Export mesh geometry in selected format",
                icon = "export",
                category = "Export",
                tags = listOf("export", "mesh", "obj", "stl", "fbx", "gltf", "glb", "dae", "dxf", "3mf", "amf", "ifc"),
                priority = 1,
                execute = {
                    showExportMeshDialog()
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
            ToolId.VECTOR_TEXT,
            ToolId.PUSH_PULL,
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
            pluginHost.getCommandPalette().registerCommand(
            com.github.alfu32.sketch.plugin.PaletteCommand(
                id = "tool.vector_text",
                name = "Tool> Vector Text",
                description = "Activate vector text tool",
                icon = "tool",
                category = "Tools",
                tags = listOf("vector", "text", "glyph"),
                priority = 1,
                execute = {
                    toolController.setTool(ToolId.VECTOR_TEXT)
                    com.github.alfu32.sketch.plugin.PluginResult.success()
                }
            )
            )
        }

        toolPointer = ToolPointerProcessor(toolController, snapper) { distanceOverrideSnap }
        val uiBlocker = object : com.badlogic.gdx.InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (pendingModelLoad != null) {
                    return true
                }
                if (uiOverlay.isUiHit(screenX, screenY)) {
                    return true
                }
                uiOverlay.clearUiFocus()
                val targetHotspotId = hotspotReferencePickTargetId
                val targetGroup = hotspotReferencePickTargetGroup
                if (targetHotspotId != null && targetGroup != null && button == Input.Buttons.LEFT) {
                    val snap = distanceOverrideSnap ?: snapper.compute(screenX, screenY)
                    val world = snap.world
                    if (snap.valid && world != null) {
                        val updated = scene.updateHotspotReferencePosition(
                            group = targetGroup,
                            id = targetHotspotId,
                            referencePosition = targetGroup.toLocal(world)
                        )
                        statusModel.message = if (updated) {
                            "Hotspot reference updated."
                        } else {
                            "Failed to update hotspot reference."
                        }
                    } else {
                        statusModel.message = "Reference pick canceled: invalid point."
                    }
                    hotspotReferencePickTargetId = null
                    hotspotReferencePickTargetGroup = null
                    return true
                }
                val addPickGroup = hotspotAddPickTargetGroup
                if (addPickGroup != null && button == Input.Buttons.LEFT) {
                    val snap = distanceOverrideSnap ?: snapper.compute(screenX, screenY)
                    val world = snap.world
                    if (snap.valid && world != null) {
                        val hotspot = scene.addHotspot(
                            group = addPickGroup,
                            position = addPickGroup.toLocal(world),
                            operation = hotspotSettings.defaultOperation,
                            shape = hotspotSettings.defaultShape,
                            color = hotspotSettings.defaultColor
                        )
                        statusModel.message = if (hotspot != null) {
                            "Hotspot added."
                        } else {
                            "Failed to add hotspot."
                        }
                    } else {
                        statusModel.message = "Hotspot add canceled: invalid point."
                    }
                    hotspotAddPickTargetGroup = null
                    return true
                }
                return false
            }

            override fun keyDown(keycode: Int): Boolean {
                if (pendingModelLoad != null) {
                    return true
                }
                if ((keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) && hotspotReferencePickTargetId != null) {
                    hotspotReferencePickTargetId = null
                    hotspotReferencePickTargetGroup = null
                    statusModel.message = "Hotspot reference pick canceled."
                    return true
                }
                if ((keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) && hotspotAddPickTargetGroup != null) {
                    hotspotAddPickTargetGroup = null
                    statusModel.message = "Hotspot add canceled."
                    return true
                }
                return uiOverlay.isUiCapturingInputByPointer()
            }

            override fun keyUp(keycode: Int): Boolean {
                if (pendingModelLoad != null) {
                    return true
                }
                return uiOverlay.isUiCapturingInputByPointer()
            }

            override fun keyTyped(character: Char): Boolean {
                if (pendingModelLoad != null) {
                    return true
                }
                return uiOverlay.isUiCapturingInputByPointer()
            }
        }
        val cameraScrollForwarder = CameraScrollForwarder(
            processorProvider = { activeCameraInputProcessor },
            shouldForward = { !uiOverlay.isUiHit(Gdx.input.x, Gdx.input.y) }
        )
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
        setupMeshes()
        setupRenderables()
        markSceneRuntimeDirty()
        setupLighting()
        setupUndoManager()
        applyLightingSettings(lightingSettings)
        applyShadowSettings(shadowSettings)
        uiOverlay.refreshLightingControls()
        scene.setChangeListener { onModelChanged() }
        if (!BuildFlags.WEB_BUILD && !webSafeRuntime) {
            pluginHost.loadCatalog()
            pluginHost.reloadEnabledAndInit()
        }
        uiOverlay.refreshPluginPanels()
        if (!BuildFlags.WEB_BUILD && !webSafeRuntime) {
            setupMcpServer()
        }
        maybeShowAndroidFirstRunInputDialog()
        applyWebEmbedUiOptions()
        emitWebEmbedReady()
        if (BuildFlags.WEB_BUILD) {
            loadModel()
        } else {
            deferredStartupLoad = true
            deferredStartupLoadFrames = 1
        }
        if (!BuildFlags.WEB_BUILD && !webSafeRuntime) {
            startConsoleIfRequested()
        }
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
        val tap = stdoutTap ?: return
        mcpServer = McpHttpServer(
            initialPort = mcpPort,
            stdoutTap = tap,
            listCommands = ::listPaletteCommandsForMcp,
            executeCommand = ::executePaletteCommandForMcp,
            executeConsoleCommand = ::executeConsoleCommandForMcp,
            dispatchPointerEvent = ::dispatchPointerEventForMcp,
            contractProvider = ::buildMcpContractForMcp,
            sceneSummaryProvider = ::buildMcpSceneSummaryForMcp,
            selectionSummaryProvider = ::buildMcpSelectionSummaryForMcp
        )
        if (isPackagedWindowsRuntime()) {
            val message = "MCP HTTP server auto-start disabled in packaged Windows builds."
            statusModel.message = message
            println(message)
            return
        }
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
        val ray = com.badlogic.gdx.math.collision.Ray(rayOrigin, rayDir)
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

        forEachCameraRayCandidateGroup(ray) { group ->
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

    private fun pickOrbitModelPoint(screenX: Int, screenY: Int): Vector3? {
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        var bestPoint: Vector3? = null
        var bestDist2 = Float.POSITIVE_INFINITY

        fun testGroup(group: GroupScene.GroupNode) {
            val localRay = com.badlogic.gdx.math.collision.Ray(
                group.toLocal(ray.origin),
                group.vectorToLocal(ray.direction).nor()
            )
            val hit = group.faceStore.pickTriangle(localRay) ?: return
            val worldHit = group.toWorld(hit.point)
            val dist2 = worldHit.dst2(ray.origin)
            if (dist2 < bestDist2) {
                bestDist2 = dist2
                bestPoint = worldHit
            }
        }

        forEachCameraRayCandidateGroup(ray) { group ->
            testGroup(group)
        }
        return bestPoint
    }

    private fun forEachCameraRayCandidateGroup(
        ray: com.badlogic.gdx.math.collision.Ray,
        visitor: (GroupScene.GroupNode) -> Unit
    ) {
        fun maybeVisit(group: GroupScene.GroupNode) {
            val bounds = group.worldBounds() ?: group.geometryWorldBounds()
            if (bounds != null) {
                val t = rayAabbIntersectionT(ray.origin, ray.direction, bounds.min, bounds.max)
                if (t == null) {
                    return
                }
            }
            visitor(group)
        }
        maybeVisit(scene.root)
        scene.walkGroups(scene.root) { group ->
            maybeVisit(group)
        }
    }

    private fun rayAabbIntersectionT(origin: Vector3, direction: Vector3, min: Vector3, max: Vector3): Float? {
        var tMin = Float.NEGATIVE_INFINITY
        var tMax = Float.POSITIVE_INFINITY
        fun axis(originValue: Float, directionValue: Float, minValue: Float, maxValue: Float): Boolean {
            if (kotlin.math.abs(directionValue) < 1e-6f) {
                return originValue in minValue..maxValue
            }
            val inv = 1f / directionValue
            var t0 = (minValue - originValue) * inv
            var t1 = (maxValue - originValue) * inv
            if (t0 > t1) {
                val swap = t0
                t0 = t1
                t1 = swap
            }
            tMin = kotlin.math.max(tMin, t0)
            tMax = kotlin.math.min(tMax, t1)
            return tMax >= tMin
        }
        if (!axis(origin.x, direction.x, min.x, max.x)) return null
        if (!axis(origin.y, direction.y, min.y, max.y)) return null
        if (!axis(origin.z, direction.z, min.z, max.z)) return null
        if (tMax < 0f) return null
        return if (tMin >= 0f) tMin else tMax
    }

    override fun render() {
        if (deferredStartupLoad) {
            if (deferredStartupLoadFrames > 0) {
                deferredStartupLoadFrames -= 1
            } else {
                deferredStartupLoad = false
                loadModel()
            }
        }
        val nowMs = System.currentTimeMillis()
        processPendingModelLoad()
        if (nowMs >= nextAsyncMaintenanceAtMs) {
            scene.processAsyncMaintenance(nowMs)
            nextAsyncMaintenanceAtMs = nowMs + asyncMaintenanceIntervalMs
        }
        statusModel.backgroundStatus = ""
        updateActiveCamera(Gdx.graphics.deltaTime)
        handleGlobalDistanceShortcut()
        updateCursorStatus()
        undoManager.update()
        flushAutosaveIfDue()
        flushWebEmbedChangeIfDue()
        drainAsyncModelSaveStatus()
        if (BuildFlags.WEB_BUILD) {
            processWebEmbedCommands()
        }
        if (::pluginHost.isInitialized) {
            pluginHost.dispatchUpdate(Gdx.graphics.deltaTime)
        }
        toolController.update(Gdx.graphics.deltaTime)
        collectFeedbackWorldLines()
        var rebuiltInstancesThisFrame = false
        if (instanceGeometryDirty) {
            scene.recomputeAllInstanceGeometryFromPrototypes()
            instanceGeometryDirty = false
            faceMeshDirty = true
            rebuiltInstancesThisFrame = true
        }

        // Split heavy instance recompute and face-mesh rebuild across frames to reduce UI stalls on large models.
        val currentFaceMeshStamp = if (rebuiltInstancesThisFrame) {
            faceMeshVisualStamp
        } else {
            computeFaceMeshVisualStamp()
        }
        if (!rebuiltInstancesThisFrame && (faceMeshDirty || currentFaceMeshStamp != faceMeshVisualStamp)) {
            updateFaceMesh()
            faceMeshVisualStamp = currentFaceMeshStamp
            faceMeshDirty = false
            shadowPassDirty = true
        }
        syncGroupFaceBundles()
        visibleRenderableGroupIds = emptySet()
        updateShadowCameraFromModelBounds()
        if (shadowPassDirty) {
            renderShadowPass()
            shadowPassDirty = false
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        val skyColor=Color(0.6f,0.75f,0.9f,1f,)
        Gdx.gl.glClearColor(0.6f, 0.75f, 0.9f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        setLineWidth(2f)
        shapeRenderer.projectionMatrix = activeCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawGrid(baseGridHalfSize, gridSpacing)
        shapeRenderer.end()

        modelBatch.begin(activeCamera)
        modelBatch.render(faceBackRenderable, environment)
        modelBatch.render(faceFrontRenderable, environment)
        scene.walkGroups(scene.root) { group ->
            val bundle = ensureGroupFaceBundle(group)
            modelBatch.render(bundle.backRenderable, environment)
            modelBatch.render(bundle.frontRenderable, environment)
        }
        modelBatch.render(groundRenderable, environment)
        modelBatch.end()

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawAxes(2.5f)
        drawActiveGroupAxes(1.8f)
        drawCameraTarget(1f)
        drawGuides()
        drawAdaptiveGridPointCloud()
        drawSelectionHighlights()
        drawDraftLines()
        drawVectorTextEdges3D()
        drawArchitectureHoleGuides()
        drawHvacControlPoints()
        drawDimensions()
        toolController.render(shapeRenderer)
        shapeRenderer.end()

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        processPendingPngExports()
        drawAnnotations2D()
        drawSelectedSegments2DOverlay()
        drawCursor2DOverlay()
        drawHotspots2D()
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
            applyNormalOverlayLineWidth()
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
            resetNormalOverlayLineWidth()
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        }

        drawFeedbackLines2DOverlay()

        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        uiOverlay.stage.viewport.apply()
        uiOverlay.act(Gdx.graphics.deltaTime)
        uiOverlay.draw()
        drawTutorialArrowOverlay()
        processPendingTutorialPreviewCaptures()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawTutorialArrowOverlay() {
        val arrow = uiOverlay.tutorialArrow() ?: return
        val start = arrow.first
        val end = arrow.second
        val direction = Vector2(end).sub(start)
        if (direction.len2() < 1f) {
            return
        }
        val dir = direction.nor()
        val headLength = 18f
        val headWidth = 8f
        val base = Vector2(end).mulAdd(dir, -headLength)
        val perp = Vector2(-dir.y, dir.x).scl(headWidth)

        shapeRenderer.projectionMatrix = uiOverlay.stage.camera.combined
        shapeRenderer.transformMatrix = Matrix4().idt()
        applyNormalOverlayLineWidth()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.9f, 0.1f, 0.1f, 1f)
        shapeRenderer.line(start.x, start.y, base.x, base.y)
        shapeRenderer.end()
        resetNormalOverlayLineWidth()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.9f, 0.1f, 0.1f, 1f)
        shapeRenderer.triangle(
            end.x,
            end.y,
            base.x + perp.x,
            base.y + perp.y,
            base.x - perp.x,
            base.y - perp.y
        )
        shapeRenderer.end()
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
            flushAutosaveIfDue(force = true)
            saveModel()
        }
        if (::walkCameraController.isInitialized) {
            saveWalkthroughTuningPrefs()
        }
        if (::pluginHost.isInitialized) {
            pluginHost.dispatchClose()
        }
        waitForAsyncModelSave()
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
        groupFaceBundles.values.forEach { it.mesh.dispose() }
        groupFaceBundles.clear()
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

    private fun drawCursor2DOverlay() {
        val snap = lastSnap ?: return
        val hit = snap.world ?: return
        val center = activeCamera.project(Vector3(hit))
        if (!center.x.isFinite() || !center.y.isFinite() || !center.z.isFinite()) {
            return
        }
        if (center.z !in 0f..1f) {
            return
        }

        shapeRenderer.projectionMatrix = uiOverlay.stage.camera.combined
        shapeRenderer.transformMatrix = Matrix4().idt()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        drawCursorAxis2D(hit, center, Vector3(1f, 0f, 0f), Color(0.9f, 0.2f, 0.2f, 1f))
        drawCursorAxis2D(hit, center, Vector3(0f, 1f, 0f), Color(0.2f, 0.45f, 0.95f, 1f))
        drawCursorAxis2D(hit, center, Vector3(0f, 0f, 1f), Color(0.2f, 0.85f, 0.3f, 1f))
        if (snap.type != com.github.alfu32.sketch.input.SnapType.NONE) {
            shapeRenderer.color = Color(1f, 0.95f, 0.6f, 0.95f)
            shapeRenderer.circle(center.x, center.y, 3.8f, 18)
        }
        shapeRenderer.end()
    }

    private fun drawCursorAxis2D(centerWorld: Vector3, centerScreen: Vector3, axisWorld: Vector3, baseColor: Color) {
        val axisTip = activeCamera.project(Vector3(centerWorld).add(axisWorld))
        if (!axisTip.x.isFinite() || !axisTip.y.isFinite() || !axisTip.z.isFinite()) {
            return
        }
        val dx = axisTip.x - centerScreen.x
        val dy = axisTip.y - centerScreen.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len <= 1e-4f) {
            return
        }
        val nx = dx / len
        val ny = dy / len
        val positiveLenPx = 16f
        val negativeLenPx = 16f
        val lineWidthPx = 3.4f

        val negX = centerScreen.x - nx * negativeLenPx
        val negY = centerScreen.y - ny * negativeLenPx
        val posX = centerScreen.x + nx * positiveLenPx
        val posY = centerScreen.y + ny * positiveLenPx

        shapeRenderer.color = lightenColor(baseColor, 0.45f)
        shapeRenderer.rectLine(negX, negY, centerScreen.x, centerScreen.y, lineWidthPx)
        shapeRenderer.circle(negX, negY, lineWidthPx * 0.55f, 14)

        shapeRenderer.color = darkenColor(baseColor, 0.25f)
        shapeRenderer.rectLine(centerScreen.x, centerScreen.y, posX, posY, lineWidthPx)
        shapeRenderer.circle(posX, posY, lineWidthPx * 0.6f, 14)
    }

    private fun lightenColor(color: Color, amount: Float): Color {
        val a = amount.coerceIn(0f, 1f)
        return Color(
            color.r + (1f - color.r) * a,
            color.g + (1f - color.g) * a,
            color.b + (1f - color.b) * a,
            color.a
        )
    }

    private fun darkenColor(color: Color, amount: Float): Color {
        val a = (1f - amount.coerceIn(0f, 1f))
        return Color(color.r * a, color.g * a, color.b * a, color.a)
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

    private fun drawAdaptiveGridPointCloud() {
        val snap = lastSnap
        if (snap == null || !snap.valid) {
            adaptiveGridCloudState = null
            return
        }
        val snapCenter = snap.world ?: return
        val snapNormal = (snap.normal ?: Vector3(0f, 1f, 0f)).cpy()
        if (snapNormal.len2() <= 1e-6f) {
            adaptiveGridCloudState = null
            return
        }
        snapNormal.nor()
        val baseExtent = baseGridHalfSize * gridSpacing
        val onGround = kotlin.math.abs(snapCenter.y) <= gridSpacing * 0.2f
        val nearBaseGridEdge = onGround &&
            (kotlin.math.abs(snapCenter.x) >= baseExtent * 0.75f || kotlin.math.abs(snapCenter.z) >= baseExtent * 0.75f)
        val outsideBaseGrid = !onGround ||
            kotlin.math.abs(snapCenter.x) > baseExtent ||
            kotlin.math.abs(snapCenter.z) > baseExtent
        val shouldActivate = nearBaseGridEdge || outsideBaseGrid || guideManager.hasGridGuides()
        if (!shouldActivate && adaptiveGridCloudState == null) {
            return
        }

        var state = adaptiveGridCloudState
        if (state == null) {
            state = createAdaptiveGridCloudState(snapCenter, snapNormal)
        } else if (shouldReanchorAdaptiveGridCloud(state, snapCenter, snapNormal)) {
            state = createAdaptiveGridCloudState(snapCenter, snapNormal)
        }
        adaptiveGridCloudState = state
        val center = state.center
        val axisU = state.axisU
        val axisV = state.axisV
        val step = gridSpacing
        val crossHalf = (gridSpacing * 0.08f).coerceIn(0.05f, 0.18f)
        val ux = axisU.x * crossHalf
        val uy = axisU.y * crossHalf
        val uz = axisU.z * crossHalf
        val vx = axisV.x * crossHalf
        val vy = axisV.y * crossHalf
        val vz = axisV.z * crossHalf

        shapeRenderer.color = Color(0.08f, 0.16f, 0.32f, 0.7f)
        for (i in -adaptiveGridCloudRadiusUnits..adaptiveGridCloudRadiusUnits) {
            for (j in -adaptiveGridCloudRadiusUnits..adaptiveGridCloudRadiusUnits) {
                val px = center.x + axisU.x * (i * step) + axisV.x * (j * step)
                val py = center.y + axisU.y * (i * step) + axisV.y * (j * step)
                val pz = center.z + axisU.z * (i * step) + axisV.z * (j * step)
                shapeRenderer.line(px - ux, py - uy, pz - uz, px + ux, py + uy, pz + uz)
                shapeRenderer.line(px - vx, py - vy, pz - vz, px + vx, py + vy, pz + vz)
            }
        }
    }

    private fun createAdaptiveGridCloudState(center: Vector3, normal: Vector3): AdaptiveGridCloudState {
        val n = Vector3(normal).nor()
        val ref = if (kotlin.math.abs(n.y) < 0.95f) Vector3(0f, 1f, 0f) else Vector3(1f, 0f, 0f)
        var axisU = Vector3(ref).crs(n)
        if (axisU.len2() <= 1e-6f) {
            axisU = Vector3(0f, 0f, 1f).crs(n)
        }
        axisU.nor()
        val axisV = Vector3(n).crs(axisU).nor()
        val planeDistance = center.dot(n)
        val planeOrigin = Vector3(n).scl(planeDistance)
        val local = Vector3(center).sub(planeOrigin)
        val u = local.dot(axisU)
        val v = local.dot(axisV)
        val snappedU = kotlin.math.round(u / gridSpacing) * gridSpacing
        val snappedV = kotlin.math.round(v / gridSpacing) * gridSpacing
        val snappedCenter = Vector3(planeOrigin).mulAdd(axisU, snappedU).mulAdd(axisV, snappedV)
        return AdaptiveGridCloudState(
            center = snappedCenter,
            normal = n,
            axisU = axisU,
            axisV = axisV,
            halfExtent = adaptiveGridCloudRadiusUnits * gridSpacing
        )
    }

    private fun shouldReanchorAdaptiveGridCloud(
        state: AdaptiveGridCloudState,
        cursorPoint: Vector3,
        cursorNormal: Vector3
    ): Boolean {
        val normalAlignment = state.normal.dot(cursorNormal)
        if (normalAlignment < 0.98f) {
            return true
        }
        val local = Vector3(cursorPoint).sub(state.center)
        val planeDistance = kotlin.math.abs(local.dot(state.normal))
        if (planeDistance > gridSpacing * 0.35f) {
            return true
        }
        val u = local.dot(state.axisU)
        val v = local.dot(state.axisV)
        return kotlin.math.abs(u) > state.halfExtent || kotlin.math.abs(v) > state.halfExtent
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
        if (BuildFlags.WEB_BUILD) {
            statusModel.message = "SVG export dialog is not available in web runtime yet."
            return
        }
        val statusBefore = statusModel.message
        val requested = showDesktopFileDialog("Export SVG (View)", FileDialog.SAVE, "view.svg")
        if (requested == null) {
            if (statusModel.message == statusBefore) {
                statusModel.message = "SVG export cancelled."
            }
            return
        }
        exportSvgView(ensureFileExtension(requested.absoluteFile, setOf("svg"), "svg"))
    }

    private fun modelFileChooserDirectory(): String {
        val currentParent = if (::modelFile.isInitialized) modelFile.parentFile else null
        if (currentParent != null && currentParent.exists()) {
            return currentParent.absolutePath
        }
        val writable = resolveDesktopWritableDataDir()
        if (writable.exists() || writable.mkdirs()) {
            return writable.absolutePath
        }
        return System.getProperty("user.dir") ?: "."
    }

    private fun isDesktopFileDialogAvailable(): Boolean {
        return !BuildFlags.WEB_BUILD && !isAndroidRuntime() && !GraphicsEnvironment.isHeadless()
    }

    private data class DesktopFileDialogOption(
        val label: String,
        val extensions: Set<String>,
        val defaultExtension: String
    )

    private fun showDesktopFileDialog(
        title: String,
        mode: Int,
        defaultFileName: String? = null,
        allowedExtensions: Set<String> = emptySet()
    ): File? {
        if (!isDesktopFileDialogAvailable()) {
            return null
        }
        val resultHolder = arrayOfNulls<File>(1)
        val openDialog = Runnable {
            val dialog = FileDialog(null as Frame?, title, mode)
            try {
                dialog.directory = modelFileChooserDirectory()
                if (!defaultFileName.isNullOrBlank()) {
                    dialog.file = defaultFileName
                }
                if (allowedExtensions.isNotEmpty() && mode == FileDialog.LOAD) {
                    dialog.filenameFilter = FilenameFilter { _, name ->
                        val ext = name.substringAfterLast('.', "").lowercase()
                        ext in allowedExtensions
                    }
                }
                dialog.isVisible = true
                val selectedFile = dialog.file
                val selectedDirectory = dialog.directory
                if (!selectedFile.isNullOrBlank() && !selectedDirectory.isNullOrBlank()) {
                    resultHolder[0] = File(selectedDirectory, selectedFile).absoluteFile
                }
            } finally {
                dialog.dispose()
            }
        }
        return try {
            if (EventQueue.isDispatchThread()) {
                openDialog.run()
            } else {
                EventQueue.invokeAndWait(openDialog)
            }
            resultHolder[0]
        } catch (t: Throwable) {
            statusModel.message = "Desktop file dialog failed: ${t.message ?: t.javaClass.simpleName}"
            null
        }
    }

    private fun chooseDesktopFileDialogOption(
        title: String,
        message: String,
        options: List<DesktopFileDialogOption>,
        defaultIndex: Int = 0
    ): DesktopFileDialogOption? {
        if (options.isEmpty()) {
            return null
        }
        if (options.size == 1 || !isDesktopFileDialogAvailable()) {
            return options.first()
        }
        val labels = options.map { it.label }.toTypedArray()
        val defaultLabel = labels[defaultIndex.coerceIn(0, labels.lastIndex)]
        var selectedLabel: String? = null
        val chooseOption = Runnable {
            selectedLabel = JOptionPane.showInputDialog(
                null,
                message,
                title,
                JOptionPane.PLAIN_MESSAGE,
                null,
                labels,
                defaultLabel
            ) as? String
        }
        return try {
            if (EventQueue.isDispatchThread()) {
                chooseOption.run()
            } else {
                EventQueue.invokeAndWait(chooseOption)
            }
            options.firstOrNull { it.label == selectedLabel }
        } catch (t: Throwable) {
            statusModel.message = "Desktop format picker failed: ${t.message ?: t.javaClass.simpleName}"
            null
        }
    }

    private fun replaceFileExtension(fileName: String, extension: String): String {
        val base = fileName.substringBeforeLast('.', fileName).ifBlank { "octodraw" }
        return "$base.$extension"
    }

    private fun ensureFileExtension(target: File, allowedExtensions: Set<String>, defaultExtension: String): File {
        val ext = target.extension.lowercase()
        return if (ext in allowedExtensions) target else File(target.parentFile, "${target.name}.$defaultExtension")
    }

    private fun showOpenModelDialog() {
        if (BuildFlags.WEB_BUILD) {
            showWebOpenModelDialog()
            return
        }
        if (isAndroidRuntime()) {
            if (!showAndroidSafOpenModelDialog()) {
                showAndroidOpenModelDialog()
            }
            return
        }
        val option = chooseDesktopFileDialogOption(
            title = "Open Octodraw Model",
            message = "Choose the file type to show in the system dialog.",
            options = listOf(
                DesktopFileDialogOption("All supported (*.octd, *.k3d)", setOf("octd", "k3d"), "octd"),
                DesktopFileDialogOption("Octodraw (*.octd)", setOf("octd"), "octd"),
                DesktopFileDialogOption("Legacy K3D (*.k3d)", setOf("k3d"), "k3d")
            )
        ) ?: run {
            statusModel.message = "Open cancelled."
            return
        }
        val statusBefore = statusModel.message
        val target = showDesktopFileDialog(
            title = "Open Octodraw Model - ${option.label}",
            mode = FileDialog.LOAD,
            allowedExtensions = option.extensions
        )
        if (target == null) {
            if (statusModel.message == statusBefore) {
                statusModel.message = "Open cancelled."
            }
            return
        }
        if (::scene.isInitialized && ::camera.isInitialized && ::modelFile.isInitialized) {
            saveModel()
        }
        modelFile = target.absoluteFile
        loadModel()
        updateWindowTitle()
        statusModel.message = "Opening ${modelFile.name}..."
    }

    private fun showSaveAsModelDialog() {
        if (BuildFlags.WEB_BUILD) {
            showWebSaveAsModelDialog()
            return
        }
        if (isAndroidRuntime()) {
            if (!showAndroidSafSaveAsModelDialog()) {
                showAndroidSaveAsModelDialog()
            }
            return
        }
        val option = chooseDesktopFileDialogOption(
            title = "Save Octodraw Model As",
            message = "Choose the target model format before opening the system dialog.",
            options = listOf(
                DesktopFileDialogOption("Octodraw (*.octd)", setOf("octd"), "octd"),
                DesktopFileDialogOption("Legacy K3D (*.k3d)", setOf("k3d"), "k3d")
            )
        ) ?: run {
            statusModel.message = "Save As cancelled."
            return
        }
        val statusBefore = statusModel.message
        val requested = showDesktopFileDialog(
            title = "Save Octodraw Model As - ${option.label}",
            mode = FileDialog.SAVE,
            defaultFileName = replaceFileExtension(
                if (::modelFile.isInitialized) modelFile.name else "octodraw.octd",
                option.defaultExtension
            )
        )
        if (requested == null) {
            if (statusModel.message == statusBefore) {
                statusModel.message = "Save As cancelled."
            }
            return
        }
        val target = ensureFileExtension(requested.absoluteFile, option.extensions, option.defaultExtension)
        target.parentFile?.mkdirs()
        modelFile = target
        saveModel()
        updateWindowTitle()
        statusModel.message = "Saved ${target.name}"
    }

    private fun sanitizeModelFileName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "octodraw.octd" }
        val ext = cleaned.substringAfterLast('.', "").lowercase()
        return if (ext == "octd" || ext == "k3d") cleaned else "$cleaned.octd"
    }

    private fun showWebOpenModelDialog() {
        val bridge = WebRuntime.bridge
        if (bridge == null) {
            statusModel.message = "Web file open is unavailable in this build."
            return
        }
        bridge.openTextDocument(".octd,.k3d,application/json,text/plain") { displayName, content ->
            if (content.isNullOrBlank()) {
                statusModel.message = "Open cancelled."
                return@openTextDocument
            }
            modelFile = File(
                resolveDesktopWritableDataDir(),
                sanitizeModelFileName(displayName ?: "octodraw-web.octd")
            ).absoluteFile
            if (!loadModelFromText(content, displayName ?: modelFile.name)) {
                statusModel.message = "Open failed: invalid model content."
                return@openTextDocument
            }
            if (!isEmbeddedWebRuntime() && bridge.writeLocalStorage(webAutosaveStorageKey, content)) {
                bridge.writeLocalStorage(webModelNameStorageKey, modelFile.name)
            }
            updateWindowTitle()
            statusModel.message = "Opened ${displayName ?: modelFile.name}"
            if (isEmbeddedWebRuntime()) {
                emitWebEmbedChange("open", content)
            }
        }
    }

    private fun showWebSaveAsModelDialog() {
        val bridge = WebRuntime.bridge
        if (bridge == null) {
            statusModel.message = "Web download is unavailable in this build."
            return
        }
        val suggestedName = sanitizeModelFileName(
            if (::modelFile.isInitialized) modelFile.name else "octodraw-web.octd"
        )
        val snapshot = snapshotForPersistence(includeUndoHistory = true)
        val text = ModelPersistence.saveSnapshotText(snapshot)
        val base64 = java.util.Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        bridge.saveBinaryDocument(suggestedName, base64, "application/json") { success, message ->
            if (success) {
                modelFile = File(resolveDesktopWritableDataDir(), suggestedName).absoluteFile
                updateWindowTitle()
                bridge.writeLocalStorage(webAutosaveStorageKey, text)
                bridge.writeLocalStorage(webModelNameStorageKey, modelFile.name)
                statusModel.message = "Saved $suggestedName"
                if (isEmbeddedWebRuntime()) {
                    emitWebEmbedChange("saveAs", text)
                }
            } else {
                statusModel.message = message ?: "Save failed."
            }
        }
    }

    private fun showAndroidSafOpenModelDialog(): Boolean {
        val bridge = AndroidSaf.bridge ?: return false
        bridge.openDocument { uri, displayName ->
            if (uri.isNullOrBlank()) {
                statusModel.message = "Open cancelled."
                return@openDocument
            }
            val data = bridge.readBytes(uri)
            if (data == null || data.isEmpty()) {
                statusModel.message = "Open failed: cannot read selected file."
                return@openDocument
            }
            val fileName = sanitizeModelFileName(displayName ?: "octodraw.octd")
            val localTarget = File(androidModelRootDirectory(), fileName).absoluteFile
            try {
                localTarget.parentFile?.mkdirs()
                localTarget.writeBytes(data)
            } catch (t: Throwable) {
                statusModel.message = "Open failed: ${t.message ?: t.javaClass.simpleName}"
                return@openDocument
            }
            if (::scene.isInitialized && ::camera.isInitialized && ::modelFile.isInitialized) {
                saveModel()
            }
            modelFile = localTarget
            loadModel()
            updateWindowTitle()
            statusModel.message = "Opening ${displayName ?: localTarget.name}..."
        }
        return true
    }

    private fun showAndroidSafSaveAsModelDialog(): Boolean {
        val bridge = AndroidSaf.bridge ?: return false
        val suggestedName = sanitizeModelFileName(
            if (::modelFile.isInitialized) modelFile.name else "octodraw.octd"
        )
        bridge.createDocument(suggestedName) { uri, displayName ->
            if (uri.isNullOrBlank()) {
                statusModel.message = "Save As cancelled."
                return@createDocument
            }
            val bytes = try {
                val temp = File.createTempFile("octodraw-save-", ".octd", androidModelRootDirectory())
                try {
                    ModelPersistence.saveSnapshot(temp, snapshotForPersistence(includeUndoHistory = true))
                    temp.readBytes()
                } finally {
                    temp.delete()
                }
            } catch (t: Throwable) {
                statusModel.message = "Save As failed: ${t.message ?: t.javaClass.simpleName}"
                return@createDocument
            }
            if (!bridge.writeBytes(uri, bytes)) {
                statusModel.message = "Save As failed: cannot write selected destination."
                return@createDocument
            }
            val localName = sanitizeModelFileName(displayName ?: suggestedName)
            val localTarget = File(androidModelRootDirectory(), localName).absoluteFile
            try {
                localTarget.parentFile?.mkdirs()
                localTarget.writeBytes(bytes)
            } catch (_: Throwable) {
                // local mirror is best-effort; destination write already succeeded
            }
            modelFile = localTarget
            updateWindowTitle()
            if (::pluginHost.isInitialized) {
                pluginHost.dispatchSave()
            }
            statusModel.message = "Saved ${displayName ?: localTarget.name}"
        }
        return true
    }

    private fun androidModelRootDirectory(): File {
        val currentParent = if (::modelFile.isInitialized) modelFile.parentFile else null
        if (currentParent != null && currentParent.exists()) {
            return currentParent.absoluteFile
        }
        if (::installDir.isInitialized && installDir.exists()) {
            return installDir.absoluteFile
        }
        return try {
            Gdx.files.local("").file().absoluteFile
        } catch (_: Throwable) {
            File(".").absoluteFile
        }
    }

    private fun isWithinDirectory(base: File, candidate: File): Boolean {
        return try {
            val basePath = base.canonicalFile.toPath()
            val candidatePath = candidate.canonicalFile.toPath()
            candidatePath.startsWith(basePath)
        } catch (_: Throwable) {
            false
        }
    }

    private fun showAndroidOpenModelDialog() {
        val stage = uiOverlay.stage
        val rootDir = androidModelRootDirectory()
        var currentDir = rootDir
        val entries = mutableListOf<File>()

        val dialog = com.kotcrab.vis.ui.widget.VisWindow("Open Octodraw Model", true).apply {
            isModal = true
            isMovable = true
            isResizable = true
            setResizeBorder(14)
            setKeepWithinParent(true)
            setSize(560f, 460f)
        }
        val pathLabel = com.kotcrab.vis.ui.widget.VisLabel("")
        pathLabel.setWrap(true)
        val list = com.kotcrab.vis.ui.widget.VisList<String>()
        val statusLabel = com.kotcrab.vis.ui.widget.VisLabel("")
        val nameLabel = com.kotcrab.vis.ui.widget.VisLabel("App storage model files (.octd/.k3d)")

        fun refreshListing() {
            if (!currentDir.exists()) {
                currentDir.mkdirs()
            }
            pathLabel.setText(currentDir.absolutePath)
            entries.clear()
            val listed = currentDir.listFiles()?.toList().orEmpty()
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            listed.forEach { file ->
                val ext = file.extension.lowercase()
                if (file.isDirectory || ext == "octd" || ext == "k3d") {
                    entries += file
                }
            }
            val names = entries.map { file ->
                if (file.isDirectory) "[${file.name}]" else file.name
            }
            if (names.isEmpty()) {
                list.setItems("(No files)")
                list.selectedIndex = 0
            } else {
                list.setItems(*names.toTypedArray())
                list.selectedIndex = 0
            }
            statusLabel.setText("")
        }

        fun selectedEntry(): File? {
            if (entries.isEmpty()) return null
            val index = list.selectedIndex
            if (index < 0 || index >= entries.size) return null
            return entries[index]
        }

        fun openSelected() {
            val entry = selectedEntry()
            if (entry == null) {
                statusLabel.setText("No model file selected.")
                return
            }
            if (entry.isDirectory) {
                currentDir = entry
                refreshListing()
                return
            }
            if (::scene.isInitialized && ::camera.isInitialized && ::modelFile.isInitialized) {
                saveModel()
            }
            modelFile = entry.absoluteFile
            loadModel()
            updateWindowTitle()
            statusModel.message = "Opening ${entry.name}..."
            dialog.remove()
        }

        val upButton = com.kotcrab.vis.ui.widget.VisTextButton("Up")
        upButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                val parent = currentDir.parentFile ?: return
                if (!isWithinDirectory(rootDir, parent)) return
                currentDir = parent
                refreshListing()
            }
        })
        val homeButton = com.kotcrab.vis.ui.widget.VisTextButton("Home")
        homeButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                currentDir = rootDir
                refreshListing()
            }
        })
        val openButton = com.kotcrab.vis.ui.widget.VisTextButton("Open")
        openButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                openSelected()
            }
        })
        val cancelButton = com.kotcrab.vis.ui.widget.VisTextButton("Cancel")
        cancelButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                dialog.remove()
            }
        })

        list.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                if (tapCount >= 2) {
                    openSelected()
                }
            }
        })

        val listScroll = com.kotcrab.vis.ui.widget.VisScrollPane(list).apply {
            setFadeScrollBars(false)
            setScrollingDisabled(true, false)
        }
        val toolbar = com.kotcrab.vis.ui.widget.VisTable().apply {
            defaults().pad(4f)
            add(upButton)
            add(homeButton)
        }
        val footer = com.kotcrab.vis.ui.widget.VisTable().apply {
            defaults().pad(4f)
            add(cancelButton)
            add(openButton)
        }
        dialog.defaults().pad(4f).growX()
        dialog.add(nameLabel).left().row()
        dialog.add(toolbar).left().row()
        dialog.add(pathLabel).growX().row()
        dialog.add(listScroll).grow().row()
        dialog.add(statusLabel).left().row()
        dialog.add(footer).right()

        refreshListing()
        stage.addActor(dialog)
        dialog.pack()
        dialog.centerWindow()
        dialog.toFront()
        dialog.fadeIn()
    }

    private fun showAndroidSaveAsModelDialog() {
        val stage = uiOverlay.stage
        val rootDir = androidModelRootDirectory()
        var currentDir = rootDir
        val entries = mutableListOf<File>()

        val dialog = com.kotcrab.vis.ui.widget.VisWindow("Save Octodraw Model As", true).apply {
            isModal = true
            isMovable = true
            isResizable = true
            setResizeBorder(14)
            setKeepWithinParent(true)
            setSize(560f, 500f)
        }
        val pathLabel = com.kotcrab.vis.ui.widget.VisLabel("")
        pathLabel.setWrap(true)
        val list = com.kotcrab.vis.ui.widget.VisList<String>()
        val statusLabel = com.kotcrab.vis.ui.widget.VisLabel("")
        val nameField = com.github.alfu32.sketch.ui.AppTextField(
            if (::modelFile.isInitialized) modelFile.name else "octodraw.octd"
        )

        fun refreshListing() {
            if (!currentDir.exists()) {
                currentDir.mkdirs()
            }
            pathLabel.setText(currentDir.absolutePath)
            entries.clear()
            val listed = currentDir.listFiles()?.toList().orEmpty()
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            listed.forEach { file ->
                val ext = file.extension.lowercase()
                if (file.isDirectory || ext == "octd" || ext == "k3d") {
                    entries += file
                }
            }
            val names = entries.map { file ->
                if (file.isDirectory) "[${file.name}]" else file.name
            }
            if (names.isEmpty()) {
                list.setItems("(No files)")
                list.selectedIndex = 0
            } else {
                list.setItems(*names.toTypedArray())
                list.selectedIndex = 0
            }
            statusLabel.setText("")
        }

        fun selectedEntry(): File? {
            if (entries.isEmpty()) return null
            val index = list.selectedIndex
            if (index < 0 || index >= entries.size) return null
            return entries[index]
        }

        fun saveCurrent() {
            val rawName = nameField.text?.trim().orEmpty()
            if (rawName.isBlank()) {
                statusLabel.setText("Please enter a file name.")
                return
            }
            val baseFile = File(currentDir, rawName)
            val target = if (baseFile.extension.lowercase() == "octd" || baseFile.extension.lowercase() == "k3d") {
                baseFile
            } else {
                File(currentDir, "$rawName.octd")
            }
            target.parentFile?.mkdirs()
            modelFile = target.absoluteFile
            saveModel()
            updateWindowTitle()
            statusModel.message = "Saved ${target.name}"
            dialog.remove()
        }

        val upButton = com.kotcrab.vis.ui.widget.VisTextButton("Up")
        upButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                val parent = currentDir.parentFile ?: return
                if (!isWithinDirectory(rootDir, parent)) return
                currentDir = parent
                refreshListing()
            }
        })
        val homeButton = com.kotcrab.vis.ui.widget.VisTextButton("Home")
        homeButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                currentDir = rootDir
                refreshListing()
            }
        })
        val saveButton = com.kotcrab.vis.ui.widget.VisTextButton("Save")
        saveButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                saveCurrent()
            }
        })
        val cancelButton = com.kotcrab.vis.ui.widget.VisTextButton("Cancel")
        cancelButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                dialog.remove()
            }
        })

        list.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                val entry = selectedEntry() ?: return
                if (entry.isDirectory) {
                    if (tapCount >= 2) {
                        currentDir = entry
                        refreshListing()
                    }
                } else {
                    nameField.text = entry.name
                    if (tapCount >= 2) {
                        saveCurrent()
                    }
                }
            }
        })

        val listScroll = com.kotcrab.vis.ui.widget.VisScrollPane(list).apply {
            setFadeScrollBars(false)
            setScrollingDisabled(true, false)
        }
        val toolbar = com.kotcrab.vis.ui.widget.VisTable().apply {
            defaults().pad(4f)
            add(upButton)
            add(homeButton)
        }
        val nameRow = com.kotcrab.vis.ui.widget.VisTable().apply {
            defaults().pad(4f)
            add(com.kotcrab.vis.ui.widget.VisLabel("File")).left()
            add(nameField).growX()
        }
        val footer = com.kotcrab.vis.ui.widget.VisTable().apply {
            defaults().pad(4f)
            add(cancelButton)
            add(saveButton)
        }

        dialog.defaults().pad(4f).growX()
        dialog.add(com.kotcrab.vis.ui.widget.VisLabel("App storage model files (.octd/.k3d)")).left().row()
        dialog.add(toolbar).left().row()
        dialog.add(pathLabel).growX().row()
        dialog.add(listScroll).grow().row()
        dialog.add(nameRow).growX().row()
        dialog.add(statusLabel).left().row()
        dialog.add(footer).right()

        refreshListing()
        stage.addActor(dialog)
        dialog.pack()
        dialog.centerWindow()
        dialog.toFront()
        dialog.fadeIn()
        stage.keyboardFocus = nameField
    }

    private fun showIfcExportDialog() {
        if (BuildFlags.WEB_BUILD) {
            statusModel.message = "IFC export dialog is not available in web runtime yet."
            return
        }
        val statusBefore = statusModel.message
        val requested = showDesktopFileDialog("Export IFC (Model)", FileDialog.SAVE, "model.ifc")
        if (requested == null) {
            if (statusModel.message == statusBefore) {
                statusModel.message = "IFC export cancelled."
            }
            return
        }
        exportIfcModel(ensureFileExtension(requested.absoluteFile, setOf("ifc"), "ifc"))
    }

    private data class MeshExportOption(
        val label: String,
        val extensions: List<String>,
        val defaultExtension: String,
        val format: MeshIo.ExportFormat? = null,
        val ifcExport: Boolean = false,
        val pngScreenshotExport: Boolean = false,
        val unsupportedReason: String? = null
    )

    private val meshExportOptions = listOf(
        MeshExportOption(
            label = "OBJ (*.obj)",
            extensions = listOf("obj"),
            defaultExtension = "obj",
            format = MeshIo.ExportFormat.OBJ
        ),
        MeshExportOption(
            label = "STL Binary (*.stl, *.stlb)",
            extensions = listOf("stl", "stlb"),
            defaultExtension = "stl",
            format = MeshIo.ExportFormat.STL_BINARY
        ),
        MeshExportOption(
            label = "STL ASCII (*.stla)",
            extensions = listOf("stla"),
            defaultExtension = "stla",
            format = MeshIo.ExportFormat.STL_ASCII
        ),
        MeshExportOption(
            label = "glTF 2.0 (*.gltf)",
            extensions = listOf("gltf"),
            defaultExtension = "gltf",
            format = MeshIo.ExportFormat.GLTF
        ),
        MeshExportOption(
            label = "GLB (*.glb)",
            extensions = listOf("glb"),
            defaultExtension = "glb",
            format = MeshIo.ExportFormat.GLB
        ),
        MeshExportOption(
            label = "DAE/Collada (*.dae)",
            extensions = listOf("dae"),
            defaultExtension = "dae",
            format = MeshIo.ExportFormat.DAE
        ),
        MeshExportOption(
            label = "DXF 3DFACE (*.dxf)",
            extensions = listOf("dxf"),
            defaultExtension = "dxf",
            format = MeshIo.ExportFormat.DXF
        ),
        MeshExportOption(
            label = "3MF (*.3mf)",
            extensions = listOf("3mf"),
            defaultExtension = "3mf",
            format = MeshIo.ExportFormat.THREE_MF
        ),
        MeshExportOption(
            label = "AMF (*.amf)",
            extensions = listOf("amf"),
            defaultExtension = "amf",
            format = MeshIo.ExportFormat.AMF
        ),
        MeshExportOption(
            label = "IFC (*.ifc)",
            extensions = listOf("ifc"),
            defaultExtension = "ifc",
            ifcExport = true
        ),
        MeshExportOption(
            label = "FBX ASCII (*.fbx)",
            extensions = listOf("fbx"),
            defaultExtension = "fbx",
            format = MeshIo.ExportFormat.FBX
        )
    )

    private val pngExportOption = MeshExportOption(
        label = "PNG Screenshot (*.png)",
        extensions = listOf("png"),
        defaultExtension = "png",
        pngScreenshotExport = true
    )

    private val exportOptions = meshExportOptions + pngExportOption

    private fun showImportMeshDialog() {
        if (BuildFlags.WEB_BUILD) {
            showWebImportMeshDialog()
            return
        }
        if (isAndroidRuntime()) {
            if (showAndroidSafImportMeshDialog()) {
                return
            }
        }
        val option = chooseDesktopFileDialogOption(
            title = "Import Mesh",
            message = "Choose the mesh format to show in the system dialog.",
            options = listOf(
                DesktopFileDialogOption(
                    "All supported (*.obj, *.stl, *.stla, *.stlb, *.gltf, *.glb, *.dae, *.dxf, *.3mf, *.amf, *.ifc, *.fbx)",
                    meshExportOptions.flatMap { it.extensions }.toSet(),
                    "obj"
                )
            ) + meshExportOptions.map { option ->
                DesktopFileDialogOption(option.label, option.extensions.toSet(), option.defaultExtension)
            }
        ) ?: run {
            statusModel.message = "Import canceled."
            return
        }
        val statusBefore = statusModel.message
        val requested = showDesktopFileDialog(
            title = "Import Mesh - ${option.label}",
            mode = FileDialog.LOAD,
            allowedExtensions = option.extensions
        )
        if (requested == null) {
            if (statusModel.message == statusBefore) {
                statusModel.message = "Import canceled."
            }
            return
        }
        if (!requested.exists()) {
            statusModel.message = "Import failed: selected file does not exist."
            return
        }
        importMeshPayload(requested.name, requested.readBytes(), sourcePath = requested.absolutePath)
    }

    private fun showExportMeshDialog() {
        if (BuildFlags.WEB_BUILD) {
            showWebExportMeshDialog()
            return
        }
        if (isAndroidRuntime()) {
            if (showAndroidSafExportMeshDialog()) {
                return
            }
        }
        val desktopOptions = exportOptions.map { exportOption ->
            DesktopFileDialogOption(
                exportOption.label,
                exportOption.extensions.toSet(),
                exportOption.defaultExtension
            )
        }
        val selectedOption = chooseDesktopFileDialogOption(
            title = "Export",
            message = "Choose the export format before opening the system dialog.",
            options = desktopOptions
        ) ?: run {
            statusModel.message = "Export canceled."
            return
        }
        val option = exportOptions.firstOrNull { it.label == selectedOption.label } ?: run {
            statusModel.message = "Export failed: unknown export format."
            return
        }
        val statusBefore = statusModel.message
        val requested = showDesktopFileDialog(
            title = "Export - ${option.label}",
            mode = FileDialog.SAVE,
            defaultFileName = replaceFileExtension(
                if (::modelFile.isInitialized) "${modelFile.nameWithoutExtension}.obj" else "mesh.obj",
                option.defaultExtension
            )
        )
        if (requested == null) {
            if (statusModel.message == statusBefore) {
                statusModel.message = "Export canceled."
            }
            return
        }
        if (option.pngScreenshotExport) {
            schedulePngExportToFile(requested.absoluteFile)
        } else {
            exportMeshToFile(requested.absoluteFile)
        }
    }

    private fun webMeshAcceptFilter(): String {
        return meshExportOptions
            .flatMap { it.extensions }
            .distinct()
            .joinToString(",") { ".${it.lowercase()}" }
    }

    private fun webMimeTypeForMeshOption(option: MeshExportOption): String {
        return when {
            option.pngScreenshotExport -> "image/png"
            option.ifcExport -> "application/octet-stream"
            option.defaultExtension == "obj" -> "text/plain"
            option.defaultExtension == "gltf" -> "model/gltf+json"
            option.defaultExtension == "dae" -> "model/vnd.collada+xml"
            option.defaultExtension == "dxf" -> "application/dxf"
            option.defaultExtension == "amf" -> "application/xml"
            option.defaultExtension == "fbx" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun showWebImportMeshDialog() {
        val bridge = WebRuntime.bridge
        if (bridge == null) {
            statusModel.message = "Web import is unavailable in this build."
            return
        }
        bridge.openBinaryDocument(webMeshAcceptFilter()) { displayName, base64Content ->
            if (base64Content.isNullOrBlank()) {
                statusModel.message = "Import canceled."
                return@openBinaryDocument
            }
            val bytes = try {
                java.util.Base64.getDecoder().decode(base64Content)
            } catch (t: Throwable) {
                statusModel.message = "Import failed: ${t.message ?: t.javaClass.simpleName}"
                return@openBinaryDocument
            }
            importMeshPayload(displayName ?: "imported-mesh", bytes)
        }
    }

    private fun showWebExportMeshDialog() {
        val bridge = WebRuntime.bridge
        if (bridge == null) {
            statusModel.message = "Web export is unavailable in this build."
            return
        }
        val webOptions = exportOptions.filterNot { it.ifcExport }
        showMeshExportOptionDialog(webOptions) { option ->
            val suggestedBase = if (::modelFile.isInitialized) modelFile.nameWithoutExtension else "mesh"
            val suggestedName = "$suggestedBase.${option.defaultExtension}"
            if (option.pngScreenshotExport) {
                schedulePngExportToWeb(suggestedName, bridge)
            } else {
                val payload = createWebExportPayloadForOption(option) ?: return@showMeshExportOptionDialog
                val base64 = java.util.Base64.getEncoder().encodeToString(payload.bytes)
                bridge.saveBinaryDocument(
                    suggestedName,
                    base64,
                    webMimeTypeForMeshOption(option)
                ) { success, message ->
                    if (success) {
                        statusModel.message = payload.statusMessage(suggestedName)
                    } else {
                        statusModel.message = message ?: "Export failed."
                    }
                }
            }
        }
    }

    private fun showAndroidSafImportMeshDialog(): Boolean {
        val bridge = AndroidSaf.bridge ?: return false
        bridge.openDocument { uri, displayName ->
            if (uri.isNullOrBlank()) {
                statusModel.message = "Import canceled."
                return@openDocument
            }
            val bytes = bridge.readBytes(uri)
            if (bytes == null || bytes.isEmpty()) {
                statusModel.message = "Import failed: cannot read selected file."
                return@openDocument
            }
            val nameHint = displayName ?: uri.substringAfterLast('/').substringBefore('?')
            importMeshPayload(nameHint, bytes)
        }
        return true
    }

    private fun showAndroidSafExportMeshDialog(): Boolean {
        val bridge = AndroidSaf.bridge ?: return false
        showMeshExportOptionDialog(exportOptions) { option ->
            val suggestedBase = if (::modelFile.isInitialized) modelFile.nameWithoutExtension else "mesh"
            val suggestedName = "$suggestedBase.${option.defaultExtension}"
            bridge.createDocument(suggestedName) { uri, displayName ->
                if (uri.isNullOrBlank()) {
                    statusModel.message = "Export canceled."
                    return@createDocument
                }
                val targetName = displayName ?: suggestedName
                if (option.pngScreenshotExport) {
                    schedulePngExportToAndroid(targetName, uri, bridge)
                    return@createDocument
                }
                val extension = targetName.substringAfterLast('.', "").lowercase()
                val optionForExtension = meshExportOptionForExtension(extension) ?: option
                val payload = createExportPayloadForOption(optionForExtension) ?: return@createDocument
                if (!bridge.writeBytes(uri, payload.bytes)) {
                    statusModel.message = "Export failed: cannot write selected destination."
                    return@createDocument
                }
                val localMirror = File(androidModelRootDirectory(), ensureMeshExportFileName(targetName, option.defaultExtension))
                try {
                    localMirror.parentFile?.mkdirs()
                    localMirror.writeBytes(payload.bytes)
                } catch (_: Throwable) {
                    // Best effort local mirror for convenience.
                }
                statusModel.message = payload.statusMessage(targetName)
            }
        }
        return true
    }

    private fun showMeshExportOptionDialog(
        options: List<MeshExportOption> = exportOptions,
        onSelected: (MeshExportOption) -> Unit
    ) {
        val stage = uiOverlay.stage
        val dialog = com.kotcrab.vis.ui.widget.VisWindow("Export Format", true).apply {
            isModal = true
            isMovable = true
            isResizable = false
            setKeepWithinParent(true)
        }
        val content = com.kotcrab.vis.ui.widget.VisTable()
        content.defaults().pad(4f).growX()
        content.add(com.kotcrab.vis.ui.widget.VisLabel("Choose format (extension drives export type):")).left().row()
        options.forEach { option ->
            val button = com.kotcrab.vis.ui.widget.VisTextButton(option.label)
            button.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                override fun clicked(
                    event: com.badlogic.gdx.scenes.scene2d.InputEvent?,
                    x: Float,
                    y: Float
                ) {
                    dialog.remove()
                    onSelected(option)
                }
            })
            content.add(button).left().row()
        }
        val cancelButton = com.kotcrab.vis.ui.widget.VisTextButton("Cancel")
        cancelButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(
                event: com.badlogic.gdx.scenes.scene2d.InputEvent?,
                x: Float,
                y: Float
            ) {
                dialog.remove()
            }
        })
        content.add(cancelButton).right().padTop(6f).row()
        dialog.add(content).pad(6f)
        dialog.pack()
        stage.addActor(dialog)
        dialog.centerWindow()
        dialog.toFront()
        dialog.fadeIn()
    }

    private data class MeshExportPayload(
        val bytes: ByteArray,
        val triangleCount: Int,
        val scopeLabel: String,
        val ifcProductCount: Int? = null
    ) {
        fun statusMessage(target: String): String {
            return if (ifcProductCount != null) {
                "Exported IFC to $target ($ifcProductCount products, $scopeLabel)."
            } else {
                "Exported $triangleCount triangle(s) to $target ($scopeLabel)."
            }
        }
    }

    private fun exportMeshToFile(requestedFile: File) {
        val normalizedTarget = if (requestedFile.extension.isBlank()) {
            File(requestedFile.parentFile, "${requestedFile.name}.obj")
        } else {
            requestedFile
        }
        val target = normalizedTarget.absoluteFile
        val extension = target.extension.lowercase()
        val option = meshExportOptionForExtension(extension)
        if (option == null) {
            statusModel.message = "Export failed: unsupported extension '${target.extension}'."
            return
        }
        if (option.pngScreenshotExport) {
            schedulePngExportToFile(target)
            return
        }
        val export = createExportPayloadForOption(option) ?: return
        try {
            target.parentFile?.mkdirs()
            target.writeBytes(export.bytes)
            statusModel.message = export.statusMessage(target.absolutePath)
        } catch (t: Throwable) {
            statusModel.message = "Export failed: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    private fun meshExportOptionForExtension(extension: String): MeshExportOption? {
        val ext = extension.lowercase()
        return exportOptions.firstOrNull { option ->
            option.extensions.any { candidate -> candidate.equals(ext, ignoreCase = true) }
        }
    }

    private fun createExportPayloadForOption(option: MeshExportOption): MeshExportPayload? {
        option.unsupportedReason?.let { reason ->
            statusModel.message = reason
            return null
        }
        if (option.ifcExport) {
            return exportIfcPayloadFromSelection()
        }
        val format = option.format ?: return null
        return exportMeshBytes(format)
    }

    private fun exportMeshBytes(format: MeshIo.ExportFormat): MeshExportPayload? {
        val selected = collectSelectedWorldTrianglesForExport()
        val triangles = if (selected.isNotEmpty()) selected else collectAllWorldTrianglesForExport()
        if (triangles.isEmpty()) {
            statusModel.message = "Export failed: no mesh triangles in the model."
            return null
        }
        val bytes = try {
            MeshIo.exportTriangles(triangles, format, meshExportSettings(format))
        } catch (t: Throwable) {
            statusModel.message = "Export failed: ${t.message ?: t.javaClass.simpleName}"
            return null
        }
        val scopeLabel = if (selected.isNotEmpty()) "selection" else "full model"
        return MeshExportPayload(bytes, triangles.size, scopeLabel)
    }

    private fun meshExportSettings(format: MeshIo.ExportFormat): MeshIo.ExportSettings {
        return when (format) {
            MeshIo.ExportFormat.THREE_MF -> {
                MeshIo.ExportSettings(
                    threeMf = MeshIo.ThreeMfExportSettings(
                        unit = MeshIo.ThreeMfUnit.MILLIMETER,
                        coordinateScale = threeMfCoordinateScale()
                    )
                )
            }

            else -> MeshIo.ExportSettings()
        }
    }

    private fun threeMfCoordinateScale(): Float {
        val unitScale = modelUnit.size.coerceAtLeast(1e-6f)
        val unitNameScale = resolveUnitToMillimeterScale(modelUnit.name) ?: 1f
        return unitScale * unitNameScale
    }

    private fun resolveUnitToMillimeterScale(name: String): Float? {
        return when (name.trim().lowercase(Locale.US)) {
            "", "unit", "units" -> null
            "micron", "microns", "um", "μm", "µm" -> 0.001f
            "mm", "millimeter", "millimeters", "millimetre", "millimetres" -> 1f
            "cm", "centimeter", "centimeters", "centimetre", "centimetres" -> 10f
            "m", "meter", "meters", "metre", "metres" -> 1000f
            "in", "inch", "inches", "\"" -> 25.4f
            "ft", "foot", "feet", "'" -> 304.8f
            else -> null
        }
    }

    private fun createWebExportPayloadForOption(option: MeshExportOption): MeshExportPayload? {
        option.unsupportedReason?.let { reason ->
            statusModel.message = reason
            return null
        }
        if (option.ifcExport) {
            statusModel.message = "IFC export is not available in the web runtime yet."
            return null
        }
        val format = option.format ?: return null
        return exportMeshBytes(format)
    }

    private fun collectAllWorldTrianglesForExport(): List<MeshIo.Triangle> {
        val out = ArrayList<MeshIo.Triangle>(4096)
        scene.collectWorldTriangles { a, b, c, _, _ ->
            out.add(MeshIo.Triangle(Vector3(a), Vector3(b), Vector3(c)))
        }
        return out
    }

    private fun collectSelectedWorldTrianglesForExport(): List<MeshIo.Triangle> {
        val out = ArrayList<MeshIo.Triangle>(2048)
        val seen = HashSet<String>()
        val selectedGroups = scene.selectedGroups().toSet()
        val selectedArchitecture = scene.selectedArchitectureElements(scene.root)
        val selectedHvac = scene.selectedHvacElements(scene.root)

        scene.root.faceStore.getTriangles().forEach { tri ->
            val selectedByFace = scene.root.faceStore.isSelected(tri)
            val selectedByArchitecture = scene.isGeneratedArchitectureTriangle(tri) &&
                selectedArchitecture.contains(scene.generatedArchitectureOwner(tri))
            val selectedByHvac = scene.isGeneratedHvacTriangle(tri) &&
                selectedHvac.contains(scene.generatedHvacOwner(tri))
            if (!selectedByFace && !selectedByArchitecture && !selectedByHvac) {
                return@forEach
            }
            val key = "root:${tri.id}"
            if (!seen.add(key)) {
                return@forEach
            }
            out.add(MeshIo.Triangle(Vector3(tri.a), Vector3(tri.b), Vector3(tri.c)))
        }

        scene.walkGroups(scene.root) { group ->
            val includeAll = selectedGroups.contains(group)
            val source = if (includeAll) group.faceStore.getTriangles() else group.faceStore.getSelected()
            source.forEach { tri ->
                val key = "${group.id}:${tri.id}"
                if (!seen.add(key)) {
                    return@forEach
                }
                out.add(
                    MeshIo.Triangle(
                        group.toWorld(tri.a),
                        group.toWorld(tri.b),
                        group.toWorld(tri.c)
                    )
                )
            }
        }
        return out
    }

    private fun exportIfcPayloadFromSelection(): MeshExportPayload? {
        val selected = collectSelectedWorldTrianglesForExport()
        val triangles = if (selected.isNotEmpty()) selected else collectAllWorldTrianglesForExport()
        if (triangles.isEmpty()) {
            statusModel.message = "IFC export failed: no mesh triangles in the model."
            return null
        }
        val exportScene = GroupScene(Color(scene.defaultFaceColor))
        exportScene.root.faceStore.withChangeSuppressed {
            triangles.forEach { tri ->
                exportScene.root.faceStore.addTriangle(Vector3(tri.a), Vector3(tri.b), Vector3(tri.c), scene.defaultFaceColor)
            }
        }
        exportScene.root.faceStore.notifyExternalChange()
        val tmp = kotlin.runCatching {
            File.createTempFile("octodraw-ifc-export-", ".ifc")
        }.getOrNull() ?: run {
            statusModel.message = "IFC export failed: cannot allocate temporary file."
            return null
        }
        val unitScale = modelUnit.size.coerceAtLeast(1e-6f)
        return try {
            val report = IfcExporter.export(exportScene, tmp, unitScale)
            val bytes = tmp.readBytes()
            MeshExportPayload(
                bytes = bytes,
                triangleCount = triangles.size,
                scopeLabel = if (selected.isNotEmpty()) "selection" else "full model",
                ifcProductCount = report.productCount
            )
        } catch (t: Throwable) {
            statusModel.message = "IFC export failed: ${t.message ?: t.javaClass.simpleName}"
            null
        } finally {
            tmp.delete()
        }
    }

    private fun importMeshPayload(nameHint: String?, bytes: ByteArray, sourcePath: String? = null) {
        val extension = nameHint?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val resolvedFormat = MeshIo.importFormatForExtension(extension)
        val importSettings = meshImportSettings()
        val triangles = when {
            resolvedFormat != null -> MeshIo.importTriangles(bytes, resolvedFormat, importSettings)
            else -> {
                // Fallback when SAF providers omit extension metadata.
                val obj = MeshIo.importTriangles(bytes, MeshIo.ImportFormat.OBJ, importSettings)
                val stl = MeshIo.importTriangles(bytes, MeshIo.ImportFormat.STL_AUTO, importSettings)
                val glb = MeshIo.importTriangles(bytes, MeshIo.ImportFormat.GLB, importSettings)
                val gltf = MeshIo.importTriangles(bytes, MeshIo.ImportFormat.GLTF, importSettings)
                val dae = MeshIo.importTriangles(bytes, MeshIo.ImportFormat.DAE, importSettings)
                val dxf = MeshIo.importTriangles(bytes, MeshIo.ImportFormat.DXF, importSettings)
                val threemf = MeshIo.importTriangles(bytes, MeshIo.ImportFormat.THREE_MF, importSettings)
                val amf = MeshIo.importTriangles(bytes, MeshIo.ImportFormat.AMF, importSettings)
                val fbx = MeshIo.importTriangles(bytes, MeshIo.ImportFormat.FBX, importSettings)
                val ifc = MeshIo.importTriangles(bytes, MeshIo.ImportFormat.IFC, importSettings)
                listOf(obj, stl, glb, gltf, dae, dxf, threemf, amf, fbx, ifc).maxByOrNull { it.size }.orEmpty()
            }
        }
        if (triangles.isEmpty()) {
            statusModel.message = "Import failed: no triangles parsed from ${nameHint ?: "selected file"}."
            return
        }
        val prototypeName = importedPrototypeName(nameHint)
        val meshTriangles = triangles.map { tri ->
            GroupScene.MeshTriangle(
                a = Vector3(tri.a),
                b = Vector3(tri.b),
                c = Vector3(tri.c),
                color = Color(scene.defaultFaceColor)
            )
        }
        val prototype = scene.createMeshPrototype(prototypeName, meshTriangles, includeEdges = true)
        if (prototype == null) {
            statusModel.message = "Import failed: could not create object prototype."
            return
        }
        startObjectPlacement(prototype.id)
        statusModel.message = "Imported ${triangles.size} triangle(s) as '$prototypeName'. Click to place object."
    }

    private fun meshImportSettings(): MeshIo.ImportSettings {
        val millimetersPerModelUnit = threeMfCoordinateScale().coerceAtLeast(1e-9f)
        return MeshIo.ImportSettings(
            threeMf = MeshIo.ThreeMfImportSettings(
                modelUnitsPerMillimeter = 1f / millimetersPerModelUnit
            )
        )
    }

    private fun importedPrototypeName(nameHint: String?): String {
        val raw = nameHint?.substringAfterLast('/')?.substringAfterLast('\\') ?: "Imported Mesh"
        val base = raw.substringBeforeLast('.', raw).trim()
        val cleaned = base.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifBlank { "Imported Mesh" }
        return cleaned
    }

    private fun ensureMeshExportFileName(name: String, fallbackExtension: String): String {
        val trimmed = name.trim().ifBlank { "mesh.$fallbackExtension" }
        val ext = trimmed.substringAfterLast('.', "").lowercase()
        return if (meshExportOptionForExtension(ext) != null) {
            trimmed
        } else {
            "$trimmed.$fallbackExtension"
        }
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
        val error = writeScreenshotFile(outFile)
        return if (error == null) {
            val success = "Screenshot saved: ${outFile.absolutePath}"
            statusModel.message = success
            println(success)
            outFile
        } else {
            val failure = "Screenshot failed: ${error.message ?: error.javaClass.simpleName}"
            statusModel.message = failure
            println(failure)
            null
        }
    }

    private fun schedulePngExportToFile(requestedFile: File) {
        val target = if (requestedFile.extension.equals("png", ignoreCase = true)) {
            requestedFile.absoluteFile
        } else {
            File(requestedFile.parentFile, "${requestedFile.name}.png").absoluteFile
        }
        pendingPngExports.addLast(PendingPngExport.FileTarget(target))
        statusModel.message = "PNG export scheduled."
    }

    private fun schedulePngExportToWeb(fileName: String, bridge: WebRuntimeBridge) {
        pendingPngExports.addLast(PendingPngExport.WebTarget(fileName, bridge))
        statusModel.message = "PNG export scheduled."
    }

    private fun schedulePngExportToAndroid(fileName: String, uri: String, bridge: AndroidSafBridge) {
        pendingPngExports.addLast(PendingPngExport.AndroidTarget(fileName, uri, bridge))
        statusModel.message = "PNG export scheduled."
    }

    private fun captureScreenshotPngBytes(): ByteArray? {
        var pixmap: Pixmap? = null
        var pngWriter: PixmapIO.PNG? = null
        val output = ByteArrayOutputStream()
        return try {
            val width = Gdx.graphics.backBufferWidth.coerceAtLeast(1)
            val height = Gdx.graphics.backBufferHeight.coerceAtLeast(1)
            pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, width, height)
            pngWriter = PixmapIO.PNG((width * height * 1.5f).toInt().coerceAtLeast(1024))
            pngWriter.setFlipY(true)
            pngWriter.write(output, pixmap)
            output.toByteArray()
        } catch (_: Throwable) {
            null
        } finally {
            pngWriter?.dispose()
            pixmap?.dispose()
            output.close()
        }
    }

    private fun writeScreenshotFile(outFile: File): Throwable? {
        return try {
            val bytes = captureScreenshotPngBytes() ?: return IllegalStateException("Unable to capture screenshot buffer.")
            outFile.parentFile?.mkdirs()
            outFile.writeBytes(bytes)
            null
        } catch (t: Throwable) {
            t
        }
    }

    private fun processPendingPngExports() {
        if (pendingPngExports.isEmpty()) {
            return
        }
        val bytes = captureScreenshotPngBytes() ?: run {
            statusModel.message = "PNG export failed: unable to capture screenshot buffer."
            pendingPngExports.clear()
            return
        }
        while (pendingPngExports.isNotEmpty()) {
            when (val pending = pendingPngExports.removeFirst()) {
                is PendingPngExport.FileTarget -> {
                    try {
                        pending.target.parentFile?.mkdirs()
                        pending.target.writeBytes(bytes)
                        statusModel.message = "Exported PNG screenshot to ${pending.target.absolutePath}."
                    } catch (t: Throwable) {
                        statusModel.message = "PNG export failed: ${t.message ?: t.javaClass.simpleName}"
                    }
                }
                is PendingPngExport.WebTarget -> {
                    val base64 = Base64.getEncoder().encodeToString(bytes)
                    pending.bridge.saveBinaryDocument(
                        pending.targetName,
                        base64,
                        "image/png"
                    ) { success, message ->
                        statusModel.message = if (success) {
                            "Exported PNG screenshot to ${pending.targetName}."
                        } else {
                            message ?: "PNG export failed."
                        }
                    }
                }
                is PendingPngExport.AndroidTarget -> {
                    val success = pending.bridge.writeBytes(pending.uri, bytes)
                    statusModel.message = if (success) {
                        "Exported PNG screenshot to ${pending.targetName}."
                    } else {
                        "PNG export failed: cannot write selected destination."
                    }
                }
            }
        }
    }

    private fun captureTutorialThumbnailBase64(): String? {
        var pixmap: Pixmap? = null
        var thumbnail: Pixmap? = null
        var pngWriter: PixmapIO.PNG? = null
        val output = ByteArrayOutputStream()
        return try {
            val width = Gdx.graphics.backBufferWidth.coerceAtLeast(1)
            val height = Gdx.graphics.backBufferHeight.coerceAtLeast(1)
            pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, width, height)
            thumbnail = buildTutorialThumbnailPixmap(pixmap)
            pngWriter = PixmapIO.PNG((tutorialThumbnailWidth * tutorialThumbnailHeight * 4).coerceAtLeast(1024))
            pngWriter?.write(output, thumbnail)
            Base64.getEncoder().encodeToString(output.toByteArray())
        } catch (_: Throwable) {
            null
        } finally {
            pngWriter?.dispose()
            thumbnail?.dispose()
            pixmap?.dispose()
            output.close()
        }
    }

    private fun buildTutorialThumbnailPixmap(source: Pixmap): Pixmap {
        val canvas = Pixmap(tutorialThumbnailWidth, tutorialThumbnailHeight, Pixmap.Format.RGBA8888)
        canvas.setColor(0.12f, 0.12f, 0.14f, 1f)
        canvas.fill()
        val scale = minOf(
            tutorialThumbnailWidth.toFloat() / source.width.toFloat().coerceAtLeast(1f),
            tutorialThumbnailHeight.toFloat() / source.height.toFloat().coerceAtLeast(1f)
        )
        val scaledWidth = (source.width * scale).roundToInt().coerceIn(1, tutorialThumbnailWidth)
        val scaledHeight = (source.height * scale).roundToInt().coerceIn(1, tutorialThumbnailHeight)
        val offsetX = ((tutorialThumbnailWidth - scaledWidth) * 0.5f).roundToInt().coerceAtLeast(0)
        val offsetY = ((tutorialThumbnailHeight - scaledHeight) * 0.5f).roundToInt().coerceAtLeast(0)
        canvas.setFilter(Pixmap.Filter.BiLinear)
        canvas.drawPixmap(
            source,
            0,
            0,
            source.width,
            source.height,
            offsetX,
            offsetY,
            scaledWidth,
            scaledHeight
        )
        return canvas
    }

    private fun processPendingTutorialPreviewCaptures() {
        if (pendingTutorialPreviewCaptures.isEmpty()) {
            return
        }
        if (pendingTutorialPreviewDelayFrames > 0) {
            pendingTutorialPreviewDelayFrames -= 1
            return
        }
        flushPendingTutorialPreviewCapturesNow()
    }

    private fun flushPendingTutorialPreviewCapturesNow() {
        if (pendingTutorialPreviewCaptures.isEmpty()) {
            return
        }
        val preview = captureTutorialThumbnailBase64()
        while (pendingTutorialPreviewCaptures.isNotEmpty()) {
            val pending = pendingTutorialPreviewCaptures.removeFirst()
            tutorialManager.setRecordedStepAfterPreview(pending.stepIndex, preview)
        }
        pendingTutorialPreviewDelayFrames = 0
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
        val pointerPos = if (::toolPointer.isInitialized) toolPointer.lastPointerScreenPosition() else null
        val screenX = pointerPos?.first ?: Gdx.input.x
        val screenY = pointerPos?.second ?: Gdx.input.y
        val camera = activeCamera
        val pointerChanged = screenX != cursorStatusLastScreenX || screenY != cursorStatusLastScreenY
        val cameraChanged =
            !cursorStatusSampleInitialized ||
                camera.position.dst2(cursorStatusLastCamPos) > 1e-8f ||
                camera.direction.dst2(cursorStatusLastCamDir) > 1e-8f ||
                camera.up.dst2(cursorStatusLastCamUp) > 1e-8f
        if (cursorStatusSampleInitialized && !pointerChanged && !cameraChanged) {
            return
        }
        val now = System.currentTimeMillis()
        val minIntervalMs = if (cameraChanged) {
            if (activeCameraMode == CameraMode.ORBIT && Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) 75L else 33L
        } else {
            0L
        }
        if (cursorStatusSampleInitialized && now - cursorStatusLastSampleAtMs < minIntervalMs) {
            return
        }

        lastSnap = snapper.compute(screenX, screenY)
        val snap = lastSnap
        if (snap != null && snap.valid && snap.world != null) {
            statusModel.cursorScreenX = snap.screenX
            statusModel.cursorScreenY = snap.screenY
            statusModel.cursorWorld = String.format("%.2f, %.2f, %.2f", snap.world.x, snap.world.y, snap.world.z)
            statusModel.cursorSnapLabel = snap.type.label
        } else {
            statusModel.cursorScreenX = screenX
            statusModel.cursorScreenY = screenY
            statusModel.cursorWorld = "--"
            statusModel.cursorSnapLabel = "No hit"
        }
        cursorStatusLastScreenX = screenX
        cursorStatusLastScreenY = screenY
        cursorStatusLastCamPos.set(camera.position)
        cursorStatusLastCamDir.set(camera.direction)
        cursorStatusLastCamUp.set(camera.up)
        cursorStatusLastSampleAtMs = now
        cursorStatusSampleInitialized = true
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
        val ctrl = InputModifiers.isCtrlPressed()
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
        val terminal = TerminalControllerFactory.createInteractive()
        val tui = ConsoleTui(runtime, terminal, outputPane, history, ::openTerminal) {
            consoleThread?.shutdown()
        }
        consoleRuntime = runtime
        consoleTerminal = terminal
        consoleThread = ConsoleThread(tui, terminal).apply { start() }
    }

    private fun shouldStartConsole(): Boolean {
        if (isAndroidRuntime()) {
            return false
        }
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
        if (isAndroidRuntime()) {
            println("Terminal shell is not available on Android.")
            return
        }
        val terminal = consoleTerminal ?: return
        terminal.restore()
        println("Entering shell. Type 'exit' to return to the Octodraw console.")
        val shell = if (System.getProperty("os.name")?.startsWith("Windows", ignoreCase = true) == true) {
            System.getenv("COMSPEC") ?: "cmd.exe"
        } else {
            System.getenv("SHELL") ?: "/bin/bash"
        }
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

    // Line width rendering is disabled for consistent behavior across platforms (notably Android).
    private fun setLineWidth(width: Float) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = width
    }

    private fun applyNormalOverlayLineWidth() {
        Gdx.gl.glLineWidth(normalOverlayLineWidth)
    }

    private fun resetNormalOverlayLineWidth() {
        Gdx.gl.glLineWidth(1f)
    }

    private fun drawDraftLines() {
        val defaultColor = Color(0.2f, 0.2f, 0.2f, 1f)
        val crossSize = 0.1f
        fun shouldDrawSegment(
            group: GroupScene.GroupNode,
            segment: com.github.alfu32.sketch.model.DraftLineStore.Segment
        ): Boolean {
            if (scene.isVoxelGroup(group)) {
                return isBasicKindVisible(BasicSelectionFilterKind.VOXEL)
            }
            if (group !== scene.root) {
                return isBasicKindVisible(BasicSelectionFilterKind.EDGE)
            }
            if (scene.isGeneratedArchitectureSegment(segment)) {
                val owner = scene.generatedArchitectureOwner(segment) ?: return true
                return isArchitectureKindVisible(owner.kind)
            }
            return isBasicKindVisible(BasicSelectionFilterKind.EDGE)
        }
        scene.walkGroups(scene.root) { group ->
            val selected = group.lineStore.getSelected()
            group.lineStore.getSegments().forEach { segment ->
                if (!shouldDrawSegment(group, segment)) {
                    return@forEach
                }
                val isSelected = selected.contains(segment)
                shapeRenderer.color = if (isSelected) selectedLineColor else defaultColor
                setLineWidth(if (isSelected) selectedLineWidth else 2f)
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
            if (!shouldDrawSegment(scene.root, segment)) {
                return@forEach
            }
            val isSelected = scene.root.lineStore.isSelected(segment)
            shapeRenderer.color = if (isSelected) selectedLineColor else defaultColor
            setLineWidth(if (isSelected) selectedLineWidth else 2f)
            if (isSelected) {
                drawDashedLine(segment.start, segment.end, 0.4f, 0.25f)
            } else {
                shapeRenderer.line(segment.start, segment.end)
            }
            drawLineCross(segment.start, crossSize)
            drawLineCross(segment.end, crossSize)
        }
        scene.collectActivePrototypeWorldLines { start, end ->
            shapeRenderer.color = prototypeGuideLineColor
            setLineWidth(2f)
            drawDashedLine(start, end, 0.2f, 0.2f)
        }
        setLineWidth(2f)
    }

    private fun drawArchitectureHoleGuides() {
        if (!scene.hasArchitectureElements()) {
            return
        }
        val selectedWallIds = scene.selectedArchitectureElements(scene.root)
            .filter { it.kind == ArchitectureStore.ElementKind.WALL }
            .map { it.id }
            .toSet()
        val selectedSlabIds = scene.selectedArchitectureElements(scene.root)
            .filter { it.kind == ArchitectureStore.ElementKind.SLAB }
            .map { it.id }
            .toSet()
        if (selectedWallIds.isEmpty() && selectedSlabIds.isEmpty()) {
            return
        }
        val guides = mutableListOf<Pair<Vector3, Vector3>>()
        selectedWallIds.forEach { wallId ->
            guides += scene.architectureHoleGuideSegmentsWorld(scene.root, includeDiagonals = true, wallId = wallId)
        }
        selectedSlabIds.forEach { slabId ->
            guides += scene.architectureSlabHoleGuideSegmentsWorld(scene.root, includeDiagonals = true, slabId = slabId)
        }
        if (guides.isEmpty()) {
            return
        }
        shapeRenderer.color = architectureHoleGuideColor
        setLineWidth(3f)
        guides.forEach { (a, b) -> shapeRenderer.line(a, b) }
        setLineWidth(2f)
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
        val selectedSlabIds = scene.selectedArchitectureElements(root)
            .filter { it.kind == ArchitectureStore.ElementKind.SLAB }
            .map { it.id }
            .toSet()
        val holeMarkers = selectedWallIds.flatMap { wallId ->
            scene.architectureHoleHandleMarkersWorld(root, wallId = wallId)
        }
        val slabHoleMarkers = selectedSlabIds.flatMap { slabId ->
            scene.architectureSlabHoleHandleMarkersWorld(root, slabId = slabId)
        }
        if (wallMarkers.isEmpty() && slabMarkers.isEmpty() && frameMarkers.isEmpty() && holeMarkers.isEmpty() && slabHoleMarkers.isEmpty()) {
            return
        }
        setLineWidth(3f)
        shapeRenderer.color = architectureHoleGuideColor
        wallMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.center, marker.halfSize) }
        shapeRenderer.color = architectureSlabHotspotColor
        slabMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.center, marker.halfSize) }
        shapeRenderer.color = architectureFrameHotspotColor
        frameMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.center, marker.halfSize) }
        shapeRenderer.color = architectureHoleHotspotColor
        holeMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.world, 0.18f) }
        slabHoleMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.world, 0.18f) }
        setLineWidth(2f)
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
        setLineWidth(3f)
        shapeRenderer.color = architectureSlabHotspotColor
        slabMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.world, hotspotSize) }
        shapeRenderer.color = architectureFrameHotspotColor
        frameMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.world, hotspotSize) }
        shapeRenderer.color = architectureHoleHotspotColor
        holeMarkers.forEach { marker -> drawArchitectureHandleSquare(marker.world, hotspotSize) }
        setLineWidth(2f)
    }

    private fun drawHvacControlPoints() {
        if (!scene.hasHvacElements()) {
            return
        }
        val root = scene.root
        val selected = scene.selectedHvacElements(root)
        if (selected.isEmpty()) {
            return
        }
        val markers = scene.hvacControlHandleMarkersWorld(root)
        if (markers.isEmpty()) {
            return
        }
        setLineWidth(3f)
        markers.forEach { marker ->
            shapeRenderer.color = when (marker.kind) {
                HvacStore.ElementKind.PLUMBING -> hvacPlumbingHotspotColor
                HvacStore.ElementKind.VENTILATION -> hvacVentilationHotspotColor
            }
            drawArchitectureHandleSquare(marker.center, marker.halfSize)
        }
        setLineWidth(2f)
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
        if (!isBasicKindVisible(BasicSelectionFilterKind.DIMENSION)) {
            return
        }
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
            setLineWidth(dimensionLineWidth)
            shapeRenderer.line(dimStart, dimEnd)
            setLineWidth(extensionLineWidth)
            shapeRenderer.line(start, extensionEndA)
            shapeRenderer.line(end, extensionEndB)
            val slashDir = Vector3(dir).add(offsetDir).nor()
            val slashLen = extension * 0.6f
            shapeRenderer.color = Color(0f, 0f, 0f, 1f)
            setLineWidth(arrowLineWidth)
            shapeRenderer.line(
                Vector3(lineStart).mulAdd(slashDir, -slashLen * 0.5f),
                Vector3(lineStart).mulAdd(slashDir, slashLen * 0.5f)
            )
            shapeRenderer.line(
                Vector3(lineEnd).mulAdd(slashDir, -slashLen * 0.5f),
                Vector3(lineEnd).mulAdd(slashDir, slashLen * 0.5f)
            )
        }
        setLineWidth(2f)
    }

    private fun drawAnnotations2D() {
        spriteBatch.projectionMatrix = uiOverlay.stage.camera.combined
        textTransform.idt()
        spriteBatch.transformMatrix = textTransform
        spriteBatch.begin()
        if (isBasicKindVisible(BasicSelectionFilterKind.DIMENSION) && !isBasicKindWireframe(BasicSelectionFilterKind.DIMENSION)) {
            scene.collectWorldDimensions { start, end, offset, selected ->
                val (lineStart, lineEnd) = DimensionMath.computeOffsetLine(start, end, offset)
                drawDimensionText(lineStart, lineEnd, start, end, offset, selected)
            }
        }
        drawActiveToolMeasurementLabels()
        spriteBatch.end()

        spriteBatch.projectionMatrix = uiOverlay.stage.camera.combined
        textTransform.idt()
        spriteBatch.transformMatrix = textTransform
        spriteBatch.begin()
        if (isBasicKindVisible(BasicSelectionFilterKind.TEXT) && !isBasicKindWireframe(BasicSelectionFilterKind.TEXT)) {
            scene.collectWorldTexts { position, text, size, normal, axisU, selected, screenText, kind, _, _, _ ->
                if (kind == DraftTextStore.Kind.BITMAP && screenText) {
                    drawWorldTextScreen(text, position, size, selected)
                }
            }
        }
        spriteBatch.end()

        spriteBatch.projectionMatrix = activeCamera.combined
        textTransform.idt()
        spriteBatch.transformMatrix = textTransform
        spriteBatch.begin()
        if (isBasicKindVisible(BasicSelectionFilterKind.TEXT) && !isBasicKindWireframe(BasicSelectionFilterKind.TEXT)) {
            scene.collectWorldTexts { position, text, size, normal, axisU, selected, screenText, kind, _, _, _ ->
                if (kind == DraftTextStore.Kind.BITMAP && !screenText) {
                    drawWorldTextModel(text, position, size, normal, axisU, selected)
                }
            }
        }
        spriteBatch.end()
    }

    private fun drawHotspots2D() {
        if (!isBasicKindVisible(BasicSelectionFilterKind.HOTSPOT)) {
            return
        }
        val markers = hotspotInteractionGroups().flatMap { group ->
            scene.hotspotMarkersWorld(group)
        }
        val entityHotspots = collectArchitectureEntityHotspots2D()
        if (markers.isEmpty() && entityHotspots.isEmpty()) {
            return
        }
        shapeRenderer.projectionMatrix = uiOverlay.stage.camera.combined
        shapeRenderer.transformMatrix = Matrix4().idt()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        markers.forEach { marker ->
            val screen = activeCamera.project(Vector3(marker.world))
            if (screen.z < 0f || screen.z > 1f) {
                return@forEach
            }
            val x = screen.x
            val y = screen.y
            shapeRenderer.color = if (marker.selected) hotspotSelectedColor else marker.color
            drawHotspotShapeFilled(marker.shape, x, y, 7.5f)
            marker.referenceWorld?.let { referenceWorld ->
                val referenceScreen = activeCamera.project(Vector3(referenceWorld))
                if (referenceScreen.z < 0f || referenceScreen.z > 1f) {
                    return@let
                }
                val rx = referenceScreen.x
                val ry = referenceScreen.y
                shapeRenderer.color = if (marker.selected) hotspotSelectedColor else hotspotReferenceColor
                shapeRenderer.circle(rx, ry, 6f, 20)
            }
        }
        entityHotspots.forEach { marker ->
            val screen = activeCamera.project(Vector3(marker.world))
            if (screen.z < 0f || screen.z > 1f) {
                return@forEach
            }
            shapeRenderer.color = marker.color
            shapeRenderer.circle(screen.x, screen.y, marker.radiusPx, 20)
        }
        shapeRenderer.end()
        applyNormalOverlayLineWidth()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        markers.forEach { marker ->
            val screen = activeCamera.project(Vector3(marker.world))
            if (screen.z < 0f || screen.z > 1f) {
                return@forEach
            }
            val x = screen.x
            val y = screen.y
            shapeRenderer.color = Color(0f, 0f, 0f, 0.9f)
            drawHotspotShapeOutline(marker.shape, x, y, 8.5f)
            marker.referenceWorld?.let { referenceWorld ->
                val referenceScreen = activeCamera.project(Vector3(referenceWorld))
                if (referenceScreen.z < 0f || referenceScreen.z > 1f) {
                    return@let
                }
                val rx = referenceScreen.x
                val ry = referenceScreen.y
                shapeRenderer.color = Color(0f, 0f, 0f, 0.9f)
                shapeRenderer.circle(rx, ry, 7f, 20)
                shapeRenderer.line(x, y, rx, ry)
            }
        }
        entityHotspots.forEach { marker ->
            val screen = activeCamera.project(Vector3(marker.world))
            if (screen.z < 0f || screen.z > 1f) {
                return@forEach
            }
            shapeRenderer.color = Color(0f, 0f, 0f, 0.95f)
            shapeRenderer.circle(screen.x, screen.y, marker.radiusPx + 1f, 20)
        }
        shapeRenderer.end()
        resetNormalOverlayLineWidth()
    }

    private fun drawSelectedSegments2DOverlay() {
        data class ScreenSeg(val ax: Float, val ay: Float, val bx: Float, val by: Float)

        val screenSegments = ArrayList<ScreenSeg>()
        fun shouldDrawSelectedOverlaySegment(
            ownerGroup: GroupScene.GroupNode?,
            segment: com.github.alfu32.sketch.model.DraftLineStore.Segment
        ): Boolean {
            val group = ownerGroup ?: scene.root
            if (scene.isVoxelGroup(group)) {
                return isBasicKindVisible(BasicSelectionFilterKind.VOXEL)
            }
            if (group !== scene.root) {
                return isBasicKindVisible(BasicSelectionFilterKind.EDGE)
            }
            if (scene.isGeneratedArchitectureSegment(segment)) {
                val owner = scene.generatedArchitectureOwner(segment) ?: return true
                return isArchitectureKindVisible(owner.kind)
            }
            return isBasicKindVisible(BasicSelectionFilterKind.EDGE)
        }
        fun collectSelectedSegments(
            segments: Iterable<com.github.alfu32.sketch.model.DraftLineStore.Segment>,
            ownerGroup: GroupScene.GroupNode? = null,
            toWorld: ((Vector3) -> Vector3)? = null
        ) {
            segments.forEach { segment ->
                if (!shouldDrawSelectedOverlaySegment(ownerGroup, segment)) {
                    return@forEach
                }
                val startWorld = if (toWorld != null) toWorld(segment.start) else Vector3(segment.start)
                val endWorld = if (toWorld != null) toWorld(segment.end) else Vector3(segment.end)
                val a = activeCamera.project(startWorld)
                val b = activeCamera.project(endWorld)
                if (!a.x.isFinite() || !a.y.isFinite() || !a.z.isFinite()) return@forEach
                if (!b.x.isFinite() || !b.y.isFinite() || !b.z.isFinite()) return@forEach
                if (a.z !in 0f..1f || b.z !in 0f..1f) return@forEach
                screenSegments.add(ScreenSeg(a.x, a.y, b.x, b.y))
            }
        }

        collectSelectedSegments(scene.root.lineStore.getSelected(), ownerGroup = scene.root)
        scene.walkGroups(scene.root) { group ->
            collectSelectedSegments(group.lineStore.getSelected(), ownerGroup = group) { local -> group.toWorld(local) }
        }
        if (screenSegments.isEmpty()) {
            return
        }

        shapeRenderer.projectionMatrix = uiOverlay.stage.camera.combined
        shapeRenderer.transformMatrix = Matrix4().idt()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        screenSegments.forEach { seg ->
            shapeRenderer.color = selectedLineColor
            drawDashedRectLine2D(seg.ax, seg.ay, seg.bx, seg.by, width = 2f, dash = 5f, gap = 3f)
            shapeRenderer.color = selectedLineOverlayPointColor
            shapeRenderer.circle(seg.ax, seg.ay, 3.5f, 16)
            shapeRenderer.circle(seg.bx, seg.by, 3.5f, 16)
        }
        shapeRenderer.end()
    }

    private fun drawDashedRectLine2D(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        width: Float,
        dash: Float,
        gap: Float
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length <= 0.001f) {
            return
        }
        val nx = dx / length
        val ny = dy / length
        val step = dash + gap
        var dist = 0f
        while (dist < length) {
            val segLen = kotlin.math.min(dash, length - dist)
            val sx = x1 + nx * dist
            val sy = y1 + ny * dist
            val ex = x1 + nx * (dist + segLen)
            val ey = y1 + ny * (dist + segLen)
            shapeRenderer.rectLine(sx, sy, ex, ey, width)
            dist += step
        }
    }

    private fun drawHotspotShapeFilled(
        shape: com.github.alfu32.sketch.model.HotspotStore.ShapeKind,
        x: Float,
        y: Float,
        radius: Float
    ) {
        when (shape) {
            com.github.alfu32.sketch.model.HotspotStore.ShapeKind.CIRCLE -> {
                shapeRenderer.circle(x, y, radius, 22)
            }
            com.github.alfu32.sketch.model.HotspotStore.ShapeKind.SQUARE -> {
                shapeRenderer.rect(x - radius, y - radius, radius * 2f, radius * 2f)
            }
            com.github.alfu32.sketch.model.HotspotStore.ShapeKind.DIAMOND -> {
                shapeRenderer.triangle(x, y + radius, x + radius, y, x, y - radius)
                shapeRenderer.triangle(x, y - radius, x - radius, y, x, y + radius)
            }
            com.github.alfu32.sketch.model.HotspotStore.ShapeKind.TRIANGLE_UP -> {
                shapeRenderer.triangle(x - radius, y - radius, x + radius, y - radius, x, y + radius)
            }
            com.github.alfu32.sketch.model.HotspotStore.ShapeKind.TRIANGLE_DOWN -> {
                shapeRenderer.triangle(x - radius, y + radius, x + radius, y + radius, x, y - radius)
            }
        }
    }

    private fun drawHotspotShapeOutline(
        shape: com.github.alfu32.sketch.model.HotspotStore.ShapeKind,
        x: Float,
        y: Float,
        radius: Float
    ) {
        when (shape) {
            com.github.alfu32.sketch.model.HotspotStore.ShapeKind.CIRCLE -> {
                shapeRenderer.circle(x, y, radius, 22)
            }
            com.github.alfu32.sketch.model.HotspotStore.ShapeKind.SQUARE -> {
                shapeRenderer.rect(x - radius, y - radius, radius * 2f, radius * 2f)
            }
            com.github.alfu32.sketch.model.HotspotStore.ShapeKind.DIAMOND -> {
                shapeRenderer.line(x, y + radius, x + radius, y)
                shapeRenderer.line(x + radius, y, x, y - radius)
                shapeRenderer.line(x, y - radius, x - radius, y)
                shapeRenderer.line(x - radius, y, x, y + radius)
            }
            com.github.alfu32.sketch.model.HotspotStore.ShapeKind.TRIANGLE_UP -> {
                shapeRenderer.line(x - radius, y - radius, x + radius, y - radius)
                shapeRenderer.line(x + radius, y - radius, x, y + radius)
                shapeRenderer.line(x, y + radius, x - radius, y - radius)
            }
            com.github.alfu32.sketch.model.HotspotStore.ShapeKind.TRIANGLE_DOWN -> {
                shapeRenderer.line(x - radius, y + radius, x + radius, y + radius)
                shapeRenderer.line(x + radius, y + radius, x, y - radius)
                shapeRenderer.line(x, y - radius, x - radius, y + radius)
            }
        }
    }

    private data class EntityHotspot2D(
        val world: Vector3,
        val color: Color,
        val radiusPx: Float
    )

    private fun collectArchitectureEntityHotspots2D(): List<EntityHotspot2D> {
        if (!scene.hasArchitectureElements() || scene.activeGroup() != scene.root) {
            return emptyList()
        }
        val root = scene.root
        val selected = scene.selectedArchitectureElements(root)
        if (selected.isEmpty()) {
            return emptyList()
        }
        val wallIds = selected.filter { it.kind == ArchitectureStore.ElementKind.WALL }.map { it.id }
        val slabIds = selected.filter { it.kind == ArchitectureStore.ElementKind.SLAB }.map { it.id }
        val frameIds = selected.filter { it.kind == ArchitectureStore.ElementKind.FRAME }.map { it.id }

        val out = mutableListOf<EntityHotspot2D>()
        scene.architectureWallEndpointHandleMarkersWorld(root).forEach { marker ->
            out += EntityHotspot2D(Vector3(marker.center), Color(entityHotspotColor), 7f)
        }
        scene.architectureSlabEndpointHandleMarkersWorld(root).forEach { marker ->
            out += EntityHotspot2D(Vector3(marker.center), Color(entityHotspotColor), 7f)
        }
        scene.architectureFrameEndpointHandleMarkersWorld(root).forEach { marker ->
            out += EntityHotspot2D(Vector3(marker.center), Color(entityHotspotColor), 7f)
        }
        wallIds.forEach { wallId ->
            scene.architectureHoleHandleMarkersWorld(root, wallId = wallId).forEach { marker ->
                out += EntityHotspot2D(Vector3(marker.world), Color(entityHotspotColor), 6f)
            }
        }
        slabIds.forEach { slabId ->
            scene.architectureSlabHoleHandleMarkersWorld(root, slabId = slabId).forEach { marker ->
                out += EntityHotspot2D(Vector3(marker.world), Color(entityHotspotColor), 6f)
            }
        }
        slabIds.forEach { slabId ->
            scene.architectureSlabConstructionHotspotsWorld(root, slabId = slabId).forEach { marker ->
                out += EntityHotspot2D(Vector3(marker.world), Color(entityHotspotAccentColor), 6f)
            }
        }
        frameIds.forEach { frameId ->
            scene.architectureFrameConstructionHotspotsWorld(root, frameId = frameId).forEach { marker ->
                out += EntityHotspot2D(Vector3(marker.world), Color(entityHotspotAccentColor), 6f)
            }
        }
        wallIds.forEach { wallId ->
            scene.architectureHoleConstructionHotspotsWorld(root, wallId = wallId).forEach { marker ->
                out += EntityHotspot2D(Vector3(marker.world), Color(entityHotspotAccentColor), 6f)
            }
        }
        return out
    }

    private fun hotspotInteractionGroups(): List<GroupScene.GroupNode> {
        val groups = linkedSetOf<GroupScene.GroupNode>()
        groups.add(scene.activeGroup())
        if (!scene.isEditing()) {
            scene.selectedGroups().forEach { groups.add(it) }
        }
        return groups.toList()
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

    private fun collectFeedbackWorldLines() {
        capturedFeedbackLines.clear()

        val measurement = toolController.activeTool().measurement(statusModel)
        if (measurement != null && measurement.startWorld.dst2(measurement.endWorld) > 1e-8f) {
            capturedFeedbackLines += FeedbackWorldLine(
                Vector3(measurement.startWorld),
                Vector3(measurement.endWorld)
            )
        }

        toolController.activeTool().feedbackLines().forEach { (a, b) ->
            if (a.dst2(b) > 1e-8f) {
                capturedFeedbackLines += FeedbackWorldLine(Vector3(a), Vector3(b))
            }
        }

        if (::pluginHost.isInitialized) {
            pluginHost.collectDrawLines().forEach { line ->
                capturedFeedbackLines += FeedbackWorldLine(Vector3(line.start), Vector3(line.end))
            }
        }

        if (scene.hasArchitectureElements()) {
            val selectedWallIds = scene.selectedArchitectureElements(scene.root)
                .filter { it.kind == ArchitectureStore.ElementKind.WALL }
                .map { it.id }
                .toSet()
            val selectedSlabIds = scene.selectedArchitectureElements(scene.root)
                .filter { it.kind == ArchitectureStore.ElementKind.SLAB }
                .map { it.id }
                .toSet()
            selectedWallIds.forEach { wallId ->
                scene.architectureHoleGuideSegmentsWorld(scene.root, includeDiagonals = true, wallId = wallId)
                    .forEach { (a, b) -> capturedFeedbackLines += FeedbackWorldLine(Vector3(a), Vector3(b)) }
            }
            selectedSlabIds.forEach { slabId ->
                scene.architectureSlabHoleGuideSegmentsWorld(scene.root, includeDiagonals = true, slabId = slabId)
                    .forEach { (a, b) -> capturedFeedbackLines += FeedbackWorldLine(Vector3(a), Vector3(b)) }
            }
        }
    }

    private fun drawFeedbackLines2DOverlay() {
        if (capturedFeedbackLines.isEmpty()) {
            return
        }
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.projectionMatrix = uiOverlay.stage.camera.combined
        shapeRenderer.transformMatrix = Matrix4().idt()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = feedbackOverlayLineColor
        capturedFeedbackLines.forEach { seg ->
            val a = activeCamera.project(Vector3(seg.start))
            val b = activeCamera.project(Vector3(seg.end))
            if (!a.z.isFinite() || !b.z.isFinite()) {
                return@forEach
            }
            if (a.z < 0f || a.z > 1f || b.z < 0f || b.z > 1f) {
                return@forEach
            }
            shapeRenderer.rectLine(a.x, a.y, b.x, b.y, feedbackOverlayLineWidth)
        }
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
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

    private fun drawVectorTextEdges3D() {
        if (!isBasicKindVisible(BasicSelectionFilterKind.TEXT)) {
            return
        }
        scene.collectWorldTexts { position, text, size, normal, axisU, selected, _, kind, tracking, lineSpacing, glyphSourcePath ->
            if (kind != DraftTextStore.Kind.VECTOR) {
                return@collectWorldTexts
            }
            val catalog = glyphCatalogForSource(glyphSourcePath)
            if (catalog.isEmpty()) {
                return@collectWorldTexts
            }
            val axisW = Vector3(axisU).crs(normal).nor()
            if (axisW.len2() <= 1e-6f) {
                return@collectWorldTexts
            }
            shapeRenderer.color = if (selected) selectedLineColor else Color(0.06f, 0.06f, 0.1f, 1f)
            drawVectorTextGeometry(
                content = text,
                position = position,
                normal = normal,
                axisU = axisU,
                axisW = axisW,
                targetHeight = size.coerceAtLeast(1e-3f),
                tracking = tracking.coerceAtLeast(0f),
                lineSpacing = lineSpacing.coerceAtLeast(0.1f),
                catalog = catalog,
                drawLine = { a, b -> shapeRenderer.line(a, b) }
            )
        }
    }

    private fun drawVectorTextGeometry(
        content: String,
        position: Vector3,
        normal: Vector3,
        axisU: Vector3,
        axisW: Vector3,
        targetHeight: Float,
        tracking: Float,
        lineSpacing: Float,
        catalog: VectorGlyphCatalog,
        drawLine: ((Vector3, Vector3) -> Unit)? = null,
        drawFace: ((Vector3, Vector3, Vector3) -> Unit)? = null
    ) {
        val lineStep = (lineSpacing * targetHeight)
        val spaceAdvance = targetHeight * 0.6f
        var pen = 0f
        var lineOffset = 0f
        var index = 0
        while (index < content.length) {
            val codePoint = Character.codePointAt(content, index)
            index += Character.charCount(codePoint)
            when (codePoint) {
                '\n'.code -> {
                    pen = 0f
                    lineOffset += lineStep
                    continue
                }
                '\r'.code -> continue
                ' '.code, '\t'.code -> {
                    pen += spaceAdvance
                    continue
                }
            }
            val glyph = catalog.glyphOrSquare(codePoint)
            if (glyph.height <= 1e-6f || glyph.width < 0f) {
                pen += spaceAdvance
                continue
            }
            val scale = targetHeight / glyph.height
            val lineOrigin = Vector3(position).mulAdd(axisW, -lineOffset)
            val glyphOrigin = Vector3(lineOrigin)
                .mulAdd(axisU, pen - glyph.minX * scale)
                .mulAdd(axisW, -glyph.minZ * scale)
            val worldU = Vector3(axisU).scl(scale)
            val worldV = Vector3(normal).scl(scale)
            val worldW = Vector3(axisW).scl(scale)
            if (drawLine != null) {
                glyph.segments.forEach { segment ->
                    val a = transformGlyphPoint(segment.start, glyphOrigin, worldU, worldV, worldW)
                    val b = transformGlyphPoint(segment.end, glyphOrigin, worldU, worldV, worldW)
                    drawLine(a, b)
                }
            }
            if (drawFace != null) {
                glyph.faces.forEach { face ->
                    val a = transformGlyphPoint(face.a, glyphOrigin, worldU, worldV, worldW)
                    val b = transformGlyphPoint(face.b, glyphOrigin, worldU, worldV, worldW)
                    val c = transformGlyphPoint(face.c, glyphOrigin, worldU, worldV, worldW)
                    drawFace(a, b, c)
                }
            }
            pen += glyph.width * scale + tracking * targetHeight
        }
    }

    private fun transformGlyphPoint(
        point: Vector3,
        origin: Vector3,
        axisU: Vector3,
        axisV: Vector3,
        axisW: Vector3
    ): Vector3 {
        return Vector3(origin)
            .mulAdd(axisU, point.x)
            .mulAdd(axisV, point.y)
            .mulAdd(axisW, point.z)
    }

    private fun glyphCatalogForSource(path: String): VectorGlyphCatalog {
        val requested = path.trim().ifBlank { "embedded:alphabet" }
        if (requested == vectorTextSettings.glyphSourcePath && !vectorGlyphCatalog.isEmpty()) {
            return vectorGlyphCatalog
        }
        return vectorGlyphCatalogCache.getOrPut(requested) {
            loadVectorGlyphCatalog(requested)
        }
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
            setLineWidth(line.width)
            shapeRenderer.color = line.color
            shapeRenderer.line(line.start, line.end)
        }
        setLineWidth(2f)
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
            },
            maxEntries = 20
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
            gridSpacing,
            circleSegments
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
                { value -> applyGridSpacing(value, false) },
                { value -> applyCircleSegments(value, false) }
            )
            scene.applyChangeListenerToAll()
            applyLightingSettings(lightingSettings)
            applyShadowSettings(shadowSettings)
            orbitCameraController.target.set(cameraTarget)
            syncCameraModesAfterOrbitStateChange()
        } finally {
            restoringSnapshot = false
        }
        markSceneRuntimeDirty()
    }

    private fun onModelChanged() {
        if (restoringSnapshot || pendingModelLoad != null) {
            return
        }
        markSceneRuntimeDirty()
        undoManager.markChanged()
        scheduleAutosave()
        scheduleWebEmbedChange()
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
        val hasHvac = scene.hasHvacElements()
        val hotspotDeletes = deleteSelectedHotspotsAcrossInteractionGroups()
        val architectureHoleEntityDeletes = if (hasArchitecture) {
            scene.deleteSelectedArchitectureHoles(scene.root) + scene.deleteSelectedArchitectureSlabHoles(scene.root)
        } else {
            0
        }
        val architectureElementDeletes = if (hasArchitecture) {
            if (architectureHoleEntityDeletes > 0) 0 else scene.deleteSelectedArchitectureElements(scene.root)
        } else {
            0
        }
        val hvacElementDeletes = if (hasHvac) {
            scene.deleteSelectedHvacElements(scene.root)
        } else {
            0
        }
        val voxelDeletes = if (isVoxel) {
            scene.deleteSelectedVoxels(group)
        } else {
            0
        }
        val architectureHoleDeletes = if (hasArchitecture) {
            if (architectureHoleEntityDeletes > 0) 0 else scene.deleteSelectedArchitectureHoleContours(group)
        } else {
            0
        }
        val edges = if (isVoxel) 0 else activeLineStore().deleteSelected()
        val faces = if (isVoxel) 0 else activeFaceStore().deleteSelected()
        val dimensions = activeDimensionStore().deleteSelected()
        val texts = activeTextStore().deleteSelected()
        val groups = scene.deleteSelectedGroups()
        if (edges + faces + voxelDeletes + hotspotDeletes + architectureHoleDeletes + architectureHoleEntityDeletes + architectureElementDeletes + hvacElementDeletes + dimensions + texts + groups > 0) {
            statusModel.message =
                "Deleted | architecture $architectureElementDeletes hvac $hvacElementDeletes hotspots $hotspotDeletes voxels $voxelDeletes holes ${architectureHoleEntityDeletes + architectureHoleDeletes} edges $edges faces $faces dimensions $dimensions texts $texts groups $groups"
            if (groups > 0 && edges + faces + voxelDeletes + hotspotDeletes + architectureHoleDeletes + architectureHoleEntityDeletes + architectureElementDeletes + hvacElementDeletes == 0) {
                undoManager.commit("Delete")
                saveModel()
            }
        } else if (hasArchitecture) {
            statusModel.message = "Select a wall/slab hole (or its contour) and press Delete."
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
        if (BuildFlags.WEB_BUILD) {
            val snapshot = snapshotForPersistence(includeUndoHistory = true)
            val text = ModelPersistence.saveSnapshotText(snapshot)
            if (isEmbeddedWebRuntime()) {
                emitWebEmbedChange("save", text)
                autosavePending = false
                return
            }
            val bridge = WebRuntime.bridge
            if (bridge == null || !bridge.writeLocalStorage(webAutosaveStorageKey, text)) {
                statusModel.message = "Web autosave failed: local storage unavailable."
            } else {
                bridge.writeLocalStorage(webModelNameStorageKey, modelFile.name)
            }
            autosavePending = false
            return
        }
        autosavePending = false
        waitForAsyncModelSave()
        ModelPersistence.saveSnapshot(modelFile, snapshotForPersistence(includeUndoHistory = true))
        if (::pluginHost.isInitialized) {
            pluginHost.dispatchSave()
        }
    }

    private fun snapshotForPersistence(includeUndoHistory: Boolean): ModelPersistence.ModelSnapshot {
        val undoHistory = if (includeUndoHistory) undoManager.exportHistory() else null
        return ModelPersistence.snapshot(
            scene,
            camera,
            orbitCameraController.target,
            lightingSettings,
            shadowSettings,
            modelUnit,
            snapEpsilon,
            gridSpacing,
            circleSegments,
            undoHistory
        )
    }

    private fun enqueueAsyncModelSave(snapshot: ModelPersistence.ModelSnapshot, file: File = modelFile) {
        autosavePending = false
        if (asyncModelSaveRunning) {
            return
        }
        asyncModelSaveRunning = true
        val worker = Thread({
            try {
                ModelPersistence.saveSnapshot(file, snapshot)
            } catch (t: Throwable) {
                asyncModelSaveError = "Autosave failed: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                asyncModelSaveRunning = false
            }
        }, "k3d-model-save").apply {
            isDaemon = true
        }
        asyncModelSaveThread = worker
        worker.start()
    }

    private fun waitForAsyncModelSave() {
        val worker = asyncModelSaveThread ?: return
        try {
            worker.join()
        } catch (t: Throwable) {
            asyncModelSaveError = "Autosave failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            if (asyncModelSaveThread === worker) {
                asyncModelSaveThread = null
            }
        }
    }

    private fun drainAsyncModelSaveStatus() {
        asyncModelSaveThread?.let { worker ->
            if (!worker.isAlive) {
                waitForAsyncModelSave()
            }
        }
        val message = asyncModelSaveError ?: return
        asyncModelSaveError = null
        statusModel.message = message
    }

    private fun isEmbeddedWebRuntime(): Boolean = BuildFlags.WEB_BUILD && webEmbedConfig != null

    private fun currentModelSnapshotText(includeUndoHistory: Boolean = true): String {
        return ModelPersistence.saveSnapshotText(snapshotForPersistence(includeUndoHistory))
    }

    private fun loadModelFromText(text: String, displayName: String): Boolean {
        val result = ModelPersistence.loadFromText(
            text,
            scene,
            camera,
            cameraTarget,
            lightingSettings,
            shadowSettings,
            modelUnit,
            { value -> applySnapEpsilon(value, false) },
            { value -> applyGridSpacing(value, false) },
            { value -> applyCircleSegments(value, false) }
        )
        if (!result.ok) {
            return false
        }
        val history = result.snapshot?.undoHistory
        undoManager.importHistory(history)
        if (history == null || history.entries.isEmpty()) {
            undoManager.reset("Loaded")
        }
        orbitCameraController.target.set(cameraTarget)
        syncCameraModesAfterOrbitStateChange()
        statusModel.message = "Loaded $displayName"
        markSceneRuntimeDirty()
        return true
    }

    private fun ensureModelLoadDialog() {
        if (modelLoadDialog != null) {
            return
        }
        val titleLabel = com.kotcrab.vis.ui.widget.VisLabel("Loading model")
        val detailLabel = com.kotcrab.vis.ui.widget.VisLabel("")
        val stepLabel = com.kotcrab.vis.ui.widget.VisLabel("1/5 Backing up file")
        val progressBar = com.kotcrab.vis.ui.widget.VisProgressBar(0f, 100f, 1f, false)
        val abortButton = com.kotcrab.vis.ui.widget.VisTextButton("Abort")
        abortButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                pendingModelLoad?.canceled = true
            }
        })
        val content = com.kotcrab.vis.ui.widget.VisTable(true).apply {
            defaults().growX().pad(4f)
            add(titleLabel).left().row()
            add(detailLabel).left().row()
            add(stepLabel).left().row()
            add(progressBar).growX().row()
            add(abortButton).right().padTop(8f).row()
        }
        val dialog = com.kotcrab.vis.ui.widget.VisWindow("Model Load", true).apply {
            isModal = true
            isMovable = false
            isResizable = false
            setKeepWithinParent(true)
            add(content).pad(10f).width(420f)
            pack()
        }
        modelLoadDialog = dialog
        modelLoadTitleLabel = titleLabel
        modelLoadDetailLabel = detailLabel
        modelLoadProgressBar = progressBar
        modelLoadStepLabel = stepLabel
    }

    private fun currentModelLoadStep(load: PendingModelLoad): ModelLoadStepUiState {
        val totalSteps = 5
        return when {
            !load.backupStageDone -> ModelLoadStepUiState(1, totalSteps, "Backing up file", load.backupProgress)
            !load.fileStageDone -> ModelLoadStepUiState(2, totalSteps, "Loading file", load.fileProgress)
            !load.decodeStageDone -> ModelLoadStepUiState(3, totalSteps, "Unzipping", load.decodeProgress)
            !load.sceneApplied -> ModelLoadStepUiState(4, totalSteps, "Scene", load.sceneProgress)
            else -> ModelLoadStepUiState(5, totalSteps, "Indexing", load.indexProgress)
        }
    }

    private fun updateModelLoadDialog(load: PendingModelLoad) {
        ensureModelLoadDialog()
        val dialog = modelLoadDialog ?: return
        if (dialog.parent == null) {
            uiOverlay.stage.addActor(dialog)
        }
        dialog.centerWindow()
        dialog.toFront()
        val step = currentModelLoadStep(load)
        val overallProgress = (((step.index - 1).toFloat() + step.progress.coerceIn(0f, 1f)) / step.total.toFloat()).coerceIn(0f, 1f)
        modelLoadTitleLabel?.setText("Loading ${load.displayName}")
        modelLoadDetailLabel?.setText(
            when {
                load.canceled && !load.sceneApplied -> "Canceling load..."
                load.errorMessage != null -> load.errorMessage
                !load.backupStageDone -> "Creating .bak copy..."
                !load.fileStageDone -> "Reading model bytes..."
                !load.decodeStageDone -> "Decompressing model data..."
                !load.sceneApplied -> "Applying scene state..."
                load.indexingDone < load.indexingTotal -> "Warming indexes..."
                else -> "Finishing..."
            }
        )
        modelLoadStepLabel?.setText("${step.index}/${step.total} ${step.description}")
        modelLoadProgressBar?.value = overallProgress * 100f
    }

    private fun hideModelLoadDialog() {
        modelLoadDialog?.remove()
    }

    private fun prepareEmptySceneForModelLoad() {
        setCameraMode(CameraMode.ORBIT)
        orbitCameraController.target.set(0f, 0f, 0f)
        cameraTarget.set(0f, 0f, 0f)
        camera.position.set(18f, 14f, 18f)
        camera.up.set(0f, 1f, 0f)
        camera.lookAt(cameraTarget)
        camera.update()
        walkCamera.position.set(camera.position)
        walkCamera.direction.set(camera.direction)
        walkCamera.up.set(camera.up)
        walkCamera.update()
        syncCameraModesAfterOrbitStateChange()
        while (scene.exitGroup()) {
            // Return to root before clearing scene state.
        }
        scene.resetScene()
        guideManager.clear()
        toolController.cancelActiveTool()
        toolController.resetToDefault()
        markSceneRuntimeDirty()
    }

    private fun detachModelFileAfterInterruptedLoad() {
        val writableDir = resolveDesktopWritableDataDir().absoluteFile
        if (!writableDir.exists()) {
            writableDir.mkdirs()
        }
        val timestamp = screenshotTimestampFormatter.format(LocalDateTime.now())
        modelFile = java.io.File(writableDir, "untitled-load-$timestamp.octd").absoluteFile
        updateWindowTitle()
        autosavePending = false
        webEmbedChangePending = false
    }

    private fun copyFileWithProgress(
        source: java.io.File,
        target: java.io.File,
        onProgress: (Float) -> Unit,
        isCanceled: () -> Boolean
    ) {
        val totalBytes = source.length().coerceAtLeast(1L)
        val buffer = ByteArray(64 * 1024)
        var copiedBytes = 0L
        source.inputStream().buffered().use { input ->
            target.outputStream().buffered().use { output ->
                while (true) {
                    if (isCanceled()) {
                        throw java.io.InterruptedIOException("backup canceled")
                    }
                    val read = input.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    output.write(buffer, 0, read)
                    copiedBytes += read
                    onProgress((copiedBytes.toDouble() / totalBytes.toDouble()).toFloat())
                }
            }
        }
        onProgress(1f)
    }

    private fun readFileBytesWithProgress(
        file: java.io.File,
        onProgress: (Float) -> Unit,
        isCanceled: () -> Boolean
    ): ByteArray {
        val totalBytes = file.length().coerceAtLeast(1L)
        val output = java.io.ByteArrayOutputStream(totalBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val buffer = ByteArray(64 * 1024)
        var readBytes = 0L
        file.inputStream().buffered().use { input ->
            while (true) {
                if (isCanceled()) {
                    throw java.io.InterruptedIOException("load canceled")
                }
                val read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                output.write(buffer, 0, read)
                readBytes += read
                onProgress((readBytes.toDouble() / totalBytes.toDouble()).toFloat())
            }
        }
        onProgress(1f)
        return output.toByteArray()
    }

    private fun initializeModelLoadIndexing(load: PendingModelLoad) {
        if (load.indexingInitialized) {
            return
        }
        val lineStores = linkedSetOf<com.github.alfu32.sketch.model.DraftLineStore>()
        val faceStores = linkedSetOf<com.github.alfu32.sketch.model.DraftFaceStore>()
        fun collectStores(group: GroupScene.GroupNode) {
            lineStores.add(group.prototype.lineStore)
            faceStores.add(group.prototype.faceStore)
            group.lineStoreOverride?.let { lineStores.add(it) }
            group.faceStoreOverride?.let { faceStores.add(it) }
        }
        collectStores(scene.root)
        scene.walkGroups(scene.root) { group -> collectStores(group) }
        load.indexingTasks.clear()
        lineStores.forEach { store -> load.indexingTasks.addLast { store.warmSpatialIndex() } }
        faceStores.forEach { store -> load.indexingTasks.addLast { store.warmSpatialIndex() } }
        load.indexingTasks.addLast { scene.warmSpatialIndex() }
        load.indexingTotal = load.indexingTasks.size
        load.indexingDone = 0
        load.indexProgress = if (load.indexingTotal == 0) 1f else 0f
        load.indexingInitialized = true
    }

    private fun finishPendingModelLoad(load: PendingModelLoad) {
        val history = load.snapshot?.undoHistory
        undoManager.importHistory(history)
        if (history == null || history.entries.isEmpty()) {
            undoManager.reset("Loaded")
        }
        if (load.needsResave) {
            enqueueAsyncModelSave(snapshotForPersistence(includeUndoHistory = true), load.file)
        }
        orbitCameraController.target.set(cameraTarget)
        syncCameraModesAfterOrbitStateChange()
        applyLightingSettings(lightingSettings)
        applyShadowSettings(shadowSettings)
        uiOverlay.refreshLightingControls()
        scene.applyChangeListenerToAll()
        markSceneRuntimeDirty()
        updateWindowTitle()
        statusModel.message = "Loaded ${load.displayName}"
        hideModelLoadDialog()
        pendingModelLoad = null
    }

    private fun processPendingModelLoad() {
        val load = pendingModelLoad ?: return
        updateModelLoadDialog(load)
        if (load.canceled && !load.sceneApplied) {
            load.sceneSession?.abort()
            load.sceneSession = null
            restoringSnapshot = false
            detachModelFileAfterInterruptedLoad()
            hideModelLoadDialog()
            pendingModelLoad = null
            statusModel.message = "Load canceled."
            return
        }
        val error = load.errorMessage
        if (error != null) {
            load.sceneSession?.abort()
            load.sceneSession = null
            restoringSnapshot = false
            detachModelFileAfterInterruptedLoad()
            hideModelLoadDialog()
            pendingModelLoad = null
            statusModel.message = "Open failed: $error"
            return
        }
        if (!load.fileStageDone) {
            return
        }
        if (!load.sceneApplied) {
            if (load.canceled) {
                load.sceneSession?.abort()
                load.sceneSession = null
                restoringSnapshot = false
                detachModelFileAfterInterruptedLoad()
                hideModelLoadDialog()
                pendingModelLoad = null
                statusModel.message = "Load canceled."
                return
            }
            val snapshot = load.snapshot ?: run {
                detachModelFileAfterInterruptedLoad()
                hideModelLoadDialog()
                pendingModelLoad = null
                statusModel.message = "Open failed: invalid model content."
                return
            }
            val sceneSession = load.sceneSession ?: ModelPersistence.beginApplySnapshot(
                    snapshot,
                    scene,
                    camera,
                    cameraTarget,
                    lightingSettings,
                    shadowSettings,
                    modelUnit,
                    { value -> applySnapEpsilon(value, false) },
                    { value -> applyGridSpacing(value, false) },
                    { value -> applyCircleSegments(value, false) }
                )
                .also {
                    load.sceneSession = it
                    restoringSnapshot = true
                }
            val sceneDeadlineNs = System.nanoTime() + 4_000_000L
            val sceneDone = try {
                sceneSession.advance(sceneDeadlineNs)
            } catch (t: Throwable) {
                sceneSession.abort()
                load.sceneSession = null
                restoringSnapshot = false
                detachModelFileAfterInterruptedLoad()
                load.errorMessage = t.message ?: t.javaClass.simpleName
                return
            }
            load.sceneProgress = sceneSession.progress
            if (!sceneDone) {
                return
            }
            load.sceneSession = null
            restoringSnapshot = false
            load.sceneApplied = true
            load.sceneProgress = 1f
            initializeModelLoadIndexing(load)
            return
        }
        if (!load.indexingInitialized) {
            initializeModelLoadIndexing(load)
        }
        if (load.canceled) {
            load.indexingTasks.clear()
            load.indexingDone = load.indexingTotal
            load.indexProgress = 1f
            finishPendingModelLoad(load)
            statusModel.message = "Loaded ${load.displayName} (index warmup skipped)"
            return
        }
        val deadlineNs = System.nanoTime() + 4_000_000L
        while (load.indexingTasks.isNotEmpty() && System.nanoTime() < deadlineNs) {
            load.indexingTasks.removeFirst().invoke()
            load.indexingDone += 1
        }
        load.indexProgress = if (load.indexingTotal <= 0) {
            1f
        } else {
            load.indexingDone.toFloat() / load.indexingTotal.toFloat()
        }
        if (load.indexingDone >= load.indexingTotal) {
            finishPendingModelLoad(load)
        }
    }

    private fun beginAsyncModelLoad(file: java.io.File, displayName: String = file.name, createIfMissing: Boolean = false) {
        if (pendingModelLoad != null) {
            statusModel.message = "Load already in progress."
            return
        }
        if (!file.exists()) {
            if (createIfMissing) {
                undoManager.reset("Created")
                saveModel()
                statusModel.message = "Created ${file.name}"
                markSceneRuntimeDirty()
            } else {
                statusModel.message = "Open failed: file not found."
            }
            return
        }
        waitForAsyncModelSave()
        val load = PendingModelLoad(file.absoluteFile, displayName, createIfMissing)
        pendingModelLoad = load
        autosavePending = false
        webEmbedChangePending = false
        updateModelLoadDialog(load)
        prepareEmptySceneForModelLoad()
        Thread({
            try {
                val backup = java.io.File(file.absolutePath + ".bak")
                copyFileWithProgress(file, backup, { progress ->
                    load.backupProgress = progress
                }, { load.canceled })
                load.backupProgress = 1f
                load.backupStageDone = true
                if (load.canceled) {
                    return@Thread
                }
                val bytes = readFileBytesWithProgress(file, { progress ->
                    load.fileProgress = progress
                }, { load.canceled })
                if (load.canceled) {
                    return@Thread
                }
                val snapshot = ModelPersistence.parseSnapshotBytes(
                    bytes,
                    onDecodeProgress = { progress -> load.decodeProgress = progress },
                    isCanceled = { load.canceled }
                )
                    ?: throw IllegalStateException("invalid model content")
                if (load.canceled) {
                    return@Thread
                }
                load.snapshot = snapshot
                load.needsResave = ModelPersistence.needsResave(snapshot)
                load.fileProgress = 1f
                load.decodeProgress = 1f
            } catch (t: Throwable) {
                if (!load.canceled) {
                    load.errorMessage = t.message ?: t.javaClass.simpleName
                }
            } finally {
                load.backupStageDone = true
                load.fileStageDone = true
                load.decodeStageDone = true
                load.workerDone = true
            }
        }, "model-load-${displayName}").apply {
            isDaemon = true
            start()
        }
    }

    private fun applyWebEmbedUiOptions() {
        if (!isEmbeddedWebRuntime()) {
            return
        }
        val config = webEmbedConfig ?: return
        uiOverlay.setToolbarsVisible(config.toolbarsVisible)
        uiOverlay.setPanelsVisible(config.panelsVisible)
    }

    private fun emitWebEmbedEvent(eventType: String, payload: Map<String, Any?>) {
        val bridge = WebRuntime.bridge ?: return
        bridge.emitEmbedEvent(eventType, encodeEmbedJson(payload))
    }

    private fun emitWebEmbedReady() {
        if (!isEmbeddedWebRuntime()) {
            return
        }
        emitWebEmbedEvent(
            "ready",
            linkedMapOf(
                "model" to currentModelSnapshotText(includeUndoHistory = true),
                "fileName" to modelFile.name,
                "tool" to toolController.activeToolId().name,
                "revision" to webEmbedRevision,
                "toolbarsVisible" to (webEmbedConfig?.toolbarsVisible ?: true),
                "panelsVisible" to (webEmbedConfig?.panelsVisible ?: true)
            )
        )
    }

    private fun emitWebEmbedChange(reason: String, modelText: String? = null) {
        if (!isEmbeddedWebRuntime()) {
            return
        }
        webEmbedChangePending = false
        webEmbedChangeDueAtMs = 0L
        webEmbedRevision += 1
        emitWebEmbedEvent(
            "change",
            linkedMapOf(
                "model" to (modelText ?: currentModelSnapshotText(includeUndoHistory = true)),
                "fileName" to modelFile.name,
                "reason" to reason,
                "revision" to webEmbedRevision,
                "tool" to toolController.activeToolId().name
            )
        )
    }

    private fun emitWebEmbedError(message: String) {
        if (!isEmbeddedWebRuntime()) {
            return
        }
        emitWebEmbedEvent("error", linkedMapOf("message" to message))
    }

    private fun webEmbedVectorPayload(vector: Vector3): Map<String, Float> = linkedMapOf(
        "x" to vector.x,
        "y" to vector.y,
        "z" to vector.z
    )

    private fun currentWebCameraPayload(): Map<String, Any?> {
        val cameraRef = activeCamera
        return linkedMapOf(
            "mode" to activeCameraMode.name,
            "position" to webEmbedVectorPayload(Vector3(cameraRef.position)),
            "direction" to webEmbedVectorPayload(Vector3(cameraRef.direction)),
            "up" to webEmbedVectorPayload(Vector3(cameraRef.up)),
            "target" to webEmbedVectorPayload(Vector3(cameraTarget)),
            "orthoDistance" to orthoDistance
        )
    }

    private fun currentWebSelectionPayload(): Map<String, Any?> {
        val info = selectionInfo()
        val activeGroup = scene.activeGroup()
        val selectedGroups = scene.selectedGroups().map { group ->
            linkedMapOf(
                "id" to group.id,
                "name" to group.name,
                "kind" to group.kind.name
            )
        }
        val selectedArchitecture = scene.root.architectureStore?.selectedElements()?.map { element ->
            linkedMapOf(
                "kind" to element.kind.name,
                "id" to element.id
            )
        }.orEmpty()
        val selectedHvac = scene.root.hvacStore?.selectedElements()?.map { element ->
            linkedMapOf(
                "kind" to element.kind.name,
                "id" to element.id
            )
        }.orEmpty()
        val selectedHotspots = hotspotInteractionGroups().flatMap { group ->
            scene.selectedHotspots(group).map { selection ->
                val hotspot = group.hotspotStore.hotspotById(selection.id)
                linkedMapOf(
                    "groupId" to group.id,
                    "groupName" to group.name,
                    "id" to selection.id,
                    "name" to hotspot?.name,
                    "operation" to hotspot?.operation?.name,
                    "shape" to hotspot?.shape?.name
                )
            }
        }
        return linkedMapOf(
            "activeGroup" to linkedMapOf(
                "id" to activeGroup.id,
                "name" to activeGroup.name,
                "kind" to activeGroup.kind.name
            ),
            "counts" to linkedMapOf(
                "edges" to info.edgeCount,
                "faces" to info.faceCount,
                "voxels" to info.voxelCount,
                "hotspots" to info.hotspotCount,
                "groups" to info.groupCount,
                "dimensions" to info.dimensionCount,
                "texts" to info.textCount,
                "walls" to info.wallSelectedCount,
                "slabs" to info.slabSelectedCount,
                "stairs" to info.stairSelectedCount,
                "frames" to info.frameSelectedCount
            ),
            "totals" to linkedMapOf(
                "edges" to info.edgeTotalCount,
                "faces" to info.faceTotalCount,
                "voxels" to info.voxelTotalCount,
                "hotspots" to info.hotspotTotalCount,
                "groups" to info.groupTotalCount,
                "dimensions" to info.dimensionTotalCount,
                "texts" to info.textTotalCount,
                "walls" to info.wallTotalCount,
                "slabs" to info.slabTotalCount,
                "stairs" to info.stairTotalCount,
                "frames" to info.frameTotalCount
            ),
            "selectedGroups" to selectedGroups,
            "selectedArchitectureElements" to selectedArchitecture,
            "selectedHvacElements" to selectedHvac,
            "selectedHotspots" to selectedHotspots,
            "selectedText" to linkedMapOf(
                "id" to info.selectedTextId,
                "value" to info.selectedTextValue,
                "size" to info.selectedTextSize,
                "screenText" to info.selectedTextScreen,
                "vectorText" to info.selectedVectorText
            )
        )
    }

    private fun emitWebEmbedSelectionChange(payload: Map<String, Any?> = currentWebSelectionPayload()) {
        if (!isEmbeddedWebRuntime()) {
            return
        }
        emitWebEmbedEvent("selectionchange", payload)
    }

    private fun emitWebEmbedToolChange(toolId: ToolId = toolController.activeToolId()) {
        if (!isEmbeddedWebRuntime()) {
            return
        }
        emitWebEmbedEvent(
            "toolchange",
            linkedMapOf(
                "tool" to toolId.name,
                "displayName" to toolId.displayName
            )
        )
    }

    private fun resolveWebEmbedCommand(
        requestId: String,
        success: Boolean,
        payload: Map<String, Any?>? = null,
        message: String? = null
    ) {
        val bridge = WebRuntime.bridge ?: return
        val payloadJson = payload?.let { encodeEmbedJson(it) }
        bridge.resolveEmbedCommand(requestId, success, payloadJson, message)
    }

    private fun resolveWebEmbedCommandError(requestId: String?, message: String) {
        if (requestId.isNullOrBlank()) {
            emitWebEmbedError(message)
            return
        }
        resolveWebEmbedCommand(requestId, false, message = message)
    }

    private fun toolIdFromWebName(raw: String?): ToolId? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val normalized = raw.trim()
            .uppercase(java.util.Locale.US)
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
        return ToolId.entries.firstOrNull { tool ->
            tool.name == normalized ||
                tool.displayName.uppercase(java.util.Locale.US)
                    .replace(Regex("[^A-Z0-9]+"), "_")
                    .trim('_') == normalized
        }
    }

    private fun cameraModeFromWebName(raw: String?): CameraMode? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return when (raw.trim().uppercase(java.util.Locale.US)) {
            "ORBIT" -> CameraMode.ORBIT
            "WALK", "WALKTHROUGH", "FIRST_PERSON" -> CameraMode.WALKTHROUGH
            "ORTHO", "ORTHOGRAPHIC" -> CameraMode.ORTHOGRAPHIC
            else -> null
        }
    }

    private fun JsonValue.childPointerButton(name: String, defaultValue: Int = Input.Buttons.LEFT): Int {
        val child = get(name) ?: return defaultValue
        return when {
            child.isLong || child.isDouble || child.isNumber -> child.asInt()
            child.isString -> when (child.asString().trim().uppercase(java.util.Locale.US)) {
                "LEFT", "PRIMARY" -> Input.Buttons.LEFT
                "RIGHT", "SECONDARY" -> Input.Buttons.RIGHT
                "MIDDLE" -> Input.Buttons.MIDDLE
                "BACK" -> Input.Buttons.BACK
                "FORWARD" -> Input.Buttons.FORWARD
                else -> child.asString().toIntOrNull() ?: defaultValue
            }

            else -> defaultValue
        }
    }

    private fun embeddedPointerResultPayload(result: McpPointerEventResult): Map<String, Any?> {
        return linkedMapOf(
            "success" to result.success,
            "handled" to result.handled,
            "action" to result.action,
            "pointer" to result.pointer,
            "button" to result.button,
            "screenX" to result.screenX,
            "screenY" to result.screenY,
            "world" to result.worldX?.let { x ->
                linkedMapOf(
                    "x" to x,
                    "y" to result.worldY,
                    "z" to result.worldZ
                )
            },
            "normal" to result.normalX?.let { x ->
                linkedMapOf(
                    "x" to x,
                    "y" to result.normalY,
                    "z" to result.normalZ
                )
            },
            "valid" to result.valid,
            "message" to result.message
        )
    }

    private fun dispatchEmbeddedPointerCommand(payload: JsonValue?): Map<String, Any?> {
        val action = payload?.childString("action")?.lowercase(java.util.Locale.US)?.ifBlank { "click" } ?: "click"
        val pointer = payload?.childInt("pointer", 0) ?: 0
        val button = payload?.childPointerButton("button", Input.Buttons.LEFT) ?: Input.Buttons.LEFT
        val screenX = payload?.childInt("screenX")
        val screenY = payload?.childInt("screenY")
        val world = payload?.childVector3Flexible("world")
        val normal = payload?.childVector3Flexible("normal")
        val valid = payload?.get("valid")?.let { payload.childBoolean("valid", true) }
        val baseRequest = McpPointerEventRequest(
            action = action,
            pointer = pointer,
            button = button,
            screenX = screenX,
            screenY = screenY,
            worldX = world?.x,
            worldY = world?.y,
            worldZ = world?.z,
            normalX = normal?.x,
            normalY = normal?.y,
            normalZ = normal?.z,
            valid = valid
        )
        if (action != "click") {
            return embeddedPointerResultPayload(dispatchPointerEventOnRenderThread(baseRequest))
        }
        val moveResult = dispatchPointerEventOnRenderThread(baseRequest.copy(action = "move"))
        val downResult = dispatchPointerEventOnRenderThread(baseRequest.copy(action = "down"))
        val upResult = dispatchPointerEventOnRenderThread(baseRequest.copy(action = "up"))
        return linkedMapOf(
            "success" to (moveResult.success && downResult.success && upResult.success),
            "handled" to (moveResult.handled || downResult.handled || upResult.handled),
            "action" to "click",
            "move" to embeddedPointerResultPayload(moveResult),
            "down" to embeddedPointerResultPayload(downResult),
            "up" to embeddedPointerResultPayload(upResult),
            "message" to upResult.message
        )
    }

    private fun refreshActiveCameraBinding(mode: CameraMode) {
        if (activeCameraMode != mode) {
            setCameraMode(mode)
            return
        }
        when (mode) {
            CameraMode.ORBIT -> {
                activeCamera = camera
                activeCameraInputProcessor = orbitCameraController
                orbitCameraController.target.set(cameraTarget)
                camera.update()
            }

            CameraMode.WALKTHROUGH -> {
                copyPoseToPerspective(camera, walkCamera, keepTarget = false)
                walkCameraController.syncFromCamera()
                activeCamera = walkCamera
                activeCameraInputProcessor = walkCameraController
                walkCamera.update()
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

    private fun applyEmbeddedCameraCommand(payload: JsonValue?): Map<String, Any?> {
        val requestedMode = cameraModeFromWebName(payload?.childString("mode")) ?: activeCameraMode
        val position = payload?.childVector3Flexible("position")
        val target = payload?.childVector3Flexible("target")
        val requestedOrthoDistance = payload?.childFloat("orthoDistance")?.takeIf { it.isFinite() && it > 0.05f }

        when (requestedMode) {
            CameraMode.ORBIT, CameraMode.WALKTHROUGH -> {
                position?.let { camera.position.set(it) }
                target?.let {
                    cameraTarget.set(it)
                    orbitCameraController.target.set(it)
                }
                val orbitLook = Vector3(cameraTarget).sub(camera.position)
                if (orbitLook.len2() > 1e-6f) {
                    camera.up.set(0f, 1f, 0f)
                    camera.lookAt(cameraTarget)
                }
                camera.update()
                refreshActiveCameraBinding(requestedMode)
            }

            CameraMode.ORTHOGRAPHIC -> {
                requestedOrthoDistance?.let { orthoDistance = it }
                target?.let {
                    cameraTarget.set(it)
                    orbitCameraController.target.set(it)
                }
                if (position != null) {
                    val lookTarget = target ?: Vector3(cameraTarget)
                    val direction = Vector3(lookTarget).sub(position)
                    if (direction.len2() > 1e-6f) {
                        orthoCamera.direction.set(direction.nor())
                    }
                    orthoDistance = position.dst(cameraTarget).coerceAtLeast(0.05f)
                }
                refreshActiveCameraBinding(CameraMode.ORTHOGRAPHIC)
            }
        }
        return currentWebCameraPayload()
    }

    private fun processWebEmbedCommands() {
        if (!isEmbeddedWebRuntime()) {
            return
        }
        val bridge = WebRuntime.bridge ?: return
        repeat(16) {
            val commandJson = bridge.pollEmbedCommandJson() ?: return
            handleWebEmbedCommand(commandJson)
        }
    }

    private fun handleWebEmbedCommand(commandJson: String) {
        val root = try {
            JsonReader().parse(commandJson)
        } catch (_: Throwable) {
            emitWebEmbedError("Invalid embedded command payload.")
            return
        }
        val requestId = root.childString("requestId")
        val command = root.childString("command")
        val payload = root.get("payload")
        if (command.isNullOrBlank()) {
            resolveWebEmbedCommandError(requestId, "Missing embedded command id.")
            return
        }
        val beforeTool = toolController.activeToolId()
        val beforeSelectionPayload = currentWebSelectionPayload()
        val beforeSelectionSignature = encodeEmbedJson(beforeSelectionPayload)
        var commandSucceeded = false
        try {
            when (command) {
                "model.get" -> {
                    resolveWebEmbedCommand(
                        requestId ?: return,
                        true,
                        linkedMapOf(
                            "model" to currentModelSnapshotText(includeUndoHistory = true),
                            "fileName" to modelFile.name,
                            "revision" to webEmbedRevision
                        )
                    )
                    commandSucceeded = true
                }

                "model.set" -> {
                    val modelText = payload?.childString("model")
                    if (modelText.isNullOrBlank()) {
                        resolveWebEmbedCommandError(requestId, "model.set requires payload.model")
                        return
                    }
                    val fileName = payload.childString("fileName")
                    if (!fileName.isNullOrBlank()) {
                        modelFile = File(resolveDesktopWritableDataDir(), sanitizeModelFileName(fileName)).absoluteFile
                    }
                    if (!loadModelFromText(modelText, modelFile.name)) {
                        resolveWebEmbedCommandError(requestId, "Invalid model content.")
                        return
                    }
                    emitWebEmbedChange("model.set", modelText)
                    resolveWebEmbedCommand(
                        requestId ?: return,
                        true,
                        linkedMapOf(
                            "model" to currentModelSnapshotText(includeUndoHistory = true),
                            "fileName" to modelFile.name,
                            "revision" to webEmbedRevision
                        )
                    )
                    commandSucceeded = true
                }

                "selection.get" -> {
                    resolveWebEmbedCommand(requestId ?: return, true, currentWebSelectionPayload())
                    commandSucceeded = true
                }

                "selection.clear" -> {
                    clearSelection()
                    val selectionPayload = currentWebSelectionPayload()
                    resolveWebEmbedCommand(requestId ?: return, true, selectionPayload)
                    commandSucceeded = true
                }

                "ui.setToolbars" -> {
                    val visible = payload?.childBoolean("visible", true) ?: true
                    uiOverlay.setToolbarsVisible(visible)
                    resolveWebEmbedCommand(requestId ?: return, true, linkedMapOf("visible" to visible))
                    commandSucceeded = true
                }

                "ui.setPanels" -> {
                    val visible = payload?.childBoolean("visible", true) ?: true
                    uiOverlay.setPanelsVisible(visible)
                    resolveWebEmbedCommand(requestId ?: return, true, linkedMapOf("visible" to visible))
                    commandSucceeded = true
                }

                "ui.setVisibility" -> {
                    val toolbarsVisible = payload?.get("toolbarsVisible")?.let { payload.childBoolean("toolbarsVisible", true) }
                    val panelsVisible = payload?.get("panelsVisible")?.let { payload.childBoolean("panelsVisible", true) }
                    toolbarsVisible?.let { uiOverlay.setToolbarsVisible(it) }
                    panelsVisible?.let { uiOverlay.setPanelsVisible(it) }
                    resolveWebEmbedCommand(
                        requestId ?: return,
                        true,
                        linkedMapOf(
                            "toolbarsVisible" to (toolbarsVisible ?: (webEmbedConfig?.toolbarsVisible ?: true)),
                            "panelsVisible" to (panelsVisible ?: (webEmbedConfig?.panelsVisible ?: true))
                        )
                    )
                    commandSucceeded = true
                }

                "camera.get" -> {
                    resolveWebEmbedCommand(requestId ?: return, true, currentWebCameraPayload())
                    commandSucceeded = true
                }

                "camera.set" -> {
                    resolveWebEmbedCommand(requestId ?: return, true, applyEmbeddedCameraCommand(payload))
                    commandSucceeded = true
                }

                "tool.select" -> {
                    val toolName = payload?.childString("tool")
                    val toolId = toolIdFromWebName(toolName)
                    if (toolId == null) {
                        resolveWebEmbedCommandError(requestId, "Unknown tool: ${toolName ?: "<null>"}")
                        return
                    }
                    toolController.setTool(toolId)
                    resolveWebEmbedCommand(
                        requestId ?: return,
                        true,
                        linkedMapOf(
                            "tool" to toolController.activeToolId().name,
                            "message" to statusModel.message
                        )
                    )
                    commandSucceeded = true
                }

                "tool.cancel" -> {
                    toolController.cancelActiveTool()
                    resolveWebEmbedCommand(
                        requestId ?: return,
                        true,
                        linkedMapOf(
                            "tool" to toolController.activeToolId().name,
                            "message" to statusModel.message
                        )
                    )
                    commandSucceeded = true
                }

                "tool.pointer" -> {
                    resolveWebEmbedCommand(
                        requestId ?: return,
                        true,
                        dispatchEmbeddedPointerCommand(payload)
                    )
                    commandSucceeded = true
                }

                else -> resolveWebEmbedCommandError(requestId, "Unsupported embedded command: $command")
            }
            if (commandSucceeded) {
                val afterTool = toolController.activeToolId()
                if (afterTool != beforeTool) {
                    emitWebEmbedToolChange(afterTool)
                }
                val afterSelectionPayload = currentWebSelectionPayload()
                val afterSelectionSignature = encodeEmbedJson(afterSelectionPayload)
                if (afterSelectionSignature != beforeSelectionSignature) {
                    emitWebEmbedSelectionChange(afterSelectionPayload)
                }
            }
        } catch (t: Throwable) {
            resolveWebEmbedCommandError(requestId, t.message ?: "Embedded command failed.")
        }
    }

    private fun loadModel() {
        if (BuildFlags.WEB_BUILD) {
            val embedConfig = webEmbedConfig
            if (embedConfig != null) {
                if (!embedConfig.initialFileName.isNullOrBlank()) {
                    modelFile = File(
                        resolveDesktopWritableDataDir(),
                        sanitizeModelFileName(embedConfig.initialFileName)
                    ).absoluteFile
                }
                if (!embedConfig.initialModel.isNullOrBlank()) {
                    if (loadModelFromText(embedConfig.initialModel, modelFile.name)) {
                        return
                    }
                    statusModel.message = "Embedded initial model is invalid."
                }
                undoManager.reset("Created")
                statusModel.message = "Created ${modelFile.name}"
                markSceneRuntimeDirty()
                return
            }
            val bridge = WebRuntime.bridge
            val storedText = bridge?.readLocalStorage(webAutosaveStorageKey)
            val storedName = bridge?.readLocalStorage(webModelNameStorageKey)
            if (!storedName.isNullOrBlank()) {
                modelFile = File(resolveDesktopWritableDataDir(), sanitizeModelFileName(storedName)).absoluteFile
            }
            if (!storedText.isNullOrBlank()) {
                if (loadModelFromText(storedText, modelFile.name)) {
                    return
                }
            }
            undoManager.reset("Created")
            saveModel()
            statusModel.message = "Created ${modelFile.name}"
            markSceneRuntimeDirty()
            return
        }
        beginAsyncModelLoad(modelFile, modelFile.name, createIfMissing = true)
    }

    private fun markSceneRuntimeDirty() {
        instanceGeometryDirty = true
        faceMeshDirty = true
        shadowPassDirty = true
        selectionTotalsDirty = true
        if (::pluginHost.isInitialized) {
            pluginHost.invalidateUpdateContextModelSnapshot()
        }
    }

    private fun scheduleAutosave(now: Long = System.currentTimeMillis()) {
        autosavePending = true
        autosaveDueAtMs = now + autosaveDebounceMs
    }

    private fun scheduleWebEmbedChange(now: Long = System.currentTimeMillis()) {
        if (!isEmbeddedWebRuntime()) {
            return
        }
        webEmbedChangePending = true
        webEmbedChangeDueAtMs = now + webEmbedChangeDebounceMs
    }

    private fun flushWebEmbedChangeIfDue(now: Long = System.currentTimeMillis(), force: Boolean = false) {
        if (!webEmbedChangePending) {
            return
        }
        if (!force && now < webEmbedChangeDueAtMs) {
            return
        }
        emitWebEmbedChange("edit")
    }

    private fun flushAutosaveIfDue(now: Long = System.currentTimeMillis(), force: Boolean = false) {
        if (!autosavePending) {
            return
        }
        if (!force && now < autosaveDueAtMs) {
            return
        }
        if (BuildFlags.WEB_BUILD) {
            autosavePending = false
            if (isEmbeddedWebRuntime()) {
                return
            }
            val bridge = WebRuntime.bridge
            if (bridge == null) {
                statusModel.message = "Web autosave failed: runtime bridge unavailable."
                return
            }
            val text = currentModelSnapshotText(includeUndoHistory = false)
            if (!bridge.writeLocalStorage(webAutosaveStorageKey, text)) {
                statusModel.message = "Web autosave failed: local storage unavailable."
                return
            }
            bridge.writeLocalStorage(webModelNameStorageKey, modelFile.name)
            return
        }
        val runningAsyncSave = asyncModelSaveRunning && (asyncModelSaveThread?.isAlive == true)
        if (!force && runningAsyncSave) {
            autosaveDueAtMs = now + autosaveDebounceMs
            return
        }
        enqueueAsyncModelSave(snapshotForPersistence(includeUndoHistory = false))
    }

    private fun resolveModelFile(args: kotlin.Array<String>): java.io.File {
        var fromArgs = false
        var fileArg: String? = null
        var i = 0
        while (i < args.size) {
            if (args[i] == "--file" && i + 1 < args.size) {
                fileArg = args[i + 1]
                fromArgs = true
                break
            }
            i++
        }
        if (fileArg.isNullOrBlank()) {
            fileArg = "octodraw.octd"
        }
        val requested = java.io.File(fileArg).absoluteFile
        if (fromArgs) {
            return requested
        }
        val preferredDir = requested.parentFile ?: resolveDesktopWritableDataDir()
        if (isDirectoryWritable(preferredDir)) {
            return requested
        }
        val fallbackDir = resolveDesktopWritableDataDir()
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        return java.io.File(fallbackDir, requested.name)
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
        if (!dirArg.isNullOrBlank()) {
            return java.io.File(dirArg).absoluteFile
        }
        val packagedDir = java.io.File(installDir, "plugins").absoluteFile
        if (isDirectoryWritable(packagedDir)) {
            return packagedDir
        }
        val fallback = java.io.File(resolveDesktopWritableDataDir(), "plugins").absoluteFile
        if (!fallback.exists()) {
            fallback.mkdirs()
        }
        return fallback
    }

    private fun ensureDesktopBundledContent(installDir: java.io.File) {
        if (BuildFlags.WEB_BUILD || isAndroidRuntime()) {
            return
        }
        val writableRoot = resolveDesktopWritableDataDir().absoluteFile
        if (!writableRoot.exists()) {
            writableRoot.mkdirs()
        }
        seedBundledDirectory(
            sourceDir = java.io.File(installDir, "tutorials").absoluteFile,
            targetDir = java.io.File(writableRoot, "tutorials").absoluteFile,
            overwriteExisting = true
        )
        seedBundledDirectory(
            sourceDir = java.io.File(installDir, "plugins").absoluteFile,
            targetDir = java.io.File(writableRoot, "plugins").absoluteFile,
            overwriteExisting = false
        )
    }

    private fun seedBundledDirectory(
        sourceDir: java.io.File,
        targetDir: java.io.File,
        overwriteExisting: Boolean
    ) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            return
        }
        if (sourceDir.absoluteFile == targetDir.absoluteFile) {
            return
        }
        sourceDir.walkTopDown().forEach { source ->
            val relative = source.relativeTo(sourceDir)
            val target = java.io.File(targetDir, relative.path)
            if (source.isDirectory) {
                if (!target.exists()) {
                    target.mkdirs()
                }
            } else if (overwriteExisting || !target.exists()) {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = overwriteExisting)
            }
        }
    }

    private fun resolveTutorialsDir(installDir: java.io.File): java.io.File {
        val packagedDir = java.io.File(installDir, "tutorials").absoluteFile
        if (isDirectoryWritable(packagedDir)) {
            return packagedDir
        }
        val fallback = java.io.File(resolveDesktopWritableDataDir(), "tutorials").absoluteFile
        if (!fallback.exists()) {
            fallback.mkdirs()
        }
        return fallback
    }

    private fun tutorialUiState(): TutorialUiState {
        if (BuildFlags.WEB_BUILD) {
            return TutorialUiState()
        }
        return tutorialManager.uiState()
    }

    private fun startTutorialRecording() {
        if (BuildFlags.WEB_BUILD) {
            statusModel.message = "Tutorial recording is unavailable in web builds."
            return
        }
        pendingTutorialPreviewCaptures.clear()
        pendingTutorialPreviewDelayFrames = 0
        val file = tutorialManager.startRecording(captureTutorialThumbnailBase64())
        statusModel.message = "Recording tutorial: ${file.name}"
    }

    private fun stopTutorialRecording() {
        if (BuildFlags.WEB_BUILD) {
            statusModel.message = "Tutorial recording is unavailable in web builds."
            return
        }
        flushPendingTutorialPreviewCapturesNow()
        val file = tutorialManager.stopRecording()
        statusModel.message = if (file != null) {
            "Saved tutorial: ${file.name}"
        } else {
            "Tutorial recording stopped."
        }
    }

    private fun playTutorial(path: String?) {
        if (BuildFlags.WEB_BUILD) {
            statusModel.message = "Tutorial playback is unavailable in web builds."
            return
        }
        if (path.isNullOrBlank()) {
            statusModel.message = "Select a tutorial to play."
            return
        }
        if (tutorialManager.startPlayback(path)) {
            statusModel.message = "Playing tutorial: ${java.io.File(path).name}"
        } else {
            statusModel.message = "Unable to load tutorial: ${java.io.File(path).name}"
        }
    }

    private fun toggleTutorialPause() {
        if (BuildFlags.WEB_BUILD) {
            statusModel.message = "Tutorial playback is unavailable in web builds."
            return
        }
        tutorialManager.togglePausePlayback()
        statusModel.message = when (tutorialManager.uiState().mode) {
            TutorialMode.PAUSED -> "Tutorial paused."
            TutorialMode.PLAYING -> "Tutorial resumed."
            else -> statusModel.message
        }
    }

    private fun stopTutorialPlayback() {
        if (BuildFlags.WEB_BUILD) {
            statusModel.message = "Tutorial playback is unavailable in web builds."
            return
        }
        tutorialManager.stopPlayback()
        statusModel.message = "Tutorial stopped."
    }

    private fun tutorialPreviousStep() {
        if (BuildFlags.WEB_BUILD) {
            statusModel.message = "Tutorial playback is unavailable in web builds."
            return
        }
        if (!tutorialManager.goToPreviousStep()) {
            statusModel.message = "Already at the first tutorial step."
            return
        }
        val state = tutorialManager.uiState()
        statusModel.message = "Tutorial step ${state.currentStepIndex}/${state.totalSteps} ready."
    }

    private fun tutorialNextStep() {
        if (BuildFlags.WEB_BUILD) {
            statusModel.message = "Tutorial playback is unavailable in web builds."
            return
        }
        if (!tutorialManager.goToNextStep()) {
            statusModel.message = "Perform the expected tutorial action before pressing Next."
            return
        }
        val state = tutorialManager.uiState()
        statusModel.message = if (state.mode == TutorialMode.IDLE) {
            "Tutorial complete."
        } else {
            "Tutorial step ${state.currentStepIndex}/${state.totalSteps} ready."
        }
    }

    private fun observeTutorialUiAction(actionId: String, label: String) {
        if (BuildFlags.WEB_BUILD) {
            return
        }
        recordTutorialAction(actionId, label)
    }

    private fun recordTutorialAction(actionId: String, label: String) {
        if (BuildFlags.WEB_BUILD) {
            return
        }
        val recordedStepIndex = tutorialManager.observeAction(actionId, label)
        if (recordedStepIndex != null) {
            pendingTutorialPreviewCaptures.addLast(PendingTutorialPreviewCapture(recordedStepIndex))
            pendingTutorialPreviewDelayFrames = 1
        }
    }

    private fun resolveDesktopWritableDataDir(): java.io.File {
        if (isAndroidRuntime()) {
            return try {
                Gdx.files.local("").file().absoluteFile
            } catch (_: Exception) {
                java.io.File(System.getProperty("user.home", ".")).absoluteFile
            }
        }
        val localAppData = System.getenv("LOCALAPPDATA")?.trim().orEmpty()
        if (localAppData.isNotBlank()) {
            return java.io.File(localAppData, "Octodraw").absoluteFile
        }
        val userHome = System.getProperty("user.home")?.trim().orEmpty()
        if (userHome.isNotBlank()) {
            return java.io.File(userHome, ".octodraw").absoluteFile
        }
        return java.io.File(System.getProperty("user.dir", ".")).absoluteFile
    }

    private fun isDirectoryWritable(dir: java.io.File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs()) {
                return false
            }
            if (!dir.isDirectory) {
                return false
            }
            val probe = java.io.File(dir, ".octodraw-write-test-${System.nanoTime()}.tmp")
            probe.writeText("ok")
            probe.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveInstallDir(): java.io.File {
        if (BuildFlags.WEB_BUILD) {
            return java.io.File(System.getProperty("user.dir", ".")).absoluteFile
        }
        if (isAndroidRuntime()) {
            resolveAndroidInstallDirFromArgs(startupArgs)?.let { return it }
            return try {
                Gdx.files.local("").file().absoluteFile
            } catch (_: Exception) {
                java.io.File(System.getProperty("user.dir", ".")).absoluteFile
            }
        }
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

    private fun isWindowsRuntime(): Boolean {
        val osName = System.getProperty("os.name")?.trim().orEmpty()
        return osName.contains("windows", ignoreCase = true)
    }

    private fun isPackagedWindowsRuntime(): Boolean {
        if (!isWindowsRuntime()) {
            return false
        }
        val installPath = installDir.absolutePath.lowercase()
        return installPath.contains("windowsapps")
    }

    private fun isAndroidRuntime(): Boolean {
        return try {
            Gdx.app?.type == Application.ApplicationType.Android
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveAndroidInstallDirFromArgs(args: kotlin.Array<String>): java.io.File? {
        var i = 0
        while (i < args.size) {
            when {
                (args[i] == "--plugins-dir" || args[i] == "--pluginsDir") && i + 1 < args.size -> {
                    return java.io.File(args[i + 1]).absoluteFile.parentFile
                }
                args[i] == "--file" && i + 1 < args.size -> {
                    return java.io.File(args[i + 1]).absoluteFile.parentFile
                }
            }
            i++
        }
        return null
    }

    private fun isWebRuntime(): Boolean {
        return try {
            Gdx.app?.type == Application.ApplicationType.WebGL
        } catch (_: Exception) {
            false
        }
    }

    private fun isWebSafeRuntime(): Boolean {
        if (BuildFlags.WEB_BUILD) {
            return true
        }
        return when (runtimeProfile) {
            RuntimeProfile.AUTO -> isWebRuntime()
            RuntimeProfile.WEB_SAFE -> true
        }
    }

    private fun maybeShowAndroidFirstRunInputDialog() {
        if (!isAndroidRuntime()) {
            return
        }
        val prefs = try {
            Gdx.app.getPreferences("k3d-android")
        } catch (_: Exception) {
            return
        }
        if (prefs.getBoolean("hardware_input_notice_ack", false)) {
            return
        }
        showAndroidInputNoticeDialog {
            prefs.putBoolean("hardware_input_notice_ack", true)
            prefs.flush()
        }
    }

    private fun showAndroidInputNoticeDialog(onAcknowledge: () -> Unit) {
        val stage = uiOverlay.stage
        val dialog = com.kotcrab.vis.ui.widget.VisWindow("Android Input Notice", true).apply {
            isModal = true
            isMovable = true
            isResizable = false
            setKeepWithinParent(true)
        }
        val content = com.kotcrab.vis.ui.widget.VisTable().apply {
            defaults().pad(6f).left().growX()
        }
        val message = com.kotcrab.vis.ui.widget.VisLabel(
            "Octodraw on Android is currently optimized for an external mouse and keyboard.\n" +
                "Touch-only use is limited.\n\n" +
                "For the best experience use a tablet, Chromebook or DeX setup with mouse + keyboard.\n" +
                "User plugins remain supported from the app plugins folder.\n\n" +
                "Tap OK to continue."
        ).apply {
            setWrap(true)
        }
        val okButton = com.kotcrab.vis.ui.widget.VisTextButton("OK")
        okButton.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(
                event: com.badlogic.gdx.scenes.scene2d.InputEvent?,
                x: Float,
                y: Float
            ) {
                onAcknowledge()
                dialog.remove()
            }
        })

        content.add(message).width(360f).row()
        dialog.add(content).growX().row()
        dialog.add(okButton).right().pad(8f)
        dialog.pack()
        val sw = stage.viewport.worldWidth.takeIf { it > 0f } ?: Gdx.graphics.width.toFloat()
        val sh = stage.viewport.worldHeight.takeIf { it > 0f } ?: Gdx.graphics.height.toFloat()
        dialog.setPosition((sw - dialog.width) * 0.5f, (sh - dialog.height) * 0.5f)
        stage.addActor(dialog)
        stage.keyboardFocus = okButton
        stage.scrollFocus = dialog
    }

    private fun selectionInfo(): SketchUiOverlay.SelectionInfo {
        val group = scene.activeGroup()
        val selectedTexts = group.textStore.getSelected()
        val selectedText = if (selectedTexts.size == 1) selectedTexts.first() else null
        val selectedVoxels = scene.selectedVoxels(group).size
        val selectedHotspots = hotspotInteractionGroups()
            .sumOf { target -> scene.selectedHotspots(target).size }
        val totals = selectionTotals()
        val architectureSelected = scene.selectedArchitectureElements(scene.root)
        val selectedWallCount = architectureSelected.count { it.kind == ArchitectureStore.ElementKind.WALL }
        val selectedSlabCount = architectureSelected.count { it.kind == ArchitectureStore.ElementKind.SLAB }
        val selectedStairCount = architectureSelected.count { it.kind == ArchitectureStore.ElementKind.STAIR }
        val selectedFrameCount = architectureSelected.count { it.kind == ArchitectureStore.ElementKind.FRAME }
        val selectionColorState = currentSelectionEditableColor()
        return SketchUiOverlay.SelectionInfo(
            edgeCount = activeLineStore().getSelected().size,
            edgeTotalCount = totals.edges,
            edgeDrawEnabled = isBasicKindVisible(BasicSelectionFilterKind.EDGE),
            edgeModifyEnabled = isBasicKindUnlocked(BasicSelectionFilterKind.EDGE),
            edgeWireframe = isBasicKindWireframe(BasicSelectionFilterKind.EDGE),
            faceCount = activeFaceStore().getSelected().size,
            faceTotalCount = totals.faces,
            faceDrawEnabled = isBasicKindVisible(BasicSelectionFilterKind.FACE),
            faceModifyEnabled = isBasicKindUnlocked(BasicSelectionFilterKind.FACE),
            faceWireframe = isBasicKindWireframe(BasicSelectionFilterKind.FACE),
            voxelCount = selectedVoxels,
            voxelTotalCount = totals.voxels,
            voxelDrawEnabled = isBasicKindVisible(BasicSelectionFilterKind.VOXEL),
            voxelModifyEnabled = isBasicKindUnlocked(BasicSelectionFilterKind.VOXEL),
            voxelWireframe = isBasicKindWireframe(BasicSelectionFilterKind.VOXEL),
            hotspotCount = selectedHotspots,
            hotspotTotalCount = totals.hotspots,
            hotspotDrawEnabled = isBasicKindVisible(BasicSelectionFilterKind.HOTSPOT),
            hotspotModifyEnabled = isBasicKindUnlocked(BasicSelectionFilterKind.HOTSPOT),
            hotspotWireframe = isBasicKindWireframe(BasicSelectionFilterKind.HOTSPOT),
            groupCount = scene.selectedGroups().size,
            groupTotalCount = totals.groups,
            objectDrawEnabled = isBasicKindVisible(BasicSelectionFilterKind.OBJECT),
            objectModifyEnabled = isBasicKindUnlocked(BasicSelectionFilterKind.OBJECT),
            objectWireframe = isBasicKindWireframe(BasicSelectionFilterKind.OBJECT),
            dimensionCount = group.dimensionStore.getSelected().size,
            dimensionTotalCount = totals.dimensions,
            dimensionDrawEnabled = isBasicKindVisible(BasicSelectionFilterKind.DIMENSION),
            dimensionModifyEnabled = isBasicKindUnlocked(BasicSelectionFilterKind.DIMENSION),
            dimensionWireframe = isBasicKindWireframe(BasicSelectionFilterKind.DIMENSION),
            textCount = selectedTexts.size,
            textTotalCount = totals.texts,
            textDrawEnabled = isBasicKindVisible(BasicSelectionFilterKind.TEXT),
            textModifyEnabled = isBasicKindUnlocked(BasicSelectionFilterKind.TEXT),
            textWireframe = isBasicKindWireframe(BasicSelectionFilterKind.TEXT),
            wallTotalCount = totals.walls,
            wallSelectedCount = selectedWallCount,
            wallDrawEnabled = isArchitectureKindVisible(ArchitectureStore.ElementKind.WALL),
            wallModifyEnabled = isArchitectureKindUnlocked(ArchitectureStore.ElementKind.WALL),
            wallWireframe = isArchitectureKindWireframe(ArchitectureStore.ElementKind.WALL),
            slabTotalCount = totals.slabs,
            slabSelectedCount = selectedSlabCount,
            slabDrawEnabled = isArchitectureKindVisible(ArchitectureStore.ElementKind.SLAB),
            slabModifyEnabled = isArchitectureKindUnlocked(ArchitectureStore.ElementKind.SLAB),
            slabWireframe = isArchitectureKindWireframe(ArchitectureStore.ElementKind.SLAB),
            stairTotalCount = totals.stairs,
            stairSelectedCount = selectedStairCount,
            stairDrawEnabled = isArchitectureKindVisible(ArchitectureStore.ElementKind.STAIR),
            stairModifyEnabled = isArchitectureKindUnlocked(ArchitectureStore.ElementKind.STAIR),
            stairWireframe = isArchitectureKindWireframe(ArchitectureStore.ElementKind.STAIR),
            frameTotalCount = totals.frames,
            frameSelectedCount = selectedFrameCount,
            frameDrawEnabled = isArchitectureKindVisible(ArchitectureStore.ElementKind.FRAME),
            frameModifyEnabled = isArchitectureKindUnlocked(ArchitectureStore.ElementKind.FRAME),
            frameWireframe = isArchitectureKindWireframe(ArchitectureStore.ElementKind.FRAME),
            selectedTextId = selectedText?.id,
            selectedTextValue = selectedText?.text,
            selectedTextSize = selectedText?.size,
            selectedTextScreen = selectedText?.screenText,
            selectedColor = selectionColorState.color,
            selectedColorEditable = selectionColorState.editable,
            selectedVectorText = selectedText?.kind == DraftTextStore.Kind.VECTOR,
            selectedVectorTextTracking = selectedText?.takeIf { it.kind == DraftTextStore.Kind.VECTOR }?.tracking,
            selectedVectorTextLineSpacing = selectedText?.takeIf { it.kind == DraftTextStore.Kind.VECTOR }?.lineSpacing,
            selectedVectorTextGlyphSourcePath = selectedText?.takeIf { it.kind == DraftTextStore.Kind.VECTOR }?.glyphSourcePath
        )
    }

    private data class SelectionEditableColorState(
        val editable: Boolean,
        val color: Color?
    )

    private fun currentSelectionEditableColor(): SelectionEditableColorState {
        val group = scene.activeGroup()
        val colors = mutableListOf<Color>()
        activeFaceStore().getSelected().forEach { triangle ->
            colors += Color(activeFaceStore().colorFor(triangle))
        }
        group.voxelStore?.let { voxelStore ->
            scene.selectedVoxels(group).forEach { key ->
                voxelStore.colorAt(key)?.let { colors += Color(it) }
            }
        }
        hotspotInteractionGroups().forEach { target ->
            scene.selectedHotspots(target).forEach { selection ->
                scene.hotspotById(target, selection.id)?.let { hotspot ->
                    colors += Color(hotspot.color)
                }
            }
        }
        scene.root.architectureStore?.let { store ->
            scene.selectedArchitectureElements(scene.root).forEach { selection ->
                when (selection.kind) {
                    ArchitectureStore.ElementKind.WALL -> store.wallById(selection.id)?.let { wall ->
                        colors += Color(wall.exteriorColor)
                        colors += Color(wall.interiorColor)
                    }
                    ArchitectureStore.ElementKind.SLAB -> store.allSlabs().firstOrNull { it.id == selection.id }?.let { slab ->
                        colors += Color(slab.topColor)
                        colors += Color(slab.bottomColor)
                        colors += Color(slab.sideColor)
                    }
                    ArchitectureStore.ElementKind.STAIR -> store.allStairs().firstOrNull { it.id == selection.id }?.let { stair ->
                        colors += Color(stair.treadColor)
                        colors += Color(stair.supportColor)
                    }
                    ArchitectureStore.ElementKind.FRAME -> store.allFrames().firstOrNull { it.id == selection.id }?.let { frame ->
                        colors += Color(frame.color)
                        if (frame.glazingEnabled) {
                            colors += Color(frame.glazingColor)
                        }
                    }
                }
            }
        }
        if (colors.isEmpty()) {
            return SelectionEditableColorState(editable = false, color = null)
        }
        val first = colors.first()
        val uniform = colors.all { candidate ->
            candidate.r == first.r &&
                candidate.g == first.g &&
                candidate.b == first.b &&
                candidate.a == first.a
        }
        return SelectionEditableColorState(editable = true, color = if (uniform) first else null)
    }

    private fun selectionTotals(): SelectionTotalsCache {
        if (!selectionTotalsDirty) {
            return selectionTotalsCache
        }
        val architectureStore = scene.root.architectureStore
        selectionTotalsCache = SelectionTotalsCache(
            edges = totalEdgeCount(),
            faces = totalFaceCount(),
            voxels = totalVoxelCount(),
            hotspots = totalHotspotCount(),
            groups = scene.groupsInActiveContext().size,
            dimensions = totalDimensionCount(),
            texts = totalTextCount(),
            walls = architectureStore?.allWalls()?.size ?: 0,
            slabs = architectureStore?.allSlabs()?.size ?: 0,
            stairs = architectureStore?.allStairs()?.size ?: 0,
            frames = architectureStore?.allFrames()?.size ?: 0
        )
        selectionTotalsDirty = false
        return selectionTotalsCache
    }

    private fun totalVoxelCount(): Int {
        var count = scene.root.voxelStore?.all()?.size ?: 0
        scene.walkGroups(scene.root) { child ->
            count += child.voxelStore?.all()?.size ?: 0
        }
        return count
    }

    private fun totalDimensionCount(): Int {
        var count = scene.root.dimensionStore.getDimensions().size
        scene.walkGroups(scene.root) { child ->
            count += child.dimensionStore.getDimensions().size
        }
        return count
    }

    private fun totalTextCount(): Int {
        var count = scene.root.textStore.getTexts().size
        scene.walkGroups(scene.root) { child ->
            count += child.textStore.getTexts().size
        }
        return count
    }

    private fun totalHotspotCount(): Int {
        return hotspotInteractionGroups()
            .distinctBy { it.id }
            .sumOf { target -> scene.hotspotMarkersWorld(target).size }
    }

    private fun architectureDisplay(kind: ArchitectureStore.ElementKind): EntityDisplayState {
        return architectureDisplayState.getOrPut(kind) { EntityDisplayState() }
    }

    private fun basicDisplay(kind: BasicSelectionFilterKind): EntityDisplayState {
        return basicDisplayState.getOrPut(kind) { EntityDisplayState() }
    }

    private fun isArchitectureKindVisible(kind: ArchitectureStore.ElementKind): Boolean = architectureDisplay(kind).draw

    private fun isArchitectureKindUnlocked(kind: ArchitectureStore.ElementKind): Boolean = architectureDisplay(kind).unlocked

    private fun isArchitectureKindWireframe(kind: ArchitectureStore.ElementKind): Boolean = architectureDisplay(kind).wireframe

    private fun isBasicKindVisible(kind: BasicSelectionFilterKind): Boolean = basicDisplay(kind).draw

    private fun isBasicKindUnlocked(kind: BasicSelectionFilterKind): Boolean = basicDisplay(kind).unlocked

    private fun isBasicKindWireframe(kind: BasicSelectionFilterKind): Boolean = basicDisplay(kind).wireframe

    private fun updateSelectionFilterFromSelectionPanel(
        kind: SketchUiOverlay.SelectionFilterKind,
        draw: Boolean,
        modifyEnabled: Boolean,
        wireframe: Boolean
    ) {
        when (kind) {
            SketchUiOverlay.SelectionFilterKind.EDGE ->
                updateBasicDisplayState(BasicSelectionFilterKind.EDGE, draw, modifyEnabled, wireframe)
            SketchUiOverlay.SelectionFilterKind.FACE ->
                updateBasicDisplayState(BasicSelectionFilterKind.FACE, draw, modifyEnabled, wireframe)
            SketchUiOverlay.SelectionFilterKind.VOXEL ->
                updateBasicDisplayState(BasicSelectionFilterKind.VOXEL, draw, modifyEnabled, wireframe)
            SketchUiOverlay.SelectionFilterKind.HOTSPOT ->
                updateBasicDisplayState(BasicSelectionFilterKind.HOTSPOT, draw, modifyEnabled, wireframe)
            SketchUiOverlay.SelectionFilterKind.OBJECT ->
                updateBasicDisplayState(BasicSelectionFilterKind.OBJECT, draw, modifyEnabled, wireframe)
            SketchUiOverlay.SelectionFilterKind.DIMENSION ->
                updateBasicDisplayState(BasicSelectionFilterKind.DIMENSION, draw, modifyEnabled, wireframe)
            SketchUiOverlay.SelectionFilterKind.TEXT ->
                updateBasicDisplayState(BasicSelectionFilterKind.TEXT, draw, modifyEnabled, wireframe)
            SketchUiOverlay.SelectionFilterKind.WALL ->
                updateArchitectureDisplayState(ArchitectureStore.ElementKind.WALL, draw, modifyEnabled, wireframe)
            SketchUiOverlay.SelectionFilterKind.SLAB ->
                updateArchitectureDisplayState(ArchitectureStore.ElementKind.SLAB, draw, modifyEnabled, wireframe)
            SketchUiOverlay.SelectionFilterKind.STAIR ->
                updateArchitectureDisplayState(ArchitectureStore.ElementKind.STAIR, draw, modifyEnabled, wireframe)
            SketchUiOverlay.SelectionFilterKind.FRAME ->
                updateArchitectureDisplayState(ArchitectureStore.ElementKind.FRAME, draw, modifyEnabled, wireframe)
        }
    }

    private fun updateBasicDisplayState(
        kind: BasicSelectionFilterKind,
        draw: Boolean? = null,
        modifyEnabled: Boolean? = null,
        wireframe: Boolean? = null
    ) {
        val state = basicDisplay(kind)
        val prevDraw = state.draw
        val prevUnlocked = state.unlocked
        val prevWireframe = state.wireframe

        draw?.let { state.draw = it }
        if (!state.draw) {
            state.unlocked = false
        } else if (draw != null && !prevDraw) {
            state.unlocked = true
        } else if (modifyEnabled != null) {
            state.unlocked = modifyEnabled
        }
        // Wireframe is only meaningful for faces and voxels; keep false for the others.
        when (kind) {
            BasicSelectionFilterKind.FACE,
            BasicSelectionFilterKind.VOXEL -> wireframe?.let { state.wireframe = it }
            else -> state.wireframe = false
        }

        if (state.draw == prevDraw && state.unlocked == prevUnlocked && state.wireframe == prevWireframe) {
            return
        }

        if (!state.draw || !state.unlocked) {
            clearSelectionForBasicKind(kind)
        }
        markSceneRuntimeDirty()
        cursorStatusSampleInitialized = false
    }

    private fun updateArchitectureDisplayState(
        kind: ArchitectureStore.ElementKind,
        draw: Boolean? = null,
        modifyEnabled: Boolean? = null,
        wireframe: Boolean? = null
    ) {
        val state = architectureDisplay(kind)
        val prevDraw = state.draw
        val prevUnlocked = state.unlocked
        val prevWireframe = state.wireframe

        draw?.let { state.draw = it }
        if (!state.draw) {
            state.unlocked = false
        } else if (draw != null && !prevDraw) {
            // Restoring visibility re-enables modification by default.
            state.unlocked = true
        } else if (modifyEnabled != null) {
            state.unlocked = modifyEnabled
        }
        wireframe?.let { state.wireframe = it }

        if (state.draw == prevDraw && state.unlocked == prevUnlocked && state.wireframe == prevWireframe) {
            return
        }

        if (!state.draw || !state.unlocked) {
            clearSelectionForArchitectureKind(kind)
        }
        markSceneRuntimeDirty()
        cursorStatusSampleInitialized = false
    }

    private fun clearSelectionForArchitectureKind(kind: ArchitectureStore.ElementKind) {
        val root = scene.root
        scene.selectedArchitectureElements(root)
            .filter { it.kind == kind }
            .toList()
            .forEach { selection ->
                scene.selectArchitectureElement(root, kind, selection.id, GroupScene.ArchitectureSelectionMode.REMOVE)
            }
        if (kind == ArchitectureStore.ElementKind.WALL) {
            scene.selectedArchitectureHole(root)?.let { holeSel ->
                scene.selectArchitectureHole(
                    root,
                    holeSel.wallId,
                    holeSel.holeId,
                    GroupScene.ArchitectureSelectionMode.REMOVE
                )
            }
        } else if (kind == ArchitectureStore.ElementKind.SLAB) {
            scene.selectedArchitectureSlabHole(root)?.let { holeSel ->
                scene.selectArchitectureSlabHole(
                    root,
                    holeSel.slabId,
                    holeSel.holeId,
                    GroupScene.ArchitectureSelectionMode.REMOVE
                )
            }
        }
        root.faceStore.getSelected().toList().forEach { tri ->
            if (!scene.isGeneratedArchitectureTriangle(tri)) return@forEach
            val owner = scene.generatedArchitectureOwner(tri) ?: return@forEach
            if (owner.kind == kind) {
                root.faceStore.removeSelection(tri)
            }
        }
        root.lineStore.getSelected().toList().forEach { seg ->
            if (!scene.isGeneratedArchitectureSegment(seg)) return@forEach
            val owner = scene.generatedArchitectureOwner(seg) ?: return@forEach
            if (owner.kind == kind) {
                root.lineStore.removeSelection(seg)
            }
        }
    }

    private fun clearSelectionForBasicKind(kind: BasicSelectionFilterKind) {
        when (kind) {
            BasicSelectionFilterKind.EDGE -> {
                scene.root.lineStore.getSelected().toList().forEach { seg ->
                    if (scene.isGeneratedArchitectureSegment(seg) || scene.isGeneratedHvacSegment(seg)) return@forEach
                    scene.root.lineStore.removeSelection(seg)
                }
                scene.walkGroups(scene.root) { group ->
                    group.lineStore.clearSelection()
                }
            }
            BasicSelectionFilterKind.FACE -> {
                scene.root.faceStore.getSelected().toList().forEach { tri ->
                    if (scene.isGeneratedArchitectureTriangle(tri) || scene.isGeneratedHvacTriangle(tri)) return@forEach
                    scene.root.faceStore.removeSelection(tri)
                }
                scene.walkGroups(scene.root) { group ->
                    group.faceStore.clearSelection()
                }
            }
            BasicSelectionFilterKind.VOXEL -> {
                if (scene.root.voxelStore != null) {
                    scene.clearVoxelSelection(scene.root)
                }
                scene.walkGroups(scene.root) { group ->
                    scene.clearVoxelSelection(group)
                }
            }
            BasicSelectionFilterKind.HOTSPOT -> {
                hotspotInteractionGroups().forEach { target -> scene.clearHotspotSelection(target) }
            }
            BasicSelectionFilterKind.OBJECT -> {
                scene.clearGroupSelection()
            }
            BasicSelectionFilterKind.DIMENSION -> {
                scene.activeGroup().dimensionStore.clearSelection()
            }
            BasicSelectionFilterKind.TEXT -> {
                scene.activeGroup().textStore.clearSelection()
            }
        }
    }

    private fun isLineSnapVisibleForSnapping(
        group: GroupScene.GroupNode,
        segment: com.github.alfu32.sketch.model.DraftLineStore.Segment
    ): Boolean {
        if (scene.isVoxelGroup(group)) {
            return isBasicKindVisible(BasicSelectionFilterKind.VOXEL)
        }
        if (group !== scene.root) {
            return isBasicKindVisible(BasicSelectionFilterKind.EDGE)
        }
        if (!isBasicKindVisible(BasicSelectionFilterKind.EDGE) && !scene.isGeneratedArchitectureSegment(segment)) {
            return false
        }
        if (scene.isGeneratedHvacSegment(segment)) {
            return isBasicKindVisible(BasicSelectionFilterKind.EDGE)
        }
        if (!scene.isGeneratedArchitectureSegment(segment)) return true
        val owner = scene.generatedArchitectureOwner(segment) ?: return true
        return isArchitectureKindVisible(owner.kind)
    }

    private fun isFaceVisibleForSnapping(
        group: GroupScene.GroupNode,
        triangle: com.github.alfu32.sketch.model.DraftFaceStore.Triangle
    ): Boolean {
        if (scene.isVoxelGroup(group)) {
            return isBasicKindVisible(BasicSelectionFilterKind.VOXEL) && !isBasicKindWireframe(BasicSelectionFilterKind.VOXEL)
        }
        if (group !== scene.root) {
            return isBasicKindVisible(BasicSelectionFilterKind.FACE) && !isBasicKindWireframe(BasicSelectionFilterKind.FACE)
        }
        if (scene.isGeneratedHvacTriangle(triangle)) {
            return isBasicKindVisible(BasicSelectionFilterKind.FACE) && !isBasicKindWireframe(BasicSelectionFilterKind.FACE)
        }
        if (!scene.isGeneratedArchitectureTriangle(triangle)) {
            return isBasicKindVisible(BasicSelectionFilterKind.FACE) && !isBasicKindWireframe(BasicSelectionFilterKind.FACE)
        }
        val owner = scene.generatedArchitectureOwner(triangle) ?: return true
        return isArchitectureKindVisible(owner.kind) && !isArchitectureKindWireframe(owner.kind)
    }

    private fun hotspotSelectionInfo(): SketchUiOverlay.HotspotSelectionInfo {
        val selectedEntries = hotspotInteractionGroups().flatMap { group ->
            scene.selectedHotspots(group).map { selection -> group to selection.id }
        }
        if (selectedEntries.size != 1) {
            return SketchUiOverlay.HotspotSelectionInfo(selectedCount = selectedEntries.size)
        }
        val (group, hotspotId) = selectedEntries.first()
        val hotspot = scene.hotspotById(group, hotspotId)
            ?: return SketchUiOverlay.HotspotSelectionInfo(selectedCount = selectedEntries.size)
        return SketchUiOverlay.HotspotSelectionInfo(
            selectedCount = selectedEntries.size,
            selectedId = hotspot.id,
            selectedName = hotspot.name,
            selectedOperation = hotspot.operation,
            selectedShape = hotspot.shape,
            selectedColor = Color(hotspot.color),
            attachedEdgeCount = hotspot.attachedSegments.size,
            attachedFaceCount = hotspot.attachedTriangles.size,
            hasReference = hotspot.referencePosition != null
        )
    }

    private fun updateHotspotDefaultOperation(operation: com.github.alfu32.sketch.model.HotspotStore.OperationKind) {
        hotspotSettings.defaultOperation = operation
    }

    private fun updateHotspotDefaultShape(shape: com.github.alfu32.sketch.model.HotspotStore.ShapeKind) {
        hotspotSettings.defaultShape = shape
    }

    private fun updateHotspotDefaultColor(color: Color) {
        hotspotSettings.defaultColor.set(color)
    }

    private fun updateHotspotName(id: String, name: String) {
        val group = hotspotOwnerGroup(id) ?: scene.activeGroup()
        if (scene.updateHotspotName(group, id, name)) {
            statusModel.message = "Hotspot name updated."
        } else if (group !== scene.activeGroup()) {
            statusModel.message = "Enter object editing mode to change hotspot definition."
        }
    }

    private fun updateHotspotOperation(id: String, operation: com.github.alfu32.sketch.model.HotspotStore.OperationKind) {
        val group = hotspotOwnerGroup(id) ?: scene.activeGroup()
        if (scene.updateHotspotOperation(group, id, operation)) {
            statusModel.message = "Hotspot operation updated."
        } else if (group !== scene.activeGroup()) {
            statusModel.message = "Enter object editing mode to change hotspot definition."
        }
    }

    private fun updateHotspotShape(id: String, shape: com.github.alfu32.sketch.model.HotspotStore.ShapeKind) {
        val group = hotspotOwnerGroup(id) ?: scene.activeGroup()
        if (scene.updateHotspotShape(group, id, shape)) {
            statusModel.message = "Hotspot shape updated."
        } else if (group !== scene.activeGroup()) {
            statusModel.message = "Enter object editing mode to change hotspot definition."
        }
    }

    private fun updateHotspotColor(id: String, color: Color) {
        val group = hotspotOwnerGroup(id) ?: scene.activeGroup()
        if (scene.updateHotspotColor(group, id, color)) {
            statusModel.message = "Hotspot color updated."
        } else if (group !== scene.activeGroup()) {
            statusModel.message = "Enter object editing mode to change hotspot definition."
        }
    }

    private fun addHotspotAtCursor() {
        val group = hotspotDefaultTargetGroup()
        if (group !== scene.activeGroup()) {
            statusModel.message = "Enter object editing mode to add hotspots."
            return
        }
        hotspotReferencePickTargetId = null
        hotspotReferencePickTargetGroup = null
        hotspotAddPickTargetGroup = group
        statusModel.message = "Pick hotspot position."
    }

    private fun isHotspotAddPickModeActive(): Boolean {
        return hotspotAddPickTargetGroup != null
    }

    private fun cancelHotspotAddPickMode() {
        if (hotspotAddPickTargetGroup != null) {
            hotspotAddPickTargetGroup = null
            statusModel.message = "Hotspot add canceled."
        }
    }

    private fun deleteSelectedHotspots() {
        val removed = deleteSelectedHotspotsAcrossInteractionGroups()
        statusModel.message = if (removed > 0) {
            "Deleted hotspots: $removed"
        } else {
            "No hotspot selected."
        }
    }

    private fun deleteSelectedHotspotsAcrossInteractionGroups(): Int {
        var removed = 0
        hotspotInteractionGroups()
            .forEach { group ->
                removed += scene.deleteSelectedHotspots(group)
            }
        return removed
    }

    private fun attachSelectionToHotspot(id: String) {
        val group = hotspotOwnerGroup(id) ?: scene.activeGroup()
        val attached = scene.attachCurrentSelectionToHotspot(group, id)
        statusModel.message = if (attached != null) {
            "Attached geometry | edges ${attached.first} faces ${attached.second}"
        } else if (group !== scene.activeGroup()) {
            "Enter object editing mode to attach geometry."
        } else {
            "Hotspot not found."
        }
    }

    private fun selectHotspotAttachedGeometry(id: String) {
        val group = hotspotOwnerGroup(id) ?: scene.activeGroup()
        val selected = scene.selectHotspotAttachedGeometry(group, id, replace = true)
        statusModel.message = if (selected != null) {
            "Selected attached geometry | edges ${selected.first} faces ${selected.second}"
        } else {
            "Hotspot not found."
        }
    }

    private fun beginHotspotReferencePick(id: String) {
        val group = hotspotOwnerGroup(id)
        if (group == null) {
            statusModel.message = "Hotspot not found."
            return
        }
        if (group !== scene.activeGroup()) {
            statusModel.message = "Enter object editing mode to set hotspot reference."
            return
        }
        hotspotAddPickTargetGroup = null
        hotspotReferencePickTargetGroup = group
        hotspotReferencePickTargetId = id
        statusModel.message = "Pick reference point for hotspot."
    }

    private fun clearHotspotReference(id: String) {
        val group = hotspotOwnerGroup(id) ?: scene.activeGroup()
        val updated = scene.updateHotspotReferencePosition(group, id, null)
        if (updated) {
            hotspotReferencePickTargetId = null
            hotspotReferencePickTargetGroup = null
            statusModel.message = "Hotspot reference cleared."
        } else {
            statusModel.message = "Hotspot reference clear failed."
        }
    }

    private fun hotspotDefaultTargetGroup(): GroupScene.GroupNode {
        if (scene.isEditing()) {
            return scene.activeGroup()
        }
        val selectedGroups = scene.selectedGroups()
        return if (selectedGroups.size == 1) selectedGroups.first() else scene.activeGroup()
    }

    private fun hotspotOwnerGroup(id: String): GroupScene.GroupNode? {
        return hotspotInteractionGroups().firstOrNull { group -> scene.hotspotById(group, id) != null }
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

    private fun updateSelectionColor(color: Color) {
        val group = scene.activeGroup()
        var updated = 0
        updated += group.faceStore.paintSelected(color)
        updated += scene.paintSelectedVoxels(group, color)
        hotspotInteractionGroups().forEach { target ->
            scene.selectedHotspots(target).forEach { selection ->
                if (scene.updateHotspotColor(target, selection.id, color)) {
                    updated++
                }
            }
        }
        scene.root.architectureStore?.let { store ->
            scene.selectedArchitectureElements(scene.root).forEach { selection ->
                when (selection.kind) {
                    ArchitectureStore.ElementKind.WALL -> {
                        store.wallById(selection.id)?.let { wall ->
                            if (
                                scene.updateArchitectureWall(
                                    scene.root,
                                    wall.id,
                                    wall.thickness,
                                    wall.height,
                                    wall.inclinationDeg,
                                    color,
                                    color
                                )
                            ) {
                                updated++
                            }
                        }
                    }
                    ArchitectureStore.ElementKind.SLAB -> {
                        store.allSlabs().firstOrNull { it.id == selection.id }?.let { slab ->
                            if (
                                scene.updateArchitectureSlab(
                                    scene.root,
                                    slab.id,
                                    slab.thickness,
                                    color,
                                    color,
                                    color
                                )
                            ) {
                                updated++
                            }
                        }
                    }
                    ArchitectureStore.ElementKind.STAIR -> {
                        store.allStairs().firstOrNull { it.id == selection.id }?.let { stair ->
                            if (
                                scene.updateArchitectureStair(
                                    scene.root,
                                    stair.id,
                                    stair.height,
                                    stair.stepCount,
                                    stair.supportThickness,
                                    stair.railLeftEnabled,
                                    stair.railRightEnabled,
                                    color,
                                    color
                                )
                            ) {
                                updated++
                            }
                        }
                    }
                    ArchitectureStore.ElementKind.FRAME -> {
                        store.allFrames().firstOrNull { it.id == selection.id }?.let { frame ->
                            if (
                                scene.updateArchitectureFrame(
                                    scene.root,
                                    frame.id,
                                    frame.depth,
                                    frame.frameWidth,
                                    color,
                                    frame.glazingEnabled,
                                    color
                                )
                            ) {
                                updated++
                            }
                        }
                    }
                }
            }
        }
        if (updated > 0) {
            statusModel.paintColor.set(color)
            statusModel.message = if (updated == 1) {
                "Selection color updated."
            } else {
                "Selection color updated for $updated items."
            }
        }
    }

    private fun updateNormalOverlayLineWidth(width: Float) {
        normalOverlayLineWidth = width.coerceIn(1f, 8f)
        runtimePrefs.putFloat(normalLineWidthPrefKey, normalOverlayLineWidth)
        runtimePrefs.flush()
    }

    private fun updateFeedbackOverlayLineWidth(width: Float) {
        feedbackOverlayLineWidth = width.coerceIn(1f, 16f)
        runtimePrefs.putFloat(feedbackLineWidthPrefKey, feedbackOverlayLineWidth)
        runtimePrefs.flush()
    }

    private fun updateSelectedVectorTextTracking(textId: String, tracking: Float) {
        val group = scene.activeGroup()
        if (group.textStore.updateTracking(textId, tracking)) {
            statusModel.message = "Vector text tracking updated."
        }
    }

    private fun updateSelectedVectorTextLineSpacing(textId: String, lineSpacing: Float) {
        val group = scene.activeGroup()
        if (group.textStore.updateLineSpacing(textId, lineSpacing)) {
            statusModel.message = "Vector text line spacing updated."
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

    private fun enterSelectedObjectEditMode() {
        val selected = scene.selectedGroups()
        if (selected.size != 1) {
            statusModel.message = "Select one object to edit."
            return
        }
        val group = selected.first()
        if (scene.enterGroup(group)) {
            scene.clearAllSelections()
            statusModel.message = "Editing object: ${group.name}"
        }
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
            recordTutorialAction(
                "guide.axis.place",
                "Press T to place an axis helper at the cursor."
            )
            statusModel.message = "Axis guide added."
        }
    }

    private fun addGridGuide() {
        val snap = lastSnap
        val point = snap?.world
        if (snap != null && snap.valid && point != null) {
            guideManager.addGridGuide(point, snap.normal)
            recordTutorialAction(
                "guide.grid.place",
                "Press G to place a grid helper at the cursor."
            )
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
        adaptiveGridCloudState = null
        snapper.gridSpacing = next
        if (save) {
            undoManager.markChanged()
            saveModel()
        }
    }

    private fun setGridSpacing(value: Float) {
        applyGridSpacing(value, true)
    }

    private fun applyCircleSegments(value: Int, save: Boolean) {
        val next = value.coerceIn(3, 256)
        if (circleSegments == next) {
            return
        }
        circleSegments = next
        if (save) {
            undoManager.markChanged()
            saveModel()
        }
    }

    private fun setCircleSegments(value: Int) {
        applyCircleSegments(value, true)
    }

    private fun vectorGlyphSourcePath(): String {
        return vectorTextSettings.glyphSourcePath
    }

    private fun showVectorGlyphSourceDialog() {
        if (BuildFlags.WEB_BUILD) {
            statusModel.message = "Vector glyph source picker is not available in web runtime yet."
            return
        }
        if (isAndroidRuntime()) {
            if (!showAndroidSafVectorGlyphSourceDialog()) {
                statusModel.message = "Vector glyph browse is unavailable on this Android runtime."
            }
            return
        }
        val option = chooseDesktopFileDialogOption(
            title = "Load Vector Glyph Catalog",
            message = "Choose the source type to show in the system dialog.",
            options = listOf(
                DesktopFileDialogOption("Vector Glyph Catalog (*.octd)", setOf("octd"), "octd"),
                DesktopFileDialogOption("Fonts (*.ttf, *.otf)", setOf("ttf", "otf"), "ttf")
            )
        ) ?: run {
            statusModel.message = "Vector glyph load cancelled."
            return
        }
        val statusBefore = statusModel.message
        val requested = showDesktopFileDialog(
            title = "Load Vector Glyph Catalog - ${option.label}",
            mode = FileDialog.LOAD,
            allowedExtensions = option.extensions
        )
        if (requested == null) {
            if (statusModel.message == statusBefore) {
                statusModel.message = "Vector glyph load cancelled."
            }
            return
        }
        loadVectorGlyphCatalogFromPath(requested.absolutePath)
    }

    private fun showAndroidSafVectorGlyphSourceDialog(): Boolean {
        val bridge = AndroidSaf.bridge ?: return false
        bridge.openDocument { uri, displayName ->
            val safeUri = uri?.trim().orEmpty()
            if (safeUri.isBlank()) {
                statusModel.message = "Vector glyph load cancelled."
                return@openDocument
            }
            val bytes = bridge.readBytes(safeUri)
            if (bytes == null || bytes.isEmpty()) {
                statusModel.message = "Failed to read selected glyph source."
                return@openDocument
            }
            val nameHint = displayName?.trim().takeUnless { it.isNullOrBlank() } ?: safeUri
            val lower = nameHint.lowercase()
            if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
                statusModel.message = "TTF/OTF vector import is not implemented yet. Use .octd glyph catalogs."
                return@openDocument
            }
            val loaded = runCatching {
                VectorGlyphCatalog.fromEncodedModel(bytes.toString(Charsets.UTF_8), "saf:$nameHint")
            }.getOrNull()
            if (loaded == null || loaded.isEmpty()) {
                statusModel.message = "Failed to load glyph catalog: $nameHint"
                return@openDocument
            }
            val persistedPath = persistSafGlyphCatalog(nameHint, bytes)
            vectorGlyphCatalog = loaded
            vectorTextSettings.glyphSourcePath = persistedPath ?: "saf:$nameHint"
            statusModel.message = "Glyph catalog loaded (${loaded.size()} glyphs) from ${loaded.source}."
        }
        return true
    }

    private fun persistSafGlyphCatalog(nameHint: String, bytes: ByteArray): String? {
        return try {
            val fileName = nameHint.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "glyphs.octd" }
            val safeName = if (fileName.lowercase().endsWith(".octd")) fileName else "$fileName.octd"
            val baseDir = modelFile.parentFile ?: File(System.getProperty("user.home"))
            val glyphDir = File(baseDir, "glyphs").apply { mkdirs() }
            val target = File(glyphDir, safeName)
            target.writeBytes(bytes)
            target.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadVectorGlyphCatalogFromPath(path: String) {
        val requested = path.trim().ifBlank { "embedded:alphabet" }
        val lower = requested.lowercase()
        if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
            statusModel.message = "TTF/OTF vector import is not implemented yet. Use .octd glyph catalogs."
            return
        }
        val loaded = loadVectorGlyphCatalog(requested, allowEmbeddedFallback = requested.startsWith("embedded", ignoreCase = true))
        if (loaded.isEmpty()) {
            statusModel.message = "Failed to load glyph catalog: $requested"
            return
        }
        vectorGlyphCatalogCache[requested] = loaded
        vectorGlyphCatalog = loaded
        vectorTextSettings.glyphSourcePath = requested
        val selected = scene.activeGroup().textStore.getSelected()
        if (selected.size == 1 && selected.first().kind == DraftTextStore.Kind.VECTOR) {
            scene.activeGroup().textStore.updateGlyphSourcePath(selected.first().id, requested)
        }
        statusModel.message = "Glyph catalog loaded (${loaded.size()} glyphs) from ${loaded.source}."
    }

    private fun loadVectorGlyphCatalog(path: String, allowEmbeddedFallback: Boolean = true): VectorGlyphCatalog {
        val requested = path.trim()
        if (requested.isBlank() || requested.startsWith("embedded", ignoreCase = true)) {
            val embedded = EmbeddedVectorGlyphCatalog.load()
            vectorGlyphCatalogCache["embedded:alphabet"] = embedded
            return embedded
        }

        fun fromInternal(internalPath: String): VectorGlyphCatalog? {
            return try {
                val handle = Gdx.files.internal(internalPath)
                if (!handle.exists()) return null
                VectorGlyphCatalog.fromEncodedModel(handle.readString("UTF-8"), "internal:$internalPath")
            } catch (_: Exception) {
                null
            }
        }
        fun fromAbsoluteOrRelative(filePath: String): VectorGlyphCatalog? {
            return try {
                val file = File(filePath)
                val resolved = if (file.isAbsolute) file else File(System.getProperty("user.dir"), filePath)
                if (!resolved.exists() || !resolved.isFile) return null
                VectorGlyphCatalog.fromEncodedModel(resolved.readText(), resolved.absolutePath)
            } catch (_: Exception) {
                null
            }
        }

        val loaded = fromInternal(requested) ?: fromAbsoluteOrRelative(requested)
        if (loaded != null && !loaded.isEmpty()) {
            return loaded
        }
        if (allowEmbeddedFallback) {
            val embedded = EmbeddedVectorGlyphCatalog.load()
            if (!embedded.isEmpty()) {
                vectorGlyphCatalogCache["embedded:alphabet"] = embedded
                return embedded
            }
        }
        return VectorGlyphCatalog.empty(requested)
    }

    private fun walkthroughTuningInfo(): WalkthroughTuning {
        return WalkthroughTuning(
            moveSpeed = walkCameraController.moveSpeed,
            jumpVelocity = walkCameraController.jumpVelocity,
            gravity = walkCameraController.gravity,
            heightAdjustSpeed = walkCameraController.heightAdjustSpeed
        )
    }

    private fun loadWalkthroughTuningPrefs() {
        if (!::walkCameraController.isInitialized) return
        walkCameraController.moveSpeed =
            runtimePrefs.getFloat(walkMoveSpeedPrefKey, walkCameraController.moveSpeed).coerceAtLeast(0.1f)
        walkCameraController.jumpVelocity =
            runtimePrefs.getFloat(walkJumpVelocityPrefKey, walkCameraController.jumpVelocity).coerceAtLeast(0.1f)
        walkCameraController.gravity =
            runtimePrefs.getFloat(walkGravityPrefKey, walkCameraController.gravity).coerceAtLeast(0.1f)
        walkCameraController.heightAdjustSpeed =
            runtimePrefs.getFloat(walkHeightAdjustSpeedPrefKey, walkCameraController.heightAdjustSpeed).coerceAtLeast(0.1f)
    }

    private fun saveWalkthroughTuningPrefs() {
        if (!::walkCameraController.isInitialized) return
        runtimePrefs.putFloat(walkMoveSpeedPrefKey, walkCameraController.moveSpeed)
        runtimePrefs.putFloat(walkJumpVelocityPrefKey, walkCameraController.jumpVelocity)
        runtimePrefs.putFloat(walkGravityPrefKey, walkCameraController.gravity)
        runtimePrefs.putFloat(walkHeightAdjustSpeedPrefKey, walkCameraController.heightAdjustSpeed)
        runtimePrefs.flush()
    }

    private fun updateWalkthroughTuning(tuning: WalkthroughTuning) {
        val moveSpeed = tuning.moveSpeed.coerceAtLeast(0.1f)
        val jump = tuning.jumpVelocity.coerceAtLeast(0.1f)
        val gravity = tuning.gravity.coerceAtLeast(0.1f)
        val adjust = tuning.heightAdjustSpeed.coerceAtLeast(0.1f)
        walkCameraController.moveSpeed = moveSpeed
        walkCameraController.jumpVelocity = jump
        walkCameraController.gravity = gravity
        walkCameraController.heightAdjustSpeed = adjust
        saveWalkthroughTuningPrefs()
        statusModel.message = "Walkthrough tuning updated."
    }

    private fun ungroupSelection() {
        val targets = scene.selectedGroups().toList()
        val architectureSelections = scene.selectedArchitectureElements(scene.root)
        val hvacSelections = scene.selectedHvacElements(scene.root)
        val explodedVectorTexts = explodeSelectedVectorTexts()
        val explodedArchitecture = if (architectureSelections.isNotEmpty()) {
            scene.explodeSelectedArchitectureElements(scene.root)
        } else {
            0
        }
        val explodedHvac = if (hvacSelections.isNotEmpty()) {
            scene.explodeSelectedHvacElements(scene.root)
        } else {
            0
        }
        fun messageFor(ungrouped: Int?): String {
            val parts = mutableListOf<String>()
            if (ungrouped != null) {
                parts += "Ungrouped $ungrouped group(s)"
            }
            if (explodedArchitecture > 0) {
                parts += "exploded architecture $explodedArchitecture element(s)"
            }
            if (explodedHvac > 0) {
                parts += "exploded HVAC $explodedHvac element(s)"
            }
            if (explodedVectorTexts > 0) {
                parts += "exploded vector texts $explodedVectorTexts"
            }
            if (parts.isEmpty()) {
                return if (ungrouped != null) "Ungrouped $ungrouped group(s)." else "Nothing to explode."
            }
            return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
        }
        if (targets.isEmpty()) {
            if (explodedArchitecture + explodedHvac + explodedVectorTexts > 0) {
                statusModel.message = messageFor(null)
                undoManager.commit("Explode Elements")
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
                val changed = count + explodedArchitecture + explodedHvac + explodedVectorTexts
                if (changed > 0) {
                    statusModel.message = messageFor(count)
                    undoManager.commit("Explode")
                    saveModel()
                }
            }
            return
        }
        val count = scene.ungroupSelected()
        val changed = count + explodedArchitecture + explodedHvac + explodedVectorTexts
        if (changed > 0) {
            statusModel.message = messageFor(count)
            undoManager.commit("Explode")
            saveModel()
        }
    }

    private fun explodeSelectedVectorTexts(): Int {
        val group = scene.activeGroup()
        val selected = group.textStore.getSelected().filter { it.kind == DraftTextStore.Kind.VECTOR }
        if (selected.isEmpty()) {
            return 0
        }
        val linesBefore = group.lineStore.getSegments().toSet()
        val facesBefore = group.faceStore.getTriangles().toSet()
        group.lineStore.withChangeSuppressed {
            group.faceStore.withChangeSuppressed {
                selected.forEach { text ->
                    explodeVectorTextEntity(group, text)
                }
            }
        }
        group.textStore.removeTexts(selected)
        group.lineStore.clearSelection()
        group.faceStore.clearSelection()
        group.textStore.clearSelection()
        group.lineStore.getSegments().filter { it !in linesBefore }.forEach { group.lineStore.addSelection(it) }
        group.faceStore.getTriangles().filter { it !in facesBefore }.forEach { group.faceStore.addSelection(it) }
        return selected.size
    }

    private fun explodeVectorTextEntity(group: GroupScene.GroupNode, text: DraftTextStore.TextEntity) {
        val axisW = Vector3(text.axisU).crs(text.normal).nor()
        if (axisW.len2() <= 1e-6f) {
            return
        }
        val catalog = glyphCatalogForSource(text.glyphSourcePath)
        drawVectorTextGeometry(
            content = text.text,
            position = Vector3(text.position),
            normal = Vector3(text.normal),
            axisU = Vector3(text.axisU),
            axisW = axisW,
            targetHeight = text.size.coerceAtLeast(1e-3f),
            tracking = text.tracking.coerceAtLeast(0f),
            lineSpacing = text.lineSpacing.coerceAtLeast(0.1f),
            catalog = catalog,
            drawLine = { a, b -> group.lineStore.addSegment(a, b, autoCleanup = false) },
            drawFace = { a, b, c -> group.faceStore.addTriangle(a, b, c, scene.defaultFaceColor) }
        )
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

    private fun updateShadowModelBoundsOnSceneChange() {
        val edgeCount = totalEdgeCount()
        val faceCount = totalFaceCount()
        val insertOnly =
            shadowModelBoundsValid &&
                shadowModelTrackedEdgeCount >= 0 &&
                shadowModelTrackedFaceCount >= 0 &&
                edgeCount >= shadowModelTrackedEdgeCount &&
                faceCount >= shadowModelTrackedFaceCount &&
                (edgeCount > shadowModelTrackedEdgeCount || faceCount > shadowModelTrackedFaceCount)

        if (insertOnly) {
            expandShadowModelBoundsFromScene()
        } else {
            recomputeShadowModelBoundsFromScene()
        }

        shadowModelTrackedEdgeCount = edgeCount
        shadowModelTrackedFaceCount = faceCount
    }

    private fun recomputeShadowModelBoundsFromScene() {
        val bounds = scene.root.worldBounds()
        if (bounds == null) {
            shadowModelBoundsCenter.setZero()
            shadowModelBoundsRadius = minimumShadowBoundsRadius
            shadowModelBoundsValid = false
        } else {
            bounds.getCenter(shadowBoundsCenterTmp)
            bounds.getDimensions(shadowBoundsDimensionsTmp)
            shadowModelBoundsCenter.set(shadowBoundsCenterTmp)
            shadowModelBoundsRadius = (shadowBoundsDimensionsTmp.len() * 0.5f).coerceAtLeast(minimumShadowBoundsRadius)
            shadowModelBoundsValid = true
        }
        shadowModelTrackedEdgeCount = totalEdgeCount()
        shadowModelTrackedFaceCount = totalFaceCount()
    }

    private fun expandShadowModelBoundsFromScene() {
        val bounds = scene.root.worldBounds()
        if (bounds == null) {
            shadowModelBoundsCenter.setZero()
            shadowModelBoundsRadius = minimumShadowBoundsRadius
            shadowModelBoundsValid = false
            return
        }
        if (!shadowModelBoundsValid) {
            recomputeShadowModelBoundsFromScene()
            return
        }
        cornersFromBounds(bounds).forEach { corner ->
            expandShadowModelBoundsWithPoint(corner)
        }
    }

    private fun expandShadowModelBoundsWithPoint(point: Vector3) {
        if (!shadowModelBoundsValid) {
            shadowModelBoundsCenter.set(point)
            shadowModelBoundsRadius = minimumShadowBoundsRadius
            shadowModelBoundsValid = true
            return
        }
        val dx = point.x - shadowModelBoundsCenter.x
        val dy = point.y - shadowModelBoundsCenter.y
        val dz = point.z - shadowModelBoundsCenter.z
        val dist2 = dx * dx + dy * dy + dz * dz
        val radius = shadowModelBoundsRadius
        if (dist2 <= radius * radius) {
            return
        }
        val dist = kotlin.math.sqrt(dist2)
        if (dist <= 1e-6f) {
            return
        }
        val newRadius = (radius + dist) * 0.5f
        val shift = (newRadius - radius) / dist
        shadowModelBoundsCenter.mulAdd(Vector3(dx, dy, dz), shift)
        shadowModelBoundsRadius = newRadius
    }

    private fun updateShadowCameraFromModelBounds() {
        val radius = if (shadowModelBoundsValid) shadowModelBoundsRadius.coerceAtLeast(minimumShadowBoundsRadius) else minimumShadowBoundsRadius
        val diameter = radius * 2f
        val paddedSize = (diameter * 1.15f).coerceAtLeast(minimumShadowBoundsRadius * 2f)
        val near = 0.5f
        val far = (near + diameter * 4f).coerceAtLeast(64f)
        shadowLight.setShadowVolume(paddedSize, paddedSize, near, far)
    }

    private fun drawGroupSelectionHighlights() {
        if (!isBasicKindVisible(BasicSelectionFilterKind.OBJECT)) {
            return
        }
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
        fun extend(point: Vector3) {
            if (!hasAny) {
                bounds.set(point, point)
                hasAny = true
            } else {
                bounds.ext(point)
            }
        }
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
                extend(world)
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
        scene.selectedHvacBounds(scene.root)?.let { hvacBounds ->
            if (!hasAny) {
                bounds.set(hvacBounds)
                hasAny = true
            } else {
                bounds.ext(hvacBounds)
            }
        }
        group.lineStore.getSelected().forEach { segment ->
            val a = group.toWorld(segment.start)
            val b = group.toWorld(segment.end)
            extend(a)
            extend(b)
        }
        group.faceStore.getSelected().forEach { tri ->
            val a = group.toWorld(tri.a)
            val b = group.toWorld(tri.b)
            val c = group.toWorld(tri.c)
            extend(a)
            extend(b)
            extend(c)
        }
        group.dimensionStore.getSelected().forEach { dimension ->
            val start = group.toWorld(dimension.start)
            val end = group.toWorld(dimension.end)
            val offsetPoint = group.toWorld(dimension.offset)
            val (lineStart, lineEnd) = DimensionMath.computeOffsetLine(start, end, offsetPoint)
            val offsetDir = DimensionMath.computeOffsetDirection(start, end, offsetPoint)
            val baseScale = 1.2f
            val extension = textWorldSize(lineStart, textFont.lineHeight * baseScale)
            val extensionEndA = Vector3(lineStart).mulAdd(offsetDir, extension)
            val extensionEndB = Vector3(lineEnd).mulAdd(offsetDir, extension)
            val dir = Vector3(lineEnd).sub(lineStart)
            if (dir.len2() > 1e-6f) {
                dir.nor()
            }
            val dimensionExtend = extension * 0.7f
            val dimStart = Vector3(lineStart).mulAdd(dir, -dimensionExtend)
            val dimEnd = Vector3(lineEnd).mulAdd(dir, dimensionExtend)
            val slashDir = Vector3(dir).add(offsetDir)
            if (slashDir.len2() > 1e-6f) {
                slashDir.nor()
            }
            val slashLen = extension * 0.6f
            val slashStartA = Vector3(lineStart).mulAdd(slashDir, -slashLen * 0.5f)
            val slashEndA = Vector3(lineStart).mulAdd(slashDir, slashLen * 0.5f)
            val slashStartB = Vector3(lineEnd).mulAdd(slashDir, -slashLen * 0.5f)
            val slashEndB = Vector3(lineEnd).mulAdd(slashDir, slashLen * 0.5f)
            extend(start)
            extend(end)
            extend(offsetPoint)
            extend(lineStart)
            extend(lineEnd)
            extend(extensionEndA)
            extend(extensionEndB)
            extend(dimStart)
            extend(dimEnd)
            extend(slashStartA)
            extend(slashEndA)
            extend(slashStartB)
            extend(slashEndB)
        }
        group.textStore.getSelected().forEach { text ->
            val pos = group.toWorld(text.position)
            extend(pos)
        }
        return if (hasAny) bounds else null
    }

    private fun drawSelectedVoxelHighlights() {
        if (!isBasicKindVisible(BasicSelectionFilterKind.VOXEL)) {
            return
        }
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
        shadowLight = ResizableDirectionalShadowLight(
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
        groundMesh = buildGroundMesh(groundPlaneSize, groundPlaneCenter)
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

    private fun computeFaceMeshVisualStamp(): Long {
        var h = 1469598103934665603L
        fun mix(v: Long) {
            h = (h xor v) * 0x100000001b3L
        }
        mix(scene.root.faceStore.visualVersion())
        val activeGroup = scene.activeGroup()
        mix(if (scene.isEditing()) 1L else 0L)
        mix(activeGroup.id.hashCode().toLong())
        mix(if (activeGroup.editPrototypeMode) 1L else 0L)
        mix(if (activeGroup.hasGeometryOverrides()) 1L else 0L)
        if (scene.isEditing() && !activeGroup.editPrototypeMode && activeGroup.hasGeometryOverrides()) {
            mix(activeGroup.prototype.faceStore.visualVersion())
        }
        return h
    }

    private fun updateFaceMesh() {
        updateGroundPlaneFromSceneBounds(scene.root.worldBounds())
        val triangles = mutableListOf<TriangleWorld>()
        syncGroupFaceBundles()
        scene.root.faceStore.getTriangles().forEach { tri ->
            if (!shouldIncludeRootTriangle(tri)) {
                return@forEach
            }
            triangles.add(
                TriangleWorld(
                Vector3(tri.a),
                Vector3(tri.b),
                Vector3(tri.c),
                scene.root.faceStore.colorFor(tri),
                scene.root.faceStore.isSelected(tri)
                )
            )
        }
        scene.collectActivePrototypeWorldTriangles { a, b, c, _ ->
            triangles.add(TriangleWorld(a, b, c, prototypeGuideFaceColor, selected = false))
        }
        updateMeshVertices(
            targetMesh = faceMesh,
            onMeshRecreated = { newMesh ->
                faceMesh = newMesh
                faceFrontRenderable = MeshRenderableProvider(faceMesh, faceFrontMaterial, GL20.GL_TRIANGLES)
                faceBackRenderable = MeshRenderableProvider(faceMesh, faceBackMaterial, GL20.GL_TRIANGLES)
            },
            triangles = triangles
        )

        val shadowBounds = scene.root.worldBounds()
        if (shadowBounds != null) {
            shadowBounds.getCenter(shadowBoundsCenterTmp)
            shadowBounds.getDimensions(shadowBoundsDimensionsTmp)
            shadowModelBoundsCenter.set(shadowBoundsCenterTmp)
            shadowModelBoundsRadius = (shadowBoundsDimensionsTmp.len() * 0.5f).coerceAtLeast(minimumShadowBoundsRadius)
            shadowModelBoundsValid = true
        } else {
            shadowModelBoundsCenter.setZero()
            shadowModelBoundsRadius = minimumShadowBoundsRadius
            shadowModelBoundsValid = false
        }
    }

    private fun shouldIncludeGroupTriangle(group: GroupScene.GroupNode): Boolean {
        return if (scene.isVoxelGroup(group)) {
            isBasicKindVisible(BasicSelectionFilterKind.VOXEL) &&
                !isBasicKindWireframe(BasicSelectionFilterKind.VOXEL)
        } else {
            isBasicKindVisible(BasicSelectionFilterKind.FACE) &&
                !isBasicKindWireframe(BasicSelectionFilterKind.FACE)
        }
    }

    private fun shouldIncludeRootTriangle(tri: com.github.alfu32.sketch.model.DraftFaceStore.Triangle): Boolean {
        if (scene.isGeneratedArchitectureTriangle(tri)) {
            val owner = scene.generatedArchitectureOwner(tri) ?: return true
            return isArchitectureKindVisible(owner.kind) && !isArchitectureKindWireframe(owner.kind)
        }
        return isBasicKindVisible(BasicSelectionFilterKind.FACE) &&
            !isBasicKindWireframe(BasicSelectionFilterKind.FACE)
    }

    private fun computeGroupFaceMeshVisualStamp(group: GroupScene.GroupNode): Long {
        var h = 1469598103934665603L
        fun mix(v: Long) {
            h = (h xor v) * 0x100000001b3L
        }
        fun mixFloat(v: Float) {
            mix(java.lang.Float.floatToIntBits(v).toLong())
        }
        mix(group.faceStore.visualVersion())
        mix(System.identityHashCode(group.faceStore).toLong())
        mix(group.id.hashCode().toLong())
        mix(if (shouldIncludeGroupTriangle(group)) 1L else 0L)
        val worldMatrix = group.worldMatrix().`val`
        worldMatrix.forEach(::mixFloat)
        return h
    }

    private fun syncGroupFaceBundles() {
        val liveIds = linkedSetOf<String>()
        scene.walkGroups(scene.root) { group ->
            liveIds.add(group.id)
        }
        groupFaceBundles.keys.filter { it !in liveIds }.toList().forEach { id ->
            groupFaceBundles.remove(id)?.mesh?.dispose()
        }
    }

    private fun ensureGroupFaceBundle(group: GroupScene.GroupNode): FaceMeshBundle {
        val stamp = computeGroupFaceMeshVisualStamp(group)
        val existing = groupFaceBundles[group.id]
        if (existing != null && existing.visualStamp == stamp) {
            return existing
        }
        val bundle = existing ?: createFaceMeshBundle()
        val triangles = if (shouldIncludeGroupTriangle(group)) {
            group.faceStore.getTriangles().map { tri ->
                TriangleWorld(
                    group.toWorld(tri.a),
                    group.toWorld(tri.b),
                    group.toWorld(tri.c),
                    group.faceStore.colorFor(tri),
                    group.faceStore.isSelected(tri)
                )
            }
        } else {
            emptyList()
        }
        updateMeshVertices(
            targetMesh = bundle.mesh,
            onMeshRecreated = { newMesh ->
                bundle.mesh = newMesh
                bundle.frontRenderable = MeshRenderableProvider(newMesh, faceFrontMaterial, GL20.GL_TRIANGLES)
                bundle.backRenderable = MeshRenderableProvider(newMesh, faceBackMaterial, GL20.GL_TRIANGLES)
            },
            triangles = triangles
        )
        bundle.visualStamp = stamp
        groupFaceBundles[group.id] = bundle
        return bundle
    }

    private fun createFaceMeshBundle(): FaceMeshBundle {
        val mesh = Mesh(false, 1, 0,
            VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
            VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color")
        )
        return FaceMeshBundle(
            mesh = mesh,
            frontRenderable = MeshRenderableProvider(mesh, faceFrontMaterial, GL20.GL_TRIANGLES),
            backRenderable = MeshRenderableProvider(mesh, faceBackMaterial, GL20.GL_TRIANGLES)
        )
    }

    private fun updateMeshVertices(
        targetMesh: Mesh,
        onMeshRecreated: (Mesh) -> Unit,
        triangles: List<TriangleWorld>
    ) {
        val vertexCount = triangles.size * 3
        if (vertexCount == 0) {
            targetMesh.setVertices(FloatArray(0))
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
        if (targetMesh.maxVertices < vertexCount) {
            targetMesh.dispose()
            val newMesh = Mesh(false, vertexCount, 0,
                VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color")
            )
            newMesh.setVertices(vertices)
            onMeshRecreated(newMesh)
            return
        }
        targetMesh.setVertices(vertices)
    }

    private data class FaceMeshBundle(
        var mesh: Mesh,
        var frontRenderable: MeshRenderableProvider,
        var backRenderable: MeshRenderableProvider,
        var visualStamp: Long = Long.MIN_VALUE
    )

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

    private fun updateGroundPlaneFromSceneBounds(bounds: com.badlogic.gdx.math.collision.BoundingBox?) {
        val targetCenter = Vector3()
        val targetSize = if (bounds == null) {
            minimumGroundPlaneSize
        } else {
            targetCenter.set(
                (bounds.min.x + bounds.max.x) * 0.5f,
                0f,
                (bounds.min.z + bounds.max.z) * 0.5f
            )
            val spanX = kotlin.math.abs(bounds.max.x - bounds.min.x)
            val spanZ = kotlin.math.abs(bounds.max.z - bounds.min.z)
            (kotlin.math.max(spanX, spanZ) * groundPlaneExtentMultiplier)
                .coerceAtLeast(minimumGroundPlaneSize)
        }
        if (::groundMesh.isInitialized &&
            kotlin.math.abs(targetSize - groundPlaneSize) <= 1e-3f &&
            targetCenter.epsilonEquals(groundPlaneCenter, 1e-3f)
        ) {
            return
        }
        groundPlaneSize = targetSize
        groundPlaneCenter.set(targetCenter)
        if (::groundMesh.isInitialized) {
            groundMesh.dispose()
        }
        groundMesh = buildGroundMesh(groundPlaneSize, groundPlaneCenter)
        if (::groundMaterial.isInitialized) {
            groundRenderable = MeshRenderableProvider(groundMesh, groundMaterial, GL20.GL_TRIANGLES)
        }
    }

    private fun buildGroundMesh(size: Float, center: Vector3 = Vector3.Zero): Mesh {
        val half = size * 0.5f
        val vertices = floatArrayOf(
            center.x - half, 0f, center.z - half, 0f, 1f, 0f,
            center.x + half, 0f, center.z + half, 0f, 1f, 0f,
            center.x + half, 0f, center.z - half, 0f, 1f, 0f,
            center.x - half, 0f, center.z - half, 0f, 1f, 0f,
            center.x - half, 0f, center.z + half, 0f, 1f, 0f,
            center.x + half, 0f, center.z + half, 0f, 1f, 0f
        )
        return Mesh(true, 6, 0,
            VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal")
        ).apply { setVertices(vertices) }
    }

    private fun renderShadowPass() {
        val shadowCenter = if (shadowModelBoundsValid) shadowModelBoundsCenter else Vector3.Zero
        shadowLight.begin(shadowCenter, shadowLight.direction)
        shadowBatch.begin(shadowLight.camera)
        shadowBatch.render(faceFrontRenderable)
        shadowBatch.render(faceBackRenderable)
        scene.walkGroups(scene.root) { group ->
            val bundle = ensureGroupFaceBundle(group)
            shadowBatch.render(bundle.frontRenderable)
            shadowBatch.render(bundle.backRenderable)
        }
        shadowBatch.end()
        shadowLight.end()
    }

    private class ResizableDirectionalShadowLight(
        shadowMapWidth: Int,
        shadowMapHeight: Int,
        viewportWidth: Float,
        viewportHeight: Float,
        near: Float,
        far: Float
    ) : DirectionalShadowLight(
        shadowMapWidth,
        shadowMapHeight,
        viewportWidth,
        viewportHeight,
        near,
        far
    ) {
        fun setShadowVolume(viewportWidth: Float, viewportHeight: Float, near: Float, far: Float) {
            val ortho = camera as? OrthographicCamera ?: return
            ortho.viewportWidth = viewportWidth
            ortho.viewportHeight = viewportHeight
            ortho.near = near
            ortho.far = far
            halfHeight = viewportHeight * 0.5f
            halfDepth = near + (far - near) * 0.5f
            ortho.update()
        }
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

package com.github.alfu32.sketch.tools

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.collision.Ray
import com.github.alfu32.sketch.DimensionMath
import com.github.alfu32.sketch.model.ArchitectureStore
import com.github.alfu32.sketch.model.DraftDimensionStore
import com.github.alfu32.sketch.model.DraftFaceStore
import com.github.alfu32.sketch.model.DraftLineStore
import com.github.alfu32.sketch.model.DraftTextStore
import com.github.alfu32.sketch.model.GroupScene
import com.github.alfu32.sketch.model.HvacStore
import com.github.alfu32.sketch.model.VoxelStore
import com.github.alfu32.sketch.ui.StatusModel
import com.github.alfu32.sketch.ui.Tool
import com.github.alfu32.sketch.ui.ToolId

class SelectTool(
    private val scene: GroupScene,
    private val cameraProvider: () -> Camera
) : Tool {
    data class WindowRect(val x: Float, val y: Float, val width: Float, val height: Float, val dashed: Boolean)

    override val id: ToolId = ToolId.SELECT
    override val message: String = "Select entities."
    private val doubleClickMs = 350L
    private val clickDistanceSq = 36
    private val dragDistanceSq = 49
    private var lastClickTime = 0L
    private var lastClickX = 0
    private var lastClickY = 0
    private var clickCount = 0
    private var volumeStartRaw: Vector3? = null
    private var volumeEndRaw: Vector3? = null
    private var selectingVolume = false
    private var selectingWindow = false
    private var windowDragActive = false
    private var windowPointerDown = false
    private var windowStartX = 0
    private var windowStartY = 0
    private var windowEndX = 0
    private var windowEndY = 0
    private var pendingVolumeStart: Vector3? = null
    private var holeDrag: HoleDragState? = null
    private var wallDrag: WallDragState? = null
    private var slabDrag: SlabDragState? = null
    private var frameDrag: FrameDragState? = null
    private var hvacDrag: HvacDragState? = null
    private var hotspotDrag: HotspotDragState? = null
    private enum class SelectionMode { REPLACE, ADD, REMOVE }

    private data class HoleHandleHit(
        val wallId: String,
        val holeId: String,
        val kind: GroupScene.HoleHandleKind,
        val point: Vector3,
        val t: Float
    )

    private data class ArchitectureHotspotHit(
        val kind: ArchitectureStore.ElementKind,
        val id: String,
        val point: Vector3,
        val t: Float
    )

    private data class HvacHotspotHit(
        val kind: HvacStore.ElementKind,
        val id: String,
        val point: Vector3,
        val t: Float
    )

    private data class HvacHandleHit(
        val marker: GroupScene.HvacControlHandleMarker,
        val point: Vector3,
        val t: Float
    )

    private data class HotspotHit(
        val id: String,
        val point: Vector3,
        val t: Float
    )

    private data class HoleDragState(
        val group: GroupScene.GroupNode,
        val wallId: String,
        val holeId: String,
        val kind: GroupScene.HoleHandleKind,
        val movingWorld: Vector3
    )

    private data class WallEndpointHit(
        val wallId: String,
        val draggingStart: Boolean,
        val fixedWorld: Vector3,
        val movingWorld: Vector3
    )

    private data class WallDragState(
        val group: GroupScene.GroupNode,
        val wallId: String,
        val draggingStart: Boolean,
        val fixedWorld: Vector3,
        val movingWorld: Vector3
    )

    private data class SlabEndpointHit(
        val slabId: String,
        val draggingStart: Boolean,
        val fixedWorld: Vector3,
        val movingWorld: Vector3
    )

    private data class SlabDragState(
        val group: GroupScene.GroupNode,
        val slabId: String,
        val draggingStart: Boolean,
        val fixedWorld: Vector3,
        val movingWorld: Vector3
    )

    private data class FrameEndpointHit(
        val frameId: String,
        val draggingStart: Boolean,
        val fixedWorld: Vector3,
        val movingWorld: Vector3
    )

    private data class FrameDragState(
        val group: GroupScene.GroupNode,
        val frameId: String,
        val draggingStart: Boolean,
        val fixedWorld: Vector3,
        val movingWorld: Vector3
    )

    private data class HvacDragState(
        val group: GroupScene.GroupNode,
        val marker: GroupScene.HvacControlHandleMarker,
        val movingWorld: Vector3
    )

    private data class HotspotDragState(
        val group: GroupScene.GroupNode,
        val hotspotId: String,
        val movingWorld: Vector3
    )

    override fun onEnter(status: StatusModel) {
        status.message = "Select entities."
    }

    override fun onCancel(status: StatusModel) {
        scene.activeGroup().lineStore.clearSelection()
        scene.activeGroup().faceStore.clearSelection()
        scene.activeGroup().dimensionStore.clearSelection()
        scene.activeGroup().textStore.clearSelection()
        scene.clearHotspotSelection(scene.activeGroup())
        scene.clearVoxelSelection(scene.activeGroup())
        scene.clearArchitectureElementSelection(scene.root)
        scene.clearHvacElementSelection(scene.root)
        scene.clearGroupSelection()
        selectingVolume = false
        volumeStartRaw = null
        volumeEndRaw = null
        selectingWindow = false
        windowDragActive = false
        windowPointerDown = false
        pendingVolumeStart = null
        holeDrag = null
        wallDrag = null
        slabDrag = null
        frameDrag = null
        hvacDrag = null
        hotspotDrag = null
        status.message = "Selection cleared."
    }

    override fun onPointerMoved(status: StatusModel, world: Vector3?, normal: Vector3?, valid: Boolean) {
        holeDrag?.let { drag ->
            if (valid && world != null) {
                holeDrag = drag.copy(movingWorld = Vector3(world))
            }
            return
        }
        wallDrag?.let { drag ->
            if (valid && world != null) {
                wallDrag = drag.copy(movingWorld = Vector3(world))
            }
            return
        }
        slabDrag?.let { drag ->
            if (valid && world != null) {
                slabDrag = drag.copy(movingWorld = Vector3(world))
            }
            return
        }
        frameDrag?.let { drag ->
            if (valid && world != null) {
                frameDrag = drag.copy(movingWorld = Vector3(world))
            }
            return
        }
        hvacDrag?.let { drag ->
            if (valid && world != null) {
                hvacDrag = drag.copy(movingWorld = Vector3(world))
            }
            return
        }
        hotspotDrag?.let { drag ->
            if (valid && world != null) {
                hotspotDrag = drag.copy(movingWorld = Vector3(world))
            }
            return
        }
        if (selectingVolume && valid && world != null) {
            volumeEndRaw = Vector3(world)
        }
        if (selectingWindow && windowPointerDown) {
            windowEndX = Gdx.input.x
            windowEndY = Gdx.input.y
            val dx = windowEndX - windowStartX
            val dy = windowEndY - windowStartY
            if (dx * dx + dy * dy >= dragDistanceSq) {
                windowDragActive = true
            }
        }
    }

    override fun onPointerDown(
        status: StatusModel,
        world: com.badlogic.gdx.math.Vector3?,
        normal: com.badlogic.gdx.math.Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        windowPointerDown = true
        if (selectingVolume) {
            if (valid && world != null) {
                volumeEndRaw = Vector3(world)
                finalizeVolumeSelection(status)
                selectingVolume = false
                return true
            }
            return false
        }
        if (isAltPressed()) {
            selectingWindow = true
            windowDragActive = false
            windowStartX = Gdx.input.x
            windowStartY = Gdx.input.y
            windowEndX = windowStartX
            windowEndY = windowStartY
            // Alt forces 2D window selection only, never starts the 3D volume mode.
            pendingVolumeStart = null
            return true
        }
        val ray = cameraProvider().getPickRay(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        val activeGroup = scene.activeGroup()
        val architectureGroup = scene.root
        if (scene.isEditing()) {
            val hotspotHit = pickHotspotWorld(activeGroup, ray, Gdx.input.x, Gdx.input.y)
            if (hotspotHit != null) {
                val mode = hotspotSelectionMode()
                val changed = scene.selectHotspot(activeGroup, hotspotHit.id, mode)
                hotspotDrag = if (mode == GroupScene.HotspotSelectionMode.REPLACE && changed) {
                    HotspotDragState(
                        group = activeGroup,
                        hotspotId = hotspotHit.id,
                        movingWorld = Vector3(hotspotHit.point)
                    )
                } else {
                    null
                }
                status.message = when (mode) {
                    GroupScene.HotspotSelectionMode.REMOVE -> "Hotspot deselected."
                    GroupScene.HotspotSelectionMode.ADD -> "Hotspot added to selection."
                    GroupScene.HotspotSelectionMode.REPLACE -> if (changed) "Hotspot selected. Drag and release to apply." else "Hotspot selection unchanged."
                }
                return true
            }
        }
        val isVoxelGroup = scene.isVoxelGroup(activeGroup)
        val architectureSelectionEnabled = scene.hasArchitectureElements() && activeGroup == architectureGroup
        val hvacSelectionEnabled = scene.hasHvacElements() && activeGroup == architectureGroup
        val parametricSelectionEnabled =
            (scene.hasArchitectureElements() || scene.hasHvacElements()) && activeGroup == architectureGroup
        if (architectureSelectionEnabled) {
            val holeHit = pickArchitectureHoleHandle(architectureGroup, ray, Gdx.input.x, Gdx.input.y)
            if (holeHit != null) {
                scene.selectArchitectureElement(
                    architectureGroup,
                    ArchitectureStore.ElementKind.WALL,
                    holeHit.wallId,
                    GroupScene.ArchitectureSelectionMode.REPLACE
                )
                holeDrag = HoleDragState(
                    group = architectureGroup,
                    wallId = holeHit.wallId,
                    holeId = holeHit.holeId,
                    kind = holeHit.kind,
                    movingWorld = Vector3(holeHit.point)
                )
                status.message = "Drag hole marker and release to update hole."
                return true
            }
            val endpointHit = pickSelectedWallEndpoint(architectureGroup, ray)
            if (endpointHit != null) {
                scene.selectArchitectureElement(
                    architectureGroup,
                    ArchitectureStore.ElementKind.WALL,
                    endpointHit.wallId,
                    GroupScene.ArchitectureSelectionMode.REPLACE
                )
                wallDrag = WallDragState(
                    group = architectureGroup,
                    wallId = endpointHit.wallId,
                    draggingStart = endpointHit.draggingStart,
                    fixedWorld = Vector3(endpointHit.fixedWorld),
                    movingWorld = Vector3(endpointHit.movingWorld)
                )
                status.message = "Drag wall end and release to update."
                return true
            }
            val slabEndpointHit = pickSelectedSlabEndpoint(architectureGroup, ray)
            if (slabEndpointHit != null) {
                scene.selectArchitectureElement(
                    architectureGroup,
                    ArchitectureStore.ElementKind.SLAB,
                    slabEndpointHit.slabId,
                    GroupScene.ArchitectureSelectionMode.REPLACE
                )
                slabDrag = SlabDragState(
                    group = architectureGroup,
                    slabId = slabEndpointHit.slabId,
                    draggingStart = slabEndpointHit.draggingStart,
                    fixedWorld = Vector3(slabEndpointHit.fixedWorld),
                    movingWorld = Vector3(slabEndpointHit.movingWorld)
                )
                status.message = "Drag slab handle and release to update."
                return true
            }
            val frameEndpointHit = pickSelectedFrameEndpoint(architectureGroup, ray)
            if (frameEndpointHit != null) {
                scene.selectArchitectureElement(
                    architectureGroup,
                    ArchitectureStore.ElementKind.FRAME,
                    frameEndpointHit.frameId,
                    GroupScene.ArchitectureSelectionMode.REPLACE
                )
                frameDrag = FrameDragState(
                    group = architectureGroup,
                    frameId = frameEndpointHit.frameId,
                    draggingStart = frameEndpointHit.draggingStart,
                    fixedWorld = Vector3(frameEndpointHit.fixedWorld),
                    movingWorld = Vector3(frameEndpointHit.movingWorld)
                )
                status.message = "Drag frame handle and release to update."
                return true
            }
            val hotspotHit = pickArchitectureConstructionHotspot(architectureGroup, ray, Gdx.input.x, Gdx.input.y)
            if (hotspotHit != null) {
                val selected = scene.selectArchitectureElement(
                    architectureGroup,
                    hotspotHit.kind,
                    hotspotHit.id,
                    architectureSelectionMode()
                )
                status.message = if (selected) {
                    "Architecture element selected."
                } else {
                    "No architecture element selected."
                }
                return true
            }
            val architectureFaceHit = pickFaceWorld(ray, onlyArchitectureGenerated = true)
            val architectureEdgeHit = pickEdgeWorld(
                ray,
                Gdx.input.x,
                Gdx.input.y,
                onlyArchitectureGenerated = true
            )
            val architecturePointHit = when {
                architectureFaceHit != null && architectureEdgeHit != null -> {
                    if (architectureFaceHit.t <= architectureEdgeHit.t) architectureFaceHit.point else architectureEdgeHit.point
                }
                architectureFaceHit != null -> architectureFaceHit.point
                architectureEdgeHit != null -> architectureEdgeHit.point
                else -> null
            }
            if (architecturePointHit != null) {
                scene.selectArchitectureElementNearWorldPoint(
                    architectureGroup,
                    architecturePointHit,
                    architectureSelectionMode()
                )
                status.message = "Architecture selection updated."
                return true
            }
        }
        if (hvacSelectionEnabled) {
            val handleHit = pickSelectedHvacHandle(architectureGroup, ray, Gdx.input.x, Gdx.input.y)
            if (handleHit != null) {
                scene.selectHvacElement(
                    architectureGroup,
                    handleHit.marker.kind,
                    handleHit.marker.id,
                    GroupScene.HvacSelectionMode.REPLACE
                )
                hvacDrag = HvacDragState(
                    group = architectureGroup,
                    marker = handleHit.marker,
                    movingWorld = Vector3(handleHit.point)
                )
                status.message = "Drag HVAC control point and release to update."
                return true
            }
            val hotspotHit = pickHvacConstructionHotspot(architectureGroup, ray, Gdx.input.x, Gdx.input.y)
            if (hotspotHit != null) {
                val selected = scene.selectHvacElement(
                    architectureGroup,
                    hotspotHit.kind,
                    hotspotHit.id,
                    hvacSelectionMode()
                )
                status.message = if (selected) {
                    "HVAC element selected."
                } else {
                    "No HVAC element selected."
                }
                return true
            }
            val hvacFaceHit = pickFaceWorld(ray, onlyHvacGenerated = true)
            val hvacEdgeHit = pickEdgeWorld(
                ray,
                Gdx.input.x,
                Gdx.input.y,
                onlyHvacGenerated = true
            )
            val hvacPointHit = when {
                hvacFaceHit != null && hvacEdgeHit != null -> {
                    if (hvacFaceHit.t <= hvacEdgeHit.t) hvacFaceHit.point else hvacEdgeHit.point
                }
                hvacFaceHit != null -> hvacFaceHit.point
                hvacEdgeHit != null -> hvacEdgeHit.point
                else -> null
            }
            if (hvacPointHit != null) {
                scene.selectHvacElementNearWorldPoint(
                    architectureGroup,
                    hvacPointHit,
                    hvacSelectionMode()
                )
                status.message = "HVAC selection updated."
                return true
            }
        }
        val allowFaceSelection = !isVoxelGroup
        val voxelHit = if (isVoxelGroup) pickVoxelWorld(ray) else null
        val faceHit = if (allowFaceSelection || architectureSelectionEnabled) {
            pickFaceWorld(ray, ignoreArchitectureGenerated = parametricSelectionEnabled)
        } else {
            null
        }
        val edgeHit = if (isVoxelGroup) null else {
            pickEdgeWorld(ray, Gdx.input.x, Gdx.input.y, ignoreArchitectureGenerated = parametricSelectionEnabled)
        }
        val dimensionHit = pickDimensionWorld(ray, Gdx.input.x, Gdx.input.y)
        val textHit = pickTextWorld(ray, Gdx.input.x, Gdx.input.y)
        val groupHit = pickGroupWorld(ray, Gdx.input.x, Gdx.input.y)
        val pickedVoxel = voxelHit != null
        val pickedFace = faceHit != null
        val pickedEdge = edgeHit != null
        val pickedDimension = dimensionHit != null
        val pickedText = textHit != null
        val pickedGroup = groupHit != null
        if (!pickedVoxel && !pickedFace && !pickedEdge && !pickedGroup && !pickedDimension && !pickedText) {
            if (architectureSelectionEnabled) {
                scene.clearArchitectureElementSelection(architectureGroup)
            }
            if (hvacSelectionEnabled) {
                scene.clearHvacElementSelection(architectureGroup)
            }
            selectingWindow = true
            windowDragActive = false
            windowStartX = Gdx.input.x
            windowStartY = Gdx.input.y
            windowEndX = windowStartX
            windowEndY = windowStartY
            pendingVolumeStart = if (valid && world != null) Vector3(world) else null
            return true
        }
        val clickType = updateClickCount()
        if (pickedVoxel && isClosest(voxelHit!!.t, faceHit?.t, edgeHit?.t, dimensionHit?.t, textHit?.t, groupHit?.t)) {
            applyVoxelSelection(voxelHit.key, selectionMode())
            status.message = if (scene.selectedVoxels(activeGroup).contains(voxelHit.key)) {
                "Voxel selected."
            } else {
                "Voxel deselected."
            }
            return true
        }
        if (clickType == 2 && pickedGroup) {
            val targetGroup = groupHit!!.group
            if (scene.enterGroup(targetGroup)) {
                scene.clearAllSelections()
                status.message = "Editing object: ${targetGroup.name}"
                return true
            }
        }
        if (allowFaceSelection && clickType >= 3) {
            clickCount = 0
            if (!pickedFace && !pickedEdge) {
                return true
            }
            val pickFace = pickedFace && (!pickedEdge || faceHit!!.t <= edgeHit!!.t)
            if (pickFace) {
                val group = scene.activeGroup().faceStore.collectConnected(faceHit!!.triangle)
                val allSelected = group.all { scene.activeGroup().faceStore.isSelected(it) }
                if (allSelected) {
                    group.forEach { scene.activeGroup().faceStore.removeSelection(it) }
                    status.message = "Connected faces deselected."
                } else {
                    group.forEach { scene.activeGroup().faceStore.addSelection(it) }
                    status.message = "Connected faces selected."
                }
            } else {
                val group = scene.activeGroup().lineStore.collectConnected(edgeHit!!.segment)
                val allSelected = group.all { scene.activeGroup().lineStore.isSelected(it) }
                if (allSelected) {
                    group.forEach { scene.activeGroup().lineStore.removeSelection(it) }
                    status.message = "Connected edges deselected."
                } else {
                    group.forEach { scene.activeGroup().lineStore.addSelection(it) }
                    status.message = "Connected edges selected."
                }
            }
            return true
        }
        if (allowFaceSelection && clickType == 2 && pickedFace) {
            val group = scene.activeGroup().faceStore.collectCoplanarConnected(faceHit!!.triangle)
            val allSelected = group.all { scene.activeGroup().faceStore.isSelected(it) }
            if (allSelected) {
                group.forEach { scene.activeGroup().faceStore.removeSelection(it) }
                status.message = "Coplanar faces deselected."
            } else {
                group.forEach { scene.activeGroup().faceStore.addSelection(it) }
                status.message = "Coplanar faces selected."
            }
            return true
        }
        if (pickedText && isClosest(textHit!!.t, faceHit?.t, edgeHit?.t, dimensionHit?.t, groupHit?.t)) {
            scene.activeGroup().textStore.toggleSelection(textHit.text)
            status.message = "Text toggled."
            return true
        }
        if (pickedDimension && isClosest(dimensionHit!!.t, faceHit?.t, edgeHit?.t, groupHit?.t)) {
            scene.activeGroup().dimensionStore.toggleSelection(dimensionHit.dimension)
            status.message = "Dimension toggled."
            return true
        }
        if (
            pickedGroup &&
            (!pickedFace || groupHit!!.t <= faceHit!!.t) &&
            (!pickedEdge || groupHit!!.t <= edgeHit!!.t) &&
            (!pickedDimension || groupHit!!.t <= dimensionHit!!.t) &&
            (!pickedText || groupHit!!.t <= textHit!!.t)
        ) {
            scene.toggleGroupSelection(groupHit!!.group)
            status.message = "Group toggled."
            return true
        }
        if (allowFaceSelection && pickedFace && pickedEdge) {
            if (faceHit!!.t <= edgeHit!!.t) {
                scene.activeGroup().faceStore.toggleSelection(faceHit.triangle)
                status.message = "Face toggled."
            } else {
                scene.activeGroup().lineStore.toggleSelection(edgeHit.segment)
                status.message = "Edge toggled."
            }
            return true
        }
        if (allowFaceSelection && pickedFace) {
            scene.activeGroup().faceStore.toggleSelection(faceHit!!.triangle)
            status.message = "Face toggled."
            return true
        }
        if (!isVoxelGroup && pickedEdge) {
            scene.activeGroup().lineStore.toggleSelection(edgeHit!!.segment)
            status.message = "Edge toggled."
            return true
        }
        return false
    }

    override fun onPointerUp(
        status: StatusModel,
        world: Vector3?,
        normal: Vector3?,
        valid: Boolean,
        button: Int
    ): Boolean {
        if (button != Input.Buttons.LEFT) {
            return false
        }
        windowPointerDown = false
        holeDrag?.let { drag ->
            val movingWorld = if (valid && world != null) Vector3(world) else Vector3(drag.movingWorld)
            val updated = scene.updateArchitectureHoleByHandle(
                group = drag.group,
                wallId = drag.wallId,
                holeId = drag.holeId,
                handleKind = drag.kind,
                targetWorld = movingWorld
            )
            holeDrag = null
            status.message = if (updated) "Hole updated." else "Hole update failed."
            return true
        }
        wallDrag?.let { drag ->
            val movingWorld = if (valid && world != null) Vector3(world) else Vector3(drag.movingWorld)
            val (startWorld, endWorld) = if (drag.draggingStart) {
                movingWorld to drag.fixedWorld
            } else {
                drag.fixedWorld to movingWorld
            }
            val updated = scene.updateArchitectureWallEndpoints(
                group = drag.group,
                id = drag.wallId,
                start = drag.group.toLocal(startWorld),
                end = drag.group.toLocal(endWorld)
            )
            wallDrag = null
            status.message = if (updated) "Wall end updated." else "Wall end update failed."
            return true
        }
        slabDrag?.let { drag ->
            val movingWorld = if (valid && world != null) Vector3(world) else Vector3(drag.movingWorld)
            val (minWorld, maxWorld) = if (drag.draggingStart) {
                movingWorld to drag.fixedWorld
            } else {
                drag.fixedWorld to movingWorld
            }
            val updated = scene.updateArchitectureSlabCorners(
                group = drag.group,
                id = drag.slabId,
                minCorner = drag.group.toLocal(minWorld),
                maxCorner = drag.group.toLocal(maxWorld)
            )
            slabDrag = null
            status.message = if (updated) "Slab handle updated." else "Slab handle update failed."
            return true
        }
        frameDrag?.let { drag ->
            val movingWorld = if (valid && world != null) Vector3(world) else Vector3(drag.movingWorld)
            val (cornerAWorld, cornerBWorld) = if (drag.draggingStart) {
                movingWorld to drag.fixedWorld
            } else {
                drag.fixedWorld to movingWorld
            }
            val updated = scene.updateArchitectureFrameCorners(
                group = drag.group,
                id = drag.frameId,
                cornerA = drag.group.toLocal(cornerAWorld),
                cornerB = drag.group.toLocal(cornerBWorld)
            )
            frameDrag = null
            status.message = if (updated) "Frame handle updated." else "Frame handle update failed."
            return true
        }
        hvacDrag?.let { drag ->
            val movingWorld = if (valid && world != null) Vector3(world) else Vector3(drag.movingWorld)
            val updated = scene.updateHvacControlPoint(
                group = drag.group,
                marker = drag.marker,
                targetWorld = movingWorld
            )
            hvacDrag = null
            status.message = if (updated) "HVAC control point updated." else "HVAC update failed."
            return true
        }
        hotspotDrag?.let { drag ->
            val movingWorld = if (valid && world != null) Vector3(world) else Vector3(drag.movingWorld)
            val updated = scene.applyHotspotDrag(
                group = drag.group,
                id = drag.hotspotId,
                targetWorld = movingWorld
            )
            hotspotDrag = null
            status.message = if (updated) "Hotspot operation applied." else "Hotspot operation failed."
            return true
        }
        if (selectingWindow) {
            windowEndX = Gdx.input.x
            windowEndY = Gdx.input.y
            val rect = if (windowDragActive) windowRectTopLeft() else null
            val hadDrag = windowDragActive
            selectingWindow = false
            windowDragActive = false
            if (rect != null) {
                val includeIntersect = windowStartX > windowEndX
                val mode = selectionMode()
                if (mode == SelectionMode.REPLACE) {
                    scene.activeGroup().faceStore.clearSelection()
                    scene.activeGroup().lineStore.clearSelection()
                    scene.activeGroup().dimensionStore.clearSelection()
                    scene.activeGroup().textStore.clearSelection()
                    scene.clearHotspotSelection(scene.activeGroup())
                    scene.clearVoxelSelection(scene.activeGroup())
                    scene.clearHvacElementSelection(scene.root)
                    scene.clearGroupSelection()
                }
                val voxelCount = if (scene.isVoxelGroup(scene.activeGroup())) {
                    selectVoxelsInWindow(rect, includeIntersect, mode)
                } else {
                    0
                }
                val architectureSelectionEnabled =
                    scene.hasArchitectureElements() && scene.activeGroup() == scene.root
                val parametricSelectionEnabled =
                    (scene.hasArchitectureElements() || scene.hasHvacElements()) && scene.activeGroup() == scene.root
                val architectureCount = if (architectureSelectionEnabled) {
                    selectArchitectureInWindow(rect, includeIntersect, mode)
                } else {
                    0
                }
                val hvacSelectionEnabled =
                    scene.hasHvacElements() && scene.activeGroup() == scene.root
                val hvacCount = if (hvacSelectionEnabled) {
                    selectHvacInWindow(rect, includeIntersect, mode)
                } else {
                    0
                }
                val hotspotCount = if (scene.isEditing()) {
                    selectHotspotsInWindow(rect, mode)
                } else {
                    0
                }
                val faces = if (scene.isVoxelGroup(scene.activeGroup())) {
                    0
                } else {
                    selectFacesInWindow(rect, includeIntersect, mode, ignoreArchitectureGenerated = parametricSelectionEnabled)
                }
                val edges = if (scene.isVoxelGroup(scene.activeGroup())) {
                    0
                } else {
                    selectEdgesInWindow(rect, includeIntersect, mode, ignoreArchitectureGenerated = parametricSelectionEnabled)
                }
                val dimensions = selectDimensionsInWindow(rect, includeIntersect, mode)
                val texts = selectTextsInWindow(rect, includeIntersect, mode)
                val groups = selectGroupsInWindow(rect, includeIntersect, mode)
                status.message =
                    "Window select | architecture $architectureCount hvac $hvacCount hotspots $hotspotCount voxels $voxelCount edges $edges faces $faces dims $dimensions texts $texts groups $groups"
                return true
            } else if (pendingVolumeStart != null) {
                selectingVolume = true
                volumeStartRaw = Vector3(pendingVolumeStart)
                volumeEndRaw = Vector3(pendingVolumeStart)
                status.message = "Volume select: pick second corner."
                pendingVolumeStart = null
                return true
            }
            pendingVolumeStart = null
            return hadDrag
        }
        return false
    }

    fun windowRect(screenWidth: Int, screenHeight: Int): WindowRect? {
        if (!windowDragActive) {
            return null
        }
        val minX = kotlin.math.min(windowStartX, windowEndX).toFloat()
        val maxX = kotlin.math.max(windowStartX, windowEndX).toFloat()
        val minY = kotlin.math.min(windowStartY, windowEndY).toFloat()
        val maxY = kotlin.math.max(windowStartY, windowEndY).toFloat()
        val bottom = screenHeight - maxY
        val top = screenHeight - minY
        val dashed = windowStartX > windowEndX
        return WindowRect(minX, bottom, maxX - minX, top - bottom, dashed)
    }

    override fun render(renderer: ShapeRenderer) {
        holeDrag?.let { drag ->
            renderer.color = com.badlogic.gdx.graphics.Color(0.2f, 0.55f, 0.95f, 1f)
            val p = drag.movingWorld
            val size = 0.15f
            renderer.line(p.x - size, p.y, p.z, p.x + size, p.y, p.z)
            renderer.line(p.x, p.y - size, p.z, p.x, p.y + size, p.z)
            renderer.line(p.x, p.y, p.z - size, p.x, p.y, p.z + size)
            return
        }
        wallDrag?.let { drag ->
            renderer.color = com.badlogic.gdx.graphics.Color(0.25f, 0.65f, 1f, 1f)
            renderer.line(drag.fixedWorld, drag.movingWorld)
            return
        }
        slabDrag?.let { drag ->
            renderer.color = com.badlogic.gdx.graphics.Color(0.2f, 0.55f, 0.95f, 1f)
            renderer.line(drag.fixedWorld, drag.movingWorld)
            return
        }
        frameDrag?.let { drag ->
            renderer.color = com.badlogic.gdx.graphics.Color(0.2f, 0.55f, 0.95f, 1f)
            renderer.line(drag.fixedWorld, drag.movingWorld)
            return
        }
        hvacDrag?.let { drag ->
            renderer.color = com.badlogic.gdx.graphics.Color(0.35f, 0.9f, 0.7f, 1f)
            renderer.line(drag.marker.center, drag.movingWorld)
            return
        }
        val bounds = volumeBounds() ?: return
        val start = bounds.first
        val end = bounds.second
        if (!selectingVolume) {
            return
        }
        val minX = kotlin.math.min(start.x, end.x)
        val minY = kotlin.math.min(start.y, end.y)
        val minZ = kotlin.math.min(start.z, end.z)
        val maxX = kotlin.math.max(start.x, end.x)
        val maxY = kotlin.math.max(start.y, end.y)
        val maxZ = kotlin.math.max(start.z, end.z)
        renderer.color = com.badlogic.gdx.graphics.Color(0.25f, 0.55f, 0.95f, 0.35f)
        drawWireBox(renderer, minX, minY, minZ, maxX, maxY, maxZ)
    }

    private data class FaceHitWorld(
        val triangle: DraftFaceStore.Triangle,
        val point: Vector3,
        val t: Float
    )

    private data class EdgeHitWorld(
        val segment: DraftLineStore.Segment,
        val point: Vector3,
        val t: Float
    )

    private data class VoxelHitWorld(
        val key: VoxelStore.Key,
        val point: Vector3,
        val t: Float
    )

    private data class DimensionHitWorld(
        val dimension: DraftDimensionStore.LinearDimension,
        val point: Vector3,
        val t: Float
    )

    private data class TextHitWorld(
        val text: DraftTextStore.TextEntity,
        val point: Vector3,
        val t: Float
    )

    private data class GroupHitWorld(
        val group: GroupScene.GroupNode,
        val point: Vector3,
        val t: Float
    )

    private fun finalizeVolumeSelection(status: StatusModel) {
        val bounds = volumeBounds() ?: return
        val group = scene.activeGroup()
        val localBounds = volumeBoundsLocal(group, bounds.first, bounds.second)
        val parametricSelectionEnabled =
            (scene.hasArchitectureElements() || scene.hasHvacElements()) && group == scene.root
        val voxelCount = if (scene.isVoxelGroup(group)) {
            selectVoxelsInVolume(localBounds.first, localBounds.second, replace = true)
        } else {
            0
        }
        val faceCount = if (scene.isVoxelGroup(group)) {
            0
        } else {
            val raw = group.faceStore.selectInVolume(localBounds.first, localBounds.second, replace = true)
            if (parametricSelectionEnabled) {
                val generated = group.faceStore.getSelected().filter { tri ->
                    scene.isGeneratedArchitectureTriangle(tri) || scene.isGeneratedHvacTriangle(tri)
                }
                generated.forEach { group.faceStore.removeSelection(it) }
                (raw - generated.size).coerceAtLeast(0)
            } else {
                raw
            }
        }
        val edgeCount = if (scene.isVoxelGroup(group)) {
            0
        } else {
            val raw = group.lineStore.selectInVolume(localBounds.first, localBounds.second, replace = true)
            if (parametricSelectionEnabled) {
                val generated = group.lineStore.getSelected().filter { segment ->
                    scene.isGeneratedArchitectureSegment(segment) || scene.isGeneratedHvacSegment(segment)
                }
                generated.forEach { group.lineStore.removeSelection(it) }
                (raw - generated.size).coerceAtLeast(0)
            } else {
                raw
            }
        }
        val groupCount = selectGroupsInVolume(bounds.first, bounds.second)
        status.message = "Volume select | voxels $voxelCount edges $edgeCount faces $faceCount groups $groupCount"
    }

    private fun pickFaceWorld(
        ray: Ray,
        ignoreArchitectureGenerated: Boolean = false,
        onlyArchitectureGenerated: Boolean = false,
        onlyHvacGenerated: Boolean = false
    ): FaceHitWorld? {
        val group = scene.activeGroup()
        val localRay = Ray(group.toLocal(ray.origin), group.vectorToLocal(ray.direction).nor())
        if (!ignoreArchitectureGenerated && !onlyArchitectureGenerated && !onlyHvacGenerated) {
            val hit = group.faceStore.pickTriangle(localRay) ?: return null
            val worldPoint = group.toWorld(hit.point)
            val t = Vector3(worldPoint).sub(ray.origin).dot(ray.direction)
            return FaceHitWorld(hit.triangle, worldPoint, t)
        }
        var best: FaceHitWorld? = null
        group.faceStore.getTriangles().forEach { triangle ->
            val isGenerated = scene.isGeneratedArchitectureTriangle(triangle) || scene.isGeneratedHvacTriangle(triangle)
            if (ignoreArchitectureGenerated && isGenerated) {
                return@forEach
            }
            if (onlyArchitectureGenerated && !scene.isGeneratedArchitectureTriangle(triangle)) {
                return@forEach
            }
            if (onlyHvacGenerated && !scene.isGeneratedHvacTriangle(triangle)) {
                return@forEach
            }
            val hit = intersectRayTriangleLocal(localRay, triangle) ?: return@forEach
            val worldPoint = group.toWorld(hit.point)
            val t = Vector3(worldPoint).sub(ray.origin).dot(ray.direction)
            if (t < 0f) {
                return@forEach
            }
            if (best == null || t < best!!.t) {
                best = FaceHitWorld(triangle, worldPoint, t)
            }
        }
        return best
    }

    private fun pickVoxelWorld(ray: Ray): VoxelHitWorld? {
        val group = scene.activeGroup()
        val store = group.voxelStore ?: return null
        val localOrigin = group.toLocal(ray.origin)
        val localDir = group.vectorToLocal(ray.direction).nor()
        var best: VoxelHitWorld? = null
        store.all().forEach { voxel ->
            val min = Vector3(voxel.x.toFloat(), voxel.y.toFloat(), voxel.z.toFloat())
            val max = Vector3((voxel.x + 1).toFloat(), (voxel.y + 1).toFloat(), (voxel.z + 1).toFloat())
            val tLocal = rayAabbIntersectionT(localOrigin, localDir, min, max) ?: return@forEach
            val localPoint = Vector3(localOrigin).mulAdd(localDir, tLocal)
            val worldPoint = group.toWorld(localPoint)
            val worldT = Vector3(worldPoint).sub(ray.origin).dot(ray.direction)
            if (worldT < 0f) {
                return@forEach
            }
            if (best == null || worldT < best!!.t) {
                best = VoxelHitWorld(
                    key = VoxelStore.Key(voxel.x, voxel.y, voxel.z),
                    point = worldPoint,
                    t = worldT
                )
            }
        }
        return best
    }

    private fun pickEdgeWorld(
        ray: Ray,
        screenX: Int,
        screenY: Int,
        maxPixels: Float = 12f,
        ignoreArchitectureGenerated: Boolean = false,
        onlyArchitectureGenerated: Boolean = false,
        onlyHvacGenerated: Boolean = false
    ): EdgeHitWorld? {
        val group = scene.activeGroup()
        var best: EdgeHitWorld? = null
        group.lineStore.getSegments().forEach { segment ->
            val isGenerated = scene.isGeneratedArchitectureSegment(segment) || scene.isGeneratedHvacSegment(segment)
            if (ignoreArchitectureGenerated && isGenerated) {
                return@forEach
            }
            if (onlyArchitectureGenerated && !scene.isGeneratedArchitectureSegment(segment)) {
                return@forEach
            }
            if (onlyHvacGenerated && !scene.isGeneratedHvacSegment(segment)) {
                return@forEach
            }
            val a = group.toWorld(segment.start)
            val b = group.toWorld(segment.end)
            val hit = closestRaySegment(ray.origin, ray.direction, a, b) ?: return@forEach
            val screenDist = screenDistance(hit.point, screenX, screenY)
            if (screenDist <= maxPixels) {
                if (best == null || hit.t < best!!.t) {
                    best = EdgeHitWorld(segment, hit.point, hit.t)
                }
            }
        }
        return best
    }

    private fun pickDimensionWorld(ray: Ray, screenX: Int, screenY: Int, maxPixels: Float = 12f): DimensionHitWorld? {
        val group = scene.activeGroup()
        var best: DimensionHitWorld? = null
        group.dimensionStore.getDimensions().forEach { dimension ->
            val start = group.toWorld(dimension.start)
            val end = group.toWorld(dimension.end)
            val offset = group.toWorld(dimension.offset)
            val (lineStart, lineEnd) = DimensionMath.computeOffsetLine(start, end, offset)
            val hit = closestRaySegment(ray.origin, ray.direction, lineStart, lineEnd) ?: return@forEach
            val screenDist = screenDistance(hit.point, screenX, screenY)
            if (screenDist <= maxPixels) {
                if (best == null || hit.t < best!!.t) {
                    best = DimensionHitWorld(dimension, hit.point, hit.t)
                }
            }
        }
        return best
    }

    private fun pickTextWorld(ray: Ray, screenX: Int, screenY: Int, maxPixels: Float = 12f): TextHitWorld? {
        val group = scene.activeGroup()
        var best: TextHitWorld? = null
        group.textStore.getTexts().forEach { text ->
            val world = group.toWorld(text.position)
            val screenDist = screenDistance(world, screenX, screenY)
            if (screenDist <= maxPixels) {
                val t = Vector3(world).sub(ray.origin).dot(ray.direction)
                if (best == null || t < best!!.t) {
                    best = TextHitWorld(text, world, t)
                }
            }
        }
        return best
    }

    private fun pickGroupWorld(ray: Ray, screenX: Int, screenY: Int, maxPixels: Float = 12f): GroupHitWorld? {
        var best: GroupHitWorld? = null
        val candidates = scene.groupsInActiveContext()
        candidates.forEach { group ->
            val localRay = Ray(group.toLocal(ray.origin), group.vectorToLocal(ray.direction).nor())
            var bestForGroup: GroupHitWorld? = null
            val faceHit = group.faceStore.pickTriangle(localRay)
            if (faceHit != null) {
                val worldPoint = group.toWorld(faceHit.point)
                val t = Vector3(worldPoint).sub(ray.origin).dot(ray.direction)
                bestForGroup = GroupHitWorld(group, worldPoint, t)
            }
            if (bestForGroup == null && group.faceStore.getTriangles().isEmpty()) {
                group.lineStore.getSegments().forEach { segment ->
                    val a = group.toWorld(segment.start)
                    val b = group.toWorld(segment.end)
                    val hit = closestRaySegment(ray.origin, ray.direction, a, b) ?: return@forEach
                    val screenDist = screenDistance(hit.point, screenX, screenY)
                    if (screenDist <= maxPixels) {
                        if (bestForGroup == null || hit.t < bestForGroup!!.t) {
                            bestForGroup = GroupHitWorld(group, hit.point, hit.t)
                        }
                    }
                }
            }
            if (bestForGroup != null) {
                if (best == null || bestForGroup!!.t < best!!.t) {
                    best = bestForGroup
                }
            }
        }
        return best
    }

    private fun applyVoxelSelection(key: VoxelStore.Key, mode: SelectionMode) {
        val group = scene.activeGroup()
        if (!scene.isVoxelGroup(group)) {
            return
        }
        when (mode) {
            SelectionMode.REPLACE -> {
                group.faceStore.clearSelection()
                group.lineStore.clearSelection()
                group.dimensionStore.clearSelection()
                group.textStore.clearSelection()
                scene.clearGroupSelection()
                scene.replaceVoxelSelection(group, listOf(key))
            }
            SelectionMode.ADD -> {
                scene.addVoxelSelection(group, key)
            }
            SelectionMode.REMOVE -> {
                scene.removeVoxelSelection(group, key)
            }
        }
    }

    private fun selectVoxelsInWindow(rect: WindowRectTopLeft, includeIntersect: Boolean, mode: SelectionMode): Int {
        val group = scene.activeGroup()
        val store = group.voxelStore ?: return 0
        var count = 0
        val selectedKeys = mutableListOf<VoxelStore.Key>()
        store.all().forEach { voxel ->
            val key = VoxelStore.Key(voxel.x, voxel.y, voxel.z)
            val screenBounds = voxelBoundsToScreen(group, key) ?: return@forEach
            val matches = if (!includeIntersect) {
                screenBounds.minX >= rect.minX &&
                    screenBounds.maxX <= rect.maxX &&
                    screenBounds.minY >= rect.minY &&
                    screenBounds.maxY <= rect.maxY
            } else {
                rect.minX <= screenBounds.maxX &&
                    rect.maxX >= screenBounds.minX &&
                    rect.minY <= screenBounds.maxY &&
                    rect.maxY >= screenBounds.minY
            }
            if (!matches) {
                return@forEach
            }
            when (mode) {
                SelectionMode.REPLACE -> {
                    selectedKeys.add(key)
                }
                SelectionMode.ADD -> {
                    if (scene.addVoxelSelection(group, key)) {
                        count++
                    }
                }
                SelectionMode.REMOVE -> {
                    if (scene.removeVoxelSelection(group, key)) {
                        count++
                    }
                }
            }
        }
        if (mode == SelectionMode.REPLACE) {
            count = scene.replaceVoxelSelection(group, selectedKeys)
        }
        return count
    }

    private fun selectVoxelsInVolume(minLocal: Vector3, maxLocal: Vector3, replace: Boolean): Int {
        val group = scene.activeGroup()
        val store = group.voxelStore ?: return 0
        val minX = kotlin.math.floor(kotlin.math.min(minLocal.x, maxLocal.x).toDouble()).toInt()
        val minY = kotlin.math.floor(kotlin.math.min(minLocal.y, maxLocal.y).toDouble()).toInt()
        val minZ = kotlin.math.floor(kotlin.math.min(minLocal.z, maxLocal.z).toDouble()).toInt()
        val maxX = kotlin.math.floor(kotlin.math.max(minLocal.x, maxLocal.x).toDouble()).toInt()
        val maxY = kotlin.math.floor(kotlin.math.max(minLocal.y, maxLocal.y).toDouble()).toInt()
        val maxZ = kotlin.math.floor(kotlin.math.max(minLocal.z, maxLocal.z).toDouble()).toInt()
        val keys = mutableListOf<VoxelStore.Key>()
        store.all().forEach { voxel ->
            if (voxel.x in minX..maxX && voxel.y in minY..maxY && voxel.z in minZ..maxZ) {
                keys.add(VoxelStore.Key(voxel.x, voxel.y, voxel.z))
            }
        }
        if (!replace) {
            var changed = 0
            keys.forEach { key ->
                if (scene.addVoxelSelection(group, key)) {
                    changed++
                }
            }
            return changed
        }
        return scene.replaceVoxelSelection(group, keys)
    }

    private fun voxelBoundsToScreen(group: GroupScene.GroupNode, key: VoxelStore.Key): ScreenBounds? {
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
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        corners.forEach { corner ->
            val world = group.toWorld(corner)
            val projected = cameraProvider().project(world)
            val x = projected.x
            val y = Gdx.graphics.height - projected.y
            minX = kotlin.math.min(minX, x)
            maxX = kotlin.math.max(maxX, x)
            minY = kotlin.math.min(minY, y)
            maxY = kotlin.math.max(maxY, y)
        }
        if (minX == Float.POSITIVE_INFINITY) {
            return null
        }
        return ScreenBounds(minX, maxX, minY, maxY)
    }

    private fun selectGroupsInWindow(rect: WindowRectTopLeft, includeIntersect: Boolean, mode: SelectionMode): Int {
        var count = 0
        val groups = scene.groupsInActiveContext()
        groups.forEach { group ->
            val bounds = group.worldBounds() ?: return@forEach
            val screenBounds = boundsToScreen(bounds) ?: return@forEach
            val matches = if (!includeIntersect) {
                screenBounds.minX >= rect.minX &&
                    screenBounds.maxX <= rect.maxX &&
                    screenBounds.minY >= rect.minY &&
                    screenBounds.maxY <= rect.maxY
            } else {
                rect.minX <= screenBounds.maxX &&
                    rect.maxX >= screenBounds.minX &&
                    rect.minY <= screenBounds.maxY &&
                    rect.maxY >= screenBounds.minY
            }
            if (matches) {
                when (mode) {
                    SelectionMode.ADD, SelectionMode.REPLACE -> {
                        if (scene.addGroupSelection(group)) {
                            count++
                        }
                    }
                    SelectionMode.REMOVE -> {
                        if (scene.selectedGroups().contains(group)) {
                            scene.removeGroupSelection(group)
                            count++
                        }
                    }
                }
            }
        }
        return count
    }

    private fun selectGroupsInVolume(min: Vector3, max: Vector3): Int {
        scene.clearGroupSelection()
        var count = 0
        scene.groupsInActiveContext().forEach { group ->
            val bounds = group.worldBounds() ?: return@forEach
            if (aabbIntersects(bounds.min, bounds.max, min, max)) {
                if (scene.addGroupSelection(group)) {
                    count++
                }
            }
        }
        return count
    }

    private data class ScreenBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

    private fun boundsToScreen(bounds: com.badlogic.gdx.math.collision.BoundingBox): ScreenBounds? {
        val corners = arrayOf(
            Vector3(bounds.min.x, bounds.min.y, bounds.min.z),
            Vector3(bounds.min.x, bounds.min.y, bounds.max.z),
            Vector3(bounds.min.x, bounds.max.y, bounds.min.z),
            Vector3(bounds.min.x, bounds.max.y, bounds.max.z),
            Vector3(bounds.max.x, bounds.min.y, bounds.min.z),
            Vector3(bounds.max.x, bounds.min.y, bounds.max.z),
            Vector3(bounds.max.x, bounds.max.y, bounds.min.z),
            Vector3(bounds.max.x, bounds.max.y, bounds.max.z)
        )
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        corners.forEach { corner ->
            val projected = cameraProvider().project(Vector3(corner))
            val x = projected.x
            val y = Gdx.graphics.height - projected.y
            minX = kotlin.math.min(minX, x)
            maxX = kotlin.math.max(maxX, x)
            minY = kotlin.math.min(minY, y)
            maxY = kotlin.math.max(maxY, y)
        }
        if (minX == Float.POSITIVE_INFINITY) {
            return null
        }
        return ScreenBounds(minX, maxX, minY, maxY)
    }

    private fun aabbIntersects(aMin: Vector3, aMax: Vector3, bMin: Vector3, bMax: Vector3): Boolean {
        return aMin.x <= bMax.x && aMax.x >= bMin.x &&
            aMin.y <= bMax.y && aMax.y >= bMin.y &&
            aMin.z <= bMax.z && aMax.z >= bMin.z
    }

    private data class RaySegmentHit(val point: Vector3, val t: Float)

    private data class RayTriangleHit(val point: Vector3, val t: Float)

    private fun closestRaySegment(rayOrigin: Vector3, rayDir: Vector3, a: Vector3, b: Vector3): RaySegmentHit? {
        val dir = Vector3(rayDir).nor()
        val e = Vector3(b).sub(a)
        val r = Vector3(rayOrigin).sub(a)
        val aDot = dir.dot(dir)
        val eDot = e.dot(e)
        val f = dir.dot(e)
        val c = dir.dot(r)
        val g = e.dot(r)
        val denom = aDot * eDot - f * f
        var t: Float
        var s: Float
        if (kotlin.math.abs(denom) > 1e-6f) {
            t = (f * g - eDot * c) / denom
            s = (aDot * g - f * c) / denom
            s = s.coerceIn(0f, 1f)
            t = (-c + f * s) / aDot
        } else {
            s = (g / eDot).coerceIn(0f, 1f)
            t = (-c + f * s) / aDot
        }
        if (t <= 0f) {
            t = 0f
            s = (g / eDot).coerceIn(0f, 1f)
        }
        val pointOnRay = Vector3(rayOrigin).mulAdd(dir, t)
        val pointOnSeg = Vector3(a).mulAdd(e, s)
        return RaySegmentHit(pointOnSeg, t)
    }

    private fun intersectRayTriangleLocal(ray: Ray, triangle: DraftFaceStore.Triangle): RayTriangleHit? {
        val edge1 = Vector3(triangle.b).sub(triangle.a)
        val edge2 = Vector3(triangle.c).sub(triangle.a)
        val pvec = Vector3(ray.direction).crs(edge2)
        val det = edge1.dot(pvec)
        if (kotlin.math.abs(det) < 1e-6f) {
            return null
        }
        val invDet = 1f / det
        val tvec = Vector3(ray.origin).sub(triangle.a)
        val u = tvec.dot(pvec) * invDet
        if (u < 0f || u > 1f) {
            return null
        }
        val qvec = Vector3(tvec).crs(edge1)
        val v = ray.direction.dot(qvec) * invDet
        if (v < 0f || u + v > 1f) {
            return null
        }
        val t = edge2.dot(qvec) * invDet
        if (t <= 1e-6f) {
            return null
        }
        val point = Vector3(ray.origin).mulAdd(ray.direction, t)
        return RayTriangleHit(point, t)
    }

    private fun rayAabbIntersectionT(origin: Vector3, direction: Vector3, min: Vector3, max: Vector3): Float? {
        var tMin = Float.NEGATIVE_INFINITY
        var tMax = Float.POSITIVE_INFINITY
        for (axis in 0..2) {
            val o = when (axis) {
                0 -> origin.x
                1 -> origin.y
                else -> origin.z
            }
            val d = when (axis) {
                0 -> direction.x
                1 -> direction.y
                else -> direction.z
            }
            val mn = when (axis) {
                0 -> min.x
                1 -> min.y
                else -> min.z
            }
            val mx = when (axis) {
                0 -> max.x
                1 -> max.y
                else -> max.z
            }
            if (kotlin.math.abs(d) <= 1e-6f) {
                if (o < mn || o > mx) {
                    return null
                }
                continue
            }
            var t1 = (mn - o) / d
            var t2 = (mx - o) / d
            if (t1 > t2) {
                val tmp = t1
                t1 = t2
                t2 = tmp
            }
            tMin = kotlin.math.max(tMin, t1)
            tMax = kotlin.math.min(tMax, t2)
            if (tMax < tMin) {
                return null
            }
        }
        if (tMax < 0f) {
            return null
        }
        return if (tMin >= 0f) tMin else tMax
    }

    private fun screenDistance(world: Vector3, screenX: Int, screenY: Int): Float {
        val projected = cameraProvider().project(Vector3(world))
        val dx = projected.x - screenX
        val dy = (Gdx.graphics.height - projected.y) - screenY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun isClosest(target: Float, vararg others: Float?): Boolean {
        others.filterNotNull().forEach { value ->
            if (target > value) {
                return false
            }
        }
        return true
    }

    private fun pickArchitectureHoleHandle(
        group: GroupScene.GroupNode,
        ray: Ray,
        screenX: Int,
        screenY: Int,
        maxPixels: Float = 14f
    ): HoleHandleHit? {
        val selectedWallId = scene.selectedArchitectureElement(group)
            ?.takeIf { it.kind == ArchitectureStore.ElementKind.WALL }
            ?.id
        val markers = scene.architectureHoleHandleMarkersWorld(group, wallId = selectedWallId)
        var best: HoleHandleHit? = null
        val halfSize = 0.18f
        markers.forEach { marker ->
            val dist = screenDistance(marker.world, screenX, screenY)
            if (dist > maxPixels * 2f) {
                return@forEach
            }
            val min = Vector3(marker.world.x - halfSize, marker.world.y - halfSize, marker.world.z - halfSize)
            val max = Vector3(marker.world.x + halfSize, marker.world.y + halfSize, marker.world.z + halfSize)
            val t = rayAabbIntersectionT(ray.origin, ray.direction, min, max)
                ?: Vector3(marker.world).sub(ray.origin).dot(ray.direction)
            if (t < 0f) {
                return@forEach
            }
            if (best == null || t < best!!.t) {
                best = HoleHandleHit(
                    wallId = marker.wallId,
                    holeId = marker.holeId,
                    kind = marker.kind,
                    point = Vector3(marker.world),
                    t = t
                )
            }
        }
        return best
    }

    private fun pickArchitectureConstructionHotspot(
        group: GroupScene.GroupNode,
        ray: Ray,
        screenX: Int,
        screenY: Int,
        maxPixels: Float = 14f
    ): ArchitectureHotspotHit? {
        val markers = mutableListOf<GroupScene.ArchitectureElementHotspotMarker>()
        markers.addAll(scene.architectureSlabConstructionHotspotsWorld(group))
        markers.addAll(scene.architectureFrameConstructionHotspotsWorld(group))
        markers.addAll(scene.architectureHoleConstructionHotspotsWorld(group))
        var best: ArchitectureHotspotHit? = null
        markers.forEach { marker ->
            val dist = screenDistance(marker.world, screenX, screenY)
            if (dist > maxPixels) {
                return@forEach
            }
            val t = Vector3(marker.world).sub(ray.origin).dot(ray.direction)
            if (t < 0f) {
                return@forEach
            }
            if (best == null || t < best!!.t) {
                best = ArchitectureHotspotHit(
                    kind = marker.kind,
                    id = marker.id,
                    point = Vector3(marker.world),
                    t = t
                )
            }
        }
        return best
    }

    private fun pickSelectedHvacHandle(
        group: GroupScene.GroupNode,
        ray: Ray,
        screenX: Int,
        screenY: Int,
        maxPixels: Float = 16f
    ): HvacHandleHit? {
        val markers = scene.hvacControlHandleMarkersWorld(group)
        var best: HvacHandleHit? = null
        markers.forEach { marker ->
            val dist = screenDistance(marker.center, screenX, screenY)
            if (dist > maxPixels * 2f) {
                return@forEach
            }
            val half = marker.halfSize
            val min = Vector3(marker.center.x - half, marker.center.y - half, marker.center.z - half)
            val max = Vector3(marker.center.x + half, marker.center.y + half, marker.center.z + half)
            val t = rayAabbIntersectionT(ray.origin, ray.direction, min, max)
                ?: Vector3(marker.center).sub(ray.origin).dot(ray.direction)
            if (t < 0f) {
                return@forEach
            }
            if (best == null || t < best!!.t) {
                best = HvacHandleHit(
                    marker = marker,
                    point = Vector3(marker.center),
                    t = t
                )
            }
        }
        return best
    }

    private fun pickHvacConstructionHotspot(
        group: GroupScene.GroupNode,
        ray: Ray,
        screenX: Int,
        screenY: Int,
        maxPixels: Float = 14f
    ): HvacHotspotHit? {
        val markers = scene.hvacConstructionHotspotsWorld(group)
        var best: HvacHotspotHit? = null
        markers.forEach { marker ->
            val dist = screenDistance(marker.world, screenX, screenY)
            if (dist > maxPixels) {
                return@forEach
            }
            val t = Vector3(marker.world).sub(ray.origin).dot(ray.direction)
            if (t < 0f) {
                return@forEach
            }
            if (best == null || t < best!!.t) {
                best = HvacHotspotHit(
                    kind = marker.kind,
                    id = marker.id,
                    point = Vector3(marker.world),
                    t = t
                )
            }
        }
        return best
    }

    private fun pickHotspotWorld(
        group: GroupScene.GroupNode,
        ray: Ray,
        screenX: Int,
        screenY: Int,
        maxPixels: Float = 12f
    ): HotspotHit? {
        val markers = scene.hotspotMarkersWorld(group)
        var best: HotspotHit? = null
        markers.forEach { marker ->
            val dist = screenDistance(marker.world, screenX, screenY)
            if (dist > maxPixels) {
                return@forEach
            }
            val t = Vector3(marker.world).sub(ray.origin).dot(ray.direction)
            if (t < 0f) {
                return@forEach
            }
            if (best == null || t < best!!.t) {
                best = HotspotHit(
                    id = marker.id,
                    point = Vector3(marker.world),
                    t = t
                )
            }
        }
        return best
    }

    private fun pickSelectedWallEndpoint(
        group: GroupScene.GroupNode,
        ray: Ray
    ): WallEndpointHit? {
        val markers = scene.architectureWallEndpointHandleMarkersWorld(group)
        if (markers.isEmpty()) {
            return null
        }
        var bestMarker: GroupScene.WallEndpointHandleMarker? = null
        var bestT = Float.POSITIVE_INFINITY
        markers.forEach { marker ->
            val t = rayPlaneIntersectionT(ray, marker.center.y) ?: return@forEach
            if (t < 0f || t >= bestT) {
                return@forEach
            }
            val hit = Vector3(ray.origin).mulAdd(ray.direction, t)
            if (kotlin.math.abs(hit.x - marker.center.x) <= marker.halfSize &&
                kotlin.math.abs(hit.z - marker.center.z) <= marker.halfSize
            ) {
                bestMarker = marker
                bestT = t
            }
        }
        val marker = bestMarker ?: return null
        val wall = scene.architectureWallById(group, marker.wallId) ?: return null
        val startWorld = group.toWorld(wall.start)
        val endWorld = group.toWorld(wall.end)
        return when (marker.draggingStart) {
            true -> {
            WallEndpointHit(
                wallId = wall.id,
                draggingStart = true,
                fixedWorld = Vector3(endWorld),
                movingWorld = Vector3(startWorld)
            )
            }
            false -> {
            WallEndpointHit(
                wallId = wall.id,
                draggingStart = false,
                fixedWorld = Vector3(startWorld),
                movingWorld = Vector3(endWorld)
            )
            }
            else -> null
        }
    }

    private fun pickSelectedSlabEndpoint(
        group: GroupScene.GroupNode,
        ray: Ray
    ): SlabEndpointHit? {
        val marker = pickArchitectureEndpointMarker(ray, scene.architectureSlabEndpointHandleMarkersWorld(group)) ?: return null
        val slab = scene.architectureSlabById(group, marker.id) ?: return null
        val minWorld = group.toWorld(slab.min)
        val maxWorld = group.toWorld(slab.max)
        return if (marker.draggingStart) {
            SlabEndpointHit(
                slabId = slab.id,
                draggingStart = true,
                fixedWorld = Vector3(maxWorld),
                movingWorld = Vector3(minWorld)
            )
        } else {
            SlabEndpointHit(
                slabId = slab.id,
                draggingStart = false,
                fixedWorld = Vector3(minWorld),
                movingWorld = Vector3(maxWorld)
            )
        }
    }

    private fun pickSelectedFrameEndpoint(
        group: GroupScene.GroupNode,
        ray: Ray
    ): FrameEndpointHit? {
        val marker = pickArchitectureEndpointMarker(ray, scene.architectureFrameEndpointHandleMarkersWorld(group)) ?: return null
        val frame = scene.architectureFrameById(group, marker.id) ?: return null
        val cornerAWorld = group.toWorld(frame.cornerA)
        val cornerBWorld = group.toWorld(frame.cornerB)
        return if (marker.draggingStart) {
            FrameEndpointHit(
                frameId = frame.id,
                draggingStart = true,
                fixedWorld = Vector3(cornerBWorld),
                movingWorld = Vector3(cornerAWorld)
            )
        } else {
            FrameEndpointHit(
                frameId = frame.id,
                draggingStart = false,
                fixedWorld = Vector3(cornerAWorld),
                movingWorld = Vector3(cornerBWorld)
            )
        }
    }

    private fun pickArchitectureEndpointMarker(
        ray: Ray,
        markers: List<GroupScene.ArchitectureEndpointHandleMarker>
    ): GroupScene.ArchitectureEndpointHandleMarker? {
        var best: GroupScene.ArchitectureEndpointHandleMarker? = null
        var bestT = Float.POSITIVE_INFINITY
        markers.forEach { marker ->
            val half = marker.halfSize
            val min = Vector3(marker.center.x - half, marker.center.y - half, marker.center.z - half)
            val max = Vector3(marker.center.x + half, marker.center.y + half, marker.center.z + half)
            val t = rayAabbIntersectionT(ray.origin, ray.direction, min, max) ?: return@forEach
            if (t < 0f || t >= bestT) {
                return@forEach
            }
            bestT = t
            best = marker
        }
        return best
    }

    private fun rayPlaneIntersectionT(ray: Ray, y: Float): Float? {
        val dirY = ray.direction.y
        if (kotlin.math.abs(dirY) <= 1e-6f) {
            return null
        }
        return (y - ray.origin.y) / dirY
    }

    private data class WindowRectTopLeft(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float
    )

    private fun windowRectTopLeft(): WindowRectTopLeft? {
        if (!windowDragActive) {
            return null
        }
        val minX = kotlin.math.min(windowStartX, windowEndX).toFloat()
        val maxX = kotlin.math.max(windowStartX, windowEndX).toFloat()
        val minY = kotlin.math.min(windowStartY, windowEndY).toFloat()
        val maxY = kotlin.math.max(windowStartY, windowEndY).toFloat()
        return WindowRectTopLeft(minX, maxX, minY, maxY)
    }

    private fun selectFacesInWindow(
        rect: WindowRectTopLeft,
        includeIntersect: Boolean,
        mode: SelectionMode,
        ignoreArchitectureGenerated: Boolean = false
    ): Int {
        var count = 0
        scene.activeGroup().faceStore.getTriangles().forEach { tri ->
            if (ignoreArchitectureGenerated && (scene.isGeneratedArchitectureTriangle(tri) || scene.isGeneratedHvacTriangle(tri))) {
                return@forEach
            }
            val a = projectToScreen(tri.a)
            val b = projectToScreen(tri.b)
            val c = projectToScreen(tri.c)
            val matches = if (!includeIntersect) {
                pointInRect(a, rect) && pointInRect(b, rect) && pointInRect(c, rect)
            } else {
                faceIntersectsRect(a, b, c, rect)
            }
            if (matches) {
                when (mode) {
                    SelectionMode.ADD, SelectionMode.REPLACE -> {
                        if (scene.activeGroup().faceStore.addSelection(tri)) {
                            count++
                        }
                    }
                    SelectionMode.REMOVE -> {
                        if (scene.activeGroup().faceStore.isSelected(tri)) {
                            scene.activeGroup().faceStore.removeSelection(tri)
                            count++
                        }
                    }
                }
            }
        }
        return count
    }

    private fun selectEdgesInWindow(
        rect: WindowRectTopLeft,
        includeIntersect: Boolean,
        mode: SelectionMode,
        ignoreArchitectureGenerated: Boolean = false
    ): Int {
        var count = 0
        scene.activeGroup().lineStore.getSegments().forEach { segment ->
            if (ignoreArchitectureGenerated && (scene.isGeneratedArchitectureSegment(segment) || scene.isGeneratedHvacSegment(segment))) {
                return@forEach
            }
            val a = projectToScreen(segment.start)
            val b = projectToScreen(segment.end)
            val matches = if (!includeIntersect) {
                pointInRect(a, rect) && pointInRect(b, rect)
            } else {
                segmentIntersectsRect(a, b, rect)
            }
            if (matches) {
                when (mode) {
                    SelectionMode.ADD, SelectionMode.REPLACE -> {
                        if (scene.activeGroup().lineStore.addSelection(segment)) {
                            count++
                        }
                    }
                    SelectionMode.REMOVE -> {
                        if (scene.activeGroup().lineStore.isSelected(segment)) {
                            scene.activeGroup().lineStore.removeSelection(segment)
                            count++
                        }
                    }
                }
            }
        }
        return count
    }

    private fun selectArchitectureInWindow(
        rect: WindowRectTopLeft,
        includeIntersect: Boolean,
        mode: SelectionMode
    ): Int {
        val group = scene.activeGroup()
        if (group != scene.root) {
            return 0
        }

        val samplePoints = mutableListOf<Vector3>()
        group.faceStore.getTriangles().forEach { triangle ->
            if (!scene.isGeneratedArchitectureTriangle(triangle)) {
                return@forEach
            }
            val a = projectToScreen(triangle.a)
            val b = projectToScreen(triangle.b)
            val c = projectToScreen(triangle.c)
            val matches = if (!includeIntersect) {
                pointInRect(a, rect) && pointInRect(b, rect) && pointInRect(c, rect)
            } else {
                faceIntersectsRect(a, b, c, rect)
            }
            if (matches) {
                val centroid = Vector3(triangle.a).add(triangle.b).add(triangle.c).scl(1f / 3f)
                samplePoints.add(group.toWorld(centroid))
            }
        }
        group.lineStore.getSegments().forEach { segment ->
            if (!scene.isGeneratedArchitectureSegment(segment)) {
                return@forEach
            }
            val a = projectToScreen(segment.start)
            val b = projectToScreen(segment.end)
            val matches = if (!includeIntersect) {
                pointInRect(a, rect) && pointInRect(b, rect)
            } else {
                segmentIntersectsRect(a, b, rect)
            }
            if (matches) {
                val midpoint = Vector3(segment.start).lerp(segment.end, 0.5f)
                samplePoints.add(group.toWorld(midpoint))
            }
        }

        val root = scene.root
        val before = scene.selectedArchitectureElements(root).toSet()
        if (mode == SelectionMode.REPLACE) {
            scene.clearArchitectureElementSelection(root)
        }
        val opMode = if (mode == SelectionMode.REMOVE) {
            GroupScene.ArchitectureSelectionMode.REMOVE
        } else {
            GroupScene.ArchitectureSelectionMode.ADD
        }
        samplePoints.forEach { point ->
            scene.selectArchitectureElementNearWorldPoint(root, point, opMode)
        }
        val after = scene.selectedArchitectureElements(root).toSet()
        return when (mode) {
            SelectionMode.REMOVE -> (before.size - after.size).coerceAtLeast(0)
            SelectionMode.ADD -> (after.size - before.size).coerceAtLeast(0)
            SelectionMode.REPLACE -> after.size
        }
    }

    private fun selectHvacInWindow(
        rect: WindowRectTopLeft,
        includeIntersect: Boolean,
        mode: SelectionMode
    ): Int {
        val group = scene.activeGroup()
        if (group != scene.root) {
            return 0
        }

        val samplePoints = mutableListOf<Vector3>()
        group.faceStore.getTriangles().forEach { triangle ->
            if (!scene.isGeneratedHvacTriangle(triangle)) {
                return@forEach
            }
            val a = projectToScreen(triangle.a)
            val b = projectToScreen(triangle.b)
            val c = projectToScreen(triangle.c)
            val matches = if (!includeIntersect) {
                pointInRect(a, rect) && pointInRect(b, rect) && pointInRect(c, rect)
            } else {
                faceIntersectsRect(a, b, c, rect)
            }
            if (matches) {
                val centroid = Vector3(triangle.a).add(triangle.b).add(triangle.c).scl(1f / 3f)
                samplePoints.add(group.toWorld(centroid))
            }
        }
        group.lineStore.getSegments().forEach { segment ->
            if (!scene.isGeneratedHvacSegment(segment)) {
                return@forEach
            }
            val a = projectToScreen(segment.start)
            val b = projectToScreen(segment.end)
            val matches = if (!includeIntersect) {
                pointInRect(a, rect) && pointInRect(b, rect)
            } else {
                segmentIntersectsRect(a, b, rect)
            }
            if (matches) {
                val midpoint = Vector3(segment.start).lerp(segment.end, 0.5f)
                samplePoints.add(group.toWorld(midpoint))
            }
        }

        val root = scene.root
        val before = scene.selectedHvacElements(root).toSet()
        if (mode == SelectionMode.REPLACE) {
            scene.clearHvacElementSelection(root)
        }
        val opMode = if (mode == SelectionMode.REMOVE) {
            GroupScene.HvacSelectionMode.REMOVE
        } else {
            GroupScene.HvacSelectionMode.ADD
        }
        samplePoints.forEach { point ->
            scene.selectHvacElementNearWorldPoint(root, point, opMode)
        }
        val after = scene.selectedHvacElements(root).toSet()
        return when (mode) {
            SelectionMode.REMOVE -> (before.size - after.size).coerceAtLeast(0)
            SelectionMode.ADD -> (after.size - before.size).coerceAtLeast(0)
            SelectionMode.REPLACE -> after.size
        }
    }

    private fun selectHotspotsInWindow(rect: WindowRectTopLeft, mode: SelectionMode): Int {
        val group = scene.activeGroup()
        val markers = scene.hotspotMarkersWorld(group)
        var count = 0
        markers.forEach { marker ->
            val point = projectWorldToScreen(marker.world)
            if (!pointInRect(point, rect)) {
                return@forEach
            }
            when (mode) {
                SelectionMode.REPLACE, SelectionMode.ADD -> {
                    if (scene.selectHotspot(group, marker.id, GroupScene.HotspotSelectionMode.ADD)) {
                        count++
                    }
                }
                SelectionMode.REMOVE -> {
                    if (scene.selectHotspot(group, marker.id, GroupScene.HotspotSelectionMode.REMOVE)) {
                        count++
                    }
                }
            }
        }
        return count
    }

    private fun selectDimensionsInWindow(rect: WindowRectTopLeft, includeIntersect: Boolean, mode: SelectionMode): Int {
        var count = 0
        val group = scene.activeGroup()
        group.dimensionStore.getDimensions().forEach { dimension ->
            val start = group.toWorld(dimension.start)
            val end = group.toWorld(dimension.end)
            val offset = group.toWorld(dimension.offset)
            val (lineStart, lineEnd) = DimensionMath.computeOffsetLine(start, end, offset)
            val a = projectWorldToScreen(lineStart)
            val b = projectWorldToScreen(lineEnd)
            val matches = if (!includeIntersect) {
                pointInRect(a, rect) && pointInRect(b, rect)
            } else {
                segmentIntersectsRect(a, b, rect)
            }
            if (matches) {
                when (mode) {
                    SelectionMode.ADD, SelectionMode.REPLACE -> {
                        if (group.dimensionStore.addSelection(dimension)) {
                            count++
                        }
                    }
                    SelectionMode.REMOVE -> {
                        if (group.dimensionStore.isSelected(dimension)) {
                            group.dimensionStore.removeSelection(dimension)
                            count++
                        }
                    }
                }
            }
        }
        return count
    }

    private fun selectTextsInWindow(rect: WindowRectTopLeft, includeIntersect: Boolean, mode: SelectionMode): Int {
        var count = 0
        val group = scene.activeGroup()
        group.textStore.getTexts().forEach { text ->
            val pos = projectWorldToScreen(group.toWorld(text.position))
            val matches = pointInRect(pos, rect)
            if (matches) {
                when (mode) {
                    SelectionMode.ADD, SelectionMode.REPLACE -> {
                        if (group.textStore.addSelection(text)) {
                            count++
                        }
                    }
                    SelectionMode.REMOVE -> {
                        if (group.textStore.isSelected(text)) {
                            group.textStore.removeSelection(text)
                            count++
                        }
                    }
                }
            }
        }
        return count
    }

    private fun projectToScreen(point: Vector3): Vector3 {
        return projectWorldToScreen(scene.activeGroup().toWorld(point))
    }

    private fun projectWorldToScreen(world: Vector3): Vector3 {
        val projected = cameraProvider().project(Vector3(world))
        projected.y = Gdx.graphics.height - projected.y
        return projected
    }

    private fun pointInRect(point: Vector3, rect: WindowRectTopLeft): Boolean {
        return point.x >= rect.minX &&
            point.x <= rect.maxX &&
            point.y >= rect.minY &&
            point.y <= rect.maxY
    }

    private fun selectionMode(): SelectionMode {
        val shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
        val ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)
        return when {
            ctrl -> SelectionMode.REMOVE
            shift -> SelectionMode.ADD
            else -> SelectionMode.REPLACE
        }
    }

    private fun architectureSelectionMode(): GroupScene.ArchitectureSelectionMode {
        return when (selectionMode()) {
            SelectionMode.REPLACE -> GroupScene.ArchitectureSelectionMode.REPLACE
            SelectionMode.ADD -> GroupScene.ArchitectureSelectionMode.ADD
            SelectionMode.REMOVE -> GroupScene.ArchitectureSelectionMode.REMOVE
        }
    }

    private fun hvacSelectionMode(): GroupScene.HvacSelectionMode {
        return when (selectionMode()) {
            SelectionMode.REPLACE -> GroupScene.HvacSelectionMode.REPLACE
            SelectionMode.ADD -> GroupScene.HvacSelectionMode.ADD
            SelectionMode.REMOVE -> GroupScene.HvacSelectionMode.REMOVE
        }
    }

    private fun hotspotSelectionMode(): GroupScene.HotspotSelectionMode {
        return when (selectionMode()) {
            SelectionMode.REPLACE -> GroupScene.HotspotSelectionMode.REPLACE
            SelectionMode.ADD -> GroupScene.HotspotSelectionMode.ADD
            SelectionMode.REMOVE -> GroupScene.HotspotSelectionMode.REMOVE
        }
    }

    private fun isAltPressed(): Boolean {
        return Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) ||
            Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)
    }

    private fun faceIntersectsRect(a: Vector3, b: Vector3, c: Vector3, rect: WindowRectTopLeft): Boolean {
        if (pointInRect(a, rect) || pointInRect(b, rect) || pointInRect(c, rect)) {
            return true
        }
        val rectPoints = rectCorners(rect)
        val a2 = Vector2(a.x, a.y)
        val b2 = Vector2(b.x, b.y)
        val c2 = Vector2(c.x, c.y)
        rectPoints.forEach { p ->
            if (pointInTriangle(p, a2, b2, c2)) {
                return true
            }
        }
        return segmentIntersectsRect(a, b, rect) ||
            segmentIntersectsRect(b, c, rect) ||
            segmentIntersectsRect(c, a, rect)
    }

    private fun segmentIntersectsRect(a: Vector3, b: Vector3, rect: WindowRectTopLeft): Boolean {
        if (pointInRect(a, rect) || pointInRect(b, rect)) {
            return true
        }
        val corners = rectCorners(rect)
        val r0 = corners[0]
        val r1 = corners[1]
        val r2 = corners[2]
        val r3 = corners[3]
        val a2 = Vector2(a.x, a.y)
        val b2 = Vector2(b.x, b.y)
        return segmentsIntersect(a2, b2, r0, r1) ||
            segmentsIntersect(a2, b2, r1, r2) ||
            segmentsIntersect(a2, b2, r2, r3) ||
            segmentsIntersect(a2, b2, r3, r0)
    }

    private fun rectCorners(rect: WindowRectTopLeft): List<Vector2> {
        val minX = rect.minX
        val maxX = rect.maxX
        val minY = rect.minY
        val maxY = rect.maxY
        return listOf(
            Vector2(minX, minY),
            Vector2(maxX, minY),
            Vector2(maxX, maxY),
            Vector2(minX, maxY)
        )
    }

    private fun pointInTriangle(p: Vector2, a: Vector2, b: Vector2, c: Vector2): Boolean {
        val v0 = Vector2(c).sub(a)
        val v1 = Vector2(b).sub(a)
        val v2 = Vector2(p).sub(a)
        val dot00 = v0.dot(v0)
        val dot01 = v0.dot(v1)
        val dot02 = v0.dot(v2)
        val dot11 = v1.dot(v1)
        val dot12 = v1.dot(v2)
        val invDen = 1f / (dot00 * dot11 - dot01 * dot01)
        val u = (dot11 * dot02 - dot01 * dot12) * invDen
        val v = (dot00 * dot12 - dot01 * dot02) * invDen
        return u >= -1e-4f && v >= -1e-4f && u + v <= 1f + 1e-4f
    }

    private fun segmentsIntersect(p1: Vector2, p2: Vector2, q1: Vector2, q2: Vector2): Boolean {
        val o1 = orientation(p1, p2, q1)
        val o2 = orientation(p1, p2, q2)
        val o3 = orientation(q1, q2, p1)
        val o4 = orientation(q1, q2, p2)
        if (o1 != o2 && o3 != o4) {
            return true
        }
        return o1 == 0 && onSegment(p1, q1, p2) ||
            o2 == 0 && onSegment(p1, q2, p2) ||
            o3 == 0 && onSegment(q1, p1, q2) ||
            o4 == 0 && onSegment(q1, p2, q2)
    }

    private fun orientation(a: Vector2, b: Vector2, c: Vector2): Int {
        val value = (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y)
        val eps = 1e-6f
        return when {
            kotlin.math.abs(value) < eps -> 0
            value > 0f -> 1
            else -> 2
        }
    }

    private fun onSegment(a: Vector2, b: Vector2, c: Vector2): Boolean {
        return b.x <= maxOf(a.x, c.x) + 1e-6f &&
            b.x + 1e-6f >= minOf(a.x, c.x) &&
            b.y <= maxOf(a.y, c.y) + 1e-6f &&
            b.y + 1e-6f >= minOf(a.y, c.y)
    }

    private fun updateClickCount(): Int {
        val now = TimeUtils.millis()
        val x = Gdx.input.x
        val y = Gdx.input.y
        val dx = x - lastClickX
        val dy = y - lastClickY
        val withinTime = (now - lastClickTime) <= doubleClickMs
        val withinDistance = (dx * dx + dy * dy) <= clickDistanceSq
        clickCount = if (withinTime && withinDistance) {
            (clickCount + 1).coerceAtMost(3)
        } else {
            1
        }
        lastClickTime = now
        lastClickX = x
        lastClickY = y
        return clickCount
    }

    private fun volumeBounds(): Pair<Vector3, Vector3>? {
        val start = volumeStartRaw ?: return null
        val end = volumeEndRaw ?: return null
        val min = Vector3(
            kotlin.math.min(start.x, end.x),
            kotlin.math.min(start.y, end.y),
            kotlin.math.min(start.z, end.z)
        )
        val max = Vector3(
            kotlin.math.max(start.x, end.x),
            kotlin.math.max(start.y, end.y),
            kotlin.math.max(start.z, end.z)
        )
        return min to max
    }

    private fun volumeBoundsLocal(
        group: GroupScene.GroupNode,
        worldMin: Vector3,
        worldMax: Vector3
    ): Pair<Vector3, Vector3> {
        val corners = arrayOf(
            Vector3(worldMin.x, worldMin.y, worldMin.z),
            Vector3(worldMin.x, worldMin.y, worldMax.z),
            Vector3(worldMin.x, worldMax.y, worldMin.z),
            Vector3(worldMin.x, worldMax.y, worldMax.z),
            Vector3(worldMax.x, worldMin.y, worldMin.z),
            Vector3(worldMax.x, worldMin.y, worldMax.z),
            Vector3(worldMax.x, worldMax.y, worldMin.z),
            Vector3(worldMax.x, worldMax.y, worldMax.z)
        )
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        corners.forEach { corner ->
            val local = group.toLocal(corner)
            minX = kotlin.math.min(minX, local.x)
            minY = kotlin.math.min(minY, local.y)
            minZ = kotlin.math.min(minZ, local.z)
            maxX = kotlin.math.max(maxX, local.x)
            maxY = kotlin.math.max(maxY, local.y)
            maxZ = kotlin.math.max(maxZ, local.z)
        }
        return Vector3(minX, minY, minZ) to Vector3(maxX, maxY, maxZ)
    }

    private fun drawWireBox(
        renderer: ShapeRenderer,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float
    ) {
        // Bottom rectangle
        renderer.line(minX, minY, minZ, maxX, minY, minZ)
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ)
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ)
        renderer.line(minX, minY, maxZ, minX, minY, minZ)
        // Top rectangle
        renderer.line(minX, maxY, minZ, maxX, maxY, minZ)
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ)
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ)
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ)
        // Vertical edges
        renderer.line(minX, minY, minZ, minX, maxY, minZ)
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ)
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ)
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ)
    }

}

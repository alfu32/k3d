/*
 * Tutorial: rectangle + rotation + surface rectangle capture
 *
 * Scenario:
 *   1) Draw rectangle on ground plane from (-5,-5) to (12,7).
 *   2) Rotate rectangle around world X axis by 30 degrees.
 *   3) Draw a 2x2 rectangle on the rotated rectangle surface.
 *
 * Inputs (all optional):
 *   MODE               : "batch" (default) | "reset" | "step"
 *   STEP               : 0..3 (used in MODE="step")
 *   OUTPUT_DIR         : output directory (default docs/tutorial/04_rectangle)
 *   OVERWRITE          : true/false (default true)
 *   HIDE_UI            : true/false (default true)
 *   CAPTURE            : true/false (default true)
 *   RECT_MIN_X         : default -5
 *   RECT_MIN_Z         : default -5
 *   RECT_MAX_X         : default 12
 *   RECT_MAX_Z         : default 7
 *   ROTATE_DEG         : default 30
 *   SMALL_SIZE         : default 2
 *   SMALL_ORIGIN_X     : default 0
 *   SMALL_ORIGIN_Z     : default 0
 *   SHOW_GROUND_AT_END : true/false (default false)
 */

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import java.util.UUID

def mode = (binding.hasVariable("MODE") ? MODE : "batch").toString().trim().toLowerCase()
def outputDirPath = (binding.hasVariable("OUTPUT_DIR") ? OUTPUT_DIR : "docs/tutorial/04_rectangle").toString()
def overwrite = binding.hasVariable("OVERWRITE") ? (OVERWRITE as boolean) : true
def hideUi = binding.hasVariable("HIDE_UI") ? (HIDE_UI as boolean) : true
def captureEnabled = binding.hasVariable("CAPTURE") ? (CAPTURE as boolean) : true
def requestedStep = binding.hasVariable("STEP") ? (STEP as int) : Integer.MAX_VALUE
float rectMinX = binding.hasVariable("RECT_MIN_X") ? (RECT_MIN_X as Number).floatValue() : -5f
float rectMinZ = binding.hasVariable("RECT_MIN_Z") ? (RECT_MIN_Z as Number).floatValue() : -5f
float rectMaxX = binding.hasVariable("RECT_MAX_X") ? (RECT_MAX_X as Number).floatValue() : 12f
float rectMaxZ = binding.hasVariable("RECT_MAX_Z") ? (RECT_MAX_Z as Number).floatValue() : 7f
float rotateDeg = binding.hasVariable("ROTATE_DEG") ? (ROTATE_DEG as Number).floatValue() : 30f
float smallSize = binding.hasVariable("SMALL_SIZE") ? (SMALL_SIZE as Number).floatValue() : 2f
float smallOriginX = binding.hasVariable("SMALL_ORIGIN_X") ? (SMALL_ORIGIN_X as Number).floatValue() : 0f
float smallOriginZ = binding.hasVariable("SMALL_ORIGIN_Z") ? (SMALL_ORIGIN_Z as Number).floatValue() : 0f
def showGroundAtEnd = binding.hasVariable("SHOW_GROUND_AT_END") ? (SHOW_GROUND_AT_END as boolean) : false

def shotNames = [
    "step-00-start.png",
    "step-01-ground-rectangle.png",
    "step-02-rectangle-rotated-x30.png",
    "step-03-surface-rectangle-2x2.png"
]

def capturePng = { String filePath ->
    if (!captureEnabled) return [ok: true, skipped: true, path: filePath]
    FileHandle out = Gdx.files.absolute(new File(filePath).absolutePath)
    FileHandle parent = out.parent()
    if (parent != null && !parent.exists()) parent.mkdirs()
    if (!overwrite && out.exists()) return [ok: true, skipped: true, path: out.file().absolutePath]

    Pixmap pix = null
    PixmapIO.PNG writer = null
    try {
        int w = Math.max(1, Gdx.graphics.backBufferWidth)
        int h = Math.max(1, Gdx.graphics.backBufferHeight)
        pix = ScreenUtils.getFrameBufferPixmap(0, 0, w, h)
        writer = new PixmapIO.PNG(Math.max(1024, (int)(w * h * 1.5f)))
        writer.setFlipY(true)
        writer.write(out, pix)
        [ok: true, skipped: false, path: out.file().absolutePath]
    } catch (Throwable t) {
        [ok: false, skipped: false, path: out.file().absolutePath, error: (t.message ?: t.class.simpleName)]
    } finally {
        try { writer?.dispose() } catch (Throwable ignored) {}
        try { pix?.dispose() } catch (Throwable ignored) {}
    }
}

def rotateAroundX = { Vector3 p, float deg ->
    double rad = Math.toRadians(deg as double)
    float c = (float) Math.cos(rad)
    float s = (float) Math.sin(rad)
    float y2 = (float) (p.y * c - p.z * s)
    float z2 = (float) (p.y * s + p.z * c)
    new Vector3(
        p.x,
        y2,
        z2
    )
}

def addRect = { group, List<Vector3> corners ->
    def lineStore = group.getLineStore()
    def faceStore = group.getFaceStore()
    for (int i = 0; i < 4; i++) {
        def a = corners[i]
        def b = corners[(i + 1) % 4]
        lineStore.addSegment(new Vector3(a), new Vector3(b), false, UUID.randomUUID().toString())
    }
    faceStore.addTriangle(new Vector3(corners[0]), new Vector3(corners[1]), new Vector3(corners[2]), UUID.randomUUID().toString())
    faceStore.addTriangle(new Vector3(corners[0]), new Vector3(corners[2]), new Vector3(corners[3]), UUID.randomUUID().toString())
}

def groundRect = [
    new Vector3(rectMinX, 0f, rectMinZ),
    new Vector3(rectMaxX, 0f, rectMinZ),
    new Vector3(rectMaxX, 0f, rectMaxZ),
    new Vector3(rectMinX, 0f, rectMaxZ),
]
def rotatedRect = groundRect.collect { rotateAroundX(it as Vector3, rotateDeg) }

def smallRectOnGround = [
    new Vector3(smallOriginX, 0f, smallOriginZ),
    new Vector3((float) (smallOriginX + smallSize), 0f, smallOriginZ),
    new Vector3((float) (smallOriginX + smallSize), 0f, (float) (smallOriginZ + smallSize)),
    new Vector3(smallOriginX, 0f, (float) (smallOriginZ + smallSize)),
]
def smallRectOnRotatedSurface = smallRectOnGround.collect { rotateAroundX(it as Vector3, rotateDeg) }

def rebuildGeometryAtStep = { int stepNo ->
    int step = Math.max(0, Math.min(stepNo, 3))
    app.run {
        def g = scene.activeGroup()
        g.getLineStore().clearAll()
        g.getFaceStore().clearAll()
        if (step >= 1) {
            if (step == 1) {
                addRect(g, groundRect as List<Vector3>)
            } else if (step == 2) {
                addRect(g, rotatedRect as List<Vector3>)
            } else {
                if (showGroundAtEnd) {
                    addRect(g, groundRect as List<Vector3>)
                }
                addRect(g, rotatedRect as List<Vector3>)
                addRect(g, smallRectOnRotatedSurface as List<Vector3>)
            }
        }
        g.getLineStore().cleanupJts()
    }
}

def outputDirFile = new File(outputDirPath)
if (!outputDirFile.exists()) outputDirFile.mkdirs()

app.run {
    scene.resetScene()
    scene.clearAllSelections()
    if (hideUi) {
        try { uiOverlay.setAutomationHidePanels(true) } catch (Throwable ignored) {}
    }
    // Keep perspective view; only adjust pose for visibility.
    cameraCtl.setPosition(18f, 14f, 20f)
    cameraCtl.setTarget(3f, 0f, 2f)
}

def captured = []
def errors = []
int maxStep = Math.max(0, Math.min(requestedStep, 3))

switch (mode) {
    case "reset":
        rebuildGeometryAtStep(0)
        def c0 = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (c0.ok) captured << c0.path else errors << "step-00: ${c0.error}"
        break
    case "step":
        rebuildGeometryAtStep(0)
        def cStart = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (cStart.ok) captured << cStart.path else errors << "step-00: ${cStart.error}"
        if (maxStep > 0) {
            (1..maxStep).each { s ->
                rebuildGeometryAtStep(s)
                def c = capturePng(new File(outputDirFile, shotNames[s]).path)
                if (c.ok) captured << c.path else errors << "step-${String.format('%02d', s)}: ${c.error}"
            }
        }
        break
    case "batch":
    default:
        rebuildGeometryAtStep(0)
        def cStartAll = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (cStartAll.ok) captured << cStartAll.path else errors << "step-00: ${cStartAll.error}"
        (1..3).each { s ->
            rebuildGeometryAtStep(s)
            def c = capturePng(new File(outputDirFile, shotNames[s]).path)
            if (c.ok) captured << c.path else errors << "step-${String.format('%02d', s)}: ${c.error}"
        }
        break
}

status.message = "Rectangle tutorial capture done: ${captured.size()} files, ${errors.size()} errors."
return [
    ok             : errors.isEmpty(),
    mode           : mode,
    outputDir      : outputDirFile.absolutePath,
    captured       : captured,
    errors         : errors,
    totalSteps     : 3,
    rotateDeg      : rotateDeg,
    smallSize      : smallSize,
    showGroundAtEnd: showGroundAtEnd
]

/*
 * Tutorial: polyline planar mesh capture
 *
 * Default polyline:
 *   0,0 -> 11,0 -> 11,1 -> 1,1 -> 1,12 -> 0,12 -> 0,0
 *
 * Inputs (all optional):
 *   MODE       : "batch" (default) | "reset" | "step"
 *   STEP       : 0..N (used in MODE="step")
 *   OUTPUT_DIR : output directory path (default docs/tutorial/02_polyline)
 *   OVERWRITE  : true/false (default true)
 *   HIDE_UI    : true/false (default true)
 *   CAPTURE    : true/false (default true)
 *   MESH       : true/false (default true)
 *   POINTS     : List<Vector3> to override default points
 *
 * Notes:
 *   - Idempotent by design: starts from a reset scene.
 *   - Captures one screenshot before drawing and one after each segment.
 */

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils

def mode = (binding.hasVariable("MODE") ? MODE : "batch").toString().trim().toLowerCase()
def outputDirPath = (binding.hasVariable("OUTPUT_DIR") ? OUTPUT_DIR : "docs/tutorial/02_polyline").toString()
def overwrite = binding.hasVariable("OVERWRITE") ? (OVERWRITE as boolean) : true
def hideUi = binding.hasVariable("HIDE_UI") ? (HIDE_UI as boolean) : true
def captureEnabled = binding.hasVariable("CAPTURE") ? (CAPTURE as boolean) : true
def meshEnabled = binding.hasVariable("MESH") ? (MESH as boolean) : true
def requestedStep = binding.hasVariable("STEP") ? (STEP as int) : Integer.MAX_VALUE

def defaultPoints = [
    new Vector3(0f, 0f, 0f),
    new Vector3(11f, 0f, 0f),
    new Vector3(11f, 0f, 1f),
    new Vector3(1f, 0f, 1f),
    new Vector3(1f, 0f, 12f),
    new Vector3(0f, 0f, 12f),
    new Vector3(0f, 0f, 0f),
]

def points = (binding.hasVariable("POINTS") && POINTS instanceof List && !POINTS.isEmpty()) ? (POINTS as List<Vector3>) : defaultPoints
if (points.size() < 2) {
    return [ok: false, error: "POINTS requires at least 2 points."]
}

def segments = (0..<points.size() - 1).collect { i -> [points[i], points[i + 1]] }

def shotNames = ["step-00-start.png"]
segments.eachWithIndex { seg, i ->
    def a = seg[0] as Vector3
    def b = seg[1] as Vector3
    shotNames << String.format(
        "step-%02d-line-%s-%s_to_%s-%s.png",
        i + 1,
        (a.x as int), (a.z as int),
        (b.x as int), (b.z as int)
    )
}

def setTopView = {
    cameraCtl.setPosition(6f, 26f, 6f)
    cameraCtl.setTarget(6f, 0f, 6f)
}

def rebuildGeometryAtStep = { int segCount ->
    int clamped = Math.max(0, Math.min(segCount, segments.size()))
    app.run {
        def g = scene.activeGroup()
        def lineStore = g.getLineStore()
        def faceStore = g.getFaceStore()
        lineStore.clearAll()
        faceStore.clearAll()

        (0..<clamped).each { i ->
            def seg = segments[i]
            g.addSketchSegment(new Vector3(seg[0]), new Vector3(seg[1]))
        }

        if (meshEnabled && clamped >= 3) {
            def polyPoints = []
            (0..clamped).each { pi -> polyPoints << new Vector3(points[pi]) }
            faceStore.addPolygon(polyPoints, new Vector3(0f, 1f, 0f))
        }
    }
}

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

def outputDirFile = new File(outputDirPath)
if (!outputDirFile.exists()) outputDirFile.mkdirs()

app.run {
    scene.resetScene()
    scene.clearAllSelections()
    setTopView()
    if (hideUi) {
        try { uiOverlay.setAutomationHidePanels(true) } catch (Throwable ignored) {}
    }
}

def captured = []
def errors = []
int maxStep = Math.max(0, Math.min(requestedStep, segments.size()))

switch (mode) {
    case "reset":
        rebuildGeometryAtStep(0)
        def cap0 = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (cap0.ok) captured << cap0.path else errors << "step-00: ${cap0.error}"
        break
    case "step":
        rebuildGeometryAtStep(0)
        def capStart = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (capStart.ok) captured << capStart.path else errors << "step-00: ${capStart.error}"
        if (maxStep > 0) {
            (1..maxStep).each { stepNo ->
                rebuildGeometryAtStep(stepNo)
                def cap = capturePng(new File(outputDirFile, shotNames[stepNo]).path)
                if (cap.ok) captured << cap.path else errors << "step-${String.format('%02d', stepNo)}: ${cap.error}"
            }
        }
        break
    case "batch":
    default:
        rebuildGeometryAtStep(0)
        def capStartAll = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (capStartAll.ok) captured << capStartAll.path else errors << "step-00: ${capStartAll.error}"
        (1..segments.size()).each { stepNo ->
            rebuildGeometryAtStep(stepNo)
            def cap = capturePng(new File(outputDirFile, shotNames[stepNo]).path)
            if (cap.ok) captured << cap.path else errors << "step-${String.format('%02d', stepNo)}: ${cap.error}"
        }
        break
}

status.message = "Polyline planar mesh capture done: ${captured.size()} files, ${errors.size()} errors."
return [
    ok        : errors.isEmpty(),
    mode      : mode,
    outputDir : outputDirFile.absolutePath,
    captured  : captured,
    errors    : errors,
    totalSteps: segments.size(),
    mesh      : meshEnabled
]

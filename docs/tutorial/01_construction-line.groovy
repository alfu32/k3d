/*
 * Generic tutorial capture script (no hardcoded MCP command required).
 *
 * Default behavior:
 *   - reset scene
 *   - set top view
 *   - draw polyline 0,0 -> 11,0 -> 11,1 -> 1,1 -> 1,12 -> 0,12 -> 0,0
 *   - capture step-00 + step-01..step-06 into docs/tutorial/01_construction-line
 *
 * Inputs (all optional):
 *   MODE         : "batch" (default) | "reset" | "step"
 *   STEP         : 0..6 (used when MODE="step")
 *   OUTPUT_DIR   : output folder path (default docs/tutorial/01_construction-line)
 *   OVERWRITE    : true/false (default true)
 *   HIDE_UI      : true/false (default true)
 *   CAPTURE      : true/false (default true)
 */

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils

def mode = (binding.hasVariable("MODE") ? MODE : "batch").toString().trim().toLowerCase()
def outputDirPath = (binding.hasVariable("OUTPUT_DIR") ? OUTPUT_DIR : "docs/tutorial/01_construction-line").toString()
def overwrite = binding.hasVariable("OVERWRITE") ? (OVERWRITE as boolean) : true
def hideUi = binding.hasVariable("HIDE_UI") ? (HIDE_UI as boolean) : true
def captureEnabled = binding.hasVariable("CAPTURE") ? (CAPTURE as boolean) : true
def requestedStep = binding.hasVariable("STEP") ? (STEP as int) : 6

def points = [
    new Vector3(0f, 0f, 0f),
    new Vector3(11f, 0f, 0f),
    new Vector3(11f, 0f, 1f),
    new Vector3(1f, 0f, 1f),
    new Vector3(1f, 0f, 12f),
    new Vector3(0f, 0f, 12f),
    new Vector3(0f, 0f, 0f),
]
def segments = (0..<points.size() - 1).collect { i -> [points[i], points[i + 1]] }

def shotNames = [
    "step-00-start.png",
    "step-01-line-0-0_to_11-0.png",
    "step-02-line-11-0_to_11-1.png",
    "step-03-line-11-1_to_1-1.png",
    "step-04-line-1-1_to_1-12.png",
    "step-05-line-1-12_to_0-12.png",
    "step-06-line-0-12_to_0-0.png",
]

def setTopView = {
    cameraCtl.setPosition(6f, 26f, 6f)
    cameraCtl.setTarget(6f, 0f, 6f)
}

def capturePng = { String filePath ->
    if (!captureEnabled) return [ok: true, skipped: true, path: filePath]
    FileHandle out = Gdx.files.absolute(new File(filePath).absolutePath)
    FileHandle parent = out.parent()
    if (parent != null && !parent.exists()) {
        parent.mkdirs()
    }
    if (!overwrite && out.exists()) {
        return [ok: true, skipped: true, path: out.file().absolutePath]
    }
    Pixmap pix = null
    PixmapIO.PNG writer = null
    try {
        int w = Math.max(1, Gdx.graphics.backBufferWidth)
        int h = Math.max(1, Gdx.graphics.backBufferHeight)
        pix = ScreenUtils.getFrameBufferPixmap(0, 0, w, h)
        writer = new PixmapIO.PNG(Math.max(1024, (int)(w * h * 1.5f)))
        writer.setFlipY(true)
        writer.write(out, pix)
        return [ok: true, skipped: false, path: out.file().absolutePath]
    } catch (Throwable t) {
        return [ok: false, skipped: false, path: out.file().absolutePath, error: (t.message ?: t.class.simpleName)]
    } finally {
        try { writer?.dispose() } catch (Throwable ignored) {}
        try { pix?.dispose() } catch (Throwable ignored) {}
    }
}

def outputDirFile = new File(outputDirPath)
if (!outputDirFile.exists()) {
    outputDirFile.mkdirs()
}

if (hideUi) {
    try {
        app.run { scene.resetScene() }
        app.run { scene.clearAllSelections() }
    } catch (Throwable ignored) {}
}

app.run {
    scene.resetScene()
    scene.clearAllSelections()
    setTopView()
}

def captured = []
def errors = []
def clampStep = Math.max(0, Math.min(requestedStep, segments.size()))

switch (mode) {
    case "reset":
        def startRes = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (startRes.ok) captured << startRes.path else errors << "step-00: ${startRes.error}"
        status.message = "Tutorial reset done."
        break
    case "step":
        def startRes2 = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (startRes2.ok) captured << startRes2.path else errors << "step-00: ${startRes2.error}"
        (0..<clampStep).each { i ->
            def seg = segments[i]
            app.run { scene.activeGroup().addSketchSegment(new Vector3(seg[0]), new Vector3(seg[1])) }
            def cap = capturePng(new File(outputDirFile, shotNames[i + 1]).path)
            if (cap.ok) captured << cap.path else errors << "step-${i + 1}: ${cap.error}"
        }
        status.message = "Tutorial step mode: ${clampStep}/${segments.size()} segments."
        break
    case "batch":
    default:
        def startRes3 = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (startRes3.ok) captured << startRes3.path else errors << "step-00: ${startRes3.error}"
        segments.eachWithIndex { seg, i ->
            app.run { scene.activeGroup().addSketchSegment(new Vector3(seg[0]), new Vector3(seg[1])) }
            def cap = capturePng(new File(outputDirFile, shotNames[i + 1]).path)
            if (cap.ok) captured << cap.path else errors << "step-${i + 1}: ${cap.error}"
        }
        status.message = "Tutorial batch mode complete."
        break
}

return [
    mode      : mode,
    outputDir : outputDirFile.absolutePath,
    captured  : captured,
    errors    : errors,
    totalSteps: segments.size(),
]

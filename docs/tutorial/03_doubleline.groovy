/*
 * Tutorial: double line (planar ribbon mesh) capture
 *
 * Default path (x,z):
 *   -5,-5 -> 11,-5 -> 11,5 -> 5,5 -> 5,12 -> -5,12 -> 0,0
 *
 * Inputs (all optional):
 *   MODE         : "batch" (default) | "reset" | "step"
 *   STEP         : 0..N (used in MODE="step")
 *   OUTPUT_DIR   : output directory path (default docs/tutorial/03_doubleline)
 *   OVERWRITE    : true/false (default true)
 *   HIDE_UI      : true/false (default true)
 *   CAPTURE      : true/false (default true)
 *   DOUBLE_SIZE  : ribbon width (default 1.0)
 *   DOUBLE_OFFSET: ribbon center offset (default 0.5)
 *   CLOSED       : true/false (default false; auto-true when first == last)
 *   POINTS       : List<Vector3> to override default points
 *
 * Notes:
 *   - Idempotent by design: starts from a reset scene.
 *   - Keeps camera perspective (does not switch to ortho).
 *   - Captures one screenshot before drawing and one after each segment.
 */

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import java.util.UUID

def mode = (binding.hasVariable("MODE") ? MODE : "batch").toString().trim().toLowerCase()
def outputDirPath = (binding.hasVariable("OUTPUT_DIR") ? OUTPUT_DIR : "docs/tutorial/03_doubleline").toString()
def overwrite = binding.hasVariable("OVERWRITE") ? (OVERWRITE as boolean) : true
def hideUi = binding.hasVariable("HIDE_UI") ? (HIDE_UI as boolean) : true
def captureEnabled = binding.hasVariable("CAPTURE") ? (CAPTURE as boolean) : true
def requestedStep = binding.hasVariable("STEP") ? (STEP as int) : Integer.MAX_VALUE
float doubleSize = binding.hasVariable("DOUBLE_SIZE") ? (DOUBLE_SIZE as Number).floatValue() : 1f
float doubleOffset = binding.hasVariable("DOUBLE_OFFSET") ? (DOUBLE_OFFSET as Number).floatValue() : 0.5f

def defaultPoints = [
    new Vector3(-5f, 0f, -5f),
    new Vector3(11f, 0f, -5f),
    new Vector3(11f, 0f, 5f),
    new Vector3(5f, 0f, 5f),
    new Vector3(5f, 0f, 12f),
    new Vector3(-5f, 0f, 12f),
    new Vector3(0f, 0f, 0f),
]
def points = (binding.hasVariable("POINTS") && POINTS instanceof List && !POINTS.isEmpty()) ? (POINTS as List<Vector3>) : defaultPoints
if (points.size() < 2) {
    return [ok: false, error: "POINTS requires at least 2 points."]
}

def autoClosed = points.first().dst2(points.last()) <= 1e-6f
def isClosed = binding.hasVariable("CLOSED") ? (CLOSED as boolean) : autoClosed

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

def isFiniteVec = { Vector3 v ->
    Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z)
}

def segmentPerp = { Vector3 start, Vector3 end ->
    def dir = new Vector3(end).sub(start)
    if (dir.len2() <= 1e-8f) return null
    dir.nor()
    def perp = new Vector3(dir).crs(0f, 1f, 0f)
    if (perp.len2() <= 1e-8f) return null
    perp.nor()
}

def buildOffsetPath = { List<Vector3> base, float offset, boolean closedFlag ->
    def result = []
    int count = base.size()
    for (int i = 0; i < count; i++) {
        def curr = base[i]
        def prev = (i > 0) ? base[i - 1] : (closedFlag ? base[count - 1] : null)
        def next = (i < count - 1) ? base[i + 1] : (closedFlag ? base[0] : null)
        def perpPrev = prev != null ? segmentPerp(prev as Vector3, curr as Vector3) : null
        def perpNext = next != null ? segmentPerp(curr as Vector3, next as Vector3) : null
        Vector3 offsetDir = null

        if (perpPrev != null && perpNext != null) {
            def miter = new Vector3(perpPrev as Vector3).add(perpNext as Vector3)
            if (miter.len2() > 1e-8f) {
                miter.nor()
                float denom = miter.dot(perpPrev as Vector3)
                if (Math.abs(denom) > 0.01f) {
                    float scale = offset / denom
                    float maxScale = Math.abs(offset) * 10f + 1f
                    if (Math.abs(scale) <= maxScale) {
                        offsetDir = new Vector3(miter).scl(scale)
                    }
                }
            }
        }
        if (offsetDir == null) {
            def fallback = perpPrev ?: perpNext
            offsetDir = fallback != null ? new Vector3(fallback as Vector3).scl(offset) : new Vector3()
        }
        if (!isFiniteVec(offsetDir)) {
            offsetDir.set(0f, 0f, 0f)
        }
        result << new Vector3(curr as Vector3).add(offsetDir)
    }
    result
}

def addRibbonGeometryAtStep = { int segCount ->
    int clamped = Math.max(0, Math.min(segCount, segments.size()))
    app.run {
        def group = scene.activeGroup()
        def lineStore = group.getLineStore()
        def faceStore = group.getFaceStore()
        lineStore.clearAll()
        faceStore.clearAll()

        if (clamped <= 0) {
            return
        }

        def base = []
        (0..clamped).each { idx -> base << new Vector3(points[idx]) }
        if ((base as List).size() < 2) {
            return
        }

        float half = Math.max(0f, doubleSize) * 0.5f
        float offA = doubleOffset - half
        float offB = doubleOffset + half
        def left = buildOffsetPath(base as List<Vector3>, offA, isClosed)
        def right = buildOffsetPath(base as List<Vector3>, offB, isClosed)

        def loop = []
        loop.addAll(left)
        loop.addAll((right as List).reverse())

        // Boundary loop segments
        for (int i = 0; i < loop.size() - 1; i++) {
            def a = loop[i] as Vector3
            def b = loop[i + 1] as Vector3
            if (a.dst2(b) > 1e-8f) {
                lineStore.addSegment(new Vector3(a), new Vector3(b), false, UUID.randomUUID().toString())
            }
        }
        if (!loop.isEmpty()) {
            def a = loop[loop.size() - 1] as Vector3
            def b = loop[0] as Vector3
            if (a.dst2(b) > 1e-8f) {
                lineStore.addSegment(new Vector3(a), new Vector3(b), false, UUID.randomUUID().toString())
            }
        }

        // Strip faces
        int count = Math.min((left as List).size(), (right as List).size())
        if (count >= 2) {
            int limit = isClosed ? count : count - 1
            for (int i = 0; i < limit; i++) {
                int next = (i + 1) % count
                def a = left[i] as Vector3
                def b = left[next] as Vector3
                def c = right[next] as Vector3
                def d = right[i] as Vector3
                faceStore.addTriangle(new Vector3(a), new Vector3(b), new Vector3(c), UUID.randomUUID().toString())
                faceStore.addTriangle(new Vector3(a), new Vector3(c), new Vector3(d), UUID.randomUUID().toString())
            }
        }

        lineStore.cleanupJts()
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
}

def captured = []
def errors = []
int maxStep = Math.max(0, Math.min(requestedStep, segments.size()))

switch (mode) {
    case "reset":
        addRibbonGeometryAtStep(0)
        def cap0 = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (cap0.ok) captured << cap0.path else errors << "step-00: ${cap0.error}"
        break
    case "step":
        addRibbonGeometryAtStep(0)
        def capStart = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (capStart.ok) captured << capStart.path else errors << "step-00: ${capStart.error}"
        if (maxStep > 0) {
            (1..maxStep).each { stepNo ->
                addRibbonGeometryAtStep(stepNo)
                def cap = capturePng(new File(outputDirFile, shotNames[stepNo]).path)
                if (cap.ok) captured << cap.path else errors << "step-${String.format('%02d', stepNo)}: ${cap.error}"
            }
        }
        break
    case "batch":
    default:
        addRibbonGeometryAtStep(0)
        def capStartAll = capturePng(new File(outputDirFile, shotNames[0]).path)
        if (capStartAll.ok) captured << capStartAll.path else errors << "step-00: ${capStartAll.error}"
        (1..segments.size()).each { stepNo ->
            addRibbonGeometryAtStep(stepNo)
            def cap = capturePng(new File(outputDirFile, shotNames[stepNo]).path)
            if (cap.ok) captured << cap.path else errors << "step-${String.format('%02d', stepNo)}: ${cap.error}"
        }
        break
}

status.message = "Double line tutorial capture done: ${captured.size()} files, ${errors.size()} errors."
return [
    ok          : errors.isEmpty(),
    mode        : mode,
    outputDir   : outputDirFile.absolutePath,
    captured    : captured,
    errors      : errors,
    totalSteps  : segments.size(),
    doubleSize  : doubleSize,
    doubleOffset: doubleOffset,
    closed      : isClosed
]

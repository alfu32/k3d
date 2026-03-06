/*
 * Tutorial: construction line sequence
 *
 * Target polyline:
 *   0,0 -> 11,0 -> 11,1 -> 1,1 -> 1,12 -> 0,12 -> 0,0
 *
 * Usage from MCP /scene/console:
 *   1) MODE = "reset" ; run script
 *   2) MODE = "step", STEP = 0..6 ; run script
 *      after each run, call /scene/command?id=export.screenshot
 *      and save/copy it as:
 *        step-00-start.png
 *        step-01-line-0-0_to_11-0.png
 *        step-02-line-11-0_to_11-1.png
 *        step-03-line-11-1_to_1-1.png
 *        step-04-line-1-1_to_1-12.png
 *        step-05-line-1-12_to_0-12.png
 *        step-06-line-0-12_to_0-0.png
 *
 * Optional:
 *   MODE = "all"  // reset + draw all segments
 */

import com.badlogic.gdx.math.Vector3

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

def setupTopView = {
    cameraCtl.setPosition(6f, 26f, 6f)
    cameraCtl.setTarget(6f, 0f, 6f)
}

def addSegments = { int count ->
    def g = scene.activeGroup()
    int clamped = Math.max(0, Math.min(count, segments.size()))
    for (int i = 0; i < clamped; i++) {
        def s = segments[i]
        g.addSketchSegment(s[0] as Vector3, s[1] as Vector3)
    }
    clamped
}

def mode = (binding.hasVariable("MODE") ? MODE : "all").toString().toLowerCase()
def step = (binding.hasVariable("STEP") ? (STEP as int) : segments.size())

app.run {
    switch (mode) {
        case "reset":
            scene.resetScene()
            scene.clearAllSelections()
            setupTopView()
            status.message = "Tutorial reset complete. Capture step-00-start."
            break
        case "step":
            scene.resetScene()
            scene.clearAllSelections()
            setupTopView()
            int drawn = addSegments(step)
            status.message = "Tutorial step mode: drew ${drawn}/${segments.size()} segments."
            break
        case "all":
        default:
            scene.resetScene()
            scene.clearAllSelections()
            setupTopView()
            int drawn = addSegments(segments.size())
            status.message = "Tutorial all mode: drew ${drawn}/${segments.size()} segments."
            break
    }
}

return [
    mode      : mode,
    step      : step,
    totalSteps: segments.size(),
    note      : "Capture screenshot via command export.screenshot after each step."
]

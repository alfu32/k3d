import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.github.alfu32.sketch.model.ModelPersistence
import com.github.alfu32.sketch.plugin.Plugin
import com.github.alfu32.sketch.plugin.PluginChange
import com.github.alfu32.sketch.plugin.PluginContext
import com.github.alfu32.sketch.plugin.PluginResult
import com.github.alfu32.sketch.plugin.capabilities.PluginTool
import com.github.alfu32.sketch.plugin.capabilities.ToolCategory

class PolylinePlugin implements Plugin {
    String id = "polyline"
    String name = "Polyline"
    String version = "0.1"
    String author = "Sketch3D"
    String description = "Polyline tool with line and arc modes."

    @Override
    List<PluginTool> registerTools() {
        return [new PolylineTool()]
    }
}

class PolylineTool implements PluginTool {
    String id = "polyline"
    String name = "Polyline"
    String description = "Draw a polyline and preview faces."
    String icon = "tool"
    String cursor = "crosshair"
    ToolCategory category = ToolCategory.DRAWING

    private final List<Vector3> points = []
    private String arcMode = "line"
    private Vector3 arcCenter = null
    private Vector3 arcPass1 = null
    private final float closeDistance = 0.15f
    private final float epsilon = 0.0001f

    @Override
    void onActivate(PluginContext context) {
        points.clear()
        arcMode = "line"
        arcCenter = null
        arcPass1 = null
    }

    @Override
    void onDeactivate(PluginContext context) {
        points.clear()
        arcMode = "line"
        arcCenter = null
        arcPass1 = null
    }

    @Override
    void onMouseMove(PluginContext context, int screenX, int screenY) {
        // Preview is computed on draw.
    }

    @Override
    void onMouseDown(PluginContext context, int button, int screenX, int screenY) {
        if (button != Input.Buttons.LEFT) {
            return
        }
        Vector3 hit = context.cursor.world
        if (hit == null) {
            return
        }
        if (points.isEmpty()) {
            points.add(new Vector3(hit))
            return
        }
        if (isClosing(hit)) {
            finalizePolyline(context)
            return
        }
        if (arcMode == "center") {
            if (arcCenter == null) {
                arcCenter = new Vector3(hit)
                return
            }
            def arcPoints = arcPointsFromCenter(points.last(), hit, arcCenter, 16)
            appendArcPoints(arcPoints)
            arcCenter = null
            return
        }
        if (arcMode == "three") {
            if (arcPass1 == null) {
                arcPass1 = new Vector3(hit)
                return
            }
            def arcPoints = arcPointsThrough(points.last(), arcPass1, hit, 16)
            appendArcPoints(arcPoints)
            arcPass1 = null
            return
        }
        points.add(new Vector3(hit))
    }

    @Override
    void onMouseUp(PluginContext context, int button, int screenX, int screenY) {
        // No-op.
    }

    @Override
    boolean onKeyDown(PluginContext context, int keycode) {
        if (keycode == Input.Keys.A) {
            arcMode = "three"
            arcPass1 = null
            arcCenter = null
            return true
        }
        if (keycode == Input.Keys.C) {
            arcMode = "center"
            arcCenter = null
            arcPass1 = null
            return true
        }
        if (keycode == Input.Keys.L) {
            arcMode = "line"
            arcCenter = null
            arcPass1 = null
            return true
        }
        if (keycode == Input.Keys.ESCAPE) {
            finalizePolyline(context)
            return true
        }
        return false
    }

    @Override
    boolean onKeyUp(PluginContext context, int keycode) {
        return false
    }

    @Override
    void onDraw2D(PluginContext context, com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        // Not used.
    }

    @Override
    void onDraw3D(PluginContext context, ShapeRenderer shapeRenderer) {
        if (points.isEmpty()) {
            return
        }
        shapeRenderer.color = new Color(0.95f, 0.75f, 0.25f, 1f)
        for (int i = 0; i < points.size() - 1; i++) {
            shapeRenderer.line(points[i], points[i + 1])
        }
        Vector3 cursor = context.cursor.world
        if (cursor != null) {
            def preview = previewArc(points.last(), cursor)
            if (preview != null) {
                drawPreview(shapeRenderer, preview)
            } else {
                shapeRenderer.line(points.last(), cursor)
            }
        }
        if (points.size() >= 3) {
            def tris = triangulate(points)
            shapeRenderer.color = new Color(0.25f, 0.85f, 0.45f, 1f)
            tris.each { tri ->
                shapeRenderer.line(tri[0], tri[1])
                shapeRenderer.line(tri[1], tri[2])
                shapeRenderer.line(tri[2], tri[0])
            }
        }
    }

    @Override
    void onUpdate(PluginContext context, float delta) {
        // No-op.
    }

    private boolean isClosing(Vector3 candidate) {
        if (points.size() < 3) {
            return false
        }
        return points.first().dst(candidate) <= closeDistance
    }

    private void finalizePolyline(PluginContext context) {
        if (points.size() < 3) {
            points.clear()
            arcCenter = null
            arcPass1 = null
            return
        }
        def snapshot = context.model
        def target = snapshot.rootGroup
        def faces = triangulate(points)
        if (target != null) {
            faces.each { tri ->
                target.faces.add(new ModelPersistence.FaceDto(
                    new ModelPersistence.Vec3Dto(tri[0]),
                    new ModelPersistence.Vec3Dto(tri[1]),
                    new ModelPersistence.Vec3Dto(tri[2]),
                    new ModelPersistence.ColorDto(new Color(0.8f, 0.8f, 0.8f, 1f))
                ))
            }
            addSegments(target.segments, points)
        } else {
            faces.each { tri ->
                snapshot.faces.add(new ModelPersistence.FaceDto(
                    new ModelPersistence.Vec3Dto(tri[0]),
                    new ModelPersistence.Vec3Dto(tri[1]),
                    new ModelPersistence.Vec3Dto(tri[2]),
                    new ModelPersistence.ColorDto(new Color(0.8f, 0.8f, 0.8f, 1f))
                ))
            }
            addSegments(snapshot.segments, points)
        }
        context.getApplyResult().invoke(new PluginResult([
            new PluginChange.ReplaceModel(snapshot),
            new PluginChange.StatusMessage("Polyline finalized.")
        ], true, null))
        points.clear()
        arcCenter = null
        arcPass1 = null
    }

    private void addSegments(def list, List<Vector3> pts) {
        for (int i = 0; i < pts.size() - 1; i++) {
            list.add(new ModelPersistence.SegmentDto(
                new ModelPersistence.Vec3Dto(pts[i]),
                new ModelPersistence.Vec3Dto(pts[i + 1])
            ))
        }
    }

    private List<Vector3> previewArc(Vector3 start, Vector3 cursor) {
        if (arcMode == "center" && arcCenter != null) {
            return arcPointsFromCenter(start, cursor, arcCenter, 24)
        }
        if (arcMode == "three" && arcPass1 != null) {
            return arcPointsThrough(start, arcPass1, cursor, 24)
        }
        return null
    }

    private void drawPreview(ShapeRenderer shapeRenderer, List<Vector3> preview) {
        if (preview.isEmpty()) {
            return
        }
        if (preview.size() == 1) {
            return
        }
        for (int i = 0; i < preview.size() - 1; i++) {
            shapeRenderer.line(preview[i], preview[i + 1])
        }
    }

    private void appendArcPoints(List<Vector3> arcPoints) {
        if (arcPoints.isEmpty()) {
            return
        }
        if (arcPoints.size() == 1) {
            return
        }
        for (int i = 1; i < arcPoints.size(); i++) {
            Vector3 next = arcPoints[i]
            if (points.last().dst(next) > epsilon) {
                points.add(new Vector3(next))
            }
        }
    }

    private List<Vector3> arcPointsFromCenter(Vector3 start, Vector3 end, Vector3 center, int steps) {
        Vector3 startVec = new Vector3(start).sub(center)
        Vector3 endVec = new Vector3(end).sub(center)
        float radius = startVec.len()
        if (radius <= epsilon) {
            return []
        }
        Vector3 normal = new Vector3(startVec).crs(endVec)
        if (normal.len2() <= epsilon * epsilon) {
            if (start.dst(end) <= epsilon) {
                return []
            }
            return [start, end]
        }
        Vector3 u = startVec.nor()
        Vector3 w = normal.nor()
        Vector3 v = new Vector3(w).crs(u).nor()
        float endAngle = (float)Math.atan2(endVec.dot(v), endVec.dot(u))
        float delta = normalizeAngle(endAngle)
        if (Math.abs(delta) <= epsilon) {
            return [start, end]
        }
        return buildArcPoints(center, u, v, radius, delta, steps)
    }

    private List<Vector3> arcPointsThrough(Vector3 start, Vector3 mid, Vector3 end, int steps) {
        Vector3 ab = new Vector3(mid).sub(start)
        Vector3 ac = new Vector3(end).sub(start)
        Vector3 normal = new Vector3(ab).crs(ac)
        if (normal.len2() <= epsilon * epsilon) {
            if (start.dst(end) <= epsilon) {
                return []
            }
            return [start, end]
        }
        Vector3 u = new Vector3(ab).nor()
        Vector3 w = new Vector3(normal).nor()
        Vector3 v = new Vector3(w).crs(u).nor()
        float bx = ab.len()
        float cx = ac.dot(u)
        float cy = ac.dot(v)
        float d = 2f * (bx * cy)
        if (Math.abs(d) <= epsilon) {
            return [start, end]
        }
        float ux = (bx * bx * cy) / d
        float uy = (cx * cx + cy * cy - bx * bx) / (2f * cy)
        Vector3 center = new Vector3(start).add(new Vector3(u).scl(ux)).add(new Vector3(v).scl(uy))
        Vector3 startVec = new Vector3(start).sub(center)
        Vector3 midVec = new Vector3(mid).sub(center)
        Vector3 endVec = new Vector3(end).sub(center)
        float radius = startVec.len()
        if (radius <= epsilon) {
            return []
        }
        Vector3 u2 = startVec.nor()
        Vector3 v2 = new Vector3(w).crs(u2).nor()
        float midAngle = (float)Math.atan2(midVec.dot(v2), midVec.dot(u2))
        float endAngle = (float)Math.atan2(endVec.dot(v2), endVec.dot(u2))
        float delta = normalizeAngle(endAngle)
        float midNorm = normalizeAngle(midAngle)
        if (!isBetweenCCW(0f, midNorm, delta)) {
            delta = delta - (float)(Math.PI * 2.0)
        }
        if (Math.abs(delta) <= epsilon) {
            return [start, end]
        }
        return buildArcPoints(center, u2, v2, radius, delta, steps)
    }

    private List<Vector3> buildArcPoints(
        Vector3 center,
        Vector3 u,
        Vector3 v,
        float radius,
        float delta,
        int steps
    ) {
        def pts = [new Vector3(center).add(new Vector3(u).scl(radius as float))]
        float step = delta / steps
        for (int i = 1; i <= steps; i++) {
            float angle = step * i
            float cos = (float)Math.cos(angle)
            float sin = (float)Math.sin(angle)
            Vector3 point = new Vector3(center)
                .add(new Vector3(u).scl((radius * cos) as float))
                .add(new Vector3(v).scl((radius * sin) as float))
            pts.add(point)
        }
        return pts
    }

    private float normalizeAngle(float angle) {
        float twoPi = (float)(Math.PI * 2.0)
        float a = angle % twoPi
        return a < 0f ? a + twoPi : a
    }

    private boolean isBetweenCCW(float start, float mid, float end) {
        if (end < start) {
            end += (float)(Math.PI * 2.0)
        }
        if (mid < start) {
            mid += (float)(Math.PI * 2.0)
        }
        return mid >= start && mid <= end
    }

    private List<List<Vector3>> triangulate(List<Vector3> pts) {
        def normal = computeNormal(pts)
        def projected = projectTo2D(pts, normal)
        def indices = (0..<pts.size()).toList()
        if (signedArea(projected) < 0f) {
            indices = indices.reverse()
        }
        def triangles = []
        int guard = 0
        while (indices.size() > 2 && guard < 10000) {
            guard++
            boolean earFound = false
            for (int i = 0; i < indices.size(); i++) {
                int prev = indices[(i - 1 + indices.size()) % indices.size()]
                int curr = indices[i]
                int next = indices[(i + 1) % indices.size()]
                if (!isConvex(projected[prev], projected[curr], projected[next])) {
                    continue
                }
                if (containsPoint(projected, indices, prev, curr, next)) {
                    continue
                }
                triangles.add([pts[prev], pts[curr], pts[next]])
                indices.remove(i)
                earFound = true
                break
            }
            if (!earFound) {
                break
            }
        }
        return triangles
    }

    private Vector3 computeNormal(List<Vector3> pts) {
        float nx = 0f
        float ny = 0f
        float nz = 0f
        for (int i = 0; i < pts.size(); i++) {
            Vector3 current = pts[i]
            Vector3 next = pts[(i + 1) % pts.size()]
            nx += (current.y - next.y) * (current.z + next.z)
            ny += (current.z - next.z) * (current.x + next.x)
            nz += (current.x - next.x) * (current.y + next.y)
        }
        Vector3 normal = new Vector3(nx, ny, nz)
        if (normal.len2() <= 0.000001f) {
            return new Vector3(0f, 1f, 0f)
        }
        return normal.nor()
    }

    private List<float[]> projectTo2D(List<Vector3> pts, Vector3 normal) {
        float ax = Math.abs(normal.x)
        float ay = Math.abs(normal.y)
        float az = Math.abs(normal.z)
        if (ax >= ay && ax >= az) {
            return pts.collect { [it.y, it.z] as float[] }
        }
        if (ay >= ax && ay >= az) {
            return pts.collect { [it.x, it.z] as float[] }
        }
        return pts.collect { [it.x, it.y] as float[] }
    }

    private float signedArea(List<float[]> pts) {
        float area = 0f
        for (int i = 0; i < pts.size(); i++) {
            def a = pts[i]
            def b = pts[(i + 1) % pts.size()]
            area += a[0] * b[1] - b[0] * a[1]
        }
        return area * 0.5f
    }

    private boolean isConvex(float[] a, float[] b, float[] c) {
        float cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        return cross > 0f
    }

    private boolean containsPoint(List<float[]> pts, List<Integer> indices, int prev, int curr, int next) {
        float[] a = pts[prev]
        float[] b = pts[curr]
        float[] c = pts[next]
        for (int idx : indices) {
            if (idx == prev || idx == curr || idx == next) {
                continue
            }
            if (pointInTriangle(pts[idx], a, b, c)) {
                return true
            }
        }
        return false
    }

    private boolean pointInTriangle(float[] p, float[] a, float[] b, float[] c) {
        float area = Math.abs(triangleArea(a, b, c))
        float a1 = Math.abs(triangleArea(p, b, c))
        float a2 = Math.abs(triangleArea(a, p, c))
        float a3 = Math.abs(triangleArea(a, b, p))
        return Math.abs(area - (a1 + a2 + a3)) < 0.0001f
    }

    private float triangleArea(float[] a, float[] b, float[] c) {
        return (a[0] * (b[1] - c[1]) +
            b[0] * (c[1] - a[1]) +
            c[0] * (a[1] - b[1])) / 2f
    }
}

return new PolylinePlugin()

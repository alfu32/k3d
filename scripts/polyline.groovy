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
import com.github.alfu32.sketch.plugin.capabilities.PluginCommand
import com.github.alfu32.sketch.plugin.capabilities.PluginPanel
import com.github.alfu32.sketch.plugin.capabilities.PanelPosition
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextField
import java.util.Locale

class PolylineSettings {
    int arcSegments = 24
    float doubleLineSize = 0.1f
    float doubleLineOffset = 0f
}

class PolylinePlugin implements Plugin {
    String id = "polyline"
    String name = "Polyline"
    String version = "0.1"
    String author = "Sketch3D"
    String description = "Polyline tool with line and arc modes."
    private final PolylineSettings settings = new PolylineSettings()
    private static final String SETTINGS_PANEL_ID = "settings"
    private static final String SETTINGS_PANEL_KEY = "polyline.settings"

    @Override
    List<PluginTool> registerTools() {
        return [new PolylineTool(settings), new DoubleLineTool(settings)]
    }

    @Override
    List<PluginCommand> registerCommands() {
        return [new ShowPolylineSettingsCommand(SETTINGS_PANEL_KEY)]
    }

    @Override
    List registerUIElements() {
        return [
            new PluginPanel(
                SETTINGS_PANEL_ID,
                "Polyline Settings",
                260f,
                220f,
                PanelPosition.RIGHT,
                { ctx ->
                    def content = new VisTable()
                    content.defaults().pad(4f).left().growX()
                    content.add(new VisLabel("Arc segments")).left().row()
                    def arcField = new VisTextField(settings.arcSegments.toString())
                    content.add(arcField).growX().row()
                    content.add(new VisLabel("Double line size")).left().padTop(4f).row()
                    def sizeField = new VisTextField(String.format(Locale.US, "%.3f", settings.doubleLineSize))
                    content.add(sizeField).growX().row()
                    content.add(new VisLabel("Double line offset")).left().padTop(4f).row()
                    def offsetField = new VisTextField(String.format(Locale.US, "%.3f", settings.doubleLineOffset))
                    content.add(offsetField).growX().row()

                    arcField.addListener(new ChangeListener() {
                        @Override
                        void changed(ChangeListener.ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                            try {
                                int value = Integer.parseInt(arcField.text.trim())
                                settings.arcSegments = Math.max(3, value)
                            } catch (Exception ignored) {
                            }
                        }
                    })
                    sizeField.addListener(new ChangeListener() {
                        @Override
                        void changed(ChangeListener.ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                            try {
                                float value = Float.parseFloat(sizeField.text.trim())
                                settings.doubleLineSize = Math.max(0f, value)
                            } catch (Exception ignored) {
                            }
                        }
                    })
                    offsetField.addListener(new ChangeListener() {
                        @Override
                        void changed(ChangeListener.ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                            try {
                                float value = Float.parseFloat(offsetField.text.trim())
                                settings.doubleLineOffset = value
                            } catch (Exception ignored) {
                            }
                        }
                    })
                    return content
                }
            )
        ]
    }
}

class ShowPolylineSettingsCommand implements PluginCommand {
    String id = "show_settings"
    String name = "Show Polyline Settings"
    String description = "Show the polyline settings panel."
    String category = "View"
    com.github.alfu32.sketch.plugin.capabilities.KeyBinding shortcut = null
    String icon = "view"
    boolean isVisibleInPalette() { return true }
    private final String panelId

    ShowPolylineSettingsCommand(String panelId) {
        this.panelId = panelId
    }

    @Override
    PluginResult execute(PluginContext context) {
        return new PluginResult([
            new PluginChange.ShowPluginPanel(panelId)
        ], true, null)
    }
}

class PolylineTool implements PluginTool {
    String id = "polyline"
    String name = "Polyline"
    String description = "Draw a polyline and preview faces."
    String icon = "tool"
    String cursor = "crosshair"
    ToolCategory category = ToolCategory.DRAWING
    TextureRegionDrawable iconDrawable = PluginContext.iconFromBase64("data:image/png,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAACXBIWXMAAA7DAAAOwwHHb6hkAAAAGXRFWHRTb2Z0d2FyZQB3d3cuaW5rc2NhcGUub3Jnm+48GgAAAjhJREFUWIXtlF9IU1Ecxz/3LrfFbLXN/kAtc0K0ZVbiSpAIVyK0BUEIkUawlyKIiJ6iHoIeegmyhx6CiCgiIoggLXoZiNhalBBE+KdEa0uiplJha7ndHrbBXU2c527zZV843HN/nO/5fs7hx4GyyspPcnqUVNVAFzDy8gBK/36SwBBwGVhb7PCjwAygAMqTNpRHral5evwEOgoRJOWodQK3AclW4x6rbe4cXOnYOaWQ5Ov7kPXD8zvOybGB9WmQDuBeIQFWA8OAubb5yGtn68mhXKZ3z65uGg3ebQC+AxuBL6IA/zbWMcBsWbclPFc4gKvt1KDFXh8GzMAJ0fBcAD6AavfBkfmMG9ztw2qPqJaof3p9bE8ocMmxI6qu18VeWQDeGhunMrUqR+NkwAs6ifrd3eIAWTeQUFJAhmVV8fmMhkpbXO0RVZbZ05P6+tzZi9Qnz7Vei0r+uhUM4PePb/qAFwLeRQBIJmel4K3jLQB6mZgWAKEGkkJdTk/lR5unh1FglxYAoRs4bXzserAH2h1cAz5rARC6gYsD0LACAhPcV9fjfrYC6G/ypqgAwajhT++nX3qgQsSvGcBktU/HZ6ZNwF7gRqa+kJNnJNQDa1wt4+npWWC5yB6aAGqaDo2brPZowIsjcpj+kgPIsi68ed+Zc6uWyhM2Izmf6Xy1gB5QIpIkhxRdxYvu89siAHVwXUt4HgD/hxZaWQCZd/2KJD2cVRKhpxeawsUInVMxP30xP30lDS1rsfUXfEKden9NU0sAAAAASUVORK5CYII=")

    private final PolylineSettings settings
    private final List<Vector3> points = []
    private String arcMode = "line"
    private Vector3 arcCenter = null
    private Vector3 arcPass1 = null
    private boolean closed = false
    private final float closeDistance = 0.15f
    private final float epsilon = 0.0001f

    PolylineTool(PolylineSettings settings) {
        this.settings = settings
    }

    @Override
    void onActivate(PluginContext context) {
        points.clear()
        arcMode = "line"
        arcCenter = null
        arcPass1 = null
        closed = false
    }

    @Override
    void onDeactivate(PluginContext context) {
        points.clear()
        arcMode = "line"
        arcCenter = null
        arcPass1 = null
        closed = false
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
            closed = true
            finalizePolyline(context)
            return
        }
        if (arcMode == "center") {
            if (arcCenter == null) {
                arcCenter = new Vector3(hit)
                return
            }
            def arcPoints = arcPointsFromCenter(points.last(), hit, arcCenter, arcSteps())
            appendArcPoints(arcPoints)
            arcCenter = null
            return
        }
        if (arcMode == "three") {
            if (arcPass1 == null) {
                arcPass1 = new Vector3(hit)
                return
            }
            def arcPoints = arcPointsThrough(points.last(), arcPass1, hit, arcSteps())
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
        if (points.size() < 2) {
            points.clear()
            arcCenter = null
            arcPass1 = null
            closed = false
            return
        }
        def snapshot = context.model
        def faces = points.size() >= 3 ? triangulate(points) : []
        def target = resolveTargetPrototype(snapshot)
        def targetSegments = target != null ? target.segments : snapshot.segments
        def targetFaces = target != null ? target.faces : snapshot.faces
        faces.each { tri ->
            targetFaces.add(new ModelPersistence.FaceDto(
                new ModelPersistence.Vec3Dto(tri[0]),
                new ModelPersistence.Vec3Dto(tri[1]),
                new ModelPersistence.Vec3Dto(tri[2]),
                new ModelPersistence.ColorDto(new Color(0.8f, 0.8f, 0.8f, 1f))
            ))
        }
        addSegments(targetSegments, points, closed)
        context.getApplyResult().invoke(new PluginResult([
            new PluginChange.ReplaceModel(snapshot),
            new PluginChange.StatusMessage("Polyline finalized.")
        ], true, null))
        points.clear()
        arcCenter = null
        arcPass1 = null
        closed = false
    }

    private void addSegments(def list, List<Vector3> pts, boolean close) {
        for (int i = 0; i < pts.size() - 1; i++) {
            list.add(new ModelPersistence.SegmentDto(
                new ModelPersistence.Vec3Dto(pts[i]),
                new ModelPersistence.Vec3Dto(pts[i + 1])
            ))
        }
        if (close && pts.size() > 1) {
            list.add(new ModelPersistence.SegmentDto(
                new ModelPersistence.Vec3Dto(pts.last()),
                new ModelPersistence.Vec3Dto(pts.first())
            ))
        }
    }

    private ModelPersistence.ObjectPrototypeDto resolveTargetPrototype(def snapshot) {
        if (snapshot.rootInstance != null && snapshot.prototypes != null && !snapshot.prototypes.isEmpty()) {
            def rootId = snapshot.rootInstance.prototypeId
            def found = snapshot.prototypes.find { it.id == rootId }
            return found != null ? found : snapshot.prototypes[0]
        }
        return null
    }

    private List<Vector3> previewArc(Vector3 start, Vector3 cursor) {
        if (arcMode == "center" && arcCenter != null) {
            return arcPointsFromCenter(start, cursor, arcCenter, arcSteps())
        }
        if (arcMode == "three" && arcPass1 != null) {
            return arcPointsThrough(start, arcPass1, cursor, arcSteps())
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

    private int arcSteps() {
        return Math.max(3, settings.arcSegments)
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

class DoubleLineTool implements PluginTool {
    String id = "double_line"
    String name = "Double Line"
    String description = "Draw double parallel lines with line and arc modes."
    String icon = "tool"
    String cursor = "crosshair"
    ToolCategory category = ToolCategory.DRAWING
    TextureRegionDrawable iconDrawable = PluginContext.iconFromBase64("data:image/png,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAACXBIWXMAAA7DAAAOwwHHb6hkAAAAGXRFWHRTb2Z0d2FyZQB3d3cuaW5rc2NhcGUub3Jnm+48GgAAAjhJREFUWIXtlF9IU1Ecxz/3LrfFbLXN/kAtc0K0ZVbiSpAIVyK0BUEIkUawlyKIiJ6iHoIeegmyhx6CiCgiIoggLXoZiNhalBBE+KdEa0uiplJha7ndHrbBXU2c527zZV843HN/nO/5fs7hx4GyyspPcnqUVNVAFzDy8gBK/36SwBBwGVhb7PCjwAygAMqTNpRHral5evwEOgoRJOWodQK3AclW4x6rbe4cXOnYOaWQ5Ov7kPXD8zvOybGB9WmQDuBeIQFWA8OAubb5yGtn68mhXKZ3z65uGg3ebQC+AxuBL6IA/zbWMcBsWbclPFc4gKvt1KDFXh8GzMAJ0fBcAD6AavfBkfmMG9ztw2qPqJaof3p9bE8ocMmxI6qu18VeWQDeGhunMrUqR+NkwAs6ifrd3eIAWTeQUFJAhmVV8fmMhkpbXO0RVZbZ05P6+tzZi9Qnz7Vei0r+uhUM4PePb/qAFwLeRQBIJmel4K3jLQB6mZgWAKEGkkJdTk/lR5unh1FglxYAoRs4bXzserAH2h1cAz5rARC6gYsD0LACAhPcV9fjfrYC6G/ypqgAwajhT++nX3qgQsSvGcBktU/HZ6ZNwF7gRqa+kJNnJNQDa1wt4+npWWC5yB6aAGqaDo2brPZowIsjcpj+kgPIsi68ed+Zc6uWyhM2Izmf6Xy1gB5QIpIkhxRdxYvu89siAHVwXUt4HgD/hxZaWQCZd/2KJD2cVRKhpxeawsUInVMxP30xP30lDS1rsfUXfEKden9NU0sAAAAASUVORK5CYII=")

    private final PolylineSettings settings
    private final List<Vector3> points = []
    private String arcMode = "line"
    private Vector3 arcCenter = null
    private Vector3 arcPass1 = null
    private boolean closed = false
    private final float closeDistance = 0.15f
    private final float epsilon = 0.0001f

    DoubleLineTool(PolylineSettings settings) {
        this.settings = settings
    }

    @Override
    void onActivate(PluginContext context) {
        points.clear()
        arcMode = "line"
        arcCenter = null
        arcPass1 = null
        closed = false
    }

    @Override
    void onDeactivate(PluginContext context) {
        points.clear()
        arcMode = "line"
        arcCenter = null
        arcPass1 = null
        closed = false
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
            closed = true
            finalizeDoubleLine(context)
            return
        }
        if (arcMode == "center") {
            if (arcCenter == null) {
                arcCenter = new Vector3(hit)
                return
            }
            def arcPoints = arcPointsFromCenter(points.last(), hit, arcCenter, arcSteps())
            appendArcPoints(arcPoints)
            arcCenter = null
            return
        }
        if (arcMode == "three") {
            if (arcPass1 == null) {
                arcPass1 = new Vector3(hit)
                return
            }
            def arcPoints = arcPointsThrough(points.last(), arcPass1, hit, arcSteps())
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
            finalizeDoubleLine(context)
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
        shapeRenderer.color = new Color(0.35f, 0.75f, 0.95f, 1f)
        def renderPoints = new ArrayList<Vector3>()
        renderPoints.addAll(points)
        Vector3 cursor = context.cursor.world
        if (cursor != null) {
            def preview = previewArc(points.last(), cursor)
            if (preview != null && preview.size() > 1) {
                for (int i = 1; i < preview.size(); i++) {
                    renderPoints.add(preview[i])
                }
            } else {
                renderPoints.add(new Vector3(cursor))
            }
        }
        drawDoublePreview(shapeRenderer, renderPoints)
    }

    @Override
    void onUpdate(PluginContext context, float delta) {
        // No-op.
    }

    private boolean isClosing(Vector3 candidate) {
        if (points.size() < 2) {
            return false
        }
        return points.first().dst(candidate) <= closeDistance
    }

    private void finalizeDoubleLine(PluginContext context) {
        if (points.size() < 2) {
            points.clear()
            arcCenter = null
            arcPass1 = null
            closed = false
            return
        }
        def snapshot = context.model
        def target = resolveTargetPrototype(snapshot)
        def targetSegments = target != null ? target.segments : snapshot.segments
        def targetFaces = target != null ? target.faces : snapshot.faces
        def paths = buildDoublePaths(points)
        addDoubleSegments(targetSegments, paths.left, paths.right)
        addStripFaces(targetFaces, paths.left, paths.right, closed)
        context.getApplyResult().invoke(new PluginResult([
            new PluginChange.ReplaceModel(snapshot),
            new PluginChange.StatusMessage("Double line finalized.")
        ], true, null))
        points.clear()
        arcCenter = null
        arcPass1 = null
        closed = false
    }

    private ModelPersistence.ObjectPrototypeDto resolveTargetPrototype(def snapshot) {
        if (snapshot.rootInstance != null && snapshot.prototypes != null && !snapshot.prototypes.isEmpty()) {
            def rootId = snapshot.rootInstance.prototypeId
            def found = snapshot.prototypes.find { it.id == rootId }
            return found != null ? found : snapshot.prototypes[0]
        }
        return null
    }

    private void addDoubleSegments(def list, List<Vector3> left, List<Vector3> right) {
        if (left == null || right == null || left.size() < 2 || right.size() < 2) {
            return
        }
        def loop = new ArrayList<Vector3>()
        loop.addAll(left)
        def reversedRight = new ArrayList<Vector3>(right)
        java.util.Collections.reverse(reversedRight)
        loop.addAll(reversedRight)
        addPolylineSegments(list, loop, true)
    }

    private void drawDoublePreview(ShapeRenderer shapeRenderer, List<Vector3> pts) {
        if (pts.size() < 2) {
            return
        }
        float size = Math.max(0f, settings.doubleLineSize)
        float offset = settings.doubleLineOffset
        float half = size * 0.5f
        float offsetA = offset - half
        float offsetB = offset + half
        def left = buildOffsetPath(pts, offsetA, closed)
        def right = buildOffsetPath(pts, offsetB, closed)
        drawPolylinePreview(shapeRenderer, left, closed)
        drawPolylinePreview(shapeRenderer, right, closed)
        if (!closed && left.size() > 0 && right.size() > 0) {
            shapeRenderer.line(left.first(), right.first())
            shapeRenderer.line(left.last(), right.last())
        }
    }

    private void drawPolylinePreview(ShapeRenderer shapeRenderer, List<Vector3> pts, boolean close) {
        if (pts.size() < 2) {
            return
        }
        for (int i = 0; i < pts.size() - 1; i++) {
            shapeRenderer.line(pts[i], pts[i + 1])
        }
        if (close) {
            shapeRenderer.line(pts.last(), pts.first())
        }
    }

    private void addPolylineSegments(def list, List<Vector3> pts, boolean close) {
        if (pts.size() < 2) {
            return
        }
        for (int i = 0; i < pts.size() - 1; i++) {
            list.add(new ModelPersistence.SegmentDto(
                new ModelPersistence.Vec3Dto(pts[i]),
                new ModelPersistence.Vec3Dto(pts[i + 1])
            ))
        }
        if (close) {
            list.add(new ModelPersistence.SegmentDto(
                new ModelPersistence.Vec3Dto(pts.last()),
                new ModelPersistence.Vec3Dto(pts.first())
            ))
        }
    }

    private Map buildDoublePaths(List<Vector3> pts) {
        float size = Math.max(0f, settings.doubleLineSize)
        float offset = settings.doubleLineOffset
        float half = size * 0.5f
        float offsetA = offset - half
        float offsetB = offset + half
        def left = buildOffsetPath(pts, offsetA, closed)
        def right = buildOffsetPath(pts, offsetB, closed)
        return [left: left, right: right]
    }

    private void addStripFaces(def list, List<Vector3> left, List<Vector3> right, boolean close) {
        if (left == null || right == null) {
            return
        }
        int count = Math.min(left.size(), right.size())
        if (count < 2) {
            return
        }
        int limit = close ? count : count - 1
        for (int i = 0; i < limit; i++) {
            int next = (i + 1) % count
            Vector3 a = left[i]
            Vector3 b = left[next]
            Vector3 c = right[next]
            Vector3 d = right[i]
            list.add(new ModelPersistence.FaceDto(
                new ModelPersistence.Vec3Dto(a),
                new ModelPersistence.Vec3Dto(b),
                new ModelPersistence.Vec3Dto(c),
                new ModelPersistence.ColorDto(new Color(0.8f, 0.8f, 0.8f, 1f))
            ))
            list.add(new ModelPersistence.FaceDto(
                new ModelPersistence.Vec3Dto(a),
                new ModelPersistence.Vec3Dto(c),
                new ModelPersistence.Vec3Dto(d),
                new ModelPersistence.ColorDto(new Color(0.8f, 0.8f, 0.8f, 1f))
            ))
        }
    }

    private List<Vector3> buildOffsetPath(List<Vector3> base, float offset, boolean isClosed) {
        def result = new ArrayList<Vector3>()
        int count = base.size()
        for (int i = 0; i < count; i++) {
            Vector3 curr = base[i]
            Vector3 prev = i > 0 ? base[i - 1] : (isClosed ? base[count - 1] : null)
            Vector3 next = i < count - 1 ? base[i + 1] : (isClosed ? base[0] : null)
            Vector3 perpPrev = prev != null ? segmentPerp(prev, curr) : null
            Vector3 perpNext = next != null ? segmentPerp(curr, next) : null
            Vector3 offsetDir = null
            if (perpPrev != null && perpNext != null) {
                Vector3 miter = new Vector3(perpPrev).add(perpNext)
                if (miter.len2() > epsilon * epsilon) {
                    miter.nor()
                    float denom = miter.dot(perpPrev)
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
                Vector3 fallback = perpPrev != null ? perpPrev : perpNext
                if (fallback != null) {
                    offsetDir = new Vector3(fallback).scl(offset)
                } else {
                    offsetDir = new Vector3(0f, 0f, 0f)
                }
            }
            if (!isFinite(offsetDir)) {
                offsetDir.set(0f, 0f, 0f)
            }
            result.add(new Vector3(curr).add(offsetDir))
        }
        return result
    }

    private Vector3 segmentPerp(Vector3 start, Vector3 end) {
        Vector3 dir = new Vector3(end).sub(start)
        if (dir.len2() <= epsilon * epsilon) {
            return null
        }
        dir.nor()
        Vector3 perp = new Vector3(dir).crs(0f, 1f, 0f)
        if (perp.len2() <= epsilon * epsilon) {
            return null
        }
        return perp.nor()
    }

    private boolean isFinite(Vector3 vec) {
        return Float.isFinite(vec.x) && Float.isFinite(vec.y) && Float.isFinite(vec.z)
    }

    private List<Vector3> previewArc(Vector3 start, Vector3 cursor) {
        if (arcMode == "center" && arcCenter != null) {
            return arcPointsFromCenter(start, cursor, arcCenter, arcSteps())
        }
        if (arcMode == "three" && arcPass1 != null) {
            return arcPointsThrough(start, arcPass1, cursor, arcSteps())
        }
        return null
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

    private int arcSteps() {
        return Math.max(3, settings.arcSegments)
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

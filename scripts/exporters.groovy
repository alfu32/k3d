import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Array
import com.github.alfu32.sketch.plugin.Plugin
import com.github.alfu32.sketch.plugin.PluginChange
import com.github.alfu32.sketch.plugin.PluginContext
import com.github.alfu32.sketch.plugin.PluginResult
import com.github.alfu32.sketch.plugin.capabilities.KeyBinding
import com.github.alfu32.sketch.plugin.capabilities.PluginCommand
import com.github.alfu32.sketch.plugin.capabilities.PluginTool
import com.github.alfu32.sketch.plugin.capabilities.ToolCategory
import com.kotcrab.vis.ui.widget.file.FileChooser
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter
import com.kotcrab.vis.ui.widget.file.FileTypeFilter

import java.io.File
import java.util.Locale

class ExportersPlugin implements Plugin {
    String id = "exporters"
    String name = "Exporters"
    String version = "0.1"
    String author = "Sketch3D"
    String description = "Adds OBJ and STL(ASCII) export commands."

    @Override
    List<PluginCommand> registerCommands() {
        return [
            makeCommand("export.obj", "Export> OBJ", "Export model as OBJ", "obj"),
            makeCommand("export.stl", "Export> STL(ASCII)", "Export model as STL (ASCII)", "stl")
        ]
    }

    @Override
    List<PluginTool> registerTools() {
        return [
            new ExporterTool(this, "export.obj.tool", "Export OBJ", "Export model as OBJ", "obj"),
            new ExporterTool(this, "export.stl.tool", "Export STL", "Export model as STL (ASCII)", "stl")
        ]
    }

    private PluginCommand makeCommand(String id, String name, String description, String ext) {
        return new ExportCommand(
            id,
            name,
            description,
            "Export",
            null,
            "export",
            true,
            { PluginContext context -> showSaveDialog(context, name, ext) }
        )
    }

    private PluginResult showSaveDialog(PluginContext context, String title, String ext) {
        Stage stage = findStage()
        if (stage == null) {
            return statusResult("Export failed: no UI stage found.", false)
        }

        FileChooser chooser = new FileChooser(System.getProperty("user.dir"), FileChooser.Mode.SAVE)
        chooser.getTitleLabel().setText(title)
        chooser.setSelectionMode(FileChooser.SelectionMode.FILES)
        chooser.setDefaultFileName("export.${ext}")

        FileTypeFilter filter = new FileTypeFilter(true)
        filter.addRule(ext.toUpperCase(Locale.US), ext)
        chooser.setFileTypeFilter(filter)

        chooser.setListener(new FileChooserAdapter() {
            @Override
            void selected(Array<FileHandle> files) {
                if (files == null || files.size == 0) {
                    return
                }
                FileHandle handle = files.first()
                File target = ensureExtension(handle.file(), ext)
                exportModel(context, ext, target)
            }

            @Override
            void canceled() {
                Gdx.app.log("Exporters", "Export canceled.")
            }
        })

        stage.addActor(chooser)
        return statusResult("Choose a file to export ${ext.toUpperCase(Locale.US)}.")
    }

    private static class ExporterTool implements PluginTool {
        private final ExportersPlugin plugin
        String id
        String name
        String description
        String icon = "export"
        String cursor = "default"
        ToolCategory category = ToolCategory.UTILITY
        private final String ext

        ExporterTool(ExportersPlugin plugin, String id, String name, String description, String ext) {
            this.plugin = plugin
            this.id = id
            this.name = name
            this.description = description
            this.ext = ext
        }

        @Override
        void onActivate(PluginContext context) {
            def result = plugin.showSaveDialog(context, name, ext)
            if (result != null) {
                context.applyResult(result)
            }
        }

        @Override
        void onDeactivate(PluginContext context) {}

        @Override
        void onMouseMove(PluginContext context, int screenX, int screenY) {}

        @Override
        void onMouseDown(PluginContext context, int button, int screenX, int screenY) {}

        @Override
        void onMouseUp(PluginContext context, int button, int screenX, int screenY) {}

        @Override
        boolean onKeyDown(PluginContext context, int keycode) { false }

        @Override
        boolean onKeyUp(PluginContext context, int keycode) { false }

        @Override
        void onDraw2D(PluginContext context, com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {}

        @Override
        void onDraw3D(PluginContext context, com.badlogic.gdx.graphics.glutils.ShapeRenderer shapeRenderer) {}

        @Override
        void onUpdate(PluginContext context, float delta) {}
    }

    private void exportModel(PluginContext context, String ext, File file) {
        List<float[]> triangles = collectTriangles(context)
        if (triangles.isEmpty()) {
            Gdx.app.log("Exporters", "No triangles to export.")
            return
        }

        if (ext == "obj") {
            writeObj(triangles, file)
        } else if (ext == "stl") {
            writeStl(triangles, file)
        }
        Gdx.app.log("Exporters", "Exported ${triangles.size()} triangles to ${file}.")
    }

    private List<float[]> collectTriangles(PluginContext context) {
        def snapshot = context.model
        List<float[]> out = []
        if (snapshot.rootGroup != null) {
            Matrix4 identity = new Matrix4().idt()
            collectFromGroup(snapshot.rootGroup, identity, out)
        } else {
            snapshot.faces.each { face ->
                out.add(faceToTriangle(face))
            }
        }
        return out
    }

    private void collectFromGroup(def group, Matrix4 parentMatrix, List<float[]> out) {
        Matrix4 world = new Matrix4(parentMatrix)
        world.mul(instanceMatrix(group))
        world.mul(definitionMatrix(group))

        group.faces.each { face ->
            def tri = faceToTriangle(face)
            out.add(transformTriangle(world, tri))
        }
        group.children.each { child ->
            collectFromGroup(child, world, out)
        }
    }

    private float[] faceToTriangle(def face) {
        return [
            face.a.x, face.a.y, face.a.z,
            face.b.x, face.b.y, face.b.z,
            face.c.x, face.c.y, face.c.z
        ] as float[]
    }

    private float[] transformTriangle(Matrix4 matrix, float[] tri) {
        float[] out = new float[9]
        for (int i = 0; i < 3; i++) {
            int base = i * 3
            Vector3 v = new Vector3(tri[base] as float, tri[base + 1] as float, tri[base + 2] as float)
            Vector3 w = transformPoint(matrix, v)
            out[base] = w.x
            out[base + 1] = w.y
            out[base + 2] = w.z
        }
        return out
    }

    private Matrix4 definitionMatrix(def group) {
        Vector3 origin = vecOrDefault(group.definitionOrigin, 0f, 0f, 0f)
        Vector3 u = vecOrDefault(group.definitionAxisU, 1f, 0f, 0f)
        Vector3 v = vecOrDefault(group.definitionAxisV, 0f, 1f, 0f)
        Vector3 w = vecOrDefault(group.definitionAxisW, 0f, 0f, 1f)
        return matrixFromAxes(origin, u, v, w)
    }

    private Matrix4 instanceMatrix(def group) {
        Vector3 origin = vecOrDefault(group.instanceOrigin, group.origin.x, group.origin.y, group.origin.z)
        Vector3 u = vecOrDefault(group.instanceAxisU, group.axisU.x, group.axisU.y, group.axisU.z)
        Vector3 v = vecOrDefault(group.instanceAxisV, group.axisV.x, group.axisV.y, group.axisV.z)
        Vector3 w = vecOrDefault(group.instanceAxisW, group.axisW.x, group.axisW.y, group.axisW.z)
        return matrixFromAxes(origin, u, v, w)
    }

    private Vector3 vecOrDefault(def dto, float dx, float dy, float dz) {
        if (dto == null) {
            return new Vector3(dx, dy, dz)
        }
        return new Vector3(dto.x, dto.y, dto.z)
    }

    private Matrix4 matrixFromAxes(Vector3 origin, Vector3 u, Vector3 v, Vector3 w) {
        Matrix4 matrix = new Matrix4()
        def m = matrix.val
        m[Matrix4.M00] = u.x
        m[Matrix4.M10] = u.y
        m[Matrix4.M20] = u.z
        m[Matrix4.M30] = 0f

        m[Matrix4.M01] = v.x
        m[Matrix4.M11] = v.y
        m[Matrix4.M21] = v.z
        m[Matrix4.M31] = 0f

        m[Matrix4.M02] = w.x
        m[Matrix4.M12] = w.y
        m[Matrix4.M22] = w.z
        m[Matrix4.M32] = 0f

        m[Matrix4.M03] = origin.x
        m[Matrix4.M13] = origin.y
        m[Matrix4.M23] = origin.z
        m[Matrix4.M33] = 1f
        return matrix
    }

    private Vector3 transformPoint(Matrix4 matrix, Vector3 point) {
        def v = matrix.val
        float x = point.x
        float y = point.y
        float z = point.z
        return new Vector3(
            (x * v[Matrix4.M00] + y * v[Matrix4.M01] + z * v[Matrix4.M02] + v[Matrix4.M03]) as float,
            (x * v[Matrix4.M10] + y * v[Matrix4.M11] + z * v[Matrix4.M12] + v[Matrix4.M13]) as float,
            (x * v[Matrix4.M20] + y * v[Matrix4.M21] + z * v[Matrix4.M22] + v[Matrix4.M23]) as float
        )
    }

    private void writeObj(List<float[]> triangles, File file) {
        StringBuilder sb = new StringBuilder()
        sb.append("# Sketch3D OBJ export\n")
        int index = 1
        triangles.each { tri ->
            for (int i = 0; i < 3; i++) {
                int base = i * 3
                sb.append("v ")
                sb.append(fmt(tri[base])).append(" ")
                sb.append(fmt(tri[base + 1])).append(" ")
                sb.append(fmt(tri[base + 2])).append("\n")
            }
            sb.append("f ").append(index).append(" ").append(index + 1).append(" ").append(index + 2).append("\n")
            index += 3
        }
        writeText(file, sb.toString())
    }

    private void writeStl(List<float[]> triangles, File file) {
        StringBuilder sb = new StringBuilder()
        sb.append("solid sketch3d\n")
        triangles.each { tri ->
            Vector3 a = new Vector3(tri[0] as float, tri[1] as float, tri[2] as float)
            Vector3 b = new Vector3(tri[3] as float, tri[4] as float, tri[5] as float)
            Vector3 c = new Vector3(tri[6] as float, tri[7] as float, tri[8] as float)
            Vector3 normal = new Vector3(b).sub(a).crs(new Vector3(c).sub(a))
            if (normal.len2() > 0f) {
                normal.nor()
            }
            sb.append("  facet normal ").append(fmt(normal.x)).append(" ")
                .append(fmt(normal.y)).append(" ").append(fmt(normal.z)).append("\n")
            sb.append("    outer loop\n")
            sb.append("      vertex ").append(fmt(a.x)).append(" ").append(fmt(a.y)).append(" ").append(fmt(a.z)).append("\n")
            sb.append("      vertex ").append(fmt(b.x)).append(" ").append(fmt(b.y)).append(" ").append(fmt(b.z)).append("\n")
            sb.append("      vertex ").append(fmt(c.x)).append(" ").append(fmt(c.y)).append(" ").append(fmt(c.z)).append("\n")
            sb.append("    endloop\n")
            sb.append("  endfacet\n")
        }
        sb.append("endsolid sketch3d\n")
        writeText(file, sb.toString())
    }

    private void writeText(File file, String text) {
        file.parentFile?.mkdirs()
        file.withWriter("UTF-8") { it << text }
    }

    private String fmt(float value) {
        return String.format(Locale.US, "%.6f", value)
    }

    private File ensureExtension(File file, String ext) {
        String name = file.name
        if (!name.toLowerCase(Locale.US).endsWith("." + ext)) {
            return new File(file.parentFile, name + "." + ext)
        }
        return file
    }

    private Stage findStage() {
        def input = Gdx.input.inputProcessor
        if (input instanceof Stage) {
            return (Stage) input
        }
        if (input instanceof InputMultiplexer) {
            def processors = input.getProcessors()
            for (int i = 0; i < processors.size; i++) {
                def proc = processors.get(i)
                if (proc instanceof Stage) {
                    return (Stage) proc
                }
            }
        }
        return null
    }

    private PluginResult statusResult(String message, boolean success = true) {
        def changesList = [new PluginChange.StatusMessage(message)]
        def msg = success ? null : message
        return new PluginResult(changesList, success, msg)
    }
}

class ExportCommand implements PluginCommand {
    private final String id
    private final String name
    private final String description
    private final String category
    private final KeyBinding shortcut
    private final String icon
    private final boolean isVisibleInPalette
    private final Closure<PluginResult> executeAction

    ExportCommand(
        String id,
        String name,
        String description,
        String category,
        KeyBinding shortcut,
        String icon,
        boolean isVisibleInPalette,
        Closure<PluginResult> executeAction
    ) {
        this.id = id
        this.name = name
        this.description = description
        this.category = category
        this.shortcut = shortcut
        this.icon = icon
        this.isVisibleInPalette = isVisibleInPalette
        this.executeAction = executeAction
    }

    @Override
    String getId() { return id }

    @Override
    String getName() { return name }

    @Override
    String getDescription() { return description }

    @Override
    String getCategory() { return category }

    @Override
    KeyBinding getShortcut() { return shortcut }

    @Override
    String getIcon() { return icon }

    @Override
    boolean isVisibleInPalette() { return isVisibleInPalette }

    @Override
    PluginResult execute(PluginContext context) {
        return executeAction.call(context)
    }

    @Override
    List<String> getSearchTags() {
        return [name.toLowerCase(Locale.US)]
    }
}

return new ExportersPlugin()

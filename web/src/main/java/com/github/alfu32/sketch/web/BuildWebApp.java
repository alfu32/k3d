package com.github.alfu32.sketch.web;

import com.github.xpenatan.gdx.teavm.backends.shared.config.AssetFileHandle;
import com.github.xpenatan.gdx.teavm.backends.shared.config.compiler.TeaCompiler;
import com.github.xpenatan.gdx.teavm.backends.web.config.backend.WebBackend;
import java.io.File;
import org.teavm.vm.TeaVMOptimizationLevel;

public final class BuildWebApp {
    private BuildWebApp() {
    }

    public static void main(String[] args) {
        String outputRoot = System.getProperty("octodraw.web.output", "build/dist");
        String outputFolder = System.getProperty("octodraw.web.folder", "webapp");
        String assetsPath = System.getProperty("octodraw.web.assets", "../assets");
        String title = System.getProperty("octodraw.web.title", "Octodraw");
        String outputName = System.getProperty("octodraw.web.outputName", "octodraw");
        boolean serve = Boolean.parseBoolean(System.getProperty("octodraw.web.serve", "false"));
        int port = Integer.getInteger("octodraw.web.port", 8766);

        WebBackend backend = new WebBackend()
            .setStartJettyAfterBuild(serve)
            .setJettyPort(port)
            .setHtmlTitle(title)
            .setWebappFolderName(outputFolder)
            .setHtmlWidth(0)
            .setHtmlHeight(0)
            .setCopyLoadingAsset(true);

        TeaCompiler compiler = new TeaCompiler(backend)
            .addAssets(new AssetFileHandle(assetsPath))
            .setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE)
            .setMainClass(OctodrawWebLauncher.class.getName())
            .setOutputName(outputName)
            .setObfuscated(false)
            .setDebugInformationGenerated(false)
            .setSourceMapsFileGenerated(false);

        String[] visUiReflectionClasses = new String[] {
            "com.kotcrab.vis.ui.Sizes",
            "com.kotcrab.vis.ui.util.adapter.SimpleListAdapter$SimpleListAdapterStyle",
            "com.kotcrab.vis.ui.util.form.SimpleFormValidator$FormValidatorStyle",
            "com.kotcrab.vis.ui.widget.BusyBar$BusyBarStyle",
            "com.kotcrab.vis.ui.widget.LinkLabel$LinkLabelStyle",
            "com.kotcrab.vis.ui.widget.ListViewStyle",
            "com.kotcrab.vis.ui.widget.Menu$MenuStyle",
            "com.kotcrab.vis.ui.widget.MenuBar$MenuBarStyle",
            "com.kotcrab.vis.ui.widget.MenuItem$MenuItemStyle",
            "com.kotcrab.vis.ui.widget.MultiSplitPane$MultiSplitPaneStyle",
            "com.kotcrab.vis.ui.widget.PopupMenu$PopupMenuStyle",
            "com.kotcrab.vis.ui.widget.Separator$SeparatorStyle",
            "com.kotcrab.vis.ui.widget.Tooltip$TooltipStyle",
            "com.kotcrab.vis.ui.widget.VisCheckBox$VisCheckBoxStyle",
            "com.kotcrab.vis.ui.widget.VisImageButton$VisImageButtonStyle",
            "com.kotcrab.vis.ui.widget.VisImageTextButton$VisImageTextButtonStyle",
            "com.kotcrab.vis.ui.widget.VisSplitPane$VisSplitPaneStyle",
            "com.kotcrab.vis.ui.widget.VisTextButton$VisTextButtonStyle",
            "com.kotcrab.vis.ui.widget.VisTextField$VisTextFieldStyle",
            "com.kotcrab.vis.ui.widget.color.ColorPickerStyle",
            "com.kotcrab.vis.ui.widget.color.ColorPickerWidgetStyle",
            "com.kotcrab.vis.ui.widget.file.FileChooserStyle",
            "com.kotcrab.vis.ui.widget.spinner.Spinner$SpinnerStyle",
            "com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane$TabbedPaneStyle",
            "com.kotcrab.vis.ui.widget.toast.Toast$ToastStyle"
        };
        for (String className : visUiReflectionClasses) {
            compiler.addReflectionClass(className);
        }

        compiler.build(new File(outputRoot));
    }
}

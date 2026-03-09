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

        new TeaCompiler(backend)
            .addAssets(new AssetFileHandle(assetsPath))
            .setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE)
            .setMainClass(OctodrawWebLauncher.class.getName())
            .setOutputName("octodraw")
            .setObfuscated(false)
            .setDebugInformationGenerated(false)
            .setSourceMapsFileGenerated(false)
            .build(new File(outputRoot));
    }
}

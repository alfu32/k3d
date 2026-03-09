package com.github.alfu32.sketch.web;

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;

public final class OctodrawWebLauncher {
    private OctodrawWebLauncher() {
    }

    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration("canvas");
        config.width = 0;
        config.height = 0;
        config.showDownloadLogs = false;
        config.useGL30 = true;
        config.storagePrefix = "octodraw";
        config.localStoragePrefix = "octodraw";
        config.shouldEncodePreference = true;
        config.alpha = true;
        config.antialiasing = true;
        config.premultipliedAlpha = true;

        new WebApplication(new OctodrawWebApp(), config);
    }
}

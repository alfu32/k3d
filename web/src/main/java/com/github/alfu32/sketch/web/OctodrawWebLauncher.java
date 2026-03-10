package com.github.alfu32.sketch.web;

import com.github.alfu32.sketch.Main;
import com.github.alfu32.sketch.WebRuntime;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;

public final class OctodrawWebLauncher {
    private OctodrawWebLauncher() {
    }

    public static void main(String[] args) {
        WebRuntimeBridgeImpl bridge = new WebRuntimeBridgeImpl();
        WebRuntime.INSTANCE.setBridge(bridge);

        String canvasId = "canvas";
        int padHorizontal = 0;
        int padVertical = 0;
        String embedConfigJson = bridge.readEmbedConfigJson();
        if (embedConfigJson != null && embedConfigJson.contains("\"canvasId\"")) {
            String extractedCanvasId = extractJsonString(embedConfigJson, "canvasId");
            if (extractedCanvasId != null && !extractedCanvasId.isBlank()) {
                canvasId = extractedCanvasId;
            }
            padHorizontal = extractJsonInt(embedConfigJson, "padHorizontal", 0);
            padVertical = extractJsonInt(embedConfigJson, "padVertical", 0);
        }

        WebApplicationConfiguration config = new WebApplicationConfiguration(canvasId);
        config.width = 0;
        config.height = 0;
        config.padHorizontal = Math.max(0, padHorizontal);
        config.padVertical = Math.max(0, padVertical);
        config.showDownloadLogs = false;
        config.useGL30 = true;
        config.baseUrlProvider = new OctodrawWebBaseUrlProvider();
        config.storagePrefix = "octodraw";
        config.localStoragePrefix = "octodraw";
        config.shouldEncodePreference = true;
        config.alpha = true;
        config.antialiasing = true;
        config.premultipliedAlpha = true;

        new WebApplication(new Main(new String[0], Main.RuntimeProfile.WEB_SAFE), config);
    }

    private static String extractJsonString(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex + marker.length());
        if (colonIndex < 0) {
            return null;
        }
        int startQuote = json.indexOf('"', colonIndex + 1);
        if (startQuote < 0) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                out.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                return out.toString();
            }
            out.append(ch);
        }
        return null;
    }

    private static int extractJsonInt(String json, String key, int defaultValue) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return defaultValue;
        }
        int colonIndex = json.indexOf(':', keyIndex + marker.length());
        if (colonIndex < 0) {
            return defaultValue;
        }
        int start = colonIndex + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length()) {
            char ch = json.charAt(end);
            if ((ch >= '0' && ch <= '9') || ch == '-') {
                end++;
                continue;
            }
            break;
        }
        if (end <= start) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}

package com.github.alfu32.sketch.web;

import com.github.xpenatan.gdx.teavm.backends.web.utils.WebBaseUrlProvider;
import org.teavm.jso.JSBody;

public final class OctodrawWebBaseUrlProvider implements WebBaseUrlProvider {
    @Override
    public String getBaseUrl() {
        String embedBase = readEmbedBaseUrl();
        if (embedBase != null && !embedBase.isBlank()) {
            return ensureTrailingSlash(embedBase);
        }
        String locationHref = readLocationHref();
        if (locationHref == null || locationHref.isBlank()) {
            return "";
        }
        return ensureTrailingSlash(stripToDirectory(locationHref));
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static String stripToDirectory(String href) {
        int queryIndex = href.indexOf('?');
        String trimmed = queryIndex >= 0 ? href.substring(0, queryIndex) : href;
        int hashIndex = trimmed.indexOf('#');
        if (hashIndex >= 0) {
            trimmed = trimmed.substring(0, hashIndex);
        }
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash < 0) {
            return trimmed;
        }
        return trimmed.substring(0, lastSlash + 1);
    }

    @JSBody(script =
            "try {\n" +
            "  var state = window.__octodrawEmbedState;\n" +
            "  return state && state.baseUrl ? String(state.baseUrl) : null;\n" +
            "} catch (e) {\n" +
            "  return null;\n" +
            "}")
    private static native String readEmbedBaseUrl();

    @JSBody(script =
            "try {\n" +
            "  return String(window.location.href || '');\n" +
            "} catch (e) {\n" +
            "  return null;\n" +
            "}")
    private static native String readLocationHref();
}

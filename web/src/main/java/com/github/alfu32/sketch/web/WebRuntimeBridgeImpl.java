package com.github.alfu32.sketch.web;

import com.github.alfu32.sketch.WebOpenTextHandler;
import com.github.alfu32.sketch.WebRuntimeBridge;
import com.github.alfu32.sketch.WebSaveTextHandler;
import org.teavm.jso.JSBody;

public final class WebRuntimeBridgeImpl implements WebRuntimeBridge {
    @Override
    public void openTextDocument(String accept, WebOpenTextHandler handler) {
        openTextDocumentJs(accept == null ? "" : accept, handler);
    }

    @Override
    public void saveTextDocument(String fileName, String content, String mimeType, WebSaveTextHandler handler) {
        String resolvedName = (fileName == null || fileName.isBlank()) ? "octodraw-web.octd" : fileName;
        String resolvedMime = (mimeType == null || mimeType.isBlank()) ? "application/json" : mimeType;
        saveTextDocumentJs(resolvedName, content == null ? "" : content, resolvedMime, handler);
    }

    @Override
    public String readLocalStorage(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return readLocalStorageJs(key);
    }

    @Override
    public boolean writeLocalStorage(String key, String value) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return writeLocalStorageJs(key, value == null ? "" : value);
    }

    @JSBody(params = {"accept", "handler"}, script =
            "try {\n" +
            "  var input = document.createElement('input');\n" +
            "  input.type = 'file';\n" +
            "  input.accept = accept || '';\n" +
            "  input.style.display = 'none';\n" +
            "  var done = false;\n" +
            "  function finish(name, text) {\n" +
            "    if (done) return;\n" +
            "    done = true;\n" +
            "    try { handler.onResult(name, text); } catch (_) {}\n" +
            "    if (input && input.parentNode) input.parentNode.removeChild(input);\n" +
            "  }\n" +
            "  input.onchange = function() {\n" +
            "    var file = (input.files && input.files.length > 0) ? input.files[0] : null;\n" +
            "    if (!file) { finish(null, null); return; }\n" +
            "    var reader = new FileReader();\n" +
            "    reader.onload = function() { finish(file.name || null, String(reader.result || '')); };\n" +
            "    reader.onerror = function() { finish(file.name || null, null); };\n" +
            "    reader.readAsText(file);\n" +
            "  };\n" +
            "  document.body.appendChild(input);\n" +
            "  input.click();\n" +
            "} catch (e) {\n" +
            "  try { handler.onResult(null, null); } catch (_) {}\n" +
            "}")
    private static native void openTextDocumentJs(String accept, WebOpenTextHandler handler);

    @JSBody(params = {"fileName", "content", "mimeType", "handler"}, script =
            "try {\n" +
            "  var blob = new Blob([content], { type: mimeType || 'application/json' });\n" +
            "  var url = URL.createObjectURL(blob);\n" +
            "  var a = document.createElement('a');\n" +
            "  a.href = url;\n" +
            "  a.download = fileName || 'octodraw-web.octd';\n" +
            "  a.style.display = 'none';\n" +
            "  document.body.appendChild(a);\n" +
            "  a.click();\n" +
            "  setTimeout(function() {\n" +
            "    try { URL.revokeObjectURL(url); } catch (_) {}\n" +
            "    if (a && a.parentNode) a.parentNode.removeChild(a);\n" +
            "  }, 0);\n" +
            "  handler.onResult(true, null);\n" +
            "} catch (e) {\n" +
            "  var message = (e && e.message) ? String(e.message) : String(e);\n" +
            "  handler.onResult(false, message);\n" +
            "}")
    private static native void saveTextDocumentJs(String fileName, String content, String mimeType, WebSaveTextHandler handler);

    @JSBody(params = {"key"}, script =
            "try {\n" +
            "  return window.localStorage.getItem(key);\n" +
            "} catch (e) {\n" +
            "  return null;\n" +
            "}")
    private static native String readLocalStorageJs(String key);

    @JSBody(params = {"key", "value"}, script =
            "try {\n" +
            "  window.localStorage.setItem(key, value);\n" +
            "  return true;\n" +
            "} catch (e) {\n" +
            "  return false;\n" +
            "}")
    private static native boolean writeLocalStorageJs(String key, String value);
}

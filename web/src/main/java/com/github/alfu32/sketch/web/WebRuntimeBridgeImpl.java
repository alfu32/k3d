package com.github.alfu32.sketch.web;

import com.github.alfu32.sketch.WebOpenBinaryHandler;
import com.github.alfu32.sketch.WebOpenTextHandler;
import com.github.alfu32.sketch.WebRuntimeBridge;
import com.github.alfu32.sketch.WebSaveTextHandler;
import org.teavm.jso.JSBody;

public final class WebRuntimeBridgeImpl implements WebRuntimeBridge {
    @Override
    public String readEmbedConfigJson() {
        return readEmbedConfigJsonJs();
    }

    @Override
    public String pollEmbedCommandJson() {
        return pollEmbedCommandJsonJs();
    }

    @Override
    public void resolveEmbedCommand(String requestId, boolean success, String payloadJson, String message) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        resolveEmbedCommandJs(requestId, success, payloadJson, message);
    }

    @Override
    public void emitEmbedEvent(String eventType, String detailJson) {
        if (eventType == null || eventType.isBlank()) {
            return;
        }
        emitEmbedEventJs(eventType, detailJson);
    }

    @Override
    public void openTextDocument(String accept, WebOpenTextHandler handler) {
        openTextDocumentJs(accept == null ? "" : accept, handler);
    }

    @Override
    public void openBinaryDocument(String accept, WebOpenBinaryHandler handler) {
        openBinaryDocumentJs(accept == null ? "" : accept, handler);
    }

    @Override
    public void saveTextDocument(String fileName, String content, String mimeType, WebSaveTextHandler handler) {
        String resolvedName = (fileName == null || fileName.isBlank()) ? "octodraw-web.octd" : fileName;
        String resolvedMime = (mimeType == null || mimeType.isBlank()) ? "application/json" : mimeType;
        saveTextDocumentJs(resolvedName, content == null ? "" : content, resolvedMime, handler);
    }

    @Override
    public void saveBinaryDocument(String fileName, String base64Content, String mimeType, WebSaveTextHandler handler) {
        String resolvedName = (fileName == null || fileName.isBlank()) ? "octodraw-web.bin" : fileName;
        String resolvedMime = (mimeType == null || mimeType.isBlank()) ? "application/octet-stream" : mimeType;
        saveBinaryDocumentJs(resolvedName, base64Content == null ? "" : base64Content, resolvedMime, handler);
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
            "  var done = false;\n" +
            "  function finish(name, text) {\n" +
            "    if (done) return;\n" +
            "    done = true;\n" +
            "    try { handler.onResult(name, text); } catch (_) {}\n" +
            "  }\n" +
            "  function openWithInput() {\n" +
            "    var input = document.createElement('input');\n" +
            "    input.type = 'file';\n" +
            "    input.accept = accept || '';\n" +
            "    input.style.display = 'none';\n" +
            "    input.onchange = function() {\n" +
            "      var file = (input.files && input.files.length > 0) ? input.files[0] : null;\n" +
            "      if (!file) { finish(null, null); return; }\n" +
            "      var reader = new FileReader();\n" +
            "      reader.onload = function() { finish(file.name || null, String(reader.result || '')); };\n" +
            "      reader.onerror = function() { finish(file.name || null, null); };\n" +
            "      reader.readAsText(file);\n" +
            "    };\n" +
            "    document.body.appendChild(input);\n" +
            "    input.click();\n" +
            "    setTimeout(function() { if (input && input.parentNode) input.parentNode.removeChild(input); }, 0);\n" +
            "  }\n" +
            "  if (window.showOpenFilePicker) {\n" +
            "    window.showOpenFilePicker({ multiple: false })\n" +
            "      .then(function(handles) { return handles && handles[0] ? handles[0].getFile() : null; })\n" +
            "      .then(function(file) {\n" +
            "        if (!file) { finish(null, null); return; }\n" +
            "        return file.text().then(function(text) { finish(file.name || null, String(text || '')); }, function() { finish(file.name || null, null); });\n" +
            "      }, function(error) {\n" +
            "        if (error && error.name === 'AbortError') { finish(null, null); return; }\n" +
            "        openWithInput();\n" +
            "      });\n" +
            "    return;\n" +
            "  }\n" +
            "  openWithInput();\n" +
            "} catch (e) {\n" +
            "  try { handler.onResult(null, null); } catch (_) {}\n" +
            "}")
    private static native void openTextDocumentJs(String accept, WebOpenTextHandler handler);

    @JSBody(params = {"accept", "handler"}, script =
            "try {\n" +
            "  var done = false;\n" +
            "  function finish(name, data) {\n" +
            "    if (done) return;\n" +
            "    done = true;\n" +
            "    try { handler.onResult(name, data); } catch (_) {}\n" +
            "  }\n" +
            "  function readFile(file) {\n" +
            "    if (!file) { finish(null, null); return; }\n" +
            "    var reader = new FileReader();\n" +
            "    reader.onload = function() {\n" +
            "      var result = String(reader.result || '');\n" +
            "      var comma = result.indexOf(',');\n" +
            "      finish(file.name || null, comma >= 0 ? result.substring(comma + 1) : null);\n" +
            "    };\n" +
            "    reader.onerror = function() { finish(file.name || null, null); };\n" +
            "    reader.readAsDataURL(file);\n" +
            "  }\n" +
            "  function openWithInput() {\n" +
            "    var input = document.createElement('input');\n" +
            "    input.type = 'file';\n" +
            "    input.accept = accept || '';\n" +
            "    input.style.display = 'none';\n" +
            "    input.onchange = function() {\n" +
            "      var file = (input.files && input.files.length > 0) ? input.files[0] : null;\n" +
            "      readFile(file);\n" +
            "    };\n" +
            "    document.body.appendChild(input);\n" +
            "    input.click();\n" +
            "    setTimeout(function() { if (input && input.parentNode) input.parentNode.removeChild(input); }, 0);\n" +
            "  }\n" +
            "  if (window.showOpenFilePicker) {\n" +
            "    window.showOpenFilePicker({ multiple: false })\n" +
            "      .then(function(handles) { return handles && handles[0] ? handles[0].getFile() : null; })\n" +
            "      .then(function(file) { readFile(file); }, function(error) {\n" +
            "        if (error && error.name === 'AbortError') { finish(null, null); return; }\n" +
            "        openWithInput();\n" +
            "      });\n" +
            "    return;\n" +
            "  }\n" +
            "  openWithInput();\n" +
            "} catch (e) {\n" +
            "  try { handler.onResult(null, null); } catch (_) {}\n" +
            "}")
    private static native void openBinaryDocumentJs(String accept, WebOpenBinaryHandler handler);

    @JSBody(params = {"fileName", "content", "mimeType", "handler"}, script =
            "try {\n" +
            "  if (window.showSaveFilePicker) {\n" +
            "    window.showSaveFilePicker({ suggestedName: fileName || 'octodraw-web.octd' })\n" +
            "      .then(function(handle) { return handle.createWritable().then(function(writable) { return writable.write(new Blob([content], { type: mimeType || 'application/json' })).then(function() { return writable.close(); }); }); })\n" +
            "      .then(function() { handler.onResult(true, null); }, function(error) {\n" +
            "        if (error && error.name === 'AbortError') { handler.onResult(false, 'Save cancelled.'); return; }\n" +
            "        var message = (error && error.message) ? String(error.message) : String(error);\n" +
            "        handler.onResult(false, message);\n" +
            "      });\n" +
            "    return;\n" +
            "  }\n" +
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

    @JSBody(params = {"fileName", "base64Content", "mimeType", "handler"}, script =
            "try {\n" +
            "  function decodeBase64(data) {\n" +
            "    var binary = atob(data || '');\n" +
            "    var len = binary.length;\n" +
            "    var bytes = new Uint8Array(len);\n" +
            "    for (var i = 0; i < len; i++) bytes[i] = binary.charCodeAt(i);\n" +
            "    return bytes;\n" +
            "  }\n" +
            "  var bytes = decodeBase64(base64Content);\n" +
            "  var blob = new Blob([bytes], { type: mimeType || 'application/octet-stream' });\n" +
            "  if (window.showSaveFilePicker) {\n" +
            "    window.showSaveFilePicker({ suggestedName: fileName || 'octodraw-web.bin' })\n" +
            "      .then(function(handle) { return handle.createWritable().then(function(writable) { return writable.write(blob).then(function() { return writable.close(); }); }); })\n" +
            "      .then(function() { handler.onResult(true, null); }, function(error) {\n" +
            "        if (error && error.name === 'AbortError') { handler.onResult(false, 'Save cancelled.'); return; }\n" +
            "        var message = (error && error.message) ? String(error.message) : String(error);\n" +
            "        handler.onResult(false, message);\n" +
            "      });\n" +
            "    return;\n" +
            "  }\n" +
            "  var url = URL.createObjectURL(blob);\n" +
            "  var a = document.createElement('a');\n" +
            "  a.href = url;\n" +
            "  a.download = fileName || 'octodraw-web.bin';\n" +
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
    private static native void saveBinaryDocumentJs(String fileName, String base64Content, String mimeType, WebSaveTextHandler handler);

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

    @JSBody(script =
            "try {\n" +
            "  var state = window.__octodrawEmbedState;\n" +
            "  if (!state) return null;\n" +
            "  return JSON.stringify({\n" +
            "    hostId: state.hostId || null,\n" +
            "    canvasId: state.canvasId || null,\n" +
            "    padHorizontal: state.padHorizontal || 0,\n" +
            "    padVertical: state.padVertical || 0,\n" +
            "    initialModel: state.initialModel || null,\n" +
            "    initialFileName: state.initialFileName || null,\n" +
            "    toolbarsVisible: state.toolbarsVisible !== false,\n" +
            "    panelsVisible: state.panelsVisible !== false\n" +
            "  });\n" +
            "} catch (e) {\n" +
            "  return null;\n" +
            "}")
    private static native String readEmbedConfigJsonJs();

    @JSBody(script =
            "try {\n" +
            "  var state = window.__octodrawEmbedState;\n" +
            "  if (!state || !state.commandQueue || state.commandQueue.length === 0) return null;\n" +
            "  return JSON.stringify(state.commandQueue.shift());\n" +
            "} catch (e) {\n" +
            "  return null;\n" +
            "}")
    private static native String pollEmbedCommandJsonJs();

    @JSBody(params = {"requestId", "success", "payloadJson", "message"}, script =
            "try {\n" +
            "  var state = window.__octodrawEmbedState;\n" +
            "  if (!state || !state.hostId) return;\n" +
            "  var host = document.getElementById(state.hostId);\n" +
            "  if (!host) return;\n" +
            "  var detail = {\n" +
            "    requestId: requestId,\n" +
            "    success: !!success,\n" +
            "    payload: payloadJson ? JSON.parse(payloadJson) : null,\n" +
            "    message: message || null\n" +
            "  };\n" +
            "  if (typeof host.__octodrawHandleCommandResponse === 'function') {\n" +
            "    host.__octodrawHandleCommandResponse(detail);\n" +
            "    return;\n" +
            "  }\n" +
            "  host.dispatchEvent(new CustomEvent('__octodraw-command-response', { detail: detail, bubbles: true, composed: true }));\n" +
            "} catch (e) {\n" +
            "}")
    private static native void resolveEmbedCommandJs(
            String requestId,
            boolean success,
            String payloadJson,
            String message
    );

    @JSBody(params = {"eventType", "detailJson"}, script =
            "try {\n" +
            "  var state = window.__octodrawEmbedState;\n" +
            "  if (!state || !state.hostId) return;\n" +
            "  var host = document.getElementById(state.hostId);\n" +
            "  if (!host) return;\n" +
            "  var detail = detailJson ? JSON.parse(detailJson) : null;\n" +
            "  if (typeof host.__octodrawHandleRuntimeEvent === 'function') {\n" +
            "    host.__octodrawHandleRuntimeEvent(eventType, detail);\n" +
            "    return;\n" +
            "  }\n" +
            "  host.dispatchEvent(new CustomEvent(eventType, { detail: detail, bubbles: true, composed: true }));\n" +
            "} catch (e) {\n" +
            "}")
    private static native void emitEmbedEventJs(String eventType, String detailJson);
}

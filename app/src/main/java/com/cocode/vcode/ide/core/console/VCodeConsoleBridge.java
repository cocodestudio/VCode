package com.cocode.vcode.ide.core.console;

import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/**
 * JavaScript interface bridge facilitating real-time bidirectional console and runtime error forwarding
 * from preview WebViews into VCode's live console manager.
 */
public class VCodeConsoleBridge {

    public static final String JS_INTERFACE_NAME = "VCodeConsoleBridge";

    public static final String INJECTION_SCRIPT =
            "(function() {" +
            "  if (window.__vcode_console_injected__) return;" +
            "  window.__vcode_console_injected__ = true;" +
            "  var send = function(level, args) {" +
            "    try {" +
            "      var msg = Array.prototype.slice.call(args).map(function(a) {" +
            "        try { return typeof a === 'object' ? JSON.stringify(a) : String(a); } catch(e) { return String(a); }" +
            "      }).join(' ');" +
            "      if (window.VCodeConsoleBridge) {" +
            "        window.VCodeConsoleBridge.postMessage(JSON.stringify({ level: level, msg: msg, time: Date.now() }));" +
            "      }" +
            "    } catch(err) {}" +
            "  };" +
            "  var origLog = console.log, origInfo = console.info, origWarn = console.warn, origError = console.error;" +
            "  console.log = function() { if (origLog) origLog.apply(console, arguments); send('LOG', arguments); };" +
            "  console.info = function() { if (origInfo) origInfo.apply(console, arguments); send('INFO', arguments); };" +
            "  console.warn = function() { if (origWarn) origWarn.apply(console, arguments); send('WARN', arguments); };" +
            "  console.error = function() { if (origError) origError.apply(console, arguments); send('ERROR', arguments); };" +
            "  window.onerror = function(msg, url, line, col) { send('ERROR', [msg + (url ? ' at ' + url + ':' + line + ':' + col : '')]); };" +
            "})();";

    @JavascriptInterface
    public void postMessage(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject obj = new JSONObject(json);
            String levelStr = obj.optString("level", "LOG");
            String msg = obj.optString("msg", "");
            long time = obj.optLong("time", System.currentTimeMillis());
            String src = obj.optString("source", "");

            ConsoleLogEntry.Level level;
            if ("ERROR".equalsIgnoreCase(levelStr)) {
                level = ConsoleLogEntry.Level.ERROR;
            } else if ("WARN".equalsIgnoreCase(levelStr)) {
                level = ConsoleLogEntry.Level.WARN;
            } else if ("INFO".equalsIgnoreCase(levelStr)) {
                level = ConsoleLogEntry.Level.INFO;
            } else {
                level = ConsoleLogEntry.Level.LOG;
            }

            LiveConsoleManager.getInstance().addLog(new ConsoleLogEntry(level, msg, time, src));
        } catch (Exception ignored) {
        }
    }
}

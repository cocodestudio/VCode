package com.cocode.vcode.ide.ui.editor.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.views.CodeEditText;

/**
 * In-editor web preview viewer tab component wrapping an embedded WebView.
 */
public class WebPreviewViewer implements IFileViewer {

    private WebView webView;
    private Context context;
    private EditorFile currentFile;

    @Override
    public View getView(Context context, ViewGroup parent) {
        if (webView == null) {
            this.context = context;
            webView = new WebView(context);
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.addJavascriptInterface(
                    new com.cocode.vcode.ide.core.console.VCodeConsoleBridge(),
                    com.cocode.vcode.ide.core.console.VCodeConsoleBridge.JS_INTERFACE_NAME);
            webView.setWebChromeClient(new android.webkit.WebChromeClient() {
                @Override
                public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                    com.cocode.vcode.ide.core.console.ConsoleLogEntry.Level level =
                            com.cocode.vcode.ide.core.console.ConsoleLogEntry.Level.LOG;
                    if (consoleMessage.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                        level = com.cocode.vcode.ide.core.console.ConsoleLogEntry.Level.ERROR;
                    } else if (consoleMessage.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.WARNING) {
                        level = com.cocode.vcode.ide.core.console.ConsoleLogEntry.Level.WARN;
                    }
                    com.cocode.vcode.ide.core.console.LiveConsoleManager.getInstance().addLog(
                            new com.cocode.vcode.ide.core.console.ConsoleLogEntry(
                                    level,
                                    consoleMessage.message(),
                                    System.currentTimeMillis(),
                                    consoleMessage.sourceId() + ":" + consoleMessage.lineNumber()));
                    return super.onConsoleMessage(consoleMessage);
                }
            });
        }
        return webView;
    }

    @Override
    public void bindFile(EditorFile file, EditorViewModel viewModel) {
        this.currentFile = file;
        if (webView == null || file == null) return;

        FileType type = file.getFileType();
        String content = file.getContent();

        if (type == FileType.SVG) {
            String base64 = Base64.encodeToString(content != null ? content.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0], Base64.NO_WRAP);
            String html = "<!DOCTYPE html><html><body style=\"margin:0;display:flex;justify-content:center;align-items:center;height:100vh;background-color:transparent;\">" +
                    "<img src=\"data:image/svg+xml;base64," + base64 + "\" style=\"max-width:100%;max-height:100%;\" />" +
                    "</body></html>";
            webView.getSettings().setJavaScriptEnabled(false);
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        } else if (type == FileType.CSV) {
            renderCsv(content);
        } else if (type == FileType.MARKDOWN) {
            renderMarkdown(content);
        } else if (type == FileType.HTML) {
            renderHtml(file);
        }
    }

    @Override
    public EditorFile getCurrentFile() {
        return currentFile;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void renderHtml(EditorFile file) {
        if (webView == null || file == null) return;
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        String content = file.getContent() != null ? file.getContent() : "";
        String script = "<script>" + com.cocode.vcode.ide.core.console.VCodeConsoleBridge.INJECTION_SCRIPT + "</script>";
        String html;
        if (content.contains("</head>")) {
            html = content.replace("</head>", script + "</head>");
        } else {
            html = script + content;
        }
        String baseUrl = null;
        if (file.getFile() != null && file.getFile().getParentFile() != null) {
            baseUrl = file.getFile().getParentFile().toURI().toString();
        }
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null);
    }

    private void renderCsv(String content) {
        if (content == null) content = "";
        StringBuilder htmlBuilder = new StringBuilder();
        int colorInt = ContextCompat.getColor(context, R.color.vcode_text_primary);
        String hexColor = String.format("#%06X", (0xFFFFFF & colorInt));
        int surfaceInt = ContextCompat.getColor(context, R.color.vcode_bg_surface);
        String surfaceColor = String.format("#%06X", (0xFFFFFF & surfaceInt));

        htmlBuilder.append("<!DOCTYPE html><html><head><style>")
                .append("body { font-family: sans-serif; color: ").append(hexColor).append("; background-color: transparent; padding: 16px; margin: 0; }")
                .append("table { width: 100%; border-collapse: collapse; margin-top: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); }")
                .append("th, td { padding: 12px 15px; border: 1px solid ").append(hexColor).append("33; text-align: left; }")
                .append("th { background-color: ").append(surfaceColor).append("; font-weight: bold; }")
                .append("tr:nth-child(even) { background-color: ").append(surfaceColor).append("66; }")
                .append("</style></head><body><table>");

        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",");
            htmlBuilder.append("<tr>");
            for (String col : cols) {
                if (i == 0) {
                    htmlBuilder.append("<th>").append(android.text.TextUtils.htmlEncode(col.trim())).append("</th>");
                } else {
                    htmlBuilder.append("<td>").append(android.text.TextUtils.htmlEncode(col.trim())).append("</td>");
                }
            }
            htmlBuilder.append("</tr>");
        }
        htmlBuilder.append("</table></body></html>");

        webView.getSettings().setJavaScriptEnabled(false);
        webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "utf-8", null);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void renderMarkdown(String content) {
        if (content == null) content = "";
        int colorInt = ContextCompat.getColor(context, R.color.vcode_text_primary);
        String hexColor = String.format("#%06X", (0xFFFFFF & colorInt));
        int surfaceInt = ContextCompat.getColor(context, R.color.vcode_bg_surface);
        String surfaceColor = String.format("#%06X", (0xFFFFFF & surfaceInt));

        String encodedContent = Base64.encodeToString(content.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP);

        String html = "<!DOCTYPE html><html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<script src=\"scripts/marked.min.js\"></script>" +
                "<style>" +
                "body { font-family: sans-serif; color: " + hexColor + "; background-color: transparent; padding: 16px; line-height: 1.6; word-wrap: break-word; }" +
                "pre { background: " + surfaceColor + "66; padding: 10px; border-radius: 5px; overflow-x: auto; }" +
                "code { background: " + surfaceColor + "66; padding: 2px 4px; border-radius: 3px; font-family: monospace; }" +
                "img { max-width: 100%; height: auto; }" +
                "blockquote { border-left: 4px solid " + hexColor + "55; margin: 0; padding-left: 16px; color: " + hexColor + "CC; }" +
                "table { border-collapse: collapse; width: 100%; margin-bottom: 16px; }" +
                "th, td { border: 1px solid " + hexColor + "33; padding: 8px; text-align: left; }" +
                "th { background-color: " + surfaceColor + "; }" +
                "a { color: #89DCEB; text-decoration: none; }" +
                "</style></head><body>" +
                "<div id=\"content\"></div>" +
                "<script>" +
                "try {" +
                "    var raw = decodeURIComponent(escape(window.atob('" + encodedContent + "')));" +
                "    var parsed = (typeof marked !== 'undefined' && typeof marked.parse === 'function')" +
                "            ? marked.parse(raw)" +
                "            : ((typeof marked === 'function') ? marked(raw) : raw);" +
                "    document.getElementById('content').innerHTML = parsed;" +
                "} catch (err) {" +
                "    document.getElementById('content').innerHTML = '<p style=\"color:red;\">Markdown parse error: ' + err.message + '</p>';" +
                "}" +
                "</script>" +
                "</body></html>";

        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null);
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onPause() {
    }

    @Override
    public void destroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        currentFile = null;
        context = null;
    }

    @Override
    public CodeEditText getCodeEditor() {
        return null;
    }
}

package com.cocode.vcode.ide.ui.editor.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.console.ConsoleLogEntry;
import com.cocode.vcode.ide.core.console.LiveConsoleManager;
import com.cocode.vcode.ide.core.console.VCodeConsoleBridge;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.views.CodeEditText;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * In-editor web preview viewer tab component wrapping an embedded WebView.
 * Delivers desktop-grade, incremental Markdown preview with full GitHub-flavored markdown syntax,
 * local relative image resolution, workspace file link navigation, Prism.js code block highlighting,
 * and heading slug in-page anchor navigation.
 */
public class WebPreviewViewer implements IFileViewer {

    private WebView webView;
    private Context context;
    private EditorFile currentFile;
    private EditorViewModel viewModel;

    private boolean isShellLoaded = false;
    private String lastRenderedFilePath = null;

    private static String cachedMarkedJs = null;
    private static String cachedPrismJs = null;

    private static synchronized void ensureScriptsLoaded(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        if (cachedMarkedJs == null) {
            cachedMarkedJs = loadAssetText(appContext, "scripts/marked.min.js");
        }
        if (cachedPrismJs == null) {
            cachedPrismJs = loadAssetText(appContext, "scripts/prism.min.js");
        }
    }

    private static String loadAssetText(Context context, String assetPath) {
        if (context == null) return "";
        try (InputStream is = context.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public View getView(Context context, ViewGroup parent) {
        if (webView == null) {
            this.context = context;
            webView = new WebView(context);
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            webView.setBackgroundColor(Color.TRANSPARENT);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(false);

            webView.addJavascriptInterface(
                    new VCodeConsoleBridge(),
                    VCodeConsoleBridge.JS_INTERFACE_NAME);

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                    ConsoleLogEntry.Level level = ConsoleLogEntry.Level.LOG;
                    if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        level = ConsoleLogEntry.Level.ERROR;
                    } else if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.WARNING) {
                        level = ConsoleLogEntry.Level.WARN;
                    }
                    LiveConsoleManager.getInstance().addLog(
                            new ConsoleLogEntry(
                                    level,
                                    consoleMessage.message(),
                                    System.currentTimeMillis(),
                                    consoleMessage.sourceId() + ":" + consoleMessage.lineNumber()));
                    return super.onConsoleMessage(consoleMessage);
                }
            });

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return handleLinkClick(request != null ? request.getUrl() : null);
                }

                @SuppressWarnings("deprecation")
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return handleLinkClick(url != null ? Uri.parse(url) : null);
                }
            });
        }
        return webView;
    }

    @Override
    public void bindFile(EditorFile file, EditorViewModel viewModel) {
        this.currentFile = file;
        this.viewModel = viewModel;
        if (webView == null || file == null) return;

        FileType type = file.getFileType();
        String content = file.getContent();

        if (type == FileType.SVG) {
            isShellLoaded = false;
            lastRenderedFilePath = null;
            renderSvg(content);
        } else if (type == FileType.CSV) {
            isShellLoaded = false;
            lastRenderedFilePath = null;
            renderCsv(content);
        } else if (type == FileType.MARKDOWN) {
            renderMarkdown(content);
        } else if (type == FileType.HTML) {
            isShellLoaded = false;
            lastRenderedFilePath = null;
            renderHtml(file);
        }
    }

    @Override
    public EditorFile getCurrentFile() {
        return currentFile;
    }

    private boolean isSameFile(EditorFile ef, String path) {
        if (ef == null || ef.getFile() == null || path == null) return false;
        return FileUtils.isSameFile(ef.getFile(), new File(path));
    }

    private boolean handleLinkClick(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();

        // 1. External links
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                || "mailto".equalsIgnoreCase(scheme) || "ftp".equalsIgnoreCase(scheme)) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (context != null) {
                    context.startActivity(intent);
                }
            } catch (Exception ignored) {
            }
            return true;
        }

        // 2. Heading in-page anchor links (#slug)
        String fragment = uri.getFragment();
        if (fragment != null && !fragment.isEmpty()) {
            String uriPath = uri.getPath();
            boolean isLocalAnchor = false;
            if (uriPath == null || uriPath.isEmpty()) {
                isLocalAnchor = true;
            } else if (currentFile != null && currentFile.getFile() != null) {
                File currentOnDisk = currentFile.getFile();
                File parentDir = currentOnDisk.getParentFile();
                File uriFile = FileUtils.uriToFile(uri.toString());
                if (uriFile == null) {
                    uriFile = new File(uriPath);
                }
                if (FileUtils.isSameFile(uriFile, currentOnDisk)) {
                    isLocalAnchor = true;
                } else if (parentDir != null && (FileUtils.isSameFile(uriFile, parentDir) || uriPath.endsWith("/"))) {
                    isLocalAnchor = true;
                }
            }
            if (isLocalAnchor) {
                String safeFragment = fragment.replace("\\", "\\\\").replace("'", "\\'");
                String js = "(function() {" +
                        "  var el = document.getElementById('" + safeFragment + "');" +
                        "  if (el) {" +
                        "    el.scrollIntoView({ behavior: 'smooth' });" +
                        "  }" +
                        "})();";
                if (webView != null) {
                    webView.evaluateJavascript(js, null);
                }
                return true;
            }
        }

        // 3. Relative or workspace files
        if (currentFile != null && currentFile.getFile() != null && viewModel != null) {
            File parentDir = currentFile.getFile().getParentFile();
            if (parentDir != null) {
                File target = FileUtils.uriToFile(uri.toString());
                if (target == null || !target.exists()) {
                    String rawPath = uri.getPath();
                    if (rawPath != null && !rawPath.isEmpty()) {
                        if (rawPath.startsWith("/")) {
                            target = new File(rawPath);
                            if (!target.exists()) {
                                target = new File(parentDir, rawPath.substring(1));
                            }
                        } else {
                            target = new File(parentDir, rawPath);
                        }
                    }
                }
                if (target != null && target.exists() && !target.isDirectory()) {
                    viewModel.openFile(target);
                    return true;
                }
            }
        }
        return false;
    }

    private void renderSvg(String content) {
        String base64 = Base64.encodeToString(
                content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0],
                Base64.NO_WRAP);
        String html = "<!DOCTYPE html><html><body style=\"margin:0;display:flex;justify-content:center;align-items:center;height:100vh;background-color:transparent;\">" +
                "<img src=\"data:image/svg+xml;base64," + base64 + "\" style=\"max-width:100%;max-height:100%;\" />" +
                "</body></html>";
        webView.getSettings().setJavaScriptEnabled(false);
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void renderHtml(EditorFile file) {
        if (webView == null || file == null) return;
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        String content = file.getContent() != null ? file.getContent() : "";
        String script = "<script>" + VCodeConsoleBridge.INJECTION_SCRIPT + "</script>";
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
        String hexColor = String.format(Locale.ROOT, "#%06X", (0xFFFFFF & colorInt));
        int surfaceInt = ContextCompat.getColor(context, R.color.vcode_bg_surface);
        String surfaceColor = String.format(Locale.ROOT, "#%06X", (0xFFFFFF & surfaceInt));

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
        if (webView == null) return;
        if (content == null) content = "";
        ensureScriptsLoaded(context);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);

        int colorInt = ContextCompat.getColor(context, R.color.vcode_text_primary);
        String hexColor = String.format(Locale.ROOT, "#%06X", (0xFFFFFF & colorInt));
        int surfaceInt = ContextCompat.getColor(context, R.color.vcode_bg_surface);
        String surfaceColor = String.format(Locale.ROOT, "#%06X", (0xFFFFFF & surfaceInt));
        int accentInt = ContextCompat.getColor(context, R.color.vcode_accent_primary);
        String accentColor = String.format(Locale.ROOT, "#%06X", (0xFFFFFF & accentInt));

        String currentPath = currentFile != null && currentFile.getFile() != null ? currentFile.getFile().getAbsolutePath() : null;
        boolean isSameFile = isSameFile(currentFile, lastRenderedFilePath);
        String encoded = Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

        if (isShellLoaded && isSameFile) {
            webView.evaluateJavascript("window.updateMarkdown('" + encoded + "');", null);
        } else {
            String baseUrl = null;
            if (currentFile != null && currentFile.getFile() != null && currentFile.getFile().getParentFile() != null) {
                baseUrl = currentFile.getFile().getParentFile().toURI().toString();
                if (!baseUrl.endsWith("/")) {
                    baseUrl += "/";
                }
            }
            String html = buildFullHtmlShell(encoded, hexColor, surfaceColor, accentColor);
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null);
            isShellLoaded = true;
            lastRenderedFilePath = currentPath;
        }
    }

    private String buildFullHtmlShell(String initialBase64, String textColor, String surfaceColor, String accentColor) {
        return "<!DOCTYPE html><html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<style>" +
                "html { scroll-behavior: smooth; }" +
                "body {" +
                "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;" +
                "  color: " + textColor + ";" +
                "  background-color: transparent;" +
                "  padding: 16px;" +
                "  line-height: 1.6;" +
                "  word-wrap: break-word;" +
                "  margin: 0;" +
                "}" +
                "h1, h2, h3, h4, h5, h6 {" +
                "  font-weight: 600;" +
                "  line-height: 1.25;" +
                "  margin-top: 24px;" +
                "  margin-bottom: 16px;" +
                "  scroll-margin-top: 16px;" +
                "}" +
                "h1 { font-size: 2em; border-bottom: 1px solid rgba(110, 118, 129, 0.3); padding-bottom: 0.3em; }" +
                "h2 { font-size: 1.5em; border-bottom: 1px solid rgba(110, 118, 129, 0.3); padding-bottom: 0.3em; }" +
                "h3 { font-size: 1.25em; }" +
                "h4 { font-size: 1em; }" +
                "h5 { font-size: 0.875em; }" +
                "h6 { font-size: 0.85em; color: rgba(110, 118, 129, 0.8); }" +
                ".heading-anchor-target { position: relative; }" +
                ".heading-anchor {" +
                "  position: absolute;" +
                "  left: -20px;" +
                "  padding-right: 4px;" +
                "  text-decoration: none;" +
                "  color: inherit;" +
                "  opacity: 0;" +
                "  transition: opacity 0.2s;" +
                "  font-weight: normal;" +
                "}" +
                ".heading-anchor-target:hover .heading-anchor, .heading-anchor:focus { opacity: 0.6; }" +
                "a { color: " + accentColor + "; text-decoration: none; }" +
                "a:hover { text-decoration: underline; }" +
                "pre {" +
                "  background-color: " + surfaceColor + ";" +
                "  padding: 14px 16px;" +
                "  border-radius: 8px;" +
                "  border: 1px solid rgba(110, 118, 129, 0.2);" +
                "  overflow-x: auto;" +
                "  font-size: 13px;" +
                "  line-height: 1.45;" +
                "  margin-top: 8px;" +
                "  margin-bottom: 16px;" +
                "}" +
                "code {" +
                "  background-color: rgba(110, 118, 129, 0.2);" +
                "  padding: 2px 6px;" +
                "  border-radius: 4px;" +
                "  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, 'Liberation Mono', monospace;" +
                "  font-size: 85%;" +
                "}" +
                "pre code {" +
                "  background-color: transparent;" +
                "  padding: 0;" +
                "  border-radius: 0;" +
                "  font-size: 100%;" +
                "  white-space: pre;" +
                "  word-break: normal;" +
                "}" +
                ".token.comment, .token.prolog, .token.doctype, .token.cdata { color: #8b949e; font-style: italic; }" +
                ".token.punctuation { color: #c9d1d9; }" +
                ".token.property, .token.tag, .token.boolean, .token.number, .token.constant, .token.symbol { color: #79c0ff; }" +
                ".token.selector, .token.attr-name, .token.string, .token.char, .token.builtin { color: #a5d6ff; }" +
                ".token.operator, .token.entity, .token.url, .token.variable { color: #79c0ff; }" +
                ".token.atrule, .token.attr-value, .token.keyword { color: #ff7b72; font-weight: 500; }" +
                ".token.function, .token.class-name { color: #d2a8ff; }" +
                ".token.regex, .token.important { color: #f2cc60; }" +
                ".markdown-alert {" +
                "  padding: 8px 16px;" +
                "  margin-bottom: 16px;" +
                "  border-left: 4px solid;" +
                "  border-radius: 6px;" +
                "  background-color: rgba(110, 118, 129, 0.08);" +
                "}" +
                ".markdown-alert-title {" +
                "  display: flex;" +
                "  align-items: center;" +
                "  font-weight: 600;" +
                "  margin-top: 4px;" +
                "  margin-bottom: 4px;" +
                "  font-size: 14px;" +
                "}" +
                ".markdown-alert-title svg {" +
                "  margin-right: 8px;" +
                "  fill: currentColor;" +
                "  flex-shrink: 0;" +
                "}" +
                ".markdown-alert-body {" +
                "  font-size: 14px;" +
                "}" +
                ".markdown-alert-body > :first-child { margin-top: 4px; }" +
                ".markdown-alert-body > :last-child { margin-bottom: 4px; }" +
                ".markdown-alert.markdown-alert-note { border-left-color: #2f81f7; }" +
                ".markdown-alert.markdown-alert-note .markdown-alert-title { color: #2f81f7; }" +
                ".markdown-alert.markdown-alert-tip { border-left-color: #3fb950; }" +
                ".markdown-alert.markdown-alert-tip .markdown-alert-title { color: #3fb950; }" +
                ".markdown-alert.markdown-alert-important { border-left-color: #a371f7; }" +
                ".markdown-alert.markdown-alert-important .markdown-alert-title { color: #a371f7; }" +
                ".markdown-alert.markdown-alert-warning { border-left-color: #d29922; }" +
                ".markdown-alert.markdown-alert-warning .markdown-alert-title { color: #d29922; }" +
                ".markdown-alert.markdown-alert-caution { border-left-color: #f85149; }" +
                ".markdown-alert.markdown-alert-caution .markdown-alert-title { color: #f85149; }" +
                ".task-list-item { list-style-type: none; }" +
                ".task-list-item-checkbox {" +
                "  margin: 0 8px 0.2em -1.4em;" +
                "  vertical-align: middle;" +
                "  width: 16px;" +
                "  height: 16px;" +
                "  accent-color: " + accentColor + ";" +
                "  cursor: default;" +
                "}" +
                "ul:has(> .task-list-item) { padding-left: 1.5em; }" +
                "table {" +
                "  border-collapse: collapse;" +
                "  width: 100%;" +
                "  margin-top: 8px;" +
                "  margin-bottom: 16px;" +
                "  display: block;" +
                "  overflow-x: auto;" +
                "}" +
                "th, td {" +
                "  border: 1px solid rgba(110, 118, 129, 0.3);" +
                "  padding: 8px 12px;" +
                "  text-align: left;" +
                "}" +
                "th {" +
                "  background-color: rgba(110, 118, 129, 0.15);" +
                "  font-weight: 600;" +
                "}" +
                "tr:nth-child(2n) {" +
                "  background-color: rgba(110, 118, 129, 0.05);" +
                "}" +
                "img {" +
                "  max-width: 100%;" +
                "  height: auto;" +
                "  border-radius: 6px;" +
                "  margin: 8px 0;" +
                "}" +
                "blockquote {" +
                "  border-left: 4px solid rgba(110, 118, 129, 0.4);" +
                "  margin: 0 0 16px 0;" +
                "  padding: 0 16px;" +
                "  color: rgba(255, 255, 255, 0.7);" +
                "}" +
                "hr {" +
                "  height: 1px;" +
                "  background-color: rgba(110, 118, 129, 0.3);" +
                "  border: none;" +
                "  margin: 24px 0;" +
                "}" +
                "</style></head><body>" +
                "<div id=\"content\"></div>" +
                "<script>" + (cachedMarkedJs != null ? cachedMarkedJs : "") + "</script>" +
                "<script>" + (cachedPrismJs != null ? cachedPrismJs : "") + "</script>" +
                "<script>" +
                "var slugCounts = {};" +
                "function decodeBase64Utf8(base64) {" +
                "  try {" +
                "    if (typeof TextDecoder !== 'undefined') {" +
                "      var binary = window.atob(base64);" +
                "      var len = binary.length;" +
                "      var bytes = new Uint8Array(len);" +
                "      for (var i = 0; i < len; i++) {" +
                "        bytes[i] = binary.charCodeAt(i);" +
                "      }" +
                "      return new TextDecoder('utf-8').decode(bytes);" +
                "    }" +
                "    return decodeURIComponent(escape(window.atob(base64)));" +
                "  } catch (e) {" +
                "    return window.atob(base64);" +
                "  }" +
                "}" +
                "function slugify(text) {" +
                "  var clean = (text || '')" +
                "    .replace(/<[^>]+>/g, '')" +
                "    .replace(/\\[([^\\]]+)\\]\\([^\\)]*\\)/g, '$1')" +
                "    .replace(/[`*~_#]/g, '')" +
                "    .trim()" +
                "    .toLowerCase();" +
                "  var slug = clean.replace(/[^\\p{L}\\p{Nd}\\s_-]/gu, '').replace(/[\\s_]+/g, '-').replace(/^-+|-+$/g, '');" +
                "  if (!slug) slug = 'heading';" +
                "  if (slugCounts[slug] !== undefined) {" +
                "    var count = ++slugCounts[slug];" +
                "    return slug + '-' + count;" +
                "  } else {" +
                "    slugCounts[slug] = 0;" +
                "    return slug;" +
                "  }" +
                "}" +
                "var ALERT_ICONS = {" +
                "  note: '<svg class=\"octicon\" viewBox=\"0 0 16 16\" width=\"16\" height=\"16\"><path fill-rule=\"evenodd\" d=\"M0 8a8 8 0 1116 0A8 8 0 010 8zm8-6.5a6.5 6.5 0 100 13 6.5 6.5 0 000-13zM6.5 7.75A.75.75 0 017.25 7h1a.75.75 0 01.75.75v2.75h.25a.75.75 0 010 1.5h-2.5a.75.75 0 010-1.5h.25v-2h-.25a.75.75 0 01-.75-.75zM8 6a1 1 0 100-2 1 1 0 000 2z\"></path></svg>'," +
                "  tip: '<svg class=\"octicon\" viewBox=\"0 0 16 16\" width=\"16\" height=\"16\"><path fill-rule=\"evenodd\" d=\"M8 1.5c-2.363 0-4 1.69-4 3.75 0 .984.424 1.625.984 2.304l.214.253c.223.264.47.556.673.848.284.411.537.896.621 1.49a.75.75 0 01-1.484.21c-.04-.282-.163-.554-.36-.84-.216-.312-.49-.636-.736-.927l-.214-.253C3.005 7.527 2.5 6.615 2.5 5.25 2.5 2.225 4.803 0 8 0s5.5 2.225 5.5 5.25c0 1.365-.505 2.277-1.177 3.085l-.214.253c-.246.291-.52.615-.736.927-.197.286-.32.558-.36.84a.75.75 0 01-1.484-.21c.084-.594.337-1.079.621-1.49.204-.292.45-.584.673-.848l.214-.253c.56-.679.984-1.32.984-2.304 0-2.06-1.637-3.75-4-3.75zM5.75 12h4.5a.75.75 0 010 1.5h-4.5a.75.75 0 010-1.5zm1 3h2.5a.75.75 0 010 1.5h-2.5a.75.75 0 010-1.5z\"></path></svg>'," +
                "  important: '<svg class=\"octicon\" viewBox=\"0 0 16 16\" width=\"16\" height=\"16\"><path fill-rule=\"evenodd\" d=\"M0 1.75C0 .784.784 0 1.75 0h12.5C15.216 0 16 .784 16 1.75v9.5A1.75 1.75 0 0114.25 13H8.06l-2.573 2.573A1.458 1.458 0 013 14.543V13H1.75A1.75 1.75 0 010 11.25v-9.5zM1.75 1.5a.25.25 0 00-.25.25v9.5c0 .138.112.25.25.25h2a.75.75 0 01.75.75v2.19l2.72-2.72a.749.749 0 01.53-.22h6.5a.25.25 0 00.25-.25v-9.5a.25.25 0 00-.25-.25H1.75zM7.25 4a.75.75 0 011.5 0v3a.75.75 0 01-1.5 0V4zm1.5 6a.75.75 0 11-1.5 0 .75.75 0 011.5 0z\"></path></svg>'," +
                "  warning: '<svg class=\"octicon\" viewBox=\"0 0 16 16\" width=\"16\" height=\"16\"><path fill-rule=\"evenodd\" d=\"M6.457 1.047c.659-1.234 2.427-1.234 3.086 0l6.082 11.378A1.75 1.75 0 0114.082 15H1.918a1.75 1.75 0 01-1.543-2.575L6.457 1.047zM8 3a.75.75 0 00-.75.75v4.5a.75.75 0 001.5 0v-4.5A.75.75 0 008 3zm0 8a1 1 0 100-2 1 1 0 000 2z\"></path></svg>'," +
                "  caution: '<svg class=\"octicon\" viewBox=\"0 0 16 16\" width=\"16\" height=\"16\"><path fill-rule=\"evenodd\" d=\"M4.47.22A.749.749 0 015 0h6c.199 0 .389.079.53.22l4.25 4.25c.141.14.22.331.22.53v6a.749.749 0 01-.22.53l-4.25 4.25A.749.749 0 0111 16H5a.749.749 0 01-.53-.22L.22 11.53A.749.749 0 010 11V5c0-.199.079-.389.22-.53L4.47.22zm.84 1.28L1.5 5.31v5.38l3.81 3.81h5.38l3.81-3.81V5.31L10.69 1.5H5.31zM8 4a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 018 4zm0 8a1 1 0 100-2 1 1 0 000 2z\"></path></svg>'" +
                "};" +
                "var ALERT_TITLES = {" +
                "  note: 'Note', tip: 'Tip', important: 'Important', warning: 'Warning', caution: 'Caution'" +
                "};" +
                "var renderer = {" +
                "  heading: function(token) {" +
                "    var rawText = token.text || '';" +
                "    var slug = slugify(rawText);" +
                "    var inlineContent = this.parser.parseInline(token.tokens);" +
                "    return '<h' + token.depth + ' id=\"' + slug + '\" class=\"heading-anchor-target\">' +" +
                "           '<a class=\"heading-anchor\" href=\"#' + slug + '\" aria-hidden=\"true\">#</a>' +" +
                "           inlineContent +" +
                "           '</h' + token.depth + '>\\n';" +
                "  }," +
                "  blockquote: function(token) {" +
                "    var html = this.parser.parse(token.tokens);" +
                "    var match = html.match(/^\\s*<p>\\s*\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\\](?:\\s*<br\\s*\\/?>|\\n|\\s*)([\\s\\S]*)$/i);" +
                "    if (match) {" +
                "      var type = match[1].toLowerCase();" +
                "      var rest = match[2];" +
                "      if (rest.startsWith('</p>')) {" +
                "        rest = rest.substring(4).trim();" +
                "      }" +
                "      var body = rest ? (rest.startsWith('<p>') ? rest : '<p>' + rest) : '';" +
                "      return '<div class=\"markdown-alert markdown-alert-' + type + '\">\\n' +" +
                "             '  <p class=\"markdown-alert-title\">' + (ALERT_ICONS[type] || '') + '<span>' + (ALERT_TITLES[type] || type) + '</span></p>\\n' +" +
                "             '  <div class=\"markdown-alert-body\">' + body + '</div>\\n' +" +
                "             '</div>\\n';" +
                "    }" +
                "    return '<blockquote>\\n' + html + '</blockquote>\\n';" +
                "  }," +
                "  code: function(token) {" +
                "    var code = token.text || '';" +
                "    var lang = (token.lang || '').trim().toLowerCase();" +
                "    if (window.Prism && lang && window.Prism.languages[lang]) {" +
                "      try {" +
                "        var highlighted = window.Prism.highlight(code, window.Prism.languages[lang], lang);" +
                "        return '<pre class=\"language-' + lang + '\"><code class=\"language-' + lang + '\">' + highlighted + '</code></pre>\\n';" +
                "      } catch (err) {}" +
                "    }" +
                "    var escaped = code.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');" +
                "    var cls = lang ? ' class=\"language-' + lang + '\"' : '';" +
                "    return '<pre' + cls + '><code' + cls + '>' + escaped + '</code></pre>\\n';" +
                "  }," +
                "  checkbox: function(token) {" +
                "    return '<input type=\"checkbox\" disabled class=\"task-list-item-checkbox\"' + (token.checked ? ' checked' : '') + '> ';" +
                "  }," +
                "  listitem: function(token) {" +
                "    var itemHtml = this.parser.parse(token.tokens, !!token.loose);" +
                "    if (token.task) {" +
                "      return '<li class=\"task-list-item\">' + this.checkbox({ checked: !!token.checked }) + itemHtml + '</li>\\n';" +
                "    }" +
                "    return '<li>' + itemHtml + '</li>\\n';" +
                "  }" +
                "};" +
                "marked.use({ renderer: renderer, gfm: true, breaks: true });" +
                "window.updateMarkdown = function(base64) {" +
                "  try {" +
                "    var scroll = window.scrollY;" +
                "    var raw = decodeBase64Utf8(base64);" +
                "    slugCounts = {};" +
                "    var html = marked.parse(raw);" +
                "    var contentEl = document.getElementById('content');" +
                "    contentEl.innerHTML = html;" +
                "    if (window.Prism) {" +
                "      Prism.highlightAllUnder(contentEl);" +
                "    }" +
                "    window.scrollTo(0, scroll);" +
                "  } catch (e) {" +
                "    document.getElementById('content').innerHTML = '<p style=\"color:red;\">Markdown parse error: ' + e.message + '</p>';" +
                "  }" +
                "};" +
                "window.updateMarkdown('" + initialBase64 + "');" +
                "</script>" +
                "</body></html>";
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
        viewModel = null;
        context = null;
        isShellLoaded = false;
        lastRenderedFilePath = null;
    }

    @Override
    public CodeEditText getCodeEditor() {
        return null;
    }
}

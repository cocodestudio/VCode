package com.cocode.vcode.ide.core.preview;

import android.net.Uri;
import android.os.Build;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Preview server controller providing dev server proxying (Vite, Next.js, local Node servers)
 * for WebView live previews using native {@link HttpURLConnection}.
 */
public class PreviewServer {

    private static final PreviewServer INSTANCE = new PreviewServer();
    private static final int TIMEOUT_MS = 5000;

    private final Map<String, String> devServerUrls = new ConcurrentHashMap<>();

    public static PreviewServer getInstance() {
        return INSTANCE;
    }

    private PreviewServer() {
    }

    /**
     * Associates a local or remote dev server URL with a workspace project root.
     */
    public void setDevServerUrl(@NonNull File projectRoot, @Nullable String url) {
        String key = projectRoot.getAbsolutePath();
        if (url != null && !url.trim().isEmpty()) {
            devServerUrls.put(key, url.trim());
        } else {
            devServerUrls.remove(key);
        }
    }

    /**
     * Gets the dev server URL for a project root, or null if none active.
     */
    @Nullable
    public String getDevServerUrl(@Nullable File projectRoot) {
        if (projectRoot == null) return null;
        return devServerUrls.get(projectRoot.getAbsolutePath());
    }

    /**
     * Checks if a dev server is currently registered and active for the given project.
     */
    public boolean isDevServerActive(@Nullable File projectRoot) {
        return getDevServerUrl(projectRoot) != null;
    }

    /**
     * Clears all registered dev server mappings.
     */
    public void clear() {
        devServerUrls.clear();
    }

    /**
     * Proxies a WebView request to the configured dev server using native HttpURLConnection.
     */
    @Nullable
    public WebResourceResponse proxyRequest(@NonNull WebResourceRequest request, @NonNull String devServerBaseUrl) {
        Uri reqUri = request.getUrl();
        if (reqUri == null) return null;

        HttpURLConnection conn = null;
        try {
            String path = reqUri.getPath();
            String query = reqUri.getQuery();
            String targetUrlStr = devServerBaseUrl;
            if (targetUrlStr.endsWith("/")) {
                targetUrlStr = targetUrlStr.substring(0, targetUrlStr.length() - 1);
            }
            if (path != null) {
                targetUrlStr += path;
            }
            if (query != null && !query.isEmpty()) {
                targetUrlStr += "?" + query;
            }

            URL targetUrl = new URL(targetUrlStr);
            conn = (HttpURLConnection) targetUrl.openConnection();
            conn.setRequestMethod(request.getMethod() != null ? request.getMethod() : "GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            // Copy request headers
            Map<String, String> reqHeaders = request.getRequestHeaders();
            if (reqHeaders != null) {
                for (Map.Entry<String, String> entry : reqHeaders.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            int statusCode = conn.getResponseCode();
            String message = conn.getResponseMessage();
            if (message == null || message.isEmpty()) {
                message = statusCode >= 200 && statusCode < 300 ? "OK" : "Error";
            }

            String contentType = conn.getContentType();
            String mimeType = "text/plain";
            String encoding = "UTF-8";
            if (contentType != null) {
                String[] parts = contentType.split(";");
                mimeType = parts[0].trim();
                for (int i = 1; i < parts.length; i++) {
                    String part = parts[i].trim();
                    if (part.toLowerCase().startsWith("charset=")) {
                        encoding = part.substring(8).trim();
                    }
                }
            }

            Map<String, String> responseHeaders = new HashMap<>();
            for (Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
                if (header.getKey() != null && !header.getValue().isEmpty()) {
                    responseHeaders.put(header.getKey(), header.getValue().get(0));
                }
            }

            InputStream stream = statusCode < 400 ? conn.getInputStream() : conn.getErrorStream();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return new WebResourceResponse(mimeType, encoding, statusCode, message, responseHeaders, stream);
            } else {
                return new WebResourceResponse(mimeType, encoding, stream);
            }
        } catch (Exception e) {
            return null;
        }
    }
}

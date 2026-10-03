package com.cocode.vcode.ide.ui.preview;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Build;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.core.console.VCodeConsoleBridge;
import com.cocode.vcode.ide.core.preview.PreviewServer;

import java.io.File;

/**
 * Controller and configurator for IDE WebView live previews.
 * Wires together WebSettings, console injection, PreviewServer dev proxying,
 * and the {@link PreviewRequestHandler} SPI pipeline.
 */
public class WebPreviewViewer {

    private final WebView webView;
    private File projectRoot;

    static {
        // Ensure default NodeModulesRequestHandler is registered
        boolean hasNodeHandler = false;
        for (PreviewRequestHandler handler : PreviewRequestHandler.getHandlers()) {
            if (handler instanceof PreviewRequestHandler.NodeModulesRequestHandler) {
                hasNodeHandler = true;
                break;
            }
        }
        if (!hasNodeHandler) {
            PreviewRequestHandler.registerHandler(new PreviewRequestHandler.NodeModulesRequestHandler());
        }
    }

    public WebPreviewViewer(@NonNull WebView webView, @Nullable File projectRoot) {
        this.webView = webView;
        this.projectRoot = projectRoot;
    }

    public void setProjectRoot(@Nullable File projectRoot) {
        this.projectRoot = projectRoot;
    }

    @Nullable
    public File getProjectRoot() {
        return projectRoot;
    }

    /**
     * Applies standard development WebSettings and wraps the WebViewClient with
     * the request interception pipeline.
     */
    @SuppressLint("SetJavaScriptEnabled")
    public void configure(@Nullable WebViewClient customClient) {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(
                new VCodeConsoleBridge(),
                VCodeConsoleBridge.JS_INTERFACE_NAME);

        webView.setWebViewClient(new InterceptingWebViewClient(customClient));
    }

    /**
     * Internal WebViewClient that intercepts requests through dev server proxy and PreviewRequestHandlers.
     */
    private class InterceptingWebViewClient extends WebViewClient {

        @Nullable
        private final WebViewClient delegate;

        InterceptingWebViewClient(@Nullable WebViewClient delegate) {
            this.delegate = delegate;
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            // 1. Check if dev server proxy is active for this project
            String devServerUrl = PreviewServer.getInstance().getDevServerUrl(projectRoot);
            if (devServerUrl != null && !devServerUrl.isEmpty()) {
                WebResourceResponse proxied = PreviewServer.getInstance().proxyRequest(request, devServerUrl);
                if (proxied != null) {
                    return proxied;
                }
            }

            // 2. Dispatch through PreviewRequestHandler SPI
            WebResourceResponse handled = PreviewRequestHandler.dispatch(request, projectRoot);
            if (handled != null) {
                return handled;
            }

            // 3. Fallback to custom delegate if provided
            if (delegate != null) {
                return delegate.shouldInterceptRequest(view, request);
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            view.evaluateJavascript(VCodeConsoleBridge.INJECTION_SCRIPT, null);
            if (delegate != null) {
                delegate.onPageStarted(view, url, favicon);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.evaluateJavascript(VCodeConsoleBridge.INJECTION_SCRIPT, null);
            if (delegate != null) {
                delegate.onPageFinished(view, url);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (delegate != null) {
                delegate.onReceivedError(view, request, error);
            } else {
                super.onReceivedError(view, request, error);
            }
        }
    }
}

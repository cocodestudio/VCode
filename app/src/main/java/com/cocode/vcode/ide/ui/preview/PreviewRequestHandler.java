package com.cocode.vcode.ide.ui.preview;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.utils.FileUtils;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service Provider Interface (SPI) for intercepting and handling WebView URL requests
 * during live preview. Handles bare ES module import rewriting, local node_modules bundling,
 * and dev server proxying.
 */
public interface PreviewRequestHandler {

    String MODULE_PREFIX = "/__modules__/";

    /**
     * Checks if this handler can process the given WebView request.
     */
    boolean canHandle(@NonNull WebResourceRequest request, @Nullable File projectRoot);

    /**
     * Processes the request and returns a synthesized or proxied {@link WebResourceResponse}.
     */
    @Nullable
    WebResourceResponse handleRequest(@NonNull WebResourceRequest request, @Nullable File projectRoot);

    // --- Static SPI Registry ---

    List<PreviewRequestHandler> HANDLERS = new CopyOnWriteArrayList<>();

    static void registerHandler(@NonNull PreviewRequestHandler handler) {
        if (!HANDLERS.contains(handler)) {
            HANDLERS.add(handler);
        }
    }

    static void unregisterHandler(@NonNull PreviewRequestHandler handler) {
        HANDLERS.remove(handler);
    }

    @NonNull
    static List<PreviewRequestHandler> getHandlers() {
        return HANDLERS;
    }

    /**
     * Dispatches the request through all registered handlers.
     */
    @Nullable
    static WebResourceResponse dispatch(@NonNull WebResourceRequest request, @Nullable File projectRoot) {
        for (PreviewRequestHandler handler : HANDLERS) {
            try {
                if (handler.canHandle(request, projectRoot)) {
                    WebResourceResponse response = handler.handleRequest(request, projectRoot);
                    if (response != null) {
                        return response;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Default built-in handler that resolves bare ES module imports and serves
     * local node_modules packages.
     */
    class NodeModulesRequestHandler implements PreviewRequestHandler {

        private static final Pattern BARE_IMPORT_PATTERN = Pattern.compile(
                "(?m)(import\\s+(?:[\\w*\\s{},$]+from\\s+)?['\"])((?![./]|https?://|/__modules__/)[^'\"]+)(['\"])");

        @Override
        public boolean canHandle(@NonNull WebResourceRequest request, @Nullable File projectRoot) {
            Uri uri = request.getUrl();
            if (uri == null) return false;
            String path = uri.getPath();
            if (path == null) return false;

            // Handle synthetic module requests
            if (path.contains(MODULE_PREFIX) || path.contains("/node_modules/")) {
                return true;
            }

            // Handle local script files that may contain bare imports
            if (projectRoot != null && "file".equalsIgnoreCase(uri.getScheme())) {
                return path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".ts");
            }

            return false;
        }

        @Nullable
        @Override
        public WebResourceResponse handleRequest(@NonNull WebResourceRequest request, @Nullable File projectRoot) {
            Uri uri = request.getUrl();
            if (uri == null) return null;
            String path = uri.getPath();
            if (path == null) return null;

            // 1. Resolve synthetic module requests: /__modules__/<package_name>
            if (path.contains(MODULE_PREFIX)) {
                int idx = path.indexOf(MODULE_PREFIX) + MODULE_PREFIX.length();
                String moduleName = path.substring(idx);
                return resolveModulePackage(moduleName, projectRoot);
            }

            // 2. Direct node_modules file requests
            if (path.contains("/node_modules/") && projectRoot != null) {
                int idx = path.indexOf("/node_modules/") + "/node_modules/".length();
                String subPath = path.substring(idx);
                File file = new File(new File(projectRoot, "node_modules"), subPath);
                if (file.exists() && file.isFile()) {
                    return serveFile(file, "application/javascript");
                }
            }

            // 3. Rewrite bare imports inside local JS files
            if (projectRoot != null && "file".equalsIgnoreCase(uri.getScheme())
                    && (path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".ts"))) {
                File jsFile = new File(path);
                if (jsFile.exists() && jsFile.isFile()) {
                    try {
                        String code = FileUtils.readFile(jsFile);
                        if (code != null) {
                            String rewritten = rewriteBareImports(code);
                            InputStream is = new ByteArrayInputStream(rewritten.getBytes(StandardCharsets.UTF_8));
                            return new WebResourceResponse("application/javascript", "UTF-8", is);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            return null;
        }

        /**
         * Rewrites bare imports:
         * {@code import axios from 'axios';} -> {@code import axios from '/__modules__/axios';}
         */
        public String rewriteBareImports(String code) {
            if (code == null || code.isEmpty()) return "";
            Matcher matcher = BARE_IMPORT_PATTERN.matcher(code);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String prefix = matcher.group(1);
                String moduleName = matcher.group(2);
                String suffix = matcher.group(3);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + MODULE_PREFIX + moduleName + suffix));
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        /**
         * Resolves a package name from local <projectRoot>/node_modules or creates a synthetic fallback.
         */
        @NonNull
        public WebResourceResponse resolveModulePackage(String moduleSpecifier, @Nullable File projectRoot) {
            if (projectRoot != null) {
                File nodeModules = new File(projectRoot, "node_modules");
                File pkgDir = new File(nodeModules, moduleSpecifier);

                if (pkgDir.isDirectory()) {
                    File pkgJson = new File(pkgDir, "package.json");
                    String entryRelPath = "index.js";
                    if (pkgJson.exists()) {
                        try {
                            String jsonStr = FileUtils.readFile(pkgJson);
                            JSONObject json = new JSONObject(jsonStr);
                            if (json.has("module")) {
                                entryRelPath = json.getString("module");
                            } else if (json.has("main")) {
                                entryRelPath = json.getString("main");
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    File entryFile = new File(pkgDir, entryRelPath);
                    if (entryFile.exists() && entryFile.isFile()) {
                        return serveFile(entryFile, "application/javascript");
                    }
                }

                // Check if direct file exists (e.g. moduleSpecifier is "axios/dist/axios.js")
                File directFile = new File(nodeModules, moduleSpecifier);
                if (directFile.exists() && directFile.isFile()) {
                    return serveFile(directFile, "application/javascript");
                }
            }

            // Synthetic ES module fallback stub so the browser does not crash on missing modules
            String syntheticCode = "/* Synthetic module for: " + moduleSpecifier + " */\n"
                    + "const syntheticModule = new Proxy({}, { get: (target, prop) => () => {} });\n"
                    + "export default syntheticModule;\n";
            InputStream is = new ByteArrayInputStream(syntheticCode.getBytes(StandardCharsets.UTF_8));
            return new WebResourceResponse("application/javascript", "UTF-8", is);
        }

        private WebResourceResponse serveFile(File file, String mimeType) {
            try {
                InputStream is = new FileInputStream(file);
                return new WebResourceResponse(mimeType, "UTF-8", is);
            } catch (Exception e) {
                return null;
            }
        }
    }
}

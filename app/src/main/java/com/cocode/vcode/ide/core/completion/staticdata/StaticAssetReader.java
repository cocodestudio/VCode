package com.cocode.vcode.ide.core.completion.staticdata;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Asset reader for static-completion JSON files. Bridges the
 * production path ({@code Context.getAssets().open(...)}) with the
 * test-injection path (raw strings keyed by asset path).
 *
 * <p>Test injection: set a raw string for an asset path via
 * {@link #setAssetOverride(String, String)} before any loader
 * method runs. The override is per-path and is preserved across
 * loader calls within the same JVM.
 */
public final class StaticAssetReader {

    private static final Map<String, String> overrides = new HashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, String> assetCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Context appContext;

    private StaticAssetReader() {
    }

    /**
     * Set the application context used for asset reads in
     * production. Idempotent.
     */
    public static void setAppContext(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    /**
     * Test injection: provide a raw JSON string for an asset
     * path. The string is used in preference to the real asset
     * (which is fine for unit tests that don't want asset IO).
     */
    public static void setAssetOverride(String assetPath, String json) {
        if (assetPath == null) return;
        assetCache.remove(assetPath);
        if (json == null) {
            overrides.remove(assetPath);
        } else {
            overrides.put(assetPath, json);
        }
    }

    /**
     * Drop all overrides, cached asset content, and the cached context (test-only).
     */
    public static synchronized void resetForTest() {
        overrides.clear();
        assetCache.clear();
        appContext = null;
    }

    /**
     * Read the JSON content of an asset. If a test override is set
     * for the path, the override is returned. Otherwise the real
     * Android asset is read via {@code Context.getAssets()}. Once read,
     * the content is cached in process memory so subsequent queries do zero I/O.
     */
    public static String readAsset(String assetPath) {
        if (assetPath == null) return "";
        String ovr = overrides.get(assetPath);
        if (ovr != null) return ovr;

        String cached = assetCache.get(assetPath);
        if (cached != null) return cached;

        Context ctx = appContext;
        if (ctx != null) {
            try (java.io.InputStream is = ctx.getAssets().open(assetPath);
                 java.io.BufferedReader reader = new java.io.BufferedReader(
                         new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(line);
                }
                String result = sb.toString();
                assetCache.put(assetPath, result);
                return result;
            } catch (Exception ignored) {
                // In unit tests, asset may not be packaged into Context, fall through to disk fallback
            }
        }
        // JVM Unit test fallback: read directly from assets directory on disk
        java.io.File file = new java.io.File("app/src/main/assets/" + assetPath);
        if (!file.exists()) {
            file = new java.io.File("src/main/assets/" + assetPath);
        }
        if (file.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(line);
                }
                String result = sb.toString();
                assetCache.put(assetPath, result);
                return result;
            } catch (Exception ignored) {
            }
        }
        return "";
    }
}

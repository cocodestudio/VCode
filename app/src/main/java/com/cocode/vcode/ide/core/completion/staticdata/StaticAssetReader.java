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

    private StaticAssetReader() {}

    private static final Map<String, String> overrides = new HashMap<>();
    private static volatile Context appContext;

    /** Set the application context used for asset reads in
     *  production. Idempotent. */
    public static void setAppContext(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    /** Test injection: provide a raw JSON string for an asset
     *  path. The string is used in preference to the real asset
     *  (which is fine for unit tests that don't want asset IO). */
    public static void setAssetOverride(String assetPath, String json) {
        if (assetPath == null) return;
        if (json == null) {
            overrides.remove(assetPath);
        } else {
            overrides.put(assetPath, json);
        }
    }

    /** Drop all overrides and the cached context (test-only). */
    public static synchronized void resetForTest() {
        overrides.clear();
        appContext = null;
    }

    /**
     * Read the JSON content of an asset. If a test override is set
     * for the path, the override is returned. Otherwise the real
     * Android asset is read via {@code Context.getAssets()}. If
     * the context is unset and no override exists, the empty
     * string is returned (callers will parse to an empty dataset).
     */
    public static String readAsset(String assetPath) {
        if (assetPath == null) return "";
        String ovr = overrides.get(assetPath);
        if (ovr != null) return ovr;
        Context ctx = appContext;
        if (ctx != null) {
            try (java.io.InputStream is = ctx.getAssets().open(assetPath);
                 java.io.BufferedReader reader = new java.io.BufferedReader(
                         new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return sb.toString();
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
                while ((line = reader.readLine()) != null) sb.append(line);
                return sb.toString();
            } catch (Exception ignored) { }
        }
        return "";
    }
}

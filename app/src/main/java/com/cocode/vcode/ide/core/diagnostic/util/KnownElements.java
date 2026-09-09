package com.cocode.vcode.ide.core.diagnostic.util;

import android.content.Context;

import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Static lookup tables and caches for HTML elements, attributes, CSS properties, colors, and JS globals used by linters.
 * All collections are dynamically populated from assets in {@code app/src/main/assets/} to maintain a single source of truth.
 */
public final class KnownElements {

    // HTML / Linter rule lookup tables — populated from diagnostic/linter_rules.json
    public static final Map<String, String> DEPRECATED_ELEMENTS = new HashMap<>();
    public static final Map<String, String> DEPRECATED_ATTRIBUTES = new HashMap<>();
    public static final Set<String> BLOCK_ELEMENTS = new HashSet<>();
    public static final Map<String, Set<String>> REQUIRED_PARENTS = new HashMap<>();
    public static final Map<String, Set<String>> REQUIRED_ATTRIBUTES = new HashMap<>();
    public static final Map<String, String> SEMANTIC_SUGGESTIONS = new LinkedHashMap<>();
    public static final Map<String, String> VENDOR_PREFIX_NEEDED = new LinkedHashMap<>();
    public static final Map<String, Set<String>> CSS_SHORTHAND_LONGHANDS = new HashMap<>();

    // JS / Async API lookup tables — populated from types/js_globals.json
    public static final Set<String> JS_GLOBALS = new HashSet<>();
    public static final Set<String> ASYNC_APIS = new HashSet<>();

    // HTML tag & void element lookup tables — populated from completions/html_tags.json
    public static final Set<String> VOID_ELEMENTS = new HashSet<>();
    public static final Set<String> VALID_HTML_TAGS = new HashSet<>();

    // CSS properties & colors — populated from completions/css_properties.json & css_colors.json
    public static final Set<String> VALID_CSS_PROPERTIES = new HashSet<>();
    public static final Set<String> CSS_NAMED_COLORS = new HashSet<>();

    private static volatile boolean isLoaded = false;
    private static final Object lock = new Object();

    static {
        ensureLoaded();
    }

    public static void init(Context context) {
        if (context != null) {
            StaticAssetReader.setAppContext(context);
        }
        synchronized (lock) {
            isLoaded = false;
            ensureLoaded();
        }
    }

    public static void ensureLoaded() {
        if (isLoaded) return;
        synchronized (lock) {
            if (isLoaded) return;
            loadLinterRules();
            loadJsGlobals();
            loadHtmlTags();
            loadCssProperties();
            loadCssColors();
            isLoaded = true;
        }
    }

    private static void loadLinterRules() {
        String json = StaticAssetReader.readAsset("diagnostic/linter_rules.json");
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("blockElements")) {
                JSONArray arr = root.getJSONArray("blockElements");
                BLOCK_ELEMENTS.clear();
                for (int i = 0; i < arr.length(); i++) {
                    BLOCK_ELEMENTS.add(arr.getString(i));
                }
            }
            if (root.has("deprecatedElements")) {
                JSONObject depElem = root.getJSONObject("deprecatedElements");
                DEPRECATED_ELEMENTS.clear();
                java.util.Iterator<String> it = depElem.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    DEPRECATED_ELEMENTS.put(k, depElem.getString(k));
                }
            }
            if (root.has("deprecatedAttributes")) {
                JSONObject depAttr = root.getJSONObject("deprecatedAttributes");
                DEPRECATED_ATTRIBUTES.clear();
                java.util.Iterator<String> it = depAttr.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    DEPRECATED_ATTRIBUTES.put(k, depAttr.getString(k));
                }
            }
            if (root.has("requiredParents")) {
                JSONObject reqParents = root.getJSONObject("requiredParents");
                REQUIRED_PARENTS.clear();
                java.util.Iterator<String> it = reqParents.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONArray arr = reqParents.getJSONArray(k);
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
                    REQUIRED_PARENTS.put(k, set);
                }
            }
            if (root.has("requiredAttributes")) {
                JSONObject reqAttrs = root.getJSONObject("requiredAttributes");
                REQUIRED_ATTRIBUTES.clear();
                java.util.Iterator<String> it = reqAttrs.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONArray arr = reqAttrs.getJSONArray(k);
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
                    REQUIRED_ATTRIBUTES.put(k, set);
                }
            }
            if (root.has("semanticSuggestions")) {
                JSONObject semSugg = root.getJSONObject("semanticSuggestions");
                SEMANTIC_SUGGESTIONS.clear();
                java.util.Iterator<String> it = semSugg.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    SEMANTIC_SUGGESTIONS.put(k, semSugg.getString(k));
                }
            }
            if (root.has("vendorPrefixes")) {
                JSONObject vp = root.getJSONObject("vendorPrefixes");
                VENDOR_PREFIX_NEEDED.clear();
                java.util.Iterator<String> it = vp.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    VENDOR_PREFIX_NEEDED.put(k, vp.getString(k));
                }
            }
            if (root.has("cssShorthands")) {
                JSONObject shorthands = root.getJSONObject("cssShorthands");
                CSS_SHORTHAND_LONGHANDS.clear();
                java.util.Iterator<String> it = shorthands.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONArray arr = shorthands.getJSONArray(k);
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
                    CSS_SHORTHAND_LONGHANDS.put(k, set);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadJsGlobals() {
        String json = StaticAssetReader.readAsset("types/js_globals.json");
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("globals")) {
                JSONArray arr = root.getJSONArray("globals");
                JS_GLOBALS.clear();
                for (int i = 0; i < arr.length(); i++) JS_GLOBALS.add(arr.getString(i));
            }
            if (root.has("asyncApis")) {
                JSONArray arr = root.getJSONArray("asyncApis");
                ASYNC_APIS.clear();
                for (int i = 0; i < arr.length(); i++) ASYNC_APIS.add(arr.getString(i));
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadHtmlTags() {
        try {
            String json = StaticAssetReader.readAsset("completions/html_tags.json");
            if (json == null || json.isEmpty()) return;
            JSONArray arr;
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                arr = new JSONArray(trimmed);
            } else {
                JSONObject root = new JSONObject(trimmed);
                arr = root.getJSONArray("tags");
            }
            VALID_HTML_TAGS.clear();
            VOID_ELEMENTS.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String tag = obj.optString("tag").toLowerCase();
                if (tag.isEmpty()) continue;
                VALID_HTML_TAGS.add(tag);
                if (obj.optBoolean("selfClosing", false)) VOID_ELEMENTS.add(tag);
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadCssProperties() {
        try {
            String json = StaticAssetReader.readAsset("completions/css_properties.json");
            if (json == null || json.isEmpty()) return;
            JSONArray arr = new JSONArray(json);
            VALID_CSS_PROPERTIES.clear();
            for (int i = 0; i < arr.length(); i++) {
                String prop = arr.getJSONObject(i).optString("property").toLowerCase();
                if (!prop.isEmpty()) VALID_CSS_PROPERTIES.add(prop);
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadCssColors() {
        try {
            String json = StaticAssetReader.readAsset("completions/css_colors.json");
            if (json == null || json.isEmpty()) return;
            JSONObject obj = new JSONObject(json);
            JSONArray colors = obj.optJSONArray("colors");
            if (colors == null) return;
            CSS_NAMED_COLORS.clear();
            for (int i = 0; i < colors.length(); i++) {
                String c = colors.optString(i).toLowerCase();
                if (!c.isEmpty()) CSS_NAMED_COLORS.add(c);
            }
        } catch (Exception ignored) {
        }
    }
}

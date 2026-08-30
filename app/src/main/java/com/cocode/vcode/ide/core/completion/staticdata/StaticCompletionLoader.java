package com.cocode.vcode.ide.core.completion.staticdata;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time loader for the four static-completion JSON datasets.
 *
 * <p>Each dataset is parsed exactly once on first use and cached
 * in process memory. Subsequent calls return the same in-memory
 * arrays/maps. No JSON re-parsing or asset reads happen per
 * completion request.
 *
 * <p>The loader exposes:
 * <ul>
 *   <li>{@link #getJsKeywords()} — flat list of
 *       {@link StaticCompletionItem} for JS keywords / builtins /
 *       snippets.</li>
 *   <li>{@link #getHtmlTags()} — flat list of HTML tag entries with
 *       their per-tag {@code attributes} array.</li>
 *   <li>{@link #getCssProperties()} — flat list of CSS property
 *       entries with their {@code values} array and
 *       {@code acceptsColor} flag.</li>
 *   <li>{@link #getCssPropertyMap()} — case-insensitive lookup
 *       table from property name to its {@link StaticCompletionItem}
 *       for O(1) value-position resolution.</li>
 *   <li>{@link #getCssColors()} — flat list of CSS color names.</li>
 *   <li>{@link #getCssColorFunctions()} — list of CSS color
 *       function snippets.</li>
 *   <li>{@link #getCssGlobalFunctions()} — list of CSS global
 *       function snippets (always offered at value position).</li>
 *   <li>{@link #getJsonSnippets()} — flat list of JSON snippet
 *       entries.</li>
 * </ul>
 *
 * <p>Thread safety: the public getters use {@code volatile} cached
 * state. Once cached, all getters return the same array reference
 * for their lifetime; the arrays are not mutated after caching.
 */
public final class StaticCompletionLoader {

    private StaticCompletionLoader() {}

    // Asset paths (relative to the assets root).
    public static final String JS_KEYWORDS_ASSET     = "completions/js_keywords.json";
    public static final String HTML_TAGS_ASSET        = "completions/html_tags.json";
    public static final String CSS_PROPERTIES_ASSET   = "completions/css_properties.json";
    public static final String CSS_COLORS_ASSET       = "completions/css_colors.json";
    public static final String JSON_SNIPPETS_ASSET    = "completions/json_snippets.json";

    // --- Cached state ---------------------------------------------------

    private static volatile StaticCompletionItem[] jsKeywordsCache;
    private static volatile StaticCompletionItem[] htmlTagsCache;
    private static volatile String[] htmlGlobalAttributesCache;
    private static volatile String[] htmlGlobalAttributePrefixesCache;
    private static volatile StaticCompletionItem[] cssPropertiesCache;
    private static volatile Map<String, StaticCompletionItem> cssPropertyMapCache;
    private static volatile String[] cssColorsCache;
    private static volatile String[] cssColorFunctionsCache;
    private static volatile String[] cssGlobalFunctionsCache;
    private static volatile StaticCompletionItem[] jsonSnippetsCache;

    private static final Object lock = new Object();

    // --- Public API ----------------------------------------------------

    public static StaticCompletionItem[] getJsKeywords() {
        if (jsKeywordsCache == null) loadJsKeywordsFromAssets();
        return jsKeywordsCache;
    }

    public static StaticCompletionItem[] getHtmlTags() {
        if (htmlTagsCache == null) loadHtmlTagsFromAssets();
        return htmlTagsCache;
    }

    /** Attributes valid on every element (e.g. {@code id},
     *  {@code class}, {@code style}, {@code title}, {@code role}). */
    public static String[] getHtmlGlobalAttributes() {
        if (htmlGlobalAttributesCache == null) loadHtmlTagsFromAssets();
        return htmlGlobalAttributesCache;
    }

    /** Prefix-matched global attributes ({@code data-},
     *  {@code aria-}). The completion engine should offer
     *  {@code <typed-prefix>-...} variants. */
    public static String[] getHtmlGlobalAttributePrefixes() {
        if (htmlGlobalAttributePrefixesCache == null) loadHtmlTagsFromAssets();
        return htmlGlobalAttributePrefixesCache;
    }

    public static StaticCompletionItem[] getCssProperties() {
        if (cssPropertiesCache == null) loadCssPropertiesFromAssets();
        return cssPropertiesCache;
    }

    /** Case-insensitive lookup of a CSS property by name. */
    public static StaticCompletionItem getCssProperty(String name) {
        if (name == null) return null;
        if (cssPropertyMapCache == null) loadCssPropertiesFromAssets();
        return cssPropertyMapCache.get(name.toLowerCase());
    }

    public static String[] getCssColors() {
        if (cssColorsCache == null) loadCssColorsFromAssets();
        return cssColorsCache;
    }

    public static String[] getCssColorFunctions() {
        if (cssColorFunctionsCache == null) loadCssColorsFromAssets();
        return cssColorFunctionsCache;
    }

    public static String[] getCssGlobalFunctions() {
        if (cssGlobalFunctionsCache == null) loadCssColorsFromAssets();
        return cssGlobalFunctionsCache;
    }

    public static StaticCompletionItem[] getJsonSnippets() {
        if (jsonSnippetsCache == null) loadJsonSnippetsFromAssets();
        return jsonSnippetsCache;
    }

    // --- Reset (test-only) ---------------------------------------------

    /** Drop all cached state. Intended for tests that want to
     *  inject custom JSON. */
    public static synchronized void resetForTest() {
        jsKeywordsCache = null;
        htmlTagsCache = null;
        htmlGlobalAttributesCache = null;
        htmlGlobalAttributePrefixesCache = null;
        cssPropertiesCache = null;
        cssPropertyMapCache = null;
        cssColorsCache = null;
        cssColorFunctionsCache = null;
        cssGlobalFunctionsCache = null;
        jsonSnippetsCache = null;
        JsStaticCompletionDispatcher.clearCachesForTest();
        HtmlStaticCompletionDispatcher.clearCachesForTest();
        CssStaticCompletionDispatcher.clearCachesForTest();
        JsonStaticCompletionDispatcher.clearCachesForTest();
    }

    // --- Asset-backed loaders (read from android.content.Context) -----

    private static void loadJsKeywordsFromAssets() {
        synchronized (lock) {
            if (jsKeywordsCache != null) return;
            String json = StaticAssetReader.readAsset(JS_KEYWORDS_ASSET);
            jsKeywordsCache = parseFlatItemArray(json, /*acceptsColor*/false);
        }
    }

    private static void loadHtmlTagsFromAssets() {
        synchronized (lock) {
            if (htmlTagsCache != null) return;
            String json = StaticAssetReader.readAsset(HTML_TAGS_ASSET);
            List<String> globalAttrs = new ArrayList<>();
            List<String> globalPrefixes = new ArrayList<>();
            htmlTagsCache = parseHtmlTags(json, globalAttrs, globalPrefixes);
            htmlGlobalAttributesCache = globalAttrs.toArray(new String[0]);
            htmlGlobalAttributePrefixesCache = globalPrefixes.toArray(new String[0]);
        }
    }

    private static void loadCssPropertiesFromAssets() {
        synchronized (lock) {
            if (cssPropertiesCache != null) return;
            String json = StaticAssetReader.readAsset(CSS_PROPERTIES_ASSET);
            cssPropertiesCache = parseCssProperties(json);
            cssPropertyMapCache = indexCssProperties(cssPropertiesCache);
        }
    }

    private static void loadCssColorsFromAssets() {
        synchronized (lock) {
            if (cssColorsCache != null) return;
            String json = StaticAssetReader.readAsset(CSS_COLORS_ASSET);
            CssColorSets s = parseCssColors(json);
            cssColorsCache = s.colors;
            cssColorFunctionsCache = s.colorFunctions;
            cssGlobalFunctionsCache = s.globalFunctions;
        }
    }

    private static void loadJsonSnippetsFromAssets() {
        synchronized (lock) {
            if (jsonSnippetsCache != null) return;
            String json = StaticAssetReader.readAsset(JSON_SNIPPETS_ASSET);
            jsonSnippetsCache = parseFlatItemArray(json, /*acceptsColor*/false);
        }
    }

    // --- Direct-from-JSON loaders (test entry points) ------------------

    /** Public for test injection: parse a JS keywords JSON string
     *  into {@link StaticCompletionItem} array. */
    public static StaticCompletionItem[] parseJsKeywords(String json) {
        return parseFlatItemArray(json, false);
    }

    /** Public for test injection: parse an HTML tags JSON string.
     *  The string may be either a top-level array (legacy) or a
     *  top-level object with {@code tags}, {@code globalAttributes},
     *  {@code globalAttributePrefixes} keys. */
    public static StaticCompletionItem[] parseHtmlTags(String json) {
        return parseHtmlTags(json, new java.util.ArrayList<>(), new java.util.ArrayList<>());
    }

    /** Overload that also populates {@code outGlobalAttrs} and
     *  {@code outGlobalPrefixes}. */
    public static StaticCompletionItem[] parseHtmlTags(String json,
                                                       List<String> outGlobalAttrs,
                                                       List<String> outGlobalPrefixes) {
        List<StaticCompletionItem> out = new ArrayList<>();
        try {
            String trimmed = json.trim();
            JSONArray arr;
            if (trimmed.startsWith("[")) {
                arr = new JSONArray(trimmed);
            } else {
                JSONObject root = new JSONObject(trimmed);
                arr = root.getJSONArray("tags");
                JSONArray g = root.optJSONArray("globalAttributes");
                if (g != null) {
                    for (int i = 0; i < g.length(); i++) {
                        outGlobalAttrs.add(g.optString(i, ""));
                    }
                }
                JSONArray p = root.optJSONArray("globalAttributePrefixes");
                if (p != null) {
                    for (int i = 0; i < p.length(); i++) {
                        outGlobalPrefixes.add(p.optString(i, ""));
                    }
                }
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String tag = o.getString("tag");
                String snippet = o.optString("snippet", "<" + tag + ">|</" + tag + ">");
                String detail = o.optString("detail", "");
                boolean selfClosing = o.optBoolean("selfClosing", false);
                String[] attrs = readStringArray(o, "attributes");
                out.add(new StaticCompletionItem(
                        tag, snippet, detail, "TAG",
                        attrs, null, false, selfClosing));
            }
        } catch (JSONException e) {
            // Malformed JSON → return empty.
        }
        return out.toArray(new StaticCompletionItem[0]);
    }

    /** Public for test injection: parse a CSS properties JSON string. */
    public static StaticCompletionItem[] parseCssProperties(String json) {
        List<StaticCompletionItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String property = o.getString("property");
                String detail = o.optString("detail", "");
                String[] values = readStringArray(o, "values");
                boolean acceptsColor = o.optBoolean("acceptsColor", false);
                out.add(new StaticCompletionItem(
                        property, property, detail, "PROPERTY",
                        null, values, acceptsColor, false));
            }
        } catch (JSONException e) {
            // Malformed JSON → return empty.
        }
        return out.toArray(new StaticCompletionItem[0]);
    }

    /** Public for test injection: parse a CSS colors JSON object. */
    public static CssColorSets parseCssColors(String json) {
        CssColorSets s = new CssColorSets();
        try {
            JSONObject o = new JSONObject(json);
            s.colors          = readStringArray(o, "colors");
            s.colorFunctions  = readStringArray(o, "color_functions");
            s.globalFunctions = readStringArray(o, "global_functions");
        } catch (JSONException ignored) {
            // Leave defaults.
        }
        return s;
    }

    /** Holder returned by {@link #parseCssColors(String)}. */
    public static final class CssColorSets {
        public String[] colors          = new String[0];
        public String[] colorFunctions  = new String[0];
        public String[] globalFunctions = new String[0];
    }

    // --- Internal parsers ------------------------------------------------

    private static StaticCompletionItem[] parseFlatItemArray(String json, boolean acceptsColorDefault) {
        List<StaticCompletionItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String label = o.getString("label");
                String detail = o.optString("detail", "");
                String type = o.optString("type", "KEYWORD");
                // Two accepted field names: js_keywords uses
                // "insertText", json_snippets uses "snippet". Both
                // contain a single '|' cursor marker.
                String insertText = o.optString("insertText", null);
                if (insertText == null) {
                    insertText = o.optString("snippet", label);
                }
                out.add(new StaticCompletionItem(
                        label, insertText, detail, type,
                        null, null, acceptsColorDefault, false));
            }
        } catch (JSONException e) {
            // Malformed JSON → return empty.
        }
        return out.toArray(new StaticCompletionItem[0]);
    }

    private static String[] readStringArray(JSONObject o, String key) {
        JSONArray arr = o.optJSONArray(key);
        if (arr == null) return new String[0];
        List<String> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            out.add(arr.optString(i, ""));
        }
        return out.toArray(new String[0]);
    }

    private static Map<String, StaticCompletionItem> indexCssProperties(StaticCompletionItem[] items) {
        // Case-insensitive: properties in the wild are case-
        // insensitive per CSS spec. Using a plain HashMap with
        // toLowerCase() keys.
        Map<String, StaticCompletionItem> map = new LinkedHashMap<>(items.length * 2);
        Set<String> seen = new HashSet<>();
        for (StaticCompletionItem item : items) {
            String key = item.label.toLowerCase();
            if (seen.add(key)) {
                map.put(key, item);
            }
        }
        return map;
    }
}

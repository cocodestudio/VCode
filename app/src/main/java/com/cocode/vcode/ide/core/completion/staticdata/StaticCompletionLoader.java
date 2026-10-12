package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.model.CompletionItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-time loader and consolidated in-memory registry for all static completion datasets.
 *
 * Each dataset is parsed once on first use (or warmed up on background startup) and cached
 * in process memory. Subsequent calls return the same in-memory collections and TwoTierIndex
 * structures, eliminating constructor latency and runtime GC pauses.
 */
public final class StaticCompletionLoader {

    // Asset paths (relative to the assets root).
    public static final String JS_KEYWORDS_ASSET = "completions/js_keywords.json";
    public static final String HTML_TAGS_ASSET = "completions/html_tags.json";
    public static final String CSS_PROPERTIES_ASSET = "completions/css_properties.json";
    public static final String CSS_COLORS_ASSET = "completions/css_colors.json";
    public static final String JSON_SNIPPETS_ASSET = "completions/json_snippets.json";
    public static final String JSON_SCHEMAS_ASSET = "completions/json_schemas.json";
    public static final String HTML_BLANK_TEMPLATE_ASSET = "templates/template_blank.html";
    private static final Object lock = new Object();

    // --- Cached raw static state ----------------------------------------
    private static volatile StaticCompletionItem[] jsKeywordsCache;
    private static volatile StaticCompletionItem[] htmlTagsCache;
    private static volatile String[] htmlGlobalAttributesCache;
    private static volatile String[] htmlGlobalAttributePrefixesCache;
    private static volatile StaticCompletionItem[] cssPropertiesCache;
    private static volatile Map<String, StaticCompletionItem> cssPropertyMapCache;
    private static volatile String[] cssColorsCache;
    private static volatile Set<String> cssColorPropertiesCache;
    private static volatile String[] cssColorFunctionsCache;
    private static volatile String[] cssGlobalFunctionsCache;
    private static volatile StaticCompletionItem[] jsonSnippetsCache;

    // --- Consolidated CompletionItem registries & TwoTierIndex caches ---
    private static volatile List<CompletionItem> htmlTagCompletionItemsCache;
    private static volatile TwoTierIndex htmlTagIndexCache;
    private static volatile Map<String, List<CompletionItem>> htmlTagAttributeMapCache;
    private static volatile String htmlBoilerplateCache;

    private static volatile List<CompletionItem> cssPropertyCompletionItemsCache;
    private static volatile TwoTierIndex cssPropertyIndexCache;
    private static volatile Map<String, List<String>> cssPropertyValueMapCache;
    private static volatile List<CompletionItem> cssHtmlTagCompletionItemsCache;
    private static volatile TwoTierIndex cssHtmlTagIndexCache;
    private static volatile List<CompletionItem> cssColorCompletionItemsCache;
    private static volatile TwoTierIndex cssColorIndexCache;
    private static volatile List<CompletionItem> cssGlobalFunctionItemsCache;

    private static volatile List<CompletionItem> jsKeywordCompletionItemsCache;
    private static volatile TwoTierIndex jsKeywordIndexCache;

    private static volatile List<CompletionItem> jsonSnippetCompletionItemsCache;
    private static volatile TwoTierIndex jsonSnippetIndexCache;
    private static volatile Map<String, List<CompletionItem>> jsonSchemaKeysMapCache;

    private StaticCompletionLoader() {
    }

    // --- Public API ----------------------------------------------------

    public static StaticCompletionItem[] getJsKeywords() {
        if (jsKeywordsCache == null) loadJsKeywordsFromAssets();
        return jsKeywordsCache;
    }

    public static StaticCompletionItem[] getHtmlTags() {
        if (htmlTagsCache == null) loadHtmlTagsFromAssets();
        return htmlTagsCache;
    }

    /**
     * Attributes valid on every element (e.g. {@code id},
     * {@code class}, {@code style}, {@code title}, {@code role}).
     */
    public static String[] getHtmlGlobalAttributes() {
        if (htmlGlobalAttributesCache == null) loadHtmlTagsFromAssets();
        return htmlGlobalAttributesCache;
    }

    /**
     * Prefix-matched global attributes ({@code data-},
     * {@code aria-}). The completion engine should offer
     * {@code <typed-prefix>-...} variants.
     */
    public static String[] getHtmlGlobalAttributePrefixes() {
        if (htmlGlobalAttributePrefixesCache == null) loadHtmlTagsFromAssets();
        return htmlGlobalAttributePrefixesCache;
    }

    public static StaticCompletionItem[] getCssProperties() {
        if (cssPropertiesCache == null) loadCssPropertiesFromAssets();
        return cssPropertiesCache;
    }

    /**
     * Case-insensitive lookup of a CSS property by name.
     */
    public static StaticCompletionItem getCssProperty(String name) {
        if (name == null) return null;
        if (cssPropertyMapCache == null) loadCssPropertiesFromAssets();
        return cssPropertyMapCache.get(name.toLowerCase());
    }

    public static String[] getCssColors() {
        if (cssColorsCache == null) loadCssColorsFromAssets();
        return cssColorsCache;
    }

    public static Set<String> getCssColorProperties() {
        if (cssColorPropertiesCache == null) {
            Set<String> colorProps = new HashSet<>();
            StaticCompletionItem[] props = getCssProperties();
            if (props != null) {
                for (StaticCompletionItem item : props) {
                    if (item != null && item.acceptsColor && item.label != null) {
                        colorProps.add(item.label.toLowerCase());
                    }
                }
            }
            cssColorPropertiesCache = Collections.unmodifiableSet(colorProps);
        }
        return cssColorPropertiesCache;
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

    // --- Consolidated CompletionItem Registries & TwoTierIndexes -------

    public static List<CompletionItem> getHtmlTagCompletionItems() {
        if (htmlTagCompletionItemsCache == null) {
            synchronized (lock) {
                if (htmlTagCompletionItemsCache == null) {
                    StaticCompletionItem[] tags = getHtmlTags();
                    List<CompletionItem> list = new ArrayList<>(tags.length);
                    for (StaticCompletionItem t : tags) {
                        SnippetCursorParser.Result r = SnippetCursorParser.parse(t.insertText);
                        String insert = r != null ? r.insertText : t.insertText;
                        int offset = r != null ? r.cursorOffset : 0;
                        list.add(new CompletionItem(t.label, insert,
                                "HTML " + (t.detail == null ? "tag" : t.detail),
                                CompletionItem.Type.TAG, offset));
                    }
                    htmlTagCompletionItemsCache = Collections.unmodifiableList(list);
                }
            }
        }
        return htmlTagCompletionItemsCache;
    }

    public static TwoTierIndex getHtmlTagIndex() {
        if (htmlTagIndexCache == null) {
            synchronized (lock) {
                if (htmlTagIndexCache == null) {
                    htmlTagIndexCache = new TwoTierIndex(getHtmlTagCompletionItems());
                }
            }
        }
        return htmlTagIndexCache;
    }

    public static Map<String, List<CompletionItem>> getHtmlTagAttributeMap() {
        if (htmlTagAttributeMapCache == null) {
            synchronized (lock) {
                if (htmlTagAttributeMapCache == null) {
                    StaticCompletionItem[] tags = getHtmlTags();
                    Map<String, List<CompletionItem>> map = new LinkedHashMap<>();
                    com.cocode.vcode.ide.core.language.html.HtmlDefinitions.ensureLoaded();
                    List<CompletionItem> globalAttrs = com.cocode.vcode.ide.core.language.html.HtmlDefinitions.getGlobalAttrs();

                    for (StaticCompletionItem t : tags) {
                        List<CompletionItem> attrList = new ArrayList<>(globalAttrs);
                        if (t.attributes != null) {
                            for (int j = 0; j < t.attributes.length; j++) {
                                String attr = t.attributes[j];
                                String insert = HtmlStaticCompletionDispatcher.isHtmlBooleanAttribute(attr)
                                        ? attr : attr + "=\"|\"";
                                attrList.add(0, new CompletionItem(attr, insert,
                                        (t.detail == null || t.detail.isEmpty()) ? t.label : t.detail,
                                        CompletionItem.Type.ATTRIBUTE, 0));
                            }
                        }
                        map.put(t.label.toLowerCase(), Collections.unmodifiableList(attrList));
                    }
                    htmlTagAttributeMapCache = Collections.unmodifiableMap(map);
                }
            }
        }
        return htmlTagAttributeMapCache;
    }

    public static String getHtmlBoilerplate() {
        if (htmlBoilerplateCache == null) {
            synchronized (lock) {
                if (htmlBoilerplateCache == null) {
                    String template = StaticAssetReader.readAsset(HTML_BLANK_TEMPLATE_ASSET);
                    if (template != null && !template.trim().isEmpty()) {
                        String boilerplate = template.replace("  <body>\n\n  </body>", "  <body>\n    |\n  </body>")
                                .replace("  <body>\r\n\r\n  </body>", "  <body>\r\n    |\r\n  </body>")
                                .replace("<body>\n\n", "<body>\n    |\n")
                                .replace("<body>\r\n\r\n", "<body>\r\n    |\r\n");
                        if (!boilerplate.contains("|")) {
                            boilerplate = boilerplate.replace("<body>", "<body>\n    |");
                        }
                        htmlBoilerplateCache = boilerplate;
                    } else {
                        htmlBoilerplateCache = "<!DOCTYPE html>\n<html>\n<head>\n    <title>|</title>\n</head>\n<body>\n</body>\n</html>";
                    }
                }
            }
        }
        return htmlBoilerplateCache;
    }

    public static List<CompletionItem> getCssPropertyCompletionItems() {
        if (cssPropertyCompletionItemsCache == null) {
            synchronized (lock) {
                if (cssPropertyCompletionItemsCache == null) {
                    StaticCompletionItem[] props = getCssProperties();
                    List<CompletionItem> list = new ArrayList<>(props.length);
                    for (StaticCompletionItem p : props) {
                        list.add(new CompletionItem(p.label, p.label + ": |;",
                                p.detail != null ? p.detail : "", CompletionItem.Type.CSS_PROPERTY, 0));
                    }
                    cssPropertyCompletionItemsCache = Collections.unmodifiableList(list);
                }
            }
        }
        return cssPropertyCompletionItemsCache;
    }

    public static TwoTierIndex getCssPropertyIndex() {
        if (cssPropertyIndexCache == null) {
            synchronized (lock) {
                if (cssPropertyIndexCache == null) {
                    cssPropertyIndexCache = new TwoTierIndex(getCssPropertyCompletionItems());
                }
            }
        }
        return cssPropertyIndexCache;
    }

    public static Map<String, List<String>> getCssPropertyValueMap() {
        if (cssPropertyValueMapCache == null) {
            synchronized (lock) {
                if (cssPropertyValueMapCache == null) {
                    StaticCompletionItem[] props = getCssProperties();
                    Map<String, List<String>> map = new LinkedHashMap<>(props.length * 2);
                    for (StaticCompletionItem p : props) {
                        if (p.values != null) {
                            List<String> vals = new ArrayList<>(p.values.length);
                            Collections.addAll(vals, p.values);
                            map.put(p.label.toLowerCase(), Collections.unmodifiableList(vals));
                        }
                    }
                    cssPropertyValueMapCache = Collections.unmodifiableMap(map);
                }
            }
        }
        return cssPropertyValueMapCache;
    }

    public static List<CompletionItem> getCssHtmlTagCompletionItems() {
        if (cssHtmlTagCompletionItemsCache == null) {
            synchronized (lock) {
                if (cssHtmlTagCompletionItemsCache == null) {
                    StaticCompletionItem[] tags = getHtmlTags();
                    List<CompletionItem> list = new ArrayList<>(tags.length);
                    for (StaticCompletionItem t : tags) {
                        list.add(new CompletionItem(t.label, t.label + " ",
                                (t.detail == null || t.detail.isEmpty()) ? "HTML Element" : t.detail,
                                CompletionItem.Type.TAG, 0));
                    }
                    cssHtmlTagCompletionItemsCache = Collections.unmodifiableList(list);
                }
            }
        }
        return cssHtmlTagCompletionItemsCache;
    }

    public static TwoTierIndex getCssHtmlTagIndex() {
        if (cssHtmlTagIndexCache == null) {
            synchronized (lock) {
                if (cssHtmlTagIndexCache == null) {
                    cssHtmlTagIndexCache = new TwoTierIndex(getCssHtmlTagCompletionItems());
                }
            }
        }
        return cssHtmlTagIndexCache;
    }

    public static List<CompletionItem> getCssColorCompletionItems() {
        if (cssColorCompletionItemsCache == null) {
            synchronized (lock) {
                if (cssColorCompletionItemsCache == null) {
                    String[] colors = getCssColors();
                    String[] colorFuncs = getCssColorFunctions();
                    List<CompletionItem> list = new ArrayList<>(colors.length + colorFuncs.length);
                    for (String c : colors) {
                        list.add(new CompletionItem(c, c, "Named color", CompletionItem.Type.CSS_VALUE, 0));
                    }
                    for (String cf : colorFuncs) {
                        String label = cf.replace("|", "");
                        list.add(new CompletionItem(label, cf, "Color function", CompletionItem.Type.CSS_VALUE, 0));
                    }
                    cssColorCompletionItemsCache = Collections.unmodifiableList(list);
                }
            }
        }
        return cssColorCompletionItemsCache;
    }

    public static TwoTierIndex getCssColorIndex() {
        if (cssColorIndexCache == null) {
            synchronized (lock) {
                if (cssColorIndexCache == null) {
                    cssColorIndexCache = new TwoTierIndex(getCssColorCompletionItems());
                }
            }
        }
        return cssColorIndexCache;
    }

    public static List<CompletionItem> getCssGlobalFunctionItems() {
        if (cssGlobalFunctionItemsCache == null) {
            synchronized (lock) {
                if (cssGlobalFunctionItemsCache == null) {
                    String[] funcs = getCssGlobalFunctions();
                    List<CompletionItem> list = new ArrayList<>(funcs.length);
                    for (String f : funcs) {
                        String label = f.replace("|", "");
                        list.add(new CompletionItem(label, f, "Function", CompletionItem.Type.CSS_VALUE, 0));
                    }
                    cssGlobalFunctionItemsCache = Collections.unmodifiableList(list);
                }
            }
        }
        return cssGlobalFunctionItemsCache;
    }

    public static List<CompletionItem> getJsKeywordCompletionItems() {
        if (jsKeywordCompletionItemsCache == null) {
            synchronized (lock) {
                if (jsKeywordCompletionItemsCache == null) {
                    StaticCompletionItem[] keywords = getJsKeywords();
                    List<CompletionItem> list = new ArrayList<>(keywords.length);
                    for (StaticCompletionItem k : keywords) {
                        SnippetCursorParser.Result r = SnippetCursorParser.parse(k.insertText);
                        String insertText = r != null ? r.insertText : k.insertText;
                        int cursorOffset = r != null ? r.cursorOffset : 0;
                        CompletionItem.Type type = CompletionItem.Type.KEYWORD;
                        if ("BUILTIN".equalsIgnoreCase(k.type)) {
                            type = CompletionItem.Type.BUILTIN;
                        } else if ("SNIPPET".equalsIgnoreCase(k.type)) {
                            type = CompletionItem.Type.SNIPPET;
                        }
                        list.add(new CompletionItem(k.label, insertText, k.detail, type, cursorOffset));
                    }
                    jsKeywordCompletionItemsCache = Collections.unmodifiableList(list);
                }
            }
        }
        return jsKeywordCompletionItemsCache;
    }

    public static TwoTierIndex getJsKeywordIndex() {
        if (jsKeywordIndexCache == null) {
            synchronized (lock) {
                if (jsKeywordIndexCache == null) {
                    jsKeywordIndexCache = new TwoTierIndex(getJsKeywordCompletionItems());
                }
            }
        }
        return jsKeywordIndexCache;
    }

    public static List<CompletionItem> getJsonSnippetCompletionItems() {
        if (jsonSnippetCompletionItemsCache == null) {
            synchronized (lock) {
                if (jsonSnippetCompletionItemsCache == null) {
                    StaticCompletionItem[] snippets = getJsonSnippets();
                    List<CompletionItem> list = new ArrayList<>(snippets.length);
                    for (StaticCompletionItem k : snippets) {
                        SnippetCursorParser.Result r = SnippetCursorParser.parse(k.insertText);
                        String insertText = r != null ? r.insertText : k.insertText;
                        int cursorOffset = r != null ? r.cursorOffset : 0;
                        list.add(new CompletionItem(k.label, insertText, k.detail,
                                CompletionItem.Type.SNIPPET, cursorOffset));
                    }
                    jsonSnippetCompletionItemsCache = Collections.unmodifiableList(list);
                }
            }
        }
        return jsonSnippetCompletionItemsCache;
    }

    public static TwoTierIndex getJsonSnippetIndex() {
        if (jsonSnippetIndexCache == null) {
            synchronized (lock) {
                if (jsonSnippetIndexCache == null) {
                    jsonSnippetIndexCache = new TwoTierIndex(getJsonSnippetCompletionItems());
                }
            }
        }
        return jsonSnippetIndexCache;
    }

    public static Map<String, List<CompletionItem>> getJsonSchemaKeysMap() {
        if (jsonSchemaKeysMapCache == null) {
            synchronized (lock) {
                if (jsonSchemaKeysMapCache == null) {
                    Map<String, List<CompletionItem>> map = new LinkedHashMap<>();
                    String jsonStr = StaticAssetReader.readAsset(JSON_SCHEMAS_ASSET);
                    if (jsonStr != null && !jsonStr.trim().isEmpty()) {
                        try {
                            JSONObject root = new JSONObject(jsonStr);
                            java.util.Iterator<String> keys = root.keys();
                            while (keys.hasNext()) {
                                String schemaName = keys.next();
                                JSONArray arr = root.getJSONArray(schemaName);
                                List<CompletionItem> items = new ArrayList<>(arr.length());
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONArray entry = arr.getJSONArray(i);
                                    String key = entry.getString(0);
                                    String insertSnippet = entry.getString(1);
                                    String detail = entry.getString(2);
                                    items.add(new CompletionItem(key, insertSnippet, detail, CompletionItem.Type.JSON_KEY, 0));
                                }
                                map.put(schemaName, Collections.unmodifiableList(items));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    jsonSchemaKeysMapCache = Collections.unmodifiableMap(map);
                }
            }
        }
        return jsonSchemaKeysMapCache;
    }

    /**
     * Eagerly parses and warms all static-completion datasets and TwoTierIndexes in RAM.
     * Dispatched on app startup to guarantee zero UI latency when opening any editor.
     */
    public static void warmUp() {
        getJsKeywords();
        getJsKeywordCompletionItems();
        getJsKeywordIndex();

        getHtmlTags();
        getHtmlGlobalAttributes();
        getHtmlGlobalAttributePrefixes();
        getHtmlTagCompletionItems();
        getHtmlTagIndex();
        getHtmlTagAttributeMap();
        getHtmlBoilerplate();
        com.cocode.vcode.ide.core.language.html.HtmlDefinitions.ensureLoaded();

        getCssProperties();
        getCssColors();
        getCssColorProperties();
        getCssPropertyCompletionItems();
        getCssPropertyIndex();
        getCssPropertyValueMap();
        getCssHtmlTagCompletionItems();
        getCssHtmlTagIndex();
        getCssColorCompletionItems();
        getCssColorIndex();
        getCssGlobalFunctionItems();
        com.cocode.vcode.ide.core.language.css.CssDefinitions.loadCssDefinitions();

        getJsonSnippets();
        getJsonSnippetCompletionItems();
        getJsonSnippetIndex();
        getJsonSchemaKeysMap();

        com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine.loadBuiltinTypeTable();
    }

    // --- Reset (test-only) ---------------------------------------------

    /**
     * Drop all cached state. Intended for tests that want to
     * inject custom JSON.
     */
    public static synchronized void resetForTest() {
        jsKeywordsCache = null;
        htmlTagsCache = null;
        htmlGlobalAttributesCache = null;
        htmlGlobalAttributePrefixesCache = null;
        cssPropertiesCache = null;
        cssPropertyMapCache = null;
        cssColorsCache = null;
        cssColorPropertiesCache = null;
        cssColorFunctionsCache = null;
        cssGlobalFunctionsCache = null;
        jsonSnippetsCache = null;

        htmlTagCompletionItemsCache = null;
        htmlTagIndexCache = null;
        htmlTagAttributeMapCache = null;
        htmlBoilerplateCache = null;
        cssPropertyCompletionItemsCache = null;
        cssPropertyIndexCache = null;
        cssPropertyValueMapCache = null;
        cssHtmlTagCompletionItemsCache = null;
        cssHtmlTagIndexCache = null;
        cssColorCompletionItemsCache = null;
        cssColorIndexCache = null;
        cssGlobalFunctionItemsCache = null;
        jsKeywordCompletionItemsCache = null;
        jsKeywordIndexCache = null;
        jsonSnippetCompletionItemsCache = null;
        jsonSnippetIndexCache = null;
        jsonSchemaKeysMapCache = null;

        JsStaticCompletionDispatcher.clearCachesForTest();
        HtmlStaticCompletionDispatcher.clearCachesForTest();
        CssStaticCompletionDispatcher.clearCachesForTest();
        JsonStaticCompletionDispatcher.clearCachesForTest();
        com.cocode.vcode.ide.core.diagnostic.util.KnownElements.resetForTest();
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

    /**
     * Public for test injection: parse a JS keywords JSON string
     * into {@link StaticCompletionItem} array.
     */
    public static StaticCompletionItem[] parseJsKeywords(String json) {
        return parseFlatItemArray(json, false);
    }

    /**
     * Public for test injection: parse an HTML tags JSON string.
     * The string may be either a top-level array (legacy) or a
     * top-level object with {@code tags}, {@code globalAttributes},
     * {@code globalAttributePrefixes} keys.
     */
    public static StaticCompletionItem[] parseHtmlTags(String json) {
        return parseHtmlTags(json, new java.util.ArrayList<>(), new java.util.ArrayList<>());
    }

    /**
     * Overload that also populates {@code outGlobalAttrs} and
     * {@code outGlobalPrefixes}.
     */
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

    /**
     * Public for test injection: parse a CSS properties JSON string.
     */
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

    /**
     * Public for test injection: parse a CSS colors JSON object.
     */
    public static CssColorSets parseCssColors(String json) {
        CssColorSets s = new CssColorSets();
        try {
            JSONObject o = new JSONObject(json);
            s.colors = readStringArray(o, "colors");
            s.colorFunctions = readStringArray(o, "color_functions");
            s.globalFunctions = readStringArray(o, "global_functions");
        } catch (JSONException ignored) {
            // Leave defaults.
        }
        return s;
    }

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

    // --- Internal parsers ------------------------------------------------

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

    /**
     * Holder returned by {@link #parseCssColors(String)}.
     */
    public static final class CssColorSets {
        public String[] colors = new String[0];
        public String[] colorFunctions = new String[0];
        public String[] globalFunctions = new String[0];
    }
}

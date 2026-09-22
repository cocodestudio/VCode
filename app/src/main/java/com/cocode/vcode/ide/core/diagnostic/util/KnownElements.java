package com.cocode.vcode.ide.core.diagnostic.util;

import android.content.Context;

import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;
import com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionItem;
import com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
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

    // HTML tag, void element & inline element lookup tables — populated from completions/html_tags.json
    public static final Set<String> VOID_ELEMENTS = new HashSet<>();
    public static final Set<String> VALID_HTML_TAGS = new HashSet<>();
    public static final Set<String> INLINE_ELEMENTS = new HashSet<>();

    // CSS properties & colors — populated from completions/css_properties.json & css_colors.json
    public static final Set<String> VALID_CSS_PROPERTIES = new HashSet<>();
    public static final Set<String> CSS_COLOR_PROPERTIES = new HashSet<>();
    public static final Set<String> CSS_NAMED_COLORS = new HashSet<>();
    public static final Set<String> CSS_PURE_COLOR_PROPERTIES = new HashSet<>();
    public static final Set<String> CSS_MULTI_COLOR_PROPERTIES = new HashSet<>();
    public static final Set<String> CSS_BORDER_SHORTHAND_PROPERTIES = new HashSet<>();
    private static final Object lock = new Object();
    private static volatile boolean isLoaded = false;

    static {
        // Fallback bootstrap records for void & block elements so queries never fail
        Collections.addAll(VOID_ELEMENTS,
                "area", "base", "br", "col", "embed", "hr", "img", "input",
                "link", "meta", "param", "source", "track", "wbr");

        Collections.addAll(BLOCK_ELEMENTS,
                "address", "article", "aside", "blockquote", "details", "dialog", "dd", "div",
                "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2",
                "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "li", "main", "nav",
                "noscript", "ol", "p", "pre", "section", "table", "ul");

        Collections.addAll(CSS_PURE_COLOR_PROPERTIES,
                "color", "background-color",
                "border-top-color", "border-right-color", "border-bottom-color", "border-left-color",
                "border-block-color", "border-block-start-color", "border-block-end-color",
                "border-inline-color", "border-inline-start-color", "border-inline-end-color",
                "text-decoration-color", "column-rule-color",
                "caret-color", "accent-color", "outline-color",
                "flood-color", "lighting-color", "stop-color");

        Collections.addAll(CSS_MULTI_COLOR_PROPERTIES,
                "border-color", "scrollbar-color");

        Collections.addAll(CSS_BORDER_SHORTHAND_PROPERTIES,
                "border", "border-top", "border-right", "border-bottom", "border-left",
                "border-block", "border-block-start", "border-block-end",
                "border-inline", "border-inline-start", "border-inline-end",
                "outline", "column-rule");

        Collections.addAll(INLINE_ELEMENTS,
                "a", "abbr", "acronym", "b", "bdo", "big", "br", "button", "cite", "code", "dfn", "em", "i",
                "img", "input", "kbd", "label", "map", "object", "output", "q", "s", "samp", "select", "small",
                "span", "strong", "sub", "sup", "textarea", "time", "tt", "u", "var");

        Collections.addAll(CSS_COLOR_PROPERTIES,
                "color", "background-color", "border-color", "border-top-color", "border-right-color",
                "border-bottom-color", "border-left-color", "outline-color", "text-decoration-color", "caret-color",
                "accent-color", "column-rule-color", "scrollbar-color");

        Collections.addAll(JS_GLOBALS,
                "window", "document", "console", "Math", "JSON", "Date", "Array", "Object",
                "String", "Number", "Boolean", "Promise", "fetch", "setTimeout", "setInterval",
                "clearTimeout", "clearInterval", "parseInt", "parseFloat", "isNaN", "isFinite",
                "encodeURIComponent", "decodeURIComponent", "decodeURI", "encodeURI",
                "localStorage", "sessionStorage", "location", "navigator", "history",
                "alert", "confirm", "prompt", "Error", "TypeError", "RangeError", "SyntaxError",
                "ReferenceError", "URIError", "EvalError", "undefined", "null", "NaN",
                "Infinity", "globalThis", "self", "queueMicrotask", "requestAnimationFrame",
                "cancelAnimationFrame", "URL", "URLSearchParams", "FormData", "XMLHttpRequest",
                "EventSource", "WebSocket", "MutationObserver", "IntersectionObserver",
                "ResizeObserver", "performance", "crypto", "Intl", "Symbol", "Map", "Set",
                "WeakMap", "WeakSet", "WeakRef", "Proxy", "Reflect", "Generator", "RegExp",
                "ArrayBuffer", "DataView", "Int8Array", "Uint8Array", "Int16Array", "Uint16Array",
                "Int32Array", "Uint32Array", "Float32Array", "Float64Array", "BigInt", "BigInt64Array",
                "BigUint64Array", "SharedArrayBuffer", "Atomics", "TextEncoder", "TextDecoder",
                "structuredClone", "addEventListener", "removeEventListener", "dispatchEvent",
                "escape", "unescape", "eval", "arguments", "this", "super", "require", "exports",
                "module", "__dirname", "__filename", "process", "Buffer", "global", "$", "jQuery",
                "React", "Vue", "Angular", "define",
                "Blob", "File", "FileList", "FileReader", "Headers", "Request", "Response",
                "AbortController", "AbortSignal", "Event", "CustomEvent", "MessageEvent",
                "ErrorEvent", "UIEvent", "MouseEvent", "KeyboardEvent", "FocusEvent", "InputEvent",
                "PointerEvent", "TouchEvent", "WheelEvent", "AnimationEvent", "TransitionEvent",
                "Node", "Element", "HTMLElement", "HTMLInputElement", "HTMLButtonElement",
                "HTMLCanvasElement", "HTMLDivElement", "HTMLSpanElement", "HTMLAnchorElement",
                "HTMLImageElement", "HTMLFormElement", "SVGElement", "DocumentFragment",
                "DOMParser", "XMLSerializer", "Worker", "SharedWorker", "ServiceWorker",
                "MessageChannel", "MessagePort", "indexedDB", "AudioContext", "Audio",
                "Image", "Option", "ReadableStream", "WritableStream", "TransformStream",
                "Notification", "DOMException", "Range", "Selection", "CSSStyleDeclaration");

        Collections.addAll(VALID_HTML_TAGS,
                "html", "head", "title", "base", "link", "meta", "style", "body",
                "article", "section", "nav", "aside", "h1", "h2", "h3", "h4", "h5", "h6",
                "hgroup", "header", "footer", "address", "p", "hr", "pre", "blockquote",
                "ol", "ul", "menu", "li", "dl", "dt", "dd", "figure", "figcaption", "main",
                "div", "a", "em", "strong", "small", "s", "cite", "q", "dfn", "abbr", "ruby",
                "rt", "rp", "data", "time", "code", "var", "samp", "kbd", "sub", "sup", "i",
                "b", "u", "mark", "bdi", "bdo", "span", "br", "wbr", "ins", "del", "picture",
                "source", "img", "iframe", "embed", "object", "param", "video", "audio", "track",
                "map", "area", "table", "caption", "colgroup", "col", "tbody", "thead", "tfoot",
                "tr", "td", "th", "form", "label", "input", "button", "select", "datalist",
                "optgroup", "option", "textarea", "output", "progress", "meter", "fieldset",
                "legend", "details", "summary", "dialog", "script", "noscript", "template",
                "slot", "canvas", "svg", "path", "circle", "rect", "line", "polyline", "polygon",
                "text", "tspan", "g", "defs", "use", "symbol", "clipPath", "mask", "pattern",
                "linearGradient", "radialGradient", "stop", "filter", "foreignObject", "math");

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
            if (!VALID_HTML_TAGS.isEmpty() && !VALID_CSS_PROPERTIES.isEmpty()) {
                isLoaded = true;
            }
        }
    }

    /**
     * Determines if a tag is a self-closing void element that cannot contain internal children.
     */
    public static boolean isVoidElement(String tag) {
        if (!isLoaded) ensureLoaded();
        return tag != null && VOID_ELEMENTS.contains(tag.toLowerCase());
    }

    /**
     * Determines if a tag behaves as structural block-level markup.
     */
    public static boolean isBlockElement(String tag) {
        if (!isLoaded) ensureLoaded();
        return tag != null && BLOCK_ELEMENTS.contains(tag.toLowerCase());
    }

    /**
     * Determines if a tag behaves as inline markup.
     */
    public static boolean isInlineElement(String tag) {
        if (!isLoaded) ensureLoaded();
        return tag != null && INLINE_ELEMENTS.contains(tag.toLowerCase());
    }

    /**
     * Determines if a CSS property accepts color values.
     */
    public static boolean isCssColorProperty(String prop) {
        if (!isLoaded) ensureLoaded();
        return prop != null && CSS_COLOR_PROPERTIES.contains(prop.toLowerCase());
    }

    /**
     * Determines if a CSS property strictly expects a single color value (e.g. 'color', 'background-color').
     */
    public static boolean isPureColorProperty(String prop) {
        if (!isLoaded) ensureLoaded();
        return prop != null && CSS_PURE_COLOR_PROPERTIES.contains(prop.toLowerCase());
    }

    /**
     * Determines if a CSS property accepts multiple space-separated colors (e.g. 'border-color', 'scrollbar-color').
     */
    public static boolean isMultiColorProperty(String prop) {
        if (!isLoaded) ensureLoaded();
        return prop != null && CSS_MULTI_COLOR_PROPERTIES.contains(prop.toLowerCase());
    }

    /**
     * Determines if a CSS property is a border-like shorthand (e.g. 'border', 'border-top', 'outline').
     */
    public static boolean isBorderShorthandProperty(String prop) {
        if (!isLoaded) ensureLoaded();
        return prop != null && CSS_BORDER_SHORTHAND_PROPERTIES.contains(prop.toLowerCase());
    }

    /**
     * Drops cached loaded flag for test injection.
     */
    public static void resetForTest() {
        synchronized (lock) {
            isLoaded = false;
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
            StaticCompletionItem[] items = StaticCompletionLoader.getHtmlTags();
            if (items != null && items.length > 0) {
                VALID_HTML_TAGS.clear();
                VOID_ELEMENTS.clear();
                INLINE_ELEMENTS.clear();
                for (StaticCompletionItem item : items) {
                    if (item.label != null && !item.label.isEmpty()) {
                        String tag = item.label.toLowerCase();
                        VALID_HTML_TAGS.add(tag);
                        if (item.selfClosing) {
                            VOID_ELEMENTS.add(tag);
                        }
                        if (!BLOCK_ELEMENTS.contains(tag) && !isSpecialDocumentElement(tag)) {
                            INLINE_ELEMENTS.add(tag);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isSpecialDocumentElement(String tag) {
        return "html".equals(tag) || "head".equals(tag) || "body".equals(tag)
                || "style".equals(tag) || "script".equals(tag);
    }

    private static void loadCssProperties() {
        try {
            StaticCompletionItem[] items = StaticCompletionLoader.getCssProperties();
            if (items != null && items.length > 0) {
                VALID_CSS_PROPERTIES.clear();
                CSS_COLOR_PROPERTIES.clear();
                for (StaticCompletionItem item : items) {
                    if (item.label != null && !item.label.isEmpty()) {
                        String prop = item.label.toLowerCase();
                        VALID_CSS_PROPERTIES.add(prop);
                        if (item.acceptsColor) {
                            CSS_COLOR_PROPERTIES.add(prop);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadCssColors() {
        try {
            String[] colors = StaticCompletionLoader.getCssColors();
            if (colors != null && colors.length > 0) {
                CSS_NAMED_COLORS.clear();
                for (String c : colors) {
                    if (c != null && !c.isEmpty()) {
                        CSS_NAMED_COLORS.add(c.toLowerCase());
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}

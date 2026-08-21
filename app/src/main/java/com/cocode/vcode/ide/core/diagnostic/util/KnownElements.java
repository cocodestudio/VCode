package com.cocode.vcode.ide.core.diagnostic.util;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static lookup tables and caches for HTML elements, attributes, CSS properties, colors, and JS globals used by linters.
 */
public final class KnownElements {

    // Asset-populated sets (initialized via init())
    public static final Map<String, String> DEPRECATED_ELEMENTS = new HashMap<>();
    public static final Map<String, String> DEPRECATED_ATTRIBUTES = new HashMap<>();
    public static final Set<String> BLOCK_ELEMENTS = new HashSet<>(Arrays.asList(
            "address", "article", "aside", "blockquote", "details", "dialog", "dd", "div",
            "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2",
            "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "li", "main", "nav", "ol",
            "p", "pre", "section", "summary", "table", "ul"
    ));
    public static final Map<String, Set<String>> REQUIRED_PARENTS = new HashMap<>();

    // Linter-logic lookup tables
    public static final Map<String, Set<String>> REQUIRED_ATTRIBUTES = new HashMap<>();
    public static final Map<String, String> SEMANTIC_SUGGESTIONS = new LinkedHashMap<>();
    public static final Map<String, String> VENDOR_PREFIX_NEEDED = new LinkedHashMap<>();
    public static final Map<String, Set<String>> CSS_SHORTHAND_LONGHANDS = new HashMap<>();
    public static final Set<String> JS_GLOBALS = new HashSet<>(Arrays.asList(
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
            "atob", "btoa", "getComputedStyle", "matchMedia", "open", "close", "focus", "blur", "print",
            "escape", "unescape", "eval", "arguments", "this", "super", "require", "exports",
            "module", "__dirname", "__filename", "process", "Buffer", "global", "$", "jQuery",
            "React", "Vue", "Angular", "define"
    ));
    public static final Set<String> ASYNC_APIS = new HashSet<>(Arrays.asList(
            "fetch", "json", "text", "arrayBuffer", "blob", "formData",
            "then", "catch", "finally", "all", "allSettled", "race", "any",
            "resolve", "reject", "waitUntil", "respondWith", "openDB", "getAll",
            "get", "put", "delete", "add", "clear", "openCursor", "transaction"
    ));
    public static Set<String> VOID_ELEMENTS = new HashSet<>(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"
    ));
    public static Set<String> VALID_HTML_TAGS = new HashSet<>(Arrays.asList(
            "a", "abbr", "address", "article", "aside", "audio", "b", "bdi", "bdo", "blockquote", "body", "br",
            "button", "canvas", "caption", "cite", "code", "col", "colgroup", "data", "datalist", "dd", "del",
            "details", "dfn", "dialog", "div", "dl", "dt", "em", "embed", "fieldset", "figcaption", "figure",
            "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hgroup", "hr", "html", "i",
            "iframe", "img", "input", "ins", "kbd", "label", "legend", "li", "link", "main", "map", "mark", "menu",
            "meta", "meter", "nav", "noscript", "object", "ol", "optgroup", "option", "output", "p", "picture",
            "pre", "progress", "q", "rp", "rt", "ruby", "s", "samp", "script", "search", "section", "select",
            "slot", "small", "source", "span", "strong", "style", "sub", "summary", "sup", "table", "tbody", "td",
            "template", "textarea", "tfoot", "th", "thead", "time", "title", "tr", "track", "u", "ul", "var",
            "video", "wbr"
    ));
    public static Set<String> VALID_CSS_PROPERTIES = new HashSet<>(Arrays.asList(
            "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
            "width", "height", "min-width", "max-width", "min-height", "max-height",
            "box-sizing", "overflow", "overflow-x", "overflow-y",
            "border", "border-top", "border-right", "border-bottom", "border-left",
            "border-width", "border-style", "border-color", "border-radius", "border-collapse", "border-spacing",
            "display", "visibility", "opacity", "z-index", "position", "top", "right", "bottom", "left",
            "float", "clear", "clip-path", "clip", "aspect-ratio",
            "flex", "flex-direction", "flex-wrap", "flex-flow", "justify-content", "align-items",
            "align-self", "align-content", "flex-grow", "flex-shrink", "flex-basis", "order", "gap",
            "row-gap", "column-gap",
            "grid", "grid-template", "grid-template-columns", "grid-template-rows", "grid-template-areas",
            "grid-area", "grid-column", "grid-row", "grid-auto-columns", "grid-auto-rows", "grid-auto-flow",
            "justify-items", "justify-self", "place-items", "place-content", "place-self",
            "font", "font-family", "font-size", "font-weight", "font-style", "font-variant",
            "line-height", "letter-spacing", "word-spacing", "text-align", "text-indent",
            "text-transform", "text-decoration", "text-shadow", "text-overflow", "white-space",
            "word-break", "overflow-wrap", "vertical-align",
            "color", "background", "background-color", "background-image", "background-position",
            "background-size", "background-repeat", "background-attachment", "background-clip",
            "transform", "transform-origin", "transition", "animation",
            "filter", "backdrop-filter", "box-shadow", "outline",
            "list-style", "content", "cursor", "pointer-events", "user-select",
            "fill", "stroke", "stroke-width", "mask", "mask-image"
    ));
    // Named CSS colors — populated from css_colors.json
    public static Set<String> CSS_NAMED_COLORS = new HashSet<>(Arrays.asList(
            "aliceblue", "antiquewhite", "aqua", "aquamarine", "azure", "beige", "bisque", "black",
            "blanchedalmond", "blue", "blueviolet", "brown", "burlywood", "cadetblue", "chartreuse",
            "chocolate", "coral", "cornflowerblue", "cornsilk", "crimson", "cyan", "darkblue", "darkcyan",
            "darkgoldenrod", "darkgray", "darkgreen", "darkgrey", "darkkhaki", "darkmagenta",
            "darkolivegreen", "darkorange", "darkorchid", "darkred", "darksalmon", "darkseagreen",
            "darkslateblue", "darkslategray", "darkturquoise", "darkviolet", "deeppink", "deepskyblue",
            "dimgray", "dimgrey", "dodgerblue", "firebrick", "floralwhite", "forestgreen", "fuchsia",
            "gainsboro", "ghostwhite", "gold", "goldenrod", "gray", "green", "greenyellow", "grey",
            "honeydew", "hotpink", "indianred", "indigo", "ivory", "khaki", "lavender", "lavenderblush",
            "lawngreen", "lemonchiffon", "lightblue", "lightcoral", "lightcyan", "lightgoldenrodyellow",
            "lightgray", "lightgreen", "lightgrey", "lightpink", "lightsalmon", "lightseagreen",
            "lightskyblue", "lightslategray", "lightsteelblue", "lightyellow", "lime", "limegreen",
            "linen", "magenta", "maroon", "mediumaquamarine", "mediumblue", "mediumorchid", "mediumpurple",
            "mediumseagreen", "mediumslateblue", "mediumspringgreen", "mediumturquoise",
            "mediumvioletred", "midnightblue", "mintcream", "mistyrose", "moccasin", "navajowhite",
            "navy", "oldlace", "olive", "olivedrab", "orange", "orangered", "orchid", "palegoldenrod",
            "palegreen", "paleturquoise", "palevioletred", "papayawhip", "peachpuff", "peru", "pink",
            "plum", "powderblue", "purple", "rebeccapurple", "red", "rosybrown", "royalblue",
            "saddlebrown", "salmon", "sandybrown", "seagreen", "seashell", "sienna", "silver", "skyblue",
            "slateblue", "slategray", "slategrey", "snow", "springgreen", "steelblue", "tan", "teal",
            "thistle", "tomato", "transparent", "turquoise", "violet", "wheat", "white", "whitesmoke",
            "yellow", "yellowgreen", "currentcolor", "inherit", "initial", "unset", "revert", "revert-layer"
    ));

    static {
        DEPRECATED_ELEMENTS.put("font", "CSS font-* properties");
        DEPRECATED_ELEMENTS.put("center", "CSS text-align: center");
        DEPRECATED_ELEMENTS.put("marquee", "CSS animations");
        DEPRECATED_ELEMENTS.put("blink", "CSS animations");
        DEPRECATED_ELEMENTS.put("frameset", "<iframe> or modern layout");
        DEPRECATED_ELEMENTS.put("frame", "<iframe>");
        DEPRECATED_ELEMENTS.put("noframes", "N/A — remove");
        DEPRECATED_ELEMENTS.put("acronym", "<abbr>");
        DEPRECATED_ELEMENTS.put("applet", "<object> or <embed>");
        DEPRECATED_ELEMENTS.put("basefont", "CSS font-* on body");
        DEPRECATED_ELEMENTS.put("big", "CSS font-size");
        DEPRECATED_ELEMENTS.put("strike", "<del> or CSS text-decoration");
        DEPRECATED_ELEMENTS.put("tt", "<code> or <kbd>");
    }

    static {
        DEPRECATED_ATTRIBUTES.put("*:bgcolor", "CSS background-color");
        DEPRECATED_ATTRIBUTES.put("*:align", "CSS text-align or flexbox");
        DEPRECATED_ATTRIBUTES.put("table:border", "CSS border");
        DEPRECATED_ATTRIBUTES.put("table:cellpadding", "CSS padding");
        DEPRECATED_ATTRIBUTES.put("table:cellspacing", "CSS border-spacing");
        DEPRECATED_ATTRIBUTES.put("img:border", "CSS border");
        DEPRECATED_ATTRIBUTES.put("body:link", "CSS a:link");
        DEPRECATED_ATTRIBUTES.put("body:vlink", "CSS a:visited");
        DEPRECATED_ATTRIBUTES.put("body:alink", "CSS a:active");
        DEPRECATED_ATTRIBUTES.put("body:text", "CSS color on body");
        DEPRECATED_ATTRIBUTES.put("hr:noshade", "CSS border style");
        DEPRECATED_ATTRIBUTES.put("*:language", "remove — obsolete");
    }

    static {
        REQUIRED_PARENTS.put("li", new HashSet<>(Arrays.asList("ul", "ol", "menu")));
        REQUIRED_PARENTS.put("td", new HashSet<>(List.of("tr")));
        REQUIRED_PARENTS.put("th", new HashSet<>(List.of("tr")));
        REQUIRED_PARENTS.put("tr", new HashSet<>(Arrays.asList("table", "thead", "tbody", "tfoot")));
        REQUIRED_PARENTS.put("caption", new HashSet<>(List.of("table")));
        REQUIRED_PARENTS.put("colgroup", new HashSet<>(List.of("table")));
        REQUIRED_PARENTS.put("col", new HashSet<>(List.of("colgroup")));
        REQUIRED_PARENTS.put("thead", new HashSet<>(List.of("table")));
        REQUIRED_PARENTS.put("tbody", new HashSet<>(List.of("table")));
        REQUIRED_PARENTS.put("tfoot", new HashSet<>(List.of("table")));
        REQUIRED_PARENTS.put("option", new HashSet<>(Arrays.asList("select", "datalist", "optgroup")));
        REQUIRED_PARENTS.put("optgroup", new HashSet<>(List.of("select")));
        REQUIRED_PARENTS.put("dt", new HashSet<>(List.of("dl")));
        REQUIRED_PARENTS.put("dd", new HashSet<>(List.of("dl")));
        REQUIRED_PARENTS.put("source", new HashSet<>(Arrays.asList("picture", "video", "audio")));
        REQUIRED_PARENTS.put("track", new HashSet<>(Arrays.asList("video", "audio")));
        REQUIRED_PARENTS.put("summary", new HashSet<>(List.of("details")));
    }

    static {
        REQUIRED_ATTRIBUTES.put("img", new HashSet<>(Arrays.asList("src", "alt")));
        REQUIRED_ATTRIBUTES.put("input", new HashSet<>(List.of("type")));
        REQUIRED_ATTRIBUTES.put("a", new HashSet<>(List.of("href")));
        REQUIRED_ATTRIBUTES.put("iframe", new HashSet<>(List.of("src")));
        REQUIRED_ATTRIBUTES.put("video", new HashSet<>(List.of("src")));
        REQUIRED_ATTRIBUTES.put("audio", new HashSet<>(List.of("src")));
        REQUIRED_ATTRIBUTES.put("track", new HashSet<>(Arrays.asList("src", "kind")));
        REQUIRED_ATTRIBUTES.put("area", new HashSet<>(List.of("alt")));
        REQUIRED_ATTRIBUTES.put("th", new HashSet<>(List.of("scope")));
        REQUIRED_ATTRIBUTES.put("label", new HashSet<>(List.of("for")));
        REQUIRED_ATTRIBUTES.put("form", new HashSet<>(List.of("action")));
        REQUIRED_ATTRIBUTES.put("meta", new HashSet<>(List.of("content")));
        REQUIRED_ATTRIBUTES.put("link", new HashSet<>(Arrays.asList("href", "rel")));
        REQUIRED_ATTRIBUTES.put("script", new HashSet<>(List.of("src")));
        REQUIRED_ATTRIBUTES.put("button", new HashSet<>(List.of("type")));
    }

    static {
        SEMANTIC_SUGGESTIONS.put("header", "<header>");
        SEMANTIC_SUGGESTIONS.put("nav", "<nav>");
        SEMANTIC_SUGGESTIONS.put("footer", "<footer>");
        SEMANTIC_SUGGESTIONS.put("main", "<main>");
        SEMANTIC_SUGGESTIONS.put("sidebar", "<aside>");
        SEMANTIC_SUGGESTIONS.put("article", "<article>");
        SEMANTIC_SUGGESTIONS.put("section", "<section>");
        SEMANTIC_SUGGESTIONS.put("aside", "<aside>");
    }

    static {
        VENDOR_PREFIX_NEEDED.put("user-select", "-webkit-user-select");
        VENDOR_PREFIX_NEEDED.put("appearance", "-webkit-appearance");
        VENDOR_PREFIX_NEEDED.put("backdrop-filter", "-webkit-backdrop-filter");
        VENDOR_PREFIX_NEEDED.put("text-stroke", "-webkit-text-stroke");
        VENDOR_PREFIX_NEEDED.put("mask-image", "-webkit-mask-image");
        VENDOR_PREFIX_NEEDED.put("clip-path", "-webkit-clip-path");
        VENDOR_PREFIX_NEEDED.put("box-decoration-break", "-webkit-box-decoration-break");
    }

    static {
        CSS_SHORTHAND_LONGHANDS.put("background", new HashSet<>(Arrays.asList(
                "background-color", "background-image", "background-position", "background-size",
                "background-repeat", "background-attachment", "background-clip", "background-origin")));
        CSS_SHORTHAND_LONGHANDS.put("border", new HashSet<>(Arrays.asList(
                "border-color", "border-width", "border-style")));
        CSS_SHORTHAND_LONGHANDS.put("border-top", new HashSet<>(Arrays.asList(
                "border-top-color", "border-top-width", "border-top-style")));
        CSS_SHORTHAND_LONGHANDS.put("border-right", new HashSet<>(Arrays.asList(
                "border-right-color", "border-right-width", "border-right-style")));
        CSS_SHORTHAND_LONGHANDS.put("border-bottom", new HashSet<>(Arrays.asList(
                "border-bottom-color", "border-bottom-width", "border-bottom-style")));
        CSS_SHORTHAND_LONGHANDS.put("border-left", new HashSet<>(Arrays.asList(
                "border-left-color", "border-left-width", "border-left-style")));
        CSS_SHORTHAND_LONGHANDS.put("margin", new HashSet<>(Arrays.asList(
                "margin-top", "margin-right", "margin-bottom", "margin-left")));
        CSS_SHORTHAND_LONGHANDS.put("padding", new HashSet<>(Arrays.asList(
                "padding-top", "padding-right", "padding-bottom", "padding-left")));
        CSS_SHORTHAND_LONGHANDS.put("font", new HashSet<>(Arrays.asList(
                "font-family", "font-size", "font-weight", "font-style", "font-variant",
                "font-stretch", "line-height")));
        CSS_SHORTHAND_LONGHANDS.put("transition", new HashSet<>(Arrays.asList(
                "transition-property", "transition-duration", "transition-timing-function", "transition-delay")));
        CSS_SHORTHAND_LONGHANDS.put("animation", new HashSet<>(Arrays.asList(
                "animation-name", "animation-duration", "animation-timing-function", "animation-delay",
                "animation-iteration-count", "animation-direction", "animation-fill-mode", "animation-play-state")));
        CSS_SHORTHAND_LONGHANDS.put("grid", new HashSet<>(Arrays.asList(
                "grid-template-rows", "grid-template-columns", "grid-template-areas",
                "grid-auto-rows", "grid-auto-columns", "grid-auto-flow")));
        CSS_SHORTHAND_LONGHANDS.put("flex", new HashSet<>(Arrays.asList(
                "flex-grow", "flex-shrink", "flex-basis")));
        CSS_SHORTHAND_LONGHANDS.put("list-style", new HashSet<>(Arrays.asList(
                "list-style-type", "list-style-position", "list-style-image")));
        CSS_SHORTHAND_LONGHANDS.put("outline", new HashSet<>(Arrays.asList(
                "outline-color", "outline-width", "outline-style")));
    }

    // Asset loader — call once from Application.onCreate()

    private KnownElements() {
    }

    public static void init(Context context) {
        loadHtmlTags(context);
        loadCssProperties(context);
        loadCssColors(context);
    }

    private static void loadHtmlTags(Context context) {
        try {
            String json = readAsset(context, "completions/html_tags.json");
            JSONArray arr = new JSONArray(json);
            Set<String> tags = new HashSet<>();
            Set<String> voids = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String tag = obj.optString("tag").toLowerCase();
                if (tag.isEmpty()) continue;
                tags.add(tag);
                if (obj.optBoolean("selfClosing", false)) voids.add(tag);
            }
            if (!tags.isEmpty()) VALID_HTML_TAGS = tags;
            if (!voids.isEmpty()) VOID_ELEMENTS = voids;
        } catch (Exception ignored) {
        }
    }

    private static void loadCssProperties(Context context) {
        try {
            String json = readAsset(context, "completions/css_properties.json");
            JSONArray arr = new JSONArray(json);
            Set<String> props = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                String prop = arr.getJSONObject(i).optString("property").toLowerCase();
                if (!prop.isEmpty()) props.add(prop);
            }
            if (!props.isEmpty()) VALID_CSS_PROPERTIES = props;
        } catch (Exception ignored) {
        }
    }

    private static void loadCssColors(Context context) {
        try {
            String json = readAsset(context, "completions/css_colors.json");
            JSONObject obj = new JSONObject(json);
            JSONArray colors = obj.optJSONArray("colors");
            if (colors == null) return;
            Set<String> set = new HashSet<>();
            for (int i = 0; i < colors.length(); i++) {
                String c = colors.optString(i).toLowerCase();
                if (!c.isEmpty()) set.add(c);
            }
            if (!set.isEmpty()) CSS_NAMED_COLORS = set;
        } catch (Exception ignored) {
        }
    }

    private static String readAsset(Context context, String path) throws Exception {
        try (InputStream is = context.getAssets().open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}

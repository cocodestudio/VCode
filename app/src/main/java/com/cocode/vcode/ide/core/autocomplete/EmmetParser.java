package com.cocode.vcode.ide.core.autocomplete;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;
import com.cocode.vcode.ide.core.language.css.EmmetCssDefinitions;
import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-grade Emmet abbreviation expander supporting HTML and CSS.
 *
 * <p>HTML features:
 * <ul>
 *   <li>AST-based recursive-descent parser supporting unlimited nesting and grouping</li>
 *   <li>Tag names, IDs (#), classes (.), multiplication (*), grouping (())</li>
 *   <li>Child (&gt;), sibling (+), climb-up (^) operators (including multi-level ^^, ^^^)</li>
 *   <li>Proper tree-wide multiplication child distribution (ul&gt;li*3&gt;a attaches &lt;a&gt; to all li)</li>
 *   <li>Item numbering with $ and zero-padded $$ across tags, IDs, classes, attributes, and text</li>
 *   <li>Contextual default tags (li in ul/ol, tr in table/tbody/thead/tfoot, td in tr, option in select)</li>
 *   <li>Multiple chained custom attributes: a[href="#"][target="_blank"]</li>
 *   <li>Unquoted and boolean attributes: button[disabled], input[type=text]</li>
 *   <li>Text content with {curly braces} including bare text nodes</li>
 *   <li>Comprehensive HTML aliases and colon shorthands: link:css, input:checkbox, meta:vp, script:src, etc.</li>
 * </ul>
 *
 * <p>CSS features:
 * <ul>
 *   <li>100+ named shorthand abbreviations (flexbox, grid, positioning, etc.)</li>
 *   <li>Numeric property shorthands (m10 → margin: 10px, w100p → width: 100%)</li>
 *   <li>Multi-value shorthands with positive and negative numbers (m10-20, m-10, m-10--20)</li>
 *   <li>Decimal support (op0.5 → opacity: 0.5, lh1.5 → line-height: 1.5, m1.5rem)</li>
 *   <li>Hex colors (c#f → color: #fff, c#333 → color: #333, bg#000)</li>
 *   <li>Auto values (m:a → margin: auto, w:a → width: auto)</li>
 *   <li>!important modifier (m10! → margin: 10px !important;)</li>
 * </ul>
 */
public class EmmetParser {

    // HTML Patterns
    private static final Pattern PAT_ABBR = Pattern.compile("^[a-zA-Z0-9_.#*()+>^\\[\\]=\"{} $!:\\-/@']+$");
    private static final Pattern PAT_HEX_COLOR = Pattern.compile("^(c|col|bg|bd|bdc)#([0-9a-fA-F]*)$");

    // CSS Patterns
    private static final Pattern PAT_CSS_NUMERIC = Pattern.compile(
            "^([a-z]+)(-?[0-9]+(?:\\.[0-9]+)?(?:-+[0-9]+(?:\\.[0-9]+)?)*)([a-z%]*)$");

    // Shared Cache
    private static final Object lock = new Object();
    private static volatile boolean loaded = false;
    private static String defaultBoilerplate;
    private static String[] loremWords;
    private static final Map<String, String> HTML_ALIASES = new HashMap<>();

    // HTML Snippet Aliases (standalone whole-snippet expansions)
    private static final Map<String, String> HTML_SNIPPET_ALIASES = new HashMap<>();
    static {
        HTML_SNIPPET_ALIASES.put("select+", "<select name=\"|\" id=\"\">\n    <option value=\"\"></option>\n</select>");
        HTML_SNIPPET_ALIASES.put("ol+", "<ol>\n    <li>|</li>\n</ol>");
        HTML_SNIPPET_ALIASES.put("ul+", "<ul>\n    <li>|</li>\n</ul>");
        HTML_SNIPPET_ALIASES.put("dl+", "<dl>\n    <dt>|</dt>\n    <dd></dd>\n</dl>");
        HTML_SNIPPET_ALIASES.put("table+", "<table>\n    <tr>\n        <td>|</td>\n    </tr>\n</table>");
    }

    // Default tag attributes for standard elements
    private static final Map<String, Map<String, String>> DEFAULT_TAG_ATTRS = new HashMap<>();
    static {
        Map<String, String> aAttrs = new LinkedHashMap<>();
        aAttrs.put("href", "");
        DEFAULT_TAG_ATTRS.put("a", aAttrs);

        Map<String, String> linkAttrs = new LinkedHashMap<>();
        linkAttrs.put("rel", "stylesheet");
        linkAttrs.put("href", "");
        DEFAULT_TAG_ATTRS.put("link", linkAttrs);

        Map<String, String> imgAttrs = new LinkedHashMap<>();
        imgAttrs.put("src", "");
        imgAttrs.put("alt", "");
        DEFAULT_TAG_ATTRS.put("img", imgAttrs);

        Map<String, String> inputAttrs = new LinkedHashMap<>();
        inputAttrs.put("type", "text");
        DEFAULT_TAG_ATTRS.put("input", inputAttrs);

        Map<String, String> formAttrs = new LinkedHashMap<>();
        formAttrs.put("action", "");
        DEFAULT_TAG_ATTRS.put("form", formAttrs);

        Map<String, String> iframeAttrs = new LinkedHashMap<>();
        iframeAttrs.put("src", "");
        iframeAttrs.put("frameborder", "0");
        DEFAULT_TAG_ATTRS.put("iframe", iframeAttrs);
    }

    // Known colon shorthands mapped to base tag + default attributes
    private static final Map<String, TagTemplate> COLON_TAG_TEMPLATES = new HashMap<>();
    static {
        registerColonTemplate("input:text", "input", "type", "text", "name", "", "id", "");
        registerColonTemplate("input:password", "input", "type", "password", "name", "", "id", "");
        registerColonTemplate("input:p", "input", "type", "password", "name", "", "id", "");
        registerColonTemplate("input:checkbox", "input", "type", "checkbox", "name", "", "id", "");
        registerColonTemplate("input:c", "input", "type", "checkbox", "name", "", "id", "");
        registerColonTemplate("input:radio", "input", "type", "radio", "name", "", "id", "");
        registerColonTemplate("input:r", "input", "type", "radio", "name", "", "id", "");
        registerColonTemplate("input:submit", "input", "type", "submit", "value", "");
        registerColonTemplate("input:s", "input", "type", "submit", "value", "");
        registerColonTemplate("input:reset", "input", "type", "reset", "value", "");
        registerColonTemplate("input:button", "input", "type", "button", "value", "");
        registerColonTemplate("input:b", "input", "type", "button", "value", "");
        registerColonTemplate("input:file", "input", "type", "file", "name", "", "id", "");
        registerColonTemplate("input:f", "input", "type", "file", "name", "", "id", "");
        registerColonTemplate("input:hidden", "input", "type", "hidden", "name", "");
        registerColonTemplate("input:h", "input", "type", "hidden", "name", "");
        registerColonTemplate("input:image", "input", "type", "image", "src", "", "alt", "");
        registerColonTemplate("input:i", "input", "type", "image", "src", "", "alt", "");
        registerColonTemplate("input:email", "input", "type", "email", "name", "", "id", "");
        registerColonTemplate("input:number", "input", "type", "number", "name", "", "id", "");
        registerColonTemplate("input:date", "input", "type", "date", "name", "", "id", "");
        registerColonTemplate("input:datetime", "input", "type", "datetime-local", "name", "", "id", "");
        registerColonTemplate("input:time", "input", "type", "time", "name", "", "id", "");
        registerColonTemplate("input:month", "input", "type", "month", "name", "", "id", "");
        registerColonTemplate("input:week", "input", "type", "week", "name", "", "id", "");
        registerColonTemplate("input:tel", "input", "type", "tel", "name", "", "id", "");
        registerColonTemplate("input:url", "input", "type", "url", "name", "", "id", "");
        registerColonTemplate("input:search", "input", "type", "search", "name", "", "id", "");
        registerColonTemplate("input:color", "input", "type", "color", "name", "", "id", "");
        registerColonTemplate("input:range", "input", "type", "range", "name", "", "id", "");
        registerColonTemplate("link:css", "link", "rel", "stylesheet", "href", "style.css");
        registerColonTemplate("link:print", "link", "rel", "stylesheet", "href", "print.css", "media", "print");
        registerColonTemplate("link:favicon", "link", "rel", "shortcut icon", "href", "favicon.ico", "type", "image/x-icon");
        registerColonTemplate("link:touch", "link", "rel", "apple-touch-icon", "href", "favicon.png");
        registerColonTemplate("link:rss", "link", "rel", "alternate", "type", "application/rss+xml", "title", "RSS", "href", "rss.xml");
        registerColonTemplate("link:atom", "link", "rel", "alternate", "type", "application/atom+xml", "title", "Atom", "href", "atom.xml");
        registerColonTemplate("meta:vp", "meta", "name", "viewport", "content", "width=device-width, initial-scale=1.0");
        registerColonTemplate("meta:utf", "meta", "charset", "UTF-8");
        registerColonTemplate("meta:compat", "meta", "http-equiv", "X-UA-Compatible", "content", "IE=edge");
        registerColonTemplate("script:src", "script", "src", "");
        registerColonTemplate("form:get", "form", "action", "", "method", "get");
        registerColonTemplate("form:post", "form", "action", "", "method", "post");
        registerColonTemplate("a:link", "a", "href", "http://");
        registerColonTemplate("a:mail", "a", "href", "mailto:");
        registerColonTemplate("a:tel", "a", "href", "tel:+");
        registerColonTemplate("btn:s", "button", "type", "submit");
        registerColonTemplate("btn:r", "button", "type", "reset");
        registerColonTemplate("btn:b", "button", "type", "button");
    }

    private static void registerColonTemplate(String alias, String tag, String... keyValues) {
        Map<String, String> attrs = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            attrs.put(keyValues[i], keyValues[i + 1]);
        }
        COLON_TAG_TEMPLATES.put(alias, new TagTemplate(tag, attrs));
    }

    private static class TagTemplate {
        final String tag;
        final Map<String, String> defaultAttrs;

        TagTemplate(String tag, Map<String, String> defaultAttrs) {
            this.tag = tag;
            this.defaultAttrs = defaultAttrs;
        }
    }

    private static final String[] FALLBACK_LOREM = {
            "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit", "sed", "do",
            "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore", "magna", "aliqua", "enim",
            "ad", "minim", "veniam", "quis", "nostrud", "exercitation", "ullamco", "laboris", "nisi", "ut",
            "aliquip", "ex", "ea", "commodo", "consequat", "duis", "aute", "irure", "dolor", "in",
            "reprehenderit", "in", "voluptate", "velit", "esse", "cillum", "dolore", "eu", "fugiat", "nulla",
            "pariatur", "excepteur", "sint", "occaecat", "cupidatat", "non", "proident", "sunt", "in", "culpa",
            "qui", "officia", "deserunt", "mollit", "anim", "id", "est", "laborum"
    };

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (lock) {
            if (loaded) return;
            loadFromAssets();
            loaded = true;
        }
    }

    private static void loadFromAssets() {
        String jsonStr = StaticAssetReader.readAsset("completions/emmet_definitions.json");
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(jsonStr);
            if (root.has("htmlBoilerplate")) {
                defaultBoilerplate = root.getString("htmlBoilerplate");
            }
            if (root.has("loremWords")) {
                JSONArray arr = root.getJSONArray("loremWords");
                String[] words = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    words[i] = arr.getString(i);
                }
                loremWords = words;
            }
            if (root.has("htmlAliases")) {
                JSONObject aliasesObj = root.getJSONObject("htmlAliases");
                Iterator<String> keys = aliasesObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    HTML_ALIASES.put(k, aliasesObj.getString(k));
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Returns true if the abbreviation matches a known HTML alias, colon shorthand, or snippet.
     */
    public static boolean isHtmlAlias(String abbr) {
        if (abbr == null || abbr.isEmpty()) return false;
        ensureLoaded();
        return HTML_SNIPPET_ALIASES.containsKey(abbr)
                || HTML_ALIASES.containsKey(abbr)
                || COLON_TAG_TEMPLATES.containsKey(abbr);
    }

    /**
     * Expands an HTML Emmet abbreviation. Returns null if the abbreviation is invalid.
     */
    public static String expandHtml(String abbr, String boilerplate) {
        if (abbr == null || abbr.trim().isEmpty()) return null;
        abbr = abbr.trim();

        if (abbr.equals("!")) {
            if (boilerplate != null && !boilerplate.isEmpty()) return boilerplate;
            return getDefaultBoilerplate();
        }

        if (!PAT_ABBR.matcher(abbr).matches()) return null;

        // Check exact snippet aliases (e.g. "ul+", "table+", "select+")
        if (HTML_SNIPPET_ALIASES.containsKey(abbr)) {
            return HTML_SNIPPET_ALIASES.get(abbr);
        }

        if (abbr.startsWith("lorem")) {
            if (abbr.equals("lorem")) {
                return generateLorem(30);
            }
            try {
                int count = Integer.parseInt(abbr.substring(5));
                return generateLorem(count);
            } catch (NumberFormatException ignored) {
            }
        }

        ensureLoaded();

        // Exact match for static alias without tree operators
        if (HTML_ALIASES.containsKey(abbr) && !hasTreeOperators(abbr)) {
            return HTML_ALIASES.get(abbr);
        }

        try {
            String result = parseEmmetTree(abbr);
            if (result == null) return null;

            // If no cursor marker exists, place it inside the first empty attribute or empty tag
            if (!result.contains("|")) {
                int firstEmptyAttr = result.indexOf("=\"\"");
                if (firstEmptyAttr != -1) {
                    result = result.substring(0, firstEmptyAttr + 2) + "|" + result.substring(firstEmptyAttr + 2);
                } else {
                    int firstClose = result.indexOf("></");
                    if (firstClose != -1) {
                        result = result.substring(0, firstClose + 1) + "|" + result.substring(firstClose + 1);
                    } else {
                        result = result + "|";
                    }
                }
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasTreeOperators(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '>' || c == '+' || c == '^' || c == '*' || c == '(' || c == '[' || c == '{') {
                return true;
            }
        }
        return false;
    }

    private static String getDefaultBoilerplate() {
        ensureLoaded();
        if (defaultBoilerplate != null) return defaultBoilerplate;
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Document</title>\n</head>\n<body>\n    |\n</body>\n</html>";
    }

    private static String generateLorem(int count) {
        if (count <= 0) return "";
        ensureLoaded();
        String[] words = (loremWords != null && loremWords.length > 0) ? loremWords : FALLBACK_LOREM;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String word = words[i % words.length];
            if (i == 0) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            } else {
                sb.append(word);
            }
            if (i < count - 1) {
                if (i > 0 && i % 8 == 0) {
                    sb.append(". ");
                } else {
                    sb.append(" ");
                }
            } else {
                sb.append(".");
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // CSS Emmet Expander
    // =========================================================================

    /**
     * Expands a CSS Emmet abbreviation. Returns null if not recognized.
     */
    public static String expandCss(String abbr) {
        if (abbr == null || abbr.isEmpty()) return null;
        abbr = abbr.trim();

        boolean important = false;
        if (abbr.endsWith("!")) {
            important = true;
            abbr = abbr.substring(0, abbr.length() - 1);
            if (abbr.isEmpty()) return null;
        }

        // 1. Hex color shorthands: c#f, c#333, bg#000, bd#f00
        Matcher hexMatch = PAT_HEX_COLOR.matcher(abbr);
        if (hexMatch.matches()) {
            String propAbbr = hexMatch.group(1);
            String hexDigits = hexMatch.group(2);
            String property;
            String prefix = "";
            switch (Objects.requireNonNull(propAbbr)) {
                case "c":
                case "col":
                    property = "color";
                    break;
                case "bg":
                    property = "background";
                    break;
                case "bd":
                    property = "border";
                    prefix = "1px solid ";
                    break;
                case "bdc":
                    property = "border-color";
                    break;
                default:
                    property = "color";
                    break;
            }

            String hexVal;
            if (hexDigits == null || hexDigits.isEmpty()) {
                hexVal = "#|";
            } else if (hexDigits.length() == 1) {
                hexVal = "#" + hexDigits + hexDigits + hexDigits;
            } else if (hexDigits.length() == 2) {
                hexVal = "#" + hexDigits + hexDigits + hexDigits;
            } else {
                hexVal = "#" + hexDigits;
            }

            String res = property + ": " + prefix + hexVal + ";";
            return applyImportant(res, important);
        }

        // 2. Check named abbreviations first (exact match)
        String named = EmmetCssDefinitions.CSS_ABBREVS.get(abbr);
        if (named != null) {
            return applyImportant(named, important);
        }

        // 3. Auto values with colon shorthand: m:a, mt:a, w:a, h:a
        if (abbr.endsWith(":a")) {
            String propKey = abbr.substring(0, abbr.length() - 2);
            String prop = EmmetCssDefinitions.CSS_PROP_MAP.get(propKey);
            if (prop != null) {
                return applyImportant(prop + ": auto;", important);
            }
        }

        // 4. Numeric property shorthand (e.g. m10, m-10, m-10--20, op0.5, lh1.5, w100p)
        Matcher m = PAT_CSS_NUMERIC.matcher(abbr);
        if (m.matches()) {
            String propAbbr = m.group(1);
            String numPart = m.group(2);
            String unitSuffix = m.group(3);

            String property = EmmetCssDefinitions.CSS_PROP_MAP.get(propAbbr);
            if (property == null) return null;

            String unit;
            switch (Objects.requireNonNull(unitSuffix)) {
                case "p":
                case "%":
                    unit = "%";
                    break;
                case "e":
                    unit = "em";
                    break;
                case "r":
                    unit = "rem";
                    break;
                case "x":
                    unit = "px";
                    break;
                case "vh":
                    unit = "vh";
                    break;
                case "vw":
                    unit = "vw";
                    break;
                case "vmin":
                    unit = "vmin";
                    break;
                case "vmax":
                    unit = "vmax";
                    break;
                case "pt":
                    unit = "pt";
                    break;
                case "s":
                    unit = "s";
                    break;
                case "ms":
                    unit = "ms";
                    break;
                default:
                    unit = unitSuffix.isEmpty() ? "px" : unitSuffix;
                    break;
            }

            StringBuilder value = parseCssNumericValues(numPart, property, unit);
            if (value.length() == 0) return null;
            String res = property + ": " + value + ";";
            return applyImportant(res, important);
        }

        return null;
    }

    private static String applyImportant(String cssDecl, boolean important) {
        if (!important) return cssDecl;
        if (cssDecl.endsWith(";")) {
            return cssDecl.substring(0, cssDecl.length() - 1) + " !important;";
        }
        return cssDecl + " !important;";
    }

    @NonNull
    private static StringBuilder parseCssNumericValues(String numPart, String property, String unit) {
        StringBuilder value = new StringBuilder();
        if (numPart == null || numPart.isEmpty()) return value;

        List<String> values = new ArrayList<>();
        int p = 0;
        int pLen = numPart.length();

        while (p < pLen) {
            boolean negative = false;
            if (numPart.charAt(p) == '-') {
                negative = true;
                p++;
            }
            int valStart = p;
            while (p < pLen && (Character.isDigit(numPart.charAt(p)) || numPart.charAt(p) == '.')) {
                p++;
            }
            if (p > valStart) {
                String val = (negative ? "-" : "") + numPart.substring(valStart, p);
                values.add(val);
            }
            // Consume separator '-' between values
            if (p < pLen && numPart.charAt(p) == '-') {
                p++;
            }
        }

        boolean isUnitless = property.equals("z-index") || property.equals("opacity")
                || property.equals("font-weight") || property.equals("line-height")
                || property.equals("flex") || property.equals("flex-grow")
                || property.equals("flex-shrink") || property.equals("order");

        for (int i = 0; i < values.size(); i++) {
            String val = values.get(i);
            if (val.equals("0") || val.equals("-0")) {
                value.append("0");
            } else if (isUnitless) {
                value.append(val);
            } else {
                value.append(val).append(unit);
            }
            if (i < values.size() - 1) {
                value.append(" ");
            }
        }
        return value;
    }

    // =========================================================================
    // HTML AST Parser & Tree Expansion
    // =========================================================================

    public static class EmmetNode {
        String tag;
        String id;
        List<String> classes = new ArrayList<>();
        Map<String, String> attributes = new LinkedHashMap<>();
        String textContent;
        boolean isTextOnly = false;
        List<EmmetNode> children = new ArrayList<>();
        EmmetNode parent;

        EmmetNode(String tag) {
            this.tag = tag;
        }

        public EmmetNode deepClone() {
            EmmetNode clone = new EmmetNode(this.tag);
            clone.id = this.id;
            clone.classes = new ArrayList<>(this.classes);
            clone.attributes = new LinkedHashMap<>(this.attributes);
            clone.textContent = this.textContent;
            clone.isTextOnly = this.isTextOnly;
            for (EmmetNode child : this.children) {
                EmmetNode childClone = child.deepClone();
                clone.addChild(childClone);
            }
            return clone;
        }

        public void addChild(EmmetNode child) {
            child.parent = this;
            this.children.add(child);
        }
    }

    private static String parseEmmetTree(String abbr) {
        List<EmmetNode> roots = parseSubTree(abbr, null);
        if (roots == null || roots.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < roots.size(); r++) {
            renderNode(roots.get(r), sb, 0, r == roots.size() - 1);
        }
        return sb.toString();
    }

    private static List<EmmetNode> parseSubTree(String abbr, String contextParentTag) {
        List<EmmetNode> roots = new ArrayList<>();
        List<EmmetNode> currentLeaves = new ArrayList<>();
        int i = 0;
        int len = abbr.length();

        char pendingOp = 0;
        int climbCount = 0;

        while (i < len) {
            char c = abbr.charAt(i);

            // Operators: >, +, ^
            if (c == '>' || c == '+') {
                pendingOp = c;
                i++;
                if (i >= len) return null;
                continue;
            } else if (c == '^') {
                climbCount = 0;
                while (i < len && abbr.charAt(i) == '^') {
                    climbCount++;
                    i++;
                }
                pendingOp = '^';
                if (i >= len) return null;
                continue;
            }

            // Grouping: (...)
            if (c == '(') {
                int closeIdx = findMatchingParen(abbr, i);
                if (closeIdx < 0) return null;
                String groupAbbr = abbr.substring(i + 1, closeIdx);
                i = closeIdx + 1;

                int mult = 1;
                if (i < len && abbr.charAt(i) == '*') {
                    i++;
                    int numStart = i;
                    while (i < len && Character.isDigit(abbr.charAt(i))) i++;
                    if (i > numStart) {
                        mult = Math.min(Integer.parseInt(abbr.substring(numStart, i)), 100);
                    }
                }

                String currentParentTag = determineCurrentParentTag(currentLeaves, pendingOp, climbCount, contextParentTag);
                List<EmmetNode> groupInstances = new ArrayList<>();

                for (int m = 0; m < mult; m++) {
                    String groupInstanceAbbr = (mult > 1 && groupAbbr.contains("$"))
                            ? replaceNumbering(groupAbbr, m + 1, mult)
                            : groupAbbr;

                    List<EmmetNode> groupRoots = parseSubTree(groupInstanceAbbr, currentParentTag);
                    if (groupRoots == null || groupRoots.isEmpty()) return null;

                    if (mult > 1 && !groupAbbr.contains("$")) {
                        applyNumberingToTree(groupRoots, m + 1, mult);
                    }
                    groupInstances.addAll(groupRoots);
                }

                currentLeaves = attachNodes(roots, currentLeaves, groupInstances, pendingOp, climbCount);
                pendingOp = 0;
                climbCount = 0;
                continue;
            }

            // Element token: tag#id.class[attr]{text}*n
            int tokenStart = i;
            i = extractToken(abbr, i);
            if (i == tokenStart) return null;
            String token = abbr.substring(tokenStart, i);

            String currentParentTag = determineCurrentParentTag(currentLeaves, pendingOp, climbCount, contextParentTag);
            List<EmmetNode> nodes = parseElementToken(token, currentParentTag);
            if (nodes == null || nodes.isEmpty()) return null;

            currentLeaves = attachNodes(roots, currentLeaves, nodes, pendingOp, climbCount);
            pendingOp = 0;
            climbCount = 0;
        }

        return roots;
    }

    private static String determineCurrentParentTag(List<EmmetNode> currentLeaves, char op, int climbCount, String fallbackTag) {
        if (currentLeaves.isEmpty()) return fallbackTag;
        if (op == '>') {
            return currentLeaves.get(0).tag;
        } else if (op == '+') {
            EmmetNode parent = currentLeaves.get(0).parent;
            return parent != null ? parent.tag : fallbackTag;
        } else if (op == '^') {
            EmmetNode target = currentLeaves.get(0).parent;
            for (int k = 0; k < climbCount && target != null; k++) {
                target = target.parent;
            }
            return target != null ? target.tag : fallbackTag;
        }
        return fallbackTag;
    }

    private static List<EmmetNode> attachNodes(List<EmmetNode> roots, List<EmmetNode> currentLeaves,
                                               List<EmmetNode> newNodes, char op, int climbCount) {
        List<EmmetNode> newLeaves = new ArrayList<>();

        if (op == '>') {
            if (currentLeaves.isEmpty()) {
                roots.addAll(newNodes);
                newLeaves.addAll(newNodes);
            } else {
                for (EmmetNode leaf : currentLeaves) {
                    for (EmmetNode node : newNodes) {
                        EmmetNode clone = node.deepClone();
                        leaf.addChild(clone);
                        newLeaves.addAll(collectLeaves(clone));
                    }
                }
            }
        } else if (op == '+') {
            if (currentLeaves.isEmpty()) {
                roots.addAll(newNodes);
                newLeaves.addAll(newNodes);
            } else {
                for (EmmetNode leaf : currentLeaves) {
                    EmmetNode parent = leaf.parent;
                    if (parent != null) {
                        for (EmmetNode node : newNodes) {
                            EmmetNode clone = node.deepClone();
                            parent.addChild(clone);
                            newLeaves.addAll(collectLeaves(clone));
                        }
                    } else {
                        for (EmmetNode node : newNodes) {
                            EmmetNode clone = node.deepClone();
                            roots.add(clone);
                            newLeaves.addAll(collectLeaves(clone));
                        }
                    }
                }
            }
        } else if (op == '^') {
            if (currentLeaves.isEmpty()) {
                roots.addAll(newNodes);
                newLeaves.addAll(newNodes);
            } else {
                EmmetNode target = currentLeaves.get(0).parent;
                for (int k = 0; k < climbCount && target != null; k++) {
                    target = target.parent;
                }
                if (target != null) {
                    for (EmmetNode node : newNodes) {
                        EmmetNode clone = node.deepClone();
                        target.addChild(clone);
                        newLeaves.addAll(collectLeaves(clone));
                    }
                } else {
                    for (EmmetNode node : newNodes) {
                        EmmetNode clone = node.deepClone();
                        roots.add(clone);
                        newLeaves.addAll(collectLeaves(clone));
                    }
                }
            }
        } else {
            // First element or root element
            roots.addAll(newNodes);
            for (EmmetNode node : newNodes) {
                newLeaves.addAll(collectLeaves(node));
            }
        }

        return newLeaves;
    }

    private static List<EmmetNode> collectLeaves(EmmetNode node) {
        List<EmmetNode> leaves = new ArrayList<>();
        if (node.children.isEmpty()) {
            leaves.add(node);
        } else {
            for (EmmetNode child : node.children) {
                leaves.addAll(collectLeaves(child));
            }
        }
        return leaves;
    }

    private static void applyNumberingToTree(List<EmmetNode> tree, int index1Based, int totalCount) {
        for (EmmetNode node : tree) {
            if (node.id != null) node.id = replaceNumbering(node.id, index1Based, totalCount);
            for (int i = 0; i < node.classes.size(); i++) {
                node.classes.set(i, replaceNumbering(node.classes.get(i), index1Based, totalCount));
            }
            if (!node.attributes.isEmpty()) {
                Map<String, String> updatedAttrs = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : node.attributes.entrySet()) {
                    String k = replaceNumbering(entry.getKey(), index1Based, totalCount);
                    String v = entry.getValue() != null
                            ? replaceNumbering(entry.getValue(), index1Based, totalCount)
                            : null;
                    updatedAttrs.put(k, v);
                }
                node.attributes = updatedAttrs;
            }
            if (node.textContent != null) {
                node.textContent = replaceNumbering(node.textContent, index1Based, totalCount);
            }
            applyNumberingToTree(node.children, index1Based, totalCount);
        }
    }

    private static int extractToken(String abbr, int start) {
        int i = start;
        int len = abbr.length();
        while (i < len) {
            char c = abbr.charAt(i);
            if (c == '>' || c == '+' || c == '^' || c == '(') break;
            if (c == '[') {
                int close = findMatchingBracket(abbr, i, '[', ']');
                if (close < 0) return len;
                i = close + 1;
            } else if (c == '{') {
                int close = findMatchingBracket(abbr, i, '{', '}');
                if (close < 0) return len;
                i = close + 1;
            } else {
                i++;
            }
        }
        return i;
    }

    private static int findMatchingBracket(String s, int openIdx, char openChar, char closeChar) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == openChar) depth++;
            else if (c == closeChar) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int findMatchingParen(String s, int openIdx) {
        return findMatchingBracket(s, openIdx, '(', ')');
    }

    // =========================================================================
    // Element Token Parsing
    // =========================================================================

    private static List<EmmetNode> parseElementToken(String token, String parentTag) {
        if (token == null || token.isEmpty()) return null;

        // 1. Check multiplication at the end (*N)
        int mult = 1;
        int lastMultIdx = -1;
        int depth = 0;
        for (int k = token.length() - 1; k >= 0; k--) {
            char ch = token.charAt(k);
            if (ch == ']' || ch == '}') depth++;
            else if (ch == '[' || ch == '{') depth--;
            else if (depth == 0 && ch == '*') {
                lastMultIdx = k;
                break;
            }
        }

        if (lastMultIdx != -1) {
            String multStr = token.substring(lastMultIdx + 1);
            try {
                mult = Math.min(Integer.parseInt(multStr), 100);
                token = token.substring(0, lastMultIdx);
            } catch (NumberFormatException ignored) {
            }
        }

        // 2. Pure text node: {Click me}
        if (token.startsWith("{") && token.endsWith("}")) {
            String text = token.substring(1, token.length() - 1);
            List<EmmetNode> nodes = new ArrayList<>(mult);
            for (int m = 0; m < mult; m++) {
                EmmetNode n = new EmmetNode("");
                n.isTextOnly = true;
                n.textContent = replaceNumbering(text, m + 1, mult);
                nodes.add(n);
            }
            return nodes;
        }

        // 3. Extract text content {text} attached to an element
        String textContent = null;
        int textOpen = -1;
        depth = 0;
        for (int k = 0; k < token.length(); k++) {
            char ch = token.charAt(k);
            if (ch == '[') depth++;
            else if (ch == ']') depth--;
            else if (depth == 0 && ch == '{') {
                textOpen = k;
                break;
            }
        }

        if (textOpen != -1) {
            int textClose = findMatchingBracket(token, textOpen, '{', '}');
            if (textClose != -1) {
                textContent = token.substring(textOpen + 1, textClose);
                token = token.substring(0, textOpen) + token.substring(textClose + 1);
            }
        }

        // 4. Extract attributes [attr=val][attr2="val2"]
        Map<String, String> customAttrs = new LinkedHashMap<>();
        StringBuilder tokenWithoutAttrs = new StringBuilder();
        int p = 0;
        int tLen = token.length();
        while (p < tLen) {
            char ch = token.charAt(p);
            if (ch == '[') {
                int bClose = findMatchingBracket(token, p, '[', ']');
                if (bClose != -1) {
                    String attrBlock = token.substring(p + 1, bClose);
                    parseAttributes(attrBlock, customAttrs);
                    p = bClose + 1;
                    continue;
                }
            }
            tokenWithoutAttrs.append(ch);
            p++;
        }

        String remaining = tokenWithoutAttrs.toString();

        // 5. Parse tag, #id, .class1.class2
        String tagOrAlias = "";
        String id = null;
        List<String> classes = new ArrayList<>();

        int cur = 0;
        int rLen = remaining.length();

        // Find where ID or classes start
        int firstDelim = -1;
        for (int k = 0; k < rLen; k++) {
            char ch = remaining.charAt(k);
            if (ch == '#' || ch == '.') {
                firstDelim = k;
                break;
            }
        }

        if (firstDelim == -1) {
            tagOrAlias = remaining;
        } else {
            tagOrAlias = remaining.substring(0, firstDelim);
            cur = firstDelim;

            while (cur < rLen) {
                char prefix = remaining.charAt(cur);
                cur++;
                int nextDelim = cur;
                while (nextDelim < rLen && remaining.charAt(nextDelim) != '#' && remaining.charAt(nextDelim) != '.') {
                    nextDelim++;
                }
                String part = remaining.substring(cur, nextDelim);
                if (prefix == '#') {
                    if (!part.isEmpty()) id = part;
                } else if (prefix == '.') {
                    if (!part.isEmpty()) classes.add(part);
                }
                cur = nextDelim;
            }
        }

        // Resolve tag and base attributes
        String finalTag;
        Map<String, String> baseAttrs = new LinkedHashMap<>();

        if (COLON_TAG_TEMPLATES.containsKey(tagOrAlias)) {
            TagTemplate tmpl = COLON_TAG_TEMPLATES.get(tagOrAlias);
            if (tmpl != null) {
                finalTag = tmpl.tag;
                baseAttrs.putAll(tmpl.defaultAttrs);
            } else {
                finalTag = tagOrAlias;
            }
        } else if (tagOrAlias.isEmpty()) {
            finalTag = getDefaultTagForParent(parentTag);
        } else {
            finalTag = tagOrAlias;
            Map<String, String> defaults = DEFAULT_TAG_ATTRS.get(finalTag);
            if (defaults != null) {
                baseAttrs.putAll(defaults);
            }
        }

        // Overlay user custom attributes on top of base defaults
        baseAttrs.putAll(customAttrs);

        List<EmmetNode> nodes = new ArrayList<>(mult);
        for (int m = 0; m < mult; m++) {
            EmmetNode n = new EmmetNode(finalTag);
            if (id != null) {
                n.id = replaceNumbering(id, m + 1, mult);
            }
            for (String cls : classes) {
                n.classes.add(replaceNumbering(cls, m + 1, mult));
            }
            for (Map.Entry<String, String> entry : baseAttrs.entrySet()) {
                String k = replaceNumbering(entry.getKey(), m + 1, mult);
                String v = entry.getValue() != null
                        ? replaceNumbering(entry.getValue(), m + 1, mult)
                        : null;
                n.attributes.put(k, v);
            }
            if (textContent != null) {
                n.textContent = replaceNumbering(textContent, m + 1, mult);
            }
            nodes.add(n);
        }

        return nodes;
    }

    private static void parseAttributes(String attrStr, Map<String, String> outAttrs) {
        if (attrStr == null || attrStr.trim().isEmpty()) return;
        int i = 0;
        int len = attrStr.length();
        while (i < len) {
            while (i < len && (Character.isWhitespace(attrStr.charAt(i)) || attrStr.charAt(i) == ',')) i++;
            if (i >= len) break;

            int nameStart = i;
            while (i < len && attrStr.charAt(i) != '=' && !Character.isWhitespace(attrStr.charAt(i))
                    && attrStr.charAt(i) != ',' && attrStr.charAt(i) != ']') {
                i++;
            }
            if (i == nameStart) {
                i++;
                continue;
            }
            String name = attrStr.substring(nameStart, i).trim();
            if (name.isEmpty()) continue;

            while (i < len && Character.isWhitespace(attrStr.charAt(i))) i++;

            if (i < len && attrStr.charAt(i) == '=') {
                i++;
                while (i < len && Character.isWhitespace(attrStr.charAt(i))) i++;
                if (i < len && (attrStr.charAt(i) == '"' || attrStr.charAt(i) == '\'')) {
                    char quote = attrStr.charAt(i);
                    i++;
                    int valStart = i;
                    while (i < len && attrStr.charAt(i) != quote) {
                        if (attrStr.charAt(i) == '\\' && i + 1 < len) i++;
                        i++;
                    }
                    String val = attrStr.substring(valStart, i);
                    if (i < len && attrStr.charAt(i) == quote) i++;
                    outAttrs.put(name, val);
                } else {
                    int valStart = i;
                    while (i < len && !Character.isWhitespace(attrStr.charAt(i))
                            && attrStr.charAt(i) != ',' && attrStr.charAt(i) != ']') {
                        i++;
                    }
                    String val = attrStr.substring(valStart, i);
                    outAttrs.put(name, val);
                }
            } else {
                outAttrs.put(name, null);
            }
        }
    }

    public static String getDefaultTagForParent(String parentTag) {
        if (parentTag == null) return "div";
        switch (parentTag.toLowerCase()) {
            case "ul":
            case "ol":
                return "li";
            case "table":
            case "tbody":
            case "thead":
            case "tfoot":
                return "tr";
            case "tr":
                return "td";
            case "select":
                return "option";
            case "dl":
                return "dt";
            case "map":
                return "area";
            case "colgroup":
                return "col";
            default:
                return "div";
        }
    }

    /**
     * Replaces item numbering placeholders $, $$, $$$, $@-, $@N with computed index values.
     */
    public static String replaceNumbering(String text, int index1Based, int totalCount) {
        if (text == null || !text.contains("$")) return text;

        Matcher m = Pattern.compile("(\\$+)(?:@(-)?([0-9]+)?)?").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int width = m.group(1).length();
            boolean descending = m.group(2) != null;
            String baseStartStr = m.group(3);

            int val;
            if (descending) {
                int start = baseStartStr != null ? Integer.parseInt(baseStartStr) : totalCount;
                val = start - (index1Based - 1);
            } else {
                int start = baseStartStr != null ? Integer.parseInt(baseStartStr) : 1;
                val = start + (index1Based - 1);
            }

            String formatted;
            if (width > 1) {
                formatted = String.format("%0" + width + "d", Math.max(0, val));
            } else {
                formatted = String.valueOf(val);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(formatted));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // =========================================================================
    // HTML Rendering
    // =========================================================================

    private static void renderNode(EmmetNode node, StringBuilder sb, int indent, boolean isLast) {
        String ind = getIndent(indent);

        if (node.isTextOnly) {
            if (node.textContent != null) {
                sb.append(ind).append(node.textContent);
            }
            if (!isLast) sb.append("\n");
            return;
        }

        sb.append(ind).append("<").append(node.tag);

        if (node.id != null) {
            sb.append(" id=\"").append(node.id).append("\"");
        }

        if (node.classes != null && !node.classes.isEmpty()) {
            sb.append(" class=\"");
            for (int i = 0; i < node.classes.size(); i++) {
                sb.append(node.classes.get(i));
                if (i < node.classes.size() - 1) sb.append(" ");
            }
            sb.append("\"");
        }

        for (Map.Entry<String, String> entry : node.attributes.entrySet()) {
            String name = entry.getKey();
            if (name.equals("id") && node.id != null) continue;
            if (name.equals("class") && node.classes != null && !node.classes.isEmpty()) continue;

            String val = entry.getValue();
            if (val == null) {
                sb.append(" ").append(name);
            } else {
                sb.append(" ").append(name).append("=\"").append(val).append("\"");
            }
        }

        boolean isVoid = isVoidElement(node.tag);
        sb.append(">");

        if (!isVoid) {
            if (node.textContent != null && node.children.isEmpty()) {
                sb.append(node.textContent).append("</").append(node.tag).append(">");
            } else if (node.children.isEmpty()) {
                sb.append("</").append(node.tag).append(">");
            } else {
                sb.append("\n");
                for (int i = 0; i < node.children.size(); i++) {
                    renderNode(node.children.get(i), sb, indent + 1, i == node.children.size() - 1);
                }
                sb.append(ind).append("</").append(node.tag).append(">");
            }
        }

        if (!isLast) sb.append("\n");
    }

    private static boolean isVoidElement(String tag) {
        return KnownElements.isVoidElement(tag);
    }

    private static String getIndent(int levels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels; i++) sb.append("    ");
        return sb.toString();
    }
}

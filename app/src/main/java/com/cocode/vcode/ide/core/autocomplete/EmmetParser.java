package com.cocode.vcode.ide.core.autocomplete;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;
import com.cocode.vcode.ide.core.language.css.EmmetCssDefinitions;
import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emmet abbreviation expander supporting HTML and CSS.
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
    private static final Pattern PAT_HEX_COLOR = Pattern.compile("^([a-zA-Z]+)#([0-9a-fA-F]*)$");

    // CSS Patterns
    private static final Pattern PAT_CSS_NUMERIC = Pattern.compile(
            "^([a-z]+)(-?[0-9]+(?:\\.[0-9]+)?(?:-+[0-9]+(?:\\.[0-9]+)?)*)([a-z%]*)$");

    // Shared Cache loaded dynamically from completions/emmet_definitions.json
    private static final Object lock = new Object();
    private static volatile boolean loaded = false;
    private static String defaultBoilerplate;
    private static String[] loremWords;
    private static final Map<String, String> HTML_ALIASES = new HashMap<>();
    private static final Map<String, String> HTML_SNIPPET_ALIASES = new HashMap<>();
    private static final Map<String, String> ELEMENT_ALIASES = new HashMap<>();
    private static final Map<String, Map<String, String>> DEFAULT_TAG_ATTRS = new HashMap<>();
    private static final Map<String, TagTemplate> COLON_TAG_TEMPLATES = new HashMap<>();
    private static final Map<String, String> CONTEXTUAL_PARENT_TAGS = new HashMap<>();
    private static final Set<String> CSS_UNITLESS_PROPERTIES = new HashSet<>();
    private static final Map<String, HexProperty> CSS_HEX_PROPERTIES = new HashMap<>();
    private static final Map<String, String> CSS_UNIT_ALIASES = new HashMap<>();

    static {
        ensureLoaded();
    }

    private static class TagTemplate {
        final String tag;
        final Map<String, String> defaultAttrs;

        TagTemplate(String tag, Map<String, String> defaultAttrs) {
            this.tag = tag;
            this.defaultAttrs = defaultAttrs;
        }
    }

    private static class HexProperty {
        final String property;
        final String prefix;

        HexProperty(String property, String prefix) {
            this.property = property;
            this.prefix = prefix != null ? prefix : "";
        }
    }

    public static void ensureLoaded() {
        if (loaded) return;
        synchronized (lock) {
            if (loaded) return;
            loadFromAssets();
            loaded = true;
        }
    }

    public static void resetForTest() {
        synchronized (lock) {
            loaded = false;
            defaultBoilerplate = null;
            loremWords = null;
            HTML_ALIASES.clear();
            HTML_SNIPPET_ALIASES.clear();
            ELEMENT_ALIASES.clear();
            DEFAULT_TAG_ATTRS.clear();
            COLON_TAG_TEMPLATES.clear();
            CONTEXTUAL_PARENT_TAGS.clear();
            CSS_UNITLESS_PROPERTIES.clear();
            CSS_HEX_PROPERTIES.clear();
            CSS_UNIT_ALIASES.clear();
            ensureLoaded();
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
            if (root.has("htmlSnippetAliases")) {
                JSONObject snipObj = root.getJSONObject("htmlSnippetAliases");
                Iterator<String> keys = snipObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    HTML_SNIPPET_ALIASES.put(k, snipObj.getString(k));
                }
            }
            if (root.has("elementAliases")) {
                JSONObject elemObj = root.getJSONObject("elementAliases");
                Iterator<String> keys = elemObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    ELEMENT_ALIASES.put(k, elemObj.getString(k));
                }
            }
            if (root.has("defaultTagAttributes")) {
                JSONObject defAttrsObj = root.getJSONObject("defaultTagAttributes");
                Iterator<String> tags = defAttrsObj.keys();
                while (tags.hasNext()) {
                    String tag = tags.next();
                    JSONObject attrsObj = defAttrsObj.getJSONObject(tag);
                    Map<String, String> attrs = new LinkedHashMap<>();
                    Iterator<String> attrKeys = attrsObj.keys();
                    while (attrKeys.hasNext()) {
                        String attrKey = attrKeys.next();
                        attrs.put(attrKey, attrsObj.getString(attrKey));
                    }
                    DEFAULT_TAG_ATTRS.put(tag, attrs);
                }
            }
            if (root.has("colonTagTemplates")) {
                JSONObject tmplObj = root.getJSONObject("colonTagTemplates");
                Iterator<String> keys = tmplObj.keys();
                while (keys.hasNext()) {
                    String alias = keys.next();
                    JSONObject item = tmplObj.getJSONObject(alias);
                    String tag = item.getString("tag");
                    Map<String, String> attrs = new LinkedHashMap<>();
                    if (item.has("attributes")) {
                        JSONObject attrsObj = item.getJSONObject("attributes");
                        Iterator<String> attrKeys = attrsObj.keys();
                        while (attrKeys.hasNext()) {
                            String attrKey = attrKeys.next();
                            attrs.put(attrKey, attrsObj.getString(attrKey));
                        }
                    }
                    COLON_TAG_TEMPLATES.put(alias, new TagTemplate(tag, attrs));
                }
            }
            if (root.has("contextualParentTags")) {
                JSONObject ctxObj = root.getJSONObject("contextualParentTags");
                Iterator<String> keys = ctxObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    CONTEXTUAL_PARENT_TAGS.put(k.toLowerCase(), ctxObj.getString(k));
                }
            }
            if (root.has("cssUnitlessProperties")) {
                JSONArray arr = root.getJSONArray("cssUnitlessProperties");
                for (int i = 0; i < arr.length(); i++) {
                    CSS_UNITLESS_PROPERTIES.add(arr.getString(i));
                }
            }
            if (root.has("cssHexProperties")) {
                JSONObject hexObj = root.getJSONObject("cssHexProperties");
                Iterator<String> keys = hexObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    JSONObject item = hexObj.getJSONObject(k);
                    CSS_HEX_PROPERTIES.put(k, new HexProperty(item.getString("property"), item.optString("prefix", "")));
                }
            }
            if (root.has("cssUnitAliases")) {
                JSONObject unitObj = root.getJSONObject("cssUnitAliases");
                Iterator<String> keys = unitObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    CSS_UNIT_ALIASES.put(k, unitObj.getString(k));
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
                || COLON_TAG_TEMPLATES.containsKey(abbr)
                || ELEMENT_ALIASES.containsKey(abbr);
    }

    private static volatile String defaultIndentUnit = "  ";

    private static final Set<String> STRUCTURAL_CONTAINERS = new HashSet<>(Arrays.asList(
            "div", "section", "article", "aside", "nav", "header", "footer", "main",
            "form", "fieldset", "figure", "details", "dialog", "table", "thead",
            "tbody", "tfoot", "tr", "ul", "ol", "dl", "head", "body", "html"
    ));

    public static void setDefaultIndentUnit(String indentUnit) {
        if (indentUnit != null && !indentUnit.isEmpty()) {
            defaultIndentUnit = indentUnit;
        }
    }

    public static String getDefaultIndentUnit() {
        return defaultIndentUnit;
    }

    public static boolean isStructuralContainer(String tag) {
        if (tag == null) return false;
        return STRUCTURAL_CONTAINERS.contains(tag.toLowerCase());
    }

    /**
     * Expands an HTML Emmet abbreviation using the default indentation unit.
     */
    public static String expandHtml(String abbr, String boilerplate) {
        return expandHtml(abbr, boilerplate, defaultIndentUnit);
    }

    /**
     * Expands an HTML Emmet abbreviation with a specified indentation unit.
     * Returns null if the abbreviation is invalid.
     */
    public static String expandHtml(String abbr, String boilerplate, String indentUnit) {
        if (abbr == null || abbr.trim().isEmpty()) return null;
        abbr = abbr.trim();
        if (indentUnit == null || indentUnit.isEmpty()) {
            indentUnit = defaultIndentUnit;
        }

        if (abbr.equals("!")) {
            String bp = (boilerplate != null && !boilerplate.isEmpty()) ? boilerplate : getDefaultBoilerplate();
            return formatSnippetIndent(bp, indentUnit);
        }

        if (!PAT_ABBR.matcher(abbr).matches()) return null;

        // Check exact snippet aliases (e.g. "ul+", "table+", "select+")
        if (HTML_SNIPPET_ALIASES.containsKey(abbr)) {
            return formatSnippetIndent(HTML_SNIPPET_ALIASES.get(abbr), indentUnit);
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
            return formatSnippetIndent(HTML_ALIASES.get(abbr), indentUnit);
        }

        try {
            String result = parseEmmetTree(abbr, indentUnit);
            if (result == null) return null;

            // Safety check: ensure result has a cursor marker
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

    private static String formatSnippetIndent(String snippet, String indentUnit) {
        if (snippet == null || indentUnit == null || indentUnit.equals("    ") || !snippet.contains("    ")) {
            return snippet;
        }
        return snippet.replace("        ", indentUnit + indentUnit)
                .replace("    ", indentUnit);
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
        return defaultBoilerplate != null ? defaultBoilerplate : "";
    }

    private static String generateLorem(int count) {
        if (count <= 0) return "";
        ensureLoaded();
        if (loremWords == null || loremWords.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String word = loremWords[i % loremWords.length];
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

        // Hex color shorthands (e.g. c#f, c#333, bg#000, bd#f00)
        Matcher hexMatch = PAT_HEX_COLOR.matcher(abbr);
        if (hexMatch.matches()) {
            ensureLoaded();
            String propAbbr = hexMatch.group(1);
            HexProperty hexProp = CSS_HEX_PROPERTIES.get(propAbbr);
            if (hexProp != null) {
                String hexDigits = hexMatch.group(2);
                String property = hexProp.property;
                String prefix = hexProp.prefix;

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
        }

        // Match exact named abbreviations
        String named = EmmetCssDefinitions.CSS_ABBREVS.get(abbr);
        if (named != null) {
            return applyImportant(named, important);
        }

        // Shorthands with auto values (e.g. ma, mta, wa, ha, za, m:a)
        if (abbr.endsWith("a") && abbr.length() > 1) {
            String propKey = abbr.substring(0, abbr.length() - 1);
            String prop = EmmetCssDefinitions.CSS_PROP_MAP.get(propKey);
            if (prop != null) {
                return applyImportant(prop + ": auto;", important);
            }
        }
        if (abbr.endsWith(":a")) {
            String propKey = abbr.substring(0, abbr.length() - 2);
            String prop = EmmetCssDefinitions.CSS_PROP_MAP.get(propKey);
            if (prop != null) {
                return applyImportant(prop + ": auto;", important);
            }
        }

        // Numeric property shorthands with units or decimals
        Matcher m = PAT_CSS_NUMERIC.matcher(abbr);
        if (m.matches()) {
            String propAbbr = m.group(1);
            String numPart = m.group(2);
            String unitSuffix = m.group(3);

            String property = EmmetCssDefinitions.CSS_PROP_MAP.get(propAbbr);
            if (property == null) return null;

            ensureLoaded();
            String unit = CSS_UNIT_ALIASES.get(unitSuffix);
            if (unit == null) {
                unit = (unitSuffix == null || unitSuffix.isEmpty()) ? "px" : unitSuffix;
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

        ensureLoaded();
        boolean isUnitless = CSS_UNITLESS_PROPERTIES.contains(property);

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
        boolean cursorInside = false;
        boolean cursorAfter = false;
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
            clone.cursorInside = this.cursorInside;
            clone.cursorAfter = this.cursorAfter;
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

    private static String parseEmmetTree(String abbr, String indentUnit) {
        List<EmmetNode> roots = parseSubTree(abbr, null);
        if (roots == null || roots.isEmpty()) return null;

        assignCursorMarker(roots);

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < roots.size(); r++) {
            renderNode(roots.get(r), sb, 0, r == roots.size() - 1, indentUnit);
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

        // Parse multiplication multiplier at the end (*N)
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

        // Standalone text node: {Click me}
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

        // Text content attached to an element: {text}
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

        // Element attribute expressions: [attr=val][attr2="val2"]
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

        // Parse tag name, ID, and class selectors
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

        ensureLoaded();
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
            String resolvedTag = ELEMENT_ALIASES.get(tagOrAlias);
            finalTag = resolvedTag != null ? resolvedTag : tagOrAlias;
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
        ensureLoaded();
        String tag = CONTEXTUAL_PARENT_TAGS.get(parentTag.toLowerCase());
        return tag != null ? tag : "div";
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

    private static boolean hasCursorMarker(List<EmmetNode> nodes) {
        if (nodes == null) return false;
        for (EmmetNode node : nodes) {
            if (node.cursorInside || node.cursorAfter) return true;
            if (node.textContent != null && node.textContent.contains("|")) return true;
            for (String val : node.attributes.values()) {
                if (val != null && val.contains("|")) return true;
            }
            if (hasCursorMarker(node.children)) return true;
        }
        return false;
    }

    private static EmmetNode findTargetLeaf(List<EmmetNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return null;
        EmmetNode chosen = null;
        for (EmmetNode n : nodes) {
            if (!n.isTextOnly) {
                chosen = n;
                break;
            }
        }
        if (chosen == null) {
            chosen = nodes.get(0);
        }
        if (chosen.children.isEmpty()) {
            return chosen;
        }
        return findTargetLeaf(chosen.children);
    }

    private static void assignCursorMarker(List<EmmetNode> roots) {
        if (roots == null || roots.isEmpty()) return;

        if (hasCursorMarker(roots)) return;

        // Find primary leaf node: prefer real elements over bare text nodes
        EmmetNode target = findTargetLeaf(roots);
        if (target == null) return;

        // If target is not a structural container, look for an empty attribute to place cursor
        if (!isStructuralContainer(target.tag)) {
            String targetAttr = null;
            if (target.attributes.containsKey("href") && "".equals(target.attributes.get("href"))) {
                targetAttr = "href";
            } else if (target.attributes.containsKey("src") && "".equals(target.attributes.get("src"))) {
                targetAttr = "src";
            } else if (target.attributes.containsKey("name") && "".equals(target.attributes.get("name"))) {
                targetAttr = "name";
            } else {
                for (Map.Entry<String, String> entry : target.attributes.entrySet()) {
                    if ("".equals(entry.getValue())) {
                        targetAttr = entry.getKey();
                        break;
                    }
                }
            }

            if (targetAttr != null) {
                target.attributes.put(targetAttr, "|");
                return;
            }
        }

        // Target has text content
        if (target.textContent != null) {
            if (target.textContent.isEmpty()) {
                target.textContent = "|";
            } else {
                target.cursorAfter = true;
            }
            return;
        }

        // Void element with no empty attributes
        if (isVoidElement(target.tag)) {
            target.cursorAfter = true;
            return;
        }

        // Container or leaf element
        target.cursorInside = true;
    }

    private static boolean shouldFormatInline(EmmetNode node) {
        if (node == null || node.children.isEmpty()) return false;
        if (isStructuralContainer(node.tag)) return false;

        for (EmmetNode child : node.children) {
            if (child.isTextOnly) continue;
            if (child.tag == null) return false;
            if (!KnownElements.isInlineElement(child.tag)) return false;
            if (!child.children.isEmpty() && !hasOnlyInlineChildren(child)) return false;
        }
        return true;
    }

    private static boolean hasOnlyInlineChildren(EmmetNode node) {
        if (node == null || node.children.isEmpty()) return false;
        for (EmmetNode child : node.children) {
            if (child.isTextOnly) continue;
            if (child.tag == null) return false;
            if (!KnownElements.isInlineElement(child.tag)) return false;
            if (!child.children.isEmpty() && !hasOnlyInlineChildren(child)) return false;
        }
        return true;
    }

    private static void renderNode(EmmetNode node, StringBuilder sb, int indent, boolean isLast, String indentUnit) {
        String ind = getIndent(indent, indentUnit);

        if (node.isTextOnly) {
            if (node.textContent != null) {
                sb.append(ind).append(node.textContent);
            }
            if (node.cursorAfter) sb.append("|");
            if (!isLast) sb.append("\n");
            return;
        }

        sb.append(ind).append("<").append(node.tag);
        renderAttributes(node, sb);

        boolean isVoid = isVoidElement(node.tag);
        sb.append(">");

        if (isVoid) {
            if (node.cursorAfter) sb.append("|");
            if (!isLast) sb.append("\n");
            return;
        }

        if (node.children.isEmpty()) {
            if (node.textContent != null) {
                sb.append(node.textContent).append("</").append(node.tag).append(">");
                if (node.cursorAfter) sb.append("|");
            } else if (node.cursorInside) {
                if (isStructuralContainer(node.tag)) {
                    sb.append("\n")
                            .append(ind).append(indentUnit).append("|\n")
                            .append(ind).append("</").append(node.tag).append(">");
                } else {
                    sb.append("|</").append(node.tag).append(">");
                }
            } else {
                if (isStructuralContainer(node.tag)) {
                    sb.append("\n").append(ind).append("</").append(node.tag).append(">");
                } else {
                    sb.append("</").append(node.tag).append(">");
                }
            }
        } else {
            if (shouldFormatInline(node)) {
                renderInlineChildren(node, sb);
                sb.append("</").append(node.tag).append(">");
                if (node.cursorAfter) sb.append("|");
            } else {
                sb.append("\n");
                for (int i = 0; i < node.children.size(); i++) {
                    renderNode(node.children.get(i), sb, indent + 1, false, indentUnit);
                }
                sb.append(ind).append("</").append(node.tag).append(">");
                if (node.cursorAfter) sb.append("|");
            }
        }

        if (!isLast) sb.append("\n");
    }

    private static void renderInlineChildren(EmmetNode node, StringBuilder sb) {
        for (EmmetNode child : node.children) {
            renderInlineNode(child, sb);
        }
    }

    private static void renderInlineNode(EmmetNode node, StringBuilder sb) {
        if (node.isTextOnly) {
            if (node.textContent != null) {
                sb.append(node.textContent);
            }
            if (node.cursorAfter) sb.append("|");
            return;
        }

        sb.append("<").append(node.tag);
        renderAttributes(node, sb);

        boolean isVoid = isVoidElement(node.tag);
        sb.append(">");

        if (isVoid) {
            if (node.cursorAfter) sb.append("|");
            return;
        }

        if (node.children.isEmpty()) {
            if (node.textContent != null) {
                sb.append(node.textContent);
            } else if (node.cursorInside) {
                sb.append("|");
            }
            sb.append("</").append(node.tag).append(">");
            if (node.cursorAfter) sb.append("|");
        } else {
            for (EmmetNode child : node.children) {
                renderInlineNode(child, sb);
            }
            sb.append("</").append(node.tag).append(">");
            if (node.cursorAfter) sb.append("|");
        }
    }

    private static void renderAttributes(EmmetNode node, StringBuilder sb) {
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
    }

    private static boolean isVoidElement(String tag) {
        return KnownElements.isVoidElement(tag);
    }

    private static String getIndent(int levels, String indentUnit) {
        if (levels <= 0) return "";
        if (indentUnit == null) indentUnit = defaultIndentUnit;
        if (levels == 1) return indentUnit;
        StringBuilder sb = new StringBuilder(levels * indentUnit.length());
        for (int i = 0; i < levels; i++) sb.append(indentUnit);
        return sb.toString();
    }
}

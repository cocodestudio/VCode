package com.cocode.vcode.ide.core.language.css;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.EmmetParser;
import com.cocode.vcode.ide.core.autocomplete.FastTrie;
import com.cocode.vcode.ide.core.autocomplete.PathAutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.completion.staticdata.CssStaticCompletionDispatcher;
import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contextual suggestion engine for CSS.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>{@code isInsideMediaQuery} is checked BEFORE the {@code @} rule check, so
 *       "@media (" triggers feature completions instead of at-rule completions.</li>
 *   <li>Pseudo-class items use {@code insertText} WITHOUT the leading {@code :} character.
 *       When user has already typed {@code p:}, inserting "hover" gives "p:hover" (not "p::hover").</li>
 *   <li>Pseudo-class detection checks if the char BEFORE the typed word is {@code :}
 *       so "p:hov" correctly filters to ":hover".</li>
 *   <li>The colon-preceded check also works for {@code ::} (pseudo-elements like {@code ::before}).</li>
 * </ul>
 */
public class CssAutoCompleteEngine extends AutoCompleteEngine {

    private static final String[] BORDER_STYLES = {
            "none", "hidden", "solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset"
    };
    private static final String[] BORDER_WIDTHS = {
            "thin", "medium", "thick", "1px", "2px", "3px", "4px"
    };
    private static final String[] BORDER_RADII = {
            "0", "2px", "4px", "6px", "8px", "12px", "16px", "24px", "50%", "9999px", "1rem", "0.5rem", "0.25rem"
    };
    private static final String[] SHADOW_PRESETS = {
            "none", "0 1px 2px rgba(0,0,0,0.05)", "0 1px 3px rgba(0,0,0,0.1)",
            "0 4px 6px -1px rgba(0,0,0,0.1)", "0 10px 15px -3px rgba(0,0,0,0.1)",
            "0 20px 25px -5px rgba(0,0,0,0.1)", "inset 0 2px 4px 0 rgba(0,0,0,0.06)"
    };
    // Instance state
    private final PathAutoCompleteEngine pathEngine;
    private final List<CompletionItem> propertyItems = new ArrayList<>();
    private final Map<String, List<String>> valueMap = new HashMap<>();
    private final List<CompletionItem> htmlTagItems = new ArrayList<>();
    private final List<CompletionItem> colorItems = new ArrayList<>();
    private final List<CompletionItem> globalValueItems = new ArrayList<>();
    private final List<CompletionItem> cachedCustomProps = new ArrayList<>();
    private final List<CompletionItem> cachedDocClasses = new ArrayList<>();
    private final List<CompletionItem> cachedDocIds = new ArrayList<>();
    private List<CompletionItem> scopedHtmlClasses = null;
    private List<CompletionItem> scopedHtmlIds = null;
    private final FastTrie propertyTrie = new FastTrie();
    private int lastTextHash = 0;
    private int lastDocClassTextHash = 0;

    public void setScopedHtmlSelectors(List<CompletionItem> scopedClasses, List<CompletionItem> scopedIds) {
        this.scopedHtmlClasses = scopedClasses != null ? new ArrayList<>(scopedClasses) : null;
        this.scopedHtmlIds = scopedIds != null ? new ArrayList<>(scopedIds) : null;
    }

    @Override
    public String getTriggerCharacters() {
        return ".#:@-(!&/\"'";
    }

    public CssAutoCompleteEngine() {
        this(null);
    }

    public CssAutoCompleteEngine(Context context) {
        super(context);
        this.pathEngine = new PathAutoCompleteEngine(context);
        loadProperties();
        loadHtmlTags();
        loadColors();
    }

    @Override
    public void setCurrentFile(File file) {
        super.setCurrentFile(file);
        if (pathEngine != null) {
            pathEngine.setCurrentFile(file);
        }
    }

    // Asset loading
    private void loadColors() {
        colorItems.clear();
        colorItems.addAll(com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getCssColorCompletionItems());
        globalValueItems.clear();
        globalValueItems.addAll(com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getCssGlobalFunctionItems());
    }

    private void loadHtmlTags() {
        htmlTagItems.clear();
        htmlTagItems.addAll(com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getCssHtmlTagCompletionItems());
    }

    private void loadProperties() {
        propertyItems.clear();
        propertyItems.addAll(com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getCssPropertyCompletionItems());
        for (CompletionItem item : propertyItems) {
            propertyTrie.insert(item);
        }
        valueMap.clear();
        valueMap.putAll(com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getCssPropertyValueMap());
    }

    // Custom property scanning
    private void ensureCustomPropsIndexed(String text) {
        int hash = text.hashCode();
        if (hash == lastTextHash) return;
        lastTextHash = hash;
        cachedCustomProps.clear();

        // Zero-regex scan for custom properties: --identifier:
        int len = Math.min(text.length(), 200_000);
        java.util.Set<String> seen = new java.util.HashSet<>();
        int i = 0;
        while (i < len - 2) {
            if (text.charAt(i) == '-' && text.charAt(i + 1) == '-') {
                int start = i;
                i += 2;
                while (i < len) {
                    char c = text.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                int end = i;
                while (i < len && Character.isWhitespace(text.charAt(i))) i++;
                if (i < len && text.charAt(i) == ':' && end > start + 2) {
                    String prop = text.substring(start, end);
                    if (seen.add(prop)) {
                        cachedCustomProps.add(new CompletionItem(prop, prop, "Custom property",
                                CompletionItem.Type.CSS_PROPERTY, 0));
                    }
                }
            } else {
                i++;
            }
        }
    }

    // Document class and ID scanning for real-time current buffer suggestions
    private void ensureDocumentClassesAndIdsIndexed(String text) {
        if (text == null) return;
        int hash = text.hashCode();
        if (hash == lastDocClassTextHash) return;
        lastDocClassTextHash = hash;
        cachedDocClasses.clear();
        cachedDocIds.clear();

        Set<String> classNames = new HashSet<>();
        Set<String> cssIds = new HashSet<>();
        ProjectSymbolIndex.extractSelectorsFromCssText(text, classNames, cssIds);

        for (String c : classNames) {
            cachedDocClasses.add(new CompletionItem("." + c, "." + c, "CSS Class", CompletionItem.Type.CSS_VALUE, 0));
        }
        for (String id : cssIds) {
            cachedDocIds.add(new CompletionItem("#" + id, "#" + id, "CSS ID", CompletionItem.Type.CSS_VALUE, 0));
        }
    }

    // Main entry points
    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        return getSuggestions(fullText, cursorPos, false);
    }

    public List<CompletionItem> getSuggestions(String fullText, int cursorPos, boolean isInlineStyle) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length())
            return Collections.emptyList();

        ensureCustomPropsIndexed(fullText);
        ensureDocumentClassesAndIdsIndexed(fullText);

        String line = getLineBeforeCursor(fullText, cursorPos);
        String trimmed = line.trim();
        String word = getWordBeforeCursor(fullText, cursorPos);

        // Delimiter suppression: directly after '{', '}', or '>' (combinator/tag closure)
        if (line.endsWith(">") || trimmed.endsWith("{") || trimmed.endsWith("}")) {
            return Collections.emptyList();
        }
        if (word.isEmpty()) {
            if (cursorPos > 0 && fullText.charAt(cursorPos - 1) == '(' && !isInsideVar(fullText, cursorPos)) {
                return Collections.emptyList();
            }
        }

        CssContextScanner.CssContext ctx = CssContextScanner.scan(fullText, cursorPos, isInlineStyle);
        if (ctx.isInsideComment) {
            return Collections.emptyList();
        }

        if (ctx.isInsideString) {
            if (isInsideCssImport(fullText, cursorPos) && pathEngine != null) {
                List<CompletionItem> pathSuggestions = pathEngine.getSuggestions(fullText, cursorPos);
                if (pathSuggestions != null && !pathSuggestions.isEmpty()) {
                    return pathSuggestions;
                }
            }
            return Collections.emptyList();
        }

        if (ctx.type == CssContextScanner.ContextType.URL_VALUE) {
            if (pathEngine != null) {
                List<CompletionItem> pathSuggestions = pathEngine.getSuggestions(fullText, cursorPos);
                if (pathSuggestions != null && !pathSuggestions.isEmpty()) {
                    return pathSuggestions;
                }
            }
            return Collections.emptyList();
        }

        if (ctx.type == CssContextScanner.ContextType.AT_RULE) {
            String atWord = word.startsWith("@") ? word : ("@" + word);
            List<CompletionItem> filtered = fuzzyFilter(CssDefinitions.AT_RULE_ITEMS, atWord);
            List<CompletionItem> out = new ArrayList<>(filtered.size());
            for (CompletionItem ci : filtered) {
                CompletionItem copy = new CompletionItem(ci);
                copy.setReplaceLength(atWord.length());
                out.add(copy);
            }
            return out;
        }

        if (ctx.type == CssContextScanner.ContextType.MEDIA_FEATURE) {
            return getMediaSuggestions(fullText, cursorPos, word);
        }

        if (ctx.type == CssContextScanner.ContextType.PROPERTY_VALUE) {
            String propName = ctx.propertyName != null && !ctx.propertyName.isEmpty()
                    ? ctx.propertyName
                    : extractPropertyBeforeColon(line);
            return getValueSuggestions(propName, word, fullText, cursorPos);
        }

        if (ctx.type == CssContextScanner.ContextType.PROPERTY_NAME) {
            if (word.startsWith("&")) {
                return getNestedSuggestions(word, trimmed, fullText, cursorPos);
            }
            return getPropertySuggestions(word, fullText, cursorPos);
        }

        // ContextType.SELECTOR
        int wordStartPos = cursorPos - word.length();
        boolean directlyAfterColon = wordStartPos > 0 && fullText.charAt(wordStartPos - 1) == ':';
        boolean cursorRightAfterColon = word.isEmpty() && cursorPos > 0
                && fullText.charAt(cursorPos - 1) == ':';
        boolean isPseudo = directlyAfterColon || cursorRightAfterColon || word.startsWith(":");

        boolean directlyAfterDot = wordStartPos > 0 && fullText.charAt(wordStartPos - 1) == '.';
        boolean cursorRightAfterDot = word.isEmpty() && cursorPos > 0
                && fullText.charAt(cursorPos - 1) == '.';
        boolean isClassSelector = directlyAfterDot || cursorRightAfterDot || word.startsWith(".");

        boolean directlyAfterHash = wordStartPos > 0 && fullText.charAt(wordStartPos - 1) == '#';
        boolean cursorRightAfterHash = word.isEmpty() && cursorPos > 0
                && fullText.charAt(cursorPos - 1) == '#';
        boolean isIdSelector = directlyAfterHash || cursorRightAfterHash || word.startsWith("#");

        if (isPseudo) {
            boolean hasTwoColons = (wordStartPos > 1 && fullText.charAt(wordStartPos - 1) == ':' && fullText.charAt(wordStartPos - 2) == ':')
                    || (word.isEmpty() && cursorPos > 1 && fullText.charAt(cursorPos - 1) == ':' && fullText.charAt(cursorPos - 2) == ':')
                    || word.startsWith("::");
            return filterPseudoSelectors(word, directlyAfterColon || cursorRightAfterColon, hasTwoColons);
        }

        if (isClassSelector) {
            String classQuery = word.startsWith(".") ? word : ("." + word);
            return filterClassSelectors(classQuery);
        }

        if (isIdSelector) {
            String idQuery = word.startsWith("#") ? word : ("#" + word);
            return filterIdSelectors(idQuery);
        }

        return getSelectorSuggestions(word, trimmed);
    }

    private List<CompletionItem> filterPseudoSelectors(String word, boolean hasPrecedingColon, boolean hasTwoColons) {
        String query = word.startsWith("::") ? word.substring(2) : (word.startsWith(":") ? word.substring(1) : word);
        List<CompletionItem> filtered = fuzzyFilter(CssDefinitions.PSEUDO_ITEMS, query);
        List<CompletionItem> out = new ArrayList<>(filtered.size());
        for (CompletionItem ci : filtered) {
            CompletionItem copy;
            if (hasTwoColons) {
                String rawName = ci.getLabel().replaceAll("^:+", "");
                copy = new CompletionItem(ci.getLabel(), rawName, ci.getDetail(), ci.getType(), ci.getCursorOffset());
            } else if (hasPrecedingColon) {
                copy = new CompletionItem(ci.getLabel(), ci.getEffectiveInsertText(), ci.getDetail(), ci.getType(), ci.getCursorOffset());
            } else if (word.startsWith(":")) {
                copy = new CompletionItem(ci.getLabel(), ci.getLabel(), ci.getDetail(), ci.getType(), ci.getCursorOffset());
            } else {
                copy = new CompletionItem(ci.getLabel(), ci.getLabel(), ci.getDetail(), ci.getType(), ci.getCursorOffset());
            }
            copy.setReplaceLength(word.length());
            out.add(copy);
        }
        return out;
    }

    /**
     * Within a declaration block, determines if cursor is in property-name or value zone.
     */
    private Zone detectDeclarationZone(String text, int cursorPos) {
        // Look back from cursor to find the start of the current declaration
        int i = cursorPos - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (c == ';' || c == '{' || c == '}') break;
            i--;
        }
        // Check if there's a colon in the current declaration segment
        String currentDecl = text.substring(i + 1, cursorPos);
        // Ignore colons inside url() or other function calls
        int colonIdx = -1;
        int parenDepth = 0;
        for (int j = 0; j < currentDecl.length(); j++) {
            char c = currentDecl.charAt(j);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == ':' && parenDepth == 0) {
                colonIdx = j;
                break;
            }
        }
        return colonIdx >= 0 ? Zone.VALUE : Zone.PROPERTY;
    }

    private String extractPropertyBeforeColon(String line) {
        // Walk backward from cursor on the line to find colon, ignoring function parens
        int blockStart = -1;
        for (int i = line.length() - 1; i >= 0; i--) {
            char c = line.charAt(i);
            if (c == ';' || c == '{') {
                blockStart = i;
                break;
            }
        }
        String decl = blockStart >= 0 ? line.substring(blockStart + 1) : line;
        int colon = -1;
        int parenDepth = 0;
        for (int i = 0; i < decl.length(); i++) {
            char c = decl.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == ':' && parenDepth == 0) {
                colon = i;
                break;
            }
        }
        return colon < 0 ? "" : decl.substring(0, colon).trim();
    }

    /**
     * Checks if the cursor is within an unclosed {@code var(...)} function call.
     */
    private boolean isInsideVar(String text, int cursorPos) {
        if (text == null || cursorPos <= 0) return false;
        int depth = 0;
        for (int i = cursorPos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ';' || c == '{' || c == '}') return false;
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                if (depth > 0) {
                    depth--;
                } else {
                    int j = i - 1;
                    while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
                    if (j >= 2 && text.charAt(j) == 'r' && text.charAt(j - 1) == 'a' && text.charAt(j - 2) == 'v') {
                        if (j == 2 || (!Character.isLetterOrDigit(text.charAt(j - 3)) && text.charAt(j - 3) != '_' && text.charAt(j - 3) != '-')) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the cursor is in the first argument of a {@code var(...)} call (before any fallback comma).
     */
    private boolean isInsideVarFirstArg(String text, int cursorPos) {
        if (text == null || cursorPos <= 0) return false;
        int depth = 0;
        int commaCount = 0;
        for (int i = cursorPos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ';' || c == '{' || c == '}') return false;
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                if (depth > 0) {
                    depth--;
                } else {
                    int j = i - 1;
                    while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
                    if (j >= 2 && text.charAt(j) == 'r' && text.charAt(j - 1) == 'a' && text.charAt(j - 2) == 'v') {
                        if (j == 2 || (!Character.isLetterOrDigit(text.charAt(j - 3)) && text.charAt(j - 3) != '_' && text.charAt(j - 3) != '-')) {
                            return commaCount == 0;
                        }
                    }
                    return false;
                }
            } else if (c == ',' && depth == 0) {
                commaCount++;
            }
        }
        return false;
    }

    private List<CompletionItem> getValueSuggestions(String propertyName, String word, String fullText, int cursorPos) {
        // First argument of var() only accepts custom properties
        if (isInsideVarFirstArg(fullText, cursorPos)) {
            List<CompletionItem> varItems = new ArrayList<>();
            for (CompletionItem ci : cachedCustomProps) {
                CompletionItem copy = new CompletionItem(ci);
                copy.setReplaceLength(word.length());
                varItems.add(copy);
            }
            return fuzzyFilter(varItems, word);
        }

        List<CompletionItem> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        // Custom properties or var() fallback value
        if (isInsideVar(fullText, cursorPos) || word.startsWith("--") || word.equals("var")) {
            for (CompletionItem ci : cachedCustomProps) {
                if (seen.add(ci.getLabel())) {
                    CompletionItem copy = new CompletionItem(ci);
                    copy.setReplaceLength(word.length());
                    items.add(copy);
                }
            }
        }

        // Property-specific enumerated values
        List<String> vals = valueMap.get(propertyName);
        if (vals != null) {
            for (String v : vals) {
                if (seen.add(v)) {
                    items.add(new CompletionItem(v, v, propertyName, CompletionItem.Type.CSS_VALUE, 0));
                }
            }
        }

        // Shorthand enrichments
        String p = propertyName != null ? propertyName.toLowerCase() : "";
        if (p.contains("radius")) {
            for (String r : BORDER_RADII) {
                if (seen.add(r))
                    items.add(new CompletionItem(r, r, "Radius", CompletionItem.Type.CSS_VALUE, 0));
            }
        } else if (p.endsWith("-style")) {
            for (String s : BORDER_STYLES) {
                if (seen.add(s))
                    items.add(new CompletionItem(s, s, "Border style", CompletionItem.Type.CSS_VALUE, 0));
            }
        } else if (p.endsWith("-width")) {
            for (String w : BORDER_WIDTHS) {
                if (seen.add(w))
                    items.add(new CompletionItem(w, w, "Border width", CompletionItem.Type.CSS_VALUE, 0));
            }
        } else if (KnownElements.isBorderShorthandProperty(p) || p.startsWith("outline") || p.startsWith("column-rule")) {
            for (String s : BORDER_STYLES) {
                if (seen.add(s))
                    items.add(new CompletionItem(s, s, "Stroke style", CompletionItem.Type.CSS_VALUE, 0));
            }
            for (String w : BORDER_WIDTHS) {
                if (seen.add(w))
                    items.add(new CompletionItem(w, w, "Stroke width", CompletionItem.Type.CSS_VALUE, 0));
            }
        } else if (p.endsWith("shadow")) {
            for (String sp : SHADOW_PRESETS) {
                if (seen.add(sp))
                    items.add(new CompletionItem(sp, sp, "Shadow preset", CompletionItem.Type.CSS_VALUE, 0));
            }
        }

        for (CompletionItem ci : globalValueItems) {
            if (seen.add(ci.getLabel())) items.add(ci);
        }

        if (isColorProperty(propertyName)) {
            for (CompletionItem ci : colorItems) {
                if (seen.add(ci.getLabel())) items.add(ci);
            }
        }

        List<CompletionItem> filtered = fuzzyFilter(items, word);
        List<CompletionItem> out = new ArrayList<>(filtered.size());
        for (CompletionItem ci : filtered) {
            CompletionItem copy = new CompletionItem(ci);
            copy.setReplaceLength(word.length());
            out.add(copy);
        }
        return out;
    }

    private List<CompletionItem> getPropertySuggestions(String word, String fullText, int cursorPos) {
        // Emmet CSS shorthand (e.g. "m10" → "margin: 10px;")
        String emmetAbbr = getEmmetAbbreviationBeforeCursor(fullText, cursorPos);
        String expanded = EmmetParser.expandCss(emmetAbbr);
        if (expanded != null) {
            List<CompletionItem> res = new ArrayList<>();
            CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded, "Emmet", CompletionItem.Type.SNIPPET, 0);
            emmetItem.setReplaceLength(emmetAbbr.length());
            res.add(emmetItem);
            return res;
        }

        List<CompletionItem> all = new ArrayList<>();
        List<CompletionItem> matches = com.cocode.vcode.ide.core.completion.staticdata.StaticCompletionLoader.getCssPropertyIndex().filter(word, MAX_SUGGESTIONS);
        if (!matches.isEmpty()) {
            for (CompletionItem ci : matches) {
                CompletionItem copy = new CompletionItem(ci);
                copy.setReplaceLength(word.length());
                all.add(copy);
            }
            List<CompletionItem> varMatches = fuzzyFilter(cachedCustomProps, word);
            for (CompletionItem ci : varMatches) {
                CompletionItem copy = new CompletionItem(ci);
                copy.setReplaceLength(word.length());
                all.add(copy);
            }
            return all;
        } else {
            List<CompletionItem> fallback = new ArrayList<>(propertyItems);
            fallback.addAll(cachedCustomProps);
            List<CompletionItem> filtered = fuzzyFilter(fallback, word);
            List<CompletionItem> outFallback = new ArrayList<>(filtered.size());
            for (CompletionItem ci : filtered) {
                CompletionItem copy = new CompletionItem(ci);
                copy.setReplaceLength(word.length());
                outFallback.add(copy);
            }
            return outFallback;
        }
    }

    // Zone-specific suggestion builders

    /**
     * Suggestions for nested selector context (inside a rule block with CSS nesting support).
     * Shows selectors (HTML tags, & prefix, class/id), properties, and Emmet at once inside nested rules.
     */
    private List<CompletionItem> getNestedSuggestions(String word, String trimmed, String fullText, int cursorPos) {
        // Emmet CSS shorthand takes priority
        String emmetAbbr = getEmmetAbbreviationBeforeCursor(fullText, cursorPos);
        String expanded = EmmetParser.expandCss(emmetAbbr);
        if (expanded != null) {
            List<CompletionItem> res = new ArrayList<>();
            CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded, "Emmet", CompletionItem.Type.SNIPPET, 0);
            emmetItem.setReplaceLength(emmetAbbr.length());
            res.add(emmetItem);
            return res;
        }

        List<CompletionItem> all = new ArrayList<>(propertyItems);
        all.addAll(cachedCustomProps);
        if (word.startsWith(".") || word.startsWith("#") || word.startsWith("&")) {
            addCrossFileSelectors(all);
        } else if (word.startsWith(":")) {
            all.addAll(CssDefinitions.PSEUDO_ITEMS);
        }
        List<CompletionItem> filtered = fuzzyFilter(all, word);
        List<CompletionItem> out = new ArrayList<>(filtered.size());
        for (CompletionItem ci : filtered) {
            CompletionItem copy = new CompletionItem(ci);
            copy.setReplaceLength(word.length());
            out.add(copy);
        }
        return out;
    }

    private List<CompletionItem> filterClassSelectors(String query) {
        List<CompletionItem> list = getClassSelectorItems();
        List<CompletionItem> filtered = fuzzyFilter(list, query);
        int replaceLen = query != null ? query.length() : 0;
        List<CompletionItem> out = new ArrayList<>(filtered.size());
        for (CompletionItem ci : filtered) {
            CompletionItem copy = new CompletionItem(ci);
            copy.setReplaceLength(replaceLen);
            out.add(copy);
        }
        return out;
    }

    private List<CompletionItem> filterIdSelectors(String query) {
        List<CompletionItem> list = getIdSelectorItems();
        List<CompletionItem> filtered = fuzzyFilter(list, query);
        int replaceLen = query != null ? query.length() : 0;
        List<CompletionItem> out = new ArrayList<>(filtered.size());
        for (CompletionItem ci : filtered) {
            CompletionItem copy = new CompletionItem(ci);
            copy.setReplaceLength(replaceLen);
            out.add(copy);
        }
        return out;
    }

    private List<CompletionItem> getClassSelectorItems() {
        if (scopedHtmlClasses != null) {
            List<CompletionItem> list = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (CompletionItem ci : scopedHtmlClasses) {
                String label = ci.getLabel().startsWith(".") ? ci.getLabel() : ("." + ci.getLabel());
                String insert = ci.getEffectiveInsertText().startsWith(".") ? ci.getEffectiveInsertText() : ("." + ci.getEffectiveInsertText());
                if (seen.add(label)) {
                    list.add(new CompletionItem(label, insert, ci.getDetail() != null ? ci.getDetail() : "CSS Class", CompletionItem.Type.CSS_VALUE, 0));
                }
            }
            return list;
        }

        List<CompletionItem> list = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (CompletionItem ci : cachedDocClasses) {
            if (seen.add(ci.getLabel())) {
                list.add(ci);
            }
        }
        List<CompletionItem> classes = ProjectSymbolIndex.getInstance().getCssClassItems();
        for (CompletionItem ci : classes) {
            String label = ci.getLabel().startsWith(".") ? ci.getLabel() : ("." + ci.getLabel());
            String insert = ci.getEffectiveInsertText().startsWith(".") ? ci.getEffectiveInsertText() : ("." + ci.getEffectiveInsertText());
            if (seen.add(label)) {
                list.add(new CompletionItem(label, insert, "CSS Class", CompletionItem.Type.CSS_VALUE, 0));
            }
        }
        return list;
    }

    private List<CompletionItem> getIdSelectorItems() {
        if (scopedHtmlIds != null) {
            List<CompletionItem> list = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (CompletionItem ci : scopedHtmlIds) {
                String label = ci.getLabel().startsWith("#") ? ci.getLabel() : ("#" + ci.getLabel());
                String insert = ci.getEffectiveInsertText().startsWith("#") ? ci.getEffectiveInsertText() : ("#" + ci.getEffectiveInsertText());
                if (seen.add(label)) {
                    list.add(new CompletionItem(label, insert, ci.getDetail() != null ? ci.getDetail() : "CSS ID", CompletionItem.Type.CSS_VALUE, 0));
                }
            }
            return list;
        }

        List<CompletionItem> list = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (CompletionItem ci : cachedDocIds) {
            if (seen.add(ci.getLabel())) {
                list.add(ci);
            }
        }
        List<CompletionItem> cssIds = ProjectSymbolIndex.getInstance().getCssIdItems();
        for (CompletionItem ci : cssIds) {
            String label = ci.getLabel().startsWith("#") ? ci.getLabel() : ("#" + ci.getLabel());
            String insert = ci.getEffectiveInsertText().startsWith("#") ? ci.getEffectiveInsertText() : ("#" + ci.getEffectiveInsertText());
            if (seen.add(label)) {
                list.add(new CompletionItem(label, insert, "CSS ID", CompletionItem.Type.CSS_VALUE, 0));
            }
        }
        List<CompletionItem> htmlIds = ProjectSymbolIndex.getInstance().getHtmlIdItems();
        for (CompletionItem ci : htmlIds) {
            String label = ci.getLabel().startsWith("#") ? ci.getLabel() : ("#" + ci.getLabel());
            String insert = ci.getEffectiveInsertText().startsWith("#") ? ci.getEffectiveInsertText() : ("#" + ci.getEffectiveInsertText());
            if (seen.add(label)) {
                list.add(new CompletionItem(label, insert, "HTML ID", CompletionItem.Type.CSS_VALUE, 0));
            }
        }
        return list;
    }

    private void addCrossFileSelectors(List<CompletionItem> list) {
        list.addAll(getClassSelectorItems());
        list.addAll(getIdSelectorItems());
    }

    private List<CompletionItem> getSelectorSuggestions(String word, String trimmed) {
        if (word.startsWith(":")) {
            return filterPseudoSelectors(word, false, false);
        }
        List<CompletionItem> all = new ArrayList<>();
        for (CompletionItem ci : htmlTagItems) {
            CompletionItem copy = new CompletionItem(ci);
            copy.setReplaceLength(word.length());
            all.add(copy);
        }
        addCrossFileSelectors(all);
        List<CompletionItem> filtered = fuzzyFilter(all, word);
        List<CompletionItem> out = new ArrayList<>(filtered.size());
        for (CompletionItem ci : filtered) {
            CompletionItem copy = new CompletionItem(ci);
            copy.setReplaceLength(word.length());
            out.add(copy);
        }
        return out;
    }

    private boolean isColorProperty(String prop) {
        if (prop == null) return false;
        String p = prop.toLowerCase();
        if (p.endsWith("-width") || p.endsWith("-style") || p.contains("radius")) {
            return false;
        }
        if (KnownElements.isCssColorProperty(p)) {
            return true;
        }
        return p.equals("background") || p.startsWith("background")
                || p.equals("fill") || p.equals("stroke")
                || p.startsWith("outline")
                || p.endsWith("shadow") || p.equals("caret-color") || p.equals("accent-color");
    }

    private List<CompletionItem> getMediaSuggestions(String fullText, int cursorPos, String word) {
        String line = getLineBeforeCursor(fullText, cursorPos);

        // Determine if inside parentheses of the media condition
        boolean inParens = isInParens(fullText, cursorPos);

        List<CompletionItem> items = new ArrayList<>();

        if (!inParens) {
            for (String kw : CssDefinitions.MEDIA_KEYWORDS) {
                items.add(new CompletionItem(kw, kw + " ", "Media type/keyword", CompletionItem.Type.KEYWORD, 0));
            }
        }

        for (CssDefinitions.MediaFeatureEntry f : CssDefinitions.MEDIA_FEATURES) {
            String label = f.label;
            String insertText = f.insertText;

            if (!inParens) {
                insertText = "(" + insertText + ")";
            }

            int offset = 0;
            if (insertText.contains("|")) {
                String after = insertText.substring(insertText.indexOf('|') + 1);
                offset = -after.length();
                insertText = insertText.replace("|", "");
            }
            items.add(new CompletionItem(label, insertText, "Media feature", CompletionItem.Type.CSS_VALUE, offset));
        }

        List<CompletionItem> filtered = fuzzyFilter(items, word);
        List<CompletionItem> out = new ArrayList<>(filtered.size());
        for (CompletionItem ci : filtered) {
            CompletionItem copy = new CompletionItem(ci);
            copy.setReplaceLength(word.length());
            out.add(copy);
        }
        return out;
    }

    private boolean isInParens(String fullText, int cursorPos) {
        int open = 0, close = 0;
        // Find the at-rule start on this line or nearby
        int searchStart = Math.max(0, cursorPos - 300);
        String snippet = fullText.substring(searchStart, cursorPos);
        int atIdx = snippet.lastIndexOf("@media");
        if (atIdx < 0) atIdx = snippet.lastIndexOf("@supports");
        if (atIdx < 0) atIdx = snippet.lastIndexOf("@container");
        if (atIdx >= 0) {
            String afterAt = snippet.substring(atIdx);
            for (char c : afterAt.toCharArray()) {
                if (c == '(') open++;
                else if (c == ')') close++;
            }
        }
        boolean inParens = open > close;
        return inParens;
    }

    // Helpers

    /**
     * Determines if the cursor is inside an at-rule CONDITION (before the opening brace).
     * e.g. "@media screen and (|)" or "@supports (display: grid|)"
     * Returns false once the { is opened (we're in the body, not the condition).
     */
    private boolean isInsideAtRuleCondition(String text, int cursorPos) {
        int start = Math.max(0, cursorPos - 500);
        String snippet = text.substring(start, cursorPos);

        // Find the last @media/@supports/@container
        int mediaIdx = -1;
        for (String rule : new String[]{"@media", "@supports", "@container"}) {
            int idx = snippet.lastIndexOf(rule);
            if (idx > mediaIdx) mediaIdx = idx;
        }
        if (mediaIdx < 0) return false;

        String afterRule = snippet.substring(mediaIdx);
        // If there's an opening brace, we're past the condition and inside the body
        return afterRule.indexOf('{') < 0;
    }

    private boolean isInsideCssImport(String text, int pos) {
        if (text == null || pos <= 0 || pos > text.length()) return false;
        int lineStart = text.lastIndexOf('\n', pos - 1);
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;
        String line = text.substring(lineStart, pos).trim();
        return line.startsWith("@import");
    }

    private enum Zone {SELECTOR, PROPERTY, VALUE, NESTED_SELECTOR}
}
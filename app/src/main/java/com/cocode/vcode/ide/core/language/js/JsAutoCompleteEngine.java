package com.cocode.vcode.ide.core.language.js;

import android.content.Context;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.autocomplete.VFSManager;
import com.cocode.vcode.ide.core.completion.staticdata.JsStaticCompletionDispatcher;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ECMAScript/JavaScript IntelliSense engine — mirrors VS Code's JavaScript Language Server behaviour.
 *
 * <p>Key behaviours:
 * <ul>
 *   <li>Member items use insertText = just the method name (e.g. "floor(|)"), NOT "Math.floor(|)".
 *       The dot and namespace are already in the document — we only insert after the dot.</li>
 *   <li>Dot completion triggers when cursor is right after '.' (word = "" thanks to getWordBeforeCursor fix).</li>
 *   <li>Chained-call completion: "fetch('url')." → Promise methods; "arr.filter(...)." → Array methods.</li>
 *   <li>Import/require path completion: shows files immediately when cursor is inside quote.</li>
 *   <li>Document symbol indexing is cached — only re-scans when text changes.</li>
 * </ul>
 */
public class JsAutoCompleteEngine extends AutoCompleteEngine {

    public static final Map<String, String> BuiltinTypeTable = new HashMap<>();
    // JSDoc type pattern
    private static final Pattern PAT_JSDOC_TYPE = Pattern.compile("@(?:type|returns?|param)\\s*\\{([^}]+)\\}");

    static {
        loadBuiltinTypeTable();
    }

    // Instance state
    private final List<CompletionItem> builtinItems = new ArrayList<>();
    private final List<CompletionItem> cachedGenericSymbols = new ArrayList<>();
    private final Map<String, String> varTypeMap = new HashMap<>();
    private int lastTextHash = 0;
    private File currentFile;
    private JsSyntaxTree cachedTree;
    private com.cocode.vcode.ide.core.diagnostic.util.TokenStream cachedTokens;
    private ScopeTree cachedScopeTree;
    public JsAutoCompleteEngine(Context context) {
        super(context);
        if (BuiltinTypeTable.isEmpty()) {
            loadBuiltinTypeTable();
        }
        loadKeywords();
    }

    /**
     * Loads JavaScript/TypeScript built-in member-to-type mappings dynamically from assets/types/builtin_types.json.
     * Supports both hierarchical/grouped definitions (e.g. "@STRING": { "split": "@ARRAY" }) and
     * flat key-value pairs (e.g. "@STRING.split": "@ARRAY").
     */
    public static synchronized void loadBuiltinTypeTable() {
        BuiltinTypeTable.clear();
        String json = com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader.readAsset("types/builtin_types.json");
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object val = root.get(key);
                if (val instanceof JSONObject) {
                    JSONObject methods = (JSONObject) val;
                    Iterator<String> methodKeys = methods.keys();
                    while (methodKeys.hasNext()) {
                        String method = methodKeys.next();
                        BuiltinTypeTable.put(key + "." + method, methods.getString(method));
                    }
                } else if (val instanceof String) {
                    BuiltinTypeTable.put(key, (String) val);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Resolves the return type for a built-in type member (e.g. "@STRING", "split" -> "@ARRAY").
     * Lazily loads the type table if not yet loaded.
     */
    public static String getBuiltinType(String lookupType, String memberName) {
        if (BuiltinTypeTable.isEmpty()) {
            loadBuiltinTypeTable();
        }
        return BuiltinTypeTable.get(lookupType + "." + memberName);
    }

    private static String getBaseMemberName(String label) {
        if (label == null) return "";
        int paren = label.indexOf('(');
        return (paren >= 0 ? label.substring(0, paren) : label).trim();
    }

    private static boolean isIgnoredClassMember(String name) {
        return "constructor".equals(name) || "prototype".equals(name) || "function".equals(name);
    }

    private static boolean isArrowIdentity(String args) {
        if (args == null) return false;
        int arrowIdx = args.indexOf("=>");
        if (arrowIdx == -1) return false;
        String after = args.substring(arrowIdx + 2).trim();
        if (after.isEmpty()) return false;
        for (int i = 0; i < after.length(); i++) {
            char c = after.charAt(i);
            if (i == 0) {
                if (!Character.isLetter(c) && c != '_' && c != '$') return false;
            } else {
                if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') return false;
            }
        }
        return true;
    }

    private static List<String> extractObjectKeys(String text, boolean allowEquals) {
        List<String> keys = new ArrayList<>();
        if (text == null || text.isEmpty()) return keys;
        int len = text.length();
        int i = 0;
        while (i < len) {
            char c = text.charAt(i);
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_' || text.charAt(i) == '$')) {
                    i++;
                }
                String id = text.substring(start, i);
                while (i < len && Character.isWhitespace(text.charAt(i))) {
                    i++;
                }
                if (i < len) {
                    char next = text.charAt(i);
                    if (next == ':' || (allowEquals && next == '=')) {
                        if (!keys.contains(id)) {
                            keys.add(id);
                        }
                    }
                }
            } else if (c == '"' || c == '\'' || c == '`') {
                char quote = c;
                i++;
                while (i < len && text.charAt(i) != quote) {
                    if (text.charAt(i) == '\\' && i + 1 < len) i++;
                    i++;
                }
                if (i < len) i++;
            } else {
                i++;
            }
        }
        return keys;
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
        File projectRoot = ProjectSymbolIndex.getProjectRoot(file);
        if (projectRoot != null) {
            ProjectSymbolIndex.getInstance().buildIndex(projectRoot);
            com.cocode.vcode.ide.utils.ExecutorProvider.getInstance().runOnIo(() -> {
                com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().indexProjectIncremental(projectRoot);
            });
        }
    }

    // Keyword loading
    private void loadKeywords() {
        try {
            List<CompletionItem> items = JsStaticCompletionDispatcher.buildCompletions();
            if (items != null && !items.isEmpty()) {
                builtinItems.addAll(items);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            String json = loadAssetJson("completions/js_keywords.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String label = obj.optString("label");
                String typeStr = obj.optString("type", "KEYWORD");
                String snippet = obj.optString("snippet", obj.optString("insertText", label));
                String detail = obj.optString("detail", "");

                CompletionItem.Type type;
                try {
                    type = CompletionItem.Type.valueOf(typeStr);
                } catch (Exception e) {
                    type = CompletionItem.Type.KEYWORD;
                }

                int offset = 0;
                if (snippet.contains("|")) {
                    String after = snippet.substring(snippet.indexOf('|') + 1);
                    offset = -after.length();
                    snippet = snippet.replace("|", "");
                }
                builtinItems.add(new CompletionItem(label, snippet, detail, type, offset));
            }
        } catch (Exception e) {
            // Non-critical
        }
    }

    // Object literal key suggestions

    // Main entry point
    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || fullText.isEmpty()) return new ArrayList<>();
        ensureDocumentIndexed(fullText);
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length())
            return new ArrayList<>();

        String word = getWordBeforeCursor(fullText, cursorPos);

        // Module path completions for import and require calls
        List<CompletionItem> importItems = getImportPathSuggestions(fullText, cursorPos);
        if (importItems != null) return importItems;

        // Named import and export clause completions
        List<CompletionItem> importExport = getImportExportSuggestions(fullText, cursorPos, word);
        if (importExport != null) return importExport;

        // Event name string completions (addEventListener/removeEventListener/on) & DOM queries
        String lineBefore = getLineBeforeCursor(fullText, cursorPos);
        int lastQuote = Math.max(lineBefore.lastIndexOf('\''), lineBefore.lastIndexOf('"'));
        if (lastQuote != -1) {
            String beforeQuote = lineBefore.substring(0, lastQuote).trim();
            if (beforeQuote.endsWith("(")) {
                String callee = beforeQuote.substring(0, beforeQuote.length() - 1).trim();
                if (callee.endsWith("addEventListener") || callee.endsWith("removeEventListener") || callee.endsWith("on")) {
                    String typedEvent = lineBefore.substring(lastQuote + 1);
                    List<CompletionItem> eventItems = new ArrayList<>();
                    for (String ev : JsStandardLibrary.EVENT_NAMES) {
                        CompletionItem ci = new CompletionItem(ev, ev, "DOM Event", CompletionItem.Type.VALUE, 0);
                        ci.setReplaceLength(typedEvent.length());
                        eventItems.add(ci);
                    }
                    return fuzzyFilter(eventItems, typedEvent);
                } else if (callee.endsWith("getElementById")) {
                    String typedId = lineBefore.substring(lastQuote + 1);
                    List<CompletionItem> ids = ProjectSymbolIndex.getInstance().getHtmlIdItems();
                    for (CompletionItem ci : ids) ci.setReplaceLength(typedId.length());
                    return fuzzyFilter(ids, typedId);
                } else if (callee.endsWith("querySelector") || callee.endsWith("querySelectorAll")) {
                    String typedQuery = lineBefore.substring(lastQuote + 1);
                    List<CompletionItem> prefixed = getCompletionItems(typedQuery);
                    for (CompletionItem ci : prefixed) ci.setReplaceLength(typedQuery.length());
                    return fuzzyFilter(prefixed, typedQuery);
                }
            }
        }

        if (cachedTokens != null && cachedTokens.length > 0) {
            int tokIdx = findTokenIndexAtOffset(cachedTokens, cursorPos);
            if (tokIdx >= 0 && tokIdx < cachedTokens.length) {
                byte type = cachedTokens.types[tokIdx];
                if (type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_COMMENT ||
                        type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_STRING ||
                        type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_TEMPLATE) {
                    if (type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_TEMPLATE && isInsideTemplateExpression(fullText, cursorPos)) {
                        // Allow completions inside template interpolation (${...})
                    } else if (cursorPos != cachedTokens.tokenStart[tokIdx]) {
                        return new ArrayList<>();
                    }
                }
            }
        }

        // Member access completions following a dot accessor
        int dotCheckPos = cursorPos - word.length() - 1;

        int enclosingNode = 0;
        if (cachedTree != null && cachedTree.nodesByOffset != null) {
            int low = 1, high = cachedTree.nodeCount - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int midNode = cachedTree.nodesByOffset[mid];
                if (cachedTree.nodeStart[midNode] <= cursorPos) {
                    enclosingNode = midNode;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
        }

        if (dotCheckPos >= 0 && fullText.charAt(dotCheckPos) == '.') {
            return getMemberCompletions(fullText, dotCheckPos, word);
        }
        if (dotCheckPos >= 1 && fullText.charAt(dotCheckPos) == '.' && fullText.charAt(dotCheckPos - 1) == '?') {
            return getMemberCompletions(fullText, dotCheckPos, word);
        }

        // Inside an object literal (after { or ,), suggest known property keys
        List<CompletionItem> objKeys = getObjectLiteralSuggestions(fullText, cursorPos, word);
        if (objKeys != null) return objKeys;

        if (word.isEmpty()) {
            if (cursorPos > 0 && fullText.charAt(cursorPos - 1) == '(') {
                return new ArrayList<>();
            }
        }

        // Scope symbols, globals, and keyword completions
        List<CompletionItem> staticItems = JsStaticCompletionDispatcher.buildCompletions();
        List<CompletionItem> baseKeywords = (staticItems != null && !staticItems.isEmpty()) ? staticItems : builtinItems;
        List<CompletionItem> filteredKeywords = JsStaticCompletionDispatcher.filterStructural(baseKeywords, fullText, cursorPos);

        List<CompletionItem> all = new ArrayList<>(filteredKeywords);
        Set<String> added = new HashSet<>();
        for (CompletionItem item : filteredKeywords) added.add(item.getLabel());

        if (cachedTree != null && cachedScopeTree != null) {
            int currentScope = cachedScopeTree.findScopeAt(cursorPos, cachedTree);
            for (String name : cachedScopeTree.symbols.keySet()) {
                if (added.contains(name)) continue;
                int[] resolved = cachedScopeTree.lookupSymbol(name, currentScope);
                if (resolved != null) {
                    added.add(name);
                    int kind = resolved[2];
                    CompletionItem.Type type = mapKindToCompletionType(kind);
                    all.add(new CompletionItem(name, name, getDetailForKind(kind), type, 0));
                }
            }
        }

        for (CompletionItem item : cachedGenericSymbols) {
            if (added.add(item.getLabel())) {
                all.add(item);
            }
        }

        return fuzzyFilter(all, word);
    }

    private CompletionItem.Type mapKindToCompletionType(int kind) {
        switch (kind) {
            case JsSyntaxTree.N_CLASS_DECL:
                return CompletionItem.Type.KEYWORD;
            case JsSyntaxTree.N_FUNC_DECL:
            case JsSyntaxTree.N_ARROW_FUNC:
            case JsSyntaxTree.N_METHOD:
                return CompletionItem.Type.FUNCTION;
            default:
                return CompletionItem.Type.VALUE;
        }
    }

    private String getDetailForKind(int kind) {
        switch (kind) {
            case JsSyntaxTree.N_CLASS_DECL:
                return "Class";
            case JsSyntaxTree.N_FUNC_DECL:
            case JsSyntaxTree.N_ARROW_FUNC:
            case JsSyntaxTree.N_METHOD:
                return "Function";
            case JsSyntaxTree.N_PARAM:
                return "Parameter";
            default:
                return "Variable";
        }
    }

    @NonNull
    private List<CompletionItem> getCompletionItems(String typedQuery) {
        List<CompletionItem> prefixed = new ArrayList<>();

        for (CompletionItem ci : ProjectSymbolIndex.getInstance().getCssClassItems()) {
            CompletionItem prefixedItem = new CompletionItem("." + ci.getLabel(), "." + ci.getEffectiveInsertText(), ci.getDetail(), ci.getType(), ci.getCursorOffset());
            prefixedItem.setReplaceLength(Objects.requireNonNull(typedQuery).length());
            prefixed.add(prefixedItem);
        }
        for (CompletionItem ci : ProjectSymbolIndex.getInstance().getHtmlIdItems()) {
            CompletionItem prefixedItem = new CompletionItem("#" + ci.getLabel(), "#" + ci.getEffectiveInsertText(), ci.getDetail(), ci.getType(), ci.getCursorOffset());
            prefixedItem.setReplaceLength(Objects.requireNonNull(typedQuery).length());
            prefixed.add(prefixedItem);
        }
        return prefixed;
    }

    /**
     * Detects if cursor is in an object literal key position and suggests known keys.
     * Returns null if not in object literal context, empty list if in context but no suggestions.
     *
     * <p>Detects these patterns:
     * <ul>
     *   <li>{@code { | }} — after opening brace</li>
     *   <li>{@code { key: value, | }} — after comma in object</li>
     *   <li>Destructuring: {@code const { | } = obj}</li>
     * </ul>
     */
    private List<CompletionItem> getObjectLiteralSuggestions(String fullText, int cursorPos, String word) {
        // Find the character that precedes the current word (skip whitespace)
        int i = cursorPos - word.length() - 1;
        while (i >= 0 && Character.isWhitespace(fullText.charAt(i))) i--;
        if (i < 0) return null;

        char preceding = fullText.charAt(i);
        // Object key position indicators: after { or after ,
        if (preceding != '{' && preceding != ',') return null;

        // Exclude class bodies, switch statements, and block-level code scopes
        if (cachedTree != null && cachedTree.nodesByOffset != null && cachedTree.nodeCount > 1) {
            int deepest = 0;
            int minLen = Integer.MAX_VALUE;
            int n = cachedTree.nodeCount - 1;
            int low = 1, high = n;
            int searchIdx = n;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int midNode = cachedTree.nodesByOffset[mid];
                if (midNode > 0 && midNode < cachedTree.nodeCount && cachedTree.nodeStart[midNode] <= cursorPos) {
                    searchIdx = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            for (int j = searchIdx; j >= 1; j--) {
                int node = cachedTree.nodesByOffset[j];
                if (node > 0 && node < cachedTree.nodeCount && cursorPos >= cachedTree.nodeStart[node] && cursorPos <= cachedTree.nodeEnd[node]) {
                    int len = cachedTree.nodeEnd[node] - cachedTree.nodeStart[node];
                    if (len < minLen) {
                        minLen = len;
                        deepest = node;
                    }
                }
            }
            if (deepest > 0) {
                int dType = cachedTree.nodeType[deepest];
                if (dType == JsSyntaxTree.N_CLASS_DECL || dType == JsSyntaxTree.N_INTERFACE ||
                        dType == JsSyntaxTree.N_SWITCH_STMT || dType == JsSyntaxTree.N_ENUM ||
                        dType == JsSyntaxTree.N_CASE_CLAUSE || dType == JsSyntaxTree.N_BLOCK) {
                    return null;
                }
            }
        }

        if (preceding == '{') {
            int j = i - 1;
            while (j >= 0 && Character.isWhitespace(fullText.charAt(j))) j--;
            if (j >= 0) {
                char beforeBrace = fullText.charAt(j);
                if (beforeBrace == ')' || beforeBrace == '>' || beforeBrace == '$' || beforeBrace == ']')
                    return null;
                int wEnd = j + 1;
                int wStart = j;
                while (wStart >= 0 && Character.isLetterOrDigit(fullText.charAt(wStart))) wStart--;
                String kwBefore = fullText.substring(wStart + 1, wEnd);
                if ("else".equals(kwBefore) || "try".equals(kwBefore) || "catch".equals(kwBefore)
                        || "finally".equals(kwBefore) || "do".equals(kwBefore) || "switch".equals(kwBefore)
                        || "class".equals(kwBefore) || "interface".equals(kwBefore) || "enum".equals(kwBefore)
                        || "function".equals(kwBefore) || "static".equals(kwBefore)) {
                    return null;
                }
            }
        }

        // We're likely in an object literal — gather keys from same object and similar objects

        // Collect keys already used in this object (to avoid re-suggesting them)
        java.util.Set<String> usedKeys = new java.util.HashSet<>();
        int braceStart = findMatchingBrace(fullText, cursorPos);
        if (braceStart >= 0) {
            String objContent = fullText.substring(braceStart + 1, cursorPos);
            for (String part : objContent.split(",")) {
                int colon = part.indexOf(':');
                String k = colon != -1 ? part.substring(0, colon).trim() : part.trim();
                if (!k.isEmpty()) usedKeys.add(k);
            }
        }

        List<CompletionItem> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>(usedKeys);

        if (cachedTree != null) {
            for (String[] shape : cachedTree.shapeTable.values()) {
                if (shape != null) {
                    for (String key : shape) {
                        if (key != null && key.length() > 1 && seen.add(key)) {
                            CompletionItem ci = new CompletionItem(key, key, "Object key", CompletionItem.Type.VALUE, 0);
                            ci.setReplaceLength(word.length());
                            items.add(ci);
                        }
                    }
                }
            }
            for (int node = 1; node < cachedTree.nodeCount; node++) {
                if (cachedTree.nodeType[node] == JsSyntaxTree.N_PROPERTY) {
                    String key = cachedTree.nodeName[node];
                    if (key != null && key.length() > 1 && seen.add(key)) {
                        CompletionItem ci = new CompletionItem(key, key, "Object key", CompletionItem.Type.VALUE, 0);
                        ci.setReplaceLength(word.length());
                        items.add(ci);
                    }
                }
            }
        }

        if (items.isEmpty()) return null; // Not enough context to suggest
        return fuzzyFilter(items, word);
    }

    private boolean isInsideTemplateExpression(String text, int cursorPos) {
        if (text == null || cursorPos <= 0) return false;
        int braceDepth = 0;
        int limit = Math.max(0, cursorPos - 4000);
        for (int i = cursorPos - 1; i >= limit; i--) {
            char c = text.charAt(i);
            if (c == '}') {
                braceDepth++;
            } else if (c == '{') {
                if (braceDepth > 0) {
                    braceDepth--;
                } else if (i > 0 && text.charAt(i - 1) == '$') {
                    return true;
                }
            } else if (c == '`') {
                int slashes = 0;
                for (int j = i - 1; j >= 0 && text.charAt(j) == '\\'; j--) slashes++;
                if (slashes % 2 == 0 && braceDepth == 0) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Finds the position of the opening { for the object literal we're currently in.
     */
    private int findMatchingBrace(String text, int cursorPos) {
        int depth = 0;
        com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = cachedTokens;
        for (int i = cursorPos - 1; i >= 0; i--) {
            if (tokens != null && i < tokens.length) {
                byte t = tokens.types[i];
                if (t == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_STRING ||
                        t == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_COMMENT ||
                        t == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_TEMPLATE) {
                    int tokStart = tokens.tokenStart[i];
                    if (tokStart >= 0 && tokStart <= i) {
                        i = tokStart;
                        continue;
                    }
                }
            }
            char c = text.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
    }

    // Import / require path completion
    private List<CompletionItem> getImportExportSuggestions(String fullText, int cursorPos, String word) {
        if (fullText == null || cursorPos <= 0 || cursorPos > fullText.length()) return null;

        // Check if preceding is `as ` (alias)
        int beforeWord = cursorPos - word.length() - 1;
        while (beforeWord >= 0 && Character.isWhitespace(fullText.charAt(beforeWord))) beforeWord--;
        if (beforeWord >= 1 && fullText.charAt(beforeWord) == 's' && fullText.charAt(beforeWord - 1) == 'a') {
            int beforeAs = beforeWord - 2;
            if (beforeAs < 0 || Character.isWhitespace(fullText.charAt(beforeAs))) {
                return new ArrayList<>(); // user is typing alias
            }
        }

        // Lexical scan backwards to find if we are inside `import { ... }`
        int braceOpen = -1;
        int depth = 0;
        for (int i = cursorPos - 1; i >= 0; i--) {
            char c = fullText.charAt(i);
            if (c == '}') {
                depth++;
            } else if (c == '{') {
                if (depth > 0) {
                    depth--;
                } else {
                    braceOpen = i;
                    break;
                }
            }
        }
        if (braceOpen < 0) return null;

        // Check if preceding non-whitespace before `{` is `import`
        int p = braceOpen - 1;
        while (p >= 0 && Character.isWhitespace(fullText.charAt(p))) p--;
        if (p < 5) return null;
        int wordEnd = p + 1;
        while (p >= 0 && Character.isLetterOrDigit(fullText.charAt(p))) p--;
        String kw = fullText.substring(p + 1, wordEnd);
        if (!"import".equals(kw)) return null;

        // We are inside `import { ... }`!
        // Scan forward to see if there is `} from 'path'`
        int braceClose = -1;
        depth = 0;
        for (int i = cursorPos; i < fullText.length(); i++) {
            char c = fullText.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                } else {
                    braceClose = i;
                    break;
                }
            }
        }

        String modulePath = null;
        if (braceClose != -1) {
            int q = braceClose + 1;
            while (q < fullText.length() && Character.isWhitespace(fullText.charAt(q))) q++;
            if (q + 4 <= fullText.length() && fullText.startsWith("from", q)) {
                q += 4;
                while (q < fullText.length() && Character.isWhitespace(fullText.charAt(q))) q++;
                if (q < fullText.length() && (fullText.charAt(q) == '\'' || fullText.charAt(q) == '"')) {
                    char quote = fullText.charAt(q);
                    int endQuote = fullText.indexOf(quote, q + 1);
                    if (endQuote != -1) {
                        modulePath = fullText.substring(q + 1, endQuote);
                    }
                }
            }
        }

        List<CompletionItem> exports;
        if (modulePath != null && !modulePath.isEmpty()) {
            exports = getExportsFromPath(modulePath);
        } else {
            exports = getExportsProjectWide(word);
        }
        if (exports == null || exports.isEmpty()) return null;

        // Collect names already imported inside this import { ... } block
        int scanEnd = braceClose != -1 ? braceClose : cursorPos;
        String insideBlock = fullText.substring(braceOpen + 1, scanEnd);
        Set<String> used = new HashSet<>();
        for (String part : insideBlock.split(",")) {
            String clean = part.trim();
            if (clean.contains(" as ")) clean = clean.split("\\s+as\\s+")[0].trim();
            if (word != null && !word.isEmpty() && clean.equals(word)) continue;
            if (!clean.isEmpty()) used.add(clean);
        }

        List<CompletionItem> filtered = new ArrayList<>();
        for (CompletionItem e : exports) {
            if (!used.contains(e.getEffectiveInsertText())) {
                e.setReplaceLength(word != null ? word.length() : 0);
                filtered.add(e);
            }
        }
        return fuzzyFilter(filtered, word);
    }

    /**
     * Resolve {@code path} against {@code currentFile} and return the
     * exports from the target file's parse result. {@code null} if
     * the path cannot be resolved or the target file is not yet
     * parsed.
     */
    private List<CompletionItem> getExportsFromPath(String path) {
        if (currentFile == null) return null;
        com.cocode.vcode.ide.core.lsp.LspLocation loc =
                com.cocode.vcode.ide.core.lsp.ModuleResolver.resolveModulePath(
                        currentFile.getAbsolutePath(), path);
        if (loc == null || loc.uri == null) return null;
        java.io.File targetFile = new java.io.File(loc.uri);
        if (!targetFile.exists()) return null;
        com.cocode.vcode.ide.core.language.js.ParseResult parseResult =
                com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getParseResult(loc.uri);
        if (parseResult == null) {
            try {
                String content = com.cocode.vcode.ide.utils.FileUtils.readFile(targetFile);
                if (content != null) {
                    com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(content);
                    com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = com.cocode.vcode.ide.core.language.js.JsParser.parseTopLevel(content, tokens);
                    parseResult = new com.cocode.vcode.ide.core.language.js.ParseResult(targetFile, content, tokens, tree, null, com.cocode.vcode.ide.core.language.js.ParseResult.MODE_FULL);
                    com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().updateParseResult(loc.uri, parseResult);
                }
            } catch (Exception ignored) {
            }
        }
        if (parseResult == null || parseResult.tree == null) return null;

        List<CompletionItem> exports = new ArrayList<>();
        com.cocode.vcode.ide.core.language.js.JsExportTable exportTable =
                com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getExportTable(loc.uri);
        if (exportTable == null && parseResult.tree != null) {
            exportTable = com.cocode.vcode.ide.core.language.js.JsExportTable.build(parseResult.tree, loc.uri);
        }
        if (exportTable != null && exportTable.count > 0) {
            for (int i = 0; i < exportTable.count; i++) {
                String name = exportTable.exportName[i];
                if (name != null && !name.isEmpty()) {
                    exports.add(new CompletionItem(name, name, "Export", CompletionItem.Type.VALUE, 0, loc.uri));
                }
            }
            return exports;
        }

        com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = parseResult.tree;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_EXPORT) {
                for (int child = tree.nodeChild[i], childLoop = 0; child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount; child = tree.nodeSibling[child]) {
                    int cType = tree.nodeType[child];
                    String name = tree.nodeName[child];
                    if (name != null && !name.isEmpty()) {
                        CompletionItem.Type type = (cType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_FUNC_DECL
                                || cType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_ARROW_FUNC)
                                ? CompletionItem.Type.FUNCTION
                                : (cType == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_CLASS_DECL ? CompletionItem.Type.KEYWORD : CompletionItem.Type.VALUE);
                        exports.add(new CompletionItem(name, name, "Export", type, 0, loc.uri));
                    }
                }
            }
        }
        return exports;
    }

    /**
     * fallback: query the project-wide export table for exports
     * matching {@code word}. Returns an empty list if the table is
     * empty or {@code word} is empty. Each suggestion carries the
     * source URI of the file that declared the export, so the
     * import-statement auto-insert can compute the relative
     * path correctly.
     */
    private List<CompletionItem> getExportsProjectWide(String word) {
        if (word == null) word = "";
        java.util.List<com.cocode.vcode.ide.core.lsp.ProjectIndex.ExportRef> refs =
                com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getExportsByPrefix(word);
        if (refs == null || refs.isEmpty()) return new ArrayList<>();
        List<CompletionItem> out = new ArrayList<>();
        for (com.cocode.vcode.ide.core.lsp.ProjectIndex.ExportRef ref : refs) {
            // Skip exports from the current file — those aren't
            // cross-file completions. Local exports can be referenced
            // directly without an import.
            if (currentFile != null && ref.sourceUri != null
                    && ref.sourceUri.equals(currentFile.getAbsolutePath())) {
                continue;
            }
            out.add(new CompletionItem(ref.name, ref.name, "Export",
                    CompletionItem.Type.VALUE, 0, ref.sourceUri));
        }
        return out;
    }

    // Dot-member completion

    /**
     * K.3: build the import-statement auto-insert for a
     * cross-file completion. The caller (the editor's
     * completion-popup handler) is responsible for performing the
     * text edit using the returned {@link
     * com.cocode.vcode.ide.core.lsp.ImportStatementBuilder.Result}.
     *
     * @param name           the exported symbol name (the
     *                       completion's label)
     * @param sourceFileUri  the URI of the file that exports the
     *                       symbol (the completion's source URI)
     * @param currentFileUri the URI of the file the user is editing
     * @param currentText    the current text of the editing file
     * @return the import statement and insertion offset, or null if
     * the two files are the same
     */
    public com.cocode.vcode.ide.core.lsp.ImportStatementBuilder.Result buildImportInsert(
            String name, String sourceFileUri, String currentFileUri, String currentText) {
        return com.cocode.vcode.ide.core.lsp.ImportStatementBuilder.build(
                name, sourceFileUri, currentFileUri, currentText);
    }

    /**
     * Returns file completions when cursor is inside an import/require path string.
     *
     * @return list of file completions, empty list if inside import but no matches,
     * or {@code null} if NOT inside an import context at all.
     */
    private List<CompletionItem> getImportPathSuggestions(String fullText, int cursorPos) {
        if (currentFile == null) return null;

        String lineBefore = getLineBeforeCursor(fullText, cursorPos);
        int lastQuote = Math.max(lineBefore.lastIndexOf('\''), lineBefore.lastIndexOf('"'));
        if (lastQuote == -1) return null;

        String beforeQuote = lineBefore.substring(0, lastQuote).trim();
        boolean isImportPath = false;
        if (beforeQuote.endsWith("from") || beforeQuote.endsWith("require(") || beforeQuote.endsWith("import(")) {
            isImportPath = true;
        } else if (beforeQuote.endsWith("(")) {
            String callee = beforeQuote.substring(0, beforeQuote.length() - 1).trim();
            if (callee.endsWith("require") || callee.endsWith("import")) {
                isImportPath = true;
            }
        }
        if (!isImportPath) return null;

        String typedPath = lineBefore.substring(lastQuote + 1);
        return buildFileCompletions(typedPath);
    }

    private List<CompletionItem> buildFileCompletions(String typedPath) {
        File baseDir = currentFile.getParentFile();
        if (baseDir == null) return new ArrayList<>();

        int lastSlash = typedPath.lastIndexOf('/');
        File searchDir;
        String filterPrefix;

        if (lastSlash != -1) {
            String dirPart = typedPath.substring(0, lastSlash);
            filterPrefix = typedPath.substring(lastSlash + 1).toLowerCase();
            searchDir = dirPart.isEmpty() ? baseDir : new File(baseDir, dirPart);
        } else {
            filterPrefix = typedPath.toLowerCase();
            // For relative paths (start with . or /) search base dir; for bare names also show from base dir
            searchDir = baseDir;
        }

        if (!searchDir.exists() || !searchDir.isDirectory()) return new ArrayList<>();

        List<CompletionItem> items = new ArrayList<>();
        List<File> files = VFSManager.getInstance().listCachedFiles(searchDir);
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.startsWith(".")) continue;
                if (!filterPrefix.isEmpty() && !name.toLowerCase().startsWith(filterPrefix))
                    continue;

                if (f.isDirectory()) {
                    items.add(new CompletionItem(name + "/", name + "/",
                            "Directory", CompletionItem.Type.FOLDER, 0));
                } else if (isJsLike(name) || typedPath.isEmpty()) {

                    if (currentFile != null && f.getAbsolutePath().equals(currentFile.getAbsolutePath()))
                        continue;

                    items.add(new CompletionItem(name, name, "File", CompletionItem.Type.FILE, 0));
                }
            }
        }

        java.util.Collections.sort(items, (a, b) -> {
            int fa = a.getType() == CompletionItem.Type.FOLDER ? 0 : 1;
            int fb = b.getType() == CompletionItem.Type.FOLDER ? 0 : 1;
            if (fa != fb) return fa - fb;
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });
        return items.size() > MAX_SUGGESTIONS ? items.subList(0, MAX_SUGGESTIONS) : items;
    }

    private boolean isJsLike(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".jsx")
                || lower.endsWith(".tsx") || lower.endsWith(".json") || lower.endsWith(".mjs");
    }

    /**
     * Computes member completions for the object/expression before the dot.
     *
     * <p>Insert text is ONLY the method name (e.g. "floor(|)"), never "Math.floor(|)".
     * The namespace is already in the document — we insert only AFTER the dot.
     */
    private List<CompletionItem> getMemberCompletions(String text, int dotPos, String word) {
        String objectToken = extractObjectBeforeDot(text, dotPos);
        if (objectToken.isEmpty()) return new ArrayList<>();

        // this. → current class member completions
        if (objectToken.equals("this")) {
            List<CompletionItem> thisMembers = getCurrentClassMembers(dotPos);
            if (!thisMembers.isEmpty()) return fuzzyFilter(thisMembers, word);
        }

        // ShapeTable lookup
        if (cachedTree != null && cachedScopeTree != null) {
            int currentScope = cachedScopeTree.findScopeAt(dotPos, cachedTree);
            int[] resolved = cachedScopeTree.lookupSymbol(objectToken, currentScope, dotPos, cachedTree);
            if (resolved != null) {
                int declNodeId = resolved[1];
                if (cachedTree.nodeType[declNodeId] == JsSyntaxTree.N_CLASS_DECL) {
                    String className = cachedTree.nodeName[declNodeId];
                    if (className == null || className.isEmpty()) className = objectToken;
                    List<CompletionItem> classMembers = buildClassMemberItems(cachedTree, declNodeId, className);
                    if (!classMembers.isEmpty()) return fuzzyFilter(classMembers, word);
                } else if (cachedTree.nodeType[declNodeId] == JsSyntaxTree.N_ENUM) {
                    List<CompletionItem> enumMembers = getEnumMembers(cachedTree, declNodeId, word);
                    if (!enumMembers.isEmpty()) return fuzzyFilter(enumMembers, word);
                } else if (cachedTree.nodeType[declNodeId] == JsSyntaxTree.N_INTERFACE) {
                    List<CompletionItem> ifaceMembers = getInterfaceMembers(cachedTree, declNodeId, word);
                    if (!ifaceMembers.isEmpty()) return fuzzyFilter(ifaceMembers, word);
                }
                String[] shape = cachedTree.shapeTable.get(declNodeId);
                if (shape != null) {
                    List<CompletionItem> shapeMembers = new ArrayList<>();
                    for (String key : shape) {
                        shapeMembers.add(new CompletionItem(key, key, "Property", CompletionItem.Type.VALUE, 0));
                    }
                    if (!shapeMembers.isEmpty()) return fuzzyFilter(shapeMembers, word);
                }
            }
        }

        // a. Check known static namespaces
        for (String[] pair : JsStandardLibrary.DOT_METHODS) {
            if (pair[0].equalsIgnoreCase(objectToken) || objectToken.endsWith(pair[0])) {
                return buildMemberList(pair[0], pair[1].split(","), word, CompletionItem.Type.BUILTIN);
            }
        }

        // b. Functions that always return Promise (e.g. fetch)
        if (JsStandardLibrary.PROMISE_FUNCTIONS.contains(objectToken)) {
            return buildMemberList("Promise", JsStandardLibrary.PROTOTYPE_METHODS.get("promise"), word, CompletionItem.Type.FUNCTION);
        }

        // c. Chain return type — e.g. "arr.filter(...)" → array methods
        String chainType = JsStandardLibrary.CHAIN_RETURN_TYPES.get(objectToken);
        if (chainType != null) {
            String[] methods = JsStandardLibrary.PROTOTYPE_METHODS.get(chainType);
            if (methods != null) {
                return buildMemberList(chainType, methods, word, CompletionItem.Type.FUNCTION);
            }
        }

        // d. User-variable type inference and chain resolution
        List<String> chain = extractChainBeforeDot(text, dotPos);
        if (chain.size() > 0) {
            String baseToken = chain.get(0);
            boolean baseIsMethod = baseToken.endsWith("()");
            if (baseIsMethod) baseToken = baseToken.substring(0, baseToken.length() - 2);

            JsSyntaxTree activeTree = cachedTree;
            int activeNodeId = 0;
            boolean isNamespace = false;
            String inferredType = null;

            if (baseToken.equals("@STRING_LITERAL")) {
                inferredType = "@STRING";
            } else if (baseToken.equals("@ARRAY_LITERAL")) {
                inferredType = "@ARRAY";
            } else if (baseToken.equals("@NUMBER_LITERAL")) {
                inferredType = "@NUMBER";
            } else if (baseToken.startsWith("@OBJECT_LITERAL:")) {
                inferredType = "@INLINE_SHAPE:" + baseToken.substring(16);
            } else {
                if (cachedTree != null && cachedScopeTree != null) {
                    int currentScope = cachedScopeTree.findScopeAt(dotPos, cachedTree);
                    int[] resolved = cachedScopeTree.lookupSymbol(baseToken, currentScope, dotPos, cachedTree);
                    if (resolved != null) {
                        int declNodeId = resolved[1];
                        activeNodeId = declNodeId;
                        String typeAnn = cachedTree.nodeTypeAnn[declNodeId];
                        if (typeAnn != null && !typeAnn.isEmpty()) {
                            inferredType = typeAnn;
                        }
                    }
                }

                if (inferredType == null) {
                    inferredType = varTypeMap.get(baseToken);
                }
                if (inferredType == null) {
                    if (JsStandardLibrary.PROMISE_FUNCTIONS.contains(baseToken)) {
                        inferredType = "@PROMISE";
                    } else {
                        String baseChainType = JsStandardLibrary.CHAIN_RETURN_TYPES.get(baseToken);
                        if (baseChainType != null) inferredType = "@" + baseChainType.toUpperCase();
                    }
                }

                String importAnn = (inferredType != null && (inferredType.startsWith("@IMPORT:") || inferredType.startsWith("@REQUIRE:") || inferredType.startsWith("@REQUIRE_PROP:")))
                        ? inferredType : null;

                if (importAnn == null && cachedTree != null && cachedScopeTree != null && (inferredType == null || "@OBJECT".equals(inferredType))) {
                    int currentScope = cachedScopeTree.findScopeAt(dotPos, cachedTree);
                    int[] resolved = cachedScopeTree.lookupSymbol(baseToken, currentScope, dotPos, cachedTree);
                    if (resolved != null) {
                        int declNodeId = resolved[1];
                        String typeAnn = cachedTree.nodeTypeAnn[declNodeId];

                        if (typeAnn != null && (typeAnn.startsWith("@IMPORT:") || typeAnn.startsWith("@REQUIRE:") || typeAnn.startsWith("@REQUIRE_PROP:"))) {
                            importAnn = typeAnn;
                        } else {
                            activeNodeId = declNodeId;
                            if (cachedTree.shapeTable.containsKey(declNodeId)) {
                                inferredType = "@INLINE_SHAPE_NODE:" + declNodeId;
                            } else if (inferredType == null) {
                                inferredType = cachedTree.nodeTypeAnn[declNodeId];
                            }
                            if (inferredType == null) {
                                int parentId = cachedTree.nodeParent[declNodeId];
                                if (parentId != 0 && "{destructure}".equals(cachedTree.nodeName[parentId])) {
                                    String varName = cachedTree.nodeName[declNodeId];
                                    int child = cachedTree.nodeChild[parentId];
                                    int rhsNode = 0;
                                    int childLoop = 0;
                                    while (child > 0 && child < cachedTree.nodeCount && ++childLoop <= cachedTree.nodeCount) {
                                        if (cachedTree.nodeType[child] != JsSyntaxTree.N_VAR_DECL && cachedTree.nodeType[child] != JsSyntaxTree.N_PARAM) {
                                            rhsNode = child;
                                            break;
                                        }
                                        child = cachedTree.nodeSibling[child];
                                    }
                                    if (rhsNode != 0) {
                                        if (cachedTree.nodeType[rhsNode] == JsSyntaxTree.N_IDENTIFIER) {
                                            String rhsName = cachedTree.nodeName[rhsNode];
                                            int[] rhsResolved = cachedScopeTree.lookupSymbol(rhsName, currentScope, cachedTree.nodeStart[rhsNode], cachedTree);
                                            if (rhsResolved != null) {
                                                int rhsDeclId = rhsResolved[1];
                                                String rhsType = cachedTree.nodeTypeAnn[rhsDeclId];
                                                if ("@OBJECT".equals(rhsType)) {
                                                    inferredType = getShapeOfProperty(cachedTree, rhsDeclId, varName);
                                                }
                                            }
                                        } else if (cachedTree.nodeType[rhsNode] == JsSyntaxTree.N_OBJECT_LITERAL) {
                                            inferredType = getShapeOfProperty(cachedTree, parentId, varName);
                                        }
                                    }
                                }
                            }

                            if (inferredType == null) {
                                String[] shape = cachedTree.shapeTable.get(declNodeId);
                                if (shape != null) {
                                    inferredType = "@INLINE_SHAPE_NODE:" + declNodeId;
                                }
                            }

                            if ("@PROMISE".equals(inferredType)) {
                                int type = cachedTree.nodeType[declNodeId];
                                if (type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_ARROW_FUNC || type == JsSyntaxTree.N_METHOD) {
                                    String[] shape = findReturnShapeDFS(declNodeId);
                                    if (shape != null) {
                                        inferredType = "@PROMISE_INLINE_SHAPE:" + String.join(",", shape);
                                    }
                                }
                            }
                        }
                    }
                }

                if (importAnn != null) {
                    ResolvedSymbol res = resolveImport(importAnn);
                    if (res != null) {
                        activeTree = res.tree;
                        activeNodeId = res.nodeId;
                        isNamespace = res.isNamespace;
                        inferredType = res.typeAnn;
                        if (!isNamespace && activeTree != null && activeNodeId > 0) {
                            if (activeTree.shapeTable.containsKey(activeNodeId)) {
                                inferredType = "@INLINE_SHAPE_NODE:" + activeNodeId;
                            } else {
                                int objLit = findChildObjectLiteral(activeTree, activeNodeId);
                                if (objLit > 0) {
                                    activeNodeId = objLit;
                                    inferredType = "@INLINE_SHAPE_NODE:" + activeNodeId;
                                } else if (inferredType == null) {
                                    inferredType = activeTree.nodeTypeAnn[activeNodeId];
                                }
                            }
                        }
                    }
                }

                if (baseIsMethod && activeTree != null && activeNodeId > 0) {
                    if (activeTree.nodeTypeAnn[activeNodeId] != null && !activeTree.nodeTypeAnn[activeNodeId].isEmpty()) {
                        inferredType = activeTree.nodeTypeAnn[activeNodeId];
                    } else {
                        int retObj = findReturnObjectLiteralNode(activeTree, activeNodeId);
                        if (retObj > 0) {
                            activeNodeId = retObj;
                            inferredType = "@INLINE_SHAPE_NODE:" + retObj;
                        } else {
                            String[] retShape = findReturnShapeDFS(activeTree, activeNodeId);
                            if (retShape != null) {
                                inferredType = "@INLINE_SHAPE:" + String.join(",", retShape);
                            }
                        }
                    }
                }

                if (cachedTree != null && !baseIsMethod) {
                    String narrowed = findNarrowedTypeForSymbol(cachedTree, text, baseToken, dotPos);
                    if (narrowed != null) {
                        inferredType = narrowed;
                    }
                }

                inferredType = normalizeTypeScriptType(inferredType);
            }

            // resolve the chain
            for (int k = 1; k < chain.size(); k++) {
                String member = chain.get(k);

                if (member.equals("!AWAIT")) {
                    if (inferredType != null && inferredType.startsWith("@PROMISE_OF_TS:")) {
                        inferredType = normalizeTypeScriptType(inferredType.substring(15));
                    } else if (inferredType != null && inferredType.startsWith("@PROMISE_INLINE_SHAPE:")) {
                        inferredType = "@INLINE_SHAPE:" + inferredType.substring(22);
                    } else if (inferredType != null && inferredType.equals("@PROMISE")) {
                        inferredType = "@ANY";
                    }
                    continue;
                }

                if (member.startsWith("!THEN:")) {
                    String args = member.substring(6);
                    if (inferredType != null && inferredType.startsWith("@PROMISE_INLINE_SHAPE:")) {
                        if (isArrowIdentity(args)) {
                            // identity
                        } else if (args.contains("=>")) {
                            String afterArrow = args.substring(args.indexOf("=>") + 2).trim();
                            if (afterArrow.startsWith("(") && afterArrow.endsWith(")"))
                                afterArrow = afterArrow.substring(1, afterArrow.length() - 1).trim();
                            if (afterArrow.startsWith("{")) {
                                List<String> keys = extractObjectKeys(afterArrow, true);
                                inferredType = "@PROMISE_INLINE_SHAPE:" + String.join(",", keys);
                            } else inferredType = "@PROMISE";
                        } else inferredType = "@PROMISE";
                    } else if (inferredType != null && inferredType.startsWith("@PROMISE_OF_TS:")) {
                        // Preserves promise wrapper type
                    } else inferredType = "@PROMISE";
                    continue;
                }

                boolean isMethod = member.endsWith("()");
                String memberName = isMethod ? member.substring(0, member.length() - 2) : member;

                if (memberName.startsWith("[")) {
                    if (inferredType != null && inferredType.startsWith("@ARRAY_OF_TS:")) {
                        inferredType = normalizeTypeScriptType(inferredType.substring(13));
                        continue;
                    }
                    if (inferredType != null && inferredType.startsWith("@TUPLE_OF_TS:")) {
                        String tupleData = inferredType.substring(13);
                        String idxStr = memberName.substring(1, memberName.length() - (memberName.endsWith("]") ? 1 : 0)).trim();
                        List<String> elems = splitTopLevelCommas(tupleData);
                        try {
                            int idx = Integer.parseInt(idxStr);
                            if (idx >= 0 && idx < elems.size()) {
                                inferredType = normalizeTypeScriptType(elems.get(idx).trim());
                            } else {
                                inferredType = "@ANY";
                            }
                        } catch (NumberFormatException e) {
                            inferredType = !elems.isEmpty() ? normalizeTypeScriptType(elems.get(0).trim()) : "@ANY";
                        }
                        continue;
                    }
                    if (activeTree != null && activeNodeId > 0) {
                        int childObj = findChildObjectLiteral(activeTree, activeNodeId);
                        if (childObj > 0) {
                            activeNodeId = childObj;
                            inferredType = "@INLINE_SHAPE_NODE:" + childObj;
                            continue;
                        }
                    }
                    if (inferredType != null && inferredType.startsWith("@ARRAY_OF_INLINE_SHAPE:")) {
                        inferredType = "@INLINE_SHAPE:" + inferredType.substring(23);
                    } else if (inferredType != null && inferredType.equals("@ARRAY_OF_NUMBER")) {
                        inferredType = "@NUMBER";
                    } else if (inferredType != null && inferredType.equals("@ARRAY_OF_STRING")) {
                        inferredType = "@STRING";
                    } else if (inferredType != null && inferredType.equals("@ARRAY")) {
                        inferredType = "@ANY";
                    } else {
                        inferredType = "@ANY";
                    }
                    continue;
                }

                if (isNamespace && activeTree != null) {
                    int expNode = findExportInTree(activeTree, memberName);
                    if (expNode > 0) {
                        activeNodeId = expNode;
                        isNamespace = false;
                        if (isMethod) {
                            int retObj = findReturnObjectLiteralNode(activeTree, expNode);
                            if (retObj > 0) {
                                activeNodeId = retObj;
                                inferredType = "@INLINE_SHAPE_NODE:" + retObj;
                            } else {
                                String[] retShape = findReturnShapeDFS(activeTree, expNode);
                                if (retShape != null)
                                    inferredType = "@INLINE_SHAPE:" + String.join(",", retShape);
                                else inferredType = "@ANY";
                            }
                        } else {
                            if (activeTree.nodeTypeAnn[expNode] != null && activeTree.nodeTypeAnn[expNode].startsWith("@ARRAY")) {
                                inferredType = activeTree.nodeTypeAnn[expNode];
                            } else if (activeTree.shapeTable.containsKey(expNode)) {
                                inferredType = "@INLINE_SHAPE_NODE:" + expNode;
                            } else {
                                int objLit = findChildObjectLiteral(activeTree, expNode);
                                if (objLit > 0) {
                                    activeNodeId = objLit;
                                    inferredType = "@INLINE_SHAPE_NODE:" + activeNodeId;
                                } else if (activeTree.nodeTypeAnn[expNode] != null) {
                                    inferredType = activeTree.nodeTypeAnn[expNode];
                                } else {
                                    inferredType = "@ANY";
                                }
                            }
                        }
                    } else {
                        inferredType = "@ANY";
                    }
                    continue;
                }

                if (inferredType != null && inferredType.startsWith("@INLINE_SHAPE_NODE:") && activeTree != null) {
                    int nodeId = activeNodeId > 0 ? activeNodeId : Integer.parseInt(inferredType.substring(19));
                    int nestedNodeId = findPropertyNodeId(activeTree, nodeId, memberName);
                    if (nestedNodeId != 0) {
                        activeNodeId = nestedNodeId;
                        if (isMethod) {
                            int retObj = findReturnObjectLiteralNode(activeTree, nestedNodeId);
                            if (retObj > 0) {
                                activeNodeId = retObj;
                                inferredType = "@INLINE_SHAPE_NODE:" + retObj;
                            } else {
                                String[] retShape = findReturnShapeDFS(activeTree, nestedNodeId);
                                if (retShape != null)
                                    inferredType = "@INLINE_SHAPE:" + String.join(",", retShape);
                                else inferredType = "@ANY";
                            }
                        } else {
                            if (activeTree.nodeTypeAnn[nestedNodeId] != null && activeTree.nodeTypeAnn[nestedNodeId].startsWith("@ARRAY")) {
                                inferredType = activeTree.nodeTypeAnn[nestedNodeId];
                            } else {
                                int childObj = findChildObjectLiteral(activeTree, nestedNodeId);
                                if (childObj != 0) {
                                    activeNodeId = childObj;
                                    inferredType = "@INLINE_SHAPE_NODE:" + childObj;
                                } else if (activeTree.shapeTable.containsKey(nestedNodeId)) {
                                    inferredType = "@INLINE_SHAPE_NODE:" + nestedNodeId;
                                } else if (activeTree.nodeTypeAnn[nestedNodeId] != null) {
                                    inferredType = activeTree.nodeTypeAnn[nestedNodeId];
                                } else {
                                    inferredType = "@ANY";
                                }
                            }
                        }
                    } else {
                        inferredType = "@ANY";
                    }
                    continue;
                }

                if (inferredType != null && inferredType.startsWith("@INLINE_TS_OBJECT:")) {
                    Map<String, String> propMap = extractObjectTypePropertyMap(inferredType.substring(18));
                    String memberType = propMap.get(memberName);
                    if (memberType != null) {
                        inferredType = normalizeTypeScriptType(memberType);
                    } else {
                        inferredType = "@ANY";
                    }
                    continue;
                }

                if (inferredType != null && (inferredType.startsWith("@ARRAY_OF_TS:") || inferredType.startsWith("@TUPLE_OF_TS:"))) {
                    String elemType = inferredType.startsWith("@ARRAY_OF_TS:") ? inferredType.substring(13) : inferredType.substring(13);
                    if (memberName.equals("find") || memberName.equals("pop") || memberName.equals("shift") || memberName.equals("at")) {
                        inferredType = normalizeTypeScriptType(elemType);
                    } else if (memberName.equals("filter") || memberName.equals("slice") || memberName.equals("concat") || memberName.equals("toReversed") || memberName.equals("toSorted")) {
                        // Preserves array wrapper type
                    } else {
                        String nextType = getBuiltinType("@ARRAY", memberName);
                        inferredType = nextType != null ? nextType : "@ANY";
                    }
                    continue;
                }

                if (inferredType != null && !inferredType.startsWith("@") && activeTree != null) {
                    String memberType = resolveMemberTypeFromAst(activeTree, inferredType, memberName);
                    if (memberType != null) {
                        inferredType = normalizeTypeScriptType(memberType);
                        continue;
                    }
                }

                if (inferredType != null && (inferredType.startsWith("@") || inferredType.equals("string") || inferredType.equals("array") || inferredType.equals("number") || inferredType.equals("boolean"))) {
                    String lookupType = inferredType.startsWith("@") ? inferredType : "@" + inferredType.toUpperCase();
                    String elementType = null;
                    if (inferredType.startsWith("@ARRAY_OF_INLINE_SHAPE:")) {
                        lookupType = "@ARRAY";
                        elementType = "@INLINE_SHAPE:" + inferredType.substring(23);
                    } else if (inferredType.equals("@ARRAY_OF_NUMBER")) {
                        lookupType = "@ARRAY";
                        elementType = "@NUMBER";
                    } else if (inferredType.equals("@ARRAY_OF_STRING")) {
                        lookupType = "@ARRAY";
                        elementType = "@STRING";
                    }
                    String nextType = getBuiltinType(lookupType, memberName);
                    if (nextType != null) {
                        if (nextType.equals("@ANY") && elementType != null && memberName.equals("find")) {
                            inferredType = elementType;
                        } else if (nextType.equals("@ARRAY") && elementType != null && (memberName.equals("map") || memberName.equals("filter") || memberName.equals("slice"))) {
                            inferredType = inferredType.startsWith("@ARRAY_OF_") ? inferredType : "@ARRAY";
                        } else {
                            inferredType = nextType;
                        }
                    } else {
                        inferredType = "@ANY";
                    }
                } else {
                    inferredType = "@ANY";
                }
            }

            if (isNamespace && activeTree != null) {
                List<CompletionItem> items = getMembersForNamespace(activeTree, word);
                if (!items.isEmpty()) return fuzzyFilter(items, word);
            }

            if (inferredType != null) {
                List<CompletionItem> typeItems = getTypeMembers(activeTree, inferredType, word);
                if (!typeItems.isEmpty()) return fuzzyFilter(typeItems, word);
            }

            if (inferredType != null && (inferredType.startsWith("@") || inferredType.equals("string") || inferredType.equals("array") || inferredType.equals("number") || inferredType.equals("boolean") || inferredType.equals("promise"))) {
                if (inferredType.startsWith("@PROMISE") || inferredType.equals("promise")) {
                    String[] methods = JsStandardLibrary.PROTOTYPE_METHODS.get("promise");
                    if (methods != null)
                        return buildMemberList("Promise", methods, word, CompletionItem.Type.FUNCTION);
                } else if (inferredType.startsWith("@INLINE_SHAPE:")) {
                    String[] keys = inferredType.substring(14).split(",");
                    List<CompletionItem> shapeMembers = new ArrayList<>();
                    for (String key : keys) {
                        if (!key.isEmpty()) {
                            CompletionItem ci = new CompletionItem(key, key, "Property", CompletionItem.Type.VALUE, 0);
                            ci.setReplaceLength(word.length());
                            shapeMembers.add(ci);
                        }
                    }
                    if (!shapeMembers.isEmpty()) return fuzzyFilter(shapeMembers, word);
                } else if (inferredType.startsWith("@INLINE_SHAPE_NODE:") && activeTree != null) {
                    int targetNode = activeNodeId > 0 ? activeNodeId : Integer.parseInt(inferredType.substring(19));
                    List<CompletionItem> shapeMembers = getMembersForNode(activeTree, targetNode, word);
                    if (!shapeMembers.isEmpty()) return fuzzyFilter(shapeMembers, word);
                } else {
                    String typeName = (inferredType.startsWith("@ARRAY_OF_") || inferredType.startsWith("@TUPLE_OF_")) ? "array" : (inferredType.startsWith("@") ? inferredType.substring(1).toLowerCase() : inferredType.toLowerCase());
                    String[] methods = JsStandardLibrary.PROTOTYPE_METHODS.get(typeName);
                    if (methods != null) {
                        return buildMemberList(typeName, methods, word, CompletionItem.Type.FUNCTION);
                    }
                    if (methods == null && ("any".equals(typeName) || "unknown".equals(typeName) || "undefined".equals(typeName)
                            || "null".equals(typeName) || "never".equals(typeName) || "void".equals(typeName) || "object".equals(typeName))) {
                        String initType = null;
                        if (activeNodeId == 0 && cachedTree != null) {
                            for (int i = 1; i < cachedTree.nodeCount; i++) {
                                if (baseToken.equals(cachedTree.nodeName[i]) &&
                                        (cachedTree.nodeType[i] == JsSyntaxTree.N_VAR_DECL || cachedTree.nodeType[i] == JsSyntaxTree.N_PARAM)) {
                                    activeNodeId = i;
                                    break;
                                }
                            }
                        }
                        if (activeNodeId > 0 && activeNodeId < cachedTree.nodeCount) {
                            int declPos = cachedTree.nodeEnd[activeNodeId];
                            if (declPos > 0 && declPos <= text.length()) {
                                int eqIdx = text.indexOf('=', cachedTree.nodeStart[activeNodeId]);
                                if (eqIdx > 0 && eqIdx < declPos) {
                                    String rhs = text.substring(eqIdx + 1, declPos).trim();
                                    if (rhs.startsWith("\"") || rhs.startsWith("'") || rhs.startsWith("`")) initType = "string";
                                    else if (!rhs.isEmpty() && (Character.isDigit(rhs.charAt(0)) || rhs.startsWith("-"))) initType = "number";
                                    else if (rhs.startsWith("[") || rhs.startsWith("Array.")) initType = "array";
                                    else if (rhs.startsWith("true") || rhs.startsWith("false")) initType = "boolean";
                                    else if (rhs.startsWith("{")) initType = "object";
                                }
                            }
                        }
                        if (initType != null) {
                            String[] specificMethods = JsStandardLibrary.PROTOTYPE_METHODS.get(initType);
                            if (specificMethods != null) {
                                return buildMemberList(initType, specificMethods, word, CompletionItem.Type.FUNCTION);
                            }
                        }
                        methods = new String[]{
                                "toString", "valueOf", "hasOwnProperty", "isPrototypeOf",
                                "propertyIsEnumerable", "toLocaleString", "constructor"
                        };
                        return buildMemberList("Object", methods, word, CompletionItem.Type.FUNCTION);
                    }
                }
            } else if (inferredType != null) {
                if (inferredType.startsWith("module:")) {
                    String path = inferredType.substring(7);
                    List<CompletionItem> exports = ProjectSymbolIndex.getInstance().getExportsForPath(currentFile, path);
                    if (!exports.isEmpty()) return fuzzyFilter(exports, word);
                }

                // Check if it's a class name — return class members
                List<CompletionItem> classMembers = ProjectSymbolIndex.getInstance().getClassMembers(inferredType);
                List<CompletionItem> localMembers = getLocalClassMembers(inferredType);
                if (!classMembers.isEmpty() || !localMembers.isEmpty()) {
                    Map<String, CompletionItem> merged = new LinkedHashMap<>();
                    for (CompletionItem item : localMembers) {
                        String key = getBaseMemberName(item.getLabel());
                        if (!key.isEmpty()) {
                            merged.put(key, item);
                        }
                    }
                    for (CompletionItem item : classMembers) {
                        String key = getBaseMemberName(item.getLabel());
                        if (!key.isEmpty() && !merged.containsKey(key)) {
                            merged.put(key, item);
                        }
                    }
                    List<CompletionItem> result = new ArrayList<>(merged.values());
                    return fuzzyFilter(result, word);
                }

                // Check if it's an interface in activeTree
                if (activeTree != null) {
                    for (int i = 1; i < activeTree.nodeCount; i++) {
                        int id = activeTree.nodesByOffset != null && i < activeTree.nodesByOffset.length ? activeTree.nodesByOffset[i] : i;
                        if (activeTree.nodeType[id] == JsSyntaxTree.N_INTERFACE && inferredType.equals(activeTree.nodeName[id])) {
                            List<CompletionItem> ifaceMembers = new ArrayList<>();
                            int childId = activeTree.nodeChild[id];
                            int childLoop = 0;
                            while (childId > 0 && childId < activeTree.nodeCount && ++childLoop <= activeTree.nodeCount) {
                                if (activeTree.nodeType[childId] == JsSyntaxTree.N_PROPERTY || activeTree.nodeType[childId] == JsSyntaxTree.N_METHOD) {
                                    String mem = activeTree.nodeName[childId];
                                    if (mem != null) {
                                        CompletionItem ci = new CompletionItem(mem, mem, "Interface Member", CompletionItem.Type.VALUE, 0);
                                        ci.setReplaceLength(word.length());
                                        ifaceMembers.add(ci);
                                    }
                                }
                                childId = activeTree.nodeSibling[childId];
                            }
                            if (!ifaceMembers.isEmpty()) return fuzzyFilter(ifaceMembers, word);
                        }
                    }
                }
            }
        }

        // e. Heuristic name-based guess
        String lower = objectToken.toLowerCase();
        for (Map.Entry<String, String[]> entry : JsStandardLibrary.PROTOTYPE_METHODS.entrySet()) {
            String key = entry.getKey();
            if (lower.contains(key) || (key.equals("array") && (lower.contains("arr") || lower.contains("list") || lower.contains("items") || lower.endsWith("s")))
                    || (key.equals("element") && (lower.startsWith("el") || lower.contains("elem") || lower.contains("node") || lower.contains("btn") || lower.contains("div")))) {
                return buildMemberList(key, entry.getValue(), word, CompletionItem.Type.FUNCTION);
            }
        }

        if (!objectToken.isEmpty()) {
            String[] objectMethods = new String[]{
                    "toString", "valueOf", "hasOwnProperty", "isPrototypeOf",
                    "propertyIsEnumerable", "toLocaleString", "constructor"
            };
            return buildMemberList("Object", objectMethods, word, CompletionItem.Type.FUNCTION);
        }

        return new ArrayList<>();
    }

    /**
     * Builds a member completion list.
     * InsertText is ONLY the member name (never "namespace.member") so the cursor-already-past-dot
     * insertion in {@code CodeEditText.insertCompletion} puts the right text after the dot.
     */
    private List<CompletionItem> buildMemberList(String ns, String[] methods, String word, CompletionItem.Type type) {
        if (methods == null) return new ArrayList<>();
        List<CompletionItem> items = new ArrayList<>();
        for (String m : methods) {
            m = m.trim();
            if (m.isEmpty()) continue;
            // Constants (all-caps or known names) don't get parentheses
            boolean isConstant = m.equals(m.toUpperCase()) || m.equals("length") || m.equals("size")
                    || Character.isUpperCase(m.charAt(0));
            // insertText is just the method name — the dot and namespace are already in the doc
            String insert = isConstant ? m : m + "(|)";
            CompletionItem ci = new CompletionItem(m, insert, ns + " member", type, 0);
            ci.setReplaceLength(word != null ? word.length() : 0);
            items.add(ci);
        }
        if (word == null || word.isEmpty()) {
            return items;
        }
        List<CompletionItem> filtered = fuzzyFilter(items, word);
        for (CompletionItem ci : filtered) ci.setReplaceLength(word.length());
        return filtered;
    }

    private List<String> extractChainBeforeDot(String text, int dotPos) {
        List<String> chain = new ArrayList<>();
        int i = dotPos - 1;
        while (i >= 0) {
            while (i >= 0 && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i--;
            if (i >= 0 && text.charAt(i) == '?') {
                i--;
                while (i >= 0 && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i--;
            }
            if (i < 0) break;

            char c = text.charAt(i);

            // String literal: "...", '...', `...`
            if (c == '"' || c == '\'' || c == '`') {
                char quote = c;
                i--;
                while (i >= 0) {
                    if (text.charAt(i) == quote) {
                        int escCount = 0;
                        int e = i - 1;
                        while (e >= 0 && text.charAt(e) == '\\') {
                            escCount++;
                            e--;
                        }
                        if (escCount % 2 == 0) {
                            i--;
                            break;
                        }
                    }
                    i--;
                }
                chain.add(0, "@STRING_LITERAL");
                break;
            }

            // Object literal: { a: 1, ... }
            if (c == '}') {
                int depth = 0;
                int endBrace = i;
                while (i >= 0) {
                    char ch = text.charAt(i);
                    if (ch == '}') depth++;
                    else if (ch == '{') {
                        depth--;
                        if (depth == 0) {
                            String inner = text.substring(i + 1, endBrace).trim();
                            List<String> keys = extractObjectKeys(inner, false);
                            chain.add(0, "@OBJECT_LITERAL:" + String.join(",", keys));
                            i--;
                            break;
                        }
                    }
                    i--;
                }
                break;
            }

            // Array literal: [1, 2, ...] or bracket index access: arr[0]
            if (c == ']') {
                int depth = 0;
                int endBracket = i;
                while (i >= 0) {
                    char ch = text.charAt(i);
                    if (ch == ']') depth++;
                    else if (ch == '[') {
                        depth--;
                        if (depth == 0) {
                            int openPos = i;
                            i--;
                            while (i >= 0 && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i--;
                            if (i >= 0 && (isWordChar(text.charAt(i)) || text.charAt(i) == ')' || text.charAt(i) == ']')) {
                                String inner = text.substring(openPos + 1, endBracket).trim();
                                chain.add(0, "[" + inner + "]");
                            } else {
                                chain.add(0, "@ARRAY_LITERAL");
                                return chain;
                            }
                            break;
                        }
                    }
                    i--;
                }
                if (i < 0) break;
                continue;
            }

            // Number literal: e.g. 123. or 123..
            if (c >= '0' && c <= '9') {
                int end = i + 1;
                while (i >= 0 && ((text.charAt(i) >= '0' && text.charAt(i) <= '9') || text.charAt(i) == '.')) {
                    i--;
                }
                if (i >= 0 && isWordChar(text.charAt(i)) && !(text.charAt(i) >= '0' && text.charAt(i) <= '9')) {
                    i = end - 1; // rewind to let identifier branch handle it
                } else {
                    chain.add(0, "@NUMBER_LITERAL");
                    break;
                }
            }

            // Parenthesized expression or method call
            boolean isMethodCall = false;
            int parenStart = -1, parenEnd = -1;
            if (text.charAt(i) == ')') {
                parenEnd = i;
                int depth = 0;
                while (i >= 0) {
                    char ch = text.charAt(i);
                    if (ch == ')') depth++;
                    else if (ch == '(') {
                        depth--;
                        if (depth == 0) {
                            i--;
                            break;
                        }
                    }
                    i--;
                }
                parenStart = i + 1;
                while (i >= 0 && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i--;

                if (i >= 0 && isWordChar(text.charAt(i))) {
                    isMethodCall = true;
                } else {
                    String inner = text.substring(parenStart + 1, parenEnd).trim();
                    boolean hasAwait = false;
                    if (inner.startsWith("await ")) {
                        hasAwait = true;
                        inner = inner.substring(6).trim();
                    }
                    List<String> innerChain = extractChainBeforeDot(inner + ".", inner.length());
                    if (!innerChain.isEmpty()) {
                        if (hasAwait) {
                            innerChain.add("!AWAIT");
                        }
                        chain.addAll(0, innerChain);
                    } else {
                        chain.add(0, "@EXPR");
                    }
                    break;
                }
            }

            if (!isWordChar(text.charAt(i))) break;
            int end = i + 1;
            while (i >= 0 && isWordChar(text.charAt(i))) i--;

            String identifier = text.substring(i + 1, end);
            if (isMethodCall && identifier.equals("then")) {
                String args = text.substring(parenStart + 1, parenEnd).trim();
                chain.add(0, "!THEN:" + args);
            } else {
                chain.add(0, isMethodCall ? identifier + "()" : identifier);
            }

            while (i >= 0 && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i--;
            if (i >= 0 && text.charAt(i) == '.') {
                i--;
                while (i >= 0 && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i--;
                if (i >= 0 && text.charAt(i) == '?') {
                    i--;
                }
            } else {
                break;
            }
        }
        return chain;
    }

    private String[] findReturnShapeDFS(JsSyntaxTree tree, int nodeId) {
        return findReturnShapeDFS(tree, nodeId, 0);
    }

    private String[] findReturnShapeDFS(JsSyntaxTree tree, int nodeId, int depth) {
        if (tree == null || nodeId <= 0 || nodeId >= tree.nodeCount || depth > 50) return null;
        int child = tree.nodeChild[nodeId];
        int childLoop = 0;
        while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
            if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                String[] shape = tree.shapeTable.get(child);
                if (shape != null) return shape;
            }
            if (tree.nodeType[child] != JsSyntaxTree.N_FUNC_DECL &&
                    tree.nodeType[child] != JsSyntaxTree.N_ARROW_FUNC &&
                    tree.nodeType[child] != JsSyntaxTree.N_METHOD) {
                String[] found = findReturnShapeDFS(tree, child, depth + 1);
                if (found != null) return found;
            }
            child = tree.nodeSibling[child];
        }
        return null;
    }

    private String[] findReturnShapeDFS(int nodeId) {
        return findReturnShapeDFS(cachedTree, nodeId);
    }

    private int findReturnObjectLiteralNode(JsSyntaxTree tree, int nodeId) {
        return findReturnObjectLiteralNode(tree, nodeId, 0);
    }

    private int findReturnObjectLiteralNode(JsSyntaxTree tree, int nodeId, int depth) {
        if (tree == null || nodeId <= 0 || nodeId >= tree.nodeCount || depth > 50) return 0;
        int child = tree.nodeChild[nodeId];
        int childLoop = 0;
        while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
            if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                return child;
            }
            if (tree.nodeType[child] != JsSyntaxTree.N_FUNC_DECL &&
                    tree.nodeType[child] != JsSyntaxTree.N_ARROW_FUNC &&
                    tree.nodeType[child] != JsSyntaxTree.N_METHOD) {
                int found = findReturnObjectLiteralNode(tree, child, depth + 1);
                if (found > 0) return found;
            }
            child = tree.nodeSibling[child];
        }
        return 0;
    }

    private String getShapeOfProperty(JsSyntaxTree tree, int objNodeId, String propName) {
        if (tree == null || objNodeId <= 0 || objNodeId >= tree.nodeCount || propName == null)
            return null;
        int child = tree.nodeChild[objNodeId];
        int childLoop = 0;
        while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
            if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                int propChild = tree.nodeChild[child];
                int propLoop = 0;
                while (propChild > 0 && propChild < tree.nodeCount && ++propLoop <= tree.nodeCount) {
                    if ((tree.nodeType[propChild] == JsSyntaxTree.N_PROPERTY || tree.nodeType[propChild] == JsSyntaxTree.N_STATEMENT)
                            && propName.equals(tree.nodeName[propChild])) {
                        String[] keys = tree.shapeTable.get(propChild);
                        if (keys != null && keys.length > 0) {
                            return "@INLINE_SHAPE:" + String.join(",", keys);
                        }
                    }
                    propChild = tree.nodeSibling[propChild];
                }
            }
            child = tree.nodeSibling[child];
        }
        return null;
    }

    private String getShapeOfProperty(int objNodeId, String propName) {
        return getShapeOfProperty(cachedTree, objNodeId, propName);
    }

    private int findPropertyNodeId(JsSyntaxTree tree, int parentNodeId, String propName) {
        if (tree == null || parentNodeId <= 0 || parentNodeId >= tree.nodeCount || propName == null)
            return 0;
        int target = parentNodeId;
        if (tree.nodeType[target] != JsSyntaxTree.N_OBJECT_LITERAL) {
            int child = tree.nodeChild[target];
            int childLoop = 0;
            while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
                if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                    target = child;
                    break;
                }
                child = tree.nodeSibling[child];
            }
        }

        if (target <= 0 || target >= tree.nodeCount) return 0;
        int propChild = tree.nodeChild[target];
        int propLoop = 0;
        while (propChild > 0 && propChild < tree.nodeCount && ++propLoop <= tree.nodeCount) {
            if ((tree.nodeType[propChild] == JsSyntaxTree.N_PROPERTY ||
                    tree.nodeType[propChild] == JsSyntaxTree.N_METHOD ||
                    tree.nodeType[propChild] == JsSyntaxTree.N_STATEMENT)
                    && propName.equals(tree.nodeName[propChild])) {
                return propChild;
            }
            propChild = tree.nodeSibling[propChild];
        }
        return 0;
    }

    private int findPropertyNodeId(int parentNodeId, String propName) {
        return findPropertyNodeId(cachedTree, parentNodeId, propName);
    }

    private int findChildObjectLiteral(JsSyntaxTree tree, int nodeId) {
        if (tree == null || nodeId <= 0 || nodeId >= tree.nodeCount) return 0;
        if (tree.nodeType[nodeId] == JsSyntaxTree.N_OBJECT_LITERAL) return nodeId;
        int child = tree.nodeChild[nodeId];
        int childLoop = 0;
        while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
            if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                return child;
            }
            child = tree.nodeSibling[child];
        }
        return 0;
    }

    private int findExportInTree(JsSyntaxTree tree, String exportName) {
        return JsExportTable.findExportNode(tree, exportName);
    }

    private ResolvedSymbol resolveImport(String typeAnn) {
        if (typeAnn == null || currentFile == null) return null;

        String modulePath = null;
        String exportName = null;
        boolean isNamespace = false;
        boolean isRequire = false;

        if (typeAnn.startsWith("@IMPORT:")) {
            int colon = typeAnn.indexOf(':', 8);
            if (colon != -1) {
                modulePath = typeAnn.substring(8, colon);
                exportName = typeAnn.substring(colon + 1);
                if ("*".equals(exportName)) {
                    isNamespace = true;
                }
            }
        } else if (typeAnn.startsWith("@REQUIRE_PROP:")) {
            int colon = typeAnn.indexOf(':', 14);
            if (colon != -1) {
                modulePath = typeAnn.substring(14, colon);
                exportName = typeAnn.substring(colon + 1);
            }
        } else if (typeAnn.startsWith("@REQUIRE:")) {
            modulePath = typeAnn.substring(9);
            isRequire = true;
        }

        if (modulePath == null || modulePath.isEmpty()) return null;

        com.cocode.vcode.ide.core.lsp.LspLocation loc =
                com.cocode.vcode.ide.core.lsp.ModuleResolver.resolveModulePath(
                        currentFile.getAbsolutePath(), modulePath);
        if (loc == null || loc.uri == null) return null;

        File targetFile = new File(loc.uri);
        boolean isCached = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getParseResult(loc.uri) != null;
        if (!targetFile.exists() && !isCached) return null;

        if (targetFile.getName().toLowerCase().endsWith(".json")) {
            return resolveJsonFile(targetFile, exportName);
        }

        com.cocode.vcode.ide.core.language.js.ParseResult targetResult =
                com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getOrParseJsFile(targetFile);
        if (targetResult == null || targetResult.tree == null) return null;

        JsSyntaxTree targetTree = targetResult.tree;

        if (isNamespace) {
            return new ResolvedSymbol(targetTree, 0, "@NAMESPACE", true);
        }

        if (isRequire) {
            int exportNode = findExportInTree(targetTree, "default");
            if (exportNode > 0) {
                return new ResolvedSymbol(targetTree, exportNode, targetTree.nodeTypeAnn[exportNode], false);
            }
            return new ResolvedSymbol(targetTree, 0, "@NAMESPACE", true);
        }

        int exportNode = findExportInTree(targetTree, exportName);
        if (exportNode > 0) {
            String ann = targetTree.nodeTypeAnn[exportNode];
            return new ResolvedSymbol(targetTree, exportNode, ann, false);
        }

        return null;
    }

    private ResolvedSymbol resolveJsonFile(File jsonFile, String exportName) {
        try {
            String content = com.cocode.vcode.ide.utils.FileUtils.readFile(jsonFile);
            if (content == null) return null;
            content = content.trim();
            if (content.startsWith("{")) {
                org.json.JSONObject obj = new org.json.JSONObject(content);
                java.util.Iterator<String> keys = obj.keys();
                List<String> keyList = new ArrayList<>();
                while (keys.hasNext()) keyList.add(keys.next());
                if (!keyList.isEmpty()) {
                    if (exportName != null && !exportName.equals("default") && !exportName.equals("*")) {
                        Object val = obj.opt(exportName);
                        if (val instanceof org.json.JSONObject) {
                            org.json.JSONObject subObj = (org.json.JSONObject) val;
                            java.util.Iterator<String> subKeys = subObj.keys();
                            List<String> subKeyList = new ArrayList<>();
                            while (subKeys.hasNext()) subKeyList.add(subKeys.next());
                            return new ResolvedSymbol(null, 0, "@INLINE_SHAPE:" + String.join(",", subKeyList), false);
                        } else if (val instanceof org.json.JSONArray) {
                            return new ResolvedSymbol(null, 0, "@ARRAY", false);
                        } else if (val instanceof String) {
                            return new ResolvedSymbol(null, 0, "@STRING", false);
                        } else if (val instanceof Number) {
                            return new ResolvedSymbol(null, 0, "@NUMBER", false);
                        } else if (val instanceof Boolean) {
                            return new ResolvedSymbol(null, 0, "@BOOLEAN", false);
                        }
                    }
                    return new ResolvedSymbol(null, 0, "@INLINE_SHAPE:" + String.join(",", keyList), false);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // Class member helpers

    private List<CompletionItem> getMembersForNode(JsSyntaxTree tree, int nodeId, String word) {
        if (tree == null || nodeId <= 0) return new ArrayList<>();
        if (tree.nodeType[nodeId] == JsSyntaxTree.N_CLASS_DECL) {
            String className = tree.nodeName[nodeId];
            if (className == null || className.isEmpty()) className = "Class";
            List<CompletionItem> classMembers = buildClassMemberItems(tree, nodeId, className);
            if (word != null && !word.isEmpty()) {
                for (CompletionItem ci : classMembers) ci.setReplaceLength(word.length());
            }
            return classMembers;
        }
        List<CompletionItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String[] shape = tree.shapeTable.get(nodeId);
        if (shape != null) {
            for (String key : shape) {
                if (key != null && !key.isEmpty() && seen.add(key)) {
                    CompletionItem ci = new CompletionItem(key, key, "Property", CompletionItem.Type.VALUE, 0);
                    ci.setReplaceLength(word != null ? word.length() : 0);
                    items.add(ci);
                }
            }
        }

        int objLit = findChildObjectLiteral(tree, nodeId);
        if (objLit > 0) {
            String[] litShape = tree.shapeTable.get(objLit);
            if (litShape != null) {
                for (String key : litShape) {
                    if (key != null && !key.isEmpty() && seen.add(key)) {
                        CompletionItem ci = new CompletionItem(key, key, "Property", CompletionItem.Type.VALUE, 0);
                        ci.setReplaceLength(word != null ? word.length() : 0);
                        items.add(ci);
                    }
                }
            }
            int pChild = tree.nodeChild[objLit];
            int pChildLoop = 0;
            while (pChild > 0 && pChild < tree.nodeCount && ++pChildLoop <= tree.nodeCount) {
                String propName = tree.nodeName[pChild];
                if (propName != null && !propName.isEmpty() && seen.add(propName)) {
                    int pType = tree.nodeType[pChild];
                    boolean isFunc = (pType == JsSyntaxTree.N_METHOD || pType == JsSyntaxTree.N_FUNC_DECL);
                    CompletionItem.Type cType = isFunc ? CompletionItem.Type.FUNCTION : CompletionItem.Type.VALUE;
                    String detail = isFunc ? "Method" : "Property";
                    String insert = isFunc ? propName + "(|)" : propName;
                    CompletionItem ci = new CompletionItem(propName, insert, detail, cType, 0);
                    ci.setReplaceLength(word != null ? word.length() : 0);
                    items.add(ci);
                }
                pChild = tree.nodeSibling[pChild];
            }
        } else if (tree.nodeType[nodeId] == JsSyntaxTree.N_CLASS_DECL) {
            int cChild = tree.nodeChild[nodeId];
            int cChildLoop = 0;
            while (cChild > 0 && cChild < tree.nodeCount && ++cChildLoop <= tree.nodeCount) {
                String mName = tree.nodeName[cChild];
                if (mName != null && !mName.isEmpty() && !"constructor".equals(mName) && seen.add(mName)) {
                    int cType = tree.nodeType[cChild];
                    boolean isFunc = (cType == JsSyntaxTree.N_METHOD);
                    CompletionItem.Type ciType = isFunc ? CompletionItem.Type.FUNCTION : CompletionItem.Type.VALUE;
                    String insert = isFunc ? mName + "(|)" : mName;
                    CompletionItem ci = new CompletionItem(mName, insert, "Method", ciType, 0);
                    ci.setReplaceLength(word != null ? word.length() : 0);
                    items.add(ci);
                }
                cChild = tree.nodeSibling[cChild];
            }
        }

        return items;
    }

    private List<CompletionItem> getMembersForNamespace(JsSyntaxTree tree, String word) {
        if (tree == null) return new ArrayList<>();
        List<CompletionItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_EXPORT) {
                int child = tree.nodeChild[i];
                int childLoop = 0;
                while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
                    String name = tree.nodeName[child];
                    if (name != null && !name.isEmpty() && seen.add(name)) {
                        int cType = tree.nodeType[child];
                        CompletionItem.Type type = (cType == JsSyntaxTree.N_FUNC_DECL || cType == JsSyntaxTree.N_ARROW_FUNC)
                                ? CompletionItem.Type.FUNCTION
                                : (cType == JsSyntaxTree.N_CLASS_DECL ? CompletionItem.Type.KEYWORD : CompletionItem.Type.VALUE);
                        CompletionItem ci = new CompletionItem(name, name, "Export", type, 0);
                        ci.setReplaceLength(word != null ? word.length() : 0);
                        items.add(ci);
                    }
                    child = tree.nodeSibling[child];
                }
            }
        }
        return items;
    }

    private String extractObjectBeforeDot(String text, int dotPos) {
        List<String> chain = extractChainBeforeDot(text, dotPos);
        if (chain.isEmpty()) return "";
        String last = chain.get(chain.size() - 1);
        if (last.endsWith("()")) last = last.substring(0, last.length() - 2);
        return last;
    }

    /**
     * Returns class members for `this.` by finding which class body the cursor is inside.
     */
    private List<CompletionItem> getCurrentClassMembers(int dotPos) {
        if (cachedTree == null || cachedScopeTree == null) return new ArrayList<>();

        int scopeId = cachedScopeTree.findScopeAt(dotPos, cachedTree);
        int nodeId = cachedScopeTree.scopeNode[scopeId];

        int current = nodeId;
        while (current > 0) {
            if (cachedTree.nodeType[current] == JsSyntaxTree.N_CLASS_DECL) {
                String className = cachedTree.nodeName[current];
                if (className == null || className.isEmpty()) className = "this";
                return buildClassMemberItems(cachedTree, current, className);
            }
            current = cachedTree.nodeParent[current];
        }
        return new ArrayList<>();
    }

    /**
     * Resolves class members directly from the AST and shape table for a named class.
     */
    private List<CompletionItem> getLocalClassMembers(String className) {
        if (cachedTree == null) return new ArrayList<>();
        for (int i = 1; i < cachedTree.nodeCount; i++) {
            if (cachedTree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL && className.equals(cachedTree.nodeName[i])) {
                return buildClassMemberItems(cachedTree, i, className);
            }
        }
        return new ArrayList<>();
    }

    private List<CompletionItem> buildClassMemberItems(JsSyntaxTree tree, int classNodeId, String className) {
        List<CompletionItem> items = new ArrayList<>();
        if (tree == null || classNodeId <= 0 || classNodeId >= tree.nodeCount) return items;

        Set<String> seen = new HashSet<>();
        collectClassMembers(tree, classNodeId, className, seen, items, 0);

        // Also check shapeTable for any this.prop assignments or dynamic properties
        String[] shape = tree.shapeTable.get(classNodeId);
        if (shape != null) {
            for (String key : shape) {
                if (key != null && !key.isEmpty() && !isIgnoredClassMember(key) && seen.add(key)) {
                    items.add(new CompletionItem(key, key, className + " property", CompletionItem.Type.VALUE, 0));
                }
            }
        }
        return items;
    }

    private void collectClassMembers(JsSyntaxTree tree, int classNodeId, String targetClassName,
                                     Set<String> seen, List<CompletionItem> items, int depth) {
        if (depth > 10 || classNodeId <= 0 || classNodeId >= tree.nodeCount) return;

        // Direct methods and properties of this class
        int child = tree.nodeChild[classNodeId];
        while (child > 0 && child < tree.nodeCount) {
            int type = tree.nodeType[child];
            String name = tree.nodeName[child];
            if (name != null && !name.isEmpty() && !seen.contains(name) && !isIgnoredClassMember(name)) {
                if (type == JsSyntaxTree.N_METHOD) {
                    seen.add(name);
                    items.add(new CompletionItem(name, name + "(|)", targetClassName + " method", CompletionItem.Type.FUNCTION, 0));
                } else if (type == JsSyntaxTree.N_PROPERTY || type == JsSyntaxTree.N_GETTER || type == JsSyntaxTree.N_SETTER) {
                    seen.add(name);
                    items.add(new CompletionItem(name, name, targetClassName + " property", CompletionItem.Type.VALUE, 0));
                }
            }
            child = tree.nodeSibling[child];
        }

        // If this class extends another class, resolve the base class and collect inherited members
        String superName = tree.nodeTypeAnn[classNodeId];
        if (superName != null && !superName.isEmpty()) {
            for (int i = 1; i < tree.nodeCount; i++) {
                if (tree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL && superName.equals(tree.nodeName[i])) {
                    collectClassMembers(tree, i, targetClassName, seen, items, depth + 1);
                    break;
                }
            }
        }
    }

    // Document symbol indexing
    protected void ensureDocumentIndexed(String text) {
        int len = text.length();
        int sample = (len > 0) ? ((text.charAt(0) << 16) ^ (text.charAt(len / 2) << 8) ^ text.charAt(len - 1)) : 0;
        int hash = len ^ (sample << 11) ^ text.hashCode();
        if (hash == lastTextHash) return;
        lastTextHash = hash;
        varTypeMap.clear();
        cachedGenericSymbols.clear();

        cachedTokens = JsLexer.tokenize(text);
        cachedTree = JsParser.parseFull(text, cachedTokens);
        if (cachedTree != null) {
            cachedTree.buildNodesByOffset();
        }
        cachedScopeTree = ScopeTree.build(cachedTree);

        Set<String> builtinNames = new HashSet<>();
        for (CompletionItem item : builtinItems) builtinNames.add(item.getLabel());

        if (cachedTree != null) {
            for (int id = 1; id < cachedTree.nodeCount; id++) {
                int type = cachedTree.nodeType[id];
                String name = cachedTree.nodeName[id];
                if (name == null || name.isEmpty() || builtinNames.contains(name)) continue;

                if (type == JsSyntaxTree.N_VAR_DECL || type == JsSyntaxTree.N_PARAM) {
                    if (cachedTree.nodeTypeAnn[id] != null) {
                        varTypeMap.put(name, cachedTree.nodeTypeAnn[id]);
                    } else {
                        String jsDocType = extractJsDocType(text, cachedTree.nodeStart[id]);
                        if (jsDocType != null) {
                            varTypeMap.put(name, jsDocType.toLowerCase());
                        } else {
                            int declPos = cachedTree.nodeStart[id] + (name != null ? name.length() : 0);
                            inferVariableType(text, declPos, name, text.length());
                        }
                    }
                } else if (type == JsSyntaxTree.N_IMPORT) {
                    int child = cachedTree.nodeChild[id];
                    int childLoop = 0;
                    while (child > 0 && child < cachedTree.nodeCount && ++childLoop <= cachedTree.nodeCount) {
                        String cName = cachedTree.nodeName[child];
                        if (cName != null && !cName.isEmpty()) {
                            cachedGenericSymbols.add(new CompletionItem(cName, cName, "Module", CompletionItem.Type.KEYWORD, 0));
                        }
                        child = cachedTree.nodeSibling[child];
                    }
                }
            }
        }

        List<int[]> classRanges = new ArrayList<>();
        Set<String> classMembers = new HashSet<>();
        if (cachedTree != null) {
            for (int i = 1; i < cachedTree.nodeCount; i++) {
                if (cachedTree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL || cachedTree.nodeType[i] == JsSyntaxTree.N_INTERFACE) {
                    classRanges.add(new int[]{cachedTree.nodeStart[i], cachedTree.nodeEnd[i]});
                }
            }
            if (cachedTree.shapeTable != null) {
                for (String[] members : cachedTree.shapeTable.values()) {
                    if (members != null) {
                        for (String m : members) {
                            if (m != null && !m.isEmpty()) classMembers.add(m);
                        }
                    }
                }
            }
        }

        if (cachedTokens != null) {
            for (int t = 0; t < cachedTokens.length; t++) {
                if (cachedTokens.types[t] == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_IDENTIFIER) {
                    int start = cachedTokens.tokenStart[t];

                    // Skip property access and private member access (e.g. this.name, obj.foo, #bar)
                    int pre = start - 1;
                    while (pre >= 0 && Character.isWhitespace(text.charAt(pre))) pre--;
                    if (pre >= 0 && (text.charAt(pre) == '.' || text.charAt(pre) == '#')) {
                        continue;
                    }

                    // Skip tokens defined inside class/interface bodies so they do not leak into global scope
                    boolean insideClass = false;
                    for (int[] range : classRanges) {
                        if (start >= range[0] && start <= range[1]) {
                            insideClass = true;
                            break;
                        }
                    }
                    if (insideClass) continue;

                    int end = (t + 1 < cachedTokens.length) ? cachedTokens.tokenStart[t + 1] : text.length();
                    while (end > start && !isWordChar(text.charAt(end - 1))) end--;
                    if (end - start >= 3) {
                        String w = text.substring(start, end);
                        if (!builtinNames.contains(w)
                                && !classMembers.contains(w)
                                && (cachedScopeTree == null || !cachedScopeTree.symbols.containsKey(w))) {
                            cachedGenericSymbols.add(new CompletionItem(w, w, "Word", CompletionItem.Type.VALUE, 0));
                        }
                    }
                }
            }
        }
    }

    private String extractJsDocType(String text, int declStart) {
        int limit = Math.max(0, declStart - 2000);
        String window = text.substring(limit, declStart);
        int commentEndInWindow = window.lastIndexOf("*/");
        if (commentEndInWindow != -1) {
            int commentEnd = limit + commentEndInWindow;
            String between = text.substring(commentEnd + 2, declStart);
            if (between.trim().isEmpty()) {
                int commentStartInWindow = window.lastIndexOf("/**", commentEndInWindow);
                if (commentStartInWindow != -1) {
                    int commentStart = limit + commentStartInWindow;
                    String jsdoc = text.substring(commentStart, commentEnd);
                    Matcher m = PAT_JSDOC_TYPE.matcher(jsdoc);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
        }
        return null;
    }

    private void inferVariableType(String text, int afterDeclEnd, String varName, int scanLimit) {
        if (afterDeclEnd >= scanLimit) return;
        int end = Math.min(afterDeclEnd + 120, scanLimit);
        String snippet = text.substring(afterDeclEnd, end);

        int stop = snippet.indexOf(';');
        if (stop != -1) snippet = snippet.substring(0, stop);
        stop = snippet.indexOf('\n');
        if (stop != -1) snippet = snippet.substring(0, stop);
        snippet = snippet.trim();

        if (snippet.startsWith("=")) {
            snippet = snippet.substring(1).trim();
            if (snippet.startsWith("[") || snippet.startsWith("Array.from"))
                varTypeMap.put(varName, "array");
            else if (snippet.startsWith("\"") || snippet.startsWith("'") || snippet.startsWith("`"))
                varTypeMap.put(varName, "string");
            else if (snippet.startsWith("{"))
                varTypeMap.put(varName, "object");
            else if (snippet.startsWith("new ")) {
                String afterNew = snippet.substring(4).trim();
                int paren = afterNew.indexOf('(');
                String cls = paren != -1 ? afterNew.substring(0, paren).trim() : afterNew;
                if (!cls.isEmpty() && isWordChar(cls.charAt(0))) {
                    varTypeMap.put(varName, cls);
                }
            } else if (snippet.startsWith("new Promise")) varTypeMap.put(varName, "promise");
            else if (snippet.startsWith("fetch(") || snippet.startsWith("axios"))
                varTypeMap.put(varName, "promise");
            else if (snippet.startsWith("new Map")) varTypeMap.put(varName, "map");
            else if (snippet.startsWith("new Set")) varTypeMap.put(varName, "set");
            else if (snippet.startsWith("new Date")) varTypeMap.put(varName, "date");
            else if (snippet.startsWith("new RegExp") || snippet.startsWith("/"))
                varTypeMap.put(varName, "regexp");
            else if (snippet.contains("new IntersectionObserver"))
                varTypeMap.put(varName, "IntersectionObserver");
            else if (snippet.contains("new ResizeObserver"))
                varTypeMap.put(varName, "ResizeObserver");
            else if (snippet.contains("new MutationObserver"))
                varTypeMap.put(varName, "MutationObserver");
            else if (snippet.contains("new WebSocket")) varTypeMap.put(varName, "WebSocket");
            else if (snippet.contains("new Worker")) varTypeMap.put(varName, "Worker");
            else if (snippet.contains("new BroadcastChannel"))
                varTypeMap.put(varName, "BroadcastChannel");
            else if (snippet.contains("new AbortController"))
                varTypeMap.put(varName, "AbortController");
            else if (snippet.contains("new URL(")) varTypeMap.put(varName, "URL");
            else if (snippet.contains("new URLSearchParams"))
                varTypeMap.put(varName, "URLSearchParams");
            else if (snippet.contains("new FormData")) varTypeMap.put(varName, "FormData");
            else if (snippet.contains("new Headers")) varTypeMap.put(varName, "Headers");
            else if (snippet.contains("new FileReader")) varTypeMap.put(varName, "filereader");
            else if (snippet.contains("new Blob")) varTypeMap.put(varName, "blob");
            else if (snippet.contains("new File(")) varTypeMap.put(varName, "file");
            else if (snippet.contains("getContext('2d')") || snippet.contains("getContext(\"2d\")"))
                varTypeMap.put(varName, "canvascontext");
            else if (snippet.startsWith("document.querySelector") || snippet.startsWith("document.getElementById") || snippet.startsWith("document.createElement"))
                varTypeMap.put(varName, "element");
            else if (snippet.startsWith("document.querySelectorAll") || snippet.startsWith("document.getElementsBy"))
                varTypeMap.put(varName, "nodelist");
            else if (snippet.contains(".filter(") || snippet.contains(".map(") || snippet.contains(".slice(") || snippet.contains(".concat(") || snippet.contains(".flat(") || snippet.contains("Array.from"))
                varTypeMap.put(varName, "array");
            else if (snippet.contains(".then(")) varTypeMap.put(varName, "promise");
            else if (snippet.contains(".split(")) varTypeMap.put(varName, "array");
            else if (snippet.contains(".toString(") || snippet.contains(".trim(") || snippet.contains(".replace("))
                varTypeMap.put(varName, "string");
            else if (!snippet.isEmpty() && Character.isDigit(snippet.charAt(0)))
                varTypeMap.put(varName, "number");
        }
    }

    public JsSyntaxTree getCachedTree() {
        return cachedTree;
    }

    public ScopeTree getCachedScopeTree() {
        return cachedScopeTree;
    }

    private int findTokenIndexAtOffset(com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens, int cursorPos) {
        if (tokens == null || tokens.length == 0) return -1;
        if (cursorPos < 0) return -1;
        if (cursorPos < tokens.length) return cursorPos;
        if (cursorPos - 1 >= 0 && cursorPos - 1 < tokens.length) return cursorPos - 1;
        return tokens.length - 1;
    }

    public static Map<String, String> extractObjectTypePropertyMap(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null) return map;
        String s = text.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.isEmpty()) return map;

        int n = s.length();
        int depthBrace = 0;
        int depthParen = 0;
        int depthBracket = 0;
        int depthAngle = 0;
        boolean inQuote = false;
        char quoteChar = 0;

        int memberStart = 0;

        for (int i = 0; i <= n; i++) {
            char c = i < n ? s.charAt(i) : ';';

            if (inQuote) {
                if (c == quoteChar && s.charAt(i - 1) != '\\') {
                    inQuote = false;
                }
                continue;
            }

            if (c == '\'' || c == '"' || c == '`') {
                inQuote = true;
                quoteChar = c;
                continue;
            }

            if (c == '{') depthBrace++;
            else if (c == '}' && depthBrace > 0) depthBrace--;
            else if (c == '(') depthParen++;
            else if (c == ')' && depthParen > 0) depthParen--;
            else if (c == '[') depthBracket++;
            else if (c == ']' && depthBracket > 0) depthBracket--;
            else if (c == '<') depthAngle++;
            else if (c == '>' && depthAngle > 0) depthAngle--;

            if ((c == ';' || c == ',' || c == '\n' || i == n) && depthBrace == 0 && depthParen == 0 && depthBracket == 0 && depthAngle == 0) {
                String member = s.substring(memberStart, i).trim();
                memberStart = i + 1;
                if (member.isEmpty()) continue;

                if (member.startsWith("readonly ")) {
                    member = member.substring(9).trim();
                }

                int parenIdx = member.indexOf('(');
                int colonIdx = member.indexOf(':');

                if (parenIdx >= 0 && (colonIdx < 0 || parenIdx < colonIdx)) {
                    String propName = member.substring(0, parenIdx).trim();
                    if (propName.endsWith("?")) propName = propName.substring(0, propName.length() - 1).trim();
                    if (!propName.isEmpty()) {
                        int closeParen = member.lastIndexOf(')');
                        String retType = "@FUNCTION";
                        if (closeParen >= 0 && closeParen < member.length()) {
                            int retColon = member.indexOf(':', closeParen);
                            if (retColon >= 0) {
                                retType = member.substring(retColon + 1).trim();
                            }
                        }
                        map.put(propName, retType);
                    }
                } else if (colonIdx >= 0) {
                    String propName = member.substring(0, colonIdx).trim();
                    if (propName.endsWith("?")) {
                        propName = propName.substring(0, propName.length() - 1).trim();
                    }
                    if ((propName.startsWith("'") && propName.endsWith("'")) ||
                            (propName.startsWith("\"") && propName.endsWith("\""))) {
                        propName = propName.substring(1, propName.length() - 1);
                    }
                    String propType = member.substring(colonIdx + 1).trim();
                    if (!propName.isEmpty()) {
                        map.put(propName, propType);
                    }
                }
            }
        }
        return map;
    }

    public static String normalizeTypeScriptType(String typeAnn) {
        if (typeAnn == null) return null;
        String t = typeAnn.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("@")) return t;

        while (t.startsWith("(") && t.endsWith(")")) {
            t = t.substring(1, t.length() - 1).trim();
        }

        if (t.contains("|")) {
            String[] parts = t.split("\\|");
            String nonNullPart = null;
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("null") && !trimmed.equals("undefined") && !trimmed.equals("void")) {
                    nonNullPart = trimmed;
                    break;
                }
            }
            if (nonNullPart != null) {
                t = nonNullPart;
            }
        }

        while (t.startsWith("(") && t.endsWith(")")) {
            t = t.substring(1, t.length() - 1).trim();
        }

        t = t.replaceAll("\\[\\s*\\]", "[]");

        if (t.endsWith("[]")) {
            String element = t.substring(0, t.length() - 2).trim();
            return "@ARRAY_OF_TS:" + element;
        }

        if ((t.startsWith("Array<") || t.startsWith("ReadonlyArray<")) && t.endsWith(">")) {
            int start = t.indexOf('<');
            String element = t.substring(start + 1, t.length() - 1).trim();
            return "@ARRAY_OF_TS:" + element;
        }

        // Tuple type: [ string, number ]
        if (t.startsWith("[") && t.endsWith("]")) {
            String inner = t.substring(1, t.length() - 1).trim();
            if (inner.isEmpty()) {
                return "@ARRAY";
            }
            List<String> elems = splitTopLevelCommas(inner);
            if (elems.isEmpty()) {
                return "@ARRAY";
            }
            return "@TUPLE_OF_TS:" + String.join(",", elems);
        }

        if (t.startsWith("Promise<") && t.endsWith(">")) {
            String element = t.substring(8, t.length() - 1).trim();
            return "@PROMISE_OF_TS:" + element;
        }

        if (t.startsWith("{") && t.endsWith("}")) {
            return "@INLINE_TS_OBJECT:" + t;
        }

        if (t.equals("string")) return "@STRING";
        if (t.equals("number")) return "@NUMBER";
        if (t.equals("boolean")) return "@BOOLEAN";
        if (t.equals("any")) return "@ANY";
        if (t.equals("unknown")) return "@UNKNOWN";
        if (t.equals("never") || t.equals("void")) return "@ANY";
        if (t.equals("null")) return "@NULL";
        if (t.equals("undefined")) return "@UNDEFINED";

        if (t.endsWith("Element") || t.equals("Element") || t.equals("Node") || t.equals("Document")) {
            return "@DOM_ELEMENT:" + t;
        }

        if (t.endsWith("Event") || t.equals("Event")) {
            return "@DOM_EVENT:" + t;
        }

        if (t.equals("Response") || t.equals("Request") || t.equals("Headers") || t.equals("FormData") ||
                t.equals("URL") || t.equals("URLSearchParams") || t.equals("Map") || t.equals("Set") ||
                t.equals("Date") || t.equals("RegExp")) {
            return "@" + t.toUpperCase();
        }

        return t;
    }

    public static List<String> splitTopLevelCommas(String text) {
        List<String> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return list;

        int start = 0;
        int angleDepth = 0;
        int squareDepth = 0;
        int curlyDepth = 0;
        int parenDepth = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') angleDepth++;
            else if (c == '>') { if (angleDepth > 0) angleDepth--; }
            else if (c == '[') squareDepth++;
            else if (c == ']') { if (squareDepth > 0) squareDepth--; }
            else if (c == '{') curlyDepth++;
            else if (c == '}') { if (curlyDepth > 0) curlyDepth--; }
            else if (c == '(') parenDepth++;
            else if (c == ')') { if (parenDepth > 0) parenDepth--; }
            else if (c == ',' && angleDepth == 0 && squareDepth == 0 && curlyDepth == 0 && parenDepth == 0) {
                String part = text.substring(start, i).trim();
                if (!part.isEmpty()) list.add(part);
                start = i + 1;
            }
        }
        if (start < text.length()) {
            String part = text.substring(start).trim();
            if (!part.isEmpty()) list.add(part);
        }
        return list;
    }

    private String findNarrowedTypeForSymbol(JsSyntaxTree tree, String source, String symbol, int offset) {
        if (tree == null || source == null || symbol == null || offset <= 0 || offset > source.length()) return null;

        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            if (type == JsSyntaxTree.N_IF_STMT || type == JsSyntaxTree.N_WHILE_STMT) {
                int start = tree.nodeStart[i];
                int end = tree.nodeEnd[i];
                if (start < offset && (end <= 0 || offset <= end)) {
                    String narrowed = inspectConditionForTypeGuard(tree, source, i, symbol, offset);
                    if (narrowed != null) return narrowed;
                }
            }
        }
        return null;
    }

    private String inspectConditionForTypeGuard(JsSyntaxTree tree, String source, int ifNode, String symbol, int offset) {
        int ifStart = tree.nodeStart[ifNode];
        int openParen = source.indexOf('(', ifStart);
        if (openParen < 0 || openParen >= offset) return null;

        int closeParen = -1;
        int depth = 0;
        int maxScan = Math.min(source.length(), ifStart + 2000);
        for (int p = openParen; p < maxScan; p++) {
            char c = source.charAt(p);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) {
                    closeParen = p;
                    break;
                }
            }
        }
        if (closeParen < 0 || offset <= closeParen) return null;

        String cond = source.substring(openParen + 1, closeParen).trim();
        if (!cond.contains(symbol)) return null;

        boolean isElseBranch = false;
        int elseIdx = source.indexOf("else", closeParen);
        if (elseIdx > 0 && elseIdx < offset && (tree.nodeEnd[ifNode] <= 0 || elseIdx < tree.nodeEnd[ifNode])) {
            isElseBranch = true;
        }

        return evaluateTypeGuardCondition(cond, symbol, isElseBranch);
    }

    private String evaluateTypeGuardCondition(String cond, String symbol, boolean isElseBranch) {
        String typeofPattern = "typeof\\s+" + java.util.regex.Pattern.quote(symbol) + "\\s*(===?|!==?)\\s*['\"]([a-zA-Z]+)['\"]";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(typeofPattern).matcher(cond);
        if (m.find()) {
            String op = m.group(1);
            String type = m.group(2);
            boolean isEq = op.startsWith("==");
            if ((isEq && !isElseBranch) || (!isEq && isElseBranch)) {
                return mapTypeofResultToType(type);
            }
        }

        String reverseTypeof = "['\"]([a-zA-Z]+)['\"]\\s*(===?|!==?)\\s*typeof\\s+" + java.util.regex.Pattern.quote(symbol);
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(reverseTypeof).matcher(cond);
        if (m2.find()) {
            String type = m2.group(1);
            String op = m2.group(2);
            boolean isEq = op.startsWith("==");
            if ((isEq && !isElseBranch) || (!isEq && isElseBranch)) {
                return mapTypeofResultToType(type);
            }
        }

        String instPattern = java.util.regex.Pattern.quote(symbol) + "\\s+instanceof\\s+([A-Za-z0-9_$]+)";
        java.util.regex.Matcher mInst = java.util.regex.Pattern.compile(instPattern).matcher(cond);
        if (mInst.find() && !isElseBranch) {
            return mInst.group(1);
        }

        String arrayPattern = "Array\\.isArray\\s*\\(\\s*" + java.util.regex.Pattern.quote(symbol) + "\\s*\\)";
        if (java.util.regex.Pattern.compile(arrayPattern).matcher(cond).find() && !isElseBranch) {
            return "@ARRAY";
        }

        return null;
    }

    private static String mapTypeofResultToType(String typeStr) {
        if ("string".equals(typeStr)) return "@STRING";
        if ("number".equals(typeStr)) return "@NUMBER";
        if ("boolean".equals(typeStr)) return "@BOOLEAN";
        if ("function".equals(typeStr)) return "@FUNCTION";
        if ("object".equals(typeStr)) return "@OBJECT";
        if ("undefined".equals(typeStr)) return "@UNDEFINED";
        if ("symbol".equals(typeStr)) return "@SYMBOL";
        if ("bigint".equals(typeStr)) return "@BIGINT";
        return "@ANY";
    }

    public List<CompletionItem> getEnumMembers(JsSyntaxTree tree, int enumNodeId, String word) {
        List<CompletionItem> members = new ArrayList<>();
        if (tree == null || enumNodeId <= 0) return members;
        int childId = tree.nodeChild[enumNodeId];
        int childLoop = 0;
        while (childId > 0 && childId < tree.nodeCount && ++childLoop <= tree.nodeCount) {
            if (tree.nodeType[childId] == JsSyntaxTree.N_PROPERTY) {
                String mem = tree.nodeName[childId];
                if (mem != null && !mem.isEmpty()) {
                    CompletionItem ci = new CompletionItem(mem, mem, "Enum Member", CompletionItem.Type.VALUE, 0);
                    ci.setReplaceLength(word.length());
                    members.add(ci);
                }
            }
            childId = tree.nodeSibling[childId];
        }
        return members;
    }

    public List<CompletionItem> getInterfaceMembers(JsSyntaxTree tree, int ifaceNodeId, String word) {
        Map<String, CompletionItem> outMap = new LinkedHashMap<>();
        Set<Integer> visited = new HashSet<>();
        collectInterfaceMembers(tree, ifaceNodeId, outMap, visited, word);
        return new ArrayList<>(outMap.values());
    }

    private void collectInterfaceMembers(JsSyntaxTree tree, int ifaceNodeId, Map<String, CompletionItem> outMap, Set<Integer> visited, String word) {
        if (tree == null || ifaceNodeId <= 0 || !visited.add(ifaceNodeId)) return;
        int childId = tree.nodeChild[ifaceNodeId];
        int childLoop = 0;
        while (childId > 0 && childId < tree.nodeCount && ++childLoop <= tree.nodeCount) {
            if (tree.nodeType[childId] == JsSyntaxTree.N_PROPERTY || tree.nodeType[childId] == JsSyntaxTree.N_METHOD) {
                String mem = tree.nodeName[childId];
                if (mem != null && !mem.isEmpty() && !outMap.containsKey(mem)) {
                    CompletionItem.Type type = tree.nodeType[childId] == JsSyntaxTree.N_METHOD ? CompletionItem.Type.FUNCTION : CompletionItem.Type.VALUE;
                    CompletionItem ci = new CompletionItem(mem, mem, "Interface Member", type, 0);
                    ci.setReplaceLength(word.length());
                    outMap.put(mem, ci);
                }
            }
            childId = tree.nodeSibling[childId];
        }
        String extendsName = tree.nodeTypeAnn[ifaceNodeId];
        if (extendsName != null && !extendsName.isEmpty()) {
            for (int i = 1; i < tree.nodeCount; i++) {
                int id = tree.nodesByOffset != null && i < tree.nodesByOffset.length ? tree.nodesByOffset[i] : i;
                if ((tree.nodeType[id] == JsSyntaxTree.N_INTERFACE || tree.nodeType[id] == JsSyntaxTree.N_CLASS_DECL) &&
                        extendsName.equals(tree.nodeName[id])) {
                    collectInterfaceMembers(tree, id, outMap, visited, word);
                    break;
                }
            }
        }
    }

    public List<CompletionItem> getTypeMembers(JsSyntaxTree tree, String typeName, String word) {
        if (typeName == null || typeName.isEmpty()) return Collections.emptyList();

        if (typeName.startsWith("@INLINE_TS_OBJECT:")) {
            Map<String, String> propMap = extractObjectTypePropertyMap(typeName.substring(18));
            List<CompletionItem> items = new ArrayList<>();
            for (Map.Entry<String, String> entry : propMap.entrySet()) {
                String name = entry.getKey();
                String propType = entry.getValue();
                CompletionItem.Type type = propType.equals("@FUNCTION") ? CompletionItem.Type.FUNCTION : CompletionItem.Type.VALUE;
                CompletionItem ci = new CompletionItem(name, name, propType, type, 0);
                ci.setReplaceLength(word.length());
                items.add(ci);
            }
            return items;
        }

        if (typeName.startsWith("@DOM_EVENT:")) {
            String evName = typeName.substring(11);
            List<CompletionItem> items = new ArrayList<>();
            if ("MouseEvent".equals(evName) || "PointerEvent".equals(evName)) {
                String[] mouseProps = {"clientX", "clientY", "pageX", "pageY", "screenX", "screenY", "button", "buttons", "altKey", "ctrlKey", "shiftKey", "metaKey"};
                for (String p : mouseProps) {
                    CompletionItem ci = new CompletionItem(p, p, "Property", CompletionItem.Type.VALUE, 0);
                    ci.setReplaceLength(word.length());
                    items.add(ci);
                }
            } else if ("KeyboardEvent".equals(evName)) {
                String[] keyProps = {"key", "code", "keyCode", "altKey", "ctrlKey", "shiftKey", "metaKey", "repeat"};
                for (String p : keyProps) {
                    CompletionItem ci = new CompletionItem(p, p, "Property", CompletionItem.Type.VALUE, 0);
                    ci.setReplaceLength(word.length());
                    items.add(ci);
                }
            }
            String[] baseEvents = JsStandardLibrary.PROTOTYPE_METHODS.get("event");
            if (baseEvents != null) {
                for (String m : baseEvents) {
                    CompletionItem ci = new CompletionItem(m, m, "Event Method", CompletionItem.Type.FUNCTION, 0);
                    ci.setReplaceLength(word.length());
                    items.add(ci);
                }
            }
            return items;
        }

        if (typeName.startsWith("@DOM_ELEMENT:")) {
            String[] elemMethods = JsStandardLibrary.PROTOTYPE_METHODS.get("element");
            if (elemMethods != null) {
                return buildMemberList("Element", elemMethods, word, CompletionItem.Type.FUNCTION);
            }
        }

        if (tree != null) {
            for (int i = 1; i < tree.nodeCount; i++) {
                int id = tree.nodesByOffset != null && i < tree.nodesByOffset.length ? tree.nodesByOffset[i] : i;
                if (typeName.equals(tree.nodeName[id])) {
                    if (tree.nodeType[id] == JsSyntaxTree.N_INTERFACE) {
                        return getInterfaceMembers(tree, id, word);
                    } else if (tree.nodeType[id] == JsSyntaxTree.N_ENUM) {
                        return getEnumMembers(tree, id, word);
                    } else if (tree.nodeType[id] == JsSyntaxTree.N_TYPE_ALIAS) {
                        String aliasAnn = tree.nodeTypeAnn[id];
                        if (aliasAnn != null) {
                            return getTypeMembers(tree, normalizeTypeScriptType(aliasAnn), word);
                        }
                    } else if (tree.nodeType[id] == JsSyntaxTree.N_CLASS_DECL) {
                        return buildClassMemberItems(tree, id, typeName);
                    }
                }
            }
        }

        List<CompletionItem> classMembers = ProjectSymbolIndex.getInstance().getClassMembers(typeName);
        if (!classMembers.isEmpty()) {
            return classMembers;
        }

        return Collections.emptyList();
    }

    private String resolveMemberTypeFromAst(JsSyntaxTree tree, String typeName, String memberName) {
        if (tree == null || typeName == null || memberName == null) return null;

        for (int i = 1; i < tree.nodeCount; i++) {
            int id = tree.nodesByOffset != null && i < tree.nodesByOffset.length ? tree.nodesByOffset[i] : i;
            if (typeName.equals(tree.nodeName[id])) {
                if (tree.nodeType[id] == JsSyntaxTree.N_INTERFACE || tree.nodeType[id] == JsSyntaxTree.N_CLASS_DECL) {
                    int childId = tree.nodeChild[id];
                    int childLoop = 0;
                    while (childId > 0 && childId < tree.nodeCount && ++childLoop <= tree.nodeCount) {
                        if (memberName.equals(tree.nodeName[childId])) {
                            return tree.nodeTypeAnn[childId];
                        }
                        childId = tree.nodeSibling[childId];
                    }
                    String extendsName = tree.nodeTypeAnn[id];
                    if (extendsName != null && !extendsName.isEmpty()) {
                        String parentResolved = resolveMemberTypeFromAst(tree, extendsName, memberName);
                        if (parentResolved != null) return parentResolved;
                    }
                } else if (tree.nodeType[id] == JsSyntaxTree.N_TYPE_ALIAS) {
                    String aliasAnn = tree.nodeTypeAnn[id];
                    if (aliasAnn != null) {
                        Map<String, String> propMap = extractObjectTypePropertyMap(aliasAnn);
                        String propType = propMap.get(memberName);
                        if (propType != null) return propType;
                    }
                }
            }
        }
        return null;
    }

    private static class ResolvedSymbol {
        final JsSyntaxTree tree;
        final int nodeId;
        final String typeAnn;
        final boolean isNamespace;

        ResolvedSymbol(JsSyntaxTree tree, int nodeId, String typeAnn, boolean isNamespace) {
            this.tree = tree;
            this.nodeId = nodeId;
            this.typeAnn = typeAnn;
            this.isNamespace = isNamespace;
        }
    }
}
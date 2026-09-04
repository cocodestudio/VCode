package com.cocode.vcode.ide.core.language.js;

import android.content.Context;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.autocomplete.VFSManager;
import com.cocode.vcode.ide.core.completion.staticdata.JsStaticCompletionDispatcher;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.data.repository.ProjectRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    // JSDoc type pattern
    private static final Pattern PAT_JSDOC_TYPE = Pattern.compile("@(?:type|returns?|param)\\s*\\{([^}]+)\\}");

    private static final Map<String, String> BuiltinTypeTable = new HashMap<>();
    static {
        // String methods -> return types
        BuiltinTypeTable.put("@STRING.toLowerCase", "@STRING");
        BuiltinTypeTable.put("@STRING.toUpperCase", "@STRING");
        BuiltinTypeTable.put("@STRING.trim", "@STRING");
        BuiltinTypeTable.put("@STRING.trimStart", "@STRING");
        BuiltinTypeTable.put("@STRING.trimEnd", "@STRING");
        BuiltinTypeTable.put("@STRING.slice", "@STRING");
        BuiltinTypeTable.put("@STRING.substring", "@STRING");
        BuiltinTypeTable.put("@STRING.substr", "@STRING");
        BuiltinTypeTable.put("@STRING.charAt", "@STRING");
        BuiltinTypeTable.put("@STRING.charCodeAt", "@NUMBER");
        BuiltinTypeTable.put("@STRING.codePointAt", "@NUMBER");
        BuiltinTypeTable.put("@STRING.indexOf", "@NUMBER");
        BuiltinTypeTable.put("@STRING.lastIndexOf", "@NUMBER");
        BuiltinTypeTable.put("@STRING.includes", "@BOOLEAN");
        BuiltinTypeTable.put("@STRING.startsWith", "@BOOLEAN");
        BuiltinTypeTable.put("@STRING.endsWith", "@BOOLEAN");
        BuiltinTypeTable.put("@STRING.repeat", "@STRING");
        BuiltinTypeTable.put("@STRING.replace", "@STRING");
        BuiltinTypeTable.put("@STRING.replaceAll", "@STRING");
        BuiltinTypeTable.put("@STRING.padStart", "@STRING");
        BuiltinTypeTable.put("@STRING.padEnd", "@STRING");
        BuiltinTypeTable.put("@STRING.concat", "@STRING");
        BuiltinTypeTable.put("@STRING.split", "@ARRAY");
        BuiltinTypeTable.put("@STRING.match", "@ARRAY");
        BuiltinTypeTable.put("@STRING.search", "@NUMBER");
        BuiltinTypeTable.put("@STRING.at", "@STRING");
        BuiltinTypeTable.put("@STRING.length", "@NUMBER");

        // Array methods -> return types
        BuiltinTypeTable.put("@ARRAY.map", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.filter", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.slice", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.concat", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.flat", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.flatMap", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.reverse", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.sort", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.join", "@STRING");
        BuiltinTypeTable.put("@ARRAY.find", "@ANY");
        BuiltinTypeTable.put("@ARRAY.findIndex", "@NUMBER");
        BuiltinTypeTable.put("@ARRAY.findLast", "@ANY");
        BuiltinTypeTable.put("@ARRAY.findLastIndex", "@NUMBER");
        BuiltinTypeTable.put("@ARRAY.indexOf", "@NUMBER");
        BuiltinTypeTable.put("@ARRAY.lastIndexOf", "@NUMBER");
        BuiltinTypeTable.put("@ARRAY.includes", "@BOOLEAN");
        BuiltinTypeTable.put("@ARRAY.some", "@BOOLEAN");
        BuiltinTypeTable.put("@ARRAY.every", "@BOOLEAN");
        BuiltinTypeTable.put("@ARRAY.push", "@NUMBER");
        BuiltinTypeTable.put("@ARRAY.pop", "@ANY");
        BuiltinTypeTable.put("@ARRAY.shift", "@ANY");
        BuiltinTypeTable.put("@ARRAY.unshift", "@NUMBER");
        BuiltinTypeTable.put("@ARRAY.splice", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.fill", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.copyWithin", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.at", "@ANY");
        BuiltinTypeTable.put("@ARRAY.forEach", "@UNDEFINED");
        BuiltinTypeTable.put("@ARRAY.reduce", "@ANY");
        BuiltinTypeTable.put("@ARRAY.reduceRight", "@ANY");
        BuiltinTypeTable.put("@ARRAY.length", "@NUMBER");

        // Number methods -> return types
        BuiltinTypeTable.put("@NUMBER.toFixed", "@STRING");
        BuiltinTypeTable.put("@NUMBER.toString", "@STRING");
        BuiltinTypeTable.put("@NUMBER.toPrecision", "@STRING");
        BuiltinTypeTable.put("@NUMBER.toExponential", "@STRING");
        BuiltinTypeTable.put("@NUMBER.toLocaleString", "@STRING");

        // Promise methods
        BuiltinTypeTable.put("@PROMISE.then", "@PROMISE");
        BuiltinTypeTable.put("@PROMISE.catch", "@PROMISE");
        BuiltinTypeTable.put("@PROMISE.finally", "@PROMISE");
        // Boolean methods
        BuiltinTypeTable.put("@BOOLEAN.toString", "@STRING");
        BuiltinTypeTable.put("@BOOLEAN.valueOf", "@BOOLEAN");
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
        loadKeywords();
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
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
        } catch (Exception ignored) {}
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

    // Main entry point
    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || fullText.isEmpty()) return new ArrayList<>();
        ensureDocumentIndexed(fullText);
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length())
            return new ArrayList<>();

        String word = getWordBeforeCursor(fullText, cursorPos);

    // 1. Import / require path completion
        List<CompletionItem> importItems = getImportPathSuggestions(fullText, cursorPos);
        if (importItems != null) return importItems;

    // 1c. Import block completion
        List<CompletionItem> importExport = getImportExportSuggestions(fullText, cursorPos, word);
        if (importExport != null) return importExport;

    // 1b. Event name string completions (addEventListener/removeEventListener/on) & DOM queries
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

        if (cachedTokens != null && cursorPos >= 0 && cursorPos < cachedTokens.length) {
            byte type = cachedTokens.types[cursorPos];
            if (type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_COMMENT ||
                type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_STRING ||
                type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_TEMPLATE) {
                if (cursorPos != cachedTokens.tokenStart[cursorPos]) {
                    return new ArrayList<>();
                }
            }
        }

    // 2. Dot-member completion
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

    // 2b. Object literal key completion
        // If we're inside an object literal (after { or ,) suggest known keys
        List<CompletionItem> objKeys = getObjectLiteralSuggestions(fullText, cursorPos, word);
        if (objKeys != null) return objKeys;

        if (word.isEmpty()) {
            if (cursorPos > 0 && fullText.charAt(cursorPos - 1) == '(') {
                return new ArrayList<>();
            }
        }

        // 3. General: keywords + user symbols
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
            case JsSyntaxTree.N_CLASS_DECL: return CompletionItem.Type.KEYWORD;
            case JsSyntaxTree.N_FUNC_DECL:
            case JsSyntaxTree.N_ARROW_FUNC:
            case JsSyntaxTree.N_METHOD: return CompletionItem.Type.FUNCTION;
            default: return CompletionItem.Type.VALUE;
        }
    }
    
    private String getDetailForKind(int kind) {
        switch (kind) {
            case JsSyntaxTree.N_CLASS_DECL: return "Class";
            case JsSyntaxTree.N_FUNC_DECL:
            case JsSyntaxTree.N_ARROW_FUNC:
            case JsSyntaxTree.N_METHOD: return "Function";
            case JsSyntaxTree.N_PARAM: return "Parameter";
            default: return "Variable";
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

    // Object literal key suggestions
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

        // Verify we're actually inside an object literal by checking brace balance
        // and ensuring this isn't a code block (function body, if block, etc.)
        // Code blocks are preceded by ) (if/for/while), else, or start a function body
        if (preceding == '{') {
            // Walk back to see if this { is a code block or an object literal
            int j = i - 1;
            while (j >= 0 && Character.isWhitespace(fullText.charAt(j))) j--;
            if (j >= 0) {
                char beforeBrace = fullText.charAt(j);
                // Code block indicators
                if (beforeBrace == ')' || beforeBrace == '>')
                    return null; // arrow function body or if/for
                // Check for keywords that indicate code blocks
                String context = fullText.substring(Math.max(0, j - 10), j + 1).trim();
                if (context.endsWith("else") || context.endsWith("try") || context.endsWith("catch")
                        || context.endsWith("finally") || context.endsWith("do")) {
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

    /**
     * Finds the position of the opening { for the object literal we're currently in.
     */
    private int findMatchingBrace(String text, int cursorPos) {
        int depth = 0;
        for (int i = cursorPos - 1; i >= 0; i--) {
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
            } catch (Exception ignored) {}
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
                for (int child = tree.nodeChild[i]; child != 0; child = tree.nodeSibling[child]) {
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
     *         the two files are the same
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

    // Dot-member completion
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
                inferredType = varTypeMap.get(baseToken);
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
                                    while (child > 0) {
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
            
            // resolve the chain
            for (int k = 1; k < chain.size(); k++) {
                String member = chain.get(k);
                
                if (member.equals("!AWAIT")) {
                    if (inferredType != null && inferredType.startsWith("@PROMISE_INLINE_SHAPE:")) {
                        inferredType = "@INLINE_SHAPE:" + inferredType.substring(22);
                    } else if (inferredType != null && inferredType.equals("@PROMISE")) {
                        inferredType = "@ANY";
                    }
                    continue;
                }
                
                if (member.startsWith("!THEN:")) {
                    String args = member.substring(6);
                    if (inferredType != null && inferredType.startsWith("@PROMISE_INLINE_SHAPE:")) {
                        if (args.matches(".*?=>\\s*[a-zA-Z_$][\\w$]*\\s*")) {
                            // identity
                        } else if (args.contains("=>")) {
                            String afterArrow = args.substring(args.indexOf("=>") + 2).trim();
                            if (afterArrow.startsWith("(") && afterArrow.endsWith(")")) afterArrow = afterArrow.substring(1, afterArrow.length() - 1).trim();
                            if (afterArrow.startsWith("{")) {
                                Matcher m = Pattern.compile("([a-zA-Z_$][\\w$]*)\\s*[:=]").matcher(afterArrow);
                                List<String> keys = new ArrayList<>();
                                while (m.find()) keys.add(m.group(1));
                                inferredType = "@PROMISE_INLINE_SHAPE:" + String.join(",", keys);
                            } else inferredType = "@PROMISE";
                        } else inferredType = "@PROMISE";
                    } else inferredType = "@PROMISE";
                    continue;
                }

                boolean isMethod = member.endsWith("()");
                String memberName = isMethod ? member.substring(0, member.length() - 2) : member;
                
                if (memberName.startsWith("[")) {
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
                                if (retShape != null) inferredType = "@INLINE_SHAPE:" + String.join(",", retShape);
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
                                if (retShape != null) inferredType = "@INLINE_SHAPE:" + String.join(",", retShape);
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
                    String nextType = BuiltinTypeTable.get(lookupType + "." + memberName);
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

            if (inferredType != null && (inferredType.startsWith("@") || inferredType.equals("string") || inferredType.equals("array") || inferredType.equals("number") || inferredType.equals("boolean") || inferredType.equals("promise"))) {
                if (inferredType.startsWith("@PROMISE") || inferredType.equals("promise")) {
                    String[] methods = JsStandardLibrary.PROTOTYPE_METHODS.get("promise");
                    if (methods != null) return buildMemberList("Promise", methods, word, CompletionItem.Type.FUNCTION);
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
                    String typeName = inferredType.startsWith("@ARRAY_OF_") ? "array" : (inferredType.startsWith("@") ? inferredType.substring(1).toLowerCase() : inferredType.toLowerCase());
                    String[] methods = JsStandardLibrary.PROTOTYPE_METHODS.get(typeName);
                    if (methods != null) {
                        return buildMemberList(typeName, methods, word, CompletionItem.Type.FUNCTION);
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
                if (!localMembers.isEmpty()) classMembers.addAll(localMembers);
                if (!classMembers.isEmpty()) return fuzzyFilter(classMembers, word);
                
                // Check if it's an interface in activeTree
                if (activeTree != null) {
                    for (int i = 1; i < activeTree.nodeCount; i++) {
                        int id = activeTree.nodesByOffset != null && i < activeTree.nodesByOffset.length ? activeTree.nodesByOffset[i] : i;
                        if (activeTree.nodeType[id] == JsSyntaxTree.N_INTERFACE && inferredType.equals(activeTree.nodeName[id])) {
                            List<CompletionItem> ifaceMembers = new ArrayList<>();
                            int childId = activeTree.nodeChild[id];
                            while (childId > 0) {
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
                            Matcher m = Pattern.compile("([a-zA-Z_$][\\w$]*)\\s*:").matcher(inner);
                            List<String> keys = new ArrayList<>();
                            while (m.find()) {
                                String k = m.group(1);
                                if (!keys.contains(k)) keys.add(k);
                            }
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
                        if (depth == 0) { i--; break; }
                    }
                    i--;
                }
                parenStart = i + 1;
                while (i >= 0 && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i--;

                if (i >= 0 && isWordChar(text.charAt(i))) {
                    isMethodCall = true;
                } else {
                    String inner = text.substring(parenStart + 1, parenEnd).trim();
                    List<String> innerChain = extractChainBeforeDot(inner + ".", inner.length() + 1);
                    if (!innerChain.isEmpty()) {
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
            } else {
                break;
            }
        }
        return chain;
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

    private String[] findReturnShapeDFS(JsSyntaxTree tree, int nodeId) {
        if (tree == null || nodeId <= 0) return null;
        int child = tree.nodeChild[nodeId];
        while (child > 0) {
            if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                String[] shape = tree.shapeTable.get(child);
                if (shape != null) return shape;
            }
            if (tree.nodeType[child] != JsSyntaxTree.N_FUNC_DECL && 
                tree.nodeType[child] != JsSyntaxTree.N_ARROW_FUNC && 
                tree.nodeType[child] != JsSyntaxTree.N_METHOD) {
                String[] found = findReturnShapeDFS(tree, child);
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
        if (tree == null || nodeId <= 0) return 0;
        int child = tree.nodeChild[nodeId];
        while (child > 0) {
            if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                return child;
            }
            if (tree.nodeType[child] != JsSyntaxTree.N_FUNC_DECL && 
                tree.nodeType[child] != JsSyntaxTree.N_ARROW_FUNC && 
                tree.nodeType[child] != JsSyntaxTree.N_METHOD) {
                int found = findReturnObjectLiteralNode(tree, child);
                if (found > 0) return found;
            }
            child = tree.nodeSibling[child];
        }
        return 0;
    }

    private String getShapeOfProperty(JsSyntaxTree tree, int objNodeId, String propName) {
        if (tree == null || objNodeId <= 0 || propName == null) return null;
        int child = tree.nodeChild[objNodeId];
        while (child > 0) {
            if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                int propChild = tree.nodeChild[child];
                while (propChild > 0) {
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
        if (tree == null || parentNodeId <= 0 || propName == null) return 0;
        int target = parentNodeId;
        if (tree.nodeType[target] != JsSyntaxTree.N_OBJECT_LITERAL) {
            int child = tree.nodeChild[target];
            while (child > 0) {
                if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                    target = child;
                    break;
                }
                child = tree.nodeSibling[child];
            }
        }

        int propChild = tree.nodeChild[target];
        while (propChild > 0) {
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
        if (tree == null || nodeId <= 0) return 0;
        if (tree.nodeType[nodeId] == JsSyntaxTree.N_OBJECT_LITERAL) return nodeId;
        int child = tree.nodeChild[nodeId];
        while (child > 0) {
            if (tree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                return child;
            }
            child = tree.nodeSibling[child];
        }
        return 0;
    }

    private int findExportInTree(JsSyntaxTree tree, String exportName) {
        if (tree == null) return 0;

        if ("default".equals(exportName) || exportName == null) {
            for (int i = 1; i < tree.nodeCount; i++) {
                if (tree.nodeType[i] == JsSyntaxTree.N_EXPORT && "default".equals(tree.nodeName[i])) {
                    int child = tree.nodeChild[i];
                    if (child > 0) {
                        if (tree.nodeType[child] == JsSyntaxTree.N_IDENTIFIER) {
                            int topId = findTopLevelDeclInTree(tree, tree.nodeName[child]);
                            return topId > 0 ? topId : child;
                        }
                        return child;
                    }
                    return i;
                }
            }
            int cjs = findCommonJsExportNode(tree);
            if (cjs > 0) return cjs;
            return 0;
        }

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_EXPORT) {
                int child = tree.nodeChild[i];
                while (child > 0) {
                    String cName = tree.nodeName[child];
                    if (exportName.equals(cName)) {
                        if (tree.nodeType[child] == JsSyntaxTree.N_IDENTIFIER) {
                            int topId = findTopLevelDeclInTree(tree, cName);
                            return topId > 0 ? topId : child;
                        }
                        return child;
                    }
                    child = tree.nodeSibling[child];
                }
            }
        }

        int topDecl = findTopLevelDeclInTree(tree, exportName);
        if (topDecl > 0) return topDecl;

        return 0;
    }

    private int findTopLevelDeclInTree(JsSyntaxTree tree, String name) {
        if (tree == null || name == null) return 0;
        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            if (type == JsSyntaxTree.N_VAR_DECL || type == JsSyntaxTree.N_FUNC_DECL 
                    || type == JsSyntaxTree.N_CLASS_DECL || type == JsSyntaxTree.N_INTERFACE) {
                if (name.equals(tree.nodeName[i])) {
                    return i;
                }
            }
        }
        return 0;
    }

    private int findCommonJsExportNode(JsSyntaxTree tree) {
        if (tree == null) return 0;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_OBJECT_LITERAL) {
                return i;
            }
        }
        return 0;
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
        } catch (Exception ignored) {}
        return null;
    }

    private List<CompletionItem> getMembersForNode(JsSyntaxTree tree, int nodeId, String word) {
        if (tree == null || nodeId <= 0) return new ArrayList<>();
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
            while (pChild > 0) {
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
            while (cChild > 0) {
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
                while (child > 0) {
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

    // Class member helpers
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
                String[] shape = cachedTree.shapeTable.get(current);
                if (shape != null) {
                    List<CompletionItem> members = new ArrayList<>();
                    for (String key : shape) {
                        members.add(new CompletionItem(key, key, "Property", CompletionItem.Type.VALUE, 0));
                    }
                    return members;
                }
                break;
            }
            current = cachedTree.nodeParent[current];
        }
        return new ArrayList<>();
    }

    /**
     * Parses class members (methods + this.prop assignments) directly from the document text.
     */
    private List<CompletionItem> getLocalClassMembers(String className) {
        if (cachedTree == null) return new ArrayList<>();
        for (int i = 1; i < cachedTree.nodeCount; i++) {
            if (cachedTree.nodeType[i] == JsSyntaxTree.N_CLASS_DECL && className.equals(cachedTree.nodeName[i])) {
                String[] shape = cachedTree.shapeTable.get(i);
                if (shape != null) {
                    List<CompletionItem> members = new ArrayList<>();
                    for (String key : shape) {
                        members.add(new CompletionItem(key, key, "Property", CompletionItem.Type.VALUE, 0));
                    }
                    return members;
                }
                break;
            }
        }
        return new ArrayList<>();
    }


    // Document symbol indexing
    private void ensureDocumentIndexed(String text) {
        int hash = text.hashCode();
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
                    while (child > 0) {
                        String cName = cachedTree.nodeName[child];
                        if (cName != null && !cName.isEmpty()) {
                            cachedGenericSymbols.add(new CompletionItem(cName, cName, "Module", CompletionItem.Type.KEYWORD, 0));
                        }
                        child = cachedTree.nodeSibling[child];
                    }
                }
            }
        }

        if (cachedTokens != null) {
            for (int t = 0; t < cachedTokens.length; t++) {
                if (cachedTokens.types[t] == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_IDENTIFIER) {
                    int start = cachedTokens.tokenStart[t];
                    int end = (t + 1 < cachedTokens.length) ? cachedTokens.tokenStart[t + 1] : text.length();
                    while (end > start && !isWordChar(text.charAt(end - 1))) end--;
                    if (end - start >= 3) {
                        String w = text.substring(start, end);
                        if (!builtinNames.contains(w) && (cachedScopeTree == null || !cachedScopeTree.symbols.containsKey(w))) {
                            cachedGenericSymbols.add(new CompletionItem(w, w, "Word", CompletionItem.Type.VALUE, 0));
                        }
                    }
                }
            }
        }
    }

    private String extractJsDocType(String text, int declStart) {
        int limit = Math.max(0, declStart - 500);
        int commentEnd = text.lastIndexOf("*/", declStart);
        if (commentEnd > limit) {
            String between = text.substring(commentEnd + 2, declStart);
            if (between.trim().isEmpty()) {
                int commentStart = text.lastIndexOf("/**", commentEnd);
                if (commentStart >= limit) {
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
            }
            else if (snippet.startsWith("new Promise")) varTypeMap.put(varName, "promise");
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
            else if (snippet.matches("^\\d.*")) varTypeMap.put(varName, "number");
        }
    }
}
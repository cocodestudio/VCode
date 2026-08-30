package com.cocode.vcode.ide.core.language.js;

import android.content.Context;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.autocomplete.VFSManager;
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

    // Regex patterns
    private static final Pattern PAT_USER_DECL = Pattern.compile(
            "function\\s+([a-zA-Z_$][\\w$]*)\\s*\\("           // named function
                    + "|class\\s+([a-zA-Z_$][\\w$]*)"                   // class
                    + "|(?:const|let|var)\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[a-zA-Z_$][\\w$]*)\\s*=>" // arrow fn
                    + "|(?:const|let|var)\\s+([a-zA-Z_$][\\w$]*)");     // variable

    private static final Pattern PAT_WORD = Pattern.compile("[a-zA-Z_$][\\w$]+");

    /**
     * Detects cursor inside addEventListener(' or on( string argument for event names
     */
    private static final Pattern PAT_EVENT_STRING = Pattern.compile(
            "(?:addEventListener|removeEventListener|on)\\s*\\(\\s*['\"]([^'\"]*?)$");

    private static final Pattern PAT_GET_ELEMENT_BY_ID = Pattern.compile("getElementById\\s*\\(\\s*['\"]([^'\"]*?)$");
    private static final Pattern PAT_QUERY_SELECTOR = Pattern.compile("querySelector(?:All)?\\s*\\(\\s*['\"]([^'\"]*?)$");
    private static final Pattern PAT_JSDOC_TYPE = Pattern.compile("@(?:type|returns?|param)\\s*\\{([^}]+)\\}");
    private static final Pattern PAT_CLASS_IN_SCOPE = Pattern.compile(
            "class\\s+([a-zA-Z_$][\\w$]*)(?:\\s+extends\\s+[\\w$]+)?\\s*\\{");
    private static final Pattern PAT_NEW_INSTANCE = Pattern.compile(
            "(?:const|let|var)\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*new\\s+([a-zA-Z_$][\\w$]*)\\s*\\(");
    private static final Pattern PAT_IMPORT_AS = Pattern.compile(
            "\\bimport\\s+\\{[^{}]*\\bas\\s+\\w*$");
    private static final Pattern PAT_IMPORT_STAR = Pattern.compile("import\\s+\\*\\s+as\\s+([a-zA-Z_$][\\w$]*)\\s+from\\s+['\"]([^'\"]+)['\"]");

    private static final Map<String, String> BuiltinTypeTable = new HashMap<>();
    static {
        BuiltinTypeTable.put("@ARRAY.map", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.filter", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.slice", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.concat", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.flat", "@ARRAY");
        BuiltinTypeTable.put("@ARRAY.join", "@STRING");
        BuiltinTypeTable.put("@ARRAY.find", "@ANY");
        BuiltinTypeTable.put("@ARRAY.forEach", "@UNDEFINED");
        BuiltinTypeTable.put("@STRING.split", "@ARRAY");
        BuiltinTypeTable.put("@STRING.toUpperCase", "@STRING");
        BuiltinTypeTable.put("@STRING.trim", "@STRING");
        BuiltinTypeTable.put("@STRING.slice", "@STRING");
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
        }
    }

    // Keyword loading
    private void loadKeywords() {
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

    // 1b. Event name string completions (addEventListener/removeEventListener)
        String lineBefore = getLineBeforeCursor(fullText, cursorPos);
        Matcher eventMatcher = PAT_EVENT_STRING.matcher(lineBefore);
        if (eventMatcher.find()) {
            String typedEvent = eventMatcher.group(1);
            List<CompletionItem> eventItems = new ArrayList<>();
            for (String ev : JsStandardLibrary.EVENT_NAMES) {
                eventItems.add(new CompletionItem(ev, ev, "DOM Event", CompletionItem.Type.VALUE, 0));
            }
            return fuzzyFilter(eventItems, typedEvent);
        }

        Matcher idMatcher = PAT_GET_ELEMENT_BY_ID.matcher(lineBefore);
        if (idMatcher.find()) {
            String typedId = idMatcher.group(1);
            List<CompletionItem> ids = ProjectSymbolIndex.getInstance().getHtmlIdItems();
            return fuzzyFilter(ids, typedId);
        }

        Matcher queryMatcher = PAT_QUERY_SELECTOR.matcher(lineBefore);
        if (queryMatcher.find()) {
            String typedQuery = queryMatcher.group(1);
            List<CompletionItem> prefixed = getCompletionItems(typedQuery);
            return fuzzyFilter(prefixed, typedQuery);
        }

        if (cachedTokens != null && cursorPos > 0 && cursorPos <= cachedTokens.length) {
            byte type = cachedTokens.types[cursorPos - 1];
            if (type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_COMMENT ||
                type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_STRING ||
                type == com.cocode.vcode.ide.core.diagnostic.util.TokenStream.TK_TEMPLATE) {
                return new ArrayList<>();
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
        List<CompletionItem> all = new ArrayList<>(builtinItems);
        Set<String> added = new HashSet<>();
        for (CompletionItem item : builtinItems) added.add(item.getLabel());

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
            Matcher keyMatcher = Pattern.compile("([a-zA-Z_$][\\w$]*)\\s*[,:]").matcher(objContent);
            while (keyMatcher.find()) {
                usedKeys.add(keyMatcher.group(1));
            }
        }

        // Collect common object property names from the document
        List<CompletionItem> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>(usedKeys);

        // Extract all object keys in the document
        Matcher objKeyMatcher = Pattern.compile("([a-zA-Z_$][\\w$]*)\\s*:(?!=)").matcher(fullText);
        int limit = Math.min(fullText.length(), 100_000);
        while (objKeyMatcher.find() && objKeyMatcher.start() < limit) {
            String key = objKeyMatcher.group(1);
            if (Objects.requireNonNull(key).length() > 1 && seen.add(key)) {
                items.add(new CompletionItem(key, key, "Object key", CompletionItem.Type.VALUE, 0));
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
        String before = fullText.substring(0, cursorPos);

        // Detect `import { ... as ` — user is typing an alias name → no autocomplete restriction
        if (PAT_IMPORT_AS.matcher(before).find()) {
            // Alias is a free identifier; suggest nothing (let them type freely)
            return new ArrayList<>();
        }

        // Detect cursor inside `import { ... }` block (including after commas)
        // Pattern: import { [already typed names,] [currentWord]
        Matcher mBefore = Pattern.compile("import\\s+\\{[^{}]*$").matcher(before);
        if (!mBefore.find()) return null;

        // Find the closing `} from 'path'` after the cursor. K.2: if
        // there is no `from '...'` clause yet, fall back to a
        // project-wide prefix query against the export table.
        String after = fullText.substring(cursorPos);
        Matcher mAfter = Pattern.compile("^[^{}]*\\}\\s*from\\s*['\"]([^'\"]+)['\"]").matcher(after);
        List<CompletionItem> exports;
        if (mAfter.find()) {
            exports = getExportsFromPath(mAfter.group(1));
        } else {
            exports = getExportsProjectWide(word);
        }
        if (exports == null || exports.isEmpty()) return null;

        // Collect names already imported in this block so we don't re-suggest them
        Set<String> used = getStrings(mBefore, word);

        List<CompletionItem> filtered = new ArrayList<>();
        for (CompletionItem e : exports) {
            if (!used.contains(e.getEffectiveInsertText())) filtered.add(e);
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
        com.cocode.vcode.ide.core.lsp.LspLocation loc =
                com.cocode.vcode.ide.core.lsp.ModuleResolver.resolveModulePath(
                        currentFile.getAbsolutePath(), path);
        if (loc == null || loc.uri == null) return null;
        java.io.File targetFile = new java.io.File(loc.uri);
        if (!targetFile.exists()) return null;
        com.cocode.vcode.ide.core.language.js.ParseResult parseResult =
                com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getParseResult(loc.uri);
        if (parseResult == null) return null;

        List<CompletionItem> exports = new ArrayList<>();
        com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = parseResult.tree;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_EXPORT) {
                for (int child = tree.nodeChild[i]; child != 0; child = tree.nodeSibling[child]) {
                    if (tree.nodeType[child] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_IDENTIFIER) {
                        String name = tree.nodeName[child];
                        if (name != null && !name.isEmpty()) {
                            exports.add(new CompletionItem(name, name, "Export",
                                    CompletionItem.Type.VALUE, 0, loc.uri));
                        }
                    } else if (tree.nodeType[child] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_FUNC_DECL
                            || tree.nodeType[child] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_CLASS_DECL
                            || tree.nodeType[child] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_VAR_DECL) {
                        String name = tree.nodeName[child];
                        if (name != null && !name.isEmpty()) {
                            CompletionItem.Type type = tree.nodeType[child] == com.cocode.vcode.ide.core.language.js.JsSyntaxTree.N_FUNC_DECL
                                    ? CompletionItem.Type.FUNCTION
                                    : CompletionItem.Type.VALUE;
                            exports.add(new CompletionItem(name, name, "Export", type, 0, loc.uri));
                        }
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

    @NonNull
    private Set<String> getStrings(Matcher mBefore, String currentWord) {
        String insideBlock = mBefore.group();
        int braceOpen = insideBlock.indexOf('{');
        String alreadyImported = braceOpen >= 0 ? insideBlock.substring(braceOpen + 1) : "";
        
        // Strip the currently typing word from the end so it doesn't get marked as "used"
        if (currentWord != null && !currentWord.isEmpty() && alreadyImported.endsWith(currentWord)) {
            alreadyImported = alreadyImported.substring(0, alreadyImported.length() - currentWord.length());
        }
        
        Set<String> used = new HashSet<>();
        for (String part : alreadyImported.split(",")) {
            String clean = part.trim();
            // Strip `name as alias` — the original name is what's used
            if (clean.contains(" as ")) clean = clean.split("\\s+as\\s+")[0].trim();
            if (!clean.isEmpty()) used.add(clean);
        }
        return used;
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

        // Match: import ... from '...' or require('...')
        // The group captures everything typed after the opening quote (may be empty)
        Matcher m = Pattern.compile(
                "(?:from\\s+['\"]|require\\s*\\(\\s*['\"])([^'\"]*)?$"
        ).matcher(lineBefore);

        if (!m.find()) return null; // Not inside an import path

        String typedPath = m.group(1) != null ? m.group(1) : "";
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
            if (baseToken.endsWith("()")) baseToken = baseToken.substring(0, baseToken.length() - 2);

            String inferredType = varTypeMap.get(baseToken);
            if (inferredType == null) {
                if (JsStandardLibrary.PROMISE_FUNCTIONS.contains(baseToken)) {
                    inferredType = "@PROMISE";
                } else {
                    String baseChainType = JsStandardLibrary.CHAIN_RETURN_TYPES.get(baseToken);
                    if (baseChainType != null) inferredType = "@" + baseChainType.toUpperCase();
                }
            }

            if (cachedTree != null && cachedScopeTree != null && inferredType == null) {
                int currentScope = cachedScopeTree.findScopeAt(dotPos, cachedTree);
                int[] resolved = cachedScopeTree.lookupSymbol(baseToken, currentScope, dotPos, cachedTree);
                if (resolved != null) {
                    int declNodeId = resolved[1];
                    inferredType = cachedTree.nodeTypeAnn[declNodeId]; 
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
                                            inferredType = getShapeOfProperty(rhsDeclId, varName);
                                        }
                                    }
                                } else if (cachedTree.nodeType[rhsNode] == JsSyntaxTree.N_OBJECT_LITERAL) {
                                    inferredType = getShapeOfProperty(parentId, varName);
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
                    if (inferredType != null && inferredType.startsWith("@ARRAY_OF_INLINE_SHAPE:")) {
                        inferredType = "@INLINE_SHAPE:" + inferredType.substring(23);
                    } else if (inferredType != null && inferredType.equals("@ARRAY")) {
                        inferredType = "@ANY";
                    } else {
                        inferredType = "@ANY"; 
                    }
                    continue;
                }

                if (inferredType != null && inferredType.startsWith("@INLINE_SHAPE_NODE:")) {
                    int nodeId = Integer.parseInt(inferredType.substring(19));
                    int nestedNodeId = findPropertyNodeId(nodeId, memberName);
                    if (nestedNodeId != 0) {
                        inferredType = "@INLINE_SHAPE_NODE:" + nestedNodeId;
                    } else {
                        inferredType = "@ANY";
                    }
                    continue;
                }

                if (inferredType != null && inferredType.startsWith("@")) {
                    String lookupType = inferredType;
                    String elementType = null;
                    if (inferredType.startsWith("@ARRAY_OF_INLINE_SHAPE:")) {
                        lookupType = "@ARRAY";
                        elementType = "@INLINE_SHAPE:" + inferredType.substring(23);
                    }
                    String nextType = BuiltinTypeTable.get(lookupType + "." + memberName);
                    if (nextType != null) {
                        if (nextType.equals("@ANY") && elementType != null && memberName.equals("find")) {
                            inferredType = elementType;
                        } else if (nextType.equals("@ARRAY") && elementType != null && (memberName.equals("map") || memberName.equals("filter") || memberName.equals("slice"))) {
                            inferredType = "@ARRAY_OF_INLINE_SHAPE:" + elementType.substring(14);
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

            if (inferredType != null && inferredType.startsWith("@")) {
                if (inferredType.startsWith("@PROMISE")) {
                    String[] methods = JsStandardLibrary.PROTOTYPE_METHODS.get("promise");
                    if (methods != null) return buildMemberList("Promise", methods, word, CompletionItem.Type.FUNCTION);
                } else if (inferredType.startsWith("@INLINE_SHAPE:")) {
                    String[] keys = inferredType.substring(14).split(",");
                    List<CompletionItem> shapeMembers = new ArrayList<>();
                    for (String key : keys) {
                        if (!key.isEmpty()) shapeMembers.add(new CompletionItem(key, key, "Property", CompletionItem.Type.VALUE, 0));
                    }
                    if (!shapeMembers.isEmpty()) return fuzzyFilter(shapeMembers, word);
                } else {
                    String typeName = inferredType.startsWith("@ARRAY_OF_INLINE_SHAPE:") ? "array" : inferredType.substring(1).toLowerCase();
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
                
                // Check if it's a local interface
                if (cachedTree != null) {
                    for (int i = 1; i < cachedTree.nodeCount; i++) {
                        int id = cachedTree.nodesByOffset[i];
                        if (cachedTree.nodeType[id] == JsSyntaxTree.N_INTERFACE && inferredType.equals(cachedTree.nodeName[id])) {
                            List<CompletionItem> ifaceMembers = new ArrayList<>();
                            int childId = cachedTree.nodeChild[id];
                            while (childId > 0) {
                                if (cachedTree.nodeType[childId] == JsSyntaxTree.N_PROPERTY || cachedTree.nodeType[childId] == JsSyntaxTree.N_METHOD) {
                                    String mem = cachedTree.nodeName[childId];
                                    if (mem != null) {
                                        ifaceMembers.add(new CompletionItem(mem, mem, "Interface Member", CompletionItem.Type.VALUE, 0));
                                    }
                                }
                                childId = cachedTree.nodeSibling[childId];
                            }
                            if (!ifaceMembers.isEmpty()) return fuzzyFilter(ifaceMembers, word);
                        }
                    }
                }
            }
        }

    // d2. Check if objectToken is a class instance (new ClassName = varName)
        // Scan document for `const objectToken = new ClassName(`
        Matcher mNew = PAT_NEW_INSTANCE.matcher(text);
        while (mNew.find()) {
            if (Objects.requireNonNull(mNew.group(1)).equals(objectToken)) {
                String className = mNew.group(2);
                List<CompletionItem> classMembers = ProjectSymbolIndex.getInstance().getClassMembers(className);
                if (!classMembers.isEmpty()) return fuzzyFilter(classMembers, word);
                // Also try local class members from this document
                classMembers = getLocalClassMembers(className);
                if (!classMembers.isEmpty()) return fuzzyFilter(classMembers, word);
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
            items.add(new CompletionItem(m, insert, ns + " member", type, 0));
        }
        return fuzzyFilter(items, word);
    }

    private List<String> extractChainBeforeDot(String text, int dotPos) {
        List<String> chain = new ArrayList<>();
        int i = dotPos - 1;
        while (i >= 0) {
            while (i >= 0 && text.charAt(i) == ' ') i--;
            if (i >= 0 && text.charAt(i) == '?') {
                i--;
                while (i >= 0 && text.charAt(i) == ' ') i--;
            }
            if (i < 0) break;

            boolean isMethodCall = false;
            int parenStart = -1, parenEnd = -1;
            if (text.charAt(i) == ')') {
                parenEnd = i;
                isMethodCall = true;
                int depth = 0;
                while (i >= 0) {
                    char c = text.charAt(i);
                    if (c == ')') depth++;
                    else if (c == '(') {
                        depth--;
                        if (depth == 0) { i--; break; }
                    }
                    i--;
                }
                parenStart = i + 1;
                while (i >= 0 && text.charAt(i) == ' ') i--;
                
                if (i < 0 || !isWordChar(text.charAt(i))) {
                    String group = text.substring(parenStart + 1, parenEnd).trim();
                    if (group.startsWith("await ")) {
                        String inner = group.substring(6).trim();
                        List<String> innerChain = extractChainBeforeDot(inner + ".", inner.length() + 1);
                        if (!innerChain.isEmpty()) {
                            chain.addAll(0, innerChain);
                            chain.add(innerChain.size(), "!AWAIT");
                        }
                    }
                    if (i < 0) break;
                    break;
                }
            }

            if (text.charAt(i) == ']') {
                int depth = 0;
                int endBracket = i;
                while (i >= 0) {
                    char c = text.charAt(i);
                    if (c == ']') depth++;
                    else if (c == '[') {
                        depth--;
                        if (depth == 0) { 
                            String inner = text.substring(i + 1, endBracket).trim();
                            chain.add(0, "[" + inner + "]");
                            i--; 
                            break; 
                        }
                    }
                    i--;
                }
                while (i >= 0 && text.charAt(i) == ' ') i--;
                if (i < 0) break;
                if (text.charAt(i) == ']') {
                    continue;
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
            
            while (i >= 0 && text.charAt(i) == ' ') i--;
            if (i >= 0 && text.charAt(i) == '.') {
                i--;
            } else {
                break;
            }
        }
        return chain;
    }

    private String[] findReturnShapeDFS(int nodeId) {
        int child = cachedTree.nodeChild[nodeId];
        while (child > 0) {
            if (cachedTree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                String[] shape = cachedTree.shapeTable.get(child);
                if (shape != null) return shape;
            }
            if (cachedTree.nodeType[child] != JsSyntaxTree.N_FUNC_DECL && 
                cachedTree.nodeType[child] != JsSyntaxTree.N_ARROW_FUNC && 
                cachedTree.nodeType[child] != JsSyntaxTree.N_METHOD) {
                String[] found = findReturnShapeDFS(child);
                if (found != null) return found;
            }
            child = cachedTree.nodeSibling[child];
        }
        return null;
    }

    private String getShapeOfProperty(int objNodeId, String propName) {
        if (cachedTree == null) return null;
        int child = cachedTree.nodeChild[objNodeId];
        while (child > 0) {
            if (cachedTree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                int propChild = cachedTree.nodeChild[child];
                while (propChild > 0) {
                    if (cachedTree.nodeType[propChild] == JsSyntaxTree.N_PROPERTY && propName.equals(cachedTree.nodeName[propChild])) {
                        String[] keys = cachedTree.shapeTable.get(propChild);
                        if (keys != null && keys.length > 0) {
                            return "@INLINE_SHAPE:" + String.join(",", keys);
                        }
                    }
                    propChild = cachedTree.nodeSibling[propChild];
                }
            }
            child = cachedTree.nodeSibling[child];
        }
        return null;
    }

    private int findPropertyNodeId(int parentNodeId, String propName) {
        if (cachedTree == null) return 0;
        int child = cachedTree.nodeChild[parentNodeId];
        while (child > 0) {
            if (cachedTree.nodeType[child] == JsSyntaxTree.N_OBJECT_LITERAL) {
                int propChild = cachedTree.nodeChild[child];
                while (propChild > 0) {
                    if (cachedTree.nodeType[propChild] == JsSyntaxTree.N_PROPERTY && propName.equals(cachedTree.nodeName[propChild])) {
                        return propChild;
                    }
                    propChild = cachedTree.nodeSibling[propChild];
                }
            }
            child = cachedTree.nodeSibling[child];
        }
        return 0;
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
        cachedScopeTree = ScopeTree.build(cachedTree);

        Set<String> builtinNames = new HashSet<>();
        for (CompletionItem item : builtinItems) builtinNames.add(item.getLabel());

        int scanLimit = Math.min(text.length(), 100_000);

        Matcher m = PAT_USER_DECL.matcher(text);
        while (m.find() && m.start() < scanLimit) {
            String name = firstNonNull(m.group(1), m.group(2), m.group(3), m.group(4));
            if (name == null || name.isEmpty() || builtinNames.contains(name)) continue;

            boolean isFunction = m.group(1) != null || m.group(3) != null;
            boolean isClass = m.group(2) != null;

            String jsDocType = extractJsDocType(text, m.start());
            if (jsDocType != null) {
                varTypeMap.put(name, jsDocType.toLowerCase());
            } else if (!isFunction && !isClass) {
                inferVariableType(text, m.end(), name, scanLimit);
            }
        }

        Matcher mImport = PAT_IMPORT_STAR.matcher(text);
        while (mImport.find() && mImport.start() < scanLimit) {
            String name = mImport.group(1);
            String path = mImport.group(2);
            varTypeMap.put(name, "module:" + path);
            cachedGenericSymbols.add(new CompletionItem(name, name, "Module", CompletionItem.Type.KEYWORD, 0));
        }

        // Track `const x = new ClassName()` → varTypeMap["x"] = "ClassName"
        Matcher mNew = PAT_NEW_INSTANCE.matcher(text);
        while (mNew.find() && mNew.start() < scanLimit) {
            varTypeMap.put(mNew.group(1), mNew.group(2));
        }
        
        Matcher wordMatcher = PAT_WORD.matcher(text);
        while (wordMatcher.find() && wordMatcher.start() < scanLimit) {
            String w = wordMatcher.group();
            if (w.length() >= 3 && !builtinNames.contains(w) && !cachedScopeTree.symbols.containsKey(w)) {
                cachedGenericSymbols.add(new CompletionItem(w, w, "Word", CompletionItem.Type.VALUE, 0));
            }
        }

        if (cachedTree != null) {
            for (int i = 1; i < cachedTree.nodeCount; i++) {
                int id = cachedTree.nodesByOffset[i];
                int type = cachedTree.nodeType[id];
                if ((type == JsSyntaxTree.N_VAR_DECL || type == JsSyntaxTree.N_PARAM) && cachedTree.nodeTypeAnn[id] != null) {
                    String name = cachedTree.nodeName[id];
                    if (name != null && !name.isEmpty()) {
                        varTypeMap.put(name, cachedTree.nodeTypeAnn[id]);
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
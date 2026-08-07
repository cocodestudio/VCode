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

    // ─── Regex patterns ────────────────────────────────────────────────────────
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

    // ─── Instance state ─────────────────────────────────────────────────────────
    private final List<CompletionItem> builtinItems = new ArrayList<>();
    private final List<JsSymbol> cachedUserSymbols = new ArrayList<>();
    private final Map<String, String> varTypeMap = new HashMap<>();
    private int lastTextHash = 0;
    private File currentFile;

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

    // ─── Keyword loading ───────────────────────────────────────────────────────

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

    // ─── Main entry point ──────────────────────────────────────────────────────

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length())
            return new ArrayList<>();

        String word = getWordBeforeCursor(fullText, cursorPos);

        // ── 1. Import / require path completion ──────────────────────────────
        List<CompletionItem> importItems = getImportPathSuggestions(fullText, cursorPos);
        if (importItems != null) return importItems;

        // ── 1c. Import block completion ──────────────────────────────────────
        List<CompletionItem> importExport = getImportExportSuggestions(fullText, cursorPos, word);
        if (importExport != null) return importExport;

        // ── 1b. Event name string completions (addEventListener/removeEventListener) ──
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

        if (isInsideStringLiteral(fullText, cursorPos)) {
            return new ArrayList<>();
        }

        // ── 2. Dot-member completion ─────────────────────────────────────────
        int dotCheckPos = cursorPos - word.length() - 1;
        if (dotCheckPos >= 0 && fullText.charAt(dotCheckPos) == '.') {
            List<CompletionItem> memberItems = getMemberCompletions(fullText, dotCheckPos, word);
            if (!memberItems.isEmpty()) return memberItems;
        }

        // ── 2b. Object literal key completion ────────────────────────────────
        // If we're inside an object literal (after { or ,) suggest known keys
        List<CompletionItem> objKeys = getObjectLiteralSuggestions(fullText, cursorPos, word);
        if (objKeys != null) return objKeys;

        // ── 3. General: keywords + user symbols ──────────────────────────────
        ensureDocumentIndexed(fullText);
        List<CompletionItem> all = new ArrayList<>(builtinItems);
        Set<String> added = new HashSet<>();
        for (CompletionItem item : builtinItems) added.add(item.getLabel());

        for (JsSymbol sym : cachedUserSymbols) {
            if (sym.scope == null || sym.scope.contains(cursorPos)) {
                if (added.add(sym.item.getLabel())) {
                    all.add(sym.item);
                }
            }
        }
        return fuzzyFilter(all, word);
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

    // ─── Object literal key suggestions ────────────────────────────────────────

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
        ensureDocumentIndexed(fullText);

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

    // ─── Import / require path completion ─────────────────────────────────────

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

        // Find the closing `} from 'path'` after the cursor
        String after = fullText.substring(cursorPos);
        Matcher mAfter = Pattern.compile("^[^{}]*\\}\\s*from\\s*['\"]([^'\"]+)['\"]").matcher(after);
        if (!mAfter.find()) return null;

        String path = mAfter.group(1);
        List<CompletionItem> exports = ProjectSymbolIndex.getInstance().getExportsForPath(currentFile, path);
        if (exports.isEmpty()) return null;

        // Collect names already imported in this block so we don't re-suggest them
        Set<String> used = getStrings(mBefore);

        List<CompletionItem> filtered = new ArrayList<>();
        for (CompletionItem e : exports) {
            if (!used.contains(e.getEffectiveInsertText())) filtered.add(e);
        }
        return fuzzyFilter(filtered, word);
    }

    @NonNull
    private Set<String> getStrings(Matcher mBefore) {
        String insideBlock = mBefore.group();
        int braceOpen = insideBlock.indexOf('{');
        String alreadyImported = braceOpen >= 0 ? insideBlock.substring(braceOpen + 1) : "";
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
                    if (name.equals(ProjectRepository.META_FILE) || name.equals(ProjectRepository.SESSION_FILE)) continue;
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

    // ─── Dot-member completion ─────────────────────────────────────────────────

    /**
     * Computes member completions for the object/expression before the dot.
     *
     * <p>Insert text is ONLY the method name (e.g. "floor(|)"), never "Math.floor(|)".
     * The namespace is already in the document — we insert only AFTER the dot.
     */
    private List<CompletionItem> getMemberCompletions(String text, int dotPos, String word) {
        String objectToken = extractObjectBeforeDot(text, dotPos);
        if (objectToken.isEmpty()) return new ArrayList<>();

        // ── this. → current class member completions ─────────────────────────
        if (objectToken.equals("this")) {
            List<CompletionItem> thisMembers = getCurrentClassMembers(text, dotPos);
            if (!thisMembers.isEmpty()) return fuzzyFilter(thisMembers, word);
        }

        // ── a. Check known static namespaces ─────────────────────────────────
        for (String[] pair : JsStandardLibrary.DOT_METHODS) {
            if (pair[0].equalsIgnoreCase(objectToken) || objectToken.endsWith(pair[0])) {
                return buildMemberList(pair[0], pair[1].split(","), word, CompletionItem.Type.BUILTIN);
            }
        }

        // ── b. Functions that always return Promise (e.g. fetch) ──────────────
        if (JsStandardLibrary.PROMISE_FUNCTIONS.contains(objectToken)) {
            return buildMemberList("Promise", JsStandardLibrary.PROTOTYPE_METHODS.get("promise"), word, CompletionItem.Type.FUNCTION);
        }

        // ── c. Chain return type — e.g. "arr.filter(...)" → array methods ────
        String chainType = JsStandardLibrary.CHAIN_RETURN_TYPES.get(objectToken);
        if (chainType != null) {
            String[] methods = JsStandardLibrary.PROTOTYPE_METHODS.get(chainType);
            if (methods != null) {
                return buildMemberList(chainType, methods, word, CompletionItem.Type.FUNCTION);
            }
        }

        // ── d. User-variable type inference ───────────────────────────────────
        String inferredType = varTypeMap.get(objectToken);
        if (inferredType != null) {
            if (inferredType.startsWith("module:")) {
                String path = inferredType.substring(7);
                List<CompletionItem> exports = ProjectSymbolIndex.getInstance().getExportsForPath(currentFile, path);
                if (!exports.isEmpty()) return fuzzyFilter(exports, word);
            }

            // First check prototype methods (array, string, etc.)
            String[] methods = JsStandardLibrary.PROTOTYPE_METHODS.get(inferredType);
            if (methods != null) {
                return buildMemberList(inferredType, methods, word, CompletionItem.Type.FUNCTION);
            }
            // Then check JsStandardLibrary.DOT_METHODS (for types like IntersectionObserver, WebSocket etc.)
            for (String[] pair : JsStandardLibrary.DOT_METHODS) {
                if (pair[0].equals(inferredType)) {
                    return buildMemberList(pair[0], pair[1].split(","), word, CompletionItem.Type.BUILTIN);
                }
            }

            // Check if it's a class name — return class members
            List<CompletionItem> classMembers = ProjectSymbolIndex.getInstance().getClassMembers(inferredType);
            if (!classMembers.isEmpty()) return fuzzyFilter(classMembers, word);
        }

        // ── d2. Check if objectToken is a class instance (new ClassName = varName) ─
        // Scan document for `const objectToken = new ClassName(`
        Matcher mNew = PAT_NEW_INSTANCE.matcher(text);
        while (mNew.find()) {
            if (Objects.requireNonNull(mNew.group(1)).equals(objectToken)) {
                String className = mNew.group(2);
                List<CompletionItem> classMembers = ProjectSymbolIndex.getInstance().getClassMembers(className);
                if (!classMembers.isEmpty()) return fuzzyFilter(classMembers, word);
                // Also try local class members from this document
                classMembers = getLocalClassMembers(text, className);
                if (!classMembers.isEmpty()) return fuzzyFilter(classMembers, word);
            }
        }

        // ── e. Heuristic name-based guess ─────────────────────────────────────
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

    /**
     * Walks backward from {@code dotPos - 1} to extract the identifier or expression
     * immediately before the dot, handling: simple identifiers, function calls {@code ()},
     * and array accesses {@code []}.
     */
    private String extractObjectBeforeDot(String text, int dotPos) {
        if (dotPos <= 0) return "";
        int i = dotPos - 1;

        // Skip spaces before dot
        while (i >= 0 && text.charAt(i) == ' ') i--;
        if (i < 0) return "";

        // Handle closing ) — walk past the entire argument list
        if (text.charAt(i) == ')') {
            int depth = 0;
            while (i >= 0) {
                char c = text.charAt(i);
                if (c == ')') depth++;
                else if (c == '(') {
                    depth--;
                    if (depth == 0) {
                        i--;
                        break;
                    }
                }
                i--;
            }
            while (i >= 0 && text.charAt(i) == ' ') i--;
            if (i < 0) return "";
        }

        // Handle closing ] — walk past array access
        if (text.charAt(i) == ']') {
            int depth = 0;
            while (i >= 0) {
                char c = text.charAt(i);
                if (c == ']') depth++;
                else if (c == '[') {
                    depth--;
                    if (depth == 0) {
                        i--;
                        break;
                    }
                }
                i--;
            }
            while (i >= 0 && text.charAt(i) == ' ') i--;
            if (i < 0) return "";
        }

        // Collect the identifier
        if (!isWordChar(text.charAt(i))) return "";
        int end = i + 1;
        while (i > 0 && isWordChar(text.charAt(i - 1))) i--;
        return text.substring(i, end);
    }

    // ─── Class member helpers ─────────────────────────────────────────────────

    /**
     * Returns class members for `this.` by finding which class body the cursor is inside.
     */
    private List<CompletionItem> getCurrentClassMembers(String text, int dotPos) {
        // Walk backwards to find the nearest enclosing `class Name {`
        Matcher m = PAT_CLASS_IN_SCOPE.matcher(text);
        String enclosingClass = null;
        int classBodyStart = -1;
        while (m.find() && m.end() < dotPos) {
            enclosingClass = m.group(1);
            classBodyStart = text.indexOf('{', m.end() - 1);
        }
        if (enclosingClass == null || classBodyStart < 0) return new ArrayList<>();
        return getLocalClassMembers(text, enclosingClass);
    }

    /**
     * Parses class members (methods + this.prop assignments) directly from the document text.
     */
    private List<CompletionItem> getLocalClassMembers(String text, String className) {
        List<CompletionItem> members = new ArrayList<>();
        Matcher mClass = PAT_CLASS_IN_SCOPE.matcher(text);
        while (mClass.find()) {
            if (!Objects.equals(mClass.group(1), className)) continue;
            int bodyStart = text.indexOf('{', mClass.end() - 1);
            if (bodyStart < 0) continue;
            int depth = 1, pos = bodyStart + 1;
            while (pos < text.length() && depth > 0) {
                char c = text.charAt(pos);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                pos++;
            }
            String body = text.substring(bodyStart + 1, pos - 1);
            Set<String> seen = new HashSet<>();

            // Methods: name(...) {
            Matcher mMethod = Pattern.compile("(?:(?:static|async|get|set)\\s+)?([a-zA-Z_$][\\w$]*)\\s*\\(([^)]*)\\)\\s*\\{")
                    .matcher(body);
            while (mMethod.find()) {
                String name = mMethod.group(1);
                if (Objects.equals(name, "constructor") || seen.contains(name)) {
                    seen.add(name);
                    continue;
                }
                seen.add(name);
                members.add(new CompletionItem(name + "(|)", name, className + " method", CompletionItem.Type.FUNCTION, 0));
            }

            // this.prop = ... assignments in constructor/methods
            Matcher mThis = Pattern.compile("this\\.([a-zA-Z_$][\\w$]*)\\s*=(?!=)").matcher(body);
            while (mThis.find()) {
                String name = mThis.group(1);
                if (seen.add(name)) {
                    members.add(new CompletionItem(name, name, className + " property", CompletionItem.Type.VALUE, 0));
                }
            }
            return members;
        }
        return members;
    }

    // ─── Document symbol indexing ──────────────────────────────────────────────

    private void ensureDocumentIndexed(String text) {
        int hash = text.hashCode();
        if (hash == lastTextHash) return;
        lastTextHash = hash;
        cachedUserSymbols.clear();
        varTypeMap.clear();

        Set<String> builtinNames = new HashSet<>();
        for (CompletionItem item : builtinItems) builtinNames.add(item.getLabel());

        List<JsScopeParser.ScopeBlock> documentScopes = JsScopeParser.buildScopes(text);
        int scanLimit = Math.min(text.length(), 100_000);

        Set<String> declNames = new HashSet<>(builtinNames);

        Matcher m = PAT_USER_DECL.matcher(text);
        while (m.find() && m.start() < scanLimit) {
            String name = firstNonNull(m.group(1), m.group(2), m.group(3), m.group(4));
            if (name == null || name.isEmpty() || builtinNames.contains(name)) continue;

            declNames.add(name);

            boolean isFunction = m.group(1) != null || m.group(3) != null;
            boolean isClass = m.group(2) != null;

            CompletionItem.Type type;
            String detail;
            String jsDocType = extractJsDocType(text, m.start());

            if (isClass) {
                type = CompletionItem.Type.KEYWORD;
                detail = "Class";
            } else if (isFunction) {
                type = CompletionItem.Type.FUNCTION;
                detail = "Function";
            } else {
                type = CompletionItem.Type.VALUE;
                detail = "Variable";
            }

            if (jsDocType != null) {
                varTypeMap.put(name, jsDocType.toLowerCase());
            } else if (!isFunction && !isClass) {
                inferVariableType(text, m.end(), name, scanLimit);
            }

            JsScopeParser.ScopeBlock symScope = JsScopeParser.findDeepestScope(documentScopes, m.start());
            cachedUserSymbols.add(new JsSymbol(new CompletionItem(name, name, detail, type, 0), symScope));
        }

        Matcher wordMatcher = PAT_WORD.matcher(text);
        JsScopeParser.ScopeBlock rootScope = documentScopes.isEmpty() ? null : documentScopes.get(0);

        Matcher mImport = PAT_IMPORT_STAR.matcher(text);
        while (mImport.find() && mImport.start() < scanLimit) {
            String name = mImport.group(1);
            String path = mImport.group(2);
            varTypeMap.put(name, "module:" + path);
            declNames.add(name);
            cachedUserSymbols.add(new JsSymbol(new CompletionItem(name, name, "Module", CompletionItem.Type.KEYWORD, 0), rootScope));
        }

        // Track `const x = new ClassName()` → varTypeMap["x"] = "ClassName"
        Matcher mNew = PAT_NEW_INSTANCE.matcher(text);
        while (mNew.find() && mNew.start() < scanLimit) {
            varTypeMap.put(mNew.group(1), mNew.group(2));
        }

        Set<String> wordSeen = new HashSet<>(declNames);
        while (wordMatcher.find() && wordMatcher.start() < scanLimit) {
            String w = wordMatcher.group();
            if (w.length() >= 3 && wordSeen.add(w)) {
                cachedUserSymbols.add(new JsSymbol(new CompletionItem(w, w, "Word", CompletionItem.Type.VALUE, 0), rootScope));
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

    private static class JsSymbol {
        CompletionItem item;
        JsScopeParser.ScopeBlock scope;

        public JsSymbol(CompletionItem item, JsScopeParser.ScopeBlock scope) {
            this.item = item;
            this.scope = scope;
        }
    }
}
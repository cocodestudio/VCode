package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.css.CssLexer;
import com.cocode.vcode.ide.core.language.css.CssParser;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;
import com.cocode.vcode.ide.core.language.html.HtmlLexer;
import com.cocode.vcode.ide.core.language.html.HtmlParser;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlTokenStream;
import com.cocode.vcode.ide.core.language.js.JsExportTable;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.lsp.index.IndexSymbolEntry;
import com.cocode.vcode.ide.core.lsp.index.IndexingProgressListener;
import com.cocode.vcode.ide.core.lsp.index.SegmentSearchQuery;
import com.cocode.vcode.ide.core.lsp.index.SegmentedSymbolStore;
import com.cocode.vcode.ide.core.lsp.index.SymbolKey;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Global project indexer that extracts CSS classes, IDs, HTML IDs, and JS/TS exports
 * and class members for cross-file intellisense using pure AST parsing and flat-memory segments.
 */
public class ProjectSymbolIndex {
    private static ProjectSymbolIndex instance;
    // Maps class name → list of member CompletionItems (methods + properties)
    private final Map<String, List<CompletionItem>> classMembers = new HashMap<>();
    // Also maps absolute file path → class members for cross-file class resolution
    private final Map<String, Map<String, List<CompletionItem>>> fileClassMembers = new HashMap<>();
    private final List<CompletionItem> cssClassItems = new ArrayList<>();
    private final List<CompletionItem> cssIdItems = new ArrayList<>();
    private final List<CompletionItem> htmlIdItems = new ArrayList<>();
    // Maps absolute file path to its exported CompletionItems
    private final Map<String, List<CompletionItem>> jsFileExports = new HashMap<>();
    // Guards against multiple concurrent buildIndex() calls for the same root.
    private final java.util.concurrent.atomic.AtomicBoolean isBuilding = new java.util.concurrent.atomic.AtomicBoolean(false);
    private String projectRoot = null;
    private SegmentedSymbolStore segmentedStore;
    private final List<IndexingProgressListener> progressListeners = new CopyOnWriteArrayList<>();

    private ProjectSymbolIndex() {
    }

    public static synchronized ProjectSymbolIndex getInstance() {
        if (instance == null) {
            instance = new ProjectSymbolIndex();
        }
        return instance;
    }

    public static File getProjectRoot(File file) {
        return ProjectRepository.findProjectRoot(file);
    }

    public void addProgressListener(IndexingProgressListener listener) {
        if (listener != null && !progressListeners.contains(listener)) {
            progressListeners.add(listener);
        }
    }

    public void removeProgressListener(IndexingProgressListener listener) {
        if (listener != null) {
            progressListeners.remove(listener);
        }
    }

    public SegmentedSymbolStore getSegmentedStore() {
        return segmentedStore;
    }

    public synchronized void clearInMemorySymbols() {
        classMembers.clear();
        fileClassMembers.clear();
        cssClassItems.clear();
        cssIdItems.clear();
        htmlIdItems.clear();
        jsFileExports.clear();
    }

    public static boolean isValidCssClassName(String name) {
        if (name == null || name.isEmpty()) return false;
        char first = name.charAt(0);
        // In CSS syntax, an identifier cannot start with a digit
        if (Character.isDigit(first)) return false;
        if (first == '-') {
            if (name.length() == 1 || Character.isDigit(name.charAt(1))) return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch != '_' && ch != '-' && !Character.isLetterOrDigit(ch) && ch < 128) {
                return false;
            }
        }
        if (isKnownUnitOrExtension(name)) {
            return false;
        }
        return true;
    }

    public static boolean isValidCssId(String name) {
        if (name == null || name.isEmpty()) return false;
        char first = name.charAt(0);
        if (Character.isDigit(first)) return false;
        if (first == '-') {
            if (name.length() == 1 || Character.isDigit(name.charAt(1))) return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch != '_' && ch != '-' && !Character.isLetterOrDigit(ch) && ch < 128) {
                return false;
            }
        }
        int len = name.length();
        if (len == 3 || len == 4 || len == 6 || len == 8) {
            boolean allHex = true;
            for (int i = 0; i < len; i++) {
                char ch = name.charAt(i);
                if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'))) {
                    allHex = false;
                    break;
                }
            }
            if (allHex) return false;
        }
        if (isKnownUnitOrExtension(name)) {
            return false;
        }
        return true;
    }

    public static boolean isValidHtmlId(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.startsWith("#")) {
            name = name.substring(1).trim();
            if (name.isEmpty()) return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isWhitespace(ch)) return false;
        }
        int len = name.length();
        if (len == 3 || len == 4 || len == 6 || len == 8) {
            boolean allHex = true;
            for (int i = 0; i < len; i++) {
                char ch = name.charAt(i);
                if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'))) {
                    allHex = false;
                    break;
                }
            }
            if (allHex) return false;
        }
        return true;
    }

    public static boolean isValidJsIdentifier(String name) {
        if (name == null || name.isEmpty()) return false;
        char first = name.charAt(0);
        if (!Character.isJavaIdentifierStart(first) && first != '$') return false;
        for (int i = 1; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (!Character.isJavaIdentifierPart(ch) && ch != '$') return false;
        }
        return !isJsReservedWord(name);
    }

    public static boolean isJsReservedWord(String word) {
        if (word == null) return false;
        switch (word) {
            case "break":
            case "case":
            case "catch":
            case "class":
            case "const":
            case "continue":
            case "debugger":
            case "default":
            case "delete":
            case "do":
            case "else":
            case "export":
            case "extends":
            case "finally":
            case "for":
            case "function":
            case "if":
            case "import":
            case "in":
            case "instanceof":
            case "new":
            case "return":
            case "super":
            case "switch":
            case "this":
            case "throw":
            case "try":
            case "typeof":
            case "var":
            case "void":
            case "while":
            case "with":
            case "yield":
            case "let":
            case "static":
            case "enum":
            case "await":
            case "null":
            case "true":
            case "false":
            case "undefined":
            case "NaN":
            case "Infinity":
                return true;
            default:
                return false;
        }
    }

    public static boolean isKnownUnitOrExtension(String name) {
        if (name == null || name.length() > 5) return false;
        String lower = name.toLowerCase(java.util.Locale.US);
        switch (lower) {
            // Absolute length units
            case "px":
            case "cm":
            case "mm":
            case "q":
            case "in":
            case "pt":
            case "pc":
            // Relative length units
            case "em":
            case "rem":
            case "ex":
            case "ch":
            case "cap":
            case "ic":
            case "lh":
            case "rlh":
            // Viewport units
            case "vh":
            case "vw":
            case "vi":
            case "vb":
            case "vmin":
            case "vmax":
            case "dvh":
            case "dvw":
            case "dvi":
            case "dvb":
            case "lvh":
            case "lvw":
            case "lvi":
            case "lvb":
            case "svh":
            case "svw":
            case "svi":
            case "svb":
            // Container query units
            case "cqw":
            case "cqh":
            case "cqi":
            case "cqb":
            case "cqmin":
            case "cqmax":
            // Angle units
            case "deg":
            case "grad":
            case "rad":
            case "turn":
            // Time units
            case "s":
            case "ms":
            // Frequency units
            case "hz":
            case "khz":
            // Resolution units
            case "dpi":
            case "dpcm":
            case "dppx":
            // Grid units
            case "fr":
            // Common file extensions
            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
            case "svg":
            case "webp":
            case "css":
            case "scss":
            case "less":
            case "html":
            case "htm":
            case "js":
            case "jsx":
            case "ts":
            case "tsx":
            case "mjs":
            case "cjs":
            case "json":
            case "woff":
            case "woff2":
            case "ttf":
            case "otf":
            case "eot":
            case "mp4":
            case "webm":
            case "ogg":
            case "mp3":
            case "wav":
            case "pdf":
            case "xml":
            case "txt":
            case "map":
            case "com":
            case "org":
            case "net":
            case "io":
            case "dev":
            case "app":
                return true;
            default:
                return false;
        }
    }

    public static void extractCssClassesAndIds(String selector, Set<String> classNames, Set<String> cssIds) {
        if (selector == null || selector.isEmpty()) return;
        String trimmed = selector.trim();
        if (trimmed.startsWith("@")) return;

        int len = selector.length();
        int i = 0;
        int bracketDepth = 0;

        while (i < len) {
            char c = selector.charAt(i);

            // Skip string literals (e.g. [data-attr="foo.bar#baz"])
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < len && selector.charAt(i) != quote) {
                    if (selector.charAt(i) == '\\') i++;
                    i++;
                }
                if (i < len) i++;
                continue;
            }

            // Skip attribute selectors [ ... ]
            if (c == '[') {
                bracketDepth++;
                i++;
                continue;
            } else if (c == ']') {
                if (bracketDepth > 0) bracketDepth--;
                i++;
                continue;
            }

            if (bracketDepth > 0) {
                i++;
                continue;
            }

            if (c == '.' || c == '#') {
                boolean isClass = (c == '.');
                i++;
                int start = i;
                while (i < len) {
                    char ch = selector.charAt(i);
                    if (ch == '_' || ch == '-' || Character.isLetterOrDigit(ch) || ch >= 128) {
                        i++;
                    } else {
                        break;
                    }
                }
                if (i > start) {
                    String name = selector.substring(start, i);
                    if (i < len && selector.charAt(i) == '[' && name.endsWith("-")) {
                        name = name.substring(0, name.length() - 1);
                    }
                    if (isClass) {
                        if (isValidCssClassName(name)) {
                            classNames.add(name);
                        }
                    } else {
                        if (isValidCssId(name)) {
                            cssIds.add(name);
                        }
                    }
                }
            } else {
                i++;
            }
        }
    }

    private static String stripQuotes(String str) {
        if (str == null || str.length() < 2) return str != null ? str : "";
        char first = str.charAt(0);
        char last = str.charAt(str.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    private static boolean isIgnoredProp(String name) {
        return "constructor".equals(name) || "prototype".equals(name) || "function".equals(name);
    }

    public void buildIndex(File rootDir) {
        if (rootDir == null) return;
        if (!isBuilding.compareAndSet(false, true)) return;

        String newRootPath = rootDir.getAbsolutePath();
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                boolean projectChanged = (segmentedStore == null || projectRoot == null || !projectRoot.equals(newRootPath));
                if (projectChanged) {
                    if (segmentedStore != null) {
                        segmentedStore.close();
                    }
                    clearInMemorySymbols();
                    projectRoot = newRootPath;
                    segmentedStore = new SegmentedSymbolStore(rootDir);
                }

                IndexingProgressListener forwarder = new IndexingProgressListener() {
                    @Override
                    public void onIndexingStarted(int totalFiles) {
                        for (IndexingProgressListener l : progressListeners) {
                            try { l.onIndexingStarted(totalFiles); } catch (Exception ignored) {}
                        }
                    }

                    @Override
                    public void onIndexingProgress(int processed, int total, String currentFile) {
                        for (IndexingProgressListener l : progressListeners) {
                            try { l.onIndexingProgress(processed, total, currentFile); } catch (Exception ignored) {}
                        }
                    }

                    @Override
                    public void onIndexingCompleted(int totalSymbols, long durationMs) {
                        for (IndexingProgressListener l : progressListeners) {
                            try { l.onIndexingCompleted(totalSymbols, durationMs); } catch (Exception ignored) {}
                        }
                    }

                    @Override
                    public void onIndexingError(Throwable error) {
                        for (IndexingProgressListener l : progressListeners) {
                            try { l.onIndexingError(error); } catch (Exception ignored) {}
                        }
                    }
                };

                List<File> candidateFiles = new ArrayList<>();
                collectCandidateFiles(rootDir, candidateFiles);

                // If project is already indexed and all files are fresh, populate in-memory symbols silently without UI
                if (segmentedStore.isIndexUpToDate(rootDir, candidateFiles)) {
                    loadCachedSymbolsIntoMemory();
                    return;
                }

                segmentedStore.addProgressListener(forwarder);

                Set<String> classNames = new HashSet<>();
                Set<String> cssIds = new HashSet<>();
                Set<String> htmlIds = new HashSet<>();

                segmentedStore.indexFiles(rootDir, candidateFiles, (file, relPath, fileId) -> {
                    List<IndexSymbolEntry> entries = new ArrayList<>();
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".css") || name.endsWith(".scss")) {
                        indexCssFile(file, classNames, cssIds, entries, fileId);
                    } else if (name.endsWith(".html") || name.endsWith(".htm")) {
                        indexHtmlFile(file, classNames, htmlIds, entries, fileId);
                    } else if (name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".jsx") || name.endsWith(".tsx") || name.endsWith(".mjs")) {
                        indexJsFile(file, relPath, entries, fileId);
                    }
                    return entries;
                });

                segmentedStore.removeProgressListener(forwarder);

                // Populate in-memory sets from segmented store for all project symbols
                loadCachedSymbolsIntoMemory();
            } catch (Throwable t) {
                for (IndexingProgressListener l : progressListeners) {
                    try { l.onIndexingError(t); } catch (Exception ignored) {}
                }
            } finally {
                isBuilding.set(false);
            }
        });
    }

    private void collectCandidateFiles(File dir, List<File> candidateFiles) {
        if (dir == null || !dir.exists()) return;
        String dirName = dir.getName();
        if (dirName.startsWith(".") || "node_modules".equals(dirName) || "build".equals(dirName)
                || "dist".equals(dirName) || "out".equals(dirName) || "vendor".equals(dirName)
                || ".next".equals(dirName) || ".nuxt".equals(dirName) || ".vcode".equals(dirName)) {
            return;
        }

        List<File> files = com.cocode.vcode.ide.core.autocomplete.VFSManager.getInstance().listCachedFiles(dir);
        if (files == null) {
            File[] diskFiles = dir.listFiles();
            if (diskFiles == null) return;
            files = new ArrayList<>();
            for (File f : diskFiles) {
                if (!f.getName().startsWith(".")) {
                    files.add(f);
                }
            }
        }

        for (File f : files) {
            if (f.isDirectory()) {
                collectCandidateFiles(f, candidateFiles);
            } else {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".css") || name.endsWith(".scss") || name.endsWith(".html") || name.endsWith(".htm")
                        || name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".jsx") || name.endsWith(".tsx") || name.endsWith(".mjs")) {
                    candidateFiles.add(f);
                }
            }
        }
    }

    private void indexDirectoryRecursively(File dir, Set<String> classNames, Set<String> cssIds, Set<String> htmlIds) {
        List<File> candidateFiles = new ArrayList<>();
        collectCandidateFiles(dir, candidateFiles);
        for (File f : candidateFiles) {
            String name = f.getName().toLowerCase();
            if (name.endsWith(".css") || name.endsWith(".scss")) {
                indexCssFile(f, classNames, cssIds);
            } else if (name.endsWith(".html") || name.endsWith(".htm")) {
                indexHtmlFile(f, classNames, htmlIds);
            } else if (name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".jsx") || name.endsWith(".tsx") || name.endsWith(".mjs")) {
                indexJsFile(f);
            }
        }
    }

    private void indexCssFile(File file, Set<String> classNames, Set<String> cssIds) {
        indexCssFile(file, classNames, cssIds, null, 0);
    }

    private void indexCssFile(File file, Set<String> classNames, Set<String> cssIds, List<IndexSymbolEntry> entries, int fileId) {
        String content = readFile(file);
        if (content == null) return;

        CssTokenStream stream = CssLexer.tokenize(content);
        CssSyntaxTree tree = CssParser.parse(stream, content);

        Set<String> fileClasses = new HashSet<>();
        Set<String> fileIds = new HashSet<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == CssSyntaxTree.N_SELECTOR) {
                int parent = tree.nodeParent[i];
                if (parent > 0 && tree.nodeType[parent] == CssSyntaxTree.N_AT_RULE) {
                    continue; // Skip at-rules (@media, @keyframes, etc.)
                }
                String sel = tree.nodeName[i];
                if (sel != null && !sel.isEmpty() && !sel.startsWith("@")) {
                    extractCssClassesAndIds(sel, fileClasses, fileIds);
                }
            }
        }

        classNames.addAll(fileClasses);
        cssIds.addAll(fileIds);
        if (entries != null) {
            for (String c : fileClasses) {
                entries.add(new IndexSymbolEntry(SymbolKey.cssClass(c), c, IndexSymbolEntry.KIND_CSS_CLASS, IndexSymbolEntry.FLAG_NONE, fileId, 1, "CSS Class"));
            }
            for (String id : fileIds) {
                entries.add(new IndexSymbolEntry(SymbolKey.cssId(id), id, IndexSymbolEntry.KIND_CSS_ID, IndexSymbolEntry.FLAG_NONE, fileId, 1, "CSS ID"));
            }
        }
    }

    private void indexHtmlFile(File file, Set<String> classNames, Set<String> htmlIds) {
        indexHtmlFile(file, classNames, htmlIds, null, 0);
    }

    private void indexHtmlFile(File file, Set<String> classNames, Set<String> htmlIds, List<IndexSymbolEntry> entries, int fileId) {
        String content = readFile(file);
        if (content == null) return;

        HtmlTokenStream stream = HtmlLexer.tokenize(content);
        ParseResult result = HtmlParser.parse(content, stream);
        HtmlSyntaxTree tree = result != null ? result.htmlTree : null;
        if (tree == null) return;

        Set<String> fileClasses = new HashSet<>();
        Set<String> fileHtmlIds = new HashSet<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == HtmlSyntaxTree.N_ATTRIBUTE) {
                String attrName = tree.nodeName[i];
                String attrValue = tree.nodeValue[i];
                if (attrName == null || attrValue == null) continue;
                if ("id".equalsIgnoreCase(attrName)) {
                    String id = stripQuotes(attrValue).trim();
                    if (id.startsWith("#")) {
                        id = id.substring(1).trim();
                    }
                    if (!id.isEmpty() && isValidHtmlId(id)) {
                        fileHtmlIds.add(id);
                    }
                } else if ("class".equalsIgnoreCase(attrName)) {
                    String unquoted = stripQuotes(attrValue);
                    int vLen = unquoted.length();
                    int start = 0;
                    for (int j = 0; j <= vLen; j++) {
                        if (j == vLen || Character.isWhitespace(unquoted.charAt(j))) {
                            if (j > start) {
                                String cls = unquoted.substring(start, j).trim();
                                if (!cls.isEmpty() && isValidCssClassName(cls)) {
                                    fileClasses.add(cls);
                                }
                            }
                            start = j + 1;
                        }
                    }
                }
            }
        }

        classNames.addAll(fileClasses);
        htmlIds.addAll(fileHtmlIds);
        if (entries != null) {
            for (String c : fileClasses) {
                entries.add(new IndexSymbolEntry(SymbolKey.cssClass(c), c, IndexSymbolEntry.KIND_CSS_CLASS, IndexSymbolEntry.FLAG_NONE, fileId, 1, "CSS Class"));
            }
            for (String id : fileHtmlIds) {
                entries.add(new IndexSymbolEntry(SymbolKey.htmlId(id), id, IndexSymbolEntry.KIND_HTML_ID, IndexSymbolEntry.FLAG_NONE, fileId, 1, "HTML ID"));
            }
        }
    }

    private void indexJsFile(File file, String relPath, List<IndexSymbolEntry> entries, int fileId) {
        indexJsFile(file);
        if (entries != null) {
            String pathKey = file.getAbsolutePath();
            try {
                pathKey = file.getCanonicalPath();
            } catch (Exception ignored) {
            }
            List<CompletionItem> exports = jsFileExports.get(pathKey);
            if (exports != null) {
                for (CompletionItem ci : exports) {
                    String name = ci.getEffectiveInsertText();
                    String key = SymbolKey.jsExport(relPath, name);
                    entries.add(new IndexSymbolEntry(key, name, IndexSymbolEntry.KIND_JS_EXPORT, IndexSymbolEntry.FLAG_EXPORTED, fileId, 1, ci.getDetail()));
                }
            }
            Map<String, List<CompletionItem>> classes = fileClassMembers.get(pathKey);
            if (classes != null) {
                for (Map.Entry<String, List<CompletionItem>> entry : classes.entrySet()) {
                    String className = entry.getKey();
                    for (CompletionItem member : entry.getValue()) {
                        String memberName = member.getLabel();
                        String key = SymbolKey.jsClassMember(className, memberName);
                        entries.add(new IndexSymbolEntry(key, memberName, IndexSymbolEntry.KIND_JS_CLASS_MEMBER, IndexSymbolEntry.FLAG_NONE, fileId, 1, member.getDetail()));
                    }
                }
            }
        }
    }

    private void indexJsFile(File file) {
        String content = readFile(file);
        if (content == null) return;

        TokenStream stream = JsLexer.tokenize(content);
        JsSyntaxTree tree = JsParser.parseTopLevel(content, stream);

        List<CompletionItem> exports = new ArrayList<>();
        Map<String, String> signatures = new HashMap<>();

        // Gather signatures for functions and arrow functions from AST
        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            if (type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_ARROW_FUNC) {
                String name = tree.nodeName[i];
                if (name != null && !name.isEmpty() && isValidJsIdentifier(name)) {
                    StringBuilder sig = new StringBuilder("(");
                    boolean first = true;
                    int child = tree.nodeChild[i];
                    while (child != 0) {
                        if (tree.nodeType[child] == JsSyntaxTree.N_PARAM) {
                            if (!first) sig.append(", ");
                            sig.append(tree.nodeName[child] != null ? tree.nodeName[child] : "arg");
                            first = false;
                        }
                        child = tree.nodeSibling[child];
                    }
                    sig.append(")");
                    signatures.put(name, sig.toString());
                }
            }
        }

        // Gather named exports using JsExportTable
        Set<String> seenExports = new HashSet<>();
        JsExportTable exportTable = JsExportTable.build(tree, file.getAbsolutePath());
        for (int e = 0; e < exportTable.count; e++) {
            String name = exportTable.exportName[e];
            if (name != null && !name.isEmpty() && isValidJsIdentifier(name) && seenExports.add(name)) {
                String sig = signatures.getOrDefault(name, "");
                exports.add(new CompletionItem(name + sig, name, "Export", CompletionItem.Type.VALUE, 0));
            }
        }

        // Handle default export from AST
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_EXPORT && "default".equals(tree.nodeName[i])) {
                int child = tree.nodeChild[i];
                String defName = null;
                if (child != 0 && tree.nodeName[child] != null) {
                    defName = tree.nodeName[child];
                }
                if (defName != null && !defName.isEmpty() && !"default".equals(defName) && isValidJsIdentifier(defName)) {
                    if (seenExports.add(defName)) {
                        String sig = signatures.getOrDefault(defName, "");
                        exports.add(new CompletionItem(defName + sig, defName, "Default Export", CompletionItem.Type.VALUE, 0));
                    }
                } else {
                    String fileName = file.getName();
                    int dotIdx = fileName.lastIndexOf('.');
                    if (dotIdx > 0) fileName = fileName.substring(0, dotIdx);
                    if (isValidJsIdentifier(fileName) && seenExports.add(fileName)) {
                        exports.add(new CompletionItem(fileName, fileName, "Default Export", CompletionItem.Type.VALUE, 0));
                    }
                }
            }
        }

        // Handle module.exports using fast character scanning
        scanModuleExports(content, exports, signatures, seenExports, file);

        synchronized (this) {
            try {
                jsFileExports.put(file.getCanonicalPath(), exports);
            } catch (Exception e) {
                jsFileExports.put(file.getAbsolutePath(), exports);
            }
        }

        // Index class members using AST
        indexClassMembersFromTree(file, tree);
    }

    private void scanModuleExports(String content, List<CompletionItem> exports, Map<String, String> signatures, Set<String> seen, File file) {
        int idx = content.indexOf("module.exports");
        if (idx < 0) return;
        int eq = content.indexOf('=', idx + 14);
        if (eq < 0) return;
        int p = eq + 1;
        while (p < content.length() && Character.isWhitespace(content.charAt(p))) p++;
        if (p < content.length() && content.charAt(p) == '{') {
            int close = content.indexOf('}', p + 1);
            if (close > p) {
                String inner = content.substring(p + 1, close);
                for (String part : inner.split(",")) {
                    String name = part.trim();
                    int commentIdx = name.indexOf("//");
                    if (commentIdx >= 0) name = name.substring(0, commentIdx).trim();
                    if (name.isEmpty()) continue;

                    int colon = name.indexOf(':');
                    int paren = name.indexOf('(');
                    if (colon > 0 && (paren < 0 || colon < paren)) {
                        name = name.substring(0, colon).trim();
                    } else if (paren > 0) {
                        name = name.substring(0, paren).trim();
                    }
                    name = stripQuotes(name).trim();
                    if (isValidJsIdentifier(name) && seen.add(name)) {
                        String sig = signatures.getOrDefault(name, "");
                        exports.add(new CompletionItem(name + sig, name, "module.exports", CompletionItem.Type.VALUE, 0));
                    }
                }
            }
        } else {
            int start = p;
            while (p < content.length() && (Character.isLetterOrDigit(content.charAt(p)) || content.charAt(p) == '_' || content.charAt(p) == '$')) {
                p++;
            }
            String name = (p > start) ? content.substring(start, p) : null;
            if (name == null || name.isEmpty()) {
                String fileName = file.getName();
                int dot = fileName.lastIndexOf('.');
                name = (dot > 0) ? fileName.substring(0, dot) : fileName;
            }
            if (!name.isEmpty() && isValidJsIdentifier(name) && seen.add(name)) {
                exports.add(new CompletionItem(name, name, "module.exports", CompletionItem.Type.VALUE, 0));
            }
        }
    }

    private void indexClassMembersFromTree(File file, JsSyntaxTree tree) {
        Map<String, List<CompletionItem>> fileClasses = new HashMap<>();

        for (int i = 1; i < tree.nodeCount; i++) {
            int nodeType = tree.nodeType[i];
            if (nodeType == JsSyntaxTree.N_CLASS_DECL || nodeType == JsSyntaxTree.N_INTERFACE || nodeType == JsSyntaxTree.N_ENUM || nodeType == JsSyntaxTree.N_TYPE_ALIAS) {
                String className = tree.nodeName[i];
                if (className == null || className.isEmpty()) continue;

                List<CompletionItem> members = new ArrayList<>();
                Set<String> seen = new HashSet<>();

                // Traverse explicit AST member children
                int child = tree.nodeChild[i];
                while (child != 0) {
                    int cType = tree.nodeType[child];
                    String name = tree.nodeName[child];
                    if (name != null && !name.isEmpty() && !seen.contains(name) && !isIgnoredProp(name)
                            && (isValidJsIdentifier(name) || "constructor".equals(name))) {
                        if (cType == JsSyntaxTree.N_METHOD || cType == JsSyntaxTree.N_GETTER || cType == JsSyntaxTree.N_SETTER) {
                            seen.add(name);
                            members.add(new CompletionItem(name, name + "(|)", className + " method", CompletionItem.Type.FUNCTION, 0));
                        } else if (cType == JsSyntaxTree.N_PROPERTY) {
                            seen.add(name);
                            members.add(new CompletionItem(name, name, className + " property", CompletionItem.Type.VALUE, 0));
                        }
                    }
                    child = tree.nodeSibling[child];
                }

                // Extract object type properties for type aliases
                if (nodeType == JsSyntaxTree.N_TYPE_ALIAS && tree.nodeTypeAnn[i] != null) {
                    Map<String, String> propMap = com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine.extractObjectTypePropertyMap(tree.nodeTypeAnn[i]);
                    for (Map.Entry<String, String> entry : propMap.entrySet()) {
                        String pName = entry.getKey();
                        String pType = entry.getValue();
                        if (pName != null && !pName.isEmpty() && isValidJsIdentifier(pName) && seen.add(pName)) {
                            boolean isMethod = pType.startsWith("(") || pType.contains("=>");
                            String insert = isMethod ? pName + "(|)" : pName;
                            CompletionItem.Type type = isMethod ? CompletionItem.Type.FUNCTION : CompletionItem.Type.VALUE;
                            members.add(new CompletionItem(pName, insert, className + " property", type, 0));
                        }
                    }
                }

                // Add constructor assignments (e.g. this.x = 1) from shapeTable
                String[] shapes = tree.shapeTable.get(i);
                if (shapes != null) {
                    for (String key : shapes) {
                        if (key != null && !key.isEmpty() && !seen.contains(key) && !isIgnoredProp(key)
                                && isValidJsIdentifier(key)) {
                            seen.add(key);
                            members.add(new CompletionItem(key, key, className + " property", CompletionItem.Type.VALUE, 0));
                        }
                    }
                }

                fileClasses.put(className, members);
            }
        }

        synchronized (this) {
            try {
                fileClassMembers.put(file.getCanonicalPath(), fileClasses);
                classMembers.putAll(fileClasses);
            } catch (Exception e) {
                fileClassMembers.put(file.getAbsolutePath(), fileClasses);
                classMembers.putAll(fileClasses);
            }
        }
    }

    private boolean hasDocument(File file) {
        if (file == null) return false;
        if (com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getDocument(file.getAbsolutePath()) != null)
            return true;
        try {
            if (com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getDocument(file.getCanonicalPath()) != null)
                return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private String readFile(File file) {
        String queryPath = file.getAbsolutePath();
        try {
            queryPath = file.getCanonicalPath();
        } catch (Exception ignored) {
        }

        com.cocode.vcode.ide.core.lsp.LspDocument doc = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getDocument(queryPath);
        if (doc == null && !queryPath.equals(file.getAbsolutePath())) {
            doc = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getDocument(file.getAbsolutePath());
        }
        if (doc != null && doc.text != null) {
            return doc.text;
        }

        try {
            if (file.length() > 500 * 1024) return null; // Skip files > 500KB
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int read;
                while ((read = br.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized List<CompletionItem> getCssClassItems() {
        return new ArrayList<>(cssClassItems);
    }

    public synchronized List<CompletionItem> getCssIdItems() {
        return new ArrayList<>(cssIdItems);
    }

    public synchronized List<CompletionItem> getHtmlIdItems() {
        return new ArrayList<>(htmlIdItems);
    }

    public synchronized List<CompletionItem> getClassMembers(String className) {
        List<CompletionItem> members = classMembers.get(className);
        return members != null ? new ArrayList<>(members) : new ArrayList<>();
    }

    public synchronized Set<String> getAllClassNames() {
        return new HashSet<>(classMembers.keySet());
    }

    public synchronized List<CompletionItem> getExportsForPath(File currentFile, String importPath) {
        if (currentFile == null || importPath == null) return new ArrayList<>();

        File parent = currentFile.getParentFile();
        if (parent == null) return new ArrayList<>();

        File targetFile = new File(parent, importPath);
        if (!targetFile.exists() && !hasDocument(targetFile) && !importPath.endsWith(".js") && !importPath.endsWith(".ts")) {
            File jsTarget = new File(parent, importPath + ".js");
            if (jsTarget.exists() || hasDocument(jsTarget)) {
                targetFile = jsTarget;
            } else {
                targetFile = new File(parent, importPath + ".ts");
            }
        }

        List<CompletionItem> exports = null;
        try {
            exports = jsFileExports.get(targetFile.getCanonicalPath());
        } catch (Exception e) {
            exports = jsFileExports.get(targetFile.getAbsolutePath());
        }

        // If not indexed yet but file exists or has in-memory document, index it now (on-demand, synchronous)
        boolean canRead = targetFile.exists() || hasDocument(targetFile);
        if ((exports == null || exports.isEmpty()) && canRead) {
            indexJsFile(targetFile);
            try {
                exports = jsFileExports.get(targetFile.getCanonicalPath());
            } catch (Exception e) {
                exports = jsFileExports.get(targetFile.getAbsolutePath());
            }
        }

        return exports != null ? new ArrayList<>(exports) : new ArrayList<>();
    }

    public synchronized void invalidateFile(String absolutePath) {
        try {
            String canonical = new File(absolutePath).getCanonicalPath();
            jsFileExports.remove(canonical);
            fileClassMembers.remove(canonical);
        } catch (Exception e) {
            // fall back to absolute
        }
        jsFileExports.remove(absolutePath);
        fileClassMembers.remove(absolutePath);
        if (segmentedStore != null && projectRoot != null) {
            String rel = SegmentedSymbolStore.getRelativePath(new File(projectRoot), new File(absolutePath));
            segmentedStore.invalidateFile(rel);
        }
    }

    private void loadCachedSymbolsIntoMemory() {
        if (segmentedStore == null) return;
        Set<String> classNames = new HashSet<>();
        Set<String> cssIds = new HashSet<>();
        Set<String> htmlIds = new HashSet<>();

        List<IndexSymbolEntry> cachedClasses = segmentedStore.query(SegmentSearchQuery.prefix(SymbolKey.PREFIX_CSS_CLASS, 0));
        for (IndexSymbolEntry e : cachedClasses) {
            classNames.add(e.getName());
        }
        List<IndexSymbolEntry> cachedCssIds = segmentedStore.query(SegmentSearchQuery.prefix(SymbolKey.PREFIX_CSS_ID, 0));
        for (IndexSymbolEntry e : cachedCssIds) {
            cssIds.add(e.getName());
        }
        List<IndexSymbolEntry> cachedHtmlIds = segmentedStore.query(SegmentSearchQuery.prefix(SymbolKey.PREFIX_HTML_ID, 0));
        for (IndexSymbolEntry e : cachedHtmlIds) {
            htmlIds.add(e.getName());
        }

        List<IndexSymbolEntry> cachedExports = segmentedStore.query(SegmentSearchQuery.prefix(SymbolKey.PREFIX_JS_EXPORT, 0));
        Map<String, List<CompletionItem>> exportsMap = new HashMap<>();
        for (IndexSymbolEntry e : cachedExports) {
            String key = e.getKey();
            if (key != null && key.startsWith(SymbolKey.PREFIX_JS_EXPORT)) {
                int hashIdx = key.indexOf('#');
                if (hashIdx > SymbolKey.PREFIX_JS_EXPORT.length()) {
                    String relPath = key.substring(SymbolKey.PREFIX_JS_EXPORT.length(), hashIdx);
                    File file = new File(projectRoot != null ? projectRoot : "", relPath);
                    String absPath = file.getAbsolutePath();
                    String name = e.getName();
                    CompletionItem item = new CompletionItem(name, name, e.getDetail(), CompletionItem.Type.VALUE, 0);
                    List<CompletionItem> list = exportsMap.computeIfAbsent(absPath, k -> new ArrayList<>());
                    list.add(item);
                    try {
                        String canonPath = file.getCanonicalPath();
                        if (!canonPath.equals(absPath)) {
                            List<CompletionItem> cList = exportsMap.computeIfAbsent(canonPath, k -> new ArrayList<>());
                            cList.add(item);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        List<IndexSymbolEntry> cachedMembers = segmentedStore.query(SegmentSearchQuery.prefix(SymbolKey.PREFIX_JS_CLASS, 0));
        Map<String, List<CompletionItem>> classMembersMap = new HashMap<>();
        for (IndexSymbolEntry e : cachedMembers) {
            String key = e.getKey();
            if (key != null && key.startsWith(SymbolKey.PREFIX_JS_CLASS)) {
                int dotIdx = key.indexOf('.');
                if (dotIdx > SymbolKey.PREFIX_JS_CLASS.length()) {
                    String className = key.substring(SymbolKey.PREFIX_JS_CLASS.length(), dotIdx);
                    String memberName = e.getName();
                    boolean isMethod = e.getDetail() != null && e.getDetail().contains("method");
                    String insert = isMethod ? memberName + "(|)" : memberName;
                    CompletionItem.Type type = isMethod ? CompletionItem.Type.FUNCTION : CompletionItem.Type.VALUE;
                    CompletionItem item = new CompletionItem(memberName, insert, e.getDetail(), type, 0);
                    List<CompletionItem> list = classMembersMap.computeIfAbsent(className, k -> new ArrayList<>());
                    list.add(item);
                }
            }
        }

        synchronized (this) {
            cssClassItems.clear();
            for (String c : classNames) {
                cssClassItems.add(new CompletionItem(c, c, "CSS Class", CompletionItem.Type.CSS_VALUE, 0));
            }
            cssIdItems.clear();
            for (String id : cssIds) {
                cssIdItems.add(new CompletionItem(id, id, "CSS ID", CompletionItem.Type.CSS_VALUE, 0));
            }
            htmlIdItems.clear();
            for (String id : htmlIds) {
                htmlIdItems.add(new CompletionItem(id, id, "HTML ID", CompletionItem.Type.VALUE, 0));
            }
            jsFileExports.clear();
            jsFileExports.putAll(exportsMap);
            classMembers.clear();
            classMembers.putAll(classMembersMap);
        }
    }

    /**
     * Silently re-indexes a single modified file in the background without showing any UI or progress card.
     * Extracts updated symbols and reflects them in in-memory completion lists and the disk segment cache.
     *
     * @param file the modified file
     */
    public void updateFileSilently(File file) {
        if (file == null) return;
        String name = file.getName().toLowerCase();
        boolean isCss = name.endsWith(".css") || name.endsWith(".scss");
        boolean isHtml = name.endsWith(".html") || name.endsWith(".htm");
        boolean isJs = name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".jsx")
                || name.endsWith(".tsx") || name.endsWith(".mjs");
        if (!isCss && !isHtml && !isJs) return;

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                if (isJs) {
                    indexJsFile(file);
                }

                if (isCss || isHtml) {
                    Set<String> classNames = new HashSet<>();
                    Set<String> ids = new HashSet<>();
                    if (isCss) {
                        indexCssFile(file, classNames, ids);
                        synchronized (this) {
                            for (String c : classNames) {
                                boolean exists = false;
                                for (CompletionItem ci : cssClassItems) {
                                    if (c.equals(ci.getLabel())) { exists = true; break; }
                                }
                                if (!exists) {
                                    cssClassItems.add(new CompletionItem(c, c, "CSS Class", CompletionItem.Type.CSS_VALUE, 0));
                                }
                            }
                            for (String id : ids) {
                                boolean exists = false;
                                for (CompletionItem ci : cssIdItems) {
                                    if (id.equals(ci.getLabel())) { exists = true; break; }
                                }
                                if (!exists) {
                                    cssIdItems.add(new CompletionItem(id, id, "CSS ID", CompletionItem.Type.CSS_VALUE, 0));
                                }
                            }
                        }
                    } else {
                        indexHtmlFile(file, classNames, ids);
                        synchronized (this) {
                            for (String c : classNames) {
                                boolean exists = false;
                                for (CompletionItem ci : cssClassItems) {
                                    if (c.equals(ci.getLabel())) { exists = true; break; }
                                }
                                if (!exists) {
                                    cssClassItems.add(new CompletionItem(c, c, "CSS Class", CompletionItem.Type.CSS_VALUE, 0));
                                }
                            }
                            for (String id : ids) {
                                boolean exists = false;
                                for (CompletionItem ci : htmlIdItems) {
                                    if (id.equals(ci.getLabel())) { exists = true; break; }
                                }
                                if (!exists) {
                                    htmlIdItems.add(new CompletionItem(id, id, "HTML ID", CompletionItem.Type.VALUE, 0));
                                }
                            }
                        }
                    }
                }

                if (segmentedStore != null && projectRoot != null) {
                    File rootDir = new File(projectRoot);
                    segmentedStore.indexSingleFileSilently(rootDir, file, (f, relPath, fileId) -> {
                        List<IndexSymbolEntry> entries = new ArrayList<>();
                        Set<String> dummyCls = new HashSet<>();
                        Set<String> dummyIds = new HashSet<>();
                        if (isCss) {
                            indexCssFile(f, dummyCls, dummyIds, entries, fileId);
                        } else if (isHtml) {
                            indexHtmlFile(f, dummyCls, dummyIds, entries, fileId);
                        } else if (isJs) {
                            indexJsFile(f, relPath, entries, fileId);
                        }
                        return entries;
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Re-indexes a single JS/TS file's exports using the latest in-memory snapshot
     * from {@link com.cocode.vcode.ide.core.lsp.ProjectIndex}.
     */
    public void updateFileFromIndex(File file) {
        updateFileSilently(file);
    }
}

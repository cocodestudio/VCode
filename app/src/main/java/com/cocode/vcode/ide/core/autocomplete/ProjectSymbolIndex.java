package com.cocode.vcode.ide.core.autocomplete;

import androidx.annotation.NonNull;

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

/**
 * Global project indexer that extracts CSS classes, IDs, HTML IDs, and JS/TS exports
 * and class members for cross-file intellisense using pure AST parsing.
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
    private String projectRoot = null;
    // Guards against multiple concurrent buildIndex() calls for the same root.
    private final java.util.concurrent.atomic.AtomicBoolean isBuilding = new java.util.concurrent.atomic.AtomicBoolean(false);

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

    public void buildIndex(File rootDir) {
        if (rootDir == null) return;
        // Use a CAS guard to avoid concurrent rebuilds. Unlike the old project-root equality
        // check (which permanently prevented re-indexing after the first build), this guard
        // only blocks concurrent runs — a subsequent call after the previous one completes
        // will proceed normally, picking up any new files added to the project.
        if (!isBuilding.compareAndSet(false, true)) return;

        projectRoot = rootDir.getAbsolutePath();
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                Set<String> classNames = new HashSet<>();
                Set<String> cssIds = new HashSet<>();
                Set<String> htmlIds = new HashSet<>();

                indexDirectoryRecursively(rootDir, classNames, cssIds, htmlIds);

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
                }
            } finally {
                isBuilding.set(false);
            }
        });
    }

    private void indexDirectoryRecursively(File dir, Set<String> classNames, Set<String> cssIds, Set<String> htmlIds) {
        List<File> files = com.cocode.vcode.ide.core.autocomplete.VFSManager.getInstance().listCachedFiles(dir);
        if (files == null) {
            // Fallback if VFS isn't built yet
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
                indexDirectoryRecursively(f, classNames, cssIds, htmlIds);
            } else {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".css")) {
                    indexCssFile(f, classNames, cssIds);
                } else if (name.endsWith(".html") || name.endsWith(".htm")) {
                    indexHtmlFile(f, classNames, htmlIds);
                } else if (name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".jsx") || name.endsWith(".tsx") || name.endsWith(".mjs")) {
                    indexJsFile(f);
                }
            }
        }
    }

    private void indexCssFile(File file, Set<String> classNames, Set<String> cssIds) {
        String content = readFile(file);
        if (content == null) return;

        CssTokenStream stream = CssLexer.tokenize(content);
        CssSyntaxTree tree = CssParser.parse(stream, content);

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == CssSyntaxTree.N_SELECTOR) {
                String sel = tree.nodeName[i];
                if (sel != null && !sel.isEmpty()) {
                    extractCssClassesAndIds(sel, classNames, cssIds);
                }
            }
        }
    }

    private static void extractCssClassesAndIds(String selector, Set<String> classNames, Set<String> cssIds) {
        int len = selector.length();
        int i = 0;
        while (i < len) {
            char c = selector.charAt(i);
            if (c == '.' || c == '#') {
                boolean isClass = (c == '.');
                i++;
                int start = i;
                while (i < len) {
                    char ch = selector.charAt(i);
                    if (ch == '_' || ch == '-' || Character.isLetterOrDigit(ch)) {
                        i++;
                    } else {
                        break;
                    }
                }
                if (i > start) {
                    String name = selector.substring(start, i);
                    if (isClass) {
                        classNames.add(name);
                    } else {
                        cssIds.add(name);
                    }
                }
            } else {
                i++;
            }
        }
    }

    private void indexHtmlFile(File file, Set<String> classNames, Set<String> htmlIds) {
        String content = readFile(file);
        if (content == null) return;

        HtmlTokenStream stream = HtmlLexer.tokenize(content);
        ParseResult result = HtmlParser.parse(content, stream);
        HtmlSyntaxTree tree = result != null ? result.htmlTree : null;
        if (tree == null) return;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == HtmlSyntaxTree.N_ATTRIBUTE) {
                String attrName = tree.nodeName[i];
                String attrValue = tree.nodeValue[i];
                if (attrName == null || attrValue == null) continue;
                if ("id".equalsIgnoreCase(attrName)) {
                    String id = stripQuotes(attrValue).trim();
                    if (!id.isEmpty()) {
                        htmlIds.add(id);
                    }
                } else if ("class".equalsIgnoreCase(attrName)) {
                    String unquoted = stripQuotes(attrValue);
                    int vLen = unquoted.length();
                    int start = 0;
                    for (int j = 0; j <= vLen; j++) {
                        if (j == vLen || Character.isWhitespace(unquoted.charAt(j))) {
                            if (j > start) {
                                classNames.add(unquoted.substring(start, j));
                            }
                            start = j + 1;
                        }
                    }
                }
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

    private void indexJsFile(File file) {
        String content = readFile(file);
        if (content == null) return;

        TokenStream stream = JsLexer.tokenize(content);
        JsSyntaxTree tree = JsParser.parseTopLevel(content, stream);

        List<CompletionItem> exports = new ArrayList<>();
        Map<String, String> signatures = new HashMap<>();

        // 1. Gather signatures for functions and arrow functions from AST
        for (int i = 1; i < tree.nodeCount; i++) {
            int type = tree.nodeType[i];
            if (type == JsSyntaxTree.N_FUNC_DECL || type == JsSyntaxTree.N_ARROW_FUNC) {
                String name = tree.nodeName[i];
                if (name != null && !name.isEmpty()) {
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

        // 2. Gather named exports using JsExportTable
        Set<String> seenExports = new HashSet<>();
        JsExportTable exportTable = JsExportTable.build(tree, file.getAbsolutePath());
        for (int e = 0; e < exportTable.count; e++) {
            String name = exportTable.exportName[e];
            if (name != null && !name.isEmpty() && seenExports.add(name)) {
                String sig = signatures.getOrDefault(name, "");
                exports.add(new CompletionItem(name + sig, name, "Export", CompletionItem.Type.VALUE, 0));
            }
        }

        // 3. Handle default export from AST
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_EXPORT && "default".equals(tree.nodeName[i])) {
                int child = tree.nodeChild[i];
                String defName = null;
                if (child != 0 && tree.nodeName[child] != null) {
                    defName = tree.nodeName[child];
                }
                if (defName != null && !defName.isEmpty() && !"default".equals(defName)) {
                    if (seenExports.add(defName)) {
                        String sig = signatures.getOrDefault(defName, "");
                        exports.add(new CompletionItem(defName + sig, defName, "Default Export", CompletionItem.Type.VALUE, 0));
                    }
                } else {
                    String fileName = file.getName();
                    int dotIdx = fileName.lastIndexOf('.');
                    if (dotIdx > 0) fileName = fileName.substring(0, dotIdx);
                    if (seenExports.add(fileName)) {
                        exports.add(new CompletionItem(fileName, fileName, "Default Export", CompletionItem.Type.VALUE, 0));
                    }
                }
            }
        }

        // 4. Handle module.exports using fast character scanning
        scanModuleExports(content, exports, signatures, seenExports, file);

        synchronized (this) {
            try {
                jsFileExports.put(file.getCanonicalPath(), exports);
            } catch (Exception e) {
                jsFileExports.put(file.getAbsolutePath(), exports);
            }
        }

        // 5. Index class members using AST
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
                    int colon = name.indexOf(':');
                    if (colon > 0) name = name.substring(0, colon).trim();
                    if (!name.isEmpty() && seen.add(name)) {
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
            if (!name.isEmpty() && seen.add(name)) {
                exports.add(new CompletionItem(name, name, "module.exports", CompletionItem.Type.VALUE, 0));
            }
        }
    }

    private void indexClassMembersFromTree(File file, JsSyntaxTree tree) {
        Map<String, List<CompletionItem>> fileClasses = new HashMap<>();

        for (int i = 1; i < tree.nodeCount; i++) {
            int nodeType = tree.nodeType[i];
            if (nodeType == JsSyntaxTree.N_CLASS_DECL || nodeType == JsSyntaxTree.N_INTERFACE || nodeType == JsSyntaxTree.N_ENUM) {
                String className = tree.nodeName[i];
                if (className == null || className.isEmpty()) continue;

                List<CompletionItem> members = new ArrayList<>();
                Set<String> seen = new HashSet<>();

                // Traverse explicit AST member children
                int child = tree.nodeChild[i];
                while (child != 0) {
                    int cType = tree.nodeType[child];
                    String name = tree.nodeName[child];
                    if (name != null && !name.isEmpty() && !seen.contains(name) && !isIgnoredProp(name)) {
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

                // Add constructor assignments (e.g. this.x = 1) from shapeTable
                String[] shapes = tree.shapeTable.get(i);
                if (shapes != null) {
                    for (String key : shapes) {
                        if (key != null && !key.isEmpty() && !seen.contains(key) && !isIgnoredProp(key)) {
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

    private static boolean isIgnoredProp(String name) {
        return "constructor".equals(name) || "prototype".equals(name) || "function".equals(name);
    }

    private boolean hasDocument(File file) {
        if (file == null) return false;
        if (com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getDocument(file.getAbsolutePath()) != null) return true;
        try {
            if (com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance().getDocument(file.getCanonicalPath()) != null) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private String readFile(File file) {
        String queryPath = file.getAbsolutePath();
        try {
            queryPath = file.getCanonicalPath();
        } catch (Exception ignored) {}
        
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
    }

    /**
     * Re-indexes a single JS/TS file's exports using the latest in-memory snapshot
     * from {@link com.cocode.vcode.ide.core.lsp.ProjectIndex}. This ensures the
     * export cache stays current even when auto-save is off, by reading from the live
     * editor buffer rather than disk.
     *
     * <p>Called from {@link com.cocode.vcode.ide.core.lsp.LspEditorBridge#setFile(java.io.File)}
     * whenever the user switches away from a file.
     *
     * @param file the file to re-index (no-op if not a JS/TS variant)
     */
    public void updateFileFromIndex(File file) {
        if (file == null) return;
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".js") && !name.endsWith(".ts") && !name.endsWith(".jsx")
                && !name.endsWith(".tsx") && !name.endsWith(".mjs")) return;
        // Re-index on the IO thread. indexJsFile() atomically puts results into jsFileExports,
        // replacing any stale entry only when new data is ready. No eager invalidation needed.
        ExecutorProvider.getInstance().runOnIo(() -> indexJsFile(file));
    }
}

package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.core.editor.search.SearchEngine;
import com.cocode.vcode.ide.core.model.SearchResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Project-wide symbol index.
 * <p>
 * Maintains an in-memory snapshot of every source file in the open project and the
 * symbols declared in each file. Used by language servers for:
 * <ul>
 *   <li>Cross-file completion (e.g. JS {@code import { foo } from './bar'})</li>
 *   <li>Go to Definition across files</li>
 *   <li>Find All References across files</li>
 * </ul>
 *
 * <p><b>Thread safety:</b> all mutable state is guarded by {@link ConcurrentHashMap}.
 * Public query methods may be called from any thread. Indexing happens on the IO
 * thread pool via {@link ExecutorProvider}.</p>
 *
 * <p><b>Lifecycle:</b> call {@link #indexProject(File, Runnable)} once when a project
 * is opened. Call {@link #updateDocument(LspDocument)} whenever a file is saved.
 * Call {@link #clear()} when the project is closed.</p>
 */
public final class ProjectIndex {

    /**
     * Singleton per app session. Replaced when a new project is opened.
     */
    private static volatile ProjectIndex sInstance;
    /**
     * Full text of every indexed file, keyed by absolute file path (URI).
     */
    private final ConcurrentHashMap<String, LspDocument> documents = new ConcurrentHashMap<>();
    /**
     * Symbols declared in each file, keyed by absolute file path.
     * Updated after a file is (re-)indexed.
     */
    private final ConcurrentHashMap<String, List<SymbolEntry>> fileSymbols = new ConcurrentHashMap<>();
    
    /**
     * Cache of the latest ParseResult for each file, keyed by absolute file path.
     */
    private final ConcurrentHashMap<String, com.cocode.vcode.ide.core.language.js.ParseResult> parseResults = new ConcurrentHashMap<>();

    /**
     * Per-file export table, keyed by absolute file path. Populated
     * from {@code parseResults} on every parse cycle.
     * Never read from disk.
     */
    private final ConcurrentHashMap<String, com.cocode.vcode.ide.core.language.js.JsExportTable> exportTables = new ConcurrentHashMap<>();
    /**
     * Absolute path of the currently indexed project root.
     */
    private volatile String projectRoot;
    private final java.util.concurrent.atomic.AtomicBoolean isIncrementalScanRunning = new java.util.concurrent.atomic.AtomicBoolean(false);

    private ProjectIndex() {
    }

    public static ProjectIndex getInstance() {
        if (sInstance == null) {
            synchronized (ProjectIndex.class) {
                if (sInstance == null) sInstance = new ProjectIndex();
            }
        }
        return sInstance;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    private static boolean isSupportedFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".html") || name.endsWith(".htm")
                || name.endsWith(".css") || name.endsWith(".scss")
                || name.endsWith(".js") || name.endsWith(".ts")
                || name.endsWith(".json") || name.endsWith(".md")
                || name.endsWith(".svg");
    }

    private static String getLanguageId(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".scss")) return "scss";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".svg")) return "svg";
        return "plaintext";
    }

    // -------------------------------------------------------------------------
    // Incremental update
    // -------------------------------------------------------------------------

    private static String readFile(File file) throws Exception {
        if (file.length() > 1024 * 1024) return ""; // 1 MB limit
        return com.cocode.vcode.ide.utils.FileUtils.readFile(file);
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /**
     * Recursively indexes all supported source files under {@code root} on the IO thread pool.
     * <p>
     * <strong>WARNING: This method clears all existing in-memory snapshots.</strong> Only use
     * this for a full reset (e.g. when the project itself changes). For normal tab-switch
     * scenarios, use {@link #indexProjectIncremental(File)} instead so live editor data
     * is not discarded.
     *
     * @param root       project root directory
     * @param onComplete optional callback, invoked on the main thread when indexing is done
     */
    public void indexProject(File root, Runnable onComplete) {
        projectRoot = root.getAbsolutePath();

        ExecutorProvider.getInstance().runOnIo(() -> {
            documents.clear();
            fileSymbols.clear();
            indexDirectory(root);
            if (onComplete != null) {
                ExecutorProvider.getInstance().runOnMain(onComplete);
            }
        });
    }

    /**
     * Indexes all source files under {@code root} that are <em>not</em> already tracked
     * in the in-memory document cache. This is the preferred method for the initial
     * project scan triggered during a session, because it never overwrites live unsaved
     * editor content with stale data from disk.
     *
     * <p>Files already present in {@link #documents} (i.e. currently open in the editor)
     * are skipped — their live snapshot is already the authoritative source of truth.
     *
     * @param root project root directory
     */
    public void indexProjectIncremental(File root) {
        if (root == null) return;
        if (!isIncrementalScanRunning.compareAndSet(false, true)) return;
        projectRoot = root.getAbsolutePath();
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                indexDirectoryIncremental(root);
            } finally {
                isIncrementalScanRunning.set(false);
            }
        });
    }

    /**
     * Clears all indexed data. Should be called when the project is closed.
     */
    public void clear() {
        projectRoot = null;
        documents.clear();
        fileSymbols.clear();
        parseResults.clear();
        exportTables.clear();
        LspEditorBridge.resetProjectSession();
    }
    
    public void updateParseResult(String uri, com.cocode.vcode.ide.core.language.js.ParseResult result) {
        if (uri == null) return;
        // Canonicalize the key so that look-ups by
        // {@code new File(...).getAbsolutePath()} (used by callers
        // like the completion engine) match the stored key.
        String key = canonicalize(uri);
        if (result == null) {
            parseResults.remove(key);
            exportTables.remove(key);
        } else {
            parseResults.put(key, result);
            // rebuild the per-file export table from the
            // freshly-parsed tree. This runs on the calling thread
            // (the diagnostic thread); the old table is replaced
            // atomically by the concurrent map's put.
            exportTables.put(key,
                    com.cocode.vcode.ide.core.language.js.JsExportTable.build(
                            result.tree, key));
        }
    }

    /**
     * Convert a possibly-relative path into the canonical
     * {@code File.getAbsolutePath()} form so that all readers
     * (callers using {@code new File(uri).getAbsolutePath()}) and
     * writers (the parse pipeline, the file scanner) compare equal.
     * Returns {@code null} for null input; returns the input as-is
     * if it cannot be canonicalized.
     */
    private static String canonicalize(String uri) {
        if (uri == null) return null;
        try {
            return new java.io.File(uri).getAbsolutePath();
        } catch (Exception e) {
            return uri;
        }
    }

    public com.cocode.vcode.ide.core.language.js.ParseResult getParseResult(String uri) {
        if (uri == null) return null;
        return parseResults.get(canonicalize(uri));
    }

    /**
     * Retrieves the ParseResult for the given JS/TS file, or parses it on demand if not
     * yet cached. If the file is open in the editor, its live document snapshot is used.
     *
     * @param file the file to get or parse
     * @return the ParseResult, or null if file cannot be read
     */
    public com.cocode.vcode.ide.core.language.js.ParseResult getOrParseJsFile(File file) {
        if (file == null || file.length() > 500 * 1024) return null; // 500 KB safety limit
        String uri = file.getAbsolutePath();
        com.cocode.vcode.ide.core.language.js.ParseResult cached = getParseResult(uri);
        if (cached != null && cached.tree != null) {
            return cached;
        }

        try {
            LspDocument doc = getDocument(uri);
            String content = (doc != null && doc.text != null) ? doc.text : com.cocode.vcode.ide.utils.FileUtils.readFile(file);
            if (content != null) {
                com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(content);
                com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = com.cocode.vcode.ide.core.language.js.JsParser.parseFull(content, tokens);
                if (tree != null) {
                    tree.buildNodesByOffset();
                }
                com.cocode.vcode.ide.core.language.js.ScopeTree scopeTree = com.cocode.vcode.ide.core.language.js.ScopeTree.build(tree);
                com.cocode.vcode.ide.core.language.js.ParseResult pr = new com.cocode.vcode.ide.core.language.js.ParseResult(
                        file, content, tokens, tree, scopeTree, com.cocode.vcode.ide.core.language.js.ParseResult.MODE_FULL);
                updateParseResult(uri, pr);
                return pr;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Returns the export table for a single file, or null if the
     * file's parse result has not been published yet.
     */
    public com.cocode.vcode.ide.core.language.js.JsExportTable getExportTable(String uri) {
        if (uri == null) return null;
        return exportTables.get(canonicalize(uri));
    }

    /**
     * Project-wide prefix query: returns every export
     * across the project whose name starts with {@code prefix} (case
     * sensitive). The {@code ExportRef} carries the source file URI
     * so callers can compute the import path.
     *
     * <p>Allocations: one {@link ArrayList} per call. Each table's
     * prefix scan is O(N) in the table's size, so the total is
     * O(sum of table sizes) bounded by the cap of 50 results.
     */
    public List<ExportRef> getExportsByPrefix(String prefix) {
        if (prefix == null) prefix = "";
        List<ExportRef> out = new ArrayList<>();
        for (com.cocode.vcode.ide.core.language.js.JsExportTable table : exportTables.values()) {
            for (int i = 0; i < table.count; i++) {
                String name = table.exportName[i];
                if (name == null) continue;
                if (name.startsWith(prefix)) {
                    out.add(new ExportRef(name, table.exportSourceUri[i]));
                    if (out.size() >= 50) return out;
                }
            }
        }
        return out;
    }

    /** A (name, sourceUri) pair returned by
     *  {@link #getExportsByPrefix(String)}. The sourceUri is the
     *  absolute path of the file that declared the export. */
    public static final class ExportRef {
        public final String name;
        public final String sourceUri;
        public ExportRef(String name, String sourceUri) {
            this.name = name;
            this.sourceUri = sourceUri;
        }
    }

    /**
     * Updates the index with the latest snapshot of a single document.
     * Call this every time a file is saved or its in-memory content changes significantly.
     *
     * @param doc updated document snapshot
     */
    public void updateDocument(LspDocument doc) {
        documents.put(doc.uri, doc);
        // Re-extract symbols for this single file in the background
        ExecutorProvider.getInstance().runOnIo(() -> {
            List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(doc);
            fileSymbols.put(doc.uri, symbols);
        });
    }

    /**
     * Removes the extracted symbols for the given URI, forcing a re-extract on next access if needed.
     */
    public void invalidateSymbols(String uri) {
        fileSymbols.remove(uri);
    }

    /**
     * Re-reads the file from disk and updates its document and symbol index.
     * Useful when switching away from a file to ensure disk changes are picked up.
     */
    public void reindexFile(File file) {
        String uri = file.getAbsolutePath();
        LspDocument existing = documents.get(uri);
        if (existing != null) {
            // Re-extract symbols from the already-up-to-date in-memory snapshot
            ExecutorProvider.getInstance().runOnIo(() -> {
                List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(existing);
                fileSymbols.put(uri, symbols);
            });
        } else {
            ExecutorProvider.getInstance().runOnIo(() -> indexFile(file));
        }
    }

    /**
     * Removes the live in-memory snapshot for the file and re-reads it from disk.
     * Call this when a file is closed in the editor to ensure the index reflects
     * the true disk state, discarding any unsaved changes that were kept in memory.
     */
    public void revertToDisk(File file) {
        String uri = file.getAbsolutePath();
        documents.remove(uri);
        ExecutorProvider.getInstance().runOnIo(() -> indexFile(file));
    }


    /**
     * Returns the latest document snapshot for the given file path, or null if not indexed.
     *
     * @param uri absolute file path
     */
    public LspDocument getDocument(String uri) {
        return documents.get(uri);
    }



    /**
     * Finds all symbols whose name starts with the given prefix (case-insensitive).
     * Searches across every indexed file.
     *
     * @param prefix the symbol name prefix to match
     * @return list of matching symbols, never null
     */
    public List<SymbolEntry> findSymbolsByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        String lower = prefix.toLowerCase();
        List<SymbolEntry> results = new ArrayList<>();
        for (List<SymbolEntry> symbols : fileSymbols.values()) {
            for (SymbolEntry s : symbols) {
                if (s.name.toLowerCase().startsWith(lower)) {
                    results.add(s);
                    if (results.size() >= 50) return results; // cap results
                }
            }
        }
        return results;
    }

    /**
     * Finds all declarations of the exact symbol name across the project.
     *
     * @param name exact symbol name
     * @return list of locations where the symbol is declared
     */
    public List<LspLocation> findDefinitions(String name) {
        List<LspLocation> locations = new ArrayList<>();
        for (List<SymbolEntry> symbols : fileSymbols.values()) {
            for (SymbolEntry s : symbols) {
                if (s.name.equals(name)) {
                    locations.add(new LspLocation(s.uri, s.range));
                }
            }
        }
        return locations;
    }

    /**
     * Scans the in-memory document cache to find all usages/references of a specific filename.
     * Uses the SearchEngine to find exact locations within the file text.
     *
     * @param filename the name of the file to search for
     * @return list of locations where the filename is referenced
     */
    public List<LspLocation> findFileUsages(String filename) {
        List<LspLocation> locations = new ArrayList<>();
        if (filename == null || filename.isEmpty()) return locations;

        SearchEngine searchEngine = new SearchEngine();
        for (LspDocument doc : documents.values()) {
            if (doc.text != null && doc.text.contains(filename)) {
                List<SearchResult> results = searchEngine.find(filename, doc.text, false, false, false);
                for (SearchResult r : results) {
                    // SearchResult is 1-indexed for line/column. LspRange is 0-indexed.
                    LspRange range = new LspRange(
                            new LspPosition(r.lineNumber - 1, r.columnStart - 1),
                            new LspPosition(r.lineNumber - 1, r.columnStart - 1 + filename.length())
                    );
                    locations.add(new LspLocation(doc.uri, range));
                }
            }
        }
        return locations;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns all symbols declared in a specific file.
     *
     * @param uri absolute file path
     * @return list of symbols, or an empty list if the file is not indexed
     */
    public List<SymbolEntry> getFileSymbols(String uri) {
        List<SymbolEntry> symbols = fileSymbols.get(uri);
        return symbols != null ? symbols : Collections.emptyList();
    }

    /**
     * Returns all URIs currently tracked in the index.
     */
    public List<String> getAllUris() {
        return new ArrayList<>(documents.keySet());
    }

    /**
     * Returns the absolute path of the currently indexed project root.
     */
    public String getProjectRoot() {
        return projectRoot;
    }

    private static boolean isIgnoredDirectory(String name) {
        return name.startsWith(".") || name.equals("node_modules") || name.equals("build")
                || name.equals("dist") || name.equals("out") || name.equals("vendor")
                || name.equals(".next") || name.equals(".nuxt") || name.equals("target");
    }

    private void indexDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!isIgnoredDirectory(f.getName())) {
                    indexDirectory(f);
                }
            } else if (isSupportedFile(f)) {
                indexFile(f);
            }
        }
    }

    /**
     * Walks {@code dir} recursively and indexes any supported file that is <em>not</em>
     * already present in {@link #documents}. Used by {@link #indexProjectIncremental(File)}
     * to fill gaps (files the user hasn't opened yet) without evicting live editor snapshots.
     */
    private void indexDirectoryIncremental(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!isIgnoredDirectory(f.getName())) {
                    indexDirectoryIncremental(f);
                }
            } else if (isSupportedFile(f)) {
                // Only read from disk if this file hasn't been opened by the editor.
                // The editor's live snapshot (version > 0) is the authoritative source.
                if (!documents.containsKey(f.getAbsolutePath())) {
                    indexFile(f);
                }
            }
        }
    }

    public void indexFile(File file) {
        try {
            String uri = file.getAbsolutePath();
            // Never overwrite an in-memory snapshot that was pushed by the editor.
            // Editor-pushed documents have version > 0; disk-read ones use version 0.
            LspDocument existing = documents.get(uri);
            if (existing != null && existing.version > 0) {
                // Re-derive symbols from the live copy without touching the text.
                List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(existing);
                fileSymbols.put(uri, symbols);
                return;
            }
            String text = readFile(file);
            String languageId = getLanguageId(file.getName());
            LspDocument doc = new LspDocument(uri, text, languageId, 0);
            documents.put(uri, doc);
            List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(doc);
            fileSymbols.put(uri, symbols);

            if ("javascript".equals(languageId) || "typescript".equals(languageId)) {
                try {
                    com.cocode.vcode.ide.core.diagnostic.util.TokenStream tokens = com.cocode.vcode.ide.core.language.js.JsLexer.tokenize(text);
                    com.cocode.vcode.ide.core.language.js.JsSyntaxTree tree = com.cocode.vcode.ide.core.language.js.JsParser.parseTopLevel(text, tokens);
                    com.cocode.vcode.ide.core.language.js.ParseResult pr = new com.cocode.vcode.ide.core.language.js.ParseResult(file, text, tokens, tree, null, com.cocode.vcode.ide.core.language.js.ParseResult.MODE_FULL);
                    updateParseResult(uri, pr);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            // Skip files that cannot be read
        }
    }
}

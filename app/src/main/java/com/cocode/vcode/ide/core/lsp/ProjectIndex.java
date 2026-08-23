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
        LspEditorBridge.resetProjectSession();
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

    private void indexDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                // Skip hidden dirs and common build artefact directories
                String name = f.getName();
                if (!name.startsWith(".") && !name.equals("node_modules") && !name.equals("build")) {
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
                String name = f.getName();
                if (!name.startsWith(".") && !name.equals("node_modules") && !name.equals("build")) {
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
        } catch (Exception ignored) {
            // Skip files that cannot be read
        }
    }
}

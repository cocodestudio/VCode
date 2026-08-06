package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        // Limit indexing to files <= 1 MB to avoid OOM on large minified assets
        if (file.length() > 1024 * 1024) return "";
        FileInputStream fis = new FileInputStream(file);
        InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8);
        char[] buffer = new char[8192];
        StringBuilder sb = new StringBuilder((int) file.length());
        int n;
        while ((n = reader.read(buffer)) != -1) sb.append(buffer, 0, n);
        reader.close();
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /**
     * Recursively indexes all supported source files under {@code root} on the IO thread pool.
     * Calls {@code onComplete} on the main thread when finished.
     *
     * @param root       project root directory
     * @param onComplete optional callback, invoked on the main thread when indexing is done
     */
    public void indexProject(File root, Runnable onComplete) {
        projectRoot = root.getAbsolutePath();
        documents.clear();
        fileSymbols.clear();

        ExecutorProvider.getInstance().runOnIo(() -> {
            indexDirectory(root);
            if (onComplete != null) {
                ExecutorProvider.getInstance().runOnMain(onComplete);
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

    private void indexFile(File file) {
        try {
            String text = readFile(file);
            String languageId = getLanguageId(file.getName());
            String uri = file.getAbsolutePath();
            int version = 0;
            LspDocument doc = new LspDocument(uri, text, languageId, version);
            documents.put(uri, doc);
            List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(doc);
            fileSymbols.put(uri, symbols);
        } catch (Exception ignored) {
            // Skip files that cannot be read
        }
    }
}

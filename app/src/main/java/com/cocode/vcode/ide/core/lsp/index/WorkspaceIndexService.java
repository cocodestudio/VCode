package com.cocode.vcode.ide.core.lsp.index;

import com.cocode.vcode.ide.core.autocomplete.SymbolScope;
import com.cocode.vcode.ide.core.autocomplete.VFSManager;
import com.cocode.vcode.ide.core.autocomplete.types.TypeDefinitionIndexer;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.editor.search.SearchEngine;
import com.cocode.vcode.ide.core.events.IdeEventBus;
import com.cocode.vcode.ide.core.events.ProjectEvent;
import com.cocode.vcode.ide.core.language.css.CssLexer;
import com.cocode.vcode.ide.core.language.css.CssParser;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;
import com.cocode.vcode.ide.core.language.html.HtmlLexer;
import com.cocode.vcode.ide.core.language.html.HtmlParser;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlTokenStream;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsExportTable;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.language.js.ScopeTree;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspEditorBridge;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.SymbolEntry;
import com.cocode.vcode.ide.core.lsp.SymbolExtractor;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.SearchResult;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified Workspace Index Service.
 * <p>
 * Consolidates {@code ProjectIndex} and {@code ProjectSymbolIndex} into a single, authoritative
 * source of truth for all project documents, ASTs, symbols, cross-file references, and definitions.
 * Backed by {@link SegmentedSymbolStore} for 8KB immutable flat binary pages on disk.
 */
public class WorkspaceIndexService {

    private static volatile WorkspaceIndexService sInstance;

    private static final int MAX_CACHED_AST_COUNT = 8;

    // Full text of every indexed file, keyed by canonical absolute file path.
    private final ConcurrentHashMap<String, LspDocument> documents = new ConcurrentHashMap<>();

    // Symbols declared in each file, keyed by canonical absolute file path.
    private final ConcurrentHashMap<String, List<SymbolEntry>> fileSymbols = new ConcurrentHashMap<>();

    // File metadata and symbols cached in memory.
    private final ConcurrentHashMap<String, FileSymbolRecord> persistentRecords = new ConcurrentHashMap<>();

    // Bounded LRU cache of latest AST ParseResults (max 8 entries).
    private final Map<String, ParseResult> parseResults =
            Collections.synchronizedMap(new LinkedHashMap<String, ParseResult>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ParseResult> eldest) {
                    return size() > MAX_CACHED_AST_COUNT;
                }
            });

    // Per-file JS export tables.
    private final ConcurrentHashMap<String, JsExportTable> exportTables = new ConcurrentHashMap<>();

    // Cross-file autocomplete member caches
    private final Map<String, List<CompletionItem>> classMembers = new HashMap<>();
    private final Map<String, Map<String, List<CompletionItem>>> fileClassMembers = new HashMap<>();
    private final List<CompletionItem> cssClassItems = new ArrayList<>();
    private final List<CompletionItem> cssIdItems = new ArrayList<>();
    private final List<CompletionItem> htmlIdItems = new ArrayList<>();
    private final Map<String, Set<String>> fileCssClasses = new HashMap<>();
    private final Map<String, Set<String>> fileCssIds = new HashMap<>();
    private final Map<String, Set<String>> fileHtmlIds = new HashMap<>();
    private final Map<String, Map<String, CompletionItem>> fileJsSymbols = new HashMap<>();
    private final Map<String, List<CompletionItem>> jsFileExports = new HashMap<>();
    private final Map<String, List<CompletionItem>> dependencyExports = new HashMap<>();
    private final Map<String, List<CompletionItem>> dependencyMembers = new HashMap<>();
    private final Map<SymbolScope, Map<String, List<CompletionItem>>> scopedSymbols = new ConcurrentHashMap<>();

    private final List<IndexingProgressListener> progressListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isBuilding = new AtomicBoolean(false);
    private final AtomicBoolean isIncrementalScanRunning = new AtomicBoolean(false);

    private volatile String projectRoot = null;
    private volatile SegmentedSymbolStore segmentedStore = null;

    private WorkspaceIndexService() {
        for (SymbolScope scope : SymbolScope.values()) {
            scopedSymbols.put(scope, new ConcurrentHashMap<>());
        }
        IdeEventBus.getInstance().subscribe(
                ProjectEvent.DependencyChangedEvent.class,
                event -> invalidateScope(SymbolScope.LIBRARIES)
        );
    }

    public static WorkspaceIndexService getInstance() {
        if (sInstance == null) {
            synchronized (WorkspaceIndexService.class) {
                if (sInstance == null) {
                    sInstance = new WorkspaceIndexService();
                }
            }
        }
        return sInstance;
    }

    public static File getProjectRoot(File file) {
        return ProjectRepository.findProjectRoot(file);
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public SegmentedSymbolStore getSegmentedStore() {
        return segmentedStore;
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

    // -------------------------------------------------------------------------
    // Path Canonicalization & File Helpers
    // -------------------------------------------------------------------------

    public static String canonicalize(String uri) {
        if (uri == null) return null;
        String clean = uri;
        if (clean.startsWith("file://")) {
            clean = clean.substring(7);
        }
        try {
            return new File(clean).getAbsolutePath();
        } catch (Exception e) {
            return clean;
        }
    }

    private static boolean isSupportedFile(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".html") || name.endsWith(".htm")
                || name.endsWith(".css") || name.endsWith(".scss")
                || name.endsWith(".js") || name.endsWith(".ts")
                || name.endsWith(".jsx") || name.endsWith(".tsx") || name.endsWith(".mjs")
                || name.endsWith(".json") || name.endsWith(".md")
                || name.endsWith(".svg");
    }

    private static String getLanguageId(String fileName) {
        return FileType.fromFileName(fileName).getLspLanguageId();
    }

    private static boolean isIgnoredDirectory(String name) {
        return name.startsWith(".") || "node_modules".equals(name) || "build".equals(name)
                || "dist".equals(name) || "out".equals(name) || "vendor".equals(name)
                || ".next".equals(name) || ".nuxt".equals(name) || ".vcode".equals(name) || "target".equals(name);
    }

    private String readFile(File file) {
        if (file == null) return null;
        String queryPath = file.getAbsolutePath();
        try {
            queryPath = file.getCanonicalPath();
        } catch (Exception ignored) {
        }

        LspDocument doc = getDocument(queryPath);
        if (doc == null && !queryPath.equals(file.getAbsolutePath())) {
            doc = getDocument(file.getAbsolutePath());
        }
        if (doc != null && doc.text != null) {
            return doc.text;
        }

        try {
            if (file.length() > 1024 * 1024) return ""; // 1MB limit
            return FileUtils.readFile(file);
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Project Lifecycle & Full / Incremental Indexing
    // -------------------------------------------------------------------------

    public void initialize(File rootDir) {
        buildIndex(rootDir, null);
    }

    public void indexProject(File root, Runnable onComplete) {
        buildIndex(root, onComplete);
    }

    public void buildIndex(File rootDir) {
        buildIndex(rootDir, null);
    }

    public void buildIndex(File rootDir, Runnable onComplete) {
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

                if (segmentedStore.isIndexUpToDate(rootDir, candidateFiles)) {
                    loadCachedSymbolsIntoMemory();
                    if (onComplete != null) {
                        ExecutorProvider.getInstance().runOnMain(onComplete);
                    }
                    return;
                }

                segmentedStore.addProgressListener(forwarder);

                Set<String> classNames = new HashSet<>();
                Set<String> cssIds = new HashSet<>();
                Set<String> htmlIds = new HashSet<>();

                segmentedStore.indexFiles(rootDir, candidateFiles, (file, relPath, fileId) -> {
                    List<IndexSymbolEntry> entries = new ArrayList<>();
                    String name = file.getName().toLowerCase(Locale.US);
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
                loadCachedSymbolsIntoMemory();

                if (onComplete != null) {
                    ExecutorProvider.getInstance().runOnMain(onComplete);
                }
            } catch (Throwable t) {
                for (IndexingProgressListener l : progressListeners) {
                    try { l.onIndexingError(t); } catch (Exception ignored) {}
                }
            } finally {
                isBuilding.set(false);
            }
        });
    }

    public void indexProjectIncremental(File root) {
        if (root == null) return;
        if (!isIncrementalScanRunning.compareAndSet(false, true)) return;
        projectRoot = root.getAbsolutePath();

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                if (segmentedStore == null) {
                    segmentedStore = new SegmentedSymbolStore(root);
                }
                List<File> candidateFiles = new ArrayList<>();
                collectCandidateFiles(root, candidateFiles);

                Set<String> classNames = new HashSet<>();
                Set<String> cssIds = new HashSet<>();
                Set<String> htmlIds = new HashSet<>();

                segmentedStore.indexFiles(root, candidateFiles, (file, relPath, fileId) -> {
                    List<IndexSymbolEntry> entries = new ArrayList<>();
                    String name = file.getName().toLowerCase(Locale.US);
                    if (name.endsWith(".css") || name.endsWith(".scss")) {
                        indexCssFile(file, classNames, cssIds, entries, fileId);
                    } else if (name.endsWith(".html") || name.endsWith(".htm")) {
                        indexHtmlFile(file, classNames, htmlIds, entries, fileId);
                    } else if (name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".jsx") || name.endsWith(".tsx") || name.endsWith(".mjs")) {
                        indexJsFile(file, relPath, entries, fileId);
                    }
                    return entries;
                }, true);

                loadCachedSymbolsIntoMemory();
            } finally {
                isIncrementalScanRunning.set(false);
            }
        });
    }

    private void collectCandidateFiles(File dir, List<File> candidateFiles) {
        if (dir == null || !dir.exists()) return;
        String dirName = dir.getName();
        if (isIgnoredDirectory(dirName)) return;

        List<File> files = VFSManager.getInstance().listCachedFiles(dir);
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
            } else if (isSupportedFile(f)) {
                candidateFiles.add(f);
            }
        }
    }

    public void clear() {
        projectRoot = null;
        documents.clear();
        fileSymbols.clear();
        parseResults.clear();
        exportTables.clear();
        persistentRecords.clear();
        clearInMemorySymbols();
        if (segmentedStore != null) {
            segmentedStore.close();
            segmentedStore = null;
        }
        LspEditorBridge.resetProjectSession();
    }

    public synchronized void clearInMemorySymbols() {
        for (Map<String, List<CompletionItem>> map : scopedSymbols.values()) {
            map.clear();
        }
        classMembers.clear();
        fileClassMembers.clear();
        fileCssClasses.clear();
        fileCssIds.clear();
        fileHtmlIds.clear();
        fileJsSymbols.clear();
        cssClassItems.clear();
        cssIdItems.clear();
        htmlIdItems.clear();
        jsFileExports.clear();
        dependencyExports.clear();
        dependencyMembers.clear();
    }

    // -------------------------------------------------------------------------
    // Document Management
    // -------------------------------------------------------------------------

    public void updateDocument(LspDocument doc) {
        if (doc == null || doc.uri == null) return;
        documents.put(doc.uri, doc);
        String canon = canonicalize(doc.uri);
        if (canon != null && !canon.equals(doc.uri)) {
            documents.put(canon, doc);
        }
        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(doc);
            updateFileSymbols(doc.uri, symbols);
        });
    }

    public void updateDocument(File file) {
        if (file == null) return;
        String text = readFile(file);
        if (text != null) {
            updateDocument(new LspDocument(file.getAbsolutePath(), text, getLanguageId(file.getName()), 0));
        }
    }

    public void updateDocumentSnapshot(String uri, String text, String languageId) {
        if (uri == null || text == null) return;
        LspDocument oldDoc = getDocument(uri);
        int version = (oldDoc != null) ? oldDoc.version + 1 : 1;
        LspDocument doc = new LspDocument(uri, text, languageId != null ? languageId : "javascript", version);
        documents.put(uri, doc);
        String canon = canonicalize(uri);
        if (canon != null && !canon.equals(uri)) {
            documents.put(canon, doc);
        }
        if (canon != null) {
            ParseResult cachedPr = parseResults.get(canon);
            if (cachedPr != null && !text.equals(cachedPr.source)) {
                parseResults.remove(canon);
                exportTables.remove(canon);
            }
        }
        if (!uri.equals(canon)) {
            ParseResult cachedPr = parseResults.get(uri);
            if (cachedPr != null && !text.equals(cachedPr.source)) {
                parseResults.remove(uri);
                exportTables.remove(uri);
            }
        }
    }

    public LspDocument getDocument(String uri) {
        if (uri == null) return null;
        LspDocument doc = documents.get(uri);
        if (doc == null) {
            doc = documents.get(canonicalize(uri));
        }
        String cleanUri = uri.startsWith("file://") ? uri.substring(7) : uri;
        if (doc == null && !cleanUri.equals(uri)) {
            doc = documents.get(cleanUri);
            if (doc == null) {
                doc = documents.get(canonicalize(cleanUri));
            }
        }
        if (doc == null) {
            File f = new File(cleanUri);
            if (f.exists() && f.isFile() && isSupportedFile(f)) {
                try {
                    String text = FileUtils.readFile(f);
                    String lang = getLanguageId(f.getName());
                    doc = new LspDocument(f.getAbsolutePath(), text, lang, 0);
                    documents.put(f.getAbsolutePath(), doc);
                    String canon = canonicalize(f.getAbsolutePath());
                    if (canon != null && !canon.equals(f.getAbsolutePath())) {
                        documents.put(canon, doc);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return doc;
    }

    public Collection<LspDocument> getDocuments() {
        return Collections.unmodifiableCollection(documents.values());
    }

    public boolean hasDocument(File file) {
        if (file == null) return false;
        if (documents.containsKey(file.getAbsolutePath())) return true;
        try {
            return documents.containsKey(file.getCanonicalPath());
        } catch (Exception e) {
            return false;
        }
    }

    public void removeDocument(String uri) {
        if (uri == null) return;
        documents.remove(uri);
        String canon = canonicalize(uri);
        if (canon != null) {
            documents.remove(canon);
        }
        invalidateFile(uri);
    }

    public void revertToDisk(File file) {
        if (file == null) return;
        String uri = file.getAbsolutePath();
        documents.remove(uri);
        String canon = canonicalize(uri);
        if (canon != null) {
            documents.remove(canon);
        }
        ExecutorProvider.getInstance().runOnIo(() -> indexFile(file));
    }

    public void reindexFile(File file) {
        if (file == null) return;
        String uri = file.getAbsolutePath();
        LspDocument existing = documents.get(uri);
        if (existing != null) {
            ExecutorProvider.getInstance().runOnCpu(() -> {
                List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(existing);
                updateFileSymbols(uri, symbols);
            });
        } else {
            ExecutorProvider.getInstance().runOnIo(() -> indexFile(file));
        }
    }

    public void reindexFile(File file, String content) {
        if (file == null || content == null) return;
        updateDocument(new LspDocument(file.getAbsolutePath(), content, getLanguageId(file.getName()), 1));
        updateFileSilently(file);
    }

    public void updateFileSymbols(String uri, List<SymbolEntry> symbols) {
        if (uri == null || symbols == null) return;
        fileSymbols.put(uri, symbols);
        String canon = canonicalize(uri);
        if (canon != null && !canon.equals(uri)) {
            fileSymbols.put(canon, symbols);
        }
        File f = new File(uri);
        if (f.exists()) {
            persistentRecords.put(uri, new FileSymbolRecord(f.lastModified(), f.length(), symbols));
        }
    }

    public void invalidateSymbols(String uri) {
        if (uri == null) return;
        fileSymbols.remove(uri);
        persistentRecords.remove(uri);
        String canon = canonicalize(uri);
        if (canon != null) {
            fileSymbols.remove(canon);
            persistentRecords.remove(canon);
        }
    }

    public synchronized void invalidateFile(String absolutePath) {
        if (absolutePath == null) return;
        try {
            String canonical = new File(absolutePath).getCanonicalPath();
            jsFileExports.remove(canonical);
            fileClassMembers.remove(canonical);
            fileCssClasses.remove(canonical);
            fileCssIds.remove(canonical);
            fileHtmlIds.remove(canonical);
            fileJsSymbols.remove(canonical);
        } catch (Exception ignored) {
        }
        jsFileExports.remove(absolutePath);
        fileClassMembers.remove(absolutePath);
        fileCssClasses.remove(absolutePath);
        fileCssIds.remove(absolutePath);
        fileHtmlIds.remove(absolutePath);
        fileJsSymbols.remove(absolutePath);
        rebuildInMemorySelectorLists();
        if (segmentedStore != null && projectRoot != null) {
            String rel = SegmentedSymbolStore.getRelativePath(new File(projectRoot), new File(absolutePath));
            segmentedStore.invalidateFile(rel);
        }
    }

    public void updateFileSilently(File file) {
        if (file == null) return;
        String name = file.getName().toLowerCase(Locale.US);
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
                    } else {
                        indexHtmlFile(file, classNames, ids);
                    }
                    rebuildInMemorySelectorLists();
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

    public void updateFileFromIndex(File file) {
        updateFileSilently(file);
    }

    // -------------------------------------------------------------------------
    // AST Cache & JS/TS Parsing
    // -------------------------------------------------------------------------

    public void updateParseResult(String uri, ParseResult result) {
        if (uri == null) return;
        String key = canonicalize(uri);
        if (result == null) {
            parseResults.remove(key);
            exportTables.remove(key);
        } else {
            parseResults.put(key, result);
            exportTables.put(key, JsExportTable.build(result.tree, key));
        }
    }

    public ParseResult getParseResult(String uri) {
        if (uri == null) return null;
        return parseResults.get(canonicalize(uri));
    }

    public ParseResult getOrParseJsFile(String uri) {
        if (uri == null) return null;
        String path = uri.startsWith("file://") ? uri.substring(7) : uri;
        return getOrParseJsFile(new File(path));
    }

    public ParseResult getOrParseJsFile(File file) {
        if (file == null) return null;
        String path = file.getPath();
        if (path.startsWith("file://")) {
            file = new File(path.substring(7));
        }
        if (file.exists() && file.length() > 500 * 1024) return null;
        String uri = file.getAbsolutePath();
        ParseResult cached = getParseResult(uri);
        LspDocument doc = getDocument(uri);
        String currentContent = (doc != null && doc.text != null) ? doc.text : null;

        if (cached != null && cached.tree != null) {
            if (currentContent != null) {
                if (currentContent.equals(cached.source)) {
                    return cached;
                }
            } else if (cached.file != null && file.lastModified() <= cached.file.lastModified()) {
                return cached;
            }
        }

        try {
            String content = (currentContent != null) ? currentContent : FileUtils.readFile(file);
            if (content != null) {
                TokenStream tokens = JsLexer.tokenize(content);
                JsSyntaxTree tree = JsParser.parseFull(content, tokens);
                if (tree != null) {
                    tree.buildNodesByOffset();
                }
                ScopeTree scopeTree = ScopeTree.build(tree);
                ParseResult pr = new ParseResult(file, content, tokens, tree, scopeTree, ParseResult.MODE_FULL);
                updateParseResult(uri, pr);
                List<SymbolEntry> syms = SymbolExtractor.extractJsSymbolsFromTree(uri, content, tree);
                updateFileSymbols(uri, syms);
                return pr;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public JsExportTable getExportTable(String uri) {
        if (uri == null) return null;
        return exportTables.get(canonicalize(uri));
    }

    public List<ExportRef> getExportsByPrefix(String prefix) {
        if (prefix == null) prefix = "";
        List<ExportRef> out = new ArrayList<>();
        for (JsExportTable table : exportTables.values()) {
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

    // -------------------------------------------------------------------------
    // Query APIs: Definitions, References, Symbols & Usages
    // -------------------------------------------------------------------------

    public List<SymbolEntry> searchSymbols(String query, int maxResults) {
        if (query == null || query.isEmpty()) return Collections.emptyList();
        String lower = query.toLowerCase(Locale.US);
        List<SymbolEntry> results = new ArrayList<>();
        int limit = maxResults > 0 ? maxResults : 50;

        for (List<SymbolEntry> symbols : fileSymbols.values()) {
            for (SymbolEntry s : symbols) {
                if (s.name != null && s.name.toLowerCase(Locale.US).contains(lower)) {
                    results.add(s);
                    if (results.size() >= limit) return results;
                }
            }
        }
        return results;
    }

    public List<SymbolEntry> findSymbolsByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        String lower = prefix.toLowerCase(Locale.US);
        List<SymbolEntry> results = new ArrayList<>();
        for (List<SymbolEntry> symbols : fileSymbols.values()) {
            for (SymbolEntry s : symbols) {
                if (s.name != null && s.name.toLowerCase(Locale.US).startsWith(lower)) {
                    results.add(s);
                    if (results.size() >= 50) return results;
                }
            }
        }
        return results;
    }

    public List<SymbolEntry> getFileSymbols(String uri) {
        if (uri == null) return Collections.emptyList();
        List<SymbolEntry> symbols = fileSymbols.get(uri);
        if (symbols == null) {
            symbols = fileSymbols.get(canonicalize(uri));
        }
        return symbols != null ? Collections.unmodifiableList(symbols) : Collections.emptyList();
    }

    public LspLocation findDefinition(String name) {
        List<LspLocation> defs = findDefinitions(name);
        return defs.isEmpty() ? null : defs.get(0);
    }

    public List<LspLocation> findDefinitions(String name) {
        if (name == null || name.isEmpty()) return Collections.emptyList();
        List<LspLocation> locations = new ArrayList<>();
        for (List<SymbolEntry> symbols : fileSymbols.values()) {
            for (SymbolEntry s : symbols) {
                if (name.equals(s.name)) {
                    locations.add(new LspLocation(s.uri, s.range));
                }
            }
        }
        return locations;
    }

    public List<LspLocation> findReferences(String name) {
        return findDefinitions(name);
    }

    public List<LspLocation> findFileUsages(String filename) {
        List<LspLocation> locations = new ArrayList<>();
        if (filename == null || filename.isEmpty()) return locations;

        SearchEngine searchEngine = new SearchEngine();
        for (LspDocument doc : documents.values()) {
            if (doc.text != null && doc.text.contains(filename)) {
                List<SearchResult> results = searchEngine.find(filename, doc.text, false, false, false);
                for (SearchResult r : results) {
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

    public List<String> getAllUris() {
        Set<String> set = new HashSet<>();
        for (String uri : documents.keySet()) {
            String canon = canonicalize(uri);
            if (canon != null) set.add(canon);
        }
        for (String uri : fileSymbols.keySet()) {
            String canon = canonicalize(uri);
            if (canon != null) set.add(canon);
        }
        return new ArrayList<>(set);
    }

    // -------------------------------------------------------------------------
    // Autocomplete & Cross-file Member Resolution
    // -------------------------------------------------------------------------

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
        if (className == null) return Collections.emptyList();
        List<CompletionItem> members = classMembers.get(className);
        return members != null ? new ArrayList<>(members) : Collections.emptyList();
    }

    public synchronized Set<String> getAllClassNames() {
        return new HashSet<>(classMembers.keySet());
    }

    public synchronized void registerDependencySymbols(String packageName, List<CompletionItem> members, List<CompletionItem> exports) {
        if (packageName == null || packageName.isEmpty()) return;
        if (members != null && !members.isEmpty()) {
            classMembers.put(packageName, new ArrayList<>(members));
            dependencyMembers.put(packageName, new ArrayList<>(members));
            if ("lodash".equals(packageName)) {
                classMembers.put("_", new ArrayList<>(members));
            }
        }
        if (exports != null && !exports.isEmpty()) {
            dependencyExports.put(packageName, new ArrayList<>(exports));
        }
    }

    public synchronized void unregisterDependencySymbols(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        classMembers.remove(packageName);
        dependencyMembers.remove(packageName);
        dependencyExports.remove(packageName);
        if ("lodash".equals(packageName)) {
            classMembers.remove("_");
        }
    }

    public synchronized Map<String, List<CompletionItem>> getDependencyExports() {
        return new HashMap<>(dependencyExports);
    }

    public synchronized Map<String, List<CompletionItem>> getDependencyMembers() {
        return new HashMap<>(dependencyMembers);
    }

    public synchronized List<CompletionItem> getDependencyMembers(String packageName) {
        return getDependencyMembers(null, packageName);
    }

    public synchronized List<CompletionItem> getDependencyMembers(File currentFile, String packageName) {
        if (packageName == null) return Collections.emptyList();
        ensureDependencyLoaded(currentFile, packageName);
        List<CompletionItem> members = dependencyMembers.get(packageName);
        return members != null ? new ArrayList<>(members) : Collections.emptyList();
    }

    public synchronized List<CompletionItem> getExportsForPath(String importPath) {
        return getExportsForPath(null, importPath);
    }

    public synchronized List<CompletionItem> getExportsForPath(File currentFile, String importPath) {
        if (importPath == null) return Collections.emptyList();

        if (!importPath.startsWith(".") && !importPath.startsWith("/")) {
            ensureDependencyLoaded(currentFile, importPath);
            List<CompletionItem> depExports = dependencyExports.get(importPath);
            if (depExports != null && !depExports.isEmpty()) {
                return new ArrayList<>(depExports);
            }
            List<CompletionItem> depMembers = dependencyMembers.get(importPath);
            if (depMembers != null && !depMembers.isEmpty()) {
                return new ArrayList<>(depMembers);
            }
        }

        if (currentFile == null) return Collections.emptyList();

        File parent = currentFile.getParentFile();
        if (parent == null) return Collections.emptyList();

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

        boolean canRead = targetFile.exists() || hasDocument(targetFile);
        if ((exports == null || exports.isEmpty()) && canRead) {
            indexJsFile(targetFile);
            try {
                exports = jsFileExports.get(targetFile.getCanonicalPath());
            } catch (Exception e) {
                exports = jsFileExports.get(targetFile.getAbsolutePath());
            }
        }

        return exports != null ? new ArrayList<>(exports) : Collections.emptyList();
    }

    public synchronized void invalidateScope(SymbolScope scope) {
        if (scope == null) return;
        Map<String, List<CompletionItem>> map = scopedSymbols.get(scope);
        if (map != null) {
            map.clear();
        }
        if (scope == SymbolScope.LIBRARIES) {
            dependencyExports.clear();
            dependencyMembers.clear();
        } else if (scope == SymbolScope.PROJECT) {
            classMembers.clear();
            fileClassMembers.clear();
            fileCssClasses.clear();
            fileCssIds.clear();
            fileHtmlIds.clear();
            fileJsSymbols.clear();
            cssClassItems.clear();
            cssIdItems.clear();
            htmlIdItems.clear();
            jsFileExports.clear();
        }
    }

    public void putScopedSymbols(SymbolScope scope, String key, List<CompletionItem> items) {
        if (scope == null || key == null || items == null) return;
        Map<String, List<CompletionItem>> map = scopedSymbols.get(scope);
        if (map != null) {
            map.put(key, new ArrayList<>(items));
        }
    }

    public List<CompletionItem> getScopedSymbols(SymbolScope scope, String key) {
        if (scope == null || key == null) return Collections.emptyList();
        Map<String, List<CompletionItem>> map = scopedSymbols.get(scope);
        if (map != null) {
            List<CompletionItem> items = map.get(key);
            if (items != null) return Collections.unmodifiableList(items);
        }
        return Collections.emptyList();
    }

    public List<CompletionItem> getAllScopedSymbols(SymbolScope scope) {
        if (scope == null) return Collections.emptyList();
        Map<String, List<CompletionItem>> map = scopedSymbols.get(scope);
        if (map == null || map.isEmpty()) return Collections.emptyList();
        List<CompletionItem> result = new ArrayList<>();
        for (List<CompletionItem> list : map.values()) {
            if (list != null) result.addAll(list);
        }
        return result;
    }

    public synchronized Set<String> getCssClassesForFile(File file) {
        if (file == null) return Collections.emptySet();
        String pathKey = file.getAbsolutePath();
        try {
            pathKey = file.getCanonicalPath();
        } catch (Exception ignored) {
        }
        Set<String> set = fileCssClasses.get(pathKey);
        if (set == null) {
            boolean canRead = file.exists() || hasDocument(file);
            if (canRead) {
                Set<String> classes = new HashSet<>();
                Set<String> ids = new HashSet<>();
                indexCssFile(file, classes, ids);
                set = fileCssClasses.get(pathKey);
            } else {
                fileCssClasses.put(pathKey, Collections.emptySet());
            }
        }
        return set != null ? new HashSet<>(set) : Collections.emptySet();
    }

    public synchronized Set<String> getCssIdsForFile(File file) {
        if (file == null) return Collections.emptySet();
        String pathKey = file.getAbsolutePath();
        try {
            pathKey = file.getCanonicalPath();
        } catch (Exception ignored) {
        }
        Set<String> set = fileCssIds.get(pathKey);
        if (set == null) {
            boolean canRead = file.exists() || hasDocument(file);
            if (canRead) {
                Set<String> classes = new HashSet<>();
                Set<String> ids = new HashSet<>();
                indexCssFile(file, classes, ids);
                set = fileCssIds.get(pathKey);
            } else {
                fileCssIds.put(pathKey, Collections.emptySet());
            }
        }
        return set != null ? new HashSet<>(set) : Collections.emptySet();
    }

    public synchronized void collectCssSelectorsRecursively(File file, Set<String> classNames, Set<String> cssIds, Set<String> visited) {
        if (file == null) return;
        String pathKey = file.getAbsolutePath();
        try {
            pathKey = file.getCanonicalPath();
        } catch (Exception ignored) {
        }
        if (visited != null && !visited.add(pathKey)) return;

        classNames.addAll(getCssClassesForFile(file));
        cssIds.addAll(getCssIdsForFile(file));

        String content = readFile(file);
        if (content != null && content.contains("@import")) {
            List<File> importedFiles = extractCssImports(content, file);
            for (File imp : importedFiles) {
                collectCssSelectorsRecursively(imp, classNames, cssIds, visited);
            }
        }
    }

    public synchronized void collectJsSymbolsRecursively(File file, Map<String, CompletionItem> targetSymbols, Set<String> visited) {
        if (file == null) return;
        String pathKey = file.getAbsolutePath();
        try {
            pathKey = file.getCanonicalPath();
        } catch (Exception ignored) {
        }
        if (visited != null && !visited.add(pathKey)) return;

        Map<String, CompletionItem> fileSyms = getJsSymbolsForFile(pathKey);
        if (fileSyms != null) {
            targetSymbols.putAll(fileSyms);
        }

        String content = readFile(file);
        if (content != null) {
            List<File> importedFiles = extractJsImports(content, file);
            for (File imp : importedFiles) {
                collectJsSymbolsRecursively(imp, targetSymbols, visited);
            }
        }
    }

    public synchronized Map<String, CompletionItem> getJsSymbolsForFile(String uri) {
        if (uri == null) return Collections.emptyMap();
        Map<String, CompletionItem> map = fileJsSymbols.get(uri);
        if (map == null) {
            map = fileJsSymbols.get(canonicalize(uri));
        }
        return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
    }

    public synchronized List<CompletionItem> getJsFileExports(String uri) {
        if (uri == null) return Collections.emptyList();
        List<CompletionItem> list = jsFileExports.get(uri);
        if (list == null) {
            list = jsFileExports.get(canonicalize(uri));
        }
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Internal File Parsing & Indexing
    // -------------------------------------------------------------------------

    private void indexFile(File file) {
        try {
            String uri = file.getAbsolutePath();
            LspDocument existing = documents.get(uri);
            if (existing != null && existing.version > 0) {
                List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(existing);
                fileSymbols.put(uri, symbols);
                persistentRecords.put(uri, new FileSymbolRecord(file.lastModified(), file.length(), symbols));
                return;
            }
            String text = readFile(file);
            if (text == null) return;
            String languageId = getLanguageId(file.getName());
            LspDocument doc = new LspDocument(uri, text, languageId, 0);
            documents.put(uri, doc);
            List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(doc);
            fileSymbols.put(uri, symbols);
            persistentRecords.put(uri, new FileSymbolRecord(file.lastModified(), file.length(), symbols));

            if ("javascript".equals(languageId) || "typescript".equals(languageId)) {
                try {
                    TokenStream tokens = JsLexer.tokenize(text);
                    JsSyntaxTree tree = JsParser.parseTopLevel(text, tokens);
                    ParseResult pr = new ParseResult(file, text, tokens, tree, null, ParseResult.MODE_FULL);
                    updateParseResult(uri, pr);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void indexCssFile(File file, Set<String> classNames, Set<String> cssIds) {
        indexCssFile(file, classNames, cssIds, null, 0);
    }

    private void indexCssFile(File file, Set<String> classNames, Set<String> cssIds, List<IndexSymbolEntry> entries, int fileId) {
        String content = readFile(file);
        if (content == null) return;

        Set<String> fileClasses = new HashSet<>();
        Set<String> fileIds = new HashSet<>();
        extractSelectorsFromCssText(content, fileClasses, fileIds);

        classNames.addAll(fileClasses);
        cssIds.addAll(fileIds);

        synchronized (this) {
            String pathKey = file.getAbsolutePath();
            try {
                pathKey = file.getCanonicalPath();
            } catch (Exception ignored) {
            }
            fileCssClasses.put(pathKey, new HashSet<>(fileClasses));
            fileCssIds.put(pathKey, new HashSet<>(fileIds));
        }

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
        Set<String> extractedHtmlIds = new HashSet<>();
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
                        extractedHtmlIds.add(id);
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

        Set<String> styleClasses = new HashSet<>();
        Set<String> styleIds = new HashSet<>();
        if (result != null && result.embeddedResults != null) {
            for (ParseResult.EmbeddedResult emb : result.embeddedResults) {
                if (emb.result != null && emb.result.cssTree != null) {
                    CssSyntaxTree cTree = emb.result.cssTree;
                    for (int j = 1; j < cTree.nodeCount; j++) {
                        if (cTree.nodeType[j] == CssSyntaxTree.N_SELECTOR) {
                            int parent = cTree.nodeParent[j];
                            if (parent > 0 && cTree.nodeType[parent] == CssSyntaxTree.N_AT_RULE) {
                                continue;
                            }
                            String sel = cTree.nodeName[j];
                            if (sel != null && !sel.isEmpty() && !sel.startsWith("@")) {
                                extractCssClassesAndIds(sel, styleClasses, styleIds);
                            }
                        }
                    }
                }
            }
        }
        fileClasses.addAll(styleClasses);
        extractedHtmlIds.addAll(styleIds);

        Map<String, CompletionItem> scriptSymbols = new LinkedHashMap<>();
        if (result != null && result.embeddedResults != null) {
            for (ParseResult.EmbeddedResult emb : result.embeddedResults) {
                if (emb.result != null && emb.result.tree != null) {
                    extractSymbolsFromJsTree(emb.result.tree, scriptSymbols, "<script>");
                }
            }
        }
        if (scriptSymbols.isEmpty() && content.contains("<script")) {
            extractEmbeddedScriptSymbolsFromText(content, scriptSymbols, "<script>");
        }

        synchronized (this) {
            String pathKey = file.getAbsolutePath();
            try {
                pathKey = file.getCanonicalPath();
            } catch (Exception ignored) {
            }
            fileCssClasses.put(pathKey, new HashSet<>(styleClasses));
            fileCssIds.put(pathKey, new HashSet<>(styleIds));
            this.fileHtmlIds.put(pathKey, new HashSet<>(extractedHtmlIds));
            fileJsSymbols.put(pathKey, scriptSymbols);
        }

        classNames.addAll(fileClasses);
        htmlIds.addAll(extractedHtmlIds);
        if (entries != null) {
            for (String c : fileClasses) {
                entries.add(new IndexSymbolEntry(SymbolKey.cssClass(c), c, IndexSymbolEntry.KIND_CSS_CLASS, IndexSymbolEntry.FLAG_NONE, fileId, 1, "CSS Class"));
            }
            for (String id : extractedHtmlIds) {
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

        Map<String, CompletionItem> fileSymbolsMap = new LinkedHashMap<>();
        extractSymbolsFromJsText(content, fileSymbolsMap, file.getName());

        TokenStream stream = JsLexer.tokenize(content);
        JsSyntaxTree tree = JsParser.parseTopLevel(content, stream);

        List<CompletionItem> exports = new ArrayList<>();
        Map<String, String> signatures = new HashMap<>();

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

        Set<String> seenExports = new HashSet<>();
        JsExportTable exportTable = JsExportTable.build(tree, file.getAbsolutePath());
        for (int e = 0; e < exportTable.count; e++) {
            String name = exportTable.exportName[e];
            if (name != null && !name.isEmpty() && isValidJsIdentifier(name) && seenExports.add(name)) {
                String sig = signatures.getOrDefault(name, "");
                exports.add(new CompletionItem(name + sig, name, "Export", CompletionItem.Type.VALUE, 0));
            }
        }

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

        scanModuleExports(content, exports, signatures, seenExports, file);

        synchronized (this) {
            String pathKey = file.getAbsolutePath();
            try {
                pathKey = file.getCanonicalPath();
            } catch (Exception ignored) {
            }
            fileJsSymbols.put(pathKey, fileSymbolsMap);
            try {
                jsFileExports.put(file.getCanonicalPath(), exports);
            } catch (Exception e) {
                jsFileExports.put(file.getAbsolutePath(), exports);
            }
        }

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

                if (nodeType == JsSyntaxTree.N_TYPE_ALIAS && tree.nodeTypeAnn[i] != null) {
                    Map<String, String> propMap = JsAutoCompleteEngine.extractObjectTypePropertyMap(tree.nodeTypeAnn[i]);
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

        if (projectRoot != null) {
            File rootDir = new File(projectRoot);
            boolean cached = TypeDefinitionIndexer.loadCachedDependencySymbols(rootDir);
            if (!cached) {
                TypeDefinitionIndexer.indexAllDependencies(rootDir);
            }
        }
    }

    private synchronized void rebuildInMemorySelectorLists() {
        cssClassItems.clear();
        Set<String> allClasses = new LinkedHashSet<>();
        for (Set<String> set : fileCssClasses.values()) {
            allClasses.addAll(set);
        }
        for (String c : allClasses) {
            cssClassItems.add(new CompletionItem(c, c, "CSS Class", CompletionItem.Type.CSS_VALUE, 0));
        }

        cssIdItems.clear();
        Set<String> allCssIds = new LinkedHashSet<>();
        for (Set<String> set : fileCssIds.values()) {
            allCssIds.addAll(set);
        }
        for (String id : allCssIds) {
            cssIdItems.add(new CompletionItem(id, id, "CSS ID", CompletionItem.Type.CSS_VALUE, 0));
        }

        htmlIdItems.clear();
        Set<String> allHtmlIds = new LinkedHashSet<>();
        for (Set<String> set : fileHtmlIds.values()) {
            allHtmlIds.addAll(set);
        }
        for (String id : allHtmlIds) {
            htmlIdItems.add(new CompletionItem(id, id, "HTML ID", CompletionItem.Type.VALUE, 0));
        }
    }

    private void ensureDependencyLoaded(File currentFile, String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        if (dependencyMembers.containsKey(packageName) || dependencyExports.containsKey(packageName)) {
            return;
        }
        File root = (currentFile != null) ? getProjectRoot(currentFile) : (projectRoot != null ? new File(projectRoot) : null);
        if (root != null) {
            TypeDefinitionIndexer.indexDependency(root, packageName);
        }
    }

    // -------------------------------------------------------------------------
    // Lexical & AST Extraction Helpers
    // -------------------------------------------------------------------------

    public static boolean isValidCssClassName(String name) {
        if (name == null || name.isEmpty()) return false;
        char first = name.charAt(0);
        if (Character.isDigit(first)) return false;
        if (first == '-') {
            if (name.length() == 1 || Character.isDigit(name.charAt(1))) return false;
        }
        if (name.endsWith("-")) return false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch != '_' && ch != '-' && !Character.isLetterOrDigit(ch) && ch < 128) return false;
        }
        return !isKnownUnitOrExtension(name);
    }

    public static boolean isValidCssId(String name) {
        if (name == null || name.isEmpty()) return false;
        char first = name.charAt(0);
        if (Character.isDigit(first)) return false;
        if (first == '-') {
            if (name.length() == 1 || Character.isDigit(name.charAt(1))) return false;
        }
        if (name.endsWith("-")) return false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch != '_' && ch != '-' && !Character.isLetterOrDigit(ch) && ch < 128) return false;
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
        return !isKnownUnitOrExtension(name);
    }

    public static boolean isValidHtmlId(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.startsWith("#")) {
            name = name.substring(1).trim();
            if (name.isEmpty()) return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isWhitespace(name.charAt(i))) return false;
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
            case "break": case "case": case "catch": case "class": case "const": case "continue":
            case "debugger": case "default": case "delete": case "do": case "else": case "export":
            case "extends": case "finally": case "for": case "function": case "if": case "import":
            case "in": case "instanceof": case "new": case "return": case "super": case "switch":
            case "this": case "throw": case "try": case "typeof": case "var": case "void":
            case "while": case "with": case "yield": case "let": case "static": case "enum":
            case "await": case "null": case "true": case "false": case "undefined": case "NaN":
            case "Infinity":
                return true;
            default:
                return false;
        }
    }

    public static boolean isKnownUnitOrExtension(String name) {
        if (name == null || name.length() > 5) return false;
        String lower = name.toLowerCase(Locale.US);
        switch (lower) {
            case "px": case "cm": case "mm": case "q": case "in": case "pt": case "pc":
            case "em": case "rem": case "ex": case "ch": case "cap": case "ic": case "lh":
            case "rlh": case "vh": case "vw": case "vi": case "vb": case "vmin": case "vmax":
            case "dvh": case "dvw": case "dvi": case "dvb": case "lvh": case "lvw": case "lvi":
            case "lvb": case "svh": case "svw": case "svi": case "svb": case "cqw": case "cqh":
            case "cqi": case "cqb": case "cqmin": case "cqmax": case "deg": case "grad":
            case "rad": case "turn": case "s": case "ms": case "hz": case "khz": case "dpi":
            case "dpcm": case "dppx": case "fr":
            case "png": case "jpg": case "jpeg": case "gif": case "svg": case "webp":
            case "css": case "scss": case "less": case "html": case "htm": case "js":
            case "jsx": case "ts": case "tsx": case "mjs": case "cjs": case "json":
                return true;
            default:
                return false;
        }
    }

    private static boolean isIgnoredProp(String name) {
        return "constructor".equals(name) || "prototype".equals(name) || "function".equals(name);
    }

    public static String stripQuotes(String str) {
        if (str == null || str.length() < 2) return str != null ? str : "";
        char first = str.charAt(0);
        char last = str.charAt(str.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return str.substring(1, str.length() - 1);
        }
        return str;
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

    public static void extractSelectorsFromCssText(String cssContent, Set<String> classNames, Set<String> cssIds) {
        if (cssContent == null || cssContent.isEmpty()) return;
        try {
            CssTokenStream stream = CssLexer.tokenize(cssContent);
            CssSyntaxTree tree = CssParser.parse(stream, cssContent);
            for (int i = 1; i < tree.nodeCount; i++) {
                if (tree.nodeType[i] == CssSyntaxTree.N_SELECTOR) {
                    int parent = tree.nodeParent[i];
                    if (parent > 0 && tree.nodeType[parent] == CssSyntaxTree.N_AT_RULE) {
                        continue;
                    }
                    String sel = tree.nodeName[i];
                    if (sel != null && !sel.isEmpty() && !sel.startsWith("@")) {
                        extractCssClassesAndIds(sel, classNames, cssIds);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static List<File> extractCssImports(String cssContent, File baseFile) {
        if (cssContent == null || cssContent.isEmpty() || !cssContent.contains("@import")) {
            return Collections.emptyList();
        }
        List<File> result = new ArrayList<>();
        File parentDir = baseFile != null ? baseFile.getParentFile() : null;
        int len = cssContent.length();
        int p = 0;
        while (p < len) {
            int importIdx = indexOfIgnoreCaseStatic(cssContent, "@import", p);
            if (importIdx == -1) break;
            int after = importIdx + 7;
            while (after < len && Character.isWhitespace(cssContent.charAt(after))) after++;
            if (after >= len) break;

            int endIdx = cssContent.indexOf(';', after);
            if (endIdx == -1) endIdx = len;

            String statement = cssContent.substring(after, endIdx).trim();
            String importTarget = null;
            if (statement.startsWith("url(") && statement.endsWith(")")) {
                importTarget = stripQuotes(statement.substring(4, statement.length() - 1).trim());
            } else if ((statement.startsWith("\"") && statement.endsWith("\""))
                    || (statement.startsWith("'") && statement.endsWith("'"))) {
                importTarget = stripQuotes(statement);
            }

            if (importTarget != null && !importTarget.isEmpty() && !importTarget.startsWith("http://")
                    && !importTarget.startsWith("https://") && !importTarget.startsWith("//")) {
                File target = new File(parentDir, importTarget);
                if (target.exists()) {
                    result.add(target);
                }
            }
            p = endIdx + 1;
        }
        return result;
    }

    public static List<File> extractJsImports(String jsContent, File baseFile) {
        if (jsContent == null || jsContent.isEmpty() || (!jsContent.contains("import") && !jsContent.contains("require"))) {
            return Collections.emptyList();
        }
        List<File> result = new ArrayList<>();
        File parentDir = baseFile != null ? baseFile.getParentFile() : null;
        if (parentDir == null) return result;

        Pattern p = Pattern.compile("(?:import\\s+(?:.*?from\\s+)?|require\\s*\\()\\s*['\"]([^'\"]+)['\"]");
        Matcher m = p.matcher(jsContent);
        while (m.find()) {
            String path = m.group(1);
            if (path == null || path.trim().isEmpty()) continue;
            path = path.trim();
            if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("//")
                    || path.startsWith("data:") || path.startsWith("#")) {
                continue;
            }

            File resolved = null;
            if (path.startsWith("/")) {
                File root = getProjectRoot(baseFile);
                resolved = resolveJsCandidate(root != null ? root : parentDir, path.substring(1));
            } else if (path.startsWith("./") || path.startsWith("../")) {
                resolved = resolveJsCandidate(parentDir, path);
            }
            if (resolved != null) {
                try {
                    resolved = resolved.getCanonicalFile();
                } catch (Exception e) {
                    resolved = resolved.getAbsoluteFile();
                }
                if (resolved.exists()) {
                    result.add(resolved);
                }
            }
        }
        return result;
    }

    private static File resolveJsCandidate(File baseDir, String relPath) {
        File f = new File(baseDir, relPath);
        if (f.exists()) return f;
        File fJs = new File(baseDir, relPath + ".js");
        if (fJs.exists()) return fJs;
        File fTs = new File(baseDir, relPath + ".ts");
        if (fTs.exists()) return fTs;
        return f;
    }

    public static void extractSymbolsFromJsTree(JsSyntaxTree tree, Map<String, CompletionItem> result, String origin) {
        if (tree == null) return;
        Map<String, String> signatures = new HashMap<>();
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

        for (int i = 1; i < tree.nodeCount; i++) {
            int parent = tree.nodeParent[i];
            if (parent != 0 && tree.nodeType[parent] != JsSyntaxTree.N_EXPORT) {
                continue;
            }
            int type = tree.nodeType[i];
            String name = tree.nodeName[i];
            if (name == null || name.isEmpty() || !isValidJsIdentifier(name)) {
                continue;
            }

            if (type == JsSyntaxTree.N_FUNC_DECL) {
                addOrMergeJsSymbol(result, name, name, name, origin, CompletionItem.Type.FUNCTION);
            } else if (type == JsSyntaxTree.N_VAR_DECL) {
                if (signatures.containsKey(name)) {
                    addOrMergeJsSymbol(result, name, name, name, origin, CompletionItem.Type.FUNCTION);
                } else {
                    addOrMergeJsSymbol(result, name, name, name, origin, CompletionItem.Type.VALUE);
                }
            } else if (type == JsSyntaxTree.N_CLASS_DECL) {
                addOrMergeJsSymbol(result, name, name, name, origin, CompletionItem.Type.KEYWORD);
            }
        }

        try {
            JsExportTable exportTable = JsExportTable.build(tree, origin);
            for (int e = 0; e < exportTable.count; e++) {
                String expName = exportTable.exportName[e];
                if (expName != null && isValidJsIdentifier(expName)) {
                    if (signatures.containsKey(expName)) {
                        addOrMergeJsSymbol(result, expName, expName, expName, origin, CompletionItem.Type.FUNCTION);
                    } else {
                        addOrMergeJsSymbol(result, expName, expName, expName, origin, CompletionItem.Type.VALUE);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void extractSymbolsFromJsText(String jsContent, Map<String, CompletionItem> result, String origin) {
        if (jsContent == null || jsContent.trim().isEmpty()) return;
        try {
            TokenStream stream = JsLexer.tokenize(jsContent);
            JsSyntaxTree tree = JsParser.parseTopLevel(jsContent, stream);
            if (tree != null) {
                extractSymbolsFromJsTree(tree, result, origin);
            }
        } catch (Exception ignored) {
        }
    }

    public static void extractEmbeddedScriptSymbolsFromText(String html, Map<String, CompletionItem> targetSymbols, String origin) {
        if (html == null || !html.contains("<script")) return;
        int len = html.length();
        int p = 0;
        while (p < len) {
            if (html.startsWith("<!--", p)) {
                int end = html.indexOf("-->", p + 4);
                p = (end == -1) ? len : end + 3;
                continue;
            }
            if (p + 7 < len && html.charAt(p) == '<' &&
                    (html.charAt(p + 1) == 's' || html.charAt(p + 1) == 'S') &&
                    (html.charAt(p + 2) == 'c' || html.charAt(p + 2) == 'C') &&
                    (html.charAt(p + 3) == 'r' || html.charAt(p + 3) == 'R') &&
                    (html.charAt(p + 4) == 'i' || html.charAt(p + 4) == 'I') &&
                    (html.charAt(p + 5) == 'p' || html.charAt(p + 5) == 'P') &&
                    (html.charAt(p + 6) == 't' || html.charAt(p + 6) == 'T') &&
                    (Character.isWhitespace(html.charAt(p + 7)) || html.charAt(p + 7) == '/' || html.charAt(p + 7) == '>')) {

                int tagEnd = html.indexOf('>', p + 7);
                if (tagEnd == -1) break;
                String tag = html.substring(p, tagEnd);
                if (!tag.toLowerCase(Locale.US).contains("src=")) {
                    int contentStart = tagEnd + 1;
                    int closeIdx = indexOfIgnoreCaseStatic(html, "</script", contentStart);
                    if (closeIdx > contentStart) {
                        String js = html.substring(contentStart, closeIdx);
                        extractSymbolsFromJsText(js, targetSymbols, origin);
                    }
                }
                p = tagEnd + 1;
                continue;
            }
            p++;
        }
    }

    public static void addOrMergeJsSymbol(Map<String, CompletionItem> result, String key, String label, String insertText, String origin, CompletionItem.Type type) {
        if (key == null || key.isEmpty() || !isValidJsIdentifier(key)) return;
        CompletionItem existing = result.get(key);
        if (existing != null) {
            String existingOrigin = existing.getDetail();
            if (existingOrigin != null && origin != null && !existingOrigin.contains(origin)) {
                existing.setDetail(existingOrigin + ", " + origin);
            }
        } else {
            result.put(key, new CompletionItem(label, insertText, origin != null ? origin : "JavaScript", type, 0));
        }
    }

    public static int indexOfIgnoreCaseStatic(String haystack, String needle, int from) {
        if (haystack == null || needle == null) return -1;
        int hLen = haystack.length();
        int nLen = needle.length();
        if (from >= hLen || nLen == 0) return -1;
        for (int i = Math.max(0, from); i <= hLen - nLen; i++) {
            boolean match = true;
            for (int j = 0; j < nLen; j++) {
                if (Character.toLowerCase(haystack.charAt(i + j)) != Character.toLowerCase(needle.charAt(j))) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // Export Reference Record
    // -------------------------------------------------------------------------

    public static final class ExportRef {
        public final String name;
        public final String sourceUri;

        public ExportRef(String name, String sourceUri) {
            this.name = name;
            this.sourceUri = sourceUri;
        }
    }
}

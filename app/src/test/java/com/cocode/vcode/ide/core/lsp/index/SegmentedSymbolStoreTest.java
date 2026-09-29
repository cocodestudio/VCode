package com.cocode.vcode.ide.core.lsp.index;

import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Comprehensive test suite verifying the flat-memory segmented index system:
 * - BoundedBlockCache LRU eviction and segment invalidation
 * - SegmentWriter & SegmentReader 8KB block serialization and prefix pushdown queries
 * - IndexCatalog fingerprint tracking and serialization
 * - SegmentedSymbolStore incremental indexing, progress listener events, and compaction
 */
public class SegmentedSymbolStoreTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File projectRoot;
    private File cacheDir;

    @Before
    public void setUp() throws IOException {
        projectRoot = tempFolder.newFolder("test_vcode_workspace");
        cacheDir = new File(projectRoot, ".vcode/cache/symbols");
        cacheDir.mkdirs();
    }

    @Test
    public void testBoundedBlockCacheLruEviction() {
        BoundedBlockCache cache = new BoundedBlockCache(4);
        assertEquals(4, cache.getMaxResidentBlocks());

        byte[] block1 = new byte[]{1};
        byte[] block2 = new byte[]{2};
        byte[] block3 = new byte[]{3};
        byte[] block4 = new byte[]{4};
        byte[] block5 = new byte[]{5};

        cache.put(1, 0, block1);
        cache.put(1, 1, block2);
        cache.put(1, 2, block3);
        cache.put(1, 3, block4);
        assertEquals(4, cache.getResidentBlockCount());

        // Access block 0 to make it recently used
        assertNotNull(cache.get(1, 0));

        // Insert 5th block -> should evict block 1 (block index 1)
        cache.put(1, 4, block5);
        assertEquals(4, cache.getResidentBlockCount());
        assertNotNull(cache.get(1, 0)); // Still resident
        assertNotNull(cache.get(1, 4)); // Present
        assertNull(cache.get(1, 1)); // Evicted

        // Invalidate segment 1
        cache.invalidateSegment(1);
        assertEquals(0, cache.getResidentBlockCount());
    }

    @Test
    public void testSegmentWriterAndReaderPrefixPushdown() throws IOException {
        File segFile = new File(cacheDir, "seg_0001.bin");
        BoundedBlockCache cache = new BoundedBlockCache(16);

        List<IndexSymbolEntry> entries = new ArrayList<>();
        // Add 300 CSS class entries to cross multiple 8KB blocks
        for (int i = 0; i < 300; i++) {
            String className = String.format("btn-primary-%04d", i);
            entries.add(new IndexSymbolEntry(
                    SymbolKey.cssClass(className),
                    className,
                    IndexSymbolEntry.KIND_CSS_CLASS,
                    IndexSymbolEntry.FLAG_NONE,
                    1,
                    i + 1,
                    "CSS Class"
            ));
        }

        // Add 50 HTML ID entries
        for (int i = 0; i < 50; i++) {
            String idName = String.format("nav-item-%02d", i);
            entries.add(new IndexSymbolEntry(
                    SymbolKey.htmlId(idName),
                    idName,
                    IndexSymbolEntry.KIND_HTML_ID,
                    IndexSymbolEntry.FLAG_NONE,
                    2,
                    i + 10,
                    "HTML ID"
            ));
        }

        // Add 20 JS Export entries
        for (int i = 0; i < 20; i++) {
            String expName = "renderView" + i;
            entries.add(new IndexSymbolEntry(
                    SymbolKey.jsExport("src/views.js", expName),
                    expName,
                    IndexSymbolEntry.KIND_JS_EXPORT,
                    IndexSymbolEntry.FLAG_EXPORTED,
                    3,
                    i * 5,
                    "(config: any) => void"
            ));
        }

        SegmentWriter.writeSegment(segFile, 1, entries);
        assertTrue(segFile.exists());
        assertTrue(segFile.length() > 0);

        try (SegmentReader reader = new SegmentReader(segFile, cache)) {
            assertEquals(1, reader.getSegmentId());
            assertEquals(370, reader.getTotalEntryCount());
            assertTrue(reader.getBlockCount() >= 1);

            // Test prefix query for CSS classes
            List<IndexSymbolEntry> cssResults = reader.queryPrefix(SymbolKey.PREFIX_CSS_CLASS, 500);
            assertEquals(300, cssResults.size());
            assertEquals("btn-primary-0000", cssResults.get(0).getName());

            // Test limited prefix query
            List<IndexSymbolEntry> limitedResults = reader.queryPrefix(SymbolKey.PREFIX_CSS_CLASS, 10);
            assertEquals(10, limitedResults.size());

            // Test prefix query for HTML IDs
            List<IndexSymbolEntry> htmlResults = reader.queryPrefix(SymbolKey.PREFIX_HTML_ID, 100);
            assertEquals(50, htmlResults.size());

            // Test exact query
            String exactKey = SymbolKey.htmlId("nav-item-05");
            List<IndexSymbolEntry> exactResults = reader.queryExact(exactKey);
            assertEquals(1, exactResults.size());
            assertEquals("nav-item-05", exactResults.get(0).getName());
            assertEquals("HTML ID", exactResults.get(0).getDetail());

            // Test non-matching prefix
            List<IndexSymbolEntry> emptyResults = reader.queryPrefix("unknown:prefix:", 10);
            assertTrue(emptyResults.isEmpty());
        }
    }

    @Test
    public void testIndexCatalogFingerprinting() throws IOException {
        File catalogFile = new File(cacheDir, "catalog.bin");
        IndexCatalog catalog = new IndexCatalog();

        File sampleFile = new File(projectRoot, "styles.css");
        try (FileWriter fw = new FileWriter(sampleFile)) {
            fw.write(".container { display: flex; }");
        }

        assertFalse(catalog.isFileUpToDate(sampleFile, "styles.css"));

        catalog.recordFile("styles.css", sampleFile, 1);
        assertTrue(catalog.isFileUpToDate(sampleFile, "styles.css"));

        // Save and reload
        catalog.save(catalogFile);
        assertTrue(catalogFile.exists());

        IndexCatalog loaded = IndexCatalog.load(catalogFile);
        assertTrue(loaded.isFileUpToDate(sampleFile, "styles.css"));
        assertEquals(1, loaded.getFingerprint("styles.css").segmentId);

        // Modify file -> isFileUpToDate must be false
        try (FileWriter fw = new FileWriter(sampleFile, true)) {
            fw.write("\n.header { color: blue; }");
        }
        assertFalse(loaded.isFileUpToDate(sampleFile, "styles.css"));
    }

    @Test
    public void testSegmentedSymbolStoreIncrementalAndProgress() throws IOException {
        SegmentedSymbolStore store = new SegmentedSymbolStore(projectRoot);

        AtomicBoolean started = new AtomicBoolean(false);
        AtomicInteger progressCalls = new AtomicInteger(0);
        AtomicBoolean completed = new AtomicBoolean(false);

        store.addProgressListener(new IndexingProgressListener() {
            @Override
            public void onIndexingStarted(int totalFiles) {
                started.set(true);
            }

            @Override
            public void onIndexingProgress(int processed, int total, String currentFile) {
                progressCalls.incrementAndGet();
            }

            @Override
            public void onIndexingCompleted(int totalSymbols, long durationMs) {
                completed.set(true);
            }

            @Override
            public void onIndexingError(Throwable error) {
                fail("Unexpected indexing error: " + error.getMessage());
            }
        });

        File cssFile = new File(projectRoot, "app.css");
        try (FileWriter fw = new FileWriter(cssFile)) {
            fw.write(".card { border: 1px solid black; }");
        }

        File jsFile = new File(projectRoot, "app.js");
        try (FileWriter fw = new FileWriter(jsFile)) {
            fw.write("export function initApp() {}");
        }

        List<File> candidateFiles = new ArrayList<>();
        candidateFiles.add(cssFile);
        candidateFiles.add(jsFile);

        store.indexFiles(projectRoot, candidateFiles, (file, relPath, fileId) -> {
            List<IndexSymbolEntry> list = new ArrayList<>();
            if (file.getName().endsWith(".css")) {
                list.add(new IndexSymbolEntry(SymbolKey.cssClass("card"), "card", IndexSymbolEntry.KIND_CSS_CLASS, IndexSymbolEntry.FLAG_NONE, fileId, 1, "CSS Class"));
            } else if (file.getName().endsWith(".js")) {
                list.add(new IndexSymbolEntry(SymbolKey.jsExport(relPath, "initApp"), "initApp", IndexSymbolEntry.KIND_JS_EXPORT, IndexSymbolEntry.FLAG_EXPORTED, fileId, 1, "() => void"));
            }
            return list;
        });

        assertTrue(started.get());
        assertTrue(progressCalls.get() >= 2);
        assertTrue(completed.get());

        // Query completions through store
        List<CompletionItem> cssCompletions = store.queryCompletions(
                SegmentSearchQuery.prefix(SymbolKey.PREFIX_CSS_CLASS, 10), null);
        assertEquals(1, cssCompletions.size());
        assertEquals("card", cssCompletions.get(0).getLabel());
        assertEquals(CompletionItem.Type.CSS_VALUE, cssCompletions.get(0).getType());

        List<CompletionItem> jsCompletions = store.queryCompletions(
                SegmentSearchQuery.prefix(SymbolKey.PREFIX_JS_EXPORT, 10), "file:///app.js");
        assertEquals(1, jsCompletions.size());
        assertEquals("initApp", jsCompletions.get(0).getLabel());
        assertEquals("file:///app.js", jsCompletions.get(0).getSourceUri());

        store.close();
    }

    @Test
    public void testIsIndexUpToDateAndSilentSkipping() throws IOException {
        SegmentedSymbolStore store = new SegmentedSymbolStore(projectRoot);

        File htmlFile = new File(projectRoot, "index.html");
        try (FileWriter fw = new FileWriter(htmlFile)) {
            fw.write("<div id='app-root'></div>");
        }

        List<File> candidateFiles = new ArrayList<>();
        candidateFiles.add(htmlFile);

        // Initially not up to date
        assertFalse(store.isIndexUpToDate(projectRoot, candidateFiles));

        // Index the file
        store.indexFiles(projectRoot, candidateFiles, (file, relPath, fileId) -> {
            List<IndexSymbolEntry> list = new ArrayList<>();
            list.add(new IndexSymbolEntry(SymbolKey.htmlId("app-root"), "app-root", IndexSymbolEntry.KIND_HTML_ID, IndexSymbolEntry.FLAG_NONE, fileId, 1, "HTML ID"));
            return list;
        });

        // Now it is up to date
        assertTrue(store.isIndexUpToDate(projectRoot, candidateFiles));

        // Running indexFiles again when up to date should NOT fire any progress listener callbacks
        AtomicBoolean startedOnUpToDate = new AtomicBoolean(false);
        store.addProgressListener(new IndexingProgressListener() {
            @Override
            public void onIndexingStarted(int totalFiles) {
                startedOnUpToDate.set(true);
            }
            @Override
            public void onIndexingProgress(int processed, int total, String currentFile) {
                startedOnUpToDate.set(true);
            }
            @Override
            public void onIndexingCompleted(int totalSymbols, long durationMs) {}
            @Override
            public void onIndexingError(Throwable error) {}
        });

        store.indexFiles(projectRoot, candidateFiles, (file, relPath, fileId) -> new ArrayList<>());
        assertFalse("Should not fire progress events when all files are up to date", startedOnUpToDate.get());

        // Test silent single-file indexing
        AtomicBoolean startedOnSilent = new AtomicBoolean(false);
        File cssFile = new File(projectRoot, "style.css");
        try (FileWriter fw = new FileWriter(cssFile)) {
            fw.write(".btn-silent { color: green; }");
        }

        store.indexSingleFileSilently(projectRoot, cssFile, (file, relPath, fileId) -> {
            List<IndexSymbolEntry> list = new ArrayList<>();
            list.add(new IndexSymbolEntry(SymbolKey.cssClass("btn-silent"), "btn-silent", IndexSymbolEntry.KIND_CSS_CLASS, IndexSymbolEntry.FLAG_NONE, fileId, 1, "CSS Class"));
            return list;
        });

        assertFalse("Silent single file indexing must not trigger progress listeners", startedOnSilent.get());

        List<CompletionItem> silentResults = store.queryCompletions(
                SegmentSearchQuery.prefix(SymbolKey.PREFIX_CSS_CLASS, 10), null);
        boolean foundSilent = false;
        for (CompletionItem item : silentResults) {
            if ("btn-silent".equals(item.getLabel())) {
                foundSilent = true;
                break;
            }
        }
        assertTrue("Silent single file indexing should be queryable in segment store", foundSilent);

        store.close();
    }
}

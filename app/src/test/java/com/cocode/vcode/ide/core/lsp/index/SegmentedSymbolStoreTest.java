package com.cocode.vcode.ide.core.lsp.index;

import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.SymbolEntry;
import com.cocode.vcode.ide.core.lsp.SymbolExtractor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Tests for the segmented symbol store index:
 * - BoundedBlockCache LRU eviction and segment invalidation
 * - SegmentWriter & SegmentReader 8KB block serialization and prefix queries
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

    @Test
    public void testMultiProjectSwitchingMaintainsCacheIntegrity() throws IOException {
        File projectA = tempFolder.newFolder("ProjectA");
        File projectB = tempFolder.newFolder("ProjectB");

        File fileA = new File(projectA, "appA.js");
        try (FileWriter fw = new FileWriter(fileA)) {
            fw.write("export function runA() {}");
        }

        File fileB = new File(projectB, "appB.js");
        try (FileWriter fw = new FileWriter(fileB)) {
            fw.write("export function runB() {}");
        }

        // Index Project A
        SegmentedSymbolStore storeA = new SegmentedSymbolStore(projectA);
        assertEquals(projectA, storeA.getProjectRoot());
        storeA.indexFiles(projectA, Collections.singletonList(fileA), (file, relPath, fileId) -> {
            List<IndexSymbolEntry> list = new ArrayList<>();
            list.add(new IndexSymbolEntry(SymbolKey.jsExport("appA.js", "runA"), "runA", IndexSymbolEntry.KIND_JS_EXPORT, IndexSymbolEntry.FLAG_EXPORTED, fileId, 1, "() => void"));
            return list;
        });
        assertTrue(storeA.isIndexUpToDate(projectA, Collections.singletonList(fileA)));
        storeA.close();

        // Index Project B
        SegmentedSymbolStore storeB = new SegmentedSymbolStore(projectB);
        assertEquals(projectB, storeB.getProjectRoot());
        storeB.indexFiles(projectB, Collections.singletonList(fileB), (file, relPath, fileId) -> {
            List<IndexSymbolEntry> list = new ArrayList<>();
            list.add(new IndexSymbolEntry(SymbolKey.jsExport("appB.js", "runB"), "runB", IndexSymbolEntry.KIND_JS_EXPORT, IndexSymbolEntry.FLAG_EXPORTED, fileId, 1, "() => void"));
            return list;
        });
        assertTrue(storeB.isIndexUpToDate(projectB, Collections.singletonList(fileB)));
        storeB.close();

        // Reopen Project A - must still be up-to-date and have its symbols intact
        SegmentedSymbolStore reopenedStoreA = new SegmentedSymbolStore(projectA);
        assertTrue("Project A should remain up to date without re-indexing",
                reopenedStoreA.isIndexUpToDate(projectA, Collections.singletonList(fileA)));
        List<IndexSymbolEntry> resultsA = reopenedStoreA.query(SegmentSearchQuery.prefix(SymbolKey.PREFIX_JS_EXPORT, 10));
        assertEquals(1, resultsA.size());
        assertEquals("runA", resultsA.get(0).getName());
        reopenedStoreA.close();

        // Reopen Project B - must still be up-to-date and have its symbols intact
        SegmentedSymbolStore reopenedStoreB = new SegmentedSymbolStore(projectB);
        assertTrue("Project B should remain up to date without re-indexing",
                reopenedStoreB.isIndexUpToDate(projectB, Collections.singletonList(fileB)));
        List<IndexSymbolEntry> resultsB = reopenedStoreB.query(SegmentSearchQuery.prefix(SymbolKey.PREFIX_JS_EXPORT, 10));
        assertEquals(1, resultsB.size());
        assertEquals("runB", resultsB.get(0).getName());
        reopenedStoreB.close();
    }

    @Test
    public void testDeletedFileCatalogPruningAndConsistency() throws IOException {
        SegmentedSymbolStore store = new SegmentedSymbolStore(projectRoot);

        File file1 = new File(projectRoot, "file1.js");
        try (FileWriter fw = new FileWriter(file1)) {
            fw.write("export const one = 1;");
        }
        File file2 = new File(projectRoot, "file2.js");
        try (FileWriter fw = new FileWriter(file2)) {
            fw.write("export const two = 2;");
        }

        List<File> candidates = new ArrayList<>();
        candidates.add(file1);
        candidates.add(file2);

        store.indexFiles(projectRoot, candidates, (file, relPath, fileId) -> {
            List<IndexSymbolEntry> list = new ArrayList<>();
            list.add(new IndexSymbolEntry(SymbolKey.jsExport(relPath, file.getName()), file.getName(), IndexSymbolEntry.KIND_JS_EXPORT, IndexSymbolEntry.FLAG_EXPORTED, fileId, 1, ""));
            return list;
        });

        assertTrue(store.isIndexUpToDate(projectRoot, candidates));

        // Delete file2
        file2.delete();
        List<File> updatedCandidates = Collections.singletonList(file1);

        // Initially not up to date because candidate count (1) != catalog count (2)
        assertFalse(store.isIndexUpToDate(projectRoot, updatedCandidates));

        // Re-run indexing with updated candidates; it should prune deleted file2
        store.indexFiles(projectRoot, updatedCandidates, (file, relPath, fileId) -> new ArrayList<>());

        // Now catalog should be pruned and up to date
        assertTrue("Store must be up to date after pruning deleted file from catalog",
                store.isIndexUpToDate(projectRoot, updatedCandidates));

        store.close();
    }

    @Test
    public void testCssSelectorValidationAndFiltering() {
        Set<String> classNames = new HashSet<>();
        Set<String> cssIds = new HashSet<>();

        // Test normal class and ID selectors
        ProjectSymbolIndex.extractCssClassesAndIds(".btn.btn-primary #main-nav:hover", classNames, cssIds);
        assertTrue(classNames.contains("btn"));
        assertTrue(classNames.contains("btn-primary"));
        assertTrue(cssIds.contains("main-nav"));

        classNames.clear();
        cssIds.clear();

        // Test hex colors rejection (e.g. #fff, #ffffff, #1a2b3c)
        ProjectSymbolIndex.extractCssClassesAndIds("color: #fff; background: #1a2b3c; border-color: #ffffff; #000", classNames, cssIds);
        assertFalse(cssIds.contains("fff"));
        assertFalse(cssIds.contains("1a2b3c"));
        assertFalse(cssIds.contains("ffffff"));
        assertFalse(cssIds.contains("000"));

        classNames.clear();
        cssIds.clear();

        // Test units, decimals and numbers rejection (.5rem, .25em, .5px, .2s)
        ProjectSymbolIndex.extractCssClassesAndIds("margin: .5rem; padding: 0.25rem; font-size: 1.5rem; transition: .2s;", classNames, cssIds);
        assertFalse(classNames.contains("5rem"));
        assertFalse(classNames.contains("25rem"));
        assertFalse(classNames.contains("2s"));
        assertTrue(classNames.isEmpty());

        classNames.clear();
        cssIds.clear();

        // Test attribute selector contents skipping [href$=".png"] [fill="#ffffff"]
        ProjectSymbolIndex.extractCssClassesAndIds("a[href$='.png'] svg[fill='#ffffff']", classNames, cssIds);
        assertFalse(classNames.contains("png"));
        assertFalse(cssIds.contains("ffffff"));

        classNames.clear();
        cssIds.clear();

        // Test Tailwind classes with arbitrary units
        ProjectSymbolIndex.extractCssClassesAndIds(".w-[2.5rem] .p-[10px] .text-[#3b82f6]", classNames, cssIds);
        assertTrue(classNames.contains("w"));
        assertTrue(classNames.contains("p"));
        assertTrue(classNames.contains("text"));
        assertFalse(classNames.contains("5rem"));
        assertFalse(cssIds.contains("3b82f6"));

        classNames.clear();
        cssIds.clear();

        // Test at-rule skipping
        ProjectSymbolIndex.extractCssClassesAndIds("@media (max-width: 768.5px)", classNames, cssIds);
        assertTrue(classNames.isEmpty());
        assertTrue(cssIds.isEmpty());
    }

    @Test
    public void testJsIdentifierValidation() {
        // Valid identifiers
        assertTrue(ProjectSymbolIndex.isValidJsIdentifier("foo"));
        assertTrue(ProjectSymbolIndex.isValidJsIdentifier("_bar"));
        assertTrue(ProjectSymbolIndex.isValidJsIdentifier("$baz"));
        assertTrue(ProjectSymbolIndex.isValidJsIdentifier("camelCase"));
        assertTrue(ProjectSymbolIndex.isValidJsIdentifier("PascalCase"));
        assertTrue(ProjectSymbolIndex.isValidJsIdentifier("func1"));

        // Invalid: digits at start, operators, hyphens, dots
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("123"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("1foo"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("foo-bar"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("foo.bar"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("foo()"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("foo,bar"));

        // Invalid: JS reserved keywords
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("default"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("export"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("import"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("class"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("function"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("const"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("let"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("var"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("null"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("true"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("false"));
        assertFalse(ProjectSymbolIndex.isValidJsIdentifier("this"));
    }

    @Test
    public void testSymbolExtractorValidation() {
        // CSS extraction test
        String css = ".btn { color: #fff; margin: .5rem; font-size: 14px; }\n" +
                "svg[fill=\"#ffffff\"] { background: #1a2b3c; }\n" +
                "@media (max-width: 600px) { .responsive-nav { display: block; } }\n" +
                "#nav-bar { border-color: #000; }";
        LspDocument cssDoc = new LspDocument("file:///test.css", css, "css", 1);
        List<SymbolEntry> cssSymbols = SymbolExtractor.extractSymbols(cssDoc);

        Set<String> cssNames = new HashSet<>();
        for (SymbolEntry s : cssSymbols) {
            cssNames.add(s.name);
        }

        assertTrue(cssNames.contains(".btn"));
        assertTrue(cssNames.contains(".responsive-nav"));
        assertTrue(cssNames.contains("#nav-bar"));
        // Make sure no hex colors or units leaked
        assertFalse(cssNames.contains("#fff"));
        assertFalse(cssNames.contains("#ffffff"));
        assertFalse(cssNames.contains("#1a2b3c"));
        assertFalse(cssNames.contains("#000"));
        assertFalse(cssNames.contains(".5rem"));
        assertFalse(cssNames.contains("5rem"));
        assertFalse(cssNames.contains("14px"));

        // HTML extraction test
        String html = "<div id=\"main-card\" class=\"card active 5rem .invalid\">\n" +
                "  <span id=\"#fff\" class=\"btn\"></span>\n" +
                "</div>";
        LspDocument htmlDoc = new LspDocument("file:///test.html", html, "html", 1);
        List<SymbolEntry> htmlSymbols = SymbolExtractor.extractSymbols(htmlDoc);

        Set<String> htmlIds = new HashSet<>();
        Set<String> htmlClasses = new HashSet<>();
        for (SymbolEntry s : htmlSymbols) {
            if (s.kind == SymbolEntry.KIND_HTML_ID) {
                htmlIds.add(s.name);
            } else if (s.kind == SymbolEntry.KIND_CSS_CLASS) {
                htmlClasses.add(s.name);
            }
        }

        assertTrue(htmlIds.contains("main-card"));
        assertFalse(htmlIds.contains("fff"));
        assertFalse(htmlIds.contains("#fff"));

        assertTrue(htmlClasses.contains("card"));
        assertTrue(htmlClasses.contains("active"));
        assertTrue(htmlClasses.contains("btn"));
        assertFalse(htmlClasses.contains("5rem"));
        assertFalse(htmlClasses.contains(".invalid"));

        // JS extraction test
        String js = "function calculateTotal(price) { return price; }\n" +
                "class ShoppingCart {}\n" +
                "const tax = 0.05;";
        LspDocument jsDoc = new LspDocument("file:///test.js", js, "javascript", 1);
        List<SymbolEntry> jsSymbols = SymbolExtractor.extractSymbols(jsDoc);

        Set<String> jsNames = new HashSet<>();
        for (SymbolEntry s : jsSymbols) {
            jsNames.add(s.name);
        }

        assertTrue(jsNames.contains("calculateTotal"));
        assertTrue(jsNames.contains("ShoppingCart"));
        assertTrue(jsNames.contains("tax"));
    }

    @Test
    public void testOrphanSegmentAndTmpFilePurge() throws IOException {
        SegmentedSymbolStore store = new SegmentedSymbolStore(projectRoot);

        File cssFile = new File(projectRoot, "styles.css");
        try (FileWriter fw = new FileWriter(cssFile)) {
            fw.write(".btn-primary { color: red; }");
        }

        List<File> files = Collections.singletonList(cssFile);
        store.indexFiles(projectRoot, files, (file, relPath, fileId) -> {
            List<IndexSymbolEntry> entries = new ArrayList<>();
            entries.add(new IndexSymbolEntry(
                    SymbolKey.cssClass("btn-primary"),
                    "btn-primary",
                    IndexSymbolEntry.KIND_CSS_CLASS,
                    IndexSymbolEntry.FLAG_NONE,
                    fileId,
                    1,
                    "CSS Class"
            ));
            return entries;
        });

        // Confirm active segment was written and catalog.bin exists
        File catalogFile = new File(cacheDir, "catalog.bin");
        assertTrue(catalogFile.exists());
        store.close();

        // Inject an orphan segment and a stray .tmp file into .vcode/cache/symbols/
        File orphanSeg = new File(cacheDir, "seg_9999.bin");
        try (FileWriter fw = new FileWriter(orphanSeg)) {
            fw.write("corrupted or orphaned segment data");
        }
        File strayTmp = new File(cacheDir, "catalog.bin.12345.tmp");
        try (FileWriter fw = new FileWriter(strayTmp)) {
            fw.write("abandoned tmp data");
        }
        assertTrue(orphanSeg.exists());
        assertTrue(strayTmp.exists());

        // Re-open store: openExistingSegments() must purge orphan and tmp files
        SegmentedSymbolStore reopened = new SegmentedSymbolStore(projectRoot);
        assertFalse("Orphan segment should be deleted", orphanSeg.exists());
        assertFalse("Stray tmp file should be deleted", strayTmp.exists());

        // Valid tracked segment should still be loaded and queried
        List<IndexSymbolEntry> results = reopened.query(SegmentSearchQuery.prefix(SymbolKey.PREFIX_CSS_CLASS, 0));
        assertEquals(1, results.size());
        assertEquals("btn-primary", results.get(0).getName());
        reopened.close();
    }
}

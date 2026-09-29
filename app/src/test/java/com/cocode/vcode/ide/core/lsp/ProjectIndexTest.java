package com.cocode.vcode.ide.core.lsp;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.robolectric.shadows.ShadowLooper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ProjectIndexTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setup() {
        ProjectIndex.getInstance().clear();
    }

    @After
    public void tearDown() {
        ProjectIndex.getInstance().clear();
    }

    @Test
    public void testIndexProject() throws IOException, InterruptedException {
        File srcDir = tempFolder.newFolder("src");
        
        File jsFile = new File(srcDir, "main.js");
        try (FileWriter writer = new FileWriter(jsFile)) {
            writer.write("function myGlobalFunc() {}\nconst MY_CONST = 42;");
        }
        
        File cssFile = new File(srcDir, "style.css");
        try (FileWriter writer = new FileWriter(cssFile)) {
            writer.write(".header-class { color: black; }");
        }

        CountDownLatch latch = new CountDownLatch(1);
        ProjectIndex.getInstance().indexProject(srcDir, latch::countDown);
        
        // Wait for indexing to complete (io thread -> main thread callback)
        long timeout = System.currentTimeMillis() + 5000;
        while (latch.getCount() > 0 && System.currentTimeMillis() < timeout) {
            Thread.sleep(10);
            ShadowLooper.runUiThreadTasks();
        }
        assertTrue("Indexing should complete within 5 seconds", latch.getCount() == 0);

        // Test querying symbols
        List<SymbolEntry> funcSymbols = ProjectIndex.getInstance().findSymbolsByPrefix("myGlobal");
        assertFalse("Should find myGlobalFunc", funcSymbols.isEmpty());
        
        List<SymbolEntry> constSymbols = ProjectIndex.getInstance().findSymbolsByPrefix("MY_");
        assertFalse("Should find MY_CONST", constSymbols.isEmpty());
        
        List<SymbolEntry> cssSymbols = ProjectIndex.getInstance().findSymbolsByPrefix(".header");
        assertFalse("Should find .header-class", cssSymbols.isEmpty());

        // Test finding exact declaration
        List<LspLocation> exactMatches = ProjectIndex.getInstance().findDefinitions("myGlobalFunc");
        assertFalse("Should find exact match", exactMatches.isEmpty());
        
        // Test document caching
        LspDocument doc = ProjectIndex.getInstance().getDocument(jsFile.getAbsolutePath());
        assertNotNull("Should have cached LspDocument for JS", doc);
        assertTrue(doc.text.contains("MY_CONST"));
    }

    @Test
    public void testSymbolsIdxPersistenceAndCacheConsumption() throws IOException, InterruptedException {
        File srcDir = tempFolder.newFolder("workspace");

        File jsFile = new File(srcDir, "app.js");
        try (FileWriter writer = new FileWriter(jsFile)) {
            writer.write("function startApp() {}\nconst APP_NAME = 'VCode';");
        }

        File cssFile = new File(srcDir, "app.css");
        try (FileWriter writer = new FileWriter(cssFile)) {
            writer.write(".app-banner { display: block; }");
        }

        CountDownLatch latch1 = new CountDownLatch(1);
        ProjectIndex.getInstance().indexProject(srcDir, latch1::countDown);

        long timeout = System.currentTimeMillis() + 5000;
        while (latch1.getCount() > 0 && System.currentTimeMillis() < timeout) {
            Thread.sleep(10);
            ShadowLooper.runUiThreadTasks();
        }
        assertTrue("Initial indexing should complete", latch1.getCount() == 0);

        // Verify .vcode/cache/symbols.idx exists
        File cacheFile = new File(srcDir, ".vcode/cache/symbols.idx");
        assertTrue("symbols.idx must be written to disk", cacheFile.exists());

        // Clear in-memory indices to simulate fresh session / cold restart
        ProjectIndex.getInstance().clear();
        assertTrue("All URIs should be empty after clear", ProjectIndex.getInstance().getAllUris().isEmpty());

        // Inject a stray .tmp file into .vcode/cache/
        File cacheDir = cacheFile.getParentFile();
        File strayTmp = new File(cacheDir, "symbols.idx.9999.tmp");
        try (FileWriter writer = new FileWriter(strayTmp)) {
            writer.write("abandoned tmp data");
        }
        assertTrue("Stray tmp file should exist before re-indexing", strayTmp.exists());

        // Re-index project (cold restart loading from symbols.idx)
        CountDownLatch latch2 = new CountDownLatch(1);
        ProjectIndex.getInstance().indexProject(srcDir, latch2::countDown);

        timeout = System.currentTimeMillis() + 5000;
        while (latch2.getCount() > 0 && System.currentTimeMillis() < timeout) {
            Thread.sleep(10);
            ShadowLooper.runUiThreadTasks();
        }
        assertTrue("Re-indexing should complete", latch2.getCount() == 0);

        // Verify that getAllUris() returns the cached files!
        List<String> uris = ProjectIndex.getInstance().getAllUris();
        assertFalse("getAllUris() must not be empty when symbols.idx is loaded", uris.isEmpty());

        // Verify symbols are served from cache
        List<SymbolEntry> cssSymbols = ProjectIndex.getInstance().getFileSymbols(cssFile.getAbsolutePath());
        assertFalse("Cached CSS symbols must be available", cssSymbols.isEmpty());
        assertEquals(".app-banner", cssSymbols.get(0).name);

        // Verify lazy document loading on demand
        LspDocument doc = ProjectIndex.getInstance().getDocument(cssFile.getAbsolutePath());
        assertNotNull("getDocument should lazily load cached file on demand", doc);
        assertTrue(doc.text.contains(".app-banner"));

        // Verify stray tmp file was purged
        assertFalse("Stray tmp file in .vcode/cache/ should be cleaned up", strayTmp.exists());

        // Test deleted file pruning: delete app.css
        assertTrue(cssFile.delete());

        CountDownLatch latch3 = new CountDownLatch(1);
        ProjectIndex.getInstance().indexProject(srcDir, latch3::countDown);

        timeout = System.currentTimeMillis() + 5000;
        while (latch3.getCount() > 0 && System.currentTimeMillis() < timeout) {
            Thread.sleep(10);
            ShadowLooper.runUiThreadTasks();
        }
        assertTrue("Re-indexing after delete should complete", latch3.getCount() == 0);

        // Confirm deleted file is pruned from getAllUris()
        boolean cssStillPresent = false;
        for (String u : ProjectIndex.getInstance().getAllUris()) {
            if (u.endsWith("app.css")) {
                cssStillPresent = true;
                break;
            }
        }
        assertFalse("Deleted file should be pruned from index", cssStillPresent);
    }
}

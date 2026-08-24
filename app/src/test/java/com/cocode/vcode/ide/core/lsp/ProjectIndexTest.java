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
}

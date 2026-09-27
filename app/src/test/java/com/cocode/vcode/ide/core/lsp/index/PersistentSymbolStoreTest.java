package com.cocode.vcode.ide.core.lsp.index;

import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.SymbolEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link PersistentSymbolStore}:
 * 1. Binary index roundtrip serialization and deserialization.
 * 2. Relative path resolution with project root.
 * 3. Graceful recovery on non-existent, empty, or corrupt files.
 * 4. Index deletion.
 */
public class PersistentSymbolStoreTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File projectRoot;
    private File cacheFile;

    @Before
    public void setUp() throws IOException {
        projectRoot = tempFolder.newFolder("test_project");
        File cacheDir = new File(projectRoot, ".vcode/cache");
        cacheDir.mkdirs();
        cacheFile = new File(cacheDir, "symbols.idx");
    }

    @After
    public void tearDown() {
        PersistentSymbolStore.deleteIndex(cacheFile);
    }

    @Test
    public void testRoundTripSerialization() {
        File file1 = new File(projectRoot, "src/app.js");
        File file2 = new File(projectRoot, "styles/main.css");

        List<SymbolEntry> syms1 = new ArrayList<>();
        syms1.add(new SymbolEntry("renderApp", file1.getAbsolutePath(), new LspRange(1, 0, 10, 1), SymbolEntry.KIND_FUNCTION, "function(): void"));
        syms1.add(new SymbolEntry("APP_VERSION", file1.getAbsolutePath(), new LspRange(12, 0, 12, 20), SymbolEntry.KIND_CONSTANT, "string"));

        List<SymbolEntry> syms2 = new ArrayList<>();
        syms2.add(new SymbolEntry(".main-container", file2.getAbsolutePath(), new LspRange(0, 0, 5, 1), SymbolEntry.KIND_CSS_CLASS, null));

        Map<String, PersistentSymbolStore.FileSymbolRecord> records = new HashMap<>();
        records.put(file1.getAbsolutePath(), new PersistentSymbolStore.FileSymbolRecord(123456789L, 1024L, syms1));
        records.put(file2.getAbsolutePath(), new PersistentSymbolStore.FileSymbolRecord(987654321L, 2048L, syms2));

        // Save
        PersistentSymbolStore.saveIndex(cacheFile, projectRoot.getAbsolutePath(), records);
        assertTrue("Cache file should exist after saving", cacheFile.exists());
        assertTrue("Cache file should not be empty", cacheFile.length() > 0);

        // Load
        Map<String, PersistentSymbolStore.FileSymbolRecord> loaded =
                PersistentSymbolStore.loadIndex(cacheFile, projectRoot.getAbsolutePath());

        assertNotNull(loaded);
        assertEquals(2, loaded.size());

        PersistentSymbolStore.FileSymbolRecord rec1 = loaded.get(file1.getAbsolutePath());
        assertNotNull(rec1);
        assertEquals(123456789L, rec1.lastModified);
        assertEquals(1024L, rec1.fileLength);
        assertEquals(2, rec1.symbols.size());

        SymbolEntry s1 = rec1.symbols.get(0);
        assertEquals("renderApp", s1.name);
        assertEquals(SymbolEntry.KIND_FUNCTION, s1.kind);
        assertEquals(1, s1.range.start.line);
        assertEquals(0, s1.range.start.character);
        assertEquals(10, s1.range.end.line);
        assertEquals(1, s1.range.end.character);
        assertEquals("function(): void", s1.detail);

        SymbolEntry s2 = rec1.symbols.get(1);
        assertEquals("APP_VERSION", s2.name);
        assertEquals(SymbolEntry.KIND_CONSTANT, s2.kind);

        PersistentSymbolStore.FileSymbolRecord rec2 = loaded.get(file2.getAbsolutePath());
        assertNotNull(rec2);
        assertEquals(987654321L, rec2.lastModified);
        assertEquals(2048L, rec2.fileLength);
        assertEquals(1, rec2.symbols.size());
        assertEquals(".main-container", rec2.symbols.get(0).name);
        assertNull(rec2.symbols.get(0).detail);
    }

    @Test
    public void testNonExistentFileReturnsEmptyMap() {
        File missing = new File(projectRoot, "does_not_exist.idx");
        Map<String, PersistentSymbolStore.FileSymbolRecord> result =
                PersistentSymbolStore.loadIndex(missing, projectRoot.getAbsolutePath());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testCorruptHeaderReturnsEmptyMapGracefully() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            fos.write(new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B});
        }
        assertTrue(cacheFile.exists());

        Map<String, PersistentSymbolStore.FileSymbolRecord> result =
                PersistentSymbolStore.loadIndex(cacheFile, projectRoot.getAbsolutePath());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testDeleteIndex() {
        Map<String, PersistentSymbolStore.FileSymbolRecord> records = new HashMap<>();
        records.put("dummy.js", new PersistentSymbolStore.FileSymbolRecord(100L, 200L, new ArrayList<>()));
        PersistentSymbolStore.saveIndex(cacheFile, projectRoot.getAbsolutePath(), records);
        assertTrue(cacheFile.exists());

        PersistentSymbolStore.deleteIndex(cacheFile);
        assertFalse(cacheFile.exists());
    }
}

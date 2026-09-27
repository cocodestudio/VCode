package com.cocode.vcode.ide.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Unit tests verifying crash-safe atomic write functionality in {@link FileUtils}.
 */
public class FileUtilsAtomicTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testAtomicWriteCreatesNewFileAndDirectories() throws IOException {
        File subDir = new File(tempFolder.getRoot(), "sub/nested/dir");
        File target = new File(subDir, "test.txt");

        String content = "Hello Atomic World!\nLine 2";
        FileUtils.writeFile(target, content);

        assertTrue(target.exists());
        assertEquals(content, FileUtils.readFile(target));
    }

    @Test
    public void testAtomicWriteOverwritesExistingFileSafely() throws IOException {
        File target = tempFolder.newFile("override.txt");
        FileUtils.writeFile(target, "Initial Content");
        assertEquals("Initial Content", FileUtils.readFile(target));

        String newContent = "Updated Content with UTF-8 characters: ⚡🚀✨";
        FileUtils.writeFile(target, newContent);
        assertEquals(newContent, FileUtils.readFile(target));

        // Ensure no temporary residue files remain
        File parent = target.getParentFile();
        assertNotNull(parent);
        File[] siblings = parent.listFiles();
        assertNotNull(siblings);
        for (File f : siblings) {
            assertFalse("Temporary file should be cleaned up: " + f.getName(), f.getName().endsWith(".tmp"));
        }
    }
}

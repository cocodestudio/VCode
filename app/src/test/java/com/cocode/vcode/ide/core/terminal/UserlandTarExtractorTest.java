package com.cocode.vcode.ide.core.terminal;

import com.cocode.vcode.ide.core.terminal.userland.UserlandTarExtractor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for UserlandTarExtractor stream parsing, directory creation,
 * symlinks, and Zip Slip path traversal defenses.
 */
public class UserlandTarExtractorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testExtractNormalFilesAndDirectories() throws IOException {
        File destDir = tempFolder.newFolder("dest_normal");

        ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
        // Entry 1: Directory "bin/"
        appendTarEntry(tarBytes, "bin/", 0, '5', null, null);
        // Entry 2: File "bin/bash" with content "echo bash"
        byte[] bashContent = "#!/bin/sh\necho bash\n".getBytes(StandardCharsets.UTF_8);
        appendTarEntry(tarBytes, "bin/bash", bashContent.length, '0', null, bashContent);
        // Entry 3: File "etc/bash.bashrc"
        byte[] bashrcContent = "export PATH=$PREFIX/bin\n".getBytes(StandardCharsets.UTF_8);
        appendTarEntry(tarBytes, "etc/bash.bashrc", bashrcContent.length, '0', null, bashrcContent);
        // Add end-of-archive marker (two 512-byte zero blocks)
        tarBytes.write(new byte[1024]);

        int extracted = UserlandTarExtractor.extractTarStream(
                new ByteArrayInputStream(tarBytes.toByteArray()), destDir, null);

        assertEquals(2, extracted);

        File binDir = new File(destDir, "bin");
        assertTrue(binDir.exists() && binDir.isDirectory());

        File bashFile = new File(destDir, "bin/bash");
        assertTrue(bashFile.exists() && bashFile.isFile());
        assertEquals("#!/bin/sh\necho bash\n", readFile(bashFile));

        File bashrcFile = new File(destDir, "etc/bash.bashrc");
        assertTrue(bashrcFile.exists() && bashrcFile.isFile());
        assertEquals("export PATH=$PREFIX/bin\n", readFile(bashrcFile));
    }

    @Test
    public void testExtractSymlinks() throws IOException {
        File destDir = tempFolder.newFolder("dest_symlink");

        ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
        byte[] bashContent = "bash-binary".getBytes(StandardCharsets.UTF_8);
        appendTarEntry(tarBytes, "bin/bash", bashContent.length, '0', null, bashContent);
        // Symlink: bin/sh -> bash
        appendTarEntry(tarBytes, "bin/sh", 0, '2', "bash", null);
        tarBytes.write(new byte[1024]);

        int extracted = UserlandTarExtractor.extractTarStream(
                new ByteArrayInputStream(tarBytes.toByteArray()), destDir, null);

        assertEquals(2, extracted);

        File shFile = new File(destDir, "bin/sh");
        assertTrue(shFile.exists());
    }

    @Test
    public void testLongLinkEntryHandling() throws IOException {
        File destDir = tempFolder.newFolder("dest_longlink");

        String longName = "lib/python3.11/site-packages/very_deep_nested_directory_structure_for_long_entry_names_that_exceed_one_hundred_characters_in_posix_tar_spec/module.py";
        byte[] moduleContent = "print('hello from deep module')\n".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
        // GNU LongLink entry ('L')
        byte[] nameBytes = (longName + "\0").getBytes(StandardCharsets.UTF_8);
        appendTarEntry(tarBytes, "././@LongLink", nameBytes.length, 'L', null, nameBytes);
        // Truncated header entry that consumes the long name
        appendTarEntry(tarBytes, "truncated_dummy_name", moduleContent.length, '0', null, moduleContent);
        tarBytes.write(new byte[1024]);

        int extracted = UserlandTarExtractor.extractTarStream(
                new ByteArrayInputStream(tarBytes.toByteArray()), destDir, null);

        assertEquals(1, extracted);
        File extractedFile = new File(destDir, longName);
        assertTrue("Long entry file should exist at: " + extractedFile.getAbsolutePath(), extractedFile.exists());
        assertEquals("print('hello from deep module')\n", readFile(extractedFile));
    }

    @Test
    public void testZipSlipPathTraversalBlocked() throws IOException {
        File destDir = tempFolder.newFolder("dest_zipslip");

        ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
        byte[] exploitContent = "malicious payload".getBytes(StandardCharsets.UTF_8);
        // Malicious entry attempting path traversal outside destination
        appendTarEntry(tarBytes, "../../../escaped_file.txt", exploitContent.length, '0', null, exploitContent);
        tarBytes.write(new byte[1024]);

        try {
            UserlandTarExtractor.extractTarStream(
                    new ByteArrayInputStream(tarBytes.toByteArray()), destDir, null);
            fail("Extraction should fail with IOException due to path traversal attempt");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Illegal path traversal"));
        }
    }

    @Test
    public void testDotSlashPrefixAndSymlinkExtraction() throws IOException {
        File destDir = tempFolder.newFolder("dest_dotslash");

        ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
        // Entry 1: Root directory "."
        appendTarEntry(tarBytes, ".", 0, '5', null, null);
        // Entry 2: Root directory "./"
        appendTarEntry(tarBytes, "./", 0, '5', null, null);
        // Entry 3: "./etc/" directory
        appendTarEntry(tarBytes, "./etc/", 0, '5', null, null);
        // Entry 4: "./etc/mtab" symlink -> "../proc/mounts"
        appendTarEntry(tarBytes, "./etc/mtab", 0, '2', "../proc/mounts", null);
        // Entry 5: "./bin/sh" file
        byte[] shContent = "echo sh".getBytes(StandardCharsets.UTF_8);
        appendTarEntry(tarBytes, "./bin/sh", shContent.length, '0', null, shContent);
        tarBytes.write(new byte[1024]);

        byte[] rawTar = tarBytes.toByteArray();

        // First extraction
        int extracted = UserlandTarExtractor.extractTarStream(
                new ByteArrayInputStream(rawTar), destDir, null);
        assertTrue(extracted >= 2);

        // Second extraction over existing files and symlinks (must not throw Zip Slip false positive)
        int reExtracted = UserlandTarExtractor.extractTarStream(
                new ByteArrayInputStream(rawTar), destDir, null);
        assertTrue(reExtracted >= 2);

        File shFile = new File(destDir, "bin/sh");
        assertTrue(shFile.exists() && shFile.isFile());
    }

    private void appendTarEntry(ByteArrayOutputStream out, String name, long size, char typeFlag,
                                String linkTarget, byte[] content) {
        byte[] header = new byte[512];

        // Name (0..100)
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 100));

        // Mode (100..108)
        writeOctal(header, 100, 8, 0755);

        // UID & GID
        writeOctal(header, 108, 8, 1000);
        writeOctal(header, 116, 8, 1000);

        // Size (124..136)
        writeOctal(header, 124, 12, size);

        // Mtime (136..148)
        writeOctal(header, 136, 12, System.currentTimeMillis() / 1000);

        // Typeflag (156)
        header[156] = (byte) typeFlag;

        // Link target (157..257)
        if (linkTarget != null) {
            byte[] linkBytes = linkTarget.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(linkBytes, 0, header, 157, Math.min(linkBytes.length, 100));
        }

        // Magic "ustar\0" (257..263)
        header[257] = 'u';
        header[258] = 's';
        header[259] = 't';
        header[260] = 'a';
        header[261] = 'r';
        header[262] = '\0';
        header[263] = '0';
        header[264] = '0';

        // Checksum calculation (148..156)
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        long sum = 0;
        for (byte b : header) {
            sum += (b & 0xFF);
        }
        writeOctal(header, 148, 7, sum);
        header[155] = ' ';

        out.write(header, 0, 512);

        // Content
        if (content != null && content.length > 0) {
            out.write(content, 0, content.length);
            int padding = 512 - (content.length % 512);
            if (padding < 512) {
                out.write(new byte[padding], 0, padding);
            }
        }
    }

    private void writeOctal(byte[] buffer, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        int writePos = offset + length - 2;
        buffer[offset + length - 1] = 0; // null terminate
        for (int i = octal.length() - 1; i >= 0 && writePos >= offset; i--) {
            buffer[writePos--] = (byte) octal.charAt(i);
        }
        while (writePos >= offset) {
            buffer[writePos--] = '0';
        }
    }

    private String readFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] b = new byte[(int) file.length()];
            in.read(b);
            return new String(b, StandardCharsets.UTF_8);
        }
    }
}

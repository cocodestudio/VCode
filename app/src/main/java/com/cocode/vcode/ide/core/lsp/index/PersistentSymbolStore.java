package com.cocode.vcode.ide.core.lsp.index;

import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.SymbolEntry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance, crash-safe on-disk binary cache for workspace symbol tables.
 * <p>
 * Stores symbol records in a compact binary format (".vcode/cache/symbols.idx") to allow
 * sub-50ms cold-start project symbol loading without re-parsing source files.
 */
public final class PersistentSymbolStore {

    /**
     * Binary format magic number: 'VCDX' (0x56434458).
     */
    private static final int MAGIC = 0x56434458;

    /**
     * Binary format version.
     */
    private static final int VERSION = 1;

    private PersistentSymbolStore() {
    }

    /**
     * Serializes workspace symbol records to an on-disk binary cache file using an atomic write.
     *
     * @param cacheFile   destination file (e.g. .vcode/cache/symbols.idx)
     * @param projectRoot optional project root directory to relativize paths for portability
     * @param records     map of file paths to their symbol records
     */
    public static void saveIndex(File cacheFile, String projectRoot, Map<String, FileSymbolRecord> records) {
        if (cacheFile == null || records == null) return;

        File parent = cacheFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile = new File(parent != null ? parent : cacheFile.getAbsoluteFile().getParentFile(),
                cacheFile.getName() + "." + System.currentTimeMillis() + ".tmp");

        try {
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 DataOutputStream dos = new DataOutputStream(bos)) {

                dos.writeInt(MAGIC);
                dos.writeInt(VERSION);
                dos.writeInt(records.size());

                for (Map.Entry<String, FileSymbolRecord> entry : records.entrySet()) {
                    String uri = entry.getKey();
                    FileSymbolRecord record = entry.getValue();
                    if (record == null) continue;

                    String path = uri;
                    if (projectRoot != null && path.startsWith(projectRoot)) {
                        path = path.substring(projectRoot.length());
                        while (path.startsWith("/") || path.startsWith("\\")) {
                            path = path.substring(1);
                        }
                        path = path.replace('\\', '/');
                    }

                    dos.writeUTF(path);
                    dos.writeLong(record.lastModified);
                    dos.writeLong(record.fileLength);

                    List<SymbolEntry> symbols = record.symbols != null ? record.symbols : Collections.emptyList();
                    dos.writeInt(symbols.size());
                    for (SymbolEntry s : symbols) {
                        dos.writeUTF(s.name != null ? s.name : "");
                        dos.writeInt(s.kind);

                        int startLine = (s.range != null && s.range.start != null) ? s.range.start.line : 0;
                        int startCol = (s.range != null && s.range.start != null) ? s.range.start.character : 0;
                        int endLine = (s.range != null && s.range.end != null) ? s.range.end.line : 0;
                        int endCol = (s.range != null && s.range.end != null) ? s.range.end.character : 0;

                        dos.writeInt(startLine);
                        dos.writeInt(startCol);
                        dos.writeInt(endLine);
                        dos.writeInt(endCol);

                        dos.writeUTF(s.detail != null ? s.detail : "");
                    }
                }

                dos.flush();
                fos.getFD().sync();
            }

            // Atomic rename
            if (!tempFile.renameTo(cacheFile)) {
                if (cacheFile.exists() && cacheFile.delete()) {
                    tempFile.renameTo(cacheFile);
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Loads workspace symbol records from the on-disk binary cache file.
     *
     * @param cacheFile   binary cache file
     * @param projectRoot optional project root directory to resolve relative paths
     * @return map of absolute file paths to their symbol records, or empty map if invalid/corrupt
     */
    public static Map<String, FileSymbolRecord> loadIndex(File cacheFile, String projectRoot) {
        if (cacheFile == null || !cacheFile.exists() || cacheFile.length() < 12) {
            return Collections.emptyMap();
        }

        Map<String, FileSymbolRecord> map = new HashMap<>();

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile)))) {
            int magic = dis.readInt();
            int version = dis.readInt();
            if (magic != MAGIC || version != VERSION) {
                return Collections.emptyMap();
            }

            int fileCount = dis.readInt();
            for (int i = 0; i < fileCount; i++) {
                String rawPath = dis.readUTF();
                long lastModified = dis.readLong();
                long fileLength = dis.readLong();

                String resolvedPath;
                if (projectRoot != null && !new File(rawPath).isAbsolute()) {
                    resolvedPath = new File(projectRoot, rawPath).getAbsolutePath();
                } else {
                    resolvedPath = rawPath;
                }

                int symCount = dis.readInt();
                List<SymbolEntry> symbols = new ArrayList<>(symCount);

                for (int j = 0; j < symCount; j++) {
                    String name = dis.readUTF();
                    int kind = dis.readInt();
                    int startLine = dis.readInt();
                    int startCol = dis.readInt();
                    int endLine = dis.readInt();
                    int endCol = dis.readInt();
                    String detail = dis.readUTF();
                    if (detail.isEmpty()) {
                        detail = null;
                    }

                    LspRange range = new LspRange(
                            new LspPosition(startLine, startCol),
                            new LspPosition(endLine, endCol)
                    );
                    symbols.add(new SymbolEntry(name, resolvedPath, range, kind, detail));
                }

                map.put(resolvedPath, new FileSymbolRecord(lastModified, fileLength, symbols));
            }
            return map;
        } catch (Exception e) {
            // In case of any I/O error or corruption, recover gracefully with an empty cache
            return Collections.emptyMap();
        }
    }

    /**
     * Deletes the persistent symbol index file if present.
     *
     * @param cacheFile cache file to delete
     */
    public static void deleteIndex(File cacheFile) {
        if (cacheFile != null && cacheFile.exists()) {
            cacheFile.delete();
        }
    }

    /**
     * In-memory record holding file metadata (timestamp and length) and its extracted symbols.
     */
    public static final class FileSymbolRecord {
        public final long lastModified;
        public final long fileLength;
        public final List<SymbolEntry> symbols;

        public FileSymbolRecord(long lastModified, long fileLength, List<SymbolEntry> symbols) {
            this.lastModified = lastModified;
            this.fileLength = fileLength;
            this.symbols = symbols != null ? symbols : Collections.emptyList();
        }
    }
}

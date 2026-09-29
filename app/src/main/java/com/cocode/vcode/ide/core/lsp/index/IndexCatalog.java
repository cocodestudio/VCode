package com.cocode.vcode.ide.core.lsp.index;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry of workspace file fingerprints and segment mappings.
 * <p>
 * Stored at {@code .vcode/cache/symbols/catalog.bin}.
 * Enables instant detection of dirty vs clean files via fast timestamp and length checks.
 */
public class IndexCatalog {

    public static final int MAGIC = 0x56434458; // 'VCDX'
    public static final int VERSION = 3;

    public static final class FileFingerprint {
        public final int fileId;
        public final long lastModified;
        public final long fileLength;
        public final int segmentId;

        public FileFingerprint(int fileId, long lastModified, long fileLength, int segmentId) {
            this.fileId = fileId;
            this.lastModified = lastModified;
            this.fileLength = fileLength;
            this.segmentId = segmentId;
        }
    }

    private final Map<String, FileFingerprint> fileToFingerprint = new HashMap<>();
    private final Map<Integer, String> idToPath = new HashMap<>();
    private int nextFileId = 1;
    private int nextSegmentId = 1;

    public synchronized boolean isFileUpToDate(File file, String relativePath) {
        if (file == null || !file.exists() || relativePath == null) return false;
        FileFingerprint fp = fileToFingerprint.get(relativePath);
        if (fp == null) return false;
        long lastMod = file.lastModified();
        long len = file.length();
        return fp.fileLength == len && (fp.lastModified == lastMod || Math.abs(fp.lastModified - lastMod) <= 1000);
    }

    public synchronized int getOrAssignFileId(String relativePath) {
        FileFingerprint fp = fileToFingerprint.get(relativePath);
        if (fp != null) {
            return fp.fileId;
        }
        int id = nextFileId++;
        idToPath.put(id, relativePath);
        return id;
    }

    public synchronized String getPathForFileId(int fileId) {
        return idToPath.get(fileId);
    }

    public synchronized void recordFile(String relativePath, File file, int segmentId) {
        if (relativePath == null || file == null) return;
        int fileId = getOrAssignFileId(relativePath);
        FileFingerprint fp = new FileFingerprint(fileId, file.lastModified(), file.length(), segmentId);
        fileToFingerprint.put(relativePath, fp);
        idToPath.put(fileId, relativePath);
    }

    public synchronized void removeFile(String relativePath) {
        if (relativePath == null) return;
        FileFingerprint fp = fileToFingerprint.remove(relativePath);
        if (fp != null) {
            idToPath.remove(fp.fileId);
        }
    }

    public synchronized Set<String> getTrackedFiles() {
        return Collections.unmodifiableSet(fileToFingerprint.keySet());
    }

    public synchronized Set<Integer> getTrackedSegmentIds() {
        Set<Integer> segIds = new HashSet<>();
        for (FileFingerprint fp : fileToFingerprint.values()) {
            if (fp != null && fp.segmentId > 0) {
                segIds.add(fp.segmentId);
            }
        }
        return segIds;
    }

    public synchronized FileFingerprint getFingerprint(String relativePath) {
        return fileToFingerprint.get(relativePath);
    }

    public synchronized int allocateNextSegmentId() {
        return nextSegmentId++;
    }

    public synchronized void clear() {
        fileToFingerprint.clear();
        idToPath.clear();
        nextFileId = 1;
        nextSegmentId = 1;
    }

    public synchronized void save(File catalogFile) throws IOException {
        if (catalogFile == null) return;
        File parent = catalogFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile = new File(parent != null ? parent : catalogFile.getAbsoluteFile().getParentFile(),
                catalogFile.getName() + "." + System.currentTimeMillis() + ".tmp");

        try {
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 DataOutputStream dos = new DataOutputStream(bos)) {

                dos.writeInt(MAGIC);
                dos.writeInt(VERSION);
                dos.writeInt(fileToFingerprint.size());
                dos.writeInt(nextFileId);
                dos.writeInt(nextSegmentId);

                for (Map.Entry<String, FileFingerprint> entry : fileToFingerprint.entrySet()) {
                    dos.writeUTF(entry.getKey());
                    FileFingerprint fp = entry.getValue();
                    dos.writeInt(fp.fileId);
                    dos.writeLong(fp.lastModified);
                    dos.writeLong(fp.fileLength);
                    dos.writeInt(fp.segmentId);
                }

                dos.flush();
                fos.getFD().sync();
            }

            if (!tempFile.renameTo(catalogFile)) {
                if (catalogFile.exists() && catalogFile.delete()) {
                    tempFile.renameTo(catalogFile);
                }
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public static IndexCatalog load(File catalogFile) {
        IndexCatalog catalog = new IndexCatalog();
        if (catalogFile == null || !catalogFile.exists() || catalogFile.length() < 20) {
            return catalog;
        }

        try (FileInputStream fis = new FileInputStream(catalogFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             DataInputStream dis = new DataInputStream(bis)) {

            int magic = dis.readInt();
            int version = dis.readInt();
            if (magic != MAGIC || version != VERSION) {
                return catalog;
            }

            int count = dis.readInt();
            catalog.nextFileId = dis.readInt();
            catalog.nextSegmentId = dis.readInt();

            for (int i = 0; i < count; i++) {
                String path = dis.readUTF();
                int fileId = dis.readInt();
                long mtime = dis.readLong();
                long length = dis.readLong();
                int segId = dis.readInt();
                FileFingerprint fp = new FileFingerprint(fileId, mtime, length, segId);
                catalog.fileToFingerprint.put(path, fp);
                catalog.idToPath.put(fileId, path);
            }
        } catch (Exception ignored) {
            catalog.clear();
        }
        return catalog;
    }
}

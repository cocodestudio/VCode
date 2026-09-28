package com.cocode.vcode.ide.core.lsp.index;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Memory-efficient reader for an immutable flat-memory segment file.
 * <p>
 * Uses {@link BoundedBlockCache} for LRU caching of 8KB blocks and executes
 * prefix pushdown queries via binary search over the block index table.
 */
public class SegmentReader implements Closeable {

    private final File segmentFile;
    private final int segmentId;
    private final int blockCount;
    private final int totalEntryCount;
    private final BlockMeta[] blockIndex;
    private final String[] stringPool;
    private final BoundedBlockCache cache;
    private final RandomAccessFile raf;
    private final Object fileLock = new Object();
    private volatile boolean isClosed = false;

    private static class BlockMeta {
        final String firstKey;
        final long fileOffset;
        final int dataLength;
        final int entryCount;

        BlockMeta(String firstKey, long fileOffset, int dataLength, int entryCount) {
            this.firstKey = firstKey;
            this.fileOffset = fileOffset;
            this.dataLength = dataLength;
            this.entryCount = entryCount;
        }
    }

    public SegmentReader(File segmentFile, BoundedBlockCache cache) throws IOException {
        this.segmentFile = segmentFile;
        this.cache = cache != null ? cache : new BoundedBlockCache();
        this.raf = new RandomAccessFile(segmentFile, "r");

        try {
            // Read Header (32 bytes)
            int magic = raf.readInt();
            int version = raf.readInt();
            if (magic != SegmentWriter.MAGIC || version != SegmentWriter.VERSION) {
                throw new IOException("Invalid segment magic or version in " + segmentFile.getName());
            }

            this.segmentId = raf.readInt();
            this.blockCount = raf.readInt();
            this.totalEntryCount = raf.readInt();
            long stringPoolOffset = raf.readLong();
            int stringPoolLength = raf.readInt();

            // Read Block Index Table
            this.blockIndex = new BlockMeta[blockCount];
            for (int i = 0; i < blockCount; i++) {
                String firstKey = raf.readUTF();
                long offset = raf.readLong();
                int len = raf.readInt();
                int entries = raf.readInt();
                blockIndex[i] = new BlockMeta(firstKey, offset, len, entries);
            }

            // Read String Pool
            if (stringPoolLength > 0 && stringPoolOffset > 0) {
                raf.seek(stringPoolOffset);
                byte[] poolBytes = new byte[stringPoolLength];
                raf.readFully(poolBytes);
                try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(poolBytes))) {
                    int poolSize = dis.readInt();
                    this.stringPool = new String[poolSize];
                    for (int i = 0; i < poolSize; i++) {
                        stringPool[i] = dis.readUTF();
                    }
                }
            } else {
                this.stringPool = new String[0];
            }
        } catch (Exception e) {
            try {
                raf.close();
            } catch (Exception ignored) {
            }
            throw (e instanceof IOException) ? (IOException) e : new IOException(e);
        }
    }

    public int getSegmentId() {
        return segmentId;
    }

    public int getTotalEntryCount() {
        return totalEntryCount;
    }

    public int getBlockCount() {
        return blockCount;
    }

    private byte[] loadBlockData(int blockIdx) throws IOException {
        if (blockIdx < 0 || blockIdx >= blockCount) return null;
        byte[] cached = cache.get(segmentId, blockIdx);
        if (cached != null) {
            return cached;
        }

        BlockMeta meta = blockIndex[blockIdx];
        byte[] buffer = new byte[meta.dataLength];
        synchronized (fileLock) {
            if (isClosed) return null;
            raf.seek(meta.fileOffset);
            raf.readFully(buffer);
        }
        cache.put(segmentId, blockIdx, buffer);
        return buffer;
    }

    /**
     * Finds the candidate starting block index using binary search on firstKey.
     */
    private int findStartBlockIndex(String prefix) {
        if (blockCount == 0) return -1;
        int low = 0;
        int high = blockCount - 1;
        int result = 0;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            String midKey = blockIndex[mid].firstKey;
            if (midKey.compareTo(prefix) <= 0) {
                result = mid; // Candidate block could contain prefix
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    /**
     * Executes prefix pushdown query against the segment blocks.
     *
     * @param prefix key prefix (e.g. "css:cls:btn-")
     * @param limit  maximum results to return (<= 0 for no limit)
     * @return list of matching symbol entries
     */
    public List<IndexSymbolEntry> queryPrefix(String prefix, int limit) {
        if (isClosed || blockCount == 0 || prefix == null) {
            return Collections.emptyList();
        }

        int maxResults = limit > 0 ? limit : Integer.MAX_VALUE;
        List<IndexSymbolEntry> results = new ArrayList<>();
        int startBlock = findStartBlockIndex(prefix);

        for (int b = startBlock; b < blockCount; b++) {
            BlockMeta meta = blockIndex[b];
            // If the block's first key is already lexicographically greater than prefix
            // and doesn't start with prefix, we can stop searching.
            if (b > startBlock && meta.firstKey.compareTo(prefix) > 0 && !meta.firstKey.startsWith(prefix)) {
                break;
            }

            try {
                byte[] data = loadBlockData(b);
                if (data == null) continue;

                try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
                    for (int i = 0; i < meta.entryCount; i++) {
                        String key = dis.readUTF();
                        String name = dis.readUTF();
                        byte kind = dis.readByte();
                        byte flags = dis.readByte();
                        int fileId = dis.readInt();
                        int line = dis.readInt();
                        int detailRef = dis.readInt();
                        String detail = (detailRef >= 0 && detailRef < stringPool.length) ? stringPool[detailRef] : "";

                        if (key.startsWith(prefix)) {
                            results.add(new IndexSymbolEntry(key, name, kind, flags, fileId, line, detail));
                            if (results.size() >= maxResults) {
                                return results;
                            }
                        } else if (key.compareTo(prefix) > 0) {
                            // Since keys inside each block are sorted, we can stop searching
                            return results;
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        return results;
    }

    /**
     * Queries for exact key matches.
     */
    public List<IndexSymbolEntry> queryExact(String key) {
        if (isClosed || blockCount == 0 || key == null) {
            return Collections.emptyList();
        }

        List<IndexSymbolEntry> results = new ArrayList<>();
        int startBlock = findStartBlockIndex(key);

        for (int b = startBlock; b < blockCount; b++) {
            BlockMeta meta = blockIndex[b];
            if (b > startBlock && meta.firstKey.compareTo(key) > 0) {
                break;
            }

            try {
                byte[] data = loadBlockData(b);
                if (data == null) continue;

                try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
                    for (int i = 0; i < meta.entryCount; i++) {
                        String k = dis.readUTF();
                        String name = dis.readUTF();
                        byte kind = dis.readByte();
                        byte flags = dis.readByte();
                        int fileId = dis.readInt();
                        int line = dis.readInt();
                        int detailRef = dis.readInt();
                        String detail = (detailRef >= 0 && detailRef < stringPool.length) ? stringPool[detailRef] : "";

                        if (k.equals(key)) {
                            results.add(new IndexSymbolEntry(k, name, kind, flags, fileId, line, detail));
                        } else if (k.compareTo(key) > 0) {
                            return results;
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        return results;
    }

    /**
     * Reads all symbol entries in the segment (used during compaction or verification).
     */
    public List<IndexSymbolEntry> readAllEntries() {
        if (isClosed || blockCount == 0) return Collections.emptyList();
        List<IndexSymbolEntry> results = new ArrayList<>(totalEntryCount);

        for (int b = 0; b < blockCount; b++) {
            BlockMeta meta = blockIndex[b];
            try {
                byte[] data = loadBlockData(b);
                if (data == null) continue;

                try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
                    for (int i = 0; i < meta.entryCount; i++) {
                        String key = dis.readUTF();
                        String name = dis.readUTF();
                        byte kind = dis.readByte();
                        byte flags = dis.readByte();
                        int fileId = dis.readInt();
                        int line = dis.readInt();
                        int detailRef = dis.readInt();
                        String detail = (detailRef >= 0 && detailRef < stringPool.length) ? stringPool[detailRef] : "";
                        results.add(new IndexSymbolEntry(key, name, kind, flags, fileId, line, detail));
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return results;
    }

    @Override
    public void close() {
        isClosed = true;
        synchronized (fileLock) {
            try {
                raf.close();
            } catch (Exception ignored) {
            }
        }
        if (cache != null) {
            cache.invalidateSegment(segmentId);
        }
    }
}

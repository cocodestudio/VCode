package com.cocode.vcode.ide.core.lsp.index;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-safe, memory-bounded Least-Recently-Used (LRU) block cache.
 * <p>
 * Ensures that flat-memory index segment blocks (8KB each) never exceed a strict
 * resident heap ceiling (e.g. 64 blocks = 512KB), preventing memory thrashing
 * and Out-Of-Memory errors on low-end Android hardware.
 */
public class BoundedBlockCache {

    public static final int BLOCK_SIZE = 8192; // 8 KB
    public static final int DEFAULT_MAX_RESIDENT_BLOCKS = 64; // 512 KB

    private final int maxResidentBlocks;
    private final Map<Long, byte[]> lruBlocks;

    public BoundedBlockCache() {
        this(DEFAULT_MAX_RESIDENT_BLOCKS);
    }

    public BoundedBlockCache(int maxResidentBlocks) {
        this.maxResidentBlocks = Math.max(1, maxResidentBlocks);
        this.lruBlocks = new LinkedHashMap<Long, byte[]>(this.maxResidentBlocks, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) {
                return size() > BoundedBlockCache.this.maxResidentBlocks;
            }
        };
    }

    public static long makeKey(int segmentId, int blockIndex) {
        return (((long) segmentId) << 32) | (blockIndex & 0xFFFFFFFFL);
    }

    public synchronized byte[] get(int segmentId, int blockIndex) {
        long key = makeKey(segmentId, blockIndex);
        return lruBlocks.get(key);
    }

    public synchronized void put(int segmentId, int blockIndex, byte[] block) {
        if (block == null) return;
        long key = makeKey(segmentId, blockIndex);
        lruBlocks.put(key, block);
    }

    public synchronized void invalidateSegment(int segmentId) {
        long segPrefix = ((long) segmentId) << 32;
        long segMask = 0xFFFFFFFF00000000L;
        Iterator<Map.Entry<Long, byte[]>> it = lruBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, byte[]> entry = it.next();
            if ((entry.getKey() & segMask) == segPrefix) {
                it.remove();
            }
        }
    }

    public synchronized void clear() {
        lruBlocks.clear();
    }

    public synchronized int getResidentBlockCount() {
        return lruBlocks.size();
    }

    public int getMaxResidentBlocks() {
        return maxResidentBlocks;
    }
}

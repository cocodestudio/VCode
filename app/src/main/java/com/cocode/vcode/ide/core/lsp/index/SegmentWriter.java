package com.cocode.vcode.ide.core.lsp.index;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes sorted index symbol entries into immutable flat-memory segment files.
 * <p>
 * Format:
 * - 32-byte header (Magic, Version, Segment ID, Block Count, Entry Count, String Pool Offset/Length)
 * - Block Index Table (First key, file offset, data length, entry count per block)
 * - 8KB Fixed Data Blocks (Packed binary entries)
 * - String Pool (Deduplicated detail strings)
 */
public final class SegmentWriter {

    public static final int MAGIC = 0x56534547; // 'VSEG'
    public static final int VERSION = 1;
    public static final int HEADER_SIZE = 32;

    private SegmentWriter() {
    }

    private static class BlockRecord {
        final String firstKey;
        final byte[] data;
        final int entryCount;

        BlockRecord(String firstKey, byte[] data, int entryCount) {
            this.firstKey = firstKey;
            this.data = data;
            this.entryCount = entryCount;
        }
    }

    /**
     * Writes entries to an immutable segment file atomically using a temporary file.
     *
     * @param outputFile destination segment file (e.g. .vcode/cache/symbols/seg_0001.bin)
     * @param segmentId  unique segment identifier
     * @param entries    list of symbol entries to serialize
     * @throws IOException on I/O error
     */
    public static void writeSegment(File outputFile, int segmentId, List<IndexSymbolEntry> entries) throws IOException {
        if (outputFile == null) return;
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile = new File(parent != null ? parent : outputFile.getAbsoluteFile().getParentFile(),
                outputFile.getName() + "." + System.currentTimeMillis() + ".tmp");

        List<IndexSymbolEntry> sortedList = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        Collections.sort(sortedList, Comparator.comparing(IndexSymbolEntry::getKey));

        // Deduplicate strings for the string pool
        Map<String, Integer> stringToId = new HashMap<>();
        List<String> stringPool = new ArrayList<>();
        for (IndexSymbolEntry entry : sortedList) {
            String detail = entry.getDetail();
            if (!stringToId.containsKey(detail)) {
                stringToId.put(detail, stringPool.size());
                stringPool.add(detail);
            }
        }

        // Pack entries into 8KB blocks
        List<BlockRecord> blocks = new ArrayList<>();
        ByteArrayOutputStream blockBos = new ByteArrayOutputStream(BoundedBlockCache.BLOCK_SIZE);
        DataOutputStream blockDos = new DataOutputStream(blockBos);
        String currentFirstKey = null;
        int currentEntryCount = 0;

        for (IndexSymbolEntry entry : sortedList) {
            // Test entry size
            ByteArrayOutputStream testBos = new ByteArrayOutputStream();
            DataOutputStream testDos = new DataOutputStream(testBos);
            testDos.writeUTF(entry.getKey());
            testDos.writeUTF(entry.getName());
            testDos.writeByte(entry.getKind());
            testDos.writeByte(entry.getFlags());
            testDos.writeInt(entry.getFileId());
            testDos.writeInt(entry.getLine());
            Integer detailId = stringToId.get(entry.getDetail());
            testDos.writeInt(detailId != null ? detailId : 0);
            testDos.flush();
            byte[] entryBytes = testBos.toByteArray();

            if (currentEntryCount > 0 && (blockBos.size() + entryBytes.length > BoundedBlockCache.BLOCK_SIZE)) {
                // Seal current block
                blocks.add(new BlockRecord(currentFirstKey, blockBos.toByteArray(), currentEntryCount));
                blockBos.reset();
                currentFirstKey = null;
                currentEntryCount = 0;
            }

            if (currentFirstKey == null) {
                currentFirstKey = entry.getKey();
            }
            blockBos.write(entryBytes);
            currentEntryCount++;
        }

        if (currentEntryCount > 0) {
            blocks.add(new BlockRecord(currentFirstKey, blockBos.toByteArray(), currentEntryCount));
        }

        try {
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 DataOutputStream dos = new DataOutputStream(bos)) {

                // Calculate Block Index size to determine initial file offsets
                ByteArrayOutputStream indexBos = new ByteArrayOutputStream();
                DataOutputStream indexDos = new DataOutputStream(indexBos);
                for (BlockRecord b : blocks) {
                    indexDos.writeUTF(b.firstKey != null ? b.firstKey : "");
                    indexDos.writeLong(0L); // placeholder for blockFileOffset
                    indexDos.writeInt(b.data.length);
                    indexDos.writeInt(b.entryCount);
                }
                indexDos.flush();
                byte[] indexBytes = indexBos.toByteArray();

                long currentOffset = HEADER_SIZE + indexBytes.length;
                long[] blockOffsets = new long[blocks.size()];
                for (int i = 0; i < blocks.size(); i++) {
                    blockOffsets[i] = currentOffset;
                    currentOffset += blocks.get(i).data.length;
                }

                long stringPoolOffset = currentOffset;

                // Serialize String Pool
                ByteArrayOutputStream poolBos = new ByteArrayOutputStream();
                DataOutputStream poolDos = new DataOutputStream(poolBos);
                poolDos.writeInt(stringPool.size());
                for (String str : stringPool) {
                    poolDos.writeUTF(str != null ? str : "");
                }
                poolDos.flush();
                byte[] poolBytes = poolBos.toByteArray();
                int stringPoolLength = poolBytes.length;

                // Write Header (32 bytes)
                dos.writeInt(MAGIC);
                dos.writeInt(VERSION);
                dos.writeInt(segmentId);
                dos.writeInt(blocks.size());
                dos.writeInt(sortedList.size());
                dos.writeLong(stringPoolOffset);
                dos.writeInt(stringPoolLength);

                // Write Block Index Table
                for (int i = 0; i < blocks.size(); i++) {
                    BlockRecord b = blocks.get(i);
                    dos.writeUTF(b.firstKey != null ? b.firstKey : "");
                    dos.writeLong(blockOffsets[i]);
                    dos.writeInt(b.data.length);
                    dos.writeInt(b.entryCount);
                }

                // Write Blocks
                for (BlockRecord b : blocks) {
                    dos.write(b.data);
                }

                // Write String Pool
                dos.write(poolBytes);

                dos.flush();
                fos.getFD().sync();
            }

            // Atomic rename
            if (!tempFile.renameTo(outputFile)) {
                if (outputFile.exists() && outputFile.delete()) {
                    tempFile.renameTo(outputFile);
                }
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}

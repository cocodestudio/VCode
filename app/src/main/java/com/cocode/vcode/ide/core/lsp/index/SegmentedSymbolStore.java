package com.cocode.vcode.ide.core.lsp.index;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Segmented symbol store for workspace indexing with on-disk binary segments
 * and bounded LRU block caching.
 */
public class SegmentedSymbolStore {

    private final File projectRoot;
    private final File cacheDir;
    private final File catalogFile;
    private final BoundedBlockCache blockCache;
    private final IndexCatalog catalog;
    private final Map<Integer, SegmentReader> segmentReaders = new HashMap<>();
    private final List<IndexingProgressListener> listeners = new CopyOnWriteArrayList<>();
    private final Object indexLock = new Object();

    public interface SymbolExtractor {
        List<IndexSymbolEntry> extractSymbols(File file, String relativePath, int fileId);
    }

    public SegmentedSymbolStore(File projectRoot) {
        this(projectRoot, new BoundedBlockCache());
    }

    public SegmentedSymbolStore(File projectRoot, BoundedBlockCache blockCache) {
        this.projectRoot = projectRoot;
        this.cacheDir = new File(projectRoot, ".vcode/cache/symbols");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        this.catalogFile = new File(cacheDir, "catalog.bin");
        this.blockCache = blockCache != null ? blockCache : new BoundedBlockCache();
        this.catalog = IndexCatalog.load(catalogFile);
        openExistingSegments();
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    private void openExistingSegments() {
        synchronized (indexLock) {
            // 1. Clean up stray temporary files (.tmp) left behind by aborted writes or crashes
            File[] tmpFiles = cacheDir.listFiles((dir, name) -> name.endsWith(".tmp"));
            if (tmpFiles != null) {
                for (File tmp : tmpFiles) {
                    tmp.delete();
                }
            }
            File parentCache = cacheDir.getParentFile();
            if (parentCache != null && parentCache.exists()) {
                File[] parentTmp = parentCache.listFiles((dir, name) -> name.endsWith(".tmp"));
                if (parentTmp != null) {
                    for (File tmp : parentTmp) {
                        tmp.delete();
                    }
                }
            }

            File[] files = cacheDir.listFiles((dir, name) -> name.startsWith("seg_") && name.endsWith(".bin"));
            if (files == null) return;

            Set<Integer> activeSegmentIds = catalog.getTrackedSegmentIds();

            // 2. If the catalog tracks no files (e.g. empty or reset catalog), any existing seg_*.bin is an orphan
            if (activeSegmentIds.isEmpty()) {
                for (File f : files) {
                    f.delete();
                }
                return;
            }

            // 3. Purge orphan segments not referenced by the catalog, and open active segments
            for (File f : files) {
                int segId = parseSegmentId(f.getName());
                if (segId <= 0 || !activeSegmentIds.contains(segId)) {
                    f.delete(); // Delete orphan segment dumped or abandoned by prior compaction/crash
                    continue;
                }
                try {
                    SegmentReader reader = new SegmentReader(f, blockCache);
                    segmentReaders.put(reader.getSegmentId(), reader);
                } catch (IOException e) {
                    // Segment corrupted on disk; delete corrupt file
                    f.delete();
                }
            }

            // 4. If any tracked files in catalog reference missing/corrupt segments, prune them from catalog
            boolean catalogModified = false;
            for (String tracked : new ArrayList<>(catalog.getTrackedFiles())) {
                IndexCatalog.FileFingerprint fp = catalog.getFingerprint(tracked);
                if (fp != null && !segmentReaders.containsKey(fp.segmentId)) {
                    catalog.removeFile(tracked);
                    catalogModified = true;
                }
            }
            if (catalogModified) {
                try {
                    catalog.save(catalogFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static int parseSegmentId(String name) {
        if (name == null || !name.startsWith("seg_") || !name.endsWith(".bin")) return -1;
        try {
            String numStr = name.substring(4, name.length() - 4);
            return Integer.parseInt(numStr);
        } catch (Exception e) {
            return -1;
        }
    }

    public void addProgressListener(IndexingProgressListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeProgressListener(IndexingProgressListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void notifyIndexingStarted(int totalFiles) {
        for (IndexingProgressListener l : listeners) {
            try {
                l.onIndexingStarted(totalFiles);
            } catch (Exception ignored) {
            }
        }
    }

    public void notifyIndexingProgress(int processed, int total, String fileName) {
        for (IndexingProgressListener l : listeners) {
            try {
                l.onIndexingProgress(processed, total, fileName);
            } catch (Exception ignored) {
            }
        }
    }

    public void notifyIndexingCompleted(int totalSymbols, long durationMs) {
        for (IndexingProgressListener l : listeners) {
            try {
                l.onIndexingCompleted(totalSymbols, durationMs);
            } catch (Exception ignored) {
            }
        }
    }

    public void notifyIndexingError(Throwable error) {
        for (IndexingProgressListener l : listeners) {
            try {
                l.onIndexingError(error);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Checks if the index for candidate files is already completely up-to-date.
     * Returns true only if the project was already indexed and no files were modified, added, or deleted.
     */
    public boolean isIndexUpToDate(File projectRoot, List<File> candidateFiles) {
        synchronized (indexLock) {
            if (candidateFiles == null || candidateFiles.isEmpty()) {
                return true;
            }
            if (segmentReaders.isEmpty() || catalog.getTrackedFiles().isEmpty()) {
                return false; // Not indexed yet
            }
            if (catalog.getTrackedFiles().size() != candidateFiles.size()) {
                return false; // Files added or deleted
            }
            for (File file : candidateFiles) {
                String relPath = getRelativePath(projectRoot, file);
                if (!catalog.isFileUpToDate(file, relPath)) {
                    return false; // File modified or new
                }
            }
            return true;
        }
    }

    /**
     * Incrementally indexes candidate files using the provided symbol extractor.
     */
    public void indexFiles(File projectRoot, List<File> candidateFiles, SymbolExtractor extractor) {
        indexFiles(projectRoot, candidateFiles, extractor, false);
    }

    /**
     * Incrementally indexes candidate files using the provided symbol extractor, optionally suppressing UI progress notifications.
     */
    public void indexFiles(File projectRoot, List<File> candidateFiles, SymbolExtractor extractor, boolean silent) {
        if (candidateFiles == null || extractor == null) return;
        long startTime = System.currentTimeMillis();

        List<File> dirtyFiles = new ArrayList<>();
        Set<String> currentRelPaths = new HashSet<>();
        for (File file : candidateFiles) {
            String relPath = getRelativePath(projectRoot, file);
            currentRelPaths.add(relPath);
            if (!catalog.isFileUpToDate(file, relPath)) {
                dirtyFiles.add(file);
            }
        }

        // Prune deleted files from the catalog if candidates have changed
        boolean catalogPruned = false;
        synchronized (indexLock) {
            for (String tracked : new ArrayList<>(catalog.getTrackedFiles())) {
                if (!currentRelPaths.contains(tracked)) {
                    catalog.removeFile(tracked);
                    catalogPruned = true;
                }
            }
            if (catalogPruned && dirtyFiles.isEmpty()) {
                try {
                    catalog.save(catalogFile);
                } catch (IOException ignored) {
                }
            }
        }

        // If no files are dirty, the index is already completely up to date. Do not show progress.
        if (dirtyFiles.isEmpty()) {
            return;
        }

        int totalDirty = dirtyFiles.size();
        if (!silent) {
            notifyIndexingStarted(totalDirty);
        }

        List<IndexSymbolEntry> dirtyEntries = new ArrayList<>();
        Map<String, File> processedDirtyFiles = new HashMap<>();
        int processedCount = 0;

        try {
            for (File file : dirtyFiles) {
                processedCount++;
                String relPath = getRelativePath(projectRoot, file);
                if (!silent) {
                    notifyIndexingProgress(processedCount, totalDirty, file.getName());
                }

                int fileId = catalog.getOrAssignFileId(relPath);
                List<IndexSymbolEntry> symbols = extractor.extractSymbols(file, relPath, fileId);
                if (symbols != null && !symbols.isEmpty()) {
                    dirtyEntries.addAll(symbols);
                }
                processedDirtyFiles.put(relPath, file);
            }

            if (!processedDirtyFiles.isEmpty()) {
                synchronized (indexLock) {
                    int segId = catalog.allocateNextSegmentId();
                    File segFile = new File(cacheDir, String.format(Locale.US, "seg_%04d.bin", segId));

                    SegmentWriter.writeSegment(segFile, segId, dirtyEntries);

                    SegmentReader newReader = new SegmentReader(segFile, blockCache);
                    segmentReaders.put(segId, newReader);

                    for (Map.Entry<String, File> entry : processedDirtyFiles.entrySet()) {
                        catalog.recordFile(entry.getKey(), entry.getValue(), segId);
                    }
                    catalog.save(catalogFile);

                    // If more than 4 segments, compact into a single unified segment
                    if (segmentReaders.size() > 4) {
                        compactSegments();
                    }
                }
            }

            if (!silent) {
                int totalSymbols = getTotalIndexedSymbolsCount();
                long duration = System.currentTimeMillis() - startTime;
                notifyIndexingCompleted(totalSymbols, duration);
            }
        } catch (Throwable t) {
            if (!silent) {
                notifyIndexingError(t);
            }
        }
    }

    /**
     * Silently indexes a single modified file in the background without firing any progress events.
     */
    public void indexSingleFileSilently(File projectRoot, File file, SymbolExtractor extractor) {
        if (projectRoot == null || file == null || extractor == null) return;
        String relPath = getRelativePath(projectRoot, file);
        int fileId = catalog.getOrAssignFileId(relPath);
        List<IndexSymbolEntry> symbols = extractor.extractSymbols(file, relPath, fileId);
        if (symbols == null) symbols = Collections.emptyList();

        synchronized (indexLock) {
            int segId = catalog.allocateNextSegmentId();
            File segFile = new File(cacheDir, String.format(Locale.US, "seg_%04d.bin", segId));
            try {
                SegmentWriter.writeSegment(segFile, segId, symbols);
                SegmentReader newReader = new SegmentReader(segFile, blockCache);
                segmentReaders.put(segId, newReader);
                catalog.recordFile(relPath, file, segId);
                catalog.save(catalogFile);
                if (segmentReaders.size() > 8) {
                    compactSegments();
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Merges multiple segment files into a single consolidated segment.
     */
    public void compactSegments() {
        synchronized (indexLock) {
            if (segmentReaders.size() <= 1) return;

            Map<String, IndexSymbolEntry> mergedMap = new LinkedHashMap<>();
            List<SegmentReader> oldReaders = new ArrayList<>(segmentReaders.values());

            for (SegmentReader reader : oldReaders) {
                List<IndexSymbolEntry> entries = reader.readAllEntries();
                for (IndexSymbolEntry e : entries) {
                    mergedMap.put(e.getKey(), e);
                }
            }

            int consolidatedSegId = catalog.allocateNextSegmentId();
            File consolidatedFile = new File(cacheDir, String.format(Locale.US, "seg_%04d.bin", consolidatedSegId));

            try {
                SegmentWriter.writeSegment(consolidatedFile, consolidatedSegId, new ArrayList<>(mergedMap.values()));

                // Close and remove old readers
                for (SegmentReader r : oldReaders) {
                    r.close();
                }
                segmentReaders.clear();

                // Delete old segment files
                File[] oldFiles = cacheDir.listFiles((dir, name) -> name.startsWith("seg_") && name.endsWith(".bin")
                        && !name.equals(consolidatedFile.getName()));
                if (oldFiles != null) {
                    for (File f : oldFiles) {
                        f.delete();
                    }
                }

                // Register consolidated reader
                SegmentReader consolidatedReader = new SegmentReader(consolidatedFile, blockCache);
                segmentReaders.put(consolidatedSegId, consolidatedReader);

                // Update catalog segment IDs
                for (String path : catalog.getTrackedFiles()) {
                    File file = new File(projectRoot, path);
                    if (file.exists()) {
                        catalog.recordFile(path, file, consolidatedSegId);
                    }
                }
                catalog.save(catalogFile);
            } catch (IOException ignored) {
            }
        }
    }

    public List<IndexSymbolEntry> query(SegmentSearchQuery query) {
        if (query == null) return Collections.emptyList();
        synchronized (indexLock) {
            Map<String, IndexSymbolEntry> deduplicated = new LinkedHashMap<>();
            int limit = query.getLimit() > 0 ? query.getLimit() : Integer.MAX_VALUE;

            for (SegmentReader reader : segmentReaders.values()) {
                List<IndexSymbolEntry> entries;
                if (query.isExact()) {
                    entries = reader.queryExact(query.getQuery());
                } else {
                    entries = reader.queryPrefix(query.getQuery(), limit);
                }

                for (IndexSymbolEntry entry : entries) {
                    if (query.matchesKind(entry.getKind())) {
                        deduplicated.put(entry.getKey(), entry);
                        if (deduplicated.size() >= limit) {
                            return new ArrayList<>(deduplicated.values());
                        }
                    }
                }
            }
            return new ArrayList<>(deduplicated.values());
        }
    }

    public List<CompletionItem> queryCompletions(SegmentSearchQuery query, String sourceUri) {
        List<IndexSymbolEntry> entries = query(query);
        List<CompletionItem> items = new ArrayList<>(entries.size());
        for (IndexSymbolEntry entry : entries) {
            items.add(entry.toCompletionItem(sourceUri));
        }
        return items;
    }

    public int getTotalIndexedSymbolsCount() {
        synchronized (indexLock) {
            int count = 0;
            for (SegmentReader r : segmentReaders.values()) {
                count += r.getTotalEntryCount();
            }
            return count;
        }
    }

    public void invalidateFile(String relativePath) {
        synchronized (indexLock) {
            catalog.removeFile(relativePath);
            try {
                catalog.save(catalogFile);
            } catch (IOException ignored) {
            }
        }
    }

    public void close() {
        synchronized (indexLock) {
            for (SegmentReader r : segmentReaders.values()) {
                r.close();
            }
            segmentReaders.clear();
            blockCache.clear();
        }
    }

    public static String getRelativePath(File base, File file) {
        if (base == null || file == null) return file != null ? file.getName() : "";
        String basePath = base.getAbsolutePath().replace('\\', '/');
        String filePath = file.getAbsolutePath().replace('\\', '/');
        if (filePath.equalsIgnoreCase(basePath)) {
            return "";
        }
        if (filePath.toLowerCase(Locale.US).startsWith(basePath.toLowerCase(Locale.US))) {
            String rel = filePath.substring(basePath.length());
            while (rel.startsWith("/")) {
                rel = rel.substring(1);
            }
            return rel;
        }
        try {
            String canonBase = base.getCanonicalPath().replace('\\', '/');
            String canonFile = file.getCanonicalPath().replace('\\', '/');
            if (canonFile.toLowerCase(Locale.US).startsWith(canonBase.toLowerCase(Locale.US))) {
                String rel = canonFile.substring(canonBase.length());
                while (rel.startsWith("/")) {
                    rel = rel.substring(1);
                }
                return rel;
            }
        } catch (Exception ignored) {
        }
        return file.getName();
    }
}

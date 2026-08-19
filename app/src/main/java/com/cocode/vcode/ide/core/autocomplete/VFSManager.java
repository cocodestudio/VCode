package com.cocode.vcode.ide.core.autocomplete;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.data.repository.ProjectRepository;

/**
 * Virtual File System (VFS) cache to allow O(1) directory listing for path suggestions.
 * Prevents hitting the slow physical disk (I/O) during fast typing.
 */
public class VFSManager {

    private static VFSManager instance;

    // Maps absolute directory path -> list of child files/folders
    private final Map<String, List<File>> directoryCache = new HashMap<>();
    private String projectRoot = null;

    private VFSManager() {
    }

    public static synchronized VFSManager getInstance() {
        if (instance == null) {
            instance = new VFSManager();
        }
        return instance;
    }

    /**
     * Initializes the cache asynchronously for a given project root.
     */
    public void buildCache(String rootPath) {
        if (rootPath == null || rootPath.equals(projectRoot)) return;

        projectRoot = rootPath;
        ExecutorProvider.getInstance().runOnIo(() -> {
            directoryCache.clear();
            File root = new File(rootPath);
            if (root.exists() && root.isDirectory()) {
                indexDirectoryRecursively(root);
            }
        });
    }

    private void indexDirectoryRecursively(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return;

        List<File> cachedList = new ArrayList<>();
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith("."))
                continue; // Skip hidden/git and meta files
            cachedList.add(child);

            // Also add sub-directories to the queue
            if (child.isDirectory()) {
                indexDirectoryRecursively(child);
            }
        }
        directoryCache.put(dir.getAbsolutePath(), cachedList);
    }

    /**
     * Retrieves files from the RAM cache instead of querying the disk.
     */
    public List<File> listCachedFiles(File directory) {
        if (directory == null) return null;

        // If not in cache, fallback to disk (and maybe add it to cache)
        if (!directoryCache.containsKey(directory.getAbsolutePath())) {
            File[] diskFiles = directory.listFiles();
            if (diskFiles != null) {
                List<File> list = new ArrayList<>();
                for (File f : diskFiles) {
                    String name = f.getName();
                    if (!name.startsWith(".")) {
                        list.add(f);
                    }
                }
                directoryCache.put(directory.getAbsolutePath(), list);
                return list;
            }
            return null;
        }

        return directoryCache.get(directory.getAbsolutePath());
    }

    /**
     * Invalidates the cache for a specific directory (e.g. when a file is created/deleted).
     */
    public void invalidateDirectory(File directory) {
        if (directory != null) {
            directoryCache.remove(directory.getAbsolutePath());
        }
    }
}

package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Virtual File System (VFS) cache to allow O(1) directory listing for path suggestions.
 * Prevents hitting the slow physical disk (I/O) during fast typing.
 *
 * <p>Uses {@link ConcurrentHashMap} and thread-safe immutable lists for concurrent access across
 * background IO indexing and foreground completion queries.
 */
public class VFSManager {

    private static final int MAX_CACHE_ENTRIES = 10_000;
    private static VFSManager instance;
    // Maps absolute directory path -> list of child files/folders
    private final Map<String, List<File>> directoryCache = new ConcurrentHashMap<>();
    private final Map<String, List<File>> allDirectoryCache = new ConcurrentHashMap<>();
    private volatile String projectRoot = null;

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
        if (directoryCache.size() >= MAX_CACHE_ENTRIES) return;
        File[] children = dir.listFiles();
        if (children == null) return;

        List<File> cachedList = new ArrayList<>();
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith(".") || name.equals("build")
                    || name.equals("dist") || name.equals("out") || name.equals("vendor")
                    || name.equals(".next") || name.equals(".nuxt"))
                continue; // Skip hidden/build/dist

            if (name.equals("node_modules")) {
                // Scoped indexing for node_modules: index top-level package directories and entry points
                cachedList.add(child);
                indexNodeModules(child);
                continue;
            }

            cachedList.add(child);

            // Also add sub-directories to the queue
            if (child.isDirectory() && directoryCache.size() < MAX_CACHE_ENTRIES) {
                indexDirectoryRecursively(child);
            }
        }
        directoryCache.put(dir.getAbsolutePath(), Collections.unmodifiableList(cachedList));
    }

    private void indexNodeModules(File nodeModulesDir) {
        if (directoryCache.size() >= MAX_CACHE_ENTRIES) return;
        File[] packages = nodeModulesDir.listFiles();
        if (packages == null) return;

        List<File> topLevelPkgs = new ArrayList<>();
        for (File pkg : packages) {
            String pkgName = pkg.getName();
            if (pkgName.startsWith(".")) continue;

            if (pkgName.startsWith("@") && pkg.isDirectory()) {
                // Scoped package namespace like @types
                topLevelPkgs.add(pkg);
                File[] scoped = pkg.listFiles();
                if (scoped != null) {
                    List<File> scopedList = new ArrayList<>();
                    for (File s : scoped) {
                        if (!s.getName().startsWith(".")) {
                            scopedList.add(s);
                            indexPackageEntries(s);
                        }
                    }
                    directoryCache.put(pkg.getAbsolutePath(), Collections.unmodifiableList(scopedList));
                }
            } else if (pkg.isDirectory()) {
                topLevelPkgs.add(pkg);
                indexPackageEntries(pkg);
            }
        }
        directoryCache.put(nodeModulesDir.getAbsolutePath(), Collections.unmodifiableList(topLevelPkgs));
    }

    private void indexPackageEntries(File pkgDir) {
        if (directoryCache.size() >= MAX_CACHE_ENTRIES) return;
        File[] files = pkgDir.listFiles();
        if (files == null) return;

        List<File> entries = new ArrayList<>();
        for (File f : files) {
            String name = f.getName();
            // Cache entry points, typings, and manifests
            if (name.equals("package.json") || name.endsWith(".d.ts")
                    || name.equals("index.js") || name.equals("main.js")
                    || name.equals("lib") || name.equals("dist")) {
                entries.add(f);
            }
        }
        directoryCache.put(pkgDir.getAbsolutePath(), Collections.unmodifiableList(entries));
    }

    /**
     * Retrieves files from the RAM cache instead of querying the disk.
     */
    public List<File> listCachedFiles(File directory) {
        return listCachedFiles(directory, false);
    }

    /**
     * Retrieves files from the RAM cache with optional hidden files inclusion (e.g. for .gitignore).
     */
    public List<File> listCachedFiles(File directory, boolean includeHidden) {
        if (directory == null) return null;

        if (!includeHidden) {
            List<File> cached = directoryCache.get(directory.getAbsolutePath());
            if (cached != null) {
                return cached;
            }

            File[] diskFiles = directory.listFiles();
            if (diskFiles != null) {
                List<File> list = new ArrayList<>();
                for (File f : diskFiles) {
                    String name = f.getName();
                    if (!name.startsWith(".")) {
                        list.add(f);
                    }
                }
                List<File> safeList = Collections.unmodifiableList(list);
                directoryCache.put(directory.getAbsolutePath(), safeList);
                return safeList;
            }
            return null;
        }

        List<File> cached = allDirectoryCache.get(directory.getAbsolutePath());
        if (cached != null) {
            return cached;
        }

        File[] diskFiles = directory.listFiles();
        if (diskFiles != null) {
            List<File> list = new ArrayList<>();
            for (File f : diskFiles) {
                String name = f.getName();
                if (!name.equals(".") && !name.equals("..") 
                        && !name.equalsIgnoreCase(".git")) {
                    list.add(f);
                }
            }
            List<File> safeList = Collections.unmodifiableList(list);
            allDirectoryCache.put(directory.getAbsolutePath(), safeList);
            return safeList;
        }
        return null;
    }

    /**
     * Invalidates the cache for a specific directory (e.g. when a file is created/deleted).
     */
    public void invalidateDirectory(File directory) {
        if (directory != null) {
            directoryCache.remove(directory.getAbsolutePath());
            allDirectoryCache.remove(directory.getAbsolutePath());
        }
    }

    /**
     * Clears all cached directory listings and resets the project root.
     */
    public void clear() {
        directoryCache.clear();
        allDirectoryCache.clear();
        projectRoot = null;
    }
}

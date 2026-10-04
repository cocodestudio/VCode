package com.cocode.vcode.ide.data.npm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.core.autocomplete.types.TypeDefinitionIndexer;
import com.cocode.vcode.ide.data.npm.extractor.NpmTarballExtractor;
import com.cocode.vcode.ide.data.npm.model.NpmPackageInfo;
import com.cocode.vcode.ide.data.npm.registry.NpmRegistryClient;
import com.cocode.vcode.ide.data.npm.safeguard.StorageSafeguardManager;
import com.cocode.vcode.ide.data.npm.sync.PackageJsonSync;
import com.cocode.vcode.ide.data.npm.sync.PackageLockJsonSync;
import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * High-level orchestration service for the Zero-Node Package Manager.
 * Coordinates package search, tarball downloads, extraction, storage safeguards,
 * package.json synchronization, and targeted type indexing.
 */
public class PackageManagerService {

    public interface PackageOperationCallback {
        void onSuccess(@NonNull String message);
        void onError(@NonNull String error);
    }

    public interface PackageListCallback {
        void onResult(@NonNull List<NpmPackageInfo> packages);
        void onError(@NonNull String error);
    }

    public interface PackageProgressCallback {
        void onProgress(@NonNull String status, int percent);
    }

    public interface BatchOperationCallback {
        void onProgress(int current, int total, @NonNull String currentPackage, @NonNull String message, int percent);
        void onComplete(int successCount, int failCount, @NonNull List<String> errors);
    }

    private static PackageManagerService instance;

    private PackageManagerService() {
    }

    public static synchronized PackageManagerService getInstance() {
        if (instance == null) {
            instance = new PackageManagerService();
        }
        return instance;
    }

    /**
     * Searches npm registry and annotates results with local installation statuses.
     */
    public void searchPackages(@NonNull File projectRoot,
                               @NonNull String query,
                               @NonNull PackageListCallback callback) {
        ExecutorProvider.getInstance().runOnPackage(() -> {
            try {
                List<NpmPackageInfo> results = NpmRegistryClient.searchPackages(query, 25);
                Map<String, String> deps = PackageJsonSync.getDependencies(projectRoot);
                Map<String, String> devDeps = PackageJsonSync.getDevDependencies(projectRoot);

                for (NpmPackageInfo info : results) {
                    String name = info.getName();
                    if (deps.containsKey(name)) {
                        info.setInstalledVersion(deps.get(name));
                        info.setDev(false);
                    } else if (devDeps.containsKey(name)) {
                        info.setInstalledVersion(devDeps.get(name));
                        info.setDev(true);
                    }
                }

                ExecutorProvider.getInstance().runOnMain(() -> callback.onResult(results));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() ->
                        callback.onError(e.getMessage() != null ? e.getMessage() : "Search failed"));
            }
        });
    }

    /**
     * Retrieves all installed packages parsed from package.json and node_modules.
     */
    public void getInstalledPackages(@NonNull File projectRoot,
                                     @NonNull PackageListCallback callback) {
        ExecutorProvider.getInstance().runOnPackage(() -> {
            try {
                List<NpmPackageInfo> installedList = new ArrayList<>();
                Map<String, String> deps = PackageJsonSync.getDependencies(projectRoot);
                Map<String, String> devDeps = PackageJsonSync.getDevDependencies(projectRoot);

                for (Map.Entry<String, String> entry : deps.entrySet()) {
                    String name = entry.getKey();
                    String reqVer = entry.getValue();
                    String actualVer = PackageJsonSync.getInstalledPackageVersion(projectRoot, name);

                    NpmPackageInfo info = new NpmPackageInfo(name, actualVer != null ? actualVer : reqVer);
                    info.setInstalledVersion(actualVer != null ? actualVer : reqVer);
                    info.setDev(false);
                    installedList.add(info);
                }

                for (Map.Entry<String, String> entry : devDeps.entrySet()) {
                    String name = entry.getKey();
                    String reqVer = entry.getValue();
                    String actualVer = PackageJsonSync.getInstalledPackageVersion(projectRoot, name);

                    NpmPackageInfo info = new NpmPackageInfo(name, actualVer != null ? actualVer : reqVer);
                    info.setInstalledVersion(actualVer != null ? actualVer : reqVer);
                    info.setDev(true);
                    installedList.add(info);
                }

                ExecutorProvider.getInstance().runOnMain(() -> callback.onResult(installedList));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() ->
                        callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to load dependencies"));
            }
        });
    }

    /**
     * Installs or updates an npm package asynchronously.
     */
    public void installPackage(@NonNull File projectRoot,
                               @NonNull String packageName,
                               @Nullable String targetVersion,
                               boolean isDev,
                               @Nullable PackageProgressCallback progress,
                               @NonNull PackageOperationCallback callback) {
        ExecutorProvider.getInstance().runOnPackage(() -> {
            try {
                String resolvedVersion = installPackageSynchronously(projectRoot, packageName, targetVersion, isDev, progress);
                ExecutorProvider.getInstance().runOnMain(() ->
                        callback.onSuccess("Successfully installed " + packageName + "@" + resolvedVersion));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() ->
                        callback.onError(e.getMessage() != null ? e.getMessage() : "Installation failed"));
            }
        });
    }

    /**
     * Installs multiple packages sequentially in the background with live batch progress.
     */
    public void installPackagesBatch(@NonNull File projectRoot,
                                     @NonNull List<NpmPackageInfo> packagesToInstall,
                                     boolean isDev,
                                     @NonNull BatchOperationCallback callback) {
        ExecutorProvider.getInstance().runOnPackage(() -> {
            int total = packagesToInstall.size();
            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                NpmPackageInfo pkg = packagesToInstall.get(i);
                final int currentIdx = i + 1;
                final int overallPct = (int) (((currentIdx - 1) * 100.0) / total);

                ExecutorProvider.getInstance().runOnMain(() ->
                        callback.onProgress(currentIdx, total, pkg.getName(),
                                "Installing " + pkg.getName() + " (" + currentIdx + "/" + total + ")…", overallPct));

                try {
                    installPackageSynchronously(projectRoot, pkg.getName(), pkg.getVersion(), isDev, (msg, pct) -> {
                        int currentPackagePct = (int) (((currentIdx - 1) * 100.0 + pct) / total);
                        ExecutorProvider.getInstance().runOnMain(() ->
                                callback.onProgress(currentIdx, total, pkg.getName(), msg, currentPackagePct));
                    });
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    errors.add(pkg.getName() + ": " + (e.getMessage() != null ? e.getMessage() : "Error"));
                }
            }

            final int finalSuccess = successCount;
            final int finalFail = failCount;
            ExecutorProvider.getInstance().runOnMain(() ->
                    callback.onComplete(finalSuccess, finalFail, errors));
        });
    }

    /**
     * Synchronous package installer for worker threads.
     */
    public String installPackageSynchronously(@NonNull File projectRoot,
                                              @NonNull String packageName,
                                              @Nullable String targetVersion,
                                              boolean isDev,
                                              @Nullable PackageProgressCallback progress) throws Exception {
        notifyProgress(progress, "Applying storage safeguards…", 10);
        StorageSafeguardManager.ensureSafeguards(projectRoot);

        notifyProgress(progress, "Resolving metadata for " + packageName + "…", 20);
        NpmPackageInfo metadata = NpmRegistryClient.fetchPackageMetadata(packageName);
        String resolvedVersion = (targetVersion != null && !targetVersion.isEmpty())
                ? targetVersion : metadata.getVersion();
        String tarballUrl = metadata.getTarballUrl();

        if (tarballUrl == null || tarballUrl.isEmpty()) {
            throw new Exception("Could not resolve download tarball for " + packageName);
        }

        notifyProgress(progress, "Downloading " + packageName + "@" + resolvedVersion + "…", 40);
        File nodeModules = new File(projectRoot, StorageSafeguardManager.NODE_MODULES);
        File pkgDest = new File(nodeModules, packageName);
        if (pkgDest.exists()) {
            deleteRecursively(pkgDest);
        }
        pkgDest.mkdirs();

        NpmTarballExtractor.extractFromUrl(tarballUrl, pkgDest, (bytesRead, totalBytes) -> {
            if (totalBytes > 0) {
                int pct = 40 + (int) ((bytesRead * 40) / totalBytes);
                notifyProgress(progress, "Extracting files (" + pct + "%)…", pct);
            }
        });

        StorageSafeguardManager.ensureNomediaShield(nodeModules);

        notifyProgress(progress, "Updating package.json & lockfile…", 85);
        PackageJsonSync.addDependency(projectRoot, packageName, resolvedVersion, isDev);
        PackageLockJsonSync.recordPackage(projectRoot, packageName, resolvedVersion, tarballUrl, metadata.getIntegrity(), metadata.getLicense(), isDev);

        // Automatic Type Acquisition (ATA): Check if package has bundled types.
        File dtsFile = TypeDefinitionIndexer.resolveTypeDefinitionFile(projectRoot, packageName);
        if (dtsFile == null && !packageName.startsWith("@types/")) {
            try {
                notifyProgress(progress, "Checking TypeScript definitions…", 90);
                String typesPkgName = "@types/" + (packageName.startsWith("@") && packageName.contains("/") ?
                        packageName.substring(1).replace("/", "__") : packageName);
                NpmPackageInfo typesInfo = NpmRegistryClient.fetchPackageMetadata(typesPkgName);
                if (typesInfo != null && typesInfo.getTarballUrl() != null && !typesInfo.getTarballUrl().isEmpty()) {
                    notifyProgress(progress, "Downloading types for " + packageName + "…", 92);
                    File atTypesDir = new File(nodeModules, typesPkgName);
                    if (atTypesDir.exists()) {
                        deleteRecursively(atTypesDir);
                    }
                    atTypesDir.mkdirs();
                    NpmTarballExtractor.extractFromUrl(typesInfo.getTarballUrl(), atTypesDir, null);
                    PackageLockJsonSync.recordPackage(projectRoot, typesPkgName, typesInfo.getVersion(), typesInfo.getTarballUrl(), typesInfo.getIntegrity(), typesInfo.getLicense(), true);
                }
            } catch (Exception ignored) {
                // ATA is best-effort; failure falls back to JS package indexing
            }
        }

        notifyProgress(progress, "Indexing type declarations…", 95);
        TypeDefinitionIndexer.indexDependency(projectRoot, packageName);

        notifyProgress(progress, "Complete!", 100);
        return resolvedVersion;
    }

    /**
     * Uninstalls a package by removing its folder from node_modules, updating package.json,
     * and purging its symbols from ProjectSymbolIndex.
     */
    public void uninstallPackage(@NonNull File projectRoot,
                                 @NonNull String packageName,
                                 @NonNull PackageOperationCallback callback) {
        ExecutorProvider.getInstance().runOnPackage(() -> {
            try {
                uninstallPackageSynchronously(projectRoot, packageName);
                ExecutorProvider.getInstance().runOnMain(() ->
                        callback.onSuccess("Successfully uninstalled " + packageName));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() ->
                        callback.onError(e.getMessage() != null ? e.getMessage() : "Uninstall failed"));
            }
        });
    }

    /**
     * Uninstalls multiple packages sequentially in the background with live batch progress.
     */
    public void uninstallPackagesBatch(@NonNull File projectRoot,
                                       @NonNull List<NpmPackageInfo> packagesToUninstall,
                                       @NonNull BatchOperationCallback callback) {
        ExecutorProvider.getInstance().runOnPackage(() -> {
            int total = packagesToUninstall.size();
            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                NpmPackageInfo pkg = packagesToUninstall.get(i);
                final int currentIdx = i + 1;
                final int overallPct = (int) (((currentIdx - 1) * 100.0) / total);

                ExecutorProvider.getInstance().runOnMain(() ->
                        callback.onProgress(currentIdx, total, pkg.getName(),
                                "Uninstalling " + pkg.getName() + " (" + currentIdx + "/" + total + ")…", overallPct));

                try {
                    uninstallPackageSynchronously(projectRoot, pkg.getName());
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    errors.add(pkg.getName() + ": " + (e.getMessage() != null ? e.getMessage() : "Error"));
                }
            }

            final int finalSuccess = successCount;
            final int finalFail = failCount;
            ExecutorProvider.getInstance().runOnMain(() ->
                    callback.onComplete(finalSuccess, finalFail, errors));
        });
    }

    public void uninstallPackageSynchronously(@NonNull File projectRoot, @NonNull String packageName) throws Exception {
        File pkgDir = new File(projectRoot, StorageSafeguardManager.NODE_MODULES + "/" + packageName);
        if (pkgDir.exists()) {
            deleteRecursively(pkgDir);
        }
        PackageJsonSync.removeDependency(projectRoot, packageName);
        PackageLockJsonSync.removePackage(projectRoot, packageName);
        ProjectSymbolIndex.getInstance().unregisterDependencySymbols(packageName);
    }

    private void notifyProgress(@Nullable PackageProgressCallback callback, @NonNull String status, int percent) {
        if (callback != null) {
            ExecutorProvider.getInstance().runOnMain(() -> callback.onProgress(status, percent));
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}

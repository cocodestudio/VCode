package com.cocode.vcode.ide.data.npm.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.utils.FileUtils;

import org.json.JSONObject;

import java.io.File;

/**
 * Generates and synchronizes npm-compliant package-lock.json (lockfileVersion 3)
 * maintaining exact package versions, tarball download URLs, integrity hashes, and licenses.
 */
public final class PackageLockJsonSync {

    public static final String PACKAGE_LOCK_JSON = "package-lock.json";
    public static final int LOCKFILE_VERSION = 3;

    private PackageLockJsonSync() {
    }

    /**
     * Reads package-lock.json from the project root.
     */
    @Nullable
    public static JSONObject readPackageLockJson(@Nullable File projectRoot) {
        if (projectRoot == null) return null;
        File file = new File(projectRoot, PACKAGE_LOCK_JSON);
        if (!file.exists()) return null;

        try {
            String content = FileUtils.readFile(file);
            if (content == null || content.trim().isEmpty()) return null;
            return new JSONObject(content);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ensures that a valid package-lock.json (lockfileVersion 3) structure exists in the project root.
     */
    @NonNull
    public static JSONObject ensurePackageLockJson(@NonNull File projectRoot) {
        JSONObject json = readPackageLockJson(projectRoot);
        if (json != null) return json;

        String rawProjectName = ProjectRepository.getProjectName(projectRoot);
        String safeName = rawProjectName.trim().toLowerCase()
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("^-+|-+$", "");
        if (safeName.isEmpty()) safeName = "vcode-project";

        json = new JSONObject();
        try {
            json.put("name", safeName);
            json.put("version", "1.0.0");
            json.put("lockfileVersion", LOCKFILE_VERSION);
            json.put("requires", true);

            JSONObject packages = new JSONObject();
            JSONObject rootPkg = new JSONObject();
            rootPkg.put("name", safeName);
            rootPkg.put("version", "1.0.0");
            rootPkg.put("dependencies", new JSONObject());
            rootPkg.put("devDependencies", new JSONObject());
            packages.put("", rootPkg);

            json.put("packages", packages);

            File file = new File(projectRoot, PACKAGE_LOCK_JSON);
            FileUtils.writeFile(file, json.toString(2));
        } catch (Exception ignored) {
        }
        return json;
    }

    /**
     * Records or updates an installed package entry in package-lock.json.
     *
     * @param projectRoot root directory of the project
     * @param packageName npm package name (e.g. "axios")
     * @param version     exact version installed (e.g. "1.7.9")
     * @param tarballUrl  resolved tarball URL from registry
     * @param integrity   sha512 integrity hash from registry dist
     * @param license     package license
     * @param isDev       whether installed as a devDependency
     * @return true if successfully persisted
     */
    public static boolean recordPackage(@NonNull File projectRoot,
                                        @NonNull String packageName,
                                        @NonNull String version,
                                        @Nullable String tarballUrl,
                                        @Nullable String integrity,
                                        @Nullable String license,
                                        boolean isDev) {
        JSONObject lockJson = readPackageLockJson(projectRoot);
        if (lockJson == null) {
            lockJson = ensurePackageLockJson(projectRoot);
        }

        try {
            JSONObject packages = lockJson.optJSONObject("packages");
            if (packages == null) {
                packages = new JSONObject();
                lockJson.put("packages", packages);
            }

            // 1. Update root project descriptor inside packages[""]
            JSONObject rootPkg = packages.optJSONObject("");
            if (rootPkg == null) {
                rootPkg = new JSONObject();
                rootPkg.put("name", lockJson.optString("name", "vcode-project"));
                rootPkg.put("version", lockJson.optString("version", "1.0.0"));
                packages.put("", rootPkg);
            }

            String semver = version;
            if (!semver.startsWith("^") && !semver.startsWith("~") && !semver.startsWith("=") && !semver.startsWith(">") && !semver.startsWith("*")) {
                semver = "^" + semver;
            }

            JSONObject rootDeps = rootPkg.optJSONObject(PackageJsonSync.KEY_DEPENDENCIES);
            if (rootDeps == null) {
                rootDeps = new JSONObject();
                rootPkg.put(PackageJsonSync.KEY_DEPENDENCIES, rootDeps);
            }

            JSONObject rootDevDeps = rootPkg.optJSONObject(PackageJsonSync.KEY_DEV_DEPENDENCIES);
            if (rootDevDeps == null) {
                rootDevDeps = new JSONObject();
                rootPkg.put(PackageJsonSync.KEY_DEV_DEPENDENCIES, rootDevDeps);
            }

            if (isDev) {
                rootDevDeps.put(packageName, semver);
                rootDeps.remove(packageName);
            } else {
                rootDeps.put(packageName, semver);
                rootDevDeps.remove(packageName);
            }

            // 2. Update node_modules/<packageName> entry
            String pkgKey = "node_modules/" + packageName;
            JSONObject pkgEntry = new JSONObject();
            pkgEntry.put("version", version);

            if (tarballUrl != null && !tarballUrl.isEmpty()) {
                pkgEntry.put("resolved", tarballUrl);
            }
            if (integrity != null && !integrity.isEmpty()) {
                pkgEntry.put("integrity", integrity);
            }
            if (license != null && !license.isEmpty()) {
                pkgEntry.put("license", license);
            }
            if (isDev) {
                pkgEntry.put("dev", true);
            }

            packages.put(pkgKey, pkgEntry);

            File file = new File(projectRoot, PACKAGE_LOCK_JSON);
            FileUtils.writeFile(file, lockJson.toString(2));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Removes an uninstalled package from package-lock.json.
     */
    public static boolean removePackage(@NonNull File projectRoot, @NonNull String packageName) {
        JSONObject lockJson = readPackageLockJson(projectRoot);
        if (lockJson == null) return false;

        boolean modified = false;
        try {
            JSONObject packages = lockJson.optJSONObject("packages");
            if (packages != null) {
                JSONObject rootPkg = packages.optJSONObject("");
                if (rootPkg != null) {
                    JSONObject rootDeps = rootPkg.optJSONObject(PackageJsonSync.KEY_DEPENDENCIES);
                    if (rootDeps != null && rootDeps.has(packageName)) {
                        rootDeps.remove(packageName);
                        modified = true;
                    }
                    JSONObject rootDevDeps = rootPkg.optJSONObject(PackageJsonSync.KEY_DEV_DEPENDENCIES);
                    if (rootDevDeps != null && rootDevDeps.has(packageName)) {
                        rootDevDeps.remove(packageName);
                        modified = true;
                    }
                }

                String pkgKey = "node_modules/" + packageName;
                if (packages.has(pkgKey)) {
                    packages.remove(pkgKey);
                    modified = true;
                }
            }

            if (modified) {
                File file = new File(projectRoot, PACKAGE_LOCK_JSON);
                FileUtils.writeFile(file, lockJson.toString(2));
                return true;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

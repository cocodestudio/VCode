package com.cocode.vcode.ide.data.npm.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.utils.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Synchronizes dependencies and devDependencies with the workspace package.json
 * using Android's native {@link org.json.JSONObject}.
 */
public final class PackageJsonSync {

    public static final String PACKAGE_JSON = "package.json";
    public static final String KEY_NAME = "name";
    public static final String KEY_VERSION = "version";
    public static final String KEY_PRIVATE = "private";
    public static final String KEY_DEPENDENCIES = "dependencies";
    public static final String KEY_DEV_DEPENDENCIES = "devDependencies";

    private PackageJsonSync() {
    }

    /**
     * Reads package.json from the project root.
     *
     * @param projectRoot the root directory of the project
     * @return JSONObject or null if not found or malformed
     */
    @Nullable
    public static JSONObject readPackageJson(@Nullable File projectRoot) {
        if (projectRoot == null) return null;
        File file = new File(projectRoot, PACKAGE_JSON);
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
     * Ensures that package.json exists in the project root. If absent, creates a clean starter manifest
     * using the human-readable project name from .vcode/meta/project.json via ProjectRepository.getProjectName().
     */
    @NonNull
    public static JSONObject ensurePackageJson(@NonNull File projectRoot) {
        return ensurePackageJson(projectRoot, ProjectRepository.getProjectName(projectRoot));
    }

    /**
     * Ensures that package.json exists in the project root. If absent, creates a clean starter manifest.
     */
    @NonNull
    public static JSONObject ensurePackageJson(@NonNull File projectRoot, @Nullable String projectName) {
        JSONObject json = readPackageJson(projectRoot);
        if (json != null) return json;

        if (projectName == null || projectName.trim().isEmpty()) {
            projectName = ProjectRepository.getProjectName(projectRoot);
        }

        json = new JSONObject();
        try {
            String safeName = projectName.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-").replaceAll("^-+|-+$", "");
            if (safeName.isEmpty()) safeName = "vcode-project";
            json.put(KEY_NAME, safeName);
            json.put(KEY_VERSION, "1.0.0");
            json.put(KEY_PRIVATE, true);
            json.put(KEY_DEPENDENCIES, new JSONObject());
            json.put(KEY_DEV_DEPENDENCIES, new JSONObject());

            File file = new File(projectRoot, PACKAGE_JSON);
            FileUtils.writeFile(file, json.toString(2));
        } catch (Exception ignored) {
        }
        return json;
    }

    /**
     * Returns a map of all dependencies defined in package.json (name → version).
     */
    @NonNull
    public static Map<String, String> getDependencies(@Nullable File projectRoot) {
        return extractMap(readPackageJson(projectRoot), KEY_DEPENDENCIES);
    }

    /**
     * Returns a map of all devDependencies defined in package.json (name → version).
     */
    @NonNull
    public static Map<String, String> getDevDependencies(@Nullable File projectRoot) {
        return extractMap(readPackageJson(projectRoot), KEY_DEV_DEPENDENCIES);
    }

    /**
     * Adds or updates a dependency in package.json and writes changes atomically.
     *
     * @param projectRoot root directory
     * @param packageName package name (e.g. "axios")
     * @param version     version (e.g. "1.7.9")
     * @param isDev       true if devDependency, false if normal dependency
     * @return true if successfully saved
     */
    public static boolean addDependency(@NonNull File projectRoot,
                                        @NonNull String packageName,
                                        @NonNull String version,
                                        boolean isDev) {
        JSONObject json = readPackageJson(projectRoot);
        if (json == null) {
            json = ensurePackageJson(projectRoot);
        }

        try {
            String targetKey = isDev ? KEY_DEV_DEPENDENCIES : KEY_DEPENDENCIES;
            String otherKey = isDev ? KEY_DEPENDENCIES : KEY_DEV_DEPENDENCIES;

            JSONObject targetObj = json.optJSONObject(targetKey);
            if (targetObj == null) {
                targetObj = new JSONObject();
                json.put(targetKey, targetObj);
            }

            // Remove from other section if switching between normal and dev
            JSONObject otherObj = json.optJSONObject(otherKey);
            if (otherObj != null && otherObj.has(packageName)) {
                otherObj.remove(packageName);
            }

            String semver = version;
            if (!semver.startsWith("^") && !semver.startsWith("~") && !semver.startsWith("=") && !semver.startsWith(">") && !semver.startsWith("*")) {
                semver = "^" + semver;
            }

            targetObj.put(packageName, semver);

            File file = new File(projectRoot, PACKAGE_JSON);
            FileUtils.writeFile(file, json.toString(2));
            com.cocode.vcode.ide.core.events.IdeEventBus.getInstance().publish(
                    new com.cocode.vcode.ide.core.events.ProjectEvent.DependencyChangedEvent(
                            projectRoot, packageName,
                            com.cocode.vcode.ide.core.events.ProjectEvent.DependencyChangedEvent.Action.ADDED));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Removes a package from both dependencies and devDependencies in package.json.
     *
     * @param projectRoot root directory
     * @param packageName package to remove
     * @return true if successfully removed and saved
     */
    public static boolean removeDependency(@NonNull File projectRoot, @NonNull String packageName) {
        JSONObject json = readPackageJson(projectRoot);
        if (json == null) return false;

        boolean modified = false;
        JSONObject deps = json.optJSONObject(KEY_DEPENDENCIES);
        if (deps != null && deps.has(packageName)) {
            deps.remove(packageName);
            modified = true;
        }

        JSONObject devDeps = json.optJSONObject(KEY_DEV_DEPENDENCIES);
        if (devDeps != null && devDeps.has(packageName)) {
            devDeps.remove(packageName);
            modified = true;
        }

        if (modified) {
            File file = new File(projectRoot, PACKAGE_JSON);
            try {
                FileUtils.writeFile(file, json.toString(2));
                com.cocode.vcode.ide.core.events.IdeEventBus.getInstance().publish(
                        new com.cocode.vcode.ide.core.events.ProjectEvent.DependencyChangedEvent(
                                projectRoot, packageName,
                                com.cocode.vcode.ide.core.events.ProjectEvent.DependencyChangedEvent.Action.REMOVED));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads the installed version from node_modules/<packageName>/package.json.
     *
     * @param projectRoot root directory
     * @param packageName package name
     * @return installed version string, or null if not installed
     */
    @Nullable
    public static String getInstalledPackageVersion(@Nullable File projectRoot, @Nullable String packageName) {
        if (projectRoot == null || packageName == null) return null;
        File pkgJson = new File(projectRoot, "node_modules/" + packageName + "/package.json");
        if (!pkgJson.exists()) return null;

        try {
            String content = FileUtils.readFile(pkgJson);
            if (content == null) return null;
            JSONObject obj = new JSONObject(content);
            return obj.optString(KEY_VERSION, null);
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Map<String, String> extractMap(@Nullable JSONObject json, @NonNull String key) {
        if (json == null) return Collections.emptyMap();
        JSONObject obj = json.optJSONObject(key);
        if (obj == null) return Collections.emptyMap();

        Map<String, String> result = new LinkedHashMap<>();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            result.put(k, obj.optString(k, "*"));
        }
        return result;
    }
}

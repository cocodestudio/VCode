package com.cocode.vcode.ide.data.npm;

import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.core.events.IdeEventBus;
import com.cocode.vcode.ide.core.events.ProjectEvent;
import com.cocode.vcode.ide.data.model.Project;
import com.cocode.vcode.ide.data.model.ProjectFacet;
import com.cocode.vcode.ide.data.npm.sync.PackageJsonSync;
import com.cocode.vcode.ide.data.npm.sync.PackageLockJsonSync;
import com.cocode.vcode.ide.utils.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Project facet representing an NPM-enabled workspace.
 * Tracks package.json, scripts, dependencies, devDependencies, and lockfiles.
 * Automatically attaches file watchers to detect external modifications and publishes
 * {@link ProjectEvent.DependencyChangedEvent} on {@link IdeEventBus}.
 */
public class NpmProjectFacet implements ProjectFacet {

    public static final String FACET_ID = "npm";
    private static final long DEBOUNCE_DELAY_MS = 300L;

    private Project project;
    private File projectRoot;
    private boolean attached = false;

    private String name = "";
    private String version = "1.0.0";
    private boolean isPrivate = false;
    private final Map<String, String> dependencies = new HashMap<>();
    private final Map<String, String> devDependencies = new HashMap<>();
    private final Map<String, String> scripts = new HashMap<>();

    private FileObserver packageJsonObserver;
    private Handler debounceHandler;
    private final Runnable debounceReloadRunnable = this::reloadAndNotify;

    private synchronized Handler getDebounceHandler() {
        if (debounceHandler == null) {
            try {
                if (Looper.getMainLooper() != null) {
                    debounceHandler = new Handler(Looper.getMainLooper());
                }
            } catch (Throwable ignored) {
            }
        }
        return debounceHandler;
    }

    public NpmProjectFacet() {
    }

    public NpmProjectFacet(@NonNull File projectRoot) {
        this.projectRoot = projectRoot;
    }

    @NonNull
    @Override
    public String getFacetId() {
        return FACET_ID;
    }

    @Override
    public void attach(@NonNull Project project) {
        this.project = project;
        if (project.getRootDir() != null) {
            this.projectRoot = project.getRootDir();
        }
        this.attached = true;
        reload();
        startWatching();
    }

    @Override
    public void detach() {
        stopWatching();
        this.attached = false;
        this.project = null;
    }

    @Override
    public boolean isAttached() {
        return attached;
    }

    @Nullable
    public File getProjectRoot() {
        return projectRoot != null ? projectRoot : (project != null ? project.getRootDir() : null);
    }

    public void setProjectRoot(@Nullable File projectRoot) {
        this.projectRoot = projectRoot;
    }

    @NonNull
    public File getPackageJsonFile() {
        File root = getProjectRoot();
        return new File(root, PackageJsonSync.PACKAGE_JSON);
    }

    @NonNull
    public File getPackageLockJsonFile() {
        File root = getProjectRoot();
        return new File(root, PackageLockJsonSync.PACKAGE_LOCK_JSON);
    }

    @NonNull
    public File getNodeModulesDir() {
        File root = getProjectRoot();
        return new File(root, "node_modules");
    }

    public boolean hasPackageJson() {
        return getPackageJsonFile().exists();
    }

    public boolean hasLockFile() {
        return getPackageLockJsonFile().exists();
    }

    public boolean hasNodeModules() {
        return getNodeModulesDir().isDirectory();
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getVersion() {
        return version;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    @NonNull
    public Map<String, String> getDependencies() {
        synchronized (dependencies) {
            return Collections.unmodifiableMap(new HashMap<>(dependencies));
        }
    }

    @NonNull
    public Map<String, String> getDevDependencies() {
        synchronized (devDependencies) {
            return Collections.unmodifiableMap(new HashMap<>(devDependencies));
        }
    }

    @NonNull
    public Map<String, String> getScripts() {
        synchronized (scripts) {
            return Collections.unmodifiableMap(new HashMap<>(scripts));
        }
    }

    public boolean hasDependency(@NonNull String packageName) {
        synchronized (dependencies) {
            if (dependencies.containsKey(packageName)) return true;
        }
        synchronized (devDependencies) {
            return devDependencies.containsKey(packageName);
        }
    }

    /**
     * Reads package.json and updates cached state.
     */
    public synchronized void reload() {
        File root = getProjectRoot();
        if (root == null) return;

        JSONObject json = PackageJsonSync.readPackageJson(root);
        if (json == null) return;

        this.name = json.optString(PackageJsonSync.KEY_NAME, "");
        this.version = json.optString(PackageJsonSync.KEY_VERSION, "1.0.0");
        this.isPrivate = json.optBoolean(PackageJsonSync.KEY_PRIVATE, false);

        Map<String, String> newDeps = PackageJsonSync.getDependencies(root);
        Map<String, String> newDevDeps = PackageJsonSync.getDevDependencies(root);
        Map<String, String> newScripts = extractScripts(json);

        synchronized (dependencies) {
            dependencies.clear();
            dependencies.putAll(newDeps);
        }
        synchronized (devDependencies) {
            devDependencies.clear();
            devDependencies.putAll(newDevDeps);
        }
        synchronized (scripts) {
            scripts.clear();
            scripts.putAll(newScripts);
        }
    }

    /**
     * Reloads package.json, diffs changes against previous cache, and emits
     * {@link ProjectEvent.DependencyChangedEvent} on {@link IdeEventBus}.
     */
    public synchronized void reloadAndNotify() {
        File root = getProjectRoot();
        if (root == null) return;

        Map<String, String> oldDeps = new HashMap<>();
        synchronized (dependencies) {
            oldDeps.putAll(dependencies);
        }
        synchronized (devDependencies) {
            oldDeps.putAll(devDependencies);
        }

        reload();

        Map<String, String> currentDeps = new HashMap<>();
        synchronized (dependencies) {
            currentDeps.putAll(dependencies);
        }
        synchronized (devDependencies) {
            currentDeps.putAll(devDependencies);
        }

        // Detect added or updated dependencies
        for (Map.Entry<String, String> entry : currentDeps.entrySet()) {
            String pkg = entry.getKey();
            String ver = entry.getValue();
            if (!oldDeps.containsKey(pkg)) {
                IdeEventBus.getInstance().publish(
                        new ProjectEvent.DependencyChangedEvent(root, pkg, ProjectEvent.DependencyChangedEvent.Action.ADDED));
            } else if (!ver.equals(oldDeps.get(pkg))) {
                IdeEventBus.getInstance().publish(
                        new ProjectEvent.DependencyChangedEvent(root, pkg, ProjectEvent.DependencyChangedEvent.Action.UPDATED));
            }
        }

        // Detect removed dependencies
        for (String oldPkg : oldDeps.keySet()) {
            if (!currentDeps.containsKey(oldPkg)) {
                IdeEventBus.getInstance().publish(
                        new ProjectEvent.DependencyChangedEvent(root, oldPkg, ProjectEvent.DependencyChangedEvent.Action.REMOVED));
            }
        }
    }

    /**
     * Adds or updates a dependency in package.json and updates the facet cache.
     */
    public boolean addDependency(@NonNull String packageName, @NonNull String version, boolean isDev) {
        File root = getProjectRoot();
        if (root == null) return false;

        boolean success = PackageJsonSync.addDependency(root, packageName, version, isDev);
        if (success) {
            reloadAndNotify();
        }
        return success;
    }

    /**
     * Removes a dependency from package.json and updates the facet cache.
     */
    public boolean removeDependency(@NonNull String packageName) {
        File root = getProjectRoot();
        if (root == null) return false;

        boolean success = PackageJsonSync.removeDependency(root, packageName);
        if (success) {
            reloadAndNotify();
        }
        return success;
    }

    private Map<String, String> extractScripts(JSONObject json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        JSONObject scriptsObj = json.optJSONObject("scripts");
        if (scriptsObj != null) {
            java.util.Iterator<String> keys = scriptsObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, scriptsObj.optString(key, ""));
            }
        }
        return map;
    }

    private void startWatching() {
        File root = getProjectRoot();
        if (root == null || !root.exists()) return;

        stopWatching();
        try {
            // Android FileObserver monitors the root directory for events on package.json
            packageJsonObserver = new FileObserver(root.getAbsolutePath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MODIFY | FileObserver.DELETE | FileObserver.MOVED_TO | FileObserver.MOVED_FROM) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    if (path != null && PackageJsonSync.PACKAGE_JSON.equals(path)) {
                        Handler handler = getDebounceHandler();
                        if (handler != null) {
                            handler.removeCallbacks(debounceReloadRunnable);
                            handler.postDelayed(debounceReloadRunnable, DEBOUNCE_DELAY_MS);
                        }
                    }
                }
            };
            packageJsonObserver.startWatching();
        } catch (Exception ignored) {
        }
    }

    private void stopWatching() {
        if (packageJsonObserver != null) {
            try {
                packageJsonObserver.stopWatching();
            } catch (Exception ignored) {
            }
            packageJsonObserver = null;
        }
        if (debounceHandler != null) {
            debounceHandler.removeCallbacks(debounceReloadRunnable);
        }
    }
}

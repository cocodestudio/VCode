package com.cocode.vcode.ide.core.extension;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;

import dalvik.system.PathClassLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central host managing extension discovery, class loading, activation, and disposal.
 * <p>
 * Supports both Tier 1 Declarative Web/JSON extensions and Tier 2 Native Android APK plugins
 * loaded via {@link PathClassLoader} without runtime DEX generation.
 */
public class ExtensionHost {

    public static final String ACTION_PLUGIN = "com.cocode.vcode.action.PLUGIN";
    private static volatile ExtensionHost instance;

    public static class ActiveExtension {
        private final ExtensionManifest manifest;
        private final VCodePlugin plugin;
        private final ExtensionContext context;

        public ActiveExtension(ExtensionManifest manifest, VCodePlugin plugin, ExtensionContext context) {
            this.manifest = manifest;
            this.plugin = plugin;
            this.context = context;
        }

        public ExtensionManifest getManifest() {
            return manifest;
        }

        public VCodePlugin getPlugin() {
            return plugin;
        }

        public ExtensionContext getContext() {
            return context;
        }
    }

    private final Map<String, ActiveExtension> activeExtensions = new ConcurrentHashMap<>();

    private ExtensionHost() {
    }

    public static ExtensionHost getInstance() {
        if (instance == null) {
            synchronized (ExtensionHost.class) {
                if (instance == null) {
                    instance = new ExtensionHost();
                }
            }
        }
        return instance;
    }

    /**
     * Scans Android's {@link PackageManager} for installed APKs responding to {@link #ACTION_PLUGIN}.
     * Executes asynchronously on {@link ExecutorProvider#runOnExtension}.
     */
    public void scanAndLoadInstalledApkPlugins(@NonNull Context context, @Nullable File workspaceDir) {
        ExecutorProvider.getInstance().runOnExtension(() -> {
            try {
                PackageManager pm = context.getPackageManager();
                Intent intent = new Intent(ACTION_PLUGIN);
                List<ResolveInfo> matches = pm.queryIntentServices(intent, 0);

                if (matches.isEmpty()) {
                    matches = pm.queryIntentActivities(intent, 0);
                }

                for (ResolveInfo info : matches) {
                    String packageName = info.serviceInfo != null ? info.serviceInfo.packageName :
                            (info.activityInfo != null ? info.activityInfo.packageName : null);
                    if (packageName == null) continue;

                    try {
                        loadApkPluginSynchronously(context, packageName, workspaceDir);
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * Loads and activates an installed APK plugin by package name.
     */
    public synchronized boolean loadApkPluginSynchronously(@NonNull Context context,
                                                           @NonNull String packageName,
                                                           @Nullable File workspaceDir) throws Exception {
        PackageManager pm = context.getPackageManager();
        String apkPath = pm.getApplicationInfo(packageName, 0).sourceDir;
        Resources res = pm.getResourcesForApplication(packageName);

        // Read manifest from @raw/vcode_plugin or assets/vcode-extension.json
        String manifestJson = null;
        int rawId = res.getIdentifier("vcode_plugin", "raw", packageName);
        if (rawId != 0) {
            try (InputStream is = res.openRawResource(rawId);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                manifestJson = sb.toString();
            }
        }

        if (manifestJson == null) {
            try (InputStream is = res.getAssets().open("vcode-extension.json");
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                manifestJson = sb.toString();
            }
        }

        if (manifestJson == null) return false;

        ExtensionManifest manifest = ExtensionManifest.fromJson(manifestJson);
        manifest.setApkPath(apkPath);

        if (activeExtensions.containsKey(manifest.getId())) {
            return true; // Already active
        }

        if (manifest.getEntryPoint() != null) {
            PathClassLoader classLoader = new PathClassLoader(apkPath, context.getClassLoader());
            Class<?> entryClass = classLoader.loadClass(manifest.getEntryPoint());
            VCodePlugin plugin = (VCodePlugin) entryClass.getDeclaredConstructor().newInstance();

            ExtensionContext extCtx = new ExtensionContext(manifest.getId(), null, workspaceDir);
            plugin.initialize(extCtx);

            activeExtensions.put(manifest.getId(), new ActiveExtension(manifest, plugin, extCtx));
            return true;
        }

        return false;
    }

    /**
     * Activates an in-memory or programmatic plugin directly (useful for tests and built-in modules).
     */
    public synchronized void activatePlugin(@NonNull ExtensionManifest manifest,
                                            @NonNull VCodePlugin plugin,
                                            @Nullable File workspaceDir) {
        if (activeExtensions.containsKey(manifest.getId())) {
            unloadExtension(manifest.getId());
        }

        ExtensionContext extCtx = new ExtensionContext(manifest.getId(), null, workspaceDir);
        plugin.initialize(extCtx);
        activeExtensions.put(manifest.getId(), new ActiveExtension(manifest, plugin, extCtx));
    }

    /**
     * Deactivates and unloads an extension cleanly, disposing all registered subscriptions.
     */
    public synchronized void unloadExtension(@NonNull String extensionId) {
        ActiveExtension ext = activeExtensions.remove(extensionId);
        if (ext != null) {
            try {
                ext.getPlugin().deactivate();
            } catch (Throwable ignored) {
            }
            try {
                ext.getContext().dispose();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Returns an unmodifiable list of active extensions.
     */
    @NonNull
    public List<ActiveExtension> getActiveExtensions() {
        return Collections.unmodifiableList(new ArrayList<>(activeExtensions.values()));
    }

    /**
     * Checks if an extension is currently active.
     */
    public boolean isExtensionActive(@NonNull String extensionId) {
        return activeExtensions.containsKey(extensionId);
    }

    /**
     * Deactivates all extensions and clears the host registry.
     */
    public synchronized void shutdown() {
        for (String id : new ArrayList<>(activeExtensions.keySet())) {
            unloadExtension(id);
        }
        activeExtensions.clear();
    }
}

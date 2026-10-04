package com.cocode.vcode.ide.core.extension;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.core.command.CommandItem;
import com.cocode.vcode.ide.core.command.CommandRegistry;
import com.cocode.vcode.ide.core.events.Disposable;
import com.cocode.vcode.ide.data.preferences.ConfigurationRegistry;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;

import dalvik.system.PathClassLoader;

import org.json.JSONArray;
import org.json.JSONObject;

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

    public static class DeclarativePlugin implements VCodePlugin {
        @Override
        public void initialize(@NonNull ExtensionContext context) {
        }

        @Override
        public void deactivate() {
        }
    }

    /**
     * Scans directory locations for Tier 1 Declarative extensions containing {@code vcode-extension.json}.
     * Registers declared configuration contributions in {@link ConfigurationRegistry} and commands in {@link CommandRegistry}.
     *
     * @param extensionsDir global or local extensions root directory (e.g. dataDir/extensions)
     * @param workspaceDir  active workspace root directory (scans workspaceDir/.vcode/extensions)
     * @return list of loaded active extensions
     */
    @NonNull
    public synchronized List<ActiveExtension> scanAndLoadDirectoryExtensions(@Nullable File extensionsDir,
                                                                             @Nullable File workspaceDir) {
        List<ActiveExtension> loaded = new ArrayList<>();
        List<File> searchDirs = new ArrayList<>();

        if (extensionsDir != null && extensionsDir.isDirectory()) {
            searchDirs.add(extensionsDir);
        }

        if (workspaceDir != null && workspaceDir.isDirectory()) {
            File wsExtDir = new File(workspaceDir, ".vcode" + File.separator + "extensions");
            if (wsExtDir.isDirectory()) {
                searchDirs.add(wsExtDir);
            }
        }

        for (File root : searchDirs) {
            File[] subDirs = root.listFiles();
            if (subDirs == null) continue;

            for (File dir : subDirs) {
                if (!dir.isDirectory()) continue;
                File manifestFile = new File(dir, "vcode-extension.json");
                if (!manifestFile.isFile()) {
                    manifestFile = new File(dir, "package.json");
                }
                if (!manifestFile.isFile()) continue;

                try {
                    String jsonContent = FileUtils.readFile(manifestFile);
                    if (jsonContent == null || jsonContent.trim().isEmpty()) continue;

                    ExtensionManifest manifest = ExtensionManifest.fromJson(jsonContent);
                    if (activeExtensions.containsKey(manifest.getId())) {
                        loaded.add(activeExtensions.get(manifest.getId()));
                        continue;
                    }

                    ExtensionContext extCtx = new ExtensionContext(manifest.getId(), dir, workspaceDir);
                    DeclarativePlugin plugin = new DeclarativePlugin();
                    plugin.initialize(extCtx);

                    // Parse contributions
                    JSONObject json = new JSONObject(jsonContent);
                    JSONObject contributes = json.optJSONObject("contributes");
                    if (contributes != null) {
                        // 1. Configurations
                        JSONObject config = contributes.optJSONObject("configuration");
                        if (config != null) {
                            JSONObject properties = config.optJSONObject("properties");
                            if (properties != null) {
                                java.util.Iterator<String> keys = properties.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();
                                    JSONObject prop = properties.optJSONObject(key);
                                    if (prop == null) continue;

                                    String typeStr = prop.optString("type", "string");
                                    String title = prop.optString("title", key);
                                    String desc = prop.optString("description", "");
                                    String cat = prop.optString("category", config.optString("title", "Extensions"));

                                    if ("boolean".equalsIgnoreCase(typeStr)) {
                                        boolean defVal = prop.optBoolean("default", false);
                                        ConfigurationRegistry.getInstance().registerKey(key, Boolean.class, defVal, title, desc, cat);
                                        extCtx.addDisposable(() -> ConfigurationRegistry.getInstance().unregisterKey(key));
                                    } else if ("integer".equalsIgnoreCase(typeStr) || "number".equalsIgnoreCase(typeStr)) {
                                        int defVal = prop.optInt("default", 0);
                                        ConfigurationRegistry.getInstance().registerKey(key, Integer.class, defVal, title, desc, cat);
                                        extCtx.addDisposable(() -> ConfigurationRegistry.getInstance().unregisterKey(key));
                                    } else {
                                        String defVal = prop.optString("default", "");
                                        ConfigurationRegistry.getInstance().registerKey(key, String.class, defVal, title, desc, cat);
                                        extCtx.addDisposable(() -> ConfigurationRegistry.getInstance().unregisterKey(key));
                                    }
                                }
                            }
                        }

                        // 2. Commands
                        JSONArray commands = contributes.optJSONArray("commands");
                        if (commands != null) {
                            for (int i = 0; i < commands.length(); i++) {
                                JSONObject cmdObj = commands.optJSONObject(i);
                                if (cmdObj == null) continue;
                                String cmdId = cmdObj.optString("command");
                                String cmdTitle = cmdObj.optString("title", cmdId);
                                String cmdCategory = cmdObj.optString("category", "General");
                                if (cmdId != null && !cmdId.isEmpty()) {
                                    Disposable d = CommandRegistry.getInstance().registerCommand(
                                            new CommandItem(cmdId, cmdTitle, cmdCategory, 0, args -> {})
                                    );
                                    extCtx.addDisposable(d);
                                }
                            }
                        }
                    }

                    ActiveExtension activeExt = new ActiveExtension(manifest, plugin, extCtx);
                    activeExtensions.put(manifest.getId(), activeExt);
                    loaded.add(activeExt);
                } catch (Throwable t) {
                    android.util.Log.e("ExtensionHost", "Failed loading extension from " + dir.getAbsolutePath(), t);
                }
            }
        }

        return Collections.unmodifiableList(loaded);
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

package com.cocode.vcode.ide.core.extension;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Model representing parsed plugin metadata from {@code vcode-extension.json} or {@code @raw/vcode_plugin.json}.
 */
public class ExtensionManifest {

    private final String id;
    private final String name;
    private final String version;
    private final String displayName;
    private final String description;
    private final String entryPoint;
    private String apkPath;

    public ExtensionManifest(@NonNull String id,
                             @NonNull String name,
                             @NonNull String version,
                             @Nullable String displayName,
                             @Nullable String description,
                             @Nullable String entryPoint) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.displayName = displayName != null ? displayName : name;
        this.description = description != null ? description : "";
        this.entryPoint = entryPoint;
    }

    /**
     * Parses an ExtensionManifest from a JSON string using native Android org.json.
     */
    @NonNull
    public static ExtensionManifest fromJson(@NonNull String jsonString) throws JSONException {
        JSONObject obj = new JSONObject(jsonString);
        String id = obj.optString("id", obj.optString("name", "unknown-plugin"));
        String name = obj.optString("name", id);
        String version = obj.optString("version", "1.0.0");
        String displayName = obj.optString("displayName", name);
        String description = obj.optString("description", "");
        String entryPoint = obj.optString("entryPoint", null);

        return new ExtensionManifest(id, name, version, displayName, description, entryPoint);
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getVersion() {
        return version;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    @Nullable
    public String getEntryPoint() {
        return entryPoint;
    }

    @Nullable
    public String getApkPath() {
        return apkPath;
    }

    public void setApkPath(@Nullable String apkPath) {
        this.apkPath = apkPath;
    }
}

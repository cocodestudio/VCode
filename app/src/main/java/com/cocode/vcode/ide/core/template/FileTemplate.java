package com.cocode.vcode.ide.core.template;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * Model representing a configurable file template bound to a file extension.
 */
public class FileTemplate {

    private String id;
    private String name;
    private String extension;
    private String content;
    private boolean isBuiltIn;

    public FileTemplate(@NonNull String id, @NonNull String name, @NonNull String extension, @NonNull String content, boolean isBuiltIn) {
        this.id = id;
        this.name = name;
        this.extension = normalizeExtension(extension);
        this.content = content;
        this.isBuiltIn = isBuiltIn;
    }

    public FileTemplate(@NonNull FileTemplate other) {
        this.id = other.id;
        this.name = other.name;
        this.extension = other.extension;
        this.content = other.content;
        this.isBuiltIn = other.isBuiltIn;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public String getExtension() {
        return extension;
    }

    public void setExtension(@NonNull String extension) {
        this.extension = normalizeExtension(extension);
    }

    @NonNull
    public String getContent() {
        return content != null ? content : "";
    }

    public void setContent(@NonNull String content) {
        this.content = content;
    }

    public boolean isBuiltIn() {
        return isBuiltIn;
    }

    public void setBuiltIn(boolean builtIn) {
        isBuiltIn = builtIn;
    }

    /**
     * Returns a safe filename for saving this template on disk so it can be opened in EditorActivity.
     */
    @NonNull
    public String getDiskFileName() {
        String safeId = id.replaceAll("[^a-zA-Z0-9_-]", "_");
        return safeId + "." + extension;
    }

    @NonNull
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("extension", extension);
            json.put("content", content);
            json.put("isBuiltIn", isBuiltIn);
        } catch (JSONException ignored) {
        }
        return json;
    }

    @Nullable
    public static FileTemplate fromJson(@NonNull JSONObject json) {
        String id = json.optString("id", "").trim();
        String name = json.optString("name", "").trim();
        String extension = json.optString("extension", "").trim();
        String content = json.optString("content", "");
        boolean isBuiltIn = json.optBoolean("isBuiltIn", false);

        if (id.isEmpty() || extension.isEmpty()) {
            return null;
        }
        if (name.isEmpty()) {
            name = extension.toUpperCase();
        }

        return new FileTemplate(id, name, extension, content, isBuiltIn);
    }

    @NonNull
    public static String normalizeExtension(@Nullable String ext) {
        if (ext == null) {
            return "";
        }
        String trimmed = ext.trim().toLowerCase();
        while (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileTemplate that = (FileTemplate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

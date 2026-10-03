package com.cocode.vcode.ide.data.preferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Definition of a dynamic configuration entry registered in {@link ConfigurationRegistry}.
 */
public class ConfigurationItem {

    private final String key;
    private final Class<?> type;
    private final Object defaultValue;
    private final String title;
    private final String description;
    private final String category;

    public ConfigurationItem(@NonNull String key,
                             @NonNull Class<?> type,
                             @Nullable Object defaultValue,
                             @NonNull String title,
                             @NonNull String description,
                             @NonNull String category) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.title = title;
        this.description = description;
        this.category = category;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @NonNull
    public Class<?> getType() {
        return type;
    }

    @Nullable
    public Object getDefaultValue() {
        return defaultValue;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    @NonNull
    public String getCategory() {
        return category;
    }
}

package com.cocode.vcode.ide.core.events;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Dispatched when a configuration or settings value is modified dynamically.
 */
public class ConfigurationEvent implements IdeEvent {

    private final String key;
    private final Object newValue;
    private final Object oldValue;
    private final long timestamp;

    public ConfigurationEvent(@NonNull String key, @Nullable Object newValue, @Nullable Object oldValue) {
        this.key = key;
        this.newValue = newValue;
        this.oldValue = oldValue;
        this.timestamp = System.currentTimeMillis();
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @Nullable
    public Object getNewValue() {
        return newValue;
    }

    @Nullable
    public Object getOldValue() {
        return oldValue;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}

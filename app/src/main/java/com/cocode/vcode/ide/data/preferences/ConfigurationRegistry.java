package com.cocode.vcode.ide.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.core.events.ConfigurationEvent;
import com.cocode.vcode.ide.core.events.IdeEventBus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dynamic configuration registry that allows core IDE subsystems, the package manager,
 * and extensions to register, validate, query, and mutate settings dynamically.
 * Backed by Android {@link SharedPreferences} and synchronized with {@link IdeEventBus}.
 */
public class ConfigurationRegistry {

    private static final String DEFAULT_PREFS_NAME = "vcode_settings";
    private static final ConfigurationRegistry INSTANCE = new ConfigurationRegistry();

    public interface OnConfigurationChangeListener {
        void onConfigurationChanged(@NonNull String key, @Nullable Object newValue);
    }

    private final Map<String, ConfigurationItem> registeredItems = new ConcurrentHashMap<>();
    private final Map<String, Object> memoryStore = new ConcurrentHashMap<>();
    private final Map<String, List<OnConfigurationChangeListener>> listeners = new ConcurrentHashMap<>();

    private SharedPreferences preferences;

    public static ConfigurationRegistry getInstance() {
        return INSTANCE;
    }

    private ConfigurationRegistry() {
    }

    /**
     * Initializes the registry with an Android context to bind persistent SharedPreferences.
     */
    public synchronized void init(@NonNull Context context) {
        if (preferences == null) {
            this.preferences = context.getApplicationContext().getSharedPreferences(DEFAULT_PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /**
     * Binds a specific SharedPreferences instance (useful for unit testing or custom partitions).
     */
    public synchronized void setPreferences(@Nullable SharedPreferences preferences) {
        this.preferences = preferences;
    }

    /**
     * Registers a new dynamic configuration key with metadata.
     */
    public <T> void registerKey(@NonNull String key,
                                @NonNull Class<T> type,
                                @Nullable T defaultValue,
                                @NonNull String title,
                                @NonNull String description,
                                @NonNull String category) {
        ConfigurationItem item = new ConfigurationItem(key, type, defaultValue, title, description, category);
        registeredItems.put(key, item);
        if (defaultValue != null && !memoryStore.containsKey(key)) {
            memoryStore.put(key, defaultValue);
        }
    }

    public <T> void registerKey(@NonNull String key,
                                @NonNull Class<T> type,
                                @Nullable T defaultValue,
                                @NonNull String title,
                                @NonNull String description) {
        registerKey(key, type, defaultValue, title, description, "general");
    }

    /**
     * Unregisters a configuration key.
     */
    public synchronized void unregisterKey(@NonNull String key) {
        registeredItems.remove(key);
        memoryStore.remove(key);
    }

    public boolean hasKey(@NonNull String key) {
        return registeredItems.containsKey(key);
    }

    @Nullable
    public ConfigurationItem getItem(@NonNull String key) {
        return registeredItems.get(key);
    }

    @NonNull
    public Collection<ConfigurationItem> getRegisteredItems() {
        return Collections.unmodifiableCollection(registeredItems.values());
    }

    @NonNull
    public List<ConfigurationItem> getAllItems() {
        return new ArrayList<>(registeredItems.values());
    }

    @NonNull
    public List<ConfigurationItem> getItemsByCategory(@NonNull String category) {
        List<ConfigurationItem> list = new ArrayList<>();
        for (ConfigurationItem item : registeredItems.values()) {
            if (category.equalsIgnoreCase(item.getCategory())) {
                list.add(item);
            }
        }
        return list;
    }

    /**
     * Retrieves a String configuration value.
     */
    @Nullable
    public String getString(@NonNull String key, @Nullable String fallback) {
        if (preferences != null && preferences.contains(key)) {
            return preferences.getString(key, fallback);
        }
        Object mem = memoryStore.get(key);
        if (mem != null) return String.valueOf(mem);
        ConfigurationItem item = registeredItems.get(key);
        if (item != null && item.getDefaultValue() != null) {
            return String.valueOf(item.getDefaultValue());
        }
        return fallback;
    }

    /**
     * Retrieves a boolean configuration value.
     */
    public boolean getBoolean(@NonNull String key, boolean fallback) {
        if (preferences != null && preferences.contains(key)) {
            return preferences.getBoolean(key, fallback);
        }
        Object mem = memoryStore.get(key);
        if (mem instanceof Boolean) return (Boolean) mem;
        ConfigurationItem item = registeredItems.get(key);
        if (item != null && item.getDefaultValue() instanceof Boolean) {
            return (Boolean) item.getDefaultValue();
        }
        return fallback;
    }

    /**
     * Retrieves an int configuration value.
     */
    public int getInt(@NonNull String key, int fallback) {
        if (preferences != null && preferences.contains(key)) {
            return preferences.getInt(key, fallback);
        }
        Object mem = memoryStore.get(key);
        if (mem instanceof Number) return ((Number) mem).intValue();
        ConfigurationItem item = registeredItems.get(key);
        if (item != null && item.getDefaultValue() instanceof Number) {
            return ((Number) item.getDefaultValue()).intValue();
        }
        return fallback;
    }

    /**
     * Retrieves an untyped configuration value.
     */
    @Nullable
    public Object get(@NonNull String key) {
        ConfigurationItem item = registeredItems.get(key);
        Object fallback = item != null ? item.getDefaultValue() : null;

        if (preferences != null && preferences.contains(key)) {
            if (item != null) {
                if (item.getType() == Boolean.class) {
                    return preferences.getBoolean(key, fallback instanceof Boolean ? (Boolean) fallback : false);
                } else if (item.getType() == Integer.class) {
                    return preferences.getInt(key, fallback instanceof Integer ? (Integer) fallback : 0);
                } else if (item.getType() == Long.class) {
                    return preferences.getLong(key, fallback instanceof Long ? (Long) fallback : 0L);
                } else if (item.getType() == Float.class) {
                    return preferences.getFloat(key, fallback instanceof Float ? (Float) fallback : 0f);
                } else {
                    return preferences.getString(key, fallback != null ? String.valueOf(fallback) : null);
                }
            }
            return preferences.getAll().get(key);
        }

        if (memoryStore.containsKey(key)) {
            return memoryStore.get(key);
        }
        return fallback;
    }

    /**
     * Sets a configuration value, updates persistent preferences, and notifies subscribers.
     */
    public synchronized void set(@NonNull String key, @Nullable Object value) {
        Object oldValue = get(key);
        if (value != null) {
            memoryStore.put(key, value);
        } else {
            memoryStore.remove(key);
        }

        if (preferences != null) {
            SharedPreferences.Editor editor = preferences.edit();
            if (value == null) {
                editor.remove(key);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else {
                editor.putString(key, String.valueOf(value));
            }
            editor.apply();
        }

        notifyChanged(key, value, oldValue);
    }

    public void addChangeListener(@NonNull String key, @NonNull OnConfigurationChangeListener listener) {
        listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void removeChangeListener(@NonNull String key, @NonNull OnConfigurationChangeListener listener) {
        List<OnConfigurationChangeListener> list = listeners.get(key);
        if (list != null) {
            list.remove(listener);
        }
    }

    private void notifyChanged(String key, Object newValue, Object oldValue) {
        List<OnConfigurationChangeListener> list = listeners.get(key);
        if (list != null) {
            for (OnConfigurationChangeListener listener : list) {
                try {
                    listener.onConfigurationChanged(key, newValue);
                } catch (Exception ignored) {
                }
            }
        }

        // Publish to global IdeEventBus
        IdeEventBus.getInstance().publish(new ConfigurationEvent(key, newValue, oldValue));
    }

    /**
     * Clears all registered keys and listeners (primarily for test teardown).
     */
    public synchronized void clear() {
        registeredItems.clear();
        memoryStore.clear();
        listeners.clear();
    }
}

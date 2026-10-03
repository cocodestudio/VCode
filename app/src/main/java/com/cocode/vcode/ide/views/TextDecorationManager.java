package com.cocode.vcode.ide.views;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages generic text canvas decorations for {@link CodeEditText}.
 * Provides thread-safe registration, filtering by type/tag/range, and invalidation notifications.
 */
public class TextDecorationManager {

    public interface OnDecorationsChangedListener {
        void onDecorationsChanged();
    }

    private final Map<String, TextDecoration> decorations = new ConcurrentHashMap<>();
    private final List<OnDecorationsChangedListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(@NonNull OnDecorationsChangedListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull OnDecorationsChangedListener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        for (OnDecorationsChangedListener listener : listeners) {
            try {
                listener.onDecorationsChanged();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Adds or updates a text decoration.
     */
    public void addDecoration(@NonNull TextDecoration decoration) {
        decorations.put(decoration.getId(), decoration);
        notifyChanged();
    }

    /**
     * Adds multiple text decorations in a single batch.
     */
    public void addDecorations(@NonNull Collection<TextDecoration> items) {
        if (items.isEmpty()) return;
        for (TextDecoration item : items) {
            if (item != null) {
                decorations.put(item.getId(), item);
            }
        }
        notifyChanged();
    }

    /**
     * Removes a decoration by unique ID.
     */
    public boolean removeDecoration(@Nullable String id) {
        if (id == null) return false;
        boolean removed = decorations.remove(id) != null;
        if (removed) {
            notifyChanged();
        }
        return removed;
    }

    /**
     * Removes all decorations matching the given type.
     */
    public void removeDecorationsByType(@NonNull TextDecoration.Type type) {
        boolean changed = decorations.values().removeIf(dec -> dec.getType() == type);
        if (changed) {
            notifyChanged();
        }
    }

    /**
     * Removes all decorations matching the given tag (e.g. "diagnostic", "git", plugin identifier).
     */
    public void removeDecorationsByTag(@NonNull Object tag) {
        boolean changed = decorations.values().removeIf(dec -> tag.equals(dec.getTag()));
        if (changed) {
            notifyChanged();
        }
    }

    /**
     * Removes all currently registered decorations.
     */
    public void clear() {
        if (!decorations.isEmpty()) {
            decorations.clear();
            notifyChanged();
        }
    }

    /**
     * Returns an unmodifiable list of all active decorations.
     */
    @NonNull
    public List<TextDecoration> getDecorations() {
        return Collections.unmodifiableList(new ArrayList<>(decorations.values()));
    }

    /**
     * Returns all decorations of the specified type.
     */
    @NonNull
    public List<TextDecoration> getDecorationsByType(@NonNull TextDecoration.Type type) {
        List<TextDecoration> list = new ArrayList<>();
        for (TextDecoration dec : decorations.values()) {
            if (dec.getType() == type) {
                list.add(dec);
            }
        }
        return list;
    }

    /**
     * Returns all decorations intersecting the given line index.
     */
    @NonNull
    public List<TextDecoration> getDecorationsForLine(int line) {
        List<TextDecoration> list = new ArrayList<>();
        for (TextDecoration dec : decorations.values()) {
            if (dec.intersectsLine(line)) {
                list.add(dec);
            }
        }
        return list;
    }

    /**
     * Returns all decorations intersecting the visible viewport line range [firstLine, lastLine].
     */
    @NonNull
    public List<TextDecoration> getDecorationsInRange(int firstLine, int lastLine) {
        List<TextDecoration> list = new ArrayList<>();
        for (TextDecoration dec : decorations.values()) {
            if (dec.intersectsRange(firstLine, lastLine)) {
                list.add(dec);
            }
        }
        return list;
    }

    /**
     * Returns all decorations of the specified type intersecting [firstLine, lastLine].
     */
    @NonNull
    public List<TextDecoration> getDecorationsInRangeAndType(int firstLine, int lastLine, @NonNull TextDecoration.Type type) {
        List<TextDecoration> list = new ArrayList<>();
        for (TextDecoration dec : decorations.values()) {
            if (dec.getType() == type && dec.intersectsRange(firstLine, lastLine)) {
                list.add(dec);
            }
        }
        return list;
    }

    /**
     * Invalidates cached geometric paths (e.g. when editor zoom, font size, or padding changes).
     */
    public void invalidateCachedPaths() {
        for (TextDecoration dec : decorations.values()) {
            dec.setCachedPath(null);
        }
    }
}

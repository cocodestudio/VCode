package com.cocode.vcode.ide.core.editor.text;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.views.span.SyntaxHighlightSpan;

import java.util.Collection;

/**
 * High-performance object pool for {@link SyntaxHighlightSpan} instances.
 * Eliminates garbage collection pauses and object allocation churn during rapid
 * viewport scrolling and continuous re-highlighting in large or minified files.
 */
public class SpanPool {

    private static final int DEFAULT_CAPACITY = 1024;
    private static final SpanPool INSTANCE = new SpanPool();

    private final SyntaxHighlightSpan[] pool;
    private final int capacity;
    private int size = 0;

    public static SpanPool getInstance() {
        return INSTANCE;
    }

    public SpanPool() {
        this(DEFAULT_CAPACITY);
    }

    public SpanPool(int capacity) {
        this.capacity = capacity > 0 ? capacity : DEFAULT_CAPACITY;
        this.pool = new SyntaxHighlightSpan[this.capacity];
    }

    /**
     * Acquires a recycled SyntaxHighlightSpan if available, or allocates a new one.
     * The acquired span is pre-configured with the specified color and underline attributes.
     */
    @NonNull
    public synchronized SyntaxHighlightSpan obtain(int color, boolean underline) {
        if (size > 0) {
            size--;
            SyntaxHighlightSpan span = pool[size];
            pool[size] = null;
            span.reconfigure(color, underline);
            return span;
        }
        return new SyntaxHighlightSpan(color, underline);
    }

    /**
     * Recycles a span back to the pool for reuse.
     */
    public synchronized void recycle(@Nullable SyntaxHighlightSpan span) {
        if (span == null) return;
        if (size < capacity) {
            pool[size] = span;
            size++;
        }
    }

    /**
     * Recycles a collection of spans back to the pool.
     */
    public synchronized void recycleAll(@Nullable Collection<SyntaxHighlightSpan> spans) {
        if (spans == null || spans.isEmpty()) return;
        for (SyntaxHighlightSpan span : spans) {
            if (span != null && size < capacity) {
                pool[size] = span;
                size++;
            }
        }
    }

    /**
     * Recycles an array of spans back to the pool.
     */
    public synchronized void recycleAll(@Nullable SyntaxHighlightSpan[] spans) {
        if (spans == null || spans.length == 0) return;
        for (SyntaxHighlightSpan span : spans) {
            if (span != null && size < capacity) {
                pool[size] = span;
                size++;
            }
        }
    }

    /**
     * Returns the current number of cached spans available for immediate reuse.
     */
    public synchronized int size() {
        return size;
    }

    /**
     * Returns the maximum pool capacity.
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Clears all cached spans from the pool.
     */
    public synchronized void clear() {
        for (int i = 0; i < size; i++) {
            pool[i] = null;
        }
        size = 0;
    }
}

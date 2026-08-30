package com.cocode.vcode.ide.core.completion.staticdata;

import androidx.annotation.NonNull;

/**
 * Represents a single pre-parsed entry loaded directly from a JSON
 * asset file.
 *
 * <p>Shared shape across all four static-completion datasets
 * ({@code js_keywords.json}, {@code html_tags.json},
 * {@code css_properties.json}, {@code css_colors.json},
 * {@code json_snippets.json}). The JSON entry's {@code snippet} or
 * {@code insertText} field is stored as {@link #insertText}; the
 * {@code detail} is a human-readable short description; {@code type}
 * is the {@link com.cocode.vcode.ide.core.model.CompletionItem.Type}
 * that this entry should be rendered as.
 *
 * <p>The instance is intentionally immutable and intentionally NOT
 * a {@code CompletionItem} — the loader produces these from JSON
 * once at startup, and the per-language completion engines promote
 * them to {@code CompletionItem}s as they emit them.
 */
public final class StaticCompletionItem {

    public final String label;
    /** Raw insertion text. May contain a single {@code |} cursor marker
     *  (handled by {@link SnippetCursorParser}, M.2). */
    public final String insertText;
    public final String detail;
    public final String type;
    /** For HTML tags: the per-tag attribute list. {@code null} for
     *  non-tag entries. */
    public final String[] attributes;
    /** For CSS properties: the canonical values array. {@code null}
     *  for non-property entries. */
    public final String[] values;
    /** For CSS properties: whether the value is a {@code <color>}. */
    public final boolean acceptsColor;
    /** For HTML tags: whether the tag is self-closing (void). */
    public final boolean selfClosing;

    public StaticCompletionItem(@NonNull String label,
                                 @NonNull String insertText,
                                 String detail,
                                 String type) {
        this(label, insertText, detail, type, null, null, false, false);
    }

    public StaticCompletionItem(@NonNull String label,
                                 @NonNull String insertText,
                                 String detail,
                                 String type,
                                 String[] attributes,
                                 String[] values,
                                 boolean acceptsColor,
                                 boolean selfClosing) {
        this.label = label;
        this.insertText = insertText;
        this.detail = detail;
        this.type = type;
        this.attributes = attributes;
        this.values = values;
        this.acceptsColor = acceptsColor;
        this.selfClosing = selfClosing;
    }
}

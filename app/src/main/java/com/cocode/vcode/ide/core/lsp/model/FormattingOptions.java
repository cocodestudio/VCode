package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Value-object describing formatting options matching LSP 3.17 FormattingOptions.
 */
public final class FormattingOptions {

    public final int tabSize;
    public final boolean insertSpaces;
    public final boolean trimTrailingWhitespace;
    public final boolean insertFinalNewline;
    public final boolean trimFinalNewlines;

    public FormattingOptions(int tabSize,
                             boolean insertSpaces,
                             boolean trimTrailingWhitespace,
                             boolean insertFinalNewline,
                             boolean trimFinalNewlines) {
        this.tabSize = tabSize > 0 ? tabSize : 4;
        this.insertSpaces = insertSpaces;
        this.trimTrailingWhitespace = trimTrailingWhitespace;
        this.insertFinalNewline = insertFinalNewline;
        this.trimFinalNewlines = trimFinalNewlines;
    }

    public FormattingOptions(int tabSize, boolean insertSpaces) {
        this(tabSize, insertSpaces, false, false, false);
    }

    public static final FormattingOptions DEFAULT = new FormattingOptions(4, true);

    @NonNull
    @Override
    public String toString() {
        return "FormattingOptions{tabSize=" + tabSize + ", spaces=" + insertSpaces + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FormattingOptions)) return false;
        FormattingOptions that = (FormattingOptions) o;
        return tabSize == that.tabSize &&
                insertSpaces == that.insertSpaces &&
                trimTrailingWhitespace == that.trimTrailingWhitespace &&
                insertFinalNewline == that.insertFinalNewline &&
                trimFinalNewlines == that.trimFinalNewlines;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tabSize, insertSpaces, trimTrailingWhitespace, insertFinalNewline, trimFinalNewlines);
    }
}

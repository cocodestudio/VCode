package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Semantic tokens response containing delta-encoded integer tokens matching LSP 3.17 SemanticTokens.
 * Every token takes 5 consecutive integers in {@link #data}:
 * [deltaLine, deltaStartChar, length, tokenType, tokenModifiers]
 */
public final class SemanticTokens {

    public final String resultId;
    public final int[] data;

    public SemanticTokens(String resultId, int[] data) {
        this.resultId = resultId;
        this.data = data != null ? data : new int[0];
    }

    public SemanticTokens(int[] data) {
        this(null, data);
    }

    public int getTokenCount() {
        return data.length / 5;
    }

    @NonNull
    @Override
    public String toString() {
        return "SemanticTokens{count=" + getTokenCount() + (resultId != null ? ", id='" + resultId + "'" : "") + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemanticTokens)) return false;
        SemanticTokens that = (SemanticTokens) o;
        return Objects.equals(resultId, that.resultId) && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(resultId);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }
}

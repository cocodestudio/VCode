package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Additional details for a completion item label matching LSP 3.17 CompletionItemLabelDetails.
 */
public final class CompletionItemLabelDetails {

    /**
     * An optional string which is rendered less prominently directly after the label,
     * without any spacing (e.g. signature or parameter list "(x: number, y: string)").
     */
    public final String detail;

    /**
     * An optional string which is rendered less prominently after {@link #detail}
     * (e.g. type or import location "Element" or "package/path").
     */
    public final String description;

    public CompletionItemLabelDetails(String detail, String description) {
        this.detail = detail != null ? detail : "";
        this.description = description != null ? description : "";
    }

    @NonNull
    @Override
    public String toString() {
        return "LabelDetails{" + detail + (description.isEmpty() ? "" : " - " + description) + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompletionItemLabelDetails)) return false;
        CompletionItemLabelDetails that = (CompletionItemLabelDetails) o;
        return Objects.equals(detail, that.detail) && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(detail, description);
    }
}

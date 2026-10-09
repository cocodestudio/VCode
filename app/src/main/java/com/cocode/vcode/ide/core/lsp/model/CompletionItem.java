package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A completion item represents a text snippet that is proposed to complete text that is being typed.
 * Matches LSP 3.17 CompletionItem specification.
 */
public final class CompletionItem {

    public final String label;
    public final CompletionItemLabelDetails labelDetails;
    public final CompletionItemKind kind;
    public final List<CompletionItemTag> tags;
    public final String detail;
    public final MarkupContent documentation;
    public final String sortText;
    public final String filterText;
    public final String insertText;
    public final InsertTextFormat insertTextFormat;
    public final TextEdit textEdit;
    public final List<TextEdit> additionalTextEdits;
    public final Object data;

    public CompletionItem(String label,
                          CompletionItemLabelDetails labelDetails,
                          CompletionItemKind kind,
                          List<CompletionItemTag> tags,
                          String detail,
                          MarkupContent documentation,
                          String sortText,
                          String filterText,
                          String insertText,
                          InsertTextFormat insertTextFormat,
                          TextEdit textEdit,
                          List<TextEdit> additionalTextEdits,
                          Object data) {
        this.label = label != null ? label : "";
        this.labelDetails = labelDetails;
        this.kind = kind != null ? kind : CompletionItemKind.TEXT;
        this.tags = tags != null ? Collections.unmodifiableList(new ArrayList<>(tags)) : Collections.emptyList();
        this.detail = detail != null ? detail : "";
        this.documentation = documentation;
        this.sortText = sortText != null ? sortText : this.label;
        this.filterText = filterText != null ? filterText : this.label;
        this.insertText = insertText != null ? insertText : this.label;
        this.insertTextFormat = insertTextFormat != null ? insertTextFormat : InsertTextFormat.PLAIN_TEXT;
        this.textEdit = textEdit;
        this.additionalTextEdits = additionalTextEdits != null
                ? Collections.unmodifiableList(new ArrayList<>(additionalTextEdits))
                : Collections.emptyList();
        this.data = data;
    }

    public CompletionItem(String label, CompletionItemKind kind) {
        this(label, null, kind, null, null, null, null, null, label, InsertTextFormat.PLAIN_TEXT, null, null, null);
    }

    public CompletionItem(String label, String detail, CompletionItemKind kind) {
        this(label, null, kind, null, detail, null, null, null, label, InsertTextFormat.PLAIN_TEXT, null, null, null);
    }

    @NonNull
    @Override
    public String toString() {
        return "CompletionItem{" + label + " (" + kind + ")}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompletionItem)) return false;
        CompletionItem that = (CompletionItem) o;
        return Objects.equals(label, that.label) &&
                kind == that.kind &&
                Objects.equals(detail, that.detail) &&
                Objects.equals(insertText, that.insertText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, kind, detail, insertText);
    }

    public static Builder builder(String label) {
        return new Builder(label);
    }

    public static class Builder {
        private final String label;
        private CompletionItemLabelDetails labelDetails;
        private CompletionItemKind kind = CompletionItemKind.TEXT;
        private List<CompletionItemTag> tags;
        private String detail;
        private MarkupContent documentation;
        private String sortText;
        private String filterText;
        private String insertText;
        private InsertTextFormat insertTextFormat = InsertTextFormat.PLAIN_TEXT;
        private TextEdit textEdit;
        private List<TextEdit> additionalTextEdits;
        private Object data;

        public Builder(String label) {
            this.label = label;
            this.insertText = label;
            this.sortText = label;
            this.filterText = label;
        }

        public Builder setLabelDetails(CompletionItemLabelDetails labelDetails) {
            this.labelDetails = labelDetails;
            return this;
        }

        public Builder setKind(CompletionItemKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder setTags(List<CompletionItemTag> tags) {
            this.tags = tags;
            return this;
        }

        public Builder setDetail(String detail) {
            this.detail = detail;
            return this;
        }

        public Builder setDocumentation(MarkupContent documentation) {
            this.documentation = documentation;
            return this;
        }

        public Builder setDocumentation(String markdown) {
            this.documentation = MarkupContent.markdown(markdown);
            return this;
        }

        public Builder setSortText(String sortText) {
            this.sortText = sortText;
            return this;
        }

        public Builder setFilterText(String filterText) {
            this.filterText = filterText;
            return this;
        }

        public Builder setInsertText(String insertText) {
            this.insertText = insertText;
            return this;
        }

        public Builder setInsertTextFormat(InsertTextFormat insertTextFormat) {
            this.insertTextFormat = insertTextFormat;
            return this;
        }

        public Builder setTextEdit(TextEdit textEdit) {
            this.textEdit = textEdit;
            return this;
        }

        public Builder setAdditionalTextEdits(List<TextEdit> additionalTextEdits) {
            this.additionalTextEdits = additionalTextEdits;
            return this;
        }

        public Builder setData(Object data) {
            this.data = data;
            return this;
        }

        public CompletionItem build() {
            return new CompletionItem(label, labelDetails, kind, tags, detail, documentation,
                    sortText, filterText, insertText, insertTextFormat, textEdit, additionalTextEdits, data);
        }
    }
}

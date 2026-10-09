package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A CodeAction represents a change that can be performed in code (e.g. quickfix, refactor).
 * Matches LSP 3.17 CodeAction specification.
 */
public final class CodeAction {

    public final String title;
    public final String kind;
    public final List<Diagnostic> diagnostics;
    public final boolean isPreferred;
    public final WorkspaceEdit edit;
    public final Command command;
    public final Object data;

    public CodeAction(String title,
                      String kind,
                      List<Diagnostic> diagnostics,
                      boolean isPreferred,
                      WorkspaceEdit edit,
                      Command command,
                      Object data) {
        this.title = title != null ? title : "";
        this.kind = kind != null ? kind : CodeActionKind.QUICKFIX;
        this.diagnostics = diagnostics != null ? Collections.unmodifiableList(new ArrayList<>(diagnostics)) : Collections.emptyList();
        this.isPreferred = isPreferred;
        this.edit = edit;
        this.command = command;
        this.data = data;
    }

    public CodeAction(String title, String kind, WorkspaceEdit edit) {
        this(title, kind, null, false, edit, null, null);
    }

    public CodeAction(String title, WorkspaceEdit edit) {
        this(title, CodeActionKind.QUICKFIX, null, true, edit, null, null);
    }

    @NonNull
    @Override
    public String toString() {
        return "CodeAction{" + title + " (" + kind + ")}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodeAction)) return false;
        CodeAction that = (CodeAction) o;
        return isPreferred == that.isPreferred &&
                Objects.equals(title, that.title) &&
                Objects.equals(kind, that.kind) &&
                Objects.equals(diagnostics, that.diagnostics) &&
                Objects.equals(edit, that.edit) &&
                Objects.equals(command, that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, kind, diagnostics, isPreferred, edit, command);
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public static class Builder {
        private final String title;
        private String kind = CodeActionKind.QUICKFIX;
        private List<Diagnostic> diagnostics;
        private boolean isPreferred;
        private WorkspaceEdit edit;
        private Command command;
        private Object data;

        public Builder(String title) {
            this.title = title;
        }

        public Builder setKind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder setDiagnostics(List<Diagnostic> diagnostics) {
            this.diagnostics = diagnostics;
            return this;
        }

        public Builder setPreferred(boolean preferred) {
            isPreferred = preferred;
            return this;
        }

        public Builder setEdit(WorkspaceEdit edit) {
            this.edit = edit;
            return this;
        }

        public Builder setCommand(Command command) {
            this.command = command;
            return this;
        }

        public Builder setData(Object data) {
            this.data = data;
            return this;
        }

        public CodeAction build() {
            return new CodeAction(title, kind, diagnostics, isPreferred, edit, command, data);
        }
    }
}

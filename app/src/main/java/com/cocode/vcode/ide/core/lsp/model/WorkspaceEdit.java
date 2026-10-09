package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A workspace edit represents changes to many resources managed in the workspace.
 * Matches LSP 3.17 WorkspaceEdit specification.
 */
public final class WorkspaceEdit {

    /**
     * Map of URI -> list of text edits to apply across files atomically.
     */
    public final Map<String, List<TextEdit>> changes;

    public WorkspaceEdit() {
        this.changes = new HashMap<>();
    }

    public WorkspaceEdit(Map<String, List<TextEdit>> changes) {
        this.changes = changes != null ? new HashMap<>(changes) : new HashMap<>();
    }

    public void addEdit(String uri, TextEdit edit) {
        if (uri == null || edit == null) return;
        List<TextEdit> edits = changes.get(uri);
        if (edits == null) {
            edits = new ArrayList<>();
            changes.put(uri, edits);
        }
        edits.add(edit);
    }

    public List<TextEdit> getEditsForUri(String uri) {
        if (uri == null) return Collections.emptyList();
        List<TextEdit> edits = changes.get(uri);
        return edits != null ? Collections.unmodifiableList(edits) : Collections.emptyList();
    }

    public boolean isEmpty() {
        if (changes.isEmpty()) return true;
        for (List<TextEdit> list : changes.values()) {
            if (list != null && !list.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    @Override
    public String toString() {
        return "WorkspaceEdit{changes=" + changes + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceEdit)) return false;
        WorkspaceEdit that = (WorkspaceEdit) o;
        return Objects.equals(changes, that.changes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(changes);
    }
}

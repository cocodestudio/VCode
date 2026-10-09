package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains additional diagnostic information about the context in which a code action is run.
 * Matches LSP 3.17 CodeActionContext.
 */
public final class CodeActionContext {

    public final List<Diagnostic> diagnostics;
    public final List<String> only;

    public CodeActionContext(List<Diagnostic> diagnostics, List<String> only) {
        this.diagnostics = diagnostics != null ? Collections.unmodifiableList(new ArrayList<>(diagnostics)) : Collections.emptyList();
        this.only = only != null ? Collections.unmodifiableList(new ArrayList<>(only)) : Collections.emptyList();
    }

    public CodeActionContext(List<Diagnostic> diagnostics) {
        this(diagnostics, null);
    }

    @NonNull
    @Override
    public String toString() {
        return "CodeActionContext{diagnostics=" + diagnostics.size() + ", only=" + only + '}';
    }
}

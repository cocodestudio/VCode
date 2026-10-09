package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Contains additional information about how completions were triggered.
 * Matches LSP 3.17 CompletionContext.
 */
public final class CompletionContext {

    public final CompletionTriggerKind triggerKind;
    public final String triggerCharacter;

    public CompletionContext(CompletionTriggerKind triggerKind, String triggerCharacter) {
        this.triggerKind = triggerKind != null ? triggerKind : CompletionTriggerKind.INVOKED;
        this.triggerCharacter = triggerCharacter;
    }

    public CompletionContext(CompletionTriggerKind triggerKind) {
        this(triggerKind, null);
    }

    public static final CompletionContext DEFAULT = new CompletionContext(CompletionTriggerKind.INVOKED);

    @NonNull
    @Override
    public String toString() {
        return "CompletionContext{" + triggerKind + (triggerCharacter != null ? ", char='" + triggerCharacter + "'" : "") + '}';
    }
}

package com.cocode.vcode.ide.core.lsp.model;

/**
 * How a completion was triggered matching LSP 3.17 CompletionTriggerKind.
 */
public enum CompletionTriggerKind {
    /**
     * Completion was triggered by typing an identifier (24x7 code complete),
     * manual invocation (e.g. Ctrl+Space) or via API.
     */
    INVOKED(1),

    /**
     * Completion was triggered by a trigger character specified by the
     * `triggerCharacters` properties of the `CompletionRegistrationOptions`.
     */
    TRIGGER_CHARACTER(2),

    /**
     * Completion was re-triggered as the current completion list is incomplete.
     */
    TRIGGER_FOR_INCOMPLETE_COMPLETIONS(3);

    private final int value;

    CompletionTriggerKind(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static CompletionTriggerKind fromValue(int value) {
        switch (value) {
            case 1: return INVOKED;
            case 2: return TRIGGER_CHARACTER;
            case 3: return TRIGGER_FOR_INCOMPLETE_COMPLETIONS;
            default: return INVOKED;
        }
    }
}

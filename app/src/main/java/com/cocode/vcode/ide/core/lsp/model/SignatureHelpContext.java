package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

/**
 * Additional information about the context in which a signature help request was triggered.
 * Matches LSP 3.17 SignatureHelpContext.
 */
public final class SignatureHelpContext {

    public static final int INVOKED = 1;
    public static final int TRIGGER_CHARACTER = 2;
    public static final int CONTENT_CHANGE = 3;

    public final int triggerKind;
    public final String triggerCharacter;
    public final boolean isRetrigger;
    public final SignatureHelp activeSignatureHelp;

    public SignatureHelpContext(int triggerKind,
                                String triggerCharacter,
                                boolean isRetrigger,
                                SignatureHelp activeSignatureHelp) {
        this.triggerKind = triggerKind;
        this.triggerCharacter = triggerCharacter;
        this.isRetrigger = isRetrigger;
        this.activeSignatureHelp = activeSignatureHelp;
    }

    public static final SignatureHelpContext DEFAULT =
            new SignatureHelpContext(INVOKED, null, false, null);

    @NonNull
    @Override
    public String toString() {
        return "SignatureHelpContext{kind=" + triggerKind + (triggerCharacter != null ? ", char='" + triggerCharacter + "'" : "") + '}';
    }
}

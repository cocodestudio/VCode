package com.cocode.vcode.ide.views;

import android.content.Context;
import android.view.View;

import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;

/**
 * Adapter and backward-compatible wrapper for {@link DocumentationPopup} operating in Signature mode.
 */
public class SignatureHintPopup {

    private final DocumentationPopup delegate;

    public SignatureHintPopup(Context context) {
        this(new DocumentationPopup(context));
    }

    public SignatureHintPopup(DocumentationPopup delegate) {
        this.delegate = delegate;
    }

    /**
     * Extracts only the simple method or function name from a signature prefix string,
     * stripping any receiver objects, chaining dots (e.g. "a.b.c.foo"), "new" keywords,
     * generic type arguments, and leading dots.
     *
     * @param prefix the text preceding '(' in a signature label, or the full label if no '(' exists
     * @return the isolated method name
     */
    public static String extractMethodName(String prefix) {
        return DocumentationPopup.extractMethodName(prefix);
    }

    public void show(LspSignatureHelp help, View editorView, int cursorOffset) {
        if (delegate != null) {
            delegate.showSignature(help, editorView, cursorOffset);
        }
    }

    public void dismiss() {
        if (delegate != null) {
            delegate.dismiss();
        }
    }

    public boolean isShowing() {
        return delegate != null && delegate.isSignatureMode();
    }

    public DocumentationPopup getDelegate() {
        return delegate;
    }
}

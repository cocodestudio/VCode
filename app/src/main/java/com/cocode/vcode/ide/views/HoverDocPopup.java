package com.cocode.vcode.ide.views;

import android.content.Context;
import android.view.View;

import com.cocode.vcode.ide.core.lsp.HoverInfo;

/**
 * Adapter and backward-compatible wrapper for {@link DocumentationPopup} operating in Hover mode.
 */
public class HoverDocPopup {

    private final DocumentationPopup delegate;

    public HoverDocPopup(Context context) {
        this(new DocumentationPopup(context));
    }

    public HoverDocPopup(DocumentationPopup delegate) {
        this.delegate = delegate;
    }

    public void show(HoverInfo info, View anchor, int screenX, int screenY) {
        if (delegate != null) {
            delegate.showHover(info, anchor, screenX, screenY);
        }
    }

    public void dismiss() {
        if (delegate != null) {
            delegate.dismiss();
        }
    }

    public boolean isShowing() {
        return delegate != null && delegate.isHoverMode();
    }

    public DocumentationPopup getDelegate() {
        return delegate;
    }
}

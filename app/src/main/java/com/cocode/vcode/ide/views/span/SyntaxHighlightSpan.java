package com.cocode.vcode.ide.views.span;

import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;

/**
 * Character formatting span that applies syntax highlighting colors and optional underline styles to tokens.
 */
public class SyntaxHighlightSpan extends ForegroundColorSpan {

    private final boolean underline;

    public SyntaxHighlightSpan(int color, boolean underline) {
        super(color);
        this.underline = underline;
    }

    @Override
    public void updateDrawState(@NonNull android.text.TextPaint ds) {
        if (getForegroundColor() != 0) {
            super.updateDrawState(ds);
        }
        if (underline) {
            ds.setUnderlineText(true);
        }
    }
}
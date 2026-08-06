package com.cocode.vcode.ide.views.span;

import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;

/**
 * Custom character text markup formatting style span.
 * Inherits from standard platform ForegroundColorSpan types to tag and paint parsed
 * programming tokens within editable code views safely.
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
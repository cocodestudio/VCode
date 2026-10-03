package com.cocode.vcode.ide.views.span;

import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;

/**
 * Character formatting span that applies syntax highlighting colors and optional underline styles to tokens.
 * Supports reconfiguration for instance pooling via {@link com.cocode.vcode.ide.core.editor.text.SpanPool}.
 */
public class SyntaxHighlightSpan extends ForegroundColorSpan {

    private int color;
    private boolean underline;

    public SyntaxHighlightSpan(int color, boolean underline) {
        super(color);
        this.color = color;
        this.underline = underline;
    }

    public void reconfigure(int color, boolean underline) {
        this.color = color;
        this.underline = underline;
    }

    @Override
    public int getForegroundColor() {
        return color;
    }

    public boolean isUnderline() {
        return underline;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        if (color != 0) {
            ds.setColor(color);
        }
        if (underline) {
            ds.setUnderlineText(true);
        }
    }
}
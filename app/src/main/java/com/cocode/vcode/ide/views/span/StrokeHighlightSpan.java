package com.cocode.vcode.ide.views.span;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Spanned;
import android.text.style.LineBackgroundSpan;

import androidx.annotation.NonNull;

/**
 * A LineBackgroundSpan that draws a stroke (border outline) around the matched text.
 * Unlike ReplacementSpan, this preserves the inner syntax highlighting of the text
 * because it only draws behind the text and lets standard text rendering happen normally.
 */
public class StrokeHighlightSpan implements LineBackgroundSpan {
    private final Paint borderPaint;

    public StrokeHighlightSpan(int borderColor) {
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f); // Bold enough to see, not too thick
        borderPaint.setColor(borderColor);
    }

    @Override
    public void drawBackground(@NonNull Canvas canvas, @NonNull Paint paint,
                               int left, int right, int top, int baseline, int bottom,
                               @NonNull CharSequence text, int start, int end, int lnum) {

        if (!(text instanceof Spanned)) return;
        Spanned spanned = (Spanned) text;

        int spanStart = spanned.getSpanStart(this);
        int spanEnd = spanned.getSpanEnd(this);

        // If the span is not on this line, or invalid, ignore
        if (spanStart < 0 || spanEnd < 0 || spanStart >= end || spanEnd <= start) {
            return;
        }

        // Calculate the intersection of the span and the current line
        int drawStart = Math.max(spanStart, start);
        int drawEnd = Math.min(spanEnd, end);

        // We need to measure the text X coordinates. 
        // We can use paint.measureText to find the offset.
        float startX = left + paint.measureText(text, start, drawStart);
        float width = paint.measureText(text, drawStart, drawEnd);

        // Draw the border box
        canvas.drawRect(startX + 0.5f, top + 1f, startX + width - 0.5f, bottom - 1f, borderPaint);
    }
}

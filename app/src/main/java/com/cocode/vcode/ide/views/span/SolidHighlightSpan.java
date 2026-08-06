package com.cocode.vcode.ide.views.span;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Spanned;
import android.text.style.LineBackgroundSpan;

import androidx.annotation.NonNull;

/**
 * A LineBackgroundSpan that draws a solid background rectangle behind the matched text.
 * Unlike ReplacementSpan or BackgroundColorSpan, this ensures it draws purely in the background layer
 * and cleanly handles multiline edge cases without disrupting the primary text layout or syntax highlights.
 */
public class SolidHighlightSpan implements LineBackgroundSpan {
    private final Paint backgroundPaint;
    private final RectF rect = new RectF();

    public SolidHighlightSpan(int bgColor) {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(bgColor);
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
        float startX = left + paint.measureText(text, start, drawStart);
        float width = paint.measureText(text, drawStart, drawEnd);

        // Draw the background box with a slight corner radius for a modern IDE look
        rect.set(startX, top, startX + width, bottom);
        canvas.drawRoundRect(rect, 4f, 4f, backgroundPaint);
    }
}

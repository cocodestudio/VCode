package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Vertical line number gutter view for the code editor.
 * Draws line numbers aligned with the editor's visual lines and highlights the active line.
 */
public class LineNumberView extends View {

    private static final int DIVIDER_WIDTH_PX = 1;
    // Perf: reuse char buffer to avoid String alloc per line in draw loop
    private final char[] lineNumBuffer = new char[6];
    private Paint numberPaint;
    private Paint bgPaint;
    private Paint dividerPaint;
    private int gutterWidth = 0;
    private CodeEditText editor;
    private int cursorOffset = 0;
    // Perf: cache color lookups (ContextCompat.getColor is not free)
    private int colorPrimary;
    private int colorSecondary;
    private boolean colorsLoaded = false;

    public LineNumberView(Context context) {
        super(context);
        init();
    }

    public LineNumberView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineNumberView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(ContextCompat.getColor(getContext(), R.color.vcode_line_number_bg));

        numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        numberPaint.setTextSize(spToPx(13));
        numberPaint.setTextAlign(Paint.Align.RIGHT);

        dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(ContextCompat.getColor(getContext(), R.color.vcode_divider));
        dividerPaint.setStrokeWidth(DIVIDER_WIDTH_PX);

        // Pre-load colors once — ContextCompat.getColor() is non-trivial
        colorPrimary = ContextCompat.getColor(getContext(), R.color.vcode_text_primary);
        colorSecondary = ContextCompat.getColor(getContext(), R.color.vcode_line_number_text);
        colorsLoaded = true;
    }

    public void setCursorOffset(int cursorOffset) {
        if (this.cursorOffset != cursorOffset) {
            this.cursorOffset = cursorOffset;
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (editor == null) return;

        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        canvas.drawLine(getWidth() - DIVIDER_WIDTH_PX, 0,
                getWidth() - DIVIDER_WIDTH_PX, getHeight(), dividerPaint);

        int lineH = editor.getEditorLineHeight();
        if (lineH <= 0) return;

        int scrollY = editor.getScrollY();
        int firstLine = editor.getFirstVisibleLine();
        int lineCount = editor.getLogicalLineCount();
        int lastLine = Math.min(lineCount - 1, firstLine + getHeight() / lineH + 2);

        // Active line for highlighting — getCurrentLine() returns 1-indexed
        int activeLine = editor.getCurrentLine() - 1; // convert to 0-indexed

        float textX = getWidth() - DIVIDER_WIDTH_PX - dpToPx(4);

        // Obtain font metrics from numberPaint to align baseline exactly with editor text
        Paint.FontMetricsInt fm = new Paint.FontMetricsInt();
        numberPaint.getFontMetricsInt(fm);
        int ascent = fm.ascent;

        int _colorPrimary = colorsLoaded ? colorPrimary : ContextCompat.getColor(getContext(), R.color.vcode_text_primary);
        int _colorSecondary = colorsLoaded ? colorSecondary : ContextCompat.getColor(getContext(), R.color.vcode_line_number_text);

        for (int i = firstLine; i <= lastLine; i++) {
            int visualRow = editor.getVisualRowStart(i);
            float y = editor.getEditorPaddingTop() + (visualRow * lineH) - ascent - scrollY;

            boolean isActive = (i == activeLine);
            numberPaint.setColor(isActive ? _colorPrimary : _colorSecondary);
            int s = fillLineNum(i + 1, lineNumBuffer);
            canvas.drawText(lineNumBuffer, s, lineNumBuffer.length - s, textX, y, numberPaint);
        }
    }

    /**
     * Binds this line number view to a CodeEditText instance.
     */
    public void bindEditor(CodeEditText editor) {
        this.editor = editor;
        if (editor != null) {
            numberPaint.setTypeface(FontManager.getInstance().getCodeFont(getContext()));
        }
        invalidate();
    }

    /**
     * Writes an integer into a pre-allocated char[] from the right, returning the start index.
     * Avoids String.valueOf() + allocation per visible line in onDraw.
     */
    private int fillLineNum(int num, char[] buf) {
        int pos = buf.length;
        do {
            buf[--pos] = (char) ('0' + (num % 10));
            num /= 10;
        } while (num > 0);
        return pos;
    }

    public void setLineCount() {
    }

    public void setScrollY(int scrollY) {
        invalidate();
    }

    public void setLineHeight() {
    }

    /**
     * Updates the gutter width based on the total line count to accommodate the number of digits.
     */
    public void updateGutterWidth(int maxLines) {
        int digits = String.valueOf(maxLines).length();
        digits = Math.max(digits, 2); // Enforce a minimum 2-digit column width

        int newGutterWidth = (int) (numberPaint.measureText("0") * digits + dpToPx(8) * 2 + DIVIDER_WIDTH_PX);

        if (gutterWidth != newGutterWidth) {
            gutterWidth = newGutterWidth;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = gutterWidth > 0 ? gutterWidth : (int) dpToPx(40);
        setMeasuredDimension(w, MeasureSpec.getSize(heightMeasureSpec));
    }

    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getContext().getResources().getDisplayMetrics().scaledDensity;
    }
}
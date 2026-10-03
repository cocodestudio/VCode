package com.cocode.vcode.ide.core.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.util.List;

/**
 * Custom Android View that renders the terminal buffer and handles touch scrolling and soft keyboard input.
 * Implements strict viewport culling to avoid allocations and ensure 60fps on low-end devices.
 */
public class TerminalView extends View {

    private TerminalSession session;
    private Paint textPaint;
    private Paint bgPaint;
    private Paint cursorPaint;

    private float charWidth;
    private float lineHeight;
    private float fontAscent;

    private OverScroller scroller;
    private GestureDetector gestureDetector;
    private boolean autoScrollToBottom = true;

    private int defaultBgColor;
    private int defaultFgColor;
    private int cursorColor;

    public TerminalView(Context context) {
        super(context);
        init(context);
    }

    public TerminalView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TerminalView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setFocusable(true);
        setFocusableInTouchMode(true);

        defaultBgColor = ContextCompat.getColor(context, R.color.vcode_bg_surface);
        defaultFgColor = ContextCompat.getColor(context, R.color.vcode_text_primary);
        cursorColor = ContextCompat.getColor(context, R.color.vcode_accent_primary);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(FontManager.getInstance().getCodeFont(context));
        textPaint.setTextSize(spToPx(13));
        textPaint.setColor(defaultFgColor);

        bgPaint = new Paint();

        cursorPaint = new Paint();
        cursorPaint.setColor(cursorColor);

        recomputeFontMetrics();

        scroller = new OverScroller(context);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                int maxScrollY = getMaxScrollY();
                int newScrollY = Math.max(0, Math.min(getScrollY() + (int) distanceY, maxScrollY));
                scrollTo(0, newScrollY);
                autoScrollToBottom = (newScrollY >= maxScrollY);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                scroller.fling(0, getScrollY(), 0, (int) -velocityY, 0, 0, 0, getMaxScrollY());
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                performClick();
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }
                return true;
            }
        });
    }

    public void updateTheme() {
        Context context = getContext();
        defaultBgColor = ContextCompat.getColor(context, R.color.vcode_bg_surface);
        defaultFgColor = ContextCompat.getColor(context, R.color.vcode_text_primary);
        cursorColor = ContextCompat.getColor(context, R.color.vcode_accent_primary);
        cursorPaint.setColor(cursorColor);
        textPaint.setColor(defaultFgColor);
        if (session != null) {
            session.getEmulator().setDarkTheme(UiUtils.isNightMode(context));
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateColumns(w);
    }

    private void updateColumns(int width) {
        if (charWidth <= 0) {
            recomputeFontMetrics();
        }
        if (charWidth > 0 && width > 0) {
            float padding = getPaddingLeft() + getPaddingRight() + 24f;
            int cols = (int) ((width - padding) / charWidth);
            if (cols > 10 && session != null) {
                session.getEmulator().setColumns(cols);
            }
        }
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    public void showKeyboard() {
        requestFocus();
        post(() -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    @Override
    public boolean performClick() {
        super.performClick();
        showKeyboard();
        return true;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) {
            post(() -> {
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
        invalidate();
    }

    public void attachSession(@NonNull TerminalSession session) {
        this.session = session;
        if (getWidth() > 0) {
            updateColumns(getWidth());
        } else {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            updateColumns(screenWidth);
        }
        session.getEmulator().setDarkTheme(UiUtils.isNightMode(getContext()));
        session.setListener(new TerminalSession.TerminalSessionListener() {
            @Override
            public void onOutput() {
                if (autoScrollToBottom) {
                    scrollToBottom();
                }
                invalidate();
            }

            @Override
            public void onProcessTerminated(int exitCode) {
                invalidate();
            }
        });
        scrollToBottom();
        invalidate();
    }

    private void recomputeFontMetrics() {
        charWidth = textPaint.measureText("M");
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        lineHeight = fm.bottom - fm.top + 2;
        fontAscent = -fm.top;
    }

    public static int calculateColumnsForDisplay(Context context) {
        if (context == null) return TerminalEmulator.DEFAULT_COLUMNS;
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        Paint testPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        testPaint.setTypeface(FontManager.getInstance().getCodeFont(context));
        testPaint.setTextSize(13 * context.getResources().getDisplayMetrics().scaledDensity);
        float charW = testPaint.measureText("M");
        if (charW <= 0) return TerminalEmulator.DEFAULT_COLUMNS;
        float padding = 48f;
        int cols = (int) ((screenWidth - padding) / charW);
        return Math.max(20, cols);
    }

    private int getMaxScrollX() {
        return 0;
    }

    private int getMaxScrollY() {
        if (session == null) return 0;
        int lineCount = session.getBuffer().getLineCount();
        int totalHeight = (int) (lineCount * lineHeight) + getPaddingTop() + getPaddingBottom();
        return Math.max(0, totalHeight - getHeight());
    }

    public void scrollToBottom() {
        int maxScrollY = getMaxScrollY();
        scrollTo(0, maxScrollY);
        autoScrollToBottom = true;
        invalidate();
    }

    public void resetScroll() {
        scrollTo(0, 0);
        autoScrollToBottom = true;
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(0, scroller.getCurrY());
            autoScrollToBottom = (scroller.getCurrY() >= getMaxScrollY());
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean gestureHandled = gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP && !gestureHandled) {
            performClick();
        }
        return gestureHandled || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(defaultBgColor);

        if (session == null) return;

        TerminalBuffer buffer = session.getBuffer();
        int lineCount = buffer.getLineCount();
        if (lineCount == 0) return;

        int scrollY = getScrollY();
        int viewHeight = getHeight();

        int firstVisibleLine = Math.max(0, (int) (scrollY / lineHeight));
        int lastVisibleLine = Math.min(lineCount - 1, (int) ((scrollY + viewHeight) / lineHeight) + 1);

        float paddingLeft = getPaddingLeft() + 12f;
        float y = (firstVisibleLine * lineHeight) + fontAscent;

        for (int i = firstVisibleLine; i <= lastVisibleLine; i++) {
            TerminalLine line = buffer.getLine(i);
            String lineText = line.getText();
            List<TerminalLine.StyleRun> styles = line.getStyleRuns();

            if (styles == null || styles.isEmpty()) {
                textPaint.setColor(defaultFgColor);
                textPaint.setFakeBoldText(false);
                canvas.drawText(lineText, paddingLeft, y, textPaint);
            } else {
                int pos = 0;
                for (TerminalLine.StyleRun style : styles) {
                    if (style.start > pos && pos < lineText.length()) {
                        String plain = lineText.substring(pos, Math.min(style.start, lineText.length()));
                        textPaint.setColor(defaultFgColor);
                        textPaint.setFakeBoldText(false);
                        canvas.drawText(plain, paddingLeft + (pos * charWidth), y, textPaint);
                    }

                    int runEnd = Math.min(style.end, lineText.length());
                    if (style.start < runEnd) {
                        String chunk = lineText.substring(style.start, runEnd);

                        if (style.bgColor != 0) {
                            bgPaint.setColor(style.bgColor);
                            RectF bgRect = new RectF(
                                    paddingLeft + (style.start * charWidth),
                                    y - fontAscent,
                                    paddingLeft + (runEnd * charWidth),
                                    y - fontAscent + lineHeight
                            );
                            canvas.drawRect(bgRect, bgPaint);
                        }

                        int fgColor = (style.fgColor == TerminalEmulator.COLOR_DEFAULT_FG || style.fgColor == 0)
                                ? defaultFgColor
                                : style.fgColor;
                        textPaint.setColor(fgColor);
                        textPaint.setFakeBoldText(style.bold);
                        canvas.drawText(chunk, paddingLeft + (style.start * charWidth), y, textPaint);
                        pos = runEnd;
                    }
                }
                if (pos < lineText.length()) {
                    String remaining = lineText.substring(pos);
                    textPaint.setColor(defaultFgColor);
                    textPaint.setFakeBoldText(false);
                    canvas.drawText(remaining, paddingLeft + (pos * charWidth), y, textPaint);
                }
            }

            // Draw cursor on active line
            if (i == lineCount - 1) {
                int cursorCol = session.getEmulator().getCursorCol();
                float cursorX = paddingLeft + (cursorCol * charWidth);
                float cursorYTop = y - fontAscent + 2;
                cursorPaint.setAlpha(isFocused() ? 255 : 120);
                canvas.drawRect(cursorX, cursorYTop, cursorX + charWidth, cursorYTop + lineHeight - 4, cursorPaint);
            }

            y += lineHeight;
        }
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (session == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ENTER);
                return true;
            case KeyEvent.KEYCODE_DEL:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_BACKSPACE);
                return true;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_DELETE);
                return true;
            case KeyEvent.KEYCODE_TAB:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_TAB);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ARROW_UP);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ARROW_DOWN);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ARROW_LEFT);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ARROW_RIGHT);
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ESC);
                return true;
            case KeyEvent.KEYCODE_MOVE_HOME:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_HOME);
                return true;
            case KeyEvent.KEYCODE_MOVE_END:
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_END);
                return true;
            case KeyEvent.KEYCODE_PAGE_UP: {
                int scrollUp = Math.max(0, getScrollY() - (int) (getHeight() * 0.75f));
                scrollTo(0, scrollUp);
                autoScrollToBottom = false;
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_PAGE_DOWN: {
                int scrollDown = Math.min(getMaxScrollY(), getScrollY() + (int) (getHeight() * 0.75f));
                scrollTo(0, scrollDown);
                autoScrollToBottom = (scrollDown >= getMaxScrollY());
                invalidate();
                return true;
            }
        }

        if (event.isCtrlPressed()) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_C) {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_C);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_L) {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_L);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_D) {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_D);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_Z) {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_Z);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_A) {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_HOME);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_E) {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_END);
                return true;
            }

            char c = (char) event.getUnicodeChar();
            char lower = Character.toLowerCase(c);
            if (lower == 'c') {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_C);
                return true;
            } else if (lower == 'l') {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_L);
                return true;
            } else if (lower == 'd') {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_D);
                return true;
            } else if (lower == 'z') {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_Z);
                return true;
            } else if (lower == 'a') {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_HOME);
                return true;
            } else if (lower == 'e') {
                session.sendSpecialKey(TerminalSession.SPECIAL_KEY_END);
                return true;
            }
            return true;
        }

        if (!event.isCtrlPressed() && !event.isAltPressed()) {
            int unicode = event.getUnicodeChar();
            if (unicode >= 32) {
                session.handleInput(String.valueOf((char) unicode));
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleKeyEvent(event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        if (session != null && event != null && event.getCharacters() != null) {
            session.handleInput(event.getCharacters());
            return true;
        }
        return super.onKeyMultiple(keyCode, repeatCount, event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE
                | EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI;

        return new BaseInputConnection(this, false) {
            private String composingText = "";

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (session != null && text != null) {
                    int compLen = composingText.length();
                    for (int i = 0; i < compLen; i++) {
                        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_BACKSPACE);
                    }
                    composingText = "";
                    String str = text.toString();
                    if ("\n".equals(str) || "\r\n".equals(str)) {
                        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ENTER);
                    } else {
                        session.handleInput(str);
                    }
                }
                return true;
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                if (session != null && text != null) {
                    int compLen = composingText.length();
                    for (int i = 0; i < compLen; i++) {
                        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_BACKSPACE);
                    }
                    String newText = text.toString();
                    composingText = newText;
                    session.handleInput(newText);
                }
                return true;
            }

            @Override
            public boolean finishComposingText() {
                composingText = "";
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (session != null) {
                    composingText = "";
                    for (int i = 0; i < beforeLength; i++) {
                        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_BACKSPACE);
                    }
                    for (int i = 0; i < afterLength; i++) {
                        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_DELETE);
                    }
                }
                return true;
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                return deleteSurroundingText(beforeLength, afterLength);
            }

            @Override
            public boolean setSelection(int start, int end) {
                if (session != null) {
                    session.setInputCursorPos(start);
                }
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (handleKeyEvent(event)) {
                    return true;
                }
                return super.sendKeyEvent(event);
            }

            @Override
            public CharSequence getTextBeforeCursor(int n, int flags) {
                if (session != null) {
                    String input = session.getCurrentInput();
                    int cursorPos = session.getInputCursorPos();
                    if (input != null && !input.isEmpty() && cursorPos > 0) {
                        int start = Math.max(0, cursorPos - n);
                        return input.substring(start, cursorPos);
                    }
                }
                return "";
            }

            @Override
            public CharSequence getTextAfterCursor(int n, int flags) {
                if (session != null) {
                    String input = session.getCurrentInput();
                    int cursorPos = session.getInputCursorPos();
                    if (input != null && cursorPos < input.length()) {
                        int end = Math.min(input.length(), cursorPos + n);
                        return input.substring(cursorPos, end);
                    }
                }
                return "";
            }
        };
    }

    private float spToPx(float sp) {
        return sp * getContext().getResources().getDisplayMetrics().scaledDensity;
    }
}

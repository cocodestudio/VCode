package com.cocode.vcode.ide.views;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextWatcher;
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
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.indent.BracketMatcher;
import com.cocode.vcode.ide.core.editor.indent.IndentationEngine;
import com.cocode.vcode.ide.core.editor.text.Content;
import com.cocode.vcode.ide.core.editor.text.ContentChangeListener;
import com.cocode.vcode.ide.core.editor.text.ContentPosition;
import com.cocode.vcode.ide.core.editor.text.UndoStack;
import com.cocode.vcode.ide.core.language.base.SyntaxHighlighter;
import com.cocode.vcode.ide.core.language.css.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.css.CssSyntaxHighlighter;
import com.cocode.vcode.ide.core.language.html.HtmlAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxHighlighter;
import com.cocode.vcode.ide.core.language.html.HtmlTagParser;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.js.JsSyntaxHighlighter;
import com.cocode.vcode.ide.core.language.json.JsonAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.json.JsonSyntaxHighlighter;
import com.cocode.vcode.ide.core.language.markdown.MarkdownSyntaxHighlighter;
import com.cocode.vcode.ide.core.language.svg.SvgSyntaxHighlighter;
import com.cocode.vcode.ide.core.language.ts.TsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.ts.TsSyntaxHighlighter;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.core.model.SearchResult;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sora-editor-grade code editor View for the VCode Android IDE.
 *
 * <p>This class is a complete rewrite from {@code AppCompatEditText} to a plain
 * {@link android.view.View}. Text storage is delegated to the Phase-1 {@link Content}
 * line-based model; undo/redo uses the Phase-1 {@link UndoStack}. All rendering is
 * performed by custom {@link #onDraw(Canvas)} code that paints only the visible viewport
 * for performance on large files.
 *
 * <p>For compatibility with existing call-sites (Phase 6 will clean these up), the class
 * re-exposes the Android {@link TextWatcher} API as a shim delegating to
 * {@link ContentChangeListener} internally (AD-5). It also exposes the new coordinate
 * methods required by AD-2 and AD-4.
 */
public class CodeEditText extends View {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int VIEWPORT_BUFFER_LINES = 200;
    private static final long AUTOCOMPLETE_DELAY_MS = 100;

    /**
     * Characters that trigger a new completion context.
     */
    private static final String TRIGGER_CHARS = ".</:'\"@#!";
    // ── Selection handle drag state (Phase 4) ─────────────────────────────────
    private static final int HANDLE_DRAG_NONE = 0;
    private static final int HANDLE_DRAG_START = 1;
    private static final int HANDLE_DRAG_END = 2;
    // Debounced visual layout rebuild (avoids scroll jumps during flings)
    private static final long VISUAL_LAYOUT_DEBOUNCE_MS = 32; // ~2 frames
    // ── Phase-1 text model ────────────────────────────────────────────────────
    private final Content content = new Content();
    private final UndoStack undoStack = new UndoStack();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HtmlTagParser htmlTagParser = new HtmlTagParser();
    private final BracketMatcher bracketMatcher = new BracketMatcher();
    private final DirtyRangeTracker dirtyTracker = new DirtyRangeTracker();
    boolean autoCloseHtmlTags = true;
    // ── IME composing region ──────────────────────────────────────────────────
    int composingStart = -1;
    int composingEnd = -1;
    private boolean autoCloseQuotes = true;
    private boolean wordWrap = false;
    private int[] visualRowStarts;
    private int totalVisualRows;
    private boolean visualLayoutPending = false;
    private boolean isSettingSelectionFromIme = false;
    // ── Rendering state ───────────────────────────────────────────────────────
    private float charWidth;
    private final Runnable visualLayoutRunnable = () -> {
        visualLayoutPending = false;
        rebuildVisualLayout();
        requestLayout();
        invalidate();
    };
    private int lineHeightPx;
    private Paint textPaint;
    private Paint lineHighlightPaint;
    private Paint cursorPaint;
    private Paint selectionPaint;
    private Paint diagnosticPaint;
    private Paint searchMatchPaint;
    private Paint searchActivePaint;
    private Paint bracketHighlightPaint;
    private int longestLineLength;
    private boolean longestLineDirty = false;
    // ── Cached colors ─────────────────────────────────────────────────────────
    private int cachedErrorColor;
    private int cachedWarningColor;
    private int cachedInfoColor;
    private int cachedBracketHighlightColor;
    // ── Scrolling ─────────────────────────────────────────────────────────────
    private OverScroller overScroller;
    private GestureDetectorCompat gestureDetector;
    private OnScrollChangeListener scrollChangeListener;
    // ── Cursor / selection ────────────────────────────────────────────────────
    private ContentPosition cursor = ContentPosition.ZERO;
    private ContentPosition selectionAnchor = null; // null == no selection
    private boolean cursorVisible = true;
    // ── Selection change listener (Phase 4) ───────────────────────────────────
    private OnSelectionChangeListener selectionChangeListener;
    private int activeDragHandle = HANDLE_DRAG_NONE;
    // ── Handle paint (Phase 4, allocated once in init) ────────────────────────
    private Paint handlePaint;
    // ── File / syntax ─────────────────────────────────────────────────────────
    private FileType fileType = FileType.TEXT;
    private File currentFile;
    private SyntaxHighlighter syntaxHighlighter;
    private AutoCompleteEngine autoCompleteEngine;
    // ── State flags ───────────────────────────────────────────────────────────
    private boolean isAutoClosing = false;
    private boolean isApplyingHighlight = false;
    private boolean isUndoRedoActive = false;
    private boolean isSettingText = false;
    private boolean isTypingText = false;
    // ── Content change listeners ──────────────────────────────────────────────
    private final List<OnContentChangeListener> contentChangeListeners = new ArrayList<>();
    // ── Diagnostics ───────────────────────────────────────────────────────────
    private List<Problem> currentProblems = new ArrayList<>();
    private float lastSquiggleConfigHash = 0;
    // ── Settings ──────────────────────────────────────────────────────────────
    private boolean autoCloseBrackets = true;
    private boolean autoIndent = true;
    private IndentationEngine indentEngine;
    private final AutoCompletePopup autoCompletePopup;
    /**
     * When true, LSP is handling completions and the legacy engine is suppressed.
     */
    private boolean lspCompletionActive = false;
    // ── Highlight state ───────────────────────────────────────────────────────
    private final Runnable autoCompleteRunnable = this::triggerAutoComplete;
    /**
     * Default text colour, captured in {@code init()} for use in onDraw.
     */
    private int defaultTextColor;
    private int[] rainbowColors;
    // Line rendering buffers
    private int[] colorBuffer = new int[1024];
    private boolean[] underlineBuffer = new boolean[1024];
    private boolean[] previewBuffer = new boolean[1024];
    private int[] previewColorBuffer = new int[1024];
    private char[] lineBuffer = new char[1024];
    // ── Bracket match positions ───────────────────────────────────────────────
    private ContentPosition bracketMatchOpen = null;
    private ContentPosition bracketMatchClose = null;
    final Runnable bracketMatchRunnable = () -> {
        CharSequence text = new ContentCharSequence(content);
        int flatCursor = content.flatOffset(cursor);
        updateBracketMatch(text, flatCursor);
    };
    // ── Search decorations (Phase 6) ──────────────────────────────────────────
    private List<SearchResult> searchDecorations = new ArrayList<>();
    private int searchActiveIndex = -1;
    private volatile long textLoadToken = 0;

    public CodeEditText(Context context) {
        super(context);
        autoCompletePopup = new AutoCompletePopup(context);
        init(context);
    }    // ── Blink ─────────────────────────────────────────────────────────────────

    public CodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        autoCompletePopup = new AutoCompletePopup(context);
        init(context);
    }    private final Runnable blinkRunnable = () -> {
        cursorVisible = !cursorVisible;
        invalidate();
        scheduleBlink();
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────

    public CodeEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        autoCompletePopup = new AutoCompletePopup(context);
        init(context);
    }

    /**
     * Returns true if {@code ch} is a "word" character for selection purposes.
     */
    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init(Context context) {
        Typeface codeFont = FontManager.getInstance().getCodeFont(context);

        // Fix #4: Restore the editor background colour (previously inherited from AppCompatEditText).
        setBackgroundColor(ContextCompat.getColor(context, R.color.vcode_bg_surface));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(codeFont);
        textPaint.setTextSize(spToPx(14, context));
        defaultTextColor = ContextCompat.getColor(context, R.color.vcode_text_primary);
        textPaint.setColor(defaultTextColor);

        Paint.FontMetricsInt fm = textPaint.getFontMetricsInt();
        lineHeightPx = fm.descent - fm.ascent + 2;
        charWidth = textPaint.measureText("m");

        lineHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineHighlightPaint.setColor(ContextCompat.getColor(context, R.color.vcode_active_line_highlight));
        lineHighlightPaint.setStyle(Paint.Style.FILL);

        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setColor(ContextCompat.getColor(context, R.color.vcode_accent_primary));
        cursorPaint.setStrokeWidth(dpToPx(2, context));
        cursorPaint.setStyle(Paint.Style.STROKE);

        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(ContextCompat.getColor(context, R.color.vcode_selection_color));
        selectionPaint.setStyle(Paint.Style.FILL);

        diagnosticPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        diagnosticPaint.setStyle(Paint.Style.STROKE);
        diagnosticPaint.setStrokeWidth(3f);

        cachedErrorColor = ContextCompat.getColor(context, R.color.vcode_accent_error);
        cachedWarningColor = ContextCompat.getColor(context, R.color.vcode_accent_warning);
        cachedInfoColor = ContextCompat.getColor(context, R.color.vcode_accent_primary);
        cachedBracketHighlightColor = ContextCompat.getColor(context, R.color.vcode_bracket_match_bg);
        rainbowColors = new int[]{
                ContextCompat.getColor(context, R.color.vcode_rainbow_1),
                ContextCompat.getColor(context, R.color.vcode_rainbow_2),
                ContextCompat.getColor(context, R.color.vcode_rainbow_3),
                ContextCompat.getColor(context, R.color.vcode_rainbow_4)
        };

        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(ContextCompat.getColor(context, R.color.vcode_accent_primary));
        handlePaint.setStyle(Paint.Style.FILL);

        overScroller = new OverScroller(context);

        searchMatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        searchMatchPaint.setColor(0x55FFD700);
        searchMatchPaint.setStyle(Paint.Style.FILL);
        searchActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        searchActivePaint.setColor(0xAAFF9800);
        searchActivePaint.setStyle(Paint.Style.FILL);

        bracketHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bracketHighlightPaint.setStyle(Paint.Style.STROKE);
        bracketHighlightPaint.setStrokeWidth(3f);

        indentEngine = new IndentationEngine(new AppSettings().tabSize);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setLongClickable(true); // allow long-press just like EditText
        setVerticalScrollBarEnabled(true);
        setHorizontalScrollBarEnabled(true);
        // Fix #1: OVER_SCROLL_NEVER prevents the "rubber-band" effect that misaligns
        // the line-number gutter when content is shorter than the viewport.
        setOverScrollMode(View.OVER_SCROLL_NEVER);

        // GestureDetector for scroll, fling, long-press and double-tap
        gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2,
                                    float distanceX, float distanceY) {
                scrollBy((int) distanceX, (int) distanceY);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2,
                                   float velocityX, float velocityY) {
                int maxScrollX;
                int maxScrollY;
                if (wordWrap) {
                    // In word-wrap mode there is no horizontal scrolling and the vertical
                    // content height is based on visual rows, NOT logical line count.
                    // Using lineCount() here would massively underestimate the content
                    // height, causing the fling to snap back early (the scroll jump bug).
                    maxScrollX = 0;
                    int totalContentH = totalVisualRows * lineHeightPx + getPaddingTop() + getPaddingBottom();
                    maxScrollY = Math.max(0, totalContentH - getHeight());
                } else {
                    maxScrollX = Math.max(0, (int) (getLongestLineLength() * charWidth) - getWidth() + getPaddingRight());
                    maxScrollY = Math.max(0, content.lineCount() * lineHeightPx - getHeight() + getPaddingBottom());
                }
                overScroller.fling(getScrollX(), getScrollY(),
                        (int) -velocityX, (int) -velocityY,
                        0, maxScrollX, 0, maxScrollY,
                        0, 0);
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                cursor = touchToPosition(e.getX(), e.getY());
                selectionAnchor = null;
                // Clear stale composing region so that spacebar-slide and
                // backspace-slide start from the exact tapped position.
                composingStart = -1;
                composingEnd = -1;
                // Reset the blink cycle so the cursor is always immediately visible
                // after a tap. Without this, if the tap lands during the "off" phase
                // of the blink the cursor stays invisible until the next blink tick.
                cursorVisible = true;
                scheduleBlink();
                invalidate();
                mainHandler.removeCallbacks(bracketMatchRunnable);
                mainHandler.postDelayed(bracketMatchRunnable, 80);
                if (autoCompletePopup != null && !isTypingText) {
                    autoCompletePopup.dismiss();
                }
                // Always notify the IME of the new cursor position so it syncs
                // its internal state regardless of whether there was a selection.
                notifySelectionChanged();
                // Fix #2: Show the keyboard only on a confirmed tap (not on every ACTION_DOWN).
                // This prevents the IME from popping up when the user just wants to scroll.
                showKeyboard();
                return true;
            }

            /**
             * Long-press: select the word under the finger, identical to stock EditText.
             * Provides haptic feedback and raises the SelectionToolbar.
             */
            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                ContentPosition pressed = touchToPosition(e.getX(), e.getY());
                if (selectWordAt(pressed)) {
                    // Haptic feedback — same as native long-click
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    notifySelectionChanged();
                    invalidate();
                }
            }

            /**
             * Double-tap: select the word under the finger (matches stock EditText).
             */
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                ContentPosition pressed = touchToPosition(e.getX(), e.getY());
                if (selectWordAt(pressed)) {
                    notifySelectionChanged();
                    invalidate();
                }
                return true;
            }
        });
        // Ensure the GestureDetector recognises long-press
        gestureDetector.setIsLongpressEnabled(true);

        // Register ContentChangeListener for tracking longest line + Content change shim
        content.addChangeListener(new ContentChangeListener() {
            @Override
            public void onInsert(int line, int col, CharSequence inserted) {
                updateLongestLine(line);
                dirtyTracker.addEdit(content.flatOffset(new ContentPosition(line, col)), 0, inserted.length());
                scheduleHighlight();
                dispatchContentChanged();
                scheduleVisualLayoutRebuild();
            }

            @Override
            public void onDelete(int startLine, int startCol, int endLine, int endCol) {
                longestLineDirty = true;
                dirtyTracker.addEdit(content.flatOffset(new ContentPosition(startLine, startCol)), 1, 0);
                scheduleHighlight();
                dispatchContentChanged();
                scheduleVisualLayoutRebuild();
            }
        });

        autoCompletePopup.setOnItemSelectedListener(this::insertCompletion);

        // Start cursor blink
        scheduleBlink();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (wordWrap) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            int contentHeight = totalVisualRows * lineHeightPx + getPaddingTop() + getPaddingBottom();
            int h = Math.max(resolveSize(contentHeight, heightMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
            setMeasuredDimension(w, h);
            return;
        }

        int contentWidth = (int) (getLongestLineLength() * charWidth)
                + getPaddingLeft() + getPaddingRight() + (int) dpToPx(64, getContext());
        int contentHeight = content.lineCount() * lineHeightPx
                + getPaddingTop() + getPaddingBottom();

        int w = Math.max(resolveSize(contentWidth, widthMeasureSpec),
                MeasureSpec.getSize(widthMeasureSpec));
        int h = Math.max(resolveSize(contentHeight, heightMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(w, h);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Measure
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int scrollY = getScrollY();
        int scrollX = getScrollX();
        int viewH = getHeight();
        int viewW = getWidth();

        int firstVisualRow = scrollY / lineHeightPx;
        int lastVisualRow = (scrollY + viewH) / lineHeightPx + 1;
        int firstLine = visualRowToLogicalLine(firstVisualRow);
        int lastLine = visualRowToLogicalLine(lastVisualRow);
        lastLine = Math.min(content.lineCount() - 1, lastLine);

        float paddingLeft = getPaddingLeft();
        float paddingTop = getPaddingTop();

        // 1. Line highlight for cursor line
        if (isFocused()) {
            int cursorVisualRow = absoluteVisualRow(cursor.line, cursor.column);
            // keeping the logic intact but scrollY cancels out in standard view since scrolling is hardware based, wait, actually canvas translates are not used here, the views do translate. Wait, no, scrollY is used directly or view translates? In Android, scrollX and scrollY are handled by canvas translation. So y should be absolute Y.
            float rectTop = paddingTop + cursorVisualRow * lineHeightPx;
            canvas.drawRect(scrollX, rectTop, scrollX + viewW, rectTop + lineHeightPx, lineHighlightPaint);
        }

        // 2. Selection background
        drawSelection(canvas, firstLine, lastLine, paddingLeft, paddingTop);

        // 3. Search decorations background (Phase 6)
        drawSearchDecorations(canvas, firstLine, lastLine, paddingLeft, paddingTop);

        // 4. Text (token-coloured, char-array overload — no String allocation per line)
        textPaint.setStyle(Paint.Style.FILL);

        Paint.FontMetricsInt fm = textPaint.getFontMetricsInt();
        int ascent = fm.ascent;

        int charsPerRow = wordWrap ? Math.max(1, (int) ((getWidth() - getPaddingLeft() - getPaddingRight()) / charWidth)) : Integer.MAX_VALUE;

        for (int line = firstLine; line <= lastLine; line++) {
            int lineLen = content.lineLength(line);
            if (lineLen == 0) continue;

            int subRows = wordWrap ? Math.max(1, (int) Math.ceil((double) lineLen / charsPerRow)) : 1;

            com.cocode.vcode.ide.core.editor.text.ContentLine contentLine = content.getLine(line);
            if (contentLine.tokens == null && syntaxHighlighter != null) {
                int state = contentLine.getTokenizerStartState();
                int internalState = state & 0xFFFF;
                int depth = (state >>> 16) & 0xFFFF;
                contentLine.tokens = syntaxHighlighter.tokenizeLine(contentLine.toLineString(), line, internalState);
                BracketMatcher.applyRainbowBrackets(contentLine.tokens, contentLine.toLineString(), rainbowColors, depth);
            }
            List<HighlightToken> lineTokens = contentLine.tokens;

            for (int sr = 0; sr < subRows; sr++) {
                int srStart = sr * charsPerRow;
                int srEnd = Math.min(lineLen, srStart + charsPerRow);

                int startVisCol, endVisCol;
                if (wordWrap) {
                    startVisCol = srStart;
                    endVisCol = srEnd;
                } else {
                    startVisCol = Math.max(0, (int) (scrollX / charWidth) - 20);
                    endVisCol = Math.min(lineLen, (int) ((scrollX + viewW) / charWidth) + 20);
                }
                startVisCol = Math.max(srStart, startVisCol);
                endVisCol = Math.min(srEnd, endVisCol);

                int renderLen = endVisCol - startVisCol;
                if (renderLen <= 0) continue;

                if (lineBuffer == null || lineBuffer.length < renderLen) {
                    int newCap = Math.max(1024, renderLen * 2);
                    lineBuffer = new char[newCap];
                    colorBuffer = new int[newCap];
                    underlineBuffer = new boolean[newCap];
                    previewBuffer = new boolean[newCap];
                    previewColorBuffer = new int[newCap];
                }
                content.getLineChars(line, startVisCol, endVisCol, lineBuffer);

                int visualRow = visualRowOf(line) + sr;
                float x = paddingLeft + (wordWrap ? 0 : getCursorX(line, startVisCol));
                float baseY = paddingTop + (visualRow * lineHeightPx) - ascent;

                if (lineTokens == null || lineTokens.isEmpty()) {
                    textPaint.setColor(defaultTextColor);
                    canvas.drawText(lineBuffer, 0, renderLen, x, baseY, textPaint);
                } else {
                    Arrays.fill(colorBuffer, 0, renderLen, defaultTextColor);
                    Arrays.fill(underlineBuffer, 0, renderLen, false);
                    for (int i = 0; i < renderLen; i++) previewBuffer[i] = false;

                    // Apply tokens (later tokens overwrite earlier ones)
                    for (HighlightToken tok : lineTokens) {
                        int s = Math.max(0, Math.min(renderLen, tok.startCol - startVisCol));
                        int e = Math.max(0, Math.min(renderLen, tok.endCol - startVisCol));
                        for (int i = s; i < e; i++) {
                            if (tok.color != 0) {
                                colorBuffer[i] = tok.color;
                            }
                            if (tok.underline) {
                                underlineBuffer[i] = true;
                            }
                        }
                        if (tok.hasPreviewColor && s < renderLen && (tok.startCol >= startVisCol)) {
                            previewBuffer[s] = true;
                            previewColorBuffer[s] = tok.previewColor;
                        }
                    }

                    // Draw contiguous segments
                    int start = 0;
                    float accumulatedShift = 0;
                    while (start < renderLen) {
                        if (previewBuffer[start]) {
                            float circleRadius = charWidth * 0.45f;
                            float circleX = x + start * charWidth + accumulatedShift + circleRadius + charWidth * 0.05f;
                            float circleY = baseY + (textPaint.ascent() + textPaint.descent()) / 2f;

                            textPaint.setStyle(Paint.Style.FILL);
                            textPaint.setColor(previewColorBuffer[start]);
                            canvas.drawCircle(circleX, circleY, circleRadius, textPaint);

                            textPaint.setStyle(Paint.Style.STROKE);
                            textPaint.setColor(android.graphics.Color.argb(50, 128, 128, 128));
                            textPaint.setStrokeWidth(2f);
                            canvas.drawCircle(circleX, circleY, circleRadius, textPaint);
                            textPaint.setStyle(Paint.Style.FILL);

                            accumulatedShift += charWidth * 1.2f;
                        }

                        int c = colorBuffer[start];
                        boolean u = underlineBuffer[start];
                        int end = start + 1;
                        while (end < renderLen && !previewBuffer[end] && colorBuffer[end] == c && underlineBuffer[end] == u) {
                            end++;
                        }

                        float startX = x + start * charWidth + accumulatedShift;
                        textPaint.setColor(c);
                        canvas.drawText(lineBuffer, start, end - start, startX, baseY, textPaint);
                        if (u) {
                            float ux1 = x + end * charWidth + accumulatedShift;
                            float uy = baseY + 2;
                            textPaint.setStyle(Paint.Style.STROKE);
                            textPaint.setStrokeWidth(1f);
                            canvas.drawLine(startX, uy, ux1, uy, textPaint);
                            textPaint.setStyle(Paint.Style.FILL);
                        }
                        start = end;
                    }
                    // Restore paint state
                    textPaint.setColor(defaultTextColor);
                    textPaint.setStyle(Paint.Style.FILL);
                }
            }
        }

        // 5. Bracket match highlights
        drawBracketHighlights(canvas, paddingLeft, paddingTop);

        // 6. Cursor
        if (isFocused() && cursorVisible && selectionAnchor == null) {
            float cx = paddingLeft + getCursorX(cursor.line, cursor.column);
            int cursorVisualRow = absoluteVisualRow(cursor.line, cursor.column);
            float cy = paddingTop + cursorVisualRow * lineHeightPx;
            canvas.drawLine(cx, cy, cx, cy + lineHeightPx, cursorPaint);
        }

        // 7. Diagnostic squiggles
        drawDiagnostics(canvas, firstLine, lastLine, paddingLeft, paddingTop);

        // 8. Selection handles (drawn at start and end of selection)
        if (selectionAnchor != null) {
            drawSelectionHandle(canvas, ContentPosition.min(cursor, selectionAnchor),
                    paddingLeft, paddingTop, true);
            drawSelectionHandle(canvas, ContentPosition.max(cursor, selectionAnchor),
                    paddingLeft, paddingTop, false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw
    // ─────────────────────────────────────────────────────────────────────────

    private void drawSelection(Canvas canvas, int firstLine, int lastLine,
                               float paddingLeft, float paddingTop) {
        if (selectionAnchor == null) return;
        ContentPosition selStart = ContentPosition.min(cursor, selectionAnchor);
        ContentPosition selEnd = ContentPosition.max(cursor, selectionAnchor);

        for (int line = Math.max(firstLine, selStart.line); line <= Math.min(lastLine, selEnd.line); line++) {
            int lineLen = content.lineLength(line);
            int colStart = (line == selStart.line) ? selStart.column : 0;
            int colEnd = (line == selEnd.line) ? selEnd.column : lineLen;

            if (!wordWrap) {
                float x0 = paddingLeft + getCursorX(line, colStart);
                float x1 = paddingLeft + getCursorX(line, colEnd);
                float y0 = paddingTop + line * lineHeightPx;
                canvas.drawRect(x0, y0, x1, y0 + lineHeightPx, selectionPaint);
            } else {
                int charsPerRow = Math.max(1, (int) ((getWidth() - getPaddingLeft() - getPaddingRight()) / charWidth));
                int subRows = Math.max(1, (int) Math.ceil((double) lineLen / charsPerRow));
                for (int sr = 0; sr < subRows; sr++) {
                    int srStart = sr * charsPerRow;
                    int srEndRow = Math.min(lineLen, srStart + charsPerRow);
                    if (colEnd <= srStart || colStart >= srEndRow) continue;
                    int s = Math.max(colStart, srStart);
                    int e = Math.min(colEnd, srEndRow);
                    float x0 = paddingLeft + getCursorX(line, s);
                    float x1 = paddingLeft + getCursorX(line, e);
                    float y0 = paddingTop + (visualRowOf(line) + sr) * lineHeightPx;
                    canvas.drawRect(x0, y0, x1, y0 + lineHeightPx, selectionPaint);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void drawSearchDecorations(Canvas canvas, int firstLine, int lastLine,
                                       float paddingLeft, float paddingTop) {
        if (searchDecorations == null || searchDecorations.isEmpty()) return;

        int total = content.totalLength();
        for (int i = 0; i < searchDecorations.size(); i++) {
            SearchResult r = searchDecorations.get(i);
            if (r.absoluteStart < 0 || r.absoluteEnd > total || r.absoluteStart >= r.absoluteEnd)
                continue;

            ContentPosition startPos = content.positionAt(r.absoluteStart);
            ContentPosition endPos = content.positionAt(r.absoluteEnd);

            if (endPos.line < firstLine || startPos.line > lastLine) continue;

            Paint paint = (i == searchActiveIndex) ? searchActivePaint : searchMatchPaint;

            for (int line = Math.max(startPos.line, firstLine); line <= Math.min(endPos.line, lastLine); line++) {
                int lineLen = content.lineLength(line);
                int colStart = (line == startPos.line) ? startPos.column : 0;
                int colEnd = (line == endPos.line) ? endPos.column : lineLen;

                if (!wordWrap) {
                    float x0 = paddingLeft + getCursorX(line, colStart);
                    float x1 = paddingLeft + getCursorX(line, colEnd);
                    float y0 = paddingTop + line * lineHeightPx;
                    canvas.drawRect(x0, y0, x1, y0 + lineHeightPx, paint);
                } else {
                    int charsPerRow = Math.max(1, (int) ((getWidth() - getPaddingLeft() - getPaddingRight()) / charWidth));
                    int subRows = Math.max(1, (int) Math.ceil((double) lineLen / charsPerRow));
                    for (int sr = 0; sr < subRows; sr++) {
                        int srStart = sr * charsPerRow;
                        int srEndRow = Math.min(lineLen, srStart + charsPerRow);
                        if (colEnd <= srStart || colStart >= srEndRow) continue;
                        int s = Math.max(colStart, srStart);
                        int e = Math.min(colEnd, srEndRow);
                        float x0 = paddingLeft + getCursorX(line, s);
                        float x1 = paddingLeft + getCursorX(line, e);
                        float y0 = paddingTop + (visualRowOf(line) + sr) * lineHeightPx;
                        canvas.drawRect(x0, y0, x1, y0 + lineHeightPx, paint);
                    }
                }
            }
        }
    }

    private void drawBracketHighlights(Canvas canvas, float paddingLeft, float paddingTop) {
        // bracketHighlightPaint is allocated once in init() — no per-frame allocation
        bracketHighlightPaint.setColor(cachedBracketHighlightColor);

        if (bracketMatchOpen != null) {
            float x = paddingLeft + getCursorX(bracketMatchOpen.line, bracketMatchOpen.column);
            float y = paddingTop + absoluteVisualRow(bracketMatchOpen.line, bracketMatchOpen.column) * lineHeightPx;
            canvas.drawRect(x, y, x + charWidth, y + lineHeightPx, bracketHighlightPaint);
        }
        if (bracketMatchClose != null) {
            float x = paddingLeft + getCursorX(bracketMatchClose.line, bracketMatchClose.column);
            float y = paddingTop + absoluteVisualRow(bracketMatchClose.line, bracketMatchClose.column) * lineHeightPx;
            canvas.drawRect(x, y, x + charWidth, y + lineHeightPx, bracketHighlightPaint);
        }
    }

    /**
     * Draws Bézier-wave squiggly diagnostic underlines for visible problems.
     * Coordinates derived from charWidth / lineHeightPx (no Layout object needed).
     */
    private void drawDiagnostics(Canvas canvas, int firstLine, int lastLine,
                                 float paddingLeft, float paddingTop) {
        if (currentProblems == null || currentProblems.isEmpty()) return;

        int lineCount = content.lineCount();

        float configHash = charWidth + lineHeightPx + paddingLeft + paddingTop;
        if (configHash != lastSquiggleConfigHash) {
            lastSquiggleConfigHash = configHash;
            for (Problem p : currentProblems) p.setCachedPath(null);
        }

        for (Problem problem : currentProblems) {
            int lineIdx = problem.getLine() - 1;
            if (lineIdx < firstLine || lineIdx > lastLine) continue;
            if (lineIdx < 0 || lineIdx >= lineCount) continue;
            if (problem.getSeverity() == Problem.Severity.INFO) continue;

            int colStart = Math.max(0, problem.getColumn() - 1);
            int colEnd = colStart + Math.max(1, problem.getLength());
            int lineLen = content.lineLength(lineIdx);
            colEnd = Math.min(colEnd, lineLen);

            if (colStart >= colEnd && lineLen > 0) colEnd = colStart + 1;

            int color;
            if (problem.getSeverity() == Problem.Severity.ERROR)
                color = cachedErrorColor;
            else if (problem.getSeverity() == Problem.Severity.WARNING)
                color = cachedWarningColor;
            else
                color = cachedInfoColor;
            diagnosticPaint.setColor(color);

            if (problem.getCachedPath() == null || wordWrap) {
                float x0 = paddingLeft + getCursorX(lineIdx, colStart);
                float x1 = paddingLeft + getCursorX(lineIdx, colEnd);
                if (wordWrap && colEnd > colStart && colInSubRow(colEnd) < colInSubRow(colStart)) {
                    // problem spans multiple subrows, just draw for the first subrow
                    x1 = paddingLeft + getWidth() - getPaddingRight();
                }
                float waveY = paddingTop + absoluteVisualRow(lineIdx, colStart) * lineHeightPx + lineHeightPx + 2;
                float amp = 2.5f;
                float period = 8f;
                float half = period / 2f;

                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(x0, waveY);
                boolean up = true;
                for (float cx2 = x0; cx2 < x1; cx2 += half) {
                    float ex = Math.min(cx2 + half, x1);
                    float mid = (cx2 + ex) / 2f;
                    float ctlY = up ? waveY - amp : waveY + amp;
                    path.quadTo(mid, ctlY, ex, waveY);
                    up = !up;
                }
                problem.setCachedPath(path);
            }
            canvas.drawPath(problem.getCachedPath(), diagnosticPaint);
        }
    }

    private void drawSelectionHandle(Canvas canvas, ContentPosition pos,
                                     float paddingLeft, float paddingTop,
                                     boolean isStart) {
        float cx = paddingLeft + getCursorX(pos.line, pos.column);
        int vRow = absoluteVisualRow(pos.line, pos.column);
        float cy = paddingTop + vRow * lineHeightPx;
        float handleY = isStart ? cy : cy + lineHeightPx;

        // Draw the cursor line at this position
        canvas.drawLine(cx, cy, cx, cy + lineHeightPx, cursorPaint);
        // Draw a small filled circle as the drag handle
        canvas.drawCircle(cx, handleY, dpToPx(5, getContext()), handlePaint);
    }

    private int hitTestHandle(float touchX, float touchY) {
        if (selectionAnchor == null) return HANDLE_DRAG_NONE;
        float threshold = dpToPx(20, getContext());

        ContentPosition startPos = ContentPosition.min(cursor, selectionAnchor);
        ContentPosition endPos = ContentPosition.max(cursor, selectionAnchor);

        float startX = getPaddingLeft() + getCursorX(startPos.line, startPos.column);
        float startY = getPaddingTop() + absoluteVisualRow(startPos.line, startPos.column) * lineHeightPx;

        float endX = getPaddingLeft() + getCursorX(endPos.line, endPos.column);
        float endY = getPaddingTop() + (absoluteVisualRow(endPos.line, endPos.column) + 1) * lineHeightPx;

        float testX = touchX + (wordWrap ? 0 : getScrollX());
        float testY = touchY + getScrollY();

        if (Math.abs(testX - startX) < threshold && Math.abs(testY - startY) < threshold) {
            return HANDLE_DRAG_START;
        }
        if (Math.abs(testX - endX) < threshold && Math.abs(testY - endY) < threshold) {
            return HANDLE_DRAG_END;
        }
        return HANDLE_DRAG_NONE;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            // Check if touch hits a selection handle before anything else
            if (selectionAnchor != null) {
                int hitHandle = hitTestHandle(event.getX(), event.getY());
                if (hitHandle != HANDLE_DRAG_NONE) {
                    activeDragHandle = hitHandle;
                    return true;
                }
            }
            activeDragHandle = HANDLE_DRAG_NONE;

            requestFocus();
            overScroller.abortAnimation();
            // Fix #2: Do NOT call showKeyboard() here. It is called in onSingleTapUp() instead,
            // so a scroll gesture (ACTION_DOWN + MOVE) never pops the keyboard.
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (activeDragHandle != HANDLE_DRAG_NONE) {
                ContentPosition dragPos = touchToPosition(event.getX(), event.getY());
                if (activeDragHandle == HANDLE_DRAG_START) {
                    selectionAnchor = dragPos;
                } else {
                    cursor = dragPos;
                }
                invalidate();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            if (activeDragHandle != HANDLE_DRAG_NONE) {
                activeDragHandle = HANDLE_DRAG_NONE;
                notifySelectionChanged();
                return true;
            }
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        gestureDetector.onTouchEvent(event);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Touch / Scroll
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void computeScroll() {
        if (overScroller.computeScrollOffset()) {
            scrollTo(overScroller.getCurrX(), overScroller.getCurrY());
            postInvalidateOnAnimation();
        }
    }

    @Override
    public void scrollTo(int x, int y) {
        if (wordWrap) {
            int totalContentH = totalVisualRows * lineHeightPx + getPaddingTop() + getPaddingBottom();
            int maxScrollY = Math.max(0, totalContentH - getHeight());
            x = 0;
            y = Math.max(0, Math.min(y, maxScrollY));
            super.scrollTo(x, y);
            return;
        }
        // Full content dimensions (same formula as onMeasure)
        int totalContentH = content.lineCount() * lineHeightPx + getPaddingTop() + getPaddingBottom();
        int totalContentW = (int) (getLongestLineLength() * charWidth) + getPaddingLeft() + getPaddingRight();
        // Max scroll = content that overflows the viewport; 0 when content fits
        int maxScrollY = Math.max(0, totalContentH - getHeight());
        int maxScrollX = Math.max(0, totalContentW - getWidth());
        x = Math.max(0, Math.min(x, maxScrollX));
        y = Math.max(0, Math.min(y, maxScrollY));
        super.scrollTo(x, y);
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
        if (scrollChangeListener != null) scrollChangeListener.onScrollChanged(horiz, vert);
        mainHandler.removeCallbacksAndMessages(null);
        scheduleHighlight();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction,
                                  android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) {
            scheduleBlink();
            showKeyboard();
        } else {
            mainHandler.removeCallbacks(blinkRunnable);
            cursorVisible = true;
            if (autoCompletePopup != null) autoCompletePopup.dismiss();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Focus / Keyboard
    // ─────────────────────────────────────────────────────────────────────────

    private void scheduleBlink() {
        mainHandler.removeCallbacks(blinkRunnable);
        if (isFocused()) mainHandler.postDelayed(blinkRunnable, 500);
    }

    private void showKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildVisualLayout();
        if (h != oldh && oldh > 0) {
            post(this::ensureCursorVisible);
        }
    }

    public void ensureCursorVisible() {
        if (cursor == null || getHeight() <= 0 || getWidth() <= 0) return;

        // Vertical
        int cursorVisualRow = absoluteVisualRow(cursor.line, cursor.column);
        int cursorYTop = getPaddingTop() + cursorVisualRow * lineHeightPx;
        int cursorYBottom = cursorYTop + lineHeightPx;
        int currentScrollY = getScrollY();
        int viewHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        int bufferY = lineHeightPx;

        int newScrollY = currentScrollY;
        if (cursorYTop - bufferY < currentScrollY) {
            newScrollY = Math.max(0, cursorYTop - bufferY);
        } else if (cursorYBottom + bufferY > currentScrollY + viewHeight) {
            newScrollY = cursorYBottom + bufferY - viewHeight;
        }

        // Horizontal
        int newScrollX = getScrollX();
        if (!wordWrap) {
            int cursorXLeft = getPaddingLeft() + (int) (getCursorX(cursor.line, cursor.column));
            int cursorXRight = cursorXLeft + (int) charWidth;
            int currentScrollX = getScrollX();
            int viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            int bufferX = (int) (charWidth * 4);

            if (cursorXLeft - bufferX < currentScrollX) {
                newScrollX = Math.max(0, cursorXLeft - bufferX);
            } else if (cursorXRight + bufferX > currentScrollX + viewWidth) {
                newScrollX = cursorXRight + bufferX - viewWidth;
            }
        }

        if (newScrollX != getScrollX() || newScrollY != currentScrollY) {
            scrollTo(newScrollX, newScrollY);
        }
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        outAttrs.initialSelStart = getSelectionStart();
        outAttrs.initialSelEnd = getSelectionEnd();
        return new CodeInputConnection(this);
    }

    /**
     * Compatibility shim: sets whether the cursor blink is visible (used by read-only mode).
     */
    public void setCursorVisible(boolean visible) {
        this.cursorVisible = visible;
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isApplyingHighlight || isUndoRedoActive || isSettingText)
            return super.onKeyDown(keyCode, event);

        // Autocomplete keyboard navigation
        if (autoCompletePopup != null && autoCompletePopup.isShowing()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    autoCompletePopup.moveSelection(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    autoCompletePopup.moveSelection(-1);
                    return true;
                case KeyEvent.KEYCODE_TAB:
                case KeyEvent.KEYCODE_ENTER: {
                    CompletionItem selected = autoCompletePopup.getSelectedItem();
                    if (selected != null) {
                        insertCompletion(selected);
                        return true;
                    }
                    break;
                }
                case KeyEvent.KEYCODE_ESCAPE:
                    autoCompletePopup.dismiss();
                    return true;
            }
        }

        // DEL key → backspace (delete char before cursor)
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            performBackspace();
            return true;
        }

        // ENTER key → newline with auto-indent
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            performInsertText();
            return true;
        }

        // Tab key → insert spaces
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            int start = getSelectionStart();
            int end = getSelectionEnd();
            String spaces = buildTabSpaces();
            ContentPosition startPos = content.positionAt(start);
            ContentPosition endPos = content.positionAt(end);
            ContentPosition selAnchorBefore = selectionAnchor;
            ContentPosition beforeCursor = cursor;
            String deleted;
            try {
                deleted = content.getSubstring(start, end);
            } catch (Exception e) {
                deleted = "";
            }
            content.replace(startPos.line, startPos.column, endPos.line, endPos.column, spaces);
            ContentPosition newCursor = content.positionAt(start + spaces.length());
            if (!deleted.isEmpty()) {
                undoStack.recordReplace(startPos.line, startPos.column, endPos.line, endPos.column,
                        deleted, spaces, snapshotAt(beforeCursor, selAnchorBefore), snapshotAt(newCursor, null));
            } else {
                undoStack.recordInsert(startPos.line, startPos.column, spaces,
                        snapshotAt(startPos, null), snapshotAt(newCursor, null));
            }
            cursor = newCursor;
            selectionAnchor = null;
            invalidate();
            scheduleHighlight();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Key events
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            if (autoCompletePopup != null && autoCompletePopup.isShowing()) {
                autoCompletePopup.dismiss();
                return true;
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    /**
     * Performs a Backspace operation from the hardware keyboard.
     * Mirrors {@code CodeInputConnection.handleBackspace()} but is accessible from the outer class.
     */
    void performBackspace() {
        if (selectionAnchor != null) {
            // Delete selection
            int start = getSelectionStart();
            int end = getSelectionEnd();
            if (start != end) {
                ContentPosition startPos = content.positionAt(start);
                ContentPosition endPos = content.positionAt(end);
                ContentPosition selAnchorBefore = selectionAnchor;
                ContentPosition beforeCursor = cursor;
                String deleted;
                try {
                    deleted = content.getSubstring(start, end);
                } catch (Exception e) {
                    deleted = "";
                }
                content.delete(startPos.line, startPos.column, endPos.line, endPos.column);
                undoStack.recordDelete(startPos.line, startPos.column, endPos.line, endPos.column,
                        deleted, snapshotAt(beforeCursor, selAnchorBefore), snapshotAt(startPos, null));
                cursor = startPos;
                selectionAnchor = null;
                invalidate();
                scheduleHighlight();
                return;
            }
            selectionAnchor = null;
        }
        int cursorFlat = content.flatOffset(cursor);
        if (cursorFlat <= 0) return;
        ContentPosition newPos = content.positionAt(cursorFlat - 1);
        String deleted;
        if (newPos.column < content.lineLength(newPos.line)) {
            deleted = String.valueOf(content.charAt(newPos.line, newPos.column));
        } else {
            deleted = "\n";
        }
        ContentPosition oldCursor = cursor;
        content.delete(newPos.line, newPos.column, cursor.line, cursor.column);
        undoStack.recordDelete(newPos.line, newPos.column, oldCursor.line, oldCursor.column,
                deleted, snapshotAt(oldCursor, null), snapshotAt(newPos, null));
        cursor = newPos;
        selectionAnchor = null;
        invalidate();
        scheduleHighlight();
        mainHandler.removeCallbacks(bracketMatchRunnable);
        mainHandler.postDelayed(bracketMatchRunnable, 150);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hardware-keyboard helpers (called from onKeyDown)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inserts {@code text} at the current cursor position from the hardware keyboard.
     * Triggers auto-indent for {@code "\n"} and auto-close for single bracket characters.
     * Mirrors {@code CodeInputConnection.insertAtCursor()} but accessible from the outer class.
     */
    void performInsertText() {

        ContentPosition selAnchorBefore = selectionAnchor;
        ContentPosition beforeCursor = cursor;
        String deletedSel = "";
        ContentPosition delStart = null;
        ContentPosition delEnd = null;

        // Delete selection first if any
        if (selectionAnchor != null) {
            int start = getSelectionStart();
            int end = getSelectionEnd();
            if (start != end) {
                delStart = content.positionAt(start);
                delEnd = content.positionAt(end);
                try {
                    deletedSel = content.getSubstring(start, end);
                } catch (Exception e) {
                    deletedSel = "";
                }
                content.delete(delStart.line, delStart.column, delEnd.line, delEnd.column);
                cursor = delStart;
            }
            selectionAnchor = null;
        }

        int beforeFlat = content.flatOffset(cursor);
        ContentPosition insertStart = cursor;
        content.insert(cursor.line, cursor.column, "\n");
        // Advance cursor past inserted text
        ContentPosition after = cursor;
        for (int i = 0; i < "\n".length(); i++) {
            after = new ContentPosition(after.line + 1, 0);
        }
        if (!deletedSel.isEmpty()) {
            undoStack.recordReplace(delStart.line, delStart.column, delEnd.line, delEnd.column,
                    deletedSel, "\n", snapshotAt(beforeCursor, selAnchorBefore), snapshotAt(after, null));
        } else {
            undoStack.recordInsert(insertStart.line, insertStart.column, "\n",
                    snapshotAt(insertStart, null), snapshotAt(after, null));
        }
        cursor = after;
        selectionAnchor = null;
        // Side effects
        if (!isAutoClosing) {
            char typed = '\n';
            if (autoCloseBrackets) {
                handleAutoClose(new ContentCharSequence(content), beforeFlat + 1, typed);
            }
            int indentTextEnd = Math.min(content.totalLength(), beforeFlat + 2);
            handleAutoIndent(content.getSubstring(0, indentTextEnd), beforeFlat);
        }
        invalidate();
        scheduleHighlight();
        scheduleAutoComplete();
        mainHandler.removeCallbacks(bracketMatchRunnable);
        mainHandler.postDelayed(bracketMatchRunnable, 150);
    }

    /**
     * Returns a {@link CharSequence} view of the full document content.
     * {@code toString()} materialises the full text; {@code length()} is O(1) via Content.
     */
    public CharSequence getText() {
        return new ContentCharSequence(content);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — AD-1 / AD-7: getText()
    // ─────────────────────────────────────────────────────────────────────────

    public void setText(CharSequence text) {
        setText(text, null);
    }

    /**
     * Returns the document length in characters (O(1)).
     */
    public int length() {
        return content.totalLength();
    }

    /**
     * Materialises the full document text as a String.
     */
    public String getTextAsString() {
        return content.getText();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — selection
    // ─────────────────────────────────────────────────────────────────────────

    public int getSelectionStart() {
        if (selectionAnchor == null) return content.flatOffset(cursor);
        return content.flatOffset(ContentPosition.min(cursor, selectionAnchor));
    }

    public int getSelectionEnd() {
        if (selectionAnchor == null) return content.flatOffset(cursor);
        return content.flatOffset(ContentPosition.max(cursor, selectionAnchor));
    }

    /**
     * Called by {@link com.cocode.vcode.ide.core.lsp.LspEditorBridge} to suppress the
     * legacy autocomplete engine when an LSP server is available for the current language.
     * When {@code suppress} is true, {@link #triggerAutoComplete()} becomes a no-op and
     * all completions are delivered via {@link #showLspCompletions(java.util.List)}.
     */
    public void suppressLegacyAutoComplete(boolean suppress) {
        this.lspCompletionActive = suppress;
        if (!suppress && autoCompletePopup != null) {
            autoCompletePopup.dismiss();
        }
    }

    /**
     * Displays LSP-generated completion items in the autocomplete popup.
     * Called on the main thread by {@link com.cocode.vcode.ide.core.lsp.LspEditorBridge}
     * after the LSP server returns its result.
     *
     * @param items the completion items to show; must not be null or empty
     */
    public void showLspCompletions(java.util.List<CompletionItem> items) {
        if (autoCompletePopup == null || items == null || items.isEmpty()) return;
        autoCompletePopup.show(items, this, getSelectionStart());
    }

    /**
     * Dismisses the autocomplete popup if it is currently showing.
     * Called by {@link com.cocode.vcode.ide.core.lsp.LspEditorBridge} when the LSP
     * server returns an empty completion list for the current position.
     */
    public void dismissAutoCompletePopup() {
        if (autoCompletePopup != null) autoCompletePopup.dismiss();
    }

    public void setSelection(int index) {
        cursor = content.positionAt(Math.max(0, Math.min(index, content.totalLength())));
        selectionAnchor = null;
        invalidate();
        notifySelectionChanged();
    }

    public void setSelection(int start, int end) {
        if (start == end) {
            selectionAnchor = null;
            cursor = content.positionAt(Math.max(0, Math.min(start, content.totalLength())));
        } else {
            selectionAnchor = content.positionAt(Math.max(0, Math.min(start, content.totalLength())));
            cursor = content.positionAt(Math.min(end, content.totalLength()));
        }
        post(this::ensureCursorVisible);
        invalidate();
        notifySelectionChanged();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — Phase 4: selection actions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Selects all text in the document.
     */
    public void selectAll() {
        selectionAnchor = ContentPosition.ZERO;
        cursor = content.positionAt(content.totalLength());
        invalidate();
        notifySelectionChanged();
    }

    /**
     * Collapses the selection to the cursor position (clears selection without moving cursor).
     */
    public void collapseSelection() {
        selectionAnchor = null;
        invalidate();
        notifySelectionChanged();
    }

    /**
     * Cuts the current selection to the system clipboard.
     */
    public void cutSelection() {
        if (selectionAnchor == null) return;
        copySelectionToClipboard();
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start >= end) {
            selectionAnchor = null;
            invalidate();
            notifySelectionChanged();
            return;
        }
        ContentPosition startPos = content.positionAt(start);
        ContentPosition endPos = content.positionAt(end);
        ContentPosition selAnchorBefore = selectionAnchor;
        ContentPosition beforeCursor = cursor;
        String deleted;
        try {
            deleted = content.getSubstring(start, end);
        } catch (Exception e) {
            deleted = "";
        }
        content.delete(startPos.line, startPos.column, endPos.line, endPos.column);
        undoStack.recordDelete(startPos.line, startPos.column, endPos.line, endPos.column,
                deleted, snapshotAt(beforeCursor, selAnchorBefore), snapshotAt(startPos, null));
        cursor = startPos;
        selectionAnchor = null;
        invalidate();
        scheduleHighlight();
        notifySelectionChanged();
    }

    /**
     * Copies the current selection to the system clipboard (no document mutation).
     */
    public void copySelection() {
        copySelectionToClipboard();
        collapseSelection();
    }

    /**
     * Internal helper: copies selected text to clipboard without changing selection state.
     */
    private void copySelectionToClipboard() {
        if (selectionAnchor == null) return;
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start >= end) return;
        try {
            String selectedText = content.getSubstring(start, end);
            ClipboardManager clipboard = (ClipboardManager) getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("code", selectedText));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Pastes clipboard text at the current cursor position.
     */
    public void paste() {
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(getContext());
        if (text == null || text.length() == 0) return;
        String pasteText = text.toString();

        ContentPosition selAnchorBefore = selectionAnchor;
        ContentPosition beforeCursor = cursor;
        String deletedSel = "";
        ContentPosition delStart = null;
        ContentPosition delEnd = null;

        // Delete selection first if any (without touching the clipboard)
        if (selectionAnchor != null) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();
            if (selStart != selEnd) {
                delStart = content.positionAt(selStart);
                delEnd = content.positionAt(selEnd);
                try {
                    deletedSel = content.getSubstring(selStart, selEnd);
                } catch (Exception e) {
                    deletedSel = "";
                }
                content.delete(delStart.line, delStart.column, delEnd.line, delEnd.column);
                cursor = delStart;
            }
            selectionAnchor = null;
        }

        ContentPosition insertStart = cursor;
        content.insert(insertStart.line, insertStart.column, pasteText);
        ContentPosition newCursor = UndoStack.advancePosition(insertStart.line, insertStart.column, pasteText);

        if (!deletedSel.isEmpty()) {
            undoStack.recordReplace(delStart.line, delStart.column, delEnd.line, delEnd.column,
                    deletedSel, pasteText, snapshotAt(beforeCursor, selAnchorBefore), snapshotAt(newCursor, null));
        } else {
            undoStack.recordInsert(insertStart.line, insertStart.column, pasteText,
                    snapshotAt(insertStart, null), snapshotAt(newCursor, null));
        }
        cursor = newCursor;
        selectionAnchor = null;
        invalidate();
        scheduleHighlight();
        notifySelectionChanged();
    }

    /**
     * Notifies any registered selection-change observer (e.g. to show/hide the SelectionToolbar).
     * Called whenever the selection state changes.
     */
    private void notifySelectionChanged() {
        // Only show/hide the selection toolbar for genuine user-initiated selections
        // (long-press, double-tap, drag handles, selectAll).
        // IME-driven selections (backspace-slide, spacebar-slide) set
        // isSettingSelectionFromIme = true — skip the toolbar in that case.
        if (selectionChangeListener != null && !isSettingSelectionFromIme) {
            selectionChangeListener.onSelectionChanged(selectionAnchor != null);
        }
        mainHandler.removeCallbacks(bracketMatchRunnable);
        mainHandler.postDelayed(bracketMatchRunnable, 80);

        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && imm.isActive(this) && !isSettingSelectionFromIme) {
            imm.updateSelection(this, getSelectionStart(), getSelectionEnd(), composingStart, composingEnd);
        }
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }

    public void setText(CharSequence text, Object bufferType) {
        if (autoCompletePopup != null) autoCompletePopup.dismiss();
        final String textStr = text != null ? text.toString() : "";
        final long myToken = ++textLoadToken;
        isSettingText = true;
        ExecutorProvider.getInstance().runOnCpu(() -> {
            Content.LoadedLines loaded = Content.prepareLoad(textStr);
            mainHandler.post(() -> {
                if (myToken != textLoadToken) return;
                content.applyLoaded(loaded);
                cursor = ContentPosition.ZERO;
                selectionAnchor = null;
                undoStack.reset();
                longestLineLength = loaded.longestLineLength;
                longestLineDirty = false;
                isSettingText = false;
                dirtyTracker.reset();
                dirtyTracker.addEdit(0, 0, content.totalLength());
                rebuildVisualLayout();
                requestLayout();
                invalidate();
                scheduleHighlight();
                notifySelectionChanged();
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — setText / setTextSize / getTypeface / getCurrentTextColor
    // ─────────────────────────────────────────────────────────────────────────

    public void setTextSize(float sizeSp) {
        textPaint.setTextSize(spToPx(sizeSp, getContext()));
        Paint.FontMetricsInt fm = textPaint.getFontMetricsInt();
        lineHeightPx = fm.descent - fm.ascent + 2;
        charWidth = textPaint.measureText("m");
        requestLayout();
        invalidate();
    }

    public Typeface getTypeface() {
        return textPaint.getTypeface();
    }

    public int getCurrentTextColor() {
        return textPaint.getColor();
    }

    public void addContentChangeListener(OnContentChangeListener listener) {
        if (listener != null && !contentChangeListeners.contains(listener))
            contentChangeListeners.add(listener);
    }

    public void removeContentChangeListener(OnContentChangeListener listener) {
        contentChangeListeners.remove(listener);
    }

    private void dispatchContentChanged() {
        if (isApplyingHighlight || isUndoRedoActive || isSettingText) return;
        for (OnContentChangeListener listener : contentChangeListeners) {
            listener.onContentChanged();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — AD-5: ContentChangeListener shim (Replaced TextWatcher to prevent lag)
    // ─────────────────────────────────────────────────────────────────────────

    public int getEditorLineHeight() {
        return lineHeightPx;
    }

    public int getFirstVisibleLine() {
        return visualRowToLogicalLine(getScrollY() / lineHeightPx);
    }

    public int getLogicalLineCount() {
        return content.lineCount();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — AD-4: LineNumberView helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the top padding of the editor in pixels — used by LineNumberView to align baselines.
     */
    public int getEditorPaddingTop() {
        return getPaddingTop();
    }

    public int[] getCursorScreenCoords(int flatOffset) {
        ContentPosition pos = content.positionAt(flatOffset);
        int[] loc = new int[2];
        getLocationInWindow(loc);
        int screenX = loc[0] + (int) (getPaddingLeft() + getCursorX(pos.line, pos.column)) - (wordWrap ? 0 : getScrollX());
        int screenYTop = loc[1] + getPaddingTop() + absoluteVisualRow(pos.line, pos.column) * lineHeightPx - getScrollY();
        int screenYBottom = screenYTop + lineHeightPx;
        return new int[]{screenX, screenYTop, screenYBottom};
    }

    public void replaceRange(int absoluteStart, int absoluteEnd, String replacement) {
        ContentPosition start = content.positionAt(absoluteStart);
        ContentPosition end = content.positionAt(absoluteEnd);
        content.replace(start.line, start.column, end.line, end.column, replacement);
        cursor = content.positionAt(absoluteStart + (replacement != null ? replacement.length() : 0));
        selectionAnchor = null;
        invalidate();
    }

    public void setSearchDecorations(List<SearchResult> results, int activeIndex) {
        this.searchDecorations = results != null ? results : new ArrayList<>();
        this.searchActiveIndex = activeIndex;
        invalidate();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — AD-2: getCursorScreenCoords
    // ─────────────────────────────────────────────────────────────────────────

    public void clearSearchDecorations() {
        searchDecorations.clear();
        searchActiveIndex = -1;
        invalidate();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — AD-3: replaceRange / search decorations / scrollToOffset
    // ─────────────────────────────────────────────────────────────────────────

    public void scrollToOffset(int flatOffset) {
        ContentPosition pos = content.positionAt(flatOffset);
        int targetY = pos.line * lineHeightPx;
        scrollTo(0, Math.max(0, targetY - getHeight() / 3));
    }

    public void goToLine(int line) {
        int targetLine = Math.max(0, Math.min(line - 1, content.lineCount() - 1));
        cursor = new ContentPosition(targetLine, 0);
        selectionAnchor = null;
        int targetScrollY = targetLine * lineHeightPx;
        scrollTo(0, Math.max(0, targetScrollY - getHeight() / 3));
        invalidate();
    }

    public int getLineCount() {
        return content.lineCount();
    }

    public int getCurrentLine() {
        return cursor.line + 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — goToLine
    // ─────────────────────────────────────────────────────────────────────────

    public void setOnScrollChangeListener(OnScrollChangeListener listener) {
        this.scrollChangeListener = listener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — line count / current line (CodeEditorLayout compat)
    // ─────────────────────────────────────────────────────────────────────────

    public void applyDiagnostics(List<Problem> problems) {
        this.currentProblems = problems != null ? problems : new ArrayList<>();
        invalidate();
    }

    public FileType getFileType() {
        return fileType;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — scroll listener
    // ─────────────────────────────────────────────────────────────────────────

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
        if (fileType != null) {
            Context ctx = getContext();
            switch (fileType) {
                case HTML:
                    this.syntaxHighlighter = new HtmlSyntaxHighlighter(ctx);
                    this.autoCompleteEngine = new HtmlAutoCompleteEngine(ctx);
                    if (currentFile != null) {
                        ((HtmlAutoCompleteEngine) this.autoCompleteEngine).setCurrentFile(currentFile);
                    }
                    break;
                case CSS:
                    this.syntaxHighlighter = new CssSyntaxHighlighter(ctx);
                    CssAutoCompleteEngine cssEngine = new CssAutoCompleteEngine(ctx);
                    if (currentFile != null) cssEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = cssEngine;
                    break;
                case JAVASCRIPT:
                    this.syntaxHighlighter = new JsSyntaxHighlighter(ctx);
                    JsAutoCompleteEngine jsEngine = new JsAutoCompleteEngine(ctx);
                    if (currentFile != null) jsEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = jsEngine;
                    break;
                case TYPESCRIPT:
                    this.syntaxHighlighter = new TsSyntaxHighlighter(ctx);
                    TsAutoCompleteEngine tsEngine = new TsAutoCompleteEngine(ctx);
                    if (currentFile != null) tsEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = tsEngine;
                    break;
                case JSON:
                    this.syntaxHighlighter = new JsonSyntaxHighlighter(ctx);
                    JsonAutoCompleteEngine jsonEngine = new JsonAutoCompleteEngine(ctx);
                    if (currentFile != null) jsonEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = jsonEngine;
                    break;
                case MARKDOWN:
                    this.syntaxHighlighter = new MarkdownSyntaxHighlighter(ctx);
                    this.autoCompleteEngine = null;
                    break;
                case SVG:
                    this.syntaxHighlighter = new SvgSyntaxHighlighter(ctx);
                    this.autoCompleteEngine = null;
                    break;
                default:
                    this.syntaxHighlighter = null;
                    this.autoCompleteEngine = null;
                    break;
            }
        }
        scheduleHighlight();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — diagnostics
    // ─────────────────────────────────────────────────────────────────────────

    public void setCurrentFile(File file) {
        this.currentFile = file;
        if (autoCompleteEngine instanceof HtmlAutoCompleteEngine) {
            ((HtmlAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        } else if (autoCompleteEngine instanceof JsAutoCompleteEngine) {
            ((JsAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        } else if (autoCompleteEngine instanceof JsonAutoCompleteEngine) {
            ((JsonAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        } else if (autoCompleteEngine instanceof CssAutoCompleteEngine) {
            ((CssAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — settings
    // ─────────────────────────────────────────────────────────────────────────

    public void setAutoCloseBrackets(boolean autoClose) {
        this.autoCloseBrackets = autoClose;
    }

    public void setAutoCloseHtmlTags(boolean autoClose) {
        this.autoCloseHtmlTags = autoClose;
    }

    public void setAutoCloseQuotes(boolean autoClose) {
        this.autoCloseQuotes = autoClose;
    }

    public void setWordWrap(boolean wordWrap) {
        if (this.wordWrap == wordWrap) return;
        // Preserve scroll position in terms of logical line so toggling wrap
        // doesn't jump the viewport.
        int scrollY = getScrollY();
        int topVisualRow = lineHeightPx > 0 ? scrollY / lineHeightPx : 0;
        int topLogicalLine = visualRowToLogicalLine(topVisualRow);
        int subRowOffset = lineHeightPx > 0 ? scrollY % lineHeightPx : 0;

        this.wordWrap = wordWrap;
        rebuildVisualLayout();
        requestLayout();
        invalidate();

        // Restore scroll to same logical line in the new coordinate system
        int newTopVisualRow = visualRowOf(topLogicalLine);
        int newScrollY = getPaddingTop() + newTopVisualRow * lineHeightPx + subRowOffset;
        post(() -> scrollTo(wordWrap ? 0 : getScrollX(), Math.max(0, newScrollY - getPaddingTop())));
    }

    private void scheduleVisualLayoutRebuild() {
        if (!wordWrap) {
            // Without word wrap the layout is trivial (1:1 lines) — update in place.
            rebuildVisualLayout();
            return;
        }
        // ALWAYS rebuild synchronously so totalVisualRows is immediately correct.
        // This prevents onMeasure() from returning a stale height that causes the
        // scroll parent to clamp scrollY to the wrong position (the "scroll jump" bug).
        rebuildVisualLayout();
        // Debounce the expensive requestLayout + invalidate to avoid doing them
        // on every single keystroke during rapid typing.
        if (!visualLayoutPending) {
            visualLayoutPending = true;
            mainHandler.postDelayed(visualLayoutRunnable, VISUAL_LAYOUT_DEBOUNCE_MS);
        }
    }

    private void rebuildVisualLayout() {
        if (!wordWrap || getWidth() <= 0) {
            int n = content.lineCount();
            if (visualRowStarts == null || visualRowStarts.length < n + 1)
                visualRowStarts = new int[n + 1];
            for (int i = 0; i <= n; i++) visualRowStarts[i] = i;
            totalVisualRows = n;
            return;
        }
        int n = content.lineCount();
        if (visualRowStarts == null || visualRowStarts.length < n + 1)
            visualRowStarts = new int[n + 1];
        int charsPerRow = Math.max(1, (int) ((getWidth() - getPaddingLeft() - getPaddingRight()) / charWidth));
        int row = 0;
        for (int i = 0; i < n; i++) {
            visualRowStarts[i] = row;
            int lineLen = content.lineLength(i);
            int rows = Math.max(1, (int) Math.ceil((double) lineLen / charsPerRow));
            row += rows;
        }
        visualRowStarts[n] = row;
        totalVisualRows = row;
    }

    private int visualRowOf(int logicalLine) {
        if (visualRowStarts == null || logicalLine >= visualRowStarts.length) return logicalLine;
        return visualRowStarts[logicalLine];
    }

    private int visualSubRow(int col) {
        if (!wordWrap || getWidth() <= 0) return 0;
        int charsPerRow = Math.max(1, (int) ((getWidth() - getPaddingLeft() - getPaddingRight()) / charWidth));
        return col / charsPerRow;
    }

    private int colInSubRow(int col) {
        if (!wordWrap || getWidth() <= 0) return col;
        int charsPerRow = Math.max(1, (int) ((getWidth() - getPaddingLeft() - getPaddingRight()) / charWidth));
        return col % charsPerRow;
    }

    private int absoluteVisualRow(int logicalLine, int col) {
        return visualRowOf(logicalLine) + visualSubRow(col);
    }

    private int visualRowToLogicalLine(int visualRow) {
        if (!wordWrap || visualRowStarts == null) return Math.max(0, visualRow);
        int lo = 0, hi = content.lineCount() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (visualRowStarts[mid] <= visualRow) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    public int getVisualRowStart(int logicalLine) {
        return visualRowOf(logicalLine);
    }

    public void setAutoIndent(boolean autoIndent) {
        this.autoIndent = autoIndent;
    }

    public void insertSnippet(String snippetTemplate) {
        if (snippetTemplate == null || snippetTemplate.isEmpty()) return;

        requestFocus();

        String currentText = content.getText();
        int flatCursor = content.flatOffset(cursor);
        String formattedSnippet = getFormattedSnippet(snippetTemplate, flatCursor, currentText);

        int pipeIndex = formattedSnippet.indexOf('|');
        if (pipeIndex != -1) {
            formattedSnippet = formattedSnippet.substring(0, pipeIndex)
                    + formattedSnippet.substring(pipeIndex + 1);
        }

        isApplyingHighlight = true;
        ContentPosition insertStart = cursor;
        UndoStack.EditorSnapshot before = snapshotAt(insertStart, selectionAnchor);
        content.insert(cursor.line, cursor.column, formattedSnippet);
        if (pipeIndex != -1) {
            cursor = content.positionAt(flatCursor + pipeIndex);
        } else {
            cursor = content.positionAt(flatCursor + formattedSnippet.length());
        }
        UndoStack.EditorSnapshot after = snapshotAt(cursor, null);
        undoStack.recordInsert(insertStart.line, insertStart.column, formattedSnippet, before, after);
        selectionAnchor = null;
        isApplyingHighlight = false;
        scheduleHighlight();
        invalidate();
    }

    @NonNull
    private String getFormattedSnippet(String snippetTemplate, int flatCursor, String currentText) {
        int lineStart = flatCursor - 1;
        while (lineStart >= 0 && currentText.charAt(lineStart) != '\n') {
            lineStart--;
        }
        lineStart++;
        StringBuilder baseIndent = new StringBuilder();
        for (int i = lineStart; i < flatCursor; i++) {
            char c = currentText.charAt(i);
            if (c == ' ' || c == '\t') baseIndent.append(c);
            else break;
        }
        return snippetTemplate.replace("\n", "\n" + baseIndent);
    }

    private UndoStack.EditorSnapshot snapshotAt(ContentPosition cur, ContentPosition sel) {
        return new UndoStack.EditorSnapshot(cur, sel, getScrollX(), getScrollY());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — snippet
    // ─────────────────────────────────────────────────────────────────────────

    public void undo() {
        undoStack.commitPending();
        UndoStack.EditorSnapshot restored = undoStack.undo(content);
        if (restored == null) return;
        isUndoRedoActive = true;
        cursor = restored.cursor;
        selectionAnchor = restored.selectionAnchor;
        longestLineLength = content.longestLineLength();
        isUndoRedoActive = false;
        requestLayout();
        invalidate();
        scheduleHighlight();
        notifySelectionChanged();
        post(() -> {
            scrollTo(restored.scrollX, restored.scrollY);
            ensureCursorVisible();
        });
    }

    public void redo() {
        UndoStack.EditorSnapshot restored = undoStack.redo(content);
        if (restored == null) return;
        isUndoRedoActive = true;
        cursor = restored.cursor;
        selectionAnchor = restored.selectionAnchor;
        longestLineLength = content.longestLineLength();
        isUndoRedoActive = false;
        requestLayout();
        invalidate();
        scheduleHighlight();
        notifySelectionChanged();
        post(() -> {
            scrollTo(restored.scrollX, restored.scrollY);
            ensureCursorVisible();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — undo / redo
    // ─────────────────────────────────────────────────────────────────────────

    public boolean canUndo() {
        return undoStack.canUndo();
    }

    public boolean canRedo() {
        return undoStack.canRedo();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mainHandler.removeCallbacks(autoCompleteRunnable);
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacks(blinkRunnable);
        mainHandler.removeCallbacks(bracketMatchRunnable);
        mainHandler.removeCallbacksAndMessages(null);
        if (autoCompletePopup != null) autoCompletePopup.dismiss();
    }

    private void scheduleHighlight() {
        if (syntaxHighlighter == null) return;

        bracketMatchOpen = null;
        bracketMatchClose = null;

        if (dirtyTracker.isDirty()) {
            int ds = Math.max(0, dirtyTracker.start);
            int de = Math.min(content.totalLength(), Math.max(ds, dirtyTracker.end));
            ContentPosition dirtyStart = content.positionAt(ds);
            ContentPosition dirtyEnd = content.positionAt(de);
            dirtyTracker.reset();

            int firstChangedLine = dirtyStart.line;
            int lastVisibleLine = Math.min(content.lineCount() - 1,
                    (getScrollY() + getHeight()) / Math.max(1, lineHeightPx) + VIEWPORT_BUFFER_LINES);
            int syncLimit = Math.max(dirtyEnd.line, lastVisibleLine);

            int state = (firstChangedLine > 0) ? content.getLine(firstChangedLine - 1).getTokenizerEndState() : 0;

            int i = firstChangedLine;
            for (; i < content.lineCount(); i++) {
                com.cocode.vcode.ide.core.editor.text.ContentLine line = content.getLine(i);
                if (i > dirtyEnd.line && line.getTokenizerStartState() == state) {
                    i = content.lineCount();
                    break;
                }
                if (i > syncLimit) break;
                line.setTokenizerStartState(state);

                int internalState = state & 0xFFFF;
                int depth = (state >>> 16) & 0xFFFF;
                int newInternalState = syntaxHighlighter.computeEndState(line, internalState);
                int newDepth = BracketMatcher.computeBracketDepth(line.toLineString(), depth);
                int newState = (newDepth << 16) | (newInternalState & 0xFFFF);

                line.setTokenizerEndState(newState);
                line.tokens = null;
                state = newState;
            }

            if (i < content.lineCount()) {
                final int startLine = i;
                final int startState = state;
                final int convergeAt = dirtyEnd.line;
                final long versionAtSchedule = content.getVersion();

                ExecutorProvider.getInstance().runOnCpu(() -> {
                    List<int[]> computed = new ArrayList<>();
                    int bgState = startState;
                    content.acquireReadLock();
                    try {
                        for (int li = startLine; li < content.lineCount(); li++) {
                            if (content.getVersion() != versionAtSchedule) return;
                            com.cocode.vcode.ide.core.editor.text.ContentLine line = content.getLine(li);
                            if (li > convergeAt && line.getTokenizerStartState() == bgState) break;

                            int internalState = bgState & 0xFFFF;
                            int depth = (bgState >>> 16) & 0xFFFF;

                            int newInternalState = syntaxHighlighter.computeEndState(line, internalState);
                            int newDepth = BracketMatcher.computeBracketDepth(line.toLineString(), depth);

                            int newState = (newDepth << 16) | (newInternalState & 0xFFFF);

                            computed.add(new int[]{li, bgState, newState});
                            bgState = newState;
                        }
                    } finally {
                        content.releaseReadLock();
                    }
                    mainHandler.post(() -> {
                        if (content.getVersion() != versionAtSchedule) return;
                        for (int[] entry : computed) {
                            com.cocode.vcode.ide.core.editor.text.ContentLine line = content.getLine(entry[0]);
                            line.setTokenizerStartState(entry[1]);
                            line.setTokenizerEndState(entry[2]);
                            line.tokens = null;
                        }
                        invalidate();
                    });
                });
            }
        }
        invalidate();
    }

    private void scheduleAutoComplete() {
        mainHandler.removeCallbacks(autoCompleteRunnable);
        mainHandler.postDelayed(autoCompleteRunnable, AUTOCOMPLETE_DELAY_MS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    private void triggerAutoComplete() {
        if (autoCompleteEngine == null) return;
        // LSP bridge has taken over completions for this language — skip legacy engine.
        if (lspCompletionActive) return;

        int flatCursor = content.flatOffset(cursor);
        String text = content.getSubstring(0, flatCursor);

        if (flatCursor <= 0 || flatCursor > text.length()) {
            autoCompletePopup.dismiss();
            return;
        }

        if (isCursorInComment(text, flatCursor)) {
            autoCompletePopup.dismiss();
            return;
        }

        char lastChar = text.charAt(flatCursor - 1);

        if (Character.isWhitespace(lastChar)) {
            autoCompletePopup.dismiss();
            return;
        }

        boolean isTriggerChar = TRIGGER_CHARS.indexOf(lastChar) >= 0;
        boolean isIdentifier = Character.isLetterOrDigit(lastChar)
                || lastChar == '_' || lastChar == '$'
                || (lastChar == '-' && (fileType == FileType.CSS || fileType == FileType.HTML));

        if (!isIdentifier && !isTriggerChar) {
            autoCompletePopup.dismiss();
            return;
        }

        final int capturedCursor = flatCursor;
        final String capturedText = text;

        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<CompletionItem> items = autoCompleteEngine.getSuggestions(capturedText, capturedCursor);
            mainHandler.post(() -> {
                if (content.flatOffset(cursor) != capturedCursor) return;
                if (items != null && !items.isEmpty()) {
                    autoCompletePopup.show(items, CodeEditText.this, capturedCursor);
                } else {
                    autoCompletePopup.dismiss();
                }
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — highlight
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Injects selected autocomplete text, properly computing replace range.
     */
    private void insertCompletion(CompletionItem item) {
        if (item == null) return;
        String insertText = item.getEffectiveInsertText();
        if (insertText == null) return;

        int flatCursor = content.flatOffset(cursor);
        String text = content.getText();

        // Compute wordStart
        int wordStart;
        if (item.getReplaceLength() >= 0) {
            wordStart = Math.max(0, flatCursor - item.getReplaceLength());
        } else {
            wordStart = flatCursor;
            while (wordStart > 0) {
                char c = text.charAt(wordStart - 1);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$' || c == '@') {
                    wordStart--;
                } else break;
            }
            if (wordStart > 0 && text.charAt(wordStart - 1) == '<' && insertText.startsWith("<")) {
                wordStart--;
            }
        }

        // Handle indentation for multi-line insertions
        int lineStart = wordStart;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        StringBuilder baseIndent = new StringBuilder();
        for (int i = lineStart; i < wordStart; i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') baseIndent.append(c);
            else break;
        }
        if (insertText.contains("\n")) {
            insertText = insertText.replace("\n", "\n" + baseIndent);
        }

        // Handle pipe cursor marker
        int pipeIdx = insertText.indexOf('|');
        String cleanInsert;
        int finalCursorFlat;
        if (pipeIdx >= 0) {
            cleanInsert = insertText.substring(0, pipeIdx) + insertText.substring(pipeIdx + 1);
            finalCursorFlat = wordStart + pipeIdx;
        } else {
            cleanInsert = insertText;
            finalCursorFlat = wordStart + cleanInsert.length() + item.getCursorOffset();
        }

        // Deduplicate trailing bracket/quote
        if (!cleanInsert.isEmpty() && flatCursor < text.length()) {
            char lastInserted = cleanInsert.charAt(cleanInsert.length() - 1);
            char nextInDoc = text.charAt(flatCursor);
            if (item.getCursorOffset() == 0 && pipeIdx < 0
                    && (lastInserted == ')' || lastInserted == ']' || lastInserted == '}'
                    || lastInserted == '"' || lastInserted == '\'')
                    && lastInserted == nextInDoc) {
                cleanInsert = cleanInsert.substring(0, cleanInsert.length() - 1);
                finalCursorFlat = wordStart + cleanInsert.length();
            }
        }
        ContentPosition wordStartPos = content.positionAt(wordStart);
        ContentPosition beforeCursor = cursor;
        UndoStack.EditorSnapshot before = snapshotAt(beforeCursor, selectionAnchor);
        String deletedText = "";
        try {
            int deleteEnd = content.flatOffset(cursor);
            if (deleteEnd > wordStart) {
                deletedText = content.getSubstring(wordStart, deleteEnd);
            }
        } catch (Exception ignored) {
        }
        content.replace(wordStartPos.line, wordStartPos.column,
                cursor.line, cursor.column, cleanInsert);
        int safeFinal = Math.min(finalCursorFlat, content.totalLength());
        cursor = content.positionAt(safeFinal);
        UndoStack.EditorSnapshot after = snapshotAt(cursor, null);
        if (!deletedText.isEmpty()) {
            undoStack.recordReplace(wordStartPos.line, wordStartPos.column,
                    beforeCursor.line, beforeCursor.column, deletedText, cleanInsert, before, after);
        } else {
            undoStack.recordInsert(wordStartPos.line, wordStartPos.column, cleanInsert, before, after);
        }
        selectionAnchor = null;

        autoCompletePopup.dismiss();
        scheduleHighlight();
        invalidate();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — autocomplete
    // ─────────────────────────────────────────────────────────────────────────

    private void handleAutoClose(CharSequence text, int insertFlatPos, char typed) {
        String closing = getClosingPair(typed);
        if (closing == null) return;

        boolean isQuote = typed == '"' || typed == '\'' || typed == '`';
        if (isQuote && !autoCloseQuotes) return;
        if (!isQuote && !autoCloseBrackets) return;

        // Don't auto-close if the next char is already the closing char
        if (insertFlatPos < text.length() && text.charAt(insertFlatPos) == closing.charAt(0))
            return;

        final String toInsert = closing;
        mainHandler.post(() -> {
            isAutoClosing = true;
            // Insert closing char at cursor position (cursor was already advanced past typed char)
            int safeFlat = Math.min(content.flatOffset(cursor), content.totalLength());
            ContentPosition pos = content.positionAt(safeFlat);
            content.insert(pos.line, pos.column, toInsert);
            // cursor stays before the inserted closing char — don't advance
            isAutoClosing = false;
            scheduleHighlight();
            invalidate();
        });
    }

    private String getClosingPair(char open) {
        switch (open) {
            case '(':
                return ")";
            case '[':
                return "]";
            case '{':
                return "}";
            case '"':
                return "\"";
            case '\'':
                return "'";
            case '`':
                return "`";
            default:
                return null;
        }
    }

    private void handleAutoCloseHtmlTag(int cursorAfterGt) {
        mainHandler.post(() -> {
            String currentText = content.getSubstring(0, Math.min(content.totalLength(), cursorAfterGt));
            String tagName = htmlTagParser.getCurrentOpenTagName(currentText, cursorAfterGt - 1);
            if (tagName == null || tagName.isEmpty() || HtmlTagParser.isVoidElement(tagName))
                return;

            String closing = "</" + tagName + ">";
            isAutoClosing = true;
            ContentPosition insertPos = content.positionAt(cursorAfterGt);
            content.insert(insertPos.line, insertPos.column, closing);
            isAutoClosing = false;
            scheduleHighlight();
            invalidate();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — auto-close brackets / indent
    // ─────────────────────────────────────────────────────────────────────────

    private void handleAutoIndent(String text, int newlineIndex) {
        if (!autoIndent || indentEngine == null) return;

        String innerIndent = indentEngine.getIndentForNewLine(text, newlineIndex, fileType);
        if (innerIndent == null) innerIndent = "";

        boolean isBracketSplit = false;
        String outerIndent = "";

        if (newlineIndex > 0 && newlineIndex + 1 < text.length()) {
            char before = text.charAt(newlineIndex - 1);
            char after = text.charAt(newlineIndex + 1);

            if ((before == '{' && after == '}')
                    || (before == '[' && after == ']')
                    || (before == '(' && after == ')')
                    || (before == '>' && after == '<')) {

                isBracketSplit = true;
                int lineStart = newlineIndex - 1;
                while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
                StringBuilder baseIndent = new StringBuilder();
                for (int i = lineStart; i < newlineIndex; i++) {
                    char c = text.charAt(i);
                    if (c == ' ' || c == '\t') baseIndent.append(c);
                    else break;
                }
                outerIndent = baseIndent.toString();
            }
        }

        final String finalInnerIndent = innerIndent;
        final boolean finalSplit = isBracketSplit;
        final String finalOuterIndent = outerIndent;
        final int insertFlat = newlineIndex + 1;

        if (!finalInnerIndent.isEmpty() || finalSplit) {
            mainHandler.post(() -> {
                isApplyingHighlight = true;
                ContentPosition insertPos = content.positionAt(insertFlat);
                if (finalSplit) {
                    String injection = finalInnerIndent + "\n" + finalOuterIndent;
                    content.insert(insertPos.line, insertPos.column, injection);
                } else {
                    content.insert(insertPos.line, insertPos.column, finalInnerIndent);
                }
                cursor = content.positionAt(insertFlat + finalInnerIndent.length());
                selectionAnchor = null;
                isApplyingHighlight = false;
                invalidate();
            });
        }
    }

    private void updateBracketMatch(CharSequence text, int cursorFlat) {
        bracketMatchOpen = null;
        bracketMatchClose = null;
        if (text.length() > 60000) {
            invalidate();
            return;
        }

        BracketMatcher.MatchResult match = null;
        if (cursorFlat < text.length()) match = bracketMatcher.findMatch(text, cursorFlat);
        if ((match == null || !match.found) && cursorFlat > 0)
            match = bracketMatcher.findMatch(text, cursorFlat - 1);
        if (match == null || !match.found)
            match = bracketMatcher.findEnclosing(text, cursorFlat);

        if (match != null && match.found) {
            bracketMatchOpen = content.positionAt(match.openPos);
            bracketMatchClose = content.positionAt(match.closePos);
        }
        invalidate();
    }

    private boolean isCursorInComment(String text, int cursorOffset) {
        int lineStart = cursorOffset - 1;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        String lineUpToCursor = text.substring(lineStart, cursorOffset);

        boolean inStr = false;
        char strCh = 0;
        for (int i = 0; i < lineUpToCursor.length() - 1; i++) {
            char c = lineUpToCursor.charAt(i);
            if (inStr) {
                if (c == strCh && lineUpToCursor.charAt(i - 1) != '\\') inStr = false;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') {
                inStr = true;
                strCh = c;
                continue;
            }
            if (c == '/' && lineUpToCursor.charAt(i + 1) == '/') return true;
        }
        int scanLimit = Math.max(0, cursorOffset - 60000);
        for (int i = cursorOffset - 2; i >= scanLimit; i--) {
            if (text.charAt(i) == '/' && text.charAt(i + 1) == '*') return true;
            if (i + 1 < text.length() && text.charAt(i) == '*' && text.charAt(i + 1) == '/')
                return false;
        }
        return false;
    }

    private float spToPx(float sp, Context ctx) {
        return sp * ctx.getResources().getDisplayMetrics().scaledDensity;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — bracket match
    // ─────────────────────────────────────────────────────────────────────────

    private float dpToPx(float dp, Context ctx) {
        return dp * ctx.getResources().getDisplayMetrics().density;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — comment detection
    // ─────────────────────────────────────────────────────────────────────────

    private void updateLongestLine(int changedLine) {
        int len = content.lineLength(changedLine);
        if (len > longestLineLength) longestLineLength = len;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int getLongestLineLength() {
        if (longestLineDirty) {
            longestLineLength = content.longestLineLength();
            longestLineDirty = false;
        }
        return longestLineLength;
    }

    private String buildTabSpaces() {
        StringBuilder sb = new StringBuilder();
        int tabSize = new AppSettings().tabSize;
        for (int i = 0; i < tabSize; i++) sb.append(' ');
        return sb.toString();
    }

    private float getCursorX(int line, int col) {
        if (wordWrap) col = colInSubRow(col);
        float cx = col * charWidth;
        java.util.List<com.cocode.vcode.ide.core.editor.highlight.HighlightToken> tokens = content.getLine(line).tokens;
        if (tokens != null) {
            for (com.cocode.vcode.ide.core.editor.highlight.HighlightToken t : tokens) {
                if (t.hasPreviewColor && t.startCol < col) {
                    cx += charWidth * 1.2f;
                }
            }
        }
        return cx;
    }

    private ContentPosition touchToPosition(float touchX, float touchY) {
        int visualRow = (int) ((touchY + getScrollY() - getPaddingTop()) / lineHeightPx);
        int logicalLine = visualRowToLogicalLine(visualRow);
        logicalLine = Math.max(0, Math.min(logicalLine, content.lineCount() - 1));

        int subRow = wordWrap ? (visualRow - visualRowOf(logicalLine)) : 0;
        int charsPerRow = wordWrap ? Math.max(1, (int) ((getWidth() - getPaddingLeft() - getPaddingRight()) / charWidth)) : Integer.MAX_VALUE;
        int colOffset = subRow * charsPerRow;

        float relativeX = touchX + (wordWrap ? 0 : getScrollX()) - getPaddingLeft();
        int lineLen = content.lineLength(logicalLine);

        java.util.List<com.cocode.vcode.ide.core.editor.highlight.HighlightToken> tokens = content.getLine(logicalLine).tokens;

        boolean hasCircles = false;
        if (tokens != null) {
            for (com.cocode.vcode.ide.core.editor.highlight.HighlightToken t : tokens) {
                if (t.hasPreviewColor) {
                    hasCircles = true;
                    break;
                }
            }
        }

        int colInSub = 0;
        if (!hasCircles) {
            colInSub = (int) (relativeX / charWidth);
            if (relativeX - colInSub * charWidth > charWidth / 2f) colInSub++;
        } else {
            float currentX = 0;
            while (colInSub < lineLen - colOffset) {
                float widthAtCol = charWidth;
                for (com.cocode.vcode.ide.core.editor.highlight.HighlightToken t : tokens) {
                    if (t.hasPreviewColor && t.startCol == colOffset + colInSub) {
                        widthAtCol += charWidth * 1.2f;
                        break;
                    }
                }
                if (relativeX < currentX + widthAtCol / 2f) break;
                currentX += widthAtCol;
                colInSub++;
            }
        }
        int col = colOffset + colInSub;
        return new ContentPosition(logicalLine, Math.max(0, Math.min(col, lineLen)));
    }

    /**
     * Selects the word at {@code pos}, mirroring Android stock EditText long-press behaviour.
     *
     * <ul>
     *   <li>Word characters: letter, digit, underscore, dollar sign (same as
     *       {@code android.text.method.WordIterator}).</li>
     *   <li>If the finger lands in whitespace, the nearest adjacent word is selected.</li>
     *   <li>If the line is empty, nothing is selected (returns {@code false}).</li>
     * </ul>
     *
     * @return {@code true} if a non-empty selection was made.
     */
    private boolean selectWordAt(ContentPosition pos) {
        int lineLen = content.lineLength(pos.line);
        if (lineLen == 0) return false;

        // Clamp column to valid range
        int col = Math.min(pos.column, lineLen - 1);

        int minStart = Math.max(0, col - 100);
        int maxEnd = Math.min(lineLen, col + 100);

        // If we landed in whitespace, try to shift to the nearest word character
        if (!isWordChar(content.charAt(pos.line, col))) {
            int right = col;
            while (right < maxEnd && !isWordChar(content.charAt(pos.line, right))) right++;
            int left = col - 1;
            while (left >= minStart && !isWordChar(content.charAt(pos.line, left))) left--;

            if (right < maxEnd) {
                col = right;
            } else if (left >= minStart) {
                col = left;
            } else {
                return false; // entire line is whitespace within bounds
            }
        }

        // Expand left to word start
        int wordStart = col;
        while (wordStart > minStart && isWordChar(content.charAt(pos.line, wordStart - 1)))
            wordStart--;

        // Expand right to word end
        int wordEnd = col;
        while (wordEnd < maxEnd && isWordChar(content.charAt(pos.line, wordEnd))) wordEnd++;

        if (wordStart >= wordEnd) return false;

        // Apply selection: anchor at start, cursor at end (matches EditText convention)
        selectionAnchor = new ContentPosition(pos.line, wordStart);
        cursor = new ContentPosition(pos.line, wordEnd);
        return true;
    }

    public interface OnContentChangeListener {
        void onContentChanged();
    }

    /**
     * Listener notified when selection becomes active or collapses.
     */
    public interface OnSelectionChangeListener {
        void onSelectionChanged(boolean hasSelection);
    }

    public interface OnScrollChangeListener {
        void onScrollChanged(int scrollX, int scrollY);
    }

    /**
     * CharSequence adapter over the Content model (AD-1 / AD-7).
     * toString() materialises the full text; length() is O(1) via Content.
     */
    private static final class ContentCharSequence implements CharSequence {
        private final Content content;

        ContentCharSequence(Content c) {
            this.content = c;
        }

        @Override
        public int length() {
            return content.totalLength();
        }

        @Override
        public char charAt(int index) {
            ContentPosition pos = content.positionAt(index);
            if (pos.column < content.lineLength(pos.line))
                return content.charAt(pos.line, pos.column);
            return '\n';
        }

        @NonNull
        @Override
        public CharSequence subSequence(int start, int end) {
            return content.getText().subSequence(start, end);
        }

        @NonNull
        @Override
        public String toString() {
            return content.getText();
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Inner classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Custom InputConnection that routes all IME mutations through the {@link Content} model.
     *
     * <p>This is the highest-risk section for OEM keyboard regressions (Samsung, Xiaomi/MIUI,
     * Gboard, SwiftKey all have quirks). Implements the minimum required subset robustly:
     * commitText, deleteSurroundingText, setComposingText/finishComposingText,
     * getTextBeforeCursor/getTextAfterCursor, setSelection, sendKeyEvent.
     */
    private static final class CodeInputConnection extends BaseInputConnection {

        private final CodeEditText editor;

        CodeInputConnection(CodeEditText editor) {
            super(editor, true);
            this.editor = editor;
        }

        // ── commitText ─────────────────────────────────────────────────────────────

        /**
         * Computes the cursor position after inserting {@code text} starting at {@code from}.
         */
        private static ContentPosition advanceCursor(ContentPosition from, String text) {
            int line = from.line;
            int col = from.column;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    col = 0;
                } else col++;
            }
            return new ContentPosition(line, col);
        }

        // ── setComposingText ───────────────────────────────────────────────────────

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (text == null) return false;
            String insertText = text.toString();

            if (editor.composingStart >= 0 && editor.composingEnd >= editor.composingStart) {
                int total = editor.content.totalLength();
                int start = Math.max(0, Math.min(editor.composingStart, total));
                int end = Math.max(start, Math.min(editor.composingEnd, total));
                ContentPosition startPos = editor.content.positionAt(start);
                ContentPosition endPos = editor.content.positionAt(end);
                String deleted;
                try {
                    deleted = editor.content.getSubstring(start, end);
                } catch (Exception e) {
                    deleted = "";
                }
                UndoStack.EditorSnapshot before = editor.snapshotAt(editor.cursor, null);

                editor.content.replace(startPos.line, startPos.column, endPos.line, endPos.column, insertText);
                ContentPosition after = editor.content.positionAt(start + insertText.length());
                editor.cursor = after;
                editor.selectionAnchor = null;

                if (!deleted.isEmpty() || !insertText.isEmpty()) {
                    editor.undoStack.recordReplace(startPos.line, startPos.column, endPos.line, endPos.column,
                            deleted, insertText, before, editor.snapshotAt(after, null));
                }

                editor.composingStart = -1;
                editor.composingEnd = -1;
                editor.post(editor::ensureCursorVisible);
                editor.invalidate();
                editor.scheduleHighlight();
                editor.scheduleAutoComplete();
            } else {
                ContentPosition selAnchorBefore = editor.selectionAnchor;
                ContentPosition beforeCursor = editor.cursor;
                String deletedSel = "";
                ContentPosition delStart = null;
                ContentPosition delEnd = null;

                if (editor.selectionAnchor != null) {
                    int start = editor.getSelectionStart();
                    int end = editor.getSelectionEnd();
                    if (start != end) {
                        delStart = editor.content.positionAt(start);
                        delEnd = editor.content.positionAt(end);
                        try {
                            deletedSel = editor.content.getSubstring(start, end);
                        } catch (Exception e) {
                            deletedSel = "";
                        }
                        editor.content.delete(delStart.line, delStart.column, delEnd.line, delEnd.column);
                        editor.cursor = delStart;
                    }
                    editor.selectionAnchor = null;
                }

                insertAtCursor(insertText, deletedSel, delStart, delEnd, beforeCursor, selAnchorBefore);
            }
            return true;
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            if (editor.composingStart < 0) {
                editor.composingStart = editor.content.flatOffset(editor.cursor);
                editor.composingEnd = editor.composingStart;
            }
            int composingLen = (editor.composingEnd > editor.composingStart) ? editor.composingEnd - editor.composingStart : 0;
            replaceRange(editor.composingStart, editor.composingStart + composingLen, text != null ? text.toString() : "");
            editor.composingEnd = editor.composingStart + (text != null ? text.length() : 0);
            return true;
        }

        @Override
        public boolean finishComposingText() {
            editor.composingStart = -1;
            editor.composingEnd = -1;
            super.finishComposingText();
            return true;
        }

        // ── deleteSurroundingText ──────────────────────────────────────────────────

        @Override
        public boolean setComposingRegion(int start, int end) {
            editor.composingStart = Math.max(0, start);
            editor.composingEnd = Math.min(editor.content.totalLength(), end);
            return true;
        }

        // ── getTextBeforeCursor / getTextAfterCursor ───────────────────────────────

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            if (deleteSelection()) return true;

            int beforeLineCount = editor.content.lineCount();

            int cursorFlat = editor.content.flatOffset(editor.cursor);
            int totalLen = editor.content.totalLength();

            if (afterLength > 0) {
                int afterEnd = Math.min(cursorFlat + afterLength, totalLen);
                if (afterEnd > cursorFlat) {
                    ContentPosition startPos = editor.content.positionAt(cursorFlat);
                    ContentPosition endPos = editor.content.positionAt(afterEnd);
                    // Capture deleted text before deleting
                    String deleted;
                    try {
                        deleted = editor.content.getSubstring(cursorFlat, afterEnd);
                    } catch (Exception e) {
                        deleted = "";
                    }
                    editor.content.delete(startPos.line, startPos.column, endPos.line, endPos.column);
                    editor.undoStack.recordDelete(startPos.line, startPos.column,
                            endPos.line, endPos.column, deleted,
                            editor.snapshotAt(editor.cursor, null), editor.snapshotAt(editor.cursor, null));
                }
            }

            if (beforeLength > 0) {
                int newCursorFlat = editor.content.flatOffset(editor.cursor);
                int beforeStart = Math.max(0, newCursorFlat - beforeLength);
                if (beforeStart < newCursorFlat) {
                    ContentPosition startPos = editor.content.positionAt(beforeStart);
                    ContentPosition endPos = editor.content.positionAt(newCursorFlat);
                    String deleted;
                    try {
                        deleted = editor.content.getSubstring(beforeStart, newCursorFlat);
                    } catch (Exception e) {
                        deleted = "";
                    }
                    editor.content.delete(startPos.line, startPos.column, endPos.line, endPos.column);
                    editor.cursor = startPos;
                    editor.undoStack.recordDelete(startPos.line, startPos.column,
                            endPos.line, endPos.column, deleted,
                            editor.snapshotAt(endPos, null), editor.snapshotAt(startPos, null));
                }
            }

            int afterLineCount = editor.content.lineCount();
            if (afterLineCount != beforeLineCount) {
                // Tokens now shift implicitly with the lines.
            }

            editor.post(editor::ensureCursorVisible);
            editor.invalidate();
            editor.scheduleHighlight();
            editor.scheduleAutoComplete();
            return true;
        }

        @Override
        public CharSequence getTextBeforeCursor(int n, int flags) {
            int cursorFlat = editor.content.flatOffset(editor.cursor);
            int start = Math.max(0, cursorFlat - n);
            try {
                return editor.content.getSubstring(start, cursorFlat);
            } catch (Exception e) {
                return "";
            }
        }

        @Override
        public CharSequence getTextAfterCursor(int n, int flags) {
            int cursorFlat = editor.content.flatOffset(editor.cursor);
            int total = editor.content.totalLength();
            int end = Math.min(total, cursorFlat + n);
            try {
                return editor.content.getSubstring(cursorFlat, end);
            } catch (Exception e) {
                return "";
            }
        }

        // ── setSelection ───────────────────────────────────────────────────────────

        @Override
        public CharSequence getSelectedText(int flags) {
            if (editor.selectionAnchor == null) return "";
            int start = editor.getSelectionStart();
            int end = editor.getSelectionEnd();
            try {
                return editor.content.getSubstring(start, end);
            } catch (Exception e) {
                return "";
            }
        }

        // ── sendKeyEvent ───────────────────────────────────────────────────────────

        @Override
        public boolean setSelection(int start, int end) {
            editor.isSettingSelectionFromIme = true;
            try {
                // Clear composing region so the IME does not snap the cursor back
                // to the composing anchor when the user slides on the spacebar.
                editor.composingStart = -1;
                editor.composingEnd = -1;
                editor.setSelection(start, end);
            } finally {
                editor.isSettingSelectionFromIme = false;
            }
            return true;
        }

        // ── Private helpers ────────────────────────────────────────────────────────

        @Override
        public boolean sendKeyEvent(android.view.KeyEvent event) {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN)
                return super.sendKeyEvent(event);

            switch (event.getKeyCode()) {
                case android.view.KeyEvent.KEYCODE_DEL:
                    handleBackspace();
                    return true;
                case android.view.KeyEvent.KEYCODE_FORWARD_DEL:
                    handleForwardDelete();
                    return true;
                case android.view.KeyEvent.KEYCODE_ENTER:
                    commitText("\n", 1);
                    return true;
                case android.view.KeyEvent.KEYCODE_TAB:
                    commitText(editor.buildTabSpaces(), 1);
                    return true;
                default:
                    return super.sendKeyEvent(event);
            }
        }

        /**
         * Inserts {@code text} at the current cursor position. If {@code deletedSel} is non-empty,
         * the preceding selection deletion (already applied to {@code content}) is combined with
         * this insertion into a single atomic undo unit, matching desktop editors' "replace
         * selection by typing" behaviour.
         */
        private void insertAtCursor(String text, String deletedSel,
                                    ContentPosition delStart, ContentPosition delEnd,
                                    ContentPosition beforeCursor, ContentPosition selAnchorBefore) {
            if (text == null || text.isEmpty()) {
                if (deletedSel != null && !deletedSel.isEmpty()) {
                    editor.undoStack.recordDelete(delStart.line, delStart.column, delEnd.line, delEnd.column,
                            deletedSel, editor.snapshotAt(beforeCursor, selAnchorBefore), editor.snapshotAt(editor.cursor, null));
                    editor.invalidate();
                    editor.scheduleHighlight();
                }
                return;
            }

            ContentPosition before = editor.cursor;
            int beforeFlat = editor.content.flatOffset(before);

            editor.isTypingText = true;
            int beforeLineCount = editor.content.lineCount();

            editor.content.insert(before.line, before.column, text);

            int afterLineCount = editor.content.lineCount();
            if (afterLineCount != beforeLineCount) {
                // Tokens now shift implicitly with the lines.
            }

            ContentPosition after = advanceCursor(before, text);
            editor.cursor = after;
            editor.selectionAnchor = null;

            editor.isTypingText = false;

            if (deletedSel != null && !deletedSel.isEmpty()) {
                editor.undoStack.recordReplace(delStart.line, delStart.column, delEnd.line, delEnd.column,
                        deletedSel, text, editor.snapshotAt(beforeCursor, selAnchorBefore), editor.snapshotAt(after, null));
            } else {
                editor.undoStack.recordInsert(before.line, before.column, text,
                        editor.snapshotAt(before, null), editor.snapshotAt(after, null));
            }

            // Side effects
            if (text.length() == 1 && !editor.isAutoClosing) {
                char typed = text.charAt(0);
                if (editor.autoCloseBrackets) {
                    editor.handleAutoClose(new ContentCharSequence(editor.content), beforeFlat + 1, typed);
                }
                if (editor.autoCloseHtmlTags
                        && editor.fileType == FileType.HTML
                        && typed == '>') {
                    editor.handleAutoCloseHtmlTag(beforeFlat + 1);
                }
                if (typed == '\n') {
                    int indentTextEnd = Math.min(editor.content.totalLength(), beforeFlat + 2);
                    editor.handleAutoIndent(editor.content.getSubstring(0, indentTextEnd), beforeFlat);
                }
            }

            editor.post(editor::ensureCursorVisible);
            editor.invalidate();
            editor.scheduleHighlight();
            editor.scheduleAutoComplete();
            editor.mainHandler.removeCallbacks(editor.bracketMatchRunnable);
            editor.mainHandler.postDelayed(editor.bracketMatchRunnable, 150);
        }

        /**
         * Replaces [start, end) flat-offset range with replacement text.
         */
        private void replaceRange(int start, int end, String replacement) {
            int total = editor.content.totalLength();
            start = Math.max(0, Math.min(start, total));
            end = Math.max(start, Math.min(end, total));

            ContentPosition startPos = editor.content.positionAt(start);
            ContentPosition endPos = editor.content.positionAt(end);

            int beforeLineCount = editor.content.lineCount();
            editor.content.replace(startPos.line, startPos.column,
                    endPos.line, endPos.column, replacement);
            int afterLineCount = editor.content.lineCount();
            if (afterLineCount != beforeLineCount) {
                // Tokens now shift implicitly with the lines.
            }

            int newFlat = start + (replacement != null ? replacement.length() : 0);
            editor.cursor = editor.content.positionAt(newFlat);
            editor.selectionAnchor = null;
            editor.post(editor::ensureCursorVisible);
            editor.invalidate();
            editor.scheduleHighlight();
            editor.scheduleAutoComplete();
        }

        /**
         * Deletes the current selection if any. Returns true if something was deleted.
         */
        private boolean deleteSelection() {
            if (editor.selectionAnchor == null) return false;
            int start = editor.getSelectionStart();
            int end = editor.getSelectionEnd();
            if (start == end) {
                editor.selectionAnchor = null;
                return false;
            }

            ContentPosition startPos = editor.content.positionAt(start);
            ContentPosition endPos = editor.content.positionAt(end);
            ContentPosition selAnchorBefore = editor.selectionAnchor;
            ContentPosition beforeCursor = editor.cursor;
            String deleted;
            try {
                deleted = editor.content.getSubstring(start, end);
            } catch (Exception e) {
                deleted = "";
            }

            editor.content.delete(startPos.line, startPos.column, endPos.line, endPos.column);
            editor.cursor = startPos;
            editor.selectionAnchor = null;

            editor.undoStack.recordDelete(startPos.line, startPos.column, endPos.line, endPos.column,
                    deleted, editor.snapshotAt(beforeCursor, selAnchorBefore), editor.snapshotAt(startPos, null));
            return true;
        }

        /**
         * Deletes the character immediately before the cursor (Backspace).
         */
        private void handleBackspace() {
            if (deleteSelection()) return;
            int cursorFlat = editor.content.flatOffset(editor.cursor);
            if (cursorFlat <= 0) return;

            ContentPosition newCursorPos = editor.content.positionAt(cursorFlat - 1);
            // Determine the character being deleted
            String deleted;
            if (newCursorPos.column < editor.content.lineLength(newCursorPos.line)) {
                deleted = String.valueOf(editor.content.charAt(newCursorPos.line, newCursorPos.column));
            } else {
                deleted = "\n"; // at end-of-line → deleting the newline
            }
            int beforeLineCount = editor.content.lineCount();
            ContentPosition oldCursor = editor.cursor;
            editor.content.delete(newCursorPos.line, newCursorPos.column,
                    oldCursor.line, oldCursor.column);
            int afterLineCount = editor.content.lineCount();
            if (afterLineCount != beforeLineCount) {
                // Tokens now shift implicitly with the lines.
            }
            editor.undoStack.recordDelete(newCursorPos.line, newCursorPos.column,
                    oldCursor.line, oldCursor.column,
                    deleted, editor.snapshotAt(oldCursor, null), editor.snapshotAt(newCursorPos, null));
            editor.cursor = newCursorPos;
            editor.selectionAnchor = null;
            editor.post(editor::ensureCursorVisible);
            editor.invalidate();
            editor.scheduleHighlight();
            editor.scheduleAutoComplete();
            editor.mainHandler.removeCallbacks(editor.bracketMatchRunnable);
            editor.mainHandler.postDelayed(editor.bracketMatchRunnable, 150);
        }

        /**
         * Deletes the character immediately after the cursor (Delete key).
         */
        private void handleForwardDelete() {
            if (deleteSelection()) return;
            int cursorFlat = editor.content.flatOffset(editor.cursor);
            int total = editor.content.totalLength();
            if (cursorFlat >= total) return;

            ContentPosition endPos = editor.content.positionAt(cursorFlat + 1);
            String deleted;
            if (editor.cursor.column < editor.content.lineLength(editor.cursor.line)) {
                deleted = String.valueOf(editor.content.charAt(editor.cursor.line, editor.cursor.column));
            } else {
                deleted = "\n";
            }
            int beforeLineCount = editor.content.lineCount();
            ContentPosition fixedCursor = editor.cursor;
            editor.content.delete(fixedCursor.line, fixedCursor.column,
                    endPos.line, endPos.column);
            int afterLineCount = editor.content.lineCount();
            if (afterLineCount != beforeLineCount) {
                // Tokens now shift implicitly with the lines.
            }
            editor.undoStack.recordDelete(fixedCursor.line, fixedCursor.column,
                    endPos.line, endPos.column, deleted,
                    editor.snapshotAt(fixedCursor, null), editor.snapshotAt(fixedCursor, null));
            editor.post(editor::ensureCursorVisible);
            editor.invalidate();
            editor.scheduleHighlight();
            editor.scheduleAutoComplete();
        }
    }



    // ─────────────────────────────────────────────────────────────────────────
    // CodeInputConnection
    // ─────────────────────────────────────────────────────────────────────────


}
package com.cocode.vcode.ide.views;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
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
import com.cocode.vcode.ide.core.editor.highlight.GradientPreview;
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
import com.cocode.vcode.ide.core.language.md.MarkdownAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.md.MarkdownSyntaxHighlighter;
import com.cocode.vcode.ide.core.language.svg.SvgAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.svg.SvgSyntaxHighlighter;
import com.cocode.vcode.ide.core.language.ts.TsAutoCompleteEngine;
import com.cocode.vcode.ide.core.language.ts.TsSyntaxHighlighter;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
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
 * High-performance code editor View for the VCode Android IDE.
 *
 * <p>Built directly on {@link android.view.View} for optimal rendering efficiency and memory utilization.
 * Text storage is backed by the line-indexed {@link Content} model with undo/redo handled by {@link UndoStack}.
 * Rendering in {@link #onDraw(Canvas)} uses viewport culling to draw only the visible lines for smooth 60fps
 * scrolling on large files.
 */
public class CodeEditText extends View {

    private static final int VIEWPORT_BUFFER_LINES = 200;
    private static final long AUTOCOMPLETE_DELAY_MS = 100;
    private static final String TRIGGER_CHARS = ".</:'\"@#!({&>+^*[]})%";

    // Selection handle drag states
    private static final int HANDLE_DRAG_NONE = 0;
    private static final int HANDLE_DRAG_START = 1;
    private static final int HANDLE_DRAG_END = 2;

    // Debounced visual layout rebuild (avoids scroll jumps during flings)
    private static final long VISUAL_LAYOUT_DEBOUNCE_MS = 32; // ~2 frames

    // Text model and state
    private final Content content = new Content();
    private final UndoStack undoStack = new UndoStack();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HtmlTagParser htmlTagParser = new HtmlTagParser();
    private final BracketMatcher bracketMatcher = new BracketMatcher();
    private final DirtyRangeTracker dirtyTracker = new DirtyRangeTracker();
    boolean autoCloseHtmlTags = true;

    // IME composing region
    int composingStart = -1;
    int composingEnd = -1;
    private boolean autoCloseQuotes = true;
    private boolean wordWrap = false;
    private int[] visualRowStarts;
    private int[][] lineWrapBreaks;
    private int cachedWrapCharsPerRow = -1;
    private int totalVisualRows;
    private boolean visualLayoutPending = false;
    private boolean isSettingSelectionFromIme = false;

    // Rendering and paint state
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

    // Cached theme colors
    private int cachedErrorColor;
    private int cachedWarningColor;
    private int cachedInfoColor;
    private int cachedBracketHighlightColor;

    // Scrolling and gestures
    private OverScroller overScroller;
    private GestureDetectorCompat gestureDetector;
    private OnScrollChangeListener scrollChangeListener;

    // Cursor and selection
    private ContentPosition cursor = ContentPosition.ZERO;
    private ContentPosition selectionAnchor = null; // null == no selection
    private boolean cursorVisible = true;
    private OnSelectionChangeListener selectionChangeListener;
    private OnCursorIdleListener cursorIdleListener;
    private final Runnable cursorIdleRunnable = () -> {
        if (cursorIdleListener != null) {
            cursorIdleListener.onCursorIdle(getSelectionStart());
        }
    };
    private int activeDragHandle = HANDLE_DRAG_NONE;
    private Paint handlePaint;

    // File and syntax
    private FileType fileType = FileType.TEXT;
    private File currentFile;
    private SyntaxHighlighter syntaxHighlighter;
    private AutoCompleteEngine autoCompleteEngine;

    // State flags
    private boolean isAutoClosing = false;
    private boolean isApplyingHighlight = false;
    private boolean isUndoRedoActive = false;
    private boolean isSettingText = false;
    private boolean isTypingText = false;
    private boolean isInsertingCompletion = false;
    /**
     * Multiple listeners for text-load lifecycle (used e.g. by a "loading…" UI and by
     * LspEditorBridge to know when a fresh file's content has actually landed in the
     * editor, since {@link #setText(CharSequence)} does NOT fire {@link OnContentChangeListener}
     * — see {@link #dispatchContentChanged()}, which is wired to discrete Content
     * insert/delete edits, not to bulk loads).
     */
    private final List<OnTextLoadListener> textLoadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<OnContentChangeListener> contentChangeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Runnable> cursorChangeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private List<Problem> currentProblems = new ArrayList<>();
    private float lastSquiggleConfigHash = 0;

    // Editor settings
    public static final int LARGE_FILE_LINE_THRESHOLD = 10000;
    private boolean autoCloseBrackets = true;
    private boolean autoIndent = true;
    private boolean showSquigglyLines = true;
    private boolean deleteMatchingPairs = true;
    private boolean forceLargeFileHighlighting = false;
    private boolean rainbowBrackets = true;
    private boolean bracketHighlighting = true;
    private IndentationEngine indentEngine;
    private final AutoCompletePopup autoCompletePopup;
    private final SignatureHintPopup signatureHintPopup;
    private boolean lspCompletionActive = false;

    // Highlighting buffers
    private final Runnable autoCompleteRunnable = this::triggerAutoComplete;
    private int defaultTextColor;
    private int[] rainbowColors;
    private int[] colorBuffer = new int[1024];
    private boolean[] underlineBuffer = new boolean[1024];
    private boolean[] previewBuffer = new boolean[1024];
    private int[] previewColorBuffer = new int[1024];
    private GradientPreview[] previewGradientBuffer = new GradientPreview[1024];
    private char[] lineBuffer = new char[1024];

    // Bracket match positions
    private ContentPosition bracketMatchOpen = null;
    private ContentPosition bracketMatchClose = null;
    final Runnable bracketMatchRunnable = () -> {
        CharSequence text = new ContentCharSequence(content);
        int flatCursor = content.flatOffset(cursor);
        updateBracketMatch(text, flatCursor);
    };

    // Search decorations
    private List<SearchResult> searchDecorations = new ArrayList<>();
    private int searchActiveIndex = -1;
    private volatile long textLoadToken = 0;

    // Full text cache for autocomplete and search
    private String cachedFullText;
    private long cachedFullTextVersion = -1;

    private String getCachedFullText() {
        long currentVersion = content.getVersion();
        if (cachedFullText == null || cachedFullTextVersion != currentVersion) {
            cachedFullText = content.getText();
            cachedFullTextVersion = currentVersion;
        }
        return cachedFullText;
    }

    // Reusable objects and cached metrics for 60fps rendering
    private final Path reusableDiagnosticPath = new Path();
    private float density = 1f;
    private float handleRadiusPx = 10f;
    private float handleThresholdPx = 40f;
    private int tabSize = 2;
    private String tabSpaces = "  ";

    public void setTabSize(int size) {
        if (size <= 0) size = 2;
        this.tabSize = size;
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) sb.append(' ');
        this.tabSpaces = sb.toString();
        this.indentEngine = new IndentationEngine(size);
    }

    public int getTabSize() {
        return tabSize;
    }

    // Cursor blink runnable
    private final Runnable blinkRunnable = () -> {
        cursorVisible = !cursorVisible;
        invalidate();
        scheduleBlink();
    };

    public CodeEditText(Context context) {
        super(context);
        autoCompletePopup = new AutoCompletePopup(context);
        signatureHintPopup = new SignatureHintPopup(context);
        init(context);
    }

    public CodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        autoCompletePopup = new AutoCompletePopup(context);
        signatureHintPopup = new SignatureHintPopup(context);
        init(context);
    }

    public CodeEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        autoCompletePopup = new AutoCompletePopup(context);
        signatureHintPopup = new SignatureHintPopup(context);
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
        density = context.getResources().getDisplayMetrics().density;
        Typeface codeFont = FontManager.getInstance().getCodeFont(context);

        // Apply theme-specific surface background color
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
        cursorPaint.setStrokeWidth(dpToPx(2f));
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
        searchMatchPaint.setColor(ContextCompat.getColor(context, R.color.vcode_search_match_bg));
        searchMatchPaint.setStyle(Paint.Style.FILL);
        searchActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        searchActivePaint.setColor(ContextCompat.getColor(context, R.color.vcode_search_active_bg));
        searchActivePaint.setStyle(Paint.Style.FILL);

        bracketHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bracketHighlightPaint.setStyle(Paint.Style.STROKE);
        bracketHighlightPaint.setStrokeWidth(3f);

        handleRadiusPx = dpToPx(5);
        handleThresholdPx = dpToPx(20);
        setTabSize(new AppSettings().tabSize);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setLongClickable(true); // allow long-press just like EditText
        setVerticalScrollBarEnabled(true);
        setHorizontalScrollBarEnabled(true);
        // Disable overscroll to prevent rubber-band bounce effects that misalign
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
                // Request soft keyboard display on confirmed single-tap rather than touch-down,
                // preventing unintentional keyboard popups during scroll gestures.
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
                } else {
                    if (hasSelection() || cursor == null || cursor.line != pressed.line || Math.abs(cursor.column - pressed.column) > 1) {
                        cursor = pressed;
                    }
                    selectionAnchor = null;
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    showKeyboard();
                    cursorVisible = true;
                    scheduleBlink();
                    invalidate();
                    if (selectionChangeListener != null && !isSettingSelectionFromIme) {
                        selectionChangeListener.onEmptyLongPress();
                    }
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
            float rectTop = paddingTop + cursorVisualRow * lineHeightPx;
            canvas.drawRect(scrollX, rectTop, scrollX + viewW, rectTop + lineHeightPx, lineHighlightPaint);
        }

        // 2. Selection background
        drawSelection(canvas, firstLine, lastLine, paddingLeft, paddingTop);

        // 3. Search decorations background
        drawSearchDecorations(canvas, firstLine, lastLine, paddingLeft, paddingTop);

        // 4. Text (token-colored using char array buffer to avoid allocations per line)
        textPaint.setStyle(Paint.Style.FILL);

        Paint.FontMetricsInt fm = textPaint.getFontMetricsInt();
        int ascent = fm.ascent;

        int charsPerRow = wordWrap ? Math.max(1, (int) ((getWidth() - getPaddingLeft() - getPaddingRight()) / charWidth)) : Integer.MAX_VALUE;

        for (int line = firstLine; line <= lastLine; line++) {
            int lineLen = content.lineLength(line);
            if (lineLen == 0) continue;

            int subRows = wordWrap ? getSubRowCount(line) : 1;

            com.cocode.vcode.ide.core.editor.text.ContentLine contentLine = content.getLine(line);
            boolean canHighlight = syntaxHighlighter != null
                    && (content.lineCount() <= LARGE_FILE_LINE_THRESHOLD || forceLargeFileHighlighting);
            if (contentLine.tokens == null && canHighlight) {
                int state = contentLine.getTokenizerStartState();
                int internalState = state & 0xFFFF;
                int depth = (state >>> 16) & 0xFFFF;
                String lineStr = contentLine.toLineString();
                contentLine.tokens = syntaxHighlighter.tokenizeLine(lineStr, line, internalState);
                if (rainbowBrackets) {
                    BracketMatcher.applyRainbowBrackets(contentLine.tokens, lineStr, rainbowColors, depth, internalState, line, fileType);
                }
            }
            List<HighlightToken> lineTokens = contentLine.tokens;

            for (int sr = 0; sr < subRows; sr++) {
                int srStart = wordWrap ? getSubRowStart(line, sr) : 0;
                int srEnd = wordWrap ? getSubRowEnd(line, sr) : lineLen;

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
                    previewGradientBuffer = new GradientPreview[newCap];
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
                    Arrays.fill(previewBuffer, 0, renderLen, false);
                    Arrays.fill(previewGradientBuffer, 0, renderLen, null);

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
                            previewGradientBuffer[s] = tok.gradientPreview;
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
                            GradientPreview grad = previewGradientBuffer[start];
                            if (grad != null) {
                                Shader shader = grad.createShader(circleX, circleY, circleRadius);
                                textPaint.setShader(shader);
                                canvas.drawCircle(circleX, circleY, circleRadius, textPaint);
                                textPaint.setShader(null);
                            } else {
                                textPaint.setColor(previewColorBuffer[start]);
                                canvas.drawCircle(circleX, circleY, circleRadius, textPaint);
                            }

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
                int subRows = getSubRowCount(line);
                for (int sr = 0; sr < subRows; sr++) {
                    int srStart = getSubRowStart(line, sr);
                    int srEndRow = getSubRowEnd(line, sr);
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

    private void drawSearchDecorations(Canvas canvas, int firstLine, int lastLine,
                                       float paddingLeft, float paddingTop) {
        if (searchDecorations == null || searchDecorations.isEmpty()) return;

        int total = content.totalLength();
        int firstLineFlat = content.flatOffset(new ContentPosition(firstLine, 0));
        int lastLineEndCol = content.lineLength(lastLine);
        int lastLineFlat = content.flatOffset(new ContentPosition(lastLine, lastLineEndCol));

        // Binary search for first decoration whose absoluteEnd >= firstLineFlat
        int low = 0;
        int high = searchDecorations.size() - 1;
        int startIndex = searchDecorations.size();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (searchDecorations.get(mid).absoluteEnd >= firstLineFlat) {
                startIndex = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        for (int i = startIndex; i < searchDecorations.size(); i++) {
            SearchResult r = searchDecorations.get(i);
            if (r.absoluteStart > lastLineFlat) {
                break; // Ordered by start offset — no subsequent match can be visible
            }
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
                    int subRows = getSubRowCount(line);
                    for (int sr = 0; sr < subRows; sr++) {
                        int srStart = getSubRowStart(line, sr);
                        int srEndRow = getSubRowEnd(line, sr);
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
        if (!bracketHighlighting) return;
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
        if (!showSquigglyLines || currentProblems == null || currentProblems.isEmpty()) return;

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

            if (wordWrap) {
                float x0 = paddingLeft + getCursorX(lineIdx, colStart);
                float x1 = paddingLeft + getCursorX(lineIdx, colEnd);
                if (colEnd > colStart && visualSubRow(lineIdx, colEnd) > visualSubRow(lineIdx, colStart)) {
                    // problem spans multiple subrows, just draw for the first subrow
                    x1 = paddingLeft + getWidth() - getPaddingRight();
                }
                float waveY = paddingTop + absoluteVisualRow(lineIdx, colStart) * lineHeightPx + lineHeightPx + 2;
                float amp = 2.5f;
                float period = 8f;
                float half = period / 2f;

                reusableDiagnosticPath.reset();
                reusableDiagnosticPath.moveTo(x0, waveY);
                boolean up = true;
                for (float cx2 = x0; cx2 < x1; cx2 += half) {
                    float ex = Math.min(cx2 + half, x1);
                    float mid = (cx2 + ex) / 2f;
                    float ctlY = up ? waveY - amp : waveY + amp;
                    reusableDiagnosticPath.quadTo(mid, ctlY, ex, waveY);
                    up = !up;
                }
                canvas.drawPath(reusableDiagnosticPath, diagnosticPaint);
            } else {
                if (problem.getCachedPath() == null) {
                    float x0 = paddingLeft + getCursorX(lineIdx, colStart);
                    float x1 = paddingLeft + getCursorX(lineIdx, colEnd);
                    float waveY = paddingTop + absoluteVisualRow(lineIdx, colStart) * lineHeightPx + lineHeightPx + 2;
                    float amp = 2.5f;
                    float period = 8f;
                    float half = period / 2f;

                    Path path = new Path();
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
        canvas.drawCircle(cx, handleY, handleRadiusPx, handlePaint);
    }

    private int hitTestHandle(float touchX, float touchY) {
        if (selectionAnchor == null) return HANDLE_DRAG_NONE;
        float threshold = handleThresholdPx;

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
                    if (selectionChangeListener != null) {
                        selectionChangeListener.onSelectionChanged(false);
                    }
                    return true;
                }
            }
            activeDragHandle = HANDLE_DRAG_NONE;

            requestFocus();
            overScroller.abortAnimation();
            // Soft keyboard is triggered in onSingleTapUp() once a stationary tap is confirmed,
            // ensuring scroll gestures (ACTION_DOWN followed by MOVE) do not open the IME.
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

    // Key events

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
                cursorVisible = true;
                scheduleBlink();
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

        boolean deletePair = false;
        if (deleteMatchingPairs
                && newPos.line == cursor.line
                && cursor.column < content.lineLength(cursor.line)
                && (autoCloseBrackets || autoCloseQuotes)) {
            char before = deleted.charAt(0);
            char after = content.charAt(cursor.line, cursor.column);
            if ((before == '{' && after == '}')
                    || (before == '[' && after == ']')
                    || (before == '(' && after == ')')
                    || (before == '"' && after == '"')
                    || (before == '\'' && after == '\'')
                    || (before == '`' && after == '`')) {
                deletePair = true;
            }
        }

        ContentPosition oldCursor = cursor;
        ContentPosition delEnd = deletePair ? content.positionAt(cursorFlat + 1) : oldCursor;
        String totalDeleted = deletePair ? (deleted + content.charAt(cursor.line, cursor.column)) : deleted;

        content.delete(newPos.line, newPos.column, delEnd.line, delEnd.column);
        undoStack.recordDelete(newPos.line, newPos.column, delEnd.line, delEnd.column,
                totalDeleted, snapshotAt(oldCursor, null), snapshotAt(newPos, null));
        cursor = newPos;
        selectionAnchor = null;
        cursorVisible = true;
        scheduleBlink();
        invalidate();
        scheduleHighlight();
        mainHandler.removeCallbacks(bracketMatchRunnable);
        mainHandler.postDelayed(bracketMatchRunnable, 150);
    }

    // Hardware-keyboard helpers (called from onKeyDown)

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
        boolean mayHaveSideEffects = true;
        if (mayHaveSideEffects) {
            undoStack.beginAtomicGroup();
        }

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
        if (mayHaveSideEffects) {
            char typed = '\n';
            if (autoCloseBrackets) {
                handleAutoClose(new ContentCharSequence(content), beforeFlat + 1, typed);
            }
            int indentTextEnd = Math.min(content.totalLength(), beforeFlat + 2);
            handleAutoIndent(content.getSubstring(0, indentTextEnd), beforeFlat);
            mainHandler.post(undoStack::endAtomicGroup);
        }
        invalidate();
        cursorVisible = true;
        scheduleBlink();
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
        if (autoCompletePopup != null) {
            autoCompletePopup.dismiss();
        }
    }

    public void showSignatureHint(LspSignatureHelp help) {
        if (help == null) {
            signatureHintPopup.dismiss();
            return;
        }
        int flatCursor = content.flatOffset(cursor);
        signatureHintPopup.show(help, this, flatCursor);
    }

    public void dismissSignatureHint() {
        if (signatureHintPopup != null) {
            signatureHintPopup.dismiss();
        }
    }

    public boolean isSignatureHintVisible() {
        return signatureHintPopup != null && signatureHintPopup.isShowing();
    }

    public boolean isAutoCompleteVisible() {
        return autoCompletePopup != null && autoCompletePopup.isShowing();
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
     * Returns true if the editor currently has a non-empty text selection.
     */
    public boolean hasSelection() {
        return selectionAnchor != null && getSelectionStart() != getSelectionEnd();
    }

    /**
     * Returns true if the entire document is currently selected.
     */
    public boolean isAllSelected() {
        return hasSelection() && getSelectionStart() == 0 && getSelectionEnd() >= length();
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

    public void addCursorChangeListener(Runnable listener) {
        if (!cursorChangeListeners.contains(listener)) {
            cursorChangeListeners.add(listener);
        }
    }

    public void removeCursorChangeListener(Runnable listener) {
        cursorChangeListeners.remove(listener);
    }

    /**
     * Notifies any registered selection-change observer (e.g. to show/hide the SelectionToolbar).
     * Called whenever the selection state changes.
     */
    private void notifySelectionChanged() {
        cursorVisible = true;
        scheduleBlink();
        // Only show/hide the selection toolbar for genuine user-initiated selections
        // (long-press, double-tap, drag handles, selectAll).
        // IME-driven selections (backspace-slide, spacebar-slide) set
        // isSettingSelectionFromIme = true — skip the toolbar in that case.
        if (selectionChangeListener != null && !isSettingSelectionFromIme) {
            selectionChangeListener.onSelectionChanged(selectionAnchor != null);
        }
        for (Runnable listener : cursorChangeListeners) {
            listener.run();
        }
        
        mainHandler.removeCallbacks(cursorIdleRunnable);
        if (selectionAnchor == null) {
            mainHandler.postDelayed(cursorIdleRunnable, 400);
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

    public void setOnCursorIdleListener(OnCursorIdleListener listener) {
        this.cursorIdleListener = listener;
    }

    public void setText(CharSequence text, Object bufferType) {
        if (autoCompletePopup != null) autoCompletePopup.dismiss();
        final String textStr = text != null ? text.toString() : "";
        final long myToken = ++textLoadToken;
        isSettingText = true;
        if (!textLoadListeners.isEmpty()) {
            mainHandler.post(() -> {
                for (OnTextLoadListener l : textLoadListeners) l.onTextLoadStateChanged(true);
            });
        }
        ExecutorProvider.getInstance().runOnCpu(() -> {
            Content.LoadedLines loaded;
            try {
                loaded = Content.prepareLoad(textStr);
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    if (myToken == textLoadToken) {
                        isSettingText = false;
                        for (OnTextLoadListener l : textLoadListeners) l.onTextLoadStateChanged(false);
                    }
                });
                return;
            }
            mainHandler.post(() -> {
                if (myToken != textLoadToken) return;
                try {
                    content.applyLoaded(loaded);
                    cursor = ContentPosition.ZERO;
                    selectionAnchor = null;
                    undoStack.reset();
                    longestLineLength = loaded.longestLineLength;
                    longestLineDirty = false;
                    dirtyTracker.reset();
                    dirtyTracker.addEdit(0, 0, content.totalLength());
                    rebuildVisualLayout();
                    requestLayout();
                    invalidate();
                    scheduleHighlight();
                    notifySelectionChanged();
                } finally {
                    isSettingText = false;
                    for (OnTextLoadListener l : textLoadListeners) l.onTextLoadStateChanged(false);
                }
            });
        });
    }

    public boolean isSettingText() {
        return isSettingText;
    }

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
        if (listener != null && !contentChangeListeners.contains(listener)) {
            contentChangeListeners.add(listener);
        }
    }

    /**
     * Registers a listener for text-load lifecycle events (fired by {@link #setText(CharSequence, Object)}).
     * Adds to an internal list rather than replacing — safe to call from multiple independent
     * observers (e.g. a loading-state UI and {@code LspEditorBridge}) without one clobbering another.
     */
    public void addTextLoadListener(OnTextLoadListener listener) {
        if (listener != null && !textLoadListeners.contains(listener)) {
            textLoadListeners.add(listener);
        }
    }

    public void removeTextLoadListener(OnTextLoadListener listener) {
        textLoadListeners.remove(listener);
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

    public int getEditorLineHeight() {
        return lineHeightPx;
    }

    public int getFirstVisibleLine() {
        return visualRowToLogicalLine(getScrollY() / lineHeightPx);
    }

    public int getLogicalLineCount() {
        return content.lineCount();
    }

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

    public void clearSearchDecorations() {
        searchDecorations.clear();
        searchActiveIndex = -1;
        invalidate();
    }

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

    public void setOnScrollChangeListener(OnScrollChangeListener listener) {
        this.scrollChangeListener = listener;
    }

    public void applyDiagnostics(List<Problem> problems) {
        this.currentProblems = problems != null ? problems : new ArrayList<>();
        invalidate();
    }

    public boolean isShowSquigglyLines() {
        return showSquigglyLines;
    }

    public void setShowSquigglyLines(boolean showSquigglyLines) {
        if (this.showSquigglyLines != showSquigglyLines) {
            this.showSquigglyLines = showSquigglyLines;
            invalidate();
        }
    }

    public boolean isDeleteMatchingPairs() {
        return deleteMatchingPairs;
    }

    public void setDeleteMatchingPairs(boolean deleteMatchingPairs) {
        this.deleteMatchingPairs = deleteMatchingPairs;
    }

    public boolean isForceLargeFileHighlighting() {
        return forceLargeFileHighlighting;
    }

    public void setForceLargeFileHighlighting(boolean forceLargeFileHighlighting) {
        if (this.forceLargeFileHighlighting != forceLargeFileHighlighting) {
            this.forceLargeFileHighlighting = forceLargeFileHighlighting;
            scheduleHighlight();
            invalidate();
        }
    }

    public boolean isRainbowBrackets() {
        return rainbowBrackets;
    }

    public void setRainbowBrackets(boolean rainbowBrackets) {
        if (this.rainbowBrackets != rainbowBrackets) {
            this.rainbowBrackets = rainbowBrackets;
            if (content != null) {
                content.acquireReadLock();
                try {
                    int count = content.lineCount();
                    for (int i = 0; i < count; i++) {
                        com.cocode.vcode.ide.core.editor.text.ContentLine line = content.getLine(i);
                        if (line != null) {
                            line.tokens = null;
                        }
                    }
                } finally {
                    content.releaseReadLock();
                }
            }
            invalidate();
        }
    }

    public boolean isBracketHighlighting() {
        return bracketHighlighting;
    }

    public void setBracketHighlighting(boolean bracketHighlighting) {
        if (this.bracketHighlighting != bracketHighlighting) {
            this.bracketHighlighting = bracketHighlighting;
            if (!bracketHighlighting) {
                bracketMatchOpen = null;
                bracketMatchClose = null;
            } else if (content != null) {
                mainHandler.removeCallbacks(bracketMatchRunnable);
                bracketMatchRunnable.run();
            }
            invalidate();
        }
    }

    public FileType getFileType() {
        return fileType;
    }

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
                    MarkdownAutoCompleteEngine mdEngine = new MarkdownAutoCompleteEngine(ctx);
                    if (currentFile != null) mdEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = mdEngine;
                    break;
                case GITIGNORE:
                    this.syntaxHighlighter = null; // Assuming no syntax highlighter for gitignore
                    com.cocode.vcode.ide.core.autocomplete.PathAutoCompleteEngine gitignoreEngine = new com.cocode.vcode.ide.core.autocomplete.PathAutoCompleteEngine(ctx);
                    if (currentFile != null) gitignoreEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = gitignoreEngine;
                    break;
                case SVG:
                    this.syntaxHighlighter = new SvgSyntaxHighlighter(ctx);
                    SvgAutoCompleteEngine svgEngine = new SvgAutoCompleteEngine(ctx);
                    if (currentFile != null) svgEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = svgEngine;
                    break;
                default:
                    this.syntaxHighlighter = null;
                    this.autoCompleteEngine = null;
                    break;
            }
        }
        scheduleHighlight();
    }

    // Public API — diagnostics

    public void setCurrentFile(File file) {
        this.currentFile = file;
        if (autoCompleteEngine != null) {
            autoCompleteEngine.setCurrentFile(file);
        }
    }

    // Public API — settings

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
            lineWrapBreaks = null;
            cachedWrapCharsPerRow = -1;
            return;
        }
        int n = content.lineCount();
        if (visualRowStarts == null || visualRowStarts.length < n + 1)
            visualRowStarts = new int[n + 65];
        if (lineWrapBreaks == null || lineWrapBreaks.length < n)
            lineWrapBreaks = new int[n + 64][];

        int charsPerRow = Math.max(1, (int) ((getWidth() - getPaddingLeft() - getPaddingRight()) / charWidth));
        cachedWrapCharsPerRow = charsPerRow;

        int row = 0;
        for (int i = 0; i < n; i++) {
            visualRowStarts[i] = row;
            int lineLen = content.lineLength(i);
            if (lineLen <= charsPerRow) {
                lineWrapBreaks[i] = null;
                row += 1;
            } else {
                int[] breaks = WordWrapHelper.computeLineWrapBreaks(content.getLine(i), lineLen, charsPerRow);
                lineWrapBreaks[i] = breaks;
                row += (breaks != null ? breaks.length : 1);
            }
        }
        visualRowStarts[n] = row;
        totalVisualRows = row;
    }

    private int getSubRowCount(int line) {
        if (!wordWrap || visualRowStarts == null || line + 1 >= visualRowStarts.length) return 1;
        return Math.max(1, visualRowStarts[line + 1] - visualRowStarts[line]);
    }

    private int getSubRowStart(int line, int sr) {
        if (!wordWrap || lineWrapBreaks == null || line >= lineWrapBreaks.length) return 0;
        return WordWrapHelper.getSubRowStart(lineWrapBreaks[line], sr);
    }

    private int getSubRowEnd(int line, int sr) {
        int lineLen = content.lineLength(line);
        if (!wordWrap || lineWrapBreaks == null || line >= lineWrapBreaks.length) return lineLen;
        return WordWrapHelper.getSubRowEnd(lineWrapBreaks[line], sr, lineLen);
    }

    private int visualRowOf(int logicalLine) {
        if (visualRowStarts == null || logicalLine >= visualRowStarts.length) return logicalLine;
        return visualRowStarts[logicalLine];
    }

    private int visualSubRow(int line, int col) {
        if (!wordWrap || lineWrapBreaks == null || line >= lineWrapBreaks.length) return 0;
        return WordWrapHelper.visualSubRow(lineWrapBreaks[line], col);
    }

    private int colInSubRow(int line, int col) {
        if (!wordWrap || lineWrapBreaks == null || line >= lineWrapBreaks.length) return col;
        return WordWrapHelper.colInSubRow(lineWrapBreaks[line], col);
    }

    private int absoluteVisualRow(int logicalLine, int col) {
        return visualRowOf(logicalLine) + visualSubRow(logicalLine, col);
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

        boolean isMarkdownTable = formattedSnippet.contains("| ---") || formattedSnippet.contains("|---");
        int pipeIndex = isMarkdownTable ? -1 : formattedSnippet.indexOf('|');
        if (pipeIndex != -1) {
            formattedSnippet = formattedSnippet.substring(0, pipeIndex)
                    + formattedSnippet.substring(pipeIndex + 1).replace("|", "");
        }

        isApplyingHighlight = true;
        ContentPosition insertStart = cursor;
        UndoStack.EditorSnapshot before = snapshotAt(insertStart, selectionAnchor);
        content.insert(cursor.line, cursor.column, formattedSnippet);
        if (pipeIndex != -1) {
            cursor = content.positionAt(flatCursor + pipeIndex);
        } else if (isMarkdownTable) {
            int firstCell = formattedSnippet.indexOf("| ");
            cursor = content.positionAt(flatCursor + (firstCell != -1 ? firstCell + 2 : 0));
        } else {
            cursor = content.positionAt(flatCursor + formattedSnippet.length());
        }
        UndoStack.EditorSnapshot after = snapshotAt(cursor, null);
        // Commit any pending group first so the snippet is always its own undo step.
        undoStack.commitPending();
        undoStack.recordInsert(insertStart.line, insertStart.column, formattedSnippet, before, after);
        undoStack.commitPending();
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

    // Public API — snippet

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

    // Public API — undo / redo

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
        if (signatureHintPopup != null) signatureHintPopup.dismiss();
    }

    private void scheduleHighlight() {
        if (syntaxHighlighter == null) return;
        if (content.lineCount() > LARGE_FILE_LINE_THRESHOLD && !forceLargeFileHighlighting) {
            return;
        }

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
                int newDepth = BracketMatcher.computeBracketDepth(line, depth, internalState, fileType);
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
                final FileType bgFileType = fileType;

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
                            int newDepth = BracketMatcher.computeBracketDepth(line, depth, internalState, bgFileType);

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

    // Lifecycle

    private void triggerAutoComplete() {
        if (autoCompleteEngine == null) return;
        // LSP bridge has taken over completions for this language — skip legacy engine.
        if (lspCompletionActive) return;

        int totalLen = content.totalLength();
        int flatCursor = content.flatOffset(cursor);

        if (flatCursor <= 0 || flatCursor > totalLen) {
            autoCompletePopup.dismiss();
            return;
        }

        // Fast character checks directly without allocating or fetching full text
        char lastChar = (cursor.column > 0)
                ? content.charAt(cursor.line, cursor.column - 1)
                : '\n';

        if (Character.isWhitespace(lastChar)) {
            autoCompletePopup.dismiss();
            return;
        }

        char prevChar = (cursor.column >= 2)
                ? content.charAt(cursor.line, cursor.column - 2)
                : (flatCursor >= 2 ? content.getSubstring(flatCursor - 2, flatCursor - 1).charAt(0) : '\0');

        boolean isTriggerChar = TRIGGER_CHARS.indexOf(lastChar) >= 0
                || (lastChar == '{' && flatCursor >= 2 && prevChar == '$');
        boolean isIdentifier = Character.isLetterOrDigit(lastChar)
                || lastChar == '_' || lastChar == '$'
                || (lastChar == '-' && (fileType == FileType.CSS || fileType == FileType.HTML));

        if (!isIdentifier && !isTriggerChar) {
            autoCompletePopup.dismiss();
            return;
        }

        String fullText = getCachedFullText();

        if (isCursorInComment(fullText, flatCursor)) {
            autoCompletePopup.dismiss();
            return;
        }

        final int capturedCursor = flatCursor;
        final String capturedText = fullText;

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

    // Internal — highlight

    /**
     * Returns true if the editor is currently inserting a completion item.
     */
    public boolean isInsertingCompletion() {
        return isInsertingCompletion;
    }

    /**
     * Injects selected autocomplete text, properly computing replace range.
     */
    void insertCompletion(CompletionItem item) {
        if (item == null) return;
        String insertText = item.getEffectiveInsertText();
        if (insertText == null) return;

        isInsertingCompletion = true;
        try {
            int flatCursor = content.flatOffset(cursor);
            int lineStartFlat = flatCursor - cursor.column;
            String linePrefix = content.getSubstring(lineStartFlat, flatCursor);

            // Compute wordStart
            int col = cursor.column;
            if (item.getReplaceLength() >= 0) {
                col = Math.max(0, cursor.column - item.getReplaceLength());
            } else {
                while (col > 0) {
                    char c = linePrefix.charAt(col - 1);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$' || c == '@') {
                        col--;
                    } else break;
                }
                if (col > 0 && linePrefix.charAt(col - 1) == '<' && insertText.startsWith("<")) {
                    col--;
                }
                if (col > 0 && linePrefix.charAt(col - 1) == '&' && insertText.startsWith("&")) {
                    col--;
                }
            }
            int wordStart = lineStartFlat + col;

            // Handle indentation for multi-line insertions
            StringBuilder baseIndent = new StringBuilder();
            for (int i = 0; i < col; i++) {
                char c = linePrefix.charAt(i);
                if (c == ' ' || c == '\t') baseIndent.append(c);
                else break;
            }
            String rawInsertText = insertText;
            if (insertText.contains("\n")) {
                insertText = insertText.replace("\n", "\n" + baseIndent);
            }

            // Check if this is a Markdown table snippet
            boolean isMarkdownTable = insertText.contains("| ---") || insertText.contains("|---");

            // Handle cursor marker or offset
            int pipeIdx = isMarkdownTable ? -1 : insertText.indexOf('|');
            String cleanInsert;
            int finalCursorFlat;
            if (pipeIdx >= 0) {
                // Strip the cursor pipe and any subsequent marker pipes
                cleanInsert = insertText.substring(0, pipeIdx)
                        + insertText.substring(pipeIdx + 1).replace("|", "");
                finalCursorFlat = wordStart + pipeIdx;
            } else if (isMarkdownTable) {
                cleanInsert = insertText;
                int firstCell = insertText.indexOf("| ");
                finalCursorFlat = wordStart + (firstCell != -1 ? firstCell + 2 : 0);
            } else if (item.getCursorOffset() > 0) {
                cleanInsert = insertText;
                // Count newlines before cursorOffset in rawInsertText to adjust for baseIndent
                int targetOffset = Math.min(rawInsertText.length(), item.getCursorOffset());
                int newlinesBefore = 0;
                for (int i = 0; i < targetOffset; i++) {
                    if (rawInsertText.charAt(i) == '\n') newlinesBefore++;
                }
                int adjustedOffset = item.getCursorOffset() + (newlinesBefore * baseIndent.length());
                finalCursorFlat = wordStart + Math.min(cleanInsert.length(), adjustedOffset);
            } else if (item.getCursorOffset() < 0) {
                cleanInsert = insertText;
                finalCursorFlat = wordStart + Math.max(0, cleanInsert.length() + item.getCursorOffset());
            } else {
                cleanInsert = insertText;
                finalCursorFlat = wordStart + cleanInsert.length();
            }

            // Deduplicate trailing bracket/quote
            int docLen = content.totalLength();
            if (!cleanInsert.isEmpty() && flatCursor < docLen) {
                char lastInserted = cleanInsert.charAt(cleanInsert.length() - 1);
                char nextInDoc = (cursor.column < content.lineLength(cursor.line))
                        ? content.charAt(cursor.line, cursor.column)
                        : '\n';
                if ((lastInserted == ')' || lastInserted == ']' || lastInserted == '}'
                        || lastInserted == '"' || lastInserted == '\'')
                        && lastInserted == nextInDoc) {
                    cleanInsert = cleanInsert.substring(0, cleanInsert.length() - 1);
                    finalCursorFlat = Math.min(finalCursorFlat, wordStart + cleanInsert.length());
                }
            }
        ContentPosition wordStartPos = content.positionAt(wordStart);
        ContentPosition beforeCursor = cursor;
        UndoStack.EditorSnapshot before = snapshotAt(beforeCursor, selectionAnchor);
        String deletedText = "";
        int deleteEnd = flatCursor + Math.max(0, item.getReplaceAfterLength());
        deleteEnd = Math.min(deleteEnd, content.totalLength());

        // Clean up redundant trailing delimiter left by auto-close or duplicate bracket
        if (deleteEnd < content.totalLength() && wordStart < flatCursor) {
            String replacedPrefix = content.getSubstring(wordStart, flatCursor);
            ContentPosition nextPos = content.positionAt(deleteEnd);
            if (nextPos.column < content.lineLength(nextPos.line)) {
                char nextCharInDoc = content.charAt(nextPos.line, nextPos.column);
                if (nextCharInDoc == '}') {
                    boolean hadBrace = replacedPrefix.contains("{");
                    if (hadBrace && (autoCloseBrackets || item.getReplaceAfterLength() > 0 || isUnclosedDelimiter(replacedPrefix, '{', '}'))) {
                        deleteEnd++;
                    }
                } else if (nextCharInDoc == ']') {
                    boolean hadBracket = replacedPrefix.contains("[");
                    if (hadBracket && (autoCloseBrackets || item.getReplaceAfterLength() > 0 || isUnclosedDelimiter(replacedPrefix, '[', ']'))) {
                        deleteEnd++;
                    }
                }
            }
        }
        try {
            if (deleteEnd > wordStart) {
                deletedText = content.getSubstring(wordStart, deleteEnd);
            }
        } catch (Exception ignored) {
        }
        ContentPosition deleteEndPos = content.positionAt(deleteEnd);
        content.replace(wordStartPos.line, wordStartPos.column,
                deleteEndPos.line, deleteEndPos.column, cleanInsert);
        int safeFinal = Math.min(finalCursorFlat, content.totalLength());
        cursor = content.positionAt(safeFinal);
        UndoStack.EditorSnapshot after = snapshotAt(cursor, null);
        // Commit any pending group first, then seal the completion as its own atomic undo step.
        undoStack.commitPending();
        if (!deletedText.isEmpty()) {
            undoStack.recordReplace(wordStartPos.line, wordStartPos.column,
                    deleteEndPos.line, deleteEndPos.column, deletedText, cleanInsert, before, after);
        } else {
            undoStack.recordInsert(wordStartPos.line, wordStartPos.column, cleanInsert, before, after);
        }
        undoStack.commitPending();
        selectionAnchor = null;

        autoCompletePopup.dismiss();
        scheduleHighlight();
        invalidate();
        } finally {
            isInsertingCompletion = false;
        }
    }

    // Internal — autocomplete

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
            UndoStack.EditorSnapshot snapBefore = snapshotAt(cursor, null);
            content.insert(pos.line, pos.column, toInsert);
            // cursor stays before the inserted closing char — don't advance
            UndoStack.EditorSnapshot snapAfter = snapshotAt(cursor, null);
            undoStack.recordInsert(pos.line, pos.column, toInsert, snapBefore, snapAfter);
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

    private static boolean isUnclosedDelimiter(String s, char open, char close) {
        if (s == null) return false;
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) depth++;
            else if (c == close) depth--;
        }
        return depth > 0;
    }

    private void handleAutoCloseHtmlTag(int cursorAfterGt) {
        mainHandler.post(() -> {
            String currentText = content.getSubstring(0, Math.min(content.totalLength(), cursorAfterGt));
            String tagName = htmlTagParser.getCurrentOpenTagName(currentText, cursorAfterGt - 1);
            if (tagName == null || tagName.isEmpty() || HtmlTagParser.isVoidElement(tagName)) {
                return;
            }

            String closing = "</" + tagName + ">";
            isAutoClosing = true;
            ContentPosition insertPos = content.positionAt(cursorAfterGt);
            UndoStack.EditorSnapshot snapBefore = snapshotAt(cursor, null);
            content.insert(insertPos.line, insertPos.column, closing);
            UndoStack.EditorSnapshot snapAfter = snapshotAt(cursor, null);
            undoStack.recordInsert(insertPos.line, insertPos.column, closing, snapBefore, snapAfter);
            isAutoClosing = false;
            scheduleHighlight();
            invalidate();
        });
    }

    // Internal — auto-close brackets / indent

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
                    UndoStack.EditorSnapshot snapBefore = snapshotAt(cursor, null);
                    content.insert(insertPos.line, insertPos.column, injection);
                    // cursor is placed at end of inner-indent line
                    ContentPosition newCursor = content.positionAt(insertFlat + finalInnerIndent.length());
                    cursor = newCursor;
                    UndoStack.EditorSnapshot snapAfter = snapshotAt(cursor, null);
                    undoStack.recordInsert(insertPos.line, insertPos.column, injection, snapBefore, snapAfter);
                } else {
                    UndoStack.EditorSnapshot snapBefore = snapshotAt(cursor, null);
                    content.insert(insertPos.line, insertPos.column, finalInnerIndent);
                    ContentPosition newCursor = content.positionAt(insertFlat + finalInnerIndent.length());
                    cursor = newCursor;
                    UndoStack.EditorSnapshot snapAfter = snapshotAt(cursor, null);
                    undoStack.recordInsert(insertPos.line, insertPos.column, finalInnerIndent, snapBefore, snapAfter);
                }
                // Use setSelection only for scroll-sync, cursor already set above.
                setSelection(content.flatOffset(cursor));
                isApplyingHighlight = false;
            });
        }
    }

    private void updateBracketMatch(CharSequence text, int cursorFlat) {
        bracketMatchOpen = null;
        bracketMatchClose = null;
        if (!bracketHighlighting) {
            invalidate();
            return;
        }
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

    // Internal — bracket match

    private float dpToPx(float dp) {
        return dp * density;
    }

    private float dpToPx(float dp, Context ctx) {
        return dpToPx(dp);
    }

    Paint getCursorPaint() {
        return cursorPaint;
    }

    float getDensity() {
        return density;
    }

    // Internal — comment detection

    private void updateLongestLine(int changedLine) {
        int len = content.lineLength(changedLine);
        if (len > longestLineLength) longestLineLength = len;
    }

    // Internal — helpers

    private int getLongestLineLength() {
        if (longestLineDirty) {
            longestLineLength = content.longestLineLength();
            longestLineDirty = false;
        }
        return longestLineLength;
    }

    private String buildTabSpaces() {
        return tabSpaces;
    }

    private float getCursorX(int line, int col) {
        int effectiveCol = wordWrap ? colInSubRow(line, col) : col;
        float cx = effectiveCol * charWidth;
        java.util.List<com.cocode.vcode.ide.core.editor.highlight.HighlightToken> tokens = content.getLine(line).tokens;
        if (tokens != null) {
            int subRowStart = wordWrap ? getSubRowStart(line, visualSubRow(line, col)) : 0;
            for (com.cocode.vcode.ide.core.editor.highlight.HighlightToken t : tokens) {
                if (t.hasPreviewColor && t.startCol >= subRowStart && t.startCol < col) {
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

        int subRow = wordWrap ? Math.max(0, visualRow - visualRowOf(logicalLine)) : 0;
        int colOffset = wordWrap ? getSubRowStart(logicalLine, subRow) : 0;
        int colEnd = wordWrap ? getSubRowEnd(logicalLine, subRow) : content.lineLength(logicalLine);
        int subRowLen = Math.max(0, colEnd - colOffset);

        float relativeX = touchX + (wordWrap ? 0 : getScrollX()) - getPaddingLeft();
        int lineLen = content.lineLength(logicalLine);

        java.util.List<com.cocode.vcode.ide.core.editor.highlight.HighlightToken> tokens = content.getLine(logicalLine).tokens;

        boolean hasCircles = false;
        if (tokens != null) {
            for (com.cocode.vcode.ide.core.editor.highlight.HighlightToken t : tokens) {
                if (t.hasPreviewColor && t.startCol >= colOffset && t.startCol < colEnd) {
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
            while (colInSub < subRowLen) {
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
        colInSub = Math.max(0, Math.min(colInSub, subRowLen));
        int col = colOffset + colInSub;
        return new ContentPosition(logicalLine, Math.max(0, Math.min(col, lineLen)));
    }

    /**
     * Selects the word at {@code pos}, mirroring Android stock EditText long-press behaviour.
     *
     * <ul>
     *   <li>Word characters: letter, digit, underscore, dollar sign (same as
     *       {@code android.text.method.WordIterator}).</li>
     *   <li>If the finger lands on a word character, the enclosing word is selected.</li>
     *   <li>If the finger lands at the end of a line or whitespace immediately after a word, that word is selected.</li>
     *   <li>If the line is empty or pos is on punctuation/whitespace not touching a word, returns {@code false}.</li>
     * </ul>
     *
     * @return {@code true} if a non-empty selection was made.
     */
    boolean selectWordAt(ContentPosition pos) {
        if (pos == null || pos.line < 0 || pos.line >= content.lineCount()) return false;
        int lineLen = content.lineLength(pos.line);
        if (lineLen == 0) return false;

        int targetCol = -1;
        if (pos.column < lineLen && isWordChar(content.charAt(pos.line, pos.column))) {
            targetCol = pos.column;
        } else if (pos.column > 0 && isWordChar(content.charAt(pos.line, pos.column - 1))) {
            // Only select preceding word if touch landed on trailing whitespace or at the end of the line
            // (prevents selecting words across punctuation like ';' or '>')
            if (pos.column >= lineLen || Character.isWhitespace(content.charAt(pos.line, pos.column))) {
                targetCol = pos.column - 1;
            }
        }

        if (targetCol == -1) return false;

        // Expand left to word start
        int wordStart = targetCol;
        while (wordStart > 0 && isWordChar(content.charAt(pos.line, wordStart - 1))) {
            wordStart--;
        }

        // Expand right to word end
        int wordEnd = targetCol + 1;
        while (wordEnd < lineLen && isWordChar(content.charAt(pos.line, wordEnd))) {
            wordEnd++;
        }

        if (wordStart >= wordEnd) return false;

        // Apply selection: anchor at start, cursor at end (matches EditText convention)
        selectionAnchor = new ContentPosition(pos.line, wordStart);
        cursor = new ContentPosition(pos.line, wordEnd);
        return true;
    }

    public interface OnContentChangeListener {
        void onContentChanged();
    }

    public interface OnTextLoadListener {
        void onTextLoadStateChanged(boolean isLoading);
    }

    /**
     * Listener notified when selection becomes active, collapses, or when an empty area is long-pressed.
     */
    public interface OnSelectionChangeListener {
        void onSelectionChanged(boolean hasSelection);
        void onEmptyLongPress();
    }

    public interface OnCursorIdleListener {
        void onCursorIdle(int flatOffset);
    }

    public interface OnScrollChangeListener {
        void onScrollChanged(int scrollX, int scrollY);
    }

    /**
     * CharSequence adapter backed by the underlying {@link Content} model.
     * {@link #toString()} materializes the full text, while {@link #length()} is O(1).
     */
    private static final class ContentCharSequence implements CharSequence {
        private final Content content;
        private int cachedIndex = -1;
        private int cachedLine = 0;
        private int cachedCol = 0;
        private int cachedLineLen = -1;
        private long cachedVersion = -1;

        ContentCharSequence(Content c) {
            this.content = c;
        }

        @Override
        public int length() {
            return content.totalLength();
        }

        @Override
        public char charAt(int index) {
            long version = content.getVersion();
            if (version != cachedVersion) {
                cachedVersion = version;
                cachedIndex = -1;
            }

            int line, col;
            if (cachedIndex >= 0 && index == cachedIndex + 1 && cachedLineLen >= 0) {
                if (cachedCol < cachedLineLen) {
                    line = cachedLine;
                    col = cachedCol + 1;
                } else {
                    line = cachedLine + 1;
                    col = 0;
                    cachedLineLen = (line < content.lineCount()) ? content.lineLength(line) : 0;
                }
            } else if (cachedIndex >= 0 && index == cachedIndex) {
                line = cachedLine;
                col = cachedCol;
            } else {
                ContentPosition pos = content.positionAt(index);
                line = pos.line;
                col = pos.column;
                cachedLineLen = content.lineLength(line);
            }

            cachedIndex = index;
            cachedLine = line;
            cachedCol = col;

            if (col < cachedLineLen) {
                return content.charAt(line, col);
            }
            return '\n';
        }

        @NonNull
        @Override
        public CharSequence subSequence(int start, int end) {
            return content.getSubstring(start, end);
        }

        @NonNull
        @Override
        public String toString() {
            return content.getText();
        }
    }

    // Inner classes

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

                // If IME commits text ending with a closing bracket/quote matching the char right after composing range, skip over it
                boolean skippedOver = false;
                if (!insertText.isEmpty() && end < total) {
                    char lastCh = insertText.charAt(insertText.length() - 1);
                    boolean isBracket = (lastCh == ')' || lastCh == ']' || lastCh == '}') && editor.autoCloseBrackets;
                    boolean isQuote = (lastCh == '"' || lastCh == '\'' || lastCh == '`') && editor.autoCloseQuotes;
                    if ((isBracket || isQuote) && endPos.column < editor.content.lineLength(endPos.line)
                            && editor.content.charAt(endPos.line, endPos.column) == lastCh) {
                        String cleanInsert = insertText.substring(0, insertText.length() - 1);
                        editor.content.replace(startPos.line, startPos.column, endPos.line, endPos.column, cleanInsert);
                        ContentPosition after = editor.content.positionAt(start + cleanInsert.length() + 1);
                        editor.cursor = after;
                        editor.selectionAnchor = null;
                        if (!deleted.isEmpty() || !cleanInsert.isEmpty()) {
                            editor.undoStack.recordReplace(startPos.line, startPos.column, endPos.line, endPos.column,
                                    deleted, cleanInsert, before, editor.snapshotAt(after, null));
                        }
                        skippedOver = true;
                    }
                }

                if (!skippedOver) {
                    editor.content.replace(startPos.line, startPos.column, endPos.line, endPos.column, insertText);
                    ContentPosition after = editor.content.positionAt(start + insertText.length());
                    editor.cursor = after;
                    editor.selectionAnchor = null;

                    if (!deleted.isEmpty() || !insertText.isEmpty()) {
                        editor.undoStack.recordReplace(startPos.line, startPos.column, endPos.line, endPos.column,
                                deleted, insertText, before, editor.snapshotAt(after, null));
                    }
                }

                editor.composingStart = -1;
                editor.composingEnd = -1;
                editor.post(editor::ensureCursorVisible);
                editor.cursorVisible = true;
                editor.scheduleBlink();
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

        @Override
        public boolean setComposingRegion(int start, int end) {
            editor.composingStart = Math.max(0, start);
            editor.composingEnd = Math.min(editor.content.totalLength(), end);
            return true;
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            if (deleteSelection()) return true;

            int beforeLineCount = editor.content.lineCount();

            int cursorFlat = editor.content.flatOffset(editor.cursor);
            int totalLen = editor.content.totalLength();

            if (beforeLength == 1 && afterLength == 0 && (editor.autoCloseBrackets || editor.autoCloseQuotes)) {
                int newCursorFlat = editor.content.flatOffset(editor.cursor);
                if (newCursorFlat > 0 && newCursorFlat < totalLen) {
                    ContentPosition prevPos = editor.content.positionAt(newCursorFlat - 1);
                    if (prevPos.line == editor.cursor.line && editor.cursor.column < editor.content.lineLength(editor.cursor.line)) {
                        char beforeCh = editor.content.charAt(prevPos.line, prevPos.column);
                        char afterCh = editor.content.charAt(editor.cursor.line, editor.cursor.column);
                        if ((beforeCh == '{' && afterCh == '}')
                                || (beforeCh == '[' && afterCh == ']')
                                || (beforeCh == '(' && afterCh == ')')
                                || (beforeCh == '"' && afterCh == '"')
                                || (beforeCh == '\'' && afterCh == '\'')
                                || (beforeCh == '`' && afterCh == '`')) {
                            afterLength = 1;
                        }
                    }
                }
            }

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
            editor.cursorVisible = true;
            editor.scheduleBlink();
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

            // Skip over already closed bracket/quote when typed
            if (text.length() == 1 && (deletedSel == null || deletedSel.isEmpty())) {
                char typed = text.charAt(0);
                boolean isBracket = (typed == ')' || typed == ']' || typed == '}') && editor.autoCloseBrackets;
                boolean isQuote = (typed == '"' || typed == '\'' || typed == '`') && editor.autoCloseQuotes;
                if (isBracket || isQuote) {
                    if (beforeFlat < editor.content.totalLength()
                            && before.column < editor.content.lineLength(before.line)
                            && editor.content.charAt(before.line, before.column) == typed) {
                        editor.cursor = editor.content.positionAt(beforeFlat + 1);
                        editor.selectionAnchor = null;
                        editor.post(editor::ensureCursorVisible);
                        editor.cursorVisible = true;
                        editor.scheduleBlink();
                        editor.invalidate();
                        editor.scheduleHighlight();
                        editor.scheduleAutoComplete();
                        editor.mainHandler.removeCallbacks(editor.bracketMatchRunnable);
                        editor.mainHandler.postDelayed(editor.bracketMatchRunnable, 150);
                        return;
                    }
                }
            }

            // Group the keystroke and its async side effects (auto-close, auto-indent) 
            // into a single undo step. Group is closed via a posted Runnable to ensure 
            // it executes after the async handlers finish.
            boolean mayHaveSideEffects = text.length() == 1 && !editor.isAutoClosing;
            if (mayHaveSideEffects) {
                editor.undoStack.beginAtomicGroup();
            }

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
            if (mayHaveSideEffects) {
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
                // Close atomic group after all async handlers have posted their operations
                editor.mainHandler.post(editor.undoStack::endAtomicGroup);
            }

            editor.post(editor::ensureCursorVisible);
            editor.cursorVisible = true;
            editor.scheduleBlink();
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
            editor.cursorVisible = true;
            editor.scheduleBlink();
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

            boolean deletePair = false;
            if (editor.deleteMatchingPairs
                    && newCursorPos.line == editor.cursor.line
                    && editor.cursor.column < editor.content.lineLength(editor.cursor.line)
                    && (editor.autoCloseBrackets || editor.autoCloseQuotes)) {
                char before = deleted.charAt(0);
                char after = editor.content.charAt(editor.cursor.line, editor.cursor.column);
                if ((before == '{' && after == '}')
                        || (before == '[' && after == ']')
                        || (before == '(' && after == ')')
                        || (before == '"' && after == '"')
                        || (before == '\'' && after == '\'')
                        || (before == '`' && after == '`')) {
                    deletePair = true;
                }
            }

            int beforeLineCount = editor.content.lineCount();
            ContentPosition oldCursor = editor.cursor;
            ContentPosition delEnd = deletePair ? editor.content.positionAt(cursorFlat + 1) : oldCursor;
            String totalDeleted = deletePair ? (deleted + editor.content.charAt(editor.cursor.line, editor.cursor.column)) : deleted;

            editor.content.delete(newCursorPos.line, newCursorPos.column,
                    delEnd.line, delEnd.column);
            int afterLineCount = editor.content.lineCount();
            if (afterLineCount != beforeLineCount) {
                // Tokens now shift implicitly with the lines.
            }
            editor.undoStack.recordDelete(newCursorPos.line, newCursorPos.column,
                    delEnd.line, delEnd.column,
                    totalDeleted, editor.snapshotAt(oldCursor, null), editor.snapshotAt(newCursorPos, null));
            editor.cursor = newCursorPos;
            editor.selectionAnchor = null;
            editor.post(editor::ensureCursorVisible);
            editor.cursorVisible = true;
            editor.scheduleBlink();
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
    
    public Content getContent() {
        return content;
    }

    public UndoStack getUndoStack() {
        return undoStack;
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public int toOffset(com.cocode.vcode.ide.core.lsp.LspPosition pos) {
        if (pos == null) return -1;
        return content.flatOffset(new ContentPosition(pos.line, pos.character));
    }
}

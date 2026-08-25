package com.cocode.vcode.ide.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Composite container linking the editor workspace components.
 * Manages layout, scrolling synchronization, and selection toolbar binding
 * between the line number gutter ({@link LineNumberView}) and the main code view ({@link CodeEditText}).
 */
public class CodeEditorLayout extends LinearLayout {

    private static final long SYNC_DEBOUNCE_MS = 16; // ~1 frame — enough to batch rapid text changes
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LineNumberView lineNumberView;
    private CodeEditText codeEditText;
    private final Runnable syncRunnable = this::syncLineNumberView;
    private SelectionToolbar selectionToolbar;
    private LspNavigationToolbar lspNavigationToolbar;

    public CodeEditorLayout(Context context) {
        super(context);
        init(context);
    }

    public CodeEditorLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CodeEditorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);

        lineNumberView = new LineNumberView(context);
        codeEditText = new CodeEditText(context);

        LayoutParams lineParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        lineNumberView.setLayoutParams(lineParams);

        LayoutParams editorParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        codeEditText.setLayoutParams(editorParams);

        int dp12 = (int) (4 * context.getResources().getDisplayMetrics().density);
        codeEditText.setPadding(dp12, dp12, dp12, 0);

        addView(lineNumberView);
        addView(codeEditText);

        lineNumberView.bindEditor(codeEditText);

        selectionToolbar = new SelectionToolbar(context);
        selectionToolbar.bindEditor(codeEditText);
        selectionToolbar.hide();

        lspNavigationToolbar = new LspNavigationToolbar(context);
        lspNavigationToolbar.bindEditor(codeEditText);
        lspNavigationToolbar.hide();

        codeEditText.setOnCursorIdleListener(offset -> {
            if (lspNavigationToolbar != null) {
                lspNavigationToolbar.onCursorIdle(offset);
            }
        });

        codeEditText.setOnSelectionChangeListener(hasSelection -> {
            if (hasSelection) {
                selectionToolbar.show();
                if (lspNavigationToolbar != null) lspNavigationToolbar.hide();
            } else {
                selectionToolbar.hide();
            }
        });

        // Synchronize scroll offsets between the editor and line number gutter.
        codeEditText.setOnScrollChangeListener((scrollX, scrollY) -> {
            lineNumberView.setScrollY(scrollY);
            if (selectionToolbar.isVisible()) {
                selectionToolbar.show();
            }
            if (lspNavigationToolbar.isVisible()) {
                lspNavigationToolbar.updatePositionIfVisible();
            }
        });

        codeEditText.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                lineNumberView.requestLayout());

        codeEditText.setOnClickListener(v -> syncLineNumberView());

        codeEditText.addContentChangeListener(this::scheduleSyncLineNumberView);
    }

    private void scheduleSyncLineNumberView() {
        handler.removeCallbacks(syncRunnable);
        handler.postDelayed(syncRunnable, SYNC_DEBOUNCE_MS);
    }

    /**
     * Synchronizes cursor position, scroll offset, and line count metrics with the line number gutter.
     */
    private void syncLineNumberView() {
        int lineCount = codeEditText.getLogicalLineCount();

        lineNumberView.setLineCount();
        lineNumberView.setLineHeight();
        lineNumberView.setCursorOffset(codeEditText.getSelectionStart());
        lineNumberView.setScrollY(codeEditText.getScrollY());
        lineNumberView.updateGutterWidth(lineCount);
    }

    /**
     * Toggles the visibility of the line number gutter.
     */
    public void setShowLineNumbers(boolean show) {
        if (lineNumberView != null) {
            lineNumberView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public CodeEditText getCodeEditText() {
        return codeEditText;
    }

    public LspNavigationToolbar getLspNavigationToolbar() {
        return lspNavigationToolbar;
    }

    public LineNumberView getLineNumberView() {
        return lineNumberView;
    }

    /**
     * Returns the {@link SelectionToolbar} bound to this editor layout.
     */
    public SelectionToolbar getSelectionToolbar() {
        return selectionToolbar;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::syncLineNumberView);
    }
}
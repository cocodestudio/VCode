package com.cocode.vcode.ide.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Composite container linking the editor workspace elements.
 * Coordinates sizing constraints and horizontal alignments between the vertical line number gutter
 * and the primary editable source code canvas sheet.
 */
public class CodeEditorLayout extends LinearLayout {

    private static final long SYNC_DEBOUNCE_MS = 16; // ~1 frame — enough to batch rapid text changes
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LineNumberView lineNumberView;
    private CodeEditText codeEditText;
    private final Runnable syncRunnable = this::syncLineNumberView;
    private SelectionToolbar selectionToolbar;

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

        // Grant expanding weight structures across the primary text field sheet component
        LayoutParams editorParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        codeEditText.setLayoutParams(editorParams);

        int dp12 = (int) (4 * context.getResources().getDisplayMetrics().density);
        codeEditText.setPadding(dp12, dp12, dp12, 0);

        addView(lineNumberView);
        addView(codeEditText);

        lineNumberView.bindEditor(codeEditText);

        // Set up SelectionToolbar (Phase 4)
        selectionToolbar = new SelectionToolbar(context);
        selectionToolbar.bindEditor(codeEditText);
        selectionToolbar.hide();

        // Wire selection changes to show/hide the toolbar
        codeEditText.setOnSelectionChangeListener(hasSelection -> {
            if (hasSelection) {
                selectionToolbar.show();
            } else {
                selectionToolbar.hide();
            }
        });

        // Synchronize scroll shifts from the editor to the line numbers gutter.
        // Only update scrollY — NOT cursorOffset — during scroll to avoid O(n) scan mid-fling.
        codeEditText.setOnScrollChangeListener((scrollX, scrollY) -> {
            lineNumberView.setScrollY(scrollY);
            if (selectionToolbar.isVisible()) {
                selectionToolbar.show();
            }
        });

        codeEditText.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                syncLineNumberView());

        codeEditText.setOnClickListener(v -> syncLineNumberView());

        // Update gutter measurements in response to typing additions (debounced)
        codeEditText.addContentChangeListener(this::scheduleSyncLineNumberView);
    }

    private void scheduleSyncLineNumberView() {
        handler.removeCallbacks(syncRunnable);
        handler.postDelayed(syncRunnable, SYNC_DEBOUNCE_MS);
    }

    /**
     * Pumps positioning coordinates and line metrics state values from the editor canvas into the side gutter view.
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
     * Controls the visibility state configuration mapping for the gutter panel view layer.
     */
    public void setShowLineNumbers(boolean show) {
        if (lineNumberView != null) {
            lineNumberView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public CodeEditText getCodeEditText() {
        return codeEditText;
    }

    public LineNumberView getLineNumberView() {
        return lineNumberView;
    }

    /**
     * Returns the {@link SelectionToolbar} bound to this editor layout.
     * Callers may add {@code getSelectionToolbar().getView()} to their own layout
     * (e.g. at the bottom of the activity's container) to display it.
     */
    public SelectionToolbar getSelectionToolbar() {
        return selectionToolbar;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Defer synchronization logic until view hierarchy cycles have resolved calculations fully
        post(this::syncLineNumberView);
    }
}
package com.cocode.vcode.ide.views;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ViewSelectionToolbarBinding;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Floating selection action bar shown whenever the editor has a non-empty selection.
 *
 * <p>Displays as a native-style floating PopupWindow above/below the selection.
 * It does NOT dismiss on outside touch — only when the selection is cleared.
 * It follows the selection as the user scrolls.
 */
public class SelectionToolbar {

    private final ViewSelectionToolbarBinding binding;
    private final Context context;
    private final PopupWindow popupWindow;
    private final ClipboardManager clipboardManager;
    private CodeEditText editor;

    public SelectionToolbar(Context context) {
        this.context = context;
        binding = ViewSelectionToolbarBinding.inflate(LayoutInflater.from(context));

        float density = context.getResources().getDisplayMetrics().density;

        setupTypefaces();
        setupListeners();

        popupWindow = new PopupWindow(
                binding.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false /* not focusable — editor keeps keyboard */);

        // Transparent background required for the card's own shadow to show.
        popupWindow.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

        // Do NOT dismiss on outside touch — scroll is an "outside touch" and we
        // want the toolbar to follow the scroll, not disappear.
        popupWindow.setOutsideTouchable(false);

        // Allow the popup to extend past screen bounds (needed for edge-clamping logic).
        popupWindow.setClippingEnabled(false);

        // Let the PopupWindow itself render the elevation/shadow. This is the only
        // reliable way to avoid shadow clipping inside the window surface.
        // The CardView elevation in the layout must be 0dp to avoid double-shadow.
        popupWindow.setElevation(12 * density);
        popupWindow.setAnimationStyle(R.style.VCodePopupMenuAnimation);

        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /**
     * Binds this toolbar to an editor. Must be called before show().
     */
    public void bindEditor(CodeEditText editor) {
        this.editor = editor;
    }

    /**
     * Shows or repositions the floating toolbar. Safe to call repeatedly.
     */
    public void show() {
        if (editor == null) return;

        boolean hasPasteData = clipboardManager != null && clipboardManager.hasPrimaryClip();
        binding.btnPaste.setVisibility(hasPasteData ? View.VISIBLE : View.GONE);

        if (!popupWindow.isShowing()) {
            // Show off-screen first so the View can measure itself.
            popupWindow.showAtLocation(editor, Gravity.NO_GRAVITY, -10000, -10000);
        }
        updatePosition();
    }

    /**
     * Hides the toolbar.
     */
    public void hide() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    /**
     * Returns true if the toolbar is currently showing.
     */
    public boolean isVisible() {
        return popupWindow.isShowing();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void updatePosition() {
        if (editor == null || !popupWindow.isShowing()) return;

        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        if (selStart == -1 || selEnd == -1) {
            hide();
            return;
        }

        // Measure the popup content (includes the shadow-padding wrapper).
        binding.getRoot().measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupWidth = binding.getRoot().getMeasuredWidth();
        int popupHeight = binding.getRoot().getMeasuredHeight();

        float density = context.getResources().getDisplayMetrics().density;
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        int margin = (int) (8 * density);  // gap between toolbar and selection anchor
        int graceH = (int) (24 * density); // minimum distance from left/right screen edge
        int graceV = (int) (16 * density); // minimum distance from top/bottom screen edge

        int firstOffset = Math.min(selStart, selEnd);
        int[] coords = editor.getCursorScreenCoords(firstOffset);
        int anchorX = coords[0];
        int anchorYTop = coords[1];
        int anchorYBot = coords[2];

        // ── Horizontal: center over anchor, clamp to screen ───────────────────
        int x = anchorX - (popupWidth / 2);
        if (x < graceH) x = graceH;
        if (x + popupWidth > screenW - graceH) x = screenW - popupWidth - graceH;

        // ── Vertical: prefer above anchor; fall back to below ─────────────────
        int yAbove = anchorYTop - popupHeight - margin;
        int yBelow = anchorYBot + margin;

        int y;
        if (yAbove >= graceV) {
            // Enough room above — show there.
            y = yAbove;
        } else if (yBelow + popupHeight <= screenH - graceV) {
            // Not enough room above — show below.
            y = yBelow;
        } else {
            // Neither fits perfectly — prefer above but clamp to grace margin.
            y = yAbove;
        }

        // Hard-clamp: toolbar must ALWAYS stay within screen bounds + grace margin.
        // This ensures it never scrolls off-screen when the user scrolls the editor.
        if (y < graceV) y = graceV;
        if (y + popupHeight > screenH - graceV) y = screenH - popupHeight - graceV;

        popupWindow.update(x, y, popupWidth, popupHeight);
    }

    private void setupTypefaces() {
        FontManager fm = FontManager.getInstance();
        binding.btnCut.setTypeface(fm.getUiMedium(context));
        binding.btnCopy.setTypeface(fm.getUiMedium(context));
        binding.btnPaste.setTypeface(fm.getUiMedium(context));
        binding.btnSelectAll.setTypeface(fm.getUiMedium(context));
    }

    private void setupListeners() {
        binding.btnCut.setOnClickListener(v -> {
            if (editor != null) editor.cutSelection();
        });
        binding.btnCopy.setOnClickListener(v -> {
            if (editor != null) editor.copySelection();
        });
        binding.btnPaste.setOnClickListener(v -> {
            if (editor != null) {
                editor.paste();
                hide();
            }
        });
        binding.btnSelectAll.setOnClickListener(v -> {
            if (editor != null) editor.selectAll();
        });
    }
}

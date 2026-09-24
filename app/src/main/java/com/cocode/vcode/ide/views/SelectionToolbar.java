package com.cocode.vcode.ide.views;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ItemCustomPopupBinding;
import com.cocode.vcode.ide.databinding.LayoutCustomPopupBinding;
import com.cocode.vcode.ide.databinding.ViewSelectionToolbarBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * Floating selection and cursor action bar shown for text selections or on empty-line long-presses.
 *
 * <p>Displays as a native-style floating PopupWindow above or below the selection or cursor.
 * Follows the selection as the user scrolls, dynamically adapts options based on document state,
 * and maintains visibility when the entire document is selected.
 */
public class SelectionToolbar {

    private final ViewSelectionToolbarBinding binding;
    private final Context context;
    private final PopupWindow popupWindow;
    private final ClipboardManager clipboardManager;
    private CodeEditText editor;
    private PopupWindow moreMenuPopup;

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
     * Checks safely whether the system clipboard has non-empty text content to paste.
     */
    private boolean checkHasPasteData() {
        if (clipboardManager == null) return false;
        try {
            if (!clipboardManager.hasPrimaryClip()) return false;
            android.content.ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return false;
            CharSequence text = clip.getItemAt(0).coerceToText(context);
            return text != null && text.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Shows or repositions the floating toolbar. Safe to call repeatedly.
     */
    public void show() {
        if (editor == null) return;

        boolean hasPasteData = checkHasPasteData();
        boolean hasSelection = editor.hasSelection();
        boolean isAllSelected = editor.isAllSelected();
        int docLength = editor.length();

        if (hasSelection) {
            binding.btnCut.setVisibility(View.VISIBLE);
            binding.btnCopy.setVisibility(View.VISIBLE);
            binding.btnPaste.setVisibility(hasPasteData ? View.VISIBLE : View.GONE);
            binding.btnSelectAll.setVisibility(isAllSelected ? View.GONE : View.VISIBLE);
        } else {
            // Cursor-only / empty-line mode
            binding.btnCut.setVisibility(View.GONE);
            binding.btnCopy.setVisibility(View.GONE);
            binding.btnPaste.setVisibility(hasPasteData ? View.VISIBLE : View.GONE);
            binding.btnSelectAll.setVisibility(docLength > 0 ? View.VISIBLE : View.GONE);
        }

        boolean canPerformLineActions = hasSelection || docLength > 0;
        binding.btnMore.setVisibility(canPerformLineActions ? View.VISIBLE : View.GONE);

        boolean anyActionVisible = (binding.btnCut.getVisibility() == View.VISIBLE)
                || (binding.btnCopy.getVisibility() == View.VISIBLE)
                || (binding.btnPaste.getVisibility() == View.VISIBLE)
                || (binding.btnSelectAll.getVisibility() == View.VISIBLE)
                || (binding.btnMore.getVisibility() == View.VISIBLE);

        if (!anyActionVisible) {
            hide();
            return;
        }

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
        dismissMoreMenu();
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public void dismissMoreMenu() {
        if (moreMenuPopup != null) {
            if (moreMenuPopup.isShowing()) {
                moreMenuPopup.dismiss();
            }
            moreMenuPopup = null;
        }
    }

    boolean isMoreMenuShowing() {
        return moreMenuPopup != null && moreMenuPopup.isShowing();
    }

    PopupWindow getMoreMenuPopup() {
        return moreMenuPopup;
    }

    /**
     * Returns true if the toolbar is currently showing.
     */
    public boolean isVisible() {
        return popupWindow.isShowing();
    }

    View findViewById(int id) {
        return binding.getRoot().findViewById(id);
    }

    private void updatePosition() {
        if (editor == null || !popupWindow.isShowing()) return;

        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        if (selStart == -1 || selEnd == -1) {
            hide();
            return;
        }

        dismissMoreMenu();

        // Measure the popup content (includes the shadow-padding wrapper).
        binding.getRoot().measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupWidth = binding.getRoot().getMeasuredWidth();
        int popupHeight = binding.getRoot().getMeasuredHeight();

        float density = context.getResources().getDisplayMetrics().density;
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
        int maxAllowedWidth = Math.min((int) (screenW * 0.92f), maxWidth);
        if (popupWidth > maxAllowedWidth) {
            popupWidth = maxAllowedWidth;
        }
        int margin = (int) (8 * density);  // gap between toolbar and selection anchor
        int graceH = (int) (24 * density); // minimum distance from left/right screen edge
        int graceV = (int) (16 * density); // minimum distance from top/bottom screen edge

        int[] editorLoc = new int[2];
        editor.getLocationInWindow(editorLoc);
        int editorTop = editorLoc[1];
        int editorBottom = editorTop + editor.getHeight();

        int firstOffset = Math.min(selStart, selEnd);
        int[] coords = editor.getCursorScreenCoords(firstOffset);
        int anchorX = coords[0];
        int anchorYTop = coords[1];
        int anchorYBot = coords[2];

        // If the selection start is scrolled off-screen above (e.g. after selectAll()),
        // anchor within the visible editor viewport so the toolbar does not leap off-screen.
        if (editor.isAllSelected() || anchorYTop < editorTop) {
            anchorX = screenW / 2;
            anchorYTop = Math.max(editorTop, graceV) + (int) (12 * density);
            anchorYBot = anchorYTop + editor.getEditorLineHeight();
        }

        // Horizontal positioning: center over anchor and clamp within screen margins
        int x = anchorX - (popupWidth / 2);
        if (x < graceH) x = graceH;
        if (x + popupWidth > screenW - graceH) x = screenW - popupWidth - graceH;

        // Vertical positioning: prefer above the anchor (if space within editor), fall back to below
        int yAbove = anchorYTop - popupHeight - margin;
        int yBelow = anchorYBot + margin;

        int y;
        if (yAbove >= Math.max(editorTop, graceV)) {
            // Enough room above editor boundary — show there.
            y = yAbove;
        } else if (yBelow + popupHeight <= Math.min(editorBottom, screenH - graceV)) {
            // Not enough room above — show below anchor if fits inside editor.
            y = yBelow;
        } else {
            // Neither fits perfectly inside editor — place below or above clamped to grace margin.
            y = Math.max(yAbove, Math.max(editorTop, graceV));
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
            if (editor != null) {
                editor.cutSelection();
                hide();
            }
        });
        binding.btnCopy.setOnClickListener(v -> {
            if (editor != null) {
                editor.copySelection();
                hide();
            }
        });
        binding.btnPaste.setOnClickListener(v -> {
            if (editor != null) {
                editor.paste();
                hide();
            }
        });
        binding.btnSelectAll.setOnClickListener(v -> {
            if (editor != null) {
                editor.selectAll();
                show();
            }
        });
        binding.btnMore.setOnClickListener(this::showMoreMenu);
    }

    private void showMoreMenu(View anchor) {
        if (moreMenuPopup != null && moreMenuPopup.isShowing()) {
            dismissMoreMenu();
            return;
        }

        LayoutCustomPopupBinding popupBinding = LayoutCustomPopupBinding.inflate(LayoutInflater.from(context));
        android.content.res.ColorStateList toolbarBg = binding.cardToolbar.getCardBackgroundColor();
        if (toolbarBg != null) {
            popupBinding.getRoot().setCardBackgroundColor(toolbarBg);
        } else {
            popupBinding.getRoot().setCardBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(context, R.color.vcode_bg_surface));
        }
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
        int preferredWidth = UiUtils.dpToPx(context, 200);
        int width = Math.min(preferredWidth, Math.min((int) (screenWidth * 0.92f), maxWidth));

        moreMenuPopup = new PopupWindow(
                popupBinding.getRoot(),
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true /* focusable so outside touch dismisses */
        );
        moreMenuPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        moreMenuPopup.setElevation(8f);
        moreMenuPopup.setAnimationStyle(R.style.VCodePopupMenuAnimation);
        moreMenuPopup.setOnDismissListener(() -> moreMenuPopup = null);

        // Group 1: Copy Line Up / Down
        addPopupItem(popupBinding.popupContainer, moreMenuPopup, R.drawable.ic_copy,
                context.getString(R.string.vcode_cmd_copy_line_up), () -> {
            if (editor != null) {
                editor.copyLine(true);
                hide();
            }
        });
        addPopupItem(popupBinding.popupContainer, moreMenuPopup, R.drawable.ic_copy,
                context.getString(R.string.vcode_cmd_copy_line_down), () -> {
            if (editor != null) {
                editor.copyLine(false);
                hide();
            }
        });

        addDivider(popupBinding.popupContainer);

        // Group 2: Move Line Up / Down
        addPopupItem(popupBinding.popupContainer, moreMenuPopup, R.drawable.ic_chevron_down, 180f,
                context.getString(R.string.vcode_cmd_move_line_up), () -> {
            if (editor != null) {
                editor.moveLine(true);
                hide();
            }
        });
        addPopupItem(popupBinding.popupContainer, moreMenuPopup, R.drawable.ic_chevron_down,
                context.getString(R.string.vcode_cmd_move_line_down), () -> {
            if (editor != null) {
                editor.moveLine(false);
                hide();
            }
        });

        addDivider(popupBinding.popupContainer);

        // Group 3: Comment / Uncomment
        addPopupItem(popupBinding.popupContainer, moreMenuPopup, R.drawable.ic_code,
                context.getString(R.string.vcode_action_comment_uncomment), () -> {
            if (editor != null) {
                editor.toggleComment();
                hide();
            }
        });

        popupBinding.getRoot().measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = popupBinding.getRoot().getMeasuredHeight();

        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int anchorX = anchorLocation[0];
        int anchorY = anchorLocation[1];

        int xOffset = anchor.getWidth() - width;
        int screenEdgeMargin = UiUtils.dpToPx(context, 8);
        if (anchorX + xOffset < screenEdgeMargin) {
            xOffset = screenEdgeMargin - anchorX;
        } else if (anchorX + xOffset + width > screenWidth - screenEdgeMargin) {
            xOffset = screenWidth - screenEdgeMargin - anchorX - width;
        }

        int spaceBelow = screenHeight - (anchorY + anchor.getHeight());
        int spaceAbove = anchorY;
        int margin = UiUtils.dpToPx(context, 4);

        int yOffset;
        if (spaceBelow >= popupHeight || spaceBelow >= spaceAbove) {
            yOffset = margin;
        } else {
            yOffset = -anchor.getHeight() - popupHeight - margin;
        }

        moreMenuPopup.showAsDropDown(anchor, xOffset, yOffset);
    }

    private View addPopupItem(ViewGroup container, PopupWindow popup, int iconRes, String title, Runnable action) {
        return addPopupItem(container, popup, iconRes, 0f, title, action);
    }

    private View addPopupItem(ViewGroup container, PopupWindow popup, int iconRes, float rotationDegrees, String title, Runnable action) {
        ItemCustomPopupBinding itemBinding = ItemCustomPopupBinding.inflate(LayoutInflater.from(context), container, false);
        itemBinding.ivIcon.setImageResource(iconRes);
        if (rotationDegrees != 0f) {
            itemBinding.ivIcon.setRotation(rotationDegrees);
        }
        itemBinding.tvTitle.setText(title);
        itemBinding.tvTitle.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
        itemBinding.tvTitle.setTypeface(FontManager.getInstance().getUiMedium(context));
        itemBinding.getRoot().setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        container.addView(itemBinding.getRoot());
        return itemBinding.getRoot();
    }

    private void addDivider(ViewGroup container) {
        View divider = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiUtils.dpToPx(context, 1)
        );
        params.setMargins(0, UiUtils.dpToPx(context, 4), 0, UiUtils.dpToPx(context, 4));
        divider.setLayoutParams(params);
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)) {
            divider.setBackgroundColor(typedValue.data);
        } else {
            divider.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.vcode_divider));
        }
        container.addView(divider);
    }
}

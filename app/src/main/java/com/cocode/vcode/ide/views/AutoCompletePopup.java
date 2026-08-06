package com.cocode.vcode.ide.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.databinding.ItemAutocompleteSuggestionBinding;
import com.cocode.vcode.ide.utils.FileIconHelper;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom completion suggestion overlay for the editor view.
 * Uses a lightweight, non-focusable PopupWindow wrapping a RecyclerView to display
 * context-aware code completions without stealing key events from the soft keyboard.
 *
 * <p>Supports keyboard navigation via moveSelection() and getSelectedItem().
 */
public class AutoCompletePopup {

    private static final int WIDTH_DP = 280;
    private static final int MAX_VISIBLE_ITEMS = 4;
    private static final int ITEM_HEIGHT_DP = 38;

    private final Context context;
    private final PopupWindow popupWindow;
    private final AutoCompleteAdapter adapter;
    private final RecyclerView recyclerView;
    private int selectedIndex = 0;

    public AutoCompletePopup(Context context) {
        this.context = context;
        this.adapter = new AutoCompleteAdapter();

        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        recyclerView.setBackground(ContextCompat.getDrawable(context, R.drawable.vcode_bg_autocomplete_popup));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        popupWindow = new PopupWindow(recyclerView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setElevation(8f);
        popupWindow.setAnimationStyle(R.style.VCodePopupMenuAnimation);
    }

    /**
     * Shows or updates the popup adjacent to the cursor. If already showing, smoothly
     * updates position and content without re-creating the window (prevents flicker).
     */
    public void show(List<CompletionItem> items, View editorView, int cursorOffset) {
        if (items == null || items.isEmpty()) {
            dismiss();
            return;
        }

        // Reset selection to top when new items arrive
        selectedIndex = 0;
        adapter.setItems(items);
        adapter.setSelectedIndex(selectedIndex);

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int popupWidth = Math.min(UiUtils.dpToPx(context, WIDTH_DP), screenWidth - UiUtils.dpToPx(context, 32));
        popupWindow.setWidth(popupWidth);

        int itemCount = Math.min(items.size(), MAX_VISIBLE_ITEMS);
        int estimatedHeight = itemCount * UiUtils.dpToPx(context, ITEM_HEIGHT_DP);

        popupWindow.setHeight(estimatedHeight);

        // AD-2: getCursorScreenCoords() already returns window-absolute pixel coordinates
        // (it calls getLocationInWindow() internally and factors in scroll + padding).
        // We must NOT convert back to view-local then re-add editorLocation — that would
        // double-subtract getScrollX/Y which are already baked in.
        int windowX = 0;
        int windowYTop = 0;
        int windowYBottom = 0;

        if (editorView instanceof CodeEditText) {
            CodeEditText codeEditor = (CodeEditText) editorView;
            int[] coords = codeEditor.getCursorScreenCoords(cursorOffset);
            // coords[0] = window-absolute X, coords[1] = window-absolute top Y, coords[2] = bottom Y
            windowX = coords[0];
            windowYTop = coords[1];
            windowYBottom = coords[2];
        }

        int x = windowX;

        android.graphics.Rect visibleFrame = new android.graphics.Rect();
        editorView.getWindowVisibleDisplayFrame(visibleFrame);

        int yBelow = windowYBottom + UiUtils.dpToPx(context, 4);
        int yAbove = windowYTop - estimatedHeight - UiUtils.dpToPx(context, 4);

        int y;
        if (yBelow + estimatedHeight > visibleFrame.bottom) {
            y = Math.max(visibleFrame.top, yAbove);
        } else {
            y = yBelow;
        }

        if (x + popupWidth > screenWidth) {
            x = screenWidth - popupWidth - UiUtils.dpToPx(context, 8);
        }
        x = Math.max(0, x);

        if (popupWindow.isShowing()) {
            popupWindow.update(x, y, popupWidth, estimatedHeight);
        } else {
            popupWindow.showAtLocation(editorView, Gravity.NO_GRAVITY, x, y);
        }
    }

    public void dismiss() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        selectedIndex = 0;
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    /**
     * Moves the keyboard selection highlight up or down.
     *
     * @param direction +1 for down, -1 for up
     */
    public void moveSelection(int direction) {
        int count = adapter.getItemCount();
        if (count == 0) return;
        int newIndex = selectedIndex + direction;
        if (newIndex < 0) newIndex = 0;
        if (newIndex >= count) newIndex = count - 1;
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex;
            adapter.setSelectedIndex(selectedIndex);
            recyclerView.scrollToPosition(selectedIndex);
        }
    }

    /**
     * Returns the currently keyboard-selected item, or the first item if none selected.
     */
    public CompletionItem getSelectedItem() {
        return adapter.getItemAt(selectedIndex);
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        adapter.setListener(listener);
    }

    public interface OnItemSelectedListener {
        void onItemSelected(CompletionItem item);
    }

    /**
     * Internal adapter linking completion items to RecyclerView rows.
     */
    private class AutoCompleteAdapter extends RecyclerView.Adapter<AutoCompleteAdapter.ViewHolder> {

        private final int colorPrimary;
        private final int colorSecondary;
        private final int colorSuccess;
        private final int colorWarning;
        private final int colorJson;
        private final int colorTextSecondary;
        private final Typeface uiFont, uiFontBold, codeFont;
        private List<CompletionItem> items = new ArrayList<>();
        private OnItemSelectedListener listener;
        private int highlightedIndex = 0;

        AutoCompleteAdapter() {
            colorPrimary = ContextCompat.getColor(context, R.color.vcode_accent_primary);
            colorSecondary = ContextCompat.getColor(context, R.color.vcode_accent_secondary);
            colorSuccess = ContextCompat.getColor(context, R.color.vcode_accent_success);
            colorWarning = ContextCompat.getColor(context, R.color.vcode_accent_warning);
            colorJson = ContextCompat.getColor(context, R.color.vcode_accent_json);
            colorTextSecondary = ContextCompat.getColor(context, R.color.vcode_text_secondary);
            int colorTextPrimary = ContextCompat.getColor(context, R.color.vcode_text_primary);
            uiFont = FontManager.getInstance().getUiFont(context);
            uiFontBold = Typeface.create(uiFont, Typeface.BOLD);
            codeFont = FontManager.getInstance().getCodeFont(context);
        }

        @SuppressLint("NotifyDataSetChanged")
        void setItems(List<CompletionItem> items) {
            this.items = items != null ? items : new ArrayList<>();
            notifyDataSetChanged();
        }

        void setListener(OnItemSelectedListener l) {
            this.listener = l;
        }

        @SuppressLint("NotifyDataSetChanged")
        void setSelectedIndex(int index) {
            this.highlightedIndex = index;
            notifyDataSetChanged();
        }

        CompletionItem getItemAt(int index) {
            if (index >= 0 && index < items.size()) return items.get(index);
            return items.isEmpty() ? null : items.get(0);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemAutocompleteSuggestionBinding binding = ItemAutocompleteSuggestionBinding.inflate(
                    android.view.LayoutInflater.from(context), parent, false);
            return new ViewHolder(binding);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CompletionItem item = items.get(position);

            // ── Type badge / icon ────────────────────────────────────────────
            if (item.getType() == CompletionItem.Type.FILE || item.getType() == CompletionItem.Type.FOLDER) {
                holder.binding.tvTypeBadge.setVisibility(View.GONE);
                holder.binding.ivTypeIcon.setVisibility(View.VISIBLE);

                if (item.getType() == CompletionItem.Type.FOLDER) {
                    holder.binding.ivTypeIcon.setImageResource(R.drawable.ic_folder);
                    holder.binding.ivTypeIcon.setColorFilter(colorPrimary, PorterDuff.Mode.SRC_IN);
                } else {
                    FileIconHelper.setFileIconAndColor(holder.binding.ivTypeIcon, item.getLabel());
                }
            } else {
                holder.binding.ivTypeIcon.setVisibility(View.GONE);
                holder.binding.tvTypeBadge.setVisibility(View.VISIBLE);
                GradientDrawable badgeBg = (GradientDrawable) holder.binding.tvTypeBadge.getBackground();
                if (badgeBg != null) {
                    badgeBg.setColor(getBadgeColor(item.getType()));
                }
                holder.binding.tvTypeBadge.setText(getBadgeLetter(item.getType()));
                holder.binding.tvTypeBadge.setTypeface(uiFontBold);
            }

            // ── Label ────────────────────────────────────────────────────────
            String labelText = item.getLabel();
            if (labelText != null && labelText.length() > 24) {
                holder.binding.tvLabel.setText(labelText.substring(0, 24) + "\u2026");
            } else {
                holder.binding.tvLabel.setText(labelText != null ? labelText : "");
            }
            holder.binding.tvLabel.setTypeface(codeFont, position == highlightedIndex ? Typeface.BOLD : Typeface.NORMAL);

            // ── Detail ───────────────────────────────────────────────────────
            holder.binding.tvDetail.setText(item.getDetail() != null ? item.getDetail() : "");
            holder.binding.tvDetail.setTypeface(uiFont);

            // ── Click handler ────────────────────────────────────────────────
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemSelected(item);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private int getBadgeColor(CompletionItem.Type type) {
            if (type == null) return colorTextSecondary;
            switch (type) {
                case TAG:
                case BUILTIN:
                case FOLDER:
                    return colorPrimary;
                case ATTRIBUTE:
                case CSS_PROPERTY:
                    return colorSecondary;
                case VALUE:
                case CSS_VALUE:
                case FUNCTION:
                    return colorSuccess;
                case KEYWORD:
                    return colorWarning;
                case SNIPPET:
                case JSON_KEY:
                    return colorJson;
                default:
                    return colorTextSecondary;
            }
        }

        private String getBadgeLetter(CompletionItem.Type type) {
            if (type == null) return "?";
            switch (type) {
                case TAG:
                    return "T";
                case ATTRIBUTE:
                    return "A";
                case VALUE:
                case CSS_VALUE:
                    return "V";
                case CSS_PROPERTY:
                    return "P";
                case KEYWORD:
                    return "K";
                case FUNCTION:
                    return "F";
                case BUILTIN:
                    return "B";
                case SNIPPET:
                    return "S";
                case JSON_KEY:
                    return "J";
                default:
                    return "?";
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ItemAutocompleteSuggestionBinding binding;

            ViewHolder(ItemAutocompleteSuggestionBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
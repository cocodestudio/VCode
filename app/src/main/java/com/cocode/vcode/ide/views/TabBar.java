package com.cocode.vcode.ide.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.databinding.ItemEditorTabBinding;
import com.cocode.vcode.ide.utils.FontManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal tab container view bar tracking active files sessions.
 * Manages item layout additions, unsaved modification tracking nodes,
 * extension language asset badge colorizations, and middle path truncation logic.
 */
public class TabBar extends HorizontalScrollView {

    private LinearLayout tabContainer;
    private List<EditorFile> tabs = new ArrayList<>();
    private int activeIndex = -1;
    private OnTabClickListener tabClickListener;
    private OnTabCloseListener tabCloseListener;
    private boolean isAutoSaveOn = false;

    public TabBar(Context context) {
        super(context);
        init();
    }

    public TabBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setBackground(ContextCompat.getDrawable(getContext(), R.drawable.vcode_bg_toolbar));

        tabContainer = new LinearLayout(getContext());
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setLayoutParams(new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(tabContainer);
    }

    /**
     * populates full selection tabs arrays profiles, focusing viewport metrics on current selections.
     */
    public void setTabs(List<EditorFile> files, int activeIdx) {
        this.tabs = files != null ? new ArrayList<>(files) : new ArrayList<>();
        this.activeIndex = activeIdx;

        rebuildTabs();
        scrollToActiveTab();
    }

    /**
     * Shifts active styling configurations indices across tabs without forcing complete array reconstructions.
     */
    public void setActiveTab(int index) {
        if (index == activeIndex) return;
        activeIndex = index;

        for (int i = 0; i < tabContainer.getChildCount(); i++) {
            View child = tabContainer.getChildAt(i);
            ItemEditorTabBinding binding = (ItemEditorTabBinding) child.getTag();
            if (binding != null) {
                updateTabActiveState(binding, i == activeIndex);
            }
        }
        scrollToActiveTab();
    }

    public void setAutoSaveOn(boolean autoSaveOn) {
        this.isAutoSaveOn = autoSaveOn;
        for (int i = 0; i < tabs.size(); i++) {
            updateTabDirtyState(i, tabs.get(i).isDirty());
        }
    }

    /**
     * Alternates visibility parameters on circular unsaved changes flags labels indicator rings.
     */
    public void updateTabDirtyState(int index, boolean dirty) {
        View tabView = tabContainer.getChildAt(index);
        if (tabView == null) return;

        ItemEditorTabBinding binding = (ItemEditorTabBinding) tabView.getTag();
        if (binding != null) {
            boolean showDot = dirty;
            if (tabs.get(index).getFileType() == FileType.API_TESTER) showDot = false;
            else if (isAutoSaveOn) showDot = false;

            binding.dotDirty.setVisibility(showDot ? VISIBLE : GONE);
        }
    }

    public void addTab(EditorFile file) {
        tabs.add(file);
        View tabView = inflateTabView(tabs.size() - 1, file);
        tabContainer.addView(tabView);
    }

    public void removeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        tabs.remove(index);
        tabContainer.removeViewAt(index);

        rebuildTabs();
    }

    public void setOnTabClickListener(OnTabClickListener l) {
        this.tabClickListener = l;
    }

    public void setOnTabCloseListener(OnTabCloseListener l) {
        this.tabCloseListener = l;
    }

    private void rebuildTabs() {
        tabContainer.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            tabContainer.addView(inflateTabView(i, tabs.get(i)));
        }
    }

    /**
     * Translates asset types and code language mappings into visual resource design properties tokens.
     */
    private View inflateTabView(int index, EditorFile file) {
        ItemEditorTabBinding binding = ItemEditorTabBinding.inflate(
                LayoutInflater.from(getContext()), tabContainer, false);

        FileType fileType = file.getFileType();
        if (fileType == null) fileType = FileType.TEXT;

        binding.ivFileIcon.setImageResource(fileType.getIconResId());
        binding.ivFileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(getContext(), fileType.getColorResId())));

        // Apply string truncations to protect tab layout row size constraints
        String displayFileName = getFileName(file);

        binding.tvFileName.setText(displayFileName);
        binding.tvFileName.setTypeface(FontManager.getInstance().getUiMedium(getContext()));

        boolean showDot = file.isDirty();
        if (file.getFileType() == FileType.API_TESTER) showDot = false;
        else if (isAutoSaveOn) showDot = false;

        binding.dotDirty.setVisibility(showDot ? VISIBLE : GONE);

        updateTabActiveState(binding, index == activeIndex);

        binding.getRoot().setOnClickListener(v -> {
            if (tabClickListener != null) {
                int currentIndex = tabContainer.indexOfChild(binding.getRoot());
                if (currentIndex >= 0) tabClickListener.onTabClick(currentIndex);
            }
        });

        binding.btnClose.setOnClickListener(v -> {
            if (tabCloseListener != null) {
                int currentIndex = tabContainer.indexOfChild(binding.getRoot());
                if (currentIndex >= 0) tabCloseListener.onTabClose(currentIndex);
            }
        });

        // Cache view binding objects inside layout tags to expedite simple runtime parameter changes later
        binding.getRoot().setTag(binding);

        return binding.getRoot();
    }

    /**
     * Performs middle truncation formatting scripts across filename loops strings to optimize screen layout space.
     * Preserves extension codes view readability while shrinking oversized base strings structures.
     */
    @Nullable
    private String getFileName(EditorFile file) {
        if (file.getFileType() == FileType.API_TESTER) {
            return "API Tester";
        }
        String displayFileName = file.getFileName();
        if (displayFileName != null && displayFileName.length() > 12) {
            int dotIndex = displayFileName.lastIndexOf('.');
            if (dotIndex > 0) {
                String extension = displayFileName.substring(dotIndex); // Extracts extension segment inclusive of dot marker
                int prefixLength = 12 - 3 - extension.length();

                if (prefixLength > 0) {
                    displayFileName = displayFileName.substring(0, prefixLength) + "..." + extension;
                } else {
                    displayFileName = displayFileName.substring(0, 3) + "..." + extension;
                }
            } else {
                displayFileName = displayFileName.substring(0, 9) + "...";
            }
        }
        return displayFileName;
    }

    private void updateTabActiveState(ItemEditorTabBinding binding, boolean active) {
        binding.getRoot().setSelected(active);

        int bgDrawable = active
                ? R.drawable.vcode_bg_editor_tab_active
                : R.drawable.vcode_bg_editor_tab_inactive;
        binding.getRoot().setBackground(ContextCompat.getDrawable(getContext(), bgDrawable));

        int textColor = active
                ? ContextCompat.getColor(getContext(), R.color.vcode_text_primary)
                : ContextCompat.getColor(getContext(), R.color.vcode_text_secondary);
        binding.tvFileName.setTextColor(textColor);
    }

    /**
     * Performs smooth horizontal scroll calculations to transition target chosen cards precisely centered on viewport.
     */
    private void scrollToActiveTab() {
        if (activeIndex < 0 || activeIndex >= tabContainer.getChildCount()) return;

        View tabView = tabContainer.getChildAt(activeIndex);
        if (tabView != null) {
            post(() -> {
                int scrollX = tabView.getLeft() - (getWidth() / 2) + (tabView.getWidth() / 2);
                smoothScrollTo(Math.max(0, scrollX), 0);
            });
        }
    }

    public interface OnTabClickListener {
        void onTabClick(int index);
    }

    public interface OnTabCloseListener {
        void onTabClose(int index);
    }
}
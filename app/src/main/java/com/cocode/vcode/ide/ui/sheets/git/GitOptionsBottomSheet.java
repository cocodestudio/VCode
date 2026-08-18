package com.cocode.vcode.ide.ui.sheets.git;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * GitOptionsBottomSheet provides a dynamic, icon-based menu for context-specific actions.
 * It is extensively used across Git tabs to provide options for commits, branches,
 * and remotes in a clean, Material Design bottom sheet.
 */
public class GitOptionsBottomSheet extends BaseBottomSheetDialogFragment {

    private final List<MenuOption> optionsList = new ArrayList<>();
    private String headerTitle = null;

    /**
     * Creates a new instance of the options sheet with an optional header.
     *
     * @param headerTitle The title to display at the top of the menu.
     */
    public static GitOptionsBottomSheet newInstance(@Nullable String headerTitle) {
        GitOptionsBottomSheet sheet = new GitOptionsBottomSheet();
        sheet.headerTitle = headerTitle;
        return sheet;
    }

    /**
     * Adds a menu option to the sheet.
     *
     * @param title     The display text for the option.
     * @param iconResId The drawable resource for the option icon.
     * @param listener  The callback to execute when the option is selected.
     * @return The current instance for method chaining.
     */
    public GitOptionsBottomSheet addOption(String title, @DrawableRes int iconResId, OptionClickListener listener) {
        optionsList.add(new MenuOption(title, iconResId, listener));
        return this;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_dialog_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();

        TextView tvHeader = view.findViewById(R.id.tv_sheet_header_title);
        LinearLayout optionsContainer = view.findViewById(R.id.layout_options_container);

        // Configure the optional header
        if (headerTitle != null && !headerTitle.trim().isEmpty()) {
            tvHeader.setText(headerTitle.toUpperCase().trim());
            tvHeader.setTypeface(FontManager.getInstance().getUiSemiBold(context));
            tvHeader.setVisibility(View.VISIBLE);
        } else {
            tvHeader.setVisibility(View.GONE);
        }

        // Dynamically inflate and add each menu option to the container
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        for (MenuOption option : optionsList) {
            View itemView = layoutInflater.inflate(R.layout.layout_bottom_sheet_menu_item, optionsContainer, false);

            ImageView ivIcon = itemView.findViewById(R.id.iv_option_icon);
            TextView tvTitle = itemView.findViewById(R.id.tv_option_title);

            ivIcon.setImageResource(option.iconResId);
            ivIcon.setColorFilter(context.getResources().getColor(R.color.vcode_text_primary, null));
            tvTitle.setText(option.title);
            tvTitle.setTypeface(FontManager.getInstance().getUiMedium(context));

            itemView.setOnClickListener(v -> {
                dismiss(); // Auto-dismiss before executing the action
                if (option.clickListener != null) {
                    option.clickListener.onOptionClick();
                }
            });

            optionsContainer.addView(itemView);
        }
    }

    /**
     * Interface for handling option selection events.
     */
    public interface OptionClickListener {
        void onOptionClick();
    }

    /**
     * Model class representing a single menu entry.
     */
    public static class MenuOption {
        private final String title;
        private final int iconResId;
        private final OptionClickListener clickListener;

        public MenuOption(String title, @DrawableRes int iconResId, OptionClickListener clickListener) {
            this.title = title;
            this.iconResId = iconResId;
            this.clickListener = clickListener;
        }
    }
}
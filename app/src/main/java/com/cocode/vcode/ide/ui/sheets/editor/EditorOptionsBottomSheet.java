package com.cocode.vcode.ide.ui.sheets.editor;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetEditorOptionsBinding;
import com.cocode.vcode.ide.databinding.ItemEditorSheetOptionBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet dialog for toggling editor settings and display options.
 */
public class EditorOptionsBottomSheet extends BaseBottomSheetDialogFragment {

    private BottomSheetEditorOptionsBinding binding;
    private List<Option> options = new ArrayList<>();

    public void setOptions(List<Option> options) {
        this.options = options;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetEditorOptionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        populateOptions();
    }

    private void populateOptions() {
        binding.gridOptions.removeAllViews();
        FontManager fm = FontManager.getInstance();

        int margin = UiUtils.dpToPx(requireContext(), 8);

        for (Option option : options) {
            ItemEditorSheetOptionBinding itemBinding = ItemEditorSheetOptionBinding.inflate(getLayoutInflater(), binding.gridOptions, false);

            itemBinding.tvOptionName.setText(option.title);
            itemBinding.tvOptionName.setTypeface(fm.getUiMedium(requireContext()));

            if (option.isReadOnlyToggle) {
                if (option.isReadOnlyActive) {
                    itemBinding.ivOptionIcon.setImageResource(R.drawable.ic_lock_open);
                    itemBinding.cvOptionCard.setStrokeWidth(UiUtils.dpToPx(requireContext(), 1));

                    // Get colorOnSurfaceVariant from theme
                    TypedValue typedValue = new TypedValue();
                    requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true);
                    itemBinding.cvOptionCard.setStrokeColor(typedValue.data);
                } else {
                    itemBinding.ivOptionIcon.setImageResource(R.drawable.ic_lock);
                    itemBinding.cvOptionCard.setStrokeWidth(0);
                }
            } else {
                itemBinding.ivOptionIcon.setImageResource(option.iconRes);
            }

            itemBinding.cvOptionCard.setOnClickListener(v -> {
                if (option.listener != null) {
                    option.listener.onClick();
                }
                dismiss();
            });

            // Set LayoutParams for GridLayout
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(margin, margin, margin, margin);
            itemBinding.getRoot().setLayoutParams(params);

            binding.gridOptions.addView(itemBinding.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public interface OptionClickListener {
        void onClick();
    }

    public static class Option {
        public int iconRes;
        public String title;
        public boolean isReadOnlyToggle;
        public boolean isReadOnlyActive;
        public OptionClickListener listener;

        public Option(int iconRes, String title, OptionClickListener listener) {
            this.iconRes = iconRes;
            this.title = title;
            this.listener = listener;
        }

        public Option(int iconRes, String title, boolean isReadOnlyToggle, boolean isReadOnlyActive, OptionClickListener listener) {
            this.iconRes = iconRes;
            this.title = title;
            this.isReadOnlyToggle = isReadOnlyToggle;
            this.isReadOnlyActive = isReadOnlyActive;
            this.listener = listener;
        }
    }
}

package com.cocode.vcode.ide.ui.sheets.settings;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.databinding.BottomSheetCustomizeQuickSymbolsBinding;
import com.cocode.vcode.ide.databinding.ItemQuickSymbolChipActiveBinding;
import com.cocode.vcode.ide.databinding.ItemQuickSymbolChipSuggestedBinding;
import com.cocode.vcode.ide.ui.settings.SettingsViewModel;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bottom sheet for configuring the symbols displayed in {@link com.cocode.vcode.ide.views.QuickSymbolBar}.
 */
public class CustomizeQuickSymbolsBottomSheet extends BaseBottomSheetDialogFragment {

    private static final List<String> ALL_SUGGESTIONS = Arrays.asList(
            "+", "-", "*", "&", "|", "$", "?", "%", "#", "@", "_", "~", "^", "\\", "`", ".", "===", "!==", "&&", "||", "??"
    );

    private BottomSheetCustomizeQuickSymbolsBinding binding;
    private SettingsViewModel viewModel;
    private final List<String> activeSymbols = new ArrayList<>();
    private OnSymbolsConfiguredListener listener;

    public interface OnSymbolsConfiguredListener {
        void onSymbolsConfigured(List<String> symbols);
    }

    public static CustomizeQuickSymbolsBottomSheet newInstance() {
        return new CustomizeQuickSymbolsBottomSheet();
    }

    public void setListener(OnSymbolsConfiguredListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCustomizeQuickSymbolsBinding.inflate(inflater, container, false);
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SettingsViewModel.class);

        setupInitialData();
        setupDesign();
        setupListeners();
        refreshUi();
    }

    private void setupInitialData() {
        activeSymbols.clear();
        if (viewModel != null && viewModel.getSettingsLiveData().getValue() != null) {
            activeSymbols.addAll(viewModel.getSettingsLiveData().getValue().getQuickSymbolsList());
        } else {
            activeSymbols.addAll(AppSettings.DEFAULT_QUICK_SYMBOLS);
        }
    }

    private void setupDesign() {
        Context context = requireContext();
        FontManager fm = FontManager.getInstance();

        binding.tvSheetTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvSheetSubtitle.setTypeface(fm.getUiFont(context));
        binding.btnResetDefaults.setTypeface(fm.getUiMedium(context));
        binding.tvPreviewTitle.setTypeface(fm.getUiMedium(context));
        binding.tvActiveSymbolsTitle.setTypeface(fm.getUiMedium(context));
        binding.tvAddSymbolTitle.setTypeface(fm.getUiMedium(context));
        binding.tvSuggestionsTitle.setTypeface(fm.getUiMedium(context));
        binding.etCustomSymbol.setTypeface(fm.getCodeFont(context));
        binding.btnAddSymbol.setTypeface(fm.getUiMedium(context));
        binding.btnSaveSymbols.setTypeface(fm.getUiSemiBold(context));

        UiUtils.setViewRounded(binding.etCustomSymbol, UiUtils.dpToPx(context, 10), ContextCompat.getColor(context, R.color.vcode_bg_elevated));
        UiUtils.applyAccentToEditText(binding.etCustomSymbol);
    }

    private void setupListeners() {
        binding.btnResetDefaults.setOnClickListener(v -> {
            activeSymbols.clear();
            activeSymbols.addAll(AppSettings.DEFAULT_QUICK_SYMBOLS);
            refreshUi();
        });

        binding.btnAddSymbol.setOnClickListener(v -> handleAddSymbol());

        binding.etCustomSymbol.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleAddSymbol();
                return true;
            }
            return false;
        });

        binding.btnSaveSymbols.setOnClickListener(v -> {
            if (viewModel != null) {
                viewModel.updateQuickSymbols(activeSymbols);
            }
            if (listener != null) {
                listener.onSymbolsConfigured(activeSymbols);
            }
            dismiss();
        });
    }

    private void handleAddSymbol() {
        String sym = binding.etCustomSymbol.getText() != null
                ? binding.etCustomSymbol.getText().toString().trim()
                : "";

        if (sym.isEmpty()) {
            binding.etCustomSymbol.setError(getString(R.string.vcode_customize_symbols_empty_error));
            binding.etCustomSymbol.requestFocus();
            return;
        }

        if (sym.length() > 12) {
            binding.etCustomSymbol.setError(getString(R.string.vcode_customize_symbols_max_length_error));
            binding.etCustomSymbol.requestFocus();
            return;
        }

        if (activeSymbols.contains(sym)) {
            binding.etCustomSymbol.setError(getString(R.string.vcode_customize_symbols_exists_error));
            binding.etCustomSymbol.requestFocus();
            return;
        }

        binding.etCustomSymbol.setError(null);
        binding.etCustomSymbol.setText("");
        activeSymbols.add(sym);
        refreshUi();
    }

    private void refreshUi() {
        if (!isAdded() || getContext() == null) return;
        Context context = requireContext();
        FontManager fm = FontManager.getInstance();

        // 1. Update header active count
        binding.tvActiveSymbolsTitle.setText(getString(R.string.vcode_customize_symbols_active_title, activeSymbols.size()));

        // 2. Update Live Preview
        updateLivePreview(context, fm);

        // 3. Update Active Chips
        updateActiveChips(context, fm);

        // 4. Update Suggested Chips
        updateSuggestedChips(context, fm);
    }

    private void updateLivePreview(Context context, FontManager fm) {
        binding.layoutPreviewKeys.removeAllViews();
        int heightPx = UiUtils.dpToPx(context, 28);
        int marginPx = UiUtils.dpToPx(context, 2);
        int textColor = ContextCompat.getColor(context, R.color.vcode_text_primary);

        for (String sym : activeSymbols) {
            TextView keyView = new TextView(context);
            keyView.setText(sym);
            keyView.setGravity(Gravity.CENTER);
            keyView.setTextColor(textColor);
            boolean isTextLabel = sym.equals("Tab");
            keyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, isTextLabel ? 11 : 13);
            keyView.setTypeface(isTextLabel ? fm.getUiMedium(context) : fm.getCodeFont(context));
            keyView.setBackgroundResource(R.drawable.vcode_bg_symbol_key_interactive);

            int minWidth = isTextLabel ? UiUtils.dpToPx(context, 42) : UiUtils.dpToPx(context, 32);
            keyView.setMinWidth(minWidth);
            keyView.setPadding(UiUtils.dpToPx(context, 4), 0, UiUtils.dpToPx(context, 4), 0);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, heightPx);
            lp.setMargins(marginPx, 0, marginPx, 0);
            keyView.setLayoutParams(lp);

            binding.layoutPreviewKeys.addView(keyView);
        }
    }

    private void updateActiveChips(Context context, FontManager fm) {
        binding.chipGroupActive.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        for (String sym : activeSymbols) {
            ItemQuickSymbolChipActiveBinding chipBinding =
                    ItemQuickSymbolChipActiveBinding.inflate(inflater, binding.chipGroupActive, false);
            Chip chip = (Chip) chipBinding.getRoot();
            chip.setText(sym);
            boolean isTextLabel = sym.equals("Tab");
            chip.setTypeface(isTextLabel ? fm.getUiMedium(context) : fm.getCodeFont(context));
            chip.setOnCloseIconClickListener(v -> {
                activeSymbols.remove(sym);
                refreshUi();
            });
            binding.chipGroupActive.addView(chip);
        }
    }

    private void updateSuggestedChips(Context context, FontManager fm) {
        binding.chipGroupSuggestions.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        int suggestedCount = 0;
        for (String sym : ALL_SUGGESTIONS) {
            if (activeSymbols.contains(sym)) continue;
            suggestedCount++;

            ItemQuickSymbolChipSuggestedBinding chipBinding =
                    ItemQuickSymbolChipSuggestedBinding.inflate(inflater, binding.chipGroupSuggestions, false);
            Chip chip = (Chip) chipBinding.getRoot();
            chip.setText(sym);
            chip.setTypeface(fm.getCodeFont(context));
            chip.setOnClickListener(v -> {
                activeSymbols.add(sym);
                refreshUi();
            });
            binding.chipGroupSuggestions.addView(chip);
        }

        int visibility = suggestedCount > 0 ? View.VISIBLE : View.GONE;
        binding.tvSuggestionsTitle.setVisibility(visibility);
        binding.chipGroupSuggestions.setVisibility(visibility);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

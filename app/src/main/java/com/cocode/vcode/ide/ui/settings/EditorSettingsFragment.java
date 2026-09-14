package com.cocode.vcode.ide.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.databinding.FragmentSettingsEditorBinding;
import com.cocode.vcode.ide.utils.FontManager;

import java.text.MessageFormat;

/**
 * EditorSettingsFragment manages code editor preferences:
 * line numbers, font size, bracket/quote/tag auto-closing, word wrap, and indentation.
 */
public class EditorSettingsFragment extends Fragment {

    private FragmentSettingsEditorBinding binding;
    private SettingsViewModel viewModel;
    private boolean isUpdatingUi = false;

    public static EditorSettingsFragment newInstance() {
        return new EditorSettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsEditorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SettingsViewModel.class);

        setupDesign();
        setupListeners();
        setupObservers();
    }

    private void setupDesign() {
        FontManager fm = FontManager.getInstance();
        binding.appBarTitle.setTypeface(fm.getUiSemiBold(requireContext()));

        binding.tvShowLineNumbers.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvShowLineNumbersDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvFontSize.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvFontSizeValue.setTypeface(fm.getUiFont(requireContext()));
        binding.tvAutoCloseBrackets.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvAutoCloseBracketsDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvAutoCloseQuotes.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvAutoCloseQuotesDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvAutoCloseTags.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvAutoCloseTagsDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvWordWrap.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvWordWrapDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvAutoIndent.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvAutoIndentDesc.setTypeface(fm.getUiFont(requireContext()));
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.opShowLineNumbers.setOnClickListener(_view ->
                binding.switchLineNumbers.setChecked(!binding.switchLineNumbers.isChecked()));
        binding.opAutoCloseBrackets.setOnClickListener(_view ->
                binding.switchAutoClose.setChecked(!binding.switchAutoClose.isChecked()));
        binding.opAutoCloseQuotes.setOnClickListener(_view ->
                binding.switchAutoCloseQuotes.setChecked(!binding.switchAutoCloseQuotes.isChecked()));
        binding.opAutoCloseTags.setOnClickListener(_view ->
                binding.switchAutoCloseTags.setChecked(!binding.switchAutoCloseTags.isChecked()));
        binding.opWordWrap.setOnClickListener(_view ->
                binding.switchWordWrap.setChecked(!binding.switchWordWrap.isChecked()));
        binding.opAutoIndent.setOnClickListener(_view ->
                binding.switchAutoIndent.setChecked(!binding.switchAutoIndent.isChecked()));

        binding.btnFontIncrease.setOnClickListener(v -> {
            AppSettings current = viewModel.getSettingsLiveData().getValue();
            if (current != null && current.getFontSize() < 32) {
                viewModel.updateFontSize(current.getFontSize() + 1);
            }
        });

        binding.btnFontDecrease.setOnClickListener(v -> {
            AppSettings current = viewModel.getSettingsLiveData().getValue();
            if (current != null && current.getFontSize() > 8) {
                viewModel.updateFontSize(current.getFontSize() - 1);
            }
        });

        binding.switchLineNumbers.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateLineNumbers(isChecked);
        });

        binding.switchAutoClose.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateAutoCloseBrackets(isChecked);
        });

        binding.switchAutoCloseQuotes.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateAutoCloseQuotes(isChecked);
        });

        binding.switchAutoCloseTags.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateAutoCloseHtmlTags(isChecked);
        });

        binding.switchWordWrap.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateWordWrap(isChecked);
        });

        binding.switchAutoIndent.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateAutoIndent(isChecked);
        });
    }

    private void setupObservers() {
        viewModel.getSettingsLiveData().observe(getViewLifecycleOwner(), settings -> {
            if (settings != null) {
                isUpdatingUi = true;
                binding.switchLineNumbers.setChecked(settings.isShowLineNumbers());
                binding.switchAutoClose.setChecked(settings.isAutoCloseBrackets());
                binding.switchAutoCloseQuotes.setChecked(settings.autoCloseQuotes);
                binding.switchAutoCloseTags.setChecked(settings.autoCloseHtmlTags);
                binding.switchWordWrap.setChecked(settings.wordWrap);
                binding.switchAutoIndent.setChecked(settings.autoIndent);
                binding.tvFontSizeValue.setText(MessageFormat.format("{0}px", settings.getFontSize()));
                isUpdatingUi = false;
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

package com.cocode.vcode.ide.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.databinding.FragmentSettingsGeneralBinding;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * GeneralSettingsFragment manages general application preferences:
 * in-app browser preview and auto-save behavior.
 */
public class GeneralSettingsFragment extends Fragment {

    private FragmentSettingsGeneralBinding binding;
    private SettingsViewModel viewModel;
    private boolean isUpdatingUi = false;

    public static GeneralSettingsFragment newInstance() {
        return new GeneralSettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsGeneralBinding.inflate(inflater, container, false);
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

        binding.tvShowInAppPreview.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvShowInAppPreviewDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvAutoSave.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvAutoSaveDesc.setTypeface(fm.getUiFont(requireContext()));
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.opShowInAppPreview.setOnClickListener(_view ->
                binding.switchInAppPreview.setChecked(!binding.switchInAppPreview.isChecked()));

        binding.opAutoSave.setOnClickListener(_view ->
                binding.switchAutoSave.setChecked(!binding.switchAutoSave.isChecked()));

        binding.switchInAppPreview.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateOpenPreviewInApp(isChecked);
        });

        binding.switchAutoSave.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateAutoSave(isChecked);
        });
    }

    private void setupObservers() {
        viewModel.getSettingsLiveData().observe(getViewLifecycleOwner(), settings -> {
            if (settings != null) {
                isUpdatingUi = true;
                binding.switchInAppPreview.setChecked(settings.openPreviewInApp);
                binding.switchAutoSave.setChecked(settings.autoSave);
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

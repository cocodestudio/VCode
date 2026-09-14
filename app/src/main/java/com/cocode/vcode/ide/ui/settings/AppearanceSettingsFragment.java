package com.cocode.vcode.ide.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.databinding.FragmentSettingsAppearanceBinding;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * AppearanceSettingsFragment manages theme selection (System, Dark, Light).
 * Theme changes are immediately applied to the entire application.
 */
public class AppearanceSettingsFragment extends Fragment {

    private FragmentSettingsAppearanceBinding binding;
    private SettingsViewModel viewModel;
    private boolean isUpdatingUi = false;

    public static AppearanceSettingsFragment newInstance() {
        return new AppearanceSettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsAppearanceBinding.inflate(inflater, container, false);
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

        binding.radioThemeSystem.setTypeface(fm.getUiFont(requireContext()));
        binding.radioThemeDark.setTypeface(fm.getUiFont(requireContext()));
        binding.radioThemeLight.setTypeface(fm.getUiFont(requireContext()));
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            if (isUpdatingUi) return;

            int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

            if (checkedId == R.id.radio_theme_system) {
                viewModel.updateTheme(AppSettings.Theme.SYSTEM);
            } else if (checkedId == R.id.radio_theme_dark) {
                viewModel.updateTheme(AppSettings.Theme.DARK);
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            } else if (checkedId == R.id.radio_theme_light) {
                viewModel.updateTheme(AppSettings.Theme.LIGHT);
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            }

            AppCompatDelegate.setDefaultNightMode(mode);
        });
    }

    private void setupObservers() {
        viewModel.getSettingsLiveData().observe(getViewLifecycleOwner(), settings -> {
            if (settings != null) {
                isUpdatingUi = true;
                switch (settings.getTheme()) {
                    case SYSTEM:
                        binding.rgTheme.check(R.id.radio_theme_system);
                        break;
                    case DARK:
                        binding.rgTheme.check(R.id.radio_theme_dark);
                        break;
                    case LIGHT:
                        binding.rgTheme.check(R.id.radio_theme_light);
                        break;
                }
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

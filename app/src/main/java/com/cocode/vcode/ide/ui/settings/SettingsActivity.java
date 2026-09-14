package com.cocode.vcode.ide.ui.settings;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ActivitySettingsBinding;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * SettingsActivity hosts the application settings experience using a two-level navigation model.
 * The primary screen presents high-level category cards (Editor, Appearance, Git, General),
 * which navigate into dedicated sub-screens managed via Fragment backstack.
 * A single shared SettingsViewModel is hosted here and shared across all child fragments.
 */
public class SettingsActivity extends BaseActivity {

    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apply standard edge-to-edge system bar padding
        UiUtils.applySystemBarInsets(binding.getRoot());

        // Initialize single activity-scoped ViewModel
        viewModel = new ViewModelProvider(this, new SettingsViewModel.Factory(this)).get(SettingsViewModel.class);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_fragment_container, SettingsHomeFragment.newInstance())
                    .commit();
        }
    }

    /**
     * Navigates into a settings category sub-screen with animation and back stack support.
     *
     * @param fragment The target category Fragment to display.
     */
    public void navigateToCategory(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                        R.anim.vcode_settings_anim_enter,
                        R.anim.vcode_settings_anim_exit,
                        R.anim.vcode_settings_anim_pop_enter,
                        R.anim.vcode_settings_anim_pop_exit
                )
                .replace(R.id.settings_fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}
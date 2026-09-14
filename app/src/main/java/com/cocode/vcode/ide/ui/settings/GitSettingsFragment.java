package com.cocode.vcode.ide.ui.settings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.databinding.FragmentSettingsGitBinding;
import com.cocode.vcode.ide.ui.sheets.git.GitAuthorInfoBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.SshKeyBottomSheet;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * GitSettingsFragment manages Git configurations:
 * default branch name, hard reset confirmations, author credentials, and SSH keys.
 */
public class GitSettingsFragment extends Fragment {

    private FragmentSettingsGitBinding binding;
    private SettingsViewModel viewModel;
    private boolean isUpdatingUi = false;

    public static GitSettingsFragment newInstance() {
        return new GitSettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsGitBinding.inflate(inflater, container, false);
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

        binding.tvDefaultBranch.setTypeface(fm.getUiMedium(requireContext()));
        binding.etDefaultBranchValue.setTypeface(fm.getCodeFont(requireContext()));
        binding.tvHardReset.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvHardResetDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvGitCredentials.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvGitCredentialsDesc.setTypeface(fm.getUiFont(requireContext()));
        binding.tvSshKey.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvSshKeyDesc.setTypeface(fm.getUiFont(requireContext()));
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.opConfirmHardReset.setOnClickListener(_view ->
                binding.switchConfirmReset.setChecked(!binding.switchConfirmReset.isChecked()));

        binding.switchConfirmReset.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isUpdatingUi) viewModel.updateConfirmHardReset(isChecked);
        });

        binding.etDefaultBranchValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingUi) viewModel.updateDefaultBranch(s.toString().trim());
            }
        });

        binding.opGitCredentials.setOnClickListener(_view -> {
            AppSettings settings = viewModel.getSettingsLiveData().getValue();
            if (settings != null) {
                String name = settings.gitAuthorName != null ? settings.gitAuthorName : "";
                String email = settings.gitAuthorEmail != null ? settings.gitAuthorEmail : "";
                String buttonText = (name.isEmpty() && email.isEmpty()) ? "Save" : "Edit";
                GitAuthorInfoBottomSheet sheet = GitAuthorInfoBottomSheet.newInstance(
                        name,
                        email,
                        buttonText
                );
                sheet.setListener((n, e) -> viewModel.updateGitCredentials(n, e));
                sheet.show(getChildFragmentManager(), "GitAuthorInfoBottomSheet");
            }
        });

        binding.opSshKey.setOnClickListener(_view -> {
            SshKeyBottomSheet sheet = SshKeyBottomSheet.newInstance();
            sheet.show(getChildFragmentManager(), "SshKeyBottomSheet");
        });
    }

    private void setupObservers() {
        viewModel.getSettingsLiveData().observe(getViewLifecycleOwner(), settings -> {
            if (settings != null) {
                isUpdatingUi = true;

                binding.switchConfirmReset.setChecked(settings.gitConfirmHardReset);

                if (!binding.etDefaultBranchValue.getText().toString().equals(settings.gitDefaultBranch)) {
                    binding.etDefaultBranchValue.setText(settings.gitDefaultBranch);
                }

                if (settings.gitAuthorName != null && !settings.gitAuthorName.trim().isEmpty()) {
                    binding.tvGitCredentialsDesc.setText(settings.gitAuthorName + " <" + settings.gitAuthorEmail + ">");
                } else {
                    binding.tvGitCredentialsDesc.setText(R.string.vcode_not_configured);
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

package com.cocode.vcode.ide.ui.sheets.git;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetCreateGithubRepoBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;

/**
 * Bottom sheet dialog for creating a new repository on GitHub and pushing the local project.
 */
public class CreateGitHubRepoBottomSheet extends BaseBottomSheetDialogFragment {
    private BottomSheetCreateGithubRepoBinding binding;
    private CreateRepoListener listener;
    private String projectName;

    public static void show(FragmentManager manager, String projectName, CreateRepoListener listener) {
        CreateGitHubRepoBottomSheet sheet = new CreateGitHubRepoBottomSheet();
        sheet.projectName = projectName;
        sheet.setListener(listener);
        sheet.show(manager, "CreateGitHubRepoBottomSheet");
    }

    public void setListener(CreateRepoListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCreateGithubRepoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        int bgColor = ContextCompat.getColor(context, R.color.vcode_bg_elevated);

        UiUtils.setViewRounded(binding.etRepoName, UiUtils.dpToPx(context, 10), bgColor);
        UiUtils.setViewRounded(binding.etRepoDescription, UiUtils.dpToPx(context, 10), bgColor);

        FontManager fm = FontManager.getInstance();
        binding.tvTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvSubtitle.setTypeface(fm.getUiMedium(context));
        binding.tvRepoNameLabel.setTypeface(fm.getUiSemiBold(context));
        binding.tvDescLabel.setTypeface(fm.getUiSemiBold(context));
        binding.tvPrivateRepoLabel.setTypeface(fm.getUiMedium(context));
        binding.tvPrivateRepoDesc.setTypeface(fm.getUiFont(context));
        binding.btnPublish.setTypeface(fm.getUiSemiBold(context));

        binding.etRepoName.setTypeface(fm.getUiMedium(context));
        binding.etRepoDescription.setTypeface(fm.getUiMedium(context));

        if (projectName != null && !projectName.isEmpty()) {
            binding.etRepoName.setText(projectName);
        }

        binding.privateRepoBtn.setOnClickListener(v -> binding.switchPrivate.setChecked(!binding.switchPrivate.isChecked()));

        binding.btnPublish.setOnClickListener(v -> {
            String name = binding.etRepoName.getText() != null ? binding.etRepoName.getText().toString().trim() : "";
            String desc = binding.etRepoDescription.getText() != null ? binding.etRepoDescription.getText().toString().trim() : "";
            boolean isPrivate = binding.switchPrivate.isChecked();

            if (name.isEmpty()) {
                binding.etRepoName.setError("Repository name is required");
                binding.etRepoName.requestFocus();
                return;
            }

            if (listener != null) {
                listener.onPublish(name, desc, isPrivate);
            }
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public interface CreateRepoListener {
        void onPublish(String name, String desc, boolean isPrivate);
    }
}

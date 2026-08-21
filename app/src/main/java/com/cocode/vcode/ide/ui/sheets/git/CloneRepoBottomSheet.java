package com.cocode.vcode.ide.ui.sheets.git;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetCloneRepoBinding;
import com.cocode.vcode.ide.git.core.GitCredentialStore;
import com.cocode.vcode.ide.git.service.GitCloneService;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

/**
 * Bottom sheet dialog for initiating Git repository clone operations from a remote URL.
 */
public class CloneRepoBottomSheet extends BaseBottomSheetDialogFragment {

    private BottomSheetCloneRepoBinding binding;
    private Runnable onCloneSuccess;

    public static void show(androidx.fragment.app.FragmentManager manager, @Nullable Runnable onCloneSuccess) {
        CloneRepoBottomSheet sheet = new CloneRepoBottomSheet();
        sheet.setOnCloneSuccess(onCloneSuccess);
        sheet.show(manager, "CloneRepoBottomSheet");
    }

    public void setOnCloneSuccess(Runnable onCloneSuccess) {
        this.onCloneSuccess = onCloneSuccess;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCloneRepoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        designUI();
        setupListeners();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    private void setupListeners() {
        binding.etRepoUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String url = s.toString().trim();
                if (url.endsWith(".git")) {
                    url = url.substring(0, url.length() - 4);
                }
                int lastSlash = url.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < url.length() - 1) {
                    String candidateName = url.substring(lastSlash + 1);
                    if (binding.etProjectName.getText().toString().trim().isEmpty()) {
                        binding.etProjectName.setText(candidateName);
                    }
                }
            }
        });

        binding.btnExecuteClone.setOnClickListener(v -> initiateRepositoryCloneWorkflow());
        binding.btnRunBackground.setOnClickListener(v -> dismiss());
    }

    private void initiateRepositoryCloneWorkflow() {
        String repoUrl = binding.etRepoUrl.getText().toString().trim();
        String projectName = binding.etProjectName.getText().toString().trim();

        if (repoUrl.isEmpty()) {
            Toast.makeText(getContext(), R.string.vcode_repo_url_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (projectName.isEmpty()) {
            Toast.makeText(getContext(), R.string.vcode_project_name_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(getContext(), R.string.vcode_storage_permission_required, Toast.LENGTH_SHORT).show();
            return;
        }

        setCancelable(false);
        binding.layoutForm.setVisibility(View.GONE);
        binding.layoutProgress.setVisibility(View.VISIBLE);

        Context context = requireContext().getApplicationContext();
        String projectId = UUID.randomUUID().toString();
        File rootDir = FileUtils.getProjectsDir(context);
        File targetProjectDirectory = new File(rootDir, projectId);

        GitCredentialStore store = new GitCredentialStore();
        String gitUser = store.getUsername(context);
        String gitToken;
        try {
            gitToken = store.getToken(context);
        } catch (Exception e) {
            notifyFailure(getString(R.string.vcode_github_auth_token_not_found));
            return;
        }

        GitCloneService.CloneListener cloneListener = new GitCloneService.CloneListener() {
            @Override
            public void onProgress(String task, int done, int total, int percentage) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) {
                        binding.tvProgressTask.setText(task);
                        if (total > 0) {
                            binding.progressIndicator.setIndeterminate(false);
                            binding.progressIndicator.setProgressCompat(percentage, true);
                            binding.tvProgressPercentage.setText(String.format(Locale.getDefault(), "%d%%", percentage));
                            binding.tvProgressDetails.setText(getString(R.string.vcode_clone_progress_details, done, total));
                        } else {
                            binding.progressIndicator.setIndeterminate(true);
                            binding.tvProgressPercentage.setText(R.string.vcode_0);
                            binding.tvProgressDetails.setText(R.string.vcode_working);
                        }
                    }
                });
            }

            @Override
            public void onUpdate(int completed) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) {
                        binding.tvProgressDetails.setText(getString(R.string.vcode_entities_synchronized, completed));
                    }
                });
            }

            @Override
            public void onSuccess() {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) {
                        if (onCloneSuccess != null) {
                            onCloneSuccess.run();
                        }
                        dismissAllowingStateLoss();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                notifyFailure(error);
            }
        };
        GitCloneService.setListener(cloneListener);

        Intent serviceIntent = new Intent(context, GitCloneService.class);
        serviceIntent.setAction(GitCloneService.ACTION_START_CLONE);
        serviceIntent.putExtra(GitCloneService.EXTRA_REPO_URL, repoUrl);
        serviceIntent.putExtra(GitCloneService.EXTRA_PROJECT_NAME, projectName);
        serviceIntent.putExtra(GitCloneService.EXTRA_TARGET_DIR, targetProjectDirectory.getAbsolutePath());
        serviceIntent.putExtra(GitCloneService.EXTRA_GIT_USER, gitUser);
        serviceIntent.putExtra(GitCloneService.EXTRA_GIT_TOKEN, gitToken);
        serviceIntent.putExtra(GitCloneService.EXTRA_PROJECT_ID, projectId);

        ContextCompat.startForegroundService(context, serviceIntent);
    }

    private void notifyFailure(String traceMessage) {
        ExecutorProvider.getInstance().runOnMain(() -> {
            if (isAdded()) {
                setCancelable(true);
                binding.layoutProgress.setVisibility(View.GONE);
                binding.layoutForm.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), getString(R.string.vcode_clone_failed, traceMessage), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void designUI() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvCloneTitle.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvCloneSubtitle.setTypeface(fm.getUiMedium(ctx));
        binding.tvRepoUrlLabel.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvProjectNameLabel.setTypeface(fm.getUiSemiBold(ctx));

        binding.etRepoUrl.setTypeface(fm.getUiMedium(ctx));
        binding.etProjectName.setTypeface(fm.getUiMedium(ctx));
        binding.btnExecuteClone.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvProgressTask.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvProgressDetails.setTypeface(fm.getUiMedium(ctx));
        binding.tvProgressPercentage.setTypeface(fm.getUiSemiBold(ctx));
        binding.btnRunBackground.setTypeface(fm.getUiMedium(ctx));

        UiUtils.setViewRounded(binding.etRepoUrl, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
        UiUtils.setViewRounded(binding.etProjectName, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
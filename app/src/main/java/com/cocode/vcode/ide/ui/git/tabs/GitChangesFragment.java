package com.cocode.vcode.ide.ui.git.tabs;

import android.content.Context;
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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.FragmentGitChangesBinding;
import com.cocode.vcode.ide.git.adapters.GitFilesAdapter;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.ui.sheets.files.DeleteBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.DiffViewerBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.GitAuthorInfoBottomSheet;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * GitChangesFragment displays the staging area for the current repository.
 * It allows users to view unstaged and staged modifications, stage/unstage files,
 * and perform commits with custom messages and optional amending.
 */
public class GitChangesFragment extends Fragment implements GitFilesAdapter.GitFileListener {

    private FragmentGitChangesBinding binding;
    private GitViewModel viewModel;
    private GitFilesAdapter unstagedAdapter;
    private GitFilesAdapter stagedAdapter;

    /**
     * Internal flags to track the expanded/collapsed state of the staging sections.
     */
    private boolean isUnstagedExpanded = true;
    private boolean isStagedExpanded = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentGitChangesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Bind to the Activity-scoped ViewModel to share Git state
        viewModel = new ViewModelProvider(requireActivity()).get(GitViewModel.class);

        // Apply visual styling and initialize UI components
        UiUtils.setViewRounded(binding.etCommitMessage, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        setupTypefaces();
        setupCollapsiblePanels();
        setupRecyclerViews();
        setupCommitUI();
        observeData();
    }

    /**
     * Applies specialized UI fonts to labels, buttons, and input fields.
     */
    private void setupTypefaces() {
        Context context = requireContext();
        binding.tvLabelUnstaged.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvUnstagedCount.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.btnStageAll.setTypeface(FontManager.getInstance().getUiSemiBold(context));

        binding.tvLabelStaged.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvStagedCount.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.btnUnstageAll.setTypeface(FontManager.getInstance().getUiSemiBold(context));

        binding.etCommitMessage.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.cbAmendCommit.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.tvCharCounter.setTypeface(FontManager.getInstance().getUiFont(context));
        binding.btnCommit.setTypeface(FontManager.getInstance().getUiSemiBold(context));
    }

    /**
     * Configures the collapsible headers for Unstaged and Staged file lists.
     */
    private void setupCollapsiblePanels() {
        // Toggle Unstaged section visibility
        binding.layoutUnstagedHeader.setOnClickListener(v -> {
            isUnstagedExpanded = !isUnstagedExpanded;
            binding.rvUnstaged.setVisibility(isUnstagedExpanded ? View.VISIBLE : View.GONE);
            binding.ivUnstagedChevron.animate().rotation(isUnstagedExpanded ? -90f : 0f).setDuration(200).start();
        });

        // Toggle Staged section visibility
        binding.layoutStagedHeader.setOnClickListener(v -> {
            isStagedExpanded = !isStagedExpanded;
            binding.rvStaged.setVisibility(isStagedExpanded ? View.VISIBLE : View.GONE);
            binding.ivStagedChevron.animate().rotation(isStagedExpanded ? -90f : 0f).setDuration(200).start();
        });

        // Prevent action button clicks from bubbling up to the header toggle
        binding.btnStageAll.setFocusable(false);
        binding.btnStageAll.setClickable(true);
        binding.btnUnstageAll.setFocusable(false);
        binding.btnUnstageAll.setClickable(true);
    }

    /**
     * Initializes the RecyclerViews for unstaged and staged modified files.
     */
    private void setupRecyclerViews() {
        String projectName = requireActivity().getIntent().getStringExtra("project_name");

        unstagedAdapter = new GitFilesAdapter(this);
        unstagedAdapter.setProjectName(projectName);
        binding.rvUnstaged.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvUnstaged.setAdapter(unstagedAdapter);

        stagedAdapter = new GitFilesAdapter(this);
        stagedAdapter.setProjectName(projectName);
        binding.rvStaged.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvStaged.setAdapter(stagedAdapter);

        binding.btnStageAll.setOnClickListener(v -> viewModel.stageAll());
        binding.btnUnstageAll.setOnClickListener(v -> viewModel.unstageAll());

        if (binding.btnDiscardAll != null) {
            binding.btnDiscardAll.setOnClickListener(v -> {
                DeleteBottomSheet.show(
                        getChildFragmentManager(),
                        DeleteBottomSheet.DeleteType.DISCARD,
                        "",
                        null,
                        () -> viewModel.discardAll()
                );
            });
        }
    }

    /**
     * Sets up the commit interaction logic, including character counting and author validation.
     */
    private void setupCommitUI() {
        // Live character counter for the commit message
        binding.etCommitMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.tvCharCounter.setText(String.valueOf(s.length()).concat(" / 120"));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.btnCommit.setOnClickListener(v -> {
            String message = binding.etCommitMessage.getText().toString().trim();
            if (message.isEmpty()) {
                Toast.makeText(getContext(), R.string.vcode_commit_message_is_required, Toast.LENGTH_SHORT).show();
                return;
            }

            boolean amend = binding.cbAmendCommit.isChecked();
            // Verify if author metadata is configured before proceeding with the commit
            if (viewModel.shouldPromptForAuthor()) {
                GitAuthorInfoBottomSheet authorInfoSheet = new GitAuthorInfoBottomSheet();
                authorInfoSheet.setListener((name, email) -> {
                    viewModel.saveLocalAuthor(name, email);
                    if (amend) viewModel.amendCommit(message);
                    else viewModel.commit(message);
                });
                authorInfoSheet.show(getParentFragmentManager(), "AuthorInfoSheet");
            } else {
                if (amend) viewModel.amendCommit(message);
                else viewModel.commit(message);
            }

            // Reset input state upon submission
            binding.etCommitMessage.setText("");
            binding.cbAmendCommit.setChecked(false);
        });
    }

    /**
     * Connects UI observers to the ViewModel's file status streams.
     */
    private void observeData() {
        viewModel.getUnstagedFiles().observe(getViewLifecycleOwner(), files -> {
            unstagedAdapter.submitList(files);
            binding.tvUnstagedCount.setText("(".concat(String.valueOf(files.size())).concat(")"));
            if (binding.btnDiscardAll != null) {
                binding.btnDiscardAll.setVisibility(files.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });

        viewModel.getStagedFiles().observe(getViewLifecycleOwner(), files -> {
            stagedAdapter.submitList(files);
            binding.tvStagedCount.setText("(".concat(String.valueOf(files.size())).concat(")"));
        });
    }

    @Override
    public void onFileClick(GitFileItem item) {
        // Launch a visual diff viewer for the selected modified file
        DiffViewerBottomSheet sheet = DiffViewerBottomSheet.newInstance(item);
        sheet.show(getChildFragmentManager(), "DiffViewer");
    }

    @Override
    public void onActionClick(GitFileItem item) {
        // Toggle staging status for an individual file
        if (item.isStaged()) viewModel.unstageFile(item.getPath());
        else viewModel.stageFile(item.getPath());
    }

    @Override
    public void onDiscardClick(GitFileItem item) {
        DeleteBottomSheet.show(
                getChildFragmentManager(),
                DeleteBottomSheet.DeleteType.DISCARD,
                item.getFileName(),
                null,
                () -> viewModel.discardFile(item.getPath())
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
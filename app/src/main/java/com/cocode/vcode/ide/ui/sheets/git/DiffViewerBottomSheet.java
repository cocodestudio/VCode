package com.cocode.vcode.ide.ui.sheets.git;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetDiffViewerBinding;
import com.cocode.vcode.ide.git.core.GitRepository;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.ui.commitdetails.CommitDetailsActivity;
import com.cocode.vcode.ide.ui.commitdetails.CommitDetailsViewModel;
import com.cocode.vcode.ide.ui.editor.EditorActivity;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;

/**
 * DiffViewerBottomSheet provides a line-by-line visual comparison of file changes.
 * it supports both workspace diffs (staged/unstaged) and historical commit diffs.
 * The UI highlights additions in green and removals in red, mimicking standard Git diff output.
 */
public class DiffViewerBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetDiffViewerBinding binding;
    private GitFileItem fileItem;

    /**
     * The specific commit SHA to compare against, or null for workspace diffs.
     */
    private String commitSha;

    /**
     * Creates a new instance for viewing workspace changes.
     */
    public static DiffViewerBottomSheet newInstance(GitFileItem item) {
        DiffViewerBottomSheet sheet = new DiffViewerBottomSheet();
        sheet.fileItem = item;
        return sheet;
    }

    /**
     * Creates a new instance for viewing changes within a specific commit.
     */
    public static DiffViewerBottomSheet newInstance(String commitSha, GitFileItem item) {
        DiffViewerBottomSheet sheet = new DiffViewerBottomSheet();
        sheet.commitSha = commitSha;
        sheet.fileItem = item;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDiffViewerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup header metadata
        binding.tvDiffFilename.setText(fileItem.getFileName());
        binding.tvDiffFilename.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));
        binding.btnCloseDiff.setOnClickListener(v -> dismiss());

        binding.btnGoToFile.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));
        if (getActivity() instanceof com.cocode.vcode.ide.ui.git.GitActivity) {
            binding.btnGoToFile.setVisibility(View.VISIBLE);
            binding.btnGoToFile.setOnClickListener(v -> {
                GitRepository repository = new ViewModelProvider(requireActivity()).get(GitViewModel.class).getRepository();
                if (repository != null && repository.getRepoDir() != null) {
                    File fileToOpen = new File(repository.getRepoDir(), fileItem.getPath());
                    Intent intent = new Intent(requireContext(), EditorActivity.class);
                    intent.putExtra(EditorActivity.EXTRA_OPEN_FILE_PATH, fileToOpen.getAbsolutePath());
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    dismiss();
                }
            });
        } else {
            binding.btnGoToFile.setVisibility(View.GONE);
        }

        // Asynchronously load the diff data from the repository
        loadDiff();
    }

    /**
     * Resolves the appropriate repository instance and retrieves the diff string.
     */
    private void loadDiff() {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                GitRepository repository;
                String diff;

                // Dynamically resolve the correct repository instance by evaluating the host activity context
                if (getActivity() instanceof CommitDetailsActivity) {
                    repository = new ViewModelProvider(requireActivity()).get(CommitDetailsViewModel.class).getRepository();
                } else {
                    repository = new ViewModelProvider(requireActivity()).get(GitViewModel.class).getRepository();
                }

                if (commitSha != null && !commitSha.isEmpty()) {
                    // Fetch diff for a historical commit
                    diff = repository.getCommitFileDiff(commitSha, fileItem.getPath());
                } else {
                    // Fetch diff for current workspace modifications
                    diff = repository.getFileDiff(fileItem.getPath(), fileItem.isStaged());
                }

                // Transition back to main thread for UI rendering
                ExecutorProvider.getInstance().runOnMain(() -> renderDiff(diff));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), R.string.vcode_failed_to_load_diff, Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                });
            }
        });
    }

    /**
     * Parses the raw diff string and dynamically populates the UI with highlighted lines.
     * Supports word-level diffing for single-line changes.
     *
     * @param diff The raw JGit unified diff output.
     */
    private void renderDiff(String diff) {
        // Handle the case where no changes were found
        if (diff == null || diff.trim().isEmpty()) {
            View emptyLineView = getLayoutInflater().inflate(R.layout.item_diff_line, binding.layoutDiffLines, false);
            TextView tvContent = emptyLineView.findViewById(R.id.root_view).findViewById(R.id.tv_line_content);
            tvContent.setText(R.string.vcode_no_changes_detected_in_this);
            tvContent.setTextColor(ContextCompat.getColor(requireContext(), R.color.vcode_text_secondary));
            binding.layoutDiffLines.addView(emptyLineView);
            return;
        }

        String[] lines = diff.split("\n");
        int i = 0;
        while (i < lines.length) {
            String removedLine = lines[i];

            // Simple heuristic for word-level diff: exactly one deletion followed by exactly one addition
            if (removedLine.startsWith("-") && i + 1 < lines.length && lines[i + 1].startsWith("+")
                    && (i + 2 >= lines.length || (!lines[i + 2].startsWith("-") && !lines[i + 2].startsWith("+")))) {
                String addedLine = lines[i + 1];

                CharSequence[] spans = computeWordDiff(removedLine.substring(1), addedLine.substring(1));

                // Render removed line
                View removedView = getLayoutInflater().inflate(R.layout.item_diff_line, binding.layoutDiffLines, false);
                TextView tvRemovedContent = removedView.findViewById(R.id.root_view).findViewById(R.id.tv_line_content);
                android.text.SpannableStringBuilder ssbRemoved = new android.text.SpannableStringBuilder("-");
                ssbRemoved.append(spans[0]);
                tvRemovedContent.setText(ssbRemoved);
                removedView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_removed_bg));
                tvRemovedContent.setTextColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_removed_text));
                binding.layoutDiffLines.addView(removedView);

                // Render added line
                View addedView = getLayoutInflater().inflate(R.layout.item_diff_line, binding.layoutDiffLines, false);
                TextView tvAddedContent = addedView.findViewById(R.id.root_view).findViewById(R.id.tv_line_content);
                android.text.SpannableStringBuilder ssbAdded = new android.text.SpannableStringBuilder("+");
                ssbAdded.append(spans[1]);
                tvAddedContent.setText(ssbAdded);
                addedView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_added_bg));
                tvAddedContent.setTextColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_added_text));
                binding.layoutDiffLines.addView(addedView);

                i += 2;
                continue;
            }

            View lineView = getLayoutInflater().inflate(R.layout.item_diff_line, binding.layoutDiffLines, false);
            TextView tvContent = lineView.findViewById(R.id.root_view).findViewById(R.id.tv_line_content);
            tvContent.setText(removedLine);

            if (removedLine.startsWith("+")) {
                lineView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_added_bg));
                tvContent.setTextColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_added_text));
            } else if (removedLine.startsWith("-")) {
                lineView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_removed_bg));
                tvContent.setTextColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_removed_text));
            } else if (removedLine.startsWith("@@")) {
                lineView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_hunk_bg));
            }

            binding.layoutDiffLines.addView(lineView);
            i++;
        }
    }

    /**
     * Computes word-level diffs using a simple prefix/suffix matching approach.
     */
    private CharSequence[] computeWordDiff(String removed, String added) {
        int prefixLength = 0;
        int minLength = Math.min(removed.length(), added.length());
        while (prefixLength < minLength && removed.charAt(prefixLength) == added.charAt(prefixLength)) {
            prefixLength++;
        }

        int suffixLength = 0;
        // Cap suffix so prefixLength + suffixLength never exceeds either string's length
        int maxSuffix = minLength - prefixLength;
        while (suffixLength < maxSuffix
                && removed.charAt(removed.length() - 1 - suffixLength) == added.charAt(added.length() - 1 - suffixLength)) {
            suffixLength++;
        }

        String removedDiff = removed.substring(prefixLength, removed.length() - suffixLength);
        String addedDiff = added.substring(prefixLength, added.length() - suffixLength);

        android.text.SpannableString spanRemoved = new android.text.SpannableString(removed);
        if (!removedDiff.isEmpty()) {
            spanRemoved.setSpan(new android.text.style.BackgroundColorSpan(
                            ContextCompat.getColor(requireContext(), R.color.vcode_diff_removed_word_bg)),
                    prefixLength, prefixLength + removedDiff.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        android.text.SpannableString spanAdded = new android.text.SpannableString(added);
        if (!addedDiff.isEmpty()) {
            spanAdded.setSpan(new android.text.style.BackgroundColorSpan(
                            ContextCompat.getColor(requireContext(), R.color.vcode_diff_added_word_bg)),
                    prefixLength, prefixLength + addedDiff.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return new CharSequence[]{spanRemoved, spanAdded};
    }
}
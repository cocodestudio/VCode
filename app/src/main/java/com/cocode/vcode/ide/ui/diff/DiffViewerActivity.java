package com.cocode.vcode.ide.ui.diff;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ActivityDiffViewerBinding;
import com.cocode.vcode.ide.databinding.ItemDiffLineBinding;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.ui.editor.EditorActivity;
import com.cocode.vcode.ide.utils.FileIconHelper;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.io.File;

/**
 * DiffViewerActivity provides a full-screen, responsive interface for reviewing Git diffs.
 * It supports word-level delta highlighting, hunk separation, and direct navigation to open
 * the viewed file in the code editor.
 */
public class DiffViewerActivity extends BaseActivity {

    public static final String EXTRA_PROJECT_PATH = "extra_project_path";
    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_FILE_NAME = "extra_file_name";
    public static final String EXTRA_IS_STAGED = "extra_is_staged";
    public static final String EXTRA_COMMIT_SHA = "extra_commit_sha";
    public static final String EXTRA_OLD_REF = "extra_old_ref";
    public static final String EXTRA_NEW_REF = "extra_new_ref";

    private ActivityDiffViewerBinding binding;
    private DiffViewerViewModel viewModel;

    private String projectPath;
    private String filePath;
    private String fileName;
    private boolean isStaged;
    private String commitSha;
    private String oldRef;
    private String newRef;

    /**
     * Factory method for creating an intent to inspect working-tree changes.
     */
    public static Intent newIntent(Context context, String projectPath, GitFileItem item) {
        Intent intent = new Intent(context, DiffViewerActivity.class);
        intent.putExtra(EXTRA_PROJECT_PATH, projectPath);
        intent.putExtra(EXTRA_FILE_PATH, item.getPath());
        intent.putExtra(EXTRA_FILE_NAME, item.getFileName());
        intent.putExtra(EXTRA_IS_STAGED, item.isStaged());
        return intent;
    }

    /**
     * Factory method for creating an intent to inspect a specific historical commit's changes.
     */
    public static Intent newIntent(Context context, String projectPath, String commitSha, GitFileItem item) {
        Intent intent = new Intent(context, DiffViewerActivity.class);
        intent.putExtra(EXTRA_PROJECT_PATH, projectPath);
        intent.putExtra(EXTRA_COMMIT_SHA, commitSha);
        intent.putExtra(EXTRA_FILE_PATH, item.getPath());
        intent.putExtra(EXTRA_FILE_NAME, item.getFileName());
        intent.putExtra(EXTRA_IS_STAGED, item.isStaged());
        return intent;
    }

    /**
     * Factory method for creating an intent to compare a file between two branch/tree references.
     */
    public static Intent newIntent(Context context, String projectPath, String oldRef, String newRef, GitFileItem item) {
        Intent intent = new Intent(context, DiffViewerActivity.class);
        intent.putExtra(EXTRA_PROJECT_PATH, projectPath);
        intent.putExtra(EXTRA_OLD_REF, oldRef);
        intent.putExtra(EXTRA_NEW_REF, newRef);
        intent.putExtra(EXTRA_FILE_PATH, item.getPath());
        intent.putExtra(EXTRA_FILE_NAME, item.getFileName());
        intent.putExtra(EXTRA_IS_STAGED, item.isStaged());
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDiffViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UiUtils.applySystemBarInsets(binding.getRoot());

        projectPath = getIntent().getStringExtra(EXTRA_PROJECT_PATH);
        filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
        isStaged = getIntent().getBooleanExtra(EXTRA_IS_STAGED, false);
        commitSha = getIntent().getStringExtra(EXTRA_COMMIT_SHA);
        oldRef = getIntent().getStringExtra(EXTRA_OLD_REF);
        newRef = getIntent().getStringExtra(EXTRA_NEW_REF);

        viewModel = new ViewModelProvider(this).get(DiffViewerViewModel.class);

        setupUI();
        setupObservers();

        if (savedInstanceState == null) {
            viewModel.loadDiff(projectPath, filePath, isStaged, commitSha, oldRef, newRef);
        }
    }

    private void setupUI() {
        FontManager fm = FontManager.getInstance();
        binding.tvDiffFilename.setTypeface(fm.getUiSemiBold(this));
        binding.tvDiffFilepath.setTypeface(fm.getUiFont(this));
        binding.btnGoToFile.setTypeface(fm.getUiSemiBold(this));
        binding.tvEmptyDiffTitle.setTypeface(fm.getUiMedium(this));

        String displayName = (fileName != null && !fileName.isEmpty()) ? fileName : filePath;
        binding.tvDiffFilename.setText(displayName);

        if (oldRef != null && newRef != null) {
            String shortOld = oldRef.replace("refs/heads/", "");
            String shortNew = newRef.replace("refs/remotes/", "");
            binding.tvDiffFilepath.setText(shortOld + " ➔ " + shortNew + " • " + (filePath != null ? filePath : ""));
        } else if (commitSha != null && !commitSha.trim().isEmpty()) {
            String shortSha = commitSha.length() > 7 ? commitSha.substring(0, 7) : commitSha;
            binding.tvDiffFilepath.setText(shortSha.concat(" • ").concat(filePath != null ? filePath : ""));
        } else {
            binding.tvDiffFilepath.setText(filePath != null ? filePath : "");
        }

        FileIconHelper.setFileIconAndColor(binding.ivFileIcon, displayName);

        binding.btnBack.setOnClickListener(v -> finish());

        setupGoToFileButton();
    }

    /**
     * Configures the "Go to file" editor button. If the file exists on disk, tapping
     * navigates straight to EditorActivity and closes the diff view.
     */
    private void setupGoToFileButton() {
        File fileToOpen = (projectPath != null && filePath != null) ? new File(projectPath, filePath) : null;
        if (fileToOpen != null && fileToOpen.exists()) {
            binding.btnGoToFile.setVisibility(View.VISIBLE);
            binding.btnGoToFile.setOnClickListener(v -> {
                Intent intent = new Intent(this, EditorActivity.class);
                intent.putExtra(EditorActivity.EXTRA_OPEN_FILE_PATH, fileToOpen.getAbsolutePath());
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        } else {
            binding.btnGoToFile.setVisibility(View.GONE);
        }
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(this, loading -> {
            binding.diffLoadingIndicator.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getDiffContent().observe(this, this::renderDiff);
    }

    /**
     * Parses and renders unified diff output into styled line views with word-level highlight spans.
     */
    private void renderDiff(String diff) {
        binding.layoutDiffLines.removeAllViews();

        if (diff == null || diff.trim().isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.diffScrollVertical.setVisibility(View.GONE);
            return;
        }

        binding.layoutEmptyState.setVisibility(View.GONE);
        binding.diffScrollVertical.setVisibility(View.VISIBLE);

        String[] lines = diff.split("\n");
        int additions = 0;
        int deletions = 0;

        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                additions++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                deletions++;
            }
        }

        // Show diff statistics badge in the subtitle if present
        if (additions > 0 || deletions > 0) {
            String stats = "+" + additions + " -" + deletions;
            if (oldRef != null && newRef != null) {
                String shortOld = oldRef.replace("refs/heads/", "");
                String shortNew = newRef.replace("refs/remotes/", "");
                binding.tvDiffFilepath.setText(shortOld + " ➔ " + shortNew + " • " + stats + " • " + filePath);
            } else if (commitSha != null && !commitSha.trim().isEmpty()) {
                String shortSha = commitSha.length() > 7 ? commitSha.substring(0, 7) : commitSha;
                binding.tvDiffFilepath.setText(shortSha + " • " + stats + " • " + filePath);
            } else {
                binding.tvDiffFilepath.setText(stats + " • " + filePath);
            }
        }

        int i = 0;
        while (i < lines.length) {
            String removedLine = lines[i];

            // Heuristic for word-level diff: single deletion followed immediately by single addition
            if (removedLine.startsWith("-") && i + 1 < lines.length && lines[i + 1].startsWith("+")
                    && (i + 2 >= lines.length || (!lines[i + 2].startsWith("-") && !lines[i + 2].startsWith("+")))) {
                String addedLine = lines[i + 1];

                CharSequence[] spans = computeWordDiff(removedLine.substring(1), addedLine.substring(1));

                // Render removed line
                ItemDiffLineBinding removedBinding = ItemDiffLineBinding.inflate(getLayoutInflater(), binding.layoutDiffLines, false);
                SpannableStringBuilder ssbRemoved = new SpannableStringBuilder("-");
                ssbRemoved.append(spans[0]);
                removedBinding.tvLineContent.setText(ssbRemoved);
                removedBinding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.vcode_diff_removed_bg));
                removedBinding.tvLineContent.setTextColor(ContextCompat.getColor(this, R.color.vcode_diff_removed_text));
                binding.layoutDiffLines.addView(removedBinding.getRoot());

                // Render added line
                ItemDiffLineBinding addedBinding = ItemDiffLineBinding.inflate(getLayoutInflater(), binding.layoutDiffLines, false);
                SpannableStringBuilder ssbAdded = new SpannableStringBuilder("+");
                ssbAdded.append(spans[1]);
                addedBinding.tvLineContent.setText(ssbAdded);
                addedBinding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.vcode_diff_added_bg));
                addedBinding.tvLineContent.setTextColor(ContextCompat.getColor(this, R.color.vcode_diff_added_text));
                binding.layoutDiffLines.addView(addedBinding.getRoot());

                i += 2;
                continue;
            }

            ItemDiffLineBinding lineBinding = ItemDiffLineBinding.inflate(getLayoutInflater(), binding.layoutDiffLines, false);
            lineBinding.tvLineContent.setText(removedLine);

            if (removedLine.startsWith("+") && !removedLine.startsWith("+++")) {
                lineBinding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.vcode_diff_added_bg));
                lineBinding.tvLineContent.setTextColor(ContextCompat.getColor(this, R.color.vcode_diff_added_text));
            } else if (removedLine.startsWith("-") && !removedLine.startsWith("---")) {
                lineBinding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.vcode_diff_removed_bg));
                lineBinding.tvLineContent.setTextColor(ContextCompat.getColor(this, R.color.vcode_diff_removed_text));
            } else if (removedLine.startsWith("@@")) {
                lineBinding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.vcode_diff_hunk_bg));
                lineBinding.tvLineContent.setTextColor(ContextCompat.getColor(this, R.color.vcode_accent_primary));
            } else {
                lineBinding.tvLineContent.setTextColor(ContextCompat.getColor(this, R.color.vcode_text_secondary));
            }

            binding.layoutDiffLines.addView(lineBinding.getRoot());
            i++;
        }
    }

    /**
     * Computes word-level diffs using a prefix/suffix matching approach.
     */
    private CharSequence[] computeWordDiff(String removed, String added) {
        int prefixLength = 0;
        int minLength = Math.min(removed.length(), added.length());
        while (prefixLength < minLength && removed.charAt(prefixLength) == added.charAt(prefixLength)) {
            prefixLength++;
        }

        int suffixLength = 0;
        int maxSuffix = minLength - prefixLength;
        while (suffixLength < maxSuffix
                && removed.charAt(removed.length() - 1 - suffixLength) == added.charAt(added.length() - 1 - suffixLength)) {
            suffixLength++;
        }

        String removedDiff = removed.substring(prefixLength, removed.length() - suffixLength);
        String addedDiff = added.substring(prefixLength, added.length() - suffixLength);

        SpannableString spanRemoved = new SpannableString(removed);
        if (!removedDiff.isEmpty()) {
            spanRemoved.setSpan(new BackgroundColorSpan(
                            ContextCompat.getColor(this, R.color.vcode_diff_removed_word_bg)),
                    prefixLength, prefixLength + removedDiff.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        SpannableString spanAdded = new SpannableString(added);
        if (!addedDiff.isEmpty()) {
            spanAdded.setSpan(new BackgroundColorSpan(
                            ContextCompat.getColor(this, R.color.vcode_diff_added_word_bg)),
                    prefixLength, prefixLength + addedDiff.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return new CharSequence[]{spanRemoved, spanAdded};
    }
}

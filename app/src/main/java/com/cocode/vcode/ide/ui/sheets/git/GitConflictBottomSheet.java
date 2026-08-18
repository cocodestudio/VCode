package com.cocode.vcode.ide.ui.sheets.git;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetConflictResolutionBinding;
import com.cocode.vcode.ide.git.core.GitRepository;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;

import java.util.List;

public class GitConflictBottomSheet extends BaseBottomSheetDialogFragment {

    private static final String TAG = "GitConflictBottomSheet";
    private static final String KEY_FILES = "conflict_files";

    private BottomSheetConflictResolutionBinding binding;
    private List<String> conflictingFiles;
    private GitRepository repository;
    private Runnable onResolved;

    public static void show(FragmentManager fm, GitRepository repository,
                            List<String> conflictingFiles, Runnable onResolved) {
        GitConflictBottomSheet sheet = new GitConflictBottomSheet();
        Bundle args = new Bundle();
        args.putStringArrayList(KEY_FILES, new java.util.ArrayList<>(conflictingFiles));
        sheet.setArguments(args);
        sheet.repository = repository;
        sheet.onResolved = onResolved;
        sheet.show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            conflictingFiles = getArguments().getStringArrayList(KEY_FILES);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetConflictResolutionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyTypefaces();
        setupFilesList();
        setupListeners();
    }

    private void applyTypefaces() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();
        binding.tvConflictTitle.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvConflictDesc.setTypeface(fm.getUiMedium(ctx));
        binding.tvOursTitle.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvOursDesc.setTypeface(fm.getUiMedium(ctx));
        binding.tvTheirsTitle.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvTheirsDesc.setTypeface(fm.getUiMedium(ctx));
        binding.tvAbortTitle.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvAbortDesc.setTypeface(fm.getUiMedium(ctx));
    }

    private void setupFilesList() {
        binding.rvConflictFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvConflictFiles.setAdapter(new FilesAdapter(conflictingFiles));
    }

    private void setupListeners() {
        binding.optionAcceptOurs.setOnClickListener(v -> resolveWith(true));
        binding.optionAcceptTheirs.setOnClickListener(v -> resolveWith(false));
        binding.optionAbortMerge.setOnClickListener(v -> abortMerge());
    }

    private void resolveWith(boolean ours) {
        if (repository == null || conflictingFiles == null) {
            dismiss();
            return;
        }
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                for (String path : conflictingFiles) {
                    repository.checkoutConflictFile(path, ours);
                    repository.stageFile(path);
                }
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) Toast.makeText(requireContext(),
                            "Conflicts resolved using " + (ours ? "local" : "remote") + " version. Stage and commit to finish.",
                            Toast.LENGTH_LONG).show();
                    if (onResolved != null) onResolved.run();
                    dismiss();
                });
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) Toast.makeText(requireContext(),
                            "Failed to resolve: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void abortMerge() {
        if (repository == null) {
            dismiss();
            return;
        }
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                repository.hardReset("ORIG_HEAD");
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded())
                        Toast.makeText(requireContext(), R.string.vcode_merge_aborted_repository_restored_to, Toast.LENGTH_SHORT).show();
                    if (onResolved != null) onResolved.run();
                    dismiss();
                });
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) Toast.makeText(requireContext(),
                            "Failed to abort: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    private static class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.VH> {
        private final List<String> files;

        FilesAdapter(List<String> files) {
            this.files = files;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_conflict_file, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            h.tv.setText(files.get(pos));
        }

        @Override
        public int getItemCount() {
            return files != null ? files.size() : 0;
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;

            VH(@NonNull View v) {
                super(v);
                tv = v.findViewById(R.id.tv_conflict_file_name);
            }
        }
    }
}

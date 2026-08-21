package com.cocode.vcode.ide.ui.git.tabs;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.databinding.FragmentGitStashBinding;
import com.cocode.vcode.ide.git.adapters.StashAdapter;
import com.cocode.vcode.ide.git.model.StashItem;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.ui.sheets.files.DeleteBottomSheet;
import com.cocode.vcode.ide.ui.sheets.git.StashMessageBottomSheet;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Fragment displaying the list of stashed changes and stash management actions.
 */
public class GitStashFragment extends Fragment implements StashAdapter.StashListener {
    private FragmentGitStashBinding binding;
    private GitViewModel viewModel;
    private StashAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentGitStashBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(GitViewModel.class);

        adapter = new StashAdapter(this);
        binding.rvStashes.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvStashes.setAdapter(adapter);

        setupTypefaces();
        setupListeners();
        observeData();
    }

    private void setupTypefaces() {
        Context context = requireContext();
        FontManager fm = FontManager.getInstance();
        binding.tvStashTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvStashSubtitle.setTypeface(fm.getUiMedium(context));
        binding.btnCreateStash.setTypeface(fm.getUiSemiBold(context));
        binding.tvEmptyStashTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvEmptyStashDesc.setTypeface(fm.getUiMedium(context));
    }

    private void setupListeners() {
        binding.btnCreateStash.setOnClickListener(v -> {
            StashMessageBottomSheet.show(getChildFragmentManager(), message -> {
                if (message != null && !message.isEmpty()) {
                    viewModel.stashCreate(message);
                } else {
                    viewModel.stashCreate();
                }
            });
        });
    }

    private void observeData() {
        viewModel.getStashes().observe(getViewLifecycleOwner(), stashes -> {
            if (stashes != null) {
                adapter.submitList(stashes);
                if (stashes.isEmpty()) {
                    binding.rvStashes.setVisibility(View.GONE);
                    binding.layoutEmptyStashes.setVisibility(View.VISIBLE);
                } else {
                    binding.rvStashes.setVisibility(View.VISIBLE);
                    binding.layoutEmptyStashes.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onApply(StashItem item) {
        viewModel.stashApply(item.getId());
    }

    @Override
    public void onDrop(StashItem item) {
        DeleteBottomSheet.show(
                getChildFragmentManager(),
                DeleteBottomSheet.DeleteType.STASH,
                item.getName(),
                null,
                () -> viewModel.stashDrop(item.getId())
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

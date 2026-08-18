package com.cocode.vcode.ide.ui.sheets.editor;

import android.app.Dialog;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.SnippetItem;
import com.cocode.vcode.ide.data.repository.SnippetRepository;
import com.cocode.vcode.ide.databinding.BottomSheetSnippetManagerBinding;
import com.cocode.vcode.ide.ui.sheets.files.CreateSnippetBottomSheet;
import com.cocode.vcode.ide.ui.sheets.files.DeleteBottomSheet;
import com.cocode.vcode.ide.ui.snippets.SnippetsAdapter;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.MarginItemDecorator;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * SnippetsBottomSheet provides a management interface for code snippets.
 * It allows users to browse, search, create, edit, and delete snippets.
 * Tapping a snippet notifies the listener to insert the code into the active editor.
 */
public class SnippetsBottomSheet extends BaseBottomSheetDialogFragment {

    private BottomSheetSnippetManagerBinding binding;
    private SnippetsAdapter adapter;
    private SnippetRepository repository;

    /**
     * Full list of snippets retrieved from the repository for local filtering.
     */
    private List<SnippetItem> allSnippets = new ArrayList<>();
    private SnippetsInteractionListener interactionListener;

    /**
     * Attaches a listener to handle snippet insertion events.
     */
    public void setListener(SnippetsInteractionListener listener) {
        this.interactionListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        // Force full-height expansion to provide a comfortable list browsing experience
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) d;
            View bottomSheetInternal = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheetInternal != null) {
                bottomSheetInternal.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheetInternal.requestLayout();

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheetInternal);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSnippetManagerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new SnippetRepository(requireContext());

        // Apply visual styling
        UiUtils.setViewRounded(binding.searchBarLayout, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        setupTypefaces();
        setupRecyclerView();
        setupSearchFilter();
        setupClickListeners();

        // Initial data load
        loadSnippetsFromRepository();
    }

    /**
     * Retrieves the latest snippets from the repository and refreshes the UI.
     */
    private void loadSnippetsFromRepository() {
        repository.getSnippets().observe(getViewLifecycleOwner(), result -> {
            if (result != null && result.isSuccess()) {
                List<SnippetItem> fetched = result.getData();
                allSnippets = new ArrayList<>();
                for (SnippetItem item : fetched) {
                    if (item.getId() != null && item.getId().startsWith("git_template_")) continue;
                    allSnippets.add(item);
                }

                // Apply existing search filter to the new data
                String currentQuery = binding.etSearch.getText() != null ? binding.etSearch.getText().toString() : "";
                filterSnippets(currentQuery);
            } else if (result != null && !result.isSuccess()) {
                Toast.makeText(requireContext(), result.getError(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Applies branding fonts to textual components.
     */
    private void setupTypefaces() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.etSearch.setTypeface(fm.getUiMedium(ctx));
        binding.tvTitle.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvEmptyTitle.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvEmptySubtitle.setTypeface(fm.getUiMedium(ctx));
    }

    /**
     * Initializes the RecyclerView with the SnippetsAdapter and custom interactions.
     */
    private void setupRecyclerView() {
        adapter = new SnippetsAdapter(new SnippetsAdapter.SnippetListener() {
            @Override
            public void onSnippetClick(SnippetItem snippet) {
                if (interactionListener != null) {
                    interactionListener.onInsertSnippet(snippet);
                }
                dismiss();
            }

            @Override
            public void onSnippetEditClick(SnippetItem snippet) {
                // Launch the snippet editor for the selected item
                CreateSnippetBottomSheet editSheet = new CreateSnippetBottomSheet();
                editSheet.setExistingSnippet(snippet);
                editSheet.setListener((updatedSnippet, isEdit) -> repository.updateSnippet(updatedSnippet).observe(getViewLifecycleOwner(), result -> {
                    if (result != null && result.isSuccess()) {
                        loadSnippetsFromRepository();
                    }
                }));
                editSheet.show(getChildFragmentManager(), "EditSnippet");
            }

            @Override
            public void onSnippetDeleteClick(SnippetItem snippet) {
                // Confirm and execute snippet deletion
                DeleteBottomSheet.show(getChildFragmentManager(),
                        DeleteBottomSheet.DeleteType.SNIPPET,
                        snippet.getTitle(),
                        null,
                        () -> repository.deleteSnippet(snippet.getId()).observe(getViewLifecycleOwner(), result -> {
                            if (result != null && result.isSuccess()) {
                                loadSnippetsFromRepository();
                            }
                        }));
            }
        });

        binding.rvSnippets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSnippets.setAdapter(adapter);

        // Apply standard list spacing
        binding.rvSnippets.addItemDecoration(new MarginItemDecorator(UiUtils.dpToPx(requireContext(), 24), UiUtils.dpToPx(requireContext(), 24), UiUtils.dpToPx(requireContext(), 12)));

        // Auto-close open swipe menus during scroll
        binding.rvSnippets.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    adapter.closeSwipedItem();
                }
            }
        });
    }

    /**
     * Connects a TextWatcher to the search input for real-time filtering.
     */
    private void setupSearchFilter() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSnippets(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * Initializes listeners for sheet-level actions like adding a new snippet.
     */
    private void setupClickListeners() {
        binding.btnAddSnippet.setOnClickListener(v -> {
            CreateSnippetBottomSheet createSheet = new CreateSnippetBottomSheet();
            createSheet.setListener((newSnippet, isEdit) -> repository.saveSnippet(newSnippet).observe(getViewLifecycleOwner(), result -> {
                if (result != null && result.isSuccess()) {
                    loadSnippetsFromRepository();
                }
            }));
            // Use child fragment manager to maintain lifecycle integrity
            createSheet.show(getChildFragmentManager(), "CreateSnippet");
        });
    }

    /**
     * Filters the snippet list locally based on title or content matches.
     *
     * @param query The search text.
     */
    private void filterSnippets(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.setSnippets(allSnippets);
            updateEmptyState(allSnippets.isEmpty());
            return;
        }

        String lowerCaseQuery = query.toLowerCase().trim();
        List<SnippetItem> filteredList = new ArrayList<>();

        for (SnippetItem item : allSnippets) {
            boolean matchesTitle = item.getTitle() != null && item.getTitle().toLowerCase().contains(lowerCaseQuery);
            boolean matchesContent = item.getContent() != null && item.getContent().toLowerCase().contains(lowerCaseQuery);

            if (matchesTitle || matchesContent) {
                filteredList.add(item);
            }
        }

        adapter.setSnippets(filteredList);
        updateEmptyState(filteredList.isEmpty());
    }

    /**
     * Toggles visibility between the snippet list and the empty state placeholder.
     */
    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            binding.rvSnippets.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            binding.rvSnippets.setVisibility(View.VISIBLE);
            binding.layoutEmptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Callback interface for snippet selection.
     */
    public interface SnippetsInteractionListener {
        void onInsertSnippet(SnippetItem snippet);
    }
}
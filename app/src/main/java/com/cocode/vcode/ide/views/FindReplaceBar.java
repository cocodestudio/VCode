package com.cocode.vcode.ide.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.editor.search.SearchEngine;
import com.cocode.vcode.ide.core.model.SearchResult;
import com.cocode.vcode.ide.databinding.ViewFindReplaceBinding;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * In-editor find and replace bar component.
 * Supports case-sensitive matching, whole-word matching, regular expressions,
 * debounced background searches, and batch text replacement.
 */
public class FindReplaceBar extends LinearLayout {

    private static final long DEBOUNCE_MS = 300;

    private final SearchEngine searchEngine = new SearchEngine();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());

    private final ViewFindReplaceBinding binding;
    private CodeEditText editor;
    private List<SearchResult> results = new ArrayList<>();
    private int currentIndex = -1;
    private int activeSearchId = 0;
    private boolean caseSensitive = false;
    private boolean wholeWord = false;
    private boolean useRegex = false;

    public FindReplaceBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        binding = ViewFindReplaceBinding.inflate(LayoutInflater.from(context), this, true);

        setupTypefaces(context);
        setupListeners();
    }

    private void setupTypefaces(Context context) {
        binding.etSearch.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.etReplace.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.btnToggleCase.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.btnToggleRegex.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.btnToggleWord.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.tvMatchCount.setTypeface(FontManager.getInstance().getUiFont(context));
        binding.btnReplace.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.btnReplaceAll.setTypeface(FontManager.getInstance().getUiSemiBold(context));
    }

    private void setupListeners() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch();
            }
        });

        binding.btnPrev.setOnClickListener(v -> navigatePrev());
        binding.btnNext.setOnClickListener(v -> navigateNext());
        binding.btnClose.setOnClickListener(v -> slideUp());
        binding.btnReplace.setOnClickListener(v -> replace());
        binding.btnReplaceAll.setOnClickListener(v -> replaceAll());

        binding.btnToggleCase.setOnClickListener(v -> {
            caseSensitive = !caseSensitive;
            updateToggle(binding.btnToggleCase, caseSensitive);
            scheduleSearch();
        });

        binding.btnToggleWord.setOnClickListener(v -> {
            wholeWord = !wholeWord;
            updateToggle(binding.btnToggleWord, wholeWord);
            scheduleSearch();
        });

        binding.btnToggleRegex.setOnClickListener(v -> {
            useRegex = !useRegex;
            updateToggle(binding.btnToggleRegex, useRegex);
            scheduleSearch();
        });
    }

    public void setEditor(CodeEditText editor) {
        this.editor = editor;
    }

    public void navigateNext() {
        if (results.isEmpty()) return;
        currentIndex = (currentIndex + 1) % results.size();
        scrollToCurrentResult();
    }

    public void navigatePrev() {
        if (results.isEmpty()) return;
        currentIndex = (currentIndex - 1 + results.size()) % results.size();
        scrollToCurrentResult();
    }

    /**
     * Replaces the currently highlighted search match with the replacement text.
     */
    public void replace() {
        if (editor == null || results.isEmpty() || currentIndex < 0) return;
        SearchResult cur = results.get(currentIndex);
        String replacement = binding.etReplace.getText().toString();

        editor.replaceRange(cur.absoluteStart, cur.absoluteEnd, replacement);
        scheduleSearch();
    }

    /**
     * Replaces all matches in the editor with the replacement text.
     * Iterates backwards so earlier offsets remain valid during replacements.
     */
    public void replaceAll() {
        if (editor == null || results.isEmpty()) return;
        String replacement = binding.etReplace.getText().toString();

        for (int i = results.size() - 1; i >= 0; i--) {
            SearchResult r = results.get(i);
            editor.replaceRange(r.absoluteStart, r.absoluteEnd, replacement);
        }
        scheduleSearch();
    }

    public void slideDown() {
        if (getVisibility() == VISIBLE) return;

        if (getParent() instanceof ViewGroup) {
            android.transition.AutoTransition transition = new android.transition.AutoTransition();
            transition.setDuration(200);
            transition.setInterpolator(new android.view.animation.DecelerateInterpolator());
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) getParent(), transition);
        }

        setVisibility(VISIBLE);
        binding.etSearch.requestFocus();
    }

    public void slideUp() {
        if (getVisibility() == GONE) return;

        if (getParent() instanceof ViewGroup) {
            android.transition.AutoTransition transition = new android.transition.AutoTransition();
            transition.setDuration(200);
            transition.setInterpolator(new android.view.animation.AccelerateInterpolator());
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) getParent(), transition);
        }

        setVisibility(GONE);
        clearHighlights();
    }

    private void scheduleSearch() {
        debounceHandler.removeCallbacksAndMessages(null);
        debounceHandler.postDelayed(this::runSearch, DEBOUNCE_MS);
    }

    /**
     * Executes the search asynchronously on a CPU thread and updates decorations on the main thread.
     */
    private void runSearch() {
        if (editor == null) return;
        String query = binding.etSearch.getText().toString();
        String text = Objects.requireNonNull(editor.getText()).toString();

        if (query.isEmpty()) {
            clearHighlights();
            results.clear();
            currentIndex = -1;
            binding.tvMatchCount.setText("");
            return;
        }

        final int searchId = ++activeSearchId;

        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<SearchResult> found = searchEngine.find(query, text, caseSensitive, useRegex, wholeWord);

            ExecutorProvider.getInstance().runOnMain(() -> {
                if (searchId != activeSearchId) return;

                results = found;
                currentIndex = found.isEmpty() ? -1 : 0;
                applyHighlights();
                updateMatchCountLabel();
                if (!found.isEmpty()) scrollToCurrentResult();
            });
        });
    }

    private void applyHighlights() {
        if (editor == null) return;
        // Delegate to setSearchDecorations() on CodeEditText for custom painting in onDraw().
        editor.setSearchDecorations(results, currentIndex);
    }

    private void clearHighlights() {
        if (editor == null) return;
        editor.clearSearchDecorations();
    }

    /**
     * Scrolls the editor viewport to bring the current search match into view and selects it.
     */
    private void scrollToCurrentResult() {
        if (editor == null || currentIndex < 0 || currentIndex >= results.size()) return;
        SearchResult r = results.get(currentIndex);
        editor.setSelection(r.absoluteEnd);
        editor.scrollToOffset(r.absoluteStart);
        applyHighlights();
        updateMatchCountLabel();
    }

    private void updateMatchCountLabel() {
        binding.tvMatchCount.setText(results.isEmpty()
                ? (binding.etSearch.getText().length() > 0 ? "0/0" : "")
                : ((currentIndex + 1) + "/" + results.size()));
    }

    private void updateToggle(MaterialButton btn, boolean active) {
        int color = ContextCompat.getColor(getContext(), active ? R.color.vcode_accent_primary : R.color.vcode_text_secondary);
        btn.setTextColor(color);
        btn.setStrokeColor(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(getContext(), active ? R.color.vcode_accent_primary : R.color.vcode_divider)));
    }

}
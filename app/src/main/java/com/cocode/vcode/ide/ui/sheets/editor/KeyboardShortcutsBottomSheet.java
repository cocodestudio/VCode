package com.cocode.vcode.ide.ui.sheets.editor;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.core.keybinding.KeyCommand;
import com.cocode.vcode.ide.core.keybinding.KeybindingManager;
import com.cocode.vcode.ide.databinding.BottomSheetKeyboardShortcutsBinding;
import com.cocode.vcode.ide.ui.settings.KeyboardSettingsAdapter;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

/**
 * Fast, searchable bottom sheet for discovering and referencing keyboard shortcuts during coding.
 */
public class KeyboardShortcutsBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "KeyboardShortcutsBottomSheet";
    private BottomSheetKeyboardShortcutsBinding binding;
    private KeyboardSettingsAdapter adapter;

    public static void show(@NonNull FragmentManager fragmentManager) {
        KeyboardShortcutsBottomSheet sheet = new KeyboardShortcutsBottomSheet();
        sheet.show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetKeyboardShortcutsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupDesign();
        setupRecyclerView();
        setupListeners();
    }

    private void setupDesign() {
        FontManager fm = FontManager.getInstance();
        binding.tvTitle.setTypeface(fm.getUiSemiBold(requireContext()));
        binding.etSearch.setTypeface(fm.getUiFont(requireContext()));
        binding.tvEmpty.setTypeface(fm.getUiMedium(requireContext()));
    }

    private void setupRecyclerView() {
        List<KeyCommand> commands = KeybindingManager.getInstance(requireContext()).getAllCommands();
        // In the read-only cheat sheet, clicking an item does not open editor dialog
        adapter = new KeyboardSettingsAdapter(requireContext(), commands, null);

        binding.rvShortcuts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvShortcuts.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> dismiss());

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                binding.btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                if (adapter != null) {
                    adapter.filter(query);
                    binding.layoutEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnClearSearch.setOnClickListener(v -> binding.etSearch.setText(""));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

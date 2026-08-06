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
import com.cocode.vcode.ide.databinding.BottomSheetStashMessageBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Objects;

public class StashMessageBottomSheet extends BottomSheetDialogFragment {
    private BottomSheetStashMessageBinding binding;
    private StashMessageListener listener;

    public static void show(FragmentManager manager, StashMessageListener listener) {
        StashMessageBottomSheet sheet = new StashMessageBottomSheet();
        sheet.setListener(listener);
        sheet.show(manager, "StashMessageBottomSheet");
    }

    public void setListener(StashMessageListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetStashMessageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        FontManager fm = FontManager.getInstance();
        binding.tvStashTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvStashSubtitle.setTypeface(fm.getUiMedium(context));
        binding.etStashMessage.setTypeface(fm.getUiMedium(context));
        binding.btnStash.setTypeface(fm.getUiSemiBold(context));

        UiUtils.setViewRounded(binding.etStashMessage, UiUtils.dpToPx(context, 10), ContextCompat.getColor(context, R.color.vcode_bg_elevated));

        binding.btnStash.setOnClickListener(v -> {
            String message = Objects.requireNonNull(binding.etStashMessage.getText()).toString().trim();
            if (listener != null) {
                listener.onStash(message);
            }
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public interface StashMessageListener {
        void onStash(String message);
    }
}

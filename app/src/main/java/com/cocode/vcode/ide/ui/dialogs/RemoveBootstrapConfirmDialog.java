package com.cocode.vcode.ide.ui.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.DialogRemoveBootstrapConfirmBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Custom confirmation dialog shown when the user toggles off the Desktop Linux Environment.
 * Informs the user that userland binaries and files (~140 MB) will be deleted to reclaim storage.
 */
public final class RemoveBootstrapConfirmDialog {

    public interface RemoveBootstrapListener {
        void onConfirmed();
        void onCancelled();
    }

    private RemoveBootstrapConfirmDialog() {
    }

    /**
     * Displays the custom removal confirmation dialog.
     *
     * @param context  the Android context
     * @param listener callback invoked on user confirmation or cancellation
     */
    public static void show(@NonNull Context context, @Nullable RemoveBootstrapListener listener) {
        DialogRemoveBootstrapConfirmBinding binding =
                DialogRemoveBootstrapConfirmBinding.inflate(LayoutInflater.from(context));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(binding.getRoot())
                .setCancelable(true)
                .create();

        FontManager fm = FontManager.getInstance();
        binding.tvRemoveTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvRemoveDesc.setTypeface(fm.getUiMedium(context));
        binding.btnCancelRemove.setTypeface(fm.getUiSemiBold(context));
        binding.btnConfirmRemove.setTypeface(fm.getUiSemiBold(context));

        binding.btnCancelRemove.setOnClickListener(v -> dialog.dismiss());

        binding.btnConfirmRemove.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onConfirmed();
            }
        });

        dialog.setOnDismissListener(d -> {
            // If dismissed without clicking confirm, notify cancel
        });

        dialog.setOnCancelListener(d -> {
            if (listener != null) {
                listener.onCancelled();
            }
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
            int targetWidth = Math.min((int) (screenWidth * 0.92f), maxWidth);
            dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}

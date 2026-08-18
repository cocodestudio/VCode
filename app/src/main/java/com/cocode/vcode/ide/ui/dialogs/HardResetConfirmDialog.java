package com.cocode.vcode.ide.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.button.MaterialButton;

/**
 * HardResetConfirmDialog provides a critical warning interface before performing a Git hard reset.
 * This operation is destructive and cannot be undone, so this specialized dialog ensures
 * the user explicitly confirms their intent.
 */
public class HardResetConfirmDialog {

    /**
     * Inflates and displays the hard reset confirmation dialog.
     *
     * @param context  The context used to inflate the layout and build the dialog.
     * @param listener Callback to execute when the user confirms the reset.
     */
    public static void show(Context context, HardResetConfirmationListener listener) {
        // Inflate custom dialog layout for precise control over styling
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_hard_reset_confirm, null);

        // Build the Material dialog container
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(true)
                .create();

        // Resolve view components from the inflated layout
        TextView tvTitle = view.findViewById(R.id.tv_reset_title);
        TextView tvDesc = view.findViewById(R.id.tv_reset_desc);
        MaterialButton btnCancel = view.findViewById(R.id.btn_cancel_hard_reset);
        MaterialButton btnConfirm = view.findViewById(R.id.btn_confirm_hard_reset);

        // Apply specialized UI fonts for a consistent look and feel
        tvTitle.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        tvDesc.setTypeface(FontManager.getInstance().getUiMedium(context));
        btnCancel.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        btnConfirm.setTypeface(FontManager.getInstance().getUiSemiBold(context));

        // Wire up cancellation logic
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Wire up confirmation logic; dismisses the dialog and notifies the listener
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onResetConfirmed();
            }
        });

        dialog.show();

        // Ensure the dialog window has a transparent background to respect the layout's rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
            int targetWidth = Math.min((int) (screenWidth * 0.92f), maxWidth);
            dialog.getWindow().setLayout(targetWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * Listener interface for capturing the user's confirmation of the hard reset.
     */
    public interface HardResetConfirmationListener {
        void onResetConfirmed();
    }
}
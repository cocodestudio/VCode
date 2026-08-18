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
 * MergeConfirmDialog facilitates the confirmation step for Git branch merging.
 * It presents a clear description of the source and target branches involved
 * in the operation to prevent accidental merges.
 */
public class MergeConfirmDialog {

    /**
     * Displays the merge confirmation dialog with branch details.
     *
     * @param context      The context for building the dialog.
     * @param sourceBranch The name of the branch to merge from.
     * @param targetBranch The name of the active branch being merged into.
     * @param listener     Callback for the confirmation event.
     */
    public static void show(Context context, String sourceBranch, String targetBranch, MergeConfirmationListener listener) {
        // Inflate the custom layout for the merge confirmation
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_merge_confirm, null);

        // Build the Material Design themed dialog
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(true)
                .create();

        TextView tvTitle = view.findViewById(R.id.tv_merge_title);
        TextView tvDesc = view.findViewById(R.id.tv_merge_desc);
        MaterialButton btnCancel = view.findViewById(R.id.btn_cancel_merge);
        MaterialButton btnConfirm = view.findViewById(R.id.btn_confirm_merge);

        // Apply branding fonts
        tvTitle.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        tvDesc.setTypeface(FontManager.getInstance().getUiMedium(context));
        btnCancel.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        btnConfirm.setTypeface(FontManager.getInstance().getUiSemiBold(context));

        // Dynamically construct the confirmation message with branch identifiers
        String trackingMessage = "Are you sure you want to merge branch \"" + sourceBranch + "\" into \"" + targetBranch + "\"? This action cannot be undone.";
        tvDesc.setText(trackingMessage);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onMergeConfirmed();
            }
        });

        dialog.show();

        // Standard transparency injection for custom rounded window support
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
            int targetWidth = Math.min((int) (screenWidth * 0.92f), maxWidth);
            dialog.getWindow().setLayout(targetWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * Listener interface for notifying when the user confirms the merge.
     */
    public interface MergeConfirmationListener {
        void onMergeConfirmed();
    }
}
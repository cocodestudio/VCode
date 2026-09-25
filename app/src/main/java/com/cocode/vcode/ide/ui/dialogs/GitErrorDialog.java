package com.cocode.vcode.ide.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.DialogGitErrorBinding;
import com.cocode.vcode.ide.git.core.GitRepository;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Custom Material 3 dialog for displaying Git errors, checkout conflicts,
 * and authentication/connection failures with consistent IDE aesthetics.
 */
public class GitErrorDialog {

    public interface OnActionClickListener {
        void onAction();
    }

    /**
     * Displays a basic Git error dialog with a single Dismiss button.
     */
    public static void show(Context context, String title, String message) {
        show(context, R.drawable.ic_triangle_exclamation, title, message, null, null, null);
    }

    /**
     * Displays a customized Git error dialog with optional positive and negative actions.
     */
    public static void show(Context context,
                            @DrawableRes int iconResId,
                            String title,
                            String message,
                            @Nullable String positiveBtnText,
                            @Nullable OnActionClickListener onPositive,
                            @Nullable String negativeBtnText) {
        if (context == null) return;

        DialogGitErrorBinding binding = DialogGitErrorBinding.inflate(LayoutInflater.from(context));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(binding.getRoot())
                .setCancelable(true)
                .create();

        FontManager fm = FontManager.getInstance();
        binding.tvErrorTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvErrorDesc.setTypeface(fm.getUiMedium(context));
        binding.btnErrorNegative.setTypeface(fm.getUiSemiBold(context));
        binding.btnErrorPositive.setTypeface(fm.getUiSemiBold(context));

        binding.ivErrorIcon.setImageResource(iconResId);
        binding.tvErrorTitle.setText(title);
        binding.tvErrorDesc.setText(message);

        if (positiveBtnText != null && !positiveBtnText.isEmpty()) {
            binding.btnErrorPositive.setText(positiveBtnText);
        } else {
            binding.btnErrorPositive.setText(R.string.vcode_dismiss);
        }

        binding.btnErrorPositive.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPositive != null) {
                onPositive.onAction();
            }
        });

        if (negativeBtnText != null && !negativeBtnText.isEmpty()) {
            binding.btnErrorNegative.setVisibility(View.VISIBLE);
            binding.btnErrorNegative.setText(negativeBtnText);
            binding.btnErrorNegative.setOnClickListener(v -> dialog.dismiss());
        } else {
            binding.btnErrorNegative.setVisibility(View.GONE);
        }

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
            int targetWidth = Math.min((int) (screenWidth * 0.92f), maxWidth);
            dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * Displays a checkout conflict dialog informing the user which files have uncommitted
     * local modifications and providing an action to navigate to the Changes tab.
     */
    public static void showCheckoutConflict(Context context,
                                           @NonNull List<String> conflictingFiles,
                                           @Nullable Runnable onViewChanges) {
        if (context == null) return;

        StringBuilder sb = new StringBuilder();
        for (String file : conflictingFiles) {
            if (file != null && !file.trim().isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("• ").append(file.trim());
            }
        }
        String filesList = sb.length() > 0 ? sb.toString() : "index.html";
        String message = context.getString(R.string.vcode_git_checkout_conflict_desc, filesList);
        String title = context.getString(R.string.vcode_git_checkout_conflict_title);

        show(context,
                R.drawable.ic_triangle_exclamation,
                title,
                message,
                context.getString(R.string.vcode_view_changes),
                () -> {
                    if (onViewChanges != null) {
                        onViewChanges.run();
                    }
                },
                context.getString(R.string.vcode_action_cancel));
    }

    /**
     * Intelligent analyzer that inspects the exception and presents the corresponding custom dialog.
     */
    public static void showOperationalError(Context context,
                                            String operation,
                                            Throwable error,
                                            @Nullable Runnable onViewChanges) {
        if (context == null || error == null) return;

        String rawMsg = error.getMessage() != null ? error.getMessage() : "";
        String lowerMsg = rawMsg.toLowerCase();

        // 1. Checkout Conflict Detection
        if (error instanceof GitRepository.GitCheckoutConflictException) {
            List<String> files = ((GitRepository.GitCheckoutConflictException) error).getConflictingFiles();
            showCheckoutConflict(context, files, onViewChanges);
            return;
        }

        if (lowerMsg.contains("checkout conflict") || lowerMsg.contains("checkout conflicts")) {
            List<String> files = extractConflictingFilesFromMessage(rawMsg);
            showCheckoutConflict(context, files, onViewChanges);
            return;
        }

        // 2. Authentication Error Detection
        if (lowerMsg.contains("not authorized") || lowerMsg.contains("authentication")
                || lowerMsg.contains("401") || lowerMsg.contains("credentials")
                || lowerMsg.contains("auth fail") || lowerMsg.contains("permission denied")) {
            show(context,
                    R.drawable.ic_lock,
                    context.getString(R.string.vcode_git_auth_failed_title),
                    context.getString(R.string.vcode_git_auth_failed_desc),
                    context.getString(R.string.vcode_dismiss),
                    null,
                    null);
            return;
        }

        // 3. Network & Connection Error Detection
        if (lowerMsg.contains("cannot open git-upload-pack") || lowerMsg.contains("connection")
                || lowerMsg.contains("connect") || lowerMsg.contains("timeout")
                || lowerMsg.contains("network") || lowerMsg.contains("host")
                || lowerMsg.contains("offline") || lowerMsg.contains("unreachable")) {
            show(context,
                    R.drawable.ic_globe,
                    context.getString(R.string.vcode_git_connection_failed_title),
                    context.getString(R.string.vcode_git_connection_failed_desc),
                    context.getString(R.string.vcode_dismiss),
                    null,
                    null);
            return;
        }

        // 4. General Operational Error
        String title;
        if ("pull".equalsIgnoreCase(operation)) {
            title = context.getString(R.string.vcode_git_pull_failed_title);
        } else if ("push".equalsIgnoreCase(operation)) {
            title = context.getString(R.string.vcode_git_push_failed_title);
        } else if ("fetch".equalsIgnoreCase(operation)) {
            title = context.getString(R.string.vcode_git_fetch_failed_title);
        } else {
            title = context.getString(R.string.vcode_git_operation_error_title);
        }

        String cleanMsg = rawMsg.replace("Operational Error: ", "").trim();
        if (cleanMsg.isEmpty()) {
            cleanMsg = context.getString(R.string.vcode_git_operation_error_generic);
        }

        show(context,
                R.drawable.ic_triangle_exclamation,
                title,
                cleanMsg,
                context.getString(R.string.vcode_dismiss),
                null,
                null);
    }

    private static List<String> extractConflictingFilesFromMessage(String msg) {
        List<String> files = new ArrayList<>();
        if (msg == null) return files;

        int colonIdx = msg.indexOf(':');
        if (colonIdx != -1 && colonIdx + 1 < msg.length()) {
            String filesPart = msg.substring(colonIdx + 1).trim();
            String[] parts = filesPart.split("[,\n]+");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    files.add(trimmed);
                }
            }
        }
        return files;
    }
}

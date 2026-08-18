package com.cocode.vcode.ide.ui.sheets.git;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.git.core.SshKeyManager;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;


public class SshKeyBottomSheet extends BaseBottomSheetDialogFragment {

    private TextView tvPublicKey;

    public static SshKeyBottomSheet newInstance() {
        return new SshKeyBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_ssh_key, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle = view.findViewById(R.id.tv_title);
        TextView tvDesc = view.findViewById(R.id.tv_desc);
        tvPublicKey = view.findViewById(R.id.tv_public_key);
        com.google.android.material.button.MaterialButton btnClose = view.findViewById(R.id.btn_close);
        com.google.android.material.button.MaterialButton btnCopy = view.findViewById(R.id.btn_copy);

        FontManager fm = FontManager.getInstance();
        tvTitle.setTypeface(fm.getUiSemiBold(requireContext()));
        tvDesc.setTypeface(fm.getUiMedium(requireContext()));
        tvPublicKey.setTypeface(fm.getCodeFont(requireContext()));
        btnClose.setTypeface(fm.getUiSemiBold(requireContext()));
        btnCopy.setTypeface(fm.getUiSemiBold(requireContext()));

        btnClose.setOnClickListener(v -> dismiss());
        btnCopy.setOnClickListener(v -> copyKeyToClipboard());

        loadOrGenerateKey();
    }

    private void loadOrGenerateKey() {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                Context ctx = requireContext();
                if (!SshKeyManager.hasKeys(ctx)) {
                    SshKeyManager.generateKeys(ctx);
                }
                String pubKey = SshKeyManager.readPublicKey(ctx);
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (tvPublicKey != null) {
                        tvPublicKey.setText(pubKey);
                    }
                });
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (tvPublicKey != null) {
                        tvPublicKey.setText("Failed to load or generate SSH key: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void copyKeyToClipboard() {
        String key = tvPublicKey.getText().toString();
        if (key.isEmpty() || key.startsWith("Failed") || key.equals("Generating...")) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("SSH Public Key", key);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), R.string.vcode_ssh_key_copied_to_clipboard, Toast.LENGTH_SHORT).show();
            dismiss();
        }
    }
}

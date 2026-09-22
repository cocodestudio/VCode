package com.cocode.vcode.ide.ui.settings;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.keybinding.KeyCommand;
import com.cocode.vcode.ide.core.keybinding.KeyStroke;
import com.cocode.vcode.ide.core.keybinding.KeybindingManager;
import com.cocode.vcode.ide.core.keybinding.KeycapViewHelper;
import com.cocode.vcode.ide.databinding.DialogEditKeybindingBinding;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Interactive dialog allowing developers to record physical keyboard shortcuts in real-time.
 */
public class EditKeybindingDialog extends Dialog {

    public interface OnKeybindingSavedListener {
        void onKeybindingSaved();
    }

    private final KeyCommand command;
    private final OnKeybindingSavedListener savedListener;
    private final KeybindingManager keybindingManager;
    private DialogEditKeybindingBinding binding;
    private KeyStroke recordedStroke;

    public EditKeybindingDialog(@NonNull Context context,
                               @NonNull KeyCommand command,
                               @Nullable OnKeybindingSavedListener savedListener) {
        super(context);
        this.command = command;
        this.savedListener = savedListener;
        this.keybindingManager = KeybindingManager.getInstance(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        binding = DialogEditKeybindingBinding.inflate(LayoutInflater.from(getContext()));
        setContentView(binding.getRoot());

        if (getWindow() != null) {
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        setupDesign();
        setupInitialState();
        setupListeners();
    }

    private void setupDesign() {
        FontManager fm = FontManager.getInstance();
        binding.tvDialogTitle.setTypeface(fm.getUiSemiBold(getContext()));
        binding.tvDialogSubtitle.setTypeface(fm.getUiFont(getContext()));
        binding.tvRecordingHint.setTypeface(fm.getUiFont(getContext()));
        binding.tvConflictWarning.setTypeface(fm.getUiMedium(getContext()));
        binding.btnUnbind.setTypeface(fm.getUiMedium(getContext()));
        binding.btnCancel.setTypeface(fm.getUiMedium(getContext()));
        binding.btnSave.setTypeface(fm.getUiSemiBold(getContext()));
    }

    private void setupInitialState() {
        String title = getContext().getString(R.string.vcode_change_keybinding_title)
                + ": " + getContext().getString(command.getTitleResId());
        binding.tvDialogTitle.setText(title);

        recordedStroke = keybindingManager.getKeyStroke(command);
        if (recordedStroke != null) {
            binding.tvRecordingHint.setVisibility(View.GONE);
            binding.containerRecordedKeycaps.setVisibility(View.VISIBLE);
            KeycapViewHelper.populateKeycaps(binding.containerRecordedKeycaps, recordedStroke);
        } else {
            binding.tvRecordingHint.setVisibility(View.VISIBLE);
            binding.containerRecordedKeycaps.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        binding.btnCancel.setOnClickListener(v -> dismiss());

        binding.btnUnbind.setOnClickListener(v -> {
            keybindingManager.setKeybinding(command, null);
            if (savedListener != null) {
                savedListener.onKeybindingSaved();
            }
            dismiss();
        });

        binding.btnSave.setOnClickListener(v -> {
            if (recordedStroke != null) {
                keybindingManager.setKeybinding(command, recordedStroke);
                if (savedListener != null) {
                    savedListener.onKeybindingSaved();
                }
            }
            dismiss();
        });

        // Request focus on the key capture card to receive hardware key events immediately
        binding.cardKeyRecorder.setFocusable(true);
        binding.cardKeyRecorder.setFocusableInTouchMode(true);
        binding.cardKeyRecorder.requestFocus();

        binding.cardKeyRecorder.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return false;
            }

            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                dismiss();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_ENTER && recordedStroke != null) {
                keybindingManager.setKeybinding(command, recordedStroke);
                if (savedListener != null) {
                    savedListener.onKeybindingSaved();
                }
                dismiss();
                return true;
            }

            if (KeyStroke.isModifierKeyCode(keyCode)) {
                return false;
            }

            KeyStroke stroke = KeyStroke.fromKeyEvent(event);
            if (stroke != null) {
                recordedStroke = stroke;
                binding.tvRecordingHint.setVisibility(View.GONE);
                binding.containerRecordedKeycaps.setVisibility(View.VISIBLE);
                KeycapViewHelper.populateKeycaps(binding.containerRecordedKeycaps, recordedStroke);

                KeyCommand conflict = keybindingManager.findConflictingCommand(command, recordedStroke);
                if (conflict != null) {
                    String conflictTitle = getContext().getString(conflict.getTitleResId());
                    binding.tvConflictWarning.setText(getContext().getString(R.string.vcode_key_conflict_warning, conflictTitle));
                    binding.tvConflictWarning.setVisibility(View.VISIBLE);
                } else {
                    binding.tvConflictWarning.setVisibility(View.GONE);
                }
                return true;
            }

            return false;
        });
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event);
        }
        // Forward key events to the recorder card if not already handled
        if (binding != null && binding.cardKeyRecorder.dispatchKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }
}

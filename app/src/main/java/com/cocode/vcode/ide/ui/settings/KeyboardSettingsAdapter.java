package com.cocode.vcode.ide.ui.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.core.keybinding.KeyCommand;
import com.cocode.vcode.ide.core.keybinding.KeyStroke;
import com.cocode.vcode.ide.core.keybinding.KeybindingManager;
import com.cocode.vcode.ide.core.keybinding.KeycapViewHelper;
import com.cocode.vcode.ide.databinding.ItemKeybindingBinding;
import com.cocode.vcode.ide.utils.FontManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying and filtering keyboard shortcuts in Settings.
 */
public class KeyboardSettingsAdapter extends RecyclerView.Adapter<KeyboardSettingsAdapter.KeybindingViewHolder> {

    public interface OnKeybindingClickListener {
        void onKeybindingClick(@NonNull KeyCommand command);
    }

    private final Context context;
    private final KeybindingManager keybindingManager;
    private final List<KeyCommand> allCommands;
    private final List<KeyCommand> filteredCommands = new ArrayList<>();
    private final OnKeybindingClickListener listener;
    private String currentQuery = "";

    public KeyboardSettingsAdapter(@NonNull Context context,
                                   @NonNull List<KeyCommand> commands,
                                   @Nullable OnKeybindingClickListener listener) {
        this.context = context;
        this.keybindingManager = KeybindingManager.getInstance(context);
        this.allCommands = new ArrayList<>(commands);
        this.filteredCommands.addAll(allCommands);
        this.listener = listener;
    }

    @NonNull
    @Override
    public KeybindingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemKeybindingBinding binding = ItemKeybindingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new KeybindingViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull KeybindingViewHolder holder, int position) {
        KeyCommand command = filteredCommands.get(position);
        holder.bind(command);
    }

    @Override
    public int getItemCount() {
        return filteredCommands.size();
    }

    public void filter(@Nullable String query) {
        currentQuery = (query != null) ? query.trim().toLowerCase(Locale.getDefault()) : "";
        filteredCommands.clear();

        if (currentQuery.isEmpty()) {
            filteredCommands.addAll(allCommands);
        } else {
            for (KeyCommand cmd : allCommands) {
                String title = context.getString(cmd.getTitleResId()).toLowerCase(Locale.getDefault());
                String cmdId = cmd.getCommandId().toLowerCase(Locale.getDefault());
                KeyStroke stroke = keybindingManager.getKeyStroke(cmd);
                String keyStr = (stroke != null) ? stroke.toDisplayString().toLowerCase(Locale.getDefault()) : "";

                if (title.contains(currentQuery) || cmdId.contains(currentQuery) || keyStr.contains(currentQuery)) {
                    filteredCommands.add(cmd);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void refresh() {
        filter(currentQuery);
    }

    class KeybindingViewHolder extends RecyclerView.ViewHolder {
        private final ItemKeybindingBinding binding;

        KeybindingViewHolder(ItemKeybindingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.tvCommandTitle.setTypeface(FontManager.getInstance().getUiMedium(context));
            binding.tvCommandId.setTypeface(FontManager.getInstance().getUiFont(context));

            binding.getRoot().setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onKeybindingClick(filteredCommands.get(pos));
                }
            });
        }

        void bind(@NonNull KeyCommand command) {
            binding.tvCommandTitle.setText(command.getTitleResId());
            binding.tvCommandId.setText(command.getCommandId());

            KeyStroke stroke = keybindingManager.getKeyStroke(command);
            KeycapViewHelper.populateKeycaps(binding.containerKeycaps, stroke);
        }
    }
}

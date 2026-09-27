package com.cocode.vcode.ide.ui.sheets.editor;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.console.ConsoleLogEntry;
import com.cocode.vcode.ide.core.console.LiveConsoleManager;
import com.cocode.vcode.ide.databinding.BottomSheetConsoleBinding;
import com.cocode.vcode.ide.databinding.ItemConsoleLogBinding;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.cocode.vcode.ide.utils.FontManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Bottom sheet displaying live console logs and errors from web previews and scripts.
 */
public class ConsoleBottomSheet extends BaseBottomSheetDialogFragment {

    private BottomSheetConsoleBinding binding;
    private ConsoleAdapter adapter;
    private ConsoleLogEntry.Level filterLevel = null; // null means ALL

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetConsoleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FontManager fm = FontManager.getInstance();
        binding.tvTitle.setTypeface(fm.getUiSemiBold(requireContext()));
        binding.tvEmptyTitle.setTypeface(fm.getUiMedium(requireContext()));
        binding.tvEmptySubtitle.setTypeface(fm.getUiFont(requireContext()));

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        binding.rvLogs.setLayoutManager(layoutManager);
        adapter = new ConsoleAdapter(requireContext());
        binding.rvLogs.setAdapter(adapter);

        binding.btnClear.setOnClickListener(v -> LiveConsoleManager.getInstance().clearLogs());

        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                filterLevel = null;
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chip_log) {
                    filterLevel = ConsoleLogEntry.Level.LOG;
                } else if (id == R.id.chip_info) {
                    filterLevel = ConsoleLogEntry.Level.INFO;
                } else if (id == R.id.chip_warn) {
                    filterLevel = ConsoleLogEntry.Level.WARN;
                } else if (id == R.id.chip_error) {
                    filterLevel = ConsoleLogEntry.Level.ERROR;
                } else {
                    filterLevel = null;
                }
            }
            updateFilteredList(LiveConsoleManager.getInstance().getLogs().getValue());
        });

        LiveConsoleManager.getInstance().getLogs().observe(getViewLifecycleOwner(), this::updateFilteredList);
    }

    private void updateFilteredList(List<ConsoleLogEntry> allLogs) {
        if (binding == null) return;
        List<ConsoleLogEntry> filtered = new ArrayList<>();
        if (allLogs != null) {
            for (ConsoleLogEntry entry : allLogs) {
                if (filterLevel == null || entry.getLevel() == filterLevel) {
                    filtered.add(entry);
                }
            }
        }

        adapter.setEntries(filtered);
        boolean isEmpty = filtered.isEmpty();
        binding.layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvLogs.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.tvLogCount.setText(String.valueOf(filtered.size()));

        if (!isEmpty) {
            binding.rvLogs.post(() -> {
                if (binding != null) {
                    binding.rvLogs.scrollToPosition(filtered.size() - 1);
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class ConsoleAdapter extends RecyclerView.Adapter<ConsoleAdapter.ViewHolder> {

        private final Context context;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        private List<ConsoleLogEntry> items = new ArrayList<>();

        ConsoleAdapter(Context context) {
            this.context = context;
        }

        @SuppressLint("NotifyDataSetChanged")
        void setEntries(List<ConsoleLogEntry> newItems) {
            this.items = newItems != null ? newItems : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemConsoleLogBinding itemBinding = ItemConsoleLogBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ConsoleLogEntry entry = items.get(position);

            holder.binding.tvMessage.setText(entry.getMessage());
            holder.binding.tvMessage.setTypeface(FontManager.getInstance().getCodeFont(context));

            holder.binding.tvTimestamp.setText(dateFormat.format(new Date(entry.getTimestamp())));
            holder.binding.tvSource.setText(entry.getSource());
            holder.binding.tvSource.setVisibility(entry.getSource().isEmpty() ? View.GONE : View.VISIBLE);

            int colorRes;
            String levelText;
            switch (entry.getLevel()) {
                case ERROR:
                    levelText = "ERR";
                    colorRes = R.color.vcode_accent_error;
                    break;
                case WARN:
                    levelText = "WRN";
                    colorRes = R.color.vcode_accent_warning;
                    break;
                case INFO:
                    levelText = "INFO";
                    colorRes = R.color.vcode_accent_secondary;
                    break;
                case LOG:
                default:
                    levelText = "LOG";
                    colorRes = R.color.vcode_accent_primary;
                    break;
            }

            int color = ContextCompat.getColor(context, colorRes);
            holder.binding.tvLevelBadge.setText(levelText);
            holder.binding.tvLevelBadge.setTextColor(color);

            holder.itemView.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Console Log", entry.getMessage()));
                    Toast.makeText(context, R.string.vcode_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemConsoleLogBinding binding;

            ViewHolder(@NonNull ItemConsoleLogBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}

package com.cocode.vcode.ide.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.DialogCustomListBinding;
import com.cocode.vcode.ide.databinding.ItemCustomListOptionBinding;
import com.cocode.vcode.ide.utils.FontManager;

import java.util.List;

/**
 * Custom popup dialog for selecting from a list of items with custom typography.
 */
public class CustomListDialog {

    public static void show(Context context, String title, List<String> options, String selectedOption, OnOptionSelectedListener listener) {
        DialogCustomListBinding binding = DialogCustomListBinding.inflate(LayoutInflater.from(context));
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(binding.getRoot())
                .create();

        binding.tvDialogTitle.setText(title);
        binding.tvDialogTitle.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.btnCancel.setTypeface(FontManager.getInstance().getUiMedium(context));

        binding.btnCancel.setOnClickListener(v -> dialog.dismiss());

        CustomListAdapter adapter = new CustomListAdapter(options, selectedOption, option -> {
            if (listener != null) {
                listener.onSelected(option);
            }
            dialog.dismiss();
        });

        binding.rvOptions.setAdapter(adapter);

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
            int targetWidth = Math.min((int) (screenWidth * 0.92f), maxWidth);
            dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public interface OnOptionSelectedListener {
        void onSelected(String option);
    }

    private static class CustomListAdapter extends RecyclerView.Adapter<CustomListAdapter.ViewHolder> {
        private final List<String> options;
        private final String selectedOption;
        private final OnOptionSelectedListener listener;

        public CustomListAdapter(List<String> options, String selectedOption, OnOptionSelectedListener listener) {
            this.options = options;
            this.selectedOption = selectedOption;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCustomListOptionBinding binding = ItemCustomListOptionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String option = options.get(position);
            holder.binding.tvOptionName.setText(option);
            holder.binding.tvOptionName.setTypeface(FontManager.getInstance().getUiMedium(holder.itemView.getContext()));

            if (option.equals(selectedOption)) {
                holder.binding.ivCheck.setVisibility(View.VISIBLE);
            } else {
                holder.binding.ivCheck.setVisibility(View.GONE);
            }

            holder.binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSelected(option);
                }
            });
        }

        @Override
        public int getItemCount() {
            return options.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemCustomListOptionBinding binding;

            public ViewHolder(@NonNull ItemCustomListOptionBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}

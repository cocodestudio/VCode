package com.cocode.vcode.ide.git.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.databinding.ItemStashBinding;
import com.cocode.vcode.ide.git.model.StashItem;
import com.cocode.vcode.ide.utils.FontManager;

public class StashAdapter extends ListAdapter<StashItem, StashAdapter.ViewHolder> {

    private final StashListener listener;

    public StashAdapter(StashListener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull StashItem oldItem, @NonNull StashItem newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull StashItem oldItem, @NonNull StashItem newItem) {
                return oldItem.getName().equals(newItem.getName()) &&
                        oldItem.getMessage().equals(newItem.getMessage()) &&
                        oldItem.getTimestamp().equals(newItem.getTimestamp());
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStashBinding binding = ItemStashBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public interface StashListener {
        void onApply(StashItem item);

        void onDrop(StashItem item);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemStashBinding binding;

        ViewHolder(ItemStashBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(StashItem item) {
            Context context = itemView.getContext();
            binding.tvStashName.setTypeface(FontManager.getInstance().getUiSemiBold(context));
            binding.tvStashMessage.setTypeface(FontManager.getInstance().getUiMedium(context));

            binding.tvStashName.setText(String.format("%s: %s", item.getName(), item.getMessage()));
            binding.tvStashMessage.setText(item.getTimestamp());

            binding.btnApplyStash.setOnClickListener(v -> listener.onApply(item));
            binding.btnDropStash.setOnClickListener(v -> listener.onDrop(item));
        }
    }
}

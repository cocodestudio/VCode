package com.cocode.vcode.ide.git.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.databinding.ItemBranchBinding;
import com.cocode.vcode.ide.git.model.BranchItem;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * RecyclerView adapter for displaying local and remote Git branches.
 */
public class BranchAdapter extends ListAdapter<BranchItem, BranchAdapter.ViewHolder> {

    private final BranchListener listener;

    public BranchAdapter(BranchListener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull BranchItem old, @NonNull BranchItem n) {
                return old.getName().equals(n.getName());
            }

            @Override
            public boolean areContentsTheSame(@NonNull BranchItem old, @NonNull BranchItem n) {
                return old.isActive() == n.isActive() &&
                        old.getLastCommit().equals(n.getLastCommit());
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemBranchBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public interface BranchListener {
        void onBranchClick(BranchItem item);

        void onOverflowClick(BranchItem item, View anchor);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemBranchBinding binding;

        ViewHolder(ItemBranchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(BranchItem item) {
            Context context = itemView.getContext();

            binding.tvBranchName.setTypeface(FontManager.getInstance().getUiSemiBold(context));
            binding.tvLastCommit.setTypeface(FontManager.getInstance().getUiMedium(context));
            binding.tvBranchName.setText(item.getName());

            String commitMsg = item.getLastCommit();
            if (commitMsg != null && commitMsg.length() > 30) {
                commitMsg = commitMsg.substring(0, 27) + "...";
            }
            binding.tvLastCommit.setText(commitMsg);
            binding.viewActiveStrip.setVisibility(item.isActive() ? View.VISIBLE : View.GONE);

            binding.getRoot().setOnClickListener(v -> listener.onBranchClick(item));
            binding.btnOverflow.setOnClickListener(v -> listener.onOverflowClick(item, v));
        }
    }
}
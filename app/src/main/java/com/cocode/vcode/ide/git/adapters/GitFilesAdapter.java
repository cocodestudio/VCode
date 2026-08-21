package com.cocode.vcode.ide.git.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ItemGitFileBinding;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.utils.FileIconHelper;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * RecyclerView adapter for displaying changed files in Git staging and working tree lists.
 */
public class GitFilesAdapter extends ListAdapter<GitFileItem, GitFilesAdapter.ViewHolder> {

    private final GitFileListener listener;
    private String projectName = "";

    public GitFilesAdapter(GitFileListener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull GitFileItem oldItem, @NonNull GitFileItem newItem) {
                return oldItem.getPath().equals(newItem.getPath());
            }

            @Override
            public boolean areContentsTheSame(@NonNull GitFileItem oldItem, @NonNull GitFileItem newItem) {
                return oldItem.getStatus().equals(newItem.getStatus()) &&
                        oldItem.isStaged() == newItem.isStaged();
            }
        });
        this.listener = listener;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName != null ? projectName.trim() : "";
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGitFileBinding binding = ItemGitFileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public interface GitFileListener {
        void onFileClick(GitFileItem item);

        void onActionClick(GitFileItem item);

        void onDiscardClick(GitFileItem item);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemGitFileBinding binding;

        ViewHolder(ItemGitFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(GitFileItem item) {
            Context context = itemView.getContext();

            binding.tvFileName.setTypeface(FontManager.getInstance().getUiSemiBold(context));
            binding.tvFilePath.setTypeface(FontManager.getInstance().getUiMedium(context));
            binding.tvStatusBadge.setTypeface(FontManager.getInstance().getUiSemiBold(context));

            binding.tvFileName.setText(item.getFileName());
            binding.tvStatusBadge.setText(item.getStatus());

            String pristinePath = item.getPath();
            if (!projectName.isEmpty() && !pristinePath.contains("/")) {
                binding.tvFilePath.setText(projectName.concat("/").concat(pristinePath));
            } else {
                binding.tvFilePath.setText(pristinePath);
            }

            int statusColor;
            switch (item.getStatus()) {
                case "A":
                    statusColor = R.color.vcode_git_staged_color;
                    break;
                case "D":
                    statusColor = R.color.vcode_git_deleted_color;
                    break;
                case "?":
                    statusColor = R.color.vcode_git_untracked_color;
                    break;
                default:
                    statusColor = R.color.vcode_git_modified_color;
                    break;
            }

            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(UiUtils.dpToPx(context, 4));
            badge.setColor(ContextCompat.getColor(context, statusColor));
            binding.tvStatusBadge.setBackground(badge);

            binding.btnAction.setImageResource(item.isStaged() ? R.drawable.ic_minus : R.drawable.ic_plus);
            binding.btnAction.setColorFilter(ContextCompat.getColor(context,
                    item.isStaged() ? R.color.vcode_accent_error : R.color.vcode_accent_primary));

            FileIconHelper.setFileIconAndColor(binding.ivFileIcon, item.getFileName());

            binding.getRoot().setOnClickListener(v -> listener.onFileClick(item));
            binding.btnAction.setOnClickListener(v -> listener.onActionClick(item));

            if (binding.btnDiscard != null) {
                if (!item.isStaged()) {
                    binding.btnDiscard.setVisibility(android.view.View.VISIBLE);
                    binding.btnDiscard.setOnClickListener(v -> listener.onDiscardClick(item));
                } else {
                    binding.btnDiscard.setVisibility(android.view.View.GONE);
                }
            }
        }
    }
}